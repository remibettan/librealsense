package com.intel.realsense.auto_calibration;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.intel.realsense.librealsense.Pipeline;
import com.intel.realsense.librealsense.ProgressListener;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;

public class CalibrationProcessor {

    private static final String TAG = "librs camera calibration on chip calibration";

    SharedPreferences mSharePreferences;
    AppCompatActivity mMainActivity;

    private View mCalibrationDialogView;

    private ProgressListener mCalibrationListener;
    ProgressBar mCalibrationProgressBar;
    private int mCalibrationProgress = 0;

    private Pipeline mPipeline;
    private CalibrationTablesHandler mCalibrationTablesHandler;

    private int mCalibrationSpeedValue = 2; //medium speed as default

    CalibrationProcessor(AppCompatActivity mainActivity, SharedPreferences sharedPref) {
        mMainActivity = mainActivity;
        mSharePreferences = sharedPref;
        mPipeline = null;
        mCalibrationTablesHandler = null;
    }
    public void init(Pipeline pipeline, CalibrationTablesHandler calibrationTablesHandler) {
        mPipeline = pipeline;
        mCalibrationTablesHandler = calibrationTablesHandler;
    }

    public void showCalibrationDialog(View view)
    {
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(mMainActivity, android.R.layout.simple_spinner_item,
                mMainActivity.getResources().getStringArray(R.array.calibration_speed_entries));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        View dialogView = mMainActivity.getLayoutInflater().inflate(R.layout.calibration_dialog, null);
        final Spinner speedSpinner = dialogView.findViewById(R.id.calib_speed_spinner);
        speedSpinner.setAdapter(adapter);

        mCalibrationProgressBar = dialogView.findViewById(R.id.calibration_progress_bar);

        AlertDialog.Builder builder = new AlertDialog.Builder(mMainActivity);
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
                            Toast.makeText(mMainActivity, formattedString, Toast.LENGTH_LONG).show();
                        }

                        //calibration
                        calibrateCamera(new ProgressListener() {
                            @Override
                            public void onProgress(final float progress) {
                                Log.d("remi", "onProgress - CALIBRATION progress = " + progress);
                                mMainActivity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.d("remi", "run - CALIBRATION progress = " + progress);
                                        TextView progressBarText = mCalibrationDialogView.findViewById(R.id.calibration_progress_bar_text);
                                        progressBarText.setVisibility(View.VISIBLE);
                                        mCalibrationProgressBar.setVisibility(View.VISIBLE);
                                        mCalibrationProgress = (int) (progress);
                                        mCalibrationProgressBar.setProgress(mCalibrationProgress);
                                    }
                                });
                            }
                        });
                        dialog.dismiss();
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
        mCalibrationDialogView = dialogView;
    }

    public synchronized void calibrateCamera(ProgressListener progressListener) {
        try {
            mCalibrationListener = progressListener;
            String jsonString = getSelfCalibrationJson();
            float health = nRunSelfCal(mPipeline.getHandle(), mCalibrationTablesHandler.getCalibrationTableNew(), jsonString);
            health = Math.abs(health);
            health *= 100;
            String formattedString = String.format("Calibration completed with health: %.02f", health);
            Toast.makeText(mMainActivity, formattedString, Toast.LENGTH_LONG).show();
            mCalibrationTablesHandler.setTable(false);
        } catch (RuntimeException e) {
            handleException(e);
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

    private void handleException(RuntimeException e) {
        String message = "Operation failed: " + e.getMessage();
        Toast.makeText(mMainActivity, message, Toast.LENGTH_LONG).show();
    }

    void calibrationOnProgress(float progress){
        mCalibrationListener.onProgress(progress);
    }

    private native float nRunSelfCal(long pipeline_handle, ByteBuffer new_table, String json_cont);

}
