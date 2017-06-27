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

import com.comapi.Callback;
import com.comapi.RxComapiClient;
import com.comapi.ServiceAccessor;
import com.comapi.Session;
import com.comapi.chat.model.ChatParticipant;
import com.comapi.chat.model.ModelAdapter;
import com.comapi.internal.CallbackAdapter;
import com.comapi.internal.helpers.APIHelper;
import com.comapi.internal.helpers.DateHelper;
import com.comapi.internal.network.ComapiResult;
import com.comapi.internal.network.model.conversation.ConversationCreate;
import com.comapi.internal.network.model.conversation.ConversationUpdate;
import com.comapi.internal.network.model.conversation.Participant;
import com.comapi.internal.network.model.messaging.MessageStatus;
import com.comapi.internal.network.model.messaging.MessageStatusUpdate;
import com.comapi.internal.network.model.messaging.MessageToSend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.comapi.chat.EventsHandler.MESSAGE_METADATA_TEMP_ID;

/**
 * Separates access to subsets of service APIs.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ChatServiceAccessor {

    private final RxComapiClient foundation;

    private final CallbackAdapter callbackAdapter;
    private final ModelAdapter modelAdapter;

    private final MessagingService messagingService;
    private final ServiceAccessor.ProfileService profileService;
    private final SessionService sessionService;

    private final ChatController controller;

    ChatServiceAccessor(ModelAdapter modelAdapter, CallbackAdapter callbackAdapter, RxComapiClient foundation, ChatController controller) {
        this.modelAdapter = modelAdapter;
        this.callbackAdapter = callbackAdapter;
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
    public ServiceAccessor.ProfileService profile() {
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
         * @param callback Callback with the result.
         */
        public void createConversation(@NonNull final ConversationCreate request, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(foundation.service().messaging().createConversation(request).flatMap(controller::handleConversationCreated), callback);
        }

        /**
         * Returns observable to create a conversation.
         *
         * @param conversationId ID of a conversation to delete.
         * @param callback       Callback with the result.
         */
        public void deleteConversation(@NonNull final String conversationId, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(foundation.service().messaging().deleteConversation(conversationId, null /* TODO eTag*/).flatMap(result -> controller.handleConversationDeleted(conversationId, result)), callback);
        }

        /**
         * Returns observable to update a conversation.
         *
         * @param conversationId ID of a conversation to update.
         * @param request        Request with conversation details to update.
         * @param callback       Callback with the result.
         */
        public void updateConversation(@NonNull final String conversationId, @NonNull final ConversationUpdate request, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(foundation.service().messaging().updateConversation(conversationId, request, null /* TODO eTag*/).flatMap(result -> controller.handleConversationUpdated(request, result)), callback);
        }

        /**
         * Returns observable to remove list of participants from a conversation.
         *
         * @param conversationId ID of a conversation to delete.
         * @param ids            List of participant ids to be removed.
         * @param callback       Callback with the result.
         */
        public void removeParticipants(@NonNull final String conversationId, @NonNull final List<String> ids, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(foundation.service().messaging().removeParticipants(conversationId, ids).flatMap(result -> controller.handleParticipantsRemoved(conversationId, ids, result)), callback);
        }

        /**
         * Returns observable to add a list of participants to a conversation.
         *
         * @param conversationId ID of a conversation to update.
         * @param participants   New conversation participants details.
         * @param callback       Callback with the result.
         */
        public void addParticipants(@NonNull final String conversationId, @NonNull final List<Participant> participants, @Nullable Callback<ChatResult> callback) {
            List<ChatParticipant> part = new ArrayList<>();
            for (Participant participant : participants) {
                part.add(ChatParticipant.builder().populate(participant).build());
            }
            callbackAdapter.adapt(foundation.service().messaging().addParticipants(conversationId, participants).flatMap(result -> controller.handleParticipantsAdded(conversationId, part, result)), callback);
        }

        /**
         * Send message to the conversation.
         *
         * @param conversationId ID of a conversation to send a message to.
         * @param message        Message to be send.
         * @param callback       Callback with the result.
         */
        public void sendMessage(@NonNull final String conversationId, @NonNull final MessageToSend message, @Nullable Callback<ChatResult> callback) {
            final String tempId = UUID.randomUUID().toString();
            message.addMetadata(MESSAGE_METADATA_TEMP_ID, tempId);
            callbackAdapter.adapt(controller.handleMessageSending(conversationId, message, tempId)
                            .flatMap(initResult -> foundation.service().messaging().sendMessage(conversationId, message))
                            .flatMap(result -> controller.handleMessageSent(conversationId, message, tempId, result))
                    , callback);
        }

        /**
         * Send message to the chanel.
         *
         * @param conversationId ID of a conversation to send a message to.
         * @param body           Message body to be send.
         * @param callback       Callback with the result.
         */
        public void sendMessage(@NonNull final String conversationId, @NonNull final String body, @Nullable Callback<ChatResult> callback) {
            final MessageToSend message = APIHelper.createMessage(conversationId, body, controller.getProfileId());
            final String tempId = UUID.randomUUID().toString();
            message.addMetadata(MESSAGE_METADATA_TEMP_ID, tempId);
            callbackAdapter.adapt(controller.handleMessageSending(conversationId, message, tempId)
                            .flatMap(initResult -> foundation.service().messaging().sendMessage(conversationId, body))
                            .flatMap((result) -> controller.handleMessageSent(conversationId, message, tempId, result))
                    , callback);
        }

        /**
         * Sets statuses for sets of messages to 'read'.
         *
         * @param conversationId ID of a conversation to modify.
         * @param messageIds     List of message ids for which the status should be updated.
         * @param callback       Callback with the result.
         */
        public void markMessagesAsRead(@NonNull final String conversationId, @NonNull final List<String> messageIds, @Nullable Callback<ChatResult> callback) {

            List<MessageStatusUpdate> statuses = new ArrayList<>();
            MessageStatusUpdate.Builder updateBuilder = MessageStatusUpdate.builder();
            for (String id : messageIds) {
                updateBuilder.addMessageId(id);
            }
            updateBuilder.setStatus(MessageStatus.read).setTimestamp(DateHelper.getCurrentUTC());
            statuses.add(updateBuilder.build());

            callbackAdapter.adapt(foundation.service().messaging().updateMessageStatus(conversationId, statuses).flatMap(result -> controller.handleMessageStatusToUpdate(statuses, result)), callback);
        }

        /**
         * Queries the next message page in conversation and delivers messages to store implementation.
         *
         * @param conversationId ID of a conversation to query messages in.
         * @param callback       Callback with the result.
         */
        public void getPreviousMessages(final String conversationId, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(controller.getPreviousMessages(conversationId), callback);
        }

        /**
         * Check for missing messages and other events and update local store.
         *
         * @param callback Callback with the result.
         */
        public void synchroniseStore(@Nullable Callback<Boolean> callback) {
            callbackAdapter.adapt(controller.synchroniseStore(), callback);
        }

        /**
         * Sends participant is typing in conversation event.
         *
         * @param conversationId ID of a conversation in which participant is typing a message.
         * @param isTyping       True if user started typing, false if he finished typing.
         * @param callback       Callback with the result.
         */
        public void isTyping(@NonNull final String conversationId, final boolean isTyping, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(foundation.service().messaging().isTyping(conversationId, isTyping).map(modelAdapter::adaptResult), callback);
        }
    }

    public class ProfileService implements ServiceAccessor.ProfileService {

        @Override
        public void getProfile(@NonNull String profileId, @Nullable Callback<ComapiResult<Map<String, Object>>> callback) {
            callbackAdapter.adapt(foundation.service().profile().getProfile(profileId), callback);
        }

        @Override
        public void queryProfiles(@NonNull String queryString, @Nullable Callback<ComapiResult<List<Map<String, Object>>>> callback) {
            callbackAdapter.adapt(foundation.service().profile().queryProfiles(queryString), callback);
        }

        @Override
        public void updateProfile(@NonNull Map<String, Object> profileDetails, String eTag, @Nullable Callback<ComapiResult<Map<String, Object>>> callback) {
            callbackAdapter.adapt(foundation.service().profile().updateProfile(profileDetails, eTag), callback);
        }

        @Override
        public void patchProfile(@NonNull String profileId, @NonNull Map<String, Object> profileDetails, String eTag, @Nullable Callback<ComapiResult<Map<String, Object>>> callback) {
            callbackAdapter.adapt(foundation.service().profile().patchProfile(profileId, profileDetails, eTag), callback);
        }

        @Override
        public void patchMyProfile(@NonNull Map<String, Object> profileDetails, String eTag, @Nullable Callback<ComapiResult<Map<String, Object>>> callback) {
            callbackAdapter.adapt(foundation.service().profile().updateProfile(profileDetails, eTag), callback);
        }
    }

    public class SessionService {

        /**
         * Create and start new ComapiImpl session.
         *
         * @param callback Callback with the result.
         */
        public void startSession(@Nullable Callback<Session> callback) {
            callbackAdapter.adapt(foundation.service().session().startSession(), callback);
        }

        /**
         * Ends currently active session.
         *
         * @param callback Callback with the result.
         */
        public void endSession(@Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(foundation.service().session().endSession().map(modelAdapter::adaptResult), callback);
        }
    }
}
