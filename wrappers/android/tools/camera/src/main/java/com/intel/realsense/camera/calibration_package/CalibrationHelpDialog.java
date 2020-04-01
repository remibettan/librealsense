package com.intel.realsense.camera.calibration_package;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatDialogFragment;

public class CalibrationHelpDialog extends AppCompatDialogFragment {

    @NonNull
    @Override
    public AlertDialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Calibration Instructions")
                .setMessage("On Chip Calibration: \n" +
                        "This will improve the depth noise.\n" +
                        "Point at a scene that normally would have > 50 % valid depth pixels,\n" +
                        "then press calibrate. The health-check will be calculated.\n" +
                        "If resulting health factor is greater than 0.25, \n" +
                        "we recommend to apply the new calibration.\n" +
                        "\n" +
                        "Tare calibration: \n" +
                        "It is used to adjust camera absolute distance to flat target.\n" +
                        "User needs to enter the distance to the flat object in mm.\n" +
                        "\n" +
                        "Full documentation for self calibration can be found at:" +
                        "https://dev.intelrealsense.com/docs/self-calibration-for-depth-cameras")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
        return builder.create();
    }
}
