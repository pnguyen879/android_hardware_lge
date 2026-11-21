//
// SPDX-FileCopyrightText: The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
//

package vendor.lge.hardware.dac;

import vendor.lge.hardware.dac.KeyValue;
import vendor.lge.hardware.dac.Range;

@VintfStability
parcelable FeatureStates {
    KeyValue[] states;
    Range range;
}
