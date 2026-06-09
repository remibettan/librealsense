// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.
#pragma once

#include <src/proc/minz-embedded-filter.h>
#include <src/option.h>
#include <src/uvc-sensor.h>

#include <functional>
#include <memory>

namespace librealsense {

// USB XU wrapper for the MinZ ("Improved Close Range Depth") filter.
// Spec: HDRD MinZ Depth - Host API Interface §2 (USB API).
//
// Wire format: 38-byte dppc_ctl payload on Depth XU selector 0x14 (csEU_CONTROL_MINZ_FILTER).
// The class exposes ONLY the `enable` field (params[0]) as a float-valued option;
// the other three fields (downscale_ratio / disparity_shift / threshold) are read from
// the device via GET_CUR and echoed back unchanged on SET_CUR so the FW state stays
// internally consistent.
class minz_xu_option : public option
{
public:
    minz_xu_option( std::weak_ptr< uvc_sensor > ep );

    void set( float value ) override;
    float query() const override;
    option_range get_range() const override { return { 0.f, 1.f, 1.f, 0.f }; }
    bool is_enabled() const override { return true; }
    const char * get_description() const override
    {
        return "Improved Close Range Depth - enable/disable the MinZ depth merge (pre-stream only)";
    }
    void enable_recording( std::function< void( const option & ) > rec ) override { _record = rec; }

    static constexpr uint8_t  XU_SELECTOR = 0x14;  // csEU_CONTROL_MINZ_FILTER
    static constexpr uint16_t CTL_ID      = 0x0008;  // dpp_minz_filter

private:
    std::weak_ptr< uvc_sensor > _ep;
    std::function< void( const option & ) > _record = []( const option & ) {};
};


// USB-backed concrete of minz_embedded_filter — surfaces in the viewer's
// Embedded Filters panel under the depth sensor with a single Enable option.
class d500_minz_embedded_filter : public minz_embedded_filter
{
public:
    explicit d500_minz_embedded_filter( std::weak_ptr< uvc_sensor > raw_depth_ep );

    rs2_embedded_filter_type get_type() const override { return RS2_EMBEDDED_FILTER_TYPE_MINZ; }
};

}  // namespace librealsense
