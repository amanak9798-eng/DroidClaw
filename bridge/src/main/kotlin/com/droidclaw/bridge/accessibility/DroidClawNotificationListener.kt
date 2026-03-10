package com.droidclaw.bridge.accessibility

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class DroidClawNotificationListener : NotificationListenerService() {
    companion object {
        @Volatile
        var instance: DroidClawNotificationListener? = null
            private set

        private val notifications = mutableListOf<StatusBarNotification>()

        fun getActiveNotifications(): List<StatusBarNotification> = synchronized(notifications) {
            notifications.toList()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        synchronized(notifications) {
            notifications.clear()
            activeNotifications?.let { notifications.addAll(it) }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        synchronized(notifications) { notifications.clear() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        synchronized(notifications) {
            notifications.removeAll { it.key == sbn.key }
            notifications.add(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        synchronized(notifications) {
            notifications.removeAll { it.key == sbn.key }
        }
    }
}
