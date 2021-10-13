package com.mmp.android.mpmetrics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MmpPushNotificationDismissedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(MmpPushNotification.PUSH_DISMISS_ACTION)) {
            MmpAPI.trackPushNotificationEventFromIntent(
                    context,
                    intent,
                    "$push_notification_dismissed"
            );
        }
    }
}
