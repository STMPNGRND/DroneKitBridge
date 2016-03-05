package com.fognl.dronekitbridge.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.fognl.dronekitbridge.DKBridgeApp;
import com.fognl.dronekitbridge.DKBridgePrefs;
import com.fognl.dronekitbridge.R;
import com.fognl.dronekitbridge.comm.SocketClient;
import com.fognl.dronekitbridge.locationrelay.LocationRelay;
import com.fognl.dronekitbridge.locationrelay.LocationRelayManager;
import com.fognl.dronekitbridge.speech.Speech;
import com.fognl.dronekitbridge.web.ServerResponse;
import com.fognl.dronekitbridge.web.UserLocation;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TrackerFragment extends Fragment {
    static final String TAG = TrackerFragment.class.getSimpleName();

    enum RelayType {
        None, Broadcast, Socket
    };

    private static final long PING_INTERVAL = 2000;
    private static final float[] MARKER_HUES = new float[] {
            BitmapDescriptorFactory.HUE_RED,
            BitmapDescriptorFactory.HUE_YELLOW,
            BitmapDescriptorFactory.HUE_ORANGE,
            BitmapDescriptorFactory.HUE_MAGENTA,
            BitmapDescriptorFactory.HUE_BLUE,
            BitmapDescriptorFactory.HUE_CYAN,
            BitmapDescriptorFactory.HUE_AZURE,
            BitmapDescriptorFactory.HUE_VIOLET,
            BitmapDescriptorFactory.HUE_GREEN,
            BitmapDescriptorFactory.HUE_ROSE
    };

    private final View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()) {
                case R.id.btn_start: {
                    onStartClick(v);
                    break;
                }

                case R.id.btn_refresh: {
                    onRefreshClick(v);
                    break;
                }

                case R.id.btn_stop_following_user: {
                    stopFollowingUser();
                    break;
                }

                case R.id.btn_connect: {
                    onConnectClick(v);
                    break;
                }
            }
        }
    };

    private final GoogleMap.OnMarkerClickListener mMarkerClickListener = new GoogleMap.OnMarkerClickListener() {
        @Override
        public boolean onMarkerClick(Marker marker) {
            Log.v(TAG, "marker.title=" + marker.getTitle());

            String user = marker.getTitle();
            followUser(user);

            return false;
        }
    };

    private final SocketClient.Listener mClientListener = new SocketClient.Listener() {
        @Override
        public void onConnected() {
            mConnectButton.setText(R.string.btn_disconnect);
        }

        @Override
        public void onConnectFailed(Throwable error) {
            Toast.makeText(getActivity(), R.string.toast_client_connect_failed, Toast.LENGTH_SHORT).show();
            disconnectClient();
        }

        @Override
        public void onDisconnected() {
            mConnectButton.setText(R.string.btn_connect);
        }

        @Override
        public void onError(Throwable error) {
            showError(error);
        }
    };

    private final TextWatcher mWatcher = new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        public void onTextChanged(CharSequence s, int start, int before, int count) { }

        @Override
        public void afterTextChanged(Editable s) {
            setConnectButtonStates();
        }
    };

    private final Handler mHandler = new android.os.Handler();

    private final Runnable mPingGroup = new Runnable() {
        @Override
        public void run() {
            // Ping the current group's locations and update the map markers.
            Log.v(TAG, "mPingGroup " + mSelectedGroup + " time=" + SystemClock.elapsedRealtime());
            doPingGroup(mSelectedGroup);
        }
    };

    private GoogleMap mMap;
    private Spinner mGroupsSpinner;
    private Button mStartButton;
    private Button mStopFollowingUserButton;
    private TextView mStatusText;

    // connection panel
    private View mConnectionPanel;
    private EditText mIpEditText;
    private EditText mPortEditText;
    private Button mConnectButton;

    private String mSelectedGroup;
    private String mSelectedUser;
    private boolean mRunning;
    private int mMarkerHueIndex;
    private boolean mPanToMarkers = false;
    private RelayType mRelayType = RelayType.None;

    private SocketClient mClient;
    private Thread mClientThread;

    private final HashMap<String, Marker> mMarkers = new HashMap<String, Marker>();
    private final HashMap<String, UserLocation> mLastUserLocations = new HashMap<String, UserLocation>();

    public TrackerFragment() {
        super();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tracker, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mStatusText = (TextView)view.findViewById(R.id.text_status);

        mStartButton = (Button)view.findViewById(R.id.btn_start);
        mStartButton.setOnClickListener(mClickListener);

        mStopFollowingUserButton = (Button)view.findViewById(R.id.btn_stop_following_user);
        mStopFollowingUserButton.setOnClickListener(mClickListener);
        mStopFollowingUserButton.setVisibility(View.GONE);

        view.findViewById(R.id.btn_refresh).setOnClickListener(mClickListener);

        ((CheckBox)view.findViewById(R.id.chk_pan_map)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPanToMarkers = isChecked;
                DKBridgePrefs.get().setPanToFollow(mPanToMarkers);
            }
        });

        initConnectionPanel(view);
        initGroupSpinner(view);
        initMap(view);
        initRelayTypes(view);

        setDefaults(view);

        retrieveGroups();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        stopPinging();

        if(mClient != null) {
            disconnectClient();
        }
    }

    void onRefreshClick(View v) {
        retrieveGroups();
    }

    void onStartClick(View v) {
        if(mRunning) {
            stopPinging();
        }
        else {
            if(mSelectedGroup != null) {
                startPinging(mSelectedGroup);
            }
        }
    }

    void onConnectClick(View v) {
        if(mClient != null) {
            disconnectClient();
        } else {
            connectClient();
        }
    }

    void followGroup(String group) {
        if(group != null && !group.equals(mSelectedGroup)) {
            clearMapMarkers();
            mSelectedGroup = group;
        }
    }

    void followUser(String user) {
        Log.v(TAG, "followUser(): user=" + user);
        mSelectedUser = user;

        mStatusText.setText(getString(R.string.follow_user_fmt, user));
        mStopFollowingUserButton.setVisibility(View.VISIBLE);
    }

    void stopFollowingUser() {
        mStatusText.setText("");
        mSelectedUser = null;
        mStopFollowingUserButton.setVisibility(View.GONE);

        for(Marker m: mMarkers.values()) {
            m.hideInfoWindow();
        }
    }

    boolean isFollowedUser(String user) {
        return (user != null && user.equals(mSelectedUser));
    }

    void warnFollowedUserHasDied(String user) {
        final String str = getString(R.string.say_warn_followed_user_died);
        showError(new Exception(str));
        Speech.get().say(str);
        stopFollowingUser();
    }

    void sendFollowedUserLocation(UserLocation location) {
        final Intent intent = LocationRelay.toIntent(LocationRelay.EVT_TARGET_LOCATION_UPDATED, location);

        switch(mRelayType) {
            case Broadcast: {
                Log.v(TAG, "Broadcast " + location);
                if(intent != null) {
                    DKBridgeApp.get().sendBroadcast(intent);
                }
                break;
            }

            case Socket: {
                if(mClient != null) {
                    JSONObject jo = LocationRelay.populateDroneLocationEvent(new JSONObject(), intent);
                    if(jo != null) {
                        mClient.send(jo.toString());
                    }
                }
                break;
            }
        }
    }

    void clearMapMarkers() {
        mMap.clear();
        mMarkers.clear();
    }

    void startPinging(String group) {
        mHandler.removeCallbacks(mPingGroup);

        mRunning = true;
        mHandler.post(mPingGroup);
        mStartButton.setText(R.string.btn_stop);
        setButtonStates();
    }

    void stopPinging() {
        mHandler.removeCallbacks(mPingGroup);
        mRunning = false;
        mStartButton.setText(R.string.btn_start);
        setButtonStates();
    }

    void retrieveGroups() {
        LocationRelayManager.retrieveGroups(DKBridgeApp.get(), new Callback<List<String>>() {
            @Override
            public void onResponse(Call<List<String>> call, Response<List<String>> response) {
                List<String> list = response.body();
                fillAdapterWith(list);
            }

            @Override
            public void onFailure(Call<List<String>> call, Throwable err) {
                showError(err);
                Log.e(TAG, err.getMessage(), err);
            }
        });
    }

    void fillAdapterWith(List<String> list) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(DKBridgeApp.get(), android.R.layout.simple_spinner_item, android.R.id.text1, list);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mGroupsSpinner.setAdapter(adapter);
    }

    void setButtonStates() {
        final Activity activity = getActivity();
        if(activity != null && !activity.isDestroyed()) {
            final View view = getView();
            if(view != null) {
                mGroupsSpinner.setEnabled(!mRunning);
                getView().findViewById(R.id.btn_refresh).setEnabled(!mRunning);
            }
        }
    }

    void setConnectButtonStates() {
        boolean enabled = true;

        for(EditText et: new EditText[] { mIpEditText, mPortEditText }) {
            if(TextUtils.isEmpty(et.getText().toString())) {
                enabled = false;
                break;
            }
        }

        mConnectButton.setEnabled(enabled);
    }

    void initMap(View view) {
        SupportMapFragment fragment = (SupportMapFragment)getChildFragmentManager().findFragmentById(R.id.map);
        fragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                Log.v(TAG, "onMapReady()");
                mMap = googleMap;
                mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                mMap.getUiSettings().setMapToolbarEnabled(false);
                mMap.setOnMarkerClickListener(mMarkerClickListener);
                setMyLocation();
            }
        });
    }

    void setDefaults(View view) {
        DKBridgePrefs prefs = DKBridgePrefs.get();

        mIpEditText.setText(prefs.getLastServerIp());
        mPortEditText.setText(prefs.getLastServerPort());

        String relayType = prefs.getTrackerRelayType();
        if(relayType != null) {
            int checkId = 0;
            switch(relayType) {
                case DKBridgePrefs.RELAY_TYPE_BCAST: {
                    checkId = R.id.rdo_broadcast;
                    break;
                }

                case DKBridgePrefs.RELAY_TYPE_SOCKET: {
                    checkId = R.id.rdo_socket;
                    break;
                }
            }

            if(checkId != 0) {
                ((RadioButton)view.findViewById(checkId)).setChecked(true);
            }
        }

        ((CheckBox)view.findViewById(R.id.chk_pan_map)).setChecked(prefs.getPanToFollow());
    }

    void initConnectionPanel(View view) {
        mConnectionPanel = view.findViewById(R.id.layout_connection_panel);
        mIpEditText = (EditText)view.findViewById(R.id.edit_ip_addr);
        mPortEditText = (EditText)view.findViewById(R.id.edit_port);

        mIpEditText.addTextChangedListener(mWatcher);
        mPortEditText.addTextChangedListener(mWatcher);

        mConnectButton = (Button)view.findViewById(R.id.btn_connect);
        mConnectButton.setOnClickListener(mClickListener);

        mConnectionPanel.setVisibility(View.GONE);
    }

    void initGroupSpinner(View view) {
        mGroupsSpinner = (Spinner)view.findViewById(R.id.spin_groups);
        mGroupsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String groupId = (String) parent.getAdapter().getItem(position);
                followGroup(groupId);
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    void initRelayTypes(View v) {
        RadioGroup group = (RadioGroup)v.findViewById(R.id.grp_send_type);
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.rdo_broadcast: {
                        setRelayType(RelayType.Broadcast);
                        break;
                    }

                    case R.id.rdo_socket: {
                        setRelayType(RelayType.Socket);
                        break;
                    }
                }
            }
        });
    }

    void setMyLocation() {
        LocationManager man = (LocationManager) DKBridgeApp.get().getSystemService(Context.LOCATION_SERVICE);
        try {
            Location last = man.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(last != null) {
                LatLng ll = new LatLng(last.getLatitude(), last.getLongitude());
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(ll, 20));
            }
        }
        catch(SecurityException ex) {
            Log.e(TAG, ex.getMessage(), ex);
        }
        catch(Throwable ex) {
            Log.e(TAG, ex.getMessage(), ex);
        }
    }

    void doPingGroup(String group) {
        LocationRelayManager.retrieveGroupLocations(DKBridgeApp.get(), group, new LocationRelayManager.RelayCallback<Map<String, UserLocation>>() {
            @Override
            public void complete(Map<String, UserLocation> result) {
                setMapMarkersFrom(result);

                if (mRunning) {
                    mHandler.postDelayed(mPingGroup, PING_INTERVAL);
                }
            }

            @Override
            public void error(Throwable error) {
                Log.e(TAG, error.getMessage(), error);
                showError(error);
            }
        });
    }

    private float nextMarkerHue() {
        float out = MARKER_HUES[mMarkerHueIndex];

        if(++mMarkerHueIndex >= MARKER_HUES.length-1) {
            mMarkerHueIndex = 0;
        }

        return out;
    }

    void setMapMarkersFrom(Map<String, UserLocation> map) {

        // Users that were here last time
        final Set<String> staleUsers = new HashSet<String>(mMarkers.keySet());

        // Users that haven't updated in a long time
        final Set<String> deadUsers = new HashSet<String>();

        final long now = SystemClock.elapsedRealtime();

        for(String user : map.keySet()) {
            final UserLocation location = map.get(user);
            Log.v(TAG, "setMarkers: user=" + user + " location=" + location);

            UserLocation last = mLastUserLocations.get(user);
            if(last != null) {
                if(last.equals(location)) {
                    Log.v(TAG, user + " is stale... stopped reporting");
                    deadUsers.add(user);

                    if(isFollowedUser(user)) {
                        warnFollowedUserHasDied(user);
                    }

                    continue;
                }
            }

            mLastUserLocations.put(user, location);

            final LatLng position = new LatLng(location.getLat(), location.getLng());
            final String snippet = String.format(
                    "heading: %.2f speed: %.2f m/s",
                    location.getHeading(), location.getSpeed());
            Marker marker = mMarkers.get(user);

            if(marker != null) {
                Log.v(TAG, "Move " + user + " to " + position);
                marker.setPosition(position);
                marker.setSnippet(snippet);
            }
            else {
                Log.v(TAG, user + " appeared at " + position);

                marker = mMap.addMarker(
                    new MarkerOptions()
                            .position(position)
                            .title(user)
                            .snippet(snippet)
                            .icon(BitmapDescriptorFactory.defaultMarker(nextMarkerHue()))
                            .alpha(0.5f)
                            .draggable(false)
                );

                mMarkers.put(user, marker);
            }

            staleUsers.remove(user);

            if(isFollowedUser(user)) {
                sendFollowedUserLocation(location);
            }
        }

        // Remove markers for users that aren't reporting anymore
        for(String user: staleUsers) {
            Marker marker = mMarkers.get(user);
            if(marker != null) {
                marker.remove();
            }

            if(isFollowedUser(user)) {
                warnFollowedUserHasDied(user);
            }
        }

        if(mPanToMarkers) {
            zoomToSpanMarkers();
        }

        if(!deadUsers.isEmpty()) {
            deleteUsers(deadUsers);
        }
    }

    void deleteUsers(Iterable<String> users) {
        if(mSelectedGroup != null) {
            Log.v(TAG, "deleteUsers(): users=" + users);

            for(String user: users) {
                mMarkers.remove(user);

                LocationRelayManager.deleteUser(DKBridgeApp.get(), mSelectedGroup, user, new Callback<ServerResponse>() {
                    @Override
                    public void onResponse(Call<ServerResponse> call, Response<ServerResponse> response) {
                        ServerResponse body = response.body();
                        if(body != null) {
                            Log.v(TAG, body.getMessage());
                        }
                        else {
                            Log.w(TAG, "Body is null. This is a problem on the server.");
                        }
                    }

                    @Override
                    public void onFailure(Call<ServerResponse> call, Throwable err) {
                        Log.e(TAG, err.getMessage(), err);
                    }
                });
            }
        }
    }

    void zoomToSpanMarkers() {
        if(mMarkers.size() > 0) {
            LatLngBounds.Builder b = new LatLngBounds.Builder();

            for(Marker marker: mMarkers.values()) {
                b.include(marker.getPosition());
            }

            LatLngBounds bounds = b.build();

            final int padding = 200;
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
        }
    }

    void setRelayType(RelayType type) {
        if(mRelayType != type) {
            String prefType = null;

            switch(type) {
                case Broadcast: {
                    mConnectionPanel.setVisibility(View.GONE);
                    prefType = DKBridgePrefs.RELAY_TYPE_BCAST;
                    break;
                }

                case Socket: {
                    mConnectionPanel.setVisibility(View.VISIBLE);
                    prefType = DKBridgePrefs.RELAY_TYPE_SOCKET;
                    break;
                }

                default: {
                    mConnectionPanel.setVisibility(View.GONE);
                    Log.v(TAG, "relay type set to " + type.toString());
                    break;
                }
            }

            if(prefType != null) {
                DKBridgePrefs.get().setTrackerRelayType(prefType);
            }
        }

        mRelayType = type;
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
}
