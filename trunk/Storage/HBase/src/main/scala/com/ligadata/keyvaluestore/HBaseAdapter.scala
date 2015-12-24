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

// Hbase core
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
// hbase client
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.HConnection
import org.apache.hadoop.hbase.client.HConnectionManager
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.HBaseAdmin;
// hbase filters
import org.apache.hadoop.hbase.filter.Filter
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter
import org.apache.hadoop.hbase.filter.FirstKeyOnlyFilter
import org.apache.hadoop.hbase.filter.FilterList
import org.apache.hadoop.hbase.filter.CompareFilter
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp
// hadoop security model
import org.apache.hadoop.security.UserGroupInformation

import org.apache.logging.log4j._
import java.nio.ByteBuffer
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.ligadata.Exceptions._
import com.ligadata.Utils.{KamanjaLoaderInfo}
import com.ligadata.KvBase.{ Key, Value, TimeRange }
import com.ligadata.StorageBase.{ DataStore, Transaction, StorageAdapterObj }
import java.util.{ Date, Calendar, TimeZone }
import java.text.SimpleDateFormat

import scala.collection.JavaConversions._

class HBaseAdapter(val kvManagerLoader: KamanjaLoaderInfo, val datastoreConfig: String) extends DataStore {
  val adapterConfig = if (datastoreConfig != null) datastoreConfig.trim else ""
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
  private[this] val lock = new Object
  private var containerList: scala.collection.mutable.Set[String] = scala.collection.mutable.Set[String]()

  //logger.setLevel(Level.DEBUG)

  if (adapterConfig.size == 0) {
    throw new Exception("Not found valid HBase Configuration.")
  }

  logger.debug("HBase configuration:" + adapterConfig)
  var parsed_json: Map[String, Any] = null
  try {
    val json = parse(adapterConfig)
    if (json == null || json.values == null) {
      logger.error("Failed to parse HBase JSON configuration string:" + adapterConfig)
      throw new Exception("Failed to parse HBase JSON configuration string:" + adapterConfig)
    }
    parsed_json = json.values.asInstanceOf[Map[String, Any]]
  } catch {
    case e: Exception => {
      logger.error("Failed to parse HBase JSON configuration string:%s. Reason:%s Message:%s".format(adapterConfig, e.getCause, e.getMessage))
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

  def CreateNameSpace(nameSpace: String): Unit = {
    relogin
    try{
      val nsd = admin.getNamespaceDescriptor(nameSpace)
      return
    } catch{
      case e: Exception => {
	logger.info("Namespace " + nameSpace + " doesn't exist, create it")
      }
    }
    try{
      admin.createNamespace(NamespaceDescriptor.create(nameSpace).build)
    } catch{
      case e: Exception => {
      val stackTrace = StackTrace.ThrowableTraceString(e)
      logger.debug("StackTrace:"+stackTrace)
      throw new ConnectionFailedException("Unable to create hbase name space " + nameSpace + ":" + e.getMessage())
      }
    }
  }

  val hostnames = if (parsed_json.contains("hostlist")) parsed_json.getOrElse("hostlist", "localhost").toString.trim else parsed_json.getOrElse("Location", "localhost").toString.trim
  val namespace = if (parsed_json.contains("SchemaName")) parsed_json.getOrElse("SchemaName", "default").toString.trim else parsed_json.getOrElse("SchemaName", "default").toString.trim

  val config = HBaseConfiguration.create();

  config.setInt("zookeeper.session.timeout", getOptionalField("zookeeper_session_timeout", parsed_json, adapterSpecificConfig_json, "5000").toString.trim.toInt);
  config.setInt("zookeeper.recovery.retry", getOptionalField("zookeeper_recovery_retry", parsed_json, adapterSpecificConfig_json, "1").toString.trim.toInt);
  config.setInt("hbase.client.retries.number", getOptionalField("hbase_client_retries_number", parsed_json, adapterSpecificConfig_json, "3").toString.trim.toInt);
  config.setInt("hbase.client.pause", getOptionalField("hbase_client_pause", parsed_json, adapterSpecificConfig_json, "5000").toString.trim.toInt);
  config.set("hbase.zookeeper.quorum", hostnames);
  config.setInt("hbase.client.keyvalue.maxsize", getOptionalField("hbase_client_keyvalue_maxsize", parsed_json, adapterSpecificConfig_json, "104857600").toString.trim.toInt);

  var isKerberos: Boolean = false
  var ugi: UserGroupInformation = null

  val auth = getOptionalField("authentication", parsed_json, adapterSpecificConfig_json, "").toString.trim
  if (auth.size > 0) {
    isKerberos = auth.compareToIgnoreCase("kerberos") == 0
    if (isKerberos) {
      try {
        val regionserver_principal = getOptionalField("regionserver_principal", parsed_json, adapterSpecificConfig_json, "").toString.trim
        val master_principal = getOptionalField("master_principal", parsed_json, adapterSpecificConfig_json, "").toString.trim
        val principal = getOptionalField("principal", parsed_json, adapterSpecificConfig_json, "").toString.trim
        val keytab = getOptionalField("keytab", parsed_json, adapterSpecificConfig_json, "").toString.trim

        logger.debug("HBase info => Hosts:" + hostnames + ", Namespace:" + namespace + ", Principal:" + principal + ", Keytab:" + keytab + ", hbase.regionserver.kerberos.principal:" + regionserver_principal + ", hbase.master.kerberos.principal:" + master_principal)

        config.set("hadoop.proxyuser.hdfs.groups", "*")
        config.set("hadoop.security.authorization", "true")
        config.set("hbase.security.authentication", "kerberos")
        config.set("hadoop.security.authentication", "kerberos")
        config.set("hbase.regionserver.kerberos.principal", regionserver_principal)
        config.set("hbase.master.kerberos.principal", master_principal)

        org.apache.hadoop.security.UserGroupInformation.setConfiguration(config);

        UserGroupInformation.loginUserFromKeytab(principal, keytab);

        ugi = UserGroupInformation.getLoginUser
      } catch {
        case e: Exception => {
          logger.error("HBase issue from JSON configuration string:%s. Reason:%s Message:%s".format(adapterConfig, e.getCause, e.getMessage))
          throw e
        }
      }
    } else {
      logger.error("Not handling any authentication other than KERBEROS. AdapterSpecificConfig:" + adapterConfig)
      throw new Exception("Not handling any authentication other than KERBEROS. AdapterSpecificConfig:" + adapterConfig)
    }
  } else {
    logger.debug("HBase info => Hosts:" + hostnames + ", Namespace:" + namespace)
  }

  var connection: HConnection = _
  try {
    connection = HConnectionManager.createConnection(config);
  } catch {
    case e: Exception => {
      val stackTrace = StackTrace.ThrowableTraceString(e)
      logger.error("Stacktrace:" + stackTrace)
      throw new ConnectionFailedException("Unable to connect to hbase at " + hostnames + ":" + e.getMessage())
    }
  }
  val admin = new HBaseAdmin(config);
  CreateNameSpace(namespace)

  private def relogin: Unit = {
    try {
      if (ugi != null)
        ugi.checkTGTAndReloginFromKeytab
    } catch {
      case e: Exception => {
        logger.error("Failed to relogin into HBase. Message:" + e.getMessage())
        // Not throwing exception from here
      }
    }
  }

  private def createTable(tableName: String): Unit = {
    relogin
    if (!admin.tableExists(tableName)) {
      val tableDesc = new HTableDescriptor(TableName.valueOf(tableName));
      val colDesc1 = new HColumnDescriptor("key".getBytes())
      val colDesc2 = new HColumnDescriptor("serializerType".getBytes())
      val colDesc3 = new HColumnDescriptor("serializedInfo".getBytes())
      tableDesc.addFamily(colDesc1)
      tableDesc.addFamily(colDesc2)
      tableDesc.addFamily(colDesc3)
      admin.createTable(tableDesc);
    }
  }

  private def dropTable(tableName: String): Unit = {
    relogin
    if (admin.tableExists(tableName)) {
      admin.disableTable(tableName)
      admin.deleteTable(tableName)
    }
  }

  private def CheckTableExists(containerName: String): Unit = {
    if (containerList.contains(containerName)) {
      return
    } else {
      CreateContainer(containerName)
      containerList.add(containerName)
    }
  }

  def DropNameSpace(namespace: String): Unit = lock.synchronized {
    relogin
    try{
      val nsd = admin.getNamespaceDescriptor(namespace)
    } catch{
      case e: Exception => {
	logger.info("Namespace " + namespace + " doesn't exist, nothing to delete")
	return
      }
    }
    try{
      admin.deleteNamespace(namespace)
    } catch{
      case e: Exception => {
      val stackTrace = StackTrace.ThrowableTraceString(e)
      logger.debug("StackTrace:"+stackTrace)
      throw new ConnectionFailedException("Unable to delete hbase name space " + namespace + ":" + e.getMessage())
      }
    }
  }

  private def toTableName(containerName: String): String = {
    // we need to check for other restrictions as well
    // such as length of the table, special characters etc
    //containerName.replace('.','_')
    namespace + ':' + containerName.toLowerCase.replace('.', '_').replace('-', '_')
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
    
  override def put(containerName: String, key: Key, value: Value): Unit = {
    var tableName = toFullTableName(containerName)
    try{
      relogin
      CheckTableExists(containerName)
      val tableHBase = connection.getTable(tableName);
      var kba = MakeCompositeKey(key)
      var p = new Put(kba)
      p.add(Bytes.toBytes("serializerType"),Bytes.toBytes("base"),Bytes.toBytes(value.serializerType))
      p.add(Bytes.toBytes("serializedInfo"),Bytes.toBytes("base"),value.serializedInfo)
      tableHBase.put(p)
    } catch {
      case e:Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:"+stackTrace)
	throw new Exception("Failed to save an object in HBase table " + tableName + ":" + e.getMessage())
      }
    }
  }

  override def put(data_list: Array[(String, Array[(Key, Value)])]): Unit = {
    try{
      relogin
      data_list.foreach(li => {
        var containerName = li._1
	CheckTableExists(containerName)
	var tableName = toFullTableName(containerName)
	val tableHBase = connection.getTable(tableName);
        var keyValuePairs = li._2
	var puts = new Array[Put](0)
        keyValuePairs.foreach(keyValuePair => {
          var key = keyValuePair._1
          var value = keyValuePair._2
	  var kba = MakeCompositeKey(key)
	  var p = new Put(kba)
	  p.add(Bytes.toBytes("serializerType"),Bytes.toBytes("base"),Bytes.toBytes(value.serializerType))
	  p.add(Bytes.toBytes("serializedInfo"),Bytes.toBytes("base"),value.serializedInfo)
	  puts = puts :+ p
	})
	if( puts.length > 0 ){
	  tableHBase.put(puts.toList)
	}
      })
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
      relogin
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      val tableHBase = connection.getTable(tableName);
      var dels = new Array[Delete](0)

      keys.foreach(key => {
	var kba = MakeCompositeKey(key)
	dels = dels :+ new Delete(kba)
      })

      if( dels.length > 0 ){
	// callling tableHBase.delete(dels.toList) results in an exception as below ??
	//  Stacktrace:java.lang.UnsupportedOperationException
	// at java.util.AbstractList.remove(AbstractList.java:161)
	// at org.apache.hadoop.hbase.client.HTable.delete(HTable.java:896)
	// at com.ligadata.keyvaluestore.HBaseAdapter.del(HBaseAdapter.scala:387)
	val dl = new java.util.ArrayList(dels.toList)
	tableHBase.delete(dl)
      }
      else{
	logger.info("No rows found for the delete operation")
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }


  override def del(containerName: String, time: TimeRange, bucketKeys: Array[Array[String]]): Unit = {
    try{
      relogin
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      val tableHBase = connection.getTable(tableName);
      var bucketKeyMap: scala.collection.mutable.Map[String,Boolean] = new scala.collection.mutable.HashMap()      
      bucketKeys.foreach(bucketKey => {
	var bkey = bucketKey.mkString(".")
	bucketKeyMap.put(bkey,true)
      })
	
      // try scan with beginRow and endRow
      logger.info("beginTime => " + time.beginTime)
      logger.info("endTime => " + time.endTime)

      var scan = new Scan()
      scan.setStartRow(Bytes.toBytes(time.beginTime.toString))
      scan.setStopRow(Bytes.toBytes((time.endTime + 1).toString))
      val rs = tableHBase.getScanner(scan);
      val it = rs.iterator()
      var dels = new Array[Delete](0)
      while( it.hasNext() ){
	val r = it.next()
	var k = Bytes.toString(r.getRow())
	var keyArray = k.split('|')
	logger.info("searching for " + keyArray(1))
	var keyExists = bucketKeyMap.getOrElse(keyArray(1),null)
	if (keyExists != null ){
	  dels = dels :+ new Delete(r.getRow())
	}
      }
      if( dels.length > 0 ){
	val dl = new java.util.ArrayList(dels.toList)
	tableHBase.delete(dl)
	// tableHBase.delete(dels.toList)
      }
      else{
	logger.info("No rows found for the delete operation")
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  // get operations
  def getRowCount(containerName: String): Long = {
    relogin
    var tableName = toFullTableName(containerName)
    val tableHBase = connection.getTable(tableName);

    var scan = new Scan();
    scan.setFilter(new FirstKeyOnlyFilter());
    var rs = tableHBase.getScanner(scan);
    val it = rs.iterator()
    var cnt = 0
    while( it.hasNext() ){
      var r = it.next()
      cnt = cnt + 1
    }
    return cnt
  }

  private def processRow(k:String,st:String, si:Array[Byte],callbackFunction: (Key, Value) => Unit){
    var keyArray = k.split('|').toArray
    var timePartition = keyArray(0).toLong
    var keyStr = keyArray(1)
    var tId = keyArray(2).toLong
    var rId = keyArray(3).toInt
    // format the data to create Key/Value
    val bucketKey = if (keyStr != null) keyStr.split('.').toArray else new Array[String](0)
    var key = new Key(timePartition, bucketKey, tId, rId)
    var value = new Value(st, si)
    (callbackFunction)(key, value)
  }

  private def processRow(key: Key, st:String, si:Array[Byte],callbackFunction: (Key, Value) => Unit){
    var value = new Value(st, si)
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
      relogin
      var tableName = toFullTableName(containerName)
      val tableHBase = connection.getTable(tableName);
      var scan = new Scan();
      var rs = tableHBase.getScanner(scan);
      val it = rs.iterator()
      while( it.hasNext() ){
	val r = it.next()
	var k = Bytes.toString(r.getRow())
	val kvit = r.list().iterator()
	var st:String  = null
	var si:Array[Byte] = null
	while( kvit.hasNext() ){
	  val kv = kvit.next()
	  val q = Bytes.toString(kv.getFamily())
	  q match  {
	    case "serializerType" => {
	      st = Bytes.toString(kv.getValue())
	    }
	    case "serializedInfo" => {
	      si = kv.getValue()
	    }
	  }
	}
	processRow(k,st,si,callbackFunction)
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
      relogin
      var tableName = toFullTableName(containerName)
      val tableHBase = connection.getTable(tableName);
      var scan = new Scan();
      scan.setFilter(new FirstKeyOnlyFilter());
      var rs = tableHBase.getScanner(scan);
      val it = rs.iterator()
      while( it.hasNext() ){
	val r = it.next()
	var k = Bytes.toString(r.getRow())
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
      relogin
      var tableName = toFullTableName(containerName)
      val tableHBase = connection.getTable(tableName);

      val filters = new java.util.ArrayList[Filter]()
      keys.foreach(key => {
	var kba = MakeCompositeKey(key)	
	val f = new SingleColumnValueFilter(Bytes.toBytes("key"), Bytes.toBytes("base"),
						CompareOp.EQUAL, kba)
	filters.add(f);
      })
      val fl = new FilterList(filters);  
      val scan = new Scan();  
      scan.setFilter(fl);  
      val rs = tableHBase.getScanner(scan);
      val it = rs.iterator()
      while( it.hasNext() ){
	val r = it.next()
	var k = Bytes.toString(r.getRow())
	processKey(k,callbackFunction)
      }
    }catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def get(containerName: String, keys: Array[Key], callbackFunction: (Key, Value) => Unit): Unit = {
    try{
      relogin
      var tableName = toFullTableName(containerName)
      val tableHBase = connection.getTable(tableName);

      val filters = new java.util.ArrayList[Filter]()
      keys.foreach(key => {
	var kba = MakeCompositeKey(key)	
	val f = new SingleColumnValueFilter(Bytes.toBytes("key"), Bytes.toBytes("base"),
						CompareOp.EQUAL, kba)
	filters.add(f);
      })
      val fl = new FilterList(filters);  
      val scan = new Scan();  
      scan.setFilter(fl);  
      val rs = tableHBase.getScanner(scan);
      val it = rs.iterator()
      while( it.hasNext() ){
	val r = it.next()
	var k = Bytes.toString(r.getRow())
	val kvit = r.list().iterator()
	var st:String  = null
	var si:Array[Byte] = null
	while( kvit.hasNext() ){
	  val kv = kvit.next()
	  val q = Bytes.toString(kv.getFamily())
	  q match  {
	    case "serializerType" => {
	      st = Bytes.toString(kv.getValue())
	    }
	    case "serializedInfo" => {
	      si = kv.getValue()
	    }
	  }
	}
	processRow(k,st,si,callbackFunction)
      }
    }catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def get(containerName: String, time_ranges: Array[TimeRange], callbackFunction: (Key, Value) => Unit): Unit = {
    try{
      relogin
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      val tableHBase = connection.getTable(tableName);

      time_ranges.foreach(time_range => {	
	// try scan with beginRow and endRow
	var scan = new Scan()
	scan.setStartRow(Bytes.toBytes(time_range.beginTime.toString))
	scan.setStopRow(Bytes.toBytes((time_range.endTime + 1).toString))
	val rs = tableHBase.getScanner(scan);
	val it = rs.iterator()
	while( it.hasNext() ){
	  val r = it.next()
	  var k = Bytes.toString(r.getRow())
	  val kvit = r.list().iterator()
	  var st:String  = null
	  var si:Array[Byte] = null
	  while( kvit.hasNext() ){
	    val kv = kvit.next()
	    val q = Bytes.toString(kv.getFamily())
	    q match  {
	      case "serializerType" => {
		st = Bytes.toString(kv.getValue())
	      }
	      case "serializedInfo" => {
		si = kv.getValue()
	      }
	    }
	  }
	  processRow(k,st,si,callbackFunction)
	}
      })
    }catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def getKeys(containerName: String, time_ranges: Array[TimeRange], callbackFunction: (Key) => Unit): Unit = {
    try{
      relogin
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      val tableHBase = connection.getTable(tableName);

      time_ranges.foreach(time_range => {	
	// try scan with beginRow and endRow
	var scan = new Scan()
	scan.setStartRow(Bytes.toBytes(time_range.beginTime.toString))
	scan.setStopRow(Bytes.toBytes((time_range.endTime + 1).toString))
	val rs = tableHBase.getScanner(scan);
	val it = rs.iterator()
	while( it.hasNext() ){
	  val r = it.next()
	  var k = Bytes.toString(r.getRow())
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


  override def get(containerName: String, time_ranges: Array[TimeRange], bucketKeys: Array[Array[String]], callbackFunction: (Key, Value) => Unit): Unit = {
    try{
      relogin
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      val tableHBase = connection.getTable(tableName);

      var bucketKeyMap: scala.collection.mutable.Map[String,Boolean] = new scala.collection.mutable.HashMap()      
      bucketKeys.foreach(bucketKey => {
	var bkey = bucketKey.mkString(".")
	bucketKeyMap.put(bkey,true)
      })

      time_ranges.foreach(time_range => {	
	// try scan with beginRow and endRow
	var scan = new Scan()
	scan.setStartRow(Bytes.toBytes(time_range.beginTime.toString))
	scan.setStopRow(Bytes.toBytes((time_range.endTime + 1).toString))
	val rs = tableHBase.getScanner(scan);
	val it = rs.iterator()
	while( it.hasNext() ){
	  val r = it.next()
	  var k = Bytes.toString(r.getRow())
	  var keyArray = k.split('|')
	  var keyExists = bucketKeyMap.getOrElse(keyArray(1),null)
	  var st:String  = null
	  var si:Array[Byte] = null
	  if (keyExists != null ){
	    val kvit = r.list().iterator()
	    while( kvit.hasNext() ){
	      val kv = kvit.next()
	      val q = Bytes.toString(kv.getFamily())
	      q match  {
		case "serializerType" => {
		  st = Bytes.toString(kv.getValue())
		}
		case "serializedInfo" => {
		  si = kv.getValue()
		}
	      }
	    }
	    processRow(k,st,si,callbackFunction)
	  }
	}
      })
    }catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def getKeys(containerName: String, time_ranges: Array[TimeRange], bucketKeys: Array[Array[String]], callbackFunction: (Key) => Unit): Unit = {
    try{
      relogin
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      val tableHBase = connection.getTable(tableName);

      var bucketKeyMap: scala.collection.mutable.Map[String,Boolean] = new scala.collection.mutable.HashMap()      
      bucketKeys.foreach(bucketKey => {
	var bkey = bucketKey.mkString(".")
	bucketKeyMap.put(bkey,true)
      })

      time_ranges.foreach(time_range => {	
	// try scan with beginRow and endRow
	var scan = new Scan()
	scan.setStartRow(Bytes.toBytes(time_range.beginTime.toString))
	scan.setStopRow(Bytes.toBytes((time_range.endTime + 1).toString))
	val rs = tableHBase.getScanner(scan);
	val it = rs.iterator()
	while( it.hasNext() ){
	  val r = it.next()
	  var k = Bytes.toString(r.getRow())
	  var keyArray = k.split('|')
	  var keyExists = bucketKeyMap.getOrElse(keyArray(1),null)
	  if (keyExists != null ){
	    processKey(k,callbackFunction)
	  }
	}
      })
    }catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def get(containerName: String, bucketKeys: Array[Array[String]], callbackFunction: (Key, Value) => Unit): Unit = {
    try{
      relogin
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      val tableHBase = connection.getTable(tableName);
      var bucketKeyMap: scala.collection.mutable.Map[String,Boolean] = new scala.collection.mutable.HashMap()      
      bucketKeys.foreach(bucketKey => {
	var bkey = bucketKey.mkString(".")
	bucketKeyMap.put(bkey,true)
      })
	
      // try scan with beginRow and endRow
      var scan = new Scan()
      val rs = tableHBase.getScanner(scan);
      val it = rs.iterator()
      var dels = new Array[Delete](0)
      while( it.hasNext() ){
	val r = it.next()
	var k = Bytes.toString(r.getRow())
	var keyArray = k.split('|')
	var keyExists = bucketKeyMap.getOrElse(keyArray(1),null)
	if (keyExists != null ){
	  val kvit = r.list().iterator()
	  var st:String  = null
	  var si:Array[Byte] = null
	  while( kvit.hasNext() ){
	    val kv = kvit.next()
	    val q = Bytes.toString(kv.getFamily())
	    q match  {
	      case "serializerType" => {
		st = Bytes.toString(kv.getValue())
	      }
	      case "serializedInfo" => {
		si = kv.getValue()
	      }
	    }
	  }
	  processRow(k,st,si,callbackFunction)
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
      relogin
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      val tableHBase = connection.getTable(tableName);
      var bucketKeyMap: scala.collection.mutable.Map[String,Boolean] = new scala.collection.mutable.HashMap()      
      bucketKeys.foreach(bucketKey => {
	var bkey = bucketKey.mkString(".")
	bucketKeyMap.put(bkey,true)
      })
      // scan the whole table
      var scan = new Scan()
      val rs = tableHBase.getScanner(scan);
      val it = rs.iterator()
      while( it.hasNext() ){
	val r = it.next()
	var k = Bytes.toString(r.getRow())
	var keyArray = k.split('|')
	var keyExists = bucketKeyMap.getOrElse(keyArray(1),null)
	if (keyExists != null ){
	  processKey(k,callbackFunction)
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
    new HBaseAdapterTx(this)
  }

  override def endTx(tx: Transaction): Unit = {}

  override def commitTx(tx: Transaction): Unit = {}

  override def rollbackTx(tx: Transaction): Unit = {}

  override def Shutdown(): Unit = {
    logger.info("close the session and connection pool")
    if (connection != null) {
      connection.close()
      connection = null
    }
  }

  private def TruncateContainer(containerName: String): Unit = {
    try{
      relogin
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      val tableHBase = connection.getTable(tableName);
      var dels = new Array[Delete](0)
      var scan = new Scan()
      val rs = tableHBase.getScanner(scan);
      val it = rs.iterator()
      while( it.hasNext() ){
	val r = it.next()
	dels = dels :+ new Delete(r.getRow())
      }
      val dl = new java.util.ArrayList(dels.toList)
      tableHBase.delete(dl)
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
      relogin
      CheckTableExists(containerName)
      var tableName = toTableName(containerName)
      var fullTableName = toFullTableName(containerName)
      dropTable(fullTableName)
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

class HBaseAdapterTx(val parent: DataStore) extends Transaction {

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

// To create HBase Datastore instance
object HBaseAdapter extends StorageAdapterObj {
  override def CreateStorageAdapter(kvManagerLoader: KamanjaLoaderInfo, datastoreConfig: String): DataStore = new HBaseAdapter(kvManagerLoader, datastoreConfig)
}
