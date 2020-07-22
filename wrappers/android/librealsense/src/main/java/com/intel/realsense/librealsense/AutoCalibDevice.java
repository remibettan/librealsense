package com.intel.realsense.librealsense;

import android.util.Log;

import java.nio.ByteBuffer;

public class AutoCalibDevice extends Device
{
    private ByteBuffer mCalibrationTable;

    AutoCalibDevice(long handle){
        super(handle);
        mOwner = false;
        mCalibrationTable = ByteBuffer.allocateDirect(512);
        nGetTable(mHandle, mCalibrationTable);
    }

    public float runAutoCalib(String jsonString) {
        float health = nRunAutoCalib(mHandle, mCalibrationTable, jsonString);
        setTableInCamera();
        return health;
    }

    public void runTare(int tareDistance_mm, String tareJson) throws RuntimeException {
        ByteBuffer oldCalib = mCalibrationTable;
        nTare(mHandle, mCalibrationTable, tareDistance_mm, tareJson);
        if (mCalibrationTable.equals(oldCalib))
            Log.d("remi", "after runTare in device - tables are identical");
        else
            Log.d("remi", "after runTare in device - tables are different");
        setTableInCamera();
    }

    public ByteBuffer getTable() {
        return mCalibrationTable;
    }

    public void setTable(ByteBuffer table) {
        mCalibrationTable = table;
        setTableInCamera();
    }

    private void setTableInCamera(){
        nSetTable(mHandle, mCalibrationTable);
    }

    public void writeToCamera() {
        setTableInCamera();
        nWriteToCamera(mHandle);
    }

    public void resetFactoryCalibration(){
        nResetToFactoryCalibration(mHandle);
    }

    void calibrationOnProgress(float progress){}


    private native float nRunAutoCalib(long handle, ByteBuffer new_table, String json_content);
    private native void nTare(long handle, ByteBuffer table, int tare_distance, String json_cont);
    private native void nResetToFactoryCalibration(long handle);
    private native void nWriteToCamera(long handle);

    private native void nSetTable(long device_handle, ByteBuffer table);
    private native void nGetTable(long device_handle, ByteBuffer table);
}
