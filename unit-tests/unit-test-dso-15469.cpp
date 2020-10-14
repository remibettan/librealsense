// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2020 Intel Corporation. All Rights Reserved.

/////////////////////////////////////////////////////////////////////////////////////////////////////////////
// This set of tests is valid for any number and combination of RealSense cameras, including R200 and F200 //
/////////////////////////////////////////////////////////////////////////////////////////////////////////////

#include <cmath>
#include "unit-tests-common.h"
#include "../include/librealsense2/rs_advanced_mode.hpp"
#include <librealsense2/hpp/rs_types.hpp>
#include <librealsense2/hpp/rs_frame.hpp>
#include <iostream>
#include <chrono>
#include <ctime>
#include <algorithm>
#include <librealsense2/rsutil.h>

using namespace rs2;

//--------------------fw logs helpers-----------------------------
std::string char2hex_test(unsigned char n)
{
    std::string res;

    do
    {
        res += "0123456789ABCDEF"[n % 16];
        n >>= 4;
    } while (n);

    reverse(res.begin(), res.end());

    if (res.size() == 1)
    {
        res.insert(0, "0");
    }

    return res;
}

std::string datetime_string_test()
{
    auto t = time(nullptr);
    char buffer[20] = {};
    const tm* time = localtime(&t);
    if (nullptr != time)
        strftime(buffer, sizeof(buffer), "%Y-%m-%d %H:%M:%S", time);

    return std::string(buffer);
}

bool stop_fw_logs = false;

void enable_fw_logs(std::string output_path, std::string parsing_file_path)
{
    context ctx;
    device_hub hub(ctx);
    auto dev = hub.wait_for_device();
    auto fw_log_device = dev.as<rs2::firmware_logger>();
    bool using_parser = false;

    std::ofstream fw_logs_output(output_path.c_str(), std::ofstream::out);

    if (!parsing_file_path.empty())
    {
        std::ifstream f(parsing_file_path);
        if (f.good())
        {
            std::string xml_content((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
            bool parser_initialized = fw_log_device.init_parser(xml_content);
            if (parser_initialized)
                using_parser = true;
        }
    }
    while (hub.is_connected(dev) && !stop_fw_logs)
    {
        auto log_message = fw_log_device.create_message();
        bool result = fw_log_device.get_firmware_log(log_message);
        if (result)
        {
            std::vector<std::string> fw_log_lines;
            if (using_parser)
            {
                auto parsed_log = fw_log_device.create_parsed_message();
                bool parsing_result = fw_log_device.parse_log(log_message, parsed_log);

                std::stringstream sstr;
                sstr << datetime_string_test() << " " << parsed_log.timestamp() << " " << parsed_log.severity() << " " << parsed_log.message()
                    << " " << parsed_log.thread_name() << " " << parsed_log.file_name()
                    << " " << parsed_log.line();

                fw_log_lines.push_back(sstr.str());
            }
            else
            {
                std::stringstream sstr;
                sstr << datetime_string_test() << "  FW_Log_Data:";
                std::vector<uint8_t> msg_data = log_message.data();
                for (int i = 0; i < msg_data.size(); ++i)
                {
                    sstr << char2hex_test(msg_data[i]) << " ";
                }
                fw_log_lines.push_back(sstr.str());
            }
            for (auto& line : fw_log_lines)
                fw_logs_output << line << std::endl;
        }
    }
}

//--------------------sensors callback-----------------------------
std::map<rs2_stream, int> frames_counter_map;
bool display_frames_flag = false;
void init_counters()
{
    frames_counter_map[RS2_STREAM_DEPTH] = 0;
    frames_counter_map[RS2_STREAM_INFRARED] = 0;
    frames_counter_map[RS2_STREAM_COLOR] = 0;
    frames_counter_map[RS2_STREAM_GYRO] = 0;
    frames_counter_map[RS2_STREAM_ACCEL] = 0;
}

void sensors_callback(rs2::frame f)
{
    rs2_stream stream_type = f.get_profile().stream_type();
    frames_counter_map[stream_type]++;

    if (display_frames_flag)
    {
        std::string status_str("Collecting frames...");
        for (auto&& st : frames_counter_map)
        {
            status_str += rs2_stream_to_string(st.first);
            status_str += ", counter = ";
            status_str += std::to_string(st.second);
            status_str += " - ";
        }
        std::cout << status_str << std::endl;
    }
}

TEST_CASE("RGB getting stuck - do not push!!!", "[remi]") {

    rs2::context ctx;
    if (make_context(SECTION_FROM_TEST_NAME, &ctx))
    {
        auto list = ctx.query_devices();
        REQUIRE(list.size());
        auto dev = list[0];

        auto model = dev.get_info(RS2_CAMERA_INFO_NAME);
        auto sn = dev.get_info(RS2_CAMERA_INFO_ASIC_SERIAL_NUMBER);
        auto fw = dev.get_info(RS2_CAMERA_INFO_FIRMWARE_VERSION);
        std::string parsing_file_path = "C://Users//rbettan//Documents//Dev//RefFolder//HWLoggerEventsDS5.xml";
        std::string fw_logs_output_path = "C://Users//rbettan//Documents//Current Work//2020_10 - Sanity bugs//DSO-15469 - rgb stuck//fw_logs.txt";
        std::thread fw_logs_thread(enable_fw_logs, fw_logs_output_path, parsing_file_path);

        std::string log_file_path = "C://Users//rbettan//Documents//Current Work//2020_10 - Sanity bugs//DSO-15469 - rgb stuck//lrs_logs.txt";
        rs2::log_to_file(RS2_LOG_SEVERITY_INFO, log_file_path.c_str());

        //get sensors
        auto sensors = dev.query_sensors();
        sensor depth_s;
        sensor color_s;
        sensor imu_s;
        for (auto&& s : sensors)
        {
            if (!strcmp(s.get_info(RS2_CAMERA_INFO_NAME), "Stereo Module"))
                depth_s = s;
            if (!strcmp(s.get_info(RS2_CAMERA_INFO_NAME),"RGB Camera"))
                color_s = s;
            if (!strcmp(s.get_info(RS2_CAMERA_INFO_NAME), "Motion Module"))
                imu_s = s;
        }
        depth_sensor depth_ir_sensor(depth_s);
        color_sensor rgb_sensor(color_s);
        motion_sensor imu_sensor(imu_s);

        // define profiles
        auto depth_profiles = depth_ir_sensor.get_stream_profiles();
        auto iterator_found = std::find_if(depth_profiles.begin(), depth_profiles.end(),
            [&](const auto& it) {return (it.stream_type() == RS2_STREAM_DEPTH &&
                it.format() == RS2_FORMAT_Z16 &&
                it.as<video_stream_profile>().width() == 1280 &&
                it.as<video_stream_profile>().height() == 720 &&
                it.fps() == 5); });
        REQUIRE(iterator_found != depth_profiles.end());
        rs2::stream_profile depth_profile = *iterator_found;

        iterator_found = std::find_if(depth_profiles.begin(), depth_profiles.end(),
            [&](const auto& it) {return (it.stream_type() == RS2_STREAM_INFRARED &&
                it.format() == RS2_FORMAT_Y8 &&
                it.as<video_stream_profile>().width() == 1280 &&
                it.as<video_stream_profile>().height() == 720 &&
                it.fps() == 5); });
        REQUIRE(iterator_found != depth_profiles.end());
        rs2::stream_profile ir_profile = *iterator_found;

        auto color_profiles = rgb_sensor.get_stream_profiles();
        iterator_found = std::find_if(color_profiles.begin(), color_profiles.end(),
            [&](const auto& it) {return (it.stream_type() == RS2_STREAM_COLOR &&
                it.format() == RS2_FORMAT_YUYV &&
                it.as<video_stream_profile>().width() == 1280 &&
                it.as<video_stream_profile>().height() == 720 &&
                it.fps() == 5); });
        REQUIRE(iterator_found != color_profiles.end());
        rs2::stream_profile color_profile = *iterator_found;

        auto imu_profiles = imu_sensor.get_stream_profiles();
        iterator_found = std::find_if(imu_profiles.begin(), imu_profiles.end(),
            [&](const auto& it) {return (it.stream_type() == RS2_STREAM_GYRO &&
                it.format() == RS2_FORMAT_MOTION_XYZ32F &&
                it.as<video_stream_profile>().width() == 0 &&
                it.as<video_stream_profile>().height() == 0 &&
                it.fps() == 400); });
        REQUIRE(iterator_found != imu_profiles.end());
        rs2::stream_profile gyro_profile = *iterator_found;

        iterator_found = std::find_if(imu_profiles.begin(), imu_profiles.end(),
            [&](const auto& it) {return (it.stream_type() == RS2_STREAM_ACCEL &&
                it.format() == RS2_FORMAT_MOTION_XYZ32F &&
                it.as<video_stream_profile>().width() == 0 &&
                it.as<video_stream_profile>().height() == 0 &&
                it.fps() == 250); });
        REQUIRE(iterator_found != imu_profiles.end());
        rs2::stream_profile accel_profile = *iterator_found;

        frames_counter_map.clear();
        int depth_missing = 0;
        int ir_missing = 0;
        int color_missing = 0;
        int gyro_missing = 0;
        int accel_missing = 0;
        int iteration = 0;

        while (++iteration < 20)
        {
            init_counters();
            std::cout << "iteration : " << iteration << std::endl;
            std::cout << "streaming color";
            rgb_sensor.open(color_profile);
            rgb_sensor.start(sensors_callback);
            std::cout << " - started, ";

            int waiting_iteration = 0;
            while (frames_counter_map[RS2_STREAM_COLOR] < 5)
            {
                std::cout << "waiting 1500 ms for the " << waiting_iteration++ << "th time" << std::endl;
                std::this_thread::sleep_for(std::chrono::milliseconds(1500));
            }

            std::this_thread::sleep_for(std::chrono::milliseconds(50));

            std::cout << "streaming depth_ir";
            depth_ir_sensor.open({ depth_profile, ir_profile });
            depth_ir_sensor.start(sensors_callback);
            std::cout << " - started, ";

            std::this_thread::sleep_for(std::chrono::milliseconds(5));

            std::cout << "streaming imu";
            imu_sensor.open({ gyro_profile, accel_profile });
            imu_sensor.start(sensors_callback);
            std::cout << " - started" << std::endl;

            display_frames_flag = true;
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
            display_frames_flag = false;

            //------------------------
            std::cout << "closing color";
            rgb_sensor.stop();
            rgb_sensor.close();
            std::cout << " - closed, ";

            std::cout << "closing depth_ir";
            depth_ir_sensor.stop();
            depth_ir_sensor.close();
            std::cout << " - closed, ";

            std::cout << "closing imu";
            imu_sensor.stop();
            imu_sensor.close();
            std::cout << " - closed." << std::endl;
            //------------------------

            if (frames_counter_map[RS2_STREAM_DEPTH] == 0)
                ++depth_missing;
            if (frames_counter_map[RS2_STREAM_INFRARED] == 0)
                ++ir_missing;
            if (frames_counter_map[RS2_STREAM_COLOR] == 0)
            {
                ++color_missing;
                std::cout << "color missing in iteration: " << iteration << std::endl;
            }
            if (frames_counter_map[RS2_STREAM_GYRO] == 0)
                ++gyro_missing;
            if (frames_counter_map[RS2_STREAM_ACCEL] == 0)
                ++accel_missing;
            if (depth_missing > 10 ||
                ir_missing > 10 ||
                color_missing > 10 ||
                gyro_missing > 10 ||
                accel_missing > 10)
                break;

        }

        std::cout << "depth frames received = " << frames_counter_map[RS2_STREAM_DEPTH] << std::endl;
        std::cout << "ir frames received = " << frames_counter_map[RS2_STREAM_INFRARED] << std::endl;
        std::cout << "color frames received = " << frames_counter_map[RS2_STREAM_COLOR] << std::endl;
        std::cout << "gyro frames received = " << frames_counter_map[RS2_STREAM_GYRO] << std::endl;
        std::cout << "accel frames received = " << frames_counter_map[RS2_STREAM_ACCEL] << std::endl;
        

        std::cout << "depth missing = " << depth_missing << std::endl;
        std::cout << "ir missing = " << ir_missing << std::endl;
        std::cout << "color missing = " << color_missing << std::endl;
        std::cout << "gyro missing = " << gyro_missing << std::endl;
        std::cout << "accel missing = " << accel_missing << std::endl;

        stop_fw_logs = true;
        fw_logs_thread.join();

    }
}