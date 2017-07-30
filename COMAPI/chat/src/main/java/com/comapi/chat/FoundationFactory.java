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

import com.comapi.RxComapi;
import com.comapi.RxComapiClient;

import rx.Observable;

/**
 * Factory class to initialise and obtain Foundation SDK client. Also used to mock the client for tests.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class FoundationFactory {

    /**
     * Initialise and obtain Foundation SDK client.
     *
     * @param app        Application instance.
     * @param chatConfig Chat SDK configuration.
     * @return Observable returning Comapi Foundation Client instance.
     */
    protected Observable<RxComapiClient> getClientInstance(@NonNull Application app, @NonNull final ChatConfig chatConfig) {
        return RxComapi.initialise(app, chatConfig.buildComapiConfig());
    }

    /**
     * Returns event handler adapting Foundation listeners for Chat SDK.
     *
     * @return Event handler adapting Foundation listeners for Chat SDK.
     */
    protected EventsHandler getAdaptingEventsHandler() {
        return new EventsHandler();
    }
}