// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include "ds-mipi-device.h"
#include "ds-device-common.h"
#include "types.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <fstream>
#include <stdexcept>
#include <string>
#include <thread>


namespace librealsense
{
    ds_mipi_device::ds_mipi_device( std::shared_ptr< ds_device_common > device_common )
        : _device_common( std::move( device_common ) )
    {
    }

    void ds_mipi_device::perform_dfu_write( const std::string & dfu_path,
                                            const void * fw_image, std::size_t fw_image_size,
                                            rs2_update_progress_callback_sptr progress_callback,
                                            int estimated_seconds ) const
    {
        options_watcher_pause_guard guard( *_device_common );

        std::ofstream fw_path_in_device( dfu_path.c_str(), std::ios::binary );
        if( ! fw_path_in_device )
            throw std::runtime_error( "Firmware Update failed - wrong path or permissions missing: " + dfu_path );

        // Progress + heartbeat thread; RAII joiner covers the throw path too.
        std::atomic< bool > done{ false };
        std::thread heartbeat( [&]() {
            auto start = std::chrono::steady_clock::now();
            auto last_log = start;
            while( ! done.load() )
            {
                std::this_thread::sleep_for( std::chrono::milliseconds( 500 ) );
                auto now = std::chrono::steady_clock::now();
                int elapsed = int( std::chrono::duration_cast< std::chrono::seconds >( now - start ).count() );
                if( progress_callback )
                    progress_callback->on_update_progress(
                        std::min( float( elapsed ) / float( estimated_seconds ), 0.99f ) );
                if( now - last_log >= std::chrono::seconds( 30 ) )
                {
                    LOG_INFO( "MIPI DFU in progress: elapsed " << elapsed << " s" );
                    last_log = now;
                }
            }
        } );
        struct joiner { std::atomic< bool > & d; std::thread & t; ~joiner() { d = true; if( t.joinable() ) t.join(); } };
        joiner _j{ done, heartbeat };

        fw_path_in_device.write( reinterpret_cast< const char * >( fw_image ), fw_image_size );
        if( ! fw_path_in_device )
            throw std::runtime_error( "Firmware Update failed - DFU chardev write error: " + dfu_path );

        fw_path_in_device.close();
        if( ! fw_path_in_device )
            throw std::runtime_error( "Firmware Update failed - DFU chardev flush/close error: " + dfu_path );
        if( progress_callback )
            progress_callback->on_update_progress( 1.0f );
        LOG_INFO( "MIPI DFU write complete for " << dfu_path );
    }
}
