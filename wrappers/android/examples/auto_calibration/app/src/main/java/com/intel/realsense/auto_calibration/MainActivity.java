package com.intel.realsense.auto_calibration;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.intel.realsense.librealsense.Colorizer;
import com.intel.realsense.librealsense.Config;
import com.intel.realsense.librealsense.DepthFrame;
import com.intel.realsense.librealsense.DeviceListener;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.Frame;
import com.intel.realsense.librealsense.FrameReleaser;
import com.intel.realsense.librealsense.FrameSet;
import com.intel.realsense.librealsense.GLRsSurfaceView;
import com.intel.realsense.librealsense.Option;
import com.intel.realsense.librealsense.Pipeline;
import com.intel.realsense.librealsense.PipelineProfile;
import com.intel.realsense.librealsense.RsContext;
import com.intel.realsense.librealsense.Sensor;
import com.intel.realsense.librealsense.StreamFormat;
import com.intel.realsense.librealsense.StreamType;

import java.text.DecimalFormat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "librs camera calibration";
    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 0;

    static {
        System.loadLibrary("native-lib");
    }

    private RsContext mRsContext;
    private Pipeline mPipeline;
    private GLRsSurfaceView mGLSurfaceView;

    private Colorizer mColorize;
    private CalibrationTablesHandler mCalibrationTablesHandler;
    private SharedPreferences mSharedPreferences;
    private Context mContext;
    private PipelineProfile mProfile;

    private TareProcessor mTareProcessor;
    private CalibrationProcessor mCalibrationProcessor;





    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mStreamingThread.isAlive())
            mStreamingThread.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mStreamingThread.interrupt();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the application context
        mContext = getApplicationContext();

        // Get the instance of SharedPreferences object
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);

        setupRs();
        setupUi();

    }

    private void setupRs() {
        RsContext.init(getApplicationContext());

        //Register to notifications regarding RealSense devices attach/detach events via the DeviceListener.
        mRsContext = new RsContext();
        mRsContext.setDevicesChangedCallback(new DeviceListener() {
            @Override
            public void onDeviceAttach() {
                mStreamingThread.start();
            }

            @Override
            public void onDeviceDetach() {
                mStreamingThread.interrupt();
            }
        });

        // Android 9 also requires camera permissions
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.O &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
        }
        mColorize = new Colorizer();

        mCalibrationTablesHandler = new CalibrationTablesHandler();
    }

    private void setupUi() {
        mGLSurfaceView = findViewById(R.id.glSurfaceView);

        Button button = (findViewById(R.id.write_table_button));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCalibrationTablesHandler.setTable(true);
            }
        });

        button = findViewById(R.id.help_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHelpDialog(v);
            }
        });

        button = findViewById(R.id.original_calibrated_toggle);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCalibrationTablesHandler.originalCalibratedToggle(v);
            }
        });

        TextView calibrationButton = findViewById(R.id.calibration_button);
        SharedPreferences sharedPrefCalib = getSharedPreferences("calibration_settings", Context.MODE_PRIVATE);
        mCalibrationProcessor = new CalibrationProcessor(this, sharedPrefCalib);
        calibrationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCalibrationProcessor.showCalibrationDialog(v);
            }
        });

        TextView tareButton = findViewById(R.id.tare_button);
        SharedPreferences sharedPrefTare = getSharedPreferences("tare_settings", Context.MODE_PRIVATE);
        mTareProcessor = new TareProcessor(this, sharedPrefTare);
        tareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTareProcessor.showTareDialog();
            }
        });
    }

    public void showHelpDialog(View view) {
        CalibrationHelpDialog helpDialog = new CalibrationHelpDialog();
        helpDialog.show(getSupportFragmentManager(), TAG);
    }

    private Thread mStreamingThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                stream();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    });

    //Start streaming and print the distance of the center pixel in the depth frame.
    private void stream() throws Exception {
        Pipeline pipe = new Pipeline();
        Config cfg = new Config();
        cfg.enableStream(StreamType.DEPTH, 256, 144, StreamFormat.Z16);
        mProfile = pipe.start(cfg);
        setProjectorState(false);
        mPipeline = pipe;

        // init CalibrationTablesHandler and TareProcessor once the pipeline is ready
        mCalibrationTablesHandler.init(mPipeline);
        mTareProcessor.init(mPipeline, mCalibrationTablesHandler);
        mCalibrationProcessor.init(mPipeline, mCalibrationTablesHandler);

        final DecimalFormat df = new DecimalFormat("#.##");
        mGLSurfaceView.clear();

        while (!mStreamingThread.isInterrupted()) {
            try (FrameReleaser fr = new FrameReleaser()) {
                FrameSet frames = mPipeline.waitForFrames(15000).releaseWith(fr);
                FrameSet orgSet = frames.applyFilter(mColorize).releaseWith(fr);
                Frame org = orgSet.first(StreamType.DEPTH, StreamFormat.RGB8).releaseWith(fr);
                mGLSurfaceView.upload(org);
                try (Frame f = frames.first(StreamType.DEPTH)) {
                    DepthFrame depth = f.as(Extension.DEPTH_FRAME);

                    final float deptValue = depth.getDistance(depth.getWidth() / 2, depth.getHeight() / 2);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView textView = findViewById(R.id.distanceTextView);
                            textView.setText("Distance: " + df.format(deptValue));
                        }
                    });
                }
            }
        }
        pipe.stop();
    }

    public void projectorToggle(View view) {
        ToggleButton b=(ToggleButton)view;
        setProjectorState(b.isChecked());

    }

    private void setProjectorState(boolean projectorOn) {
        if(mProfile !=null) {
            Sensor sensor = mProfile.getDevice().querySensors().get(0);
            float value = projectorOn ? sensor.getMaxRange(Option.LASER_POWER) : 0.0f;
            sensor.setValue(Option.LASER_POWER, value);
        }
    }

}
