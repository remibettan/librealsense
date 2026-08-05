// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#pragma once

#include <cmath>
#include <stdexcept>


namespace librealsense {


enum class gyro_sensitivity_encoding
{
    d400_hid_token,
    hkr_range_index
};


inline bool is_valid_gyro_sensitivity( float value )
{
    return std::isfinite( value ) && value >= 0.f && value <= 4.f
        && std::floor( value ) == value;
}


inline double encode_gyro_sensitivity( float value, gyro_sensitivity_encoding encoding )
{
    if( ! is_valid_gyro_sensitivity( value ) )
        throw std::out_of_range( "unsupported gyro sensitivity" );

    const auto index = static_cast< int >( value );
    if( encoding == gyro_sensitivity_encoding::hkr_range_index )
        return index;

    // Preserve the exact HID tokens used by the original D400 lookup table.
    static constexpr double d400_hid_tokens[] = { 0., 0.1, 0.2, 0.3, 0.4 };
    return d400_hid_tokens[index];
}


inline double default_gyro_sensitivity( gyro_sensitivity_encoding encoding )
{
    return encoding == gyro_sensitivity_encoding::hkr_range_index ? 4. : 0.1;
}


}  // namespace librealsense
