ThisBuild / organization := "com.github.matsluni"
ThisBuild / crossScalaVersions := List("2.11.12", "2.12.15", "2.13.6")
ThisBuild / scalaVersion := crossScalaVersions.value.last

ThisBuild / githubWorkflowJavaVersions := Seq("adopt@1.8")
ThisBuild / githubWorkflowPublishTargetBranches := Nil

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-target:jvm-1.8",
    "-encoding", "UTF-8",
    "-unchecked",
    "-deprecation"),
  description := "An alternative non-blocking async http engine for aws-sdk-java-v2 based on akka-http",
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

lazy val IntegrationTest = config("it") extend Test

lazy val root = (project in file("."))
  .enablePlugins(AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    Defaults.itSettings,
    automateHeaderSettings(Test, IntegrationTest),
    headerSettings(Test, IntegrationTest),
    libraryDependencies ++= deps,
    (Test / fork) := true,
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    useGpg := false,
    pgpPublicRing := file(Path.userHome.absolutePath + "/.gnupg/pubring.gpg"),
    pgpSecretRing := file(Path.userHome.absolutePath + "/.gnupg/secring.gpg"),
    pgpPassphrase := sys.env.get("PGP_PASS").map(_.toArray),
    publishMavenStyle := true,
    (Test / publishArtifact) := false,
    pomIncludeRepository := (_ => false),
    publishTo := Some(
      if (version.value.trim.endsWith("SNAPSHOT"))
        "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
      else
        "releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
  )


lazy val deps = {
  val awsSDKVersion = "2.11.4"
  val akkaVersion = "2.5.31"
  val AkkaHttpVersion = "10.1.14"

  Seq(
    "com.typesafe.akka"       %% "akka-stream"             % akkaVersion,
    "com.typesafe.akka"       %% "akka-http"               % AkkaHttpVersion,
    "software.amazon.awssdk"  %  "http-client-spi"         % awsSDKVersion,
    "org.scala-lang.modules"  %% "scala-collection-compat" % "2.5.0",

    "software.amazon.awssdk"  %  "s3"                      % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),
    "software.amazon.awssdk"  %  "dynamodb"                % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),
    "software.amazon.awssdk"  %  "sqs"                     % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),
    "software.amazon.awssdk"  %  "sns"                     % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),
    "software.amazon.awssdk"  %  "kinesis"                 % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),

    "com.dimafeng"            %% "testcontainers-scala"    % "0.39.9"        % "test",

    "junit"                   %  "junit"                   % "4.13.2"          % "test",

    "org.scala-lang.modules"  %% "scala-java8-compat"      % "1.0.1"         % "it,test",
    "org.scalatest"           %% "scalatest"               % "3.2.9"         % "it,test",
    "org.scalatestplus"       %% "scalatestplus-junit"     % "1.0.0-M2"      % "it,test",
    "ch.qos.logback"          %  "logback-classic"         % "1.2.6"         % "it,test"
  )
}
