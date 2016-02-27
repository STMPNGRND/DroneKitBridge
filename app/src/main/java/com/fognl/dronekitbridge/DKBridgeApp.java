package com.fognl.dronekitbridge;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

/**
 * Created by kellys on 2/26/16.
 */
public class DKBridgeApp extends Application {
    static final String TAG = DKBridgeApp.class.getSimpleName();

    static DKBridgeApp sInstance;

    public static DKBridgeApp get() { return sInstance; }

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public DKBridgeApp() {
        super();
        sInstance = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        DKBridgePrefs.init(this);
    }

    public Handler getHandler() {
        return mMainHandler;
    }
}
