package com.intel.realsense.camera;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
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

import java.nio.ByteBuffer;
import java.util.HashMap;

public class CalibTareProcessor {

    private static final String TAG = "librs camera calib tare";

    SharedPreferences mSharePreferences;
    AppCompatActivity mMainActivity;
    private int mTareDistance = 0; //0mm as default
    private View mTareDialogView;
    private ProgressListener mTareListener;
    ProgressBar mTareProgressBar;
    private AlertDialog mTareAlertDialog;

    CalibTareProcessor(AppCompatActivity mainActivity, SharedPreferences sharedPref) {
        mMainActivity = mainActivity;
        mSharePreferences = sharedPref;
    }

    public void showTareDialog() {
        View dialogView = mMainActivity.getLayoutInflater().inflate(R.layout.tare_dialog, null);

        //setting advanced preferences
        setTareAverageStepCountValue(dialogView);
        setTareStepCountValue(dialogView);
        setTareAccuracy(dialogView);

        final EditText distanceText = dialogView.findViewById(R.id.tare_distance_text);

        mTareProgressBar = dialogView.findViewById(R.id.tare_progress_bar);

        AlertDialog.Builder builder = new AlertDialog.Builder(mMainActivity);
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        mTareAlertDialog = dialog;
        mTareDialogView = dialogView;

        View negativeButton = dialogView.findViewById(R.id.calib_tare_cancel_button);
        negativeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTareAlertDialog.dismiss();
            }
        });
        Button positiveButton = dialogView.findViewById(R.id.calib_tare_ok_button);
        positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int distance = Integer.parseInt(distanceText.getText().toString());
                setTareDistance(distance);

                //tare operation
                Thread t = new Thread(mTareCamera);
                t.start();

                //update progress
                mMainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //make progress bar visible
                        TextView progressBarText = mTareDialogView.findViewById(R.id.tare_progress_bar_text);
                        progressBarText.setVisibility(View.VISIBLE);
                        mTareProgressBar.setVisibility(View.VISIBLE);
                        int progress = 10;
                        mTareProgressBar.setProgress(progress);
                        //update progress
                        setProgressValue(progress);
                    }
                });
            }
        });
    }

    private Runnable mTareCamera = new Runnable() {
        @Override
        public void run() {
            tareCamera();
        }
    };

    private void setProgressValue(final int progress)
    {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mTareProgressBar.setProgress(progress);
        if (progress + 10 > 100)
            return;
        setProgressValue(progress + 10);
    }

    private void showTareResultDialog(String resultString)
    {
        CalibrationResultDialog dialog = new CalibrationResultDialog(mMainActivity, "Tare Result", resultString);
    }

    private void tareCamera() {
        try {
            if (mTareDistance > 0) {
                Log.i(TAG, "Tare process started");
                RsContext ctx = new RsContext();
                try(DeviceList devices = ctx.queryDevices()) {
                    try (Device device = devices.createDevice(0)) {
                        AutoCalibDevice autoCalibDevice = device.as(Extension.AUTO_CALIBRATED_DEVICE);
                        autoCalibDevice.runTare(mTareDistance, getTareJson());
                    }
                }
                mTareAlertDialog.dismiss();
                Log.i(TAG, "Tare process succesfully finished");
                showTareResultDialog("Tare process succesfully finished");
            } else {
                String formattedString = String.format("Distance to wall cannot be 0 mm");
                Toast.makeText(mMainActivity, formattedString, Toast.LENGTH_LONG).show();
            }
        } catch (RuntimeException e) {
            mTareAlertDialog.dismiss();
            Log.i(TAG, "Tare process failed");
            showTareResultDialog("Tare process failed with error: "+ e.getMessage());
        }
    }

    private void setTareAverageStepCountValue(View dialogView) {
        final TextView avgStepCountValueString = dialogView.findViewById(R.id.tare_avg_step_count_current_value);
        final SeekBar avgStepCountSeekBar = dialogView.findViewById(R.id.tare_avg_step_count_seekBar);
        avgStepCountSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                avgStepCountValueString.setText(Integer.toString(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //saving in shared preferences
                SharedPreferences.Editor editor = mSharePreferences.edit();
                editor.putInt("tare_avg_step_count", seekBar.getProgress());
                editor.commit();
            }
        });
    }

    private void setTareStepCountValue(View dialogView) {
        final TextView stepCountValueString = dialogView.findViewById(R.id.tare_step_count_current_value);
        final SeekBar stepCountSeekBar = dialogView.findViewById(R.id.tare_step_count_seekBar);
        stepCountSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                stepCountValueString.setText(Integer.toString(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //saving in shared preferences
                SharedPreferences.Editor editor = mSharePreferences.edit();
                editor.putInt("tare_step_count", seekBar.getProgress());
                editor.commit();
            }
        });
    }

    private void setTareAccuracy(View dialogView) {
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(mMainActivity, R.layout.spinner_item,
                mMainActivity.getResources().getStringArray(R.array.accuracy_entries));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        final Spinner accuracySpinner = dialogView.findViewById(R.id.tare_accuracy_spinner);
        accuracySpinner.setAdapter(adapter);
        accuracySpinner.setSelection(2); //Medium accuracy as default

        accuracySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                //getting value from input position
                String[] accuracyValues = mMainActivity.getResources().getStringArray(R.array.accuracy_values);
                int accuracyValue = Integer.parseInt(accuracyValues[position]);
                //saving in shared preferences
                SharedPreferences.Editor editor = mSharePreferences.edit();
                editor.putInt("tare_accuracy", accuracyValue);
                editor.commit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void setTareDistance(int distance) {
        mTareDistance = distance;
    }

    String getTareJson() {
        HashMap<String, Object> settingMap = new HashMap<>();
        settingMap.put("average step count", mSharePreferences.getInt("tare_avg_step_count", 20));
        settingMap.put("step count", mSharePreferences.getInt("tare_step_count", 20));
        settingMap.put("accuracy", mSharePreferences.getInt("tare_accuracy", 2));
        settingMap.put("scan parameter", 0);
        settingMap.put("data sampling", 0);
        JSONObject json = new JSONObject(settingMap);
        return json.toString();

    }

    private void handleException(RuntimeException e) {
        String message = "Operation failed: " + e.getMessage();
        Toast.makeText(mMainActivity, message, Toast.LENGTH_LONG).show();
    }

    void tareOnProgress(float progress){
        mTareListener.onProgress(progress);
    }
}
