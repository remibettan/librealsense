package com.intel.realsense.camera;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.intel.realsense.librealsense.Device;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.Option;
import com.intel.realsense.librealsense.RsContext;
import com.intel.realsense.librealsense.Sensor;

import java.util.List;
import java.util.Locale;

/** Bottom sheet listing every writable option of every sensor, as switches and sliders. */
public class ControlsDialog extends BottomSheetDialogFragment {
    private static final String TAG = "librs controls";

    private Device mDevice;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_controls, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LinearLayout list = view.findViewById(R.id.controls_container);

        try (RsContext ctx = new RsContext(); DeviceList devices = ctx.queryDevices()) {
            if (devices.getDeviceCount() > 0)
                mDevice = devices.createDevice(0);
        }
        if (mDevice == null) {
            Toast.makeText(requireContext(), R.string.no_device, Toast.LENGTH_SHORT).show();
            dismissAllowingStateLoss();
            return;
        }

        int controls = 0;
        List<Sensor> sensors = mDevice.querySensors();
        for (int i = 0; i < sensors.size(); i++) {
            Sensor sensor = sensors.get(i);
            int before = list.getChildCount();
            addHeader(list, sensorName(sensor, i));
            for (Option option : Option.values()) {
                try {
                    if (sensor.supports(option) && !sensor.isReadOnly(option))
                        addControl(list, sensor, option);
                } catch (Exception e) {
                    Log.w(TAG, option.name() + ": " + e.getMessage());
                }
            }
            // Drop the header when the sensor exposed nothing adjustable
            if (list.getChildCount() == before + 1)
                list.removeViewAt(before);
            else
                controls++;
        }
        if (controls == 0)
            Toast.makeText(requireContext(), R.string.no_controls, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mDevice != null) {
            mDevice.close();
            mDevice = null;
        }
    }

    private String sensorName(Sensor sensor, int index) {
        if (sensor.is(Extension.DEPTH_SENSOR))
            return getString(R.string.depth_sensor);
        if (sensor.is(Extension.COLOR_SENSOR))
            return getString(R.string.color_sensor);
        if (sensor.is(Extension.MOTION_SENSOR))
            return getString(R.string.motion_sensor);
        return getString(R.string.sensor_n, index + 1);
    }

    private void addHeader(LinearLayout list, String title) {
        TextView header = (TextView) getLayoutInflater().inflate(R.layout.item_section_header, list, false);
        header.setText(title);
        list.addView(header);
    }

    private void addControl(LinearLayout list, Sensor sensor, Option option) {
        final float min = sensor.getMinRange(option), max = sensor.getMaxRange(option), step = sensor.getStep(option);
        if (max <= min)
            return;
        final float value = sensor.getValue(option);
        final String name = InfoActivity.prettyName(option);

        if (min == 0 && max == 1 && step == 1) {
            MaterialSwitch sw = (MaterialSwitch) getLayoutInflater().inflate(R.layout.item_control_switch, list, false);
            sw.setText(name);
            sw.setChecked(value != 0);
            sw.setOnCheckedChangeListener((button, checked) -> apply(sensor, option, name, checked ? 1 : 0));
            list.addView(sw);
            return;
        }

        View row = getLayoutInflater().inflate(R.layout.item_control_slider, list, false);
        ((TextView) row.findViewById(R.id.control_name)).setText(name);
        final TextView valueText = row.findViewById(R.id.control_value);
        final Slider slider = row.findViewById(R.id.control_slider);
        slider.setValueFrom(min);
        slider.setValueTo(max);
        // Material sliders require the range to be a whole number of steps
        float steps = (max - min) / step;
        if (step > 0 && Math.abs(steps - Math.round(steps)) < 1e-4)
            slider.setStepSize(step);
        slider.setValue(Math.max(min, Math.min(max, value)));
        valueText.setText(describe(sensor, option, value));
        slider.setLabelFormatter(v -> describe(sensor, option, v));
        slider.addOnChangeListener((s, v, fromUser) -> valueText.setText(describe(sensor, option, v)));
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider s) {
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider s) {
                apply(sensor, option, name, s.getValue());
            }
        });
        list.addView(row);
    }

    private static String describe(Sensor sensor, Option option, float value) {
        try {
            String description = sensor.valueDescription(option, value);
            if (description != null && !description.isEmpty())
                return description;
        } catch (Exception ignored) {
        }
        return value == Math.rint(value) ? String.valueOf((int) value) : String.format(Locale.US, "%.2f", value);
    }

    private void apply(Sensor sensor, Option option, String name, float value) {
        try {
            sensor.setValue(option, value);
        } catch (Exception e) {
            Log.e(TAG, "set " + option.name() + " failed: " + e.getMessage());
            Toast.makeText(requireContext(), getString(R.string.control_set_failed, name, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }
}
