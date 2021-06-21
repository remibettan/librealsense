package com.intel.realsense.start_stop_test;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.intel.realsense.librealsense.Colorizer;
import com.intel.realsense.librealsense.Config;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.DeviceListener;
import com.intel.realsense.librealsense.FrameSet;
import com.intel.realsense.librealsense.GLRsSurfaceView;
import com.intel.realsense.librealsense.Pipeline;
import com.intel.realsense.librealsense.PipelineProfile;
import com.intel.realsense.librealsense.RsContext;
import com.intel.realsense.librealsense.StreamFormat;
import com.intel.realsense.librealsense.StreamType;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "librs_start_stop";
    private static final int PERMISSIONS_REQUEST_CAMERA = 0;
    private static final int PERMISSIONS_REQUEST_READ = 1;
    private static final int PERMISSIONS_REQUEST_WRITE = 2;
    private static final int PERMISSIONS_REQUEST_ALL = 3;

    private boolean mPermissionsGranted = false;

    private Context mAppContext;
    private TextView mBackGroundText;
    private GLRsSurfaceView mGLSurfaceView;
    private boolean mIsStreaming = false;
    private final Handler mHandler = new Handler();

    private Pipeline mPipeline;
    private Colorizer mColorizer;
    private RsContext mRsContext;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAppContext = getApplicationContext();
        mBackGroundText = findViewById(R.id.connectCameraText);
        mGLSurfaceView = findViewById(R.id.glSurfaceView);
        mGLSurfaceView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        ArrayList<String> permissions = new ArrayList<>();
        if (!isCameraPermissionGranted()) {
            permissions.add(Manifest.permission.CAMERA);
        }

        if (!isWritePermissionGranted()) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!permissions.isEmpty())
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[permissions.size()]), PERMISSIONS_REQUEST_ALL);

        mPermissionsGranted = true;
    }

    private boolean isCameraPermissionGranted() {
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.O)
            return true;
        return  ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isWritePermissionGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGLSurfaceView.close();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
            return;
        }
        mPermissionsGranted = true;
    }

    private FwLogsThread mFwLogsThread;

    @Override
    protected void onResume() {
        super.onResume();
        if(mPermissionsGranted)
            init();
        else
            Log.e(TAG, "missing permissions");

        mFwLogsThread = new FwLogsThread();
        mFwLogsThread.init("/sdcard/realsense/hw/HWLoggerEventsDS5.xml");
        mFwLogsThread.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mRsContext != null)
            mRsContext.close();
        stop();
        mColorizer.close();
        mPipeline.close();
        mFwLogsThread.stopLogging();
    }

    private void init(){
        //RsContext.init must be called once in the application lifetime before any interaction with physical RealSense devices.
        //For multi activities applications use the application context instead of the activity context
        RsContext.init(mAppContext);

        //Register to notifications regarding RealSense devices attach/detach events via the DeviceListener.
        mRsContext = new RsContext();
        mRsContext.setDevicesChangedCallback(mListener);

        mPipeline = new Pipeline();
        mColorizer = new Colorizer();

        try(DeviceList dl = mRsContext.queryDevices()){
            if(dl.getDeviceCount() > 0) {
                showConnectLabel(false);
                start();
            }
        }
        /*while (start_stop_iterations < 10) {
            Log.i(TAG, "start called for iteration: " + start_stop_iterations);
            start();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Log.i(TAG, "stop called for iteration: " + start_stop_iterations);
            stop();
            start_stop_iterations++;
        }*/
    }

    private void showConnectLabel(final boolean state){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mBackGroundText.setVisibility(state ? View.VISIBLE : View.GONE);
            }
        });
    }

    private DeviceListener mListener = new DeviceListener() {
        @Override
        public void onDeviceAttach() {
            showConnectLabel(false);
        }

        @Override
        public void onDeviceDetach() {
            showConnectLabel(true);
            stop();
        }
    };

    int start_stop_iterations = 0;
    Long start_time = Long.valueOf(0);
    int iterations = 0;
    Runnable mStreaming = new Runnable() {
        @Override
        public void run() {
            try {
                try(FrameSet frames = mPipeline.waitForFrames()) {
                    try(FrameSet processed = frames.applyFilter(mColorizer)) {
                        mGLSurfaceView.upload(processed);
                        Long now_time = System.currentTimeMillis();
                        if (now_time - start_time > 30000)
                        {
                            int num_of_required_framesets = fps * 30;
                            Log.i(TAG, "30 seconds streaming done  - " + iterations + " of framesets received from " + num_of_required_framesets);
                            iterations = 0;
                            stop();

                        }
                        else if (iterations % 100 == 0){
                            Log.i(TAG, "frame " + (iterations) + " received");
                        }
                        ++iterations;
                    }
                }
                mHandler.post(mStreaming);
            }
            catch (Exception e) {
                Log.e(TAG, "streaming, error: " + e.getMessage());
            }
        }
    };

    public int fps = 30;
    private void configAndStart() throws Exception {
        try(Config config  = new Config())
        {
            int res = start_stop_iterations % 24;

            if ( res == 0){
                fps = 5;
                config.enableStream(StreamType.DEPTH, -1, 424, 240, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 424, 240, StreamFormat.Y8, fps);
            }
            else if (res == 1){
                fps = 15;
                config.enableStream(StreamType.DEPTH, -1, 424, 240, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 424, 240, StreamFormat.Y8, fps);
            }
            else if (res == 2){
                fps = 30;
                config.enableStream(StreamType.DEPTH, -1, 424, 240, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 424, 240, StreamFormat.Y8, fps);
            }
            else if (res == 3){
                fps = 60;
                config.enableStream(StreamType.DEPTH, -1, 424, 240, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 424, 240, StreamFormat.Y8, fps);
            }
            else if (res == 4){
                fps = 5;
                config.enableStream(StreamType.DEPTH, -1, 480, 270, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 480, 270, StreamFormat.Y8, fps);
            }
            else if (res == 5){
                fps = 15;
                config.enableStream(StreamType.DEPTH, -1, 480, 270, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 480, 270, StreamFormat.Y8, fps);
            }
            else if (res == 6){
                fps = 30;
                config.enableStream(StreamType.DEPTH, -1, 480, 270, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 480, 270, StreamFormat.Y8, fps);
            }
            else if (res == 7){
                fps = 60;
                config.enableStream(StreamType.DEPTH, -1, 480, 270, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 480, 270, StreamFormat.Y8, fps);
            }
            else if (res == 8){
                fps = 5;
                config.enableStream(StreamType.DEPTH, -1, 640, 360, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 640, 360, StreamFormat.Y8, fps);
            }
            else if (res == 9){
                fps = 15;
                config.enableStream(StreamType.DEPTH, -1, 640, 360, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 640, 360, StreamFormat.Y8, fps);
            }
            else if (res == 10){
                fps = 30;
                config.enableStream(StreamType.DEPTH, -1, 640, 360, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 640, 360, StreamFormat.Y8, fps);
            }
            else if (res == 11){
                fps = 60;
                config.enableStream(StreamType.DEPTH, -1, 640, 360, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 640, 360, StreamFormat.Y8, fps);
            }
            else if (res == 12){
                fps = 5;
                config.enableStream(StreamType.DEPTH, -1, 640, 480, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 640, 480, StreamFormat.Y8, fps);
            }
            else if (res == 13){
                fps = 15;
                config.enableStream(StreamType.DEPTH, -1, 640, 480, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 640, 480, StreamFormat.Y8, fps);
            }
            else if (res == 14){
                fps = 30;
                config.enableStream(StreamType.DEPTH, -1, 640, 480, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 640, 480, StreamFormat.Y8, fps);
            }
            else if (res == 15){
                fps = 60;
                config.enableStream(StreamType.DEPTH, -1, 640, 480, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 640, 480, StreamFormat.Y8, fps);
            }
            else if (res == 16){
                fps = 5;
                config.enableStream(StreamType.DEPTH, -1, 848, 480, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 848, 480, StreamFormat.Y8, fps);
            }
            else if (res == 17){
                fps = 15;
                config.enableStream(StreamType.DEPTH, -1, 848, 480, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 848, 480, StreamFormat.Y8, fps);
            }
            else if (res == 18){
                fps = 30;
                config.enableStream(StreamType.DEPTH, -1, 848, 480, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 848, 480, StreamFormat.Y8, fps);
            }
            else if (res == 19){
                fps = 60;
                config.enableStream(StreamType.DEPTH, -1, 848, 480, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 848, 480, StreamFormat.Y8, fps);
            }
            else if (res == 20){
                fps = 5;
                config.enableStream(StreamType.DEPTH, -1, 1280, 720, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 1280, 720, StreamFormat.Y8, fps);
            }
            else if (res == 21){
                fps = 15;
                config.enableStream(StreamType.DEPTH, -1, 1280, 720, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 1280, 720, StreamFormat.Y8, fps);
            }
            else if (res == 22){
                fps = 30;
                config.enableStream(StreamType.DEPTH, -1, 1280, 720, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 1280, 720, StreamFormat.Y8, fps);
            }
            else if (res == 23){
                fps = 60;
                config.enableStream(StreamType.DEPTH, -1, 1280, 720, StreamFormat.Z16, fps);
                config.enableStream(StreamType.INFRARED, -1, 1280, 720, StreamFormat.Y8, fps);
            }

            // try statement needed here to release resources allocated by the Pipeline:start() method
            try(PipelineProfile pp = mPipeline.start(config)){}

            start_time = System.currentTimeMillis();
            Log.i(TAG, "starting iteration " + start_stop_iterations);
        }
    }

    private synchronized void start() {
        if(mIsStreaming)
            return;
        try{
            Log.d(TAG, "try start streaming");
            mGLSurfaceView.clear();
            configAndStart();
            mIsStreaming = true;
            mHandler.post(mStreaming);
            Log.d(TAG, "streaming started successfully");
        } catch (Exception e) {
            Log.d(TAG, "failed to start streaming");
        }
    }

    private synchronized void stop() {
        if(!mIsStreaming)
            return;
        try {
            Log.d(TAG, "try stop streaming");
            mIsStreaming = false;
            mHandler.removeCallbacks(mStreaming);
            mPipeline.stop();
            mGLSurfaceView.clear();
            Log.d(TAG, "streaming stopped successfully");
        } catch (Exception e) {
            Log.d(TAG, "failed to stop streaming");
        }
        Log.i(TAG, "stopped for iteration: " + start_stop_iterations);
        ++start_stop_iterations;
        if (start_stop_iterations < 200)
            start();
    }
}
