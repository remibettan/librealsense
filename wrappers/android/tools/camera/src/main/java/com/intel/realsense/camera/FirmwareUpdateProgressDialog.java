package com.intel.realsense.camera;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.intel.realsense.librealsense.Device;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.ProgressListener;
import com.intel.realsense.librealsense.RsContext;
import com.intel.realsense.librealsense.Updatable;
import com.intel.realsense.librealsense.UpdateDevice;

import java.io.File;
import java.util.Locale;

/** Flashes a firmware image: signed on a camera in recovery mode, unsigned on a regular (unlocked) one. */
public class FirmwareUpdateProgressDialog extends DialogFragment {
    private static final String TAG = "librs fwupdate";
    private static final String ARG_FILE = "file";

    private LinearProgressIndicator mProgressBar;
    private TextView mPercent;
    private String mFirmwareFilePath;

    static FirmwareUpdateProgressDialog newInstance(String firmwareFilePath) {
        FirmwareUpdateProgressDialog dialog = new FirmwareUpdateProgressDialog();
        Bundle args = new Bundle();
        args.putString(ARG_FILE, firmwareFilePath);
        dialog.setArguments(args);
        dialog.setCancelable(false);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mFirmwareFilePath = requireArguments().getString(ARG_FILE, "");

        View view = View.inflate(requireContext(), R.layout.dialog_progress, null);
        ((TextView) view.findViewById(R.id.progress_message)).setText(R.string.fw_update_keep_connected);
        mProgressBar = view.findViewById(R.id.progress_bar);
        mPercent = view.findViewById(R.id.progress_percent);

        new Thread(this::update, "librs fw update").start();

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.fw_update_in_progress)
                .setView(view)
                .create();
    }

    private void update() {
        boolean done = false;
        boolean notify = false;
        final Activity activity = requireActivity();
        try (RsContext ctx = new RsContext(); DeviceList dl = ctx.queryDevices()) {
            if (dl.getDeviceCount() == 0)
                throw new RuntimeException(getString(R.string.no_device));
            try (Device d = dl.createDevice(0)) {
                if (mFirmwareFilePath.isEmpty())
                    throw new RuntimeException(getString(R.string.fw_file_required));
                final byte[] bytes = FileUtilities.readFile(new File(mFirmwareFilePath));
                if (d.is(Extension.UPDATE_DEVICE)) {
                    notify = true;
                    try (UpdateDevice fwud = d.as(Extension.UPDATE_DEVICE)) {
                        fwud.update(bytes, mProgressListener);
                    }
                } else if (d.is(Extension.UPDATABLE)) {
                    try (Updatable fwud = d.as(Extension.UPDATABLE)) {
                        fwud.updateUnsigned(bytes, mProgressListener);
                    }
                }
                done = true;
                Log.i(TAG, "firmware update finished successfully");
            }
        } catch (Exception e) {
            final String msg = getString(R.string.fw_update_error, e.getMessage());
            Log.e(TAG, msg);
            activity.runOnUiThread(() -> Toast.makeText(activity, msg, Toast.LENGTH_LONG).show());
        } finally {
            if (notify && activity instanceof DetachedActivity)
                ((DetachedActivity) activity).onFwUpdateStatus(done);
            activity.runOnUiThread(this::dismissAllowingStateLoss);
        }
    }

    private final ProgressListener mProgressListener = progress -> {
        final int percent = (int) (progress * 100);
        mProgressBar.post(() -> {
            mProgressBar.setProgressCompat(percent, true);
            mPercent.setText(String.format(Locale.US, "%d%%", percent));
        });
    };
}
