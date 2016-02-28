package com.fognl.dronekitbridge;

import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.fognl.dronekitbridge.fragments.ClientFragment;
import com.fognl.dronekitbridge.fragments.ServerFragment;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity
        implements
        NavigationView.OnNavigationItemSelectedListener,
        ServerFragment.OnFragmentInteractionListener,
        ClientFragment.OnFragmentInteractionListener {

    static final String TAG = MainActivity.class.getSimpleName();

    private final HashMap<String, Fragment> mFragMap = new HashMap<String, Fragment>();
    private Fragment mMainFragment;
    private ServerFragment mServerFragment;
    private ClientFragment mClientFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        mServerFragment = new ServerFragment();
        mClientFragment = new ClientFragment();

        mFragMap.put(DKBridgePrefs.FRAG_SERVER, mServerFragment);
        mFragMap.put(DKBridgePrefs.FRAG_CLIENT, mClientFragment);

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        setMainFragment(mFragMap.get(DKBridgePrefs.get().getLastFragment()));
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
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    void setMainFragment(Fragment frag) {
        if(mMainFragment != frag) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_holder, frag)
                    .commit();

            // TODO: Yuck. This could be cleaner.
            final String tag = (frag == mServerFragment)? DKBridgePrefs.FRAG_SERVER: DKBridgePrefs.FRAG_CLIENT;
            DKBridgePrefs.get().setLastFragment(tag);
        }
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }
}