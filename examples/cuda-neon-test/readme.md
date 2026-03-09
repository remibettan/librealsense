# CUDA+NEON Hybrid Acceleration Test

This example tests and validates the CUDA+NEON hybrid acceleration feature for pointcloud processing on ARM platforms with CUDA support.

## Purpose

When building librealsense with CUDA on ARM platforms (like NVIDIA Jetson), this test verifies that:
- **CUDA acceleration** is used for `depth_to_points()` method
- **NEON acceleration** is used for `get_texture_map()` method (which doesn't have a CUDA implementation)

This hybrid approach ensures optimal performance by using the best available acceleration for each method, rather than falling back to generic C++ for methods not implemented in CUDA.

## Building

### Prerequisites
- ARM platform with NEON support (e.g., NVIDIA Jetson)
- CUDA toolkit installed
- RealSense camera connected

### Build with CUDA support
```bash
cd librealsense
mkdir build && cd build
cmake .. -DBUILD_EXAMPLES=ON -DBUILD_WITH_CUDA=ON -DCMAKE_BUILD_TYPE=Release
make rs-cuda-neon-test
```

## Running

```bash
./examples/cuda-neon-test/rs-cuda-neon-test
```

The test will:
1. Display the build configuration (CUDA, NEON, SSSE3 status)
2. Connect to a RealSense camera
3. Process 100 frames using the pointcloud API
4. Display performance metrics
5. Confirm which acceleration paths are being used

## Expected Output

On an ARM platform with CUDA enabled:
```
=== Build Configuration ===
CUDA support: ENABLED
NEON support: ENABLED

=== Expected Behavior ===
Pointcloud implementation: CUDA+NEON hybrid
  - depth_to_points: CUDA accelerated
  - get_texture_map: NEON accelerated (inherited)
==============================

... processing frames ...

=== Performance Summary ===
Frames processed: 100
Average calculate time: X.XX ms/frame
Throughput: XX.X fps

=== Test Results ===
✓ CUDA+NEON hybrid is active
✓ depth_to_points: Using CUDA acceleration
✓ get_texture_map: Using NEON acceleration

This confirms that NEON optimizations are used as fallback
for methods not accelerated by CUDA!
```

## Implementation Details

The hybrid implementation is achieved by conditional inheritance in `pointcloud_cuda`:
- On ARM+CUDA builds: `pointcloud_cuda` inherits from `pointcloud_neon`
- This allows CUDA to override `depth_to_points()` while inheriting `get_texture_map()` from NEON
- SSSE3 remains completely unchanged

See:
- `src/proc/cuda/cuda-pointcloud.h` - Conditional inheritance
- `src/proc/cuda/cuda-pointcloud.cpp` - CUDA implementation
- `src/proc/neon/neon-pointcloud.cpp` - NEON implementation (inherited)

## Troubleshooting

**If CUDA is not detected:**
- Ensure CUDA toolkit is properly installed
- Check that `-DBUILD_WITH_CUDA=ON` was used during CMake configuration

**If NEON is not detected:**
- This test requires an ARM platform with NEON support
- Ensure you're compiling natively on ARM (not cross-compiling)
- Check compiler flags include ARM NEON extensions

**No camera found:**
- Ensure RealSense camera is connected and recognized
- Check `lsusb` or equivalent to verify USB connection
- Run with elevated permissions if needed
