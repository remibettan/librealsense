package com.intel.realsense.camera;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.intel.realsense.librealsense.Device;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.RsContext;

import java.io.IOException;
import java.io.InputStream;

/** Single-choice list of the bundled depth presets (assets/presets). */
public class PresetsDialog extends DialogFragment {
    private static final String TAG = "librs presets";
    private static int sSelected = -1;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String[] files;
        try {
            files = requireContext().getAssets().list("presets");
        } catch (IOException e) {
            files = new String[0];
        }
        final String[] presets = files == null ? new String[0] : files;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.presets);
        if (presets.length == 0)
            return builder.setMessage(R.string.no_presets).setPositiveButton(android.R.string.ok, null).create();

        String[] names = new String[presets.length];
        for (int i = 0; i < presets.length; i++)
            names[i] = presets[i].contains(".") ? presets[i].substring(0, presets[i].lastIndexOf('.')) : presets[i];
        final int[] selected = {sSelected};
        return builder
                .setSingleChoiceItems(names, sSelected, (d, which) -> selected[0] = which)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.apply, (d, which) -> {
                    if (selected[0] >= 0) {
                        sSelected = selected[0];
                        apply(presets[selected[0]], names[selected[0]]);
                    }
                })
                .create();
    }

    private void apply(String asset, String name) {
        try (RsContext ctx = new RsContext(); DeviceList devices = ctx.queryDevices()) {
            if (devices.getDeviceCount() == 0) {
                Toast.makeText(requireContext(), R.string.no_device, Toast.LENGTH_LONG).show();
                return;
            }
            try (Device device = devices.createDevice(0)) {
                if (device == null || !device.isInAdvancedMode()) {
                    Toast.makeText(requireContext(), R.string.preset_requires_advanced, Toast.LENGTH_LONG).show();
                    return;
                }
                try (InputStream is = requireContext().getAssets().open("presets/" + asset)) {
                    byte[] buffer = new byte[is.available()];
                    int read = is.read(buffer);
                    device.loadPresetFromJson(buffer);
                    Log.i(TAG, "preset set to " + name + " (" + read + " bytes)");
                    Toast.makeText(requireContext(), getString(R.string.preset_applied, name), Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "failed to set preset: " + e.getMessage());
            Toast.makeText(requireContext(), getString(R.string.preset_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }
}
