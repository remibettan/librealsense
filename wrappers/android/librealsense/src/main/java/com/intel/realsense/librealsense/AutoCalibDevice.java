package com.intel.realsense.librealsense;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.HashMap;

public class AutoCalibDevice extends Device
{
    private boolean bShowCalibrated = false;
    private ByteBuffer mCalibrationTableNew;
    private ByteBuffer mCalibrationTableOriginal;

    AutoCalibDevice(long handle){
        super(handle);
        mOwner = false;

        mCalibrationTableNew = ByteBuffer.allocateDirect(512);
        mCalibrationTableOriginal = ByteBuffer.allocateDirect(512);
        nGetTable(mHandle, mCalibrationTableOriginal);
        nGetTable(mHandle, mCalibrationTableNew);
    }

    public float runAutoCalib(String jsonString) {
        float health = nRunAutoCalib(mHandle, mCalibrationTableNew, jsonString);
        setTable(false);
        return health;
    }

    public void runTare(int tareDistance_mm, String tareJson) throws RuntimeException {
        nTare(mHandle, mCalibrationTableNew, tareDistance_mm, tareJson);
        setTable(false);
    }

    public void toggleOrigCalibTables(View view) {
        ToggleButton toggleButton =
                (ToggleButton) view;
        if (toggleButton.isChecked()) {
            bShowCalibrated = true;
        } else {
            bShowCalibrated = false;
        }
        setTable(false);
    }

    public void setTable(boolean write){
        nSetTable(mHandle, bShowCalibrated ? mCalibrationTableNew : mCalibrationTableOriginal, write);
    }

    public void resetFactoryCalibration(){
        nResetToFactoryCalibration(mHandle);
    }

    void calibrationOnProgress(float progress){}


    private native float nRunAutoCalib(long handle, ByteBuffer new_table, String json_content);
    private native void nTare(long handle, ByteBuffer table, int tare_distance, String json_cont);
    private native void nResetToFactoryCalibration(long handle);

    private native void nSetTable(long device_handle, ByteBuffer table, boolean write_table);
    private native void nGetTable(long pipeline_handle, ByteBuffer table);
}
