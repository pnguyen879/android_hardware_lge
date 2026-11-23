/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "vendor.lge.hardware.dac-service"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

#include <string>

#include "DacControl.h"

using ::aidl::vendor::lge::hardware::dac::IDac;
using ::aidl::vendor::lge::hardware::dac::implementation::DacControl;

int main() {
    LOG(INFO) << "DAC Control HAL service is starting.";
    ABinderProcess_setThreadPoolMaxThreadCount(0);
    auto interface = ndk::SharedRefBase::make<DacControl>();
    const std::string instance = std::string(IDac::descriptor) + "/default";
    const binder_status_t status =
            AServiceManager_addService(interface->asBinder().get(), instance.c_str());
    if (status != STATUS_OK) {
        LOG(ERROR) << "Could not register DAC Control HAL service (" << status << ")";
        return 1;
    }

    LOG(INFO) << "DAC Control HAL service is ready.";
    ABinderProcess_joinThreadPool();
    return 0;
}
