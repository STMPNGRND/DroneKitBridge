package com.fognl.dronekitbridge.comm;

import android.os.Handler;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by kellys on 2/26/16.
 */
public class SocketClient implements Runnable {
    static final String TAG = SocketClient.class.getSimpleName();

    static final byte[] CHECK_BUF = new byte[100];

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onConnectFailed(Throwable error);
        void onError(Throwable error);
    }

    private final Handler mHandler;
    private final String mIpAddress;
    private final int mPort;
    private final Listener mListener;
    private final LinkedBlockingDeque<String> mOutputQueue = new LinkedBlockingDeque<>();

    private Socket mSocket;
    private boolean mConnected;
    private PrintWriter mPrintWriter;

    public SocketClient(Handler handler, Listener listener, String ip, int port) {
        super();
        mHandler = handler;
        mListener = listener;
        mIpAddress = ip;
        mPort = port;
    }

    public void run() {
        try {
            InetAddress serverIp = InetAddress.getByName(mIpAddress);
            mSocket = new Socket();

            try {
                mSocket.connect(new InetSocketAddress(serverIp, mPort), 5000);
            }
            catch(SocketTimeoutException ex) {

                mSocket = null;

                final Throwable err = ex;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onConnectFailed(err);
                    }
                });

                return;
            }

            final SocketAddress remoteAddr = mSocket.getRemoteSocketAddress();

            mConnected = mSocket.isConnected();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnected();
                }
            });

            mPrintWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream())));
            BufferedReader reader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
            final OutputStream outputStream = mSocket.getOutputStream();

            int count = 0;

            while(mConnected) {
                try {
                    Thread.currentThread().sleep(100);

                    if(!mOutputQueue.isEmpty()) {
                        String output = mOutputQueue.removeLast();

                        if(output != null) {
                            Log.v(TAG, "Writing output: " + output);

                            mPrintWriter.println(output);
                            mPrintWriter.flush();
                        }
                    }

                    // Check the connection.
                    if(++count >= 20) {
                        count = 0;
                        boolean err = false;

                        try {
                            outputStream.write(CHECK_BUF);
                            outputStream.flush();
                        } catch(IOException ioex) {
                            Log.v(TAG, ioex.getMessage(), ioex);
                            err = true;
                        }

                        if(err) {
                            mConnected = false;

                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mListener.onConnectFailed(new Exception("Server stopped"));
                                }
                            });

                            break;
                        }
                    }
                } catch(InterruptedException ex) {
                    break;
                }
            }

            mPrintWriter.close();
            mSocket.close();

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onDisconnected();
                }
            });
        }
        catch(Throwable ex) {
            Log.e(TAG, ex.getMessage(), ex);
            final Throwable error = ex;
            mConnected = false;
            try {
                mSocket.close();
            } catch(IOException exx) { /* ok */ }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onError(error);
                }
            });
        }
    }

    public boolean isConnected() { return mConnected; }

    public void send(String msg) {
        mOutputQueue.addFirst(msg);
    }

    public void cancel() {
        mConnected = false;

        if(mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException ex) {
                Log.e(TAG, ex.getMessage(), ex);
            }
        }
    }
}
