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
import com.comapi.internal.network.model.events.conversation.message.MessageDeliveredEvent;
import com.comapi.internal.network.model.events.conversation.message.MessageReadEvent;
import com.comapi.internal.network.model.messaging.MessageStatus;

/**
 * Message status model for db storage.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ChatMessageStatus {

    private String conversationId;

    private String messageId;

    private String profileId;

    private Long conversationEventId;

    /**
     * Message status as defined in {@link MessageStatus}
     */
    private MessageStatus messageStatus;

    /**
     * Time when the status was set for the message.
     */
    private Long updatedOn;

    public ChatMessageStatus(String conversationId, String messageId, String profileId, MessageStatus messageStatus, Long updatedOn, Long conversationEventId) {
        this.messageId = messageId;
        this.profileId = profileId;
        this.messageStatus = messageStatus;
        this.updatedOn = updatedOn;
        this.conversationId = conversationId;
        if (conversationEventId != null) {
            this.conversationEventId = conversationEventId;
        }
    }

    public ChatMessageStatus(MessageDeliveredEvent event) {
        this.messageId = event.getMessageId();
        this.profileId = event.getProfileId();
        this.messageStatus = MessageStatus.delivered;
        this.updatedOn = DateHelper.getUTCMilliseconds(event.getTimestamp());
        this.conversationId = event.getConversationId();
        this.conversationEventId = event.getConversationEventId();
    }

    public ChatMessageStatus(MessageReadEvent event) {
        this.messageId = event.getMessageId();
        this.profileId = event.getProfileId();
        this.messageStatus = MessageStatus.read;
        this.updatedOn = DateHelper.getUTCMilliseconds(event.getTimestamp());
        this.conversationId = event.getConversationId();
        this.conversationEventId = event.getConversationEventId();
    }

    public String getMessageId() {
        return messageId;
    }

    public String getProfileId() {
        return profileId;
    }

    /**
     * Message status as defined in {@link MessageStatus}
     *
     * @return Message status.
     */
    public MessageStatus getMessageStatus() {
        return messageStatus;
    }

    /**
     * Time when the status was set for the message.
     *
     * @return Time when the status was set for the message.
     */
    public Long getUpdatedOn() {
        return updatedOn;
    }

    public String getConversationId() {
        return conversationId;
    }

    public Long getConversationEventId() {
        return conversationEventId;
    }
}