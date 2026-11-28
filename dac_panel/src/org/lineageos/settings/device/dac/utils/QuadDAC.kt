/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dac.utils

import android.os.ServiceManager
import vendor.lge.hardware.dac.Feature
import vendor.lge.hardware.dac.FeatureStates
import vendor.lge.hardware.dac.IDac

object QuadDAC {

    private const val TAG = "QuadDAC"

    private lateinit var dac: IDac

    private lateinit var dac_features: ArrayList<Int>

    var dac_service_available = false

    fun initialize() {
        dac =
            IDac.Stub.asInterface(
                ServiceManager.waitForDeclaredService("vendor.lge.hardware.dac.IDac/default")
            )
        dac_features = ArrayList(dac.supportedFeatures.toList())
        dac_service_available = true
    }

    fun enable() {
        try {
            dac.setHifiDacState(true)

            val mode = getDACMode()
            val left_balance = getLeftBalance()
            val right_balance = getRightBalance()
            val avc_vol = getAVCVolume()
            val digital_filter = getDigitalFilter()
            setDACMode(mode)
            setLeftBalance(left_balance)
            setRightBalance(right_balance)
            setAVCVolume(avc_vol)
            setDigitalFilter(digital_filter)

            if (dac_features.contains(Feature.SoundPreset)) {
                val sound_preset = getSoundPreset()
                setSoundPreset(sound_preset)
            }

            if (dac_features.contains(Feature.CustomFilter)) {
                val custom_filter_shape = getCustomFilterShape()
                val custom_filter_symmetry = getCustomFilterSymmetry()
                val custom_filter_coefficients = IntArray(14)
                var i: Int
                i = 0
                while (i < 14) {
                    custom_filter_coefficients[i] = getCustomFilterCoeff(i)
                    i++
                }
                setCustomFilterShape(custom_filter_shape)
                setCustomFilterSymmetry(custom_filter_symmetry)
                i = 0
                while (i < 14) {
                    setCustomFilterCoeff(i, custom_filter_coefficients[i])
                    i++
                }
            }
        } catch (e: Exception) {}
    }

    fun disable() {
        dac.setHifiDacState(false)
    }

    fun getSupportedFeatures(): ArrayList<Int> {
        return dac_features
    }

    fun getSupportedFeatureValues(feature: Int): FeatureStates {
        return dac.getSupportedFeatureValues(feature)
    }

    fun setDACMode(mode: Int) {
        dac.setFeatureValue(Feature.HifiMode, mode)
    }

    fun getDACMode(): Int {
        return dac.getFeatureValue(Feature.HifiMode)
    }

    fun setAVCVolume(avc_volume: Int) {
        dac.setFeatureValue(Feature.AVCVolume, avc_volume)
    }

    fun getAVCVolume(): Int {
        return dac.getFeatureValue(Feature.AVCVolume)
    }

    fun setDigitalFilter(filter: Int) {
        dac.setFeatureValue(Feature.DigitalFilter, filter)
        if (dac_features.contains(Feature.CustomFilter) && filter == 3) {
            // Reapply stored filter data so it becomes active in the kernel.
            setCustomFilterShape(getCustomFilterShape())
        }
    }

    fun getDigitalFilter(): Int {
        return dac.getFeatureValue(Feature.DigitalFilter)
    }

    fun setSoundPreset(preset: Int) {
        if (dac_features.contains(Feature.SoundPreset))
            dac.setFeatureValue(Feature.SoundPreset, preset)
    }

    fun getSoundPreset(): Int {
        if (!dac_features.contains(Feature.SoundPreset)) return 0
        return dac.getFeatureValue(Feature.SoundPreset)
    }

    fun setLeftBalance(balance: Int) {
        dac.setFeatureValue(Feature.BalanceLeft, balance)
    }

    fun getLeftBalance(): Int {
        return dac.getFeatureValue(Feature.BalanceLeft)
    }

    fun setRightBalance(balance: Int) {
        dac.setFeatureValue(Feature.BalanceRight, balance)
    }

    fun getRightBalance(): Int {
        return dac.getFeatureValue(Feature.BalanceRight)
    }

    fun isEnabled(): Boolean {
        return dac.hifiDacState
    }

    fun setCustomFilterShape(shape: Int): Boolean {
        return dac.setCustomFilterShape(shape)
    }

    fun getCustomFilterShape(): Int {
        return dac.customFilterShape
    }

    fun setCustomFilterSymmetry(symmetry: Int): Boolean {
        return dac.setCustomFilterSymmetry(symmetry)
    }

    fun getCustomFilterSymmetry(): Int {
        return dac.customFilterSymmetry
    }

    fun setCustomFilterCoeff(coeffIndex: Int, coeff_val: Int): Boolean {
        return dac.setCustomFilterCoeff(coeffIndex, coeff_val)
    }

    fun getCustomFilterCoeff(coeffIndex: Int): Int {
        return dac.getCustomFilterCoeff(coeffIndex)
    }

    fun resetCustomFilterCoeffs() {
        dac.resetCustomFilterCoeffs()
    }
}
