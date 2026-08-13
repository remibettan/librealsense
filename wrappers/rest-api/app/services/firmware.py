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
import sys
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
# Firmware may only be fetched from the domain that serves the versions DB itself, so a
# tampered/mistaken `link` can't turn the backend into a fetcher for arbitrary hosts.
_FW_DOWNLOAD_DOMAIN = ".".join(urllib.parse.urlparse(SERVER_VERSIONS_DB_URL).hostname.split(".")[-2:])

# The versions DB is fetched once per process (no TTL — re-downloaded on backend restart,
# like the C++ viewer). Guarded by a lock since request-handler threads read/populate it.
_VERSIONS_DB_ATTEMPTS = 3
_versions_db_lock = threading.Lock()
_versions_db_cache: Dict[str, Any] = {"entries": None}


class _NoRedirects(urllib.request.HTTPRedirectHandler):
    """Refuse to follow redirects, so the domain check can't be sidestepped by a 3xx.

    Returning None leaves urllib to raise HTTPError for the redirect status.
    """

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


_fw_download_opener = urllib.request.build_opener(_NoRedirects)


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


def platform_name():
    """Host name in the versions-DB vocabulary (mirrors rsutils::os::get_platform_name).

    The DB only ever carries "Windows amd64", "Windows x86", "Linux amd64", "Linux arm",
    "Mac OS" or "*", so platform.system() alone ("Darwin", no arch) would never match.
    """
    system = platform.system()
    if system == "Windows":
        return "Windows amd64" if sys.maxsize > 2 ** 32 else "Windows x86"
    if system == "Darwin":
        return "Mac OS"
    if system == "Linux":
        machine = platform.machine().lower()
        return "Linux arm" if machine.startswith(("arm", "aarch64")) else "Linux amd64"
    return ""


def _pick_recommended_fw(entries, device_name, host_platform):
    """Pick the best RECOMMENDED FIRMWARE (version, link) for a device from DB entries.

    Matches each entry's `device_name` against the device (prefix-agnostic, '*' wildcard);
    the most specific pattern (longest literal) wins. Entry `platform` must be '*' or
    equal the host (exact compare, like the C++ query_versions). Returns (None, None)
    when nothing matches.
    """
    if not entries or not device_name:
        return None, None
    best = None  # (specificity, version, link)
    for e in entries:
        if e.get("component") != "FIRMWARE" or e.get("policy_type") != "RECOMMENDED":
            continue
        pattern = e.get("device_name", "")
        if not _device_name_matches(pattern, device_name):
            continue
        plat = e.get("platform") or "*"
        if plat != "*" and plat != host_platform:
            continue
        specificity = len(pattern.replace("*", ""))
        if best is None or specificity > best[0]:
            best = (specificity, e.get("version"), e.get("link"))
    return (best[1], best[2]) if best else (None, None)


def _fetch_versions_db():
    """Return the DB 'versions' list (cached for the process lifetime), or None on failure.

    The host drops a noticeable share of connections, so a single attempt would leave the
    proposal missing for no good reason; retry a couple of times before giving up. The
    lock only guards the cache — holding it across the fetch would stall every other
    request handler for the whole retry budget. Two threads may fetch on a cold cache;
    they produce the same list, so the duplicate work is harmless.
    """
    with _versions_db_lock:
        if _versions_db_cache["entries"] is not None:
            return _versions_db_cache["entries"]
    for attempt in range(_VERSIONS_DB_ATTEMPTS):
        try:
            with urllib.request.urlopen(SERVER_VERSIONS_DB_URL, timeout=5) as resp:
                data = json.loads(resp.read().decode("utf-8"))
        except Exception as e:  # network down, timeout, bad JSON — degrade gracefully
            logging.warning("Could not fetch firmware versions DB (attempt %d): %s", attempt + 1, e)
            continue
        entries = data.get("versions") or []
        # Only cache a non-empty DB: an empty list (CDN hiccup, wrong endpoint) would
        # otherwise be served for the process lifetime and suppress every proposal.
        if entries:
            with _versions_db_lock:
                _versions_db_cache["entries"] = entries
        return entries
    return None


def recommended_firmware(device_name):
    """Recommended FIRMWARE (version, link) for a device from the online DB, or (None, None)."""
    return _pick_recommended_fw(_fetch_versions_db(), device_name, platform_name())


def download_firmware(url: str, on_progress=None) -> bytes:
    """Download a firmware .bin into memory (no disk cache). Raises RealSenseError on failure.

    The link comes from the versions DB, so it must be https on the DB's own domain —
    that rejects file:// / relative / other-scheme links and keeps the fetch from being
    pointed at an arbitrary host. Redirects are refused too, so the domain check can't be
    bounced onward to some other address. Streams in chunks and reports 0..1 progress via
    on_progress(fraction) when Content-Length is available.
    """
    parsed = urllib.parse.urlparse(url)
    host = parsed.hostname or ""
    if parsed.scheme != "https" or not (host == _FW_DOWNLOAD_DOMAIN or host.endswith("." + _FW_DOWNLOAD_DOMAIN)):
        raise RealSenseError(
            status_code=400,
            detail=f"Refusing to download firmware from outside https://*.{_FW_DOWNLOAD_DOMAIN}",
        )
    reported = 0.0
    try:
        buf = bytearray()
        with _fw_download_opener.open(url, timeout=30) as resp:
            total = int(resp.headers.get("Content-Length") or 0)
            while True:
                chunk = resp.read(256 * 1024)
                if not chunk:
                    break
                buf.extend(chunk)
                if len(buf) > _MAX_FW_DOWNLOAD_BYTES:
                    raise RealSenseError(status_code=413, detail="Firmware image exceeds size limit")
                if on_progress and total:
                    reported = min(len(buf) / total, 1.0)
                    on_progress(reported)
        data = bytes(buf)
    except RealSenseError:
        raise
    except Exception as e:
        raise RealSenseError(status_code=502, detail=f"Failed to download firmware: {e}")
    if not data:
        raise RealSenseError(status_code=502, detail="Downloaded firmware image is empty")
    if on_progress and reported < 1.0:
        on_progress(1.0)
    return data
