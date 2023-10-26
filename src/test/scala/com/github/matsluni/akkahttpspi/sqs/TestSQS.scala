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

package com.github.matsluni.akkahttpspi.sqs

import akka.http.scaladsl.model.HttpProtocols
import com.github.matsluni.akkahttpspi.AkkaHttpClient.AkkaHttpClientBuilder
import com.github.matsluni.akkahttpspi.{AkkaHttpAsyncHttpService, ElasticMQSQSBaseAwsClientTest}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model._

import java.util.concurrent.CompletionException

// switched to use ElasticMQ container instead of Localstack due to https://github.com/localstack/localstack/issues/8545
class TestSQS extends ElasticMQSQSBaseAwsClientTest[SqsAsyncClient] {
  "Async SQS client" should {

    "publish a message to a queue" in withClient() { implicit client =>
      client.createQueue(CreateQueueRequest.builder().queueName("foo").build()).join()
      client.sendMessage(SendMessageRequest.builder().queueUrl(s"$endpoint/queue/foo").messageBody("123").build()).join()
      val receivedMessage = client.receiveMessage(ReceiveMessageRequest.builder().queueUrl(s"$endpoint/queue/foo").maxNumberOfMessages(1).build()).join()
      receivedMessage.messages().get(0).body() should be("123")
    }

    "delete a message" in withClient() { implicit client =>
      client.createQueue(CreateQueueRequest.builder().queueName("foo").build()).join()
      client.sendMessage(SendMessageRequest.builder().queueUrl(s"$endpoint/queue/foo").messageBody("123").build()).join()

      val receivedMessages = client.receiveMessage(ReceiveMessageRequest.builder().queueUrl(s"$endpoint/queue/foo").maxNumberOfMessages(1).build()).join

      client.deleteMessage(DeleteMessageRequest.builder().queueUrl(s"$endpoint/queue/foo").receiptHandle(receivedMessages.messages().get(0).receiptHandle()).build()).join()

      val receivedMessage = client.receiveMessage(ReceiveMessageRequest.builder().queueUrl(s"$endpoint/queue/foo").maxNumberOfMessages(1).waitTimeSeconds(1).build()).join()
      receivedMessage.messages() shouldBe java.util.Collections.EMPTY_LIST
    }

    //softwaremill/elasticmq-native does not support HTTP/2
    "work with HTTP/2" in withClient(_.withProtocol(HttpProtocols.`HTTP/2.0`)) { implicit client =>
      the [CompletionException] thrownBy {
        val result = client.listQueues().join()
        result.queueUrls() should not be null
      } should have message "software.amazon.awssdk.services.sqs.model.SqsException: null (Service: Sqs, Status Code: 505, Request ID: null)"
    }

  }

  private def withClient(builderFn: AkkaHttpClientBuilder => AkkaHttpClientBuilder = identity)(testCode: SqsAsyncClient => Any): Any = {

    val akkaClient = builderFn(new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory()).build()

    val client = SqsAsyncClient
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

  override def service: String = "sqs"
}
