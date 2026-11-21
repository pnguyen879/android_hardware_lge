/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <aidl/vendor/lge/hardware/dac/BnDac.h>
#include <aidl/vendor/lge/hardware/dac/Feature.h>
#include <aidl/vendor/lge/hardware/dac/FeatureStates.h>
#include <aidl/vendor/lge/hardware/dac/KeyValue.h>


#include <map>
#include <unordered_set>
#include <vector>

#include "Constants.h"

namespace vendor {
namespace lge {
namespace hardware {
namespace audio {
namespace dac {
namespace control {
namespace service {
namespace implementation {

using ::aidl::vendor::lge::hardware::dac::BnDac;
using ::aidl::vendor::lge::hardware::dac::Feature;
using ::aidl::vendor::lge::hardware::dac::FeatureStates;
using ::aidl::vendor::lge::hardware::dac::KeyValue;

class DacControl : public BnDac {
  public:
    DacControl();

    ndk::ScopedAStatus getSupportedFeatures(std::vector<Feature>* _aidl_return) override;
    ndk::ScopedAStatus getSupportedFeatureValues(Feature feature, FeatureStates* _aidl_return) override;

    ndk::ScopedAStatus getHifiDacState(bool* result) override;
    ndk::ScopedAStatus setHifiDacState(bool enable, bool* result) override;
    ndk::ScopedAStatus setFeatureValue(Feature feature, int32_t value, bool* result) override;
    ndk::ScopedAStatus getFeatureValue(Feature feature, int32_t* result) override;
    ndk::ScopedAStatus getCustomFilterShape(int32_t* result) override;
    ndk::ScopedAStatus setCustomFilterShape(int32_t shape, bool* result) override;
    ndk::ScopedAStatus getCustomFilterSymmetry(int32_t* result) override;
    ndk::ScopedAStatus setCustomFilterSymmetry(int32_t symmetry, bool* result) override;
    ndk::ScopedAStatus getCustomFilterCoeff(int32_t coeffIndex, int32_t* result) override;
    ndk::ScopedAStatus setCustomFilterCoeff(int32_t coeffIndex, int32_t value, bool* result) override;
    ndk::ScopedAStatus resetCustomFilterCoeffs(bool* result) override;

    std::map<Feature, FeatureStates> mSupportedStates;
    std::vector<Feature> mSupportedFeatures;

  private:
    bool getHifiDacStateImpl(); bool setHifiDacStateImpl(bool);
    bool setFeatureValueImpl(Feature, int32_t); int32_t getFeatureValueImpl(Feature);
    int32_t getCustomFilterShapeImpl(); bool setCustomFilterShapeImpl(int32_t);
    int32_t getCustomFilterSymmetryImpl(); bool setCustomFilterSymmetryImpl(int32_t);
    int32_t getCustomFilterCoeffImpl(int32_t); bool setCustomFilterCoeffImpl(int32_t, int32_t);
    bool resetCustomFilterCoeffsImpl();
    FeatureStates getAvcVolumeStates();
    FeatureStates getHifiModeStates();
    bool writeAvcVolumeState(int32_t value);
    bool writeHifiModeState(int32_t value);
    bool setAudioHALParameters(KeyValue kv);
    bool setDigitalFilterState(int32_t value);
    bool setVolumeBalance(Feature direction, int32_t value);
    std::string parseUpdatedCustomFilterData();

    std::string avcPath;
    std::string hifiPath;
    std::string volumeLeftPath;
    std::string volumeRightPath;
    std::string essFilterPath;
    std::string customFilterPath;
};

}  // namespace implementation
}  // namespace service
}  // namespace control
}  // namespace dac
}  // namespace audio
}  // namespace hardware
}  // namespace lge
}  // namespace vendor
