# CUDA Performance Benchmarking Scripts

This directory contains scripts for benchmarking CUDA-accelerated operations in librealsense.

## Scripts

### 1. `cuda_benchmark.py` - Live Camera Benchmarking
Measures actual CUDA performance using a connected RealSense camera.

**Requirements:**
- Connected RealSense D400/D500 series camera
- librealsense built with CUDA support (`-DBUILD_WITH_CUDA=ON`)
- Python packages:
  ```bash
  pip install "numpy<2" matplotlib nvidia-ml-py3
  ```
  **Note:** Use NumPy 1.x to avoid compatibility issues with matplotlib and pyrealsense2.

  If you have NumPy errors, run the fix script:
  ```bash
  cd ~/librealsense/examples
  chmod +x fix_numpy_compatibility.sh
  ./fix_numpy_compatibility.sh
  ```

**Usage:**
```bash
cd ~/librealsense/examples
python3 cuda_benchmark.py
```

**Operations Tested:**
- Point cloud generation
- Align depth to color
- Align color to depth  
- YUY2 → RGB8 format conversion
- Y8I → Y8+Y8 stereo split
- Y12I → Y16+Y16 stereo conversion

**Output:**
- `cuda_benchmark_results/` directory containing:
  - PNG graphs for each operation (time vs GPU load)
  - `cuda_benchmark_summary.txt` with statistics

---

### 2. `cuda_benchmark_synthetic.py` - Synthetic Data Benchmarking
Tests performance scaling with synthetic data at multiple resolutions (no camera required).

**Requirements:**
- Python packages:
  ```bash
  pip install numpy matplotlib nvidia-ml-py3
  ```

**Usage:**
```bash
cd ~/librealsense/examples
python3 cuda_benchmark_synthetic.py
```

**Test Resolutions:**
- VGA (640x480)
- HD (1280x720)
- Full HD (1920x1080)

**Output:**
- `cuda_benchmark_synthetic/` directory containing:
  - Individual operation graphs per resolution
  - Comparison graphs across resolutions

---

## Understanding the Output

### Graphs Generated

Each operation produces two scatter plots:

1. **Process Time vs Average GPU Load**
   - X-axis: Execution time in microseconds
   - Y-axis: Average GPU utilization during execution (%)
   - Shows how GPU load correlates with processing time

2. **Process Time vs Peak GPU Load**
   - X-axis: Execution time in microseconds  
   - Y-axis: Peak GPU utilization (%)
   - Shows maximum GPU usage during execution

### Graph Interpretation

- **Higher GPU Load with Longer Times**: CUDA operations are being utilized but may be compute-bound
- **Low GPU Load with Long Times**: Potential memory transfer bottleneck or CPU overhead
- **High Variance in Times**: May indicate scheduling issues or GPU contention
- **Tight Clustering**: Consistent performance, operation is well-optimized

### Summary Statistics

The `cuda_benchmark_summary.txt` file contains:
- Sample count
- Time statistics (min, max, mean, std dev)
- Average GPU load statistics
- Peak GPU load statistics

---

## Verifying CUDA is Being Used

To confirm CUDA acceleration is active:

1. Check build configuration:
   ```bash
   cd ~/librealsense/build
   grep RS2_USE_CUDA CMakeCache.txt
   ```
   Should show: `RS2_USE_CUDA:BOOL=ON`

2. Monitor GPU while running:
   ```bash
   # In another terminal
   nvidia-smi -l 1
   ```
   You should see GPU utilization spikes during benchmark runs.

3. Check for CUDA in logs:
   The benchmark script will log whether CUDA operations are being used.

---

## Troubleshooting

### NumPy 2.x Compatibility Issues

**Problem:** Errors like "_ARRAY_API not found" or "numpy.core.multiarray failed to import"

**Cause:** matplotlib and pyrealsense2 compiled with NumPy 1.x are incompatible with NumPy 2.x

**Solution (Automated):**
```bash
cd ~/librealsense/examples
chmod +x fix_numpy_compatibility.sh
./fix_numpy_compatibility.sh
```

**Solution (Manual):**
```bash
pip install "numpy<2" --force-reinstall
pip install matplotlib --force-reinstall
```

---

### GPU Load Always Shows 0%

**Cause:** `nvidia-ml-py3` not installed or GPU monitoring failed

**Solution:**
```bash
pip install nvidia-ml-py3
# Verify nvidia-smi works
nvidia-smi
```

### "No RealSense camera connected"

**Cause:** Camera not detected (only affects `cuda_benchmark.py`)

**Solutions:**
- Check USB connection
- Run `rs-enumerate-devices` to verify detection
- Try `cuda_benchmark_synthetic.py` instead (no camera required)

### Low/No GPU Utilization

**Cause:** CUDA not enabled in build

**Solution:** Rebuild with CUDA:
```bash
cd ~/librealsense/build
cmake .. -DBUILD_WITH_CUDA=ON
make -j$(nproc)
```

### Import Error: pyrealsense2

**Cause:** Python bindings not built or not in Python path

**Note:** The benchmark scripts automatically search for pyrealsense2 in:
- Standard Python path (if installed with `pip install pyrealsense2`)
- `~/librealsense/build/Release/` (built locally)
- `~/librealsense/build/` (built locally)

**Solution (if automatic detection fails):**
```bash
cd ~/librealsense/build
cmake .. -DBUILD_PYTHON_BINDINGS=ON
make -j$(nproc)

# Built module will be in build/Release/pyrealsense2.so
# Script will find it automatically, or install system-wide:
sudo make install
```

---

## Advanced Usage

### Custom Iteration Count

Edit the scripts to change the number of iterations:
```python
iterations = 200  # Default is 100
```

### Custom Resolutions (Synthetic Benchmark)

Edit `cuda_benchmark_synthetic.py`:
```python
resolutions = [
    (640, 480, "VGA"),
    (1920, 1080, "Full HD"),
    (3840, 2160, "4K"),  # Add custom resolution
]
```

### Real-time Monitoring

Run with GPU monitoring in another terminal:
```bash
watch -n 0.1 nvidia-smi
```

---

## Expected Performance

Typical execution times vary by GPU and resolution:

| Operation              | VGA (640x480) | HD (1280x720) | Note |
|------------------------|---------------|---------------|------|
| Point Cloud            | 500-2000 μs   | 2000-8000 μs  | Resolution-dependent |
| Align (D→C)            | 300-1500 μs   | 1500-5000 μs  | Depends on extrinsics |
| Align (C→D)            | 300-1500 μs   | 1500-5000 μs  | Similar to D→C |
| YUY2→RGB8              | 100-500 μs    | 500-2000 μs   | Memory bandwidth limited |
| Y8I→Y8+Y8              | 50-200 μs     | 200-800 μs    | Simple split |
| Y12I→Y16+Y16           | 100-400 μs    | 400-1500 μs   | Bit unpacking overhead |

**Note:** Actual times depend on GPU model, CUDA version, and system configuration.

---

## GPU Models Tested

These scripts have been validated on:
- **Desktop GPUs** (use NVML for monitoring):
  - NVIDIA RTX 30-series (Ampere)
  - NVIDIA RTX 40-series (Ada Lovelace)
  - NVIDIA GTX 16-series (Turing)
- **Jetson Platforms** (use tegrastats for monitoring):
  - Jetson Orin (AGX, NX, Nano)
  - Jetson Xavier (AGX, NX)
  - Jetson TX2
  - Jetson Nano

### Automatic Platform Detection

The scripts automatically detect your platform:
- **Desktop/Server GPUs**: Uses NVML (nvidia-ml-py3) for GPU utilization
- **Jetson Devices**: Uses `tegrastats` when NVML isn't supported
- **No GPU monitoring**: Benchmarks still run and measure execution times

---

## References

- [librealsense CUDA Documentation](https://github.com/IntelRealSense/librealsense/blob/master/doc/cuda.md)
- [Intel RealSense SDK](https://github.com/IntelRealSense/librealsense)
- CUDA implementation: `src/cuda/` and `src/proc/cuda/`

---

## License

Apache 2.0 - See LICENSE file in root directory.
Copyright(c) 2026 RealSense, Inc. All Rights Reserved.
