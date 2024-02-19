ThisBuild / organization := "com.github.matsluni"
// https://www.scala-lang.org/download/all.html
ThisBuild / crossScalaVersions := List("2.12.18", "2.13.12", "3.3.1")
ThisBuild / scalaVersion := crossScalaVersions.value.last

ThisBuild / githubWorkflowPublishTargetBranches := Nil

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    if (scalaVersion.value.startsWith("2.12")) "-target:jvm-1.8"
    else if (scalaVersion.value.startsWith("3.")) "-Xtarget:8"
    else "-release:8",
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
  val akkaVersion = "2.8.4"
  val AkkaHttpVersion = "10.5.2"

  Seq(
    "com.typesafe.akka"       %% "akka-stream"                    % akkaVersion,
    "com.typesafe.akka"       %% "akka-http"                      % AkkaHttpVersion,
    "software.amazon.awssdk"  %  "http-client-spi"                % awsSDKVersion,
    "org.scala-lang.modules"  %% "scala-collection-compat"        % "2.11.0",

    "software.amazon.awssdk"  %  "s3"                             % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),
    "software.amazon.awssdk"  %  "dynamodb"                       % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),
    "software.amazon.awssdk"  %  "sqs"                            % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),
    "software.amazon.awssdk"  %  "sns"                            % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),
    "software.amazon.awssdk"  %  "kinesis"                        % awsSDKVersion   % "test" exclude("software.amazon.awssdk", "netty-nio-client"),

    "com.dimafeng"            %% "testcontainers-scala-scalatest" % "0.41.0"       % "test",

    "junit"                   %  "junit"                          % "4.13.2"        % "test",

    "org.scala-lang.modules"  %% "scala-java8-compat"             % "1.0.2"         % "it,test",
    "org.scalatest"           %% "scalatest"                      % "3.2.17"        % "it,test",
    "org.scalatestplus"       %% "junit-4-13"                     % "3.2.18.0"      % "it,test",
    "ch.qos.logback"          %  "logback-classic"                % "1.3.11"        % "it,test"
  )
}
