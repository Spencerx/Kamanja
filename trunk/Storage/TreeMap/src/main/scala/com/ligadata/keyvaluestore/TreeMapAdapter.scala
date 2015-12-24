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

/*
datastoreConfig should have the following:
	Mandatory Options:
		path/Location

	Optional Options:
		inmemory
		withtransaction

		All the optional values may come from "AdapterSpecificConfig" also. That is the old way of giving more information specific to Adapter
*/

/*

No schema setup

 */


package com.ligadata.keyvaluestore

import com.ligadata.KvBase.{ Key, Value, TimeRange }
import com.ligadata.StorageBase.{ DataStore, Transaction, StorageAdapterObj }

import org.mapdb._
import java.io._
import java.nio.ByteBuffer
import org.apache.logging.log4j._
import com.ligadata.Exceptions._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.ligadata.Utils.{KamanjaLoaderInfo}

class TreeMapAdapter(val kvManagerLoader: KamanjaLoaderInfo, val datastoreConfig: String) extends DataStore {
  val adapterConfig = if (datastoreConfig != null) datastoreConfig.trim else ""
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
  private[this] val lock = new Object
  private var containerList: scala.collection.mutable.Set[String] = scala.collection.mutable.Set[String]()

  private var dbStoreMap: scala.collection.mutable.Map[String,DB] = new scala.collection.mutable.HashMap()

  private var tablesMap: scala.collection.mutable.Map[String,BTreeMap[Array[Byte], Array[Byte]]] = new scala.collection.mutable.HashMap()

  if (adapterConfig.size == 0) {
    throw new Exception("Not found valid TreeMap Configuration.")
  }

  logger.debug("TreeMap configuration:" + adapterConfig)
  var parsed_json: Map[String, Any] = null
  try {
    val json = parse(adapterConfig)
    if (json == null || json.values == null) {
      logger.error("Failed to parse TreeMap JSON configuration string:" + adapterConfig)
      throw new Exception("Failed to parse TreeMap JSON configuration string:" + adapterConfig)
    }
    parsed_json = json.values.asInstanceOf[Map[String, Any]]
  } catch {
    case e: Exception => {
      logger.error("Failed to parse TreeMap JSON configuration string:%s. Reason:%s Message:%s".format(adapterConfig, e.getCause, e.getMessage))
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

  val path = if (parsed_json.contains("path")) parsed_json.getOrElse("path", ".").toString.trim else parsed_json.getOrElse("Location", ".").toString.trim
  val InMemory = getOptionalField("inmemory", parsed_json, adapterSpecificConfig_json, "false").toString.trim.toBoolean
  val withTransactions = getOptionalField("withtransaction", parsed_json, adapterSpecificConfig_json, "false").toString.trim.toBoolean

  private def createTable(tableName: String): Unit = {
    var db: DB = null
    if (InMemory == true) {
      db = DBMaker.newMemoryDB().make()
    } else {
      val dir = new File(path);
      if (!dir.exists()) {
	// attempt to create the directory here
	dir.mkdir();
      }
      db = DBMaker.newFileDB(new File(path + "/" + tableName + ".db"))
	.closeOnJvmShutdown()
	.mmapFileEnable()
	.transactionDisable()
	.commitFileSyncDisable()
	.make()
      dbStoreMap.put(tableName,db)
      var map = db.createTreeMap(tableName)
	.comparator(Fun.BYTE_ARRAY_COMPARATOR)
	.makeOrGet[Array[Byte], Array[Byte]]();
      tablesMap.put(tableName,map)
    }
  }

  @throws(classOf[FileNotFoundException])
  def deleteFile(file:File):Unit = {
    if(file.exists()){
      var ret = true
      if (file.isDirectory){
	for(f <- file.listFiles) {
          deleteFile(f)
	}
      }
      logger.debug("cleanup: Deleting file '" + file + "'")
      file.delete()
    }
  }

  private def dropTable(tableName: String): Unit = {
    var f = new File(path + "/" + tableName + ".db")
    deleteFile(f)
    f = new File(path + "/" + tableName + ".db.p")
    deleteFile(f)
  }

  private def CheckTableExists(containerName: String): Unit = {
    if (containerList.contains(containerName)) {
      return
    } else {
      CreateContainer(containerName)
      containerList.add(containerName)
    }
  }

  private def toTableName(containerName: String): String = {
    // we need to check for other restrictions as well
    // such as length of the table, special characters etc
    //containerName.replace('.','_')
    containerName.toLowerCase.replace('.', '_').replace('-', '_')
  }

  private def toFullTableName(containerName: String): String = {
    // we need to check for other restrictions as well
    // such as length of the table, special characters etc
    toTableName(containerName)
  }

  private def CreateContainer(containerName: String): Unit = lock.synchronized {
    var tableName = toTableName(containerName)
    var fullTableName = toFullTableName(containerName)
    try {
      createTable(fullTableName)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def CreateContainer(containerNames: Array[String]): Unit = {
    logger.info("create the container tables")
    containerNames.foreach(cont => {
      logger.info("create the container " + cont)
      CreateContainer(cont)
    })
  }

  private def MakeCompositeKey(key: Key): Array[Byte] = {
    var compKey = key.timePartition.toString + "|" + key.bucketKey.mkString(".") + 
		  "|" + key.transactionId.toString + "|" + key.rowId.toString
    compKey.getBytes()
  }


  private def ValueToByteArray(value: Value): Array[Byte] = {
    var byteOs = new ByteArrayOutputStream();
    var out = new DataOutputStream(byteOs);
    out.writeInt(value.serializerType.length);
    out.write(value.serializerType.getBytes());
    out.writeInt(value.serializedInfo.length);
    out.write(value.serializedInfo);
    byteOs.toByteArray()
  }

  private def ByteArrayToValue(byteArray: Array[Byte]): Value = {
    var in = new DataInputStream(new ByteArrayInputStream(byteArray));

    var serializerTypeLength = in.readInt();
    var serializerType = new Array[Byte](serializerTypeLength);
    in.read(serializerType, 0, serializerTypeLength);

    var serializedInfoLength = in.readInt();
    var serializedInfo = new Array[Byte](serializedInfoLength);
    in.read(serializedInfo, 0, serializedInfoLength);

    var v = new Value(new String(serializerType), serializedInfo)
    v
  }

  private def Commit: Unit = {
    //if (withTransactions)
    dbStoreMap.foreach(db => {
      logger.debug("Committing transactions for db " + db._1)
      db._2.commit();
    })
    //db.commit() //persist changes into disk
  }

  override def put(containerName: String, key: Key, value: Value): Unit = {
    var tableName = toFullTableName(containerName)
    try{
      CheckTableExists(containerName)
      var kba = MakeCompositeKey(key)
      var vba = ValueToByteArray(value)
      var map = tablesMap(tableName)
      map.put(kba,vba)
      Commit
    } catch {
      case e:Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:"+stackTrace)
	throw new Exception("Failed to save an object in TreeMap table " + tableName + ":" + e.getMessage())
      }
    }
  }

  override def put(data_list: Array[(String, Array[(Key, Value)])]): Unit = {
    try{
      data_list.foreach(li => {
        var containerName = li._1
	CheckTableExists(containerName)
	var tableName = toFullTableName(containerName)
	var map = tablesMap(tableName)
        var keyValuePairs = li._2
        keyValuePairs.foreach(keyValuePair => {
          var key = keyValuePair._1
          var value = keyValuePair._2
	  var kba = MakeCompositeKey(key)
	  var vba = ValueToByteArray(value)
	  map.put(kba,vba)
	})
      })
      Commit
   } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  // delete operations
  override def del(containerName: String, keys: Array[Key]): Unit = {
    try{
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      var map = tablesMap(tableName)      
      keys.foreach(key => {
	var kba = MakeCompositeKey(key)
	map.remove(kba)
      })
      Commit
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }


  override def del(containerName: String, time: TimeRange, bucketKeys: Array[Array[String]]): Unit = {
    try{
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      var bucketKeyMap: scala.collection.mutable.Map[String,Boolean] = new scala.collection.mutable.HashMap()      
      bucketKeys.foreach(bucketKey => {
	var bkey = bucketKey.mkString(".")
	bucketKeyMap.put(bkey,true)
      })
	
      var map = tablesMap(tableName)      
      var iter = map.keySet().iterator()
      while (iter.hasNext()) {
	val kba = iter.next()
	val k = new String(kba)
	var keyArray = k.split('|')
	var tp = keyArray(0).toLong
	var bkey = keyArray(1)
	if ( tp >= time.beginTime && tp <= time.endTime ){
	  logger.info("searching for " + keyArray(1))
	  var keyExists = bucketKeyMap.getOrElse(bkey,null)
	  if (keyExists != null ){
	    map.remove(kba)
	  }
	}
      }
      Commit
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  // get operations
  def getRowCount(containerName: String): Long = {
    var tableName = toFullTableName(containerName)
    var map = tablesMap(tableName)      
    var iter = map.keySet().iterator()
    var cnt = 0
    while (iter.hasNext()) {
      val kba = iter.next()
      cnt = cnt + 1
    }
    return cnt
  }

  private def processRow(k:String,value:Value,callbackFunction: (Key, Value) => Unit){
    var keyArray = k.split('|').toArray
    var timePartition = keyArray(0).toLong
    var keyStr = keyArray(1)
    var tId = keyArray(2).toLong
    var rId = keyArray(3).toInt
    // format the data to create Key/Value
    val bucketKey = if (keyStr != null) keyStr.split('.').toArray else new Array[String](0)
    var key = new Key(timePartition, bucketKey, tId, rId)
    (callbackFunction)(key, value)
  }

  private def processRow(key: Key, value:Value,callbackFunction: (Key, Value) => Unit){
    (callbackFunction)(key, value)
  }

  private def processKey(k: String,callbackFunction: (Key) => Unit){
    var keyArray = k.split('|').toArray
    var timePartition = keyArray(0).toLong
    var keyStr = keyArray(1)
    var tId = keyArray(2).toLong
    var rId = keyArray(3).toInt
    // format the data to create Key/Value
    val bucketKey = if (keyStr != null) keyStr.split('.').toArray else new Array[String](0)
    var key = new Key(timePartition, bucketKey, tId, rId)
    (callbackFunction)(key)
  }

  override def get(containerName: String, callbackFunction: (Key, Value) => Unit): Unit = {
    try{
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      var map = tablesMap(tableName)      
      var iter = map.keySet().iterator()
      while (iter.hasNext()) {
	val kba = iter.next()
	val k = new String(kba)
	var vba = map.get(kba)
	var v = ByteArrayToValue(vba)
	processRow(k,v,callbackFunction)
      }
    }catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def getKeys(containerName: String, callbackFunction: (Key) => Unit): Unit = {
    try{
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      var map = tablesMap(tableName)      
      var iter = map.keySet().iterator()
      while (iter.hasNext()) {
	val kba = iter.next()
	val k = new String(kba)
	processKey(k,callbackFunction)
      }
    }catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def getKeys(containerName: String, keys: Array[Key], callbackFunction: (Key) => Unit): Unit = {
    try{
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      var map = tablesMap(tableName)      
      keys.foreach(key => {
	var kba = MakeCompositeKey(key)
	var k = new String(kba)
	var vba = map.get(kba)
	if( vba != null ){
	  processKey(k,callbackFunction)
	}
      })
    }catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def get(containerName: String, keys: Array[Key], callbackFunction: (Key, Value) => Unit): Unit = {
    try{
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      var map = tablesMap(tableName)      
      keys.foreach(key => {
	var kba = MakeCompositeKey(key)
	var vba = map.get(kba)
	if( vba != null ){
	  var value = ByteArrayToValue(vba)
	  processRow(key,value,callbackFunction)
	}
      })
    }catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def get(containerName: String, time_ranges: Array[TimeRange], callbackFunction: (Key, Value) => Unit): Unit = {
    try{
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      var map = tablesMap(tableName)      

      var iter = map.keySet().iterator()
      while (iter.hasNext()) {
	val kba = iter.next()
	val key = new String(kba)
	time_ranges.foreach(timeRange => {
	  var keyArray = key.split('|')
	  var tp = keyArray(0).toLong
	  if ( tp >= timeRange.beginTime && tp <= timeRange.endTime ){	
	    var vba = map.get(kba)
	    if( vba != null ){
	      var value = ByteArrayToValue(vba)
	      processRow(key,value,callbackFunction)
	    }
	  }
	})
      }
    }catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def getKeys(containerName: String, time_ranges: Array[TimeRange], callbackFunction: (Key) => Unit): Unit = {
    try{
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      var map = tablesMap(tableName)      

      var iter = map.keySet().iterator()
      while (iter.hasNext()) {
	val kba = iter.next()
	val key = new String(kba)
	time_ranges.foreach(timeRange => {
	  var keyArray = key.split('|')
	  var tp = keyArray(0).toLong
	  if ( tp >= timeRange.beginTime && tp <= timeRange.endTime ){	
	    processKey(key,callbackFunction)
	  }
	})
      }
    }catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }


  override def get(containerName: String, time_ranges: Array[TimeRange], bucketKeys: Array[Array[String]], callbackFunction: (Key, Value) => Unit): Unit = {
    try{
      var bucketKeyMap: scala.collection.mutable.Map[String,Boolean] = new scala.collection.mutable.HashMap()      
      bucketKeys.foreach(bucketKey => {
	var bkey = bucketKey.mkString(".")
	bucketKeyMap.put(bkey,true)
      })

      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      var map = tablesMap(tableName)      

      var iter = map.keySet().iterator()
      while (iter.hasNext()) {
	val kba = iter.next()
	val key = new String(kba)
	time_ranges.foreach(timeRange => {
	  var keyArray = key.split('|')
	  var tp = keyArray(0).toLong
	  if ( tp >= timeRange.beginTime && tp <= timeRange.endTime ){	
	    var bkey = keyArray(1)
	    var keyExists = bucketKeyMap.getOrElse(bkey,null)
	    if( keyExists != null ){
	      var vba = map.get(kba)
	      if( vba != null ){
		var value = ByteArrayToValue(vba)
		processRow(key,value,callbackFunction)
	      }
	    }
	  }
	})
      }
    }catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def getKeys(containerName: String, time_ranges: Array[TimeRange], bucketKeys: Array[Array[String]], callbackFunction: (Key) => Unit): Unit = {
    try{
      var bucketKeyMap: scala.collection.mutable.Map[String,Boolean] = new scala.collection.mutable.HashMap()      
      bucketKeys.foreach(bucketKey => {
	var bkey = bucketKey.mkString(".")
	bucketKeyMap.put(bkey,true)
      })

      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      var map = tablesMap(tableName)      

      var iter = map.keySet().iterator()
      while (iter.hasNext()) {
	val kba = iter.next()
	val key = new String(kba)
	time_ranges.foreach(timeRange => {
	  var keyArray = key.split('|')
	  var tp = keyArray(0).toLong
	  if ( tp >= timeRange.beginTime && tp <= timeRange.endTime ){	
	    var bkey = keyArray(1)
	    var keyExists = bucketKeyMap.getOrElse(bkey,null)
	    if( keyExists != null ){
	      processKey(key,callbackFunction)
	    }
	  }
	})
      }
    }catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def get(containerName: String, bucketKeys: Array[Array[String]], callbackFunction: (Key, Value) => Unit): Unit = {
    try{
      var bucketKeyMap: scala.collection.mutable.Map[String,Boolean] = new scala.collection.mutable.HashMap()      
      bucketKeys.foreach(bucketKey => {
	var bkey = bucketKey.mkString(".")
	bucketKeyMap.put(bkey,true)
      })

      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      var map = tablesMap(tableName)      

      var iter = map.keySet().iterator()
      while (iter.hasNext()) {
	val kba = iter.next()
	val key = new String(kba)
	var keyArray = key.split('|')
	var bkey = keyArray(1)
	var keyExists = bucketKeyMap.getOrElse(bkey,null)
	if( keyExists != null ){
	  var vba = map.get(kba)
	  if( vba != null ){
	    var value = ByteArrayToValue(vba)
	    processRow(key,value,callbackFunction)
	  }
	}
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def getKeys(containerName: String, bucketKeys: Array[Array[String]], callbackFunction: (Key) => Unit): Unit = {
    try{
      var bucketKeyMap: scala.collection.mutable.Map[String,Boolean] = new scala.collection.mutable.HashMap()      
      bucketKeys.foreach(bucketKey => {
	var bkey = bucketKey.mkString(".")
	bucketKeyMap.put(bkey,true)
      })

      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      var map = tablesMap(tableName)      

      var iter = map.keySet().iterator()
      while (iter.hasNext()) {
	val kba = iter.next()
	val key = new String(kba)
	var keyArray = key.split('|')
	var bkey = keyArray(1)
	var keyExists = bucketKeyMap.getOrElse(bkey,null)
	if( keyExists != null ){
	  processKey(key,callbackFunction)
	}
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def beginTx(): Transaction = {
    new TreeMapAdapterTx(this)
  }

  override def endTx(tx: Transaction): Unit = {}

  override def commitTx(tx: Transaction): Unit = {}

  override def rollbackTx(tx: Transaction): Unit = {}

  override def Shutdown(): Unit = {
    logger.info("Trying to shutdown treemap db")
    try {
      logger.info("Commit any outstanding transactions")
      dbStoreMap.foreach(db => {
	logger.info("Committing transactions for db " + db._1)
	db._2.commit();
      })
      logger.info("Close all map objects")
      tablesMap.foreach(map => {
	logger.info("Closing the map " + map._1)
        map._2.close();
      })
    } catch {
      case e: NullPointerException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }
  
  private def TruncateContainer(containerName: String): Unit = {
    try{
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      var map = tablesMap(tableName)
      var iter = map.keySet().iterator()
      while (iter.hasNext()) {
	val kba = iter.next()
	map.remove(kba)
      }
      Commit
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def TruncateContainer(containerNames: Array[String]): Unit = {
    logger.info("truncate the container tables")
    containerNames.foreach(cont => {
      logger.info("truncate the container " + cont)
      TruncateContainer(cont)
    })
  }

  private def DropContainer(containerName: String): Unit = lock.synchronized {
    try {
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      dropTable(tableName)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def DropContainer(containerNames: Array[String]): Unit = {
    logger.info("drop the container tables")
    containerNames.foreach(cont => {
      logger.info("drop the container " + cont)
      DropContainer(cont)
    })
  }
}

class TreeMapAdapterTx(val parent: DataStore) extends Transaction {

  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)

  override def put(containerName: String, key: Key, value: Value): Unit = {
    parent.put(containerName, key, value)
  }

  override def put(data_list: Array[(String, Array[(Key, Value)])]): Unit = {
    parent.put(data_list)
  }

  // delete operations
  override def del(containerName: String, keys: Array[Key]): Unit = {
    parent.del(containerName, keys)
  }

  override def del(containerName: String, time: TimeRange, keys: Array[Array[String]]): Unit = {
    parent.del(containerName, time, keys)
  }

  // get operations
  override def get(containerName: String, callbackFunction: (Key, Value) => Unit): Unit = {
    parent.get(containerName, callbackFunction)
  }

  override def get(containerName: String, keys: Array[Key], callbackFunction: (Key, Value) => Unit): Unit = {
    parent.get(containerName, keys, callbackFunction)
  }

  override def get(containerName: String, time_ranges: Array[TimeRange], callbackFunction: (Key, Value) => Unit): Unit = {
    parent.get(containerName, time_ranges, callbackFunction)
  }

  override def get(containerName: String, time_ranges: Array[TimeRange], bucketKeys: Array[Array[String]], callbackFunction: (Key, Value) => Unit): Unit = {
    parent.get(containerName, time_ranges, bucketKeys, callbackFunction)
  }
  override def get(containerName: String, bucketKeys: Array[Array[String]], callbackFunction: (Key, Value) => Unit): Unit = {
    parent.get(containerName, bucketKeys, callbackFunction)
  }

  def getKeys(containerName: String, callbackFunction: (Key) => Unit): Unit = {
    parent.getKeys(containerName, callbackFunction)
  }

  def getKeys(containerName: String, keys: Array[Key], callbackFunction: (Key) => Unit): Unit = {
    parent.getKeys(containerName, keys, callbackFunction)
  }
  def getKeys(containerName: String, timeRanges: Array[TimeRange], callbackFunction: (Key) => Unit): Unit = {
    parent.getKeys(containerName, timeRanges, callbackFunction)
  }

  def getKeys(containerName: String, timeRanges: Array[TimeRange], bucketKeys: Array[Array[String]], callbackFunction: (Key) => Unit): Unit = {
    parent.getKeys(containerName, timeRanges, bucketKeys, callbackFunction)
  }

  def getKeys(containerName: String, bucketKeys: Array[Array[String]], callbackFunction: (Key) => Unit): Unit = {
    parent.getKeys(containerName, bucketKeys, callbackFunction)
  }
}

// To create TreeMap Datastore instance
object TreeMapAdapter extends StorageAdapterObj {
  override def CreateStorageAdapter(kvManagerLoader: KamanjaLoaderInfo, datastoreConfig: String): DataStore = new TreeMapAdapter(kvManagerLoader, datastoreConfig)
}
