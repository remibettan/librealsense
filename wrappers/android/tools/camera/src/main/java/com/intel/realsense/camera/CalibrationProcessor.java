package com.intel.realsense.camera;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.intel.realsense.librealsense.AutoCalibDevice;
import com.intel.realsense.librealsense.Device;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.ProgressListener;
import com.intel.realsense.librealsense.RsContext;

import org.json.JSONObject;

import java.beans.PropertyChangeEvent;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;

public class CalibrationProcessor {

    private static final String TAG = "librs camera calib cal";

    SharedPreferences mSharePreferences;
    AppCompatActivity mMainActivity;
    private View mCalibrationDialogView;
    private ProgressListener mCalibrationListener;
    ProgressBar mCalibrationProgressBar;
    private int mCalibrationProgress = 0;
    private int mCalibrationSpeedValue = 2; //medium speed as default
    private float mCalibrationHealth;

    private ObservableValue<CalibrationActivity.CalibrationResult> mCalibrationResult = new ObservableValue();
    private RsContext mRsContext;
    private Device mDevice;

    private AlertDialog mCalibrationAlertDialog;

    private ByteBuffer mCalibrationTableOriginal;
    private ByteBuffer mCalibrationTableNew;

    CalibrationProcessor(final AppCompatActivity mainActivity, SharedPreferences sharedPref) {
        mMainActivity = mainActivity;
        mSharePreferences = sharedPref;

        mCalibrationResult.setOnValueChangeListener(new OnValueChangeListener<CalibrationActivity.CalibrationResult>() {
            @Override
            public void onValueChanged(CalibrationActivity.CalibrationResult value) {
                mMainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        switch(mCalibrationResult.get()) {
                            case CALIB_RESULT_IN_PROCESS:
                                return;
                            case CALIB_RESULT_FAILURE:
                                return;
                            case CALIB_RESULT_SUCCESS:
                                String title = "On Chip Calibration Completed with health = " + mCalibrationHealth;
                                PostCalibrationDialog dialog = new PostCalibrationDialog(mMainActivity, title, mCalibrationTableNew, mCalibrationTableOriginal);
                        }
                    }
                });
            }
        });

        mCalibrationTableNew = ByteBuffer.allocateDirect(512);
        mCalibrationTableOriginal = ByteBuffer.allocateDirect(512);
    }


    public void showCalibrationDialog(View view)
    {
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(mMainActivity, R.layout.spinner_item,
                mMainActivity.getResources().getStringArray(R.array.calibration_speed_entries));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        View dialogView = mMainActivity.getLayoutInflater().inflate(R.layout.calibration_dialog, null);
        final Spinner speedSpinner = dialogView.findViewById(R.id.calib_speed_spinner);
        speedSpinner.setAdapter(adapter);

        mCalibrationProgressBar = dialogView.findViewById(R.id.calibration_progress_bar);

        AlertDialog.Builder builder = new AlertDialog.Builder(mMainActivity);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        mCalibrationAlertDialog = dialog;
        mCalibrationDialogView = dialogView;
        Button positiveButton = dialogView.findViewById(R.id.calib_ok_button);
        positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!speedSpinner.getSelectedItem().toString().equalsIgnoreCase("Choose calibration speed")){
                    //setting calibration speed
                    int selectedEntryPosition = adapter.getPosition(speedSpinner.getSelectedItem().toString());
                    setCalibrationSpeed(selectedEntryPosition);
                } else {
                    String currentSpeed = getCalibrationSpeedString();
                    String str = "Speed not changed - previous set speed is: " + currentSpeed;
                    String formattedString = String.format(str);
                    Toast.makeText(mMainActivity, formattedString, Toast.LENGTH_LONG).show();
                }
                //calibration
                Thread t = new Thread(mCalibrateCamera);
                t.start();
                //resetting calibration result
                mCalibrationResult.set(CalibrationActivity.CalibrationResult.CALIB_RESULT_IN_PROCESS);
            }
        });

        View negativeButton = dialogView.findViewById(R.id.calib_cancel_button);
        negativeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCalibrationAlertDialog.dismiss();
            }
        });
    }


    private Runnable mCalibrateCamera = new Runnable() {
        @Override
        public void run() {
            calibrateCamera(new ProgressListener() {
                @Override
                public void onProgress(final float progress) {
                    mMainActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView progressBarText = mCalibrationDialogView.findViewById(R.id.calibration_progress_bar_text);
                            progressBarText.setVisibility(View.VISIBLE);
                            mCalibrationProgressBar.setVisibility(View.VISIBLE);
                            mCalibrationProgress = (int) (progress);
                            mCalibrationProgressBar.setProgress(mCalibrationProgress);
                        }
                    });
                }
            });
        }
    };


    public synchronized void calibrateCamera(ProgressListener progressListener) {
        try {
            //calibration operation
            mCalibrationListener = progressListener;
            String jsonString = getSelfCalibrationJson();
            mRsContext = new RsContext();
            try(DeviceList devices = mRsContext.queryDevices()) {
                mDevice = devices.createDevice(0);
                AutoCalibDevice autoCalibDevice = mDevice.as(Extension.AUTO_CALIBRATED_DEVICE);
                mCalibrationTableNew = autoCalibDevice.getTable();
                mCalibrationTableOriginal = autoCalibDevice.getTable();
                float health = autoCalibDevice.runAutoCalib(jsonString);
                health = Math.abs(health);
                health *= 100;
                mCalibrationHealth = health;
                mCalibrationTableNew = autoCalibDevice.getTable();
            }

            //sending calibration result changed event
            PropertyChangeEvent event = new PropertyChangeEvent(mCalibrationResult, "result",
                    CalibrationActivity.CalibrationResult.CALIB_RESULT_IN_PROCESS, CalibrationActivity.CalibrationResult.CALIB_RESULT_SUCCESS);
            mCalibrationResult.set(CalibrationActivity.CalibrationResult.CALIB_RESULT_SUCCESS);
            Log.i(TAG, "Calibration process succesfully finished");
            //closing dialog
            mCalibrationAlertDialog.dismiss();

        } catch (RuntimeException e) {
            //sending calibration result changed event
            PropertyChangeEvent event = new PropertyChangeEvent(mCalibrationResult, "result",
                    CalibrationActivity.CalibrationResult.CALIB_RESULT_IN_PROCESS, CalibrationActivity.CalibrationResult.CALIB_RESULT_FAILURE);
            mCalibrationResult.set(CalibrationActivity.CalibrationResult.CALIB_RESULT_FAILURE);
            Log.i(TAG, "Calibration process failed");
            //closing dialog
            mCalibrationAlertDialog.dismiss();
        }
    }

    public void setCalibrationSpeed(int selectedEntryPosition) {
        String[] speedValues = mMainActivity.getResources().getStringArray(R.array.calibration_speed_values);
        mCalibrationSpeedValue = Integer.parseInt(speedValues[selectedEntryPosition]);

        //saving in shared preferences
        SharedPreferences.Editor editor = mSharePreferences.edit();
        editor.putInt("calibration_speed", mCalibrationSpeedValue);
        editor.commit();
    }
    private String getCalibrationSpeedString() {
        String[] speedValues = mMainActivity.getResources().getStringArray(R.array.calibration_speed_values);
        String currentSpeedValueString = Integer.toString(mCalibrationSpeedValue);
        int speedValuePosition = Arrays.asList(speedValues).indexOf(currentSpeedValueString);

        String[] speedEntries = mMainActivity.getResources().getStringArray(R.array.calibration_speed_entries);
        String currentSpeed = speedEntries[speedValuePosition];

        return currentSpeed;
    }

    private String getSelfCalibrationJson() {
        HashMap<String, Object> settingMap = new HashMap<>();
        settingMap.put("speed", mCalibrationSpeedValue);
        settingMap.put("scan parameter", 0);
        settingMap.put("data sampling", 0);
        JSONObject json = new JSONObject(settingMap);
        return json.toString();
    }

    void calibrationOnProgress(float progress){
        mCalibrationListener.onProgress(progress);
    }
}
