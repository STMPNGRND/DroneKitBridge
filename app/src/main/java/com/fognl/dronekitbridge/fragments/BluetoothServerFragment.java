package com.fognl.dronekitbridge.fragments;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.fognl.dronekitbridge.DKBridgeApp;
import com.fognl.dronekitbridge.DKBridgePrefs;
import com.fognl.dronekitbridge.R;
import com.fognl.dronekitbridge.comm.BluetoothService;
import com.fognl.dronekitbridge.locationrelay.LocationRelay;

public class BluetoothServerFragment extends Fragment {
    static final String TAG = BluetoothServerFragment.class.getSimpleName();

    private static final String STATE_LOG = "log";
    private static final String STATE_RELAY_LOCATIONS = "relay";
    private static final int REQUEST_ENABLE_BT = 1001;

    private final View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()) {
                case R.id.btn_listen: {
                    onListenClick(v);
                    break;
                }
            }
        }
    };

    private final BluetoothService.Listener mBtListener = new BluetoothService.Listener() {
        @Override
        public void onStarted() {
            Log.v(TAG, "onStarted()");
            mRunning = true;
            setButtonStates();
        }

        @Override
        public void onStopped() {
            Log.v(TAG, "onStopped()");
            mRunning = false;
            setButtonStates();
        }

        @Override
        public void onConnected() {
            Toast.makeText(getActivity(), R.string.toast_bt_connected, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnectionFailed() {
            showError(new Exception("Connection failed"));
            mRunning = false;
            setButtonStates();
        }

        @Override
        public void onConnectionLost() {
            showError(new Exception("Connection lost"));
            mRunning = false;
            setButtonStates();
            mLogText.setText("");
        }

        @Override
        public void onRead(byte[] data) {
            Log.v(TAG, "onRead()");
            final String str = new String(data);
            addLog(new String(data));

            if(mRelayLocations) {
                Intent intent = LocationRelay.parseIntoTargetLocationEvent(str);
                if(intent != null) {
                    Log.v(TAG, "Send " + intent.getAction() + " event");
                    getActivity().sendBroadcast(intent);
                }
            }
        }

        @Override
        public void onWrite(byte[] data) {
            Log.v(TAG, "onWrite()");
            addLog(new String(data));
        }
    };

    private TextView mLogText;
    private ScrollView mLogScroll;
    private Button mStartStopButton;
    private boolean mRunning;
    private String mConnectedDeviceName;

    private boolean mLogIncomingData;
    private boolean mRelayLocations;

    private BluetoothService mBtService = null;

    private String m = null;

    public BluetoothServerFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle state) {
        Log.v(TAG, "onCreate(): state=" + state);
        super.onCreate(state);
        setRetainInstance(true);

        if(!BluetoothService.isBluetoothAvailable()) {
            Toast.makeText(getActivity(), R.string.toast_bt_not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        if(state != null) {
            mLogIncomingData = state.getBoolean(STATE_LOG);
            mRelayLocations = state.getBoolean(STATE_RELAY_LOCATIONS);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.v(TAG, "onSaveInstanceState()");
        super.onSaveInstanceState(outState);

        outState.putBoolean(STATE_LOG, mLogIncomingData);
        outState.putBoolean(STATE_RELAY_LOCATIONS, mRelayLocations);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bt_server, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Log.v(TAG, "onViewCreated(): state=" + savedInstanceState);

        mLogText = (TextView)view.findViewById(R.id.text_log);
        mLogText.setMovementMethod(new ScrollingMovementMethod());
        mLogScroll = (ScrollView)view.findViewById(R.id.scrollview);

        mStartStopButton = (Button)view.findViewById(R.id.btn_listen);
        mStartStopButton.setOnClickListener(mClickListener);

        CheckBox cb = (CheckBox)view.findViewById(R.id.chk_log);
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mLogIncomingData = isChecked;
                DKBridgePrefs.get().setLogIncoming(mLogIncomingData);
            }
        });

        cb.setChecked(mLogIncomingData);

        ((CheckBox)view.findViewById(R.id.chk_relay_locations)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mRelayLocations = isChecked;
                DKBridgePrefs.get().setRelayLocations(mRelayLocations);
            }
        });

        ((CheckBox)view.findViewById(R.id.chk_relay_locations)).setChecked(DKBridgePrefs.get().getRelayLocations());
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

        // Just before actually dying
        Log.v(TAG, "onDestroy()");

        stopServer();
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

            default: {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    void setupService() {
        if(mBtService == null) {
            mBtService = new BluetoothService(DKBridgeApp.get(), mBtListener);
        }
    }

    void onListenClick(View v) {
        if(mRunning) {
            stopServer();
        } else {
            startServer();
        }
    }

    void startServer() {
        mLogText.setText("");
        mBtService.start();
    }

    void stopServer() {
        if(mBtService != null) {
            mBtService.stop();
        }
    }

    void setButtonStates() {
        boolean enabled = BluetoothService.isBluetoothEnabled();

        mStartStopButton.setEnabled(enabled);
        mStartStopButton.setText((mRunning) ? R.string.btn_stop_listening : R.string.btn_listen);
    }

    void showError(Throwable error) {
        final Activity activity = getActivity();
        if(activity != null && !activity.isDestroyed()) {
            Toast.makeText(activity, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void addLog(String str) {
        if(mLogIncomingData) {
            mLogText.append(str + "\n");
            mLogScroll.scrollTo(0, mLogScroll.getBottom());
            mLogScroll.fullScroll(View.FOCUS_DOWN);
        }
    }
}
