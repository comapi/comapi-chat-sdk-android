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

import com.comapi.chat.internal.MissingEventsTracker;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MissingEventsTest {

    private Listener listener;

    @Test
    public void test_TrackIds() {

        String conversationId = "id-1";

        MissingEventsTracker tracker = new MissingEventsTracker();
        listener = new Listener();

        for (int i = 0; i < 100; i++) {
            tracker.checkEventId(conversationId, i, listener);
        }
        assertNull(listener.conversationId);
        assertNull(listener.limit);
        assertNull(listener.from);

        tracker.checkEventId(conversationId, 200, listener);
        assertEquals(conversationId, listener.conversationId);
        assertEquals(Integer.valueOf(101), listener.limit);
        assertEquals(Long.valueOf(100), listener.from);
    }

    @Test
    public void test_TrackIdsRepeated() {

        String conversationId = "id-1";

        MissingEventsTracker tracker = new MissingEventsTracker();
        listener = new Listener();

        for (int i = 0; i < 100; i++) {
            tracker.checkEventId(conversationId, i, listener);
        }
        for (int i = 0; i < 100; i++) {
            tracker.checkEventId(conversationId, i, listener);
        }
        assertNull(listener.conversationId);
        assertNull(listener.limit);
        assertNull(listener.from);
    }

    @After
    public void tearDown() throws Exception {
        listener.reset();
    }

    private class Listener implements MissingEventsTracker.MissingEventsListener {

        String conversationId;
        Long from;
        Integer limit;

        @Override
        public void missingEvents(String conversationId, long from, int limit) {
            this.conversationId = conversationId;
            this.from = from;
            this.limit = limit;
        }

        void reset() {
            conversationId = null;
            from = null;
            limit = null;
        }
    }
}