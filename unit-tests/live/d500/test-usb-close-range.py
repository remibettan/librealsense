# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

# USB Improved Close Range Depth demo - only the on/off toggle is exposed; ratio/shift/threshold are not.
# This test must run over USB, NOT DDS - the dds context guard is intentionally absent.
# test:donotrun:!nightly
# test:device D555

import pyrealsense2 as rs
from rspy import test, log
from rspy.timer import Timer
import pyrsutils as rsutils
import time


# Metadata bit in the embedded_filters bitmask (DPP Filter Bitmask bit 5)
CLOSE_RANGE_METADATA_BIT = 1 << 5

MAX_TIME_TO_WAIT_FOR_FRAMES = 10  # [sec]


def check_option_in_list(option_id, options_list):
    for option in options_list:
        if option_id == option:
            return True
    return False


def set_get_filter_option_value(embedded_filter, option, value_to_assign):
    initial_value = embedded_filter.get_option(option)
    option_step = embedded_filter.get_option_range(option).step
    embedded_filter.set_option(option, value_to_assign)
    test.check_approx_abs(embedded_filter.get_option(option), value_to_assign, option_step)
    embedded_filter.set_option(option, initial_value)
    test.check_approx_abs(embedded_filter.get_option(option), initial_value, option_step)


dev, _ = test.find_first_device_or_exit()
depth_sensor = dev.first_depth_sensor()

with test.closure("Get Improved Close Range Depth embedded filter on USB (demo)"):
    close_range_filter = depth_sensor.get_embedded_filter(rs.embedded_filter_type.improved_close_range_depth)
    test.check(close_range_filter)

    close_range_options = close_range_filter.get_supported_options()
    # Demo: only the Enable toggle is exposed over USB
    test.check_equal(len(close_range_options), 1)
    test.check(check_option_in_list(rs.option.embedded_filter_enabled, close_range_options))

with test.closure("Close Range Enable range + default (USB demo)"):
    rng = close_range_filter.get_option_range(rs.option.embedded_filter_enabled)
    test.check_equal(rng.min, 0.0)
    test.check_equal(rng.max, 1.0)
    test.check_equal(rng.step, 1.0)
    test.check_equal(close_range_filter.get_option(rs.option.embedded_filter_enabled), 0.0)

with test.closure("Close Range Enable set/get round-trip (USB demo)"):
    set_get_filter_option_value(close_range_filter, rs.option.embedded_filter_enabled, 1.0)

with test.closure("Close Range invalid Enable rejected (USB demo)"):
    test.check_throws(
        lambda: close_range_filter.set_option(rs.option.embedded_filter_enabled, 2.0),
        RuntimeError)
    test.check_throws(
        lambda: close_range_filter.set_option(rs.option.embedded_filter_enabled, -1.0),
        RuntimeError)


depth_profile = next(
    (p for p in depth_sensor.profiles if p.fps() == 30
     and p.stream_type() == rs.stream.depth
     and p.format() == rs.format.z16
     and p.as_video_stream_profile().width() == 640
     and p.as_video_stream_profile().height() == 360),
    None)
if depth_profile is None:
    log.f("D555 did not advertise a 640x360@30fps Z16 depth profile - cannot run the metadata test")

waiting_for_test = False
wait_for_frames_timer = Timer(MAX_TIME_TO_WAIT_FOR_FRAMES)
close_range_enabled = False


def close_range_check_callback(frame):
    # Only stop waiting once we've actually checked a frame carrying the embedded_filters
    # metadata - otherwise an early frame with no metadata could silently pass the test.
    global waiting_for_test, close_range_enabled
    if frame.supports_frame_metadata(rs.frame_metadata_value.embedded_filters):
        md_val = frame.get_frame_metadata(rs.frame_metadata_value.embedded_filters)
        value_to_check = CLOSE_RANGE_METADATA_BIT if close_range_enabled else 0
        test.check_equal(md_val & CLOSE_RANGE_METADATA_BIT, value_to_check)
        waiting_for_test = False


def stream_and_check_close_range_filter():
    global waiting_for_test
    depth_sensor.open(depth_profile)
    try:
        depth_sensor.start(close_range_check_callback)
        try:
            wait_for_frames_timer.start()
            waiting_for_test = True
            while waiting_for_test and not wait_for_frames_timer.has_expired():
                time.sleep(0.5)
            test.check(not wait_for_frames_timer.has_expired())
        finally:
            depth_sensor.stop()
    finally:
        depth_sensor.close()


def enable_close_range_filter():
    close_range_filter.set_option(rs.option.embedded_filter_enabled, 1.0)
    test.check_equal(close_range_filter.get_option(rs.option.embedded_filter_enabled), 1.0)


def disable_close_range_filter():
    close_range_filter.set_option(rs.option.embedded_filter_enabled, 0.0)
    test.check_equal(close_range_filter.get_option(rs.option.embedded_filter_enabled), 0.0)


with test.closure("Improved Close Range Depth embedded filter metadata member (USB demo)"):
    disable_close_range_filter()
    close_range_enabled = False
    stream_and_check_close_range_filter()
    time.sleep(1)
    enable_close_range_filter()
    close_range_enabled = True
    stream_and_check_close_range_filter()
    time.sleep(1)
    disable_close_range_filter()


test.print_results_and_exit()
