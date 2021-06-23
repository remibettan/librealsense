package com.intel.realsense.camera;

public class ResolutionAndFps {
    private int mWidth;
    private int mHeight;
    private int mFps;

    public ResolutionAndFps(int width, int height, int fps) {
        mWidth = width;
        mHeight = height;
        mFps = fps;
    }

    public String getResolutionString() {
        return String.valueOf(mWidth) + "x" + String.valueOf(mHeight);
    }
}
