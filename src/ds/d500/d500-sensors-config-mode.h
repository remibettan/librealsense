// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.
#pragma once

#include <src/platform/uvc-option.h>

#include <mutex>


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
    //
    // set() is a no-op when the requested value equals the value currently reported by the FW
    // (avoids a needless reboot); get_range() caches the FW-reported range on first successful
    // query (falls back to the hardcoded 0/1 range if the FW query fails).
    class sensors_config_mode_option : public uvc_xu_option< uint8_t >
    {
    public:
        sensors_config_mode_option( const std::weak_ptr< uvc_sensor > & ep, d500_device & dev );

        void set( float value ) override;
        option_range get_range() const override;
        const char * get_value_description( float value ) const override;

    private:
        d500_device & _dev;
        mutable std::once_flag _range_cached_flag;
        mutable option_range _cached_range = { 0.f, 1.f, 1.f, 0.f };
    };

    // Registers RS2_OPTION_SENSORS_CONFIG_MODE on the given device's depth sensor. Call from
    // rs5x5_device / rs5x5_dedicated_color_device / rs5x5_gmsl_dedicated_color_device ctors,
    // after d500_device::init has created the depth sensor.
    void register_sensors_config_mode_option( d500_device & dev );
}
