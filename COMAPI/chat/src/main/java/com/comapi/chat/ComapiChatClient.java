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

import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.comapi.ClientHelper;
import com.comapi.MessagingListener;
import com.comapi.RxComapiClient;
import com.comapi.Session;
import com.comapi.chat.database.Database;
import com.comapi.chat.internal.AttachmentController;
import com.comapi.chat.internal.MissingEventsTracker;
import com.comapi.chat.listeners.ParticipantsListener;
import com.comapi.chat.listeners.ProfileListener;
import com.comapi.chat.listeners.TypingListener;
import com.comapi.chat.model.ModelAdapter;
import com.comapi.chat.profile.ProfileManager;
import com.comapi.internal.CallbackAdapter;
import com.comapi.internal.lifecycle.LifecycleListener;
import com.comapi.internal.log.Logger;
import com.comapi.internal.network.model.events.ProfileUpdateEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantAddedEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantRemovedEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantTypingEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantTypingOffEvent;
import com.comapi.internal.network.model.events.conversation.ParticipantUpdatedEvent;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;

/**
 * Client implementation for chat layer SDK. Handles initialisation and stores all internal objects.
 *
 * @author Marcin Swierczek
 * @since 1.0.0
 */
public class ComapiChatClient {

    private final static String VERSION = "1.1.0";

    private final RxComapiClient client;

    @SuppressWarnings("FieldCanBeLocal")
    private final EventsHandler eventsHandler;

    @SuppressWarnings("FieldCanBeLocal")
    private final ChatController controller;

    private final ChatServiceAccessor serviceAccessor;

    private final RxChatServiceAccessor rxServiceAccessor;

    private final Map<ParticipantsListener, MessagingListener> participantsListeners;
    private final Map<ProfileListener, com.comapi.ProfileListener> profileListeners;
    private final Map<TypingListener, MessagingListener> typingListeners;

    private final Database db;

    /**
     * Recommended constructor.
     *
     * @param app             Application instance.
     * @param client          Comapi Foundation SDK client.
     * @param chatConfig      SDK configuration.
     * @param eventsHandler   Socket events handler.
     * @param callbackAdapter Adapts Observables to callback APIs.
     */
    protected ComapiChatClient(Application app, final RxComapiClient client, final ChatConfig chatConfig, final EventsHandler eventsHandler, CallbackAdapter callbackAdapter) {
        this.client = client;
        this.eventsHandler = eventsHandler;
        final Logger log = ClientHelper.getLogger(client).clone("Chat_" + VERSION);
        ModelAdapter modelAdapter = new ModelAdapter();
        db = Database.getInstance(app, false, log);
        PersistenceController persistenceController = new PersistenceController(db, modelAdapter, chatConfig.getStoreFactory(), log);
        final InternalConfig internal = chatConfig.getInternalConfig();
        controller = new ChatController(client, persistenceController, new AttachmentController(log, internal.getMaxPartDataSize()), internal, chatConfig.getObservableExecutor(), modelAdapter, log);
        rxServiceAccessor = new RxChatServiceAccessor(modelAdapter, client, controller);
        serviceAccessor = new ChatServiceAccessor(callbackAdapter, rxServiceAccessor);
        eventsHandler.init(persistenceController, controller, new MissingEventsTracker(), chatConfig);

        client.addListener(eventsHandler.getMessagingListenerAdapter());
        client.addListener(eventsHandler.getStateListenerAdapter());

        participantsListeners = new ConcurrentHashMap<>();
        profileListeners = new ConcurrentHashMap<>();
        typingListeners = new ConcurrentHashMap<>();
        addListener(chatConfig.getParticipantsListener());
        addListener(chatConfig.getProfileListener());
        addListener(chatConfig.getTypingListener());

        log.i("Comapi Chat SDK " + VERSION + " client " + this.hashCode() + " initialising on " + (Thread.currentThread() == Looper.getMainLooper().getThread() ? "main thread." : "background thread."));
        log.d(internal.toString());
    }

    /**
     * Gets the active session data.
     *
     * @return Active session data.
     */
    public Session getSession() {
        return client.getSession();
    }

    /**
     * Access to reactive Comapi service APIs.
     *
     * @return Comapi reactive service APIs.
     */
    public RxChatServiceAccessor rxService() {
        return rxServiceAccessor;
    }

    /**
     * Access to Comapi service APIs.
     *
     * @return Comapi service APIs.
     */
    public ChatServiceAccessor service() {
        return serviceAccessor;
    }

    /**
     * Creates listener for Application visibility.
     *
     * @param ref Weak reference to comapi client used to trigger synchronisation in response to app being foregrounded.
     * @return Listener to app lifecycle changes.
     */
    LifecycleListener createLifecycleListener(final WeakReference<ComapiChatClient> ref) {

        return new LifecycleListener() {

            /**
             * App foregrounded.
             *
             * @param context Application context
             */
            public void onForegrounded(Context context) {
                ComapiChatClient client = ref.get();
                if (client != null) {
                    client.service().messaging().synchroniseStore(null);
                }
            }

            /**
             * App backgrounded.
             *
             * @param context Application context
             */
            public void onBackgrounded(Context context) {

            }
        };
    }

    /**
     * Gets the internal state of the SDK. Possible values in com.comapi.GlobalState.
     *
     * @return State of the ComapiImpl SDK. Compare with values in com.comapi.GlobalState.
     */
    public int getState() {
        return client.getState();
    }

    /**
     * Gets the content of internal log files merged into provided file.
     *
     * @param file File to merge internal logs into.
     * @return Observable emitting the same file but this time containing all the internal logs merged into.
     */
    public Observable<File> copyLogs(@NonNull File file) {
        return client.copyLogs(file);
    }

    /**
     * Returns the content of internal log files in a single String. For large limits of internal files consider using {@link ComapiChatClient#copyLogs(File)} and loading the content line by line.
     *
     * @return Observable emitting internal log files content as a single string.
     * @deprecated Use safer version - {@link this#copyLogs(File)} instead.
     */
    @Deprecated
    public Observable<String> getLogs() {
        return client.getLogs();
    }

    /**
     * Method to close the client state, won't be usable anymore. Useful e.g. for unit testing.
     *
     * @param context Context.
     */
    public void close(Context context) {
        client.clean(context.getApplicationContext());
        if (db != null) {
            db.closeDatabase();
        }
    }

    /**
     * Registers listener for changes in participant list in conversations. Delivers participant added, updated and removed events.
     *
     * @param participantsListener Listener for changes in participant list in conversations.
     */
    public void addListener(final ParticipantsListener participantsListener) {
        if (participantsListener != null) {
            MessagingListener messagingListener = new MessagingListener() {

                @Override
                public void onParticipantAdded(ParticipantAddedEvent event) {
                    participantsListener.onParticipantAdded(event);
                }

                @Override
                public void onParticipantUpdated(ParticipantUpdatedEvent event) {
                    participantsListener.onParticipantUpdated(event);
                }

                @Override
                public void onParticipantRemoved(ParticipantRemovedEvent event) {
                    participantsListener.onParticipantRemoved(event);
                }
            };
            participantsListeners.put(participantsListener, messagingListener);
            client.addListener(messagingListener);
        }
    }

    /**
     * Removes listener for changes in participant list in conversations.
     *
     * @param participantsListener Listener for changes in participant list in conversations.
     */
    public void removeListener(final ParticipantsListener participantsListener) {
        MessagingListener messagingListener = participantsListeners.get(participantsListener);
        if (messagingListener != null) {
            client.removeListener(messagingListener);
            participantsListeners.remove(participantsListener);
        }
    }

    /**
     * Registers listener for changes in profile details.
     *
     * @param profileListener Listener for changes in in profile details.
     */
    public void addListener(final ProfileListener profileListener) {
        if (profileListener != null) {
            com.comapi.ProfileListener foundationListener = new com.comapi.ProfileListener() {
                @Override
                public void onProfileUpdate(ProfileUpdateEvent event) {
                    profileListener.onProfileUpdate(event);
                }
            };
            profileListeners.put(profileListener, foundationListener);
            client.addListener(foundationListener);
        }
    }

    /**
     * Removes listener for changes in profile details.
     *
     * @param profileListener Listener for changes in in profile details.
     */
    public void removeListener(final ProfileListener profileListener) {
        com.comapi.ProfileListener foundationListener = profileListeners.get(profileListener);
        if (foundationListener != null) {
            client.removeListener(foundationListener);
            profileListeners.remove(profileListener);
        }
    }

    /**
     * Registers listener for user is typing/finished typing events.
     *
     * @param typingListener Listener for user is typing/finished typing events.
     */
    public void addListener(final TypingListener typingListener) {
        if (typingListener != null) {
            MessagingListener messagingListener = new MessagingListener() {

                @Override
                public void onParticipantIsTyping(ParticipantTypingEvent event) {
                    typingListener.participantTyping(event.getConversationId(), event.getProfileId(), true);
                }

                @Override
                public void onParticipantTypingOff(ParticipantTypingOffEvent event) {
                    typingListener.participantTyping(event.getConversationId(), event.getProfileId(), false);
                }
            };
            typingListeners.put(typingListener, messagingListener);
            client.addListener(messagingListener);
        }
    }

    /**
     * Removes listener for user is typing/finished typing events.
     *
     * @param typingListener Listener for user is typing/finished typing events.
     */
    public void removeListener(final TypingListener typingListener) {
        MessagingListener messagingListener = typingListeners.get(typingListener);
        if (messagingListener != null) {
            client.removeListener(messagingListener);
            typingListeners.remove(typingListener);
        }
    }

    /**
     * Create ProfileManager to manage profile data.
     *
     * @param context Application context.
     * @return ProfileManager to manage profile data.
     */
    public ProfileManager createProfileManager(@NonNull Context context) {
        return new ProfileManager(context, client, new ObservableExecutor() {

            @Override
            public <T> void execute(final Observable<T> obs) {
                obs.subscribe(new Subscriber<T>() {
                            @Override
                            public void onCompleted() {
                                // Completed, will unsubscribe automatically
                            }

                            @Override
                            public void onError(Throwable e) {
                                // Report errors in doOnError
                            }

                            @Override
                            public void onNext(T t) {
                                // Ignore result
                            }
                        });
            }
        });
    }

    Logger getLogger(String tagSuffix) {
        return ClientHelper.getLogger(client).clone(tagSuffix);
    }

    public static class ExtHelper {

        public static Logger getLogger(@NonNull final ComapiChatClient client, final String tagSuffix) {
            return client.getLogger(tagSuffix);
        }
    }
}