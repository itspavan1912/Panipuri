package com.myjarvis

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class Contact(val names: List<String>, val number: String, val ig: String)

object Store {
    private val NUM = Regex("^\\+\\d{8,15}$")
    private fun prefs(c: Context) = c.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
    private fun norm(s: String) = s.lowercase().replace(Regex("[\\s.,!?'\"-]+"), "")
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun kdf(p: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(norm(p).toCharArray(), salt, 120000, 256)).encoded

    // One contact per line:  name|alias, +919999999999, instagram_username
    fun parseContacts(txt: String): List<Contact> =
        txt.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val p = line.split(",").map { it.trim() }
            require(p.size >= 2 && NUM.matches(p[1])) { "Bad line: $line" }
            Contact(
                p[0].lowercase().split("|").map { it.trim() }.filter { it.isNotEmpty() },
                p[1],
                p.getOrElse(2) { "" }.removePrefix("@")
            )
        }

    fun save(c: Context, phrase: String, contactsText: String) {
        require(phrase.trim().split(Regex("\\s+")).size >= 3) { "Use at least 3 words" }
        parseContacts(contactsText)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(c).edit().putString("salt", hex(salt)).putString("hash", hex(kdf(phrase, salt)))
            .putString("contacts", contactsText).apply()
    }

    fun check(c: Context, attempt: String): Boolean {
        val p = prefs(c)
        val s = p.getString("salt", null) ?: return false
        val h = p.getString("hash", null) ?: return false
        return MessageDigest.isEqual(kdf(attempt, unhex(s)), unhex(h))
    }

    fun contacts(c: Context): List<Contact> =
        try { parseContacts(prefs(c).getString("contacts", "") ?: "") } catch (e: Exception) { emptyList() }
}
