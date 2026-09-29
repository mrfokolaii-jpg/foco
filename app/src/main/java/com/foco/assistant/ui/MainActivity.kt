package com.foco.assistant.ui
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "فوکو فعاله ✅\nبه تنظیمات حرفه ای برو"
        tv.textSize = 22f
        tv.setPadding(40,200,40,40)
        setContentView(tv)
    }
}
