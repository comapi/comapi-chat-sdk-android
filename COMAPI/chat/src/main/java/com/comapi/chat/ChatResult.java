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

        private int code;

        private String message;

        public Error(int code, String message) {
            this.code = code;
            this.message = message;
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
    }
}