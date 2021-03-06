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

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;

import com.comapi.internal.helpers.DateHelper;
import com.comapi.internal.network.model.events.conversation.message.MessageSentEvent;
import com.comapi.internal.network.model.messaging.MessageReceived;
import com.comapi.internal.network.model.messaging.Part;
import com.comapi.internal.network.model.messaging.Sender;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Message model for db storage.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ChatMessage implements Comparable<ChatMessage> {

    /**
     * Unique monotonically increasing identifier of message in the conversation. We recommend to sort the messages based on its value.
     */
    private Long sentEventId;

    /**
     * Globally unique identifier of a message.
     */
    private String messageId;

    /**
     * Globally unique identifier of a conversation.
     */
    private String conversationId;

    /**
     * Who is the official sender of the message.
     */
    private Sender fromWhom;

    /**
     * Who actually sent the message. This is usually internal information.
     */
    private String sentBy;

    /**
     * UTC time in milliseconds when the message was sent.
     */
    private Long sentOn;

    /**
     * Parts of the message with data, type, name and size.
     */
    private List<Part> parts;

    /**
     * Message statuses 'delivered' or 'read' for particular participants.
     */
    private Map<Integer, ChatMessageStatus> statusUpdates;

    /**
     * Message custom metadata.
     */
    private Map<String, Object> metadata;

    /**
     * Gets Unique monotonically increasing identifier of message in the conversation. We recommend to sort the messages based on its value.
     *
     * @return Unique monotonically increasing identifier of message in the conversation. We recommend to sort the messages based on its value.
     */
    public Long getSentEventId() {
        return sentEventId;
    }

    /**
     * Globally unique identifier of a message.
     *
     * @return Globally unique identifier of a message.
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * Globally unique identifier of a message.
     *
     * @return Globally unique identifier of a message.
     */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * Who is the official sender of the message.
     *
     * @return Who is the official sender of the message.
     */
    public Sender getFromWhom() {
        return fromWhom;
    }

    /**
     * Who actually sent the message. This is usually internal information.
     *
     * @return Who actually sent the message. This is usually internal information.
     */
    public String getSentBy() {
        return sentBy;
    }

    /**
     * UTC time in milliseconds when the message was sent.
     *
     * @return UTC time in milliseconds when the message was sent.
     */
    public Long getSentOn() {
        return sentOn;
    }

    /**
     * Parts of the message with data, type, name and size.
     *
     * @return Parts of the message with data, type, name and size.
     */
    public List<Part> getParts() {
        return parts;
    }

    /**
     * Message statuses 'delivered' or 'read' for particular participants.
     *
     * @return Message statuses 'delivered' or 'read' for particular participants.
     */
    public Collection<ChatMessageStatus> getStatusUpdates() {
        return statusUpdates.values();
    }

    /**
     * Metadata associated with the message e.g. custom id.
     *
     * @return Metadata map.
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Update status list with a new status.
     *
     * @param status New message status details.
     */
    @SuppressLint("UseSparseArrays")
    public void addStatusUpdate(ChatMessageStatus status) {
        int unique = (status.getMessageId() + status.getProfileId() + status.getMessageStatus().name()).hashCode();
        if (statusUpdates == null) {
            statusUpdates = new HashMap<>();
        }
        statusUpdates.put(unique, status);
    }

    /**
     * Internal setter to update sent event id.
     */
    public ChatMessage setSentEventId(Long sentEventId) {
        this.sentEventId = sentEventId;
        return this;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        ChatMessage message;

        Builder() {
            message = new ChatMessage();
            message.statusUpdates = new HashMap<>();
        }

        public ChatMessage build() {
            return message;
        }

        public Builder populate(MessageReceived update) {

            message.sentEventId = update.getSentEventId();
            message.messageId = update.getMessageId();
            message.conversationId = update.getConversationId();
            message.fromWhom = update.getFromWhom();
            message.sentBy = update.getSentBy();
            message.sentOn = DateHelper.getUTCMilliseconds(update.getSentOn());
            message.parts = update.getParts();
            message.metadata = update.getMetadata();

            return this;
        }

        public Builder populate(MessageSentEvent event) {

            message.sentEventId = event.getConversationEventId();
            message.messageId = event.getMessageId();
            message.conversationId = event.getContext().getConversationId();
            message.fromWhom = event.getContext().getFromWhom();
            message.sentBy = event.getContext().getSentBy();
            message.sentOn = DateHelper.getUTCMilliseconds(event.getContext().getSentOn());
            message.parts = event.getParts();
            message.metadata = event.getMetadata();

            return this;
        }

        public Builder setMessageId(String messageId) {
            message.messageId = messageId;
            return this;
        }

        public Builder setConversationId(String conversationId) {
            message.conversationId = conversationId;
            return this;
        }

        public Builder setFromWhom(Sender sender) {
            message.fromWhom = sender;
            return this;
        }

        public Builder setSentBy(String senderProfileId) {
            message.sentBy = senderProfileId;
            return this;
        }

        public Builder setSentOn(Long sentOn) {
            message.sentOn = sentOn;
            return this;
        }

        public Builder setParts(List<Part> parts) {
            message.parts = parts;
            return this;
        }

        public Builder setMetadata(Map<String, Object> metadata) {
            message.metadata = metadata;
            return this;
        }

        public Builder setSentEventId(Long sentEventId) {
            message.sentEventId = sentEventId;
            return this;
        }
    }

    @Override
    public int compareTo(@NonNull ChatMessage message) {

        if (sentEventId < message.sentEventId) {
            return 1;
        } else if (sentEventId > message.sentEventId) {
            return -1;
        } else {
            if (sentOn != null && message.sentOn != null) {
                if (sentOn < message.sentOn) {
                    return -1;
                } else {
                    return 1;
                }
            }
        }

        return 0;
    }
}