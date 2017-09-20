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
import android.text.TextUtils;

import com.comapi.APIConfig;
import com.comapi.ComapiAuthenticator;
import com.comapi.RxComapiClient;
import com.comapi.chat.database.Database;
import com.comapi.chat.database.model.DbOrphanedEvent;
import com.comapi.chat.helpers.ChatTestConst;
import com.comapi.chat.helpers.FileResHelper;
import com.comapi.chat.helpers.MockCallback;
import com.comapi.chat.helpers.MockComapiClient;
import com.comapi.chat.helpers.MockConversationDetails;
import com.comapi.chat.helpers.MockFoundationFactory;
import com.comapi.chat.helpers.MockResult;
import com.comapi.chat.helpers.TestChatStore;
import com.comapi.chat.internal.AttachmentController;
import com.comapi.chat.model.Attachment;
import com.comapi.chat.model.ChatConversation;
import com.comapi.chat.model.ChatConversationBase;
import com.comapi.chat.model.ChatMessage;
import com.comapi.chat.model.ChatMessageStatus;
import com.comapi.chat.model.ChatStore;
import com.comapi.chat.model.LocalMessageStatus;
import com.comapi.chat.model.ModelAdapter;
import com.comapi.internal.ComapiException;
import com.comapi.internal.Parser;
import com.comapi.internal.helpers.DateHelper;
import com.comapi.internal.log.LogLevel;
import com.comapi.internal.log.LogManager;
import com.comapi.internal.log.Logger;
import com.comapi.internal.network.AuthClient;
import com.comapi.internal.network.ChallengeOptions;
import com.comapi.internal.network.ComapiResult;
import com.comapi.internal.network.model.conversation.Conversation;
import com.comapi.internal.network.model.conversation.ConversationDetails;
import com.comapi.internal.network.model.conversation.ConversationUpdate;
import com.comapi.internal.network.model.messaging.ConversationEventsResponse;
import com.comapi.internal.network.model.messaging.MessageSentResponse;
import com.comapi.internal.network.model.messaging.MessageStatus;
import com.comapi.internal.network.model.messaging.MessageStatusUpdate;
import com.comapi.internal.network.model.messaging.MessageToSend;
import com.comapi.internal.network.model.messaging.MessagesQueryResponse;
import com.comapi.internal.network.model.messaging.OrphanedEvent;
import com.comapi.internal.network.model.messaging.Part;
import com.comapi.internal.network.model.messaging.Sender;
import com.comapi.internal.network.model.messaging.UploadContentResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import junit.framework.Assert;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rx.Observable;
import rx.schedulers.Schedulers;

import static com.comapi.chat.EventsHandler.MESSAGE_METADATA_TEMP_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
@Config(manifest = "chat/src/main/AndroidManifest.xml", sdk = Build.VERSION_CODES.M, constants = BuildConfig.class, packageName = "com.comapi.chat")
public class ControllerTest {

    private static final long TIME_OUT = 10000;

    private StoreFactory<ChatStore> factory;
    private ComapiChatClient client;
    private EventsHandler eventsHandler;
    private MockComapiClient mockedComapiClient;
    private final MockCallback<Boolean> callback = new MockCallback<>();
    private ChatController chatController;
    private TestChatStore store;
    private Database db;
    private PersistenceController persistenceController;
    private Logger logger;
    private AttachmentController attachmentController;


    @Before
    public void setUpChat() throws Exception {

        APIConfig apiConfig = new APIConfig().service("http://localhost:59273/").socket("ws://10.0.0.0");

        store = new TestChatStore();

        StoreFactory<ChatStore> factory = new StoreFactory<ChatStore>() {
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
                        authClient.authenticateWithToken(ChatTestConst.TOKEN);
                    }
                })
                .apiConfiguration(apiConfig)
                .store(factory)
                .observableExecutor(new ObservableExecutor() {
                    @Override
                    <T> void execute(Observable<T> obs) {
                        obs.observeOn(Schedulers.test()).subscribeOn(Schedulers.immediate()).subscribe();
                    }
                });

        client = ComapiChat.initialise(RuntimeEnvironment.application, chatConfig).toBlocking().first();
        mockedComapiClient = foundationFactory.getMockedClient();
        eventsHandler = foundationFactory.getMockedEventsHandler();

        LogManager logMgr = new LogManager();
        logMgr.init(RuntimeEnvironment.application, LogLevel.OFF.getValue(), LogLevel.OFF.getValue(), 0);
        logger = new Logger(logMgr, "");

        ModelAdapter modelAdapter = new ModelAdapter();
        db = Database.getInstance(RuntimeEnvironment.application, true, new Logger(logMgr, ""));
        persistenceController = new PersistenceController(db, modelAdapter, factory, logger);

        InternalConfig internal = new InternalConfig();

        attachmentController = new AttachmentController(logger, internal.getMaxPartDataSize());

        chatController = new ChatController(mockedComapiClient, persistenceController, attachmentController, internal, chatConfig.getObservableExecutor(), modelAdapter, logger);
    }

    @Test
    public void test_sendMessageWithAttachments() throws IOException {

        String conversationId = "conversationId";

        ChatConversationBase conversationInStore = ChatConversationBase.baseBuilder()
                .setConversationId(conversationId)
                .setETag("eTag-0")
                .setFirstLocalEventId(1L)
                .setLastLocalEventId(2L)
                .setLastRemoteEventId(2L)
                .setUpdatedOn(0L)
                .build();
        store.getConversations().put(conversationId, conversationInStore);

        String json = FileResHelper.readFromFile(this, "upload_content.json");
        Parser parser = new Parser();
        UploadContentResponse response1 = parser.parse(json, UploadContentResponse.class);
        UploadContentResponse response2 = parser.parse(json, UploadContentResponse.class);
        UploadContentResponse response3 = parser.parse(json, UploadContentResponse.class);

        String json2 = FileResHelper.readFromFile(this, "rest_message_sent.json");
        MessageSentResponse response4 = parser.parse(json2, MessageSentResponse.class);

        mockedComapiClient.addMockedResult(new MockResult<>(response1, true, ChatTestConst.ETAG, 200));
        mockedComapiClient.addMockedResult(new MockResult<>(response2, true, ChatTestConst.ETAG, 200));
        mockedComapiClient.addMockedResult(new MockResult<>(response3, true, ChatTestConst.ETAG, 200));
        mockedComapiClient.addMockedResult(new MockResult<>(response4, true, ChatTestConst.ETAG, 200));

        MessageToSend messsage = MessageToSend.builder().addPart(Part.builder().setData("text").setType("text/plain").build()).build();
        List<Attachment> list = createAttachments();

        ChatResult result = chatController.sendMessageWithAttachments(conversationId, messsage, list).toBlocking().first();
        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertEquals(null, result.getError());

        Map<String, ChatMessage> saved = store.getMessages();
        assertEquals(1, saved.size());
        ChatMessage savedMessage = saved.get("someId");


        assertEquals(4, savedMessage.getParts().size());
        assertEquals(1L, savedMessage.getSentEventId().longValue());
        assertEquals("someId", savedMessage.getMessageId());
        assertEquals("conversationId", savedMessage.getConversationId());
        assertEquals("profileId-123", savedMessage.getSentBy());
        assertEquals("profileId-123", savedMessage.getFromWhom().getId());
        assertEquals("profileId-123", savedMessage.getSentBy());
        assertNotNull(savedMessage.getMetadata().get("tempIdAndroid"));
        assertTrue(savedMessage.getSentOn() > 0);

        List<Part> attachmentParts = new ArrayList<>();
        for (int i=0; i< 4; i++) {
            if (!TextUtils.equals(savedMessage.getParts().get(i).getType(), "text/plain")) {
                attachmentParts.add(savedMessage.getParts().get(i));
            }
        }

        for (int i=0; i< 3; i++) {
            assertEquals("https://url", attachmentParts.get(i).getUrl());
            assertEquals(2662193, attachmentParts.get(i).getSize());
            assertEquals("image/jpeg", attachmentParts.get(i).getType());
            assertEquals("152f860e6f5951a3afbcc42654daddd6a2863262", attachmentParts.get(i).getName());
        }

    }

    @Test
    public void test_MarkDelivered() {

        String conversationId = "id-1";
        Set<String> ids = new HashSet<>();
        ids.add("id-2");
        ids.add("id-3");

        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 200));

        ComapiResult<Void> result = chatController.markDelivered(conversationId, ids).toBlocking().first();
        assertNotNull(result);
        assertNull(result.getResult());
        assertTrue(result.isSuccessful());
        assertEquals(200, result.getCode());
    }

    @Test
    public void test_MarkDelivered_failed() {

        String conversationId = "id-1";
        Set<String> ids = new HashSet<>();
        ids.add("id-2");
        ids.add("id-3");

        mockedComapiClient.addMockedResult(new MockResult<>(null, false, null, 500));
        mockedComapiClient.addMockedResult(new MockResult<>(null, false, null, 500));
        mockedComapiClient.addMockedResult(new MockResult<>(null, false, null, 500));
        mockedComapiClient.addMockedResult(new MockResult<>(null, false, null, 500));

        List<ComapiResult<Void>> result = chatController.markDelivered(conversationId, ids).toList().toBlocking().first();
        assertTrue(result.isEmpty());
    }

    @Test
    public void test_HandleMessage_noLocalConversation_shouldGetOne() {

        String conversationId = "id-1";
        String tempId = "temp-1";
        String sender = client.getSession().getProfileId();

        ConversationDetails conversation = new MockConversationDetails(conversationId);
        mockedComapiClient.addMockedResult(new MockResult<>(conversation, true, ChatTestConst.ETAG, 200));

        MessageToSend message = MessageToSend.builder().addPart(Part.builder().setData("hello").build()).build();
        List<Part> parts = new ArrayList<>();
        parts.add(Part.builder().setData("hello").build());
        ChatMessage chatMessage = ChatMessage.builder().setMessageId(tempId).setConversationId(conversationId).setSentBy(sender).setFromWhom(new Sender(sender, sender)).setParts(parts).build();

        Boolean result = chatController.handleMessage(chatMessage).toBlocking().first();
        assertTrue(result);

        // Check if message in the store

        ChatMessage loadedMsg = store.getMessages().get(tempId);
        assertNotNull(loadedMsg);
        assertTrue(loadedMsg.getConversationId().equals(conversationId));
        assertTrue(loadedMsg.getSentBy().equals(sender));
        assertTrue(loadedMsg.getFromWhom().getId().equals(sender));
        assertTrue(loadedMsg.getParts().get(0).getData().equals("hello"));

        ChatConversationBase loadedConversation = store.getConversations().get(conversationId);
        assertNotNull(loadedConversation);
        assertTrue(loadedConversation.getConversationId().equals(conversationId));
    }

    @Test
    public void test_HandleMessageFromOtherUser() {

        String conversationId = "id-1";
        String tempId = "temp-1";
        String sender = "someone";

        // Put conversation into store

        ChatConversationBase conversationInStore = ChatConversationBase.baseBuilder()
                .setConversationId(conversationId)
                .setETag("eTag-0")
                .setFirstLocalEventId(1L)
                .setLastLocalEventId(2L)
                .setLastRemoteEventId(2L)
                .setUpdatedOn(0L)
                .build();
        store.getConversations().put(conversationId, conversationInStore);

        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 200));

        List<Part> parts = new ArrayList<>();
        parts.add(Part.builder().setData("hello").build());
        ChatMessage chatMessage = ChatMessage.builder().setMessageId(tempId).setConversationId(conversationId).setSentBy(sender).setFromWhom(new Sender(sender, sender)).setParts(parts).setSentOn(0L).build();

        Boolean result = chatController.handleMessage(chatMessage).toBlocking().first();
        assertTrue(result);

        // Check if message in the store

        ChatMessage loadedMsg = store.getMessages().get(tempId);
        assertNotNull(loadedMsg);
        assertTrue(loadedMsg.getConversationId().equals(conversationId));
        assertTrue(loadedMsg.getSentBy().equals(sender));
        assertTrue(loadedMsg.getFromWhom().getId().equals(sender));
        assertTrue(loadedMsg.getParts().get(0).getData().equals("hello"));

        ChatConversationBase loadedConversation = store.getConversations().get(conversationId);
        assertNotNull(loadedConversation);
        assertTrue(loadedConversation.getConversationId().equals(conversationId));
    }

    @Test
    public void test_noLocalConversation_shouldGetOne_failed() {

        String conversationId = "id-1";

        mockedComapiClient.addMockedResult(new MockResult<>(null, false, null, 500));

        ChatResult result = chatController.handleNoLocalConversation(conversationId).toBlocking().first();
        // Check if sdk reacted with no errors when updating for a missing conversation
        assertFalse(result.isSuccessful());
        assertFalse(result.getError() == null);

        // Check if missing conversation has not been saved
        ChatConversationBase loadedConversation = store.getConversations().get(conversationId);
        assertNull(loadedConversation);
    }

    @Test
    public void test_HandleMessage_localConversationExists() {

        String conversationId = "id-1";
        String messageId = "id-2";
        String tempId = "temp-1";
        String sender = client.getSession().getProfileId();

        // Put conversation into store

        ChatConversationBase conversationInStore = ChatConversationBase.baseBuilder()
                .setConversationId(conversationId)
                .setETag("eTag-0")
                .setFirstLocalEventId(1L)
                .setLastLocalEventId(2L)
                .setLastRemoteEventId(2L)
                .setUpdatedOn(0L)
                .build();
        store.getConversations().put(conversationId, conversationInStore);

        Part part = Part.builder().setData("hello").build();
        List<Part> parts = new ArrayList<>();
        parts.add(part);

        // Put temp message into store

        ChatMessage tempChatMessage = ChatMessage.builder()
                .setMessageId(tempId)
                .setConversationId(conversationId)
                .setSentBy(sender)
                .setFromWhom(new Sender(sender, sender))
                .setParts(parts)
                .setSentOn(System.currentTimeMillis()).build();
        store.getMessages().put(tempId, tempChatMessage);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(MESSAGE_METADATA_TEMP_ID, tempId);
        ChatMessage chatMessage = ChatMessage.builder()
                .setMetadata(metadata)
                .setMessageId(messageId)
                .setConversationId(conversationId)
                .setSentBy(sender)
                .setFromWhom(new Sender(sender, sender))
                .setParts(parts)
                .setSentOn(System.currentTimeMillis()).build();

        Boolean result = chatController.handleMessage(chatMessage).toBlocking().first();
        assertTrue(result);

        // Check if only the right message is in the store

        ChatMessage loadedTempMsg = store.getMessages().get(tempId);
        assertNull(loadedTempMsg);

        ChatMessage loadedMsg = store.getMessages().get(messageId);
        assertNotNull(loadedMsg);
        assertTrue(loadedMsg.getConversationId().equals(conversationId));
        assertTrue(loadedMsg.getSentBy().equals(sender));
        assertTrue(loadedMsg.getFromWhom().getId().equals(sender));
        assertTrue(loadedMsg.getParts().get(0).getData().equals("hello"));

        // Check if conversation is still in the store

        ChatConversationBase loadedConversation = store.getConversations().get(conversationId);
        assertNotNull(loadedConversation);
        assertTrue(loadedConversation.getConversationId().equals(conversationId));
    }

    @Test
    public void test_GetMessagePage_localConversationExists() throws IOException, JSONException {

        String conversationId = "someId";
        String messageId = "60526ba0-76b3-4f33-9e2e-20f4a8bb548b";
        String orphanedMessageId = "cbe04573-cf2f-4f8e-bc25-ddbea192ab98";

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

        // Put orphaned event for message

        /*
         * Load orphaned events from json file.
         */
        List<OrphanedEvent> orphanedEventsFromFile = new ArrayList<>();
        json = FileResHelper.readFromFile(this, "orphaned_events_array.json");
        JSONArray jsonarray = new JSONArray(json);
        for (int i = 0; i < jsonarray.length(); i++) {
            org.json.JSONObject object = jsonarray.getJSONObject(i);
            orphanedEventsFromFile.add(parser.parse(object.toString(), OrphanedEvent.class));
        }
        /*
         * Check if two rows are added to the table.
         */
        Assert.assertEquals(2, db.save(orphanedEventsFromFile).toBlocking().first().intValue());

        // Process next page in conversation

        ChatResult result = chatController.getPreviousMessages(conversationId).toBlocking().first();
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

        // Check if orphaned events added

        String[] ids = new String[1];
        ids[0] = orphanedMessageId;
        List<DbOrphanedEvent> orphanedEvents = db.queryOrphanedEvents(ids).toBlocking().first();
        assertTrue(orphanedEvents.size() == 26);
        for (DbOrphanedEvent oe : orphanedEvents) {
            assertTrue(!TextUtils.isEmpty(oe.id()));
            assertTrue(!TextUtils.isEmpty(oe.messageId()));
            assertTrue(!TextUtils.isEmpty(oe.event()));
        }

        // Check if orphaned events for new message was removed

        ids = new String[2];
        ids[0] = messageId;
        orphanedEvents = db.queryOrphanedEvents(ids).toBlocking().first();
        assertTrue(orphanedEvents.size() == 0);
    }

    @Test
    public void test_Comparison() {

        store.addConversationToStore(ChatTestConst.CONVERSATION_ID1, -1L, 1, 3, 0, ChatTestConst.ETAG);
        store.addConversationToStore(ChatTestConst.CONVERSATION_ID2, -1L, -1L, 3, 0, ChatTestConst.ETAG);
        store.addConversationToStore(ChatTestConst.CONVERSATION_ID3, -1L, -1L, 3, 0, ChatTestConst.ETAG);

        Conversation conversationA = new MockConversationDetails(ChatTestConst.CONVERSATION_ID1);
        Conversation conversationB = new MockConversationDetails(ChatTestConst.CONVERSATION_ID4);
        List<Conversation> result = new ArrayList<>();
        result.add(conversationA);
        result.add(conversationB);
        mockedComapiClient.addMockedResult(new MockResult<>(result, true, ChatTestConst.ETAG, 200));

        ChatController.ConversationComparison comparison = chatController.compare(result, store.getAllConversations());

        assertEquals(1, comparison.conversationsToAdd.size());
        assertEquals(ChatTestConst.CONVERSATION_ID4, comparison.conversationsToAdd.get(0).getConversationId());
        assertEquals(2, comparison.conversationsToDelete.size());
        assertEquals(ChatTestConst.CONVERSATION_ID2, comparison.conversationsToDelete.get(1).getConversationId());
        assertEquals(ChatTestConst.CONVERSATION_ID3, comparison.conversationsToDelete.get(0).getConversationId());
        assertEquals(1, comparison.conversationsToUpdate.size());
        assertEquals(ChatTestConst.CONVERSATION_ID1, comparison.conversationsToUpdate.get(0).getConversationId());

        comparison = chatController.compare(null, store.getAllConversations());
        assertEquals(3, comparison.conversationsToDelete.size());
        assertEquals(0, comparison.conversationsToUpdate.size());
        assertEquals(0, comparison.conversationsToAdd.size());


        comparison = chatController.compare(result, null);
        assertEquals(0, comparison.conversationsToDelete.size());
        assertEquals(0, comparison.conversationsToUpdate.size());
        assertEquals(2, comparison.conversationsToAdd.size());
    }

    @Test
    public void test_updateLocalConversationList() throws NoSuchFieldException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        String newETag = "eTag-A";

        store.addConversationToStore(ChatTestConst.CONVERSATION_ID1, -1L, 1, 3, 0, ChatTestConst.ETAG);
        store.addConversationToStore(ChatTestConst.CONVERSATION_ID2, -1L, -1L, 3, 0, ChatTestConst.ETAG);
        store.addConversationToStore(ChatTestConst.CONVERSATION_ID3, -1L, -1L, 3, 0, ChatTestConst.ETAG);

        Conversation conversationA = new MockConversationDetails(ChatTestConst.CONVERSATION_ID1).setETag(newETag);
        Conversation conversationB = new MockConversationDetails(ChatTestConst.CONVERSATION_ID4).setETag(newETag);
        List<Conversation> result = new ArrayList<>();
        result.add(conversationA);
        result.add(conversationB);
        mockedComapiClient.addMockedResult(new MockResult<>(result, true, newETag, 200));

        ChatController.ConversationComparison comparison = chatController.compare(result, store.getAllConversations());


        Method method = chatController.getClass().getDeclaredMethod("updateLocalConversationList", ChatController.ConversationComparison.class);
        method.setAccessible(true);
        ChatController.ConversationComparison conversationComparison = (ChatController.ConversationComparison) ((Observable) method.invoke(chatController, comparison)).toBlocking().first();

        Map<String, ChatConversationBase> loaded = store.getConversations();
        assertTrue(loaded.size() == 2);
        ChatConversationBase loaded1 = store.getConversations().get(ChatTestConst.CONVERSATION_ID1);
        ChatConversationBase loaded4 = store.getConversations().get(ChatTestConst.CONVERSATION_ID4);
        assertNotNull(loaded1);
        assertNotNull(loaded4);
        assertEquals(newETag, loaded1.getETag());
        assertEquals(newETag, loaded4.getETag());
    }

    @Test
    public void test_updateLocalConversationList_largeNumberOfConversations() throws NoSuchFieldException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        List<ChatConversation> list = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            list.add(ChatConversation.builder().setConversationId(Integer.toString(i)).setLastRemoteEventId(1L).setUpdatedOn((long) i).build());
        }

        Method method = chatController.getClass().getDeclaredMethod("limitNumberOfConversations", List.class);
        method.setAccessible(true);
        List result = (List) method.invoke(chatController, list);

        assertEquals(20, result.size());
    }

    @Test
    public void test_updateEvents() throws NoSuchFieldException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException {

        store.addConversationToStore(ChatTestConst.CONVERSATION_ID1, -1L, 1, 3, 0, ChatTestConst.ETAG);

        String json = FileResHelper.readFromFile(this, "rest_events_query.json");
        Parser parser = new Parser();

        Type listType = new TypeToken<ArrayList<JsonObject>>() {
        }.getType();
        List<JsonObject> list = new Gson().fromJson(json, listType);

        ConversationEventsResponse response = new ConversationEventsResponse(list, parser);
        mockedComapiClient.addMockedResult(new MockResult<>(response, true, ChatTestConst.ETAG, 200));

        ChatController.ConversationComparison comparison = chatController.new ConversationComparison(new HashMap<>(), new HashMap<>());
        comparison.setSuccessful(true);
        comparison.conversationsToUpdate.add(ChatConversation.builder().setConversationId(ChatTestConst.CONVERSATION_ID1).setFirstLocalEventId(-1L).setLastLocalEventId(-1L).setLastRemoteEventId(3L).build());

        Method method = chatController.getClass().getDeclaredMethod("lookForMissingEvents", RxComapiClient.class, ChatController.ConversationComparison.class);
        method.setAccessible(true);
        ChatController.ConversationComparison conversationComparison = (ChatController.ConversationComparison) ((Observable) method.invoke(chatController, mockedComapiClient, comparison)).toBlocking().first();

        // Check conversation

        assertTrue(store.getAllConversations().size() == 1);
        ChatConversationBase loadedConversation = store.getConversations().get(ChatTestConst.CONVERSATION_ID1);
        assertNotNull(loadedConversation);
        assertTrue(loadedConversation.getConversationId().equals(ChatTestConst.CONVERSATION_ID1));
        assertEquals(1, loadedConversation.getFirstLocalEventId().longValue());
        assertEquals(3, loadedConversation.getLastLocalEventId().longValue());
        assertEquals(3, loadedConversation.getLastRemoteEventId().longValue());
        assertTrue(loadedConversation.getUpdatedOn() > 0);
        assertEquals(ChatTestConst.ETAG, loadedConversation.getETag());

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

        ChatMessageStatus status1 = (ChatMessageStatus) store.getMessages().get("60526ba0-76b3-4f33-9e2e-20f4a8bb548b").getStatusUpdates().toArray()[0];
        ChatMessageStatus status2 = (ChatMessageStatus) store.getMessages().get("60526ba0-76b3-4f33-9e2e-20f4a8bb548b").getStatusUpdates().toArray()[1];

        assertTrue((LocalMessageStatus.delivered == status1.getMessageStatus() && LocalMessageStatus.read == status2.getMessageStatus()) || (LocalMessageStatus.read == status1.getMessageStatus() && LocalMessageStatus.delivered == status2.getMessageStatus()));

        assertEquals("60526ba0-76b3-4f33-9e2e-20f4a8bb548b", status1.getMessageId());
        assertEquals("p1", status1.getProfileId());
        assertTrue(status1.getUpdatedOn() > 0);
        assertEquals("60526ba0-76b3-4f33-9e2e-20f4a8bb548b", status2.getMessageId());
        assertEquals("p1", status2.getProfileId());
        assertTrue(status2.getUpdatedOn() > 0);

    }

    @Test
    public void test_synchroniseConversation() throws IOException {

        String newETag = "eTag-A";

        // Conversations setup

        store.addConversationToStore(ChatTestConst.CONVERSATION_ID1, -1L, 1, 3, 0, ChatTestConst.ETAG);

        // message setup

        String json = FileResHelper.readFromFile(this, "rest_message_query_no_orphans.json");
        Parser parser = new Parser();
        MessagesQueryResponse response = parser.parse(json, MessagesQueryResponse.class);
        mockedComapiClient.addMockedResult(new MockResult<>(response, true, ChatTestConst.ETAG, 200));

        // Events setup

        json = FileResHelper.readFromFile(this, "rest_events_query.json");

        Type listType = new TypeToken<ArrayList<JsonObject>>() {
        }.getType();
        List<JsonObject> list = new Gson().fromJson(json, listType);

        ConversationEventsResponse response2 = new ConversationEventsResponse(list, parser);
        mockedComapiClient.addMockedResult(new MockResult<>(response2, true, ChatTestConst.ETAG, 200));

        // Test

        Boolean synchroniseSuccess = chatController.synchroniseConversation(ChatTestConst.CONVERSATION_ID1).toBlocking().first();

        assertTrue(synchroniseSuccess);

        // Check conversation

        assertTrue(store.getAllConversations().size() == 1);
        ChatConversationBase loadedConversation = store.getConversations().get(ChatTestConst.CONVERSATION_ID1);
        assertNotNull(loadedConversation);
        assertTrue(loadedConversation.getConversationId().equals(ChatTestConst.CONVERSATION_ID1));
        assertEquals(1, loadedConversation.getFirstLocalEventId().longValue());
        assertEquals(3, loadedConversation.getLastLocalEventId().longValue());
        assertEquals(164, loadedConversation.getLastRemoteEventId().longValue());
        assertTrue(loadedConversation.getUpdatedOn() > 0);
        assertEquals(ChatTestConst.ETAG, loadedConversation.getETag());

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

        ChatMessageStatus status1 = (ChatMessageStatus) store.getMessages().get("60526ba0-76b3-4f33-9e2e-20f4a8bb548b").getStatusUpdates().toArray()[0];
        ChatMessageStatus status2 = (ChatMessageStatus) store.getMessages().get("60526ba0-76b3-4f33-9e2e-20f4a8bb548b").getStatusUpdates().toArray()[1];

        assertTrue((LocalMessageStatus.delivered == status1.getMessageStatus() && LocalMessageStatus.read == status2.getMessageStatus()) || (LocalMessageStatus.read == status1.getMessageStatus() && LocalMessageStatus.delivered == status2.getMessageStatus()));

        assertEquals("60526ba0-76b3-4f33-9e2e-20f4a8bb548b", status1.getMessageId());
        assertEquals("p1", status1.getProfileId());
        assertTrue(status1.getUpdatedOn() > 0);
        assertEquals("60526ba0-76b3-4f33-9e2e-20f4a8bb548b", status2.getMessageId());
        assertEquals("p1", status2.getProfileId());
        assertTrue(status2.getUpdatedOn() > 0);
    }

    @Test
    public void test_noLocalConversation_shouldGetOne() {

        String conversationId = "id-1";

        ConversationDetails conversation = new MockConversationDetails(conversationId);
        mockedComapiClient.addMockedResult(new MockResult<>(conversation, true, ChatTestConst.ETAG, 200));

        ChatResult result = chatController.handleNoLocalConversation(conversationId).toBlocking().first();
        // Check if sdk reacted with no errors when updating for a missing conversation
        assertTrue(result.isSuccessful());
        assertTrue(result.getError() == null);

        // Check if missing conversation has been saved
        ChatConversationBase loadedConversation = store.getConversations().get(conversationId);
        assertNotNull(loadedConversation);
        assertTrue(loadedConversation.getConversationId().equals(conversationId));
        assertEquals(-1L, loadedConversation.getFirstLocalEventId().longValue());
        assertEquals(-1L, loadedConversation.getLastLocalEventId().longValue());
        assertEquals(-1L, loadedConversation.getLastRemoteEventId().longValue());
        assertTrue(loadedConversation.getUpdatedOn() > 0);
        assertEquals(ChatTestConst.ETAG, loadedConversation.getETag());
    }

    @Test(expected = ComapiException.class)
    public void test_checkState() {

        ChatController chatController = new ChatController(mockedComapiClient, persistenceController, attachmentController, new InternalConfig(), new ObservableExecutor() {
            @Override
            <T> void execute(Observable<T> obs) {
                obs.toBlocking().first();
            }
        }, new ModelAdapter(), logger);

        RxComapiClient clientInstance = chatController.checkState().toBlocking().first();
        assertNotNull(clientInstance);

        chatController = new ChatController(null, persistenceController, attachmentController, new InternalConfig(), new ObservableExecutor() {
            @Override
            <T> void execute(Observable<T> obs) {
                obs.toBlocking().first();
            }
        }, new ModelAdapter(), logger);

        // This should throw ComapiException (no Foundation client instance available)
        chatController.checkState().toBlocking().first();
    }

    @Test
    public void test_handleConversationCreated_failed() {

        String conversationId = "id-1";

        ConversationDetails conversation = new MockConversationDetails(conversationId);
        mockedComapiClient.addMockedResult(new MockResult<>(conversation, true, ChatTestConst.ETAG, 200));

        ChatResult result = chatController.handleConversationCreated(new MockResult<>(null, false, null, 500)).toBlocking().first();

        assertNotNull(result);
        assertFalse(result.isSuccessful());
        assertEquals(500, result.getError().getCode());
    }

    @Test
    public void test_handleConversationDeleted_outdatedDataVersion() {

        String conversationId = "id-1";

        ConversationDetails conversation = new MockConversationDetails(conversationId);
        mockedComapiClient.addMockedResult(new MockResult<>(conversation, true, ChatTestConst.ETAG, 200));

        ChatResult result = chatController.handleConversationDeleted(conversationId, new MockResult<>(null, false, ChatTestConst.ETAG, 412)).toBlocking().first();

        assertNotNull(result);
        assertFalse(result.isSuccessful());
        assertEquals(412, result.getError().getCode());

        assertEquals(ChatTestConst.ETAG, store.getConversation(conversationId).getETag());
    }

    @Test
    public void test_handleConversationDeleted_outdatedDataVersion_failed() {

        String conversationId = "id-1";

        mockedComapiClient.addMockedResult(new MockResult<>(null, false, null, 500));

        ChatResult result = chatController.handleConversationDeleted(conversationId, new MockResult<>(null, false, ChatTestConst.ETAG, 412)).toBlocking().first();

        assertNotNull(result);
        assertFalse(result.isSuccessful());
        assertEquals(500, result.getError().getCode());
    }

    @Test
    public void test_handleConversationDeleted_failed() {

        String conversationId = "id-1";

        ChatResult result = chatController.handleConversationDeleted(conversationId, new MockResult<>(null, false, null, 500)).toBlocking().first();

        assertNotNull(result);
        assertFalse(result.isSuccessful());
        assertEquals(500, result.getError().getCode());
    }

    @Test
    public void test_handleConversationUpdated_failed() {

        String conversationId = "id-1";

        ConversationUpdate conversationUpdate = ConversationUpdate.builder().setName("name").build();

        ChatResult result = chatController.handleConversationUpdated(conversationUpdate, new MockResult<>(null, false, null, 500)).toBlocking().first();

        assertNotNull(result);
        assertFalse(result.isSuccessful());
        assertEquals(500, result.getError().getCode());
    }

    @Test
    public void test_handleConversationUpdated_outdatedDataVersion() {

        String conversationId = "id-1";
        String name = "name";

        ConversationDetails conversation = new MockConversationDetails(conversationId);
        mockedComapiClient.addMockedResult(new MockResult<>(conversation, true, ChatTestConst.ETAG, 200));

        ConversationUpdate conversationUpdate = ConversationUpdate.builder().setName(name).build();

        ChatResult result = chatController.handleConversationUpdated(conversationUpdate, new MockResult<>(null, false, ChatTestConst.ETAG, 412)).toBlocking().first();

        assertNotNull(result);
        assertFalse(result.isSuccessful());
        assertEquals(412, result.getError().getCode());

        assertEquals(ChatTestConst.ETAG, store.getConversation(conversationId).getETag());
    }

    @Test
    public void test_handleConversationUpdated_outdatedDataVersion_failed() {

        String conversationId = "id-1";
        String name = "name";

        ConversationDetails conversation = new MockConversationDetails(conversationId);
        mockedComapiClient.addMockedResult(new MockResult<>(conversation, false, null, 500));

        ConversationUpdate conversationUpdate = ConversationUpdate.builder().setName(name).build();

        ChatResult result = chatController.handleConversationUpdated(conversationUpdate, new MockResult<>(null, false, ChatTestConst.ETAG, 412)).toBlocking().first();

        assertNotNull(result);
        assertFalse(result.isSuccessful());
        assertEquals(500, result.getError().getCode());
    }

    @Test
    public void test_handleMessageStatusToUpdate() {

        MessageStatusUpdate update1 = MessageStatusUpdate.builder().addMessageId("1").addMessageId("2").setStatus(MessageStatus.delivered).setTimestamp(DateHelper.getCurrentUTC()).build();
        MessageStatusUpdate update2 = MessageStatusUpdate.builder().addMessageId("3").addMessageId("4").setStatus(MessageStatus.read).setTimestamp(DateHelper.getCurrentUTC()).build();
        List<MessageStatusUpdate> list = new ArrayList<>();
        list.add(update1);
        list.add(update2);

        String conversationId = "id";

        store.addMessageToStore("1");
        store.addMessageToStore("2");
        store.addMessageToStore("3");
        store.addMessageToStore("4");


        ChatResult result = chatController.handleMessageStatusToUpdate(conversationId, list, new MockResult<>(null, true, null, 200)).toBlocking().first();
        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertNull(result.getError());

        assertEquals(MessageStatus.delivered.name(), ((ChatMessageStatus) store.getMessages().get("1").getStatusUpdates().toArray()[0]).getMessageStatus().name());
        assertEquals(MessageStatus.delivered.name(), ((ChatMessageStatus) store.getMessages().get("2").getStatusUpdates().toArray()[0]).getMessageStatus().name());
        assertEquals(MessageStatus.read.name(), ((ChatMessageStatus) store.getMessages().get("3").getStatusUpdates().toArray()[0]).getMessageStatus().name());
        assertEquals(MessageStatus.read.name(), ((ChatMessageStatus) store.getMessages().get("4").getStatusUpdates().toArray()[0]).getMessageStatus().name());
    }

    @Test
    public void test_handleMessageStatusToUpdate_failed() {

        ChatResult result = chatController.handleMessageStatusToUpdate("id", null, new MockResult<>(null, false, null, 500)).toBlocking().first();
        assertNotNull(result);
        assertFalse(result.isSuccessful());
        assertEquals(500, result.getError().getCode());
    }

    @Test
    public void test_handleMessageSent_failed() {

        ChatResult result = chatController.updateStoreWithSentMsg(attachmentController.createMessageProcessor(MessageToSend.builder().build(), null, "conversationId", "profileId"), new MockResult<>(null, false, null, 500)).toBlocking().first();
        assertNotNull(result);
        assertFalse(result.isSuccessful());
        assertEquals(500, result.getError().getCode());
    }

    @Test
    public void test_synchroniseEvents_failed() throws IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        store.addConversationToStore(ChatTestConst.CONVERSATION_ID1, -1L, -1L, 3, 0, ChatTestConst.ETAG);

        mockedComapiClient.addMockedResult(new MockResult<>(null, false, null, 500));

        ChatController.ConversationComparison comparison = chatController.new ConversationComparison(new HashMap<>(), new HashMap<>());
        comparison.conversationsToUpdate.add(ChatConversation.builder().setConversationId(ChatTestConst.CONVERSATION_ID1).setFirstLocalEventId(-1L).setLastLocalEventId(-1L).setLastRemoteEventId(-1L).build());

        Method method = chatController.getClass().getDeclaredMethod("lookForMissingEvents", RxComapiClient.class, ChatController.ConversationComparison.class);
        method.setAccessible(true);
        ChatController.ConversationComparison conversationComparison = (ChatController.ConversationComparison) ((Observable) method.invoke(chatController, mockedComapiClient, comparison)).toBlocking().first();

        assertFalse(comparison.isSuccessful);
    }


    @Test
    public void test_queryEventsRecursively_largeNumber() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException {

        store.addConversationToStore(ChatTestConst.CONVERSATION_ID1, -1L, 1, 3, 0, ChatTestConst.ETAG);

        String json = FileResHelper.readFromFile(this, "rest_events_query.json");
        Parser parser = new Parser();

        Type listType = new TypeToken<ArrayList<JsonObject>>() {
        }.getType();
        List<JsonObject> list = new Gson().fromJson(json, listType);

        JsonObject obj = list.get(0);
        String str = obj.toString();
        for (int i = 0; i < 999; i++) {
            JsonObject newObj = parser.parse(str, JsonObject.class);
            newObj.getAsJsonObject("payload").remove("messageId");
            newObj.getAsJsonObject("payload").addProperty("messageId", String.valueOf(i));
            list.add(newObj);
        }

        int index = 0;
        while (index < list.size()) {
            int newIndex = index + 100;
            ConversationEventsResponse response = new ConversationEventsResponse(list.subList(index, newIndex < list.size() ? newIndex : (list.size() - 1)), parser);
            mockedComapiClient.addMockedResult(new MockResult<>(response, true, ChatTestConst.ETAG, 200));
            index = newIndex;
        }

        ChatController.ConversationComparison comparison = chatController.new ConversationComparison(new HashMap<>(), new HashMap<>());
        comparison.conversationsToUpdate.add(ChatConversation.builder().setConversationId(ChatTestConst.CONVERSATION_ID1).setFirstLocalEventId(-1L).setLastLocalEventId(-1L).setLastRemoteEventId(-1L).build());

        Method method = chatController.getClass().getDeclaredMethod("queryEventsRecursively", RxComapiClient.class, String.class, Long.TYPE, Integer.TYPE, List.class);
        method.setAccessible(true);
        ((Observable) method.invoke(chatController, mockedComapiClient, ChatTestConst.CONVERSATION_ID1, -1, 0, new ArrayList<Boolean>())).toBlocking().first();

        assertFalse(comparison.isSuccessful);
        assertEquals(999, store.getMessages().size());
        assertEquals(2, store.getMessages().get("60526ba0-76b3-4f33-9e2e-20f4a8bb548b").getStatusUpdates().size());
    }

    @Test
    public void test_queryMissingEvents() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException {

        store.addConversationToStore(ChatTestConst.CONVERSATION_ID1, -1L, 1, 3, 0, ChatTestConst.ETAG);

        String json = FileResHelper.readFromFile(this, "rest_events_query.json");
        Parser parser = new Parser();

        Type listType = new TypeToken<ArrayList<JsonObject>>() {
        }.getType();
        List<JsonObject> list = new Gson().fromJson(json, listType);

        JsonObject obj = list.get(0);
        String str = obj.toString();
        for (int i = 0; i < 999; i++) {
            JsonObject newObj = parser.parse(str, JsonObject.class);
            newObj.getAsJsonObject("payload").remove("messageId");
            newObj.getAsJsonObject("payload").addProperty("messageId", String.valueOf(i));
            list.add(newObj);
        }

        ConversationEventsResponse response = new ConversationEventsResponse(list, parser);
        mockedComapiClient.addMockedResult(new MockResult<>(response, true, ChatTestConst.ETAG, 200));

        ChatController.ConversationComparison comparison = chatController.new ConversationComparison(new HashMap<>(), new HashMap<>());
        comparison.conversationsToUpdate.add(ChatConversation.builder().setConversationId(ChatTestConst.CONVERSATION_ID1).setFirstLocalEventId(-1L).setLastLocalEventId(-1L).setLastRemoteEventId(-1L).build());

        chatController.queryMissingEvents(ChatTestConst.CONVERSATION_ID1, 0, 1002);

        assertFalse(comparison.isSuccessful);
        assertEquals(1000, store.getMessages().size());
        assertEquals(2, store.getMessages().get("60526ba0-76b3-4f33-9e2e-20f4a8bb548b").getStatusUpdates().size());
    }

    @After
    public void tearDown() throws Exception {
        mockedComapiClient.clearResults();
        mockedComapiClient.clean(RuntimeEnvironment.application);
        callback.reset();
        store.clearDatabase();
    }

    private List<Attachment> createAttachments() {
        List<Attachment> list = new ArrayList<>();
        list.add(Attachment.create("", "dataType", "test"));
        list.add(Attachment.create(new File(""), "dataType", "test"));
        list.add(Attachment.create(new byte[0], "dataType", "test"));
        return list;
    }
}