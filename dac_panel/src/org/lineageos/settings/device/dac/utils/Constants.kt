/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dac.utils

object Constants {
    const val DAC_SWITCH_KEY = "quaddac_switch"
    const val SOUND_PRESET_KEY = "sound_preset_dropdown"
    const val DIGITAL_FILTER_KEY = "digital_filter_dropdown"
    const val BALANCE_KEY = "balance"
    const val RESET_COEFFICIENTS_KEY = "reset_coefficients"
    const val HIFI_DOP_KEY = "hifi_dop_dropdown"
    const val HIFI_MODE_KEY = "hifi_mode_dropdown"
    const val AVC_VOLUME_KEY = "avc_volume_seekbar"

    const val CUSTOM_FILTER_SHAPE_KEY = "custom_filter_shape"
    const val CUSTOM_FILTER_SYMMETRY_KEY = "custom_filter_symmetry"
    const val CUSTOM_FILTER_SETTINGS_CATEGORY_KEY = "custom_filter_settings_category"
    const val CUSTOM_FILTER_COEFFS_CATEGORY_KEY = "custom_filter_coeffs_category"
    val CUSTOM_FILTER_COEFF_KEYS = List(14) { "custom_filter_coeff$it" }
}
