# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2019 RealSense, Inc. All Rights Reserved.

if(BUILD_WITH_HIP)
    set(RS_GPU_CONVERSION_SOURCE "${CMAKE_CURRENT_LIST_DIR}/cuda-conversion.hip")
    set(RS_GPU_POINTCLOUD_SOURCE "${CMAKE_CURRENT_LIST_DIR}/cuda-pointcloud.hip")
    set_source_files_properties(
        "${RS_GPU_CONVERSION_SOURCE}"
        "${RS_GPU_POINTCLOUD_SOURCE}"
        PROPERTIES LANGUAGE HIP
    )
    set(RS_GPU_EXTRA_SOURCES "")
else()
    set(RS_GPU_CONVERSION_SOURCE "${CMAKE_CURRENT_LIST_DIR}/cuda-conversion.cu")
    set(RS_GPU_POINTCLOUD_SOURCE "${CMAKE_CURRENT_LIST_DIR}/cuda-pointcloud.cu")
    set(RS_GPU_EXTRA_SOURCES
        "${CMAKE_CURRENT_LIST_DIR}/cuda-frame-memory.cu"
        "${CMAKE_CURRENT_LIST_DIR}/cuda-frame-memory.h"
    )
endif()

target_sources(${LRS_TARGET}
    PRIVATE
        "${RS_GPU_CONVERSION_SOURCE}"
        "${CMAKE_CURRENT_LIST_DIR}/cuda-conversion.cuh"
        "${RS_GPU_POINTCLOUD_SOURCE}"
        "${CMAKE_CURRENT_LIST_DIR}/cuda-pointcloud.cuh"
        "${CMAKE_CURRENT_LIST_DIR}/cuda-compat.h"
        "${CMAKE_CURRENT_LIST_DIR}/rscuda_utils.cuh"
        ${RS_GPU_EXTRA_SOURCES}
)