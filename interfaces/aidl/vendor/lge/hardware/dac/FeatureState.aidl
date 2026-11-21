//
// SPDX-FileCopyrightText: The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
//

package vendor.lge.hardware.dac;

import vendor.lge.hardware.dac.KeyValue;

@VintfStability
parcelable FeatureState {
    KeyValue kv;
    float value;
}
