package com.intel.realsense.auto_calibration;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
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

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;

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
    private boolean bShowCalibrated = false;
    private ByteBuffer mCalibrationTableNew;
    private ByteBuffer mCalibrationTableOriginal;
    private SharedPreferences mSharedPreferences;
    private Context mContext;
    private MainActivity mActivity;
    private PipelineProfile mProfile;

    private int mCalibrationSpeedValue = 2; //medium speed as default

    private void getPrefInt(HashMap<String, Object> settingMap, String key,boolean fromString) {
        int pref_value;
        if(fromString){
            String str=mSharedPreferences.getString(key,"-1");
            pref_value = Integer.parseInt(str);
        } else {
            pref_value=mSharedPreferences.getInt(key,-1);
        }
        settingMap.put(key,pref_value);
    }

    String getSelfCalibrationJson() {
        HashMap<String, Object> settingMap = new HashMap<>();
        settingMap.put("speed", mCalibrationSpeedValue);
        settingMap.put("scan parameter", 0);
        settingMap.put("data sampling", 0);
        JSONObject json = new JSONObject(settingMap);
        return json.toString();
    }


    String getTareJson() {
        HashMap<String, Object> settingMap = new HashMap<>();
        getPrefInt(settingMap,"average step count",false);
        getPrefInt(settingMap,"step count",false);
        getPrefInt(settingMap,"accuracy",true);
        settingMap.put("scan parameter", 0);
        settingMap.put("data sampling", 0);
        JSONObject json = new JSONObject(settingMap);
        return json.toString();

    }

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

        // Get the activity
        mActivity = MainActivity.this;

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
        mCalibrationTableNew = ByteBuffer.allocateDirect(512);
        mCalibrationTableOriginal = ByteBuffer.allocateDirect(512);
    }

    private void setupUi() {
        mGLSurfaceView = findViewById(R.id.glSurfaceView);

        Button button = (findViewById(R.id.write_table_button));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setTable(true);
            }
        });

        button = (findViewById(R.id.settings_button));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, SettingsActivity.class);
                startActivity(intent);
            }
        });

        button = findViewById(R.id.help_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHelpDialog(v);
            }
        });

        button = findViewById(R.id.calibration_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOnChipCalibrationDialog(v);
            }
        });
    }

    public void tareCamera(View view) {
        try {
            EditText editText = (EditText) (findViewById(R.id.ground_truth_text));
            int ground_truth = Integer.parseInt(editText.getText().toString());
            if (ground_truth > 0) {
                nTare(mPipeline.getHandle(), mCalibrationTableNew, ground_truth, getTareJson());
                setTable(false);
            } else {
                String formattedString = String.format("Distance to wall cannot be 0 mm");
                Toast.makeText(this, formattedString, Toast.LENGTH_LONG).show();
            }
        } catch (RuntimeException e) {
            handleException(e);
        }
    }

    private String getCalibrationSpeedString() {
        String[] speedValues = getResources().getStringArray(R.array.calibration_speed_values);
        String currentSpeedValueString = Integer.toString(mCalibrationSpeedValue);
        int speedValuePosition = Arrays.asList(speedValues).indexOf(currentSpeedValueString);

        String[] speedEntries = getResources().getStringArray(R.array.calibration_speed_entries);
        String currentSpeed = speedEntries[speedValuePosition];

        return currentSpeed;
    }

    public void showOnChipCalibrationDialog(View view)
    {
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item,
                getResources().getStringArray(R.array.calibration_speed_entries));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        View dialogView = getLayoutInflater().inflate(R.layout.spinner_dialog, null);
        final Spinner speedSpinner = dialogView.findViewById(R.id.calib_speed_spinner);
        speedSpinner.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("On Chip Calibration")
                .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!speedSpinner.getSelectedItem().toString().equalsIgnoreCase("Choose calibration speed")){
                            //setting calibration speed
                            int selectedEntryPosition = adapter.getPosition(speedSpinner.getSelectedItem().toString());
                            setCalibrationSpeed(selectedEntryPosition);
                        } else {
                            String currentSpeed = getCalibrationSpeedString();
                            String str = "Speed not changed - previous set speed is: " + currentSpeed;
                            String formattedString = String.format(str);
                            Toast.makeText(MainActivity.this, formattedString, Toast.LENGTH_LONG).show();
                        }
                        dialog.dismiss();
                        //calibration
                        calibrateCamera();
                    }
                })
                .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setView(dialogView);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void setCalibrationSpeed(int selectedEntryPosition) {
        String[] speedValues = getResources().getStringArray(R.array.calibration_speed_values);
        mCalibrationSpeedValue = Integer.parseInt(speedValues[selectedEntryPosition]);
    }

    public void showHelpDialog(View view)
    {
        CalibrationHelpDialog helpDialog = new CalibrationHelpDialog();
        helpDialog.show(getSupportFragmentManager(), TAG);
    }

    public void calibrateCamera() {
        try {
            String jsonString = getSelfCalibrationJson();
            float health = nRunSelfCal(mPipeline.getHandle(), mCalibrationTableNew, jsonString);
            health = Math.abs(health);
            health *= 100;
            String formattedString = String.format("Calibration completed with health: %.02f", health);
            Toast.makeText(this, formattedString, Toast.LENGTH_LONG).show();
            setTable(false);
        } catch (RuntimeException e) {
            handleException(e);
        }
    }

    private void handleException(RuntimeException e) {
        String message = "Operation failed: " + e.getMessage();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
        nGetTable(mPipeline.getHandle(), mCalibrationTableOriginal);
        nGetTable(mPipeline.getHandle(), mCalibrationTableNew);
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

    static native float nRunSelfCal(long pipeline_handle, ByteBuffer new_table, String json_cont);

    static native void nSetTable(long pipeline_handle, ByteBuffer table, boolean write_table);

    static native void nGetTable(long pipeline_handle, ByteBuffer table);

    static native void nTare(long pipeline_handle, ByteBuffer table, int ground_truth, String json_cont);

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

    public void originalCalibratedToggle(View view) {
        ToggleButton toggleButton=(ToggleButton) view;
        if (toggleButton.isChecked()) {
            bShowCalibrated = true;
        } else {
            bShowCalibrated = false;
        }
        setTable(false);

    }
    private void setTable(boolean write){
        nSetTable(mPipeline.getHandle(), bShowCalibrated ? mCalibrationTableNew : mCalibrationTableOriginal, write);
    }

}
