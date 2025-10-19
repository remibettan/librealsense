// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2017 RealSense, Inc. All Rights Reserved.

#pragma once
#include "types.h"
#include <fstream>


namespace librealsense
{
    const size_t PRESISTENCY_LUT_SIZE = 256;

    class temporal_filter : public depth_processing_block
    {
    public:
        temporal_filter();

    protected:
        void    update_configuration(const rs2::frame& f);
        rs2::frame process_frame(const rs2::frame_source& source, const rs2::frame& f) override;

        rs2::frame prepare_target_frame(const rs2::frame& f, const rs2::frame_source& source);

        template<typename T>
        void temp_jw_smooth(void* frame_data, void * _last_frame_data, uint8_t *history)
        {
            static_assert((std::is_arithmetic<T>::value), "temporal filter assumes numeric types");

            const bool fp = (std::is_floating_point<T>::value);

            T delta_z = static_cast<T>(_delta_param);

            auto frame          = reinterpret_cast<T*>(frame_data);
            auto _last_frame    = reinterpret_cast<T*>(_last_frame_data);

            unsigned char mask = 1 << _cur_frame_index;

            // Copy locally, to remove need for a lock.
            float alpha = _alpha_param;
            float one_minus_alpha = 1.f - alpha;

			// create folder and change dir to it
			_saving_data_folder_path = "temp_jw_smooth_frame_" + std::to_string(static_cast<int>(_cur_frame_index));
            if (!create_folder_for_saving_data())
            {
				throw std::runtime_error("Failed to create folder for saving data");
            }

			// Save input parameters for test vector
			save_input_params<T>(delta_z, alpha);

			// Save input data for test vector
			save_input_data<T>(frame, _last_frame, history);

            // pass one -- go through image and update all
            for (size_t i = 0; i < _current_frm_size_pixels; i++)
            {
                T cur_val = frame[i];
                T prev_val = _last_frame[i];

                if (cur_val)
                {
                    if (!prev_val)
                    {
                        _last_frame[i] = cur_val;
                        history[i] = mask;
                    }
                    else
                    {  // old and new val
                        T diff = static_cast<T>(fabs(cur_val - prev_val));

                        if (diff < delta_z)
                        {  // old and new val agree
                            history[i] |= mask;
                            float filtered = alpha * cur_val + one_minus_alpha * prev_val;
                            T result = static_cast<T>(filtered);
                            frame[i] = result;
                            _last_frame[i] = result;
                        }
                        else
                        {
                            _last_frame[i] = cur_val;
                            history[i] = mask;
                        }
                    }
                }
                else
                {  // no cur_val
                    if (prev_val)
                    { // only case we can help
                        unsigned char hist = history[i];
                        unsigned char classification = _persistence_map[hist];
                        if (classification & mask)
                        { // we have had enough samples lately
                            frame[i] = prev_val;
                        }
                    }
                    history[i] &= ~mask;
                }
            }

            _cur_frame_index = (_cur_frame_index + 1) % 8;  // at end of cycle

            // writing output data from test vector
			save_output_data<T>(frame, history);

            if (!change_dir_to_one_above())
            {
				throw std::runtime_error("Failed to change dir to one above");
            }
        }

    private:
        void on_set_persistence_control(uint8_t val);
        void on_set_alpha(float val);
        void on_set_delta(float val);

        void recalc_persistence_map();
        uint8_t                 _persistence_param;

        float                   _alpha_param;               // The normalized weight of the current pixel
        uint8_t                 _delta_param;               // A threshold when a filter is invoked
        size_t                  _width, _height, _stride;
        size_t                  _bpp;
        rs2_extension           _extension_type;            // Strictly Depth/Disparity
        size_t                  _current_frm_size_pixels;
        rs2::stream_profile     _source_stream_profile;
        rs2::stream_profile     _target_stream_profile;
        std::vector<uint8_t>    _last_frame;                // Hold the last frame received for the current profile
        std::vector<uint8_t>    _history;                   // represents the history over the last 8 frames, 1 bit per frame
        uint8_t                 _cur_frame_index;
        // encodes whether a particular 8 bit history is good enough for all 8 phases of storage
        std::array<uint8_t, PRESISTENCY_LUT_SIZE> _persistence_map;
        std::string _saving_data_folder_path;

        bool create_folder_for_saving_data();
        bool change_dir_to_one_above();

        template <class T>
        void save_input_params(T delta_z, float alpha)
        {
            // writing input data from test vector

            std::ofstream input_params_file("temp_jw_smooth_input_params.txt");
            input_params_file << "size \t\t\t" << _current_frm_size_pixels << std::endl;
            input_params_file << "delta_z \t\t" << delta_z << std::endl;
            input_params_file << "alpha \t\t\t" << alpha << std::endl;
            input_params_file << "frame_index \t" << static_cast<int>(_cur_frame_index) << std::endl;
        }

        template <class T>
        void save_input_data(T* frame, T* last_frame, uint8_t* history)
        {
            std::ofstream input_frame_file("temp_jw_smooth_input_frame.bin", std::ios::binary);
            if constexpr (std::is_same_v<T, float>)
            {
				// convert float to uint16_t
				std::vector<uint16_t> frame_u16(_current_frm_size_pixels);
				for (size_t i = 0; i < _current_frm_size_pixels; i++)
					frame_u16[i] = static_cast<uint16_t>(frame[i] + 0.5f);
                input_frame_file.write(reinterpret_cast<const char*>(frame_u16.data()), _current_frm_size_pixels * sizeof(uint16_t));
            }
            else
			{
                input_frame_file.write(reinterpret_cast<const char*>(frame), _current_frm_size_pixels * sizeof(T));
			}

            std::ofstream input_last_processed_frame_file("temp_jw_smooth_input_last_processed_frame.bin", std::ios::binary);
            if constexpr (std::is_same_v<T, float>)
            {
                // convert float to uint16_t
				std::vector<uint16_t> last_frame_u16(_current_frm_size_pixels);
				for (size_t i = 0; i < _current_frm_size_pixels; i++)
					last_frame_u16[i] = static_cast<uint16_t>(last_frame[i] + 0.5f);
				input_last_processed_frame_file.write(reinterpret_cast<const char*>(last_frame_u16.data()), _current_frm_size_pixels * sizeof(uint16_t));
			}
            else
            {
                input_last_processed_frame_file.write(reinterpret_cast<const char*>(last_frame), _current_frm_size_pixels * sizeof(T));
            }
                
            std::ofstream input_history_vector_file("temp_jw_smooth_input_history_vector.bin", std::ios::binary);
            input_history_vector_file.write(reinterpret_cast<const char*>(history), _current_frm_size_pixels * sizeof(uint8_t));

            std::ofstream input_persistency_index_file("temp_jw_smooth_input_persistency_index.bin", std::ios::binary);
            input_persistency_index_file.write(reinterpret_cast<const char*>(_persistence_map.data()), PRESISTENCY_LUT_SIZE * sizeof(uint8_t));
        }

        template <class T>
        void save_output_data(T* frame, uint8_t* history)
        {
            // writing output data from test vector
            std::ofstream output_frame_file("temp_jw_smooth_output_frame.bin", std::ios::binary);
            if constexpr (std::is_same_v<T, float>)
            {
				// convert float to uint16_t
				std::vector<uint16_t> frame_u16(_current_frm_size_pixels);
				for (size_t i = 0; i < _current_frm_size_pixels; i++)
                    frame_u16[i] = static_cast<uint16_t>(frame[i] + 0.5f);
                output_frame_file.write(reinterpret_cast<const char*>(frame_u16.data()), _current_frm_size_pixels * sizeof(uint16_t));
            }
            else
            {
				output_frame_file.write(reinterpret_cast<const char*>(frame), _current_frm_size_pixels * sizeof(T));
            }

            std::ofstream output_history_vector_file("temp_jw_smooth_output_history_vector.bin", std::ios::binary);
            output_history_vector_file.write(reinterpret_cast<const char*>(history), _current_frm_size_pixels * sizeof(uint8_t));
        }
    };
    MAP_EXTENSION(RS2_EXTENSION_TEMPORAL_FILTER, librealsense::temporal_filter);
}
