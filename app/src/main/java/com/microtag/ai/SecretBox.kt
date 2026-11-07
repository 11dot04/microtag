package com.microtag.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps the API key encrypted with a key that never leaves the Android Keystore. Nothing is stored in plain text. */
internal object SecretBox {
    private const val ALIAS = "microtag_ai_key"
    private const val PREFS = "microtag_ai"
    private const val KEY_BLOB = "key_blob"

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun put(context: Context, secret: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (secret.isBlank()) { prefs.edit().remove(KEY_BLOB).apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val iv = cipher.iv
        val enc = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY_BLOB, Base64.encodeToString(iv + enc, Base64.NO_WRAP)).apply()
    }

    fun get(context: Context): String? = runCatching {
        val blob = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BLOB, null) ?: return null
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        }
        String(cipher.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    }.getOrNull()

    fun has(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_BLOB)
}
