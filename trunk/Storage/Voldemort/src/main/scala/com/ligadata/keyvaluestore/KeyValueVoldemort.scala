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

// This libraries need to come first or import won't find them
//
import com.ligadata.StorageBase.{ Key, Value, IStorage, DataStoreOperations, DataStore, Transaction, StorageAdapterObj }
import voldemort.client._
import voldemort.client.ClientConfig
import voldemort.client.SocketStoreClientFactory
import voldemort.client.StoreClient
import voldemort.versioning.Versioned;
import org.apache.logging.log4j._
import com.ligadata.Exceptions._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import org.apache.commons.codec.binary.Base64

import java.nio.ByteBuffer
import com.ligadata.Utils.{ KamanjaLoaderInfo }

/*
datastoreConfig should have the following:
	Mandatory Options:
		hostname/Location
		schema/SchemaName

	Optional Options:
		client

		All the optional values may come from "AdapterSpecificConfig" also. That is the old way of giving more information specific to Adapter
*/

class KeyValueVoldemortTx(val parent: DataStore) extends Transaction {
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

class KeyValueVoldemort(val kvManagerLoader: KamanjaLoaderInfo, val datastoreConfig: String, val tableName: String) extends DataStore {
  val adapterConfig = if (datastoreConfig != null) datastoreConfig.trim else ""
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)

  if (adapterConfig.size == 0) {
    throw new Exception("Not found valid Voldemort Configuration.")
  }

  logger.debug("Voldemort configuration:" + adapterConfig)
  var parsed_json: Map[String, Any] = null
  try {
    val json = parse(adapterConfig)
    if (json == null || json.values == null) {
      logger.error("Failed to parse Voldemort JSON configuration string:" + adapterConfig)
      throw new Exception("Failed to parse Voldemort JSON configuration string:" + adapterConfig)
    }
    parsed_json = json.values.asInstanceOf[Map[String, Any]]
  } catch {
    case e: Exception => {
      logger.error("Failed to parse Voldemort JSON configuration string:%s. Reason:%s Message:%s".format(adapterConfig, e.getCause, e.getMessage))
      throw e
    }
  }

  // Getting AdapterSpecificConfig if it has
  var adapterSpecificConfig_json: Map[String, Any] = null

  if (parsed_json.contains("AdapterSpecificConfig")) {
    val adapterSpecificStr = parsed_json.getOrElse("AdapterSpecificConfig", "").toString.trim
    if (adapterSpecificStr.size > 0) {
      try {
        val json = parse(adapterSpecificStr)
        if (json == null || json.values == null) {
          logger.error("Failed to parse Cassandra Adapter Specific JSON configuration string:" + adapterSpecificStr)
          throw new Exception("Failed to parse Cassandra Adapter Specific JSON configuration string:" + adapterSpecificStr)
        }
        adapterSpecificConfig_json = json.values.asInstanceOf[Map[String, Any]]
      } catch {
        case e: Exception => {
          logger.error("Failed to parse Cassandra Adapter Specific JSON configuration string:%s. Reason:%s Message:%s".format(adapterSpecificStr, e.getCause, e.getMessage))
          throw e
        }
      }
    }
  }

  private def getOptionalField(key: String, main_json: Map[String, Any], adapterSpecific_json: Map[String, Any], default: Any): Any = {
    if (main_json != null) {
      val mainVal = main_json.getOrElse(key, null)
      if (mainVal != null)
        return mainVal
    }
    if (adapterSpecific_json != null) {
      val mainVal1 = adapterSpecific_json.getOrElse(key, null)
      if (mainVal1 != null)
        return mainVal1
    }
    return default
  }
  
  val hostname = if (parsed_json.contains("hostname")) parsed_json.getOrElse("hostname", "localhost").toString.trim else parsed_json.getOrElse("Location", "tcp://localhost:6666").toString.trim 
  val keyspace = if (parsed_json.contains("schema")) parsed_json.getOrElse("schema", "default").toString.trim else parsed_json.getOrElse("SchemaName", "default").toString.trim
  val table = tableName
  val clientName = getOptionalField("client", parsed_json, adapterSpecificConfig_json, "test").toString.trim

  val config = new ClientConfig().setBootstrapUrls(hostname)
  val factory = new SocketStoreClientFactory(config)
  val store: StoreClient[String, String] = factory.getStoreClient(clientName) //keyspace + "_" + table)

  // Little helper, I won't have time to reconfigure voldemort right now
  val seperator = "\r\n"
  val seperatorBytes = seperator.getBytes("UTF-8")
  val base64 = new Base64(1024, seperatorBytes, true)
  val base64key = new Base64(1024, Array.empty[Byte], true)

  override def add(source: IStorage): Unit = {
    val key = base64key.encodeToString(source.Key.toArray[Byte])
    val value = base64.encodeToString(source.Value.toArray[Byte])
    store.put(key, value);
  }

  override def put(source: IStorage): Unit = {
    val key = base64key.encodeToString(source.Key.toArray[Byte])
    val value = base64.encodeToString(source.Value.toArray[Byte])
    store.put(key, value);
  }

  override def putBatch(sourceArray: Array[IStorage]): Unit = {
    sourceArray.foreach(source => {
      val key = base64key.encodeToString(source.Key.toArray[Byte])
      val value = base64.encodeToString(source.Value.toArray[Byte])
      store.put(key, value);
    })
  }

  override def delBatch(keyArray: Array[Key]): Unit = {
    keyArray.foreach(k => {
      val key1 = base64key.encodeToString(k.toArray[Byte])
      store.delete(key1)
    })
  }

  override def get(key: Key, handler: (Value) => Unit): Unit = {
    val key1 = base64key.encodeToString(key.toArray[Byte])
    val value1 = store.get(key1).getValue;
    val value2: Array[Byte] = base64.decode(value1)
    val value3 = ByteBuffer.wrap(value2)
    //
    val value = new Value
    value += value3.get()

    handler(value)
  }

  override def get(key: Key, target: IStorage): Unit = {
    val key1 = base64key.encodeToString(key.toArray[Byte])
    val value1 = store.get(key1).getValue;
    val value2: Array[Byte] = base64.decode(value1)
    val value3 = ByteBuffer.wrap(value2)
    //
    val value = new Value
    value += value3.get()

    target.Construct(key, value)
  }

  override def del(key: Key): Unit = {
    val key1 = base64key.encodeToString(key.toArray[Byte])
    store.delete(key1)
  }

  override def del(source: IStorage): Unit = { del(source.Key) }

  override def beginTx(): Transaction = { new KeyValueVoldemortTx(this) }

  override def endTx(tx: Transaction): Unit = {}

  override def commitTx(tx: Transaction): Unit = {}

  override def Shutdown(): Unit = {
    factory.close();
  }

  override def TruncateStore(): Unit = {
    throw new Exception("Don't know how to truncate store")
  }

  override def getAllKeys(handler: (Key) => Unit): Unit = {
    throw new Exception("Don't know how to get all keys")
  }
}

// To create Voldemort Datastore instance
object KeyValueVoldemort extends StorageAdapterObj {
  override def CreateStorageAdapter(kvManagerLoader: KamanjaLoaderInfo, datastoreConfig: String, tableName: String): DataStore = new KeyValueVoldemort(kvManagerLoader, datastoreConfig, tableName)
}

