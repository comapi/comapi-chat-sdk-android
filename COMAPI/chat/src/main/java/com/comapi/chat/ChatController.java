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

import com.comapi.RxComapiClient;
import com.comapi.chat.model.ChatConversation;
import com.comapi.chat.model.ChatConversationBase;
import com.comapi.chat.model.ChatMessage;
import com.comapi.chat.model.ChatMessageStatus;
import com.comapi.chat.model.ChatParticipant;
import com.comapi.chat.model.ChatStore;
import com.comapi.chat.model.ModelAdapter;
import com.comapi.internal.ComapiException;
import com.comapi.internal.helpers.DateHelper;
import com.comapi.internal.log.Logger;
import com.comapi.internal.network.ComapiResult;
import com.comapi.internal.network.model.conversation.ConversationDetails;
import com.comapi.internal.network.model.conversation.ConversationUpdate;
import com.comapi.internal.network.model.conversation.Participant;
import com.comapi.internal.network.model.conversation.Scope;
import com.comapi.internal.network.model.events.Event;
import com.comapi.internal.network.model.events.conversation.message.MessageDeliveredEvent;
import com.comapi.internal.network.model.events.conversation.message.MessageReadEvent;
import com.comapi.internal.network.model.events.conversation.message.MessageSentEvent;
import com.comapi.internal.network.model.messaging.ConversationEventsResponse;
import com.comapi.internal.network.model.messaging.MessageSentResponse;
import com.comapi.internal.network.model.messaging.MessageStatus;
import com.comapi.internal.network.model.messaging.MessageStatusUpdate;
import com.comapi.internal.network.model.messaging.MessageToSend;
import com.comapi.internal.network.model.messaging.MessagesQueryResponse;
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
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

import static com.comapi.chat.EventsHandler.MESSAGE_METADATA_TEMP_ID;

/**
 * Main controller for Chat Layer specific functionality.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
class ChatController {

    private static final Integer PAGE_SIZE = 100;

    private static final Integer UPDATE_FROM_EVENTS_LIMIT = 100;

    private int QUERY_EVENTS_NUMBER_OF_CALLS_LIMIT = 1000;

    private final WeakReference<RxComapiClient> clientReference;

    private final ModelAdapter adapter;

    private final PersistenceController persistenceController;

    private final ObservableExecutor obsExec;

    private final Logger log;

    private NoConversationListener noConversationListener;

    private OrphanedEventsToRemoveListener orphanedEventsToRemoveListener;

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
                                           return Observable.timer((long) Math.pow(5, retryCount), TimeUnit.SECONDS);
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
                .flatMap(new Func1<Long, Observable<ComapiResult<MessagesQueryResponse>>>() {
                    @Override
                    public Observable<ComapiResult<MessagesQueryResponse>> call(Long from) {

                        return checkState().flatMap(client -> client.service().messaging().queryMessages(conversationId, (from != null && from >= 0) ? from : null, PAGE_SIZE));
                    }
                })
                .flatMap(result -> persistenceController.processMessageQueryResponse(conversationId, result))
                .flatMap(result -> persistenceController.processOrphanedEvents(result, orphanedEventsToRemoveListener))
                .map(result -> new ChatResult(result.isSuccessful(), result.isSuccessful() ? null : new ChatResult.Error(result.getCode(), result.getMessage())));
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

        if (!TextUtils.isEmpty(conversationId)) {
            if (result.getCode() != 412) {
                return persistenceController.deleteConversation(conversationId).map(success -> adapter.adaptResult(result, success));
            } else {
                return checkState().flatMap(client -> client.service().messaging().getConversation(conversationId)
                        .flatMap(newResult -> {
                            if (newResult.isSuccessful()) {
                                return persistenceController.upsertConversation(ChatConversation.builder().populate(newResult.getResult(), newResult.getETag()).build())
                                        .flatMap(success -> Observable.fromCallable(() -> new ChatResult(false, success ? new ChatResult.Error(412, "Conversation updated, try delete again.") : new ChatResult.Error(1500, "Error updating in custom store."))));
                            } else {
                                return Observable.fromCallable(() -> adapter.adaptResult(newResult));
                            }
                        }));
            }
        }

        return Observable.fromCallable(() -> adapter.adaptResult(result));
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
        if (result.getCode() == 412) {

            return checkState().flatMap(client -> client.service().messaging().getConversation(request.getId())
                    .flatMap(newResult -> {
                        if (newResult.isSuccessful()) {

                            return persistenceController.upsertConversation(ChatConversation.builder().populate(newResult.getResult(), newResult.getETag()).build())
                                    .flatMap(success -> Observable.fromCallable(() -> new ChatResult(false, success ? new ChatResult.Error(412, "Conversation updated, try delete again.") : new ChatResult.Error(1500, "Error updating in custom store."))));
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
    Observable<ChatResult> handleMessageStatusToUpdate(List<MessageStatusUpdate> msgStatusList, ComapiResult<Void> result) {
        if (result.isSuccessful() && msgStatusList != null && !msgStatusList.isEmpty()) {
            return persistenceController.upsertMessageStatuses(getProfileId(), msgStatusList).map(success -> adapter.adaptResult(result, success));
        } else {
            return Observable.fromCallable(() -> adapter.adaptResult(result));
        }
    }

    Observable<Boolean> handleMessageSending(String conversationId, MessageToSend message, String tempId) {

        String profileId = getProfileId();
        ChatMessage chatMessage = ChatMessage.builder()
                .setMessageId(tempId)
                .setConversationId(conversationId)
                .setSentBy(profileId)
                .setFromWhom(new Sender(profileId, profileId))
                .setSentOn(System.currentTimeMillis())
                .setParts(message.getParts())
                .setMetadata(message.getMetadata()).build();

        return persistenceController.updateStoreForNewMessage(chatMessage, null, noConversationListener);
    }

    /**
     * Handles message send service response.
     *
     * @param conversationId Unique identifier of an conversation.
     * @param message        Service API request object.
     * @param result         Service call response.
     * @return Observable emitting result of operations.
     */
    Observable<ChatResult> handleMessageSent(String conversationId, MessageToSend message, String tempId, ComapiResult<MessageSentResponse> result) {
        if (result.isSuccessful()) {

            String profileId = getProfileId();
            ChatMessage chatMessage = ChatMessage.builder()
                    .setMessageId(result.getResult().getId())
                    .setConversationId(conversationId)
                    .setSentBy(profileId)
                    .setFromWhom(new Sender(profileId, profileId))
                    .setSentOn(System.currentTimeMillis())
                    .setParts(message.getParts())
                    .setMetadata(message.getMetadata()).build();

            return persistenceController.updateStoreForNewMessage(chatMessage, tempId, noConversationListener).map(success -> adapter.adaptResult(result, success));

        } else {
            return Observable.fromCallable(() -> adapter.adaptResult(result));
        }
    }

    /**
     * Handle add participant service response
     *
     * @param conversationId Unique identifier of an conversation.
     * @param participants   List of participants to process.
     * @param result         Service call response.
     * @return Observable emitting result of operations.
     */
    Observable<ChatResult> handleParticipantsAdded(String conversationId, List<ChatParticipant> participants, ComapiResult<Void> result) {
        if (result.isSuccessful()) {
            return persistenceController.upsertParticipant(conversationId, participants).map(success -> adapter.adaptResult(result, success));
        } else {
            return Observable.fromCallable(() -> adapter.adaptResult(result));
        }
    }

    /**
     * Handle remove participant service response
     *
     * @param conversationId Unique identifier of an conversation.
     * @param ids            List of ids of participants to remove.
     * @param result         Service call response.
     * @return Observable emitting result of operations.
     */
    Observable<ChatResult> handleParticipantsRemoved(@NonNull final String conversationId, @NonNull final List<String> ids, ComapiResult<Void> result) {
        if (result.isSuccessful()) {
            return persistenceController.removeParticipants(conversationId, ids).map(success -> adapter.adaptResult(result, success));
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
                .getConversations(Scope.PARTICIPANT)
                .flatMap(result -> persistenceController.loadAllConversations()
                        .map(chatConversationBases -> compare(result.getResult(), chatConversationBases, result.getETag())))
                .flatMap(this::updateLocalConversationList)
                .flatMap(result -> lookForDiffInParticipantList(client, result))
                .flatMap(result -> lookForMissingEvents(client, result))
                .map(result -> result.isSuccessful));
    }

    ConversationComparison compare(List<ConversationDetails> remote, List<ChatConversationBase> local, String remoteETag) {
        return new ConversationComparison(adapter.makeMapFromDownloadedConversations(remote), remoteETag, adapter.makeMapFromSavedConversations(local));
    }

    ConversationComparison compare(ConversationDetails remote, ChatConversationBase local, String remoteETag) {
        return new ConversationComparison(remote, remoteETag, local);
    }

    /**
     * Updates single conversation state.
     *
     * @return Result of synchronisation with services.
     */
    Observable<Boolean> synchroniseConversation(String conversationId) {

        return checkState().flatMap(client -> client.service().messaging()
                .getConversation(conversationId)
                .flatMap(result -> persistenceController.loadConversation(conversationId).map(loaded -> compare(result.getResult(), loaded, result.getETag())))
                .flatMap(this::updateLocalConversationList)
                .flatMap(result -> lookForDiffInParticipantList(client, result))
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
        return synchroniseEvents(client, conversationComparison.conversationsToUpdate)
                .map(result -> {
                    if (conversationComparison.isSuccessful && !result) {
                        conversationComparison.setSuccessful(false);
                    }
                    return conversationComparison;
                });
    }

    /**
     * Checks services for differences in local/remote participant lists for stored conversations.
     *
     * @param client                 Foundation client.
     * @param conversationComparison Describes differences in local and remote participant list.
     * @return Observable returning unchanged argument to further processing.
     */
    private Observable<ConversationComparison> lookForDiffInParticipantList(final RxComapiClient client, ConversationComparison conversationComparison) {

        return Observable.from(limitNumberOfConversations(conversationComparison.conversationsToUpdate))
                .onBackpressureBuffer()
                .flatMap(conversation -> Observable.zip(client.service().messaging().getParticipants(conversation.getConversationId()),
                        persistenceController.getParticipants(conversation.getConversationId()),
                        (listComapiResult, chatParticipants) -> new ParticipantsComparison(conversation.getConversationId(), adapter.makeMapFromDownloadedParticipants(listComapiResult.getResult()), adapter.makeMapFromSavedParticipants(chatParticipants))))
                .flatMap(new Func1<ParticipantsComparison, Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call(ParticipantsComparison participantsComparison) {
                        return Observable.zip(persistenceController.removeParticipants(participantsComparison.conversationId, participantsComparison.getParticipantsToDeleteIds()),
                                persistenceController.upsertParticipants(participantsComparison.conversationId, participantsComparison.participantsToAdd),
                                (success1, success2) -> success1 && success2);
                    }
                })
                .map(participantsComparison -> conversationComparison);
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
                (success1, success2) -> success1 && success2)
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
     * @return Observable with the merged result of operations.
     */
    private Observable<Boolean> synchroniseEvents(final RxComapiClient client, List<ChatConversation> conversationsToUpdate) {
        return Observable.from(limitNumberOfConversations(conversationsToUpdate))
                .onBackpressureBuffer()
                .flatMap(conversation -> queryEventsRecursively(client, conversation.getConversationId(), conversation.getLastLocalEventId(), 0))
                .all(ComapiResult::isSuccessful);
    }

    /**
     * Synchronise missing events for particular conversation.
     *
     * @param client         Foundation client.
     * @param conversationId Unique ID of a conversation.
     * @param lastEventId    Last known event id - query should start form it.
     * @param count          Number of queries already made.
     * @return Observable with the merged result of operations.
     */
    private Observable<ComapiResult<ConversationEventsResponse>> queryEventsRecursively(final RxComapiClient client, final String conversationId, final long lastEventId, final int count) {

        return client.service().messaging().queryConversationEvents(conversationId, lastEventId, UPDATE_FROM_EVENTS_LIMIT)
                .flatMap(this::processEventsQueryResponse)
                .flatMap(new Func1<ComapiResult<ConversationEventsResponse>, Observable<ComapiResult<ConversationEventsResponse>>>() {
                    @Override
                    public Observable<ComapiResult<ConversationEventsResponse>> call(ComapiResult<ConversationEventsResponse> result) {
                        if (result.getResult() != null && result.getResult().getEventsInOrder().size() >= UPDATE_FROM_EVENTS_LIMIT && count < QUERY_EVENTS_NUMBER_OF_CALLS_LIMIT) {
                            return queryEventsRecursively(client, conversationId, lastEventId + result.getResult().getEventsInOrder().size(), count + 1);
                        } else {
                            return Observable.just(result);
                        }
                    }
                });
    }

    private Observable<ComapiResult<ConversationEventsResponse>> processEventsQueryResponse(ComapiResult<ConversationEventsResponse> result) {

        List<Observable<Boolean>> list = new ArrayList<>();

        ConversationEventsResponse response = result.getResult();
        if (response != null && response.getEventsInOrder().size() > 0) {

            Collection<Event> events = response.getEventsInOrder();

            for (Event event : events) {

                if (event instanceof MessageSentEvent) {
                    MessageSentEvent messageEvent = (MessageSentEvent) event;
                    final String tempId = messageEvent.getMetadata() != null ? (String) messageEvent.getMetadata().get(MESSAGE_METADATA_TEMP_ID) : null;
                    list.add(persistenceController.updateStoreForNewMessage(ChatMessage.builder().populate(messageEvent).build(), tempId, noConversationListener));
                } else if (event instanceof MessageDeliveredEvent) {
                    list.add(persistenceController.upsertMessageStatus(new ChatMessageStatus((MessageDeliveredEvent) event)));
                } else if (event instanceof MessageReadEvent) {
                    list.add(persistenceController.upsertMessageStatus(new ChatMessageStatus((MessageReadEvent) event)));
                }
            }
        }

        if (!list.isEmpty()) {
            Observable<Boolean> task = list.get(0);
            if (list.size() > 1) {
                for (int i = 1; i < list.size(); i++) {
                    int finalI = i;
                    task = task.flatMap(success -> list.get(finalI));
                }
            }
            return task.map(success -> result);
        }

        return Observable.fromCallable(() -> result);
    }

    private List<ChatConversation> limitNumberOfConversations(List<ChatConversation> conversations) {

        List<ChatConversation> limitedList;

        if (conversations.size() < 21) {
            limitedList = conversations;
        } else {
            SortedMap<Long, ChatConversation> sorted = new TreeMap<>();
            for (ChatConversation conversation : conversations) {
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

    public Observable<Boolean> handleMessage(final ChatMessage message, String tempId) {

        String sender = message.getSentBy();

        Observable<Boolean> replaceMessages = persistenceController.updateStoreForNewMessage(message, tempId, noConversationListener);

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
                .flatMap(this::processEventsQueryResponse));
    }

    NoConversationListener getNoConversationListener() {
        return noConversationListener;
    }

    class ConversationComparison {

        boolean isSuccessful = false;

        List<ChatConversation> conversationsToAdd;
        List<ChatConversationBase> conversationsToDelete;
        List<ChatConversation> conversationsToUpdate;

        ConversationComparison(Map<String, ConversationDetails> downloadedList, String eTag, Map<String, ChatConversationBase> savedList) {

            conversationsToDelete = new ArrayList<>();
            conversationsToUpdate = new ArrayList<>();
            conversationsToAdd = new ArrayList<>();

            Map<String, ChatConversationBase> savedListProcessed = new HashMap<>(savedList);

            for (String key : downloadedList.keySet()) {
                if (savedListProcessed.containsKey(key)) {
                    ChatConversationBase saved = savedListProcessed.get(key);
                    conversationsToUpdate.add(ChatConversation.builder()
                            .populate(downloadedList.get(key), eTag)
                            .setFirstLocalEventId(saved.getFirstLocalEventId())
                            .setLastLocalEventId(saved.getLastLocalEventId())
                            .build());
                } else {
                    conversationsToAdd.add(ChatConversation.builder().populate(downloadedList.get(key), eTag).build());
                }

                savedListProcessed.remove(key);
            }

            if (!savedListProcessed.isEmpty()) {
                conversationsToDelete.addAll(savedListProcessed.values());
            }
        }

        ConversationComparison(ConversationDetails result, String eTag, ChatConversationBase loaded) {

            conversationsToDelete = new ArrayList<>();
            conversationsToUpdate = new ArrayList<>();
            conversationsToAdd = new ArrayList<>();

            if (result != null) {
                if (loaded == null) {
                    conversationsToAdd.add(ChatConversation.builder().populate(result, eTag).build());
                } else {
                    conversationsToUpdate.add(ChatConversation.builder().populate(loaded).populate(result, eTag).build());
                }
            } else {
                conversationsToDelete.add(loaded);
            }
        }

        void setSuccessful(boolean isSuccessful) {
            this.isSuccessful = isSuccessful;
        }
    }

    class ParticipantsComparison {

        List<ChatParticipant> participantsToDelete;
        List<ChatParticipant> participantsToAdd;
        String conversationId;

        ParticipantsComparison(String conversationId, Map<String, Participant> downloaded, Map<String, ChatParticipant> saved) {

            participantsToDelete = new ArrayList<>();
            participantsToAdd = new ArrayList<>();
            this.conversationId = conversationId;

            Map<String, Participant> downloadedProcessed = new HashMap<>();

            for (String downloadedId : downloaded.keySet()) {
                if (!saved.containsKey(downloadedId)) {
                    participantsToAdd.add(adapter.adapt(downloaded.get(downloadedId)));
                } else {
                    downloadedProcessed.put(downloadedId, downloaded.get(downloadedId));
                }
            }

            for (String savedId : saved.keySet()) {
                if (!downloadedProcessed.containsKey(savedId)) {
                    participantsToDelete.add(saved.get(savedId));
                }
            }
        }

        List<String> getParticipantsToDeleteIds() {

            if (!participantsToDelete.isEmpty()) {
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < participantsToDelete.size(); i++) {
                    ids.add(participantsToDelete.get(i).getParticipantId());
                }
                return ids;
            } else {
                return null;
            }
        }
    }
}