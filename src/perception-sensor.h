// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#pragma once

#include "core/extension.h"

namespace librealsense {


class perception_sensor
{
public:
    virtual ~perception_sensor() = default;
};

MAP_EXTENSION( RS2_EXTENSION_PERCEPTION_SENSOR, librealsense::perception_sensor );



class object_detection_sensor : public virtual perception_sensor
{
public:
    // Shared names for the OD sensor and stream profile, used by both the USB and DDS paths
    static constexpr const char * SENSOR_NAME = "Perception";
    static constexpr const char * STREAM_NAME = "Object Detection";

    virtual ~object_detection_sensor() = default;
};

MAP_EXTENSION( RS2_EXTENSION_OBJECT_DETECTION_SENSOR, librealsense::object_detection_sensor );


}  // namespace librealsense
