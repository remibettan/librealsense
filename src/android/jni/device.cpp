// License: Apache 2.0. See LICENSE file in root directory.
// Copyright(c) 2019 Intel Corporation. All Rights Reserved.

#include <jni.h>
#include <memory>
#include <vector>
#include "error.h"

#include "../../../include/librealsense2/rs.h"
#include "../../../include/librealsense2/hpp/rs_device.hpp"
#include "../../api.h"
#include "../../../include/librealsense2/hpp/rs_pipeline.hpp"

extern "C" JNIEXPORT jboolean JNICALL
Java_com_intel_realsense_librealsense_Device_nSupportsInfo(JNIEnv *env, jclass type, jlong handle,
                                                           jint info) {
    rs2_error *e = NULL;
    auto rv = rs2_supports_device_info(reinterpret_cast<const rs2_device *>(handle),
                                       static_cast<rs2_camera_info>(info), &e);
    handle_error(env, e);
    return rv > 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_intel_realsense_librealsense_Device_nGetInfo(JNIEnv *env, jclass type, jlong handle,
                                                      jint info) {
    rs2_error *e = NULL;
    const char* rv = rs2_get_device_info(reinterpret_cast<const rs2_device *>(handle),
                                         static_cast<rs2_camera_info>(info), &e);
    handle_error(env, e);
    if (NULL == rv)
        rv = "";
    return env->NewStringUTF(rv);
}

extern "C" JNIEXPORT void JNICALL
Java_com_intel_realsense_librealsense_Device_nRelease(JNIEnv *env, jclass type, jlong handle) {
    rs2_delete_device(reinterpret_cast<rs2_device *>(handle));
}

extern "C"
JNIEXPORT jlongArray JNICALL
Java_com_intel_realsense_librealsense_Device_nQuerySensors(JNIEnv *env, jclass type, jlong handle) {
    rs2_error* e = nullptr;
    std::shared_ptr<rs2_sensor_list> list(
            rs2_query_sensors(reinterpret_cast<const rs2_device *>(handle), &e),
            rs2_delete_sensor_list);
    handle_error(env, e);

    auto size = rs2_get_sensors_count(list.get(), &e);
    handle_error(env, e);

    std::vector<rs2_sensor*> sensors;
    for (auto i = 0; i < size; i++)
    {
        auto s = rs2_create_sensor(list.get(), i, &e);
        handle_error(env, e);
        sensors.push_back(s);
    }
    jlongArray rv = env->NewLongArray(sensors.size());

    for (auto i = 0; i < sensors.size(); i++)
    {
        env->SetLongArrayRegion(rv, i, 1, reinterpret_cast<const jlong *>(&sensors[i]));
    }

    return rv;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_intel_realsense_librealsense_Updatable_nEnterUpdateState(JNIEnv *env, jclass type,
                                                                  jlong handle) {
    rs2_error *e = NULL;
    rs2_enter_update_state(reinterpret_cast<const rs2_device *>(handle), &e);
    handle_error(env, e);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_intel_realsense_librealsense_Updatable_nUpdateFirmwareUnsigned(JNIEnv *env,
                                                                        jobject instance,
                                                                        jlong handle,
                                                                        jbyteArray image_,
                                                                        jint update_mode) {
    jbyte *image = env->GetByteArrayElements(image_, NULL);
    auto length = env->GetArrayLength(image_);
    rs2_error *e = NULL;
    jclass cls = env->GetObjectClass(instance);
    jmethodID id = env->GetMethodID(cls, "onProgress", "(F)V");
    auto cb = [&](float progress){ env->CallVoidMethod(instance, id, progress); };
    rs2_update_firmware_unsigned_cpp(reinterpret_cast<const rs2_device *>(handle), image, length,
                                     new rs2::update_progress_callback<decltype(cb)>(cb), update_mode, &e);
    handle_error(env, e);
    env->ReleaseByteArrayElements(image_, image, 0);
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_intel_realsense_librealsense_Updatable_nCreateFlashBackup(JNIEnv *env, jobject instance,
                                                                   jlong handle) {
    rs2_error* e = NULL;
    jclass cls = env->GetObjectClass(instance);
    jmethodID id = env->GetMethodID(cls, "onProgress", "(F)V");
    auto cb = [&](float progress){ env->CallVoidMethod(instance, id, progress); };

    std::shared_ptr<const rs2_raw_data_buffer> raw_data_buffer(
            rs2_create_flash_backup_cpp(reinterpret_cast<rs2_device *>(handle), new rs2::update_progress_callback<decltype(cb)>(cb), &e),
            [](const rs2_raw_data_buffer* buff){ if(buff) delete buff;});
    handle_error(env, e);

    jbyteArray rv = env->NewByteArray(raw_data_buffer->buffer.size());
    env->SetByteArrayRegion(rv, 0, raw_data_buffer->buffer.size(),
        reinterpret_cast<const jbyte *>(raw_data_buffer->buffer.data()));
    return rv;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_intel_realsense_librealsense_UpdateDevice_nUpdateFirmware(JNIEnv *env, jobject instance,
                                                                   jlong handle,
                                                                   jbyteArray image_) {
    jbyte *image = env->GetByteArrayElements(image_, NULL);
    auto length = env->GetArrayLength(image_);
    rs2_error *e = NULL;
    jclass cls = env->GetObjectClass(instance);
    jmethodID id = env->GetMethodID(cls, "onProgress", "(F)V");
    auto cb = [&](float progress){ env->CallVoidMethod(instance, id, progress); };
    rs2_update_firmware_cpp(reinterpret_cast<const rs2_device *>(handle), image, length,
                            new rs2::update_progress_callback<decltype(cb)>(cb), &e);
    handle_error(env, e);
    env->ReleaseByteArrayElements(image_, image, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_intel_realsense_librealsense_Device_nHardwareReset(JNIEnv *env, jclass type,
                                                            jlong handle) {
    rs2_error *e = NULL;
    rs2_hardware_reset(reinterpret_cast<const rs2_device *>(handle), &e);
    handle_error(env, e);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_intel_realsense_librealsense_Device_nIsDeviceExtendableTo(JNIEnv *env, jclass type,
                                                                   jlong handle, jint extension) {
    rs2_error *e = NULL;
    int rv = rs2_is_device_extendable_to(reinterpret_cast<const rs2_device *>(handle),
                                         static_cast<rs2_extension>(extension), &e);
    handle_error(env, e);
    return rv > 0;
}

//-----------------------------------------------------------------------------
//AutoCalibDevice jni functions
//-----------------------------------------------------------------------------
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
copy_vector_to_jbytebuffer(JNIEnv *env, rs2::calibration_table vector, jobject target_jbuffer) {
    auto new_table_buffer = (uint8_t *) (*env).GetDirectBufferAddress(target_jbuffer);
    auto new_table_buffer_size = (size_t) (*env).GetDirectBufferCapacity(target_jbuffer);
    if (vector.size() <= new_table_buffer_size)
        std::copy(vector.begin(), vector.end(), new_table_buffer);
}



extern "C"
JNIEXPORT jint JNICALL
Java_com_intel_realsense_librealsense_AutoCalibDevice_nGetTable(JNIEnv *env, jobject instance,
                                                                jlong device_handle,
                                                                jobject table) {
    rs2_device* device = reinterpret_cast<rs2_device*>(device_handle);

    rs2_error* e = nullptr;
    std::shared_ptr<const rs2_raw_data_buffer> calibration_table(
            rs2_get_calibration_table(device, &e),
            rs2_delete_raw_data);
    handle_error(env, e);

    std::vector<uint8_t> current_table;

    auto size = rs2_get_raw_data_size(calibration_table.get(), &e);
    handle_error(env, e);
    auto start = rs2_get_raw_data(calibration_table.get(), &e);
    handle_error(env, e);
    current_table.insert(current_table.begin(), start, start + size);
    copy_vector_to_jbytebuffer(env, current_table, table);

    return current_table.size();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_intel_realsense_librealsense_AutoCalibDevice_nSetTable(JNIEnv *env, jobject instance,
                                                                 jlong device_handle,
                                                                 jobject table) {
    rs2_device* device = reinterpret_cast<rs2_device*>(device_handle);

    uint8_t *new_table_buffer = (uint8_t *) (*env).GetDirectBufferAddress(table);
    size_t new_table_buffer_size = (*env).GetDirectBufferCapacity(table);

    if (new_table_buffer != NULL) {
        rs2_error* e = nullptr;
        rs2_set_calibration_table(device, new_table_buffer,
                                  new_table_buffer_size, &e);
        handle_error(env, e);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_intel_realsense_librealsense_AutoCalibDevice_nWriteToCamera(JNIEnv *env, jobject instance,
                                                                jlong device_handle) {
    rs2_device* device = reinterpret_cast<rs2_device*>(device_handle);
    rs2_error* e = nullptr;
    rs2_write_calibration(device, &e);
    handle_error(env, e);
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_intel_realsense_librealsense_AutoCalibDevice_nRunAutoCalib(JNIEnv *env, jobject instance,
                                                                                      jlong device_handle,
                                                                                      jobject target_buffer,
                                                                                      jstring json_cont) {
    try {
        rs2_device* device = reinterpret_cast<rs2_device*>(device_handle);
        float health = MAXFLOAT;

        auto json_ptr = (*env).GetStringUTFChars(json_cont, NULL);
        int json_length = strlen(json_ptr);

        //preparing progress callback method
        jclass cls = env->GetObjectClass(instance);
        jmethodID id = env->GetMethodID(cls, "calibrationOnProgress", "(F)V");
        auto cb = [&](float progress){ env->CallVoidMethod(instance, id, progress); };

        //TODO Remi - find out how to use C API instead of C++ AI with the callback function
        //std::shared_ptr<rs2_update_progress_callback> callback_function = std::make_shared<rs2_update_progress_callback>(cb);

        /*rs2_error* e = nullptr;
        int timeout_ms = 5000; //default as defined in CPP API
        std::shared_ptr<const rs2_raw_data_buffer> list(
                rs2_run_on_chip_calibration_cpp(device, json_ptr, json_length, &health,
                                                new update_progress_callback<void(*)(float)>(std::move(cb)), timeout_ms, &e),
                rs2_delete_raw_data);
        handle_error(env, e);

        auto size = rs2_get_raw_data_size(list.get(), &e);
        handle_error(env, e);
        auto start = rs2_get_raw_data(list.get(), &e);
        handle_error(env, e);

        std::vector<uint8_t> new_table;
        new_table.insert(new_table.begin(), start, start + size);*/
        std::shared_ptr<rs2::device> dev = std::make_shared<rs2::device>();

        std::shared_ptr<rs2_device> shared_ptr_device(device);
        (*dev) = shared_ptr_device;
        auto new_table_vector = (*dev).as<rs2::auto_calibrated_device>().run_on_chip_calibration(std::string(json_ptr), &health, cb);

        copy_vector_to_jbytebuffer(env, new_table_vector, target_buffer);
        return health;
    } catch (const rs2::error &e) {
        handle_error(env, e);
        return -1;
    }

}
extern "C"
JNIEXPORT void JNICALL
Java_com_intel_realsense_librealsense_AutoCalibDevice_nTare(JNIEnv *env, jobject instance,
                                                                         jlong device_handle,
                                                                         jobject target_buffer,
                                                                         jint ground_truth,
                                                                         jstring json_cont) {
    rs2_device* device = reinterpret_cast<rs2_device*>(device_handle);
    auto json_ptr = (*env).GetStringUTFChars(json_cont, NULL);
    int json_length = strlen(json_ptr);

    rs2_error* e = nullptr;
    int timeout_ms = 5000; //default as defined in CPP API
    std::shared_ptr<const rs2_raw_data_buffer> new_table_buffer(
            //no callback for progress because it is not available in FW
            rs2_run_tare_calibration_cpp(device, ground_truth, json_ptr, json_length,
                    nullptr, timeout_ms, &e),
            rs2_delete_raw_data);
    handle_error(env, e);

    if (new_table_buffer != nullptr) {
        auto size = rs2_get_raw_data_size(new_table_buffer.get(), &e);
        handle_error(env, e);
        auto start = rs2_get_raw_data(new_table_buffer.get(), &e);
        handle_error(env, e);

        std::vector<uint8_t> new_table;
        new_table.insert(new_table.begin(), start, start + size);


        copy_vector_to_jbytebuffer(env, new_table, target_buffer);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_intel_realsense_librealsense_AutoCalibDevice_nResetToFactoryCalibration(JNIEnv *env, jobject instance,
                                                            jlong device_handle) {
    rs2_device* device = reinterpret_cast<rs2_device*>(device_handle);
    rs2_error* e = nullptr;
    rs2_reset_to_factory_calibration(device, &e);
    handle_error(env, e);
}


