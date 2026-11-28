/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dac

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.android.settingslib.widget.SliderPreference
import org.lineageos.settings.device.dac.ui.BalancePreference
import org.lineageos.settings.device.dac.ui.ButtonPreference
import org.lineageos.settings.device.dac.utils.Constants
import org.lineageos.settings.device.dac.utils.QuadDAC
import vendor.lge.hardware.dac.Feature
import vendor.lge.hardware.dac.IDac
import vendor.lge.hardware.dac.Range

class QuadDACPanelFragment : SettingsBasePreferenceFragment(), OnPreferenceChangeListener {

    private val DacControlInterface = QuadDAC

    private lateinit var quaddac_switch: SwitchPreferenceCompat
    private lateinit var sound_preset_list: ListPreference
    private lateinit var digital_filter_list: ListPreference
    private lateinit var mode_list: ListPreference
    private lateinit var balance_preference: BalancePreference
    private lateinit var avc_volume: SliderPreference

    // Custom-filter controls.
    private lateinit var custom_filter_shape: ListPreference
    private lateinit var custom_filter_symmetry: ListPreference
    private lateinit var custom_filter_settings_category: PreferenceCategory
    private lateinit var custom_filter_coeffs_category: PreferenceCategory

    private lateinit var custom_filter_reset_coeffs_button: ButtonPreference

    private lateinit var headsetPluggedFragmentReceiver: HeadsetPluggedFragmentReceiver

    private var dac: IDac? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        headsetPluggedFragmentReceiver = HeadsetPluggedFragmentReceiver()
        try {
            DacControlInterface.initialize()
        } catch (e: Exception) {
            Log.d(TAG, "onCreatePreferences: " + e.toString())
        }
        addPreferencesFromResource(R.xml.quaddac_panel)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {

        try {
            if (preference is SwitchPreferenceCompat) {

                val set_dac_on = newValue as Boolean
                if (set_dac_on) {
                    DacControlInterface.enable()
                    enableExtraSettings()
                    return true
                } else {
                    DacControlInterface.disable()
                    disableExtraSettings()
                    return true
                }
            }
            if (preference is ListPreference) {
                if (preference.key == Constants.HIFI_MODE_KEY) {
                    val lp = preference

                    val mode = lp.findIndexOfValue(newValue as String)
                    DacControlInterface.setDACMode(mode)
                    return true
                } else if (preference.key == Constants.DIGITAL_FILTER_KEY) {
                    val lp = preference

                    val digital_filter = lp.findIndexOfValue(newValue as String)
                    DacControlInterface.setDigitalFilter(digital_filter)
                    updateDigitalFilterSummary(digital_filter)

                    // Digital-filter index 3 selects the custom filter.
                    if (
                        DacControlInterface.getSupportedFeatures().contains(Feature.CustomFilter) &&
                            digital_filter == 3
                    )
                        enableCustomFilter()
                    else disableCustomFilter()

                    return true
                } else if (preference.key == Constants.SOUND_PRESET_KEY) {
                    val lp = preference

                    val sound_preset = lp.findIndexOfValue(newValue as String)
                    DacControlInterface.setSoundPreset(sound_preset)
                    return true
                } else if (preference.key == Constants.CUSTOM_FILTER_SHAPE_KEY) {
                    val lp = preference

                    val filter_shape = lp.findIndexOfValue(newValue as String)
                    DacControlInterface.setCustomFilterShape(filter_shape)
                    return true
                } else if (preference.key == Constants.CUSTOM_FILTER_SYMMETRY_KEY) {
                    val lp = preference

                    val filter_symmetry = lp.findIndexOfValue(newValue as String)
                    DacControlInterface.setCustomFilterSymmetry(filter_symmetry)
                    return true
                }
                return false
            }

            if (preference is SliderPreference) {
                if (preference.key == Constants.AVC_VOLUME_KEY) {
                    if (newValue is Int) {
                        val avc_vol: Int = newValue

                        DacControlInterface.setAVCVolume(avc_vol)
                        return true
                    } else {
                        return false
                    }
                } else {
                    for (i in 0 until 14) {
                        if (preference.key == Constants.CUSTOM_FILTER_COEFF_KEYS[i]) {
                            if (newValue is Int) {
                                val coeffVal: Int = newValue

                                setCoeffSummary(requireContext(), i, coeffVal)

                                DacControlInterface.setCustomFilterCoeff(i, coeffVal)
                                return true
                            } else return false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "onPreferenceChange: " + e.toString())
        }
        return false
    }

    override fun onResume() {
        val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        requireActivity().registerReceiver(headsetPluggedFragmentReceiver, filter)
        super.onResume()
    }

    override fun onPause() {
        requireActivity().unregisterReceiver(headsetPluggedFragmentReceiver)
        super.onPause()
    }

    override fun addPreferencesFromResource(preferencesResId: Int) {
        super.addPreferencesFromResource(preferencesResId)
        val am = requireContext().getSystemService(AudioManager::class.java)

        quaddac_switch = findPreference<SwitchPreferenceCompat>(Constants.DAC_SWITCH_KEY)!!
        quaddac_switch.onPreferenceChangeListener = this

        sound_preset_list = findPreference<ListPreference>(Constants.SOUND_PRESET_KEY)!!
        sound_preset_list.onPreferenceChangeListener = this

        digital_filter_list = findPreference<ListPreference>(Constants.DIGITAL_FILTER_KEY)!!
        digital_filter_list.onPreferenceChangeListener = this

        custom_filter_shape = findPreference<ListPreference>(Constants.CUSTOM_FILTER_SHAPE_KEY)!!
        custom_filter_shape.onPreferenceChangeListener = this

        custom_filter_settings_category =
            findPreference<PreferenceCategory>(Constants.CUSTOM_FILTER_SETTINGS_CATEGORY_KEY)!!
        custom_filter_coeffs_category =
            findPreference<PreferenceCategory>(Constants.CUSTOM_FILTER_COEFFS_CATEGORY_KEY)!!

        custom_filter_symmetry =
            findPreference<ListPreference>(Constants.CUSTOM_FILTER_SYMMETRY_KEY)!!
        custom_filter_symmetry.onPreferenceChangeListener = this

        custom_filter_reset_coeffs_button =
            findPreference<ButtonPreference>(Constants.RESET_COEFFICIENTS_KEY)!!
        custom_filter_reset_coeffs_button.onPreferenceChangeListener = this

        for (i in 0 until 14) {
            custom_filter_coeffs[i] =
                findPreference<SliderPreference>(Constants.CUSTOM_FILTER_COEFF_KEYS[i])
            custom_filter_coeffs[i]!!.onPreferenceChangeListener = this
        }

        mode_list = findPreference<ListPreference>(Constants.HIFI_MODE_KEY)!!
        mode_list.onPreferenceChangeListener = this

        avc_volume = findPreference<SliderPreference>(Constants.AVC_VOLUME_KEY)!!
        avc_volume.onPreferenceChangeListener = this

        balance_preference = findPreference<BalancePreference>(Constants.BALANCE_KEY)!!

        try {
            if (DacControlInterface.getSupportedFeatures().contains(Feature.QuadDAC)) {
                quaddac_switch.isVisible = true
            }
            if (DacControlInterface.getSupportedFeatures().contains(Feature.SoundPreset)) {
                sound_preset_list.isVisible = true
                sound_preset_list.setValueIndex(DacControlInterface.getSoundPreset())
            }
            if (DacControlInterface.getSupportedFeatures().contains(Feature.DigitalFilter)) {
                digital_filter_list.isVisible = true
                val digitalFilter = DacControlInterface.getDigitalFilter()
                digital_filter_list.setValueIndex(digitalFilter)
                updateDigitalFilterSummary(digitalFilter)
            }
            if (
                DacControlInterface.getSupportedFeatures().contains(Feature.BalanceLeft) &&
                    DacControlInterface.getSupportedFeatures().contains(Feature.BalanceRight)
            ) {
                balance_preference.isVisible = true
            }
            if (DacControlInterface.getSupportedFeatures().contains(Feature.AVCVolume)) {
                avc_volume.isVisible = true
                val range: Range =
                    DacControlInterface.getSupportedFeatureValues(Feature.AVCVolume).range
                avc_volume.min = range.min.toInt()
                avc_volume.max = range.max.toInt()
                avc_volume.value = DacControlInterface.getAVCVolume()
            }
            if (DacControlInterface.getSupportedFeatures().contains(Feature.HifiMode)) {
                mode_list.isVisible = true
                mode_list.setValueIndex(DacControlInterface.getDACMode())
            }
            if (DacControlInterface.getSupportedFeatures().contains(Feature.CustomFilter)) {
                custom_filter_shape.setValueIndex(DacControlInterface.getCustomFilterShape())
                custom_filter_symmetry.setValueIndex(DacControlInterface.getCustomFilterSymmetry())
                for (i in 0 until 14) {
                    custom_filter_coeffs[i]!!.value = DacControlInterface.getCustomFilterCoeff(i)
                    setCoeffSummary(
                        requireContext(),
                        i,
                        DacControlInterface.getCustomFilterCoeff(i),
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "addPreferencesFromResource: " + e.toString())
        }

        try {
            if (am.isWiredHeadsetOn) {
                quaddac_switch.isEnabled = true
                if (DacControlInterface.isEnabled()) {
                    quaddac_switch.isChecked = true
                    enableExtraSettings()
                } else {
                    quaddac_switch.isChecked = false
                    disableExtraSettings()
                }
            } else {
                quaddac_switch.isEnabled = false
                disableExtraSettings()
                if (DacControlInterface.isEnabled()) {
                    quaddac_switch.isChecked = true
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "addPreferencesFromResource2: " + e.toString())
        }
    }

    private fun enableExtraSettings() {
        val supportedFeatures: ArrayList<Int> = DacControlInterface.getSupportedFeatures()
        digital_filter_list.isEnabled = true
        mode_list.isEnabled = true
        avc_volume.isEnabled = true
        balance_preference.isEnabled = true
        if (supportedFeatures.contains(Feature.SoundPreset)) sound_preset_list.isEnabled = true
        if (supportedFeatures.contains(Feature.CustomFilter)) enableCustomFilter()
    }

    private fun disableExtraSettings() {
        digital_filter_list.isEnabled = false
        mode_list.isEnabled = false
        avc_volume.isEnabled = false
        balance_preference.isEnabled = false
        sound_preset_list.isEnabled = false
        disableCustomFilter()
    }

    private fun updateDigitalFilterSummary(filter: Int) {
        digital_filter_list.summary =
            when (filter) {
                0 -> getString(R.string.sp_hifi_digital_filter_short_summary)
                1 -> getString(R.string.sp_hifi_digital_filter_sharp_summary)
                2 -> getString(R.string.sp_hifi_digital_filter_slow_summary)
                else -> getString(R.string.sp_hifi_digital_filter_description)
            }
    }

    private fun enableCustomFilter() {
        checkCustomFilterVisibility()
        custom_filter_shape.isEnabled = true
        custom_filter_symmetry.isEnabled = true
        for (i in 0 until 14) {
            custom_filter_coeffs[i]!!.isEnabled = true
        }

        custom_filter_reset_coeffs_button.isEnabled = true

        try {
            // Reapplying the shape loads stored coefficients into the active filter.
            DacControlInterface.setCustomFilterShape(DacControlInterface.getCustomFilterShape())
        } catch (e: Exception) {}
    }

    private fun disableCustomFilter() {
        checkCustomFilterVisibility()
        custom_filter_shape.isEnabled = false
        custom_filter_symmetry.isEnabled = false
        for (i in 0 until 14) custom_filter_coeffs[i]!!.isEnabled = false

        custom_filter_reset_coeffs_button.isEnabled = false
    }

    private fun checkCustomFilterVisibility() {
        try {
            val showCustomFilter =
                DacControlInterface.getSupportedFeatures().contains(Feature.CustomFilter) &&
                    DacControlInterface.getDigitalFilter() == 3
            custom_filter_settings_category.isVisible = showCustomFilter
            custom_filter_coeffs_category.isVisible = showCustomFilter
            if (showCustomFilter) {
                custom_filter_shape.isVisible = true
                custom_filter_symmetry.isVisible = true
                for (i in 0 until 14) custom_filter_coeffs[i]!!.isVisible = true
            } else {
                custom_filter_shape.isVisible = false
                custom_filter_symmetry.isVisible = false
                for (i in 0 until 14) custom_filter_coeffs[i]!!.isVisible = false
            }
        } catch (e: Exception) {}
    }

    private inner class HeadsetPluggedFragmentReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_HEADSET_PLUG) {
                val state = intent.getIntExtra("state", -1)
                when (state) {
                    1 -> {
                        quaddac_switch.isEnabled = true
                        if (quaddac_switch.isChecked) enableExtraSettings()
                    }
                    0 -> {
                        quaddac_switch.isEnabled = false
                        disableExtraSettings()
                    }
                    else -> {}
                }
            }
        }
    }

    fun getDhc(): IDac? {
        return dac
    }

    companion object {
        private const val TAG = "QuadDACPanelFragment"

        // Coefficient order must match the ES9218 kernel interface.
        private val custom_filter_coeffs = arrayOfNulls<SliderPreference>(14)

        fun setCoeffSummary(context: Context, index: Int, value: Int) {
            custom_filter_coeffs[index]!!.value = value
            custom_filter_coeffs[index]!!.summary =
                context.getString(R.string.coefficient_summary, index, value)
        }
    }
}
