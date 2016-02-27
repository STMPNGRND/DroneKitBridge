package com.fognl.dronekitbridge.comm;

import android.os.Handler;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Created by kellys on 2/26/16.
 */
public class SocketServer implements Runnable {
    static final String TAG = SocketServer.class.getSimpleName();
    public static final int DEFAULT_PORT = 8888;

    public interface Listener {
        void onStarted();
        void onStopped();
        void onClientConnected();
        void onData(String data);
        void onError(Throwable error);
        void onLocalIpFound(String ip);
        void onClientDisconnected();
    }

    public static String getLocalIpAddress() throws SocketException {
        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
            NetworkInterface intf = en.nextElement();
            for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                InetAddress inetAddress = enumIpAddr.nextElement();
                // Only want ipv4 addresses. Someone's going to have to type it in!
                if (!inetAddress.isLoopbackAddress() && (inetAddress instanceof Inet4Address)) {
                    return inetAddress.getHostAddress();
                }
            }
        }

        return null;
    }

    private final Handler mHandler;
    private final Listener mListener;
    private int mPort = DEFAULT_PORT;
    private Socket mClientSocket;

    public SocketServer(Handler handler, Listener listener) {
        super();
        mHandler = handler;
        mListener = listener;
    }

    public int getPort() {
        return mPort;
    }

    public SocketServer setPort(int port) {
        mPort = port;
        return this;
    }

    private ServerSocket mServerSocket;

    public void run() {
        try {
            init();

            final String ip = getLocalIpAddress();
            if(ip != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onLocalIpFound(ip);
                    }
                });
            } else {
                throw new Exception("Cannot get local IP address");
            }

            mServerSocket = new ServerSocket(mPort);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onStarted();
                }
            });

            while(true) {
                // Listen for connections
                mClientSocket = mServerSocket.accept();

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onClientConnected();
                    }
                });

                BufferedReader in = new BufferedReader(new InputStreamReader(mClientSocket.getInputStream()));

                Log.v(TAG, "Got a reader");

                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    final String thisLine = line;
                    Log.v(TAG, "thisLine=" + thisLine);

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mListener.onData(thisLine);
                        }
                    });
                }

                Log.v(TAG, "Connection stopped, waiting for another one");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onClientDisconnected();
                    }
                });
            }
        } catch (Throwable ex) {
            final Throwable error = ex;
            mHandler.post(new Runnable() {
                public void run() {
                    mListener.onError(error);
                }
            });
        } finally {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onStopped();
                }
            });
        }
    }

    public void cancel() {
        try {
            mClientSocket.close();
        } catch(Throwable ex) { /* ok */ }

        try {
            mServerSocket.close();
        } catch(IOException ex) { /* ok */ }
    }

    private void init() throws Throwable {
    }
}
