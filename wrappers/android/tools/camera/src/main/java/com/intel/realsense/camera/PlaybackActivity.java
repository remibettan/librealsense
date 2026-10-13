package com.intel.realsense.camera;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.intel.realsense.librealsense.Config;
import com.intel.realsense.librealsense.FrameSet;
import com.intel.realsense.librealsense.GLRsSurfaceView;

import java.io.File;

/** Plays a .bag recording from the app's video folder. */
public class PlaybackActivity extends AppCompatActivity {
    private static final String TAG = "librs camera pb";
    private static final String FILE_PATH_KEY = "FILE_PATH_KEY";

    private String mFilePath;
    private Streamer mStreamer;
    private GLRsSurfaceView mGLSurfaceView;
    private StreamLabels mLabels;
    private TextView mFileChip;
    private volatile boolean mShow3D = false;

    private final ActivityResultLauncher<Intent> mPickFile = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    mFilePath = result.getData().getStringExtra(FileBrowserActivity.EXTRA_FILE_PATH);
                    mFileChip.setText(new File(mFilePath).getName());
                } else if (mFilePath == null) {
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playback);
        Ui.edgeToEdge(this, findViewById(R.id.overlay));
        Ui.immersive(this);

        mGLSurfaceView = findViewById(R.id.playbackGlSurfaceView);
        mLabels = new StreamLabels(findViewById(R.id.labels_layout));
        mFileChip = findViewById(R.id.file_chip);
        findViewById(R.id.playback_open_button).setOnClickListener(v -> pickFile());

        MaterialButton button3d = findViewById(R.id.playback_3d_button);
        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.app_settings), Context.MODE_PRIVATE);
        mShow3D = sharedPref.getBoolean(getString(R.string.show_3d), false);
        button3d.setChecked(mShow3D);
        button3d.addOnCheckedChangeListener((button, checked) -> {
            mLabels.clear();
            mGLSurfaceView.clear();
            mShow3D = checked;
            mGLSurfaceView.showPointcloud(checked);
            sharedPref.edit().putBoolean(getString(R.string.show_3d), checked).apply();
        });

        if (savedInstanceState != null)
            mFilePath = savedInstanceState.getString(FILE_PATH_KEY);
        if (mFilePath != null)
            mFileChip.setText(new File(mFilePath).getName());
    }

    private void pickFile() {
        mPickFile.launch(FileBrowserActivity.newIntent(this, FileUtilities.getRealSenseDir(this, "video"), R.string.select_recording));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing())
            return;
        Ui.immersive(this);
        if (mFilePath == null) {
            pickFile();
            return;
        }
        mStreamer = new Streamer(this, false, new Streamer.Listener() {
            @Override
            public void config(Config config) {
                config.enableAllStreams();
                config.enableDeviceFromFile(mFilePath);
            }

            @Override
            public void onFrameset(FrameSet frameSet) {
                mGLSurfaceView.showPointcloud(mShow3D);
                mGLSurfaceView.upload(frameSet);
                mLabels.update(mGLSurfaceView.getRectangles());
            }
        });
        try {
            mGLSurfaceView.clear();
            mStreamer.start();
        } catch (Exception e) {
            Log.e(TAG, "failed to play " + mFilePath + ": " + e.getMessage());
            Toast.makeText(this, R.string.stream_config_failed, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mLabels.clear();
        if (mStreamer != null)
            mStreamer.stop();
        mGLSurfaceView.clear();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGLSurfaceView.close();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(FILE_PATH_KEY, mFilePath);
    }
}
