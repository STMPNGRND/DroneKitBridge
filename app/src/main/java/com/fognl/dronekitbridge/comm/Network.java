package com.fognl.dronekitbridge.comm;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

/**
 * Created by kellys on 2/27/16.
 */
public class Network {
    public static boolean isOnWifi(Context context) {
        ConnectivityManager conMan = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo net = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return (net != null && net.isConnected());
    }

    public static WifiManager getWifiManager(Context c) {
        return (WifiManager)c.getSystemService(Context.WIFI_SERVICE);
    }
}
