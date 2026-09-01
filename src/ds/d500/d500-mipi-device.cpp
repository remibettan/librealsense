// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include "d500-mipi-device.h"
#include "ds/ds-device-common.h"

#include <algorithm>
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
        // Rig-measured D5xx GMSL DFU throughput: ~13 s per 128 KiB chunk (HKR limits
        // the DFU status protocol pace, not the I2C bus). Derive the progress-bar
        // estimate from the actual image size so both full (~19 MB, ~30 min) and
        // compressed (~7 MB, ~12 min) images map to real elapsed time.
        constexpr int DFU_CHUNK_BYTES  = 128 * 1024;
        constexpr int SEC_PER_CHUNK    = 13;
        int chunks = ( fw_image_size + DFU_CHUNK_BYTES - 1 ) / DFU_CHUNK_BYTES;
        int estimated_seconds = std::max( 1, chunks * SEC_PER_CHUNK );

        _mipi.perform_dfu_write( _dfu_device_path, fw_image,
                                 static_cast< std::size_t >( fw_image_size ),
                                 update_progress_callback, estimated_seconds,
                                 [this, update_progress_callback]() {
                                     // Wait for the FW's own dfuMANIFEST_WAIT_RESET → dfuIDLE
                                     // reset to complete before we send our HWRST. On the HKR
                                     // proto, an HWRST arriving mid-manifest-reset is interpreted
                                     // as a recovery-mode request and leaves the device in
                                     // DFU/recovery instead of booting the new operational image.
                                     std::this_thread::sleep_for( std::chrono::seconds( 10 ) );
                                     // Now the FW is on the new operational image — a normal
                                     // hardware_reset kicks off simulate_device_reconnect so the
                                     // SDK re-enumerates the device transparently.
                                     hardware_reset();
                                     std::this_thread::sleep_for( std::chrono::seconds( 5 ) );
                                     // Terminal 100% only after the post-DFU reset is done, so
                                     // the viewer's DFU dialog transitions to the OK state at
                                     // the moment the device is really back — not the moment
                                     // the DFU chardev closed.
                                     if( update_progress_callback )
                                         update_progress_callback->on_update_progress( 1.f );
                                 } );
    }

    void d500_mipi_device::hardware_reset() const
    {
        _device_common->hardware_reset( std::chrono::seconds( 5 ) );
    }
}
