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

import com.comapi.BaseConfig;
import com.comapi.ComapiConfig;
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

    private FoundationFactory foundationFactory;

    /**
     * Sets builder for {@link ChatStore} db interface that can handle single db transactions.
     *
     * @param builder Factory class to create {@link ChatStore}.
     * @return Builder instance with new value set.
     */
    public ChatConfig store(StoreFactory<ChatStore> builder) {
        this.storeFactory = builder;
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

    FoundationFactory getFoundationFactory() {
        if (foundationFactory != null) {
            return foundationFactory;
        } else {
            return new FoundationFactory();
        }
    }

    ChatConfig setFoundationFactory(FoundationFactory foundationFactory) {
        this.foundationFactory = foundationFactory;
        return this;
    }

    /**
     * Build config for foundation SDK initialisation.
     *
     * @return Build config for foundation SDK initialisation.
     */
    ComapiConfig buildComapiConfig(EventsHandler handler) {
        return new ComapiConfig()
                .apiSpaceId(apiSpaceId)
                .authenticator(authenticator)
                .logConfig(logConfig)
                .apiConfiguration(apiConfig)
                .logSizeLimitKilobytes(logSizeLimit)
                .pushMessageListener(pushMessageListener)
                .profileListener(handler.getProfileListenerAdapter())
                .messagingListener(handler.getMessagingListenerAdapter());
    }

    @Override
    protected ChatConfig getThis() {
        return this;
    }
}