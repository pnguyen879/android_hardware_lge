/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <aidl/vendor/lge/hardware/dac/BnDac.h>

#include <android/hardware/audio/5.0/IDevice.h>
#include <android/hardware/audio/5.0/IDevicesFactory.h>
#include <android/hardware/audio/6.0/IDevice.h>
#include <android/hardware/audio/6.0/IDevicesFactory.h>
#include <android/hardware/audio/7.0/IDevice.h>
#include <android/hardware/audio/7.0/IDevicesFactory.h>

#include <map>
#include <unordered_set>
#include <vector>

#include "Constants.h"

namespace aidl {
namespace vendor {
namespace lge {
namespace hardware {
namespace dac {
namespace implementation {

using ::aidl::vendor::lge::hardware::dac::Feature;
using ::aidl::vendor::lge::hardware::dac::FeatureStates;
using ::aidl::vendor::lge::hardware::dac::KeyValue;
using ::android::hardware::hidl_vec;
using ::ndk::ScopedAStatus;

enum AudioVersion { V5_0, V6_0, V7_0 };

class DacControl : public ::aidl::vendor::lge::hardware::dac::BnDac {
  public:
    DacControl();

    ScopedAStatus getSupportedFeatures(std::vector<Feature>* features) override;
    ScopedAStatus getSupportedFeatureValues(Feature feature, FeatureStates* states) override;
    ScopedAStatus getHifiDacState(bool* enabled) override;
    ScopedAStatus setHifiDacState(bool enable, bool* success) override;
    ScopedAStatus setFeatureValue(Feature feature, int32_t value, bool* success) override;
    ScopedAStatus getFeatureValue(Feature feature, int32_t* value) override;
    ScopedAStatus getCustomFilterShape(int32_t* shape) override;
    ScopedAStatus setCustomFilterShape(int32_t shape, bool* success) override;
    ScopedAStatus getCustomFilterSymmetry(int32_t* symmetry) override;
    ScopedAStatus setCustomFilterSymmetry(int32_t symmetry, bool* success) override;
    ScopedAStatus getCustomFilterCoeff(int32_t coeffIndex, int32_t* value) override;
    ScopedAStatus setCustomFilterCoeff(int32_t coeffIndex, int32_t value, bool* success) override;
    ScopedAStatus resetCustomFilterCoeffs(bool* success) override;

    std::map<Feature, FeatureStates> mSupportedStates;
    std::vector<Feature> mSupportedFeatures;

  private:
    FeatureStates getAvcVolumeStates();
    FeatureStates getHifiModeStates();
    bool writeAvcVolumeState(int32_t value);
    bool writeHifiModeState(int32_t value);
    bool setAudioHALParameters(const KeyValue& kv);
    bool setDigitalFilterState(int32_t value);
    bool setVolumeBalance(Feature direction, int32_t value);
    std::string parseUpdatedCustomFilterData();

    android::sp<::android::hardware::audio::V5_0::IDevice> mAudioDevice_V5_0;
    android::sp<::android::hardware::audio::V5_0::IDevicesFactory> mAudioDevicesFactory_V5_0;
    android::sp<::android::hardware::audio::V6_0::IDevice> mAudioDevice_V6_0;
    android::sp<::android::hardware::audio::V6_0::IDevicesFactory> mAudioDevicesFactory_V6_0;
    android::sp<::android::hardware::audio::V7_0::IDevice> mAudioDevice_V7_0;
    android::sp<::android::hardware::audio::V7_0::IDevicesFactory> mAudioDevicesFactory_V7_0;

    AudioVersion usedVersion;

    std::string avcPath;
    std::string hifiPath;
    std::string volumeLeftPath;
    std::string volumeRightPath;
    std::string essFilterPath;
    std::string customFilterPath;
};

}  // namespace implementation
}  // namespace dac
}  // namespace hardware
}  // namespace lge
}  // namespace vendor
}  // namespace aidl
