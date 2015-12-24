
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

package com.ligadata.KamanjaManager

import com.ligadata.kamanja.metadata.{ BaseElem, MappedMsgTypeDef, BaseAttributeDef, StructTypeDef, EntityType, AttributeDef, ArrayBufTypeDef, MessageDef, ContainerDef, ModelDef }
import com.ligadata.kamanja.metadata._
import com.ligadata.kamanja.metadata.MdMgr._

import com.ligadata.kamanja.metadataload.MetadataLoad
import scala.collection.mutable.TreeSet
import scala.util.control.Breaks._
import com.ligadata.KamanjaBase.{ BaseMsg, MessageContainerBase, MessageContainerObjBase, BaseMsgObj, BaseContainerObj, BaseContainer, ModelInstanceFactory, TransformMessage, EnvContext, MdBaseResolveInfo, FactoryOfModelInstanceFactory, NodeContext, TransactionContext }
import scala.collection.mutable.HashMap
import org.apache.logging.log4j._
import scala.collection.mutable.ArrayBuffer
import com.ligadata.Serialize._
import com.ligadata.ZooKeeper._
import com.ligadata.MetadataAPI.MetadataAPIImpl
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.ligadata.Utils.{ Utils, KamanjaClassLoader, KamanjaLoaderInfo }
import com.ligadata.Exceptions.StackTrace
import com.ligadata.KamanjaBase.{ EnvContext, ContainerNameAndDatastoreInfo }
import scala.actors.threadpool.{ ExecutorService, Executors }
import com.ligadata.KamanjaBase.ThreadLocalStorage

class MdlInfo(val mdl: ModelInstanceFactory, val jarPath: String, val dependencyJarNames: Array[String]) {
}

class TransformMsgFldsMap(var keyflds: Array[Int], var outputFlds: Array[Int]) {
}

// msgobj is null for Containers
class MsgContainerObjAndTransformInfo(var tranformMsgFlds: TransformMsgFldsMap, var contmsgobj: MessageContainerObjBase) {
  var parents = new ArrayBuffer[(String, String)] // Immediate parent comes at the end, grand parent last but one, ... Messages/Containers. the format is Message/Container Type name and the variable in that.
  var childs = new ArrayBuffer[(String, String)] // Child Messages/Containers (Name & type). We fill this when we create message and populate parent later from this
}

// This is shared by multiple threads to read (because we are not locking). We create this only once at this moment while starting the manager
class KamanjaMetadata {
  val LOG = LogManager.getLogger(getClass);

  // LOG.setLevel(Level.TRACE)

  // Metadata manager
  val messageObjects = new HashMap[String, MsgContainerObjAndTransformInfo]
  val containerObjects = new HashMap[String, MsgContainerObjAndTransformInfo]
  val modelObjsMap = new HashMap[String, MdlInfo]

  def LoadMdMgrElems(tmpMsgDefs: Option[scala.collection.immutable.Set[MessageDef]], tmpContainerDefs: Option[scala.collection.immutable.Set[ContainerDef]],
                     tmpModelDefs: Option[scala.collection.immutable.Set[ModelDef]]): Unit = {
    PrepareMessages(tmpMsgDefs)
    PrepareContainers(tmpContainerDefs)
    PrepareModelsFactories(tmpModelDefs)

    LOG.info("Loaded Metadata Messages:" + messageObjects.map(msg => msg._1).mkString(","))
    LOG.info("Loaded Metadata Containers:" + containerObjects.map(container => container._1).mkString(","))
    LOG.info("Loaded Metadata Models:" + modelObjsMap.map(mdl => mdl._1).mkString(","))
  }

  private[this] def CheckAndPrepMessage(clsName: String, msg: MessageDef): Boolean = {
    var isMsg = true
    var curClass: Class[_] = null

    try {
      // If required we need to enable this test
      // Convert class name into a class
      var curClz = Class.forName(clsName, true, KamanjaConfiguration.metadataLoader.loader)
      curClass = curClz

      isMsg = false

      while (curClz != null && isMsg == false) {
        isMsg = Utils.isDerivedFrom(curClz, "com.ligadata.KamanjaBase.BaseMsgObj")
        if (isMsg == false)
          curClz = curClz.getSuperclass()
      }
    } catch {
      case e: Exception => {
        LOG.debug("Failed to get message classname :" + clsName)
        return false
      }
    }

    if (isMsg) {
      try {
        var objinst: Any = null
        try {
          // Trying Singleton Object
          val module = KamanjaConfiguration.metadataLoader.mirror.staticModule(clsName)
          val obj = KamanjaConfiguration.metadataLoader.mirror.reflectModule(module)
          objinst = obj.instance
        } catch {
          case e: Exception => {
            // Trying Regular Object instantiation
            val stackTrace = StackTrace.ThrowableTraceString(e)
            LOG.debug("StackTrace:" + stackTrace)
            objinst = curClass.newInstance
          }
        }
        if (objinst.isInstanceOf[BaseMsgObj]) {
          val messageobj = objinst.asInstanceOf[BaseMsgObj]
          val msgName = msg.FullName.toLowerCase
          var tranformMsgFlds: TransformMsgFldsMap = null
          if (messageobj.NeedToTransformData) {
            val txfnMsg: TransformMessage = messageobj.TransformDataAttributes

            val inputFieldsMap = txfnMsg.inputFields.map(f => f.trim.toLowerCase).view.zipWithIndex.toMap
            val outputFldIdxs = txfnMsg.outputFields.map(f => {
              val fld = inputFieldsMap.getOrElse(f.trim.toLowerCase, -1)
              if (fld < 0) {
                throw new Exception("Output Field \"" + f + "\" not found in input list of fields")
              }
              fld
            })

            val keyfldsIdxs = txfnMsg.outputKeys.map(f => {
              val fld = inputFieldsMap.getOrElse(f.trim.toLowerCase, -1)
              if (fld < 0)
                throw new Exception("Key Field \"" + f + "\" not found in input list of fields")
              fld
            })
            tranformMsgFlds = new TransformMsgFldsMap(keyfldsIdxs, outputFldIdxs)
          }
          val mgsObj = new MsgContainerObjAndTransformInfo(tranformMsgFlds, messageobj)
          GetChildsFromEntity(msg.containerType, mgsObj.childs)
          messageObjects(msgName) = mgsObj

          LOG.info("Created Message:" + msgName)
          return true
        } else {
          LOG.debug("Failed to instantiate message object :" + clsName)
          return false
        }
      } catch {
        case e: Exception => {
          LOG.debug("Failed to instantiate message object:" + clsName + ". Reason:" + e.getCause + ". Message:" + e.getMessage())
          return false
        }
      }
    }
    return false
  }

  def PrepareMessage(msg: MessageDef, loadJars: Boolean): Unit = {
    if (loadJars)
      KamanjaMetadata.LoadJarIfNeeded(msg)
    // else Assuming we are already loaded all the required jars

    var clsName = msg.PhysicalName.trim
    var orgClsName = clsName

    var foundFlg = CheckAndPrepMessage(clsName, msg)

    if (foundFlg == false) {
      if (clsName.size > 0 && clsName.charAt(clsName.size - 1) != '$') { // if no $ at the end we are taking $
        clsName = clsName + "$"
        foundFlg = CheckAndPrepMessage(clsName, msg)
      }
    }
    if (foundFlg == false) {
      LOG.error("Failed to instantiate message object:%s, class name:%s".format(msg.FullName, orgClsName))
    }
  }

  private[this] def CheckAndPrepContainer(clsName: String, container: ContainerDef): Boolean = {
    var isContainer = true
    var curClass: Class[_] = null

    try {
      // If required we need to enable this test
      // Convert class name into a class
      var curClz = Class.forName(clsName, true, KamanjaConfiguration.metadataLoader.loader)
      curClass = curClz

      isContainer = false

      while (curClz != null && isContainer == false) {
        isContainer = Utils.isDerivedFrom(curClz, "com.ligadata.KamanjaBase.BaseContainerObj")
        if (isContainer == false)
          curClz = curClz.getSuperclass()
      }
    } catch {
      case e: Exception => {
        LOG.debug("Failed to get container classname: " + clsName)
        return false
      }
    }

    if (isContainer) {
      try {
        var objinst: Any = null
        try {
          // Trying Singleton Object
          val module = KamanjaConfiguration.metadataLoader.mirror.staticModule(clsName)
          val obj = KamanjaConfiguration.metadataLoader.mirror.reflectModule(module)
          objinst = obj.instance
        } catch {
          case e: Exception => {
            val stackTrace = StackTrace.ThrowableTraceString(e)
            LOG.error("Stacktrace:" + stackTrace)
            // Trying Regular Object instantiation
            objinst = curClass.newInstance
          }
        }

        if (objinst.isInstanceOf[BaseContainerObj]) {
          val containerobj = objinst.asInstanceOf[BaseContainerObj]
          val contName = container.FullName.toLowerCase
          val contObj = new MsgContainerObjAndTransformInfo(null, containerobj)
          GetChildsFromEntity(container.containerType, contObj.childs)
          containerObjects(contName) = contObj

          LOG.info("Created Container:" + contName)
          return true
        } else {
          LOG.debug("Failed to instantiate container object :" + clsName)
          return false
        }
      } catch {
        case e: Exception => {
          LOG.debug("Failed to instantiate containerObjects object:" + clsName + ". Reason:" + e.getCause + ". Message:" + e.getMessage())
          return false
        }
      }
    }
    return false
  }

  def PrepareContainer(container: ContainerDef, loadJars: Boolean, ignoreClassLoad: Boolean): Unit = {
    if (loadJars)
      KamanjaMetadata.LoadJarIfNeeded(container)
    // else Assuming we are already loaded all the required jars

    if (ignoreClassLoad) {
      val contName = container.FullName.toLowerCase
      val containerObj = new MsgContainerObjAndTransformInfo(null, null)
      GetChildsFromEntity(container.containerType, containerObj.childs)
      containerObjects(contName) = containerObj
      LOG.debug("Added Base Container:" + contName)
      return
    }

    var clsName = container.PhysicalName.trim
    var orgClsName = clsName

    var foundFlg = CheckAndPrepContainer(clsName, container)
    if (foundFlg == false) {
      if (clsName.size > 0 && clsName.charAt(clsName.size - 1) != '$') { // if no $ at the end we are taking $
        clsName = clsName + "$"
        foundFlg = CheckAndPrepContainer(clsName, container)
      }
    }
    if (foundFlg == false) {
      LOG.error("Failed to instantiate container object:%s, class name:%s".format(container.FullName, orgClsName))
    }
  }

  private def GetFactoryOfMdlInstanceFactory(fqName: String): FactoryOfModelInstanceFactory = {
    val factObjs = KamanjaMetadata.AllFactoryOfMdlInstFactoriesObjects
    factObjs.getOrElse(fqName.toLowerCase(), null)
  }

  def PrepareModelFactory(mdl: ModelDef, loadJars: Boolean, txnCtxt: TransactionContext): Unit = {
    if (mdl != null) {
      if (loadJars)
        KamanjaMetadata.LoadJarIfNeeded(mdl)
      // else Assuming we are already loaded all the required jars
      val factoryOfMdlInstFactoryFqName = "com.ligadata.FactoryOfModelInstanceFactory.JarFactoryOfModelInstanceFactory" //BUGBUG:: We need to get the name from Model Def.
      val factoryOfMdlInstFactory: FactoryOfModelInstanceFactory = GetFactoryOfMdlInstanceFactory(factoryOfMdlInstFactoryFqName)
      if (factoryOfMdlInstFactory == null) {
        LOG.error("FactoryOfModelInstanceFactory %s not found in metadata. Unable to create ModelInstanceFactory for %s".format(factoryOfMdlInstFactoryFqName, mdl.FullName))
      } else {
        try {
          val factory: ModelInstanceFactory = factoryOfMdlInstFactory.getModelInstanceFactory(mdl, KamanjaMetadata.gNodeContext, KamanjaConfiguration.metadataLoader, KamanjaConfiguration.jarPaths)
          if (factory != null) {
            if (txnCtxt != null) // We are expecting txnCtxt is null only for first time initialization
              factory.init(txnCtxt)
            val mdlName = (mdl.NameSpace.trim + "." + mdl.Name.trim).toLowerCase
            modelObjsMap(mdlName) = new MdlInfo(factory, mdl.jarName, mdl.dependencyJarNames)
          } else {
            LOG.debug("Failed to get ModelInstanceFactory for " + mdl.FullName)
          }
        } catch {
          case e: Exception => {
            val stackTrace = StackTrace.ThrowableTraceString(e)
            LOG.debug("Failed to get/initialize ModelInstanceFactory for %s. Reason:%s, Cause:%s\nStackTrace:%s".format(mdl.FullName, e.getMessage, e.getCause, stackTrace))
          }
        }
      }
    }
  }

  private def GetChildsFromEntity(entity: EntityType, childs: ArrayBuffer[(String, String)]): Unit = {
    // mgsObj.childs +=
    if (entity.isInstanceOf[MappedMsgTypeDef]) {
      var attrMap = entity.asInstanceOf[MappedMsgTypeDef].attrMap
      //BUGBUG:: Checking for only one level at this moment
      if (attrMap != null) {
        childs ++= attrMap.filter(a => (a._2.isInstanceOf[AttributeDef] && (a._2.asInstanceOf[AttributeDef].aType.isInstanceOf[MappedMsgTypeDef] || a._2.asInstanceOf[AttributeDef].aType.isInstanceOf[StructTypeDef]))).map(a => (a._2.Name, a._2.asInstanceOf[AttributeDef].aType.FullName))
        // If the attribute is an arraybuffer (not yet handling others)
        childs ++= attrMap.filter(a => (a._2.isInstanceOf[AttributeDef] && a._2.asInstanceOf[AttributeDef].aType.isInstanceOf[ArrayBufTypeDef] && (a._2.asInstanceOf[AttributeDef].aType.asInstanceOf[ArrayBufTypeDef].elemDef.isInstanceOf[MappedMsgTypeDef] || a._2.asInstanceOf[AttributeDef].aType.asInstanceOf[ArrayBufTypeDef].elemDef.isInstanceOf[StructTypeDef]))).map(a => (a._2.Name, a._2.asInstanceOf[AttributeDef].aType.asInstanceOf[ArrayBufTypeDef].elemDef.FullName))
      }
    } else if (entity.isInstanceOf[StructTypeDef]) {
      var memberDefs = entity.asInstanceOf[StructTypeDef].memberDefs
      //BUGBUG:: Checking for only one level at this moment
      if (memberDefs != null) {
        childs ++= memberDefs.filter(a => (a.isInstanceOf[AttributeDef] && (a.asInstanceOf[AttributeDef].aType.isInstanceOf[MappedMsgTypeDef] || a.asInstanceOf[AttributeDef].aType.isInstanceOf[StructTypeDef]))).map(a => (a.Name, a.asInstanceOf[AttributeDef].aType.FullName))
        // If the attribute is an arraybuffer (not yet handling others)
        childs ++= memberDefs.filter(a => (a.isInstanceOf[AttributeDef] && a.asInstanceOf[AttributeDef].aType.isInstanceOf[ArrayBufTypeDef] && (a.asInstanceOf[AttributeDef].aType.asInstanceOf[ArrayBufTypeDef].elemDef.isInstanceOf[MappedMsgTypeDef] || a.asInstanceOf[AttributeDef].aType.asInstanceOf[ArrayBufTypeDef].elemDef.isInstanceOf[StructTypeDef]))).map(a => (a.Name, a.asInstanceOf[AttributeDef].aType.asInstanceOf[ArrayBufTypeDef].elemDef.FullName))
      }
    } else {
      // Nothing to do at this moment
    }
  }

  private def PrepareMessages(tmpMsgDefs: Option[scala.collection.immutable.Set[MessageDef]]): Unit = {
    if (tmpMsgDefs == None) // Not found any messages
      return

    val msgDefs = tmpMsgDefs.get

    // Load all jars first
    msgDefs.foreach(msg => {
      // LOG.debug("Loading msg:" + msg.FullName)
      KamanjaMetadata.LoadJarIfNeeded(msg)
    })

    msgDefs.foreach(msg => {
      PrepareMessage(msg, false) // Already Loaded required dependency jars before calling this
    })
  }

  private def PrepareContainers(tmpContainerDefs: Option[scala.collection.immutable.Set[ContainerDef]]): Unit = {
    if (tmpContainerDefs == None) // Not found any containers
      return

    val containerDefs = tmpContainerDefs.get

    // Load all jars first
    containerDefs.foreach(container => {
      KamanjaMetadata.LoadJarIfNeeded(container)
    })

    val baseContainersPhyName = scala.collection.mutable.Set[String]()
    val baseContainerInfo = MetadataLoad.BaseContainersInfo
    baseContainerInfo.foreach(bc => {
      baseContainersPhyName += bc._3
    })

    containerDefs.foreach(container => {
      PrepareContainer(container, false, baseContainersPhyName.contains(container.PhysicalName.trim)) // Already Loaded required dependency jars before calling this
    })

  }

  private def PrepareModelsFactories(tmpModelDefs: Option[scala.collection.immutable.Set[ModelDef]]): Unit = {
    if (tmpModelDefs == None) // Not found any models
      return

    val modelDefs = tmpModelDefs.get

    // Load all jars first
    modelDefs.foreach(mdl => {
      KamanjaMetadata.LoadJarIfNeeded(mdl)
    })

    modelDefs.foreach(mdl => {
      PrepareModelFactory(mdl, false, null) // Already Loaded required dependency jars before calling this
    })
  }
}

object KamanjaMetadata extends MdBaseResolveInfo {
  var envCtxt: EnvContext = null // Engine will set it once EnvContext is initialized
  var gNodeContext: NodeContext = null
  private[this] val LOG = LogManager.getLogger(getClass);
  private[this] val mdMgr = GetMdMgr
  private[this] var messageContainerObjects = new HashMap[String, MsgContainerObjAndTransformInfo]
  private[this] var modelObjs = new HashMap[String, MdlInfo]
  private[this] var modelExecOrderedObjects = Array[(String, MdlInfo)]()
  private[this] var factoryOfMdlInstFactoriesObjects = scala.collection.immutable.Map[String, FactoryOfModelInstanceFactory]()
  private[this] var zkListener: ZooKeeperListener = _
  private[this] var mdlsChangedCntr: Long = 0
  private[this] var initializedFactOfMdlInstFactObjs = false
  private[this] val reent_lock = new ReentrantReadWriteLock(true);
  private[this] val updMetadataExecutor = Executors.newFixedThreadPool(1)

  def AllFactoryOfMdlInstFactoriesObjects = factoryOfMdlInstFactoriesObjects.toMap

  def GetAllJarsFromElem(elem: BaseElem): Set[String] = {
    var allJars: Array[String] = null

    val jarname = if (elem.JarName == null) "" else elem.JarName.trim

    if (elem.DependencyJarNames != null && elem.DependencyJarNames.size > 0 && jarname.size > 0) {
      allJars = elem.DependencyJarNames :+ jarname
    } else if (elem.DependencyJarNames != null && elem.DependencyJarNames.size > 0) {
      allJars = elem.DependencyJarNames
    } else if (jarname.size > 0) {
      allJars = Array(jarname)
    } else {
      return Set[String]()
    }

    return allJars.map(j => Utils.GetValidJarFile(KamanjaConfiguration.jarPaths, j)).toSet
  }

  def LoadJarIfNeeded(elem: BaseElem): Boolean = {
    val allJars = GetAllJarsFromElem(elem)
    if (allJars.size > 0) {
      return Utils.LoadJars(allJars.toArray, KamanjaConfiguration.metadataLoader.loadedJars, KamanjaConfiguration.metadataLoader.loader)
    } else {
      return true
    }
  }

  def ValidateAllRequiredJars(tmpMsgDefs: Option[scala.collection.immutable.Set[MessageDef]], tmpContainerDefs: Option[scala.collection.immutable.Set[ContainerDef]],
                              tmpModelDefs: Option[scala.collection.immutable.Set[ModelDef]], tmpModelFactiresDefs: Option[scala.collection.immutable.Set[FactoryOfModelInstanceFactoryDef]]): Boolean = {
    val allJarsToBeValidated = scala.collection.mutable.Set[String]();

    if (tmpMsgDefs != None) { // Not found any messages
      tmpMsgDefs.get.foreach(elem => {
        allJarsToBeValidated ++= KamanjaMetadata.GetAllJarsFromElem(elem)
      })
    }

    if (tmpContainerDefs != None) { // Not found any messages
      tmpContainerDefs.get.foreach(elem => {
        allJarsToBeValidated ++= KamanjaMetadata.GetAllJarsFromElem(elem)
      })
    }

    if (tmpModelDefs != None) { // Not found any messages
      tmpModelDefs.get.foreach(elem => {
        allJarsToBeValidated ++= KamanjaMetadata.GetAllJarsFromElem(elem)
      })
    }

    if (tmpModelFactiresDefs != None) { // Not found any messages
      tmpModelFactiresDefs.get.foreach(elem => {
        allJarsToBeValidated ++= KamanjaMetadata.GetAllJarsFromElem(elem)
      })
    }

    val nonExistsJars = Utils.CheckForNonExistanceJars(allJarsToBeValidated.toSet)
    if (nonExistsJars.size > 0) {
      LOG.error("Not found jars in Messages/Containers/Models Jars List : {" + nonExistsJars.mkString(", ") + "}")
      return false
    }

    true
  }

  private[this] def ResolveFactoryOfModelInstanceFactoryDef(clsName: String, fDef: FactoryOfModelInstanceFactoryDef): FactoryOfModelInstanceFactory = {
    var isValid = true
    var curClass: Class[_] = null

    try {
      // If required we need to enable this test
      // Convert class name into a class
      var curClz = Class.forName(clsName, true, KamanjaConfiguration.metadataLoader.loader)
      curClass = curClz

      isValid = false

      while (curClz != null && isValid == false) {
        isValid = Utils.isDerivedFrom(curClz, "com.ligadata.KamanjaBase.FactoryOfModelInstanceFactory")
        if (isValid == false)
          curClz = curClz.getSuperclass()
      }
    } catch {
      case e: Exception => {
        LOG.debug("Failed to get model classname :" + clsName)
        return null
      }
    }

    if (isValid) {
      try {
        var objinst: Any = null
        try {
          // Trying Singleton Object
          val module = KamanjaConfiguration.metadataLoader.mirror.staticModule(clsName)
          val obj = KamanjaConfiguration.metadataLoader.mirror.reflectModule(module)
          // curClz.newInstance
          objinst = obj.instance
        } catch {
          case e: Exception => {
            val stackTrace = StackTrace.ThrowableTraceString(e)
            LOG.debug("StackTrace:" + stackTrace)
            // Trying Regular Object instantiation
            objinst = curClass.newInstance
          }
        }

        if (objinst.isInstanceOf[FactoryOfModelInstanceFactory]) {
          val factoryObj = objinst.asInstanceOf[FactoryOfModelInstanceFactory]
          val fName = (fDef.NameSpace.trim + "." + fDef.Name.trim).toLowerCase
          LOG.info("Created FactoryOfModelInstanceFactory:" + fName)
          return factoryObj
        } else {
          LOG.debug("Failed to instantiate FactoryOfModelInstanceFactory object :" + clsName + ". ObjType0:" + objinst.getClass.getSimpleName + ". ObjType1:" + objinst.getClass.getCanonicalName)
          return null
        }
      } catch {
        case e: Exception =>
          LOG.debug("Failed to instantiate FactoryOfModelInstanceFactory object:" + clsName + ". Reason:" + e.getCause + ". Message:" + e.getMessage)
          return null
      }
    }
    return null
  }

  def ResolveAllFactoryOfMdlInstFactoriesObjects(): Unit = {
    val fDefsOptions = mdMgr.FactoryOfMdlInstFactories(true, true)
    val tmpFactoryOfMdlInstFactObjects = scala.collection.mutable.Map[String, FactoryOfModelInstanceFactory]()

    if (fDefsOptions != None) {
      val fDefs = fDefsOptions.get

      LOG.debug("Found %d FactoryOfModelInstanceFactory objects".format(fDefs.size))

      fDefs.foreach(f => {
        LoadJarIfNeeded(f)
        // else Assuming we are already loaded all the required jars

        var clsName = f.PhysicalName.trim
        var orgClsName = clsName

        LOG.debug("FactoryOfModelInstanceFactory. FullName:%s, ClassName:%s".format(f.FullName, clsName))
        var fDefObj = ResolveFactoryOfModelInstanceFactoryDef(clsName, f)
        if (fDefObj == null) {
          if (clsName.size > 0 && clsName.charAt(clsName.size - 1) != '$') { // if no $ at the end we are taking $
            clsName = clsName + "$"
            fDefObj = ResolveFactoryOfModelInstanceFactoryDef(clsName, f)
          }
        }

        if (fDefObj != null) {
          tmpFactoryOfMdlInstFactObjects(f.FullName.toLowerCase()) = fDefObj
        } else {
          LOG.error("Failed to resolve FactoryOfModelInstanceFactory object:%s, Classname:%s".format(f.FullName, orgClsName))
        }
      })
    } else {
      logger.debug("Not Found any FactoryOfModelInstanceFactory objects")
    }

    var exp: Exception = null

    reent_lock.writeLock().lock();
    try {
      factoryOfMdlInstFactoriesObjects = tmpFactoryOfMdlInstFactObjects.toMap
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        exp = e
      }
    } finally {
      reent_lock.writeLock().unlock();
    }
    if (exp != null)
      throw exp
  }

  private def UpdateKamanjaMdObjects(msgObjects: HashMap[String, MsgContainerObjAndTransformInfo], contObjects: HashMap[String, MsgContainerObjAndTransformInfo],
                                     mdlObjects: HashMap[String, MdlInfo], removedModels: ArrayBuffer[(String, String, Long)], removedMessages: ArrayBuffer[(String, String, Long)],
                                     removedContainers: ArrayBuffer[(String, String, Long)]): Unit = {

    var exp: Exception = null

    reent_lock.writeLock().lock();
    try {
      localUpdateKamanjaMdObjects(msgObjects, contObjects, mdlObjects, removedModels, removedMessages, removedContainers)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        exp = e
      }
    } finally {
      reent_lock.writeLock().unlock();
    }
    if (exp != null)
      throw exp
  }

  private def localUpdateKamanjaMdObjects(msgObjects: HashMap[String, MsgContainerObjAndTransformInfo], contObjects: HashMap[String, MsgContainerObjAndTransformInfo],
                                          mdlObjects: HashMap[String, MdlInfo], removedModels: ArrayBuffer[(String, String, Long)], removedMessages: ArrayBuffer[(String, String, Long)],
                                          removedContainers: ArrayBuffer[(String, String, Long)]): Unit = {
    //BUGBUG:: Assuming there is no issues if we remove the objects first and then add the new objects. We are not adding the object in the same order as it added in the transaction. 

    var mdlsChanged = false

    // First removing the objects
    // Removing Models
    if (removedModels != null && removedModels.size > 0) {
      val prevCnt = modelObjs.size
      removedModels.foreach(mdl => {
        val elemName = (mdl._1.trim + "." + mdl._2.trim).toLowerCase
        modelObjs -= elemName
      })
      mdlsChanged = (prevCnt != modelObjs.size)
    }

    // Removing Messages
    if (removedMessages != null && removedMessages.size > 0) {
      removedMessages.foreach(msg => {
        val elemName = (msg._1.trim + "." + msg._2.trim).toLowerCase
        messageContainerObjects -= elemName //BUGBUG:: It has both Messages & Containers. Are we sure it only removes Messages here?
      })
    }

    // Removing Containers
    if (removedContainers != null && removedContainers.size > 0) {
      removedContainers.foreach(cnt => {
        val elemName = (cnt._1.trim + "." + cnt._2.trim).toLowerCase
        messageContainerObjects -= elemName //BUGBUG:: It has both Messages & Containers. Are we sure it only removes Containers here?
      })
    }

    // Adding new objects now
    // Adding container
    if (contObjects != null && contObjects.size > 0) {
      messageContainerObjects ++= contObjects
      if (envCtxt != null) {
        val containerNames = contObjects.map(container => container._1.toLowerCase).toList.sorted.toArray // Sort topics by names
        val containerInfos = containerNames.map(c => { ContainerNameAndDatastoreInfo(c, null) })
        envCtxt.RegisterMessageOrContainers(containerInfos) // Containers
        envCtxt.CacheContainers(KamanjaConfiguration.clusterId) // Load data for Caching
      }
    }

    // Adding Messages
    if (msgObjects != null && msgObjects.size > 0) {
      messageContainerObjects ++= msgObjects
      if (envCtxt != null) {
        val topMessageNames = msgObjects.filter(msg => msg._2.parents.size == 0).map(msg => msg._1.toLowerCase).toList.sorted.toArray // Sort topics by names
        val messagesInfos = topMessageNames.map(c => { ContainerNameAndDatastoreInfo(c, null) })
        envCtxt.RegisterMessageOrContainers(messagesInfos) // Messages
        envCtxt.CacheContainers(KamanjaConfiguration.clusterId) // Load data for Caching
      }
    }

    // Adding Models
    if (mdlObjects != null && mdlObjects.size > 0) {
      mdlsChanged = true // already checked for mdlObjects.size > 0
      modelObjs ++= mdlObjects
    }

    // If messages/Containers removed or added, jsut change the parents chain
    if ((removedMessages != null && removedMessages.size > 0) ||
      (removedContainers != null && removedContainers.size > 0) ||
      (contObjects != null && contObjects.size > 0) ||
      (msgObjects != null && msgObjects.size > 0)) {

      // Prepare Parents for each message now
      val childToParentMap = scala.collection.mutable.Map[String, (String, String)]() // ChildType, (ParentType, ChildAttrName) 

      // Clear previous parents
      messageContainerObjects.foreach(c => {
        c._2.parents.clear
      })

      // 1. First prepare one level of parents
      messageContainerObjects.foreach(m => {
        m._2.childs.foreach(c => {
          // Checking whether we already have in childToParentMap or not before we replace. So that way we can check same child under multiple parents.
          val childMsgNm = c._2.toLowerCase
          val fnd = childToParentMap.getOrElse(childMsgNm, null)
          if (fnd != null) {
            LOG.error(s"$childMsgNm is used as child under $c and $fnd._1. First detected $fnd._1, so using as child of $fnd._1 as it is.")
          } else {
            childToParentMap(childMsgNm) = (m._1.toLowerCase, c._1)
          }
        })
      })

      // 2. Now prepare Full Parent Hierarchy
      messageContainerObjects.foreach(m => {
        var curParent = childToParentMap.getOrElse(m._1.toLowerCase, null)
        while (curParent != null) {
          m._2.parents += curParent
          curParent = childToParentMap.getOrElse(curParent._1.toLowerCase, null)
        }
      })

      // 3. Order Parent Hierarchy properly
      messageContainerObjects.foreach(m => {
        m._2.parents.reverse
      })
    }

    // Order Models (if property is given) if we added any
    if (mdlObjects != null && mdlObjects.size > 0) {
      // Order Models Execution
      val tmpExecOrderStr = mdMgr.GetUserProperty(KamanjaConfiguration.clusterId, "modelsexecutionorder")
      val ExecOrderStr = if (tmpExecOrderStr != null) tmpExecOrderStr.trim.toLowerCase.split(",").map(s => s.trim).filter(s => s.size > 0) else Array[String]()

      if (ExecOrderStr.size > 0 && modelObjs != null) {
        var mdlsOrder = ArrayBuffer[(String, MdlInfo)]()
        ExecOrderStr.foreach(mdlNm => {
          val m = modelObjs.getOrElse(mdlNm, null)
          if (m != null)
            mdlsOrder += ((mdlNm, m))
        })

        var orderedMdlsSet = ExecOrderStr.toSet
        modelObjs.foreach(kv => {
          val mdlNm = kv._1.toLowerCase()
          if (orderedMdlsSet.contains(mdlNm) == false)
            mdlsOrder += ((mdlNm, kv._2))
        })

        LOG.warn("Models Order changed from %s to %s".format(modelObjs.map(kv => kv._1).mkString(","), mdlsOrder.map(kv => kv._1).mkString(",")))
        modelExecOrderedObjects = mdlsOrder.toArray
      } else {
        modelExecOrderedObjects = if (modelObjs != null) modelObjs.toArray else Array[(String, MdlInfo)]()
      }

      mdlsChanged = true
    } else {
      modelExecOrderedObjects = Array[(String, MdlInfo)]()
    }

    LOG.debug("mdlsChanged:" + mdlsChanged.toString)

    if (mdlsChanged)
      mdlsChangedCntr += 1
  }

  def InitBootstrap: Unit = {
    MetadataAPIImpl.InitMdMgrFromBootStrap(KamanjaConfiguration.configFile, false)
  }

  def InitMdMgr(zkConnectString: String, znodePath: String, zkSessionTimeoutMs: Int, zkConnectionTimeoutMs: Int): Unit = {
    val tmpMsgDefs = mdMgr.Messages(true, true)
    val tmpContainerDefs = mdMgr.Containers(true, true)
    val tmpModelDefs = mdMgr.Models(true, true)

    val obj = new KamanjaMetadata

    try {
      if (initializedFactOfMdlInstFactObjs == false) {
        KamanjaMetadata.ResolveAllFactoryOfMdlInstFactoriesObjects()
        initializedFactOfMdlInstFactObjs = true
      }
      obj.LoadMdMgrElems(tmpMsgDefs, tmpContainerDefs, tmpModelDefs)
      // Lock the global object here and update the global objects
      UpdateKamanjaMdObjects(obj.messageObjects, obj.containerObjects, obj.modelObjsMap, null, null, null)
    } catch {
      case e: Exception => {
        LOG.error("Failed to load messages, containers & models from metadata manager. Reason:%s Message:%s".format(e.getCause, e.getMessage))
        throw e
      }
    }

    if (zkConnectString != null && zkConnectString.isEmpty() == false && znodePath != null && znodePath.isEmpty() == false) {
      try {
        CreateClient.CreateNodeIfNotExists(zkConnectString, znodePath)
        zkListener = new ZooKeeperListener
        zkListener.CreateListener(zkConnectString, znodePath, UpdateMetadata, zkSessionTimeoutMs, zkConnectionTimeoutMs)
      } catch {
        case e: Exception => {

          LOG.error("Failed to initialize ZooKeeper Connection. Reason:%s Message:%s".format(e.getCause, e.getMessage))
          throw e
        }
      }
    }
  }

  def UpdateMetadata(receivedJsonStr: String): Unit = {

    LOG.info("Process ZooKeeper notification " + receivedJsonStr)

    if (receivedJsonStr == null || receivedJsonStr.size == 0) {
      // nothing to do
      return
    }

    val zkTransaction = JsonSerializer.parseZkTransaction(receivedJsonStr, "JSON")

    if (zkTransaction == null || zkTransaction.Notifications.size == 0) {
      // nothing to do
      return
    }

    if (mdMgr == null) {
      LOG.error("Metadata Manager should not be NULL while updaing metadta in Kamanja manager.")
      return
    }
    
    if (zkTransaction.transactionId.getOrElse("0").toLong <= MetadataAPIImpl.getCurrentTranLevel) return

    updMetadataExecutor.execute(new MetadataUpdate(zkTransaction))
  }

  // Assuming mdMgr is locked at this moment for not to update while doing this operation
  class MetadataUpdate(val zkTransaction: ZooKeeperTransaction) extends Runnable {
    def run() {
      var txnCtxt: TransactionContext = null
      var txnId = KamanjaConfiguration.nodeId.toString.hashCode()
      if (txnId > 0)
        txnId = -1 * txnId
      // Finally we are taking -ve txnid for this
      try {
        txnCtxt = new TransactionContext(txnId, KamanjaMetadata.gNodeContext, Array[Byte](), "")
        ThreadLocalStorage.txnContextInfo.set(txnCtxt)
        run1(txnCtxt)
      } catch {
        case e: Exception => throw e
        case e: Throwable => throw e
      } finally {
        ThreadLocalStorage.txnContextInfo.remove
        if (txnCtxt != null) {
          KamanjaMetadata.gNodeContext.getEnvCtxt.rollbackData(txnId)
        }
      }
    }

    private def run1(txnCtxt: TransactionContext) {
      if (updMetadataExecutor.isShutdown)
        return

      if (zkTransaction == null || zkTransaction.Notifications.size == 0) {
        // nothing to do
        return
      }

      if (mdMgr == null) {
        LOG.error("Metadata Manager should not be NULL while updaing metadta in Kamanja manager.")
        return
      }

      MetadataAPIImpl.UpdateMdMgr(zkTransaction)

      if (updMetadataExecutor.isShutdown)
        return

      val obj = new KamanjaMetadata

      // BUGBUG:: Not expecting added element & Removed element will happen in same transaction at this moment
      // First we are adding what ever we need to add, then we are removing. So, we are locking before we append to global array and remove what ever is gone.
      val removedModels = new ArrayBuffer[(String, String, Long)]
      val removedMessages = new ArrayBuffer[(String, String, Long)]
      val removedContainers = new ArrayBuffer[(String, String, Long)]

      //// Check for Jars -- Begin
      val allJarsToBeValidated = scala.collection.mutable.Set[String]();

      val unloadMsgsContainers = scala.collection.mutable.Set[String]()

      var removedValues = 0

      zkTransaction.Notifications.foreach(zkMessage => {
        if (updMetadataExecutor.isShutdown)
          return
        val key = zkMessage.NameSpace + "." + zkMessage.Name + "." + zkMessage.Version
        LOG.debug("Processing ZooKeeperNotification, the object => " + key + ",objectType => " + zkMessage.ObjectType + ",Operation => " + zkMessage.Operation)
        zkMessage.ObjectType match {
          case "ModelDef" => {
            zkMessage.Operation match {
              case "Add" => {
                try {
                  val mdl = mdMgr.Model(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, true)
                  if (mdl != None) {
                    allJarsToBeValidated ++= GetAllJarsFromElem(mdl.get)
                  }
                } catch {
                  case e: Exception => {
                    val stackTrace = StackTrace.ThrowableTraceString(e)
                    LOG.debug("StackTrace:" + stackTrace)
                  }
                }
              }
              case "Remove" | "Deactivate" => { removedValues += 1 }
              case _                       => {}
            }
          }
          case "MessageDef" => {
            unloadMsgsContainers += (zkMessage.NameSpace + "." + zkMessage.Name)
            zkMessage.Operation match {
              case "Add" => {
                try {
                  val msg = mdMgr.Message(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, true)
                  if (msg != None) {
                    allJarsToBeValidated ++= GetAllJarsFromElem(msg.get)
                  }
                } catch {
                  case e: Exception => {
                    val stackTrace = StackTrace.ThrowableTraceString(e)
                    LOG.error("StackTrace:" + stackTrace)
                  }
                }
              }
              case "Remove" => { removedValues += 1 }
              case _        => {}
            }
          }
          case "ContainerDef" => {
            unloadMsgsContainers += (zkMessage.NameSpace + "." + zkMessage.Name)
            zkMessage.Operation match {
              case "Add" => {
                try {
                  val container = mdMgr.Container(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, true)
                  if (container != None) {
                    allJarsToBeValidated ++= GetAllJarsFromElem(container.get)
                  }
                } catch {
                  case e: Exception => {
                    val stackTrace = StackTrace.ThrowableTraceString(e)
                    LOG.error("StackTrace:" + stackTrace)
                  }
                }
              }
              case "Remove" => { removedValues += 1 }
              case _        => {}
            }
          }
          case _ => {}
        }
      })

      // Removed some elements
      if (removedValues > 0) {
        reent_lock.writeLock().lock();
        try {
          KamanjaConfiguration.metadataLoader = new KamanjaLoaderInfo(KamanjaConfiguration.metadataLoader, true, true)
          envCtxt.SetClassLoader(KamanjaConfiguration.metadataLoader.loader)
        } catch {
          case e: Exception => {
          }
        } finally {
          reent_lock.writeLock().unlock();
        }
      }

      if (unloadMsgsContainers.size > 0)
        envCtxt.clearIntermediateResults(unloadMsgsContainers.toArray)

      val nonExistsJars = Utils.CheckForNonExistanceJars(allJarsToBeValidated.toSet)
      if (nonExistsJars.size > 0) {
        LOG.error("Not found jars in Messages/Containers/Models Jars List : {" + nonExistsJars.mkString(", ") + "}")
        // return
      }

      //// Check for Jars -- End

      zkTransaction.Notifications.foreach(zkMessage => {
        if (updMetadataExecutor.isShutdown)
          return
        val key = zkMessage.NameSpace + "." + zkMessage.Name + "." + zkMessage.Version.toLong
        LOG.info("Processing ZooKeeperNotification, the object => " + key + ",objectType => " + zkMessage.ObjectType + ",Operation => " + zkMessage.Operation)
        zkMessage.ObjectType match {
          case "ModelDef" => {
            zkMessage.Operation match {
              case "Add" | "Activate" => {
                try {
                  val mdl = mdMgr.Model(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, true)
                  if (mdl != None) {
                    obj.PrepareModelFactory(mdl.get, true, txnCtxt)
                  } else {
                    LOG.error("Failed to find Model:" + key)
                  }
                } catch {
                  case e: Exception => {

                    LOG.error("Failed to Add Model:" + key)
                  }
                }
              }
              case "Remove" | "Deactivate" => {
                try {
                  removedModels += ((zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong))
                } catch {
                  case e: Exception => {

                    LOG.error("Failed to Remove Model:" + key)
                  }
                }
              }
              case _ => {
                LOG.error("Unknown Operation " + zkMessage.Operation + " in zookeeper notification, notification is not processed ..")
              }
            }
          }
          case "MessageDef" => {
            zkMessage.Operation match {
              case "Add" => {
                try {
                  val msg = mdMgr.Message(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, true)
                  if (msg != None) {
                    obj.PrepareMessage(msg.get, true)
                  } else {
                    LOG.error("Failed to find Message:" + key)
                  }
                } catch {
                  case e: Exception => {
                    LOG.error("Failed to Add Message:" + key)
                  }
                }
              }
              case "Remove" => {
                try {
                  removedMessages += ((zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong))
                } catch {
                  case e: Exception => {
                    LOG.error("Failed to Remove Message:" + key)
                  }
                }
              }
              case _ => {
                LOG.error("Unknown Operation " + zkMessage.Operation + " in zookeeper notification, notification is not processed ..")
              }
            }
          }
          case "ContainerDef" => {
            zkMessage.Operation match {
              case "Add" => {
                try {
                  val container = mdMgr.Container(zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong, true)
                  if (container != None) {
                    obj.PrepareContainer(container.get, true, false)
                  } else {
                    LOG.error("Failed to find Container:" + key)
                  }
                } catch {
                  case e: Exception => {
                    LOG.error("Failed to Add Container:" + key)
                  }
                }
              }
              case "Remove" => {
                try {
                  removedContainers += ((zkMessage.NameSpace, zkMessage.Name, zkMessage.Version.toLong))
                } catch {
                  case e: Exception => {

                    LOG.error("Failed to Remove Container:" + key)
                  }
                }
              }
              case _ => {
                LOG.error("Unknown Operation " + zkMessage.Operation + " in zookeeper notification, notification is not processed ..")
              }
            }
          }
          case "OutputMsgDef" => {

          }
          case _ => {
            LOG.warn("Unknown objectType " + zkMessage.ObjectType + " in zookeeper notification, notification is not processed ..")
          }
        }
      })

      // Lock the global object here and update the global objects
      if (updMetadataExecutor.isShutdown == false)
        UpdateKamanjaMdObjects(obj.messageObjects, obj.containerObjects, obj.modelObjsMap, removedModels, removedMessages, removedContainers)
    }
  }

  override def getMessgeOrContainerInstance(MsgContainerType: String): MessageContainerBase = {
    var v: MsgContainerObjAndTransformInfo = null

    v = getMessageOrContainer(MsgContainerType)
    if (v != null && v.contmsgobj != null && v.contmsgobj.isInstanceOf[BaseMsgObj]) {
      return v.contmsgobj.asInstanceOf[BaseMsgObj].CreateNewMessage
    } else if (v != null && v.contmsgobj != null && v.contmsgobj.isInstanceOf[BaseContainerObj]) { // NOTENOTE: Not considering Base containers here
      return v.contmsgobj.asInstanceOf[BaseContainerObj].CreateNewContainer
    }
    return null
  }

  def getMessgeInfo(msgType: String): MsgContainerObjAndTransformInfo = {
    var exp: Exception = null
    var v: MsgContainerObjAndTransformInfo = null

    reent_lock.readLock().lock();
    try {
      v = localgetMessgeInfo(msgType)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        exp = e
      }
    } finally {
      reent_lock.readLock().unlock();
    }
    if (exp != null)
      throw exp
    v
  }

  def getModel(mdlName: String): MdlInfo = {
    var exp: Exception = null
    var v: MdlInfo = null

    reent_lock.readLock().lock();
    try {
      v = localgetModel(mdlName)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        exp = e
      }
    } finally {
      reent_lock.readLock().unlock();
    }
    if (exp != null)
      throw exp
    v
  }

  def getContainer(containerName: String): MsgContainerObjAndTransformInfo = {
    var exp: Exception = null
    var v: MsgContainerObjAndTransformInfo = null

    reent_lock.readLock().lock();
    try {
      v = localgetContainer(containerName)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        exp = e
      }
    } finally {
      reent_lock.readLock().unlock();
    }
    if (exp != null)
      throw exp
    v
  }

  def getMessageOrContainer(msgOrContainerName: String): MsgContainerObjAndTransformInfo = {
    var exp: Exception = null
    var v: MsgContainerObjAndTransformInfo = null

    reent_lock.readLock().lock();
    try {
      v = localgetMessgeOrContainer(msgOrContainerName)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        exp = e
      }
    } finally {
      reent_lock.readLock().unlock();
    }
    if (exp != null)
      throw exp
    v
  }

  def getAllMessges: Map[String, MsgContainerObjAndTransformInfo] = {
    var exp: Exception = null
    var v: Map[String, MsgContainerObjAndTransformInfo] = null

    reent_lock.readLock().lock();
    try {
      v = localgetAllMessges
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        exp = e
      }
    } finally {
      reent_lock.readLock().unlock();
    }
    if (exp != null)
      throw exp
    v
  }

  def getAllModels: (Array[(String, MdlInfo)], Long) = {
    var exp: Exception = null
    var v: Array[(String, MdlInfo)] = null

    reent_lock.readLock().lock();
    try {
      v = localgetAllModels
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        exp = e
      }
    } finally {
      reent_lock.readLock().unlock();
    }
    if (exp != null)
      throw exp
    (v, mdlsChangedCntr)
  }

  def getAllContainers: Map[String, MsgContainerObjAndTransformInfo] = {
    var exp: Exception = null
    var v: Map[String, MsgContainerObjAndTransformInfo] = null

    reent_lock.readLock().lock();
    try {
      v = localgetAllContainers
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        exp = e
      }
    } finally {
      reent_lock.readLock().unlock();
    }
    if (exp != null)
      throw exp
    v
  }

  private def localgetMessgeInfo(msgType: String): MsgContainerObjAndTransformInfo = {
    if (messageContainerObjects == null) return null
    val v = messageContainerObjects.getOrElse(msgType.toLowerCase, null)
    if (v != null && v.contmsgobj != null && v.contmsgobj.isInstanceOf[BaseMsgObj])
      return v
    return null
  }

  private def localgetModel(mdlName: String): MdlInfo = {
    if (modelObjs == null) return null
    modelObjs.getOrElse(mdlName.toLowerCase, null)
  }

  private def localgetContainer(containerName: String): MsgContainerObjAndTransformInfo = {
    if (messageContainerObjects == null) return null
    val v = messageContainerObjects.getOrElse(containerName.toLowerCase, null)
    if ((v != null && v.contmsgobj == null) // Base containers 
      || (v != null && v.contmsgobj != null && v.contmsgobj.isInstanceOf[BaseContainerObj]))
      return v
    return null
  }

  private def localgetMessgeOrContainer(msgOrContainerName: String): MsgContainerObjAndTransformInfo = {
    if (messageContainerObjects == null) return null
    val v = messageContainerObjects.getOrElse(msgOrContainerName.toLowerCase, null)
    v
  }

  private def localgetAllMessges: Map[String, MsgContainerObjAndTransformInfo] = {
    if (messageContainerObjects == null) return null
    messageContainerObjects.filter(o => {
      val v = o._2
      (v != null && v.contmsgobj != null && v.contmsgobj.isInstanceOf[BaseMsgObj])
    }).toMap
  }

  private def localgetAllModels: Array[(String, MdlInfo)] = {
    modelExecOrderedObjects
  }

  private def localgetAllContainers: Map[String, MsgContainerObjAndTransformInfo] = {
    if (messageContainerObjects == null) return null
    messageContainerObjects.filter(o => {
      val v = o._2
      ((v != null && v.contmsgobj == null) // Base containers 
        || (v != null && v.contmsgobj != null && v.contmsgobj.isInstanceOf[BaseContainerObj]))
    }).toMap
  }

  def getMdMgr: MdMgr = mdMgr

  def Shutdown: Unit = {
    if (zkListener != null)
      zkListener.Shutdown
    zkListener = null
    if (updMetadataExecutor != null) {
      updMetadataExecutor.shutdownNow
      while (updMetadataExecutor.isTerminated == false) {
        Thread.sleep(100)
      }
    }
  }

  def GetModelsChangedCounter = mdlsChangedCntr
}

