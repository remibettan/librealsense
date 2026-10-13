package com.intel.realsense.camera;

import android.content.Context;
import android.content.SharedPreferences;

import com.intel.realsense.librealsense.Device;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.ProductLine;
import com.intel.realsense.librealsense.RsContext;
import com.intel.realsense.librealsense.Updatable;

/**
 * Signed firmware update flow: the image is picked and stored first, then the camera is switched to
 * recovery mode. Once it re-enumerates as an update device the stored image is flashed.
 */
final class FirmwareUpdater {
    private static final String PREF_PENDING_FILE = "pending_fw_file";

    private FirmwareUpdater() {}

    static void setPendingFile(Context context, String path) {
        prefs(context).edit().putString(PREF_PENDING_FILE, path).apply();
    }

    static String getPendingFile(Context context) {
        return prefs(context).getString(PREF_PENDING_FILE, "");
    }

    static void clearPendingFile(Context context) {
        prefs(context).edit().remove(PREF_PENDING_FILE).apply();
    }

    /** Puts the first connected depth camera into recovery (DFU) mode. */
    static boolean enterUpdateState() {
        try (RsContext ctx = new RsContext(); DeviceList dl = ctx.queryDevices(ProductLine.DEPTH)) {
            if (dl.getDeviceCount() == 0)
                return false;
            try (Device d = dl.createDevice(0)) {
                if (d == null || !d.is(Extension.UPDATABLE))
                    return false;
                try (Updatable updatable = d.as(Extension.UPDATABLE)) {
                    updatable.enterUpdateState();
                }
                return true;
            }
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(context.getString(R.string.app_settings), Context.MODE_PRIVATE);
    }
}
