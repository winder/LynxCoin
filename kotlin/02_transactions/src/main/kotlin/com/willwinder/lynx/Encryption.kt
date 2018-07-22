/**
 * Methods from the following blog post:
 * http://niels.nu/blog/2016/java-rsa.html
 */
package com.willwinder.lynx

import java.nio.charset.StandardCharsets.UTF_8
import java.security.*
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher

@Throws(Exception::class)
fun generateKeyPair(): KeyPair {
  val generator = KeyPairGenerator.getInstance("RSA")
  generator.initialize(2048, SecureRandom())

  return generator.generateKeyPair()
}

@Throws(Exception::class)
fun getPublicKey(key: String): PublicKey {
  val byteKey = Base64.getDecoder().decode(key.toByteArray())
  val k = X509EncodedKeySpec(byteKey)
  val kf = KeyFactory.getInstance("RSA")

  return kf.generatePublic(k)
}

@Throws(Exception::class)
fun getPrivateKey(key: String): PrivateKey {
  val byteKey = Base64.getDecoder().decode(key.toByteArray())
  val k = X509EncodedKeySpec(byteKey)
  val kf = KeyFactory.getInstance("RSA")

  return kf.generatePrivate(k)
}


@Throws(Exception::class)
fun sign(text: String, privateKey: PrivateKey): String {
  val privateSignature = Signature.getInstance("SHA256withRSA")
  privateSignature.initSign(privateKey)
  privateSignature.update(text.toByteArray(UTF_8))

  val signature = privateSignature.sign()

  return Base64.getEncoder().encodeToString(signature)
}

@Throws(Exception::class)
fun verify(text: String, signature: String, publicKey: PublicKey): Boolean {
  val publicSignature = Signature.getInstance("SHA256withRSA")
  publicSignature.initVerify(publicKey)
  publicSignature.update(text.toByteArray(UTF_8))

  val signatureBytes = Base64.getDecoder().decode(signature)

  return publicSignature.verify(signatureBytes)
}

@Throws(Exception::class)
fun encrypt(text: String, publicKey: PublicKey): String {
  val cipher = Cipher.getInstance("RSA")
  cipher.init(Cipher.ENCRYPT_MODE, publicKey)

  val cipherText = cipher.doFinal(text.toByteArray(UTF_8))

  return Base64.getEncoder().encodeToString(cipherText)
      ?: throw IllegalArgumentException("Failed to encode: $text")
}

@Throws(Exception::class)
fun decrypt(cipherText: String, privateKey: PrivateKey): String {
  val bytes = Base64.getDecoder().decode(cipherText)

  val cipher = Cipher.getInstance("RSA")
  cipher.init(Cipher.DECRYPT_MODE, privateKey)

  return String(cipher.doFinal(bytes), UTF_8)
}
