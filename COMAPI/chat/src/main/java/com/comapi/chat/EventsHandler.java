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

import android.os.Handler;

import com.comapi.MessagingListener;
import com.comapi.chat.listeners.ProfileListener;
import com.comapi.chat.internal.MissingEventsTracker;
import com.comapi.chat.listeners.ParticipantsListener;
import com.comapi.chat.listeners.TypingListener;
import com.comapi.chat.model.ChatConversation;
import com.comapi.chat.model.ChatMessage;
import com.comapi.chat.model.ChatMessageStatus;
import com.comapi.internal.network.model.events.ProfileUpdateEvent;
import com.comapi.internal.network.model.events.conversation.ConversationCreateEvent;
import com.comapi.internal.network.model.events.conversation.ConversationDeleteEvent;
import com.comapi.internal.network.model.events.conversation.ConversationUndeleteEvent;
import com.comapi.internal.network.model.events.conversation.ConversationUpdateEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantAddedEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantRemovedEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantTypingEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantUpdatedEvent;
import com.comapi.internal.network.model.events.conversation.message.MessageDeliveredEvent;
import com.comapi.internal.network.model.events.conversation.message.MessageReadEvent;
import com.comapi.internal.network.model.events.conversation.message.MessageSentEvent;

/**
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class EventsHandler {

    public final static String MESSAGE_METADATA_TEMP_ID = "tempIdAndroid";

    private ProfileListenerAdapter profileListenerAdapter;

    private MessagingListenerAdapter messagingListenerAdapter;

    private ChatController controller;

    private PersistenceController persistenceController;

    private MissingEventsTracker tracker;

    private MissingEventsTracker.MissingEventsListener missingEventsListener;

    private ObservableExecutor observableExecutor;

    void init(PersistenceController store, ChatController controller, MissingEventsTracker tracker, ChatConfig config) {
        this.persistenceController = store;
        this.controller = controller;
        this.profileListenerAdapter = new ProfileListenerAdapter();
        this.messagingListenerAdapter = new MessagingListenerAdapter();
        this.tracker = tracker;
        this.observableExecutor = config.getObservableExecutor();
        this.missingEventsListener = controller::queryMissingEvents;
    }

    ProfileListenerAdapter getProfileListenerAdapter() {
        return profileListenerAdapter;
    }

    MessagingListenerAdapter getMessagingListenerAdapter() {
        return messagingListenerAdapter;
    }

    class ProfileListenerAdapter extends com.comapi.ProfileListener {

    }

    class MessagingListenerAdapter extends MessagingListener {

        @Override
        public void onMessage(MessageSentEvent event) {
            tracker.checkEventId(event.getContext().getConversationId(), event.getConversationEventId(), missingEventsListener);
            observableExecutor.execute(controller.handleMessage(ChatMessage.builder().populate(event).build()));
        }

        /**
         * Dispatch conversation message update event.
         *
         * @param event Event to dispatch.
         */
        @Override
        public void onMessageDelivered(MessageDeliveredEvent event) {
            tracker.checkEventId(event.getConversationId(), event.getConversationEventId(), missingEventsListener);
            observableExecutor.execute(persistenceController.upsertMessageStatus(new ChatMessageStatus(event)));
        }

        /**
         * Dispatch conversation message update event.
         *
         * @param event Event to dispatch.
         */
        @Override
        public void onMessageRead(MessageReadEvent event) {
            tracker.checkEventId(event.getConversationId(), event.getConversationEventId(), missingEventsListener);
            observableExecutor.execute(persistenceController.upsertMessageStatus(new ChatMessageStatus(event)));
        }

        /**
         * Dispatch participant added to a conversation event.
         *
         * @param event Event to dispatch.
         */
        @Override
        public void onParticipantAdded(ParticipantAddedEvent event) {
            observableExecutor.execute(controller.handleParticipantsAdded(event.getConversationId()));
        }

        /**
         * Dispatch conversation created event.
         *
         * @param event Event to dispatch.
         */
        @Override
        public void onConversationCreated(ConversationCreateEvent event) {
            observableExecutor.execute(persistenceController.upsertConversation(ChatConversation.builder().populate(event).build()));
        }

        /**
         * Dispatch conversation updated event.
         *
         * @param event Event to dispatch.
         */
        @Override
        public void onConversationUpdated(ConversationUpdateEvent event) {
            observableExecutor.execute(persistenceController.upsertConversation(ChatConversation.builder().populate(event).build()));
        }

        /**
         * Dispatch conversation deleted event.
         *
         * @param event Event to dispatch.
         */
        @Override
        public void onConversationDeleted(ConversationDeleteEvent event) {
            observableExecutor.execute(persistenceController.deleteConversation(event.getConversationId()));
        }

        /**
         * Dispatch conversation restored event.
         *
         * @param event Event to dispatch.
         */
        @Override
        public void onConversationUndeleted(ConversationUndeleteEvent event) {
            observableExecutor.execute(persistenceController.upsertConversation(ChatConversation.builder().populate(event).build()));
        }
    }
}