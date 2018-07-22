package com.willwinder.lynx

import com.willwinder.lynx.LynxChain.Companion.REWARD
import com.willwinder.lynx.Transaction.Companion.basicCoinbase
import com.willwinder.lynx.Transaction.Companion.buildTransactionList
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.security.PublicKey

internal class LynxChainTest {
  companion object {
    val key1 = generateKeyPair()
    val key2 = generateKeyPair()

    val genesisBlock = LynxBlock("0",
        listOf(
            Transaction(
                listOf(),
                listOf(Output(0, REWARD, key1.public)))))
  }

  @Test
  fun validGenesisBlock() {
    val chain = LynxChain(genesisBlock)
    Assertions.assertThat(chain.height).isEqualTo(1)
  }

  /**
   * Test a valid chain of transactions.
   */
  @Test
  fun chainOfTransactions() {
    val chain = generateChainWithRewardsFor(key1.public, 1)

    val transaction1 = buildTransactionList(
        chain,
        listOf(Pair(1L, key1.private)),
        listOf(Pair(50.0, key1.public), Pair(50.0, key1.public)),
        key1.public)

    chain.add(transaction1)

    val transaction2 = buildTransactionList(
        chain,
        listOf(Pair(chain.lastId, key1.private)),
        listOf(
            Pair(10.0, key1.public),
            Pair(10.0, key1.public),
            Pair(10.0, key1.public),
            Pair(10.0, key1.public),
            Pair(10.0, key1.public)),
        key1.public)

    chain.add(transaction2)

    val transaction3 = buildTransactionList(
        chain,
        listOf(Pair(chain.lastId, key1.private)),
        listOf(
            Pair(5.0, key1.public),
            Pair(5.0, key1.public)),
        key1.public)

    chain.add(transaction3)

    Assertions.assertThat(chain.height).isEqualTo(5)
  }

  /**
   * Error when a coinbase larger than REWARD is specified.
   */
  @Test
  fun largeCoinbaseError() {
    val badTransaction = Transaction(
        listOf(),
        listOf(Output(0, REWARD + 1, key1.public)))

    Assertions.assertThatThrownBy { LynxChain(LynxBlock("0", listOf(badTransaction))) }
        .isExactlyInstanceOf(CoinbaseRewardTooLarge::class.java)
        .hasFieldOrPropertyWithValue("rewardSize", 101.0)
  }

  /**
   * Generate some transactions, then use them with some invalid transaction IDs.
   * Make sure only the invalid ones are reported.
   */
  @Test
  fun nonIncreasingTxId() {
    val chain = generateChainWithRewardsFor(key1.public, 5)
    Assertions.assertThatThrownBy {
      chain.add(LynxBlock(chain.last.getHash(), listOf(
          Transaction(listOf(Input(1, chain.getSignature(1, key1.private))), listOf(Output(3, REWARD, key1.public))),
          Transaction(listOf(Input(2, chain.getSignature(2, key1.private))), listOf(Output(4, REWARD, key1.public))),
          Transaction(listOf(Input(3, chain.getSignature(3, key1.private))), listOf(Output(5, REWARD, key1.public))),
          Transaction(listOf(Input(4, chain.getSignature(4, key1.private))), listOf(Output(6, REWARD, key1.public)))
      ))) }
        .isExactlyInstanceOf(InvalidTransactionIds::class.java)
        .hasFieldOrPropertyWithValue("transactionIds", listOf(3L, 4L, 5L))
  }

  /**
   * Simple valid transaction with one input and one output.
   */
  @Test
  fun validOneToOneTransactionNoChange() {
    val chain = generateChainWithRewardsFor(key1.public, 1)
    chain.add(LynxBlock(chain.last.getHash(), listOf(
        Transaction(listOf(Input(1, chain.getSignature(1, key1.private))), listOf(Output(6, REWARD, key1.public)))
    )))
    Assertions.assertThat(chain.height).isEqualTo(3)
  }

  /**
   * Error when the same id is used in multiple transactions in the same block.
   */
  @Test
  fun doubleSpendTransaction() {
    val chain = generateChainWithRewardsFor(key1.public, 1)
    val coinbase = basicCoinbase(key1.public, 2)
    val transaction = Transaction(
        listOf(Input(1, chain.getSignature(1, key1.private))),
        listOf(Output(3, REWARD, key1.public)))

    Assertions.assertThatThrownBy { chain.add(listOf(coinbase, transaction, transaction)) }
        .isExactlyInstanceOf(TransactionDoubleSpend::class.java)
        .hasFieldOrPropertyWithValue("transactionIds", listOf(1L))
  }

  /**
   * Error when attempting to spend a previously spent id.
   */
  @Test
  fun previouslySpentTransaction() {
    val chain = generateChainWithRewardsFor(key1.public, 1)

    val transactions = buildTransactionList(
        chain,
        listOf(Pair(1L, key1.private)),
        listOf(Pair(REWARD, key1.public)),
        key1.public)

    // Spend id 1.
    chain.add(transactions)

    // Attempt to spend it again.
    Assertions.assertThatThrownBy { chain.add(transactions) }
        .isExactlyInstanceOf(PreviouslySpentTransactions::class.java)
        .hasFieldOrPropertyWithValue("transactionIds", listOf(1L))
  }

  /**
   * Error when the sum of inputs is less than the sum of outputs.
   */
  @Test
  fun inputLessThanOutputError() {
    val chain = generateChainWithRewardsFor(key1.public, 5)

    val transactions = buildTransactionList(
        chain,
        listOf(Pair(1L, key1.private)),
        listOf(Pair(REWARD + 1, key1.public)),
        key1.public)

    Assertions.assertThatThrownBy { chain.add(transactions) }
        .isExactlyInstanceOf(InsufficientInputValue::class.java)
        .hasFieldOrProperty("transactions")
  }

  /**
   * Try some different combinations of multiple inputs/outputs.
   * Using 2x REWARD transactions and 2x values.
   * Note: Leftover money in the ouputs is not an error.
   */
  @ParameterizedTest
  @CsvSource(
      "1.0,1.0,true",
      "199.0,1.0,true",
      "199.9,0.1,true",
      "200.0,1.0,false",
      "1.0,200.0,false"
  )
  fun multipleInputsAndOutputs(d1: Double, d2: Double, isGood: Boolean) {
    val chain = generateChainWithRewardsFor(key1.public, 5)

    val transactions = buildTransactionList(
        chain,
        listOf(Pair(1L, key1.private), Pair(2L, key1.private)),
        listOf(Pair(d1, key1.public), Pair(d2, key1.public)),
        key1.public)

    when(isGood) {
      true -> chain.add(transactions)
      false -> {
        Assertions.assertThatThrownBy { chain.add(transactions) }
            .isExactlyInstanceOf(InsufficientInputValue::class.java)
            .hasFieldOrProperty("transactions")
      }
    }
  }

  @Test
  fun badSignature() {
    val chain = generateChainWithRewardsFor(key1.public, 3)
    val coinbase = Transaction.basicCoinbase(key1.public,4)
    val badSrc1 = Input(1, "Bad Signature")
    val badSrc2 = Input(3, "Bad Signature")
    val transaction = Transaction(
        listOf(
            badSrc1,
            Input(2, chain.getSignature(2, key1.private)),
            badSrc2),
        listOf(Output(5, REWARD, key1.public)))

    Assertions.assertThatThrownBy { chain.add(listOf(coinbase, transaction)) }
        .isExactlyInstanceOf(BadSignatures::class.java)
        .hasFieldOrPropertyWithValue("inputs", listOf(badSrc1, badSrc2))
  }

  /**
   * Generate some transactions to add currency to key
   */
  private fun generateChainWithRewardsFor(key: PublicKey, count: Long = 5, firstTx: Long = 1) : LynxChain {
    val chain = LynxChain(genesisBlock)
    for (i in 0 until count) {
      chain.add(
          LynxBlock(chain.last.getHash(), listOf(basicCoinbase(key, firstTx + i))))
    }
    return chain
  }
}
