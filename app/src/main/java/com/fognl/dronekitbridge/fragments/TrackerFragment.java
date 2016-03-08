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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
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

import java.util.ArrayList;
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

    private static final String
        STATE_RUNNING = "running"
    ,   STATE_SELECTED_GROUP = "selected_group"
    ,   STATE_SELECTED_USER = "selected_user"
    ,   STATE_GROUP_LIST = "group_list"
    ,   STATE_MAP_CENTER = "center_ll"
    ,   STATE_MAP_ZOOM = "map_zoom"
    ;

    enum RelayType {
        None, Broadcast, Socket
    };

    static class MapState {
        LatLng center;
        float zoom;
    }

    static class StaleLocation {
        UserLocation location;
        int staleCount;
        private boolean stale = false;

        StaleLocation(UserLocation loc) {
            location = loc;
        }

        void checkAndRemember(UserLocation loc) {
            if(location.equals(loc)) {
                ++staleCount;
            }
            else {
                --staleCount;
            }

            location = loc;
        }

        boolean isStale() {
            return (staleCount >= 10);
        }

        int getStaleCount() { return staleCount; }
    }

    private static final int MAP_PADDING = 200;
    private static final long DEF_PING_INTERVAL = 1000;
    private static final long SLOW_PING_INTERVAL = 5000;

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

                case R.id.layout_top: {
                    onTitleAreaClick(v);
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
    private View mFieldsLayout;
    private View mBottomBar;

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
    private long mPingInterval = DEF_PING_INTERVAL;
    private Animation mInTop, mInBottom;
    private Animation mOutTop, mOutBottom;

    private MapState mSavedMapState;

    private final HashMap<String, Marker> mMarkers = new HashMap<String, Marker>();
    private final HashMap<String, StaleLocation> mStaleLocations = new HashMap<String, StaleLocation>();

    public TrackerFragment() {
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
        return inflater.inflate(R.layout.fragment_tracker, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle state) {
        super.onViewCreated(view, state);

        mInBottom = AnimationUtils.loadAnimation(getContext(), R.anim.in_bottom);
        mOutBottom = AnimationUtils.loadAnimation(getContext(), R.anim.out_bottom);
        mInTop = AnimationUtils.loadAnimation(getContext(), R.anim.in_top);
        mOutTop = AnimationUtils.loadAnimation(getContext(), R.anim.out_top);

        mFieldsLayout = view.findViewById(R.id.layout_fields);
        mBottomBar = view.findViewById(R.id.bottombar);

        mStatusText = (TextView)view.findViewById(R.id.text_status);

        mStartButton = (Button)view.findViewById(R.id.btn_start);
        mStartButton.setOnClickListener(mClickListener);

        mStopFollowingUserButton = (Button)view.findViewById(R.id.btn_stop_following_user);
        mStopFollowingUserButton.setOnClickListener(mClickListener);
        mStopFollowingUserButton.setVisibility(View.GONE);

        view.findViewById(R.id.btn_refresh).setOnClickListener(mClickListener);
        view.findViewById(R.id.layout_top).setOnClickListener(mClickListener);

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

        if(state != null) {
            mRunning = state.getBoolean(STATE_RUNNING);
            mSelectedGroup = state.getString(STATE_SELECTED_GROUP);
            mSelectedUser = state.getString(STATE_SELECTED_USER);

            mSavedMapState = new MapState();
            mSavedMapState.center = state.getParcelable(STATE_MAP_CENTER);
            mSavedMapState.zoom = state.getFloat(STATE_MAP_ZOOM);

            fillAdapterWith(state.getStringArrayList(STATE_GROUP_LIST));

            if(mSelectedGroup != null && mRunning) {
                startPinging(mSelectedGroup);
            }
        }
        else {
            retrieveGroups();
        }

        setButtonStates();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        stopPinging();

        if(mClient != null) {
            disconnectClient();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        final ArrayList<String> groups = getGroupsFromSpinner();

        outState.putBoolean(STATE_RUNNING, mRunning);
        outState.putString(STATE_SELECTED_GROUP, mSelectedGroup);
        outState.putString(STATE_SELECTED_USER, mSelectedUser);
        outState.putStringArrayList(STATE_GROUP_LIST, groups);
        outState.putParcelable(STATE_MAP_CENTER, mMap.getCameraPosition().target);
        outState.putFloat(STATE_MAP_ZOOM, mMap.getCameraPosition().zoom);
    }

    ArrayList<String> getGroupsFromSpinner() {
        final ArrayList<String> list = new ArrayList<String>();

        final SpinnerAdapter adapter = mGroupsSpinner.getAdapter();
        final int size = adapter.getCount();

        for(int i = 0; i < size; ++i) {
            list.add((String)adapter.getItem(i));
        }

        return list;
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

    void onTitleAreaClick(View v) {
        final int viz = (mFieldsLayout.isShown())? View.GONE: View.VISIBLE;

        Animation bottom = (viz == View.VISIBLE)? mInBottom: mOutBottom;
        Animation top = (viz == View.VISIBLE)? mInTop: mOutTop;

        mFieldsLayout.startAnimation(top);
        mFieldsLayout.setVisibility(viz);

        mBottomBar.startAnimation(bottom);
        mBottomBar.setVisibility(viz);
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
        mPingInterval = DEF_PING_INTERVAL;
        mRunning = true;
        mHandler.post(mPingGroup);
        setButtonStates();
    }

    void stopPinging() {
        mHandler.removeCallbacks(mPingGroup);
        mRunning = false;
        stopFollowingUser();
        clearMapMarkers();
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
        final Context context = DKBridgeApp.get();

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, android.R.id.text1, list) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                ((TextView)v).setTextColor(context.getResources().getColor(R.color.text_combo_top));
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                ((TextView)v).setTextColor(context.getResources().getColor(R.color.text_combo));
                return v;
            }
        };

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
                mStartButton.setText(mRunning? R.string.btn_stop: R.string.btn_start);
                mConnectButton.setText((mClient != null)? R.string.btn_disconnect: R.string.btn_connect);
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

                if (mSavedMapState != null) {
                    reloadMapMarkers();
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mSavedMapState.center, mSavedMapState.zoom));

                    mSavedMapState = null;
                }

                setMyLocation();
            }
        });
    }

    void reloadMapMarkers() {
        final HashMap<String, Marker> map = new HashMap<String, Marker>(mMarkers);

        for(String key: map.keySet()) {
            Marker m = map.get(key);

            Marker marker = mMap.addMarker(
                    new MarkerOptions()
                            .position(m.getPosition())
                            .title(key)
                            .snippet(m.getSnippet())
                            .icon(BitmapDescriptorFactory.defaultMarker(nextMarkerHue()))
                            .alpha(0.7f)
                            .draggable(false)
            );

            mMarkers.put(key, marker);
        }
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
                    mHandler.postDelayed(mPingGroup, mPingInterval);
                }
            }

            @Override
            public void error(Throwable error) {
                Log.e(TAG, error.getMessage(), error);
                showError(error);
                stopPinging();
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
        final Set<String> usersWhoLeft = new HashSet<String>(mMarkers.keySet());

        // Users that haven't updated in a long time
        final Set<String> deadUsers = new HashSet<String>();

        final long now = SystemClock.elapsedRealtime();
        long totalDiff = 0;

        for(String user : map.keySet()) {
            final UserLocation location = map.get(user);

            StaleLocation stale = mStaleLocations.get(user);

            if(stale != null) {
                long diff = location.getTime() - stale.location.getTime();
                totalDiff += diff;

                stale.checkAndRemember(location);

                if(stale.isStale()) {
                    Log.v(TAG, String.format("%s hasn't reported in %d cycles. Must be dead", user, stale.getStaleCount()));
                    deadUsers.add(user);
                    mStaleLocations.remove(user);

                    if(isFollowedUser(user)) {
                        warnFollowedUserHasDied(user);
                    }

                    continue;
                }
            }
            else {
                mStaleLocations.put(user, stale = new StaleLocation(location));
            }

            final LatLng position = new LatLng(location.getLat(), location.getLng());
            final String snippet = String.format(
                    "heading: %.2f speed: %.2f m/s",
                    location.getHeading(), location.getSpeed());
            Marker marker = mMarkers.get(user);

            if(marker != null) {
                marker.setPosition(position);
                marker.setSnippet(snippet);

                if(isFollowedUser(user)) {
                    if(marker.isInfoWindowShown()) {
                        // force an update (should be a better way to do this)
                        marker.hideInfoWindow();
                        marker.showInfoWindow();
                    }
                }
            }
            else {
                marker = mMap.addMarker(
                    new MarkerOptions()
                            .position(position)
                            .title(user)
                            .snippet(snippet)
                            .icon(BitmapDescriptorFactory.defaultMarker(nextMarkerHue()))
                            .alpha(0.7f)
                            .draggable(false)
                );

                mMarkers.put(user, marker);
            }

            usersWhoLeft.remove(user);

            if(isFollowedUser(user)) {
                sendFollowedUserLocation(location);
            }
        }

        if(!map.isEmpty()) {
            mPingInterval = DEF_PING_INTERVAL;
            Log.v(TAG, "mPingInterval=" + mPingInterval);
        }
        else {
            mPingInterval = SLOW_PING_INTERVAL;
            Log.v(TAG, "mPingInterval=" + mPingInterval);
        }

        // Remove markers for users that aren't reporting anymore
        for(String user: usersWhoLeft) {
            Marker marker = mMarkers.get(user);
            if(marker != null) {
                marker.remove();
                mMarkers.remove(user);
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

            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, MAP_PADDING));
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
