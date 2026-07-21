// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include <src/ds/features/gyro-sensitivity-encoding.h>

#include "../catch.h"


using namespace librealsense;


TEST_CASE( "gyro sensitivity encoding is transport specific", "[options]" )
{
    const double d400_expected[] = { 0., 0.1, 0.2, 0.3, 0.4 };

    for( int value = 0; value <= 4; ++value )
    {
        CHECK( encode_gyro_sensitivity(
                   static_cast< float >( value ),
                   gyro_sensitivity_encoding::d400_hid_token )
               == d400_expected[value] );
        CHECK( encode_gyro_sensitivity(
                   static_cast< float >( value ),
                   gyro_sensitivity_encoding::hkr_range_index )
               == value );
    }

    CHECK( default_gyro_sensitivity( gyro_sensitivity_encoding::d400_hid_token ) == 0.1 );
    CHECK( default_gyro_sensitivity( gyro_sensitivity_encoding::hkr_range_index ) == 4. );
}
