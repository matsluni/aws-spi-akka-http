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

import java.net.URI

import org.scalatest.{Matchers, WordSpec}
import software.amazon.awssdk.auth.credentials.{AwsCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDBAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import com.amazonaws.services.dynamodbv2.local.main.ServerRunner
import com.github.matsluni.akkahttpspi.AkkaHttpAsyncHttpService
import software.amazon.awssdk.core.client.builder.ClientAsyncHttpConfiguration
import software.amazon.awssdk.utils.AttributeMap

import scala.collection.JavaConverters._
import scala.util.Random

class TestDynamoDB extends WordSpec with Matchers {

  // from http://www.scalatest.org/user_guide/sharing_fixtures
  def withClient(port: Int = Random.nextInt(50000) + 1025)(testCode: DynamoDBAsyncClient => Any) {

    // For the local DynamoDB to work you need to copy the 'libsqlite4java-osx-1.0.392.dylib' from 'com.almworks.sqlite4java' (.ivy2)
    // to the 'java.library.path' e.g. ~/Library/Java/Extensions
    val server = ServerRunner.createServerFromCommandLineArgs(Array("-inMemory", "-port", port.toString))
    server.start()

    val akkaClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory().createHttpClientWithDefaults(AttributeMap.empty())

    val client = DynamoDBAsyncClient
      .builder()
      .endpointOverride(new URI(s"http://localhost:$port"))
      .region(Region.of("dynamodb"))
      .credentialsProvider(StaticCredentialsProvider.create(AwsCredentials.create("x", "x")))
      .asyncHttpConfiguration(
        ClientAsyncHttpConfiguration.builder().httpClient(akkaClient).build())
      .build()

    try {
      testCode(client)
    }
    finally { // clean up
      server.stop()
      akkaClient.close()
      client.close()
    }
  }

  "DynamoDB" should {
    "create a table" in withClient() { implicit client =>
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
      desc.tableName() should be ("Movies")

    }

    "list all tables" in withClient() { implicit client =>
      val tableResult = client.listTables().join()
      tableResult.tableNames().asScala should have size(0)
    }
  }
}
