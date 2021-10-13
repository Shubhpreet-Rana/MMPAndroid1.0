/**
 * This package contains the interface to Mmp that you can use from your
 * Android apps. You can use Mmp to send events, update people analytics properties,
 * display push notifications and other Mmp-driven content to your users.
 *
 * The primary interface to Mmp services is in {@link com.mmp.android.mpmetrics.MmpAPI}.
 * At it's simplest, you can send events with
 * <pre>
 * {@code
 *
 * MmpAPI mmp = MmpAPI.getInstance(context, MMP_TOKEN);
 * mmp.track("Library integrated", null);
 *
 * }
 * </pre>
 *
 * In addition to this reference documentation, you can also see our overview
 * and getting started documentation at
 * <a href="https://mmp.com/help/reference/android" target="_blank"
 *    >https://mmp.com/help/reference/android</a>
 *
 */
package com.mmp.android.mpmetrics;