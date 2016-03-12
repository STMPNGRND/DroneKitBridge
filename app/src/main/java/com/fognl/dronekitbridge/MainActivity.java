package com.fognl.dronekitbridge;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.fognl.dronekitbridge.fragments.BluetoothClientFragment;
import com.fognl.dronekitbridge.fragments.BluetoothServerFragment;
import com.fognl.dronekitbridge.fragments.ClientFragment;
import com.fognl.dronekitbridge.fragments.RemoteTargetFragment;
import com.fognl.dronekitbridge.fragments.ServerFragment;
import com.fognl.dronekitbridge.fragments.TrackerFragment;
import com.fognl.dronekitbridge.location.LocationAwareness;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    static final String TAG = MainActivity.class.getSimpleName();

    private final HashMap<String, Fragment> mFragMap = new HashMap<String, Fragment>();
    private Fragment mMainFragment;
    private ServerFragment mServerFragment;
    private ClientFragment mClientFragment;
    private RemoteTargetFragment mRemoteTargetFragment;
    private TrackerFragment mTrackerFragment;
    private BluetoothServerFragment mBtServerFragment;
    private BluetoothClientFragment mBtClientFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        mFragMap.put(DKBridgePrefs.FRAG_SERVER, mServerFragment = new ServerFragment());
        mFragMap.put(DKBridgePrefs.FRAG_CLIENT, mClientFragment = new ClientFragment());
        mFragMap.put(DKBridgePrefs.FRAG_REMOTE, mRemoteTargetFragment = new RemoteTargetFragment());
        mFragMap.put(DKBridgePrefs.FRAG_TRACKER, mTrackerFragment = new TrackerFragment());
        mFragMap.put(DKBridgePrefs.FRAG_BT_SERVER, mBtServerFragment = new BluetoothServerFragment());
        mFragMap.put(DKBridgePrefs.FRAG_BT_CLIENT, mBtClientFragment = new BluetoothClientFragment());

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        if(savedInstanceState == null) {
            setMainFragment(mFragMap.get(DKBridgePrefs.get().getLastFragment()));
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        switch(id) {
            case R.id.nav_server: {
                setMainFragment(mServerFragment);
                break;
            }

            case R.id.nav_client: {
                setMainFragment(mClientFragment);
                break;
            }

            case R.id.nav_bt_server: {
                setMainFragment(mBtServerFragment);
                break;
            }

            case R.id.nav_bt_client: {
                setMainFragment(mBtClientFragment);
                break;
            }

            case R.id.nav_remote_track: {
                setMainFragment(mRemoteTargetFragment);
                break;
            }

            case R.id.nav_tracker: {
                setMainFragment(mTrackerFragment);
                break;
            }
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        if(requestCode == LocationAwareness.PERMISSION_REQUEST_LOCATION) {
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LocationAwareness.get().onPermissionGranted();
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    void setMainFragment(Fragment frag) {
        if(mMainFragment != frag) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_holder, frag)
                    .commit();

            String tag = null;
            if(frag == mServerFragment) tag = DKBridgePrefs.FRAG_SERVER;
            if(frag == mClientFragment) tag = DKBridgePrefs.FRAG_CLIENT;
            if(frag == mRemoteTargetFragment) tag = DKBridgePrefs.FRAG_REMOTE;
            if(frag == mTrackerFragment) tag = DKBridgePrefs.FRAG_TRACKER;
            if(frag == mBtServerFragment) tag = DKBridgePrefs.FRAG_BT_SERVER;
            if(frag == mBtClientFragment) tag = DKBridgePrefs.FRAG_BT_CLIENT;

            if(tag != null) {
                DKBridgePrefs.get().setLastFragment(tag);
            }
        }
    }
}
