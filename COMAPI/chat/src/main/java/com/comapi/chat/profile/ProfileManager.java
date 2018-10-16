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

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import com.comapi.ProfileListener;
import com.comapi.RxComapiClient;
import com.comapi.chat.ObservableExecutor;
import com.comapi.internal.network.ComapiResult;
import com.comapi.internal.network.model.events.ProfileUpdateEvent;
import com.comapi.internal.network.model.profile.ComapiProfile;

import java.lang.ref.WeakReference;
import java.util.List;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;

import static android.content.Context.MODE_PRIVATE;

/**
 * Class to help managing profile details for registered user.
 *
 * @author Marcin Swierczek
 * @since 1.1.0
 */
public class ProfileManager {

    // SharedPreferences file name prefix
    private static final String PREF_PREFIX = "profile.comapi";

    // SharedPreferences key for profile eTag
    static final String KEY_ETAG = "eTag";

    private static final int ETAG_NOT_VALID = 412;

    final private ProfileChecker profileChecker;
    final private ProfileUpdater profileUpdater;

    /**
     * Internal usage constructor. To obtain ProfileManager instance use ComapiChatClient#createProfileManager method.
     *
     * @param context Application context.
     * @param client  Foundation SDK client.
     */
    public ProfileManager(@NonNull Context context, @NonNull RxComapiClient client, @NonNull ObservableExecutor executor) {
        String fileName = PREF_PREFIX + client.getSession().getProfileId();
        VersionController versionController = new VersionController(context.getSharedPreferences(fileName, MODE_PRIVATE));
        WeakReference<RxComapiClient> ref = new WeakReference<>(client);
        profileUpdater = new ProfileUpdater(ref, versionController, executor);
        profileChecker = new ProfileChecker(ref, versionController, executor);
    }

    /**
     * Start managing user profile data. Call this method in onResume method of your profile Activity.
     *
     * @param subscriber Subscriber for callbacks relevant for UI updates.
     */
    public void resume(ProfileManagerSubscriber subscriber) {
        profileUpdater.resume(subscriber);
        profileChecker.resume(subscriber);
    }

    /**
     * Stop managing user profile data. Call this method in onPause method of your profile Activity.
     */
    public void pause() {
        profileUpdater.pause();
        profileChecker.pause();
    }

    /**
     * Applies profile patch for an active session. Changes are applied only for the specified properties leaving other unchanged.
     * This method will
     *
     * @param profile Profile details patch.
     */
    public void patchProfile(ComapiProfile profile) {
        profileUpdater.patchProfile(profile);
    }

    /**
     * Holder for etag value.
     */
    private static class VersionController {

        private final SharedPreferences pref;

        VersionController(SharedPreferences pref) {
            this.pref = pref;
        }

        void updateVersion(String eTag) {
            pref.edit().putString(KEY_ETAG, eTag).apply();
        }

        String getVersion() {
            return pref.getString(KEY_ETAG, null);
        }
    }

    /**
     * Conflict resolution provider.
     */
    public static class ConflictResolver {

        private final ComapiProfile requested;
        private final ComapiProfile actual;
        WeakReference<ProfileUpdater> updRef;
        private WeakReference<VersionController> vCRef;
        String newETag;

        ConflictResolver(ComapiProfile requestedPatch, ComapiResult<ComapiProfile> actualProfileResponse, ProfileUpdater updater, VersionController versionController) {
            this.requested = requestedPatch;
            this.actual = actualProfileResponse.getResult();
            this.newETag = actualProfileResponse.getETag();
            this.updRef = new WeakReference<>(updater);
            this.vCRef = new WeakReference<>(versionController);
        }

        /**
         * Resolve data version conflict.
         *
         * @param resolution Profile data to proceed with a previously failed patch. If null the SDK will cancel this patch request.
         */
        public void resolve(ComapiProfile resolution) {
            ProfileUpdater updater = updRef.get();
            if (updater != null && resolution != null) {
                VersionController vC = vCRef.get();
                if (vC != null) {
                    vC.updateVersion(newETag);
                    updater.patchProfile(resolution);
                }
            }
        }

        /**
         * Requested patch of user profile data.
         *
         * @return Requested patch of user profile data.
         */
        public ComapiProfile getRequestedPatch() {
            return requested;
        }

        /**
         * Current profile data obtained from the services.
         *
         * @return Current profile data obtained from the services.
         */
        public ComapiProfile getCurrentProfile() {
            return actual;
        }
    }

    /**
     * Manages profile patching and updating.
     */
    private static class ProfileUpdater {

        private static final int MAX_CALL_COUNT = 3;
        private final WeakReference<RxComapiClient> ref;
        private VersionController versionController;
        private ObservableExecutor executor;
        private ProfileManagerSubscriber listener;

        private int callCount;

        ProfileUpdater(WeakReference<RxComapiClient> ref, VersionController versionController, ObservableExecutor executor) {
            this.ref = ref;
            this.versionController = versionController;
            this.executor = executor;
            callCount = 0;
        }

        void resume(ProfileManagerSubscriber listener) {
            this.listener = listener;
        }

        void pause() {
            listener = null;
        }

        private Observable<ComapiResult<ComapiProfile>> doPatchProfile(final ComapiProfile requestedProfileChange) {
            callCount += 1;
            final RxComapiClient client = ref.get();
            return client.service().profileWithDefaults().patchMyProfile(requestedProfileChange, versionController.getVersion())
                    .flatMap((Func1<ComapiResult<ComapiProfile>, Observable<ComapiResult<ComapiProfile>>>) result -> {
                        if (result.getCode() == ETAG_NOT_VALID && callCount < MAX_CALL_COUNT) {
                            return client.service().profileWithDefaults().getProfile(client.getSession().getProfileId())
                                    .flatMap((Func1<ComapiResult<ComapiProfile>, Observable<ComapiResult<ComapiProfile>>>) profileQueryResult -> Observable.fromCallable(() -> profileQueryResult)
                                            .subscribeOn(AndroidSchedulers.mainThread())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .doOnNext(comapiProfileComapiResult -> {
                                                if (profileQueryResult.isSuccessful()) {
                                                    final ConflictResolver resolver = new ConflictResolver(requestedProfileChange, profileQueryResult, ProfileUpdater.this, versionController);
                                                    listener.onConflictPatchingData(resolver);
                                                } else {
                                                    listener.profileCallFinishedWithError("Error when obtaining latest profile details.");
                                                }
                                            }))
                                    .observeOn(AndroidSchedulers.mainThread());
                        } else {
                            return Observable.fromCallable(() -> {
                                versionController.updateVersion(result.getETag());
                                return result;
                            });
                        }
                    });
        }

        void patchProfile(final ComapiProfile comapiProfile) {
            listener.profileCallStarted();
            executor.execute(doPatchProfile(comapiProfile)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnCompleted(() -> callCount = 0)
                    .doOnNext(result -> {
                        if (result.isSuccessful()) {
                            listener.profileCallFinishedSuccessfully("Profile updated.");
                        } else {
                            List<ComapiResult<ComapiProfile>.ComapiValidationFailure> failures = result.getValidationFailures();
                            if (failures != null && !failures.isEmpty()) {
                                StringBuilder sb = new StringBuilder();
                                for (ComapiResult<ComapiProfile>.ComapiValidationFailure f : failures) {
                                    if (sb.length() > 0) {
                                        sb.append(" ");
                                    }
                                    sb.append(f.getMessage());
                                }
                                listener.profileCallFinishedWithError(sb.toString());
                            } else {
                                listener.profileCallFinishedWithError("Error patching profile.");
                            }
                        }
                    })
                    .doOnError(throwable -> listener.profileCallFinishedWithError("Error patching profile.")));
        }
    }

    /**
     * Manages profile queries.
     */
    private static class ProfileChecker extends ProfileListener {

        private final WeakReference<RxComapiClient> ref;
        private VersionController versionController;
        private ObservableExecutor executor;
        private ProfileManagerSubscriber listener;

        ProfileChecker(WeakReference<RxComapiClient> ref, VersionController versionController, ObservableExecutor executor) {
            this.ref = ref;
            this.versionController = versionController;
            this.executor = executor;
        }

        void resume(ProfileManagerSubscriber listener) {
            this.listener = listener;
            final RxComapiClient client = ref.get();
            if (client != null) {
                client.addListener(this);
            }
            getProfile();
        }

        void pause() {
            listener = null;
            final RxComapiClient client = ref.get();
            if (client != null) {
                client.removeListener(this);
            }
        }

        void getProfile() {
            listener.profileCallStarted();
            final RxComapiClient client = ref.get();
            if (client != null) {
                executor.execute(client.service().profileWithDefaults().getProfile(client.getSession().getProfileId())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnNext(result -> {
                            if (result.isSuccessful()) {
                                listener.profileCallFinishedSuccessfully("Profile details obtained.");
                                versionController.updateVersion(result.getETag());
                                listener.onProfileDetails(result.getResult());
                            } else {
                                List<ComapiResult<ComapiProfile>.ComapiValidationFailure> failures = result.getValidationFailures();
                                if (failures != null && !failures.isEmpty()) {
                                    StringBuilder sb = new StringBuilder();
                                    for (ComapiResult<ComapiProfile>.ComapiValidationFailure f : failures) {
                                        if (sb.length() > 0) {
                                            sb.append(" ");
                                        }
                                        sb.append(f.getMessage());
                                    }
                                    listener.profileCallFinishedWithError(sb.toString());
                                } else {
                                    listener.profileCallFinishedWithError("Error obtaining profile details.");
                                }
                            }
                        })
                        .doOnError(throwable -> listener.profileCallFinishedWithError("Error obtaining profile details.")));
            }
        }

        @Override
        public void onProfileUpdate(ProfileUpdateEvent event) {
            versionController.updateVersion(event.getETag());
            listener.onProfileDetails(event.getProfileDetails());
        }
    }
}