// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include "composite-xu-option.h"
#include "composite-usb-xu-option.h"
#include "composite-mipi-xu-option.h"

#include <src/librealsense-exception.h>
#include <rsutils/string/from.h>

namespace librealsense {

composite_xu_option::composite_xu_option( std::weak_ptr< uvc_sensor > ep,
                                          platform::extension_unit xu,
                                          uint8_t ctrl_id,
                                          uint32_t wire_size,
                                          std::string description )
    : _ep( ep )
    , _xu( xu )
    , _ctrl_id( ctrl_id )
    , _wire_size( wire_size )
    , _description( description )
{
}

/* static */ std::shared_ptr< composite_xu_option >
composite_xu_option::create( bool is_mipi,
                             std::weak_ptr< uvc_sensor > ep,
                             platform::extension_unit xu,
                             uint8_t ctrl_id,
                             uint32_t wire_size,
                             std::string description,
                             const dpp_control_desc & desc )
{
    if( is_mipi )
        return std::make_shared< composite_mipi_xu_option >( std::move( ep ), xu, ctrl_id, wire_size,
                                                             std::move( description ), desc );
    return std::make_shared< composite_usb_xu_option >( std::move( ep ), xu, ctrl_id, wire_size,
                                                        std::move( description ) );
}

std::vector< uint8_t > composite_xu_option::get_raw() const
{
    auto ep = _ep.lock();
    if( ! ep )
        throw wrong_api_call_sequence_exception( "composite option is not available: sensor is not alive" );

    return ep->invoke_powered(
        [this]( platform::uvc_device & dev ) -> std::vector< uint8_t > { return read_from_device( dev ); } );
}

void composite_xu_option::set_raw( const void * data, size_t size )
{
    if( ! data )
        throw invalid_value_exception( "composite_xu_option::set_raw: data is null" );

    if( size != _wire_size )
        throw invalid_value_exception( rsutils::string::from()
                                        << "composite_xu_option::set_raw: data size " << size
                                        << " does not match this control's wire size " << _wire_size );

    auto ep = _ep.lock();
    if( ! ep )
        throw wrong_api_call_sequence_exception( "composite option is not available: sensor is not alive" );

    ep->invoke_powered(
        [this, data, size]( platform::uvc_device & dev ) { write_to_device( dev, data, size ); } );
}

std::vector< uint8_t > composite_xu_option::get_raw_range() const
{
    auto ep = _ep.lock();
    if( ! ep )
        throw wrong_api_call_sequence_exception( "composite option is not available: sensor is not alive" );

    auto uvc_range = ep->invoke_powered(
        [this]( platform::uvc_device & dev ) { return read_range_from_device( dev ); } );

    if( uvc_range.min.size() != _wire_size || uvc_range.max.size() != _wire_size || uvc_range.step.size() != _wire_size
        || uvc_range.def.size() != _wire_size )
        throw invalid_value_exception( "composite_xu_option::get_raw_range: device returned an unexpected range payload size" );

    // Generic {min, max, step, def} packing - see composite_option_interface::get_raw_range()
    // for the convention this follows.
    std::vector< uint8_t > result;
    result.reserve( 4 * _wire_size );
    result.insert( result.end(), uvc_range.min.begin(), uvc_range.min.end() );
    result.insert( result.end(), uvc_range.max.begin(), uvc_range.max.end() );
    result.insert( result.end(), uvc_range.step.begin(), uvc_range.step.end() );
    result.insert( result.end(), uvc_range.def.begin(), uvc_range.def.end() );
    return result;
}

}  // namespace librealsense
