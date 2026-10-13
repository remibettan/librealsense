package com.intel.realsense.camera;

import android.view.View;
import android.view.WindowManager;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;

/** Window helpers shared by the activities: edge-to-edge insets and immersive streaming screens. */
final class Ui {
    private Ui() {}

    /** Draws edge-to-edge and pads {@code content} so it clears the system bars and display cutout. */
    static void edgeToEdge(ComponentActivity activity, View content) {
        EdgeToEdge.enable(activity);
        padForSystemBars(content, true);
    }

    /** Same, for screens whose AppBarLayout already absorbs the top inset. */
    static void edgeToEdgeWithAppBar(ComponentActivity activity, View content) {
        EdgeToEdge.enable(activity);
        padForSystemBars(content, false);
    }

    /** Pads the view by the system bar insets; {@code includeTop} is false when an app bar already handles the top. */
    static void padForSystemBars(View view, boolean includeTop) {
        final int top = view.getPaddingTop(), bottom = view.getPaddingBottom();
        final int left = view.getPaddingLeft(), right = view.getPaddingRight();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(left + bars.left, includeTop ? top + bars.top : top, right + bars.right, bottom + bars.bottom);
            return insets;
        });
    }

    /** Full-screen streaming mode: bars hidden until swiped in, screen kept on. */
    static void immersive(ComponentActivity activity) {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    static void setupToolbar(ComponentActivity activity, MaterialToolbar toolbar) {
        toolbar.setNavigationOnClickListener(v -> activity.getOnBackPressedDispatcher().onBackPressed());
    }
}
