package com.intel.realsense.librealsense;

import android.content.SharedPreferences;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.HashMap;

public class AutoCalibDevice extends Device
{
    SharedPreferences mSharePreferences;
    private ByteBuffer mCalibrationTableNew;
    private ByteBuffer mCalibrationTableOriginal;
    private boolean bShowCalibrated = false;

    AutoCalibDevice(long handle){
        super(handle);
        mOwner = false;
        mCalibrationTableNew = ByteBuffer.allocateDirect(512);
        mCalibrationTableOriginal = ByteBuffer.allocateDirect(512);
    }

    private void setSharedPreferences(SharedPreferences sharedPref) {
        mSharePreferences = sharedPref;
    }

    private String getAutoCalibJson(float calibrationSpeed) {
        HashMap<String, Object> settingMap = new HashMap<>();
        settingMap.put("speed", calibrationSpeed);
        settingMap.put("scan parameter", 0);
        settingMap.put("data sampling", 0);
        JSONObject json = new JSONObject(settingMap);
        return json.toString();
    }

    float runAutoCalib(float calibrationSpeed) {
        float health = nRunAutoCalib(mHandle, mCalibrationTableNew, getAutoCalibJson(calibrationSpeed));
        return health;
    }

    String getTareJson() {
        HashMap<String, Object> settingMap = new HashMap<>();
        settingMap.put("average step count", mSharePreferences.getInt("tare_avg_step_count", 20));
        settingMap.put("step count", mSharePreferences.getInt("tare_step_count", 20));
        settingMap.put("accuracy", mSharePreferences.getInt("tare_accuracy", 2));
        settingMap.put("scan parameter", 0);
        settingMap.put("data sampling", 0);
        JSONObject json = new JSONObject(settingMap);
        return json.toString();
    }

    void runTare(int tareDistance_mm) {
        nTare(mHandle, mCalibrationTableNew, tareDistance_mm, getTareJson());
    }

    void calibrationOnProgress(float progress){}



    private native float nRunAutoCalib(long handle, ByteBuffer new_table, String json_content);
    private native void nTare(long handle, ByteBuffer table, int tare_distance, String json_cont);
}
