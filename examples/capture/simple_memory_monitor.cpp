// Simple C++ equivalent of the Python memory monitoring script
// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2024 RealSense, Inc. All Rights Reserved.

#include <iostream>
#include <vector>
#include <chrono>
#include <thread>
#include <fstream>

#ifdef _WIN32
#include <windows.h>
#include <psapi.h>
#elif __linux__
#include <unistd.h>
#include <fstream>
#include <sstream>
#elif __APPLE__
#include <mach/mach.h>
#include <mach/task.h>
#include <mach/mach_init.h>
#endif

std::vector<double> memory_samples;

// Equivalent to psutil.Process().memory_info().rss / (1024 ** 2)
double get_memory_usage() {
#ifdef _WIN32
    PROCESS_MEMORY_COUNTERS pmc;
    if (GetProcessMemoryInfo(GetCurrentProcess(), &pmc, sizeof(pmc))) {
        return static_cast<double>(pmc.WorkingSetSize) / (1024.0 * 1024.0);
    }
    return 0.0;
#elif __linux__
    std::ifstream status_file("/proc/self/status");
    std::string line;
    while (std::getline(status_file, line)) {
        if (line.substr(0, 6) == "VmRSS:") {
            std::istringstream iss(line);
            std::string label, value, unit;
            iss >> label >> value >> unit;
            return std::stod(value) / 1024.0; // Convert KB to MB
        }
    }
    return 0.0;
#elif __APPLE__
    struct task_basic_info info;
    mach_msg_type_number_t size = sizeof(info);
    kern_return_t kerr = task_info(mach_task_self(), TASK_BASIC_INFO, 
                                  (task_info_t)&info, &size);
    if (kerr == KERN_SUCCESS) {
        return static_cast<double>(info.resident_size) / (1024.0 * 1024.0);
    }
    return 0.0;
#else
    return 0.0;
#endif
}

int main() {
    // Equivalent to: for i in range(10):
    for (int i = 0; i < 10; ++i) {
        double mem_usage = get_memory_usage();
        memory_samples.push_back(mem_usage);
        std::this_thread::sleep_for(std::chrono::milliseconds(100)); // time.sleep(0.1)
    }
    
    // Equivalent to: print('Size of memory_samples: ' + repr(sys.getsizeof(memory_samples)))
    size_t size_bytes = memory_samples.size() * sizeof(double) + sizeof(memory_samples);
    std::cout << "Size of memory_samples: " << size_bytes << std::endl;
    
    // Save data to CSV (since we don't have matplotlib in C++)
    std::ofstream file("memory_usage.csv");
    file << "Sample,Memory_MB\n";
    for (size_t i = 0; i < memory_samples.size(); ++i) {
        file << i << "," << memory_samples[i] << "\n";
    }
    file.close();
    
    std::cout << "Memory data saved to memory_usage.csv" << std::endl;
    std::cout << "Import this CSV into Excel or Python for plotting." << std::endl;
    
    return 0;
}