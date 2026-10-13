cmake_minimum_required(VERSION 3.10)
project(realsense_native_example)

#################### librealsense dependencies begin ####################
# Headers come straight from the librealsense source tree; the .so is extracted from the AAR by gradle
set(LRS_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../include" CACHE PATH "librealsense public headers")
include_directories("${LRS_INCLUDE_DIR}")
add_library(realsense2 SHARED IMPORTED)
set_target_properties(
    realsense2
    PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/build/librealsense/jni/${ANDROID_ABI}/librealsense2.so
)
####################  librealsense dependencies end  ####################

add_library(native-lib SHARED src/main/cpp/native-lib.cpp)

find_library(log-lib log)

target_link_libraries(
    native-lib
    ${log-lib}
#################### librealsense dependencies begin ####################
    realsense2
####################  librealsense dependencies end  ####################
)
