package com.example.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Lets FOKO read incoming notifications once the user grants "Notification access".
 * Keeps the latest notifications in memory so the agent can summarize them.
 */
class FokoNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val extras = n.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        synchronized(recent) {
            recent.add(0, RecentNotification(n.packageName, title, text, n.postTime))
            while (recent.size > MAX) recent.removeAt(recent.size - 1)
        }
    }

    data class RecentNotification(val packageName: String, val title: String, val text: String, val postTime: Long)

    companion object {
        private const val MAX = 50
        private val recent = mutableListOf<RecentNotification>()
        fun latest(): List<RecentNotification> = synchronized(recent) { recent.toList() }
    }
}
