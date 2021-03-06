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
import com.comapi.Session;
import com.comapi.chat.helpers.ChatTestConst;
import com.comapi.chat.helpers.FileResHelper;
import com.comapi.chat.helpers.MockCallback;
import com.comapi.chat.helpers.MockComapiClient;
import com.comapi.chat.helpers.MockConversationDetails;
import com.comapi.chat.helpers.MockFoundationFactory;
import com.comapi.chat.helpers.MockMsgSentResponse;
import com.comapi.chat.helpers.MockResult;
import com.comapi.chat.helpers.TestChatStore;
import com.comapi.chat.model.ChatConversationBase;
import com.comapi.chat.model.ChatMessage;
import com.comapi.chat.model.ChatMessageStatus;
import com.comapi.chat.model.ChatStore;
import com.comapi.chat.model.LocalMessageStatus;
import com.comapi.internal.CallbackAdapter;
import com.comapi.internal.Parser;
import com.comapi.internal.network.AuthClient;
import com.comapi.internal.network.ChallengeOptions;
import com.comapi.internal.network.ComapiResult;
import com.comapi.internal.network.model.conversation.ConversationCreate;
import com.comapi.internal.network.model.conversation.ConversationDetails;
import com.comapi.internal.network.model.conversation.ConversationUpdate;
import com.comapi.internal.network.model.messaging.ConversationEventsResponse;
import com.comapi.internal.network.model.messaging.MessageStatus;
import com.comapi.internal.network.model.messaging.MessageToSend;
import com.comapi.internal.network.model.messaging.MessagesQueryResponse;
import com.comapi.internal.network.model.messaging.Part;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.Subscriber;

import static com.comapi.chat.helpers.ChatTestConst.ETAG;
import static com.comapi.chat.helpers.ChatTestConst.PROFILE_ID;
import static com.comapi.chat.helpers.ChatTestConst.TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


@RunWith(RobolectricTestRunner.class)
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
                    public <T> void execute(Observable<T> obs) {
                        obs.toBlocking().firstOrDefault(null);
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

        // get conversation returns null response
        mockedComapiClient.addMockedResult(new MockResult<>(null, true, etag1, 200));
        // create conversation response
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

        ChatResult result2 = client.rxService().messaging().updateConversation(conversationId, etag1, conversationUpdate).toBlocking().first();
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

        ChatResult result3 = client.rxService().messaging().deleteConversation(conversationId, etag2).toBlocking().first();
        assertNotNull(result3);
        assertTrue(result3.isSuccessful());
        assertNull(result3.getError());

        assertNull(store.getConversation(conversationId));

    }

    @Test
    public void test_manageConversation_callbacks() throws InterruptedException {

        String conversationId = "id-1";
        String name1 = "name-1";
        String etag1 = "etag-1";
        String etag2 = "etag-2";

        ConversationCreate conversationCreate = ConversationCreate.builder().setId(conversationId).setName(name1).build();
        ConversationDetails details1 = new MockConversationDetails(conversationId).setName(name1);

        // get conversation returns null response
        mockedComapiClient.addMockedResult(new MockResult<>(null, true, etag1, 200));
        // create conversation response
        mockedComapiClient.addMockedResult(new MockResult<>(details1, true, etag1, 200));

        final MockCallback<ChatResult> callback = new MockCallback<>();
        client.service().messaging().createConversation(conversationCreate, callback);
        assertNotNull(callback.getResult());
        assertTrue(callback.getResult().isSuccessful());
        assertNull(callback.getResult().getError());

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

        callback.reset();
        client.service().messaging().updateConversation(conversationId, etag1, conversationUpdate, callback);
        assertNotNull(callback.getResult());
        assertTrue(callback.getResult().isSuccessful());
        assertNull(callback.getResult().getError());

        assertEquals(conversationId, store.getConversation(conversationId).getConversationId());
        ChatConversationBase con2 = store.getConversation(conversationId);
        long updated2 = con2.getUpdatedOn();
        assertTrue(updated2 > 0);
        assertEquals(etag2, con2.getETag());

        // Delete

        mockedComapiClient.addMockedResult(new MockResult<>(null, true, etag2, 200));

        callback.reset();
        client.service().messaging().deleteConversation(conversationId, etag1, callback);
        assertNotNull(callback.getResult());
        assertTrue(callback.getResult().isSuccessful());
        assertNull(callback.getResult().getError());

        assertNull(store.getConversation(conversationId));

    }

    @Test
    public void test_sendMessages() throws InterruptedException {

        String conversationId = ChatTestConst.CONVERSATION_ID1;
        String messageId1 = "id-1";
        String messageId2 = "id-2";
        String body = "hello";

        // API ver 1

        ConversationDetails conversation = new MockConversationDetails(conversationId);
        mockedComapiClient.addMockedResult(new MockResult<>(conversation, true, ChatTestConst.ETAG, 200));
        mockedComapiClient.addMockedResult(new MockResult<>(new MockMsgSentResponse(messageId1), true, ETAG, 200));

        MessageToSend message = MessageToSend.builder().addPart(Part.builder().setData(body).build()).build();
        ChatResult result1 = client.rxService().messaging().sendMessage(conversationId, message).toBlocking().first();

        assertTrue(result1.isSuccessful());
        assertEquals(1, store.getMessages().size());
        ChatMessage savedMessage1 = store.getMessages().get(messageId1);
        assertEquals(body, savedMessage1.getParts().get(0).getData());

        ChatConversationBase savedConversation = store.getConversations().get(ChatTestConst.CONVERSATION_ID1);
        assertEquals(conversationId, savedConversation.getConversationId());


        // API ver 2

        mockedComapiClient.addMockedResult(new MockResult<>(new MockMsgSentResponse(messageId2), true, ETAG, 200));

        ChatResult result2 = client.rxService().messaging().sendMessage(conversationId, body).toBlocking().first();

        assertTrue(result2.isSuccessful());
        assertEquals(2, store.getMessages().size());
        ChatMessage savedMessage2 = store.getMessages().get(messageId2);
        assertEquals(body, savedMessage2.getParts().get(0).getData());

        savedConversation = store.getConversations().get(ChatTestConst.CONVERSATION_ID1);
        assertEquals(conversationId, savedConversation.getConversationId());

    }

    @Test
    public void test_sendMessages_callbacks() throws InterruptedException {

        String conversationId = ChatTestConst.CONVERSATION_ID1;
        String messageId1 = "id-1";
        String messageId2 = "id-2";
        String body = "hello";

        final MockCallback<ChatResult> chatResultCallback = new MockCallback<>();

        MessageToSend message = MessageToSend.builder().addPart(Part.builder().setData(body).build()).build();

        // API ver 1

        ConversationDetails conversation = new MockConversationDetails(conversationId);
        mockedComapiClient.addMockedResult(new MockResult<>(conversation, true, ChatTestConst.ETAG, 200));
        mockedComapiClient.addMockedResult(new MockResult<>(new MockMsgSentResponse(messageId1), true, ETAG, 200));

        client.service().messaging().sendMessage(conversationId, message, chatResultCallback);
        ChatResult result1 = chatResultCallback.getResult();

        assertTrue(result1.isSuccessful());
        assertEquals(1, store.getMessages().size());
        ChatMessage savedMessage1 = store.getMessages().get(messageId1);
        assertEquals(body, savedMessage1.getParts().get(0).getData());

        ChatConversationBase savedConversation = store.getConversations().get(ChatTestConst.CONVERSATION_ID1);
        assertEquals(conversationId, savedConversation.getConversationId());

        chatResultCallback.reset();

        // API ver 2

        mockedComapiClient.addMockedResult(new MockResult<>(new MockMsgSentResponse(messageId2), true, ETAG, 200));

        client.service().messaging().sendMessage(conversationId, body, chatResultCallback);
        ChatResult result2 = chatResultCallback.getResult();

        assertTrue(result2.isSuccessful());
        assertEquals(2, store.getMessages().size());
        ChatMessage savedMessage2 = store.getMessages().get(messageId2);
        assertEquals(body, savedMessage2.getParts().get(0).getData());

        savedConversation = store.getConversations().get(ChatTestConst.CONVERSATION_ID1);
        assertEquals(conversationId, savedConversation.getConversationId());

        chatResultCallback.reset();
    }

    @Test
    public void test_synchroniseStore() throws IOException {

        String newETag = "eTag-A";

        // Conversations setup

        // local event != -1  and remote event larger than local so update will be triggered,
        store.addConversationToStore(ChatTestConst.CONVERSATION_ID1, 2, 2, 3, 0, ChatTestConst.ETAG);
        store.addConversationToStore(ChatTestConst.CONVERSATION_ID2, -1L, -1L, 0, 0, ChatTestConst.ETAG); // will be deleted
        store.addConversationToStore(ChatTestConst.CONVERSATION_ID3, -1L, -1L, 0, 0, ChatTestConst.ETAG); // will be deleted

        ConversationDetails conversationA = new MockConversationDetails(ChatTestConst.CONVERSATION_ID1); // will be updated
        ConversationDetails conversationB = new MockConversationDetails(ChatTestConst.CONVERSATION_ID4); // will be added

        List<ConversationDetails> result = new ArrayList<>();
        result.add(conversationA);
        result.add(conversationB);
        mockedComapiClient.addMockedResult(new MockResult<>(result, true, newETag, 200));

        // Events setup
        // 3 events with ids 1,2,3
        String json = FileResHelper.readFromFile(this, "rest_events_query.json");
        Parser parser = new Parser();

        Type listType = new TypeToken<ArrayList<JsonObject>>() {
        }.getType();
        List<JsonObject> list = new Gson().fromJson(json, listType);

        ConversationEventsResponse response = new ConversationEventsResponse(list, parser);
        mockedComapiClient.addMockedResult(new MockResult<>(response, true, null, 200));

        // Test

        Boolean synchroniseSuccess = client.rxService().messaging().synchroniseStore().toBlocking().first().isSuccessful();

        assertTrue(synchroniseSuccess);

        // Check conversation

        assertTrue(store.getAllConversations().size() == 2);
        ChatConversationBase loadedConversation = store.getConversations().get(ChatTestConst.CONVERSATION_ID1);
        assertNotNull(loadedConversation);
        assertTrue(loadedConversation.getConversationId().equals(ChatTestConst.CONVERSATION_ID1));
        assertEquals(2, loadedConversation.getFirstLocalEventId().longValue()); // event query or socket events don't change first known id unless it's -1 (empty conversation)
        assertEquals(3, loadedConversation.getLastLocalEventId().longValue()); // was increased
        assertEquals(3, loadedConversation.getLastRemoteEventId().longValue());
        assertTrue(loadedConversation.getUpdatedOn() > 0);
        assertEquals("eTag", loadedConversation.getETag());

        // Check message

        assertTrue(store.getMessages().size() == 1);
        ChatMessage loadedMessage = store.getMessages().get("60526ba0-76b3-4f33-9e2e-20f4a8bb548b");
        assertNotNull(loadedMessage);
        assertTrue(loadedMessage.getConversationId().equals(ChatTestConst.CONVERSATION_ID1));
        assertEquals("p1", loadedMessage.getFromWhom().getId());
        assertEquals("p1", loadedMessage.getFromWhom().getName());
        assertEquals("p1", loadedMessage.getSentBy());
        assertEquals(1, loadedMessage.getSentEventId().longValue());
        assertNotNull(loadedMessage.getParts().get(0));
        assertEquals("body", loadedMessage.getParts().get(0).getName());
        assertEquals("non", loadedMessage.getParts().get(0).getData());
        assertEquals(3, loadedMessage.getParts().get(0).getSize());
        assertEquals("text/plain", loadedMessage.getParts().get(0).getType());

        // Check message status

        Collection<ChatMessageStatus> statuses = store.getMessages().get("60526ba0-76b3-4f33-9e2e-20f4a8bb548b").getStatusUpdates();
        assertNotNull(statuses);
        ChatMessageStatus status = (ChatMessageStatus) statuses.toArray()[0];
        assertNotNull(status);
        assertEquals("60526ba0-76b3-4f33-9e2e-20f4a8bb548b", status.getMessageId());
        assertEquals("p1", status.getProfileId());
        assertEquals(LocalMessageStatus.read, status.getMessageStatus());
        assertTrue(status.getUpdatedOn() > 0);
    }

    @Test
    public void test_synchroniseConversation() throws IOException, InterruptedException {

        // local event != -1  and remote event larger than local so update will be triggered,
        store.addConversationToStore(ChatTestConst.CONVERSATION_ID1, 2, 2, 3, 0, ChatTestConst.ETAG);

        String json = FileResHelper.readFromFile(this, "rest_message_query_no_orphans.json");
        Parser parser = new Parser();
        mockedComapiClient.addMockedResult(new MockResult<>(parser.parse(json, MessagesQueryResponse.class), true, ChatTestConst.ETAG, 200));

        // Events setup
        // 3 events with ids 1,2,3
        json = FileResHelper.readFromFile(this, "rest_events_query.json");

        Type listType = new TypeToken<ArrayList<JsonObject>>() {
        }.getType();
        List<JsonObject> list = new Gson().fromJson(json, listType);

        mockedComapiClient.addMockedResult(new MockResult<>(new ConversationEventsResponse(list, parser), true, null, 200));

        // Test
        final MockCallback<ChatResult> c = new MockCallback<>();
        client.service().messaging().synchroniseConversation(ChatTestConst.CONVERSATION_ID1, c);
        synchronized (c) {
            c.wait(TIME_OUT);
            c.notifyAll();
        }

        assertTrue(c.getResult().isSuccessful());

        // Check conversation

        assertTrue(store.getAllConversations().size() == 1);
        ChatConversationBase loadedConversation = store.getConversations().get(ChatTestConst.CONVERSATION_ID1);
        assertNotNull(loadedConversation);
        assertTrue(loadedConversation.getConversationId().equals(ChatTestConst.CONVERSATION_ID1));
        assertEquals(2, loadedConversation.getFirstLocalEventId().longValue()); // event query or socket events don't change first known id unless it's -1 (empty conversation)
        assertEquals(3, loadedConversation.getLastLocalEventId().longValue()); // was increased
        assertEquals(164, loadedConversation.getLastRemoteEventId().longValue());
        assertTrue(loadedConversation.getUpdatedOn() > 0);

        // Check message

        assertTrue(store.getMessages().size() == 1);
        ChatMessage loadedMessage = store.getMessages().get("60526ba0-76b3-4f33-9e2e-20f4a8bb548b");
        assertNotNull(loadedMessage);
        assertTrue(loadedMessage.getConversationId().equals(ChatTestConst.CONVERSATION_ID1));
        assertEquals("p1", loadedMessage.getFromWhom().getId());
        assertEquals("p1", loadedMessage.getFromWhom().getName());
        assertEquals("p1", loadedMessage.getSentBy());
        assertEquals(1, loadedMessage.getSentEventId().longValue());
        assertNotNull(loadedMessage.getParts().get(0));
        assertEquals("body", loadedMessage.getParts().get(0).getName());
        assertEquals("non", loadedMessage.getParts().get(0).getData());
        assertEquals(3, loadedMessage.getParts().get(0).getSize());
        assertEquals("text/plain", loadedMessage.getParts().get(0).getType());

        // Check message status

        Collection<ChatMessageStatus> statuses = store.getMessages().get("60526ba0-76b3-4f33-9e2e-20f4a8bb548b").getStatusUpdates();
        assertNotNull(statuses);
        ChatMessageStatus status = (ChatMessageStatus) statuses.toArray()[0];
        assertNotNull(status);
        assertEquals("60526ba0-76b3-4f33-9e2e-20f4a8bb548b", status.getMessageId());
        assertEquals("p1", status.getProfileId());
        assertEquals(LocalMessageStatus.read, status.getMessageStatus());
        assertTrue(status.getUpdatedOn() > 0);
    }

    @Test
    public void test_synchroniseStore_callback() throws IOException {

        String newETag = "eTag-A";

        // Conversations setup

        store.addConversationToStore(ChatTestConst.CONVERSATION_ID1, 2, 2, 3, 0, ChatTestConst.ETAG);
        store.addConversationToStore(ChatTestConst.CONVERSATION_ID2, -1L, -1L, 0, 0, ChatTestConst.ETAG); // will be deleted
        store.addConversationToStore(ChatTestConst.CONVERSATION_ID3, -1L, -1L, 0, 0, ChatTestConst.ETAG); // will be deleted

        ConversationDetails conversationA = new MockConversationDetails(ChatTestConst.CONVERSATION_ID1);
        ConversationDetails conversationB = new MockConversationDetails(ChatTestConst.CONVERSATION_ID4);

        List<ConversationDetails> result = new ArrayList<>();
        result.add(conversationA);
        result.add(conversationB);
        mockedComapiClient.addMockedResult(new MockResult<>(result, true, newETag, 200));

        // Events setup

        String json = FileResHelper.readFromFile(this, "rest_events_query.json");
        Parser parser = new Parser();

        Type listType = new TypeToken<ArrayList<JsonObject>>() {
        }.getType();
        List<JsonObject> list = new Gson().fromJson(json, listType);

        ConversationEventsResponse response = new ConversationEventsResponse(list, parser);
        mockedComapiClient.addMockedResult(new MockResult<>(response, true, null, 200));

        // Test
        final MockCallback<ChatResult> callback = new MockCallback<>();
        client.service().messaging().synchroniseStore(callback);

        assertTrue(callback.getResult().isSuccessful());

        // Check conversation

        assertTrue(store.getAllConversations().size() == 2);
        ChatConversationBase loadedConversation = store.getConversations().get(ChatTestConst.CONVERSATION_ID1);
        assertNotNull(loadedConversation);
        assertTrue(loadedConversation.getConversationId().equals(ChatTestConst.CONVERSATION_ID1));
        assertEquals(2, loadedConversation.getFirstLocalEventId().longValue()); // event query or socket events don't change first known id unless it's -1 (empty conversation)
        assertEquals(3, loadedConversation.getLastLocalEventId().longValue()); // was increased
        assertEquals(3, loadedConversation.getLastRemoteEventId().longValue());
        assertTrue(loadedConversation.getUpdatedOn() > 0);
        assertEquals("eTag", loadedConversation.getETag());

        // Check message

        assertTrue(store.getMessages().size() == 1);
        ChatMessage loadedMessage = store.getMessages().get("60526ba0-76b3-4f33-9e2e-20f4a8bb548b");
        assertNotNull(loadedMessage);
        assertTrue(loadedMessage.getConversationId().equals(ChatTestConst.CONVERSATION_ID1));
        assertEquals("p1", loadedMessage.getFromWhom().getId());
        assertEquals("p1", loadedMessage.getFromWhom().getName());
        assertEquals("p1", loadedMessage.getSentBy());
        assertEquals(1, loadedMessage.getSentEventId().longValue());
        assertNotNull(loadedMessage.getParts().get(0));
        assertEquals("body", loadedMessage.getParts().get(0).getName());
        assertEquals("non", loadedMessage.getParts().get(0).getData());
        assertEquals(3, loadedMessage.getParts().get(0).getSize());
        assertEquals("text/plain", loadedMessage.getParts().get(0).getType());

        // Check message status

        Collection<ChatMessageStatus> statuses = store.getMessages().get("60526ba0-76b3-4f33-9e2e-20f4a8bb548b").getStatusUpdates();
        assertNotNull(statuses);
        ChatMessageStatus status = (ChatMessageStatus) statuses.toArray()[0];
        assertNotNull(status);
        assertEquals("60526ba0-76b3-4f33-9e2e-20f4a8bb548b", status.getMessageId());
        assertEquals("p1", status.getProfileId());
        assertEquals(LocalMessageStatus.read, status.getMessageStatus());
        assertTrue(status.getUpdatedOn() > 0);
    }

    @Test
    public void test_getMessagePage() throws IOException, JSONException {

        String conversationId = "someId";
        String messageId = "60526ba0-76b3-4f33-9e2e-20f4a8bb548b";

        String json = FileResHelper.readFromFile(this, "rest_message_query_orphaned.json");
        Parser parser = new Parser();
        MessagesQueryResponse response = parser.parse(json, MessagesQueryResponse.class);

        // Put conversation into store

        mockedComapiClient.addMockedResult(new MockResult<>(response, true, ChatTestConst.ETAG, 200));

        ChatConversationBase conversationInStore = ChatConversationBase.baseBuilder()
                .setConversationId(conversationId)
                .setETag("eTag-0")
                .setFirstLocalEventId(-1L)
                .setLastLocalEventId(-1L)
                .setLastRemoteEventId(-1L)
                .setUpdatedOn(0L)
                .build();
        store.getConversations().put(conversationId, conversationInStore);

        // Process next page in conversation

        ChatResult result = client.rxService().messaging().getPreviousMessages(conversationId).toBlocking().first();
        assertTrue(result.isSuccessful());

        // Check if conversation updated

        ChatConversationBase loadedConversation = store.getConversations().get(conversationId);
        assertNotNull(loadedConversation);
        assertTrue(loadedConversation.getConversationId().equals(conversationId));
        assertTrue(loadedConversation.getFirstLocalEventId() == 132L);
        assertTrue(loadedConversation.getLastLocalEventId() == 164L);
        assertTrue(loadedConversation.getLastRemoteEventId() == 164L);
        assertTrue(loadedConversation.getUpdatedOn() > 0);

        // Check if new message added

        Map<String, ChatMessage> loadedMessages = store.getMessages();
        assertNotNull(loadedMessages);
        assertTrue(loadedMessages.size() == 1);
        assertEquals(conversationId, loadedMessages.get(messageId).getConversationId());
    }

    @Test
    public void test_getMessagePage_callback() throws IOException, JSONException {

        String conversationId = "someId";
        String messageId = "60526ba0-76b3-4f33-9e2e-20f4a8bb548b";

        String json = FileResHelper.readFromFile(this, "rest_message_query_no_orphans.json");
        Parser parser = new Parser();
        MessagesQueryResponse response = parser.parse(json, MessagesQueryResponse.class);

        // Put conversation into store

        mockedComapiClient.addMockedResult(new MockResult<>(response, true, ChatTestConst.ETAG, 200));

        ChatConversationBase conversationInStore = ChatConversationBase.baseBuilder()
                .setConversationId(conversationId)
                .setETag("eTag-0")
                .setFirstLocalEventId(-1L)
                .setLastLocalEventId(-1L)
                .setLastRemoteEventId(-1L)
                .setUpdatedOn(0L)
                .build();
        store.getConversations().put(conversationId, conversationInStore);

        // Process next page in conversation
        final MockCallback<ChatResult> callback = new MockCallback<>();
        client.service().messaging().getPreviousMessages(conversationId, callback);
        assertTrue(callback.getResult().isSuccessful());

        // Check if conversation updated

        ChatConversationBase loadedConversation = store.getConversations().get(conversationId);
        assertNotNull(loadedConversation);
        assertTrue(loadedConversation.getConversationId().equals(conversationId));
        assertTrue(loadedConversation.getFirstLocalEventId() == 132L);
        assertTrue(loadedConversation.getLastLocalEventId() == 164L);
        assertTrue(loadedConversation.getLastRemoteEventId() == 164L);
        assertTrue(loadedConversation.getUpdatedOn() > 0);

        // Check if new message added

        Map<String, ChatMessage> loadedMessages = store.getMessages();
        assertNotNull(loadedMessages);
        assertTrue(loadedMessages.size() == 1);
        assertEquals(conversationId, loadedMessages.get(messageId).getConversationId());
    }

    @Test
    public void test_updateMessageStatus() {

        String conversationId = "someId";
        String messageId1 = "id-1";

        store.addMessageToStore(messageId1);

        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 200));

        List<String> ids = new ArrayList<>();
        ids.add(messageId1);

        ChatResult result = client.rxService().messaging().markMessagesAsRead(conversationId, ids).toBlocking().first();
        assertTrue(result.isSuccessful());

        // Check message status

        Collection<ChatMessageStatus> statuses = store.getMessages().get("id-1").getStatusUpdates();
        assertNotNull(statuses);
        ChatMessageStatus status = (ChatMessageStatus) statuses.toArray()[0];

        assertNotNull(status);
        assertEquals(messageId1, status.getMessageId());
        assertEquals(MessageStatus.read.name(), status.getMessageStatus().name());
        assertEquals(client.getSession().getProfileId(), status.getProfileId());
        assertTrue(status.getUpdatedOn() > 0);
    }

    @Test
    public void test_updateMessageStatus_callbacks() {

        String conversationId = "someId";
        String messageId1 = "id-1";
        String messageId2 = "id-2";

        store.addMessageToStore(messageId1);

        List<String> ids = new ArrayList<>();
        ids.add(messageId1);

        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 200));
        final MockCallback<ChatResult> callback = new MockCallback<>();

        client.service().messaging().markMessagesAsRead(conversationId, ids, callback);
        assertTrue(callback.getResult().isSuccessful());


        Collection<ChatMessageStatus> statuses = store.getMessages().get(messageId1).getStatusUpdates();
        assertNotNull(statuses);
        ChatMessageStatus status = (ChatMessageStatus) statuses.toArray()[0];
        assertNotNull(status);
        assertEquals(messageId1, status.getMessageId());
        assertEquals(MessageStatus.read.name(), status.getMessageStatus().name());
        assertEquals(client.getSession().getProfileId(), status.getProfileId());
        assertTrue(status.getUpdatedOn() > 0);
    }

    @Test
    public void test_isTyping() {

        String conversationId = "someId";

        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 200));
        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 200));

        assertTrue(client.rxService().messaging().isTyping(conversationId, true).toBlocking().first().isSuccessful());
        assertTrue(client.rxService().messaging().isTyping(conversationId, false).toBlocking().first().isSuccessful());
    }

    @Test
    public void test_isTyping_callbacks() {

        String conversationId = "someId";

        final MockCallback<ChatResult> callback = new MockCallback<>();

        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 200));
        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 200));

        client.service().messaging().isTyping(conversationId, true, callback);
        assertTrue(callback.getResult().isSuccessful());
        callback.reset();
        client.service().messaging().isTyping(conversationId, false, callback);
        assertTrue(callback.getResult().isSuccessful());
    }

    @Test
    public void test_profileAPIs() {

        mockedComapiClient.addMockedResult(new MockResult<>(new HashMap<>(), true, ChatTestConst.ETAG, 200));
        ComapiResult<Map<String, Object>> result1a = client.rxService().profile().getProfile("id").toBlocking().first();
        assertNotNull(result1a.getResult());

        mockedComapiClient.addMockedResult(new MockResult<>(new ArrayList(), true, ChatTestConst.ETAG, 200));
        ComapiResult<List<Map<String, Object>>> result2 = client.rxService().profile().queryProfiles("query").toBlocking().first();
        assertNotNull(result2.getResult());

        mockedComapiClient.addMockedResult(new MockResult<>(new HashMap<>(), true, ChatTestConst.ETAG, 200));
        ComapiResult<Map<String, Object>> result3 = client.rxService().profile().updateProfile(new HashMap<>(), "eTag").toBlocking().first();
        assertNotNull(result3.getResult());
    }

    @Test
    public void test_profileAPIs_callbacks() {

        mockedComapiClient.addMockedResult(new MockResult<>(new HashMap<>(), true, ChatTestConst.ETAG, 200));
        final MockCallback<ComapiResult<Map<String, Object>>> callback1 = new MockCallback<>();
        client.service().profile().getProfile("id", callback1);
        assertNotNull(callback1.getResult());

        mockedComapiClient.addMockedResult(new MockResult<>(new ArrayList<>(), true, ChatTestConst.ETAG, 200));
        final MockCallback<ComapiResult<List<Map<String, Object>>>> callback2 = new MockCallback<>();
        client.service().profile().queryProfiles("query", callback2);
        assertNotNull(callback2.getResult());

        mockedComapiClient.addMockedResult(new MockResult<>(new HashMap<>(), true, ChatTestConst.ETAG, 200));
        final MockCallback<ComapiResult<Map<String, Object>>> callback3 = new MockCallback<>();
        client.service().profile().updateProfile(new HashMap<>(), "eTag", callback3);
        assertNotNull(callback3.getResult());
    }

    @Test
    public void test_patchProfile_callback() throws InterruptedException {

        final MockCallback<ComapiResult<Map<String, Object>>> c = new MockCallback<>();

        mockedComapiClient.addMockedResult(new MockResult<>(new HashMap<>(), true, ChatTestConst.ETAG, 200));
        client.service().profile().patchProfile("id", new HashMap<>(), "eTag", c);
        synchronized (c) {
            c.wait(TIME_OUT);
            c.notifyAll();
        }
        assertTrue(c.getResult().isSuccessful());
        c.reset();

        mockedComapiClient.addMockedResult(new MockResult<>(new HashMap(), true, ChatTestConst.ETAG, 200));
        client.service().profile().patchMyProfile(new HashMap<>(), "eTag", c);
        synchronized (c) {
            c.wait(TIME_OUT);
            c.notifyAll();
        }
        assertTrue(c.getResult().isSuccessful());
    }

    @Test
    public void test_sessionAPIs() {

        Session result1 = client.rxService().session().startSession().toBlocking().first();
        assertNotNull(result1);

        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 200));
        ChatResult result2 = client.rxService().session().endSession().toBlocking().first();
        assertTrue(result2.isSuccessful());
    }

    @Test
    public void test_sessionAPIs_callback() {

        final MockCallback<Session> callback1 = new MockCallback<>();
        client.service().session().startSession(callback1);
        assertNotNull(callback1.getResult());

        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 200));
        final MockCallback<ChatResult> callback2 = new MockCallback<>();
        client.service().session().endSession(callback2);
        assertNotNull(callback2.getResult());
    }

    @After
    public void tearDown() throws Exception {
        mockedComapiClient.clearResults();
        mockedComapiClient.clean(RuntimeEnvironment.application);
        store.clearDatabase();
        client.close(RuntimeEnvironment.application);
    }
}
