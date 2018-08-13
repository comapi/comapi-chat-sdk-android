package com.comapi.chat.model;

/**
 * Describes status of the message interprated locally be the SDK.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public enum LocalMessageStatus {

    /**
     * Status set locally when the request to send the message has been sent.
     */
    sent(0),

    /**
     * Message status set when the 'delivered' event has been delivered from another device.
     */
    delivered(1),

    /**
     * Message status set when the 'read' event has been delivered from another device.
     */
    read(2),

    /**
     * Message status set locally when the sdk failed to send the message.
     */
    error(3);

    private final int value;

    LocalMessageStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
