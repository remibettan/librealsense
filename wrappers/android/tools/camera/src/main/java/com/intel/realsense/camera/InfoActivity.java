package com.intel.realsense.camera;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.intel.realsense.librealsense.CameraInfo;
import com.intel.realsense.librealsense.Device;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.RsContext;

import java.util.ArrayList;
import java.util.List;

/** All camera info fields of the connected device; tap a row to copy its value. */
public class InfoActivity extends AppCompatActivity {
    private static final String TAG = "librs camera info";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        Ui.edgeToEdgeWithAppBar(this, findViewById(R.id.root));
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.device_info);
        Ui.setupToolbar(this, toolbar);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final List<String[]> info = new ArrayList<>();
        try (RsContext ctx = new RsContext(); DeviceList devices = ctx.queryDevices()) {
            if (devices.getDeviceCount() == 0) {
                finish();
                return;
            }
            try (Device device = devices.createDevice(0)) {
                if (device == null) {
                    Log.e(TAG, "failed to create device");
                    finish();
                    return;
                }
                for (CameraInfo ci : CameraInfo.values())
                    if (device.supportsInfo(ci))
                        info.add(new String[]{prettyName(ci), device.getInfo(ci)});
            }
        }

        ListView listView = findViewById(R.id.list_view);
        listView.setAdapter(new ArrayAdapter<String[]>(this, R.layout.item_two_line, info) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = convertView != null ? convertView : LayoutInflater.from(getContext()).inflate(R.layout.item_two_line, parent, false);
                ((ImageView) view.findViewById(R.id.item_icon)).setImageResource(R.drawable.ic_info);
                ((TextView) view.findViewById(R.id.item_title)).setText(info.get(position)[0]);
                ((TextView) view.findViewById(R.id.item_subtitle)).setText(info.get(position)[1]);
                return view;
            }
        });
        listView.setOnItemClickListener((parent, view, position, id) -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(info.get(position)[0], info.get(position)[1]));
            Toast.makeText(this, getString(R.string.copied, info.get(position)[0]), Toast.LENGTH_SHORT).show();
        });
    }

    /** SERIAL_NUMBER -> "Serial number" */
    static String prettyName(Enum<?> value) {
        String name = value.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
