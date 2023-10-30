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

package com.github.matsluni.akkahttpspi.sns

import akka.http.scaladsl.model.HttpProtocols
import com.github.matsluni.akkahttpspi.AkkaHttpClient.AkkaHttpClientBuilder
import com.github.matsluni.akkahttpspi.{AkkaHttpAsyncHttpService, LocalstackBaseAwsClientTest}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.{CreateTopicRequest, PublishRequest}

class TestSNS extends LocalstackBaseAwsClientTest[SnsAsyncClient] {

  "Async SNS client" should {
    "publish a message to a topic" in withClient() { implicit client =>
      val arn = client.createTopic(CreateTopicRequest.builder().name("topic-example").build()).join().topicArn()
      val result = client.publish(PublishRequest.builder().message("a message").topicArn(arn).build()).join()

      result.messageId() should not be null
    }

    "work with HTTP/2" ignore withClient(_.withProtocol(HttpProtocols.`HTTP/2.0`)) { implicit client =>
      val result = client.listTopics().join()
      result.topics() should not be null
    }
  }

  private def withClient(builderFn: AkkaHttpClientBuilder => AkkaHttpClientBuilder = identity)(testCode: SnsAsyncClient => Any): Any = {

    val akkaClient = builderFn(new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory()).build()

    val client = SnsAsyncClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
      .httpClient(akkaClient)
      .region(defaultRegion)
      .endpointOverride(endpoint)
      .build()

    try {
      testCode(client)
    }
    finally { // clean up
      akkaClient.close()
      client.close()
    }
  }

  override def service: String = "sns"
}
