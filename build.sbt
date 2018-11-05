
lazy val repoResolvers = Seq(
  Resolver.mavenLocal, "DynamoDB Local Release Repository" at "https://s3-us-west-2.amazonaws.com/dynamodb-local/release"
)

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-target:jvm-1.8",
    "-encoding", "UTF-8",
    "-unchecked",
    "-deprecation"),
  scalaVersion := "2.12.7",
  description := "An alternative non-blocking async http engine for aws-sdk-java-v2 based on akka-http",
  crossScalaVersions := Seq("2.11.12", "2.12.7"),
  organization := "com.github.matsluni",
  name := "aws-spi-akka-http",
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  organizationName := "Matthias Lüneberg",
  startYear := Some(2018),
  homepage := Some(url("https://github.com/matsluni/aws-spi-akka-http")),
  organizationHomepage := Some(url("https://github.com/matsluni/aws-spi-akka-http")),
  developers := Developer("matsluni", "Matthias Lüneberg", "", url("https://github.com/matsluni")) :: Nil,
  scmInfo := Some(ScmInfo(
    browseUrl = url("https://github.com/matsluni/aws-spi-akka-http.git"),
    connection = "scm:git:git@github.com:matsluni/aws-spi-akka-http.git"
  ))
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
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    useGpg := false,
    pgpPublicRing := file(Path.userHome.absolutePath + "/.gnupg/pubring.gpg"),
    pgpSecretRing := file(Path.userHome.absolutePath + "/.gnupg/secring.gpg"),
    pgpPassphrase := sys.env.get("PGP_PASS").map(_.toArray),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := (_ => false),
    publishTo := Some(
      if (version.value.trim.endsWith("SNAPSHOT"))
        "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
      else
        "releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"),
    resolvers ++= repoResolvers
  )


lazy val deps = {
  val awsSDKVersion = "2.0.0-preview-12"
  val akkaVersion = "2.5.17"
  val AkkaHttpVersion = "10.1.5"

  Seq(
    "com.typesafe.akka"       %% "akka-stream"        % akkaVersion     withSources(),
    "com.typesafe.akka"       %% "akka-http"          % AkkaHttpVersion withSources(),
    "software.amazon.awssdk"  %  "http-client-spi"    % awsSDKVersion   withSources(),

    "software.amazon.awssdk"  %  "s3"                 % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),
    "software.amazon.awssdk"  %  "dynamodb"           % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),
    "software.amazon.awssdk"  %  "sqs"                % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),

    "io.findify"              %% "s3mock"             % "0.2.5"         % "test",
    "com.amazonaws"           %  "DynamoDBLocal"      % "1.11.119"      % "test",
    "org.elasticmq"           %% "elasticmq-server"   % "0.14.1"        % "test",
    "junit"                   %  "junit"              % "4.12"          % "test",

    "org.scala-lang.modules"  %% "scala-java8-compat" % "0.9.0"         % "it,test",
    "org.scalatest"           %% "scalatest"          % "3.0.5"         % "it,test",
    "ch.qos.logback"          %  "logback-classic"    % "1.2.3"         % "it,test"
  )
}
