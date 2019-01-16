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

import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.http.SdkHttpFullResponse
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.Success

class RunnableRequest(httpRequest: HttpRequest, handler: SdkAsyncHttpResponseHandler)(implicit actorSystem: ActorSystem, ec: ExecutionContext, mat: Materializer) {

  val logger = LoggerFactory.getLogger(this.getClass)

  def run(): CompletableFuture[Void] = {
    val p = Promise[Void]()
    Http().singleRequest(httpRequest).foreach { res =>

      val response = SdkHttpFullResponse.builder()
        .headers(res.headers.groupBy(_.name()).map{ case (k, v) => k -> v.map(_.value()).asJava }.asJava)
        .statusCode(res.status.intValue())
        .statusText(res.status.reason)
        .build

      handler.onHeaders(response)

      val dataPublisher = res.entity.dataBytes.map(_.asByteBuffer).runWith(Sink.asPublisher(fanout = false))
      handler.onStream(new PublisherDecorator(dataPublisher, p))
    }
    FutureConverters.toJava(p.future).toCompletableFuture
  }

  private class PublisherDecorator(protected val publisherDelegate: Publisher[ByteBuffer],
                                   protected val resultPromise: Promise[Void]) extends Publisher[ByteBuffer] {

    override def subscribe(s: Subscriber[_ >: ByteBuffer]): Unit =
      publisherDelegate.subscribe(new SubscriberDecorator(s, resultPromise))

    private class SubscriberDecorator(protected val subscriberDelegate: Subscriber[_ >: ByteBuffer],
                                      protected val resultPromise: Promise[Void]) extends Subscriber[ByteBuffer] {

      override def onSubscribe(s: Subscription): Unit = subscriberDelegate.onSubscribe(s)

      override def onNext(t: ByteBuffer): Unit = subscriberDelegate.onNext(t)

      override def onError(t: Throwable): Unit = {
        subscriberDelegate.onError(t)
        resultPromise.failure(t)
      }

      override def onComplete(): Unit = {
        subscriberDelegate.onComplete()
        resultPromise.complete(Success(null))
      }
    }
  }
}
