package com.myjarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.text.Normalizer
import java.util.Locale

class JarvisService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var running = false
    private var unlockedUntil = 0L
    private var lockoutUntil = 0L
    private var fails = 0
    private var strikes = 0
    private val session = 120_000L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "jarvis").setContentTitle("Jarvis is listening")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (tts == null) tts = TextToSpeech(this) { tts?.language = Locale("hi", "IN") }
        if (!running) { running = true; listen() }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        h.removeCallbacksAndMessages(null)
        sr?.destroy(); tts?.shutdown()
        super.onDestroy()
    }

    private fun fix(s: String) =
        Normalizer.normalize(s, Normalizer.Form.NFC).replace("\u093C", "").lowercase()

    private fun say(s: String) { tts?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "j") }

    private fun listen() {
        if (!running) return
        sr?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { process(fix(it)) }
                h.postDelayed({ listen() }, 300)
            }
            override fun onError(e: Int) { h.postDelayed({ listen() }, 1000) }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        )
        sr = r
    }

    private fun process(t: String) {
        val now = SystemClock.elapsedRealtime()
        if (now >= unlockedUntil) {                       // locked: only "hey jarvis <passphrase>"
            val m = Regex("^(hey |हे )?(jarvis|जार्विस|जारविस|जरविस|जार्विश)\\s*(.*)$").find(t) ?: return
            if (now < lockoutUntil) return
            if (Store.check(this, m.groupValues[3])) {
                fails = 0; strikes = 0; unlockedUntil = now + session
                wakeScreen(); say("Unlocked")
            } else if (++fails >= 3) {
                fails = 0; strikes++
                lockoutUntil = now + minOf(60_000L * (1L shl (strikes - 1)), 3_600_000L)
                say("Too many attempts")
            }
            return
        }
        unlockedUntil = now + session
        try { run(t) } catch (e: Exception) { say("That failed") }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "jarvis:wake"
        ).acquire(10_000)
        startActivity(Intent(this, UnlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun go(i: Intent) = startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun run(t: String) {
        val a = JarvisAccessibility.instance
        val words = t.split(Regex("\\s+"))
        fun has(vararg k: String) = k.any { it in words }

        val hits = Store.contacts(this).mapNotNull { c ->
            c.names.map { fix(it) }.firstOrNull { it in t }?.let { c to it }
        }
        val contact = hits.singleOrNull()?.first
        val spoken = hits.singleOrNull()?.second ?: ""
        val msg = if (spoken.isEmpty()) "" else
            t.substringAfter(spoken).trim().replace(Regex("^(ko |को |message |msg |मैसेज |कि )+"), "").take(500)

        when {
            has("whatsapp", "व्हाट्सएप") ->
                if (contact == null || msg.isEmpty()) say("Say the contact and the message") else {
                    go(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/${contact.number.drop(1)}?text=${Uri.encode(msg)}"))
                        .setPackage("com.whatsapp"))
                    a?.sendWhenReady(msg)
                }
            has("instagram", "इंस्टाग्राम") ->
                if (contact == null || contact.ig.isEmpty() || msg.isEmpty()) say("Say the contact and the message") else {
                    go(Intent(Intent.ACTION_VIEW, Uri.parse("https://ig.me/m/${contact.ig}"))
                        .setPackage("com.instagram.android"))
                    a?.sendWhenReady(msg)
                }
            has("call", "कॉल") ->
                if (contact == null) say("Contact not found")
                else go(Intent(Intent.ACTION_CALL, Uri.parse("tel:${contact.number}")))
            "power off" in t || "switch off" in t || "shut down" in t || "फोन बंद" in t -> {
                say("Powering off")
                if (a == null) say("Turn on Jarvis in accessibility") else a.powerOff()
            }
            has("lock", "लॉक") -> {
                unlockedUntil = 0
                if (a == null) say("Turn on Jarvis in accessibility") else a.lockScreen()
            }
            has("open", "kholo", "खोलो") -> openApp(t)
            else -> say("Sorry, I did not understand")
        }
    }

    private fun openApp(t: String) {
        val pm = packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = apps.filter { it.loadLabel(pm).length > 2 && fix(it.loadLabel(pm).toString()) in t }
            .maxByOrNull { it.loadLabel(pm).length } ?: return say("App not found")
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return say("App not found")
        say("Opening ${hit.loadLabel(pm)}")
        go(i)
    }
}
