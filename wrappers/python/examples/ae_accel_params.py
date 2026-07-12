# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.
#
# Terminal-only utility for the AE_ACCEL_PARAMS Hardware-Monitor command that
# exposes the Accelerated AE tuning parameters on D400.
#
# Opcode:  AE_ACCEL_PARAMS = 0x95 (D400 family; must match the FW-side handler)
# Applies: D400 SKUs where the FW supports Accelerated AE, FW >= 5.17.3.20.
#
# Wire protocol (subject to FW confirmation):
#   GET: param1 = 0, no data payload.
#     Response: 4-byte opcode echo + 7 IEEE-754 floats (28 bytes), in order:
#         score_setpoint, score_deadband, saturation_weight,
#         saturation_value, stability_factor, score_low_th, score_high_th
#   SET: param1 = 1, data payload = 5 IEEE-754 floats:
#         score_setpoint, score_deadband, saturation_weight,
#         saturation_value, stability_factor
#     FW recomputes score_low_th and score_high_th from the new setpoint/deadband
#     (clamping: setpoint - deadband/2 >= 0.08, setpoint + deadband/2 <= 0.45).
#     SET is NACK'd while the depth sensor is streaming.
#
# Usage:
#   python ae_accel_params.py                                                 # GET current values
#   python ae_accel_params.py --set setpoint=0.20 deadband=0.10 stability=2.0 # partial SET (unspecified fields kept)
#   python ae_accel_params.py --serial <SN>                                   # target a specific device

import argparse
import struct
import sys
import pyrealsense2 as rs

AE_ACCEL_PARAMS_OPCODE = 0x95
PARAM1_GET = 0
PARAM1_SET = 1

# D400-family only. On D500, opcode 0x95 is SAFETY_PRESET_WRITE - running
# this against a D500 would be destructive, hence the strict product-line gate.
REQUIRED_PRODUCT_LINE = "D400"
MIN_FW_VERSION = (5, 17, 3, 20)  # First FW to ship Accelerated AE on D40x/D43x (R58.3b)

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


def pick_device(serial=None):
    ctx = rs.context()
    devices = list(ctx.query_devices())
    if not devices:
        sys.exit("No RealSense device found.")
    if serial:
        for d in devices:
            if d.get_info(rs.camera_info.serial_number) == serial:
                return d
        sys.exit(f"No device with serial {serial}.")
    return devices[0]


def unpack_response(raw):
    if len(raw) < 4 + 4 * len(FIELDS_ALL):
        sys.exit(f"Response too short ({len(raw)} bytes) - FW may not implement AE_ACCEL_PARAMS yet.")
    payload = bytes(raw[4:4 + 4 * len(FIELDS_ALL)])
    values = struct.unpack("<7f", payload)
    return dict(zip(FIELDS_ALL, values))


def get_params(hwm):
    cmd = hwm.build_command(opcode=AE_ACCEL_PARAMS_OPCODE, param1=PARAM1_GET)
    try:
        raw = hwm.send_and_receive_raw_data(cmd)
    except Exception as e:
        sys.exit(f"GET AE_ACCEL_PARAMS failed: {e}")
    return unpack_response(raw)


def set_params(hwm, current, updates):
    validated = dict(current)
    for k, v in updates.items():
        if k not in FIELDS_RW:
            sys.exit(f"'{k}' is not a writable field. Writable: {FIELDS_RW}")
        lo, hi = RANGES[k]
        if not (lo <= v <= hi):
            sys.exit(f"{k}={v} is out of range [{lo}, {hi}].")
        validated[k] = v

    payload = struct.pack("<5f", *(validated[k] for k in FIELDS_RW))
    data = list(payload)
    cmd = hwm.build_command(opcode=AE_ACCEL_PARAMS_OPCODE, param1=PARAM1_SET, data=data)
    try:
        hwm.send_and_receive_raw_data(cmd)
    except Exception as e:
        sys.exit(f"SET AE_ACCEL_PARAMS failed (is the depth sensor streaming?): {e}")
    return get_params(hwm)


def parse_fw_version(fw_str):
    try:
        return tuple(int(x) for x in fw_str.split("."))
    except (ValueError, AttributeError):
        return None


def guard_device(device):
    product_line = device.get_info(rs.camera_info.product_line)
    if product_line != REQUIRED_PRODUCT_LINE:
        sys.exit(f"AE_ACCEL_PARAMS is D400-only (opcode 0x95 on D500 is SAFETY_PRESET_WRITE). "
                 f"Detected product line: {product_line}")

    fw_str = device.get_info(rs.camera_info.firmware_version)
    fw_tuple = parse_fw_version(fw_str)
    if fw_tuple is None or fw_tuple < MIN_FW_VERSION:
        sys.exit(f"FW {fw_str} is below minimum {'.'.join(map(str, MIN_FW_VERSION))} "
                 f"required for AE_ACCEL_PARAMS.")


def print_params(label, params):
    print(f"\n{label}")
    for k in FIELDS_ALL:
        marker = "R/W" if k in FIELDS_RW else "R  "
        lo, hi = RANGES[k]
        print(f"  {marker}  {k:<20}= {params[k]:.4f}   (range [{lo}, {hi}])")


def parse_set_args(pairs):
    updates = {}
    for pair in pairs:
        if "=" not in pair:
            sys.exit(f"--set argument '{pair}' must be name=value.")
        name, val = pair.split("=", 1)
        try:
            updates[name.strip()] = float(val)
        except ValueError:
            sys.exit(f"'{val}' is not a float for {name}.")
    return updates


def main():
    p = argparse.ArgumentParser(description="AE_ACCEL_PARAMS HWM tuning utility")
    p.add_argument("--serial", help="Target a device by serial number")
    p.add_argument("--set", nargs="+", metavar="name=value",
                   help=f"Set one or more writable params ({', '.join(FIELDS_RW)})")
    args = p.parse_args()

    device = pick_device(args.serial)
    name   = device.get_info(rs.camera_info.name)
    serial = device.get_info(rs.camera_info.serial_number)
    fw     = device.get_info(rs.camera_info.firmware_version)
    print(f"Device: {name}  SN={serial}  FW={fw}")

    guard_device(device)

    hwm = device.as_debug_protocol()
    if hwm is None:
        sys.exit("Device does not expose a debug-protocol interface.")

    current = get_params(hwm)
    print_params("Current AE_ACCEL_PARAMS:", current)

    if args.set:
        updates = parse_set_args(args.set)
        print("\nApplying updates:", updates)
        new_values = set_params(hwm, current, updates)
        print_params("After SET (FW-recomputed values shown):", new_values)


if __name__ == "__main__":
    main()
