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

import com.dimafeng.testcontainers.GenericContainer
import com.github.matsluni.akkahttpspi.testcontainers.TimeoutWaitStrategy
import com.github.matsluni.akkahttpspi.{AkkaHttpAsyncHttpService, BaseAwsClientTest}
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer}
import software.amazon.awssdk.services.s3.{S3AsyncClient, S3Configuration}
import software.amazon.awssdk.services.s3.model._

import scala.jdk.CollectionConverters._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class TestS3 extends BaseAwsClientTest[S3AsyncClient] {

  "Async S3 client" should {
    "create bucket" in withClient { implicit client =>
      val bucketName = createBucket()
      val buckets = client.listBuckets().join
      buckets.buckets() should have size (1)
      buckets.buckets().asScala.toList.head.name() should be(bucketName)
    }

    "upload and download a file to a bucket" in withClient { implicit client =>
      val bucketName = createBucket()
      val fileContent = 0 to 1000 mkString

      client.putObject(PutObjectRequest.builder().bucket(bucketName).key("my-file").contentType("text/plain").build(), AsyncRequestBody.fromString(fileContent)).join

      val result = client.getObject(GetObjectRequest.builder().bucket(bucketName).key("my-file").build(),
        AsyncResponseTransformer.toBytes[GetObjectResponse]()).join

      result.asUtf8String() should be(fileContent)
      result.response().contentType() should be("text/plain")
      result.response().contentLength() shouldEqual fileContent.getBytes().length
    }

    "multipart upload" in withClient { implicit client =>
      val bucketName = createBucket()
      val randomFile = File.createTempFile("aws1", Random.alphanumeric.take(5).mkString)
      val fileContent = (0 to 1000000).mkString
      val fileWriter = new FileWriter(randomFile)
      fileWriter.write(fileContent)
      fileWriter.flush()
      val createMultipartUploadResponse = client.createMultipartUpload(CreateMultipartUploadRequest.builder().bucket(bucketName).key("bar").contentType("text/plain").build()).join()

      val p1 = client.uploadPart(UploadPartRequest.builder().bucket(bucketName).key("bar").partNumber(1).uploadId(createMultipartUploadResponse.uploadId()).build(), randomFile.toPath).join
      val p2 = client.uploadPart(UploadPartRequest.builder().bucket(bucketName).key("bar").partNumber(2).uploadId(createMultipartUploadResponse.uploadId()).build(), randomFile.toPath).join

      client.completeMultipartUpload(CompleteMultipartUploadRequest
        .builder()
        .bucket(bucketName)
        .key("bar")
        .multipartUpload(CompletedMultipartUpload
          .builder()
          .parts(
            CompletedPart.builder().partNumber(1).eTag(p1.eTag()).build(),
            CompletedPart.builder().partNumber(2).eTag(p2.eTag()).build())
          .build())
        .uploadId(createMultipartUploadResponse.uploadId())
        .build()).join

      val result = client.getObject(GetObjectRequest.builder().bucket(bucketName).key("bar").build(),
        AsyncResponseTransformer.toBytes[GetObjectResponse]()).join
      result.asUtf8String() should be(fileContent + fileContent)
    }

  }

  def createBucket()(implicit client: S3AsyncClient): String = {
    val bucketName = Random.alphanumeric.take(7).map(_.toLower).mkString
    client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build()).join
    bucketName
  }

  private def withClient(testCode: S3AsyncClient => Any): Any = {

    val akkaClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory().build()

    val client = S3AsyncClient
      .builder()
      .serviceConfiguration(
        S3Configuration
          .builder()
          .checksumValidationEnabled(false)
          .pathStyleAccessEnabled(true)
          .build()
      )
      .credentialsProvider(AnonymousCredentialsProvider.create)
      .endpointOverride(endpoint)
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

  override def exposedServicePort: Int = 9090

  private lazy val containerInstance = new GenericContainer(
    dockerImage = "adobe/s3mock:2.13.0",
    exposedPorts = Seq(exposedServicePort),
    waitStrategy = Some(TimeoutWaitStrategy(10 seconds))
  )
  override val container: GenericContainer = containerInstance
}
