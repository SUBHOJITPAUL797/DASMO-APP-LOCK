package com.example.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "DasmoLockSecureKey"
    private const val TAG_SIZE = 128
    private const val BUFFER_SIZE = 64 * 1024 // 64 KB read buffer

    // Cryptographic deterministic seed and salt to ensure encrypted vault items and settings
    // survive app uninstallation and can be restored seamlessly on reinstallation!
    private val PERSISTENT_SALT = byteArrayOf(
        0x44, 0x41, 0x53, 0x4D, 0x4F, 0x5F, 0x53, 0x45,
        0x43, 0x55, 0x52, 0x45, 0x5F, 0x56, 0x41, 0x55,
        0x4C, 0x54, 0x5F, 0x50, 0x52, 0x4F, 0x54, 0x45,
        0x43, 0x54, 0x5F, 0x32, 0x30, 0x32, 0x36, 0x21
    ) // "DASMO_SECURE_VAULT_PROTECT_2026!"
    private const val PERSISTENT_SEED = "DASMO_VAULT_DETERMINISTIC_AES256_SEED_V1"

    private val persistentKey: SecretKey by lazy {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(PERSISTENT_SEED.toCharArray(), PERSISTENT_SALT, 10000, 256)
        val tmp = factory.generateSecret(spec)
        SecretKeySpec(tmp.encoded, "AES")
    }

    private val keyStore: KeyStore? by lazy {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        } catch (e: Exception) {
            null
        }
    }

    private fun getKeystoreKey(): SecretKey? {
        return try {
            val ks = keyStore ?: return null
            val existingKey = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            existingKey?.secretKey ?: generateKeystoreKey()
        } catch (e: Exception) {
            null
        }
    }

    private fun generateKeystoreKey(): SecretKey? {
        return try {
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
            keyGenerator.generateKey()
        } catch (e: Exception) {
            null
        }
    }

    private fun getSecretKey(): SecretKey {
        return persistentKey
    }

    /**
     * Encrypts the raw string data using AES-256-GCM with persistent key.
     * Returns a string consisting of: Base64(IV) + ":" + Base64(EncryptedBytes)
     */
    fun encrypt(data: String): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, persistentKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        
        return "$ivBase64:$encryptedBase64"
    }

    /**
     * Decrypts the cipher text (Base64(IV) + ":" + Base64(EncryptedBytes)) back to the original string.
     * Tries the persistent key first, falling back to legacy Keystore key if necessary.
     */
    fun decrypt(encryptedData: String): String {
        val parts = encryptedData.split(":")
        if (parts.size != 2) throw IllegalArgumentException("Malformed encrypted data")
        
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
        val spec = GCMParameterSpec(TAG_SIZE, iv)

        try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, persistentKey, spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            val legacyKey = getKeystoreKey()
            if (legacyKey != null) {
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, legacyKey, spec)
                val decryptedBytes = cipher.doFinal(encryptedBytes)
                return String(decryptedBytes, Charsets.UTF_8)
            }
            throw e
        }
    }

    /**
     * Encrypts raw bytes using persistent AES-256-GCM.
     * Combines IV and encrypted payload into a single byte array: [IV_Length (4 bytes)][IV][Encrypted Payload]
     */
    fun encryptBytes(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, persistentKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(data)
        
        val result = ByteArray(4 + iv.size + encryptedBytes.size)
        java.nio.ByteBuffer.wrap(result, 0, 4).putInt(iv.size)
        System.arraycopy(iv, 0, result, 4, iv.size)
        System.arraycopy(encryptedBytes, 0, result, 4 + iv.size, encryptedBytes.size)
        return result
    }

    /**
     * Encrypts raw bytes from an InputStream, reporting progress via [onProgress].
     * [totalBytes] is the total byte count of the source file (used for progress %).
     * [onProgress] is called on the IO thread with bytes read so far and total bytes.
     */
    fun encryptBytesWithProgress(
        inputStream: InputStream,
        totalBytes: Long,
        onProgress: (bytesRead: Long, total: Long) -> Unit
    ): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, persistentKey)
        val iv = cipher.iv

        // Read source in chunks, updating progress
        val rawBuffer = ByteArrayOutputStream(totalBytes.coerceAtLeast(1).toInt())
        val buf = ByteArray(BUFFER_SIZE)
        var totalRead = 0L
        var n: Int
        while (inputStream.read(buf).also { n = it } != -1) {
            rawBuffer.write(buf, 0, n)
            totalRead += n
            if (totalBytes > 0) onProgress(totalRead, totalBytes)
        }
        inputStream.close()

        val rawBytes = rawBuffer.toByteArray()
        val encryptedBytes = cipher.doFinal(rawBytes)

        val result = ByteArray(4 + iv.size + encryptedBytes.size)
        java.nio.ByteBuffer.wrap(result, 0, 4).putInt(iv.size)
        System.arraycopy(iv, 0, result, 4, iv.size)
        System.arraycopy(encryptedBytes, 0, result, 4 + iv.size, encryptedBytes.size)
        return result
    }

    /**
     * Decrypts a combined byte array: [IV_Length (4 bytes)][IV][Encrypted Payload] back to raw bytes.
     * Tries the persistent key first, falling back to legacy Keystore key if necessary.
     */
    fun decryptBytes(encryptedData: ByteArray): ByteArray {
        val buffer = java.nio.ByteBuffer.wrap(encryptedData)
        val ivLength = buffer.int
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val payloadOffset = 4 + ivLength
        val payloadLen = encryptedData.size - payloadOffset
        val spec = GCMParameterSpec(TAG_SIZE, iv)

        try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, persistentKey, spec)
            return cipher.doFinal(encryptedData, payloadOffset, payloadLen)
        } catch (e: Exception) {
            val legacyKey = getKeystoreKey()
            if (legacyKey != null) {
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, legacyKey, spec)
                return cipher.doFinal(encryptedData, payloadOffset, payloadLen)
            }
            throw e
        }
    }

    /**
     * Returns the IV from an encrypted byte array without decrypting the payload.
     * Format: [IV_Length (4 bytes)][IV][Encrypted Payload]
     */
    fun extractIvAndPayload(encryptedData: ByteArray): Pair<ByteArray, ByteArray> {
        val buffer = java.nio.ByteBuffer.wrap(encryptedData)
        val ivLength = buffer.int
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val payload = ByteArray(encryptedData.size - 4 - ivLength)
        buffer.get(payload)
        return Pair(iv, payload)
    }
}
