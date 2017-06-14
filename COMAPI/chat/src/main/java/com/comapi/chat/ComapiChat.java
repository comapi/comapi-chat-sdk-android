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

import android.app.Application;
import android.support.annotation.NonNull;

import com.comapi.Callback;
import com.comapi.ClientHelper;
import com.comapi.GlobalState;
import com.comapi.RxComapiClient;
import com.comapi.internal.CallbackAdapter;

import rx.Observable;

/**
 * Initialisation point for {@link ComapiChatClient} instance.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ComapiChat {

    private static volatile ComapiChatClient instance;

    /**
     * Initialise and build SDK client.
     */
    public static Observable<ComapiChatClient> initialise(@NonNull final Application app, @NonNull final ChatConfig chatConfig) {

        final FoundationFactory factory = chatConfig.getFoundationFactory();
        final EventsHandler eventsHandler = factory.getAdaptingEventsHandler();
        final CallbackAdapter adapter = chatConfig.getComapiCallbackAdapter();

        return factory.getClientInstance(app, chatConfig, eventsHandler)
                .map(client -> {
                    ComapiChatClient chatClient = new ComapiChatClient(app, client, chatConfig, eventsHandler, adapter);
                    ClientHelper.addLifecycleListener(client, chatClient.createLifecycleListener());
                    if (client.getState() == GlobalState.SESSION_ACTIVE) {
                        //TODO check state
                    }
                    return chatClient;
                });
    }

    /**
     * Initialise and build SDK client singleton.
     */
    public static Observable<ComapiChatClient> initialiseShared(@NonNull final Application app, @NonNull final ChatConfig chatConfig) {

        final FoundationFactory factory = chatConfig.getFoundationFactory();
        final EventsHandler eventsHandler = new EventsHandler();
        final CallbackAdapter adapter = chatConfig.getComapiCallbackAdapter();

        return factory.getClientInstance(app, chatConfig, eventsHandler)
                .map(client -> {
                    createShared(app, client, chatConfig, eventsHandler, adapter);
                    ClientHelper.addLifecycleListener(client, (getShared().createLifecycleListener()));
                    if (client.getState() == GlobalState.SESSION_ACTIVE) {
                        //TODO check state
                    }
                    return getShared();
                });
    }

    /**
     * Initialise and build SDK client.
     */
    public static void initialise(@NonNull final Application app, @NonNull final ChatConfig chatConfig, final Callback<ComapiChatClient> callback) {

        final FoundationFactory factory = chatConfig.getFoundationFactory();
        final EventsHandler eventsHandler = new EventsHandler();
        final CallbackAdapter adapter = chatConfig.getComapiCallbackAdapter();

        adapter.adapt(factory.getClientInstance(app, chatConfig, eventsHandler)
                .map(client -> {
                    ComapiChatClient chatClient = new ComapiChatClient(app, client, chatConfig, eventsHandler, adapter);
                    ClientHelper.addLifecycleListener(client, chatClient.createLifecycleListener());
                    if (client.getState() == GlobalState.SESSION_ACTIVE) {
                        //TODO check state
                    }
                    return chatClient;
                }), callback);
    }

    /**
     * Initialise and build SDK client singleton.
     */
    public static void initialiseShared(@NonNull final Application app, @NonNull final ChatConfig chatConfig, final Callback<ComapiChatClient> callback) {

        final FoundationFactory factory = chatConfig.getFoundationFactory();
        final EventsHandler eventsHandler = new EventsHandler();
        final CallbackAdapter adapter = chatConfig.getComapiCallbackAdapter();

        adapter.adapt(factory.getClientInstance(app, chatConfig, eventsHandler)
                .map(client -> {
                    createShared(app, client, chatConfig, eventsHandler, adapter);
                    ClientHelper.addLifecycleListener(client, (getShared().createLifecycleListener()));
                    if (client.getState() == GlobalState.SESSION_ACTIVE) {
                        //TODO check state
                    }
                    return getShared();
                }), callback);
    }

    /**
     * Get global singleton of {@link ComapiChatClient}.
     *
     * @return Singleton of {@link ComapiChatClient}
     */
    private static ComapiChatClient createShared(Application app, @NonNull RxComapiClient comapiClient, @NonNull final ChatConfig chatConfig, @NonNull final EventsHandler eventsHandler, @NonNull final CallbackAdapter adapter) {

        if (instance == null) {
            synchronized (ComapiChatClient.class) {
                if (instance == null) {
                    instance = new ComapiChatClient(app, comapiClient, chatConfig, eventsHandler, adapter);
                }
            }
        }

        return instance;
    }

    /**
     * Get global singleton of {@link ComapiChatClient}.
     *
     * @return Singleton of {@link ComapiChatClient}
     */
    public static ComapiChatClient getShared() {

        if (instance == null) {
            synchronized (ComapiChatClient.class) {
                if (instance == null) {
                    throw new RuntimeException("Comapi Chat Client singleton has not been initialised.");
                }
            }
        }

        return instance;
    }

    /**
     * Method used by tests to reset shared client instance.
     */
    static void reset() {
        instance = null;
    }
}