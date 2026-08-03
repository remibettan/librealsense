// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.
#pragma once

#include <src/platform/uvc-option.h>


namespace librealsense
{
    class d500_device;

    // Boolean sensor-configuration selector exposed as RS2_OPTION_SENSORS_CONFIG_MODE on D5x5
    // SKUs whose FW supports the depth_xu DUAL_RGB_MODE (0x12) selector — FW-spec name
    // csEU_CONTROL_ADVANCED_DEVICE_MODE:
    //   0 = dedicated color sensor (3C variants)
    //   1 = dual RGB, no dedicated color sensor (2C variants)
    // Single-byte GET/SET; set() writes the XU and triggers hardware_reset so the device
    // re-enumerates under the target PID.
    class sensors_config_mode_option : public uvc_xu_option< uint8_t >
    {
    public:
        sensors_config_mode_option( const std::weak_ptr< uvc_sensor > & ep, d500_device & dev );

        void set( float value ) override;
        option_range get_range() const override;
        const char * get_value_description( float value ) const override;

    private:
        d500_device & _dev;
    };

    // Registers RS2_OPTION_SENSORS_CONFIG_MODE on the given device's depth sensor. Call from
    // rs5x5_device / rs5x5_dedicated_color_device / rs5x5_gmsl_dedicated_color_device ctors,
    // after d500_device::init has created the depth sensor.
    void register_sensors_config_mode_option( d500_device & dev );
}
