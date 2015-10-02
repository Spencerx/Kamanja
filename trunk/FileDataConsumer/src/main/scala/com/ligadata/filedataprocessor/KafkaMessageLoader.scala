package com.ligadata.filedataprocessor

import java.io.{IOException, File, PrintWriter}
import java.nio.file.StandardCopyOption._
import java.nio.file.{Paths, Files}
import java.util.Properties

import com.ligadata.Exceptions.{InternalErrorException, UnsupportedObjectException, MsgCompilationFailedException, StackTrace}
import com.ligadata.KamanjaBase._
import com.ligadata.MetadataAPI.MetadataAPIImpl
import com.ligadata.Utils.{Utils, KamanjaLoaderInfo}
import com.ligadata.ZooKeeper.CreateClient
import com.ligadata.kamanja.metadata.MdMgr._
import com.ligadata.kamanja.metadata.MessageDef
import kafka.producer.{KeyedMessage, ProducerConfig, Producer}
import org.apache.curator.framework.CuratorFramework
import org.apache.log4j.Logger

/**
 * Created by danielkozin on 9/24/15.
 */
class KafkaMessageLoader(partIdx: Int , inConfiguration: scala.collection.mutable.Map[String, String]) {
  var fileBeingProcessing: String = ""
  var lastOffsetProcessed: Int = 0
  lazy val loggerName = this.getClass.getName
  lazy val logger = Logger.getLogger(loggerName)

  // Set up some properties for the Kafka Producer
  val props = new Properties()
  props.put("metadata.broker.list", inConfiguration(SmartFileAdapterConstants.KAFKA_BROKER));
  props.put("request.required.acks", "1")
  // create the producer object
  val producer = new Producer[AnyRef, AnyRef](new ProducerConfig(props))

  var delimiters = new DataDelimiters
  delimiters.keyAndValueDelimiter = inConfiguration.getOrElse(SmartFileAdapterConstants.KV_SEPARATOR,"\\x01")
  delimiters.fieldDelimiter = inConfiguration.getOrElse(SmartFileAdapterConstants.FIELD_SEPARATOR,"\\x01")
  delimiters.valueDelimiter = inConfiguration.getOrElse(SmartFileAdapterConstants.VALUE_SEPARATOR,"~")

  val zkcConnectString = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("ZOOKEEPER_CONNECT_STRING")
  logger.debug(partIdx + " SMART FILE CONSUMER Using zookeeper " +zkcConnectString)
  val znodePath = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("ZNODE_PATH") + "/smartFileConsumer/" + partIdx
  var zkc: CuratorFramework = initZookeeper
  var objInst: Any = configureMessageDef
  if (objInst == null) {
    shutdown
    throw new UnsupportedObjectException("Unknown message definition " + inConfiguration(SmartFileAdapterConstants.MESSAGE_NAME))
  }


  def pushData(messages: Array[KafkaMessage]): Unit = {
    // First, if we are handling failover, then the messages could be of size 0.
    logger.debug("SMART FILE CONSUMER **** processing "+messages.size+"messages")
    if (messages.size == 0) return

    // If we start processing a new file, then mark so in the zk.
    if (fileBeingProcessing.compareToIgnoreCase(messages(0).relatedFileName) != 0) {
      fileBeingProcessing = messages(0).relatedFileName
      val zkFname = "{" + "\""+fileBeingProcessing+ "\":" + 0 + "}"
      zkc.setData.forPath(znodePath, zkFname.getBytes)
    }

    messages.foreach(msg => {
      // Now, there are some special cases here.  If offset is -1, then its just a signal to close the file
      // else, this may or may not be the last message in the file... look to isLast for guidance.
      if(msg.offsetInFile > 0) {
        var inputData =  CreateKafkaInput(new String(msg.msg), SmartFileAdapterConstants.MESSAGE_NAME, delimiters)

        logger.debug(partIdx +" SMART FILE CONSUMER \nKafkaMessage:\n  File: " + msg.relatedFileName+", offset:  "+ msg.offsetInFile + " Message Partition ID is "+
                     objInst.asInstanceOf[MessageContainerObjBase].PartitionKeyData(inputData).mkString + "\n " + new String(msg.msg))
        try {
          //if ( msg.offsetInFile > 1) throw new Exception("FUKCYEAH")
          producer.send(new KeyedMessage(inConfiguration(SmartFileAdapterConstants.KAFKA_TOPIC),
            objInst.asInstanceOf[MessageContainerObjBase].PartitionKeyData(inputData).mkString.getBytes("UTF8"),
            new String(msg.msg).getBytes("UTF8")))

          val zkFname = "{" + "\""+fileBeingProcessing+ "\":" + msg.offsetInFile + "}"
          zkc.setData.forPath(znodePath, zkFname.getBytes)
        } catch {
          case e: Exception =>
            logger.error(partIdx +" Could not add to the queue due to an Exception "+ e.getMessage)
            e.printStackTrace
            shutdown
            throw e
        }
      }

      if (msg.isLast) {
        try {
          // Either move or rename the file.
          var fileStruct = msg.relatedFileName.split("/")
          if (inConfiguration.getOrElse(SmartFileAdapterConstants.DIRECTORY_TO_MOVE_TO, null) != null) {
            logger.debug(partIdx +" SMART FILE CONSUMER Moving File" + msg.relatedFileName + " to "+ inConfiguration(SmartFileAdapterConstants.DIRECTORY_TO_MOVE_TO))
            Files.copy(Paths.get(msg.relatedFileName), Paths.get(inConfiguration(SmartFileAdapterConstants.DIRECTORY_TO_MOVE_TO)+"/"+ fileStruct(fileStruct.size - 1)),REPLACE_EXISTING)
            Files.deleteIfExists(Paths.get(msg.relatedFileName))
          } else {
            logger.debug(partIdx +" SMART FILE CONSUMER Renaming file "+ msg.relatedFileName + " to " + msg.relatedFileName + "_COMPLETE")
            (new File(msg.relatedFileName)).renameTo(new File(msg.relatedFileName + "_COMPLETE"))
          }

          // Remove reference to this file from Zookeeper - this file is done and will not be replayed
          //
          // SetData in Zookeeper... set null...
          zkc.setData.forPath(znodePath, null)
        } catch {
          case ioe: IOException => ioe.printStackTrace()
        }
      }

    })
  }

  /**
   *
   * @param inputData
   * @param associatedMsg
   * @param delimiters
   * @return
   */
  private def CreateKafkaInput(inputData: String, associatedMsg: String, delimiters: DataDelimiters): InputData = {
    if (associatedMsg == null || associatedMsg.size == 0) {
      shutdown
      throw new Exception("KV data expecting Associated messages as input.")
    }

    if (delimiters.fieldDelimiter == null) delimiters.fieldDelimiter = ","
    if (delimiters.valueDelimiter == null) delimiters.valueDelimiter = "~"
    if (delimiters.keyAndValueDelimiter == null) delimiters.keyAndValueDelimiter = "\\x01"

    val str_arr = inputData.split(delimiters.fieldDelimiter, -1)
    val inpData = new KvData(inputData, delimiters)
    val dataMap = scala.collection.mutable.Map[String, String]()

    if (delimiters.fieldDelimiter.compareTo(delimiters.keyAndValueDelimiter) == 0) {
      if (str_arr.size % 2 != 0) {
        val errStr = "Expecting Key & Value pairs are even number of tokens when FieldDelimiter & KeyAndValueDelimiter are matched. We got %d tokens from input string %s".format(str_arr.size, inputData)
        logger.error(errStr)
        shutdown
        throw new Exception(errStr)
      }
      for (i <- 0 until str_arr.size by 2) {
        dataMap(str_arr(i).trim) = str_arr(i + 1)
      }
    } else {
      str_arr.foreach(kv => {
        val kvpair = kv.split(delimiters.keyAndValueDelimiter)
        if (kvpair.size != 2) {
          shutdown
          throw new Exception("Expecting Key & Value pair only")
        }
        dataMap(kvpair(0).trim) = kvpair(1)
      })
    }

    inpData.dataMap = dataMap.toMap
    inpData

  }


  /**
   *
   */
  def initZookeeper: CuratorFramework = {
    try {
      CreateClient.CreateNodeIfNotExists(zkcConnectString, znodePath)
      return CreateClient.createSimple(zkcConnectString)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:" + stackTrace)
        throw new InternalErrorException("Failed to start a zookeeper session with(" + zkcConnectString + "): " + e.getMessage())
      }
    }
  }

  /**
   *
   * @return
   */
  private def configureMessageDef (): com.ligadata.KamanjaBase.BaseMsgObj = {
    val loaderInfo = new KamanjaLoaderInfo()
    var msgDef: MessageDef = null
    try {
      val(typNameSpace, typName) = com.ligadata.kamanja.metadata.Utils.parseNameTokenNoVersion(inConfiguration(SmartFileAdapterConstants.MESSAGE_NAME))
      msgDef = mdMgr.ActiveMessage(typNameSpace, typName)
    } catch {
      case e: Exception => {
        shutdown
        logger.error("Unable to to parse message defintion")
        throw new UnsupportedObjectException("Unknown message definition " + inConfiguration(SmartFileAdapterConstants.MESSAGE_NAME))
      }
    }

    if (msgDef == null) {
      shutdown
      logger.error("Unable to to retrieve message defintion")
      throw new UnsupportedObjectException("Unknown message definition " + inConfiguration(SmartFileAdapterConstants.MESSAGE_NAME))
    }
    // Just in case we want this to deal with more then 1 MSG_DEF in a future.  - msgName paramter will probably have to
    // be an array inthat case.. but for now......
    var allJars = collection.mutable.Set[String]()
    allJars = allJars + msgDef.jarName

    var jarPaths0 = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS").split(",").toSet
    jarPaths0 = jarPaths0 + MetadataAPIImpl.GetMetadataAPIConfig.getProperty("COMPILER_WORK_DIR")

    Utils.LoadJars(allJars.map(j => Utils.GetValidJarFile(jarPaths0, j)).toArray, loaderInfo.loadedJars, loaderInfo.loader)
    val jarName0 = Utils.GetValidJarFile(jarPaths0, msgDef.jarName)
    var classNames = Utils.getClasseNamesInJar(jarName0)


    var tempCurClass: Class[_] = null
    classNames.foreach(clsName => {
      try {
        Class.forName(clsName, true, loaderInfo.loader)
      } catch {
        case e: Exception => {
          logger.error("Failed to load Model class %s with Reason:%s Message:%s".format(clsName, e.getCause, e.getMessage))
          throw e // Rethrow
        }
      }

      var curClz = Class.forName(clsName, true, loaderInfo.loader)
      tempCurClass = curClz

      var isMsg = false
      while (curClz != null && isMsg == false) {
        isMsg = Utils.isDerivedFrom(curClz, "com.ligadata.KamanjaBase.BaseMsgObj")
        if (isMsg == false)
          curClz = curClz.getSuperclass()
      }

      if (isMsg) {
        try {
          // Trying Singleton Object
          val module = loaderInfo.mirror.staticModule(clsName)
          val obj = loaderInfo.mirror.reflectModule(module)
          objInst = obj.instance
          return objInst.asInstanceOf[com.ligadata.KamanjaBase.BaseMsgObj]
        } catch {
          case e: java.lang.NoClassDefFoundError => {
            e.printStackTrace()
            throw e
          }
          case e: Exception => {
            objInst = tempCurClass.newInstance
            return objInst.asInstanceOf[com.ligadata.KamanjaBase.BaseMsgObj]
          }
        }
      }
    })
    return null
  }

  /**
   *
   */
  private def shutdown: Unit = {
    MetadataAPIImpl.shutdown
    if (producer != null)
      producer.close
    if (zkc != null)
      zkc.close

    Thread.sleep(2000)
  }
}
