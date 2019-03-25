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

package com.github.matsluni.akkahttpspi.s3;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.scalatest.junit.JUnitSuite;
import com.github.matsluni.akkahttpspi.AkkaHttpAsyncHttpService;
import org.testcontainers.containers.GenericContainer;
import scala.util.Random;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3Test extends JUnitSuite {
  @Rule
  public GenericContainer s3mock = new GenericContainer<>("findify/s3mock:0.2.4").withExposedPorts(8001);

  @Test
  @Ignore
  public void testS3() throws Exception {
    SdkAsyncHttpClient akkaClient = null;
    S3AsyncClient client = null;

    try {
      akkaClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory().build();

      client = S3AsyncClient
              .builder()
              .credentialsProvider(AnonymousCredentialsProvider.create())
              .endpointOverride(new URI("http://localhost:8001"))
              .region(Region.of("s3"))
              .httpClient(akkaClient)
              .build();

      client.createBucket(CreateBucketRequest.builder().bucket("foo").build()).join();
      File randomFile = File.createTempFile("aws1", new Random().alphanumeric().take(5).mkString());
      String fileContent = new Random().alphanumeric().take(1000).mkString();
      FileWriter fileWriter = new FileWriter(randomFile);
      fileWriter.write(fileContent);
      fileWriter.flush();
      client.putObject(PutObjectRequest.builder().bucket("foo").key("my-file").build(), randomFile.toPath()).join();

      ResponseBytes result = client.getObject(GetObjectRequest.builder().bucket("foo").key("my-file").build(),
              AsyncResponseTransformer.toBytes()).join();

      assertEquals(fileContent, result.asUtf8String());

    } finally {
      akkaClient.close();
      client.close();
    }
  }

}
