/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "vendor.lge.hardware.dac-service"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

#include "DacControl.h"

using vendor::lge::hardware::audio::dac::control::service::implementation::DacControl;

int main() {
    ABinderProcess_setThreadPoolMaxThreadCount(1);
    auto service = ndk::SharedRefBase::make<DacControl>();
    const std::string instance = std::string(DacControl::descriptor) + "/default";
    binder_status_t status = AServiceManager_addService(service->asBinder().get(), instance.c_str());
    if (status != STATUS_OK) {
        LOG(ERROR) << "Failed to register DAC service: " << status;
        return 1;
    }
    ABinderProcess_joinThreadPool();
    return 1;
}
