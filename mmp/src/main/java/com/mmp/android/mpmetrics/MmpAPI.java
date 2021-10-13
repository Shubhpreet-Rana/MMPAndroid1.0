package com.mmp.android.mpmetrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.mmp.android.R;
import com.mmp.android.takeoverinapp.TakeoverInAppActivity;
import com.mmp.android.util.ActivityImageUtils;
import com.mmp.android.util.MPLog;
import com.mmp.android.viewcrawler.TrackingDebug;
import com.mmp.android.viewcrawler.UpdatesFromMmp;
import com.mmp.android.viewcrawler.ViewCrawler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Core class for interacting with Mmp Analytics.
 *
 * <p>Call {@link #getInstance(Context, String)} with
 * your main application activity and your Mmp API token as arguments
 * an to get an instance you can use to report how users are using your
 * application.
 *
 * <p>Once you have an instance, you can send events to Mmp
 * using {@link #track(String, JSONObject)}, and update People Analytics
 * records with {@link #getPeople()}
 *
 * <p>The Mmp library will periodically send information to
 * Mmp servers, so your application will need to have
 * <code>android.permission.INTERNET</code>. In addition, to preserve
 * battery life, messages to Mmp servers may not be sent immediately
 * when you call {@link #track(String)}or {@link People#set(String, Object)}.
 * The library will send messages periodically throughout the lifetime
 * of your application, but you will need to call {@link #flush()}
 * before your application is completely shutdown to ensure all of your
 * events are sent.
 *
 * <p>A typical use-case for the library might look like this:
 *
 * <pre>
 * {@code
 * public class MainActivity extends Activity {
 *      MmpAPI mMmp;
 *
 *      public void onCreate(Bundle saved) {
 *          mMmp = MmpAPI.getInstance(this, "YOUR MMP API TOKEN");
 *          ...
 *      }
 *
 *      public void whenSomethingInterestingHappens(int flavor) {
 *          JSONObject properties = new JSONObject();
 *          properties.put("flavor", flavor);
 *          mMmp.track("Something Interesting Happened", properties);
 *          ...
 *      }
 *
 *      public void onDestroy() {
 *          mMmp.flush();
 *          super.onDestroy();
 *      }
 * }
 * }
 * </pre>
 *
 * <p>In addition to this documentation, you may wish to take a look at
 * <a href="https://github.com/mmp/sample-android-mmp-integration">the Mmp sample Android application</a>.
 * It demonstrates a variety of techniques, including
 * updating People Analytics records with {@link People} and others.
 *
 * <p>There are also <a href="https://mmp.com/docs/">step-by-step getting started documents</a>
 * available at mmp.com
 *
 * @see <a href="https://mmp.com/docs/integration-libraries/android">getting started documentation for tracking events</a>
 * @see <a href="https://mmp.com/docs/people-analytics/android">getting started documentation for People Analytics</a>
 * @see <a href="https://mmp.com/docs/people-analytics/android-push">getting started with push notifications for Android</a>
 * @see <a href="https://github.com/mmp/sample-android-mmp-integration">The Mmp Android sample application</a>
 */
public class MmpAPI {
    /**
     * String version of the library.
     */
    public static final String VERSION = MPConfig.VERSION;

    /**
     * Declare a string-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mmp A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     *
     * @param tweakName Unique name to identify your tweak.
     * @param defaultValue Default value of your tweak.
     *
     * @return A new or existing tweak object.
     */
    public static Tweak<String> stringTweak(String tweakName, String defaultValue) {
        return sSharedTweaks.stringTweak(tweakName, defaultValue);
    }

    /**
     * Declare a boolean-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mmp A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     *
     * @param tweakName Unique name to identify your tweak.
     * @param defaultValue Default value of your tweak.
     *
     * @return A new or existing tweak object.
     */
    public static Tweak<Boolean> booleanTweak(String tweakName, boolean defaultValue) {
        return sSharedTweaks.booleanTweak(tweakName, defaultValue);
    }

    /**
     * Declare a double-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mmp A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     *
     * @param tweakName Unique name to identify your tweak.
     * @param defaultValue Default value of your tweak.
     *
     * @return A new or existing tweak object.
     */
    public static Tweak<Double> doubleTweak(String tweakName, double defaultValue) {
        return sSharedTweaks.doubleTweak(tweakName, defaultValue);
    }

    /**
     * Declare a double-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mmp A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     *
     * @param tweakName Unique name to identify your tweak.
     * @param defaultValue Default value of your tweak.
     * @param minimumValue Minimum numeric value of your tweak.
     * @param maximumValue Maximum numeric value of your tweak.
     *
     * @return A new or existing tweak object.
     */
    public static Tweak<Double> doubleTweak(String tweakName, double defaultValue, double minimumValue, double maximumValue) {
        return sSharedTweaks.doubleTweak(tweakName, defaultValue, minimumValue, maximumValue);
    }

    /**
     * Declare a float-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mmp A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     *
     * @param tweakName Unique name to identify your tweak.
     * @param defaultValue Default value of your tweak.
     *
     * @return A new or existing tweak object.
     */
    public static Tweak<Float> floatTweak(String tweakName, float defaultValue) {
        return sSharedTweaks.floatTweak(tweakName, defaultValue);
    }

    /**
     * Declare a float-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mmp A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     *
     * @param tweakName Unique name to identify your tweak.
     * @param defaultValue Default value of your tweak.
     * @param minimumValue Minimum numeric value of your tweak.
     * @param maximumValue Maximum numeric value of your tweak.
     *
     * @return A new or existing tweak object.
     */
    public static Tweak<Float> floatTweak(String tweakName, float defaultValue, float minimumValue, float maximumValue) {
        return sSharedTweaks.floatTweak(tweakName, defaultValue, minimumValue, maximumValue);
    }

    /**
     * Declare a long-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mmp A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     *
     * @param tweakName Unique name to identify your tweak.
     * @param defaultValue Default value of your tweak.
     *
     * @return A new or existing tweak object.
     */
    public static Tweak<Long> longTweak(String tweakName, long defaultValue) {
        return sSharedTweaks.longTweak(tweakName, defaultValue);
    }

    /**
     * Declare a long-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mmp A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     *
     * @param tweakName Unique name to identify your tweak.
     * @param defaultValue Default value of your tweak.
     * @param minimumValue Minimum numeric value of your tweak.
     * @param maximumValue Maximum numeric value of your tweak.
     *
     * @return A new or existing tweak object.
     */
    public static Tweak<Long> longTweak(String tweakName, long defaultValue, long minimumValue, long maximumValue) {
        return sSharedTweaks.longTweak(tweakName, defaultValue, minimumValue, maximumValue);
    }

    /**
     * Declare an int-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mmp A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     *
     * @param tweakName Unique name to identify your tweak.
     * @param defaultValue Default value of your tweak.
     *
     * @return A new or existing tweak object.
     */
    public static Tweak<Integer> intTweak(String tweakName, int defaultValue) {
        return sSharedTweaks.intTweak(tweakName, defaultValue);
    }

    /**
     * Declare an int-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mmp A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     *
     * @param tweakName Unique name to identify your tweak.
     * @param defaultValue Default value of your tweak.
     * @param minimumValue Minimum numeric value of your tweak.
     * @param maximumValue Maximum numeric value of your tweak.
     *
     * @return A new or existing tweak object.
     */
    public static Tweak<Integer> intTweak(String tweakName, int defaultValue, int minimumValue, int maximumValue) {
        return sSharedTweaks.intTweak(tweakName, defaultValue, minimumValue, maximumValue);
    }

    /**
     * Declare short-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mmp A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     *
     * @param tweakName Unique name to identify your tweak.
     * @param defaultValue Default value of your tweak.
     *
     * @return A new or existing tweak object.
     */
    public static Tweak<Short> shortTweak(String tweakName, short defaultValue) {
        return sSharedTweaks.shortTweak(tweakName, defaultValue);
    }

    /**
     * Declare byte-valued tweak, and return a reference you can use to read the value of the tweak.
     * Tweaks can be changed in Mmp A/B tests, and can allow you to alter your customers' experience
     * in your app without re-deploying your application through the app store.
     *
     * @param tweakName Unique name to identify your tweak.
     * @param defaultValue Default value of your tweak.
     *
     * @return A new or existing tweak object.
     */
    public static Tweak<Byte> byteTweak(String tweakName, byte defaultValue) {
        return sSharedTweaks.byteTweak(tweakName, defaultValue);
    }

    /**
     * Track a push notification event using data from the intent
     */
    /* package */ static void trackPushNotificationEventFromIntent(Context context, Intent intent, String eventName) {
        trackPushNotificationEventFromIntent(context, intent, eventName, new JSONObject());
    }

    /**
     * Track a push notification event using data from the intent
     */
    /* package */ static void trackPushNotificationEventFromIntent(Context context, Intent intent, String eventName, JSONObject additionalProperties) {
        if (intent.hasExtra("mp") && intent.hasExtra("mp_campaign_id") && intent.hasExtra("mp_message_id")) {
            final String messageId = intent.getStringExtra("mp_message_id");
            final String campaignId = intent.getStringExtra("mp_campaign_id");
            final String androidNotificationId = intent.getStringExtra("mp_canonical_notification_id");
            trackPushNotificationEvent(context, Integer.valueOf(campaignId), Integer.valueOf(messageId), androidNotificationId, intent.getStringExtra("mp"), eventName, additionalProperties);
        } else {
            MPLog.e(LOGTAG, "Intent is missing Mmp notification metadata, not tracking event: \"" + eventName + "\"");
        }
    }

    /**
     * Track a push notification event using the project token and distinct id from the mp payload
     */
    /* package */ static void trackPushNotificationEvent(Context context, Integer campaignId, Integer messageId, String androidNotificationId, String mpPayloadStr, String eventName, JSONObject additionalProperties) {
        JSONObject mpPayload;
        try {
            mpPayload = new JSONObject(mpPayloadStr);
        } catch (JSONException e) {
            MPLog.e(LOGTAG, "Exception parsing mp payload from intent extras, not tracking event: \"" + eventName + "\"", e);
            return;
        }

        String projectToken = mpPayload.optString("token");
        if (projectToken == null) {
            MPLog.e(LOGTAG, "\"token\" not found in mp payload, not tracking event: \"" + eventName + "\"");
            return;
        }
        mpPayload.remove("token");

        String distinctId = mpPayload.optString("distinct_id");
        if (distinctId == null) {
            MPLog.e(LOGTAG, "\"distinct_id\" not found in mp payload, not tracking event: \"" + eventName + "\"");
            return;
        }
        mpPayload.remove("distinct_id");

        JSONObject properties = mpPayload;

        try {
            Iterator<String> itr = additionalProperties.keys();
            while(itr.hasNext()) {
                String key = itr.next();
                properties.put(key, additionalProperties.get(key));
            }
            properties.put("message_id", messageId); // no $ prefix for historical consistency
            properties.put("campaign_id", campaignId); // no $ prefix for historical consistency
            properties.put("$android_notification_id", androidNotificationId);
        } catch (JSONException e) {
            MPLog.e(LOGTAG, "Error setting tracking JSON properties.", e);
        }

        MmpAPI instance = getInstanceFromMpPayload(context, mpPayloadStr);
        if (instance == null) {
            MPLog.e(LOGTAG, "Got null instance, not tracking \"" + eventName + "\"");
        } else {
            instance.track(eventName, properties);
            instance.flushNoDecideCheck();
        }
    }

    /* package */ static MmpAPI getInstanceFromMpPayload(Context context, String mpPayloadStr) {
        JSONObject mpPayload;
        try {
            mpPayload = new JSONObject(mpPayloadStr);
        } catch (JSONException e) {
            return null;
        }

        String projectToken = mpPayload.optString("token");
        if (projectToken == null) {
            return null;
        }

        return MmpAPI.getInstance(context, projectToken);
    }

    /**
     * You shouldn't instantiate MmpAPI objects directly.
     * Use MmpAPI.getInstance to get an instance.
     */
    MmpAPI(Context context, Future<SharedPreferences> referrerPreferences, String token, boolean optOutTrackingDefault, JSONObject superProperties) {
        this(context, referrerPreferences, token, MPConfig.getInstance(context), optOutTrackingDefault, superProperties);
    }

    /**
     * You shouldn't instantiate MmpAPI objects directly.
     * Use MmpAPI.getInstance to get an instance.
     */
    MmpAPI(Context context, Future<SharedPreferences> referrerPreferences, String token, MPConfig config, boolean optOutTrackingDefault, JSONObject superProperties) {
        mContext = context;
        mToken = token;
        mPeople = new PeopleImpl();
        mGroups = new HashMap<String, GroupImpl>();
        mConfig = config;

        final Map<String, String> deviceInfo = new HashMap<String, String>();
        deviceInfo.put("$android_lib_version", MPConfig.VERSION);
        deviceInfo.put("$android_os", "Android");
        deviceInfo.put("$android_os_version", Build.VERSION.RELEASE == null ? "UNKNOWN" : Build.VERSION.RELEASE);
        deviceInfo.put("$android_manufacturer", Build.MANUFACTURER == null ? "UNKNOWN" : Build.MANUFACTURER);
        deviceInfo.put("$android_brand", Build.BRAND == null ? "UNKNOWN" : Build.BRAND);
        deviceInfo.put("$android_model", Build.MODEL == null ? "UNKNOWN" : Build.MODEL);
        try {
            final PackageManager manager = mContext.getPackageManager();
            final PackageInfo info = manager.getPackageInfo(mContext.getPackageName(), 0);
            deviceInfo.put("$android_app_version", info.versionName);
            deviceInfo.put("$android_app_version_code", Integer.toString(info.versionCode));
        } catch (final PackageManager.NameNotFoundException e) {
            MPLog.e(LOGTAG, "Exception getting app version name", e);
        }
        mDeviceInfo = Collections.unmodifiableMap(deviceInfo);

        mSessionMetadata = new SessionMetadata();
        mUpdatesFromMmp = constructUpdatesFromMmp(context, token);
        mTrackingDebug = constructTrackingDebug();
        mMessages = getAnalyticsMessages();
        mPersistentIdentity = getPersistentIdentity(context, referrerPreferences, token);
        mEventTimings = mPersistentIdentity.getTimeEvents();

        if (optOutTrackingDefault && (hasOptedOutTracking() || !mPersistentIdentity.hasOptOutFlag(token))) {
            optOutTracking();
        }

        if (superProperties != null) {
            registerSuperProperties(superProperties);
        }
        mUpdatesListener = constructUpdatesListener();
        mDecideMessages = constructDecideUpdates(token, mUpdatesListener, mUpdatesFromMmp);
        mConnectIntegrations = new ConnectIntegrations(this, mContext);

        // TODO reading persistent identify immediately forces the lazy load of the preferences, and defeats the
        // purpose of PersistentIdentity's laziness.
        String decideId = mPersistentIdentity.getPeopleDistinctId();
        if (null == decideId) {
            decideId = mPersistentIdentity.getEventsDistinctId();
        }
        mDecideMessages.setDistinctId(decideId);

        final boolean dbExists = MPDbAdapter.getInstance(mContext).getDatabaseFile().exists();

        registerMmpActivityLifecycleCallbacks();

        if (ConfigurationChecker.checkInstallReferrerConfiguration(sReferrerPrefs)) {
            InstallReferrerPlay referrerPlay = new InstallReferrerPlay(getContext(), new InstallReferrerPlay.ReferrerCallback() {
                @Override
                public void onReferrerReadSuccess() {
                    mMessages.updateEventProperties(new AnalyticsMessages.UpdateEventsPropertiesDescription(mToken, mPersistentIdentity.getReferrerProperties()));
                }
            });
            referrerPlay.connect();
        }

        if (mPersistentIdentity.isFirstLaunch(dbExists, mToken)) {
            track(AutomaticEvents.FIRST_OPEN, null, true);
            mPersistentIdentity.setHasLaunched(mToken);
        }

        if (!mConfig.getDisableDecideChecker()) {
            mMessages.installDecideCheck(mDecideMessages);
        }

        if (sendAppOpen()) {
            track("$app_open", null);
        }

        if (!mPersistentIdentity.isFirstIntegration(mToken)) {
            try {
                final JSONObject messageProps = new JSONObject();
                messageProps.put("mp_lib", "Android");
                messageProps.put("lib", "Android");
                messageProps.put("distinct_id", token);
                messageProps.put("$lib_version", MPConfig.VERSION);
                messageProps.put("$user_id", token);
                final AnalyticsMessages.EventDescription eventDescription =
                        new AnalyticsMessages.EventDescription(
                                "Integration",
                                messageProps,
                                "85053bf24bba75239b16a601d9387e17");
                mMessages.eventsMessage(eventDescription);
                mMessages.postToServer(new AnalyticsMessages.FlushDescription("85053bf24bba75239b16a601d9387e17", false));

                mPersistentIdentity.setIsIntegrated(mToken);
            } catch (JSONException e) {
            }
        }

        if (mPersistentIdentity.isNewVersion(deviceInfo.get("$android_app_version_code"))) {
            try {
                final JSONObject messageProps = new JSONObject();
                messageProps.put(AutomaticEvents.VERSION_UPDATED, deviceInfo.get("$android_app_version"));
                track(AutomaticEvents.APP_UPDATED, messageProps, true);
            } catch (JSONException e) {}

        }

        mUpdatesFromMmp.startUpdates();

        if (!mConfig.getDisableExceptionHandler()) {
            ExceptionHandler.init();
        }
    }

    /**
     * Get the instance of MmpAPI associated with your Mmp project token.
     *
     * <p>Use getInstance to get a reference to a shared
     * instance of MmpAPI you can use to send events
     * and People Analytics updates to Mmp.</p>
     * <p>getInstance is thread safe, but the returned instance is not,
     * and may be shared with other callers of getInstance.
     * The best practice is to call getInstance, and use the returned MmpAPI,
     * object from a single thread (probably the main UI thread of your application).</p>
     * <p>If you do choose to track events from multiple threads in your application,
     * you should synchronize your calls on the instance itself, like so:</p>
     * <pre>
     * {@code
     * MmpAPI instance = MmpAPI.getInstance(context, token);
     * synchronized(instance) { // Only necessary if the instance will be used in multiple threads.
     *     instance.track(...)
     * }
     * }
     * </pre>
     *
     * @param context The application context you are tracking
     * @param token Your Mmp project token. You can get your project token on the Mmp web site,
     *     in the settings dialog.
     * @return an instance of MmpAPI associated with your project
     */
    public static MmpAPI getInstance(Context context, String token) {
        return getInstance(context, token, false, null);
    }

    /**
     * Get the instance of MmpAPI associated with your Mmp project token.
     *
     * <p>Use getInstance to get a reference to a shared
     * instance of MmpAPI you can use to send events
     * and People Analytics updates to Mmp.</p>
     * <p>getInstance is thread safe, but the returned instance is not,
     * and may be shared with other callers of getInstance.
     * The best practice is to call getInstance, and use the returned MmpAPI,
     * object from a single thread (probably the main UI thread of your application).</p>
     * <p>If you do choose to track events from multiple threads in your application,
     * you should synchronize your calls on the instance itself, like so:</p>
     * <pre>
     * {@code
     * MmpAPI instance = MmpAPI.getInstance(context, token);
     * synchronized(instance) { // Only necessary if the instance will be used in multiple threads.
     *     instance.track(...)
     * }
     * }
     * </pre>
     *
     * @param context The application context you are tracking
     * @param token Your Mmp project token. You can get your project token on the Mmp web site,
     *     in the settings dialog.
     * @param optOutTrackingDefault Whether or not Mmp can start tracking by default. See
     *     {@link #optOutTracking()}.
     * @return an instance of MmpAPI associated with your project
     */
    public static MmpAPI getInstance(Context context, String token, boolean optOutTrackingDefault) {
        return getInstance(context, token, optOutTrackingDefault, null);
    }

    /**
     * Get the instance of MmpAPI associated with your Mmp project token.
     *
     * <p>Use getInstance to get a reference to a shared
     * instance of MmpAPI you can use to send events
     * and People Analytics updates to Mmp.</p>
     * <p>getInstance is thread safe, but the returned instance is not,
     * and may be shared with other callers of getInstance.
     * The best practice is to call getInstance, and use the returned MmpAPI,
     * object from a single thread (probably the main UI thread of your application).</p>
     * <p>If you do choose to track events from multiple threads in your application,
     * you should synchronize your calls on the instance itself, like so:</p>
     * <pre>
     * {@code
     * MmpAPI instance = MmpAPI.getInstance(context, token);
     * synchronized(instance) { // Only necessary if the instance will be used in multiple threads.
     *     instance.track(...)
     * }
     * }
     * </pre>
     *
     * @param context The application context you are tracking
     * @param token Your Mmp project token. You can get your project token on the Mmp web site,
     *     in the settings dialog.
     * @param superProperties A JSONObject containing super properties to register.
     * @return an instance of MmpAPI associated with your project
     */
    public static MmpAPI getInstance(Context context, String token, JSONObject superProperties) {
        return getInstance(context, token, false, superProperties);
    }

    /**
     * Get the instance of MmpAPI associated with your Mmp project token.
     *
     * <p>Use getInstance to get a reference to a shared
     * instance of MmpAPI you can use to send events
     * and People Analytics updates to Mmp.</p>
     * <p>getInstance is thread safe, but the returned instance is not,
     * and may be shared with other callers of getInstance.
     * The best practice is to call getInstance, and use the returned MmpAPI,
     * object from a single thread (probably the main UI thread of your application).</p>
     * <p>If you do choose to track events from multiple threads in your application,
     * you should synchronize your calls on the instance itself, like so:</p>
     * <pre>
     * {@code
     * MmpAPI instance = MmpAPI.getInstance(context, token);
     * synchronized(instance) { // Only necessary if the instance will be used in multiple threads.
     *     instance.track(...)
     * }
     * }
     * </pre>
     *
     * @param context The application context you are tracking
     * @param token Your Mmp project token. You can get your project token on the Mmp web site,
     *     in the settings dialog.
     * @param optOutTrackingDefault Whether or not Mmp can start tracking by default. See
     *     {@link #optOutTracking()}.
     * @param superProperties A JSONObject containing super properties to register.
     * @return an instance of MmpAPI associated with your project
     */
    public static MmpAPI getInstance(Context context, String token, boolean optOutTrackingDefault, JSONObject superProperties) {
        if (null == token || null == context) {
            return null;
        }
        synchronized (sInstanceMap) {
            final Context appContext = context.getApplicationContext();

            if (null == sReferrerPrefs) {
                sReferrerPrefs = sPrefsLoader.loadPreferences(context, MPConfig.REFERRER_PREFS_NAME, null);
            }

            Map <Context, MmpAPI> instances = sInstanceMap.get(token);
            if (null == instances) {
                instances = new HashMap<Context, MmpAPI>();
                sInstanceMap.put(token, instances);
            }

            MmpAPI instance = instances.get(appContext);
            if (null == instance && ConfigurationChecker.checkBasicConfiguration(appContext)) {
                instance = new MmpAPI(appContext, sReferrerPrefs, token, optOutTrackingDefault, superProperties);
                registerAppLinksListeners(context, instance);
                instances.put(appContext, instance);
                if (ConfigurationChecker.checkPushNotificationConfiguration(appContext)) {
                    try {
                        MmpFCMMessagingService.init();
                    } catch (Exception e) {
                        MPLog.e(LOGTAG, "Push notification could not be initialized", e);
                    }
                }
            }

            checkIntentForInboundAppLink(context);

            return instance;
        }
    }

    /**
     * Controls whether to automatically send the client IP Address as part of event tracking.
     *
     * <p> With an IP address, geo-location is possible down to neighborhoods within a city,
     * although the Mmp Dashboard will just show you city level location specificity.
     *
     * @param useIpAddressForGeolocation If true, automatically send the client IP Address. Defaults to true.
     */
    public void setUseIpAddressForGeolocation(boolean useIpAddressForGeolocation) {
        mConfig.setUseIpAddressForGeolocation(useIpAddressForGeolocation);
    }

    /**
     * Controls whether to enable the run time debug logging
     *
     * @param enableLogging If true, emit more detailed log messages. Defaults to false
     */
    public void setEnableLogging(boolean enableLogging) {
        mConfig.setEnableLogging(enableLogging);
    }

    /**
     * Set the base URL used for Mmp API requests.
     * Useful if you need to proxy Mmp requests. Defaults to https://api.mmp.com.
     * To route data to Mmp's EU servers, set to https://api-eu.mmp.com
     *
     * @param serverURL the base URL used for Mmp API requests
     */
    public void setSertrackverURL(String serverURL) {
        mConfig.setServerURL(serverURL);
    }

    /**
     * This function creates a distinct_id alias from alias to original. If original is null, then it will create an alias
     * to the current events distinct_id, which may be the distinct_id randomly generated by the Mmp library
     * before {@link #identify(String)} is called.
     *
     * <p>This call does not identify the user after. You must still call both {@link #identify(String)} and
     * {@link People#identify(String)} if you wish the new alias to be used for Events and People.
     *
     * @param alias the new distinct_id that should represent original.
     * @param original the old distinct_id that alias will be mapped to.
     */
    public void alias(String alias, String original) {
        if (hasOptedOutTracking()) return;
        if (original == null) {
            original = getDistinctId();
        }
        if (alias.equals(original)) {
            MPLog.w(LOGTAG, "Attempted to alias identical distinct_ids " + alias + ". Alias message will not be sent.");
            return;
        }
        try {
            final JSONObject j = new JSONObject();
            j.put("alias", alias);
            j.put("original", original);
            track("$create_alias", j);
        } catch (final JSONException e) {
            MPLog.e(LOGTAG, "Failed to alias", e);
        }
        flush();
    }

    /**
     * Associate all future calls to {@link #track(String, JSONObject)} with the user identified by
     * the given distinct id.
     *
     * <p>This call does not identify the user for People Analytics;
     * to do that, see {@link People#identify(String)}. Mmp recommends using
     * the same distinct_id for both calls, and using a distinct_id that is easy
     * to associate with the given user, for example, a server-side account identifier.
     *
     * <p>Calls to {@link #track(String, JSONObject)} made before corresponding calls to identify
     * will use an anonymous locally generated distinct id, which means it is best to call identify
     * early to ensure that your Mmp funnels and retention analytics can continue to track the
     * user throughout their lifetime. We recommend calling identify when the user authenticates.
     *
     * <p>Once identify is called, the local distinct id persists across restarts of
     * your application.
     *
     * @param distinctId a string uniquely identifying this user. Events sent to
     *     Mmp using the same disinct_id will be considered associated with the
     *     same visitor/customer for retention and funnel reporting, so be sure that the given
     *     value is globally unique for each individual user you intend to track.
     *
     * @see People#identify(String)
     */
    public void identify(String distinctId) {
        identify(distinctId, true);
    }

    private void identify(String distinctId, boolean markAsUserId) {
        if (hasOptedOutTracking()) return;
        if (distinctId == null) {
            MPLog.e(LOGTAG, "Can't identify with null distinct_id.");
            return;
        }
        synchronized (mPersistentIdentity) {
            String currentEventsDistinctId = mPersistentIdentity.getEventsDistinctId();
            mPersistentIdentity.setAnonymousIdIfAbsent(currentEventsDistinctId);
            mPersistentIdentity.setEventsDistinctId(distinctId);
            if(markAsUserId) {
                mPersistentIdentity.markEventsUserIdPresent();
            }
            String decideId = mPersistentIdentity.getPeopleDistinctId();
            if (null == decideId) {
                decideId = mPersistentIdentity.getEventsDistinctId();
            }
            mDecideMessages.setDistinctId(decideId);

            if (!distinctId.equals(currentEventsDistinctId)) {
                try {
                    JSONObject identifyPayload = new JSONObject();
                    identifyPayload.put("$anon_distinct_id", currentEventsDistinctId);
                    track("$identify", identifyPayload);
                } catch (JSONException e) {
                    MPLog.e(LOGTAG, "Could not track $identify event");
                }
            }
        }
    }

    /**
     * Begin timing of an event. Calling timeEvent("Thing") will not send an event, but
     * when you eventually call track("Thing"), your tracked event will be sent with a "$duration"
     * property, representing the number of seconds between your calls.
     *
     * @param eventName the name of the event to track with timing.
     */
    public void timeEvent(final String eventName) {
        if (hasOptedOutTracking()) return;
        final long writeTime = System.currentTimeMillis();
        synchronized (mEventTimings) {
            mEventTimings.put(eventName, writeTime);
            mPersistentIdentity.addTimeEvent(eventName, writeTime);
        }
    }

    /**
     * Retrieves the time elapsed for the named event since timeEvent() was called.
     *
     * @param eventName the name of the event to be tracked that was previously called with timeEvent()
     *
     * @return Time elapsed since {@link #timeEvent(String)} was called for the given eventName.
     */
    public double eventElapsedTime(final String eventName) {
        final long currentTime = System.currentTimeMillis();
        Long startTime;
        synchronized (mEventTimings) {
            startTime = mEventTimings.get(eventName);
        }
        return startTime == null ? 0 : (double)((currentTime - startTime) / 1000);
    }

    /**
     * Track an event.
     *
     * <p>Every call to track eventually results in a data point sent to Mmp. These data points
     * are what are measured, counted, and broken down to create your Mmp reports. Events
     * have a string name, and an optional set of name/value pairs that describe the properties of
     * that event.
     *
     * @param eventName The name of the event to send
     * @param properties A Map containing the key value pairs of the properties to include in this event.
     *                   Pass null if no extra properties exist.
     *
     * See also {@link #track(String, org.json.JSONObject)}
     */
    public void trackMap(String eventName, Map<String, Object> properties) {
        if (hasOptedOutTracking()) return;
        if (null == properties) {
            track(eventName, null);
        } else {
            try {
                track(eventName, new JSONObject(properties));
            } catch (NullPointerException e) {
                MPLog.w(LOGTAG, "Can't have null keys in the properties of trackMap!");
            }
        }
    }

    /**
     * Track an event with specific groups.
     *
     * <p>Every call to track eventually results in a data point sent to Mmp. These data points
     * are what are measured, counted, and broken down to create your Mmp reports. Events
     * have a string name, and an optional set of name/value pairs that describe the properties of
     * that event. Group key/value pairs are upserted into the property map before tracking.
     *
     * @param eventName The name of the event to send
     * @param properties A Map containing the key value pairs of the properties to include in this event.
     *                   Pass null if no extra properties exist.
     * @param groups A Map containing the group key value pairs for this event.
     *
     * See also {@link #track(String, org.json.JSONObject)}, {@link #trackMap(String, Map)}
     */
    public void trackWithGroups(String eventName, Map<String, Object> properties, Map<String, Object> groups) {
        if (hasOptedOutTracking()) return;

        if (null == groups) {
            trackMap(eventName, properties);
        } else if (null == properties) {
            trackMap(eventName, groups);
        } else {
            for (Entry<String, Object> e : groups.entrySet()) {
                if (e.getValue() != null) {
                    properties.put(e.getKey(), e.getValue());
                }
            }

            trackMap(eventName, properties);
        }
    }

    /**
     * Track an event.
     *
     * <p>Every call to track eventually results in a data point sent to Mmp. These data points
     * are what are measured, counted, and broken down to create your Mmp reports. Events
     * have a string name, and an optional set of name/value pairs that describe the properties of
     * that event.
     *
     * @param eventName The name of the event to send
     * @param properties A JSONObject containing the key value pairs of the properties to include in this event.
     *                   Pass null if no extra properties exist.
     */
    // DO NOT DOCUMENT, but track() must be thread safe since it is used to track events in
    // notifications from the UI thread, which might not be our MmpAPI "home" thread.
    // This MAY CHANGE IN FUTURE RELEASES, so minimize code that assumes thread safety
    // (and perhaps document that code here).
    public void track(String eventName, JSONObject properties) {
        if (hasOptedOutTracking()) return;
        track(eventName, properties, false);
    }

    /**
     * Equivalent to {@link #track(String, JSONObject)} with a null argument for properties.
     * Consider adding properties to your tracking to get the best insights and experience from Mmp.
     * @param eventName the name of the event to send
     */
    public void track(String eventName) {
        if (hasOptedOutTracking()) return;
        track(eventName, null);
    }

    /**
     * Push all queued Mmp events and People Analytics changes to Mmp servers.
     *
     * <p>Events and People messages are pushed gradually throughout
     * the lifetime of your application. This means that to ensure that all messages
     * are sent to Mmp when your application is shut down, you will
     * need to call flush() to let the Mmp library know it should
     * send all remaining messages to the server. We strongly recommend
     * placing a call to flush() in the onDestroy() method of
     * your main application activity.
     */
    public void flush() {
        if (hasOptedOutTracking()) return;
        mMessages.postToServer(new AnalyticsMessages.FlushDescription(mToken));
    }

    /**
     * Returns a json object of the user's current super properties
     *
     *<p>SuperProperties are a collection of properties that will be sent with every event to Mmp,
     * and persist beyond the lifetime of your application.
     *
     * @return Super properties for this Mmp instance.
     */
      public JSONObject getSuperProperties() {
          JSONObject ret = new JSONObject();
          mPersistentIdentity.addSuperPropertiesToObject(ret);
          return ret;
      }

    /**
     * Returns the string id currently being used to uniquely identify the user associated
     * with events sent using {@link #track(String, JSONObject)}. Before any calls to
     * {@link #identify(String)}, this will be an id automatically generated by the library.
     *
     * <p>The id returned by getDistinctId is independent of the distinct id used to identify
     * any People Analytics properties in Mmp. To read and write that identifier,
     * use {@link People#identify(String)} and {@link People#getDistinctId()}.
     *
     * @return The distinct id associated with event tracking
     *
     * @see #identify(String)
     * @see People#getDistinctId()
     */
    public String getDistinctId() {
        return mPersistentIdentity.getEventsDistinctId();
    }

     /**
     * Returns the anonymoous id currently being used to uniquely identify the device and all
     * with events sent using {@link #track(String, JSONObject)} will have this id as a device
     * id
     *
     * @return The device id associated with event tracking
     */
    protected String getAnonymousId() {
        return mPersistentIdentity.getAnonymousId();
    }

    /**
     * Returns the user id with which identify is called  and all the with events sent using
     * {@link #track(String, JSONObject)} will have this id as a user id
     *
     * @return The user id associated with event tracking
     */
    protected String getUserId() {
        return mPersistentIdentity.getEventsUserId();
    }

    /**
     * Register properties that will be sent with every subsequent call to {@link #track(String, JSONObject)}.
     *
     * <p>SuperProperties are a collection of properties that will be sent with every event to Mmp,
     * and persist beyond the lifetime of your application.
     *
     * <p>Setting a superProperty with registerSuperProperties will store a new superProperty,
     * possibly overwriting any existing superProperty with the same name (to set a
     * superProperty only if it is currently unset, use {@link #registerSuperPropertiesOnce(JSONObject)})
     *
     * <p>SuperProperties will persist even if your application is taken completely out of memory.
     * to remove a superProperty, call {@link #unregisterSuperProperty(String)} or {@link #clearSuperProperties()}
     *
     * @param superProperties    A Map containing super properties to register
     *
     * See also {@link #registerSuperProperties(org.json.JSONObject)}
     */
    public void registerSuperPropertiesMap(Map<String, Object> superProperties) {
        if (hasOptedOutTracking()) return;
        if (null == superProperties) {
            MPLog.e(LOGTAG, "registerSuperPropertiesMap does not accept null properties");
            return;
        }

        try {
            registerSuperProperties(new JSONObject(superProperties));
        } catch (NullPointerException e) {
            MPLog.w(LOGTAG, "Can't have null keys in the properties of registerSuperPropertiesMap");
        }
    }

    /**
     * Register properties that will be sent with every subsequent call to {@link #track(String, JSONObject)}.
     *
     * <p>SuperProperties are a collection of properties that will be sent with every event to Mmp,
     * and persist beyond the lifetime of your application.
     *
     * <p>Setting a superProperty with registerSuperProperties will store a new superProperty,
     * possibly overwriting any existing superProperty with the same name (to set a
     * superProperty only if it is currently unset, use {@link #registerSuperPropertiesOnce(JSONObject)})
     *
     * <p>SuperProperties will persist even if your application is taken completely out of memory.
     * to remove a superProperty, call {@link #unregisterSuperProperty(String)} or {@link #clearSuperProperties()}
     *
     * @param superProperties    A JSONObject containing super properties to register
     * @see #registerSuperPropertiesOnce(JSONObject)
     * @see #unregisterSuperProperty(String)
     * @see #clearSuperProperties()
     */
    public void registerSuperProperties(JSONObject superProperties) {
        if (hasOptedOutTracking()) return;
        mPersistentIdentity.registerSuperProperties(superProperties);
    }

    /**
     * Remove a single superProperty, so that it will not be sent with future calls to {@link #track(String, JSONObject)}.
     *
     * <p>If there is a superProperty registered with the given name, it will be permanently
     * removed from the existing superProperties.
     * To clear all superProperties, use {@link #clearSuperProperties()}
     *
     * @param superPropertyName name of the property to unregister
     * @see #registerSuperProperties(JSONObject)
     */
    public void unregisterSuperProperty(String superPropertyName) {
        if (hasOptedOutTracking()) return;
        mPersistentIdentity.unregisterSuperProperty(superPropertyName);
    }

    /**
     * Register super properties for events, only if no other super property with the
     * same names has already been registered.
     *
     * <p>Calling registerSuperPropertiesOnce will never overwrite existing properties.
     *
     * @param superProperties A Map containing the super properties to register.
     *
     * See also {@link #registerSuperPropertiesOnce(org.json.JSONObject)}
     */
    public void registerSuperPropertiesOnceMap(Map<String, Object> superProperties) {
        if (hasOptedOutTracking()) return;
        if (null == superProperties) {
            MPLog.e(LOGTAG, "registerSuperPropertiesOnceMap does not accept null properties");
            return;
        }

        try {
            registerSuperPropertiesOnce(new JSONObject(superProperties));
        } catch (NullPointerException e) {
            MPLog.w(LOGTAG, "Can't have null keys in the properties of registerSuperPropertiesOnce!");
        }
    }

    /**
     * Register super properties for events, only if no other super property with the
     * same names has already been registered.
     *
     * <p>Calling registerSuperPropertiesOnce will never overwrite existing properties.
     *
     * @param superProperties A JSONObject containing the super properties to register.
     * @see #registerSuperProperties(JSONObject)
     */
    public void registerSuperPropertiesOnce(JSONObject superProperties) {
        if (hasOptedOutTracking()) return;
        mPersistentIdentity.registerSuperPropertiesOnce(superProperties);
    }

    /**
     * Erase all currently registered superProperties.
     *
     * <p>Future tracking calls to Mmp will not contain the specific
     * superProperties registered before the clearSuperProperties method was called.
     *
     * <p>To remove a single superProperty, use {@link #unregisterSuperProperty(String)}
     *
     * @see #registerSuperProperties(JSONObject)
     */
    public void clearSuperProperties() {
        mPersistentIdentity.clearSuperProperties();
    }

    /**
     * Updates super properties in place. Given a SuperPropertyUpdate object, will
     * pass the current values of SuperProperties to that update and replace all
     * results with the return value of the update. Updates are synchronized on
     * the underlying super properties store, so they are guaranteed to be thread safe
     * (but long running updates may slow down your tracking.)
     *
     * @param update A function from one set of super properties to another. The update should not return null.
     */
    public void updateSuperProperties(SuperPropertyUpdate update) {
        if (hasOptedOutTracking()) return;
        mPersistentIdentity.updateSuperProperties(update);
    }

    /**
     * Set the group this user belongs to.
     *
     * @param groupKey The property name associated with this group type (must already have been set up).
     * @param groupID The group the user belongs to.
     */
    public void setGroup(String groupKey, Object groupID) {
        if (hasOptedOutTracking()) return;

        List<Object> groupIDs = new ArrayList<>(1);
        groupIDs.add(groupID);
        setGroup(groupKey, groupIDs);
    }

    /**
     * Set the groups this user belongs to.
     *
     * @param groupKey The property name associated with this group type (must already have been set up).
     * @param groupIDs The list of groups the user belongs to.
     */
    public void setGroup(String groupKey, List<Object> groupIDs) {
        if (hasOptedOutTracking()) return;

        JSONArray vals = new JSONArray();

        for (Object s : groupIDs) {
            if (s == null) {
                MPLog.w(LOGTAG, "groupID must be non-null");
            } else {
                vals.put(s);
            }
        }

        try {
            registerSuperProperties((new JSONObject()).put(groupKey, vals));
            mPeople.set(groupKey, vals);
        } catch (JSONException e) {
            MPLog.w(LOGTAG, "groupKey must be non-null");
        }
    }

    /**
     * Add a group to this user's membership for a particular group key
     *
     * @param groupKey The property name associated with this group type (must already have been set up).
     * @param groupID The new group the user belongs to.
     */
    public void addGroup(final String groupKey, final Object groupID) {
        if (hasOptedOutTracking()) return;

        updateSuperProperties(new SuperPropertyUpdate() {
            public JSONObject update(JSONObject in) {
                try {
                    in.accumulate(groupKey, groupID);
                } catch (JSONException e) {
                    MPLog.e(LOGTAG, "Failed to add groups superProperty", e);
                }

                return in;
            }
        });

        // This is a best effort--if the people property is not already a list, this call does nothing.
        mPeople.union(groupKey, (new JSONArray()).put(groupID));
    }

    /**
     * Remove a group from this user's membership for a particular group key
     *
     * @param groupKey The property name associated with this group type (must already have been set up).
     * @param groupID The group value to remove.
     */
    public void removeGroup(final String groupKey, final Object groupID) {
        if (hasOptedOutTracking()) return;

        updateSuperProperties(new SuperPropertyUpdate() {
            public JSONObject update(JSONObject in) {
                try {
                    JSONArray vals = in.getJSONArray(groupKey);
                    JSONArray newVals = new JSONArray();

                    if (vals.length() <= 1) {
                        in.remove(groupKey);

                        // This is a best effort--we can't guarantee people and super properties match
                        mPeople.unset(groupKey);
                    } else {

                        for (int i = 0; i < vals.length(); i++) {
                            if (!vals.get(i).equals(groupID)) {
                                newVals.put(vals.get(i));
                            }
                        }

                        in.put(groupKey, newVals);

                        // This is a best effort--we can't guarantee people and super properties match
                        // If people property is not a list, this call does nothing.
                        mPeople.remove(groupKey, groupID);
                    }
                } catch (JSONException e) {
                    in.remove(groupKey);

                    // This is a best effort--we can't guarantee people and super properties match
                    mPeople.unset(groupKey);
                }

                return in;
            }
        });
    }


    /**
     * Returns a Mmp.People object that can be used to set and increment
     * People Analytics properties.
     *
     * @return an instance of {@link People} that you can use to update
     *     records in Mmp People Analytics and manage Mmp Firebase Cloud Messaging notifications.
     */
    public People getPeople() {
        return mPeople;
    }

    /**
     * Returns a Mmp.Group object that can be used to set and increment
     * Group Analytics properties.
     *
     * @param groupKey String identifying the type of group (must be already in use as a group key)
     * @param groupID Object identifying the specific group
     * @return an instance of {@link Group} that you can use to update
     *     records in Mmp Group Analytics
     */
    public Group getGroup(String groupKey, Object groupID) {
        String mapKey = makeMapKey(groupKey, groupID);
        GroupImpl group = mGroups.get(mapKey);

        if (group == null) {
            group = new GroupImpl(groupKey, groupID);
            mGroups.put(mapKey, group);
        }

        if (!(group.mGroupKey.equals(groupKey) && group.mGroupID.equals(groupID))) {
            // we hit a map key collision, return a new group with the correct key and ID
            MPLog.i(LOGTAG, "groups map key collision " + mapKey);
            group = new GroupImpl(groupKey, groupID);
            mGroups.put(mapKey, group);
        }

        return group;
    }

    private String makeMapKey(String groupKey, Object groupID) {
        return groupKey + '_' + groupID;
    }

    /**
     * Clears tweaks and all distinct_ids, superProperties, and push registrations from persistent storage.
     * Will not clear referrer information.
     */
    public void reset() {
        // Will clear distinct_ids, superProperties, notifications, experiments,
        // and waiting People Analytics properties. Will have no effect
        // on messages already queued to send with AnalyticsMessages.
        mPersistentIdentity.clearPreferences();
        getAnalyticsMessages().clearAnonymousUpdatesMessage(new AnalyticsMessages.MmpDescription(mToken));
        identify(getDistinctId(), false);
        mConnectIntegrations.reset();
        mUpdatesFromMmp.storeVariants(new JSONArray());
        mUpdatesFromMmp.applyPersistedUpdates();
        flush();
    }

    /**
     * Returns an unmodifiable map that contains the device description properties
     * that will be sent to Mmp. These are not all of the default properties,
     * but are a subset that are dependant on the user's device or installed version
     * of the host application, and are guaranteed not to change while the app is running.
     *
     * @return Map containing the device description properties that are sent to Mmp.
     */
    public Map<String, String> getDeviceInfo() {
        return mDeviceInfo;
    }

    /**
     * Use this method to opt-out a user from tracking. Events and people updates that haven't been
     * flushed yet will be deleted. Use {@link #flush()} before calling this method if you want
     * to send all the queues to Mmp before.
     *
     * This method will also remove any user-related information from the device.
     */
    public void optOutTracking() {
        getAnalyticsMessages().emptyTrackingQueues(new AnalyticsMessages.MmpDescription(mToken));
        if (getPeople().isIdentified()) {
            getPeople().deleteUser();
            getPeople().clearCharges();
        }
        mPersistentIdentity.clearPreferences();
        synchronized (mEventTimings) {
            mEventTimings.clear();
            mPersistentIdentity.clearTimeEvents();
        }
        mPersistentIdentity.clearReferrerProperties();
        mPersistentIdentity.setOptOutTracking(true, mToken);
    }

    /**
     * Use this method to opt-in an already opted-out user from tracking. People updates and track
     * calls will be sent to Mmp after using this method.
     * This method will internally track an opt-in event to your project. If you want to identify
     * the opt-in event and/or pass properties to the event, see {@link #optInTracking(String)} and
     * {@link #optInTracking(String, JSONObject)}
     *
     * See also {@link #optOutTracking()}.
     */
    public void optInTracking() {
        optInTracking(null, null);
    }

    /**
     * Use this method to opt-in an already opted-out user from tracking. People updates and track
     * calls will be sent to Mmp after using this method.
     * This method will internally track an opt-in event to your project.
     *
     * @param distinctId Optional string to use as the distinct ID for events.
     *                   This will call {@link #identify(String)}.
     *                   If you use people profiles make sure you manually call
     *                   {@link People#identify(String)} after this method.
     *
     * See also {@link #optInTracking(String)}, {@link #optInTracking(String, JSONObject)} and
     *  {@link #optOutTracking()}.
     */
    public void optInTracking(String distinctId) {
        optInTracking(distinctId, null);
    }

    /**
     * Use this method to opt-in an already opted-out user from tracking. People updates and track
     * calls will be sent to Mmp after using this method.
     * This method will internally track an opt-in event to your project.
     *
     * @param distinctId Optional string to use as the distinct ID for events.
     *                   This will call {@link #identify(String)}.
     *                   If you use people profiles make sure you manually call
     *                   {@link People#identify(String)} after this method.
     * @param properties Optional JSONObject that could be passed to add properties to the
     *                   opt-in event that is sent to Mmp.
     *
     * See also {@link #optInTracking()} and {@link #optOutTracking()}.
     */
    public void optInTracking(String distinctId, JSONObject properties) {
        mPersistentIdentity.setOptOutTracking(false, mToken);
        if (distinctId != null) {
            identify(distinctId);
        }
        track("$opt_in", properties);
    }
    /**
     * Will return true if the user has opted out from tracking. See {@link #optOutTracking()} and
     * {@link MmpAPI#getInstance(Context, String, boolean, JSONObject)} for more information.
     *
     * @return true if user has opted out from tracking. Defaults to false.
     */
    public boolean hasOptedOutTracking() {
        return mPersistentIdentity.getOptOutTracking(mToken);
    }

    /**
     * Core interface for using Mmp People Analytics features.
     * You can get an instance by calling {@link MmpAPI#getPeople()}
     *
     * <p>The People object is used to update properties in a user's People Analytics record,
     * and to manage the receipt of push notifications sent via Mmp Engage.
     * For this reason, it's important to call {@link #identify(String)} on the People
     * object before you work with it. Once you call identify, the user identity will
     * persist across stops and starts of your application, until you make another
     * call to identify using a different id.
     *
     * A typical use case for the People object might look like this:
     *
     * <pre>
     * {@code
     *
     * public class MainActivity extends Activity {
     *      MmpAPI mMmp;
     *
     *      public void onCreate(Bundle saved) {
     *          mMmp = MmpAPI.getInstance(this, "YOUR MMP API TOKEN");
     *          mMmp.getPeople().identify("A UNIQUE ID FOR THIS USER");
     *          ...
     *      }
     *
     *      public void userUpdatedJobTitle(String newTitle) {
     *          mMmp.getPeople().set("Job Title", newTitle);
     *          ...
     *      }
     *
     *      public void onDestroy() {
     *          mMmp.flush();
     *          super.onDestroy();
     *      }
     * }
     *
     * }
     * </pre>
     *
     * @see MmpAPI
     */
    public interface People {
        /**
         * Associate future calls to {@link #set(JSONObject)}, {@link #increment(Map)},
         * {@link #append(String, Object)}, etc... with a particular People Analytics user.
         *
         * <p>All future calls to the People object will rely on this value to assign
         * and increment properties. The user identification will persist across
         * restarts of your application. We recommend calling
         * People.identify as soon as you know the distinct id of the user.
         *
         * @param distinctId a String that uniquely identifies the user. Users identified with
         *     the same distinct id will be considered to be the same user in Mmp,
         *     across all platforms and devices. We recommend choosing a distinct id
         *     that is meaningful to your other systems (for example, a server-side account
         *     identifier), and using the same distinct id for both calls to People.identify
         *     and {@link MmpAPI#identify(String)}
         *
         * @see MmpAPI#identify(String)
         */
        public void identify(String distinctId);

        /**
         * Sets a single property with the given name and value for this user.
         * The given name and value will be assigned to the user in Mmp People Analytics,
         * possibly overwriting an existing property with the same name.
         *
         * @param propertyName The name of the Mmp property. This must be a String, for example "Zip Code"
         * @param value The value of the Mmp property. For "Zip Code", this value might be the String "90210"
         */
        public void set(String propertyName, Object value);

        /**
         * Set a collection of properties on the identified user all at once.
         *
         * @param properties a Map containing the collection of properties you wish to apply
         *      to the identified user. Each key in the Map will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         *
         * See also {@link #set(org.json.JSONObject)}
         */
        public void setMap(Map<String, Object> properties);

        /**
         * Set a collection of properties on the identified user all at once.
         *
         * @param properties a JSONObject containing the collection of properties you wish to apply
         *      to the identified user. Each key in the JSONObject will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         */
        public void set(JSONObject properties);

        /**
         * Works just like {@link People#set(String, Object)}, except it will not overwrite existing property values. This is useful for properties like "First login date".
         *
         * @param propertyName The name of the Mmp property. This must be a String, for example "Zip Code"
         * @param value The value of the Mmp property. For "Zip Code", this value might be the String "90210"
         */
        public void setOnce(String propertyName, Object value);

        /**
         * Like {@link People#set(String, Object)}, but will not set properties that already exist on a record.
         *
         * @param properties a Map containing the collection of properties you wish to apply
         *      to the identified user. Each key in the Map will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         *
         * See also {@link #setOnce(org.json.JSONObject)}
         */
        public void setOnceMap(Map<String, Object> properties);

        /**
         * Like {@link People#set(String, Object)}, but will not set properties that already exist on a record.
         *
         * @param properties a JSONObject containing the collection of properties you wish to apply
         *      to the identified user. Each key in the JSONObject will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         */
        public void setOnce(JSONObject properties);

        /**
         * Add the given amount to an existing property on the identified user. If the user does not already
         * have the associated property, the amount will be added to zero. To reduce a property,
         * provide a negative number for the value.
         *
         * @param name the People Analytics property that should have its value changed
         * @param increment the amount to be added to the current value of the named property
         *
         * @see #increment(Map)
         */
        public void increment(String name, double increment);

        /**
         * Merge a given JSONObject into the object-valued property named name. If the user does not
         * already have the associated property, an new property will be created with the value of
         * the given updates. If the user already has a value for the given property, the updates will
         * be merged into the existing value, with key/value pairs in updates taking precedence over
         * existing key/value pairs where the keys are the same.
         *
         * @param name the People Analytics property that should have the update merged into it
         * @param updates a JSONObject with keys and values that will be merged into the property
         */
        public void merge(String name, JSONObject updates);

        /**
         * Change the existing values of multiple People Analytics properties at once.
         *
         * <p>If the user does not already have the associated property, the amount will
         * be added to zero. To reduce a property, provide a negative number for the value.
         *
         * @param properties A map of String properties names to Long amounts. Each
         *     property associated with a name in the map will have its value changed by the given amount
         *
         * @see #increment(String, double)
         */
        public void increment(Map<String, ? extends Number> properties);

        /**
         * Appends a value to a list-valued property. If the property does not currently exist,
         * it will be created as a list of one element. If the property does exist and doesn't
         * currently have a list value, the append will be ignored.
         * @param name the People Analytics property that should have it's value appended to
         * @param value the new value that will appear at the end of the property's list
         */
        public void append(String name, Object value);

        /**
         * Adds values to a list-valued property only if they are not already present in the list.
         * If the property does not currently exist, it will be created with the given list as it's value.
         * If the property exists and is not list-valued, the union will be ignored.
         *
         * @param name name of the list-valued property to set or modify
         * @param value an array of values to add to the property value if not already present
         */
        void union(String name, JSONArray value);

        /**
         * Remove value from a list-valued property only if they are already present in the list.
         * If the property does not currently exist, the remove will be ignored.
         * If the property exists and is not list-valued, the remove will be ignored.
         * @param name the People Analytics property that should have it's value removed from
         * @param value the value that will be removed from the property's list
         */
        public void remove(String name, Object value);

        /**
         * permanently removes the property with the given name from the user's profile
         * @param name name of a property to unset
         */
        void unset(String name);

        /**
         * Track a revenue transaction for the identified people profile.
         *
         * @param amount the amount of money exchanged. Positive amounts represent purchases or income from the customer, negative amounts represent refunds or payments to the customer.
         * @param properties an optional collection of properties to associate with this transaction.
         */
        public void trackCharge(double amount, JSONObject properties);

        /**
         * Permanently clear the whole transaction history for the identified people profile.
         */
        public void clearCharges();

        /**
         * Permanently deletes the identified user's record from People Analytics.
         *
         * <p>Calling deleteUser deletes an entire record completely. Any future calls
         * to People Analytics using the same distinct id will create and store new values.
         */
        public void deleteUser();

        /**
         * Checks if the people profile is identified or not.
         *
         * @return Whether the current user is identified or not.
         */
        public boolean isIdentified();


        /**
         * Retrieves current Firebase Cloud Messaging token.
         *
         * <p>{@link People#getPushRegistrationId} should only be called after {@link #identify(String)} has been called.
         *
         * @return FCM push token or null if the user has not been registered in FCM.
         *
         * @see #setPushRegistrationId(String)
         * @see #clearPushRegistrationId
         */
        public String getPushRegistrationId();

        /**
         * Manually send a Firebase Cloud Messaging token to Mmp.
         *
         * <p>If you are handling Firebase Cloud Messages in your own application, but would like to
         * allow Mmp to handle messages originating from Mmp campaigns, you should
         * call setPushRegistrationId with the FCM token.
         *
         * <p>setPushRegistrationId should only be called after {@link #identify(String)} has been called.
         *
         * Optionally, applications that call setPushRegistrationId should also call
         * {@link #clearPushRegistrationId()} when they unregister the device id.
         *
         * @param token Firebase Cloud Messaging token
         *
         * @see #clearPushRegistrationId()
         */
        public void setPushRegistrationId(String token);

        /**
         * Manually clear all current Firebase Cloud Messaging tokens from Mmp.
         *
         * <p>{@link People#clearPushRegistrationId} should only be called after {@link #identify(String)} has been called.
         *
         * <p>In general, all applications that call {@link #setPushRegistrationId(String)} should include a call to
         * clearPushRegistrationId.
         */
        public void clearPushRegistrationId();

        /**
         * Manually clear a single Firebase Cloud Messaging token from Mmp.
         *
         * <p>{@link People#clearPushRegistrationId} should only be called after {@link #identify(String)} has been called.
         *
         * <p>In general, all applications that call {@link #setPushRegistrationId(String)} should include a call to
         * clearPushRegistrationId.
         *
         * @param registrationId The device token you want to remove.
         */
        public void clearPushRegistrationId(String registrationId);

        /**
         * Returns the string id currently being used to uniquely identify the user associated
         * with events sent using {@link People#set(String, Object)} and {@link People#increment(String, double)}.
         * If no calls to {@link People#identify(String)} have been made, this method will return null.
         *
         * <p>The id returned by getDistinctId is independent of the distinct id used to identify
         * any events sent with {@link MmpAPI#track(String, JSONObject)}. To read and write that identifier,
         * use {@link MmpAPI#identify(String)} and {@link MmpAPI#getDistinctId()}.
         *
         * @return The distinct id associated with updates to People Analytics
         *
         * @see People#identify(String)
         * @see MmpAPI#getDistinctId()
         */
        public String getDistinctId();

        /**
         * Shows an in-app notification to the user if one is available. If the notification
         * is a mini notification, this method will attach and remove a Fragment to parent.
         * The lifecycle of the Fragment will be handled entirely by the Mmp library.
         *
         * <p>If the notification is a takeover notification, a TakeoverInAppActivity will be launched to
         * display the Takeover notification.
         *
         * <p>It is safe to call this method any time you want to potentially display an in-app notification.
         * This method will be a no-op if there is already an in-app notification being displayed.
         *
         * <p>This method is a no-op in environments with
         * Android API before JellyBean/API level 16.
         *
         * @param parent the Activity that the mini notification will be displayed in, or the Activity
         * that will be used to launch TakeoverInAppActivity for the takeover notification.
         */
        public void showNotificationIfAvailable(Activity parent);

        /**
         * Applies A/B test changes, if they are present. By default, your application will attempt
         * to join available experiments any time an activity is resumed, but you can disable this
         * automatic behavior by adding the following tag to the &lt;application&gt; tag in your AndroidManifest.xml
         * {@code
         *     <meta-data android:name="com.mmp.android.MPConfig.AutoShowMmpUpdates"
         *                android:value="false" />
         * }
         *
         * If you disable AutoShowMmpUpdates, you'll need to call joinExperimentIfAvailable to
         * join or clear existing experiments. If you want to display a loading screen or otherwise
         * wait for experiments to load from the server before you apply them, you can use
         * {@link #addOnMmpUpdatesReceivedListener(OnMmpUpdatesReceivedListener)} to
         * be informed that new experiments are ready.
         */
        public void joinExperimentIfAvailable();

        /**
         * Shows the given in-app notification to the user. Display will occur just as if the
         * notification was shown via showNotificationIfAvailable. In most cases, it is
         * easier and more efficient to use showNotificationIfAvailable.
         *
         * @param notif the {@link com.mmp.android.mpmetrics.InAppNotification} to show
         *
         * @param parent the Activity that the mini notification will be displayed in, or the Activity
         * that will be used to launch TakeoverInAppActivity for the takeover notification.
         */
        public void showGivenNotification(InAppNotification notif, Activity parent);

        /**
         * Sends an event to Mmp that includes the automatic properties associated
         * with the given notification. In most cases this is not required, unless you're
         * not showing notifications using the library-provided in views and activities.
         *
         * @param eventName the name to use when the event is tracked.
         *
         * @param notif the {@link com.mmp.android.mpmetrics.InAppNotification} associated with the event you'd like to track.
         *
         * @param properties additional properties to be tracked with the event.
         */
        public void trackNotification(String eventName, InAppNotification notif, JSONObject properties);

        /**
         * Returns an InAppNotification object if one is available and being held by the library, or null if
         * no notification is currently available. Callers who want to display in-app notifications should call this
         * method periodically. A given InAppNotification will be returned only once from this method, so callers
         * should be ready to consume any non-null return value.
         *
         * <p>This function will return quickly, and will not cause any communication with
         * Mmp's servers, so it is safe to call this from the UI thread.
         *
         * Note: you must call call {@link People#trackNotificationSeen(InAppNotification)} or you will
         * receive the same {@link com.mmp.android.mpmetrics.InAppNotification} again the
         * next time notifications are refreshed from Mmp's servers (on identify, or when
         * your app is destroyed and re-created)
         *
         * @return an InAppNotification object if one is available, null otherwise.
         */
        public InAppNotification getNotificationIfAvailable();

        /**
         * Tells MixPanel that you have handled an {@link com.mmp.android.mpmetrics.InAppNotification}
         * in the case where you are manually dealing with your notifications ({@link People#getNotificationIfAvailable()}).
         *
         * Note: if you do not acknowledge the notification you will receive it again each time
         * you call {@link People#identify(String)} and then call {@link People#getNotificationIfAvailable()}
         *
         * @param notif the notification to track (no-op on null)
         */
        void trackNotificationSeen(InAppNotification notif);

        /**
         * Shows an in-app notification identified by id. The behavior of this is otherwise identical to
         * {@link People#showNotificationIfAvailable(Activity)}.
         *
         * @param id the id of the InAppNotification you wish to show.
         * @param parent  the Activity that the mini notification will be displayed in, or the Activity
         * that will be used to launch TakeoverInAppActivity for the takeover notification.
         */
        public void showNotificationById(int id, final Activity parent);

        /**
         * Return an instance of Mmp people with a temporary distinct id.
         * Instances returned by withIdentity will not check decide with the given distinctId.
         *
         * @param distinctId Unique identifier (distinct_id) that the people object will have
         *
         * @return An instance of {@link MmpAPI.People} with the specified distinct_id
         */
        public People withIdentity(String distinctId);

        /**
         * Adds a new listener that will receive a callback when new updates from Mmp
         * (like in-app notifications or A/B test experiments) are discovered. Most users of the library
         * will not need this method since in-app notifications and experiments are
         * applied automatically to your application by default.
         *
         * <p>The given listener will be called when a new batch of updates is detected. Handlers
         * should be prepared to handle the callback on an arbitrary thread.
         *
         * <p>The listener will be called when new in-app notifications or experiments
         * are detected as available. That means you wait to call
         * {@link People#showNotificationIfAvailable(Activity)}, and {@link People#joinExperimentIfAvailable()}
         * to show content and updates that have been delivered to your app. (You can also call these
         * functions whenever else you would like, they're inexpensive and will do nothing if no
         * content is available.)
         *
         * @param listener the listener to add
         */
        public void addOnMmpUpdatesReceivedListener(OnMmpUpdatesReceivedListener listener);

        /**
         * Removes a listener previously registered with addOnMmpUpdatesReceivedListener.
         *
         * @param listener the listener to add
         */
        public void removeOnMmpUpdatesReceivedListener(OnMmpUpdatesReceivedListener listener);

        /**
         * Sets the listener that will receive a callback when new Tweaks from Mmp are discovered. Most
         * users of the library will not need this method, since Tweaks are applied automatically to your
         * application by default.
         *
         * <p>The given listener will be called when a new batch of Tweaks is applied. Handlers
         * should be prepared to handle the callback on an arbitrary thread.
         *
         * <p>The listener will be called when new Tweaks are detected as available. That means the listener
         * will get called once {@link People#joinExperimentIfAvailable()} has successfully applied the changes.
         *
         * @param listener the listener to set
         */
        public void addOnMmpTweaksUpdatedListener(OnMmpTweaksUpdatedListener listener);

        /**
         * Removes the listener previously registered with addOnMmpTweaksUpdatedListener.
         *
         * @param listener Listener you that will be removed.
         */
        public void removeOnMmpTweaksUpdatedListener(OnMmpTweaksUpdatedListener listener);

    }

    /**
     * Core interface for using Mmp Group Analytics features.
     * You can get an instance by calling {@link MmpAPI#getGroup(String, Object)}
     *
     * <p>The Group object is used to update properties in a group's Group Analytics record.
     *
     * A typical use case for the Group object might look like this:
     *
     * <pre>
     * {@code
     *
     * public class MainActivity extends Activity {
     *      MmpAPI mMmp;
     *
     *      public void onCreate(Bundle saved) {
     *          mMmp = MmpAPI.getInstance(this, "YOUR MMP API TOKEN");
     *          ...
     *      }
     *
     *      public void companyPlanTypeChanged(string company, String newPlan) {
     *          mMmp.getGroup("Company", company).set("Plan Type", newPlan);
     *          ...
     *      }
     *
     *      public void onDestroy() {
     *          mMmp.flush();
     *          super.onDestroy();
     *      }
     * }
     *
     * }
     * </pre>
     *
     * @see MmpAPI
     */
    public interface Group {
        /**
         * Sets a single property with the given name and value for this group.
         * The given name and value will be assigned to the user in Mmp Group Analytics,
         * possibly overwriting an existing property with the same name.
         *
         * @param propertyName The name of the Mmp property. This must be a String, for example "Zip Code"
         * @param value The value of the Mmp property. For "Zip Code", this value might be the String "90210"
         */
        public void set(String propertyName, Object value);

        /**
         * Set a collection of properties on the identified group all at once.
         *
         * @param properties a Map containing the collection of properties you wish to apply
         *      to the identified group. Each key in the Map will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         *
         * See also {@link #set(org.json.JSONObject)}
         */
        public void setMap(Map<String, Object> properties);

        /**
         * Set a collection of properties on the identified group all at once.
         *
         * @param properties a JSONObject containing the collection of properties you wish to apply
         *      to the identified group. Each key in the JSONObject will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         */
        public void set(JSONObject properties);

        /**
         * Works just like {@link Group#set(String, Object)}, except it will not overwrite existing property values. This is useful for properties like "First login date".
         *
         * @param propertyName The name of the Mmp property. This must be a String, for example "Zip Code"
         * @param value The value of the Mmp property. For "Zip Code", this value might be the String "90210"
         */
        public void setOnce(String propertyName, Object value);

        /**
         * Like {@link Group#set(String, Object)}, but will not set properties that already exist on a record.
         *
         * @param properties a Map containing the collection of properties you wish to apply
         *      to the identified group. Each key in the Map will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         *
         * See also {@link #setOnce(org.json.JSONObject)}
         */
        public void setOnceMap(Map<String, Object> properties);

        /**
         * Like {@link Group#set(String, Object)}, but will not set properties that already exist on a record.
         *
         * @param properties a JSONObject containing the collection of properties you wish to apply
         *      to this group. Each key in the JSONObject will be associated with
         *      a property name, and the value of that key will be assigned to the property.
         */
        public void setOnce(JSONObject properties);

        /**
         * Adds values to a list-valued property only if they are not already present in the list.
         * If the property does not currently exist, it will be created with the given list as its value.
         * If the property exists and is not list-valued, the union will be ignored.
         *
         * @param name name of the list-valued property to set or modify
         * @param value an array of values to add to the property value if not already present
         */
        void union(String name, JSONArray value);

        /**
         * Remove value from a list-valued property only if it is already present in the list.
         * If the property does not currently exist, the remove will be ignored.
         * If the property exists and is not list-valued, the remove will be ignored.
         *
         * @param name the Group Analytics list-valued property that should have a value removed
         * @param value the value that will be removed from the list
         */
        public void remove(String name, Object value);

        /**
         * Permanently removes the property with the given name from the group's profile
         * @param name name of a property to unset
         */
        void unset(String name);


        /**
         * Permanently deletes this group's record from Group Analytics.
         *
         * <p>Calling deleteGroup deletes an entire record completely. Any future calls
         * to Group Analytics using the same group value will create and store new values.
         */
        public void deleteGroup();
    }

    /**
     * Attempt to register MmpActivityLifecycleCallbacks to the application's event lifecycle.
     * Once registered, we can automatically check for and show in-app notifications
     * when any Activity is opened.
     *
     * This is only available if the android version is >= 16. You can disable livecycle callbacks by setting
     * com.mmp.android.MPConfig.AutoShowMmpUpdates to false in your AndroidManifest.xml
     *
     * This function is automatically called when the library is initialized unless you explicitly
     * set com.mmp.android.MPConfig.AutoShowMmpUpdates to false in your AndroidManifest.xml
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    /* package */ void registerMmpActivityLifecycleCallbacks() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (mContext.getApplicationContext() instanceof Application) {
                final Application app = (Application) mContext.getApplicationContext();
                mMmpActivityLifecycleCallbacks = new MmpActivityLifecycleCallbacks(this, mConfig);
                app.registerActivityLifecycleCallbacks(mMmpActivityLifecycleCallbacks);
            } else {
                MPLog.i(LOGTAG, "Context is not an Application, Mmp will not automatically show in-app notifications or A/B test experiments. We won't be able to automatically flush on an app background.");
            }
        }
    }

    /**
     * Based on the application's event lifecycle this method will determine whether the app
     * is running in the foreground or not.
     *
     * If your build version is below 14 this method will always return false.
     *
     * @return True if the app is running in the foreground.
     */
    public boolean isAppInForeground() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (mMmpActivityLifecycleCallbacks != null) {
                return mMmpActivityLifecycleCallbacks.isInForeground();
            }
        } else {
            MPLog.e(LOGTAG, "Your build version is below 14. This method will always return false.");
        }
        return false;
    }

    /* package */ void onBackground() {
        if (mConfig.getFlushOnBackground()) {
            flush();
        }
        mUpdatesFromMmp.applyPersistedUpdates();
    }

    /* package */ void onForeground() {
        mSessionMetadata.initSession();
    }

    // Package-level access. Used (at least) by MmpFCMMessagingService
    // when OS-level events occur.
    /* package */ interface InstanceProcessor {
        public void process(MmpAPI m);
    }

    /* package */ static void allInstances(InstanceProcessor processor) {
        synchronized (sInstanceMap) {
            for (final Map<Context, MmpAPI> contextInstances : sInstanceMap.values()) {
                for (final MmpAPI instance : contextInstances.values()) {
                    processor.process(instance);
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////
    // Conveniences for testing. These methods should not be called by
    // non-test client code.

    /* package */ AnalyticsMessages getAnalyticsMessages() {
        return AnalyticsMessages.getInstance(mContext);
    }

    /* package */ DecideMessages getDecideMessages() {
        return mDecideMessages;
    }

    /* package */ PersistentIdentity getPersistentIdentity(final Context context, Future<SharedPreferences> referrerPreferences, final String token) {
        final SharedPreferencesLoader.OnPrefsLoadedListener listener = new SharedPreferencesLoader.OnPrefsLoadedListener() {
            @Override
            public void onPrefsLoaded(SharedPreferences preferences) {
                final String distinctId = PersistentIdentity.getPeopleDistinctId(preferences);
                if (null != distinctId) {
                    pushWaitingPeopleRecord(distinctId);
                }
            }
        };

        final String prefsName = "com.mmp.android.mpmetrics.MmpAPI_" + token;
        final Future<SharedPreferences> storedPreferences = sPrefsLoader.loadPreferences(context, prefsName, listener);

        final String timeEventsPrefsName = "com.mmp.android.mpmetrics.MmpAPI.TimeEvents_" + token;
        final Future<SharedPreferences> timeEventsPrefs = sPrefsLoader.loadPreferences(context, timeEventsPrefsName, null);

        final String mmpPrefsName = "com.mmp.android.mpmetrics.Mmp";
        final Future<SharedPreferences> mmpPrefs = sPrefsLoader.loadPreferences(context, mmpPrefsName, null);

        return new PersistentIdentity(referrerPreferences, storedPreferences, timeEventsPrefs, mmpPrefs);
    }

    /* package */ DecideMessages constructDecideUpdates(final String token, final DecideMessages.OnNewResultsListener listener, UpdatesFromMmp updatesFromMmp) {
        return new DecideMessages(mContext, token, listener, updatesFromMmp, mPersistentIdentity.getSeenCampaignIds());
    }

    /* package */ UpdatesListener constructUpdatesListener() {
        if (Build.VERSION.SDK_INT < MPConfig.UI_FEATURES_MIN_API) {
            MPLog.i(LOGTAG, "Notifications are not supported on this Android OS Version");
            return new UnsupportedUpdatesListener();
        } else {
            return new SupportedUpdatesListener();
        }
    }

    /* package */ UpdatesFromMmp constructUpdatesFromMmp(final Context context, final String token) {
        if (Build.VERSION.SDK_INT < MPConfig.UI_FEATURES_MIN_API) {
            MPLog.i(LOGTAG, "SDK version is lower than " + MPConfig.UI_FEATURES_MIN_API + ". Web Configuration, A/B Testing, and Dynamic Tweaks are disabled.");
            return new NoOpUpdatesFromMmp(sSharedTweaks);
        } else if (mConfig.getDisableViewCrawler() || Arrays.asList(mConfig.getDisableViewCrawlerForProjects()).contains(token)) {
            MPLog.i(LOGTAG, "DisableViewCrawler is set to true. Web Configuration, A/B Testing, and Dynamic Tweaks are disabled.");
            return new NoOpUpdatesFromMmp(sSharedTweaks);
        } else {
            return new ViewCrawler(mContext, mToken, this, sSharedTweaks);
        }
    }

    /* package */ TrackingDebug constructTrackingDebug() {
        if (mUpdatesFromMmp instanceof ViewCrawler) {
            return (TrackingDebug) mUpdatesFromMmp;
        }

        return null;
    }

    /* package */ boolean sendAppOpen() {
        return !mConfig.getDisableAppOpenEvent();
    }

    ///////////////////////

    private class PeopleImpl implements People {
        @Override
        public void identify(String distinctId) {
            if (hasOptedOutTracking()) return;
            if (distinctId == null) {
                MPLog.e(LOGTAG, "Can't identify with null distinct_id.");
                return;
            }
            synchronized (mPersistentIdentity) {
                mPersistentIdentity.setPeopleDistinctId(distinctId);
                mDecideMessages.setDistinctId(distinctId);
            }
            pushWaitingPeopleRecord(distinctId);
         }

        @Override
        public void setMap(Map<String, Object> properties) {
            if (hasOptedOutTracking()) return;
            if (null == properties) {
                MPLog.e(LOGTAG, "setMap does not accept null properties");
                return;
            }

            try {
                set(new JSONObject(properties));
            } catch (NullPointerException e) {
                MPLog.w(LOGTAG, "Can't have null keys in the properties of setMap!");
            }
        }

        @Override
        public void set(JSONObject properties) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject sendProperties = new JSONObject(mDeviceInfo);
                for (final Iterator<?> iter = properties.keys(); iter.hasNext();) {
                    final String key = (String) iter.next();
                    sendProperties.put(key, properties.get(key));
                }

                final JSONObject message = stdPeopleMessage("$set", sendProperties);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception setting people properties", e);
            }
        }

        @Override
        public void set(String property, Object value) {
            if (hasOptedOutTracking()) return;
            try {
                set(new JSONObject().put(property, value));
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "set", e);
            }
        }

        @Override
        public void setOnceMap(Map<String, Object> properties) {
            if (hasOptedOutTracking()) return;
            if (null == properties) {
                MPLog.e(LOGTAG, "setOnceMap does not accept null properties");
                return;
            }
            try {
                setOnce(new JSONObject(properties));
            } catch (NullPointerException e) {
                MPLog.w(LOGTAG, "Can't have null keys in the properties setOnceMap!");
            }
        }

        @Override
        public void setOnce(JSONObject properties) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject message = stdPeopleMessage("$set_once", properties);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception setting people properties");
            }
        }

        @Override
        public void setOnce(String property, Object value) {
            if (hasOptedOutTracking()) return;
            try {
                setOnce(new JSONObject().put(property, value));
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "set", e);
            }
        }

        @Override
        public void increment(Map<String, ? extends Number> properties) {
            if (hasOptedOutTracking()) return;
            final JSONObject json = new JSONObject(properties);
            try {
                final JSONObject message = stdPeopleMessage("$add", json);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception incrementing properties", e);
            }
        }

        @Override
        // Must be thread safe
        public void merge(String property, JSONObject updates) {
            if (hasOptedOutTracking()) return;
            final JSONObject mergeMessage = new JSONObject();
            try {
                mergeMessage.put(property, updates);
                final JSONObject message = stdPeopleMessage("$merge", mergeMessage);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception merging a property", e);
            }
        }

        @Override
        public void increment(String property, double value) {
            if (hasOptedOutTracking()) return;
            final Map<String, Double> map = new HashMap<String, Double>();
            map.put(property, value);
            increment(map);
        }

        @Override
        public void append(String name, Object value) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject properties = new JSONObject();
                properties.put(name, value);
                final JSONObject message = stdPeopleMessage("$append", properties);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception appending a property", e);
            }
        }

        @Override
        public void union(String name, JSONArray value) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject properties = new JSONObject();
                properties.put(name, value);
                final JSONObject message = stdPeopleMessage("$union", properties);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception unioning a property");
            }
        }

        @Override
        public void remove(String name, Object value) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject properties = new JSONObject();
                properties.put(name, value);
                final JSONObject message = stdPeopleMessage("$remove", properties);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception appending a property", e);
            }
        }

        @Override
        public void unset(String name) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONArray names = new JSONArray();
                names.put(name);
                final JSONObject message = stdPeopleMessage("$unset", names);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception unsetting a property", e);
            }
        }

        @Override
        public InAppNotification getNotificationIfAvailable() {
            return mDecideMessages.getNotification(mConfig.getTestMode());
        }

        @Override
        public void trackNotificationSeen(InAppNotification notif) {
            if (notif == null) return;
            mPersistentIdentity.saveCampaignAsSeen(notif.getId());
            if (hasOptedOutTracking()) return;

            trackNotification("$campaign_delivery", notif, null);
            final MmpAPI.People people = getPeople().withIdentity(getDistinctId());
            if (people != null) {
                final DateFormat dateFormat = new SimpleDateFormat(ENGAGE_DATE_FORMAT_STRING, Locale.US);
                final JSONObject notifProperties = notif.getCampaignProperties();
                try {
                    notifProperties.put("$time", dateFormat.format(new Date()));
                } catch (final JSONException e) {
                    MPLog.e(LOGTAG, "Exception trying to track an in-app notification seen", e);
                }
                people.append("$campaigns", notif.getId());
                people.append("$notifications", notifProperties);
            } else {
                MPLog.e(LOGTAG, "No identity found. Make sure to call getPeople().identify() before showing in-app notifications.");
            }
        }

        @Override
        public void showNotificationIfAvailable(final Activity parent) {
            if (Build.VERSION.SDK_INT < MPConfig.UI_FEATURES_MIN_API) {
                return;
            }

            showGivenOrAvailableNotification(null, parent);
        }

        @Override
        public void showNotificationById(int id, final Activity parent) {
            final InAppNotification notif = mDecideMessages.getNotification(id, mConfig.getTestMode());
            showGivenNotification(notif, parent);
        }

        @Override
        public void showGivenNotification(final InAppNotification notif, final Activity parent) {
            if (notif != null) {
                showGivenOrAvailableNotification(notif, parent);
            }
        }

        @Override
        public void trackNotification(final String eventName, final InAppNotification notif, final JSONObject properties) {
            if (hasOptedOutTracking()) return;
            JSONObject notificationProperties = notif.getCampaignProperties();
            if (properties != null) {
                try {
                    Iterator<String> keyIterator = properties.keys();
                    while (keyIterator.hasNext()) {
                        String key = keyIterator.next();
                        notificationProperties.put(key, properties.get(key));
                    }
                } catch (final JSONException e) {
                    MPLog.e(LOGTAG, "Exception merging provided properties with notification properties", e);
                }
            }
            track(eventName, notificationProperties);
        }

        @Override
        public void joinExperimentIfAvailable() {
            final JSONArray variants = mDecideMessages.getVariants();
            mUpdatesFromMmp.setVariants(variants);
        }

        @Override
        public void trackCharge(double amount, JSONObject properties) {
            if (hasOptedOutTracking()) return;
            final Date now = new Date();
            final DateFormat dateFormat = new SimpleDateFormat(ENGAGE_DATE_FORMAT_STRING, Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            try {
                final JSONObject transactionValue = new JSONObject();
                transactionValue.put("$amount", amount);
                transactionValue.put("$time", dateFormat.format(now));

                if (null != properties) {
                    for (final Iterator<?> iter = properties.keys(); iter.hasNext();) {
                        final String key = (String) iter.next();
                        transactionValue.put(key, properties.get(key));
                    }
                }

                this.append("$transactions", transactionValue);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception creating new charge", e);
            }
        }

        /**
         * Permanently clear the whole transaction history for the identified people profile.
         */
        @Override
        public void clearCharges() {
            this.unset("$transactions");
        }

        @Override
        public void deleteUser() {
            try {
                final JSONObject message = stdPeopleMessage("$delete", JSONObject.NULL);
                recordPeopleMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception deleting a user");
            }
        }

        @Override
        public String getPushRegistrationId() {
            return mPersistentIdentity.getPushId();
        }

        @Override
        // Must be thread safe, will be called from a lot of different threads.
        public void setPushRegistrationId(String registrationId) {
            synchronized (mPersistentIdentity) {
                MPLog.d(LOGTAG, "Setting push token on people profile: " + registrationId);
                mPersistentIdentity.storePushId(registrationId);
                final JSONArray ids = new JSONArray();
                ids.put(registrationId);
                union("$android_devices", ids);
            }
        }

        @Override
        public void clearPushRegistrationId() {
            mPersistentIdentity.clearPushId();
            set("$android_devices", new JSONArray());
        }

        @Override
        public void clearPushRegistrationId(String registrationId) {
            if (registrationId == null) {
                return;
            }

            if (registrationId.equals(mPersistentIdentity.getPushId())) {
                mPersistentIdentity.clearPushId();
            }
            remove("$android_devices", registrationId);
        }


        @Override
        public String getDistinctId() {
            return mPersistentIdentity.getPeopleDistinctId();
        }

        @Override
        public People withIdentity(final String distinctId) {
            if (null == distinctId) {
                return null;
            }
            return new PeopleImpl() {
                @Override
                public String getDistinctId() {
                    return distinctId;
                }

                @Override
                public void identify(String distinctId) {
                    throw new RuntimeException("This MmpPeople object has a fixed, constant distinctId");
                }
            };
        }

        @Override
        public void addOnMmpUpdatesReceivedListener(final OnMmpUpdatesReceivedListener listener) {
            mUpdatesListener.addOnMmpUpdatesReceivedListener(listener);
        }

        @Override
        public void removeOnMmpUpdatesReceivedListener(final OnMmpUpdatesReceivedListener listener) {
            mUpdatesListener.removeOnMmpUpdatesReceivedListener(listener);
        }

        @Override
        public void addOnMmpTweaksUpdatedListener(OnMmpTweaksUpdatedListener listener) {
            if (null == listener) {
                throw new NullPointerException("Listener cannot be null");
            }

            mUpdatesFromMmp.addOnMmpTweaksUpdatedListener(listener);
        }

        @Override
        public void removeOnMmpTweaksUpdatedListener(OnMmpTweaksUpdatedListener listener) {
            mUpdatesFromMmp.removeOnMmpTweaksUpdatedListener(listener);
        }

        private JSONObject stdPeopleMessage(String actionType, Object properties)
                throws JSONException {
            final JSONObject dataObj = new JSONObject();
            final String distinctId = getDistinctId(); // TODO ensure getDistinctId is thread safe
            final String anonymousId = getAnonymousId();
            dataObj.put(actionType, properties);
            dataObj.put("$token", mToken);
            dataObj.put("$time", System.currentTimeMillis());
            dataObj.put("$had_persisted_distinct_id", mPersistentIdentity.getHadPersistedDistinctId());
            if (null != anonymousId) {
                dataObj.put("$device_id", anonymousId);
            }
            if (null != distinctId) {
                dataObj.put("$distinct_id", distinctId);
                dataObj.put("$user_id", distinctId);
            }
            dataObj.put("$mp_metadata", mSessionMetadata.getMetadataForPeople());

            return dataObj;
        }

        private void showGivenOrAvailableNotification(final InAppNotification notifOrNull, final Activity parent) {
            if (Build.VERSION.SDK_INT < MPConfig.UI_FEATURES_MIN_API) {
                MPLog.v(LOGTAG, "Will not show notifications, os version is too low.");
                return;
            }

            parent.runOnUiThread(new Runnable() {
                @Override
                @TargetApi(MPConfig.UI_FEATURES_MIN_API)
                public void run() {
                    final ReentrantLock lock = UpdateDisplayState.getLockObject();
                    lock.lock();
                    try {
                        if (UpdateDisplayState.hasCurrentProposal()) {
                            MPLog.v(LOGTAG, "DisplayState is locked, will not show notifications.");
                            return; // Already being used.
                        }

                        InAppNotification toShow = notifOrNull;
                        if (null == toShow) {
                            toShow = getNotificationIfAvailable();
                        }
                        if (null == toShow) {
                            MPLog.v(LOGTAG, "No notification available, will not show.");
                            return; // Nothing to show
                        }

                        final InAppNotification.Type inAppType = toShow.getType();
                        if (inAppType == InAppNotification.Type.TAKEOVER && !ConfigurationChecker.checkTakeoverInAppActivityAvailable(parent.getApplicationContext())) {
                            MPLog.v(LOGTAG, "Application is not configured to show takeover notifications, none will be shown.");
                            return; // Can't show due to config.
                        }

                        final int highlightColor = ActivityImageUtils.getHighlightColorFromBackground(parent);
                        final UpdateDisplayState.DisplayState.InAppNotificationState proposal =
                                new UpdateDisplayState.DisplayState.InAppNotificationState(toShow, highlightColor);
                        final int intentId = UpdateDisplayState.proposeDisplay(proposal, getDistinctId(), mToken);
                        if (intentId <= 0) {
                            MPLog.e(LOGTAG, "DisplayState Lock in inconsistent state! Please report this issue to Mmp");
                            return;
                        }

                        switch (inAppType) {
                            case MINI: {
                                final UpdateDisplayState claimed = UpdateDisplayState.claimDisplayState(intentId);
                                if (null == claimed) {
                                    MPLog.v(LOGTAG, "Notification's display proposal was already consumed, no notification will be shown.");
                                    return; // Can't claim the display state
                                }
                                final InAppFragment inapp = new InAppFragment();
                                inapp.setDisplayState(
                                    MmpAPI.this,
                                    intentId,
                                    (UpdateDisplayState.DisplayState.InAppNotificationState) claimed.getDisplayState()
                                );
                                inapp.setRetainInstance(true);

                                MPLog.v(LOGTAG, "Attempting to show mini notification.");
                                final FragmentTransaction transaction = parent.getFragmentManager().beginTransaction();
                                transaction.setCustomAnimations(0, R.animator.com_mmp_android_slide_down);
                                transaction.add(android.R.id.content, inapp);

                                try {
                                    transaction.commit();
                                } catch (IllegalStateException e) {
                                    // if the app is in the background or the current activity gets killed, rendering the
                                    // notifiction will lead to a crash
                                    MPLog.v(LOGTAG, "Unable to show notification.");
                                    mDecideMessages.markNotificationAsUnseen(toShow);
                                }
                            }
                            break;
                            case TAKEOVER: {
                                MPLog.v(LOGTAG, "Sending intent for takeover notification.");

                                final Intent intent = new Intent(parent.getApplicationContext(), TakeoverInAppActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                                intent.putExtra(TakeoverInAppActivity.INTENT_ID_KEY, intentId);
                                parent.startActivity(intent);
                            }
                            break;
                            default:
                                MPLog.e(LOGTAG, "Unrecognized notification type " + inAppType + " can't be shown");
                        }
                        if (!mConfig.getTestMode()) {
                            trackNotificationSeen(toShow);
                        }
                    } finally {
                        lock.unlock();
                    }
                } // run()

            });
        }

        @Override
        public boolean isIdentified() {
            return getDistinctId() != null;
        }
    }// PeopleImpl

    private interface UpdatesListener extends DecideMessages.OnNewResultsListener {
        public void addOnMmpUpdatesReceivedListener(OnMmpUpdatesReceivedListener listener);
        public void removeOnMmpUpdatesReceivedListener(OnMmpUpdatesReceivedListener listener);
    }

    private class UnsupportedUpdatesListener implements UpdatesListener {
        @Override
        public void onNewResults() {
            // Do nothing, these features aren't supported in older versions of the library
        }

        @Override
        public void addOnMmpUpdatesReceivedListener(OnMmpUpdatesReceivedListener listener) {
            // Do nothing, not supported
        }

        @Override
        public void removeOnMmpUpdatesReceivedListener(OnMmpUpdatesReceivedListener listener) {
            // Do nothing, not supported
        }
    }

    private class GroupImpl implements Group {
        private String mGroupKey;
        private Object mGroupID;

        public GroupImpl(String groupKey, Object groupID) {
            mGroupKey = groupKey;
            mGroupID = groupID;
        }

        @Override
        public void setMap(Map<String, Object> properties) {
            if (hasOptedOutTracking()) return;
            if (null == properties) {
                MPLog.e(LOGTAG, "setMap does not accept null properties");
                return;
            }

            set(new JSONObject(properties));
        }

        @Override
        public void set(JSONObject properties) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject sendProperties = new JSONObject();
                for (final Iterator<?> iter = properties.keys(); iter.hasNext();) {
                    final String key = (String) iter.next();
                    sendProperties.put(key, properties.get(key));
                }

                final JSONObject message = stdGroupMessage("$set", sendProperties);
                recordGroupMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception setting group properties", e);
            }
        }

        @Override
        public void set(String property, Object value) {
            if (hasOptedOutTracking()) return;
            try {
                set(new JSONObject().put(property, value));
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "set", e);
            }
        }

        @Override
        public void setOnceMap(Map<String, Object> properties) {
            if (hasOptedOutTracking()) return;
            if (null == properties) {
                MPLog.e(LOGTAG, "setOnceMap does not accept null properties");
                return;
            }
            try {
                setOnce(new JSONObject(properties));
            } catch (NullPointerException e) {
                MPLog.w(LOGTAG, "Can't have null keys in the properties for setOnceMap!");
            }
        }

        @Override
        public void setOnce(JSONObject properties) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject message = stdGroupMessage("$set_once", properties);
                recordGroupMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception setting group properties");
            }
        }

        @Override
        public void setOnce(String property, Object value) {
            if (hasOptedOutTracking()) return;
            try {
                setOnce(new JSONObject().put(property, value));
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Property name cannot be null", e);
            }
        }

        @Override
        public void union(String name, JSONArray value) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject properties = new JSONObject();
                properties.put(name, value);
                final JSONObject message = stdGroupMessage("$union", properties);
                recordGroupMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception unioning a property", e);
            }
        }

        @Override
        public void remove(String name, Object value) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONObject properties = new JSONObject();
                properties.put(name, value);
                final JSONObject message = stdGroupMessage("$remove", properties);
                recordGroupMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception removing a property", e);
            }
        }

        @Override
        public void unset(String name) {
            if (hasOptedOutTracking()) return;
            try {
                final JSONArray names = new JSONArray();
                names.put(name);
                final JSONObject message = stdGroupMessage("$unset", names);
                recordGroupMessage(message);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception unsetting a property", e);
            }
        }

        @Override
        public void deleteGroup() {
            try {
                final JSONObject message = stdGroupMessage("$delete", JSONObject.NULL);
                recordGroupMessage(message);
                mGroups.remove(makeMapKey(mGroupKey, mGroupID));
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Exception deleting a group", e);
            }
        }

        private JSONObject stdGroupMessage(String actionType, Object properties)
                throws JSONException {
            final JSONObject dataObj = new JSONObject();

            dataObj.put(actionType, properties);
            dataObj.put("$token", mToken);
            dataObj.put("$time", System.currentTimeMillis());
            dataObj.put("$group_key", mGroupKey);
            dataObj.put("$group_id", mGroupID);
            dataObj.put("$mp_metadata", mSessionMetadata.getMetadataForPeople());

            return dataObj;
        }
    }// GroupImpl

    private class SupportedUpdatesListener implements UpdatesListener, Runnable {
        @Override
        public void onNewResults() {
            mExecutor.execute(this);
        }

        @Override
        public void addOnMmpUpdatesReceivedListener(OnMmpUpdatesReceivedListener listener) {
            mListeners.add(listener);

            if (mDecideMessages.hasUpdatesAvailable()) {
                onNewResults();
            }
        }

        @Override
        public void removeOnMmpUpdatesReceivedListener(OnMmpUpdatesReceivedListener listener) {
            mListeners.remove(listener);
        }

        @Override
        public void run() {
            // It's possible that by the time this has run the updates we detected are no longer
            // present, which is ok.
            for (final OnMmpUpdatesReceivedListener listener : mListeners) {
                listener.onMmpUpdatesReceived();
            }
            mConnectIntegrations.setupIntegrations(mDecideMessages.getIntegrations());
        }

        private final Set<OnMmpUpdatesReceivedListener> mListeners = Collections.newSetFromMap(new ConcurrentHashMap<OnMmpUpdatesReceivedListener, Boolean>());
        private final Executor mExecutor = Executors.newSingleThreadExecutor();
    }

    /* package */ class NoOpUpdatesFromMmp implements UpdatesFromMmp {
        public NoOpUpdatesFromMmp(Tweaks tweaks) {
            mTweaks = tweaks;
        }

        @Override
        public void startUpdates() {
            // No op
        }

        @Override
        public void storeVariants(JSONArray variants) {
            // No op
        }

        @Override
        public void applyPersistedUpdates() {
            // No op
        }

        @Override
        public void setEventBindings(JSONArray bindings) {
            // No op
        }

        @Override
        public void setVariants(JSONArray variants) {
            // No op
        }

        @Override
        public Tweaks getTweaks() {
            return mTweaks;
        }

        @Override
        public void addOnMmpTweaksUpdatedListener(OnMmpTweaksUpdatedListener listener) {
            // No op
        }

        @Override
        public void removeOnMmpTweaksUpdatedListener(OnMmpTweaksUpdatedListener listener) {
            // No op
        }

        private final Tweaks mTweaks;
    }

    ////////////////////////////////////////////////////
    protected void flushNoDecideCheck() {
        if (hasOptedOutTracking()) return;
        mMessages.postToServer(new AnalyticsMessages.FlushDescription(mToken, false));
    }

    protected void track(String eventName, JSONObject properties, boolean isAutomaticEvent) {
        if (hasOptedOutTracking() || (isAutomaticEvent && !mDecideMessages.shouldTrackAutomaticEvent())) {
            return;
        }

        final Long eventBegin;
        synchronized (mEventTimings) {
            eventBegin = mEventTimings.get(eventName);
            mEventTimings.remove(eventName);
            mPersistentIdentity.removeTimeEvent(eventName);
        }

        try {
            final JSONObject messageProps = new JSONObject();

            final Map<String, String> referrerProperties = mPersistentIdentity.getReferrerProperties();
            for (final Map.Entry<String, String> entry : referrerProperties.entrySet()) {
                final String key = entry.getKey();
                final String value = entry.getValue();
                messageProps.put(key, value);
            }

            mPersistentIdentity.addSuperPropertiesToObject(messageProps);

            // Don't allow super properties or referral properties to override these fields,
            // but DO allow the caller to override them in their given properties.
            final double timeSecondsDouble = (System.currentTimeMillis()) / 1000.0;
            final long timeSeconds = (long) timeSecondsDouble;
            final String distinctId = getDistinctId();
            final String anonymousId = getAnonymousId();
            final String userId = getUserId();
            messageProps.put("time", timeSeconds);
            messageProps.put("distinct_id", distinctId);
            messageProps.put("$had_persisted_distinct_id", mPersistentIdentity.getHadPersistedDistinctId());
            if(anonymousId != null) {
                messageProps.put("$device_id", anonymousId);
            }
            if(userId != null) {
                messageProps.put("$user_id", userId);
            }

            if (null != eventBegin) {
                final double eventBeginDouble = ((double) eventBegin) / 1000.0;
                final double secondsElapsed = timeSecondsDouble - eventBeginDouble;
                messageProps.put("$duration", secondsElapsed);
            }

            if (null != properties) {
                final Iterator<?> propIter = properties.keys();
                while (propIter.hasNext()) {
                    final String key = (String) propIter.next();
                    messageProps.put(key, properties.get(key));
                }
            }

            final AnalyticsMessages.EventDescription eventDescription =
                    new AnalyticsMessages.EventDescription(eventName, messageProps,
                            mToken, isAutomaticEvent, mSessionMetadata.getMetadataForEvent());
            mMessages.eventsMessage(eventDescription);

            if (mMmpActivityLifecycleCallbacks.getCurrentActivity() != null) {
                getPeople().showGivenNotification(mDecideMessages.getNotification(eventDescription, mConfig.getTestMode()), mMmpActivityLifecycleCallbacks.getCurrentActivity());
            }

            if (null != mTrackingDebug) {
                mTrackingDebug.reportTrack(eventName);
            }
        } catch (final JSONException e) {
            MPLog.e(LOGTAG, "Exception tracking event " + eventName, e);
        }
    }

    private void recordPeopleMessage(JSONObject message) {
        if (hasOptedOutTracking()) return;
        mMessages.peopleMessage(new AnalyticsMessages.PeopleDescription(message, mToken));
    }

    private void recordGroupMessage(JSONObject message) {
        if (hasOptedOutTracking()) return;
        if (message.has("$group_key") && message.has("$group_id")) {
            mMessages.groupMessage(new AnalyticsMessages.GroupDescription(message, mToken));
        } else {
            MPLog.e(LOGTAG, "Attempt to update group without key and value--this should not happen.");
        }
    }

    private void pushWaitingPeopleRecord(String distinctId) {
        mMessages.pushAnonymousPeopleMessage(new AnalyticsMessages.PushAnonymousPeopleDescription(distinctId, mToken));
    }

    private static void registerAppLinksListeners(Context context, final MmpAPI mmp) {
        // Register a BroadcastReceiver to receive com.parse.bolts.measurement_event and track a call to mmp
        try {
            final Class<?> clazz = Class.forName("androidx.localbroadcastmanager.content.LocalBroadcastManager");
            final Method methodGetInstance = clazz.getMethod("getInstance", Context.class);
            final Method methodRegisterReceiver = clazz.getMethod("registerReceiver", BroadcastReceiver.class, IntentFilter.class);
            final Object localBroadcastManager = methodGetInstance.invoke(null, context);
            methodRegisterReceiver.invoke(localBroadcastManager, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final JSONObject properties = new JSONObject();
                    final Bundle args = intent.getBundleExtra("event_args");
                    if (args != null) {
                        for (final String key : args.keySet()) {
                            try {
                                properties.put(key, args.get(key));
                            } catch (final JSONException e) {
                                MPLog.e(APP_LINKS_LOGTAG, "failed to add key \"" + key + "\" to properties for tracking bolts event", e);
                            }
                        }
                    }
                    mmp.track("$" + intent.getStringExtra("event_name"), properties);
                }
            }, new IntentFilter("com.parse.bolts.measurement_event"));
        } catch (final InvocationTargetException e) {
            MPLog.d(APP_LINKS_LOGTAG, "Failed to invoke LocalBroadcastManager.registerReceiver() -- App Links tracking will not be enabled due to this exception", e);
        } catch (final ClassNotFoundException e) {
            MPLog.d(APP_LINKS_LOGTAG, "To enable App Links tracking, add implementation 'androidx.localbroadcastmanager:localbroadcastmanager:1.0.0': " + e.getMessage());
        } catch (final NoSuchMethodException e) {
            MPLog.d(APP_LINKS_LOGTAG, "To enable App Links tracking, add implementation 'androidx.localbroadcastmanager:localbroadcastmanager:1.0.0': " + e.getMessage());
        } catch (final IllegalAccessException e) {
            MPLog.d(APP_LINKS_LOGTAG, "App Links tracking will not be enabled due to this exception: " + e.getMessage());
        }
    }

    private static void checkIntentForInboundAppLink(Context context) {
        // call the Bolts getTargetUrlFromInboundIntent method simply for a side effect
        // if the intent is the result of an App Link, it'll trigger al_nav_in
        // https://github.com/BoltsFramework/Bolts-Android/blob/1.1.2/Bolts/src/bolts/AppLinks.java#L86
        if (context instanceof Activity) {
            try {
                final Class<?> clazz = Class.forName("bolts.AppLinks");
                final Intent intent = ((Activity) context).getIntent();
                final Method getTargetUrlFromInboundIntent = clazz.getMethod("getTargetUrlFromInboundIntent", Context.class, Intent.class);
                getTargetUrlFromInboundIntent.invoke(null, context, intent);
            } catch (final InvocationTargetException e) {
                MPLog.d(APP_LINKS_LOGTAG, "Failed to invoke bolts.AppLinks.getTargetUrlFromInboundIntent() -- Unable to detect inbound App Links", e);
            } catch (final ClassNotFoundException e) {
                MPLog.d(APP_LINKS_LOGTAG, "Please install the Bolts library >= 1.1.2 to track App Links: " + e.getMessage());
            } catch (final NoSuchMethodException e) {
                MPLog.d(APP_LINKS_LOGTAG, "Please install the Bolts library >= 1.1.2 to track App Links: " + e.getMessage());
            } catch (final IllegalAccessException e) {
                MPLog.d(APP_LINKS_LOGTAG, "Unable to detect inbound App Links: " + e.getMessage());
            }
        } else {
            MPLog.d(APP_LINKS_LOGTAG, "Context is not an instance of Activity. To detect inbound App Links, pass an instance of an Activity to getInstance.");
        }
    }

    /* package */ Context getContext() {
        return mContext;
    }

    private final Context mContext;
    private final AnalyticsMessages mMessages;
    private final MPConfig mConfig;
    private final String mToken;
    private final PeopleImpl mPeople;
    private final Map<String, GroupImpl> mGroups;
    private final UpdatesFromMmp mUpdatesFromMmp;
    private final PersistentIdentity mPersistentIdentity;
    private final UpdatesListener mUpdatesListener;
    private final TrackingDebug mTrackingDebug;
    private final ConnectIntegrations mConnectIntegrations;
    private final DecideMessages mDecideMessages;
    private final Map<String, String> mDeviceInfo;
    private final Map<String, Long> mEventTimings;
    private MmpActivityLifecycleCallbacks mMmpActivityLifecycleCallbacks;
    private final SessionMetadata mSessionMetadata;

    // Maps each token to a singleton MmpAPI instance
    private static final Map<String, Map<Context, MmpAPI>> sInstanceMap = new HashMap<String, Map<Context, MmpAPI>>();
    private static final SharedPreferencesLoader sPrefsLoader = new SharedPreferencesLoader();
    private static final Tweaks sSharedTweaks = new Tweaks();
    private static Future<SharedPreferences> sReferrerPrefs;

    private static final String LOGTAG = "MmpAPI.API";
    private static final String APP_LINKS_LOGTAG = "MmpAPI.AL";
    private static final String ENGAGE_DATE_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ss";
}
