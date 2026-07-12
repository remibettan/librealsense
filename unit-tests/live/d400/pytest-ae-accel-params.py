# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.
#
# Validates the AE_ACCEL_PARAMS Hardware-Monitor command (opcode 0x95) on D400
# devices. The command exposes 7 IEEE-754 float tuning parameters for the
# Accelerated AE algorithm:
#   R/W: score_setpoint, score_deadband, saturation_weight,
#        saturation_value, stability_factor
#   R:   score_low_th, score_high_th   (auto-recomputed by FW from setpoint/deadband)
#
# Coverage:
#   1. GET returns 7 floats with values inside the documented ranges
#   2. SET applies R/W fields; a re-GET reflects the new values; low/high_th get
#      recomputed as (setpoint - deadband/2, setpoint + deadband/2) with clamps
#      at 0.08 and 0.45
#   3. SET while the depth sensor is streaming is NACK'd
#
# Skipped until the FW side ships an AE_ACCEL_PARAMS handler — matches the
# pattern used by pytest-depth-ae-toggle.py / pytest-depth-ae-metadata.py.

import struct
import pytest
import pyrealsense2 as rs
import pyrsutils as rsutils
from rspy.pytest.device_helpers import require_min_fw_version
import logging
log = logging.getLogger(__name__)


AE_ACCEL_PARAMS_OPCODE = 0x95
PARAM1_GET = 0
PARAM1_SET = 1

FIELDS_RW = ["setpoint", "deadband", "saturation_weight", "saturation_value", "stability"]
FIELDS_RO = ["score_low_th", "score_high_th"]
FIELDS_ALL = FIELDS_RW + FIELDS_RO

RANGES = {
    "setpoint":          (0.11, 0.40),
    "deadband":          (0.05, 0.20),
    "saturation_weight": (0.00, 1.00),
    "saturation_value":  (0.20, 0.80),
    "stability":         (1.00, 5.00),
    "score_low_th":      (0.08, 0.35),
    "score_high_th":     (0.13, 0.45),
}

# D415 family does not implement Accelerated AE. The rest of the D400 SKUs
# support it starting from FW 5.17.3.20 (R58.3b).
MIN_FW = rsutils.version(5, 17, 3, 20)

pytestmark = [
    pytest.mark.device_each("D400*"),
    pytest.mark.skip(reason="until FW ships AE_ACCEL_PARAMS handler"),
]


def _get_params(hwm):
    cmd = hwm.build_command(opcode=AE_ACCEL_PARAMS_OPCODE, param1=PARAM1_GET)
    raw = hwm.send_and_receive_raw_data(cmd)
    assert len(raw) >= 4 + 4 * len(FIELDS_ALL), \
        f"GET response too short ({len(raw)} bytes); expected >= {4 + 4 * len(FIELDS_ALL)}"
    payload = bytes(raw[4:4 + 4 * len(FIELDS_ALL)])
    return dict(zip(FIELDS_ALL, struct.unpack("<7f", payload)))


def _set_params(hwm, values_rw):
    payload = struct.pack("<5f", *(values_rw[k] for k in FIELDS_RW))
    cmd = hwm.build_command(opcode=AE_ACCEL_PARAMS_OPCODE, param1=PARAM1_SET, data=list(payload))
    hwm.send_and_receive_raw_data(cmd)


def _skip_if_d415(dev):
    name = dev.get_info(rs.camera_info.name)
    if "D415" in name:
        pytest.skip(f"AE_ACCEL_PARAMS not supported on D415 family ({name})")


@pytest.fixture
def hwm_and_sensor(test_device_wrapped):
    dev, _ = test_device_wrapped
    _skip_if_d415(dev)
    require_min_fw_version(dev, MIN_FW, "AE_ACCEL_PARAMS")
    hwm = dev.as_debug_protocol()
    assert hwm is not None, "Device does not expose debug protocol"
    return hwm, dev.first_depth_sensor()


def test_get_returns_seven_fields_in_range(hwm_and_sensor):
    hwm, _ = hwm_and_sensor
    params = _get_params(hwm)
    for field in FIELDS_ALL:
        lo, hi = RANGES[field]
        assert lo <= params[field] <= hi, \
            f"{field}={params[field]} not in [{lo}, {hi}]"


def test_set_applies_writable_fields(hwm_and_sensor):
    hwm, _ = hwm_and_sensor
    original = _get_params(hwm)
    try:
        # Pick mid-range values that satisfy clamping (setpoint +/- deadband/2 within [0.08, 0.45])
        target = {
            "setpoint":          0.25,
            "deadband":          0.10,
            "saturation_weight": 0.50,
            "saturation_value":  0.50,
            "stability":         2.50,
        }
        _set_params(hwm, target)
        new_values = _get_params(hwm)
        for k in FIELDS_RW:
            assert abs(new_values[k] - target[k]) < 1e-4, \
                f"{k}: expected ~{target[k]}, got {new_values[k]}"
        # low/high_th should be recomputed from the new setpoint/deadband
        assert abs(new_values["score_low_th"]  - (target["setpoint"] - target["deadband"] / 2)) < 1e-4
        assert abs(new_values["score_high_th"] - (target["setpoint"] + target["deadband"] / 2)) < 1e-4
    finally:
        _set_params(hwm, {k: original[k] for k in FIELDS_RW})


def test_setpoint_deadband_clamping(hwm_and_sensor):
    """Ticket clamp rules ported from D457: keep score_low_th >= 0.08 and score_high_th <= 0.45."""
    hwm, _ = hwm_and_sensor
    original = _get_params(hwm)
    try:
        # setpoint - deadband/2 < 0.08  -> setpoint clamped to 0.08 + deadband/2
        below = {"setpoint": 0.11, "deadband": 0.20, "saturation_weight": 0.5, "saturation_value": 0.5, "stability": 2.0}
        _set_params(hwm, below)
        clamped_low = _get_params(hwm)
        assert clamped_low["score_low_th"] >= 0.08 - 1e-6, \
            f"score_low_th={clamped_low['score_low_th']} violates >= 0.08 clamp"

        # setpoint + deadband/2 > 0.45  -> setpoint clamped to 0.45 - deadband/2
        above = {"setpoint": 0.40, "deadband": 0.20, "saturation_weight": 0.5, "saturation_value": 0.5, "stability": 2.0}
        _set_params(hwm, above)
        clamped_high = _get_params(hwm)
        assert clamped_high["score_high_th"] <= 0.45 + 1e-6, \
            f"score_high_th={clamped_high['score_high_th']} violates <= 0.45 clamp"
    finally:
        _set_params(hwm, {k: original[k] for k in FIELDS_RW})


def test_set_during_streaming_is_nacked(hwm_and_sensor):
    hwm, depth_sensor = hwm_and_sensor
    original = _get_params(hwm)
    depth_profile = next((p for p in depth_sensor.profiles if p.stream_type() == rs.stream.depth), None)
    if depth_profile is None:
        pytest.skip("Sensor does not expose a depth-stream profile")
    depth_sensor.open(depth_profile)
    depth_sensor.start(lambda _f: None)
    try:
        with pytest.raises(Exception):
            _set_params(hwm, {k: original[k] for k in FIELDS_RW})
        # Values must not have moved
        assert _get_params(hwm) == original
    finally:
        depth_sensor.stop()
        depth_sensor.close()


def test_no_rs2_option_registered(test_device_wrapped):
    """AE_ACCEL_PARAMS is terminal-only; per the ticket, no RS2_OPTION_* is exposed."""
    dev, _ = test_device_wrapped
    _skip_if_d415(dev)
    depth = dev.first_depth_sensor()
    for opt in depth.get_supported_options():
        name = str(opt).lower()
        assert "accel_params" not in name, f"Unexpected option registered: {opt}"
