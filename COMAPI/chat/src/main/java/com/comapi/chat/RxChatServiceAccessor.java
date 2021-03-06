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

import com.comapi.QueryBuilder;
import com.comapi.RxComapiClient;
import com.comapi.RxServiceAccessor;
import com.comapi.Session;
import com.comapi.chat.model.Attachment;
import com.comapi.chat.model.ChatParticipant;
import com.comapi.chat.model.ModelAdapter;
import com.comapi.internal.helpers.APIHelper;
import com.comapi.internal.helpers.DateHelper;
import com.comapi.internal.network.ComapiResult;
import com.comapi.internal.network.model.conversation.ConversationCreate;
import com.comapi.internal.network.model.conversation.ConversationDetails;
import com.comapi.internal.network.model.conversation.ConversationUpdate;
import com.comapi.internal.network.model.conversation.Participant;
import com.comapi.internal.network.model.messaging.MessageStatus;
import com.comapi.internal.network.model.messaging.MessageStatusUpdate;
import com.comapi.internal.network.model.messaging.MessageToSend;
import com.comapi.internal.network.model.profile.ComapiProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import rx.Observable;


/**
 * @author Marcin Swierczek
 * @since 1.0.0
 */
@SuppressWarnings("WeakerAccess")
public class RxChatServiceAccessor {

    private final RxComapiClient foundation;

    private final ModelAdapter modelAdapter;

    private final MessagingService messagingService;
    private final ProfileService profileService;
    private final ProfileServiceWithDefaults profileServiceWithDefaults;
    private final SessionService sessionService;

    private final ChatController controller;

    RxChatServiceAccessor(ModelAdapter modelAdapter, RxComapiClient foundation, ChatController controller) {
        this.modelAdapter = modelAdapter;
        this.foundation = foundation;
        this.controller = controller;
        this.messagingService = new MessagingService();
        this.profileService = new ProfileService();
        this.profileServiceWithDefaults = new ProfileServiceWithDefaults();
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
     * This APIs version operates with the raw map of profile key-value pairs.
     * @see this#profileWithDefaults
     *
     * @return COMAPI Service profile APIs.
     */
    public ProfileService profile() {
        return profileService;
    }

    /**
     * Access COMAPI Service profile APIs.
     * This APIs version wraps the raw map of profile key-value pairs in ComapiProfile objects that introduces default keys that can be understood by the Comapi Portal.
     *
     * @return COMAPI Service profile APIs.
     */
    public ProfileServiceWithDefaults profileWithDefaults() {
        return profileServiceWithDefaults;
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

        private MessagingService() {

        }

        /**
         * Returns observable to create a conversation.
         *
         * @param request Request with conversation details to create.
         * @return Observable to subscribe to.
         */
        public Observable<ChatResult> createConversation(@NonNull final ConversationCreate request) {
            return foundation.service().messaging().getConversation(request.getId())
                    .flatMap(result -> {
                        if (result.isSuccessful() && result.getResult() != null) {
                            return Observable.fromCallable(() -> new ChatResult(false, new ChatResult.Error(409, "Conversation already exist.", null)));
                        } else {
                            return foundation.service().messaging().createConversation(request).flatMap(controller::handleConversationCreated);
                        }
                    });
        }

        public Observable<ComapiResult<ConversationDetails>> getConversation(@NonNull final String conversationId) {
            return foundation.service().messaging().getConversation(conversationId);
        }

        /**
         * Returns observable to create a conversation.
         *
         * @param conversationId ID of a conversation to delete.
         * @return Observable to subscribe to.
         */
        public Observable<ChatResult> deleteConversation(@NonNull final String conversationId, @Nullable String eTag) {
            return foundation.service().messaging().deleteConversation(conversationId, eTag).flatMap(result -> controller.handleConversationDeleted(conversationId, result));
        }

        /**
         * Returns observable to update a conversation.
         *
         * @param conversationId ID of a conversation to update.
         * @param request        Request with conversation details to update.
         * @return Observable to subscribe to.
         */
        public Observable<ChatResult> updateConversation(@NonNull final String conversationId, @Nullable String eTag, @NonNull final ConversationUpdate request) {
            return foundation.service().messaging().updateConversation(conversationId, request, eTag).flatMap(result -> controller.handleConversationUpdated(request, result));
        }

        /**
         * Gets conversation participants.
         *
         * @param conversationId ID of a conversation to query participant list.
         * @return Observable to get a list of conversation participants.
         */
        public Observable<List<ChatParticipant>> getParticipants(@NonNull final String conversationId) {
            return foundation.service().messaging().getParticipants(conversationId).map(result -> modelAdapter.adapt(result.getResult()));
        }

        /**
         * Returns observable to remove list of participants from a conversation.
         *
         * @param conversationId ID of a conversation to delete.
         * @param ids            List of participant ids to be removed.
         * @return Observable to subscribe to.
         */
        public Observable<ChatResult> removeParticipants(@NonNull final String conversationId, @NonNull final List<String> ids) {
            return foundation.service().messaging().removeParticipants(conversationId, ids).map(modelAdapter::adaptResult);
        }

        /**
         * Returns observable to add a list of participants to a conversation.
         *
         * @param conversationId ID of a conversation to update.
         * @param participants   New conversation participants details.
         * @return Observable to subscribe to.
         */
        public Observable<ChatResult> addParticipants(@NonNull final String conversationId, @NonNull final List<Participant> participants) {
            return foundation.service().messaging().addParticipants(conversationId, participants)
                    .flatMap(result -> controller.handleParticipantsAdded(conversationId).map(conversation -> result))
                    .map(modelAdapter::adaptResult);
        }

        /**
         * Send message to the conversation.
         *
         * @param conversationId ID of a conversation to send a message to.
         * @param message        Message to be send.
         * @return Observable to subscribe to.
         */
        public Observable<ChatResult> sendMessage(@NonNull final String conversationId, @NonNull final MessageToSend message) {
            return doSendMessage(conversationId, message, null);
        }

        /**
         * Send message to the chanel.
         *
         * @param conversationId ID of a conversation to send a message to.
         * @param body           Message body to be send.
         * @return Observable to subscribe to.
         */
        public Observable<ChatResult> sendMessage(@NonNull final String conversationId, @NonNull final String body) {
            final MessageToSend message = APIHelper.createMessage(conversationId, body, controller.getProfileId());
            return doSendMessage(conversationId, message, null);
        }

        /**
         * Send message to the conversation.
         *
         * @param conversationId ID of a conversation to send a message to.
         * @param message        Message to be send.
         * @param data           Attachments to the message.
         * @return Observable to subscribe to.
         */
        public Observable<ChatResult> sendMessage(@NonNull final String conversationId, @NonNull final MessageToSend message, @Nullable List<Attachment> data) {
            return doSendMessage(conversationId, message, data);
        }

        /**
         * Send message to the chanel.
         *
         * @param conversationId ID of a conversation to send a message to.
         * @param body           Message body to be send.
         * @param data           Attachments to the message.
         * @return Observable to subscribe to.
         */
        public Observable<ChatResult> sendMessage(@NonNull final String conversationId, @NonNull final String body, @Nullable List<Attachment> data) {
            final MessageToSend message = APIHelper.createMessage(conversationId, body, controller.getProfileId());
            return doSendMessage(conversationId, message, data);
        }

        /**
         * Send message to the chanel.
         *
         * @param conversationId ID of a conversation to send a message to.
         * @param message        Message to be send.
         * @param attachments    Attachments to the message.
         * @return Observable to subscribe to.
         */
        private Observable<ChatResult> doSendMessage(@NonNull final String conversationId, @NonNull final MessageToSend message, @Nullable List<Attachment> attachments) {
            return controller.sendMessageWithAttachments(conversationId, message, attachments);
        }

        /**
         * Sets statuses for sets of messages to 'read'.
         *
         * @param conversationId ID of a conversation to modify.
         * @param messageIds     List of message ids for which the status should be updated.
         * @return Observable to subscribe to.
         */
        public Observable<ChatResult> markMessagesAsRead(@NonNull final String conversationId, @NonNull final List<String> messageIds) {

            List<MessageStatusUpdate> statuses = new ArrayList<>();
            MessageStatusUpdate.Builder updateBuilder = MessageStatusUpdate.builder();
            for (String id : messageIds) {
                updateBuilder.addMessageId(id);
            }
            updateBuilder.setStatus(MessageStatus.read).setTimestamp(DateHelper.getCurrentUTC());
            statuses.add(updateBuilder.build());

            return foundation.service().messaging().updateMessageStatus(conversationId, statuses).flatMap(result -> controller.handleMessageStatusToUpdate(conversationId, statuses, result));
        }

        /**
         * Queries the next message page in conversation and delivers messages to store implementation.
         *
         * @param conversationId ID of a conversation to query messages in.
         * @return Observable to subscribe to.
         */
        public Observable<ChatResult> getPreviousMessages(final String conversationId) {
            return controller.getPreviousMessages(conversationId);
        }

        /**
         * Check for missing messages and other events and update local store.
         *
         * @return Observable to subscribe to.
         */
        public Observable<ChatResult> synchroniseStore() {
            return controller.synchroniseStore();
        }

        /**
         * Check for missing messages and other events and update local store.
         *
         * @param conversationId Unique conversationId.
         * @return Observable to subscribe to.
         */
        public Observable<ChatResult> synchroniseConversation(@NonNull final String conversationId) {
            return controller.synchroniseConversation(conversationId);
        }

        /**
         * Sends participant is typing in conversation event.
         *
         * @param conversationId ID of a conversation in which participant is typing a message.
         * @param isTyping       True if user started typing, false if he finished typing.
         * @return Observable to subscribe to.
         */
        public Observable<ChatResult> isTyping(@NonNull final String conversationId, final boolean isTyping) {
            return foundation.service().messaging().isTyping(conversationId, isTyping).map(modelAdapter::adaptResult);
        }
    }

    public class ProfileService implements RxServiceAccessor.ProfileService {

        private ProfileService() {

        }

        /**
         * Gets profile data.
         *
         * @param profileId Unique profile id.
         * @return Map of custom profile data.
         */
        @Override
        public Observable<ComapiResult<Map<String, Object>>> getProfile(@NonNull String profileId) {
            return foundation.service().profile().getProfile(profileId);
        }

        /**
         * Query user profiles.
         *
         * @param queryString Query string. See https://www.npmjs.com/package/mongo-querystring for query syntax. You can use com.comapi.QueryBuilder helper class to construct valid query string.
         * @return List of maps of custom profile data.
         */
        @Override
        public Observable<ComapiResult<List<Map<String, Object>>>> queryProfiles(@NonNull String queryString) {
            return foundation.service().profile().queryProfiles(queryString);
        }

        /**
         * Updates profile for an active session.
         *
         * @param profileDetails Profile details.
         * @return Observable with to perform update profile for current session.
         */
        @Override
        public Observable<ComapiResult<Map<String, Object>>> updateProfile(@NonNull Map<String, Object> profileDetails, @Nullable String eTag) {
            return foundation.service().profile().updateProfile(profileDetails, eTag);
        }

        /**
         * Applies given profile patch if required permission is granted.
         *
         * @param profileDetails Profile details.
         * @return Observable with to perform patch profile for current session.
         */
        @Override
        public Observable<ComapiResult<Map<String, Object>>> patchProfile(@NonNull String profileId, @NonNull Map<String, Object> profileDetails, @Nullable String eTag) {
            return foundation.service().profile().patchProfile(profileId, profileDetails, eTag);
        }

        /**
         * Applies profile patch for an active session.
         *
         * @param profileDetails Profile details.
         * @param eTag           Tag to specify local data version. Can be null.
         * @return Observable with to perform patch profile for current session.
         */
        @Override
        public Observable<ComapiResult<Map<String, Object>>> patchMyProfile(@NonNull Map<String, Object> profileDetails, @Nullable String eTag) {
            return foundation.service().profile().patchMyProfile(profileDetails, eTag);
        }
    }

    public class ProfileServiceWithDefaults implements  com.comapi.RxServiceAccessor.ProfileServiceWithDefaults {

        /**
         * Get profile details from the service.
         *
         * @param profileId Profile Id of the user.
         * @return Profile details from the service.
         */
        public Observable<ComapiResult<ComapiProfile>> getProfile(@NonNull final String profileId) {
            return foundation.service().profileWithDefaults().getProfile(profileId);
        }

        /**
         * Query user profiles on the services.
         *
         * @param queryString Query string. See https://www.npmjs.com/package/mongo-querystring for query syntax. You can use {@link QueryBuilder} helper class to construct valid query string.
         * @return Profiles detail from the service.
         */
        public Observable<ComapiResult<List<ComapiProfile>>> queryProfiles(@NonNull final String queryString) {
            return foundation.service().profileWithDefaults().queryProfiles(queryString);
        }

        /**
         * Updates profile for an active session.
         *
         * @param profileDetails Profile details.
         * @return Observable with to perform update profile for current session.
         */
        public Observable<ComapiResult<ComapiProfile>> updateProfile(@NonNull final ComapiProfile profileDetails, final String eTag) {
            return foundation.service().profileWithDefaults().updateProfile(profileDetails, eTag);
        }

        /**
         * Applies given profile patch if required permission is granted.
         *
         * @param profileDetails Profile details.
         * @return Observable with to perform patch profile for current session.
         */
        public Observable<ComapiResult<ComapiProfile>> patchProfile(@NonNull String profileId, @NonNull final ComapiProfile profileDetails, final String eTag) {
            return foundation.service().profileWithDefaults().patchProfile(profileId, profileDetails, eTag);
        }

        /**
         * Applies profile patch for an active session.
         *
         * @param profileDetails Profile details.
         * @return Observable with to perform patch profile for current session.
         */
        public Observable<ComapiResult<ComapiProfile>> patchMyProfile(@NonNull final ComapiProfile profileDetails, final String eTag) {
            return foundation.service().profileWithDefaults().patchMyProfile(profileDetails, eTag);
        }
    }

    public class SessionService {

        private SessionService() {

        }

        /**
         * Create and start new Comapi session.
         *
         * @return Observable to subscribe to. Returns Session details.
         */
        public Observable<Session> startSession() {
            return foundation.service().session().startSession();
        }

        /**
         * Ends currently active session.
         *
         * @return Observable to subscribe to.
         */
        public Observable<ChatResult> endSession() {
            return foundation.service().session().endSession().map(modelAdapter::adaptResult);
        }
    }
}
