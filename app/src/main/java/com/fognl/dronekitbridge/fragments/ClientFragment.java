package com.fognl.dronekitbridge.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.fognl.dronekitbridge.DKBridgeApp;
import com.fognl.dronekitbridge.DKBridgePrefs;
import com.fognl.dronekitbridge.R;
import com.fognl.dronekitbridge.comm.SocketClient;
import com.fognl.dronekitbridge.location.LocationAwareness;
import com.fognl.dronekitbridge.locationrelay.LocationRelay;

import org.json.JSONObject;

public class ClientFragment extends Fragment {
    static final String TAG = ClientFragment.class.getSimpleName();

    static String formatForLog(Location loc) {
        return String.format("Location: %.3f/%.3f, speed=%.2f heading=%.2f", loc.getLatitude(), loc.getLongitude(), loc.getSpeed(), loc.getBearing());
    }

    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(Uri uri);
    }

    private final View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()) {
                case R.id.btn_connect: {
                    onConnectClick(v);
                    break;
                }

                case R.id.btn_send: {
                    onSendClick(v);
                    break;
                }
            }
        }
    };

    private final RadioGroup.OnCheckedChangeListener mLocationOptionListener = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            listenForDroneEvents(checkedId == R.id.rdo_drone_location);
            listenForLocationEvents(checkedId == R.id.rdo_my_location);
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

    private final SocketClient.Listener mClientListener = new SocketClient.Listener() {
        @Override
        public void onConnected() {
            mConnectButton.setText(R.string.btn_disconnect);
            mSendButton.setEnabled(true);
        }

        @Override
        public void onConnectFailed(Throwable error) {
            Toast.makeText(getActivity(), R.string.toast_client_connect_failed, Toast.LENGTH_SHORT).show();
            disconnectClient();
        }

        @Override
        public void onDisconnected() {
            mConnectButton.setText(R.string.btn_connect);
            mSendButton.setEnabled(false);
        }

        @Override
        public void onError(Throwable error) {
            showError(error);
        }
    };

    private final LocationRelay.DroneLocationListener mDroneLocationListener = new LocationRelay.DroneLocationListener() {
        @Override
        public void onDroneLocationUpdated(String msg) {
            if(mClient != null && mClient.isConnected()) {
                mClient.send(msg);
            }
        }
    };

    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {

            if(mLoggingOutput) {
                addLog(formatForLog(location));
            }

            // Got an Android location. Conver to an Intent
            final Intent intent = LocationRelay.toIntent(LocationRelay.EVT_TARGET_LOCATION_UPDATED, location);

            if(intent != null) {
                // Broadcast it system-wide.
                getActivity().sendBroadcast(intent);

                // If connected, send it across the socket connection.
                JSONObject jo = LocationRelay.populateDroneLocationEvent(new JSONObject(), intent);
                if(jo != null) {
                    sendData(jo.toString());
                }
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.v(TAG, "Provider " + provider + " disabled");
        }
    };

    private EditText mIpEditText;
    private EditText mPortEditText;
    private TextView mLogText;
    private ScrollView mLogScroll;
    private Button mConnectButton;
    private Button mSendButton;

    private SocketClient mClient;
    private Thread mClientThread;
    private boolean mListeningForDroneEvents;
    private boolean mListeningForMyLocations;
    private boolean mLoggingOutput;
    private OnFragmentInteractionListener mListener;

    public ClientFragment() {
        super();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_client, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mIpEditText = (EditText)view.findViewById(R.id.edit_ip_addr);
        mPortEditText = (EditText)view.findViewById(R.id.edit_port);

        mLogText = (TextView)view.findViewById(R.id.text_log);
        mLogText.setMovementMethod(new ScrollingMovementMethod());
        mLogScroll = (ScrollView)view.findViewById(R.id.scrollview);

        mIpEditText.addTextChangedListener(mWatcher);
        mPortEditText.addTextChangedListener(mWatcher);

        mConnectButton = (Button)view.findViewById(R.id.btn_connect);
        mSendButton = (Button)view.findViewById(R.id.btn_send);

        mConnectButton.setOnClickListener(mClickListener);
        mSendButton.setOnClickListener(mClickListener);

        DKBridgePrefs prefs = DKBridgePrefs.get();
        mIpEditText.setText(prefs.getLastServerIp());
        mPortEditText.setText(prefs.getLastServerPort());

        initLocationOptions(view);

        ((CheckBox)view.findViewById(R.id.chk_log)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mLoggingOutput = isChecked;
            }
        });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(mClient != null) {
            disconnectClient();
        }

        if(mListeningForDroneEvents) {
            listenForDroneEvents(false);
        }

        if(mListeningForMyLocations) {
            listenForLocationEvents(false);
        }
    }

    void onConnectClick(View v) {
        if(mClient != null) {
            disconnectClient();
        } else {
            connectClient();
        }
    }

    void onSendClick(View v) {
        // Test sending a target location
        getActivity().sendBroadcast(LocationRelay.makeDroneLocationEvent());
//        sendData(new java.util.Date().toString());
    }

    void sendData(String data) {
        if(mClient != null) {
            mClient.send(data);

            if(mLoggingOutput) {
                addLog(data);
            }
        }
    }

    void setButtonStates() {
        boolean enabled = true;

        for(EditText et: new EditText[] { mIpEditText, mPortEditText }) {
            if(TextUtils.isEmpty(et.getText().toString())) {
                enabled = false;
                break;
            }
        }

        mConnectButton.setEnabled(enabled);
    }

    void connectClient() {
        String ip = mIpEditText.getText().toString();
        int port = Integer.valueOf(mPortEditText.getText().toString());

        mClient = new SocketClient(DKBridgeApp.get().getHandler(), mClientListener, ip, port);
        mClientThread = new Thread(mClient);
        mClientThread.start();

        DKBridgePrefs.get().setLastServerIp(ip);
        DKBridgePrefs.get().setLastServerPort(String.valueOf(port));
    }

    void disconnectClient() {
        if(mClient != null && mClient.isConnected()) {
            mClient.cancel();
        }

        if(mClientThread != null) {
            try {
                mClientThread.join(1000);
            } catch(InterruptedException ex) {
                showError(ex);
            }
        }

        mClient = null;
        mClientThread = null;
    }

    void showError(Throwable error) {
        final Activity activity = getActivity();
        if(activity != null && !activity.isDestroyed()) {
            Toast.makeText(activity, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void initLocationOptions(View v) {
        RadioGroup grp = (RadioGroup)v.findViewById(R.id.group_locations);
        grp.setOnCheckedChangeListener(mLocationOptionListener);
    }

    void listenForDroneEvents(boolean listen) {
        if(mListeningForDroneEvents != listen) {
            if(mListeningForDroneEvents) {
                LocationRelay.get().stopListeningForDroneEvents();
            } else {
                LocationRelay.get().listenForDroneEvents(mDroneLocationListener);
            }

            mListeningForDroneEvents = listen;
        }
    }

    void listenForLocationEvents(boolean listen) {
        if(mListeningForMyLocations != listen) {
            if(mListeningForMyLocations) {
                // Stop listening for locations
                LocationAwareness.get().stopLocationUpdates();
            } else {
                // Start listening for locations
                LocationAwareness.get().startLocationUpdates(getActivity(), LocationManager.GPS_PROVIDER, mLocationListener);
            }

            mListeningForMyLocations = listen;
        }
    }

    void addLog(String str) {
        mLogText.append(str + "\n");
        mLogScroll.scrollTo(0, mLogScroll.getBottom());
    }
}
