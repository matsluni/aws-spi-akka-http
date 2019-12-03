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

import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.{HttpHeader, MediaTypes}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AkkaHttpClientSpec extends AnyWordSpec with Matchers {

  "AkkaHttpClient" should {

    "parse custom content type" in {
      val contentTypeStr = "application/xml"
      val contentType = AkkaHttpClient.tryCreateCustomContentType(contentTypeStr)
      contentType.mediaType should be (MediaTypes.`application/xml`)
    }

    "remove 'ContentType' and 'ContentLength' header from headers" in {
      val Ok(contentType, pErr1) = HttpHeader.parse("Content-Type", "application/xml")
      val Ok(contentLength, pErr2) = HttpHeader.parse("Content-Length", "123")

      val filteredHeaders = AkkaHttpClient.filterContentTypeAndContentLengthHeader(contentType :: contentLength :: Nil)

      pErr1 should have size 0
      pErr2 should have size 0
      filteredHeaders should have size 0
    }
  }
}
