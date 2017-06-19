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
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.comapi.ClientHelper;
import com.comapi.RxComapiClient;
import com.comapi.Session;
import com.comapi.chat.database.Database;
import com.comapi.chat.model.ModelAdapter;
import com.comapi.internal.CallbackAdapter;
import com.comapi.internal.lifecycle.LifecycleListener;
import com.comapi.internal.log.Logger;

/**
 * Client implementation for chat layer SDK. Handles initialisation and stores all internal objects.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ComapiChatClient {

    private final RxComapiClient client;

    @SuppressWarnings("FieldCanBeLocal")
    private final EventsHandler eventsHandler;

    @SuppressWarnings("FieldCanBeLocal")
    private final ChatController controller;

    private final ChatServiceAccessor serviceAccessor;

    private final RxChatServiceAccessor rxServiceAccessor;

    /**
     * Recommended constructor.
     *
     * @param app             Application instance.
     * @param client          Comapi Foundation SDK client.
     * @param chatConfig      SDK configuration.
     * @param eventsHandler   Socket events handler.
     * @param callbackAdapter Adapts Observables to callback APIs.
     */
    ComapiChatClient(Application app, final RxComapiClient client, final ChatConfig chatConfig, final EventsHandler eventsHandler, CallbackAdapter callbackAdapter) {
        this.client = client;
        this.eventsHandler = eventsHandler;
        Logger log = ClientHelper.getLogger(client);
        ModelAdapter modelAdapter = new ModelAdapter();
        Database db = Database.getInstance(app, false, log);
        PersistenceController persistenceController = new PersistenceController(db, modelAdapter, chatConfig.getStoreFactory());
        controller = new ChatController(client, persistenceController, ObservableExecutor.getInstance(), modelAdapter, log);
        serviceAccessor = new ChatServiceAccessor(modelAdapter, callbackAdapter, client, controller);
        rxServiceAccessor = new RxChatServiceAccessor(modelAdapter, client, controller);
        eventsHandler.init(new Handler(Looper.getMainLooper()), persistenceController, controller, chatConfig.getTypingListener());
    }

    /**
     * Gets the active session data.
     *
     * @return Active session data.
     */
    public Session getSession() {
        return client.getSession();
    }

    /**
     * Access to reactive Comapi service APIs.
     *
     * @return Comapi reactive service APIs.
     */
    public RxChatServiceAccessor rxService() {
        return rxServiceAccessor;
    }

    /**
     * Access to Comapi service APIs.
     *
     * @return Comapi service APIs.
     */
    public ChatServiceAccessor service() {
        return serviceAccessor;
    }

    LifecycleListener createLifecycleListener() {
        return new LifecycleListener() {

            public void onForegrounded(Context context) {
                //TODO check state
            }


            public void onBackgrounded(Context context) {

            }
        };
    }

    void clean(Application application) {
        client.clean(application.getApplicationContext());
    }
}