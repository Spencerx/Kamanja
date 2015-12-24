
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

package com.ligadata.KamanjaBase

import scala.collection.immutable.Map
import com.ligadata.Utils.Utils
import com.ligadata.kamanja.metadata.{ MdMgr, ModelDef }
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import java.io.{ DataInputStream, DataOutputStream }
import com.ligadata.KvBase.{ TimeRange }
import com.ligadata.KvBase.{ Key, Value, TimeRange /* , KvBaseDefalts, KeyWithBucketIdAndPrimaryKey, KeyWithBucketIdAndPrimaryKeyCompHelper */ }
import com.ligadata.Utils.{ KamanjaLoaderInfo }

object MinVarType extends Enumeration {
  type MinVarType = Value
  val Unknown, Active, Predicted, Supplementary, FeatureExtraction = Value
  def StrToMinVarType(tsTypeStr: String): MinVarType = {
    tsTypeStr.toLowerCase.trim() match {
      case "active"            => Active
      case "predicted"         => Predicted
      case "supplementary"     => Supplementary
      case "featureextraction" => FeatureExtraction
      case _                   => Unknown
    }
  }
}

import MinVarType._

case class Result(val name: String, val result: Any)

object ModelsResults {
  def ValueString(v: Any): String = {
    if (v == null) {
      return "null"
    }
    if (v.isInstanceOf[Set[_]]) {
      return v.asInstanceOf[Set[_]].mkString(",")
    }
    if (v.isInstanceOf[List[_]]) {
      return v.asInstanceOf[List[_]].mkString(",")
    }
    if (v.isInstanceOf[Array[_]]) {
      return v.asInstanceOf[Array[_]].mkString(",")
    }
    v.toString
  }

  def Deserialize(modelResults: Array[SavedMdlResult], dis: DataInputStream, mdResolver: MdBaseResolveInfo, loader: java.lang.ClassLoader, savedDataVersion: String): Unit = {

  }

  def Serialize(dos: DataOutputStream): Array[SavedMdlResult] = {
    null
  }
}

class SavedMdlResult {
  var mdlName: String = ""
  var mdlVersion: String = ""
  var uniqKey: String = ""
  var uniqVal: String = ""
  var txnId: Long = 0
  var xformedMsgCntr: Int = 0 // Current message Index, In case if we have multiple Transformed messages for a given input message
  var totalXformedMsgs: Int = 0 // Total transformed messages, In case if we have multiple Transformed messages for a given input message
  var mdlRes: ModelResultBase = null

  def withMdlName(mdl_Name: String): SavedMdlResult = {
    mdlName = mdl_Name
    this
  }

  def withMdlVersion(mdl_Version: String): SavedMdlResult = {
    mdlVersion = mdl_Version
    this
  }

  def withUniqKey(uniq_Key: String): SavedMdlResult = {
    uniqKey = uniq_Key
    this
  }

  def withUniqVal(uniq_Val: String): SavedMdlResult = {
    uniqVal = uniq_Val
    this
  }

  def withTxnId(txn_Id: Long): SavedMdlResult = {
    txnId = txn_Id
    this
  }

  def withXformedMsgCntr(xfrmedMsgCntr: Int): SavedMdlResult = {
    xformedMsgCntr = xfrmedMsgCntr
    this
  }

  def withTotalXformedMsgs(totalXfrmedMsgs: Int): SavedMdlResult = {
    totalXformedMsgs = totalXfrmedMsgs
    this
  }

  def withMdlResult(mdl_Res: ModelResultBase): SavedMdlResult = {
    mdlRes = mdl_Res
    this
  }

  def toJson: org.json4s.JsonAST.JObject = {
    val output = if (mdlRes == null) List[org.json4s.JsonAST.JObject]() else mdlRes.toJson
    val json =
      ("ModelName" -> mdlName) ~
        ("ModelVersion" -> mdlVersion) ~
        ("uniqKey" -> uniqKey) ~
        ("uniqVal" -> uniqVal) ~
        ("xformedMsgCntr" -> xformedMsgCntr) ~
        ("totalXformedMsgs" -> totalXformedMsgs) ~
        ("output" -> output)
    return json
  }

  override def toString: String = {
    compact(render(toJson))
  }
}

trait ModelResultBase {
  def toJson: List[org.json4s.JsonAST.JObject]
  def toString: String // Returns JSON string
  def get(key: String): Any // Get the value for the given key, if exists, otherwise NULL
  def asKeyValuesMap: Map[String, Any] // Return all key & values as Map of KeyValue pairs
  def Deserialize(dis: DataInputStream): Unit // Serialize this object
  def Serialize(dos: DataOutputStream): Unit // Deserialize this object
}

// Keys are handled as case sensitive
class MappedModelResults extends ModelResultBase {
  val results = scala.collection.mutable.Map[String, Any]()

  def withResults(res: Array[Result]): MappedModelResults = {
    if (res != null) {
      res.foreach(r => {
        results(r.name) = r.result
      })

    }
    this
  }

  def withResults(res: Array[(String, Any)]): MappedModelResults = {
    if (res != null) {
      res.foreach(r => {
        results(r._1) = r._2
      })
    }
    this
  }

  def withResults(res: scala.collection.immutable.Map[String, Any]): MappedModelResults = {
    if (res != null) {
      res.foreach(r => {
        results(r._1) = r._2
      })
    }
    this
  }

  def withResult(res: Result): MappedModelResults = {
    if (res != null) {
      results(res.name) = res.result
    }
    this
  }

  def withResult(res: (String, Any)): MappedModelResults = {
    if (res != null) {
      results(res._1) = res._2
    }
    this
  }

  def withResult(key: String, value: Any): MappedModelResults = {
    if (key != null)
      results(key) = value
    this
  }

  override def toJson: List[org.json4s.JsonAST.JObject] = {
    val json =
      results.toList.map(r =>
        (("Name" -> r._1) ~
          ("Value" -> ModelsResults.ValueString(r._2))))
    return json
  }

  override def toString: String = {
    compact(render(toJson))
  }

  override def get(key: String): Any = {
    results.getOrElse(key, null)
  }

  override def asKeyValuesMap: Map[String, Any] = {
    results.toMap
  }

  override def Deserialize(dis: DataInputStream): Unit = {
    // BUGBUG:: Yet to implement
  }

  override def Serialize(dos: DataOutputStream): Unit = {
    // BUGBUG:: Yet to implement
  }
}

case class ContainerNameAndDatastoreInfo(containerName: String, dataDataStoreInfo: String)

trait EnvContext {
  // Metadata Ops
  var _mgr: MdMgr = _
  def setMdMgr(mgr: MdMgr): Unit
  def getPropertyValue(clusterId: String, key: String): String
  def SetClassLoader(cl: java.lang.ClassLoader): Unit
  def SetMetadataResolveInfo(mdres: MdBaseResolveInfo): Unit

  // Setting JarPaths
  def SetJarPaths(jarPaths: collection.immutable.Set[String]): Unit

  // Datastores
  def SetDefaultDatastore(dataDataStoreInfo: String): Unit
  def SetStatusInfoDatastore(statusDataStoreInfo: String): Unit

  // Registerd Messages/Containers
  def RegisterMessageOrContainers(containersInfo: Array[ContainerNameAndDatastoreInfo]): Unit

  // RDD Ops
  def getRecent(transId: Long, containerName: String, partKey: List[String], tmRange: TimeRange, f: MessageContainerBase => Boolean): Option[MessageContainerBase]
  def getRDD(transId: Long, containerName: String, partKey: List[String], tmRange: TimeRange, f: MessageContainerBase => Boolean): Array[MessageContainerBase]
  def saveOne(transId: Long, containerName: String, partKey: List[String], value: MessageContainerBase): Unit
  def saveRDD(transId: Long, containerName: String, values: Array[MessageContainerBase]): Unit

  // RDD Ops
  def Shutdown: Unit
  def getAllObjects(transId: Long, containerName: String): Array[MessageContainerBase]
  def getObject(transId: Long, containerName: String, partKey: List[String], primaryKey: List[String]): MessageContainerBase
  def getHistoryObjects(transId: Long, containerName: String, partKey: List[String], appendCurrentChanges: Boolean): Array[MessageContainerBase] // if appendCurrentChanges is true return output includes the in memory changes (new or mods) at the end otherwise it ignore them.
  def setObject(transId: Long, containerName: String, partKey: List[String], value: MessageContainerBase): Unit
  def contains(transId: Long, containerName: String, partKey: List[String], primaryKey: List[String]): Boolean
  def containsAny(transId: Long, containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): Boolean //partKeys.size should be same as primaryKeys.size  
  def containsAll(transId: Long, containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): Boolean //partKeys.size should be same as primaryKeys.size

  // Adapters Keys & values
  def setAdapterUniqueKeyValue(transId: Long, key: String, value: String, outputResults: List[(String, String, String)]): Unit
  def getAdapterUniqueKeyValue(transId: Long, key: String): (Long, String, List[(String, String, String)])
  /*
  def getAllIntermediateStatusInfo: Array[(String, (String, Int, Int))] // Get all Status information from intermediate table. No Transaction required here.
  def getIntermediateStatusInfo(keys: Array[String]): Array[(String, (String, Int, Int))] // Get Status information from intermediate table for given keys. No Transaction required here.
*/
  def setAdapterUniqKeyAndValues(keyAndValues: List[(String, String)]): Unit
  def getAllAdapterUniqKvDataInfo(keys: Array[String]): Array[(String, (Long, String, List[(String, String, String)]))] // Get Status information from Final table. No Transaction required here.

  //  def getAllIntermediateCommittingInfo: Array[(String, (Long, String, List[(String, String)]))] // Getting intermediate committing information. Once we commit we don't have this, because we remove after commit

  //  def getAllIntermediateCommittingInfo(keys: Array[String]): Array[(String, (Long, String, List[(String, String)]))] // Getting intermediate committing information.

  //  def removeCommittedKey(transId: Long, key: String): Unit
  //  def removeCommittedKeys(keys: Array[String]): Unit

  // Model Results Saving & retrieving. Don't return null, always return empty, if we don't find
  def saveModelsResult(transId: Long, key: List[String], value: scala.collection.mutable.Map[String, SavedMdlResult]): Unit
  def getModelsResult(transId: Long, key: List[String]): scala.collection.mutable.Map[String, SavedMdlResult]

  // Final Commit for the given transaction
  // outputResults has AdapterName, PartitionKey & Message
  def commitData(transId: Long, key: String, value: String, outputResults: List[(String, String, String)], forceCommit: Boolean): Unit
  def rollbackData(transId: Long): Unit

  // Save State Entries on local node & on Leader
  // def PersistLocalNodeStateEntries: Unit
  // def PersistRemainingStateEntriesOnLeader: Unit

  // Clear Intermediate results before Restart processing
  def clearIntermediateResults: Unit

  // Clear Intermediate results After updating them on different node or different component (like KVInit), etc
  def clearIntermediateResults(unloadMsgsContainers: Array[String]): Unit

  // Changed Data & Reloading data are Time in MS, Bucket Key & TransactionId
  def getChangedData(tempTransId: Long, includeMessages: Boolean, includeContainers: Boolean): scala.collection.immutable.Map[String, List[Key]]
  def ReloadKeys(tempTransId: Long, containerName: String, keys: List[Key]): Unit

  // Set Reload Flag
  //  def setReloadFlag(transId: Long, containerName: String): Unit

  def PersistValidateAdapterInformation(validateUniqVals: Array[(String, String)]): Unit
  def GetValidateAdapterInformation: Array[(String, String)]

  /**
   *  Answer an empty instance of the message or container with the supplied fully qualified class name.  If the name is
   *  invalid, null is returned.
   *  @param fqclassname : a full package qualifed class name
   *  @return a MesssageContainerBase of that ilk
   */
  def NewMessageOrContainer(fqclassname: String): MessageContainerBase

  // Just get the cached container key and see what are the containers we need to cache
  def CacheContainers(clusterId: String): Unit

  def EnableEachTransactionCommit: Boolean
}

// ModelInstance will be created from ModelInstanceFactory by demand.
//	If ModelInstanceFactory:isModelInstanceReusable returns true, engine requests one ModelInstance per partition.
//	If ModelInstanceFactory:isModelInstanceReusable returns false, engine requests one ModelInstance per input message related to this model (message is validated with ModelInstanceFactory.isValidMessage).
abstract class ModelInstance(val factory: ModelInstanceFactory) {
  // Getting NodeContext from ModelInstanceFactory
  final def getNodeContext() = factory.getNodeContext()

  // Getting EnvContext from ModelInstanceFactory
  final def getEnvContext() = factory.getEnvContext()

  // Getting ModelName from ModelInstanceFactory
  final def getModelName() = factory.getModelName()

  // Getting Model Version from ModelInstanceFactory
  final def getVersion() = factory.getVersion()

  // Getting ModelInstanceFactory, which is passed in constructor
  final def getModelInstanceFactory() = factory

  // This calls when the instance got created. And only calls once per instance.
  //	Intput:
  //		instanceMetadata: Metadata related to this instance (partition information)
  def init(instanceMetadata: String): Unit = {} // Local Instance level initialization

  // This calls when the instance is shutting down. There is no guarantee
  def shutdown(): Unit = {} // Shutting down this factory. 

  // This calls for each input message related to this model (message is validated with ModelInstanceFactory.isValidMessage)
  //	Intput:
  //		txnCtxt: Transaction context related to this execution
  //		outputDefault: If this is true, engine is expecting output always.
  //	Output:
  //		Derived class of ModelResultBase is the return results expected. null if no results.
  def execute(txnCtxt: TransactionContext, outputDefault: Boolean): ModelResultBase
}

// ModelInstanceFactory will be created from FactoryOfModelInstanceFactory when metadata got resolved (while engine is starting and when metadata adding while running the engine).
abstract class ModelInstanceFactory(val modelDef: ModelDef, val nodeContext: NodeContext) {
  // Getting NodeContext, which is passed in constructor
  final def getNodeContext() = nodeContext

  // Getting EnvContext from nodeContext, if available
  final def getEnvContext() = if (nodeContext != null) nodeContext.getEnvCtxt else null

  // Getting ModelDef, which is passed in constructor
  final def getModelDef() = modelDef

  // This calls when the instance got created. And only calls once per instance.
  // Common initialization for all Model Instances. This gets called once per node during the metadata load or corresponding model def change.
  //	Intput:
  //		txnCtxt: Transaction context to do get operations on this transactionid. But this transaction will be rolledback once the initialization is done.
  def init(txnContext: TransactionContext): Unit = {}

  // This calls when the factory is shutting down. There is no guarantee.
  def shutdown(): Unit = {} // Shutting down this factory. 

  // Getting ModelName.
  def getModelName(): String // Model Name

  // Getting Model Version
  def getVersion(): String // Model Version

  // Checking whether the message is valid to execute this model instance or not.
  def isValidMessage(msg: MessageContainerBase): Boolean

  // Creating new model instance related to this ModelInstanceFactory.
  def createModelInstance(): ModelInstance

  // Creating ModelResultBase associated this model/modelfactory.
  def createResultObject(): ModelResultBase

  // Is the ModelInstance created by this ModelInstanceFactory is reusable?
  def isModelInstanceReusable(): Boolean = false
}

trait FactoryOfModelInstanceFactory {
  def getModelInstanceFactory(modelDef: ModelDef, nodeContext: NodeContext, loaderInfo: KamanjaLoaderInfo, jarPaths: collection.immutable.Set[String]): ModelInstanceFactory
  // Input:
  //  modelDefStr is Model Definition String
  //  inpMsgName is Input Message Name
  //  outMsgName is output Message Name
  // Output: ModelDef
  def prepareModel(nodeContext: NodeContext, modelDefStr: String, inpMsgName: String, outMsgName: String, loaderInfo: KamanjaLoaderInfo, jarPaths: collection.immutable.Set[String]): ModelDef
}

class TransactionContext(val transId: Long, val nodeCtxt: NodeContext, val msgData: Array[Byte], val partitionKey: String) {
  private var msg: MessageContainerBase = _
  def getInputMessageData(): Array[Byte] = msgData
  def getPartitionKey(): String = partitionKey
  def getMessage(): MessageContainerBase = msg
  def setMessage(m: MessageContainerBase): Unit = { msg = m }
  def getTransactionId() = transId
  def getNodeCtxt() = nodeCtxt
  private var valuesMap = new java.util.HashMap[String, Any]()
  def getPropertyValue(clusterId: String, key: String): String = { if (nodeCtxt != null) nodeCtxt.getPropertyValue(clusterId, key) else "" }
  def putValue(key: String, value: Any): Unit = { valuesMap.put(key, value) }
  def getValue(key: String): Any = { valuesMap.get(key) }
}

// Node level context
class NodeContext(val gCtx: EnvContext) {
  def getEnvCtxt() = gCtx
  private var valuesMap = new java.util.HashMap[String, Any]()
  def getPropertyValue(clusterId: String, key: String): String = { if (gCtx != null) gCtx.getPropertyValue(clusterId, key) else "" }
  def putValue(key: String, value: Any): Unit = { valuesMap.put(key, value) }
  def getValue(key: String): Any = { valuesMap.get(key) }
}

