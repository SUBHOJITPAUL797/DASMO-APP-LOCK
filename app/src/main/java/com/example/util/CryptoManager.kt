package com.example.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoManager {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "DasmoLockSecureKey"
    private const val TAG_SIZE = 128

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun getSecretKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts the raw string data using AES-256-GCM.
     * Returns a string consisting of: Base64(IV) + ":" + Base64(EncryptedBytes)
     */
    fun encrypt(data: String): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        
        return "$ivBase64:$encryptedBase64"
    }

    /**
     * Decrypts the cipher text (Base64(IV) + ":" + Base64(EncryptedBytes)) back to the original string.
     */
    fun decrypt(encryptedData: String): String {
        val parts = encryptedData.split(":")
        if (parts.size != 2) throw IllegalArgumentException("Malformed encrypted data")
        
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Encrypts raw bytes.
     * Combines IV and encrypted payload into a single byte array: [IV_Length (4 bytes)][IV][Encrypted Payload]
     */
    fun encryptBytes(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(data)
        
        val result = ByteArray(4 + iv.size + encryptedBytes.size)
        java.nio.ByteBuffer.wrap(result, 0, 4).putInt(iv.size)
        System.arraycopy(iv, 0, result, 4, iv.size)
        System.arraycopy(encryptedBytes, 0, result, 4 + iv.size, encryptedBytes.size)
        return result
    }

    /**
     * Decrypts a combined byte array: [IV_Length (4 bytes)][IV][Encrypted Payload] back to raw bytes.
     */
    fun decryptBytes(encryptedData: ByteArray): ByteArray {
        val buffer = java.nio.ByteBuffer.wrap(encryptedData)
        val ivLength = buffer.int
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val encryptedBytes = ByteArray(encryptedData.size - 4 - ivLength)
        buffer.get(encryptedBytes)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        return cipher.doFinal(encryptedBytes)
    }
}
