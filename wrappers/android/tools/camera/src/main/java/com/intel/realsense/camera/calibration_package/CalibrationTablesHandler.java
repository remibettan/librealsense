package com.intel.realsense.camera.calibration_package;

import android.view.View;
import android.widget.ToggleButton;

import com.intel.realsense.librealsense.Pipeline;

import java.nio.ByteBuffer;

public class CalibrationTablesHandler {

    private ByteBuffer mCalibrationTableNew;
    private ByteBuffer mCalibrationTableOriginal;
    private boolean bShowCalibrated = false;
    private Pipeline mPipeline;

    CalibrationTablesHandler(){
        mCalibrationTableNew = ByteBuffer.allocateDirect(512);
        mCalibrationTableOriginal = ByteBuffer.allocateDirect(512);
        mPipeline = null;
    }

    public void init(Pipeline pipeline) {
        mPipeline = pipeline;
        nGetTable(mPipeline.getHandle(), mCalibrationTableOriginal);
        nGetTable(mPipeline.getHandle(), mCalibrationTableNew);
    }

    public ByteBuffer getCalibrationTableNew() { return mCalibrationTableNew;}

    public void originalCalibratedToggle(View view) {
        ToggleButton toggleButton = (ToggleButton) view;
        if (toggleButton.isChecked()) {
            bShowCalibrated = true;
        } else {
            bShowCalibrated = false;
        }
        setTable(false);
    }

    public void setTable(boolean write){
        nSetTable(mPipeline.getHandle(), bShowCalibrated ? mCalibrationTableNew : mCalibrationTableOriginal, write);
    }

    private native void nSetTable(long pipeline_handle, ByteBuffer table, boolean write_table);

    private native void nGetTable(long pipeline_handle, ByteBuffer table);
}
