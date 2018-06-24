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

import java.net.URI

import com.github.matsluni.akkahttpspi.AkkaHttpAsyncHttpService
import org.elasticmq.rest.sqs.SQSRestServerBuilder
import org.scalatest.{Matchers, WordSpec}
import software.amazon.awssdk.auth.credentials.{AwsCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.client.builder.ClientAsyncHttpConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SQSAsyncClient
import software.amazon.awssdk.services.sqs.model._
import software.amazon.awssdk.utils.AttributeMap

class TestSQS extends WordSpec with Matchers {

  val baseUrl = "http://localhost:9324"

  def withClient(testCode: SQSAsyncClient => Any) {
    val server = SQSRestServerBuilder.withPort(9324).withInterface("localhost").start()
      server.waitUntilStarted()

    val akkaClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory().createHttpClientWithDefaults(AttributeMap.empty())

    val client = SQSAsyncClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsCredentials.create("x", "x")))
      .region(Region.of("elasticmq"))
      .endpointOverride(new URI(baseUrl))
      .asyncHttpConfiguration(
        ClientAsyncHttpConfiguration.builder().httpClient(akkaClient).build())
      .build()

    try {
      testCode(client)
    }
    finally { // clean up
      server.stopAndWait()
      akkaClient.close()
      client.close()
    }
  }

  "Async SQS client" should {

    "publish a message to a queue" in withClient { implicit client =>
      client.createQueue(CreateQueueRequest.builder().queueName("foo").build()).join()
      client.sendMessage(SendMessageRequest.builder().queueUrl(s"$baseUrl/queue/foo").messageBody("123").build()).join()
      val receivedMessage = client.receiveMessage(ReceiveMessageRequest.builder().queueUrl(s"$baseUrl/queue/foo").maxNumberOfMessages(1).build()).join()
      receivedMessage.messages().get(0).body() should be ("123")
    }

    "delete a message" in withClient { implicit client =>
      client.createQueue(CreateQueueRequest.builder().queueName("foo").build()).join()
      client.sendMessage(SendMessageRequest.builder().queueUrl(s"$baseUrl/queue/foo").messageBody("123").build()).join()

      val receivedMessages = client.receiveMessage(ReceiveMessageRequest.builder().queueUrl(s"$baseUrl/queue/foo").maxNumberOfMessages(1).build()).join

      client.deleteMessage(DeleteMessageRequest.builder().queueUrl(s"$baseUrl/queue/foo").receiptHandle(receivedMessages.messages().get(0).receiptHandle()).build()).join()

      val receivedMessage = client.receiveMessage(ReceiveMessageRequest.builder().queueUrl(s"$baseUrl/queue/foo").maxNumberOfMessages(1).waitTimeSeconds(1).build()).join()
      receivedMessage.messages() should be (null)
    }

  }

}
