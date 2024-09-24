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

import akka.http.scaladsl.model.headers.`Content-Type`
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import software.amazon.awssdk.http.SdkHttpConfigurationOption
import software.amazon.awssdk.utils.AttributeMap

import scala.concurrent.duration._
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

    "build() should use default ConnectionPoolSettings" in {
      val akkaClient: AkkaHttpClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory()
        .build()
        .asInstanceOf[AkkaHttpClient]

      akkaClient.connectionSettings shouldBe ConnectionPoolSettings(ConfigFactory.load())
    }

    "withConnectionPoolSettingsBuilderFromAttributeMap().buildWithDefaults() should propagate configuration options" in {
      val attributeMap = AttributeMap.builder()
        .put(SdkHttpConfigurationOption.CONNECTION_TIMEOUT, toJava(1.second))
        .put(SdkHttpConfigurationOption.CONNECTION_MAX_IDLE_TIMEOUT, toJava(2.second))
        .put(SdkHttpConfigurationOption.MAX_CONNECTIONS, Integer.valueOf(3))
        .put(SdkHttpConfigurationOption.CONNECTION_TIME_TO_LIVE, toJava(4.second))
        .build()
      val akkaClient: AkkaHttpClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory()
        .withConnectionPoolSettingsBuilderFromAttributeMap()
        .buildWithDefaults(attributeMap)
        .asInstanceOf[AkkaHttpClient]

      akkaClient.connectionSettings.connectionSettings.connectingTimeout shouldBe 1.second
      akkaClient.connectionSettings.connectionSettings.idleTimeout shouldBe 2.seconds
      akkaClient.connectionSettings.maxConnections shouldBe 3
      akkaClient.connectionSettings.maxConnectionLifetime shouldBe 4.seconds
    }

    "withConnectionPoolSettingsBuilderFromAttributeMap().build() should fallback to GLOBAL_HTTP_DEFAULTS" in {
      val akkaClient: AkkaHttpClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory()
        .withConnectionPoolSettingsBuilderFromAttributeMap()
        .build()
        .asInstanceOf[AkkaHttpClient]

      akkaClient.connectionSettings.connectionSettings.connectingTimeout shouldBe
        toScala(SdkHttpConfigurationOption.GLOBAL_HTTP_DEFAULTS.get(SdkHttpConfigurationOption.CONNECTION_TIMEOUT))
      akkaClient.connectionSettings.connectionSettings.idleTimeout shouldBe
        toScala(SdkHttpConfigurationOption.GLOBAL_HTTP_DEFAULTS.get(SdkHttpConfigurationOption.CONNECTION_MAX_IDLE_TIMEOUT))
      akkaClient.connectionSettings.maxConnections shouldBe
        SdkHttpConfigurationOption.GLOBAL_HTTP_DEFAULTS.get(SdkHttpConfigurationOption.MAX_CONNECTIONS).intValue()
      infiniteToZero(akkaClient.connectionSettings.maxConnectionLifetime) shouldBe
        SdkHttpConfigurationOption.GLOBAL_HTTP_DEFAULTS.get(SdkHttpConfigurationOption.CONNECTION_TIME_TO_LIVE)
    }

    "withConnectionPoolSettingsBuilder().build() should use passed connectionPoolSettings builder" in {
      val connectionPoolSettings = ConnectionPoolSettings(ConfigFactory.load())
        .withConnectionSettings(
          ClientConnectionSettings(ConfigFactory.load())
            .withConnectingTimeout(1.second)
            .withIdleTimeout(2.seconds)
        )
        .withMaxConnections(3)
        .withMaxConnectionLifetime(4.seconds)

      val akkaClient: AkkaHttpClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory()
        .withConnectionPoolSettingsBuilder((_, _) => connectionPoolSettings)
        .build()
        .asInstanceOf[AkkaHttpClient]

      akkaClient.connectionSettings shouldBe connectionPoolSettings
    }

    "withConnectionPoolSettings().build() should use passed ConnectionPoolSettings" in {
      val connectionPoolSettings = ConnectionPoolSettings(ConfigFactory.load())
        .withConnectionSettings(
          ClientConnectionSettings(ConfigFactory.load())
            .withConnectingTimeout(1.second)
            .withIdleTimeout(2.seconds)
        )
        .withMaxConnections(3)
        .withMaxConnectionLifetime(4.seconds)
      val akkaClient: AkkaHttpClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory()
        .withConnectionPoolSettings(connectionPoolSettings)
        .build()
        .asInstanceOf[AkkaHttpClient]

      akkaClient.connectionSettings shouldBe connectionPoolSettings
    }
  }

  private def infiniteToZero(duration: scala.concurrent.duration.Duration): java.time.Duration = duration match {
    case _: scala.concurrent.duration.Duration.Infinite => java.time.Duration.ZERO
    case duration: FiniteDuration => toJava(duration)
  }

  private def toJava(duration: scala.concurrent.duration.FiniteDuration): java.time.Duration = {
    java.time.Duration.ofNanos(duration.toNanos)
  }

  private def toScala(duration: java.time.Duration): scala.concurrent.duration.FiniteDuration = {
    scala.concurrent.duration.Duration.fromNanos(duration.toNanos)
  }
}
