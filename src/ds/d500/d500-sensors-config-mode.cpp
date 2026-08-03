// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include "d500-sensors-config-mode.h"
#include "d500-device.h"
#include "d500-private.h"

#include <src/sensor.h>
#include <src/uvc-sensor.h>

#include <rsutils/easylogging/easyloggingpp.h>


namespace librealsense
{
    sensors_config_mode_option::sensors_config_mode_option( const std::weak_ptr< uvc_sensor > & ep, d500_device & dev )
        : uvc_xu_option< uint8_t >( ep,
                                    ds::depth_xu,
                                    ds::d500_xu_id::DUAL_RGB_MODE,
                                    "Dedicated color sensor (0) vs dual RGB (1). Setting reboots the device.",
                                    false /* not settable while streaming */ )
        , _dev( dev )
    {
    }

    void sensors_config_mode_option::set( float value )
    {
        uvc_xu_option< uint8_t >::set( value );
        // The mode selector only takes effect on next enumeration.
        _dev.hardware_reset();
    }

    option_range sensors_config_mode_option::get_range() const
    {
        return option_range{ 0.f, 1.f, 1.f, 0.f };
    }

    const char * sensors_config_mode_option::get_value_description( float value ) const
    {
        if( value == 0.f )
            return "Dedicated Color Sensor";
        if( value == 1.f )
            return "Dual RGB";
        return nullptr;
    }

    void register_sensors_config_mode_option( d500_device & dev )
    {
        auto raw_depth = dev.get_raw_depth_sensor();
        if( ! raw_depth )
        {
            LOG_WARNING( "register_sensors_config_mode_option: no raw depth sensor, skipping" );
            return;
        }

        auto & depth_sensor = dev.get_depth_sensor();
        depth_sensor.register_option( RS2_OPTION_SENSORS_CONFIG_MODE,
                                      std::make_shared< sensors_config_mode_option >( raw_depth, dev ) );
        LOG_INFO( "RS2_OPTION_SENSORS_CONFIG_MODE registered on Stereo Module (XU DUAL_RGB_MODE = 0x12)" );
    }
}
