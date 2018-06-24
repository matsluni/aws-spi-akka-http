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

package com.github.matsluni.akkahttpspi.s3

import java.io.{File, FileWriter}

import com.github.matsluni.akkahttpspi.{AkkaHttpAsyncHttpService, TestBase}
import org.scalatest.{Matchers, WordSpec}
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.client.builder.ClientAsyncHttpConfiguration
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.utils.AttributeMap

import scala.util.Random

class ITTestS3 extends WordSpec with Matchers with TestBase {

  def withClient(testCode: S3AsyncClient => Any) {

    val akkaClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory().createHttpClientWithDefaults(AttributeMap.empty())

    val client = S3AsyncClient
      .builder()
      .credentialsProvider(credentialProviderChain)
      .region(defaultRegion)
      .asyncHttpConfiguration(ClientAsyncHttpConfiguration.builder().httpClient(akkaClient).build())
      .build()

    try {
      testCode(client)
    }
    finally { // clean up
      akkaClient.close()
      client.close()
    }
  }

  "S3 async client" should {

    "upload and download a file to a bucket + cleanup" in withClient { implicit client =>
      val bucketName = "aws-spi-test-" + Random.alphanumeric.take(10).filterNot(_.isUpper).mkString
      createBucket(bucketName)
      val randomFile = File.createTempFile("aws", Random.alphanumeric.take(5).mkString)
      val fileContent = Random.alphanumeric.take(1000).mkString
      val fileWriter = new FileWriter(randomFile)
      fileWriter.write(fileContent)
      fileWriter.flush()
      client.putObject(PutObjectRequest.builder().bucket(bucketName).key("my-file").build(), randomFile.toPath).join

      val result = client.getObject(GetObjectRequest.builder().bucket(bucketName).key("my-file").build(),
        AsyncResponseTransformer.toBytes[GetObjectResponse]()).join
      result.asUtf8String() should be(fileContent)

      client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key("my-file").build()).join()

      client.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build()).join()
    }
  }

  def createBucket(name: String)(implicit client: S3AsyncClient): Unit = {
    client.createBucket(CreateBucketRequest.builder().bucket(name).build()).join
  }

}
