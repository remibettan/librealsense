// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include "composite-mipi-xu-option.h"

#include <src/librealsense-exception.h>
#include <rsutils/string/from.h>

#include <cstring>

namespace librealsense {

composite_mipi_xu_option::composite_mipi_xu_option( std::weak_ptr< uvc_sensor > ep,
                                                    platform::extension_unit xu,
                                                    uint8_t ctrl_id,
                                                    uint32_t wire_size,
                                                    std::string description,
                                                    const dpp_control_desc & desc )
    : composite_xu_option( std::move( ep ), xu, ctrl_id, wire_size, std::move( description ) )
    , _desc( desc )
{
    // Validate the desc fits in the wire once, so the memcpy sites below can trust the invariant.
    const size_t required = sizeof( dpp_header ) + _desc.param_count * sizeof( int32_t );
    if( required > wire_size )
        throw invalid_value_exception( rsutils::string::from()
                                        << "composite_mipi_xu_option: dpp_control_desc (ctl_id=" << (int)_desc.ctl_id
                                        << ") needs " << required << " bytes but wire_size is only " << wire_size );
}

// The driver's compound U32 array carries only param_count active slots. Read that, then
// rebuild LibRS's dpp_header + 8-slot layout: header from the desc, active params from the
// driver, reserved slots zero-filled.
std::vector< uint8_t > composite_mipi_xu_option::read_from_device( platform::uvc_device & dev ) const
{
    std::vector< uint8_t > params( _desc.param_count * sizeof( int32_t ) );
    if( ! dev.get_xu( xu(), ctrl_id(), params.data(), (int)params.size() ) )
        throw invalid_value_exception( rsutils::string::from()
                                        << "MIPI composite get_xu(ctl_id=" << (int)_desc.ctl_id << ") failed" );

    std::vector< uint8_t > result( wire_size(), 0 );
    dpp_header hdr{};
    hdr.version = DPP_HEADER_CURRENT_VERSION;
    hdr.flags = 0;
    hdr.ctl_id = _desc.ctl_id;
    hdr.param_count = _desc.param_count;
    hdr.param_type = _desc.param_type;
    std::memcpy( result.data(), &hdr, sizeof( dpp_header ) );
    std::memcpy( result.data() + sizeof( dpp_header ), params.data(), params.size() );
    return result;
}

// Drop LibRS's dpp_header + reserved slots on the way out - the driver only wants the active
// params. Base's set_raw already enforces size == wire_size, but re-check here so this hook
// stays safe if invoked outside that path.
void composite_mipi_xu_option::write_to_device( platform::uvc_device & dev, const void * data, size_t size ) const
{
    const size_t required = sizeof( dpp_header ) + _desc.param_count * sizeof( int32_t );
    if( size < required )
        throw invalid_value_exception( rsutils::string::from()
                                        << "MIPI composite set_xu(ctl_id=" << (int)_desc.ctl_id
                                        << "): payload " << size << " bytes < required " << required );
    auto bytes = reinterpret_cast< const uint8_t * >( data );
    if( ! dev.set_xu( xu(), ctrl_id(), bytes + sizeof( dpp_header ), (int)( _desc.param_count * sizeof( int32_t ) ) ) )
        throw invalid_value_exception( rsutils::string::from()
                                        << "MIPI composite set_xu(ctl_id=" << (int)_desc.ctl_id << ") failed" );
}

// V4L2's VIDIOC_QUERY_EXT_CTRL only reports aggregate scalar bounds for a compound U32 array
// with heterogeneous slots; the driver validates SETs against the same per-slot arrays we
// mirror here in _desc. Build each bound as a full LibRS 38-byte struct (header + active
// params + reserved zero-fill), matching what composite_xu_option::get_raw_range expects.
platform::control_range composite_mipi_xu_option::read_range_from_device( platform::uvc_device & /*dev*/ ) const
{
    auto build = [this]( const int32_t * slots )
    {
        std::vector< uint8_t > v( wire_size(), 0 );
        dpp_header hdr{};
        hdr.version = DPP_HEADER_CURRENT_VERSION;
        hdr.flags = 0;
        hdr.ctl_id = _desc.ctl_id;
        hdr.param_count = _desc.param_count;
        hdr.param_type = _desc.param_type;
        std::memcpy( v.data(), &hdr, sizeof( dpp_header ) );
        std::memcpy( v.data() + sizeof( dpp_header ), slots, _desc.param_count * sizeof( int32_t ) );
        return v;
    };
    return platform::control_range( build( _desc.min ), build( _desc.max ), build( _desc.step ), build( _desc.def ) );
}

}  // namespace librealsense
