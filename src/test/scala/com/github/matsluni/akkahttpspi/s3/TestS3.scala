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
import org.scalatest.Ignore
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer}
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

@Ignore
class TestS3 extends BaseAwsClientTest[S3AsyncClient] {

  "Async S3 client" should {
    "create bucket" in {
      createBucket("foo")
      val buckets = client.listBuckets().join
      buckets.buckets() should have size (1)
      buckets.buckets().asScala.toList.head.name() should be("foo")
    }

    "upload and download a file to a bucket" in {
      createBucket("foo")
      val fileContent = 0 to 1000 mkString

      client.putObject(PutObjectRequest.builder().bucket("foo").key("my-file").build(), AsyncRequestBody.fromString(fileContent)).join

      val result = client.getObject(GetObjectRequest.builder().bucket("foo").key("my-file").build(),
        AsyncResponseTransformer.toBytes[GetObjectResponse]()).join

      result.asUtf8String() should be(fileContent)
    }

    "multipart upload" in {
      createBucket("foo")
      val randomFile = File.createTempFile("aws1", Random.alphanumeric.take(5).mkString)
      val fileContent = Random.alphanumeric.take(1000).mkString
      val fileWriter = new FileWriter(randomFile)
      fileWriter.write(fileContent)
      fileWriter.flush()
      val createMultipartUploadResponse = client.createMultipartUpload(CreateMultipartUploadRequest.builder().bucket("foo").key("bar").build()).join()

      client.uploadPart(UploadPartRequest.builder().bucket("foo").key("bar").partNumber(1).uploadId(createMultipartUploadResponse.uploadId()).build(), randomFile.toPath).join
      client.uploadPart(UploadPartRequest.builder().bucket("foo").key("bar").partNumber(2).uploadId(createMultipartUploadResponse.uploadId()).build(), randomFile.toPath).join

      client.completeMultipartUpload(CompleteMultipartUploadRequest
        .builder()
        .bucket("foo")
        .key("bar")
        .multipartUpload(CompletedMultipartUpload
          .builder()
          .parts(CompletedPart.builder().partNumber(1).build(), CompletedPart.builder().partNumber(1).build())
          .build())
        .uploadId(createMultipartUploadResponse.uploadId())
        .build()).join

      val result = client.getObject(GetObjectRequest.builder().bucket("foo").key("bar").build(),
        AsyncResponseTransformer.toBytes[GetObjectResponse]()).join
      result.asUtf8String() should be(fileContent + fileContent)
    }

  }

  def createBucket(name: String) {
    client.createBucket(CreateBucketRequest.builder().bucket(name).build()).join
  }

  override def client: S3AsyncClient = S3AsyncClient
    .builder()
    .region(defaultRegion)
    .credentialsProvider(AnonymousCredentialsProvider.create)
    .endpointOverride(endpoint)
    .httpClient(new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory().build())
    .build()

  override def exposedServicePort: Int = 8001

  override lazy val container: GenericContainer = new GenericContainer(
    dockerImage = "findify/s3mock:0.2.4",
    exposedPorts = Seq(exposedServicePort),
    waitStrategy = Some(TimeoutWaitStrategy(10 seconds))
  )
}
