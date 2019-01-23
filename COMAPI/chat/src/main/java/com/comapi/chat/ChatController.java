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
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.comapi.RxComapiClient;
import com.comapi.chat.internal.AttachmentController;
import com.comapi.chat.internal.CallLimiter;
import com.comapi.chat.internal.MessageProcessor;
import com.comapi.chat.model.Attachment;
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
import com.comapi.internal.network.model.messaging.MessagesQueryResponse;

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
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final Integer messagesPerQuery;

    private final Integer eventsPerQuery;

    private final AttachmentController attCon;

    private int maxEventQueries;

    private int maxConversationsSynced;

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

    private final AtomicBoolean isSynchronising;

    private final AtomicBoolean socketWasDisconnected;
    private final CallLimiter syncCallsLimiter;

    /**
     * Recommended constructor.
     *
     * @param client                Comapi client.
     * @param persistenceController Controller over implementation of local conversation and message store.
     * @param internal              Internal SDK configuration.
     * @param log                   Internal logger instance.
     */
    ChatController(final RxComapiClient client, final PersistenceController persistenceController, final AttachmentController attachmentController, InternalConfig internal, final ObservableExecutor obsExec, final ModelAdapter adapter, final Logger log) {
        this.isSynchronising = new AtomicBoolean(false);
        this.clientReference = new WeakReference<>(client);
        this.adapter = adapter;
        this.log = log;
        this.persistenceController = persistenceController;
        this.obsExec = obsExec;
        this.noConversationListener = conversationId -> obsExec.execute(handleNoLocalConversation(conversationId));
        this.orphanedEventsToRemoveListener = ids -> obsExec.execute(persistenceController.deleteOrphanedEvents(ids));
        this.attCon = attachmentController;

        messagesPerQuery = internal.getMaxMessagesPerPage();
        eventsPerQuery = internal.getMaxEventsPerQuery();
        maxEventQueries = internal.getMaxEventQueries();
        maxConversationsSynced = internal.getMaxConversationsSynced();

        this.socketWasDisconnected = new AtomicBoolean(false);
        this.syncCallsLimiter = new CallLimiter(30, 50, TimeUnit.MINUTES, 5, 15, TimeUnit.MINUTES, 60);
    }

    void handleSocketConnected() {
        System.out.println("handleSocketConnected");
        if (socketWasDisconnected.compareAndSet(true, false)) {
            if (syncCallsLimiter.checkAndIncrease() && !TextUtils.isEmpty(getProfileId())) {
                System.out.println("auto synchroniseStore");
                obsExec.execute(synchroniseStore());
            }
        }
    }

    void handleSocketDisconnected() {
        System.out.println("handleSocketConnected");
        socketWasDisconnected.compareAndSet(false, true);
    }

    /**
     * Save and send message with attachments.
     *
     * @param conversationId Unique conversation id.
     * @param message        Message to send
     * @param attachments    List of attachments to send with a message.
     * @return Observable with Chat SDK result.
     */
    Observable<ChatResult> sendMessageWithAttachments(@NonNull final String conversationId, @NonNull final MessageToSend message, @Nullable final List<Attachment> attachments) {

        final MessageProcessor messageProcessor = attCon.createMessageProcessor(message, attachments, conversationId, getProfileId());

        return checkState()
                .flatMap(client ->
                {
                    messageProcessor.preparePreUpload(); // convert fom too large message parts to attachments, adds temp upload parts for all attachments
                    return upsertTempMessage(messageProcessor.createTempMessage()) // create temporary message
                            .flatMap(isOk -> attCon.uploadAttachments(messageProcessor.getAttachments(), client)) // upload attachments
                            .flatMap(uploaded -> {
                                if (uploaded != null && !uploaded.isEmpty()) {
                                    messageProcessor.preparePostUpload(uploaded); // remove temp upload parts, add parts with upload data
                                    return upsertTempMessage(messageProcessor.createTempMessage()); // update message with attachments details like url
                                } else {
                                    return Observable.fromCallable(() -> true);
                                }
                            })
                            .flatMap(isOk -> client.service().messaging().sendMessage(conversationId, messageProcessor.prepareMessageToSend()) // send message with attachments details as additional message parts
                                    .flatMap(result -> result.isSuccessful() ? updateStoreWithSentMsg(messageProcessor, result) : handleMessageError(messageProcessor, new ComapiException(result.getErrorBody()))) // update temporary message with a new message id obtained from the response
                                    .onErrorResumeNext(t -> handleMessageError(messageProcessor, t))); // if error occurred update message status list adding error status
                });
    }

    /**
     * Handle participant added to a conversation Foundation SDK event.
     *
     * @param conversationId Unique conversation id.
     * @return Observable with Chat SDK result.
     */
    public Observable<ChatResult> handleParticipantsAdded(final String conversationId) {
        return persistenceController.getConversation(conversationId).flatMap(conversation -> {
            if (conversation == null) {
                return handleNoLocalConversation(conversationId);
            } else {
                return Observable.fromCallable(() -> new ChatResult(true, null));
            }
        });
    }

    /**
     * Handle failure when sent message.
     *
     * @param mp Message processor holding message sent details.
     * @param t  Thrown exception.
     * @return Observable with Chat SDK result.
     */
    private Observable<ChatResult> handleMessageError(MessageProcessor mp, Throwable t) {
        return persistenceController.updateStoreForSentError(mp.getConversationId(), mp.getTempId(), mp.getSender())
                .map(success -> new ChatResult(false, new ChatResult.Error(0, t)));
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
                .flatMap(result -> {
                    if (result.isSuccessful() && result.getResult() != null) {
                        return persistenceController.upsertConversation(ChatConversation.builder().populate(result.getResult(), result.getETag()).build())
                                .map(success -> new ChatResult(success, success ? null : new ChatResult.Error(0, "External store reported failure.", "Error when inserting conversation "+conversationId)));
                    } else {
                        return Observable.fromCallable(() -> adapter.adaptResult(result));
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
                .retryWhen(observable -> {
                    return observable.zipWith(Observable.range(1, 3), (Func2<Throwable, Integer, Integer>) (throwable, integer) -> integer).flatMap(new Func1<Integer, Observable<Long>>() {
                        @Override
                        public Observable<Long> call(Integer retryCount) {
                            return Observable.timer((long) Math.pow(1, retryCount), TimeUnit.SECONDS);
                        }
                    });
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

        return persistenceController.getConversation(conversationId)
                .map(conversation -> conversation != null ? conversation.getFirstLocalEventId() : null)
                .flatMap(from -> {

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

                    return checkState().flatMap(client -> client.service().messaging().queryMessages(conversationId, queryFrom, messagesPerQuery))
                            .flatMap(result -> persistenceController.processMessageQueryResponse(conversationId, result))
                            .flatMap(result -> persistenceController.processOrphanedEvents(result, orphanedEventsToRemoveListener))
                            .flatMap((Func1<ComapiResult<MessagesQueryResponse>, Observable<ChatResult>>) result -> (result.getResult().getMessages().isEmpty() && result.getResult().getEarliestEventId() > 0) ?
                                    getPreviousMessages(conversationId) :
                                    Observable.fromCallable(() -> new ChatResult(result.isSuccessful(), result.isSuccessful() ? null : new ChatResult.Error(result))));
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
    Observable<ChatResult> synchroniseStore() {
        if (isSynchronising.getAndSet(true)) {
            log.i("Synchronisation in progress.");
            return Observable.fromCallable(() -> new ChatResult(true, null));
        }
        log.i("Synchronising store.");
        return synchroniseConversations()
                .onErrorReturn(t -> new ChatResult(false, new ChatResult.Error(0, t)))
                .doOnNext(i -> {
                    if (i.isSuccessful()) {
                        log.i("Synchronisation successfully finished.");
                    } else {
                        log.e("Synchronisation finished with error. " + (i.getError() != null ? i.getError().getDetails() : ""));
                    }
                    isSynchronising.compareAndSet(true, false);
                });
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
                                    .flatMap(success -> Observable.fromCallable(() -> new ChatResult(false, success ? new ChatResult.Error(ETAG_NOT_VALID, "Conversation updated, try delete again.", "Conversation "+conversationId+" updated in response to  wrong eTag error when deleting.") : new ChatResult.Error(0, "Error updating custom store.", null))));
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
                                    .flatMap(success -> Observable.fromCallable(() -> new ChatResult(false, success ? new ChatResult.Error(ETAG_NOT_VALID, "Conversation updated, try delete again.", "Conversation "+request.getId()+" updated in response to  wrong eTag error when updating."): new ChatResult.Error(1500, "Error updating custom store.", null))));
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

    /**
     * Insert temporary message to the store for the ui to be responsive.
     *
     * @param message Message to be send.
     * @return Observable emitting result of operations.
     */
    private Observable<Boolean> upsertTempMessage(ChatMessage message) {
        return persistenceController.updateStoreWithNewMessage(message, noConversationListener)
                .doOnError(t -> log.e("Error saving temp message " + t.getLocalizedMessage()))
                .onErrorReturn(t -> false);
    }

    /**
     * Handles message send service response. Will delete temporary message object. Same message but with correct message id will be inserted instead.
     *
     * @param mp       Message processor holding message sent details.
     * @param response Service call response.
     * @return Observable emitting result of operations.
     */
    Observable<ChatResult> updateStoreWithSentMsg(MessageProcessor mp, ComapiResult<MessageSentResponse> response) {
        if (response.isSuccessful()) {
            return persistenceController.updateStoreWithNewMessage(mp.createFinalMessage(response.getResult()), noConversationListener).map(success -> adapter.adaptResult(response, success));
        } else {
            return Observable.fromCallable(() -> adapter.adaptResult(response));
        }
    }

    /**
     * Updates all conversation states.
     *
     * @return Result of synchronisation with services.
     */
    private Observable<ChatResult> synchroniseConversations() {

        return checkState().flatMap(client -> client.service().messaging()
                .getConversations(false)
                .flatMap(result -> persistenceController.loadAllConversations()
                        .map(chatConversationBases -> compare(result.isSuccessful(), result.getResult(), chatConversationBases)))
                .flatMap(this::updateLocalConversationList)
                .flatMap(result -> lookForMissingEvents(client, result))
                .map(result -> new ChatResult(result.isSuccessful, null)));
    }

    /**
     * Compares remote and local conversation lists.
     *
     * @param successful True if the service call was successful.
     * @param remote     Conversation list obtained from the server.
     * @param local      Conversation list obtained from local store.
     * @return Comparison object.
     */
    ConversationComparison compare(boolean successful, List<Conversation> remote, List<ChatConversationBase> local) {
        return new ConversationComparison(successful, makeMapFromDownloadedConversations(remote), makeMapFromSavedConversations(local));
    }

    /**
     * Checks local conversation object if it need to be updated.
     *
     * @param remoteLastEventId Id of the last conversation event known by server.
     * @param conversation      Conversation state to check.
     * @return Comparison object.
     */
    ConversationComparison compare(Long remoteLastEventId, ChatConversationBase conversation) {
        return new ConversationComparison(remoteLastEventId, conversation);
    }

    /**
     * Updates single conversation state.
     *
     * @return Result of synchronisation with services.
     */
    Observable<ChatResult> synchroniseConversation(String conversationId) {

        return checkState().flatMap(client -> client.service().messaging()
                .queryMessages(conversationId, null, 1)
                .map(result -> {
                    if (result.isSuccessful() && result.getResult() != null) {
                        return (long) result.getResult().getLatestEventId();
                    }
                    return -1L;
                })
                .flatMap(result -> persistenceController.getConversation(conversationId).map(loaded -> compare(result, loaded)))
                .flatMap(this::updateLocalConversationList)
                .flatMap(result -> lookForMissingEvents(client, result))
                .map(result -> new ChatResult(result.isSuccessful, null)));
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
                        conversationComparison.addSuccess(false);
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
                    conversationComparison.addSuccess(result);
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
    private Observable<Boolean> synchroniseEvents(final RxComapiClient client, @NonNull final List<ChatConversation> conversationsToUpdate, @NonNull final List<Boolean> successes) {

        final List<ChatConversation> limited = limitNumberOfConversations(conversationsToUpdate);

        if (limited.isEmpty()) {
            return Observable.fromCallable(() -> true);
        }

        return Observable.from(limited)
                .onBackpressureBuffer()
                .flatMap(conversation -> persistenceController.getConversation(conversation.getConversationId()))
                .flatMap(conversation -> {
                    if (conversation.getLastRemoteEventId() > conversation.getLastLocalEventId()) {
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
     * @param successes      list of query & processing results in recursive call.
     * @return Observable with the merged result of operations.
     */
    private Observable<ComapiResult<ConversationEventsResponse>> queryEventsRecursively(final RxComapiClient client, final String conversationId, final long lastEventId, final int count, final List<Boolean> successes) {

        return client.service().messaging().queryConversationEvents(conversationId, lastEventId, eventsPerQuery)
                .flatMap(result -> processEventsQueryResponse(result, successes))
                .flatMap(result -> {
                    if (result.getResult() != null && result.getResult().getEventsInOrder().size() >= eventsPerQuery && count < maxEventQueries) {
                        return queryEventsRecursively(client, conversationId, lastEventId + result.getResult().getEventsInOrder().size(), count + 1, successes);
                    } else {
                        return Observable.just(result);
                    }
                });
    }

    /**
     * Process the event query response. Calls appropraiate persistance controller methods for received events.
     *
     * @param result    Event query response.
     * @param successes List of successes in recursive query.
     * @return Observable with same result object for further processing.
     */
    private Observable<ComapiResult<ConversationEventsResponse>> processEventsQueryResponse(ComapiResult<ConversationEventsResponse> result, final List<Boolean> successes) {

        ConversationEventsResponse response = result.getResult();
        successes.add(result.isSuccessful());
        if (response != null && response.getEventsInOrder().size() > 0) {

            Collection<Event> events = response.getEventsInOrder();

            List<Observable<Boolean>> list = new ArrayList<>();

            for (Event event : events) {

                if (event instanceof MessageSentEvent) {
                    MessageSentEvent messageEvent = (MessageSentEvent) event;
                    list.add(persistenceController.updateStoreWithNewMessage(ChatMessage.builder().populate(messageEvent).build(), noConversationListener));
                } else if (event instanceof MessageDeliveredEvent) {
                    list.add(persistenceController.upsertMessageStatus(ChatMessageStatus.builder().populate((MessageDeliveredEvent) event).build()));
                } else if (event instanceof MessageReadEvent) {
                    list.add(persistenceController.upsertMessageStatus(ChatMessageStatus.builder().populate((MessageReadEvent) event).build()));
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

    /**
     * Limits number of conversations to check and synchronise. Emty conversations wont be synchronised. The synchronisation will take place for twenty conversations updated most recently.
     *
     * @param conversations List of conversations to be limited.
     * @return Limited list of conversations to update.
     */
    private List<ChatConversation> limitNumberOfConversations(List<ChatConversation> conversations) {

        List<ChatConversation> noEmptyConversations = new ArrayList<>();
        for (ChatConversation c : conversations) {
            if (c.getLastRemoteEventId() != null && c.getLastRemoteEventId() >= 0) {
                noEmptyConversations.add(c);
            }
        }

        List<ChatConversation> limitedList;

        if (noEmptyConversations.size() <= maxConversationsSynced) {
            limitedList = noEmptyConversations;
        } else {
            SortedMap<Long, ChatConversation> sorted = new TreeMap<>();
            for (ChatConversation conversation : noEmptyConversations) {
                Long updatedOn = conversation.getUpdatedOn();
                if (updatedOn != null) {
                    sorted.put(updatedOn, conversation);
                }
            }
            limitedList = new ArrayList<>();
            Object[] array = sorted.values().toArray();
            for (int i = 0; i < Math.min(maxConversationsSynced, array.length); i++) {
                limitedList.add((ChatConversation) array[i]);
            }
        }

        return limitedList;
    }

    /**
     * Handle incomming message. Replace temporary message with received one and mark as delivered.
     *
     * @param message Message to save and mark delivered.
     * @return Observable with a result.
     */
    public Observable<Boolean> handleMessage(final ChatMessage message) {

        String sender = message.getSentBy();

        Observable<Boolean> replaceMessages = persistenceController.updateStoreWithNewMessage(message, noConversationListener);

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

    /**
     * Creates map of conversations based on conversationId for internal purpose.
     *
     * @param list List of conversations.
     * @return Map of conversations.
     */
    private Map<String, ChatConversationBase> makeMapFromSavedConversations(List<ChatConversationBase> list) {

        Map<String, ChatConversationBase> map = new HashMap<>();

        if (list != null && !list.isEmpty()) {
            for (ChatConversationBase details : list) {
                map.put(details.getConversationId(), details);
            }
        }

        return map;
    }

    /**
     * Creates map of conversations based on conversationId for internal purpose.
     *
     * @param list List of conversations.
     * @return Map of conversations.
     */
    private Map<String, Conversation> makeMapFromDownloadedConversations(List<Conversation> list) {

        Map<String, Conversation> map = new HashMap<>();

        if (list != null && !list.isEmpty()) {
            for (Conversation details : list) {
                map.put(details.getId(), details);
            }
        }

        return map;
    }

    class ConversationComparison {

        boolean remoteCallSuccessful = true;
        boolean isSuccessful = false;

        List<ChatConversation> conversationsToAdd;
        List<ChatConversationBase> conversationsToDelete;
        List<ChatConversation> conversationsToUpdate;

        /**
         * Creates remote and local conversation list comparison.
         *
         * @param successful     Was remote service call successful.
         * @param downloadedList Remote list.
         * @param savedList      Local list.
         */
        ConversationComparison(boolean successful, Map<String, Conversation> downloadedList, Map<String, ChatConversationBase> savedList) {

            remoteCallSuccessful = successful;
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

        /**
         * Checks local conversation if it needs to be updated and creates comaprison object.
         *
         * @param remoteLastEventId Last event id in a conversation known by he server.
         * @param conversation      Conversation object to check.
         */
        public ConversationComparison(Long remoteLastEventId, ChatConversationBase conversation) {

            conversationsToDelete = new ArrayList<>();
            conversationsToUpdate = new ArrayList<>();
            conversationsToAdd = new ArrayList<>();

            if (conversation != null && remoteLastEventId != null) {
                if (conversation.getLastRemoteEventId() != null && conversation.getLastRemoteEventId() != -1L && remoteLastEventId > conversation.getLastRemoteEventId()) {
                    conversationsToUpdate.add(ChatConversation.builder().populate(conversation).setLastRemoteEventId(remoteLastEventId).build());
                }
            }

            if (remoteLastEventId == null || remoteLastEventId == -1L) {
                remoteCallSuccessful = false;
            }
        }

        /**
         * Set if processing of conversations was successful.
         *
         * @param isSuccessful TRue if processing of conversations was successful.
         */
        void addSuccess(boolean isSuccessful) {
            this.isSuccessful = isSuccessful && remoteCallSuccessful;
        }
    }
}