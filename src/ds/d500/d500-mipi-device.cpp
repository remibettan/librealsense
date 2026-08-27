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
                                        std::shared_ptr< hw_monitor > hw_monitor )
        : _dfu_device_path( dfu_device_path )
        , _mipi( std::move( device_common ) )
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

        // HWRST kicks the D585 out of dfuMANIFEST_WAIT_RESET so it boots the new
        // image; without it the FW stays in the bootloader and even a driver
        // rmmod/modprobe cycle won't recover it (a physical power-cycle is needed).
        // Matches d400_mipi_device::update_signed_firmware() post-write pattern,
        // sent directly via hw_monitor instead of ds_device_common::hardware_reset()
        // to skip the simulate_device_reconnect side-effect — for D5xx GMSL the
        // device does not come back on its own, so a fake reconnect callback would
        // just create a phantom device notification.
        //
        // Gated off: the current D5xx GMSL proto hardware does not have the HW-reset
        // path enabled, so sending HWRST is a no-op at best and can hang the bus at
        // worst. Flip to true once the shipping HW wires it up.
        constexpr bool HWRST_ENABLED_ON_D5XX_PROTO = false;
        if( HWRST_ENABLED_ON_D5XX_PROTO && _hw_monitor )
        {
            command reset_cmd( ds::fw_cmd::HWRST );
            reset_cmd.require_response = false;
            try
            {
                _hw_monitor->send( reset_cmd );
            }
            catch( const std::exception & e )
            {
                // Device resets before ACKing; require_response is false so the send-side
                // exception is expected.
                LOG_DEBUG( "HWRST after DFU did not complete (expected during reset): " << e.what() );
            }
        }
    }
}
