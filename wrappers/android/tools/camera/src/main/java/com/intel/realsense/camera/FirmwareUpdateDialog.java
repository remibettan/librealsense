package com.intel.realsense.camera;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.intel.realsense.librealsense.CameraInfo;
import com.intel.realsense.librealsense.Device;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.RsContext;

/** Offers (or demands) a signed firmware update; the host activity picks the image and starts the flow. */
public class FirmwareUpdateDialog extends DialogFragment {
    private static final String ARG_REQUEST = "request";
    private static final String ARG_REQUIRED = "required";

    interface Host {
        /** The user accepted: pick a firmware file and switch the camera to recovery mode. */
        void onFirmwareUpdateRequested();

        /** The user postponed an optional update. */
        void onFirmwareUpdateSkipped();
    }

    private boolean mLatestInstalled = false;
    private boolean mSkipped = false;

    /**
     * @param request  opened from Settings by the user rather than as a prompt
     * @param required the camera firmware is below the supported minimum
     */
    static FirmwareUpdateDialog newInstance(boolean request, boolean required) {
        FirmwareUpdateDialog dialog = new FirmwareUpdateDialog();
        Bundle args = new Bundle();
        args.putBoolean(ARG_REQUEST, request);
        args.putBoolean(ARG_REQUIRED, required);
        dialog.setArguments(args);
        dialog.setCancelable(!required);
        return dialog;
    }

    private Host host() {
        return (Host) requireActivity();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = requireArguments();
        final boolean request = args.getBoolean(ARG_REQUEST);
        final boolean required = args.getBoolean(ARG_REQUIRED);

        View view = View.inflate(requireContext(), R.layout.dialog_firmware_update, null);
        TextView message = view.findViewById(R.id.firmware_update_message);
        CheckBox dontAsk = view.findViewById(R.id.dont_ask_checkbox);
        dontAsk.setVisibility(request || required ? View.GONE : View.VISIBLE);
        dontAsk.setOnCheckedChangeListener((button, checked) ->
                requireContext().getSharedPreferences(getString(R.string.app_settings), Context.MODE_PRIVATE).edit()
                        .putBoolean(getString(R.string.show_update_firmware), !checked).apply());

        message.setText(required ? getString(R.string.fw_update_required_message) : describeDevice());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(required ? R.string.fw_update_required_title : request ? R.string.firmware_update : R.string.fw_update_title)
                .setView(view)
                .setPositiveButton(R.string.update, (d, w) -> host().onFirmwareUpdateRequested());
        if (!required)
            builder.setNegativeButton(request ? R.string.cancel : R.string.later, (d, w) -> skip(request));
        return builder.create();
    }

    private String describeDevice() {
        try (RsContext ctx = new RsContext(); DeviceList dl = ctx.queryDevices()) {
            if (dl.getDeviceCount() == 0)
                return getString(R.string.no_device);
            try (Device device = dl.createDevice(0)) {
                if (device == null)
                    return getString(R.string.no_device);
                final String fw = device.getInfo(CameraInfo.FIRMWARE_VERSION);
                final String recFw = device.supportsInfo(CameraInfo.RECOMMENDED_FIRMWARE_VERSION) ?
                        device.getInfo(CameraInfo.RECOMMENDED_FIRMWARE_VERSION) : getString(R.string.unknown);
                if (recFw.equals(fw)) {
                    mLatestInstalled = true;
                    return getString(R.string.fw_latest_installed);
                }
                return getString(R.string.fw_update_message, fw, recFw);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mLatestInstalled)
            ((AlertDialog) requireDialog()).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
    }

    private void skip(boolean request) {
        if (mSkipped || request)
            return;
        mSkipped = true;
        host().onFirmwareUpdateSkipped();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        skip(requireArguments().getBoolean(ARG_REQUEST));
    }
}
