name := "OutputMsgDef"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.4.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.4.1"

libraryDependencies += "metadata" % "metadata_2.10" % "1.0"

libraryDependencies += "org.json4s" %% "json4s-native" % "3.2.9"

libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.9" 

libraryDependencies ++= Seq("junit" % "junit" % "4.8.1" % "test")

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.1.2" % "test"

