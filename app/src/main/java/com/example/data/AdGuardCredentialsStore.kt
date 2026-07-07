package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.KeyStore

class AdGuardCredentialsStore(private val context: Context) {

    private val tag = "AdGuardCredentialsStore"
    private val prefsFilename = "adguard_secured_prefs"
    private var sharedPreferences: SharedPreferences

    init {
        sharedPreferences = try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                prefsFilename,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(tag, "EncryptedSharedPreferences creation failed, falling back to standard obfuscated SharedPreferences", e)
            try {
                // In case of error (e.g. key store issues), clean old corrupted file and use a secure fallback
                context.deleteSharedPreferences(prefsFilename)
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    prefsFilename,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (ex: Exception) {
                Log.e(tag, "Failed second attempt of EncryptedSharedPreferences, using standard prefs with local obfuscation", ex)
                context.getSharedPreferences("adguard_fallback_prefs", Context.MODE_PRIVATE)
            }
        }
    }

    private fun encrypt(value: String): String {
        return if (sharedPreferences.javaClass.simpleName == "EncryptedSharedPreferences") {
            value
        } else {
            // Local fallback obfuscation if EncryptedSharedPreferences completely failed
            try {
                Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.DEFAULT).trim()
            } catch (e: Exception) {
                value
            }
        }
    }

    private fun decrypt(value: String): String {
        return if (sharedPreferences.javaClass.simpleName == "EncryptedSharedPreferences") {
            value
        } else {
            try {
                String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8).trim()
            } catch (e: Exception) {
                value
            }
        }
    }

    fun saveCredentials(url: String, name: String, pass: String) {
        val cleanUrl = cleanUrl(url)
        val cleanName = name.trim()
        val cleanPass = pass.trim()
        sharedPreferences.edit().apply {
            putString("server_url", cleanUrl)
            putString("username", encrypt(cleanName))
            if (cleanPass.isNotEmpty()) {
                putString("password", encrypt(cleanPass))
            }
            putBoolean("has_credentials", true)
            apply()
        }
    }

    fun clearCredentials() {
        sharedPreferences.edit().apply {
            remove("server_url")
            remove("username")
            remove("password")
            putBoolean("has_credentials", false)
            apply()
        }
    }

    fun hasCredentials(): Boolean {
        return sharedPreferences.getBoolean("has_credentials", false) &&
                !getServerUrl().isNullOrBlank()
    }

    fun getServerUrl(): String? {
        return sharedPreferences.getString("server_url", null)
    }

    fun getUsername(): String? {
        val encrypted = sharedPreferences.getString("username", null) ?: return null
        return decrypt(encrypted)
    }

    fun getPassword(): String? {
        val encrypted = sharedPreferences.getString("password", null) ?: return null
        return decrypt(encrypted)
    }

    fun isTrustSelfSigned(): Boolean {
        return sharedPreferences.getBoolean("trust_self_signed", false)
    }

    fun setTrustSelfSigned(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("trust_self_signed", enabled).apply()
    }

    fun isLogsEnabled(): Boolean {
        return sharedPreferences.getBoolean("enable_logs", false)
    }

    fun setLogsEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("enable_logs", enabled).apply()
    }

    fun isNotificationsEnabled(): Boolean {
        return sharedPreferences.getBoolean("enable_notifications", true)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("enable_notifications", enabled).apply()
    }

    fun cleanUrl(url: String): String {
        var clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length - 1)
        }
        return clean
    }
}
