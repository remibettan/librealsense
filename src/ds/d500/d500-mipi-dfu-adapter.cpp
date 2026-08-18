// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include "d500-mipi-dfu-adapter.h"
#include "ds/ds-device-common.h"
#include "types.h"

#include <algorithm>
#include <chrono>
#include <fstream>
#include <stdexcept>

namespace librealsense
{
    d500_mipi_dfu_adapter::d500_mipi_dfu_adapter( const std::string & dfu_device_path,
                                                   std::shared_ptr< ds_device_common > device_common )
        : _dfu_device_path( dfu_device_path )
        , _device_common( std::move( device_common ) )
    {
    }

    void d500_mipi_dfu_adapter::update( const void * fw_image, int fw_image_size,
                                        rs2_update_progress_callback_sptr update_progress_callback ) const
    {
        // Pause per-sensor options watchers for the duration of the DFU. Mirrors
        // d400_mipi_device::update_non_const() (d400-mipi-device.cpp): the watcher's
        // periodic option reads share the d4xx state->lock with the DFU chardev.
        options_watcher_pause_guard guard( *_device_common );

        // Same write API as the base update_device::update_mipi() (std::ofstream),
        // but with 128 KiB chunks to match `cat` and reduce driver-side round-trips
        // over the D5xx GMSL DFU chardev.
        const size_t transfer_size = 128 * 1024;
        size_t remaining_bytes = fw_image_size;
        size_t offset = 0;

        std::ofstream fw_path_in_device( _dfu_device_path.c_str(), std::ios::binary );
        if( ! fw_path_in_device )
            throw std::runtime_error( "Firmware Update failed - wrong path or permissions missing" );

        auto last_heartbeat = std::chrono::steady_clock::now();
        while( remaining_bytes > 0 )
        {
            size_t chunk_size = std::min( transfer_size, remaining_bytes );
            auto curr_block = ( (uint8_t *)fw_image + offset );
            fw_path_in_device.write( reinterpret_cast< const char * >( curr_block ), chunk_size );
            if( ! fw_path_in_device )
                throw std::runtime_error( "Firmware Update failed - DFU chardev write error at offset "
                                          + std::to_string( offset ) );

            remaining_bytes -= chunk_size;
            offset += chunk_size;

            float progress = (float)offset / (float)fw_image_size;
            if( update_progress_callback )
                update_progress_callback->on_update_progress( progress );

            auto now = std::chrono::steady_clock::now();
            if( now - last_heartbeat >= std::chrono::seconds( 30 ) )
            {
                LOG_INFO( "D5xx MIPI DFU in progress: " << offset << "/" << fw_image_size
                          << " bytes (" << int( progress * 100 ) << "%)" );
                last_heartbeat = now;
            }
        }
        fw_path_in_device.close();
        if( ! fw_path_in_device )
            throw std::runtime_error( "Firmware Update failed - DFU chardev flush/close error" );
        LOG_INFO( "Firmware Update for D5xx MIPI device done." );
    }
}
