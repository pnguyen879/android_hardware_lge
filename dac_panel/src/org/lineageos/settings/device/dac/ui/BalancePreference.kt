/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dac.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.Slider
import org.lineageos.settings.device.dac.R
import org.lineageos.settings.device.dac.utils.QuadDAC
import vendor.lge.hardware.dac.Feature

class BalancePreference(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
    defStyleRes: Int,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private var leftBalance = 0
    private var rightBalance = 0
    private var maxAllowedValue = 0
    private var minAllowedValue = -12

    private lateinit var leftSlider: Slider
    private lateinit var rightSlider: Slider
    private lateinit var leftValue: TextView
    private lateinit var rightValue: TextView

    init {
        layoutResource = R.layout.balance_preference
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : this(context, attrs, defStyleAttr, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context) : this(context, null)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.isClickable = false

        leftSlider = holder.findViewById(R.id.seek_left_balance) as Slider
        rightSlider = holder.findViewById(R.id.seek_right_balance) as Slider
        leftValue = holder.findViewById(R.id.tv_left_val) as TextView
        rightValue = holder.findViewById(R.id.tv_right_val) as TextView

        loadBalanceConfiguration()

        bindSlider(leftSlider, leftBalance, isLeft = true)
        bindSlider(rightSlider, rightBalance, isLeft = false)
    }

    private fun loadBalanceConfiguration() {
        try {
            val range = QuadDAC.getSupportedFeatureValues(Feature.BalanceLeft).range
            minAllowedValue = range.min.toInt()
            maxAllowedValue = range.max.toInt()
            leftBalance = -QuadDAC.getLeftBalance()
            rightBalance = -QuadDAC.getRightBalance()
        } catch (e: Exception) {
            Log.d(TAG, "loadBalanceConfiguration: $e")
        }

        updateValueLabel(isLeft = true, leftBalance)
        updateValueLabel(isLeft = false, rightBalance)
    }

    private fun bindSlider(slider: Slider, current: Int, isLeft: Boolean) {
        slider.clearOnChangeListeners()
        // Expand bounds first so DAC ranges that exclude zero remain valid during setup.
        slider.stepSize = 0f
        slider.valueFrom = minOf(minAllowedValue, 0).toFloat()
        slider.valueTo = maxOf(maxAllowedValue, 1).toFloat()
        slider.value = current.toFloat().coerceIn(
            minAllowedValue.toFloat(),
            maxAllowedValue.toFloat(),
        )
        slider.valueFrom = minAllowedValue.toFloat()
        slider.valueTo = maxAllowedValue.toFloat()
        slider.stepSize = 1f
        slider.isEnabled = isEnabled
        slider.addOnChangeListener { _, newValue, fromUser ->
            val value = newValue.toInt()
            if (isLeft) {
                leftBalance = value
                updateValueLabel(isLeft = true, value)
            } else {
                rightBalance = value
                updateValueLabel(isLeft = false, value)
            }
            if (fromUser) {
                try {
                    if (isLeft) QuadDAC.setLeftBalance(-value)
                    else QuadDAC.setRightBalance(-value)
                } catch (e: Exception) {
                    Log.d(TAG, "update${if (isLeft) "Left" else "Right"}Balance: $e")
                }
            }
        }
    }

    private fun updateValueLabel(isLeft: Boolean, value: Int) {
        val label = context.getString(R.string.balance_value_db, value / 2.0)
        if (isLeft) leftValue.text = label else rightValue.text = label
    }

    companion object {
        private const val TAG = "BalancePreference"
    }
}
