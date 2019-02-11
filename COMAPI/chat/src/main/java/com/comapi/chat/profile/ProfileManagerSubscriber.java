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

package com.comapi.chat.profile;

import com.comapi.internal.network.model.profile.ComapiProfile;

/**
 * Interface components interested in logged in user profile data.
 *
 * @author Marcin Swierczek
 * @since 1.1.0
 */
public interface ProfileManagerSubscriber {

    /**
     * Service call is running in the background. This is e.g. a good place to lock UI from making new changes to profile data by the user.
     */
    void profileCallStarted();

    /**
     * Service call has been finished successfully. This is e.g. a good place to unlock UI from making new changes to profile data by the user.
     */
    void profileCallFinishedSuccessfully(String message);

    /**
     * Service call has been finished with errors. This is e.g. a good place to display error message and unlock UI from making new changes to profile data by the user.
     *
     * @param message Error message.
     */
    void profileCallFinishedWithError(String message);

    /**
     * SDK is notifying UI about logged user profile details change. This is e.g. a good place to update UI with recent data.
     *
     * @param profile New profile details.
     */
    void onProfileDetails(ComapiProfile profile);

    /**
     * Data version conflict occurred when patching or updating profile data. This is a place to decide (or ask user) if the patch to profile data is still valid.
     *
     * @param resolver Object allowing conflict resolution.
     */
    void onConflictPatchingData(ProfileManager.ConflictResolver resolver);
}