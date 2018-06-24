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

package com.github.matsluni.akkahttpspi.dynamodb

import com.github.matsluni.akkahttpspi.{AkkaHttpAsyncHttpService, TestBase}
import org.scalatest.concurrent.{Eventually, Futures, IntegrationPatience}
import org.scalatest.{Matchers, WordSpec}
import software.amazon.awssdk.services.dynamodb.DynamoDBAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import org.scalatest.concurrent.ScalaFutures._
import software.amazon.awssdk.core.client.builder.ClientAsyncHttpConfiguration
import software.amazon.awssdk.utils.AttributeMap

import scala.compat.java8.FutureConverters._

class ITTestDynamoDB extends WordSpec with Matchers with Futures with Eventually with IntegrationPatience with TestBase {

  def withClient(testCode: DynamoDBAsyncClient => Any) {

    val akkaClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory().createHttpClientWithDefaults(AttributeMap.empty())

    val client = DynamoDBAsyncClient
      .builder()
      .credentialsProvider(credentialProviderChain)
      .region(defaultRegion)
      .asyncHttpConfiguration(
        ClientAsyncHttpConfiguration.builder().httpClient(akkaClient).build())
      .build()

    try {
      testCode(client)
    }
    finally { // clean up
      akkaClient.close()
      client.close()
    }
  }

  "DynamoDB" should {
    "create a table" in withClient { implicit client =>
      val tableName = s"Movies-${randomIdentifier(5)}"
      val attributes = AttributeDefinition.builder.attributeName("film_id").attributeType(ScalarAttributeType.S).build()
      val keySchema = KeySchemaElement.builder.attributeName("film_id").keyType(KeyType.HASH).build()

      val result = client.createTable(
        CreateTableRequest.builder()
          .tableName(tableName)
          .attributeDefinitions(attributes)
          .keySchema(keySchema)
          .provisionedThroughput(ProvisionedThroughput
                                  .builder
                                  .readCapacityUnits(1L)
                                  .writeCapacityUnits(1L)
                                  .build())
          .build()).join

      val desc = result.tableDescription()
      desc.tableName() should be (tableName)

      eventually {
        val response = client.describeTable(DescribeTableRequest.builder().tableName(tableName).build()).toScala
        response.futureValue.table().tableStatus() should be (TableStatus.ACTIVE)
      }
      client.deleteTable(DeleteTableRequest.builder().tableName(tableName).build()).toScala

    }
  }

}
