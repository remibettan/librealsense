#!/bin/bash
# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

# Fix NumPy 2.x compatibility issues for CUDA benchmarks

echo "============================================================================"
echo "  NumPy Compatibility Fix for CUDA Benchmarks"
echo "============================================================================"
echo ""

# Check current NumPy version
NUMPY_VERSION=$(python3 -c "import numpy; print(numpy.__version__)" 2>/dev/null)

if [ $? -ne 0 ]; then
    echo "NumPy not found. Installing dependencies..."
else
    echo "Current NumPy version: $NUMPY_VERSION"
    
    # Check if it's version 2.x
    MAJOR_VERSION=$(echo "$NUMPY_VERSION" | cut -d. -f1)
    if [ "$MAJOR_VERSION" -ge 2 ]; then
        echo ""
        echo "⚠️  NumPy 2.x detected - this may cause compatibility issues"
        echo "   with matplotlib and pyrealsense2 compiled for NumPy 1.x"
        echo ""
    fi
fi

echo ""
echo "This script will:"
echo "  1. Downgrade NumPy to 1.x for compatibility"
echo "  2. Reinstall matplotlib to match NumPy version"
echo "  3. Install other required dependencies"
echo ""
read -p "Continue? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cancelled."
    exit 0
fi

echo ""
echo "Step 1: Installing NumPy 1.x..."
pip install "numpy<2" --force-reinstall

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to install NumPy"
    exit 1
fi

echo ""
echo "Step 2: Reinstalling matplotlib..."
pip install matplotlib --force-reinstall --no-deps

if [ $? -ne 0 ]; then
    echo "WARNING: Failed to reinstall matplotlib"
    echo "Trying with dependencies..."
    pip install matplotlib --force-reinstall
fi

echo ""
echo "Step 3: Installing other dependencies..."
pip install nvidia-ml-py3

echo ""
echo "============================================================================"
echo "Verifying installation..."
echo "============================================================================"
echo ""

# Verify NumPy
NUMPY_VERSION=$(python3 -c "import numpy; print(numpy.__version__)" 2>/dev/null)
if [ $? -eq 0 ]; then
    echo "✓ NumPy: $NUMPY_VERSION"
else
    echo "✗ NumPy: FAILED"
fi

# Verify matplotlib
MATPLOTLIB_VERSION=$(python3 -c "import matplotlib; print(matplotlib.__version__)" 2>/dev/null)
if [ $? -eq 0 ]; then
    echo "✓ matplotlib: $MATPLOTLIB_VERSION"
else
    echo "✗ matplotlib: FAILED"
fi

# Verify nvidia-ml-py3
PYNVML_VERSION=$(python3 -c "import pynvml; print('installed')" 2>/dev/null)
if [ $? -eq 0 ]; then
    echo "✓ nvidia-ml-py3: installed"
else
    echo "✗ nvidia-ml-py3: FAILED (optional - only needed for GPU monitoring)"
fi

# Try to verify pyrealsense2
PYREALSENSE2_VERSION=$(python3 -c "import sys; sys.path.insert(0, '$HOME/librealsense/build/Release'); import pyrealsense2; print('found')" 2>/dev/null)
if [ $? -eq 0 ]; then
    echo "✓ pyrealsense2: found"
else
    echo "⚠ pyrealsense2: not found (will be auto-detected from build directory)"
fi

echo ""
echo "============================================================================"
echo "Done!"
echo "============================================================================"
echo ""
echo "You can now run the CUDA benchmarks:"
echo "  cd ~/librealsense/examples"
echo "  python3 cuda_benchmark.py"
echo ""
