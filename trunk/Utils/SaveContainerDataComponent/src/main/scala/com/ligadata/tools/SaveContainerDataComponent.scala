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

package com.ligadata.tools

// import java.nio.charset.StandardCharsets
import java.io.File
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
import scala.collection.mutable.{ ArrayBuffer, TreeSet }
// import com.ligadata.ZooKeeper._
// import org.apache.curator.framework._
// import com.ligadata.Serialize.{ JZKInfo }
import com.ligadata.KvBase.{ Key, Value, TimeRange, KvBaseDefalts, KeyWithBucketIdAndPrimaryKey, KeyWithBucketIdAndPrimaryKeyCompHelper, LoadKeyWithBucketId }
import com.ligadata.StorageBase.{ DataStore, Transaction }
import com.ligadata.Exceptions.StackTrace
import java.util.{ Collection, Iterator, TreeMap }
import com.ligadata.Exceptions._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.ligadata.transactions.{ SimpleTransService, NodeLevelTransService }

trait LogTrait {
  val loggerName = this.getClass.getName()
  val logger = LogManager.getLogger(loggerName)
}

class SaveContainerDataCompImpl extends LogTrait with MdBaseResolveInfo {
  private var _configFile: String = null
  private var _nodeId: Int = 0
  private var _initialized = false
  private var _jarPaths = collection.immutable.Set[String]()
  private var _dataStore: DataStore = null
  private val _baseObjs = scala.collection.mutable.Map[String, MessageContainerObjBase]()
  private val _kamanjaLoader = new KamanjaLoaderInfo
  private var _transService: SimpleTransService = null

  private def LoadJarIfNeeded(elem: BaseElem, loadedJars: TreeSet[String], loader: KamanjaClassLoader): Unit = {
    var retVal: Boolean = true
    var allJars: Array[String] = null

    val jarname = if (elem.JarName == null) "" else elem.JarName.trim

    if (elem.DependencyJarNames != null && elem.DependencyJarNames.size > 0 && jarname.size > 0) {
      allJars = elem.DependencyJarNames :+ jarname
    } else if (elem.DependencyJarNames != null && elem.DependencyJarNames.size > 0) {
      allJars = elem.DependencyJarNames
    } else if (jarname.size > 0) {
      allJars = Array(jarname)
    }

    if (allJars.size > 0) {
      val jars = allJars.map(j => Utils.GetValidJarFile(_jarPaths, j))

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
              throw e
            }
            case e: Throwable => {
              logger.error("Jar " + j.trim + " failed added to class path. Message: " + e.getMessage)
              throw e
            }
          }
        } else {
          val errMsg = "Jar " + j.trim + " not found"
          logger.error(errMsg)
          throw new Exception(errMsg)
        }
      }
    }
  }

  private def GetDataStoreHandle(jarPaths: collection.immutable.Set[String], dataStoreInfo: String): DataStore = {
    try {
      logger.debug("Getting DB Connection for dataStoreInfo:%s".format(dataStoreInfo))
      return KeyValueManager.Get(jarPaths, dataStoreInfo)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Failed to connect Database:" + dataStoreInfo)
        logger.error("StackTrace:" + stackTrace)
        throw e
      }
      case e: Throwable => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Failed to connect Database:" + dataStoreInfo)
        logger.error("StackTrace:" + stackTrace)
        throw e
      }
    }
  }

  private def collectKeyAndValues(k: Key, v: Value, dataByBucketKeyPart: TreeMap[KeyWithBucketIdAndPrimaryKey, MessageContainerBaseWithModFlag], loadedKeys: java.util.TreeSet[LoadKeyWithBucketId]): Unit = {
    val value = SerializeDeserialize.Deserialize(v.serializedInfo, this, _kamanjaLoader.loader, true, "")
    val primarykey = value.PrimaryKeyData
    val key = KeyWithBucketIdAndPrimaryKey(KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(k.bucketKey), k, primarykey != null && primarykey.size > 0, primarykey)
    dataByBucketKeyPart.put(key, MessageContainerBaseWithModFlag(false, value))

    val bucketId = KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(k.bucketKey)
    val loadKey = LoadKeyWithBucketId(bucketId, TimeRange(k.timePartition, k.timePartition), k.bucketKey)
    loadedKeys.add(loadKey)
  }

  private def LoadDataIfNeeded(typ: String, loadKey: LoadKeyWithBucketId, loadedKeys: java.util.TreeSet[LoadKeyWithBucketId], dataByBucketKeyPart: TreeMap[KeyWithBucketIdAndPrimaryKey, MessageContainerBaseWithModFlag]): Unit = {
    if (loadedKeys.contains(loadKey))
      return
    val buildOne = (k: Key, v: Value) => {
      collectKeyAndValues(k, v, dataByBucketKeyPart, loadedKeys)
    }
    try {
      _dataStore.get(typ, Array(loadKey.tmRange), Array(loadKey.bucketKey), buildOne)
      loadedKeys.add(loadKey)
    } catch {
      case e: ObjectNotFoundException => {
        logger.debug("Key %s Not found for timerange: %d-%d".format(loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime))
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Key %s Not found for timerange: %d-%d.\nStackTrace:%s".format(loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime, stackTrace))
      }
    }
  }

  override def getMessgeOrContainerInstance(MsgContainerType: String): MessageContainerBase = {
    try {
      return GetMessageContainerBase(MsgContainerType)
    } catch {
      case e: Exception => {}
      case e: Throwable => {}
    }
    return null
  }

  @throws(classOf[Exception])
  def Init(cfgfile: String): Unit = {
    if (cfgfile == null) {
      logger.error("Invalid configuration file")
      throw new Exception("Invalid configuration file")
    }

    val (loadConfigs, failStr) = Utils.loadConfiguration(cfgfile, true)
    if (failStr != null && failStr.size > 0) {
      logger.error(failStr)
      throw new Exception(failStr)
    }
    if (loadConfigs == null) {
      val str = "Failed to load configurations from configuration file:" + cfgfile
      logger.error(str)
      throw new Exception(str)
    }

    val nodeId = loadConfigs.getProperty("nodeid".toLowerCase, "0").replace("\"", "").trim.toInt
    if (nodeId <= 0) {
      logger.error("Not found valid nodeId. It should be greater than 0")
      throw new Exception("Not found valid nodeId. It should be greater than 0")
    }

    // Metadata Init
    MetadataAPIImpl.InitMdMgrFromBootStrap(cfgfile, false)

    val nodeInfo = mdMgr.Nodes.getOrElse(nodeId.toString, null)
    if (nodeInfo == null) {
      val msgStr = "Node %d not found in metadata".format(nodeId)
      logger.error(msgStr)
      throw new Exception(msgStr)
    }

    val jps = if (nodeInfo.JarPaths == null) Array[String]().toSet else nodeInfo.JarPaths.map(str => str.replace("\"", "").trim).filter(str => str.size > 0).toSet
    if (jps == 0) {
      val msgStr = "Not found valid JarPaths for nodeid:" + nodeId
      logger.error(msgStr)
      throw new Exception(msgStr)
    }

    val jarPaths = jps ++ _jarPaths

    val cluster = mdMgr.ClusterCfgs.getOrElse(nodeInfo.ClusterId, null)
    if (cluster == null) {
      val msgStr = "Cluster not found for Node %d  & ClusterId : %s".format(nodeId, nodeInfo.ClusterId)
      logger.error(msgStr)
      throw new Exception(msgStr)
    }

    val dataStore = cluster.cfgMap.getOrElse("DataStore", null)
    if (dataStore == null) {
      val msgStr = "DataStore not found for Node %d  & ClusterId : %s".format(nodeId, nodeInfo.ClusterId)
      logger.error(msgStr)
      throw new Exception(msgStr)
    }

    val zooKeeperInfo = cluster.cfgMap.getOrElse("ZooKeeperInfo", null)
    if (dataStore == null) {
      val msgStr = "ZooKeeperInfo not found for Node %d  & ClusterId : %s".format(nodeId, nodeInfo.ClusterId)
      logger.error(msgStr)
      throw new Exception(msgStr)
    }

    implicit val jsonFormats: Formats = DefaultFormats
    val zKInfo = parse(zooKeeperInfo).extract[JZKInfo]

    var zkConnectString = zKInfo.ZooKeeperConnectString.replace("\"", "").trim
    var zkNodeBasePath = zKInfo.ZooKeeperNodeBasePath.replace("\"", "").trim
    var zkSessionTimeoutMs = if (zKInfo.ZooKeeperSessionTimeoutMs == None || zKInfo.ZooKeeperSessionTimeoutMs == null) 0 else zKInfo.ZooKeeperSessionTimeoutMs.get.toString.toInt
    var zkConnectionTimeoutMs = if (zKInfo.ZooKeeperConnectionTimeoutMs == None || zKInfo.ZooKeeperConnectionTimeoutMs == null) 0 else zKInfo.ZooKeeperConnectionTimeoutMs.get.toString.toInt

    // Taking minimum values in case if needed
    if (zkSessionTimeoutMs <= 0)
      zkSessionTimeoutMs = 30000
    if (zkConnectionTimeoutMs <= 0)
      zkConnectionTimeoutMs = 30000

    if (zkConnectString.size == 0) {
      logger.error("Not found valid Zookeeper connection string.")
      throw new Exception("Not found valid Zookeeper connection string.")
    }

    if (zkConnectString.size > 0 && zkNodeBasePath.size == 0) {
      logger.error("Not found valid Zookeeper ZNode Base Path.")
      throw new Exception("Not found valid Zookeeper ZNode Base Path.")
    }

    // Init Transaction Service
    if (zkConnectString != null && zkNodeBasePath != null && zkConnectString.size > 0 && zkNodeBasePath.size > 0) {
      try {
        NodeLevelTransService.init(zkConnectString, zkSessionTimeoutMs, zkConnectionTimeoutMs, zkNodeBasePath, 1, dataStore, jarPaths)
        _transService = new SimpleTransService
        _transService.init(1)
      } catch {
        case e: Exception => {
          logger.error("Failed to start Transaction service.")
          throw e
        }
        case e: Throwable => {
          logger.error("Failed to start Transaction service.")
          throw e
        }
      }
    }

    // Datastore Init
    val dataStoreInst = GetDataStoreHandle(jarPaths, dataStore)

    _configFile = cfgfile
    _nodeId = nodeId
    _jarPaths = jarPaths
    _dataStore = dataStoreInst
    _initialized = true
  }

  @throws(classOf[Exception])
  def GetMessageContainerBase(typ: String): MessageContainerBase = {
    if (_initialized == false) {
      val msgStr = "SaveContainerDataComponent is not yet initialized"
      logger.error(msgStr)
      throw new Exception(msgStr)
    }

    if (typ == null) {
      val msgStr = "Not expecting NULL type"
      logger.error(msgStr)
      throw new Exception(msgStr)
    }

    val typeName = typ.toLowerCase
    val cachedObj = _baseObjs.getOrElse(typeName, null)

    // If we have cached obj, just create from it
    if (cachedObj != null) {
      if (cachedObj.isMessage)
        return cachedObj.asInstanceOf[BaseMsgObj].CreateNewMessage
      return cachedObj.asInstanceOf[BaseContainerObj].CreateNewContainer
    }

    val typeNameCorrType = mdMgr.ActiveType(typeName)
    if (typeNameCorrType == null || typeNameCorrType == None) {
      val msgStr = "Not found valid type for " + typ
      logger.error(msgStr)
      throw new Exception(msgStr)
    }

    LoadJarIfNeeded(typeNameCorrType, _kamanjaLoader.loadedJars, _kamanjaLoader.loader)

    var isMsg = false
    var isContainer = false

    // BUGBUG:: For now we are checking for the classname ending with $. Need to fix when we go from Scala Objects to Java static classes
    var clsName = typeNameCorrType.PhysicalName.trim
    if (clsName.size > 0 && clsName.charAt(clsName.size - 1) != '$') // if no $ at the end we are taking $
      clsName = clsName + "$"

    if (isMsg == false) {
      // Checking for Message
      try {
        // Convert class name into a class
        var curClz = Class.forName(clsName, true, _kamanjaLoader.loader)

        while (curClz != null && isContainer == false) {
          isContainer = Utils.isDerivedFrom(curClz, "com.ligadata.KamanjaBase.BaseContainerObj")
          if (isContainer == false)
            curClz = curClz.getSuperclass()
        }
      } catch {
        case e: Exception => {
          logger.error("Failed to load message type:%s (class:%s) with Reason:%s Message:%s".format(typ, clsName, e.getCause, e.getMessage))
          throw e
        }
        case e: Throwable => {
          logger.error("Failed to load message type:%s (class:%s) with Reason:%s Message:%s".format(typ, clsName, e.getCause, e.getMessage))
          throw e
        }
      }
    }

    if (isContainer == false) {
      // Checking for container
      try {
        // If required we need to enable this test
        // Convert class name into a class
        var curClz = Class.forName(clsName, true, _kamanjaLoader.loader)

        while (curClz != null && isMsg == false) {
          isMsg = Utils.isDerivedFrom(curClz, "com.ligadata.KamanjaBase.BaseMsgObj")
          if (isMsg == false)
            curClz = curClz.getSuperclass()
        }
      } catch {
        case e: Exception => {
          logger.error("Failed to load container. type:%s (class:%s) with Reason:%s Message:%s".format(typ, clsName, e.getCause, e.getMessage))
          throw e
        }
        case e: Throwable => {
          logger.error("Failed to load container. type:%s (class:%s) with Reason:%s Message:%s".format(typ, clsName, e.getCause, e.getMessage))
          throw e
        }
      }
    }

    if (isMsg || isContainer) {
      try {
        val module = _kamanjaLoader.mirror.staticModule(clsName)
        val obj = _kamanjaLoader.mirror.reflectModule(module)
        val objinst = obj.instance
        if (objinst.isInstanceOf[BaseMsgObj]) {
          val messageObj = objinst.asInstanceOf[BaseMsgObj]
          logger.debug("Created Message Object for type:%s (class:%s)".format(typ, clsName))
          _baseObjs(typeName) = messageObj
          return messageObj.CreateNewMessage
        } else if (objinst.isInstanceOf[BaseContainerObj]) {
          val containerObj = objinst.asInstanceOf[BaseContainerObj]
          logger.debug("Created Container Object for type:%s (class:%s)".format(typ, clsName))
          _baseObjs(typeName) = containerObj
          return containerObj.CreateNewContainer
        } else {
          val msgStr = "Failed to instantiate message or conatiner. type:%s (class:%s)".format(typ, clsName)
          logger.error(msgStr)
          throw new Exception(msgStr)
        }
      } catch {
        case e: Exception => {
          logger.error("Failed to instantiate message or conatiner. type::" + typ + " (class:" + clsName + "). Reason:" + e.getCause + ". Message:" + e.getMessage())
          throw e
        }
        case e: Throwable => {
          logger.error("Failed to instantiate message or conatiner. type::" + typ + " (class:" + clsName + "). Reason:" + e.getCause + ". Message:" + e.getMessage())
          throw e
        }
      }
    }

    val msgStr = "Failed to find message or conatiner. type:%s (class:%s) isMsg:%s, isContainer:%s".format(typ, clsName, isMsg.toString(), isContainer.toString())
    logger.error(msgStr)
    throw new Exception(msgStr)
  }

  @throws(classOf[Exception])
  def GetNewTransactionId(): Long = {
    if (_initialized == false) {
      val msgStr = "SaveContainerDataComponent is not yet initialized"
      logger.error(msgStr)
      throw new Exception(msgStr)
    }
    _transService.getNextTransId
  }

  @throws(classOf[Exception])
  def SaveMessageContainerBase(typ: String, data: Array[MessageContainerBase], setNewTransactionId: Boolean, setNewRowNumber: Boolean): Unit = {
    if (_initialized == false) {
      val msgStr = "SaveContainerDataComponent is not yet initialized"
      logger.error(msgStr)
      throw new Exception(msgStr)
    }

    if (data == null || data.size == 0)
      return

    if (typ == null) {
      val msgStr = "Not expecting NULL type"
      logger.error(msgStr)
      throw new Exception(msgStr)
    }

    var transId: Long = 0

    if (setNewTransactionId)
      transId = GetNewTransactionId

    var rowNumber = 0

    val storeObjects = data.map(d => {
      if (setNewRowNumber) {
        rowNumber += 1
        d.RowNumber(rowNumber)
      }

      if (setNewTransactionId)
        d.TransactionId(transId)

      val keyData = d.PartitionKeyData
      val timeVal = d.TimePartitionData
      val k = Key(timeVal, keyData, d.TransactionId, d.RowNumber)
      val v = Value("manual", SerializeDeserialize.Serialize(d))
      (k, v)
    })

    try {
      logger.debug("Going to save " + storeObjects.size + " objects")
      storeObjects.foreach(kv => {
        logger.debug("ObjKey:(" + kv._1.timePartition + ":" + kv._1.bucketKey.mkString(",") + ":" + kv._1.transactionId + ") Value Size: " + kv._2.serializedInfo.size)
      })
      _dataStore.put(Array((typ.toLowerCase, storeObjects)))
    } catch {
      case e: Exception => {
        logger.error("Failed to write data for type:" + typ)
        throw e
      }
      case e: Throwable => {
        logger.error("Failed to write data for type:" + typ)
        throw e
      }
    }
  }

  def Shutdown: Unit = {
    if (_initialized) {
      _dataStore.Shutdown()
      NodeLevelTransService.Shutdown
      MetadataAPIImpl.CloseDbStore
    }

    _dataStore = null
    _configFile = null
    _nodeId = 0
    _initialized = false
    _jarPaths = collection.immutable.Set[String]()
    _dataStore = null
    _baseObjs.clear()
    _transService = null
  }
}

class SaveContainerDataComponent {
  private val impl = new SaveContainerDataCompImpl

  /* Initialize MetadataManager, DataStore & Transaction Service */
  @throws(classOf[Exception])
  def Init(cfgfile: String): Unit = {
    impl.Init(cfgfile)
  }

  /* Get New Message/Container data Instances for the given Message/Container */
  @throws(classOf[Exception])
  def GetMessageContainerBase(typ: String): MessageContainerBase = {
    impl.GetMessageContainerBase(typ)
  }

  /* Get New TransactionId */
  @throws(classOf[Exception])
  def GetNewTransactionId(): Long = {
    impl.GetNewTransactionId
  }

  /* Save given Message/Container data Instances for the given container name. Caller can request to set new transactionid (so that he does not need to set it) and new rownumber. */
  @throws(classOf[Exception])
  def SaveMessageContainerBase(typ: String, data: Array[MessageContainerBase], setNewTransactionId: Boolean, setNewRowNumber: Boolean): Unit = {
    impl.SaveMessageContainerBase(typ, data, setNewTransactionId, setNewRowNumber)
  }

  /* Shutdown services and reset everything */
  def Shutdown: Unit = {
    impl.Shutdown
  }
}

