// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

// Plain uvc XU concrete of composite_xu_option: the SDK never touches the dpp_header on this
// path - FW authors it into the get/range payload and the app carries it back on set - so the
// hooks are simple passthroughs to uvc_device::{get,set,get_range}_xu. Instantiated by
// composite_xu_option::create() on the USB branch; nothing else constructs it directly.

#pragma once

#include "composite-xu-option.h"


namespace librealsense {

class composite_usb_xu_option : public composite_xu_option
{
public:
    composite_usb_xu_option( std::weak_ptr< uvc_sensor > ep,
                             platform::extension_unit xu,
                             uint8_t ctrl_id,
                             uint32_t wire_size,
                             std::string description );

protected:
    std::vector< uint8_t > read_from_device( platform::uvc_device & dev ) const override;
    void write_to_device( platform::uvc_device & dev, const void * data, size_t size ) const override;
    platform::control_range read_range_from_device( platform::uvc_device & dev ) const override;
};

}  // namespace librealsense
