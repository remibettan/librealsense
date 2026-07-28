// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

#include "v4l-mipi-logic.h"
#include "backend-v4l2.h"  // xioctl(), linux_backend_exception

#include <linux/media.h>  // media_device_info, MEDIA_IOC_DEVICE_INFO

#include <cstring>
#include <regex>
#include <thread>
#include <chrono>
#include <memory>
#include <functional>

namespace librealsense
{
    namespace platform
    {
        namespace v4l_mipi_logic
        {
            // MIPI GVD control id.
            static constexpr uint32_t RS_STREAM_CONFIG_0 = 0x4000;
            static constexpr uint32_t RS_CAMERA_CID_BASE = ( V4L2_CTRL_CLASS_CAMERA | RS_STREAM_CONFIG_0 );
            static constexpr uint32_t RS_CAMERA_CID_GVD = ( RS_CAMERA_CID_BASE + 8 );

            static constexpr uint8_t GVD_VALID_OPCODE = 0x10;

            std::vector< uint8_t > get_gvd( const std::string & dev_name )
            {
                // RAII to close the fd on every exit path
                std::unique_ptr< int, std::function< void( int * ) > > fd( new int( open( dev_name.c_str(), O_RDWR ) ),
                                                                           []( int * d ) { if( d && *d >= 0 ) ::close( *d ); delete d; } );
                if( *fd < 0 )
                    throw linux_backend_exception( "Mipi device GVD could not be read" );

                // GVD payload has different size depending on product line. Query the control's size so the full struct is read.
                struct v4l2_query_ext_ctrl qctrl = {};
                qctrl.id = RS_CAMERA_CID_GVD;
                if( xioctl( *fd, VIDIOC_QUERY_EXT_CTRL, &qctrl ) != 0 || qctrl.elems == 0 )
                    throw linux_backend_exception( "Mipi device GVD size could not be queried" );

                std::vector< uint8_t > gvd( qctrl.elems * qctrl.elem_size, 0 );
                struct v4l2_ext_control ctrl;

                ctrl.id = RS_CAMERA_CID_GVD;
                ctrl.size = gvd.size();
                ctrl.p_u8 = gvd.data();

                struct v4l2_ext_controls ext;

                ext.ctrl_class = V4L2_CTRL_CLASS_CAMERA;
                ext.controls = &ctrl;
                ext.count = 1;

                int retries = 5;
                bool opcode_ok = false;
                while( ! opcode_ok && retries-- )
                {
                    if( xioctl( *fd, VIDIOC_G_EXT_CTRLS, &ext ) == 0 )
                    {
                        auto opcode = gvd[0];
                        if( opcode != GVD_VALID_OPCODE )
                            LOG_WARNING( "Wrong opcode when pulling GVD: gvd[0] returned as: " << static_cast< int >( opcode ) );
                        else
                            opcode_ok = true;
                    }
                    if( ! opcode_ok && retries )  // wait before the next attempt, but not after the last
                        std::this_thread::sleep_for( std::chrono::milliseconds( 100 ) );
                }

                if( ! opcode_ok )
                    throw linux_backend_exception( "Failed to pull a valid GVD from " + dev_name );

                return gvd;
            }

            bool is_format_supported_on_node( const std::string & dev_name, std::string v4l_4cc_fmt )
            {
                int fd = open( dev_name.c_str(), O_RDWR );
                if( fd < 0 )
                    throw linux_backend_exception( "Mipi device format could not be grabbed" );

                struct v4l2_fmtdesc fmtdesc;
                memset( &fmtdesc, 0, sizeof( fmtdesc ) );
                fmtdesc.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;

                uint32_t format;
                memcpy( &format, v4l_4cc_fmt.c_str(), sizeof( format ) );

                while( ioctl( fd, VIDIOC_ENUM_FMT, &fmtdesc ) == 0 )
                {
                    if( fmtdesc.pixelformat == format )
                    {
                        ::close( fd );
                        return true;
                    }

                    fmtdesc.index++;
                }

                ::close( fd );

                return false;
            }

            bool is_device_depth_node( const std::string & dev_name )
            {
                bool is_depth = false;

                // first search video node links, for example, video-rs-depth-0
                std::smatch match;
                static std::regex video_dev_rs( "video-rs-" );
                static std::regex video_dev_depth( "video-rs-depth-\\d+$" );
                if( std::regex_search( dev_name, match, video_dev_rs ) )
                {
                    if( std::regex_search( dev_name, match, video_dev_depth ) )
                        is_depth = true;
                    else
                        is_depth = false;
                }
                // then search video nodes to find the depth node
                else if( is_format_supported_on_node( dev_name, "Z16 " ) )
                    is_depth = true;
                else
                    is_depth = false;

                return is_depth;
            }

            void get_device_info( const std::string & dev_name, std::string & bus_info, std::string & card )
            {
                struct v4l2_capability vcap;
                int fd = open( dev_name.c_str(), O_RDWR );
                if( fd < 0 )
                    throw linux_backend_exception( "Mipi device capability could not be grabbed" );
                int err = ioctl( fd, VIDIOC_QUERYCAP, &vcap );
                if( err )
                {
                    struct media_device_info mdi;

                    err = ioctl( fd, MEDIA_IOC_DEVICE_INFO, &mdi );
                    if( ! err )
                    {
                        if( mdi.bus_info[0] )
                            bus_info = mdi.bus_info;
                        else
                            bus_info = std::string( "platform:" ) + mdi.driver;

                        if( mdi.model[0] )
                            card = mdi.model;
                        else
                            card = mdi.driver;
                    }
                }
                else
                {
                    bus_info = reinterpret_cast< const char * >( vcap.bus_info );
                    card = reinterpret_cast< const char * >( vcap.card );
                }

                ::close( fd );
            }

            int parse_video_index( const std::string & name )
            {
                static std::regex video_dev_index( "\\d+$" );
                std::smatch match;
                if( ! std::regex_search( name, match, video_dev_index ) )
                {
                    LOG_WARNING( "Unresolved Video4Linux device pattern: " << name << ", device is skipped" );
                    throw linux_backend_exception( "Unresolved Video4Linux device, device is skipped" );
                }
                return std::stoi( match[0] );
            }

            void derive_mi_and_cam_id( int video_index, uint16_t & mi, int & cam_id )
            {
                //  D457 exposes (assuming first_video_index = 0):
                // - video0 for Depth and video1 for Depth's md.
                // - video2 for RGB and video3 for RGB's md.
                // - video4 for IR and video5 for IR's md.
                // - video6 for IMU (accel or gyro TBD)
                // next several lines permit to use D457 even if a usb device has already "taken" the video0,1,2 (for example)
                // further development is needed to permit use of several mipi devices
                static int first_video_index = video_index;

                // Use camera_video_nodes as number of /dev/video[%d] for each camera sensor subset
                const int camera_video_nodes = 7;
                cam_id = video_index / camera_video_nodes;
                int ind = ( video_index - first_video_index )
                    % camera_video_nodes;  // offset from first mipi video node and assume 6 nodes per mipi camera
                if( ind == 0 || ind == 2 || ind == 4 )
                    mi = 0;  // video node indicator
                else if( ind == 1 || ind == 3 || ind == 5 )
                    mi = 3;  // metadata node indicator
                else if( ind == 6 )
                    mi = 4;  // IMU node indicator
                else
                {
                    LOG_WARNING( "Unresolved Video4Linux device mi, device is skipped" );
                    throw linux_backend_exception( "Unresolved Video4Linux device, device is skipped" );
                }
            }

            std::string rs_enum_video_node_name( const std::string & sensor, int cam_idx, bool metadata )
            {
                return "video-rs-" + sensor + ( metadata ? "-md-" : "-" ) + std::to_string( cam_idx );
            }

            std::string rs_enum_dfu_node_path( int cam_idx )
            {
                return "/dev/d4xx-dfu-" + std::to_string( cam_idx );
            }
        }  // namespace v4l_mipi_logic
    }  // namespace platform
}  // namespace librealsense
