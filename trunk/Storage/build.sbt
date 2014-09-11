import sbt._

import Keys._

shellPrompt := { state =>  "sbt (%s)> ".format(Project.extract(state).currentProject.id) }

net.virtualvoid.sbt.graph.Plugin.graphSettings

name := "Storage"

version := "0.0.0.2"

scalaVersion := "2.10.4"

resolvers += "spring-milestones" at "http://repo.springsource.org/libs-milestone"

libraryDependencies += "com.datastax.cassandra" % "cassandra-driver-parent" % "2.0.2"

libraryDependencies += "com.datastax.cassandra" % "cassandra-driver-core" % "2.0.2"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.0" % "test"

libraryDependencies += "com.novocode" % "junit-interface" % "0.11-RC1" % "test"

libraryDependencies += "commons-lang" % "commons-lang" % "2.4"

libraryDependencies += "jdom" % "jdom" % "1.1"

libraryDependencies += "com.google.protobuf" % "protobuf-java" % "2.3.0"

libraryDependencies += "voldemort" % "voldemort" % "0.96"

libraryDependencies += "log4j" % "log4j" % "1.2.17"

libraryDependencies += "commons-codec" % "commons-codec" % "1.9"

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.7"

libraryDependencies += "org.apache.hbase" % "hbase-client" % "0.98.4-hadoop2"

libraryDependencies += "org.apache.hbase" % "hbase-common" % "0.98.4-hadoop2"

libraryDependencies += "org.apache.hadoop" % "hadoop-common" % "2.4.1"

libraryDependencies += "org.mapdb" % "mapdb" % "1.0.6"

scalacOptions += "-deprecation"

testOptions += Tests.Argument(TestFrameworks.JUnit, "-q", "-v")
