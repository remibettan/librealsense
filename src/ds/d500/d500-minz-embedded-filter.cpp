// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include "d500-minz-embedded-filter.h"
#include "ds/ds-private.h"
#include <src/librealsense-exception.h>
#include <rsutils/string/from.h>

#include <cstdint>
#include <cstring>

namespace librealsense {

namespace {

// 38-byte dppc_ctl wire payload, packed per HDRD MinZ Depth - Host API Interface §2.2.
#pragma pack(push, 1)
struct dppc_ctl
{
    uint8_t  version;       // 0x01
    uint8_t  flags;         // 0x01
    uint16_t ctl_id;        // 0x0008 (dpp_minz_filter)
    uint8_t  param_count;   // 4
    uint8_t  param_type;    // 0x00 (integer)
    int32_t  params[8];     // [enable, downscale_ratio, disparity_shift, threshold, reserved...]
};
#pragma pack(pop)
static_assert( sizeof( dppc_ctl ) == 38, "dppc_ctl must be exactly 38 bytes" );

// Fill the header fields in case FW didn't echo them back on GET_CUR.
void stamp_header( dppc_ctl & p )
{
    p.version     = 0x01;
    p.flags       = 0x01;
    p.ctl_id      = minz_xu_option::CTL_ID;
    p.param_count = 4;
    p.param_type  = 0x00;
}

}  // anonymous

minz_xu_option::minz_xu_option( std::weak_ptr< uvc_sensor > ep )
    : _ep( ep )
{
}

void minz_xu_option::set( float value )
{
    if( value != 0.f && value != 1.f )
        throw invalid_value_exception( rsutils::string::from() << "MinZ Enable must be 0 or 1; got " << value );

    auto ep = _ep.lock();
    if( ! ep )
        throw invalid_value_exception( "MinZ: depth sensor not alive for set" );

    if( ep->is_streaming() )
        throw wrong_api_call_sequence_exception( "MinZ Enable is pre-stream only" );

    ep->invoke_powered(
        [this, value]( platform::uvc_device & dev )
        {
            dppc_ctl payload = {};

            // Read current payload first so we preserve the FW's current
            // downscale_ratio / disparity_shift / threshold values.
            if( ! dev.get_xu( ds::depth_xu,
                              XU_SELECTOR,
                              reinterpret_cast< uint8_t * >( &payload ),
                              sizeof( payload ) ) )
            {
                throw invalid_value_exception( rsutils::string::from()
                                               << "MinZ get_xu(0x14) failed before set; errno=" << strerror( errno ) );
            }

            stamp_header( payload );
            payload.params[0] = ( value != 0.f ) ? 1 : 0;

            if( ! dev.set_xu( ds::depth_xu,
                              XU_SELECTOR,
                              reinterpret_cast< uint8_t * >( &payload ),
                              sizeof( payload ) ) )
            {
                throw invalid_value_exception( rsutils::string::from()
                                               << "MinZ set_xu(0x14) failed; errno=" << strerror( errno ) );
            }

            _record( *this );
        } );
}

float minz_xu_option::query() const
{
    auto ep = _ep.lock();
    if( ! ep )
        return 0.f;

    return ep->invoke_powered(
        [this]( platform::uvc_device & dev ) -> float
        {
            dppc_ctl payload = {};
            if( ! dev.get_xu( ds::depth_xu,
                              XU_SELECTOR,
                              reinterpret_cast< uint8_t * >( &payload ),
                              sizeof( payload ) ) )
            {
                throw invalid_value_exception( rsutils::string::from()
                                               << "MinZ get_xu(0x14) failed; errno=" << strerror( errno ) );
            }
            return static_cast< float >( payload.params[0] );
        } );
}


d500_minz_embedded_filter::d500_minz_embedded_filter( std::weak_ptr< uvc_sensor > raw_depth_ep )
{
    auto opt = std::make_shared< minz_xu_option >( raw_depth_ep );
    register_option( RS2_OPTION_EMBEDDED_FILTER_ENABLED, opt );
    _options_watcher.register_option( RS2_OPTION_EMBEDDED_FILTER_ENABLED, opt );
}

}  // namespace librealsense
