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

package com.ligadata.keyvaluestore

import com.ligadata.StorageBase.{ Key, Value, IStorage, DataStoreOperations, DataStore, Transaction, StorageAdapterObj }
import java.io.File
import java.nio.ByteBuffer
import com.redis.cluster._
import com.redis._
import collection.mutable.WrappedArray
import org.apache.logging.log4j._
import com.ligadata.Exceptions._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.ligadata.Utils.{ KamanjaLoaderInfo }

/*
datastoreConfig should have the following:
	Mandatory Options:
		hostlist/Location

	Optional Options:

*/

class KeyValueRedisTx(val parent: DataStore) extends Transaction {
  override def add(source: IStorage): Unit = { parent.add(source) }
  override def put(source: IStorage): Unit = { parent.put(source) }
  override def get(key: Key, target: IStorage): Unit = { parent.get(key, target) }
  override def get(key: Key, handler: (Value) => Unit): Unit = { parent.get(key, handler) }
  override def del(key: Key): Unit = { parent.del(key) }
  override def del(source: IStorage): Unit = { parent.del(source) }
  override def getAllKeys(handler: (Key) => Unit): Unit = { parent.getAllKeys(handler) }
  override def putBatch(sourceArray: Array[IStorage]): Unit = { parent.putBatch(sourceArray) }
  override def delBatch(keyArray: Array[Key]): Unit = { parent.delBatch(keyArray) }
}

class KeyValueRedis(val kvManagerLoader: KamanjaLoaderInfo, val datastoreConfig: String, val tableName: String) extends DataStore {
  val adapterConfig = if (datastoreConfig != null) datastoreConfig.trim else ""
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)

  if (adapterConfig.size == 0) {
    throw new Exception("Not found valid Redis Configuration.")
  }

  logger.debug("Redis configuration:" + adapterConfig)
  var parsed_json: Map[String, Any] = null
  try {
    val json = parse(adapterConfig)
    if (json == null || json.values == null) {
      logger.error("Failed to parse Redis JSON configuration string:" + adapterConfig)
      throw new Exception("Failed to parse Redis JSON configuration string:" + adapterConfig)
    }
    parsed_json = json.values.asInstanceOf[Map[String, Any]]
  } catch {
    case e: Exception => {
      logger.error("Failed to parse Redis JSON configuration string:%s. Reason:%s Message:%s".format(adapterConfig, e.getCause, e.getMessage))
      throw e
    }
  }

  val hostnames = if (parsed_json.contains("hostlist")) parsed_json.getOrElse("hostlist", "localhost").toString.trim else parsed_json.getOrElse("Location", "localhost:6379").toString.trim 
  val hosts = hostnames.split(",").map(hst => hst.trim).filter(hst => hst.size > 0)
  if (hosts.size == 0) {
    throw new Exception("Not found any valid hosts in hostlist")
  }

  val nodes = new Array[ClusterNode](hosts.size)

  for (i <- 0 until hosts.size) {
    val nodeInfo = hosts(i).split(":")
    if (nodeInfo.size == 2) {
      nodes(i) = ClusterNode("node" + i + "_" + hashCode, nodeInfo(0), nodeInfo(1).toInt)
    } else if (nodeInfo.size == 1) {
      nodes(i) = ClusterNode("node" + i + "_" + hashCode, nodeInfo(0), 6379)
    } else {
      throw new Exception("Expecting hostname/ip and port number as host information in hostslist in the format of hostname:port. But found " + hosts(i) + ". Format hostname:port[,hostname:port...]")
    }
  }

  val cluster = new RedisCluster(new WrappedArray.ofRef(nodes): _*) {
    val keyTag = Some(RegexKeyTag)
  }

  override def add(source: IStorage): Unit = {
    cluster.setnx(source.Key.toArray[Byte], source.Value.toArray[Byte])
  }

  override def put(source: IStorage): Unit = {
    cluster.set(source.Key.toArray[Byte], source.Value.toArray[Byte])
  }

  override def putBatch(sourceArray: Array[IStorage]): Unit = {
    sourceArray.foreach(source => {
      cluster.set(source.Key.toArray[Byte], source.Value.toArray[Byte])
    })
  }

  override def delBatch(keyArray: Array[Key]): Unit = {
    keyArray.foreach(k => {
      cluster.del(k.toArray[Byte])
    })
  }

  override def get(key: Key, handler: (Value) => Unit): Unit = {
    val buffer = cluster.get(key.toArray[Byte]).getOrElse(null)
    // Construct the output value
    // BUGBUG-jh-20140703: There should be a more concise way to get the data
    //
    println("buffer " + buffer)
    val value = new Value
    if (buffer != null) {
      for (b <- buffer.getBytes())
        value += b
    } else {
      throw new KeyNotFoundException("Key Not found")
    }

    handler(value)
  }

  override def get(key: Key, target: IStorage): Unit = {
    val buffer = cluster.get(key.toArray[Byte]).getOrElse(null)

    // Construct the output value
    // BUGBUG-jh-20140703: There should be a more concise way to get the data
    //
    println("buffer.getBytes() " + buffer.getBytes())
    val value = new Value
    if (buffer != null) {
      value ++= buffer.getBytes()
      println("value " + value)
    } else {
      throw new KeyNotFoundException("Key Not found")
    }

    target.Construct(key, value)

  }

  override def del(key: Key): Unit = {
    cluster.del(key.toArray[Byte])
  }

  override def del(source: IStorage): Unit = { del(source.Key) }

  override def beginTx(): Transaction = { new KeyValueRedisTx(this) }

  override def endTx(tx: Transaction): Unit = {}

  override def commitTx(tx: Transaction): Unit = {}

  override def Shutdown(): Unit = {
    cluster.close
  }

  override def TruncateStore(): Unit = {
    cluster.flushdb
  }

  override def getAllKeys(handler: (Key) => Unit): Unit = {

    val bufferlist = cluster.keys().get
    if (bufferlist != null)
      bufferlist.foreach { buffer =>
        val key = new Key
        for (b <- buffer.get.getBytes())
          key += b
        handler(key)
        println(key)

      }
  }
}

// To create Redis Datastore instance
object KeyValueRedis extends StorageAdapterObj {
  override def CreateStorageAdapter(kvManagerLoader: KamanjaLoaderInfo, datastoreConfig: String, tableName: String): DataStore = new KeyValueRedis(kvManagerLoader, datastoreConfig, tableName)
}

