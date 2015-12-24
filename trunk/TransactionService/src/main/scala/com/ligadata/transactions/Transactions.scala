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

package com.ligadata.transactions

import com.ligadata.KamanjaBase.{ EnvContext }

import org.apache.logging.log4j.{ Logger, LogManager }
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scala.collection.mutable.ArrayBuffer

import com.ligadata.ZooKeeper._
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.locks.InterProcessMutex
import com.ligadata.StorageBase.{ DataStore, Transaction }
import com.ligadata.keyvaluestore.KeyValueManager
import com.ligadata.KvBase.{ Key, Value, TimeRange, KvBaseDefalts }

object NodeLevelTransService {
  private[this] val LOG = LogManager.getLogger(getClass);
  private[this] var startTxnRangeIdx: Long = 1
  private[this] var endTxnRangeIdx: Long = 0
  private[this] val _lock = new Object
  private[this] var zkcForGetRange: CuratorFramework = null
  private[this] var zkConnectString: String = ""
  private[this] var zkSessionTimeoutMs: Int = 0
  private[this] var zkConnectionTimeoutMs: Int = 0
  private[this] var zkDistributeLockBasePath: String = ""
  private[this] var txnIdsRangeForNode: Int = 0
  private[this] var dataDataStoreInfo: String = ""
  private[this] var initialized: Boolean = false
  private[this] var txnsDataStore: DataStore = null
  private[this] var jarPaths: collection.immutable.Set[String] = null
  private[this] var _serInfoBufBytes = 32

  private def GetDataStoreHandle(jarPaths: collection.immutable.Set[String], dataStoreInfo: String, tableName: String): DataStore = {
    try {
      LOG.debug("Getting DB Connection for dataStoreInfo:%s".format(dataStoreInfo))
      return KeyValueManager.Get(jarPaths, dataStoreInfo)
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new Exception(e.getMessage())
      }
    }
  }

  private def buildTxnStartOffset(k: Key, v: Value, objs: Array[Long]) {
    val uniqVal = new String(v.serializedInfo).toLong
    objs(0) = uniqVal
  }

  // We are not locking anything here. Just pulling from db and returning as requested. 
  private def getNextTransactionRange(requestedRange: Int): (Long, Long) = {
    if (requestedRange <= 0) {
      return (0, -1)
    }

    val containerName = "GlobalCounters"
    val bucket_key = Array[String]("TransactionId")

    var startTxnIdx: Long = 0
    var endTxnIdx: Long = -1
    var objs: Array[Long] = new Array[Long](1)
    try {
      if (txnsDataStore == null) {
        throw new Exception("Not found Status DataStore to save Status.")
      }

      val buildTxnOff = (k: Key, v: Value) => {
        buildTxnStartOffset(k, v, objs)
      }

      try {
        objs(0) = 0
        txnsDataStore.get(containerName, Array(TimeRange(KvBaseDefalts.defaultTime, KvBaseDefalts.defaultTime)), Array(bucket_key), buildTxnOff)
        startTxnIdx = objs(0)
      } catch {
        case e: Exception => LOG.debug("Key %s not found. Reason:%s, Message:%s".format(bucket_key.mkString(","), e.getCause, e.getMessage))
        case t: Throwable => LOG.debug("Key %s not found. Reason:%s, Message:%s".format(bucket_key.mkString(","), t.getCause, t.getMessage))
      }

      if (startTxnIdx <= 0)
        startTxnIdx = 1
      endTxnIdx = startTxnIdx + requestedRange - 1

      // Persists next start transactionId
      var cntr = 0
      val nextTxnStart = endTxnIdx + 1

      val k = Key(KvBaseDefalts.defaultTime, bucket_key, 0L, 0)
      val v = Value("", nextTxnStart.toString.getBytes())
      val txn = txnsDataStore.beginTx()
      txnsDataStore.put(containerName, k, v)
      txnsDataStore.commitTx(txn)
    } catch {
      case e: Exception => {
        LOG.debug(s"getNextTransactionRange() -- Unable to get Next Transaction Range")
      }
    }
    (startTxnIdx, endTxnIdx)
  }

  def init(zkConnectString1: String, zkSessionTimeoutMs1: Int, zkConnectionTimeoutMs1: Int, zkDistributeLockBasePath1: String, txnIdsRangeForNode1: Int, dataDataStoreInfo1: String, jarPaths1: collection.immutable.Set[String]): Unit = {
    zkConnectString = zkConnectString1
    zkSessionTimeoutMs = zkSessionTimeoutMs1
    zkConnectionTimeoutMs = zkConnectionTimeoutMs1
    zkDistributeLockBasePath = zkDistributeLockBasePath1
    txnIdsRangeForNode = txnIdsRangeForNode1
    dataDataStoreInfo = dataDataStoreInfo1
    jarPaths = jarPaths1
    initialized = true
  }

  def getNextTransRange(requestedPartitionRange: Int): (Long, Long) = _lock.synchronized {
    if (requestedPartitionRange <= 0) {
      return (0, -1)
    }
    LOG.info("Start Requesting another Range %d for Partition".format(requestedPartitionRange))
    if (startTxnRangeIdx > endTxnRangeIdx) {
      if (initialized == false) {
        throw new Exception("Transaction Service is not yet initialized")
      }
      LOG.info("Start Requesting another Range %d for Node from Storage".format(requestedPartitionRange))

      // Connect to DB if not connected already
      if (txnsDataStore == null) {
        LOG.debug("Transaction DB Info:" + dataDataStoreInfo)
        txnsDataStore = GetDataStoreHandle(jarPaths, dataDataStoreInfo, "ClusterCounts")
        if (txnsDataStore == null)
          throw new Exception("Unable to connect to DataStore with " + dataDataStoreInfo)
      }

      // Do Distributed zookeeper lock here
      if (zkcForGetRange == null) {
        CreateClient.CreateNodeIfNotExists(zkConnectString, zkDistributeLockBasePath) // Creating 
        zkcForGetRange = CreateClient.createSimple(zkConnectString, zkSessionTimeoutMs, zkConnectionTimeoutMs)
      }
      if (zkcForGetRange == null)
        throw new Exception("Failed to connect to Zookeeper with connection string:" + zkConnectString)
      val lockPath = zkDistributeLockBasePath + "/distributed-transaction-lock"

      try {
        val lock = new InterProcessMutex(zkcForGetRange, lockPath);
        try {
          lock.acquire();
          val (startRng, endRng) = getNextTransactionRange(txnIdsRangeForNode)
          startTxnRangeIdx = startRng
          endTxnRangeIdx = endRng
        } catch {
          case e: Exception => throw e
        } finally {
          if (lock != null)
            lock.release();
        }

      } catch {
        case e: Exception => throw e
      } finally {
      }

      LOG.info("Done Requesting another Range %d for Node from Storage".format(requestedPartitionRange))
    }
    val retStartIdx = startTxnRangeIdx
    val retMaxEndIdx = startTxnRangeIdx + requestedPartitionRange - 1
    val retEndIdx = if (retMaxEndIdx > endTxnRangeIdx) endTxnRangeIdx else retMaxEndIdx
    startTxnRangeIdx = retEndIdx + 1
    LOG.info("Done Requesting another Range %d for Partition".format(requestedPartitionRange))
    return (retStartIdx, retEndIdx)
  }

  def Shutdown = _lock.synchronized {
    if (zkcForGetRange != null)
      zkcForGetRange.close()
    zkcForGetRange = null
    if (txnsDataStore != null)
      txnsDataStore.Shutdown
    txnsDataStore = null
  }

}

class SimpleTransService {
  private[this] val LOG = LogManager.getLogger(getClass);
  private[this] var startTxnRangeIdx: Long = 1
  private[this] var endTxnRangeIdx: Long = 0
  private[this] var txnIdsRangeForPartition: Int = 1

  def init(txnIdsRangeForPartition1: Int): Unit = {
    txnIdsRangeForPartition = txnIdsRangeForPartition1
  }

  def getNextTransId: Long = {
    if (startTxnRangeIdx > endTxnRangeIdx) {
      val (startRng, endRng) = NodeLevelTransService.getNextTransRange(txnIdsRangeForPartition)
      startTxnRangeIdx = startRng
      endTxnRangeIdx = endRng
    }
    val retval = startTxnRangeIdx
    startTxnRangeIdx += 1
    retval
  }
}
