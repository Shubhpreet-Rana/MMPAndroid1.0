package com.mmp.android.mpmetrics;

/**
 * A Tweak allows you to alter values in your user's applications through the Mmp UI.
 * Use Tweaks to expose parameters you can adjust in A/B tests, to determine what application
 * settings result in the best experiences for your users and which are best for achieving
 * your goals.
 *
 * You can declare tweaks with
 * {@link MmpAPI#stringTweak(String, String)}, {@link MmpAPI#booleanTweak(String, boolean)},
 * {@link MmpAPI#doubleTweak(String, double)}, {@link MmpAPI#longTweak(String, long)},
 * and other tweak-related interfaces on MmpAPI.
 */
public interface Tweak<T> {
    /**
     * @return a value for this tweak, either the default value or a value set as part of a Mmp A/B test.
     */
    T get();
}
