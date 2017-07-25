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
import com.comapi.chat.model.ChatMessage;
import com.comapi.internal.network.model.conversation.Role;
import com.comapi.internal.network.model.messaging.Part;
import com.comapi.internal.network.model.messaging.Sender;

import java.util.List;

/**
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class DBObjectsHelper {


    public static ChatConversation createConversation(String id) {

        return new ChatConversation() {

            @Override
            public String getConversationId() {
                return id;
            }

            @Override
            public String getName() {
                return "name";
            }

            @Override
            public String getDescription() {
                return "description";
            }

            @Override
            public Role getOwnerPrivileges() {
                return Role.builder().build();
            }

            @Override
            public Role getParticipantPrivileges() {
                return Role.builder().build();
            }

            @Override
            public String getETag() {
                return "ETag-123";
            }

            @Override
            public boolean isPublic() {
                return false;
            }

            @Override
            public Long getFirstLocalEventId() {
                return 99L;
            }

            @Override
            public Long getLastLocalEventId() {
                return 190L;
            }
        };
    }

    public static ChatMessage createMessage(String messageId, String conversationId) {

        return new ChatMessage() {

            @Override
            public Long getSentEventId() {
                return 0L;
            }

            @Override
            public String getMessageId() {
                return messageId;
            }

            @Override
            public String getConversationId() {
                return conversationId;
            }

            @Override
            public Sender getFromWhom() {
                return new Sender("profileId", "name");
            }

            @Override
            public String getSentBy() {
                return "profileId";
            }

            @Override
            public Long getSentOn() {
                return 0L;
            }

            @Override
            public List<Part> getParts() {
                return null;
            }
        };
    }
}