# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

from app.services.firmware import (
    firmware_update_status,
    _pick_recommended_fw,
    FW_STATUS_OUTDATED,
    FW_STATUS_UP_TO_DATE,
    FW_STATUS_UNKNOWN,
)

_DB = [
    {"device_name": "Intel RealSense D4*", "policy_type": "RECOMMENDED", "component": "FIRMWARE",
     "version": "5.17.0.10", "platform": "*", "link": "d4x.bin"},
    {"device_name": "Intel RealSense D455", "policy_type": "RECOMMENDED", "component": "FIRMWARE",
     "version": "5.17.3.10", "platform": "*", "link": "d455.bin"},
    {"device_name": "Intel RealSense D4*", "policy_type": "RECOMMENDED", "component": "LIBREALSENSE",
     "version": "2.58.2", "platform": "*", "link": "sw"},
    {"device_name": "Intel RealSense D457", "policy_type": "REQUIRED", "component": "FIRMWARE",
     "version": "9.9.9.9", "platform": "*", "link": "x"},
]


def test_current_below_recommended_is_outdated():
    assert firmware_update_status("5.16.0.1", "5.17.0.9") == FW_STATUS_OUTDATED


def test_current_equal_recommended_is_up_to_date():
    assert firmware_update_status("5.17.0.9", "5.17.0.9") == FW_STATUS_UP_TO_DATE


def test_current_above_recommended_is_up_to_date():
    # numeric compare, not lexical: 10 > 9
    assert firmware_update_status("5.17.0.10", "5.17.0.9") == FW_STATUS_UP_TO_DATE


def test_missing_recommended_is_unknown():
    assert firmware_update_status("5.17.0.9", None) == FW_STATUS_UNKNOWN
    assert firmware_update_status("5.17.0.9", "") == FW_STATUS_UNKNOWN


def test_missing_current_is_unknown():
    assert firmware_update_status(None, "5.17.0.9") == FW_STATUS_UNKNOWN


def test_non_numeric_is_unknown():
    assert firmware_update_status("abc", "5.17.0.9") == FW_STATUS_UNKNOWN


def test_pick_prefers_specific_over_wildcard():
    # D455 has an exact entry (5.17.3.10) that must win over the D4* wildcard (5.17.0.10)
    ver, link = _pick_recommended_fw(_DB, "Intel RealSense D455", "Windows")
    assert (ver, link) == ("5.17.3.10", "d455.bin")


def test_pick_falls_back_to_wildcard():
    ver, link = _pick_recommended_fw(_DB, "Intel RealSense D435", "Windows")
    assert (ver, link) == ("5.17.0.10", "d4x.bin")


def test_pick_ignores_non_firmware_and_non_recommended():
    # LIBREALSENSE component and REQUIRED policy must never be returned as FW recommendation
    ver, _ = _pick_recommended_fw(_DB, "Intel RealSense D457", "Windows")
    assert ver == "5.17.0.10"  # matches D4* FIRMWARE/RECOMMENDED, not the REQUIRED 9.9.9.9


def test_pick_matches_prefixless_reported_name():
    # SDK reports "RealSense D455" (no "Intel " prefix); DB uses "Intel RealSense D455".
    # The prefix must be stripped on both sides or the proposal never fires.
    ver, link = _pick_recommended_fw(_DB, "RealSense D455", "Windows")
    assert (ver, link) == ("5.17.3.10", "d455.bin")


def test_pick_prefixless_falls_back_to_wildcard():
    ver, _ = _pick_recommended_fw(_DB, "RealSense D435", "Windows")
    assert ver == "5.17.0.10"


def test_pick_no_match_returns_none():
    assert _pick_recommended_fw(_DB, "Some Other Camera", "Windows") == (None, None)
    assert _pick_recommended_fw([], "Intel RealSense D455", "Windows") == (None, None)
    assert _pick_recommended_fw(None, "Intel RealSense D455", "Windows") == (None, None)
