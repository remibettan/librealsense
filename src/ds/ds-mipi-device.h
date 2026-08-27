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
    // Single-shot ofstream write of the whole image: the kernel driver's
    // ds5_dfu_device_write processes the entire DFU inside one syscall holding
    // state->lock, so concurrent I2C on the bus is impossible. A background
    // thread emits progress-callback ticks and a 30 s heartbeat log while the
    // write blocks; it is joined on any exit path via RAII.
    class ds_mipi_device
    {
    public:
        explicit ds_mipi_device( std::shared_ptr< ds_device_common > device_common );

        void perform_dfu_write( const std::string & dfu_path,
                                const void * fw_image, std::size_t fw_image_size,
                                rs2_update_progress_callback_sptr progress_callback = nullptr,
                                int estimated_seconds = 120 ) const;

    private:
        std::shared_ptr< ds_device_common > _device_common;
    };
}
