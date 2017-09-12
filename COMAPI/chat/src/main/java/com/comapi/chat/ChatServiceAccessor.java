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
import com.comapi.ServiceAccessor;
import com.comapi.Session;
import com.comapi.chat.model.Attachment;
import com.comapi.chat.model.ChatParticipant;
import com.comapi.internal.CallbackAdapter;
import com.comapi.internal.network.ComapiResult;
import com.comapi.internal.network.model.conversation.ConversationCreate;
import com.comapi.internal.network.model.conversation.ConversationUpdate;
import com.comapi.internal.network.model.conversation.Participant;
import com.comapi.internal.network.model.messaging.MessageToSend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Separates access to subsets of service APIs.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ChatServiceAccessor {

    private final CallbackAdapter callbackAdapter;
    private final MessagingService messagingService;
    private final ServiceAccessor.ProfileService profileService;
    private final SessionService sessionService;

    ChatServiceAccessor(CallbackAdapter callbackAdapter, RxChatServiceAccessor rxChatServiceAccessor) {
        this.callbackAdapter = callbackAdapter;
        this.messagingService = new MessagingService(rxChatServiceAccessor.messaging());
        this.profileService = new ProfileService(rxChatServiceAccessor.profile());
        this.sessionService = new SessionService(rxChatServiceAccessor.session());
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

        private RxChatServiceAccessor.MessagingService rxMessaging;

        private MessagingService(RxChatServiceAccessor.MessagingService rxMessaging) {
            this.rxMessaging = rxMessaging;
        }

        /**
         * Create a conversation.
         *
         * @param request  Request with conversation details to create.
         * @param callback Callback with the result.
         */
        public void createConversation(@NonNull final ConversationCreate request, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(rxMessaging.createConversation(request), callback);
        }

        /**
         * Create a conversation.
         *
         * @param conversationId ID of a conversation to delete.
         * @param callback       Callback with the result.
         */
        public void deleteConversation(@NonNull final String conversationId, @Nullable String eTag, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(rxMessaging.deleteConversation(conversationId, eTag), callback);
        }

        /**
         * Update a conversation.
         *
         * @param conversationId ID of a conversation to update.
         * @param request        Request with conversation details to update.
         * @param callback       Callback with the result.
         */
        public void updateConversation(@NonNull final String conversationId, @Nullable String eTag, @NonNull final ConversationUpdate request, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(rxMessaging.updateConversation(conversationId, eTag, request), callback);
        }

        /**
         * Gets conversation participants.
         *
         * @param conversationId ID of a conversation to query participant list.
         * @param callback       Callback with the result.
         */
        public void getParticipants(@NonNull final String conversationId, @Nullable Callback<List<ChatParticipant>> callback) {
            callbackAdapter.adapt(rxMessaging.getParticipants(conversationId), callback);
        }

        /**
         * Remove list of participants from a conversation.
         *
         * @param conversationId ID of a conversation to delete.
         * @param ids            List of participant ids to be removed.
         * @param callback       Callback with the result.
         */
        public void removeParticipants(@NonNull final String conversationId, @NonNull final List<String> ids, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(rxMessaging.removeParticipants(conversationId, ids), callback);
        }

        /**
         * Add a list of participants to a conversation.
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
            callbackAdapter.adapt(rxMessaging.addParticipants(conversationId, participants), callback);
        }

        /**
         * Send message to the conversation.
         *
         * @param conversationId ID of a conversation to send a message to.
         * @param message        Message to be send.
         * @param callback       Callback with the result.
         */
        public void sendMessage(@NonNull final String conversationId, @NonNull final MessageToSend message, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(rxMessaging.sendMessage(conversationId, message, null), callback);
        }

        /**
         * Send message to the chanel.
         *
         * @param conversationId ID of a conversation to send a message to.
         * @param body           Message body to be send.
         * @param callback       Callback with the result.
         */
        public void sendMessage(@NonNull final String conversationId, @NonNull final String body, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(rxMessaging.sendMessage(conversationId, body, null), callback);
        }

        /**
         * Send message to the conversation.
         *
         * @param conversationId ID of a conversation to send a message to.
         * @param message        Message to be send.
         * @param data           Attachments to the message.
         * @param callback       Callback with the result.
         */
        public void sendMessage(@NonNull final String conversationId, @NonNull final MessageToSend message, @Nullable List<Attachment> data, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(rxMessaging.sendMessage(conversationId, message, data), callback);
        }

        /**
         * Send message to the chanel.
         *
         * @param conversationId ID of a conversation to send a message to.
         * @param body           Message body to be send.
         * @param data           Attachments to the message.
         * @param callback       Callback with the result.
         */
        public void sendMessage(@NonNull final String conversationId, @NonNull final String body, @Nullable List<Attachment> data, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(rxMessaging.sendMessage(conversationId, body, data), callback);
        }

        /**
         * Sets statuses for sets of messages to 'read'.
         *
         * @param conversationId ID of a conversation to modify.
         * @param messageIds     List of message ids for which the status should be updated.
         * @param callback       Callback with the result.
         */
        public void markMessagesAsRead(@NonNull final String conversationId, @NonNull final List<String> messageIds, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(rxMessaging.markMessagesAsRead(conversationId, messageIds), callback);
        }

        /**
         * Queries the next message page in conversation and delivers messages to store implementation.
         *
         * @param conversationId ID of a conversation to query messages in.
         * @param callback       Callback with the result.
         */
        public void getPreviousMessages(final String conversationId, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(rxMessaging.getPreviousMessages(conversationId), callback);
        }

        /**
         * Check for missing messages and other events and update local store.
         *
         * @param callback Callback with the result.
         */
        public void synchroniseStore(@Nullable Callback<Boolean> callback) {
            callbackAdapter.adapt(rxMessaging.synchroniseStore(), callback);
        }

        /**
         * Check for missing messages and other events and update local store.
         *
         * @param conversationId Unique conversationId.
         * @param callback       Callback with the result.
         */
        public void synchroniseConversation(@NonNull final String conversationId, @Nullable Callback<Boolean> callback) {
            callbackAdapter.adapt(rxMessaging.synchroniseConversation(conversationId), callback);
        }

        /**
         * Sends participant is typing in conversation event.
         *
         * @param conversationId ID of a conversation in which participant is typing a message.
         * @param isTyping       True if user started typing, false if he finished typing.
         * @param callback       Callback with the result.
         */
        public void isTyping(@NonNull final String conversationId, final boolean isTyping, @Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(rxMessaging.isTyping(conversationId, isTyping), callback);
        }
    }

    public class ProfileService implements ServiceAccessor.ProfileService {

        private final RxChatServiceAccessor.ProfileService rxProfile;

        private ProfileService(RxChatServiceAccessor.ProfileService rxProfile) {
            this.rxProfile = rxProfile;
        }

        /**
         * Gets profile data.
         *
         * @param profileId Unique profile id.
         * @param callback Callback returning Map of custom profile data.
         */
        @Override
        public void getProfile(@NonNull String profileId, @Nullable Callback<ComapiResult<Map<String, Object>>> callback) {
            callbackAdapter.adapt(rxProfile.getProfile(profileId), callback);
        }

        /**
         * Query user profiles.
         *
         * @param queryString Query string. See https://www.npmjs.com/package/mongo-querystring for query syntax. You can use QueryBuilder helper class to construct valid query string.
         * @param callback    Callback with the result.
         */
        @Override
        public void queryProfiles(@NonNull String queryString, @Nullable Callback<ComapiResult<List<Map<String, Object>>>> callback) {
            callbackAdapter.adapt(rxProfile.queryProfiles(queryString), callback);
        }

        /**
         * Updates profile for an active session.
         *
         * @param profileDetails Profile details.
         * @param callback       Callback with the result.
         */
        @Override
        public void updateProfile(@NonNull Map<String, Object> profileDetails, @Nullable String eTag, @Nullable Callback<ComapiResult<Map<String, Object>>> callback) {
            callbackAdapter.adapt(rxProfile.updateProfile(profileDetails, eTag), callback);
        }

        /**
         * Applies given profile patch if required permission is granted.
         *
         * @param profileDetails Profile details.
         * @param callback       Callback with the result.
         */
        @Override
        public void patchProfile(@NonNull String profileId, @NonNull Map<String, Object> profileDetails, @Nullable String eTag, @Nullable Callback<ComapiResult<Map<String, Object>>> callback) {
            callbackAdapter.adapt(rxProfile.patchProfile(profileId, profileDetails, eTag), callback);
        }

        /**
         * Applies profile patch for an active session.
         *
         * @param profileDetails Profile details.
         * @param eTag           Tag to specify local data version. Can be null.
         * @param callback       Callback with the result.
         */
        @Override
        public void patchMyProfile(@NonNull Map<String, Object> profileDetails, @Nullable String eTag, @Nullable Callback<ComapiResult<Map<String, Object>>> callback) {
            callbackAdapter.adapt(rxProfile.updateProfile(profileDetails, eTag), callback);
        }
    }

    public class SessionService {


        private final RxChatServiceAccessor.SessionService rxSession;

        private SessionService(RxChatServiceAccessor.SessionService rxSession) {
            this.rxSession = rxSession;
        }

        /**
         * Create and start new ComapiImpl session.
         *
         * @param callback Callback with the result.
         */
        public void startSession(@Nullable Callback<Session> callback) {
            callbackAdapter.adapt(rxSession.startSession(), callback);
        }

        /**
         * Ends currently active session.
         *
         * @param callback Callback with the result.
         */
        public void endSession(@Nullable Callback<ChatResult> callback) {
            callbackAdapter.adapt(rxSession.endSession(), callback);
        }
    }
}
