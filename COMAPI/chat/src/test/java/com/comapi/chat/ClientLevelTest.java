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

import com.comapi.APIConfig;
import com.comapi.ComapiAuthenticator;
import com.comapi.chat.helpers.MockCallback;
import com.comapi.chat.helpers.MockComapiClient;
import com.comapi.chat.helpers.MockConversationDetails;
import com.comapi.chat.helpers.MockFoundationFactory;
import com.comapi.chat.helpers.MockResult;
import com.comapi.chat.helpers.TestChatStore;
import com.comapi.chat.model.ChatStore;
import com.comapi.internal.network.AuthClient;
import com.comapi.internal.network.ChallengeOptions;
import com.comapi.internal.network.model.conversation.ConversationCreate;
import com.comapi.internal.network.model.conversation.ConversationDetails;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static com.comapi.chat.helpers.ChatTestConst.ETAG;
import static com.comapi.chat.helpers.ChatTestConst.PROFILE_ID;
import static com.comapi.chat.helpers.ChatTestConst.TOKEN;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


@RunWith(RobolectricGradleTestRunner.class)
@Config(manifest = "chat/src/main/AndroidManifest.xml", sdk = Build.VERSION_CODES.M, constants = BuildConfig.class, packageName = "com.comapi.chat")
public class ClientLevelTest {

    private static final long TIME_OUT = 10000;

    private StoreFactory<ChatStore> factory;
    private ComapiChatClient client;
    private EventsHandler eventsHandler;
    private MockComapiClient mockedComapiClient;

    private final MockCallback<Boolean> callback = new MockCallback<>();

    private TestChatStore store;

    @Before
    public void setUpChat() throws Exception {

        APIConfig apiConfig = new APIConfig().service("http://localhost:59273/").socket("ws://10.0.0.0");

        store = new TestChatStore();

        factory = new StoreFactory<ChatStore>() {
            @Override
            public void build(StoreCallback<ChatStore> callback) {
                callback.created(store);
            }
        };

        MockFoundationFactory foundationFactory = new MockFoundationFactory(ChatConfig::buildComapiConfig);

        ChatConfig chatConfig = new ChatConfig()
                .setFoundationFactory(foundationFactory)
                .apiSpaceId("ApiSpaceId")
                .authenticator(new ComapiAuthenticator() {
                    @Override
                    public void onAuthenticationChallenge(AuthClient authClient, ChallengeOptions challengeOptions) {
                        authClient.authenticateWithToken(TOKEN);
                    }
                })
                .apiConfiguration(apiConfig)
                .store(factory);

        client = ComapiChat.initialise(RuntimeEnvironment.application, chatConfig).toBlocking().first();
        mockedComapiClient = foundationFactory.getMockedClient();
    }

    @Test
    public void test_clientAPIs() {

        assertNotNull(client);
        assertTrue(client.getSession().isSuccessfullyCreated());
        assertTrue(client.getSession().getProfileId().equals(PROFILE_ID));

        assertNotNull(client.service().messaging());
        assertNotNull(client.service().messaging());
        assertNotNull(client.service().messaging());

        assertNotNull(client.service().messaging());
        assertNotNull(client.service().messaging());
        assertNotNull(client.service().messaging());

    }

    @Test
    public void test_createConversation() throws InterruptedException {

        String conversationId = "id-1";
        String name = "name-1";

        ConversationCreate conversation = ConversationCreate.builder().setId(conversationId).setName(name).build();
        ConversationDetails details = new MockConversationDetails(conversationId);

        mockedComapiClient.addMockedResult(new MockResult<>(details, true, ETAG, 200));

        ChatResult result = client.rxService().messaging().createConversation(conversation).toBlocking().first();
        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertNull(result.getError());

        //callback.reset();

        factory.execute(new StoreTransaction<ChatStore>() {
            @Override
            protected void execute(ChatStore store) {
                callback.success(store.getConversation(conversationId).getConversationId().equals(conversationId));
            }
        });

        synchronized (callback) {
            callback.wait(TIME_OUT);
        }

        assertTrue(callback.getResult());
    }

    @After
    public void tearDown() throws Exception {
        mockedComapiClient.clearResults();
        mockedComapiClient.clean(RuntimeEnvironment.application);
        callback.reset();
    }
}
