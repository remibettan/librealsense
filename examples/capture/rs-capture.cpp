// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2017 Intel Corporation. All Rights Reserved.

#include "memory-monitor.h"
#include <librealsense2/rs.hpp> // Include RealSense Cross Platform API
#include "example.hpp"          // Include short list of convenience functions for rendering
#include <thread>
#include <cstring>
// Capture Example demonstrates how to
// capture depth and color video streams and render them to the screen

rs2::stream_profile get_depth_profile(rs2::depth_sensor depth_sensor)
{
    auto depth_profiles = depth_sensor.get_stream_profiles();
    rs2::stream_profile depth_profile;
    for (auto& p : depth_profiles)
    {
        if (p.format() == RS2_FORMAT_Z16 && p.fps() == 30)
        {
            auto vsp = p.as<rs2::video_stream_profile>();
            if (vsp.height() == 480 && vsp.width() == 640)
            {
                depth_profile = p;
                break;
            }
        }
    }
    return depth_profile;
}


int main(int argc, char* argv[]) try
{
    rs2::log_to_file(RS2_LOG_SEVERITY_DEBUG, "lrs.log");

    auto mem_monitor = memory_monitor();

    auto ctx = rs2::context();
    auto dev = ctx.query_devices()[0];
    auto sensors = dev.query_sensors();
    auto depth_sensor = sensors[0];

    auto depth_profile = get_depth_profile(depth_sensor);

    
    // DEPTH
    depth_sensor.open(depth_profile);
    int iterations = 0;
    depth_sensor.start([&iterations](rs2::frame f) {
        std::cout << ".";
        });

    int minutes = 300;
    std::cout << "streaming depth for " << minutes << " minutes" << std::endl;
    mem_monitor.record_memory_samples(minutes);

    depth_sensor.stop();
    depth_sensor.close();

    std::string path_to_file("memory_usage.csv");
    mem_monitor.generate_memory_usage_csv(path_to_file);
    std::cout << "Memory data saved to: " << path_to_file << std::endl;
    std::cout << "Import this CSV into Excel or Python for plotting." << std::endl;

    return EXIT_SUCCESS;
}
catch (const rs2::error& e)
{
    std::cerr << "RealSense error calling " << e.get_failed_function() << "(" << e.get_failed_args() << "):\n    " << e.what() << std::endl;
    return EXIT_FAILURE;
}
catch (const std::exception& e)
{
    std::cerr << e.what() << std::endl;
    return EXIT_FAILURE;
}
