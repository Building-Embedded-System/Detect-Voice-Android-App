package com.example.baobang.detectvoice;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceHolder> {

    private Context mContext;
    private ArrayList<BluetoothDevice> mDevices;

    public DeviceAdapter(Context mContext, ArrayList<BluetoothDevice> mDevices) {
        this.mContext = mContext;
        this.mDevices = mDevices;
    }

    @NonNull
    @Override
    public DeviceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.device_item, parent, false);

        return new DeviceHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceHolder holder, int position) {
        holder.onBindView(mDevices.get(position));
    }

    @Override
    public int getItemCount() {
        return mDevices.size();
    }

    public class DeviceHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.txtDeviceName)
        TextView txtDeviceName;
        @BindView(R.id.txtDeviceMac)
        TextView txtDeviceMac;

        public DeviceHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        @OnClick(R.id.container)
        void onItemClick(){
            if(listener == null) return;
            listener.onClick(getAdapterPosition());
        }

        public void onBindView(BluetoothDevice bluetoothDevice) {
            txtDeviceName.setText(bluetoothDevice.getName());
            txtDeviceMac.setText(bluetoothDevice.getAddress());
        }
    }

    public interface OnItemClickListener {
        void onClick(int position);
    }

    public void setOnItemClickListener(DeviceAdapter.OnItemClickListener callBack){
        this.listener = callBack;
    }
    private OnItemClickListener listener;
}
