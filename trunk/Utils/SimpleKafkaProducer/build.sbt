import AssemblyKeys._ // put this at the top of the file
import sbt._
import Keys._

shellPrompt := { state =>  "sbt (%s)> ".format(Project.extract(state).currentProject.id) }

assemblySettings

assemblyOption in assembly ~= { _.copy(prependShellScript = Some(defaultShellScript)) }

jarName in assembly := { s"${name.value}-${version.value}" }

// for some reason the merge strategy for non ligadata classes are not working and thus added those conflicting jars in exclusions
// this may result some run time errors

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    // case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
    // case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
    case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
    case x if x endsWith "google/common/annotations/GwtCompatible.class" => MergeStrategy.first
    case x if x endsWith "google/common/annotations/GwtIncompatible.class" => MergeStrategy.first
    case x if x endsWith "/apache/commons/beanutils/BasicDynaBean.class" => MergeStrategy.first
	case x if x endsWith "org/apache/commons/beanutils/BasicDynaBean.class" => MergeStrategy.last
    case x if x contains "org/apache/commons/collections" =>  MergeStrategy.last
    case x if x contains "org\\apache\\commons\\collections" =>  MergeStrategy.last
    case "log4j.properties" => MergeStrategy.first
    case "unwanted.txt"     => MergeStrategy.discard
    case x => old(x)
  }
}

excludedJars in assembly <<= (fullClasspath in assembly) map { cp => 
  val excludes = Set("commons-beanutils-1.7.0.jar", "google-collections-1.0.jar", "commons-collections4-4.0.jar" )
  cp filter { jar => excludes(jar.data.getName) }
}

name := "SimpleKafkaProducer"

version := "0.1.0"

scalaVersion := "2.10.4"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.4.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.4.1"

resolvers += "Apache repo" at "https://repository.apache.org/content/repositories/releases"

libraryDependencies ++= Seq("org.apache.kafka" % "kafka_2.10" % "0.8.1.1"
                              exclude("javax.jms", "jms")
                              exclude("com.sun.jdmk", "jmxtools")
                              exclude("com.sun.jmx", "jmxri")
)

libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-actors" % _)

libraryDependencies += "org.json4s" %% "json4s-native" % "3.2.9" 

libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.9" 

