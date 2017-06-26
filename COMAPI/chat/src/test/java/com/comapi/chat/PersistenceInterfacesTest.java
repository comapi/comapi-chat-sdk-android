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

import com.comapi.chat.helpers.DBObjectsHelper;
import com.comapi.chat.helpers.TestChatStore;
import com.comapi.chat.model.ChatStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

@RunWith(RobolectricGradleTestRunner.class)
@Config(manifest = "chat/src/main/AndroidManifest.xml", sdk = Build.VERSION_CODES.M, constants = BuildConfig.class, packageName = "com.comapi.chat")
public class PersistenceInterfacesTest {

    private StoreFactory<ChatStore> factory;

    private TestChatStore store;

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
    }

    @Test
    public void test_StoreApisForConversations() throws InterruptedException {

        factory.execute(new StoreTransaction<ChatStore>() {
            @Override
            protected void execute(ChatStore store) {
                String conversationId = "id123";
                assertNull(store.getConversation(conversationId));
                store.upsert(DBObjectsHelper.createConversation(conversationId));
                assertEquals(conversationId, store.getConversation(conversationId).getConversationId());
                store.deleteConversation(conversationId);
                assertNull(store.getConversation(conversationId));
            }
        });
    }

    @Test
    public void test_StoreApisForMessages() throws InterruptedException {

        factory.execute(new StoreTransaction<ChatStore>() {
            @Override
            protected void execute(ChatStore store) {
                String messageId = "id123";
                String conversationId = "id321";

                assertNull(((TestChatStore)store).getMessages().get(messageId));
                store.upsert(DBObjectsHelper.createMessage(messageId, conversationId));
                assertEquals(conversationId, ((TestChatStore)store).getMessages().get(messageId).getConversationId());
                store.deleteAllMessages(conversationId);
                assertNull(((TestChatStore)store).getMessages().get(messageId));
            }
        });
    }

    @Test
    public void test_StoreApisForParticipants() throws InterruptedException {

        factory.execute(new StoreTransaction<ChatStore>() {
            @Override
            protected void execute(ChatStore store) {
                String participantId1 = "id";
                String participantId2 = "id2";
                String conversationId = "id321";

                assertNull(store.getParticipants(conversationId));

                store.upsert(conversationId, DBObjectsHelper.createParticipants(participantId1));
                store.upsert(conversationId, DBObjectsHelper.createParticipants(participantId2));
                assertEquals(2, store.getParticipants(conversationId).size());

                store.deleteAllMessages(conversationId);
                assertNull(store.getParticipants(conversationId));
            }
        });
    }

    @Test
    public void test_StoreClear() throws InterruptedException {

        factory.execute(new StoreTransaction<ChatStore>() {
            @Override
            protected void execute(ChatStore store) {
                String participantId1 = "id";
                String participantId2 = "id2";
                String conversationId = "id321";

                store.upsert(conversationId, DBObjectsHelper.createParticipants(participantId1));
                store.upsert(conversationId, DBObjectsHelper.createParticipants(participantId2));
                assertEquals(2, store.getParticipants(conversationId).size());
                store.upsert(DBObjectsHelper.createConversation(conversationId));

                store.clearDatabase();
                assertNull(store.getParticipants(conversationId));
                assertNull(store.getConversation(conversationId));
            }
        });
    }

    @After
    public void tearDown() throws Exception {
        store.clearDatabase();
    }
}