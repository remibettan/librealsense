package com.intel.realsense.camera;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.intel.realsense.librealsense.CameraInfo;
import com.intel.realsense.librealsense.Device;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.RsContext;
import com.intel.realsense.librealsense.Sensor;
import com.intel.realsense.librealsense.StreamProfile;
import com.intel.realsense.librealsense.StreamType;
import com.intel.realsense.librealsense.Updatable;
import com.intel.realsense.librealsense.VideoStreamProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Device actions (info, advanced mode, presets, firmware) and the per-stream profile selection. */
public class SettingsActivity extends AppCompatActivity implements FirmwareUpdateDialog.Host {
    private static final String TAG = "librs camera settings";
    private static final int LOAD_RETRIES = 3;
    private static final int LOAD_RETRY_MS = 500;
    private static final String BACKUP_FILE_NAME = "fwdump.bin";
    private static final String TEMP_FW_FILE = "tempfw.bin";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private Device mDevice;
    private boolean mAdvancedFeaturesEnabled = false; // terminal and fw logs need xml files in the hw folder
    private LinearLayout mActions;
    private RecyclerView mStreams;

    private final ActivityResultLauncher<String[]> mOpenUnsignedFirmware = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null)
                    flashUnsigned(uri);
            });

    private final ActivityResultLauncher<String[]> mOpenSignedFirmware = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null)
                    startSignedUpdate(uri);
            });

    private final ActivityResultLauncher<Intent> mPickFwLogsFile = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    prefs().edit().putString(getString(R.string.fw_logging_file_path),
                            result.getData().getStringExtra(FileBrowserActivity.EXTRA_FILE_PATH)).apply();
                    toggleFwLogging(false);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Ui.edgeToEdgeWithAppBar(this, findViewById(R.id.root));
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        Ui.setupToolbar(this, toolbar);

        mActions = findViewById(R.id.actions_container);
        mStreams = findViewById(R.id.streams_list);
        mStreams.setLayoutManager(new LinearLayoutManager(this));
        mAdvancedFeaturesEnabled = FileUtilities.hasFiles(FileUtilities.getRealSenseDir(this, "hw"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDevice(0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacksAndMessages(null);
        closeDevice();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutor.shutdown();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(getString(R.string.app_settings), Context.MODE_PRIVATE);
    }

    private void closeDevice() {
        if (mDevice != null) {
            mDevice.close();
            mDevice = null;
        }
    }

    /** The camera may still be re-enumerating right after the viewer stopped, so retry briefly. */
    private void loadDevice(int attempt) {
        closeDevice();
        try (RsContext ctx = new RsContext(); DeviceList devices = ctx.queryDevices()) {
            if (devices.getDeviceCount() > 0)
                mDevice = devices.createDevice(0);
        }
        if (mDevice != null && mDevice.is(Extension.UPDATE_DEVICE))
            closeDevice();
        if (mDevice == null) {
            if (attempt < LOAD_RETRIES) {
                mHandler.postDelayed(() -> loadDevice(attempt + 1), LOAD_RETRY_MS);
                return;
            }
            Log.e(TAG, "no device to configure");
            Toast.makeText(this, R.string.settings_load_failed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            showDevice();
            buildActions();
            buildStreams();
        } catch (Exception e) {
            Log.e(TAG, "failed to load settings: " + e.getMessage());
            Toast.makeText(this, R.string.settings_load_failed, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private String info(CameraInfo ci) {
        return mDevice.supportsInfo(ci) ? mDevice.getInfo(ci) : getString(R.string.unknown);
    }

    private void showDevice() {
        ((TextView) findViewById(R.id.device_name)).setText(info(CameraInfo.NAME));
        String details = getString(R.string.serial_number) + " " + info(CameraInfo.SERIAL_NUMBER) +
                "  •  " + getString(R.string.firmware_version) + " " + info(CameraInfo.FIRMWARE_VERSION);
        if (mDevice.supportsInfo(CameraInfo.USB_TYPE_DESCRIPTOR))
            details += "  •  USB " + mDevice.getInfo(CameraInfo.USB_TYPE_DESCRIPTOR);
        ((TextView) findViewById(R.id.device_details)).setText(details);

        String appVersion = "-";
        try {
            appVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        ((TextView) findViewById(R.id.software_details)).setText(getString(R.string.versions_format, RsContext.getVersion(), appVersion));
    }

    private void buildActions() {
        mActions.removeAllViews();
        addAction(R.drawable.ic_info, R.string.device_info, R.string.device_info_subtitle,
                v -> startActivity(new Intent(this, InfoActivity.class)));

        if (mDevice.supportsInfo(CameraInfo.ADVANCED_MODE)) {
            boolean advanced = mDevice.isInAdvancedMode();
            addSwitch(R.drawable.ic_tune, R.string.advanced_mode, R.string.advanced_mode_subtitle, advanced, checked -> {
                mDevice.toggleAdvancedMode(checked);
                // The camera resets to apply the mode; the home screen picks it up again
                Toast.makeText(this, R.string.camera_restarting, Toast.LENGTH_SHORT).show();
                finish();
            });
            if (advanced)
                addAction(R.drawable.ic_list, R.string.presets, R.string.presets_subtitle,
                        v -> new PresetsDialog().show(getSupportFragmentManager(), null));
        }

        if (mDevice.is(Extension.UPDATABLE)) {
            addAction(R.drawable.ic_download, R.string.firmware_update, R.string.firmware_update_subtitle,
                    v -> FirmwareUpdateDialog.newInstance(true, false).show(getSupportFragmentManager(), null));
            try (Updatable fwud = mDevice.as(Extension.UPDATABLE)) {
                if (fwud.supportsInfo(CameraInfo.CAMERA_LOCKED) && fwud.getInfo(CameraInfo.CAMERA_LOCKED).equals("NO"))
                    addAction(R.drawable.ic_download, R.string.firmware_update_unsigned, R.string.firmware_update_unsigned_subtitle,
                            v -> mOpenUnsignedFirmware.launch(new String[]{"*/*"}));
            }
            addAction(R.drawable.ic_save, R.string.create_fw_backup, R.string.create_fw_backup_subtitle, v -> createBackup());
        }

        if (mAdvancedFeaturesEnabled) {
            addAction(R.drawable.ic_terminal, R.string.terminal, R.string.terminal_subtitle,
                    v -> startActivity(new Intent(this, TerminalActivity.class)));
            addSwitch(R.drawable.ic_description, R.string.fw_logging_title, R.string.fw_logging_subtitle,
                    prefs().getBoolean(getString(R.string.fw_logging), false), checked -> toggleFwLogging(true));
        }
    }

    private interface OnToggle {
        void onToggle(boolean checked);
    }

    private View addRow(@DrawableRes int icon, @StringRes int title, @StringRes int subtitle) {
        View row = getLayoutInflater().inflate(R.layout.item_action, mActions, false);
        ((ImageView) row.findViewById(R.id.action_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.action_title)).setText(title);
        ((TextView) row.findViewById(R.id.action_subtitle)).setText(subtitle);
        mActions.addView(row);
        return row;
    }

    private void addAction(@DrawableRes int icon, @StringRes int title, @StringRes int subtitle, View.OnClickListener listener) {
        addRow(icon, title, subtitle).setOnClickListener(listener);
    }

    private void addSwitch(@DrawableRes int icon, @StringRes int title, @StringRes int subtitle, boolean checked, OnToggle onToggle) {
        View row = addRow(icon, title, subtitle);
        row.findViewById(R.id.action_chevron).setVisibility(View.GONE);
        MaterialSwitch sw = row.findViewById(R.id.action_switch);
        sw.setVisibility(View.VISIBLE);
        sw.setChecked(checked);
        row.setOnClickListener(v -> {
            sw.setChecked(!sw.isChecked());
            onToggle.onToggle(sw.isChecked());
        });
    }

    private void buildStreams() {
        if (!mDevice.supportsInfo(CameraInfo.PRODUCT_ID))
            throw new RuntimeException("try to config unknown device");
        final String pid = mDevice.getInfo(CameraInfo.PRODUCT_ID);
        List<StreamProfileSelector> selectors = createSettingList(mDevice, pid);
        removeUnsupportedProfiles(selectors);
        mStreams.setAdapter(new StreamProfileAdapter(selectors, selector -> {
            StreamProfile p = selector.getProfile();
            prefs().edit()
                    .putBoolean(getEnabledDeviceConfigString(pid, p.getType(), p.getIndex()), selector.isEnabled())
                    .putInt(getIndexDeviceConfigString(pid, p.getType(), p.getIndex()), selector.getIndex())
                    .apply();
        }));
    }

    private List<StreamProfileSelector> createSettingList(Device device, String pid) {
        List<StreamProfileSelector> lines = new ArrayList<>();
        for (List<StreamProfile> list : createProfilesMap(device).values()) {
            StreamProfile p = list.get(0);
            boolean enabled = prefs().getBoolean(getEnabledDeviceConfigString(pid, p.getType(), p.getIndex()), false);
            int index = prefs().getInt(getIndexDeviceConfigString(pid, p.getType(), p.getIndex()), 0);
            lines.add(new StreamProfileSelector(enabled, index, list));
        }
        Collections.sort(lines);
        return lines;
    }

    /** The confidence stream is RAW8, which the viewer cannot display. */
    private static void removeUnsupportedProfiles(List<StreamProfileSelector> selectors) {
        for (StreamProfileSelector s : selectors) {
            if (s.getProfile().getType() == StreamType.CONFIDENCE) {
                selectors.remove(s);
                return;
            }
        }
    }

    private void createBackup() {
        View view = View.inflate(this, R.layout.dialog_progress, null);
        ((TextView) view.findViewById(R.id.progress_message)).setText(R.string.please_wait_minutes);
        ((LinearProgressIndicator) view.findViewById(R.id.progress_bar)).setIndeterminate(true);
        view.findViewById(R.id.progress_percent).setVisibility(View.GONE);
        final AlertDialog progress = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.saving_backup).setView(view).setCancelable(false).show();

        mExecutor.execute(() -> {
            boolean ok = false;
            String message;
            try (RsContext ctx = new RsContext(); DeviceList dl = ctx.queryDevices();
                 Device device = dl.createDevice(0); Updatable upd = device.as(Extension.UPDATABLE)) {
                File file = FileUtilities.saveFile(this, BACKUP_FILE_NAME, upd.createFlashBackup());
                message = getString(R.string.backup_saved_message, file.getAbsolutePath());
                ok = true;
            } catch (Exception e) {
                Log.e(TAG, "backup failed: " + e.getMessage());
                message = getString(R.string.backup_failed, e.getMessage());
            }
            final boolean success = ok;
            final String text = message;
            runOnUiThread(() -> {
                progress.dismiss();
                if (!isFinishing())
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(success ? R.string.backup_saved_title : R.string.create_fw_backup)
                            .setMessage(text)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
            });
        });
    }

    private void flashUnsigned(Uri uri) {
        mExecutor.execute(() -> {
            try {
                final File file = FileUtilities.copyToAppStorage(this, uri, TEMP_FW_FILE);
                runOnUiThread(() -> FirmwareUpdateProgressDialog.newInstance(file.getAbsolutePath()).show(getSupportFragmentManager(), null));
            } catch (Exception e) {
                Log.e(TAG, "firmware file error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.fw_file_copy_failed, e.getMessage()), Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public void onFirmwareUpdateRequested() {
        mOpenSignedFirmware.launch(new String[]{"*/*"});
    }

    @Override
    public void onFirmwareUpdateSkipped() {
    }

    /** Stores the image and reboots the camera into recovery; the home screen flashes it once it reappears. */
    private void startSignedUpdate(Uri uri) {
        mExecutor.execute(() -> {
            try {
                File file = FileUtilities.copyToAppStorage(this, uri, TEMP_FW_FILE);
                FirmwareUpdater.setPendingFile(this, file.getAbsolutePath());
                if (FirmwareUpdater.enterUpdateState())
                    runOnUiThread(this::finish);
                else
                    runOnUiThread(() -> Toast.makeText(this, R.string.no_device, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                Log.e(TAG, "firmware file error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.fw_file_copy_failed, e.getMessage()), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void toggleFwLogging(boolean rebuild) {
        String path = prefs().getString(getString(R.string.fw_logging_file_path), "");
        if (path.isEmpty() || !new File(path).exists()) {
            mPickFwLogsFile.launch(FileBrowserActivity.newIntent(this, FileUtilities.getRealSenseDir(this, "hw"), R.string.select_file));
            return;
        }
        boolean enabled = prefs().getBoolean(getString(R.string.fw_logging), false);
        prefs().edit().putBoolean(getString(R.string.fw_logging), !enabled).apply();
        if (rebuild)
            buildActions();
    }

    // Shared with the streamer: how stream choices are keyed in the preferences

    private static String getDeviceConfig(String pid, StreamType streamType, int streamIndex) {
        return pid + "_" + streamType.name() + "_" + streamIndex;
    }

    public static String getEnabledDeviceConfigString(String pid, StreamType streamType, int streamIndex) {
        return getDeviceConfig(pid, streamType, streamIndex) + "_enabled";
    }

    public static String getIndexDeviceConfigString(String pid, StreamType streamType, int streamIndex) {
        return getDeviceConfig(pid, streamType, streamIndex) + "_index";
    }

    /** Profiles grouped per stream (type + index), in a stable order shared by the settings and the streamer. */
    public static Map<Integer, List<StreamProfile>> createProfilesMap(Device device) {
        Map<Integer, List<StreamProfile>> rv = new HashMap<>();
        for (Sensor s : device.querySensors()) {
            for (StreamProfile p : s.getStreamProfiles()) {
                int key = new Pair<>(p.getType(), p.getIndex()).hashCode();
                List<StreamProfile> list = rv.get(key);
                if (list == null) {
                    list = new ArrayList<>();
                    rv.put(key, list);
                }
                list.add(p);
            }
        }
        return rv;
    }

    /** Marks a stream profile on/off in the preferences, e.g. to record the camera's default streams. */
    public static void setProfileSetting(SharedPreferences sharedPref, Device device, StreamProfile p, boolean enabled) {
        String pid = device.getInfo(CameraInfo.PRODUCT_ID);
        for (List<StreamProfile> profiles : createProfilesMap(device).values()) {
            for (int i = 0; i < profiles.size(); i++) {
                if (sameProfile(p, profiles.get(i))) {
                    sharedPref.edit()
                            .putBoolean(getEnabledDeviceConfigString(pid, p.getType(), p.getIndex()), enabled)
                            .putInt(getIndexDeviceConfigString(pid, p.getType(), p.getIndex()), i)
                            .apply();
                    return;
                }
            }
        }
    }

    private static boolean sameProfile(StreamProfile a, StreamProfile b) {
        if (a.getType() != b.getType() || a.getIndex() != b.getIndex() || a.getFormat() != b.getFormat() || a.getFrameRate() != b.getFrameRate())
            return false;
        if (a.is(Extension.VIDEO_PROFILE) && b.is(Extension.VIDEO_PROFILE)) {
            VideoStreamProfile va = a.as(Extension.VIDEO_PROFILE), vb = b.as(Extension.VIDEO_PROFILE);
            return va.getWidth() == vb.getWidth() && va.getHeight() == vb.getHeight();
        }
        return a.is(Extension.MOTION_PROFILE) && b.is(Extension.MOTION_PROFILE);
    }
}
