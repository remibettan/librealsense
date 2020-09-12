/* License: Apache 2.0. See LICENSE file in root directory.
Copyright(c) 2020 Intel Corporation. All Rights Reserved. */


#pragma once

#include "synthetic-stream.h"
#include "option.h"

namespace librealsense
{
    class merge_filter : public generic_processing_block
    {
    public:
        merge_filter();

    protected:
        bool should_process(const rs2::frame& frame) override;
        rs2::frame process_frame(const rs2::frame_source& source, const rs2::frame& f) override;


    private:
        const int IR_UNDER_SATURATED_VALUE = 0x10; // 16
        const int IR_OVER_SATURATED_VALUE = 0xf0; // 240
        bool is_infrared_valid(uint8_t ir_value) const;

        bool check_frames_mergeability(const rs2::frameset first_fs, const rs2::frameset second_fs, bool& use_ir);
        rs2::frame merging_algorithm(const rs2::frame_source& source, const rs2::frameset first_fs, 
            const rs2::frameset second_fs, const bool use_ir);

        bool should_ir_be_used_for_merging(const rs2::depth_frame& first_depth, const rs2::video_frame& first_ir,
            const rs2::depth_frame& second_depth, const rs2::video_frame& second_ir) const;

        std::map<int, rs2::frameset> _framesets;
        rs2::frame _depth_merged_frame;
    };
    MAP_EXTENSION(RS2_EXTENSION_MERGE_FILTER, librealsense::merge_filter);
}
