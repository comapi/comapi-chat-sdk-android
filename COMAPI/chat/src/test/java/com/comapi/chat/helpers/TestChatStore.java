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

package com.comapi.chat.helpers;

import com.comapi.chat.model.ChatConversation;
import com.comapi.chat.model.ChatConversationBase;
import com.comapi.chat.model.ChatMessage;
import com.comapi.chat.model.ChatMessageStatus;
import com.comapi.chat.model.ChatParticipant;
import com.comapi.chat.model.ChatProfile;
import com.comapi.chat.model.ChatStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Store interfaces 'in memory' implementation for tests.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class TestChatStore extends ChatStore {

    private final static long WAIT_TIME = 500;

    private final static Object lock = new Object();

    private final Map<String, ChatConversationBase> conversations = new HashMap<>();
    private final Map<String, ChatMessage> messages = new HashMap<>();
    private final Map<String, ChatMessageStatus> messagesStatuses = new HashMap<>();
    private final Map<String, List<ChatParticipant>> participants = new HashMap<>();

    @Override
    public ChatConversationBase getConversation(String conversationId) {

        ChatConversationBase conversation = null;

        synchronized (lock) {
            try {
                lock.wait(WAIT_TIME);
                conversation = conversations.get(conversationId);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.notifyAll();
            }
        }

        return conversation;
    }

    @Override
    public List<ChatConversationBase> getAllConversations() {
        return new ArrayList<>(conversations.values());
    }

    @Override
    public boolean upsert(ChatConversation conversation) {

        synchronized (lock) {
            try {
                lock.wait(WAIT_TIME);
                conversations.put(conversation.getConversationId(), conversation);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.notifyAll();
            }
        }
        return true;
    }

    @Override
    public boolean update(ChatConversationBase conversation) {
        synchronized (lock) {
            try {
                lock.wait(WAIT_TIME);
                conversations.put(conversation.getConversationId(), conversation);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.notifyAll();
            }
        }
        return true;
    }

    @Override
    public boolean deleteConversation(String conversationId) {

        synchronized (lock) {
            try {
                lock.wait(WAIT_TIME);
                deleteAllMessages(conversationId);
                conversations.remove(conversationId);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.notifyAll();
            }
        }

        return true;
    }

    @Override
    public boolean upsert(ChatMessage message) {

        synchronized (lock) {
            try {
                lock.wait(WAIT_TIME);
                messages.put(message.getMessageId(), message);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.notifyAll();
            }
        }

        return true;
    }

    @Override
    public boolean upsert(ChatMessageStatus status) {
        synchronized (lock) {
            try {
                lock.wait(WAIT_TIME);
                messagesStatuses.put(status.getMessageId(), status);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.notifyAll();
            }
        }

        return true;
    }

    @Override
    public ChatMessageStatus getStatus(String messageId) {

        ChatMessageStatus result = null;

        synchronized (lock) {
            try {
                lock.wait(WAIT_TIME);
                result = messagesStatuses.get(messageId);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.notifyAll();
            }
        }

        return result;
    }

    @Override
    public List<ChatParticipant> getParticipants(String conversationId) {

        List<ChatParticipant> result = null;

        synchronized (lock) {
            try {
                lock.wait(WAIT_TIME);
                result = participants.get(conversationId);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.notifyAll();
            }
        }

        return result;
    }

    @Override
    public boolean upsert(String conversationId, ChatParticipant participant) {

        synchronized (lock) {
            try {
                lock.wait(WAIT_TIME);
                if (!participants.containsKey(conversationId)) {
                    participants.put(conversationId, new ArrayList<>());
                }
                participants.get(conversationId).add(participant);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.notifyAll();
            }
        }

        return true;
    }

    @Override
    public boolean removeParticipant(String conversationId, String profileId) {

        int toDelete = -1;

        List<ChatParticipant> list = participants.get(conversationId);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getParticipantId().equals(profileId)) {
                    toDelete = i;
                }
            }
        }

        if (toDelete >= 0) {
            list.remove(toDelete);
        }

        return true;
    }

    @Override
    public boolean upsert(ChatProfile message) {
        return true;
    }

    @Override
    public boolean clearDatabase() {

        synchronized (lock) {
            try {
                lock.wait(WAIT_TIME);
                conversations.clear();
                messages.clear();
                participants.clear();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.notifyAll();
            }
        }

        return true;
    }

    @Override
    public boolean deleteAllMessages(String conversationId) {

        synchronized (lock) {
            try {
                lock.wait(WAIT_TIME);

                List<String> idsToRemove = new ArrayList<>();

                for (ChatMessage msg : messages.values()) {
                    if (conversationId.equals(msg.getConversationId())) {
                        idsToRemove.add(msg.getMessageId());
                    }
                }

                for (String id : idsToRemove) {
                    messages.remove(id);
                }

                participants.remove(conversationId);

            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.notifyAll();
            }
        }

        return true;
    }

    @Override
    public boolean deleteMessage(String messageId) {
        messages.remove(messageId);
        return true;
    }

    public Map<String, ChatMessage> getMessages() {
        return messages;
    }

    public Map<String, ChatConversationBase> getConversations() {
        return conversations;
    }

    public void addConversationToStore(String conversationId, long first, long last, long updatedOn, String eTag) {

        ChatConversationBase conversationInStore1 = ChatConversationBase.baseBuilder()
                .setConversationId(conversationId)
                .setETag(eTag)
                .setFirstEventId(first)
                .setLastEventIdd(last)
                .setLatestRemoteEventId(last)
                .setUpdatedOn(updatedOn)
                .build();
        conversations.put(conversationId, conversationInStore1);

    }
}