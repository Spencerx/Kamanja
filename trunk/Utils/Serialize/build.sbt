name := "Serialize"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.2.0"

libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.4.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.4.1"

libraryDependencies += "org.json4s" %% "json4s-native" % "3.2.9" 

libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.9" 

libraryDependencies ++= Seq(
"com.twitter" %% "chill" % "0.3.6",
"org.scalamacros" % "quasiquotes_2.10.4" % "2.0.0-M6"
)

libraryDependencies += "com.google.protobuf" % "protobuf-java" % "2.6.0" 

scalacOptions += "-deprecation"
