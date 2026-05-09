package cn.logicliu.filesafe.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoManager(
    private val context: Context
) {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createKey()
    }

    private fun createKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    fun encryptFile(inputFile: File, outputFile: File): Boolean {
        return try {
            if (inputFile.length() > LARGE_FILE_THRESHOLD) {
                encryptFileInChunks(inputFile, outputFile)
            } else {
                encryptFileInOneGo(inputFile, outputFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun encryptFileInOneGo(inputFile: File, outputFile: File): Boolean {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

            val iv = cipher.iv
            FileInputStream(inputFile).use { fis ->
                FileOutputStream(outputFile).use { fos ->
                    // Write header: 1 byte for isChunked (0)
                    fos.write(0)
                    // Write IV
                    fos.write(iv)
                    
                    val inputBytes = fis.readBytes()
                    val encryptedBytes = cipher.doFinal(inputBytes)
                    fos.write(encryptedBytes)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun encryptFileInChunks(inputFile: File, outputFile: File): Boolean {
        return try {
            FileInputStream(inputFile).use { fis ->
                FileOutputStream(outputFile).use { fos ->
                    // Write header: 1 byte for isChunked (1) + 8 bytes for original file size
                    fos.write(1)
                    val originalSizeBytes = java.nio.ByteBuffer.allocate(8).putLong(inputFile.length()).array()
                    fos.write(originalSizeBytes)

                    var bytesRead: Int
                    val buffer = ByteArray(CHUNK_SIZE)

                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val chunkData = if (bytesRead < CHUNK_SIZE) buffer.copyOf(bytesRead) else buffer

                        // Create cipher for each chunk
                        val cipher = Cipher.getInstance(TRANSFORMATION)
                        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                        val iv = cipher.iv
                        val encryptedChunk = cipher.doFinal(chunkData)

                        // Write chunk: 4 bytes for iv size, iv, 4 bytes for encrypted data size, encrypted data
                        val ivSizeBytes = java.nio.ByteBuffer.allocate(4).putInt(iv.size).array()
                        val encryptedSizeBytes = java.nio.ByteBuffer.allocate(4).putInt(encryptedChunk.size).array()

                        fos.write(ivSizeBytes)
                        fos.write(iv)
                        fos.write(encryptedSizeBytes)
                        fos.write(encryptedChunk)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun decryptFile(inputFile: File, outputFile: File): Boolean {
        return try {
            FileInputStream(inputFile).use { fis ->
                // Read first byte to see if it's chunked
                val isChunked = fis.read() == 1
                
                if (isChunked) {
                    decryptFileInChunks(fis, outputFile)
                } else {
                    decryptFileInOneGo(fis, outputFile)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun decryptFileInOneGo(fis: FileInputStream, outputFile: File): Boolean {
        return try {
            val iv = ByteArray(12)
            fis.read(iv)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)

            val encryptedBytes = fis.readBytes()
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            FileOutputStream(outputFile).use { fos ->
                fos.write(decryptedBytes)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun decryptFileInChunks(fis: FileInputStream, outputFile: File): Boolean {
        return try {
            // Skip original file size (8 bytes)
            fis.skip(8)

            FileOutputStream(outputFile).use { fos ->
                val ivSizeBuffer = ByteArray(4)
                val encryptedSizeBuffer = ByteArray(4)

                while (fis.read(ivSizeBuffer) != -1) {
                    val ivSize = java.nio.ByteBuffer.wrap(ivSizeBuffer).int
                    val iv = ByteArray(ivSize)
                    fis.read(iv)

                    fis.read(encryptedSizeBuffer)
                    val encryptedSize = java.nio.ByteBuffer.wrap(encryptedSizeBuffer).int
                    val encryptedChunk = ByteArray(encryptedSize)
                    fis.read(encryptedChunk)

                    // Decrypt the chunk
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    val spec = GCMParameterSpec(128, iv)
                    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
                    val decryptedChunk = cipher.doFinal(encryptedChunk)

                    fos.write(decryptedChunk)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun encryptFile(inputFile: File, password: String): ByteArray {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val key = deriveKeyFromPassword(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val inputBytes = FileInputStream(inputFile).use { it.readBytes() }
        val encryptedBytes = cipher.doFinal(inputBytes)

        return salt + iv + encryptedBytes
    }

    fun decryptFile(encryptedData: ByteArray, password: String): ByteArray {
        val salt = encryptedData.copyOfRange(0, 16)
        val iv = encryptedData.copyOfRange(16, 28)
        val data = encryptedData.copyOfRange(28, encryptedData.size)

        val key = deriveKeyFromPassword(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(data)
    }

    private fun deriveKeyFromPassword(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_SIZE)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, KEY_ALGORITHM)
    }

    fun encryptBytes(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data)
        return iv + encryptedData
    }

    fun decryptBytes(encryptedData: ByteArray): ByteArray {
        val iv = encryptedData.copyOfRange(0, 12)
        val data = encryptedData.copyOfRange(12, encryptedData.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        return cipher.doFinal(data)
    }

    fun generateFileKey(): String {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val key = keyGenerator.generateKey()
        return android.util.Base64.encodeToString(key.encoded, android.util.Base64.NO_WRAP)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "FileSafeKey"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 65536
        private const val KEY_SIZE = 256
        
        private const val CHUNK_SIZE = 4 * 1024 * 1024 // 4MB per chunk
        private const val LARGE_FILE_THRESHOLD = 10 * 1024 * 1024 // 10MB
    }
}
