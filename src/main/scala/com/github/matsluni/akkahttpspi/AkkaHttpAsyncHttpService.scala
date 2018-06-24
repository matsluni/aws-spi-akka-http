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

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import software.amazon.awssdk.http.async.{SdkAsyncHttpClient, SdkAsyncHttpClientFactory, SdkAsyncHttpService}
import software.amazon.awssdk.utils.AttributeMap

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

class AkkaHttpAsyncHttpService extends SdkAsyncHttpService {
  override def createAsyncHttpClientFactory(): SdkAsyncHttpClientFactory = new AkkaAsyncHttpClientFactory
}

class AkkaAsyncHttpClientFactory extends SdkAsyncHttpClientFactory {
  override def createHttpClientWithDefaults(serviceDefaults: AttributeMap): SdkAsyncHttpClient = AkkaAsyncHttpClientFactoryBuilder().build()
  case class AkkaAsyncHttpClientFactoryBuilder(private val actorSystem: Option[ActorSystem] = None,
                                               private val executionContext: Option[ExecutionContext] = None) {

    def withActorSystem(actorSystem: ActorSystem): AkkaAsyncHttpClientFactoryBuilder = copy(actorSystem = Some(actorSystem))
    def withExecutionContext(executionContext: ExecutionContext): AkkaAsyncHttpClientFactoryBuilder = copy(executionContext = Some(executionContext))
    def build(): SdkAsyncHttpClient = {
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
  }

}
