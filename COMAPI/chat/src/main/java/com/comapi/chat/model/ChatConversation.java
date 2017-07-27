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

import com.comapi.internal.network.model.conversation.Conversation;
import com.comapi.internal.network.model.conversation.ConversationDetails;
import com.comapi.internal.network.model.conversation.Role;
import com.comapi.internal.network.model.events.conversation.ConversationCreateEvent;
import com.comapi.internal.network.model.events.conversation.ConversationUndeleteEvent;
import com.comapi.internal.network.model.events.conversation.ConversationUpdateEvent;

/**
 * Conversation details.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ChatConversation extends ChatConversationBase {

    /**
     * Name of the Conversation.
     */
    private String name;

    /**
     * Description of the Conversation.
     */
    private String description;

    /**
     * Privileges of owner in this conversation.
     */
    private Role ownerRoles;

    /**
     * Privileges of participant in this conversation.
     */
    private Role participantRoles;

    /**
     * True if the Conversation is public.
     */
    private boolean isPublic;

    /**
     * Get name of the Conversation.
     *
     * @return Name of the Conversation.
     */
    public String getName() {
        return name;
    }

    /**
     * Get description of the Conversation.
     *
     * @return Description of the Conversation.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets owner privileges in associated conversation.
     *
     * @return Gets owner privileges in associated conversation.
     */
    public Role getOwnerPrivileges() {
        return ownerRoles;
    }

    /**
     * Gets participant privileges in associated conversation.
     *
     * @return Gets participant privileges in associated conversation.
     */
    public Role getParticipantPrivileges() {
        return participantRoles;
    }

    /**
     * Check if the Conversation is public.
     *
     * @return True if Conversation is publicly accessible.
     */
    public boolean isPublic() {
        return isPublic;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ChatConversation conversation;

        private Builder() {
            conversation = new ChatConversation();
        }

        public Builder setConversationId(String id) {
            conversation.conversationId = id;
            return this;
        }

        public Builder setFirstLocalEventId(Long id) {
            conversation.firstLocalEventId = id;
            return this;
        }

        public Builder setLastLocalEventId(Long id) {
            conversation.lastLocalEventId = id;
            return this;
        }

        public Builder setLatestRemoteEventId(Long id) {
            conversation.latestRemoteEventId = id;
            return this;
        }

        public Builder setUpdatedOn(Long updatedOn) {
            conversation.setUpdatedOn(updatedOn);
            return this;
        }

        public Builder setETag(String eTag) {
            conversation.eTag = eTag;
            return this;
        }

        public ChatConversation build() {
            return conversation;
        }

        public Builder populate(ConversationCreateEvent event) {
            conversation.conversationId = event.getConversation().getId();
            conversation.name = event.getConversation().getName();
            conversation.description = event.getConversation().getDescription();
            conversation.ownerRoles = event.getConversation().getRoles().getOwner();
            conversation.participantRoles = event.getConversation().getRoles().getParticipant();
            return this;
        }

        public Builder populate(ConversationUpdateEvent event) {
            conversation.conversationId = event.getConversationId();
            conversation.name = event.getConversationName();
            conversation.description = event.getDescription();
            conversation.ownerRoles = event.getRoles().getOwner();
            conversation.participantRoles = event.getRoles().getParticipant();
            conversation.eTag = event.getETag();
            return this;
        }

        public Builder populate(ConversationUndeleteEvent event) {
            conversation.conversationId = event.getConversation().getId();
            conversation.name = event.getConversation().getName();
            conversation.description = event.getConversation().getDescription();
            conversation.ownerRoles = event.getConversation().getRoles().getOwner();
            conversation.participantRoles = event.getConversation().getRoles().getParticipant();
            conversation.eTag = event.getETag();
            return this;
        }

        public Builder populate(Conversation details) {
            conversation.conversationId = details.getId();
            conversation.name = details.getName();
            conversation.description = details.getDescription();
            conversation.ownerRoles = details.getRoles().getOwner();
            conversation.participantRoles = details.getRoles().getParticipant();
            conversation.latestRemoteEventId = details.getLatestSentEventId();
            conversation.eTag = details.getETag();
            return this;
        }

        public Builder populate(ConversationDetails details, String eTag) {
            conversation.conversationId = details.getId();
            conversation.name = details.getName();
            conversation.description = details.getDescription();
            conversation.ownerRoles = details.getRoles().getOwner();
            conversation.participantRoles = details.getRoles().getParticipant();
            conversation.eTag = eTag;
            return this;
        }

        public Builder populate(ChatConversationBase base) {
            conversation.conversationId = base.getConversationId();
            conversation.updatedOn = base.getUpdatedOn();
            conversation.latestRemoteEventId = base.getLastRemoteEventId();
            conversation.lastLocalEventId = base.getLastLocalEventId();
            conversation.firstLocalEventId = base.getFirstLocalEventId();
            conversation.eTag = base.getETag();
            return this;
        }

        public Builder populate(ChatConversation base) {
            populate((ChatConversationBase) base);
            conversation.description = base.description;
            conversation.name = base.name;
            conversation.ownerRoles = base.getOwnerPrivileges();
            conversation.participantRoles = base.getParticipantPrivileges();
            conversation.isPublic = base.isPublic();
            conversation.eTag = base.getETag();
            return this;
        }
    }
}
