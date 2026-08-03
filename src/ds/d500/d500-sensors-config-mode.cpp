// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include "d500-sensors-config-mode.h"
#include "d500-device.h"
#include "d500-private.h"

#include <src/sensor.h>
#include <src/uvc-sensor.h>
#include <src/librealsense-exception.h>

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
        // Reject anything outside {0, 1} up-front — a rogue set (e.g. sensor.set_option(..., 5))
        // would otherwise slice to a valid uint8_t and reboot the device with a garbage mode.
        if( value != 0.f && value != 1.f )
            throw invalid_value_exception( rsutils::string::from()
                                           << "Sensors Config Mode: value must be 0 or 1, got " << value );

        // Skip the write + reboot when the FW is already in the requested mode. If the read
        // itself fails, fall through to the write path so the user's intent is honored.
        try
        {
            float current = uvc_xu_option< uint8_t >::query();
            if( static_cast< uint8_t >( current ) == static_cast< uint8_t >( value ) )
            {
                LOG_INFO( "Sensors Config Mode already at " << static_cast< int >( value )
                          << "; skipping XU write + hardware_reset" );
                return;
            }
        }
        catch( ... ) { /* read failed — proceed with the write */ }

        uvc_xu_option< uint8_t >::set( value );
        // The mode selector only takes effect on next enumeration.
        _dev.hardware_reset();
    }

    option_range sensors_config_mode_option::get_range() const
    {
        // Cache the FW-reported range on first successful query; the range is stable per FW
        // build so a single round-trip suffices. If the query fails (e.g. FW without the
        // control) the initialized default {0,1,1,0} stays in place.
        std::call_once( _range_cached_flag, [this]()
        {
            try
            {
                _cached_range = uvc_xu_option< uint8_t >::get_range();
            }
            catch( std::exception const & e )
            {
                LOG_DEBUG( "sensors_config_mode: FW range query failed, keeping default {0,1,1,0}: " << e.what() );
            }
        } );
        return _cached_range;
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

        // Probe the FW once before advertising the option. Old FW builds on 2C/3C SKUs may not
        // implement XU selector 0x12 yet; skip registration cleanly in that case so the option
        // never shows in the UI (rather than showing broken and throwing on the first read).
        auto option = std::make_shared< sensors_config_mode_option >( raw_depth, dev );
        try
        {
            option->query();
        }
        catch( std::exception const & e )
        {
            LOG_DEBUG( "XU DUAL_RGB_MODE (0x12) not supported by this FW, option not registered: " << e.what() );
            return;
        }

        auto & depth_sensor = dev.get_depth_sensor();
        depth_sensor.register_option( RS2_OPTION_SENSORS_CONFIG_MODE, option );
        LOG_INFO( "RS2_OPTION_SENSORS_CONFIG_MODE registered on Stereo Module (XU DUAL_RGB_MODE = 0x12)" );
    }
}
