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
import com.comapi.chat.database.Database;
import com.comapi.chat.helpers.ChatTestConst;
import com.comapi.chat.helpers.FileResHelper;
import com.comapi.chat.helpers.MockCallback;
import com.comapi.chat.helpers.MockComapiClient;
import com.comapi.chat.helpers.MockConversationDetails;
import com.comapi.chat.helpers.MockFoundationFactory;
import com.comapi.chat.helpers.MockResult;
import com.comapi.chat.helpers.TestChatStore;
import com.comapi.chat.internal.MissingEventsTracker;
import com.comapi.chat.listeners.ParticipantsListener;
import com.comapi.chat.listeners.ProfileListener;
import com.comapi.chat.listeners.TypingListener;
import com.comapi.chat.model.ChatConversationBase;
import com.comapi.chat.model.ChatMessage;
import com.comapi.chat.model.ChatMessageStatus;
import com.comapi.chat.model.ChatStore;
import com.comapi.chat.model.ModelAdapter;
import com.comapi.internal.Parser;
import com.comapi.internal.log.LogLevel;
import com.comapi.internal.log.LogManager;
import com.comapi.internal.log.Logger;
import com.comapi.internal.network.AuthClient;
import com.comapi.internal.network.ChallengeOptions;
import com.comapi.internal.network.model.conversation.ConversationDetails;
import com.comapi.internal.network.model.events.ProfileUpdateEvent;
import com.comapi.internal.network.model.events.conversation.ConversationDeleteEvent;
import com.comapi.internal.network.model.events.conversation.ConversationUndeleteEvent;
import com.comapi.internal.network.model.events.conversation.ConversationUpdateEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantAddedEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantRemovedEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantTypingEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantTypingOffEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantUpdatedEvent;
import com.comapi.internal.network.model.events.conversation.message.MessageDeliveredEvent;
import com.comapi.internal.network.model.events.conversation.message.MessageSentEvent;
import com.comapi.internal.network.model.messaging.MessageStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import rx.Observable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
@Config(manifest = "chat/src/main/AndroidManifest.xml", sdk = Build.VERSION_CODES.M, constants = BuildConfig.class, packageName = "com.comapi.chat")
public class EventHandlerTest {

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

    private boolean wasChecked = false;

    final List<Object> events = new ArrayList<>();

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
                .observableExecutor(new ObservableExecutor() {
                    @Override
                    <T> void execute(Observable<T> obs) {
                        obs.toBlocking().first();
                    }
                })
                .typingListener(new TypingListener() {
                    @Override
                    public void participantTyping(String conversationId, String participantId, boolean isTyping) {
                        events.add(isTyping);
                    }
                })
                .participantsListener(new ParticipantsListener() {
                    @Override
                    public void onParticipantAdded(ParticipantAddedEvent event) {
                        events.add(event);
                    }

                    @Override
                    public void onParticipantUpdated(ParticipantUpdatedEvent event) {
                        events.add(event);
                    }

                    @Override
                    public void onParticipantRemoved(ParticipantRemovedEvent event) {
                        events.add(event);
                    }
                })
                .profileListener(new ProfileListener() {
                    @Override
                    public void onProfileUpdate(ProfileUpdateEvent event) {
                        events.add(event);
                    }
                })
                .apiConfiguration(apiConfig)
                .store(factory);

        client = ComapiChat.initialise(RuntimeEnvironment.application, chatConfig).toBlocking().first();
        mockedComapiClient = foundationFactory.getMockedClient();
        eventsHandler = foundationFactory.getMockedEventsHandler();

        LogManager logMgr = new LogManager();
        logMgr.init(RuntimeEnvironment.application, LogLevel.OFF.getValue(), LogLevel.OFF.getValue(), 0);
        logger = new Logger(logMgr, "");

        ModelAdapter modelAdapter = new ModelAdapter();
        db = Database.getInstance(RuntimeEnvironment.application, true, new Logger(logMgr, ""));
        persistenceController = new PersistenceController(db, modelAdapter, factory, logger);

        chatController = new ChatController(mockedComapiClient, persistenceController, new ObservableExecutor() {
            @Override
            <T> void execute(Observable<T> obs) {
                obs.toBlocking().first();
            }
        }, modelAdapter, logger);

        eventsHandler.init(persistenceController, chatController, new MissingEventsTracker() {
            @Override
            public boolean checkEventId(String conversationId, long conversationEventId, MissingEventsListener missingEventsListener) {
                wasChecked = true;
                return false;
            }
        }, chatConfig);
    }

    @Test
    public void test_messageSent() throws IOException {

        Parser parser = new Parser();
        String json = FileResHelper.readFromFile(this, "message_sent.json");
        MessageSentEvent event = parser.parse(json, MessageSentEvent.class);
        mockedComapiClient.addMockedResult(new MockResult<>(null, true, ChatTestConst.ETAG, 200));

        String conversationId = "id";
        String messageId = "2e65e429-5c88-42f1-9fd1-36e86ca3d1ff";
        ChatConversationBase conversationInStore = ChatConversationBase.baseBuilder()
                .setConversationId(conversationId)
                .setETag("eTag-0")
                .setFirstLocalEventId(1L)
                .setLastLocalEventId(2L)
                .setLastRemoteEventId(2L)
                .setUpdatedOn(0L)
                .build();
        store.getConversations().put(conversationId, conversationInStore);

        eventsHandler.getMessagingListenerAdapter().onMessage(event);
        assertTrue(wasChecked);
        ChatMessage message = store.getMessages().get(messageId);
        assertEquals(messageId, message.getMessageId());
        assertEquals(Long.valueOf(1), message.getSentEventId());
        assertEquals(conversationId, message.getConversationId());
        assertEquals("user", message.getFromWhom().getName());
        assertEquals("user", message.getFromWhom().getId());
        assertEquals("user", message.getSentBy());
        assertEquals(1, message.getParts().size());
        assertEquals("value", message.getMetadata().get("key"));
    }

    @Test
    public void test_messageStatusDelivered() throws IOException {

        Parser parser = new Parser();
        String json = FileResHelper.readFromFile(this, "message_delivered.json");
        MessageDeliveredEvent event = parser.parse(json, MessageDeliveredEvent.class);

        String conversationId = "conversationId";
        String messageId = "messageId";
        ChatConversationBase conversationInStore = ChatConversationBase.baseBuilder()
                .setConversationId(conversationId)
                .setETag("eTag-0")
                .setFirstLocalEventId(1L)
                .setLastLocalEventId(2L)
                .setLastRemoteEventId(2L)
                .setUpdatedOn(0L)
                .build();
        store.getConversations().put(conversationId, conversationInStore);
        store.addMessageToStore(messageId);

        eventsHandler.getMessagingListenerAdapter().onMessageDelivered(event);
        assertTrue(wasChecked);

        ChatMessageStatus status = (ChatMessageStatus) store.getMessages().get(messageId).getStatusUpdates().toArray()[0];
        assertEquals(messageId, status.getMessageId());
        assertTrue(status.getUpdatedOn() > 0);
        assertEquals("profileId", status.getProfileId());
        assertEquals(MessageStatus.delivered.name(), status.getMessageStatus().name());

        // Repeat same status and check again

        eventsHandler.getMessagingListenerAdapter().onMessageDelivered(event);
        assertTrue(wasChecked);
        status = (ChatMessageStatus) store.getMessages().get(messageId).getStatusUpdates().toArray()[0];
        assertEquals(messageId, status.getMessageId());
        assertTrue(status.getUpdatedOn() > 0);
        assertEquals("profileId", status.getProfileId());
        assertEquals(MessageStatus.delivered.name(), status.getMessageStatus().name());
    }

    @Test
    public void test_messageStatusRead() throws IOException {

        Parser parser = new Parser();
        String json = FileResHelper.readFromFile(this, "message_read.json");
        MessageDeliveredEvent event = parser.parse(json, MessageDeliveredEvent.class);

        String conversationId = "conversationId";
        String messageId = "messageId";
        ChatConversationBase conversationInStore = ChatConversationBase.baseBuilder()
                .setConversationId(conversationId)
                .setETag("eTag-0")
                .setFirstLocalEventId(1L)
                .setLastLocalEventId(2L)
                .setLastRemoteEventId(2L)
                .setUpdatedOn(0L)
                .build();
        store.getConversations().put(conversationId, conversationInStore);
        store.addMessageToStore(messageId);

        eventsHandler.getMessagingListenerAdapter().onMessageDelivered(event);
        assertTrue(wasChecked);
        ChatMessageStatus status = (ChatMessageStatus) store.getMessages().get(messageId).getStatusUpdates().toArray()[0];
        assertEquals(messageId, status.getMessageId());
        assertTrue(status.getUpdatedOn() > 0);
        assertEquals("profileId", status.getProfileId());
        assertEquals(MessageStatus.delivered.name(), status.getMessageStatus().name());
    }

    @Test
    public void test_conversationEvents() throws IOException {

        Parser parser = new Parser();
        String json = FileResHelper.readFromFile(this, "participant_added.json");
        ParticipantAddedEvent event = parser.parse(json, ParticipantAddedEvent.class);

        String conversationId = "conversationId";
        String eTag = "\"c-cSYaByHVYhzDLvTlV/6DReHISIA\"";

        ConversationDetails conversation = new MockConversationDetails(conversationId);
        mockedComapiClient.addMockedResult(new MockResult<>(conversation, true, eTag, 200));

        // Added

        eventsHandler.getMessagingListenerAdapter().onParticipantAdded(event);
        ChatConversationBase conversationLoaded = store.getConversation(conversationId);
        assertEquals(conversationId, conversationLoaded.getConversationId());
        assertEquals(eTag, conversationLoaded.getETag());
        assertTrue(conversationLoaded.getUpdatedOn() > 0);
        assertEquals(Long.valueOf(-1), conversationLoaded.getLastRemoteEventId());
        assertEquals(Long.valueOf(-1), conversationLoaded.getFirstLocalEventId());
        assertEquals(Long.valueOf(-1), conversationLoaded.getLastLocalEventId());

        // Updated

        json = FileResHelper.readFromFile(this, "conversation_update.json");
        ConversationUpdateEvent eventUpdate = parser.parse(json, ConversationUpdateEvent.class);
        eventsHandler.getMessagingListenerAdapter().onConversationUpdated(eventUpdate);
        conversationLoaded = store.getConversation(conversationId);
        assertEquals(conversationId, conversationLoaded.getConversationId());
        assertEquals(eTag, conversationLoaded.getETag());
        assertTrue(conversationLoaded.getUpdatedOn() > 0);
        assertEquals(Long.valueOf(-1), conversationLoaded.getLastRemoteEventId());
        assertEquals(Long.valueOf(-1), conversationLoaded.getFirstLocalEventId());
        assertEquals(Long.valueOf(-1), conversationLoaded.getLastLocalEventId());

        // Removed

        json = FileResHelper.readFromFile(this, "conversation_delete.json");
        ConversationDeleteEvent eventRemove = parser.parse(json, ConversationDeleteEvent.class);
        eventsHandler.getMessagingListenerAdapter().onConversationDeleted(eventRemove);
        assertNull(store.getConversation(conversationId));

        // Undelete

        json = FileResHelper.readFromFile(this, "conversation_undelete.json");
        ConversationUndeleteEvent eventUndelete = parser.parse(json, ConversationUndeleteEvent.class);
        eventsHandler.getMessagingListenerAdapter().onConversationUndeleted(eventUndelete);
        conversationLoaded = store.getConversation(conversationId);
        assertEquals(conversationId, conversationLoaded.getConversationId());
        assertEquals(eTag, conversationLoaded.getETag());
        assertTrue(conversationLoaded.getUpdatedOn() > 0);
        assertEquals(Long.valueOf(-1), conversationLoaded.getLastRemoteEventId());
        assertEquals(Long.valueOf(-1), conversationLoaded.getFirstLocalEventId());
        assertEquals(Long.valueOf(-1), conversationLoaded.getLastLocalEventId());
    }

    @Test
    public void test_ParticipantEvents() throws IOException {

        final List<ParticipantEvent> events = new ArrayList<>();

        ParticipantsListener participantListener = new ParticipantsListener() {
            @Override
            public void onParticipantAdded(ParticipantAddedEvent event) {
                events.add(event);
            }

            @Override
            public void onParticipantUpdated(ParticipantUpdatedEvent event) {
                events.add(event);
            }

            @Override
            public void onParticipantRemoved(ParticipantRemovedEvent event) {
                events.add(event);
            }
        };

        client.addListener(participantListener);

        Parser parser = new Parser();
        String json = FileResHelper.readFromFile(this, "participant_added.json");
        ParticipantAddedEvent event1 = parser.parse(json, ParticipantAddedEvent.class);
        mockedComapiClient.dispatchTestEvent(event1);

        json = FileResHelper.readFromFile(this, "participant_updated.json");
        ParticipantUpdatedEvent event2 = parser.parse(json, ParticipantUpdatedEvent.class);
        mockedComapiClient.dispatchTestEvent(event2);

        json = FileResHelper.readFromFile(this, "participant_removed.json");
        ParticipantRemovedEvent event3 = parser.parse(json, ParticipantRemovedEvent.class);
        mockedComapiClient.dispatchTestEvent(event3);

        assertTrue(events.get(0) instanceof ParticipantAddedEvent);
        assertEquals("conversationId", events.get(0).getConversationId());
        assertEquals("profileId", events.get(0).getProfileId());

        assertTrue(events.get(1) instanceof ParticipantUpdatedEvent);
        assertEquals("conversationId", events.get(0).getConversationId());
        assertEquals("profileId", events.get(0).getProfileId());

        assertTrue(events.get(2) instanceof ParticipantRemovedEvent);
        assertEquals("conversationId", events.get(0).getConversationId());
        assertEquals("profileId", events.get(0).getProfileId());

        events.clear();
        client.removeListener(participantListener);
        mockedComapiClient.dispatchTestEvent(event1);

        assertTrue(events.isEmpty());

        assertTrue(this.events.size() == 3);
    }

    @Test
    public void test_ProfileEvents() throws IOException {

        final List<ProfileUpdateEvent> events = new ArrayList<>();

        ProfileListener profileListener = new ProfileListener() {
            @Override
            public void onProfileUpdate(ProfileUpdateEvent event) {
                events.add(event);
            }
        };

        client.addListener(profileListener);

        Parser parser = new Parser();
        String json = FileResHelper.readFromFile(this, "profile_update.json");
        ProfileUpdateEvent event1 = parser.parse(json, ProfileUpdateEvent.class);
        mockedComapiClient.dispatchTestEvent(event1);

        assertNotNull(events.get(0));
        assertEquals("profileId", events.get(0).getProfileId());

        events.clear();
        client.removeListener(profileListener);
        mockedComapiClient.dispatchTestEvent(event1);

        assertTrue(events.isEmpty());

        assertTrue(this.events.size() == 1); // only one event type was dispatched.
    }

    @Test
    public void test_TypingEvents() throws IOException {

        final List<Boolean> events = new ArrayList<>();

        TypingListener typingListener = new TypingListener() {
            @Override
            public void participantTyping(String conversationId, String participantId, boolean isTyping) {
                events.add(isTyping);
            }
        };

        client.addListener(typingListener);

        Parser parser = new Parser();
        String json = FileResHelper.readFromFile(this, "is_typing.json");
        ParticipantTypingEvent event1 = parser.parse(json, ParticipantTypingEvent.class);
        mockedComapiClient.dispatchTestEvent(event1);

        json = FileResHelper.readFromFile(this, "typing_off.json");
        ParticipantTypingOffEvent event2 = parser.parse(json, ParticipantTypingOffEvent.class);
        mockedComapiClient.dispatchTestEvent(event2);

        assertEquals(true, (!events.get(0) && events.get(1)) || (events.get(0) && !events.get(1)));

        client.removeListener(typingListener);
        events.clear();
        mockedComapiClient.dispatchTestEvent(event1);
        assertTrue(events.isEmpty());

        assertTrue(this.events.size() == 2);
    }

    @After
    public void tearDown() throws Exception {
        mockedComapiClient.clearResults();
        mockedComapiClient.clean(RuntimeEnvironment.application);
        callback.reset();
        store.clearDatabase();
        wasChecked = false;
        events.clear();
    }
}