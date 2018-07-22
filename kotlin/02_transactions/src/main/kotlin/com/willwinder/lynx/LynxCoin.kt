package com.willwinder.lynx

import com.willwinder.lynx.LynxChain.Companion.REWARD

fun main(args: Array<String>) {
  // Create some keys for the demo.
  val key1 = generateKeyPair()
  val key2 = generateKeyPair()
  val key3 = generateKeyPair()

  // Genesis block with coinbase reward for key1.
  val genesisBlock = LynxBlock("0",
      listOf(
          // Coinbase transaction
          Transaction(
              // No inputs.
              listOf(),
              // One output.
              listOf(Output(0, REWARD, key1.public)))))

  // Create chain with genesis block.
  val chain = LynxChain(genesisBlock)

  val transaction1 = LynxBlock(chain.last.getHash(),
      listOf(
          // Coinbase transaction.
          Transaction(listOf(), listOf(Output(1, REWARD, key2.public))),
          // Send the initial reward to the owner of key2 and key3.
          Transaction(
              // Sign output using key1's private key.
              listOf(Input(0, chain.getSignature(0, key1.private))),
              // Create outputs for key2 and key3.
              listOf(
                  Output(chain.lastId + 2, 50.0, key2.public),
                  Output(chain.lastId + 3, 50.0, key3.public)
              ))))
  chain.add(transaction1)

  val transaction2 = LynxBlock(chain.last.getHash(),
      listOf(
          // Coinbase transaction.
          Transaction(listOf(), listOf(Output(chain.lastId + 1, REWARD, key2.public))),
          // Multiple outputs
          Transaction(
              // Use coinbase reward from transaction 1.
              listOf(Input(1, chain.getSignature(1, key2.private))),
              // Split up output into multiple pieces to show that we can.
              listOf(
                  Output(chain.lastId + 2, 20.0, key3.public),
                  Output(chain.lastId + 3, 20.0, key3.public),
                  Output(chain.lastId + 4, 20.0, key3.public),
                  Output(chain.lastId + 5, 20.0, key3.public),
                  Output(chain.lastId + 6, 20.0, key3.public)
              ))))
  chain.add(transaction2)

  chain.print()
}

