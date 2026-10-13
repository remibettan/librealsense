package com.intel.realsense.camera;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.intel.realsense.librealsense.Config;
import com.intel.realsense.librealsense.FrameSet;
import com.intel.realsense.librealsense.GLRsSurfaceView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Records the configured streams to a rosbag2 (.db3) file in the app's video folder. */
public class RecordingActivity extends AppCompatActivity {
    private static final String TAG = "librs camera rec";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Streamer mStreamer;
    private GLRsSurfaceView mGLSurfaceView;
    private TextView mRecordingChip;
    private long mStartTime;

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            long seconds = (SystemClock.elapsedRealtime() - mStartTime) / 1000;
            mRecordingChip.setText(getString(R.string.recording_label, String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)));
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording);
        Ui.edgeToEdge(this, findViewById(R.id.overlay));
        Ui.immersive(this);

        mGLSurfaceView = findViewById(R.id.recordingGlSurfaceView);
        mRecordingChip = findViewById(R.id.recording_chip);
        findViewById(R.id.stopRecordFab).setOnClickListener(v -> {
            startActivity(new Intent(this, PreviewActivity.class));
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        // Every resume records to a fresh file so a paused recording is never overwritten
        mStreamer = new Streamer(this, true, new Streamer.Listener() {
            @Override
            public void config(Config config) {
            }

            @Override
            public void onFrameset(FrameSet frameSet) {
                mGLSurfaceView.upload(frameSet);
            }
        });
        mStreamer.setRecordFilePath(newFilePath());
        try {
            mGLSurfaceView.clear();
            mStreamer.start();
            mStartTime = SystemClock.elapsedRealtime();
            mHandler.post(mTick);
        } catch (Exception e) {
            Log.e(TAG, "failed to start recording: " + e.getMessage());
            Toast.makeText(this, R.string.stream_config_failed, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mTick);
        if (mStreamer != null)
            mStreamer.stop();
        mGLSurfaceView.clear();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGLSurfaceView.close();
    }

    private String newFilePath() {
        File folder = FileUtilities.getRealSenseDir(this, "video");
        String name = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".db3";
        return new File(folder, name).getAbsolutePath();
    }
}
