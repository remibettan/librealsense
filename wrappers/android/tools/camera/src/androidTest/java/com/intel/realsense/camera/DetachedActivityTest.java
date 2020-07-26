package com.intel.realsense.camera;

import android.app.Activity;
import android.app.Instrumentation;
import android.support.test.rule.ActivityTestRule;
import android.support.test.rule.GrantPermissionRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertNotNull;

public class DetachedActivityTest {

    @Rule
    public ActivityTestRule<DetachedActivity> mDetachedActivityTestRule = new ActivityTestRule<>(DetachedActivity.class);

    private Activity mDetachedActivity = null;
    Instrumentation.ActivityMonitor mMonitor = getInstrumentation().addMonitor(PreviewActivity.class.getName(), null, false);

    @Rule
    public GrantPermissionRule cameraPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.CAMERA);
    @Rule
    public GrantPermissionRule writePermissionRule = GrantPermissionRule.grant(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);


    @Before
    public void setUp() throws Exception {
        mDetachedActivity = mDetachedActivityTestRule.getActivity();
    }

    @Test
    public void testLaunchPreviewActivity() {
        Activity previewActivity = getInstrumentation().waitForMonitorWithTimeout(mMonitor, 10000);
        assertNotNull(previewActivity);

        previewActivity.finish();
    }

    @After
    public void tearDown() throws Exception {
        mDetachedActivity = null;
    }
}