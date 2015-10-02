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

package com.ligadata.SimpleEnvContextImpl

import scala.collection.immutable.Map
import scala.collection.mutable._
import scala.util.control.Breaks._
import scala.reflect.runtime.{ universe => ru }
import org.apache.log4j.Logger
import com.ligadata.StorageBase.{ DataStore, Transaction, IStorage, Key, Value, StorageAdapterObj }
import com.ligadata.KamanjaBase._
// import com.ligadata.KamanjaBase.{ EnvContext, MessageContainerBase }
import com.ligadata.kamanja.metadata._
import com.ligadata.Exceptions._
import java.net.URLClassLoader
import com.ligadata.Serialize._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.ligadata.Exceptions.StackTrace
import com.ligadata.KamanjaData.{ KamanjaData }
import com.ligadata.keyvaluestore.KeyValueManager

case class KamanjaDataKey(T: String, K: List[String], D: List[Int], V: Int)
case class InMemoryKeyData(K: List[String])

trait LogTrait {
  val loggerName = this.getClass.getName()
  val logger = Logger.getLogger(loggerName)
}

// case class AdapterUniqueValueDes(T: Long, V: String, Qs: Option[List[String]], Ks: Option[List[String]], Res: Option[List[String]]) // TransactionId, Value, Queues & Result Strings. Queues and Result Strings should be same size.  

case class AdapterUniqueValueDes(T: Long, V: String, Out: Option[List[List[String]]]) // TransactionId, Value, Queues & Result Strings. Adapter Name, Key and Result Strings  

/**
 *  The SimpleEnvContextImpl supports kv stores that are based upon MapDb hash tables.
 */
object SimpleEnvContextImpl extends EnvContext with LogTrait {

  override def setMdMgr(inMgr: MdMgr): Unit = { _mgr = inMgr }

  override def NewMessageOrContainer(fqclassname: String): MessageContainerBase = {
    try {
      Class.forName(fqclassname)
    } catch {
      case e: Exception => {
        logger.error("Failed to load Message/Container class %s with Reason:%s Message:%s".format(fqclassname, e.getCause, e.getMessage))
        throw e // Rethrow
      }
    }
    val msgOrContainer: MessageContainerBase = Class.forName(fqclassname).newInstance().asInstanceOf[MessageContainerBase]
    msgOrContainer
  }

  class TxnCtxtKey {
    var containerName: String = _
    var key: String = _
  }

  class MsgContainerInfo {
    var current_msg_cont_data: scala.collection.mutable.ArrayBuffer[MessageContainerBase] = scala.collection.mutable.ArrayBuffer[MessageContainerBase]()
    var data: scala.collection.mutable.Map[String, (Boolean, KamanjaData)] = scala.collection.mutable.Map[String, (Boolean, KamanjaData)]()
    var containerType: BaseTypeDef = null
    var loadedAll: Boolean = false
    var reload: Boolean = false
    var isContainer: Boolean = false
    var objFullName: String = ""
  }

  object TxnContextCommonFunctions {
    def getRecentFromKamanjaData(kamanjaData: KamanjaData, tmRange: TimeRange, f: MessageContainerBase => Boolean): (MessageContainerBase, Boolean) = {
      // BUGBUG:: tmRange is not yet handled
      if (kamanjaData != null) {
        /*
            val data = kamanjaData.GetAllData
            var validRetVal: MessageContainerBase = null
            var maxTxnId = 0
            if (f != null) {
              
            } else {
              
            }
            
            data.foreach(v => {})
*/

        if (f != null) {
          val filterddata = kamanjaData.GetAllData.filter(v => f(v))
          if (filterddata.size > 0)
            return (filterddata(filterddata.size - 1), true)
        } else {
          val data = kamanjaData.GetAllData
          if (data.size > 0)
            return (data(data.size - 1), true)
        }
      }
      (null, false)
    }

    def getRddDataFromKamanjaData(kamanjaData: KamanjaData, tmRange: TimeRange, f: MessageContainerBase => Boolean): Array[MessageContainerBase] = {
      // BUGBUG:: tmRange is not yet handled
      if (kamanjaData != null) {
        if (f != null) {
          return kamanjaData.GetAllData.filter(v => f(v))
        } else {
          return kamanjaData.GetAllData
        }
      }
      Array[MessageContainerBase]()
    }

    def getRecent(container: MsgContainerInfo, partKey: List[String], tmRange: TimeRange, f: MessageContainerBase => Boolean): (MessageContainerBase, Boolean) = {
      //BUGBUG:: tmRange is not yet handled
      //BUGBUG:: Taking last record. But that may not be correct. Need to take max txnid one. But the issue is, if we are getting same data from multiple partitions, the txnids may be completely different.
      if (container != null) {
        if (TxnContextCommonFunctions.IsEmptyKey(partKey) == false) {
          val kamanjaData = container.data.getOrElse(InMemoryKeyDataInJson(partKey), null)
          if (kamanjaData != null)
            return getRecentFromKamanjaData(kamanjaData._2, tmRange, f)
        } else {
          val dataAsArr = container.data.toArray
          var idx = dataAsArr.size - 1
          while (idx >= 0) {
            val (v, foundPartKey) = getRecentFromKamanjaData(dataAsArr(idx)._2._2, tmRange, f)
            if (foundPartKey)
              return (v, foundPartKey)
            idx = idx - 1
          }
        }
      }
      (null, false)
    }

    def IsEmptyKey(key: List[String]): Boolean = {
      (key == null || key.size == 0)
    }

    def IsSameKey(key1: List[String], key2: List[String]): Boolean = {
      if (key1.size != key2.size)
        return false

      for (i <- 0 until key1.size) {
        if (key1(i).compareTo(key2(i)) != 0)
          return false
      }

      return true
    }

    def IsKeyExists(keys: Array[List[String]], searchKey: List[String]): Boolean = {
      keys.foreach(k => {
        if (IsSameKey(k, searchKey))
          return true
      })
      return false
    }

    def getRddData(container: MsgContainerInfo, partKey: List[String], tmRange: TimeRange, f: MessageContainerBase => Boolean, alreadyFoundPartKeys: Array[List[String]]): (Array[MessageContainerBase], Array[List[String]]) = {
      val retResult = ArrayBuffer[MessageContainerBase]()
      var foundPartKeys = ArrayBuffer[List[String]]()
      if (container != null) {
        if (TxnContextCommonFunctions.IsEmptyKey(partKey) == false) {
          val kamanjaData = container.data.getOrElse(InMemoryKeyDataInJson(partKey), null)
          if (kamanjaData != null && IsKeyExists(alreadyFoundPartKeys, kamanjaData._2.GetKey.toList) == false) {
            retResult ++= getRddDataFromKamanjaData(kamanjaData._2, tmRange, f)
            foundPartKeys += partKey
          }
        } else {
          container.data.foreach(kv => {
            val k = kv._2._2.GetKey.toList
            if (IsKeyExists(alreadyFoundPartKeys, k) == false) {
              retResult ++= getRddDataFromKamanjaData(kv._2._2, tmRange, f)
              foundPartKeys += k.toList
            }
          })
        }
      }
      (retResult.toArray, foundPartKeys.toArray)
    }
  }

  class TransactionContext(var txnId: Long) {
    private[this] val _messagesOrContainers = scala.collection.mutable.Map[String, MsgContainerInfo]()
    private[this] val _adapterUniqKeyValData = scala.collection.mutable.Map[String, (Long, String, List[(String, String, String)])]()
    private[this] val _modelsResult = scala.collection.mutable.Map[String, scala.collection.mutable.Map[String, SavedMdlResult]]()
    /*
    private[this] val _statusStrings = new ArrayBuffer[String]()
*/

    def getMsgContainer(containerName: String, addIfMissing: Boolean): MsgContainerInfo = {
      var fnd = _messagesOrContainers.getOrElse(containerName.toLowerCase, null)
      if (fnd == null && addIfMissing) {
        fnd = new MsgContainerInfo
        _messagesOrContainers(containerName) = fnd
      }
      fnd
    }

    def setFetchedObj(containerName: String, partKeyStr: String, kamanjaData: KamanjaData): Unit = {
      val container = getMsgContainer(containerName.toLowerCase, true)
      if (container != null) {
        container.data(partKeyStr) = (false, kamanjaData)
      }
    }

    def getAllObjects(containerName: String): Array[MessageContainerBase] = {
      val container = getMsgContainer(containerName.toLowerCase, false)
      val arrList = ArrayBuffer[MessageContainerBase]()
      if (container != null) {
        container.data.foreach(kv => {
          arrList ++= kv._2._2.GetAllData
        })
      }
      arrList.toArray
    }

    def getObject(containerName: String, partKey: List[String], primaryKey: List[String]): (MessageContainerBase, Boolean) = {
      val container = getMsgContainer(containerName.toLowerCase, false)
      if (container != null && TxnContextCommonFunctions.IsEmptyKey(partKey) == false && TxnContextCommonFunctions.IsEmptyKey(primaryKey) == false) {
        val kamanjaData = container.data.getOrElse(InMemoryKeyDataInJson(partKey), null)
        if (kamanjaData != null) {
          // Search for primary key match
          return (kamanjaData._2.GetMessageContainerBase(primaryKey.toArray, false), true)
        }
      }
      (null, false)
    }

    def getObjects(containerName: String, partKey: List[String], appendCurrentChanges: Boolean): (Array[MessageContainerBase], Boolean) = {
      val container = getMsgContainer(containerName.toLowerCase, false)
      if (container != null) {
        val kamanjaData = container.data.getOrElse(InMemoryKeyDataInJson(partKey), null)
        if (kamanjaData != null) {
          if (container.current_msg_cont_data.size > 0) {
            val allData = ArrayBuffer[MessageContainerBase]()
            if (appendCurrentChanges) {
              allData ++= kamanjaData._2.GetAllData
              allData --= container.current_msg_cont_data
              allData ++= container.current_msg_cont_data // Just to get the current messages to end
            } else {
              allData ++= kamanjaData._2.GetAllData
              allData --= container.current_msg_cont_data
            }
            return (allData.toArray, true)
          } else {
            return (kamanjaData._2.GetAllData, true)
          }
        }
      }
      (Array[MessageContainerBase](), false)
    }

    def containsAny(containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): (Boolean, Array[List[String]]) = {
      val container = getMsgContainer(containerName.toLowerCase, false)
      if (container != null) {
        val notFndPartkeys = ArrayBuffer[List[String]]()
        for (i <- 0 until partKeys.size) {
          if (TxnContextCommonFunctions.IsEmptyKey(partKeys(i)) == false) {
            val kamanjaData = container.data.getOrElse(InMemoryKeyDataInJson(partKeys(i)), null)
            if (kamanjaData != null) {
              if (TxnContextCommonFunctions.IsEmptyKey(primaryKeys(i)) == false) {
                // Search for primary key match
                val fnd = kamanjaData._2.GetMessageContainerBase(primaryKeys(i).toArray, false)
                if (fnd != null)
                  return (true, Array[List[String]]())
              }
            } else {
              notFndPartkeys += partKeys(i)
            }
          }
        }
        return (false, notFndPartkeys.toArray)
      }
      (false, primaryKeys)
    }

    def containsKeys(containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): (Array[List[String]], Array[List[String]], Array[List[String]], Array[List[String]]) = {
      val container = getMsgContainer(containerName.toLowerCase, false)
      val matchedPartKeys = ArrayBuffer[List[String]]()
      val unmatchedPartKeys = ArrayBuffer[List[String]]()
      val matchedPrimaryKeys = ArrayBuffer[List[String]]()
      val unmatchedPrimaryKeys = ArrayBuffer[List[String]]()
      if (container != null) {
        for (i <- 0 until partKeys.size) {
          val kamanjaData = container.data.getOrElse(InMemoryKeyDataInJson(partKeys(i)), null)
          if (kamanjaData != null) {
            // Search for primary key match
            val fnd = kamanjaData._2.GetMessageContainerBase(primaryKeys(i).toArray, false)
            if (fnd != null) {
              matchedPartKeys += partKeys(i)
              matchedPrimaryKeys += primaryKeys(i)
            } else {
              unmatchedPartKeys += partKeys(i)
              unmatchedPrimaryKeys += primaryKeys(i)
            }
          } else {
            unmatchedPartKeys += partKeys(i)
            unmatchedPrimaryKeys += primaryKeys(i)
          }
        }
      }
      (matchedPartKeys.toArray, unmatchedPartKeys.toArray, matchedPrimaryKeys.toArray, unmatchedPrimaryKeys.toArray)
    }

    def setObject(containerName: String, partKey: List[String], value: MessageContainerBase): Unit = {
      if (TxnContextCommonFunctions.IsEmptyKey(partKey))
        return
      val container = getMsgContainer(containerName.toLowerCase, true)
      if (container != null) {
        value.TransactionId(txnId) // Setting the current transactionid
        val partKeyStr = InMemoryKeyDataInJson(partKey)
        val fnd = container.data.getOrElse(partKeyStr, null)
        if (fnd != null) {
          fnd._2.AddMessageContainerBase(value, true, true)
          if (fnd._1 == false) {
            container.data(partKeyStr) = (true, fnd._2)
          }
        } else {
          val ffData = new KamanjaData
          ffData.SetKey(partKey.toArray)
          ffData.SetTypeName(containerName)
          ffData.AddMessageContainerBase(value, true, true)
          container.data(partKeyStr) = (true, ffData)
        }
        container.current_msg_cont_data -= value
        container.current_msg_cont_data += value // to get the value to end
      }
    }

    // Adapters Keys & values
    def setAdapterUniqueKeyValue(transId: Long, key: String, value: String, outputResults: List[(String, String, String)]): Unit = {
      _adapterUniqKeyValData(key) = (transId, value, outputResults)
    }

    def getAdapterUniqueKeyValue(key: String): (Long, String, List[(String, String, String)]) = {
      _adapterUniqKeyValData.getOrElse(key, null)
    }

    // Model Results Saving & retrieving. Don't return null, always return empty, if we don't find
    def saveModelsResult(key: List[String], value: scala.collection.mutable.Map[String, SavedMdlResult]): Unit = {
      _modelsResult(InMemoryKeyDataInJson(key)) = value
    }

    def getModelsResult(key: List[String]): scala.collection.mutable.Map[String, SavedMdlResult] = {
      val keystr = InMemoryKeyDataInJson(key)
      _modelsResult.getOrElse(keystr, null)
    }

    def setReloadFlag(containerName: String): Unit = {
      val container = getMsgContainer(containerName.toLowerCase, false)
      if (container != null)
        container.reload = true
    }

    /*
    def saveStatus(status: String): Unit = {
      _statusStrings += status
    }
*/

    def getAllMessagesAndContainers = _messagesOrContainers.toMap

    def getAllAdapterUniqKeyValData = _adapterUniqKeyValData.toMap

    def getAllModelsResult = _modelsResult.toMap

    /*
    def getAllStatusStrings = _statusStrings
*/

    def getRecent(containerName: String, partKey: List[String], tmRange: TimeRange, f: MessageContainerBase => Boolean): (MessageContainerBase, Boolean) = {
      val (v, foundPartKey) = TxnContextCommonFunctions.getRecent(getMsgContainer(containerName.toLowerCase, false), partKey, tmRange, f)
      (v, foundPartKey)
    }

    def getRddData(containerName: String, partKey: List[String], tmRange: TimeRange, f: MessageContainerBase => Boolean, alreadyFoundPartKeys: Array[List[String]]): (Array[MessageContainerBase], Array[List[String]]) = {
      return TxnContextCommonFunctions.getRddData(getMsgContainer(containerName.toLowerCase, false), partKey, tmRange, f, alreadyFoundPartKeys)
    }
  }

  private[this] val _buckets = 257 // Prime number

  private[this] val _locks = new Array[Object](_buckets)

  private[this] val _messagesOrContainers = scala.collection.mutable.Map[String, MsgContainerInfo]()
  private[this] val _txnContexts = new Array[scala.collection.mutable.Map[Long, TransactionContext]](_buckets)
  private[this] val _adapterUniqKeyValData = scala.collection.mutable.Map[String, (Long, String, List[(String, String, String)])]()
  private[this] val _modelsResult = scala.collection.mutable.Map[String, scala.collection.mutable.Map[String, SavedMdlResult]]()

  private[this] var _serInfoBufBytes = 32

  private[this] var _kryoSer: com.ligadata.Serialize.Serializer = null
  private[this] var _classLoader: java.lang.ClassLoader = null
  private[this] var _allDataDataStore: DataStore = null
  private[this] var _committingPartitionsDataStore: DataStore = null
  private[this] var _checkPointAdapInfoDataStore: DataStore = null
  private[this] var _mdres: MdBaseResolveInfo = null
  private[this] var _jarPaths: collection.immutable.Set[String] = null // Jar paths where we can resolve all jars (including dependency jars).

  for (i <- 0 until _buckets) {
    _txnContexts(i) = scala.collection.mutable.Map[Long, TransactionContext]()
    _locks(i) = new Object()
  }

  private[this] def lockIdx(transId: Long): Int = {
    return (transId % _buckets).toInt
  }

  private[this] def getTransactionContext(transId: Long, addIfMissing: Boolean): TransactionContext = {
    _locks(lockIdx(transId)).synchronized {
      var txnCtxt = _txnContexts(lockIdx(transId)).getOrElse(transId, null)
      if (txnCtxt == null && addIfMissing) {
        txnCtxt = new TransactionContext(transId)
        _txnContexts(lockIdx(transId))(transId) = txnCtxt
      }
      return txnCtxt
    }
  }

  private[this] def removeTransactionContext(transId: Long): Unit = {
    _locks(lockIdx(transId)).synchronized {
      _txnContexts(lockIdx(transId)) -= transId
    }
  }

  private[this] def InMemoryKeyDataInJson(keyData: List[String]): String = {
    val json = ("K" -> keyData)
    return compact(render(json))
  }

  private[this] def KeyFromInMemoryJson(keyData: String): List[String] = {
    implicit val jsonFormats: Formats = DefaultFormats
    val parsed_key = parse(keyData).extract[InMemoryKeyData]
    return parsed_key.K
  }

  /**
   * For the current container, load the values for each key, coercing it to the appropriate MessageContainerBase, and storing
   * each in the supplied map.
   *
   * @param containerType - a ContainerDef that describes the current container.  Its typeName is used to create an instance
   *                      of the MessageContainerBase derivative.
   * @param keys : the keys to use on the dstore to extract a given element.
   * @param dstore : the mapdb handle
   * @param map : the map to be updated with key/MessageContainerBase pairs.
   */
  private def loadMap(keys: Array[KamanjaDataKey], msgOrCont: MsgContainerInfo): Unit = {
    var objs: Array[KamanjaData] = new Array[KamanjaData](1)
    var notFoundKeys = 0
    val buildOne = (tupleBytes: Value) => {
      buildObject(tupleBytes, objs, msgOrCont.containerType)
    }
    keys.foreach(key => {
      try {
        val StartDateRange = if (key.D.size == 2) key.D(0) else 0
        val EndDateRange = if (key.D.size == 2) key.D(1) else 0
        _allDataDataStore.get(makeKey(KamanjaData.PrepareKey(key.T, key.K, StartDateRange, EndDateRange)), buildOne)
        msgOrCont.synchronized {
          msgOrCont.data(InMemoryKeyDataInJson(key.K)) = (false, objs(0))
        }
      } catch {
        case e: ClassNotFoundException => {

          logger.error(s"Not found key:${key.K.mkString(",")}. Reason:${e.getCause}, Message:${e.getMessage}")
          notFoundKeys += 1
        }
        case e: KeyNotFoundException => {

          logger.error(s"Not found key:${key.K.mkString(",")}. Reason:${e.getCause}, Message:${e.getMessage}")
          notFoundKeys += 1
        }
        case e: Exception => {

          logger.error(s"Not found key:${key.K.mkString(",")}. Reason:${e.getCause}, Message:${e.getMessage}")
          notFoundKeys += 1
        }
        case ooh: Throwable => {

          logger.error(s"Not found key:${key.K.mkString(",")}. Reason:${ooh.getCause}, Message:${ooh.getMessage}")
          throw ooh
        }
      }
    })

    if (notFoundKeys > 0) {
      logger.error("Not found some keys to load")
      throw new Exception("Not found some keys to load")
    }

    logger.info("Loaded %d objects for %s".format(msgOrCont.data.size, msgOrCont.objFullName))
  }

  private def getSerializeInfo(tupleBytes: Value): String = {
    if (tupleBytes.size < _serInfoBufBytes) return ""
    val serInfoBytes = new Array[Byte](_serInfoBufBytes)
    tupleBytes.copyToArray(serInfoBytes, 0, _serInfoBufBytes)
    return (new String(serInfoBytes)).trim
  }

  private def getValueInfo(tupleBytes: Value): Array[Byte] = {
    if (tupleBytes.size < _serInfoBufBytes) return null
    val valInfoBytes = new Array[Byte](tupleBytes.size - _serInfoBufBytes)
    Array.copy(tupleBytes.toArray, _serInfoBufBytes, valInfoBytes, 0, tupleBytes.size - _serInfoBufBytes)
    valInfoBytes
  }

  private def buildObject(tupleBytes: Value, objs: Array[KamanjaData], containerType: BaseTypeDef): Unit = {
    // Get first _serInfoBufBytes bytes
    if (tupleBytes.size < _serInfoBufBytes) {
      val errMsg = s"Invalid input. This has only ${tupleBytes.size} bytes data. But we are expecting serializer buffer bytes as of size ${_serInfoBufBytes}"
      logger.error(errMsg)
      throw new Exception(errMsg)
    }

    val serInfo = getSerializeInfo(tupleBytes)

    serInfo.toLowerCase match {
      case "kryo" => {
        val valInfo = getValueInfo(tupleBytes)
        if (_kryoSer == null) {
          _kryoSer = SerializerManager.GetSerializer("kryo")
          if (_kryoSer != null && _classLoader != null) {
            _kryoSer.SetClassLoader(_classLoader)
          }
        }
        if (_kryoSer != null) {
          objs(0) = _kryoSer.DeserializeObjectFromByteArray(valInfo).asInstanceOf[KamanjaData]
        }
      }
      case "manual" => {
        val valInfo = getValueInfo(tupleBytes)
        val datarec = new KamanjaData
        datarec.DeserializeData(valInfo, _mdres, _classLoader)
        objs(0) = datarec
      }
      case _ => {
        throw new Exception("Found un-handled Serializer Info: " + serInfo)
      }
    }
  }

  private def GetDataStoreHandle(jarPaths: collection.immutable.Set[String], dataStoreInfo: String, tableName: String): DataStore = {
    try {
      logger.debug("Getting DB Connection for dataStoreInfo:%s, tableName:%s".format(dataStoreInfo, tableName))
      return KeyValueManager.Get(jarPaths, dataStoreInfo, tableName)
    } catch {
      case e: Exception => {
        logger.error("Failed to GetDataStoreHandle")
        throw e
      }
    }
  }

  private def makeKey(key: String): Key = {
    var k = new Key
    k ++= key.getBytes("UTF8")
    k
  }

  private def makeValue(value: String, serializerInfo: String): Value = {
    var v = new Value
    v ++= serializerInfo.getBytes("UTF8")

    // Making sure we write first _serInfoBufBytes bytes as serializerInfo. Pad it if it is less than _serInfoBufBytes bytes
    if (v.size < _serInfoBufBytes) {
      val spacebyte = ' '.toByte
      for (c <- v.size to _serInfoBufBytes)
        v += spacebyte
    }

    // Trim if it is more than _serInfoBufBytes bytes
    if (v.size > _serInfoBufBytes) {
      v.reduceToSize(_serInfoBufBytes)
    }

    // Saving Value
    v ++= value.getBytes("UTF8")

    v
  }

  private def makeValue(value: Array[Byte], serializerInfo: String): Value = {
    var v = new Value
    v ++= serializerInfo.getBytes("UTF8")

    // Making sure we write first _serInfoBufBytes bytes as serializerInfo. Pad it if it is less than _serInfoBufBytes bytes
    if (v.size < _serInfoBufBytes) {
      val spacebyte = ' '.toByte
      for (c <- v.size to _serInfoBufBytes)
        v += spacebyte
    }

    // Trim if it is more than _serInfoBufBytes bytes
    if (v.size > _serInfoBufBytes) {
      v.reduceToSize(_serInfoBufBytes)
    }

    // Saving Value
    v ++= value

    v
  }

  private def buildAdapterUniqueValue(tupleBytes: Value, objs: Array[(Long, String, List[(String, String, String)])]) {
    // Get first _serInfoBufBytes bytes
    if (tupleBytes.size < _serInfoBufBytes) {
      val errMsg = s"Invalid input. This has only ${tupleBytes.size} bytes data. But we are expecting serializer buffer bytes as of size ${_serInfoBufBytes}"
      logger.error(errMsg)
      throw new Exception(errMsg)
    }

    val valInfo = getValueInfo(tupleBytes)

    implicit val jsonFormats: Formats = DefaultFormats
    val uniqVal = parse(new String(valInfo)).extract[AdapterUniqueValueDes]

    var res = List[(String, String, String)]()

    if (uniqVal.Out != None) {
      res = uniqVal.Out.get.map(o => { (o(0), o(1), o(2)) })
    }

    objs(0) = (uniqVal.T, uniqVal.V, res)
  }

  private def buildModelsResult(tupleBytes: Value, objs: Array[scala.collection.mutable.Map[String, SavedMdlResult]]) {
    // Get first _serInfoBufBytes bytes
    if (tupleBytes.size < _serInfoBufBytes) {
      val errMsg = s"Invalid input. This has only ${tupleBytes.size} bytes data. But we are expecting serializer buffer bytes as of size ${_serInfoBufBytes}"
      logger.error(errMsg)
      throw new Exception(errMsg)
    }

    val serInfo = getSerializeInfo(tupleBytes)

    serInfo.toLowerCase match {
      case "kryo" => {
        val valInfo = getValueInfo(tupleBytes)
        if (_kryoSer == null) {
          _kryoSer = SerializerManager.GetSerializer("kryo")
          if (_kryoSer != null && _classLoader != null) {
            _kryoSer.SetClassLoader(_classLoader)
          }
        }
        if (_kryoSer != null) {
          objs(0) = _kryoSer.DeserializeObjectFromByteArray(valInfo).asInstanceOf[scala.collection.mutable.Map[String, SavedMdlResult]]
        }
      }
      case _ => {
        throw new Exception("Found un-handled Serializer Info: " + serInfo)
      }
    }
  }

  private def loadObjFromDb(transId: Long, msgOrCont: MsgContainerInfo, key: List[String]): KamanjaData = {
    val partKeyStr = KamanjaData.PrepareKey(msgOrCont.objFullName, key, 0, 0)
    var objs: Array[KamanjaData] = new Array[KamanjaData](1)
    val buildOne = (tupleBytes: Value) => {
      buildObject(tupleBytes, objs, msgOrCont.containerType)
    }
    try {
      _allDataDataStore.get(makeKey(partKeyStr), buildOne)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Data not found for key:" + partKeyStr + "\nStackTrace:" + stackTrace)
      }
    }
    if (objs(0) != null) {
      val lockObj = if (msgOrCont.loadedAll) msgOrCont else _locks(lockIdx(transId))
      lockObj.synchronized {
        msgOrCont.data(InMemoryKeyDataInJson(key)) = (false, objs(0))
      }
    }
    return objs(0)
  }

  private def localGetObject(transId: Long, containerName: String, partKey: List[String], primaryKey: List[String]): MessageContainerBase = {
    if (TxnContextCommonFunctions.IsEmptyKey(partKey) || TxnContextCommonFunctions.IsEmptyKey(primaryKey))
      return null
    val txnCtxt = getTransactionContext(transId, false)
    if (txnCtxt != null) {
      val (v, foundPartKey) = txnCtxt.getObject(containerName, partKey, primaryKey)
      if (foundPartKey) {
        return v
      }
      if (v != null) return v // It must be null. Without finding partition key it should not find the primary key
    }

    val container = _messagesOrContainers.getOrElse(containerName.toLowerCase, null)
    if (container != null) {
      val partKeyStr = InMemoryKeyDataInJson(partKey)
      val kamanjaData = container.data.getOrElse(partKeyStr, null)
      if (kamanjaData != null) {
        // Search for primary key match
        val v = kamanjaData._2.GetMessageContainerBase(primaryKey.toArray, false)
        if (txnCtxt != null)
          txnCtxt.setFetchedObj(containerName, partKeyStr, kamanjaData._2)
        return v;
      }
      // If not found in memory, try in DB
      val loadedFfData = loadObjFromDb(transId, container, partKey)
      if (loadedFfData != null) {
        // Search for primary key match
        val v = loadedFfData.GetMessageContainerBase(primaryKey.toArray, false)
        if (txnCtxt != null)
          txnCtxt.setFetchedObj(containerName, partKeyStr, loadedFfData)
        return v;
      }
      // If not found in DB, Create Empty and set to current transaction context
      if (txnCtxt != null) {
        val emptyFfData = new KamanjaData
        emptyFfData.SetKey(partKey.toArray)
        emptyFfData.SetTypeName(containerName)
        txnCtxt.setFetchedObj(containerName, partKeyStr, emptyFfData)
      }
    }
    null
  }

  private def localHistoryObjects(transId: Long, containerName: String, partKey: List[String], appendCurrentChanges: Boolean): Array[MessageContainerBase] = {
    val retVals = ArrayBuffer[MessageContainerBase]()
    if (TxnContextCommonFunctions.IsEmptyKey(partKey))
      return retVals.toArray
    val txnCtxt = getTransactionContext(transId, false)
    if (txnCtxt != null) {
      val (objs, foundPartKey) = txnCtxt.getObjects(containerName, partKey, appendCurrentChanges)
      retVals ++= objs
      if (foundPartKey)
        return retVals.toArray
    }

    val container = _messagesOrContainers.getOrElse(containerName.toLowerCase, null)
    if (container != null) {
      val partKeyStr = InMemoryKeyDataInJson(partKey)
      val kamanjaData = container.data.getOrElse(partKeyStr, null)
      if (kamanjaData != null) {
        // Search for primary key match
        if (txnCtxt != null)
          txnCtxt.setFetchedObj(containerName, partKeyStr, kamanjaData._2)
        retVals ++= kamanjaData._2.GetAllData
      } else {
        val loadedFfData = loadObjFromDb(transId, container, partKey)
        if (loadedFfData != null) {
          // Search for primary key match
          if (txnCtxt != null)
            txnCtxt.setFetchedObj(containerName, partKeyStr, loadedFfData)
          retVals ++= loadedFfData.GetAllData
        } else {
          // If not found in DB, Create Empty and set to current transaction context
          if (txnCtxt != null) {
            val emptyFfData = new KamanjaData
            emptyFfData.SetKey(partKey.toArray)
            emptyFfData.SetTypeName(containerName)
            txnCtxt.setFetchedObj(containerName, partKeyStr, emptyFfData)
          }
        }
      }
    }

    retVals.toArray
  }

  private def localGetAllKeyValues(transId: Long, containerName: String): Array[MessageContainerBase] = {
    val fnd = _messagesOrContainers.getOrElse(containerName.toLowerCase, null)
    if (fnd != null) {
      if (fnd.loadedAll) {
        var allObjs = ArrayBuffer[MessageContainerBase]()
        fnd.data.foreach(kv => {
          allObjs ++= kv._2._2.GetAllData
        })
        val txnCtxt = getTransactionContext(transId, false)
        if (txnCtxt != null)
          allObjs ++= txnCtxt.getAllObjects(containerName)
        return allObjs.toArray
      } else {
        throw new Exception("Object %s is not loaded all at once. So, we can not get all objects here".format(fnd.objFullName))
      }
    } else {
      return Array[MessageContainerBase]()
    }
  }

  private def localGetAllObjects(transId: Long, containerName: String): Array[MessageContainerBase] = {
    return localGetAllKeyValues(transId, containerName)
  }

  private def localGetAdapterUniqueKeyValue(transId: Long, key: String): (Long, String, List[(String, String, String)]) = {
    val txnCtxt = getTransactionContext(transId, false)
    if (txnCtxt != null) {
      val v = txnCtxt.getAdapterUniqueKeyValue(key)
      if (v != null) return v
    }

    val v = _adapterUniqKeyValData.getOrElse(key, null)
    if (v != null) return v
    val partKeyStr = KamanjaData.PrepareKey("AdapterUniqKvData", List(key), 0, 0)
    var objs = new Array[(Long, String, List[(String, String, String)])](1)
    val buildAdapOne = (tupleBytes: Value) => {
      buildAdapterUniqueValue(tupleBytes, objs)
    }
    try {
      _allDataDataStore.get(makeKey(partKeyStr), buildAdapOne)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Data not found for key:" + partKeyStr + "\nStackTrace:" + stackTrace)
      }
    }
    if (objs(0) != null) {
      _adapterUniqKeyValData.synchronized {
        _adapterUniqKeyValData(key) = objs(0)
      }
    }
    return objs(0)
  }

  private def localGetModelsResult(transId: Long, key: List[String]): scala.collection.mutable.Map[String, SavedMdlResult] = {
    val txnCtxt = getTransactionContext(transId, false)
    if (txnCtxt != null) {
      val v = txnCtxt.getModelsResult(key)
      if (v != null) return v
    }

    val keystr = InMemoryKeyDataInJson(key)
    val v = _modelsResult.getOrElse(keystr, null)
    if (v != null) return v
    var objs = new Array[scala.collection.mutable.Map[String, SavedMdlResult]](1)
    val buildMdlOne = (tupleBytes: Value) => { buildModelsResult(tupleBytes, objs) }
    val partKeyStr = KamanjaData.PrepareKey("ModelResults", key, 0, 0)
    try {
      _allDataDataStore.get(makeKey(partKeyStr), buildMdlOne)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Data not found for key:" + partKeyStr + "\nStackTrace:" + stackTrace)
      }
    }
    if (objs(0) != null) {
      _modelsResult.synchronized {
        _modelsResult(keystr) = objs(0)
      }
      return objs(0)
    }
    return scala.collection.mutable.Map[String, SavedMdlResult]()
  }

  /**
   * Does at least one of the supplied keys exist in a container with the supplied name?
   */
  private def localContainsAny(transId: Long, containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): Boolean = {
    var remainingPartKeys = partKeys
    val txnCtxt = getTransactionContext(transId, false)
    if (txnCtxt != null) {
      val (retval, notFoundPartKeys) = txnCtxt.containsAny(containerName, remainingPartKeys, primaryKeys)
      if (retval)
        return true
      remainingPartKeys = notFoundPartKeys
    }

    val container = _messagesOrContainers.getOrElse(containerName.toLowerCase, null)
    val reMainingForDb = ArrayBuffer[List[String]]()
    if (container != null) {
      for (i <- 0 until remainingPartKeys.size) {
        val kamanjaData = container.data.getOrElse(InMemoryKeyDataInJson(remainingPartKeys(i)), null)
        if (kamanjaData != null) {
          // Search for primary key match
          val fnd = kamanjaData._2.GetMessageContainerBase(primaryKeys(i).toArray, false)
          if (fnd != null)
            return true
        } else {
          reMainingForDb += remainingPartKeys(i)
        }
      }

      for (i <- 0 until reMainingForDb.size) {
        val kamanjaData = loadObjFromDb(transId, container, reMainingForDb(i))
        if (kamanjaData != null) {
          // Search for primary key match
          val fnd = kamanjaData.GetMessageContainerBase(primaryKeys(i).toArray, false)
          if (fnd != null)
            return true
        }
      }
    }
    false
  }

  /**
   * Do all of the supplied keys exist in a container with the supplied name?
   */
  private def localContainsAll(transId: Long, containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): Boolean = {
    var remainingPartKeys: Array[List[String]] = partKeys
    var remainingPrimaryKeys: Array[List[String]] = primaryKeys
    val txnCtxt = getTransactionContext(transId, false)
    if (txnCtxt != null) {
      val (matchedPartKeys, unmatchedPartKeys, matchedPrimaryKeys, unmatchedPrimaryKeys) = txnCtxt.containsKeys(containerName, remainingPartKeys, remainingPrimaryKeys)
      remainingPartKeys = unmatchedPartKeys
      remainingPrimaryKeys = unmatchedPrimaryKeys
    }

    val container = _messagesOrContainers.getOrElse(containerName.toLowerCase, null)
    if (container != null) {
      var unmatchedPartKeys = ArrayBuffer[List[String]]()
      var unmatchedPrimaryKeys = ArrayBuffer[List[String]]()

      for (i <- 0 until remainingPartKeys.size) {
        val kamanjaData = container.data.getOrElse(InMemoryKeyDataInJson(remainingPartKeys(i)), null)
        if (kamanjaData != null) {
          // Search for primary key match
          val fnd = kamanjaData._2.GetMessageContainerBase(remainingPrimaryKeys(i).toArray, false)
          if (fnd != null) {
            // Matched
          } else {
            unmatchedPartKeys += remainingPartKeys(i)
            unmatchedPrimaryKeys += remainingPrimaryKeys(i)
          }
        } else {
          unmatchedPartKeys += remainingPartKeys(i)
          unmatchedPrimaryKeys += remainingPrimaryKeys(i)
        }
      }

      var unmatchedFinalPatKeys = ArrayBuffer[List[String]]()
      var unmatchedFinalPrimaryKeys = ArrayBuffer[List[String]]()

      // BUGBUG:: Need to check whether the 1st key loaded 2nd key also. Because same partition key can have multiple primary keys or partition key itself can be duplicated
      for (i <- 0 until unmatchedPartKeys.size) {
        val kamanjaData = container.data.getOrElse(InMemoryKeyDataInJson(unmatchedPartKeys(i)), null)
        if (kamanjaData != null) {
          // Search for primary key match
          val fnd = kamanjaData._2.GetMessageContainerBase(unmatchedPrimaryKeys(i).toArray, false)
          if (fnd != null) {
            // Matched
          } else {
            unmatchedFinalPatKeys += unmatchedPartKeys(i)
            unmatchedFinalPrimaryKeys += unmatchedPrimaryKeys(i)
          }
        } else {
          unmatchedFinalPatKeys += unmatchedPartKeys(i)
          unmatchedFinalPrimaryKeys += unmatchedPrimaryKeys(i)
        }
      }
      remainingPartKeys = unmatchedFinalPatKeys.toArray
    }

    (remainingPartKeys.size == 0)
  }

  // Same kind of code is there in localGetObject
  private def loadBeforeSetObject(txnCtxt: TransactionContext, transId: Long, containerName: String, partKey: List[String]): Unit = {
    if (TxnContextCommonFunctions.IsEmptyKey(partKey))
      return
    val container = _messagesOrContainers.getOrElse(containerName.toLowerCase, null)
    if (container != null) {
      val partKeyStr = InMemoryKeyDataInJson(partKey)
      val kamanjaData = container.data.getOrElse(partKeyStr, null)
      if (kamanjaData != null) {
        if (txnCtxt != null)
          txnCtxt.setFetchedObj(containerName, partKeyStr, kamanjaData._2)
        return ;
      }
      // If not found in memory, try in DB
      val loadedFfData = loadObjFromDb(transId, container, partKey)
      if (loadedFfData != null) {
        if (txnCtxt != null)
          txnCtxt.setFetchedObj(containerName, partKeyStr, loadedFfData)
        return ;
      }
      // If not found in DB, Create Empty and set to current transaction context
      if (txnCtxt != null) {
        val emptyFfData = new KamanjaData
        emptyFfData.SetKey(partKey.toArray)
        emptyFfData.SetTypeName(containerName)
        txnCtxt.setFetchedObj(containerName, partKeyStr, emptyFfData)
      }
    }
  }

  private def localSetObject(transId: Long, containerName: String, partKey: List[String], value: MessageContainerBase): Unit = {
    var txnCtxt = getTransactionContext(transId, true)
    if (txnCtxt != null) {
      val container = txnCtxt.getMsgContainer(containerName.toLowerCase, false)
      if (container == null) {
        // Try to load the key if they exists in global storage.
        loadBeforeSetObject(txnCtxt, transId, containerName, partKey)
      }
      txnCtxt.setObject(containerName, partKey, value)
    }
  }

  private def localSetAdapterUniqueKeyValue(transId: Long, key: String, value: String, outputResults: List[(String, String, String)]): Unit = {
    var txnCtxt = getTransactionContext(transId, true)
    if (txnCtxt != null) {
      txnCtxt.setAdapterUniqueKeyValue(transId, key, value, outputResults)
    }
  }

  private def localSaveModelsResult(transId: Long, key: List[String], value: scala.collection.mutable.Map[String, SavedMdlResult]): Unit = {
    var txnCtxt = getTransactionContext(transId, true)
    if (txnCtxt != null) {
      txnCtxt.saveModelsResult(key, value)
    }
  }

  private def collectKey(key: Key, keys: ArrayBuffer[KamanjaDataKey]): Unit = {
    implicit val jsonFormats: Formats = DefaultFormats
    val parsed_key = parse(new String(key.toArray)).extract[KamanjaDataKey]
    keys += parsed_key
  }

  override def SetClassLoader(cl: java.lang.ClassLoader): Unit = {
    _classLoader = cl
    if (_kryoSer != null)
      _kryoSer.SetClassLoader(_classLoader)
  }

  override def SetMetadataResolveInfo(mdres: MdBaseResolveInfo): Unit = {
    _mdres = mdres
  }

  //BUGBUG:: May be we need to lock before we do anything here
  override def Shutdown: Unit = {
    _adapterUniqKeyValData.clear
    if (_allDataDataStore != null)
      _allDataDataStore.Shutdown
    _allDataDataStore = null
    if (_committingPartitionsDataStore != null)
      _committingPartitionsDataStore.Shutdown
    _committingPartitionsDataStore = null
    if (_checkPointAdapInfoDataStore != null)
      _checkPointAdapInfoDataStore.Shutdown
    _checkPointAdapInfoDataStore = null
    _messagesOrContainers.clear
  }

  private def getTableKeys(all_keys: ArrayBuffer[KamanjaDataKey], objName: String): Array[KamanjaDataKey] = {
    val tmpObjName = objName.toLowerCase
    all_keys.filter(k => k.T.compareTo(objName) == 0).toArray
  }

  override def getPropertyValue(clusterId: String, key: String): String = {
    _mgr.GetUserProperty(clusterId, key)
  }

  // Adding new messages or Containers
  override def AddNewMessageOrContainers(dataDataStoreInfo: String, containerNames: Array[String], loadAllData: Boolean, statusDataStoreInfo: String, jarPaths: collection.immutable.Set[String]): Unit = {
    logger.info("Messages/Containers => " + (if (containerNames != null) containerNames.mkString(",") else ""))
    logger.debug("Messages/Containers => loadAllData:%s, jarPaths:%s".format(loadAllData.toString, jarPaths.mkString(",")))
    if (_allDataDataStore == null) {
      logger.debug("Messages/Containers => dataDataStoreInfo:" + dataDataStoreInfo)
      _allDataDataStore = GetDataStoreHandle(jarPaths, dataDataStoreInfo, "AllData")
    }
    if (_committingPartitionsDataStore == null) {
      _committingPartitionsDataStore = GetDataStoreHandle(jarPaths, statusDataStoreInfo, "CommmittingTransactions")
      logger.debug("Messages/Containers => statusDataStoreInfo:" + statusDataStoreInfo + ", _committingPartitionsDataStore:" + _committingPartitionsDataStore)
    }
    if (_checkPointAdapInfoDataStore == null) {
      _checkPointAdapInfoDataStore = GetDataStoreHandle(jarPaths, statusDataStoreInfo, "checkPointAdapInfo")
      logger.debug("Messages/Containers => statusDataStoreInfo:" + statusDataStoreInfo + ", _checkPointAdapInfoDataStore:" + _checkPointAdapInfoDataStore)
    }

    _jarPaths = jarPaths
    val all_keys = ArrayBuffer[KamanjaDataKey]() // All keys for all tables for now
    var keysAlreadyLoaded = false

    containerNames.foreach(c1 => {
      val c = c1.toLowerCase
      val (namespace, name) = Utils.parseNameTokenNoVersion(c)
      var containerType = _mgr.ActiveType(namespace, name)

      if (containerType != null) {

        val objFullName: String = containerType.FullName.toLowerCase

        val fnd = _messagesOrContainers.getOrElse(objFullName, null)

        if (fnd != null) {
          // We already have this
        } else {
          val newMsgOrContainer = new MsgContainerInfo

          newMsgOrContainer.containerType = containerType
          newMsgOrContainer.objFullName = objFullName
          newMsgOrContainer.isContainer = (_mgr.ActiveContainer(namespace, name) != null)

          /** create a map to cache the entries to be resurrected from the mapdb */
          _messagesOrContainers(objFullName) = newMsgOrContainer
          if (loadAllData) {
            if (keysAlreadyLoaded == false) {
              val keyCollector = (key: Key) => {
                collectKey(key, all_keys)
              }
              _allDataDataStore.getAllKeys(keyCollector)
              keysAlreadyLoaded = true
            }
            val keys = getTableKeys(all_keys, objFullName)
            if (keys.size > 0) {
              loadMap(keys, newMsgOrContainer)
            }
            newMsgOrContainer.loadedAll = true
            newMsgOrContainer.reload = false
          }
        }
      } else {
        logger.error("Message/Container %s not found".format(c))
      }
    })
  }

  private def Clone(vals: Array[MessageContainerBase]): Array[MessageContainerBase] = {
    if (vals == null) return null
    return vals.map(v => {
      if (v == null) null else v.Clone()
    })
  }

  private def Clone(v: MessageContainerBase): MessageContainerBase = {
    if (v == null) return null
    return v.Clone()
  }

  private def Clone(ov: Option[MessageContainerBase]): Option[MessageContainerBase] = {
    if (ov == None) return ov
    Some(ov.get.Clone())
  }

  override def getAllObjects(transId: Long, containerName: String): Array[MessageContainerBase] = {
    Clone(localGetAllObjects(transId, containerName))
  }

  override def getObject(transId: Long, containerName: String, partKey: List[String], primaryKey: List[String]): MessageContainerBase = {
    Clone(localGetObject(transId, containerName, partKey, primaryKey))
  }

  override def getHistoryObjects(transId: Long, containerName: String, partKey: List[String], appendCurrentChanges: Boolean): Array[MessageContainerBase] = {
    Clone(localHistoryObjects(transId, containerName, partKey, appendCurrentChanges))
  }

  override def getAdapterUniqueKeyValue(transId: Long, key: String): (Long, String, List[(String, String, String)]) = {
    localGetAdapterUniqueKeyValue(transId, key)
  }

  override def getModelsResult(transId: Long, key: List[String]): scala.collection.mutable.Map[String, SavedMdlResult] = {
    localGetModelsResult(transId, key)
  }

  /**
   * Does the supplied key exist in a container with the supplied name?
   */
  override def contains(transId: Long, containerName: String, partKey: List[String], primaryKey: List[String]): Boolean = {
    localContainsAny(transId, containerName, Array(partKey), Array(primaryKey))
  }

  /**
   * Does at least one of the supplied keys exist in a container with the supplied name?
   */
  override def containsAny(transId: Long, containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): Boolean = {
    localContainsAny(transId, containerName, partKeys, primaryKeys)
  }

  /**
   * Do all of the supplied keys exist in a container with the supplied name?
   */
  override def containsAll(transId: Long, containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): Boolean = {
    localContainsAll(transId, containerName, partKeys, primaryKeys)
  }

  override def setObject(transId: Long, containerName: String, partKey: List[String], value: MessageContainerBase): Unit = {
    localSetObject(transId, containerName, partKey, value)
  }

  override def setAdapterUniqueKeyValue(transId: Long, key: String, value: String, outputResults: List[(String, String, String)]): Unit = {
    localSetAdapterUniqueKeyValue(transId, key, value, outputResults)
  }

  override def saveModelsResult(transId: Long, key: List[String], value: scala.collection.mutable.Map[String, SavedMdlResult]): Unit = {
    localSaveModelsResult(transId, key, value)
  }

  override def getChangedData(tempTransId: Long, includeMessages: Boolean, includeContainers: Boolean): scala.collection.immutable.Map[String, List[List[String]]] = {
    val changedContainersData = scala.collection.mutable.Map[String, List[List[String]]]()

    // Commit Data and Removed Transaction information from status
    val txnCtxt = getTransactionContext(tempTransId, false)
    if (txnCtxt == null)
      return changedContainersData.toMap

    val messagesOrContainers = txnCtxt.getAllMessagesAndContainers

    messagesOrContainers.foreach(v => {
      val mc = _messagesOrContainers.getOrElse(v._1, null)
      if (mc != null) {
        val savingKeys = new ArrayBuffer[List[String]]()
        if (includeMessages && includeContainers) {
          // All the vlaues (includes messages & containers)
          v._2.data.foreach(kv => {
            if (kv._2._1 && kv._2._2.DataSize > 0) {
              savingKeys += kv._2._2.GetKey.toList
            }
          })
        } else if (includeMessages && /* v._2.containerType.tTypeType == ObjTypeType.tContainer && */ (mc.isContainer == false && v._2.isContainer == false)) {
          // Msgs
          v._2.data.foreach(kv => {
            if (kv._2._1 && kv._2._2.DataSize > 0) {
              savingKeys += kv._2._2.GetKey.toList
            }
          })
        } else if (includeContainers && /* v._2.containerType.tTypeType == ObjTypeType.tContainer && */ (mc.isContainer || v._2.isContainer)) {
          // Containers
          v._2.data.foreach(kv => {
            if (kv._2._1 && kv._2._2.DataSize > 0) {
              savingKeys += kv._2._2.GetKey.toList
            }
          })
        }
        if (savingKeys.size > 0)
          changedContainersData(v._1) = savingKeys.toList
      }
    })

    return changedContainersData.toMap
  }

  // Final Commit for the given transaction
  // outputResults has AdapterName, PartitionKey & Message
  override def commitData(transId: Long, key: String, value: String, outResults: List[(String, String, String)]): Unit = {
    val outputResults = if (outResults != null) outResults else List[(String, String, String)]()

    // Commit Data and Removed Transaction information from status
    val txnCtxt = getTransactionContext(transId, false)
    if (txnCtxt == null && (key == null || value == null))
      return

    // Persist current transaction objects
    val messagesOrContainers = if (txnCtxt != null) txnCtxt.getAllMessagesAndContainers else Map[String, MsgContainerInfo]()

    val localValues = scala.collection.mutable.Map[String, (Long, String, List[(String, String, String)])]()

    if (key != null && value != null && outputResults != null)
      localValues(key) = (transId, value, outputResults)

    val adapterUniqKeyValData = if (localValues.size > 0) localValues.toMap else if (txnCtxt != null) txnCtxt.getAllAdapterUniqKeyValData else Map[String, (Long, String, List[(String, String, String)])]()
    val modelsResult = if (txnCtxt != null) txnCtxt.getAllModelsResult else Map[String, scala.collection.mutable.Map[String, SavedMdlResult]]()

    if (_kryoSer == null) {
      _kryoSer = SerializerManager.GetSerializer("kryo")
      if (_kryoSer != null && _classLoader != null) {
        _kryoSer.SetClassLoader(_classLoader)
      }
    }

    val storeObjects = ArrayBuffer[IStorage]()
    var cntr = 0

    messagesOrContainers.foreach(v => {
      val mc = _messagesOrContainers.getOrElse(v._1, null)
      if (mc != null) {
        if (v._2.reload)
          mc.reload = true
        v._2.data.foreach(kv => {
          if (kv._2._1 && kv._2._2.DataSize > 0) {
            mc.data(kv._1) = (false, kv._2._2) // Here it is already converted to proper key type. Because we are copying from somewhere else where we applied function InMemoryKeyDataInJson
            try {
              val serVal = kv._2._2.SerializeData
              object obj extends IStorage {
                val k = makeKey(kv._2._2.SerializeKey)
                // KamanjaData.PrepareKey(mc.objFullName, ka, 0, 0)
                val v = makeValue(serVal, "manual")

                def Key = k

                def Value = v

                def Construct(Key: Key, Value: Value) = {}
              }
              storeObjects += obj
              cntr += 1
            } catch {
              case e: Exception => {

                logger.error("Failed to serialize/write data.")
                throw e
              }
            }
          }
        })

        v._2.current_msg_cont_data.clear
      }
    })

    adapterUniqKeyValData.foreach(v1 => {
      _adapterUniqKeyValData(v1._1) = v1._2
      try {
        object obj extends IStorage {
          val k = makeKey(KamanjaData.PrepareKey("AdapterUniqKvData", List(v1._1), 0, 0))
          val json = ("T" -> v1._2._1) ~
            ("V" -> v1._2._2)
          val compjson = compact(render(json))
          val v = makeValue(compjson.getBytes("UTF8"), "CSV")

          def Key = k

          def Value = v

          def Construct(Key: Key, Value: Value) = {}
        }
        storeObjects += obj
        cntr += 1
      } catch {
        case e: Exception => {
          logger.error("Failed to write data")
          throw e
        }
      }
    })

    modelsResult.foreach(v1 => {
      _modelsResult(v1._1) = v1._2
      try {
        val serVal = _kryoSer.SerializeObjectToByteArray(v1._2)
        object obj extends IStorage {
          val k = makeKey(KamanjaData.PrepareKey("ModelResults", List(v1._1), 0, 0))
          val v = makeValue(serVal, "kryo")

          def Key = k

          def Value = v

          def Construct(Key: Key, Value: Value) = {}
        }
        storeObjects += obj
        cntr += 1
      } catch {
        case e: Exception => {
          logger.error("Failed to write data")
          throw e
        }
      }
    })

    if (adapterUniqKeyValData.size > 0) {
      if (_committingPartitionsDataStore == null) {
        throw new Exception("Not found Status DataStore to save Status.")
      }

      // Persists unique key & value here for this transactionId
      val adapKeyValStoreObjs = new ArrayBuffer[IStorage]()

      adapterUniqKeyValData.foreach(v1 => {
        if (v1._2._3 != null && v1._2._3.size > 0) { // If we have output then only commit this, otherwise ignore 
          try {
            object obj extends IStorage {
              val k = makeKey(KamanjaData.PrepareKey("UK", List(v1._1), 0, 0))
              val json = ("T" -> v1._2._1) ~
                ("V" -> v1._2._2) ~
                ("Out" -> v1._2._3.map(qsres => List(qsres._1, qsres._2, qsres._3)))
              /*
              val json = ("T" -> v1._2._1) ~
                ("V" -> v1._2._2) ~
                ("Qs" -> v1._2._3.map(qsres => { qsres._1 })) ~
                ("Ks" -> v1._2._3.map(qsres => { qsres._2 })) ~
                ("Res" -> v1._2._3.map(qsres => { qsres._3 }))
*/
              val compjson = compact(render(json))
              val v = makeValue(compjson.getBytes("UTF8"), "CSV")

              def Key = k

              def Value = v

              def Construct(Key: Key, Value: Value) = {}
            }
            adapKeyValStoreObjs += obj
          } catch {
            case e: Exception => {
              logger.error("Failed to write data")
              throw e
            }
          }
        }
      })

      if (adapKeyValStoreObjs.size > 0) {
        val txn1 = _committingPartitionsDataStore.beginTx()
        _committingPartitionsDataStore.putBatch(adapKeyValStoreObjs.toArray)
        _committingPartitionsDataStore.commitTx(txn1)
      }
    }

    val txn = _allDataDataStore.beginTx()
    try {
      logger.debug("Going to save " + cntr + " objects")
      storeObjects.foreach(o => {
        logger.debug("ObjKey:" + new String(o.Key.toArray) + " Value Size: " + o.Value.toArray.size)
      })
      _allDataDataStore.putBatch(storeObjects.toArray)
      _allDataDataStore.commitTx(txn)
    } catch {
      case e: Exception => {
        _allDataDataStore.endTx(txn)
        logger.error("Failed to write data")
        throw e
      }
    }

    // Remove the current transaction
    removeTransactionContext(transId)
  }

  // Set Reload Flag
  override def setReloadFlag(transId: Long, containerName: String): Unit = {
    // BUGBUG:: Set Reload Flag
    val txnCtxt = getTransactionContext(transId, true)
    if (txnCtxt != null) {
      txnCtxt.setReloadFlag(containerName)
    }
  }

  // Clear Intermediate results before Restart processing
  //BUGBUG:: May be we need to lock before we do anything here
  override def clearIntermediateResults: Unit = {
    // BUGBUG:: What happens to containers with reload flag
    _messagesOrContainers.foreach(v => {
      if (v._2.loadedAll == false) {
        v._2.data.clear
      }
    })

    _adapterUniqKeyValData.clear

    _modelsResult.clear
  }

  // Clear Intermediate results After updating them on different node or different component (like KVInit), etc
  //BUGBUG:: May be we need to lock before we do anything here
  def clearIntermediateResults(unloadMsgsContainers: Array[String]): Unit = {
    if (unloadMsgsContainers == null)
      return
    unloadMsgsContainers.foreach(mc => {
      val msgCont = _messagesOrContainers.getOrElse(mc.trim.toLowerCase, null)
      if (msgCont != null && msgCont.data != null) {
        msgCont.data.clear
      }
    })
  }

  // Get Status information from Final table
  override def getAllAdapterUniqKvDataInfo(keys: Array[String]): Array[(String, (Long, String))] = {
    val results = new ArrayBuffer[(String, (Long, String))]()
    var objs = new Array[(Long, String, List[(String, String, String)])](1)
    keys.foreach(key => {
      try {
        val buildAdapOne = (tupleBytes: Value) => {
          buildAdapterUniqueValue(tupleBytes, objs)
        }
        _allDataDataStore.get(makeKey(KamanjaData.PrepareKey("AdapterUniqKvData", List(key), 0, 0)), buildAdapOne)
        results += ((key, (objs(0)._1, objs(0)._2)))
      } catch {
        case e: Exception => {
          val stackTrace = StackTrace.ThrowableTraceString(e)
          logger.debug(s"getAllAdapterUniqKvDataInfo() -- Unable to load Status Info.  Reason:%s, Message:%s.\nStackTrace:%s".format(key, e.getCause, e.getMessage, stackTrace))
        }
      }
    })
    logger.debug("Loaded %d committing informations".format(results.size))
    results.toArray
  }

  // Get Committing information
  override def getAllIntermediateCommittingInfo: Array[(String, (Long, String, List[(String, String, String)]))] = {
    if (_committingPartitionsDataStore == null) {
      throw new Exception("Not found Status DataStore to get Status.")
    }

    val keys = ArrayBuffer[KamanjaDataKey]()
    val keyCollector = (key: Key) => {
      collectKey(key, keys)
    }
    _committingPartitionsDataStore.getAllKeys(keyCollector)

    val results = new ArrayBuffer[(String, (Long, String, List[(String, String, String)]))]()
    var objs = new Array[(Long, String, List[(String, String, String)])](1)
    keys.foreach(key => {
      if (key.T.compareToIgnoreCase("UK") == 0) {
        try {
          val buildAdapOne = (tupleBytes: Value) => {
            buildAdapterUniqueValue(tupleBytes, objs)
          }
          objs(0) = null
          _committingPartitionsDataStore.get(makeKey(KamanjaData.PrepareKey("UK", key.K, 0, 0)), buildAdapOne)
          if (objs(0) != null) {
            results += ((key.K(0), (objs(0))))
          }
        } catch {
          case e: Exception => {
            val stackTrace = StackTrace.ThrowableTraceString(e)
            logger.debug(s"getAllIntermediateCommittingInfo() -- Unable to load committing Info. Reason:%s, Message:%s.\nStackTrace:%s".format(key, e.getCause, e.getMessage, stackTrace))
          }
        }
      }
    })

    logger.debug("Loaded %d committing informations".format(results.size))
    results.toArray
  }

  // Getting intermediate committing information.
  override def getAllIntermediateCommittingInfo(keys: Array[String]): Array[(String, (Long, String, List[(String, String, String)]))] = {
    if (_committingPartitionsDataStore == null) {
      throw new Exception("Not found Status DataStore to get Status.")
    }

    val results = new ArrayBuffer[(String, (Long, String, List[(String, String, String)]))]()
    var objs = new Array[(Long, String, List[(String, String, String)])](1)
    keys.foreach(key => {
      try {
        val buildAdapOne = (tupleBytes: Value) => {
          buildAdapterUniqueValue(tupleBytes, objs)
        }
        objs(0) = null
        _committingPartitionsDataStore.get(makeKey(KamanjaData.PrepareKey("UK", List(key), 0, 0)), buildAdapOne)
        if (objs(0) != null)
          results += ((key, (objs(0))))
      } catch {
        case e: Exception => {
          val stackTrace = StackTrace.ThrowableTraceString(e)
          logger.debug(s"getAllIntermediateCommittingInfo() -- Unable to load committing Info. Reason:%s, Message:%s.\nStackTrace:%s".format(key, e.getCause, e.getMessage, stackTrace))
        }
      }
    })
    logger.debug("Loaded %d committing informations".format(results.size))
    results.toArray
  }

  override def removeCommittedKey(transId: Long, key: String): Unit = {
    if (_committingPartitionsDataStore == null) {
      throw new Exception("Not found Status DataStore to get Status.")
    }
    try {
      _committingPartitionsDataStore.del(makeKey(KamanjaData.PrepareKey("UK", List(key), 0, 0)))
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error(s"removeCommittedKey() -- Filed to delete key:%s. Reason:%s, Message:%s.\nStackTrace:%s".format(key, e.getCause, e.getMessage, stackTrace))
      }
    }
  }

  override def removeCommittedKeys(keys: Array[String]): Unit = {
    if (_committingPartitionsDataStore == null) {
      throw new Exception("Not found Status DataStore to get Status.")
    }

    val delKeys = keys.map(key => {
      makeKey(KamanjaData.PrepareKey("UK", List(key), 0, 0))
    })

    try {
      _committingPartitionsDataStore.delBatch(delKeys)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error(s"removeCommittedKey() -- Filed to delete keys:%s. Reason:%s, Message:%s.\nStackTrace:%s".format(keys.mkString(","), e.getCause, e.getMessage, stackTrace))
      }
    }
  }

  // Save Current State of the machine
  override def PersistLocalNodeStateEntries: Unit = {
    // BUGBUG:: Persist all state on this node.
  }

  // Save Remaining State of the machine
  override def PersistRemainingStateEntriesOnLeader: Unit = {
    // BUGBUG:: Persist Remaining state (when other nodes goes down, this helps)
  }

  override def PersistValidateAdapterInformation(validateUniqVals: Array[(String, String)]): Unit = {
    if (_checkPointAdapInfoDataStore == null) {
      throw new Exception("Not found Status DataStore to save Validate Adapters Information.")
    }
    // Persists unique key & value here for this transactionId
    val storeObjects = new Array[IStorage](validateUniqVals.size)
    var cntr = 0

    logger.debug(s"PersistValidateAdapterInformation => " + validateUniqVals.mkString(","))

    validateUniqVals.foreach(kv => {
      object obj extends IStorage {
        val key = makeKey(KamanjaData.PrepareKey("CP", List(kv._1), 0, 0))
        val value = kv._2
        val v = makeValue(value.getBytes("UTF8"), "CSV")

        def Key = key

        def Value = v

        def Construct(Key: Key, Value: Value) = {}
      }
      storeObjects(cntr) = obj
      cntr += 1
    })

    val txn = _checkPointAdapInfoDataStore.beginTx()
    _checkPointAdapInfoDataStore.putBatch(storeObjects)
    _checkPointAdapInfoDataStore.commitTx(txn)
  }

  private def buildValidateAdapInfo(tupleBytes: Value, objs: Array[String]) {
    // Get first _serInfoBufBytes bytes
    if (tupleBytes.size < _serInfoBufBytes) {
      val errMsg = s"Invalid input. This has only ${tupleBytes.size} bytes data. But we are expecting serializer buffer bytes as of size ${_serInfoBufBytes}"
      logger.error(errMsg)
      throw new Exception(errMsg)
    }

    val valInfo = getValueInfo(tupleBytes)
    objs(0) = new String(valInfo)
  }

  override def GetValidateAdapterInformation: Array[(String, String)] = {
    if (_checkPointAdapInfoDataStore == null) {
      throw new Exception("Not found Status DataStore to get Validate Adapters Information.")
    }
    logger.debug(s"GetValidateAdapterInformation() -- Entered")
    val results = ArrayBuffer[(String, String)]()

    val keys = ArrayBuffer[KamanjaDataKey]()
    val keyCollector = (key: Key) => {
      collectKey(key, keys)
    }
    logger.debug(s"GetValidateAdapterInformation() -- About to get keys from _checkPointAdapInfoDataStore:" + _checkPointAdapInfoDataStore)
    _checkPointAdapInfoDataStore.getAllKeys(keyCollector)
    var objs: Array[String] = new Array[String](1)
    logger.debug(s"GetValidateAdapterInformation() -- Get %d keys".format(keys.size))
    keys.foreach(key => {
      try {
        val buildAdapOne = (tupleBytes: Value) => {
          buildValidateAdapInfo(tupleBytes, objs)
        }
        _checkPointAdapInfoDataStore.get(makeKey(KamanjaData.PrepareKey(key.T, key.K, 0, 0)), buildAdapOne)
        logger.debug(s"GetValidateAdapterInformation -- %s -> %s".format(key.K(0), objs(0).toString))
        results += ((key.K(0), objs(0)))
      } catch {
        case e: Exception => {
          logger.debug(s"GetValidateAdapterInformation() -- Unable to load Validate (Check Point) Adapter Information")
        }
      }
    })
    logger.debug("Loaded %d Validate (Check Point) Adapter Information".format(results.size))
    results.toArray
  }

  override def ReloadKeys(tempTransId: Long, containerName: String, keys: List[List[String]]): Unit = {
    val container = _messagesOrContainers.getOrElse(containerName.toLowerCase, null)
    if (container != null) {
      val dataKeys = keys.map(partKey => {
        KamanjaDataKey(container.objFullName, partKey, List[Int](), 0)
      }).toArray
      loadMap(dataKeys, container)
      /*
      keys.foreach(partKey => {
        // Loading full Partition key for now.
        loadObjFromDb(tempTransId, container, partKey)
      })
      */
    }
  }

  private def getLocalRecent(transId: Long, containerName: String, partKey: List[String], tmRange: TimeRange, f: MessageContainerBase => Boolean): Option[MessageContainerBase] = {
    if (TxnContextCommonFunctions.IsEmptyKey(partKey))
      None
    val txnCtxt = getTransactionContext(transId, false)
    if (txnCtxt != null) {
      val (v, foundPartKey) = txnCtxt.getRecent(containerName, partKey, tmRange, f)
      if (foundPartKey) {
        return Some(v)
      }
      if (v != null) return Some(v) // It must be null. Without finding partition key it should not find the primary key
    }
    val container = _messagesOrContainers.getOrElse(containerName.toLowerCase, null)

    val (v, foundPartKey) = TxnContextCommonFunctions.getRecent(container, partKey, tmRange, f)

    if (foundPartKey)
      return Some(v)

    if (container != null) { // Loading for partition id
      // If not found in memory, try in DB
      val loadedFfData = loadObjFromDb(transId, container, partKey)
      if (loadedFfData != null) {
        val (v1, foundPartKey1) = TxnContextCommonFunctions.getRecentFromKamanjaData(loadedFfData, tmRange, f)
        if (foundPartKey1)
          return Some(v1)
      }
      return None // If partition key exists, tried DB also, no need to go down
    }

    None
  }

  override def getRecent(transId: Long, containerName: String, partKey: List[String], tmRange: TimeRange, f: MessageContainerBase => Boolean): Option[MessageContainerBase] = {
    Clone(getLocalRecent(transId, containerName, partKey, tmRange, f))
  }

  override def getRDD(transId: Long, containerName: String, partKey: List[String], tmRange: TimeRange, f: MessageContainerBase => Boolean): Array[MessageContainerBase] = {
    Clone(getLocalRDD(transId, containerName, partKey, tmRange, f))
  }

  private def getLocalRDD(transId: Long, containerName: String, partKey: List[String], tmRange: TimeRange, f: MessageContainerBase => Boolean): Array[MessageContainerBase] = {
    val foundPartKeys = ArrayBuffer[List[String]]()
    val retResult = ArrayBuffer[MessageContainerBase]()
    val txnCtxt = getTransactionContext(transId, false)
    if (txnCtxt != null) {
      val (res, foundPartKeys1) = txnCtxt.getRddData(containerName, partKey, tmRange, f, foundPartKeys.toArray)
      if (foundPartKeys1.size > 0) {
        foundPartKeys ++= foundPartKeys1
        retResult ++= res
        if (TxnContextCommonFunctions.IsEmptyKey(partKey) == false) // Already found the key, no need to go down
          return retResult.toArray
      }
    }

    val container = _messagesOrContainers.getOrElse(containerName.toLowerCase, null)
    // In memory
    if (container != null) {
      val (res1, foundPartKeys2) = TxnContextCommonFunctions.getRddData(container, partKey, tmRange, f, foundPartKeys.toArray)
      if (foundPartKeys2.size > 0) {
        foundPartKeys ++= foundPartKeys2
        retResult ++= res1 // Add only if we find all here
        if (TxnContextCommonFunctions.IsEmptyKey(partKey) == false) // Already found the key, no need to go down
          return retResult.toArray
      }
    }

    if (container != null && TxnContextCommonFunctions.IsEmptyKey(partKey) == false) { // Loading for partition id
      // If not found in memory, try in DB
      val loadedFfData = loadObjFromDb(transId, container, partKey)
      if (loadedFfData != null) {
        val res2 = TxnContextCommonFunctions.getRddDataFromKamanjaData(loadedFfData, tmRange, f)
        retResult ++= res2
      }
      return retResult.toArray
    }

    if (container != null) {
      if (container.loadedAll) {
        // Nothing to be loaded from database
      } else {
        // Need to get all keys for this message/container and take all the data 
        val all_keys = ArrayBuffer[KamanjaDataKey]() // All keys for all tables for now
        val keyCollector = (key: Key) => { collectKey(key, all_keys) }
        _allDataDataStore.getAllKeys(keyCollector)
        val keys = getTableKeys(all_keys, containerName.toLowerCase)
        if (keys.size > 0) {
          var objs: Array[KamanjaData] = new Array[KamanjaData](1)
          val buildOne = (tupleBytes: Value) => { buildObject(tupleBytes, objs, container.containerType) }
          val alreadyFoundPartKeys = foundPartKeys.toArray // No need to add to this list anymore. This is the final place we used this to check
          keys.foreach(key => {
            if (TxnContextCommonFunctions.IsKeyExists(alreadyFoundPartKeys, key.K) == false) {
              val StartDateRange = if (key.D.size == 2) key.D(0) else 0
              val EndDateRange = if (key.D.size == 2) key.D(1) else 0
              objs(0) = null
              try {
                _allDataDataStore.get(makeKey(KamanjaData.PrepareKey(key.T, key.K, StartDateRange, EndDateRange)), buildOne)

              } catch {
                case e: Exception => {
                  val stackTrace = StackTrace.ThrowableTraceString(e)
                  logger.debug("\nStackTrace:" + stackTrace)
                }
                case t: Throwable => {
                  val stackTrace = StackTrace.ThrowableTraceString(t)
                  logger.debug("\nStackTrace:" + stackTrace)
                }
              }
              if (objs(0) != null) {
                retResult ++= TxnContextCommonFunctions.getRddDataFromKamanjaData(objs(0), tmRange, f)
              }
            }
          })
        }
      }
    }

    return retResult.toArray
  }

  override def saveOne(transId: Long, containerName: String, partKey: List[String], value: MessageContainerBase): Unit = {
    if (value != null)
      localSetObject(transId, containerName, partKey, value)
  }

  override def saveRDD(transId: Long, containerName: String, values: Array[MessageContainerBase]): Unit = {
    if (values == null)
      return
    //BUGBUG:: For now we are looping thru and saving on Partition key
    values.foreach(v => {
      localSetObject(transId, containerName, v.PartitionKeyData.toList, v)
    })
  }
}