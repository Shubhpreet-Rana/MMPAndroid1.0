package com.mmp.android.mpmetrics;

/**
 * For use with {@link MmpAPI.People#addOnMmpUpdatesReceivedListener(OnMmpUpdatesReceivedListener)}
 */
public interface OnMmpUpdatesReceivedListener {
    /**
     * Called when the Mmp library has updates, for example, Notifications.
     * This method will not be called once per update, but rather any time a batch of updates
     * becomes available. The related updates can be checked with
     * {@link MmpAPI.People#getNotificationIfAvailable()}
     */
    public void onMmpUpdatesReceived();
}
