package co.chatsdk.firebase;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DatabaseReference;

import java.util.ArrayList;
import java.util.HashMap;

import co.chatsdk.core.dao.DaoCore;
import co.chatsdk.core.dao.Keys;
import co.chatsdk.core.dao.Thread;
import co.chatsdk.core.dao.User;
import co.chatsdk.core.events.NetworkEvent;
import co.chatsdk.core.handlers.EventHandler;
import co.chatsdk.core.hook.HookEvent;
import co.chatsdk.core.interfaces.ThreadType;
import co.chatsdk.core.session.ChatSDK;
import co.chatsdk.core.session.StorageManager;
import co.chatsdk.core.types.ConnectionType;
import co.chatsdk.core.utils.CrashReportingCompletableObserver;
import co.chatsdk.core.utils.CrashReportingObserver;
import co.chatsdk.core.utils.DisposableList;
import co.chatsdk.firebase.wrappers.ThreadWrapper;
import co.chatsdk.firebase.wrappers.UserWrapper;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

/**
 * Created by benjaminsmiley-andrews on 10/05/2017.
 */

public class FirebaseEventHandler implements EventHandler {

    final private PublishSubject<NetworkEvent> eventSource = PublishSubject.create();

    private static FirebaseEventHandler instance;
    boolean isOn = false;
    private DisposableList disposableList = new DisposableList();

    public static FirebaseEventHandler shared() {
        if (instance == null) {
            instance = new FirebaseEventHandler();
        }
        return instance;
    }

    public void currentUserOn(final String entityID){

        if(isOn) {
            return;
        }
        isOn = true;

        final User user = DaoCore.fetchEntityWithEntityID(User.class, entityID);

        if(ChatSDK.hook() != null) {
            HashMap<String, Object> data = new HashMap<>();
            data.put(HookEvent.User, user);
            ChatSDK.hook().executeHook(HookEvent.UserOn, data).subscribe(new CrashReportingCompletableObserver());;
        }

        // Remove all users from public threads
        // These may not have been cleared down when we exited so clear them down and
        // start again

        // #575 We avoid removing the user from Public threads, as we are controlling it in RoomActivity manually
        //        for(Thread thread : ChatSDK.thread().getThreads(ThreadType.Public)) {
        //            for(User u : thread.getUsers()) {
        //                thread.removeUser(u);
        //            }
        //        }

        final DatabaseReference threadsRef = FirebasePaths.userThreadsRef(entityID);
        ChildEventListener threadsListener = threadsRef.addChildEventListener(new FirebaseEventListener().onChildAdded((snapshot, s, hasValue) -> {
            if(hasValue) {
                final ThreadWrapper thread = new ThreadWrapper(snapshot.getKey());

                thread.getModel().addUser(user);

                // Starting to listen to thread changes.
                thread.on().doOnNext(thread14 -> eventSource.onNext(NetworkEvent.threadDetailsUpdated(thread14))).subscribe(new CrashReportingObserver<>(disposableList));
                thread.lastMessageOn().doOnNext(thread13 -> eventSource.onNext(NetworkEvent.threadLastMessageUpdated(thread13))).subscribe(new CrashReportingObserver<>(disposableList));
                thread.messagesOn().doOnNext(message -> eventSource.onNext(NetworkEvent.messageAdded(message.getThread(), message))).subscribe(new CrashReportingObserver<>(disposableList));
                thread.messageRemovedOn().doOnNext(message -> eventSource.onNext(NetworkEvent.messageRemoved(message.getThread(), message))).subscribe(new CrashReportingObserver<>(disposableList));
                thread.usersOn().doOnNext(user12 -> eventSource.onNext(NetworkEvent.threadUsersChanged(thread.getModel(), user12))).subscribe(new CrashReportingObserver<>(disposableList));
                thread.metaOn().doOnNext(thread1 -> eventSource.onNext(NetworkEvent.threadMetaUpdated(thread.getModel()))).subscribe(new CrashReportingObserver<>(disposableList));

                eventSource.onNext(NetworkEvent.threadAdded(thread.getModel()));

            }
        }).onChildRemoved((snapshot, hasValue) -> {
            if (hasValue) {
                ThreadWrapper thread = new ThreadWrapper(snapshot.getKey());
                thread.off();
                eventSource.onNext(NetworkEvent.threadRemoved(thread.getModel()));
            }
        }));
        FirebaseReferenceManager.shared().addRef(threadsRef, threadsListener);

        DatabaseReference publicThreadsRef = FirebasePaths.publicThreadsRef();
        ChildEventListener publicThreadsListener = publicThreadsRef.addChildEventListener(new FirebaseEventListener().onChildAdded((snapshot, s, hasValue) -> {
            final ThreadWrapper thread = new ThreadWrapper(snapshot.getKey());

            // Starting to listen to thread changes.
            thread.on().doOnNext(thread12 -> eventSource.onNext(NetworkEvent.threadDetailsUpdated(thread12))).subscribe(new CrashReportingObserver<>(disposableList));
            thread.lastMessageOn().doOnNext(thread15 -> eventSource.onNext(NetworkEvent.threadLastMessageUpdated(thread15))).subscribe(new CrashReportingObserver<>(disposableList));
            thread.messagesOn().doOnNext(message -> eventSource.onNext(NetworkEvent.messageAdded(message.getThread(), message))).subscribe(new CrashReportingObserver<>(disposableList));
            thread.messageRemovedOn().doOnNext(message -> eventSource.onNext(NetworkEvent.messageRemoved(message.getThread(), message))).subscribe(new CrashReportingObserver<>(disposableList));
            thread.usersOn().doOnNext(user1 -> eventSource.onNext(NetworkEvent.threadUsersChanged(thread.getModel(), user1))).subscribe(new CrashReportingObserver<>(disposableList));

            // #577 Listening to meta for public groups...
            thread.metaOn().doOnNext(thread1 -> eventSource.onNext(NetworkEvent.threadMetaUpdated(thread1))).subscribe(new CrashReportingObserver<>(disposableList));

            eventSource.onNext(NetworkEvent.threadAdded(thread.getModel()));
        }).onChildRemoved((snapshot, hasValue) -> {
            ThreadWrapper thread = new ThreadWrapper(snapshot.getKey());
            thread.off();
            eventSource.onNext(NetworkEvent.threadRemoved(thread.getModel()));
        }));
        FirebaseReferenceManager.shared().addRef(publicThreadsRef, publicThreadsListener);

        if (ChatSDK.push() != null) {
            ChatSDK.push().subscribeToPushChannel(user.getPushChannel());
        }

        // TODO: Check this
        DatabaseReference followersRef = FirebasePaths.userFollowersRef(entityID);
        ChildEventListener followersListener = followersRef.addChildEventListener(new FirebaseEventListener().onChildAdded((snapshot, s, hasValue) -> {

            //TODO: Implement this

            //FollowerLink follower = (FollowerLink) FirebaseAdapter.objectFromSnapshot(snapshot);
//
//
//
//                UserWrapper wrapper = UserWrapper.initWithModel(follower.getUser());
//                wrapper.once();
//                wrapper.metaOn();

            eventSource.onNext(NetworkEvent.followerAdded());

        }).onChildRemoved((snapshot, hasValue) -> {

//                FollowerLink follower = (FollowerLink) FirebaseAdapter.objectFromSnapshot(snapshot);
//                DaoCore.deleteEntity(follower);

            eventSource.onNext(NetworkEvent.followerRemoved());

        }));
        FirebaseReferenceManager.shared().addRef(followersRef, followersListener);

        DatabaseReference followingRef = FirebasePaths.userFollowingRef(entityID);
        ChildEventListener followingListener = followingRef.addChildEventListener(new FirebaseEventListener().onChildAdded((snapshot, s, hasValue) -> {

            // TODO: Implement this
//                FollowerLink follower = (FollowerLink) FirebaseAdapter.objectFromSnapshot(snapshot);
//
//                UserWrapper wrapper = UserWrapper.initWithModel(follower.getUser());
//                wrapper.once();
//                wrapper.metaOn();

            eventSource.onNext(NetworkEvent.followingAdded());

        }).onChildRemoved((snapshot, hasValue) -> {

//                FollowerLink follower = (FollowerLink) FirebaseAdapter.objectFromSnapshot(snapshot);
//                DaoCore.deleteEntity(follower);

            eventSource.onNext(NetworkEvent.followingRemoved());
        }));
        FirebaseReferenceManager.shared().addRef(followersRef, followingListener);

        DatabaseReference ref = FirebasePaths.userContactsRef(ChatSDK.currentUserID());

        ref.addChildEventListener(new FirebaseEventListener().onChildAdded((snapshot, s, hasValue) -> {
            if (hasValue) {
                User contact = StorageManager.shared().fetchOrCreateEntityWithEntityID(User.class, snapshot.getKey());
                Object value = snapshot.getValue();
                if (value instanceof HashMap) {
                    Object type = ((HashMap) value).get(Keys.Type);
                    if (type instanceof Long) {
                        ConnectionType connectionType = ConnectionType.values()[((Long) type).intValue()];
                        ChatSDK.contact().addContactLocal(contact, connectionType);
                        eventSource.onNext(NetworkEvent.contactAdded(contact));
                    }
                }
            }
        }));

        ref.addChildEventListener(new FirebaseEventListener().onChildRemoved((snapshot, hasValue) -> {
            if (hasValue) {
                User contact = StorageManager.shared().fetchOrCreateEntityWithEntityID(User.class, snapshot.getKey());
                Object value = snapshot.getValue();
                if (value instanceof HashMap) {
                    Object type = ((HashMap) value).get(Keys.Type);
                    if (type instanceof Long) {
                        ConnectionType connectionType = ConnectionType.values()[((Long) type).intValue()];
                        ChatSDK.contact().deleteContactLocal(contact, connectionType);
                        eventSource.onNext(NetworkEvent.contactDeleted(contact));
                    }
                }
            }
        }));

        contactsMetaOn().subscribe(new CrashReportingCompletableObserver(disposableList));
    }

    private Completable contactsMetaOn () {
        return Completable.create(e -> {
            ArrayList<Completable> completables = new ArrayList<>();
            for (User contact : ChatSDK.contact().contacts()) {
                completables.add(ChatSDK.core().userOn(contact));
            }
            Completable.merge(completables).subscribe(e::onComplete, throwable -> e.onError(throwable));
        }).subscribeOn(Schedulers.single());
    }

    public void userOff(final String entityID){
        isOn = false;

        final User user = DaoCore.fetchEntityWithEntityID(User.class, entityID);

        FirebaseReferenceManager.shared().removeListeners(FirebasePaths.userThreadsRef(entityID));
        FirebaseReferenceManager.shared().removeListeners(FirebasePaths.publicThreadsRef());
        FirebaseReferenceManager.shared().removeListeners(FirebasePaths.userFollowersRef(entityID));
        FirebaseReferenceManager.shared().removeListeners(FirebasePaths.userFollowingRef(entityID));

        ThreadWrapper wrapper;
        for (Thread thread : ChatSDK.thread().getThreads(ThreadType.All))
        {
            wrapper = new ThreadWrapper(thread);

            wrapper.off();
            wrapper.messagesOff();
            wrapper.usersOff();
        }

        for (User contact : ChatSDK.contact().contacts()) {
            UserWrapper.initWithModel(contact).metaOff();
        }

        if (ChatSDK.push() != null) {
            ChatSDK.push().unsubscribeToPushChannel(user.getPushChannel());
        }

        disposableList.dispose();
    }

    public PublishSubject<NetworkEvent> source () {
        return eventSource;
    }

    public Observable<NetworkEvent> sourceOnMain () {
        return eventSource.observeOn(AndroidSchedulers.mainThread());
    }


}
