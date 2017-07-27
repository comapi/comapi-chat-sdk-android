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

import android.os.Build;

import com.comapi.chat.database.Database;
import com.comapi.chat.helpers.ChatTestConst;
import com.comapi.chat.helpers.TestChatStore;
import com.comapi.chat.model.ChatConversation;
import com.comapi.chat.model.ChatConversationBase;
import com.comapi.chat.model.ChatStore;
import com.comapi.chat.model.ModelAdapter;
import com.comapi.internal.log.LogLevel;
import com.comapi.internal.log.LogManager;
import com.comapi.internal.log.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
@Config(manifest = "chat/src/main/AndroidManifest.xml", sdk = Build.VERSION_CODES.M, constants = BuildConfig.class, packageName = "com.comapi.chat")
public class PersistenceControllerTest {

    private StoreFactory<ChatStore> factory;

    private TestChatStore store;

    private PersistenceController persistenceController;

    @Before
    public void setUpChat() throws Exception {

        if (store == null) {
            store = new TestChatStore();
        }

        factory = new StoreFactory<ChatStore>() {
            @Override
            public void build(StoreCallback<ChatStore> callback) {
                callback.created(store);
            }
        };

        LogManager logMgr = new LogManager();
        logMgr.init(RuntimeEnvironment.application, LogLevel.OFF.getValue(), LogLevel.OFF.getValue(), 0);
        Logger log = new Logger(logMgr, "");

        ModelAdapter modelAdapter = new ModelAdapter();
        Database db = Database.getInstance(RuntimeEnvironment.application, true, new Logger(logMgr, ""));
        persistenceController = new PersistenceController(db, modelAdapter, factory, log);
    }

    @Test
    public void test_upsertConversations() {

        ChatConversation conversationA = ChatConversation.builder().setConversationId(ChatTestConst.CONVERSATION_ID1).setLatestRemoteEventId(3L).setUpdatedOn(1L).build();
        ChatConversation conversationB = ChatConversation.builder().setConversationId(ChatTestConst.CONVERSATION_ID2).setLatestRemoteEventId(6L).setUpdatedOn(2L).build();
        List<ChatConversation> list = new ArrayList<>();
        list.add(conversationA);
        list.add(conversationB);

        Boolean result = persistenceController.upsertConversations(list).toBlocking().first();
        assertTrue(result);

        assertEquals(ChatTestConst.CONVERSATION_ID1, store.getConversation(ChatTestConst.CONVERSATION_ID1).getConversationId());
        assertEquals(Long.valueOf(3), store.getConversation(ChatTestConst.CONVERSATION_ID1).getLastRemoteEventId());
        assertEquals(Long.valueOf(1), store.getConversation(ChatTestConst.CONVERSATION_ID1).getUpdatedOn());

        assertEquals(ChatTestConst.CONVERSATION_ID2, store.getConversation(ChatTestConst.CONVERSATION_ID2).getConversationId());
        assertEquals(Long.valueOf(6), store.getConversation(ChatTestConst.CONVERSATION_ID2).getLastRemoteEventId());
        assertEquals(Long.valueOf(2), store.getConversation(ChatTestConst.CONVERSATION_ID2).getUpdatedOn());

        store.upsert(ChatConversation.builder().populate(store.getConversation(ChatTestConst.CONVERSATION_ID1)).setFirstLocalEventId(1L).setLastLocalEventId(2L).build());
        store.upsert(ChatConversation.builder().populate(store.getConversation(ChatTestConst.CONVERSATION_ID2)).setFirstLocalEventId(4L).setLastLocalEventId(5L).build());

        result = persistenceController.upsertConversations(list).toBlocking().first();
        assertTrue(result);

        ChatConversationBase conversation1 = persistenceController.getConversation(ChatTestConst.CONVERSATION_ID1).toBlocking().first();

        assertEquals(ChatTestConst.CONVERSATION_ID1, conversation1.getConversationId());
        assertEquals(Long.valueOf(2), conversation1.getLastLocalEventId());
        assertEquals(Long.valueOf(3), conversation1.getLastRemoteEventId());
        assertEquals(Long.valueOf(1), conversation1.getUpdatedOn());
        assertEquals(Long.valueOf(1), conversation1.getFirstLocalEventId());

        ChatConversationBase conversation2 = persistenceController.getConversation(ChatTestConst.CONVERSATION_ID2).toBlocking().first();

        assertEquals(ChatTestConst.CONVERSATION_ID2, conversation2.getConversationId());
        assertEquals(Long.valueOf(5), conversation2.getLastLocalEventId());
        assertEquals(Long.valueOf(6), conversation2.getLastRemoteEventId());
        assertEquals(Long.valueOf(2), conversation2.getUpdatedOn());
        assertEquals(Long.valueOf(4), conversation2.getFirstLocalEventId());
    }

    @After
    public void tearDown() throws Exception {
        store.clearDatabase();
    }
}