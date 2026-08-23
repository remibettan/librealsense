// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.
#pragma once

#include "frame.h"
#include "perception-frame.h"
#include "core/extension.h"
#include <librealsense2/h/rs_types.h>
#include <rsutils/string/from.h>
#include <atomic>
#include <cstddef>

namespace librealsense {

class object_detection_frame : public perception_frame
{
public:
    // Frames received over the object detection stream are binary blobs laid out as a frame header,
    // a payload header, and an array of versioned detection entries (see object_detection_payload_entry_v2/v3).

    static constexpr uint32_t MAGIC_NUMBER = 0x5445444F;  // ASCII "ODET" as a little-endian uint32
    static constexpr uint32_t MAX_DETECTIONS = 64;
    static constexpr uint16_t VERSION_V2 = 0x0200;
    static constexpr uint16_t VERSION_V3 = 0x0300;  // Adds center-of-mass world/image coordinates

    enum class source : uint8_t
    {
        RGB = 0,
        DEPTH = 1
    };

#pragma pack( push, 1 )
    struct object_detection_frame_header
    {
        uint32_t magic_number;  // Must equal MAGIC_NUMBER
        uint16_t version;       // major.minor SDK/HKR API version
        uint8_t data_type;      // 0 = object detection
        uint8_t flags;
        uint32_t size;          // Expected frame size, header excluded
        uint32_t spare;
        uint32_t crc32;         // CRC of the data, header excluded
    };

    struct object_detection_payload_header
    {
        double timestamp_ms;       // Frame timestamp [milliseconds]
        uint64_t frame_id;         // Frame counter
        uint16_t number_of_detections;
        uint8_t source;            // 0 = RGB, 1 = depth
        uint32_t source_frame_id;  // ID of the frame detection was calculated on
    };

    struct object_detection_payload_entry_v2
    {
        uint16_t detection_id;    // For detection/tracking traceability
        uint8_t detection_type;   // 0 = person
        uint8_t confidence;       // 0-100
        uint16_t top_left_x;      // Bounding box top-left X [pixels]
        uint16_t top_left_y;      // Bounding box top-left Y [pixels]
        uint16_t bottom_right_x;  // Bounding box bottom-right X [pixels]
        uint16_t bottom_right_y;  // Bounding box bottom-right Y [pixels]
        float distance;           // Object distance from camera [meters]
    };

    struct object_detection_payload_entry_v3
    {
        object_detection_payload_entry_v2 detection;
        float world_x;             // Camera coordinate [meters]
        float world_y;             // Camera coordinate [meters]
        float world_z;             // Optical-axis coordinate [meters]
        float image_x;             // COM column [source-image pixels]
        float image_y;             // COM row [source-image pixels]
    };
#pragma pack( pop )

    static constexpr size_t FRAME_HEADER_SIZE = sizeof( object_detection_frame_header );
    static constexpr size_t PAYLOAD_HEADER_SIZE = sizeof( object_detection_payload_header );
    static constexpr size_t MIN_FRAME_SIZE = FRAME_HEADER_SIZE + PAYLOAD_HEADER_SIZE;

    static_assert( FRAME_HEADER_SIZE == 20, "Object Detection frame header ABI must be 20 bytes" );
    static_assert( PAYLOAD_HEADER_SIZE == 23, "Object Detection payload header ABI must be 23 bytes" );
    static_assert( sizeof( object_detection_payload_entry_v2 ) == 16, "Object Detection v2 entry ABI must be 16 bytes" );
    static_assert( sizeof( object_detection_payload_entry_v3 ) == 36, "Object Detection v3 entry ABI must be 36 bytes" );
    static_assert( MIN_FRAME_SIZE == 43, "Object Detection minimum frame ABI must be 43 bytes" );

    object_detection_frame() = default;
    object_detection_frame( object_detection_frame && other );
    object_detection_frame & operator=( object_detection_frame && other );

    // Decoded detection. COM fields (world_position, image_x/y) are populated only when
    // com_valid is set, which requires a V3 frame with a firmware-reported valid center of mass.
    struct decoded_object_detection
    {
        uint16_t detection_id = 0;
        uint8_t detection_type = 0;
        uint8_t confidence = 0;
        uint16_t top_left_x = 0;
        uint16_t top_left_y = 0;
        uint16_t bottom_right_x = 0;
        uint16_t bottom_right_y = 0;
        float distance = 0.f;
        rs2_vector world_position = {};
        float image_x = 0.f;
        float image_y = 0.f;
        bool com_valid = false;
    };

    size_t get_detection_count() const;
    decoded_object_detection get_detection( size_t index ) const;
    object_detection_payload_header get_payload_header() const;
    uint16_t get_version() const;

private:
    bool validate() const;
    bool validate_payload() const;
    size_t entry_size() const;

    mutable std::atomic_bool _validated{ false };
};

MAP_EXTENSION(RS2_EXTENSION_OBJECT_DETECTION_FRAME, librealsense::object_detection_frame);

}  // namespace librealsense
