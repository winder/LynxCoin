package com.willwinder.lynx

import com.willwinder.lynx.LynxChain.Companion.REWARD
import org.apache.commons.codec.binary.Hex
import java.security.PrivateKey
import java.security.PublicKey

data class Input(val id: Long, val signature: String) {
  fun serialize() = "${id}_$signature"
}

data class Output(val id: Long, val value: Double, val destination: PublicKey) {
  fun serialize() = "${id}_${value}_$destination"
}

data class Transaction(
    val inputs: List<Input>,
    val outputs: List<Output>
) {

  companion object {
    /**
     * Deserialize a string into a Transaction.
     */
    @JvmStatic
    fun deserialize(bytes: String): Transaction {
      val sb = StringBuffer()
      val inputs = mutableListOf<Input>()
      val outputs = mutableListOf<Output>()
      val iterator = bytes.iterator()
      while (iterator.hasNext()) {
        val char = iterator.next()
        if (char != '\n') {
          sb.append(char)
        } else {
          if (sb.isEmpty()) {
            break
          }

          val parts = sb.toString().split("_")
          if (parts.size != 2) throw IllegalArgumentException("Unexpected input: $sb")
          inputs.add(Input(parts[0].toLong(), parts[1]))
        }
      }

      while (iterator.hasNext()) {
        val char = iterator.next()
        if (char != '\n') {
          sb.append(char)
        } else {
          if (sb.isEmpty()) {
            break
          }

          val parts = sb.toString().split("_")
          if (parts.size != 2) throw IllegalArgumentException("Unexpected dest: $sb")
          outputs.add(Output(parts[0].toLong(), parts[1].toDouble(), getPublicKey(parts[2])))
        }
      }

      return Transaction(inputs, outputs)
    }

    /**
     * Helper for creating the coinbase Transaction.
     */
    @JvmStatic
    fun basicCoinbase(key: PublicKey, tx: Long) =
        Transaction(listOf(), listOf(Output(tx, REWARD, key)))

    /**
     * Helper to create the Transaction list, autocreates the coinbase reward somewhere.
     */
    @JvmStatic
    fun buildTransactionList(
        chain: LynxChain,
        src: List<Pair<Long, PrivateKey>>,
        dest: List<Pair<Double, PublicKey>>,
        coinbaseKey: PublicKey) : List<Transaction>
    {
      var inc = 1L
      return listOf(
          basicCoinbase(coinbaseKey, chain.lastId + inc++),
          Transaction(
              src.map { Input(it.first, chain.getSignature(it.first, it.second)) },
              dest.map { Output(chain.lastId + inc++, it.first, it.second) }
          )
      )
    }
  }

  override fun toString() : String {
    val builder = StringBuilder()

    builder.appendln("  | inputs: ${inputs.size}")
    inputs.forEach {
      builder.appendln("  |   tx: ${it.id}, sig: ${it.signature.substring(0..10)}...")
    }

    builder.appendln("  | outputs: ${outputs.size}")
    outputs.forEach {
      val pubKey = Hex.encodeHexString(it.destination.encoded).substring(256..266)
      builder.appendln("  |   tx: ${it.id}, value: ${it.value}, key: $pubKey...")
    }

    return builder.toString()
  }
  /**
   * Value of output transactions.
   */
  fun value() = outputs.map { it.value }.sum()

  /**
   * Serialize the transaction to a string.
   *
   * Format:
   * Inputs, one per line in the format "<id>_<sig>" until an empty line. Then outputs in the
   * format "<id>_<value>_<destination>" until a final empty line.
   */
  fun serialize(): String {
    val buffer = StringBuffer()
    inputs
        .map { "${it.serialize()}\n" }
        .forEach { buffer.append(it) }

    buffer.append("\n")

    outputs
        .map{"${it.serialize()}\n"}
        .forEach { buffer.append(it) }

    buffer.append("\n")

    return buffer.toString()
  }
}
