# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

"""Firmware version comparison, online versions-DB lookup, and image download.

Pure logic with no pyrealsense2 dependency, so tests can import it directly without
loading the SDK. The SDK no longer bundles firmware, so the recommended version is
looked up from the online versions DB (mirrors the C++ viewer's server_versions_db_url),
and the image is downloaded into memory and flashed from there (like the C++ viewer's
download_to_bytes_vector) — no temp files.
"""

import json
import logging
import platform
import threading
import urllib.parse
import urllib.request
from typing import Any, Dict, Optional

from app.core.errors import RealSenseError

FW_STATUS_UNKNOWN = "unknown"
FW_STATUS_OUTDATED = "outdated"
FW_STATUS_UP_TO_DATE = "up_to_date"

SERVER_VERSIONS_DB_URL = "https://librealsense.realsenseai.com/Releases/rs_versions_db.json"
_MAX_FW_DOWNLOAD_BYTES = 64 * 1024 * 1024

# The versions DB is fetched once per process (no TTL — re-downloaded on backend restart,
# like the C++ viewer). Guarded by a lock since request-handler threads read/populate it.
_versions_db_lock = threading.Lock()
_versions_db_cache: Dict[str, Any] = {"entries": None}


def _parse_fw_version(v: Optional[str]) -> Optional[tuple]:
    """Parse a dotted firmware version ("5.17.0.10") into an int tuple, or None."""
    if not v:
        return None
    try:
        return tuple(int(p) for p in v.split("."))
    except ValueError:
        return None


def firmware_update_status(current: Optional[str], recommended: Optional[str]) -> str:
    """Compare current vs recommended FW versions numerically.

    Returns FW_STATUS_OUTDATED when current < recommended, FW_STATUS_UP_TO_DATE when
    current >= recommended, and FW_STATUS_UNKNOWN when either can't be parsed.
    """
    cur = _parse_fw_version(current)
    rec = _parse_fw_version(recommended)
    if cur is None or rec is None:
        return FW_STATUS_UNKNOWN
    return FW_STATUS_OUTDATED if cur < rec else FW_STATUS_UP_TO_DATE


def _strip_intel_prefix(name):
    """Drop the legacy 'Intel ' vendor prefix. Newer SDKs report names without it while
    the DB still carries it, so comparisons must be prefix-agnostic (mirrors the C++
    versions_db_manager::strip_intel_prefix)."""
    prefix = "Intel "
    return name[len(prefix):] if name.startswith(prefix) else name


def _device_name_matches(db_name, device_name):
    """True if a DB device_name pattern matches the reported name, '*' = trailing wildcard.
    Mirrors versions_db_manager::is_device_name_equal (prefix-stripped, compare up to '*')."""
    db = _strip_intel_prefix(db_name or "")
    cmp = _strip_intel_prefix(device_name or "")
    star = db.find("*")
    if star == -1:
        return db == cmp
    return db[:star] == cmp[:star]


def _pick_recommended_fw(entries, device_name, host_platform):
    """Pick the best RECOMMENDED FIRMWARE (version, link) for a device from DB entries.

    Matches each entry's `device_name` against the device (prefix-agnostic, '*' wildcard);
    the most specific pattern (longest literal) wins. Entry `platform` must be '*' or
    match the host. Returns (None, None) when nothing matches.
    """
    if not entries or not device_name:
        return None, None
    host = (host_platform or "").lower()
    best = None  # (specificity, version, link)
    for e in entries:
        if e.get("component") != "FIRMWARE" or e.get("policy_type") != "RECOMMENDED":
            continue
        pattern = e.get("device_name", "")
        if not _device_name_matches(pattern, device_name):
            continue
        plat = (e.get("platform") or "*").lower()
        if plat != "*" and plat not in host and host not in plat:
            continue
        specificity = len(pattern.replace("*", ""))
        if best is None or specificity > best[0]:
            best = (specificity, e.get("version"), e.get("link"))
    return (best[1], best[2]) if best else (None, None)


def _fetch_versions_db():
    """Return the DB 'versions' list (cached for the process lifetime), or None on failure."""
    with _versions_db_lock:
        if _versions_db_cache["entries"] is not None:
            return _versions_db_cache["entries"]
        try:
            with urllib.request.urlopen(SERVER_VERSIONS_DB_URL, timeout=10) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            entries = data.get("versions", [])
            _versions_db_cache["entries"] = entries
            return entries
        except Exception as e:  # network down, timeout, bad JSON — degrade gracefully
            logging.warning("Could not fetch firmware versions DB: %s", e)
            return None


def recommended_firmware(device_name):
    """Recommended FIRMWARE (version, link) for a device from the online DB, or (None, None)."""
    return _pick_recommended_fw(_fetch_versions_db(), device_name, platform.system())


def download_firmware(url: str, on_progress=None) -> bytes:
    """Download a firmware .bin into memory (no disk cache). Raises RealSenseError on failure.

    Restricts to http(s) URLs (the link comes from the versions DB; reject anything else
    to avoid file:// / relative / other-scheme fetches). Streams in chunks and reports
    0..1 progress via on_progress(fraction) when Content-Length is available.
    """
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme not in ("http", "https") or not parsed.netloc:
        raise RealSenseError(status_code=400, detail="Refusing to download firmware from a non-http(s) URL")
    try:
        buf = bytearray()
        with urllib.request.urlopen(url, timeout=30) as resp:
            total = int(resp.headers.get("Content-Length") or 0)
            while True:
                chunk = resp.read(256 * 1024)
                if not chunk:
                    break
                buf.extend(chunk)
                if len(buf) > _MAX_FW_DOWNLOAD_BYTES:
                    raise RealSenseError(status_code=413, detail="Firmware image exceeds size limit")
                if on_progress and total:
                    on_progress(min(len(buf) / total, 1.0))
        data = bytes(buf)
    except RealSenseError:
        raise
    except Exception as e:
        raise RealSenseError(status_code=502, detail=f"Failed to download firmware: {e}")
    if not data:
        raise RealSenseError(status_code=502, detail="Downloaded firmware image is empty")
    if on_progress:
        on_progress(1.0)
    return data
