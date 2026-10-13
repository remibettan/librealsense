#  Build RealSense SDK for Android OS
This document describes how to build the RealSense SDK 2.0 (`librealsense.aar`), the RealSense Camera app and the examples for Android devices.

> Read about Android support [here](../../doc/android.md).

## Requirements
- [Android Studio](https://developer.android.com/studio) Narwhal (2025.1) or newer, with the Android SDK Platform 36 (Android 16), NDK `28.2.13676358` and CMake `3.22.1` installed from the SDK Manager (Android Studio offers to install missing components on the first sync).
- JDK 17 or newer (bundled with Android Studio).
- Builds use Android Gradle Plugin 8.13 and Gradle 8.13; the Gradle wrapper downloads Gradle on first use.

The native library is built with 16 KB page alignment, as required by Android 15+ and Android 16 devices.

## Instructions
### Build With Gradle
To build the AAR from the command line, navigate to `<librealsense_root_dir>/wrappers/android`
and run `gradlew :librealsense:assembleRelease` on a Windows host or `./gradlew :librealsense:assembleRelease` on a Linux/macOS host.
If the build succeeded, the generated AAR will be located in `<librealsense_root_dir>/wrappers/android/librealsense/build/outputs/aar`.

To build the RealSense Camera app run `gradlew :tools:camera:assembleRelease`; the APK is written to `tools/camera/build/outputs/apk/release`.

### Build With Android Studio
1. Download and install [Android Studio](https://developer.android.com/studio).
2. Clone the latest [RealSense&trade; SDK 2.0](https://github.com/realsenseai/librealsense/releases) to your host machine.
3. Open Android Studio.
4. Open the Android project: Menu Bar-->File-->Open-->`<librealsense_root_dir>/wrappers/android`.
5. Accept the SDK component installs Android Studio proposes, then in the Gradle tasks run `:librealsense-->build-->assembleRelease`.

The build takes a few minutes since librealsense is compiled for both the `arm64-v8a` and `x86_64` ABIs.
If the build succeeded, the generated AAR will be located in `<librealsense_root_dir>/wrappers/android/librealsense/build/outputs/aar`.

You can also build and run any of the example apps or the camera app from this project by selecting it in the run configuration box and running it while your target phone is connected.

### RealSense Camera app
`tools/camera` is the reference viewer for Android: it streams any connected camera, lets you pick the stream profiles, shows live statistics, a point cloud, records to and plays back `.bag` files, exposes the sensor controls and handles firmware updates. It targets Android 16 (API 36), runs on Android 7.0 and newer, and follows the system light/dark theme.

Plugging a camera in launches the app automatically (or asks which app should handle the camera the first time).
