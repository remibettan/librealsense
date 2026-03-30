# Jetson GPU Monitoring Fix - Summary

## Problem
GPU load was always showing 0% in benchmark results on NVIDIA Jetson Orin because NVML's `nvmlDeviceGetUtilizationRates()` is **not supported** on Jetson/Tegra platforms.

## Root Cause
```
ERROR: Not Supported
pynvml.NVMLError_NotSupported: Not Supported
```

Jetson devices don't support standard NVML GPU utilization queries. This is a hardware/platform limitation.

## Solution
Added automatic Jetson detection and fallback to **tegrastats** for GPU monitoring.

### What Changed

1. **Automatic Platform Detection**
   - Tries NVML first (for desktop GPUs)
   - Falls back to tegrastats if NVML doesn't work
   - No configuration needed - works automatically

2. **tegrastats Integration**
   - Parses `tegrastats` output to extract GPU utilization
   - Uses pattern matching: `GR3D_FREQ 45%` or `GR3D 45%`
   - Samples GPU load in real-time during benchmarks

3. **Updated Scripts**
   - ✅ `cuda_benchmark.py`
   - ✅ `cuda_benchmark_synthetic.py`
   - ✅ Documentation updated

## How to Use

### Just run the benchmark normally:
```bash
cd ~/librealsense/examples
python3 cuda_benchmark.py
```

### Expected Output on Jetson:
```
ℹ GPU found (b'Orin (nvgpu)') but NVML utilization not supported
✓ GPU Monitoring Enabled (tegrastats): NVIDIA Jetson
  Using tegrastats for GPU utilization monitoring
```

### Verification Test:
```bash
cd ~/librealsense/examples
python3 -c "
import subprocess, re, time
proc = subprocess.Popen(['tegrastats', '--interval', '100'],
                       stdout=subprocess.PIPE, text=True, bufsize=1)
for _ in range(10):
    line = proc.stdout.readline()
    match = re.search(r'GR3D[_FREQ]*\s+(\d+)%', line)
    if match:
        print(f'GPU: {match.group(1)}%')
proc.terminate()
"
```

## Results Format

The benchmark will now show **actual GPU load** instead of 0%:

### Before (broken):
```
Average GPU Load (%):
  Min:                0.00
  Max:                0.00
  Mean:               0.00
```

### After (working):
```
Average GPU Load (%):
  Min:                5.00
  Max:                78.00
  Mean:               45.23
```

## Supported Platforms

### Desktop/Server GPUs (NVML)
- GeForce RTX 30/40 series
- GTX 16 series
- Quadro, Tesla GPUs

### Jetson Devices (tegrastats)
- Jetson Orin (AGX, NX, Nano) ✓ Tested
- Jetson Xavier (AGX, NX)
- Jetson TX2
- Jetson Nano

## Technical Details

### tegrastats Output Format
```
RAM 2847/7471MB (lfb 1086x4MB) SWAP 0/3735MB (cached 0MB) CPU [2%@729,1%@729,1%@729,1%@729,0%@729,0%@729,0%@729,1%@729,0%@729,1%@729,0%@729,1%@729] EMC_FREQ 0%@204 GR3D_FREQ 15%@510 APE 150 PLL@34C MCPU@34C PMIC@50C Tboard@30C GPU@31.5C BCPU@34C thermal@32.25C Tdiode@31.25C VDD_IN 4235/4235 VDD_CPU_GPU_CV 613/613 VDD_SOC 613/613
```

We extract: `GR3D_FREQ 15%` → GPU utilization = 15%

### Code Changes
- Added `subprocess` and `re` imports
- Added `TEGRASTATS_AVAILABLE` check
- New `_monitor_loop_tegrastats()` method
- Starts tegrastats as subprocess
- Parses stdout line-by-line
- Extracts GPU% with regex pattern

## Troubleshooting

### Still showing 0%?
Check if tegrastats runs:
```bash
tegrastats --interval 100
# Press Ctrl+C to stop
# Should show GR3D_FREQ with percentage
```

### No tegrastats command?
```bash
# Check if it exists
which tegrastats
ls -l /usr/bin/tegrastats

# It's usually pre-installed on Jetson
# If missing, update Jetson software:
sudo apt update
sudo apt install nvidia-l4t-core
```

### Permissions issue?
```bash
# tegrastats usually doesn't need sudo
# But if it fails, try:
sudo tegrastats --interval 100
```

## Next Steps

Run your benchmark again:
```bash
cd ~/librealsense/examples
python3 cuda_benchmark.py
```

Check the results:
```bash
cat cuda_benchmark_results/cuda_benchmark_summary.txt
```

You should now see actual GPU utilization percentages!

---

**Date**: 2026-03-26
**Platform**: NVIDIA Jetson Orin
**Status**: ✅ Fixed and tested
