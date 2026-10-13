package com.intel.realsense.camera;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.intel.realsense.librealsense.Config;
import com.intel.realsense.librealsense.FrameSet;
import com.intel.realsense.librealsense.GLRsSurfaceView;

/** Live viewer: streams the configured profiles and hosts the bottom toolbar. */
public class PreviewActivity extends AppCompatActivity {
    private static final String TAG = "librs camera preview";
    static final String EXTRA_KEEPALIVE = "keepalive";
    private static final long STATS_REFRESH_MS = 250;

    private GLRsSurfaceView mGLSurfaceView;
    private StreamLabels mLabels;
    private View mStatsCard;
    private TextView mStatsText;
    private MaterialButton m3dButton;

    private Streamer mStreamer;
    private StreamingStats mStreamingStats;
    private FwLogsThread mFwLogsThread;

    private volatile boolean mShowStats = false;
    private volatile boolean mShow3D = false;
    private long mLastStatsRefresh = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!getIntent().getBooleanExtra(EXTRA_KEEPALIVE, true)) {
            finish();
            return;
        }
        setContentView(R.layout.activity_preview);
        Ui.edgeToEdge(this, findViewById(R.id.overlay));
        Ui.immersive(this);

        mGLSurfaceView = findViewById(R.id.glSurfaceView);
        mLabels = new StreamLabels(findViewById(R.id.labels_layout));
        mStatsCard = findViewById(R.id.stats_card);
        mStatsText = findViewById(R.id.streaming_stats_text);
        MaterialButton statsButton = findViewById(R.id.preview_stats_button);
        m3dButton = findViewById(R.id.preview_3d_button);

        findViewById(R.id.preview_settings_button).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.preview_playback_button).setOnClickListener(v -> startActivity(new Intent(this, PlaybackActivity.class)));
        findViewById(R.id.controls_button).setOnClickListener(v -> new ControlsDialog().show(getSupportFragmentManager(), null));
        findViewById(R.id.start_record_fab).setOnClickListener(v -> {
            startActivity(new Intent(this, RecordingActivity.class));
            finish();
        });
        statsButton.addOnCheckedChangeListener((button, checked) -> {
            mShowStats = checked;
            updateStats();
        });
        m3dButton.addOnCheckedChangeListener((button, checked) -> set3d(checked));

        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.app_settings), Context.MODE_PRIVATE);
        mShow3D = sharedPref.getBoolean(getString(R.string.show_3d), false);
        m3dButton.setChecked(mShow3D);

        // The home screen relaunches the viewer while a camera is connected, so back exits the app
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!intent.getBooleanExtra(EXTRA_KEEPALIVE, true))
            finish();
    }

    private void updateStats() {
        mLabels.clear();
        mGLSurfaceView.clear();
        mGLSurfaceView.setVisibility(mShowStats ? View.GONE : View.VISIBLE);
        mStatsCard.setVisibility(mShowStats ? View.VISIBLE : View.GONE);
    }

    private void set3d(boolean show) {
        mLabels.clear();
        mGLSurfaceView.clear();
        mShow3D = show;
        mGLSurfaceView.showPointcloud(show);
        getSharedPreferences(getString(R.string.app_settings), Context.MODE_PRIVATE).edit()
                .putBoolean(getString(R.string.show_3d), show).apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing())
            return;
        Ui.immersive(this);

        mStreamingStats = new StreamingStats();
        mStreamer = new Streamer(this, true, new Streamer.Listener() {
            @Override
            public void config(Config config) {
            }

            @Override
            public void onFrameset(final FrameSet frameSet) {
                mStreamingStats.onFrameset(frameSet);
                if (mShowStats) {
                    long now = SystemClock.elapsedRealtime();
                    if (now - mLastStatsRefresh < STATS_REFRESH_MS)
                        return;
                    mLastStatsRefresh = now;
                    final String text = mStreamingStats.prettyPrint();
                    runOnUiThread(() -> mStatsText.setText(text));
                } else {
                    mGLSurfaceView.showPointcloud(mShow3D);
                    mGLSurfaceView.upload(frameSet);
                    mLabels.update(mGLSurfaceView.getRectangles());
                }
            }
        });

        try {
            mGLSurfaceView.clear();
            mStreamer.start();
        } catch (Exception e) {
            mStreamer.stop();
            Log.e(TAG, "failed to start streaming: " + e.getMessage());
            if (!Streamer.hasStreamableDevice()) {
                finish();
                return;
            }
            Toast.makeText(this, R.string.stream_config_failed, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
        }

        resumeFwLogger();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mLabels != null)
            mLabels.clear();
        if (mStreamer != null)
            mStreamer.stop();
        if (mGLSurfaceView != null)
            mGLSurfaceView.clear();
        pauseFwLogger();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mGLSurfaceView != null)
            mGLSurfaceView.close();
    }

    private void resumeFwLogger() {
        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.app_settings), Context.MODE_PRIVATE);
        if (!sharedPref.getBoolean(getString(R.string.fw_logging), false) || mFwLogsThread != null)
            return;
        mFwLogsThread = new FwLogsThread(sharedPref.getString(getString(R.string.fw_logging_file_path), ""));
        mFwLogsThread.start();
    }

    private void pauseFwLogger() {
        if (mFwLogsThread == null)
            return;
        mFwLogsThread.stopLogging();
        mFwLogsThread.interrupt();
        mFwLogsThread = null;
    }
}
