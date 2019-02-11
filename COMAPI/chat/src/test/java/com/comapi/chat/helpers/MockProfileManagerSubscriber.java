package com.comapi.chat.helpers;

import com.comapi.chat.profile.ProfileManager;
import com.comapi.chat.profile.ProfileManagerSubscriber;
import com.comapi.internal.network.model.profile.ComapiProfile;

/**
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class MockProfileManagerSubscriber implements ProfileManagerSubscriber {

    final static int READY = 0;
    final static int STARTED = 1;
    final static int FINISHED = 2;
    final static int NEEDS_RESOLVING = 3;
    final static int ERROR = 4;

    final static int NEW_PROFILE_EVENT = 5;

    ComapiProfile data = null;

    ProfileManager.ConflictResolver resolver;


    int state = 0;

    @Override
    public void profileCallStarted() {
        state = STARTED;
    }

    @Override
    public void profileCallFinishedSuccessfully(String message) {
        state = FINISHED;
    }

    @Override
    public void profileCallFinishedWithError(String message) {
        state = ERROR;
    }

    @Override
    public void onProfileDetails(ComapiProfile profile) {
        state = NEW_PROFILE_EVENT;
        data = profile;
    }

    @Override
    public void onConflictPatchingData(ProfileManager.ConflictResolver resolver) {
        state = NEEDS_RESOLVING;
        this.resolver = resolver;
        //resolver.resolve(resolver.getRequestedPatch());
    }

    public ComapiProfile getData() {
        return data;
    }

    public int getState() {
        return state;
    }

    public ProfileManager.ConflictResolver getResolver() {
        return resolver;
    }

    public void reset() {
        state = 0;
    }
}