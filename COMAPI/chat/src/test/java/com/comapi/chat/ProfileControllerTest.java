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

package com.comapi.chat;

import android.content.SharedPreferences;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.comapi.APIConfig;
import com.comapi.Callback;
import com.comapi.ComapiAuthenticator;
import com.comapi.MessagingListener;
import com.comapi.ProfileListener;
import com.comapi.chat.helpers.ChatTestConst;
import com.comapi.chat.helpers.FileResHelper;
import com.comapi.chat.helpers.MockCallback;
import com.comapi.chat.helpers.MockComapiClient;
import com.comapi.chat.helpers.MockFoundationFactory;
import com.comapi.chat.helpers.MockProfileManagerSubscriber;
import com.comapi.chat.helpers.MockResult;
import com.comapi.chat.helpers.TestChatStore;
import com.comapi.chat.model.ChatStore;
import com.comapi.chat.profile.ProfileManager;
import com.comapi.chat.helpers.DataTestHelper;
import com.comapi.chat.helpers.ResponseTestHelper;
import com.comapi.internal.CallbackAdapter;
import com.comapi.internal.Parser;
import com.comapi.internal.network.AuthClient;
import com.comapi.internal.network.ChallengeOptions;
import com.comapi.internal.network.ComapiResult;
import com.comapi.internal.network.model.events.ProfileUpdateEvent;
import com.comapi.internal.network.model.messaging.MessagesQueryResponse;
import com.comapi.internal.network.model.profile.ComapiProfile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import rx.Observable;
import rx.Subscriber;

import static android.content.Context.MODE_PRIVATE;
import static com.comapi.chat.helpers.ChatTestConst.TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = "chat/src/main/AndroidManifest.xml", sdk = Build.VERSION_CODES.M, constants = BuildConfig.class, packageName = "com.comapi.chat")
public class ProfileControllerTest {

    private MockWebServer server;

    private ComapiChatClient client;
    private final MockCallback<ComapiChatClient> callback = new MockCallback<>();
    private MockComapiClient mockedComapiClient;

    @Before
    public void setUpChat() throws Exception {

        server = new MockWebServer();
        server.start();

        APIConfig apiConfig = new APIConfig().service("http://localhost:59273/").socket("ws://10.0.0.0");

        TestChatStore store = new TestChatStore();

        StoreFactory<ChatStore> factory = new StoreFactory<ChatStore>() {
            @Override
            public void build(StoreCallback<ChatStore> callback) {
                callback.created(store);
            }
        };

        MockFoundationFactory foundationFactory = new MockFoundationFactory(ChatConfig::buildComapiConfig);

        ChatConfig chatConfig = new ChatConfig()
                .setFoundationFactory(foundationFactory)
                .apiSpaceId("ApiSpaceId")
                .internalConfig(new InternalConfig().limitConversationSynced(1).limitEventQueries(1).limitEventsPerQuery(10).limitMessagesPerPage(1).limitPartDataSize(1000))
                .authenticator(new ComapiAuthenticator() {
                    @Override
                    public void onAuthenticationChallenge(AuthClient authClient, ChallengeOptions challengeOptions) {
                        authClient.authenticateWithToken(TOKEN);
                    }
                })
                .apiConfiguration(apiConfig)
                .store(factory)
                .observableExecutor(new ObservableExecutor() {
                    @Override
                    public <T> void execute(Observable<T> obs) {
                        obs.toBlocking().firstOrDefault(null);
                    }
                })
                .overrideCallbackAdapter(new CallbackAdapter() {
                    @Override
                    public <T> void adapt(@NonNull Observable<T> subscriber, @Nullable Callback<T> callback) {
                        subscriber.subscribe(new Subscriber<T>() {

                            @Override
                            public void onCompleted() {

                            }

                            @Override
                            public void onError(Throwable e) {
                                if (callback != null) {
                                    callback.error(e);
                                }
                            }

                            @Override
                            public void onNext(T result) {
                                if (callback != null) {
                                    callback.success(result);
                                }
                            }
                        });
                    }
                });

        client = ComapiChat.initialise(RuntimeEnvironment.application, chatConfig).toBlocking().first();
        mockedComapiClient = foundationFactory.getMockedClient();

        assertNotNull(client);
        assertNotNull(mockedComapiClient);

    }

    @Test
    public void patchProfile() {

        Map<String, Object> response = new HashMap<>();
        response.put("firstName", "X");
        mockedComapiClient.addMockedResult(new MockResult<>(new ComapiProfile(response), true, ChatTestConst.ETAG, 200));
        mockedComapiClient.addMockedResult(new MockResult<>(new ComapiProfile(response), true, ChatTestConst.ETAG, 200));

        ProfileManager profileManager = new ProfileManager(RuntimeEnvironment.application, mockedComapiClient, new ObservableExecutor() {

            @Override
            public <T> void execute(final Observable<T> obs) {
                obs.toBlocking().subscribe(new Subscriber<T>() {
                    @Override
                    public void onCompleted() {
                        // Completed, will unsubscribe automatically
                    }

                    @Override
                    public void onError(Throwable e) {
                        // Report errors in doOnError
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public void onNext(T t) {
                        assertTrue(( ((MockResult<ComapiProfile>) t).isSuccessful()));
                        assertTrue(( ((MockResult<ComapiProfile>) t).getResult().getFirstName().equals("X")));
                    }
                });
            }
        });


        MockProfileManagerSubscriber sub = new MockProfileManagerSubscriber();
        profileManager.resume(sub);

        ComapiProfile p = new ComapiProfile();
        p.setFirstName("a");
        p.add("b", "c");

        profileManager.patchProfile(p);

        assertEquals(2, sub.getState());

        profileManager.pause();
    }

    @Test
    public void test_profileEvent() throws IOException {

        Map<String, Object> response = new HashMap<>();
        response.put("firstName", "X");
        mockedComapiClient.addMockedResult(new MockResult<>(new ComapiProfile(response), true, ChatTestConst.ETAG, 200));

        ProfileManager profileManager = new ProfileManager(RuntimeEnvironment.application, mockedComapiClient, new ObservableExecutor() {

            @Override
            public <T> void execute(final Observable<T> obs) {
                obs.toBlocking().subscribe(new Subscriber<T>() {
                    @Override
                    public void onCompleted() {
                        // Completed, will unsubscribe automatically
                    }

                    @Override
                    public void onError(Throwable e) {
                        // Report errors in doOnError
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public void onNext(T t) {
                        assertTrue(( ((MockResult<ComapiProfile>) t).isSuccessful()));
                        assertTrue(( ((MockResult<ComapiProfile>) t).getResult().getFirstName().equals("X")));
                    }
                });
            }
        });

        MockProfileManagerSubscriber sub = new MockProfileManagerSubscriber();
        profileManager.resume(sub);

        ComapiProfile p = new ComapiProfile();
        p.setFirstName("a");
        p.add("b", "c");

        Parser parser = new Parser();
        String json = FileResHelper.readFromFile(this, "profile_update.json");
        ProfileUpdateEvent event1 = parser.parse(json, ProfileUpdateEvent.class);
        mockedComapiClient.dispatchTestEvent(event1);

        assertEquals(5, sub.getState());

        String fileName = "profile.comapi" + client.getSession().getProfileId();
        SharedPreferences pref = RuntimeEnvironment.application.getSharedPreferences(fileName, MODE_PRIVATE);
        assertNotNull(pref.getString("eTag", null));
        assertEquals(5, sub.getState());

        profileManager.pause();
    }

    @Test
    public void patchProfile_resolve() {

        Map<String, Object> response = new HashMap<>();
        response.put("firstName", "X");
        mockedComapiClient.addMockedResult(new MockResult<>(new ComapiProfile(response), true, ChatTestConst.ETAG, 200));

        MockResponse response2 = new MockResponse();
        response2.setResponseCode(412);
        response2.setHttp2ErrorCode(412);
        mockedComapiClient.addMockedResult(new MockResult<>(null, false, ChatTestConst.ETAG, 412));

        mockedComapiClient.addMockedResult(new MockResult<>(new ComapiProfile(response), true, ChatTestConst.ETAG, 200));

        mockedComapiClient.addMockedResult(new MockResult<>(new ComapiProfile(response), true, ChatTestConst.ETAG, 200));

        ProfileManager profileManager = new ProfileManager(RuntimeEnvironment.application, mockedComapiClient, new ObservableExecutor() {

            @Override
            public <T> void execute(final Observable<T> obs) {
                obs.toBlocking().subscribe(new Subscriber<T>() {
                    @Override
                    public void onCompleted() {
                        // Completed, will unsubscribe automatically
                    }

                    @Override
                    public void onError(Throwable e) {
                        // Report errors in doOnError
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public void onNext(T t) {
                        assertTrue(( ((MockResult<ComapiProfile>) t).isSuccessful()));
                        assertTrue(( ((MockResult<ComapiProfile>) t).getResult().getFirstName().equals("X")));
                    }
                });
            }
        });

        MockProfileManagerSubscriber sub = new MockProfileManagerSubscriber();
        profileManager.resume(sub);

        ComapiProfile p = new ComapiProfile();
        p.setFirstName("a");
        p.add("b", "c");
        profileManager.patchProfile(p);

        assertEquals(2, sub.getState());

        sub.reset();

        ProfileManager.ConflictResolver resolver = sub.getResolver();

        assertNotNull(resolver.getRequestedPatch());
        assertNotNull(resolver.getCurrentProfile());
        resolver.resolve(resolver.getRequestedPatch());
        assertEquals(2, sub.getState());

        profileManager.pause();
    }


    @Test
    public void patchProfile_getProfileError() {

        MockResponse response = new MockResponse();
        response.setResponseCode(400);
        response.setHttp2ErrorCode(400);
        response.setBody("{\"validationFailures\":[{\"paramName\":\"someParameter\",\"message\":\"details\"}]}");

        mockedComapiClient.addMockedResult(new MockResult<>(null, false, ChatTestConst.ETAG, 400, "{\"validationFailures\":[{\"paramName\":\"someParameter\",\"message\":\"details\"}]}"));

        ProfileManager profileManager = new ProfileManager(RuntimeEnvironment.application, mockedComapiClient, new ObservableExecutor() {

            @Override
            public <T> void execute(final Observable<T> obs) {
                obs.toBlocking().subscribe(new Subscriber<T>() {
                    @Override
                    public void onCompleted() {
                        // Completed, will unsubscribe automatically
                    }

                    @Override
                    public void onError(Throwable e) {
                        // Report errors in doOnError
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public void onNext(T t) {
                        assertFalse(( ((MockResult<ComapiProfile>) t).isSuccessful()));
                    }
                });
            }
        });

        MockProfileManagerSubscriber sub = new MockProfileManagerSubscriber();
        profileManager.resume(sub);

        assertEquals(4, sub.getState());

        profileManager.pause();
    }

    @Test
    public void patchProfile_patchProfileError412() {

        Map<String, Object> response = new HashMap<>();
        response.put("firstName", "X");
        mockedComapiClient.addMockedResult(new MockResult<>(new ComapiProfile(response), true, ChatTestConst.ETAG, 200));

        MockResponse response2 = new MockResponse();
        response2.setResponseCode(412);
        response2.setHttp2ErrorCode(412);
        mockedComapiClient.addMockedResult(new MockResult<>(null, false, ChatTestConst.ETAG, 412));

        mockedComapiClient.addMockedResult(new MockResult<>(new ComapiProfile(response), true, ChatTestConst.ETAG, 200));

        mockedComapiClient.addMockedResult(new MockResult<>(new ComapiProfile(response), true, ChatTestConst.ETAG, 200));

        ProfileManager profileManager = new ProfileManager(RuntimeEnvironment.application, mockedComapiClient, new ObservableExecutor() {

            @Override
            public <T> void execute(final Observable<T> obs) {
                obs.toBlocking().subscribe(new Subscriber<T>() {
                    @Override
                    public void onCompleted() {
                        // Completed, will unsubscribe automatically
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onNext(T t) {

                    }
                });
            }
        });

        MockProfileManagerSubscriber sub = new MockProfileManagerSubscriber();
        profileManager.resume(sub);

        ComapiProfile p = new ComapiProfile();
        p.setFirstName("a");
        p.add("b", "c");

        profileManager.patchProfile(p);

        assertEquals(2, sub.getState());

        profileManager.pause();
    }

    @Test
    public void patchProfile_patchProfileError400() {

        Map<String, Object> response = new HashMap<>();
        response.put("firstName", "X");
        mockedComapiClient.addMockedResult(new MockResult<>(new ComapiProfile(response), true, ChatTestConst.ETAG, 200));
        mockedComapiClient.addMockedResult(new MockResult<>(null, false, ChatTestConst.ETAG, 400, "{\"validationFailures\":[{\"paramName\":\"someParameter\",\"message\":\"details\"}]}"));

        ProfileManager profileManager = new ProfileManager(RuntimeEnvironment.application, mockedComapiClient, new ObservableExecutor() {

            @Override
            public <T> void execute(final Observable<T> obs) {
                obs.toBlocking().subscribe(new Subscriber<T>() {
                    @Override
                    public void onCompleted() {
                        // Completed, will unsubscribe automatically
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(T t) {

                    }
                });
            }
        });

        MockProfileManagerSubscriber sub = new MockProfileManagerSubscriber();
        profileManager.resume(sub);

        ComapiProfile p = new ComapiProfile();
        p.setFirstName("a");
        p.add("b", "c");

        profileManager.patchProfile(p);

        assertEquals(4, sub.getState());

        profileManager.pause();
    }

    @After
    public void tearDown() throws Exception {
        DataTestHelper.clearDeviceData();
        DataTestHelper.clearSessionData();
        server.shutdown();
        mockedComapiClient.clearResults();
    }
}