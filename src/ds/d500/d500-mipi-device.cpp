// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include "d500-mipi-device.h"
#include "ds/ds-private.h"
#include "hw-monitor.h"
#include "types.h"

#include <stdexcept>

namespace librealsense
{
    d500_mipi_device::d500_mipi_device( const std::string & dfu_device_path,
                                        std::shared_ptr< ds_device_common > device_common,
                                        std::shared_ptr< hw_monitor > hw_monitor,
                                        std::shared_ptr< polling_error_handler > error_poller )
        : _dfu_device_path( dfu_device_path )
        , _mipi( std::move( device_common ), std::move( error_poller ) )
        , _hw_monitor( std::move( hw_monitor ) )
    {
    }

    void d500_mipi_device::update( const void * fw_image, int fw_image_size,
                                   rs2_update_progress_callback_sptr update_progress_callback ) const
    {
        // D5xx GMSL DFU wall-clock is ~30 min end-to-end (D400 default of 120 s does not fit).
        _mipi.perform_dfu_write( _dfu_device_path, fw_image,
                                 static_cast< std::size_t >( fw_image_size ),
                                 update_progress_callback, 1800 );

        // Send HWRST directly, without ds_device_common::hardware_reset(): on the
        // current HKR proto the device comes back in DFU/recovery mode after HWRST,
        // not operational, so simulate_device_reconnect() would feed the SDK a fake
        // add-event for a device that isn't really back and crash the enumerator.
        // Once the FW boots operational after HWRST, switch this back to
        // _ds_device_common->hardware_reset() to match d400_mipi_device.
        if( _hw_monitor )
        {
            command reset_cmd( ds::fw_cmd::HWRST );
            reset_cmd.require_response = false;
            try
            {
                _hw_monitor->send( reset_cmd );
            }
            catch( const std::exception & e )
            {
                // Device resets before ACKing; require_response is false so this is expected.
                LOG_DEBUG( "HWRST after DFU did not complete (expected during reset): " << e.what() );
            }
        }
    }
}
