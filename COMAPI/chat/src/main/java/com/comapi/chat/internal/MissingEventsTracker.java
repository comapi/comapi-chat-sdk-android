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

package com.comapi.chat.internal;

import com.comapi.internal.log.Logger;

import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks last conversation events and looks for gaps in monotonically increasing conversation event ids.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class MissingEventsTracker {

    public interface RefreshListener {
        void refresh(String conversationId, long from, long limit);
    }

    private Map<String, TreeSet<Long>> idsPerConversation;

    public MissingEventsTracker() {
        idsPerConversation = new ConcurrentHashMap<>();
    }

    /**
     * Check conversation event id for duplicates or missing events.
     * @param conversationId Unique identifier of an conversation.
     * @param conversationEventId Unique per conversation, monotonically increasing conversation event id.
     * @return True if event with a given id already processed.
     */
    public boolean checkEventId(String conversationId, long conversationEventId, RefreshListener refreshListener) {

        if (!idsPerConversation.containsKey(conversationId)) {
            TreeSet<Long> ids = new TreeSet<>();
            boolean added = ids.add(conversationEventId);
            idsPerConversation.put(conversationId, ids);
            return !added;
        } else {

            TreeSet<Long> ids = idsPerConversation.get(conversationId);

            long last = ids.last();
            boolean added = ids.add(conversationEventId);

            if (last < conversationEventId - 1) {
                refreshListener.refresh(conversationId, last+1, conversationEventId - last);
            }

            while (ids.size() > 10) {
                ids.pollFirst();
            }

            return !added;
        }
    }
}