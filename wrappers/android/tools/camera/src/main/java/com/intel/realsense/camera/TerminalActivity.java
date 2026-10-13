package com.intel.realsense.camera;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.intel.realsense.librealsense.DebugProtocol;
import com.intel.realsense.librealsense.Device;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.FrameSet;
import com.intel.realsense.librealsense.Pipeline;
import com.intel.realsense.librealsense.RsContext;

import java.io.File;

/** Raw command terminal over the debug protocol, driven by a commands XML file from the hw folder. */
public class TerminalActivity extends AppCompatActivity {
    private static final String TAG = "librs camera terminal";

    private MaterialSwitch mAutoComplete;
    private MaterialSwitch mStreaming;
    private MaterialAutoCompleteTextView mInputText;
    private TextView mOutputText;
    private String mFilePath;
    private Thread mStreamingThread;

    private final ActivityResultLauncher<Intent> mPickCommandsFile = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    finish();
                    return;
                }
                mFilePath = result.getData().getStringExtra(FileBrowserActivity.EXTRA_FILE_PATH);
                prefs().edit().putString(getString(R.string.terminal_commands_file), mFilePath).apply();
                setupAutoComplete();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);
        Ui.edgeToEdgeWithAppBar(this, findViewById(R.id.root));
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        Ui.setupToolbar(this, toolbar);

        mInputText = findViewById(R.id.terminal_input_edit_text);
        mOutputText = findViewById(R.id.terminal_output_text);
        mAutoComplete = findViewById(R.id.terminal_input_auto_complete);
        mStreaming = findViewById(R.id.terminal_stream);

        findViewById(R.id.terminal_send_button).setOnClickListener(v -> send());
        findViewById(R.id.terminal_clear_button).setOnClickListener(v -> {
            mInputText.setText("");
            mOutputText.setText("");
        });
        mInputText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send();
                return true;
            }
            return false;
        });
        mAutoComplete.setOnCheckedChangeListener((button, checked) -> {
            prefs().edit().putBoolean(getString(R.string.terminal_auto_complete), checked).apply();
            setupAutoComplete();
        });
        mStreaming.setOnCheckedChangeListener((button, checked) -> {
            if (checked)
                startStreaming();
            else
                stopStreaming();
        });

        mFilePath = prefs().getString(getString(R.string.terminal_commands_file), "");
        mAutoComplete.setChecked(prefs().getBoolean(getString(R.string.terminal_auto_complete), false));
        if (mFilePath.isEmpty() || !new File(mFilePath).exists())
            mPickCommandsFile.launch(FileBrowserActivity.newIntent(this, FileUtilities.getRealSenseDir(this, "hw"), R.string.commands_file_title));
        else
            setupAutoComplete();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(getString(R.string.app_settings), Context.MODE_PRIVATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mStreaming.isChecked())
            mStreaming.setChecked(false);
    }

    private void startStreaming() {
        mStreamingThread = new Thread(() -> {
            try (Pipeline pipe = new Pipeline()) {
                pipe.start();
                while (!Thread.currentThread().isInterrupted()) {
                    try (FrameSet frames = pipe.waitForFrames()) {
                    }
                }
                pipe.stop();
            } catch (Exception e) {
                runOnUiThread(() -> mOutputText.setText(getString(R.string.streaming_error, e.getMessage())));
            }
        }, "librs terminal streaming");
        mStreamingThread.start();
    }

    private void stopStreaming() {
        if (mStreamingThread == null)
            return;
        mStreamingThread.interrupt();
        try {
            mStreamingThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        mStreamingThread = null;
    }

    private void send() {
        String content = mInputText.getText().toString().trim();
        if (content.isEmpty())
            return;
        try (RsContext ctx = new RsContext(); DeviceList devices = ctx.queryDevices()) {
            if (devices.getDeviceCount() == 0) {
                Toast.makeText(this, R.string.no_device, Toast.LENGTH_LONG).show();
                return;
            }
            try (Device device = devices.createDevice(0); DebugProtocol dp = device.as(Extension.DEBUG)) {
                String res = dp.SendAndReceiveRawData(mFilePath, content);
                mOutputText.setText(getString(R.string.command_prefix, content, res));
                mInputText.setText("");
            } catch (Exception e) {
                mOutputText.setText(getString(R.string.error_prefix, e.getMessage()));
            }
        }
    }

    private void setupAutoComplete() {
        try {
            if (mAutoComplete.isChecked() && mFilePath != null && !mFilePath.isEmpty())
                mInputText.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, DebugProtocol.getCommands(mFilePath)));
            else
                mInputText.setAdapter(null);
        } catch (Exception e) {
            Log.e(TAG, "auto complete: " + e.getMessage());
            mOutputText.setText(getString(R.string.error_prefix, e.getMessage()));
        }
    }
}
