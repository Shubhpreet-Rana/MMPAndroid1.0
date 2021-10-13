package com.mmp.android.viewcrawler;

import com.mmp.android.mpmetrics.OnMmpTweaksUpdatedListener;
import com.mmp.android.mpmetrics.Tweaks;

import org.json.JSONArray;

/* This interface is for internal use in the Mmp library, and should not be
   implemented in client code. */
public interface UpdatesFromMmp {
    public void startUpdates();
    public void applyPersistedUpdates();
    public void setEventBindings(JSONArray bindings);
    public void storeVariants(JSONArray variants);
    public void setVariants(JSONArray variants);
    public Tweaks getTweaks();
    public void addOnMmpTweaksUpdatedListener(OnMmpTweaksUpdatedListener listener);
    public void removeOnMmpTweaksUpdatedListener(OnMmpTweaksUpdatedListener listener);
}
