package com.willwinder.lynx

import java.security.PrivateKey

// Validation types
sealed class ValidationState: RuntimeException()
object Valid: ValidationState()
data class InvalidLastHash(private val expected: String, private val actual: String) : ValidationState()
data class InsufficientInputValue(val transactions: List<Transaction>) : ValidationState()
data class TransactionDoubleSpend(val transactionIds: List<Long>) : ValidationState()
data class PreviouslySpentTransactions(val transactionIds: List<Long>) : ValidationState()
data class CoinbaseRewardTooLarge(val rewardSize: Double) : ValidationState()
data class InvalidTransactionIds(val transactionIds: List<Long>) : ValidationState()
data class BadSignatures(val inputs: List<Input>) : ValidationState()

/**
 * LynxChain data structure. Contains methods and services for adding blocks to the chain.
 */
class LynxChain(genesisBlock: LynxBlock) {
  private val chain = mutableListOf<LynxBlock>()

  // This is a cache of the current balances based on chain data.
  private var unspentOutputCache = mapOf<Long,Output>()

  /**
   * Height of blockchain
   */
  val height get() = chain.size
  val last get() = chain.last()

  /**
   * Return the last ID used for an output, or -1 if there are none.
   */
  val lastId get() = chain.lastOrNull()
      ?.data               // get the transaction list
      ?.map { it.outputs } // get the output list
      ?.flatten()          // flatten to output stream
      ?.map { it.id }      // get the IDs
      ?.max() ?: -1L       // return the largest ID, or -1.

  init {
    add(genesisBlock)
  }

  companion object {
    const val REWARD = 100.0
  }

  /**
   * Print the blocks.
   */
  fun print() {
    println("Size: ${chain.size}\n")

    var i = 0
    chain.forEach {
      println("Block ${i++}")
      println("hash: ${it.getHash()}")
      println("last hash: ${it.lastHash}")
      println("transactions:")
      var j = 0
      it.data.forEach {
        when(j) {
          0    -> println("  Transaction: ${j++} (coinbase)")
          else -> println("  Transaction: ${j++}")
        }
        println("$it")
      }
    }
  }

  /**
   * Add and validate a new block.
   */
  fun add(block: LynxBlock) {
    val state = validate(block)
    when (state) {
      is Valid -> chain.add(block)
      else -> throw state
    }

    this.unspentOutputCache = unspentOutputs()
  }

  /**
   * Create and add a new block containing some data.
   */
  fun add(data: List<Transaction>) {
    add(LynxBlock(chain.last().getHash(), data))
  }

  /**
   * Create a signature of the Output.
   */
  fun getSignature(id: Long, key: PrivateKey) : String {
    // Find the given output and serialize it to a string.
    return sign(find(id).serialize(), key)
  }

  /**
   * Verify the signature using the Outputs PublicKey.
   */
  fun verifySignature(src: Input) : Boolean {
    val output = find(src.id)
    return try {
      verify(output.serialize(), src.signature, output.destination)
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Lookup a specific transaction, or null.
   */
  fun findOpt(id: Long) : Output? {
    return unspentOutputCache[id] // Cache to speed up most lookups.
        ?: chain.asSequence()     // Stop processing when a match is found.
            .map { it.data }      // Get transaction list.
            .flatten()            // Get transactions.
            .map { it.outputs }   // Get output list.
            .flatten()            // Get outputs.
            .find { it.id == id } // Look for output with given ID.
  }

  /**
   * Lookup a specific transaction.
   */
  fun find(id: Long) : Output {
    return findOpt(id) ?: throw NoSuchElementException("Not found: $id")
  }

  /**
   * Logic for validating the content of new block.
   */
  fun validate(block: LynxBlock) : ValidationState {
    /**
     * Last hash must equal previous block's hash.
     */
    fun badLastHash() = chain.isNotEmpty() && (chain.last().getHash() != block.lastHash)

    /**
     * Find inputs referencing spent transaction-ids.
     */
    fun previouslySpentTransactions() : List<Input> {
      return block.data
          // Get all the input transactions.
          .map {it.inputs }.flatten()
          // Get inputs which are NOT identified in the unspent cache.
          .filter { !unspentOutputCache.containsKey(it.id) }
    }

    /**
     * Look for duplicate transactionIds in the current transaction list.
     */
    fun doubleSpendsInCurrentTransaction() : Map<Long,Int> {
      return block.data
          // Get all the input transactions.
          .map { it.inputs }.flatten()
          // Group them by transaction ID, count occurrences.
          .groupingBy{ it.id }.eachCount()
          // Filter out the valid cases (spent 0 or 1 times).
          .filter { it.value > 1 }
    }

    /**
     * Make sure the sum of Inputs is >= the sum of Outputs
     */
    fun inputsGreaterThanOutputs() : List<Transaction> {
      // Lookup inputs and tally their values.
      fun getInputsValue(transaction: Transaction) : Double =
          transaction.inputs
              .map { this.unspentOutputCache[it.id]?.value ?: 0.0 }
              .sum()

      return block.data
          // skip coinbase transaction
          .drop(1)
          .filter { getInputsValue(it) < it.value() }
    }

    /**
     * Compute the coinbase transaction size.
     */
    fun coinbaseTransactionSize() : Double {
      return block.data[0].outputs.map { it.value }.sum()
    }

    /**
     * Transaction IDs should all be greater than the largest ID in the last block,
     * and unique in the current transaction list.
     */
    fun invalidTransactionIds() : Map<Long, Int> {
      return block.data
          // Get all the input transactions.
          .map { it.outputs }.flatten()
          // Group them by transaction ID, count occurrences.
          .groupingBy{ it.id }.eachCount()
          // Look for duplicate transaction IDs.
          .filter { it.value > 1 || it.key <= lastId }
    }

    /**
     * Verify that the signatures match.
     */
    fun badSignatures() : List<Input> {
      return block.data
          .map { it.inputs }
          .flatten()
          .filter { !verifySignature(it) }
    }

    //
    //
    // Do the checks.
    //
    //

    if (badLastHash()) {
      return InvalidLastHash(chain.last().getHash(), block.lastHash)
    }

    // Inputs must not be spent.
    previouslySpentTransactions().let {
      if (it.isNotEmpty()) {
        return PreviouslySpentTransactions(it.map { it.id }.toList())
      }
    }

    // A transaction must not be used in multiple Inputs.
    doubleSpendsInCurrentTransaction().let {
      if (it.isNotEmpty()) {
        return TransactionDoubleSpend(it.map { it.key }.toList() )
      }
    }

    // Transaction inputs > outputs.
    inputsGreaterThanOutputs().let {
      if (it.isNotEmpty()) {
        return InsufficientInputValue(it)
      }
    }

    // Coinbase reward transaction is not too large.
    coinbaseTransactionSize().let {
      if (it > REWARD) {
        return CoinbaseRewardTooLarge(it)
      }
    }

    // Validate transaction IDs are unique and increase in size.
    invalidTransactionIds().let {
      if (it.isNotEmpty()) {
        return InvalidTransactionIds(it.map { it.key }.toList())
      }
    }

    badSignatures().let {
      if (it.isNotEmpty()) {
        return BadSignatures(it)
      }
    }

    // Verify transaction signatures.
    return Valid
  }

  /**
   * Iterate over all transactions to find unspent transactions.
   */
  private fun unspentOutputs() : Map<Long, Output> {
    val inputMap = chain
        .map { it.data }
        .flatten()
        .map { it.inputs }
        .flatten()
        .groupBy { it.id }

    return chain
        .map { it.data }
        .flatten()
        .map { it.outputs }
        .flatten()
        .filter { !inputMap.containsKey(it.id) } // remove spent outputs.
        .map { Pair(it.id, it) }
        .toMap()
  }
}
