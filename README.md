# AWS Akka-Http SPI implementation 

[![Build Status](https://travis-ci.org/matsluni/aws-spi-akka-http.svg?branch=master)](https://travis-ci.org/matsluni/aws-spi-akka-http) 
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.matsluni/aws-spi-akka-http_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.matsluni/aws-spi-akka-http_2.12)
[![License](http://img.shields.io/:license-Apache%202-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) [![Join the chat at https://gitter.im/aws-spi-akka-http/community](https://badges.gitter.im/aws-spi-akka-http/community.svg)](https://gitter.im/aws-spi-akka-http/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This library implements the provided [SPI](https://en.wikipedia.org/wiki/Service_provider_interface) for the asynchronous 
and non-blocking http calls in the new [AWS Java SDK](https://github.com/aws/aws-sdk-java-v2) with 
[Akka HTTP](https://github.com/akka/akka-http).

This is a prototypical implementation to explore an alternative to netty as the build-in http engine in the aws sdk.

This library is **not production ready** and early alpha. Use at your own risk. 
Also, the underlying SPI is subject to change.

## Usage

Create a dependency to this library by adding the following to your `build.sbt`:

    "com.github.matsluni" %% "aws-spi-akka-http" % "0.0.6"
    
or for Maven, the following to `pom.xml`:

```
<dependency>
    <groupId>com.github.matsluni</groupId>
    <artifactId>aws-spi-akka-http_2.12</artifactId>
    <version>0.0.6</version>
</dependency>
```

An example (in scala) from the test shows how to use akka-http as the underlying http provider instead of netty.

```scala
val akkaClient = new AkkaHttpAsyncHttpService().createAsyncHttpClientFactory().build()

val client = S3AsyncClient
              .builder()
              .credentialsProvider(ProfileCredentialsProvider.builder().build())
              .region(Region.EU_CENTRAL_1)
              .httpClient(akkaClient)
              .build()
              
val eventualResponse = client.listBuckets()
```

There also exists an [mini example project](https://github.com/matsluni/aws-spi-akka-http-example) in gradle which shows the usage.

## Running the tests in this repo

In this repository there are unit tests and integration tests. The unit tests run against some local started aws 
services, which test some basic functionality and do not use real services from aws and cost no money. 

But, there are also integration tests which require **valid aws credentials** to run and running these tests is **not for free**. 
If you are not careful it can cost you money on aws. Be warned.

The integration tests look for aws credentials as either an environment variable, a system property or a credential file.
See the [here](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/setup-credentials.html) and 
[here](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html) in the aws documentation for details.

The tests can be run in `sbt` with:

    test
    
To run the integration tests

    it:test



# License
This library is Open Source and available under the Apache 2 License.
