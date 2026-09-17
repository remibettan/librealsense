// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.
//#cmake: static!

#include "../catch.h"
#include <src/ds/d500/mapping-timing.h>
#include <src/metadata-parser.h>

using namespace librealsense;

namespace
{
frame mapping_frame( md_type type, uint32_t counter, uint64_t aicv_timestamp, uint32_t hif_pts )
{
    frame f;
    platform::uvc_header uvc{};
    uvc.length = sizeof( uvc ) + sizeof( md_occupancy );
    uvc.timestamp = hif_pts;
    std::memcpy( f.additional_data.metadata_blob.data(), &uvc, sizeof( uvc ) );

    md_occupancy md{};
    md.header.md_type_id = type;
    md.header.md_size = sizeof( md );
    md.flags = static_cast< uint32_t >( md_occupancy_attributes::frame_counter_attribute )
        | static_cast< uint32_t >( md_occupancy_attributes::frame_timestamp_attribute );
    md.frame_counter = counter;
    md.frame_timestamp = aicv_timestamp;
    std::memcpy( f.additional_data.metadata_blob.data() + platform::uvc_header_size, &md, sizeof( md ) );
    f.additional_data.metadata_size = platform::uvc_header_size + sizeof( md );
    return f;
}
} // namespace

TEST_CASE( "Mapping keeps HIF PTS and AICV sensor timestamp distinct", "[mapping-timing]" )
{
    constexpr uint32_t hif_pts = 123456;
    constexpr uint64_t aicv_timestamp = 6400000000ULL;
    auto f = mapping_frame( md_type::META_DATA_INTEL_OCCUPANCY_ID, 99, aicv_timestamp, hif_pts );

    rs2_metadata_type frame_timestamp = 0;
    auto hif_parser = make_uvc_header_parser( &platform::uvc_header::timestamp );
    REQUIRE( hif_parser->find( f, &frame_timestamp ) );
    CHECK( frame_timestamp == hif_pts );

    uint32_t counter = 0;
    uint64_t sensor_timestamp = 0;
    REQUIRE( get_mapping_capture_timing( f, counter, sensor_timestamp ) );
    CHECK( counter == 99 );
    CHECK( sensor_timestamp == aicv_timestamp );
    CHECK( sensor_timestamp != frame_timestamp );
}

TEST_CASE( "Mapping hardware identity preserves FPS across dropped deliveries", "[mapping-timing]" )
{
    auto first = mapping_frame( md_type::META_DATA_INTEL_OCCUPANCY_ID, 101, 6400000000ULL, 1000 );
    auto next = mapping_frame( md_type::META_DATA_INTEL_OCCUPANCY_ID, 103, 6400066667ULL, 2000 );
    uint32_t first_counter = 0, next_counter = 0;
    uint64_t first_timestamp = 0, next_timestamp = 0;
    REQUIRE( get_mapping_capture_timing( first, first_counter, first_timestamp ) );
    REQUIRE( get_mapping_capture_timing( next, next_counter, next_timestamp ) );
    next.additional_data.frame_number = next_counter;
    next.additional_data.last_frame_number = first_counter;
    next.additional_data.timestamp = next_timestamp * 0.001;
    next.additional_data.last_timestamp = first_timestamp * 0.001;
    CHECK( next.calc_actual_fps() == Catch::Approx( 30. ).margin( 0.001 ) );
}

TEST_CASE( "Mapping accepts complete metadata and rejects invalid extensions", "[mapping-timing]" )
{
    for( auto type : { md_type::META_DATA_INTEL_OCCUPANCY_ID,
                       md_type::META_DATA_INTEL_POINT_CLOUD_ID } )
    {
        auto f = mapping_frame( type, 99, 6400000000ULL, 123456 );
        uint32_t counter = 0;
        uint64_t timestamp = 0;
        REQUIRE( get_mapping_capture_timing( f, counter, timestamp ) );

        for( unsigned size = 0; size < platform::uvc_header_size + sizeof( md_occupancy ); ++size )
        {
            f.additional_data.metadata_size = size;
            CHECK_FALSE( get_mapping_capture_timing( f, counter, timestamp ) );
        }

        f = mapping_frame( type, 99, 6400000000ULL, 123456 );
        auto *md = reinterpret_cast< md_occupancy * >(
            f.additional_data.metadata_blob.data() + platform::uvc_header_size );
        md->header.md_size = sizeof( md_occupancy ) - 1;
        CHECK_FALSE( get_mapping_capture_timing( f, counter, timestamp ) );
        md->header.md_size = sizeof( md_occupancy );
        md->flags &= ~static_cast< uint32_t >( md_occupancy_attributes::frame_counter_attribute );
        CHECK_FALSE( get_mapping_capture_timing( f, counter, timestamp ) );
        md->flags |= static_cast< uint32_t >( md_occupancy_attributes::frame_counter_attribute );
        md->frame_timestamp = 0;
        CHECK_FALSE( get_mapping_capture_timing( f, counter, timestamp ) );
    }
}
