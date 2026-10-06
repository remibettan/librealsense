# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

# TEMPORARY: demonstrates soft-check failure reporting in CI; to be removed before merge.

import pytest
import pyrealsense2 as rs
from pytest_check import check

pytestmark = [pytest.mark.device("D400*")]


def test_soft_check_demo(test_device):
    dev, _ = test_device

    def helper():
        check.is_true(False, "helper check that always fails")

    helper()
    check.equal(dev.get_info(rs.camera_info.name), "nope", "second failing check")
