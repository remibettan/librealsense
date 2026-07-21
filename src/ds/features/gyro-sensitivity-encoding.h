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
    if( encoding == gyro_sensitivity_encoding::hkr_range_index )
        return value;

    switch( static_cast< int >( value ) )
    {
    case 0:
        return 0.;
    case 1:
        return 0.1;
    case 2:
        return 0.2;
    case 3:
        return 0.3;
    case 4:
        return 0.4;
    default:
        throw std::out_of_range( "unsupported gyro sensitivity" );
    }
}


inline double default_gyro_sensitivity( gyro_sensitivity_encoding encoding )
{
    return encoding == gyro_sensitivity_encoding::hkr_range_index ? 4. : 0.1;
}


}  // namespace librealsense
