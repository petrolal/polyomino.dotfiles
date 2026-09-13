enablePlugins(NativeImagePlugin)

scalaVersion := "3.5.2"
name := "polyomino"
organization := "io.github.petrolal"
version := {
  val tagVersion = sys.env.get("CI_VERSION")
  tagVersion.getOrElse {
    val gitTag = try {
      scala.sys.process.Process("git" :: "describe" :: "--tags" :: "--abbrev=0" :: Nil).!!.trim
    } catch {
      case _: Exception => ""
    }
    if (gitTag.isEmpty) "0.1.0-SNAPSHOT" else gitTag.stripPrefix("v")
  }
}

licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))
homepage := Some(url("https://github.com/petrolal/polyomino.dotfiles"))
scmInfo := Some(ScmInfo(url("https://github.com/petrolal/polyomino.dotfiles"), "scm:git@github.com:petrolal/polyomino.dotfiles.git"))
developers := List(
  Developer(
    id = "petrolal",
    name = "Petrolal",
    email = "petrolal@users.noreply.github.com",
    url = url("https://github.com/petrolal")
  )
)

libraryDependencies ++= Seq(
  "com.lihaoyi" %% "os-lib" % "0.11.9-M8",
  "com.lihaoyi" %% "upickle" % "4.4.3",
  "com.lihaoyi" %% "mainargs" % "0.7.0",
  "org.scalameta" %% "munit" % "1.0.0" % Test
)

Test / parallelExecution := false

Compile / mainClass := Some("polyomino.Main")

// Package JAR with all dependencies (fat JAR)
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
assembly / assemblyJarName := s"${name.value}-${version.value}-assembly.jar"
assembly / mainClass := Some("polyomino.Main")

// GraalVM Native Image settings
nativeImageOptions ++= Seq(
  "--no-fallback",
  "-H:+ReportExceptionStackTraces",
  "--enable-preview"
)

