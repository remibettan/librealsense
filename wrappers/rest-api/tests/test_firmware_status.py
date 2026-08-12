# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

import json
from unittest import mock

import pytest

from app.core.errors import RealSenseError
from app.services import firmware
from app.services.firmware import (
    download_firmware,
    firmware_update_status,
    platform_name,
    _fetch_versions_db,
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


_PLATFORM_DB = [
    {"device_name": "Intel RealSense D455", "policy_type": "RECOMMENDED", "component": "FIRMWARE",
     "version": "5.17.3.10", "platform": "Linux arm", "link": "arm.bin"},
]


def test_platform_name_is_versions_db_vocabulary():
    # Whatever the host, it must be a string the DB actually uses (or empty on exotic hosts).
    assert platform_name() in ("Windows amd64", "Windows x86", "Linux amd64", "Linux arm", "Mac OS", "")


def test_pick_requires_exact_platform_match():
    # "Linux" must not match the "Linux arm" entry — the C++ query compares platform strings
    # for equality, and a substring match would pick an image built for another arch.
    assert _pick_recommended_fw(_PLATFORM_DB, "Intel RealSense D455", "Linux amd64") == (None, None)
    assert _pick_recommended_fw(_PLATFORM_DB, "Intel RealSense D455", "Linux arm") == ("5.17.3.10", "arm.bin")


def _fake_urlopen(payload):
    resp = mock.MagicMock()
    resp.read.return_value = json.dumps(payload).encode()
    resp.__enter__.return_value = resp
    return mock.patch("urllib.request.urlopen", return_value=resp)


def test_empty_versions_db_is_not_cached(monkeypatch):
    # An empty list must not be remembered — it would suppress every proposal for the
    # lifetime of the process even after the server recovers.
    monkeypatch.setattr(firmware, "_versions_db_cache", {"entries": None})
    with _fake_urlopen({"versions": []}):
        assert _fetch_versions_db() == []
    assert firmware._versions_db_cache["entries"] is None

    with _fake_urlopen({"versions": _DB}):
        assert _fetch_versions_db() == _DB
    assert firmware._versions_db_cache["entries"] == _DB


def test_versions_db_fetch_retries_before_giving_up(monkeypatch):
    monkeypatch.setattr(firmware, "_versions_db_cache", {"entries": None})
    calls = {"n": 0}

    def flaky(*_args, **_kwargs):
        calls["n"] += 1
        if calls["n"] < firmware._VERSIONS_DB_ATTEMPTS:
            raise OSError("connection reset")
        resp = mock.MagicMock()
        resp.read.return_value = json.dumps({"versions": _DB}).encode()
        resp.__enter__.return_value = resp
        return resp

    with mock.patch("urllib.request.urlopen", side_effect=flaky):
        assert _fetch_versions_db() == _DB
    assert calls["n"] == firmware._VERSIONS_DB_ATTEMPTS


@pytest.mark.parametrize("url", [
    "file:///etc/passwd",
    "/Releases/RS4xx/FW/image.bin",                       # relative — no scheme/host
    "http://librealsense.realsenseai.com/image.bin",      # plaintext
    "https://evil.example/image.bin",
    "https://librealsense.realsenseai.com.evil.example/image.bin",  # suffix look-alike
])
def test_download_rejects_urls_outside_the_versions_db_domain(url):
    with pytest.raises(RealSenseError) as excinfo:
        download_firmware(url)
    assert excinfo.value.status_code == 400
