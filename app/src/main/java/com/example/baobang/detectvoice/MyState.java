package com.example.baobang.detectvoice;

public interface MyState {
    static final int NONE = 0;
    static final int LISTEN = 1;
    static final int CONNECTING = 2;
    static final int CONNECTED = 3;

    static final int MESSAGE_STATE_CHANGE = 1;
    static final int MESSAGE_READ = 2;
    static final int MESSAGE_WRITE = 3;
    static final int MESSAGE_DEVICE_OBJECT = 4;
    static final int MESSAGE_TOAST = 5;
}
