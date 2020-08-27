// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2020 Intel Corporation. All Rights Reserved.

#include "merge.h"

namespace librealsense
{
	merge::merge()
		: generic_processing_block("merge"),
        _first_exp(0.f),
        _second_exp(0.f)
	{

	}

    // processing only framesets
	bool merge::should_process(const rs2::frame & frame)
	{
        if (!frame)
            return false;

        auto set = frame.as<rs2::frameset>();
        if (!set)
            return false;

        return true;
	}


	rs2::frame merge::process_frame(const rs2::frame_source& source, const rs2::frame& f)
	{
        // steps:
        // 1. check that depth and infrared frames are in arriving frameset (if not - return latest merge frame)
        // 2. add the frameset to vector of framesets
        // 3. check if size of this vector is at least 2 (if not - return latest merge frame)
        // 4. pop out both framesets from the vector
        // 5. apply merge algo
        // 6. save merge frame as latest merge frame
        // 7. return the merge frame

        // 1. check that depth and infrared frames are in arriving frameset (if not - return latest merge frame)
        auto fs = f.as<rs2::frameset>();
        if (!fs)
        {
            LOG_DEBUG("Merging PB - frame receive not a frameset");
            return f;
        }
        auto depth_frame = fs.get_depth_frame();
        if (!depth_frame)
        {
            LOG_DEBUG("Merging PB - no depth frame in frameset");
            return f;
        }

        // 2. add the frameset to vector of framesets
        /*int depth_sequ_size = depth_frame.get_frame_metadata(RS2_FRAME_METADATA_HDR_SEQUENCE_SIZE);
        if (depth_sequ_size != 2)
            throw invalid_value_exception(to_string() << "merge::process_frame(...) failed! sequence size must be 2 for merging pb.");
        int depth_sequ_id = depth_frame.get_frame_metadata(RS2_FRAME_METADATA_HDR_SEQUENCE_ID);

        // hdr sequ id shall be the same in the ir and the depth frames to save the frameset
        int ir_sequ_size = ir_frame.get_frame_metadata(RS2_FRAME_METADATA_HDR_SEQUENCE_SIZE);
        if (ir_sequ_size != 2)
            throw invalid_value_exception(to_string() << "merge::process_frame(...) failed! sequence size must be 2 for merging pb.");
        int ir_sequ_id = ir_frame.get_frame_metadata(RS2_FRAME_METADATA_HDR_SEQUENCE_ID);

        if ((depth_sequ_size != ir_sequ_size) || (depth_sequ_id != ir_sequ_id))
        {
            // timestamp to be also compared???
            // solution to be found...
            int a = 1;
        }*/

        //_framesets[depth_sequ_id] = fs;

        //to be used only till metadata is available
        float depth_exposure = depth_frame.get_frame_metadata(RS2_FRAME_METADATA_ACTUAL_EXPOSURE);
        if (_first_exp == depth_exposure)
            return f;
        if (_first_exp == 0.f)
        {
            _first_exp = depth_exposure;
        }
        else
        {
            _second_exp = depth_exposure;
        }
        _framesets_without_md[depth_exposure] = fs;
        //till here


        // 3. check if size of this vector is at least 2 (if not - return latest merge frame)
        //if (_framesets.size() < 2)
        if (_framesets_without_md.size() >= 2)
        {
            // 4. pop out both framesets from the vector
        //rs2::frameset fs_0 = _framesets[0];
        //rs2::frameset fs_1 = _framesets[1];
        //_framesets.clear();
            float min_exp = std::min(_first_exp, _second_exp);
            float max_exp = std::max(_first_exp, _second_exp);
            rs2::frameset fs_0 = _framesets_without_md[min_exp];
            rs2::frameset fs_1 = _framesets_without_md[max_exp];
            _first_exp = 0.f;
            _second_exp = 0.f;
            _framesets_without_md.clear();

            // 5. apply merge algo
            rs2::frame new_frame = merging_algorithm(source, fs_0, fs_1);
            if (new_frame)
            {
                // 6. save merge frame as latest merge frame
                _depth_merged_frame = new_frame;
            }
        }

        // 7. return the merge frame
        if(_depth_merged_frame)
            return _depth_merged_frame;

        return f;
	}


    rs2::frame merge::merging_algorithm(const rs2::frame_source& source, const rs2::frameset first_fs, const rs2::frameset second_fs)
    {
        auto first = first_fs;
        auto second = second_fs;

        auto first_depth = first.get_depth_frame();
        auto second_depth = second.get_depth_frame();
        auto first_ir = first.get_infrared_frame();
        auto second_ir = second.get_infrared_frame();

        // for now it happens that the exposures are the same
        // or that their order change
        // this should be corrected when getting the sequ id from frame metadata
        auto first_depth_exposure = first_depth.get_frame_metadata(RS2_FRAME_METADATA_ACTUAL_EXPOSURE);
        auto second_depth_exposure = second_depth.get_frame_metadata(RS2_FRAME_METADATA_ACTUAL_EXPOSURE);

        // new frame allocation
        auto vf = first_depth.as<rs2::depth_frame>();
        auto width = vf.get_width();
        auto height = vf.get_height();
        auto new_f = source.allocate_video_frame(first_depth.get_profile(), first_depth,
            vf.get_bytes_per_pixel(), width, height, vf.get_stride_in_bytes(), RS2_EXTENSION_DEPTH_FRAME);

        bool use_ir = (first_ir && second_ir);
        if (use_ir)
        {
            int depth_frame_counter = static_cast<int>(first_depth.get_frame_metadata(RS2_FRAME_METADATA_FRAME_COUNTER));
            int ir_frame_counter = static_cast<int>(first_ir.get_frame_metadata(RS2_FRAME_METADATA_FRAME_COUNTER));
            use_ir = (depth_frame_counter == ir_frame_counter);

            if (use_ir)
            {
                int depth_frame_counter = static_cast<int>(second_depth.get_frame_metadata(RS2_FRAME_METADATA_FRAME_COUNTER));
                int ir_frame_counter = static_cast<int>(second_ir.get_frame_metadata(RS2_FRAME_METADATA_FRAME_COUNTER));
                use_ir = (depth_frame_counter == ir_frame_counter);
            }
        }

        if (new_f)
        {
            auto ptr = dynamic_cast<librealsense::depth_frame*>((librealsense::frame_interface*)new_f.get());
            auto orig = dynamic_cast<librealsense::depth_frame*>((librealsense::frame_interface*)first_depth.get());

            auto d1 = (uint16_t*)first_depth.get_data();
            auto d0 = (uint16_t*)second_depth.get_data();

            auto new_data = (uint16_t*)ptr->get_frame_data();

            ptr->set_sensor(orig->get_sensor());

            memset(new_data, 0, width * height * sizeof(uint16_t));

            if (use_ir)
            {
                auto i1 = (uint8_t*)first_ir.get_data();
                auto i0 = (uint8_t*)second_ir.get_data();

                for (int i = 0; i < width * height; i++)
                {
                    if (i1[i] > 0x10 && i1[i] < 0xf0 && d1[i])
                        new_data[i] = d1[i];
                    else if (i0[i] > 0x10 && i0[i] < 0xf0 && d0[i])
                        new_data[i] = d0[i];
                    else if (d1[i] && d0[i])
                        new_data[i] = std::min(d0[i], d1[i]);
                    else if (d1[i])
                        new_data[i] = d1[i];
                    else if (d0[i])
                        new_data[i] = d0[i];
                    else
                        new_data[i] = 0;
                }
            }
            else
            {
                for (int i = 0; i < width * height; i++)
                {
                    if (d1[i])
                        new_data[i] = d1[i];
                    else if (d0[i])
                        new_data[i] = d0[i];
                    else if (new_data[i] == 0xffff)
                        new_data[i] = 0;
                    else
                        new_data[i] = 0;
                }
            }        

            return new_f;
        }
        return first_fs;
    }
}