/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include "CameraProvider.h"

#include <set>
#include <string>

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {

class LgeCameraProvider : public CameraProvider {
  public:
    LgeCameraProvider();
    ~LgeCameraProvider() override = default;

    // Hooks that intercept HAL notifications and suppress blacklisted IDs.
    static void cameraDeviceStatusChange(const camera_module_callbacks_t* callbacks, int cameraId,
                                         int newStatus);
    static void torchModeStatusChange(const camera_module_callbacks_t* callbacks,
                                      const char* cameraId, int newStatus);
};

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
