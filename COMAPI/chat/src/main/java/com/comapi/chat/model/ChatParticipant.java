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

import com.comapi.internal.network.model.conversation.Participant;
import com.comapi.internal.network.model.events.conversation.ParticipantEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantUpdatedEvent;

/**
 * Conversation participant model for db storage.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ChatParticipant {

    /**
     * Globally unique profile identifier.
     */
    private String participantId;

    /**
     * Get participant privileges in associated conversation.
     */
    private ChatRole role;

    /**
     * Globally unique profile identifier.
     *
     * @return Globally unique profile identifier.
     */
    public String getParticipantId() {
        return participantId;
    }

    /**
     * Get participant role in associated conversation.
     *
     * @return Get participant role in associated conversation.
     */
    public ChatRole getRole() {
        return role;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        ChatParticipant participant;

        Builder() {
            participant = new ChatParticipant();
        }

        public ChatParticipant build() {
            return participant;
        }

        public Builder setParticipantId(String value) {
            participant.participantId = value;
            return this;
        }

        public Builder setRole(ChatRole value) {
            participant.role = value;
            return this;
        }

        public Builder populate(ParticipantEvent event) {
            participant.participantId = event.getProfileId();
            participant.role = ChatRole.valueOf(event.getRole());
            return this;
        }

        public Builder populate(ParticipantUpdatedEvent event) {
            participant.participantId = event.getProfileId();
            participant.role = ChatRole.valueOf(event.getRole());
            return this;
        }

        public Builder populate(Participant foundationParticipant) {
            participant.participantId = foundationParticipant.getId();
            participant.role = ChatRole.valueOf(foundationParticipant.getRole());
            return this;
        }
    }
}