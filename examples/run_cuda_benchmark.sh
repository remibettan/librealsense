#!/bin/bash
# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

# CUDA Benchmark Launcher
# Interactive script to run CUDA performance benchmarks

set -e

echo "============================================================================"
echo "  RealSense CUDA Performance Benchmark Launcher"
echo "============================================================================"
echo ""

# Check if we're in the examples directory
if [ ! -f "cuda_benchmark.py" ]; then
    echo "Error: This script must be run from the examples directory"
    echo "Usage: cd ~/librealsense/examples && bash run_cuda_benchmark.sh"
    exit 1
fi

# Check Python
if ! command -v python3 &> /dev/null; then
    echo "Error: python3 not found. Please install Python 3."
    exit 1
fi

echo "Checking dependencies..."

# Check for required Python packages
MISSING_DEPS=()

if ! python3 -c "import numpy" 2>/dev/null; then
    MISSING_DEPS+=("numpy")
fi

if ! python3 -c "import matplotlib" 2>/dev/null; then
    MISSING_DEPS+=("matplotlib")
fi

if ! python3 -c "import pynvml" 2>/dev/null; then
    echo "Warning: nvidia-ml-py3 not installed (GPU monitoring will be limited)"
    echo "  Install with: pip install nvidia-ml-py3"
    MISSING_DEPS+=("nvidia-ml-py3")
fi

if [ ${#MISSING_DEPS[@]} -gt 0 ]; then
    echo ""
    echo "Missing Python packages: ${MISSING_DEPS[*]}"
    echo ""
    read -p "Install missing packages now? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        pip install --user "${MISSING_DEPS[@]}"
    else
        echo "Please install missing packages manually:"
        echo "  pip install ${MISSING_DEPS[*]}"
        exit 1
    fi
fi

echo "Dependencies OK"
echo ""

# Check for pyrealsense2 (needed for live camera benchmark)
PYREALSENSE2_AVAILABLE=false
if python3 -c "import pyrealsense2" 2>/dev/null; then
    PYREALSENSE2_AVAILABLE=true
    echo "✓ pyrealsense2 module found"
elif [ -f "$HOME/librealsense/build/Release/pyrealsense2.so" ] || [ -f "$HOME/librealsense/build/pyrealsense2.so" ]; then
    PYREALSENSE2_AVAILABLE=true
    echo "✓ pyrealsense2 found in build directory"
else
    echo "✗ pyrealsense2 not found"
    echo "  The live camera benchmark requires pyrealsense2"
    echo "  Build it with: cd ~/librealsense/build && cmake .. -DBUILD_PYTHON_BINDINGS=ON && make"
    echo "  The script will attempt to load it from ~/librealsense/build/Release/"
fi

# Check for camera (for live benchmark)
CAMERA_AVAILABLE=false
if command -v rs-enumerate-devices &> /dev/null; then
    if rs-enumerate-devices 2>/dev/null | grep -q "Device"; then
        CAMERA_AVAILABLE=true
        echo "✓ RealSense camera detected"
    else
        echo "✗ No RealSense camera detected"
    fi
else
    echo "? Cannot verify camera (rs-enumerate-devices not found)"
fi

# Check CUDA
CUDA_AVAILABLE=false
if command -v nvidia-smi &> /dev/null; then
    if nvidia-smi &> /dev/null; then
        CUDA_AVAILABLE=true
        echo "✓ NVIDIA GPU detected:"
        nvidia-smi --query-gpu=name,driver_version,memory.total --format=csv,noheader | head -1
    else
        echo "✗ nvidia-smi failed - check NVIDIA driver"
    fi
else
    echo "✗ nvidia-smi not found - NVIDIA GPU may not be available"
fi

echo ""
echo "============================================================================"
echo ""
echo "Select benchmark mode:"
echo ""
echo "  1) Live Camera Benchmark (requires RealSense camera)"
echo "     - Measures actual CUDA performance with real camera data"
echo "     - Tests: point cloud, alignment, format conversions"
echo "     - Requires: Connected RealSense D400/D500 camera"
echo ""
echo "  2) Synthetic Data Benchmark (no camera required)"
echo "     - Tests performance scaling across resolutions"
echo "     - Uses synthetic data to simulate operations"
echo "     - Runs: VGA, HD, Full HD tests"
echo ""
echo "  3) Both (run live benchmark then synthetic)"
echo ""
echo "  4) View README documentation"
echo ""
echo "  5) Exit"
echo ""

read -p "Enter choice [1-5]: " choice

case $choice in
    1)
        echo ""
        echo "Starting Live Camera Benchmark..."
        echo "============================================================================"
        if [ "$CAMERA_AVAILABLE" = false ]; then
            echo "WARNING: Camera not detected. Benchmark may fail."
            read -p "Continue anyway? (y/n) " -n 1 -r
            echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                exit 1
            fi
        fi
        echo ""
        python3 cuda_benchmark.py
        echo ""
        echo "============================================================================"
        echo "Results saved to: cuda_benchmark_results/"
        echo ""
        if [ -d "cuda_benchmark_results" ]; then
            echo "Generated files:"
            ls -lh cuda_benchmark_results/
        fi
        ;;
    
    2)
        echo ""
        echo "Starting Synthetic Data Benchmark..."
        echo "============================================================================"
        echo ""
        python3 cuda_benchmark_synthetic.py
        echo ""
        echo "============================================================================"
        echo "Results saved to: cuda_benchmark_synthetic/"
        echo ""
        if [ -d "cuda_benchmark_synthetic" ]; then
            echo "Generated files:"
            ls -lh cuda_benchmark_synthetic/
        fi
        ;;
    
    3)
        echo ""
        echo "Running both benchmarks..."
        echo ""
        
        # Run live benchmark
        if [ "$CAMERA_AVAILABLE" = true ]; then
            echo "============================================================================"
            echo "Part 1: Live Camera Benchmark"
            echo "============================================================================"
            echo ""
            python3 cuda_benchmark.py
            echo ""
        else
            echo "Skipping live benchmark (no camera detected)"
        fi
        
        # Run synthetic benchmark
        echo "============================================================================"
        echo "Part 2: Synthetic Data Benchmark"
        echo "============================================================================"
        echo ""
        python3 cuda_benchmark_synthetic.py
        echo ""
        
        echo "============================================================================"
        echo "All benchmarks completed!"
        echo ""
        echo "Results:"
        if [ -d "cuda_benchmark_results" ]; then
            echo "  Live camera: cuda_benchmark_results/"
        fi
        if [ -d "cuda_benchmark_synthetic" ]; then
            echo "  Synthetic:   cuda_benchmark_synthetic/"
        fi
        ;;
    
    4)
        echo ""
        if [ -f "CUDA_BENCHMARK_README.md" ]; then
            less CUDA_BENCHMARK_README.md
        else
            echo "README not found: CUDA_BENCHMARK_README.md"
        fi
        ;;
    
    5)
        echo "Exiting."
        exit 0
        ;;
    
    *)
        echo "Invalid choice. Exiting."
        exit 1
        ;;
esac

echo ""
echo "============================================================================"
echo "Benchmark complete!"
echo ""
echo "To view results:"
echo "  - Open PNG files in the results directory"
echo "  - Read the summary text file for statistics"
echo ""
echo "For more information, see: CUDA_BENCHMARK_README.md"
echo "============================================================================"
