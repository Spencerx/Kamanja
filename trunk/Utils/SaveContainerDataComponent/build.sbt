import AssemblyKeys._ // put this at the top of the file
import sbt._
import Keys._

shellPrompt := { state =>  "sbt (%s)> ".format(Project.extract(state).currentProject.id) }

assemblySettings

assemblyOption in assembly ~= { _.copy(prependShellScript = Some(defaultShellScript)) }

jarName in assembly := { s"${name.value}-${version.value}" }

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

unmanagedJars in Compile <<= baseDirectory map { base => (base ** "*.jar").classpath }

name := "SaveContainerDataComponent"

version := "1.0"

scalaVersion := "2.10.4"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.4.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.4.1"

libraryDependencies += "org.json4s" %% "json4s-native" % "3.2.9" 

libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.9" 
