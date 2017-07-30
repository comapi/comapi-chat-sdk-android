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

/**
 * Conversation details model for db storage. Encapsulates data that are essential for Comapi Chat SDK to ensure consistency of local db state with services.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ChatConversationBase {

    /**
     * Unique id of the Conversation.
     */
    protected String conversationId;

    /**
     * Oldest conversation event id known by the app.
     */
    protected Long firstLocalEventId;

    /**
     * Latest conversation event id known by the app.
     */
    protected Long lastLocalEventId;

    /**
     * Latest conversation event id.
     */
    protected Long latestRemoteEventId;

    /**
     * Date on which the conversation was updated for the last time.
     */
    protected Long updatedOn;

    /**
     * ETag for server to check if local version of the data is the same as the one the server side.
     */
    protected String eTag;

    /**
     * Get Unique id of the Conversation.
     *
     * @return ID of the Conversation.
     */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * Gets oldest conversation event id known by the app.
     *
     * @return Oldest conversation event id known by the app.
     */
    public Long getFirstLocalEventId() {
        return firstLocalEventId;
    }

    /**
     * Gets latest conversation event id known by the app.
     *
     * @return Latest conversation event id known by the app.
     */
    public Long getLastLocalEventId() {
        return lastLocalEventId;
    }

    /**
     * Gets latest conversation event id.
     *
     * @return Latest conversation event id.
     */
    public Long getLastRemoteEventId() {
        return latestRemoteEventId;
    }

    /**
     * Gets date on which the conversation was updated for the last time.
     *
     * @return Date on which the conversation was updated for the last time.
     */
    public Long getUpdatedOn() {
        return updatedOn;
    }

    /**
     * Gets ETag for server to check if local version of the data is the same as the one the server side.
     *
     * @return ETag for server to check if local version of the data is the same as the one the server side.
     */
    public String getETag() {
        return eTag;
    }

    public static ChatConversationBase.Builder baseBuilder() {
        return new Builder();
    }

    public void setUpdatedOn(Long updatedOn) {
        this.updatedOn = updatedOn;
    }

    public static class Builder {

        ChatConversationBase conversation;

        private Builder() {
            conversation = new ChatConversationBase();
        }

        public Builder setConversationId(String conversationId) {
            this.conversation.conversationId = conversationId;
            return this;
        }

        /**
         * Sets oldest conversation event id known by the app.
         *
         * @param firstEventId Oldest conversation event id known by the app.
         * @return Builder instance.
         */
        public Builder setFirstLocalEventId(Long firstEventId) {
            this.conversation.firstLocalEventId = firstEventId;
            return this;
        }

        /**
         * Sets latest conversation event id known by the app.
         *
         * @param lastEventId Latest conversation event id known by the app.
         * @return Builder instance.
         */
        public Builder setLastLocalEventId(Long lastEventId) {
            this.conversation.lastLocalEventId = lastEventId;
            return this;
        }

        /**
         * Sets latest conversation event id.
         *
         * @param latestRemoteEventId Latest conversation event id.
         * @return Builder instance.
         */
        public Builder setLastRemoteEventId(Long latestRemoteEventId) {
            this.conversation.latestRemoteEventId = latestRemoteEventId;
            return this;
        }

        /**
         * Sets ETag for server to check if local version of the data is the same as the one the server side.
         *
         * @param eTag ETag for server to check if local version of the data is the same as the one the server side.
         * @return Builder instance.
         */
        public Builder setETag(String eTag) {
            this.conversation.eTag = eTag;
            return this;
        }

        /**
         * Sets date on which the conversation was updated for the last time.
         *
         * @param updatedOn Date on which the conversation was updated for the last time.
         * @return Builder instance.
         */
        public Builder setUpdatedOn(Long updatedOn) {
            this.conversation.updatedOn = updatedOn;
            return this;
        }

        public Builder populate(ChatConversationBase conversation) {
            this.conversation.conversationId = conversation.conversationId;
            this.conversation.latestRemoteEventId = conversation.latestRemoteEventId;
            this.conversation.lastLocalEventId = conversation.lastLocalEventId;
            this.conversation.firstLocalEventId = conversation.firstLocalEventId;
            this.conversation.updatedOn = conversation.updatedOn;
            this.conversation.eTag = conversation.eTag;
            return this;
        }

        public ChatConversationBase build() {
            return conversation;
        }
    }
}