package com.ubirch.webui.batch

import java.io.{ BufferedWriter, ByteArrayInputStream, FileWriter }
import java.security
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.util.{ Base64, UUID }

import com.ubirch.webui.batch.SIM.COMMONNAMEOID
import org.apache.commons.codec.binary.Hex
import com.uirch.BCCertGen
import org.bouncycastle.jcajce.BCFKSLoadStoreParameter.SignatureAlgorithm
import org.bouncycastle.jce.PrincipalUtil

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
      if (base64) Base64.getEncoder.encodeToString(xCert.getEncoded)
      else Hex.encodeHexString(xCert.getEncoded)

    println(encodedCert)

    val factory = security.cert.CertificateFactory.getInstance("X.509")

    val x509Cert = factory.generateCertificate(new ByteArrayInputStream(Hex.decodeHex("30820315308201fda003020102020101300d06092a864886f70d01010d0500301e311c301a06035504030c1375626972636820476d62482054657374204341301e170d3230303330353135333134385a170d3231303330363031343035385a307e3110300e060355040613074765726d616e7931193017060355040a0c10756269726368205465737420476d6248310f300d06035504070c064265726c696e310f300d06035504080c064265726c696e312d302b06035504030c2436333964666261382d343065612d343630662d613838342d64396332326465383561666330820122300d06092a864886f70d01010105000382010f003082010a0282010100867a1e1c61dfbb27db5e39bb1bbc762949c56ef137c4e800994a1471932e0c6eaa0193096153ed48a428c082a1a758122a30a5ab057b35c8547efd758a6be51aec5a14994477602c3aea4100ec7fc0c2a38db4b322c4cb09bc36e9784738e950fffaa6ad17f520cc51b7e00b17e10f23b092d94cae2b5d734cbd793138f1ecc46d28470e434be97a993a988691b2bf614325c83a7ab0e5f647cf8d778a2d427884be84dcfcf0147a4e17d6fe87b1c34ed0254abbbd08ddc5a659545c954cb043a4246388a029b82d03f410e664c060df4ce09b4afb63449cb52109e5e8027ce51dd479a7eb89dff208c7d8a0c3e71bd31501cda48c823f45a809dac65b1852950203010001300d06092a864886f70d01010d0500038201010053b41dfbfa1d8e2a35ec342e6c5a911d7a927e572517454b0d8952832f685bd8b9311cb45d58263a1d916ec2dd2d8f5879a74b939f7407240c6e2ab5665bcd2e54bbb1d9c3fd8d2ddcd8975fe2e21f9d3a55e4e8af346018c513b58758c09fff4922797bec7ef5233d7d8d0fab1e432ea17f541e2c1a5a44e65c79f5dcc50b20d7fa459cf878222300ec02ee59597b35f5b30b4b6af969bfc292f7f53034218d9e2307a67cb56fcc8da74308e6e1e84b4c1320199322bab0765d2f49cc05132c477ab701289a03060f583e437493d4674c78ce69889277586b33bd934f7affcef1c9d819dac98f6e9e5666d26c5d6b46f976e7a9a57c948e7f571a3be63a87ec"))).asInstanceOf[X509Certificate]
    val principal = PrincipalUtil.getSubjectX509Principal(x509Cert)
    println(principal.getName)
    val values = principal.getValues(COMMONNAMEOID)
    println(values)

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

    val row: List[String] = createCert(
      (901288001099948L).toString,
      "111",
      UUID.randomUUID(),
      false
    )(kpg)

  }

}
