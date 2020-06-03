// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2017 Intel Corporation. All Rights Reserved.

#include <librealsense2/rs.hpp> // Include RealSense Cross Platform API
#include "example.hpp"          // Include short list of convenience functions for rendering
#include <thread>
#include <string>
#include <vector>
#include <fstream>

using namespace std;


string hexify(unsigned char n)
{
    string res;

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

string datetime_string()
{
    auto t = time(nullptr);
    char buffer[20] = {};
    const tm* time = localtime(&t);
    if (nullptr != time)
        strftime(buffer, sizeof(buffer), "%Y-%m-%d %H:%M:%S", time);

    return string(buffer);
}

// Capture Example demonstrates how to
// capture depth and color video streams and render them to the screen
int main(int argc, char * argv[])
{
    // Declare RealSense pipeline, encapsulating the actual device and sensors
    rs2::pipeline pipe;

    // Start streaming with default recommended configuration
    // The default video configuration contains Depth and Color streams
    // If a device is capable to stream IMU data, both Gyro and Accelerometer are enabled by default
    auto res = pipe.start();

    rs2::context ctx;
    rs2::device_hub hub(ctx);

    try
    {
        cout << "\nWaiting for RealSense device to connect...\n";
        auto dev = hub.wait_for_device();
        cout << "RealSense device was connected...\n";

        setvbuf(stdout, NULL, _IONBF, 0); // unbuffering stdout


        auto fw_log = res.get_device().as<rs2::firmware_logger>();
        auto number_of_flash_logs_in_device = fw_log.get_number_of_flash_logs();

        for (int i = 0; i < number_of_flash_logs_in_device; ++i)
        {
            auto flash_log_message = fw_log.create_message();
            bool result = fw_log.get_flash_log(flash_log_message);
            if (result)
            {
                std::vector<string> fw_log_lines;

                static bool usingParser = true;
                if (usingParser)
                {
                    std::string xml_path("HWLoggerEventsDS5.xml");
                    if (!xml_path.empty())
                    {
                        ifstream f(xml_path);
                        if (f.good())
                        {
                            unique_ptr<rs2::firmware_log_parser> parser =
                                unique_ptr<rs2::firmware_log_parser>(new rs2::firmware_log_parser(xml_path));

                            rs2::firmware_log_parsed_message parsed_log = parser->parse_firmware_log(flash_log_message);
                            stringstream sstr;
                            sstr << parsed_log.timestamp() << " " << parsed_log.severity() << " " << parsed_log.message()
                                << " " << parsed_log.thread_name() << " " << parsed_log.file_name()
                                << " " << parsed_log.line();

                            fw_log_lines.push_back(sstr.str());
                        }
                    }
                }
                else
                {
                    stringstream sstr;
                    sstr << flash_log_message.get_timestamp();
                    sstr << " " << flash_log_message.get_severity_str();
                    sstr << "  FW_Log_Data:";
                    std::vector<uint8_t> msg_data = flash_log_message.data();
                    for (int i = 0; i < msg_data.size(); ++i)
                    {
                        sstr << hexify(msg_data[i]) << " ";
                    }
                    fw_log_lines.push_back(sstr.str());
                }
                for (auto& line : fw_log_lines)
                    cout << line << endl;
            }
        }
    }
    catch (const rs2::error & e)
    {
        cerr << "RealSense error calling " << e.get_failed_function() << "(" << e.get_failed_args() << "):\n    " << e.what() << endl;
    }

    return EXIT_SUCCESS;
}