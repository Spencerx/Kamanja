name := "Metadata"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies += "org.joda" % "joda-convert" % "1.6"

libraryDependencies += "joda-time" % "joda-time" % "2.8.2"

libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.4.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.4.1"

libraryDependencies += "org.json4s" %% "json4s-native" % "3.2.9"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.0" % "test"

libraryDependencies += "com.novocode" % "junit-interface" % "0.11-RC1" % "test"

scalacOptions += "-deprecation"

testOptions += Tests.Argument(TestFrameworks.JUnit, "-q", "-v")

