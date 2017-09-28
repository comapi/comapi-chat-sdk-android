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

import com.comapi.RxComapiClient;
import com.comapi.chat.model.Attachment;
import com.comapi.internal.log.Logger;
import com.comapi.internal.network.model.messaging.MessageToSend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import rx.Observable;

/**
 * Controller for the process of uploading content with a message.
 *
 * @author Marcin Swierczek
 * @since 1.0.1
 */
public class AttachmentController {

    private final int maxPartSize;

    private final Logger log;

    /**
     * Recommended constructor.
     *
     * @param log         Logger instance.
     * @param maxPartSize Maximum size (number of characters) of a single Part data.
     */
    public AttachmentController(Logger log, int maxPartSize) {
        this.maxPartSize = maxPartSize;
        this.log = log;
    }

    /**
     * Create observable to perform attachments upload.
     *
     * @param data List of Attachments to upload.
     * @param c    Comapi client to acces service APIs.
     * @return Observable to perform attachments upload.
     */
    public Observable<List<Attachment>> uploadAttachments(@NonNull List<Attachment> data, @NonNull RxComapiClient c) {
        if (!data.isEmpty()) {
            return Observable.concatDelayError(upload(c, data)).toList();
        } else {
            return Observable.fromCallable(() -> null);
        }
    }

    /**
     * Create instance of message processor to handle sending process of a message with attachments.
     *
     * @param message        Message to send.
     * @param attachments    Attachments to upload with a message.
     * @param conversationId Unique conversation id.
     * @param profileId      User profile id.
     * @return Message processor
     */
    public MessageProcessor createMessageProcessor(@NonNull MessageToSend message, @Nullable List<Attachment> attachments, @NonNull String conversationId, @NonNull String profileId) {
        return new MessageProcessor(conversationId, profileId, message, attachments, maxPartSize, log);
    }

    /**
     * Create list of upload attachment observables.
     */
    private Collection<Observable<Attachment>> upload(RxComapiClient client, List<Attachment> data) {
        Collection<Observable<Attachment>> obsList = new ArrayList<>();
        for (Attachment a : data) {
            obsList.add(upload(client, a));
        }
        return obsList;
    }

    /**
     * Upload single attachment and update the details in it from the response.
     */
    private Observable<Attachment> upload(RxComapiClient client, Attachment a) {
        return client.service().messaging().uploadContent(a.getFolder(), a.getData())
                .map(response -> a.updateWithUploadDetails(response.getResult()))
                .doOnError(t -> log.e("Error uploading attachment. " + t.getLocalizedMessage()))
                .onErrorReturn(a::setError);
    }
}