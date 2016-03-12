package com.fognl.dronekitbridge.location;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;

import com.fognl.dronekitbridge.R;

/**
 * Created by kellys on 2/28/16.
 */
public class LocationAwareness {
    static final String TAG = LocationAwareness.class.getSimpleName();

    public static final int PERMISSION_REQUEST_LOCATION = 10;

    public static final long LOCATION_INTERVAL = 1000;
    public static final float LOCATION_DISTANCE = 1f;

    private static LocationAwareness sInstance;

    public static void init(Context context) {
        if(sInstance == null) {
            sInstance = new LocationAwareness(context);
        }
    }

    public static LocationAwareness get() {
        if(sInstance == null) {
            throw new IllegalStateException("Call init() before get()");
        }

        return sInstance;
    }

    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.v(TAG, "onLocationChanged(): location=" + location);

            if(mExternalLocationListener != null) {
                mExternalLocationListener.onLocationChanged(location);
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            if(mExternalLocationListener != null) {
                mExternalLocationListener.onStatusChanged(provider, status, extras);
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
            if(mExternalLocationListener != null) {
                mExternalLocationListener.onProviderEnabled(provider);
            }
        }

        @Override
        public void onProviderDisabled(String provider) {
            if(mProviderType.equals(provider)) {
                stopLocationUpdates();
            }

            if(mExternalLocationListener != null) {
                mExternalLocationListener.onProviderDisabled(provider);
            }
        }
    };

    private final Context mContext;
    private final LocationManager mLocationManager;
    private String mProviderType = LocationManager.GPS_PROVIDER;
    private LocationListener mExternalLocationListener;

    private LocationAwareness(Context context) {
        super();
        mContext = context;
        mLocationManager = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
    }

    public void startLocationUpdates(final Activity activity, @NonNull String provider, @NonNull LocationListener listener) {
        mProviderType = provider;
        mExternalLocationListener = listener;

        if(isProviderEnabled(provider)) {
            if(isMarshmallow()) {
                switch(ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    case PackageManager.PERMISSION_GRANTED: {
                        doStartLocationUpdates();
                        break;
                    }

                    case PackageManager.PERMISSION_DENIED:
                    default: {
                        if(ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                            Toast.makeText(activity, R.string.toast_location_rationale, Toast.LENGTH_LONG).show();
                        }

                        ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, PERMISSION_REQUEST_LOCATION);
                        break;
                    }
                }
            }
            else {
                doStartLocationUpdates();
            }
        }
        else {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.dlg_enable_location_title)
                    .setMessage(R.string.dlg_enable_location_msg)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            activity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        }
                    })
                    .create()
                    .show();
        }
    }

    public void stopLocationUpdates() {
        try {
            mLocationManager.removeUpdates(mLocationListener);
        }
        catch(SecurityException ex) {
            Log.e(TAG, ex.getMessage(), ex);
        }
    }

    public boolean isListeningForLocations() {
        return false; // TODO
    }

    public void onPermissionGranted() {
        doStartLocationUpdates();
    }

    void doStartLocationUpdates() {
        try {
            mLocationManager.requestLocationUpdates(mProviderType, LOCATION_INTERVAL, LOCATION_DISTANCE, mLocationListener);
        }
        catch(SecurityException ex) {
            Log.e(TAG, ex.getMessage(), ex);
        }
    }

    boolean isProviderEnabled(String provider) {
        return mLocationManager.isProviderEnabled(provider);
    }

    static boolean isMarshmallow() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
    }
}
