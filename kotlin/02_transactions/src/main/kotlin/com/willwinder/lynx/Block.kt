package com.willwinder.lynx

import org.apache.commons.codec.binary.Hex
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

/**
 * Simple block data class with some helpers.
 */
data class LynxBlock(
    val lastHash: String,
    val data: List<Transaction>
) {
  lateinit var hashCache: String

  fun getHash(): String {
    if (this::hashCache.isInitialized) return hashCache

    val digest = MessageDigest.getInstance("SHA-256")

    digest.update(lastHash.toByteArray(UTF_8))

    for (tx in data) {
      digest.update(tx.toString().toByteArray(UTF_8))
    }

    hashCache = Hex.encodeHexString(digest.digest()) ?:
        throw RuntimeException("Failed to create hash.")

    return hashCache
  }
}
