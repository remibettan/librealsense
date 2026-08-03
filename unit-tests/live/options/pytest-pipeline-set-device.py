# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2024 RealSense, Inc. All Rights Reserved.

import pytest
import platform
import pyrealsense2 as rs
import logging
log = logging.getLogger(__name__)

pytestmark = [
    pytest.mark.device_each("D455"),
    pytest.mark.device_each("D555"),
    pytest.mark.device_type_exclude("DDS"),  # USB-focused: DDS advertises the option through a separate transport
    pytest.mark.skipif(platform.machine() == "aarch64", reason="D455 not available on CI Jetson"),
]

gyro_sensitivity_value = 4.0


def test_pipeline_set_device(test_device):
    dev, ctx = test_device
    motion_sensor = dev.first_motion_sensor()
    if not motion_sensor.supports(rs.option.gyro_sensitivity):
        pytest.skip("Gyro Sensitivity option not supported on this device/FW")
    pipe = rs.pipeline(ctx)
    pipe.set_device(dev)

    motion_sensor.set_option(rs.option.gyro_sensitivity, gyro_sensitivity_value)

    cfg = rs.config()
    cfg.enable_stream(rs.stream.accel)
    cfg.enable_stream(rs.stream.gyro)

    profile = pipe.start(cfg)
    device_from_profile = profile.get_device()
    sensor = device_from_profile.first_motion_sensor()
    sensor_gyro_sensitivity_value = sensor.get_option(rs.option.gyro_sensitivity)
    assert gyro_sensitivity_value == sensor_gyro_sensitivity_value
    pipe.stop()
