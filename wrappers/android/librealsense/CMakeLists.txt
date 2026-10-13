# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.
cmake_minimum_required(VERSION 3.10)

include("../config.cmake")
# Sources live outside this project, so CMake mirrors their absolute paths under CMakeFiles; keep the object paths
# short enough for Windows (MAX_PATH) by hashing long object directories
set(CMAKE_OBJECT_PATH_MAX 255)
# Build inside the per-variant/per-ABI directory the Android Gradle plugin provides, so debug and release
# builds never share (or race on) generated files
add_subdirectory(${LRS_ROOT_DIR} ${CMAKE_BINARY_DIR}/lrs)
