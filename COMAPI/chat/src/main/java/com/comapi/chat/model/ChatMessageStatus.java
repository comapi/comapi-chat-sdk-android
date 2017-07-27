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

import android.support.annotation.NonNull;

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
public class ChatMessageStatus implements Comparable<ChatMessageStatus> {

    private String conversationId;

    private String messageId;

    private String profileId;

    private Long conversationEventId;

    /**
     * Message status as defined in {@link MessageStatus}
     */
    private LocalMessageStatus messageStatus;

    /**
     * Time when the status was set for the message.
     */
    private Long updatedOn;

    private ChatMessageStatus() {

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
    public LocalMessageStatus getMessageStatus() {
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        ChatMessageStatus status;

        Builder() {
            status = new ChatMessageStatus();
        }

        public ChatMessageStatus build() {
            return status;
        }

        public Builder populate(String conversationId, String messageId, String profileId, LocalMessageStatus messageStatus, Long updatedOn, Long conversationEventId) {
            this.status.messageId = messageId;
            this.status.profileId = profileId;
            this.status.messageStatus = messageStatus;
            this.status.updatedOn = updatedOn;
            this.status.conversationId = conversationId;
            if (conversationEventId != null) {
                this.status.conversationEventId = conversationEventId;
            }
            return this;
        }

        public Builder populate(MessageDeliveredEvent event) {
            this.status.messageId = event.getMessageId();
            this.status.profileId = event.getProfileId();
            this.status.messageStatus = LocalMessageStatus.delivered;
            this.status.updatedOn = DateHelper.getUTCMilliseconds(event.getTimestamp());
            this.status.conversationId = event.getConversationId();
            this.status.conversationEventId = event.getConversationEventId();
            return this;
        }

        public Builder populate(MessageReadEvent event) {
            this.status.messageId = event.getMessageId();
            this.status.profileId = event.getProfileId();
            this.status.messageStatus = LocalMessageStatus.read;
            this.status.updatedOn = DateHelper.getUTCMilliseconds(event.getTimestamp());
            this.status.conversationId = event.getConversationId();
            this.status.conversationEventId = event.getConversationEventId();
            return this;
        }
    }

    @Override
    public int compareTo(@NonNull ChatMessageStatus status) {

        if (messageStatus != null && status.messageStatus != null) {
            return messageStatus.compareTo(status.getMessageStatus());
        }
        return 0;
    }
}