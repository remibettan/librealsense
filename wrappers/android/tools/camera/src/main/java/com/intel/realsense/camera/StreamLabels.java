package com.intel.realsense.camera;

import android.graphics.Rect;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.Map;

/** Stream name chips drawn over each stream's viewport. Safe to call from the streaming thread. */
final class StreamLabels {
    private final FrameLayout mContainer;
    private final Map<Integer, TextView> mLabels = new HashMap<>();
    private final int mMargin;

    StreamLabels(FrameLayout container) {
        mContainer = container;
        mMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, container.getResources().getDisplayMetrics());
    }

    void update(final Map<Integer, Pair<String, Rect>> rects) {
        if (rects == null)
            return;
        mContainer.post(() -> {
            for (Map.Entry<Integer, Pair<String, Rect>> e : rects.entrySet()) {
                TextView tv = mLabels.get(e.getKey());
                if (tv == null) {
                    tv = createLabel();
                    mLabels.put(e.getKey(), tv);
                }
                Rect r = e.getValue().second;
                tv.setX(r.left + mMargin);
                tv.setY(r.top + mMargin);
                tv.setText(e.getValue().first);
            }
        });
    }

    void clear() {
        mContainer.post(() -> {
            mContainer.removeAllViews();
            mLabels.clear();
        });
    }

    private TextView createLabel() {
        TextView tv = new TextView(mContainer.getContext());
        tv.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));
        tv.setBackground(ContextCompat.getDrawable(mContainer.getContext(), R.drawable.bg_label_chip));
        tv.setTextColor(ContextCompat.getColor(mContainer.getContext(), R.color.viewer_on_overlay));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        int pad = mMargin / 2;
        tv.setPadding(pad * 2, pad, pad * 2, pad);
        mContainer.addView(tv);
        return tv;
    }
}
