/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dac.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.lineageos.settings.device.dac.QuadDACPanelFragment
import org.lineageos.settings.device.dac.R
import org.lineageos.settings.device.dac.utils.QuadDAC

class ButtonPreference(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
    defStyleRes: Int,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private lateinit var reset_button: Button

    init {
        layoutResource = R.layout.button_preference
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

        reset_button = holder.findViewById(R.id.reset_coefficients) as Button
        reset_button.isClickable = true

        reset_button.setOnClickListener { resetCoefficients() }
    }

    private fun resetCoefficients() {
        try {
            QuadDAC.resetCustomFilterCoeffs()
        } catch (e: Exception) {}
        for (i in 0 until 14) {
            QuadDACPanelFragment.setCoeffSummary(context, i, 0)
        }
    }

    companion object {
        private const val TAG = "ButtonPreference"
    }
}
