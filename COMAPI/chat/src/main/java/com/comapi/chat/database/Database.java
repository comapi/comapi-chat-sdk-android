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

package com.comapi.chat.database;

import android.app.Application;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import com.comapi.chat.database.model.DbOrphanedEvent;
import com.comapi.internal.Parser;
import com.comapi.internal.log.Logger;
import com.comapi.internal.network.model.messaging.OrphanedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import rx.Observable;
import rx.exceptions.Exceptions;

/**
 * Database manager for data internal to chat layer.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class Database {

    private static DatabaseHelper dbHelper;

    private final Logger log;

    /**
     * Private constructor.
     *
     * @param log Logger instance to use Foundation logging mechanism.
     */
    private Database(@NonNull Logger log) {
        this.log = log;
    }

    /**
     * Creates database controller and new database instance if wasn't created yet or parameter reset was set to true.
     *
     * @param app                   Application instance.
     * @param resetDBHelperInstance True if underlying db helper should be recreated. This should be set to true only for tests.
     * @param log                   Logger instance to use Foundation logging mechanism.
     * @return DB controller instance.
     */
    public static synchronized Database getInstance(@NonNull Application app, boolean resetDBHelperInstance, @NonNull Logger log) {

        if (dbHelper == null) {
            dbHelper = DatabaseHelper.getInstance(app.getApplicationContext());
        } else if (resetDBHelperInstance) {
            //For testing purposes we need separate instance of DatabaseHelper for every test.
            dbHelper = new DatabaseHelper(app.getApplicationContext());
        }

        return new Database(log);
    }

    /**
     * Save orphaned events (events returned in message query related to some message that was send later then messages obtained from the query. Should be used to update messages in previous message pages.)
     * This method is blocking.
     *
     * @param events Orphaned events to insert.
     * @return Observable returning number of affected rows.
     */
    public Observable<Integer> save(List<OrphanedEvent> events) {

        return Observable.fromCallable(() -> {

            int numberOfRowsInserted = 0;

            if (events != null && !events.isEmpty()) {

                Parser parser = new Parser();
                SQLiteDatabase writable = dbHelper.getWritableDatabase();

                writable.beginTransaction();

                try {

                    for (OrphanedEvent event : events) {
                        if (writable.insertWithOnConflict(DbOrphanedEvent.TABLE_NAME, null, new DbOrphanedEvent.Builder()
                                .eventId(event.getEventId())
                                .messageId(event.getMessageId())
                                .event(parser.toJson(event))
                                .build(), SQLiteDatabase.CONFLICT_IGNORE) > 0) {
                            numberOfRowsInserted += 1;
                        }
                    }

                    writable.setTransactionSuccessful();

                } finally {
                    writable.endTransaction();
                    log.d(numberOfRowsInserted + " orphaned events inserted and pending.");
                }

            }

            return numberOfRowsInserted;
        });
    }

    /**
     * Query orphaned events (events returned in message query related to some message that was send later then messages obtained from the query. Should be used to update messages in previous message pages.)
     *
     * @param ids Message unique identifiers for a message that the orphaned events are updating.
     * @return Observable returning orphaned events.
     */
    public Observable<List<DbOrphanedEvent>> queryOrphanedEvents(final String[] ids) {

        return Observable.fromCallable(() -> {

            List<DbOrphanedEvent> items;
            SQLiteDatabase readable = dbHelper.getReadableDatabase();

            String QUERY_ORPHANED_EVENTS = "SELECT *"
                    + " FROM " + DbOrphanedEvent.TABLE_NAME + " WHERE " + DbOrphanedEvent.MESSAGE_ID + " IN " + queryPlaceholder(ids.length);
            Cursor cursor;

            try {

                cursor = readable.rawQuery(QUERY_ORPHANED_EVENTS, ids);
                if (cursor != null) {

                    try {
                        items = new ArrayList<>(cursor.getCount());
                        while (cursor.moveToNext()) {
                            items.add(DbOrphanedEvent.MAP.call(cursor));
                        }
                    } finally {
                        cursor.close();
                    }

                } else {
                    items = new ArrayList<>(0);
                }

                log.d("Applying "+items.size()+" orphaned events");

                return items;

            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
            }

            return new ArrayList<DbOrphanedEvent>();
        });
    }

    /**
     * Delete orphaned events (events returned in message query related to some message that was send later then messages obtained from the query. Should be used to update messages in previous message pages.)
     *
     * @param ids Events unique identifiers for orophaned events that should be deleted from database.
     * @return Observable returning number of deleted rows in database.
     */
    public Observable<Integer> deleteOrphanedEvents(String[] ids) {

        return Observable.fromCallable(() -> {
            SQLiteDatabase writable = dbHelper.getWritableDatabase();
            if (ids.length > 0) {
                int numberOfRows = writable.delete(DbOrphanedEvent.TABLE_NAME, DbOrphanedEvent.EVENT_ID + " IN " + queryPlaceholder(ids.length), ids);
                log.d("Deleted " + numberOfRows + " orphaned events with ids " + Arrays.toString(ids));
                return numberOfRows;
            } else {
                return 0;
            }
        });
    }

    /**
     * Recreates empty database.
     */
    public void resetDatabase() {

        String dropOrphanedEvents = "DROP TABLE IF EXISTS " + DbOrphanedEvent.TABLE_NAME;

        SQLiteDatabase writable = dbHelper.getWritableDatabase();

        writable.beginTransaction();

        try {

            writable.execSQL(dropOrphanedEvents);
            writable.execSQL(DatabaseHelper.SQL_CREATE_ORPHANED_EVENTS_TABLE);
            writable.setTransactionSuccessful();

        } finally {
            writable.endTransaction();
            log.d("Chat database reset.");
        }
    }

    /**
     * Create a placeholder for SQL query syntax e.g. "(?,?,?)".
     *
     * @param length Number of values that will be inserted to the placeholder.
     * @return A placeholder for SQL query syntax.
     */
    private String queryPlaceholder(int length) {
        StringBuilder sb = new StringBuilder(length * 2 + 1);
        sb.append("(?");
        for (int i = 1; i < length; i++) {
            sb.append(",?");
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Close the database.
     */
    public void closeDatabase() {
        dbHelper.close();
    }
}
