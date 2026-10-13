package com.intel.realsense.camera;

import androidx.annotation.NonNull;

import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.StreamProfile;
import com.intel.realsense.librealsense.StreamType;
import com.intel.realsense.librealsense.VideoStreamProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One stream (type + index) of the camera, its selectable profiles and the currently chosen one. */
public class StreamProfileSelector implements Comparable<StreamProfileSelector> {
    private final List<StreamProfile> mProfiles;
    private boolean mEnabled;
    private int mIndex;

    public StreamProfileSelector(boolean enable, int index, List<StreamProfile> profiles) {
        mProfiles = profiles;
        mEnabled = enable;
        mIndex = index >= 0 && index < profiles.size() ? index : 0;
    }

    public boolean isEnabled() { return mEnabled; }
    public void setEnabled(boolean enabled) { mEnabled = enabled; }
    public int getIndex() { return mIndex; }
    public List<StreamProfile> getProfiles() { return mProfiles; }
    public StreamProfile getProfile() { return mProfiles.get(mIndex); }

    public String getName() {
        StreamProfile p = getProfile();
        if (p.getType() == StreamType.INFRARED)
            return p.getType().name() + "-" + p.getIndex();
        return p.getType().name();
    }

    public boolean isVideo() {
        return getProfile().is(Extension.VIDEO_PROFILE);
    }

    public String getFormat() { return getProfile().getFormat().name(); }
    public String getFrameRate() { return String.valueOf(getProfile().getFrameRate()); }
    public String getResolution() { return resolutionOf(getProfile()); }

    private static String resolutionOf(StreamProfile p) {
        if (!p.is(Extension.VIDEO_PROFILE))
            return "";
        VideoStreamProfile vsp = p.as(Extension.VIDEO_PROFILE);
        return vsp.getWidth() + "x" + vsp.getHeight();
    }

    public List<String> getFormats() {
        Set<String> set = new LinkedHashSet<>();
        for (StreamProfile p : mProfiles)
            set.add(p.getFormat().name());
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }

    public List<String> getFrameRates() {
        Set<Integer> set = new LinkedHashSet<>();
        for (StreamProfile p : mProfiles)
            set.add(p.getFrameRate());
        List<Integer> sorted = new ArrayList<>(set);
        Collections.sort(sorted, Collections.reverseOrder());
        List<String> list = new ArrayList<>();
        for (Integer fps : sorted)
            list.add(String.valueOf(fps));
        return list;
    }

    public List<String> getResolutions() {
        List<VideoStreamProfile> videos = new ArrayList<>();
        for (StreamProfile p : mProfiles)
            if (p.is(Extension.VIDEO_PROFILE))
                videos.add(p.as(Extension.VIDEO_PROFILE));
        Collections.sort(videos, (a, b) -> a.getWidth() != b.getWidth() ? b.getWidth() - a.getWidth() : b.getHeight() - a.getHeight());
        Set<String> set = new LinkedHashSet<>();
        for (VideoStreamProfile v : videos)
            set.add(v.getWidth() + "x" + v.getHeight());
        return new ArrayList<>(set);
    }

    public void selectFormat(String format) { select(format, getResolution(), getFrameRate()); }
    public void selectResolution(String resolution) { select(getFormat(), resolution, getFrameRate()); }
    public void selectFrameRate(String fps) { select(getFormat(), getResolution(), fps); }

    /** Picks the profile matching the request, falling back to the closest one so a choice never leaves the stream unresolved. */
    private void select(String format, String resolution, String fps) {
        int best = -1, bestScore = -1;
        for (int i = 0; i < mProfiles.size(); i++) {
            StreamProfile p = mProfiles.get(i);
            int score = (p.getFormat().name().equals(format) ? 4 : 0)
                    + (resolutionOf(p).equals(resolution) ? 2 : 0)
                    + (String.valueOf(p.getFrameRate()).equals(fps) ? 1 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        mIndex = best;
    }

    @Override
    public int compareTo(@NonNull StreamProfileSelector other) {
        return getName().compareTo(other.getName());
    }
}
