package com.intel.realsense.camera;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.intel.realsense.librealsense.AutoCalibDevice;
import com.intel.realsense.librealsense.Device;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.RsContext;

import java.nio.ByteBuffer;

public class PostCalibrationDialog {

    private AlertDialog mPostCalibrationDialog;

    PostCalibrationDialog(final AppCompatActivity activity, final String title, final ByteBuffer newTable, final ByteBuffer origTable){
        View postCalibView = activity.getLayoutInflater().inflate(R.layout.post_calibration_dialog, null);

        TextView calibTaskCompleted = postCalibView.findViewById(R.id.post_calib_task_completed);
        calibTaskCompleted.setText(title); //title

        RadioGroup tablesGroup = postCalibView.findViewById(R.id.post_calib_radio);
        tablesGroup.check(R.id.post_calib_new_button);

        final Device device;
        RsContext ctx = new RsContext();
        try(DeviceList devices = ctx.queryDevices()) {
            device = devices.createDevice(0);
        }

        tablesGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                AutoCalibDevice autoCalibDevice = device.as(Extension.AUTO_CALIBRATED_DEVICE);
                autoCalibDevice.setTable((checkedId == 0) ? newTable : origTable);
            }
        });

        Button applyButton = postCalibView.findViewById(R.id.post_calib_apply_button);
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AutoCalibDevice autoCalibDevice = device.as(Extension.AUTO_CALIBRATED_DEVICE);
                autoCalibDevice.setTable(newTable);
                autoCalibDevice.writeToCamera();
                mPostCalibrationDialog.dismiss();
            }
        });

        Button dismissButton = postCalibView.findViewById(R.id.post_calib_dismiss_button);
        dismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPostCalibrationDialog.dismiss();
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(postCalibView);
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        mPostCalibrationDialog = dialog;
    }
}