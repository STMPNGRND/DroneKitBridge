package com.fognl.dronekitbridge.fragments;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.fognl.dronekitbridge.DKBridgeApp;
import com.fognl.dronekitbridge.DKBridgePrefs;
import com.fognl.dronekitbridge.DeviceListActivity;
import com.fognl.dronekitbridge.R;
import com.fognl.dronekitbridge.comm.BluetoothService;
import com.fognl.dronekitbridge.location.LocationAwareness;
import com.fognl.dronekitbridge.locationrelay.LocationRelay;

import org.json.JSONObject;

public class BluetoothClientFragment extends Fragment {
    static final String TAG = BluetoothClientFragment.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 1001;
    private static final int REQUEST_CONNECT_DEVICE = 2001;

    static String formatForLog(Location loc) {
        return String.format("Location: %.3f/%.3f, speed=%.2f heading=%.2f acc=%.1f", loc.getLatitude(), loc.getLongitude(), loc.getSpeed(), loc.getBearing(), loc.getAccuracy());
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

    private final LocationRelay.DroneLocationListener mDroneLocationListener = new LocationRelay.DroneLocationListener() {
        @Override
        public void onDroneLocationUpdated(String msg) {
            if(mConnected) {
                sendData(msg);
            }
        }
    };

    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {

            if(mLoggingOutput) {
                addLog(formatForLog(location));
            }

            // Got an Android location. Convert to an Intent
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

        public void onStatusChanged(String provider, int status, Bundle extras) { }
        public void onProviderEnabled(String provider) { }

        @Override
        public void onProviderDisabled(String provider) {
            Log.v(TAG, "Provider " + provider + " disabled");
        }
    };

    private final BluetoothService.Listener mBtListener = new BluetoothService.Listener() {
        @Override
        public void onConnecting() {
            addLog(getString(R.string.status_connecting));
        }

        @Override
        public void onConnected() {
            addLog(getString(R.string.status_connected));
            mConnected = true;
            setButtonStates();
        }

        @Override
        public void onConnectionFailed() {
            mConnected = false;
            showError(new Exception(getString(R.string.toast_bt_connect_failed)));
            setButtonStates();
        }

        @Override
        public void onConnectionLost() {
            mConnected = false;
            showError(new Exception(getString(R.string.toast_bt_connection_lost)));
            setButtonStates();
        }

        @Override
        public void onStopped() {
            mConnected = false;
            setButtonStates();
        }

        @Override
        public void onWrite(byte[] data) {
            addLog(new String(data));
        }

        @Override
        public void onRead(byte[] data) {
            addLog(new String(data));
        }
    };

    private TextView mLogText;
    private ScrollView mLogScroll;
    private Button mConnectButton;
    private Button mSendButton;

    private boolean mListeningForDroneEvents;
    private boolean mListeningForMyLocations;
    private boolean mLoggingOutput;
    private boolean mConnected;
    private BluetoothService mBtService;

    public BluetoothClientFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        if(!BluetoothService.isBluetoothAvailable()) {
            Toast.makeText(getActivity(), R.string.toast_bt_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bt_client, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mLogText = (TextView)view.findViewById(R.id.text_log);
        mLogText.setMovementMethod(new ScrollingMovementMethod());
        mLogScroll = (ScrollView)view.findViewById(R.id.scrollview);

        mConnectButton = (Button)view.findViewById(R.id.btn_connect);
        mSendButton = (Button)view.findViewById(R.id.btn_send);

        mConnectButton.setOnClickListener(mClickListener);
        mSendButton.setOnClickListener(mClickListener);

        initLocationOptions(view);

        ((CheckBox)view.findViewById(R.id.chk_log)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mLoggingOutput = isChecked;
                DKBridgePrefs.get().setLogIncoming(mLoggingOutput);
            }
        });

        ((CheckBox)view.findViewById(R.id.chk_log)).setChecked(DKBridgePrefs.get().getLogIncoming());

        setButtonStates();
    }

    @Override
    public void onStart() {
        super.onStart();

        if(BluetoothService.isBluetoothEnabled()) {
            setupService();
        }
        else {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(mConnected) {
            disconnectClient();
        }

        if(mListeningForDroneEvents) {
            listenForDroneEvents(false);
        }

        if(mListeningForMyLocations) {
            listenForLocationEvents(false);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case REQUEST_ENABLE_BT: {
                switch(resultCode) {
                    case Activity.RESULT_OK: {
                        setupService();
                        break;
                    }

                    case Activity.RESULT_CANCELED: {
                        Toast.makeText(getActivity(), R.string.toast_bt_not_enabled, Toast.LENGTH_SHORT).show();
                        break;
                    }
                }

                break;
            }

            case REQUEST_CONNECT_DEVICE: {
                switch(resultCode) {
                    case Activity.RESULT_OK: {
                        connectDevice(data, false);
                        break;
                    }
                }
            }

            default: {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    void onConnectClick(View v) {
        if(mConnected) {
            disconnectClient();
        } else {
            connectClient();
        }
    }

    void onSendClick(View v) {
        // Test sending a target location
//        getActivity().sendBroadcast(LocationRelay.makeDroneLocationEvent());
        sendData("Hey there");
    }

    void sendData(String data) {
        Log.v(TAG, "sendData(): data=" + data);
        mBtService.write(data.getBytes());
    }

    void setButtonStates() {
        boolean enabled = mConnected;
        mConnectButton.setText((mConnected)? R.string.btn_disconnect: R.string.btn_connect);
        mSendButton.setEnabled(enabled);

        mConnectButton.setEnabled(BluetoothService.isBluetoothEnabled());
    }

    void connectClient() {
        mLogText.setText("");
        startActivityForResult(new Intent(getActivity(), DeviceListActivity.class), REQUEST_CONNECT_DEVICE);
    }

    void disconnectClient() {
        if(mBtService != null) {
            mBtService.stop();
        }

        mConnected = false;
        setButtonStates();
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

    void setupService() {
        if(mBtService == null) {
            mBtService = new BluetoothService(DKBridgeApp.get(), mBtListener);
        }
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
        if(mLoggingOutput) {
            mLogText.append(str + "\n");
            mLogScroll.scrollTo(0, mLogScroll.getBottom());
        }
    }

    /**
     * Establish connection with other divice
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    void connectDevice(Intent data, boolean secure) {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if(adapter != null && adapter.isEnabled()) {
            // Get the device MAC address
            String address = data.getExtras()
                    .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
            // Get the BluetoothDevice object
            BluetoothDevice device = adapter.getRemoteDevice(address);
            // Attempt to connect to the device
            mBtService.connect(device, secure);
        }
    }
}
