package com.mmp.android.mpmetrics;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.mmp.android.R;
import com.mmp.android.util.MPLog;
import com.mmp.android.util.ViewUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Attached to an Activity when you display a mini in-app notification.
 *
 * Users of the library should not reference this class directly.
 */
@TargetApi(MPConfig.UI_FEATURES_MIN_API)
@SuppressLint("ClickableViewAccessibility")
public class InAppFragment extends Fragment {

    public void setDisplayState(final MmpAPI mmp, final int stateId, final UpdateDisplayState.DisplayState.InAppNotificationState displayState) {
        // It would be better to pass in displayState to the only constructor, but
        // Fragments require a default constructor that is called when Activities recreate them.
        // This means that when the Activity recreates this Fragment (due to rotation, or
        // the Activity going away and coming back), mDisplayStateId and mDisplayState are not
        // initialized. Lifecycle methods should be aware of this case, and decline to show.
        mMmp = mmp;
        mDisplayStateId = stateId;
        mDisplayState = displayState;
    }

    // It's safe to use onAttach(Activity) in API 23 as its implementation has not been changed.
    // Bypass the Lint check for now.
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        mParent = activity;
        if (null == mDisplayState) {
            cleanUp();
            return;
        }

        // We have to manually clear these Runnables in onStop in case they exist, since they
        // do illegal operations when onSaveInstanceState has been called already.

        mHandler = new Handler();
        mRemover = new Runnable() {
            public void run() {
                InAppFragment.this.remove();
            }
        };
        mDisplayMini = new Runnable() {
            @Override
            public void run() {
                mInAppView.setVisibility(View.VISIBLE);
                mInAppView.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View view, MotionEvent event) {
                        return InAppFragment.this.mDetector.onTouchEvent(event);
                    }
                });

                final ImageView notifImage = (ImageView) mInAppView.findViewById(R.id.com_mmp_android_notification_image);

                final float heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 65, mParent.getResources().getDisplayMetrics());
                final TranslateAnimation translate = new TranslateAnimation(0, 0, heightPx, 0);
                translate.setInterpolator(new DecelerateInterpolator());
                translate.setDuration(200);
                mInAppView.startAnimation(translate);

                final ScaleAnimation scale = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, heightPx / 2, heightPx / 2);
                scale.setInterpolator(new SineBounceInterpolator());
                scale.setDuration(400);
                scale.setStartOffset(200);
                notifImage.startAnimation(scale);
            }
        };

        mDetector = new GestureDetector(activity, new GestureDetector.OnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2,
                                   float velocityX, float velocityY) {
                if (velocityY > 0) {
                    remove();
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) { }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                    float distanceX, float distanceY) {
                return false;
            }

            @Override
            public void onShowPress(MotionEvent e) { }

            @Override
            public boolean onSingleTapUp(MotionEvent event) {
                final MiniInAppNotification inApp = (MiniInAppNotification) mDisplayState.getInAppNotification();

                JSONObject trackingProperties = null;
                final String uriString = inApp.getCtaUrl();
                if (uriString != null && uriString.length() > 0) {
                    Uri uri;
                    try {
                        uri = Uri.parse(uriString);
                    } catch (IllegalArgumentException e) {
                        MPLog.i(LOGTAG, "Can't parse notification URI, will not take any action", e);
                        return true;
                    }

                    try {
                        Intent viewIntent = new Intent(Intent.ACTION_VIEW, uri);
                        mParent.startActivity(viewIntent);
                    } catch (ActivityNotFoundException e) {
                        MPLog.i(LOGTAG, "User doesn't have an activity for notification URI " + uri);
                    }

                    try {
                        trackingProperties = new JSONObject();
                        trackingProperties.put("url", uriString);
                    } catch (final JSONException e) {
                        MPLog.e(LOGTAG, "Can't put url into json properties");
                    }
                }
                mMmp.getPeople().trackNotification("$campaign_open", inApp, trackingProperties);

                remove();
                return true;
            }
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCleanedUp.set(false);
    }

    @SuppressWarnings("deprecation")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        if (null == mDisplayState) {
            cleanUp();
        } else {
            mInAppView = inflater.inflate(R.layout.com_mmp_android_activity_notification_mini, container, false);
            final TextView bodyTextView = (TextView) mInAppView.findViewById(R.id.com_mmp_android_notification_title);
            final ImageView notifImage = (ImageView) mInAppView.findViewById(R.id.com_mmp_android_notification_image);

            MiniInAppNotification inApp = (MiniInAppNotification) mDisplayState.getInAppNotification();

            bodyTextView.setText(inApp.getBody());
            bodyTextView.setTextColor(inApp.getBodyColor());
            notifImage.setImageBitmap(inApp.getImage());

            mHandler.postDelayed(mRemover, MINI_REMOVE_TIME);

            GradientDrawable viewBackground = new GradientDrawable();
            viewBackground.setColor(inApp.getBackgroundColor());
            viewBackground.setCornerRadius(ViewUtils.dpToPx(7, getActivity()));
            viewBackground.setStroke((int)ViewUtils.dpToPx(2, getActivity()), inApp.getBorderColor());

            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                mInAppView.setBackgroundDrawable(viewBackground);
            } else {
                mInAppView.setBackground(viewBackground);
            }

            Drawable myIcon = new BitmapDrawable(getResources(), mDisplayState.getInAppNotification().getImage());
            myIcon.setColorFilter(inApp.getImageTintColor(), PorterDuff.Mode.SRC_ATOP);
            notifImage.setImageDrawable(myIcon);
        }

        return mInAppView;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mCleanedUp.get()) {
            mParent.getFragmentManager().beginTransaction().remove(this).commit();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // getHighlightColorFromBackground doesn't seem to work on onResume because the view
        // has not been fully rendered, so try and delay a little bit. This is also a bit better UX
        // by giving the user some time to process the new Activity before displaying the notification.
        mHandler.postDelayed(mDisplayMini, 500);
    }

    @Override
    public void onPause() {
        super.onPause();
        cleanUp();
    }

    private void cleanUp() {
        if (!mCleanedUp.get()) {
            if (mHandler != null) {
                mHandler.removeCallbacks(mRemover);
                mHandler.removeCallbacks(mDisplayMini);
            }
            UpdateDisplayState.releaseDisplayState(mDisplayStateId);

            final FragmentManager fragmentManager = mParent.getFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            try {
                transaction.remove(this).commit();
            } catch (IllegalStateException e) {
                fragmentManager.beginTransaction().remove(this).commitAllowingStateLoss();
            }
        }

        mCleanedUp.set(true);
    }

    private void remove() {
        boolean isDestroyed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 ? mParent.isDestroyed() : false;
        if (mParent != null && !mParent.isFinishing() && !isDestroyed && !mCleanedUp.get()) {
            mHandler.removeCallbacks(mRemover);
            mHandler.removeCallbacks(mDisplayMini);

            final FragmentManager fragmentManager = mParent.getFragmentManager();

            // setCustomAnimations works on a per transaction level, so the animations set
            // when this fragment was created do not apply
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            try {
                transaction.setCustomAnimations(0, R.animator.com_mmp_android_slide_down).remove(this).commit();
            } catch (IllegalStateException e) {
                fragmentManager.beginTransaction().setCustomAnimations(0, R.animator.com_mmp_android_slide_down).remove(this).commitAllowingStateLoss();
            }
            UpdateDisplayState.releaseDisplayState(mDisplayStateId);
            mCleanedUp.set(true);
        }
    }

    private class SineBounceInterpolator implements Interpolator {
        public SineBounceInterpolator() { }
        public float getInterpolation(float t) {
            return (float) -(Math.pow(Math.E, -8*t) * Math.cos(12*t)) + 1;
        }
    }

    private MmpAPI mMmp;
    private Activity mParent;
    private GestureDetector mDetector;
    private Handler mHandler;
    private int mDisplayStateId;
    private UpdateDisplayState.DisplayState.InAppNotificationState mDisplayState;
    private Runnable mRemover, mDisplayMini;
    private View mInAppView;

    private AtomicBoolean mCleanedUp = new AtomicBoolean();

    private static final String LOGTAG = "MmpAPI.InAppFrag";
    private static final int MINI_REMOVE_TIME = 10000;
}
