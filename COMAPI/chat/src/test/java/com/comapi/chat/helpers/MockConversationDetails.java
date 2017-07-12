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

import com.comapi.internal.network.model.conversation.Conversation;
import com.comapi.internal.network.model.conversation.ConversationDetails;
import com.comapi.internal.network.model.conversation.Role;
import com.comapi.internal.network.model.conversation.Roles;

/**
 * Instance of {@link ConversationDetails} with some initial values.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class MockConversationDetails extends Conversation {

    public static final boolean IS_PUBLIC = false;
    public static final String NAME = "name";
    public static final String ETAG = "eTag";
    public static final Integer PARTICIPANT_COUNT = 2;
    public static final Long LATEST_SENT_ID = 234L;
    public static final String DESCRIPTION = "description";
    public static final Roles ROLES = new Roles(Role.builder().build(), Role.builder().build());

    public MockConversationDetails(String conversationId) {
        this.isPublic = IS_PUBLIC;
        this.id = conversationId;
        this.name = NAME;
        this.description = DESCRIPTION;
        this.roles = ROLES;
        this.eTag = ETAG;
        this.participantCount = PARTICIPANT_COUNT;
        this.latestSentEventId = LATEST_SENT_ID;
    }

    public MockConversationDetails setName(String name) {
        this.name = name;
        return this;
    }

    public MockConversationDetails setETag(String eTag) {
        this.eTag = eTag;
        return this;
    }
}