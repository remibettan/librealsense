#!/usr/bin/env python3
# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

"""
CUDA Performance Benchmark with Synthetic Data
Tests CUDA operations with varying input sizes to measure performance scaling
"""

import sys
import os

# Check NumPy version first to provide helpful error message
try:
    import numpy as np
    numpy_version = tuple(map(int, np.__version__.split('.')[:2]))
    if numpy_version[0] >= 2:
        print("=" * 70)
        print("WARNING: NumPy 2.x detected!")
        print("=" * 70)
        print(f"Current NumPy version: {np.__version__}")
        print("\nNumPy 2.x may cause compatibility issues with matplotlib")
        print("that was compiled with NumPy 1.x.")
        print("\nRecommended fix:")
        print("  pip install 'numpy<2' --force-reinstall")
        print("\nOr install all dependencies with:")
        print("  pip install -r cuda_benchmark_requirements.txt --force-reinstall")
        print("=" * 70)
        print("\nAttempting to continue anyway...")
        print()
except ImportError:
    print("ERROR: NumPy not found. Install with: pip install numpy")
    sys.exit(1)

import time
import threading
from collections import deque

# Try to import matplotlib
try:
    import matplotlib
    matplotlib.use('Agg')  # Use non-interactive backend to avoid display issues
    import matplotlib.pyplot as plt
except ImportError as e:
    print(f"ERROR: Failed to import matplotlib: {e}")
    print("\nThis is likely due to NumPy version incompatibility.")
    print("\nFix:")
    print("  pip install 'numpy<2' matplotlib --force-reinstall")
    sys.exit(1)
except AttributeError as e:
    print(f"ERROR: matplotlib failed to load: {e}")
    print("\nThis is a NumPy 2.x compatibility issue.")
    print("\nFix:")
    print("  pip install 'numpy<2' --force-reinstall")
    print("  pip install matplotlib --force-reinstall --no-deps")
    sys.exit(1)

# Try to import pynvml for GPU monitoring
try:
    import pynvml
    NVML_AVAILABLE = True
except ImportError:
    print("Warning: pynvml not installed. GPU load monitoring will not be available.")
    print("Install with: pip install nvidia-ml-py3")
    NVML_AVAILABLE = False

# Check for tegrastats (Jetson GPU monitoring)
import subprocess
import re
import select
TEGRASTATS_AVAILABLE = os.path.exists('/usr/bin/tegrastats')

try:
    import ctypes
    CUDA_AVAILABLE = True
    # Try to load CUDA runtime
    try:
        cuda = ctypes.CDLL('libcudart.so')
    except:
        try:
            cuda = ctypes.CDLL('libcudart.so.12')
        except:
            CUDA_AVAILABLE = False
except:
    CUDA_AVAILABLE = False


class GPUMonitor:
    """Monitor GPU utilization during benchmarks"""
    
    def __init__(self):
        self.monitoring = False
        self.gpu_loads = deque()
        self.timestamps = deque()
        self.monitor_thread = None
        self.handle = None
        self.monitoring_enabled = False
        self.error_shown = False
        self.use_tegrastats = False
        self.tegrastats_proc = None
        
        # Try NVML first (works on desktop GPUs)
        if NVML_AVAILABLE:
            try:
                pynvml.nvmlInit()
                self.handle = pynvml.nvmlDeviceGetHandleByIndex(0)
                gpu_name = pynvml.nvmlDeviceGetName(self.handle)
                
                # Test if GPU utilization monitoring actually works
                try:
                    util = pynvml.nvmlDeviceGetUtilizationRates(self.handle)
                    self.monitoring_enabled = True
                    print(f"✓ GPU Monitoring Enabled (NVML): {gpu_name}")
                    return
                except Exception as e:
                    # NVML doesn't support utilization on this GPU
                    print(f"ℹ GPU found ({gpu_name}) but NVML utilization not supported")
                    self.handle = None
                    
            except Exception as e:
                print(f"ℹ NVML initialization failed: {e}")
                self.handle = None
        
        # Fallback to tegrastats for Jetson devices
        if TEGRASTATS_AVAILABLE and not self.monitoring_enabled:
            try:
                # Test tegrastats by starting and quickly reading output
                test_proc = subprocess.Popen(
                    ['tegrastats', '--interval', '100'],
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True
                )
                
                # Read first line with timeout
                ready, _, _ = select.select([test_proc.stdout], [], [], 2.0)
                
                if ready:
                    line = test_proc.stdout.readline()
                    test_proc.terminate()
                    test_proc.wait(timeout=1)
                    
                    if 'GR3D' in line or 'RAM' in line:  # Valid tegrastats output
                        self.use_tegrastats = True
                        self.monitoring_enabled = True
                        print("✓ GPU Monitoring Enabled (tegrastats): NVIDIA Jetson")
                        print("  Using tegrastats for GPU utilization monitoring")
                        return
                else:
                    test_proc.terminate()
                    test_proc.wait(timeout=1)
                    
            except Exception as e:
                print(f"ℹ tegrastats test failed: {e}")
        
        # No monitoring available
        if not self.monitoring_enabled:
            print("⚠ GPU load monitoring not available")
            print("  Benchmarks will run and measure execution times only.")
            if not NVML_AVAILABLE:
                print("  For desktop GPUs: pip install nvidia-ml-py3")
            if not TEGRASTATS_AVAILABLE:
                print("  For Jetson: tegrastats not found")
    
    def start_monitoring(self):
        """Start monitoring GPU load"""
        if not self.monitoring_enabled:
            return
        
        self.monitoring = True
        self.gpu_loads.clear()
        self.timestamps.clear()
        self.monitor_thread = threading.Thread(target=self._monitor_loop)
        self.monitor_thread.daemon = True
        self.monitor_thread.start()
    
    def stop_monitoring(self):
        """Stop monitoring and return collected data"""
        self.monitoring = False
        
        # Stop tegrastats process if running
        if self.tegrastats_proc:
            try:
                self.tegrastats_proc.terminate()
                self.tegrastats_proc.wait(timeout=1)
            except:
                pass
            self.tegrastats_proc = None
        
        if self.monitor_thread:
            self.monitor_thread.join(timeout=1.0)
        return list(self.gpu_loads), list(self.timestamps)
    
    def _monitor_loop(self):
        """Monitoring loop running in separate thread"""
        if self.use_tegrastats:
            self._monitor_loop_tegrastats()
        elif self.handle:
            self._monitor_loop_nvml()
    
    def _monitor_loop_nvml(self):
        """NVML monitoring loop for desktop GPUs"""
        start_time = time.time()
        while self.monitoring:
            try:
                util = pynvml.nvmlDeviceGetUtilizationRates(self.handle)
                current_time = time.time() - start_time
                self.gpu_loads.append(util.gpu)
                self.timestamps.append(current_time)
                time.sleep(0.001)  # Sample every 1ms
            except Exception as e:
                if not self.error_shown:
                    print(f"⚠ GPU monitoring stopped: {e}")
                    self.error_shown = True
                self.monitoring_enabled = False
                break
    
    def _monitor_loop_tegrastats(self):
        """tegrastats monitoring loop for Jetson"""
        start_time = time.time()
        
        # Start tegrastats with output redirected
        try:
            self.tegrastats_proc = subprocess.Popen(
                ['tegrastats', '--interval', '10'],  # 10ms intervals
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                bufsize=1
            )
            
            # Pattern to match: GR3D_FREQ 45%@... or GR3D 45%@...
            gpu_pattern = re.compile(r'GR3D[_FREQ]*\s+(\d+)%')
            
            while self.monitoring and self.tegrastats_proc.poll() is None:
                try:
                    line = self.tegrastats_proc.stdout.readline()
                    if line:
                        match = gpu_pattern.search(line)
                        if match:
                            gpu_util = int(match.group(1))
                            current_time = time.time() - start_time
                            self.gpu_loads.append(gpu_util)
                            self.timestamps.append(current_time)
                except Exception as e:
                    if not self.error_shown:
                        print(f"⚠ tegrastats monitoring error: {e}")
                        self.error_shown = True
                    break
                    
        except Exception as e:
            if not self.error_shown:
                print(f"⚠ Failed to start tegrastats: {e}")
                self.error_shown = True
            self.monitoring_enabled = False
    
    def cleanup(self):
        """Cleanup NVML resources"""
        if NVML_AVAILABLE and self.handle:
            try:
                pynvml.nvmlShutdown()
            except:
                pass


class SyntheticCUDABenchmark:
    """Benchmark CUDA operations with synthetic data"""
    
    def __init__(self):
        self.gpu_monitor = GPUMonitor()
        self.results = {}
    
    def simulate_pointcloud_generation(self, width, height, iterations=50):
        """Simulate point cloud generation with varying resolutions"""
        print(f"\nBenchmarking Point Cloud ({width}x{height}, {iterations} iterations)...")
        
        results = []
        
        for i in range(iterations):
            # Generate synthetic depth data
            depth_data = np.random.randint(500, 5000, (height, width), dtype=np.uint16)
            
            # Simulate intrinsics
            fx, fy = width * 0.8, height * 0.8
            cx, cy = width / 2, height / 2
            
            # Start GPU monitoring
            self.gpu_monitor.start_monitoring()
            
            # Measure execution time - simulate point cloud calculation
            start = time.perf_counter()
            
            # Simulate deprojection calculation (vectorized)
            y_coords, x_coords = np.mgrid[0:height, 0:width]
            z = depth_data * 0.001  # Convert to meters
            x = (x_coords - cx) * z / fx
            y = (y_coords - cy) * z / fy
            points = np.stack([x, y, z], axis=-1)
            
            # Simulate some processing
            _ = points.reshape(-1, 3)
            
            end = time.perf_counter()
            
            # Stop GPU monitoring
            gpu_loads, timestamps = self.gpu_monitor.stop_monitoring()
            
            time_us = (end - start) * 1e6
            avg_gpu = np.mean(gpu_loads) if gpu_loads else 0
            peak_gpu = np.max(gpu_loads) if gpu_loads else 0
            
            results.append((time_us, avg_gpu, peak_gpu))
        
        return results
    
    def simulate_alignment(self, width, height, iterations=50):
        """Simulate frame alignment"""
        print(f"\nBenchmarking Alignment ({width}x{height}, {iterations} iterations)...")
        
        results = []
        
        for i in range(iterations):
            # Generate synthetic depth and color data
            depth_data = np.random.randint(500, 5000, (height, width), dtype=np.uint16)
            color_data = np.random.randint(0, 255, (height, width, 3), dtype=np.uint8)
            
            # Start GPU monitoring
            self.gpu_monitor.start_monitoring()
            
            # Measure execution time - simulate alignment
            start = time.perf_counter()
            
            # Simulate pixel mapping and warping
            scale_x, scale_y = 1.02, 0.98  # Simulate slight distortion
            shift_x, shift_y = 5, -3
            
            # Create coordinate grids
            y_coords, x_coords = np.mgrid[0:height, 0:width]
            new_x = (x_coords * scale_x + shift_x).astype(int)
            new_y = (y_coords * scale_y + shift_y).astype(int)
            
            # Clip coordinates
            new_x = np.clip(new_x, 0, width - 1)
            new_y = np.clip(new_y, 0, height - 1)
            
            # Simulate aligned output
            aligned = color_data[new_y, new_x]
            
            end = time.perf_counter()
            
            # Stop GPU monitoring
            gpu_loads, timestamps = self.gpu_monitor.stop_monitoring()
            
            time_us = (end - start) * 1e6
            avg_gpu = np.mean(gpu_loads) if gpu_loads else 0
            peak_gpu = np.max(gpu_loads) if gpu_loads else 0
            
            results.append((time_us, avg_gpu, peak_gpu))
        
        return results
    
    def simulate_yuy2_to_rgb(self, width, height, iterations=50):
        """Simulate YUY2 to RGB8 conversion"""
        print(f"\nBenchmarking YUY2→RGB8 ({width}x{height}, {iterations} iterations)...")
        
        results = []
        
        for i in range(iterations):
            # Generate synthetic YUY2 data (4 bytes per 2 pixels)
            yuy2_data = np.random.randint(0, 255, (height, width * 2), dtype=np.uint8)
            
            # Start GPU monitoring
            self.gpu_monitor.start_monitoring()
            
            # Measure execution time - simulate YUY2 to RGB conversion
            start = time.perf_counter()
            
            # Extract Y, U, V components
            y0 = yuy2_data[:, 0::4].astype(float)
            u = yuy2_data[:, 1::4].astype(float)
            y1 = yuy2_data[:, 2::4].astype(float)
            v = yuy2_data[:, 3::4].astype(float)
            
            # YUV to RGB conversion
            c = y0 - 16
            d = u - 128
            e = v - 128
            
            r = np.clip((298 * c + 409 * e + 128) // 256, 0, 255)
            g = np.clip((298 * c - 100 * d - 208 * e + 128) // 256, 0, 255)
            b = np.clip((298 * c + 516 * d + 128) // 256, 0, 255)
            
            rgb = np.stack([r, g, b], axis=-1).astype(np.uint8)
            
            end = time.perf_counter()
            
            # Stop GPU monitoring
            gpu_loads, timestamps = self.gpu_monitor.stop_monitoring()
            
            time_us = (end - start) * 1e6
            avg_gpu = np.mean(gpu_loads) if gpu_loads else 0
            peak_gpu = np.max(gpu_loads) if gpu_loads else 0
            
            results.append((time_us, avg_gpu, peak_gpu))
        
        return results
    
    def simulate_y8i_split(self, width, height, iterations=50):
        """Simulate Y8I to Y8+Y8 split"""
        print(f"\nBenchmarking Y8I→Y8+Y8 ({width}x{height}, {iterations} iterations)...")
        
        results = []
        
        for i in range(iterations):
            # Generate synthetic Y8I data (2 bytes per pixel pair)
            y8i_data = np.random.randint(0, 255, (height, width, 2), dtype=np.uint8)
            
            # Start GPU monitoring
            self.gpu_monitor.start_monitoring()
            
            # Measure execution time - simulate Y8I split
            start = time.perf_counter()
            
            # Split into left and right frames
            left = y8i_data[:, :, 0].copy()
            right = y8i_data[:, :, 1].copy()
            
            end = time.perf_counter()
            
            # Stop GPU monitoring
            gpu_loads, timestamps = self.gpu_monitor.stop_monitoring()
            
            time_us = (end - start) * 1e6
            avg_gpu = np.mean(gpu_loads) if gpu_loads else 0
            peak_gpu = np.max(gpu_loads) if gpu_loads else 0
            
            results.append((time_us, avg_gpu, peak_gpu))
        
        return results
    
    def simulate_y12i_to_y16(self, width, height, iterations=50):
        """Simulate Y12I to Y16+Y16 conversion"""
        print(f"\nBenchmarking Y12I→Y16+Y16 ({width}x{height}, {iterations} iterations)...")
        
        results = []
        
        for i in range(iterations):
            # Generate synthetic Y12I data (3 bytes per 2 pixels)
            pixels = width * height // 2
            y12i_data = np.random.randint(0, 255, pixels * 3, dtype=np.uint8)
            
            # Start GPU monitoring
            self.gpu_monitor.start_monitoring()
            
            # Measure execution time - simulate Y12I unpacking
            start = time.perf_counter()
            
            # Unpack 12-bit values
            byte0 = y12i_data[0::3].astype(np.uint16)
            byte1 = y12i_data[1::3].astype(np.uint16)
            byte2 = y12i_data[2::3].astype(np.uint16)
            
            # Extract left and right 12-bit values and expand to 16-bit
            left_12 = ((byte1 & 0x0F) << 8) | byte0
            right_12 = (byte2 << 4) | ((byte1 & 0xF0) >> 4)
            
            # Scale to 16-bit
            left_16 = (left_12 << 4) | (left_12 >> 8)
            right_16 = (right_12 << 4) | (right_12 >> 8)
            
            end = time.perf_counter()
            
            # Stop GPU monitoring
            gpu_loads, timestamps = self.gpu_monitor.stop_monitoring()
            
            time_us = (end - start) * 1e6
            avg_gpu = np.mean(gpu_loads) if gpu_loads else 0
            peak_gpu = np.max(gpu_loads) if gpu_loads else 0
            
            results.append((time_us, avg_gpu, peak_gpu))
        
        return results
    
    def run_multi_resolution_benchmark(self):
        """Run benchmarks at multiple resolutions"""
        resolutions = [
            (640, 480, "VGA"),
            (1280, 720, "HD"),
            (1920, 1080, "Full HD"),
        ]
        
        for width, height, name in resolutions:
            print(f"\n{'='*70}")
            print(f"Resolution: {name} ({width}x{height})")
            print(f"{'='*70}")
            
            key = f"pointcloud_{name.lower().replace(' ', '_')}"
            self.results[key] = self.simulate_pointcloud_generation(width, height, 50)
            
            key = f"align_{name.lower().replace(' ', '_')}"
            self.results[key] = self.simulate_alignment(width, height, 50)
            
            key = f"yuy2_rgb_{name.lower().replace(' ', '_')}"
            self.results[key] = self.simulate_yuy2_to_rgb(width, height, 50)
            
            key = f"y8i_split_{name.lower().replace(' ', '_')}"
            self.results[key] = self.simulate_y8i_split(width, height, 50)
            
            key = f"y12i_y16_{name.lower().replace(' ', '_')}"
            self.results[key] = self.simulate_y12i_to_y16(width, height, 50)
    
    def generate_graphs(self, output_dir='./'):
        """Generate graphs for all benchmark results"""
        print(f"\nGenerating graphs...")
        
        os.makedirs(output_dir, exist_ok=True)
        
        for operation, results in self.results.items():
            if not results:
                continue
            
            times = [r[0] for r in results]
            avg_loads = [r[1] for r in results]
            peak_loads = [r[2] for r in results]
            
            # Create figure with two subplots
            fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))
            fig.suptitle(f'CUDA Performance: {operation.replace("_", " ").title()}', 
                        fontsize=14, fontweight='bold')
            
            # Scatter plot: Time vs Average GPU Load
            ax1.scatter(times, avg_loads, alpha=0.6, s=30, color='blue')
            ax1.set_xlabel('Process Time (microseconds)', fontsize=11)
            ax1.set_ylabel('Average GPU Load (%)', fontsize=11)
            ax1.set_title('Time vs Average GPU Load')
            ax1.grid(True, alpha=0.3)
            
            # Add statistics text
            stats_text = f'Mean: {np.mean(times):.1f} μs\nStd: {np.std(times):.1f} μs'
            ax1.text(0.02, 0.98, stats_text, transform=ax1.transAxes,
                    verticalalignment='top', fontsize=9,
                    bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))
            
            # Scatter plot: Time vs Peak GPU Load
            ax2.scatter(times, peak_loads, alpha=0.6, s=30, color='orange')
            ax2.set_xlabel('Process Time (microseconds)', fontsize=11)
            ax2.set_ylabel('Peak GPU Load (%)', fontsize=11)
            ax2.set_title('Time vs Peak GPU Load')
            ax2.grid(True, alpha=0.3)
            
            # Add statistics text
            stats_text = f'Mean GPU: {np.mean(avg_loads):.1f}%\nPeak GPU: {np.mean(peak_loads):.1f}%'
            ax2.text(0.02, 0.98, stats_text, transform=ax2.transAxes,
                    verticalalignment='top', fontsize=9,
                    bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))
            
            plt.tight_layout()
            
            # Save figure
            filename = os.path.join(output_dir, f'cuda_synthetic_{operation}.png')
            plt.savefig(filename, dpi=150, bbox_inches='tight')
            print(f"  Saved: {filename}")
            plt.close()
        
        # Generate comparison graphs
        self.generate_comparison_graphs(output_dir)
    
    def generate_comparison_graphs(self, output_dir='./'):
        """Generate comparison graphs across resolutions"""
        print("  Generating comparison graphs...")
        
        # Group results by operation type
        operations = ['pointcloud', 'align', 'yuy2_rgb', 'y8i_split', 'y12i_y16']
        resolutions = ['vga', 'hd', 'full_hd']
        
        for op in operations:
            fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))
            fig.suptitle(f'Resolution Comparison: {op.replace("_", " ").title()}',
                        fontsize=14, fontweight='bold')
            
            colors = ['blue', 'green', 'red']
            
            for i, res in enumerate(resolutions):
                key = f"{op}_{res}"
                if key in self.results:
                    times = [r[0] for r in self.results[key]]
                    avg_loads = [r[1] for r in self.results[key]]
                    
                    ax1.scatter(times, avg_loads, alpha=0.6, s=20, 
                              color=colors[i], label=res.upper().replace('_', ' '))
            
            ax1.set_xlabel('Process Time (microseconds)', fontsize=11)
            ax1.set_ylabel('Average GPU Load (%)', fontsize=11)
            ax1.set_title('Time vs GPU Load (All Resolutions)')
            ax1.legend()
            ax1.grid(True, alpha=0.3)
            
            # Bar chart: Average times by resolution
            res_times = []
            res_labels = []
            for res in resolutions:
                key = f"{op}_{res}"
                if key in self.results:
                    times = [r[0] for r in self.results[key]]
                    res_times.append(np.mean(times))
                    res_labels.append(res.upper().replace('_', ' '))
            
            if res_times:
                bars = ax2.bar(res_labels, res_times, color=colors[:len(res_times)])
                ax2.set_ylabel('Average Time (microseconds)', fontsize=11)
                ax2.set_title('Average Processing Time by Resolution')
                ax2.grid(True, alpha=0.3, axis='y')
                
                # Add value labels on bars
                for bar in bars:
                    height = bar.get_height()
                    ax2.text(bar.get_x() + bar.get_width()/2., height,
                            f'{height:.1f}',
                            ha='center', va='bottom', fontsize=10)
            
            plt.tight_layout()
            filename = os.path.join(output_dir, f'cuda_comparison_{op}.png')
            plt.savefig(filename, dpi=150, bbox_inches='tight')
            print(f"  Saved: {filename}")
            plt.close()
    
    def cleanup(self):
        """Cleanup resources"""
        self.gpu_monitor.cleanup()


def main():
    """Main benchmark execution"""
    print("=" * 70)
    print("RealSense CUDA Synthetic Benchmark")
    print("=" * 70)
    print("\nThis script simulates CUDA operations with synthetic data")
    print("to measure performance scaling across different resolutions.")
    print("\nNote: This uses CPU to simulate the operations.")
    print("For actual CUDA benchmarking, use cuda_benchmark.py with a camera.")
    print("=" * 70)
    print()
    
    benchmark = SyntheticCUDABenchmark()
    
    # Show GPU monitoring status
    if benchmark.gpu_monitor.monitoring_enabled:
        print("GPU load will be measured during benchmarks.")
    else:
        print("Note: GPU load monitoring is disabled (benchmarks will still run).")
        if not NVML_AVAILABLE:
            print("      Install nvidia-ml-py3: pip install nvidia-ml-py3")
    print()
    
    try:
        # Run multi-resolution benchmarks
        benchmark.run_multi_resolution_benchmark()
        
        # Generate graphs
        output_dir = './cuda_benchmark_synthetic'
        benchmark.generate_graphs(output_dir)
        
        print("\n" + "=" * 70)
        print("Benchmark completed successfully!")
        print(f"Results saved to: {output_dir}/")
        if not benchmark.gpu_monitor.monitoring_enabled:
            print("\nNote: GPU load data is not available (monitoring was disabled).")
            print("      Graphs show only execution times.")
        print("=" * 70)
        
        return 0
        
    except KeyboardInterrupt:
        print("\n\nBenchmark interrupted by user")
        return 1
    except Exception as e:
        print(f"\nError during benchmark: {e}")
        import traceback
        traceback.print_exc()
        return 1
    finally:
        benchmark.cleanup()


if __name__ == "__main__":
    sys.exit(main())
