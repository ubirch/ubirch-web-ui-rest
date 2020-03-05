package com.ubirch.webui.batch

import java.io.{ BufferedWriter, FileWriter }
import java.security.KeyPairGenerator
import java.util.{ Base64, UUID }
import org.apache.commons.codec.binary.Hex


import com.uirch.BCCertGen
import org.bouncycastle.jcajce.BCFKSLoadStoreParameter.SignatureAlgorithm

object BatchGen {

  def init: (KeyPairGenerator, BufferedWriter) = {

    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048)

    val writer = new BufferedWriter(new FileWriter("./certs_base64.csv"))
    (kpg, writer)
  }

  def createCert(imsi: String, pin: String, uuid: UUID, base64: Boolean)(kpg: KeyPairGenerator): List[String] = {
    val kp = kpg.generateKeyPair

    val xCert = BCCertGen.generate(
      kp.getPrivate,
      kp.getPublic,
      365,
      SignatureAlgorithm.SHA512withRSA.toString,
      true,
      uuid.toString
    )

    val encodedCert: String =
      if(base64) Base64.getEncoder.encodeToString(xCert.getEncoded)
      else Hex.encodeHexString(xCert.getEncoded)

    List(
      imsi,
      pin,
      uuid.toString,
      encodedCert
    )

  }

  def createCerts(kpg: KeyPairGenerator, limit: Int, count: Int = 1000, base64: Boolean): List[List[String]] = {
    if (limit <= 0) Nil
    else {

      val row: List[String] = createCert(
        (901288001099948L + count).toString,
        count.toString,
        UUID.randomUUID(),
        base64
      )(kpg)
      List(row) ++ createCerts(kpg, limit - 1, count + 1, base64)
    }

  }

  def main(args: Array[String]): Unit = {

    val (kpg, writer) = init

    createCerts(kpg, 500, base64 = false).foreach { row =>
      writer.write(s"${row.mkString(";")}\n")
    }
    writer.flush()
    writer.close()

  }

}
