package com.ubirch.webui.batch

import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.crypto.utils.Curve
import com.ubirch.crypto.{ GeneratorKeyFactory, PubKey }
import org.bouncycastle.jce.provider.BouncyCastleProvider

import scala.util.{ Failure, Success, Try }

/**
  * Created by derMicha on 19/05/17.
  */
object PublicKeyUtil extends LazyLogging {

  case class NoCurveException(message: String) extends Exception(message)

  final val ECDSA_names = List("ecdsa-p256v1", "ECC_ECDSA", "ECDSA", "SHA256withECDSA", "SHA512withECDSA")
  final val EDDSA_names = List("ed25519-sha-512", "ECC_ED25519", "Ed25519", "1.3.101.112")

  final val ECDSA = ECDSA_names.headOption.getOrElse("CURVE WITH NO NAME")
  final val EDDSA = EDDSA_names.headOption.getOrElse("CURVE WITH NO NAME")

  def associateCurve(algorithm: String): Try[Curve] = {
    algorithm.toLowerCase match {
      case a if ECDSA_names.map(_.toLowerCase).contains(a) => Success(Curve.PRIME256V1)
      case a if EDDSA_names.map(_.toLowerCase).contains(a) => Success(Curve.Ed25519)
      case _ => Failure(NoCurveException(s"No matching curve for $algorithm"))
    }
  }

  def pubKey(pubKeyBytes: Array[Byte], curve: Curve): PubKey = GeneratorKeyFactory.getPubKey(pubKeyBytes, curve)

  def provider: KeyPairGenerator = {
    val provider = new BouncyCastleProvider
    val kpg = KeyPairGenerator.getInstance("EC", provider)
    kpg.initialize(new ECGenParameterSpec("PRIME256V1"))
    kpg
  }

  def recreatePublicKey(encoded: Array[Byte], curve: Curve): Try[PubKey] = {

    def recreate(bytes: Array[Byte]) = Try(GeneratorKeyFactory.getPubKey(bytes, curve))

    val bytesLength = encoded.length
    for {
      bytesToUse <- Try {
        //We are slicing as only the latest 64 bytes are needed to recreate the key.
        if (curve == Curve.PRIME256V1) encoded.slice(bytesLength - 64, bytesLength) else encoded
      }
      pubKey <- recreate(bytesToUse)
    } yield {
      pubKey
    }

  }

}
