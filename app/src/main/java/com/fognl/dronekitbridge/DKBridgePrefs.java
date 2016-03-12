package com.fognl.dronekitbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

/**
 * Created by kellys on 2/27/16.
 */
public class DKBridgePrefs {
    static final String TAG = DKBridgePrefs.class.getSimpleName();
    private static DKBridgePrefs sInstance;

    public static final String PREF_SERVER_IP = "server_ip";
    public static final String PREF_SERVER_PORT = "server_port";

    public static final String PREF_LAST_FRAGMENT = "last_fragment";
        public static final String FRAG_SERVER = "server";
        public static final String FRAG_CLIENT = "client";
        public static final String FRAG_REMOTE = "remote_track";
        public static final String FRAG_TRACKER = "tracker";

    public static final String PREF_LAST_GROUPID = "last_groupid";
    public static final String PREF_LAST_USERID = "last_userid";

    public static final String PREF_PAN_FOLLOW = "pan_to_follow";
    public static final String PREF_TRACKER_RELAY_TYPE = "tracker_relay_type";
        public static final String RELAY_TYPE_BCAST = "bcast";
        public static final String RELAY_TYPE_SOCKET = "socket";

    public static final String PREF_LAST_MAP_ZOOM = "last_map_zoom";
    public static final String PREF_LAST_MAP_CENTER = "last_map_center";

    public static void init(Context context) {
        if(sInstance == null) {
            sInstance = new DKBridgePrefs(context);
        }
    }

    public static DKBridgePrefs get() {
        if(sInstance == null) {
            throw new IllegalArgumentException("Call init() before calling get()");
        }

        return sInstance;
    }

    private final Context mContext;

    private DKBridgePrefs(Context context) {
        super();
        mContext = context;
    }

    public String getLastServerIp() { return getPrefs().getString(PREF_SERVER_IP, ""); }
    public void setLastServerIp(String ip) { getPrefs().edit().putString(PREF_SERVER_IP, ip).commit(); }

    public String getLastServerPort() { return getPrefs().getString(PREF_SERVER_PORT, "8888"); }
    public void setLastServerPort(String port) { getPrefs().edit().putString(PREF_SERVER_PORT, port).commit(); }

    public String getLastFragment() { return getPrefs().getString(PREF_LAST_FRAGMENT, FRAG_SERVER); }
    public void setLastFragment(String f) { getPrefs().edit().putString(PREF_LAST_FRAGMENT, f).commit(); }

    public String getLastGroupId() { return getPrefs().getString(PREF_LAST_GROUPID, null); }
    public void setLastGroupId(String groupId) { getPrefs().edit().putString(PREF_LAST_GROUPID, groupId).commit(); }

    public String getLastUserId() { return getPrefs().getString(PREF_LAST_USERID, null); }
    public void setLastUserId(String userId) { getPrefs().edit().putString(PREF_LAST_USERID, userId).commit(); }

    public boolean getPanToFollow() { return getPrefs().getBoolean(PREF_PAN_FOLLOW, true); }
    public void setPanToFollow(boolean pan) { getPrefs().edit().putBoolean(PREF_PAN_FOLLOW, pan).commit(); }

    public String getTrackerRelayType() { return getPrefs().getString(PREF_TRACKER_RELAY_TYPE, RELAY_TYPE_BCAST); }
    public void setTrackerRelayType(String type) { getPrefs().edit().putString(PREF_TRACKER_RELAY_TYPE, type).commit(); }

    public LatLng getLastMapCenter() {
        String str = getPrefs().getString(PREF_LAST_MAP_CENTER, null);

        if(str != null) {
            String[] parts = str.split("/");
            try {
                return new LatLng(Double.valueOf(parts[0]), Double.valueOf(parts[1]));
            }
            catch(Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                return null;
            }
        }

        return null;
    }

    public void setLastMapCenter(LatLng center) {
        String value = String.format("%.4f/%.4f", center.latitude, center.longitude);
        getPrefs().edit().putString(PREF_LAST_MAP_CENTER, value).commit();
    }

    public float getLastMapZoom() { return getPrefs().getFloat(PREF_LAST_MAP_ZOOM, 20); }
    public void setLastMapZoom(float zoom) { getPrefs().edit().putFloat(PREF_LAST_MAP_ZOOM, zoom).commit(); }

    public void setLastGroupAndUserId(String groupId, String userId) {
        getPrefs()
            .edit()
                .putString(PREF_LAST_GROUPID, groupId)
                .putString(PREF_LAST_USERID, userId)
            .commit();
    }

    public SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }
}
