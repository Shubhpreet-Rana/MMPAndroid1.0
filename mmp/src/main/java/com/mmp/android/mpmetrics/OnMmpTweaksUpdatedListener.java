package com.mmp.android.mpmetrics;

import java.util.Set;

/**
 * For use with {@link MmpAPI.People#addOnMmpTweaksUpdatedListener(OnMmpTweaksUpdatedListener)}
 */
public interface OnMmpTweaksUpdatedListener {
    /**
     * Called when the Mmp library has updated tweaks.
     * This method will not be called once per tweak update, but rather any time a batch of updates
     * becomes available.
     *
     * @param updatedTweaksName The set of tweak names that were updated.
     */
    public void onMmpTweakUpdated(Set<String> updatedTweaksName);
}