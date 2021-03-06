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

import com.comapi.BaseConfig;
import com.comapi.ComapiConfig;
import com.comapi.chat.listeners.ParticipantsListener;
import com.comapi.chat.listeners.ProfileListener;
import com.comapi.chat.listeners.TypingListener;
import com.comapi.chat.model.ChatStore;
import com.comapi.internal.CallbackAdapter;

/**
 * Comapi Chat configuration and setup.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ChatConfig extends BaseConfig<ChatConfig> {

    private StoreFactory<ChatStore> storeFactory;

    private TypingListener typingListener;

    private ProfileListener profileListener;

    private ParticipantsListener participantsListener;

    private FoundationFactory foundationFactory;

    private ObservableExecutor observableExecutor;

    private InternalConfig internalConfig;

    /**
     * Sets builder for {@link ChatStore} db interface that can handle single db transactions.
     *
     * @param builder Factory class to create {@link ChatStore}.
     * @param <T> Generic type of persistance store parent class.
     * @return Builder instance with new value set.
     */
    public <T extends ChatStore> ChatConfig store(StoreFactory<T> builder) {
        this.storeFactory = builder.asChatStoreFactory();
        return this;
    }

    /**
     * Gets chat database implementation factory.
     *
     * @return Chat database implementation factory.
     */
    StoreFactory<ChatStore> getStoreFactory() {
        return storeFactory;
    }

    /**
     * Sets participant is typing in a conversation listener.
     *
     * @param typingListener Participant is typing in a conversation listener.
     * @return Builder instance with new value set.
     */
    public ChatConfig typingListener(TypingListener typingListener) {
        this.typingListener = typingListener;
        return this;
    }

    /**
     * Sets conversation participants changes listener.
     *
     * @param participantsListener Conversation participants changes listener.
     * @return Builder instance with new value set.
     */
    public ChatConfig participantsListener(ParticipantsListener participantsListener) {
        this.participantsListener = participantsListener;
        return this;
    }

    /**
     * Gets conversation participants changes listener.
     *
     * @return Conversation participants changes listener.
     */
    ParticipantsListener getParticipantsListener() {
        return participantsListener;
    }

    /**
     * Sets profile changes listener.
     *
     * @param profileListener Profile changes listener.
     * @return Builder instance with new value set.
     */
    public ChatConfig profileListener(ProfileListener profileListener) {
        this.profileListener = profileListener;
        return this;
    }

    /**
     * Gets internal configuration.
     *
     * @return Internal configuration.
     */
    InternalConfig getInternalConfig() {
        if (internalConfig == null) {
            internalConfig = new InternalConfig();
        }
        return internalConfig;
    }

    /**
     * Sets SDKs new internal configuration. Overrides a default setup.
     *
     * @param config Internal configuration.
     * @return Builder instance with new value set.
     */
    public ChatConfig internalConfig(InternalConfig config) {
        this.internalConfig = config;
        return this;
    }

    /**
     * Gets profile changes listener.
     *
     * @return Profile changes listener.
     */
    ProfileListener getProfileListener() {
        return profileListener;
    }

    /**
     * Gets participant is typing in a conversation listener.
     *
     * @return Participant is typing in a conversation listener.
     */
    TypingListener getTypingListener() {
        return typingListener;
    }

    CallbackAdapter getComapiCallbackAdapter() {
        if (callbackAdapter != null) {
            return callbackAdapter;
        } else {
            return new CallbackAdapter();
        }
    }

    /**
     * Gets factory class to create Foundation SDK client.
     *
     * @return Factory class to create Foundation SDK client.
     */
    FoundationFactory getFoundationFactory() {
        if (foundationFactory != null) {
            return foundationFactory;
        } else {
            return new FoundationFactory();
        }
    }

    /**
     * Sets factory class to create Foundation SDK client. Used in tests to replace with mock class.
     *
     * @param foundationFactory Factory class to create Foundation SDK client.
     * @return Builder instance with new value set.
     */
    ChatConfig setFoundationFactory(FoundationFactory foundationFactory) {
        this.foundationFactory = foundationFactory;
        return this;
    }

    /**
     * Gets method to subscribe to internal observables. Used to synchronise internal processing in the tests.
     *
     * @return Class to provide method to subscribe to internal observables.
     */
    ObservableExecutor getObservableExecutor() {
        if (observableExecutor != null) {
            return observableExecutor;
        } else {
            return ObservableExecutor.getInstance();
        }
    }

    /**
     * Sets method to subscribe to internal observables. Used to synchronise internal processing in the tests.
     *
     * @param observableExecutor Class to provide method to subscribe to internal observables
     * @return Builder instance with new value set.
     */
    ChatConfig observableExecutor(ObservableExecutor observableExecutor) {
        this.observableExecutor = observableExecutor;
        return this;
    }

    /**
     * Build config for foundation SDK initialisation.
     *
     * @return Build config for foundation SDK initialisation.
     */
    ComapiConfig buildComapiConfig() {
        return new ComapiConfig()
                .apiSpaceId(apiSpaceId)
                .authenticator(authenticator)
                .logConfig(logConfig)
                .apiConfiguration(apiConfig)
                .logSizeLimitKilobytes(logSizeLimit)
                .pushMessageListener(pushMessageListener)
                .fcmEnabled(fcmEnabled);
    }

    @Override
    protected ChatConfig getThis() {
        return this;
    }

    public static class ExtHelper {

        public static void setFoundationFactory(@NonNull final ChatConfig config, FoundationFactory factory) {
            config.setFoundationFactory(factory);
        }

        public static void setObservableExecutor(@NonNull final ChatConfig config, ObservableExecutor obsExec) {
            config.observableExecutor(obsExec);
        }

        public static void overrideCallbackAdapter(@NonNull final ChatConfig config, CallbackAdapter adapter) {
            config.overrideCallbackAdapter(adapter);
        }

        public static ComapiConfig buildComapiConfig(@NonNull final ChatConfig config) {
            return config.buildComapiConfig();
        }
    }
}