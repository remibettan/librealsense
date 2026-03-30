# CUDA Benchmark Quick Start Guide

## 🚀 Quick Start

### Option 1: Interactive Launcher (Easiest)
```bash
cd ~/librealsense/examples
./run_cuda_benchmark.sh
```
Follow the interactive menu to choose your benchmark type.

### Option 2: Direct Execution

**With RealSense Camera:**
```bash
cd ~/librealsense/examples
python3 cuda_benchmark.py
```

**Without Camera (Synthetic Data):**
```bash
cd ~/librealsense/examples
python3 cuda_benchmark_synthetic.py
```

---

## 📦 Installation

### 1. Install Python Dependencies
```bash
cd ~/librealsense/examples
pip install -r cuda_benchmark_requirements.txt
```

Or manually:
```bash
pip install "numpy<2" matplotlib nvidia-ml-py3
```

**Important:** NumPy 2.x causes compatibility issues with matplotlib and pyrealsense2.
If you encounter NumPy-related errors, run the fix script:
```bash
cd ~/librealsense/examples
chmod +x fix_numpy_compatibility.sh
./fix_numpy_compatibility.sh
```

### 2. Verify CUDA Build
```bash
cd ~/librealsense/build
grep RS2_USE_CUDA CMakeCache.txt
```
Should show: `RS2_USE_CUDA:BOOL=ON`

If not, rebuild with CUDA:
```bash
cd ~/librealsense/build
cmake .. -DBUILD_WITH_CUDA=ON -DBUILD_PYTHON_BINDINGS=ON
make -j$(nproc)
```

---

## 📊 Viewing Results

### Graphs
Results are saved as PNG files in:
- `cuda_benchmark_results/` (live camera)
- `cuda_benchmark_synthetic/` (synthetic data)

Open with any image viewer:
```bash
cd ~/librealsense/examples
eog cuda_benchmark_results/*.png  # or xdg-open
```

### Summary Statistics
Text reports are in the results directories:
```bash
cat cuda_benchmark_results/cuda_benchmark_summary.txt
```

### Analysis Tool
Compare and analyze results:
```bash
python3 cuda_benchmark_analyzer.py
```

---

## 🎯 What Gets Measured

Each benchmark measures:
- **Execution Time** (microseconds): How long each operation takes
- **GPU Load** (percentage): GPU utilization during execution
  - Average GPU load across the operation
  - Peak GPU load during operation

### Operations Benchmarked

| Operation | Description |
|-----------|-------------|
| Point Cloud | Depth → 3D point cloud conversion |
| Align D→C | Align depth frame to color frame |
| Align C→D | Align color frame to depth frame |
| YUY2→RGB8 | Color format conversion |
| Y8I→Y8+Y8 | Stereo infrared split |
| Y12I→Y16+Y16 | 12-bit stereo to 16-bit conversion |

---

## 🔍 Interpreting Results

### Graph Types

**1. Time vs Average GPU Load**
- Shows correlation between execution time and GPU utilization
- High GPU load + long time = GPU compute-bound
- Low GPU load + long time = Memory/CPU bottleneck

**2. Time vs Peak GPU Load**
- Shows maximum GPU usage during operation
- Helps identify burst performance

### Good Performance Indicators
- ✅ Tight clustering of points (consistent performance)
- ✅ High GPU utilization (60%+) for compute operations
- ✅ Low variance in execution times

### Performance Issues to Investigate
- ⚠️ Low GPU utilization (<30%) with long times
- ⚠️ High variance in execution times
- ⚠️ Outliers with extreme times

---

## 🛠️ Troubleshooting

### NumPy 2.x Compatibility Error
**Problem:** Error about "NumPy 1.x cannot be run in NumPy 2.x" or "_ARRAY_API not found"

**Symptoms:**
```
AttributeError: _ARRAY_API not found
ImportError: numpy.core.multiarray failed to import
```

**Quick Fix:**
```bash
cd ~/librealsense/examples
./fix_numpy_compatibility.sh
```

**Manual Fix:**
```bash
pip install "numpy<2" --force-reinstall
pip install matplotlib --force-reinstall
```

**Explanation:** matplotlib and pyrealsense2 were compiled with NumPy 1.x.
NumPy 2.0+ introduced breaking changes that cause compatibility issues.
Downgrading to NumPy 1.x (1.19-1.26) resolves this.

---

### GPU Load Shows 0% or "Not Supported"
**Problem:** GPU monitoring isn't working

**Symptoms:**
```
Error monitoring GPU: Not Supported
⚠ GPU found but monitoring not supported
```

**Causes:**
1. **nvidia-ml-py3 not installed**
   ```bash
   pip install nvidia-ml-py3
   ```

2. **NVIDIA driver too old or not working**
   ```bash
   nvidia-smi  # Should show GPU info
   ```

3. **Permissions issue**
   ```bash
   sudo nvidia-smi  # Try with sudo
   ```

4. **VM or WSL without GPU passthrough**
   - Virtual machines may not support GPU monitoring
   - WSL2 requires proper GPU configuration

5. **Old GPU or driver doesn't support utilization queries**
   - Some older GPUs don't expose utilization metrics
   - **Jetson devices**: NVML doesn't work, but tegrastats is used automatically

**Jetson Users**: The script automatically uses `tegrastats` for GPU monitoring on Jetson platforms (Orin, Xavier, TX2, Nano). If you see "GPU Monitoring Enabled (tegrastats)", it's working correctly!

**Note:** The benchmark will still run and measure execution times.
GPU load data is optional - you'll get time-only graphs without it.

**Workaround:** Run the benchmark anyway:
```bash
python3 cuda_benchmark.py
# Graphs will show execution times (GPU load will be 0)
```

---
**Problem:** GPU monitoring not working

**Solution:**
```bash
pip install nvidia-ml-py3
nvidia-smi  # Verify this works
```

### "No camera connected"
**Problem:** Camera not detected (only affects live benchmark)

**Solutions:**
1. Check camera connection
2. Run `rs-enumerate-devices` to verify
3. Use synthetic benchmark instead: `python3 cuda_benchmark_synthetic.py`

### Slow Performance / No CUDA Acceleration
**Problem:** CUDA not enabled or not being used

**Check:**
```bash
# Verify CUDA build
cd ~/librealsense/build
grep RS2_USE_CUDA CMakeCache.txt

# Should show ON
```

**Fix:**
```bash
cd ~/librealsense/build
cmake .. -DBUILD_WITH_CUDA=ON
make -j$(nproc)
```

### Import Error: pyrealsense2
**Problem:** Python bindings not installed or not found

**Automatic Fallback:** The script automatically searches for pyrealsense2 in:
- Standard Python path
- `~/librealsense/build/Release/`
- `~/librealsense/build/`
- Relative to the examples directory

**Fix (if not found):**
```bash
cd ~/librealsense/build
cmake .. -DBUILD_PYTHON_BINDINGS=ON
make -j$(nproc)

# The script will find it automatically in build/Release/
# OR install system-wide:
sudo make install
```

---

## 📈 Expected Results

Typical execution times (varies by GPU):

| Operation | 640x480 | 1280x720 | Notes |
|-----------|---------|----------|-------|
| Point Cloud | 0.5-2 ms | 2-8 ms | Resolution dependent |
| Alignment | 0.3-1.5 ms | 1.5-5 ms | Uses CUDA kernels |
| YUY2→RGB8 | 0.1-0.5 ms | 0.5-2 ms | Memory bound |
| Y8I Split | 0.05-0.2 ms | 0.2-0.8 ms | Simple split |

**GPU Load:** 40-80% typical for compute operations

---

## 💡 Tips

1. **Run Multiple Times**: Run benchmarks multiple times to account for variance
2. **Monitor GPU**: Use `nvidia-smi -l 1` in another terminal to watch GPU usage
3. **Close Other Apps**: Close other GPU-using applications for accurate results
4. **Check Temperature**: Ensure GPU isn't thermal throttling
5. **Compare Builds**: Benchmark before/after code changes to measure impact

---

## 📁 Files Created

| File | Purpose |
|------|---------|
| `cuda_benchmark.py` | Live camera benchmark |
| `cuda_benchmark_synthetic.py` | Synthetic data benchmark |
| `cuda_benchmark_analyzer.py` | Results analysis tool |
| `run_cuda_benchmark.sh` | Interactive launcher |
| `cuda_benchmark_requirements.txt` | Python dependencies |
| `CUDA_BENCHMARK_README.md` | Full documentation |
| `QUICKSTART.md` | This file |

---

## 🆘 Getting Help

1. Check full documentation: `CUDA_BENCHMARK_README.md`
2. Verify GPU: `nvidia-smi`
3. Verify camera: `rs-enumerate-devices`
4. Check logs in benchmark output directories

---

## ⚙️ Advanced

### Custom Iterations
Edit the scripts to change iteration count:
```python
iterations = 200  # Default: 100
```

### Custom Resolutions (Synthetic)
Edit `cuda_benchmark_synthetic.py`:
```python
resolutions = [
    (640, 480, "VGA"),
    (3840, 2160, "4K"),  # Add this
]
```

### Automated Testing
Run both benchmarks sequentially:
```bash
./run_cuda_benchmark.sh <<< "3"
```

---

**Ready to benchmark?** Start with:
```bash
cd ~/librealsense/examples
./run_cuda_benchmark.sh
```
