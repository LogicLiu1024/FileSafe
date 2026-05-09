package cn.logicliu.filesafe.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class EncryptionAlgorithm {
    AES_256_GCM,
    XCHACHA20_POLY1305
}

class CryptoManager(
    private val context: Context
) {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    
    init {
        Security.addProvider(BouncyCastleProvider())
    }

    private fun getOrCreateAESKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS_AES, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createAESKey()
    }

    private fun createAESKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS_AES,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    private fun generateXChaCha20Key(): SecretKey {
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, "ChaCha20")
    }

    private fun hChaCha20Subkey(key: ByteArray, nonce16: ByteArray): ByteArray {
        val state = IntArray(16)
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574
        for (i in 0..7) {
            state[4 + i] = ((key[i * 4].toInt() and 0xFF)
                or ((key[i * 4 + 1].toInt() and 0xFF) shl 8)
                or ((key[i * 4 + 2].toInt() and 0xFF) shl 16)
                or ((key[i * 4 + 3].toInt() and 0xFF) shl 24))
        }
        for (i in 0..3) {
            state[12 + i] = ((nonce16[i * 4].toInt() and 0xFF)
                or ((nonce16[i * 4 + 1].toInt() and 0xFF) shl 8)
                or ((nonce16[i * 4 + 2].toInt() and 0xFF) shl 16)
                or ((nonce16[i * 4 + 3].toInt() and 0xFF) shl 24))
        }

        val x = state.copyOf()
        for (i in 0 until 10) {
            quarterRound(x, 0, 4, 8, 12)
            quarterRound(x, 1, 5, 9, 13)
            quarterRound(x, 2, 6, 10, 14)
            quarterRound(x, 3, 7, 11, 15)
            quarterRound(x, 0, 5, 10, 15)
            quarterRound(x, 1, 6, 11, 12)
            quarterRound(x, 2, 7, 8, 13)
            quarterRound(x, 3, 4, 9, 14)
        }
        for (i in 0..15) {
            x[i] += state[i]
        }

        val subkey = ByteArray(32)
        for (i in 0..3) {
            val v0 = x[i]
            subkey[i * 4] = (v0 and 0xFF).toByte()
            subkey[i * 4 + 1] = ((v0 ushr 8) and 0xFF).toByte()
            subkey[i * 4 + 2] = ((v0 ushr 16) and 0xFF).toByte()
            subkey[i * 4 + 3] = ((v0 ushr 24) and 0xFF).toByte()
            val v12 = x[12 + i]
            subkey[16 + i * 4] = (v12 and 0xFF).toByte()
            subkey[16 + i * 4 + 1] = ((v12 ushr 8) and 0xFF).toByte()
            subkey[16 + i * 4 + 2] = ((v12 ushr 16) and 0xFF).toByte()
            subkey[16 + i * 4 + 3] = ((v12 ushr 24) and 0xFF).toByte()
        }
        return subkey
    }

    private fun quarterRound(state: IntArray, a: Int, b: Int, c: Int, d: Int) {
        state[a] += state[b]
        state[d] = (state[d] xor state[a]) shl 16 or ((state[d] xor state[a]) ushr 16)
        state[c] += state[d]
        state[b] = (state[b] xor state[c]) shl 12 or ((state[b] xor state[c]) ushr 20)
        state[a] += state[b]
        state[d] = (state[d] xor state[a]) shl 8 or ((state[d] xor state[a]) ushr 24)
        state[c] += state[d]
        state[b] = (state[b] xor state[c]) shl 7 or ((state[b] xor state[c]) ushr 25)
    }

    private fun toInternalNonce(nonce24: ByteArray): ByteArray {
        val internalNonce = ByteArray(12)
        System.arraycopy(nonce24, 16, internalNonce, 4, 8)
        return internalNonce
    }

    fun encryptFile(
        inputFile: File,
        outputFile: File,
        algorithm: EncryptionAlgorithm = EncryptionAlgorithm.AES_256_GCM,
        progressCallback: ((Float) -> Unit)? = null
    ): Boolean {
        return try {
            if (inputFile.length() > LARGE_FILE_THRESHOLD) {
                encryptFileInChunks(inputFile, outputFile, algorithm, progressCallback)
            } else {
                encryptFileInOneGo(inputFile, outputFile, algorithm, progressCallback)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun encryptFileInOneGo(
        inputFile: File,
        outputFile: File,
        algorithm: EncryptionAlgorithm,
        progressCallback: ((Float) -> Unit)?
    ): Boolean {
        return try {
            progressCallback?.invoke(0f)
            
            when (algorithm) {
                EncryptionAlgorithm.AES_256_GCM -> {
                    val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAESKey())
                    val iv = cipher.iv

                    FileInputStream(inputFile).use { fis ->
                        FileOutputStream(outputFile).use { fos ->
                            fos.write(ALGORITHM_MARKER_AES)
                            fos.write(0)
                            fos.write(iv)
                            
                            val inputBytes = fis.readBytes()
                            progressCallback?.invoke(0.5f)
                            val encryptedBytes = cipher.doFinal(inputBytes)
                            fos.write(encryptedBytes)
                        }
                    }
                }
                EncryptionAlgorithm.XCHACHA20_POLY1305 -> {
                    val key = generateXChaCha20Key()
                    val nonce = ByteArray(24).apply { SecureRandom().nextBytes(this) }
                    val subkey = hChaCha20Subkey(key.encoded, nonce.copyOfRange(0, 16))
                    val internalNonce = toInternalNonce(nonce)

                    val cipher = ChaCha20Poly1305()
                    cipher.init(true, AEADParameters(KeyParameter(subkey), 128, internalNonce))

                    FileInputStream(inputFile).use { fis ->
                        FileOutputStream(outputFile).use { fos ->
                            fos.write(ALGORITHM_MARKER_XCHACHA)
                            fos.write(0)
                            fos.write(nonce)
                            fos.write(key.encoded)

                            val inputBytes = fis.readBytes()
                            progressCallback?.invoke(0.5f)
                            val encryptedBytes = ByteArray(cipher.getOutputSize(inputBytes.size))
                            val len = cipher.processBytes(inputBytes, 0, inputBytes.size, encryptedBytes, 0)
                            cipher.doFinal(encryptedBytes, len)
                            fos.write(encryptedBytes)
                        }
                    }
                }
            }
            
            progressCallback?.invoke(1f)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun encryptFileInChunks(
        inputFile: File,
        outputFile: File,
        algorithm: EncryptionAlgorithm,
        progressCallback: ((Float) -> Unit)?
    ): Boolean {
        return try {
            val totalSize = inputFile.length()
            var processed = 0L
            progressCallback?.invoke(0f)
            
            FileInputStream(inputFile).use { fis ->
                FileOutputStream(outputFile).use { fos ->
                    when (algorithm) {
                        EncryptionAlgorithm.AES_256_GCM -> {
                            fos.write(ALGORITHM_MARKER_AES)
                            fos.write(1)
                            val originalSizeBytes = ByteBuffer.allocate(8).putLong(inputFile.length()).array()
                            fos.write(originalSizeBytes)

                            var bytesRead: Int
                            val buffer = ByteArray(CHUNK_SIZE)

                            while (fis.read(buffer).also { bytesRead = it } != -1) {
                                val chunkData = if (bytesRead < CHUNK_SIZE) buffer.copyOf(bytesRead) else buffer
                                processed += bytesRead

                                val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAESKey())
                                val iv = cipher.iv
                                val encryptedChunk = cipher.doFinal(chunkData)

                                val ivSizeBytes = ByteBuffer.allocate(4).putInt(iv.size).array()
                                val encryptedSizeBytes = ByteBuffer.allocate(4).putInt(encryptedChunk.size).array()

                                fos.write(ivSizeBytes)
                                fos.write(iv)
                                fos.write(encryptedSizeBytes)
                                fos.write(encryptedChunk)
                                
                                progressCallback?.invoke(processed.toFloat() / totalSize)
                            }
                        }
                        EncryptionAlgorithm.XCHACHA20_POLY1305 -> {
                            val key = generateXChaCha20Key()
                            fos.write(ALGORITHM_MARKER_XCHACHA)
                            fos.write(1)
                            val originalSizeBytes = ByteBuffer.allocate(8).putLong(inputFile.length()).array()
                            fos.write(originalSizeBytes)
                            fos.write(key.encoded)

                            var bytesRead: Int
                            val buffer = ByteArray(CHUNK_SIZE)

                            while (fis.read(buffer).also { bytesRead = it } != -1) {
                                val chunkData = if (bytesRead < CHUNK_SIZE) buffer.copyOf(bytesRead) else buffer
                                processed += bytesRead

                                val nonce = ByteArray(24).apply { SecureRandom().nextBytes(this) }
                                val subkey = hChaCha20Subkey(key.encoded, nonce.copyOfRange(0, 16))
                                val internalNonce = toInternalNonce(nonce)

                                val cipher = ChaCha20Poly1305()
                                cipher.init(true, AEADParameters(KeyParameter(subkey), 128, internalNonce))
                                val encryptedChunk = ByteArray(cipher.getOutputSize(chunkData.size))
                                val len = cipher.processBytes(chunkData, 0, chunkData.size, encryptedChunk, 0)
                                cipher.doFinal(encryptedChunk, len)

                                val nonceSizeBytes = ByteBuffer.allocate(4).putInt(nonce.size).array()
                                val encryptedSizeBytes = ByteBuffer.allocate(4).putInt(encryptedChunk.size).array()

                                fos.write(nonceSizeBytes)
                                fos.write(nonce)
                                fos.write(encryptedSizeBytes)
                                fos.write(encryptedChunk)

                                progressCallback?.invoke(processed.toFloat() / totalSize)
                            }
                        }
                    }
                }
            }
            progressCallback?.invoke(1f)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun encryptFileParallel(
        inputFile: File,
        outputFile: File,
        algorithm: EncryptionAlgorithm = EncryptionAlgorithm.AES_256_GCM,
        progressCallback: ((Float) -> Unit)? = null
    ): Boolean {
        return try {
            if (inputFile.length() <= LARGE_FILE_THRESHOLD) {
                return encryptFile(inputFile, outputFile, algorithm, progressCallback)
            }

            val totalSize = inputFile.length()
            val chunks = mutableListOf<ByteArray>()
            val chunkMetadata = mutableListOf<ChunkMeta>()
            
            FileInputStream(inputFile).use { fis ->
                var bytesRead: Int
                val buffer = ByteArray(CHUNK_SIZE)
                var offset = 0L
                
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    val chunkData = if (bytesRead < CHUNK_SIZE) buffer.copyOf(bytesRead) else buffer
                    chunks.add(chunkData)
                    chunkMetadata.add(ChunkMeta(offset, bytesRead))
                    offset += bytesRead
                }
            }

            progressCallback?.invoke(0.1f)

            val encryptedChunks = coroutineScope {
                chunks.mapIndexed { index, chunkData ->
                    async(Dispatchers.Default) {
                        when (algorithm) {
                            EncryptionAlgorithm.AES_256_GCM -> {
                                val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAESKey())
                                val iv = cipher.iv
                                val encrypted = cipher.doFinal(chunkData)
                                EncryptedChunk(iv, encrypted, index)
                            }
                            EncryptionAlgorithm.XCHACHA20_POLY1305 -> {
                                val key = generateXChaCha20Key()
                                val nonce = ByteArray(24).apply { SecureRandom().nextBytes(this) }
                                val subkey = hChaCha20Subkey(key.encoded, nonce.copyOfRange(0, 16))
                                val internalNonce = toInternalNonce(nonce)

                                val cipher = ChaCha20Poly1305()
                                cipher.init(true, AEADParameters(KeyParameter(subkey), 128, internalNonce))
                                val encrypted = ByteArray(cipher.getOutputSize(chunkData.size))
                                val len = cipher.processBytes(chunkData, 0, chunkData.size, encrypted, 0)
                                cipher.doFinal(encrypted, len)
                                EncryptedChunk(nonce, encrypted, index, key.encoded)
                            }
                        }
                    }
                }.awaitAll()
            }

            progressCallback?.invoke(0.8f)

            FileOutputStream(outputFile).use { fos ->
                when (algorithm) {
                    EncryptionAlgorithm.AES_256_GCM -> {
                        fos.write(ALGORITHM_MARKER_AES)
                        fos.write(1)
                        fos.write(ByteBuffer.allocate(8).putLong(inputFile.length()).array())
                    }
                    EncryptionAlgorithm.XCHACHA20_POLY1305 -> {
                        fos.write(ALGORITHM_MARKER_XCHACHA)
                        fos.write(1)
                        fos.write(ByteBuffer.allocate(8).putLong(inputFile.length()).array())
                    }
                }

                encryptedChunks.sortedBy { it.index }.forEach { chunk ->
                    val ivSizeBytes = ByteBuffer.allocate(4).putInt(chunk.iv.size).array()
                    val encryptedSizeBytes = ByteBuffer.allocate(4).putInt(chunk.encryptedData.size).array()

                    fos.write(ivSizeBytes)
                    fos.write(chunk.iv)
                    fos.write(encryptedSizeBytes)
                    fos.write(chunk.encryptedData)
                    
                    if (chunk.key != null) {
                        fos.write(chunk.key)
                    }
                }
            }

            progressCallback?.invoke(1f)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun decryptFile(
        inputFile: File,
        outputFile: File,
        progressCallback: ((Float) -> Unit)? = null
    ): Boolean {
        return try {
            progressCallback?.invoke(0f)
            
            FileInputStream(inputFile).use { fis ->
                val algorithmMarker = fis.read()
                val isChunked = fis.read() == 1
                
                val algorithm = when (algorithmMarker) {
                    ALGORITHM_MARKER_XCHACHA -> EncryptionAlgorithm.XCHACHA20_POLY1305
                    ALGORITHM_MARKER_AES -> EncryptionAlgorithm.AES_256_GCM
                    else -> {
                        fis.close()
                        return decryptLegacyFile(inputFile, outputFile, progressCallback)
                    }
                }
                
                if (isChunked) {
                    decryptFileInChunks(fis, outputFile, algorithm, progressCallback)
                } else {
                    decryptFileInOneGo(fis, outputFile, algorithm, progressCallback)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun decryptFileInOneGo(
        fis: FileInputStream,
        outputFile: File,
        algorithm: EncryptionAlgorithm,
        progressCallback: ((Float) -> Unit)?
    ): Boolean {
        return try {
            when (algorithm) {
                EncryptionAlgorithm.AES_256_GCM -> {
                    val iv = ByteArray(12)
                    fis.read(iv)
                    progressCallback?.invoke(0.3f)

                    val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                    val spec = GCMParameterSpec(128, iv)
                    cipher.init(Cipher.DECRYPT_MODE, getOrCreateAESKey(), spec)

                    val encryptedBytes = fis.readBytes()
                    progressCallback?.invoke(0.7f)
                    val decryptedBytes = cipher.doFinal(encryptedBytes)

                    FileOutputStream(outputFile).use { fos ->
                        fos.write(decryptedBytes)
                    }
                }
                EncryptionAlgorithm.XCHACHA20_POLY1305 -> {
                    val nonce = ByteArray(24)
                    fis.read(nonce)
                    val keyBytes = ByteArray(32)
                    fis.read(keyBytes)
                    progressCallback?.invoke(0.3f)

                    val subkey = hChaCha20Subkey(keyBytes, nonce.copyOfRange(0, 16))
                    val internalNonce = toInternalNonce(nonce)

                    val cipher = ChaCha20Poly1305()
                    cipher.init(false, AEADParameters(KeyParameter(subkey), 128, internalNonce))

                    val encryptedBytes = fis.readBytes()
                    progressCallback?.invoke(0.7f)
                    val decryptedBytes = ByteArray(cipher.getOutputSize(encryptedBytes.size))
                    val len = cipher.processBytes(encryptedBytes, 0, encryptedBytes.size, decryptedBytes, 0)
                    cipher.doFinal(decryptedBytes, len)

                    FileOutputStream(outputFile).use { fos ->
                        fos.write(decryptedBytes, 0, len)
                    }
                }
            }
            progressCallback?.invoke(1f)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun decryptFileInChunks(
        fis: FileInputStream,
        outputFile: File,
        algorithm: EncryptionAlgorithm,
        progressCallback: ((Float) -> Unit)?
    ): Boolean {
        return try {
            fis.skip(8)

            FileOutputStream(outputFile).use { fos ->
                val ivSizeBuffer = ByteArray(4)
                val encryptedSizeBuffer = ByteArray(4)

                when (algorithm) {
                    EncryptionAlgorithm.AES_256_GCM -> {
                        while (fis.read(ivSizeBuffer) != -1) {
                            val ivSize = ByteBuffer.wrap(ivSizeBuffer).int
                            val iv = ByteArray(ivSize)
                            fis.read(iv)

                            fis.read(encryptedSizeBuffer)
                            val encryptedSize = ByteBuffer.wrap(encryptedSizeBuffer).int
                            val encryptedChunk = ByteArray(encryptedSize)
                            fis.read(encryptedChunk)

                            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                            val spec = GCMParameterSpec(128, iv)
                            cipher.init(Cipher.DECRYPT_MODE, getOrCreateAESKey(), spec)
                            val decryptedChunk = cipher.doFinal(encryptedChunk)

                            fos.write(decryptedChunk)
                        }
                    }
                    EncryptionAlgorithm.XCHACHA20_POLY1305 -> {
                        val keyBytes = ByteArray(32)
                        fis.read(keyBytes)

                        while (fis.read(ivSizeBuffer) != -1) {
                            val nonceSize = ByteBuffer.wrap(ivSizeBuffer).int
                            val nonce = ByteArray(nonceSize)
                            fis.read(nonce)

                            fis.read(encryptedSizeBuffer)
                            val encryptedSize = ByteBuffer.wrap(encryptedSizeBuffer).int
                            val encryptedChunk = ByteArray(encryptedSize)
                            fis.read(encryptedChunk)

                            val subkey = hChaCha20Subkey(keyBytes, nonce.copyOfRange(0, 16))
                            val internalNonce = toInternalNonce(nonce)

                            val cipher = ChaCha20Poly1305()
                            cipher.init(false, AEADParameters(KeyParameter(subkey), 128, internalNonce))
                            val decryptedChunk = ByteArray(cipher.getOutputSize(encryptedChunk.size))
                            val len = cipher.processBytes(encryptedChunk, 0, encryptedChunk.size, decryptedChunk, 0)
                            cipher.doFinal(decryptedChunk, len)

                            fos.write(decryptedChunk, 0, len)
                        }
                    }
                }
            }
            progressCallback?.invoke(1f)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun decryptLegacyFile(
        inputFile: File,
        outputFile: File,
        progressCallback: ((Float) -> Unit)?
    ): Boolean {
        return try {
            progressCallback?.invoke(0f)
            val totalSize = inputFile.length()
            var processed = 1L
            
            FileInputStream(inputFile).use { fis ->
                val isChunked = fis.read() == 1
                
                if (isChunked) {
                    processed += 8
                    fis.skip(8)

                    FileOutputStream(outputFile).use { fos ->
                        val ivSizeBuffer = ByteArray(4)
                        val encryptedSizeBuffer = ByteArray(4)

                        while (fis.read(ivSizeBuffer) != -1) {
                            val ivSize = ByteBuffer.wrap(ivSizeBuffer).int
                            val iv = ByteArray(ivSize)
                            fis.read(iv)
                            processed += 4 + ivSize

                            fis.read(encryptedSizeBuffer)
                            val encryptedSize = ByteBuffer.wrap(encryptedSizeBuffer).int
                            val encryptedChunk = ByteArray(encryptedSize)
                            fis.read(encryptedChunk)
                            processed += 4 + encryptedSize

                            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                            val spec = GCMParameterSpec(128, iv)
                            cipher.init(Cipher.DECRYPT_MODE, getOrCreateAESKey(), spec)
                            val decryptedChunk = cipher.doFinal(encryptedChunk)

                            fos.write(decryptedChunk)
                            progressCallback?.invoke(processed.toFloat() / totalSize)
                        }
                    }
                } else {
                    val iv = ByteArray(12)
                    fis.read(iv)
                    progressCallback?.invoke(0.3f)

                    val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                    val spec = GCMParameterSpec(128, iv)
                    cipher.init(Cipher.DECRYPT_MODE, getOrCreateAESKey(), spec)

                    val encryptedBytes = fis.readBytes()
                    progressCallback?.invoke(0.7f)
                    val decryptedBytes = cipher.doFinal(encryptedBytes)

                    FileOutputStream(outputFile).use { fos ->
                        fos.write(decryptedBytes)
                    }
                }
            }
            progressCallback?.invoke(1f)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun encryptFile(inputFile: File, password: String): ByteArray {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val key = deriveKeyFromPassword(password, salt)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
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
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
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
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAESKey())
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data)
        return iv + encryptedData
    }

    fun decryptBytes(encryptedData: ByteArray): ByteArray {
        val iv = encryptedData.copyOfRange(0, 12)
        val data = encryptedData.copyOfRange(12, encryptedData.size)

        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateAESKey(), spec)
        return cipher.doFinal(data)
    }

    fun generateFileKey(): String {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val key = keyGenerator.generateKey()
        return android.util.Base64.encodeToString(key.encoded, android.util.Base64.NO_WRAP)
    }

    private data class ChunkMeta(val offset: Long, val size: Int)
    private data class EncryptedChunk(
        val iv: ByteArray,
        val encryptedData: ByteArray,
        val index: Int,
        val key: ByteArray? = null
    )

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_AES = "FileSafeKey_AES"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 10000
        private const val KEY_SIZE = 256
        
        private const val CHUNK_SIZE = 8 * 1024 * 1024
        private const val LARGE_FILE_THRESHOLD = 10 * 1024 * 1024
        
        private const val ALGORITHM_MARKER_AES = 0x01
        private const val ALGORITHM_MARKER_XCHACHA = 0x02
    }
}
