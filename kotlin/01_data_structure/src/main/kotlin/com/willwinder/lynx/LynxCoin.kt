package com.willwinder.lynx

import org.apache.commons.codec.digest.DigestUtils
import java.time.LocalDateTime

fun main(args: Array<String>) {
  val chain = LynxChain(LynxBlock("0", "Genesis block"))
  chain.add("More data")
  chain.add(LocalDateTime.now())
  chain.printChain()
}

/**
 * Simple block data class with some helpers.
 */
data class LynxBlock(
    val lastHash: String,
    val data: Any
) {
  lateinit var hashCache: String

  fun getHash(): String {
    if (this::hashCache.isInitialized) return hashCache

    val str = "$lastHash$data"
    hashCache = DigestUtils.sha256Hex(str) ?:
        throw RuntimeException("Failed to create hash from: $str")

    return hashCache
  }
}

/**
 * Wraps a mutable list...
 */
class LynxChain(genesisBlock: LynxBlock) {
  private val chain = mutableListOf<LynxBlock>()

  init {
    add(genesisBlock)
  }

  /**
   * Print the blocks.
   */
  fun printChain() {
    print("Size: ${chain.size}")

    var i = 0
    chain.forEach {
      println("\n")
      println("Block ${i++}")
      println("hash: ${it.getHash()}")
      println("last hash: ${it.lastHash}")
      println("data: ${it.data}")
    }
  }

  /**
   * Add and validate a new block.
   */
  fun add(block: LynxBlock) {
    if (chain.isNotEmpty()) {
      if (chain.last().getHash() != block.lastHash) {
        val message = "Invalid block: wrong lastHash."
        throw IllegalArgumentException(message)
      }
    }

    chain.add(block)
  }

  /**
   * Create and add a new block containing some data.
   */
  fun add(data: Any) {
    add(LynxBlock(chain.last().getHash(), data))
  }
}
