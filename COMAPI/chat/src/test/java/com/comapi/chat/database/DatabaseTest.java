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

import android.os.Build;
import android.text.TextUtils;

import com.comapi.chat.BuildConfig;
import com.comapi.chat.database.model.DbOrphanedEvent;
import com.comapi.chat.helpers.ResponseTestHelper;
import com.comapi.internal.Parser;
import com.comapi.internal.log.LogLevel;
import com.comapi.internal.log.LogManager;
import com.comapi.internal.log.Logger;
import com.comapi.internal.network.model.messaging.OrphanedEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
@Config(manifest = "chat/src/main/AndroidManifest.xml", sdk = Build.VERSION_CODES.M, constants = BuildConfig.class, packageName = "com.comapi.chat")
public class DatabaseTest {

    private Database database;

    @Before
    public void setUpChat() throws Exception {

        LogManager logMgr = new LogManager();
        logMgr.init(RuntimeEnvironment.application, LogLevel.OFF.getValue(), LogLevel.OFF.getValue(), 1000);

        if (database == null) {
            database = Database.getInstance(RuntimeEnvironment.application, true, new Logger(logMgr, ""));
        }
    }

    @Test
    public void test_OrphanedEvents_shouldSucceedInsertQueryDeleteClear() throws IOException, JSONException {

        String messageId = "60526ba0-76b3-4f33-9e2e-20f4a8bb548b"; // message id for all events (two) in orphaned_events_array.json
        final String[] ids = new String[1];
        ids[0] = messageId;

        /*
         * Check if initially query returnes no results.
         */
        List<DbOrphanedEvent> orphanedEvents = database.queryOrphanedEvents(ids).toBlocking().first();
        assertTrue(orphanedEvents.isEmpty());

        /*
         * Load orphaned events from json file.
         */
        List<OrphanedEvent> orphanedEventsFromFile = new ArrayList<>();
        String json = ResponseTestHelper.readFromFile(this, "orphaned_events_array.json");
        Parser parser = new Parser();
        JSONArray jsonarray = new JSONArray(json);
        for (int i = 0; i < jsonarray.length(); i++) {
            org.json.JSONObject object = jsonarray.getJSONObject(i);
            orphanedEventsFromFile.add(parser.parse(object.toString(), OrphanedEvent.class));
        }

        /*
         * Check if two rows are added to the table.
         */
        assertEquals(2, database.save(orphanedEventsFromFile).toBlocking().first().intValue());

        /*
         * Compare initial orphaned events with query result.
         */
        List<DbOrphanedEvent> loaded = database.queryOrphanedEvents(ids).toBlocking().first();

        Set<String> savedIds = new HashSet<>();
        for (OrphanedEvent event : orphanedEventsFromFile) {
            savedIds.add(event.getEventId());
        }

        Set<String> loadedIds = new HashSet<>();
        for (DbOrphanedEvent event : loaded) {
            assertFalse(TextUtils.isEmpty(event.messageId()));
            assertFalse(TextUtils.isEmpty(event.event()));
            assertFalse(TextUtils.isEmpty(event.id()));
            loadedIds.add(event.id());
        }

        assertTrue(savedIds.size() > 0);
        assertTrue(loadedIds.size() == savedIds.size());

        boolean isOk = true;
        for (OrphanedEvent event : orphanedEventsFromFile) {
            if (!savedIds.contains(event.getEventId())) {
                isOk = false;
            }
        }
        assertTrue(isOk);

        /*
         * Check if deletes the right event successfully and if the event left in db has correct id.
         */
        String[] toDelete = new String[1];
        toDelete[0] = loadedIds.toArray()[0].toString();
        String toPreserve = loadedIds.toArray()[1].toString();
        assertTrue(database.deleteOrphanedEvents(toDelete).toBlocking().first() > -1);
        loaded = database.queryOrphanedEvents(ids).toBlocking().first();
        assertEquals(1, loaded.size());
        assertEquals(toPreserve, loaded.get(0).id());

        /*
         * Check if after reseting the db state querry will return empty result.
         */
        database.resetDatabase();
        loaded = database.queryOrphanedEvents(ids).toBlocking().first();
        assertTrue(loaded.isEmpty());
    }

    @After
    public void tearDown() throws Exception {
        database.closeDatabase();
        database.resetDatabase();
    }
}