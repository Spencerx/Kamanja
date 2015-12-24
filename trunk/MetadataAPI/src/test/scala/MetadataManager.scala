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

import java.io.File
import java.util.Properties

import com.ligadata.MetadataAPI.{ApiResult, MetadataAPIImpl => md}
import com.ligadata.Serialize.SerializerManager
import com.ligadata.kamanja.metadata.MdMgr
import com.ligadata.Exceptions.AlreadyExistsException
import com.ligadata.Utils.Utils.loadConfiguration

import scala.collection.mutable
import scala.io.Source
import org.apache.logging.log4j._

case class MetadataAPIProperties(var database: String = "hashmap", 
				 var databaseHost: String = "localhost", 
				 var databaseSchema: String = "metadata",
				 var dataDirectory: String = ConfigDefaults.dataDirectory,
                                 var classPath: String = ConfigDefaults.metadataClasspath, 
				 var znodeBasePath: String = "/ligadata",
                                 var zkConnStr: String = "localhost:2181", 
				 var modelExecLog: String = "true", 
				 var node_id: String = "1",
			         var serviceHost: String = "localhost",
			         var servicePort: String = "8081")

case class MetadataManagerException(message: String) extends Exception(message)

class MetadataManager(var config: MetadataAPIProperties) {

  private val logger = org.apache.logging.log4j.LogManager.getLogger(this.getClass)

  private val metadataDirResource = getClass.getResource("/Metadata")
  if( metadataDirResource == null ){
      throw new MetadataManagerException("Failed to retrieve resource value for '/Metadata'.");
  }

  private val metadataDir = new File(getClass.getResource("/Metadata").getPath)
  logger.info("metadataDir => " + metadataDir)

  private val scalaHome = System.getenv("SCALA_HOME")
  private val javaHome = System.getenv("JAVA_HOME")

  if(scalaHome == null || javaHome == null) {
    throw new MetadataManagerException("Environment Variable SCALA_HOME or JAVA_HOME not set")
  }

  /// Init is kept separate rather than as a part of the construction of the class in order to prevent execution of certain operations too soon.
  def initMetadataCfg: Unit = {
    if(scalaHome != null)
      md.GetMetadataAPIConfig.setProperty("SCALA_HOME", scalaHome)
    else {
      throw new MetadataManagerException("Failed to retrieve environmental variable 'SCALA_HOME'. You must set this variable in order to run tests.")
    }

    if(javaHome != null)
      md.GetMetadataAPIConfig.setProperty("JAVA_HOME", javaHome)
    else {
      throw new MetadataManagerException("Failed to retrieve environmental variable 'JAVA_HOME'. You must set this variable in order to run tests.")
    }

    val jarPathSystem: String = getClass.getResource("/jars/lib/system").getPath
    logger.info("jarPathSystem => " + jarPathSystem)

    val jarPathApp: String = getClass.getResource("/jars/lib/application").getPath
    logger.info("jarPathApp => " + jarPathApp)

    md.metadataAPIConfig.setProperty("ROOT_DIR", "")
    md.metadataAPIConfig.setProperty("GIT_ROOT", "")

    // loadConfiguration converts all the key values to lowercase
    val (prop, failStr) = com.ligadata.Utils.Utils.loadConfiguration(ConfigDefaults.dataStorePropertiesFile, true)
    if (failStr != null && failStr.size > 0) {
      logger.error(failStr)
      return
    }
    if (prop == null) {
      logger.error("Failed to parse the entries in " + ConfigDefaults.dataStorePropertiesFile)
      return
    }
    // Read datastore properties from src/test/resources/metadata/config/DataStore.properties
    val eProps1 = prop.propertyNames();
    while (eProps1.hasMoreElements()) {
      val key = eProps1.nextElement().asInstanceOf[String]
      val value = prop.getProperty(key);
      logger.info(key + " => " + value)
      md.metadataAPIConfig.setProperty(key,value)
    }

    config.database = md.metadataAPIConfig.getProperty("database")
    logger.info("DATABASE => " + config.database)

    md.metadataAPIConfig.setProperty("NODE_ID", config.node_id)
    md.metadataAPIConfig.setProperty("SERVICE_HOST", config.serviceHost)
    md.metadataAPIConfig.setProperty("SERVICE_PORT", config.servicePort)
    md.metadataAPIConfig.setProperty("DATABASE", config.database)
    md.metadataAPIConfig.setProperty("DATABASE_HOST", config.databaseHost)
    md.metadataAPIConfig.setProperty("DATABASE_SCHEMA", config.databaseSchema)
    var dsJson:String = null

    
    if( config.database.equalsIgnoreCase("sqlserver") || config.database.equalsIgnoreCase("mysql") ){
      md.metadataAPIConfig.setProperty("DATABASE_LOCATION", config.dataDirectory)
      dsJson = md.metadataAPIConfig.getProperty("metadatadatastore")
      logger.info("metadataDataStore => " + dsJson)
    }
    else{
      if( config.database.equalsIgnoreCase("hashmap") || config.database.equalsIgnoreCase("treemap") ){
	md.metadataAPIConfig.setProperty("DATABASE_LOCATION", config.dataDirectory)
	dsJson="{\"StoreType\": \"" + config.database + "\",\"SchemaName\": \"" + config.databaseSchema + "\",\"Location\": \"" + config.dataDirectory + "\"}"
      }
      else{
	md.metadataAPIConfig.setProperty("DATABASE_LOCATION", config.databaseHost)
	dsJson="{\"StoreType\": \"" + config.database + "\",\"SchemaName\": \"" + config.databaseSchema + "\",\"Location\": \"" + config.databaseHost + "\"}"
      }
    }
    md.metadataAPIConfig.setProperty("MetadataDataStore",dsJson)
    md.metadataAPIConfig.setProperty("METADATA_DATASTORE",dsJson)
    md.metadataAPIConfig.setProperty("JAR_TARGET_DIR", jarPathApp)
    md.metadataAPIConfig.setProperty("JAR_PATHS",  jarPathSystem + "," + jarPathApp)
    md.metadataAPIConfig.setProperty("MANIFEST_PATH", metadataDir.getAbsoluteFile + "/manifest.mf")
    md.metadataAPIConfig.setProperty("CLASSPATH", config.classPath)
    md.metadataAPIConfig.setProperty("NOTIFY_ENGINE", "YES")
    md.metadataAPIConfig.setProperty("ZNODE_PATH", config.znodeBasePath)
    md.metadataAPIConfig.setProperty("ZOOKEEPER_CONNECT_STRING", config.zkConnStr)
    md.metadataAPIConfig.setProperty("COMPILER_WORK_DIR", getClass.getResource("/jars/lib/workingdir").getPath)
    md.metadataAPIConfig.setProperty("API_LEADER_SELECTION_ZK_NODE", "/ligadata/metadata")
    md.metadataAPIConfig.setProperty("MODEL_EXEC_LOG", config.modelExecLog)
    md.metadataAPIConfig.setProperty("SECURITY_IMPL_JAR", jarPathSystem + "/simpleapacheshiroadapter_2.10-1.0.jar")
    md.metadataAPIConfig.setProperty("SECURITY_IMPL_CLASS", "com.ligadata.Security.SimpleApacheShiroAdapter")
    md.metadataAPIConfig.setProperty("DO_AUTH", "YES")
    md.metadataAPIConfig.setProperty("AUDIT_IMPL_JAR", jarPathSystem + "/auditadapters_2.10-1.0.jar")
    md.metadataAPIConfig.setProperty("DO_AUDIT", "NO")
    var db = config.database.toLowerCase
    db match {
      case "cassandra" => {
	md.metadataAPIConfig.setProperty("AUDIT_IMPL_CLASS", "com.ligadata.audit.adapters.AuditCassandraAdapter")
      }
      case "hbase" => {
	md.metadataAPIConfig.setProperty("AUDIT_IMPL_CLASS", "com.ligadata.audit.adapters.AuditHBaseAdapter")
      }
      case "hashmap" => {
	md.metadataAPIConfig.setProperty("AUDIT_IMPL_CLASS", "com.ligadata.audit.adapters.AuditHashMapAdapter")
      }
      case "treemap" => {
	md.metadataAPIConfig.setProperty("AUDIT_IMPL_CLASS", "com.ligadata.audit.adapters.AuditHashMapAdapter")
      }
      case "sqlserver" | "mysql" => {
	md.metadataAPIConfig.setProperty("AUDIT_IMPL_CLASS", "com.ligadata.audit.adapters.AuditCassandraAdapter")
      }
      case _ => {
	throw new MetadataManagerException("Unknown DataStoreType: " + db)
      }
    }
    md.metadataAPIConfig.setProperty("SSL_CERTIFICATE",metadataDir.getAbsoluteFile + "/certs/keystore.jks")
    md.metadataAPIConfig.setProperty("CONTAINER_FILES_DIR",metadataDir.getAbsoluteFile + "/container")
    md.metadataAPIConfig.setProperty("MESSAGE_FILES_DIR",metadataDir.getAbsoluteFile + "/message")
    md.metadataAPIConfig.setProperty("MODEL_FILES_DIR",metadataDir.getAbsoluteFile + "/model")
    md.metadataAPIConfig.setProperty("FUNCTION_FILES_DIR",metadataDir.getAbsoluteFile + "/function")
    md.metadataAPIConfig.setProperty("CONCEPT_FILES_DIR",metadataDir.getAbsoluteFile + "/concept")
    md.metadataAPIConfig.setProperty("TYPE_FILES_DIR",metadataDir.getAbsoluteFile + "/type")
    md.metadataAPIConfig.setProperty("CONFIG_FILES_DIR",metadataDir.getAbsoluteFile + "/config")
    md.propertiesAlreadyLoaded = true
  }
}
