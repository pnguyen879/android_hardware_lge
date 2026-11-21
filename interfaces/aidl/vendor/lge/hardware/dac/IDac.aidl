//
// SPDX-FileCopyrightText: The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
//

package vendor.lge.hardware.dac;

import vendor.lge.hardware.dac.Feature;
import vendor.lge.hardware.dac.FeatureStates;

@VintfStability
interface IDac {
    int getCustomFilterCoeff(in int coeffIndex);
    int getCustomFilterShape();
    int getCustomFilterSymmetry();
    int getFeatureValue(in Feature feature);
    boolean getHifiDacState();
    FeatureStates getSupportedFeatureValues(in Feature feature);
    Feature[] getSupportedFeatures();
    boolean resetCustomFilterCoeffs();
    boolean setCustomFilterCoeff(in int coeffIndex, in int value);
    boolean setCustomFilterShape(in int shape);
    boolean setCustomFilterSymmetry(in int symmetry);
    boolean setFeatureValue(in Feature feature, in int value);
    boolean setHifiDacState(in boolean enable);
}
