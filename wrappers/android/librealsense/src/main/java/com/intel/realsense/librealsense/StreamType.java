package com.intel.realsense.librealsense;

public enum StreamType {
    ANY(0),
    DEPTH(1),
    COLOR(2),
    INFRARED(3),
    FISHEYE(4),
    GYRO(5),
    ACCEL(6),
    GPIO(7),
    POSE(8),
    CONFIDENCE(9),
    MOTION(10),
    SAFETY(11),
    OCCUPANCY(12),
    LABELED_POINT_CLOUD(13),
    OBJECT_DETECTION(14);

    private final int mValue;

    private StreamType(int value) { mValue = value; }
    public int value() { return mValue; }
}
