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

import com.github.dockerjava.api.model.Frame
import com.github.matsluni.akkahttpspi.{AkkaHttpAsyncHttpService, LocalstackBaseAwsClientTest}
import org.testcontainers.shaded.com.github.dockerjava.core.InvocationBuilder.AsyncResultCallback
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.retry.RetryPolicy
import software.amazon.awssdk.core.retry.backoff.FixedDelayBackoffStrategy
import software.amazon.awssdk.services.sns.model.{CreateTopicRequest, PublishRequest}
import software.amazon.awssdk.services.sns.{SnsAsyncClient, SnsAsyncClientBuilder}

import java.time.Duration
import scala.concurrent.duration.DurationInt

class TestSNS extends LocalstackBaseAwsClientTest[SnsAsyncClient] {

  "Async SNS client" should {
    "publish a message to a topic" in withClient { implicit client =>
      val arn = client.createTopic(CreateTopicRequest.builder().name("topic-example").build()).join().topicArn()
      val result = client.publish(PublishRequest.builder().message("a message").topicArn(arn).build()).join()

      result.messageId() should not be null
    }
  }

  "Retryable SNS client" should {
    "retry" in withLongRetriesClient { client =>
      //the localstack process wil take a few seconds to restart
      killLocalstackProcess()

      val startTime = System.currentTimeMillis()
      val listTopicsFuture = client.listTopics()

      //If debug logs on "software.amazon" are enabled, one should see the retry messages
      //"Retryable error detected. Will retry in 1000ms. Request attempt number 2"
      listTopicsFuture.join()

      val duration = System.currentTimeMillis() - startTime
      duration shouldBe > (3.seconds.toMillis)
    }
  }

  def withClient(testCode: SnsAsyncClient => Any): Any = withCustomClient(identity)(testCode)
  def withCustomClient(builderFn: SnsAsyncClientBuilder => SnsAsyncClientBuilder)(testCode: SnsAsyncClient => Any): Any = {

    val akkaClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory().build()

    val builder = SnsAsyncClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
      .httpClient(akkaClient)
      .region(defaultRegion)
      .endpointOverride(endpoint)

    val client = builderFn(builder).build()

    try {
      testCode(client)
    }
    finally { // clean up
      akkaClient.close()
      client.close()
    }
  }

  /** Uses an SnsAsyncClient will retry more times than the default retry policy (3 retries) and with a bigger delay between retries */
  private def withLongRetriesClient(testCode: SnsAsyncClient => Any) = withCustomClient(b =>
    b.overrideConfiguration { (b: ClientOverrideConfiguration.Builder) =>
      b.retryPolicy(RetryPolicy.builder()
        .numRetries(6)
        .backoffStrategy(FixedDelayBackoffStrategy.create(Duration.ofSeconds(1)))
        .build()
      )
    }
  )(testCode)

  override def service: String = "sns"
}
