/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.dualscreen

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

internal object CoverNotifications {
    @Volatile
    var active: List<StatusBarNotification> = emptyList()

    @Volatile
    var onChanged: (() -> Unit)? = null
}

class CoverNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    override fun onListenerDisconnected() {
        CoverNotifications.active = emptyList()
        CoverNotifications.onChanged?.invoke()
    }

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull() ?: emptyArray()
        CoverNotifications.active = list
            .filter {
                !it.isOngoing &&
                    (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                    it.notification.smallIcon != null
            }
            .sortedByDescending { it.postTime }
            .distinctBy { it.packageName }
        CoverNotifications.onChanged?.invoke()
    }
}
