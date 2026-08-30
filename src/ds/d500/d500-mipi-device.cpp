// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include "d500-mipi-device.h"
#include "ds/ds-device-common.h"

#include <chrono>
#include <thread>

namespace librealsense
{
    d500_mipi_device::d500_mipi_device( const std::string & dfu_device_path,
                                        std::shared_ptr< ds_device_common > device_common,
                                        std::shared_ptr< polling_error_handler > error_poller )
        : _dfu_device_path( dfu_device_path )
        , _device_common( device_common )
        , _mipi( std::move( device_common ), std::move( error_poller ) )
    {
    }

    void d500_mipi_device::update( const void * fw_image, int fw_image_size,
                                   rs2_update_progress_callback_sptr update_progress_callback ) const
    {
        // D5xx GMSL DFU wall-clock is ~30 min end-to-end (D400 default of 120 s does not fit).
        _mipi.perform_dfu_write( _dfu_device_path, fw_image,
                                 static_cast< std::size_t >( fw_image_size ),
                                 update_progress_callback, 1800 );

        // Restart the device to reconstruct with the new version information
        // simulate_device_reconnect takes 5 seconds to fake the reconnect cycle
        hardware_reset();
        std::this_thread::sleep_for( std::chrono::seconds( 5 ) );
    }

    void d500_mipi_device::hardware_reset() const
    {
        _device_common->hardware_reset( std::chrono::seconds( 5 ) );
    }
}
