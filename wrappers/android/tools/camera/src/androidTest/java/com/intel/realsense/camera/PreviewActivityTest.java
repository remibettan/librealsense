package com.intel.realsense.camera;

import android.app.Activity;
import android.app.Instrumentation;
import android.support.test.rule.ActivityTestRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertNotNull;

public class PreviewActivityTest {

    @Rule
    public ActivityTestRule<PreviewActivity> mPreviewActivityTestRule = new ActivityTestRule<>(PreviewActivity.class);


    private PreviewActivity mActivity = null;
    Instrumentation.ActivityMonitor mMonitor = getInstrumentation().addMonitor(SettingsActivity.class.getName(), null, false);

    @Before
    public void setUp() throws Exception {
        mActivity = mPreviewActivityTestRule.getActivity();
    }

    @Test
    public void testLaunchSettingsActivity() {
        assertNotNull(mActivity.findViewById(R.id.preview_settings_button));

        onView(withId(R.id.preview_settings_button)).perform(click());

        Activity settingsActivity = getInstrumentation().waitForMonitorWithTimeout(mMonitor, 5000);
        assertNotNull(settingsActivity);

        settingsActivity.finish();
    }


    @After
    public void tearDown() throws Exception {
        mActivity = null;
    }
}