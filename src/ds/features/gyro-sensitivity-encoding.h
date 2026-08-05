// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#pragma once

#include <stdexcept>


namespace librealsense {


enum class gyro_sensitivity_encoding
{
    d400_hid_token,
    hkr_range_index
};


inline double encode_gyro_sensitivity( float value, gyro_sensitivity_encoding encoding )
{
    const auto index = static_cast< int >( value );
    if( value != index || index < 0 || index > 4 )
        throw std::out_of_range( "unsupported gyro sensitivity" );

    if( encoding == gyro_sensitivity_encoding::hkr_range_index )
        return index;

    return index * 0.1;
}


inline double default_gyro_sensitivity( gyro_sensitivity_encoding encoding )
{
    return encoding == gyro_sensitivity_encoding::hkr_range_index ? 4. : 0.1;
}


}  // namespace librealsense
