// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2015 RealSense, Inc. All Rights Reserved.

#pragma once
#ifndef LIBREALSENSE_IMAGE_AVX_H
#define LIBREALSENSE_IMAGE_AVX_H

#include <cstdint>

// AVX2 YUY2 unpacking is built on x86-64 only and selected at runtime via cpu_supports_avx2().
// image-avx.cpp is compiled with the baseline flags; only its unpack functions use AVX2.
#if defined( __SSSE3__ ) && ! defined( ANDROID ) && ( defined( __x86_64__ ) || defined( _M_X64 ) )
#define LRS_YUY2_AVX2
#endif

namespace librealsense
{
#ifdef LRS_YUY2_AVX2
    bool cpu_supports_avx2();

    // n must be a multiple of 32 pixels
    void unpack_yuy2_avx_rgb8(uint8_t * const d[], const uint8_t * s, int n);
    void unpack_yuy2_avx_rgba8(uint8_t * const d[], const uint8_t * s, int n);
    void unpack_yuy2_avx_bgr8(uint8_t * const d[], const uint8_t * s, int n);
    void unpack_yuy2_avx_bgra8(uint8_t * const d[], const uint8_t * s, int n);
#endif
}

#endif
