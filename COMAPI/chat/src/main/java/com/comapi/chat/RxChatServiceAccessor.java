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

import com.comapi.RxComapiClient;
import com.comapi.RxServiceAccessor;
import com.comapi.Session;
import com.comapi.chat.model.ModelAdapter;
import com.comapi.internal.helpers.APIHelper;
import com.comapi.internal.network.ComapiResult;
import com.comapi.internal.network.model.conversation.ConversationCreate;
import com.comapi.internal.network.model.conversation.ConversationUpdate;
import com.comapi.internal.network.model.conversation.Participant;
import com.comapi.internal.network.model.messaging.MessageStatusUpdate;
import com.comapi.internal.network.model.messaging.MessageToSend;

import java.util.List;
import java.util.Map;

import rx.Observable;

/**
 * @author Marcin Swierczek
 * @since 1.0.0
 */
class RxChatServiceAccessor {

    private final RxComapiClient foundation;

    private final ModelAdapter modelAdapter;

    private final MessagingService messagingService;
    private final ProfileService profileService;
    private final SessionService sessionService;

    private final ChatController controller;

    public RxChatServiceAccessor(ModelAdapter modelAdapter, RxComapiClient foundation, ChatController controller) {
        this.modelAdapter = modelAdapter;
        this.foundation = foundation;
        this.controller = controller;
        this.messagingService = new MessagingService();
        this.profileService = new ProfileService();
        this.sessionService = new SessionService();
    }

    /**
     * Access COMAPI Service messaging APIs.
     *
     * @return COMAPI Service messaging APIs.
     */
    public MessagingService messaging() {
        return messagingService;
    }

    /**
     * Access COMAPI Service profile APIs.
     *
     * @return COMAPI Service profile APIs.
     */
    public ProfileService profile() {
        return profileService;
    }

    /**
     * Access COMAPI Service session management APIs.
     *
     * @return COMAPI Service session management APIs.
     */
    public SessionService session() {
        return sessionService;
    }

    public class MessagingService {

        /**
         * Returns observable to create a conversation.
         *
         * @param request  Request with conversation details to create.
         */
        public Observable<ChatResult> createConversation(@NonNull final ConversationCreate request) {
            return foundation.service().messaging().createConversation(request).flatMap(controller::handleConversationCreated);
        }

        /**
         * Returns observable to create a conversation.
         *
         * @param conversationId ID of a conversation to delete.
         */
        public Observable<ChatResult> deleteConversation(@NonNull final String conversationId) {
            return foundation.service().messaging().deleteConversation(conversationId, null /* TODO eTag*/).flatMap(result -> controller.handleConversationDeleted(conversationId, result));
        }

        /**
         * Returns observable to update a conversation.
         *  @param conversationId ID of a conversation to update.
         * @param request        Request with conversation details to update.
         */
        public Observable<ChatResult> updateConversation(@NonNull final String conversationId, @NonNull final ConversationUpdate request) {
            return foundation.service().messaging().updateConversation(conversationId, request, null /* TODO eTag*/).flatMap(result -> controller.handleConversationUpdated(request, result));
        }

        /**
         * Returns observable to remove list of participants from a conversation.
         *  @param conversationId ID of a conversation to delete.
         * @param ids            List of participant ids to be removed.
         */
        public Observable<ChatResult> removeParticipants(@NonNull final String conversationId, @NonNull final List<String> ids) {
            return foundation.service().messaging().removeParticipants(conversationId, ids).flatMap(result -> controller.handleParticipantsRemoved(conversationId, ids, result));
        }

        /**
         * Returns observable to add a list of participants to a conversation.
         *  @param conversationId ID of a conversation to update.
         * @param participants   New conversation participants details.

         */
        public Observable<ChatResult> addParticipants(@NonNull final String conversationId, @NonNull final List<Participant> participants) {
            return foundation.service().messaging().addParticipants(conversationId, participants).flatMap(result -> controller.handleParticipantsAdded(conversationId, modelAdapter.adapt(participants), result));
        }

        /**
         * Send message to the conversation.
         *  @param conversationId ID of a conversation to send a message to.
         * @param message        Message to be send.

         */
        public Observable<ChatResult> sendMessage(@NonNull final String conversationId, @NonNull final MessageToSend message) {
            return foundation.service().messaging().sendMessage(conversationId, message).flatMap(result -> controller.handleMessageSent(conversationId, message, result));
        }

        /**
         * Send message to the chanel.
         *  @param conversationId ID of a conversation to send a message to.
         * @param body           Message body to be send.

         */
        public Observable<ChatResult> sendMessage(@NonNull final String conversationId, @NonNull final String body) {
            final MessageToSend message = APIHelper.createMessage(conversationId, body, controller.getProfileId());
            return foundation.service().messaging().sendMessage(conversationId, body).flatMap((result) -> controller.handleMessageSent(conversationId, message, result));
        }

        /**
         * Sets statuses for sets of messages.
         *  @param conversationId ID of a conversation to modify.
         * @param msgStatusList  List of status modifications.

         */
        public Observable<ChatResult> updateMessageStatus(@NonNull final String conversationId, @NonNull final List<MessageStatusUpdate> msgStatusList) {
            return foundation.service().messaging().updateMessageStatus(conversationId, msgStatusList).flatMap(result -> controller.handleMessageStatusUpdated(msgStatusList, result));
        }

        /**
         * Queries the next message page in conversation and delivers messages to store implementation.
         *
         * @param conversationId ID of a conversation to query messages in.

         */
        public Observable<ChatResult> getPreviousMessages(final String conversationId) {
            return controller.getPreviousMessages(conversationId);
        }

        /**
         * Check for missing messages and other events and update local store.
         */
        public Observable<Boolean> synchroniseStore() {
            return controller.synchroniseStore();
        }

        /**
         * Sends participant is typing in conversation event.
         *  @param conversationId ID of a conversation in which participant is typing a message.
         * @param isTyping       True if user started typing, false if he finished typing.

         */
        public Observable<ChatResult> isTyping(@NonNull final String conversationId, final boolean isTyping) {
            return foundation.service().messaging().isTyping(conversationId, isTyping).map(modelAdapter::adaptResult);
        }
    }

    public class ProfileService implements RxServiceAccessor.ProfileService {

        public Observable<ComapiResult<Map<String, Object>>> getProfile(@NonNull String profileId) {
            return foundation.service().profile().getProfile(profileId);
        }

        public Observable<ComapiResult<List<Map<String, Object>>>> queryProfiles(@NonNull String queryString) {
            return foundation.service().profile().queryProfiles(queryString);
        }

        @Override
        public Observable<ComapiResult<Map<String, Object>>> updateProfile(@NonNull Map<String, Object> profileDetails, String eTag) {
            return foundation.service().profile().updateProfile(profileDetails, eTag);
        }
    }

    public class SessionService {

        /**
         * Create and start new ComapiImpl session.
         */
        public Observable<Session> startSession() {
            return foundation.service().session().startSession();
        }

        /**
         * Ends currently active session.
         */
        public Observable<ChatResult> endSession() {
            return foundation.service().session().endSession().map(modelAdapter::adaptResult);
        }
    }
}