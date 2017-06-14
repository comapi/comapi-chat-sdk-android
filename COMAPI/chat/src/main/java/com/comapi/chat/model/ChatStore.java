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

package com.comapi.chat.model;

import java.util.List;

/**
 * Top level interface for db storage.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public abstract class ChatStore {

    /**
     * Get conversation from persistence store.
     *
     * @param conversationId Unique global conversation identifier.
     * @return Conversation from persistence store.
     */
    public abstract ChatConversationBase getConversation(String conversationId);

    /**
     * Get all conversations from persistence store.
     *
     * @return All conversations from persistence store.
     */
    public abstract List<ChatConversationBase> getAllConversations();

    /**
     * Insert or update conversation in persistence store.
     *
     * @param conversation Conversation to insert or update.
     */
    public abstract boolean upsert(ChatConversation conversation);

    /**
     * Update conversation in persistence store.
     *
     * @param conversation Conversation to update.
     */
    public abstract boolean update(ChatConversationBase conversation);

    /**
     * Delete conversation from persistence store.
     *
     * @param conversationId Unique global conversation identifier of an conversation to delete.
     */
    public abstract boolean deleteConversation(String conversationId);

    /**
     * Insert or update message in persistence store.
     *
     * @param message Message to insert or update.
     */
    public abstract boolean upsert(ChatMessage message);

    /**
     * Delete all messages in persistence store that are related to given conversation.
     *
     * @param conversationId Unique global conversation identifier.
     */
    public abstract boolean deleteAllMessages(String conversationId);

    public abstract boolean deleteMessage(String messageId);

    /**
     * Insert or update message status in persistence store.
     *
     * @param status Message status to insert or update.
     */
    public abstract boolean upsert(ChatMessageStatus status);

    public abstract ChatMessageStatus getStatus(String messageId);

    /**
     * Get participants of the conversation.
     *
     * @param conversationId Unique global conversation identifier.
     * @return Participants of the conversation.
     */
    public abstract List<ChatParticipant> getParticipants(String conversationId);

    /**
     * Insert or update participants of an conversation in persistence store.
     *
     * @param conversationId Unique global conversation identifier.
     */
    public abstract boolean upsert(String conversationId, ChatParticipant participant);

    /**
     * Remove participant from conversation in persistence store.
     *
     * @param conversationId Unique global conversation identifier.
     */
    public abstract boolean removeParticipant(String conversationId, String profileId);

    public abstract boolean upsert(ChatProfile message);

    /**
     * Delete all content of persistence store that is related to current user.
     */
    public abstract boolean clearDatabase();
}