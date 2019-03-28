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

package com.github.matsluni.akkahttpspi

import java.net.URI

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import com.github.matsluni.akkahttpspi.testcontainers.LocalStackReadyLogWaitStrategy
import org.scalatest.concurrent.{Eventually, Futures, IntegrationPatience}
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import software.amazon.awssdk.core.SdkClient
import software.amazon.awssdk.regions.Region

import scala.util.Random

trait BaseAwsClientTest[C <: SdkClient]
  extends WordSpec
    with Matchers
    with Futures
    with Eventually
    with BeforeAndAfter
    with IntegrationPatience
    with ForAllTestContainer {

  lazy val defaultRegion: Region = Region.EU_WEST_1

  def client: C
  def exposedServicePort: Int
  val container: GenericContainer

  def endpoint = new URI(s"http://localhost:${container.mappedPort(exposedServicePort)}")
  def randomIdentifier(length: Int): String = Random.alphanumeric.take(length).mkString
}

trait LocalstackBaseAwsClientTest[C <: SdkClient] extends BaseAwsClientTest[C] {
  def service: String

  lazy val exposedServicePort: Int = LocalstackServicePorts.services(service)

  override lazy val container: GenericContainer =
    new GenericContainer(
      dockerImage = "localstack/localstack",
      exposedPorts = Seq(exposedServicePort),
      env = Map("SERVICES" -> service),
      waitStrategy = Some(LocalStackReadyLogWaitStrategy)
    )
}

object LocalstackServicePorts {
  //services and ports based on https://github.com/localstack/localstack
  val services: Map[String, Int] = Map(
    "s3" -> 4572,
    "sqs" -> 4576,
    "sns" -> 4575,
    "dynamodb" -> 4569
  )
}
