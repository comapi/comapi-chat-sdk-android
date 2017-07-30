package com.comapi.chat.helpers;

import com.comapi.internal.network.model.messaging.MessageSentResponse;

/**
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class MockMsgSentResponse extends MessageSentResponse {

    private String mockedId;

    public MockMsgSentResponse(String id) {
        this.mockedId = id;
    }

    @Override
    public String getId() {
        return mockedId;
    }
}
