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

/**
 * Internal configuration for Chat SDK.
 *
 * @author Marcin Swierczek
 * @since 1.0.1
 */
@SuppressWarnings("WeakerAccess")
public class InternalConfig {

    public static final int DEFAULT_MESSAGES_PER_PAGE = 10;

    public static final int DEFAULT_EVENTS_PER_QUERY = 100;

    public static final int DEFAULT_EVENT_QUERIES = 100;

    public static final int DEFAULT_PART_DATA_SIZE = 13333;

    public static final int DEFAULT_CONVERSATION_SYNCED = 20;

    private int maxMessagesPerPage;

    private int maxEventsPerQuery;

    private int maxEventQueries;

    private int maxPartDataSize;

    private int maxConversationsSynced;

    /**
     * Recommended constructor.
     */
    public InternalConfig() {
        maxMessagesPerPage = DEFAULT_MESSAGES_PER_PAGE;
        maxEventsPerQuery = DEFAULT_EVENTS_PER_QUERY;
        maxEventQueries = DEFAULT_EVENT_QUERIES;
        maxPartDataSize = DEFAULT_PART_DATA_SIZE;
        maxConversationsSynced = DEFAULT_CONVERSATION_SYNCED;
    }

    /**
     * Calling {@link RxChatServiceAccessor#messaging()}.getPreviousMessages(String) will query messages with a limit of messages in a single call equal to this number.
     * The default is {@link InternalConfig#DEFAULT_MESSAGES_PER_PAGE}
     *
     * @param messagesPerPage Maximum number of messages in a nex messages page obtained from the server.
     * @return InternalConfig instance.
     */
    public InternalConfig limitMessagesPerPage(int messagesPerPage) {
        this.maxMessagesPerPage = messagesPerPage;
        return this;
    }

    /**
     * When syncing the SDK calling events query internally will impose a limit on events received in a single call.
     * The default is {@link InternalConfig#DEFAULT_EVENTS_PER_QUERY}
     *
     * @param eventsPerQuery Limit of event query.
     * @return InternalConfig instance.
     */
    public InternalConfig limitEventsPerQuery(int eventsPerQuery) {
        this.maxEventsPerQuery = eventsPerQuery;
        return this;
    }

    /**
     * When syncing the SDK will impose a limit on number of sequential events queries per conversation. Any missing events not obtained in this number of calls will be still missing.
     * The default is {@link InternalConfig#DEFAULT_EVENT_QUERIES}
     *
     * @param eventQueries Limit of event queries per conversation when synchronising.
     * @return InternalConfig instance.
     */
    public InternalConfig limitEventQueries(int eventQueries) {
        this.maxEventQueries = eventQueries;
        return this;
    }

    /**
     * When sending a message with Parts if the size of a part exceeds this limit on number of characters it will be automatically uploaded as an attachment.
     * The default is {@link InternalConfig#DEFAULT_PART_DATA_SIZE}
     *
     * @param partDataSize Limit of part data size.
     * @return InternalConfig instance.
     */
    public InternalConfig limitPartDataSize(int partDataSize) {
        this.maxPartDataSize = partDataSize;
        return this;
    }

    /**
     * When the {@link RxChatServiceAccessor#messaging()}.synchroniseStore() will be called the SDK will sync only this number of most recent conversations.
     * The default is {@link InternalConfig#DEFAULT_CONVERSATION_SYNCED}
     *
     * @param conversationSynced The default is
     * @return InternalConfig instance.
     */
    public InternalConfig limitConversationSynced(int conversationSynced) {
        this.maxConversationsSynced = conversationSynced;
        return this;
    }

    int getMaxMessagesPerPage() {
        return maxMessagesPerPage;
    }

    int getMaxEventsPerQuery() {
        return maxEventsPerQuery;
    }

    int getMaxEventQueries() {
        return maxEventQueries;
    }

    int getMaxPartDataSize() {
        return maxPartDataSize;
    }

    int getMaxConversationsSynced() {
        return maxConversationsSynced;
    }

    @Override
    public String toString() {
        return "Max messages per conversation: " + maxMessagesPerPage + "; Max events per query: " + maxEventsPerQuery + "; Max event queries: " + maxEventQueries + "; Max data part size: " + maxPartDataSize;
    }
}