/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dac.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.lineageos.settings.device.dac.R
import org.lineageos.settings.device.dac.utils.QuadDAC
import vendor.lge.hardware.dac.Feature
import vendor.lge.hardware.dac.FeatureStates

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

    private lateinit var leftSlider: SeekBar
    private lateinit var rightSlider: SeekBar
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

        leftSlider = holder.findViewById(R.id.seek_left_balance) as SeekBar
        rightSlider = holder.findViewById(R.id.seek_right_balance) as SeekBar
        leftValue = holder.findViewById(R.id.tv_left_val) as TextView
        rightValue = holder.findViewById(R.id.tv_right_val) as TextView

        loadBalanceConfiguration()

        leftSlider.setOnSeekBarChangeListener(balanceListener(isLeft = true))
        rightSlider.setOnSeekBarChangeListener(balanceListener(isLeft = false))
    }

    private fun loadBalanceConfiguration() {
        try {
            val states: FeatureStates = QuadDAC.getSupportedFeatureValues(Feature.BalanceLeft)
            minAllowedValue = states.range.min.toInt()
            maxAllowedValue = states.range.max.toInt()
            leftBalance = -QuadDAC.getLeftBalance()
            rightBalance = -QuadDAC.getRightBalance()
        } catch (e: Exception) {
            Log.d(TAG, "loadBalanceConfiguration: $e")
        }

        val range = (maxAllowedValue - minAllowedValue).coerceAtLeast(0)
        leftSlider.max = range
        rightSlider.max = range
        leftSlider.progress = (leftBalance - minAllowedValue).coerceIn(0, range)
        rightSlider.progress = (rightBalance - minAllowedValue).coerceIn(0, range)
        updateValueLabel(isLeft = true, leftBalance)
        updateValueLabel(isLeft = false, rightBalance)
    }

    private fun balanceListener(isLeft: Boolean) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val value = minAllowedValue + progress
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

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        }

    private fun updateValueLabel(isLeft: Boolean, value: Int) {
        val label = context.getString(R.string.balance_value_db, value / 2.0)
        if (isLeft) leftValue.text = label else rightValue.text = label
    }

    companion object {
        private const val TAG = "BalancePreference"
    }
}
