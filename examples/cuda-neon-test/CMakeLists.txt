# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.
cmake_minimum_required(VERSION 3.10)

project(RealsenseExamplesCudaNeonTest)

# This example tests CUDA+NEON hybrid acceleration
add_executable(rs-cuda-neon-test rs-cuda-neon-test.cpp)
set_property(TARGET rs-cuda-neon-test PROPERTY CXX_STANDARD 11)
target_link_libraries(rs-cuda-neon-test ${DEPENDENCIES})
include_directories(../)
set_target_properties(rs-cuda-neon-test PROPERTIES FOLDER "Examples")

install(TARGETS rs-cuda-neon-test RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR})
