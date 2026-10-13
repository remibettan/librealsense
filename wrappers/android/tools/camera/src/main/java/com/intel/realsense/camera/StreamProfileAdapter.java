package com.intel.realsense.camera;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/** Settings list of streams: an on/off switch plus resolution, frame rate and format pickers per stream. */
public class StreamProfileAdapter extends RecyclerView.Adapter<StreamProfileAdapter.Holder> {

    interface Listener {
        void onChanged(StreamProfileSelector selector);
    }

    private final List<StreamProfileSelector> mItems;
    private final Listener mListener;

    public StreamProfileAdapter(List<StreamProfileSelector> items, Listener listener) {
        mItems = items;
        mListener = listener;
    }

    static class Holder extends RecyclerView.ViewHolder {
        final MaterialSwitch type;
        final View options;
        final TextInputLayout resolutionLayout;
        final MaterialAutoCompleteTextView resolution, fps, format;

        Holder(View view) {
            super(view);
            type = view.findViewById(R.id.stream_type_switch);
            options = view.findViewById(R.id.stream_options);
            resolutionLayout = view.findViewById(R.id.resolution_layout);
            resolution = view.findViewById(R.id.resolution_dropdown);
            fps = view.findViewById(R.id.fps_dropdown);
            format = view.findViewById(R.id.format_dropdown);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stream, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        final StreamProfileSelector item = mItems.get(position);

        h.type.setOnCheckedChangeListener(null);
        h.type.setText(item.getName());
        h.type.setChecked(item.isEnabled());
        h.type.setOnCheckedChangeListener((button, checked) -> {
            item.setEnabled(checked);
            h.options.setAlpha(checked ? 1f : 0.5f);
            mListener.onChanged(item);
        });
        h.options.setAlpha(item.isEnabled() ? 1f : 0.5f);

        h.resolutionLayout.setVisibility(item.isVideo() ? View.VISIBLE : View.GONE);
        bindDropdown(h.resolution, item.getResolutions(), item.getResolution(), value -> {
            item.selectResolution(value);
            refresh(h, item);
        });
        bindDropdown(h.fps, item.getFrameRates(), item.getFrameRate(), value -> {
            item.selectFrameRate(value);
            refresh(h, item);
        });
        bindDropdown(h.format, item.getFormats(), item.getFormat(), value -> {
            item.selectFormat(value);
            refresh(h, item);
        });
    }

    /** Re-show the resolved values (a pick can move the other two fields) and persist. */
    private void refresh(Holder h, StreamProfileSelector item) {
        h.resolution.setText(item.getResolution(), false);
        h.fps.setText(item.getFrameRate(), false);
        h.format.setText(item.getFormat(), false);
        mListener.onChanged(item);
    }

    private interface OnPick {
        void onPick(String value);
    }

    private static void bindDropdown(MaterialAutoCompleteTextView view, List<String> values, String current, OnPick onPick) {
        view.setAdapter(new ArrayAdapter<>(view.getContext(), android.R.layout.simple_list_item_1, values));
        view.setText(current, false);
        view.setOnItemClickListener((parent, v, pos, id) -> onPick.onPick(values.get(pos)));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }
}
