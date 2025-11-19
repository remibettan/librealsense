#include <iomanip>
#include <cmath>
#include "memory-monitor.h"

double memory_monitor::get_memory_usage()
{
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

std::string get_time()
{
    using namespace std::chrono;
    // Get current time
    auto now = system_clock::now();
    // Convert to time_t for date/time breakdown
    auto now_time_t = system_clock::to_time_t(now);
    auto now_ms = duration_cast<milliseconds>(now.time_since_epoch()) % 1000;
    // Convert to tm for readable H:M:S
    std::tm local_tm{};
#ifdef _WIN32
    localtime_s(&local_tm, &now_time_t);  // Window
#else
    localtime_r(&now_time_t, &local_tm);  // Linux / macO
#endif
    std::stringstream ss;
    // Print timestamp in H:M:S:MS format
    ss << std::put_time(&local_tm, "%H:%M:%S")
       << ':' << std::setfill('0') << std::setw(3) << now_ms.count();

    return ss.str();
}


void memory_monitor::record_memory_samples(int minutes)
{
    static double prev_mem_usage = 0;
    for (int i = 0; i < 10 * 60 * minutes; ++i)
    {
        double mem_usage = get_memory_usage();
        if (std::abs(prev_mem_usage - mem_usage) > 0.1)
        {
            std::cout << get_time() << " - mem_usage = " << mem_usage << ", prev = " << prev_mem_usage << std::endl;
        }
        prev_mem_usage = mem_usage;
        _memory_samples.push_back(mem_usage);
        std::this_thread::sleep_for(std::chrono::milliseconds(100)); // time.sleep(0.1)
    }
}

void memory_monitor::generate_memory_usage_csv(const std::string& path_to_file)
{
    // Equivalent to: print('Size of memory_samples: ' + repr(sys.getsizeof(memory_samples)))
    size_t size_bytes = _memory_samples.size() * sizeof(double) + sizeof(_memory_samples);
    std::cout << "Size of memory_samples in bytes: " << size_bytes << std::endl;

    // Save data to CSV (since we don't have matplotlib in C++)
    std::ofstream file(path_to_file);
    file << "Sample,Memory_MB\n";
    for (size_t i = 0; i < _memory_samples.size(); ++i) {
        file << i << "," << _memory_samples[i] << "\n";
    }
    file.close();


}
