# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

"""
Coverage for the CUDA zero-copy GPU frame path (RSDSO-21841 / RSDEV-12094).

Exercises the public GPU-frame API on a live frame:
  - frame.get_gpu_data_or_upload() always yields a usable device pointer on a CUDA build,
    with copied=False for a true zero-copy frame and copied=True for the upload fallback.
  - the gpu_frame extension (rs.gpu_frame(frame)) and the strict frame.get_gpu_data() through
    it resolve ONLY when the frame is GPU-resident, and are null/invalid otherwise.
  - the CPU-side frame data stays fully readable and the expected size, i.e. the mapped
    allocator behaves like the default one (allocator correctness).

Degrades gracefully (Definition of Done):
  - Non-CUDA / non-RS2_USE_CUDA_ZEROCOPY build: get_gpu_data_or_upload() returns None -> the
    test skips, so every existing (non-zero-copy) LibCI leg skips cleanly.
  - Discrete / non-integrated GPU (or a buffer that is not GPU-mapped): the path intentionally
    falls back to an upload (copied=True); the zero-copy-only assertions are gated off there,
    while the always-usable _or_upload contract and the null-safety of the strict API are
    still checked.
"""

import pytest
import numpy as np
import pyrealsense2 as rs
import logging
log = logging.getLogger(__name__)

pytestmark = pytest.mark.device("D400*")

WIDTH, HEIGHT, FPS = 640, 480, 30
WARMUP = 30  # exclude first frames (AWB / one-time CUDA init) before probing GPU data


def test_zero_copy_gpu_frame_path(test_device):
    dev, ctx = test_device
    if not hasattr(rs.frame, "get_gpu_data_or_upload") or not hasattr(rs, "gpu_frame"):
        pytest.skip("pyrealsense2 built without the GPU-frame API")

    pipe = rs.pipeline(ctx)
    cfg = rs.config()
    cfg.enable_device(dev.get_info(rs.camera_info.serial_number))
    cfg.enable_stream(rs.stream.depth, WIDTH, HEIGHT, rs.format.z16, FPS)
    pipe.start(cfg)
    try:
        f = None
        for _ in range(WARMUP + 10):
            f = pipe.wait_for_frames().get_depth_frame()
        assert f, "no depth frame received"

        # --- allocator correctness: size + full CPU-side readability ---
        # frame::data uses frame_data_allocator, which under zero-copy is CUDA host-mapped
        # memory. It must still report the same logical size and be readable end to end on the
        # CPU (touching the last element exercises the buffer right up to its tail).
        expected = f.get_stride_in_bytes() * f.get_height()
        assert f.get_data_size() == expected, \
            "get_data_size() {} != stride*height {} (allocator/size regression)".format(f.get_data_size(), expected)
        arr = np.asanyarray(f.get_data())
        assert arr.size > 0
        assert int(arr.flat[0]) >= 0 and int(arr.flat[-1]) >= 0  # read head + tail, no fault

        # --- get_gpu_data_or_upload(): always usable on a CUDA build ---
        r = f.get_gpu_data_or_upload()
        if r is None:
            pytest.skip("no CUDA / RS2_USE_CUDA_ZEROCOPY build: GPU device pointer unavailable")
        addr, copied = r
        assert isinstance(addr, int) and addr != 0, "expected a non-null CUDA device address"
        assert isinstance(copied, bool)

        # --- gpu_frame extension + strict get_gpu_data(): only when GPU-resident ---
        gf = rs.gpu_frame(f)
        strict = gf.get_gpu_data() if gf else None
        # The extension, the strict pointer, and the copied flag must all agree.
        assert bool(gf) == (strict is not None), "gpu_frame validity must match get_gpu_data() null-ness"
        assert bool(gf) == (not copied), "gpu_frame is reported iff the frame is GPU-resident (not uploaded)"

        if not copied:
            # true zero-copy (integrated GPU, GPU-mapped frame)
            assert strict == addr, "zero-copy: strict get_gpu_data() must equal the _or_upload address"
            log.info("zero-copy ACTIVE: device ptr 0x%x (no host->device copy)", addr)
        else:
            # CUDA build but the frame is not GPU-mapped (discrete GPU, or a non-mapped backend
            # buffer): the strict API is null and the _or_upload path uploaded a copy.
            assert strict is None, "strict get_gpu_data() must be null when the frame is not GPU-resident"
            log.info("zero-copy fell back to upload (frame not GPU-resident); _or_upload address 0x%x", addr)
    finally:
        pipe.stop()
