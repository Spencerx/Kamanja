name := "MySql"

version := "0.1.0"

scalaVersion := "2.10.4"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.0" % "test"

libraryDependencies += "org.json4s" %% "json4s-native" % "3.2.9" 

libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.9" 

libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.4.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.4.1"

libraryDependencies += "commons-dbcp" % "commons-dbcp" % "1.4"

libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.36"
