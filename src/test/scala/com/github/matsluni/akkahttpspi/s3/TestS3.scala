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
import java.net.URI

import com.github.matsluni.akkahttpspi.AkkaHttpAsyncHttpService
import org.scalatest.{Matchers, WordSpec}
import io.findify.s3mock.S3Mock
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.{S3AsyncClient, S3Configuration}
import software.amazon.awssdk.services.s3.model._

import scala.collection.JavaConverters._
import scala.util.Random

class TestS3 extends WordSpec with Matchers {

  // from http://www.scalatest.org/user_guide/sharing_fixtures
  def withClient(testCode: S3AsyncClient => Any) {
    val api = new S3Mock.Builder().withPort(8001).withInMemoryBackend.build

    val akkaClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory().build()

    val client = S3AsyncClient
      .builder()
      .serviceConfiguration(S3Configuration.builder().build())
      .credentialsProvider(AnonymousCredentialsProvider.create())
      .endpointOverride(new URI("http://localhost:8001"))
      .region(Region.of("s3"))
      .httpClient(akkaClient)
      .build()

    try {
      api.start
      testCode(client)
    }
    finally { // clean up
      api.stop
      akkaClient.close()
      client.close()
    }
  }

  "Async S3 client" should {

    "create bucket" in withClient { implicit client =>
      createBucket("foo")
      val buckets = client.listBuckets().join
      buckets.buckets() should have size(1)
      buckets.buckets().asScala.toList.head.name() should be("foo")
    }

    "upload and download a file to a bucket" in withClient { implicit client =>
      createBucket("foo")
      val randomFile = File.createTempFile("aws1", Random.alphanumeric.take(5).mkString)
      val fileContent = Random.alphanumeric.take(1000).mkString
      val fileWriter = new FileWriter(randomFile)
      fileWriter.write(fileContent)
      fileWriter.flush()
      client.putObject(PutObjectRequest.builder().bucket("foo").key("my-file").build(), randomFile.toPath).join

      val result = client.getObject(GetObjectRequest.builder().bucket("foo").key("my-file").build(),
                                    AsyncResponseTransformer.toBytes[GetObjectResponse]()).join
      result.asUtf8String() should be(fileContent)
    }

    "multipart upload" in withClient { implicit client =>
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

  def createBucket(name: String)(implicit client: S3AsyncClient): Unit = {
    client.createBucket(CreateBucketRequest.builder().bucket(name).build()).join
  }


}
