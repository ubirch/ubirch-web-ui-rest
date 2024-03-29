package com.ubirch.webui.batch

import java.io.{ BufferedWriter, FileWriter }
import java.security.KeyPairGenerator
import java.util.{ Base64, Date, UUID }

import com.uirch.BCCertGen
import org.apache.commons.codec.binary.Hex
import org.bouncycastle.jcajce.BCFKSLoadStoreParameter.SignatureAlgorithm

object BatchGen {

  def createCert(imsi: String, pin: String, uuid: String, base64: Boolean)(kpg: KeyPairGenerator): List[String] = {
    val kp = kpg.generateKeyPair

    val xCert = BCCertGen.generate(
      kp.getPrivate,
      kp.getPublic,
      365,
      SignatureAlgorithm.SHA512withRSA.toString,
      true,
      uuid
    )

    val encodedCert: String =
      if (base64) Base64.getEncoder.encodeToString(xCert.getEncoded)
      else Hex.encodeHexString(xCert.getEncoded)

    List(
      imsi,
      pin,
      uuid,
      encodedCert
    )

  }

  def main(args: Array[String]): Unit = {

    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048)

    var writer: BufferedWriter = null

    val total = 500
    val base64 = false
    val time = new Date()
    val compressedUUID = true
    val fileName = if (base64) s"./certs_base64_" + time.getTime + s"_$total" + "_.csv" else s"./certs_hex_" + time.getTime + s"_$total" + "_.csv"

    try {

      writer = new BufferedWriter(new FileWriter(fileName))

      var index = 0
      var ret = 100

      var imsi = 901288001099948L
      var pin = 1234L
      def uuiF: String = UUID.randomUUID().toString

      Iterator.continually {

        val uuid = if (compressedUUID) uuiF.replaceAll("-", "") else uuiF

        val cert = createCert(
          imsi.toString,
          pin.toString,
          uuid,
          base64 = base64
        )(kpg)

        writer.write(s"${cert.mkString(";")}\n")

        imsi = imsi - 1
        pin = pin - 1
        index = index + 1

      }.take(total).foreach { _ =>
        ret = ret - 1
        if (ret == 0) {
          ret = 100
          println(".[" + index + "]")
        } else {
          print(".")
        }

      }

    } finally {

      if (writer != null) {
        writer.flush()
        writer.close()
      }

    }

  }
}
