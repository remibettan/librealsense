// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2020 Intel Corporation. All Rights Reserved.

#include <jni.h>
#include "error.h"
#include "../../../include/librealsense2/rs.h"  // use for C API
#include "../../../include/librealsense2/rs.hpp"

///////// TODO REMI - below code should use C API instead of C++ API
/*void handle_error(JNIEnv *env, rs2_error *error) {
    if (error) {
        const char *message = rs2_get_error_message(error);
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), message);
    }
}*/

void handle_error(JNIEnv *env, rs2::error error) {

    const char *message = error.what();
    env->ThrowNew(env->FindClass("java/lang/RuntimeException"), message);
}

rs2::pipeline_profile get_profile(JNIEnv *env, jlong handle) {
    auto _pipeline = reinterpret_cast<rs2_pipeline *>((long) handle);
    rs2_error *e = nullptr;
    auto p = std::shared_ptr<rs2_pipeline_profile>(
            rs2_pipeline_get_active_profile(_pipeline, &e),
            rs2_delete_pipeline_profile);
    handle_error(env, e);
    return p;
}

void
copy_vector_to_jbytebuffer(JNIEnv *env, rs2::calibration_table vector, jobject target_jbuffer)
{
    auto new_table_buffer = (uint8_t *) (*env).GetDirectBufferAddress(target_jbuffer);
    auto new_table_buffer_size = (size_t) (*env).GetDirectBufferCapacity(target_jbuffer);
    if (vector.size() <= new_table_buffer_size)
        std::copy(vector.begin(), vector.end(), new_table_buffer);
}

void
copy_raw_data_buffer_to_jbytebuffer(JNIEnv *env, const rs2_raw_data_buffer* raw_data_buffer, jobject target_buffer)
{
    rs2_error *e = NULL;
    size_t data_size = rs2_get_raw_data_size(raw_data_buffer, &e);

    const unsigned char* data = rs2_get_raw_data(raw_data_buffer, &e);

    std::memcpy(target_buffer, data, data_size);
}



extern "C"
JNIEXPORT jint JNICALL
Java_com_intel_realsense_camera_calibration_1package_CalibrationTablesHandler_nGetTable(JNIEnv *env, jobject instance,
                                                                                        jlong pipeline_handle,
                                                                                        jobject table) {
    try {
        auto profile = get_profile(env, pipeline_handle);
        auto calib_dev = rs2::auto_calibrated_device(profile.get_device());
        auto current_table = calib_dev.get_calibration_table();
        copy_vector_to_jbytebuffer(env, current_table, table);
        return current_table.size();
    } catch (const rs2::error &e) {
        handle_error(env, e);
        return -1;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_intel_realsense_camera_calibration_1package_CalibrationTablesHandler_nSetTable(JNIEnv *env, jobject instance,
                                                                                        jlong pipeline_handle,
                                                                                        jobject table,
                                                                                        jboolean write) {
    auto profile = get_profile(env, pipeline_handle);
    rs2_error *e = nullptr;
    auto calib_dev = rs2::auto_calibrated_device(profile.get_device());

    uint8_t *new_table_buffer = (uint8_t *) (*env).GetDirectBufferAddress(table);
    size_t new_table_buffer_size = (*env).GetDirectBufferCapacity(table);

    if (new_table_buffer != NULL) {
        rs2_set_calibration_table(profile.get_device().get().get(), new_table_buffer,
                                  new_table_buffer_size, &e);
        handle_error(env, e);
        if (write) {
            calib_dev.write_calibration();
        }
    }
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_intel_realsense_librealsense_AutoCalibDevice_nRunAutoCalib(JNIEnv *env, jobject instance,
                                                             jlong device_handle,
                                                             jobject target_buffer,
                                                             jstring json_cont)
{
    const char* json_ptr = env->GetStringUTFChars(json_cont, NULL);
    jsize json_size = env->GetStringUTFLength(json_cont);

    //preparing progress callback method
    jclass cls = env->GetObjectClass(instance);
    jmethodID id = env->GetMethodID(cls, "calibrationOnProgress", "(F)V");
    auto cb = [&](float progress){ env->CallVoidMethod(instance, id, progress); };

    float health = 0;
    rs2_error *e = NULL;
    int timeout_ms = 5000;
    const rs2_raw_data_buffer* buffer = rs2_run_on_chip_calibration(reinterpret_cast<rs2_device*>(device_handle),
                                                                    json_ptr, json_size, &health,
                                                                    reinterpret_cast<rs2_update_progress_callback_ptr>(cb), nullptr, timeout_ms, &e);
    copy_raw_data_buffer_to_jbytebuffer(env, buffer, target_buffer);

    env->ReleaseStringUTFChars(json_cont, json_ptr);

    return health;


    /*try {
        //auto profile = get_profile(env, pipeline_handle);
        //float health = MAXFLOAT;
        //auto sensor = profile.get_device().first<rs2::depth_sensor>();
        // Set the device to High Accuracy preset of the D400 stereoscopic cameras
        //sensor.set_option(RS2_OPTION_VISUAL_PRESET, RS2_RS400_VISUAL_PRESET_HIGH_ACCURACY);
        //auto calib_dev = rs2::auto_calibrated_device(profile.get_device());
        //auto json_ptr = (*env).GetStringUTFChars(json_cont, NULL);

        //preparing progress callback method
        jclass cls = env->GetObjectClass(instance);
        jmethodID id = env->GetMethodID(cls, "calibrationOnProgress", "(F)V");
        auto cb = [&](float progress){ env->CallVoidMethod(instance, id, progress); };


        //auto new_table_vector = calib_dev.run_on_chip_calibration(std::string(json_ptr), &health, cb);

        //copy_vector_to_jbytebuffer(env, new_table_vector, target_buffer);
        //env->ReleaseStringUTFChars(json_cont, json_ptr);

        //return health;
    } catch (const rs2::error &e) {
        handle_error(env, e);
        return -1;
    }*/

}

extern "C"
JNIEXPORT void JNICALL
Java_com_intel_realsense_librealsense_AutoCalibDevice_nTare(JNIEnv *env, jobject instance,
                                                                         jlong device_handle,
                                                                         jobject target_buffer,
                                                                         jint ground_truth,
                                                                         jstring json_cont)
{
    const char* json_ptr = env->GetStringUTFChars(json_cont, NULL);
    jsize json_size = env->GetStringUTFLength(json_cont);

    rs2_error *e = NULL;
    int timeout_ms = 5000;
    //no callback for progress because it is not available in FW
    const rs2_raw_data_buffer* buffer = rs2_run_tare_calibration(reinterpret_cast<rs2_device*>(device_handle), ground_truth,
    json_ptr, json_size, nullptr, nullptr, timeout_ms, &e);

    copy_raw_data_buffer_to_jbytebuffer(env, buffer, target_buffer);

    env->ReleaseStringUTFChars(json_cont, json_ptr);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_intel_realsense_camera_calibration_1package_TareProcessor_nTare(JNIEnv *env, jobject instance,
                                                                         jlong pipeline_handle,
                                                                         jobject target_buffer,
                                                                         jint ground_truth,
                                                                         jstring json_cont) {
    try {
        auto profile = get_profile(env, pipeline_handle);
        auto sensor = profile.get_device().first<rs2::depth_sensor>();
        sensor.set_option(RS2_OPTION_VISUAL_PRESET, RS2_RS400_VISUAL_PRESET_HIGH_ACCURACY);
        auto calib_dev = rs2::auto_calibrated_device(profile.get_device());
        auto json_ptr = (*env).GetStringUTFChars(json_cont, NULL);

        //no callback for progress because it is not available in FW
        auto new_table = calib_dev.run_tare_calibration(ground_truth, std::string(json_ptr));

        copy_vector_to_jbytebuffer(env, new_table, target_buffer);
    } catch (const rs2::error &e) {
        handle_error(env, e);
    }
}