package com.example.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.example.data.local.ApiKeyStore
import java.util.Locale

/**
 * کنترل مرکزی صدای فوکو.
 *
 * پیش از این، چت، ویجت شناور و کارت تنظیمات هر کدام موتور صدای جداگانه‌ی خودشان را
 * می‌ساختند و فقط صدا را «یک‌بار، هنگام ساخته‌شدن» می‌خواندند. نتیجه این بود که با
 * عوض‌کردن صدا در تنظیمات، صدای چت و ویجت تغییر نمی‌کرد و انتخاب کاربر بی‌اثر می‌ماند.
 *
 * این کلاس سه کار می‌کند:
 *  • `applySettings` — زبان فارسی، صدای ذخیره‌شده، زیر‌و‌بمی و سرعت را روی موتور اعمال می‌کند.
 *    اگر نام صدای ذخیره‌شده دیگر روی گوشی نباشد، خودش اولین صدای فارسی موجود را می‌گذارد.
 *  • `register` / `notifyChanged` — هر وقت کاربر صدا را عوض کرد، همه‌ی بخش‌های زنده
 *    (چت و ویجت شناور) بلافاصله به‌روز می‌شوند و دیگر لازم نیست اپ restart شود.
 *  • `speak` — متن را با آماده‌سازی فارسی (`SpeechText`) می‌خواند.
 */
object FokoVoiceCtl {

    /** شناسه‌ی پیش‌فرض؛ اندروید از آن برای تشخیص صدای فارسی استفاده می‌کند. */
    const val PERSIAN_TAG = "fa-IR"

    /** زبان‌هایی که برای فارسی امتحان می‌کنیم، بهترین اول. */
    private val PERSIAN_LOCALES = listOf(
        Locale("fa", "IR"),
        Locale("fa"),
        Locale("fa", "AF")
    )

    private val listeners = mutableListOf<() -> Unit>()

    // -------------------------------------------------------------- listeners ----

    /** چت و ویجت شناور اینجا ثبت می‌کنند تا با تغییر صدا بلافاصله به‌روز شوند. */
    fun register(listener: () -> Unit) {
        synchronized(listeners) { if (!listeners.contains(listener)) listeners.add(listener) }
    }

    fun unregister(listener: () -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    /** بعد از هر تغییر صدا در تنظیمات، صدا زده می‌شود. */
    fun notifyChanged() {
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { runCatching { it() } }
    }

    // ----------------------------------------------------------------- voices ----

    fun isPersian(voice: Voice): Boolean = try {
        val lang = voice.locale.language.lowercase(Locale.ROOT)
        val country = voice.locale.country.uppercase(Locale.ROOT)
        lang == "fa" || country == "IR"
    } catch (_: Exception) {
        false
    }

    /**
     * نشانه‌های احتمالی صدای مرد در نام صدا.
     * اندروید فیلدی برای جنسیت صدا ندارد، پس فقط از روی نام می‌شود حدس زد.
     */
    private val MALE_HINTS = listOf("male", "man", "m1", "m2", "m3")

    private fun looksMale(voice: Voice): Boolean = try {
        val n = voice.name.lowercase(Locale.ROOT)
        MALE_HINTS.any { n.contains(it) }
    } catch (_: Exception) {
        false
    }

    /** بعضی صداها فقط در فهرست موتور هستند ولی داده‌شان نصب نشده و کار نمی‌کنند. */
    private fun isInstalled(voice: Voice): Boolean = try {
        voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
    } catch (_: Exception) {
        true
    }

    private fun needsNetwork(voice: Voice): Boolean = try {
        voice.isNetworkConnectionRequired
    } catch (_: Exception) {
        false
    }

    private fun qualityOf(voice: Voice): Int = try {
        voice.quality
    } catch (_: Exception) {
        0
    }

    /**
     * همه‌ی صداهای گوشی، به‌ترتیبی که «بهترین گزینهٔ قابل‌استفاده» اول بیفتد:
     * فارسی → نصب‌شده → صدای مرد → آفلاین → کیفیت بالاتر → زبان و نام.
     *
     * پیش‌تر فقط فارسی/زبان/نام مرتب می‌شد و نتیجه این بود که در گوشی‌ای بدون صدای فارسی،
     * فهرست با صدای عربی شروع می‌شد و بقیه هم داده‌شان نصب نبود.
     */
    fun voicesOf(engine: TextToSpeech?): List<Voice> {
        val all = try {
            engine?.voices?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        return all.sortedWith(
            compareBy<Voice>(
                { if (isPersian(it)) 0 else 1 },
                { if (isInstalled(it)) 0 else 1 },
                { if (looksMale(it)) 0 else 1 },
                { if (needsNetwork(it)) 1 else 0 },
                { -qualityOf(it) },
                { it.locale.language },
                { it.name }
            )
        )
    }

    fun persianVoices(engine: TextToSpeech?): List<Voice> = voicesOf(engine).filter { isPersian(it) }

    /** بهترین صدای فارسیِ قابل‌استفاده، یا null اگر گوشی هیچ صدای فارسی نداشته باشد. */
    fun bestPersian(engine: TextToSpeech?): Voice? = persianVoices(engine).firstOrNull()

    // --------------------------------------------------------------- settings ----

    /**
     * زبان، صدا، زیر‌و‌بمی و سرعت را روی موتور اعمال می‌کند. هر مرحله اختیاری است و
     * صدا زدن چندباره‌اش بی‌خطر است.
     */
    fun applySettings(context: Context, engine: TextToSpeech?) {
        val e = engine ?: return
        SpeechText.init(context)
        val store = ApiKeyStore(context)
        try {
            // ۱. زبان فارسی — اولین زبانی که گوشی پشتیبانی کند.
            var persianSupported = false
            for (locale in PERSIAN_LOCALES) {
                val result = try {
                    e.setLanguage(locale)
                } catch (_: Exception) {
                    TextToSpeech.LANG_NOT_SUPPORTED
                }
                if (result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    persianSupported = true
                    break
                }
            }

            // ۲. صدای انتخاب‌شده؛ اگر پیدا نشد یا فارسی نبود، بهترین صدای فارسی موجود.
            val available = voicesOf(e)
            val wanted = store.getTtsVoice()
            var chosen = if (wanted.isNotBlank()) available.firstOrNull { it.name == wanted } else null
            if (chosen == null || !isPersian(chosen)) {
                available.firstOrNull { isPersian(it) }?.let { chosen = it }
            }
            if (chosen != null) {
                runCatching { e.voice = chosen }
                // اولین باری که صدای فارسی پیدا می‌شود، همان را ذخیره کن تا ثابت بماند.
                if (wanted.isBlank() && isPersian(chosen)) {
                    runCatching { store.setTtsVoice(chosen.name) }
                }
            }

            // ۳. زیر‌و‌بمی و سرعت.
            e.setPitch(store.getTtsPitch())
            e.setSpeechRate(store.getTtsRate())

            // اگر هیچ صدای فارسی روی گوشی نبود، صدا را عوض نمی‌کنیم تا کاربر خودش نصب کند.
            if (!persianSupported && available.isEmpty()) {
                runCatching { e.setLanguage(Locale.getDefault()) }
            }
        } catch (_: Exception) {
        }
    }

    /** آخرین وضعیت را ذخیره می‌کند و همه‌ی بخش‌های زنده را خبر می‌کند. */
    fun persistAndNotify(context: Context, voiceName: String?, pitch: Float?, rate: Float?) {
        val store = ApiKeyStore(context)
        voiceName?.let { store.setTtsVoice(it) }
        pitch?.let { store.setTtsPitch(it) }
        rate?.let { store.setTtsRate(it) }
        notifyChanged()
    }

    // ------------------------------------------------------------------ speak ----

    /**
     * خواندن متن با آماده‌سازی فارسی و بلندترین حجم ممکن.
     * بعضی موتورها حجم پیش‌فرض را کم می‌گیرند؛ `KEY_PARAM_VOLUME = 1.0` آن را جبران می‌کند.
     */
    fun speak(engine: TextToSpeech?, text: String, utteranceId: String = "foko_speech") {
        val e = engine ?: return
        val spoken = SpeechText.forSpeech(text)
        if (spoken.isBlank()) return
        val params = android.os.Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        runCatching { e.speak(spoken, TextToSpeech.QUEUE_FLUSH, params, utteranceId) }
    }

    fun stop(engine: TextToSpeech?) {
        runCatching { engine?.stop() }
    }

    /**
     * صفحه‌ی تنظیمات صدای اندروید را باز می‌کند تا کاربر بتواند موتور فارسی را نصب کند.
     * روی بعضی گوشی‌ها این صفحه وجود ندارد؛ در آن صورت تنظیمات کلی باز می‌شود.
     */
    fun openSystemTtsSettings(context: Context) {
        val candidates = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }
}
