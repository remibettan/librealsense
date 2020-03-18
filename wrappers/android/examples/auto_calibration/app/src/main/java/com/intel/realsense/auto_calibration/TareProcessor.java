package com.intel.realsense.auto_calibration;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.intel.realsense.librealsense.Pipeline;
import com.intel.realsense.librealsense.ProgressListener;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.HashMap;

public class TareProcessor {

    private static final String TAG = "librs camera calibration tare";

    SharedPreferences mSharePreferences;
    AppCompatActivity mMainActivity;

    private int mTareDistance = 0; //0mm as default

    ProgressBar mTareProgressBar;
    private ProgressListener mTareListener;
    private int mTareProgress = 0;
    private Pipeline mPipeline;
    private CalibrationTablesHandler mCalibrationTablesHandler;


    TareProcessor(AppCompatActivity mainActivity, SharedPreferences sharedPref) {
        mMainActivity = mainActivity;
        mSharePreferences = sharedPref;
        mPipeline = null;
        mCalibrationTablesHandler = null;
    }
    public void init(Pipeline pipeline, CalibrationTablesHandler calibrationTablesHandler) {
        mPipeline = pipeline;
        mCalibrationTablesHandler = calibrationTablesHandler;
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
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(mMainActivity, android.R.layout.simple_spinner_item,
                mMainActivity.getResources().getStringArray(R.array.accuracy_entries));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        final Spinner accuracySpinner = dialogView.findViewById(R.id.tare_accuracy_spinner);
        accuracySpinner.setAdapter(adapter);

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

    public void showTareDialog(View view) {
        View dialogView = mMainActivity.getLayoutInflater().inflate(R.layout.tare_dialog, null);

        //setting advanced preferences
        setTareAverageStepCountValue(dialogView);
        setTareStepCountValue(dialogView);
        setTareAccuracy(dialogView);

        final EditText distanceText = dialogView.findViewById(R.id.tare_distance_text);

        mTareProgressBar = dialogView.findViewById(R.id.tare_progress_bar);

        AlertDialog.Builder builder = new AlertDialog.Builder(mMainActivity);
        builder.setTitle("Tare Depth Distance")
                .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            int distance = Integer.parseInt(distanceText.getText().toString());
                            setTareDistance(distance);
                            //tare action
                            tareCamera(dialog);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Distance not entered");
                            String formattedString = String.format("Enter distance to flat surface");
                            Toast.makeText(mMainActivity, formattedString, Toast.LENGTH_LONG).show();
                        }
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

    private void setTareDistance(int distance) {
        mTareDistance = distance;
    }

    String getTareJson() {
        HashMap<String, Object> settingMap = new HashMap<>();
        settingMap.put("average step count", mSharePreferences.getInt("tare_avg_step_count", -1));
        settingMap.put("step count", mSharePreferences.getInt("tare_step_count", -1));
        settingMap.put("accuracy", mSharePreferences.getInt("tare_accuracy", -1));
        settingMap.put("scan parameter", 0);
        settingMap.put("data sampling", 0);
        JSONObject json = new JSONObject(settingMap);
        return json.toString();

    }

    public void tareCamera(DialogInterface dialog) {
        try {
            if (mTareDistance > 0) {

                mTareListener = new ProgressListener() {
                    @Override
                    public void onProgress(float progress) {
                        mTareProgress = (int) (progress * 100);
                        if (mTareProgress < 100) {
                            mMainActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    View dialogView = mMainActivity.getLayoutInflater().inflate(R.layout.tare_dialog, null);
                                    TextView progressBarText = dialogView.findViewById(R.id.tare_progress_bar_text);
                                    progressBarText.setVisibility(View.VISIBLE);
                                    mTareProgressBar.setVisibility(View.VISIBLE);
                                    int percentage = mTareProgress;
                                    mTareProgressBar.setProgress(percentage);
                                }
                            });
                        } else {
                            View dialogView = mMainActivity.getLayoutInflater().inflate(R.layout.tare_dialog, null);
                            ((DialogInterface)dialogView).dismiss();
                        }
                    }
                };
                ByteBuffer calibNew = mCalibrationTablesHandler.getCalibrationTableNew();
                nTare(mPipeline.getHandle(), calibNew, mTareDistance, getTareJson());
                mCalibrationTablesHandler.setTable(false);
            } else {
                String formattedString = String.format("Distance to wall cannot be 0 mm");
                Toast.makeText(mMainActivity, formattedString, Toast.LENGTH_LONG).show();
            }
        } catch (RuntimeException e) {
            handleException(e);
        }
    }

    private void handleException(RuntimeException e) {
        String message = "Operation failed: " + e.getMessage();
        Toast.makeText(mMainActivity, message, Toast.LENGTH_LONG).show();
    }

    void tareOnProgress(float progress){
        mTareListener.onProgress(progress);
    }

    private native void nTare(long pipeline_handle, ByteBuffer table, int ground_truth, String json_cont);
}
