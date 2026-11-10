package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.util.Base64
import android.view.Display
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
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
        val e = event ?: return
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = e.packageName?.toString().orEmpty()
            if (pkg.isNotBlank() && pkg != packageName) screenChangeListener?.invoke(pkg, e.className?.toString().orEmpty())
        }
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

        /** برای حالت «توضیح زنده صفحه»: هر بار پنجره عوض شد صدا زده می‌شود. */
        var screenChangeListener: ((packageName: String, className: String) -> Unit)? = null

        /** تایپ متن در کادر فعال (یا اولین کادر نوشتنی صفحه). submit=true یعنی بعدش دکمه ارسال را هم بزن. */
        fun typeText(text: String, submit: Boolean = false): Boolean {
            val root = instance?.rootInActiveWindow ?: return false
            val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
                ?: findFirstEditable(root) ?: return false
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (ok && submit) {
                val sendLabels = listOf("ارسال", "بفرست", "Send", "send")
                for (label in sendLabels) {
                    if (clickNodeWithText(label)) break
                }
            }
            return ok
        }

        private fun findFirstEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isEditable) return node
            for (i in 0 until node.childCount) {
                findFirstEditable(node.getChild(i))?.let { return it }
            }
            return null
        }

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

        /**
         * عکس صفحه فعلی گوشی (اندروید ۱۱ به بالا) به صورت JPEG base64 کوچک‌شده.
         * اگر سرویس فعال نباشد یا اندروید قدیمی باشد null برمی‌گرداند.
         */
        suspend fun captureScreenshotBase64(maxSide: Int = 1080): String? {
            val service = instance ?: return null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            return suspendCancellableCoroutine { cont ->
                try {
                    service.takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        service.mainExecutor,
                        object : AccessibilityService.TakeScreenshotCallback {
                            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                                val encoded = try {
                                    val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                                    result.hardwareBuffer.close()
                                    if (hw == null) null else {
                                        val soft = hw.copy(Bitmap.Config.ARGB_8888, false)
                                        hw.recycle()
                                        val scale = maxSide.toFloat() / maxOf(soft.width, soft.height)
                                        val scaled = if (scale < 1f) {
                                            Bitmap.createScaledBitmap(soft, (soft.width * scale).toInt(), (soft.height * scale).toInt(), true)
                                        } else soft
                                        val out = ByteArrayOutputStream()
                                        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
                                        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                                    }
                                } catch (_: Exception) { null }
                                if (cont.isActive) cont.resume(encoded)
                            }

                            override fun onFailure(errorCode: Int) {
                                if (cont.isActive) cont.resume(null)
                            }
                        }
                    )
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

        /** نام بسته برنامه‌ای که الان روی صفحه است. */
        fun currentPackageName(): String? = instance?.rootInActiveWindow?.packageName?.toString()

        fun getScreenContentHierarchy(): String {
            val service = instance ?: return "سرویس دسترسی‌پذیری فعال نیست. لطفاً آن را در تنظیمات فعال کنید."
            val root = service.rootInActiveWindow ?: return "صفحه فعال یافت نشد."

            val texts = mutableListOf<String>()
            extractNodeTexts(root, texts)

            return if (texts.isNotEmpty()) {
                texts.distinct().take(60).joinToString("\n")
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
                // اولویت با تطابق دقیق؛ اگر خود گره قابل کلیک نبود، والد قابل کلیک را بزن
                val exact = nodes.firstOrNull {
                    it.text?.toString()?.trim().equals(targetText.trim(), true) ||
                        it.contentDescription?.toString()?.trim().equals(targetText.trim(), true)
                } ?: nodes.first()
                var n: AccessibilityNodeInfo? = exact
                while (n != null && !n.isClickable) n = n.parent
                if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                // آخرین راه: زدن روی مرکز گره
                val rect = android.graphics.Rect()
                exact.getBoundsInScreen(rect)
                if (!rect.isEmpty) return clickCoordinates(rect.exactCenterX(), rect.exactCenterY())
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
