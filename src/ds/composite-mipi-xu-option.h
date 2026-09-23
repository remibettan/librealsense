// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

// MIPI/GMSL concrete of composite_xu_option. The MIPI driver publishes each DPP control as a
// compound U32 V4L2 array carrying only the active param_count slots - no dpp_header, no
// reserved-slot padding. This class overrides composite_xu_option's I/O hooks to translate
// between that shape and LibRS's on-wire 38-byte {dpp_header + 8 int32 slots} struct, and to
// synthesize the per-slot range from the caller-supplied `dpp_control_desc` (V4L2 can't
// publish heterogeneous per-slot bounds for a compound U32 array via VIDIOC_QUERY_EXT_CTRL).
// Header handling is intentionally on this side only: on USB the FW authors the dpp_header
// and the SDK never overwrites it. Instantiated by composite_xu_option::create() on the MIPI
// branch; nothing else constructs it directly.

#pragma once

#include "composite-xu-option.h"

#include <librealsense2/h/rs_dpp_header.h>


namespace librealsense {

class composite_mipi_xu_option : public composite_xu_option
{
public:
    composite_mipi_xu_option( std::weak_ptr< uvc_sensor > ep,
                              platform::extension_unit xu,
                              uint8_t ctrl_id,
                              uint32_t wire_size,
                              std::string description,
                              const dpp_control_desc & desc );

protected:
    std::vector< uint8_t > read_from_device( platform::uvc_device & dev ) const override;
    void write_to_device( platform::uvc_device & dev, const void * data, size_t size ) const override;
    platform::control_range read_range_from_device( platform::uvc_device & dev ) const override;

private:
    // Stored by value so a caller-side temporary desc can't dangle - the caller-side pointer
    // arrays (min/max/step/def) still need static lifetime, which today's `static constexpr`
    // tables in each *_filter_feature.cpp satisfy.
    dpp_control_desc _desc;
};

}  // namespace librealsense
