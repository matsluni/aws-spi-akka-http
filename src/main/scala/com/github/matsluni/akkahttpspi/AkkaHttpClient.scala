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

import akka.actor.{ActorSystem, ClassicActorSystemProvider}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.MediaType.Compressible
import akka.http.scaladsl.model.RequestEntityAcceptance.Expected
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Content-Length`, `Content-Type`}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.scaladsl.Source
import akka.stream.{Materializer, SystemMaterializer}
import akka.util.ByteString
import org.slf4j.LoggerFactory
import software.amazon.awssdk.http.async._
import software.amazon.awssdk.http.{SdkHttpConfigurationOption, SdkHttpRequest}
import software.amazon.awssdk.utils.AttributeMap

import scala.collection.immutable
import scala.jdk.CollectionConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

class AkkaHttpClient(shutdownHandle: () => Unit, private[akkahttpspi] val connectionSettings: ConnectionPoolSettings)(implicit actorSystem: ActorSystem, ec: ExecutionContext, mat: Materializer) extends SdkAsyncHttpClient {
  import AkkaHttpClient._

  lazy val runner = new RequestRunner()

  override def execute(request: AsyncExecuteRequest): CompletableFuture[Void] = {
    val akkaHttpRequest = toAkkaRequest(request.request(), request.requestContentPublisher())
    runner.run(
      () => Http().singleRequest(akkaHttpRequest, settings = connectionSettings),
      request.responseHandler()
    )
  }

  override def close(): Unit = {
    shutdownHandle()
  }

  override def clientName(): String = "akka-http"
}

object AkkaHttpClient {

  val logger = LoggerFactory.getLogger(this.getClass)

  private[akkahttpspi] def toAkkaRequest(request: SdkHttpRequest, contentPublisher: SdkHttpContentPublisher): HttpRequest = {
    val (contentTypeHeader, reqheaders) = convertHeaders(request.headers())
    val method = convertMethod(request.method().name())
    HttpRequest(
      method   = method,
      uri      = Uri(request.getUri.toString),
      headers  = reqheaders,
      entity   = entityForMethodAndContentType(method, contentTypeHeaderToContentType(contentTypeHeader), contentPublisher),
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
      case _ => HttpEntity.Empty
    }

  private[akkahttpspi] def convertMethod(method: String): HttpMethod =
    HttpMethods
      .getForKeyCaseInsensitive(method)
      .getOrElse(throw new IllegalArgumentException(s"Method not configured: $method"))


  private[akkahttpspi] def contentTypeHeaderToContentType(contentTypeHeader: Option[HttpHeader]): ContentType =
    contentTypeHeader
      .map(_.value())
      .map(v => contentTypeMap.getOrElse(v, tryCreateCustomContentType(v)))
      // Its allowed to not have a content-type: https://www.w3.org/Protocols/rfc2616/rfc2616-sec7.html#sec7.2.1
      //
      //  Any HTTP/1.1 message containing an entity-body SHOULD include a Content-Type header field defining the media type
      //  of that body. If and only if the media type is not given by a Content-Type field, the recipient MAY attempt to
      //  guess the media type via inspection of its content and/or the name extension(s) of the URI used to identify the
      //  resource. If the media type remains unknown, the recipient SHOULD treat it as type "application/octet-stream".
      //
      .getOrElse(ContentTypes.NoContentType)

  // This method converts the headers to Akka-http headers and drops content-length and returns content-type separately
  private[akkahttpspi] def convertHeaders(headers: java.util.Map[String, java.util.List[String]]): (Option[HttpHeader], immutable.Seq[HttpHeader]) =
    headers.asScala.foldLeft((Option.empty[HttpHeader], List.empty[HttpHeader])) { case ((ctHeader, hdrs), header) =>
      val (headerName, headerValue) = header
      if (headerValue.size() != 1) {
        throw new IllegalArgumentException(s"Found invalid header: key: $headerName, Value: ${headerValue.asScala.toList}.")
      }
      // skip content-length as it will be calculated by akka-http itself and must not be provided in the request headers
      if (`Content-Length`.lowercaseName == headerName.toLowerCase) (ctHeader, hdrs)
      else {
        HttpHeader.parse(headerName, headerValue.get(0)) match {
          case ok: Ok =>
            // return content-type separately as it will be used to calculate ContentType, which is used on HttpEntity
            if (ok.header.lowercaseName() == `Content-Type`.lowercaseName) (Some(ok.header), hdrs)
            else (ctHeader, (hdrs :+ ok.header))
          case error: ParsingResult.Error => throw new IllegalArgumentException(s"Found invalid header: ${error.errors}.")
        }
      }
    }

  private[akkahttpspi] def tryCreateCustomContentType(contentTypeStr: String): ContentType = {
    logger.debug(s"Try to parse content type from $contentTypeStr")
    val mainAndsubType = contentTypeStr.split('/')
    if (mainAndsubType.length == 2)
      ContentType(MediaType.customBinary(mainAndsubType(0), mainAndsubType(1), Compressible))
    else throw new RuntimeException(s"Could not parse custom content type '$contentTypeStr'.")
  }

  // based on NettyNioAsyncHttpClient and ApacheHttpClient
  // https://github.com/search?q=repo%3Aaws%2Faws-sdk-java-v2+SdkHttpConfigurationOption+path%3A%2F%5Ehttp-clients%5C%2Fnetty-nio-client%5C%2Fsrc%5C%2Fmain%2F&type=code
  // https://github.com/search?q=repo%3Aaws%2Faws-sdk-java-v2+SdkHttpConfigurationOption+path%3A%2F%5Ehttp-clients%5C%2Fapache-client%5C%2Fsrc%5C%2Fmain%2F&type=code
  private[akkahttpspi] def buildConnectionPoolSettings(base: ConnectionPoolSettings,attributeMap: AttributeMap): ConnectionPoolSettings = {
    def zeroToInfinite(duration: java.time.Duration): scala.concurrent.duration.Duration =
      if (duration.isZero) scala.concurrent.duration.Duration.Inf
      else toScala(duration)

    def toScala(duration: java.time.Duration): scala.concurrent.duration.FiniteDuration =
      scala.concurrent.duration.Duration.fromNanos(duration.toNanos)

    base
      .withUpdatedConnectionSettings(s =>
        s.withConnectingTimeout(toScala(attributeMap.get(SdkHttpConfigurationOption.CONNECTION_TIMEOUT)))
          .withIdleTimeout(toScala(attributeMap.get(SdkHttpConfigurationOption.CONNECTION_MAX_IDLE_TIMEOUT)))
      )
      .withMaxConnections(attributeMap.get(SdkHttpConfigurationOption.MAX_CONNECTIONS).intValue())
      .withMaxConnectionLifetime(zeroToInfinite(attributeMap.get(SdkHttpConfigurationOption.CONNECTION_TIME_TO_LIVE)))
  }

  def builder() = AkkaHttpClientBuilder()

  case class AkkaHttpClientBuilder(private val actorSystem: Option[ActorSystem] = None,
                                   private val executionContext: Option[ExecutionContext] = None,
                                   private val connectionPoolSettings: Option[ConnectionPoolSettings] = None,
                                   private val connectionPoolSettingsBuilder: (ConnectionPoolSettings, AttributeMap) => ConnectionPoolSettings = (c, _) => c
                                  ) extends SdkAsyncHttpClient.Builder[AkkaHttpClientBuilder] {

    def buildWithDefaults(serviceDefaults: AttributeMap): SdkAsyncHttpClient = {
      implicit val as = actorSystem.getOrElse(ActorSystem("aws-akka-http"))
      implicit val ec = executionContext.getOrElse(as.dispatcher)
      val mat: Materializer = SystemMaterializer(as).materializer

      val resolvedOptions = serviceDefaults.merge(SdkHttpConfigurationOption.GLOBAL_HTTP_DEFAULTS);

      val cps = connectionPoolSettingsBuilder(connectionPoolSettings.getOrElse(ConnectionPoolSettings(as)), resolvedOptions)
      val shutdownhandleF = () => {
        if (actorSystem.isEmpty) {
          Await.result(Http().shutdownAllConnectionPools().flatMap(_ => as.terminate()), Duration.apply(10, TimeUnit.SECONDS))
        }
        ()
      }
      new AkkaHttpClient(shutdownhandleF, cps)(as, ec, mat)
    }
    def withActorSystem(actorSystem: ActorSystem): AkkaHttpClientBuilder = copy(actorSystem = Some(actorSystem))
    def withActorSystem(actorSystem: ClassicActorSystemProvider): AkkaHttpClientBuilder = copy(actorSystem = Some(actorSystem.classicSystem))
    def withExecutionContext(executionContext: ExecutionContext): AkkaHttpClientBuilder = copy(executionContext = Some(executionContext))
    def withConnectionPoolSettings(connectionPoolSettings: ConnectionPoolSettings): AkkaHttpClientBuilder = copy(connectionPoolSettings = Some(connectionPoolSettings))
    def withConnectionPoolSettingsBuilder(connectionPoolSettingsBuilder: (ConnectionPoolSettings, AttributeMap) => ConnectionPoolSettings): AkkaHttpClientBuilder =
      copy(connectionPoolSettingsBuilder = connectionPoolSettingsBuilder)
    def withConnectionPoolSettingsBuilderFromAttributeMap(): AkkaHttpClientBuilder = copy(connectionPoolSettingsBuilder = buildConnectionPoolSettings)
  }

  lazy val xAmzJson = ContentType(MediaType.customBinary("application", "x-amz-json-1.0", Compressible))
  lazy val xAmzJson11 = ContentType(MediaType.customBinary("application", "x-amz-json-1.1", Compressible))
  lazy val xAmzCbor11 = ContentType(MediaType.customBinary("application", "x-amz-cbor-1.1", Compressible))
  lazy val formUrlEncoded = ContentType(MediaType.applicationWithOpenCharset("x-www-form-urlencoded"), HttpCharset.custom("utf-8"))
  lazy val applicationXml = ContentType(MediaType.customBinary("application", "xml", Compressible))

  lazy val contentTypeMap: collection.immutable.Map[String, ContentType] = collection.immutable.Map(
    "application/x-amz-json-1.0" -> xAmzJson,
    "application/x-amz-json-1.1" -> xAmzJson11,
    "application/x-amz-cbor-1.1" -> xAmzCbor11, // used by Kinesis
    "application/x-www-form-urlencoded; charset-UTF-8" -> formUrlEncoded,
    "application/x-www-form-urlencoded" -> formUrlEncoded,
    "application/xml" -> applicationXml
  )
}
