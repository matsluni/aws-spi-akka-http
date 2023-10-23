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
import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.`User-Agent`
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import software.amazon.awssdk.http.SdkHttpResponse
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler

import java.io.IOException
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class RequestRunnerSpec extends AnyWordSpec with Matchers with OptionValues {
  "Check headers are present from response" in {
    implicit val system = ActorSystem("test")
    implicit val ec = system.dispatcher
    val response = HttpResponse(entity = HttpEntity("Ok"), headers = `User-Agent`("Mozilla") :: Nil)
    val runner = new RequestRunner()
    val handler = new MyHeaderHandler()
    val resp = runner.run(() => Future.successful(response), handler)
    resp.join()

    handler.responseHeaders.headers().asScala.get("User-Agent").value.asScala.headOption.value shouldBe "Mozilla"
    handler.responseHeaders.headers().asScala.get("Content-Type").value.asScala.headOption.value shouldBe "text/plain; charset=UTF-8"
    handler.responseHeaders.headers().asScala.get("Content-Length").value.asScala.headOption.value shouldBe "2"
  }

  "decorate UnexpectedConnectionClosureException" in {
    //instantiate akka.http.impl.engine.client.OutgoingConnectionBlueprint.UnexpectedConnectionClosureException using reflection
    val clazz = Class.forName("akka.http.impl.engine.client.OutgoingConnectionBlueprint$UnexpectedConnectionClosureException")
    val e = clazz.getDeclaredConstructor(classOf[Int]).newInstance(Integer.valueOf(1)).asInstanceOf[Throwable]
    val ioexception = RequestRunner.decorateException(e)
    ioexception shouldBe a[IOException]
    ioexception.getMessage shouldBe "akka.http.impl.engine.client.OutgoingConnectionBlueprint$UnexpectedConnectionClosureException: The http server closed the connection unexpectedly before delivering responses for 1 outstanding requests"
  }

  "decorate StreamTcpException" in {
    val e = new akka.stream.StreamTcpException("The connection closed with error: Connection reset")
    val ioexception = RequestRunner.decorateException(e)
    ioexception shouldBe a[IOException]
    ioexception.getMessage shouldBe "akka.stream.StreamTcpException: The connection closed with error: Connection reset"
  }
  "decorate IllegalStateException with 'Connection was shutdown' reason" in {
    val e = new IllegalStateException("Connection was shutdown.")
    val ioexception = RequestRunner.decorateException(e)
    ioexception shouldBe a[IOException]
    ioexception.getMessage shouldBe "java.lang.IllegalStateException: Connection was shutdown."
  }



  class MyHeaderHandler() extends SdkAsyncHttpResponseHandler {
    private val headers = new AtomicReference[SdkHttpResponse](null)
    def responseHeaders = headers.get()
    override def onHeaders(headers: SdkHttpResponse): Unit = this.headers.set(headers)
    override def onStream(stream: Publisher[ByteBuffer]): Unit = stream.subscribe(new Subscriber[ByteBuffer] {
      override def onSubscribe(s: Subscription): Unit = s.request(1000)
      override def onNext(t: ByteBuffer): Unit = ()
      override def onError(t: Throwable): Unit = ()
      override def onComplete(): Unit = ()
    })
    override def onError(error: Throwable): Unit = ()
  }
}
