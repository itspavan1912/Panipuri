package com.myjarvis

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle

// Wakes the screen and asks Android to dismiss the lock screen.
// With a PIN/pattern/fingerprint, Android still asks you to authenticate
// unless Smart Lock (trusted place/device) already has the phone unlocked.
class UnlockActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val done = object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { finish() }
            override fun onDismissCancelled() { finish() }
            override fun onDismissError() { finish() }
        }
        (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, done)
    }
}
