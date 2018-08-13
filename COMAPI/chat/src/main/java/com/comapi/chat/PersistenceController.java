/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Comapi (trading name of Dynmark International Limited)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons
 * to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.comapi.chat;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.comapi.chat.database.Database;
import com.comapi.chat.model.ChatConversation;
import com.comapi.chat.model.ChatConversationBase;
import com.comapi.chat.model.ChatMessage;
import com.comapi.chat.model.ChatMessageStatus;
import com.comapi.chat.model.ChatStore;
import com.comapi.chat.model.LocalMessageStatus;
import com.comapi.chat.model.ModelAdapter;
import com.comapi.internal.helpers.DateHelper;
import com.comapi.internal.log.Logger;
import com.comapi.internal.network.ComapiResult;
import com.comapi.internal.network.model.messaging.MessageReceived;
import com.comapi.internal.network.model.messaging.MessageStatus;
import com.comapi.internal.network.model.messaging.MessageStatusUpdate;
import com.comapi.internal.network.model.messaging.MessagesQueryResponse;

import java.util.ArrayList;
import java.util.List;

import rx.Emitter;
import rx.Observable;

import static com.comapi.chat.EventsHandler.MESSAGE_METADATA_TEMP_ID;

/**
 * Persistence controller for more general {@link ChatController} class.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
class PersistenceController {

    private final StoreFactory<ChatStore> storeFactory;
    private final ModelAdapter modelAdapter;
    private final Database db;

    /**
     * Recommended constructor.
     *
     * @param db           Chat layer database.
     * @param adapter      Model adapter between Foundation and Chat Layer.
     * @param storeFactory Transaction factory for messaging persistence store implementation.
     */
    PersistenceController(Database db, ModelAdapter adapter, StoreFactory<ChatStore> storeFactory, Logger log) {
        this.db = db;
        this.modelAdapter = adapter;
        storeFactory.injectLogger(log);
        this.storeFactory = storeFactory;
    }

    /**
     * Wraps loading all conversations from store implementation into an Observable.
     *
     * @return Observable returning all conversations from store.
     */
    Observable<List<ChatConversationBase>> loadAllConversations() {

        return Observable.create(emitter -> storeFactory.execute(new StoreTransaction<ChatStore>() {
            @Override
            protected void execute(ChatStore store) {
                store.open();
                List<ChatConversationBase> conversations = store.getAllConversations();
                store.close();
                emitter.onNext(conversations);
                emitter.onCompleted();
            }
        }), Emitter.BackpressureMode.LATEST);
    }

    /**
     * Get single conversations from store implementation as an Observable.
     *
     * @return Observable returning single conversation from store.
     */
    Observable<ChatConversationBase> getConversation(@NonNull String conversationId) {

        return Observable.create(emitter -> storeFactory.execute(new StoreTransaction<ChatStore>() {
            @Override
            protected void execute(ChatStore store) {
                store.open();
                ChatConversationBase c = store.getConversation(conversationId);
                store.close();
                emitter.onNext(c);
                emitter.onCompleted();
            }
        }), Emitter.BackpressureMode.BUFFER);
    }

    /**
     * Upsert messages from the query and updates conversation in single store transaction.
     *
     * @param conversationId Conversation unique id.
     * @param result         Response from message query.
     * @return Observable returning message query response from argument unchanged by the method.
     */
    Observable<ComapiResult<MessagesQueryResponse>> processMessageQueryResponse(@NonNull String conversationId, @NonNull ComapiResult<MessagesQueryResponse> result) {

        final MessagesQueryResponse response = result.getResult();

        return result.isSuccessful() && response != null ?

                Observable.create(emitter -> storeFactory.execute(new StoreTransaction<ChatStore>() {

                    @Override
                    protected void execute(ChatStore store) {

                        List<ChatMessage> messages = modelAdapter.adaptMessages(response.getMessages());

                        long updatedOn = 0;

                        store.beginTransaction();

                        if (messages != null && !messages.isEmpty()) {
                            for (ChatMessage msg : messages) {
                                store.upsert(msg);
                                if (msg.getSentOn() > updatedOn) {
                                    updatedOn = msg.getSentOn();
                                }
                            }
                        }

                        ChatConversationBase savedConversation = store.getConversation(conversationId);
                        if (savedConversation != null) {
                            ChatConversationBase updateConversation = ChatConversationBase.baseBuilder()
                                    .setConversationId(savedConversation.getConversationId())
                                    .setETag(savedConversation.getETag())
                                    .setFirstLocalEventId(nullOrNegative(savedConversation.getFirstLocalEventId()) ? response.getEarliestEventId() : Math.min(savedConversation.getFirstLocalEventId(), response.getEarliestEventId()))
                                    .setLastLocalEventId(nullOrNegative(savedConversation.getLastLocalEventId()) ? response.getLatestEventId() : Math.max(savedConversation.getLastLocalEventId(), response.getLatestEventId()))
                                    .setLastRemoteEventId(nullOrNegative(savedConversation.getLastRemoteEventId()) ? response.getLatestEventId() : Math.max(savedConversation.getLastRemoteEventId(), response.getLatestEventId()))
                                    .setUpdatedOn(Math.max(savedConversation.getUpdatedOn(), updatedOn))
                                    .build();

                            store.update(updateConversation);
                        }

                        store.endTransaction();

                        emitter.onNext(result);
                        emitter.onCompleted();
                    }
                }), Emitter.BackpressureMode.BUFFER) : Observable.fromCallable(() -> result);
    }

    /**
     * Checks if value is non-null and non-negative.
     *
     * @param n Long number to check.
     * @return True if parameter is non-null and non-negative.
     */
    private boolean nullOrNegative(Long n) {
        return (n == null || n < 0);
    }

    /**
     * Handle orphaned events related to message query.
     *
     * @param result Message query response
     * @return Observable returning message query response from argument unchanged by the method.
     */
    Observable<ComapiResult<MessagesQueryResponse>> processOrphanedEvents(ComapiResult<MessagesQueryResponse> result, final ChatController.OrphanedEventsToRemoveListener removeListener) {

        if (result.isSuccessful() && result.getResult() != null) {

            final MessagesQueryResponse response = result.getResult();

            final List<MessageReceived> messages = response.getMessages();
            final String[] ids = new String[messages.size()];

            if (!messages.isEmpty()) {
                for (int i = 0; i < messages.size(); i++) {
                    ids[i] = messages.get(i).getMessageId();
                }
            }

            return db.save(response.getOrphanedEvents())
                    .flatMap(count -> db.queryOrphanedEvents(ids))
                    .flatMap(toDelete -> Observable.create(emitter ->
                            storeFactory.execute(new StoreTransaction<ChatStore>() {

                                @Override
                                protected void execute(ChatStore store) {

                                    if (!toDelete.isEmpty()) {

                                        storeFactory.execute(new StoreTransaction<ChatStore>() {
                                            @Override
                                            protected void execute(ChatStore store) {

                                                List<ChatMessageStatus> statuses = modelAdapter.adaptEvents(toDelete);

                                                store.beginTransaction();

                                                if (!statuses.isEmpty()) {
                                                    for (ChatMessageStatus status : statuses) {
                                                        store.update(status);
                                                    }
                                                }

                                                String[] ids = new String[toDelete.size()];
                                                for (int i = 0; i < toDelete.size(); i++) {
                                                    ids[i] = toDelete.get(i).id();
                                                }
                                                removeListener.remove(ids);

                                                store.endTransaction();

                                                emitter.onNext(result);
                                                emitter.onCompleted();
                                            }
                                        });
                                    } else {
                                        emitter.onNext(result);
                                        emitter.onCompleted();
                                    }
                                }
                            }), Emitter.BackpressureMode.BUFFER));
        } else {
            return Observable.fromCallable(() -> result);
        }
    }

    /**
     * Deletes temporary message and inserts provided one. If no associated conversation exists will trigger GET from server.
     *
     * @param message                Message to save.
     * @param noConversationListener Listener for the chat controller to get conversation if no local copy is present.
     * @return Observable emitting result.
     */
    public Observable<Boolean> updateStoreWithNewMessage(final ChatMessage message, final ChatController.NoConversationListener noConversationListener) {

        return asObservable(new Executor<Boolean>() {
            @Override
            protected void execute(ChatStore store, Emitter<Boolean> emitter) {

                boolean isSuccessful = true;

                store.beginTransaction();

                ChatConversationBase conversation = store.getConversation(message.getConversationId());

                String tempId = (String) (message.getMetadata() != null ? message.getMetadata().get(MESSAGE_METADATA_TEMP_ID) : null);
                if (!TextUtils.isEmpty(tempId)) {
                    store.deleteMessage(message.getConversationId(), tempId);
                }

                if (message.getSentEventId() == null) {
                    message.setSentEventId(-1L);
                }

                if (message.getSentEventId() == -1L) {
                    if (conversation != null && conversation.getLastLocalEventId() != -1L) {
                        message.setSentEventId(conversation.getLastLocalEventId() + 1);
                    }
                    message.addStatusUpdate(ChatMessageStatus.builder().populate(message.getConversationId(), message.getMessageId(), message.getFromWhom().getId(), LocalMessageStatus.sent, System.currentTimeMillis(), null).build());
                    isSuccessful = store.upsert(message);
                } else {
                    isSuccessful = store.upsert(message);
                }

                if (!doUpdateConversationFromEvent(store, message.getConversationId(), message.getSentEventId(), message.getSentOn()) && noConversationListener != null) {
                    noConversationListener.getConversation(message.getConversationId());
                }

                store.endTransaction();

                emitter.onNext(isSuccessful);
                emitter.onCompleted();
            }
        });
    }

    /**
     * Insert 'error' message status if sending message failed.
     *
     * @param conversationId Unique conversation id.
     * @param tempId         Id of an temporary message for which
     * @param profileId      Profile id from current session data.
     * @return Observable emitting result.
     */
    public Observable<Boolean> updateStoreForSentError(String conversationId, String tempId, String profileId) {

        return asObservable(new Executor<Boolean>() {
            @Override
            protected void execute(ChatStore store, Emitter<Boolean> emitter) {
                store.beginTransaction();
                boolean isSuccess = store.update(ChatMessageStatus.builder().populate(conversationId, tempId, profileId, LocalMessageStatus.error, System.currentTimeMillis(), null).build());
                store.endTransaction();
                emitter.onNext(isSuccess);
                emitter.onCompleted();
            }
        });
    }

    /**
     * Update conversation state with received event details. This should be called only inside transaction.
     *
     * @param store          Chat Store instance.
     * @param conversationId Unique conversation id.
     * @param eventId        Conversation event id.
     * @param updatedOn      New timestamp of state update.
     * @return True if successful.
     */
    private boolean doUpdateConversationFromEvent(ChatStore store, String conversationId, Long eventId, Long updatedOn) {

        ChatConversationBase conversation = store.getConversation(conversationId);

        if (conversation != null) {

            ChatConversationBase.Builder builder = ChatConversationBase.baseBuilder().populate(conversation);

            if (eventId != null) {
                if (conversation.getLastRemoteEventId() < eventId) {
                    builder.setLastRemoteEventId(eventId);
                }
                if (conversation.getLastLocalEventId() < eventId) {
                    builder.setLastLocalEventId(eventId);
                }
                if (conversation.getFirstLocalEventId() == -1) {
                    builder.setFirstLocalEventId(eventId);
                }
                if (conversation.getUpdatedOn() < updatedOn) {
                    builder.setUpdatedOn(updatedOn);
                }
            }

            return store.update(builder.build());
        }
        return false;
    }

    /**
     * Insert new message status.
     *
     * @param status New message status.
     * @return Observable emitting result.
     */
    public Observable<Boolean> upsertMessageStatus(ChatMessageStatus status) {

        return asObservable(new Executor<Boolean>() {
            @Override
            void execute(ChatStore store, Emitter<Boolean> emitter) {

                store.beginTransaction();

                boolean isSuccessful = store.update(status) && doUpdateConversationFromEvent(store, status.getConversationId(), status.getConversationEventId(), status.getUpdatedOn());
                store.endTransaction();

                emitter.onNext(isSuccessful);
                emitter.onCompleted();
            }
        });
    }

    /**
     * Insert new message statuses obtained from message query.
     *
     * @param conversationId Unique conversation id.
     * @param profileId      Profile id from current session details.
     * @param msgStatusList  New message statuses.
     * @return Observable emitting result.
     */
    public Observable<Boolean> upsertMessageStatuses(String conversationId, String profileId, List<MessageStatusUpdate> msgStatusList) {

        return asObservable(new Executor<Boolean>() {
            @Override
            void execute(ChatStore store, Emitter<Boolean> emitter) {

                store.beginTransaction();

                boolean isSuccess = false;
                for (MessageStatusUpdate statusUpdate : msgStatusList) {
                    for (String messageId : statusUpdate.getMessageIds()) {
                        LocalMessageStatus status = null;
                        if (MessageStatus.delivered.name().equals(statusUpdate.getStatus())) {
                            status = LocalMessageStatus.delivered;
                        } else if (MessageStatus.read.name().equals(statusUpdate.getStatus())) {
                            status = LocalMessageStatus.read;
                        }

                        if (status != null) {
                            isSuccess = store.update(ChatMessageStatus.builder().populate(conversationId, messageId, profileId, status, DateHelper.getUTCMilliseconds(statusUpdate.getTimestamp()), null).build());
                        }
                    }
                }

                store.endTransaction();

                emitter.onNext(isSuccess);
                emitter.onCompleted();
            }
        });
    }

    /**
     * Insert or update conversation in the store.
     *
     * @param conversation Conversation object to insert or apply an update.
     * @return Observable emitting result.
     */
    public Observable<Boolean> upsertConversation(ChatConversation conversation) {

        List<ChatConversation> conversations = new ArrayList<>();
        conversations.add(conversation);
        return upsertConversations(conversations);
    }

    /**
     * Insert or update a list of conversations.
     *
     * @param conversationsToAdd List of conversations to insert or apply an update.
     * @return Observable emitting result.
     */
    public Observable<Boolean> upsertConversations(List<ChatConversation> conversationsToAdd) {

        return asObservable(new Executor<Boolean>() {
            @Override
            void execute(ChatStore store, Emitter<Boolean> emitter) {

                store.beginTransaction();

                boolean isSuccess = true;

                for (ChatConversation conversation : conversationsToAdd) {
                    ChatConversation.Builder toSave = ChatConversation.builder().populate(conversation);

                    ChatConversationBase saved = store.getConversation(conversation.getConversationId());
                    if (saved == null) {
                        toSave.setFirstLocalEventId(-1L);
                        toSave.setLastLocalEventId(-1L);
                        if (conversation.getLastRemoteEventId() == null) {
                            toSave.setLastRemoteEventId(-1L);
                        } else {
                            toSave.setLastRemoteEventId(conversation.getLastRemoteEventId());
                        }
                        if (conversation.getUpdatedOn() == null) {
                            toSave.setUpdatedOn(System.currentTimeMillis());
                        } else {
                            toSave.setUpdatedOn(conversation.getUpdatedOn());
                        }
                    } else {
                        toSave.setFirstLocalEventId(saved.getFirstLocalEventId());
                        toSave.setLastLocalEventId(saved.getLastLocalEventId());
                        if (conversation.getLastRemoteEventId() == null) {
                            toSave.setLastRemoteEventId(saved.getLastRemoteEventId());
                        } else {
                            toSave.setLastRemoteEventId(Math.max(saved.getLastRemoteEventId(), conversation.getLastRemoteEventId()));
                        }
                        if (conversation.getUpdatedOn() == null) {
                            toSave.setUpdatedOn(System.currentTimeMillis());
                        } else {
                            toSave.setUpdatedOn(conversation.getUpdatedOn());
                        }
                    }

                    isSuccess = isSuccess && store.upsert(toSave.build());
                }

                store.endTransaction();

                emitter.onNext(isSuccess);
                emitter.onCompleted();
            }
        });
    }

    /**
     * Update conversations.
     *
     * @param conversationsToUpdate List of conversations to apply an update.
     * @return Observable emitting result.
     */
    public Observable<Boolean> updateConversations(List<ChatConversation> conversationsToUpdate) {

        return asObservable(new Executor<Boolean>() {
            @Override
            void execute(ChatStore store, Emitter<Boolean> emitter) {

                store.beginTransaction();

                boolean isSuccess = true;

                for (ChatConversationBase conversation : conversationsToUpdate) {
                    ChatConversationBase.Builder toSave = ChatConversationBase.baseBuilder();

                    ChatConversationBase saved = store.getConversation(conversation.getConversationId());
                    if (saved != null) {
                        toSave.setConversationId(saved.getConversationId());
                        toSave.setFirstLocalEventId(saved.getFirstLocalEventId());
                        toSave.setLastLocalEventId(saved.getLastLocalEventId());
                        if (conversation.getLastRemoteEventId() == null) {
                            toSave.setLastRemoteEventId(saved.getLastRemoteEventId());
                        } else {
                            toSave.setLastRemoteEventId(Math.max(saved.getLastRemoteEventId(), conversation.getLastRemoteEventId()));
                        }
                        if (conversation.getUpdatedOn() == null) {
                            toSave.setUpdatedOn(System.currentTimeMillis());
                        } else {
                            toSave.setUpdatedOn(conversation.getUpdatedOn());
                        }
                        toSave.setETag(conversation.getETag());
                    }
                    isSuccess = isSuccess && store.update(toSave.build());
                }

                store.endTransaction();

                emitter.onNext(isSuccess);
                emitter.onCompleted();
            }
        });

    }

    /**
     * Delete conversation from the store.
     *
     * @param conversationId Unique conversation id.
     * @return Observable emitting result.
     */
    public Observable<Boolean> deleteConversation(String conversationId) {

        return asObservable(new Executor<Boolean>() {
            @Override
            void execute(ChatStore store, Emitter<Boolean> emitter) {
                store.beginTransaction();
                boolean isSuccess = store.deleteConversation(conversationId);
                store.endTransaction();
                emitter.onNext(isSuccess);
                emitter.onCompleted();
            }
        });
    }

    /**
     * Delete orphaned events from internal database.
     *
     * @param ids Ids of events to remove from internal database.
     * @return Observable emitting number of deleted events.
     */
    public Observable<Integer> deleteOrphanedEvents(String[] ids) {
        return db.deleteOrphanedEvents(ids);
    }

    /**
     * Delete conversations from the store.
     *
     * @param conversationsToDelete List of conversations to delete.
     * @return Observable emitting result.
     */
    public Observable<Boolean> deleteConversations(List<ChatConversationBase> conversationsToDelete) {
        return asObservable(new Executor<Boolean>() {
            @Override
            void execute(ChatStore store, Emitter<Boolean> emitter) {
                store.beginTransaction();
                boolean isSuccess = true;
                for (int i = 0; i < conversationsToDelete.size(); i++) {
                    isSuccess = isSuccess && store.deleteConversation(conversationsToDelete.get(i).getConversationId());
                }
                store.endTransaction();
                emitter.onNext(isSuccess);
                emitter.onCompleted();
            }
        });
    }

    /**
     * Executes transaction callback ass an observable.
     *
     * @param transaction Store transaction.
     * @param <T>         Store class.
     * @return Observable executing given store transaction.
     */
    private <T> Observable<T> asObservable(Executor<T> transaction) {
        return Observable.create(emitter -> storeFactory.execute(new StoreTransaction<ChatStore>() {
            @Override
            protected void execute(ChatStore store) {
                try {
                    transaction.execute(store, emitter);
                } finally {
                    emitter.onCompleted();
                }
            }
        }), Emitter.BackpressureMode.BUFFER);
    }

    /**
     * Interface to provide implementation of an transaction to execute.
     *
     * @param <T> Emitted result class.
     */
    abstract class Executor<T> {
        /**
         * Execute transaction.
         *
         * @param store   Store implementation.
         * @param emitter Abstraction over a RxJava Subscriber.
         */
        abstract void execute(ChatStore store, Emitter<T> emitter);
    }
}
