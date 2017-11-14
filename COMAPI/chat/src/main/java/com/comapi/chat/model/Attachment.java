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
import android.text.TextUtils;

import com.comapi.internal.network.ContentData;
import com.comapi.internal.network.model.messaging.UploadContentResponse;

import java.io.File;

/**
 * Attachment to be send with a chat message.
 *
 * @author Marcin Swierczek
 * @since 1.0.1
 */
public class Attachment {

    public static final String LOCAL_PART_TYPE_ERROR = "comapi/error";

    public static final String LOCAL_PART_TYPE_UPLOADING = "comapi/uploading";

    public static final String LOCAL_AUTO_CONVERTED_FOLDER = "AutoConverted";

    private ContentData data;

    private String folder;

    private String id;

    private String type;

    private String url;

    private long size;

    private Throwable error;

    /**
     * Create data object to send from a file.
     *
     * @param data File to upload.
     * @param type Mime type of the data.
     * @return Data object to send.
     */
    public static Attachment create(@NonNull File data, @NonNull String type, @Nullable String folder, @Nullable String name) {
        return new Attachment(ContentData.create(data, type, TextUtils.isEmpty(name) ? data.getName() : name), folder, type);
    }

    /**
     * Create data object to send from a file.
     *
     * @param data Raw data bytes to upload.
     * @param type Mime type of the data.
     * @return Data object to send.
     */
    public static Attachment create(@NonNull byte[] data, @NonNull String type, @Nullable String folder, @Nullable String name) {
        return new Attachment(ContentData.create(data, type, name), folder, type);
    }

    /**
     * Create data object to send from a file.
     *
     * @param data Base64 encoded data to upload.
     * @param type Mime type of the data.
     * @return Data object to send.
     */
    public static Attachment create(@NonNull String data, @NonNull String type, @Nullable String folder, @Nullable String name) {
        return new Attachment(ContentData.create(data, type, name), folder, type);
    }

    private Attachment(ContentData data, String folder, String type) {
        this.data = data;
        this.folder = folder;
        this.type = type;
    }

    /**
     * Get content data to upload.
     *
     * @return Content data to upload.
     */
    public ContentData getData() {
        return data;
    }

    /**
     * Get folder to which the attachment was uploaded.
     *
     * @return Folder to which the attachment was uploaded.
     */
    public String getFolder() {
        return folder;
    }

    /**
     * Get attachment id.
     *
     * @return Attachment id.
     */
    public String getId() {
        return id;
    }

    /**
     * Get attachment mime type.
     *
     * @return Attachment mime type.
     */
    public String getType() {
        return type;
    }

    /**
     * Get url under which the content is available.
     *
     * @return Url under which the content is available.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Get uploaded content size.
     *
     * @return Uploaded content size.
     */
    public long getSize() {
        return size;
    }

    /**
     * Get assigned file name.
     *
     * @return File name.
     */
    public String getName() {
        return data.getName();
    }

    /**
     * For internal use. Update attachment details with upload APIs response.
     *
     * @param response Upload content APIs response.
     * @return Updated attachment.
     */
    public Attachment updateWithUploadDetails(UploadContentResponse response) {
        this.id = response.getId();
        this.url = response.getUrl();
        this.size = response.getSize();
        this.type = response.getType();
        return this;
    }

    /**
     * For internal use. Set upload content exception.
     *
     * @param error Upload content exception.
     * @return Updated attachment.
     */
    public Attachment setError(Throwable error) {
        this.error = error;
        return this;
    }

    /**
     * Get upload content exception.
     *
     * @return Upload content exception.
     */
    public Throwable getError() {
        return error;
    }
}