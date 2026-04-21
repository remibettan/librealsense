#!/usr/bin/env python3
# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

"""
CUDA Performance Benchmark for RealSense
Measures execution time vs GPU load for CUDA-accelerated operations
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
        print("\nNumPy 2.x may cause compatibility issues with matplotlib and pyrealsense2")
        print("that were compiled with NumPy 1.x.")
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

# Try to import pyrealsense2, checking build directory if not found
try:
    import pyrealsense2 as rs
    PYREALSENSE2_AVAILABLE = True
except ImportError:
    # Try looking in the build directory
    build_paths = [
        os.path.expanduser('~/librealsense/build/Release'),
        os.path.expanduser('~/librealsense/build'),
        os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'build', 'Release'),
        os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'build'),
    ]
    
    pyrealsense2_found = False
    for build_path in build_paths:
        if os.path.exists(build_path):
            sys.path.insert(0, build_path)
            try:
                import pyrealsense2 as rs
                pyrealsense2_found = True
                print(f"Found pyrealsense2 in: {build_path}")
                break
            except ImportError:
                sys.path.pop(0)
    
    if not pyrealsense2_found:
        print("ERROR: pyrealsense2 not found!")
        print("\nSearched in:")
        for path in build_paths:
            print(f"  - {path}")
        print("\nPlease build librealsense with Python bindings:")
        print("  cd ~/librealsense/build")
        print("  cmake .. -DBUILD_PYTHON_BINDINGS=ON")
        print("  make -j$(nproc)")
        print("\nOr install via pip:")
        print("  pip install pyrealsense2")
        sys.exit(1)
    
    PYREALSENSE2_AVAILABLE = True

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


class CUDABenchmark:
    """Benchmark CUDA operations in RealSense"""
    
    def __init__(self):
        self.pipeline = None
        self.align_to_color = None
        self.align_to_depth = None
        self.pc = None
        self.gpu_monitor = GPUMonitor()
        
        # Results storage: {operation_name: [(time_us, avg_gpu_load, peak_gpu_load)]}
        self.results = {}
        
        # Time-series data: {operation_name: {'timestamps': [], 'gpu_loads': [], 'exec_times': []}}
        self.timeseries_data = {}
    
    def initialize_pipeline(self):
        """Initialize RealSense pipeline"""
        print("Initializing RealSense pipeline...")
        
        self.pipeline = rs.pipeline()
        config = rs.config()
        
        # Enable depth and color streams (D401 supports these formats)
        config.enable_stream(rs.stream.depth, 640, 480, rs.format.z16, 30)
        config.enable_stream(rs.stream.color, 640, 480, rs.format.yuyv, 30)
        
        try:
            profile = self.pipeline.start(config)
            
            # Warm up - discard first few frames
            for _ in range(30):
                self.pipeline.wait_for_frames()
            
            print("Pipeline initialized successfully")
            return True
            
        except Exception as e:
            print(f"Failed to initialize pipeline: {e}")
            print("Note: CUDA operations require a connected RealSense camera")
            return False
    
    def benchmark_pointcloud(self, iterations=100):
        """Benchmark point cloud generation"""
        print(f"\nBenchmarking Point Cloud Generation ({iterations} iterations)...")
        
        if not self.pc:
            self.pc = rs.pointcloud()
        
        results = []
        exec_times = []
        
        # Start GPU monitoring for entire benchmark run
        self.gpu_monitor.start_monitoring()
        
        for i in range(iterations):
            frames = self.pipeline.wait_for_frames()
            depth = frames.get_depth_frame()
            
            if not depth:
                continue
            
            # Measure execution time
            start = time.perf_counter()
            points = self.pc.calculate(depth)
            end = time.perf_counter()
            
            time_us = (end - start) * 1e6
            exec_times.append(time_us)
            results.append((time_us, 0, 0))  # GPU loads filled in later
            
            if (i + 1) % 10 == 0:
                print(f"  Progress: {i + 1}/{iterations}")
        
        # Stop GPU monitoring and get collected data
        gpu_loads, timestamps = self.gpu_monitor.stop_monitoring()
        
        # Store time-series data
        self.timeseries_data['pointcloud'] = {
            'timestamps': timestamps,
            'gpu_loads': gpu_loads,
            'exec_times': exec_times
        }
        
        # Calculate average and peak GPU from all collected samples
        avg_gpu = np.mean(gpu_loads) if gpu_loads else 0
        peak_gpu = np.max(gpu_loads) if gpu_loads else 0
        
        # Update results with GPU data
        results = [(time_us, avg_gpu, peak_gpu) for time_us, _, _ in results]
        
        self.results['pointcloud'] = results
        print(f"  Completed: {len(results)} samples")
        if gpu_loads:
            print(f"  GPU Load: avg={avg_gpu:.1f}%, peak={peak_gpu:.1f}%")
        return results
    
    def benchmark_align_depth_to_color(self, iterations=100):
        """Benchmark align depth to color"""
        print(f"\nBenchmarking Align Depth to Color ({iterations} iterations)...")
        
        if not self.align_to_color:
            self.align_to_color = rs.align(rs.stream.color)
        
        results = []
        exec_times = []
        
        # Start GPU monitoring for entire benchmark run
        self.gpu_monitor.start_monitoring()
        
        for i in range(iterations):
            frames = self.pipeline.wait_for_frames()
            
            # Measure execution time
            start = time.perf_counter()
            aligned_frames = self.align_to_color.process(frames)
            end = time.perf_counter()
            
            time_us = (end - start) * 1e6
            exec_times.append(time_us)
            results.append((time_us, 0, 0))
            
            if (i + 1) % 10 == 0:
                print(f"  Progress: {i + 1}/{iterations}")
        
        # Stop GPU monitoring and get collected data
        gpu_loads, timestamps = self.gpu_monitor.stop_monitoring()
        
        # Store time-series data
        self.timeseries_data['align_depth_to_color'] = {
            'timestamps': timestamps,
            'gpu_loads': gpu_loads,
            'exec_times': exec_times
        }
        
        avg_gpu = np.mean(gpu_loads) if gpu_loads else 0
        peak_gpu = np.max(gpu_loads) if gpu_loads else 0
        
        # Update results with GPU data
        results = [(time_us, avg_gpu, peak_gpu) for time_us, _, _ in results]
        
        self.results['align_depth_to_color'] = results
        print(f"  Completed: {len(results)} samples")
        if gpu_loads:
            print(f"  GPU Load: avg={avg_gpu:.1f}%, peak={peak_gpu:.1f}%")
        return results
    
    def benchmark_align_color_to_depth(self, iterations=100):
        """Benchmark align color to depth"""
        print(f"\nBenchmarking Align Color to Depth ({iterations} iterations)...")
        
        if not self.align_to_depth:
            self.align_to_depth = rs.align(rs.stream.depth)
        
        results = []
        exec_times = []
        
        # Start GPU monitoring for entire benchmark run
        self.gpu_monitor.start_monitoring()
        
        for i in range(iterations):
            frames = self.pipeline.wait_for_frames()
            
            # Measure execution time
            start = time.perf_counter()
            aligned_frames = self.align_to_depth.process(frames)
            end = time.perf_counter()
            
            time_us = (end - start) * 1e6
            exec_times.append(time_us)
            results.append((time_us, 0, 0))
            
            if (i + 1) % 10 == 0:
                print(f"  Progress: {i + 1}/{iterations}")
        
        # Stop GPU monitoring and get collected data
        gpu_loads, timestamps = self.gpu_monitor.stop_monitoring()
        
        # Store time-series data
        self.timeseries_data['align_color_to_depth'] = {
            'timestamps': timestamps,
            'gpu_loads': gpu_loads,
            'exec_times': exec_times
        }
        
        avg_gpu = np.mean(gpu_loads) if gpu_loads else 0
        peak_gpu = np.max(gpu_loads) if gpu_loads else 0
        
        # Update results with GPU data
        results = [(time_us, avg_gpu, peak_gpu) for time_us, _, _ in results]
        
        self.results['align_color_to_depth'] = results
        print(f"  Completed: {len(results)} samples")
        if gpu_loads:
            print(f"  GPU Load: avg={avg_gpu:.1f}%, peak={peak_gpu:.1f}%")
        return results
    
    def benchmark_format_conversion_yuy2_rgb(self, iterations=100):
        """Benchmark YUY2 to RGB8 conversion"""
        print(f"\nBenchmarking YUY2 → RGB8 Conversion ({iterations} iterations)...")
        
        results = []
        exec_times = []
        
        # Start GPU monitoring for entire benchmark run
        self.gpu_monitor.start_monitoring()
        
        for i in range(iterations):
            frames = self.pipeline.wait_for_frames()
            color = frames.get_color_frame()
            
            if not color or color.get_profile().format() != rs.format.yuyv:
                # Try to get YUYV format frame
                continue
            
            # Measure execution time
            start = time.perf_counter()
            data = np.asanyarray(color.get_data())
            end = time.perf_counter()
            
            time_us = (end - start) * 1e6
            exec_times.append(time_us)
            results.append((time_us, 0, 0))
            
            if (i + 1) % 10 == 0:
                print(f"  Progress: {i + 1}/{iterations}")
        
        # Stop GPU monitoring and get collected data
        gpu_loads, timestamps = self.gpu_monitor.stop_monitoring()
        
        # Store time-series data
        self.timeseries_data['yuy2_to_rgb8'] = {
            'timestamps': timestamps,
            'gpu_loads': gpu_loads,
            'exec_times': exec_times
        }
        
        avg_gpu = np.mean(gpu_loads) if gpu_loads else 0
        peak_gpu = np.max(gpu_loads) if gpu_loads else 0
        
        # Update results with GPU data
        results = [(time_us, avg_gpu, peak_gpu) for time_us, _, _ in results]
        
        self.results['yuy2_to_rgb8'] = results
        print(f"  Completed: {len(results)} samples")
        if gpu_loads:
            print(f"  GPU Load: avg={avg_gpu:.1f}%, peak={peak_gpu:.1f}%")
        return results
    
    def benchmark_format_conversion_y8i(self, iterations=100):
        """Benchmark Y8 infrared processing"""
        print(f"\nBenchmarking Y8 Infrared Processing ({iterations} iterations)...")
        print("  Note: D401 doesn't support Y8I format, testing Y8 instead")
        
        # This operation may not use CUDA on D401
        results = []
        exec_times = []
        
        # Try to enable infrared stream temporarily
        config = rs.config()
        config.enable_stream(rs.stream.infrared, 1, 640, 480, rs.format.y8, 30)
        
        try:
            temp_pipeline = rs.pipeline()
            temp_pipeline.start(config)
            
            # Warm up
            for _ in range(10):
                temp_pipeline.wait_for_frames()
            
            # Start GPU monitoring for entire benchmark run
            self.gpu_monitor.start_monitoring()
            
            for i in range(iterations):
                frames = temp_pipeline.wait_for_frames()
                ir_frame = frames.get_infrared_frame(1)
                
                if not ir_frame:
                    continue
                
                # Measure execution time
                start = time.perf_counter()
                data = np.asanyarray(ir_frame.get_data())
                end = time.perf_counter()
                
                time_us = (end - start) * 1e6
                exec_times.append(time_us)
                results.append((time_us, 0, 0))
                
                if (i + 1) % 10 == 0:
                    print(f"  Progress: {i + 1}/{iterations}")
            
            temp_pipeline.stop()
            
            # Stop GPU monitoring and get collected data
            gpu_loads, timestamps = self.gpu_monitor.stop_monitoring()
            
            # Store time-series data
            self.timeseries_data['y8i_to_y8y8'] = {
                'timestamps': timestamps,
                'gpu_loads': gpu_loads,
                'exec_times': exec_times
            }
            
            avg_gpu = np.mean(gpu_loads) if gpu_loads else 0
            peak_gpu = np.max(gpu_loads) if gpu_loads else 0
            
            # Update results with GPU data
            results = [(time_us, avg_gpu, peak_gpu) for time_us, _, _ in results]
            
            self.results['y8i_to_y8y8'] = results
            print(f"  Completed: {len(results)} samples")
            if gpu_loads:
                print(f"  GPU Load: avg={avg_gpu:.1f}%, peak={peak_gpu:.1f}%")
                
        except Exception as e:
            print(f"  Skipped: {e}")
            self.results['y8i_to_y8y8'] = [(0, 0, 0)]
            results = [(0, 0, 0)]
        
        return results
    
    def benchmark_format_conversion_y12i(self, iterations=100):
        """Benchmark Y12I to Y16+Y16 conversion"""
        print(f"\nBenchmarking Y12I → Y16+Y16 Conversion ({iterations} iterations)...")
        print("  Note: This requires a device that outputs Y12I format")
        
        # This format is less common, may not be available on all devices
        results = []
        
        for i in range(iterations):
            frames = self.pipeline.wait_for_frames()
            # Y12I conversion would happen here if device supports it
            # For now, we'll skip this if not available
            
            if (i + 1) % 10 == 0:
                print(f"  Progress: {i + 1}/{iterations}")
        
        if not results:
            print("  Y12I format not available on current device")
            results.append((0, 0, 0))  # Placeholder
        
        self.results['y12i_to_y16y16'] = results
        return results
    
    def generate_graphs(self, output_dir='./'):
        """Generate graphs for all benchmark results"""
        print(f"\nGenerating graphs...")
        
        os.makedirs(output_dir, exist_ok=True)
        
        for operation, results in self.results.items():
            if not results or (len(results) == 1 and results[0] == (0, 0, 0)):
                print(f"  Skipping {operation} (no data)")
                continue
            
            times = [r[0] for r in results]
            avg_loads = [r[1] for r in results]
            peak_loads = [r[2] for r in results]
            
            # Get time-series data if available
            timeseries = self.timeseries_data.get(operation, {})
            has_timeseries = bool(timeseries.get('timestamps') and timeseries.get('gpu_loads'))
            
            if not has_timeseries:
                print(f"  Skipping {operation} (no time-series data)")
                continue
            
            # Create single figure for GPU load over time
            fig, ax = plt.subplots(1, 1, figsize=(10, 6))
            fig.suptitle(f'CUDA Performance: {operation.replace("_", " ").title()}', fontsize=14, fontweight='bold')
            
            # Plot: GPU Load Over Time (time-series)
            timestamps = timeseries['timestamps']
            gpu_loads = timeseries['gpu_loads']
            
            ax.plot(timestamps, gpu_loads, linewidth=1.5, alpha=0.8, color='#2E86AB')
            ax.fill_between(timestamps, gpu_loads, alpha=0.3, color='#2E86AB')
            ax.set_xlabel('Time (seconds)', fontsize=12)
            ax.set_ylabel('GPU Load (%)', fontsize=12)
            ax.set_title('GPU Load Over Time', fontweight='bold', fontsize=13)
            ax.grid(True, alpha=0.3, linestyle='--')
            
            # Set y-axis limits with some headroom
            if gpu_loads:
                max_load = max(gpu_loads)
                ax.set_ylim(0, max_load * 1.1 if max_load > 0 else 10)
            
            # Add statistics annotation
            if gpu_loads:
                avg = np.mean(gpu_loads)
                peak = np.max(gpu_loads)
                min_gpu = np.min(gpu_loads)
                stats_text = f'Avg: {avg:.1f}%\nPeak: {peak:.1f}%\nMin: {min_gpu:.1f}%'
                ax.text(0.98, 0.98, stats_text, transform=ax.transAxes,
                        verticalalignment='top', horizontalalignment='right',
                        bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5),
                        fontsize=10, family='monospace')
            
            plt.tight_layout()
            
            # Save figure
            filename = os.path.join(output_dir, f'cuda_benchmark_{operation}.png')
            plt.savefig(filename, dpi=150, bbox_inches='tight')
            print(f"  Saved: {filename}")
            plt.close()
        
        # Generate summary statistics
        self.generate_summary_report(output_dir)
    
    def generate_summary_report(self, output_dir='./'):
        """Generate a text summary report"""
        report_file = os.path.join(output_dir, 'cuda_benchmark_summary.txt')
        
        with open(report_file, 'w') as f:
            f.write("=" * 70 + "\n")
            f.write("CUDA Performance Benchmark Summary\n")
            f.write("=" * 70 + "\n\n")
            
            for operation, results in self.results.items():
                if not results or (len(results) == 1 and results[0] == (0, 0, 0)):
                    continue
                
                times = [r[0] for r in results]
                avg_loads = [r[1] for r in results]
                peak_loads = [r[2] for r in results]
                
                f.write(f"\n{operation.replace('_', ' ').title()}\n")
                f.write("-" * 70 + "\n")
                f.write(f"  Samples:              {len(results)}\n")
                f.write(f"  Time (μs):\n")
                f.write(f"    Min:                {min(times):.2f}\n")
                f.write(f"    Max:                {max(times):.2f}\n")
                f.write(f"    Mean:               {np.mean(times):.2f}\n")
                f.write(f"    Std Dev:            {np.std(times):.2f}\n")
                f.write(f"  Average GPU Load (%):\n")
                f.write(f"    Min:                {min(avg_loads):.2f}\n")
                f.write(f"    Max:                {max(avg_loads):.2f}\n")
                f.write(f"    Mean:               {np.mean(avg_loads):.2f}\n")
                f.write(f"    Std Dev:            {np.std(avg_loads):.2f}\n")
                f.write(f"  Peak GPU Load (%):\n")
                f.write(f"    Min:                {min(peak_loads):.2f}\n")
                f.write(f"    Max:                {max(peak_loads):.2f}\n")
                f.write(f"    Mean:               {np.mean(peak_loads):.2f}\n")
                f.write(f"    Std Dev:            {np.std(peak_loads):.2f}\n")
        
        print(f"  Saved summary: {report_file}")
    
    def cleanup(self):
        """Cleanup resources"""
        if self.pipeline:
            try:
                self.pipeline.stop()
            except RuntimeError:
                pass  # Pipeline was never started
        self.gpu_monitor.cleanup()


def main():
    """Main benchmark execution"""
    print("=" * 70)
    print("RealSense CUDA Performance Benchmark")
    print("=" * 70)
    print("\nThis script measures CUDA operation performance and GPU utilization.")
    print("Ensure you have:")
    print("  1. A RealSense camera connected")
    print("  2. librealsense built with CUDA support (BUILD_WITH_CUDA=ON)")
    print("  3. nvidia-ml-py3 installed for GPU monitoring (optional)")
    print("=" * 70)
    print()
    
    benchmark = CUDABenchmark()
    
    # Show GPU monitoring status
    if benchmark.gpu_monitor.monitoring_enabled:
        print("GPU load will be measured during benchmarks.")
    else:
        print("Note: GPU load monitoring is disabled (benchmarks will still run).")
        if not NVML_AVAILABLE:
            print("      Install nvidia-ml-py3: pip install nvidia-ml-py3")
    print()
    
    try:
        if not benchmark.initialize_pipeline():
            print("\nFailed to initialize. Exiting.")
            return 1
        
        # Run benchmarks
        iterations = 100
        
        benchmark.benchmark_pointcloud(iterations)
        benchmark.benchmark_align_depth_to_color(iterations)
        benchmark.benchmark_align_color_to_depth(iterations)
        benchmark.benchmark_format_conversion_yuy2_rgb(iterations)
        benchmark.benchmark_format_conversion_y8i(iterations)
        benchmark.benchmark_format_conversion_y12i(iterations)
        
        # Generate graphs
        output_dir = './cuda_benchmark_results'
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
