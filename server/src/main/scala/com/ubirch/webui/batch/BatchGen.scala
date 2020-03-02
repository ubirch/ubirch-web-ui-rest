package com.ubirch.webui.batch

import java.io.{ BufferedWriter, FileWriter }
import java.security.KeyPairGenerator
import java.util.{ Base64, UUID }

import com.uirch.BCCertGen
import org.bouncycastle.jcajce.BCFKSLoadStoreParameter.SignatureAlgorithm

object BatchGen {

  def init: (KeyPairGenerator, BufferedWriter) = {

    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048)

    val writer = new BufferedWriter(new FileWriter("./certs.csv"))
    (kpg, writer)
  }

  def createCert(provider: String, imsi: String, pin: String, uuid: UUID)(kpg: KeyPairGenerator) = {
    val kp = kpg.generateKeyPair

    val xCert = BCCertGen.generate(
      kp.getPrivate,
      kp.getPublic,
      365,
      SignatureAlgorithm.SHA512withRSA.toString,
      true,
      uuid.toString
    )

    List(
      provider,
      imsi,
      pin,
      Base64.getEncoder.encodeToString(xCert.getEncoded)
    )

  }

  def createCerts(kpg: KeyPairGenerator, limit: Int, count: Int = 1000): List[List[String]] = {
    if (limit <= 0)
      Nil
    else {

      val row: List[String] = createCert(
        "1once",
        (100000000000000L + count).toString,
        count.toString,
        UUID.randomUUID()
      )(kpg)
      List(row) ++ createCerts(kpg, limit - 1, count + 1)
    }

  }

  def main(args: Array[String]): Unit = {

    val (kpg, writer) = init

    createCerts(kpg, 1).foreach { row =>
      writer.write(s"${row.mkString(";")}\n")
    }
    writer.flush()
    writer.close()

  }

}
