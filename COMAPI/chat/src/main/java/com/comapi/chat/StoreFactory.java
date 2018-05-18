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

import com.comapi.chat.model.ChatStore;
import com.comapi.internal.log.Logger;

/**
 * Factory class for {@link ChatStore} implementations that preferably can support transactions.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public abstract class StoreFactory<T extends ChatStore> {

    private Logger log;

    /**
     * Execute store transaction based on provided {@link ChatStore} implementation. Every transaction should begin with {@link ChatStore#beginTransaction()} and end with {@link ChatStore#endTransaction()}
     *
     * @param transaction Store transaction based on provided {@link ChatStore} implementation.
     */
    synchronized public final void execute(@NonNull StoreTransaction<T> transaction) {
        try {
            build(transaction::execute);
        } catch (Exception e) {
            if (log != null) {
                log.f("Error executing external store transaction : " + e.getLocalizedMessage(), e);
            }
        }
    }

    /**
     * Build {@link ChatStore} implementation, preferably one that can support transactions.
     *
     * @param callback Callback to provide store implementation asynchronously.
     */
    protected abstract void build(StoreCallback<T> callback);

    /**
     * Injects logger to save information about failing store transactions.
     *
     * @param log Logger instance.
     */
    void injectLogger(final Logger log) {
        this.log = log;
    }

    /**
     * Converts the generic store factory object to specific one needed by chat SDK.
     *
     * @return Non generic store factory.
     */
    StoreFactory<ChatStore> asChatStoreFactory() {
        return new ChatStoreFactory<>(this);
    }

    /**
     * Store factory class to build non generic persistance store objects.
     *
     * @param <T> Generic type of persistance store parent class.
     */
    public static class ChatStoreFactory<T extends ChatStore> extends StoreFactory<ChatStore> {

        private final StoreFactory<T> factory;

        ChatStoreFactory(StoreFactory<T> factory) {
            this.factory = factory;
        }

        @Override
        protected void build(StoreCallback<ChatStore> callback) {
            factory.build(callback::created);
        }
    }
}