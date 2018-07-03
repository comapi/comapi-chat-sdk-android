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
     * @return True if operation was successful.
     */
    public abstract boolean upsert(ChatConversation conversation);

    /**
     * Update conversation in persistence store.
     *
     * @param conversation Conversation to update.
     * @return True if operation was successful.
     */
    public abstract boolean update(ChatConversationBase conversation);

    /**
     * Delete conversation from persistence store.
     *
     * @param conversationId Unique global conversation identifier of an conversation to delete.
     * @return True if operation was successful.
     */
    public abstract boolean deleteConversation(String conversationId);

    /**
     * Insert or update message in persistence store.
     *
     * @param message Message to insert or update.
     * @return True if operation was successful.
     */
    public abstract boolean upsert(ChatMessage message);

    /**
     * Update stored {@link ChatMessage} with a new {@link ChatMessageStatus}. The chat message is unique for a combination of messageId, profileId and {@link LocalMessageStatus} value.
     *
     * @param status Chat message status for the {@link ChatMessage} with id {@link ChatMessageStatus#getMessageId()}
     * @return True if operation was successful.
     */
    public abstract boolean update(ChatMessageStatus status);

    /**
     * Delete all messages from persistence store that are related to given conversation.
     *
     * @param conversationId Unique global conversation identifier.
     * @return True if operation was successful.
     */
    public abstract boolean deleteAllMessages(String conversationId);

    /**
     * Delete message from persistence store.
     *
     * @param conversationId Unique global conversation identifier.
     * @param messageId      Unique global message identifier.
     * @return True if operation was successful.
     */
    public abstract boolean deleteMessage(String conversationId, String messageId);

    /**
     * Delete all content of persistence store that is related to current user.
     *
     * @return True if operation was successful.
     */
    public abstract boolean clearDatabase();

    /**
     * Begin transaction. From this point calling other methods of this class will queue store updates and inserts to be executed when {@link ChatStore#endTransaction()} on same instance is called.
     */
    public abstract void beginTransaction();

    /**
     * End transaction. Execute queued store updates and inserts.
     */
    public abstract void endTransaction();

    /**
     * Open/prepare database.
     */
    public abstract void open();

    /**
     * Close database.
     */
    public abstract void close();
}