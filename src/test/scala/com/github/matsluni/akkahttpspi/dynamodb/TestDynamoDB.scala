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

import com.github.matsluni.akkahttpspi.{AkkaHttpAsyncHttpService, LocalstackBaseAwsClientTest}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._

import scala.collection.JavaConverters._

class TestDynamoDB extends LocalstackBaseAwsClientTest[DynamoDbAsyncClient] {
  "DynamoDB" should {
    "create a table" in {
      val attributes = AttributeDefinition.builder.attributeName("film_id").attributeType(ScalarAttributeType.S).build()
      val keySchema = KeySchemaElement.builder.attributeName("film_id").keyType(KeyType.HASH).build()

      val result = client.createTable(
        CreateTableRequest.builder()
          .tableName("Movies")
          .attributeDefinitions(attributes)
          .keySchema(keySchema)
          .provisionedThroughput(ProvisionedThroughput.builder.readCapacityUnits(1000L).writeCapacityUnits(1000L).build())
          .build()).join

      val desc = result.tableDescription()
      desc.tableName() should be("Movies")
    }

    "list all tables" in {
      val tableResult = client.listTables().join()
      tableResult.tableNames().asScala should have size (0)
    }
  }

  override val service: String = "dynamodb"

  override def client: DynamoDbAsyncClient = DynamoDbAsyncClient
    .builder()
    .endpointOverride(endpoint)
    .region(defaultRegion)
    .httpClient(new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory().build())
    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
    .build()

  before {
    client.listTables().join().tableNames().asScala.foreach { t =>
      client.deleteTable(DeleteTableRequest.builder().tableName(t).build()).join()
    }
  }

}
