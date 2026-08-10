// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include <unit-tests/catch.h>

#include <src/object-detection-frame.h>
#include <rsutils/number/crc32.h>


namespace {

using od_frame = librealsense::object_detection_frame;


od_frame make_frame( uint16_t detection_count = 1, size_t trailing_padding = 0 )
{
    od_frame frame;
    auto const payload_size = od_frame::PAYLOAD_HEADER_SIZE + detection_count * od_frame::ENTRY_SIZE;
    auto const frame_size = od_frame::FRAME_HEADER_SIZE + payload_size;
    frame.data.resize( frame_size + trailing_padding );

    auto * payload = reinterpret_cast< od_frame::object_detection_payload * >( frame.data.data() );
    payload->header.magic_number = od_frame::MAGIC_NUMBER;
    payload->header.version = 0x0200;
    payload->header.data_type
        = static_cast< uint8_t >( librealsense::perception_frame::type::OBJECT_DETECTION );
    payload->header.size = static_cast< uint32_t >( payload_size );
    payload->timestamp_ms = 1234.5;
    payload->frame_id = 42;
    payload->number_of_detections = detection_count;
    payload->source = static_cast< uint8_t >( od_frame::source::RGB );
    payload->source_frame_id = 41;

    for( uint16_t i = 0; i < detection_count; ++i )
    {
        auto & detection = payload->detections[i];
        detection.detection_id = static_cast< uint16_t >( 100 + i );
        detection.detection_type = 0;
        detection.confidence = 90;
        detection.top_left_x = 10;
        detection.top_left_y = 20;
        detection.bottom_right_x = 110;
        detection.bottom_right_y = 220;
        detection.distance = 1.25f;
    }

    auto const payload_data = frame.data.data() + od_frame::FRAME_HEADER_SIZE;
    payload->header.crc32 = rsutils::number::calc_crc32( payload_data, payload_size );
    return frame;
}

}  // namespace


TEST_CASE( "Object Detection frame matches the firmware ABI", "[object-detection][frame]" )
{
    auto frame = make_frame();

    REQUIRE( frame.get_detection_count() == 1 );
    auto const detection = frame.get_detection( 0 );
    CHECK( detection.detection_id == 100 );
    CHECK( detection.confidence == 90 );
    CHECK( detection.bottom_right_x == 110 );
    CHECK( detection.distance == Catch::Approx( 1.25f ) );
}


TEST_CASE( "Object Detection frame accepts UVC trailing padding", "[object-detection][frame]" )
{
    auto frame = make_frame( 1, 32 );

    CHECK( frame.get_detection_count() == 1 );
}


TEST_CASE( "Object Detection frame rejects a CRC mismatch", "[object-detection][frame]" )
{
    auto frame = make_frame();
    frame.data.back() ^= 0x01;

    CHECK( frame.get_detection_count() == 0 );
    CHECK_THROWS_AS( frame.get_detection( 0 ), std::out_of_range );
}


TEST_CASE( "Object Detection frame rejects a size mismatch", "[object-detection][frame]" )
{
    auto frame = make_frame();
    frame.data.pop_back();

    CHECK( frame.get_detection_count() == 0 );
}
