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
import com.comapi.internal.network.model.events.ProfileUpdateEvent;

import java.util.Map;

/**
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ChatProfile {

    protected String profileId;

    protected Long publishedOn;

    protected Map<String, Object> payload;

    protected String eTag;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        ChatProfile profile;

        Builder() {
            profile = new ChatProfile();
        }

        public ChatProfile build() {
            return profile;
        }

        public Builder populate(ProfileUpdateEvent event) {
            profile.profileId = event.getProfileId();
            profile.publishedOn = DateHelper.getUTCMilliseconds(event.getPublishedOn());
            profile.eTag = event.getETag();
            profile.payload = event.getPayload();
            return this;
        }
    }

    public String getProfileId() {
        return profileId;
    }

    public Long getPublishedOn() {
        return publishedOn;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public String geteTag() {
        return eTag;
    }
}
