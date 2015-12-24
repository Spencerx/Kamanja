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

package com.ligadata.metadataapiserviceclient

import org.scalatest.Assertions._
import scala.collection.mutable.{ ArrayBuffer }
import uk.co.bigbeeconsultants.http._
import uk.co.bigbeeconsultants.http.response._
import uk.co.bigbeeconsultants.http.request._
import uk.co.bigbeeconsultants.http.header._
import uk.co.bigbeeconsultants.http.header.MediaType._
import request.Request
import header.Headers
import header.HeaderName._

import java.net.URL
import org.apache.logging.log4j._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import java.io._
import java.util.Properties
import scala.io._
import com.ligadata.Serialize._
import com.ligadata.Exceptions._
import com.ligadata.Exceptions.StackTrace

case class ApiResultInfo(statusCode:Int, statusDescription: String, resultData: String)
case class ApiResultJsonProxy(ApiResults: ApiResultInfo)

object TestApiService {
  val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)

  lazy val metadataAPIConfig = new Properties()
  var host_url: String = ""

  var propertiesAlreadyLoaded = false;

  val config = Config(connectTimeout = 120000, readTimeout = 120000)

  def loadConfiguration(configFile: String, keysLowerCase: Boolean): (Properties, String) = {
    var configs: Properties = null
    var failStr: String = null
    try {
      val file: File = new File(configFile);
      if (file.exists()) {
        val input: InputStream = new FileInputStream(file)
        try {
          // Load configuration
          configs = new Properties()
          configs.load(input);
        } catch {
          case e: Exception =>
            val stackTrace = StackTrace.ThrowableTraceString(e)
            logger.debug("StackTrace:"+stackTrace)
            failStr = "Failed to load configuration. Message:" + e.getMessage+"\nStackTrace:"+stackTrace
            configs = null
        } finally {
          input.close();
        }
        if (keysLowerCase && configs != null) {
          val it = configs.entrySet().iterator()
          val lowercaseconfigs = new Properties()
          while (it.hasNext()) {
            val entry = it.next();
            lowercaseconfigs.setProperty(entry.getKey().asInstanceOf[String].toLowerCase, entry.getValue().asInstanceOf[String])
          }
          configs = lowercaseconfigs
        }
      } else {
        failStr = "Configuration file not found : " + configFile
        configs = null
      }
    } catch {
      case e: Exception =>
        val stackTrace = StackTrace.ThrowableTraceString(e)
        failStr = "Invalid Configuration. Message: " + e.getMessage()
        logger.error("Error:"+failStr)
        configs = null
    }
    return (configs, failStr)
  }

  @throws(classOf[MissingPropertyException])
  @throws(classOf[InvalidPropertyException])
  def readMetadataAPIConfigFromPropertiesFile(configFile: String): Unit = {
    try {
      if (propertiesAlreadyLoaded) {
        return ;
      }

      val (prop, failStr) = loadConfiguration(configFile.toString, true)
      if (failStr != null && failStr.size > 0) {
        logger.error(failStr)
        return
      }
      if (prop == null) {
        logger.error("Failed to load configuration")
        return
      }

      var root_dir = System.getenv("HOME")
      var git_root = root_dir + "/github"

      val eProps1 = prop.propertyNames();
      while (eProps1.hasMoreElements()) {
        val key = eProps1.nextElement().asInstanceOf[String]
        val value = prop.getProperty(key);
        logger.debug("The key=" + key + "; value=" + value);

        if (key.equalsIgnoreCase("ROOT_DIR")) {
          root_dir = value
          logger.debug("ROOT_DIR => " + root_dir)
        } else if (key.equalsIgnoreCase("GIT_ROOT")) {
          git_root = value
          logger.debug("GIT_ROOT => " + git_root)
        }
      }

      var database = "hashmap"
      var database_host = "localhost"
      var database_schema = "metadata"
      var database_location = "/tmp"
      var jar_target_dir = "/tmp/KamanjaInstall"
      var scala_home = root_dir + "/scala-2.10.4"
      var java_home = root_dir + "/jdk1.8.0_05"
      var manifest_path = git_root + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Models/manifest.mf"
      var classpath = ".:/tmp/KamanjaInstall/metadata_2.10-1.0.jar:/tmp/KamanjaInstall/basefunctions_2.10-0.1.0.jar:/tmp/KamanjaInstall/messagedef_2.10-1.0.jar:/tmp/KamanjaInstall/methodextractor_2.10-1.0.jar:/tmp/KamanjaInstall/pmmlcompiler_2.10-1.0.jar:/tmp/KamanjaInstall/bankenvcontext_2.10-1.0.jar:/tmp/KamanjaInstall/kamanjabase_2.10-1.0.jar:/tmp/KamanjaInstall/bankbootstrap_2.10-1.0.jar:/tmp/KamanjaInstall/bankmsgsandcontainers_2.10-1.0.jar:/tmp/KamanjaInstall/medicalbootstrap_2.10-1.0.jar:/tmp/KamanjaInstall/joda-time-2.3.jar:/tmp/KamanjaInstall/joda-convert-1.6.jar:/tmp/KamanjaInstall/basetypes_2.10-0.1.0.jar:/tmp/KamanjaInstall/pmmludfs_2.10-1.0.jar:/tmp/KamanjaInstall/pmmlruntime_2.10-1.0.jar:/tmp/KamanjaInstall/json4s-native_2.10-3.2.9.jar:/tmp/KamanjaInstall/json4s-core_2.10-3.2.9.jar:/tmp/KamanjaInstall/json4s-ast_2.10-3.2.9.jar:/tmp/KamanjaInstall/jackson-databind-2.3.1.jar:/tmp/KamanjaInstall/jackson-annotations-2.3.0.jar:/tmp/KamanjaInstall/json4s-jackson_2.10-3.2.9.jar:/tmp/KamanjaInstall/jackson-core-2.3.1.jar:/tmp/KamanjaInstall/log4j-1.2.17.jar"
      var notify_engine = "NO"
      var znode_path = "/ligadata/metadata"
      var zookeeper_connect_string = "localhost:2181"
      var node_id = "localhost:2181"
      var service_host = "localhost"
      var service_port = "8081"
      var api_leader_selection_zk_node = "/ligadata/metadata"
      var zk_session_timeout_ms = "4000"
      var zk_connection_timeout_ms = "30000"
      var config_files_dir = git_root + "/Kamanja/trunk/SampleApplication/Medical/Configs"
      var model_files_dir = git_root + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Models"
      var message_files_dir = git_root + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Messages"
      var container_files_dir = git_root + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Containers"
      var function_files_dir = git_root + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Functions"
      var concept_files_dir = git_root + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Concepts"
      var type_files_dir = git_root + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Types"
      var outputmessage_files_dir = git_root + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/OutputMsgs"
      var compiler_work_dir = root_dir + "/tmp"
      var model_exec_log = "false"

      val eProps2 = prop.propertyNames();
      while (eProps2.hasMoreElements()) {
        val key = eProps2.nextElement().asInstanceOf[String]
        val value = prop.getProperty(key);
        logger.debug("The key=" + key + "; value=" + value);
        if (key.equalsIgnoreCase("DATABASE") || key.equalsIgnoreCase("MetadataStoreType")) {
          database = value
          logger.debug("database => " + database)
        } else if (key.equalsIgnoreCase("DATABASE_HOST") || key.equalsIgnoreCase("MetadataLocation")) {
          database_host = value
          logger.debug("database_host => " + database_host)
        } else if (key.equalsIgnoreCase("DATABASE_SCHEMA") || key.equalsIgnoreCase("MetadataSchemaName")) {
          database_schema = value
          logger.debug("database_schema(applicable to cassandra only) => " + database_schema)
        } else if (key.equalsIgnoreCase("DATABASE_LOCATION") || key.equalsIgnoreCase("MetadataLocation")) {
          database_location = value
          logger.debug("database_location(applicable to treemap or hashmap only) => " + database_location)
        } else if (key.equalsIgnoreCase("JAR_TARGET_DIR")) {
          jar_target_dir = value
          logger.debug("JAR_TARGET_DIR => " + jar_target_dir)
        } else if (key.equalsIgnoreCase("SCALA_HOME")) {
          scala_home = value
          logger.debug("SCALA_HOME => " + scala_home)
        } else if (key.equalsIgnoreCase("JAVA_HOME")) {
          java_home = value
          logger.debug("JAVA_HOME => " + java_home)
        } else if (key.equalsIgnoreCase("MANIFEST_PATH")) {
          manifest_path = value
          logger.debug("MANIFEST_PATH => " + manifest_path)
        } else if (key.equalsIgnoreCase("CLASSPATH")) {
          classpath = value
          logger.debug("CLASSPATH => " + classpath)
        } else if (key.equalsIgnoreCase("NOTIFY_ENGINE")) {
          notify_engine = value
          logger.debug("NOTIFY_ENGINE => " + notify_engine)
        } else if (key.equalsIgnoreCase("ZNODE_PATH")) {
          znode_path = value
          logger.debug("ZNODE_PATH => " + znode_path)
        } else if (key.equalsIgnoreCase("ZOOKEEPER_CONNECT_STRING")) {
          zookeeper_connect_string = value
          logger.debug("ZOOKEEPER_CONNECT_STRING => " + zookeeper_connect_string)
        } else if (key.equalsIgnoreCase("NODE_ID")) {
          node_id = value
          logger.debug("NODE_ID => " + node_id)
        } else if (key.equalsIgnoreCase("SERVICE_HOST")) {
          service_host = value
          logger.debug("SERVICE_HOST => " + service_host)
        } else if (key.equalsIgnoreCase("SERVICE_PORT")) {
          service_port = value
          logger.debug("SERVICE_PORT => " + service_port)
        } else if (key.equalsIgnoreCase("API_LEADER_SELECTION_ZK_NODE")) {
          api_leader_selection_zk_node = value
          logger.debug("API_LEADER_SELECTION_ZK_NODE => " + api_leader_selection_zk_node)
        } else if (key.equalsIgnoreCase("ZK_SESSION_TIMEOUT_MS")) {
          zk_session_timeout_ms = value
          logger.debug("ZK_SESSION_TIMEOUT_MS => " + zk_session_timeout_ms)
        } else if (key.equalsIgnoreCase("ZK_CONNECTION_TIMEOUT_MS")) {
          zk_connection_timeout_ms = value
          logger.debug("ZK_CONNECTION_TIMEOUT_MS => " + zk_connection_timeout_ms)
        } else if (key.equalsIgnoreCase("MODEL_FILES_DIR")) {
          model_files_dir = value
          logger.debug("MODEL_FILES_DIR => " + model_files_dir)
        } else if (key.equalsIgnoreCase("CONFIG_FILES_DIR")) {
          config_files_dir = value
          logger.debug("CONFIG_FILES_DIR => " + config_files_dir)
        } else if (key.equalsIgnoreCase("MESSAGE_FILES_DIR")) {
          message_files_dir = value
          logger.debug("MESSAGE_FILES_DIR => " + message_files_dir)
        } else if (key.equalsIgnoreCase("CONTAINER_FILES_DIR")) {
          container_files_dir = value
          logger.debug("CONTAINER_FILES_DIR => " + container_files_dir)
        } else if (key.equalsIgnoreCase("CONCEPT_FILES_DIR")) {
          concept_files_dir = value
          logger.debug("CONCEPT_FILES_DIR => " + concept_files_dir)
        } else if (key.equalsIgnoreCase("FUNCTION_FILES_DIR")) {
          function_files_dir = value
          logger.debug("FUNCTION_FILES_DIR => " + function_files_dir)
        } else if (key.equalsIgnoreCase("TYPE_FILES_DIR")) {
          type_files_dir = value
          logger.debug("TYPE_FILES_DIR => " + type_files_dir)
        } else if (key.equalsIgnoreCase("COMPILER_WORK_DIR")) {
          compiler_work_dir = value
          logger.debug("COMPILER_WORK_DIR => " + compiler_work_dir)
        } else if (key.equalsIgnoreCase("MODEL_EXEC_LOG")) {
          model_exec_log = value
          logger.debug("MODEL_EXEC_LOG => " + model_exec_log)
        } else if (key.equalsIgnoreCase("OUTPUTMESSAGE_FILES_DIR")) {
          outputmessage_files_dir = value
          logger.debug("OUTPUTMESSAGE_FILES_DIR => " + outputmessage_files_dir)
        }
      }

      metadataAPIConfig.setProperty("ROOT_DIR", root_dir)
      metadataAPIConfig.setProperty("DATABASE", database)
      metadataAPIConfig.setProperty("DATABASE_HOST", database_host)
      metadataAPIConfig.setProperty("DATABASE_SCHEMA", database_schema)
      metadataAPIConfig.setProperty("DATABASE_LOCATION", database_location)
      metadataAPIConfig.setProperty("GIT_ROOT", git_root)
      metadataAPIConfig.setProperty("JAR_TARGET_DIR", jar_target_dir)
      metadataAPIConfig.setProperty("SCALA_HOME", scala_home)
      metadataAPIConfig.setProperty("JAVA_HOME", java_home)
      metadataAPIConfig.setProperty("MANIFEST_PATH", manifest_path)
      metadataAPIConfig.setProperty("CLASSPATH", classpath)
      metadataAPIConfig.setProperty("NOTIFY_ENGINE", notify_engine)
      metadataAPIConfig.setProperty("ZNODE_PATH", znode_path)
      metadataAPIConfig.setProperty("ZOOKEEPER_CONNECT_STRING", zookeeper_connect_string)
      metadataAPIConfig.setProperty("NODE_ID", node_id)
      metadataAPIConfig.setProperty("SERVICE_HOST", service_host)
      metadataAPIConfig.setProperty("SERVICE_PORT", service_port)
      metadataAPIConfig.setProperty("API_LEADER_SELECTION_ZK_NODE", api_leader_selection_zk_node)
      metadataAPIConfig.setProperty("ZK_SESSION_TIMEOUT_MS", zk_session_timeout_ms)
      metadataAPIConfig.setProperty("ZK_CONNECTION_TIMEOUT_MS", zk_connection_timeout_ms)
      metadataAPIConfig.setProperty("CONFIG_FILES_DIR", config_files_dir)
      metadataAPIConfig.setProperty("MODEL_FILES_DIR", model_files_dir)
      metadataAPIConfig.setProperty("TYPE_FILES_DIR", type_files_dir)
      metadataAPIConfig.setProperty("FUNCTION_FILES_DIR", function_files_dir)
      metadataAPIConfig.setProperty("CONCEPT_FILES_DIR", concept_files_dir)
      metadataAPIConfig.setProperty("MESSAGE_FILES_DIR", message_files_dir)
      metadataAPIConfig.setProperty("CONTAINER_FILES_DIR", container_files_dir)
      metadataAPIConfig.setProperty("COMPILER_WORK_DIR", compiler_work_dir)
      metadataAPIConfig.setProperty("MODEL_EXEC_LOG", model_exec_log)
      metadataAPIConfig.setProperty("OUTPUTMESSAGE_FILES_DIR", outputmessage_files_dir)

      propertiesAlreadyLoaded = true;

    } catch {
      case e: Exception =>
        logger.error("Failed to load configuration: " + e.getMessage)
        sys.exit(1)
    }
  }

  def GetJarAsArrayOfBytes(jarName: String): Array[Byte] = {
    try {
      val iFile = new File(jarName)
      if (!iFile.exists) {
        throw new FileNotFoundException("Jar file (" + jarName + ") is not found: ")
      }

      val bis = new BufferedInputStream(new FileInputStream(iFile));
      val baos = new ByteArrayOutputStream();
      var readBuf = new Array[Byte](1024) // buffer size

      // read until a single byte is available
      while (bis.available() > 0) {
        val c = bis.read();
        baos.write(c)
      }
      bis.close();
      baos.toByteArray()
    } catch {
      case e: IOException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        throw new FileNotFoundException("Failed to Convert the Jar (" + jarName + ") to array of bytes: " + e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("StackTrace:"+stackTrace)
        throw new InternalErrorException("Failed to Convert the Jar (" + jarName + ") to array of bytes: " + e.getMessage())
      }
    }
  }

  def GetHttpResponse(reqType: String, url: String, body: Option[String], bodyType: MediaType): String = {
    var response: Response = null

    try {
      val httpClient = new HttpClient(config)
      if (body == None) {
        logger.debug(reqType + ":URL => " + url)
        if (reqType.equalsIgnoreCase("get")) {
          val request = Request.get(new URL(url))
          response = httpClient.makeRequest(request)
          //response = httpClient.get(new URL(url))
          //println(response.toString)
          return response.body.asString
        } else {
          response = httpClient.delete(new URL(url))
          //println(response.toString)
          return response.body.asString
        }
      } else {
        logger.debug(reqType + "URL => " + url + ", parameter => " + body.get)
        bodyType match {
          case TEXT_PLAIN | TEXT_XML | APPLICATION_JSON => {
            if (reqType.equalsIgnoreCase("put")) {
              val requestBody = new StringRequestBody(body.get, bodyType)
              response = httpClient.put(new URL(url), requestBody)
            } else {
              val requestBody = new StringRequestBody(body.get, bodyType)
              response = httpClient.post(new URL(url), Some(requestBody))
            }
          }
          case APPLICATION_OCTET_STREAM => {
            // assuming the parameter is name of the binary file such as jar file
            val ba = GetJarAsArrayOfBytes(body.get)
            val requestBody = new BinaryRequestBody(ba, bodyType)
            response = httpClient.put(new URL(url), requestBody)
          }
          case _ => {
            val errStr = "The MediaType " + bodyType + " is not supported yet"
            throw new Exception(errStr)
          }
        }
      }
      response.body.asString
    } catch {
      case e: Exception =>
       
        val errStr = "Failed to get response for the API call(" + url + "), status = " + response.status
        logger.error("Error:"+errStr)
        throw new Exception(errStr)
    }
  }

  def MakeHttpRequest(reqType: String, host_url: String, apiFunctionName: String, parameterType: String, parameterValue: String): String = {
    try {
      var url = host_url + "/api/" + apiFunctionName
      logger.debug(url + "   " + parameterType + " " + parameterValue)
      var bodyType: MediaType = TEXT_PLAIN
      parameterType match {
        case "STR" => bodyType = TEXT_PLAIN
        case "XML" => bodyType = TEXT_XML
        case "JSON" => bodyType = APPLICATION_JSON
        case "BINARY_FILE" => bodyType = APPLICATION_OCTET_STREAM
        case _ => throw new InvalidArgumentException("Invalid Argument in MakeHttpRequest: " + parameterType)
      }
      var apiParameters: Option[String] = None
      if (parameterValue != null) {
        apiParameters = Some(parameterValue)
      }

      GetHttpResponse(reqType, url, apiParameters, bodyType)
    } catch {
      case e: Exception =>{
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw new Exception(e.getMessage())
      }
    }
  }

  def IsValidDir(dirName: String): Boolean = {
    val iFile = new File(dirName)
    if (!iFile.exists) {
      logger.error("The File Path (" + dirName + ") is not found: ")
      false
    } else if (!iFile.isDirectory) {
      logger.error("The File Path (" + dirName + ") is not a directory: ")
      false
    } else
      true
  }

  def GetAllObjectKeys(objectType: String): Array[String] = {
    var objKeys: Array[String] = new Array[String](0)
    try {
      // val objKeysJson = MakeHttpRequest(host_url,"GetAllObjectKeys","STR",objectType)
      val objKeysJson = MakeHttpRequest("get", host_url, "keys/" + objectType, "STR", null)
      implicit val jsonFormats: Formats = DefaultFormats
      logger.debug("result => " + objKeysJson)
      val json1 = parse(objKeysJson).values.asInstanceOf[Map[String, Any]]
      val allValues = json1.getOrElse("APIResults", null).asInstanceOf[Map[String, Any]]
      if (allValues == null) {
        logger.error("BAD MESSAGE: No Results, need to investigate")
        return new Array[String](0)
      }

      val resultsValues = allValues.getOrElse("resultData", null).asInstanceOf[String]
      if (resultsValues == null) {
        logger.debug("No Objects Found")
        return new Array[String](0)
      }

      val objKeys: Array[String] = resultsValues.split(",")
      return objKeys
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        return null
      }
    }
  }

  def GetAllMetadataObjects = {
    var objKeys: Array[String] = new Array[String](0)
    try {
      //  val objKeysJson = MakeHttpRequest("get",host_url,"GetAllObjectKeys/ALL","STR",null)
      val objKeysJson = MakeHttpRequest("get", host_url, "keys/ALL", "STR", null)
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(objKeysJson)
      objKeys = json.extract[Array[String]]
      objKeys.foreach(k => { println(k) });
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def GetConfigObjects(objectType: String) = {
    try {
      println("---->" + objectType + "<---------")
      val objJson = MakeHttpRequest("get", host_url, "Config/" + objectType, "STR", null)
      logger.debug(objJson)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def MakeJsonStrForArgList(objKey: String, objectType: String): String = {
    try {
      val keyTokens = objKey.split("\\.")
      val nameSpace = keyTokens(0)
      val name = keyTokens(1)
      val version = keyTokens(2)
      val mdArg = new MetadataApiArg(objectType, nameSpace, name, version, "JSON")
      val argList = new Array[MetadataApiArg](1)
      argList(0) = mdArg
      val mdArgList = new MetadataApiArgList(argList.toList)
      val apiArgJson = JsonSerializer.SerializeApiArgListToJson(mdArgList)
      apiArgJson
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw new Exception("Failed to convert given object key into json string" + e.getMessage())
      }
    }
  }

  def GetObjects(objectType: String) {
    try {
      val keys = GetAllObjectKeys(objectType)
      if (keys.length == 0) {
        println("Sorry, No objects of type " + objectType + " available in the Metadata")
        return
      }

      println("\nPick the object of type " + objectType + " to be presented from the following list: ")
      var seq = 0
      keys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > keys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val key = keys(choice - 1)
      // val apiArgJson = MakeJsonStrForArgList(key,objectType)
      val apiName = "GetObjects"
      val apiResult = MakeHttpRequest("get", host_url, objectType + "/" + key, "JSON", null)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def GetModel {
    GetObjects("Model")
  }

  def GetMessage {
    GetObjects("Message")
  }

  def GetContainer {
    GetObjects("Container")
  }

  def GetFunction {
    GetObjects("Function")
  }
  def GetType {
    GetObjects("Type")
  }
  def GetConcept {
    GetObjects("Concept")
  }

  def GetAllObjects(objectType: String) {
    try {
      val keys = GetAllObjectKeys(objectType)
      if (keys.length == 0) {
        println("Sorry, No objects of type " + objectType + " available in the Metadata")
        return
      }

      var seq = 0
      keys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def GetAllModels {
    GetAllObjects("Model")
  }

  def GetAllMessages {
    GetAllObjects("Message")
  }

  def GetAllContainers {
    GetAllObjects("Container")
  }

  def GetAllFunctions {
    GetAllObjects("Function")
  }

  def GetAllTypes {
    GetAllObjects("Type")
  }

  def GetAllConcepts {
    GetAllObjects("Concept")
  }

  def GetAllNodes {
    GetConfigObjects("Node")
  }

  def GetAllClusters {
    GetConfigObjects("Cluster")
  }

  def GetAllClusterCfgs {
    GetConfigObjects("ClusterCfg")
  }

  def GetAllAdapters {
    GetConfigObjects("Adapter")
  }

  def GetAllConfigObjects {
    GetConfigObjects("ALL")
  }

  def GetAllOutputMsgs {
    GetAllObjects("OutputMsg")
  }
  def ActivateObjects(objectType: String) {
    try {

      val keys = GetAllObjectKeys(objectType)
      if (keys.length == 0) {
        println("Sorry, No objects of type " + objectType + " available in the Metadata")
        return
      }

      println("\nPick the object of type " + objectType + " to be presented from the following list: ")
      var seq = 0
      keys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > keys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val key = keys(choice - 1)
      val apiArgJson = MakeJsonStrForArgList(key, objectType)
      val apiName = "ActivateObjects"
      println("Activating Objects ->" + objectType + "/activate " + apiArgJson)
      val apiResult = MakeHttpRequest("put", host_url, objectType + "/activate", "JSON", apiArgJson)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def ActivateModel {
    ActivateObjects("Model")
  }

  def ActivateMessage {
    ActivateObjects("Message")
  }

  def ActivateContainer {
    ActivateObjects("Container")
  }

  def ActivateFunction {
    ActivateObjects("Function")
  }
  def ActivateType {
    ActivateObjects("Type")
  }
  def ActivateConcept {
    ActivateObjects("Concept")
  }

  def DeactivateObjects(objectType: String) {
    try {

      val keys = GetAllObjectKeys(objectType)
      if (keys.length == 0) {
        println("Sorry, No objects of type " + objectType + " available in the Metadata")
        return
      }

      println("\nPick the object of type " + objectType + " to be presented from the following list: ")
      var seq = 0
      keys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > keys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val key = keys(choice - 1)
      val apiArgJson = MakeJsonStrForArgList(key, objectType)
      val apiName = "DeactivateObjects"
      println("DE-Activating Objects ->" + objectType + "/deactivate " + apiArgJson)
      val apiResult = MakeHttpRequest("put", host_url, objectType + "/deactivate", "JSON", apiArgJson)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def DeactivateModel {
    DeactivateObjects("Model")
  }

  def DeactivateMessage {
    DeactivateObjects("Message")
  }

  def DeactivateContainer {
    DeactivateObjects("Container")
  }

  def DeactivateFunction {
    DeactivateObjects("Function")
  }
  def DeactivateType {
    DeactivateObjects("Type")
  }
  def DeactivateConcept {
    DeactivateObjects("Concept")
  }

  def RemoveObjects(objectType: String) {
    try {
      println("*&^*&^*&^*&^*&^*&^*")
      val keys = GetAllObjectKeys(objectType)
      if (keys.length == 0) {
        println("Sorry, No objects of type " + objectType + " available in the Metadata")
        return
      }

      println("\nPick the object of type " + objectType + " to be presented from the following list: ")
      var seq = 0
      keys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > keys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val key = keys(choice - 1)
      val apiArgJson = MakeJsonStrForArgList(key, objectType)
      val apiName = "RemoveObjects"
      println("---->" + objectType + "/" + key)
      val apiResult = MakeHttpRequest("delete", host_url, objectType + "/" + key, "JSON", null)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def RemoveModel {
    RemoveObjects("Model")
  }

  def RemoveMessage {
    RemoveObjects("Message")
  }

  def RemoveContainer {
    RemoveObjects("Container")
  }

  def RemoveFunction {
    RemoveObjects("Function")
  }
  def RemoveType {
    RemoveObjects("Type")
  }
  def RemoveConcept {
    RemoveObjects("Concept")
  }
  def RemoveOutputMsg {
    RemoveObjects("OutputMsg")
  }

  def AddModel {
    try {
      var dirName = metadataAPIConfig.getProperty("MODEL_FILES_DIR")
      if (!IsValidDir(dirName))
        return

      val pmmlFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".xml"))
      if (pmmlFiles.length == 0) {
        logger.error("No model files in the directory " + dirName)
        return
      }

      var pmmlFilePath = ""
      println("Pick a Model Definition file(pmml) from below choices")

      var seq = 0
      pmmlFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice == pmmlFiles.length + 1) {
        return
      }
      if (choice < 1 || choice > pmmlFiles.length + 1) {
        logger.error("Invalid Choice : " + choice)
        return
      }

      pmmlFilePath = pmmlFiles(choice - 1).toString
      val pmmlStr = Source.fromFile(pmmlFilePath).mkString
      // Save the model
      var res = MakeHttpRequest("post", host_url, "Model", "XML", pmmlStr)
      logger.debug("Results of AddModel Operation => " + res)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def AddContainer {
    try {
      var dirName = metadataAPIConfig.getProperty("CONTAINER_FILES_DIR")
      if (!IsValidDir(dirName)) {
        return
      }
      val contFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      if (contFiles.length == 0) {
        logger.error("No json container files in the directory " + dirName)
        return
      }

      println("\nPick a Container Definition file(s) from below choices\n")

      var seq = 0
      contFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choices (separate with commas if more than 1 choice given): ")
      //val choice:Int = readInt()
      val choicesStr: String = readLine()

      var valid: Boolean = true
      var choices: List[Int] = List[Int]()
      var results: ArrayBuffer[(String, String, String)] = ArrayBuffer[(String, String, String)]()
      try {
        choices = choicesStr.filter(_ != '\n').split(',').filter(ch => (ch != null && ch != "")).map(_.trim.toInt).toList
      } catch {
        case _: Throwable => valid = false
      }

      if (valid) {
        choices.foreach(choice => {
          if (choice == contFiles.length + 1) {
            return
          }
          if (choice < 1 || choice > contFiles.length + 1) {
            logger.error("Invalid Choice : " + choice)
            return
          }
          val contDefFile = contFiles(choice - 1).toString
          //  logger.setLevel(Level.TRACE);
          val contStr = Source.fromFile(contDefFile).mkString
          //val res : String =  MakeHttpRequest("put",host_url, "AddContainerDef","JSON",contStr)
          val res: String = MakeHttpRequest("post", host_url, "Container", "JSON", contStr)
          results += Tuple3(choice.toString, contDefFile, res)
        })
      } else {
        logger.error("Invalid Choices... choose 1 or more integers from list separating multiple entries with a comma")
        return
      }

      results.foreach(triple => {
        val (choice, filePath, result): (String, String, String) = triple
        println(s"Results for container [$choice] $filePath => \n$result")
      })
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Container Already in the metadata...." + e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def AddMessage {
    try {
      var dirName = metadataAPIConfig.getProperty("MESSAGE_FILES_DIR")
      if (!IsValidDir(dirName))
        return

      val msgFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      if (msgFiles.length == 0) {
        logger.error("No json message files in the directory " + dirName)
        return
      }
      println("\nPick a Message Definition file(s) from below choices\n")

      var seq = 0
      msgFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choices (separate with commas if more than 1 choice given): ")
      //val choice:Int = readInt()
      val choicesStr: String = readLine()

      var valid: Boolean = true
      var choices: List[Int] = List[Int]()
      var results: ArrayBuffer[(String, String, String)] = ArrayBuffer[(String, String, String)]()
      try {
        choices = choicesStr.filter(_ != '\n').split(',').filter(ch => (ch != null && ch != "")).map(_.trim.toInt).toList
      } catch {
        case _: Throwable => valid = false
      }

      if (valid) {
        choices.foreach(choice => {
          if (choice == msgFiles.length + 1) {
            return
          }
          if (choice < 1 || choice > msgFiles.length + 1) {
            logger.error("Invalid Choice : " + choice)
            return
          }
          val msgDefFile = msgFiles(choice - 1).toString
          val msgStr = Source.fromFile(msgDefFile).mkString
          val res: String = MakeHttpRequest("post", host_url, "Message", "JSON", msgStr)
          results += Tuple3(choice.toString, msgDefFile, res)
        })
      } else {
        logger.error("Invalid Choices... choose 1 or more integers from list separating multiple entries with a comma")
        return
      }

      results.foreach(triple => {
        val (choice, filePath, result): (String, String, String) = triple
        println(s"Results for message [$choice] $filePath => \n$result")
      })
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Message Already in the metadata....")
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def UploadEngineConfig {
    try {
      var dirName = metadataAPIConfig.getProperty("CONFIG_FILES_DIR")
      if (!IsValidDir(dirName))
        return

      val cfgFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      if (cfgFiles.length == 0) {
        logger.error("No config files in the directory " + dirName)
        return
      }

      var cfgFilePath = ""
      println("Pick a Config file(cfg) from below choices")

      var seq = 0
      cfgFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice == cfgFiles.length + 1) {
        return
      }
      if (choice < 1 || choice > cfgFiles.length + 1) {
        logger.error("Invalid Choice : " + choice)
        return
      }

      cfgFilePath = cfgFiles(choice - 1).toString
      val cfgStr = Source.fromFile(cfgFilePath).mkString
      val res: String = MakeHttpRequest("put", host_url, "UploadConfig", "JSON", cfgStr)
      println("Results as json string => \n" + res)
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Object Already in the metadata....")
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def RemoveEngineConfig {
    try {
      var dirName = metadataAPIConfig.getProperty("CONFIG_FILES_DIR")
      if (!IsValidDir(dirName))
        return

      val cfgFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      if (cfgFiles.length == 0) {
        logger.error("No config files in the directory " + dirName)
        return
      }

      var cfgFilePath = ""
      println("Pick a Config file(cfg) from below choices")

      var seq = 0
      cfgFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice == cfgFiles.length + 1) {
        return
      }
      if (choice < 1 || choice > cfgFiles.length + 1) {
        logger.error("Invalid Choice : " + choice)
        return
      }

      cfgFilePath = cfgFiles(choice - 1).toString
      val cfgStr = Source.fromFile(cfgFilePath).mkString
      val res: String = MakeHttpRequest("put", host_url, "RemoveConfig", "JSON", cfgStr)
      println("Results as json string => \n" + res)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def UploadJarFile {
    try {
      var dirName = metadataAPIConfig.getProperty("JAR_TARGET_DIR")
      if (!IsValidDir(dirName))
        return

      val jarFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".jar"))
      if (jarFiles.length == 0) {
        logger.error("No jar files in the directory " + dirName)
        return
      }

      var jarFilePath = ""
      println("Pick a Jar file(xxxx.jar) from below choices")

      var seq = 0
      jarFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice == jarFiles.length + 1) {
        return
      }
      if (choice < 1 || choice > jarFiles.length + 1) {
        logger.error("Invalid Choice : " + choice)
        return
      }

      jarFilePath = jarFiles(choice - 1).toString
      val jarUrl = "UploadJars?name=" + jarFilePath
      val res: String = MakeHttpRequest("put", host_url, jarUrl, "BINARY_FILE", jarFilePath)
      println("Results as json string => \n" + res)
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Model Already in the metadata....")
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def AddFunction {
    try {
      var dirName = metadataAPIConfig.getProperty("FUNCTION_FILES_DIR")
      if (!IsValidDir(dirName))
        return

      val functionFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      if (functionFiles.length == 0) {
        logger.error("No function files in the directory " + dirName)
        return
      }

      var functionFilePath = ""
      println("Pick a Function Definition file(function) from below choices")

      var seq = 0
      functionFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice == functionFiles.length + 1) {
        return
      }
      if (choice < 1 || choice > functionFiles.length + 1) {
        logger.error("Invalid Choice : " + choice)
        return
      }

      functionFilePath = functionFiles(choice - 1).toString

      val functionStr = Source.fromFile(functionFilePath).mkString
      val res = MakeHttpRequest("post", host_url, "Function", "JSON", functionStr)
      println("Results as json string => \n" + res)
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Function Already in the metadata....")
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def UpdateFunction {
    try {
      var dirName = metadataAPIConfig.getProperty("FUNCTION_FILES_DIR")
      if (!IsValidDir(dirName))
        return

      val functionFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      if (functionFiles.length == 0) {
        logger.error("No function files in the directory " + dirName)
        return
      }

      var functionFilePath = ""
      println("Pick a Function Definition file(function) from below choices")

      var seq = 0
      functionFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice == functionFiles.length + 1) {
        return
      }
      if (choice < 1 || choice > functionFiles.length + 1) {
        logger.error("Invalid Choice : " + choice)
        return
      }

      functionFilePath = functionFiles(choice - 1).toString

      val functionStr = Source.fromFile(functionFilePath).mkString
      val res = MakeHttpRequest("put", host_url, "Function", "JSON", functionStr)
      println("Results as json string => \n" + res)

    } catch {
      case e: AlreadyExistsException => {
        logger.error("Function Already in the metadata....")
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def AddConcept {
    try {
      var dirName = metadataAPIConfig.getProperty("CONCEPT_FILES_DIR")
      if (!IsValidDir(dirName))
        return

      val conceptFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      if (conceptFiles.length == 0) {
        logger.error("No concept files in the directory " + dirName)
        return
      }

      var conceptFilePath = ""
      println("Pick a Concept Definition file(concept) from below choices")

      var seq = 0
      conceptFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice == conceptFiles.length + 1) {
        return
      }
      if (choice < 1 || choice > conceptFiles.length + 1) {
        logger.error("Invalid Choice : " + choice)
        return
      }

      conceptFilePath = conceptFiles(choice - 1).toString

      val conceptStr = Source.fromFile(conceptFilePath).mkString
      val res = MakeHttpRequest("post", host_url, "Concept", "JSON", conceptStr)
      println("Results as json string => \n" + res)
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Concept Already in the metadata....")
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def UpdateConcept {
    try {

      // val res = MakeHttpRequest("put",host_url, "Concept","JSON","conceptStr")

      // println("Results as json string => \n" + res)

      var dirName = metadataAPIConfig.getProperty("CONCEPT_FILES_DIR")
      if (!IsValidDir(dirName))
        return

      val conceptFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      if (conceptFiles.length == 0) {
        logger.error("No concept files in the directory " + dirName)
        return
      }

      var conceptFilePath = ""
      println("Pick a Concept Definition file(concept) from below choices")

      var seq = 0
      conceptFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice == conceptFiles.length + 1) {
        return
      }
      if (choice < 1 || choice > conceptFiles.length + 1) {
        logger.error("Invalid Choice : " + choice)
        return
      }

      conceptFilePath = conceptFiles(choice - 1).toString

      val conceptStr = Source.fromFile(conceptFilePath).mkString
      val res = MakeHttpRequest("put", host_url, "Concept", "JSON", conceptStr)
      println("Results as json string => \n" + res)
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Concept Already in the metadata....")
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def AddType {
    try {
      var dirName = metadataAPIConfig.getProperty("TYPE_FILES_DIR")

      if (!IsValidDir(dirName))
        return

      val typeFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      if (typeFiles.length == 0) {
        logger.error("No type files in the directory " + dirName)
        return
      }

      var typeFilePath = ""
      println("Pick a Type Definition file(type) from below choices")

      var seq = 0
      typeFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice == typeFiles.length + 1) {
        return
      }
      if (choice < 1 || choice > typeFiles.length + 1) {
        logger.error("Invalid Choice : " + choice)
        return
      }

      typeFilePath = typeFiles(choice - 1).toString

      val typeStr = Source.fromFile(typeFilePath).mkString
      val res = MakeHttpRequest("post", host_url, "Type", "JSON", typeStr)
      println("Results as json string => \n" + res)
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Type Already in the metadata....")
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def GetAllTypesByObjType {
    try {
      val typeMenu = Map(1 -> "ScalarTypeDef",
        2 -> "ArrayTypeDef",
        3 -> "ArrayBufTypeDef",
        4 -> "SetTypeDef",
        5 -> "TreeSetTypeDef",
        6 -> "AnyTypeDef",
        7 -> "SortedSetTypeDef",
        8 -> "MapTypeDef",
        9 -> "HashMapTypeDef",
        10 -> "ImmutableMapTypeDef",
        11 -> "ListTypeDef",
        12 -> "QueueTypeDef",
        13 -> "TupleTypeDef")
      var selectedType = "com.ligadata.kamanja.metadata.ScalarTypeDef"
      var done = false
      while (done == false) {
        println("\n\nPick a Type ")
        var seq = 0
        typeMenu.foreach(key => { seq += 1; println("[" + seq + "] " + typeMenu(seq)) })
        seq += 1
        println("[" + seq + "] Main Menu")
        print("\nEnter your choice: ")
        val choice: Int = readInt()
        if (choice <= typeMenu.size) {
          selectedType = "com.ligadata.kamanja.metadata." + typeMenu(choice)
          done = true
        } else if (choice == typeMenu.size + 1) {
          done = true
        } else {
          logger.error("Invalid Choice : " + choice)
        }
      }

      val res = MakeHttpRequest("get", host_url, "GetAllTypesByObjType", "JSON", selectedType)
      println("Results as json string => \n" + res)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def AddOutputMessage {
    try {
      var dirName = metadataAPIConfig.getProperty("OUTPUTMESSAGE_FILES_DIR")
      if (!IsValidDir(dirName))
        return

      val outputmsgFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      if (outputmsgFiles.length == 0) {
        logger.fatal("No json output message files in the directory " + dirName)
        return
      }
      println("\nPick a Output Message Definition file(s) from below choices\n")

      var seq = 0
      outputmsgFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choices (separate with commas if more than 1 choice given): ")
      //val choice:Int = readInt()
      val choicesStr: String = readLine()

      var valid: Boolean = true
      var choices: List[Int] = List[Int]()
      var results: ArrayBuffer[(String, String, String)] = ArrayBuffer[(String, String, String)]()
      try {
        choices = choicesStr.filter(_ != '\n').split(',').filter(ch => (ch != null && ch != "")).map(_.trim.toInt).toList
      } catch {
        case _: Throwable => valid = false
      }

      if (valid) {

        choices.foreach(choice => {
          if (choice == outputmsgFiles.length + 1) {
            return
          }
          if (choice < 1 || choice > outputmsgFiles.length + 1) {
            logger.fatal("Invalid Choice : " + choice)
            return
          }

          val outputmsgDefFile = outputmsgFiles(choice - 1).toString
          val outputmsgStr = Source.fromFile(outputmsgDefFile).mkString
          val res: String = MakeHttpRequest("post", host_url, "OutputMsg", "JSON", outputmsgStr)
          results += Tuple3(choice.toString, outputmsgDefFile, res)
        })
      } else {
        logger.fatal("Invalid Choices... choose 1 or more integers from list separating multiple entries with a comma")
        return
      }

      results.foreach(triple => {
        val (choice, filePath, result): (String, String, String) = triple
        println(s"Results for output message [$choice] $filePath => \n$result")
      })

    } catch {
      case e: AlreadyExistsException => {
        logger.error("Object Already in the metadata....")
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def UpdateOutputMsg: Unit = {
    try {
      var dirName = metadataAPIConfig.getProperty("OUTPUTMESSAGE_FILES_DIR")
      if (!IsValidDir(dirName))
        return

      val outputmsgFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      if (outputmsgFiles.length == 0) {
        logger.error("No output message files in the directory " + dirName)
        return
      }

      var outputmsgFilePath = ""
      println("Pick a Output Message Definition file from the below choice")

      var seq = 0
      outputmsgFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice == outputmsgFiles.length + 1)
        return

      if (choice < 1 || choice > outputmsgFiles.length + 1) {
        logger.error("Invalid Choice: " + choice)
        return
      }

      outputmsgFilePath = outputmsgFiles(choice - 1).toString
      val outputmsgStr = Source.fromFile(outputmsgFilePath).mkString
      val res: String = MakeHttpRequest("put", host_url, "OutputMsg", "JSON", outputmsgStr)
      println("Results as json string => \n" + res)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def StartTest {
    try {
      val dumpMetadata = () => { GetAllMetadataObjects }
      val addModel = () => { AddModel }
      val getModel = () => { GetModel }
      val getAllModels = () => { GetAllModels }
      val removeModel = () => { RemoveModel }
      val deactivateModel = () => { DeactivateModel }
      val activateModel = () => { ActivateModel }
      val addMessage = () => { AddMessage }
      val getMessage = () => { GetMessage }
      val getAllMessages = () => { GetAllMessages }
      val removeMessage = () => { RemoveMessage }
      val activateMessage = () => { ActivateMessage }
      val deactivateMessage = () => { DeactivateMessage }
      val addContainer = () => { AddContainer }
      val getContainer = () => { GetContainer }
      val getAllContainers = () => { GetAllContainers }
      val removeContainer = () => { RemoveContainer }
      val addType = () => { AddType }
      val getType = () => { GetType }
      val getAllTypes = () => { GetAllTypes }
      val removeType = () => { RemoveType }
      val addFunction = () => { AddFunction }
      val removeFunction = () => { RemoveFunction }
      val updateFunction = () => { UpdateFunction }
      val addConcept = () => { AddConcept }
      val removeConcept = () => { RemoveConcept }
      val updateConcept = () => { UpdateConcept }
      val dumpAllFunctionsAsJson = () => { GetAllFunctions }
      val loadFunctionsFromAFile = () => { AddFunction }
      val dumpAllConceptsAsJson = () => { GetAllConcepts }
      val loadConceptsFromAFile = () => { AddConcept }
      val dumpAllTypesByObjTypeAsJson = () => { GetAllTypesByObjType }
      val loadTypesFromAFile = () => { AddType }
      val uploadJarFile = () => { UploadJarFile }
      val uploadEngineConfig = () => { UploadEngineConfig }
      val dumpAllNodes = () => { GetAllNodes }
      val dumpAllClusters = () => { GetAllClusters }
      val dumpAllClusterCfgs = () => { GetAllClusterCfgs }
      val dumpAllAdapters = () => { GetAllAdapters }
      val dumpAllCfgObjects = () => { GetAllConfigObjects }
      val removeEngineConfig = () => { RemoveEngineConfig }
      val addOutputMessage = () => { AddOutputMessage }
      val getAllOutputMsgs = () => { GetAllOutputMsgs }
      val removeOutputMsg = () 	=> { RemoveOutputMsg }
      val updateOutputMsg = () 	=> { UpdateOutputMsg }

      val topLevelMenu = List(("Add Model", addModel),
        ("Get Model", getModel),
        ("Get All Models", getAllModels),
        ("Remove Model", removeModel),
        ("Deactivate Model", deactivateModel),
        ("Activate Model", activateModel),
        ("Add Message", addMessage),
        ("Get Message", getMessage),
        ("Get All Messages", getAllMessages),
        ("Remove Message", removeMessage),
        ("Activate Message", activateMessage),
        ("Deactivate Message", deactivateMessage),
        ("Add Container", addContainer),
        ("Get Container", getContainer),
        ("Get All Containers", getAllContainers),
        ("Remove Container", removeContainer),
        ("Add Type", addType),
        ("Get Type", getType),
        ("Get All Types", getAllTypes),
        ("Remove Type", removeType),
        ("Add Function", addFunction),
        ("Remove Function", removeFunction),
        ("Update Function", updateFunction),
        ("Add Concept", addConcept),
        ("Remove Concept", removeConcept),
        ("Update Concept", updateConcept),
        ("Load Concepts from a file", loadConceptsFromAFile),
        ("Load Functions from a file", loadFunctionsFromAFile),
        ("Load Types from a file", loadTypesFromAFile),
        ("Dump All Metadata Keys", dumpMetadata),
        ("Dump All Functions", dumpAllFunctionsAsJson),
        ("Dump All Concepts", dumpAllConceptsAsJson),
        ("Dump All Types By Object Type", dumpAllTypesByObjTypeAsJson),
        ("Upload Any Jar", uploadJarFile),
        ("Upload Engine Config", uploadEngineConfig),
        ("Dump Node Objects", dumpAllNodes),
        ("Dump Cluster Objects", dumpAllClusters),
        ("Dump ClusterCfg Node Objects", dumpAllClusterCfgs),
        ("Dump Adapter Node Objects", dumpAllAdapters),
        ("Dump All Config Objects", dumpAllCfgObjects),
        ("Remove Engine Config", removeEngineConfig),
        ("Add Output Message", addOutputMessage),
		("Get All Output Messages", getAllOutputMsgs),
		("Remove Output Message", removeOutputMsg),
		("Update Output Message", updateOutputMsg))

      var done = false
      while (done == false) {
        println("\n\nPick an API ")
        for ((key, idx) <- topLevelMenu.zipWithIndex) { println("[" + (idx + 1) + "] " + key._1) }
        println("[" + (topLevelMenu.size + 1) + "] Exit")
        print("\nEnter your choice: ")
        val choice: Int = readInt()
        if (choice <= topLevelMenu.size) {
          topLevelMenu(choice - 1)._2.apply
        } else if (choice == topLevelMenu.size + 1) {
          done = true
        } else {
          logger.error("Invalid Choice : " + choice)
        }
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def main(args: Array[String]) {
    // logger.setLevel(Level.TRACE);
    val httpClient = new HttpClient

    try {
      var myConfigFile = System.getenv("HOME") + "/MetadataAPIConfig.properties"
      if (args.length == 0) {
        logger.warn("Config File defaults to " + myConfigFile)
        logger.warn("One Could optionally pass a config file as a command line argument:  --config myConfig.properties")
        logger.warn("The config file supplied is a complete path name of a config file similar to one in github/Kamanja/trunk/MetadataAPI/src/main/resources/MetadataAPIConfig.properties")
      } else {
        val cfgfile = args(0)
        if (cfgfile == null) {
          logger.error("Need configuration file as parameter")
          throw new MissingArgumentException("Usage: configFile  supplied as --config myConfig.json")
        }
        myConfigFile = cfgfile
      }
      // read properties file
      readMetadataAPIConfigFromPropertiesFile(myConfigFile)
      host_url = "http://" + metadataAPIConfig.getProperty("SERVICE_HOST") + ":" + metadataAPIConfig.getProperty("SERVICE_PORT")
      //AddModel
      //GetModel
      StartTest
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    } finally {
      // Cleanup and exit
      logger.debug("Done");
    }
  }
}
