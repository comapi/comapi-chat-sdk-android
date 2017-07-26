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
                List<ChatConversationBase> conversations = store.getAllConversations();
                emitter.onNext(conversations);
                emitter.onCompleted();
            }
        }), Emitter.BackpressureMode.LATEST);
    }

    /**
     * Wraps loading single conversations from store implementation into an Observable.
     *
     * @return Observable returning single conversation from store.
     */
    Observable<ChatConversationBase> loadConversation(@NonNull String conversationId) {

        return Observable.create(emitter -> storeFactory.execute(new StoreTransaction<ChatStore>() {
            @Override
            protected void execute(ChatStore store) {
                emitter.onNext(store.getConversation(conversationId));
                emitter.onCompleted();
            }
        }), Emitter.BackpressureMode.LATEST);
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

                        store.beginTransaction();

                        List<ChatMessage> messages = modelAdapter.adaptMessages(response.getMessages());

                        long updatedOn = 0;

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
                                    .setFirstEventId(savedConversation.getFirstLocalEventId() < 0 ? response.getEarliestEventId() : Math.min(savedConversation.getFirstLocalEventId(), response.getEarliestEventId()))
                                    .setLastEventId(savedConversation.getLastLocalEventId() < 0 ? response.getLatestEventId() : Math.max(savedConversation.getLastLocalEventId(), response.getLatestEventId()))
                                    .setLatestRemoteEventId(savedConversation.getLatestRemoteEventId() < 0 ? response.getLatestEventId() : Math.max(savedConversation.getLatestRemoteEventId(), response.getLatestEventId()))
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

                                                store.beginTransaction();

                                                List<ChatMessageStatus> statuses = modelAdapter.adaptEvents(toDelete);

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

    public Observable<Boolean> updateStoreForNewMessage(final ChatMessage message, final ChatController.NoConversationListener noConversationListener) {

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
                    message.addStatusUpdate(new ChatMessageStatus(message.getConversationId(), message.getMessageId(), message.getFromWhom().getId(), LocalMessageStatus.sending, System.currentTimeMillis(), null));
                    isSuccessful = isSuccessful && store.upsert(message);
                } else {
                    isSuccessful = isSuccessful && store.upsert(message);
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

    public Observable<Boolean> updateStoreForSentError(String conversationId, String tempId, String profileId) {

        return asObservable(new Executor<Boolean>() {
            @Override
            protected void execute(ChatStore store, Emitter<Boolean> emitter) {
                store.beginTransaction();
                boolean isSuccess = store.update(new ChatMessageStatus(conversationId, tempId, profileId, LocalMessageStatus.error, System.currentTimeMillis(), null));
                store.endTransaction();
                emitter.onNext(isSuccess);
                emitter.onCompleted();
            }
        });
    }

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

    private boolean doUpdateConversationFromEvent(ChatStore store, String conversationId, Long eventId, Long updatedOn) {

        ChatConversationBase conversation = store.getConversation(conversationId);
        if (conversation != null) {
            if (eventId != null) {
                if (conversation.getLatestRemoteEventId() < eventId) {
                    conversation.setLatestRemoteEventId(eventId);
                }
                if (conversation.getLastLocalEventId() < eventId) {
                    conversation.setLatestLocalEventId(eventId);
                }
                if (conversation.getFirstLocalEventId() == -1) {
                    conversation.setFirstLocalEventId(eventId);
                }
                if (conversation.getUpdatedOn() < updatedOn) {
                    conversation.setUpdatedOn(updatedOn);
                }
            }
            return store.update(conversation);
        }
        return false;
    }

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
                            isSuccess = store.update(new ChatMessageStatus(conversationId, messageId, profileId, status, DateHelper.getUTCMilliseconds(statusUpdate.getTimestamp()), null));
                        }
                    }
                }

                store.endTransaction();

                emitter.onNext(isSuccess);
                emitter.onCompleted();
            }
        });
    }

    public Observable<ChatConversationBase> getConversation(String conversationId) {

        return asObservable(new Executor<ChatConversationBase>() {
            @Override
            void execute(ChatStore store, Emitter<ChatConversationBase> emitter) {
                emitter.onNext(store.getConversation(conversationId));
                emitter.onCompleted();
            }
        });
    }

    public Observable<Boolean> upsertConversation(ChatConversation conversation) {

        List<ChatConversation> conversations = new ArrayList<>();
        conversations.add(conversation);
        return upsertConversations(conversations);
    }

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
                        if (conversation.getLatestRemoteEventId() == null) {
                            toSave.setLatestRemoteEventId(-1L);
                        } else {
                            toSave.setLatestRemoteEventId(conversation.getLatestRemoteEventId());
                        }
                        if (conversation.getUpdatedOn() == null) {
                            toSave.setUpdatedOn(System.currentTimeMillis());
                        } else {
                            toSave.setUpdatedOn(conversation.getUpdatedOn());
                        }
                    } else {
                        toSave.setFirstLocalEventId(saved.getFirstLocalEventId());
                        toSave.setLastLocalEventId(saved.getLastLocalEventId());
                        if (conversation.getLatestRemoteEventId() == null) {
                            toSave.setLatestRemoteEventId(saved.getLatestRemoteEventId());
                        } else {
                            toSave.setLatestRemoteEventId(Math.max(saved.getLatestRemoteEventId(), conversation.getLatestRemoteEventId()));
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
                        toSave.setFirstEventId(saved.getFirstLocalEventId());
                        toSave.setLastEventId(saved.getLastLocalEventId());
                        if (conversation.getLatestRemoteEventId() == null) {
                            toSave.setLatestRemoteEventId(saved.getLatestRemoteEventId());
                        } else {
                            toSave.setLatestRemoteEventId(Math.max(saved.getLatestRemoteEventId(), conversation.getLatestRemoteEventId()));
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

    public Observable<Integer> deleteOrphanedEvents(String[] ids) {
        return db.deleteOrphanedEvents(ids);
    }

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

    abstract class Executor<T> {
        abstract void execute(ChatStore store, Emitter<T> emitter);
    }
}
