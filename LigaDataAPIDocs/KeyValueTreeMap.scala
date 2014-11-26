package com.ligadata.keyvaluestore.mapdb

import org.mapdb._
import com.ligadata.keyvaluestore._
import java.io.File
import java.nio.ByteBuffer
import org.mapdb.Fun._;

/*

 */

class KeyValueTreeMapTx(owner : DataStore) extends Transaction
{
	var parent :DataStore = owner

	def add(source: IStorage) = { owner.add(source) }
	def put(source: IStorage) = { owner.put(source) }
	def get(key: Key, target: IStorage) = { owner.get(key, target) }
	def get( key: Key, handler : (Value) => Unit) = { owner.get(key, handler) }
	def del(key: Key) = { owner.del(key) }
	def del(source: IStorage) = { owner.del(source) }
	def getAllKeys( handler : (Key) => Unit) = { owner.getAllKeys(handler) }
	def putBatch(sourceArray: Array[IStorage]) = { owner.putBatch(sourceArray) }
}

class KeyValueTreeMap(parameter: PropertyMap) extends DataStore
{
	var path = parameter.getOrElse("path", ".")
	var keyspace = parameter.getOrElse("schema", "default")
	var table = parameter.getOrElse("table", "default")

	var InMemory = parameter.getOrElse("inmemory", "false")
	val withTransactions = parameter.getOrElse("withtransaction", "false").toBoolean
	
	var db : DB = null

	if(InMemory.toBoolean == true)
	{
	  db = DBMaker.newMemoryDB().make()
	}
	else
	{
	  val dir = new File(path);
	  if (!dir.exists()){
	    // attempt to create the directory here
	    dir.mkdir();
	  }
	  db = DBMaker.newFileDB(new File(path + "/" + keyspace + ".db"))
					.closeOnJvmShutdown()
					.asyncWriteEnable()
					.asyncWriteFlushDelay(100)
					.mmapFileEnable()
					.transactionDisable()
					.commitFileSyncDisable()
					.make()
	}

	var map = db.createTreeMap(table)
				.comparator(Fun.BYTE_ARRAY_COMPARATOR)
				.makeOrGet[Array[Byte], Array[Byte]](); 
	
	def add(source: IStorage) =
	{
		map.putIfAbsent(source.Key.toArray[Byte], source.Value.toArray[Byte])
		if(withTransactions)
			db.commit() //persist changes into disk
	}
	def put(source: IStorage) =
	{
		map.put(source.Key.toArray[Byte], source.Value.toArray[Byte])
		if(withTransactions)
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

	def get(key: Key, handler : (Value) => Unit) =
	{
		val buffer = map.get(key.toArray[Byte])

		// Construct the output value
		// BUGBUG-jh-20140703: There should be a more concise way to get the data
		//
		val value = new Value
		if (buffer != null) {
		  for(b <- buffer)
			value+=b
		} else {
		  throw new KeyNotFoundException("Key Not found")
		}

		handler(value)
	}

	def get(key: Key, target: IStorage)  =
	{
		val buffer = map.get(key.toArray[Byte])

		// Construct the output value
		// BUGBUG-jh-20140703: There should be a more concise way to get the data
		//
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
		if(withTransactions)
			db.commit(); //persist changes into disk
	}

	def del(source: IStorage) = { del(source.Key) }

	def beginTx() : Transaction = { new KeyValueHashMapTx(this) }

	def endTx(tx : Transaction) = {}

	def commitTx(tx : Transaction) = {}

	override def Shutdown() =
	{
		map.close();
	}

	def TruncateStore()
	{
		map.clear()
		if(withTransactions)
			db.commit() //persist changes into disk
			
		// Defrag on startup
		db.compact()
	}

	def getAllKeys( handler : (Key) => Unit) =
	{
		var iter = map.keySet().iterator()
		while(iter.hasNext())
		{
			val buffer = iter.next()

			// Construct the output value
			// BUGBUG-jh-20140703: There should be a more concise way to get the data
			//
			val key = new Key
			for(b <- buffer)
				key+=b

			handler(key)
		}
	}
}

