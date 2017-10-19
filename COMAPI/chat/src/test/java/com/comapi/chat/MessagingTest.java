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
import android.text.TextUtils;

import com.comapi.APIConfig;
import com.comapi.Callback;
import com.comapi.ComapiAuthenticator;
import com.comapi.chat.helpers.ChatTestConst;
import com.comapi.chat.helpers.FileResHelper;
import com.comapi.chat.helpers.MockCallback;
import com.comapi.chat.helpers.MockComapiClient;
import com.comapi.chat.helpers.MockFoundationFactory;
import com.comapi.chat.helpers.MockResult;
import com.comapi.chat.helpers.TestChatStore;
import com.comapi.chat.internal.AttachmentController;
import com.comapi.chat.internal.MessageProcessor;
import com.comapi.chat.model.Attachment;
import com.comapi.chat.model.ChatMessage;
import com.comapi.chat.model.ChatParticipant;
import com.comapi.chat.model.ChatStore;
import com.comapi.internal.CallbackAdapter;
import com.comapi.internal.Parser;
import com.comapi.internal.log.LogLevel;
import com.comapi.internal.log.LogManager;
import com.comapi.internal.log.Logger;
import com.comapi.internal.network.AuthClient;
import com.comapi.internal.network.ChallengeOptions;
import com.comapi.internal.network.model.conversation.Participant;
import com.comapi.internal.network.model.messaging.MessageSentResponse;
import com.comapi.internal.network.model.messaging.MessageToSend;
import com.comapi.internal.network.model.messaging.Part;
import com.comapi.internal.network.model.messaging.UploadContentResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.Subscriber;

import static com.comapi.chat.helpers.ChatTestConst.TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
@Config(manifest = "chat/src/main/AndroidManifest.xml", sdk = Build.VERSION_CODES.M, constants = BuildConfig.class, packageName = "com.comapi.chat")
public class MessagingTest {

    private static final long TIME_OUT = 10000;

    private StoreFactory<ChatStore> factory;
    private ComapiChatClient client;
    private MockComapiClient mockedComapiClient;
    private final MockCallback<?> callback = new MockCallback<>();
    private TestChatStore store;
    private Logger logger;
    private AttachmentController attachmentController;

    private MockFoundationFactory foundationFactory;

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

        foundationFactory = new MockFoundationFactory(ChatConfig::buildComapiConfig);

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

        LogManager logMgr = new LogManager();
        logMgr.init(RuntimeEnvironment.application, LogLevel.OFF.getValue(), LogLevel.OFF.getValue(), 0);
        logger = new Logger(logMgr, "");

        attachmentController = new AttachmentController(logger, 13333);
    }

    @Test
    public void test_upload() throws IOException {

        String json = FileResHelper.readFromFile(this, "upload_content.json");
        Parser parser = new Parser();
        UploadContentResponse response = parser.parse(json, UploadContentResponse.class);

        mockedComapiClient.addMockedResult(new MockResult<>(response, true, ChatTestConst.ETAG, 200));
        mockedComapiClient.addMockedResult(new MockResult<>(response, true, ChatTestConst.ETAG, 200));
        mockedComapiClient.addMockedResult(new MockResult<>(response, true, ChatTestConst.ETAG, 200));

        List<Attachment> list = createAttachments();

        List<Attachment> result = attachmentController.uploadAttachments(list, foundationFactory.getMockedClient()).toBlocking().first();
        assertNotNull(result);

        assertTrue(result.size() == 3);
        for (int i=0; i< 3; i++) {
            assertEquals("https://url", result.get(i).getUrl());
            assertEquals(2662193, result.get(i).getSize());
            assertEquals("image/jpeg", result.get(i).getType());
            assertEquals("test", result.get(i).getFolder());
            assertEquals("152f860e6f5951a3afbcc42654daddd6a2863262", result.get(i).getId());
        }
    }

    @Test
    public void test_getParticipants() throws IOException, JSONException, InterruptedException {

        Parser parser = new Parser();

        List<Participant> participants = new ArrayList<>();
        String json = FileResHelper.readFromFile(this, "rest_participants_get.json");
        JSONArray jsonarray = new JSONArray(json);
        for (int i = 0; i < jsonarray.length(); i++) {
            JSONObject jsonobject = jsonarray.getJSONObject(i);
            Participant p = parser.parse(jsonobject.toString(), Participant.class);
            participants.add(p);
        }

        final MockCallback<List<ChatParticipant>> c = new MockCallback<>();

        mockedComapiClient.addMockedResult(new MockResult<>(participants, true, ChatTestConst.ETAG, 200));

        client.service().messaging().getParticipants(ChatTestConst.CONVERSATION_ID1, c);

        synchronized (c) {
            c.wait(TIME_OUT);
            c.notifyAll();
        }

        assertTrue(c.getResult().size() == 3);
    }

    @Test
    public void test_removeParticipants() throws IOException, JSONException, InterruptedException {

        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 200));

        final MockCallback<ChatResult> c = new MockCallback<>();

        client.service().messaging().removeParticipants(ChatTestConst.CONVERSATION_ID1, new ArrayList<>(), c);

        synchronized (c) {
            c.wait(TIME_OUT);
            c.notifyAll();
        }

        assertTrue(c.getResult().isSuccessful());
    }

    @Test
    public void test_addParticipants() throws IOException, JSONException, InterruptedException {

        store.addConversationToStore(ChatTestConst.CONVERSATION_ID1, 0, 0, 0, 0, ChatTestConst.ETAG);
        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 200));

        final MockCallback<ChatResult> c = new MockCallback<>();

        ArrayList<Participant> participants = new ArrayList<>();
        participants.add(Participant.builder().setId("id").build());

        client.service().messaging().addParticipants(ChatTestConst.CONVERSATION_ID1, participants, c);

        synchronized (c) {
            c.wait(TIME_OUT);
            c.notifyAll();
        }

        assertTrue(c.getResult().isSuccessful());
    }

    @Test
    public void test_sendMessage_msgObj() throws IOException, JSONException, InterruptedException {

        Parser parser = new Parser();
        String json = FileResHelper.readFromFile(this, "rest_message_sent.json");
        MessageSentResponse response = parser.parse(json, MessageSentResponse.class);

        final MockCallback<ChatResult> c = new MockCallback<>();

        store.addConversationToStore(ChatTestConst.CONVERSATION_ID1, 0, 0, 0, 0, ChatTestConst.ETAG);
        mockedComapiClient.addMockedResult(new MockResult<>(response, true, ChatTestConst.ETAG, 200));

        MessageToSend messsage = MessageToSend.builder().addPart(Part.builder().setData("text").setType("text/plain").build()).build();
        client.service().messaging().sendMessage(ChatTestConst.CONVERSATION_ID1, messsage, null, c);

        synchronized (c) {
            c.wait(TIME_OUT);
            c.notifyAll();
        }

        assertTrue(c.getResult().isSuccessful());
        assertEquals(1, store.getMessages().size());
    }

    @Test
    public void test_sendMessage_body() throws IOException, JSONException, InterruptedException {

        Parser parser = new Parser();
        String json = FileResHelper.readFromFile(this, "rest_message_sent.json");
        MessageSentResponse response = parser.parse(json, MessageSentResponse.class);

        final MockCallback<ChatResult> c = new MockCallback<>();

        store.addConversationToStore(ChatTestConst.CONVERSATION_ID1, 0, 0, 0, 0, ChatTestConst.ETAG);
        mockedComapiClient.addMockedResult(new MockResult<>(response, true, ChatTestConst.ETAG, 200));

        client.service().messaging().sendMessage(ChatTestConst.CONVERSATION_ID1, "body", null, c);

        synchronized (c) {
            c.wait(TIME_OUT);
            c.notifyAll();
        }

        assertTrue(c.getResult().isSuccessful());
        assertEquals(1, store.getMessages().size());
    }

    @Test
    public void test_preparePreUpload() throws IOException {

        String conversationId = "conversationId";
        String profileId = "profileId";

        MessageToSend messsage = MessageToSend.builder().addPart(Part.builder().setData("text").setType("text/plain").build()).build();

        List<Attachment> list = createAttachments();

        MessageProcessor mP = attachmentController.createMessageProcessor(messsage, list, conversationId, profileId);

        assertEquals(conversationId, mP.getConversationId());
        assertEquals(profileId, mP.getSender());
        assertNotNull(mP.getTempId());
        assertEquals(3, mP.getAttachments().size());
        assertEquals(1, mP.getMessage().getParts().size());

        mP.preparePreUpload();
        ChatMessage prepared = mP.createTempMessage();

        // Only local temp message will be populated with new Parts
        assertEquals(4, prepared.getParts().size());
        assertEquals(1, mP.getMessage().getParts().size());

        List<Part> attachmentParts = new ArrayList<>();
        for (int i=0; i< 4; i++) {
            if (!TextUtils.equals(prepared.getParts().get(i).getType(), "text/plain")) {
                attachmentParts.add(prepared.getParts().get(i));
            }
        }

        for (int i=0; i< 3; i++) {
            assertEquals(Attachment.LOCAL_PART_TYPE_UPLOADING, attachmentParts.get(i).getType());
        }
    }

    @Test
    public void test_preparePostUpload() throws IOException {

        String conversationId = "conversationId";
        String profileId = "profileId";

        MessageToSend message = MessageToSend.builder().addPart(Part.builder().setData("text").setType("text/plain").build()).build();

        List<Attachment> list = createAttachments();

        MessageProcessor mP = attachmentController.createMessageProcessor(message, list, conversationId, profileId);

        assertEquals(conversationId, mP.getConversationId());
        assertEquals(profileId, mP.getSender());
        assertNotNull(mP.getTempId());
        assertEquals(3, mP.getAttachments().size());
        assertEquals(1, mP.getMessage().getParts().size());

        mP.preparePreUpload();
        mP.preparePostUpload(list);
        mP.prepareMessageToSend();

        // Attachment details will populate message to send with new Parts
        assertEquals(4, message.getParts().size());

        List<Part> attachmentParts = new ArrayList<>();
        for (int i=0; i< 4; i++) {
            if (!TextUtils.equals(message.getParts().get(i).getType(), "text/plain")) {
                attachmentParts.add(message.getParts().get(i));
            }
        }

        for (int i=0; i< 3; i++) {
            assertEquals("dataType", attachmentParts.get(i).getType());
        }
    }

    @Test
    public void test_finalMessage() throws IOException {

        String json = FileResHelper.readFromFile(this, "rest_message_sent.json");
        Parser parser = new Parser();
        MessageSentResponse response = parser.parse(json, MessageSentResponse.class);

        String conversationId = "conversationId";
        String messageId = "someId";
        String profileId = "profileId";

        MessageToSend messageToSend = MessageToSend.builder().addPart(Part.builder().setData("text").setType("text/plain").build()).build();

        List<Attachment> list = createAttachments();

        MessageProcessor mP = attachmentController.createMessageProcessor(messageToSend, list, conversationId, profileId);

        mP.preparePreUpload();
        mP.preparePostUpload(list);
        mP.prepareMessageToSend();
        ChatMessage message = mP.createFinalMessage(response);

        assertEquals(conversationId, message.getConversationId());
        assertEquals(messageId, message.getMessageId());
        assertEquals(profileId, message.getSentBy());
        assertEquals(profileId, message.getFromWhom().getId());
        assertNotNull(message.getMetadata().get("tempIdAndroid"));
        assertEquals(1L, message.getSentEventId().longValue());
        assertTrue(message.getSentOn() > 0);

        // Attachment details will populate final message
        assertEquals(4, message.getParts().size());
        List<Part> attachmentParts = new ArrayList<>();
        for (int i=0; i< 4; i++) {
            if (!TextUtils.equals(message.getParts().get(i).getType(), "text/plain")) {
                attachmentParts.add(message.getParts().get(i));
            }
        }

        for (int i=0; i< 3; i++) {
            assertEquals("dataType", attachmentParts.get(i).getType());
        }
    }

    @Test
    public void test_errorUploading() throws IOException {

        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 500));
        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 500));
        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 500));

        List<Attachment> list = createAttachments();
        int one = list.get(0).hashCode();
        int two = list.get(1).hashCode();
        int three = list.get(2).hashCode();

        List<Attachment> result = attachmentController.uploadAttachments(list, foundationFactory.getMockedClient()).toBlocking().first();
        assertNotNull(result);

        assertTrue(result.size() == 3);
        for (int i=0; i< 3; i++) {
            assertNotNull(result.get(i).getError());
        }

        String conversationId = "conversationId";
        String profileId = "profileId";
        MessageToSend messageToSend = MessageToSend.builder().addPart(Part.builder().setData("text").setType("text/plain").build()).build();
        MessageProcessor mP = attachmentController.createMessageProcessor(messageToSend, list, conversationId, profileId);

        mP.preparePreUpload();
        mP.preparePostUpload(list);
        mP.prepareMessageToSend();

        assertEquals(1, messageToSend.getParts().size());
        assertEquals("text/plain", messageToSend.getParts().get(0).getType());

        String json = FileResHelper.readFromFile(this, "rest_message_sent.json");
        Parser parser = new Parser();
        MessageSentResponse response = parser.parse(json, MessageSentResponse.class);
        ChatMessage finalMessage = mP.createFinalMessage(response);

        List<Part> attachmentParts = new ArrayList<>();
        for (int i=0; i< 4; i++) {
            if (!TextUtils.equals(finalMessage.getParts().get(i).getType(), "text/plain")) {
                attachmentParts.add(finalMessage.getParts().get(i));
            }
        }

        for (int i=0; i< 3; i++) {
            assertEquals(Attachment.LOCAL_PART_TYPE_ERROR, attachmentParts.get(i).getType());
        }
    }

    @After
    public void tearDown() throws Exception {
        store.clearDatabase();
        mockedComapiClient.clearResults();
        mockedComapiClient.clean(RuntimeEnvironment.application);
        callback.reset();
    }

    private List<Attachment> createAttachments() {
        List<Attachment> list = new ArrayList<>();
        list.add(Attachment.create("", "dataType", "test", "name"));
        list.add(Attachment.create(new File(""), "dataType", "test", "name"));
        list.add(Attachment.create(new byte[0], "dataType", "test", "name"));
        return list;
    }
}