// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include "composite-usb-xu-option.h"

#include <src/librealsense-exception.h>
#include <rsutils/string/from.h>

namespace librealsense {

composite_usb_xu_option::composite_usb_xu_option( std::weak_ptr< uvc_sensor > ep,
                                                  platform::extension_unit xu,
                                                  uint8_t ctrl_id,
                                                  uint32_t wire_size,
                                                  std::string description )
    : composite_xu_option( std::move( ep ), xu, ctrl_id, wire_size, std::move( description ) )
{
}

std::vector< uint8_t > composite_usb_xu_option::read_from_device( platform::uvc_device & dev ) const
{
    std::vector< uint8_t > data( wire_size() );
    if( ! dev.get_xu( xu(), ctrl_id(), data.data(), (int)wire_size() ) )
        throw invalid_value_exception( rsutils::string::from() << "get_xu(id=" << (int)ctrl_id() << ") failed!" );
    return data;
}

void composite_usb_xu_option::write_to_device( platform::uvc_device & dev, const void * data, size_t size ) const
{
    if( ! dev.set_xu( xu(), ctrl_id(), reinterpret_cast< const uint8_t * >( data ), (int)size ) )
        throw invalid_value_exception( rsutils::string::from() << "set_xu(id=" << (int)ctrl_id() << ") failed!" );
}

platform::control_range composite_usb_xu_option::read_range_from_device( platform::uvc_device & dev ) const
{
    return dev.get_xu_range( xu(), ctrl_id(), (int)wire_size() );
}

}  // namespace librealsense
