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

import akka.http.scaladsl.model.HttpProtocols
import com.github.matsluni.akkahttpspi.AkkaHttpClient.AkkaHttpClientBuilder
import com.github.matsluni.akkahttpspi.{AkkaHttpAsyncHttpService, LocalstackBaseAwsClientTest}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._

import scala.jdk.CollectionConverters._

class TestDynamoDB extends LocalstackBaseAwsClientTest[DynamoDbAsyncClient] {
  "DynamoDB" should {
    "create a table" in withClient() { implicit client =>
      val attributes = AttributeDefinition.builder.attributeName("film_id").attributeType(ScalarAttributeType.S).build()
      val keySchema = KeySchemaElement.builder.attributeName("film_id").keyType(KeyType.HASH).build()

      val emptyTableResult = client.listTables().join()
      emptyTableResult.tableNames().asScala should have size (0)

      val result = client.createTable(
        CreateTableRequest.builder()
          .tableName("Movies")
          .attributeDefinitions(attributes)
          .keySchema(keySchema)
          .provisionedThroughput(ProvisionedThroughput.builder.readCapacityUnits(1000L).writeCapacityUnits(1000L).build())
          .build()).join

      val desc = result.tableDescription()
      desc.tableName() should be("Movies")

      val tableResult = client.listTables().join()
      tableResult.tableNames().asScala should have size (1)
    }

    "work with HTTP/2" ignore withClient(_.withProtocol(HttpProtocols.`HTTP/2.0`)) { implicit client =>
      val result = client.listTables().join()
      result.tableNames() should not be null
    }
  }

  private def withClient(builderFn: AkkaHttpClientBuilder => AkkaHttpClientBuilder = identity)(testCode: DynamoDbAsyncClient => Any): Any = {

    val akkaClient = builderFn(new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory()).build()

    val client = DynamoDbAsyncClient
      .builder()
      .endpointOverride(endpoint)
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
      .httpClient(akkaClient)
      .region(defaultRegion)
      .build()

    try {
      testCode(client)
    }
    finally { // clean up
      akkaClient.close()
      client.close()
    }
  }

  override val service: String = "dynamodb"

}
