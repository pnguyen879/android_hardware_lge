/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.dualscreen

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

internal object CoverNotifications {
    /** Newest notification of each app, newest first. */
    @Volatile
    var active: List<StatusBarNotification> = emptyList()

    /** Number of visible notifications per package. */
    @Volatile
    var counts: Map<String, Int> = emptyMap()

    @Volatile
    var onChanged: (() -> Unit)? = null
}

class CoverNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap) = refresh()

    override fun onListenerDisconnected() {
        CoverNotifications.active = emptyList()
        CoverNotifications.counts = emptyMap()
        CoverNotifications.onChanged?.invoke()
    }

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull() ?: emptyArray()
        val ranking = runCatching { currentRanking }.getOrNull()
        val visible =
            list.filter {
                val n = it.notification
                !it.isOngoing &&
                    (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                    n.smallIcon != null &&
                    n.visibility != Notification.VISIBILITY_SECRET &&
                    !isHidden(it, ranking)
            }
        CoverNotifications.counts = visible.groupingBy { it.packageName }.eachCount()
        CoverNotifications.active =
            visible.sortedByDescending { it.postTime }.distinctBy { it.packageName }
        CoverNotifications.onChanged?.invoke()
    }

    // Same rule as the status bar: hide MIN and NONE importance.
    private fun isHidden(sbn: StatusBarNotification, map: RankingMap?): Boolean {
        if (map == null) return false
        val r = Ranking()
        if (!map.getRanking(sbn.key, r)) return false
        return r.importance < NotificationManager.IMPORTANCE_LOW
    }
}
