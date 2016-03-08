package com.fognl.dronekitbridge.fragments;


import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.fognl.dronekitbridge.DKBridgeApp;
import com.fognl.dronekitbridge.DKBridgePrefs;
import com.fognl.dronekitbridge.R;
import com.fognl.dronekitbridge.location.LocationAwareness;
import com.fognl.dronekitbridge.locationrelay.LocationRelayManager;
import com.fognl.dronekitbridge.web.ServerResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RemoteTargetFragment extends Fragment {
    static final String TAG = RemoteTargetFragment.class.getSimpleName();

    private static final long SEND_INTERVAL = 3000;

    private final View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()) {
                case R.id.btn_start: {
                    onStartStopClick(v);
                    break;
                }
            }
        }
    };

    private final TextWatcher mWatcher = new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        public void onTextChanged(CharSequence s, int start, int before, int count) { }

        @Override
        public void afterTextChanged(Editable s) {
            setButtonStates();
        }
    };

    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location here) {
            Log.v(TAG, "onLocationChanged(): here=" + here);

            mCurrentLocation = here;

            if(mLastLocation == null) {
                mLastLocation = mCurrentLocation;
            }

            mHandler.removeCallbacks(mSendLocation);
            mHandler.post(mSendLocation);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };

    private final Handler mHandler = new Handler();

    private final Runnable mSendLocation = new Runnable() {
        @Override
        public void run() {
            if(mCurrentLocation != null) {
                // Make sure the location is fresh.
                // While we're reporting, even if we're sitting still, we want to be
                // a viable target.
                mCurrentLocation.setTime(System.currentTimeMillis());
                doSendLocation(mCurrentLocation);
            }
            else {
                Log.v(TAG, "No current location yet");
            }
        }
    };

    private EditText mGroupText;
    private EditText mUserText;
    private TextView mStatusText;
    private TextView mRunStatusText;
    private Button mButton;
    private Location mCurrentLocation;
    private Location mLastLocation;

    private boolean mRunning = false;
    private long mTotalDistance = 0;
    private float mTotalSpeed = 0f;
    private long mTotalReadings = 0;

    public RemoteTargetFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_remote_track, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mGroupText = (EditText)view.findViewById(R.id.edit_groupId);
        mUserText = (EditText)view.findViewById(R.id.edit_userId);

        mGroupText.addTextChangedListener(mWatcher);
        mUserText.addTextChangedListener(mWatcher);

        mStatusText = (TextView)view.findViewById(R.id.text_status);
        mRunStatusText = (TextView)view.findViewById(R.id.text_run_status);

        mButton = (Button)view.findViewById(R.id.btn_start);
        mButton.setOnClickListener(mClickListener);

        mGroupText.setText(DKBridgePrefs.get().getLastGroupId());
        mUserText.setText(DKBridgePrefs.get().getLastUserId());

        setButtonStates();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        setRunning(false);
    }

    void onStartStopClick(View v) {
        setRunning(!mRunning);
    }

    void setButtonStates() {
        boolean enabled = true;

        for(EditText et: new EditText[] {
            mGroupText, mUserText
        }) {
            if(TextUtils.isEmpty(et.getText().toString())) {
                enabled = false;
                break;
            }
        }

        mButton.setEnabled(enabled);
        mButton.setText(mRunning? R.string.btn_stop: R.string.btn_start);
    }

    void log(String text) {
        mStatusText.setText(text);
    }

    void setRunning(boolean running) {
        if(mRunning != running) {
            if(mRunning) {
                // Stop listening for locations
                LocationAwareness.get().stopLocationUpdates();
                mStatusText.setText("");
                mHandler.removeCallbacks(mSendLocation);
                mCurrentLocation = null;

                deleteUserFromGroup();
            }
            else {
                // Start listening for locations
                LocationAwareness.get().startLocationUpdates(
                        getActivity(), LocationManager.GPS_PROVIDER, mLocationListener);
                mButton.setText(R.string.btn_stop);

                DKBridgePrefs.get().setLastGroupAndUserId(getGroupId(), getUserId());

                mCurrentLocation = null;
                mLastLocation = null;
                mRunStatusText.setText("");
                mTotalDistance = 0;
                mTotalSpeed = 0f;
                mTotalReadings = 0;

                mHandler.postDelayed(mSendLocation, SEND_INTERVAL);
            }

            mRunning = running;

            setButtonStates();
        }
    }

    void deleteUserFromGroup() {
        LocationRelayManager.deleteUser(DKBridgeApp.get(), getGroupId(), getUserId(), new Callback<ServerResponse>() {
            @Override
            public void onResponse(Call<ServerResponse> call, Response<ServerResponse> response) {
                final ServerResponse res = response.body();
                if (res != null) {
                    if (res.hasError()) {
                        Toast.makeText(DKBridgeApp.get(), res.getError(), Toast.LENGTH_SHORT).show();
                    } else {
                        mStatusText.setText(res.getStatus());
                    }
                }
            }

            @Override
            public void onFailure(Call<ServerResponse> call, Throwable err) {
                Log.e(TAG, err.getMessage(), err);
                Toast.makeText(DKBridgeApp.get(), err.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    String getGroupId() { return mGroupText.getText().toString(); }
    String getUserId() { return mUserText.getText().toString(); }

    boolean areEqual(Location l1, Location l2) {
        return (
            l1 != null && l2 != null &&
            l1.getLatitude() == l2.getLatitude() &&
            l1.getLongitude() == l2.getLongitude()
        );
    }

    void doSendLocation(Location location) {
        Log.v(TAG, String.format("location: lat=%.4f lng=%.4f time=%d", location.getLatitude(), location.getLongitude(), location.getTime()));

        LocationRelayManager.sendLocation(DKBridgeApp.get(), getGroupId(), getUserId(), location, new Callback<ServerResponse>() {
            @Override
            public void onResponse(Call<ServerResponse> call, Response<ServerResponse> response) {
                final ServerResponse result = response.body();

                if(result != null) {
                    if(result.hasError()) {
                        mStatusText.setText(result.getError());
                    }
                    else {
                        mStatusText.setText(result.getStatus());
                    }

                    if(!areEqual(mCurrentLocation, mLastLocation)) {
                        calcAndDisplayStats();
                        mLastLocation = mCurrentLocation;
                    }
                }

                mHandler.postDelayed(mSendLocation, SEND_INTERVAL);
            }

            @Override
            public void onFailure(Call<ServerResponse> call, Throwable err) {
                Log.e(TAG, err.getMessage(), err);
                Toast.makeText(getActivity(), err.getMessage(), Toast.LENGTH_SHORT).show();
                mStatusText.setText(err.getMessage());
            }
        });
    }

    void calcAndDisplayStats() {
        float distance = mCurrentLocation.distanceTo(mLastLocation);
        mTotalDistance += distance;
        long time = mCurrentLocation.getTime() - mLastLocation.getTime();
        float heading = mCurrentLocation.getBearing();

        if(time < 1000) {
            time = 1000;
        }

        float currSpeed = (distance / (time / 1000));
        mTotalSpeed += currSpeed;

        float avgSpeed = (mTotalSpeed / ++mTotalReadings);

        Log.v(TAG, "totalSpeed=" + mTotalSpeed + " avgSpeed=" + avgSpeed);

        String str = String.format("Speed: %.2f m/s (%.2f avg) Distance: %d Heading: %.0f",
                currSpeed, avgSpeed, mTotalDistance, heading);
        mRunStatusText.setText(str);
    }
}
