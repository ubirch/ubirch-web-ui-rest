package com.ubirch.webui.core

import java.util.Base64

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.connector.TokenProcessor
import org.keycloak.TokenVerifier
import org.keycloak.representations.AccessToken
import org.scalatest.{FeatureSpec, Matchers}


class TokenProcessorTest extends FeatureSpec with LazyLogging with Matchers {

  val fullTokenB46 = "eyJhbGciOiJFUzUxMiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICIxLVZMM3BsMFpsajNGZy1iblRlSFJDM0Q3N2F0RnhvTGRZbHdIZl9nZHRVIn0.eyJqdGkiOiI0M2JjZTM5Mi00YjMwLTRlMDctOTJlZS05ODA0Nzg4YTE1MGYiLCJleHAiOjE1NjUzNDgzOTEsIm5iZiI6MCwiaWF0IjoxNTY1MzQ4MDkxLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAvYXV0aC9yZWFsbXMvdGVzdC1yZWFsbSIsImF1ZCI6ImFjY291bnQiLCJzdWIiOiJhNzBkNThhOC1lMGNkLTQ2OTMtOTAxNi03MTZlYTI4M2M1ZTYiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJ1YmlyY2gtMi4wLXVzZXItYWNjZXNzLWxvY2FsIiwiYXV0aF90aW1lIjoxNTY1MzQ4MDkxLCJzZXNzaW9uX3N0YXRlIjoiMDc3NmIzMjMtODY4OS00MWYzLWIzMjEtYzhiZmExNGExMzk3IiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwOi8vbG9jYWxob3N0OjgwODAiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIlVTRVIiXX0sInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6Im9wZW5pZCBwcm9maWxlIGVtYWlsIiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJuYW1lIjoiZmlyc3RuYW1lX2NkIGxhc3RuYW1lX2NkIiwicHJlZmVycmVkX3VzZXJuYW1lIjoidXNlcm5hbWVfY2QiLCJnaXZlbl9uYW1lIjoiZmlyc3RuYW1lX2NkIiwiZmFtaWx5X25hbWUiOiJsYXN0bmFtZV9jZCJ9.MIGIAkIB1nYg6yLCkZsVkBwQAknLyLnEgXvtk4gBFJ-n5kuC2vZPkyAF0lLJbLGX7bV5TDuPXGh97eQ5iqSYYCPL32cojX8CQgEn9_ulnEvaa_R59sA3yBjWXBtd_9iltq-VPWRI6htXnGmQPNvecYrltrZ-n1m-O03K_YG82O0cIxEJNr12-5Rosw"

  feature("decode token") {

    scenario("test") {

      val splitToken = fullTokenB46.split("\\.")
      println(new String(Base64.getDecoder.decode(splitToken(1).getBytes)))
      val tokVerifier: TokenVerifier[AccessToken] = TokenVerifier.create(fullTokenB46, classOf[AccessToken])
      val tok = TokenProcessor.stringToToken(fullTokenB46)
      TokenVerifier.createWithoutSignature(tokVerifier.getToken)
      println(tokVerifier.getHeader)
      println(tokVerifier.getHeader)
      //tokVerifier.publicKey()

      println(s"issuer = ${tok.getIssuer}")
      val realm = TokenProcessor.getRealm(tok)
      println("realm: " + realm)
      println("username: " + TokenProcessor.getUsername(tok))
      println(tokVerifier.getToken.toString)
    }

  }
}

