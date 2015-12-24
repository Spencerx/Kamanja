name := "KafkaSimpleInputOutputAdapters"

version := "1.0"

scalaVersion := "2.10.4"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
resolvers += "Apache repo" at "https://repository.apache.org/content/repositories/releases"

libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.4.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.4.1"

libraryDependencies ++= Seq("org.apache.kafka" % "kafka_2.10" % "0.8.1.1"
                              exclude("javax.jms", "jms")
                              exclude("com.sun.jdmk", "jmxtools")
                              exclude("com.sun.jmx", "jmxri")
)

libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-actors" % _)

libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.9" 

libraryDependencies += "org.json4s" %% "json4s-native" % "3.2.9"

