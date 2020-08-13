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

import java.util.concurrent.CompletableFuture

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.http.SdkHttpFullResponse
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler

import scala.compat.java8.FutureConverters
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

class RequestRunner(connectionPoolSettings: ConnectionPoolSettings)(implicit sys: ActorSystem,
                                                          ec: ExecutionContext,
                                                          mat: Materializer) {
  val logger = LoggerFactory.getLogger(this.getClass)

  def run(httpRequest: HttpRequest,
          handler: SdkAsyncHttpResponseHandler): CompletableFuture[Void] = {
    val result = Http()
      .singleRequest(httpRequest, settings = connectionPoolSettings)
      .flatMap { response =>
        val sdkResponse = SdkHttpFullResponse.builder()
          .headers(response.headers.groupBy(_.name()).map{ case (k, v) => k -> v.map(_.value()).asJava }.asJava)
          .statusCode(response.status.intValue())
          .statusText(response.status.reason)
          .build

        handler.onHeaders(sdkResponse)

        val (complete, publisher) = response
          .entity
          .dataBytes
          .map(_.asByteBuffer)
          .alsoToMat(Sink.ignore)(Keep.right)
          .toMat(Sink.asPublisher(fanout = false))(Keep.both)
          .run()

        handler.onStream(publisher)

        complete
      }

    result.failed.foreach(handler.onError)
    FutureConverters.toJava(result.map(_ => null: Void)).toCompletableFuture
  }
}
