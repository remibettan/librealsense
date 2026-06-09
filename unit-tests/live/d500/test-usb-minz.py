# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

# USB MinZ demo - only the toggle is exposed (see doc/minz-usb-demo-design.md).
# This test must run over USB, NOT DDS - the dds context guard is intentionally absent.
# test:donotrun:!nightly
# test:device D555

import pyrealsense2 as rs
from rspy import test, log
from rspy.timer import Timer
import pyrsutils as rsutils
import time


# MinZ metadata bit in the embedded_filters bitmask (DPP Filter Bitmask bit 5)
MINZ_METADATA_BIT = 1 << 5

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

with test.closure("Get MinZ embedded filter on USB (demo)"):
    minz_embedded_filter = depth_sensor.get_embedded_filter(rs.embedded_filter_type.improved_close_range_depth)
    test.check(minz_embedded_filter)

    minz_options = minz_embedded_filter.get_supported_options()
    # Demo: only the Enable toggle is exposed over USB
    test.check_equal(len(minz_options), 1)
    test.check(check_option_in_list(rs.option.embedded_filter_enabled, minz_options))

with test.closure("MinZ Enable range + default (USB demo)"):
    rng = minz_embedded_filter.get_option_range(rs.option.embedded_filter_enabled)
    test.check_equal(rng.min, 0.0)
    test.check_equal(rng.max, 1.0)
    test.check_equal(rng.step, 1.0)
    test.check_equal(minz_embedded_filter.get_option(rs.option.embedded_filter_enabled), 0.0)

with test.closure("MinZ Enable set/get round-trip (USB demo)"):
    set_get_filter_option_value(minz_embedded_filter, rs.option.embedded_filter_enabled, 1.0)

with test.closure("MinZ invalid Enable rejected (USB demo)"):
    test.check_throws(
        lambda: minz_embedded_filter.set_option(rs.option.embedded_filter_enabled, 2.0),
        RuntimeError)
    test.check_throws(
        lambda: minz_embedded_filter.set_option(rs.option.embedded_filter_enabled, -1.0),
        RuntimeError)


depth_profile = next(p for p in
                     depth_sensor.profiles if p.fps() == 30
                     and p.stream_type() == rs.stream.depth
                     and p.format() == rs.format.z16
                     and p.as_video_stream_profile().width() == 640
                     and p.as_video_stream_profile().height() == 360)

waiting_for_test = False
wait_for_frames_timer = Timer(MAX_TIME_TO_WAIT_FOR_FRAMES)
minz_enabled = False


def minz_check_callback(frame):
    # Only stop waiting once we've actually checked a frame carrying the embedded_filters
    # metadata - otherwise an early frame with no metadata could silently pass the test.
    global waiting_for_test, minz_enabled
    if frame.supports_frame_metadata(rs.frame_metadata_value.embedded_filters):
        md_val = frame.get_frame_metadata(rs.frame_metadata_value.embedded_filters)
        value_to_check = MINZ_METADATA_BIT if minz_enabled else 0
        test.check_equal(md_val & MINZ_METADATA_BIT, value_to_check)
        waiting_for_test = False


def stream_and_check_minz_filter():
    global waiting_for_test
    depth_sensor.open(depth_profile)
    depth_sensor.start(minz_check_callback)
    wait_for_frames_timer.start()
    waiting_for_test = True
    while waiting_for_test and not wait_for_frames_timer.has_expired():
        time.sleep(0.5)
    test.check(not wait_for_frames_timer.has_expired())
    depth_sensor.stop()
    depth_sensor.close()


def enable_minz_filter():
    minz_embedded_filter.set_option(rs.option.embedded_filter_enabled, 1.0)
    test.check_equal(minz_embedded_filter.get_option(rs.option.embedded_filter_enabled), 1.0)


def disable_minz_filter():
    minz_embedded_filter.set_option(rs.option.embedded_filter_enabled, 0.0)
    test.check_equal(minz_embedded_filter.get_option(rs.option.embedded_filter_enabled), 0.0)


with test.closure("MinZ embedded filter metadata member (USB demo)"):
    disable_minz_filter()
    minz_enabled = False
    stream_and_check_minz_filter()
    time.sleep(1)
    enable_minz_filter()
    minz_enabled = True
    stream_and_check_minz_filter()
    time.sleep(1)
    disable_minz_filter()


test.print_results_and_exit()
