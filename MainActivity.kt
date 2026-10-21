package com.myjarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        fun label(s: String, size: Float = 16f) = TextView(this).apply {
            text = s; textSize = size; setPadding(0, pad / 2, 0, 0)
        }.also { box.addView(it) }
        fun button(s: String, f: () -> Unit) = Button(this).apply {
            text = s; setOnClickListener { f() }
        }.also { box.addView(it) }

        label("Jarvis", 28f)
        label("Spoken passphrase (3+ words). You say: \"hey jarvis <passphrase>\" to unlock.")
        val pp = EditText(this).apply {
            hint = "blue river morning"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }.also { box.addView(it) }

        label("Contacts, one per line: name|spoken alias, +number, instagram username")
        val ct = EditText(this).apply {
            hint = "rahul|राहुल, +919999999999, rahul_insta"
            minLines = 4; gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }.also { box.addView(it) }

        button("Save passphrase and contacts") {
            try {
                Store.save(this, pp.text.toString(), ct.text.toString())
                pp.setText("")
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "Error", Toast.LENGTH_LONG).show()
            }
        }

        label("Then do these four steps in order:")
        button("1. Allow microphone, calls, notifications") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
                Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        button("2. Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        button("3. Turn on Jarvis in Accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("4. Start Jarvis") {
            startForegroundService(Intent(this, JarvisService::class.java))
        }
        button("Stop Jarvis") { stopService(Intent(this, JarvisService::class.java)) }

        setContentView(ScrollView(this).apply { addView(box) })
    }
}
