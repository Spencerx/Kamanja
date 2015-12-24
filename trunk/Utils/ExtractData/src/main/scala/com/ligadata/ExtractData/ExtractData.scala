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

package com.ligadata.ExtractData

import scala.reflect.runtime.universe
import scala.collection.mutable.ArrayBuffer
import java.util.Properties
import org.apache.logging.log4j.{ Logger, LogManager }
import java.io.{ OutputStream, FileOutputStream, File, BufferedWriter, Writer, PrintWriter }
import java.util.zip.GZIPOutputStream
import java.nio.file.{ Paths, Files }
import scala.reflect.runtime.{ universe => ru }
import scala.collection.mutable.TreeSet
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.ligadata.Serialize._
import com.ligadata.MetadataAPI.MetadataAPIImpl
import com.ligadata.kamanja.metadata.MdMgr._
import com.ligadata.kamanja.metadata._
import com.ligadata.keyvaluestore._
import com.ligadata.KamanjaBase._
import com.ligadata.kamanja.metadataload.MetadataLoad
import com.ligadata.Utils.{ Utils, KamanjaClassLoader, KamanjaLoaderInfo }
import com.ligadata.Serialize.{ JDataStore }
import com.ligadata.KvBase.{ Key, Value, TimeRange }
import com.ligadata.StorageBase.{ DataStore, Transaction }
import com.ligadata.Exceptions.StackTrace
import java.util.Date
import java.text.SimpleDateFormat

object ExtractData extends MdBaseResolveInfo {
  private val LOG = LogManager.getLogger(getClass);
  private val clsLoaderInfo = new KamanjaLoaderInfo
  private var _currentMessageObj: BaseMsgObj = null
  private var _currentContainerObj: BaseContainerObj = null
  private var _currentTypName: String = ""
  private var jarPaths: Set[String] = null
  private var _dataStore: DataStore = null

  private type OptionMap = Map[Symbol, Any]

  private def PrintUsage(): Unit = {
    LOG.warn("Available options:")
    LOG.warn("    --config <configfilename>")
  }

  override def getMessgeOrContainerInstance(MsgContainerType: String): MessageContainerBase = {
    if (MsgContainerType.compareToIgnoreCase(_currentTypName) == 0) {
      if (_currentMessageObj != null)
        return _currentMessageObj.CreateNewMessage
      if (_currentContainerObj != null)
        return _currentContainerObj.CreateNewContainer
    }
    return null
  }

  private def LoadJars(elem: BaseElem): Boolean = {
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

    val jars = allJars.map(j => Utils.GetValidJarFile(jarPaths, j))

    // Loading all jars
    for (j <- jars) {
      val jarNm = j.trim
      LOG.debug("%s:Processing Jar: %s".format(GetCurDtTmStr, jarNm))
      val fl = new File(jarNm)
      if (fl.exists) {
        try {
          if (clsLoaderInfo.loadedJars(fl.getPath())) {
            LOG.debug("%s:Jar %s already loaded to class path.".format(GetCurDtTmStr, jarNm))
          } else {
            clsLoaderInfo.loader.addURL(fl.toURI().toURL())
            LOG.debug("%s:Jar %s added to class path.".format(GetCurDtTmStr, jarNm))
            clsLoaderInfo.loadedJars += fl.getPath()
          }
        } catch {
          case e: Exception => {
            val errMsg = "Jar " + jarNm + " failed added to class path. Reason:%s Message:%s".format(e.getCause, e.getMessage)
            logger.error("Error:" + errMsg)
            throw new Exception(errMsg)
          }
        }
      } else {
        val errMsg = "Jar " + jarNm + " not found"
        throw new Exception(errMsg)
      }
    }

    return true
  }

  private def serializeObject(ob: Object): String = {
    // val gson = new GsonBuilder().setPrettyPrinting().create();
    val gson = new Gson();
    gson.toJson(ob)
  }

  private def serializeObject(gson: Gson, ob: Object): String = {
    gson.toJson(ob)
  }

  private def nextOption(map: OptionMap, list: List[String]): OptionMap = {
    def isSwitch(s: String) = (s(0) == '-')
    list match {
      case Nil => map
      case "--config" :: value :: tail =>
        nextOption(map ++ Map('config -> value), tail)
      case option :: tail => {
        LOG.error("%s:Unknown option:%s".format(GetCurDtTmStr, option))
        sys.exit(1)
      }
    }
  }

  private def GetCurDtTmStr: String = {
    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(System.currentTimeMillis))
  }

  private def resolveCurretType(typName: String): Unit = {
    val typ = mdMgr.ActiveType(typName)
    if (typ == null || typ == None) {
      LOG.error("Not found valid type for " + typName)
      sys.exit(1)
    }

    if (LoadJars(typ) == false) {
      LOG.error("Failed to load dependency jars for type " + typName)
      sys.exit(1)
    }

    var isMsg = false
    var isContainer = false

    var clsName = typ.PhysicalName.trim
    if (clsName.size > 0 && clsName.charAt(clsName.size - 1) != '$') // if no $ at the end we are taking $
      clsName = clsName + "$"

    if (isMsg == false) {
      // Checking for Message
      try {
        try {
          Class.forName(clsName, true, clsLoaderInfo.loader)
        } catch {
          case e: Exception => {
            logger.error("Failed to load Message class %s with Reason:%s Message:%s".format(clsName, e.getCause, e.getMessage))
            sys.exit(1)
          }
        }

        // Convert class name into a class
        var curClz = Class.forName(clsName, true, clsLoaderInfo.loader)

        while (curClz != null && isContainer == false) {
          isContainer = Utils.isDerivedFrom(curClz, "com.ligadata.KamanjaBase.BaseContainerObj")
          if (isContainer == false)
            curClz = curClz.getSuperclass()
        }
      } catch {
        case e: Exception => {
          LOG.error("Failed to get classname:%s as message".format(clsName))
          sys.exit(1)
        }
      }
    }

    if (isContainer == false) {
      // Checking for container
      try {
        try {
          Class.forName(clsName, true, clsLoaderInfo.loader)
        } catch {
          case e: Exception => {
            logger.error("Failed to load Container class %s with Reason:%s Message:%s".format(clsName, e.getCause, e.getMessage))
            sys.exit(1)
          }
        }

        // Convert class name into a class
        var curClz = Class.forName(clsName, true, clsLoaderInfo.loader)

        while (curClz != null && isMsg == false) {
          isMsg = Utils.isDerivedFrom(curClz, "com.ligadata.KamanjaBase.BaseMsgObj")
          if (isMsg == false)
            curClz = curClz.getSuperclass()
        }
      } catch {
        case e: Exception => {
          LOG.error("Failed to get classname:%s as container".format(clsName))
          sys.exit(1)
        }
      }
    }

    if (isMsg || isContainer) {
      try {
        val mirror = ru.runtimeMirror(clsLoaderInfo.loader)
        val module = mirror.staticModule(clsName)
        val obj = mirror.reflectModule(module)
        val objinst = obj.instance
        if (objinst.isInstanceOf[BaseMsgObj]) {
          _currentMessageObj = objinst.asInstanceOf[BaseMsgObj]
          _currentTypName = typName
          LOG.debug("Created Message Object")
        } else if (objinst.isInstanceOf[BaseContainerObj]) {
          _currentContainerObj = objinst.asInstanceOf[BaseContainerObj]
          _currentTypName = typName
          LOG.debug("Created Container Object")
        } else {
          LOG.error("Failed to instantiate message or conatiner object :" + clsName)
          sys.exit(1)
        }
      } catch {
        case e: Exception => {
          LOG.error("Failed to instantiate message or conatiner object:" + clsName + ". Reason:" + e.getCause + ". Message:" + e.getMessage())
          sys.exit(1)
        }
      }
    } else {
      LOG.error("Failed to instantiate message or conatiner object :" + clsName)
      sys.exit(1)
    }
  }

  private def GetDataStoreHandle(jarPaths: collection.immutable.Set[String], dataStoreInfo: String): DataStore = {
    try {
      logger.debug("Getting DB Connection for dataStoreInfo:%s".format(dataStoreInfo))
      return KeyValueManager.Get(jarPaths, dataStoreInfo)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:" + stackTrace)
        throw new Exception(e.getMessage())
      }
    }
  }

  private def deSerializeData(v: Value): MessageContainerBase = {
    v.serializerType.toLowerCase match {
      case "manual" => {
        return SerializeDeserialize.Deserialize(v.serializedInfo, this, clsLoaderInfo.loader, true, "")
      }
      case _ => {
        throw new Exception("Found un-handled Serializer Info: " + v.serializerType)
      }
    }
  }

  private def extractData(startTm: Date, endTm: Date, compressionString: String, sFileName: String, partKey: List[String], primaryKey: List[String]): Unit = {
    val hasValidPrimaryKey = (partKey != null && primaryKey != null && partKey.size > 0 && primaryKey.size > 0)

    var os: OutputStream = null
    val gson = new Gson();

    try {
      val compString = if (compressionString == null) null else compressionString.trim

      if (compString == null || compString.size == 0) {
        os = new FileOutputStream(sFileName);
      } else if (compString.compareToIgnoreCase("gz") == 0) {
        os = new GZIPOutputStream(new FileOutputStream(sFileName))
      } else {
        throw new Exception("%s:Not yet handled other than text & GZ files".format(GetCurDtTmStr))
      }

      val ln = "\n".getBytes("UTF8")

      val getObjFn = (k: Key, v: Value) => {
        val dta = deSerializeData(v)
        if (dta != null) {
          if (hasValidPrimaryKey) {
            // Search for primary key match
            if (primaryKey.sameElements(dta.PrimaryKeyData)) {
              LOG.debug("Primarykey found")
              os.write(gson.toJson(dta).getBytes("UTF8"));
              os.write(ln);
            }
          } else {
            // Write the whole list
            os.write(gson.toJson(dta).getBytes("UTF8"));
            os.write(ln);
          }
        }
      }

      if (partKey == null || partKey.size == 0) {
        _dataStore.get(_currentTypName, Array(TimeRange(startTm.getTime(), endTm.getTime())), getObjFn)
      } else {
        _dataStore.get(_currentTypName, Array(TimeRange(startTm.getTime(), endTm.getTime())), Array(partKey.toArray), getObjFn)
      }

      // Close file
      if (os != null)
        os.close
      os = null
    } catch {
      case e: Exception => {
        // Close file
        if (os != null)
          os.close
        os = null
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:" + stackTrace)
        throw new Exception("%s:Exception. Message:%s, Reason:%s".format(GetCurDtTmStr, e.getMessage, e.getCause))
      }
    }

  }

  def main(args: Array[String]): Unit = {
    if (args.length == 0) {
      PrintUsage()
      System.exit(1)
    }

    var exitCode = 0

    try {
      LOG.debug("%s:Parsing options".format(GetCurDtTmStr))
      val options = nextOption(Map(), args.toList)
      val cfgfile = options.getOrElse('config, "").toString.trim
      if (cfgfile.size == 0) {
        LOG.error("%s:Configuration file missing".format(GetCurDtTmStr))
        sys.exit(1)
      }

      val tmpfl = new File(cfgfile)
      if (tmpfl.exists == false) {
        LOG.error("%s:Configuration file %s is not valid".format(GetCurDtTmStr, cfgfile))
        sys.exit(1)
      }

      val (loadConfigs, failStr) = Utils.loadConfiguration(cfgfile.toString, true)
      if (failStr != null && failStr.size > 0) {
        LOG.error(failStr)
        sys.exit(1)
      }

      if (loadConfigs == null) {
        LOG.error("Failed to load configurations from configuration file")
        sys.exit(1)
      }

      val nodeId = loadConfigs.getProperty("NodeId".toLowerCase, "0").replace("\"", "").trim.toInt
      if (nodeId <= 0) {
        LOG.error("Not found valid nodeId. It should be greater than 0")
        sys.exit(1)
      }

      val typName = loadConfigs.getProperty("TypeName".toLowerCase, "").replace("\"", "").trim.toLowerCase

      MetadataAPIImpl.InitMdMgrFromBootStrap(cfgfile, false)

      val nodeInfo = mdMgr.Nodes.getOrElse(nodeId.toString, null)
      if (nodeInfo == null) {
        LOG.error("Node %d not found in metadata".format(nodeId))
        sys.exit(1)
      }

      jarPaths = if (nodeInfo.JarPaths == null) Array[String]().toSet else nodeInfo.JarPaths.map(str => str.replace("\"", "").trim).filter(str => str.size > 0).toSet
      if (jarPaths.size == 0) {
        LOG.error("Not found valid JarPaths.")
        sys.exit(1)
      }

      val cluster = mdMgr.ClusterCfgs.getOrElse(nodeInfo.ClusterId, null)
      if (cluster == null) {
        LOG.error("Cluster not found for Node %d  & ClusterId : %s".format(nodeId, nodeInfo.ClusterId))
        sys.exit(1)
      }

      val dataStore = cluster.cfgMap.getOrElse("DataStore", null)
      if (dataStore == null) {
        LOG.error("DataStore not found for Node %d  & ClusterId : %s".format(nodeId, nodeInfo.ClusterId))
        sys.exit(1)
      }

      resolveCurretType(typName)

      _dataStore = GetDataStoreHandle(jarPaths, dataStore)

      val stTmStr = loadConfigs.getProperty("StartTime".toLowerCase, "").replace("\"", "").trim // Expecting in the format of "yyyy-MM-dd HH:mm:ss"
      val edTmStr = loadConfigs.getProperty("EndTime".toLowerCase, "").replace("\"", "").trim // Expecting in the format of "yyyy-MM-dd HH:mm:ss"
      val partKey = loadConfigs.getProperty("PartitionKey".toLowerCase, "").replace("\"", "").trim.split(",").map(_.trim.toLowerCase).filter(_.size > 0).toList
      val primaryKey = loadConfigs.getProperty("PrimaryKey".toLowerCase, "").replace("\"", "").trim.split(",").map(_.trim).filter(_.size > 0).toList

      val flName = loadConfigs.getProperty("OutputFile".toLowerCase, "").replace("\"", "").trim
      val compression = loadConfigs.getProperty("Compression".toLowerCase, "").replace("\"", "").trim.toLowerCase

      val dtFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      val startTm =
        if (stTmStr.size == 0) {
          new Date(0)
        } else {
          dtFormat.parse(stTmStr)
        }

      val endTm =
        if (edTmStr.size == 0) {
          new Date(Long.MaxValue)
        } else {
          dtFormat.parse(edTmStr)
        }

      extractData(startTm, endTm, compression, flName, partKey, primaryKey)

      exitCode = 0
    } catch {
      case e: Exception => {
        LOG.error("%s:Failed to extract data with exception. Reason:%s, Message:%s".format(GetCurDtTmStr, e.getCause, e.getMessage))
        exitCode = 1
      }
    } finally {
      if (_dataStore != null)
        _dataStore.Shutdown
      _dataStore = null
    }

    sys.exit(exitCode)
  }
}

