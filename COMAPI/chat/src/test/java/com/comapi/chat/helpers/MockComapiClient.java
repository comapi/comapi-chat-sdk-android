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

package com.comapi.chat.helpers;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.comapi.GlobalState;
import com.comapi.MessagingListener;
import com.comapi.ProfileListener;
import com.comapi.RxComapiClient;
import com.comapi.RxServiceAccessor;
import com.comapi.Session;
import com.comapi.chat.ChatConfig;
import com.comapi.chat.listeners.ParticipantsListener;
import com.comapi.chat.listeners.TypingListener;
import com.comapi.internal.data.SessionData;
import com.comapi.internal.log.LogManager;
import com.comapi.internal.log.Logger;
import com.comapi.internal.network.ComapiResult;
import com.comapi.internal.network.api.RxComapiService;
import com.comapi.internal.network.model.conversation.Conversation;
import com.comapi.internal.network.model.conversation.ConversationCreate;
import com.comapi.internal.network.model.conversation.ConversationDetails;
import com.comapi.internal.network.model.conversation.ConversationUpdate;
import com.comapi.internal.network.model.conversation.Participant;
import com.comapi.internal.network.model.conversation.Scope;
import com.comapi.internal.network.model.events.Event;
import com.comapi.internal.network.model.events.ProfileUpdateEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantAddedEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantRemovedEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantTypingEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantTypingOffEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantUpdatedEvent;
import com.comapi.internal.network.model.messaging.ConversationEventsResponse;
import com.comapi.internal.network.model.messaging.EventsQueryResponse;
import com.comapi.internal.network.model.messaging.MessageSentResponse;
import com.comapi.internal.network.model.messaging.MessageStatusUpdate;
import com.comapi.internal.network.model.messaging.MessageToSend;
import com.comapi.internal.network.model.messaging.MessagesQueryResponse;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import rx.Observable;

/**
 * @author Marcin Swierczek
 * @since 1.0.0
 */
@SuppressWarnings("unchecked")
public class MockComapiClient extends RxComapiClient {

    private LinkedBlockingQueue<ComapiResult<?>> results;

    private ProfileListener testProfileListener;
    private MessagingListener testMessagingListener;

    private com.comapi.chat.listeners.ProfileListener configTestProfileListener;
    private ParticipantsListener configTestMessagingListener;
    private TypingListener configTypingListener;

    MockComapiClient(ChatConfig config, MockFoundationFactory.ConfigAdapter configAdapter) {
        super(configAdapter.adapt(config));

        try {
            Method method = config.getClass().getDeclaredMethod("getParticipantsListener");
            method.setAccessible(true);
            configTestMessagingListener = (ParticipantsListener) (method.invoke(config));

            method = config.getClass().getDeclaredMethod("getProfileListener");
            method.setAccessible(true);
            configTestProfileListener = (com.comapi.chat.listeners.ProfileListener) (method.invoke(config));

            method = config.getClass().getDeclaredMethod("getTypingListener");
            method.setAccessible(true);
            configTypingListener = (TypingListener) (method.invoke(config));

        } catch (Exception e) {
            e.printStackTrace();
        }

        results = new LinkedBlockingQueue<>();
    }

    @Override
    public int getState() {
        return GlobalState.SESSION_ACTIVE;
    }

    @Override
    public Session getSession() {
        return new Session(new SessionData().setAccessToken(ChatTestConst.TOKEN).setExpiresOn(Long.MAX_VALUE).setProfileId(ChatTestConst.PROFILE_ID).setSessionId(ChatTestConst.SESSION_ID));
    }

    public RxServiceAccessor service() {

        return new RxServiceAccessor(null) {

            /**
             * Access COMAPI Service messaging APIs.
             *
             * @return COMAPI Service messaging APIs.
             */
            public MessagingService messaging() {

                return new MessagingService() {

                    @Override
                    public Observable<ComapiResult<ConversationDetails>> createConversation(@NonNull ConversationCreate request) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof ConversationDetails)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<ConversationDetails>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<Void>> deleteConversation(@NonNull String conversationId, String eTag) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<Void>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<ConversationDetails>> getConversation(@NonNull String conversationId) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof MockConversationDetails)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<ConversationDetails>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<List<ConversationDetails>>> getConversations(@NonNull Scope scope) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof List)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<List<ConversationDetails>>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<List<Conversation>>> getConversations(boolean isPublic) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof List)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<List<Conversation>>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<ConversationDetails>> updateConversation(@NonNull String conversationId, @NonNull ConversationUpdate request, @Nullable String eTag) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof MockConversationDetails)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<ConversationDetails>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<Void>> removeParticipants(@NonNull String conversationId, @NonNull List<String> ids) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<Void>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<List<Participant>>> getParticipants(@NonNull String conversationId) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof List)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<List<Participant>>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<Void>> addParticipants(@NonNull String conversationId, @NonNull List<Participant> participants) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<Void>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<MessageSentResponse>> sendMessage(@NonNull String conversationId, @NonNull MessageToSend message) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof MessageSentResponse)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<MessageSentResponse>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<MessageSentResponse>> sendMessage(@NonNull String conversationId, @NonNull String body) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof MessageSentResponse)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<MessageSentResponse>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<Void>> updateMessageStatus(@NonNull String conversationId, @NonNull List<MessageStatusUpdate> msgStatusList) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else if (result.getCode() == 500) {
                                throw new Exception("Mocked server error.");
                            } else {
                                return (ComapiResult<Void>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<EventsQueryResponse>> queryEvents(@NonNull String conversationId, @NonNull Long from, @NonNull Integer limit) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof EventsQueryResponse)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<EventsQueryResponse>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<ConversationEventsResponse>> queryConversationEvents(@NonNull String conversationId, @NonNull Long from, @NonNull Integer limit) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof ConversationEventsResponse)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<ConversationEventsResponse>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<MessagesQueryResponse>> queryMessages(@NonNull String conversationId, Long from, @NonNull Integer limit) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof MessagesQueryResponse)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<MessagesQueryResponse>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<Void>> isTyping(@NonNull String conversationId) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            return (ComapiResult<Void>) result;
                        });
                    }

                    @Override
                    public Observable<ComapiResult<Void>> isTyping(@NonNull String conversationId, boolean isTyping) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            return (ComapiResult<Void>) result;
                        });
                    }
                };
            }

            /**
             * Access COMAPI Service profile APIs.
             *
             * @return COMAPI Service profile APIs.
             */
            public ProfileService profile() {
                return new ProfileService() {
                    @Override
                    public Observable<ComapiResult<Map<String, Object>>> getProfile(@NonNull String profileId) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof Map)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<Map<String, Object>>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<List<Map<String, Object>>>> queryProfiles(@NonNull String queryString) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof List)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<List<Map<String, Object>>>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<Map<String, Object>>> updateProfile(@NonNull Map<String, Object> profileDetails, String eTag) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof Map)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<Map<String, Object>>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<Map<String, Object>>> patchProfile(@NonNull String s, @NonNull Map<String, Object> map, String s1) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof Map)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<Map<String, Object>>) result;
                            }
                        });
                    }

                    @Override
                    public Observable<ComapiResult<Map<String, Object>>> patchMyProfile(@NonNull Map<String, Object> map, String s) {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            if (result == null || !(result.getResult() == null || result.getResult() instanceof Map)) {
                                throw new Exception("Mocking response error in MockFoundationFactory class");
                            } else {
                                return (ComapiResult<Map<String, Object>>) result;
                            }
                        });
                    }
                };
            }

            /**
             * Access COMAPI Service session management APIs.
             *
             * @return COMAPI Service session management APIs.
             */
            public SessionService session() {
                return new SessionService() {

                    @Override
                    public Observable<Session> startSession() {
                        return Observable.fromCallable(() -> new Session(new SessionData()
                                .setSessionId(ChatTestConst.SESSION_ID)
                                .setExpiresOn(System.currentTimeMillis() + 10000)
                                .setAccessToken(ChatTestConst.TOKEN)
                                .setProfileId(ChatTestConst.PROFILE_ID)));
                    }

                    @Override
                    public Observable<ComapiResult<Void>> endSession() {
                        return Observable.fromCallable(() -> {
                            ComapiResult<?> result = results.poll();
                            return (ComapiResult<Void>) result;
                        });
                    }
                };
            }

            /**
             * Access COMAPI Service support of various messaging channels.
             *
             * @return COMAPI Service support of various messaging channels.
             */
            public ChannelsService channels() {
                return () -> null;
            }
        };
    }

    @Override
    public void addListener(MessagingListener listener) {
        testMessagingListener = listener;
    }

    @Override
    public void removeListener(MessagingListener listener) {
        testMessagingListener = null;
    }

    @Override
    public void addListener(ProfileListener listener) {
        testProfileListener = listener;
    }

    @Override
    public void removeListener(ProfileListener listener) {
        testProfileListener = null;
    }

    @Override
    public Observable<String> getLogs() {
        return Observable.fromCallable(() -> "logs...");
    }

    @Override
    public void clean(@NonNull Context context) {
        // do nothing
    }

    @Override
    protected Logger getLogger() {
        return new Logger(new LogManager() {
            public void log(final String clazz, final int logLevel, final String msg, final Throwable exception) {
                // do nothing
            }
        }, "");
    }

    @Override
    protected RxComapiService getComapiService() {
        return service;
    }

    public void addMockedResult(ComapiResult<?> result) {
        results.add(result);
    }

    public void clearResults() {
        results.clear();
    }

    public void dispatchTestEvent(Event event) {
        if (event instanceof ParticipantAddedEvent) {
            if (testMessagingListener != null) {
                testMessagingListener.onParticipantAdded((ParticipantAddedEvent) event);
                configTestMessagingListener.onParticipantAdded((ParticipantAddedEvent) event);
            }
        } else if (event instanceof ParticipantRemovedEvent) {
            if (testMessagingListener != null) {
                testMessagingListener.onParticipantRemoved((ParticipantRemovedEvent) event);
                configTestMessagingListener.onParticipantRemoved((ParticipantRemovedEvent) event);
            }
        } else if (event instanceof ParticipantUpdatedEvent) {
            if (testMessagingListener != null) {
                testMessagingListener.onParticipantUpdated((ParticipantUpdatedEvent) event);
                configTestMessagingListener.onParticipantUpdated((ParticipantUpdatedEvent) event);
            }
        } else if (event instanceof ProfileUpdateEvent) {
            if (testProfileListener != null) {
                testProfileListener.onProfileUpdate((ProfileUpdateEvent) event);
                configTestProfileListener.onProfileUpdate((ProfileUpdateEvent) event);
            }
        } else if (event instanceof ParticipantTypingEvent) {
            if (testMessagingListener != null) {
                ParticipantTypingEvent on = (ParticipantTypingEvent) event;
                testMessagingListener.onParticipantIsTyping(on);
                configTypingListener.participantTyping(on.getConversationId(), on.getProfileId(), true);
            }
        } else if (event instanceof ParticipantTypingOffEvent) {
            if (testMessagingListener != null) {
                ParticipantTypingOffEvent off = (ParticipantTypingOffEvent) event;
                testMessagingListener.onParticipantTypingOff(off);
                configTypingListener.participantTyping(off.getConversationId(), off.getProfileId(), false);
            }
        }
    }
}
