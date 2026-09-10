/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "android.hardware.camera.provider-service.lge"

#include "LgeCameraProvider.h"

#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <log/log.h>

using ::android::hardware::camera::provider::implementation::LgeCameraProvider;

int main() {
    ALOGI("CameraProvider: Lineage LGE-filtering service is starting.");

    const int HWBINDER_THREAD_COUNT = 6;
    ABinderProcess_setThreadPoolMaxThreadCount(HWBINDER_THREAD_COUNT);

    // Create and register the filtered provider instance.
    std::shared_ptr<LgeCameraProvider> provider = ndk::SharedRefBase::make<LgeCameraProvider>();
    const std::string serviceName = std::string(LgeCameraProvider::descriptor) + "/legacy/0";

    binder_exception_t ret =
            AServiceManager_addService(provider->asBinder().get(), serviceName.c_str());
    LOG_ALWAYS_FATAL_IF(ret != EX_NONE, "Error while registering camera provider service: %d", ret);

    ABinderProcess_joinThreadPool();
    return EXIT_FAILURE;  // should not reach
}
