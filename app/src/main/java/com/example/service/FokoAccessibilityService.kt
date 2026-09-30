package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FokoAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Track screen updates
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    companion object {
        var instance: FokoAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null

        /** Checks Android's actual enabled-service setting, including after process restart. */
        fun isEnabledInSystem(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val expected = ComponentName(context, FokoAccessibilityService::class.java).flattenToString()
            return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        fun performBack(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        }

        fun performHome(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false
        }

        fun performRecents(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) ?: false
        }

        fun performNotifications(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false
        }

        fun getScreenContentHierarchy(): String {
            val service = instance ?: return "سرویس دسترسی‌پذیری فعال نیست. لطفاً آن را در تنظیمات فعال کنید."
            val root = service.rootInActiveWindow ?: return "صفحه فعال یافت نشد."

            val texts = mutableListOf<String>()
            extractNodeTexts(root, texts)

            return if (texts.isNotEmpty()) {
                texts.distinct().take(30).joinToString("\n")
            } else {
                "متنی در صفحه جاری یافت نشد."
            }
        }

        private fun extractNodeTexts(node: AccessibilityNodeInfo?, result: MutableList<String>) {
            if (node == null) return

            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()

            if (!text.isNullOrBlank() && text.length > 1) {
                result.add(text)
            } else if (!desc.isNullOrBlank() && desc.length > 1) {
                result.add("[$desc]")
            }

            for (i in 0 until node.childCount) {
                extractNodeTexts(node.getChild(i), result)
            }
        }

        fun performScrollDown(): Boolean {
            val root = instance?.rootInActiveWindow ?: return false
            return root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }

        fun performScrollUp(): Boolean {
            val root = instance?.rootInActiveWindow ?: return false
            return root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        }

        fun clickNodeWithText(targetText: String): Boolean {
            val root = instance?.rootInActiveWindow ?: return false
            val nodes = root.findAccessibilityNodeInfosByText(targetText)
            if (!nodes.isNullOrEmpty()) {
                return nodes.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            return false
        }

        fun clickCoordinates(x: Float, y: Float): Boolean {
            val service = instance ?: return false
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return service.dispatchGesture(gesture, null, null)
        }
    }
}
