/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dac

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.lineageos.settings.device.dac.utils.QuadDAC

class QuadDACTileService : TileService() {

    private val headsetPluggedTileReceiver = HeadsetPluggedTileReceiver()

    private val DacControlInterface = QuadDAC

    override fun onClick() {
        super.onClick()
        try {
            if (DacControlInterface.isEnabled()) {
                DacControlInterface.disable()
                setTileInactive()
            } else {
                DacControlInterface.enable()
                setTileActive()
            }
        } catch (e: Exception) {}
    }

    override fun onStartListening() {
        super.onStartListening()
        try {
            DacControlInterface.initialize()
        } catch (e: Exception) {}
        val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        registerReceiver(headsetPluggedTileReceiver, filter)
    }

    override fun onStopListening() {
        super.onStopListening()
        unregisterReceiver(headsetPluggedTileReceiver)
    }

    private fun setTileActive() {
        val quaddactile = qsTile
        quaddactile.state = Tile.STATE_ACTIVE
        quaddactile.label = resources.getString(R.string.sp_hifi_quad_mode_title)
        quaddactile.updateTile()
    }

    private fun setTileInactive() {
        val quaddactile = qsTile
        quaddactile.state = Tile.STATE_INACTIVE
        quaddactile.label = resources.getString(R.string.sp_hifi_quad_mode_title)
        quaddactile.updateTile()
    }

    private fun setTileUnavailable() {
        val quaddactile = qsTile
        quaddactile.state = Tile.STATE_UNAVAILABLE
        quaddactile.label = resources.getString(R.string.quad_dac_unavail)
        quaddactile.updateTile()
    }

    private inner class HeadsetPluggedTileReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (!DacControlInterface.dac_service_available) {
                setTileUnavailable()
                return
            }
            if (intent.action == Intent.ACTION_HEADSET_PLUG) {
                val state = intent.getIntExtra("state", -1)
                when (state) {
                    1 -> {
                        try {
                            if (DacControlInterface.isEnabled()) {
                                setTileActive()
                            } else {
                                setTileInactive()
                            }
                        } catch (e: Exception) {}
                    }
                    0 -> {
                        setTileUnavailable()
                    }
                    else -> {}
                }
            }
        }
    }

    companion object {
        private const val TAG = "QuadDACTileService"
    }
}
