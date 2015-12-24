/*
 * Copyright 2015 ligaDATA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ligadata.automation.unittests.api.setup

import scala.io._
import java.util.Date
import java.io._
import java.nio.channels._
import sys.process._
import org.apache.logging.log4j._

/**
 * Created by wtarver on 1/28/15.
 * Some basic defaults for use in manual creation of configuration for instantiation of certain classes. Meant for typical usage.
 * Custom extensions need custom configuration.
 * This may be deprecated later.
 */
object ConfigDefaults {

  private val loggerName = this.getClass.getName
  private val logger = LogManager.getLogger(loggerName)

  private val RootDir = "./MetadataAPI/target/scala-2.10/test-classes"
  private val targetLibDir = RootDir + "/jars/lib/system"
  private val appLibDir = RootDir + "/jars/lib/application"
  private val workDir = RootDir + "/jars/lib/workingdir"

  private def copyFile(sourceFile:File, destFile:File)  {
    
    if(!destFile.exists()) {
        destFile.createNewFile();
    }

    var source:FileChannel = null;
    var destination:FileChannel = null;

    source = new FileInputStream(sourceFile).getChannel();
    destination = new FileOutputStream(destFile).getChannel();
    destination.transferFrom(source, 0, source.size());
    source.close()
    destination.close()
  }

  private def createDirectory(dirName:String){
    var dir = new File(dirName)
    if( ! dir.exists() ){
      dir.mkdir()
    }
  }

  private def copy(path: File): Unit = {
    if(path.isDirectory ){
      if( path.getPath.contains(targetLibDir) ){
	return
      }
      Option(path.listFiles).map(_.toList).getOrElse(Nil).foreach(f => {
        if (f.isDirectory){
          copy(f)
	}
        else if (f.getPath.endsWith(".jar")) {
          try {
	    //logger.info("Copying " + f + "," + "(file size => " + f.length() + ") to " + targetLibDir + "/" + f.getName)
	    copyFile(f,new File(targetLibDir + "/" + f.getName))
          }
          catch {
            case e: Exception => throw new Exception("Failed to copy file: " + f + " with exception:\n" + e)
          }
        }
      })
    }
  }

  createDirectory(targetLibDir)
  createDirectory(appLibDir)
  createDirectory(workDir)

  copy(new File("lib_managed"))
  copy(new File("."))

  def jarResourceDir = getClass.getResource("/jars/lib/system").getPath

  logger.info("jarResourceDir " + jarResourceDir)
  

  def envContextClassName: String = "com.ligadata.SimpleEnvContextImpl.SimpleEnvContextImpl$"
  def envContextDependecyJarList: List[String] = List("log4j-1.2.17.jar","kamanjabase_2.10-1.0.jar","metadata_2.10-1.0.jar","serialize_2.10-1.0.jar","storage_2.10-0.0.0.2.jar","metrics-core-3.0.2.jar","cassandra-driver-core-2.1.2.jar","kryo-2.21.jar","minlog-1.2.jar","reflectasm-1.07-shaded.jar","jackson-annotations-2.3.0.jar","jackson-core-2.3.1.jar","jackson-databind-2.3.1.jar","findbugs-annotations-1.3.9-1.jar","jsr305-1.3.9.jar","google-collections-1.0.jar","guava-16.0.1.jar","protobuf-java-2.6.0.jar","java-xmlbuilder-0.4.jar","jsch-0.1.46.jar","compress-lzf-0.9.1.jar","je-4.0.92.jar","jersey-core-1.9.jar","jersey-json-1.9.jar","jersey-server-1.9.jar","jaxb-impl-2.2.3-1.jar","paranamer-2.6.jar","chill-java-0.3.6.jar","chill_2.10-0.3.6.jar","commons-beanutils-1.8.3.jar","commons-cli-1.2.jar","commons-codec-1.9.jar","commons-collections-3.2.1.jar","commons-collections4-4.0.jar","commons-configuration-1.7.jar","commons-dbcp-1.2.2.jar","commons-digester-1.8.1.jar","commons-el-1.0.jar","commons-httpclient-3.1.jar","commons-io-2.4.jar","commons-lang-2.6.jar","commons-logging-1.1.3.jar","commons-net-3.1.jar","commons-pool-1.6.jar","netty-3.9.0.Final.jar","activation-1.1.jar","jsp-api-2.1.jar","servlet-api-2.5.jar","jaxb-api-2.2.2.jar","stax-api-1.0-2.jar","jline-1.0.jar","joda-time-2.3.jar","jets3t-0.9.0.jar","jna-4.0.0.jar","avro-1.7.4.jar","commons-compress-1.4.1.jar","commons-math3-3.2.jar","hadoop-annotations-2.4.1.jar","hadoop-auth-2.4.1.jar","hadoop-common-2.4.1.jar","hbase-client-0.98.4-hadoop2.jar","hbase-common-0.98.4-hadoop2.jar","hbase-protocol-0.98.4-hadoop2.jar","httpclient-4.2.5.jar","httpcore-4.2.4.jar","zookeeper-3.4.6.jar","htrace-core-2.04.jar","jackson-core-asl-1.9.2.jar","jackson-jaxrs-1.8.3.jar","jackson-mapper-asl-1.9.2.jar","jackson-xc-1.8.3.jar","jettison-1.1.jar","hamcrest-core-1.3.jar","jdom-1.1.jar","joda-convert-1.6.jar","json4s-ast_2.10-3.2.10.jar","json4s-core_2.10-3.2.10.jar","json4s-jackson_2.10-3.2.10.jar","json4s-native_2.10-3.2.10.jar","mapdb-1.0.6.jar","jetty-util-6.1.26.jar","jetty-6.1.26.jar","objenesis-1.2.jar","asm-commons-4.0.jar","asm-tree-4.0.jar","asm-4.0.jar","scalap-2.10.0.jar","test-interface-1.0.jar","quasiquotes_2.10.4-2.0.0-M6.jar","scalatest_2.10-2.2.0.jar","slf4j-api-1.7.10.jar","slf4j-log4j12-1.7.5.jar","xz-1.0.jar","snappy-java-1.0.5.jar","jasper-compiler-5.5.23.jar","jasper-runtime-5.5.23.jar","voldemort-0.96.jar","xmlenc-0.52.jar")
  def envContextJarName = "simpleenvcontextimpl_2.10-1.0.jar"

  def nodeClassPath: String = ".:" + jarResourceDir + "/metadata_2.10-1.0.jar:" + jarResourceDir + "/basefunctions_2.10-0.1.0.jar:" + jarResourceDir + "/messagedef_2.10-1.0.jar:" + jarResourceDir + "/methodextractor_2.10-1.0.jar:" + jarResourceDir + "/pmmlcompiler_2.10-1.0.jar:" + jarResourceDir + "/kamanjabase_2.10-1.0.jar:" + jarResourceDir + "/bootstrap_2.10-1.0.jar:" + jarResourceDir + "/joda-time-2.3.jar:" + jarResourceDir + "/joda-convert-1.6.jar:" + jarResourceDir + "/basetypes_2.10-0.1.0.jar:" + jarResourceDir + "/pmmludfs_2.10-1.0.jar:" + jarResourceDir + "/pmmlruntime_2.10-1.0.jar:" + jarResourceDir + "/json4s-native_2.10-3.2.9.jar:" + jarResourceDir + "/json4s-core_2.10-3.2.9.jar:" + jarResourceDir + "/json4s-ast_2.10-3.2.9.jar:" + jarResourceDir + "/jackson-databind-2.3.1.jar:" + jarResourceDir + "/jackson-annotations-2.3.0.jar:" + jarResourceDir + "/json4s-jackson_2.10-3.2.9.jar:" + jarResourceDir + "/jackson-core-2.3.1.jar:" + jarResourceDir + "/log4j-1.2.17.jar" + jarResourceDir + "/guava-16.0.1.jar" + jarResourceDir + "/exceptions_2.10-1.0.jar" + jarResourceDir + "/scala-reflect-2.10.4.jar" + jarResourceDir + "/scala-library-2.10.4.jar" + jarResourceDir + "/jsr305-1.3.9.jar"

  def adapterDepJars: List[String] = List("jopt-simple-3.2.jar", "kafka_2.10-0.8.1.1.jar", "metrics-core-2.2.0.jar", "zkclient-0.3.jar", "kamanjabase_2.10-1.0.jar")

  val scala_home = System.getenv("SCALA_HOME")

  def scalaJarsClasspath = s"$scala_home/lib/typesafe-config.jar:$scala_home/lib/scala-actors.jar:$scala_home/lib/akka-actors.jar:$scala_home/lib/scalap.jar:$scala_home/lib/jline.jar:$scala_home/lib/scala-swing.jar:$scala_home/lib/scala-library.jar:$scala_home/lib/scala-actors-migration.jar:$scala_home/lib/scala-reflect.jar:$scala_home/lib/scala-compiler.jar"

  def dataDirectory = getClass.getResource("/DataDirectories").getPath
  logger.info("dataDirectory => " + dataDirectory)

  def metadataDirectory = getClass.getResource("/Metadata").getPath
  logger.info("metadataDirectory => " + metadataDirectory)

  def dataStorePropertiesFile:String = metadataDirectory + "/config/DataStore.properties"

  def metadataClasspath: String = jarResourceDir + "/metadata_2.10-1.0.jar:" + jarResourceDir + "/basefunctions_2.10-0.1.0.jar:" + jarResourceDir + "/messagedef_2.10-1.0.jar:" + jarResourceDir + "/methodextractor_2.10-1.0.jar:" + jarResourceDir + "/pmmlcompiler_2.10-1.0.jar:" + jarResourceDir + "/kamanjabase_2.10-1.0.jar:" + jarResourceDir + "/bootstrap_2.10-1.0.jar:" + jarResourceDir + "/joda-time-2.8.2.jar:" + jarResourceDir + "/joda-convert-1.6.jar:" + jarResourceDir + "/basetypes_2.10-0.1.0.jar:" + jarResourceDir + "/pmmludfs_2.10-1.0.jar:" + jarResourceDir + "/pmmlruntime_2.10-1.0.jar:" + jarResourceDir + "/json4s-native_2.10-3.2.9.jar:" + jarResourceDir + "/json4s-core_2.10-3.2.9.jar:" + jarResourceDir + "/json4s-ast_2.10-3.2.9.jar:" + jarResourceDir + "/jackson-databind-2.3.1.jar:" + jarResourceDir + "/jackson-annotations-2.3.0.jar:" + jarResourceDir + "/json4s-jackson_2.10-3.2.9.jar:" + jarResourceDir + "/jackson-core-2.3.1.jar:" + jarResourceDir + "/log4j-1.2.17.jar:" + jarResourceDir + "/guava-16.0.1.jar:" + jarResourceDir + "/exceptions_2.10-1.0.jar:" + jarResourceDir + "/scala-reflect-2.10.4.jar:" + jarResourceDir + "/scala-library-2.10.4.jar:" + jarResourceDir + "/basetypes_2.10-0.1.0.jar:" + jarResourceDir + "/metadata_2.10-1.0.jar:" + jarResourceDir + "/kvbase_2.10-0.1.0.jar:" + jarResourceDir + "/datadelimiters_2.10-1.0.jar:" + jarResourceDir + "/jsr305-1.3.9.jar"
}
