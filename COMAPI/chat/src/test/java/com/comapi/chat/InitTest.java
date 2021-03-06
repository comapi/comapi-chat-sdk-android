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

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.comapi.APIConfig;
import com.comapi.Callback;
import com.comapi.ComapiAuthenticator;
import com.comapi.chat.helpers.DataTestHelper;
import com.comapi.chat.helpers.MockCallback;
import com.comapi.chat.helpers.MockComapiClient;
import com.comapi.chat.helpers.MockFoundationFactory;
import com.comapi.chat.helpers.TestChatStore;
import com.comapi.chat.model.ChatStore;
import com.comapi.internal.CallbackAdapter;
import com.comapi.internal.network.AuthClient;
import com.comapi.internal.network.ChallengeOptions;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import rx.Observable;
import rx.Subscriber;

import static com.comapi.chat.helpers.ChatTestConst.TOKEN;
import static org.junit.Assert.assertNotNull;


@RunWith(RobolectricTestRunner.class)
@Config(manifest = "chat/src/main/AndroidManifest.xml", sdk = Build.VERSION_CODES.M, constants = BuildConfig.class, packageName = "com.comapi.chat")
public class InitTest {

    private ComapiChatClient client;
    private final MockCallback<ComapiChatClient> callback = new MockCallback<>();
    private MockComapiClient mockedComapiClient;

    @Test
    public void test_initialise() {

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
    public void test_initialiseCallback() {

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
                        obs.toBlocking().first();
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

        ComapiChat.initialise(RuntimeEnvironment.application, chatConfig, callback);
        client = callback.getResult();
        mockedComapiClient = foundationFactory.getMockedClient();

        assertNotNull(client);
        assertNotNull(mockedComapiClient);
    }

    @Test
    public void test_initialiseShared() {

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

        client = ComapiChat.initialiseShared(RuntimeEnvironment.application, chatConfig).toBlocking().first();
        mockedComapiClient = foundationFactory.getMockedClient();

        assertNotNull(client);
        assertNotNull(mockedComapiClient);
        assertNotNull(ComapiChat.getShared());
    }

    @Test
    public void test_initialiseSharedCallback() {

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

        ComapiChat.initialiseShared(RuntimeEnvironment.application, chatConfig, callback);
        client = callback.getResult();
        mockedComapiClient = foundationFactory.getMockedClient();

        assertNotNull(client);
        assertNotNull(mockedComapiClient);
        assertNotNull(ComapiChat.getShared());
    }

    @Test
    public void test_getShared() {

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

        ComapiChat.initialiseShared(RuntimeEnvironment.application, chatConfig, callback);
        client = callback.getResult();
        mockedComapiClient = foundationFactory.getMockedClient();

        assertNotNull(ComapiChat.getShared());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void test_coverPrivateConstructor() throws IllegalAccessException, InvocationTargetException, InstantiationException {

        Constructor<ComapiChat> constructor = (Constructor<ComapiChat>) ComapiChat.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ComapiChat obj = constructor.newInstance();
        assertNotNull(obj);
    }

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            client.close(RuntimeEnvironment.application);
        }
        DataTestHelper.clearDeviceData();
        DataTestHelper.clearSessionData();
        callback.reset();
        ComapiChat.reset();
    }
}