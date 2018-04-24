package com.example.baobang.detectvoice;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.RecognizerIntent;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

import butterknife.BindDrawable;
import butterknife.BindString;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindString(R.string.state_off)
    String STATE_OFF;
    @BindString(R.string.state_on)
    String STATE_ON;
    @BindDrawable(R.drawable.red_led_off_hi)
    Drawable RED_LED_ON;
    @BindDrawable(R.drawable.white_led_off_md)
    Drawable WHITE_LED_OFF;

    @BindView(R.id.imgBluetooth)
    ImageButton imgBluetooth;
    @BindView(R.id.lbBluetooth)
    TextView lbBluetooth;
    @BindView(R.id.txtResult)
    TextView txtResult;
    @BindView(R.id.txtLedState)
    TextView txtLedState;
    @BindView(R.id.imgLed)
    ImageView imgLed;

    private Dialog mDialog;
    private BluetoothAdapter mBluetoothAdapter;
    private DeviceAdapter mDeviceAdapter;
    private ArrayList<BluetoothDevice> mDevices;
    private BluetoothDevice mConnectingDevice;
    private MyService mMyService;
    // Create a BroadcastReceiver for ACTION_FOUND.
    IntentFilter mFilter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e("TAG", action);
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mDevices.add(device);
                mDeviceAdapter.notifyDataSetChanged();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        checkSupporBluetooth();
        checkEnableBluetooth();
        addControls();
        mFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        mFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, mFilter);
    }

    private void addControls() {
        mDevices = new ArrayList<>();
        mDeviceAdapter = new DeviceAdapter(this, mDevices);

    }

    @OnClick(value = {R.id.imgBluetooth, R.id.imgSpeechVoice, R.id.imgLed})
    void onClick(View view){
        switch (view.getId()){
            case R.id.imgBluetooth:
                showDialogDevice();
                break;
            case R.id.imgSpeechVoice:
                showSpeechVoiceDialog();
                break;
        }
    }

    private void showSpeechVoiceDialog() {

        if(mConnectingDevice == null){
            Toast.makeText(this, "Need to connect a device...", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, Constants.REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showDialogDevice() {
        mDialog = new Dialog(this);
        mDialog.setContentView(R.layout.layout_bluetooth);
        mDialog.setTitle("Bluetooth Devices");

        if(mBluetoothAdapter.isDiscovering()){
            mBluetoothAdapter.cancelDiscovery();
        }

        mBluetoothAdapter.startDiscovery();

        final ArrayList<BluetoothDevice> pairedDevices = new ArrayList<>();
        DeviceAdapter pairedDeviceAdapter = new DeviceAdapter(this, pairedDevices);

        RecyclerView rvPairedDevice = mDialog.findViewById(R.id.rvPairedDevice);
        rvPairedDevice.setLayoutManager(new LinearLayoutManager(this));
        rvPairedDevice.setAdapter(pairedDeviceAdapter);

        RecyclerView rvDiscoveringDevice = mDialog.findViewById(R.id.rvDiscoveringDevice);
        rvDiscoveringDevice.setLayoutManager(new LinearLayoutManager(this));
        rvDiscoveringDevice.setAdapter(mDeviceAdapter);


        // Register for broadcasts when discovery has finished


        Set<BluetoothDevice> pairedDeviceSet = mBluetoothAdapter.getBondedDevices();
        if(pairedDeviceSet.size() > 0){
            pairedDevices.addAll(pairedDeviceSet);
            pairedDeviceAdapter.notifyDataSetChanged();
        }

        pairedDeviceAdapter.setOnItemClickListener(new DeviceAdapter.OnItemClickListener() {
            @Override
            public void onClick(int position) {
                mBluetoothAdapter.cancelDiscovery();
                connectToDevice(pairedDevices.get(position).getAddress());
                mDialog.dismiss();
            }
        });
        mDeviceAdapter.setOnItemClickListener(new DeviceAdapter.OnItemClickListener() {
            @Override
            public void onClick(int position) {
                mBluetoothAdapter.cancelDiscovery();
                connectToDevice(mDevices.get(position).getAddress());
                mDialog.dismiss();
            }
        });
        Button btnCancel = mDialog.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDialog.dismiss();
            }
        });
        mDialog.show();
    }

    private void connectToDevice(String address) {
        mBluetoothAdapter.cancelDiscovery();
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        mMyService.connect(device);
    }

    private void checkEnableBluetooth() {
        if(!mBluetoothAdapter.isEnabled()){
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
        }else{
            mMyService = new MyService(mHandler);
        }
    }

    private void checkSupporBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null){
            new AlertDialog.Builder(this)
                    .setTitle("Not compatible")
                    .setMessage("Your phone does not support Bluetooth")
                    .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            System.exit(0);
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Constants.REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    mMyService = new MyService(mHandler);
                } else {
                    Toast.makeText(this, "Bluetooth still disabled, turn off application!", Toast.LENGTH_SHORT).show();
                    finish();
                }
            case Constants.REQ_CODE_SPEECH_INPUT:
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    String dataVoiceInput = result.get(0);
                    txtResult.setText(dataVoiceInput);
                    String str;
                    if(dataVoiceInput.toLowerCase().contains("bật") || dataVoiceInput.toLowerCase().contains("on")){
                        dataVoiceInput = "on";
                    }else if(dataVoiceInput.toLowerCase().contains("tắt") || dataVoiceInput.toLowerCase().contains("off")){
                        dataVoiceInput = "off";
                    }
                    mMyService.write(dataVoiceInput.getBytes());
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        if(mMyService != null)
            mMyService.stop();
    }

    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {

            switch (msg.what) {
                case MyState.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case MyState.CONNECTED:
                            setStatus("Connected to: " + mConnectingDevice.getName(), Color.GREEN);
                            imgBluetooth.setEnabled(true);
                            break;
                        case MyState.CONNECTING:
                            setStatus("Connecting...", Color.BLUE);
                            imgBluetooth.setEnabled(true);
                            break;
                        case MyState.LISTEN:
                        case MyState.NONE:
                            setStatus("Not connected", Color.RED);
                            break;
                    }
                    break;
                case MyState.MESSAGE_DEVICE_OBJECT:
                    mConnectingDevice = msg.getData().getParcelable(Constants.DEVICE_OBJECT);
                    Toast.makeText(getApplicationContext(), "Connected to " + mConnectingDevice.getName(),
                            Toast.LENGTH_SHORT).show();
                    break;
                case MyState.MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString("toast"),
                            Toast.LENGTH_SHORT).show();
                    break;
                case MyState.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    String writeMessage = new String(writeBuf);
                    break;
                case MyState.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    turnOnOffLed(readMessage);
                    break;
            }

            return false;
        }
    });

    private void turnOnOffLed(String readMessage) {
        if(readMessage.length() > 0){
            if(readMessage.equals("1")){
                txtLedState.setText(STATE_ON);
                imgLed.setImageDrawable(RED_LED_ON);
            }else{
                txtLedState.setText(STATE_OFF);
                imgLed.setImageDrawable(WHITE_LED_OFF);
            }
        }
    }

    public void setStatus(String status, int color) {
        lbBluetooth.setText(status);
        lbBluetooth.setTextColor(color);
    }
}
