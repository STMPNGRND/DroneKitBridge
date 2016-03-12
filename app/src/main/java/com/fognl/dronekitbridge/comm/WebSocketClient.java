package com.fognl.dronekitbridge.comm;

import android.content.Context;

import com.fognl.dronekitbridge.R;

import org.json.JSONException;
import org.json.JSONObject;

import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;
import de.tavendo.autobahn.WebSocketHandler;

/**
 * Created by kellys on 3/11/16.
 */
public class WebSocketClient {
    static final String TAG = WebSocketClient.class.getSimpleName();

    public interface Listener {
        void onConnected();
        void onDisconnected(int code, String reason);
        void onText(String text);
        void onJsonObject(JSONObject jo);
        void onError(Throwable err);
    }

    public static class Exception extends java.lang.Exception {
        Exception(String msg) {
            super(msg);
        }
    }

    private final WebSocketHandler mSocketHandler = new WebSocketHandler() {
        @Override
        public void onOpen() {
            super.onOpen();
            mListener.onConnected();
        }

        @Override
        public void onClose(int code, String reason) {
            super.onClose(code, reason);
            mListener.onDisconnected(code, reason);
        }

        @Override
        public void onTextMessage(String payload) {
            super.onTextMessage(payload);
            try {
                JSONObject jo = new JSONObject(payload);
                mListener.onJsonObject(jo);
            }
            catch(JSONException jex) {
                // Wasn't json
                mListener.onText(payload);
            }
            catch(Throwable error) {
                // Something actually went wrong
                mListener.onError(error);
            }
        }
    };

    private final WebSocketConnection mConnection = new WebSocketConnection();

    private final Context mContext;
    private final Listener mListener;
    private boolean mConnected;

    public WebSocketClient(Context context, Listener listener) {
        super();

        if(listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }

        mContext = context;
        mListener = listener;
    }

    public void connect() {
        try {
            mConnection.connect(mContext.getString(R.string.ws_base_url), mSocketHandler);
            mConnected = true;
        }
        catch(WebSocketException ex) {
            mListener.onError(ex);
        }
    }

    public void disconnect() {
        mConnection.disconnect();
        mConnected = false;
    }

    public boolean isConnected() { return mConnected; }

    public void send(String msg) {
        if(mConnected) {
            mConnection.sendTextMessage(msg);
        }
        else {
            mListener.onError(new Exception("Not connected"));
        }
    }
}
