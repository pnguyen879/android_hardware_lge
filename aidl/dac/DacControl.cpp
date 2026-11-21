/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#include "DacControl.h"

#include <android-base/logging.h>
#include <android-base/strings.h>
#include <cutils/properties.h>
#include <media/AudioSystem.h>
#include <utils/String8.h>

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

namespace vendor {
namespace lge {
namespace hardware {
namespace audio {
namespace dac {
namespace control {
namespace service {
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

    /* Quad DAC */
    mSupportedFeatures.push_back(Feature::QuadDAC);
    setHifiDacStateImpl(getHifiDacStateImpl());

    /* Digital Filter */
    if (stat(essFilterPath.c_str(), &buffer) == 0) {
        mSupportedFeatures.push_back(Feature::DigitalFilter);
        FeatureStates digfilter_fstates;
        digfilter_fstates.states = digital_filters;
        mSupportedStates.emplace(Feature::DigitalFilter, digfilter_fstates);
        setFeatureValueImpl(Feature::DigitalFilter, getFeatureValueImpl(Feature::DigitalFilter));
    }

#ifdef PROPRIETARY_AUDIO_MODULE
    /* Sound Presets */
    mSupportedFeatures.push_back(Feature::SoundPreset);
    FeatureStates soundpresets_fstates;
    soundpresets_fstates.states = sound_presets;
    mSupportedStates.emplace(Feature::SoundPreset, soundpresets_fstates);
    setFeatureValueImpl(Feature::SoundPreset, getFeatureValueImpl(Feature::SoundPreset));
#endif

    /* Balance Left */
    if (stat(volumeLeftPath.c_str(), &buffer) == 0) {
        mSupportedFeatures.push_back(Feature::BalanceLeft);
        FeatureStates balanceleft_fstates;
        balanceleft_fstates.range.max = MAX_BALANCE_VALUE;
        balanceleft_fstates.range.min = MIN_BALANCE_VALUE;
        balanceleft_fstates.range.step = 1;
        mSupportedStates.emplace(Feature::BalanceLeft, balanceleft_fstates);
        setFeatureValueImpl(Feature::BalanceLeft, getFeatureValueImpl(Feature::BalanceLeft));
    }

    /* Balance Right */
    if (stat(volumeRightPath.c_str(), &buffer) == 0) {
        mSupportedFeatures.push_back(Feature::BalanceRight);
        FeatureStates balanceright_fstates;
        balanceright_fstates.range.max = MAX_BALANCE_VALUE;
        balanceright_fstates.range.min = MIN_BALANCE_VALUE;
        balanceright_fstates.range.step = 1;
        mSupportedStates.emplace(Feature::BalanceRight, balanceright_fstates);
        setFeatureValueImpl(Feature::BalanceRight, getFeatureValueImpl(Feature::BalanceRight));
    }

    /* AVC Volume */
    if (stat(avcPath.c_str(), &buffer) == 0) {
        mSupportedFeatures.push_back(Feature::AVCVolume);
        writeAvcVolumeState(getFeatureValueImpl(Feature::AVCVolume));
    }

    /* Hi-Fi Mode setting */
    if (stat(hifiPath.c_str(), &buffer) == 0) {
        mSupportedFeatures.push_back(Feature::HifiMode);
        writeHifiModeState(getFeatureValueImpl(Feature::HifiMode));
    }

    /* Custom ESS filter setting */
    if (stat(customFilterPath.c_str(), &buffer) == 0) {
        mSupportedFeatures.push_back(Feature::CustomFilter);
        setCustomFilterShapeImpl(getCustomFilterShapeImpl());
        setCustomFilterSymmetryImpl(getCustomFilterSymmetryImpl());
        for (int i = 0; i < 14; i++) setCustomFilterCoeffImpl(i, getCustomFilterCoeffImpl(i));
    }
}

ndk::ScopedAStatus DacControl::getSupportedFeatures(std::vector<Feature>* result) {
    *result = mSupportedFeatures;
    return ndk::ScopedAStatus::ok();
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

ndk::ScopedAStatus DacControl::getSupportedFeatureValues(Feature feature, FeatureStates* result) {
    switch (feature) {
        case Feature::AVCVolume: *result = getAvcVolumeStates(); break;
        case Feature::HifiMode: *result = getHifiModeStates(); break;
        default: { auto it = mSupportedStates.find(feature); if (it != mSupportedStates.end()) *result = it->second; break; }
    }
    return ndk::ScopedAStatus::ok();
}


#define DAC_BOOL_WRAPPER(name, args, call) ndk::ScopedAStatus DacControl::name args { *result = call; return ndk::ScopedAStatus::ok(); }
DAC_BOOL_WRAPPER(getHifiDacState, (bool* result), getHifiDacStateImpl())
DAC_BOOL_WRAPPER(setHifiDacState, (bool enable, bool* result), setHifiDacStateImpl(enable))
DAC_BOOL_WRAPPER(setFeatureValue, (Feature feature, int32_t value, bool* result), setFeatureValueImpl(feature, value))
DAC_BOOL_WRAPPER(getFeatureValue, (Feature feature, int32_t* result), getFeatureValueImpl(feature))
DAC_BOOL_WRAPPER(getCustomFilterShape, (int32_t* result), getCustomFilterShapeImpl())
DAC_BOOL_WRAPPER(setCustomFilterShape, (int32_t shape, bool* result), setCustomFilterShapeImpl(shape))
DAC_BOOL_WRAPPER(getCustomFilterSymmetry, (int32_t* result), getCustomFilterSymmetryImpl())
DAC_BOOL_WRAPPER(setCustomFilterSymmetry, (int32_t symmetry, bool* result), setCustomFilterSymmetryImpl(symmetry))
DAC_BOOL_WRAPPER(getCustomFilterCoeff, (int32_t index, int32_t* result), getCustomFilterCoeffImpl(index))
DAC_BOOL_WRAPPER(setCustomFilterCoeff, (int32_t index, int32_t value, bool* result), setCustomFilterCoeffImpl(index, value))
DAC_BOOL_WRAPPER(resetCustomFilterCoeffs, (bool* result), resetCustomFilterCoeffsImpl())
#undef DAC_BOOL_WRAPPER

bool DacControl::writeAvcVolumeState(int32_t value) {
    // we save it as the actual value, while the kernel requires a positive value
    bool ok = set(avcPath, (-1) * value);
    return property_set(PROPERTY_HIFI_DAC_AVC_VOLUME, std::to_string(value).c_str()) == 0 && ok;
}

bool DacControl::writeHifiModeState(int32_t value) {
    bool ok = set(hifiPath, value);
    return property_set(PROPERTY_HIFI_DAC_MODE, std::to_string(value).c_str()) == 0 && ok;
}

bool DacControl::setAudioHALParameters(KeyValue kv) {
    const std::string parameters = kv.name + "=" + kv.value;
    return ::android::AudioSystem::setParameters(
                   AUDIO_IO_HANDLE_NONE, ::android::String8(parameters.c_str())) == 0;
}

bool DacControl::setHifiDacStateImpl(bool enable) {
    KeyValue kv;
    kv.name = DAC_COMMAND;
    kv.value = enable ? SET_DAC_ON_COMMAND : SET_DAC_OFF_COMMAND;
    return setAudioHALParameters(kv);
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

bool DacControl::setFeatureValueImpl(Feature feature, int32_t value) {
    bool rc;

    if (std::find(mSupportedFeatures.begin(), mSupportedFeatures.end(), feature) ==
        mSupportedFeatures.end()) {
        LOG(ERROR) << "DacControl::setFeatureValue: tried to set value for unsupported Feature...";
        return false;
    }

    KeyValue kv;
    std::string property;
    switch (feature) {
        case Feature::DigitalFilter: {
            return setDigitalFilterState(value);
        }
        case Feature::SoundPreset: {
            kv.name = SET_SOUND_PRESET_COMMAND;
            property = PROPERTY_SOUND_PRESET;
            break;
        }
        case Feature::BalanceLeft:
        case Feature::BalanceRight:
            return setVolumeBalance(feature, value);
        case Feature::AVCVolume: {
            return writeAvcVolumeState(value);
        }
        case Feature::HifiMode: {
            return writeHifiModeState(value);
        }
        default:
            return false;
    }
    kv.value = std::to_string(value);

#ifdef PROPRIETARY_AUDIO_MODULE
    rc = setAudioHALParameters(kv);
#else
    rc = true;
#endif

    if (rc) {
        property_set(property.c_str(), kv.value.c_str());
        return true;
    } else {
        return false;
    }
}

bool DacControl::getHifiDacStateImpl() {
    char value[PROPERTY_VALUE_MAX];
    property_get(PROPERTY_HIFI_DAC_ENABLED, value, PROPERTY_VALUE_HIFI_DAC_DISABLED);
    return (strcmp(value, PROPERTY_VALUE_HIFI_DAC_ENABLED) == 0);
}

int32_t DacControl::getFeatureValueImpl(Feature feature) {
    if (std::find(mSupportedFeatures.begin(), mSupportedFeatures.end(), feature) ==
        mSupportedFeatures.end()) {
        LOG(ERROR) << "DacControl::getFeatureValue: tried to set value for unsupported Feature...";
        return -1;
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
            return false;
    }
    property_get(property.c_str(), value, std::to_string(property_default_value).c_str());
    return std::stoi(value);
}

// Custom filter implementation

int32_t DacControl::getCustomFilterShapeImpl(void) {
    return property_get_int32(PROPERTY_CUSTOM_FILTER_SHAPE, 0);
}

int32_t DacControl::getCustomFilterSymmetryImpl(void) {
    return property_get_int32(PROPERTY_CUSTOM_FILTER_SYMMETRY, 0);
}

int32_t DacControl::getCustomFilterCoeffImpl(int32_t coeffIndex) {
    return property_get_int32(PROPERTY_CUSTOM_FILTER_COEFFS.at(coeffIndex).c_str(), 0);
}

std::string DacControl::parseUpdatedCustomFilterData() {
    std::string filter_data;

    /*
     * Let's build the actual string with the custom filter's shape, symmetry and 14 Stage 2
     * coefficients
     *
     */
    filter_data.append(std::to_string(getCustomFilterShapeImpl())).append(",");
    filter_data.append(std::to_string(getCustomFilterSymmetryImpl())).append(",");
    for (int i = 0; i < 14; i++) {
        filter_data.append(std::to_string(getCustomFilterCoeffImpl(i)));
        if (i < 13) /* Last element doesn't need to have a comma appended after it */
            filter_data.append(",");
    }

    return filter_data;
}

bool DacControl::setCustomFilterShapeImpl(int32_t shape) {
    if (std::find(mSupportedFeatures.begin(), mSupportedFeatures.end(), Feature::CustomFilter) ==
        mSupportedFeatures.end()) {
        LOG(ERROR) << "DacControl::setCustomFilterShape: tried to set custom filter control on "
                      "unsupported device";
        return false;
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
        return false;
    }
    set(customFilterPath, parseUpdatedCustomFilterData());
    return true;
}

bool DacControl::setCustomFilterSymmetryImpl(int symmetry) {
    if (std::find(mSupportedFeatures.begin(), mSupportedFeatures.end(), Feature::CustomFilter) ==
        mSupportedFeatures.end()) {
        LOG(ERROR) << "DacControl::setCustomFilterSymmetry: tried to set custom filter control on "
                      "unsupported device";
        return false;
    }

    int rc = property_set(PROPERTY_CUSTOM_FILTER_SYMMETRY, std::to_string(symmetry).c_str());
    if (rc) {
        LOG(ERROR) << "DacControl::setCustomFilterSymmetry: failed to set property "
                   << PROPERTY_CUSTOM_FILTER_SYMMETRY << " with error " << rc;
        return false;
    }
    set(customFilterPath, parseUpdatedCustomFilterData());
    return true;
}

bool DacControl::setCustomFilterCoeffImpl(int coeffIndex, int value) {
    if (std::find(mSupportedFeatures.begin(), mSupportedFeatures.end(), Feature::CustomFilter) ==
        mSupportedFeatures.end()) {
        LOG(ERROR) << "DacControl::setCustomFilterCoeff: tried to set custom filter control on "
                      "unsupported device";
        return false;
    }

    int rc = property_set(PROPERTY_CUSTOM_FILTER_COEFFS.at(coeffIndex).c_str(),
                          std::to_string(value).c_str());
    if (rc) {
        LOG(ERROR) << "DacControl::setCustomFilterCoeff: failed to set property "
                   << PROPERTY_CUSTOM_FILTER_COEFFS.at(coeffIndex) << " with error " << rc;
        return false;
    }
    set(customFilterPath, parseUpdatedCustomFilterData());
    return true;
}

bool DacControl::resetCustomFilterCoeffsImpl() {
    int rc;

    if (std::find(mSupportedFeatures.begin(), mSupportedFeatures.end(), Feature::CustomFilter) ==
        mSupportedFeatures.end()) {
        LOG(ERROR) << "DacControl::setCustomFilterCoeff: tried to set custom filter control on "
                      "unsupported device";
        return false;
    }

    for (int i = 0; i < 14; i++) {
        rc = property_set(PROPERTY_CUSTOM_FILTER_COEFFS.at(i).c_str(), "0");
        if (rc) {
            LOG(ERROR) << "DacControl::resetCustomFilterCoeff: failed to set property "
                       << PROPERTY_CUSTOM_FILTER_COEFFS.at(i) << " with error " << rc;
            return false;
        }
    }
    set(customFilterPath, parseUpdatedCustomFilterData());

    return true;
}

}  // namespace implementation
} } } } } } } }
