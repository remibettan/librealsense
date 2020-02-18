package com.intel.realsense.multicam_test;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TimingLogger;

public class TestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    private TimingLogger timingLogger = new TimingLogger("remi", "multicamStream");
    private int iterationCounter = 0;
    @Override
    protected void onResume() {
        super.onResume();
        Log.i("remi", "Iteration number: " + (++iterationCounter));
        Intent intent = new Intent( this, MainActivity.class);
        startActivity(intent);
        timingLogger.addSplit("endOfStream");
        timingLogger.dumpToLog();
    }
}
