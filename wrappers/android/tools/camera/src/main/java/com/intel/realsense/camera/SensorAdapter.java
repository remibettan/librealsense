package com.intel.realsense.camera;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;

import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.StreamProfile;
import com.intel.realsense.librealsense.VideoStreamProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SensorAdapter extends SettingsViewAdapter{

    private static final int mLayoutResourceId = R.layout.stream_profile_list_view;
    private final SensorSelector mSensorCells[];
    private final LayoutInflater mLayoutInflater;
    private final Listener mListener;
    private Context mContext;

    public class Holder {
        private Spinner resolution;
        private Spinner fps;
    }

    interface Listener{
        void onCheckedChanged(SensorSelector holder);
    }

    public SensorAdapter(Context context, List<String> expandableListTitle,
                         HashMap<String, List<String>> expandableListDetail,
                         SensorSelector[] data,
                         Listener listener) {
        super(context, expandableListTitle, expandableListDetail);
        mSensorCells = data;
        mLayoutInflater = ((Activity) context).getLayoutInflater();
        mListener = listener;
        mContext = context;
    }

    @Override
    public View getChildView(int listPosition, int expandedListPosition,
                             boolean isLastChild, View convertView, ViewGroup parent) {
        View rawView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        SensorSelector listViewLine = mSensorCells[expandedListPosition];

        final Holder holder;
        holder = new Holder();
        holder.resolution = rawView.findViewById(R.id.resolution_spinner);
        holder.fps = rawView.findViewById(R.id.fps_spinner);

        createSpinners(holder, expandedListPosition, listViewLine);

        return rawView;
    }

    void createSpinners(final Holder holder, final int position, SensorSelector sensorSelector){
        Set<String> sensorsNamesSet = new HashSet<>();
        Set<String> frameRatesSet = new HashSet<>();
        Set<String> resolutionsSet = new HashSet<>();

        for(StreamProfile sp : sensorSelector.getProfiles()){
            frameRatesSet.add(String.valueOf(sp.getFrameRate()));
            if(!sp.is(Extension.VIDEO_PROFILE))
                continue;
            VideoStreamProfile vsp = sp.as(Extension.VIDEO_PROFILE);
            resolutionsSet.add(String.valueOf(vsp.getWidth()) + "x" + String.valueOf(vsp.getHeight()));
        }

        ArrayList<String> frameRates = new ArrayList<>(frameRatesSet);
        ArrayList<String> resolutions = new ArrayList<>(resolutionsSet);

        //frame rates
        ArrayAdapter<String> frameRatesAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item, frameRates);
        frameRatesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        holder.fps.setAdapter(frameRatesAdapter);
        holder.fps.setSelection(frameRates.indexOf(String.valueOf(sps.getProfile().getFrameRate())));
        holder.fps.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                StreamProfileSelector s = mStreamProfileCells[position];
                String str = (String) adapterView.getItemAtPosition(i);
                s.updateFrameRate(str);
                mListener.onCheckedChanged(s);
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        //resolutions
        ArrayAdapter<String> resolutionsAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item,  resolutions);
        resolutionsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        holder.resolution.setAdapter(resolutionsAdapter);
        holder.resolution.setSelection(resolutions.indexOf(sps.getResolutionString()));
        holder.resolution.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                StreamProfileSelector s = mStreamProfileCells[position];
                String str = (String) adapterView.getItemAtPosition(i);
                s.updateResolution(str);
                mListener.onCheckedChanged(s);
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        holder.type.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                int position = (int) compoundButton.getTag();
                StreamProfileSelector s = mStreamProfileCells[position];
                s.updateEnabled(b);
                mListener.onCheckedChanged(s);

            }
        });
    }
}
