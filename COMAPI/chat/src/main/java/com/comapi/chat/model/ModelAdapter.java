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

import com.comapi.chat.ChatResult;
import com.comapi.chat.database.model.DbOrphanedEvent;
import com.comapi.internal.Parser;
import com.comapi.internal.helpers.DateHelper;
import com.comapi.internal.network.ComapiResult;
import com.comapi.internal.network.model.conversation.Participant;
import com.comapi.internal.network.model.messaging.MessageReceived;
import com.comapi.internal.network.model.messaging.MessageStatus;
import com.comapi.internal.network.model.messaging.OrphanedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class with helper methods to adapt interfaces of Foundation SDK to Chat SDK.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ModelAdapter {

    /**
     * Translates Orphaned events to message statuses.
     *
     * @param dbOrphanedEvents Orphaned events received when paging back message history.
     * @return Message statuses to be applied to appropriate messages.
     */
    public List<ChatMessageStatus> adaptEvents(List<DbOrphanedEvent> dbOrphanedEvents) {

        List<ChatMessageStatus> statuses = new ArrayList<>();

        Parser parser = new Parser();
        for (DbOrphanedEvent event : dbOrphanedEvents) {
            OrphanedEvent orphanedEvent = parser.parse(event.event(), OrphanedEvent.class);
            statuses.add(ChatMessageStatus.builder().populate(orphanedEvent.getConversationId(), orphanedEvent.getMessageId(), orphanedEvent.getProfileId(),
                    orphanedEvent.isEventTypeRead() ? LocalMessageStatus.read : LocalMessageStatus.delivered,
                    DateHelper.getUTCMilliseconds(orphanedEvent.getTimestamp()), (long) orphanedEvent.getConversationEventId()).build());
        }

        return statuses;
    }

    /**
     * Translates received messages through message query to chat SDK model.
     *
     * @param messagesReceived Foundation message objects.
     * @return Chat SDK message objects.
     */
    public List<ChatMessage> adaptMessages(List<MessageReceived> messagesReceived) {

        List<ChatMessage> chatMessages = new ArrayList<>();

        if (messagesReceived != null) {
            for (MessageReceived msg : messagesReceived) {

                ChatMessage adaptedMessage = ChatMessage.builder().populate(msg).build();
                List<ChatMessageStatus> adaptedStatuses = adaptStatuses(msg.getConversationId(), msg.getMessageId(), msg.getStatusUpdate());
                for (ChatMessageStatus s : adaptedStatuses) {
                    adaptedMessage.addStatusUpdate(s);
                }
                chatMessages.add(adaptedMessage);
            }
        }

        return chatMessages;
    }

    /**
     * Translates received message statuses through message query to chat SDK model.
     *
     * @param conversationId Conversation unique id.
     * @param messageId      Message unique id.
     * @param statuses       Foundation statuses.
     * @return
     */
    public List<ChatMessageStatus> adaptStatuses(String conversationId, String messageId, Map<String, MessageReceived.Status> statuses) {
        List<ChatMessageStatus> adapted = new ArrayList<>();
        for (String key : statuses.keySet()) {
            MessageReceived.Status status = statuses.get(key);
            adapted.add(ChatMessageStatus.builder().populate(conversationId, messageId, key, status.getStatus().compareTo(MessageStatus.delivered) == 0 ? LocalMessageStatus.delivered : LocalMessageStatus.read, DateHelper.getUTCMilliseconds(status.getTimestamp()), null).build());
        }
        return adapted;
    }

    /**
     * Translates Foundation result to Chat SDK result.
     *
     * @param result Foundation result.
     * @return Chat SDK result.
     */
    public ChatResult adaptResult(ComapiResult<?> result) {

        return new ChatResult(result.isSuccessful(), result.isSuccessful() ? null : new ChatResult.Error(result.getCode(), result.getMessage() + " " + result.getErrorBody()));
    }

    /**
     * Translates Foundation result to Chat SDK result.
     *
     * @param result  Foundation result.
     * @param success True if required Chat SDK processing was successful.
     * @return Chat SDK result.
     */
    public ChatResult adaptResult(ComapiResult<?> result, Boolean success) {

        return new ChatResult(result.isSuccessful() && success, result.isSuccessful() ? null : new ChatResult.Error(result.getCode(), result.getMessage() + " " + result.getErrorBody()));
    }

    /**
     * Translates Foundation conversation participants to Chat SDK conversation participants.
     *
     * @param participants Foundation conversation participants.
     * @return Chat SDK result.
     */
    public List<ChatParticipant> adapt(List<Participant> participants) {
        List<ChatParticipant> result = new ArrayList<>();
        if (participants != null && !participants.isEmpty()) {
            for (Participant p : participants) {
                result.add(ChatParticipant.builder().populate(p).build());
            }
        }
        return result;
    }
}