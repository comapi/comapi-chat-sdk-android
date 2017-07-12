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

import android.app.Application;
import android.support.annotation.NonNull;

import com.comapi.ComapiConfig;
import com.comapi.RxComapiClient;
import com.comapi.chat.ChatConfig;
import com.comapi.chat.EventsHandler;
import com.comapi.chat.FoundationFactory;

import java.util.ArrayList;

import rx.Observable;

/**
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class MockFoundationFactory extends FoundationFactory {

    private ConfigAdapter configAdapter;
    private MockComapiClient client;
    private EventsHandler eventsHandler;

    public MockFoundationFactory(ConfigAdapter configAdapter) {
        this.configAdapter = configAdapter;
    }

    @Override
    protected Observable<RxComapiClient> getClientInstance(@NonNull Application app, @NonNull ChatConfig chatConfig) {
        client = new MockComapiClient(configAdapter.adapt(chatConfig));
        client.addMockedResult(new MockResult<>(new ArrayList<>(), true, null, 200));
        return Observable.fromCallable(() -> client);
    }

    @Override
    protected EventsHandler getAdaptingEventsHandler() {
        eventsHandler = new EventsHandler();
        return eventsHandler;
    }

    public interface ConfigAdapter {
        ComapiConfig adapt(ChatConfig config);
    }

    public MockComapiClient getMockedClient() {
        return client;
    }

    public EventsHandler getMockedEventsHandler() {
        return eventsHandler;
    }
}
