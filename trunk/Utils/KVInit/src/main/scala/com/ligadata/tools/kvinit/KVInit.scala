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

package com.ligadata.tools.kvinit

import scala.collection.mutable._
import scala.io.Source
import scala.util.control.Breaks._
import java.io.BufferedWriter
import java.io.FileWriter
import sys.process._
import java.io.PrintWriter
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.keyvaluestore._
import com.ligadata.KamanjaBase._
import com.ligadata.kamanja.metadataload.MetadataLoad
import com.ligadata.Utils.{ Utils, KamanjaClassLoader, KamanjaLoaderInfo }
import java.util.Properties
import com.ligadata.MetadataAPI.MetadataAPIImpl
import com.ligadata.kamanja.metadata.MdMgr._
import com.ligadata.kamanja.metadata._
import scala.reflect.runtime.{ universe => ru }
import com.ligadata.Serialize._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
//import scala.util.control.Breaks._
import scala.collection.mutable.ArrayBuffer
import com.ligadata.ZooKeeper._
import org.apache.curator.framework._
import com.ligadata.Serialize.{ JDataStore, JZKInfo, JEnvCtxtJsonStr }
import com.ligadata.KvBase.{ Key, Value, TimeRange, KvBaseDefalts, KeyWithBucketIdAndPrimaryKey, KeyWithBucketIdAndPrimaryKeyCompHelper, LoadKeyWithBucketId }
import com.ligadata.StorageBase.{ DataStore, Transaction }
import com.ligadata.Exceptions.StackTrace
import java.util.{ Collection, Iterator, TreeMap }
import com.ligadata.Exceptions._

trait LogTrait {
  val loggerName = this.getClass.getName()
  val logger = LogManager.getLogger(loggerName)
}

object KVInit extends App with LogTrait {

  def usage: String = {
    """ 
Usage: scala com.ligadata.kvinit.KVInit 
    --config <config file while has jarpaths, metadata store information & data store information>
    --typename <full package qualified name of a Container or Message> 
    --datafiles <input to load> 
    --keyfieldname  <name of one of the fields in the first line of the datafiles file>

Nothing fancy here.  Mapdb kv store is created from arguments... style is hash map. Support
for other styles of input (e.g., JSON, XML) are not supported.  
      
The name of the kvstore will be the classname(without it path).

It is expected that the first row of the csv file will be the column names.  One of the names
must be specified as the key field name.  Failure to find this name causes termination and
no kv store creation.
      
Sample uses:
      java -jar /tmp/KamanjaInstall/KVInit-1.0 --typename System.TestContainer --config /tmp/KamanjaInstall/EngineConfig.cfg --datafiles /tmp/KamanjaInstall/sampledata/TestContainer.csv --keyfieldname Id

"""
  }

  override def main(args: Array[String]) {

    logger.debug("KVInit.main begins")

    if (args.length == 0) logger.error(usage)
    val arglist = args.toList
    type OptionMap = Map[Symbol, String]
    logger.debug(arglist)
    def nextOption(map: OptionMap, list: List[String]): OptionMap = {
      list match {
        case Nil => map
        case "--config" :: value :: tail =>
          nextOption(map ++ Map('config -> value), tail)
        case "--kvname" :: value :: tail => // Deprecated, use typename instead
          nextOption(map ++ Map('kvname -> value), tail) // Deprecated, use typename instead
        case "--typename" :: value :: tail =>
          nextOption(map ++ Map('typename -> value), tail)
        case "--csvpath" :: value :: tail => // Deprecated, use datafiles instead
          nextOption(map ++ Map('csvpath -> value), tail) // Deprecated, use datafiles instead
        case "--datafiles" :: value :: tail =>
          nextOption(map ++ Map('datafiles -> value), tail)
        case "--keyfieldname" :: value :: tail => // Deprecated, use keyfields instead
          nextOption(map ++ Map('keyfieldname -> value), tail) // Deprecated, use keyfields instead
        case "--keyfields" :: value :: tail =>
          nextOption(map ++ Map('keyfields -> value), tail)
        case "--delimiter" :: value :: tail =>
          nextOption(map ++ Map('delimiter -> value), tail)
        case "--keyandvaluedelimiter" :: value :: tail =>
          nextOption(map ++ Map('keyandvaluedelimiter -> value), tail)
        case "--fielddelimiter" :: value :: tail =>
          nextOption(map ++ Map('fielddelimiter -> value), tail)
        case "--valuedelimiter" :: value :: tail =>
          nextOption(map ++ Map('valuedelimiter -> value), tail)
        case "--ignoreerrors" :: value :: tail =>
          nextOption(map ++ Map('ignoreerrors -> value), tail)
        case "--ignorerecords" :: value :: tail =>
          nextOption(map ++ Map('ignorerecords -> value), tail)
        case "--format" :: value :: tail =>
          nextOption(map ++ Map('format -> value), tail)
        case option :: tail =>
          logger.error("Unknown option " + option)
          sys.exit(1)
      }
    }

    val options = nextOption(Map(), arglist)

    var cfgfile = if (options.contains('config)) options.apply('config) else null
    var typename = if (options.contains('typename)) options.apply('typename) else if (options.contains('kvname)) options.apply('kvname) else null
    var tmpdatafiles = if (options.contains('datafiles)) options.apply('datafiles) else if (options.contains('csvpath)) options.apply('csvpath) else null
    val tmpkeyfieldnames = if (options.contains('keyfields)) options.apply('keyfields) else if (options.contains('keyfieldname)) options.apply('keyfieldname) else null
    val delimiterString = if (options.contains('delimiter)) options.apply('delimiter) else null
    val keyAndValueDelimiter = if (options.contains('keyandvaluedelimiter)) options.apply('keyandvaluedelimiter) else null
    val fieldDelimiter1 = if (options.contains('fielddelimiter)) options.apply('fielddelimiter) else null
    val valueDelimiter = if (options.contains('valuedelimiter)) options.apply('valuedelimiter) else null
    var ignoreerrors = (if (options.contains('ignoreerrors)) options.apply('ignoreerrors) else "0").trim
    val format = (if (options.contains('format)) options.apply('format) else "delimited").trim.toLowerCase()
    var commitBatchSize = (if (options.contains('commitbatchsize)) options.apply('commitbatchsize) else "10000").trim.toInt

    if (commitBatchSize <= 0) {
      logger.error("commitbatchsize must be greater than 0")
      return
    }

    val fieldDelimiter = if (fieldDelimiter1 != null) fieldDelimiter1 else delimiterString

    if (!(format.equals("delimited") || format.equals("json"))) {
      logger.error("Supported formats are only delimited & json")
      return
    }

    val keyfieldnames = if (tmpkeyfieldnames != null && tmpkeyfieldnames.trim.size > 0) tmpkeyfieldnames.trim.split(",").map(_.trim).filter(_.length() > 0) else Array[String]()

    val ignoreRecords = (if (options.contains('ignorerecords)) options.apply('ignorerecords) else "0".toString).trim.toInt

    if (ignoreerrors.size == 0) ignoreerrors = "0"

    if (options.contains('keyfieldname) && keyfieldnames.size == 0) {
      logger.error("keyfieldname does not have valid strings to take header")
      return
    }

    val dataFiles = if (tmpdatafiles == null || tmpdatafiles.trim.size == 0) Array[String]() else tmpdatafiles.trim.split(",").map(_.trim).filter(_.length() > 0)

    var valid: Boolean = (cfgfile != null && dataFiles.size > 0 && typename != null)

    if (valid) {
      cfgfile = cfgfile.trim
      typename = typename.trim
      valid = (cfgfile.size != 0 && dataFiles.size > 0 && typename.size != 0)
    }

    if (valid) {
      val (loadConfigs, failStr) = Utils.loadConfiguration(cfgfile.toString, true)
      if (failStr != null && failStr.size > 0) {
        logger.error(failStr)
        return
      }
      if (loadConfigs == null) {
        logger.error("Failed to load configurations from configuration file")
        return
      }

      KvInitConfiguration.configFile = cfgfile.toString

      val kvmaker: KVInit = new KVInit(loadConfigs, typename.toLowerCase, dataFiles, keyfieldnames, keyAndValueDelimiter, fieldDelimiter, valueDelimiter, ignoreerrors, ignoreRecords, format, commitBatchSize)
      if (kvmaker.isOk) {
        try {
          val dstore = kvmaker.GetDataStoreHandle(KvInitConfiguration.jarPaths, kvmaker.dataDataStoreInfo)
          if (dstore != null) {
            try {
              kvmaker.buildContainerOrMessage(dstore)
            } catch {
              case e: Exception => {
                val stackTrace = StackTrace.ThrowableTraceString(e)
                logger.error("Failed to build Container or Message." + "\nStackTrace:" + stackTrace)
              }
            } finally {
              if (dstore != null)
                dstore.Shutdown()
              com.ligadata.transactions.NodeLevelTransService.Shutdown
              if (kvmaker.zkcForSetData != null)
                kvmaker.zkcForSetData.close()
            }
          }
        } catch {
          case e: FatalAdapterException => {
            val causeStackTrace = StackTrace.ThrowableTraceString(e.cause)
            logger.error("Failed to connect to Datastore. Reason:" + e.getCause + ". Message:" + e.getMessage + "\nCause:\n" + causeStackTrace)
          }
          case e: StorageConnectionException => {
            val causeStackTrace = StackTrace.ThrowableTraceString(e.cause)
            logger.error("Failed to connect to Datastore. Reason:" + e.getCause + ". Message:" + e.getMessage + "\nCause:\n" + causeStackTrace)
          }
          case e: StorageFetchException => {
            val causeStackTrace = StackTrace.ThrowableTraceString(e.cause)
            logger.error("Failed to connect to Datastore. Reason:" + e.getCause + ". Message:" + e.getMessage + "\nCause:\n" + causeStackTrace)
          }
          case e: StorageDMLException => {
            val causeStackTrace = StackTrace.ThrowableTraceString(e.cause)
            logger.error("Failed to connect to Datastore. Reason:" + e.getCause + ". Message:" + e.getMessage + "\nCause:\n" + causeStackTrace)
          }
          case e: StorageDDLException => {
            val causeStackTrace = StackTrace.ThrowableTraceString(e.cause)
            logger.error("Failed to connect to Datastore. Reason:" + e.getCause + ". Message:" + e.getMessage + "\nCause:\n" + causeStackTrace)
          }
          case e: Exception => {
            val causeStackTrace = StackTrace.ThrowableTraceString(e)
            logger.error("Failed to connect to Datastore. Reason:" + e.getCause + ". Message:" + e.getMessage + "\nCause:\n" + causeStackTrace)
          }
          case e: Throwable => {
            val causeStackTrace = StackTrace.ThrowableTraceString(e)
            logger.error("Failed to connect to Datastore. Reason:" + e.getCause + ". Message:" + e.getMessage + "\nCause:\n" + causeStackTrace)
          }
        }
      }
      MetadataAPIImpl.CloseDbStore

    } else {
      logger.error("Illegal and/or missing arguments")
      logger.error(usage)
    }
  }
}

object KvInitConfiguration {
  var nodeId: Int = _
  var configFile: String = _
  var jarPaths: collection.immutable.Set[String] = _
}

class KVInit(val loadConfigs: Properties, val typename: String, val dataFiles: Array[String], val keyfieldnames: Array[String], keyAndValueDelimiter1: String,
             fieldDelimiter1: String, valueDelimiter1: String, ignoreerrors: String, ignoreRecords: Int, format: String, commitBatchSize: Int) extends LogTrait with MdBaseResolveInfo {
  val fieldDelimiter = if (DataDelimiters.IsEmptyDelimiter(fieldDelimiter1) == false) fieldDelimiter1 else ","
  val keyAndValueDelimiter = if (DataDelimiters.IsEmptyDelimiter(keyAndValueDelimiter1) == false) keyAndValueDelimiter1 else "\\x01"
  val valueDelimiter = if (DataDelimiters.IsEmptyDelimiter(valueDelimiter1) == false) valueDelimiter1 else "~"

  var ignoreErrsCount = if (ignoreerrors != null && ignoreerrors.size > 0) ignoreerrors.toInt else 0
  if (ignoreErrsCount < 0) ignoreErrsCount = 0
  var isOk: Boolean = true
  val isDelimited = format.equals("delimited")
  val isJson = format.equals("json")
  val isKv = format.equals("kv")
  var zkcForSetData: CuratorFramework = null
  var totalCommittedMsgs: Int = 0

  val kvInitLoader = new KamanjaLoaderInfo

  KvInitConfiguration.nodeId = loadConfigs.getProperty("nodeId".toLowerCase, "0").replace("\"", "").trim.toInt
  if (KvInitConfiguration.nodeId <= 0) {
    logger.error("Not found valid nodeId. It should be greater than 0")
    isOk = false
  }

  var nodeInfo: NodeInfo = _

  if (isOk) {
    MetadataAPIImpl.InitMdMgrFromBootStrap(KvInitConfiguration.configFile, false)

    nodeInfo = mdMgr.Nodes.getOrElse(KvInitConfiguration.nodeId.toString, null)
    if (nodeInfo == null) {
      logger.error("Node %d not found in metadata".format(KvInitConfiguration.nodeId))
      isOk = false
    }
  }

  if (isOk) {
    KvInitConfiguration.jarPaths = if (nodeInfo.JarPaths == null) Array[String]().toSet else nodeInfo.JarPaths.map(str => str.replace("\"", "").trim).filter(str => str.size > 0).toSet
    if (KvInitConfiguration.jarPaths.size == 0) {
      logger.error("Not found valid JarPaths.")
      isOk = false
    }
  }

  val cluster = if (isOk) mdMgr.ClusterCfgs.getOrElse(nodeInfo.ClusterId, null) else null
  if (isOk && cluster == null) {
    logger.error("Cluster not found for Node %d  & ClusterId : %s".format(KvInitConfiguration.nodeId, nodeInfo.ClusterId))
    isOk = false
  }

  val dataStore = if (isOk) cluster.cfgMap.getOrElse("DataStore", null) else null
  if (isOk && dataStore == null) {
    logger.error("DataStore not found for Node %d  & ClusterId : %s".format(KvInitConfiguration.nodeId, nodeInfo.ClusterId))
    isOk = false
  }

  val zooKeeperInfo = if (isOk) cluster.cfgMap.getOrElse("ZooKeeperInfo", null) else null
  if (isOk && dataStore == null) {
    logger.error("ZooKeeperInfo not found for Node %d  & ClusterId : %s".format(KvInitConfiguration.nodeId, nodeInfo.ClusterId))
    isOk = false
  }

  var dataDataStoreInfo: String = null
  var zkConnectString: String = null
  var zkNodeBasePath: String = null
  var zkSessionTimeoutMs: Int = 0
  var zkConnectionTimeoutMs: Int = 0

  if (isOk) {
    implicit val jsonFormats: Formats = DefaultFormats
    // val dataStoreInfo = parse(dataStore).extract[JDataStore]
    val zKInfo = parse(zooKeeperInfo).extract[JZKInfo]

    dataDataStoreInfo = dataStore

    if (isOk) {
      zkConnectString = zKInfo.ZooKeeperConnectString.replace("\"", "").trim
      zkNodeBasePath = zKInfo.ZooKeeperNodeBasePath.replace("\"", "").trim
      zkSessionTimeoutMs = if (zKInfo.ZooKeeperSessionTimeoutMs == None || zKInfo.ZooKeeperSessionTimeoutMs == null) 0 else zKInfo.ZooKeeperSessionTimeoutMs.get.toString.toInt
      zkConnectionTimeoutMs = if (zKInfo.ZooKeeperConnectionTimeoutMs == None || zKInfo.ZooKeeperConnectionTimeoutMs == null) 0 else zKInfo.ZooKeeperConnectionTimeoutMs.get.toString.toInt

      // Taking minimum values in case if needed
      if (zkSessionTimeoutMs <= 0)
        zkSessionTimeoutMs = 30000
      if (zkConnectionTimeoutMs <= 0)
        zkConnectionTimeoutMs = 30000
    }

    if (zkConnectString.size == 0) {
      logger.warn("Not found valid Zookeeper connection string.")
    }

    if (zkConnectString.size > 0 && zkNodeBasePath.size == 0) {
      logger.warn("Not found valid Zookeeper ZNode Base Path.")
    }
  }

  var typeNameCorrType: BaseTypeDef = _
  var kvTableName: String = _
  var messageObj: BaseMsgObj = _
  var containerObj: BaseContainerObj = _
  var objFullName: String = _
  // var kryoSer: com.ligadata.Serialize.Serializer = null

  if (isOk) {
    typeNameCorrType = mdMgr.ActiveType(typename.toLowerCase)
    if (typeNameCorrType == null || typeNameCorrType == None) {
      logger.error("Not found valid type for " + typename.toLowerCase)
      isOk = false
    } else {
      objFullName = typeNameCorrType.FullName.toLowerCase
      kvTableName = objFullName.replace('.', '_')
    }
  }

  var isMsg = false
  var isContainer = false

  if (isOk) {
    isOk = LoadJarIfNeeded(typeNameCorrType, kvInitLoader.loadedJars, kvInitLoader.loader)
  }

  if (isOk) {
    var clsName = typeNameCorrType.PhysicalName.trim
    if (clsName.size > 0 && clsName.charAt(clsName.size - 1) != '$') // if no $ at the end we are taking $
      clsName = clsName + "$"

    if (isMsg == false) {
      // Checking for Message
      try {
        // Convert class name into a class
        var curClz = Class.forName(clsName, true, kvInitLoader.loader)

        while (curClz != null && isContainer == false) {
          isContainer = Utils.isDerivedFrom(curClz, "com.ligadata.KamanjaBase.BaseContainerObj")
          if (isContainer == false)
            curClz = curClz.getSuperclass()
        }
      } catch {
        case e: Exception => {
          logger.error("Failed to load message class %s with Reason:%s Message:%s".format(clsName, e.getCause, e.getMessage))
        }
      }
    }

    if (isContainer == false) {
      // Checking for container
      try {
        // If required we need to enable this test
        // Convert class name into a class
        var curClz = Class.forName(clsName, true, kvInitLoader.loader)

        while (curClz != null && isMsg == false) {
          isMsg = Utils.isDerivedFrom(curClz, "com.ligadata.KamanjaBase.BaseMsgObj")
          if (isMsg == false)
            curClz = curClz.getSuperclass()
        }
      } catch {
        case e: Exception => {
          logger.error("Failed to load container class %s with Reason:%s Message:%s".format(clsName, e.getCause, e.getMessage))
        }
      }
    }

    if (isMsg || isContainer) {
      try {
        val module = kvInitLoader.mirror.staticModule(clsName)
        val obj = kvInitLoader.mirror.reflectModule(module)
        val objinst = obj.instance
        if (objinst.isInstanceOf[BaseMsgObj]) {
          messageObj = objinst.asInstanceOf[BaseMsgObj]
          logger.debug("Created Message Object")
        } else if (objinst.isInstanceOf[BaseContainerObj]) {
          containerObj = objinst.asInstanceOf[BaseContainerObj]
          logger.debug("Created Container Object")
        } else {
          logger.error("Failed to instantiate message or conatiner object :" + clsName)
          isOk = false
        }
      } catch {
        case e: Exception => {
          logger.error("Failed to instantiate message or conatiner object:" + clsName + ". Reason:" + e.getCause + ". Message:" + e.getMessage())
          isOk = false
        }
      }
    } else {
      logger.error("Failed to instantiate message or conatiner object :" + clsName)
      isOk = false
    }
    /*
    if (isOk) {
      kryoSer = SerializerManager.GetSerializer("kryo")
      if (kryoSer != null)
        kryoSer.SetClassLoader(kvInitLoader.loader)
    }
*/
  }

  private def LoadJarIfNeeded(elem: BaseElem, loadedJars: TreeSet[String], loader: KamanjaClassLoader): Boolean = {
    if (KvInitConfiguration.jarPaths == null) return false

    var retVal: Boolean = true
    var allJars: Array[String] = null

    val jarname = if (elem.JarName == null) "" else elem.JarName.trim

    if (elem.DependencyJarNames != null && elem.DependencyJarNames.size > 0 && jarname.size > 0) {
      allJars = elem.DependencyJarNames :+ jarname
    } else if (elem.DependencyJarNames != null && elem.DependencyJarNames.size > 0) {
      allJars = elem.DependencyJarNames
    } else if (jarname.size > 0) {
      allJars = Array(jarname)
    } else {
      return retVal
    }

    val jars = allJars.map(j => Utils.GetValidJarFile(KvInitConfiguration.jarPaths, j))

    // Loading all jars
    for (j <- jars) {
      logger.debug("Processing Jar " + j.trim)
      val fl = new File(j.trim)
      if (fl.exists) {
        try {
          if (loadedJars(fl.getPath())) {
            logger.debug("Jar " + j.trim + " already loaded to class path.")
          } else {
            loader.addURL(fl.toURI().toURL())
            logger.debug("Jar " + j.trim + " added to class path.")
            loadedJars += fl.getPath()
          }
        } catch {
          case e: Exception => {
            logger.error("Jar " + j.trim + " failed added to class path. Message: " + e.getMessage)
            return false
          }
        }
      } else {
        logger.error("Jar " + j.trim + " not found")
        return false
      }
    }

    true
  }

  override def getMessgeOrContainerInstance(MsgContainerType: String): MessageContainerBase = {
    if (MsgContainerType.compareToIgnoreCase(objFullName) != 0)
      return null
    // Simply creating new object and returning. Not checking for MsgContainerType. This is issue if the child level messages ask for the type 
    if (isMsg)
      return messageObj.CreateNewMessage
    if (isContainer)
      return containerObj.CreateNewContainer
    return null
  }

  private def GetDataStoreHandle(jarPaths: collection.immutable.Set[String], dataStoreInfo: String): DataStore = {
    try {
      logger.debug("Getting DB Connection for dataStoreInfo:%s".format(dataStoreInfo))
      return KeyValueManager.Get(jarPaths, dataStoreInfo)
    } catch {
      case e: Exception => throw e
      case e: Throwable => throw e
    }
  }

  // We are not expecting Container/MessageType as first child of JSON. This is just to match to Delimited data, where we don't have type in the data.
  private def prepareInputData(inputStr: String): InputData = {
    /** if we can make one ... we add the data to the store. This will crash if the data is bad */
    if (isDelimited) {
      val fieldDelimiter = if (DataDelimiters.IsEmptyDelimiter(fieldDelimiter1) == false) fieldDelimiter1 else ","
      val keyAndValueDelimiter = if (DataDelimiters.IsEmptyDelimiter(keyAndValueDelimiter1) == false) keyAndValueDelimiter1 else "\\x01"
      val valueDelimiter = if (DataDelimiters.IsEmptyDelimiter(valueDelimiter1) == false) valueDelimiter1 else "~"
      val delimiters = new DataDelimiters()
      delimiters.keyAndValueDelimiter = keyAndValueDelimiter
      delimiters.fieldDelimiter = fieldDelimiter
      delimiters.valueDelimiter = valueDelimiter
      val inputData = new DelimitedData(inputStr, delimiters)
      inputData.tokens = inputData.dataInput.split(inputData.delimiters.fieldDelimiter, -1)
      inputData.curPos = 0
      return inputData
    }

    if (isKv) {
      val fieldDelimiter = if (DataDelimiters.IsEmptyDelimiter(fieldDelimiter1) == false) fieldDelimiter1 else ","
      val keyAndValueDelimiter = if (DataDelimiters.IsEmptyDelimiter(keyAndValueDelimiter1) == false) keyAndValueDelimiter1 else "\\x01"
      val valueDelimiter = if (DataDelimiters.IsEmptyDelimiter(valueDelimiter1) == false) valueDelimiter1 else "~"
      val delimiters = new DataDelimiters()
      delimiters.keyAndValueDelimiter = keyAndValueDelimiter
      delimiters.fieldDelimiter = fieldDelimiter
      delimiters.valueDelimiter = valueDelimiter
      val inputData = new KvData(inputStr, delimiters)

      val dataMap = scala.collection.mutable.Map[String, String]()

      val str_arr = inputData.dataInput.split(delimiters.fieldDelimiter, -1)

      if (delimiters.fieldDelimiter.compareTo(delimiters.keyAndValueDelimiter) == 0) {
        if (str_arr.size % 2 != 0) {
          throw new Exception("Expecting Key & Value pairs are even number of tokens when FieldDelimiter & KeyAndValueDelimiter are matched")
        }
        for (i <- 0 until str_arr.size by 2) {
          dataMap(str_arr(i).trim) = str_arr(i + 1)
        }
      } else {
        str_arr.foreach(kv => {
          val kvpair = kv.split(delimiters.keyAndValueDelimiter)
          if (kvpair.size != 2) {
            throw new Exception("Expecting Key & Value pair only")
          }
          dataMap(kvpair(0).trim) = kvpair(1)
        })
      }

      inputData.dataMap = dataMap.toMap
      return inputData
    }

    if (isJson) {
      try {
        val json = parse(inputStr)
        if (json == null || json.values == null) {
          logger.error("Invalid JSON data: " + inputStr)
          return null
        } else {
          val parsed_json = json.values.asInstanceOf[scala.collection.immutable.Map[String, Any]]
          val inputData = new JsonData(inputStr)

          inputData.root_json = Option(parsed_json)
          inputData.cur_json = Option(parsed_json)
          return inputData
        }
      } catch {
        case e: Exception => {
          logger.error("Invalid JSON data:%s, Reason:%s, Message:%s".format(inputStr, e.getCause, e.getMessage()))
          return null
        }
      }
    }

    logger.error("Not handling anything other than JSON & Delimited formats. Found:" + format)
    return null
  }

  private def collectKeyAndValues(k: Key, v: Value, dataByBucketKeyPart: TreeMap[KeyWithBucketIdAndPrimaryKey, MessageContainerBaseWithModFlag], loadedKeys: java.util.TreeSet[LoadKeyWithBucketId]): Unit = {
    val value = SerializeDeserialize.Deserialize(v.serializedInfo, this, kvInitLoader.loader, true, "")
    val primarykey = value.PrimaryKeyData
    val key = KeyWithBucketIdAndPrimaryKey(KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(k.bucketKey), k, primarykey != null && primarykey.size > 0, primarykey)
    dataByBucketKeyPart.put(key, MessageContainerBaseWithModFlag(false, value))

    val bucketId = KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(k.bucketKey)
    val loadKey = LoadKeyWithBucketId(bucketId, TimeRange(k.timePartition, k.timePartition), k.bucketKey)
    loadedKeys.add(loadKey)
  }

  private def LoadDataIfNeeded(loadKey: LoadKeyWithBucketId, loadedKeys: java.util.TreeSet[LoadKeyWithBucketId], dataByBucketKeyPart: TreeMap[KeyWithBucketIdAndPrimaryKey, MessageContainerBaseWithModFlag], kvstore: DataStore): Unit = {
    if (loadedKeys.contains(loadKey))
      return
    val buildOne = (k: Key, v: Value) => {
      collectKeyAndValues(k, v, dataByBucketKeyPart, loadedKeys)
    }

    var failedWaitTime = 15000 // Wait time starts at 15 secs
    val maxFailedWaitTime = 60000 // Max Wait time 60 secs
    var doneGet = false

    while (!doneGet) {
      try {
        kvstore.get(objFullName, Array(loadKey.tmRange), Array(loadKey.bucketKey), buildOne)
        loadedKeys.add(loadKey)
        doneGet = true
      } catch {
        case e @ (_: ObjectNotFoundException | _: KeyNotFoundException) => {
          logger.debug("In Container %s Key %s Not found for timerange: %d-%d".format(objFullName, loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime))
          doneGet = true
        }
        case e: FatalAdapterException => {
          val stackTrace = StackTrace.ThrowableTraceString(e.cause)
          logger.error("In Container %s Key %s Not found for timerange: %d-%d.\nStackTrace:%s".format(objFullName, loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime, stackTrace))
        }
        case e: StorageDMLException => {
          val stackTrace = StackTrace.ThrowableTraceString(e.cause)
          logger.error("In Container %s Key %s Not found for timerange: %d-%d.\nStackTrace:%s".format(objFullName, loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime, stackTrace))
        }
        case e: StorageDDLException => {
          val stackTrace = StackTrace.ThrowableTraceString(e.cause)
          logger.error("In Container %s Key %s Not found for timerange: %d-%d.\nStackTrace:%s".format(objFullName, loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime, stackTrace))
        }
        case e: Exception => {
          val stackTrace = StackTrace.ThrowableTraceString(e)
          logger.error("In Container %s Key %s Not found for timerange: %d-%d.\nStackTrace:%s".format(objFullName, loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime, stackTrace))
        }
        case e: Throwable => {
          val stackTrace = StackTrace.ThrowableTraceString(e)
          logger.error("In Container %s Key %s Not found for timerange: %d-%d.\nStackTrace:%s".format(objFullName, loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime, stackTrace))
        }
      }

      if (!doneGet) {
        try {
          logger.error("Failed to get data from datastore. Waiting for another %d milli seconds and going to start them again.".format(failedWaitTime))
          Thread.sleep(failedWaitTime)
        } catch {
          case e: Exception => {}
        }
        // Adjust time for next time
        if (failedWaitTime < maxFailedWaitTime) {
          failedWaitTime = failedWaitTime * 2
          if (failedWaitTime > maxFailedWaitTime)
            failedWaitTime = maxFailedWaitTime
        }
      }
    }
  }

  private def commitData(transId: Long, kvstore: DataStore, dataByBucketKeyPart: TreeMap[KeyWithBucketIdAndPrimaryKey, MessageContainerBaseWithModFlag], commitBatchSize: Int, processedRows: Int): Unit = {
    val storeObjects = new ArrayBuffer[(Key, Value)](dataByBucketKeyPart.size())
    var it1 = dataByBucketKeyPart.entrySet().iterator()
    while (it1.hasNext()) {
      val entry = it1.next();

      val value = entry.getValue();
      if (value.modified) {
        val key = entry.getKey();
        try {
          val k = entry.getKey().key
          val v = Value("manual", SerializeDeserialize.Serialize(value.value))
          storeObjects += ((k, v))
        } catch {
          case e: Exception => {
            logger.error("Failed to serialize/write data.")
            throw e
          }
        }
      }
    }

    var failedWaitTime = 15000 // Wait time starts at 15 secs
    val maxFailedWaitTime = 60000 // Max Wait time 60 secs
    var doneSave = false

    while (!doneSave) {
      try {
        kvstore.put(Array((objFullName, storeObjects.toArray)))
        doneSave = true
      } catch {
        case e: FatalAdapterException => {
          val causeStackTrace = StackTrace.ThrowableTraceString(e.cause)
          logger.error("Failed to save data into datastore, cause: \n" + causeStackTrace)
        }
        case e: StorageDMLException => {
          val causeStackTrace = StackTrace.ThrowableTraceString(e.cause)
          logger.error("Failed to save data into datastore, cause: \n" + causeStackTrace)
        }
        case e: StorageDDLException => {
          val causeStackTrace = StackTrace.ThrowableTraceString(e.cause)
          logger.error("Failed to save data into datastore, cause: \n" + causeStackTrace)
        }
        case e: Exception => {
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          logger.error("Failed to save data into datastore, cause: \n" + causeStackTrace)
        }
        case e: Throwable => {
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          logger.error("Failed to save data into datastore, cause: \n" + causeStackTrace)
        }
      }

      if (!doneSave) {
        try {
          logger.error("Failed to save data into datastore. Waiting for another %d milli seconds and going to start them again.".format(failedWaitTime))
          Thread.sleep(failedWaitTime)
        } catch {
          case e: Exception => {}
        }
        // Adjust time for next time
        if (failedWaitTime < maxFailedWaitTime) {
          failedWaitTime = failedWaitTime * 2
          if (failedWaitTime > maxFailedWaitTime)
            failedWaitTime = maxFailedWaitTime
        }
      }
    }

    val savedKeys = storeObjects.map(kv => kv._1)

    totalCommittedMsgs += storeObjects.size

    val msgStr = "%s: Inserted %d keys in this commit. Totals (Processed:%d, inserted:%d) so far".format(GetCurDtTmStr, storeObjects.size, processedRows, totalCommittedMsgs)
    logger.info(msgStr)
    println(msgStr)

    if (zkcForSetData != null) {
      logger.info("Notifying Engines after updating is done through Zookeeper.")
      try {
        val dataChangeZkNodePath = zkNodeBasePath + "/datachange"
        val changedContainersData = Map[String, List[Key]]()
        changedContainersData(typename) = savedKeys.toList
        val datachangedata = ("txnid" -> transId.toString) ~
          ("changeddatakeys" -> changedContainersData.map(kv =>
            ("C" -> kv._1) ~
              ("K" -> kv._2.map(k =>
                ("tm" -> k.timePartition) ~
                  ("bk" -> k.bucketKey.toList) ~
                  ("tx" -> k.transactionId) ~
                  ("rid" -> k.rowId)))))
        val sendJson = compact(render(datachangedata))
        zkcForSetData.setData().forPath(dataChangeZkNodePath, sendJson.getBytes("UTF8"))
      } catch {
        case e: Exception => {
          logger.error("Failed to send update notification to engine.")
          throw e
        }
      }
    } else {
      logger.error("Failed to send update notification to engine.")
    }
  }

  // If we have keyfieldnames.size > 0
  private def buildContainerOrMessage(kvstore: DataStore): Unit = {
    if (!isOk)
      return

    // kvstore.TruncateStore

    var processedRows: Int = 0
    var errsCnt: Int = 0
    var transId: Long = 0

    logger.debug("KeyFields:" + keyfieldnames.mkString(","))

    // The value for this is Boolean & MessageContainerBase. Here Boolean represents it is changed in this transaction or loaded from previous file
    var dataByBucketKeyPart = new TreeMap[KeyWithBucketIdAndPrimaryKey, MessageContainerBaseWithModFlag](KvBaseDefalts.defualtBucketKeyComp) // By time, BucketKey, then PrimaryKey/{transactionid & rowid}. This is little cheaper if we are going to get exact match, because we compare time & then bucketid
    var loadedKeys = new java.util.TreeSet[LoadKeyWithBucketId](KvBaseDefalts.defaultLoadKeyComp) // By BucketId, BucketKey, Time Range

    var hasPrimaryKey = false
    var triedForPrimaryKey = false
    var transService: com.ligadata.transactions.SimpleTransService = null

    if (zkConnectString != null && zkNodeBasePath != null && zkConnectString.size > 0 && zkNodeBasePath.size > 0) {
      try {
        // TransactionId
        com.ligadata.transactions.NodeLevelTransService.init(zkConnectString, zkSessionTimeoutMs, zkConnectionTimeoutMs, zkNodeBasePath, 1, dataDataStoreInfo, KvInitConfiguration.jarPaths)
        transService = new com.ligadata.transactions.SimpleTransService
        transService.init(1)
        transId = transService.getNextTransId // Getting first transaction. It may get wasted if we don't have any lines to save.

        // ZK notifications
        val dataChangeZkNodePath = zkNodeBasePath + "/datachange"
        CreateClient.CreateNodeIfNotExists(zkConnectString, dataChangeZkNodePath) // Creating path if missing
        zkcForSetData = CreateClient.createSimple(zkConnectString, zkSessionTimeoutMs, zkConnectionTimeoutMs)
      } catch {
        case e: Exception => throw e
      }
    }

    dataFiles.foreach(fl => {
      logger.info("%s: File:%s => About to process".format(GetCurDtTmStr, fl))
      val alldata: List[String] = fileData(fl, format)
      logger.info("%s: File:%s => Lines in file:%d".format(GetCurDtTmStr, fl, alldata.size))

      var lnNo = 0

      alldata.foreach(inputStr => {
        lnNo += 1
        if (lnNo > ignoreRecords && inputStr.size > 0) {
          logger.debug("Record:" + inputStr)

          /** if we can make one ... we add the data to the store. This will crash if the data is bad */
          val inputData = prepareInputData(inputStr)

          if (inputData != null) {
            var messageOrContainer: MessageContainerBase = null

            if (isMsg) {
              messageOrContainer = messageObj.CreateNewMessage
            } else if (isContainer) {
              messageOrContainer = containerObj.CreateNewContainer
            } else { // This should not happen
              throw new Exception("Handling only message or container")
            }

            if (messageOrContainer != null) {
              try {
                messageOrContainer.TransactionId(transId)
                messageOrContainer.populate(inputData)
                if (triedForPrimaryKey == false) {
                  val primaryKey = messageOrContainer.PrimaryKeyData
                  hasPrimaryKey = primaryKey != null && primaryKey.size > 0 // Checking for the first record
                }
                triedForPrimaryKey = true
              } catch {
                case e: Exception => {
                  val stackTrace = StackTrace.ThrowableTraceString(e)
                  logger.debug("Failed to populate message/container." + "\nStackTrace:" + stackTrace)
                  errsCnt += 1
                }
              }
              try {
                // If we have external Partition Key, we are taking the key stuff from value, otherwise we are taking it from messageOrContainer.PartitionKeyData
                // BUGBUG:: For now we are using messageOrContainer.get and converting it to String. It may not always convert properly (if we have complex type etc). So, we need to get String for the given key from message/container itself.
                val keyData =
                  if (keyfieldnames.size > 0) keyfieldnames.map(key => {
                    var value = "" // Default in case of Exception or NULL value (Mainly for NULL value)
                    try {
                      val v = messageOrContainer.get(key.toLowerCase) //BUGBUG:: We need to remove this if we are going to handle case sensitive.
                      if (v != null) {
                        value = v.toString
                        logger.debug("Requested field:%s, got value:%s".format(key, value))
                      } else {
                        logger.debug("Requested field:%s, but got null back".format(key))
                      }
                    } catch {
                      case e: Exception => {
                        logger.error("Failed to get value for field:%s. Reason:%s, Message:%s\nStackTrace:%s".format(key, e.getCause, e.getMessage, StackTrace.ThrowableTraceString(e)))
                        //BUGBUG:: May be we can take empty string if we get any exception in get. which one is correct way?
                        throw e
                      }
                    }
                    value
                  })
                  else {
                    messageOrContainer.PartitionKeyData
                  }

                val timeVal = messageOrContainer.TimePartitionData
                messageOrContainer.RowNumber(processedRows)

                val bucketId = KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(keyData)
                val k = KeyWithBucketIdAndPrimaryKey(bucketId, Key(timeVal, keyData, transId, processedRows), hasPrimaryKey, if (hasPrimaryKey) messageOrContainer.PrimaryKeyData else null)
                if (hasPrimaryKey) {
                  // Get the record(s) for this partition key, time value & primary key
                  val loadKey = LoadKeyWithBucketId(bucketId, TimeRange(timeVal, timeVal), keyData)
                  LoadDataIfNeeded(loadKey, loadedKeys, dataByBucketKeyPart, kvstore)
                }

                dataByBucketKeyPart.put(k, MessageContainerBaseWithModFlag(true, messageOrContainer))
                processedRows += 1
                if (processedRows % commitBatchSize == 0) {
                  logger.info("%s: Collected batch (%d) of values. About to insert".format(GetCurDtTmStr, commitBatchSize))
                  // Get transaction and commit them
                  commitData(transId, kvstore, dataByBucketKeyPart, commitBatchSize, processedRows)
                  dataByBucketKeyPart.clear()
                  loadedKeys.clear()
                  // Getting new transactionid for next batch
                  transId = transService.getNextTransId
                  logger.info("%s: Inserted batch (%d) of values. Total processed so far:%d".format(GetCurDtTmStr, commitBatchSize, processedRows))

                }
              } catch {
                case e: Exception => {
                  val stackTrace = StackTrace.ThrowableTraceString(e)
                  logger.debug("Failed to serialize/write data." + "\nStackTrace:" + stackTrace)
                  errsCnt += 1
                }
              }
            }
          }

          if (errsCnt > ignoreErrsCount) {
            val errStr = "Populate/Serialize errors (%d) exceed the given count(%d)." format (errsCnt, ignoreErrsCount)
            logger.error(errStr)
            throw new Exception(errStr)
          }
        }
      })
    })

    if (dataByBucketKeyPart.size > 0) {
      commitData(transId, kvstore, dataByBucketKeyPart, commitBatchSize, processedRows)
      dataByBucketKeyPart.clear()
      loadedKeys.clear()
    }

    val msgStr = "%s: Processed %d records and Inserted %d keys total for Type %s".format(GetCurDtTmStr, processedRows, totalCommittedMsgs, typename)
    logger.info(msgStr)
    println(msgStr)
  }

  private def fileData(inputeventfile: String, format: String): List[String] = {
    var br: BufferedReader = null
    try {
      if (isCompressed(inputeventfile)) {
        br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(inputeventfile))))
      } else {
        br = new BufferedReader(new InputStreamReader(new FileInputStream(inputeventfile)))
      }
    } catch {
      case e: Exception => {
        logger.error("Failed to open Input File %s. Message:%s".format(inputeventfile, e.getMessage))
        throw e
      }
    }
    var fileContentsArray = ArrayBuffer[String]()

    try {
      if (isDelimited) {
        var line: String = ""
        while ({ line = br.readLine(); line != null }) {
          fileContentsArray += line
        }
      } else if (isJson) {
        var buf = new ArrayBuffer[Int]()
        var ch = br.read()

        while (ch != -1) {
          buf.clear() // Reset
          // Finding start "{" char
          var foundOpen = false
          while (ch != -1 && foundOpen == false) {
            if (ch == '{')
              foundOpen = true;
            else {
              buf += ch
              ch = br.read()
            }
          }

          if (buf.size > 0) {
            val str = new String(buf.toArray, 0, buf.size)
            if (str.trim.size > 0)
              logger.error("Found invalid string in JSON file, which is not json String:" + str)
          }

          buf.clear() // Reset

          while (foundOpen && ch != -1) {
            if (ch == '}') {
              // Try the string now
              buf += ch
              ch = br.read()

              val possibleFullJsonStr = new String(buf.toArray, 0, buf.size)
              try {
                implicit val jsonFormats: Formats = DefaultFormats
                val validJson = parse(possibleFullJsonStr)
                // If we find valid json, that means we will take this json
                if (validJson != null) {
                  fileContentsArray += possibleFullJsonStr
                  buf.clear() // Reset
                  foundOpen = false
                } // else // Did not match the correct json even if we have one open brace. Tring for the next open one
              } catch {
                case e: Exception => {} // Not yet valid json
              }
            } else {
              buf += ch
              ch = br.read()
            }
          }
        }

        if (buf.size > 0) {
          val str = new String(buf.toArray, 0, buf.size)
          if (str.trim.size > 0)
            logger.error("Found invalid string in JSON file, which is not json String:" + str)
        }
      } else {
        // Un-handled format
        logger.error("Got unhandled format :" + format)
        throw new Exception("Got unhandled format :" + format)
      }
    } catch {
      case e: Exception => {
        logger.error("Failed to open Input File %s. Message:%s".format(inputeventfile, e.getMessage))
        throw e
      }
    }

    br.close();

    return fileContentsArray.toList
  }

  private def isCompressed(inputfile: String): Boolean = {
    var is: FileInputStream = null
    try {
      is = new FileInputStream(inputfile)
    } catch {
      case e: Exception =>
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStacktrace:" + stackTrace)
        return false
    }

    val maxlen = 2
    val buffer = new Array[Byte](maxlen)
    val readlen = is.read(buffer, 0, maxlen)

    is.close() // Close before we really check and return the data

    if (readlen < 2)
      return false;

    val b0: Int = buffer(0)
    val b1: Int = buffer(1)

    val head = (b0 & 0xff) | ((b1 << 8) & 0xff00)

    return (head == GZIPInputStream.GZIP_MAGIC);
  }

  private val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

  private def SimpDateFmtTimeFromMs(tmMs: Long): String = {
    formatter.format(new java.util.Date(tmMs))
  }

  private def GetCurDtTmStr: String = {
    SimpDateFmtTimeFromMs(System.currentTimeMillis)
  }
}


