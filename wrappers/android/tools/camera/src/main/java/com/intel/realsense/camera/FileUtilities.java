package com.intel.realsense.camera;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/** App-private storage helpers. Everything lives under the app's external files dir, so no storage permission is needed. */
final class FileUtilities {
    private FileUtilities() {}

    /** Sub-folder of the app's RealSense folder, created on demand (e.g. "video", "hw"). */
    static File getRealSenseDir(Context context, String subFolder) {
        File dir = new File(context.getExternalFilesDir(null), context.getString(R.string.realsense_folder) + File.separator + subFolder);
        dir.mkdirs();
        return dir;
    }

    static File saveFile(Context context, String fileName, byte[] data) throws IOException {
        File file = new File(context.getExternalFilesDir(null), fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
        return file;
    }

    /** Copies a document picked by the user into app storage so the native code can open it by path. */
    static File copyToAppStorage(Context context, Uri uri, String fileName) throws IOException {
        File file = new File(context.getExternalFilesDir(null), fileName);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(file)) {
            if (in == null)
                throw new IOException("cannot open " + uri);
            byte[] buffer = new byte[64 * 1024];
            int len;
            while ((len = in.read(buffer)) > 0)
                out.write(buffer, 0, len);
        }
        return file;
    }

    static byte[] readFile(File file) throws IOException {
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = in.read(data, offset, data.length - offset);
                if (read < 0)
                    break;
                offset += read;
            }
            return data;
        }
    }

    static boolean hasFiles(File folder) {
        File[] files = folder.listFiles();
        return files != null && files.length > 0;
    }

    static String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        double value = bytes;
        String[] units = {"KB", "MB", "GB"};
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.US, value >= 100 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }
}
