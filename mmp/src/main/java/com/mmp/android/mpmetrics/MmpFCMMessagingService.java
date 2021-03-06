package com.mmp.android.mpmetrics;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.mmp.android.util.MPLog;

/**
 * Service for handling Firebase Cloud Messaging callbacks.
 *
 * <p>You can use FirebaseMessagingService to report Firebase Cloud Messaging registration identifiers
 * to Mmp, and to display incoming notifications from Mmp to
 * the device status bar. This is the simplest way to get up and running with notifications from Mmp.
 *
 * <p>To enable FCM in your application, place your google-services.json file in your Android project
 * root directory, add firebase messaging as a dependency in your gradle file:
 *
 * <pre>
 * {@code
 * buildscript {
 *      ...
 *      dependencies {
 *          classpath 'com.google.gms:google-services:4.1.0'
 *          ...
 *      }
 * }
 *
 * dependencies {
 *     implementation 'com.google.firebase:firebase-messaging:17.3.4'
 *     ...
 * }
 *
 * apply plugin: 'com.google.gms.google-services'
 * }
 * </pre>
 *
 * And finally add a clause like the following
 * to the &lt;application&gt; tag of your AndroidManifest.xml.
 *
 *<pre>
 *{@code
 *
 * <service
 *  android:name="com.mmp.android.mpmetrics.MmpFCMMessagingService"
 *  android:enabled="true"
 *  android:exported="false">
 *      <intent-filter>
 *          <action android:name="com.google.firebase.MESSAGING_EVENT"/>
 *      </intent-filter>
 * </service>
 *}
 *</pre>
 *
 * <p>Once the FirebaseMessagingService is configured, the only thing you have to do to
 * get set up Mmp messages is call {@link MmpAPI.People#identify(String) }
 * with a distinct id for your user.
 *
 * <pre>
 * {@code
 *
 * MmpAPI.People people = mMmpAPI.getPeople();
 * people.identify("A USER DISTINCT ID");
 *
 * }
 * </pre>
 *
 * @see MmpAPI#getPeople()
 * @see <a href="https://mmp.com/docs/people-analytics/android-push">Getting Started with Android Push Notifications</a>
 */
public class MmpFCMMessagingService extends FirebaseMessagingService {
    private static final String LOGTAG = "MmpAPI.MmpFCMMessagingService";
    protected static final int NOTIFICATION_ID = 1;

    /* package */ static void init() {
        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(Task<InstanceIdResult> task) {
                        if (task.isSuccessful()) {
                            String registrationId = task.getResult().getToken();
                            addToken(registrationId);
                        }
                    }
                });
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        MPLog.d(LOGTAG, "MP FCM on new message received");
        onMessageReceived(getApplicationContext(), remoteMessage.toIntent());
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        MPLog.d(LOGTAG, "MP FCM on new push token: " + token);
        addToken(token);
    }

    /**
     * Util method to let subclasses customize the payload through the push notification intent.
     *
     * @param context The application context
     * @param intent Push payload intent. Could be modified before calling super() from a sub-class.
     *
     */
    protected void onMessageReceived(Context context, Intent intent) {
        showPushNotification(context, intent);
    }

    /**
     * Only use this method if you have implemented your own custom FirebaseMessagingService. This
     * is useful when you use multiple push providers.
     * This method should be called from a onNewToken callback. It adds a new FCM token to a Mmp
     * people profile.
     *
     * @param token Firebase Cloud Messaging token to be added to the people profile.
     */
    public static void addToken(final String token) {
        MmpAPI.allInstances(new MmpAPI.InstanceProcessor() {
            @Override
            public void process(MmpAPI api) {
                api.getPeople().setPushRegistrationId(token);
            }
        });
    }

    /**
     * Only use this method if you have implemented your own custom FirebaseMessagingService. This
     * is useful when you use multiple push providers.
     * Displays a Mmp push notification on the device.
     *
     * @param context The application context you are tracking
     * @param messageIntent Intent that bundles the data used to build a notification. If the intent
     *                      is not valid, the notification will not be shown.
     *                      See {@link #showPushNotification(Context, Intent)}
     */
    public static void showPushNotification(Context context, Intent messageIntent) {
        MmpPushNotification mmpPushNotification = new MmpPushNotification(context.getApplicationContext());
        showPushNotification(context, messageIntent, mmpPushNotification);
    }

    /**
     * Only use this method if you have implemented your own custom FirebaseMessagingService. This is
     * useful if you need to override {@link MmpPushNotification} to further customize your
     * Mmp push notification.
     * Displays a Mmp push notification on the device.
     *
     * @param context The application context you are tracking
     * @param messageIntent Intent that bundles the data used to build a notification. If the intent
     *                      is not valid, the notification will not be shown.
     *                      See {@link #showPushNotification(Context, Intent)}
     * @param mmpPushNotification A customized MmpPushNotification object.
     */
    public static void showPushNotification(Context context, Intent messageIntent, MmpPushNotification mmpPushNotification) {
        Notification notification = mmpPushNotification.createNotification(messageIntent);

        MmpNotificationData data = mmpPushNotification.getData();
        String message = data == null ? "null" : data.getMessage();
        MPLog.d(LOGTAG, "MP FCM notification received: " + message);

        if (notification != null) {
            if (!mmpPushNotification.isValid()) {
                MPLog.e(LOGTAG, "MP FCM notification has error");
            }
            final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (null != data.getTag()) {
                notificationManager.notify(data.getTag(), NOTIFICATION_ID, notification);
            } else {
                notificationManager.notify(mmpPushNotification.getNotificationId(), notification);
            }
        }
    }

    protected void cancelNotification(Bundle extras, NotificationManager notificationManager) {
        int notificationId = extras.getInt("mp_notification_id");
        String tag = extras.getString("mp_tag");
        boolean hasTag = tag != null;

        if (hasTag) {
            notificationManager.cancel(tag, MmpFCMMessagingService.NOTIFICATION_ID);
        } else {
            notificationManager.cancel(notificationId);
        }
    }
}
