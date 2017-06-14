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

package com.comapi.chat.database.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Parcelable;

import com.comapi.chat.database.DbCursorHelper;
import com.google.auto.value.AutoValue;

import rx.functions.Func1;

/**
 * Orphaned event related to a message that wasn't processed yet. Will be stored in database and delivered with the relevant message.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
@AutoValue
public abstract class DbOrphanedEvent implements Parcelable {

    public static final String TABLE_NAME = "OrphanedEvents";
    public static final String EVENT_ID = "_id";
    public static final String MESSAGE_ID = "mId";
    public static final String EVENT = "e";

    /**
     * Event id.
     *
     * @return Event id.
     */
    public abstract String id();

    /**
     * Message id of a message that this orphaned event is related to.
     *
     * @return Message id of a message that this orphaned event is related to.
     */
    public abstract String messageId();

    /**
     * Json String representing the orphaned event.
     *
     * @return Orphaned event as json String.
     */
    public abstract String event();

    /**
     * Maps cursor row to an {@link DbOrphanedEvent} object.
     */
    public static Func1<Cursor, DbOrphanedEvent> MAP = cursor -> {
        String id = DbCursorHelper.getString(cursor, EVENT_ID);
        String messageId = DbCursorHelper.getString(cursor, MESSAGE_ID);
        String event = DbCursorHelper.getString(cursor, EVENT);
        return new AutoValue_DbOrphanedEvent(id, messageId, event);
    };

    /**
     * Builder to construct ContentValues for database.
     */
    public static final class Builder {

        private final ContentValues values = new ContentValues();

        /**
         * Event unique identifier.
         *
         * @param eventId Event unique identifier.
         * @return Builder instance.
         */
        public Builder eventId(String eventId) {
            values.put(EVENT_ID, eventId);
            return this;
        }

        /**
         * Message unique identifier.
         *
         * @param messageId Message unique identifier.
         * @return Builder instance.
         */
        public Builder messageId(String messageId) {
            values.put(MESSAGE_ID, messageId);
            return this;
        }

        /**
         * Event jason (can be parsed to {@link com.comapi.internal.network.model.messaging.OrphanedEvent}
         *
         * @param event {@link com.comapi.internal.network.model.messaging.OrphanedEvent} as a json string.
         * @return Builder instance.
         */
        public Builder event(String event) {
            values.put(EVENT, event);
            return this;
        }

        /**
         * Build ContentValues for the db.
         *
         * @return ContentValues for the db.
         */
        public ContentValues build() {
            return values;
        }
    }
}