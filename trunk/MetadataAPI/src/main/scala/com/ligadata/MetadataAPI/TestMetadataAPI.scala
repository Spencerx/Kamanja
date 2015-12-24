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

package com.ligadata.MetadataAPI

import java.io.{ ByteArrayOutputStream, _ }

import com.esotericsoftware.kryo.io.{ Input, Output }
import com.ligadata.Serialize._
import com.ligadata.ZooKeeper._
import com.ligadata.KvBase.{ Key, Value, TimeRange }
import com.ligadata.StorageBase.{ DataStore, Transaction }
import com.ligadata.kamanja.metadata._
import com.ligadata.kamanja.metadataload.MetadataLoad
import com.twitter.chill.ScalaKryoInstantiator
import org.apache.logging.log4j._
import org.apache.zookeeper.CreateMode
import org.scalatest.Assertions._

import scala.collection.mutable.ArrayBuffer
import scala.io._
import java.util.Date
import java.util.Properties
import com.ligadata.Exceptions._
import com.ligadata.kamanja.metadata.Utils
import com.ligadata.Exceptions.StackTrace

object TestMetadataAPI {

  private type OptionMap = Map[Symbol, Any]
  private val userid: Option[String] = Some("someUser")

  val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)

  var serializer = SerializerManager.GetSerializer("kryo")

  def testDbConn {
  /*
    var hostnames = "localhost"
    var keyspace = "default"
    var table = "default"

    var clusterBuilder = Cluster.builder()

    clusterBuilder.addContactPoints(hostnames)

    val cluster = clusterBuilder.build()
    val session = cluster.connect(keyspace);
*/
  }

  // Type defs

  def AddType {
    try {
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("TYPE_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Types"
        logger.debug("The environment variable TYPE_FILES_DIR is undefined. Setting to default " + dirName)
      }

      if (!IsValidDir(dirName)) {
        logger.error("Invalid Directory " + dirName)
        return
      }
      val typFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      if (typFiles.length == 0) {
        logger.error("No json type files exist in the directory " + dirName)
        return
      }

      println("\nSelect a Type Definition file:\n")

      var seq = 0
      typFiles.foreach(key => { seq += 1; println("[" + seq + "]" + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice == typFiles.length + 1) {
        return
      }

      if (choice < 1 || choice > typFiles.length + 1) {
        logger.error("Invalid Choice: " + choice)
        return
      }

      val typDefFile = typFiles(choice - 1).toString
      //logger.setLevel(Level.DEBUG); //check again
      val typStr = Source.fromFile(typDefFile).mkString
      //  MetadataAPIImpl.SetLoggerLevel(Level.TRACE) //check again
      println("Results as json string => \n" + MetadataAPIImpl.AddTypes(typStr, "JSON", userid))
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Type already exists in metadata...")
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def GetType {
    try {
      //logger.setLevel(Level.TRACE);  //check again

      val typKeys = MetadataAPIImpl.GetAllKeys("TypeDef", None)
      if (typKeys.length == 0) {
        println("Sorry, No types available in the Metadata")
        return
      }

      println("\nPick the type to be presented from the following list: ")
      var seq = 0
      typKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > typKeys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val typKey = typKeys(choice - 1)
      val(typNameSpace, typName, typVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(typKey)
      val typOpt = MetadataAPIImpl.GetType(typNameSpace, typName, typVersion, "JSON", userid)

      typOpt match {
        case None => None
        case Some(ts) =>
          val apiResult = new ApiResult(ErrorCodeConstants.Success, "GetType", JsonSerializer.SerializeObjectToJson(ts), ErrorCodeConstants.Get_Type_Successful).toString()
          // val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
          println("Result as Json String => \n" + apiResult)
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        
      }
    }
  }

  def GetAllTypes {
    val apiResult = MetadataAPIImpl.GetAllTypes("JSON", userid)
    //val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
    println("Result as Json String => \n" + apiResult)
  }

  def RemoveType {
    val loggerName = this.getClass.getName
    lazy val logger = LogManager.getLogger(loggerName)

    try {
      // logger.setLevel(Level.TRACE); //check again

      val typKeys = MetadataAPIImpl.GetAllKeys("TypeDef", None)
      if (typKeys.length == 0) {
        println("Sorry, there are no types available in the Metadata")
        return
      }

      println("\nPick the Type to be deleted from the following list: ")
      var seq = 0
      typKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > typKeys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val typKey = typKeys(choice - 1)
      val(typNameSpace, typName, typVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(typKey)
      val apiResult = MetadataAPIImpl.RemoveType(typNameSpace, typName, typVersion.toLong, userid)

      // val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        
      }
    }
  }

  // End Type defs

  // TODO: Rewrite Update Type to allow a user to pick the file they wish to update a type from.
  def UpdateType = {
    val apiResult = MetadataAPIImpl.UpdateType(SampleData.sampleNewScalarTypeStr, "JSON", userid)
    //val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
    println("Result as Json String => \n" + apiResult)
  }

  def AddFunction {
    try {
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("FUNCTION_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Types"
        logger.debug("The environment variable FUNCTION_FILES_DIR is undefined. Setting to default " + dirName)
      }

      if (!IsValidDir(dirName)) {
        logger.error("Invalid Directory " + dirName)
        return
      }
      val fcnFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      if (fcnFiles.length == 0) {
        logger.error("No json type files exist in the directory " + dirName)
        return
      }

      println("\nSelect a Function Definition file:\n")

      var seq = 0
      fcnFiles.foreach(key => { seq += 1; println("[" + seq + "]" + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice == fcnFiles.length + 1) {
        return
      }

      if (choice < 1 || choice > fcnFiles.length + 1) {
        logger.error("Invalid Choice: " + choice)
        return
      }

      val fcnDefFile = fcnFiles(choice - 1).toString
      //logger.setLevel(Level.DEBUG); //check again
      val fcnStr = Source.fromFile(fcnDefFile).mkString
      //  MetadataAPIImpl.SetLoggerLevel(Level.TRACE) //check again
      println("Results as json string => \n" + MetadataAPIImpl.AddFunctions(fcnStr, "JSON", userid))
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Function already exists in metadata...")
      }
      case e: Exception => {
      val stackTrace = StackTrace.ThrowableTraceString(e)
      logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def RemoveFunction: Unit = {
    val loggerName = this.getClass.getName
    lazy val logger = LogManager.getLogger(loggerName)

    try {
      //logger.setLevel(Level.TRACE);

      val fcnKeys = MetadataAPIImpl.GetAllFunctionsFromCache(true, None)
      if (fcnKeys.length == 0) {
        println("Sorry, there are no functions available in the Metadata")
        return
      }

      println("\nPick the Function to be deleted from the following list: ")
      var seq = 0
      fcnKeys.foreach(key => {
        seq += 1;
        println("[" + seq + "] " + key)
      })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > fcnKeys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val fcnKey = fcnKeys(choice - 1)
      val(fcnNameSpace, fcnName, fcnVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(fcnKey)
      val apiResult = MetadataAPIImpl.RemoveFunction(fcnNameSpace, fcnName, fcnVersion.toLong, userid)

      //val resultData = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def GetFunction: Unit = {
    val loggerName = this.getClass.getName
    lazy val logger = LogManager.getLogger(loggerName)

    try {
      //logger.setLevel(Level.TRACE)

      val fcnKeys = MetadataAPIImpl.GetAllFunctionsFromCache(true, None)
      if (fcnKeys.length == 0) {
        println("Sorry, No functions available in the Metadata")
        return
      }

      println("\nPick the Function to be presented from the following list: ")
      var seq = 0
      fcnKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > fcnKeys.length) {
        println("Invalid choice " + choice + ", start with main menu...")
        return
      }

      val fcnKey = fcnKeys(choice - 1)
      val(fcnNameSpace, fcnName, fcnVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(fcnKey)
      val apiResult = MetadataAPIImpl.GetFunctionDef(fcnNameSpace, fcnName, "JSON", userid)

      //   val (statusCode,resultData) = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def UpdateFunction = {
    val apiResult = MetadataAPIImpl.UpdateFunctions(SampleData.sampleFunctionStr, "JSON", userid)
    //  val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
    println("Result as Json String => \n" + apiResult)
  }

  def AddConcept = {
    val apiResult = MetadataAPIImpl.AddConcepts(SampleData.sampleConceptStr, "JSON", userid)
    //   val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
    println("Result as Json String => \n" + apiResult)
  }

  def RemoveConcept = {
    val apiResult = MetadataAPIImpl.RemoveConcept("Ligadata.ProviderId.100", userid)
    //   val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
    println("Result as Json String => \n" + apiResult)
  }

  def RemoveConcepts = {
    val apiResult = MetadataAPIImpl.RemoveConcepts(Array("Ligadata.ProviderId.100"), userid)
    //val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
    println("Result as Json String => \n" + apiResult)
  }

  def UpdateConcept = {
    val apiResult = MetadataAPIImpl.UpdateConcepts(SampleData.sampleConceptStr, "JSON", userid)
    //  val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
    println("Result as Json String => \n" + apiResult)
  }

  def AddDerivedConcept = {
    val apiResult = MetadataAPIImpl.AddDerivedConcept(SampleData.sampleDerivedConceptStr, "JSON")
    //  val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
    println("Result as Json String => \n" + apiResult)
  }

  def fileToString(filePath: String): String = {
    val file = new java.io.File(filePath)
    val inStream = new FileInputStream(file)
    val outStream = new ByteArrayOutputStream
    try {
      var reading = true
      while (reading) {
        inStream.read() match {
          case -1 => reading = false
          case c => outStream.write(c)
        }
      }
      outStream.flush()
    } finally {
      inStream.close()
    }
    val pmmlStr = new String(outStream.toByteArray())
    logger.debug(pmmlStr)
    pmmlStr
  }

  def GetMessage {
    try {
      //    logger.setLevel(Level.TRACE); //check again

      val msgKeys = MetadataAPIImpl.GetAllKeys("MessageDef", None)

      if (msgKeys.length == 0) {
        println("Sorry, No messages available in the Metadata")
        return
      }

      println("\nPick the message to be presented from the following list: ")

      var seq = 0
      msgKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > msgKeys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }
      val msgKey = msgKeys(choice - 1)
      val(msgNameSpace, msgName, msgVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(msgKey)
      val depModels = MetadataAPIImpl.GetDependentModels(msgNameSpace, msgName, msgVersion.toLong)
      logger.debug("DependentModels => " + depModels)

      logger.debug("DependentModels => " + depModels)

      val apiResult = MetadataAPIImpl.GetMessageDef(msgNameSpace, msgName, "JSON", msgVersion, userid)

      //     val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def GetMessageFromCache {
    try {
      //  logger.setLevel(Level.TRACE); //check again

      val msgKeys = MetadataAPIImpl.GetAllMessagesFromCache(true, None)

      if (msgKeys.length == 0) {
        println("Sorry, No messages available in the Metadata")
        return
      }

      println("\nPick the message to be presented from the following list: ")

      var seq = 0
      msgKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > msgKeys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }
      val msgKey = msgKeys(choice - 1)
      val(msgNameSpace, msgName, msgVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(msgKey)
      val depModels = MetadataAPIImpl.GetDependentModels(msgNameSpace, msgName, msgVersion.toLong)
      if (depModels.length > 0) {
        depModels.foreach(mod => {
          logger.debug("DependentModel => " + mod.FullNameWithVer)
        })
      }

      val apiResult = MetadataAPIImpl.GetMessageDefFromCache(msgNameSpace, msgName, "JSON", msgVersion, userid)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        
      }
    }
  }

  def GetContainerFromCache {
    try {
      // logger.setLevel(Level.TRACE); //check again

      val contKeys = MetadataAPIImpl.GetAllContainersFromCache(true, None)

      if (contKeys.length == 0) {
        println("Sorry, No containers available in the Metadata")
        return
      }

      println("\nPick the container to be presented from the following list: ")

      var seq = 0
      contKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > contKeys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }
      val contKey = contKeys(choice - 1)
      val(contNameSpace, contName, contVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(contKey)
      val apiResult = MetadataAPIImpl.GetContainerDefFromCache(contNameSpace, contName, "JSON", contVersion, userid)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def GetModel {
    val loggerName = this.getClass.getName
    lazy val logger = LogManager.getLogger(loggerName)

    try {
      //  logger.setLevel(Level.TRACE); //check again

      val modKeys = MetadataAPIImpl.GetAllKeys("ModelDef", None)
      if (modKeys.length == 0) {
        println("Sorry, No models available in the Metadata")
        return
      }

      println("\nPick the model to be presented from the following list: ")
      var seq = 0
      modKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > modKeys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val modKey = modKeys(choice - 1)
      val(modNameSpace, modName, modVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(modKey)
      val apiResult = MetadataAPIImpl.GetModelDefFromDB(modNameSpace, modName, "JSON", modVersion, userid)

      //     val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def GetModelFromCache {
    val loggerName = this.getClass.getName
    lazy val logger = LogManager.getLogger(loggerName)

    try {
      //    logger.setLevel(Level.TRACE); //check again

      val modKeys = MetadataAPIImpl.GetAllModelsFromCache(true, None)
      if (modKeys.length == 0) {
        println("Sorry, No models available in the Metadata")
        return
      }

      println("\nPick the model to be presented from the following list: ")
      var seq = 0
      modKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > modKeys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val modKey = modKeys(choice - 1)
      val(modNameSpace, modName, modVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(modKey)
      val apiResult = MetadataAPIImpl.GetModelDefFromCache(modNameSpace, modName, "JSON", modVersion, userid)

      //     val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def RemoveMessageFromStore {
    try {
      // logger.setLevel(Level.TRACE); //check agai
      val msgKeys = MetadataAPIImpl.GetAllKeys("MessageDef", None)

      if (msgKeys.length == 0) {
        println("Sorry, No messages available in the Metadata")
        return
      }

      println("\nPick the message to be deleted from the following list: ")
      var seq = 0
      msgKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > msgKeys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val msgKey = msgKeys(choice - 1)
      val(msgNameSpace, msgName, msgVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(msgKey)
      val apiResult = MetadataAPIImpl.RemoveMessage(msgNameSpace, msgName, msgVersion.toLong, userid)

      //val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def RemoveContainer {
    try {
      //  logger.setLevel(Level.TRACE);  //check again

      val contKeys = MetadataAPIImpl.GetAllContainersFromCache(true, None)

      if (contKeys.length == 0) {
        println("Sorry, No containers available in the Metadata")
        return
      }

      println("\nPick the container to be deleted from the following list: ")
      var seq = 0
      contKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > contKeys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val contKey = contKeys(choice - 1)
      val(contNameSpace, contName, contVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(contKey)
      val apiResult = MetadataAPIImpl.RemoveContainer(contNameSpace, contName, contVersion.toLong, userid)

      // val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: NumberFormatException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        print("\n Entry not in desired format. Please enter only one choice correctly")
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error(e.toString)
        logger.debug("\nStackTrace:"+stackTrace)
      }
    }
  }

  def RemoveMessage {
    try {
      //  logger.setLevel(Level.TRACE); //check again
println("Getting Messages")
      val msgKeys = MetadataAPIImpl.GetAllMessagesFromCache(true, None)

      if (msgKeys.length == 0) {
        println("Sorry, No messages available in the Metadata")
        return
      }

      println("\nPick the message to be deleted from the following list: ")
      var seq = 0
      msgKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > msgKeys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val msgKey = msgKeys(choice - 1)
      val(msgNameSpace, msgName, msgVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(msgKey)
      val apiResult = MetadataAPIImpl.RemoveMessage(msgNameSpace, msgName, msgVersion.toLong, userid)

      //  val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def RemoveModel {
    try {
      //logger.setLevel(Level.TRACE);  //check again

      val modKeys = MetadataAPIImpl.GetAllModelsFromCache(true, None)

      if (modKeys.length == 0) {
        println("Sorry, No models available in the Metadata")
        return
      }

      println("\nPick the model to be deleted from the following list: ")
      var seq = 0
      modKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > modKeys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val modKey = modKeys(choice - 1)
      val(modNameSpace, modName, modVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(modKey)
      val apiResult = MetadataAPIImpl.RemoveModel(modNameSpace, modName, modVersion.toLong, userid)

      //   val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

 
  def DeactivateModel {
    try {
      //logger.setLevel(Level.TRACE);  //check again

      val modKeys = MetadataAPIImpl.GetAllModelsFromCache(true, None)

      if (modKeys.length == 0) {
        println("Sorry, No models available in the Metadata")
        return
      }

      println("\nPick the model to be deleted from the following list: ")
      var seq = 0
      modKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > modKeys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val modKey = modKeys(choice - 1)
      val(modNameSpace, modName, modVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(modKey)
      val apiResult = MetadataAPIImpl.DeactivateModel(modNameSpace, modName, modVersion.toLong, userid)

      //   val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def ActivateModel {
    try {
      //logger.setLevel(Level.TRACE);  //check again

      val modKeys = MetadataAPIImpl.GetAllModelsFromCache(false, None)

      if (modKeys.length == 0) {
        println("Sorry, No models available in the Metadata")
        return
      }

      println("\nPick the model to be deleted from the following list: ")
      var seq = 0
      modKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > modKeys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val modKey = modKeys(choice - 1)
      val(modNameSpace, modName, modVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(modKey)
      val apiResult = MetadataAPIImpl.ActivateModel(modNameSpace, modName, modVersion.toLong, userid)

      //   val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def RemoveModelFromCache {
    try {
      //logger.setLevel(Level.TRACE);  //check again

      val modKeys = MetadataAPIImpl.GetAllModelsFromCache(true, None)

      if (modKeys.length == 0) {
        println("Sorry, No models available in the Metadata")
        return
      }

      println("\nPick the model to be deleted from the following list: ")
      var seq = 0
      modKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice < 1 || choice > modKeys.length) {
        println("Invalid choice " + choice + ",start with main menu...")
        return
      }

      val modKey = modKeys(choice - 1)
      val(modNameSpace, modName, modVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(modKey)
      val apiResult = MetadataAPIImpl.RemoveModel(modNameSpace, modName, modVersion.toLong, userid)

      //    val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

 
  def GetAllMessagesFromStore {
    try {
      //logger.setLevel(Level.TRACE);  //check again
      val msgKeys = MetadataAPIImpl.GetAllKeys("MessageDef", userid)
      if (msgKeys.length == 0) {
        println("Sorry, No messages available in the Metadata")
        return
      }
      var seq = 0
      msgKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def GetAllModelsFromCache {
    try {
      //logger.setLevel(Level.TRACE);  //check again
      val modKeys = MetadataAPIImpl.GetAllModelsFromCache(true, userid)
      if (modKeys.length == 0) {
        println("Sorry, No models available in the Metadata")
        return
      }

      var seq = 0
      modKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def GetAllMessagesFromCache {
    try {
      //logger.setLevel(Level.TRACE);  //check again
      val msgKeys = MetadataAPIImpl.GetAllMessagesFromCache(true, userid)
      if (msgKeys.length == 0) {
        println("Sorry, No messages are available in the Metadata")
        return
      }

      var seq = 0
      msgKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def GetAllContainersFromCache {
    try {
      //logger.setLevel(Level.TRACE);  //check again
      val msgKeys = MetadataAPIImpl.GetAllContainersFromCache(true, userid)
      if (msgKeys.length == 0) {
        println("Sorry, No containers are available in the Metadata")
        return
      }

      var seq = 0
      msgKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def GetAllModelsFromStore {
    try {
      //logger.setLevel(Level.TRACE);  //check again
      val modKeys = MetadataAPIImpl.GetAllKeys("ModelDef", userid)
      if (modKeys.length == 0) {
        println("Sorry, No models are available in the Metadata")
        return
      }

      var seq = 0
      modKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def UpdateContainer {
    try {
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CONTAINER_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Containers"
        logger.debug("The environment variable CONTAINER_FILES_DIR is undefined, The directory defaults to " + dirName)
      }

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
          //logger.setLevel(Level.TRACE);  //check again
          val contStr = Source.fromFile(contDefFile).mkString
          //  MetadataAPIImpl.SetLoggerLevel(Level.TRACE)
          val res: String = MetadataAPIImpl.UpdateContainer(contStr, "JSON", userid)
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

  def AddContainer {
    try {
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CONTAINER_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Containers"
        logger.debug("The environment variable CONTAINER_FILES_DIR is undefined, The directory defaults to " + dirName)
      }

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
          //logger.setLevel(Level.TRACE);  //check again
          val contStr = Source.fromFile(contDefFile).mkString
          // MetadataAPIImpl.SetLoggerLevel(Level.TRACE)
          val res: String = MetadataAPIImpl.AddContainer(contStr, "JSON", userid)
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
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Container Already in the metadata...." + e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
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

  def UpdateMessage: Unit = {
    try {
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MESSAGE_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Messages"
        logger.debug("The environment variable MESSAGE_FILES_DIR is undefined, The directory defaults to " + dirName)
      }

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
          //logger.setLevel(Level.TRACE);  //check again
          val msgStr = Source.fromFile(msgDefFile).mkString
          //   MetadataAPIImpl.SetLoggerLevel(Level.TRACE)
          val res: String = MetadataAPIImpl.UpdateMessage(msgStr, "JSON", userid)
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
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def AddMessage {
    try {
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MESSAGE_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Messages"
        logger.debug("The environment variable MESSAGE_FILES_DIR is undefined, The directory defaults to " + dirName)
      }

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
          //logger.setLevel(Level.TRACE);  //check again
          val msgStr = Source.fromFile(msgDefFile).mkString
          //   MetadataAPIImpl.SetLoggerLevel(Level.TRACE)
          val res: String = MetadataAPIImpl.AddContainer(msgStr, "JSON", userid)
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
        e.printStackTrace()
        logger.error("Message Already in the metadata....")
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def UpdateModel: Unit = {
    try {
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MODEL_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Models"
        logger.debug("The environment variable MODEL_FILES_DIR is undefined, the directory defaults to " + dirName)
      }

      if (!IsValidDir(dirName))
        return

      val pmmlFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".xml"))
      if (pmmlFiles.length == 0) {
        logger.error("No model files in the directory " + dirName)
        return
      }

      var pmmlFilePath = ""
      println("Pick a Model Definition file(pmml) from the below choice")

      var seq = 0
      pmmlFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice == pmmlFiles.length + 1)
        return

      if (choice < 1 || choice > pmmlFiles.length + 1) {
        logger.error("Invalid Choice: " + choice)
        return
      }

      pmmlFilePath = pmmlFiles(choice - 1).toString
      val pmmlStr = Source.fromFile(pmmlFilePath).mkString
      println(pmmlStr)
      // Save the model
      //    MetadataAPIImpl.SetLoggerLevel(Level.TRACE)
      println("Results as json string => \n" + MetadataAPIImpl.UpdateModel(pmmlStr, userid))
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  private def loadConfFile(configFile: String): Properties = {
    var configs: Properties = null
    try {
      val file: File = new File(configFile);
      if (file.exists()) {
        val input: InputStream = new FileInputStream(file)
        try {
          // Load configuration
          configs = new Properties()
          configs.load(input);
        } catch {
          case e: Exception =>{
            val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
            throw new Exception("Failed to load configuration. Message:" + e.getMessage)
          }
        } finally {
          input.close();
        }
      } else {
        throw new Exception("Configuration file not found : " + configFile)
      }
    } catch {
      case e: Exception =>{
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw new Exception("Invalid Configuration. Message: " + e.getMessage())
      }
    }
    return configs
  }

  private def loadCfgsAndPrepareDeps(configFile: String): Properties = {
    val metaProps = loadConfFile(configFile)

    metaProps
  }

  def AddModelSourceJava(op: String):Unit = {
    try {
      
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MODEL_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Models"
        logger.debug("The environment variable MODEL_FILES_DIR is undefined, The directory defaults to " + dirName)
      }

      if (!IsValidDir(dirName))
        return

      val sourceFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".java"))
      if (sourceFiles.length == 0) {
        logger.error("No model files in the directory " + dirName)
        return
      }

      var sourceFilePath = ""
      println("Pick a Model Definition file(source) from below choices")

      var seq = 0
      sourceFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice == sourceFiles.length + 1) {
        return
      }
      if (choice < 1 || choice > sourceFiles.length + 1) {
        logger.error("Invalid Choice : " + choice)
        return
      }

      sourceFilePath = sourceFiles(choice - 1).toString
      val sourceStr = Source.fromFile(sourceFilePath).mkString
      // Save the model
      // MetadataAPIImpl.SetLoggerLevel(Level.TRACE)

     // val metaProps = loadCfgsAndPrepareDeps("/tmp/scalamdlconfig.conf")
      var modelConfigName = ""
      var configToChoices: scala.collection.mutable.Map[Int,String] = scala.collection.mutable.Map[Int,String]()
      var configsKeys = MetadataAPIImpl.getModelConfigNames
      var i = 0
      configsKeys.foreach (configName => {
        configToChoices(i) = configName
        i = i + 1
      }) 
      
      println("Pick the Model Config from below choices")
      seq = 0
      configsKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")      
      print("\nEnter your choice: ")
      val choice2: Int = readInt()
      println("Entered CHOICE: "+choice2)
      if (choice2 == configsKeys.length + 1) {
        return
      }
    //  if (choice2 < 1 || choice2 > sourceFiles.length + 1) {
    //    logger.error("Invalid Choice : " + choice)
    //    return
    //  }

      println("Before modelConfigName")
      modelConfigName =  configToChoices(choice2 - 1)
      println("CHOSE " + (choice2-1) + "  "+modelConfigName)
      
      if( op.equalsIgnoreCase("add") ){
	println("Results as json string => \n" + 
		MetadataAPIImpl.AddModelFromSource(sourceStr, "java", modelConfigName, userid))
      }
      else{
	println("Results as json string => \n" + 
		MetadataAPIImpl.UpdateModel(sourceStr, "java", modelConfigName, userid))
      }
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

  def AddModelSourceScala(op: String): Unit = {
    try {
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MODEL_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Models"
        logger.debug("The environment variable MODEL_FILES_DIR is undefined, The directory defaults to " + dirName)
      }

      if (!IsValidDir(dirName))
        return
      val sourceFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".scala"))

      if (sourceFiles.length == 0) {
        logger.error("No model files in the directory " + dirName)
        return
      }

      var sourceFilePath = ""
      println("Pick a Model Definition file(source) from below choices")

      var seq = 0
      sourceFiles.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")

      print("\nEnter your choice: ")
      val choice: Int = readInt()

      if (choice == sourceFiles.length + 1) {
        return
      }
      if (choice < 1 || choice > sourceFiles.length + 1) {
        logger.error("Invalid Choice : " + choice)
        return
      }

      sourceFilePath = sourceFiles(choice - 1).toString
      val sourceStr = Source.fromFile(sourceFilePath).mkString
      // Save the model
      // MetadataAPIImpl.SetLoggerLevel(Level.TRACE)

     // val metaProps = loadCfgsAndPrepareDeps("/tmp/scalamdlconfig.conf")
      var modelConfigName = ""
      var configToChoices: scala.collection.mutable.Map[Int,String] = scala.collection.mutable.Map[Int,String]()
      var configsKeys = MetadataAPIImpl.getModelConfigNames
       var i = 0
      configsKeys.foreach (configName => {
        println("Addding "+ configName + " at location "+i )
        configToChoices(i) = configName
        i = i + 1
      }) 
      
      println("Pick the Model Config from below choices")
      seq = 0
      configsKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
      seq += 1
      println("[" + seq + "] Main Menu")      
      print("\nEnter your choice: ")
      val choice2: Int = readInt()
 
      if (choice2 == configsKeys.length + 1) {
        return
      }
      if (choice2 < 1 || choice2 > configsKeys.length + 1) {
        logger.error("Invalid Choice : " + choice)
        return
      }    
      modelConfigName =  configToChoices(choice2 - 1)
    
      println("CHOSE " + (choice2-1) + "  "+modelConfigName)
     
      if( op.equalsIgnoreCase("add") ){
	println("Results as json string => \n" +
	      MetadataAPIImpl.AddModelFromSource(sourceStr, "scala", modelConfigName, userid))
      }
      else{
	println("Results as json string => \n" +
	      MetadataAPIImpl.UpdateModel(sourceStr, "scala", modelConfigName, userid))
      }
	
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

  def AddModel {
    try {
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MODEL_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Models"
        logger.debug("The environment variable MODEL_FILES_DIR is undefined, The directory defaults to " + dirName)
      }

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
      // MetadataAPIImpl.SetLoggerLevel(Level.TRACE)

      println("Results as json string => \n" + MetadataAPIImpl.AddModel(pmmlStr, userid))
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

  def UploadCompileConfig {
     try {
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CONFIG_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/Kamanja/trunk/SampleApplication/Medical/Configs"
        logger.debug("The environment variable MODEL_FILES_DIR is undefined, The directory defaults to " + dirName)
      }

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
      // Save the model
      //  MetadataAPIImpl.SetLoggerLevel(Level.TRACE)
      println("Results as json string => \n" + MetadataAPIImpl.UploadModelsConfig(cfgStr, userid, "testConf"))
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
  
  def UploadEngineConfig {
    try {
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CONFIG_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/Kamanja/trunk/SampleApplication/Medical/Configs"
        logger.debug("The environment variable MODEL_FILES_DIR is undefined, The directory defaults to " + dirName)
      }

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
      // Save the model
      //  MetadataAPIImpl.SetLoggerLevel(Level.TRACE)
      println("Results as json string => \n" + MetadataAPIImpl.UploadConfig(cfgStr, userid, "testConf"))
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
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CONFIG_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/Kamanja/trunk/SampleApplication/Medical/Configs"
        logger.debug("The environment variable MODEL_FILES_DIR is undefined, The directory defaults to " + dirName)
      }

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
      // Save the model
      // MetadataAPIImpl.SetLoggerLevel(Level.TRACE)
      println("Results as json string => \n" + MetadataAPIImpl.RemoveConfig(cfgStr, userid, "n/a"))
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

  def UploadJarFile {
    try {
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_TARGET_DIR")
      if (dirName == null) {
        dirName = "/tmp/KamanjaInstall"
        logger.debug("The environment variable JAR_TARGET_DIR is undefined, The directory defaults to " + dirName)
      }

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
      // Save the jar
      // MetadataAPIImpl.SetLoggerLevel(Level.TRACE)
      println("Results as json string => \n" + MetadataAPIImpl.UploadJar(jarFilePath), userid)
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

  def LoadFunctionsFromAFile {
    try {
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("FUNCTION_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Functions"
        logger.debug("The environment variable FUNCTION_FILES_DIR is undefined, The directory defaults to " + dirName)
      }

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
      //MdMgr.GetMdMgr.truncate("FunctionDef")
      val apiResult = MetadataAPIImpl.AddFunctions(functionStr, "JSON", userid)
      // val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)
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

  def DumpAllFunctionsAsJson {
    try {
      val apiResult = MetadataAPIImpl.GetAllFunctionDefs("JSON", userid)
      //  val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def LoadConceptsFromAFile {
    try {
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CONCEPT_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Concepts"
        logger.debug("The environment variable CONCEPT_FILES_DIR is undefined, The directory defaults to " + dirName)
      }

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
      MdMgr.GetMdMgr.truncate("ConceptDef")
      val apiResult = MetadataAPIImpl.AddConcepts(conceptStr, "JSON", userid)
      // val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)
    } catch {
      case e: AlreadyExistsException => {
       
        logger.error("Concept Already in the metadata....")
      }
      case e: Exception => {
        //check again
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def DumpAllConceptsAsJson {
    try {
      val apiResult = MetadataAPIImpl.GetAllConcepts("JSON", userid)
      //  val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def LoadTypesFromAFile {
    try {
      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("TYPE_FILES_DIR")
      if (dirName == null) {
        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Types"
        logger.debug("The environment variable TYPE_FILES_DIR is undefined, The directory defaults to " + dirName)
      }

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
      //MdMgr.GetMdMgr.truncate("TypeDef")
      val apiResult = MetadataAPIImpl.AddTypes(typeStr, "JSON", userid)
      //   val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)
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

  def DumpAllTypesByObjTypeAsJson {
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

      val apiResult = MetadataAPIImpl.GetAllTypesByObjType("JSON", selectedType)
      //  val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => " + apiResult)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def DumpAllNodesAsJson {
    try {
      val apiResult = MetadataAPIImpl.GetAllNodes("JSON", userid)
      // val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def DumpAllClusterCfgsAsJson {
    try {
      val apiResult = MetadataAPIImpl.GetAllClusterCfgs("JSON", userid)
      //    val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def DumpAllClustersAsJson {
    try {
      val apiResult = MetadataAPIImpl.GetAllClusters("JSON", userid)
      // val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def DumpAllAdaptersAsJson {
    try {
      val apiResult = MetadataAPIImpl.GetAllAdapters("JSON", userid)
      //  val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def DumpAllCfgObjectsAsJson {
    try {
      val apiResult = MetadataAPIImpl.GetAllCfgObjects("JSON", userid)
      //    val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
      println("Result as Json String => \n" + apiResult)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }

  def initMsgCompilerBootstrap {
    MdMgr.GetMdMgr.truncate
    val mdLoader = new MetadataLoad(MdMgr.mdMgr, "", "", "", "")
    mdLoader.initialize
  }

  def DumpMetadata {
    //  MdMgr.GetMdMgr.SetLoggerLevel(Level.TRACE)
    MdMgr.GetMdMgr.dump
    //  MdMgr.GetMdMgr.SetLoggerLevel(Level.ERROR)
  }

  def initModCompilerBootstrap {
    MdMgr.GetMdMgr.truncate
    // logger.setLevel(Level.ERROR);
    val mdLoader = new MetadataLoad(MdMgr.GetMdMgr, "", "", "", "")
    mdLoader.initialize
  }

  def TestChill[T <: BaseElemDef](obj: List[T]) {
    val items = obj;

    logger.debug("Serializing " + obj.length + " objects ")
    val instantiator = new ScalaKryoInstantiator
    instantiator.setRegistrationRequired(false)

    val kryo = instantiator.newKryo()
    val baos = new ByteArrayOutputStream
    val output = new Output(baos, 4096)
    kryo.writeObject(output, items)

    val input = new Input(baos.toByteArray)
    val deser = kryo.readObject(input, classOf[List[T]])

    logger.debug("DeSerialized " + deser.length + " objects ")
    assert(deser.length == obj.length)
  }

  def TestKryoSerialize(configFile: String) {
    MetadataAPIImpl.InitMdMgrFromBootStrap(configFile, true)
    val msgDefs = MdMgr.GetMdMgr.Types(true, true)
    msgDefs match {
      case None => {
        logger.debug("No Messages found ")
      }
      case Some(ms) => {
        val msa = ms.toArray
        TestChill(msa.toList)
      }
    }
  }

  def TestSerialize1(serializeType: String) = {
    //MetadataAPIImpl.InitMdMgrFromBootStrap
    var serializer = SerializerManager.GetSerializer(serializeType)
    // serializer.SetLoggerLevel(Level.TRACE)
    val modelDefs = MdMgr.GetMdMgr.Models(true, true)
    modelDefs match {
      case None => {
        logger.debug("No Models found ")
      }
      case Some(ms) => {
        val msa = ms.toArray
        msa.foreach(m => {
          val ba = serializer.SerializeObjectToByteArray(m)
          MdMgr.GetMdMgr.ModifyModel(m.nameSpace, m.name, m.ver, "Remove")
          val m1 = serializer.DeserializeObjectFromByteArray(ba, m.getClass().getName()).asInstanceOf[ModelDef]
          val preJson = JsonSerializer.SerializeObjectToJson(m);
          val postJson = JsonSerializer.SerializeObjectToJson(m1);
          logger.debug("Length of pre  Json string => " + preJson.length)
          logger.debug("Length of post Json string => " + postJson.length)

          logger.debug("Json Before Any Serialization => " + preJson)
          logger.debug("Json After  Serialization/DeSerialization => " + postJson)
          //assert(preJson == postJson)
        })
      }
    }
  }

  def TestGenericProtobufSerializer = {
    val serializer = new ProtoBufSerializer
    // serializer.SetLoggerLevel(Level.TRACE)
    val a = MdMgr.GetMdMgr.MakeConcept("System", "concept1", "System", "Int", 1, false)
    //val ba = serializer.SerializeObjectToByteArray1(a)
    //val o = serializer.DeserializeObjectFromByteArray1(ba)
    //assert(JsonSerializer.SerializeObjectToJson(a) == JsonSerializer.SerializeObjectToJson(o.asInstanceOf[AttributeDef]))
  }

  def TestNotifyZooKeeper {
    val zkc = CreateClient.createSimple("localhost:2181")
    try {
      zkc.start()
      if (zkc.checkExists().forPath("/ligadata/models") == null) {
        zkc.create().withMode(CreateMode.PERSISTENT).forPath("/ligadata/models", null);
      }
      zkc.setData().forPath("/ligadata/models", "Activate ModelDef-2".getBytes);
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    } finally {
      zkc.close();
    }
  }

  def NotifyZooKeeperAddModelEvent {
    val modelDefs = MdMgr.GetMdMgr.Models(true, true)
    modelDefs match {
      case None => {
        logger.debug("No Models found ")
      }
      case Some(ms) => {
        val msa = ms.toArray
        val objList = new Array[BaseElemDef](msa.length)
        var i = 0
        msa.foreach(m => { objList(i) = m; i = i + 1 })
        val operations = for (op <- objList) yield "Add"
        MetadataAPIImpl.NotifyEngine(objList, operations)
      }
    }
  }

  def NotifyZooKeeperAddMessageEvent {
    val msgDefs = MdMgr.GetMdMgr.Messages(true, true)
    msgDefs match {
      case None => {
        logger.debug("No Msgs found ")
      }
      case Some(ms) => {
        val msa = ms.toArray
        val objList = new Array[BaseElemDef](msa.length)
        var i = 0
        msa.foreach(m => { objList(i) = m; i = i + 1 })
        val operations = for (op <- objList) yield "Add"
        MetadataAPIImpl.NotifyEngine(objList, operations)
      }
    }
  }

  def testSaveObject(key: String, value: String, storeType: String) {
    val serializer = SerializerManager.GetSerializer("kryo")
    var ba = serializer.SerializeObjectToByteArray(value)
    try {
      MetadataAPIImpl.UpdateObject(key, ba, storeType, "kryo")
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Failed to save the object : " + e.getMessage()+"\nStackTrace:"+stackTrace)
      }
    }
  }

  def testDbOp {
    try {
      val serializer = SerializerManager.GetSerializer("kryo")
      testSaveObject("key1", "value1", "other")
      var obj = MetadataAPIImpl.GetObject("key1", "other")
      var v = serializer.DeserializeObjectFromByteArray(obj.serializedInfo).asInstanceOf[String]
      assert(v == "value1")
      testSaveObject("key1", "value2", "other")
      obj = MetadataAPIImpl.GetObject("key1", "other")
      v = serializer.DeserializeObjectFromByteArray(obj.serializedInfo).asInstanceOf[String]
      assert(v == "value2")
      testSaveObject("key1", "value3", "other")
      obj = MetadataAPIImpl.GetObject("key1", "other")
      v = serializer.DeserializeObjectFromByteArray(obj.serializedInfo).asInstanceOf[String]
      assert(v == "value3")
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
      }
    }
  }
  
  def AddOutputMessage {
	    try {
	      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("OUTPUTMESSAGE_FILES_DIR")
	      if (dirName == null) {
	        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/OutputMessages"
	        logger.info("The environment variable OUTPUTMESSAGE_FILES_DIR is undefined, The directory defaults to " + dirName)
	      }

	      if (!IsValidDir(dirName))
	        return

	      val cfgFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
	      if (cfgFiles.length == 0) {
	        logger.fatal("No config files in the directory " + dirName)
	        return
	      }

	      val outputmsgFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
	      if (outputmsgFiles.length == 0) {
	        logger.fatal("No json message files in the directory " + dirName)
	        return
	      }
	      println("\nPick a Message Definition file(s) from below choices\n")

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
	          //logger.setLevel(Level.TRACE);
	          val outputmsgStr = Source.fromFile(outputmsgDefFile).mkString
	          //MetadataAPIImpl.SetLoggerLevel(Level.TRACE)
	          val res: String = MetadataAPIOutputMsg.AddOutputMessage(outputmsgStr, "JSON", userid)
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
	      var dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("OUTPUTMESSAGE_FILES_DIR")
	      if (dirName == null) {
	        dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("GIT_ROOT") + "/kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/OutputMessages"
	        logger.debug("The environment variable OUTPUTMESSAGE_FILES_DIR is undefined, the directory defaults to " + dirName)
	      }

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
	      println(outputmsgStr)
	      println("Results as json string => \n" + MetadataAPIOutputMsg.UpdateOutputMsg(outputmsgStr, userid))
	    } catch {
	      case e: Exception => {
	        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
	      }
	    }
	  }

	  def RemoveOutputMsg {
	    try {
	      //logger.setLevel(Level.TRACE);  //check again

	      val outputMsgKeys = MetadataAPIOutputMsg.GetAllOutputMsgsFromCache(true, userid)

	      if (outputMsgKeys.length == 0) {
	        println("Sorry, No output messages available in the Metadata")
	        return
	      }
        
	      println("\nPick the output message to be deleted from the following list: ")
	      var seq = 0
	      outputMsgKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })

	      print("\nEnter your choice: ")
	      val choice: Int = readInt()

	      if (choice < 1 || choice > outputMsgKeys.length) {
	        println("Invalid choice " + choice + ",start with main menu...")
	        return
	      }

	      val outputMsgKey = outputMsgKeys(choice - 1)
        val(outputNameSpace, outputName, outputVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(outputMsgKey)
	      val apiResult = MetadataAPIOutputMsg.RemoveOutputMsg(outputNameSpace, outputName, outputVersion.toLong, userid)

	      //   val apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
	      println("Result as Json String => \n" + apiResult)

	    } catch {
	      case e: Exception => {
	        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
	      }
	    }
	  }

	  def GetAllOutputMsgsFromCache {
	    try {
	      //logger.setLevel(Level.TRACE);  //check again
	      val outputMsgsKeys = MetadataAPIOutputMsg.GetAllOutputMsgsFromCache(true, userid)
	      if (outputMsgsKeys.length == 0) {
	        println("Sorry, No Output Msgs available in the Metadata")
	        return
	      }

	      var seq = 0
	      outputMsgsKeys.foreach(key => { seq += 1; println("[" + seq + "] " + key) })
	    } catch {
	      case e: Exception => {
	        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
	      }
	    }
	  }

  def StartTest{
    try{
      val dumpMetadata = ()               => { DumpMetadata }
      val addModel = ()                   => { AddModel }
      val getModel = ()                   => { GetModelFromCache }
      val getAllModels = ()               => { GetAllModelsFromCache }
      val removeModel = ()                => { RemoveModel }
      val updateModel = ()                => { UpdateModel }
      val deactivateModel = ()            => { DeactivateModel }
      val activateModel = ()              => { ActivateModel }
      val addMessage = ()                 => { AddMessage }
      val updateMessage = ()              => { UpdateMessage }
      val getMessage = ()                 => { GetMessageFromCache }
      val getAllMessages = ()             => { GetAllMessagesFromCache }
      val removeMessage = ()              => { RemoveMessage }
      val addContainer = ()               => { AddContainer }
      val updateContainer = ()            => { UpdateContainer}
      val getContainer = ()               => { GetContainerFromCache }
      val getAllContainers = ()           => { GetAllContainersFromCache }
      val removeContainer = ()            => { RemoveContainer }
      val addType         = ()            => { AddType }
      val getType         = ()            => { GetType }
      val getAllTypes     = ()            => { GetAllTypes }
      val removeType      = ()            => { RemoveType }
      val addFunction         = ()        => { AddFunction }
      val getFunction     =()             => { GetFunction }
      val removeFunction      = ()        => { RemoveFunction }
      val updateFunction         = ()     => { UpdateFunction }
      val addConcept         = ()         => { AddConcept }
      val removeConcept      = ()         => { RemoveConcept }
      val updateConcept         = ()      => { UpdateConcept }
      val dumpAllFunctionsAsJson = ()     => { DumpAllFunctionsAsJson }
      val loadFunctionsFromAFile = ()     => { LoadFunctionsFromAFile }
      val dumpAllConceptsAsJson = ()      => { DumpAllConceptsAsJson }
      val loadConceptsFromAFile = ()      => { LoadConceptsFromAFile }
      val dumpAllTypesByObjTypeAsJson = ()=> { DumpAllTypesByObjTypeAsJson }
      val loadTypesFromAFile = ()         => { LoadTypesFromAFile }
      val uploadJarFile = ()              => { UploadJarFile }
      val uploadEngineConfig = ()         => { UploadEngineConfig }
      val uploadCompileConfig = ()        => { UploadCompileConfig }
      val dumpAllNodes = ()               => { DumpAllNodesAsJson }
      val dumpAllClusters = ()            => { DumpAllClustersAsJson }
      val dumpAllClusterCfgs = ()         => { DumpAllClusterCfgsAsJson }
      val dumpAllAdapters = ()            => { DumpAllAdaptersAsJson }
      val dumpAllCfgObjects = ()          => { DumpAllCfgObjectsAsJson }
      val removeEngineConfig = ()         => { RemoveEngineConfig }
      val addOutputMessage = () 		  => { AddOutputMessage }
      val getAllOutputMsgs = () 		  => { GetAllOutputMsgsFromCache }
      val removeOutputMsg = () 			  => { RemoveOutputMsg }
      val updateOutputMsg = () 			  => { UpdateOutputMsg }
      val addModelSourceJava = ()         => { AddModelSourceJava("add") }
      val addModelSourceScala = ()        => { AddModelSourceScala("add") }
      val updateModelSourceJava = ()         => { AddModelSourceJava("update") }
      val updateModelSourceScala = ()        => { AddModelSourceScala("update") }

      val topLevelMenu = List(("Add Model",addModel),
            ("Add Model - Java", addModelSourceJava),
            ("Add Model - Scala", addModelSourceScala),
			      ("Get Model",getModel),
			      ("Get All Models",getAllModels),
			      ("Remove Model",removeModel),
            ("Update Model",updateModel),
			      ("Deactivate Model",deactivateModel),
			      ("Activate Model",activateModel),
			      ("Add Message",addMessage),
            ("Update Model - Java", updateModelSourceJava),
            ("Update Model - Scala", updateModelSourceScala),
            ("Update Message", updateMessage),
			      ("Get Message",getMessage),
			      ("Get All Messages",getAllMessages),
			      ("Remove Message",removeMessage),
			      ("Add Container",addContainer),
            ("Update Container", updateContainer),
			      ("Get Container",getContainer),
			      ("Get All Containers",getAllContainers),
			      ("Remove Container",removeContainer),
			      ("Add Type",addType),
			      ("Get Type",getType),
			      ("Get All Types",getAllTypes),
			      ("Remove Type",removeType),
			      ("Add Function",addFunction),
            ("Get Function", getFunction),
			      ("Remove Function",removeFunction),
			      ("Update Function",updateFunction),
			      ("Add Concept",addConcept),
			      ("Remove Concept",removeConcept),
			      ("Update Concept",updateConcept),
			      ("Load Concepts from a file",loadConceptsFromAFile),
			      ("Load Functions from a file",loadFunctionsFromAFile),
			      ("Load Types from a file",loadTypesFromAFile),
			      ("Dump All Metadata Keys",dumpMetadata),
			      ("Dump All Functions",dumpAllFunctionsAsJson),
			      ("Dump All Concepts",dumpAllConceptsAsJson),
			      ("Dump All Types By Object Type",dumpAllTypesByObjTypeAsJson),
			      ("Upload Any Jar",uploadJarFile),
			      ("Upload Engine Config",uploadEngineConfig),
            ("Upload Compile Config", uploadCompileConfig),
			      ("Dump Node Objects",dumpAllNodes),
			      ("Dump Cluster Objects",dumpAllClusters),
			      ("Dump ClusterCfg Node Objects",dumpAllClusterCfgs),
			      ("Dump Adapter Node Objects",dumpAllAdapters),
			      ("Dump All Config Objects",dumpAllCfgObjects),
			      ("Remove Engine Config",removeEngineConfig),
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

  private def PrintUsage(): Unit = {
    logger.warn("    --config <configfilename>")
  }

  private def nextOption(map: OptionMap, list: List[String]): OptionMap = {
    def isSwitch(s: String) = (s(0) == '-')
    list match {
      case Nil => map
      case "--config" :: value :: tail =>
        nextOption(map ++ Map('config -> value), tail)
      case option :: tail => {
        logger.error("Unknown option " + option)
        sys.exit(1)
      }
    }
  }

  def TestCheckAuth: Unit = {
    MetadataAPIImpl.checkAuth(Some("lonestarr"), Some("vespa"), Some("goodguy"), "winnebago:drive:eagle5")
  }

  def TestCheckAuth1: Unit = {
    MetadataAPIImpl.checkAuth(Some("Pete Minsky"), Some("readwrite"), Some("ReadwriteUsers"), "winnebago:drive:eagle5")
  }

  def TestLogAuditRec: Unit = {
    MetadataAPIImpl.logAuditRec(Some("lonestarr"), Some("write"), "Create Model Started", "system.copdriskassessment.100", "success", "-1", "Initiated operation")
  }

  def TestGetAuditRec: Unit = {
    MetadataAPIImpl.getAuditRec(new Date((new Date).getTime() - 1500 * 60000), null, null, null, null)
  }

  def TestGetAuditRec1: Unit = {
    val filterParameters = new Array[String](1)
    filterParameters(0) = "20150320000000"
    MetadataAPIImpl.getAuditRec(filterParameters)
  }

  def TestGetAuditRec2: Unit = {
    val filterParameters = new Array[String](2)
    filterParameters(0) = "20150320000000"
    filterParameters(1) = "20150323000000"
    MetadataAPIImpl.getAuditRec(filterParameters)
  }
/*
  def main(args: Array[String]) {
    try {
      var myConfigFile: String = null
      if (args.length == 0) {
        logger.error("Config File must be supplied, pass a config file as a command line argument:  --config /your-install-path/MetadataAPIConfig.properties")
        return
      } else {
        val options = nextOption(Map(), args.toList)
        val cfgfile = options.getOrElse('config, null)
        if (cfgfile == null) {
          logger.error("Need configuration file as parameter")
          throw new MissingArgumentException("Usage: configFile  supplied as --config myConfig.json")
        }
        myConfigFile = cfgfile.asInstanceOf[String]
      }
      MetadataAPIImpl.InitMdMgrFromBootStrap(myConfigFile, true)

      StartTest
    } catch {
      case e: Throwable => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("StackTrace:"+stackTrace)
      }
    } finally {
      MetadataAPIImpl.shutdown
    }
  } */
}
