// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2022 RealSense, Inc. All Rights Reserved.

#include "d500-motion.h"

#include <mutex>
#include <chrono>
#include <vector>
#include <map>
#include <iterator>
#include <cstddef>

#include <src/metadata.h>
#include <src/context.h>
#include "ds/ds-timestamp.h"
#include "ds/ds-options.h"
#include "ds/ds-private.h"
#include "d500-info.h"
#include "d500-private.h"
#include "stream.h"
#include "proc/motion-transform.h"
#include "proc/auto-exposure-processor.h"
#include "backend.h"
#include <src/metadata-parser.h>
#include <src/hid-sensor.h>
#include <src/ds/features/gyro-sensitivity-feature.h>

using namespace librealsense;
namespace librealsense
{
    namespace
    {
        // REVIEW BLOCKER: replace this sentinel with the first released FW that
        // contains IO 34cf87d6 and device-manager 1be508e0.
        const firmware_version hkr_physical_imu_min_fw( "65535.65535.65535.65535" );

        bool is_hkr_physical_imu_pid( uint16_t pid )
        {
            switch( pid )
            {
            case ds::D555_PID:
            case ds::D585_LEGACY_PID:
            case ds::D585S_PID:
            case ds::D585_2C_PID:
            case ds::D585_3C_PID:
            case ds::D585F_PID:
            case ds::D585_2C_PROTO_PID:
            case ds::D585_3C_PROTO_PID:
                return true;
            default:
                return false;
            }
        }
    }

    rs2_motion_device_intrinsic d500_motion::get_motion_intrinsics(rs2_stream stream) const
    {
        if( _has_motion_module_failed )
            throw std::runtime_error( "Motion module is not available on this device" );
        return _ds_motion_common->get_motion_intrinsics(stream);
    }

    bool d500_motion::supports_hkr_physical_imu() const
    {
        return is_hkr_physical_imu_pid( _pid ) && _fw_version >= hkr_physical_imu_min_fw;
    }

    bool d500_motion::is_imu_high_accuracy() const
    {
        return supports_hkr_physical_imu();
    }

    double d500_motion::get_gyro_default_scale() const
    {
        if( supports_hkr_physical_imu() )
            return 0.0001;  // Fixed physical-unit report: 0.0001 degree/second per LSB

        // Legacy D500 reports signed 16-bit raw samples at a fixed 125 dps assumption.
        return 125. / 32768.;
    }

    std::shared_ptr<synthetic_sensor> d500_motion::create_hid_device( std::shared_ptr<context> ctx,
                                                                      const std::vector<platform::hid_device_info>& all_hid_infos )
    {
        return _ds_motion_common->create_hid_device( ctx, all_hid_infos, _tf_keeper );
    }

    d500_motion::d500_motion( std::shared_ptr< const d500_info > const & dev_info )
        : device( dev_info )
        , d500_device( dev_info )
    {
        try
        {
            if (get_info(RS2_CAMERA_INFO_IMU_TYPE) == "IMU_Unknown")
            {
                throw std::runtime_error("Motion Sensor Failure - IMU type not recognized");
            }
            using namespace ds;

            std::vector<platform::hid_device_info> hid_infos = dev_info->get_group().hid_devices;

            _ds_motion_common = std::make_shared<ds_motion_common>(this, _fw_version,
                                                                   _device_capabilities, _hw_monitor);
            _ds_motion_common->init_motion(hid_infos.empty(), *_depth_stream);

#if !defined(__APPLE__) // Motion sensors not supported on macOS
            // Try to add HID endpoint
            auto hid_ep = create_hid_device( dev_info->get_context(), dev_info->get_group().hid_devices );
            if (hid_ep)
            {
                _motion_module_device_idx = static_cast<uint8_t>(add_sensor(hid_ep));

                // HID metadata attributes
                hid_ep->get_raw_sensor()->register_metadata(RS2_FRAME_METADATA_FRAME_TIMESTAMP, make_hid_header_parser(&hid_header::timestamp));

                if( supports_hkr_physical_imu() )
                    get_raw_motion_sensor()->set_gyro_scale_factor( 10000. );
            }
#endif
        }
        catch (const std::exception& e)
        {
            _has_motion_module_failed = true;
            auto device_name = get_info( RS2_CAMERA_INFO_NAME );
            auto serial = get_info( RS2_CAMERA_INFO_SERIAL_NUMBER );
            if( ! ds::is_partial_device_allowed( dev_info->get_context() ) )
            {
                LOG_ERROR( device_name << " #" << serial << " - HID Motion Sensor Failure! " << e.what() );
                throw;
            }
            LOG_WARNING( device_name << " #" << serial << " - HID Motion Sensor Failure (continuing as partial device): " << e.what() );
        }

    }

    ds_motion_sensor & d500_motion::get_motion_sensor()
    {
#if defined(__APPLE__)
        throw std::runtime_error( "Motion sensors are not supported on macOS" );
#else
        return dynamic_cast< ds_motion_sensor & >( get_sensor( _motion_module_device_idx.value() ) );
#endif
    }

    std::shared_ptr< hid_sensor > d500_motion::get_raw_motion_sensor()
    {
#if defined(__APPLE__)
        return nullptr;
#else
        auto raw_sensor = get_motion_sensor().get_raw_sensor();
        return std::dynamic_pointer_cast< hid_sensor >( raw_sensor );
#endif
    }

    void d500_motion::register_gyro_sensitivity()
    {
        if( supports_hkr_physical_imu() && ! _has_motion_module_failed )
        {
            get_raw_motion_sensor()->set_gyro_sensitivity_encoding(
                gyro_sensitivity_encoding::hkr_range_index );
            register_feature(
                std::make_shared< gyro_sensitivity_feature >(
                    get_raw_motion_sensor(), get_motion_sensor(), 4.f ) );
        }
    }

    void d500_motion::register_stream_to_extrinsic_group(const stream_interface& stream, uint32_t group_index)
    {
        device::register_stream_to_extrinsic_group(stream, group_index);
    }
}
