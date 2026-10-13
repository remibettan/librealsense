package com.intel.realsense.camera;

import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.Frame;
import com.intel.realsense.librealsense.FrameCallback;
import com.intel.realsense.librealsense.FrameMetadata;
import com.intel.realsense.librealsense.FrameSet;
import com.intel.realsense.librealsense.StreamProfile;
import com.intel.realsense.librealsense.VideoStreamProfile;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Per-stream frame counters and metadata, rendered as text by {@link #prettyPrint()}. */
public class StreamingStats {
    private final Map<Integer, Statistics> mStreamsMap = new TreeMap<>();

    private Statistics initStream(StreamProfile profile) {
        String resolution = "";
        if (profile.is(Extension.VIDEO_PROFILE)) {
            VideoStreamProfile vsp = profile.as(Extension.VIDEO_PROFILE);
            resolution = vsp.getWidth() + "x" + vsp.getHeight();
        }
        String name = profile.getType().name() + " | " + profile.getFormat().name() + " | " + resolution + " | " + profile.getFrameRate() + " fps";
        Statistics stats = new Statistics(name);
        mStreamsMap.put(profile.getUniqueId(), stats);
        return stats;
    }

    private final FrameCallback mFrameCallback = new FrameCallback() {
        @Override
        public void onFrame(Frame f) {
            try (StreamProfile profile = f.getProfile()) {
                Statistics stats = mStreamsMap.get(profile.getUniqueId());
                if (stats == null)
                    stats = initStream(profile);
                stats.onFrame(f);
            }
        }
    };

    public synchronized void onFrameset(FrameSet frames) {
        frames.foreach(mFrameCallback);
    }

    public synchronized String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        for (Statistics s : mStreamsMap.values())
            sb.append(s.prettyPrint()).append("\n\n");
        return sb.toString().trim();
    }

    private static class Statistics {
        private final String mName;
        private final long mStartTime = System.currentTimeMillis();
        private long mBaseTime = mStartTime;
        private float mFps = 0;
        private long mFrameCount = 0;
        private long mTotalFrameCount = 0;
        private int mFrameNumber = 0;
        private int mFirstFrameNumber = -1;
        private double mHWTimestamp = 0;
        private long mSWTimestamp = 0;
        private String mEmitter = "-";
        private String mExposure = "-";

        Statistics(String name) {
            mName = name;
        }

        void onFrame(Frame f) {
            int fn = f.getNumber();
            if (fn == mFrameNumber)
                return;
            mFrameNumber = fn;
            if (mFirstFrameNumber < 0)
                mFirstFrameNumber = fn;
            mFrameCount++;
            mTotalFrameCount++;
            mHWTimestamp = f.getTimestamp();
            mSWTimestamp = System.currentTimeMillis();
            if (f.supportsMetadata(FrameMetadata.FRAME_EMITTER_MODE))
                mEmitter = String.valueOf(f.getMetadata(FrameMetadata.FRAME_EMITTER_MODE));
            if (f.supportsMetadata(FrameMetadata.ACTUAL_EXPOSURE))
                mExposure = String.valueOf(f.getMetadata(FrameMetadata.ACTUAL_EXPOSURE));

            float diffInSeconds = (mSWTimestamp - mBaseTime) * 0.001f;
            if (diffInSeconds > 2) {
                mFps = mFrameCount / diffInSeconds;
                mFrameCount = 0;
                mBaseTime = mSWTimestamp;
            }
        }

        String prettyPrint() {
            long runTime = (System.currentTimeMillis() - mStartTime) / 1000;
            long loss = mFirstFrameNumber < 0 ? 0 : Math.max(0, (mFrameNumber - mFirstFrameNumber + 1) - mTotalFrameCount);
            return mName +
                    String.format(Locale.US, "\n  Frame rate   %.1f fps", mFps) +
                    "\n  Frame count  " + mTotalFrameCount +
                    "\n  Frame number " + mFrameNumber +
                    "\n  Frame loss   " + loss +
                    String.format(Locale.US, "\n  HW timestamp %.3f", mHWTimestamp) +
                    "\n  SW timestamp " + mSWTimestamp +
                    "\n  Run time     " + runTime + " s" +
                    "\n  Emitter mode " + mEmitter +
                    "\n  Exposure     " + mExposure;
        }
    }
}
