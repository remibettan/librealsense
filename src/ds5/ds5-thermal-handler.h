// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2020 Intel Corporation. All Rights Reserved.
#pragma once

#include <list>
#include "device-calibration.h"

namespace librealsense
{
    class ds5_recalibrable_color_sensor;
    class ds5_thermal_handler : public device_calibration
    {
    public:
        ds5_thermal_handler(synthetic_sensor& activation_sensor);

        virtual ~ds5_thermal_handler();

        void set_feature(bool state);
        float query();

        void register_calibration_change_callback(calibration_change_callback_ptr callback)
        {
            _calibration_change_callbacks.push_back(callback);
        }

        void trigger_device_calibration(rs2_calibration_type);

    private:
        void update_mode(bool on_streaming=false);
        void start();
        void stop();
        void polling(dispatcher::cancellable_timer cancellable_timer);
        void notify_of_calibration_change(rs2_calibration_status status);

        std::weak_ptr<synthetic_sensor>                 _dpt_sensor;
        std::weak_ptr<ds5_recalibrable_color_sensor>    _recalib_sensor;

        std::vector< calibration_change_callback_ptr >  _calibration_change_callbacks;   // End-user updates to track calibration changes

        std::shared_ptr<option> _tl_activation;

        active_object<> _active_object;
        unsigned int _poll_intervals_ms;
        float _thermal_threshold_deg;
        float _temp_base;
        bool _streaming_on;
        bool _control_on;
    };

}
