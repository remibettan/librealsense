package com.intel.realsense.camera;


import com.intel.realsense.librealsense.CameraInfo;
import com.intel.realsense.librealsense.Sensor;
import com.intel.realsense.librealsense.StreamProfile;

import java.util.List;

public class SensorSelector{

    private int mWidth = 0;
    private int mHeight = 0;
    private int mFps = 0;
    private boolean mEnabled;
    private List<StreamProfile> mStreamProfiles;

    public SensorSelector(boolean enable, List<StreamProfile> streamProfiles)
    {
        mEnabled = enable;
        mStreamProfiles = streamProfiles;
    }

    public String getName(){
        Sensor s = mSensors.get(mIndex);
        return s.getInfo(CameraInfo.NAME);
    }

    public void updateResolution(String str) {
        mWidth = Integer.parseInt(str.split("x")[0]);
        mHeight = Integer.parseInt(str.split("x")[1]);
    }

    public void updateFrameRate(String str) {
        mFps = Integer.parseInt(str);
    }

    public void updateEnabled(boolean state) {
        mEnabled = state;
    }

    public String getResolutionString() {
        return String.valueOf(mWidth) + "x" + String.valueOf(mHeight);
    }
}
