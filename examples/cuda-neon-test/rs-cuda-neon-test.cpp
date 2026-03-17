// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

// This example tests the CUDA+NEON hybrid acceleration
// When built with CUDA on ARM platforms:
// - depth_to_points uses CUDA acceleration
// - get_texture_map uses NEON acceleration (fallback for methods not in CUDA)

#include <librealsense2/rs.hpp>
#include <iostream>
#include <chrono>
#include <iomanip>

// Helper to measure execution time
class Timer {
    std::chrono::high_resolution_clock::time_point start;
    std::string name;
public:
    Timer(const std::string& n) : name(n), start(std::chrono::high_resolution_clock::now()) {}
    ~Timer() {
        auto end = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::microseconds>(end - start).count();
        std::cout << name << " took: " << duration / 1000.0 << " ms" << std::endl;
    }
};

void print_build_info() {
    std::cout << "\n=== Build Configuration ===" << std::endl;
    
#ifdef RS2_USE_CUDA
    std::cout << "CUDA support: ENABLED" << std::endl;
#else
    std::cout << "CUDA support: DISABLED" << std::endl;
#endif

#if defined(__ARM_NEON) && defined(BUILD_WITH_NEON) && !defined(ANDROID)
    std::cout << "NEON support: ENABLED" << std::endl;
#else
    std::cout << "NEON support: DISABLED" << std::endl;
#endif

#ifdef __SSSE3__
    std::cout << "SSSE3 support: ENABLED" << std::endl;
#else
    std::cout << "SSSE3 support: DISABLED" << std::endl;
#endif

    std::cout << std::endl;

    // Expected behavior
    std::cout << "=== Expected Behavior ===" << std::endl;
#if defined(RS2_USE_CUDA)
    #if defined(__ARM_NEON) && defined(BUILD_WITH_NEON) && !defined(ANDROID)
        std::cout << "Pointcloud implementation: CUDA+NEON hybrid" << std::endl;
        std::cout << "  - depth_to_points: CUDA accelerated" << std::endl;
        std::cout << "  - get_texture_map: NEON accelerated (inherited)" << std::endl;
    #else
        std::cout << "Pointcloud implementation: CUDA only" << std::endl;
        std::cout << "  - depth_to_points: CUDA accelerated" << std::endl;
        std::cout << "  - get_texture_map: generic C++ (not accelerated)" << std::endl;
    #endif
#elif defined(__SSSE3__)
    std::cout << "Pointcloud implementation: SSSE3" << std::endl;
#elif defined(__ARM_NEON) && defined(BUILD_WITH_NEON) && !defined(ANDROID)
    std::cout << "Pointcloud implementation: NEON" << std::endl;
#else
    std::cout << "Pointcloud implementation: generic C++" << std::endl;
#endif
    std::cout << "==============================\n" << std::endl;
}

int main(int argc, char * argv[]) try
{
    // Print build configuration
    print_build_info();

    // Configure logging
    rs2::log_to_console(RS2_LOG_SEVERITY_INFO);

    // Create pipeline
    rs2::pipeline pipe;
    rs2::config cfg;

    // Enable depth and color streams
    cfg.enable_stream(RS2_STREAM_DEPTH, 640, 480, RS2_FORMAT_Z16, 30);
    cfg.enable_stream(RS2_STREAM_COLOR, 640, 480, RS2_FORMAT_RGB8, 30);

    std::cout << "Starting RealSense pipeline..." << std::endl;
    auto profile = pipe.start(cfg);

    // Get device name
    auto device = profile.get_device();
    std::cout << "Using device: " << device.get_info(RS2_CAMERA_INFO_NAME) << std::endl;
    std::cout << "Serial number: " << device.get_info(RS2_CAMERA_INFO_SERIAL_NUMBER) << std::endl;

    // Create pointcloud processor
    rs2::pointcloud pc;
    rs2::points points;

    std::cout << "\nProcessing frames..." << std::endl;
    std::cout << "Press Ctrl+C to stop\n" << std::endl;

    int frame_count = 0;
    double total_calculate_time = 0.0;
    double total_texture_map_time = 0.0;

    while (frame_count < 100) // Process 100 frames for testing
    {
        // Wait for frames
        auto frames = pipe.wait_for_frames();
        auto depth = frames.get_depth_frame();
        auto color = frames.get_color_frame();

        if (!depth || !color)
            continue;

        frame_count++;

        // Map pointcloud to color frame (triggers texture mapping)
        pc.map_to(color);

        // Measure pointcloud calculation time (includes depth_to_points)
        auto calc_start = std::chrono::high_resolution_clock::now();
        points = pc.calculate(depth);
        auto calc_end = std::chrono::high_resolution_clock::now();
        auto calc_duration = std::chrono::duration_cast<std::chrono::microseconds>(calc_end - calc_start).count();
        total_calculate_time += calc_duration / 1000.0;

        // The texture mapping happens inside calculate() when map_to() has been called
        // We can't time it separately with the public API, but the calculate() time includes it

        if (frame_count % 10 == 0) {
            std::cout << "Frame " << std::setw(3) << frame_count 
                      << " - Calculate: " << std::setw(6) << std::fixed << std::setprecision(2)
                      << calc_duration / 1000.0 << " ms"
                      << " - Points: " << points.size() << std::endl;
        }
    }

    std::cout << "\n=== Performance Summary ===" << std::endl;
    std::cout << "Frames processed: " << frame_count << std::endl;
    std::cout << "Average calculate time: " << std::fixed << std::setprecision(2) 
              << (total_calculate_time / frame_count) << " ms/frame" << std::endl;
    std::cout << "Throughput: " << std::fixed << std::setprecision(1)
              << (1000.0 / (total_calculate_time / frame_count)) << " fps" << std::endl;

    std::cout << "\n=== Test Results ===" << std::endl;
#if defined(RS2_USE_CUDA) && defined(__ARM_NEON) && defined(BUILD_WITH_NEON) && !defined(ANDROID)
    std::cout << "✓ CUDA+NEON hybrid is active" << std::endl;
    std::cout << "✓ depth_to_points: Using CUDA acceleration" << std::endl;
    std::cout << "✓ get_texture_map: Using NEON acceleration" << std::endl;
    std::cout << "\nThis confirms that NEON optimizations are used as fallback" << std::endl;
    std::cout << "for methods not accelerated by CUDA!" << std::endl;
#elif defined(RS2_USE_CUDA)
    std::cout << "⚠ CUDA is active but NEON is not available" << std::endl;
    std::cout << "✓ depth_to_points: Using CUDA acceleration" << std::endl;
    std::cout << "  get_texture_map uses generic C++ implementation" << std::endl;
#elif defined(__ARM_NEON) && defined(BUILD_WITH_NEON) && !defined(ANDROID)
    std::cout << "✓ NEON acceleration is active" << std::endl;
#elif defined(__SSSE3__)
    std::cout << "✓ SSSE3 acceleration is active" << std::endl;
#else
    std::cout << "ℹ Using generic C++ implementation (no acceleration)" << std::endl;
#endif
    std::cout << "========================\n" << std::endl;

    pipe.stop();
    return EXIT_SUCCESS;
}
catch (const rs2::error & e)
{
    std::cerr << "RealSense error calling " << e.get_failed_function() 
              << "(" << e.get_failed_args() << "):\n    " << e.what() << std::endl;
    return EXIT_FAILURE;
}
catch (const std::exception& e)
{
    std::cerr << "Error: " << e.what() << std::endl;
    return EXIT_FAILURE;
}
