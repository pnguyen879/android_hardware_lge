/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#include "DacControl.h"

#include <android-base/logging.h>
#include <android-base/strings.h>
#include <cutils/properties.h>

#include <sys/stat.h>
#include <unistd.h>
#include <fstream>

std::vector<std::string> PROPERTY_CUSTOM_FILTER_COEFFS = {
        "persist.vendor.audio.ess.customFilterCoeff0",
        "persist.vendor.audio.ess.customFilterCoeff1",
        "persist.vendor.audio.ess.customFilterCoeff2",
        "persist.vendor.audio.ess.customFilterCoeff3",
        "persist.vendor.audio.ess.customFilterCoeff4",
        "persist.vendor.audio.ess.customFilterCoeff5",
        "persist.vendor.audio.ess.customFilterCoeff6",
        "persist.vendor.audio.ess.customFilterCoeff7",
        "persist.vendor.audio.ess.customFilterCoeff8",
        "persist.vendor.audio.ess.customFilterCoeff9",
        "persist.vendor.audio.ess.customFilterCoeff10",
        "persist.vendor.audio.ess.customFilterCoeff11",
        "persist.vendor.audio.ess.customFilterCoeff12",
        "persist.vendor.audio.ess.customFilterCoeff13"};

namespace aidl {
namespace vendor {
namespace lge {
namespace hardware {
namespace dac {
namespace implementation {

static constexpr int32_t MAX_BALANCE_VALUE = 0;
static constexpr int32_t MIN_BALANCE_VALUE = -12;

static std::vector<KeyValue> sound_presets = {
        {"Normal", "0"}, {"Enhanced", "1"}, {"Detailed", "2"}, {"Live", "3"}, {"Bass", "4"}};

static std::vector<KeyValue> digital_filters = {{"Short", "0"}, {"Sharp", "1"}, {"Slow", "2"}};

static std::vector<KeyValue> hifi_modes = {
        {"Normal", "0"}, {"High Impedance", "1"}, {"AUX High Impedance", "2"}};

/*
 * Write value to path and close file. Returns false if the write failed.
 */
template <typename T>
static bool set(const std::string& path, const T& value) {
    std::ofstream file(path);
    if (!file.is_open()) {
        LOG(ERROR) << "Failed to open " << path;
        return false;
    }
    file << value;
    file.flush();
    if (file.fail()) {
        LOG(ERROR) << "Failed to write '" << value << "' to " << path;
        return false;
    }
    return true;
}

DacControl::DacControl() {
    if (access(COMMON_ES9218_PATH, F_OK) != 0) {
        LOG(ERROR) << "DacControl: No ES9218 path found, exiting...";
        return;
    }

    struct stat buffer;

    avcPath = std::string(COMMON_ES9218_PATH);
    avcPath.append(AVC_VOLUME);
    volumeLeftPath = std::string(COMMON_ES9218_PATH);
    volumeLeftPath.append(VOLUME_LEFT);
    volumeRightPath = std::string(COMMON_ES9218_PATH);
    volumeRightPath.append(VOLUME_RIGHT);
    hifiPath = std::string(COMMON_ES9218_PATH);
    hifiPath.append(HIFI_MODE);
    essFilterPath = std::string(COMMON_ES9218_PATH);
    essFilterPath.append(ESS_FILTER);
    customFilterPath = std::string(COMMON_ES9218_PATH);
    customFilterPath.append(ESS_CUSTOM_FILTER);

    mAudioDevicesFactory_V7_0 = ::android::hardware::audio::V7_0::IDevicesFactory::getService();
    if (mAudioDevicesFactory_V7_0 != nullptr) {
        usedVersion = AudioVersion::V7_0;
    } else {
        LOG(INFO) << "mAudioDevicesFactory_V7_0 null, trying V6_0";
        mAudioDevicesFactory_V6_0 = ::android::hardware::audio::V6_0::IDevicesFactory::getService();

        if (mAudioDevicesFactory_V6_0 != nullptr) {
            usedVersion = AudioVersion::V6_0;
        } else {
            LOG(INFO) << "mAudioDevicesFactory_V6_0 null, trying V5_0";
            mAudioDevicesFactory_V5_0 =
                    ::android::hardware::audio::V5_0::IDevicesFactory::getService();

            if (mAudioDevicesFactory_V5_0 == nullptr) {
                LOG(ERROR) << "mAudioDevicesFactory_V5_0 null, aborting";
                return;
            }
            usedVersion = AudioVersion::V5_0;
        }
    }

    switch (usedVersion) {
        case AudioVersion::V7_0: {
            std::function<void(::android::hardware::audio::V7_0::Result,
                               const android::sp<::android::hardware::audio::V7_0::IDevice>&)>
                    openDevice_cb_V7_0 =
                            [this](::android::hardware::audio::V7_0::Result result,
                                   const android::sp<::android::hardware::audio::V7_0::IDevice>&
                                           device) {
                                if (result == ::android::hardware::audio::V7_0::Result::OK) {
                                    this->mAudioDevice_V7_0 = device;
                                } else {
                                    LOG(INFO) << "Couldnt open primary audio device";
                                }
                            };
            mAudioDevicesFactory_V7_0->openDevice("primary", openDevice_cb_V7_0);

            if (mAudioDevice_V7_0 == nullptr) {
                LOG(INFO) << "mAudioDevice_V7_0 null, aborting";
                return;
            }
        } break;
        case AudioVersion::V6_0: {
            std::function<void(::android::hardware::audio::V6_0::Result,
                               const android::sp<::android::hardware::audio::V6_0::IDevice>&)>
                    openDevice_cb_V6_0 =
                            [this](::android::hardware::audio::V6_0::Result result,
                                   const android::sp<::android::hardware::audio::V6_0::IDevice>&
                                           device) {
                                if (result == ::android::hardware::audio::V6_0::Result::OK) {
                                    this->mAudioDevice_V6_0 = device;
                                } else {
                                    LOG(INFO) << "Couldnt open primary audio device";
                                }
                            };
            mAudioDevicesFactory_V6_0->openDevice("primary", openDevice_cb_V6_0);

            if (mAudioDevice_V6_0 == nullptr) {
                LOG(INFO) << "mAudioDevice_V6_0 null, aborting";
                return;
            }
        } break;
        case AudioVersion::V5_0: {
            std::function<void(::android::hardware::audio::V5_0::Result,
                               const android::sp<::android::hardware::audio::V5_0::IDevice>&)>
                    openDevice_cb_V5_0 =
                            [this](::android::hardware::audio::V5_0::Result result,
                                   const android::sp<::android::hardware::audio::V5_0::IDevice>&
                                           device) {
                                if (result == ::android::hardware::audio::V5_0::Result::OK) {
                                    this->mAudioDevice_V5_0 = device;
                                } else {
                                    LOG(INFO) << "Couldnt open primary audio device";
                                }
                            };
            mAudioDevicesFactory_V5_0->openDevice("primary", openDevice_cb_V5_0);

            if (mAudioDevice_V5_0 == nullptr) {
                LOG(INFO) << "mAudioDevice_V5_0 null, aborting";
                return;
            }
        } break;
        default:
            return;  // Should never reach this state
    }

    /* Quad DAC */
    mSupportedFeatures.push_back(Feature::QuadDAC);
    char enabled[PROPERTY_VALUE_MAX];
    property_get(PROPERTY_HIFI_DAC_ENABLED, enabled, PROPERTY_VALUE_HIFI_DAC_DISABLED);
    bool ignored;
    setHifiDacState(strcmp(enabled, PROPERTY_VALUE_HIFI_DAC_ENABLED) == 0, &ignored);

    /* Digital Filter */
    if (stat(essFilterPath.c_str(), &buffer) == 0) {
        mSupportedFeatures.push_back(Feature::DigitalFilter);
        FeatureStates digfilter_fstates;
        digfilter_fstates.states = digital_filters;
        mSupportedStates.emplace(Feature::DigitalFilter, digfilter_fstates);
        setDigitalFilterState(property_get_int32(PROPERTY_DIGITAL_FILTER, 0));
    }

#ifdef PROPRIETARY_AUDIO_MODULE
    /* Sound Presets */
    mSupportedFeatures.push_back(Feature::SoundPreset);
    FeatureStates soundpresets_fstates;
    soundpresets_fstates.states = sound_presets;
    mSupportedStates.emplace(Feature::SoundPreset, soundpresets_fstates);
    bool ignored_result;
    setFeatureValue(Feature::SoundPreset, property_get_int32(PROPERTY_SOUND_PRESET, 0),
                    &ignored_result);
#endif

    /* Balance Left */
    if (stat(volumeLeftPath.c_str(), &buffer) == 0) {
        mSupportedFeatures.push_back(Feature::BalanceLeft);
        FeatureStates balanceleft_fstates;
        balanceleft_fstates.range.max = MAX_BALANCE_VALUE;
        balanceleft_fstates.range.min = MIN_BALANCE_VALUE;
        balanceleft_fstates.range.step = 1;
        mSupportedStates.emplace(Feature::BalanceLeft, balanceleft_fstates);
        setVolumeBalance(Feature::BalanceLeft, property_get_int32(PROPERTY_LEFT_BALANCE, 0));
    }

    /* Balance Right */
    if (stat(volumeRightPath.c_str(), &buffer) == 0) {
        mSupportedFeatures.push_back(Feature::BalanceRight);
        FeatureStates balanceright_fstates;
        balanceright_fstates.range.max = MAX_BALANCE_VALUE;
        balanceright_fstates.range.min = MIN_BALANCE_VALUE;
        balanceright_fstates.range.step = 1;
        mSupportedStates.emplace(Feature::BalanceRight, balanceright_fstates);
        setVolumeBalance(Feature::BalanceRight, property_get_int32(PROPERTY_RIGHT_BALANCE, 0));
    }

    /* AVC Volume */
    if (stat(avcPath.c_str(), &buffer) == 0) {
        mSupportedFeatures.push_back(Feature::AVCVolume);
        writeAvcVolumeState(property_get_int32(PROPERTY_HIFI_DAC_AVC_VOLUME, AVC_VOLUME_DEFAULT));
    }

    /* Hi-Fi Mode setting */
    if (stat(hifiPath.c_str(), &buffer) == 0) {
        mSupportedFeatures.push_back(Feature::HifiMode);
        writeHifiModeState(property_get_int32(PROPERTY_HIFI_DAC_MODE, HIFI_MODE_DEFAULT));
    }

    /* Custom ESS filter setting */
    if (stat(customFilterPath.c_str(), &buffer) == 0) {
        mSupportedFeatures.push_back(Feature::CustomFilter);
        set(customFilterPath, parseUpdatedCustomFilterData());
    }
}

ScopedAStatus DacControl::getSupportedFeatures(std::vector<Feature>* features) {
    *features = mSupportedFeatures;
    return ScopedAStatus::ok();
}

FeatureStates DacControl::getAvcVolumeStates() {
    FeatureStates states;

    states.range.min = -24;
    states.range.max = 0;
    states.range.step = 1;

    return states;
}

FeatureStates DacControl::getHifiModeStates() {
    FeatureStates states;

    states.states = hifi_modes;

    return states;
}

ScopedAStatus DacControl::getSupportedFeatureValues(Feature feature, FeatureStates* states) {
    std::map<Feature, FeatureStates>::iterator it;

    // Check for sysfs-based features; these do different things
    switch (feature) {
        case Feature::AVCVolume:
            *states = getAvcVolumeStates();
            goto end;
        case Feature::HifiMode:
            *states = getHifiModeStates();
            goto end;
        default:
            break;
    }
    it = mSupportedStates.find(feature);
    if (it != mSupportedStates.end()) {
        *states = it->second;
    } else {
        LOG(ERROR) << "DacControl::getSupportedFeatureValues: tried to get values for unsupported "
                      "Feature...";
        return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }

end:
    return ScopedAStatus::ok();
}

bool DacControl::writeAvcVolumeState(int32_t value) {
    // we save it as the actual value, while the kernel requires a positive value
    bool ok = set(avcPath, (-1) * value);
    return property_set(PROPERTY_HIFI_DAC_AVC_VOLUME, std::to_string(value).c_str()) == 0 && ok;
}

bool DacControl::writeHifiModeState(int32_t value) {
    bool ok = set(hifiPath, value);
    return property_set(PROPERTY_HIFI_DAC_MODE, std::to_string(value).c_str()) == 0 && ok;
}

bool DacControl::setAudioHALParameters(const KeyValue& kv) {
    ::android::hardware::audio::V5_0::Result result_V5_0 =
            ::android::hardware::audio::V5_0::Result::NOT_SUPPORTED;
    ::android::hardware::audio::V6_0::Result result_V6_0 =
            ::android::hardware::audio::V6_0::Result::NOT_SUPPORTED;
    ::android::hardware::audio::V7_0::Result result_V7_0 =
            ::android::hardware::audio::V7_0::Result::NOT_SUPPORTED;

    switch (usedVersion) {
        case AudioVersion::V7_0: {
            std::vector<::android::hardware::audio::V7_0::ParameterValue> pv_vec = {
                    {kv.name, kv.value}};
            hidl_vec<::android::hardware::audio::V7_0::ParameterValue> parameters_V7_0 =
                    hidl_vec<::android::hardware::audio::V7_0::ParameterValue>{pv_vec};
            result_V7_0 = mAudioDevice_V7_0->setParameters({}, parameters_V7_0);
        } break;
        case AudioVersion::V6_0: {
            std::vector<::android::hardware::audio::V6_0::ParameterValue> pv_vec = {
                    {kv.name, kv.value}};
            hidl_vec<::android::hardware::audio::V6_0::ParameterValue> parameters_V6_0 =
                    hidl_vec<::android::hardware::audio::V6_0::ParameterValue>{pv_vec};
            result_V6_0 = mAudioDevice_V6_0->setParameters({}, parameters_V6_0);
        } break;
        case AudioVersion::V5_0: {
            std::vector<::android::hardware::audio::V5_0::ParameterValue> pv_vec = {
                    {kv.name, kv.value}};
            hidl_vec<::android::hardware::audio::V5_0::ParameterValue> parameters_V5_0 =
                    hidl_vec<::android::hardware::audio::V5_0::ParameterValue>{pv_vec};
            result_V5_0 = mAudioDevice_V5_0->setParameters({}, parameters_V5_0);
        } break;
        default:
            return false;
    }

    if (result_V5_0 == ::android::hardware::audio::V5_0::Result::OK ||
        result_V6_0 == ::android::hardware::audio::V6_0::Result::OK ||
        result_V7_0 == ::android::hardware::audio::V7_0::Result::OK)
        return true;

    // Should not reach this point
    return false;
}

ScopedAStatus DacControl::setHifiDacState(bool enable, bool* success) {
    KeyValue kv;
    kv.name = DAC_COMMAND;
    kv.value = enable ? SET_DAC_ON_COMMAND : SET_DAC_OFF_COMMAND;
    *success = setAudioHALParameters(kv);
    return ScopedAStatus::ok();
}

bool DacControl::setDigitalFilterState(int32_t value) {
    int filter;

    switch (value) {
        case 0:  // Short
            filter = 9;
            break;
        case 1:  // Sharp
            filter = 4;
            break;
        case 2:  // Slow
            filter = 5;
            break;
        case 3:  // Custom
            filter = 3;
            break;
        default:
            LOG(ERROR) << "DacControl::setDigitalFilterState: Invalid filter " << value;
            return false;
    }
    bool ok = set(essFilterPath, filter);
    return property_set(PROPERTY_DIGITAL_FILTER, std::to_string(value).c_str()) == 0 && ok;
}

bool DacControl::setVolumeBalance(Feature direction, int32_t value) {
    switch (direction) {
        case Feature::BalanceLeft: {
            bool ok = set(volumeLeftPath, value);
            return property_set(PROPERTY_LEFT_BALANCE, std::to_string(value).c_str()) == 0 && ok;
        }
        case Feature::BalanceRight: {
            bool ok = set(volumeRightPath, value);
            return property_set(PROPERTY_RIGHT_BALANCE, std::to_string(value).c_str()) == 0 && ok;
        }
        default:
            return false;
    }
}

ScopedAStatus DacControl::setFeatureValue(Feature feature, int32_t value, bool* success) {
    bool rc;

    if (std::find(mSupportedFeatures.begin(), mSupportedFeatures.end(), feature) ==
        mSupportedFeatures.end()) {
        LOG(ERROR) << "DacControl::setFeatureValue: tried to set value for unsupported Feature...";
        *success = false;
        return ScopedAStatus::ok();
    }

    KeyValue kv;
    std::string property;
    switch (feature) {
        case Feature::DigitalFilter: {
            *success = setDigitalFilterState(value);
            return ScopedAStatus::ok();
        }
        case Feature::SoundPreset: {
            kv.name = SET_SOUND_PRESET_COMMAND;
            property = PROPERTY_SOUND_PRESET;
            break;
        }
        case Feature::BalanceLeft:
        case Feature::BalanceRight:
            *success = setVolumeBalance(feature, value);
            return ScopedAStatus::ok();
        case Feature::AVCVolume: {
            *success = writeAvcVolumeState(value);
            return ScopedAStatus::ok();
        }
        case Feature::HifiMode: {
            *success = writeHifiModeState(value);
            return ScopedAStatus::ok();
        }
        default:
            *success = false;
            return ScopedAStatus::ok();
    }
    kv.value = std::to_string(value);

#ifdef PROPRIETARY_AUDIO_MODULE
    rc = setAudioHALParameters(kv);
#else
    rc = true;
#endif

    if (rc) {
        property_set(property.c_str(), kv.value.c_str());
        *success = true;
    } else {
        *success = false;
    }
    return ScopedAStatus::ok();
}

ScopedAStatus DacControl::getHifiDacState(bool* enabled) {
    char value[PROPERTY_VALUE_MAX];
    property_get(PROPERTY_HIFI_DAC_ENABLED, value, PROPERTY_VALUE_HIFI_DAC_DISABLED);
    *enabled = strcmp(value, PROPERTY_VALUE_HIFI_DAC_ENABLED) == 0;
    return ScopedAStatus::ok();
}

ScopedAStatus DacControl::getFeatureValue(Feature feature, int32_t* result) {
    if (std::find(mSupportedFeatures.begin(), mSupportedFeatures.end(), feature) ==
        mSupportedFeatures.end()) {
        LOG(ERROR) << "DacControl::getFeatureValue: tried to set value for unsupported Feature...";
        *result = -1;
        return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }

    int32_t property_default_value = 0;
    std::string property;
    char value[PROPERTY_VALUE_MAX];

    switch (feature) {
        case Feature::DigitalFilter: {
            property = PROPERTY_DIGITAL_FILTER;
            break;
        }
        case Feature::SoundPreset: {
            property = PROPERTY_SOUND_PRESET;
            break;
        }
        case Feature::BalanceLeft: {
            property = PROPERTY_LEFT_BALANCE;
            break;
        }
        case Feature::BalanceRight: {
            property = PROPERTY_RIGHT_BALANCE;
            break;
        }
        case Feature::AVCVolume: {
            property = PROPERTY_HIFI_DAC_AVC_VOLUME;
            property_default_value = AVC_VOLUME_DEFAULT;
            break;
        }
        case Feature::HifiMode: {
            property = PROPERTY_HIFI_DAC_MODE;
            property_default_value = HIFI_MODE_DEFAULT;
            break;
        }
        default:
            return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }
    property_get(property.c_str(), value, std::to_string(property_default_value).c_str());
    *result = std::stoi(value);
    return ScopedAStatus::ok();
}

// Custom filter implementation

ScopedAStatus DacControl::getCustomFilterShape(int32_t* shape) {
    *shape = property_get_int32(PROPERTY_CUSTOM_FILTER_SHAPE, 0);
    return ScopedAStatus::ok();
}

ScopedAStatus DacControl::getCustomFilterSymmetry(int32_t* symmetry) {
    *symmetry = property_get_int32(PROPERTY_CUSTOM_FILTER_SYMMETRY, 0);
    return ScopedAStatus::ok();
}

ScopedAStatus DacControl::getCustomFilterCoeff(int32_t coeffIndex, int32_t* value) {
    if (coeffIndex < 0 ||
        coeffIndex >= static_cast<int32_t>(PROPERTY_CUSTOM_FILTER_COEFFS.size())) {
        return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }
    *value = property_get_int32(PROPERTY_CUSTOM_FILTER_COEFFS.at(coeffIndex).c_str(), 0);
    return ScopedAStatus::ok();
}

std::string DacControl::parseUpdatedCustomFilterData() {
    std::string filter_data;

    /*
     * Let's build the actual string with the custom filter's shape, symmetry and 14 Stage 2
     * coefficients
     *
     */
    filter_data.append(std::to_string(property_get_int32(PROPERTY_CUSTOM_FILTER_SHAPE, 0)))
            .append(",");
    filter_data.append(std::to_string(property_get_int32(PROPERTY_CUSTOM_FILTER_SYMMETRY, 0)))
            .append(",");
    for (int i = 0; i < PROPERTY_CUSTOM_FILTER_COEFFS.size(); i++) {
        filter_data.append(
                std::to_string(property_get_int32(PROPERTY_CUSTOM_FILTER_COEFFS.at(i).c_str(), 0)));
        if (i < 13) /* Last element doesn't need to have a comma appended after it */
            filter_data.append(",");
    }

    return filter_data;
}

ScopedAStatus DacControl::setCustomFilterShape(int32_t shape, bool* success) {
    if (std::find(mSupportedFeatures.begin(), mSupportedFeatures.end(), Feature::CustomFilter) ==
        mSupportedFeatures.end()) {
        LOG(ERROR) << "DacControl::setCustomFilterShape: tried to set custom filter control on "
                      "unsupported device";
        *success = false;
        return ScopedAStatus::ok();
    }

    int rc;
    if (shape <= 4)
        rc = property_set(PROPERTY_CUSTOM_FILTER_SHAPE, std::to_string(shape).c_str());
    else /* Filter 5 (counting from 0) is enumerated 6 on es9218.h, so anything after receives +1 as
            well */
        rc = property_set(PROPERTY_CUSTOM_FILTER_SHAPE, std::to_string(shape + 1).c_str());
    if (rc) {
        LOG(ERROR) << "DacControl::setCustomFilterShape: failed to set property "
                   << PROPERTY_CUSTOM_FILTER_SHAPE << " with error " << rc;
        *success = false;
        return ScopedAStatus::ok();
    }
    *success = set(customFilterPath, parseUpdatedCustomFilterData());
    return ScopedAStatus::ok();
}

ScopedAStatus DacControl::setCustomFilterSymmetry(int32_t symmetry, bool* success) {
    if (std::find(mSupportedFeatures.begin(), mSupportedFeatures.end(), Feature::CustomFilter) ==
        mSupportedFeatures.end()) {
        LOG(ERROR) << "DacControl::setCustomFilterSymmetry: tried to set custom filter control on "
                      "unsupported device";
        *success = false;
        return ScopedAStatus::ok();
    }

    int rc = property_set(PROPERTY_CUSTOM_FILTER_SYMMETRY, std::to_string(symmetry).c_str());
    if (rc) {
        LOG(ERROR) << "DacControl::setCustomFilterSymmetry: failed to set property "
                   << PROPERTY_CUSTOM_FILTER_SYMMETRY << " with error " << rc;
        *success = false;
        return ScopedAStatus::ok();
    }
    *success = set(customFilterPath, parseUpdatedCustomFilterData());
    return ScopedAStatus::ok();
}

ScopedAStatus DacControl::setCustomFilterCoeff(int32_t coeffIndex, int32_t value, bool* success) {
    if (coeffIndex < 0 ||
        coeffIndex >= static_cast<int32_t>(PROPERTY_CUSTOM_FILTER_COEFFS.size())) {
        return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }
    if (std::find(mSupportedFeatures.begin(), mSupportedFeatures.end(), Feature::CustomFilter) ==
        mSupportedFeatures.end()) {
        LOG(ERROR) << "DacControl::setCustomFilterCoeff: tried to set custom filter control on "
                      "unsupported device";
        *success = false;
        return ScopedAStatus::ok();
    }

    int rc = property_set(PROPERTY_CUSTOM_FILTER_COEFFS.at(coeffIndex).c_str(),
                          std::to_string(value).c_str());
    if (rc) {
        LOG(ERROR) << "DacControl::setCustomFilterCoeff: failed to set property "
                   << PROPERTY_CUSTOM_FILTER_COEFFS.at(coeffIndex) << " with error " << rc;
        *success = false;
        return ScopedAStatus::ok();
    }
    *success = set(customFilterPath, parseUpdatedCustomFilterData());
    return ScopedAStatus::ok();
}

ScopedAStatus DacControl::resetCustomFilterCoeffs(bool* success) {
    int rc;

    if (std::find(mSupportedFeatures.begin(), mSupportedFeatures.end(), Feature::CustomFilter) ==
        mSupportedFeatures.end()) {
        LOG(ERROR) << "DacControl::setCustomFilterCoeff: tried to set custom filter control on "
                      "unsupported device";
        *success = false;
        return ScopedAStatus::ok();
    }

    for (int i = 0; i < 14; i++) {
        rc = property_set(PROPERTY_CUSTOM_FILTER_COEFFS.at(i).c_str(), "0");
        if (rc) {
            LOG(ERROR) << "DacControl::resetCustomFilterCoeff: failed to set property "
                       << PROPERTY_CUSTOM_FILTER_COEFFS.at(i) << " with error " << rc;
            *success = false;
            return ScopedAStatus::ok();
        }
    }
    *success = set(customFilterPath, parseUpdatedCustomFilterData());
    return ScopedAStatus::ok();
}

}  // namespace implementation
}  // namespace dac
}  // namespace hardware
}  // namespace lge
}  // namespace vendor
}  // namespace aidl
