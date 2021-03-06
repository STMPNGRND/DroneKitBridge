package com.fognl.dronekitbridge.fragments;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.fognl.dronekitbridge.DKBridgeApp;
import com.fognl.dronekitbridge.DKBridgePrefs;
import com.fognl.dronekitbridge.R;
import com.fognl.dronekitbridge.comm.Network;
import com.fognl.dronekitbridge.comm.SocketServer;
import com.fognl.dronekitbridge.locationrelay.LocationRelay;

public class ServerFragment extends Fragment {
    static final String TAG = ServerFragment.class.getSimpleName();

    private static final String STATE_LOG = "log";
    private static final String STATE_RELAY_LOCATIONS = "relay";

    private final View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()) {
                case R.id.btn_listen: {
                    onListenClick(v);
                    break;
                }

                case R.id.btn_copy: {
                    onCopyClick(v);
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

    private final SocketServer.Listener mServerListener = new SocketServer.Listener() {
        @Override
        public void onStarted() {
            setButtonStates();
        }

        @Override
        public void onStopped() {
            setButtonStates();
        }

        @Override
        public void onClientDisconnected() {
            Toast.makeText(getActivity(), R.string.toast_client_disconnected, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onClientConnected() {
            Toast.makeText(getActivity(), R.string.toast_client_connected, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onData(String data) {
            Log.v(TAG, "onData(): data=" + data);

            if(mLogIncomingData) {
                addLog(data);
            }

            if(mRelayLocations) {
                Intent intent = LocationRelay.parseIntoTargetLocationEvent(data);
                if(intent != null) {
                    Log.v(TAG, "Send " + intent.getAction() + " event");
                    getActivity().sendBroadcast(intent);
                }
            }
        }

        @Override
        public void onError(Throwable error) {
            showError(error);
            Log.e(TAG, error.getMessage(), error);
        }

        @Override
        public void onLocalIpFound(String ip) {
            Log.v(TAG, "onLocalIpFound(): ip=" + ip);
        }
    };

    private TextView mIpAddrText;
    private TextView mLogText;
    private ScrollView mLogScroll;
    private EditText mPortEditText;
    private Button mButton;

    private SocketServer mServer;
    private Thread mServerThread;
    private boolean mLogIncomingData;
    private boolean mRelayLocations;

    private String m = null;

    public ServerFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle state) {
        Log.v(TAG, "onCreate(): state=" + state);
        super.onCreate(state);
        setRetainInstance(true);

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
        return inflater.inflate(R.layout.fragment_server, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Log.v(TAG, "onViewCreated(): state=" + savedInstanceState);

        mIpAddrText = (TextView)view.findViewById(R.id.text_ip_addr);

        mLogText = (TextView)view.findViewById(R.id.text_log);
        mLogText.setMovementMethod(new ScrollingMovementMethod());
        mLogScroll = (ScrollView)view.findViewById(R.id.scrollview);

        mButton = (Button)view.findViewById(R.id.btn_listen);
        mPortEditText = (EditText)view.findViewById(R.id.edit_port);
        mPortEditText.addTextChangedListener(mWatcher);

        mButton.setOnClickListener(mClickListener);
        view.findViewById(R.id.btn_copy).setOnClickListener(mClickListener);

        mPortEditText.setText(String.valueOf(SocketServer.DEFAULT_PORT));

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

        if(Network.isOnWifi(DKBridgeApp.get())) {
            try {
                mIpAddrText.setText(SocketServer.getLocalIpAddress());
            } catch(Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
            }
        }
        else {
            mIpAddrText.setText(R.string.no_wifi_connection);
        }

        ((CheckBox)view.findViewById(R.id.chk_relay_locations)).setChecked(DKBridgePrefs.get().getRelayLocations());
        ((CheckBox)view.findViewById(R.id.chk_log)).setChecked(DKBridgePrefs.get().getLogIncoming());
    }

    @Override
    public void onResume() {
        super.onResume();

        if(!Network.isOnWifi(DKBridgeApp.get())) {
            showError(new Exception(getString(R.string.toast_not_on_wifi)));
        }

        mButton.setEnabled(Network.isOnWifi(DKBridgeApp.get()));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Just before actually dying
        Log.v(TAG, "onDestroy()");

        if(mServer != null) {
            try {
                stopServer();
            }
            catch(Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
            }
        }
    }

    void onListenClick(View v) {
        if(mServer != null) {
            // Already running, stop
            stopServer();
        } else {
            // Not running, start.
            startServer();
        }
    }

    void onCopyClick(View v) {
        ClipboardManager clipMan = (ClipboardManager)getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        String ip = String.format("%s:%s", mIpAddrText.getText().toString(), mPortEditText.getText().toString());

        ClipData clip = ClipData.newPlainText(getString(R.string.ip_addr_port), ip);
        clipMan.setPrimaryClip(clip);

        Toast.makeText(getActivity(), R.string.toast_copied, Toast.LENGTH_SHORT).show();
    }

    void startServer() {
        mLogText.setText("");
        mServer = new SocketServer(DKBridgeApp.get().getHandler(), mServerListener);
        mServerThread = new Thread(mServer);
        mServerThread.start();
    }

    void stopServer() {
        mServer.cancel();

        try {
            mServerThread.join(5000);
        }
        catch(InterruptedException ex) {
            Log.e(TAG, ex.getMessage(), ex);
        }

        mServer = null;
        mServerThread = null;
    }

    void setButtonStates() {
        boolean enabled = true;

        if(TextUtils.isEmpty(mPortEditText.getText().toString())) {
            enabled = false;
        }

        mButton.setEnabled(enabled);
        mButton.setText((mServer != null) ? R.string.btn_stop_listening: R.string.btn_listen);
    }

    void showError(Throwable error) {
        final Activity activity = getActivity();
        if(activity != null && !activity.isDestroyed()) {
            Toast.makeText(activity, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void addLog(String str) {
        mLogText.append(str + "\n");
        mLogScroll.scrollTo(0, mLogScroll.getBottom());
        mLogScroll.fullScroll(View.FOCUS_DOWN);
    }
}
