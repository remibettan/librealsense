package com.intel.realsense.camera;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.arch.lifecycle.Lifecycle.State.STARTED;
import static junit.framework.TestCase.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class StreamingTest {

    @Rule
    public ActivityTestRule<DetachedActivity> mDetachedActivityTestRule =
            new ActivityTestRule< >(DetachedActivity.class);

    @Rule
    public ActivityTestRule<PreviewActivity> mPreviewActivityTestRule =
            new ActivityTestRule< >(PreviewActivity.class);//, false, false);

    @Test
    public void onDetachedCreate() {
        assertNotNull(mDetachedActivityTestRule);
    }

    @Test
    public void onPreviewCreate() {
        assertNotNull(mPreviewActivityTestRule);
    }

    @Test
    public void checkSettingsButton() {
        PreviewActivity previewActivity = mPreviewActivityTestRule.getActivity();
        View settingsButton = previewActivity.findViewById(R.id.preview_settings_button);
        assertNotNull(settingsButton);

        // TODO - Remi - following line does not work - find why?!
        // onView(withId(R.id.preview_settings_button)).check(matches(isDisplayed()));
    }

    @Test
    public void hasPreviewActivityStarted() {
        boolean hasStarted = mPreviewActivityTestRule.getActivity().getLifecycle().getCurrentState().isAtLeast(STARTED);
        assert(hasStarted);
    }




}
