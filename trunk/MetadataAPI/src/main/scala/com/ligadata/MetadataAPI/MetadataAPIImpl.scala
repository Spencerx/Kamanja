package com.ligadata.MetadataAPI

import java.util.Properties
import java.io._
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.text.ParseException
import scala.Enumeration
import scala.io._
import scala.collection.mutable.ArrayBuffer

import scala.collection.mutable._
import scala.reflect.runtime.{ universe => ru }

import com.ligadata.kamanja.metadata.ObjType._
import com.ligadata.kamanja.metadata._
import com.ligadata.kamanja.metadata.MdMgr._

import com.ligadata.kamanja.metadataload.MetadataLoad

import com.ligadata.StorageBase.{ DataStore, Transaction, IStorage, Key, Value, StorageAdapterObj }

import scala.util.parsing.json.JSON
import scala.util.parsing.json.{ JSONObject, JSONArray }
import scala.collection.immutable.Map
import scala.collection.immutable.HashMap
import scala.collection.mutable.HashMap

import com.ligadata.messagedef._
import com.ligadata.Exceptions._

import scala.xml.XML
import org.apache.log4j._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import com.ligadata.ZooKeeper._
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.CreateMode

import com.ligadata.Serialize._
import com.ligadata.Utils._
import util.control.Breaks._
import com.ligadata.AuditAdapterInfo._
import com.ligadata.SecurityAdapterInfo.SecurityAdapter
import com.ligadata.keyvaluestore.KeyValueManager

import java.util.Date

import org.json4s.jackson.Serialization

case class ParameterMap(RootDir: String, GitRootDir: String, MetadataStoreType: String, MetadataSchemaName: Option[String], /* MetadataAdapterSpecificConfig: Option[String], */ MetadataLocation: String, JarTargetDir: String, ScalaHome: String, JavaHome: String, ManifestPath: String, ClassPath: String, NotifyEngine: String, ZnodePath: String, ZooKeeperConnectString: String, MODEL_FILES_DIR: Option[String], TYPE_FILES_DIR: Option[String], FUNCTION_FILES_DIR: Option[String], CONCEPT_FILES_DIR: Option[String], MESSAGE_FILES_DIR: Option[String], CONTAINER_FILES_DIR: Option[String], COMPILER_WORK_DIR: Option[String], MODEL_EXEC_FLAG: Option[String], OUTPUTMESSAGE_FILES_DIR: Option[String])
case class TypeDef(MetadataType: String, NameSpace: String, Name: String, TypeTypeName: String, TypeNameSpace: String, TypeName: String, PhysicalName: String, var Version: String, JarName: String, DependencyJars: List[String], Implementation: String, Fixed: Option[Boolean], NumberOfDimensions: Option[Int], KeyTypeNameSpace: Option[String], KeyTypeName: Option[String], ValueTypeNameSpace: Option[String], ValueTypeName: Option[String], TupleDefinitions: Option[List[TypeDef]])
case class TypeDefList(Types: List[TypeDef])

case class Argument(ArgName: String, ArgTypeNameSpace: String, ArgTypeName: String)
case class Function(NameSpace: String, Name: String, PhysicalName: String, ReturnTypeNameSpace: String, ReturnTypeName: String, Arguments: List[Argument], Version: String, JarName: String, DependantJars: List[String])
case class FunctionList(Functions: List[Function])

//case class Concept(NameSpace: String,Name: String, TypeNameSpace: String, TypeName: String,Version: String,Description: String, Author: String, ActiveDate: String)
case class Concept(NameSpace: String, Name: String, TypeNameSpace: String, TypeName: String, Version: String)
case class ConceptList(Concepts: List[Concept])

// case class Attr(NameSpace: String, Name: String, Version: Long, Type: TypeDef)

// case class MessageStruct(NameSpace: String, Name: String, FullName: String, Version: Long, JarName: String, PhysicalName: String, DependencyJars: List[String], Attributes: List[Attr])
case class MessageDefinition(Message: MessageStruct)
case class ContainerDefinition(Container: MessageStruct)

case class ModelInfo(NameSpace: String, Name: String, Version: String, ModelType: String, JarName: String, PhysicalName: String, DependencyJars: List[String], InputAttributes: List[Attr], OutputAttributes: List[Attr])
case class ModelDefinition(Model: ModelInfo)

case class ZooKeeperInfo(ZooKeeperNodeBasePath: String, ZooKeeperConnectString: String, ZooKeeperSessionTimeoutMs: Option[String], ZooKeeperConnectionTimeoutMs: Option[String])

case class MetadataAPIConfig(APIConfigParameters: ParameterMap)

case class APIResultInfo(statusCode: Int, functionName: String, resultData: String, description: String)
case class APIResultJsonProxy(APIResults: APIResultInfo)

// The implementation class
object MetadataAPIImpl extends MetadataAPI {

  lazy val sysNS = "System" // system name space
  lazy val loggerName = this.getClass.getName
  lazy val logger = Logger.getLogger(loggerName)
  lazy val serializer = SerializerManager.GetSerializer("kryo")
  lazy val metadataAPIConfig = new Properties()
  var zkc: CuratorFramework = null
  private var authObj: SecurityAdapter = null
  private var auditObj: AuditAdapter = null
  val configFile = System.getenv("HOME") + "/MetadataAPIConfig.json"
  var propertiesAlreadyLoaded = false
  var isInitilized: Boolean = false
  private var zkListener: ZooKeeperListener = _
  private var cacheOfOwnChanges: scala.collection.mutable.Set[String] = scala.collection.mutable.Set[String]()
  private var currentTranLevel: Long = _
  private var passwd: String = null
  private var compileCfg: String = ""

  // For future debugging  purposes, we want to know which properties were not set - so create a set
  // of values that can be set via our config files
  var pList: Set[String] = Set("ZK_SESSION_TIMEOUT_MS", "ZK_CONNECTION_TIMEOUT_MS", "DATABASE_SCHEMA", "DATABASE", "DATABASE_LOCATION", "DATABASE_HOST", "API_LEADER_SELECTION_ZK_NODE",
    "JAR_PATHS", "JAR_TARGET_DIR", "ROOT_DIR", "GIT_ROOT", "SCALA_HOME", "JAVA_HOME", "MANIFEST_PATH", "CLASSPATH", "NOTIFY_ENGINE", "SERVICE_HOST",
    "ZNODE_PATH", "ZOOKEEPER_CONNECT_STRING", "COMPILER_WORK_DIR", "SERVICE_PORT", "MODEL_FILES_DIR", "TYPE_FILES_DIR", "FUNCTION_FILES_DIR",
    "CONCEPT_FILES_DIR", "MESSAGE_FILES_DIR", "CONTAINER_FILES_DIR", "CONFIG_FILES_DIR", "MODEL_EXEC_LOG", "NODE_ID", "SSL_CERTIFICATE", "SSL_PASSWD", "DO_AUTH", "SECURITY_IMPL_CLASS",
    "SECURITY_IMPL_JAR", "AUDIT_IMPL_CLASS", "AUDIT_IMPL_JAR", "DO_AUDIT", "AUDIT_PARMS", "ADAPTER_SPECIFIC_CONFIG", "METADATA_DATASTORE")
    
  // This is used to exclude all non-engine related configs from Uplodad Config method 
  private val excludeList: Set[String] = Set[String]("ClusterId","StatusInfo","Nodes","Config","Adapters","DataStore","ZooKeeperInfo","EnvironmentContext") 
  
  var isCassandra = false
  private[this] val lock = new Object
  var startup = false

  private var tableStoreMap: Map[String, DataStore] = Map()

  def CloseZKSession: Unit = lock.synchronized {
    if (zkc != null) {
      logger.debug("Closing zookeeper session ..")
      try {
        zkc.close()
        logger.debug("Closed zookeeper session ..")
        zkc = null
      } catch {
        case e: Exception => {
          logger.error("Unexpected Error while closing zookeeper session: " + e.getMessage())
        }
      }
    }
  }

  /**
   * InitSecImpl  - 1. Create the Security Adapter class.  The class name and jar name containing
   *                the implementation of that class are specified in the CONFIG FILE.
   *                2. Create the Audit Adapter class, The class name and jar name containing
   *                the implementation of that class are specified in the CONFIG FILE.
   */
  def InitSecImpl: Unit = {
    logger.debug("Establishing connection to domain security server..")
    val classLoader = new KamanjaLoaderInfo

    // Validate the Auth/Audit flags for valid input.
    if ((metadataAPIConfig.getProperty("DO_AUTH") != null) &&
      (!metadataAPIConfig.getProperty("DO_AUTH").equalsIgnoreCase("YES") &&
        !metadataAPIConfig.getProperty("DO_AUTH").equalsIgnoreCase("NO"))) {
      throw new Exception("Invalid value for DO_AUTH detected.  Correct it and restart")
    }
    if ((metadataAPIConfig.getProperty("DO_AUDIT") != null) &&
      (!metadataAPIConfig.getProperty("DO_AUDIT").equalsIgnoreCase("YES") &&
        !metadataAPIConfig.getProperty("DO_AUDIT").equalsIgnoreCase("NO"))) {
      throw new Exception("Invalid value for DO_AUDIT detected.  Correct it and restart")
    }

    // If already have one, use that!
    if (authObj == null && (metadataAPIConfig.getProperty("DO_AUTH") != null) && (metadataAPIConfig.getProperty("DO_AUTH").equalsIgnoreCase("YES"))) {
      createAuthObj(classLoader)
    }
    if (auditObj == null && (metadataAPIConfig.getProperty("DO_AUDIT") != null) && (metadataAPIConfig.getProperty("DO_AUDIT").equalsIgnoreCase("YES"))) {
      createAuditObj(classLoader)
    }
  }

  /**
   * private method to instantiate an authObj
   */
  private def createAuthObj(classLoader: KamanjaLoaderInfo): Unit = {
    // Load the location and name of the implementing class from the
    val implJarName = if (metadataAPIConfig.getProperty("SECURITY_IMPL_JAR") == null) "" else metadataAPIConfig.getProperty("SECURITY_IMPL_JAR").trim
    val implClassName = if (metadataAPIConfig.getProperty("SECURITY_IMPL_CLASS") == null) "" else metadataAPIConfig.getProperty("SECURITY_IMPL_CLASS").trim
    logger.debug("Using " + implClassName + ", from the " + implJarName + " jar file")
    if (implClassName == null) {
      logger.error("Security Adapter Class is not specified")
      return
    }

    // Add the Jarfile to the class loader
    loadJar(classLoader, implJarName)

    // All is good, create the new class
    var className = Class.forName(implClassName, true, classLoader.loader).asInstanceOf[Class[SecurityAdapter]]
    authObj = className.newInstance
    authObj.init
    logger.debug("Created class " + className.getName)
  }

  /**
   * private method to instantiate an authObj
   */
  private def createAuditObj(classLoader: KamanjaLoaderInfo): Unit = {
    // Load the location and name of the implementing class froms the
    val implJarName = if (metadataAPIConfig.getProperty("AUDIT_IMPL_JAR") == null) "" else metadataAPIConfig.getProperty("AUDIT_IMPL_JAR").trim
    val implClassName = if (metadataAPIConfig.getProperty("AUDIT_IMPL_CLASS") == null) "" else metadataAPIConfig.getProperty("AUDIT_IMPL_CLASS").trim
    logger.debug("Using " + implClassName + ", from the " + implJarName + " jar file")
    if (implClassName == null) {
      logger.error("Audit Adapter Class is not specified")
      return
    }

    // Add the Jarfile to the class loader
    loadJar(classLoader, implJarName)

    // All is good, create the new class
    var className = Class.forName(implClassName, true, classLoader.loader).asInstanceOf[Class[AuditAdapter]]
    auditObj = className.newInstance
    auditObj.init(metadataAPIConfig.getProperty("AUDIT_PARMS"))
    logger.debug("Created class " + className.getName)
  }

  /**
   * loadJar - load the specified jar into the classLoader
   */
  private def loadJar(classLoader: KamanjaLoaderInfo, implJarName: String): Unit = {
    // Add the Jarfile to the class loader
    val tmpJarPaths = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS")
    val jarPaths = if (tmpJarPaths != null) tmpJarPaths.split(",").toSet else scala.collection.immutable.Set[String]()
    val jarName = Utils.GetValidJarFile(jarPaths, implJarName)
    val fl = new File(jarName)
    if (fl.exists) {
      try {
        classLoader.loader.addURL(fl.toURI().toURL())
        logger.debug("Jar " + implJarName.trim + " added to class path.")
        classLoader.loadedJars += fl.getPath()
      } catch {
        case e: Exception => {
          logger.error("Failed to add " + implJarName + " due to internal exception " + e.printStackTrace)
          return
        }
      }
    } else {
      logger.error("Unable to locate Jar '" + implJarName + "'")
      return
    }
  }

  /**
   * checkAuth
   */
  def checkAuth(usrid: Option[String], password: Option[String], role: Option[String], privilige: String): Boolean = {

    var authParms: java.util.Properties = new Properties
    // Do we want to run AUTH?
    if ((metadataAPIConfig.getProperty("DO_AUTH") == null) ||
      (metadataAPIConfig.getProperty("DO_AUTH") != null && !metadataAPIConfig.getProperty("DO_AUTH").equalsIgnoreCase("YES"))) {
      return true
    }

    // check if the Auth object exists
    if (authObj == null) return false

    // Run the Auth - if userId is supplied, defer to that.
    if ((usrid == None) && (role == None)) return false

    if (usrid != None) authParms.setProperty("userid", usrid.get.asInstanceOf[String])
    if (password != None) authParms.setProperty("password", password.get.asInstanceOf[String])
    if (role != None) authParms.setProperty("role", role.get.asInstanceOf[String])
    authParms.setProperty("privilige", privilige)

    return authObj.performAuth(authParms)
  }

  /**
   * getPrivilegeName
   */
  def getPrivilegeName(op: String, objName: String): String = {
    // check if the Auth object exists
    logger.debug("op => " + op)
    logger.debug("objName => " + objName)
    if (authObj == null) return ""
    return authObj.getPrivilegeName(op, objName)
  }

  /**
   * getSSLCertificatePath
   */
  def getSSLCertificatePath: String = {
    val certPath = metadataAPIConfig.getProperty("SSL_CERTIFICATE")
    if (certPath != null) return certPath
    ""
  }

  /**
   * getSSLCertificatePasswd
   */
  def getSSLCertificatePasswd: String = {
    if (passwd != null) return passwd
    ""
  }

  /**
   * setSSLCertificatePasswd
   */
  def setSSLCertificatePasswd(pw: String) = {
    passwd = pw
  }

  /**
   * getCurrentTime - Return string representation of the current Date/Time
   */
  def getCurrentTime: String = {
    new Date().getTime().toString()
  }

  /**
   * logAuditRec - Record an Audit event using the audit adapter.
   */
  def logAuditRec(userOrRole: Option[String], userPrivilege: Option[String], action: String, objectText: String, success: String, transactionId: String, objName: String) = {
    if (auditObj != null) {
      val aRec = new AuditRecord

      // If no userName is provided here, that means that somehow we are not running with Security but with Audit ON.
      var userName = "undefined"
      if (userOrRole != None) {
        userName = userOrRole.get
      }

      // If no priv is provided here, that means that somehow we are not running with Security but with Audit ON.
      var priv = "undefined"
      if (userPrivilege != None) {
        priv = userPrivilege.get
      }

      aRec.userOrRole = userName
      aRec.userPrivilege = priv
      aRec.actionTime = getCurrentTime
      aRec.action = action
      aRec.objectAccessed = objName
      aRec.success = success
      aRec.transactionId = transactionId
      aRec.notes = objectText
      try {
        auditObj.addAuditRecord(aRec)
      } catch {
        case e: Exception => {
          throw new UpdateStoreFailedException("Failed to save audit record" + aRec.toString + ":" + e.getMessage())
        }
      }
    }
  }

  /**
   * getAuditRec - Get an audit record from the audit adapter.
   */
  def getAuditRec(startTime: Date, endTime: Date, userOrRole: String, action: String, objectAccessed: String): String = {
    var apiResultStr = ""
    if (auditObj == null) {
      apiResultStr = "no audit records found "
      return apiResultStr
    }
    try {
      val recs = auditObj.getAuditRecord(startTime, endTime, userOrRole, action, objectAccessed)
      if (recs.length > 0) {
        apiResultStr = JsonSerializer.SerializeAuditRecordsToJson(recs)
      } else {
        apiResultStr = "no audit records found "
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "Failed to fetch all the audit objects:", null, "Error :" + e.toString)
        apiResultStr = apiResult.toString()
      }
    }
    logger.debug(apiResultStr)
    apiResultStr
  }

  /**
   * parseDateStr
   */
  def parseDateStr(dateStr: String): Date = {
    try {
      val format = new java.text.SimpleDateFormat("yyyyMMddHHmmss")
      val d = format.parse(dateStr)
      d
    } catch {
      case e: ParseException => {
        val format = new java.text.SimpleDateFormat("yyyyMMdd")
        val d = format.parse(dateStr)
        d
      }
    }
  }

  def getLeaderHost(leaderNode: String): String = {
    val nodes = MdMgr.GetMdMgr.Nodes.values.toArray
    if (nodes.length == 0) {
      logger.debug("No Nodes found ")
      var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetLeaderHost", null, ErrorCodeConstants.Get_Leader_Host_Failed_Not_Available + " :" + leaderNode)
      apiResult.toString()
    } else {
      val nhosts = nodes.filter(n => n.nodeId == leaderNode)
      if (nhosts.length == 0) {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetLeaderHost", null, ErrorCodeConstants.Get_Leader_Host_Failed_Not_Available + " :" + leaderNode)
        apiResult.toString()
      } else {
        val nhost = nhosts(0)
        logger.debug("node addr => " + nhost.NodeAddr)
        nhost.NodeAddr
      }
    }
  }

  def getAuditRec(filterParameters: Array[String]): String = {
    var apiResultStr = ""
    if (auditObj == null) {
      apiResultStr = "no audit records found "
      return apiResultStr
    }
    try {
      var audit_interval = 10
      var ai = metadataAPIConfig.getProperty("AUDIT_INTERVAL")
      if (ai != null) {
        audit_interval = ai.toInt
      }
      var startTime: Date = new Date((new Date).getTime() - audit_interval * 60000)
      var endTime: Date = new Date()
      var userOrRole: String = null
      var action: String = null
      var objectAccessed: String = null

      if (filterParameters != null) {
        val paramCnt = filterParameters.size
        paramCnt match {
          case 1 => {
            startTime = parseDateStr(filterParameters(0))
          }
          case 2 => {
            startTime = parseDateStr(filterParameters(0))
            endTime = parseDateStr(filterParameters(1))
          }
          case 3 => {
            startTime = parseDateStr(filterParameters(0))
            endTime = parseDateStr(filterParameters(1))
            userOrRole = filterParameters(2)
          }
          case 4 => {
            startTime = parseDateStr(filterParameters(0))
            endTime = parseDateStr(filterParameters(1))
            userOrRole = filterParameters(2)
            action = filterParameters(3)
          }
          case 5 => {
            startTime = parseDateStr(filterParameters(0))
            endTime = parseDateStr(filterParameters(1))
            userOrRole = filterParameters(2)
            action = filterParameters(3)
            objectAccessed = filterParameters(4)
          }
        }
      } else {
        logger.debug("filterParameters is null")
      }
      apiResultStr = getAuditRec(startTime, endTime, userOrRole, action, objectAccessed)
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "Failed to fetch all the audit objects:", null, "Error :" + e.toString)
        apiResultStr = apiResult.toString()
      }
    }
    logger.debug(apiResultStr)
    apiResultStr
  }

  /**
   * InitZooKeeper - Establish a connection to zookeeper
   */
  def InitZooKeeper: Unit = {
    logger.debug("Connect to zookeeper..")
    if (zkc != null) {
      // Zookeeper is already connected
      return
    }

    val zkcConnectString = GetMetadataAPIConfig.getProperty("ZOOKEEPER_CONNECT_STRING")
    val znodePath = GetMetadataAPIConfig.getProperty("ZNODE_PATH") + "/metadataupdate"
    logger.debug("Connect To ZooKeeper using " + zkcConnectString)
    try {
      CreateClient.CreateNodeIfNotExists(zkcConnectString, znodePath)
      zkc = CreateClient.createSimple(zkcConnectString)
    } catch {
      case e: Exception => {
        throw new InternalErrorException("Failed to start a zookeeper session with(" + zkcConnectString + "): " + e.getMessage())
      }
    }
  }

  private def shutdownAuditAdapter(): Unit = {
    if (auditObj != null) auditObj.Shutdown
  }

  def GetMetadataAPIConfig: Properties = {
    metadataAPIConfig
  }

  def SetLoggerLevel(level: Level) {
    logger.setLevel(level);
  }

  private var metadataStore: DataStore = _
  private var transStore: DataStore = _
  private var modelStore: DataStore = _
  private var messageStore: DataStore = _
  private var containerStore: DataStore = _
  private var functionStore: DataStore = _
  private var conceptStore: DataStore = _
  private var typeStore: DataStore = _
  private var otherStore: DataStore = _
  private var jarStore: DataStore = _
  private var configStore: DataStore = _
  private var outputmsgStore: DataStore = _
  private var modelConfigStore: DataStore = _
  private var userPopertiesStore: DataStore = _
  
  
   def oStore = otherStore

  def KeyAsStr(k: Key): String = {
    val k1 = k.toArray[Byte]
    new String(k1)
  }

  def ValueAsStr(v: Value): String = {
    val v1 = v.toArray[Byte]
    new String(v1)
  }

  def GetObject(key: Key, store: DataStore): IStorage = {
    try {
      object o extends IStorage {
        var key = new Key;
        var value = new Value

        def Key = key
        def Value = value
        def Construct(k: Key, v: Value) =
          {
            key = k;
            value = v;
          }
      }

      var k = key
      logger.debug("Get the object from store, key => " + KeyAsStr(k))
      store.get(k, o)
      o
    } catch {
      case e: KeyNotFoundException => {
        logger.debug("KeyNotFound Exception: Error => " + e.getMessage())
        throw new ObjectNotFoundException(e.getMessage())
      }
      case e: Exception => {
        e.printStackTrace()
        logger.debug("General Exception: Error => " + e.getMessage())
        throw new ObjectNotFoundException(e.getMessage())
      }
    }
  }

  def GetObject(key: String, store: DataStore): IStorage = {
    try {
      var k = new Key
      for (c <- key) {
        k += c.toByte
      }
      GetObject(k, store)
    }
  }

  def SaveObject(key: String, value: Array[Byte], store: DataStore) {
    val t = store.beginTx
    object obj extends IStorage {
      var k = new Key
      var v = new Value
      for (c <- key) {
        k += c.toByte
      }
      for (c <- value) {
        v += c
      }
      def Key = k
      def Value = v
      def Construct(Key: Key, Value: Value) = {}
    }
    try {
      store.put(obj)
      store.commitTx(t)
    } catch {
      case e: Exception => {
        logger.error("Failed to insert/update object for : " + key + ", Reason:" + e.getCause + ", Message:" + e.getMessage)
        e.printStackTrace
        store.endTx(t)
        throw new UpdateStoreFailedException("Failed to insert/update object for : " + key)
      }
    }
  }

  def SaveObjectList(keyList: Array[String], valueList: Array[Array[Byte]], store: DataStore) {
    var i = 0
    val t = store.beginTx
    var storeObjects = new Array[IStorage](keyList.length)
    keyList.foreach(key => {
      var value = valueList(i)
      logger.debug("Writing Key:" + key)

      SaveObject(key, value, store)
      /*

      object obj extends IStorage {
        var k = new Key
        var v = new Value
        for (c <- key) {
          k += c.toByte
        }
        for (c <- value) {
          v += c
        }
        def Key = k
        def Value = v
        def Construct(Key: Key, Value: Value) = {}
      }
      storeObjects(i) = obj
*/

      i = i + 1
    })

    /*
    try {
      store.putBatch(storeObjects)
      store.commitTx(t)
    } catch {
      case e: Exception => {
        logger.error("Failed to insert/update object for : " + keyList.mkString(",") + ". Exception Message:" + e.getMessage + ". Reason:" + e.getCause)
        store.endTx(t)
        throw new UpdateStoreFailedException("Failed to insert/update object for : " + keyList.mkString(","))
      }
    }
*/

  }

  def RemoveObjectList(keyList: Array[String], store: DataStore) {
    var i = 0
    val t = store.beginTx
    val KeyList = new Array[Key](keyList.length)
    keyList.foreach(key => {
      var k = new Key
      for (c <- key) {
        k += c.toByte
      }
      KeyList(i) = k
      i = i + 1
    })
    try {
      store.delBatch(KeyList)
      store.commitTx(t)
    } catch {
      case e: Exception => {
        logger.error("Failed to delete object batch for : " + keyList.mkString(","))
        store.endTx(t)
        throw new UpdateStoreFailedException("Failed to delete object batch for : " + keyList.mkString(","))
      }
    }
  }

  // If tables are different, an internal utility function
  def getTable(obj: BaseElemDef): String = {
    obj match {
      case o: ModelDef => {
        "models"
      }
      case o: MessageDef => {
        "messages"
      }
      case o: ContainerDef => {
        "containers"
      }
      case o: FunctionDef => {
        "functions"
      }
      case o: AttributeDef => {
        "concepts"
      }
      case o: BaseTypeDef => {
        "types"
      }
      case _ => {
        logger.error("getTable is not implemented for objects of type " + obj.getClass.getName)
        throw new InternalErrorException("getTable is not implemented for objects of type " + obj.getClass.getName)
      }
    }
  }

  def getObjectType(obj: BaseElemDef): String = {
    val className = obj.getClass().getName();
    className.split("\\.").last
  }

  // 
  // The following batch function is useful when we store data in single table
  // If we use Storage component library, note that table itself is associated with a single
  // database connection( which itself can be mean different things depending on the type
  // of datastore, such as cassandra, hbase, etc..)
  // 
  def SaveObjectList(objList: Array[BaseElemDef], store: DataStore) {
    logger.debug("Save " + objList.length + " objects in a single transaction ")
    val tranId = GetNewTranId
    var keyList = new Array[String](objList.length)
    var valueList = new Array[Array[Byte]](objList.length)
    try {
      var i = 0;
      objList.foreach(obj => {
        obj.tranId = tranId
        val key = (getObjectType(obj) + "." + obj.FullNameWithVer).toLowerCase
        var value = serializer.SerializeObjectToByteArray(obj)
        keyList(i) = key
        valueList(i) = value
        i = i + 1
      })
      SaveObjectList(keyList, valueList, store)
    } catch {
      case e: Exception => {
        logger.error("Failed to insert/update object for : " + keyList.mkString(","))
        throw new UpdateStoreFailedException("Failed to insert/update object for : " + keyList.mkString(","))
      }
    }
  }

  // The following batch function is useful when we store data in multiple tables
  // If we use Storage component library, note that each table is associated with a different
  // database connection( which itself can be mean different things depending on the type
  // of datastore, such as cassandra, hbase, etc..)
  def SaveObjectList(objList: Array[BaseElemDef]) {
    logger.debug("Save " + objList.length + " objects in a single transaction ")
    val tranId = GetNewTranId
    var keyList = new Array[String](objList.length)
    var valueList = new Array[Array[Byte]](objList.length)
    var tableList = new Array[String](objList.length)
    try {
      var i = 0;
      objList.foreach(obj => {
        obj.tranId = tranId
        val key = (getObjectType(obj) + "." + obj.FullNameWithVer).toLowerCase
        var value = serializer.SerializeObjectToByteArray(obj)
        keyList(i) = key
        valueList(i) = value
        tableList(i) = getTable(obj)
        i = i + 1
      })
      i = 0
      var storeObjects = new Array[IStorage](keyList.length)
      keyList.foreach(key => {
        var value = valueList(i)
        object obj extends IStorage {
          var k = new Key
          var v = new Value
          for (c <- key) {
            k += c.toByte
          }
          for (c <- value) {
            v += c
          }
          def Key = k
          def Value = v
          def Construct(Key: Key, Value: Value) = {}
        }
        storeObjects(i) = obj
        i = i + 1
      })

      {
        var i = 0
        tableList.foreach(table => {
          val store = tableStoreMap(table)
          val t = store.beginTx
          store.put(storeObjects(i))
          store.commitTx(t)
          i = i + 1
        })
      }

      /*
      val store = tableStoreMap(tableList(0))
      val t = store.beginTx
      store.putBatch(storeObjects)
      store.commitTx(t)
*/
    } catch {
      case e: Exception => {
        logger.error("Failed to insert/update object for : " + keyList.mkString(","))
        throw new UpdateStoreFailedException("Failed to insert/update object for : " + keyList.mkString(","))
      }
    }
  }

  def SaveOutputMsObjectList(objList: Array[BaseElemDef]) {
    SaveObjectList(objList, outputmsgStore)
  }

  def SaveObject(key: String, value: String, store: DataStore) {
    var ba = serializer.SerializeObjectToByteArray(value)
    SaveObject(key, ba, store)
  }

  def UpdateObject(key: String, value: Array[Byte], store: DataStore) {
    SaveObject(key, value, store)
  }

  def ZooKeeperMessage(objList: Array[BaseElemDef], operations: Array[String]): Array[Byte] = {
    try {
      val notification = JsonSerializer.zkSerializeObjectListToJson("Notifications", objList, operations)
      notification.getBytes
    } catch {
      case e: Exception => {
        throw new InternalErrorException("Failed to generate a zookeeper message from the objList " + e.getMessage())
      }
    }
  }

  def NotifyEngine(objList: Array[BaseElemDef], operations: Array[String]) {
    try {
      val notifyEngine = GetMetadataAPIConfig.getProperty("NOTIFY_ENGINE")

      // We have to update the currentTranLevel here, since addObjectToCache method does not have the tranId in object
      // yet (a bug that is being ractified now)...  We can remove this code when that is fixed.
      var max: Long = 0
      objList.foreach(obj => { max = scala.math.max(max, obj.TranId) })
      if (currentTranLevel < max) currentTranLevel = max

      if (notifyEngine != "YES") {
        logger.warn("Not Notifying the engine about this operation because The property NOTIFY_ENGINE is not set to YES")
        PutTranId(objList(0).tranId)
        return
      }

      // Set up the cache of CACHES!!!! Since we are listening for changes to Metadata, we will be notified by Zookeeper
      // of this change that we are making.  This cache of Caches will tell us to ignore this.
      var corrId: Int = 0
      objList.foreach(elem => {
        cacheOfOwnChanges.add((operations(corrId) + "." + elem.NameSpace + "." + elem.Name + "." + elem.Version).toLowerCase)
        corrId = corrId + 1
      })

      val data = ZooKeeperMessage(objList, operations)
      InitZooKeeper
      val znodePath = GetMetadataAPIConfig.getProperty("ZNODE_PATH") + "/metadataupdate"
      logger.debug("Set the data on the zookeeper node " + znodePath)
      zkc.setData().forPath(znodePath, data)
      PutTranId(objList(0).tranId)
    } catch {
      case e: Exception => {
        throw new InternalErrorException("Failed to notify a zookeeper message from the objectList " + e.getMessage())
      }
    }
  }

  def GetNewTranId: Long = {
    try {
      val key = "transaction_id"
      val obj = GetObject(key, transStore)
      val idStr = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte]).asInstanceOf[String]
      idStr.toLong + 1
    } catch {
      case e: ObjectNotFoundException => {
        // first time
        1
      }
      case e: Exception => {
        throw new TranIdNotFoundException("Unable to retrieve the transaction id " + e.toString)
      }
    }
  }

  def GetTranId: Long = {
    try {
      val key = "transaction_id"
      val obj = GetObject(key, transStore)
      val idStr = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte]).asInstanceOf[String]
      idStr.toLong
    } catch {
      case e: ObjectNotFoundException => {
        // first time
        0
      }
      case e: Exception => {
        throw new TranIdNotFoundException("Unable to retrieve the transaction id " + e.toString)
      }
    }
  }

  def PutTranId(tId: Long) = {
    try {
      val key = "transaction_id"
      val value = tId.toString
      SaveObject(key, value, transStore)
    } catch {
      case e: Exception => {
        throw new UpdateStoreFailedException("Unable to Save the transaction id " + tId + ":" + e.getMessage())
      }
    }
  }

  def SaveObject(obj: BaseElemDef, mdMgr: MdMgr): Boolean = {
    try {
      val key = (getObjectType(obj) + "." + obj.FullNameWithVer).toLowerCase
      val dispkey = (getObjectType(obj) + "." + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version)).toLowerCase
      obj.tranId = GetNewTranId
      //val value = JsonSerializer.SerializeObjectToJson(obj)
      logger.debug("Serialize the object: name of the object => " + dispkey)
      var value = serializer.SerializeObjectToByteArray(obj)
      obj match {
        case o: ModelDef => {
          logger.debug("Adding the model to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, modelStore)
          mdMgr.AddModelDef(o, false)
        }
        case o: MessageDef => {
          logger.debug("Adding the message to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, messageStore)
          mdMgr.AddMsg(o)
        }
        case o: ContainerDef => {
          logger.debug("Adding the container to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, containerStore)
          mdMgr.AddContainer(o)
        }
        case o: FunctionDef => {
          val funcKey = (obj.getClass().getName().split("\\.").last + "." + o.typeString).toLowerCase
          logger.debug("Adding the function to the cache: name of the object =>  " + funcKey)
          SaveObject(funcKey, value, functionStore)
          mdMgr.AddFunc(o)
        }
        case o: AttributeDef => {
          logger.debug("Adding the attribute to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, conceptStore)
          mdMgr.AddAttribute(o)
        }
        case o: ScalarTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, typeStore)
          mdMgr.AddScalar(o)
        }
        case o: ArrayTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, typeStore)
          mdMgr.AddArray(o)
        }
        case o: ArrayBufTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, typeStore)
          mdMgr.AddArrayBuffer(o)
        }
        case o: ListTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, typeStore)
          mdMgr.AddList(o)
        }
        case o: QueueTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, typeStore)
          mdMgr.AddQueue(o)
        }
        case o: SetTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, typeStore)
          mdMgr.AddSet(o)
        }
        case o: TreeSetTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, typeStore)
          mdMgr.AddTreeSet(o)
        }
        case o: SortedSetTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, typeStore)
          mdMgr.AddSortedSet(o)
        }
        case o: MapTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, typeStore)
          mdMgr.AddMap(o)
        }
        case o: ImmutableMapTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, typeStore)
          mdMgr.AddImmutableMap(o)
        }
        case o: HashMapTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, typeStore)
          mdMgr.AddHashMap(o)
        }
        case o: TupleTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, typeStore)
          mdMgr.AddTupleType(o)
        }
        case o: ContainerTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, typeStore)
          mdMgr.AddContainerType(o)
        }
        case o: OutputMsgDef => {
          logger.trace("Adding the Output Message to the cache: name of the object =>  " + dispkey)
          SaveObject(key, value, outputmsgStore)
          mdMgr.AddOutputMsg(o)
        }
        case _ => {
          logger.error("SaveObject is not implemented for objects of type " + obj.getClass.getName)
        }
      }
      true
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Failed to Save the object(" + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + "): " + e.getMessage())
        false
      }
      case e: Exception => {
        logger.error("Failed to Save the object(" + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + "): " + e.getMessage())
        false
      }
    }
  }

  def UpdateObjectInDB(obj: BaseElemDef) {
    try {
      val key = (getObjectType(obj) + "." + obj.FullNameWithVer).toLowerCase
      val dispkey = (getObjectType(obj) + "." + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version)).toLowerCase

      logger.debug("Serialize the object: name of the object => " + dispkey)
      var value = serializer.SerializeObjectToByteArray(obj)
      obj match {
        case o: ModelDef => {
          logger.debug("Updating the model in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, modelStore)
        }
        case o: MessageDef => {
          logger.debug("Updating the message in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, messageStore)
        }
        case o: ContainerDef => {
          logger.debug("Updating the container in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, containerStore)
        }
        case o: FunctionDef => {
          val funcKey = (obj.getClass().getName().split("\\.").last + "." + o.typeString).toLowerCase
          logger.debug("Updating the function in the DB: name of the object =>  " + funcKey)
          UpdateObject(funcKey, value, functionStore)
        }
        case o: AttributeDef => {
          logger.debug("Updating the attribute in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, conceptStore)
        }
        case o: ScalarTypeDef => {
          logger.debug("Updating the Type in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, typeStore)
        }
        case o: ArrayTypeDef => {
          logger.debug("Updating the Type in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, typeStore)
        }
        case o: ArrayBufTypeDef => {
          logger.debug("Updating the Type in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, typeStore)
        }
        case o: ListTypeDef => {
          logger.debug("Updating the Type in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, typeStore)
        }
        case o: QueueTypeDef => {
          logger.debug("Updating the Type in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, typeStore)
        }
        case o: SetTypeDef => {
          logger.debug("Updating the Type in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, typeStore)
        }
        case o: TreeSetTypeDef => {
          logger.debug("Updating the Type in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, typeStore)
        }
        case o: SortedSetTypeDef => {
          logger.debug("Updating the Type in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, typeStore)
        }
        case o: MapTypeDef => {
          logger.debug("Updating the Type in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, typeStore)
        }
        case o: ImmutableMapTypeDef => {
          logger.debug("Updating the Type in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, typeStore)
        }
        case o: HashMapTypeDef => {
          logger.debug("Updating the Type in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, typeStore)
        }
        case o: TupleTypeDef => {
          logger.debug("Updating the Type in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, typeStore)
        }
        case o: ContainerTypeDef => {
          logger.debug("Updating the Type in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, typeStore)
        }
        case o: OutputMsgDef => {
          logger.debug("Updating the output message in the DB: name of the object =>  " + dispkey)
          UpdateObject(key, value, outputmsgStore)
        }
        case _ => {
          logger.error("UpdateObject is not implemented for objects of type " + obj.getClass.getName)
        }
      }
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Failed to Update the object(" + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + "): " + e.getMessage())
      }
      case e: Exception => {
        logger.error("Failed to Update the object(" + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + "): " + e.getMessage())
      }
    }
  }

  def GetJarAsArrayOfBytes(jarName: String): Array[Byte] = {
    try {
      val iFile = new File(jarName)
      if (!iFile.exists) {
        logger.error("Jar file (" + jarName + ") is not found: ")
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
        e.printStackTrace()
        throw new FileNotFoundException("Failed to Convert the Jar (" + jarName + ") to array of bytes: " + e.getMessage())
      }
      case e: Exception => {
        e.printStackTrace()
        throw new InternalErrorException("Failed to Convert the Jar (" + jarName + ") to array of bytes: " + e.getMessage())
      }
    }
  }

  def PutArrayOfBytesToJar(ba: Array[Byte], jarName: String) = {
    logger.debug("Downloading the jar contents into the file " + jarName)
    try {
      val iFile = new File(jarName)
      val bos = new BufferedOutputStream(new FileOutputStream(iFile));
      bos.write(ba)
      bos.close();
    } catch {
      case e: Exception => {
        logger.error("Failed to dump array of bytes to the Jar file (" + jarName + "):  " + e.getMessage())
      }
    }
  }

  def UploadJarsToDB(obj: BaseElemDef, forceUploadMainJar: Boolean = true, alreadyCheckedJars: scala.collection.mutable.Set[String] = null): Unit = {
    val checkedJars: scala.collection.mutable.Set[String] = if (alreadyCheckedJars == null) scala.collection.mutable.Set[String]() else alreadyCheckedJars

    try {
      var keyList = new ArrayBuffer[String](0)
      var valueList = new ArrayBuffer[Array[Byte]](0)

      val tmpJarPaths = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS")
      val jarPaths = if (tmpJarPaths != null) tmpJarPaths.split(",").toSet else scala.collection.immutable.Set[String]()
      if (obj.jarName != null && (forceUploadMainJar || checkedJars.contains(obj.jarName) == false)) { //BUGBUG 
        val jarsPathsInclTgtDir = jarPaths + MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_TARGET_DIR")
        var jarName = Utils.GetValidJarFile(jarsPathsInclTgtDir, obj.jarName)
        var value = GetJarAsArrayOfBytes(jarName)

        var loadObject = false

        if (forceUploadMainJar) {
          loadObject = true
        } else {
          var mObj: IStorage = null
          try {
            mObj = GetObject(obj.jarName, jarStore)
          } catch {
            case e: ObjectNotFoundException => {
              loadObject = true
            }
          }

          if (loadObject == false) {
            val ba = mObj.Value.toArray[Byte]
            val fs = ba.length
            if (fs != value.length) {
              logger.debug("A jar file already exists, but it's size (" + fs + ") doesn't match with the size of the Jar (" +
                jarName + "," + value.length + ") of the object(" + obj.FullNameWithVer + ")")
              loadObject = true
            }
          }
        }

        checkedJars += obj.jarName

        if (loadObject) {
          logger.debug("Update the jarfile (size => " + value.length + ") of the object: " + obj.jarName)
          keyList += obj.jarName
          valueList += value
        }
      }

      if (obj.DependencyJarNames != null) {
        obj.DependencyJarNames.foreach(j => {
          // do not upload if it already exist & just uploaded/checked in db, minor optimization
          if (j.endsWith(".jar") && checkedJars.contains(j) == false) {
            var loadObject = false
            val jarName = Utils.GetValidJarFile(jarPaths, j)
            val value = GetJarAsArrayOfBytes(jarName)
            var mObj: IStorage = null
            try {
              mObj = GetObject(j, jarStore)
            } catch {
              case e: ObjectNotFoundException => {
                loadObject = true
              }
            }

            if (loadObject == false) {
              val ba = mObj.Value.toArray[Byte]
              val fs = ba.length
              if (fs != value.length) {
                logger.debug("A jar file already exists, but it's size (" + fs + ") doesn't match with the size of the Jar (" +
                  jarName + "," + value.length + ") of the object(" + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + ")")
                loadObject = true
              }
            }

            if (loadObject) {
              keyList += j
              logger.debug("Update the jarfile (size => " + value.length + ") of the object: " + j)
              valueList += value
            } else {
              logger.debug("The jarfile " + j + " already exists in DB.")
            }
            checkedJars += j
          }
        })
      }
      if (keyList.length > 0) {
        SaveObjectList(keyList.toArray, valueList.toArray, jarStore)
      }
    } catch {
      case e: Exception => {
        e.printStackTrace
        throw new InternalErrorException("Failed to Update the Jar of the object(" + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + "): " + e.getMessage())
      }
    }
  }

  def UploadJarToDB(jarName: String) {
    try {
      val f = new File(jarName)
      if (f.exists()) {
        var key = f.getName()
        var value = GetJarAsArrayOfBytes(jarName)
        logger.debug("Update the jarfile (size => " + value.length + ") of the object: " + jarName)
        SaveObject(key, value, jarStore)
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "UploadJarToDB", null, ErrorCodeConstants.Upload_Jar_Successful + ":" + jarName)
        apiResult.toString()

      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UploadJarToDB", null, "Error : " + e.toString() + ErrorCodeConstants.Upload_Jar_Failed + ":" + jarName)
        apiResult.toString()
      }
    }
  }

  def UploadJarToDB(jarName: String, byteArray: Array[Byte], userid: Option[String]): String = {
    try {
      var key = jarName
      var value = byteArray
      logger.debug("Update the jarfile (size => " + value.length + ") of the object: " + jarName)
      logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.INSERTJAR, jarName, AuditConstants.SUCCESS, "", jarName)
      SaveObject(key, value, jarStore)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "UploadJarToDB", null, ErrorCodeConstants.Upload_Jar_Successful + ":" + jarName)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UploadJarToDB", null, "Error : " + e.toString() + ErrorCodeConstants.Upload_Jar_Failed + ":" + jarName)
        apiResult.toString()
      }
    }
  }

  def IsDownloadNeeded(jar: String, obj: BaseElemDef): Boolean = {
    try {
      if (jar == null) {
        logger.debug("The object " + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + " has no jar associated with it. Nothing to download..")
        false
      }
      val tmpJarPaths = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS")
      val jarPaths = if (tmpJarPaths != null) tmpJarPaths.split(",").toSet else scala.collection.immutable.Set[String]()
      val jarName = Utils.GetValidJarFile(jarPaths, jar)
      val f = new File(jarName)
      if (f.exists()) {
        val key = jar
        val mObj = GetObject(key, jarStore)
        val ba = mObj.Value.toArray[Byte]
        val fs = f.length()
        if (fs != ba.length) {
          logger.debug("A jar file already exists, but it's size (" + fs + ") doesn't match with the size of the Jar (" +
            jar + "," + ba.length + ") of the object(" + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + ")")
          true
        } else {
          logger.debug("A jar file already exists, and it's size (" + fs + ")  matches with the size of the existing Jar (" +
            jar + "," + ba.length + ") of the object(" + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + "), no need to download.")
          false
        }
      } else {
        logger.debug("The jar " + jarName + " is not available, download from database. ")
        true
      }
    } catch {
      case e: Exception => {
        throw new InternalErrorException("Failed to verify whether a download is required for the jar " + jar + " of the object(" + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + "): " + e.getMessage())
      }
    }
  }

  def GetDependantJars(obj: BaseElemDef): Array[String] = {
    try {
      var allJars = new Array[String](0)
      if (obj.JarName != null)
        allJars = allJars :+ obj.JarName
      if (obj.DependencyJarNames != null) {
        obj.DependencyJarNames.foreach(j => {
          if (j.endsWith(".jar")) {
            allJars = allJars :+ j
          }
        })
      }
      allJars
    } catch {
      case e: Exception => {
        throw new InternalErrorException("Failed to get dependant jars for the given object (" + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + "): " + e.getMessage())
      }
    }
  }

  def DownloadJarFromDB(obj: BaseElemDef) {
    var curJar: String = ""
    try {
      //val key:String = (getObjectType(obj) + "." + obj.FullNameWithVer).toLowerCase
      if (obj.jarName == null) {
        logger.debug("The object " + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + " has no jar associated with it. Nothing to download..")
        return
      }
      var allJars = GetDependantJars(obj)
      logger.debug("Found " + allJars.length + " dependant jars. Jars:" + allJars.mkString(","))
      if (allJars.length > 0) {
        val tmpJarPaths = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS")
        val jarPaths = if (tmpJarPaths != null) tmpJarPaths.split(",").toSet else scala.collection.immutable.Set[String]()
        jarPaths.foreach(jardir => {
          val dir = new File(jardir)
          if (!dir.exists()) {
            // attempt to create the missing directory
            dir.mkdir();
          }
        })

        val dirPath = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_TARGET_DIR")
        val dir = new File(dirPath)
        if (!dir.exists()) {
          // attempt to create the missing directory
          dir.mkdir();
        }

        allJars.foreach(jar => {
          curJar = jar
          try {
            // download only if it doesn't already exists
            val b = IsDownloadNeeded(jar, obj)
            if (b == true) {
              val key = jar
              val mObj = GetObject(key, jarStore)
              val ba = mObj.Value.toArray[Byte]
              val jarName = dirPath + "/" + jar
              PutArrayOfBytesToJar(ba, jarName)
            }
          } catch {
            case e: Exception => {
              logger.error("Failed to download the Jar of the object(" + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + "'s dep jar " + curJar + "). Message:" + e.getMessage + " Reason:" + e.getCause)
              e.printStackTrace
            }
          }
        })
      }
    } catch {
      case e: Exception => {
        logger.error("Failed to download the Jar of the object(" + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + "'s dep jar " + curJar + "). Message:" + e.getMessage + " Reason:" + e.getCause)
        e.printStackTrace
      }
    }
  }

  def UpdateObjectInCache(obj: BaseElemDef, operation: String, mdMgr: MdMgr): BaseElemDef = {
    var updatedObject: BaseElemDef = null

    // Update the current transaction level with this object  ???? What if an exception occurs ????
    if (currentTranLevel < obj.TranId) currentTranLevel = obj.TranId

    try {
      obj match {
        case o: FunctionDef => {
          updatedObject = mdMgr.ModifyFunction(o.nameSpace, o.name, o.ver, operation)
        }
        case o: ModelDef => {
          updatedObject = mdMgr.ModifyModel(o.nameSpace, o.name, o.ver, operation)
        }
        case o: MessageDef => {
          updatedObject = mdMgr.ModifyMessage(o.nameSpace, o.name, o.ver, operation)
        }
        case o: ContainerDef => {
          updatedObject = mdMgr.ModifyContainer(o.nameSpace, o.name, o.ver, operation)
        }
        case o: AttributeDef => {
          updatedObject = mdMgr.ModifyAttribute(o.nameSpace, o.name, o.ver, operation)
        }
        case o: ScalarTypeDef => {
          updatedObject = mdMgr.ModifyType(o.nameSpace, o.name, o.ver, operation)
        }
        case o: ArrayTypeDef => {
          updatedObject = mdMgr.ModifyType(o.nameSpace, o.name, o.ver, operation)
        }
        case o: ArrayBufTypeDef => {
          updatedObject = mdMgr.ModifyType(o.nameSpace, o.name, o.ver, operation)
        }
        case o: ListTypeDef => {
          updatedObject = mdMgr.ModifyType(o.nameSpace, o.name, o.ver, operation)
        }
        case o: QueueTypeDef => {
          updatedObject = mdMgr.ModifyType(o.nameSpace, o.name, o.ver, operation)
        }
        case o: SetTypeDef => {
          updatedObject = mdMgr.ModifyType(o.nameSpace, o.name, o.ver, operation)
        }
        case o: TreeSetTypeDef => {
          updatedObject = mdMgr.ModifyType(o.nameSpace, o.name, o.ver, operation)
        }
        case o: SortedSetTypeDef => {
          updatedObject = mdMgr.ModifyType(o.nameSpace, o.name, o.ver, operation)
        }
        case o: MapTypeDef => {
          updatedObject = mdMgr.ModifyType(o.nameSpace, o.name, o.ver, operation)
        }
        case o: ImmutableMapTypeDef => {
          updatedObject = mdMgr.ModifyType(o.nameSpace, o.name, o.ver, operation)
        }
        case o: HashMapTypeDef => {
          updatedObject = mdMgr.ModifyType(o.nameSpace, o.name, o.ver, operation)
        }
        case o: TupleTypeDef => {
          updatedObject = mdMgr.ModifyType(o.nameSpace, o.name, o.ver, operation)
        }
        case o: ContainerTypeDef => {
          updatedObject = mdMgr.ModifyType(o.nameSpace, o.name, o.ver, operation)
        }
        case o: OutputMsgDef => {
          updatedObject = mdMgr.ModifyOutputMsg(o.nameSpace, o.name, o.ver, operation)
        }
        case _ => {
          throw new InternalErrorException("UpdateObjectInCache is not implemented for objects of type " + obj.getClass.getName)
        }
      }
      updatedObject
    } catch {
      case e: ObjectNolongerExistsException => {
        throw new ObjectNolongerExistsException("The object " + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + " nolonger exists in metadata : It may have been removed already")
      }
      case e: Exception => {
        throw new Exception("Unexpected error in UpdateObjectInCache: " + e.getMessage())
      }
    }
  }

  def AddObjectToCache(o: Object, mdMgr: MdMgr) {
    // If the object's Delete flag is set, this is a noop.
    val obj = o.asInstanceOf[BaseElemDef]

    // Update the current transaction level with this object  ???? What if an exception occurs ????
    if (currentTranLevel < obj.TranId) currentTranLevel = obj.TranId

    if (obj.IsDeleted)
      return
    try {
      val key = obj.FullNameWithVer.toLowerCase
      val dispkey = obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version)
      obj match {
        case o: ModelDef => {
          logger.debug("Adding the model to the cache: name of the object =>  " + dispkey)
          mdMgr.AddModelDef(o, true)
        }
        case o: MessageDef => {
          logger.debug("Adding the message to the cache: name of the object =>  " + dispkey)
          mdMgr.AddMsg(o)
        }
        case o: ContainerDef => {
          logger.debug("Adding the container to the cache: name of the object =>  " + dispkey)
          mdMgr.AddContainer(o)
        }
        case o: FunctionDef => {
          val funcKey = o.typeString.toLowerCase
          logger.debug("Adding the function to the cache: name of the object =>  " + funcKey)
          mdMgr.AddFunc(o)
        }
        case o: AttributeDef => {
          logger.debug("Adding the attribute to the cache: name of the object =>  " + dispkey)
          mdMgr.AddAttribute(o)
        }
        case o: ScalarTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          mdMgr.AddScalar(o)
        }
        case o: ArrayTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          mdMgr.AddArray(o)
        }
        case o: ArrayBufTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          mdMgr.AddArrayBuffer(o)
        }
        case o: ListTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          mdMgr.AddList(o)
        }
        case o: QueueTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          mdMgr.AddQueue(o)
        }
        case o: SetTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          mdMgr.AddSet(o)
        }
        case o: TreeSetTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          mdMgr.AddTreeSet(o)
        }
        case o: SortedSetTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          mdMgr.AddSortedSet(o)
        }
        case o: MapTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          mdMgr.AddMap(o)
        }
        case o: ImmutableMapTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          mdMgr.AddImmutableMap(o)
        }
        case o: HashMapTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          mdMgr.AddHashMap(o)
        }
        case o: TupleTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          mdMgr.AddTupleType(o)
        }
        case o: ContainerTypeDef => {
          logger.debug("Adding the Type to the cache: name of the object =>  " + dispkey)
          mdMgr.AddContainerType(o)
        }
        case o: OutputMsgDef => {
          logger.trace("Adding the Output Msg to the cache: name of the object =>  " + key)
          mdMgr.AddOutputMsg(o)
        }
        case _ => {
          logger.error("SaveObject is not implemented for objects of type " + obj.getClass.getName)
        }
      }
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Failed to Cache the object(" + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + "): " + e.getMessage())
      }
      case e: Exception => {
        logger.error("Failed to Cache the object(" + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + "): " + e.getMessage())
      }
    }
  }

  def ModifyObject(obj: BaseElemDef, operation: String) {
    try {
      val o1 = UpdateObjectInCache(obj, operation, MdMgr.GetMdMgr)
      UpdateObjectInDB(o1)
    } catch {
      case e: ObjectNolongerExistsException => {
        logger.error("The object " + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + " nolonger exists in metadata : It may have been removed already")
      }
      case e: Exception => {
        throw new Exception("Unexpected error in ModifyObject: " + e.getMessage())
      }
    }
  }

  def DeleteObject(key: String, store: DataStore) {
    var k = new Key
    for (c <- key) {
      k += c.toByte
    }
    val t = store.beginTx
    store.del(k)
    store.commitTx(t)
  }

  def DeleteObject(obj: BaseElemDef) {
    try {
      ModifyObject(obj, "Remove")
    } catch {
      case e: ObjectNolongerExistsException => {
        logger.error("The object " + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + " nolonger exists in metadata : It may have been removed already")
      }
      case e: Exception => {
        throw new Exception("Unexpected error in DeleteObject: " + e.getMessage())
      }
    }
  }

  def ActivateObject(obj: BaseElemDef) {
    try {
      ModifyObject(obj, "Activate")
    } catch {
      case e: ObjectNolongerExistsException => {
        logger.error("The object " + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + " nolonger exists in metadata : It may have been removed already")
      }
      case e: Exception => {
        throw new Exception("Unexpected error in ActivateObject: " + e.getMessage())
      }
    }
  }

  def DeactivateObject(obj: BaseElemDef) {
    try {
      ModifyObject(obj, "Deactivate")
    } catch {
      case e: ObjectNolongerExistsException => {
        logger.error("The object " + obj.FullName + "." + MdMgr.Pad0s2Version(obj.Version) + " nolonger exists in metadata : It may have been removed already")
      }
      case e: Exception => {
        throw new Exception("Unexpected error in DeactivateObject: " + e.getMessage())
      }
    }
  }

  @throws(classOf[Json4sParsingException])
  @throws(classOf[ApiResultParsingException])
  def getApiResult(apiResultJson: String): String = {
    // parse using Json4s
    try {
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(apiResultJson)
      //logger.debug("Parsed the json : " + apiResultJson)
      val apiResultInfo = json.extract[APIResultJsonProxy]
      (apiResultInfo.APIResults.statusCode + apiResultInfo.APIResults.functionName + apiResultInfo.APIResults.resultData + apiResultInfo.APIResults.description)
    } catch {
      case e: MappingException => {
        e.printStackTrace()
        throw Json4sParsingException(e.getMessage())
      }
      case e: Exception => {
        e.printStackTrace()
        throw new ApiResultParsingException(e.getMessage())
      }
    }
  }

  private def GetDataStoreHandle(jarPaths: collection.immutable.Set[String], dataStoreInfo: String, tableName: String): DataStore = {
    try {
      logger.debug("Getting DB Connection for dataStoreInfo:%s, tableName:%s".format(dataStoreInfo, tableName))
      return KeyValueManager.Get(jarPaths, dataStoreInfo, tableName)
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new Exception(e.getMessage())
      }
    }
  }

  def OpenDbStore(jarPaths: collection.immutable.Set[String], dataStoreInfo: String) {
    try {
      logger.debug("Opening datastore")
      metadataStore = GetDataStoreHandle(jarPaths, dataStoreInfo, "metadata_objects")
      configStore = GetDataStoreHandle(jarPaths, dataStoreInfo, "config_objects")
      jarStore = GetDataStoreHandle(jarPaths, dataStoreInfo, "jar_store")
      transStore = GetDataStoreHandle(jarPaths, dataStoreInfo, "transaction_id")
      modelConfigStore = GetDataStoreHandle(jarPaths, dataStoreInfo, "model_config_objects")
    
      modelStore = metadataStore
      messageStore = metadataStore
      containerStore = metadataStore
      functionStore = metadataStore
      conceptStore = metadataStore
      typeStore = metadataStore
      otherStore = metadataStore
      outputmsgStore = metadataStore
      tableStoreMap = Map("models" -> modelStore,
        "messages" -> messageStore,
        "containers" -> containerStore,
        "functions" -> functionStore,
        "concepts" -> conceptStore,
        "types" -> typeStore,
        "others" -> otherStore,
        "jar_store" -> jarStore,
        "config_objects" -> configStore,
        "transaction_id" -> transStore,
        "outputmsgs" -> outputmsgStore,
        "model_config_objects" -> modelConfigStore,
        "transaction_id" -> transStore)
    } catch {
      case e: CreateStoreFailedException => {
        e.printStackTrace()
        throw new CreateStoreFailedException(e.getMessage())
      }
      case e: Exception => {
        e.printStackTrace()
        throw new CreateStoreFailedException(e.getMessage())
      }
    }
  }

  def CloseDbStore: Unit = lock.synchronized {
    try {
      logger.debug("Closing datastore")
      if (metadataStore != null) {
        metadataStore.Shutdown()
        metadataStore = null
        logger.debug("metdatastore closed")
      }
      if (transStore != null) {
        transStore.Shutdown()
        transStore = null
        logger.debug("transStore closed")
      }
      if (jarStore != null) {
        jarStore.Shutdown()
        jarStore = null
        logger.debug("jarStore closed")
      }
      if (configStore != null) {
        configStore.Shutdown()
        configStore = null
        logger.debug("configStore closed")
      }
      if (modelConfigStore != null) {
        modelConfigStore.Shutdown()
        modelConfigStore = null
        logger.debug("modelConfigStore closed")
      }
      if (userPopertiesStore != null) {
        userPopertiesStore.Shutdown()
        userPopertiesStore = null
        logger.debug("userPopertiesStore closed")              
      }
    } catch {
      case e: Exception => {
        throw e;
      }
    }
  }

  def TruncateDbStore: Unit = lock.synchronized {
    try {
      logger.debug("Truncating datastore")
      metadataStore.TruncateStore
      transStore.TruncateStore
      jarStore.TruncateStore
      configStore.TruncateStore
    } catch {
      case e: Exception => {
        throw e;
      }
    }
  }

  def AddType(typeText: String, format: String): String = {
    try {
      logger.debug("Parsing type object given as Json String..")
      val typ = JsonSerializer.parseType(typeText, "JSON")
      SaveObject(typ, MdMgr.GetMdMgr)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddType", typeText, ErrorCodeConstants.Add_Type_Successful)
      apiResult.toString()
    } catch {
      case e: AlreadyExistsException => {
        logger.warn("Failed to add the type, json => " + typeText + "\nError => " + e.getMessage())
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddType", typeText, "Error :" + e.toString() + ErrorCodeConstants.Add_Type_Failed)
        apiResult.toString()
      }
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddType", typeText, "Error :" + e.toString() + ErrorCodeConstants.Add_Type_Failed)
        apiResult.toString()
      }
    }
  }

  def DumpTypeDef(typeDef: ScalarTypeDef) {
    logger.debug("NameSpace => " + typeDef.nameSpace)
    logger.debug("Name => " + typeDef.name)
    logger.debug("Version => " + MdMgr.Pad0s2Version(typeDef.ver))
    logger.debug("Description => " + typeDef.description)
    logger.debug("Implementation class name => " + typeDef.implementationNm)
    logger.debug("Implementation jar name => " + typeDef.jarName)
  }

  def AddType(typeDef: BaseTypeDef): String = {
    val dispkey = typeDef.FullName + "." + MdMgr.Pad0s2Version(typeDef.Version)
    try {
      var key = typeDef.FullNameWithVer
      var value = JsonSerializer.SerializeObjectToJson(typeDef);
      logger.debug("key => " + key + ",value =>" + value);
      SaveObject(typeDef, MdMgr.GetMdMgr)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddType", null, ErrorCodeConstants.Add_Type_Successful + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddType", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Type_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def AddTypes(typesText: String, format: String, userid: Option[String]): String = {
    try {
      if (format != "JSON") {
        var apiResult = new ApiResult(ErrorCodeConstants.Not_Implemented_Yet, "AddTypes", typesText, ErrorCodeConstants.Not_Implemented_Yet_Msg)
        apiResult.toString()
      } else {
        var typeList = JsonSerializer.parseTypeList(typesText, "JSON")
        if (typeList.length > 0) {
          logger.debug("Found " + typeList.length + " type objects ")
          typeList.foreach(typ => {
            logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.INSERTOBJECT, typesText, AuditConstants.SUCCESS, "", typ.FullNameWithVer)
            SaveObject(typ, MdMgr.GetMdMgr)
            logger.debug("Type object name => " + typ.FullName + "." + MdMgr.Pad0s2Version(typ.Version))
          })
          /** Only report the ones actually saved... if there were others, they are in the log as "fail to add" due most likely to already being defined */
          val typesSavedAsJson: String = JsonSerializer.SerializeObjectListToJson(typeList)
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddTypes", typesText, ErrorCodeConstants.Add_Type_Successful)
          apiResult.toString()
        } else {
          var apiResult = new ApiResult(ErrorCodeConstants.Warning, "AddTypes", null, "All supplied types are already available. No types to add.")
          apiResult.toString()
        }
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddTypes", typesText, "Error :" + e.toString() + ErrorCodeConstants.Add_Type_Failed)
        apiResult.toString()
      }
    }
  }

  // Remove type for given TypeName and Version
  def RemoveType(typeNameSpace: String, typeName: String, version: Long, userid: Option[String]): String = {
    val key = typeNameSpace + "." + typeName + "." + version
    val dispkey = typeNameSpace + "." + typeName + "." + MdMgr.Pad0s2Version(version)
    if (userid != None) logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.DELETEOBJECT, AuditConstants.TYPE, AuditConstants.SUCCESS, "", key)
    try {
      val typ = MdMgr.GetMdMgr.Type(typeNameSpace, typeName, version, true)
      typ match {
        case None =>
          None
          logger.debug("Type " + dispkey + " is not found in the cache ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveType", null, ErrorCodeConstants.Remove_Type_Not_Found + ":" + dispkey)
          apiResult.toString()
        case Some(ts) =>
          DeleteObject(ts.asInstanceOf[BaseElemDef])
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveType", null, ErrorCodeConstants.Remove_Type_Successful + ":" + dispkey)
          apiResult.toString()
      }
    } catch {
      case e: ObjectNolongerExistsException => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveType", null, "Error: " + e.toString() + ErrorCodeConstants.Remove_Type_Failed + ":" + dispkey)
        apiResult.toString()
      }
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveType", null, "Error: " + e.toString() + ErrorCodeConstants.Remove_Type_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def UpdateType(typeJson: String, format: String, userid: Option[String]): String = {
    implicit val jsonFormats: Formats = DefaultFormats
    val typeDef = JsonSerializer.parseType(typeJson, "JSON")
    logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.UPDATEOBJECT, typeJson, AuditConstants.SUCCESS, "", typeDef.FullNameWithVer)

    val key = typeDef.nameSpace + "." + typeDef.name + "." + typeDef.Version
    val dispkey = typeDef.nameSpace + "." + typeDef.name + "." + MdMgr.Pad0s2Version(typeDef.Version)
    var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateType", null, ErrorCodeConstants.Update_Type_Internal_Error + ":" + dispkey)
    try {
      val fName = MdMgr.MkFullName(typeDef.nameSpace, typeDef.name)
      val latestType = MdMgr.GetMdMgr.Types(typeDef.nameSpace, typeDef.name, true, true)
      latestType match {
        case None =>
          None
          logger.debug("No types with the name " + fName + " are found ")
          apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateType", null, ErrorCodeConstants.Update_Type_Failed_Not_Found + ":" + dispkey)
        case Some(ts) =>
          val tsa = ts.toArray
          logger.debug("Found " + tsa.length + " types ")
          if (tsa.length > 1) {
            apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateType", null, ErrorCodeConstants.Update_Type_Failed_More_Than_One_Found + ":" + dispkey)
          } else {
            val latestVersion = tsa(0)
            if (latestVersion.ver > typeDef.ver) {
              RemoveType(latestVersion.nameSpace, latestVersion.name, latestVersion.ver, userid)
              AddType(typeDef)
              apiResult = new ApiResult(ErrorCodeConstants.Success, "UpdateType", null, ErrorCodeConstants.Update_Type_Successful + ":" + key)
            } else {
              apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateType", null, ErrorCodeConstants.Update_Type_Failed_Higher_Version_Required + ":" + dispkey)
            }
          }
      }
      apiResult.toString()
    } catch {
      case e: MappingException => {
        logger.error("Failed to parse the type, json => " + typeJson + ",Error => " + e.getMessage())
        apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateType", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Type_Failed + ":" + dispkey)
        apiResult.toString()
      }
      case e: AlreadyExistsException => {
        logger.error("Failed to update the type, json => " + typeJson + ",Error => " + e.getMessage())
        apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateType", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Type_Failed + ":" + dispkey)
        apiResult.toString()
      }
      case e: Exception => {
        logger.error("Failed to up the type, json => " + typeJson + ",Error => " + e.getMessage())
        apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateType", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Type_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  // Upload Jars into system. Dependency jars may need to upload first. Once we upload the jar, if we retry to upload it will throw an exception.
  def UploadJar(jarPath: String): String = {
    try {
      val iFile = new File(jarPath)
      if (!iFile.exists) {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UploadJar", null, ErrorCodeConstants.File_Not_Found + ":" + jarPath)
        apiResult.toString()
      } else {
        val jarName = iFile.getName()
        val jarObject = MdMgr.GetMdMgr.MakeJarDef(MetadataAPIImpl.sysNS, jarName, "100")
        var objectsAdded = new Array[BaseElemDef](0)
        objectsAdded = objectsAdded :+ jarObject
        UploadJarToDB(jarPath)
        val operations = for (op <- objectsAdded) yield "Add"
        NotifyEngine(objectsAdded, operations)
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "UploadJar", null, ErrorCodeConstants.Upload_Jar_Successful + ":" + jarPath)
        apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UploadJar", null, "Error :" + e.toString() + ErrorCodeConstants.Upload_Jar_Failed + ":" + jarPath)
        apiResult.toString()
      }
    }
  }

  def DumpAttributeDef(attrDef: AttributeDef) {
    logger.debug("NameSpace => " + attrDef.nameSpace)
    logger.debug("Name => " + attrDef.name)
    logger.debug("Type => " + attrDef.typeString)
  }

  def AddConcept(attributeDef: BaseAttributeDef): String = {
    val key = attributeDef.FullNameWithVer
    val dispkey = attributeDef.FullName + "." + MdMgr.Pad0s2Version(attributeDef.Version)
    try {
      SaveObject(attributeDef, MdMgr.GetMdMgr)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddConcept", null, ErrorCodeConstants.Add_Concept_Successful + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Concept_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def RemoveConcept(concept: AttributeDef): String = {
    var key = concept.nameSpace + ":" + concept.name
    val dispkey = key // Not looking version at this moment
    try {
      DeleteObject(concept)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveConcept", null, ErrorCodeConstants.Remove_Concept_Successful + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Concept_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def RemoveConcept(key: String, userid: Option[String]): String = {
    try {
      if (userid != None) logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.DELETEOBJECT, AuditConstants.FUNCTION, AuditConstants.SUCCESS, "", key)
      val c = MdMgr.GetMdMgr.Attributes(key, false, false)
      c match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveConcept", null, ErrorCodeConstants.Remove_Concept_Failed_Not_Available + ":" + key)
          apiResult.toString()
        case Some(cs) =>
          val conceptArray = cs.toArray
          conceptArray.foreach(concept => { DeleteObject(concept) })
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveConcept", null, ErrorCodeConstants.Remove_Concept_Successful + ":" + key) //JsonSerializer.SerializeObjectListToJson(conceptArray))
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Concept_Failed + ":" + key)
        apiResult.toString()
      }
    }
  }

  def RemoveConcept(nameSpace: String, name: String, version: Long, userid: Option[String]): String = {
    val dispkey = nameSpace + "." + name + "." + MdMgr.Pad0s2Version(version)
    if (userid != None) logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.DELETEOBJECT, AuditConstants.CONCEPT, AuditConstants.SUCCESS, "", dispkey)
    try {
      val c = MdMgr.GetMdMgr.Attribute(nameSpace, name, version, true)
      c match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveConcept", null, ErrorCodeConstants.Remove_Concept_Failed_Not_Available + ":" + dispkey)
          apiResult.toString()
        case Some(cs) =>
          val concept = cs.asInstanceOf[AttributeDef]
          DeleteObject(concept)
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveConcept", null, ErrorCodeConstants.Remove_Concept_Successful + ":" + dispkey) //JsonSerializer.SerializeObjectListToJson(concept))
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Concept_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def AddFunction(functionDef: FunctionDef): String = {
    val key = functionDef.FullNameWithVer
    val dispkey = functionDef.FullName + "." + MdMgr.Pad0s2Version(functionDef.Version)
    try {
      val value = JsonSerializer.SerializeObjectToJson(functionDef)

      logger.debug("key => " + key + ",value =>" + value);
      SaveObject(functionDef, MdMgr.GetMdMgr)
      logger.debug("Added function " + key + " successfully ")
      val apiResult = new ApiResult(ErrorCodeConstants.Success, "AddFunction", null, ErrorCodeConstants.Add_Function_Successful + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: Exception => {
        val apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddFunction", null, ErrorCodeConstants.Add_Function_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def RemoveFunction(functionDef: FunctionDef): String = {
    var key = functionDef.typeString
    val dispkey = functionDef.FullName + "." + MdMgr.Pad0s2Version(functionDef.Version)
    try {
      DeleteObject(functionDef)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveFunction", null, ErrorCodeConstants.Remove_Function_Successfully + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveFunction", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Function_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def RemoveFunction(nameSpace: String, functionName: String, version: Long, userid: Option[String]): String = {
    var key = functionName + ":" + version
    val dispkey = functionName + "." + MdMgr.Pad0s2Version(version)
    if (userid != None) logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.DELETEOBJECT, AuditConstants.FUNCTION, AuditConstants.SUCCESS, "", nameSpace + "." + key)
    try {
      DeleteObject(key, functionStore)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveFunction", null, ErrorCodeConstants.Remove_Function_Successfully + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveFunction", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Function_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def DumpFunctionDef(funcDef: FunctionDef) {
    logger.debug("Name => " + funcDef.Name)
    for (arg <- funcDef.args) {
      logger.debug("arg_name => " + arg.name)
      logger.debug("arg_type => " + arg.Type.tType)
    }
    logger.debug("Json string => " + JsonSerializer.SerializeObjectToJson(funcDef))
  }

  def UpdateFunction(functionDef: FunctionDef): String = {
    val key = functionDef.typeString
    val dispkey = key // This does not have version at this moment
    try {
      if (IsFunctionAlreadyExists(functionDef)) {
        functionDef.ver = functionDef.ver + 1
      }
      AddFunction(functionDef)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "UpdateFunction", null, ErrorCodeConstants.Update_Function_Successful + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Failed to update the function, key => " + key + ",Error => " + e.getMessage())
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateFunction", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Function_Failed + ":" + dispkey)
        apiResult.toString()
      }
      case e: Exception => {
        logger.error("Failed to up the type, json => " + key + ",Error => " + e.getMessage())
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateFunction", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Function_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  private def CheckForMissingJar(obj: BaseElemDef): Array[String] = {
    val missingJars = scala.collection.mutable.Set[String]()

    var allJars = GetDependantJars(obj)
    if (allJars.length > 0) {
      val tmpJarPaths = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS")
      val jarPaths = if (tmpJarPaths != null) tmpJarPaths.split(",").toSet else scala.collection.immutable.Set[String]()
      jarPaths.foreach(jardir => {
        val dir = new File(jardir)
        if (!dir.exists()) {
          // attempt to create the missing directory
          dir.mkdir();
        }
      })

      val dirPath = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_TARGET_DIR")
      val dir = new File(dirPath)
      if (!dir.exists()) {
        // attempt to create the missing directory
        dir.mkdir();
      }

      allJars.foreach(jar => {
        val jarName = Utils.GetValidJarFile(jarPaths, jar)
        val f = new File(jarName)
        if (f.exists()) {
          // Nothing to do
        } else {
          try {
            val mObj = GetObject(jar, jarStore)
            // Nothing to do after getting the object.
          } catch {
            case e: Exception => {
              missingJars += jar
            }
          }
        }
      })
    }

    missingJars.toArray
  }

  def AddFunctions(functionsText: String, format: String, userid: Option[String]): String = {
    logger.debug("Started AddFunctions => ")
    var aggFailures: String = ""
    try {
      if (format != "JSON") {
        var apiResult = new ApiResult(ErrorCodeConstants.Not_Implemented_Yet, "AddFunctions", functionsText, ErrorCodeConstants.Not_Implemented_Yet_Msg)
        apiResult.toString()
      } else {
        var funcList = JsonSerializer.parseFunctionList(functionsText, "JSON")
        // Check for the Jars
        val missingJars = scala.collection.mutable.Set[String]()
        funcList.foreach(func => {
          logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.INSERTOBJECT, functionsText, AuditConstants.SUCCESS, "", func.FullNameWithVer)
          SaveObject(func, MdMgr.GetMdMgr)
          missingJars ++= CheckForMissingJar(func)
        })
        if (missingJars.size > 0) {
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddFunctions", null, "Error : Not found required jars " + missingJars.mkString(",") + "\n" + ErrorCodeConstants.Add_Function_Failed + ":" + functionsText)
          return apiResult.toString()
        }
        val alreadyCheckedJars = scala.collection.mutable.Set[String]()
        funcList.foreach(func => {
          UploadJarsToDB(func, false, alreadyCheckedJars)
          if (!SaveObject(func, MdMgr.GetMdMgr)) {
            if (!aggFailures.equalsIgnoreCase("")) aggFailures = aggFailures + ","
            aggFailures = aggFailures + func.FullNameWithVer
          }
        })
        /*
        val objectsAdded = funcList.map(f => f.asInstanceOf[BaseElemDef])
        val operations = funcList.map(f => "Add")
        NotifyEngine(objectsAdded, operations)
*/
        if (funcList.size > 0)
          PutTranId(funcList(0).tranId)
        if (!aggFailures.equalsIgnoreCase("")) {
          (new ApiResult(ErrorCodeConstants.Warning, "AddFunctions", aggFailures, ErrorCodeConstants.Add_Function_Warning)).toString()
        } else {
          (new ApiResult(ErrorCodeConstants.Success, "AddFunctions", functionsText, ErrorCodeConstants.Add_Function_Successful)).toString()
        }
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddFunctions", functionsText, "Error :" + e.toString() + ErrorCodeConstants.Add_Function_Failed)
        apiResult.toString()
      }
    }
  }

  def UpdateFunctions(functionsText: String, format: String, userid: Option[String]): String = {
    logger.debug("Started UpdateFunctions => ")
    try {
      if (format != "JSON") {
        var apiResult = new ApiResult(ErrorCodeConstants.Not_Implemented_Yet, "UpdateFunctions", null, ErrorCodeConstants.Not_Implemented_Yet_Msg + ":" + functionsText + ".Format not JSON.")
        apiResult.toString()
      } else {
        var funcList = JsonSerializer.parseFunctionList(functionsText, "JSON")
        // Check for the Jars
        val missingJars = scala.collection.mutable.Set[String]()
        funcList.foreach(func => {
          missingJars ++= CheckForMissingJar(func)
        })
        if (missingJars.size > 0) {
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateFunctions", null, "Error : Not found required jars " + missingJars.mkString(",") + "\n" + ErrorCodeConstants.Update_Function_Failed + ":" + functionsText)
          return apiResult.toString()
        }
        val alreadyCheckedJars = scala.collection.mutable.Set[String]()
        funcList.foreach(func => {
          logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.UPDATEOBJECT, functionsText, AuditConstants.SUCCESS, "", func.FullNameWithVer)
          UploadJarsToDB(func, false, alreadyCheckedJars)
          UpdateFunction(func)
        })
        /*
        val objectsAdded = funcList.map(f => f.asInstanceOf[BaseElemDef])
        val operations = funcList.map(f => "Add")
        NotifyEngine(objectsAdded, operations)
*/
        if (funcList.size > 0)
          PutTranId(funcList(0).tranId)
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "UpdateFunctions", null, ErrorCodeConstants.Update_Function_Successful + ":" + functionsText)
        apiResult.toString()
      }
    } catch {
      case e: MappingException => {
        logger.error("Failed to parse the function, json => " + functionsText + ",Error => " + e.getMessage())
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateFunctions", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Function_Failed + ":" + functionsText)
        apiResult.toString()
      }
      case e: AlreadyExistsException => {
        logger.error("Failed to add the function, json => " + functionsText + ",Error => " + e.getMessage())
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateFunctions", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Function_Failed + ":" + functionsText)
        apiResult.toString()
      }
      case e: Exception => {
        logger.error("Failed to up the function, json => " + functionsText + ",Error => " + e.getMessage())
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateFunctions", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Function_Failed + ":" + functionsText)
        apiResult.toString()
      }
    }
  }

  def AddDerivedConcept(conceptsText: String, format: String): String = {
    try {
      if (format != "JSON") {
        var apiResult = new ApiResult(ErrorCodeConstants.Not_Implemented_Yet, "AddDerivedConcept", null, ErrorCodeConstants.Not_Implemented_Yet_Msg + ":" + conceptsText + ".Format not JSON.")
        apiResult.toString()
      } else {
        var concept = JsonSerializer.parseDerivedConcept(conceptsText, format)
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddDerivedConcept", null, ErrorCodeConstants.Add_Concept_Successful + ":" + conceptsText)
        apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddDerivedConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Concept_Failed + ":" + conceptsText)
        apiResult.toString()
      }
    }
  }

  def AddConcepts(conceptsText: String, format: String, userid: Option[String]): String = {
    try {
      if (format != "JSON") {
        var apiResult = new ApiResult(ErrorCodeConstants.Not_Implemented_Yet, "AddConcepts", null, ErrorCodeConstants.Not_Implemented_Yet_Msg + ":" + conceptsText + ".Format not JSON.")
        apiResult.toString()
      } else {
        var conceptList = JsonSerializer.parseConceptList(conceptsText, format)
        conceptList.foreach(concept => {
          //logger.debug("Save concept object " + JsonSerializer.SerializeObjectToJson(concept))
          logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.INSERTOBJECT, conceptsText, AuditConstants.SUCCESS, "", concept.FullNameWithVer)
          SaveObject(concept, MdMgr.GetMdMgr)
        })
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddConcepts", null, ErrorCodeConstants.Add_Concept_Successful + ":" + conceptsText)
        apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddConcepts", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Concept_Failed + ":" + conceptsText)
        apiResult.toString()
      }
    }
  }

  def UpdateConcept(concept: BaseAttributeDef): String = {
    val key = concept.FullNameWithVer
    val dispkey = concept.FullName + "." + MdMgr.Pad0s2Version(concept.Version)
    try {
      if (IsConceptAlreadyExists(concept)) {
        concept.ver = concept.ver + 1
      }
      AddConcept(concept)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "UpdateConcept", null, ErrorCodeConstants.Update_Concept_Successful + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Failed to update the concept, key => " + key + ",Error => " + e.getMessage())
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Concept_Failed + ":" + dispkey)
        apiResult.toString()
      }
      case e: Exception => {
        logger.error("Failed to update the concept, key => " + key + ",Error => " + e.getMessage())
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Concept_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def UpdateConcepts(conceptsText: String, format: String, userid: Option[String]): String = {
    try {
      if (format != "JSON") {
        var apiResult = new ApiResult(ErrorCodeConstants.Not_Implemented_Yet, "UpdateConcepts", null, ErrorCodeConstants.Not_Implemented_Yet_Msg + ":" + conceptsText + ".Format not JSON.")
        apiResult.toString()
      } else {
        var conceptList = JsonSerializer.parseConceptList(conceptsText, "JSON")
        conceptList.foreach(concept => {
          logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.UPDATEOBJECT, conceptsText, AuditConstants.SUCCESS, "", concept.FullNameWithVer)
          UpdateConcept(concept)
        })
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "UpdateConcepts", null, ErrorCodeConstants.Update_Concept_Successful + ":" + conceptsText)
        apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateConcepts", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Concept_Failed + ":" + conceptsText)
        apiResult.toString()
      }
    }
  }

  // RemoveConcepts take all concepts names to be removed as an Array
  def RemoveConcepts(concepts: Array[String], userid: Option[String]): String = {
    val json = ("ConceptList" -> concepts.toList)
    val jsonStr = pretty(render(json))
    //  logAuditRec(userid,Some(AuditConstants.WRITE),AuditConstants.UPDATEOBJECT,AuditConstants.CONCEPT,AuditConstants.SUCCESS,"",concepts.mkString(",")) 
    try {
      concepts.foreach(c => { RemoveConcept(c, None) })
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveConcepts", null, ErrorCodeConstants.Remove_Concept_Successful + ":" + jsonStr)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveConcepts", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Concept_Failed + ":" + jsonStr)
        apiResult.toString()
      }
    }
  }

  def AddContainerDef(contDef: ContainerDef, recompile: Boolean = false): String = {
    var key = contDef.FullNameWithVer
    val dispkey = contDef.FullName + "." + MdMgr.Pad0s2Version(contDef.Version)
    try {
      AddObjectToCache(contDef, MdMgr.GetMdMgr)
      UploadJarsToDB(contDef)
      var objectsAdded = AddMessageTypes(contDef, MdMgr.GetMdMgr, recompile)
      objectsAdded = objectsAdded :+ contDef
      SaveObjectList(objectsAdded, metadataStore)
      val operations = for (op <- objectsAdded) yield "Add"
      NotifyEngine(objectsAdded, operations)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddContainerDef", null, ErrorCodeConstants.Add_Container_Successful + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddContainerDef", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Container_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def AddMessageDef(msgDef: MessageDef, recompile: Boolean = false): String = {
    val dispkey = msgDef.FullName + "." + MdMgr.Pad0s2Version(msgDef.Version)
    try {
      AddObjectToCache(msgDef, MdMgr.GetMdMgr)
      UploadJarsToDB(msgDef)
      var objectsAdded = AddMessageTypes(msgDef, MdMgr.GetMdMgr, recompile)
      objectsAdded = objectsAdded :+ msgDef
      SaveObjectList(objectsAdded, metadataStore)
      val operations = for (op <- objectsAdded) yield "Add"
      NotifyEngine(objectsAdded, operations)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddMessageDef", null, ErrorCodeConstants.Add_Message_Successful + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddMessageDef", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Message_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  // As per Rich's requirement, Add array/arraybuf/sortedset types for this messageDef
  // along with the messageDef.  
  def AddMessageTypes(msgDef: BaseElemDef, mdMgr: MdMgr, recompile: Boolean = false): Array[BaseElemDef] = {
    logger.debug("The class name => " + msgDef.getClass().getName())
    try {
      var types = new Array[BaseElemDef](0)
      val msgType = getObjectType(msgDef)
      val depJars = if (msgDef.DependencyJarNames != null)
        (msgDef.DependencyJarNames :+ msgDef.JarName) else Array(msgDef.JarName)
      msgType match {
        case "MessageDef" | "ContainerDef" => {
          // ArrayOf<TypeName>
          var obj: BaseElemDef = mdMgr.MakeArray(msgDef.nameSpace, "arrayof" + msgDef.name, msgDef.nameSpace, msgDef.name, 1, msgDef.ver, recompile)
          obj.dependencyJarNames = depJars
          AddObjectToCache(obj, mdMgr)
          types = types :+ obj
          // ArrayBufferOf<TypeName>
          obj = mdMgr.MakeArrayBuffer(msgDef.nameSpace, "arraybufferof" + msgDef.name, msgDef.nameSpace, msgDef.name, 1, msgDef.ver, recompile)
          obj.dependencyJarNames = depJars
          AddObjectToCache(obj, mdMgr)
          types = types :+ obj
          // SortedSetOf<TypeName>
          obj = mdMgr.MakeSortedSet(msgDef.nameSpace, "sortedsetof" + msgDef.name, msgDef.nameSpace, msgDef.name, msgDef.ver, recompile)
          obj.dependencyJarNames = depJars
          AddObjectToCache(obj, mdMgr)
          types = types :+ obj
          // ImmutableMapOfIntArrayOf<TypeName>
          obj = mdMgr.MakeImmutableMap(msgDef.nameSpace, "immutablemapofintarrayof" + msgDef.name, ("System", "Int"), (msgDef.nameSpace, "arrayof" + msgDef.name), msgDef.ver, recompile)
          obj.dependencyJarNames = depJars
          AddObjectToCache(obj, mdMgr)
          types = types :+ obj
          // ImmutableMapOfString<TypeName>
          obj = mdMgr.MakeImmutableMap(msgDef.nameSpace, "immutablemapofstringarrayof" + msgDef.name, ("System", "String"), (msgDef.nameSpace, "arrayof" + msgDef.name), msgDef.ver, recompile)
          obj.dependencyJarNames = depJars
          AddObjectToCache(obj, mdMgr)
          types = types :+ obj
          // ArrayOfArrayOf<TypeName>
          obj = mdMgr.MakeArray(msgDef.nameSpace, "arrayofarrayof" + msgDef.name, msgDef.nameSpace, "arrayof" + msgDef.name, 1, msgDef.ver, recompile)
          obj.dependencyJarNames = depJars
          AddObjectToCache(obj, mdMgr)
          types = types :+ obj
          // MapOfStringArrayOf<TypeName>
          obj = mdMgr.MakeMap(msgDef.nameSpace, "mapofstringarrayof" + msgDef.name, ("System", "String"), ("System", "arrayof" + msgDef.name), msgDef.ver, recompile)
          obj.dependencyJarNames = depJars
          AddObjectToCache(obj, mdMgr)
          types = types :+ obj
          // MapOfIntArrayOf<TypeName>
          obj = mdMgr.MakeMap(msgDef.nameSpace, "mapofintarrayof" + msgDef.name, ("System", "Int"), ("System", "arrayof" + msgDef.name), msgDef.ver, recompile)
          obj.dependencyJarNames = depJars
          AddObjectToCache(obj, mdMgr)
          types = types :+ obj
          // SetOf<TypeName>
          obj = mdMgr.MakeSet(msgDef.nameSpace, "setof" + msgDef.name, msgDef.nameSpace, msgDef.name, msgDef.ver, recompile)
          obj.dependencyJarNames = depJars
          AddObjectToCache(obj, mdMgr)
          types = types :+ obj
          // TreeSetOf<TypeName>
          obj = mdMgr.MakeTreeSet(msgDef.nameSpace, "treesetof" + msgDef.name, msgDef.nameSpace, msgDef.name, msgDef.ver, recompile)
          obj.dependencyJarNames = depJars
          AddObjectToCache(obj, mdMgr)
          types = types :+ obj
          types
        }
        case _ => {
          throw new InternalErrorException("Unknown class in AddMessageTypes")
        }
      }
    } catch {
      case e: Exception => {
        throw new Exception(e.getMessage())
      }
    }
  }

  private def AddContainerOrMessage(contOrMsgText: String, format: String, userid: Option[String], recompile: Boolean = false): String = {
    var resultStr: String = ""
    try {
      var compProxy = new CompilerProxy
      compProxy.setLoggerLevel(Level.TRACE)
      val (classStrVer, cntOrMsgDef, classStrNoVer) = compProxy.compileMessageDef(contOrMsgText, recompile)
      logger.debug("Message/Container Compiler returned an object of type " + cntOrMsgDef.getClass().getName())
      cntOrMsgDef match {
        case msg: MessageDef => {
          logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.INSERTOBJECT, contOrMsgText, AuditConstants.SUCCESS, "", msg.FullNameWithVer)
          // Make sure we are allowed to add this version.
          val latestVersion = GetLatestMessage(msg)
          var isValid = true
          if (latestVersion != None) {
            isValid = IsValidVersion(latestVersion.get, msg)
          }
          if (!isValid) {
            var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateMessage", null, ErrorCodeConstants.Update_Message_Failed + ":" + msg.Name + " Error:Invalid Version")
            apiResult.toString()
          }

          if (recompile) {
            // Incase of recompile, Message Compiler is automatically incrementing the previous version
            // by 1. Before Updating the metadata with the new version, remove the old version
            val latestVersion = GetLatestMessage(msg)
            RemoveMessage(latestVersion.get.nameSpace, latestVersion.get.name, latestVersion.get.ver, None)
            resultStr = AddMessageDef(msg, recompile)
          } else {
            resultStr = AddMessageDef(msg, recompile)
          }

          if (recompile) {
            val depModels = GetDependentModels(msg.NameSpace, msg.Name, msg.ver)
            if (depModels.length > 0) {
              depModels.foreach(mod => {
                logger.debug("DependentModel => " + mod.FullNameWithVer)
                resultStr = resultStr + RecompileModel(mod)
              })
            }
          }
          resultStr
        }
        case cont: ContainerDef => {
          logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.INSERTOBJECT, contOrMsgText, AuditConstants.SUCCESS, "", cont.FullNameWithVer)
          // Make sure we are allowed to add this version.
          val latestVersion = GetLatestContainer(cont)
          var isValid = true
          if (latestVersion != None) {
            isValid = IsValidVersion(latestVersion.get, cont)
          }
          if (!isValid) {
            var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateMessage", null, ErrorCodeConstants.Update_Message_Failed + ":" + cont.Name + " Error:Invalid Version")
            apiResult.toString()
          }

          if (recompile) {
            // Incase of recompile, Message Compiler is automatically incrementing the previous version
            // by 1. Before Updating the metadata with the new version, remove the old version
            val latestVersion = GetLatestContainer(cont)
            RemoveContainer(latestVersion.get.nameSpace, latestVersion.get.name, latestVersion.get.ver, None)
            resultStr = AddContainerDef(cont, recompile)
          } else {
            resultStr = AddContainerDef(cont, recompile)
          }

          if (recompile) {
            val depModels = GetDependentModels(cont.NameSpace, cont.Name, cont.ver)
            if (depModels.length > 0) {
              depModels.foreach(mod => {
                logger.debug("DependentModel => " + mod.FullNameWithVer)
                resultStr = resultStr + RecompileModel(mod)
              })
            }
          }
          resultStr
        }
      }
    } catch {
      case e: ModelCompilationFailedException => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddContainerOrMessage", contOrMsgText, "Error: " + e.toString + ErrorCodeConstants.Add_Container_Or_Message_Failed)
        apiResult.toString()
      }
      case e: MsgCompilationFailedException => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddContainerOrMessage", contOrMsgText, "Error: " + e.toString + ErrorCodeConstants.Add_Container_Or_Message_Failed)
        apiResult.toString()
      }
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddContainerOrMessage", contOrMsgText, "Error: " + e.toString + ErrorCodeConstants.Add_Container_Or_Message_Failed)
        apiResult.toString()
      }
    }
  }

  def AddMessage(messageText: String, format: String, userid: Option[String]): String = {
    AddContainerOrMessage(messageText, format, userid)
  }

  def AddMessage(messageText: String, userid: Option[String]): String = {
    AddMessage(messageText, "JSON", userid)
  }

  def AddContainer(containerText: String, format: String, userid: Option[String]): String = {
    AddContainerOrMessage(containerText, format, userid)
  }

  def AddContainer(containerText: String, userid: Option[String]): String = {
    AddContainer(containerText, "JSON", userid)
  }

  def RecompileMessage(msgFullName: String): String = {
    var resultStr: String = ""
    try {
      var messageText: String = null

      val latestMsgDef = MdMgr.GetMdMgr.Message(msgFullName, -1, true)
      if (latestMsgDef == None) {
        val latestContDef = MdMgr.GetMdMgr.Container(msgFullName, -1, true)
        if (latestContDef == None) {
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RecompileMessage", null, ErrorCodeConstants.Recompile_Message_Failed + ":" + msgFullName + " Error:No message or container named ")
          return apiResult.toString()
        } else {
          messageText = latestContDef.get.objectDefinition
        }
      } else {
        messageText = latestMsgDef.get.objectDefinition
      }
      resultStr = AddContainerOrMessage(messageText, "JSON", None, true)
      resultStr

    } catch {
      case e: MsgCompilationFailedException => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RecompileMessage", null, "Error :" + e.toString() + ErrorCodeConstants.Recompile_Message_Failed + ":" + msgFullName)
        apiResult.toString()
      }
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RecompileMessage", null, "Error :" + e.toString() + ErrorCodeConstants.Recompile_Message_Failed + ":" + msgFullName)
        apiResult.toString()
      }
    }
  }

  def UpdateMessage(messageText: String, format: String, userid: Option[String]): String = {
    var resultStr: String = ""
    try {
      var compProxy = new CompilerProxy
      compProxy.setLoggerLevel(Level.TRACE)
      val (classStrVer, msgDef, classStrNoVer) = compProxy.compileMessageDef(messageText)
      val key = msgDef.FullNameWithVer
      msgDef match {
        case msg: MessageDef => {
          logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.UPDATEOBJECT, messageText, AuditConstants.SUCCESS, "", msg.FullNameWithVer)
          val latestVersion = GetLatestMessage(msg)
          var isValid = true
          if (latestVersion != None) {
            isValid = IsValidVersion(latestVersion.get, msg)
          }
          if (isValid) {
            RemoveMessage(latestVersion.get.nameSpace, latestVersion.get.name, latestVersion.get.ver, None)
            resultStr = AddMessageDef(msg)

            logger.debug("Check for dependent messages ...")
            val depMessages = GetDependentMessages.getDependentObjects(msg)
            if (depMessages.length > 0) {
              depMessages.foreach(msg => {
                logger.debug("DependentMessage => " + msg)
                resultStr = resultStr + RecompileMessage(msg)
              })
            }
            val depModels = GetDependentModels(msg.NameSpace, msg.Name, msg.Version.toLong)
            if (depModels.length > 0) {
              depModels.foreach(mod => {
                logger.debug("DependentModel => " + mod.FullNameWithVer)
                resultStr = resultStr + RecompileModel(mod)
              })
            }
            resultStr
          } else {
            var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateMessage", messageText, ErrorCodeConstants.Update_Message_Failed + " Error:Invalid Version")
            apiResult.toString()
          }
        }
        case msg: ContainerDef => {
          logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.UPDATEOBJECT, messageText, AuditConstants.SUCCESS, "", msg.FullNameWithVer)
          val latestVersion = GetLatestContainer(msg)
          var isValid = true
          if (latestVersion != None) {
            isValid = IsValidVersion(latestVersion.get, msg)
          }
          if (isValid) {
            RemoveContainer(latestVersion.get.nameSpace, latestVersion.get.name, latestVersion.get.ver, None)
            resultStr = AddContainerDef(msg)

            val depMessages = GetDependentMessages.getDependentObjects(msg)
            if (depMessages.length > 0) {
              depMessages.foreach(msg => {
                logger.debug("DependentMessage => " + msg)
                resultStr = resultStr + RecompileMessage(msg)
              })
            }
            val depModels = MetadataAPIImpl.GetDependentModels(msg.NameSpace, msg.Name, msg.Version.toLong)
            if (depModels.length > 0) {
              depModels.foreach(mod => {
                logger.debug("DependentModel => " + mod.FullName + "." + MdMgr.Pad0s2Version(mod.Version))
                resultStr = resultStr + RecompileModel(mod)
              })
            }
            resultStr
          } else {
            var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateMessage", messageText, ErrorCodeConstants.Update_Message_Failed + " Error:Invalid Version")
            apiResult.toString()
          }
        }
      }
    } catch {
      case e: MsgCompilationFailedException => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateMessage", messageText, "Error :" + e.toString() + ErrorCodeConstants.Update_Message_Failed)
        apiResult.toString()
      }
      case e: ObjectNotFoundException => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateMessage", messageText, "Error :" + e.toString() + ErrorCodeConstants.Update_Message_Failed)
        apiResult.toString()
      }
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateMessage", messageText, "Error :" + e.toString() + ErrorCodeConstants.Update_Message_Failed)
        apiResult.toString()
      }
    }
  }

  def UpdateContainer(messageText: String, format: String, userid: Option[String]): String = {
    UpdateMessage(messageText, format, userid)
  }

  def UpdateContainer(messageText: String, userid: Option[String]): String = {
    UpdateMessage(messageText, "JSON", userid)
  }

  def UpdateMessage(messageText: String, userid: Option[String]): String = {
    UpdateMessage(messageText, "JSON", userid)
  }

  /**
   * UpdateCompiledContainer - called from a few places to update a compiled ContainerDef
   */
  private def UpdateCompiledContainer(msg: ContainerDef, latestVersion: Option[ContainerDef], key: String): String = {
    var isValid = true
    if (latestVersion != None) {
      isValid = IsValidVersion(latestVersion.get, msg)
    }
    if (isValid) {
      RemoveContainer(latestVersion.get.nameSpace, latestVersion.get.name, latestVersion.get.ver, None)
      AddContainerDef(msg)
    } else {
      var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateCompiledContainer", null, "Error : Failed to update compiled Container")
      apiResult.toString()
    }
  }

  /**
   * UpdateCompiledContainer - called from a few places to update a compiled ContainerDef
   */
  private def UpdateCompiledMessage(msg: MessageDef, latestVersion: Option[MessageDef], key: String): String = {
    var isValid = true
    if (latestVersion != None) {
      isValid = IsValidVersion(latestVersion.get, msg)
    }
    if (isValid) {
      RemoveMessage(latestVersion.get.nameSpace, latestVersion.get.name, latestVersion.get.ver, None)
      AddMessageDef(msg)
    } else {
      var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateCompiledMessage", null, "Error : Failed to update compiled Message")
      apiResult.toString()
    }
  }

  // Remove container with Container Name and Version Number
  def RemoveContainer(nameSpace: String, name: String, version: Long, userid: Option[String], zkNotify: Boolean = true): String = {
    var key = nameSpace + "." + name + "." + version
    val dispkey = nameSpace + "." + name + "." + MdMgr.Pad0s2Version(version)
    var newTranId = GetNewTranId
    if (userid != None) logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.DELETEOBJECT, "Container", AuditConstants.SUCCESS, "", key)
    try {
      val o = MdMgr.GetMdMgr.Container(nameSpace.toLowerCase, name.toLowerCase, version, true)
      o match {
        case None =>
          None
          logger.debug("container not found => " + key)
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveContainer", null, ErrorCodeConstants.Remove_Container_Failed_Not_Found + ":" + dispkey)
          apiResult.toString()
        case Some(m) =>
          logger.debug("container found => " + m.asInstanceOf[ContainerDef].FullName + "." + MdMgr.Pad0s2Version(m.asInstanceOf[ContainerDef].Version))
          val contDef = m.asInstanceOf[ContainerDef]
          var objectsToBeRemoved = GetAdditionalTypesAdded(contDef, MdMgr.GetMdMgr)
          // Also remove a type with same name as messageDef
          var typeName = name
          var typeDef = GetType(nameSpace, typeName, version.toString, "JSON", None)
          if (typeDef != None) {
            objectsToBeRemoved = objectsToBeRemoved :+ typeDef.get
          }
          objectsToBeRemoved.foreach(typ => {
            typ.tranId = newTranId
            RemoveType(typ.nameSpace, typ.name, typ.ver, None)
          })
          // ContainerDef itself
          contDef.tranId = newTranId
          DeleteObject(contDef)
          var allObjectsArray = objectsToBeRemoved :+ contDef

          val operations = for (op <- allObjectsArray) yield "Remove"
          NotifyEngine(allObjectsArray, operations)

          var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveContainer", null, ErrorCodeConstants.Remove_Container_Successful + ":" + dispkey)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveContainer", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Container_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  // Remove message with Message Name and Version Number
  def RemoveMessage(nameSpace: String, name: String, version: Long, userid: Option[String], zkNotify: Boolean = true): String = {
    var key = nameSpace + "." + name + "." + version
    val dispkey = nameSpace + "." + name + "." + MdMgr.Pad0s2Version(version)

    var newTranId = GetNewTranId
    if (userid != None) logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.DELETEOBJECT, AuditConstants.MESSAGE, AuditConstants.SUCCESS, "", key)
    try {
      val o = MdMgr.GetMdMgr.Message(nameSpace.toLowerCase, name.toLowerCase, version, true)
      o match {
        case None =>
          None
          logger.debug("Message not found => " + key)
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveMessage", null, ErrorCodeConstants.Remove_Message_Failed_Not_Found + ":" + dispkey)
          apiResult.toString()
        case Some(m) =>
          val msgDef = m.asInstanceOf[MessageDef]
          logger.debug("message found => " + msgDef.FullName + "." + MdMgr.Pad0s2Version(msgDef.Version))
          var objectsToBeRemoved = GetAdditionalTypesAdded(msgDef, MdMgr.GetMdMgr)

          // Also remove a type with same name as messageDef
          var typeName = name
          var typeDef = GetType(nameSpace, typeName, version.toString, "JSON", None)

          if (typeDef != None) {
            objectsToBeRemoved = objectsToBeRemoved :+ typeDef.get
          }

          objectsToBeRemoved.foreach(typ => {
            typ.tranId = newTranId
            RemoveType(typ.nameSpace, typ.name, typ.ver, None)
          })

          // MessageDef itself - add it to the list of other objects to be passed to the zookeeper
          // to notify other instnances
          msgDef.tranId = newTranId
          DeleteObject(msgDef)
          var allObjectsArray = objectsToBeRemoved :+ msgDef

          val operations = for (op <- allObjectsArray) yield "Remove"
          NotifyEngine(allObjectsArray, operations)

          var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveMessage", null, ErrorCodeConstants.Remove_Message_Successful + ":" + dispkey)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveMessage", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Message_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def GetAdditionalTypesAdded(msgDef: BaseElemDef, mdMgr: MdMgr): Array[BaseElemDef] = {
    var types = new Array[BaseElemDef](0)
    logger.debug("The class name => " + msgDef.getClass().getName())
    try {
      val msgType = getObjectType(msgDef)
      msgType match {
        case "MessageDef" | "ContainerDef" => {
          // ArrayOf<TypeName>
          var typeName = "arrayof" + msgDef.name
          var typeDef = GetType(msgDef.nameSpace, typeName, msgDef.ver.toString, "JSON", None)
          if (typeDef != None) {
            types = types :+ typeDef.get
          }
          // ArrayBufferOf<TypeName>
          typeName = "arraybufferof" + msgDef.name
          typeDef = GetType(msgDef.nameSpace, typeName, msgDef.ver.toString, "JSON", None)
          if (typeDef != None) {
            types = types :+ typeDef.get
          }
          // SortedSetOf<TypeName>
          typeName = "sortedsetof" + msgDef.name
          typeDef = GetType(msgDef.nameSpace, typeName, msgDef.ver.toString, "JSON", None)
          if (typeDef != None) {
            types = types :+ typeDef.get
          }
          // ImmutableMapOfIntArrayOf<TypeName>
          typeName = "immutablemapofintarrayof" + msgDef.name
          typeDef = GetType(msgDef.nameSpace, typeName, msgDef.ver.toString, "JSON", None)
          if (typeDef != None) {
            types = types :+ typeDef.get
          }
          // ImmutableMapOfString<TypeName>
          typeName = "immutablemapofstringarrayof" + msgDef.name
          typeDef = GetType(msgDef.nameSpace, typeName, msgDef.ver.toString, "JSON", None)
          if (typeDef != None) {
            types = types :+ typeDef.get
          }
          // ArrayOfArrayOf<TypeName>
          typeName = "arrayofarrayof" + msgDef.name
          typeDef = GetType(msgDef.nameSpace, typeName, msgDef.ver.toString, "JSON", None)
          if (typeDef != None) {
            types = types :+ typeDef.get
          }
          // MapOfStringArrayOf<TypeName>
          typeName = "mapofstringarrayof" + msgDef.name
          typeDef = GetType(msgDef.nameSpace, typeName, msgDef.ver.toString, "JSON", None)
          if (typeDef != None) {
            types = types :+ typeDef.get
          }
          // MapOfIntArrayOf<TypeName>
          typeName = "mapofintarrayof" + msgDef.name
          typeDef = GetType(msgDef.nameSpace, typeName, msgDef.ver.toString, "JSON", None)
          if (typeDef != None) {
            types = types :+ typeDef.get
          }
          // SetOf<TypeName>
          typeName = "setof" + msgDef.name
          typeDef = GetType(msgDef.nameSpace, typeName, msgDef.ver.toString, "JSON", None)
          if (typeDef != None) {
            types = types :+ typeDef.get
          }
          // TreeSetOf<TypeName>
          typeName = "treesetof" + msgDef.name
          typeDef = GetType(msgDef.nameSpace, typeName, msgDef.ver.toString, "JSON", None)
          if (typeDef != None) {
            types = types :+ typeDef.get
          }
          logger.debug("Type objects to be removed = " + types.length)
          types
        }
        case _ => {
          throw new InternalErrorException("Unknown class in AddMessageTypes")
        }
      }
    } catch {
      case e: Exception => {
        throw new Exception(e.getMessage())
      }
    }
  }

  // Remove message with Message Name and Version Number
  def RemoveMessageFromCache(zkMessage: ZooKeeperNotification) = {
    try {
      var key = zkMessage.NameSpace + "." + zkMessage.Name + "." + zkMessage.Version
      val dispkey = zkMessage.NameSpace + "." + zkMessage.Name + "." + MdMgr.Pad0s2Version(zkMessage.Version.toLong)
      val o = MdMgr.GetMdMgr.Message(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, true)
      o match {
        case None =>
          None
          logger.debug("Message not found, Already Removed? => " + dispkey)
        case Some(m) =>
          val msgDef = m.asInstanceOf[MessageDef]
          logger.debug("message found => " + msgDef.FullName + "." + MdMgr.Pad0s2Version(msgDef.Version))
          val types = GetAdditionalTypesAdded(msgDef, MdMgr.GetMdMgr)

          var typeName = zkMessage.Name
          MdMgr.GetMdMgr.RemoveType(zkMessage.NameSpace, typeName, zkMessage.Version.toLong)
          typeName = "arrayof" + zkMessage.Name
          MdMgr.GetMdMgr.RemoveType(zkMessage.NameSpace, typeName, zkMessage.Version.toLong)
          typeName = "sortedsetof" + zkMessage.Name
          MdMgr.GetMdMgr.RemoveType(zkMessage.NameSpace, typeName, zkMessage.Version.toLong)
          typeName = "arraybufferof" + zkMessage.Name
          MdMgr.GetMdMgr.RemoveType(zkMessage.NameSpace, typeName, zkMessage.Version.toLong)
          MdMgr.GetMdMgr.RemoveMessage(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong)
      }
    } catch {
      case e: Exception => {
        logger.error("Failed to delete the Message from cache:" + e.toString)
      }
    }
  }

  def RemoveContainerFromCache(zkMessage: ZooKeeperNotification) = {
    try {
      var key = zkMessage.NameSpace + "." + zkMessage.Name + "." + zkMessage.Version
      val dispkey = zkMessage.NameSpace + "." + zkMessage.Name + "." + MdMgr.Pad0s2Version(zkMessage.Version.toLong)
      val o = MdMgr.GetMdMgr.Container(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, true)
      o match {
        case None =>
          None
          logger.debug("Message not found, Already Removed? => " + dispkey)
        case Some(m) =>
          val msgDef = m.asInstanceOf[MessageDef]
          logger.debug("message found => " + msgDef.FullName + "." + MdMgr.Pad0s2Version(msgDef.Version))
          var typeName = zkMessage.Name
          MdMgr.GetMdMgr.RemoveType(zkMessage.NameSpace, typeName, zkMessage.Version.toLong)
          typeName = "arrayof" + zkMessage.Name
          MdMgr.GetMdMgr.RemoveType(zkMessage.NameSpace, typeName, zkMessage.Version.toLong)
          typeName = "sortedsetof" + zkMessage.Name
          MdMgr.GetMdMgr.RemoveType(zkMessage.NameSpace, typeName, zkMessage.Version.toLong)
          typeName = "arraybufferof" + zkMessage.Name
          MdMgr.GetMdMgr.RemoveType(zkMessage.NameSpace, typeName, zkMessage.Version.toLong)
          MdMgr.GetMdMgr.RemoveContainer(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong)
      }
    } catch {
      case e: Exception => {
        logger.error("Failed to delete the Message from cache:" + e.toString)
      }
    }
  }

  // Remove message with Message Name and Version Number
  def RemoveMessage(messageName: String, version: Long, userid: Option[String]): String = {
    RemoveMessage(sysNS, messageName, version, userid)
  }

  // Remove container with Container Name and Version Number
  def RemoveContainer(containerName: String, version: Long, userid: Option[String]): String = {
    RemoveContainer(sysNS, containerName, version, userid)
  }

  /**
   *
   */
  def DeactivateModel(nameSpace: String, name: String, version: Long, userid: Option[String]): String = {
    val dispkey = nameSpace + "." + name + "." + MdMgr.Pad0s2Version(version)
    logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.DEACTIVATEOBJECT, AuditConstants.MODEL, AuditConstants.SUCCESS, "", dispkey)
    if (DeactivateLocalModel(nameSpace, name, version)) {
      (new ApiResult(ErrorCodeConstants.Success, "Deactivate Model", null, ErrorCodeConstants.Deactivate_Model_Successful + ":" + dispkey)).toString
    } else {
      (new ApiResult(ErrorCodeConstants.Failure, "Deactivate Model", null, ErrorCodeConstants.Deactivate_Model_Failed_Not_Active + ":" + dispkey)).toString
    }
  }

  // Remove model with Model Name and Version Number
  private def DeactivateLocalModel(nameSpace: String, name: String, version: Long): Boolean = {
    var key = nameSpace + "." + name + "." + version
    val dispkey = nameSpace + "." + name + "." + MdMgr.Pad0s2Version(version)
    val newTranId = GetNewTranId
    try {
      val o = MdMgr.GetMdMgr.Model(nameSpace.toLowerCase, name.toLowerCase, version, true)
      o match {
        case None =>
          None
          logger.debug("No active model found => " + dispkey)
          false
        case Some(m) =>
          logger.debug("model found => " + m.asInstanceOf[ModelDef].FullName + "." + MdMgr.Pad0s2Version(m.asInstanceOf[ModelDef].Version))
          DeactivateObject(m.asInstanceOf[ModelDef])

          // TODO: Need to deactivate the appropriate message?
          m.tranId = newTranId
          var objectsUpdated = new Array[BaseElemDef](0)
          objectsUpdated = objectsUpdated :+ m.asInstanceOf[ModelDef]
          val operations = for (op <- objectsUpdated) yield "Deactivate"
          NotifyEngine(objectsUpdated, operations)
          true
      }
    } catch {
      case e: Exception => {
        logger.error(e.getStackTrace)
        false
      }
    }
  }

  /**
   *
   */
  def ActivateModel(nameSpace: String, name: String, version: Long, userid: Option[String]): String = {
    var key = nameSpace + "." + name + "." + version
    val dispkey = nameSpace + "." + name + "." + MdMgr.Pad0s2Version(version)
    var currActiveModel: ModelDef = null
    val newTranId = GetNewTranId

    // Audit this call
    logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.ACTIVATEOBJECT, AuditConstants.MODEL, AuditConstants.SUCCESS, "", nameSpace + "." + name + "." + version)

    try {
      // We may need to deactivate an model if something else is active.  Find the active model
      val oCur = MdMgr.GetMdMgr.Models(nameSpace, name, true, false)
      oCur match {
        case None =>
        case Some(m) =>
          var setOfModels = m.asInstanceOf[scala.collection.immutable.Set[ModelDef]]
          if (setOfModels.size > 1) {
            logger.error("Internal Metadata error, there are more then 1 versions of model " + nameSpace + "." + name + " active on this system.")
          }

          // If some model is active, deactivate it.
          if (setOfModels.size != 0) {
            currActiveModel = setOfModels.last
            if (currActiveModel.NameSpace.equalsIgnoreCase(nameSpace) &&
              currActiveModel.name.equalsIgnoreCase(name) &&
              currActiveModel.Version == version) {
              return (new ApiResult(ErrorCodeConstants.Success, "ActivateModel", null, dispkey + " already active")).toString

            }
            var isSuccess = DeactivateLocalModel(currActiveModel.nameSpace, currActiveModel.name, currActiveModel.Version)
            if (!isSuccess) {
              logger.error("Error while trying to activate " + dispkey + ", unable to deactivate active model. model ")
              var apiResult = new ApiResult(ErrorCodeConstants.Failure, "ActivateModel", null, "Error :" + ErrorCodeConstants.Activate_Model_Failed + ":" + dispkey + " -Unable to deactivate existing model")
              apiResult.toString()
            }
          }

      }

      // Ok, at this point, we have deactivate  a previously active model.. now we activate this one.
      val o = MdMgr.GetMdMgr.Model(nameSpace.toLowerCase, name.toLowerCase, version, false)
      o match {
        case None =>
          None
          logger.debug("No active model found => " + dispkey)
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "ActivateModel", null, ErrorCodeConstants.Activate_Model_Failed_Not_Active + ":" + dispkey)
          apiResult.toString()
        case Some(m) =>
          logger.debug("model found => " + m.asInstanceOf[ModelDef].FullName + "." + MdMgr.Pad0s2Version(m.asInstanceOf[ModelDef].Version))
          ActivateObject(m.asInstanceOf[ModelDef])

          // Issue a Notification to all registered listeners that an Acivation took place.
          // TODO: Need to activate the appropriate message?
          var objectsUpdated = new Array[BaseElemDef](0)
          m.tranId = newTranId
          objectsUpdated = objectsUpdated :+ m.asInstanceOf[ModelDef]
          val operations = for (op <- objectsUpdated) yield "Activate"
          NotifyEngine(objectsUpdated, operations)

          // No exceptions, we succeded
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "ActivateModel", null, ErrorCodeConstants.Activate_Model_Successful + ":" + dispkey)
          apiResult.toString()
      }
    } catch {

      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "ActivateModel", null, "Error :" + e.toString() + ErrorCodeConstants.Activate_Model_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  // Remove model with Model Name and Version Number
  def RemoveModel(nameSpace: String, name: String, version: Long, userid: Option[String]): String = {
    var key = nameSpace + "." + name + "." + version
    if (userid != None) logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.DELETEOBJECT, "Model", AuditConstants.SUCCESS, "", key)
    val dispkey = nameSpace + "." + name + "." + MdMgr.Pad0s2Version(version)
    var newTranId = GetNewTranId
    try {
      val o = MdMgr.GetMdMgr.Model(nameSpace.toLowerCase, name.toLowerCase, version, true)
      o match {
        case None =>
          None
          logger.debug("model not found => " + dispkey)
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveModel", null, ErrorCodeConstants.Remove_Model_Failed_Not_Found + ":" + dispkey)
          apiResult.toString()
        case Some(m) =>
          logger.debug("model found => " + m.asInstanceOf[ModelDef].FullName + "." + MdMgr.Pad0s2Version(m.asInstanceOf[ModelDef].Version))
          DeleteObject(m.asInstanceOf[ModelDef])
          var objectsUpdated = new Array[BaseElemDef](0)
          m.tranId = newTranId
          objectsUpdated = objectsUpdated :+ m.asInstanceOf[ModelDef]
          var operations = for (op <- objectsUpdated) yield "Remove"
          NotifyEngine(objectsUpdated, operations)
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveModel", null, ErrorCodeConstants.Remove_Model_Successful + ":" + dispkey)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveModel", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Model_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  // Remove model with Model Name and Version Number
  def RemoveModel(modelName: String, version: Long, userid: Option[String]): String = {
    RemoveModel(sysNS, modelName, version, userid)
  }

  // Add Model (model def)
  def AddModel(model: ModelDef): String = {
    var key = model.FullNameWithVer
    val dispkey = model.FullName + "." + MdMgr.Pad0s2Version(model.Version)
    try {
      SaveObject(model, MdMgr.GetMdMgr)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddModel", null, ErrorCodeConstants.Add_Model_Successful + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddModel", null, ErrorCodeConstants.Add_Model_Successful + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def AddModelFromSource(sourceCode: String, sourceLang: String, modelName: String, userid: Option[String]): String = {

    var compProxy = new CompilerProxy
    compProxy.setSessionUserId(userid)
    val modDef: ModelDef = compProxy.compileModelFromSource(sourceCode, modelName, sourceLang)
    logger.info("Begin uploading dependent Jar, please wait.")
    UploadJarsToDB(modDef)
    logger.info("Finished uploading dependent Jars.")
    val apiResult = AddModel(modDef)

    // Add all the objects and NOTIFY the world
    var objectsAdded = new Array[BaseElemDef](0)
    objectsAdded = objectsAdded :+ modDef
    val operations = for (op <- objectsAdded) yield "Add"
    logger.debug("Notify engine via zookeeper")
    NotifyEngine(objectsAdded, operations)

    apiResult
  }

  // Add Model (format XML)
  def AddModel(pmmlText: String, userid: Option[String]): String = {
    try {
      var compProxy = new CompilerProxy
      compProxy.setLoggerLevel(Level.TRACE)
      var (classStr, modDef) = compProxy.compilePmml(pmmlText)

      // ModelDef may be null if there were pmml compiler errors... act accordingly.  If modelDef present,
      // make sure the version of the model is greater than any of previous models with same FullName
      val latestVersion = if (modDef == null) None else GetLatestModel(modDef)
      val isValid: Boolean = if (latestVersion != None) IsValidVersion(latestVersion.get, modDef) else true

      if (isValid && modDef != null) {
        logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.INSERTOBJECT, pmmlText, AuditConstants.SUCCESS, "", modDef.FullNameWithVer)
        // save the jar file first
        UploadJarsToDB(modDef)
        val apiResult = AddModel(modDef)
        logger.debug("Model is added..")
        var objectsAdded = new Array[BaseElemDef](0)
        objectsAdded = objectsAdded :+ modDef
        val operations = for (op <- objectsAdded) yield "Add"
        logger.debug("Notify engine via zookeeper")
        NotifyEngine(objectsAdded, operations)
        apiResult
      } else {
        val reasonForFailure: String = if (modDef != null) ErrorCodeConstants.Add_Model_Failed_Higher_Version_Required else ErrorCodeConstants.Add_Model_Failed
        val modDefName: String = if (modDef != null) modDef.FullName else "(pmml compile failed)"
        val modDefVer: String = if (modDef != null) MdMgr.Pad0s2Version(modDef.Version) else MdMgr.UnknownVersion
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddModel", null, reasonForFailure + ":" + modDefName + "." + modDefVer)
        apiResult.toString()
      }
    } catch {
      case e: ModelCompilationFailedException => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddModel", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Model_Failed)
        apiResult.toString()
      }
      case e: AlreadyExistsException => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddModel", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Model_Failed)
        apiResult.toString()
      }
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddModel", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Model_Failed)
        apiResult.toString()
      }
    }
  }

  // Add Model (format XML)
  def RecompileModel(mod: ModelDef): String = {
    try {
      var compProxy = new CompilerProxy
      compProxy.setLoggerLevel(Level.TRACE)
      var modDef: ModelDef = null

      // Models can be either PMML or Custom Sourced.  See which one we are dealing with
      // here.
      if (mod.objectFormat == ObjFormatType.fXML) {
        val pmmlText = mod.ObjectDefinition
        var (classStrTemp, modDefTemp) = compProxy.compilePmml(pmmlText, true)
        modDef = modDefTemp
      } else {
        val saveModelParms = parse(mod.ObjectDefinition).values.asInstanceOf[Map[String, Any]]
        //val souce = mod.ObjectDefinition
        modDef = compProxy.recompileModelFromSource(saveModelParms.getOrElse(ModelCompilationConstants.SOURCECODE, "").asInstanceOf[String],
          saveModelParms.getOrElse(ModelCompilationConstants.PHYSICALNAME, "").asInstanceOf[String],
          saveModelParms.getOrElse(ModelCompilationConstants.DEPENDENCIES, List[String]()).asInstanceOf[List[String]],
          saveModelParms.getOrElse(ModelCompilationConstants.TYPES_DEPENDENCIES, List[String]()).asInstanceOf[List[String]],
          mod.ObjectFormat.toString)
      }

      val latestVersion = if (modDef == null) None else GetLatestModel(modDef)
      val isValid: Boolean = (modDef != null)
      if (isValid) {
        RemoveModel(latestVersion.get.nameSpace, latestVersion.get.name, latestVersion.get.ver, None)
        UploadJarsToDB(modDef)
        val result = AddModel(modDef)
        var objectsUpdated = new Array[BaseElemDef](0)
        var operations = new Array[String](0)
        objectsUpdated = objectsUpdated :+ latestVersion.get
        operations = operations :+ "Remove"
        objectsUpdated = objectsUpdated :+ modDef
        operations = operations :+ "Add"
        NotifyEngine(objectsUpdated, operations)
        result
      } else {
        val reasonForFailure: String = ErrorCodeConstants.Add_Model_Failed
        val modDefName: String = if (modDef != null) modDef.FullName else "(pmml compile failed)"
        val modDefVer: String = if (modDef != null) MdMgr.Pad0s2Version(modDef.Version) else MdMgr.UnknownVersion
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddModel", null, reasonForFailure + ":" + modDefName + "." + modDefVer)
        apiResult.toString()
      }
    } catch {
      case e: ModelCompilationFailedException => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddModel", null, "Error in producing scala file or Jar file.." + ErrorCodeConstants.Add_Model_Failed)
        apiResult.toString()
      }
      case e: AlreadyExistsException => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddModel", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Model_Failed)
        apiResult.toString()
      }
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddModel", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Model_Failed)
        apiResult.toString()
      }
    }
  }

  def UpdateModel(pmmlText: String, userid: Option[String]): String = {
    try {
      var compProxy = new CompilerProxy
      compProxy.setLoggerLevel(Level.TRACE)
      var (classStr, modDef) = compProxy.compilePmml(pmmlText)
      val latestVersion = if (modDef == null) None else GetLatestModel(modDef)
      val isValid: Boolean = if (latestVersion != None) IsValidVersion(latestVersion.get, modDef) else true

      if (isValid && modDef != null) {
        logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.UPDATEOBJECT, pmmlText, AuditConstants.SUCCESS, "", modDef.FullNameWithVer)
        val key = MdMgr.MkFullNameWithVersion(modDef.nameSpace, modDef.name, modDef.ver)

        RemoveModel(latestVersion.get.nameSpace, latestVersion.get.name, latestVersion.get.ver, None)
        UploadJarsToDB(modDef)
        val result = AddModel(modDef)
        var objectsUpdated = new Array[BaseElemDef](0)
        var operations = new Array[String](0)
        objectsUpdated = objectsUpdated :+ latestVersion.get
        operations = operations :+ "Remove"
        objectsUpdated = objectsUpdated :+ modDef
        operations = operations :+ "Add"
        NotifyEngine(objectsUpdated, operations)
        result
      } else {
        val reasonForFailure: String = if (modDef != null) ErrorCodeConstants.Update_Model_Failed_Invalid_Version else ErrorCodeConstants.Update_Model_Failed
        val modDefName: String = if (modDef != null) modDef.FullName else "(pmml compile failed)"
        val modDefVer: String = if (modDef != null) MdMgr.Pad0s2Version(modDef.Version) else MdMgr.UnknownVersion
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddModel", null, reasonForFailure + ":" + modDefName + "." + modDefVer)
        apiResult.toString()
      }
    } catch {
      case e: ObjectNotFoundException => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateModel", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Model_Failed)
        apiResult.toString()
      }
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "Update Model", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Model_Failed)
        apiResult.toString()
      }
    }
  }

  private def getBaseType(typ: BaseTypeDef): BaseTypeDef = {
    // Just return the "typ" if "typ" is not supported yet
    if (typ.tType == tMap) {
      logger.debug("MapTypeDef/ImmutableMapTypeDef is not yet handled")
      return typ
    }
    if (typ.tType == tHashMap) {
      logger.debug("HashMapTypeDef is not yet handled")
      return typ
    }
    if (typ.tType == tSet) {
      val typ1 = typ.asInstanceOf[SetTypeDef].keyDef
      return getBaseType(typ1)
    }
    if (typ.tType == tTreeSet) {
      val typ1 = typ.asInstanceOf[TreeSetTypeDef].keyDef
      return getBaseType(typ1)
    }
    if (typ.tType == tSortedSet) {
      val typ1 = typ.asInstanceOf[SortedSetTypeDef].keyDef
      return getBaseType(typ1)
    }
    if (typ.tType == tList) {
      val typ1 = typ.asInstanceOf[ListTypeDef].valDef
      return getBaseType(typ1)
    }
    if (typ.tType == tQueue) {
      val typ1 = typ.asInstanceOf[QueueTypeDef].valDef
      return getBaseType(typ1)
    }
    if (typ.tType == tArray) {
      val typ1 = typ.asInstanceOf[ArrayTypeDef].elemDef
      return getBaseType(typ1)
    }
    if (typ.tType == tArrayBuf) {
      val typ1 = typ.asInstanceOf[ArrayBufTypeDef].elemDef
      return getBaseType(typ1)
    }
    return typ
  }

  def GetDependentModels(msgNameSpace: String, msgName: String, msgVer: Long): Array[ModelDef] = {
    try {
      val msgObj = Array(msgNameSpace, msgName, msgVer).mkString(".").toLowerCase
      val msgObjName = (msgNameSpace + "." + msgName).toLowerCase
      val modDefs = MdMgr.GetMdMgr.Models(true, true)
      var depModels = new Array[ModelDef](0)
      modDefs match {
        case None =>
          logger.debug("No Models found ")
        case Some(ms) =>
          val msa = ms.toArray
          msa.foreach(mod => {
            logger.debug("Checking model " + mod.FullName + "." + MdMgr.Pad0s2Version(mod.Version))
            breakable {
              mod.inputVars.foreach(ivar => {
                val baseTyp = getBaseType(ivar.asInstanceOf[AttributeDef].typeDef)
                if (baseTyp.FullName.toLowerCase == msgObjName) {
                  logger.debug("The model " + mod.FullName + "." + MdMgr.Pad0s2Version(mod.Version) + " is  dependent on the message " + msgObj)
                  depModels = depModels :+ mod
                  break
                }
              })
              //Output vars don't determine dependant models at this time, comment out the following code
              // which is causing the Issue 355...
              /*
              mod.outputVars.foreach(ovar => {
                val baseTyp = getBaseType(ovar.asInstanceOf[AttributeDef].typeDef)
                if (baseTyp.FullName.toLowerCase == msgObjName) {
                  logger.debug("The model " + mod.FullName + "." + MdMgr.Pad0s2Version(mod.Version) + " is a dependent on the message " + msgObj)
                  depModels = depModels :+ mod
                  break
                }
              })
	      */
            }
          })
      }
      logger.debug("Found " + depModels.length + " dependant models ")
      depModels
    } catch {
      case e: Exception => {
        throw new InternalErrorException("Unable to find dependent models " + e.getMessage())
      }
    }
  }

  // All available models(format JSON or XML) as a String
  def GetAllModelDefs(formatType: String): String = {
    try {
      val modDefs = MdMgr.GetMdMgr.Models(true, true)
      modDefs match {
        case None =>
          None
          logger.debug("No Models found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllModelDefs", null, ErrorCodeConstants.Get_All_Models_Failed_Not_Available)
          apiResult.toString()
        case Some(ms) =>
          val msa = ms.toArray
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllModelDefs", JsonSerializer.SerializeObjectListToJson("Models", msa), ErrorCodeConstants.Get_All_Models_Successful)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllModelDefs", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Models_Failed)
        apiResult.toString()
      }
    }
  }

  // All available messages(format JSON or XML) as a String
  def GetAllMessageDefs(formatType: String): String = {
    try {
      val msgDefs = MdMgr.GetMdMgr.Messages(true, true)
      msgDefs match {
        case None =>
          None
          logger.debug("No Messages found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllMessageDefs", null, ErrorCodeConstants.Get_All_Messages_Failed_Not_Available)
          apiResult.toString()
        case Some(ms) =>
          val msa = ms.toArray
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllMessageDefs", JsonSerializer.SerializeObjectListToJson("Messages", msa), ErrorCodeConstants.Get_All_Messages_Succesful)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllMessageDefs", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Messages_Failed)
        apiResult.toString()
      }
    }
  }

  // All available containers(format JSON or XML) as a String
  def GetAllContainerDefs(formatType: String): String = {
    try {
      val msgDefs = MdMgr.GetMdMgr.Containers(true, true)
      msgDefs match {
        case None =>
          None
          logger.debug("No Containers found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllContainerDefs", null, ErrorCodeConstants.Get_All_Containers_Failed_Not_Available)
          apiResult.toString()
        case Some(ms) =>
          val msa = ms.toArray
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllContainerDefs", JsonSerializer.SerializeObjectListToJson("Containers", msa), ErrorCodeConstants.Get_All_Containers_Successful)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllContainerDefs", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Containers_Failed)
        apiResult.toString()
      }
    }
  }

  def GetAllModelsFromCache(active: Boolean, userid: Option[String]): Array[String] = {
    var modelList: Array[String] = new Array[String](0)
    if (userid != None) logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETKEYS, AuditConstants.MODEL, AuditConstants.SUCCESS, "", AuditConstants.MODEL)
    try {
      val modDefs = MdMgr.GetMdMgr.Models(active, true)
      modDefs match {
        case None =>
          None
          logger.debug("No Models found ")
          modelList
        case Some(ms) =>
          val msa = ms.toArray
          val modCount = msa.length
          modelList = new Array[String](modCount)
          for (i <- 0 to modCount - 1) {
            modelList(i) = msa(i).FullName + "." + MdMgr.Pad0s2Version(msa(i).Version)
          }
          modelList
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException("Failed to fetch all the models:" + e.toString)
      }
    }
  }

  def GetAllMessagesFromCache(active: Boolean, userid: Option[String]): Array[String] = {
    var messageList: Array[String] = new Array[String](0)
    if (userid != None) logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETKEYS, AuditConstants.MESSAGE, AuditConstants.SUCCESS, "", AuditConstants.MESSAGE)
    try {
      val msgDefs = MdMgr.GetMdMgr.Messages(active, true)
      msgDefs match {
        case None =>
          None
          logger.debug("No Messages found ")
          messageList
        case Some(ms) =>
          val msa = ms.toArray
          val msgCount = msa.length
          messageList = new Array[String](msgCount)
          for (i <- 0 to msgCount - 1) {
            messageList(i) = msa(i).FullName + "." + MdMgr.Pad0s2Version(msa(i).Version)
          }
          messageList
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException("Failed to fetch all the messages:" + e.toString)
      }
    }
  }

  def GetAllContainersFromCache(active: Boolean, userid: Option[String]): Array[String] = {
    var containerList: Array[String] = new Array[String](0)
    if (userid != None) logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETKEYS, AuditConstants.CONTAINER, AuditConstants.SUCCESS, "", AuditConstants.CONTAINER)
    try {
      val contDefs = MdMgr.GetMdMgr.Containers(active, true)
      contDefs match {
        case None =>
          None
          logger.debug("No Containers found ")
          containerList
        case Some(ms) =>
          val msa = ms.toArray
          val contCount = msa.length
          containerList = new Array[String](contCount)
          for (i <- 0 to contCount - 1) {
            containerList(i) = msa(i).FullName + "." + MdMgr.Pad0s2Version(msa(i).Version)
          }
          containerList
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException("Failed to fetch all the containers:" + e.toString)
      }
    }
  }

  def GetAllFunctionsFromCache(active: Boolean, userid: Option[String]): Array[String] = {
    var functionList: Array[String] = new Array[String](0)
    logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETKEYS, AuditConstants.FUNCTION, AuditConstants.SUCCESS, "", AuditConstants.FUNCTION)
    try {
      val contDefs = MdMgr.GetMdMgr.Functions(active, true)
      contDefs match {
        case None =>
          None
          logger.debug("No Functions found ")
          functionList
        case Some(ms) =>
          val msa = ms.toArray
          val contCount = msa.length
          functionList = new Array[String](contCount)
          for (i <- 0 to contCount - 1) {
            functionList(i) = msa(i).FullName + "." + MdMgr.Pad0s2Version(msa(i).Version)
          }
          functionList
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException("Failed to fetch all the functions:" + e.toString)
      }
    }
  }

  def GetAllConceptsFromCache(active: Boolean, userid: Option[String]): Array[String] = {
    var conceptList: Array[String] = new Array[String](0)
    logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETKEYS, AuditConstants.CONCEPT, AuditConstants.SUCCESS, "", AuditConstants.CONCEPT)
    try {
      val contDefs = MdMgr.GetMdMgr.Attributes(active, true)
      contDefs match {
        case None =>
          None
          logger.debug("No Concepts found ")
          conceptList
        case Some(ms) =>
          val msa = ms.toArray
          val contCount = msa.length
          conceptList = new Array[String](contCount)
          for (i <- 0 to contCount - 1) {
            conceptList(i) = msa(i).FullName + "." + MdMgr.Pad0s2Version(msa(i).Version)
          }
          conceptList
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException("Failed to fetch all the concepts:" + e.toString)
      }
    }
  }

  def GetAllTypesFromCache(active: Boolean, userid: Option[String]): Array[String] = {
    var typeList: Array[String] = new Array[String](0)
    logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETKEYS, AuditConstants.TYPE, AuditConstants.SUCCESS, "", AuditConstants.TYPE)
    try {
      val contDefs = MdMgr.GetMdMgr.Types(active, true)
      contDefs match {
        case None =>
          None
          logger.debug("No Types found ")
          typeList
        case Some(ms) =>
          val msa = ms.toArray
          val contCount = msa.length
          typeList = new Array[String](contCount)
          for (i <- 0 to contCount - 1) {
            typeList(i) = msa(i).FullName + "." + MdMgr.Pad0s2Version(msa(i).Version)
          }
          typeList
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException("Failed to fetch all the types:" + e.toString)
      }
    }
  }

  // Specific models (format JSON or XML) as an array of strings using modelName(without version) as the key
  def GetModelDef(nameSpace: String, objectName: String, formatType: String): String = {
    try {
      val modDefs = MdMgr.GetMdMgr.Models(nameSpace, objectName, true, true)
      modDefs match {
        case None =>
          None
          logger.debug("No Models found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetModelDef", null, ErrorCodeConstants.Get_Model_Failed_Not_Available + ":" + nameSpace + "." + objectName)
          apiResult.toString()
        case Some(ms) =>
          val msa = ms.toArray
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetModelDef", JsonSerializer.SerializeObjectListToJson("Models", msa), ErrorCodeConstants.Get_Model_Successful)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetModelDef", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Model_Failed + ":" + nameSpace + "." + objectName)
        apiResult.toString()
      }
    }
  }

  // Specific models (format JSON or XML) as an array of strings using modelName(without version) as the key
  def GetModelDef(objectName: String, formatType: String): String = {
    GetModelDef(sysNS, objectName, formatType)
  }

  // Specific model (format JSON or XML) as a String using modelName(with version) as the key
  def GetModelDefFromCache(nameSpace: String, name: String, formatType: String, version: String, userid: Option[String]): String = {
    val dispkey = nameSpace + "." + name + "." + MdMgr.Pad0s2Version(version.toLong)
    if (userid != None) logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.GETOBJECT, AuditConstants.MODEL, AuditConstants.SUCCESS, "", dispkey)
    try {
      var key = nameSpace + "." + name + "." + version.toLong
      val o = MdMgr.GetMdMgr.Model(nameSpace.toLowerCase, name.toLowerCase, version.toLong, true)
      o match {
        case None =>
          None
          logger.debug("model not found => " + dispkey)
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetModelDefFromCache", null, ErrorCodeConstants.Get_Model_From_Cache_Failed_Not_Active + ":" + dispkey)
          apiResult.toString()
        case Some(m) =>
          logger.debug("model found => " + m.asInstanceOf[ModelDef].FullName + "." + MdMgr.Pad0s2Version(m.asInstanceOf[ModelDef].Version))
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetModelDefFromCache", JsonSerializer.SerializeObjectToJson(m), ErrorCodeConstants.Get_Model_From_Cache_Successful + ":" + dispkey)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetModelDefFromCache", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Model_From_Cache_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  // Specific models (format JSON or XML) as an array of strings using modelName(without version) as the key
  def GetModelDef(nameSpace: String, objectName: String, formatType: String, version: String, userid: Option[String]): String = {
    logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.MODEL, AuditConstants.SUCCESS, "", nameSpace + "." + objectName + "." + version)
    GetModelDefFromCache(nameSpace, objectName, formatType, version, None)
  }

  // Specific message (format JSON or XML) as a String using messageName(with version) as the key
  def GetMessageDefFromCache(nameSpace: String, name: String, formatType: String, version: String, userid: Option[String]): String = {
    val dispkey = nameSpace + "." + name + "." + MdMgr.Pad0s2Version(version.toLong)
    var key = nameSpace + "." + name + "." + version.toLong
    if (userid != None) logAuditRec(userid, Some(AuditConstants.GETOBJECT), AuditConstants.GETOBJECT, AuditConstants.MESSAGE, AuditConstants.SUCCESS, "", dispkey)
    try {
      val o = MdMgr.GetMdMgr.Message(nameSpace.toLowerCase, name.toLowerCase, version.toLong, true)
      o match {
        case None =>
          None
          logger.debug("message not found => " + dispkey)
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetMessageDefFromCache", null, ErrorCodeConstants.Get_Message_From_Cache_Failed + ":" + dispkey)
          apiResult.toString()
        case Some(m) =>
          logger.debug("message found => " + m.asInstanceOf[MessageDef].FullName + "." + MdMgr.Pad0s2Version(m.asInstanceOf[MessageDef].Version))
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetMessageDefFromCache", JsonSerializer.SerializeObjectToJson(m), ErrorCodeConstants.Get_Message_From_Cache_Successful)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetMessageDefFromCache", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Message_From_Cache_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  // Specific container (format JSON or XML) as a String using containerName(with version) as the key
  def GetContainerDefFromCache(nameSpace: String, name: String, formatType: String, version: String, userid: Option[String]): String = {
    var key = nameSpace + "." + name + "." + version.toLong
    val dispkey = nameSpace + "." + name + "." + MdMgr.Pad0s2Version(version.toLong)
    if (userid != None) logAuditRec(userid, Some(AuditConstants.GETOBJECT), AuditConstants.GETOBJECT, AuditConstants.CONTAINER, AuditConstants.SUCCESS, "", dispkey)
    try {
      val o = MdMgr.GetMdMgr.Container(nameSpace.toLowerCase, name.toLowerCase, version.toLong, true)
      o match {
        case None =>
          None
          logger.debug("container not found => " + dispkey)
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetContainerDefFromCache", null, ErrorCodeConstants.Get_Container_From_Cache_Failed + ":" + dispkey)
          apiResult.toString()
        case Some(m) =>
          logger.debug("container found => " + m.asInstanceOf[ContainerDef].FullName + "." + MdMgr.Pad0s2Version(m.asInstanceOf[ContainerDef].Version))
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetContainerDefFromCache", JsonSerializer.SerializeObjectToJson(m), ErrorCodeConstants.Get_Container_From_Cache_Successful)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetContainerDefFromCache", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Container_From_Cache_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  // Return Specific messageDef object using messageName(with version) as the key
  @throws(classOf[ObjectNotFoundException])
  def GetMessageDefInstanceFromCache(nameSpace: String, name: String, formatType: String, version: String): MessageDef = {
    var key = nameSpace + "." + name + "." + version.toLong
    val dispkey = nameSpace + "." + name + "." + MdMgr.Pad0s2Version(version.toLong)
    try {
      val o = MdMgr.GetMdMgr.Message(nameSpace.toLowerCase, name.toLowerCase, version.toLong, true)
      o match {
        case None =>
          None
          logger.debug("message not found => " + dispkey)
          throw new ObjectNotFoundException("Failed to Fetch the message:" + dispkey)
        case Some(m) =>
          m.asInstanceOf[MessageDef]
      }
    } catch {
      case e: Exception => {
        throw new ObjectNotFoundException("Failed to Fetch the message:" + dispkey + ":" + e.getMessage())
      }
    }
  }

  // check whether model already exists in metadata manager. Ideally,
  // we should never add the model into metadata manager more than once
  // and there is no need to use this function in main code flow
  // This is just a utility function being during these initial phases
  def IsModelAlreadyExists(modDef: ModelDef): Boolean = {
    try {
      var key = modDef.nameSpace + "." + modDef.name + "." + modDef.ver
      val dispkey = modDef.nameSpace + "." + modDef.name + "." + MdMgr.Pad0s2Version(modDef.ver)
      val o = MdMgr.GetMdMgr.Model(modDef.nameSpace.toLowerCase,
        modDef.name.toLowerCase,
        modDef.ver,
        false)
      o match {
        case None =>
          None
          logger.debug("model not in the cache => " + dispkey)
          return false;
        case Some(m) =>
          logger.debug("model found => " + m.asInstanceOf[ModelDef].FullName + "." + MdMgr.Pad0s2Version(m.asInstanceOf[ModelDef].ver))
          return true
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException(e.getMessage())
      }
    }
  }

  // Get the latest model for a given FullName
  def GetLatestModel(modDef: ModelDef): Option[ModelDef] = {
    try {
      var key = modDef.nameSpace + "." + modDef.name + "." + modDef.ver
      val dispkey = modDef.nameSpace + "." + modDef.name + "." + MdMgr.Pad0s2Version(modDef.ver)
      val o = MdMgr.GetMdMgr.Models(modDef.nameSpace.toLowerCase,
        modDef.name.toLowerCase,
        false,
        true)
      o match {
        case None =>
          None
          logger.debug("model not in the cache => " + dispkey)
          None
        case Some(m) =>
          if (m.size > 0) {
            logger.debug("model found => " + m.head.asInstanceOf[ModelDef].FullName + "." + MdMgr.Pad0s2Version(m.head.asInstanceOf[ModelDef].ver))
            Some(m.head.asInstanceOf[ModelDef])
          } else
            None
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException(e.getMessage())
      }
    }
  }

  //Get the Higher Version Model from the Set of Models
  def GetLatestModelFromModels(modelSet: Set[ModelDef]): ModelDef = {
    var model: ModelDef = null
    var verList: List[Long] = List[Long]()
    var modelmap: scala.collection.mutable.Map[Long, ModelDef] = scala.collection.mutable.Map()
    try {
      modelSet.foreach(m => {
        modelmap.put(m.Version, m)
        verList = m.Version :: verList
      })
      model = modelmap.getOrElse(verList.max, null)
    } catch {
      case e: Exception =>
        throw new Exception("Error in traversing Model set " + e.getMessage())
    }
    model
  }

  def GetLatestFunction(fDef: FunctionDef): Option[FunctionDef] = {
    try {
      var key = fDef.nameSpace + "." + fDef.name + "." + fDef.ver
      val dispkey = fDef.nameSpace + "." + fDef.name + "." + MdMgr.Pad0s2Version(fDef.ver)
      val o = MdMgr.GetMdMgr.Messages(fDef.nameSpace.toLowerCase,
        fDef.name.toLowerCase,
        false,
        true)
      o match {
        case None =>
          None
          logger.debug("message not in the cache => " + dispkey)
          None
        case Some(m) =>
          // We can get called from the Add Message path, and M could be empty.
          if (m.size == 0) return None
          logger.debug("message found => " + m.head.asInstanceOf[MessageDef].FullName + "." + MdMgr.Pad0s2Version(m.head.asInstanceOf[MessageDef].ver))
          Some(m.head.asInstanceOf[FunctionDef])
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException(e.getMessage())
      }
    }
  }

  // Get the latest message for a given FullName
  def GetLatestMessage(msgDef: MessageDef): Option[MessageDef] = {
    try {
      var key = msgDef.nameSpace + "." + msgDef.name + "." + msgDef.ver
      val dispkey = msgDef.nameSpace + "." + msgDef.name + "." + MdMgr.Pad0s2Version(msgDef.ver)
      val o = MdMgr.GetMdMgr.Messages(msgDef.nameSpace.toLowerCase,
        msgDef.name.toLowerCase,
        false,
        true)
      o match {
        case None =>
          None
          logger.debug("message not in the cache => " + dispkey)
          None
        case Some(m) =>
          // We can get called from the Add Message path, and M could be empty.
          if (m.size == 0) return None
          logger.debug("message found => " + m.head.asInstanceOf[MessageDef].FullName + "." + MdMgr.Pad0s2Version(m.head.asInstanceOf[MessageDef].ver))
          Some(m.head.asInstanceOf[MessageDef])
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException(e.getMessage())
      }
    }
  }

  // Get the latest container for a given FullName
  def GetLatestContainer(contDef: ContainerDef): Option[ContainerDef] = {
    try {
      var key = contDef.nameSpace + "." + contDef.name + "." + contDef.ver
      val dispkey = contDef.nameSpace + "." + contDef.name + "." + MdMgr.Pad0s2Version(contDef.ver)
      val o = MdMgr.GetMdMgr.Containers(contDef.nameSpace.toLowerCase,
        contDef.name.toLowerCase,
        false,
        true)
      o match {
        case None =>
          None
          logger.debug("container not in the cache => " + dispkey)
          None
        case Some(m) =>
          // We can get called from the Add Container path, and M could be empty.
          if (m.size == 0) return None
          logger.debug("container found => " + m.head.asInstanceOf[ContainerDef].FullName + "." + MdMgr.Pad0s2Version(m.head.asInstanceOf[ContainerDef].ver))
          Some(m.head.asInstanceOf[ContainerDef])
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException(e.getMessage())
      }
    }
  }

  def IsValidVersion(oldObj: BaseElemDef, newObj: BaseElemDef): Boolean = {
    if (newObj.ver > oldObj.ver) {
      return true
    } else {
      return false
    }
  }

  // check whether message already exists in metadata manager. Ideally,
  // we should never add the message into metadata manager more than once
  // and there is no need to use this function in main code flow
  // This is just a utility function being during these initial phases
  def IsMessageAlreadyExists(msgDef: MessageDef): Boolean = {
    try {
      var key = msgDef.nameSpace + "." + msgDef.name + "." + msgDef.ver
      val dispkey = msgDef.nameSpace + "." + msgDef.name + "." + MdMgr.Pad0s2Version(msgDef.ver)
      val o = MdMgr.GetMdMgr.Message(msgDef.nameSpace.toLowerCase,
        msgDef.name.toLowerCase,
        msgDef.ver,
        false)
      o match {
        case None =>
          None
          logger.debug("message not in the cache => " + key)
          return false;
        case Some(m) =>
          logger.debug("message found => " + m.asInstanceOf[MessageDef].FullName + "." + MdMgr.Pad0s2Version(m.asInstanceOf[MessageDef].ver))
          return true
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException(e.getMessage())
      }
    }
  }

  def IsContainerAlreadyExists(contDef: ContainerDef): Boolean = {
    try {
      var key = contDef.nameSpace + "." + contDef.name + "." + contDef.ver
      val dispkey = contDef.nameSpace + "." + contDef.name + "." + MdMgr.Pad0s2Version(contDef.ver)
      val o = MdMgr.GetMdMgr.Container(contDef.nameSpace.toLowerCase,
        contDef.name.toLowerCase,
        contDef.ver,
        false)
      o match {
        case None =>
          None
          logger.debug("container not in the cache => " + dispkey)
          return false;
        case Some(m) =>
          logger.debug("container found => " + m.asInstanceOf[ContainerDef].FullName + "." + MdMgr.Pad0s2Version(m.asInstanceOf[ContainerDef].ver))
          return true
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException(e.getMessage())
      }
    }
  }

  def IsFunctionAlreadyExists(funcDef: FunctionDef): Boolean = {
    try {
      var key = funcDef.typeString
      val dispkey = key // No version in this string
      val o = MdMgr.GetMdMgr.Function(funcDef.nameSpace,
        funcDef.name,
        funcDef.args.toList.map(a => (a.aType.nameSpace, a.aType.name)),
        funcDef.ver,
        false)
      o match {
        case None =>
          None
          logger.debug("function not in the cache => " + dispkey)
          return false;
        case Some(m) =>
          logger.debug("function found => " + m.asInstanceOf[FunctionDef].typeString)
          return true
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException(e.getMessage())
      }
    }
  }

  def IsConceptAlreadyExists(attrDef: BaseAttributeDef): Boolean = {
    try {
      var key = attrDef.nameSpace + "." + attrDef.name + "." + attrDef.ver
      val dispkey = attrDef.nameSpace + "." + attrDef.name + "." + MdMgr.Pad0s2Version(attrDef.ver)
      val o = MdMgr.GetMdMgr.Attribute(attrDef.nameSpace,
        attrDef.name,
        attrDef.ver,
        false)
      o match {
        case None =>
          None
          logger.debug("concept not in the cache => " + dispkey)
          return false;
        case Some(m) =>
          logger.debug("concept found => " + m.asInstanceOf[AttributeDef].FullName + "." + MdMgr.Pad0s2Version(m.asInstanceOf[AttributeDef].ver))
          return true
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException(e.getMessage())
      }
    }
  }

  def IsTypeAlreadyExists(typeDef: BaseTypeDef): Boolean = {
    try {
      var key = typeDef.nameSpace + "." + typeDef.name + "." + typeDef.ver
      val dispkey = typeDef.nameSpace + "." + typeDef.name + "." + MdMgr.Pad0s2Version(typeDef.ver)
      val o = MdMgr.GetMdMgr.Type(typeDef.nameSpace,
        typeDef.name,
        typeDef.ver,
        false)
      o match {
        case None =>
          None
          logger.debug("Type not in the cache => " + key)
          return false;
        case Some(m) =>
          logger.debug("Type found => " + m.asInstanceOf[BaseTypeDef].FullName + "." + MdMgr.Pad0s2Version(m.asInstanceOf[BaseTypeDef].ver))
          return true
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new UnexpectedMetadataAPIException(e.getMessage())
      }
    }
  }

  // Specific message (format JSON or XML) as a String using messageName(with version) as the key
  def GetModelDefFromDB(nameSpace: String, objectName: String, formatType: String, version: String, userid: Option[String]): String = {
    var key = "ModelDef" + "." + nameSpace + '.' + objectName + "." + version.toLong
    val dispkey = "ModelDef" + "." + nameSpace + '.' + objectName + "." + MdMgr.Pad0s2Version(version.toLong)
    if (userid != None) logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.GETOBJECT, AuditConstants.MODEL, AuditConstants.SUCCESS, "", dispkey)
    try {
      var obj = GetObject(key.toLowerCase, modelStore)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetModelDefFromCache", ValueAsStr(obj.Value), ErrorCodeConstants.Get_Model_From_DB_Successful + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetModelDefFromCache", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Model_From_DB_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  private def IsTypeObject(typeName: String): Boolean = {
    typeName match {
      case "scalartypedef" | "arraytypedef" | "arraybuftypedef" | "listtypedef" | "settypedef" | "treesettypedef" | "queuetypedef" | "maptypedef" | "immutablemaptypedef" | "hashmaptypedef" | "tupletypedef" | "structtypedef" | "sortedsettypedef" => {
        return true
      }
      case _ => {
        return false
      }
    }
  }

  def GetAllKeys(objectType: String, userid: Option[String]): Array[String] = {
    try {
      var keys = scala.collection.mutable.Set[String]()
      typeStore.getAllKeys({ (key: Key) =>
        {
          val strKey = KeyAsStr(key)
          val i = strKey.indexOf(".")
          val objType = strKey.substring(0, i)
          val typeName = strKey.substring(i + 1)
          objectType match {
            case "TypeDef" => {
              if (IsTypeObject(objType)) {
                keys.add(typeName)
              }
              if (userid != None) logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETKEYS, AuditConstants.TYPE, AuditConstants.SUCCESS, "", AuditConstants.TYPE)
            }
            case "FunctionDef" => {
              if (objType == "functiondef") {
                keys.add(typeName)
              }
              if (userid != None) logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETKEYS, AuditConstants.FUNCTION, AuditConstants.SUCCESS, "", AuditConstants.FUNCTION)
            }
            case "MessageDef" => {
              if (objType == "messagedef") {
                keys.add(typeName)
              }
              if (userid != None) logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETKEYS, AuditConstants.MESSAGE, AuditConstants.SUCCESS, "", AuditConstants.MESSAGE)
            }
            case "ContainerDef" => {
              if (objType == "containerdef") {
                keys.add(typeName)
              }
              if (userid != None) logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETKEYS, AuditConstants.CONTAINER, AuditConstants.SUCCESS, "", AuditConstants.CONTAINER)
            }
            case "Concept" => {
              if (objType == "attributedef") {
                keys.add(typeName)
              }
              if (userid != None) logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETKEYS, AuditConstants.CONCEPT, AuditConstants.SUCCESS, "", AuditConstants.CONCEPT)
            }
            case "ModelDef" => {
              if (objType == "modeldef") {
                keys.add(typeName)
              }
              if (userid != None) logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETKEYS, AuditConstants.MODEL, AuditConstants.SUCCESS, "", AuditConstants.MODEL)
            }
            case _ => {
              logger.error("Unknown object type " + objectType + " in GetAllKeys function")
              throw InternalErrorException("Unknown object type " + objectType + " in GetAllKeys function")
            }
          }
        }
      })
      keys.toArray
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw InternalErrorException("Failed to get keys from persistent store")
      }
    }
  }

  def LoadAllConfigObjectsIntoCache: Boolean = {
    try {
      var keys = scala.collection.mutable.Set[Key]()
      configStore.getAllKeys({ (key: Key) => keys.add(key) })
      val keyArray = keys.toArray
      if (keyArray.length == 0) {
        logger.debug("No config objects available in the Database")
        return false
      }
      keyArray.foreach(key => {
        //logger.debug("key => " + KeyAsStr(key))
        val obj = GetObject(key, configStore)
        val strKey = KeyAsStr(key)
        val i = strKey.indexOf(".")
        val objType = strKey.substring(0, i)
        val typeName = strKey.substring(i + 1)
        objType match {
          case "nodeinfo" => {
            val ni = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte]).asInstanceOf[NodeInfo]
            MdMgr.GetMdMgr.AddNode(ni)
          }
          case "adapterinfo" => {
            val ai = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte]).asInstanceOf[AdapterInfo]
            MdMgr.GetMdMgr.AddAdapter(ai)
          }
          case "clusterinfo" => {
            val ci = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte]).asInstanceOf[ClusterInfo]
            MdMgr.GetMdMgr.AddCluster(ci)
          }
          case "clustercfginfo" => {
            val ci = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte]).asInstanceOf[ClusterCfgInfo]
            MdMgr.GetMdMgr.AddClusterCfg(ci)
          }
          case "userproperties" => {
            val up = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte]).asInstanceOf[UserPropertiesInfo]
            MdMgr.GetMdMgr.AddUserProperty(up)
          }
          case _ => {
            throw InternalErrorException("LoadAllConfigObjectsIntoCache: Unknown objectType " + objType)
          }
        }
      })
      return true
    } catch {
      case e: Exception => {
        e.printStackTrace()
        return false
      }
    }
  }

  private def LoadAllModelConfigsIntoChache: Unit = {
    var keys = scala.collection.mutable.Set[Key]()
    modelConfigStore.getAllKeys({ (key: Key) => keys.add(key) })
    val keyArray = keys.toArray
    if (keyArray.length == 0) {
      logger.debug("No model config objects available in the Database")
      return
    }
    keyArray.foreach(key => {
      val obj = GetObject(key, modelConfigStore)
      val conf = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte]).asInstanceOf[Map[String, List[String]]]
      MdMgr.GetMdMgr.AddModelConfig(KeyAsStr(key), conf)
    })
    MdMgr.GetMdMgr.DumpModelConfigs
  }
  
  //LoadAllUserPopertiesIntoChache - load all the date in the underlying userPopertiesStore into MdMgr Hashmap
  //private def LoadAllUserPopertiesIntoChache: Unit = {
  //    var keys = scala.collection.mutable.Set[com.ligadata.keyvaluestore.Key]()
  //    userPopertiesStore.getAllKeys({ (key: Key) => keys.add(key) })  
  //    val keyArray = keys.toArray
  //    if (keyArray.length == 0) {
  //      logger.debug("No model config objects available in the Database")
  //      return
  //    }
  //    keyArray.foreach (key => {
  //      val obj = GetObject(key, userPopertiesStore)
   //     val conf = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte]).asInstanceOf[Map[String,List[String]]]
   //     MdMgr.GetMdMgr.AddModelConfig(KeyAsStr(key),conf)
   //   })
    //  MdMgr.GetMdMgr.DumpModelConfigs
 // }

  def LoadAllObjectsIntoCache {
    try {
      val configAvailable = LoadAllConfigObjectsIntoCache
      if (configAvailable) {
        RefreshApiConfigForGivenNode(metadataAPIConfig.getProperty("NODE_ID"))
      } else {
        logger.debug("Assuming bootstrap... No config objects in persistent store")
      }

      // Load All the Model Configs here... 
      LoadAllModelConfigsIntoChache
      //LoadAllUserPopertiesIntoChache
      startup = true
      var objectsChanged = new Array[BaseElemDef](0)
      var operations = new Array[String](0)
      val maxTranId = GetTranId
      currentTranLevel = maxTranId
      logger.debug("Max Transaction Id => " + maxTranId)
      var keys = scala.collection.mutable.Set[Key]()
      metadataStore.getAllKeys({ (key: Key) => keys.add(key) })
      val keyArray = keys.toArray
      if (keyArray.length == 0) {
        logger.debug("No objects available in the Database")
        return
      }
      keyArray.foreach(key => {
        val obj = GetObject(key, metadataStore)
        val mObj = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte]).asInstanceOf[BaseElemDef]
        if (mObj != null) {
          if (mObj.tranId <= maxTranId) {
            AddObjectToCache(mObj, MdMgr.GetMdMgr)
            DownloadJarFromDB(mObj)
          } else {
            if (mObj.isInstanceOf[FunctionDef]) {
              // BUGBUG:: Not notifying functions at this moment. This may cause inconsistance between different instances of the metadata.
            } else {
              logger.debug("The transaction id of the object => " + mObj.tranId)
              AddObjectToCache(mObj, MdMgr.GetMdMgr)
              DownloadJarFromDB(mObj)
              logger.error("Transaction is incomplete with the object " + KeyAsStr(key) + ",we may not have notified engine, attempt to do it now...")
              objectsChanged = objectsChanged :+ mObj
              if (mObj.IsActive) {
                operations = for (op <- objectsChanged) yield "Add"
              } else {
                operations = for (op <- objectsChanged) yield "Remove"
              }
            }
          }
        } else {
          throw InternalErrorException("serializer.Deserialize returned a null object")
        }
      })
      if (objectsChanged.length > 0) {
        NotifyEngine(objectsChanged, operations)
      }
      startup = false
    } catch {
      case e: Exception => {
        e.printStackTrace()
      }
    }
  }

  def LoadAllTypesIntoCache {
    try {
      val typeKeys = GetAllKeys("TypeDef", None)
      if (typeKeys.length == 0) {
        logger.debug("No types available in the Database")
        return
      }
      typeKeys.foreach(key => {
        val obj = GetObject(key.toLowerCase, typeStore)
        val typ = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte])
        if (typ != null) {
          AddObjectToCache(typ, MdMgr.GetMdMgr)
        }
      })
    } catch {
      case e: Exception => {
        e.printStackTrace()
      }
    }
  }

  def LoadAllConceptsIntoCache {
    try {
      val conceptKeys = GetAllKeys("Concept", None)
      if (conceptKeys.length == 0) {
        logger.debug("No concepts available in the Database")
        return
      }
      conceptKeys.foreach(key => {
        val obj = GetObject(key.toLowerCase, conceptStore)
        val concept = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte])
        AddObjectToCache(concept.asInstanceOf[AttributeDef], MdMgr.GetMdMgr)
      })
    } catch {
      case e: Exception => {
        e.printStackTrace()
      }
    }
  }

  def LoadAllFunctionsIntoCache {
    try {
      val functionKeys = GetAllKeys("FunctionDef", None)
      if (functionKeys.length == 0) {
        logger.debug("No functions available in the Database")
        return
      }
      functionKeys.foreach(key => {
        val obj = GetObject(key.toLowerCase, functionStore)
        val function = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte])
        AddObjectToCache(function.asInstanceOf[FunctionDef], MdMgr.GetMdMgr)
      })
    } catch {
      case e: Exception => {
        e.printStackTrace()
      }
    }
  }

  def LoadAllMessagesIntoCache {
    try {
      val msgKeys = GetAllKeys("MessageDef", None)
      if (msgKeys.length == 0) {
        logger.debug("No messages available in the Database")
        return
      }
      msgKeys.foreach(key => {
        val obj = GetObject(key.toLowerCase, messageStore)
        val msg = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte])
        AddObjectToCache(msg.asInstanceOf[MessageDef], MdMgr.GetMdMgr)
      })
    } catch {
      case e: Exception => {
        e.printStackTrace()
      }
    }
  }

  def LoadMessageIntoCache(key: String) {
    try {
      logger.debug("Fetch the object " + key + " from database ")
      val obj = GetObject(key.toLowerCase, messageStore)
      logger.debug("Deserialize the object " + key)
      val msg = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte])
      logger.debug("Get the jar from database ")
      val msgDef = msg.asInstanceOf[MessageDef]
      DownloadJarFromDB(msgDef)
      logger.debug("Add the object " + key + " to the cache ")
      AddObjectToCache(msgDef, MdMgr.GetMdMgr)
    } catch {
      case e: Exception => {
        logger.error("Failed to load message into cache " + key + ":" + e.getMessage())
      }
    }
  }

  def LoadTypeIntoCache(key: String) {
    try {
      logger.debug("Fetch the object " + key + " from database ")
      val obj = GetObject(key.toLowerCase, typeStore)
      logger.debug("Deserialize the object " + key)
      val typ = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte])
      if (typ != null) {
        logger.debug("Add the object " + key + " to the cache ")
        AddObjectToCache(typ, MdMgr.GetMdMgr)
      }
    } catch {
      case e: Exception => {
        logger.warn("Unable to load the object " + key + " into cache ")
      }
    }
  }

  def LoadModelIntoCache(key: String) {
    try {
      logger.debug("Fetch the object " + key + " from database ")
      val obj = GetObject(key.toLowerCase, modelStore)
      logger.debug("Deserialize the object " + key)
      val model = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte])
      logger.debug("Get the jar from database ")
      val modDef = model.asInstanceOf[ModelDef]
      DownloadJarFromDB(modDef)
      logger.debug("Add the object " + key + " to the cache ")
      AddObjectToCache(modDef, MdMgr.GetMdMgr)
    } catch {
      case e: Exception => {
        e.printStackTrace()
      }
    }
  }

  def LoadContainerIntoCache(key: String) {
    try {
      val obj = GetObject(key.toLowerCase, containerStore)
      val cont = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte])
      logger.debug("Get the jar from database ")
      val contDef = cont.asInstanceOf[ContainerDef]
      DownloadJarFromDB(contDef)
      AddObjectToCache(contDef, MdMgr.GetMdMgr)
    } catch {
      case e: Exception => {
        e.printStackTrace()
      }
    }
  }

  def LoadFunctionIntoCache(key: String) {
    try {
      val obj = GetObject(key.toLowerCase, functionStore)
      val cont = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte])
      AddObjectToCache(cont.asInstanceOf[FunctionDef], MdMgr.GetMdMgr)
    } catch {
      case e: Exception => {
        e.printStackTrace()
      }
    }
  }

  def LoadAttributeIntoCache(key: String) {
    try {
      val obj = GetObject(key.toLowerCase, conceptStore)
      val cont = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte])
      AddObjectToCache(cont.asInstanceOf[AttributeDef], MdMgr.GetMdMgr)
    } catch {
      case e: Exception => {
        e.printStackTrace()
      }
    }
  }

  private def updateThisKey(zkMessage: ZooKeeperNotification) {

    var key: String = (zkMessage.ObjectType + "." + zkMessage.NameSpace + "." + zkMessage.Name + "." + zkMessage.Version.toLong).toLowerCase
    val dispkey = (zkMessage.ObjectType + "." + zkMessage.NameSpace + "." + zkMessage.Name + "." + MdMgr.Pad0s2Version(zkMessage.Version.toLong)).toLowerCase

    zkMessage.ObjectType match {
      case "ModelDef" => {
        zkMessage.Operation match {
          case "Add" => {
            LoadModelIntoCache(key)
          }
          case "Remove" | "Activate" | "Deactivate" => {
            try {
              MdMgr.GetMdMgr.ModifyModel(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, zkMessage.Operation)
            } catch {
              case e: ObjectNolongerExistsException => {
                logger.error("The object " + dispkey + " nolonger exists in metadata : It may have been removed already")
              }
            }
          }
          case _ => { logger.error("Unknown Operation " + zkMessage.Operation + " in zookeeper notification, notification is not processed ..") }
        }
      }
      case "MessageDef" => {
        zkMessage.Operation match {
          case "Add" => {
            LoadMessageIntoCache(key)
          }
          case "Remove" => {
            try {
              RemoveMessage(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, None, false)
            } catch {
              case e: ObjectNolongerExistsException => {
                logger.error("The object " + dispkey + " nolonger exists in metadata : It may have been removed already")
              }
            }
          }
          case "Activate" | "Deactivate" => {
            try {
              MdMgr.GetMdMgr.ModifyMessage(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, zkMessage.Operation)
            } catch {
              case e: ObjectNolongerExistsException => {
                logger.error("The object " + dispkey + " nolonger exists in metadata : It may have been removed already")
              }
            }
          }
          case _ => { logger.error("Unknown Operation " + zkMessage.Operation + " in zookeeper notification, notification is not processed ..") }
        }
      }
      case "ContainerDef" => {
        zkMessage.Operation match {
          case "Add" => {
            LoadContainerIntoCache(key)
          }
          case "Remove" => {
            try {
              RemoveContainer(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, None, false)
            } catch {
              case e: ObjectNolongerExistsException => {
                logger.error("The object " + dispkey + " nolonger exists in metadata : It may have been removed already")
              }
            }
          }
          case "Activate" | "Deactivate" => {
            try {
              MdMgr.GetMdMgr.ModifyContainer(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, zkMessage.Operation)
            } catch {
              case e: ObjectNolongerExistsException => {
                logger.error("The object " + dispkey + " nolonger exists in metadata : It may have been removed already")
              }
            }
          }
          case _ => { logger.error("Unknown Operation " + zkMessage.Operation + " in zookeeper notification, notification is not processed ..") }
        }
      }
      case "FunctionDef" => {
        zkMessage.Operation match {
          case "Add" => {
            LoadFunctionIntoCache(key)
          }
          case "Remove" | "Activate" | "Deactivate" => {
            try {
              MdMgr.GetMdMgr.ModifyFunction(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, zkMessage.Operation)
            } catch {
              case e: ObjectNolongerExistsException => {
                logger.error("The object " + dispkey + " nolonger exists in metadata : It may have been removed already")
              }
            }
          }
          case _ => { logger.error("Unknown Operation " + zkMessage.Operation + " in zookeeper notification, notification is not processed ..") }
        }
      }
      case "AttributeDef" => {
        zkMessage.Operation match {
          case "Add" => {
            LoadAttributeIntoCache(key)
          }
          case "Remove" | "Activate" | "Deactivate" => {
            try {
              MdMgr.GetMdMgr.ModifyAttribute(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, zkMessage.Operation)
            } catch {
              case e: ObjectNolongerExistsException => {
                logger.error("The object " + dispkey + " nolonger exists in metadata : It may have been removed already")
              }
            }
          }
          case _ => { logger.error("Unknown Operation " + zkMessage.Operation + " in zookeeper notification, notification is not processed ..") }
        }
      }
      case "JarDef" => {
        zkMessage.Operation match {
          case "Add" => {
            DownloadJarFromDB(MdMgr.GetMdMgr.MakeJarDef(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version))
          }
          case _ => { logger.error("Unknown Operation " + zkMessage.Operation + " in zookeeper notification, notification is not processed ..") }
        }
      }
      case "ScalarTypeDef" | "ArrayTypeDef" | "ArrayBufTypeDef" | "ListTypeDef" | "MappedMsgTypeDef" | "SetTypeDef" | "TreeSetTypeDef" | "QueueTypeDef" | "MapTypeDef" | "ImmutableMapTypeDef" | "HashMapTypeDef" | "TupleTypeDef" | "StructTypeDef" | "SortedSetTypeDef" => {
        zkMessage.Operation match {
          case "Add" => {
            LoadTypeIntoCache(key)
          }
          case "Remove" | "Activate" | "Deactivate" => {
            try {
              logger.debug("Remove the type " + dispkey + " from cache ")
              MdMgr.GetMdMgr.ModifyType(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, zkMessage.Operation)
            } catch {
              case e: ObjectNolongerExistsException => {
                logger.error("The object " + dispkey + " nolonger exists in metadata : It may have been removed already")
              }
            }
          }
          case _ => { logger.error("Unknown Operation " + zkMessage.Operation + " in zookeeper notification, notification is not processed ..") }
        }
      }
      case "OutputMsgDef" => {
        zkMessage.Operation match {
          case "Add" => {
            LoadOutputMsgIntoCache(key)
          }
          case "Remove" | "Activate" | "Deactivate" => {
            try {
              MdMgr.GetMdMgr.ModifyOutputMsg(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, zkMessage.Operation)
            } catch {
              case e: ObjectNolongerExistsException => {
                logger.error("The object " + key + " nolonger exists in metadata : It may have been removed already")
              }
            }
          }
        }
      }
      case _ => { logger.error("Unknown objectType " + zkMessage.ObjectType + " in zookeeper notification, notification is not processed ..") }
    }
  }

  def LoadOutputMsgIntoCache(key: String) {
    try {
      logger.debug("Fetch the object " + key + " from database ")
      val obj = GetObject(key.toLowerCase, outputmsgStore)
      logger.debug("Deserialize the object " + key)
      val outputMsg = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte])
      val outputMsgDef = outputMsg.asInstanceOf[OutputMsgDef]
      logger.debug("Add the output msg def object " + key + " to the cache ")
      AddObjectToCache(outputMsgDef, MdMgr.GetMdMgr)
    } catch {
      case e: Exception => {
        e.printStackTrace()
      }
    }
  }

  def UpdateMdMgr(zkTransaction: ZooKeeperTransaction): Unit = {
    var key: String = null
    var dispkey: String = null

    // If we already processed this transaction, currTranLevel will be at least at the level of this notify.
    if (zkTransaction.transactionId.getOrElse("0").toLong <= currentTranLevel) return

    try {
      zkTransaction.Notifications.foreach(zkMessage => {
        key = (zkMessage.Operation + "." + zkMessage.NameSpace + "." + zkMessage.Name + "." + zkMessage.Version).toLowerCase
        dispkey = (zkMessage.Operation + "." + zkMessage.NameSpace + "." + zkMessage.Name + "." + MdMgr.Pad0s2Version(zkMessage.Version.toLong)).toLowerCase
        if (!cacheOfOwnChanges.contains(key)) {
          // Proceed with update.
          updateThisKey(zkMessage)
        } else {
          // Ignore the update, remove the element from set.
          cacheOfOwnChanges.remove(key)
        }
      })
    } catch {
      case e: AlreadyExistsException => {
        logger.warn("Failed to load the object(" + dispkey + ") into cache: " + e.getMessage())
      }
      case e: Exception => {
        logger.warn("Failed to load the object(" + dispkey + ") into cache: " + e.getMessage())
      }
    }
  }

  def LoadAllContainersIntoCache {
    try {
      val contKeys = GetAllKeys("ContainerDef", None)
      if (contKeys.length == 0) {
        logger.debug("No containers available in the Database")
        return
      }
      contKeys.foreach(key => {
        val obj = GetObject(key.toLowerCase, containerStore)
        val contDef = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte])
        AddObjectToCache(contDef.asInstanceOf[ContainerDef], MdMgr.GetMdMgr)
      })
    } catch {
      case e: Exception => {
        e.printStackTrace()
      }
    }
  }

  def LoadAllModelsIntoCache {
    try {
      val modKeys = GetAllKeys("ModelDef", None)
      if (modKeys.length == 0) {
        logger.debug("No models available in the Database")
        return
      }
      modKeys.foreach(key => {
        val obj = GetObject(key.toLowerCase, modelStore)
        val modDef = serializer.DeserializeObjectFromByteArray(obj.Value.toArray[Byte])
        AddObjectToCache(modDef.asInstanceOf[ModelDef], MdMgr.GetMdMgr)
      })
    } catch {
      case e: Exception => {
        e.printStackTrace()
      }
    }
  }

  def LoadObjectsIntoCache {
    LoadAllModelsIntoCache
    LoadAllMessagesIntoCache
    LoadAllContainersIntoCache
    LoadAllFunctionsIntoCache
    LoadAllConceptsIntoCache
    LoadAllTypesIntoCache
  }

  // Specific messages (format JSON or XML) as a String using messageName(without version) as the key
  def GetMessageDef(objectName: String, formatType: String): String = {
    val nameSpace = MdMgr.sysNS
    GetMessageDefFromCache(nameSpace, objectName, formatType, "-1", None)
  }
  // Specific message (format JSON or XML) as a String using messageName(with version) as the key
  def GetMessageDef(nameSpace: String, objectName: String, formatType: String, version: String, userid: Option[String]): String = {
    logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.MESSAGE, AuditConstants.SUCCESS, "", nameSpace + "." + objectName + "." + version)
    GetMessageDefFromCache(nameSpace, objectName, formatType, version, None)
  }

  // Specific message (format JSON or XML) as a String using messageName(with version) as the key
  def GetMessageDef(objectName: String, version: String, formatType: String): String = {
    val nameSpace = MdMgr.sysNS
    GetMessageDef(nameSpace, objectName, formatType, version, None)
  }

  // Specific containers (format JSON or XML) as a String using containerName(without version) as the key
  def GetContainerDef(objectName: String, formatType: String): String = {
    val nameSpace = MdMgr.sysNS
    GetContainerDefFromCache(nameSpace, objectName, formatType, "-1", None)
  }
  // Specific container (format JSON or XML) as a String using containerName(with version) as the key
  def GetContainerDef(nameSpace: String, objectName: String, formatType: String, version: String, userid: Option[String]): String = {
    logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.CONTAINER, AuditConstants.SUCCESS, "", nameSpace + "." + objectName + "." + version)
    GetContainerDefFromCache(nameSpace, objectName, formatType, version, None)
  }

  // Specific container (format JSON or XML) as a String using containerName(with version) as the key
  def GetContainerDef(objectName: String, version: String, formatType: String): String = {
    val nameSpace = MdMgr.sysNS
    GetContainerDef(nameSpace, objectName, formatType, version, None)
  }

  // Answer count and dump of all available functions(format JSON or XML) as a String
  def GetAllFunctionDefs(formatType: String, userid: Option[String]): (Int, String) = {
    try {
      val funcDefs = MdMgr.GetMdMgr.Functions(true, true)
      if (userid != None) logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.FUNCTION, AuditConstants.SUCCESS, "", "ALL")
      funcDefs match {
        case None =>
          None
          logger.debug("No Functions found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllFunctionDefs", null, ErrorCodeConstants.Get_All_Functions_Failed_Not_Available)
          (0, apiResult.toString())
        case Some(fs) =>
          val fsa: Array[FunctionDef] = fs.toArray
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllFunctionDefs", JsonSerializer.SerializeObjectListToJson("Functions", fsa), ErrorCodeConstants.Get_All_Functions_Successful)
          (fsa.size, apiResult.toString())
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllFunctionDefs", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Functions_Failed)
        (0, apiResult.toString())
      }
    }
  }

  def GetFunctionDef(nameSpace: String, objectName: String, formatType: String, userid: Option[String]): String = {
    try {
      if (userid != None) logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.FUNCTION, AuditConstants.SUCCESS, "", nameSpace + "." + objectName + "LATEST")
      val funcDefs = MdMgr.GetMdMgr.FunctionsAvailable(nameSpace, objectName)
      if (funcDefs == null) {
        logger.debug("No Functions found ")
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetFunctionDef", null, ErrorCodeConstants.Get_Function_Failed + ":" + nameSpace + "." + objectName)
        apiResult.toString()
      } else {
        val fsa = funcDefs.toArray
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetFunctionDef", JsonSerializer.SerializeObjectListToJson("Functions", fsa), ErrorCodeConstants.Get_Function_Successful)
        apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetFunctionDef", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Function_Failed + ":" + nameSpace + "." + objectName)
        apiResult.toString()
      }
    }
  }

  def GetFunctionDef(nameSpace: String, objectName: String, formatType: String, version: String, userid: Option[String]): String = {
    logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.FUNCTION, AuditConstants.SUCCESS, "", nameSpace + "." + objectName + "." + version)
    GetFunctionDef(nameSpace, objectName, formatType, None)
  }

  // Specific messages (format JSON or XML) as a String using messageName(without version) as the key
  def GetFunctionDef(objectName: String, formatType: String, userid: Option[String]): String = {
    val nameSpace = MdMgr.sysNS
    GetFunctionDef(nameSpace, objectName, formatType, userid)
  }

  // All available concepts as a String
  def GetAllConcepts(formatType: String, userid: Option[String]): String = {
    try {
      if (userid != None) logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.CONCEPT, AuditConstants.SUCCESS, "", "ALL")
      val concepts = MdMgr.GetMdMgr.Attributes(true, true)
      concepts match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllConcepts", null, ErrorCodeConstants.Get_All_Concepts_Failed_Not_Available)
          apiResult.toString()
        case Some(cs) =>
          val csa = cs.toArray
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllConcepts", JsonSerializer.SerializeObjectListToJson("Concepts", csa), ErrorCodeConstants.Get_All_Concepts_Successful)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllConcepts", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Concepts_Failed)
        apiResult.toString()
      }
    }
  }

  // A single concept as a string using name and version as the key
  def GetConcept(nameSpace: String, objectName: String, version: String, formatType: String): String = {
    var key = nameSpace + "." + objectName + "." + version.toLong
    val dispkey = nameSpace + "." + objectName + "." + MdMgr.Pad0s2Version(version.toLong)
    try {
      val concept = MdMgr.GetMdMgr.Attribute(nameSpace, objectName, version.toLong, false)
      concept match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetConcept", null, ErrorCodeConstants.Get_Concept_Failed_Not_Available)
          apiResult.toString()
        case Some(cs) =>
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetConcept", JsonSerializer.SerializeObjectToJson(cs), ErrorCodeConstants.Get_Concept_Successful + ":" + dispkey)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Concept_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  // A single concept as a string using name and version as the key
  def GetConcept(objectName: String, version: String, formatType: String): String = {
    GetConcept(MdMgr.sysNS, objectName, version, formatType)
  }

  // A single concept as a string using name and version as the key
  def GetConceptDef(nameSpace: String, objectName: String, formatType: String, version: String, userid: Option[String]): String = {
    logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.CONCEPT, AuditConstants.SUCCESS, "", nameSpace + "." + objectName + "." + version)
    GetConcept(nameSpace, objectName, version, formatType)
  }

  // A list of concept(s) as a string using name 
  def GetConcept(objectName: String, formatType: String): String = {
    try {
      val concepts = MdMgr.GetMdMgr.Attributes(MdMgr.sysNS, objectName, false, false)
      concepts match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetConcept", null, ErrorCodeConstants.Get_Concept_Failed_Not_Available + ":" + objectName)
          apiResult.toString()
        case Some(cs) =>
          val csa = cs.toArray
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetConcept", JsonSerializer.SerializeObjectListToJson("Concepts", csa), ErrorCodeConstants.Get_Concept_Successful + ":" + objectName)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Concept_Failed + ":" + objectName)
        apiResult.toString()
      }
    }
  }

  // All available derived concepts(format JSON or XML) as a String
  def GetAllDerivedConcepts(formatType: String): String = {
    try {
      val concepts = MdMgr.GetMdMgr.Attributes(true, true)
      concepts match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllDerivedConcepts", null, ErrorCodeConstants.Get_All_Derived_Concepts_Failed_Not_Available)
          apiResult.toString()
        case Some(cs) =>
          val csa = cs.toArray.filter(t => { t.getClass.getName.contains("DerivedAttributeDef") })
          if (csa.length > 0) {
            var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllDerivedConcepts", JsonSerializer.SerializeObjectListToJson("Concepts", csa), ErrorCodeConstants.Get_All_Derived_Concepts_Successful)
            apiResult.toString()
          } else {
            var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllDerivedConcepts", null, ErrorCodeConstants.Get_All_Derived_Concepts_Failed_Not_Available)
            apiResult.toString()
          }
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllDerivedConcepts", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Derived_Concepts_Failed)
        apiResult.toString()
      }
    }
  }
  // A derived concept(format JSON or XML) as a string using name(without version) as the key
  def GetDerivedConcept(objectName: String, formatType: String): String = {
    try {
      val concepts = MdMgr.GetMdMgr.Attributes(MdMgr.sysNS, objectName, false, false)
      concepts match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetDerivedConcept", null, ErrorCodeConstants.Get_Derived_Concept_Failed_Not_Available + ":" + objectName)
          apiResult.toString()
        case Some(cs) =>
          val csa = cs.toArray.filter(t => { t.getClass.getName.contains("DerivedAttributeDef") })
          if (csa.length > 0) {
            var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetDerivedConcept", JsonSerializer.SerializeObjectListToJson("Concepts", csa), ErrorCodeConstants.Get_Derived_Concept_Successful + ":" + objectName)
            apiResult.toString()
          } else {
            var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetDerivedConcept", null, ErrorCodeConstants.Get_Derived_Concept_Failed_Not_Available + ":" + objectName)
            apiResult.toString()
          }
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetDerivedConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Derived_Concept_Failed + ":" + objectName)
        apiResult.toString()
      }
    }
  }
  // A derived concept(format JSON or XML) as a string using name and version as the key
  def GetDerivedConcept(objectName: String, version: String, formatType: String): String = {
    var key = objectName + "." + version.toLong
    val dispkey = objectName + "." + MdMgr.Pad0s2Version(version.toLong)
    try {
      val concept = MdMgr.GetMdMgr.Attribute(MdMgr.sysNS, objectName, version.toLong, false)
      concept match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetDerivedConcept", null, ErrorCodeConstants.Get_Derived_Concept_Failed_Not_Available + ":" + dispkey)
          apiResult.toString()
        case Some(cs) =>
          if (cs.isInstanceOf[DerivedAttributeDef]) {
            var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetDerivedConcept", JsonSerializer.SerializeObjectToJson(cs), ErrorCodeConstants.Get_Derived_Concept_Successful + ":" + dispkey)
            apiResult.toString()
          } else {
            logger.debug("No Derived concepts found ")
            var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetDerivedConcept", null, ErrorCodeConstants.Get_Derived_Concept_Failed_Not_Available + ":" + dispkey)
            apiResult.toString()
          }
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetDerivedConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Derived_Concept_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  // All available types(format JSON or XML) as a String
  def GetAllTypes(formatType: String, userid: Option[String]): String = {
    try {
      if (userid != None) logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.TYPE, AuditConstants.SUCCESS, "", "ALL")
      val typeDefs = MdMgr.GetMdMgr.Types(true, true)
      typeDefs match {
        case None =>
          None
          logger.debug("No typeDefs found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllTypes", null, ErrorCodeConstants.Get_All_Types_Failed_Not_Available)
          apiResult.toString()
        case Some(ts) =>
          val tsa = ts.toArray
          logger.debug("Found " + tsa.length + " types ")
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllTypes", JsonSerializer.SerializeObjectListToJson("Types", tsa), ErrorCodeConstants.Get_All_Types_Successful)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllTypes", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Types_Failed)
        apiResult.toString()
      }
    }
  }

  // All available types(format JSON or XML) as a String
  def GetAllTypesByObjType(formatType: String, objType: String): String = {
    logger.debug("Find the types of type " + objType)
    try {
      val typeDefs = MdMgr.GetMdMgr.Types(true, true)
      typeDefs match {
        case None =>
          None
          logger.debug("No typeDefs found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllTypesByObjType", null, ErrorCodeConstants.Get_All_Types_Failed_Not_Available + ":" + objType)
          apiResult.toString()
        case Some(ts) =>
          val tsa = ts.toArray.filter(t => { t.getClass.getName == objType })
          if (tsa.length == 0) {
            var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllTypesByObjType", null, ErrorCodeConstants.Get_All_Types_Failed_Not_Available + ":" + objType)
            apiResult.toString()
          } else {
            var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllTypesByObjType", JsonSerializer.SerializeObjectListToJson("Types", tsa), ErrorCodeConstants.Get_All_Types_Successful + ":" + objType)
            apiResult.toString()
          }
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllTypesByObjType", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Types_Failed + ":" + objType)
        apiResult.toString()
      }
    }
  }

  // Get types for a given name
  def GetType(objectName: String, formatType: String): String = {
    try {
      val typeDefs = MdMgr.GetMdMgr.Types(MdMgr.sysNS, objectName, false, false)
      typeDefs match {
        case None =>
          None
          logger.debug("No typeDefs found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetType", null, ErrorCodeConstants.Get_Type_Failed_Not_Available + ":" + objectName)
          apiResult.toString()
        case Some(ts) =>
          val tsa = ts.toArray
          logger.debug("Found " + tsa.length + " types ")
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetType", JsonSerializer.SerializeObjectListToJson("Types", tsa), ErrorCodeConstants.Get_Type_Successful + ":" + objectName)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetType", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Type_Failed + ":" + objectName)
        apiResult.toString()
      }
    }
  }

  def GetTypeDef(nameSpace: String, objectName: String, formatType: String, version: String, userid: Option[String]): String = {
    var key = nameSpace + "." + objectName + "." + version.toLong
    val dispkey = nameSpace + "." + objectName + "." + MdMgr.Pad0s2Version(version.toLong)
    logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.TYPE, AuditConstants.SUCCESS, "", dispkey)
    try {
      val typeDefs = MdMgr.GetMdMgr.Types(nameSpace, objectName, false, false)
      typeDefs match {
        case None =>
          None
          logger.debug("No typeDefs found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetTypeDef", null, ErrorCodeConstants.Get_Type_Def_Failed_Not_Available + ":" + dispkey)
          apiResult.toString()
        case Some(ts) =>
          val tsa = ts.toArray
          logger.debug("Found " + tsa.length + " types ")
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetTypeDef", JsonSerializer.SerializeObjectListToJson("Types", tsa), ErrorCodeConstants.Get_Type_Def_Successful + ":" + dispkey)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetTypeDef", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Type_Def_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def GetType(nameSpace: String, objectName: String, version: String, formatType: String, userid: Option[String]): Option[BaseTypeDef] = {
    try {
      val dispkey = nameSpace + "." + objectName + "." + version
      if (userid != None) logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.TYPE, AuditConstants.SUCCESS, "", dispkey)
      val typeDefs = MdMgr.GetMdMgr.Types(MdMgr.sysNS, objectName, false, false)
      typeDefs match {
        case None => None
        case Some(ts) =>
          val tsa = ts.toArray.filter(t => { t.ver == version.toLong })
          tsa.length match {
            case 0 => None
            case 1 => Some(tsa(0))
            case _ => {
              logger.debug("Found More than one type, Doesn't make sesne")
              Some(tsa(0))
            }
          }
      }
    } catch {
      case e: Exception => {
        logger.error("Failed to fetch the typeDefs:" + e.getMessage())
        None
      }
    }
  }

  def AddNode(nodeId: String, nodePort: Int, nodeIpAddr: String,
    jarPaths: List[String], scala_home: String,
    java_home: String, classpath: String,
    clusterId: String, power: Int,
    roles: Array[String], description: String): String = {
    try {
      // save in memory
      val ni = MdMgr.GetMdMgr.MakeNode(nodeId, nodePort, nodeIpAddr, jarPaths, scala_home,
        java_home, classpath, clusterId, power, roles, description)
      MdMgr.GetMdMgr.AddNode(ni)
      // save in database
      val key = "NodeInfo." + nodeId
      val value = serializer.SerializeObjectToByteArray(ni)
      SaveObject(key.toLowerCase, value, configStore)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddNode", null, ErrorCodeConstants.Add_Node_Successful + ":" + nodeId)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddNode", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Node_Failed + ":" + nodeId)
        apiResult.toString()
      }
    }
  }

  def UpdateNode(nodeId: String, nodePort: Int, nodeIpAddr: String,
    jarPaths: List[String], scala_home: String,
    java_home: String, classpath: String,
    clusterId: String, power: Int,
    roles: Array[String], description: String): String = {
    AddNode(nodeId, nodePort, nodeIpAddr, jarPaths, scala_home,
      java_home, classpath,
      clusterId, power, roles, description)
  }

  def RemoveNode(nodeId: String): String = {
    try {
      MdMgr.GetMdMgr.RemoveNode(nodeId)
      val key = "NodeInfo." + nodeId
      DeleteObject(key.toLowerCase, configStore)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveNode", null, ErrorCodeConstants.Remove_Node_Successful + ":" + nodeId)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveNode", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Node_Failed + ":" + nodeId)
        apiResult.toString()
      }
    }
  }

  def AddAdapter(name: String, typeString: String, dataFormat: String, className: String,
    jarName: String, dependencyJars: List[String],
    adapterSpecificCfg: String, inputAdapterToVerify: String, delimiterString: String, associatedMsg: String): String = {
    try {
      // save in memory
      val ai = MdMgr.GetMdMgr.MakeAdapter(name, typeString, dataFormat, className, jarName,
        dependencyJars, adapterSpecificCfg, inputAdapterToVerify, delimiterString, associatedMsg)
      MdMgr.GetMdMgr.AddAdapter(ai)
      // save in database
      val key = "AdapterInfo." + name
      val value = serializer.SerializeObjectToByteArray(ai)
      SaveObject(key.toLowerCase, value, configStore)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddAdapter", null, ErrorCodeConstants.Add_Adapter_Successful + ":" + name)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddAdapter", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Adapter_Failed + ":" + name)
        apiResult.toString()
      }
    }
  }

  def UpdateAdapter(name: String, typeString: String, dataFormat: String, className: String,
    jarName: String, dependencyJars: List[String],
    adapterSpecificCfg: String, inputAdapterToVerify: String, delimiterString: String, associatedMsg: String): String = {
    AddAdapter(name, typeString, dataFormat, className, jarName, dependencyJars, adapterSpecificCfg, inputAdapterToVerify, delimiterString, associatedMsg)
  }

  def RemoveAdapter(name: String): String = {
    try {
      MdMgr.GetMdMgr.RemoveAdapter(name)
      val key = "AdapterInfo." + name
      DeleteObject(key.toLowerCase, configStore)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveAdapter", null, ErrorCodeConstants.Remove_Adapter_Successful + ":" + name)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveAdapter", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Adapter_Failed + ":" + name)
        apiResult.toString()
      }
    }
  }

  def AddCluster(clusterId: String, description: String, privileges: String): String = {
    try {
      // save in memory
      val ci = MdMgr.GetMdMgr.MakeCluster(clusterId, description, privileges)
      MdMgr.GetMdMgr.AddCluster(ci)
      // save in database
      val key = "ClusterInfo." + clusterId
      val value = serializer.SerializeObjectToByteArray(ci)
      SaveObject(key.toLowerCase, value, configStore)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddCluster", null, ErrorCodeConstants.Add_Cluster_Successful + ":" + clusterId)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddCluster", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Cluster_Failed + ":" + clusterId)
        apiResult.toString()
      }
    }
  }

  def UpdateCluster(clusterId: String, description: String, privileges: String): String = {
    AddCluster(clusterId, description, privileges)
  }

  def RemoveCluster(clusterId: String): String = {
    try {
      MdMgr.GetMdMgr.RemoveCluster(clusterId)
      val key = "ClusterInfo." + clusterId
      DeleteObject(key.toLowerCase, configStore)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveCluster", null, ErrorCodeConstants.Remove_Cluster_Successful + ":" + clusterId)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveCluster", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Cluster_Failed + ":" + clusterId)
        apiResult.toString()
      }
    }
  }

  def AddClusterCfg(clusterCfgId: String, cfgMap: scala.collection.mutable.HashMap[String, String],
    modifiedTime: Date, createdTime: Date): String = {
    try {
      // save in memory
      val ci = MdMgr.GetMdMgr.MakeClusterCfg(clusterCfgId, cfgMap, modifiedTime, createdTime)
      MdMgr.GetMdMgr.AddClusterCfg(ci)
      // save in database
      val key = "ClusterCfgInfo." + clusterCfgId
      val value = serializer.SerializeObjectToByteArray(ci)
      SaveObject(key.toLowerCase, value, configStore)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddClusterCfg", null, ErrorCodeConstants.Add_Cluster_Config_Successful + ":" + clusterCfgId)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddClusterCfg", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Cluster_Config_Failed + ":" + clusterCfgId)
        apiResult.toString()
      }
    }
  }

  def UpdateClusterCfg(clusterCfgId: String, cfgMap: scala.collection.mutable.HashMap[String, String],
    modifiedTime: Date, createdTime: Date): String = {
    AddClusterCfg(clusterCfgId, cfgMap, modifiedTime, createdTime)
  }

  def RemoveClusterCfg(clusterCfgId: String): String = {
    try {
      MdMgr.GetMdMgr.RemoveClusterCfg(clusterCfgId)
      val key = "ClusterCfgInfo." + clusterCfgId
      DeleteObject(key.toLowerCase, configStore)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveCLusterCfg", null, ErrorCodeConstants.Remove_Cluster_Config_Successful + ":" + clusterCfgId)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveCLusterCfg", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Cluster_Config_Failed + ":" + clusterCfgId)
        apiResult.toString()
      }
    }
  }

  def RemoveConfig(cfgStr: String, userid: Option[String], cobjects: String): String = {
    var keyList = new Array[String](0)
    logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.REMOVECONFIG, cfgStr, AuditConstants.SUCCESS, "", cobjects)
    try {
      // extract config objects
      val map = JsonSerializer.parseEngineConfig(cfgStr)
      // process clusterInfo object if it exists
      if (map.contains("Clusters")) {
        var globalAdaptersCollected = false // to support previous versions
        val clustersList = map.get("Clusters").get.asInstanceOf[List[_]] //BUGBUG:: Do we need to check the type before converting
        logger.debug("Found " + clustersList.length + " cluster objects ")
        clustersList.foreach(clustny => {
          val cluster = clustny.asInstanceOf[Map[String, Any]] //BUGBUG:: Do we need to check the type before converting
          val ClusterId = cluster.getOrElse("ClusterId", "").toString.trim.toLowerCase

          MdMgr.GetMdMgr.RemoveCluster(ClusterId)
          var key = "ClusterInfo." + ClusterId
          keyList = keyList :+ key.toLowerCase
          MdMgr.GetMdMgr.RemoveClusterCfg(ClusterId)
          key = "ClusterCfgInfo." + ClusterId
          keyList = keyList :+ key.toLowerCase

          if (cluster.contains("Nodes")) {
            val nodes = cluster.get("Nodes").get.asInstanceOf[List[_]]
            nodes.foreach(n => {
              val node = n.asInstanceOf[Map[String, Any]]
              val nodeId = node.getOrElse("NodeId", "").toString.trim.toLowerCase
              if (nodeId.size > 0) {
                MdMgr.GetMdMgr.RemoveNode(nodeId.toLowerCase)
                key = "NodeInfo." + nodeId
                keyList = keyList :+ key.toLowerCase
              }
            })
          }

          if (cluster.contains("Adapters") || (globalAdaptersCollected == false && map.contains("Adapters"))) {
            val adapters = if (cluster.contains("Adapters") && (globalAdaptersCollected == false && map.contains("Adapters"))) {
              map.get("Adapters").get.asInstanceOf[List[_]] ++ cluster.get("Adapters").get.asInstanceOf[List[_]]
            } else if (cluster.contains("Adapters")) {
              cluster.get("Adapters").get.asInstanceOf[List[_]]
            } else if (globalAdaptersCollected == false && map.contains("Adapters")) {
              map.get("Adapters").get.asInstanceOf[List[_]]
            } else {
              List[Any]()
            }

            globalAdaptersCollected = true // to support previous versions

            adapters.foreach(a => {
              val adap = a.asInstanceOf[Map[String, Any]]
              val nm = adap.getOrElse("Name", "").toString.trim.toLowerCase
              if (nm.size > 0) {
                MdMgr.GetMdMgr.RemoveAdapter(nm)
                val key = "AdapterInfo." + nm
                keyList = keyList :+ key.toLowerCase
              }
            })
          }
        })
      }
      if (keyList.size > 0)
        RemoveObjectList(keyList, configStore)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveConfig", null, ErrorCodeConstants.Remove_Config_Successful + ":" + cfgStr)
      apiResult.toString()
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveConfig", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Config_Failed + ":" + cfgStr)
        apiResult.toString()
      }
    }
  }

  def getModelDependencies(modelConfigName: String, userid: Option[String]): List[String] = {
    var config: scala.collection.immutable.Map[String, List[String]] = MdMgr.GetMdMgr.GetModelConfig(modelConfigName)
    config.getOrElse(ModelCompilationConstants.DEPENDENCIES, List[String]())
  }

  def getModelMessagesContainers(modelConfigName: String, userid: Option[String]): List[String] = {
    var config: scala.collection.immutable.Map[String, List[String]] = MdMgr.GetMdMgr.GetModelConfig(modelConfigName)
    config.getOrElse(ModelCompilationConstants.TYPES_DEPENDENCIES, List[String]())
  }

  def getModelConfigNames(): Array[String] = {
    MdMgr.GetMdMgr.GetModelConfigKeys
  }

  /**
   *
   */
  private var cfgmap: Map[String, Any] = null
  def UploadModelsConfig(cfgStr: String, userid: Option[String], objectList: String): String = {
    var keyList = new Array[String](0)
    var valueList = new Array[Array[Byte]](0)
    cfgmap = parse(cfgStr).values.asInstanceOf[Map[String, Any]]

    cfgmap.keys.foreach(key => {
      var mdl = cfgmap(key).asInstanceOf[Map[String, List[String]]]
      // Prepare KEY/VALUE for persistent insertion
      var modelKey = userid.getOrElse("") + "." + key
      var value = serializer.SerializeObjectToByteArray(mdl)
      keyList = keyList :+ modelKey.toLowerCase
      valueList = valueList :+ value
      // Save inmemory
      MdMgr.GetMdMgr.AddModelConfig(modelKey, mdl)
    })
    // Save in Databae
    SaveObjectList(keyList, valueList, modelConfigStore)

    // return reuslts
    var apiResult = new ApiResult(ErrorCodeConstants.Success, "UploadModelsConfig", 0.toString, "Upload of model config successful")
    apiResult.toString()
  }

  private def getStringFromJsonNode(v: Any): String = {
    if (v == null) return ""

    if (v.isInstanceOf[String]) return v.asInstanceOf[String]

    implicit val jsonFormats: Formats = DefaultFormats
    val lst = List(v)
    val str = Serialization.write(lst)
    if (str.size > 2) {
      return str.substring(1, str.size - 1)
    }
    return ""
  }

  /*
  private def getJsonNodeFromString(s: String): Any = {
    if (s.size == 0) return s

    val s1 = "[" + s + "]"
    
    implicit val jsonFormats: Formats = DefaultFormats
    val list = Serialization.read[List[_]](s1)

    return list(0)
  }
*/

  /**
   *
   */
  def UploadConfig(cfgStr: String, userid: Option[String], objectList: String): String = {
    var keyList = new Array[String](0)
    var valueList = new Array[Array[Byte]](0)

    logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.INSERTCONFIG, cfgStr, AuditConstants.SUCCESS, "", objectList)

    try {
      // extract config objects
      val map = JsonSerializer.parseEngineConfig(cfgStr)
      // process clusterInfo object if it exists
      if (map.contains("Clusters") == false) {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UploadConfig", null, ErrorCodeConstants.Upload_Config_Failed + ":" + cfgStr)
        apiResult.toString()
      } else {
        if (map.contains("Clusters")) {
          var globalAdaptersCollected = false // to support previous versions
          val clustersList = map.get("Clusters").get.asInstanceOf[List[_]] //BUGBUG:: Do we need to check the type before converting
          logger.debug("Found " + clustersList.length + " cluster objects ")
          clustersList.foreach(clustny => {
            val cluster = clustny.asInstanceOf[Map[String, Any]] //BUGBUG:: Do we need to check the type before converting
            val ClusterId = cluster.getOrElse("ClusterId", "").toString.trim.toLowerCase
            logger.debug("Processing the cluster => " + ClusterId)
            // save in memory
            var ci = MdMgr.GetMdMgr.MakeCluster(ClusterId, null, null)
            MdMgr.GetMdMgr.AddCluster(ci)
            var key = "ClusterInfo." + ci.clusterId
            var value = serializer.SerializeObjectToByteArray(ci)
            keyList = keyList :+ key.toLowerCase
            valueList = valueList :+ value
            // gather config name-value pairs
            val cfgMap = new scala.collection.mutable.HashMap[String, String]
            if (cluster.contains("DataStore"))
              cfgMap("DataStore") = getStringFromJsonNode(cluster.getOrElse("DataStore", null))
            if (cluster.contains("StatusInfo"))
              cfgMap("StatusInfo") = getStringFromJsonNode(cluster.getOrElse("StatusInfo", null))
            if (cluster.contains("ZooKeeperInfo"))
              cfgMap("ZooKeeperInfo") = getStringFromJsonNode(cluster.getOrElse("ZooKeeperInfo", null))
            if (cluster.contains("EnvironmentContext"))
              cfgMap("EnvironmentContext") = getStringFromJsonNode(cluster.getOrElse("EnvironmentContext", null))
            if (cluster.contains("Config")) {
              val config = cluster.get("Config").get.asInstanceOf[Map[String, Any]] //BUGBUG:: Do we need to check the type before converting
              if (config.contains("DataStore"))
                cfgMap("DataStore") = getStringFromJsonNode(config.get("DataStore"))
              if (config.contains("StatusInfo"))
                cfgMap("StatusInfo") = getStringFromJsonNode(config.get("StatusInfo"))
              if (config.contains("ZooKeeperInfo"))
                cfgMap("ZooKeeperInfo") = getStringFromJsonNode(config.get("ZooKeeperInfo"))
              if (config.contains("EnvironmentContext"))
                cfgMap("EnvironmentContext") = getStringFromJsonNode(config.get("EnvironmentContext"))
            }

            // save in memory
            val cic = MdMgr.GetMdMgr.MakeClusterCfg(ClusterId, cfgMap, null, null)
            MdMgr.GetMdMgr.AddClusterCfg(cic)
            key = "ClusterCfgInfo." + cic.clusterId
            value = serializer.SerializeObjectToByteArray(cic)
            keyList = keyList :+ key.toLowerCase
            valueList = valueList :+ value

            if (cluster.contains("Nodes")) {
              val nodes = cluster.get("Nodes").get.asInstanceOf[List[_]]
              nodes.foreach(n => {
                val node = n.asInstanceOf[Map[String, Any]]
                val nodeId = node.getOrElse("NodeId", "").toString.trim.toLowerCase
                val nodePort = node.getOrElse("NodePort", "0").toString.trim.toInt
                val nodeIpAddr = node.getOrElse("NodeIpAddr", "").toString.trim
                val scala_home = node.getOrElse("Scala_home", "").toString.trim
                val java_home = node.getOrElse("Java_home", "").toString.trim
                val classpath = node.getOrElse("Classpath", "").toString.trim
                val jarPaths = if (node.contains("JarPaths")) node.get("JarPaths").get.asInstanceOf[List[String]] else List[String]()
                val roles = if (node.contains("Roles")) node.get("Roles").get.asInstanceOf[List[String]] else List[String]()

                val validRoles = NodeRole.ValidRoles.map(r => r.toLowerCase).toSet
                val givenRoles = roles
                var foundRoles = ArrayBuffer[String]()
                var notfoundRoles = ArrayBuffer[String]()
                if (givenRoles != null) {
                  val gvnRoles = givenRoles.foreach(r => {
                    if (validRoles.contains(r.toLowerCase))
                      foundRoles += r
                    else
                      notfoundRoles += r
                  })
                  if (notfoundRoles.size > 0) {
                    logger.error("Found invalid node roles:%s for nodeid: %d".format(notfoundRoles.mkString(","), nodeId))
                  }
                }

                val ni = MdMgr.GetMdMgr.MakeNode(nodeId, nodePort, nodeIpAddr, jarPaths,
                  scala_home, java_home, classpath, ClusterId, 0, foundRoles.toArray, null)
                MdMgr.GetMdMgr.AddNode(ni)
                val key = "NodeInfo." + ni.nodeId
                val value = serializer.SerializeObjectToByteArray(ni)
                keyList = keyList :+ key.toLowerCase
                valueList = valueList :+ value
              })
            }

            if (cluster.contains("Adapters") || (globalAdaptersCollected == false && map.contains("Adapters"))) {
              val adapters = if (cluster.contains("Adapters") && (globalAdaptersCollected == false && map.contains("Adapters"))) {
                map.get("Adapters").get.asInstanceOf[List[_]] ++ cluster.get("Adapters").get.asInstanceOf[List[_]]
              } else if (cluster.contains("Adapters")) {
                cluster.get("Adapters").get.asInstanceOf[List[_]]
              } else if (globalAdaptersCollected == false && map.contains("Adapters")) {
                map.get("Adapters").get.asInstanceOf[List[_]]
              } else {
                List[Any]()
              }

              globalAdaptersCollected = true // to support previous versions

              adapters.foreach(a => {
                val adap = a.asInstanceOf[Map[String, Any]]
                val nm = adap.getOrElse("Name", "").toString.trim
                val jarnm = adap.getOrElse("JarName", "").toString.trim
                val typStr = adap.getOrElse("TypeString", "").toString.trim
                val clsNm = adap.getOrElse("ClassName", "").toString.trim

                var depJars: List[String] = null
                if (adap.contains("DependencyJars")) {
                  depJars = adap.get("DependencyJars").get.asInstanceOf[List[String]]
                }
                var ascfg: String = null
                if (adap.contains("AdapterSpecificCfg")) {
                  ascfg = getStringFromJsonNode(adap.get("AdapterSpecificCfg"))
                }
                var inputAdapterToVerify: String = null
                if (adap.contains("InputAdapterToVerify")) {
                  inputAdapterToVerify = adap.get("InputAdapterToVerify").get.asInstanceOf[String]
                }
                var dataFormat: String = null
                if (adap.contains("DataFormat")) {
                  dataFormat = adap.get("DataFormat").get.asInstanceOf[String]
                }
                var delimiterString: String = null
                var associatedMsg: String = null
                if (adap.contains("DelimiterString")) {
                  delimiterString = adap.get("DelimiterString").get.asInstanceOf[String]
                }
                if (adap.contains("AssociatedMessage")) {
                  associatedMsg = adap.get("AssociatedMessage").get.asInstanceOf[String]
                }
                // save in memory
                val ai = MdMgr.GetMdMgr.MakeAdapter(nm, typStr, dataFormat, clsNm, jarnm, depJars, ascfg, inputAdapterToVerify, delimiterString, associatedMsg)
                MdMgr.GetMdMgr.AddAdapter(ai)
                val key = "AdapterInfo." + ai.name
                val value = serializer.SerializeObjectToByteArray(ai)
                keyList = keyList :+ key.toLowerCase
                valueList = valueList :+ value
              })
            } else {
              logger.debug("Found no adapater objects in the config file")
            }
         
            // Now see if there are any other User Defined Properties in this cluster, if there are any, create a container
            // like we did for adapters and noteds, etc....
            var userDefinedProps: Map[String, Any]  = cluster.filter(x => {!excludeList.contains(x._1)} )
            if (userDefinedProps.size > 0) {
              val upProps: UserPropertiesInfo = MdMgr.GetMdMgr.MakeUPProps(ClusterId)
              userDefinedProps.keys.foreach(key => {
                println("Adding key "+key+"-->" + userDefinedProps(key).toString)
                upProps.Props(key) = userDefinedProps(key).toString
              })   
              MdMgr.GetMdMgr.AddUserProperty(upProps)
              val upKey = "userProperties." + upProps.clusterId 
              val upValue = serializer.SerializeObjectToByteArray(upProps)
              keyList = keyList :+ upKey.toLowerCase
              valueList = valueList :+ upValue              
              
            }
          })

        } else {
          logger.debug("Found no adapater objects in the config file")
        }

        SaveObjectList(keyList, valueList, configStore)
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "UploadConfig", cfgStr, ErrorCodeConstants.Upload_Config_Successful)
        apiResult.toString()
      }
    } catch {
      case e: Exception => {
        e.printStackTrace
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UploadConfig", cfgStr, "Error :" + e.toString() + ErrorCodeConstants.Upload_Config_Failed)
        apiResult.toString()
      }
    }
  }
  
  def getUP (ci: String, key: String): String = {
     MdMgr.GetMdMgr.GetUserProperty(ci,key)
  }

  // All available nodes(format JSON) as a String
  def GetAllNodes(formatType: String, userid: Option[String]): String = {
    try {
      val nodes = MdMgr.GetMdMgr.Nodes.values.toArray
      logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETCONFIG, AuditConstants.CONFIG, AuditConstants.SUCCESS, "", "nodes")
      if (nodes.length == 0) {
        logger.debug("No Nodes found ")
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllNodes", null, ErrorCodeConstants.Get_All_Nodes_Failed_Not_Available)
        apiResult.toString()
      } else {
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllNodes", JsonSerializer.SerializeCfgObjectListToJson("Nodes", nodes), ErrorCodeConstants.Get_All_Nodes_Successful)
        apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllNodes", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Nodes_Failed)
        apiResult.toString()
      }
    }
  }

  // All available adapters(format JSON) as a String
  def GetAllAdapters(formatType: String, userid: Option[String]): String = {
    try {
      val adapters = MdMgr.GetMdMgr.Adapters.values.toArray
      logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETCONFIG, AuditConstants.CONFIG, AuditConstants.FAIL, "", "adapters")
      if (adapters.length == 0) {
        logger.debug("No Adapters found ")
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllAdapters", null, ErrorCodeConstants.Get_All_Adapters_Failed_Not_Available)
        apiResult.toString()
      } else {
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllAdapters", JsonSerializer.SerializeCfgObjectListToJson("Adapters", adapters), ErrorCodeConstants.Get_All_Adapters_Successful)
        apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllAdapters", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Adapters_Failed)

        apiResult.toString()
      }
    }
  }

  // All available clusters(format JSON) as a String
  def GetAllClusters(formatType: String, userid: Option[String]): String = {
    try {
      val clusters = MdMgr.GetMdMgr.Clusters.values.toArray
      logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETCONFIG, AuditConstants.CONFIG, AuditConstants.SUCCESS, "", "Clusters")
      if (clusters.length == 0) {
        logger.debug("No Clusters found ")
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllClusters", null, ErrorCodeConstants.Get_All_Clusters_Failed_Not_Available)
        apiResult.toString()
      } else {
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllClusters", JsonSerializer.SerializeCfgObjectListToJson("Clusters", clusters), ErrorCodeConstants.Get_All_Clusters_Successful)
        apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllClusters", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Clusters_Failed)
        apiResult.toString()
      }
    }
  }

  // All available clusterCfgs(format JSON) as a String
  def GetAllClusterCfgs(formatType: String, userid: Option[String]): String = {
    try {
      logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETCONFIG, AuditConstants.CONFIG, AuditConstants.SUCCESS, "", "ClusterCfg")
      val clusterCfgs = MdMgr.GetMdMgr.ClusterCfgs.values.toArray
      if (clusterCfgs.length == 0) {
        logger.debug("No ClusterCfgs found ")
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllClusterCfgs", null, ErrorCodeConstants.Get_All_Cluster_Configs_Failed_Not_Available)
        apiResult.toString()
      } else {
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllClusterCfgs", JsonSerializer.SerializeCfgObjectListToJson("ClusterCfgs", clusterCfgs), ErrorCodeConstants.Get_All_Cluster_Configs_Successful)

        apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllClusterCfgs", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Cluster_Configs_Failed)

        apiResult.toString()
      }
    }
  }

  // All available config objects(format JSON) as a String
  def GetAllCfgObjects(formatType: String, userid: Option[String]): String = {
    var cfgObjList = new Array[Object](0)
    logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETCONFIG, AuditConstants.CONFIG, AuditConstants.SUCCESS, "", "all")
    var jsonStr: String = ""
    var jsonStr1: String = ""
    try {
      val clusters = MdMgr.GetMdMgr.Clusters.values.toArray
      if (clusters.length != 0) {
        cfgObjList = cfgObjList :+ clusters
        jsonStr1 = JsonSerializer.SerializeCfgObjectListToJson("Clusters", clusters)
        jsonStr1 = jsonStr1.substring(1)
        jsonStr1 = JsonSerializer.replaceLast(jsonStr1, "}", ",")
        jsonStr = jsonStr + jsonStr1
      }
      val clusterCfgs = MdMgr.GetMdMgr.ClusterCfgs.values.toArray
      if (clusterCfgs.length != 0) {
        cfgObjList = cfgObjList :+ clusterCfgs
        jsonStr1 = JsonSerializer.SerializeCfgObjectListToJson("ClusterCfgs", clusterCfgs)
        jsonStr1 = jsonStr1.substring(1)
        jsonStr1 = JsonSerializer.replaceLast(jsonStr1, "}", ",")
        jsonStr = jsonStr + jsonStr1
      }
      val nodes = MdMgr.GetMdMgr.Nodes.values.toArray
      if (nodes.length != 0) {
        cfgObjList = cfgObjList :+ nodes
        jsonStr1 = JsonSerializer.SerializeCfgObjectListToJson("Nodes", nodes)
        jsonStr1 = jsonStr1.substring(1)
        jsonStr1 = JsonSerializer.replaceLast(jsonStr1, "}", ",")
        jsonStr = jsonStr + jsonStr1
      }
      val adapters = MdMgr.GetMdMgr.Adapters.values.toArray
      if (adapters.length != 0) {
        cfgObjList = cfgObjList :+ adapters
        jsonStr1 = JsonSerializer.SerializeCfgObjectListToJson("Adapters", adapters)
        jsonStr1 = jsonStr1.substring(1)
        jsonStr1 = JsonSerializer.replaceLast(jsonStr1, "}", ",")
        jsonStr = jsonStr + jsonStr1
      }

      jsonStr = "{" + JsonSerializer.replaceLast(jsonStr, ",", "") + "}"

      if (cfgObjList.length == 0) {
        logger.debug("No Config Objects found ")
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllCfgObjects", null, ErrorCodeConstants.Get_All_Configs_Failed_Not_Available)
        apiResult.toString()
      } else {
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllCfgObjects", jsonStr, ErrorCodeConstants.Get_All_Configs_Successful)
        apiResult.toString()
      }
    } catch {
      case e: Exception => {
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllCfgObjects", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Configs_Failed)
        apiResult.toString()
      }
    }
  }

  def dumpMetadataAPIConfig {
    val e = metadataAPIConfig.propertyNames()
    while (e.hasMoreElements()) {
      val key = e.nextElement().asInstanceOf[String]
      val value = metadataAPIConfig.getProperty(key)
      logger.debug("Key : " + key + ", Value : " + value)
    }
  }

  /**
   * setPropertyFromConfigFile - convert a specific KEY:VALUE pair in the config file into the
   * KEY:VALUE pair in the  Properties object
   */
  private def setPropertyFromConfigFile(key: String, value: String) {
    var finalKey = key
    var finalValue = value

    // JAR_PATHs need to be trimmed 
    if (key.equalsIgnoreCase("JarPaths") || key.equalsIgnoreCase("JAR_PATHS")) {
      val jp = value
      val j_paths = jp.split(",").map(s => s.trim).filter(s => s.size > 0)
      finalValue = j_paths.mkString(",")
      finalKey = "JAR_PATHS"
    }

    // Special case 1. for config.  if JAR_PATHS is never set, then it should default to JAR_TARGET_DIR..
    // so we set the JAR_PATH if it was never set.. no worries, if JAR_PATH comes later, it willsimply
    // overwrite the value.
    if (key.equalsIgnoreCase("JAR_TARGET_DIR") && (metadataAPIConfig.getProperty("JAR_PATHS") == null)) {
      metadataAPIConfig.setProperty("JAR_PATHS", finalValue)
      logger.debug("JAR_PATHS = " + finalValue)
      pList = pList - "JAR_PATHS"
    }

    // Special case 2.. MetadataLocation must set 2 properties in the config object.. 1. prop set by DATABASE_HOST,
    // 2. prop set by DATABASE_LOCATION.  MetadataLocation will overwrite those values, but not the other way around.
    if (key.equalsIgnoreCase("MetadataLocation")) {
      metadataAPIConfig.setProperty("DATABASE_LOCATION", finalValue)
      metadataAPIConfig.setProperty("DATABASE_HOST", finalValue)
      logger.debug("DATABASE_LOCATION  = " + finalValue)
      pList = pList - "DATABASE_LOCATION"
      logger.debug("DATABASE_HOST  = " + finalValue)
      pList = pList - "DATABASE_HOST"
      return
    }

    // SSL_PASSWORD will not be saved in the Config object, since that object is printed out for debugging purposes.
    if (key.equalsIgnoreCase("SSL_PASSWD")) {
      setSSLCertificatePasswd(value)
      return
    }

    // Special case 2a.. DATABASE_HOST should not override METADATA_LOCATION
    if (key.equalsIgnoreCase("DATABASE_HOST") && (metadataAPIConfig.getProperty(key.toUpperCase) != null)) {
      return
    }
    // Special case 2b.. DATABASE_LOCATION should not override METADATA_LOCATION
    if (key.equalsIgnoreCase("DATABASE_LOCATION") && (metadataAPIConfig.getProperty(key.toUpperCase) != null)) {
      return
    }

    // Special case 3: SCHEMA_NAME can come it under several keys, but we store it as DATABASE SCHEMA
    if (key.equalsIgnoreCase("MetadataSchemaName")) {
      finalKey = "DATABASE_SCHEMA"
    }

    if (key.equalsIgnoreCase("MetadataAdapterSpecificConfig")) {
      finalKey = "ADAPTER_SPECIFIC_CONFIG"
    }

    // Special case 4: DATABASE can come under DATABASE or MetaDataStoreType
    if (key.equalsIgnoreCase("DATABASE") || key.equalsIgnoreCase("MetadataStoreType")) {
      finalKey = "DATABASE"
    }

    // Special case 5: NodeId or Node_ID is possible
    if (key.equalsIgnoreCase("NODE_ID") || key.equalsIgnoreCase("NODEID")) {
      finalKey = "NODE_ID"
    }

    if (key.equalsIgnoreCase("MetadataDataStore")) {
      finalKey = "METADATA_DATASTORE"
    }

    // Store the Key/Value pair
    metadataAPIConfig.setProperty(finalKey.toUpperCase, finalValue)
    logger.debug(finalKey.toUpperCase + " = " + finalValue)
    pList = pList - finalKey.toUpperCase
  }

  def RefreshApiConfigForGivenNode(nodeId: String): Boolean = {
    val nd = mdMgr.Nodes.getOrElse(nodeId, null)
    if (nd == null) {
      logger.error("Node %s not found in metadata".format(nodeId))
      return false
    }

    val clusterId = nd.ClusterId

    val cluster = mdMgr.ClusterCfgs.getOrElse(nd.ClusterId, null)
    if (cluster == null) {
      logger.error("Cluster not found for Node %s  & ClusterId : %s".format(nodeId, nd.ClusterId))
      return false
    }

    logger.debug("Configurations for the clusterId:" + clusterId)
    cluster.cfgMap.foreach(kv => {
      logger.debug("Key: %s, Value: %s".format(kv._1, kv._2))
    })

    val zooKeeperInfo = cluster.cfgMap.getOrElse("ZooKeeperInfo", null)
    if (zooKeeperInfo == null) {
      logger.error("ZooKeeperInfo not found for Node %s  & ClusterId : %s".format(nodeId, nd.ClusterId))
      return false
    }
    println("->"+zooKeeperInfo)
    val jarPaths = if (nd.JarPaths == null) Set[String]() else nd.JarPaths.map(str => str.replace("\"", "").trim).filter(str => str.size > 0).toSet
    if (jarPaths.size == 0) {
      logger.error("Not found valid JarPaths.")
      return false
    } else {
      metadataAPIConfig.setProperty("JAR_PATHS", jarPaths.mkString(","))
      logger.debug("JarPaths Based on node(%s) => %s".format(nodeId, jarPaths))
      val jarDir = compact(render(jarPaths(0))).replace("\"", "").trim
      metadataAPIConfig.setProperty("JAR_TARGET_DIR", jarDir)
      logger.debug("Jar_target_dir Based on node(%s) => %s".format(nodeId, jarDir))
    }

    implicit val jsonFormats: Formats = DefaultFormats
    val zKInfo = parse(zooKeeperInfo).extract[JZKInfo]

    val zkConnectString = zKInfo.ZooKeeperConnectString.replace("\"", "").trim
    metadataAPIConfig.setProperty("ZOOKEEPER_CONNECT_STRING", zkConnectString)
    logger.debug("ZOOKEEPER_CONNECT_STRING(based on nodeId) => " + zkConnectString)

    val zkNodeBasePath = zKInfo.ZooKeeperNodeBasePath.replace("\"", "").trim
    metadataAPIConfig.setProperty("ZNODE_PATH", zkNodeBasePath)
    logger.debug("ZNODE_PATH(based on nodeid) => " + zkNodeBasePath)

    val zkSessionTimeoutMs1 = if (zKInfo.ZooKeeperSessionTimeoutMs == None || zKInfo.ZooKeeperSessionTimeoutMs == null) 0 else zKInfo.ZooKeeperSessionTimeoutMs.get.toString.toInt
    // Taking minimum values in case if needed
    val zkSessionTimeoutMs = if (zkSessionTimeoutMs1 <= 0) 1000 else zkSessionTimeoutMs1
    metadataAPIConfig.setProperty("ZK_SESSION_TIMEOUT_MS", zkSessionTimeoutMs.toString)
    logger.debug("ZK_SESSION_TIMEOUT_MS(based on nodeId) => " + zkSessionTimeoutMs)

    val zkConnectionTimeoutMs1 = if (zKInfo.ZooKeeperConnectionTimeoutMs == None || zKInfo.ZooKeeperConnectionTimeoutMs == null) 0 else zKInfo.ZooKeeperConnectionTimeoutMs.get.toString.toInt
    // Taking minimum values in case if needed
    val zkConnectionTimeoutMs = if (zkConnectionTimeoutMs1 <= 0) 30000 else zkConnectionTimeoutMs1
    metadataAPIConfig.setProperty("ZK_CONNECTION_TIMEOUT_MS", zkConnectionTimeoutMs.toString)
    logger.debug("ZK_CONNECTION_TIMEOUT_MS(based on nodeId) => " + zkConnectionTimeoutMs)
    true
  }

  @throws(classOf[MissingPropertyException])
  @throws(classOf[InvalidPropertyException])
  def readMetadataAPIConfigFromPropertiesFile(configFile: String): Unit = {
    try {
      if (propertiesAlreadyLoaded) {
        logger.debug("Configuratin properties already loaded, skipping the load configuration step")
        return ;
      }

      val (prop, failStr) = Utils.loadConfiguration(configFile.toString, true)
      if (failStr != null && failStr.size > 0) {
        logger.error(failStr)
        return
      }
      if (prop == null) {
        logger.error("Failed to load configuration")
        return
      }

      // some zookeper vals can be safely defaulted to.
      setPropertyFromConfigFile("NODE_ID", "Undefined")
      setPropertyFromConfigFile("API_LEADER_SELECTION_ZK_NODE", "/ligadata")
      setPropertyFromConfigFile("ZK_SESSION_TIMEOUT_MS", "3000")
      setPropertyFromConfigFile("ZK_CONNECTION_TIMEOUT_MS", "3000")

      // Loop through and set the rest of the values.
      val eProps1 = prop.propertyNames();
      while (eProps1.hasMoreElements()) {
        val key = eProps1.nextElement().asInstanceOf[String]
        val value = prop.getProperty(key);
        setPropertyFromConfigFile(key, value)
      }

      val mdDataStore = GetMetadataAPIConfig.getProperty("METADATA_DATASTORE")

      if (mdDataStore == null) {
        // Prepare from
        val dbType = GetMetadataAPIConfig.getProperty("DATABASE")
        val dbHost = if (GetMetadataAPIConfig.getProperty("DATABASE_HOST") != null) GetMetadataAPIConfig.getProperty("DATABASE_HOST") else GetMetadataAPIConfig.getProperty("DATABASE_LOCATION")
        val dbSchema = GetMetadataAPIConfig.getProperty("DATABASE_SCHEMA")
        val dbAdapterSpecific = GetMetadataAPIConfig.getProperty("ADAPTER_SPECIFIC_CONFIG")

        val dbType1 = if (dbType == null) "" else dbType.trim
        val dbHost1 = if (dbHost == null) "" else dbHost.trim
        val dbSchema1 = if (dbSchema == null) "" else dbSchema.trim

        if (dbAdapterSpecific != null) {
          val json = ("StoreType" -> dbType1) ~
            ("SchemaName" -> dbSchema1) ~
            ("Location" -> dbHost1) ~
            ("AdapterSpecificConfig" -> dbAdapterSpecific)
          val jsonStr = pretty(render(json))
          setPropertyFromConfigFile("METADATA_DATASTORE", jsonStr)
        } else {
          val json = ("StoreType" -> dbType1) ~
            ("SchemaName" -> dbSchema1) ~
            ("Location" -> dbHost1)
          val jsonStr = pretty(render(json))
          setPropertyFromConfigFile("METADATA_DATASTORE", jsonStr)
        }
      }

      pList.map(v => logger.warn(v + " remains unset"))
      propertiesAlreadyLoaded = true;

    } catch {
      case e: Exception =>
        logger.error("Failed to load configuration: " + e.getMessage)
        sys.exit(1)
    }
  }

  @throws(classOf[MissingPropertyException])
  @throws(classOf[LoadAPIConfigException])
  def readMetadataAPIConfigFromJsonFile(cfgFile: String): Unit = {
    try {
      if (propertiesAlreadyLoaded) {
        return ;
      }
      var configFile = "MetadataAPIConfig.json"
      if (cfgFile != null) {
        configFile = cfgFile
      }

      val configJson = Source.fromFile(configFile).mkString
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(configJson)

      logger.debug("Parsed the json : " + configJson)
      val configMap = json.extract[MetadataAPIConfig]

      var rootDir = configMap.APIConfigParameters.RootDir
      if (rootDir == null) {
        rootDir = System.getenv("HOME")
      }
      logger.debug("RootDir => " + rootDir)

      var gitRootDir = configMap.APIConfigParameters.GitRootDir
      if (gitRootDir == null) {
        gitRootDir = rootDir + "git_hub"
      }
      logger.debug("GitRootDir => " + gitRootDir)

      var database = configMap.APIConfigParameters.MetadataStoreType
      if (database == null) {
        database = "hashmap"
      }
      logger.debug("Database => " + database)

      var databaseLocation = "/tmp"
      var databaseHost = configMap.APIConfigParameters.MetadataLocation
      if (databaseHost == null) {
        databaseHost = "localhost"
      } else {
        databaseLocation = databaseHost
      }
      logger.debug("DatabaseHost => " + databaseHost + ", DatabaseLocation(applicable to treemap or hashmap databases only) => " + databaseLocation)

      var databaseAdapterSpecificConfig = ""
      var metadataDataStore = ""
      /*
      var tmpMdAdapSpecCfg = configMap.APIConfigParameters.MetadataAdapterSpecificConfig
      if (tmpMdAdapSpecCfg != null && tmpMdAdapSpecCfg != None) {
        databaseAdapterSpecificConfig = tmpMdAdapSpecCfg
      }
*/
      logger.debug("DatabaseAdapterSpecificConfig => " + databaseAdapterSpecificConfig)

      var databaseSchema = "metadata"
      val databaseSchemaOpt = configMap.APIConfigParameters.MetadataSchemaName
      if (databaseSchemaOpt != None) {
        databaseSchema = databaseSchemaOpt.get
      }
      logger.debug("DatabaseSchema(applicable to cassandra only) => " + databaseSchema)

      var jarTargetDir = configMap.APIConfigParameters.JarTargetDir
      if (jarTargetDir == null) {
        throw new MissingPropertyException("The property JarTargetDir must be defined in the config file " + configFile)
      }
      logger.debug("JarTargetDir => " + jarTargetDir)

      var jarPaths = jarTargetDir // configMap.APIConfigParameters.JarPaths
      if (jarPaths == null) {
        throw new MissingPropertyException("The property JarPaths must be defined in the config file " + configFile)
      }
      logger.debug("JarPaths => " + jarPaths)

      var scalaHome = configMap.APIConfigParameters.ScalaHome
      if (scalaHome == null) {
        throw new MissingPropertyException("The property ScalaHome must be defined in the config file " + configFile)
      }
      logger.debug("ScalaHome => " + scalaHome)

      var javaHome = configMap.APIConfigParameters.JavaHome
      if (javaHome == null) {
        throw new MissingPropertyException("The property JavaHome must be defined in the config file " + configFile)
      }
      logger.debug("JavaHome => " + javaHome)

      var manifestPath = configMap.APIConfigParameters.ManifestPath
      if (manifestPath == null) {
        throw new MissingPropertyException("The property ManifestPath must be defined in the config file " + configFile)
      }
      logger.debug("ManifestPath => " + manifestPath)

      var classPath = configMap.APIConfigParameters.ClassPath
      if (classPath == null) {
        throw new MissingPropertyException("The property ClassPath must be defined in the config file " + configFile)
      }
      logger.debug("ClassPath => " + classPath)

      var notifyEngine = configMap.APIConfigParameters.NotifyEngine
      if (notifyEngine == null) {
        throw new MissingPropertyException("The property NotifyEngine must be defined in the config file " + configFile)
      }
      logger.debug("NotifyEngine => " + notifyEngine)

      var znodePath = configMap.APIConfigParameters.ZnodePath
      if (znodePath == null) {
        throw new MissingPropertyException("The property ZnodePath must be defined in the config file " + configFile)
      }
      logger.debug("ZNodePath => " + znodePath)

      var zooKeeperConnectString = configMap.APIConfigParameters.ZooKeeperConnectString
      if (zooKeeperConnectString == null) {
        throw new MissingPropertyException("The property ZooKeeperConnectString must be defined in the config file " + configFile)
      }
      logger.debug("ZooKeeperConnectString => " + zooKeeperConnectString)

      var MODEL_FILES_DIR = ""
      val MODEL_FILES_DIR1 = configMap.APIConfigParameters.MODEL_FILES_DIR
      if (MODEL_FILES_DIR1 == None) {
        MODEL_FILES_DIR = gitRootDir + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Models"
      } else
        MODEL_FILES_DIR = MODEL_FILES_DIR1.get
      logger.debug("MODEL_FILES_DIR => " + MODEL_FILES_DIR)

      var TYPE_FILES_DIR = ""
      val TYPE_FILES_DIR1 = configMap.APIConfigParameters.TYPE_FILES_DIR
      if (TYPE_FILES_DIR1 == None) {
        TYPE_FILES_DIR = gitRootDir + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Types"
      } else
        TYPE_FILES_DIR = TYPE_FILES_DIR1.get
      logger.debug("TYPE_FILES_DIR => " + TYPE_FILES_DIR)

      var FUNCTION_FILES_DIR = ""
      val FUNCTION_FILES_DIR1 = configMap.APIConfigParameters.FUNCTION_FILES_DIR
      if (FUNCTION_FILES_DIR1 == None) {
        FUNCTION_FILES_DIR = gitRootDir + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Functions"
      } else
        FUNCTION_FILES_DIR = FUNCTION_FILES_DIR1.get
      logger.debug("FUNCTION_FILES_DIR => " + FUNCTION_FILES_DIR)

      var CONCEPT_FILES_DIR = ""
      val CONCEPT_FILES_DIR1 = configMap.APIConfigParameters.CONCEPT_FILES_DIR
      if (CONCEPT_FILES_DIR1 == None) {
        CONCEPT_FILES_DIR = gitRootDir + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Concepts"
      } else
        CONCEPT_FILES_DIR = CONCEPT_FILES_DIR1.get
      logger.debug("CONCEPT_FILES_DIR => " + CONCEPT_FILES_DIR)

      var MESSAGE_FILES_DIR = ""
      val MESSAGE_FILES_DIR1 = configMap.APIConfigParameters.MESSAGE_FILES_DIR
      if (MESSAGE_FILES_DIR1 == None) {
        MESSAGE_FILES_DIR = gitRootDir + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Messages"
      } else
        MESSAGE_FILES_DIR = MESSAGE_FILES_DIR1.get
      logger.debug("MESSAGE_FILES_DIR => " + MESSAGE_FILES_DIR)

      var CONTAINER_FILES_DIR = ""
      val CONTAINER_FILES_DIR1 = configMap.APIConfigParameters.CONTAINER_FILES_DIR
      if (CONTAINER_FILES_DIR1 == None) {
        CONTAINER_FILES_DIR = gitRootDir + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/Containers"
      } else
        CONTAINER_FILES_DIR = CONTAINER_FILES_DIR1.get

      logger.debug("CONTAINER_FILES_DIR => " + CONTAINER_FILES_DIR)

      var COMPILER_WORK_DIR = ""
      val COMPILER_WORK_DIR1 = configMap.APIConfigParameters.COMPILER_WORK_DIR
      if (COMPILER_WORK_DIR1 == None) {
        COMPILER_WORK_DIR = "/tmp"
      } else
        COMPILER_WORK_DIR = COMPILER_WORK_DIR1.get

      logger.debug("COMPILER_WORK_DIR => " + COMPILER_WORK_DIR)

      var MODEL_EXEC_FLAG = ""
      val MODEL_EXEC_FLAG1 = configMap.APIConfigParameters.MODEL_EXEC_FLAG
      if (MODEL_EXEC_FLAG1 == None) {
        MODEL_EXEC_FLAG = "false"
      } else
        MODEL_EXEC_FLAG = MODEL_EXEC_FLAG1.get

      logger.debug("MODEL_EXEC_FLAG => " + MODEL_EXEC_FLAG)

      val CONFIG_FILES_DIR = gitRootDir + "/Kamanja/trunk/SampleApplication/Medical/Configs"
      logger.debug("CONFIG_FILES_DIR => " + CONFIG_FILES_DIR)

      var OUTPUTMESSAGE_FILES_DIR = ""
      val OUTPUTMESSAGE_FILES_DIR1 = configMap.APIConfigParameters.OUTPUTMESSAGE_FILES_DIR
      if (OUTPUTMESSAGE_FILES_DIR1 == None) {
        OUTPUTMESSAGE_FILES_DIR = gitRootDir + "/Kamanja/trunk/MetadataAPI/src/test/SampleTestFiles/OutputMsgs"
      } else
        OUTPUTMESSAGE_FILES_DIR = OUTPUTMESSAGE_FILES_DIR1.get
      logger.debug("OUTPUTMESSAGE_FILES_DIR => " + OUTPUTMESSAGE_FILES_DIR)

      metadataAPIConfig.setProperty("ROOT_DIR", rootDir)
      metadataAPIConfig.setProperty("GIT_ROOT", gitRootDir)
      metadataAPIConfig.setProperty("DATABASE", database)
      metadataAPIConfig.setProperty("DATABASE_HOST", databaseHost)
      metadataAPIConfig.setProperty("DATABASE_SCHEMA", databaseSchema)
      metadataAPIConfig.setProperty("DATABASE_LOCATION", databaseLocation)
      metadataAPIConfig.setProperty("ADAPTER_SPECIFIC_CONFIG", databaseAdapterSpecificConfig)
      metadataAPIConfig.setProperty("METADATA_DATASTORE", metadataDataStore)
      metadataAPIConfig.setProperty("JAR_TARGET_DIR", jarTargetDir)
      val jp = if (jarPaths != null) jarPaths else jarTargetDir
      val j_paths = jp.split(",").map(s => s.trim).filter(s => s.size > 0)
      metadataAPIConfig.setProperty("JAR_PATHS", j_paths.mkString(","))
      metadataAPIConfig.setProperty("SCALA_HOME", scalaHome)
      metadataAPIConfig.setProperty("JAVA_HOME", javaHome)
      metadataAPIConfig.setProperty("MANIFEST_PATH", manifestPath)
      metadataAPIConfig.setProperty("CLASSPATH", classPath)
      metadataAPIConfig.setProperty("NOTIFY_ENGINE", notifyEngine)
      metadataAPIConfig.setProperty("ZNODE_PATH", znodePath)
      metadataAPIConfig.setProperty("ZOOKEEPER_CONNECT_STRING", zooKeeperConnectString)
      metadataAPIConfig.setProperty("MODEL_FILES_DIR", MODEL_FILES_DIR)
      metadataAPIConfig.setProperty("TYPE_FILES_DIR", TYPE_FILES_DIR)
      metadataAPIConfig.setProperty("FUNCTION_FILES_DIR", FUNCTION_FILES_DIR)
      metadataAPIConfig.setProperty("CONCEPT_FILES_DIR", CONCEPT_FILES_DIR)
      metadataAPIConfig.setProperty("MESSAGE_FILES_DIR", MESSAGE_FILES_DIR)
      metadataAPIConfig.setProperty("CONTAINER_FILES_DIR", CONTAINER_FILES_DIR)
      metadataAPIConfig.setProperty("COMPILER_WORK_DIR", COMPILER_WORK_DIR)
      metadataAPIConfig.setProperty("MODEL_EXEC_LOG", MODEL_EXEC_FLAG)
      metadataAPIConfig.setProperty("CONFIG_FILES_DIR", CONFIG_FILES_DIR)
      metadataAPIConfig.setProperty("OUTPUTMESSAGE_FILES_DIR", OUTPUTMESSAGE_FILES_DIR)

      propertiesAlreadyLoaded = true;

    } catch {
      case e: MappingException => {
        throw Json4sParsingException(e.getMessage())
      }
      case e: Exception => {
        throw LoadAPIConfigException("Failed to load configuration: " + e.getMessage())
      }
    }
  }

  def InitMdMgr(configFile: String) {
    MdMgr.GetMdMgr.truncate
    val mdLoader = new MetadataLoad(MdMgr.mdMgr, "", "", "", "")
    mdLoader.initialize
    if (configFile.endsWith(".json")) {
      MetadataAPIImpl.readMetadataAPIConfigFromJsonFile(configFile)
    } else {
      MetadataAPIImpl.readMetadataAPIConfigFromPropertiesFile(configFile)
    }
    val tmpJarPaths = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS")
    val jarPaths = if (tmpJarPaths != null) tmpJarPaths.split(",").toSet else scala.collection.immutable.Set[String]()
    MetadataAPIImpl.OpenDbStore(jarPaths, GetMetadataAPIConfig.getProperty("METADATA_DATASTORE"))
    MetadataAPIImpl.LoadAllObjectsIntoCache
    MetadataAPIImpl.CloseDbStore
    MetadataAPIImpl.InitSecImpl
    initZkListener
  }

  def InitMdMgrFromBootStrap(configFile: String) {
    MdMgr.GetMdMgr.truncate
    val mdLoader = new MetadataLoad(MdMgr.mdMgr, "", "", "", "")
    mdLoader.initialize
    if (configFile.endsWith(".json")) {
      MetadataAPIImpl.readMetadataAPIConfigFromJsonFile(configFile)
    } else {
      MetadataAPIImpl.readMetadataAPIConfigFromPropertiesFile(configFile)
    }

    initZkListener
    val tmpJarPaths = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS")
    val jarPaths = if (tmpJarPaths != null) tmpJarPaths.split(",").toSet else scala.collection.immutable.Set[String]()
    MetadataAPIImpl.OpenDbStore(jarPaths, GetMetadataAPIConfig.getProperty("METADATA_DATASTORE"))
    MetadataAPIImpl.LoadAllObjectsIntoCache
    MetadataAPIImpl.InitSecImpl
    isInitilized = true
    logger.debug("Metadata synching is now available.")

  }

  /**
   * Create a listener to monitor Meatadata Cache
   */
  def initZkListener: Unit = {
    // Set up a zk listener for metadata invalidation   metadataAPIConfig.getProperty("AUDIT_IMPL_CLASS").trim
    var znodePath = metadataAPIConfig.getProperty("ZNODE_PATH")
    if (znodePath != null) znodePath = znodePath.trim + "/metadataupdate" else return
    var zkConnectString = metadataAPIConfig.getProperty("ZOOKEEPER_CONNECT_STRING")
    if (zkConnectString != null) zkConnectString = zkConnectString.trim else return

    if (zkConnectString != null && zkConnectString.isEmpty() == false && znodePath != null && znodePath.isEmpty() == false) {
      try {
        CreateClient.CreateNodeIfNotExists(zkConnectString, znodePath)
        zkListener = new ZooKeeperListener
        zkListener.CreateListener(zkConnectString, znodePath, UpdateMetadata, 3000, 3000)
      } catch {
        case e: Exception => {
          logger.error("Failed to initialize ZooKeeper Connection. Reason:%s Message:%s".format(e.getCause, e.getMessage))
          throw e
        }
      }
    }
  }

  /**
   * shutdownZkListener - should be called by application using MetadataAPIImpl directly to disable synching of Metadata cache.
   */
  private def shutdownZkListener: Unit = {
    try {
      CloseZKSession
      if (zkListener != null) {
        zkListener.Shutdown
      }
    } catch {
      case e: Exception => {
        logger.error("Error trying to shutdown zookeeper listener.  ")
        throw e
      }
    }
  }

  /**
   * shutdown - call this method to release various resources held by
   */
  def shutdown: Unit = {
    CloseDbStore
    shutdownZkListener
    shutdownAuditAdapter
  }

  /**
   * UpdateMetadata - This is a callback function for the Zookeeper Listener.  It will get called when we detect Metadata being updated from
   *                  a different metadataImpl service.
   */
  def UpdateMetadata(receivedJsonStr: String): Unit = {
    logger.debug("Process ZooKeeper notification " + receivedJsonStr)

    if (receivedJsonStr == null || receivedJsonStr.size == 0 || !isInitilized) {
      // nothing to do
      logger.debug("Metadata synching is not available.")
      return
    }

    val zkTransaction = JsonSerializer.parseZkTransaction(receivedJsonStr, "JSON")
    MetadataAPIImpl.UpdateMdMgr(zkTransaction)
  }

  /**
   *  InitMdMgr -
   */
  def InitMdMgr(mgr: MdMgr, jarPathsInfo: String, databaseInfo: String) {

    SetLoggerLevel(Level.TRACE)
    val mdLoader = new MetadataLoad(mgr, "", "", "", "")
    mdLoader.initialize

    metadataAPIConfig.setProperty("JAR_PATHS", jarPathsInfo)
    metadataAPIConfig.setProperty("METADATA_DATASTORE", databaseInfo)

    val tmpJarPaths = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS")
    val jarPaths = if (tmpJarPaths != null) tmpJarPaths.split(",").toSet else scala.collection.immutable.Set[String]()
    MetadataAPIImpl.OpenDbStore(jarPaths, GetMetadataAPIConfig.getProperty("METADATA_DATASTORE"))
    MetadataAPIImpl.LoadAllObjectsIntoCache
  }
}
