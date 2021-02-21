name := "aes"
organization := "edu.berkeley.cs"
version := "0.0.1-SNAPSHOT"
scalaVersion := "2.12.12"

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases"),
  Resolver.mavenLocal
)
scalacOptions := Seq("-deprecation", "-unchecked", "-Xsource:2.11", "-language:reflectiveCalls")
libraryDependencies += "edu.berkeley.cs" %% "chisel3" % "3.4-SNAPSHOT"
libraryDependencies += "edu.berkeley.cs" %% "rocketchip" % "1.2-SNAPSHOT"
libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "0.3.1"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.+" % "test"
