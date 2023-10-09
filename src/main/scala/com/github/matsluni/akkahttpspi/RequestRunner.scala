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
import akka.http.scaladsl.model.{ContentTypes, HttpResponse}
import akka.http.scaladsl.model.headers.{`Content-Length`, `Content-Type`}
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.http.SdkHttpFullResponse
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler

import java.io.IOException
import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

class RequestRunner()(implicit sys: ActorSystem, ec: ExecutionContext, mat: Materializer) {

  val logger = LoggerFactory.getLogger(this.getClass)

  def run(runRequest: () => Future[HttpResponse],
          handler: SdkAsyncHttpResponseHandler): CompletableFuture[Void] = {
    val result = runRequest().flatMap { response =>
      handler.onHeaders(toSdkHttpFullResponse(response))

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

    result.failed.foreach(e => handler.onError(decorateException(e)))
    FutureConverters.toJava(result.map(_ => null: Void)).toCompletableFuture
  }

  private[akkahttpspi] def toSdkHttpFullResponse(response: HttpResponse): SdkHttpFullResponse = {
    SdkHttpFullResponse.builder()
      .headers(convertToSdkResponseHeaders(response).map { case (k, v) => k -> v.asJava }.asJava)
      .statusCode(response.status.intValue())
      .statusText(response.status.reason)
      .build
  }

  //Decorate akka-http exceptions to exceptions understood by the AWS SDK so that they are automatically retried by the default retry policy
  //This was inspired in NettyUtils.decorateException (https://github.com/aws/aws-sdk-java-v2/blob/13985e0668a9a0b12ad331644e3c4fd1385c2cd7/http-clients/netty-nio-client/src/main/java/software/amazon/awssdk/http/nio/netty/internal/utils/NettyUtils.java#L67-L80)
  private[akkahttpspi] def decorateException(e: Throwable): Throwable = e match {
    //workaround for akka.http.impl.engine.client.OutgoingConnectionBlueprint.UnexpectedConnectionClosureException being private
    //see more details in https://github.com/akka/akka-http/issues/3481
    case e if e.getMessage.startsWith("The http server closed the connection unexpectedly") => new IOException(e)
    case e => e
  }

  private[akkahttpspi] def convertToSdkResponseHeaders(response: HttpResponse): Map[String, Seq[String]] = {
    val contentType = response.entity.contentType match {
      case ContentTypes.NoContentType => Map.empty[String, List[String]]
      case contentType => Map(`Content-Type`.name -> List(contentType.value))
    }

    val contentLength = response.entity.contentLengthOption
      .map(length => `Content-Length`.name -> List(length.toString)).toMap

    val headers = response.headers.groupBy(_.name()).map { case (k, v) => k -> v.map(_.value()) }

    headers ++ contentType ++ contentLength
  }
}
