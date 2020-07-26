package com.intel.realsense.librealsense;

import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class JniDeviceTests {


    @Test
    public void checkDeviceIsFound() {
        RsContext ctx = new RsContext();
        try (DeviceList devices = ctx.queryDevices()) {
            int numOfDevices = devices.getDeviceCount();
            System.out.println("Number of devices found = " + numOfDevices);
            assert (numOfDevices > 0);
        }
    }
}
