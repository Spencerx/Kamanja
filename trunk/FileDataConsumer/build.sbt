// put this at the top of the file
import sbt._
import AssemblyKeys._
import sbt.Keys._

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
  case x if x endsWith "com\\ligadata\\kamanja\\metadataload\\MetadataLoad.class" => MergeStrategy.first
  case x if x endsWith "com/ligadata/kamanja/metadataload/MetadataLoad.class" => MergeStrategy.first
  case x if x endsWith "org/apache/commons/beanutils/BasicDynaBean.class" => MergeStrategy.last
  case x if x endsWith "com\\esotericsoftware\\minlog\\Log.class" => MergeStrategy.first
  case x if x endsWith "com\\esotericsoftware\\minlog\\Log$Logger.class" => MergeStrategy.first
  case x if x endsWith "com/esotericsoftware/minlog/Log.class" => MergeStrategy.first
  case x if x endsWith "com/esotericsoftware/minlog/Log$Logger.class" => MergeStrategy.first
  case x if x endsWith "com\\esotericsoftware\\minlog\\pom.properties" => MergeStrategy.first
  case x if x endsWith "com/esotericsoftware/minlog/pom.properties" => MergeStrategy.first
  case x if x contains "com.esotericsoftware.minlog\\minlog\\pom.properties" => MergeStrategy.first
  case x if x contains "com.esotericsoftware.minlog/minlog/pom.properties" => MergeStrategy.first
  case x if x contains "org\\objectweb\\asm\\" => MergeStrategy.last
  case x if x contains "org/objectweb/asm/" => MergeStrategy.last
  case x if x contains "org/apache/commons/collections" =>  MergeStrategy.last
  case x if x contains "org\\apache\\commons\\collections" =>  MergeStrategy.last
    case x if x contains "com.fasterxml.jackson.core" => MergeStrategy.first
    case x if x contains "com/fasterxml/jackson/core" => MergeStrategy.first
    case x if x contains "com\\fasterxml\\jackson\\core" => MergeStrategy.first
    case x if x contains "commons-logging" => MergeStrategy.first
  case "log4j.properties" => MergeStrategy.first
  case "unwanted.txt"     => MergeStrategy.discard
  case x => old(x)
}
}

excludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
  val excludes = Set("commons-beanutils-1.7.0.jar", "google-collections-1.0.jar", "commons-collections4-4.0.jar" )
  cp filter { jar => excludes(jar.data.getName) }
}


name := "FileDataConsumer"

version := "0.1.0"

libraryDependencies ++= {
  val sprayVersion = "1.3.1"
  val akkaVersion = "2.3.2"
  val scalaVersion = "2.10.4"
  Seq(
   // "io.spray" % "spray-can" % sprayVersion,
   // "io.spray" % "spray-routing" % sprayVersion,
   // "io.spray" % "spray-testkit" % sprayVersion,
   // "io.spray" % "spray-client" % sprayVersion,
   // "io.spray" %%  "spray-json" % "1.2.5",
    "org.apache.kafka" % "kafka_2.10" % "0.8.1.1"
      exclude("javax.jms", "jms")
      exclude("com.sun.jdmk", "jmxtools")
      exclude("com.sun.jmx", "jmxri"),
   // "org.apache.kafka" % "kafka_2.10" % "0.8.2.0",
  //  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  //  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
   // "ch.qos.logback" % "logback-classic" % "1.0.12",
  //  "org.apache.camel" % "camel-core" % "2.9.2",
    "org.scala-lang" % "scala-actors" % scalaVersion
  )
}
