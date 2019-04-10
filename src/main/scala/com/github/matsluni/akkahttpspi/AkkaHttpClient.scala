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

import java.util.concurrent.{CompletableFuture, TimeUnit}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity.Empty
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.MediaType.Compressible
import akka.http.scaladsl.model.RequestEntityAcceptance.Expected
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import org.slf4j.LoggerFactory
import software.amazon.awssdk.http.async._
import software.amazon.awssdk.http.SdkHttpRequest
import software.amazon.awssdk.utils.AttributeMap

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

class AkkaHttpClient(shutdownHandle: () => Unit)(implicit actorSystem: ActorSystem, ec: ExecutionContext, mat: Materializer) extends SdkAsyncHttpClient {
  import AkkaHttpClient._

  override def execute(request: AsyncExecuteRequest): CompletableFuture[Void] =
    new RunnableRequest(toAkkaRequest(request.request(), request.requestContentPublisher()), request.responseHandler()).run()

  override def close(): Unit = {
    shutdownHandle()
  }
}

object AkkaHttpClient {

  val logger = LoggerFactory.getLogger(this.getClass)

  private[akkahttpspi] def toAkkaRequest(request: SdkHttpRequest, contentPublisher: SdkHttpContentPublisher): HttpRequest = {
    val headers = convertHeaders(request.headers())
    val method = convertMethod(request.method().name())
    HttpRequest(
      method   = method,
      uri      = Uri(request.getUri.toString),
      headers  = filterContentTypeAndContentLengthHeader(headers),
      entity   = entityForMethodAndContentType(method, contentTypeHeaderToContentType(headers), contentPublisher),
      protocol = HttpProtocols.`HTTP/1.1`
    )
  }

  private[akkahttpspi] def entityForMethodAndContentType(method: HttpMethod,
                                                         contentType: ContentType,
                                                         contentPublisher: SdkHttpContentPublisher): RequestEntity =
    method.requestEntityAcceptance match {
      case Expected => contentPublisher.contentLength().asScala match {
        case Some(length) => HttpEntity(contentType, length, Source.fromPublisher(contentPublisher).map(ByteString(_)))
        case None         => HttpEntity(contentType, Source.fromPublisher(contentPublisher).map(ByteString(_)))
      }
      case _ => HttpEntity.empty(Empty.contentType)
    }

  private[akkahttpspi] def convertMethod(method: String): HttpMethod =
    HttpMethods
      .getForKeyCaseInsensitive(method)
      .getOrElse(throw new IllegalArgumentException(s"Method not configured: ${method}"))


  private[akkahttpspi] def contentTypeHeaderToContentType(headers: List[HttpHeader]): ContentType = {
    headers.find(_.lowercaseName() == "content-type").map(_.value()) match {
      case Some("application/x-amz-json-1.0") => AkkaHttpClient.xAmzJson
      case Some("application/x-amz-json-1.1") => AkkaHttpClient.xAmzJson11
      case Some("application/x-www-form-urlencoded; charset=UTF-8") => AkkaHttpClient.formUrlEncoded
      case Some("application/x-www-form-urlencoded") => AkkaHttpClient.formUrlEncoded
      case Some("application/xml") => AkkaHttpClient.applicationXml
      case Some(s) => tryCreateCustomContentType(s)
      case None => AkkaHttpClient.formUrlEncoded
    }
  }

  private[akkahttpspi] def convertHeaders(headers: java.util.Map[String, java.util.List[String]]): List[HttpHeader] = {
    headers.asScala.map { case (key, value) =>
      if (value.size() > 1 || value.size() == 0) throw new IllegalArgumentException(s"found invalid header: key: $key, Value: ${value.asScala.toList}")
      HttpHeader.parse(key, value.get(0)) match {
        case ok:Ok => ok.header
        case error:ParsingResult.Error => throw new IllegalArgumentException(s"found invalid header: ${error.errors}")
      }
    }.toList
  }

  private[akkahttpspi] def filterContentTypeAndContentLengthHeader(headers: Seq[HttpHeader]): collection.immutable.Seq[HttpHeader] =
    headers.filterNot(h => h.lowercaseName() == "content-type" || h.lowercaseName() == "content-length").toList

  private[akkahttpspi] def tryCreateCustomContentType(contentTypeStr: String): ContentType = {
    logger.info(s"Try to parse content type from $contentTypeStr")
    val mainAndsubType = contentTypeStr.split('/')
    if (mainAndsubType.length == 2)
      ContentType(MediaType.customBinary(mainAndsubType(0), mainAndsubType(1), Compressible))
    else throw new RuntimeException(s"Could not parse custom content type '$contentTypeStr'.")
  }


  def builder() = AkkaHttpClientBuilder()

  case class AkkaHttpClientBuilder(private val actorSystem: Option[ActorSystem] = None,
                                   private val executionContext: Option[ExecutionContext] = None) extends SdkAsyncHttpClient.Builder[AkkaHttpClientBuilder] {
    def buildWithDefaults(attributeMap: AttributeMap): SdkAsyncHttpClient = {
      implicit val as = actorSystem.getOrElse(ActorSystem("aws-akka-http"))
      implicit val ec = executionContext.getOrElse(as.dispatcher)
      val mat: ActorMaterializer = ActorMaterializer()

      val shutdownhandleF = () => {
        if (actorSystem.isEmpty) {
          Await.result(Http().shutdownAllConnectionPools().flatMap(_ => as.terminate()), Duration.apply(10, TimeUnit.SECONDS))
          mat.shutdown()
        }
      }
      new AkkaHttpClient(shutdownhandleF)(as, ec, mat)
    }
    def withActorSystem(actorSystem: ActorSystem): AkkaHttpClientBuilder = copy(actorSystem = Some(actorSystem))
    def withExecutionContext(executionContext: ExecutionContext): AkkaHttpClientBuilder = copy(executionContext = Some(executionContext))
  }

  lazy val xAmzJson = ContentType(MediaType.customBinary("application", "x-amz-json-1.0", Compressible))
  lazy val xAmzJson11 = ContentType(MediaType.customBinary("application", "x-amz-json-1.1", Compressible))
  lazy val formUrlEncoded = ContentType(MediaType.applicationWithOpenCharset("x-www-form-urlencoded"), HttpCharset.custom("utf-8"))
  lazy val applicationXml = ContentType(MediaType.customBinary("application", "xml", Compressible))
}
