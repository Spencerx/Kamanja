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

package com.ligadata.keyvaluestore.mapdb

import org.mapdb._;
import com.ligadata.keyvaluestore._
import java.io.File;
import java.nio.ByteBuffer
import org.apache.logging.log4j._
import com.ligadata.Exceptions._
import com.ligadata.Exceptions.StackTrace

/*

No schema setup

 */

class KeyValueHashMapTx(owner: DataStore) extends Transaction {
  var parent: DataStore = owner

  def add(source: IStorage) = { owner.add(source) }
  def put(source: IStorage) = { owner.put(source) }
  def get(key: Key, target: IStorage) = { owner.get(key, target) }
  def get(key: Key, handler: (Value) => Unit) = { owner.get(key, handler) }
  def del(key: Key) = { owner.del(key) }
  def del(source: IStorage) = { owner.del(source) }
  def getAllKeys(handler: (Key) => Unit) = { owner.getAllKeys(handler) }
  def putBatch(sourceArray: Array[IStorage]) = { owner.putBatch(sourceArray) }
  def delBatch(keyArray: Array[Key]) = { owner.delBatch(keyArray) }
}

class KeyValueHashMap(parameter: PropertyMap) extends DataStore {
  var path = parameter.getOrElse("path", ".")
  var keyspace = parameter.getOrElse("schema", "default")
  var table = parameter.getOrElse("table", "default")

  val InMemory = parameter.getOrElse("inmemory", "false").toBoolean
  val withTransactions = parameter.getOrElse("withtransaction", "false").toBoolean

  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)

  var db: DB = null

  if (InMemory == true) {
    db = DBMaker.newMemoryDB()
      .make()
  } else {
    val dir = new File(path);
    if (!dir.exists()){
      // attempt to create the directory here
      dir.mkdir();
    }
    db = DBMaker.newFileDB(new File(path + "/" + keyspace + ".hdb"))
      .closeOnJvmShutdown()
      .mmapFileEnable()
      .transactionDisable()
      .commitFileSyncDisable()
      .make()
  }

  var map = db.createHashMap(table)
    .hasher(Hasher.BYTE_ARRAY)
    .makeOrGet[Array[Byte], Array[Byte]]()

  def add(source: IStorage) =
    {
      map.putIfAbsent(source.Key.toArray[Byte], source.Value.toArray[Byte])
      if (withTransactions)
        db.commit() //persist changes into disk
    }

  def put(source: IStorage) =
    {
      map.put(source.Key.toArray[Byte], source.Value.toArray[Byte])
      if (withTransactions)
        db.commit() //persist changes into disk
    }

  def putBatch(sourceArray: Array[IStorage]) =
    {
      sourceArray.foreach( source => {
	map.put(source.Key.toArray[Byte], source.Value.toArray[Byte])
      })
      if (withTransactions)
	db.commit() //persist changes into disk
    }

  def delBatch(keyArray: Array[Key]) =
  {
    keyArray.foreach( k => {
      map.remove(k.toArray[Byte])
    })
    if (withTransactions)
      db.commit() //persist changes into disk
  }

  def get(key: Key, handler: (Value) => Unit) =
    {
      val buffer = map.get(key.toArray[Byte])

      // Construct the output value
      val value = new Value
      if (buffer != null) {
        value ++= buffer
      } else {
        throw new KeyNotFoundException("Key Not found")
      }

      handler(value)
    }

  def get(key: Key, target: IStorage) =
    {
      val buffer = map.get(key.toArray[Byte])

      // Construct the output value
      val value = new Value
      if (buffer != null) {
        value ++= buffer
      } else {
        throw new KeyNotFoundException("Key Not found")
      }

      target.Construct(key, value)
    }

  def del(key: Key) =
    {
      map.remove(key.toArray[Byte])
      if (withTransactions)
        db.commit(); //persist changes into disk
    }

  def del(source: IStorage) = { del(source.Key) }

  def beginTx(): Transaction = { new KeyValueHashMapTx(this) }

  def endTx(tx: Transaction) = {}

  def commitTx(tx: Transaction) = {}

  override def Shutdown() = {
    if( db != null && db.isClosed() == false ){
      logger.debug("Trying to shutdown hashmap db")
      try{
	db.commit(); //persist changes into disk
	db = null
	map.close();
      }catch{
	case e:NullPointerException =>{
    
	  logger.error("Unexpected Null pointer exception when closing hashmap, seems like internal bug related to mapdb ")
	}
	case e:Exception =>{
    
	  logger.error("Unexpected error when closing hashmap " + e.getMessage())
	}
      }
    }
  }

  def TruncateStore() {
    map.clear()
    if (withTransactions)
      db.commit() //persist changes into disk

    // Defrag on startup
    db.compact()
  }

  def getAllKeys(handler: (Key) => Unit) =
    {
      var iter = map.keySet().iterator()
      while (iter.hasNext()) {
        val buffer = iter.next()

        // Construct the output value
        // BUGBUG-jh-20140703: There should be a more concise way to get the data
        //
        val key = new Key
        for (b <- buffer)
          key += b

        handler(key)
      }
    }
}

