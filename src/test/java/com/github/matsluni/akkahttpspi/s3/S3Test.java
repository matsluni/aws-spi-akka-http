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

import akka.actor.ActorSystem;
import com.github.matsluni.akkahttpspi.AkkaHttpAsyncHttpService;
import org.junit.Rule;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class S3Test extends JUnitSuite {

  private static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static SecureRandom rnd = new SecureRandom();

  @Rule
  public GenericContainer s3mock = new GenericContainer<>("adobe/s3mock:2.13.0").withExposedPorts(9090);

  @Test
  public void testS3() throws Exception {
    SdkAsyncHttpClient akkaClient = null;
    S3AsyncClient client = null;

    try {
      akkaClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory().build();
      client = getAsyncClient(akkaClient);

      createBucketAndAssert(client);
    } finally {
      akkaClient.close();
      client.close();
    }
  }

  @Test
  public void testS3WithExistingActorSystem() throws Exception {
    ActorSystem system = ActorSystem.create();
    SdkAsyncHttpClient akkaClient = null;
    S3AsyncClient client = null;

    try {
      akkaClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory().withActorSystem(system).build();
      client = getAsyncClient(akkaClient);

      createBucketAndAssert(client);
    } finally {
      akkaClient.close();
      client.close();
      system.terminate();
      system.getWhenTerminated().toCompletableFuture().get(2, TimeUnit.SECONDS);
    }
  }

  private void createBucketAndAssert(S3AsyncClient client) throws IOException {
    client.createBucket(CreateBucketRequest.builder().bucket("foo").build()).join();
    File randomFile = File.createTempFile("aws1", randomString(5));
    String fileContent = randomString(1000);
    FileWriter fileWriter = new FileWriter(randomFile);
    fileWriter.write(fileContent);
    fileWriter.flush();
    client.putObject(PutObjectRequest.builder().bucket("foo").key("my-file").contentType("text/plain").build(), randomFile.toPath()).join();

    ResponseBytes result = client.getObject(GetObjectRequest.builder().bucket("foo").key("my-file").build(),
            AsyncResponseTransformer.toBytes()).join();

    assertEquals(fileContent, result.asUtf8String());
  }

  private S3AsyncClient getAsyncClient(SdkAsyncHttpClient akkaClient) throws URISyntaxException {
    return S3AsyncClient
            .builder()
            .serviceConfiguration(
                    S3Configuration.builder()
                            .checksumValidationEnabled(false)
                            .pathStyleAccessEnabled(true)
                            .build()
            )
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .endpointOverride(new URI("http://localhost:" + s3mock.getMappedPort(9090)))
            .region(Region.of("s3"))
            .httpClient(akkaClient)
            .build();
  }

  String randomString(int len) {
  	 StringBuilder sb = new StringBuilder( len );
  	 for( int i = 0; i < len; i++ )
  	 	 sb.append( AB.charAt( rnd.nextInt(AB.length()) ) );
  	 return sb.toString();
  }

}
