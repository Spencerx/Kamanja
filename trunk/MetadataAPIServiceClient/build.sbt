name := "MetadataAPIServiceClient"

version := "0.1"

scalaVersion := "2.10.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= Seq(
//    "org.slf4j" % "slf4j-api" % "1.7.10",
    "ch.qos.logback" % "logback-core"    % "1.0.13",
    "ch.qos.logback" % "logback-classic" % "1.0.13",
    "uk.co.bigbeeconsultants" %% "bee-client" % "0.28.0",
    "org.apache.httpcomponents" % "httpclient" % "4.1.2"
)

libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.4.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.4.1"

libraryDependencies += "org.json4s" %% "json4s-native" % "3.2.9" 

libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.9"

resolvers += "Big Bee Consultants" at "http://repo.bigbeeconsultants.co.uk/repo"
