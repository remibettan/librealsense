package com.intel.realsense.camera.calibration_package;

import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.intel.realsense.camera.R;

public class CalibrationResultDialog  {

    CalibrationResultDialog(final AppCompatActivity activity, final String title, final String result){
        final View calibrationResultView = activity.getLayoutInflater().inflate(R.layout.calibraton_result_dialog, null);
        TextView msg = calibrationResultView.findViewById(R.id.calibration_result_text);
        msg.setText(result);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setTitle(title)
                        .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .setView(calibrationResultView);
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }
}
