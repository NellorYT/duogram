package com.latsudev.duogram

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoManager {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val MASTER_ALIAS = "duogram_master_aes"
    private const val IDENTITY_ALIAS = "duogram_identity_ec"

    fun generateIdentityIfNeeded() {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!ks.containsAlias(MASTER_ALIAS)) {
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            kg.init(
                KeyGenParameterSpec.Builder(
                    MASTER_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            kg.generateKey()
        }
        if (!ks.containsAlias(IDENTITY_ALIAS)) {
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
            kpg.initialize(
                KeyGenParameterSpec.Builder(
                    IDENTITY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .build()
            )
            kpg.generateKeyPair()
        }
    }

    fun getPublicKeySha256(): String {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val cert = ks.getCertificate(IDENTITY_ALIAS) ?: return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.publicKey.encoded)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun generateUsernameFromPublicKey(): String {
        generateIdentityIfNeeded()
        return "duo_${getPublicKeySha256().take(12)}"
    }

    fun encryptSavedNote(value: String): String {
        generateIdentityIfNeeded()
        val key = getMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val data = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val merged = iv + data
        return android.util.Base64.encodeToString(merged, android.util.Base64.NO_WRAP)
    }

    fun decryptSavedNote(cipherText: String): String {
        val raw = android.util.Base64.decode(cipherText, android.util.Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, 12)
        val body = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(body), StandardCharsets.UTF_8)
    }

    fun getSavedMessagesId(): String = "saved_messages_${getPublicKeySha256().take(16)}"

    fun encryptForDirectMessage(plain: ByteArray): String {
        // TODO: Здесь должна быть интеграция libsignal (Double Ratchet для 1-1).
        return encryptSavedNote(String(plain, StandardCharsets.UTF_8))
    }

    fun encryptForGroupMessage(plain: ByteArray): String {
        // TODO: Здесь должна быть интеграция libsignal Sender Keys для групп.
        return encryptSavedNote(String(plain, StandardCharsets.UTF_8))
    }

    fun exportPublicBundle(): String {
        // TODO: В экспорт добавить prekeys/signature bundle libsignal.
        return "{\"username\":\"${generateUsernameFromPublicKey()}\",\"pubKeyHash\":\"${getPublicKeySha256()}\"}"
    }

    private fun getMasterKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val entry = ks.getEntry(MASTER_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }
}
