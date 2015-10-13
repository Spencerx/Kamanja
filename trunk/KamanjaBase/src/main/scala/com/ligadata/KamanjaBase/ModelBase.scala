
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
import com.ligadata.kamanja.metadata.MdMgr
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import java.io.{ DataInputStream, DataOutputStream }
import com.ligadata.KvBase.{ TimeRange }
import com.ligadata.KvBase.{ Key, Value, TimeRange/* , KvBaseDefalts, KeyWithBucketIdAndPrimaryKey, KeyWithBucketIdAndPrimaryKeyCompHelper */ }

object MinVarType extends Enumeration {
  type MinVarType = Value
  val Unknown, Active, Predicted, Supplementary, FeatureExtraction = Value
  def StrToMinVarType(tsTypeStr: String): MinVarType = {
    tsTypeStr.toLowerCase.trim() match {
      case "active" => Active
      case "predicted" => Predicted
      case "supplementary" => Supplementary
      case "featureextraction" => FeatureExtraction
      case _ => Unknown
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
  def commitData(transId: Long, key: String, value: String, outputResults: List[(String, String, String)]): Unit

  // Save State Entries on local node & on Leader
  // def PersistLocalNodeStateEntries: Unit
  // def PersistRemainingStateEntriesOnLeader: Unit

  // Clear Intermediate results before Restart processing
  def clearIntermediateResults: Unit

  // Clear Intermediate results After updating them on different node or different component (like KVInit), etc
  def clearIntermediateResults(unloadMsgsContainers: Array[String]): Unit

  // Changed Data & Reloading data are Time in MS, Bucket Key & TransactionId
  def getChangedData(tempTransId: Long, includeMessages: Boolean, includeContainers: Boolean): scala.collection.immutable.Map[String, Array[Key]]
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
}

abstract class ModelBase(val modelContext: ModelContext, val factory: ModelBaseObj) {
  final def EnvContext() = if (modelContext != null && modelContext.txnContext != null) modelContext.txnContext.gCtx else null // gCtx
  final def ModelName() = factory.ModelName() // Model Name
  final def Version() = factory.Version() // Model Version
  final def TenantId() = if (modelContext != null && modelContext.txnContext != null) modelContext.txnContext.tenantId else null // Tenant Id
  final def TransId() = if (modelContext != null && modelContext.txnContext != null) modelContext.txnContext.transId else null // transId

  def execute(outputDefault: Boolean): ModelResultBase // if outputDefault is true we will output the default value if nothing matches, otherwise null 
}

trait ModelBaseObj {
  def IsValidMessage(msg: MessageContainerBase): Boolean // Check to fire the model
  def CreateNewModel(mdlCtxt: ModelContext): ModelBase // Creating same type of object with given values 
  def ModelName(): String // Model Name
  def Version(): String // Model Version
  def CreateResultObject(): ModelResultBase // ResultClass associated the model. Mainly used for Returning results as well as Deserialization
}

class MdlInfo(val mdl: ModelBaseObj, val jarPath: String, val dependencyJarNames: Array[String], val tenantId: String) {
}

// partitionKey is the one used for this message
class ModelContext(val txnContext: TransactionContext, val msg: MessageContainerBase, val msgData: Array[Byte], val partitionKey: String) {
  def InputMessageData: Array[Byte] = msgData
  def Message: MessageContainerBase = msg
  def TransactionContext: TransactionContext = txnContext
  def PartitionKey: String = partitionKey
  def getPropertyValue(clusterId: String, key: String): String = (txnContext.getPropertyValue(clusterId, key))
}

class TransactionContext(val transId: Long, val gCtx: EnvContext, val tenantId: String) {
  def getPropertyValue(clusterId: String, key: String): String = { gCtx.getPropertyValue(clusterId, key) }
}

