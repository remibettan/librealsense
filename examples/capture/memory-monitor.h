
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

class memory_monitor
{
public:
    void record_memory_samples(int minutes);
    void generate_memory_usage_csv(const std::string& path_to_file);
private:
    double get_memory_usage();
    std::vector<double> _memory_samples;
};
