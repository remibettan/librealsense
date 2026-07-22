// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include "camera-identifier-v4l.h"
#include "v4l-mipi-logic.h"
#include "v4l-usb-logic.h"

#include <src/librealsense-exception.h>

#include <sstream>

namespace librealsense
{
    namespace platform
    {
        void camera_identifier_v4l_mipi::resolve( const std::string & dev_name )
        {
            // GVD product ID
            const uint8_t GVD_PID_OFFSET = 4;

            const uint8_t GVD_PID_D457 = 0x12;
            const uint8_t GVD_PID_D401_GMSL = 0x13;
            const uint8_t GVD_PID_D430_GMSL = 0x0F;
            const uint8_t GVD_PID_D415_GMSL = 0x06;

            uint16_t device_pid = 0;

            std::vector< uint8_t > gvd = v4l_mipi_logic::get_gvd( dev_name );

            // d500 MIPI (e.g. D585 GMSL) uses a different GVD layout than d400 GMSL:
            // byte 8 holds the CRC32, and the RealSense VID/PID are embedded at bytes 16-19.
            // TODO - temp WA for D585 GMSL, until the GVD layout is fixed to match the D400 GMSL layout
            uint16_t embedded_vid = gvd[16] | ( gvd[17] << 8 );
            if( embedded_vid == 0x38e5 )  // RealSense VID identifies the d500 GMSL family
            {
                device_pid = D585_GMSL_PID;
            }
            else
            {
                uint8_t product_pid = gvd[4 + GVD_PID_OFFSET];

                switch( product_pid )
                {
                case( GVD_PID_D457 ):
                    device_pid = D457_PID;
                    break;

                case( GVD_PID_D430_GMSL ):
                    device_pid = D430_GMSL_PID;
                    break;

                case( GVD_PID_D415_GMSL ):
                    device_pid = D415_GMSL_PID;
                    break;

                case( GVD_PID_D401_GMSL ):
                    device_pid = D401_GMSL_PID;
                    break;

                default:
                    LOG_WARNING( "Unidentified MIPI device product id: 0x" << std::hex << (int)product_pid );
                    device_pid = 0x0000;
                    break;
                }
            }

            _pid = device_pid;
            _vid = ( _pid == D585_GMSL_PID ) ? 0x38e5 : 0x8086;  // D585 GMSL uses RealSense VID
        }

        void camera_identifier_v4l_usb::resolve( const std::string & name )
        {
            uint16_t vid{}, pid{};

            std::string modalias = v4l_usb_logic::read_modalias( name );
            if( modalias.size() < 14 || modalias.substr( 0, 5 ) != "usb:v" || modalias[9] != 'p' )
                throw linux_backend_exception( "Not a usb format modalias" );
            if( ! ( std::istringstream( modalias.substr( 5, 4 ) ) >> std::hex >> vid ) )
                throw linux_backend_exception( "Failed to read vendor ID" );
            if( ! ( std::istringstream( modalias.substr( 10, 4 ) ) >> std::hex >> pid ) )
                throw linux_backend_exception( "Failed to read product ID" );

            _vid = vid;
            _pid = pid;
        }
    }  // namespace platform
}  // namespace librealsense
