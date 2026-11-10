package com.example.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Lets FOKO read incoming notifications once the user grants "Notification access".
 * Keeps the latest notifications in memory so the agent can summarize them, read them
 * aloud, and reply through the notification's own reply button (RemoteInput).
 */
class FokoNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        appContext = applicationContext
    }

    override fun onListenerDisconnected() {
        connected = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName == packageName) return
        val extras = n.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val replyAction = findReplyAction(n.notification)
        synchronized(recent) {
            recent.removeAll { it.key == n.key && it.text == text }
            recent.add(0, RecentNotification(n.key, n.packageName, title, text, n.postTime))
            while (recent.size > MAX) recent.removeAt(recent.size - 1)
            if (replyAction != null) replyActions[n.key] = replyAction else replyActions.remove(n.key)
            replyActions.keys.retainAll(recent.map { it.key }.toSet())
        }
        listener?.invoke(RecentNotification(n.key, n.packageName, title, text, n.postTime))
    }

    private fun findReplyAction(notification: Notification?): Notification.Action? {
        val actions = notification?.actions ?: return null
        return actions.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
    }

    data class RecentNotification(
        val key: String,
        val packageName: String,
        val title: String,
        val text: String,
        val postTime: Long
    )

    companion object {
        private const val MAX = 50
        private val recent = mutableListOf<RecentNotification>()
        private val replyActions = mutableMapOf<String, Notification.Action>()
        @Volatile private var connected = false
        private var appContext: Context? = null

        /** برای خواندن خودکار اعلان‌های تازه (مثلاً در حالت مکالمه). */
        var listener: ((RecentNotification) -> Unit)? = null

        fun isConnected(): Boolean = connected
        fun latest(): List<RecentNotification> = synchronized(recent) { recent.toList() }
        fun canReply(key: String): Boolean = synchronized(recent) { replyActions.containsKey(key) }

        fun appLabel(pkg: String): String {
            val ctx = appContext ?: return pkg
            return try {
                val pm = ctx.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) { pkg }
        }

        /** جواب دادن از طریق دکمه «پاسخ» خود اعلان. */
        fun reply(context: Context, key: String, text: String): Boolean {
            val action = synchronized(recent) { replyActions[key] } ?: return false
            val inputs = action.remoteInputs ?: return false
            return try {
                val intent = Intent()
                val results = Bundle()
                inputs.forEach { results.putCharSequence(it.resultKey, text) }
                RemoteInput.addResultsToIntent(inputs, intent, results)
                action.actionIntent.send(context, 0, intent)
                true
            } catch (_: Exception) { false }
        }
    }
}
