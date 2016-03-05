package com.fognl.dronekitbridge.fragments;


import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
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

public class RemoteTrackFragment extends Fragment {
    static final String TAG = RemoteTrackFragment.class.getSimpleName();

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
        public void onLocationChanged(Location location) {
            LocationRelayManager.sendLocation(DKBridgeApp.get(), getGroupId(), getUserId(), location, new Callback<ServerResponse>() {
                @Override
                public void onResponse(Call<ServerResponse> call, Response<ServerResponse> response) {
                    final ServerResponse result = response.body();
                    if(result.hasError()) {
                        mStatusText.setText(result.getError());
                    }
                    else {
                        mStatusText.setText(result.getStatus());
                    }
                }

                @Override
                public void onFailure(Call<ServerResponse> call, Throwable err) {
                    Log.e(TAG, err.getMessage(), err);
                    Toast.makeText(getActivity(), err.getMessage(), Toast.LENGTH_SHORT).show();
                    mStatusText.setText(err.getMessage());
                }
            });
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

    private EditText mGroupText;
    private EditText mUserText;
    private TextView mStatusText;
    private Button mButton;

    private boolean mRunning = false;

    public RemoteTrackFragment() {
        super();
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

        mButton = (Button)view.findViewById(R.id.btn_start);
        mButton.setOnClickListener(mClickListener);

        mGroupText.setText(DKBridgePrefs.get().getLastGroupId());
        mUserText.setText(DKBridgePrefs.get().getLastUserId());
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
    }

    void log(String text) {
        mStatusText.setText(text);
    }

    void setRunning(boolean running) {
        if(mRunning != running) {
            if(mRunning) {
                // Stop listening for locations
                LocationAwareness.get().stopLocationUpdates();
                mButton.setText(R.string.btn_start);
                mStatusText.setText("");
            }
            else {
                // Start listening for locations
                LocationAwareness.get().startLocationUpdates(
                        getActivity(), LocationManager.GPS_PROVIDER, mLocationListener);
                mButton.setText(R.string.btn_stop);

                DKBridgePrefs.get().setLastGroupAndUserId(getGroupId(), getUserId());
            }

            mRunning = running;
        }
    }

    String getGroupId() { return mGroupText.getText().toString(); }
    String getUserId() { return mUserText.getText().toString(); }
}
