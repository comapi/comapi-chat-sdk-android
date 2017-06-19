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
import com.comapi.internal.network.model.conversation.ConversationDetails;
import com.comapi.internal.network.model.conversation.Participant;
import com.comapi.internal.network.model.events.conversation.ParticipantEvent;
import com.comapi.internal.network.model.messaging.MessageReceived;
import com.comapi.internal.network.model.messaging.MessageStatus;
import com.comapi.internal.network.model.messaging.OrphanedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ModelAdapter {

    public List<ChatMessageStatus> adaptEvents(List<DbOrphanedEvent> dbOrphanedEvents) {

        List<ChatMessageStatus> statuses = new ArrayList<>();

        Parser parser = new Parser();
        for (DbOrphanedEvent event : dbOrphanedEvents) {
            OrphanedEvent orphanedEvent = parser.parse(event.event(), OrphanedEvent.class);
            statuses.add(new ChatMessageStatus(orphanedEvent.getMessageId(), orphanedEvent.getProfileId(),
                    orphanedEvent.isEventTypeRead() ? MessageStatus.read : (orphanedEvent.isEventTypeDelivered() ? MessageStatus.delivered : null),
                    DateHelper.getUTCMilliseconds(orphanedEvent.getTimestamp())));
        }

        return statuses;
    }

    public List<ChatMessage> adaptMessages(List<MessageReceived> messagesReceived) {

        List<ChatMessage> chatMessages = new ArrayList<>();
        for (MessageReceived msg : messagesReceived) {
            chatMessages.add(ChatMessage.builder().populate(msg).build());
        }

        return chatMessages;
    }

    public ChatResult adaptResult(ComapiResult<?> result) {

        return new ChatResult(result.isSuccessful(), result.isSuccessful() ? null : new ChatResult.Error(result.getCode(), result.getMessage()+" "+result.getErrorBody()));
    }

    public ChatResult adaptResult(ComapiResult<?> result, Boolean success) {

        return new ChatResult(result.isSuccessful() && success, result.isSuccessful() ? null : new ChatResult.Error(result.getCode(), result.getMessage()+" "+result.getErrorBody()));
    }

    public ChatParticipant adapt(Participant participant) {

        return ChatParticipant.builder().populate(participant).build();
    }

    public List<ChatParticipant> adapt(List<Participant> participants) {
        List<ChatParticipant> result = new ArrayList<>();
        if (participants != null && !participants.isEmpty()) {
            for (Participant p :participants) {
                result.add(adapt(p));
            }
        }
        return result;
    }

    public Map<String, Participant> makeMapFromDownloadedParticipants(List<Participant> result) {
        Map<String, Participant> map = new HashMap<>();
        for (Participant participant : result) {
            map.put(participant.getId(), participant);
        }

        return map;
    }

    public Map<String, ChatParticipant> makeMapFromSavedParticipants(List<ChatParticipant> result) {
        Map<String, ChatParticipant> map = new HashMap<>();
        for (ChatParticipant participant : result) {
            map.put(participant.getParticipantId(), participant);
        }

        return map;
    }

    public <T extends ParticipantEvent> Map<String, List<ChatParticipant>> makeMapFromParticipantEvents(List<T> participantAdded) {

        if (!participantAdded.isEmpty()) {
            Map<String, List<ChatParticipant>> map = new HashMap<>();
            for (ParticipantEvent event : participantAdded) {
                if (map.containsKey(event.getConversationId())) {
                    map.get(event.getConversationId()).add(ChatParticipant.builder().populate(event).build());
                } else {
                    List<ChatParticipant> list = new ArrayList<>();
                    list.add(ChatParticipant.builder().populate(event).build());
                    map.put(event.getConversationId(), list);
                }
            }

            return map;
        } else {

            return null;
        }
    }

    public Map<String, ChatConversationBase> makeMapFromSavedConversations(List<ChatConversationBase> list) {

        Map<String, ChatConversationBase> map = new HashMap<>();

        if (list != null && !list.isEmpty()) {
            for (ChatConversationBase details : list) {
                map.put(details.getConversationId(), details);
            }
        }

        return map;
    }

    public Map<String, ConversationDetails> makeMapFromDownloadedConversations(List<ConversationDetails> list) {

        Map<String, ConversationDetails> map = new HashMap<>();

        if (list != null && !list.isEmpty()) {
            for (ConversationDetails details : list) {
                map.put(details.getId(), details);
            }
        }

        return map;
    }

    public List<String> getParticipantIds(List<ChatParticipant> participants) {

        List<String> ids = new ArrayList<>();

        if (!participants.isEmpty()) {
            for (int i = 0; i< participants.size(); i++) {
                ids.add(participants.get(i).getParticipantId());
            }
        }

        return ids;
    }

    public ChatResult adaptResult(boolean isSuccess, int code, String message, String errorBody) {
        return null;
    }
}