package com.mmp.android.mpmetrics;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import com.mmp.android.util.JSONUtils;
import com.mmp.android.util.MPLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class InAppNotification implements Parcelable {

    private static final String LOGTAG = "MmpAPI.InAppNotif";
    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile("(\\.[^./]+$)");

    protected final JSONObject mDescription;
    protected final JSONObject mExtras;

    protected final int mId;
    protected final int mMessageId;
    private final int mBackgroundColor;
    private final String mBody;
    private final int mBodyColor;
    private final String mImageUrl;
    private final List<DisplayTrigger> mDisplayTriggers;

    private Bitmap mImage;

    public InAppNotification() {
        mDescription = null;
        mExtras = null;
        mId = 0;
        mMessageId = 0;
        mBackgroundColor = 0;
        mBody = null;
        mBodyColor = 0;
        mImageUrl = null;
        mDisplayTriggers = null;
    }

    public InAppNotification(Parcel in) {
        JSONObject tempDescription = new JSONObject();
        JSONObject tempExtras = new JSONObject();
        try {
            tempDescription = new JSONObject(in.readString());
            tempExtras = new JSONObject(in.readString());
        } catch (JSONException e) {
            MPLog.e(LOGTAG, "Error reading JSON when creating InAppNotification from Parcel");
        }
        mDescription = tempDescription;
        mExtras = tempExtras;

        mId = in.readInt();
        mMessageId = in.readInt();
        mBackgroundColor = in.readInt();
        mBody = in.readString();
        mBodyColor = in.readInt();
        mImageUrl = in.readString();
        mImage = in.readParcelable(Bitmap.class.getClassLoader());
        mDisplayTriggers = new ArrayList<>();
        in.readList(mDisplayTriggers, null);
    }

    /* package */ InAppNotification(JSONObject description) throws BadDecideObjectException {
        JSONArray tempDisplayTriggers;
        mDisplayTriggers = new ArrayList<>();
        try {
            mDescription = description;
            mExtras = description.getJSONObject("extras");
            mId = description.getInt("id");
            mMessageId = description.getInt("message_id");
            mBackgroundColor = description.getInt("bg_color");
            mBody = JSONUtils.optionalStringKey(description, "body");
            mBodyColor = description.optInt("body_color");
            mImageUrl = description.getString("image_url");
            mImage = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888);
            tempDisplayTriggers = description.optJSONArray("display_triggers");
            for (int i = 0; null != tempDisplayTriggers && i < tempDisplayTriggers.length(); i++) {
                mDisplayTriggers.add(new DisplayTrigger(tempDisplayTriggers.getJSONObject(i)));
            }
        } catch (final JSONException e) {
            throw new BadDecideObjectException("Notification JSON was unexpected or bad", e);
        }
    }

    /**
     * InApp Notifications in Mmp are either TAKEOVERs, that display full screen,
     * or MINI notifications that appear and disappear on the margins of the screen.
     */
    public enum Type {
        UNKNOWN {
            @Override
            public String toString() {
                return "*unknown_type*";
            }
        },
        MINI {
            @Override
            public String toString() {
                return "mini";
            }
        },
        TAKEOVER {
            @Override
            public String toString() {
                return "takeover";
            }
        }
    }

    /* package */ String toJSON() {
        return mDescription.toString();
    }

    /* package */ JSONObject getCampaignProperties() {
        final JSONObject ret = new JSONObject();
        try {
            ret.put("campaign_id", getId());
            ret.put("message_id", getMessageId());
            ret.put("message_type", "inapp");
            ret.put("message_subtype", getType().toString());
        } catch (JSONException e) {
            MPLog.e(LOGTAG, "Impossible JSON Exception", e);
        }

        return ret;
    }

    public int getId() {
        return mId;
    }

    public int getMessageId() {
        return mMessageId;
    }

    public abstract Type getType();

    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    public boolean hasBody() {
        return mBody != null;
    }

    public String getBody() {
        return mBody;
    }

    public int getBodyColor() {
        return mBodyColor;
    }

    public String getImageUrl() {
        return mImageUrl;
    }

    public String getImage2xUrl() {
        return sizeSuffixUrl(mImageUrl, "@2x");
    }

    public String getImage4xUrl() {
        return sizeSuffixUrl(mImageUrl, "@4x");
    }

    /* package */ void setImage(final Bitmap image) {
        mImage = image;
    }

    public Bitmap getImage() {
        return mImage;
    }

    public boolean isEventTriggered() {
        return null != mDisplayTriggers && !mDisplayTriggers.isEmpty();
    }

    public boolean matchesEventDescription(AnalyticsMessages.EventDescription eventDescription) {
        if (isEventTriggered()) {
            for (DisplayTrigger trigger : mDisplayTriggers) {
                if (trigger.matchesEventDescription(eventDescription)) {
                    return true;
                }
            }
        }
        return false;
    }

    /* package */ static String sizeSuffixUrl(String url, String sizeSuffix) {
        final Matcher matcher = FILE_EXTENSION_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.replaceFirst(sizeSuffix + "$1");
        } else {
            return url;
        }
    }

    protected JSONObject getExtras() {
        return mExtras;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mDescription.toString());
        dest.writeString(mExtras.toString());
        dest.writeInt(mId);
        dest.writeInt(mMessageId);
        dest.writeInt(mBackgroundColor);
        dest.writeString(mBody);
        dest.writeInt(mBodyColor);
        dest.writeString(mImageUrl);
        dest.writeParcelable(mImage, flags);
        dest.writeList(mDisplayTriggers);
    }

    @Override
    public String toString() {
        return mDescription.toString();
    }
}
