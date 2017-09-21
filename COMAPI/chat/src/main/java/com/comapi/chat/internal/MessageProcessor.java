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

package com.comapi.chat.internal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.comapi.chat.model.Attachment;
import com.comapi.chat.model.ChatMessage;
import com.comapi.internal.log.Logger;
import com.comapi.internal.network.model.messaging.MessageSentResponse;
import com.comapi.internal.network.model.messaging.MessageToSend;
import com.comapi.internal.network.model.messaging.Part;
import com.comapi.internal.network.model.messaging.Sender;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.comapi.chat.EventsHandler.MESSAGE_METADATA_TEMP_ID;

/**
 * Manages changes to message in the process oif uploading attachments and sending message.
 *
 * @author Marcin Swierczek
 * @since 1.0.1
 */
public class MessageProcessor {

    private final int maxPartSize;

    private final Logger log;

    private final String tempId;

    private final String conversationId;

    private final String sender;

    private List<Part> publicParts;

    private List<Part> tempParts;

    private List<Part> errorParts;

    private List<Attachment> attachments;

    private MessageToSend originalMessage;

    /**
     * Recommended constructor.
     *
     * @param conversationId Conversation id to which the message belongs.
     * @param sender         Profile id of the current user to be set as a sender.
     * @param message        Message to send.
     * @param attachments    Attachments to upload with the message.
     * @param maxPartSize    Maximum size of a part data.
     * @param log            Logger instance.
     */
    MessageProcessor(@NonNull final String conversationId, @NonNull final String sender, @NonNull final MessageToSend message, @Nullable final List<Attachment> attachments, int maxPartSize, @NonNull final Logger log) {

        this.log = log;
        this.maxPartSize = maxPartSize;
        this.conversationId = conversationId;
        this.sender = sender;
        this.originalMessage = message;
        if (attachments != null) {
            this.attachments = new ArrayList<>(attachments);
        } else {
            this.attachments = new ArrayList<>();
        }
        if (message.getParts() != null) {
            this.publicParts = new ArrayList<>(message.getParts());
        } else {
            this.publicParts = new ArrayList<>();
        }
        this.tempParts = new ArrayList<>();
        this.errorParts = new ArrayList<>();

        //Generate temporary id for a message to be put into db before sending, allows seamless update of chat screen
        tempId = UUID.randomUUID().toString();
        message.addMetadata(MESSAGE_METADATA_TEMP_ID, tempId);
    }

    /**
     * Add a temporary message part per not uet uploaded attachment.
     */
    public void preparePreUpload() {
        convertLargeParts();
        for (Attachment a : attachments) {
            tempParts.add(createTempPart(a));
        }
    }

    /**
     * Replace temporary attachment parts with final parts with upload details or error message.
     *
     * @param attachments List of attachments details.
     */
    public void preparePostUpload(final List<Attachment> attachments) {
        tempParts.clear();
        if (!attachments.isEmpty()) {
            for (Attachment a : attachments) {
                if (a.getError() != null) {
                    errorParts.add(createErrorPart(a));
                } else {
                    publicParts.add(createPart(a));
                }
            }
        }
    }

    /**
     * Create message part based on attachment upload error details.
     *
     * @return Message part.
     */
    private Part createErrorPart(Attachment a) {
        return Part.builder().setName(String.valueOf(a.hashCode())).setSize(0).setType(Attachment.LOCAL_PART_TYPE_ERROR).setUrl(null).setData(a.getError().getLocalizedMessage()).build();
    }

    /**
     * Create message part based on attachment upload. This is a temporary message to indicate that one of the attachments for this message is being uploaded.
     *
     * @return Message part.
     */
    private Part createTempPart(Attachment a) {
        return Part.builder().setName(String.valueOf(a.hashCode())).setSize(0).setType(Attachment.LOCAL_PART_TYPE_UPLOADING).setUrl(null).setData("Uploading attachment.").build();
    }

    /**
     * Create message part based on attachment details.
     *
     * @return Message part.
     */
    private Part createPart(Attachment a) {
        return Part.builder().setName(a.getId()).setSize(a.getSize()).setType(a.getType()).setUrl(a.getUrl()).build();
    }

    /**
     * Convert message parts which data part exceed the limit of characters ({@link MessageProcessor#maxPartSize}) to content data.
     */
    private void convertLargeParts() {

        List<Attachment> newAttachments = new ArrayList<>();

        if (!publicParts.isEmpty()) {
            try {

                List<Part> toLarge = new ArrayList<>();
                for (Part p : publicParts) {
                    if (p.getData() != null && p.getData().length() > maxPartSize) {
                        String type = p.getType() != null ? p.getType() : "application/octet-stream";
                        newAttachments.add(Attachment.create(p.getData(), type, "AutoConverted"));
                        toLarge.add(p);
                        log.w("Message part " + p.getName() + " to large (" + p.getData().length() + ">" + maxPartSize + ") - converting to attachment.");
                    }
                }
                if (!toLarge.isEmpty()) {
                    publicParts.removeAll(toLarge);
                }

            } catch (Exception e) {
                log.f("Error when removing large message parts", e);
            }
        }

        attachments.addAll(newAttachments);
    }

    /**
     * Gets list of attachments to be sent with a message.
     *
     * @return List of attachments
     */
    public List<Attachment> getAttachments() {
        return attachments;
    }

    /**
     * Get message. Call {@link this#prepareMessageToSend()} before sending to the server.
     *
     * @return Message.
     */
    public MessageToSend getMessage() {
        return originalMessage;
    }

    private List<Part> getAllParts() {
        List<Part> allParts = new ArrayList<>();
        allParts.addAll(publicParts);
        allParts.addAll(tempParts);
        allParts.addAll(errorParts);
        return allParts;
    }

    /**
     * Prepare message to be sent through messaging service.
     *
     * @return Message to be sent with messaging service.
     */
    public MessageToSend prepareMessageToSend() {
        originalMessage.getParts().clear();
        originalMessage.getParts().addAll(publicParts);
        return originalMessage;
    }

    /**
     * Create a temporary message to be displayed while the message is being send. to be replaced later on with a final message constructed with {@link this#createFinalMessage(MessageSentResponse)}
     *
     * @return Temporary message to be saved in persistance store.
     */
    public ChatMessage createTempMessage() {
        return ChatMessage.builder()
                .setMessageId(tempId)
                .setSentEventId(-1L) // temporary value, will be replaced by persistence controller
                .setConversationId(conversationId)
                .setSentBy(sender)
                .setFromWhom(new Sender(sender, sender))
                .setSentOn(System.currentTimeMillis())
                .setParts(getAllParts())
                .setMetadata(originalMessage.getMetadata())
                .build();
    }

    /**
     * Create a final message for a conversation. At this point we know message id and sent event id.
     *
     * @param response Response from sending message call.
     * @return Final message to be saved in persistance store.
     */
    public ChatMessage createFinalMessage(MessageSentResponse response) {
        return ChatMessage.builder()
                .setMessageId(response.getId())
                .setSentEventId(response.getEventId())
                .setConversationId(conversationId)
                .setSentBy(sender)
                .setFromWhom(new Sender(sender, sender))
                .setSentOn(System.currentTimeMillis())
                .setParts(getAllParts())
                .setMetadata(originalMessage.getMetadata()).build();
    }

    /**
     * Conversation id to which the message belongs.
     * @return Conversation id to which the message belongs.
     */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * Message id of an temporary message. This message will be removed when it will be successfully delivered to the server. Same message but with correct message id will be inserted instead.
     *
     * @return Message id of an temporary message.
     */
    public String getTempId() {
        return tempId;
    }

    /**
     * Profile id of the current user to be set as a sender.
     *
     * @return Profile id of the current user to be set as a sender.
     */
    public String getSender() {
        return sender;
    }
}