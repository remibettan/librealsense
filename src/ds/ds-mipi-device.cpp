// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include "ds-mipi-device.h"
#include "ds-device-common.h"
#include "types.h"

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <fstream>
#include <stdexcept>
#include <string>


namespace librealsense
{
    ds_mipi_device::ds_mipi_device( std::shared_ptr< ds_device_common > device_common )
        : _device_common( std::move( device_common ) )
    {
    }

    void ds_mipi_device::perform_dfu_write( const std::string & dfu_path,
                                            const void * fw_image, std::size_t fw_image_size,
                                            rs2_update_progress_callback_sptr progress_callback,
                                            std::size_t chunk_size ) const
    {
        // Pause per-sensor options watchers for the duration of the DFU. The watcher's
        // periodic option reads share the d4xx state->lock with the DFU chardev.
        options_watcher_pause_guard guard( *_device_common );

        std::ofstream fw_path_in_device( dfu_path.c_str(), std::ios::binary );
        if( ! fw_path_in_device )
            throw std::runtime_error( "Firmware Update failed - wrong path or permissions missing: " + dfu_path );

        std::size_t remaining_bytes = fw_image_size;
        std::size_t offset = 0;
        auto last_heartbeat = std::chrono::steady_clock::now();

        while( remaining_bytes > 0 )
        {
            std::size_t bytes = std::min( chunk_size, remaining_bytes );
            const char * curr_block = reinterpret_cast< const char * >( fw_image ) + offset;
            fw_path_in_device.write( curr_block, bytes );
            if( ! fw_path_in_device )
                throw std::runtime_error( "Firmware Update failed - DFU chardev write error at offset "
                                          + std::to_string( offset ) );

            remaining_bytes -= bytes;
            offset += bytes;

            float progress = (float)offset / (float)fw_image_size;
            if( progress_callback )
                progress_callback->on_update_progress( progress );

            auto now = std::chrono::steady_clock::now();
            if( now - last_heartbeat >= std::chrono::seconds( 30 ) )
            {
                LOG_INFO( "MIPI DFU in progress: " << offset << "/" << fw_image_size
                          << " bytes (" << int( progress * 100 ) << "%)" );
                last_heartbeat = now;
            }
        }
        fw_path_in_device.close();
        if( ! fw_path_in_device )
            throw std::runtime_error( "Firmware Update failed - DFU chardev flush/close error: " + dfu_path );
        LOG_INFO( "MIPI DFU write complete for " << dfu_path );
    }
}
