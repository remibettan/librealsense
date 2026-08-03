// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include "viewer-test-helpers.h"

#include <rsutils/string/string-utilities.h>

using rsutils::string::to_lower;

// Type into the Controls search box and verify the option list is filtered live:
// case-insensitive substring match on the control name, empty input restores the full list
VIEWER_TEST( "controls", "options_filter" )
{
    auto & model = test.find_first_device_or_exit();
    bool tested = false;

    for( auto && sub : model.subdevices )
    {
        test.expand_sensor_panel( model, sub );
        test.expand_controls( model, sub );

        // the options the UI actually renders inside this sensor's Controls section
        auto options = test.controls_options( model, sub );
        if( options.size() < 2 )
        {
            test.collapse_sensor_panel( model, sub );
            continue;
        }

        auto name = [&]( rs2_option o ) {
            auto & label = sub->options_metadata.at( o ).label;
            return to_lower( label.substr( 0, label.find( "##" ) ) );
        };

        // filter = first control's name minus its last char; expected = the controls
        // whose names contain it
        std::string filter = name( options[0] );
        filter.pop_back();
        std::vector< rs2_option > expected;
        for( auto o : options )
            if( name( o ).find( filter ) != std::string::npos )
                expected.push_back( o );
        if( expected.size() == options.size() ) // filter hides nothing — can't verify on this sensor
        {
            test.collapse_sensor_panel( model, sub );
            continue;
        }

        // case-insensitive substring match: exactly the matching controls stay visible
        test.set_controls_filter( model, sub, filter );
        IM_CHECK( test.controls_options( model, sub ) == expected );

        // clearing the box restores the full list
        test.set_controls_filter( model, sub, "" );
        IM_CHECK( test.controls_options( model, sub ) == options );

        test.collapse_controls( model, sub );
        test.collapse_sensor_panel( model, sub );
        tested = true;
        break; // one sensor is enough — the filter code path is per-sensor identical
    }

    IM_CHECK( tested );
}
