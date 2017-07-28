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

import rx.Observable;
import rx.Subscriber;

/**
 * Subscribes to an observable
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
abstract class ObservableExecutor {

    /**
     * Subscribe to observable.
     *
     * @param obs Observable to execute.
     * @param <T> Type of result emited by the observable.
     */
    abstract <T> void execute(final Observable<T> obs);

    /**
     * New instance.
     *
     * @return New instance.
     */
    public static ObservableExecutor getInstance() {

        return new ObservableExecutor() {

            @Override
            <T> void execute(final Observable<T> obs) {

                obs.subscribe(new Subscriber<T>() {
                    @Override
                    public void onCompleted() {
                        // Completed, will unsubscribe automatically
                    }

                    @Override
                    public void onError(Throwable e) {
                        // Report errors in doOnError
                    }

                    @Override
                    public void onNext(T t) {
                        // Ignore result
                    }
                });
            }
        };
    }
}
