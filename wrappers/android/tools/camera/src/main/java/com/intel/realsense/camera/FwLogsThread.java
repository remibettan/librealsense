package com.intel.realsense.camera;

import android.util.Log;

import com.intel.realsense.librealsense.Device;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.FwLogMsg;
import com.intel.realsense.librealsense.FwLogParsedMsg;
import com.intel.realsense.librealsense.FwLogger;
import com.intel.realsense.librealsense.RsContext;

/** Pulls firmware logs from the first camera and writes them to logcat until stopped. */
public class FwLogsThread extends Thread {
    private static final String TAG = "librs_fwLogsThread";
    private volatile boolean mAreFwLogsRequested;
    private String mFwLogsParsingFilePath = "";

    public FwLogsThread(String fwLogsParsingFilePath) {
        super("librs fw logs");
        mFwLogsParsingFilePath = fwLogsParsingFilePath == null ? "" : fwLogsParsingFilePath;
    }

    @Override
    public void run() {
        mAreFwLogsRequested = true;
        try (RsContext ctx = new RsContext(); DeviceList devices = ctx.queryDevices()) {
            if (devices.getDeviceCount() == 0)
                return;
            // Only device 0 is handled, the camera app works with a single device
            try (Device device = devices.createDevice(0)) {
                if (device == null)
                    return;
                try (FwLogger fwLogger = device.as(Extension.FW_LOGGER)) {
                    boolean parserReady = !mFwLogsParsingFilePath.isEmpty() && fwLogger.initParser(mFwLogsParsingFilePath);
                    while (mAreFwLogsRequested) {
                        try (FwLogMsg logMsg = fwLogger.getFwLog()) {
                            if (!fwLogger.getFwLogPullingStatus())
                                continue;
                            if (parserReady) {
                                try (FwLogParsedMsg parsed = fwLogger.parseFwLog(logMsg)) {
                                    Log.d(TAG, parsed.getTimestamp() + " - " + parsed.getSeverity() + " " + parsed.getFileName() + " " +
                                            parsed.getLine() + " " + parsed.getThreadName() + " " + parsed.getMessage());
                                }
                            } else {
                                StringBuilder sb = new StringBuilder(logMsg.getTimestamp() + " " + logMsg.getSeverityStr() + " ");
                                byte[] buffer = logMsg.getData(new byte[logMsg.getSize()]);
                                for (byte b : buffer)
                                    sb.append(String.format("%02X ", b));
                                Log.d(TAG, sb.toString());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "fw logs stopped: " + e.getMessage());
        }
    }

    public void stopLogging() {
        mAreFwLogsRequested = false;
    }
}
