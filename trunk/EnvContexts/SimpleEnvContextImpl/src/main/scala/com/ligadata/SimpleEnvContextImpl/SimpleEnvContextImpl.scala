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
import com.ligadata.KvBase.{ Key, Value, TimeRange, KvBaseDefalts, KeyWithBucketIdAndPrimaryKey, KeyWithBucketIdAndPrimaryKeyCompHelper, LoadKeyWithBucketId }
import com.ligadata.StorageBase.{ DataStore, Transaction }
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
import com.ligadata.keyvaluestore.KeyValueManager
import java.io.{ ByteArrayInputStream, DataInputStream, DataOutputStream, ByteArrayOutputStream }
import java.util.{ TreeMap, Date };
// import collection._
// import JavaConverters._

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
    var loadedKeys = new java.util.TreeSet[LoadKeyWithBucketId](KvBaseDefalts.defaultLoadKeyComp) // By BucketId, BucketKey, Time Range
    //  val current_msg_cont_data = ArrayBuffer[MessageContainerBase]()
    val dataByTmPart = new TreeMap[KeyWithBucketIdAndPrimaryKey, MessageContainerBaseWithModFlag](KvBaseDefalts.defualtTimePartComp) // By time, BucketKey, then PrimaryKey/{transactionid & rowid}. This is little cheaper if we are going to get exact match, because we compare time & then bucketid
    val dataByBucketKey = new TreeMap[KeyWithBucketIdAndPrimaryKey, MessageContainerBaseWithModFlag](KvBaseDefalts.defualtBucketKeyComp) // By BucketKey, time, then PrimaryKey/{Transactionid & Rowid}
    var containerType: BaseTypeDef = null
    var isContainer: Boolean = false
    var objFullName: String = ""
    var dataStore: DataStore = null
  }

  object TxnContextCommonFunctions {
    //BUGBUG:: we are handling primaryKey only when partKey
    def getRecent(container: MsgContainerInfo, partKey: List[String], tmRange: TimeRange, primaryKey: List[String], f: MessageContainerBase => Boolean): (MessageContainerBase, Boolean) = {
      //BUGBUG:: Taking last record from the search. it may not be the most recent
      if (container != null) {
        if (TxnContextCommonFunctions.IsEmptyKey(partKey) == false) {
          val tmRng =
            if (tmRange == null)
              TimeRange(Long.MinValue, Long.MaxValue)
            else
              tmRange
          val partKeyAsArray = partKey.toArray
          val primKeyAsArray = if (primaryKey != null && primaryKey.size > 0) primaryKey.toArray else null
          val fromKey = KeyWithBucketIdAndPrimaryKey(KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(partKeyAsArray), Key(tmRng.beginTime, partKeyAsArray, 0, 0), primKeyAsArray != null, primKeyAsArray)
          val toKey = KeyWithBucketIdAndPrimaryKey(KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(partKeyAsArray), Key(tmRng.endTime, partKeyAsArray, Long.MaxValue, Int.MaxValue), primKeyAsArray != null, primKeyAsArray)
          val tmpDataByTmPart = new TreeMap[KeyWithBucketIdAndPrimaryKey, MessageContainerBaseWithModFlag](KvBaseDefalts.defualtTimePartComp) // By time, BucketKey, then PrimaryKey/{transactionid & rowid}. This is little cheaper if we are going to get exact match, because we compare time & then bucketid
          tmpDataByTmPart.putAll(container.dataByBucketKey.subMap(fromKey, true, toKey, true))
          val tmFilterMap = tmpDataByTmPart.subMap(fromKey, true, toKey, true)

          if (f != null) {
            var it1 = tmFilterMap.descendingMap().entrySet().iterator()
            while (it1.hasNext()) {
              val entry = it1.next();
              val value = entry.getValue();
              if (primKeyAsArray != null) {
                if (primKeyAsArray.sameElements(value.value.PrimaryKeyData) && f(value.value)) {
                  return (value.value, true);
                }
              } else {
                if (f(value.value)) {
                  return (value.value, true);
                }
              }
            }
          } else {
            if (primKeyAsArray != null) {
              var it1 = tmFilterMap.descendingMap().entrySet().iterator()
              while (it1.hasNext()) {
                val entry = it1.next();
                val value = entry.getValue();
                if (primKeyAsArray.sameElements(value.value.PrimaryKeyData))
                  return (value.value, true);
              }
            } else {
              val data = tmFilterMap.lastEntry()
              if (data != null)
                return (data.getValue().value, true)
            }
          }
        } else if (tmRange != null) {
          val fromKey = KeyWithBucketIdAndPrimaryKey(Int.MinValue, Key(tmRange.beginTime, null, 0, 0), false, null)
          val toKey = KeyWithBucketIdAndPrimaryKey(Int.MaxValue, Key(tmRange.endTime, null, Long.MaxValue, Int.MaxValue), false, null)
          val tmFilterMap = container.dataByTmPart.subMap(fromKey, true, toKey, true)

          if (f != null) {
            var it1 = tmFilterMap.descendingMap().entrySet().iterator()
            while (it1.hasNext()) {
              val entry = it1.next();
              val value = entry.getValue();
              if (f(value.value)) {
                return (value.value, true);
              }
            }
          } else {
            val data = tmFilterMap.lastEntry()
            if (data != null)
              return (data.getValue().value, true)
          }
        } else {
          val data = container.dataByTmPart.lastEntry()
          if (data != null)
            return (data.getValue().value, true)
        }
      }
      (null, false)
    }

    def IsEmptyKey(key: List[String]): Boolean = {
      (key == null || key.size == 0 /* || key.filter(k => k != null).size == 0 */)
    }

    def IsEmptyKey(key: Array[String]): Boolean = {
      (key == null || key.size == 0 /* || key.filter(k => k != null).size == 0 */)
    }

    /*
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
*/

    def getRddData(container: MsgContainerInfo, partKey: List[String], tmRange: TimeRange, primaryKey: List[String], f: MessageContainerBase => Boolean, alreadyFoundPartKeys: Array[Key]): (Array[MessageContainerBase], Array[Key]) = {
      val retResult = ArrayBuffer[MessageContainerBase]()
      var foundPartKeys = ArrayBuffer[Key]()
      if (container != null) {
        if (TxnContextCommonFunctions.IsEmptyKey(partKey) == false) {
          val tmRng =
            if (tmRange == null)
              TimeRange(Long.MinValue, Long.MaxValue)
            else
              tmRange
          val partKeyAsArray = partKey.toArray
          val primKeyAsArray = if (primaryKey != null && primaryKey.size > 0) primaryKey.toArray else null
          val fromKey = KeyWithBucketIdAndPrimaryKey(KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(partKeyAsArray), Key(tmRng.beginTime, partKeyAsArray, 0, 0), primKeyAsArray != null, primKeyAsArray)
          val toKey = KeyWithBucketIdAndPrimaryKey(KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(partKeyAsArray), Key(tmRng.endTime, partKeyAsArray, Long.MaxValue, Int.MaxValue), primKeyAsArray != null, primKeyAsArray)
          val tmpDataByTmPart = new TreeMap[KeyWithBucketIdAndPrimaryKey, MessageContainerBaseWithModFlag](KvBaseDefalts.defualtTimePartComp) // By time, BucketKey, then PrimaryKey/{transactionid & rowid}. This is little cheaper if we are going to get exact match, because we compare time & then bucketid
          tmpDataByTmPart.putAll(container.dataByBucketKey.subMap(fromKey, true, toKey, true))
          val tmFilterMap = tmpDataByTmPart.subMap(fromKey, true, toKey, true)

          if (f != null) {
            var it1 = tmFilterMap.entrySet().iterator()
            while (it1.hasNext()) {
              val entry = it1.next();
              val value = entry.getValue();
              if (f(value.value)) {
                retResult += value.value
                foundPartKeys += entry.getKey().key
              }
            }
          } else {
            var it1 = tmFilterMap.entrySet().iterator()
            while (it1.hasNext()) {
              val entry = it1.next();
              retResult += entry.getValue().value
              foundPartKeys += entry.getKey().key
            }
          }
        } else if (tmRange != null) {
          val fromKey = KeyWithBucketIdAndPrimaryKey(Int.MinValue, Key(tmRange.beginTime, null, 0, 0), false, null)
          val toKey = KeyWithBucketIdAndPrimaryKey(Int.MaxValue, Key(tmRange.endTime, null, Long.MaxValue, Int.MaxValue), false, null)
          val tmFilterMap = container.dataByTmPart.subMap(fromKey, true, toKey, true)

          if (f != null) {
            var it1 = tmFilterMap.entrySet().iterator()
            while (it1.hasNext()) {
              val entry = it1.next();
              val value = entry.getValue();
              if (f(value.value)) {
                retResult += value.value
                foundPartKeys += entry.getKey().key
              }
            }
          } else {
            var it1 = tmFilterMap.entrySet().iterator()
            while (it1.hasNext()) {
              val entry = it1.next();
              retResult += entry.getValue().value
              foundPartKeys += entry.getKey().key
            }
          }
        } else {
          var it1 = container.dataByTmPart.entrySet().iterator()
          while (it1.hasNext()) {
            val entry = it1.next();
            retResult += entry.getValue().value
            foundPartKeys += entry.getKey().key
          }
        }
      }
      (retResult.toArray, foundPartKeys.toArray)
    }
  }

  class TransactionContext(var txnId: Long) {
    private[this] val _messagesOrContainers = scala.collection.mutable.Map[String, MsgContainerInfo]()
    private[this] val _adapterUniqKeyValData = scala.collection.mutable.Map[String, (Long, String, List[(String, String, String)])]()
    private[this] val _modelsResult = scala.collection.mutable.Map[Key, scala.collection.mutable.Map[String, SavedMdlResult]]()

    def getMsgContainer(containerName: String, addIfMissing: Boolean): MsgContainerInfo = {
      var fnd = _messagesOrContainers.getOrElse(containerName.toLowerCase, null)
      if (fnd == null && addIfMissing) {
        fnd = new MsgContainerInfo
        _messagesOrContainers(containerName) = fnd
      }
      fnd
    }

    /*
    def setFetchedObj(containerName: String, partKeyStr: String, kamanjaData: KamanjaData): Unit = {
      val container = getMsgContainer(containerName.toLowerCase, true)
      if (container != null) {
        container.data(partKeyStr) = (false, kamanjaData)
      }
    }
*/
    def getAllObjects(containerName: String): Array[MessageContainerBase] = {
      return TxnContextCommonFunctions.getRddData(getMsgContainer(containerName.toLowerCase, false), null, null, null, null, Array[Key]())._1
    }

    def getObject(containerName: String, partKey: List[String], primaryKey: List[String]): (MessageContainerBase, Boolean) = {
      return TxnContextCommonFunctions.getRecent(getMsgContainer(containerName.toLowerCase, false), partKey, null, primaryKey, null)
    }

    /*
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
*/

    def containsAny(containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): (Boolean, Array[List[String]]) = {
      /*
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
*/
      (false, primaryKeys)
    }

    def containsKeys(containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): (Array[List[String]], Array[List[String]], Array[List[String]], Array[List[String]]) = {
      val container = getMsgContainer(containerName.toLowerCase, false)
      val matchedPartKeys = ArrayBuffer[List[String]]()
      val unmatchedPartKeys = ArrayBuffer[List[String]]()
      val matchedPrimaryKeys = ArrayBuffer[List[String]]()
      val unmatchedPrimaryKeys = ArrayBuffer[List[String]]()
      /*
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
*/
      (matchedPartKeys.toArray, unmatchedPartKeys.toArray, matchedPrimaryKeys.toArray, unmatchedPrimaryKeys.toArray)
    }

    def setObjects(containerName: String, tmValues: Array[Long], partKeys: Array[Array[String]], values: Array[MessageContainerBase]): Unit = {
      if (tmValues.size != partKeys.size || partKeys.size != values.size) {
        logger.error("All time partition value, bucket keys & values should have the same count in arrays. tmValues.size(%d), partKeys.size(%d), values.size(%d)".format(tmValues.size, partKeys.size, values.size))
        return
      }

      val container = getMsgContainer(containerName.toLowerCase, true)
      if (container != null) {
        for (i <- 0 until tmValues.size) {
          val bk = partKeys(i)
          if (TxnContextCommonFunctions.IsEmptyKey(bk) == false) {
            val t = tmValues(i)
            val v = values(i)

            if (v.TransactionId == 0)
              v.TransactionId(txnId) // Setting the current transactionid

            val primkey = v.PrimaryKeyData
            val putkey = KeyWithBucketIdAndPrimaryKey(KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(bk), Key(t, bk, v.TransactionId, v.RowNumber), primkey != null && primkey.size > 0, primkey)
            container.dataByBucketKey.put(putkey, MessageContainerBaseWithModFlag(true, v))
            container.dataByTmPart.put(putkey, MessageContainerBaseWithModFlag(true, v))
            /*
            container.current_msg_cont_data -= value
            container.current_msg_cont_data += value // to get the value to end
*/
          }
        }

      }

      return
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
      _modelsResult(Key(KvBaseDefalts.defaultTime, key.toArray, 0L, 0)) = value
    }

    def getModelsResult(k: Key): scala.collection.mutable.Map[String, SavedMdlResult] = {
      _modelsResult.getOrElse(k, null)
    }

    def getAllMessagesAndContainers = _messagesOrContainers.toMap

    def getAllAdapterUniqKeyValData = _adapterUniqKeyValData.toMap

    def getAllModelsResult = _modelsResult.toMap

    def getRecent(containerName: String, partKey: List[String], tmRange: TimeRange, primaryKey: List[String], f: MessageContainerBase => Boolean): (MessageContainerBase, Boolean) = {
      val (v, foundPartKey) = TxnContextCommonFunctions.getRecent(getMsgContainer(containerName.toLowerCase, false), partKey, tmRange, primaryKey, f)
      (v, foundPartKey)
    }

    def getRddData(containerName: String, partKey: List[String], tmRange: TimeRange, primaryKey: List[String], f: MessageContainerBase => Boolean, alreadyFoundPartKeys: Array[Key]): (Array[MessageContainerBase], Array[Key]) = {
      return TxnContextCommonFunctions.getRddData(getMsgContainer(containerName.toLowerCase, false), partKey, tmRange, primaryKey, f, alreadyFoundPartKeys)
    }
  }

  private[this] val _buckets = 257 // Prime number

  private[this] val _locks = new Array[Object](_buckets)

  // private[this] val _messagesOrContainers = scala.collection.mutable.Map[String, MsgContainerInfo]()
  private[this] val _txnContexts = new Array[scala.collection.mutable.Map[Long, TransactionContext]](_buckets)
  private[this] val _adapterUniqKeyValData = scala.collection.mutable.Map[String, (Long, String, List[(String, String, String)])]()
  private[this] val _modelsResult = scala.collection.mutable.Map[Key, scala.collection.mutable.Map[String, SavedMdlResult]]()

  private[this] var _kryoSer: com.ligadata.Serialize.Serializer = null
  private[this] var _classLoader: java.lang.ClassLoader = null
  private[this] var _defaultDataStore: DataStore = null
  private[this] var _statusinfoDataStore: DataStore = null
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

  private def buildObject(k: Key, v: Value): MessageContainerBase = {
    v.serializerType.toLowerCase match {
      case "kryo" => {
        if (_kryoSer == null) {
          _kryoSer = SerializerManager.GetSerializer("kryo")
          if (_kryoSer != null && _classLoader != null) {
            _kryoSer.SetClassLoader(_classLoader)
          }
        }
        if (_kryoSer != null) {
          return _kryoSer.DeserializeObjectFromByteArray(v.serializedInfo).asInstanceOf[MessageContainerBase]
        }
      }
      case "manual" => {
        return SerializeDeserialize.Deserialize(v.serializedInfo, _mdres, _classLoader, true, "")
      }
      case _ => {
        throw new Exception("Found un-handled Serializer Info: " + v.serializerType)
      }
    }

    return null
  }

  private def GetDataStoreHandle(jarPaths: collection.immutable.Set[String], dataStoreInfo: String): DataStore = {
    try {
      logger.debug("Getting DB Connection for dataStoreInfo:%s".format(dataStoreInfo))
      return KeyValueManager.Get(jarPaths, dataStoreInfo)
    } catch {
      case e: Exception => {
        logger.error("Failed to GetDataStoreHandle")
        throw e
      }
    }
  }

  val results = new ArrayBuffer[(String, (Long, String, List[(String, String)]))]()

  private def buildAdapterUniqueValue(k: Key, v: Value, results: ArrayBuffer[(String, (Long, String, List[(String, String, String)]))]) {
    implicit val jsonFormats: Formats = DefaultFormats
    val uniqVal = parse(new String(v.serializedInfo)).extract[AdapterUniqueValueDes]

    var res = List[(String, String, String)]()

    if (uniqVal.Out != None) {
      res = uniqVal.Out.get.map(o => { (o(0), o(1), o(2)) })
    }

    results += ((k.bucketKey(0), (uniqVal.T, uniqVal.V, res.toList))) // taking 1st key, that is what we are expecting
  }

  private def buildModelsResult(k: Key, v: Value, objs: Array[(Key, scala.collection.mutable.Map[String, SavedMdlResult])]) {
    v.serializerType.toLowerCase match {
      case "kryo" => {
        if (_kryoSer == null) {
          _kryoSer = SerializerManager.GetSerializer("kryo")
          if (_kryoSer != null && _classLoader != null) {
            _kryoSer.SetClassLoader(_classLoader)
          }
        }
        if (_kryoSer != null) {
          objs(0) = ((k, _kryoSer.DeserializeObjectFromByteArray(v.serializedInfo).asInstanceOf[scala.collection.mutable.Map[String, SavedMdlResult]]))
        }
      }
      case _ => {
        throw new Exception("Found un-handled Serializer Info: " + v.serializerType)
      }
    }
  }

  /*
  
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
  
  */

  private def localGetObject(transId: Long, containerName: String, partKey: List[String], primaryKey: List[String]): MessageContainerBase = {
    /*
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
*/
    null
  }

  private def localHistoryObjects(transId: Long, containerName: String, partKey: List[String], appendCurrentChanges: Boolean): Array[MessageContainerBase] = {
    val retVals = ArrayBuffer[MessageContainerBase]()
    /*
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
*/
    retVals.toArray
  }
  private def localGetAllKeyValues(transId: Long, containerName: String): Array[MessageContainerBase] = {
    /*
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
*/
    return Array[MessageContainerBase]()
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
    val results = new ArrayBuffer[(String, (Long, String, List[(String, String, String)]))]()
    val buildAdapOne = (k: Key, v: Value) => {
      buildAdapterUniqueValue(k, v, results)
    }
    try {
      _defaultDataStore.get("AdapterUniqKvData", Array(TimeRange(KvBaseDefalts.defaultTime, KvBaseDefalts.defaultTime)), Array(Array(key)), buildAdapOne)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Data not found for key:" + key + "\nStackTrace:" + stackTrace)
      }
    }
    if (results.size > 0) {
      _adapterUniqKeyValData.synchronized {
        _adapterUniqKeyValData(key) = results(0)._2
      }
      return results(0)._2
    }
    return null
  }

  private def localGetModelsResult(transId: Long, key: List[String]): scala.collection.mutable.Map[String, SavedMdlResult] = {
    val k = Key(KvBaseDefalts.defaultTime, key.toArray, 0L, 0)
    val txnCtxt = getTransactionContext(transId, false)
    if (txnCtxt != null) {
      val v = txnCtxt.getModelsResult(k)
      if (v != null) return v
    }

    val v = _modelsResult.getOrElse(k, null)
    if (v != null) return v

    var objs = new Array[(Key, scala.collection.mutable.Map[String, SavedMdlResult])](1)
    val buildMdlOne = (k: Key, v: Value) => { buildModelsResult(k, v, objs) }
    try {
      _defaultDataStore.get("ModelResults", Array(TimeRange(KvBaseDefalts.defaultTime, KvBaseDefalts.defaultTime)), Array(key.toArray), buildMdlOne)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Data not found for key:" + key.mkString(",") + "\nStackTrace:" + stackTrace)
      }
    }
    if (objs(0) != null) {
      _modelsResult.synchronized {
        _modelsResult(objs(0)._1) = objs(0)._2
      }
      return objs(0)._2
    }
    return scala.collection.mutable.Map[String, SavedMdlResult]()
  }

  /**
   * Does at least one of the supplied keys exist in a container with the supplied name?
   */
  private def localContainsAny(transId: Long, containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): Boolean = {
    /*
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
*/
    false
  }
  /**
   * Do all of the supplied keys exist in a container with the supplied name?
   */
  private def localContainsAll(transId: Long, containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): Boolean = {
    /*
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
*/
    false
  }

  private def collectKeyAndValues(k: Key, v: Value, container: MsgContainerInfo): Unit = {
    logger.debug("Key:(%d, %s, %d, %d), Value Info:(Ser:%s, Size:%d)".format(k.timePartition, k.bucketKey.mkString(","), k.transactionId, k.rowId, v.serializerType, v.serializedInfo.size))
    val value = SerializeDeserialize.Deserialize(v.serializedInfo, _mdres, _classLoader, true, "")
    val primarykey = value.PrimaryKeyData
    val key = KeyWithBucketIdAndPrimaryKey(KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(k.bucketKey), k, primarykey != null && primarykey.size > 0, primarykey)
    val v1 = MessageContainerBaseWithModFlag(false, value)
    container.dataByBucketKey.put(key, v1)
    container.dataByTmPart.put(key, v1)

    val bucketId = KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(k.bucketKey)
    val loadKey = LoadKeyWithBucketId(bucketId, TimeRange(k.timePartition, k.timePartition), k.bucketKey)
    container.loadedKeys.add(loadKey)
  }

  // Same kind of code is there in localGetObject
  private def LoadDataIfNeeded(txnCtxt: TransactionContext, transId: Long, containerName: String, tmRangeValues: Array[TimeRange], partKeys: Array[Array[String]]): Unit = {
    if (tmRangeValues.size == partKeys.size) {
      val container = txnCtxt.getMsgContainer(containerName.toLowerCase, true) // adding if not there
      if (container != null) {
        val buildOne = (k: Key, v: Value) => {
          collectKeyAndValues(k, v, container)
        }

        for (i <- 0 until tmRangeValues.size) {
          val bk = partKeys(i)
          val tr = tmRangeValues(i)
          if (TxnContextCommonFunctions.IsEmptyKey(bk) == false) {
            // println("1. containerName:" + containerName)
            val bucketId = KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(bk)
            val tr1 = if (tr != null) tr else TimeRange(Long.MinValue, Long.MaxValue)

            val loadKey = LoadKeyWithBucketId(bucketId, tr1, bk)

            if (container.loadedKeys.contains(loadKey) == false) {
              try {
                logger.debug("Table %s Key %s for timerange: (%d,%d)".format(containerName, loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime))
                if (tr != null)
                  _defaultDataStore.get(containerName, Array(tr), Array(bk), buildOne)
                else
                  _defaultDataStore.get(containerName, Array(bk), buildOne)
                container.loadedKeys.add(loadKey)
              } catch {
                case e: ObjectNotFoundException => {
                  val stackTrace = StackTrace.ThrowableTraceString(e)
                  logger.debug("Table %s Key %s Not found for timerange: (%d,%d). Message:%s, Cause:%s \nStackTrace:%s".format(containerName, loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime, e.getMessage(), e.getCause(), stackTrace))
                }
                case e: Exception => {
                  val stackTrace = StackTrace.ThrowableTraceString(e)
                  logger.error("Table %s Key %s Not found for timerange: (%d,%d). Message:%s, Cause:%s \nStackTrace:%s".format(containerName, loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime, e.getMessage(), e.getCause(), stackTrace))
                }
              }
            }
          } else if (tr != null) {
            // println("2. containerName:" + containerName)
            val bucketId = KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(Array[String]())
            val loadKey = LoadKeyWithBucketId(bucketId, tr, Array[String]())
            if (container.loadedKeys.contains(loadKey) == false) {
              try {
                logger.debug("Table %s Key %s for timerange: (%d,%d)".format(containerName, loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime))
                _defaultDataStore.get(containerName, Array(tr), buildOne)
                container.loadedKeys.add(loadKey)
              } catch {
                case e: ObjectNotFoundException => {
                  val stackTrace = StackTrace.ThrowableTraceString(e)
                  logger.debug("Table %s Key %s Not found for timerange: (%d,%d). Message:%s, Cause:%s \nStackTrace:%s".format(containerName, loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime, e.getMessage(), e.getCause(), stackTrace))
                }
                case e: Exception => {
                  val stackTrace = StackTrace.ThrowableTraceString(e)
                  logger.error("Table %s Key %s Not found for timerange: (%d,%d). Message:%s, Cause:%s \nStackTrace:%s".format(containerName, loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime, e.getMessage(), e.getCause(), stackTrace))
                }
              }
            }
          } else {
            // println("3. containerName:" + containerName)
            val bucketId = KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(Array[String]())
            val loadKey = LoadKeyWithBucketId(bucketId, TimeRange(Long.MinValue, Long.MaxValue), Array[String]())
            if (container.loadedKeys.contains(loadKey) == false) {
              try {
                logger.debug("Table %s Key %s for timerange: (%d,%d)".format(containerName, loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime))
                _defaultDataStore.get(containerName, buildOne)
                container.loadedKeys.add(loadKey)
              } catch {
                case e: ObjectNotFoundException => {
                  val stackTrace = StackTrace.ThrowableTraceString(e)
                  logger.debug("Table %s Key %s Not found for timerange: (%d,%d). Message:%s, Cause:%s \nStackTrace:%s".format(containerName, loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime, e.getMessage(), e.getCause(), stackTrace))
                }
                case e: Exception => {
                  val stackTrace = StackTrace.ThrowableTraceString(e)
                  logger.error("Table %s Key %s Not found for timerange: (%d,%d). Message:%s, Cause:%s \nStackTrace:%s".format(containerName, loadKey.bucketKey.mkString(","), loadKey.tmRange.beginTime, loadKey.tmRange.endTime, e.getMessage(), e.getCause(), stackTrace))
                }
              }
            }
          }
        }
      }
    } else {
      logger.error("All time partition value, bucket keys & values should have the same count in arrays. tmRangeValues.size(%d), partKeys.size(%d)".format(tmRangeValues.size, partKeys.size))
    }
  }

  // tmValues, partKeys & values are kind of triplate. So, we should have same size for all those
  private def localSetObject(transId: Long, containerName: String, tmValues: Array[Long], partKeys: Array[Array[String]], values: Array[MessageContainerBase]): Unit = {
    val txnCtxt = getTransactionContext(transId, true)
    if (txnCtxt != null) {
      // Try to load the key(s) if they exists in global storage.
      LoadDataIfNeeded(txnCtxt, transId, containerName, tmValues.map(t => TimeRange(t, t)), partKeys)
      txnCtxt.setObjects(containerName, tmValues, partKeys, values)
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
  /*
  private def collectKey(key: Key, keys: ArrayBuffer[KamanjaDataKey]): Unit = {
    implicit val jsonFormats: Formats = DefaultFormats
    val parsed_key = parse(new String(key.toArray)).extract[KamanjaDataKey]
    keys += parsed_key
  }
*/
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
    if (_defaultDataStore != null)
      _defaultDataStore.Shutdown
    _defaultDataStore = null

    if (_statusinfoDataStore != null)
      _statusinfoDataStore.Shutdown
    _statusinfoDataStore = null

    // _messagesOrContainers.clear
  }

  /*
  private def getTableKeys(all_keys: ArrayBuffer[KamanjaDataKey], objName: String): Array[KamanjaDataKey] = {
    val tmpObjName = objName.toLowerCase
    all_keys.filter(k => k.T.compareTo(objName) == 0).toArray
  }
*/

  override def getPropertyValue(clusterId: String, key: String): String = {
    _mgr.GetUserProperty(clusterId, key)
  }

  override def SetJarPaths(jarPaths: collection.immutable.Set[String]): Unit = {
    if (jarPaths != null) {
      logger.debug("JarPaths:%s".format(jarPaths.mkString(",")))
    }
    _jarPaths = jarPaths
  }

  override def SetDefaultDatastore(dataDataStoreInfo: String): Unit = {
    if (dataDataStoreInfo != null)
      logger.debug("DefaultDatastore Information:%s".format(dataDataStoreInfo))
    if (_defaultDataStore == null) { // Doing it only once
      _defaultDataStore = GetDataStoreHandle(_jarPaths, dataDataStoreInfo)
    }
  }

  override def SetStatusInfoDatastore(statusDataStoreInfo: String): Unit = {
    if (statusDataStoreInfo != null)
      logger.debug("DefaultDatastore Information:%s".format(statusDataStoreInfo))
    if (_statusinfoDataStore == null) { // Doing it only once
      _statusinfoDataStore = GetDataStoreHandle(_jarPaths, statusDataStoreInfo)
    }
  }

  // Adding new messages or Containers
  override def RegisterMessageOrContainers(containersInfo: Array[ContainerNameAndDatastoreInfo]): Unit = {
    /*
    if (containersInfo != null)
      logger.info("Messages/Containers:%s".format(containersInfo.map(ci => (if (ci.containerName != null) ci.containerName else "", if (ci.dataDataStoreInfo != null) ci.dataDataStoreInfo else "")).mkString(",")))

    containersInfo.foreach(ci => {
      val c = ci.containerName.toLowerCase
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

          if (ci.dataDataStoreInfo != null)
            newMsgOrContainer.dataStore = GetDataStoreHandle(_jarPaths, ci.dataDataStoreInfo)
          else
            newMsgOrContainer.dataStore = _defaultDataStore

          /** create a map to cache the entries to be resurrected from the mapdb */
          _messagesOrContainers(objFullName) = newMsgOrContainer
        }
      } else {
        var error_msg = "Message/Container %s not found".format(c)
        logger.error(error_msg)
        throw new Exception(error_msg)
      }
    })
*/
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
    // Clone(localGetAllObjects(transId, containerName))
    localGetAllObjects(transId, containerName)
  }

  override def getObject(transId: Long, containerName: String, partKey: List[String], primaryKey: List[String]): MessageContainerBase = {
    // Clone(localGetObject(transId, containerName, partKey, primaryKey))
    localGetObject(transId, containerName, partKey, primaryKey)
  }

  override def getHistoryObjects(transId: Long, containerName: String, partKey: List[String], appendCurrentChanges: Boolean): Array[MessageContainerBase] = {
    // Clone(localHistoryObjects(transId, containerName, partKey, appendCurrentChanges))
    localHistoryObjects(transId, containerName, partKey, appendCurrentChanges)
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
    if (value != null && TxnContextCommonFunctions.IsEmptyKey(partKey) == false)
      localSetObject(transId, containerName, Array(value.TimePartitionData), Array(partKey.toArray), Array(value))
  }

  override def setAdapterUniqueKeyValue(transId: Long, key: String, value: String, outputResults: List[(String, String, String)]): Unit = {
    localSetAdapterUniqueKeyValue(transId, key, value, outputResults)
  }

  override def saveModelsResult(transId: Long, key: List[String], value: scala.collection.mutable.Map[String, SavedMdlResult]): Unit = {
    localSaveModelsResult(transId, key, value)
  }

  override def getChangedData(tempTransId: Long, includeMessages: Boolean, includeContainers: Boolean): scala.collection.immutable.Map[String, Array[Key]] = {
    val changedContainersData = scala.collection.mutable.Map[String, Array[Key]]()

    // Commit Data and Removed Transaction information from status
    val txnCtxt = getTransactionContext(tempTransId, false)
    if (txnCtxt == null)
      return changedContainersData.toMap

    val messagesOrContainers = txnCtxt.getAllMessagesAndContainers

    messagesOrContainers.foreach(v => {
      // val mc = _messagesOrContainers.getOrElse(v._1, null)
      val canConsiderThis = ((includeMessages && includeContainers) ||
        (includeMessages && /* v._2.containerType.tTypeType == ObjTypeType.tContainer && */ ( /* mc.isContainer == false && */ v._2.isContainer == false)) ||
        (includeContainers && /* v._2.containerType.tTypeType == ObjTypeType.tContainer && */ ( /* mc.isContainer || */ v._2.isContainer)))

      if (canConsiderThis) {
        var foundPartKeys = new ArrayBuffer[Key](v._2.dataByTmPart.size())
        var it1 = v._2.dataByTmPart.entrySet().iterator()
        while (it1.hasNext()) {
          val entry = it1.next();
          foundPartKeys += entry.getKey().key
        }
        if (foundPartKeys.size > 0)
          changedContainersData(v._1) = foundPartKeys.toArray
      }
    })

    return changedContainersData.toMap
  }

  // Final Commit for the given transaction
  // BUGBUG:: For now we are committing all the data into default datastore. Not yet handled message level datastore.
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
    val modelsResult = if (txnCtxt != null) txnCtxt.getAllModelsResult else Map[Key, scala.collection.mutable.Map[String, SavedMdlResult]]()

    if (_kryoSer == null) {
      _kryoSer = SerializerManager.GetSerializer("kryo")
      if (_kryoSer != null && _classLoader != null) {
        _kryoSer.SetClassLoader(_classLoader)
      }
    }

    val bos = new ByteArrayOutputStream(1024 * 1024)
    val dos = new DataOutputStream(bos)

    val commiting_data = ArrayBuffer[(String, Array[(Key, Value)])]()
    val dataForContainer = ArrayBuffer[(Key, Value)]()

    messagesOrContainers.foreach(v => {
      dataForContainer.clear
      //       val mc = _messagesOrContainers.getOrElse(v._1, null)
      var it1 = v._2.dataByTmPart.entrySet().iterator()
      while (it1.hasNext()) {
        val entry = it1.next();
        val v = entry.getValue()
        if (v.modified) {
          val k = entry.getKey()
          bos.reset
          SerializeDeserialize.Serialize(v.value, dos)
          dataForContainer += ((k.key, Value("manual", bos.toByteArray)))
        }
      }

      // mc.dataByTmPart.putAll(v._2.dataByTmPart) // Assigning new data
      // mc.dataByBucketKey.putAll(v._2.dataByTmPart) // Assigning new data

      // v._2.current_msg_cont_data.clear
      if (dataForContainer.size > 0)
        commiting_data += ((v._1, dataForContainer.toArray))
    })

    dataForContainer.clear
    adapterUniqKeyValData.foreach(v1 => {
      _adapterUniqKeyValData(v1._1) = v1._2
      val json = ("T" -> v1._2._1) ~
        ("V" -> v1._2._2) ~
        ("Qs" -> v1._2._3.map(qsres => { qsres._1 })) ~
        ("Res" -> v1._2._3.map(qsres => { qsres._2 }))
      val compjson = compact(render(json))
      dataForContainer += ((Key(KvBaseDefalts.defaultTime, Array(v1._1), 0, 0), Value("json", compjson.getBytes("UTF8"))))
    })
    if (dataForContainer.size > 0)
      commiting_data += (("AdapterUniqKvData", dataForContainer.toArray))

    dataForContainer.clear
    modelsResult.foreach(v1 => {
      _modelsResult(v1._1) = v1._2
      dataForContainer += ((v1._1, Value("kryo", _kryoSer.SerializeObjectToByteArray(v1._2))))
    })
    if (dataForContainer.size > 0)
      commiting_data += (("ModelResults", dataForContainer.toArray))

    /*
    dataForContainer.clear
    if (adapterUniqKeyValData.size > 0) {
      adapterUniqKeyValData.foreach(v1 => {
        if (v1._2._3 != null && v1._2._3.size > 0) { // If we have output then only commit this, otherwise ignore 

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
          dataForContainer += ((Key(KvBaseDefalts.defaultTime, Array(v1._1), 0, 0), Value("json", compjson.getBytes("UTF8"))))
        }
      })
      commiting_data += (("UK", dataForContainer.toArray))
    }
*/

    dos.close()
    bos.close()

    try {
      logger.debug("Going to commit data into datastore.")
      commiting_data.foreach(cd => {
        cd._2.foreach(kv => {
          logger.debug("ObjKey:(%d, %s, %d, %d), Value Info:(Ser:%s, Size:%d)".format(kv._1.timePartition, kv._1.bucketKey.mkString(","), kv._1.transactionId, kv._1.rowId, kv._2.serializerType, kv._2.serializedInfo.size))
        })
      })
      _defaultDataStore.put(commiting_data.toArray)
    } catch {
      case e: Exception => {
        logger.error("Failed to write data")
        throw e
      }
    }

    // Remove the current transaction
    removeTransactionContext(transId)
  }

  /*
  // Set Reload Flag
  override def setReloadFlag(transId: Long, containerName: String): Unit = {
    // BUGBUG:: Set Reload Flag
    val txnCtxt = getTransactionContext(transId, true)
    if (txnCtxt != null) {
      txnCtxt.setReloadFlag(containerName)
    }
  }
*/

  // Clear Intermediate results before Restart processing
  //BUGBUG:: May be we need to lock before we do anything here
  override def clearIntermediateResults: Unit = {
    /*
    _messagesOrContainers.foreach(v => {
      v._2.dataByBucketKey.clear()
      v._2.dataByTmPart.clear()
      // v._2.current_msg_cont_data.clear
    })
*/

    _adapterUniqKeyValData.clear

    _modelsResult.clear
  }

  // Clear Intermediate results After updating them on different node or different component (like KVInit), etc
  //BUGBUG:: May be we need to lock before we do anything here
  def clearIntermediateResults(unloadMsgsContainers: Array[String]): Unit = {
    if (unloadMsgsContainers == null)
      return
    /*
    unloadMsgsContainers.foreach(mc => {
      val msgCont = _messagesOrContainers.getOrElse(mc.trim.toLowerCase, null)
      if (msgCont != null) {
        if (msgCont.dataByBucketKey != null)
          msgCont.dataByBucketKey.clear()
        if (msgCont.dataByTmPart != null)
          msgCont.dataByTmPart.clear()
        /*
          if (msgCont.current_msg_cont_data != null)
          msgCont.current_msg_cont_data.clear
*/
      }
    })
*/
  }

  // Get Status information from Final table
  override def getAllAdapterUniqKvDataInfo(keys: Array[String]): Array[(String, (Long, String, List[(String, String, String)]))] = {
    val results = new ArrayBuffer[(String, (Long, String, List[(String, String, String)]))]()

    val buildAdapOne = (k: Key, v: Value) => {
      buildAdapterUniqueValue(k, v, results)
    }

    _defaultDataStore.get("AdapterUniqKvData", Array(TimeRange(KvBaseDefalts.defaultTime, KvBaseDefalts.defaultTime)), keys.map(k => Array(k)), buildAdapOne)

    logger.debug("Loaded %d committing informations".format(results.size))
    results.toArray
  }

  /*
  // Save Current State of the machine
  override def PersistLocalNodeStateEntries: Unit = {
    // BUGBUG:: Persist all state on this node.
  }

  // Save Remaining State of the machine
  override def PersistRemainingStateEntriesOnLeader: Unit = {
    // BUGBUG:: Persist Remaining state (when other nodes goes down, this helps)
  }
*/

  override def PersistValidateAdapterInformation(validateUniqVals: Array[(String, String)]): Unit = {
    val ukvs = validateUniqVals.map(kv => {
      (Key(KvBaseDefalts.defaultTime, Array(kv._1), 0L, 0), Value("", kv._2.getBytes("UTF8")))
    })

    _defaultDataStore.put(Array(("ValidateAdapterPartitionInfo", ukvs)))
  }

  private def buildValidateAdapInfo(k: Key, v: Value, results: ArrayBuffer[(String, String)]): Unit = {
    results += ((k.bucketKey(0), new String(v.serializedInfo)))
  }

  override def GetValidateAdapterInformation: Array[(String, String)] = {
    logger.debug(s"GetValidateAdapterInformation() -- Entered")
    val results = ArrayBuffer[(String, String)]()
    val collectorValidateAdapInfo = (k: Key, v: Value) => {
      buildValidateAdapInfo(k, v, results)
    }
    _defaultDataStore.get("CheckPointInformation", Array(TimeRange(KvBaseDefalts.defaultTime, KvBaseDefalts.defaultTime)), collectorValidateAdapInfo)
    logger.debug("Loaded %d Validate (Check Point) Adapter Information".format(results.size))
    results.toArray
  }

  private def collectKeyAndValues(k: Key, v: Value, readValues: ArrayBuffer[(Key, MessageContainerBase)]): Unit = {
    val o = buildObject(k, v)
    readValues += ((k, o))
  }

  override def ReloadKeys(tempTransId: Long, containerName: String, keys: List[Key]): Unit = {
    /*
    val container = _messagesOrContainers.getOrElse(containerName.toLowerCase, null)
    if (container != null) {
      val readValues = ArrayBuffer[(Key, MessageContainerBase)]()
      val buildOne = (k: Key, v: Value) => {
        collectKeyAndValues(k, v, readValues)
      }
      _defaultDataStore.get(containerName, keys.toArray, buildOne)

      readValues.foreach(kv => {
        val primkey = kv._2.PrimaryKeyData
        val putkey = KeyWithBucketIdAndPrimaryKey(KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(kv._1.bucketKey), kv._1, primkey != null && primkey.size > 0, primkey)
        // container.dataByBucketKey.put(putkey, kv._2)
        // container.dataByTmPart.put(putkey, kv._2)
      })
    }
*/
  }

  private def getLocalRecent(transId: Long, containerName: String, partKey: List[String], tmRange: TimeRange, f: MessageContainerBase => Boolean): Option[MessageContainerBase] = {
    if (TxnContextCommonFunctions.IsEmptyKey(partKey))
      return None

    val txnCtxt = getTransactionContext(transId, true)
    if (txnCtxt != null) {
      val (v, foundPartKey) = txnCtxt.getRecent(containerName, partKey, tmRange, null, f)
      if (foundPartKey) {
        return Some(v)
      }
      if (v != null) return Some(v) // It must be null. Without finding partition key it should not find the primary key
    }

    if (txnCtxt != null) {
      // Try to load the key(s) if they exists in global storage.
      LoadDataIfNeeded(txnCtxt, transId, containerName, Array(tmRange), Array(partKey.toArray))
    }

    if (txnCtxt != null) {
      val (v, foundPartKey) = txnCtxt.getRecent(containerName, partKey, tmRange, null, f)
      if (foundPartKey) {
        return Some(v)
      }
      if (v != null) return Some(v) // It must be null. Without finding partition key it should not find the primary key
    }

    /*
    val container = _messagesOrContainers.getOrElse(containerName.toLowerCase, null)

    val (v, foundPartKey) = TxnContextCommonFunctions.getRecent(container, partKey, tmRange, null, f)

    if (foundPartKey)
      return Some(v)

    if (container != null) { // Loading for partition id
      val readValues = ArrayBuffer[(Key, MessageContainerBase)]()
      val buildOne = (k: Key, v: Value) => {
        collectKeyAndValues(k, v, readValues)
      }

      val tmRng = if (tmRange != null) Array(tmRange) else Array[TimeRange]()
      val prtKeys = if (partKey != null) Array(partKey.toArray) else Array[Array[String]]()

      _defaultDataStore.get(containerName, tmRng, prtKeys, buildOne)

      readValues.foreach(kv => {
        val primkey = kv._2.PrimaryKeyData
        val putkey = KeyWithBucketIdAndPrimaryKey(KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(kv._1.bucketKey), kv._1, primkey != null && primkey.size > 0, primkey)
        // container.dataByBucketKey.put(putkey, kv._2)
        // container.dataByTmPart.put(putkey, kv._2)
      })

      val (v, foundPartKey) = TxnContextCommonFunctions.getRecent(container, partKey, tmRange, null, f)

      if (foundPartKey)
        return Some(v)

      return None // If partition key exists, tried DB also, no need to go down
    }
*/

    None
  }

  override def getRecent(transId: Long, containerName: String, partKey: List[String], tmRange: TimeRange, f: MessageContainerBase => Boolean): Option[MessageContainerBase] = {
    // Clone(getLocalRecent(transId, containerName, partKey, tmRange, f))
    getLocalRecent(transId, containerName, partKey, tmRange, f)
  }

  override def getRDD(transId: Long, containerName: String, partKey: List[String], tmRange: TimeRange, f: MessageContainerBase => Boolean): Array[MessageContainerBase] = {
    // Clone(getLocalRDD(transId, containerName, partKey, tmRange, f))
    getLocalRDD(transId, containerName, partKey, tmRange, f)
  }

  private def getLocalRDD(transId: Long, containerName: String, partKey: List[String], tmRange: TimeRange, f: MessageContainerBase => Boolean): Array[MessageContainerBase] = {
    // if (TxnContextCommonFunctions.IsEmptyKey(partKey))
    //  return Array[MessageContainerBase]()

    val txnCtxt = getTransactionContext(transId, true)
    if (txnCtxt != null) {
      // Try to load the key(s) if they exists in global storage.
      LoadDataIfNeeded(txnCtxt, transId, containerName, Array(tmRange), if (partKey != null) Array(partKey.toArray) else Array(null))
    }

    val foundPartKeys = ArrayBuffer[Key]()
    val retResult = ArrayBuffer[MessageContainerBase]()
    if (txnCtxt != null) {
      val (res, foundPartKeys1) = txnCtxt.getRddData(containerName, partKey, tmRange, null, f, foundPartKeys.toArray)
      if (foundPartKeys1.size > 0) {
        foundPartKeys ++= foundPartKeys1
        retResult ++= res
        if (TxnContextCommonFunctions.IsEmptyKey(partKey) == false) // Already found the key, no need to go down
          return retResult.toArray
      }
    }
    /*
    val container = _messagesOrContainers.getOrElse(containerName.toLowerCase, null)
    // In memory
    if (container != null) {
      val (res1, foundPartKeys2) = TxnContextCommonFunctions.getRddData(container, partKey, tmRange, null, f, foundPartKeys.toArray)
      if (foundPartKeys2.size > 0) {
        foundPartKeys ++= foundPartKeys2
        retResult ++= res1 // Add only if we find all here
        if (TxnContextCommonFunctions.IsEmptyKey(partKey) == false) // Already found the key, no need to go down
          return retResult.toArray
      }
    }

    /*
    if (container != null && TxnContextCommonFunctions.IsEmptyKey(partKey) == false) { // Loading for partition id
      // If not found in memory, try in DB
      val loadedFfData = loadObjFromDb(transId, container, partKey)
      if (loadedFfData != null) {
        val res2 = TxnContextCommonFunctions.getRddDataFromKamanjaData(loadedFfData, tmRange, f)
        retResult ++= res2
      }
      return retResult.toArray
    }
*/

    if (container != null) {
      // Need to get all keys for this message/container and take all the data 
      val all_keys = ArrayBuffer[MessageContainerBase]() // All keys for all tables for now

      val readValues = ArrayBuffer[(Key, MessageContainerBase)]()
      val buildOne = (k: Key, v: Value) => {
        collectKeyAndValues(k, v, readValues)
      }

      val tmRng = if (tmRange != null) Array(tmRange) else Array[TimeRange]()
      val prtKeys = if (partKey != null) Array(partKey.toArray) else Array[Array[String]]()

      try {
        _defaultDataStore.get(containerName, tmRng, prtKeys, buildOne)
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

      readValues.foreach(kv => {
        val primkey = kv._2.PrimaryKeyData
        val putkey = KeyWithBucketIdAndPrimaryKey(KeyWithBucketIdAndPrimaryKeyCompHelper.BucketIdForBucketKey(kv._1.bucketKey), kv._1, primkey != null && primkey.size > 0, primkey)
        // container.dataByBucketKey.put(putkey, kv._2)
        // container.dataByTmPart.put(putkey, kv._2)
      })
    }
*/
    return retResult.toArray
  }

  override def saveOne(transId: Long, containerName: String, partKey: List[String], value: MessageContainerBase): Unit = {
    if (value != null && TxnContextCommonFunctions.IsEmptyKey(partKey) == false) {
      localSetObject(transId, containerName, Array(value.TimePartitionData), Array(partKey.toArray), Array(value))
    }
  }

  override def saveRDD(transId: Long, containerName: String, values: Array[MessageContainerBase]): Unit = {
    if (values == null)
      return

    val tmValues = ArrayBuffer[Long]()
    val partKeyValues = ArrayBuffer[Array[String]]()
    val finalValues = ArrayBuffer[MessageContainerBase]()

    values.foreach(v => {
      if (v != null) {
        tmValues += v.TimePartitionData
        partKeyValues += v.PartitionKeyData
        finalValues += v
      }
    })

    localSetObject(transId, containerName, tmValues.toArray, partKeyValues.toArray, finalValues.toArray)
  }
}