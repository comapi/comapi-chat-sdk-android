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

import android.text.TextUtils;

import com.comapi.RxComapiClient;
import com.comapi.chat.model.ChatConversation;
import com.comapi.chat.model.ChatConversationBase;
import com.comapi.chat.model.ChatMessage;
import com.comapi.chat.model.ChatMessageStatus;
import com.comapi.chat.model.ChatStore;
import com.comapi.chat.model.ModelAdapter;
import com.comapi.internal.ComapiException;
import com.comapi.internal.helpers.DateHelper;
import com.comapi.internal.log.Logger;
import com.comapi.internal.network.ComapiResult;
import com.comapi.internal.network.model.conversation.Conversation;
import com.comapi.internal.network.model.conversation.ConversationDetails;
import com.comapi.internal.network.model.conversation.ConversationUpdate;
import com.comapi.internal.network.model.events.Event;
import com.comapi.internal.network.model.events.conversation.message.MessageDeliveredEvent;
import com.comapi.internal.network.model.events.conversation.message.MessageReadEvent;
import com.comapi.internal.network.model.events.conversation.message.MessageSentEvent;
import com.comapi.internal.network.model.messaging.ConversationEventsResponse;
import com.comapi.internal.network.model.messaging.MessageSentResponse;
import com.comapi.internal.network.model.messaging.MessageStatus;
import com.comapi.internal.network.model.messaging.MessageStatusUpdate;
import com.comapi.internal.network.model.messaging.MessageToSend;
import com.comapi.internal.network.model.messaging.Sender;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

/**
 * Main controller for Chat Layer specific functionality.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
class ChatController {

    private static final int ETAG_NOT_VALID = 412;

    private static final Integer PAGE_SIZE = 5;

    private static final Integer UPDATE_FROM_EVENTS_LIMIT = 5;

    private int QUERY_EVENTS_NUMBER_OF_CALLS_LIMIT = 1000;

    private final WeakReference<RxComapiClient> clientReference;

    private final ModelAdapter adapter;

    private final PersistenceController persistenceController;

    private final ObservableExecutor obsExec;

    private final Logger log;

    private NoConversationListener noConversationListener;

    private OrphanedEventsToRemoveListener orphanedEventsToRemoveListener;

    public Observable<ComapiResult<Void>> handleParticipantsAdded(final String conversationId, final ComapiResult<Void> result) {
        return handleParticipantsAdded(conversationId).map(conversation -> result);
    }

    public Observable<ChatResult> handleParticipantsAdded(final String conversationId) {
        return persistenceController.getConversation(conversationId).flatMap(new Func1<ChatConversationBase, Observable<ChatResult>>() {
            @Override
            public Observable<ChatResult> call(ChatConversationBase conversation) {
                if (conversation == null) {
                    return handleNoLocalConversation(conversationId);
                } else {
                    return Observable.fromCallable(() -> new ChatResult(true, null));
                }
            }
        });
    }

    public Observable<ChatResult> handleMessageError(String conversationId, String tempId, Throwable throwable) {
        return persistenceController.updateStoreForSentError(conversationId, tempId, getProfileId()).map(success -> new ChatResult(false, new ChatResult.Error(1501, throwable.getLocalizedMessage())));
    }

    interface NoConversationListener {
        void getConversation(String conversationId);
    }

    interface OrphanedEventsToRemoveListener {
        void remove(String[] ids);
    }

    /**
     * Recommended constructor.
     *
     * @param client                Comapi client.
     * @param persistenceController Controller over implementation of local conversation and message store.
     * @param log                   Internal logger instance.
     */
    ChatController(final RxComapiClient client, final PersistenceController persistenceController, final ObservableExecutor obsExec, final ModelAdapter adapter, final Logger log) {
        this.clientReference = new WeakReference<>(client);
        this.adapter = adapter;
        this.log = log;
        this.persistenceController = persistenceController;
        this.obsExec = obsExec;
        this.noConversationListener = conversationId -> obsExec.execute(handleNoLocalConversation(conversationId));
        this.orphanedEventsToRemoveListener = ids -> obsExec.execute(persistenceController.deleteOrphanedEvents(ids));
    }

    /**
     * Checks if controller state is correct.
     *
     * @return Foundation client instance.
     */
    Observable<RxComapiClient> checkState() {

        final RxComapiClient client = clientReference.get();
        if (client == null) {
            return Observable.error(new ComapiException("No client instance available in controller."));
        } else {
            return Observable.fromCallable(() -> client);
        }
    }

    /**
     * When SDK detects missing conversation it makes query in services and saves in the saves locally.
     *
     * @param conversationId Unique identifier of an conversation.
     * @return Observable to handle missing local conversation data.
     */
    Observable<ChatResult> handleNoLocalConversation(String conversationId) {

        return checkState().flatMap(client -> client.service().messaging().getConversation(conversationId)
                .flatMap(new Func1<ComapiResult<ConversationDetails>, Observable<ChatResult>>() {
                    @Override
                    public Observable<ChatResult> call(ComapiResult<ConversationDetails> result) {
                        if (result.isSuccessful() && result.getResult() != null) {
                            return persistenceController.upsertConversation(ChatConversation.builder().populate(result.getResult(), result.getETag()).build())
                                    .map(success -> new ChatResult(success, success ? null : new ChatResult.Error(1500, "External store reported failure.")));
                        } else {
                            return Observable.fromCallable(() -> adapter.adaptResult(result));
                        }
                    }
                }));
    }

    /**
     * Mark messages in a conversations as delivered.
     *
     * @param conversationId Conversation unique id.
     * @param ids            Ids of messages in a single conversation to be marked as delivered.
     */
    Observable<ComapiResult<Void>> markDelivered(String conversationId, Set<String> ids) {

        final List<MessageStatusUpdate> updates = new ArrayList<>();
        updates.add(MessageStatusUpdate.builder().setMessagesIds(ids).setStatus(MessageStatus.delivered).setTimestamp(DateHelper.getCurrentUTC()).build());

        return checkState().flatMap(client -> client.service().messaging().updateMessageStatus(conversationId, updates)
                .retryWhen(new Func1<Observable<? extends Throwable>, Observable<Long>>() {
                               @Override
                               public Observable<Long> call(Observable<? extends Throwable> observable) {
                                   return observable.zipWith(Observable.range(1, 3), (Func2<Throwable, Integer, Integer>) (throwable, integer) -> integer).flatMap(new Func1<Integer, Observable<Long>>() {
                                       @Override
                                       public Observable<Long> call(Integer retryCount) {
                                           return Observable.timer((long) Math.pow(1, retryCount), TimeUnit.SECONDS);
                                       }
                                   });
                               }
                           }
                ));
    }

    /**
     * Gets next page of messages and saves them using {@link ChatStore} implementation.
     *
     * @param conversationId ID of a conversation in which participant is typing a message.
     * @return Observable with the result.
     */
    Observable<ChatResult> getPreviousMessages(final String conversationId) {

        return persistenceController.loadConversation(conversationId)
                .map(conversation -> conversation != null ? conversation.getFirstLocalEventId() : null)
                .flatMap(new Func1<Long, Observable<ChatResult>>() {
                    @Override
                    public Observable<ChatResult> call(Long from) {

                        final Long queryFrom;

                        if (from != null) {

                            if (from == 0) {
                                return Observable.fromCallable(() -> new ChatResult(true, null));
                            } else if (from > 0) {
                                queryFrom = from - 1;
                            } else {
                                queryFrom = null;
                            }
                        } else {
                            queryFrom = null;
                        }

                        return checkState().flatMap(client -> client.service().messaging().queryMessages(conversationId, queryFrom, PAGE_SIZE))
                                .flatMap(result -> persistenceController.processMessageQueryResponse(conversationId, result))
                                .flatMap(result -> persistenceController.processOrphanedEvents(result, orphanedEventsToRemoveListener))
                                .map(result -> new ChatResult(result.isSuccessful(), result.isSuccessful() ? null : new ChatResult.Error(result.getCode(), result.getMessage())));
                    }
                });
    }


    /**
     * Gets profile id from Foundation for the active user.
     *
     * @return Active user profile id.
     */
    String getProfileId() {
        final RxComapiClient client = clientReference.get();
        return client != null ? client.getSession().getProfileId() : null;
    }

    /**
     * Check state for all conversations and update from services.
     *
     * @return Result of synchronisation process.
     */
    Observable<Boolean> synchroniseStore() {
        return synchroniseConversations();
    }

    /**
     * Handles conversation create service response.
     *
     * @param result Service call response.
     * @return Observable emitting result of operations.
     */
    Observable<ChatResult> handleConversationCreated(ComapiResult<ConversationDetails> result) {
        if (result.isSuccessful()) {
            return persistenceController.upsertConversation(ChatConversation.builder().populate(result.getResult(), result.getETag()).build()).map(success -> adapter.adaptResult(result, success));
        } else {
            return Observable.fromCallable(() -> adapter.adaptResult(result));
        }
    }

    /**
     * Handles conversation delete service response.
     *
     * @param conversationId Unique identifier of an conversation.
     * @param result         Service call response.
     * @return Observable emitting result of operations.
     */
    Observable<ChatResult> handleConversationDeleted(String conversationId, ComapiResult<Void> result) {
        if (result.getCode() != ETAG_NOT_VALID) {
            return persistenceController.deleteConversation(conversationId).map(success -> adapter.adaptResult(result, success));
        } else {
            return checkState().flatMap(client -> client.service().messaging().getConversation(conversationId)
                    .flatMap(newResult -> {
                        if (newResult.isSuccessful()) {
                            return persistenceController.upsertConversation(ChatConversation.builder().populate(newResult.getResult(), newResult.getETag()).build())
                                    .flatMap(success -> Observable.fromCallable(() -> new ChatResult(false, success ? new ChatResult.Error(ETAG_NOT_VALID, "Conversation updated, try delete again.") : new ChatResult.Error(1500, "Error updating in custom store."))));
                        } else {
                            return Observable.fromCallable(() -> adapter.adaptResult(newResult));
                        }
                    }));
        }
    }

    /**
     * Handles conversation update service response.
     *
     * @param request Service API request object.
     * @param result  Service call response.
     * @return Observable emitting result of operations.
     */
    Observable<ChatResult> handleConversationUpdated(ConversationUpdate request, ComapiResult<ConversationDetails> result) {

        if (result.isSuccessful()) {

            return persistenceController.upsertConversation(ChatConversation.builder().populate(result.getResult(), result.getETag()).build()).map(success -> adapter.adaptResult(result, success));
        }
        if (result.getCode() == ETAG_NOT_VALID) {

            return checkState().flatMap(client -> client.service().messaging().getConversation(request.getId())
                    .flatMap(newResult -> {
                        if (newResult.isSuccessful()) {

                            return persistenceController.upsertConversation(ChatConversation.builder().populate(newResult.getResult(), newResult.getETag()).build())
                                    .flatMap(success -> Observable.fromCallable(() -> new ChatResult(false, success ? new ChatResult.Error(ETAG_NOT_VALID, "Conversation updated, try delete again.") : new ChatResult.Error(1500, "Error updating in custom store."))));
                        } else {
                            return Observable.fromCallable(() -> adapter.adaptResult(newResult));
                        }
                    }));
        } else {

            return Observable.fromCallable(() -> adapter.adaptResult(result));
        }
    }

    /**
     * Handles message status update service response.
     *
     * @param msgStatusList List of message status updates to process.
     * @param result        Service call response.
     * @return Observable emitting result of operations.
     */
    Observable<ChatResult> handleMessageStatusToUpdate(String conversationId, List<MessageStatusUpdate> msgStatusList, ComapiResult<Void> result) {
        if (result.isSuccessful() && msgStatusList != null && !msgStatusList.isEmpty()) {
            return persistenceController.upsertMessageStatuses(conversationId, getProfileId(), msgStatusList).map(success -> adapter.adaptResult(result, success));
        } else {
            return Observable.fromCallable(() -> adapter.adaptResult(result));
        }
    }

    Observable<Boolean> handleMessageSending(String conversationId, MessageToSend message, String tempId) {

        String profileId = getProfileId();
        ChatMessage chatMessage = ChatMessage.builder()
                .setMessageId(tempId)
                .setSentEventId(-1L) // temporary value, will be replaced by persistence controller
                .setConversationId(conversationId)
                .setSentBy(profileId)
                .setFromWhom(new Sender(profileId, profileId))
                .setSentOn(System.currentTimeMillis())
                .setParts(message.getParts())
                .setMetadata(message.getMetadata()).build();

        return persistenceController.updateStoreForNewMessage(chatMessage, noConversationListener);
    }

    /**
     * Handles message send service response.
     *
     * @param conversationId Unique identifier of an conversation.
     * @param message        Service API request object.
     * @param result         Service call response.
     * @return Observable emitting result of operations.
     */
    Observable<ChatResult> handleMessageSent(String conversationId, MessageToSend message, ComapiResult<MessageSentResponse> result) {
        if (result.isSuccessful()) {

            String profileId = getProfileId();
            ChatMessage chatMessage = ChatMessage.builder()
                    .setMessageId(result.getResult().getId())
                    .setSentEventId(result.getResult().getEventId())
                    .setConversationId(conversationId)
                    .setSentBy(profileId)
                    .setFromWhom(new Sender(profileId, profileId))
                    .setSentOn(System.currentTimeMillis())
                    .setParts(message.getParts())
                    .setMetadata(message.getMetadata()).build();

            return persistenceController.updateStoreForNewMessage(chatMessage, noConversationListener).map(success -> adapter.adaptResult(result, success));

        } else {
            return Observable.fromCallable(() -> adapter.adaptResult(result));
        }
    }

    /**
     * Updates all conversation states.
     *
     * @return Result of synchronisation with services.
     */
    private Observable<Boolean> synchroniseConversations() {

        return checkState().flatMap(client -> client.service().messaging()
                .getConversations(false)
                .flatMap(result -> persistenceController.loadAllConversations()
                        .map(chatConversationBases -> compare(result.getResult(), chatConversationBases)))
                .flatMap(this::updateLocalConversationList)
                .flatMap(result -> lookForMissingEvents(client, result))
                .map(result -> result.isSuccessful));
    }

    ConversationComparison compare(List<Conversation> remote, List<ChatConversationBase> local) {
        return new ConversationComparison(adapter.makeMapFromDownloadedConversations(remote), adapter.makeMapFromSavedConversations(local));
    }

    ConversationComparison compare(Long remoteLasetEventId, ChatConversationBase conversation) {
        return new ConversationComparison(remoteLasetEventId, conversation);
    }

    /**
     * Updates single conversation state.
     *
     * @return Result of synchronisation with services.
     */
    Observable<Boolean> synchroniseConversation(String conversationId) {

        return checkState().flatMap(client -> client.service().messaging()
                .queryMessages(conversationId, null, 1)
                .map(result -> {
                    if (result.isSuccessful() && result.getResult() != null) {
                        return (long) result.getResult().getLatestEventId();
                    }
                    return -1L;
                })
                .flatMap(result -> persistenceController.loadConversation(conversationId).map(loaded -> compare(result, loaded)))
                .flatMap(this::updateLocalConversationList)
                .flatMap(result -> lookForMissingEvents(client, result))
                .map(result -> result.isSuccessful));
    }

    /**
     * Checks services for missing events in stored conversations.
     *
     * @param client                 Foundation client.
     * @param conversationComparison Describes differences in local and remote conversation list.
     * @return Observable returning unchanged argument to further processing.
     */
    private Observable<ConversationComparison> lookForMissingEvents(final RxComapiClient client, ConversationComparison conversationComparison) {

        if (!conversationComparison.isSuccessful || conversationComparison.conversationsToUpdate.isEmpty()) {
            return Observable.fromCallable(() -> conversationComparison);
        }

        return synchroniseEvents(client, conversationComparison.conversationsToUpdate, new ArrayList<>())
                .map(result -> {
                    if (conversationComparison.isSuccessful && !result) {
                        conversationComparison.setSuccessful(false);
                    }
                    return conversationComparison;
                });
    }

    /**
     * Update list of local conversations.
     *
     * @param conversationComparison Describes differences in local and remote participant list.
     * @return Observable returning unchanged argument to further processing.
     */
    private Observable<ConversationComparison> updateLocalConversationList(final ConversationComparison conversationComparison) {

        return Observable.zip(persistenceController.deleteConversations(conversationComparison.conversationsToDelete),
                persistenceController.upsertConversations(conversationComparison.conversationsToAdd),
                persistenceController.updateConversations(conversationComparison.conversationsToUpdate),
                (success1, success2, success3) -> success1 && success2 && success3)
                .map(result -> {
                    conversationComparison.setSuccessful(result);
                    return conversationComparison;
                });
    }

    /**
     * Synchronise missing events for the list of locally stored conversations.
     *
     * @param client                Foundation client.
     * @param conversationsToUpdate List of conversations to query last events.
     * @param successes             List of partial successes.
     * @return Observable with the merged result of operations.
     */
    private Observable<Boolean> synchroniseEvents(final RxComapiClient client, final List<ChatConversation> conversationsToUpdate, final List<Boolean> successes) {

        final List<ChatConversation> limited = limitNumberOfConversations(conversationsToUpdate);

        if (limited.isEmpty()) {
            return Observable.fromCallable(() -> true);
        }

        return Observable.from(limited)
                .onBackpressureBuffer()
                .flatMap(conversation -> persistenceController.getConversation(conversation.getConversationId()))
                .flatMap(conversation -> {
                    if (conversation.getLatestRemoteEventId() > conversation.getLastLocalEventId()) {
                        final long from = conversation.getLastLocalEventId() >= 0 ? conversation.getLastLocalEventId() : 0;
                        return queryEventsRecursively(client, conversation.getConversationId(), from, 0, successes).map(ComapiResult::isSuccessful);
                    } else {
                        return Observable.fromCallable((Callable<Object>) () -> true);
                    }
                })
                .flatMap(res -> Observable.from(successes).all(Boolean::booleanValue));
    }

    /**
     * Synchronise missing events for particular conversation.
     *
     * @param client         Foundation client.
     * @param conversationId Unique ID of a conversation.
     * @param lastEventId    Last known event id - query should start form it.
     * @param count          Number of queries already made.
     * @param successes
     * @return Observable with the merged result of operations.
     */
    private Observable<ComapiResult<ConversationEventsResponse>> queryEventsRecursively(final RxComapiClient client, final String conversationId, final long lastEventId, final int count, final List<Boolean> successes) {

        return client.service().messaging().queryConversationEvents(conversationId, lastEventId, UPDATE_FROM_EVENTS_LIMIT)
                .flatMap(new Func1<ComapiResult<ConversationEventsResponse>, Observable<ComapiResult<ConversationEventsResponse>>>() {
                    @Override
                    public Observable<ComapiResult<ConversationEventsResponse>> call(ComapiResult<ConversationEventsResponse> result) {
                        return processEventsQueryResponse(result, successes);
                    }
                })
                .flatMap(new Func1<ComapiResult<ConversationEventsResponse>, Observable<ComapiResult<ConversationEventsResponse>>>() {
                    @Override
                    public Observable<ComapiResult<ConversationEventsResponse>> call(ComapiResult<ConversationEventsResponse> result) {
                        if (result.getResult() != null && result.getResult().getEventsInOrder().size() >= UPDATE_FROM_EVENTS_LIMIT && count < QUERY_EVENTS_NUMBER_OF_CALLS_LIMIT) {
                            return queryEventsRecursively(client, conversationId, lastEventId + result.getResult().getEventsInOrder().size(), count + 1, successes);
                        } else {
                            return Observable.just(result);
                        }
                    }
                });
    }

    private Observable<ComapiResult<ConversationEventsResponse>> processEventsQueryResponse(ComapiResult<ConversationEventsResponse> result, final List<Boolean> successes) {

        ConversationEventsResponse response = result.getResult();
        successes.add(result.isSuccessful());
        if (response != null && response.getEventsInOrder().size() > 0) {

            Collection<Event> events = response.getEventsInOrder();

            List<Observable<Boolean>> list = new ArrayList<>();

            for (Event event : events) {

                if (event instanceof MessageSentEvent) {
                    MessageSentEvent messageEvent = (MessageSentEvent) event;
                    list.add(persistenceController.updateStoreForNewMessage(ChatMessage.builder().populate(messageEvent).build(), noConversationListener));
                } else if (event instanceof MessageDeliveredEvent) {
                    list.add(persistenceController.upsertMessageStatus(new ChatMessageStatus((MessageDeliveredEvent) event)));
                } else if (event instanceof MessageReadEvent) {
                    list.add(persistenceController.upsertMessageStatus(new ChatMessageStatus((MessageReadEvent) event)));
                }
            }

            return Observable.from(list)
                    .flatMap(task -> task)
                    .doOnNext(successes::add)
                    .toList()
                    .map(results -> result);
        }

        return Observable.fromCallable(() -> result);
    }

    private List<ChatConversation> limitNumberOfConversations(List<ChatConversation> conversations) {

        List<ChatConversation> noEmptyConversations = new ArrayList<>();
        for (ChatConversation c : conversations) {
            if (c.getLatestRemoteEventId() != null && c.getLatestRemoteEventId() >= 0) {
                noEmptyConversations.add(c);
            }
        }

        List<ChatConversation> limitedList;

        if (noEmptyConversations.size() < 21) {
            limitedList = noEmptyConversations;
        } else {
            SortedMap<Long, ChatConversation> sorted = new TreeMap<>();
            for (ChatConversation conversation : noEmptyConversations) {
                sorted.put(conversation.getUpdatedOn(), conversation);
            }
            limitedList = new ArrayList<>();
            Object[] array = sorted.values().toArray();
            for (int i = 0; i < 21; i++) {
                limitedList.add((ChatConversation) array[i]);
            }
        }

        return limitedList;
    }

    public Observable<Boolean> handleMessage(final ChatMessage message) {

        String sender = message.getSentBy();

        Observable<Boolean> replaceMessages = persistenceController.updateStoreForNewMessage(message, noConversationListener);

        if (!TextUtils.isEmpty(sender) && !sender.equals(getProfileId())) {

            final Set<String> ids = new HashSet<>();
            ids.add(message.getMessageId());

            return Observable.zip(replaceMessages, markDelivered(message.getConversationId(), ids), (saved, result) -> saved && result.isSuccessful());

        } else {

            return replaceMessages;
        }
    }

    /**
     * Query missing events as {@link com.comapi.chat.internal.MissingEventsTracker} reported.
     *
     * @param conversationId Unique id of a conversation.
     * @param from           Conversation event id to start from.
     * @param limit          Limit of events in a query.
     */
    void queryMissingEvents(String conversationId, long from, int limit) {
        obsExec.execute(checkState().flatMap(client -> client.service().messaging().queryConversationEvents(conversationId, from, limit))
                .flatMap((result) -> processEventsQueryResponse(result, new ArrayList<>())));
    }

    class ConversationComparison {

        boolean isSuccessful = false;

        List<ChatConversation> conversationsToAdd;
        List<ChatConversationBase> conversationsToDelete;
        List<ChatConversation> conversationsToUpdate;

        ConversationComparison(Map<String, Conversation> downloadedList, Map<String, ChatConversationBase> savedList) {

            conversationsToDelete = new ArrayList<>();
            conversationsToUpdate = new ArrayList<>();
            conversationsToAdd = new ArrayList<>();

            Map<String, ChatConversationBase> savedListProcessed = new HashMap<>(savedList);

            for (String key : downloadedList.keySet()) {
                if (savedListProcessed.containsKey(key)) {

                    ChatConversationBase saved = savedListProcessed.get(key);
                    if (saved.getLastLocalEventId() != -1L) {
                        conversationsToUpdate.add(ChatConversation.builder()
                                .populate(downloadedList.get(key))
                                .setFirstLocalEventId(saved.getFirstLocalEventId())
                                .setLastLocalEventId(saved.getLastLocalEventId())
                                .build());
                    }
                } else {
                    conversationsToAdd.add(ChatConversation.builder().populate(downloadedList.get(key)).build());
                }

                savedListProcessed.remove(key);
            }

            if (!savedListProcessed.isEmpty()) {
                conversationsToDelete.addAll(savedListProcessed.values());
            }
        }

        public ConversationComparison(Long remoteLastEventId, ChatConversationBase conversation) {

            conversationsToDelete = new ArrayList<>();
            conversationsToUpdate = new ArrayList<>();
            conversationsToAdd = new ArrayList<>();

            if (conversation != null && remoteLastEventId != null) {
                if (conversation.getLatestRemoteEventId() != null && conversation.getLatestRemoteEventId() != -1L && remoteLastEventId > conversation.getLatestRemoteEventId()) {
                    conversationsToUpdate.add(ChatConversation.builder().populate(conversation).setLatestRemoteEventId(remoteLastEventId).build());
                }
            }
        }

        void setSuccessful(boolean isSuccessful) {
            this.isSuccessful = isSuccessful;
        }
    }
}