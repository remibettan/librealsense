package com.intel.realsense.camera;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.intel.realsense.librealsense.CameraInfo;
import com.intel.realsense.librealsense.Device;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.DeviceListener;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.ProductLine;
import com.intel.realsense.librealsense.RsContext;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home screen: waits for a camera, validates its firmware and launches the viewer.
 * It stays below the viewer so it can close the whole stack when the camera is unplugged.
 */
public class DetachedActivity extends AppCompatActivity implements FirmwareUpdateDialog.Host {
    private static final String TAG = "librs camera detached";
    private static final String MINIMAL_D400_FW_VERSION = "5.10.0.0";
    private static final String TEMP_FW_FILE = "tempfw.bin";

    private final RsContext mRsContext = new RsContext();
    private final Map<ProductLine, String> mMinimalFirmwares = new HashMap<>();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private View mPermissionButton;
    private boolean mUpdating = false;
    private boolean mPreviewStarted = false;

    private final ActivityResultLauncher<String> mCameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    onPermissionGranted();
                } else {
                    Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show();
                    mPermissionButton.setVisibility(View.VISIBLE);
                }
            });

    private final ActivityResultLauncher<String[]> mPickFirmware = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onFirmwareFilePicked);

    private final DeviceListener mListener = new DeviceListener() {
        @Override
        public void onDeviceAttach() {
            runOnUiThread(DetachedActivity.this::validateDevice);
        }

        @Override
        public void onDeviceDetach() {
            runOnUiThread(DetachedActivity.this::onDetached);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detached);
        Ui.edgeToEdge(this, findViewById(R.id.root));

        mMinimalFirmwares.put(ProductLine.D400, MINIMAL_D400_FW_VERSION);

        findViewById(R.id.playback_button).setOnClickListener(v -> startActivity(new Intent(this, PlaybackActivity.class)));
        mPermissionButton = findViewById(R.id.permission_button);
        mPermissionButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null))));

        String appVersion = "-";
        try {
            appVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        ((TextView) findViewById(R.id.versions_text)).setText(getString(R.string.versions_format, RsContext.getVersion(), appVersion));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            onPermissionGranted();
        else
            mCameraPermission.launch(Manifest.permission.CAMERA);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRsContext.close();
        mExecutor.shutdownNow();
    }

    private void onPermissionGranted() {
        mPermissionButton.setVisibility(View.GONE);
        try {
            RsContext.init(getApplicationContext());
            mRsContext.setDevicesChangedCallback(mListener);
            validateDevice();
        } catch (Exception e) {
            Log.e(TAG, "SDK init failed: " + e.getMessage());
            Toast.makeText(this, getString(R.string.sdk_init_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    /** Closes the viewer (and anything above it) once the camera is gone. */
    private void onDetached() {
        if (!mPreviewStarted || Streamer.hasStreamableDevice())
            return;
        mPreviewStarted = false;
        Intent intent = new Intent(this, PreviewActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(PreviewActivity.EXTRA_KEEPALIVE, false);
        startActivity(intent);
    }

    private void validateDevice() {
        if (mUpdating)
            return;
        try (DeviceList dl = mRsContext.queryDevices()) {
            if (dl.getDeviceCount() == 0)
                return;
            try (Device d = dl.createDevice(0)) {
                if (d == null)
                    return;
                if (d.is(Extension.UPDATE_DEVICE))
                    onRecoveryDevice();
                else if (validateFwVersion(d))
                    startPreview();
            }
        } catch (Exception e) {
            Log.e(TAG, "error while validating device: " + e.getMessage());
        }
    }

    private void startPreview() {
        mPreviewStarted = true;
        // Single-top instance: a later intent with keepalive=false closes it instead of stacking another
        Intent intent = new Intent(this, PreviewActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(PreviewActivity.EXTRA_KEEPALIVE, true);
        startActivity(intent);
    }

    private boolean validateFwVersion(Device device) {
        final String currFw = device.getInfo(CameraInfo.FIRMWARE_VERSION).split("\n")[0];
        final ProductLine pl = ProductLine.valueOf(device.getInfo(CameraInfo.PRODUCT_LINE));
        String minimalFw = mMinimalFirmwares.get(pl);
        if (minimalFw != null && !compareFwVersion(currFw, minimalFw)) {
            FirmwareUpdateDialog.newInstance(false, true).show(getSupportFragmentManager(), null);
            return false;
        }

        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.app_settings), Context.MODE_PRIVATE);
        boolean showUpdateMessage = sharedPref.getBoolean(getString(R.string.show_update_firmware), true);
        if (!showUpdateMessage || !device.supportsInfo(CameraInfo.RECOMMENDED_FIRMWARE_VERSION))
            return true;

        final String recommendedFw = device.getInfo(CameraInfo.RECOMMENDED_FIRMWARE_VERSION);
        if (!compareFwVersion(currFw, recommendedFw)) {
            FirmwareUpdateDialog.newInstance(false, false).show(getSupportFragmentManager(), null);
            return false;
        }
        return true;
    }

    /** True when currFw is at least otherFw (dotted numeric versions). */
    private static boolean compareFwVersion(String currFw, String otherFw) {
        String[] sFw = currFw.split("\\.");
        String[] sOther = otherFw.split("\\.");
        for (int i = 0; i < Math.min(sFw.length, sOther.length); i++) {
            int curr = Integer.parseInt(sFw[i].trim()), other = Integer.parseInt(sOther[i].trim());
            if (curr > other)
                return true;
            if (curr < other)
                return false;
        }
        return true;
    }

    /** A camera in recovery mode: flash the image chosen earlier, or ask for one. */
    private void onRecoveryDevice() {
        String pending = FirmwareUpdater.getPendingFile(this);
        if (!pending.isEmpty() && new File(pending).exists()) {
            flash(pending);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fw_recovery_title)
                .setMessage(R.string.fw_recovery_message)
                .setCancelable(false)
                .setPositiveButton(R.string.choose_file, (d, w) -> mPickFirmware.launch(new String[]{"*/*"}))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void flash(String path) {
        mUpdating = true;
        FirmwareUpdateProgressDialog.newInstance(path).show(getSupportFragmentManager(), null);
    }

    @Override
    public void onFirmwareUpdateRequested() {
        mPickFirmware.launch(new String[]{"*/*"});
    }

    @Override
    public void onFirmwareUpdateSkipped() {
        startPreview();
    }

    private void onFirmwareFilePicked(Uri uri) {
        if (uri == null)
            return;
        mExecutor.execute(() -> {
            try {
                File file = FileUtilities.copyToAppStorage(this, uri, TEMP_FW_FILE);
                FirmwareUpdater.setPendingFile(this, file.getAbsolutePath());
                if (isInRecovery())
                    runOnUiThread(() -> flash(file.getAbsolutePath()));
                else if (!FirmwareUpdater.enterUpdateState())
                    runOnUiThread(() -> Toast.makeText(this, R.string.no_device, Toast.LENGTH_LONG).show());
                // Otherwise the camera re-enumerates in recovery mode and validateDevice() flashes the stored image
            } catch (Exception e) {
                Log.e(TAG, "firmware file error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.fw_file_copy_failed, e.getMessage()), Toast.LENGTH_LONG).show());
            }
        });
    }

    private boolean isInRecovery() {
        try (DeviceList dl = mRsContext.queryDevices()) {
            if (dl.getDeviceCount() == 0)
                return false;
            try (Device d = dl.createDevice(0)) {
                return d != null && d.is(Extension.UPDATE_DEVICE);
            }
        }
    }

    public void onFwUpdateStatus(final boolean status) {
        mUpdating = false;
        FirmwareUpdater.clearPendingFile(this);
        runOnUiThread(() -> Toast.makeText(this, status ? R.string.fw_update_done : R.string.fw_update_failed, Toast.LENGTH_LONG).show());
    }
}
