# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

import time

import pyrealsense2 as rs
import log_helpers as common


def wait_for_messages(n, timeout=5.0):
    """Async dispatch: the callback fires on a worker thread shortly after rs.log()."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if common.n_messages >= n:
            return
        time.sleep(0.05)


def test_async_callback_receives_messages(reset_logger):
    rs.log_to_callback(rs.log_severity.warn, common.message_counter, asynchronous=True)
    assert common.n_messages == 0
    common.log_all()
    wait_for_messages(2)
    assert common.n_messages == 2  # warning, error


def test_async_message_content(reset_logger):
    received = []

    def collect(severity, message):
        received.append((severity, message.raw(), message.full(), message.line_number(), message.filename()))

    rs.log_to_callback(rs.log_severity.error, collect, asynchronous=True)
    rs.log(rs.log_severity.error, "async content check")
    deadline = time.monotonic() + 5.0
    while time.monotonic() < deadline and not received:
        time.sleep(0.05)
    assert len(received) == 1
    severity, raw, full, line, filename = received[0]
    assert severity == rs.log_severity.error
    assert raw == "async content check"
    assert raw in full


def test_async_and_sync_coexist(reset_logger):
    rs.log_to_callback(rs.log_severity.error, common.message_counter, asynchronous=True)
    rs.log_to_callback(rs.log_severity.error, common.message_counter_2)  # sync
    common.log_all()
    assert common.n_messages_2 == 1  # sync path: already delivered
    wait_for_messages(1)
    assert common.n_messages == 1
