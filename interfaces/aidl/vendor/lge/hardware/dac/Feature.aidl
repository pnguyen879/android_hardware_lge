//
// SPDX-FileCopyrightText: The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
//

package vendor.lge.hardware.dac;

@VintfStability
@Backing(type="int")
enum Feature {
    QuadDAC,
    SoundPreset,
    DigitalFilter,
    BalanceLeft,
    BalanceRight,
    HifiMode,
    AVCVolume,
    CustomFilter,
}
