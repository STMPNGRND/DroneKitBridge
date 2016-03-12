package com.fognl.dronekitbridge.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
import com.fognl.dronekitbridge.comm.Network;
import com.fognl.dronekitbridge.comm.SocketClient;
import com.fognl.dronekitbridge.comm.WebSocketClient;
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
import java.util.List;

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

    private static final int MSG_STALE_TARGET = 1001;
    private static final long STALE_USER_DELETE_DELAY = 5000;
    private static final long STALE_USER_MAX_AGE = 30000;

    enum RelayType {
        None, Broadcast, Socket
    };

    static class MapState {
        LatLng center;
        float zoom;
    }

    static class StaleLocation {
        UserLocation location;
        private final long time;

        StaleLocation(UserLocation loc) {
            location = loc;
            time = SystemClock.elapsedRealtime();
        }

        long getTime() { return time; }
    }

    private static final int MAP_PADDING = 300;

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
                    onStartStopClick(v);
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

            if(mSelectedUser != null) {
                stopFollowingUser();
            }

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

    private final LocationRelayManager.WsMessageCallback mWsMessageCallback = new LocationRelayManager.WsMessageCallback() {
        @Override
        public void onUserDeleted(String user) {
            Marker marker = mMapMarkers.get(user);
            if(marker != null) {
                marker.remove();
            }

            mMapMarkers.remove(user);
        }

        @Override
        public void onUserLocation(String user, UserLocation location) {
            setMapMarkerFrom(user, location);

            if(mPanToMarkers) {
                zoomToSpanMarkers();
            }
        }

        @Override
        public void onError(Throwable error) {
            showError(error);
        }
    };

    private final WebSocketClient.Listener mWebSocketListener = new WebSocketClient.Listener() {
        @Override
        public void onConnected() {
            Log.v(TAG, "websocket client connected");
        }

        @Override
        public void onDisconnected(int code, String reason) {
            Log.v(TAG, "websocket client disconnected");
        }

        @Override
        public void onText(String text) {
            Toast.makeText(getActivity(), text, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onJsonObject(JSONObject jo) {
            Log.v(TAG, jo.toString());
            LocationRelayManager.handleIncomingWsObject(jo, mWsMessageCallback);
        }

        @Override
        public void onError(Throwable err) {
            showError(err);
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
    private Animation mInTop, mInBottom;
    private Animation mOutTop, mOutBottom;

    private MapState mSavedMapState;
    private WebSocketClient mWebSocketClient;

    private final HashMap<String, Marker> mMapMarkers = new HashMap<String, Marker>();
    private final HashMap<String, StaleLocation> mStaleLocations = new HashMap<String, StaleLocation>();

    private final Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_STALE_TARGET: {
                    Log.v(TAG, "MSG_STALE_TARGET");
                    final long now = SystemClock.elapsedRealtime();

                    final ArrayList<String> toRemove = new ArrayList<String>();

                    for(String user: mStaleLocations.keySet()) {
                        StaleLocation loc = mStaleLocations.get(user);

                        if((now - loc.getTime()) > STALE_USER_MAX_AGE) {
                            deleteUser(user);
                            toRemove.add(user);
                        }
                    }

                    for(String r: toRemove) {
                        mStaleLocations.remove(r);
                    }

                    if(!mStaleLocations.isEmpty()) {
                        mHandler.removeMessages(msg.what);
                        mHandler.sendEmptyMessageDelayed(msg.what, STALE_USER_DELETE_DELAY);
                    }

                    return true;
                }

                default: {
                    return false;
                }
            }
        }
    });

    public TrackerFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        final Context context = getContext();
        mWebSocketClient = new WebSocketClient(context, mWebSocketListener);

        if(Network.isConnected(context)) {
            mWebSocketClient.connect();
        }
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

            if(!TextUtils.isEmpty(mSelectedUser)) {
                showFollowingUser(mSelectedUser);
            }
        }
        else {
            retrieveGroups();
        }

        setButtonStates();
    }

    @Override
    public void onStop() {
        super.onStop();

        DKBridgePrefs prefs = DKBridgePrefs.get();
        prefs.setLastMapCenter(mMap.getCameraPosition().target);
        prefs.setLastMapZoom(mMap.getCameraPosition().zoom);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(mWebSocketClient.isConnected()) {
            mWebSocketClient.disconnect();
        }

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

    void onStartStopClick(View v) {
        if(mRunning) {
            unsubscribe();
            mRunning = false;
            setButtonStates();
        }
        else {
            if(mSelectedGroup != null) {
                subscribeToGroup(mSelectedGroup);
                mRunning = true;
                setButtonStates();
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

        mWebSocketClient.send(LocationRelayManager.getSubscribeToString(mSelectedGroup, mSelectedUser));
        showFollowingUser(user);
    }

    void showFollowingUser(String user) {
        mStatusText.setText(getString(R.string.follow_user_fmt, user));
        mStopFollowingUserButton.setVisibility(View.VISIBLE);
    }

    void stopFollowingUser() {
        mWebSocketClient.send(LocationRelayManager.getUnsubscribeString(mSelectedGroup, mSelectedUser));

        mStatusText.setText("");
        mSelectedUser = null;
        mStopFollowingUserButton.setVisibility(View.GONE);

        for(Marker m: mMapMarkers.values()) {
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
        mMapMarkers.clear();
    }

    void subscribeToGroup(String group) {
        mWebSocketClient.send(LocationRelayManager.getSubscribeToString(group));
    }

    void subscribeToGroupAndUser(String group, String user) {
        mWebSocketClient.send(LocationRelayManager.getSubscribeToString(group, user));
    }

    void unsubscribe() {
        stopFollowingUser();
        mWebSocketClient.send(LocationRelayManager.getUnsubscribeString(mSelectedGroup, null));
        mMapMarkers.clear();
        clearMapMarkers();
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
                } else {
                    DKBridgePrefs prefs = DKBridgePrefs.get();
                    LatLng center = prefs.getLastMapCenter();
                    if (center != null) {
                        float zoom = prefs.getLastMapZoom();

                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, zoom));
                    }
                }
            }
        });
    }

    void reloadMapMarkers() {
        final HashMap<String, Marker> map = new HashMap<String, Marker>(mMapMarkers);

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

            mMapMarkers.put(key, marker);
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

    private float nextMarkerHue() {
        float out = MARKER_HUES[mMarkerHueIndex];

        if(++mMarkerHueIndex >= MARKER_HUES.length-1) {
            mMarkerHueIndex = 0;
        }

        return out;
    }

    void setMapMarkerFrom(String user, UserLocation location) {
        Marker marker = mMapMarkers.get(user);
        final LatLng position = new LatLng(location.getLat(), location.getLng());
        final String snippet = String.format(
                "heading: %.2f speed: %.2f m/s",
                location.getHeading(), location.getSpeed());

        if(marker != null) {
            marker.setPosition(position);
            marker.setSnippet(snippet);

            if(isFollowedUser(user)) {
                if(marker.isInfoWindowShown()) {
                    // force an update (should be a better way to do this)
                    marker.hideInfoWindow();
                }

                marker.showInfoWindow();
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

            mMapMarkers.put(user, marker);
        }

        mStaleLocations.put(user, new StaleLocation(location));

        mHandler.removeMessages(MSG_STALE_TARGET);
        mHandler.sendEmptyMessageDelayed(MSG_STALE_TARGET, STALE_USER_DELETE_DELAY);
    }

    void deleteUser(String user) {
        // Takes care of removing the markers, etc
        // WS message will come back and take care of removing the marker, etc.

        if (isFollowedUser(user)) {
            stopFollowingUser();
        }

        LocationRelayManager.deleteUser(DKBridgeApp.get(), mSelectedGroup, user, new Callback<ServerResponse>() {
            @Override
            public void onResponse(Call<ServerResponse> call, Response<ServerResponse> response) {
                ServerResponse body = response.body();
                if (body != null) {
                    Log.v(TAG, body.getMessage());
                } else {
                    Log.w(TAG, "Body is null. This is a problem on the server.");
                }
            }

            @Override
            public void onFailure(Call<ServerResponse> call, Throwable err) {
                Log.e(TAG, err.getMessage(), err);
            }
        });
    }

    void zoomToSpanMarkers() {
        if(mMapMarkers.size() > 0) {
            LatLngBounds.Builder b = new LatLngBounds.Builder();

            for(Marker marker: mMapMarkers.values()) {
                b.include(marker.getPosition());
            }

            LatLngBounds bounds = b.build();

            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, MAP_PADDING), 500, null);
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
        Log.e(TAG, error.getMessage(), error);

        final Activity activity = getActivity();
        if(activity != null && !activity.isDestroyed()) {
            Toast.makeText(activity, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
