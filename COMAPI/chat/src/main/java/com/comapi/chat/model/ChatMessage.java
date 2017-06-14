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

import com.comapi.internal.helpers.DateHelper;
import com.comapi.internal.network.model.events.conversation.message.MessageSentEvent;
import com.comapi.internal.network.model.messaging.MessageReceived;
import com.comapi.internal.network.model.messaging.Part;
import com.comapi.internal.network.model.messaging.Sender;

import java.util.ArrayList;
import java.util.List;

/**
 * Message model for db storage.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ChatMessage {

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
    private List<ChatMessageStatus> statusUpdates;

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
    public List<ChatMessageStatus> getStatusUpdates() {
        return statusUpdates;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        ChatMessage message;

        Builder() {
            message = new ChatMessage();
            message.statusUpdates = new ArrayList<>();
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

            for (String profileId : update.getStatusUpdate().keySet()) {
                MessageReceived.Status status = update.getStatusUpdate().get(profileId);
                message.statusUpdates.add(new ChatMessageStatus(message.messageId, profileId, status.getStatus(), DateHelper.getUTCMilliseconds(status.getTimestamp())));
            }

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
    }
}