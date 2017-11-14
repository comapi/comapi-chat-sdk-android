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

import com.comapi.internal.network.ComapiResult;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Chat SDK result.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ChatResult {

    private final boolean isSuccessful;

    private final Error error;

    public ChatResult(boolean isSuccessful, Error error) {
        this.isSuccessful = isSuccessful;
        this.error = error;
    }

    /**
     * True if operation was performed successfully.
     *
     * @return True if operation was performed successfully.
     */
    public boolean isSuccessful() {
        return isSuccessful;
    }

    /**
     * Gets error details if operation unsuccessful.
     *
     * @return Error details.
     */
    public Error getError() {
        return error;
    }

    /**
     * Error details.
     */
    public static class Error {

        private final int code;

        private final String message;

        private final String details;

        /**
         * Recommended constructor.
         *
         * @param code    Error code. For service call errors this will be http error code.
         * @param message Error description.
         * @param details Error details.
         */
        public Error(int code, @NonNull String message, @Nullable String details) {
            this.code = code;
            this.message = message;
            this.details = details;
        }

        /**
         * Recommended constructor.
         *
         * @param code    Error code. For service call errors this will be http error code.
         * @param e Error details.
         */
        public Error(int code, @Nullable Throwable e) {
            this.code = code;
            if (e != null) {
                this.message = e.getLocalizedMessage();
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                this.details = sw.toString();
            } else {
                this.message = null;
                this.details = null;
            }
        }

        /**
         * Recommended constructor.
         *
         * @param e Error details.
         */
        public Error(@NonNull ComapiResult e) {
            this.code = e.getCode();
            this.message = e.getMessage();
            this.details = e.getErrorBody();
        }

        /**
         * Error code. In most cases http error code.
         *
         * @return Error code.
         */
        public int getCode() {
            return code;
        }

        /**
         * Error message.
         *
         * @return Error message.
         */
        public String getMessage() {
            return message;
        }

        public String getDetails() {
            return details;
        }
    }
}