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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;

import com.comapi.chat.database.model.DbOrphanedEvent;

/**
 * Implementation of SQLite helper interface.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
class DatabaseHelper extends SQLiteOpenHelper {

    /**
     * If the database schema change, the database version will be incremented.
     */
    private static final int DATABASE_VERSION = 1;

    /**
     * File body for SQLite database.
     */
    private static final String DATABASE_NAME = "ComapiChat.db";

    private static DatabaseHelper instance;

    private static final String TEXT_TYPE = " TEXT";
    private static final String COMMA_SEP = ",";

    static final String SQL_CREATE_ORPHANED_EVENTS_TABLE =
            "CREATE TABLE " + DbOrphanedEvent.TABLE_NAME + "(" +
                    DbOrphanedEvent.EVENT_ID + " TEXT PRIMARY KEY" + COMMA_SEP +
                    DbOrphanedEvent.MESSAGE_ID + TEXT_TYPE + COMMA_SEP +
                    DbOrphanedEvent.EVENT + TEXT_TYPE + ")";

    DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    static synchronized DatabaseHelper getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ORPHANED_EVENTS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
