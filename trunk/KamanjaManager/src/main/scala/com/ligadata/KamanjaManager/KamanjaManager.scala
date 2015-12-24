
package com.ligadata.KamanjaManager

import com.ligadata.KamanjaBase._
import com.ligadata.InputOutputAdapterInfo.{ ExecContext, InputAdapter, OutputAdapter, ExecContextObj, PartitionUniqueRecordKey, PartitionUniqueRecordValue }

import scala.reflect.runtime.{ universe => ru }
import scala.util.control.Breaks._
import scala.collection.mutable.ArrayBuffer
import collection.mutable.{ MultiMap, Set }
import java.io.{ PrintWriter, File, PrintStream, BufferedReader, InputStreamReader }
import scala.util.Random
import scala.Array.canBuildFrom
import java.util.{ Properties, Observer, Observable }
import java.sql.Connection
import scala.collection.mutable.TreeSet
import java.net.{ Socket, ServerSocket }
import java.util.concurrent.{ Executors, ScheduledExecutorService, TimeUnit }
import com.ligadata.Utils.{ Utils, KamanjaClassLoader, KamanjaLoaderInfo }
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.HeartBeat.HeartBeatUtil
import com.ligadata.Exceptions.{ FatalAdapterException, StackTrace }

class KamanjaServer(var mgr: KamanjaManager, port: Int) extends Runnable {
  private val LOG = LogManager.getLogger(getClass);
  private val serverSocket = new ServerSocket(port)

  def run() {
    try {
      while (true) {
        // This will block until a connection comes in.
        val socket = serverSocket.accept()
        (new Thread(new ConnHandler(socket, mgr))).start()
      }
    } catch {
      case e: Exception => {
        LOG.error("Socket Error. Reason:%s Message:%s".format(e.getCause, e.getMessage))
      }
    } finally {
      if (serverSocket.isClosed() == false)
        serverSocket.close
    }
  }

  def shutdown() {
    if (serverSocket.isClosed() == false)
      serverSocket.close
  }
}

class ConnHandler(var socket: Socket, var mgr: KamanjaManager) extends Runnable {
  private val LOG = LogManager.getLogger(getClass);
  private val out = new PrintStream(socket.getOutputStream)
  private val in = new BufferedReader(new InputStreamReader(socket.getInputStream))

  def run() {
    val vt = 0
    try {
      breakable {
        while (true) {
          val strLine = in.readLine()
          if (strLine == null)
            break
          mgr.execCmd(strLine)
        }
      }
    } catch {
      case e: Exception => {
        LOG.error("Reason:%s Message:%s".format(e.getCause, e.getMessage))
      }
    } finally {
      socket.close;
    }
  }
}

object KamanjaConfiguration {
  var configFile: String = _
  var allConfigs: Properties = _
  //  var metadataDataStoreInfo: String = _
  var dataDataStoreInfo: String = _
  var statusDataStoreInfo: String = _
  var jarPaths: collection.immutable.Set[String] = _
  var nodeId: Int = _
  var clusterId: String = _
  var nodePort: Int = _
  var zkConnectString: String = _
  var zkNodeBasePath: String = _
  var zkSessionTimeoutMs: Int = _
  var zkConnectionTimeoutMs: Int = _

  var txnIdsRangeForNode: Int = 100000 // Each time get txnIdsRange of transaction ids for each Node
  var txnIdsRangeForPartition: Int = 10000 // Each time get txnIdsRange of transaction ids for each partition

  // Debugging info configs -- Begin
  var waitProcessingSteps = collection.immutable.Set[Int]()
  var waitProcessingTime = 0
  // Debugging info configs -- End

  var shutdown = false
  var participentsChangedCntr: Long = 0
  var baseLoader = new KamanjaLoaderInfo
  var adaptersAndEnvCtxtLoader = new KamanjaLoaderInfo(baseLoader, true, true)
  var metadataLoader = new KamanjaLoaderInfo(baseLoader, true, true)

  var adapterInfoCommitTime = 5000 // Default 5 secs

  def Reset: Unit = {
    configFile = null
    allConfigs = null
    //    metadataDataStoreInfo = null
    dataDataStoreInfo = null
    statusDataStoreInfo = null
    jarPaths = null
    nodeId = 0
    clusterId = null
    nodePort = 0
    zkConnectString = null
    zkNodeBasePath = null
    zkSessionTimeoutMs = 0
    zkConnectionTimeoutMs = 0

    // Debugging info configs -- Begin
    waitProcessingSteps = collection.immutable.Set[Int]()
    waitProcessingTime = 0
    // Debugging info configs -- End

    shutdown = false
    participentsChangedCntr = 0
  }
}

object ProcessedAdaptersInfo {
  private val LOG = LogManager.getLogger(getClass);
  private val lock = new Object
  private val instances = scala.collection.mutable.Map[Int, scala.collection.mutable.Map[String, String]]()
  private var prevAdapterCommittedValues = Map[String, String]()
  private def getAllValues: Map[String, String] = {
    var maps: List[scala.collection.mutable.Map[String, String]] = null
    lock.synchronized {
      maps = instances.map(kv => kv._2).toList
    }

    var retVals = scala.collection.mutable.Map[String, String]()
    maps.foreach(m => retVals ++= m)
    retVals.toMap
  }

  def getOneInstance(hashCode: Int, createIfNotExists: Boolean): scala.collection.mutable.Map[String, String] = {
    lock.synchronized {
      val inst = instances.getOrElse(hashCode, null)
      if (inst != null) {
        return inst
      }
      if (createIfNotExists == false)
        return null

      val newInst = scala.collection.mutable.Map[String, String]()
      instances(hashCode) = newInst
      return newInst
    }
  }

  def clearInstances: Unit = {
    var maps: List[scala.collection.mutable.Map[String, String]] = null
    lock.synchronized {
      instances.clear()
      prevAdapterCommittedValues
    }
  }

  def CommitAdapterValues: Boolean = {
    LOG.debug("CommitAdapterValues. AdapterCommitTime: " + KamanjaConfiguration.adapterInfoCommitTime)
    var committed = false
    if (KamanjaMetadata.envCtxt != null) {
      // Try to commit now
      var changedValues: List[(String, String)] = null
      val newValues = getAllValues
      if (prevAdapterCommittedValues.size == 0) {
        changedValues = newValues.toList
      } else {
        var changedArr = ArrayBuffer[(String, String)]()
        newValues.foreach(v1 => {
          val oldVal = prevAdapterCommittedValues.getOrElse(v1._1, null)
          if (oldVal == null || v1._2.equals(oldVal) == false) { // It is not found or changed, simple take it
            changedArr += v1
          }
        })
        changedValues = changedArr.toList
      }
      // Commit here
      try {
        if (changedValues.size > 0)
          KamanjaMetadata.envCtxt.setAdapterUniqKeyAndValues(changedValues)
        prevAdapterCommittedValues = newValues
        committed = true
      } catch {
        case e: Exception => {
          LOG.error("Failed to commit adapter changes. if we can not save this we will reprocess the information when service restarts. Reason:%s Message:%s".format(e.getCause, e.getMessage))
          val stackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("StackTrace:" + stackTrace)
        }
        case e: Throwable => {
          LOG.error("Failed to commit adapter changes. if we can not save this we will reprocess the information when service restarts. Reason:%s Message:%s".format(e.getCause, e.getMessage))
          val stackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("StackTrace:" + stackTrace)
        }
      }

    }
    committed
  }
}

class KamanjaManager extends Observer {
  private val LOG = LogManager.getLogger(getClass);

  // KamanjaServer Object
  private var serviceObj: KamanjaServer = null

  private val inputAdapters = new ArrayBuffer[InputAdapter]
  private val outputAdapters = new ArrayBuffer[OutputAdapter]
  private val statusAdapters = new ArrayBuffer[OutputAdapter]
  private val validateInputAdapters = new ArrayBuffer[InputAdapter]
  private var heartBeat: HeartBeatUtil = null

  private type OptionMap = Map[Symbol, Any]

  private def PrintUsage(): Unit = {
    LOG.warn("Available commands:")
    LOG.warn("    Quit")
    LOG.warn("    Help")
    LOG.warn("    --config <configfilename>")
  }

  private def Shutdown(exitCode: Int): Int = {
    /*
    if (KamanjaMetadata.envCtxt != null)
      KamanjaMetadata.envCtxt.PersistRemainingStateEntriesOnLeader
*/
    if (heartBeat != null)
      heartBeat.Shutdown
    heartBeat = null
    KamanjaLeader.Shutdown
    KamanjaMetadata.Shutdown
    ShutdownAdapters
    if (KamanjaMetadata.envCtxt != null)
      KamanjaMetadata.envCtxt.Shutdown
    if (serviceObj != null)
      serviceObj.shutdown
    com.ligadata.transactions.NodeLevelTransService.Shutdown
    return exitCode
  }

  private def nextOption(map: OptionMap, list: List[String]): OptionMap = {
    def isSwitch(s: String) = (s(0) == '-')
    list match {
      case Nil => map
      case "--config" :: value :: tail =>
        nextOption(map ++ Map('config -> value), tail)
      case option :: tail => {
        LOG.error("Unknown option " + option)
        throw new Exception("Unknown option " + option)
      }
    }
  }

  private def LoadDynamicJarsIfRequired(loadConfigs: Properties): Boolean = {
    val dynamicjars: String = loadConfigs.getProperty("dynamicjars".toLowerCase, "").trim

    if (dynamicjars != null && dynamicjars.length() > 0) {
      val jars = dynamicjars.split(",").map(_.trim).filter(_.length() > 0)
      if (jars.length > 0) {
        val qualJars = jars.map(j => Utils.GetValidJarFile(KamanjaConfiguration.jarPaths, j))
        val nonExistsJars = Utils.CheckForNonExistanceJars(qualJars.toSet)
        if (nonExistsJars.size > 0) {
          LOG.error("Not found jars in given Dynamic Jars List : {" + nonExistsJars.mkString(", ") + "}")
          return false
        }
        return Utils.LoadJars(qualJars.toArray, KamanjaConfiguration.baseLoader.loadedJars, KamanjaConfiguration.baseLoader.loader)
      }
    }

    true
  }

  private def ShutdownAdapters: Boolean = {
    LOG.debug("Shutdown Adapters started @ " + Utils.GetCurDtTmStr)
    val s0 = System.nanoTime

    validateInputAdapters.foreach(ia => {
      try {
        ia.Shutdown
      } catch {
        case fae: FatalAdapterException => {
          val causeStackTrace = StackTrace.ThrowableTraceString(fae.cause)
          LOG.error("Validate adapter " + ia.UniqueName + "failed to shutdown, cause: \n" + causeStackTrace)
        }
        case e: Exception => {
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("Validate adapter " + ia.UniqueName + "failed to shutdown, cause: \n" + causeStackTrace)
        }
        case e: Throwable => {
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("Validate adapter " + ia.UniqueName + "failed to shutdown, cause: \n" + causeStackTrace)
        }
      }
    })

    validateInputAdapters.clear

    inputAdapters.foreach(ia => {
      try {
        ia.Shutdown
      } catch {
        case fae: FatalAdapterException => {
          val causeStackTrace = StackTrace.ThrowableTraceString(fae.cause)
          LOG.error("Input adapter " + ia.UniqueName + "failed to shutdown, cause: \n" + causeStackTrace)
        }
        case e: Exception => {
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("Input adapter " + ia.UniqueName + "failed to shutdown, cause: \n" + causeStackTrace)
        }
        case e: Throwable => {
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("Input adapter " + ia.UniqueName + "failed to shutdown, cause: \n" + causeStackTrace)
        }
      }
    })

    inputAdapters.clear

    outputAdapters.foreach(oa => {
      try {
        oa.Shutdown
      } catch {
        case fae: FatalAdapterException => {
          val causeStackTrace = StackTrace.ThrowableTraceString(fae.cause)
          LOG.error("Output adapter failed to shutdown, cause: \n" + causeStackTrace)
        }
        case e: Exception => {
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("Output adapter failed to shutdown, cause: \n" + causeStackTrace)
        }
        case e: Throwable => {
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("Output adapter failed to shutdown, cause: \n" + causeStackTrace)
        }
      }
    })

    outputAdapters.clear

    statusAdapters.foreach(oa => {
      try {
        oa.Shutdown
      } catch {
        case fae: FatalAdapterException => {
          val causeStackTrace = StackTrace.ThrowableTraceString(fae.cause)
          LOG.error("Status adapter failed to shutdown, cause: \n" + causeStackTrace)
        }
        case e: Exception => {
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("Status adapter failed to shutdown, cause: \n" + causeStackTrace)
        }
        case e: Throwable => {
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("Status adapter failed to shutdown, cause: \n" + causeStackTrace)
        }
      }
    })

    statusAdapters.clear

    val totaltm = "TimeConsumed:%.02fms".format((System.nanoTime - s0) / 1000000.0);
    LOG.debug("Shutdown Adapters done @ " + Utils.GetCurDtTmStr + ". " + totaltm)

    true
  }

  private def initialize: Boolean = {
    var retval: Boolean = true

    val loadConfigs = KamanjaConfiguration.allConfigs

    try {
      KamanjaConfiguration.nodeId = loadConfigs.getProperty("nodeId".toLowerCase, "0").replace("\"", "").trim.toInt
      if (KamanjaConfiguration.nodeId <= 0) {
        LOG.error("Not found valid nodeId. It should be greater than 0")
        return false
      }

      try {
        val adapterCommitTime = loadConfigs.getProperty("AdapterCommitTime".toLowerCase, "0").replace("\"", "").trim.toInt
        if (adapterCommitTime > 0) {
          KamanjaConfiguration.adapterInfoCommitTime = adapterCommitTime
        }
      } catch {
        case e: Exception => {}
      }

      try {
        KamanjaConfiguration.waitProcessingTime = loadConfigs.getProperty("waitProcessingTime".toLowerCase, "0").replace("\"", "").trim.toInt
        if (KamanjaConfiguration.waitProcessingTime > 0) {
          val setps = loadConfigs.getProperty("waitProcessingSteps".toLowerCase, "").replace("\"", "").split(",").map(_.trim).filter(_.length() > 0)
          if (setps.size > 0)
            KamanjaConfiguration.waitProcessingSteps = setps.map(_.toInt).toSet
        }
      } catch {
        case e: Exception => {}
      }

      LOG.debug("Initializing metadata bootstrap")
      KamanjaMetadata.InitBootstrap

      if (KamanjaMdCfg.InitConfigInfo == false)
        return false

      var engineLeaderZkNodePath = ""
      var engineDistributionZkNodePath = ""
      var metadataUpdatesZkNodePath = ""
      var adaptersStatusPath = ""
      var dataChangeZkNodePath = ""
      var zkHeartBeatNodePath = ""

      if (KamanjaConfiguration.zkNodeBasePath.size > 0) {
        val zkNodeBasePath = KamanjaConfiguration.zkNodeBasePath.stripSuffix("/").trim
        KamanjaConfiguration.zkNodeBasePath = zkNodeBasePath
        engineLeaderZkNodePath = zkNodeBasePath + "/engineleader"
        engineDistributionZkNodePath = zkNodeBasePath + "/enginedistribution"
        metadataUpdatesZkNodePath = zkNodeBasePath + "/metadataupdate"
        adaptersStatusPath = zkNodeBasePath + "/adaptersstatus"
        dataChangeZkNodePath = zkNodeBasePath + "/datachange"
        zkHeartBeatNodePath = zkNodeBasePath + "/monitor/engine/" + KamanjaConfiguration.nodeId.toString
      }

      LOG.debug("Validating required jars")
      KamanjaMdCfg.ValidateAllRequiredJars

      LOG.debug("Load Environment Context")
      KamanjaMetadata.envCtxt = KamanjaMdCfg.LoadEnvCtxt
      if (KamanjaMetadata.envCtxt == null)
        return false

      KamanjaMetadata.gNodeContext = new NodeContext(KamanjaMetadata.envCtxt)

      LOG.debug("Loading Adapters")
      // Loading Adapters (Do this after loading metadata manager & models & Dimensions (if we are loading them into memory))
      retval = KamanjaMdCfg.LoadAdapters(inputAdapters, outputAdapters, statusAdapters, validateInputAdapters)

      if (retval) {
        LOG.debug("Initialize Metadata Manager")
        KamanjaMetadata.InitMdMgr(KamanjaConfiguration.zkConnectString, metadataUpdatesZkNodePath, KamanjaConfiguration.zkSessionTimeoutMs, KamanjaConfiguration.zkConnectionTimeoutMs)
        KamanjaMetadata.envCtxt.CacheContainers(KamanjaConfiguration.clusterId) // Load data for Caching
        LOG.debug("Initializing Leader")

        var txnCtxt: TransactionContext = null
        var txnId = KamanjaConfiguration.nodeId.toString.hashCode()
        if (txnId > 0)
          txnId = -1 * txnId
        // Finally we are taking -ve txnid for this
        try {
          txnCtxt = new TransactionContext(txnId, KamanjaMetadata.gNodeContext, Array[Byte](), "")
          ThreadLocalStorage.txnContextInfo.set(txnCtxt)

          val (tmpMdls, tMdlsChangedCntr) = KamanjaMetadata.getAllModels
          val tModels = if (tmpMdls != null) tmpMdls else Array[(String, MdlInfo)]()

          tModels.foreach(tup => {
            tup._2.mdl.init(txnCtxt)
          })
        } catch {
          case e: Exception => throw e
          case e: Throwable => throw e
        } finally {
          ThreadLocalStorage.txnContextInfo.remove
          if (txnCtxt != null) {
            KamanjaMetadata.gNodeContext.getEnvCtxt.rollbackData(txnId)
          }
        }

        KamanjaLeader.Init(KamanjaConfiguration.nodeId.toString, KamanjaConfiguration.zkConnectString, engineLeaderZkNodePath, engineDistributionZkNodePath, adaptersStatusPath, inputAdapters, outputAdapters, statusAdapters, validateInputAdapters, KamanjaMetadata.envCtxt, KamanjaConfiguration.zkSessionTimeoutMs, KamanjaConfiguration.zkConnectionTimeoutMs, dataChangeZkNodePath)
      }

      if (retval && zkHeartBeatNodePath.size > 0) {
        heartBeat = new HeartBeatUtil
        heartBeat.Init(KamanjaConfiguration.nodeId.toString, KamanjaConfiguration.zkConnectString, zkHeartBeatNodePath, KamanjaConfiguration.zkSessionTimeoutMs, KamanjaConfiguration.zkConnectionTimeoutMs, 5000) // for every 5 secs
        heartBeat.SetMainData(KamanjaConfiguration.nodeId.toString)
      }

      /*
      if (retval) {
        try {
          serviceObj = new KamanjaServer(this, KamanjaConfiguration.nodePort)
          (new Thread(serviceObj)).start()
        } catch {
          case e: Exception => {
            LOG.error("Failed to create server to accept connection on port:" + nodePort+ ". Reason:" + e.getCause + ". Message:" + e.getMessage)
            retval = false
          }
        }
      }
*/

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.error("Failed to initialize. Reason:%s Message:%s\nStackTrace:%s".format(e.getCause, e.getMessage, stackTrace))
        retval = false
      }
    } finally {

    }

    return retval
  }

  def execCmd(ln: String): Boolean = {
    if (ln.length() > 0) {
      val trmln = ln.trim
      if (trmln.length() > 0 && (trmln.compareToIgnoreCase("Quit") == 0 || trmln.compareToIgnoreCase("Exit") == 0))
        return true
    }
    return false;
  }

  def update(o: Observable, arg: AnyRef): Unit = {
    val sig = arg.toString
    LOG.debug("Received signal: " + sig)
    if (sig.compareToIgnoreCase("SIGTERM") == 0 || sig.compareToIgnoreCase("SIGINT") == 0 || sig.compareToIgnoreCase("SIGABRT") == 0) {
      LOG.warn("Got " + sig + " signal. Shutting down the process")
      KamanjaConfiguration.shutdown = true
    }
  }

  def run(args: Array[String]): Int = {
    KamanjaConfiguration.Reset
    KamanjaLeader.Reset
    if (args.length == 0) {
      PrintUsage()
      return Shutdown(1)
    }

    val options = nextOption(Map(), args.toList)
    val cfgfile = options.getOrElse('config, null)
    if (cfgfile == null) {
      LOG.error("Need configuration file as parameter")
      return Shutdown(1)
    }

    KamanjaConfiguration.configFile = cfgfile.toString
    val (loadConfigs, failStr) = Utils.loadConfiguration(KamanjaConfiguration.configFile, true)
    if (failStr != null && failStr.size > 0) {
      LOG.error(failStr)
      return Shutdown(1)
    }
    if (loadConfigs == null) {
      return Shutdown(1)
    }

    KamanjaConfiguration.allConfigs = loadConfigs

    {
      // Printing all configuration
      LOG.info("Configurations:")
      val it = loadConfigs.entrySet().iterator()
      val lowercaseconfigs = new Properties()
      while (it.hasNext()) {
        val entry = it.next();
        LOG.info("\t" + entry.getKey().asInstanceOf[String] + " -> " + entry.getValue().asInstanceOf[String])
      }
      LOG.info("\n")
    }

    if (LoadDynamicJarsIfRequired(loadConfigs) == false) {
      return Shutdown(1)
    }

    if (initialize == false) {
      return Shutdown(1)
    }

    val exceptionStatusAdaps = scala.collection.mutable.Set[String]()
    var curCntr = 0
    val maxFailureCnt = 30

    val statusPrint_PD = new Runnable {
      def run() {
        val stats: scala.collection.immutable.Map[String, Long] = SimpleStats.copyMap
        val statsStr = stats.mkString("~")
        val dispStr = "PD,%d,%s,%s".format(KamanjaConfiguration.nodeId, Utils.GetCurDtTmStr, statsStr)

        if (statusAdapters != null) {
          curCntr += 1
          statusAdapters.foreach(sa => {
            val adapNm = sa.inputConfig.Name
            val alreadyFailed = (exceptionStatusAdaps.size > 0 && exceptionStatusAdaps.contains(adapNm))
            try {
              if (alreadyFailed == false || curCntr >= maxFailureCnt) {
                sa.send(dispStr, "1")
                if (alreadyFailed)
                  exceptionStatusAdaps -= adapNm
              }
            } catch {
              case fae: FatalAdapterException => {
                val causeStackTrace = StackTrace.ThrowableTraceString(fae.cause)
                LOG.error("Failed to send data to status adapter:" + adapNm + "\n.Internal Cause:" + causeStackTrace)
                if (alreadyFailed == false)
                  exceptionStatusAdaps += adapNm
              }
              case e: Exception => {
                val stackTrace = StackTrace.ThrowableTraceString(e)
                LOG.error("Failed to send data to status adapter:" + adapNm + "\n.Stack Trace:" + stackTrace)
                if (alreadyFailed == false)
                  exceptionStatusAdaps += adapNm
              }
              case t: Throwable => {
                val stackTrace = StackTrace.ThrowableTraceString(t)
                LOG.error("Failed to send data to status adapter:" + adapNm + "\n.Stack Trace:" + stackTrace)
                if (alreadyFailed == false)
                  exceptionStatusAdaps += adapNm
              }
            }
          })
          if (curCntr >= maxFailureCnt)
            curCntr = 0
        } else {
          LOG.info(dispStr)
        }
      }
    }

    val scheduledThreadPool = Executors.newScheduledThreadPool(2);

    scheduledThreadPool.scheduleWithFixedDelay(statusPrint_PD, 0, 1000, TimeUnit.MILLISECONDS);

    /**
     * print("=> ")
     * breakable {
     * for (ln <- io.Source.stdin.getLines) {
     * val rv = execCmd(ln)
     * if (rv)
     * break;
     * print("=> ")
     * }
     * }
     */

    var timeOutEndTime: Long = 0
    var participentsChangedCntr: Long = 0
    var lookingForDups = false
    var cntr: Long = 0
    var prevParticipents = ""

    val nodeNameToSetZk = KamanjaConfiguration.nodeId.toString

    var sh: SignalHandler = null
    try {
      sh = new SignalHandler()
      sh.addObserver(this)
      sh.handleSignal("TERM")
      sh.handleSignal("INT")
      sh.handleSignal("ABRT")
    } catch {
      case e: Throwable => {
        LOG.error("Failed to add signal handler.\nStacktrace:" + StackTrace.ThrowableTraceString(e))
      }
    }

    var nextAdapterValuesCommit = System.currentTimeMillis + KamanjaConfiguration.adapterInfoCommitTime

    LOG.warn("KamanjaManager is running now. Waiting for user to terminate with SIGTERM, SIGINT or SIGABRT signals")
    while (KamanjaConfiguration.shutdown == false) { // Infinite wait for now 
      if (KamanjaMetadata.envCtxt != null && nextAdapterValuesCommit < System.currentTimeMillis) {
        if (ProcessedAdaptersInfo.CommitAdapterValues)
          nextAdapterValuesCommit = System.currentTimeMillis + KamanjaConfiguration.adapterInfoCommitTime
      }
      cntr = cntr + 1
      if (participentsChangedCntr != KamanjaConfiguration.participentsChangedCntr) {
        val dispWarn = (lookingForDups && timeOutEndTime > 0)
        lookingForDups = false
        timeOutEndTime = 0
        participentsChangedCntr = KamanjaConfiguration.participentsChangedCntr
        val cs = KamanjaLeader.GetClusterStatus
        if (cs.leader != null && cs.participants != null && cs.participants.size > 0) {
          if (dispWarn) {
            LOG.warn("Got new participents. Trying to see whether the node still has duplicates participents. Previous Participents:{%s} Current Participents:{%s}".format(prevParticipents, cs.participants.mkString(",")))
          }
          prevParticipents = ""
          val isNotLeader = (cs.isLeader == false || cs.leader != cs.nodeId)
          if (isNotLeader) {
            val sameNodeIds = cs.participants.filter(p => p == cs.nodeId)
            if (sameNodeIds.size > 1) {
              lookingForDups = true
              var mxTm = if (KamanjaConfiguration.zkSessionTimeoutMs > KamanjaConfiguration.zkConnectionTimeoutMs) KamanjaConfiguration.zkSessionTimeoutMs else KamanjaConfiguration.zkConnectionTimeoutMs
              if (mxTm < 5000) // if the value is < 5secs, we are taking 5 secs
                mxTm = 5000
              timeOutEndTime = System.currentTimeMillis + mxTm + 2000 // waiting another 2secs
              LOG.error("Found more than one of NodeId:%s in Participents:{%s}. Waiting for %d milli seconds to check whether it is real duplicate or not.".format(cs.nodeId, cs.participants.mkString(","), mxTm))
              prevParticipents = cs.participants.mkString(",")
            }
          }
        }
      }

      if (lookingForDups && timeOutEndTime > 0) {
        if (timeOutEndTime < System.currentTimeMillis) {
          lookingForDups = false
          timeOutEndTime = 0
          val cs = KamanjaLeader.GetClusterStatus
          if (cs.leader != null && cs.participants != null && cs.participants.size > 0) {
            val isNotLeader = (cs.isLeader == false || cs.leader != cs.nodeId)
            if (isNotLeader) {
              val sameNodeIds = cs.participants.filter(p => p == cs.nodeId)
              if (sameNodeIds.size > 1) {
                LOG.error("Found more than one of NodeId:%s in Participents:{%s} for ever. Shutting down this node.".format(cs.nodeId, cs.participants.mkString(",")))
                KamanjaConfiguration.shutdown = true
              }
            }
          }
        }
      }

      try {
        Thread.sleep(500) // Waiting for 500 milli secs
      } catch {
        case e: Exception => {
          val stackTrace = StackTrace.ThrowableTraceString(e)
          LOG.debug("\nStackTrace:" + stackTrace)
        }
      }
      if (heartBeat != null && (cntr % 2 == 1)) {
        heartBeat.SetMainData(nodeNameToSetZk)
      }
    }

    scheduledThreadPool.shutdownNow()
    sh = null
    return Shutdown(0)
  }

  class SignalHandler extends Observable with sun.misc.SignalHandler {
    def handleSignal(signalName: String) {
      sun.misc.Signal.handle(new sun.misc.Signal(signalName), this)
    }
    def handle(signal: sun.misc.Signal) {
      setChanged()
      notifyObservers(signal)
    }
  }
}

object OleService {
  private val LOG = LogManager.getLogger(getClass);
  def main(args: Array[String]): Unit = {
    val mgr = new KamanjaManager
    scala.sys.addShutdownHook({
      if (KamanjaConfiguration.shutdown == false) {
        LOG.warn("Got Shutdown request")
        KamanjaConfiguration.shutdown = true // Setting the global shutdown
      }
    })

    sys.exit(mgr.run(args))
  }
}

