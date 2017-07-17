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

package com.comapi.chat.helpers;

import com.comapi.chat.model.ChatConversation;
import com.comapi.chat.model.ChatConversationBase;
import com.comapi.chat.model.ChatMessage;
import com.comapi.chat.model.ChatMessageStatus;
import com.comapi.chat.model.ChatParticipant;
import com.comapi.chat.model.ChatProfile;
import com.comapi.chat.model.ChatStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Store interfaces 'in memory' implementation for tests.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class TestChatStore extends ChatStore {

    private final Map<String, ChatConversationBase> conversations = new HashMap<>();
    private final Map<String, ChatMessage> messages = new HashMap<>();
    private final Map<String, ChatMessageStatus> messagesStatuses = new HashMap<>();
    private final Map<String, List<ChatParticipant>> participants = new HashMap<>();
    private ChatProfile profile;

    @Override
    public ChatConversationBase getConversation(String conversationId) {
        return conversations.get(conversationId);
    }

    @Override
    public List<ChatConversationBase> getAllConversations() {
        return new ArrayList<>(conversations.values());
    }

    @Override
    public boolean upsert(ChatConversation conversation) {
        conversations.put(conversation.getConversationId(), conversation);
        return true;
    }

    @Override
    public boolean update(ChatConversationBase conversation) {
        conversations.put(conversation.getConversationId(), conversation);
        return true;
    }

    @Override
    public boolean deleteConversation(String conversationId) {
        deleteAllMessages(conversationId);
        conversations.remove(conversationId);
        return true;
    }

    @Override
    public boolean upsert(ChatMessage message) {
        messages.put(message.getMessageId(), message);
        return true;
    }

    @Override
    public boolean upsert(ChatMessageStatus status) {
        messagesStatuses.put(status.getMessageId(), status);
        return true;
    }

    @Override
    public ChatMessageStatus getStatus(String conversationId, String messageId) {
        return messagesStatuses.get(messageId);
    }

    @Override
    public boolean clearDatabase() {
        conversations.clear();
        messages.clear();
        participants.clear();
        profile = null;
        return true;
    }

    @Override
    public boolean deleteAllMessages(String conversationId) {

        List<String> idsToRemove = new ArrayList<>();

        for (ChatMessage msg : messages.values()) {
            if (conversationId.equals(msg.getConversationId())) {
                idsToRemove.add(msg.getMessageId());
            }
        }

        for (String id : idsToRemove) {
            messages.remove(id);
        }

        participants.remove(conversationId);
        return true;
    }

    @Override
    public boolean deleteMessage(String conversationId, String messageId) {
        messages.remove(messageId);
        return true;
    }

    public Map<String, ChatMessage> getMessages() {
        return messages;
    }

    public Map<String, ChatConversationBase> getConversations() {
        return conversations;
    }

    public Map<String, ChatMessageStatus> getStatuses() {
        return messagesStatuses;
    }

    public void addConversationToStore(String conversationId, long first, long last, long updatedOn, String eTag) {

        ChatConversationBase conversationInStore1 = ChatConversationBase.baseBuilder()
                .setConversationId(conversationId)
                .setETag(eTag)
                .setFirstEventId(first)
                .setLastEventIdd(last)
                .setLatestRemoteEventId(last)
                .setUpdatedOn(updatedOn)
                .build();
        conversations.put(conversationId, conversationInStore1);

    }

    public ChatProfile getProfile() {
        return profile;
    }
}