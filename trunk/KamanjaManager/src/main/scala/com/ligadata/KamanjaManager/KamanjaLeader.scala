
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

import com.ligadata.KamanjaBase._
import com.ligadata.InputOutputAdapterInfo.{ ExecContext, InputAdapter, OutputAdapter, ExecContextObj, PartitionUniqueRecordKey, PartitionUniqueRecordValue, StartProcPartInfo }
import com.ligadata.kamanja.metadata.{ BaseElem, MappedMsgTypeDef, BaseAttributeDef, StructTypeDef, EntityType, AttributeDef, ArrayBufTypeDef, MessageDef, ContainerDef, ModelDef }
import com.ligadata.kamanja.metadata._
import com.ligadata.kamanja.metadata.MdMgr._

import com.ligadata.kamanja.metadataload.MetadataLoad
import scala.collection.mutable.TreeSet
import com.ligadata.KamanjaBase.{ MessageContainerObjBase, BaseMsgObj, BaseContainer, TransformMessage, EnvContext }
import scala.collection.mutable.HashMap
import org.apache.logging.log4j.{ Logger, LogManager }
import scala.collection.mutable.ArrayBuffer
import com.ligadata.Serialize._
import com.ligadata.ZooKeeper._
import org.apache.curator.framework._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.apache.curator.utils.ZKPaths
import scala.actors.threadpool.{ Executors, ExecutorService }
import com.ligadata.Exceptions.{ FatalAdapterException, StackTrace }
import scala.collection.JavaConversions._
import com.ligadata.KvBase.{ Key }

case class AdapMaxPartitions(Adap: String, MaxParts: Int)
case class NodeDistMap(Adap: String, Parts: List[String])
case class DistributionMap(Node: String, Adaps: List[NodeDistMap])
case class FoundKeysInValidation(K: String, V1: String, V2: Int, V3: Int, V4: Long)
case class ActionOnAdaptersMap(action: String, adaptermaxpartitions: Option[List[AdapMaxPartitions]], distributionmap: Option[List[DistributionMap]], foundKeysInValidation: Option[List[FoundKeysInValidation]])

object KamanjaLeader {
  private[this] val LOG = LogManager.getLogger(getClass);
  private[this] val lock = new Object()
  private[this] val lock1 = new Object()
  private[this] var clusterStatus = ClusterStatus("", false, "", null)
  private[this] var zkLeaderLatch: ZkLeaderLatch = _
  private[this] var nodeId: String = _
  private[this] var zkConnectString: String = _
  private[this] var engineLeaderZkNodePath: String = _
  private[this] var engineDistributionZkNodePath: String = _
  private[this] var dataChangeZkNodePath: String = _
  private[this] var adaptersStatusPath: String = _
  private[this] var zkSessionTimeoutMs: Int = _
  private[this] var zkConnectionTimeoutMs: Int = _
  private[this] var zkEngineDistributionNodeListener: ZooKeeperListener = _
  private[this] var zkAdapterStatusNodeListener: ZooKeeperListener = _
  private[this] var zkDataChangeNodeListener: ZooKeeperListener = _
  private[this] var zkcForSetData: CuratorFramework = null
  private[this] val setDataLockObj = new Object()
  private[this] var distributionMap = scala.collection.mutable.Map[String, scala.collection.mutable.Map[String, ArrayBuffer[String]]]() // Nodeid & Unique Keys (adapter unique name & unique key)
  private[this] var foundKeysInValidation: scala.collection.immutable.Map[String, (String, Int, Int, Long)] = _
  private[this] var adapterMaxPartitions = scala.collection.mutable.Map[String, Int]() // Adapters & Max Partitions
  private[this] var allPartitionsToValidate = scala.collection.mutable.Map[String, Set[String]]()
  private[this] var nodesStatus = scala.collection.mutable.Set[String]() // NodeId
  private[this] var expectedNodesAction: String = _
  private[this] var curParticipents = Set[String]() // Derived from clusterStatus.participants
  private[this] var canRedistribute = false
  private[this] var inputAdapters: ArrayBuffer[InputAdapter] = _
  private[this] var outputAdapters: ArrayBuffer[OutputAdapter] = _
  private[this] var statusAdapters: ArrayBuffer[OutputAdapter] = _
  private[this] var validateInputAdapters: ArrayBuffer[InputAdapter] = _
  private[this] var envCtxt: EnvContext = _
  private[this] var updatePartitionsFlag = false
  private[this] var distributionExecutor = Executors.newFixedThreadPool(1)

  def Reset: Unit = {
    clusterStatus = ClusterStatus("", false, "", null)
    zkLeaderLatch = null
    nodeId = null
    zkConnectString = null
    engineLeaderZkNodePath = null
    engineDistributionZkNodePath = null
    dataChangeZkNodePath = null
    adaptersStatusPath = null
    zkSessionTimeoutMs = 0
    zkConnectionTimeoutMs = 0
    zkEngineDistributionNodeListener = null
    zkAdapterStatusNodeListener = null
    zkDataChangeNodeListener = null
    zkcForSetData = null
    distributionMap = scala.collection.mutable.Map[String, scala.collection.mutable.Map[String, ArrayBuffer[String]]]() // Nodeid & Unique Keys (adapter unique name & unique key)
    foundKeysInValidation = null
    adapterMaxPartitions = scala.collection.mutable.Map[String, Int]() // Adapters & Max Partitions
    allPartitionsToValidate = scala.collection.mutable.Map[String, Set[String]]()
    nodesStatus = scala.collection.mutable.Set[String]() // NodeId
    expectedNodesAction = null
    curParticipents = Set[String]() // Derived from clusterStatus.participants
    canRedistribute = false
    inputAdapters = null
    outputAdapters = null
    statusAdapters = null
    validateInputAdapters = null
    envCtxt = null
    updatePartitionsFlag = false
    distributionExecutor = Executors.newFixedThreadPool(1)
  }

  private def SetCanRedistribute(redistFlag: Boolean): Unit = lock.synchronized {
    canRedistribute = redistFlag
  }

  private def SendUnSentInfoToOutputAdapters: Unit = lock.synchronized {
    /*
    // LOG.info("SendUnSentInfoToOutputAdapters -- envCtxt:" + envCtxt + ", outputAdapters:" + outputAdapters)
    if (envCtxt != null && outputAdapters != null) {
      // Information found in Committing list
      val committingInfo = envCtxt.getAllIntermediateCommittingInfo // Array[(String, (Long, String, List[(String, String)]))]

      LOG.info("Information found in Committing Info. table:" + committingInfo.map(info => (info._1, (info._2._1, info._2._2, info._2._3.mkString(";")))).mkString(","))

      if (committingInfo != null && committingInfo.size > 0) {
        // For current key, we need to hold the values of Committing, What ever we have in main table, Validate Adapter Info
        val currentValues = scala.collection.mutable.Map[String, ((Long, String, List[(String, String, String)]), (Long, String), (String, Int, Int, Long))]()

        committingInfo.foreach(ci => {
          currentValues(ci._1.toLowerCase) = (ci._2, null, null)
        })

        val keys = committingInfo.map(info => { info._1 })

        // Get AdapterUniqKvDataInfo 
        val allAdapterUniqKvDataInfo = envCtxt.getAllAdapterUniqKvDataInfo(keys)

        if (allAdapterUniqKvDataInfo != null) {
          LOG.debug("Information found in data table:" + allAdapterUniqKvDataInfo.mkString(","))
          allAdapterUniqKvDataInfo.foreach(ai => {
            val key = ai._1.toLowerCase
            val fndVal = currentValues.getOrElse(key, null)
            if (fndVal != null) {
              currentValues(key) = (fndVal._1, ai._2, null)
            }
          })
        }

        // Get recent information from output validators
        val validateFndKeysAndVals = getValidateAdaptersInfo
        if (validateFndKeysAndVals != null) {
          LOG.debug("Information found in Validate Adapters:" + validateFndKeysAndVals.mkString(","))
          validateFndKeysAndVals.foreach(validatekv => {
            val key = validatekv._1.toLowerCase
            val fndVal = currentValues.getOrElse(key, null)
            if (fndVal != null) {
              currentValues(key) = (fndVal._1, fndVal._2, validatekv._2)
            }
          })
        }

        val allOuAdapters = if (outputAdapters != null && currentValues.size > 0) outputAdapters.map(o => (o.inputConfig.Name.toLowerCase, o)).toMap else Map[String, OutputAdapter]()

        // Now find which one we need to resend
        // BUGBUG:: Not yet handling M/N Sub Messages for a Message
        currentValues.foreach(possibleKeyToSend => {
          // We should not have possibleKeyToSend._2._1._1 < possibleKeyToSend._2._2._1 and if it is possibleKeyToSend._2._1._1 > possibleKeyToSend._2._2._1, the data is not yet committed to main table.
          if (possibleKeyToSend._2._1 != null && possibleKeyToSend._2._2 != null && possibleKeyToSend._2._1._1 == possibleKeyToSend._2._2._1) { // This is just committing record and is written in main table.
            if (possibleKeyToSend._2._3 == null || possibleKeyToSend._2._1._1 != possibleKeyToSend._2._3._4) { // Not yet written in. possibleKeyToSend._2._1._1 < possibleKeyToSend._2._3._4 should not happen and possibleKeyToSend._2._1._1 == possibleKeyToSend._2._3._4 is already written.
              val outputs = possibleKeyToSend._2._1._3.groupBy(_._1)
              outputs.foreach(output => {
                val oadap = allOuAdapters.getOrElse(output._1, null)
                if (oadap != null) {
                  oadap.send(output._2.map(out => out._3.getBytes("UTF8")).toArray, output._2.map(out => out._2.getBytes("UTF8")).toArray)
                }
              })
            }
          }
        })
        // Remove after sending again 
        // envCtxt.removeCommittedKeys(keys)
      }
    }
*/
  }

  private def UpdatePartitionsNodeData(eventType: String, eventPath: String, eventPathData: Array[Byte]): Unit = lock.synchronized {
    try {
      val evntPthData = if (eventPathData != null) (new String(eventPathData)) else "{}"
      val extractedNode = ZKPaths.getNodeFromPath(eventPath)
      LOG.info("UpdatePartitionsNodeData => eventType: %s, eventPath: %s, eventPathData: %s, Extracted Node:%s".format(eventType, eventPath, evntPthData, extractedNode))

      if (eventType.compareToIgnoreCase("CHILD_UPDATED") == 0) {
        if (curParticipents(extractedNode)) { // If this node is one of the participent, then work on this, otherwise ignore
          try {
            val json = parse(evntPthData)
            if (json == null || json.values == null) // Not doing any action if not found valid json
              return
            val values = json.values.asInstanceOf[Map[String, Any]]
            val action = values.getOrElse("action", "").toString.toLowerCase

            if (expectedNodesAction.compareToIgnoreCase(action) == 0) {
              nodesStatus += extractedNode
              if (nodesStatus.size == curParticipents.size && expectedNodesAction == "stopped" && (nodesStatus -- curParticipents).isEmpty) {
                // Send the data to output queues in case if anything not sent before
                SendUnSentInfoToOutputAdapters
                // envCtxt.PersistRemainingStateEntriesOnLeader
                nodesStatus.clear
                expectedNodesAction = "distributed"

                val fndKeyInVal = if (foundKeysInValidation == null) scala.collection.immutable.Map[String, (String, Int, Int, Long)]() else foundKeysInValidation

                // Set DISTRIBUTE Action on engineDistributionZkNodePath
                // Send all Unique keys to corresponding nodes 
                val distribute =
                  ("action" -> "distribute") ~
                    ("adaptermaxpartitions" -> adapterMaxPartitions.map(kv =>
                      ("Adap" -> kv._1) ~
                        ("MaxParts" -> kv._2))) ~
                    ("distributionmap" -> distributionMap.map(kv =>
                      ("Node" -> kv._1) ~
                        ("Adaps" -> kv._2.map(kv1 => ("Adap" -> kv1._1) ~
                          ("Parts" -> kv1._2.toList))))) ~
                    ("foundKeysInValidation" -> fndKeyInVal.map(kv =>
                      ("K" -> kv._1) ~
                        ("V1" -> kv._2._1) ~
                        ("V2" -> kv._2._2) ~
                        ("V3" -> kv._2._3) ~
                        ("V4" -> kv._2._4)))
                val sendJson = compact(render(distribute))
                SetNewDataToZkc(engineDistributionZkNodePath, sendJson.getBytes("UTF8"))
              }
            } else {
              val redStr = if (canRedistribute) "canRedistribute is true, Redistributing" else "canRedistribute is false, waiting until next call"
              // Got different action. May be re-distribute. For now any non-expected action we will redistribute
              LOG.info("UpdatePartitionsNodeData => eventType: %s, eventPath: %s, eventPathData: %s, Extracted Node:%s. Expected Action:%s, Recieved Action:%s %s.".format(eventType, eventPath, evntPthData, extractedNode, expectedNodesAction, action, redStr))
              if (canRedistribute) {
                LOG.warn("Got different action (%s) than expected. Going to redistribute the work".format(action, expectedNodesAction))
                SetUpdatePartitionsFlag
              }
            }
          } catch {
            case e: Exception => {
              LOG.error("UpdatePartitionsNodeData => Failed eventType: %s, eventPath: %s, eventPathData: %s, Reason:%s, Message:%s".format(eventType, eventPath, evntPthData, e.getCause, e.getMessage))
            }
          }
        }

      } else if (eventType.compareToIgnoreCase("CHILD_REMOVED") == 0) {
        // Not expected this. Need to check what is going on
        LOG.error("UpdatePartitionsNodeData => eventType: %s, eventPath: %s, eventPathData: %s".format(eventType, eventPath, evntPthData))
      } else if (eventType.compareToIgnoreCase("CHILD_ADDED") == 0) {
        // Not doing anything here
      }
    } catch {
      case e: Exception => {
        LOG.error("Exception while UpdatePartitionsNodeData, reason %s, message %s".format(e.getCause, e.getMessage))
      }
    }
  }

  private def getAllPartitionsToValidate: (Array[(String, String)], scala.collection.immutable.Map[String, Set[String]]) = lock.synchronized {
    val allPartitionUniqueRecordKeys = ArrayBuffer[(String, String)]()
    val allPartsToValidate = scala.collection.mutable.Map[String, Set[String]]()

    // Get all PartitionUniqueRecordKey for all Input Adapters

    inputAdapters.foreach(ia => {
      try {
        val uk = ia.GetAllPartitionUniqueRecordKey
        val name = ia.UniqueName
        val ukCnt = if (uk != null) uk.size else 0
        adapterMaxPartitions(name) = ukCnt
        if (ukCnt > 0) {
          val serUK = uk.map(k => {
            val kstr = k.Serialize
            LOG.debug("Unique Key in %s => %s".format(name, kstr))
            (name, kstr)
          })
          allPartitionUniqueRecordKeys ++= serUK
          allPartsToValidate(name) = serUK.map(k => k._2).toSet
        } else {
          allPartsToValidate(name) = Set[String]()
        }
      } catch {
        case fae: FatalAdapterException => {
          // Adapter could not get partition information and can't reconver.
          val causeStackTrace = StackTrace.ThrowableTraceString(fae.cause)
          LOG.error("Failed to get partitions from validate adapter " + ia.UniqueName + ", cause: \n" + causeStackTrace)
        }
        case e: Exception => {
          // Adapter could not get partition information and can't reconver.
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("Failed to get partitions from validate adapter " + ia.UniqueName + ", cause: \n" + causeStackTrace)
        }
        case e: Throwable => {
          // Adapter could not get partition information and can't reconver.
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("Failed to get partitions from validate adapter " + ia.UniqueName + ", cause: \n" + causeStackTrace)
        }
      }
    })
    return (allPartitionUniqueRecordKeys.toArray, allPartsToValidate.toMap)
  }

  private def getValidateAdaptersInfo: scala.collection.immutable.Map[String, (String, Int, Int, Long)] = lock.synchronized {
    val savedValidatedAdaptInfo = envCtxt.GetValidateAdapterInformation
    val map = scala.collection.mutable.Map[String, String]()

    LOG.debug("savedValidatedAdaptInfo: " + savedValidatedAdaptInfo.mkString(","))

    savedValidatedAdaptInfo.foreach(kv => {
      map(kv._1.toLowerCase) = kv._2
    })

    CollectKeyValsFromValidation.clear
    validateInputAdapters.foreach(via => {
      try {
        // Get all Begin values for Unique Keys
        val begVals = via.getAllPartitionBeginValues
        val finalVals = new ArrayBuffer[StartProcPartInfo](begVals.size)

        // Replace the newly found ones
        val nParts = begVals.size
        for (i <- 0 until nParts) {
          val key = begVals(i)._1.Serialize.toLowerCase
          val foundVal = map.getOrElse(key, null)

          val info = new StartProcPartInfo

          if (foundVal != null) {
            val desVal = via.DeserializeValue(foundVal)
            info._validateInfoVal = desVal
            info._key = begVals(i)._1
            info._val = desVal
          } else {
            info._validateInfoVal = begVals(i)._2
            info._key = begVals(i)._1
            info._val = begVals(i)._2
          }

          finalVals += info
        }
        LOG.debug("Trying to read data from ValidatedAdapter: " + via.UniqueName)
        via.StartProcessing(finalVals.toArray, false)
      } catch {
        case e: FatalAdapterException => {
          // If validate adapter is not able to connect, just ignoring it for now
          val causeStackTrace = StackTrace.ThrowableTraceString(e.cause)
          LOG.error("Validate Adapter " + via.UniqueName + " failed to start processing. Message:" + e.getMessage + ", Reason:" + e.getCause + ". Internal Cause: \n" + causeStackTrace)
        }
        case e: Exception => {
          // If validate adapter is not able to connect, just ignoring it for now
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("Validate Adapter " + via.UniqueName + " failed to start processing. Message:" + e.getMessage + ", Reason:" + e.getCause + ". Internal Cause: \n" + causeStackTrace)
        }
        case e: Throwable => {
          // If validate adapter is not able to connect, just ignoring it for now
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("Validate Adapter " + via.UniqueName + " failed to start processing. Message:" + e.getMessage + ", Reason:" + e.getCause + ". Internal Cause: \n" + causeStackTrace)
        }
      }
    })

    var stillWait = true
    while (stillWait) {
      try {
        Thread.sleep(1000) // sleep 1000 ms and then check
      } catch {
        case e: Exception => {
          val stackTrace = StackTrace.ThrowableTraceString(e)
          LOG.debug("StackTrace:" + stackTrace)
        }
      }
      if ((System.nanoTime - CollectKeyValsFromValidation.getLastUpdateTime) < 1000 * 1000000) // 1000ms
        stillWait = true
      else
        stillWait = false
    }

    // Stopping the Adapters
    validateInputAdapters.foreach(via => {
      try {
        via.StopProcessing
      } catch {
        case fae: FatalAdapterException => {
          val causeStackTrace = StackTrace.ThrowableTraceString(fae.cause)
          LOG.error("Validate adapter " + via.UniqueName + "failed to stop processing, cause: \n" + causeStackTrace)
        }
        case e: Exception => {
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("Validate adapter " + via.UniqueName + "failed to stop processing, cause: \n" + causeStackTrace)
        }
        case e: Throwable => {
          val causeStackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("Validate adapter " + via.UniqueName + "failed to stop processing, cause: \n" + causeStackTrace)
        }
      }
    })

    if (distributionExecutor.isShutdown) {
      LOG.debug("Distribution Executor is shutting down")
      return scala.collection.immutable.Map[String, (String, Int, Int, Long)]()
    } else {
      val validateFndKeysAndVals = CollectKeyValsFromValidation.get
      LOG.debug("foundKeysAndValuesInValidation: " + validateFndKeysAndVals.map(v => { (v._1.toString, v._2.toString) }).mkString(","))
      return validateFndKeysAndVals
    }
  }

  private def UpdatePartitionsIfNeededOnLeader: Unit = lock.synchronized {
    val cs = GetClusterStatus
    if (cs.isLeader == false || cs.leader != cs.nodeId) return // This is not leader, just return from here. This is same as (cs.leader != cs.nodeId)

    LOG.warn("Distribution NodeId:%s, IsLeader:%s, Leader:%s, AllParticipents:{%s}".format(cs.nodeId, cs.isLeader.toString, cs.leader, cs.participants.mkString(",")))

    // Clear Previous Distribution Map
    distributionMap.clear
    adapterMaxPartitions.clear
    nodesStatus.clear
    expectedNodesAction = ""
    curParticipents = if (cs.participants != null) cs.participants.toSet else Set[String]()

    try {
      var tmpDistMap = ArrayBuffer[(String, scala.collection.mutable.Map[String, ArrayBuffer[String]])]()

      if (cs.participants != null) {

        // Create ArrayBuffer for each node participating at this moment
        cs.participants.foreach(p => {
          tmpDistMap += ((p, scala.collection.mutable.Map[String, ArrayBuffer[String]]()))
        })

        val (allPartitionUniqueRecordKeys, allPartsToValidate) = getAllPartitionsToValidate
        val validateFndKeysAndVals = getValidateAdaptersInfo

        allPartsToValidate.foreach(kv => {
          AddPartitionsToValidate(kv._1, kv._2)
        })

        foundKeysInValidation = validateFndKeysAndVals

        // Update New partitions for all nodes and Set the text
        val totalParticipents: Int = cs.participants.size
        if (allPartitionUniqueRecordKeys != null && allPartitionUniqueRecordKeys.size > 0) {
          LOG.debug("allPartitionUniqueRecordKeys: %d".format(allPartitionUniqueRecordKeys.size))
          var cntr: Int = 0
          allPartitionUniqueRecordKeys.foreach(k => {
            val fnd = foundKeysInValidation.getOrElse(k._2.toLowerCase, null)
            val af = tmpDistMap(cntr % totalParticipents)._2.getOrElse(k._1, null)
            if (af == null) {
              val af1 = new ArrayBuffer[String]
              af1 += (k._2)
              tmpDistMap(cntr % totalParticipents)._2(k._1) = af1
            } else {
              af += (k._2)
            }
            cntr += 1
          })
        }

        tmpDistMap.foreach(tup => {
          distributionMap(tup._1) = tup._2
        })
      }

      expectedNodesAction = "stopped"
      // Set STOP Action on engineDistributionZkNodePath
      val act = ("action" -> "stop")
      val sendJson = compact(render(act))
      SetNewDataToZkc(engineDistributionZkNodePath, sendJson.getBytes("UTF8"))
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
      }
    }
  }

  private def AddPartitionsToValidate(adapName: String, partitions: Set[String]): Unit = lock1.synchronized {
    allPartitionsToValidate(adapName) = partitions
  }

  private def GetPartitionsToValidate(adapName: String): Set[String] = lock1.synchronized {
    allPartitionsToValidate.getOrElse(adapName, null)
  }

  private def GetAllAdaptersPartitionsToValidate: Map[String, Set[String]] = lock1.synchronized {
    allPartitionsToValidate.toMap
  }

  private def SetClusterStatus(cs: ClusterStatus): Unit = lock1.synchronized {
    clusterStatus = cs
    updatePartitionsFlag = true
  }

  def GetClusterStatus: ClusterStatus = lock1.synchronized {
    return clusterStatus
  }

  private def IsLeaderNode: Boolean = lock1.synchronized {
    return (clusterStatus.isLeader && clusterStatus.leader == clusterStatus.nodeId)
  }

  private def IsLeaderNodeAndUpdatePartitionsFlagSet: Boolean = lock1.synchronized {
    if (clusterStatus.isLeader && clusterStatus.leader == clusterStatus.nodeId)
      return updatePartitionsFlag
    else
      return false
  }

  private def SetUpdatePartitionsFlag: Unit = lock1.synchronized {
    updatePartitionsFlag = true
  }

  private def GetUpdatePartitionsFlag: Boolean = lock1.synchronized {
    return updatePartitionsFlag
  }

  private def GetUpdatePartitionsFlagAndReset: Boolean = lock1.synchronized {
    val retVal = updatePartitionsFlag
    updatePartitionsFlag = false
    retVal
  }

  // Here Leader can change or Participants can change
  private def EventChangeCallback(cs: ClusterStatus): Unit = {
    LOG.debug("EventChangeCallback => Enter")
    KamanjaConfiguration.participentsChangedCntr += 1
    SetClusterStatus(cs)
    LOG.warn("NodeId:%s, IsLeader:%s, Leader:%s, AllParticipents:{%s}".format(cs.nodeId, cs.isLeader.toString, cs.leader, cs.participants.mkString(",")))
    LOG.debug("EventChangeCallback => Exit")
  }

  private def GetUniqueKeyValue(uk: String): (Long, String, List[(String, String, String)]) = {
    envCtxt.getAdapterUniqueKeyValue(0, uk)
  }

  private def StartNodeKeysMap(nodeKeysMap: scala.collection.immutable.Map[String, Array[String]], receivedJsonStr: String, adapMaxPartsMap: Map[String, Int], foundKeysInVald: Map[String, (String, Int, Int, Long)]): Boolean = {
    if (nodeKeysMap == null || nodeKeysMap.size == 0) {
      return true
    }

    var remainingInpAdapters = inputAdapters.toArray
    var failedWaitTime = 15000 // Wait time starts at 15 secs
    val maxFailedWaitTime = 60000 // Max Wait time 60 secs

    //BUGBUG:: We may be blocking here for long time. Which will not give any updates from zookeeper for this node.
    while (remainingInpAdapters.size > 0) {
      var failedInpAdapters = ArrayBuffer[InputAdapter]()
      remainingInpAdapters.foreach(ia => {
        val name = ia.UniqueName
        try {
          val uAK = nodeKeysMap.getOrElse(name, null)
          if (uAK != null) {
            val uKV = uAK.map(uk => { GetUniqueKeyValue(uk) })
            val maxParts = adapMaxPartsMap.getOrElse(name, 0)
            LOG.info("On Node %s for Adapter %s with Max Partitions %d UniqueKeys %s, UniqueValues %s".format(nodeId, name, maxParts, uAK.mkString(","), uKV.mkString(",")))

            LOG.debug("Deserializing Keys")
            val keys = uAK.map(k => ia.DeserializeKey(k))

            LOG.debug("Deserializing Values")
            val vals = uKV.map(v => ia.DeserializeValue(if (v != null) v._2 else null))

            LOG.debug("Deserializing Keys & Values done")

            val quads = new ArrayBuffer[StartProcPartInfo](keys.size)

            for (i <- 0 until keys.size) {
              val key = keys(i)

              val info = new StartProcPartInfo
              info._key = key
              info._val = vals(i)
              info._validateInfoVal = vals(i)
              quads += info
            }

            LOG.info(ia.UniqueName + " ==> Processing Keys & values: " + quads.map(q => { (q._key.Serialize, q._val.Serialize, q._validateInfoVal.Serialize) }).mkString(","))
            ia.StartProcessing(quads.toArray, true)
          }
        } catch {
          case fae: FatalAdapterException => {
            val causeStackTrace = StackTrace.ThrowableTraceString(fae.cause)
            LOG.error("Failed to start processing input adapter:" + name + "\n.Internal Cause:" + causeStackTrace)
            failedInpAdapters += ia
          }
          case e: Exception => {
            val stackTrace = StackTrace.ThrowableTraceString(e)
            LOG.error("Failed to start processing input adapter:" + name + "\n.Stack Trace:" + stackTrace)
            failedInpAdapters += ia
          }
          case t: Throwable => {
            val stackTrace = StackTrace.ThrowableTraceString(t)
            LOG.error("Failed to start processing input adapter:" + name + "\n.Stack Trace:" + stackTrace)
            failedInpAdapters += ia
          }
        }
      })

      remainingInpAdapters = failedInpAdapters.toArray

      if (remainingInpAdapters.size > 0) {
        try {
          LOG.error("Failed to start processing %d input adapters while distributing. Waiting for another %d milli seconds and going to start them again.".format(remainingInpAdapters.size, failedWaitTime))
          Thread.sleep(failedWaitTime)
        } catch {
          case e: Exception => {

          }
        }
        // Adjust time for next time
        if (failedWaitTime < maxFailedWaitTime) {
          failedWaitTime = failedWaitTime * 2
          if (failedWaitTime > maxFailedWaitTime)
            failedWaitTime = maxFailedWaitTime
        }
      }
    }

    return true
  }

  def GetAdaptersMaxPartitioinsMap(adaptermaxpartitions: Option[List[AdapMaxPartitions]]): Map[String, Int] = {
    val adapterMax = scala.collection.mutable.Map[String, Int]() // Adapters & Max Partitions
    if (adaptermaxpartitions != None && adaptermaxpartitions != null) {
      adaptermaxpartitions.get.foreach(adapMaxParts => {
        adapterMax(adapMaxParts.Adap) = adapMaxParts.MaxParts
      })
    }

    adapterMax.toMap
  }

  private def GetDistMapForNodeId(distributionmap: Option[List[DistributionMap]], nodeId: String): scala.collection.immutable.Map[String, Array[String]] = {
    val retVals = scala.collection.mutable.Map[String, Array[String]]()
    if (distributionmap != None && distributionmap != null) {
      val nodeDistMap = distributionmap.get.filter(ndistmap => { (ndistmap.Node.compareToIgnoreCase(nodeId) == 0) })
      if (nodeDistMap != None && nodeDistMap != null) { // At most 1 value, but not enforcing
        nodeDistMap.foreach(ndistmap => {
          ndistmap.Adaps.map(ndm => {
            retVals(ndm.Adap) = ndm.Parts.toArray
          })
        })
      }
    }

    retVals.toMap
  }

  // Using canRedistribute as startup mechanism here, because until we do bootstap ignore all the messages from this 
  private def ActionOnAdaptersDistImpl(receivedJsonStr: String): Unit = lock.synchronized {
    // LOG.debug("ActionOnAdaptersDistImpl => receivedJsonStr: " + receivedJsonStr)

    if (receivedJsonStr == null || receivedJsonStr.size == 0 || canRedistribute == false) {
      // nothing to do
      LOG.debug("ActionOnAdaptersDistImpl => Exit. receivedJsonStr: " + receivedJsonStr)
      return
    }

    if (IsLeaderNodeAndUpdatePartitionsFlagSet) {
      LOG.debug("Already got Re-distribution request. Ignoring any actions from ActionOnAdaptersDistImpl") // Atleast this happens on main node
      return
    }

    LOG.info("ActionOnAdaptersDistImpl => receivedJsonStr: " + receivedJsonStr)

    try {
      // Perform the action here (STOP or DISTRIBUTE for now)
      val json = parse(receivedJsonStr)
      if (json == null || json.values == null) { // Not doing any action if not found valid json
        LOG.debug("ActionOnAdaptersDistImpl => Exit. receivedJsonStr: " + receivedJsonStr)
        return
      }

      implicit val jsonFormats: Formats = DefaultFormats
      val actionOnAdaptersMap = json.extract[ActionOnAdaptersMap]

      actionOnAdaptersMap.action match {
        case "stop" => {
          try {
            var remInputAdaps = inputAdapters.toArray
            var failedWaitTime = 15000 // Wait time starts at 15 secs
            val maxFailedWaitTime = 60000 // Max Wait time 60 secs
            val maxTries = 5
            var tryNo = 0

            while (remInputAdaps.size > 0 && tryNo < maxTries) { // maximum trying only 5 times
              tryNo += 1
              var failedInputAdaps = ArrayBuffer[InputAdapter]()

              // STOP all Input Adapters on local node
              remInputAdaps.foreach(ia => {
                try {
                  ia.StopProcessing
                } catch {
                  case fae: FatalAdapterException => {
                    val causeStackTrace = StackTrace.ThrowableTraceString(fae.cause)
                    LOG.error("Input adapter " + ia.UniqueName + "failed to stop processing, cause: \n" + causeStackTrace)
                    failedInputAdaps += ia
                  }
                  case e: Exception => {
                    val causeStackTrace = StackTrace.ThrowableTraceString(e)
                    LOG.error("Input adapter " + ia.UniqueName + "failed to stop processing, cause: \n" + causeStackTrace)
                    failedInputAdaps += ia
                  }
                  case e: Throwable => {
                    val causeStackTrace = StackTrace.ThrowableTraceString(e)
                    LOG.error("Input adapter " + ia.UniqueName + "failed to stop processing, cause: \n" + causeStackTrace)
                    failedInputAdaps += ia
                  }
                }
              })

              remInputAdaps = failedInputAdaps.toArray
              if (remInputAdaps.size > 0 && distributionExecutor.isShutdown == false) {
                try {
                  LOG.error("Failed to stop %d input adapters. Waiting for another %d milli seconds and going to start them again.".format(remInputAdaps.size, failedWaitTime))
                  Thread.sleep(failedWaitTime)
                } catch {
                  case e: Exception => {

                  }
                }
                // Adjust time for next time
                if (failedWaitTime < maxFailedWaitTime) {
                  failedWaitTime = failedWaitTime * 2
                  if (failedWaitTime > maxFailedWaitTime)
                    failedWaitTime = maxFailedWaitTime
                }
              }
              if (distributionExecutor.isShutdown) // If it is shutting down, no more retries
                tryNo = maxTries
            }

            // Sleep for a sec
            try {
              Thread.sleep(1000)
            } catch {
              case e: Exception => {
                // Not doing anything
                val stackTrace = StackTrace.ThrowableTraceString(e)
                LOG.debug("\nStackTrace:" + stackTrace)
              }
            }

            if (distributionExecutor.isShutdown == false) {
              // Save the state and Clear the maps
              ProcessedAdaptersInfo.CommitAdapterValues
              ProcessedAdaptersInfo.clearInstances
              // envCtxt.PersistLocalNodeStateEntries
              envCtxt.clearIntermediateResults

              // Set STOPPED action in adaptersStatusPath + "/" + nodeId path
              val adaptrStatusPathForNode = adaptersStatusPath + "/" + nodeId
              val act = ("action" -> "stopped")
              val sendJson = compact(render(act))
              SetNewDataToZkc(adaptrStatusPathForNode, sendJson.getBytes("UTF8"))
            }
          } catch {
            case e: Exception => {
              LOG.error("Failed to get Input Adapters partitions. Reason:%s Message:%s".format(e.getCause, e.getMessage))
            }
          }
        }
        case "distribute" => {
          envCtxt.clearIntermediateResults // We may not need it here. But anyway safe side
          // Clear the maps
          ProcessedAdaptersInfo.clearInstances
          var distributed = true
          try {
            // get Unique Keys for this nodeId
            // Distribution Map 
            if (actionOnAdaptersMap.distributionmap != None && actionOnAdaptersMap.distributionmap != null) {
              val adapMaxPartsMap = GetAdaptersMaxPartitioinsMap(actionOnAdaptersMap.adaptermaxpartitions)
              val nodeDistMap = GetDistMapForNodeId(actionOnAdaptersMap.distributionmap, nodeId)

              var foundKeysInVald = scala.collection.mutable.Map[String, (String, Int, Int, Long)]()

              if (actionOnAdaptersMap.foundKeysInValidation != None && actionOnAdaptersMap.foundKeysInValidation != null) {
                actionOnAdaptersMap.foundKeysInValidation.get.foreach(ks => {
                  foundKeysInVald(ks.K.toLowerCase) = (ks.V1, ks.V2, ks.V3, ks.V4)
                })

              }
              StartNodeKeysMap(nodeDistMap, receivedJsonStr, adapMaxPartsMap, foundKeysInVald.toMap)
            }

          } catch {
            case e: Exception => {
              LOG.error("distribute action failed with reason %s, message %s".format(e.getCause, e.getMessage))
              distributed = false
            }
          }

          val adaptrStatusPathForNode = adaptersStatusPath + "/" + nodeId
          var sentDistributed = false
          if (distributed) {
            try {
              // Set DISTRIBUTED action in adaptersStatusPath + "/" + nodeId path
              val act = ("action" -> "distributed")
              val sendJson = compact(render(act))
              SetNewDataToZkc(adaptrStatusPathForNode, sendJson.getBytes("UTF8"))
              sentDistributed = true
            } catch {
              case e: Exception => {
                LOG.error("distribute action failed with reason %s, message %s".format(e.getCause, e.getMessage))
              }
            }
          }

          if (sentDistributed == false) {
            // Set RE-DISTRIBUTED action in adaptersStatusPath + "/" + nodeId path
            val act = ("action" -> "re-distribute")
            val sendJson = compact(render(act))
            SetNewDataToZkc(adaptrStatusPathForNode, sendJson.getBytes("UTF8"))
          }
        }
        case _ => {
          LOG.debug("No action performed, because of invalid action %s in json %s".format(actionOnAdaptersMap.action, receivedJsonStr))
        }
      }

      // 
    } catch {
      case e: Exception => {
        LOG.error("Found invalid JSON: %s".format(receivedJsonStr))
      }
    }

    LOG.debug("ActionOnAdaptersDistImpl => Exit. receivedJsonStr: " + receivedJsonStr)
  }

  private def ActionOnAdaptersDistribution(receivedJsonStr: String): Unit = {
    ActionOnAdaptersDistImpl(receivedJsonStr)
  }

  private def ActionOnDataChngImpl(receivedJsonStr: String): Unit = lock.synchronized {
    // LOG.debug("ActionOnDataChngImpl => receivedJsonStr: " + receivedJsonStr)
    if (receivedJsonStr == null || receivedJsonStr.size == 0) {
      // nothing to do
      // LOG.debug("ActionOnDataChngImpl => Exit. receivedJsonStr: " + receivedJsonStr)
      return
    }

    try {
      // Perform the action here
      val json = parse(receivedJsonStr)
      if (json == null || json.values == null) { // Not doing any action if not found valid json
        LOG.error("ActionOnAdaptersDistImpl => Exit. receivedJsonStr: " + receivedJsonStr)
        return
      }

      val values = json.values.asInstanceOf[Map[String, Any]]
      val changedMsgsContainers = values.getOrElse("changeddata", null)
      val tmpChngdContainersAndKeys = values.getOrElse("changeddatakeys", null)

      if (changedMsgsContainers != null) {
        // Expecting List/Array of String here
        var changedVals: Array[String] = null
        if (changedMsgsContainers.isInstanceOf[List[_]]) {
          try {
            changedVals = changedMsgsContainers.asInstanceOf[List[String]].toArray
          } catch {
            case e: Exception => {}
          }
        } else if (changedMsgsContainers.isInstanceOf[Array[_]]) {
          try {
            changedVals = changedMsgsContainers.asInstanceOf[Array[String]]
          } catch {
            case e: Exception => {}
          }
        }

        if (changedVals != null) {
          envCtxt.clearIntermediateResults(changedVals)
        }
      }

      if (tmpChngdContainersAndKeys != null) {
        val changedContainersAndKeys = if (tmpChngdContainersAndKeys.isInstanceOf[List[_]]) tmpChngdContainersAndKeys.asInstanceOf[List[_]] else if (tmpChngdContainersAndKeys.isInstanceOf[Array[_]]) tmpChngdContainersAndKeys.asInstanceOf[Array[_]].toList else null
        if (changedContainersAndKeys != null && changedContainersAndKeys.size > 0) {
          val txnid = values.getOrElse("txnid", "0").toString.trim.toLong // txnid is 0, if it is not passed
          changedContainersAndKeys.foreach(CK => {
            if (CK != null && CK.isInstanceOf[Map[_, _]]) {
              val contAndKeys = CK.asInstanceOf[Map[String, Any]]
              val contName = contAndKeys.getOrElse("C", "").toString.trim
              val tmpKeys = contAndKeys.getOrElse("K", null)
              if (contName.size > 0 && tmpKeys != null) {
                // Expecting List/Array of Keys
                var keys: List[Any] = null
                if (tmpKeys.isInstanceOf[List[_]]) {
                  try {
                    keys = tmpKeys.asInstanceOf[List[Any]]
                  } catch {
                    case e: Exception => {}
                  }
                } else if (tmpKeys.isInstanceOf[Array[_]]) {
                  try {
                    keys = tmpKeys.asInstanceOf[Array[Any]].toList
                  } catch {
                    case e: Exception => {}
                  }
                } else if (tmpKeys.isInstanceOf[Map[_, _]]) {
                  try {
                    keys = tmpKeys.asInstanceOf[Map[String, Any]].toList
                  } catch {
                    case e: Exception => {}
                  }
                } else if (tmpKeys.isInstanceOf[scala.collection.mutable.Map[_, _]]) {
                  try {
                    keys = tmpKeys.asInstanceOf[scala.collection.mutable.Map[String, Any]].toList
                  } catch {
                    case e: Exception => {}
                  }
                }

                if (keys != null && keys.size > 0) {
                  var loadableKeys = ArrayBuffer[Key]()
                  val ks = keys.map(k => {
                    var oneKey: Map[String, Any] = null
                    if (k.isInstanceOf[List[_]]) {
                      try {
                        oneKey = k.asInstanceOf[List[(String, Any)]].toMap
                      } catch {
                        case e: Exception => {}
                      }
                    } else if (k.isInstanceOf[Array[_]]) {
                      try {
                        oneKey = k.asInstanceOf[Array[(String, Any)]].toMap
                      } catch {
                        case e: Exception => {}
                      }
                    } else if (k.isInstanceOf[Map[_, _]]) {
                      try {
                        oneKey = k.asInstanceOf[Map[String, Any]]
                      } catch {
                        case e: Exception => {}
                      }
                    } else if (k.isInstanceOf[scala.collection.mutable.Map[_, _]]) {
                      try {
                        oneKey = k.asInstanceOf[scala.collection.mutable.Map[String, Any]].toMap
                      } catch {
                        case e: Exception => {}
                      }
                    }

                    if (oneKey != null) {
                      val bk = oneKey.getOrElse("bk", null)
                      if (bk != null) {
                        val tm = oneKey.getOrElse("tm", "0").toString().toLong
                        val tx = oneKey.getOrElse("tx", "0").toString().toLong
                        val rid = oneKey.getOrElse("rid", "0").toString().toInt
                        loadableKeys += Key(tm, bk.asInstanceOf[List[String]].toArray, tx, rid)
                      }
                    }
                  })

                  if (loadableKeys.size > 0) {
                    try {
                      logger.debug("Loading Keys => Txnid:%d, ContainerName:%s, Keys:%s".format(txnid, contName, loadableKeys.map(k => (k.timePartition, k.bucketKey.mkString("="), k.transactionId, k.rowId)).mkString(",")))
                      envCtxt.ReloadKeys(txnid, contName, loadableKeys.toList)
                    } catch {
                      case e: Exception => {
                        logger.error("Failed to reload keys for container:" + contName)
                      }
                      case t: Throwable => {
                        logger.error("Failed to reload keys for container:" + contName)
                      }
                    }
                  }
                }
              }
            } // else // not handling
          })
        }
      }

      // 
    } catch {
      case e: Exception => {
        LOG.error("Found invalid JSON: %s".format(receivedJsonStr))
      }
    }

    LOG.debug("ActionOnDataChngImpl => Exit. receivedJsonStr: " + receivedJsonStr)
  }

  private def ActionOnDataChange(receivedJsonStr: String): Unit = {
    ActionOnDataChngImpl(receivedJsonStr)
  }

  private def ParticipentsAdaptersStatus(eventType: String, eventPath: String, eventPathData: Array[Byte], childs: Array[(String, Array[Byte])]): Unit = {
    // LOG.debug("ParticipentsAdaptersStatus => Enter, eventType:%s, eventPath:%s ".format(eventType, eventPath))
    if (IsLeaderNode == false) { // Not Leader node
      // LOG.debug("ParticipentsAdaptersStatus => Exit, eventType:%s, eventPath:%s ".format(eventType, eventPath))
      return
    }

    if (IsLeaderNodeAndUpdatePartitionsFlagSet) {
      LOG.debug("Already got Re-distribution request. Ignoring any actions from ParticipentsAdaptersStatus")
      return
    }

    UpdatePartitionsNodeData(eventType, eventPath, eventPathData)
    // LOG.debug("ParticipentsAdaptersStatus => Exit, eventType:%s, eventPath:%s ".format(eventType, eventPath))
  }

  private def CheckForPartitionsChange: Unit = {
    if (inputAdapters != null) {
      var updFlag = false
      var i = 0
      try {
        while (!updFlag && i < inputAdapters.size) {
          val ia = inputAdapters(i)
          i += 1
          try {
            val uk = ia.GetAllPartitionUniqueRecordKey
            val name = ia.UniqueName
            val ukCnt = if (uk != null) uk.size else 0
            val prevParts = GetPartitionsToValidate(name)
            val prevCnt = if (prevParts != null) prevParts.size else 0
            if (prevCnt != ukCnt) {
              // Number of partitions does not match
              LOG.warn("Number of partitions changed from %d to %d for %s. Going to redistribute the work".format(prevCnt, ukCnt, ia.UniqueName))
              SetUpdatePartitionsFlag
              updFlag = true
            }
            if (ukCnt > 0 && !updFlag) {
              // Check the real content
              val serUKSet = uk.map(k => {
                k.Serialize
              }).toSet
              if ((serUKSet -- prevParts).isEmpty == false) {
                LOG.warn("Partitions changed from for %s. Going to redistribute the work".format(ia.UniqueName))
                // Partition keys does not match
                SetUpdatePartitionsFlag
                updFlag = true
              }
              if (ukCnt > 0 && !updFlag) {
                // Check the real content
                val serUKSet = uk.map(k => {
                  k.Serialize
                }).toSet
                if ((serUKSet -- prevParts).isEmpty == false) {
                  // Partition keys does not match
                  SetUpdatePartitionsFlag
                  updFlag = true
                }
              }
            }
          } catch {
            case fae: FatalAdapterException => {
              // Adapter could not get partition information and can't reconver.
              val causeStackTrace = StackTrace.ThrowableTraceString(fae.cause)
              LOG.error("Failed to get partitions from input adapter " + ia.UniqueName + ". We are not going to change work load for now. Cause: \n" + causeStackTrace)
            }
            case e: Exception => {
              // Adapter could not get partition information and can't reconver.
              val causeStackTrace = StackTrace.ThrowableTraceString(e)
              LOG.error("Failed to get partitions from input adapter " + ia.UniqueName + ". We are not going to change work load for now. Cause: \n" + causeStackTrace)
            }
            case e: Throwable => {
              // Adapter could not get partition information and can't reconver.
              val causeStackTrace = StackTrace.ThrowableTraceString(e)
              LOG.error("Failed to get partitions from input adapter " + ia.UniqueName + ". We are not going to change work load for now. Cause: \n" + causeStackTrace)
            }
          }
        }
      } catch {
        case e: Exception => {
          LOG.error("Failed to get Input Adapters partitions. Reason:%s Message:%s".format(e.getCause, e.getMessage))
        }
      }
    }
  }

  private def GetEndPartitionsValuesForValidateAdapters: Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)] = {
    val uniqPartKeysValues = ArrayBuffer[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)]()

    if (validateInputAdapters != null) {
      var lastAdapterException: Throwable = null
      try {
        validateInputAdapters.foreach(ia => {
          try {
            val uKeysVals = ia.getAllPartitionEndValues
            uniqPartKeysValues ++= uKeysVals
          } catch {

            case e: FatalAdapterException => {
              lastAdapterException = e // Adapter could not partition information and can't recover.
              // If validate adapter is not able to connect, just ignoring it for now
              val causeStackTrace = StackTrace.ThrowableTraceString(e.cause)
              LOG.error("Failed to get partition values for Validate Adapter " + ia.UniqueName + ". Message:" + e.getMessage + ", Reason:" + e.getCause + ". Internal Cause: \n" + causeStackTrace)
            }
            case e: Exception => {
              lastAdapterException = e // Adapter could not partition information and can't recover.
              // If validate adapter is not able to connect, just ignoring it for now
              val causeStackTrace = StackTrace.ThrowableTraceString(e)
              LOG.error("Failed to get partition values for Validate Adapter " + ia.UniqueName + ". Message:" + e.getMessage + ", Reason:" + e.getCause + ". Internal Cause: \n" + causeStackTrace)
            }
            case e: Throwable => {
              lastAdapterException = e // Adapter could not partition information and can't recover.
              // If validate adapter is not able to connect, just ignoring it for now
              val causeStackTrace = StackTrace.ThrowableTraceString(e)
              LOG.error("Failed to get partition values for Validate Adapter " + ia.UniqueName + ". Message:" + e.getMessage + ", Reason:" + e.getCause + ". Internal Cause: \n" + causeStackTrace)
            }
          }
        })
        // Weird, but we want to record an FatalAdapterException for each consumer and handle all other unexpected ones.
        if (lastAdapterException != null) throw lastAdapterException
      } catch {
        case e: Exception => {
          LOG.error("Failed to get Validate Input Adapters partitions. Reason:%s Message:%s".format(e.getCause, e.getMessage))
        }
      }
    }

    uniqPartKeysValues.toArray
  }

  def Init(nodeId1: String, zkConnectString1: String, engineLeaderZkNodePath1: String, engineDistributionZkNodePath1: String, adaptersStatusPath1: String, inputAdap: ArrayBuffer[InputAdapter], outputAdap: ArrayBuffer[OutputAdapter], statusAdap: ArrayBuffer[OutputAdapter], validateInputAdap: ArrayBuffer[InputAdapter], enviCxt: EnvContext, zkSessionTimeoutMs1: Int, zkConnectionTimeoutMs1: Int, dataChangeZkNodePath1: String): Unit = {
    nodeId = nodeId1.toLowerCase
    zkConnectString = zkConnectString1
    engineLeaderZkNodePath = engineLeaderZkNodePath1
    engineDistributionZkNodePath = engineDistributionZkNodePath1
    dataChangeZkNodePath = dataChangeZkNodePath1
    adaptersStatusPath = adaptersStatusPath1
    zkSessionTimeoutMs = zkSessionTimeoutMs1
    zkConnectionTimeoutMs = zkConnectionTimeoutMs1
    inputAdapters = inputAdap
    outputAdapters = outputAdap
    statusAdapters = statusAdap
    validateInputAdapters = validateInputAdap
    envCtxt = enviCxt

    if (zkConnectString != null && zkConnectString.isEmpty() == false && engineLeaderZkNodePath != null && engineLeaderZkNodePath.isEmpty() == false && engineDistributionZkNodePath != null && engineDistributionZkNodePath.isEmpty() == false && dataChangeZkNodePath != null && dataChangeZkNodePath.isEmpty() == false) {
      try {
        val adaptrStatusPathForNode = adaptersStatusPath + "/" + nodeId
        LOG.info("ZK Connecting. adaptrStatusPathForNode:%s, zkConnectString:%s, engineLeaderZkNodePath:%s, engineDistributionZkNodePath:%s, dataChangeZkNodePath:%s".format(adaptrStatusPathForNode, zkConnectString, engineLeaderZkNodePath, engineDistributionZkNodePath, dataChangeZkNodePath))
        CreateClient.CreateNodeIfNotExists(zkConnectString, engineDistributionZkNodePath) // Creating 
        CreateClient.CreateNodeIfNotExists(zkConnectString, adaptrStatusPathForNode) // Creating path for Adapter Statues
        CreateClient.CreateNodeIfNotExists(zkConnectString, dataChangeZkNodePath) // Creating 
        zkcForSetData = CreateClient.createSimple(zkConnectString, zkSessionTimeoutMs, zkConnectionTimeoutMs)
        zkAdapterStatusNodeListener = new ZooKeeperListener
        zkAdapterStatusNodeListener.CreatePathChildrenCacheListener(zkConnectString, adaptersStatusPath, false, ParticipentsAdaptersStatus, zkSessionTimeoutMs, zkConnectionTimeoutMs)
        zkEngineDistributionNodeListener = new ZooKeeperListener
        zkEngineDistributionNodeListener.CreateListener(zkConnectString, engineDistributionZkNodePath, ActionOnAdaptersDistribution, zkSessionTimeoutMs, zkConnectionTimeoutMs)
        zkDataChangeNodeListener = new ZooKeeperListener
        zkDataChangeNodeListener.CreateListener(zkConnectString, dataChangeZkNodePath, ActionOnDataChange, zkSessionTimeoutMs, zkConnectionTimeoutMs)
        try {
          Thread.sleep(500)
        } catch {
          case e: Exception => {
            // Not doing anything
            val stackTrace = StackTrace.ThrowableTraceString(e)
            LOG.debug("StackTrace:" + stackTrace)
          }
        }

        distributionExecutor.execute(new Runnable() {
          override def run() = {
            var updatePartsCntr = 0
            var getValidateAdapCntr = 0
            var wait4ValidateCheck = 0
            var validateUniqVals: Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)] = null

            var lastParticipentChngCntr: Long = 0
            var lastParticipentChngDistTime: Long = 0

            while (distributionExecutor.isShutdown == false) {
              try {
                Thread.sleep(1000) // Waiting for 1000 milli secs
              } catch {
                case e: Exception => {
                  val stackTrace = StackTrace.ThrowableTraceString(e)
                  LOG.debug("\nStackTrace:" + stackTrace)
                }
              }

              var execDefaultPath = true
              if (IsLeaderNode && GetUpdatePartitionsFlag && distributionExecutor.isShutdown == false) {
                val curParticipentChngCntr = KamanjaConfiguration.participentsChangedCntr
                if (lastParticipentChngCntr != curParticipentChngCntr) {
                  lastParticipentChngCntr = curParticipentChngCntr
                  val cs = GetClusterStatus
                  var mxTm = 0

                  // Make sure we check the number of nodes participating in the node start (get number of nodes from metadata manager and if all of them are in participents, no need to wait more than 4-5 secs, other wait more time)
                  val mdMgr = GetMdMgr
                  var allNodesUp = false

                  if (mdMgr == null) {
                    LOG.warn("Got Redistribution request and not able to get metadata manager. Not going to check whether all nodes came up or not in participents {%s}.".format(cs.participants.mkString(",")))
                  } else {
                    val nodes = mdMgr.NodesForCluster(KamanjaConfiguration.clusterId)
                    if (nodes == null) {
                      LOG.warn("Got Redistribution request and not able to get nodes from metadata manager for cluster %s. Not going to check whether all nodes came up or not in participents {%s}.".format(KamanjaConfiguration.clusterId, cs.participants.mkString(",")))
                    } else {
                      val participents = cs.participants.toSet
                      // Check for nodes in participents now
                      allNodesUp = true
                      var i = 0
                      while (i < nodes.size && allNodesUp) {
                        if (participents.contains(nodes(i).nodeId) == false)
                          allNodesUp = false
                        i += 1
                      }

                      if (allNodesUp) {
                        // Check for duplicates if we have any in participents
                        // Just do group by and do get duplicates if we have any. If we have duplicates just make allNodesUp as false, so it will wait long time and by that time the duplicate node may go down.
                        allNodesUp = (cs.participants.groupBy(x => x).mapValues(lst => lst.size).filter(kv => kv._2 > 1).size == 0)
                      }
                    }
                  }

                  if (allNodesUp == false) { // If all nodes are not up then wait for long time
                    mxTm = if (KamanjaConfiguration.zkSessionTimeoutMs > KamanjaConfiguration.zkConnectionTimeoutMs) KamanjaConfiguration.zkSessionTimeoutMs else KamanjaConfiguration.zkConnectionTimeoutMs
                    if (mxTm < 5000) // if the value is < 5secs, we are taking 5 secs
                      mxTm = 5000
                    LOG.warn("Got Redistribution request. Participents are {%s}. Looks like all nodes are not yet up. Waiting for %d milli seconds to see whether there are any more changes in participents".format(cs.participants.mkString(","), mxTm))
                    lastParticipentChngDistTime = System.currentTimeMillis + mxTm + 5000 // waiting another 5secs
                    execDefaultPath = false
                  } else { // if all nodes are up, no need to wait any more
                    LOG.warn("All Participents are {%s} up. Going to distribute the work now".format(cs.participants.mkString(",")))
                  }
                } else if (lastParticipentChngDistTime > System.currentTimeMillis) {
                  // Still waiting to distribute
                  execDefaultPath = false
                }
              }

              if (execDefaultPath && distributionExecutor.isShutdown == false) {
                lastParticipentChngCntr = 0
                if (GetUpdatePartitionsFlagAndReset) {
                  UpdatePartitionsIfNeededOnLeader
                  wait4ValidateCheck = 180 // When ever rebalancing it should be 180 secs
                  updatePartsCntr = 0
                  getValidateAdapCntr = 0
                } else if (IsLeaderNode) {
                  if (GetUpdatePartitionsFlag == false) {
                    // Get Partitions for every N secs and see whether any partitions changes from previous get
                    if (updatePartsCntr >= 30) { // for every 30 secs
                      CheckForPartitionsChange
                      updatePartsCntr = 0
                    } else {
                      updatePartsCntr += 1
                    }

                    if (wait4ValidateCheck > 0) {
                      // Get Partitions keys and values for every M secs
                      if (getValidateAdapCntr >= wait4ValidateCheck) { // for every waitForValidateCheck secs
                        // Persists the previous ones if we have any
                        if (validateUniqVals != null && validateUniqVals.size > 0) {
                          envCtxt.PersistValidateAdapterInformation(validateUniqVals.map(kv => (kv._1.Serialize, kv._2.Serialize)))
                        }
                        // Get the latest ones
                        validateUniqVals = GetEndPartitionsValuesForValidateAdapters
                        getValidateAdapCntr = 0
                        wait4ValidateCheck = 60 // Next time onwards it is 60 secs
                      } else {
                        getValidateAdapCntr += 1
                      }
                    } else {
                      getValidateAdapCntr = 0
                    }
                  }
                } else {
                  wait4ValidateCheck = 0 // Not leader node, don't check for it until we set it in redistribute
                  getValidateAdapCntr = 0
                }
              }
            }
          }
        })

        SetCanRedistribute(true)
        zkLeaderLatch = new ZkLeaderLatch(zkConnectString, engineLeaderZkNodePath, nodeId, EventChangeCallback, zkSessionTimeoutMs, zkConnectionTimeoutMs)
        zkLeaderLatch.SelectLeader
        /*
        // Set RE-DISTRIBUTED action in adaptersStatusPath + "/" + nodeId path
        val act = ("action" -> "re-distribute")
        val sendJson = compact(render(act))
        SetNewDataToZkc(adaptrStatusPathForNode, sendJson.getBytes("UTF8"))
        */
      } catch {
        case e: Exception => {
          LOG.error("Failed to initialize ZooKeeper Connection. Reason:%s Message:%s".format(e.getCause, e.getMessage))
          throw e
        }
      }
    } else {
      LOG.error("Not connected to elect Leader and not distributing data between nodes.")
    }
  }

  private def CloseSetDataZkc: Unit = {
    setDataLockObj.synchronized {
      if (zkcForSetData != null)
        zkcForSetData.close
      zkcForSetData = null
    }
  }

  def SetNewDataToZkc(zkNodePath: String, data: Array[Byte]): Unit = {
    setDataLockObj.synchronized {
      if (zkcForSetData != null)
        zkcForSetData.setData().forPath(zkNodePath, data)
    }
  }

  def GetDataFromZkc(zkNodePath: String): Array[Byte] = {
    setDataLockObj.synchronized {
      if (zkcForSetData != null)
        return zkcForSetData.getData().forPath(zkNodePath);
      else
        return Array[Byte]()
    }
  }

  def GetChildrenFromZkc(zkNodePath: String): List[String] = {
    setDataLockObj.synchronized {
      if (zkcForSetData != null)
        return zkcForSetData.getChildren().forPath(zkNodePath).toList
      else
        return List[String]()
    }
  }

  def GetChildrenDataFromZkc(zkNodePath: String): List[(String, Array[Byte])] = {
    setDataLockObj.synchronized {
      if (zkcForSetData != null) {
        val childs = zkcForSetData.getChildren().forPath(zkNodePath)
        return childs.map(child => {
          val path = zkNodePath + "/" + child
          val chldData = zkcForSetData.getData().forPath(path);
          (child, chldData)
        }).toList
      } else {
        return List[(String, Array[Byte])]()
      }
    }
  }

  def Shutdown: Unit = {
    distributionExecutor.shutdown
    if (zkLeaderLatch != null)
      zkLeaderLatch.Shutdown
    zkLeaderLatch = null
    if (zkEngineDistributionNodeListener != null)
      zkEngineDistributionNodeListener.Shutdown
    zkEngineDistributionNodeListener = null
    if (zkDataChangeNodeListener != null)
      zkDataChangeNodeListener.Shutdown
    zkDataChangeNodeListener = null
    if (zkAdapterStatusNodeListener != null)
      zkAdapterStatusNodeListener.Shutdown
    zkAdapterStatusNodeListener = null
    CloseSetDataZkc
  }
}


