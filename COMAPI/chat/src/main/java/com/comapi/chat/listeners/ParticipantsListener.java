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

package com.comapi.chat.listeners;

import com.comapi.internal.network.model.events.conversation.ParticipantAddedEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantRemovedEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantUpdatedEvent;

/**
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public abstract class ParticipantsListener {

    /**
     * Dispatched participant added to a conversation event.
     *
     * @param event Participant added event.
     */
    public abstract void onParticipantAdded(ParticipantAddedEvent event);

    /**
     * Dispatched participant updated event.
     *
     * @param event Participant removed event.
     */
    public abstract void onParticipantUpdated(ParticipantUpdatedEvent event);

    /**
     * Dispatched participant removed event.
     *
     * @param event Event to dispatch.
     */
    public abstract void onParticipantRemoved(ParticipantRemovedEvent event);
}
