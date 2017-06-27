import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbtprotoc.ProtocPlugin.autoImport.PB

object Build extends Build {
  def mappingContainsAnyPath(mapping: (File, String), paths: Seq[String]): Boolean = {
    paths.foldLeft(false)(_ || mapping._1.getPath.contains(_))
  }

  lazy val root = Project("belink-sdk", file("."))
    .aggregate(clients, core, client, example)
    .settings(basicSettings: _*)
    .settings(noPublishing: _*)

  lazy val clients = Project("belink-clients", file("clients"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(releaseSettings: _*)
    .settings(libraryDependencies ++= (Dependencies.all ++ Dependencies.belink) )
    .settings(scalaPbSettings: _*)

  lazy val core = Project("belink-core", file("core"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(releaseSettings: _*)
    .settings(libraryDependencies ++= (Dependencies.all ++ Dependencies.belink) )
    .settings(scalaPbSettings: _*)
    .dependsOn(clients)

  lazy val client = Project("belink-client", file("client"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(releaseSettings: _*)
    .settings(libraryDependencies ++= (Dependencies.all ++ Dependencies.belink) )
    .settings(scalaPbSettings: _*)

  lazy val example = Project("example", file("example"))
    .settings(basicSettings: _*)
    .settings(formatSettings: _*)
    .settings(releaseSettings: _*)
    .settings(libraryDependencies ++= (Dependencies.all ) )

	lazy val scalaPbSettings = Seq(
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    )
  )

  lazy val noPublishing = Seq(
    publish := (),
    publishLocal := (),
    // required until these tickets are closed https://github.com/sbt/sbt-pgp/issues/42,
    // https://github.com/sbt/sbt-pgp/issues/36
    publishTo := None
  )

  lazy val basicSettings = Seq(
    organization := "com.ynet.chainsdk",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.11.2",
    crossScalaVersions := Seq("2.10.4", "2.11.8"),
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-encoding", "UTF-8"),
    javacOptions ++= Seq("-encoding", "UTF-8"),
    resolvers ++= Seq(
      //"Local Sonatype OSS Releases" at "http://10.8.0.1:48081/nexus/content/repositories/releases",
      //"Local Sonatype OSS Snapshots" at "http://10.8.0.1:48081/nexus/content/repositories/snapshots",
      //"Spray repository" at "http://repo.spray.io/"
    )
//  ,  ScoverageSbtPlugin.ScoverageKeys.coverageHighlighting := (
//      CrossVersion.partialVersion(scalaVersion.value) match {
//        case Some((2, 10)) => false
//        case _ => true
//      })
//    , assemblyMergeStrategy in assembly := {
//      case PathList("org", "slf4j", xs @ _*)         => MergeStrategy.first
//      case PathList(ps @ _*) if ps.last endsWith "axiom.xml" => MergeStrategy.filterDistinctLines
//      case PathList(ps @ _*) if ps.last endsWith "Log$Logger.class" => MergeStrategy.first
//      case PathList(ps @ _*) if ps.last endsWith "ILoggerFactory.class" => MergeStrategy.first
//      case PathList("META-INF", xs @ _*) if !xs.headOption.isDefined || !xs.head.startsWith("services") => MergeStrategy.discard
//      case x => MergeStrategy.first
//    }
  )

  lazy val releaseSettings = Seq(
    publishTo := {
      val nexus = "http://192.168.65.120:8081/nexus/"
      if (version.value.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { (repo: MavenRepository) => false },
    pomExtra := (
      <url>https://github.com/mqshen/spray-socketio</url>
        <licenses>
          <license>
            <name>The Apache Software License, Version 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:wandoulabs/spray-socketio.git</url>
          <connection>scm:git:git@github.com:wandoulabs/spray-socketio.git</connection>
        </scm>
        <developers>
          <developer>
            <id>mqshen</id>
            <name>miaoqi shen</name>
            <email>goldratio87@gmail.com</email>
          </developer>
          <developer>
            <id>gaolin</id>
            <name>lin gao</name>
            <email>gaolin@belink.com</email>
          </developer>
        </developers>
      )
  )

  lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test := formattingPreferences)

  import scalariform.formatter.preferences._
  def formattingPreferences =
    FormattingPreferences()
      .setPreference(RewriteArrowSymbols, false)
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true)
      .setPreference(IndentSpaces, 2)

}
object Dependencies {
  val branch = "1"//Process("git" :: "rev-parse" :: "--abbrev-ref" :: "HEAD" :: Nil).!!.trim
  val suffix = if (branch == "master") "" else "-SNAPSHOT"

  val libVersion = "6.34.0" + suffix
  val utilVersion = "6.33.0" + suffix
  val ostrichVersion = "9.17.0" + suffix
  val scroogeVersion = "4.6.0" + suffix
  val twitterServer = "1.19.0" + suffix

  val bouncycastleVersion = "1.51.0.0"

  val slf4jVersion = "1.7.21"
  val log4jVersion = "1.2.17"

  val snakeyamlVersion = "1.12"

  val guiceVersion = "4.0"
  val scalaGuiceVersion = "4.0.1"



  def util(which: String) =
    "com.twitter" %% ("util-"+which) % utilVersion excludeAll(
      ExclusionRule(organization = "junit"),
      ExclusionRule(organization = "org.scala-tools.testing"),
      ExclusionRule(organization = "org.mockito"))

//  val guices = Seq(
//    "guice").map { dep =>
//    "com.google.inject" % dep % guiceVersion
//  }
//
//  val guiceExtensions = Seq(
//    "guice-assistedinject",
//    "guice-multibindings").map { dep =>
//    "com.google.inject.extensions" % dep % guiceVersion
//  }

//  val scalaGuices = Seq(
//    "scala-guice").map { dep =>
//    "net.codingwell" %% dep % scalaGuiceVersion
//  }


  val testLibs = Seq(
    "org.mockito" % "mockito-core" % "1.9.5" % "test",
    "org.scalatest" %% "scalatest" % "2.2.3" % "test",
    "org.specs2" %% "specs2" % "2.3.12" % "test"
  )
  val bcVersion = "1.55"


  val all = Seq(
    "org.json4s" %% "json4s-jackson" % "3.2.10",
    "commons-io" % "commons-io" % "2.4",
    "commons-lang" % "commons-lang" % "2.6",
    "commons-dbcp" % "commons-dbcp" % "1.4",
    "org.apache.commons" % "commons-compress" % "1.5",
    "org.apache.commons" % "commons-email" % "1.3.1",
    "org.apache.httpcomponents" % "httpclient" % "4.5.2",
    "junit" % "junit" % "4.11" % "test",
    "log4j" % "log4j" % log4jVersion,
    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "org.slf4j" % "slf4j-log4j12" % slf4jVersion,
    "net.sf.jopt-simple" % "jopt-simple" % "5.0.3",
    "com.typesafe" % "config" % "1.3.0",
    "org.bouncycastle" % "bcprov-jdk15on" % bcVersion,
    "org.bouncycastle" % "bcpkix-jdk15on" % bcVersion//,
    //"org.bouncycastle" % "bcprov-ext-jdk15on" % "1.54"
    //"com.madgag.spongycastle" % "core" % bouncycastleVersion,
    //"com.madgag.spongycastle" % "prov" % bouncycastleVersion
  ).map(_.exclude("org.slf4j", "slf4j-jdk14")) ++ testLibs
  //++ akka ++ akka_http
  // "com.github.os72" % "protoc-jar" % "3.0.0-b2",


  val ScalatraVersion = "2.3.0"

  val belink = Seq(
    "net.i2p.crypto" % "eddsa" % "0.1.0",
    "cn.bubi" % "message" % "0.0.1",
    "com.alibaba" % "fastjson" % "1.2.15",
    "com.chrylis" % "base58-codec" % "1.2.0",
    "com.ynet.chainsdk" %% "fabric-client" % "0.1.0-SNAPSHOT"
  )

  val benchmark = Seq(
    "com.zaxxer" % "HikariCP" % "2.5.1"
  )

}
