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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.comapi.internal.network.ContentData;
import com.comapi.internal.network.model.messaging.Part;
import com.comapi.internal.network.model.messaging.UploadContentResponse;

import java.io.File;

/**
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class Attachment {

    private ContentData data;

    private String folder;

    private String id;

    private String type;

    private String url;

    private long size;

    /**
     * Create data object to send from a file.
     *
     * @param data File to upload.
     * @param type Mime type of the data.
     * @return Data object to send.
     */
    public static Attachment create(@NonNull File data, @NonNull String type, @Nullable String folder) {
        return new Attachment(ContentData.create(data, type), folder);
    }

    /**
     * Create data object to send from a file.
     *
     * @param data Raw data bytes to upload.
     * @param type Mime type of the data.
     * @return Data object to send.
     */
    public static Attachment create(@NonNull byte[] data, @NonNull String type, @Nullable String folder) {
        return new Attachment(ContentData.create(data, type), folder);
    }

    /**
     * Create data object to send from a file.
     *
     * @param data Base64 encoded data to upload.
     * @param type Mime type of the data.
     * @return Data object to send.
     */
    public static Attachment create(@NonNull String data, @NonNull String type, @Nullable String folder) {
        return new Attachment(ContentData.create(data, type), folder);
    }

    private Attachment(ContentData data, String folder) {
        this.data = data;
        this.folder = folder;
    }

    public ContentData getData() {
        return data;
    }

    public String getFolder() {
        return folder;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public long getSize() {
        return size;
    }

    public Attachment updateUploadDetails(UploadContentResponse response) {
        this.id = response.getId();
        this.url = response.getUrl();
        this.size = response.getSize();
        this.type = response.getType();
        return this;
    }

    public Part createPart() {
        return Part.builder().setName(id).setSize(size).setType(type).setUrl(url).build();
    }
}