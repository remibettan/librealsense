// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#pragma once

#include <librealsense2/hpp/rs_types.hpp>

#include <cstddef>
#include <memory>
#include <string>


namespace librealsense
{
    class ds_device_common;

    // Shared MIPI DFU write flow used by d400_mipi_device and d500_mipi_device.
    // Pauses the ds_device_common options watchers for the duration of the write,
    // performs a chunked ofstream write to the DFU chardev with a 30 s heartbeat
    // log, and validates the stream state on every chunk and on close so an EIO
    // never walks progress silently to 100 %. Family-specific post-write actions
    // (hardware_reset() on D400, HWRST on D500) stay in the caller.
    class ds_mipi_device
    {
    public:
        explicit ds_mipi_device( std::shared_ptr< ds_device_common > device_common );

        void perform_dfu_write( const std::string & dfu_path,
                                const void * fw_image, std::size_t fw_image_size,
                                rs2_update_progress_callback_sptr progress_callback = nullptr,
                                std::size_t chunk_size = 128U * 1024U ) const;

    private:
        std::shared_ptr< ds_device_common > _device_common;
    };
}
