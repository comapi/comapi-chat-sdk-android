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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.comapi.APIConfig;
import com.comapi.Callback;
import com.comapi.ComapiAuthenticator;
import com.comapi.chat.helpers.ChatTestConst;
import com.comapi.chat.helpers.MockCallback;
import com.comapi.chat.helpers.MockComapiClient;
import com.comapi.chat.helpers.MockConversationDetails;
import com.comapi.chat.helpers.MockFoundationFactory;
import com.comapi.chat.helpers.MockMsgSentResponse;
import com.comapi.chat.helpers.MockResult;
import com.comapi.chat.helpers.TestChatStore;
import com.comapi.chat.model.ChatConversationBase;
import com.comapi.chat.model.ChatMessage;
import com.comapi.chat.model.ChatParticipant;
import com.comapi.chat.model.ChatRole;
import com.comapi.chat.model.ChatStore;
import com.comapi.internal.CallbackAdapter;
import com.comapi.internal.network.AuthClient;
import com.comapi.internal.network.ChallengeOptions;
import com.comapi.internal.network.model.conversation.ConversationCreate;
import com.comapi.internal.network.model.conversation.ConversationDetails;
import com.comapi.internal.network.model.conversation.ConversationUpdate;
import com.comapi.internal.network.model.conversation.Participant;
import com.comapi.internal.network.model.messaging.MessageToSend;
import com.comapi.internal.network.model.messaging.Part;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.Subscriber;

import static com.comapi.chat.helpers.ChatTestConst.ETAG;
import static com.comapi.chat.helpers.ChatTestConst.PROFILE_ID;
import static com.comapi.chat.helpers.ChatTestConst.TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


@RunWith(RobolectricGradleTestRunner.class)
@Config(manifest = "chat/src/main/AndroidManifest.xml", sdk = Build.VERSION_CODES.M, constants = BuildConfig.class, packageName = "com.comapi.chat")
public class ClientLevelTest {

    private static final long TIME_OUT = 10000;

    private StoreFactory<ChatStore> factory;
    private ComapiChatClient client;
    private MockComapiClient mockedComapiClient;

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
                .store(factory)
                .observableExecutor(new ObservableExecutor() {
                    @Override
                    <T> void execute(Observable<T> obs) {
                        obs.toBlocking().first();
                    }
                })
                .overrideCallbackAdapter(new CallbackAdapter() {
                    @Override
                    public <T> void adapt(@NonNull Observable<T> subscriber, @Nullable Callback<T> callback) {
                        subscriber.subscribe(new Subscriber<T>() {

                            @Override
                            public void onCompleted() {

                            }

                            @Override
                            public void onError(Throwable e) {
                                if (callback != null) {
                                    callback.error(e);
                                }
                            }

                            @Override
                            public void onNext(T result) {
                                if (callback != null) {
                                    callback.success(result);
                                }
                            }
                        });
                    }
                });

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
    public void test_manageConversation() throws InterruptedException {

        String conversationId = "id-1";
        String name1 = "name-1";
        String etag1 = "etag-1";
        String etag2 = "etag-2";

        ConversationCreate conversationCreate = ConversationCreate.builder().setId(conversationId).setName(name1).build();
        ConversationDetails details1 = new MockConversationDetails(conversationId).setName(name1);

        // Create

        mockedComapiClient.addMockedResult(new MockResult<>(details1, true, etag1, 200));

        ChatResult result1 = client.rxService().messaging().createConversation(conversationCreate).toBlocking().first();
        assertNotNull(result1);
        assertTrue(result1.isSuccessful());
        assertNull(result1.getError());

        assertEquals(conversationId, store.getConversation(conversationId).getConversationId());
        ChatConversationBase con1 = store.getConversation(conversationId);
        long updated1 = con1.getUpdatedOn();
        assertTrue(updated1 > 0);
        assertEquals(etag1, con1.getETag());

        // Update

        String name2 = "name-2";

        ConversationUpdate conversationUpdate = ConversationUpdate.builder().setName(name2).build();
        ConversationDetails details2 = new MockConversationDetails(conversationId).setName(name2);

        mockedComapiClient.addMockedResult(new MockResult<>(details2, true, etag2, 200));

        ChatResult result2 = client.rxService().messaging().updateConversation(conversationId, conversationUpdate).toBlocking().first();
        assertNotNull(result2);
        assertTrue(result2.isSuccessful());
        assertNull(result2.getError());

        assertEquals(conversationId, store.getConversation(conversationId).getConversationId());
        ChatConversationBase con2 = store.getConversation(conversationId);
        long updated2 = con2.getUpdatedOn();
        assertTrue(updated2 > updated1);
        assertEquals(etag2, con2.getETag());

        // Delete

        mockedComapiClient.addMockedResult(new MockResult<>(null, true, etag2, 200));

        ChatResult result3 = client.rxService().messaging().deleteConversation(conversationId).toBlocking().first();
        assertNotNull(result3);
        assertTrue(result3.isSuccessful());
        assertNull(result3.getError());

        assertNull(store.getConversation(conversationId));

    }

    @Test
    public void test_manageParticipants() throws InterruptedException {

        String conversationId = ChatTestConst.CONVERSATION_ID1;

        // Add conversation and participant to the store

        store.addConversationToStore(conversationId, -1L, -1L, 0, ChatTestConst.ETAG);

        ChatParticipant participant1 = ChatParticipant.builder().setParticipantId(ChatTestConst.PARTICIPANT_ID1).setName("name").setRole(ChatRole.participant).build();
        store.upsert(conversationId, participant1);

        // Add another participant

        Participant pToAdd = Participant.builder().setId(ChatTestConst.PARTICIPANT_ID2).setIsParticipant().build();
        List<Participant> participants = new ArrayList<>();
        participants.add(pToAdd);
        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ETAG, 200));

        ChatResult result1 = client.rxService().messaging().addParticipants(conversationId, participants).toBlocking().first();
        assertNotNull(result1);
        assertTrue(result1.isSuccessful());
        assertNull(result1.getError());

        assertEquals(2, store.getParticipants(conversationId).size());
        ChatParticipant p1 = store.getParticipants(conversationId).get(0);
        ChatParticipant p2 = store.getParticipants(conversationId).get(1);
        assertTrue((p1.getParticipantId().equals(ChatTestConst.PARTICIPANT_ID1) && p2.getParticipantId().equals(ChatTestConst.PARTICIPANT_ID2)) || (p2.getParticipantId().equals(ChatTestConst.PARTICIPANT_ID1) && p1.getParticipantId().equals(ChatTestConst.PARTICIPANT_ID2)));

        // Remove participants

        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ETAG, 200));
        List<String> participantsIds = new ArrayList<>();
        participantsIds.add(ChatTestConst.CONVERSATION_ID1);
        participantsIds.add(ChatTestConst.CONVERSATION_ID2);

        ChatResult result2 = client.rxService().messaging().removeParticipants(conversationId, participantsIds).toBlocking().first();
        assertNotNull(result2);
        assertTrue(result2.isSuccessful());
        assertNull(result2.getError());
        assertEquals(0, store.getParticipants(conversationId).size());
    }

    @Test
    public void test_sendMessages() throws InterruptedException {

        String conversationId = ChatTestConst.CONVERSATION_ID1;
        String messageId1a = "id-1";
        String messageId1b = "id-2";
        String messageId2a = "id-3";
        String messageId2b = "id-4";
        String body = "hello";

        final MockCallback<ChatResult> chatResultCallback = new MockCallback<>();

        // API ver 1a

        ConversationDetails conversation = new MockConversationDetails(conversationId);
        mockedComapiClient.addMockedResult(new MockResult<>(conversation, true, ChatTestConst.ETAG, 200));
        mockedComapiClient.addMockedResult(new MockResult<>(new MockMsgSentResponse(messageId1a), true, ETAG, 200));

        MessageToSend message = MessageToSend.builder().addPart(Part.builder().setData(body).build()).build();
        ChatResult result1a = client.rxService().messaging().sendMessage(conversationId, message).toBlocking().first();

        assertTrue(result1a.isSuccessful());
        assertEquals(1, store.getMessages().size());
        ChatMessage savedMessage1a = store.getMessages().get(messageId1a);
        assertEquals(body, savedMessage1a.getParts().get(0).getData());

        ChatConversationBase savedConversation = store.getConversations().get(ChatTestConst.CONVERSATION_ID1);
        assertEquals(conversationId, savedConversation.getConversationId());

        // API ver 1b

        mockedComapiClient.addMockedResult(new MockResult<>(new MockMsgSentResponse(messageId1b), true, ETAG, 200));

        client.service().messaging().sendMessage(conversationId, message, chatResultCallback);
        ChatResult result1b = chatResultCallback.getResult();

        assertTrue(result1b.isSuccessful());
        assertEquals(2, store.getMessages().size());
        ChatMessage savedMessage1b = store.getMessages().get(messageId1b);
        assertEquals(body, savedMessage1b.getParts().get(0).getData());

        savedConversation = store.getConversations().get(ChatTestConst.CONVERSATION_ID1);
        assertEquals(conversationId, savedConversation.getConversationId());

        chatResultCallback.reset();

        // API ver 3

        mockedComapiClient.addMockedResult(new MockResult<>(new MockMsgSentResponse(messageId2a), true, ETAG, 200));

        ChatResult result2a = client.rxService().messaging().sendMessage(conversationId, body).toBlocking().first();

        assertTrue(result2a.isSuccessful());
        assertEquals(3, store.getMessages().size());
        ChatMessage savedMessage2 = store.getMessages().get(messageId2a);
        assertEquals(body, savedMessage2.getParts().get(0).getData());

        savedConversation = store.getConversations().get(ChatTestConst.CONVERSATION_ID1);
        assertEquals(conversationId, savedConversation.getConversationId());

        // API ver 4

        mockedComapiClient.addMockedResult(new MockResult<>(new MockMsgSentResponse(messageId2b), true, ETAG, 200));

        client.service().messaging().sendMessage(conversationId, message, chatResultCallback);
        ChatResult result2b = chatResultCallback.getResult();

        assertTrue(result2b.isSuccessful());
        assertEquals(4, store.getMessages().size());
        ChatMessage savedMessage2b = store.getMessages().get(messageId2b);
        assertEquals(body, savedMessage2b.getParts().get(0).getData());

        savedConversation = store.getConversations().get(ChatTestConst.CONVERSATION_ID1);
        assertEquals(conversationId, savedConversation.getConversationId());

        chatResultCallback.reset();
    }

    @After
    public void tearDown() throws Exception {
        mockedComapiClient.clearResults();
        mockedComapiClient.clean(RuntimeEnvironment.application);
        store.clearDatabase();
    }
}
