package com.fognl.dronekitbridge.locationrelay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class LocationRelay {
    static final String TAG = LocationRelay.class.getSimpleName();
    private static LocationRelay sInstance = null;
    private static final boolean V = true;

    public interface DroneLocationListener {
        void onDroneLocationUpdated(String msg);
    }

    private static final double DEFAULT_MIN_ALTITUDE = 10.0;

    public static final String EVT_BASE = "org.droidplanner.android.locationrelay";
    public static final String EVT_DRONE_LOCATION_UPDATED = EVT_BASE + ".DRONE_LOCATION_UPDATED";
    public static final String EVT_TARGET_LOCATION_UPDATED = EVT_BASE + ".TARGET_LOCATION_UPDATED";

    // Internal events
    public static final String EVT_INTERNAL_TARGET_LOCATION = EVT_BASE + ".INTERNAL_TARGET_LOCATION";
    public static final String EVT_FOLLOW_STOPPED = EVT_BASE + ".FOLLOW_STOPPED";

    public static final String
        EXTRA_LAT = "lat"
    ,   EXTRA_LNG = "lng"
    ,   EXTRA_ALTITUDE = "altitude"
    ,   EXTRA_HEADING = "heading"
    ,   EXTRA_TIME = "time"
    ,   EXTRA_ACCURACY = "accuracy"
    ,   EXTRA_SPEED = "speed"
    ,   EXTRA_LOCATION = "location"
    ;

    public static void init(Context context) {
        if(sInstance == null) {
            sInstance = new LocationRelay(context);
            sInstance.setUp();
        }
    }

    public static void shutdown() {
        if(sInstance != null) {
            sInstance.tearDown();
            sInstance = null;
        }
    }

    public static Intent makeDroneLocationEvent() {
        Intent intent = new Intent(EVT_DRONE_LOCATION_UPDATED)
                .putExtra(EXTRA_LAT, 123.45)
                .putExtra(EXTRA_LNG, 234.56)
                .putExtra(EXTRA_ALTITUDE, 567.89)
                .putExtra(EXTRA_ACCURACY, 10)
                .putExtra(EXTRA_HEADING, 30f)
                .putExtra(EXTRA_SPEED, 25f)
                .putExtra(EXTRA_TIME, SystemClock.elapsedRealtime())
                ;
        return intent;
    }

    public static Intent parseIntoTargetLocationEvent(String str) {
        try {
            JSONObject jo = new JSONObject(str);

            Intent intent = new Intent(EVT_TARGET_LOCATION_UPDATED);

            if(jo.has(EXTRA_LAT)) intent.putExtra(EXTRA_LAT, jo.getDouble(EXTRA_LAT));
            if(jo.has(EXTRA_LNG)) intent.putExtra(EXTRA_LNG, jo.getDouble(EXTRA_LNG));
            if(jo.has(EXTRA_ALTITUDE)) intent.putExtra(EXTRA_ALTITUDE, jo.getDouble(EXTRA_ALTITUDE));
            if(jo.has(EXTRA_ACCURACY)) intent.putExtra(EXTRA_ACCURACY, jo.getInt(EXTRA_ACCURACY));
            if(jo.has(EXTRA_HEADING)) intent.putExtra(EXTRA_HEADING, (float)jo.getDouble(EXTRA_HEADING));
            if(jo.has(EXTRA_SPEED)) intent.putExtra(EXTRA_SPEED, (float)jo.getDouble(EXTRA_SPEED));
            if(jo.has(EXTRA_TIME)) intent.putExtra(EXTRA_TIME, jo.getLong(EXTRA_TIME));

            return intent;

        } catch(JSONException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            return null;
        }
    }

    public static JSONObject populateDroneLocationEvent(JSONObject jo, Intent intent) {
        try {
            if(intent.hasExtra(EXTRA_LAT)) jo.put(EXTRA_LAT, intent.getDoubleExtra(EXTRA_LAT, 0));
            if(intent.hasExtra(EXTRA_LNG)) jo.put(EXTRA_LNG, intent.getDoubleExtra(EXTRA_LNG, 0));
            if(intent.hasExtra(EXTRA_ALTITUDE)) jo.put(EXTRA_ALTITUDE, intent.getDoubleExtra(EXTRA_ALTITUDE, 0));
            if(intent.hasExtra(EXTRA_ACCURACY)) jo.put(EXTRA_ACCURACY, intent.getFloatExtra(EXTRA_ACCURACY, 0));
            if(intent.hasExtra(EXTRA_HEADING)) jo.put(EXTRA_HEADING, intent.getFloatExtra(EXTRA_HEADING, 0));
            if(intent.hasExtra(EXTRA_SPEED)) jo.put(EXTRA_SPEED, intent.getFloatExtra(EXTRA_SPEED, 0));
            if(intent.hasExtra(EXTRA_TIME)) jo.put(EXTRA_TIME, intent.getLongExtra(EXTRA_TIME, 0));

        } catch(Exception ex) {
            Log.e(TAG, ex.getMessage(), ex);
            jo = null;
        }

        return jo;
    }

    public static LocationRelay get() {
        return sInstance;
    }

    private final BroadcastReceiver mDroneLocationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            switch(action) {
                case EVT_DRONE_LOCATION_UPDATED: {
                    if(mDroneLocationListener != null) {
                        JSONObject jo = populateDroneLocationEvent(new JSONObject(), intent);
                        if(jo != null) {
                            mDroneLocationListener.onDroneLocationUpdated(jo.toString());
                        }
                        else {
                            Log.w(TAG, "Unable to convert the intent to a JSONObject");
                        }
                    }

                    break;
                }
            }
        }
    };

    private final BroadcastReceiver mLocalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

        }
    };

    private final Context mContext;
    private DroneLocationListener mDroneLocationListener;

    private LocationRelay(Context context) {
        super();
        mContext = context;
    }

    public static Location toAndroidLocation(Intent intent) {
        Location loc = new Location("EXPLICIT");
        loc.setLatitude(intent.getDoubleExtra(EXTRA_LAT, 0));
        loc.setLongitude(intent.getDoubleExtra(EXTRA_LNG, 0));
        loc.setAltitude(intent.getDoubleExtra(EXTRA_ALTITUDE, 0));
        loc.setAccuracy(intent.getIntExtra(EXTRA_ACCURACY, 100));
        loc.setBearing(intent.getFloatExtra(EXTRA_HEADING, 0));
        loc.setSpeed(intent.getFloatExtra(EXTRA_SPEED, 0));
        loc.setTime(intent.getLongExtra(EXTRA_TIME, 0));
        return loc;
    }

    public static Intent toIntent(String action, Location loc) {
        Intent intent = new Intent(action)
                .putExtra(EXTRA_LAT, loc.getLatitude())
                .putExtra(EXTRA_LNG, loc.getLongitude())
                .putExtra(EXTRA_ALTITUDE, loc.getAltitude())
                .putExtra(EXTRA_ACCURACY, loc.getAccuracy())
                .putExtra(EXTRA_HEADING, loc.getBearing())
                .putExtra(EXTRA_SPEED, loc.getSpeed())
                .putExtra(EXTRA_TIME, loc.getTime())
                ;
        return intent;
    }

    private void setUp() {
    }

    private void tearDown() {
    }

    public void relayAndroidTargetLocation(Location loc) {
        Intent intent = toIntent(EVT_TARGET_LOCATION_UPDATED, loc);
        if(intent != null) {
            mContext.sendBroadcast(intent);
        }
        else {
            Log.w(TAG, "No intent made from location: " + loc);
        }
    }

    public void listenForDroneEvents(DroneLocationListener listener) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(EVT_DRONE_LOCATION_UPDATED);

        mContext.registerReceiver(mDroneLocationReceiver, filter);
        mDroneLocationListener = listener;
    }

    public void stopListeningForDroneEvents() {
        mDroneLocationListener = null;

        try {
            mContext.unregisterReceiver(mDroneLocationReceiver);
        } catch(Throwable ex) {}
    }
}
