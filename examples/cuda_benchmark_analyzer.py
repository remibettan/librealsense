#!/usr/bin/env python3
# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

"""
CUDA Benchmark Results Analyzer
Analyzes and compares results from multiple benchmark runs
"""

import numpy as np
import matplotlib.pyplot as plt
import sys
import os
import glob
from datetime import datetime


class BenchmarkAnalyzer:
    """Analyze and compare benchmark results"""
    
    def __init__(self):
        self.result_dirs = []
        self.all_results = {}
    
    def load_results(self, directory):
        """Load results from a benchmark output directory"""
        print(f"Loading results from: {directory}")
        
        if not os.path.isdir(directory):
            print(f"  Error: Directory not found")
            return False
        
        # Look for summary file
        summary_file = os.path.join(directory, 'cuda_benchmark_summary.txt')
        if not os.path.exists(summary_file):
            print(f"  Warning: Summary file not found")
            return False
        
        # Parse summary file
        results = self.parse_summary_file(summary_file)
        
        if results:
            self.all_results[directory] = results
            print(f"  Loaded {len(results)} operations")
            return True
        
        return False
    
    def parse_summary_file(self, filepath):
        """Parse a benchmark summary text file"""
        results = {}
        current_op = None
        
        with open(filepath, 'r') as f:
            for line in f:
                line = line.strip()
                
                # Check for operation header
                if line and not line.startswith('=') and not line.startswith('-'):
                    # Check if this is a metric line
                    if ':' in line and any(keyword in line.lower() for keyword in 
                                          ['samples', 'min', 'max', 'mean', 'std']):
                        if current_op:
                            # Parse metric
                            parts = line.split(':')
                            if len(parts) == 2:
                                key = parts[0].strip()
                                try:
                                    value = float(parts[1].strip())
                                    results[current_op][key] = value
                                except ValueError:
                                    pass
                    elif not any(keyword in line.lower() for keyword in 
                               ['time', 'gpu', 'load', 'average', 'peak']):
                        # This might be an operation name
                        current_op = line
                        results[current_op] = {}
        
        return results
    
    def compare_operations(self):
        """Compare performance across different operations"""
        print("\n" + "=" * 70)
        print("Performance Comparison Across Operations")
        print("=" * 70 + "\n")
        
        if not self.all_results:
            print("No results loaded.")
            return
        
        # Use the first result directory for comparison
        first_dir = list(self.all_results.keys())[0]
        results = self.all_results[first_dir]
        
        operations = list(results.keys())
        mean_times = []
        std_times = []
        
        for op in operations:
            if 'Mean' in results[op]:
                mean_times.append(results[op]['Mean'])
                std_times.append(results[op].get('Std Dev', 0))
            else:
                mean_times.append(0)
                std_times.append(0)
        
        # Create bar chart
        fig, ax = plt.subplots(figsize=(12, 6))
        
        x_pos = np.arange(len(operations))
        bars = ax.bar(x_pos, mean_times, yerr=std_times, capsize=5, 
                     alpha=0.7, color='steelblue')
        
        ax.set_xlabel('Operation', fontsize=11)
        ax.set_ylabel('Mean Execution Time (μs)', fontsize=11)
        ax.set_title('CUDA Operation Performance Comparison', fontsize=13, fontweight='bold')
        ax.set_xticks(x_pos)
        ax.set_xticklabels(operations, rotation=45, ha='right')
        ax.grid(True, alpha=0.3, axis='y')
        
        # Add value labels on bars
        for bar in bars:
            height = bar.get_height()
            if height > 0:
                ax.text(bar.get_x() + bar.get_width()/2., height,
                       f'{height:.1f}',
                       ha='center', va='bottom', fontsize=9)
        
        plt.tight_layout()
        
        output_file = 'cuda_operation_comparison.png'
        plt.savefig(output_file, dpi=150, bbox_inches='tight')
        print(f"Saved comparison chart: {output_file}")
        plt.close()
    
    def generate_performance_report(self, output_file='cuda_analysis_report.txt'):
        """Generate a detailed performance analysis report"""
        print(f"\nGenerating performance report: {output_file}")
        
        with open(output_file, 'w') as f:
            f.write("=" * 70 + "\n")
            f.write("CUDA Benchmark Analysis Report\n")
            f.write(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write("=" * 70 + "\n\n")
            
            for directory, results in self.all_results.items():
                f.write(f"\nResults from: {directory}\n")
                f.write("-" * 70 + "\n\n")
                
                # Sort operations by mean time
                sorted_ops = sorted(results.items(), 
                                  key=lambda x: x[1].get('Mean', 0) if isinstance(x[1].get('Mean'), (int, float)) else 0,
                                  reverse=True)
                
                for op_name, metrics in sorted_ops:
                    f.write(f"{op_name}\n")
                    for metric, value in sorted(metrics.items()):
                        if isinstance(value, float):
                            f.write(f"  {metric:20s}: {value:12.2f}\n")
                        else:
                            f.write(f"  {metric:20s}: {value}\n")
                    f.write("\n")
                
                # Performance insights
                f.write("\nPerformance Insights:\n")
                f.write("-" * 70 + "\n")
                
                if results:
                    # Find fastest and slowest operations
                    times = {}
                    for op_name, metrics in results.items():
                        if 'Mean' in metrics and isinstance(metrics['Mean'], (int, float)):
                            times[op_name] = metrics['Mean']
                    
                    if times:
                        fastest = min(times.items(), key=lambda x: x[1])
                        slowest = max(times.items(), key=lambda x: x[1])
                        
                        f.write(f"\nFastest operation: {fastest[0]}\n")
                        f.write(f"  Mean time: {fastest[1]:.2f} μs\n")
                        f.write(f"\nSlowest operation: {slowest[0]}\n")
                        f.write(f"  Mean time: {slowest[1]:.2f} μs\n")
                        f.write(f"\nPerformance ratio: {slowest[1]/fastest[1]:.2f}x\n")
        
        print(f"Report saved: {output_file}")
    
    def interactive_analysis(self):
        """Interactive analysis menu"""
        while True:
            print("\n" + "=" * 70)
            print("CUDA Benchmark Analysis")
            print("=" * 70)
            print("\n1. Load results from directory")
            print("2. Compare operations")
            print("3. Generate performance report")
            print("4. List loaded results")
            print("5. Exit")
            
            choice = input("\nEnter choice [1-5]: ").strip()
            
            if choice == '1':
                directory = input("Enter directory path: ").strip()
                self.load_results(directory)
            
            elif choice == '2':
                self.compare_operations()
            
            elif choice == '3':
                self.generate_performance_report()
            
            elif choice == '4':
                print("\nLoaded result directories:")
                for i, d in enumerate(self.all_results.keys(), 1):
                    print(f"  {i}. {d}")
                if not self.all_results:
                    print("  (none)")
            
            elif choice == '5':
                break
            
            else:
                print("Invalid choice.")


def main():
    """Main execution"""
    analyzer = BenchmarkAnalyzer()
    
    # Auto-load results if directories exist
    for dirname in ['cuda_benchmark_results', 'cuda_benchmark_synthetic']:
        if os.path.isdir(dirname):
            analyzer.load_results(dirname)
    
    # Check for command line arguments
    if len(sys.argv) > 1:
        # Load specified directories
        for arg in sys.argv[1:]:
            if os.path.isdir(arg):
                analyzer.load_results(arg)
        
        # Generate reports
        if analyzer.all_results:
            analyzer.compare_operations()
            analyzer.generate_performance_report()
    else:
        # Interactive mode
        analyzer.interactive_analysis()
    
    return 0


if __name__ == "__main__":
    sys.exit(main())
