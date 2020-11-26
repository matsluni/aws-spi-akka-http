/*
 * Copyright 2018 Matthias Lüneberg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.matsluni.akkahttpspi

import akka.http.scaladsl.model.headers.{Accept, `Content-Type`}
import akka.http.scaladsl.model._
import akka.util.ByteString
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters._

class AkkaHttpClientSpec extends AnyWordSpec with Matchers with OptionValues {

  "AkkaHttpClient" should {

    "parse custom content type" in {
      val contentTypeStr = "application/xml"
      val contentType = AkkaHttpClient.tryCreateCustomContentType(contentTypeStr)
      contentType.mediaType should be (MediaTypes.`application/xml`)
    }

    "remove 'ContentType' return 'ContentLength' separate from sdk headers" in {
      val headers = collection.immutable.Map(
        "Content-Type" -> List("application/xml").asJava,
        "Content-Length"-> List("123").asJava,
        "Accept" -> List("*/*").asJava
      ).asJava

      val (contentTypeHeader, reqHeaders) = AkkaHttpClient.convertHeaders(headers)

      contentTypeHeader.value.lowercaseName() shouldBe `Content-Type`.lowercaseName
      reqHeaders should have size 1
    }

    "put 'ContentType' and 'ContentLength' into SdkResponse" in {
      val sdkResponseHeaders = collection.immutable.Map(
        "Content-Type" -> List("application/json"),
        "Content-Length"-> List("1"),
        "Accept" -> List("*/*")
      ).toList

      val akkaHttpResponse = HttpResponse(
        headers = collection.immutable.Seq(`Accept`(MediaRanges.`*/*`)),
        entity = HttpEntity.Strict(ContentTypes.`application/json`, ByteString("1"))
      )
      val resultHeaders =  AkkaHttpClient.convertToSdkResponseHeaders(akkaHttpResponse)

      resultHeaders.toList should contain theSameElementsAs sdkResponseHeaders
    }
  }
}
