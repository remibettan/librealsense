// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2017 RealSense, Inc. All Rights Reserved.

#include <librealsense2/rs.hpp> // Include RealSense Cross Platform API
#include <chrono>
#include <thread>
#include <iostream>
#include <atomic>
#include <assert.h>

rs2::stream_profile get_depth_profile(rs2::depth_sensor depth_sensor)
{
    auto depth_profiles = depth_sensor.get_stream_profiles();
    rs2::stream_profile depth_profile;
    for (auto& p : depth_profiles)
    {
        if (p.format() == RS2_FORMAT_Z16 && p.fps() == 30)
        {
            auto vsp = p.as<rs2::video_stream_profile>();
            if (vsp.height() == 480 && vsp.width() == 848)
            {
                depth_profile = p;
                break;
            }
        }
    }
    return depth_profile;
}

rs2::stream_profile get_color_profile_from_depth_sensor(rs2::depth_sensor depth_sensor)
{
    auto color_profiles = depth_sensor.get_stream_profiles();
    rs2::stream_profile color_profile;

    for (auto& p : color_profiles)
    {
        if (p.format() == RS2_FORMAT_RGB8 && p.fps() == 30)
        {
            auto vsp = p.as<rs2::video_stream_profile>();
            if (vsp.height() == 480 && vsp.width() == 848)
            {
                color_profile = p;
                break;
            }
        }
    }
    return color_profile;
}

rs2::stream_profile get_color_profile(rs2::color_sensor color_sensor)
{
    auto color_profiles = color_sensor.get_stream_profiles();
    rs2::stream_profile color_profile;

    for (auto& p : color_profiles)
    {
        if (p.format() == RS2_FORMAT_RGB8 && p.fps() == 30)
        {
            auto vsp = p.as<rs2::video_stream_profile>();
            if (vsp.height() == 480 && vsp.width() == 848)
            {
                color_profile = p;
                break;
            }
        }
    }
    return color_profile;
}

void d455_depth_color_stream_task(rs2::device&& dev)
{
    auto dev_name = dev.get_info(RS2_CAMERA_INFO_NAME);
    std::cout << "Starting thread with device: " << dev_name << std::endl;

    auto depth_sensor = dev.first<rs2::depth_sensor>();
    depth_sensor.open(get_depth_profile(depth_sensor));
    std::atomic<int> depth_frames_count(0);
    std::cout << "Starting sensor: " << depth_sensor.get_info(RS2_CAMERA_INFO_NAME) 
              << " for device: " << dev_name << std::endl;
    depth_sensor.start([&depth_frames_count](rs2::frame frame)
    {
        ++depth_frames_count;
    });

    std::atomic<int> color_frames_count(0);
    auto color_sensor = dev.first<rs2::color_sensor>();
    color_sensor.open(get_color_profile(color_sensor));
    std::cout << "Starting sensor: " << color_sensor.get_info(RS2_CAMERA_INFO_NAME) 
              << " for device: " << dev_name << std::endl;
    color_sensor.start([&color_frames_count](rs2::frame frame)
    {
        ++color_frames_count;
    });

    auto start = std::chrono::steady_clock::now();
    int starting_i = 1200;
    int i = starting_i; // 2 minutes time of run
    while (--i) // time_of_run = sleep_time * num_of_iterations
    {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));

        auto now = std::chrono::steady_clock::now();
        float elapsed = std::chrono::duration<float>(now - start).count();

        if (elapsed >= 1.0f) {
            int depth_fps = depth_frames_count.exchange(0);
            int color_fps = color_frames_count.exchange(0);
            std::cout << "FPS - " << dev.get_info(RS2_CAMERA_INFO_NAME) << " Depth: " << depth_fps << ", Color: " << color_fps << std::endl;
            start = now;
            // waiting 10 iterations before checking
            if ((starting_i - i) > 20 && (depth_fps <= 20 || color_fps <= 20))
            {
                std::cout << "*********** D455 - FPS too low! ************" << std::endl;
            }
        }
    }

    depth_sensor.stop();
    depth_sensor.close();
    color_sensor.stop();
    color_sensor.close();
}


bool check_d405_frameset(rs2::frame frames)
{
    bool result = false;
    if (frames.is<rs2::frameset>())
    {
        bool depth_frame_found = false;
        bool color_frame_found = false;
        auto frameset = frames.as<rs2::frameset>();
        auto depth_frame = frameset.get_depth_frame();
        auto color_frame = frameset.get_color_frame();
        result = (depth_frame && color_frame);
    }
    return result;
}

void d405_depth_color_stream_task(rs2::device&& dev)
{
    auto dev_name = dev.get_info(RS2_CAMERA_INFO_NAME);
    std::cout << "Starting thread with device: " << dev_name << std::endl;

    auto depth_sensor = dev.first<rs2::depth_sensor>();
    auto depth_profile = get_depth_profile(depth_sensor);
    auto color_profile = get_color_profile_from_depth_sensor(depth_sensor);
    depth_sensor.open({depth_profile, color_profile});
    std::atomic<int> depth_color_frames_count(0);
    std::cout << "Starting sensor: " << depth_sensor.get_info(RS2_CAMERA_INFO_NAME) 
              << " for device: " << dev_name << std::endl;
    depth_sensor.start([&depth_color_frames_count](rs2::frame frame)
    {
        //bool is_valid = check_d405_frameset(frame);
        ++depth_color_frames_count;
    });    

    auto start = std::chrono::steady_clock::now();
    int starting_i = 1200;
    int i = starting_i; // 2 minutes time of run
    while (--i) // time_of_run = sleep_time * num_of_iterations
    {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));

        auto now = std::chrono::steady_clock::now();
        float elapsed = std::chrono::duration<float>(now - start).count();

        if (elapsed >= 1.0f) {
            int depth_color_fps = depth_color_frames_count.exchange(0);
            std::cout << "FPS - " << dev_name << " Depth and Color: " << depth_color_fps / 2 << std::endl;
            start = now;
            // waiting 10 iterations before checking
            if ((starting_i - i) > 20 && depth_color_fps <= 40)
            {
                std::cout << "*********** D405 - FPS too low! ************" << std::endl;
            }
        }
    }
    depth_sensor.stop();
    depth_sensor.close();
}

void depth_color_stream_task(rs2::device&& dev)
{
    auto name = dev.get_info(RS2_CAMERA_INFO_NAME);
    if (!strcmp(name, "Intel RealSense D405"))
    {
        d405_depth_color_stream_task(std::move(dev));
    }
    else if (!strcmp(name, "Intel RealSense D455"))
    {
        d455_depth_color_stream_task(std::move(dev));
    }
}

int main(int argc, char * argv[]) try
{
    rs2::log_to_console(RS2_LOG_SEVERITY_WARN);
    
    rs2::context ctx;
    auto devices = ctx.query_devices();
    
    if (devices.size() != 3)
    {
    	throw ("Make sure 2 D405 and one D455 are connected - no more");
    }
    
    std::thread first_dev(depth_color_stream_task, devices[0]);
    std::thread second_dev(depth_color_stream_task, devices[1]);
    std::thread third_dev(depth_color_stream_task, devices[2]);


    std::this_thread::sleep_for(std::chrono::seconds(15));

    // trying to join the 3 threads - stop trying after the 3 threads joined
    std::vector<std::thread*> threads = { &first_dev, &second_dev, &third_dev };
    std::vector<bool> joined(3, false);

    while (true) {
        for (size_t i = 0; i < threads.size(); ++i) {
            if (!joined[i] && threads[i]->joinable()) {
                // Try to join
                threads[i]->join();
                joined[i] = true;
                std::cout << "Joined thread " << (i + 1) << "\n";
            }
        }

        // Check if all threads joined
        bool all_joined = true;
        for (bool j : joined) all_joined &= j;
        if (all_joined) break;

        // Sleep briefly to avoid busy-waiting
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }

    return EXIT_SUCCESS;
}
catch (const rs2::error & e)
{
    std::cerr << "RealSense error calling " << e.get_failed_function() << "(" << e.get_failed_args() << "):\n    " << e.what() << std::endl;
    return EXIT_FAILURE;
}
catch (const std::exception& e)
{
    std::cerr << e.what() << std::endl;
    return EXIT_FAILURE;
}
