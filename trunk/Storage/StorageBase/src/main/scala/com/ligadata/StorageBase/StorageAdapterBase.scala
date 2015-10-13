/*
 * This interface is primarily used for implementing storage adapters where
 * row data is partitioned by a time stamp column and also contains one or more key
 * columns
 *
 */
package com.ligadata.StorageBase

import com.ligadata.KvBase.{ Key, Value, TimeRange }
import com.ligadata.Utils.{ KamanjaLoaderInfo }
import java.util.Date

trait DataStoreOperations {
  // update operations, add & update semantics are different for relational databases
  def put(containerName: String, key: Key, value: Value): Unit
  // def put(containerName: String, data_list: Array[(Key, Value)]): Unit
  def put(data_list: Array[(String, Array[(Key, Value)])]): Unit // data_list has List of container names, and each container has list of key & value

  // delete operations
  def del(containerName: String, keys: Array[Key]): Unit // For the given keys, delete the values
  def del(containerName: String, time: TimeRange, keys: Array[Array[String]]): Unit // For the given multiple bucket key strings, delete the values with in given date range

  // get operations
  def get(containerName: String, callbackFunction: (Key, Value) => Unit): Unit
  def get(containerName: String, keys: Array[Key], callbackFunction: (Key, Value) => Unit): Unit
  def get(containerName: String, timeRanges: Array[TimeRange], callbackFunction: (Key, Value) => Unit): Unit // Range of dates
  def get(containerName: String, timeRanges: Array[TimeRange], bucketKeys: Array[Array[String]], callbackFunction: (Key, Value) => Unit): Unit
  def get(containerName: String, bucketKeys: Array[Array[String]], callbackFunction: (Key, Value) => Unit): Unit

/*
  // Passing filter to storage
  def get(containerName: String, filterFunction: (Key, Value) => Boolean, callbackFunction: (Key, Value) => Unit): Unit
  def get(containerName: String, timeRanges: Array[TimeRange], filterFunction: (Key, Value) => Boolean, callbackFunction: (Key, Value) => Unit): Unit // Range of dates
  def get(containerName: String, timeRanges: Array[TimeRange], bucketKeys: Array[Array[String]], filterFunction: (Key, Value) => Boolean, callbackFunction: (Key, Value) => Unit): Unit
  def get(containerName: String, bucketKeys: Array[Array[String]], filterFunction: (Key, Value) => Boolean, callbackFunction: (Key, Value) => Unit): Unit
*/

  // getKeys operations similar to get, but only key values
  def getKeys(containerName: String, callbackFunction: (Key) => Unit): Unit
  def getKeys(containerName: String, keys: Array[Key], callbackFunction: (Key) => Unit): Unit
  def getKeys(containerName: String, timeRanges: Array[TimeRange], callbackFunction: (Key) => Unit): Unit // Range of dates
  def getKeys(containerName: String, timeRanges: Array[TimeRange], bucketKeys: Array[Array[String]], callbackFunction: (Key) => Unit): Unit
  def getKeys(containerName: String, bucketKeys: Array[Array[String]], callbackFunction: (Key) => Unit): Unit
}

trait DataStore extends DataStoreOperations {
  def beginTx(): Transaction
  def endTx(tx: Transaction): Unit // Same as commit
  def commitTx(tx: Transaction): Unit
  def rollbackTx(tx: Transaction): Unit

  // clean up operations
  def Shutdown(): Unit
  def TruncateContainer(containerNames: Array[String]): Unit
  def DropContainer(containerNames: Array[String]): Unit
  def CreateContainer(containerNames: Array[String]): Unit
}

trait Transaction extends DataStoreOperations {
  val parent: DataStore // Parent Data Store
}

// Storage Adapter Object to create storage adapter
trait StorageAdapterObj {
  def CreateStorageAdapter(kvManagerLoader: KamanjaLoaderInfo, datastoreConfig: String): DataStore
}
