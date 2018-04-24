package com.example.baobang.detectvoice;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MyService {

    private final BluetoothAdapter mBluetoothAdapter;
    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ReadWriteThread mReadWriteThread;
    private int mState;

    public MyService(Handler handler) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = MyState.NONE;
        this.mHandler = handler;
    }

    // Set the current mState of the chat connection
    private synchronized void setState(int state) {
        this.mState = state;
        mHandler.obtainMessage(MyState.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    // get current connection mState
    public synchronized int getState() {
        return mState;
    }

    // start service
    public synchronized void start() {
        // Cancel any thread
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any running thresd
        if (mReadWriteThread != null) {
            mReadWriteThread.cancel();
            mReadWriteThread = null;
        }

        setState(MyState.LISTEN);
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
    }

    // initiate connection to remote mBluetoothDevice
    public synchronized void connect(BluetoothDevice device) {
        // Cancel any thread
        if (mState == MyState.CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel running thread
        if (mReadWriteThread != null) {
            mReadWriteThread.cancel();
            mReadWriteThread = null;
        }

        // Start the thread to connect with the given mBluetoothDevice
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(MyState.CONNECTING);
    }

    // manage Bluetooth connection
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        // Cancel the thread
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel running thread
        if (mReadWriteThread != null) {
            mReadWriteThread.cancel();
            mReadWriteThread = null;
        }

        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mReadWriteThread = new ReadWriteThread(socket);
        mReadWriteThread.start();

        // Send the name of the connected mBluetoothDevice back to the UI Activity
        Message msg = mHandler.obtainMessage(MyState.MESSAGE_DEVICE_OBJECT);
        Bundle bundle = new Bundle();
        bundle.putParcelable(Constants.DEVICE_OBJECT, device);
        msg.setData(bundle)
        ;
        mHandler.sendMessage(msg);

        setState(MyState.CONNECTED);
    }

    // stop all threads
    public synchronized void stop() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mReadWriteThread != null) {
            mReadWriteThread.cancel();
            mReadWriteThread = null;
        }

        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        setState(MyState.NONE);
    }

    public void write(byte[] out) {
        synchronized (this) {
            if (mState != MyState.CONNECTED)
                return;
        }
        mReadWriteThread.write(out);
    }

    private void connectionFailed() {
        Message msg = mHandler.obtainMessage(MyState.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Unable to connect mBluetoothDevice");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        // Start the service over to restart listening mode
        MyService.this.start();
    }

    private void connectionLost() {
        Message msg = mHandler.obtainMessage(MyState.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        // Start the service over to restart listening mode
        MyService.this.start();
    }

    // runs while listening for incoming connections
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(Constants.APP_NAME, Constants.MY_UUID);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            mmServerSocket = tmp;
        }

        public void run() {
            setName("AcceptThread");
            BluetoothSocket socket;
            while (mState != MyState.CONNECTED) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (MyService.this) {
                        switch (mState) {
                            case MyState.LISTEN:
                            case MyState.CONNECTING:
                                // start the connected thread.
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case MyState.NONE:
                            case MyState.CONNECTED:
                                // Either not ready or already connected. Terminate
                                // new mmBluetoothSocket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                }
                                break;
                        }
                    }
                }
            }
        }

        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
            }
        }
    }

    // runs while attempting to make an outgoing connection
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmBluetoothSocket;
        private final BluetoothDevice mBluetoothDevice;

        public ConnectThread(BluetoothDevice device) {
            this.mBluetoothDevice = device;
            BluetoothSocket tmp = null;
            try {
//                tmp = mBluetoothDevice.createInsecureRfcommSocketToServiceRecord(Constants.MY_UUID);
                  Method method = device.getClass().getMethod("createRfcommSocket", new Class[] {int.class});
                tmp = (BluetoothSocket) method.invoke(device, 1);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            mmBluetoothSocket = tmp;
        }

        public void run() {
            setName("ConnectThread");
            // Always cancel discovery because it will slow down a connection
            mBluetoothAdapter.cancelDiscovery();
            // Make a connection to the BluetoothSocket
            try {
                mmBluetoothSocket.connect();
            } catch (IOException e) {
                try {
                    mmBluetoothSocket.close();
                } catch (IOException e2) {}
                connectionFailed();
                return;
            }
            // Reset the ConnectThread because we're done
            synchronized (MyService.this) {
                mConnectThread = null;
            }
            // Start the connected thread
            connected(mmBluetoothSocket, mBluetoothDevice);
        }
        public void cancel() {
            try {
                mmBluetoothSocket.close();
            } catch (IOException e) {}
        }
    }

    // runs during a connection with a remote mBluetoothDevice
    private class ReadWriteThread extends Thread {
        private final BluetoothSocket mmBluetoothSocket;
        private final InputStream mmInputStream;
        private final OutputStream mmOutputStream;

        public ReadWriteThread(BluetoothSocket socket) {
            this.mmBluetoothSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInputStream = tmpIn;
            mmOutputStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1];
            int bytes;
            // Keep listening to the InputStream
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInputStream.read(buffer);
                    String readMessage = new String(buffer, 0, bytes);
                    if(readMessage.trim().equals("1") || readMessage.trim().equals("0")){
                        // Send the obtained bytes to the UI Activity
                        mHandler.obtainMessage(MyState.MESSAGE_READ, bytes, -1,
                                buffer).sendToTarget();
                    }
                } catch (IOException e) {
                    connectionLost();
                    // Start the service over to restart listening mode
                    MyService.this.start();
                    break;
                }
            }
        }

        // write to OutputStream
        public void write(byte[] buffer) {
            try {
                mmOutputStream.write(buffer);
                mHandler.obtainMessage(MyState.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {}
        }

        public void cancel() {
            try {
                mmBluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
