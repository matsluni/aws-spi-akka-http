
lazy val repoResolvers = Seq(
  Resolver.mavenLocal, "DynamoDB Local Release Repository" at "https://s3-us-west-2.amazonaws.com/dynamodb-local/release"
)

lazy val commonSettings = Seq(
  scalaVersion := "2.12.6",
  organization := "com.github.matsluni",
  name := "aws-spi-akka-http",
  version := "0.0.1-SNAPSHOT",
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  organizationName := "Matthias Lüneberg",
  startYear := Some(2018)
)

lazy val IntegrationTest = config("it") extend(Test)

lazy val root = (project in file("."))
  .enablePlugins(AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    Defaults.itSettings,
    automateHeaderSettings(Test, IntegrationTest),
    headerSettings(Test, IntegrationTest),
    libraryDependencies ++= deps,
    fork in Test := true,
    resolvers ++= repoResolvers
  )


lazy val deps = {
  val awsSDKVersion = "2.0.0-preview-10"
  val akkaVersion = "2.5.13"
  val AkkaHttpVersion = "10.1.3"

  Seq(
    "com.typesafe.akka"       %% "akka-stream"        % akkaVersion     withSources(),
    "com.typesafe.akka"       %% "akka-http"          % AkkaHttpVersion withSources(),
    "software.amazon.awssdk"  %  "http-client-spi"    % awsSDKVersion   withSources(),

    "software.amazon.awssdk"  %  "s3"                 % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),
    "software.amazon.awssdk"  %  "dynamodb"           % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),
    "software.amazon.awssdk"  %  "sqs"                % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),

    "io.findify"              %% "s3mock"             % "0.2.4"         % "test",
    "com.amazonaws"           %  "DynamoDBLocal"      % "1.11.119"      % "test",
    "org.elasticmq"           %% "elasticmq-server"   % "0.13.9"        % "test",
    "junit"                   %  "junit"              % "4.12"          % "test",

    "org.scala-lang.modules"  %% "scala-java8-compat" % "0.8.0"         % "it,test",
    "org.scalatest"           %% "scalatest"          % "3.0.5"         % "it,test",
    "ch.qos.logback"          %  "logback-classic"    % "1.2.3"         % "it,test"
  )
}
