package com.intel.realsense.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.intel.realsense.librealsense.CameraInfo;
import com.intel.realsense.librealsense.Config;
import com.intel.realsense.librealsense.Device;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.FrameSet;
import com.intel.realsense.librealsense.MotionStreamProfile;
import com.intel.realsense.librealsense.Pipeline;
import com.intel.realsense.librealsense.PipelineProfile;
import com.intel.realsense.librealsense.RsContext;
import com.intel.realsense.librealsense.StreamProfile;
import com.intel.realsense.librealsense.VideoStreamProfile;

import java.util.List;
import java.util.Map;

/** Runs a pipeline on a dedicated thread and hands every frameset to the listener (off the UI thread). */
public class Streamer {
    private static final String TAG = "librs camera streamer";
    private static final int FIRST_FRAME_TIMEOUT_MS = 5000;
    private static final int THREAD_JOIN_MS = 3000;

    interface Listener {
        void config(Config config);
        void onFrameset(FrameSet frameSet);
    }

    private final Context mContext;
    private final boolean mLoadConfig;
    private final Listener mListener;

    private volatile boolean mIsStreaming = false;
    private Thread mThread;
    private Pipeline mPipeline;
    private String mRecordFilePath;

    public Streamer(Context context, boolean loadConfig, Listener listener) {
        mContext = context;
        mListener = listener;
        mLoadConfig = loadConfig;
    }

    public void setRecordFilePath(String path) {
        mRecordFilePath = path;
    }

    /** True when a camera is connected and not in recovery mode. */
    public static boolean hasStreamableDevice() {
        try (RsContext ctx = new RsContext(); DeviceList devices = ctx.queryDevices()) {
            if (devices.getDeviceCount() == 0)
                return false;
            try (Device device = devices.createDevice(0)) {
                return device != null && !device.is(Extension.UPDATE_DEVICE);
            }
        }
    }

    private int configStream(Config config) {
        int numStreams = 0;
        config.disableAllStreams();

        try (RsContext ctx = new RsContext(); DeviceList devices = ctx.queryDevices()) {
            if (devices.getDeviceCount() == 0)
                return 0;
            try (Device device = devices.createDevice(0)) {
                if (device == null) {
                    Log.e(TAG, "failed to create device");
                    return 0;
                }
                String pid = device.getInfo(CameraInfo.PRODUCT_ID);
                Map<Integer, List<StreamProfile>> profilesMap = SettingsActivity.createProfilesMap(device);
                SharedPreferences sharedPref = mContext.getSharedPreferences(mContext.getString(R.string.app_settings), Context.MODE_PRIVATE);

                for (List<StreamProfile> profiles : profilesMap.values()) {
                    StreamProfile p = profiles.get(0);
                    if (!sharedPref.getBoolean(SettingsActivity.getEnabledDeviceConfigString(pid, p.getType(), p.getIndex()), false))
                        continue;
                    int index = sharedPref.getInt(SettingsActivity.getIndexDeviceConfigString(pid, p.getType(), p.getIndex()), 0);
                    if (index < 0 || index >= profiles.size())
                        throw new IllegalArgumentException("Failed to resolve config");
                    StreamProfile sp = profiles.get(index);
                    if (sp.is(Extension.VIDEO_PROFILE)) {
                        VideoStreamProfile vsp = sp.as(Extension.VIDEO_PROFILE);
                        config.enableStream(vsp.getType(), vsp.getIndex(), vsp.getWidth(), vsp.getHeight(), vsp.getFormat(), vsp.getFrameRate());
                        numStreams++;
                    } else if (sp.is(Extension.MOTION_PROFILE)) {
                        MotionStreamProfile msp = sp.as(Extension.MOTION_PROFILE);
                        config.enableStream(msp.getType(), msp.getIndex(), 0, 0, msp.getFormat(), msp.getFrameRate());
                        numStreams++;
                    }
                }
            }
        }
        return numStreams;
    }

    private void configAndStart() throws Exception {
        try (Config config = new Config()) {
            boolean defaultConfig = true;
            if (mRecordFilePath != null)
                config.enableRecordToFile(mRecordFilePath);
            if (mLoadConfig && configStream(config) > 0)
                defaultConfig = false;
            if (mListener != null)
                mListener.config(config);

            // The profile must be released, hence the try block
            try (PipelineProfile pp = mPipeline.start(config)) {
                // Remember the default streams in the settings so the Settings screen reflects what runs
                if (defaultConfig) {
                    SharedPreferences sharedPref = mContext.getSharedPreferences(mContext.getString(R.string.app_settings), Context.MODE_PRIVATE);
                    try (Device device = pp.getDevice()) {
                        for (StreamProfile sp : mPipeline.getActiveStreams())
                            SettingsActivity.setProfileSetting(sharedPref, device, sp, true);
                    }
                }
            }
        }
    }

    public synchronized void start() throws Exception {
        if (mIsStreaming)
            return;
        mPipeline = new Pipeline();
        try {
            Log.d(TAG, "try start streaming");
            configAndStart();
            try (FrameSet frames = mPipeline.waitForFrames(FIRST_FRAME_TIMEOUT_MS)) {}
        } catch (Exception e) {
            Log.e(TAG, "failed to start streaming: " + e.getMessage());
            mPipeline.close();
            mPipeline = null;
            throw e;
        }
        mIsStreaming = true;
        mThread = new Thread(this::streamingLoop, "librs camera streaming");
        mThread.start();
        Log.d(TAG, "streaming started successfully");
    }

    private void streamingLoop() {
        while (mIsStreaming) {
            try (FrameSet frames = mPipeline.waitForFrames()) {
                mListener.onFrameset(frames);
            } catch (Exception e) {
                if (mIsStreaming)
                    Log.e(TAG, "streaming error: " + e.getMessage());
            }
        }
    }

    public synchronized void stop() {
        if (!mIsStreaming)
            return;
        Log.d(TAG, "try stop streaming");
        mIsStreaming = false;
        try {
            mPipeline.stop();
        } catch (Exception e) {
            Log.w(TAG, "failed to stop pipeline: " + e.getMessage());
        }
        try {
            mThread.join(THREAD_JOIN_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        mThread = null;
        mPipeline.close();
        mPipeline = null;
        Log.d(TAG, "streaming stopped successfully");
    }
}
