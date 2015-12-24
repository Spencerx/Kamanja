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

import com.datastax.driver.core.Cluster
import com.datastax.driver.core.Session
import com.datastax.driver.core.querybuilder.Insert
import com.datastax.driver.core.Row
import com.datastax.driver.core.ResultSet
import com.datastax.driver.core.ConsistencyLevel
import com.datastax.driver.core.SimpleStatement
import com.datastax.driver.core.PreparedStatement
import com.datastax.driver.core.BatchStatement
import com.datastax.driver.core.HostDistance
import com.datastax.driver.core.PoolingOptions
import java.nio.ByteBuffer
import org.apache.logging.log4j._
import com.ligadata.Exceptions._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.ligadata.Utils.{KamanjaLoaderInfo}

import com.ligadata.KvBase.{ Key, Value, TimeRange }
import com.ligadata.StorageBase.{ DataStore, Transaction, StorageAdapterObj }
import java.util.{ Date, Calendar, TimeZone }
import java.text.SimpleDateFormat
import java.io.File
import scala.collection.mutable.TreeSet
import java.util.Properties

import scala.collection.JavaConversions._

/*
datastoreConfig should have the following:
	Mandatory Options:
		hostlist/Location
		schema/SchemaName

	Optional Options:
		user
		password
		replication_class
		replication_factor
		ConsistencyLevelRead
		ConsistencyLevelWrite
		ConsistencyLevelDelete
		
		All the optional values may come from "AdapterSpecificConfig" also. That is the old way of giving more information specific to Adapter
*/

/*
  	You open connection to a cluster hostname[,hostname]:port
  	You could provide username/password

 	You can operator on keyspace / table

 	if key space is missing we will try to create
 	if table is missing we will try to create

	-- Lets start with this schema
	--
	CREATE KEYSPACE default WITH replication = { 'class': 'SimpleStrategy', 'replication_factor': '4' };
	USE default;
	CREATE TABLE default (key blob, value blob, primary key(key) );
 */

class CassandraAdapter(val kvManagerLoader: KamanjaLoaderInfo, val datastoreConfig: String) extends DataStore {
  val adapterConfig = if (datastoreConfig != null) datastoreConfig.trim else ""
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)

  //logger.setLevel(Level.DEBUG)

  private[this] val lock = new Object
  private var containerList: scala.collection.mutable.Set[String] = scala.collection.mutable.Set[String]()
  private var preparedStatementsMap: scala.collection.mutable.Map[String,PreparedStatement] = new scala.collection.mutable.HashMap()

  if (adapterConfig.size == 0) {
    throw new Exception("Not found valid Cassandra Configuration.")
  }

  logger.debug("Cassandra configuration:" + adapterConfig)
  var parsed_json: Map[String, Any] = null
  try {
    val json = parse(adapterConfig)
    if (json == null || json.values == null) {
      logger.error("Failed to parse Cassandra JSON configuration string:" + adapterConfig)
      throw new Exception("Failed to parse Cassandra JSON configuration string:" + adapterConfig)
    }
    parsed_json = json.values.asInstanceOf[Map[String, Any]]
  } catch {
    case e: Exception => {
      logger.error("Failed to parse Cassandra JSON configuration string:%s. Reason:%s Message:%s".format(adapterConfig, e.getCause, e.getMessage))
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

  // Read all cassandra parameters
  val hostnames = if (parsed_json.contains("hostlist")) parsed_json.getOrElse("hostlist", "localhost").toString.trim else parsed_json.getOrElse("Location", "localhost").toString.trim
  val keyspace = if (parsed_json.contains("schema")) parsed_json.getOrElse("schema", "default").toString.trim else parsed_json.getOrElse("SchemaName", "default").toString.trim
  val replication_class = getOptionalField("replication_class", parsed_json, adapterSpecificConfig_json, "SimpleStrategy").toString.trim
  val replication_factor = getOptionalField("replication_factor", parsed_json, adapterSpecificConfig_json, "1").toString.trim
  val consistencylevelRead = ConsistencyLevel.valueOf(getOptionalField("ConsistencyLevelRead", parsed_json, adapterSpecificConfig_json, "ONE").toString.trim)
  val consistencylevelWrite = ConsistencyLevel.valueOf(getOptionalField("ConsistencyLevelWrite", parsed_json, adapterSpecificConfig_json, "ANY").toString.trim)
  val consistencylevelDelete = ConsistencyLevel.valueOf(getOptionalField("ConsistencyLevelDelete", parsed_json, adapterSpecificConfig_json, "ONE").toString.trim)

  var batchPuts = "NO"
  if (parsed_json.contains("batchPuts")) {
    batchPuts = parsed_json.get("batchPuts").get.toString.trim
  }

  var tableNameLength = 48
  if (parsed_json.contains("tableNameLength")) {
    tableNameLength = parsed_json.get("tableNameLength").get.toString.trim.toInt
  }

  val clusterBuilder = Cluster.builder()
  var cluster: Cluster = _
  var session: Session = _
  var keyspace_exists = false

  try {
    clusterBuilder.addContactPoints(hostnames)
    val usr = getOptionalField("user", parsed_json, adapterSpecificConfig_json, null)
    if (usr != null)
      clusterBuilder.withCredentials(usr.toString.trim, getOptionalField("password", parsed_json, adapterSpecificConfig_json, "").toString.trim)

    // Cassandra connection Pooling
    var poolingOptions = new PoolingOptions();
    var minConPerHostLocal = 4
    if (parsed_json.contains("minConPerHostLocal")) {
      minConPerHostLocal = parsed_json.get("minConPerHostLocal").get.toString.trim.toInt
    }
    var minConPerHostRemote = 2
    if (parsed_json.contains("minConPerHostRemote")) {
      minConPerHostRemote = parsed_json.get("minConPerHostRemote").get.toString.trim.toInt
    }

    var maxConPerHostLocal = 10
    if (parsed_json.contains("maxConPerHostLocal")) {
      maxConPerHostLocal = parsed_json.get("maxConPerHostLocal").get.toString.trim.toInt
    }
    var maxConPerHostRemote = 3
    if (parsed_json.contains("maxConPerHostRemote")) {
      maxConPerHostRemote = parsed_json.get("maxConPerHostRemote").get.toString.trim.toInt
    }

    // set some arbitrary values for coreConnections and maxConnections for now..
    poolingOptions.setCoreConnectionsPerHost(HostDistance.LOCAL,   minConPerHostLocal)
    poolingOptions.setCoreConnectionsPerHost(HostDistance.REMOTE,  minConPerHostRemote)
    poolingOptions.setMaxConnectionsPerHost(HostDistance.LOCAL,   maxConPerHostLocal)
    poolingOptions.setMaxConnectionsPerHost(HostDistance.REMOTE,  maxConPerHostRemote)

    cluster = clusterBuilder.withPoolingOptions(poolingOptions).build()
    //cluster = clusterBuilder.build()

    if (cluster.getMetadata().getKeyspace(keyspace) == null) {
      logger.warn("The keyspace " + keyspace + " doesn't exist yet, we will create a new keyspace and continue")
      // create a session that is not associated with a key space yet so we can create one if needed
      session = cluster.connect();
      // create keyspace if not exists
      val createKeySpaceStmt = "CREATE KEYSPACE IF NOT EXISTS " + keyspace + " with replication = {'class':'" + replication_class + "', 'replication_factor':" + replication_factor + "};"
      try {
        session.execute(createKeySpaceStmt);
      } catch {
        case e: Exception => {
          throw new CreateKeySpaceFailedException("Unable to create keyspace " + keyspace + ":" + e.getMessage())
        }
      }
      // make sure the session is associated with the new tablespace, can be expensive if we create recycle sessions  too often
      session.close()
      session = cluster.connect(keyspace)
    } else {
      keyspace_exists = true
      session = cluster.connect(keyspace)
    }
    logger.info("DataStore created successfully")
  } catch {
    case e: Exception => {
      val stackTrace = StackTrace.ThrowableTraceString(e)
      logger.error("Stacktrace:" + stackTrace)
      throw new ConnectionFailedException("Unable to connect to cassandra at " + hostnames + ":" + e.getMessage())
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

  def DropKeySpace(keyspace: String): Unit = lock.synchronized {
    if (cluster.getMetadata().getKeyspace(keyspace) == null) {
      logger.info("The keyspace " + keyspace + " doesn't exist yet, noting to drop")
      return
    }
    // create keyspace if not exists
    val dropKeySpaceStmt = "DROP KEYSPACE " + keyspace;
    try {
        session.execute(dropKeySpaceStmt);
      } catch {
      case e: Exception => {
        throw new Exception("Unable to drop keyspace " + keyspace + ":" + e.getMessage())
      }
    }
  }

  private def CreateContainer(containerName: String): Unit = lock.synchronized {
    var tableName = toTableName(containerName)
    var fullTableName = toFullTableName(containerName)
    try {
      var query = "create table if not exists " + fullTableName + "(bucketkey varchar,timepartition bigint,transactionid bigint, rowid int, serializertype varchar, serializedinfo blob, primary key(bucketkey,timepartition,transactionid,rowid));"
      session.execute(query);
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

  private def toTableName(containerName: String): String = {
    // we need to check for other restrictions as well
    // such as length of the table, special characters etc
    //containerName.replace('.','_')
    // Cassandra has a limit of 48 characters for table name, so take the first 48 characters only
    // Even though we don't allow duplicate containers within the same namespace,
    // Taking first 48 characters may result in  duplicate table names
    // So I am reversing the long string to ensure unique name
    // Need to be documented, at the least.
    var t = containerName.toLowerCase.replace('.', '_').replace('-', '_')
    if ( t.length > tableNameLength ){
      t.reverse.substring(0,tableNameLength)
    }
    else{
      t
    }
  }

  private def toFullTableName(containerName: String): String = {
    // we need to check for other restrictions as well
    // such as length of the table, special characters etc
    toTableName(containerName)
  }

  override def put(containerName: String, key: Key, value: Value): Unit = {
    try{
      CheckTableExists(containerName)
      var tableName = toFullTableName(containerName)
      var query = "UPDATE " + tableName + " SET serializertype = ? , serializedinfo = ? where timepartition = ? and bucketkey = ? and transactionid = ? and rowid = ?;"
      var prepStmt = preparedStatementsMap.getOrElse(query,null)
      if( prepStmt == null ){
	prepStmt = session.prepare(query)
	preparedStatementsMap.put(query,prepStmt)
      }
      var byteBuf = ByteBuffer.wrap(value.serializedInfo.toArray[Byte]);      
      session.execute(prepStmt.bind(value.serializerType, 
				      byteBuf,
				      new java.lang.Long(key.timePartition),
				      key.bucketKey.mkString(","),
				      new java.lang.Long(key.transactionId),
				      new java.lang.Integer(key.rowId)).
		      setConsistencyLevel(consistencylevelWrite))
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def put(data_list: Array[(String, Array[(Key, Value)])]): Unit = {
    try{
      val batch = new BatchStatement
      data_list.foreach(li => {
        var containerName = li._1
	CheckTableExists(containerName)
	var tableName = toFullTableName(containerName)
	var query = "UPDATE " + tableName + " SET serializertype = ? , serializedinfo = ? where timepartition = ? and bucketkey = ? and transactionid = ? and rowid = ?;"
	var prepStmt = preparedStatementsMap.getOrElse(query,null)
	if( prepStmt == null ){
	  prepStmt = session.prepare(query)
	  preparedStatementsMap.put(query,prepStmt)
	}
        var keyValuePairs = li._2
        keyValuePairs.foreach(keyValuePair => {
          var key = keyValuePair._1
          var value = keyValuePair._2
	  var byteBuf = ByteBuffer.wrap(value.serializedInfo.toArray[Byte]);
	  // Need to sort out the issue by doing more experimentation
	  // When we use a batch statement, and when uploading severaljars when we save a model
	  // object, I am receiving an exception as follows
	  // com.datastax.driver.core.exceptions.WriteTimeoutException: Cassandra timeout during 
	  // write query at consistency ONE (1 replica were required but only 0 acknowledged the write)
	  // Based on my reading on cassandra, I have the upped the parameter write_request_timeout_in_ms
	  // to as much as: 60000( 60 seconds). Now, I see a different exception as shown below
	  // com.datastax.driver.core.exceptions.NoHostAvailableException: All host(s) tried for query failed 
	  // The later error apparently is a misleading error indicating slowness with cassandra server
	  // This may not be an issue in production like configuration
	  // As a work around, I have created a optional configuration parameter to determine
	  // whether updates are done in bulk or one at a time. By default we are doing this 
	  // one at a time until we have a better solution.
	  if( batchPuts.equalsIgnoreCase("YES") ){
	    batch.add(prepStmt.bind(value.serializerType, 
				      byteBuf,
				      new java.lang.Long(key.timePartition),
				      key.bucketKey.mkString(","),
				      new java.lang.Long(key.transactionId),
				      new java.lang.Integer(key.rowId)).
		      setConsistencyLevel(consistencylevelWrite))
	  }
	  else{
	    session.execute(prepStmt.bind(value.serializerType, 
				      byteBuf,
				      new java.lang.Long(key.timePartition),
				      key.bucketKey.mkString(","),
				      new java.lang.Long(key.transactionId),
				      new java.lang.Integer(key.rowId)).
		      setConsistencyLevel(consistencylevelWrite))
	  }
	})
      })
      if( batchPuts.equalsIgnoreCase("YES") ){
	session.execute(batch); 
      }     
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
      val batch = new BatchStatement
      var query = "delete from " + tableName + " where timepartition = ? and bucketkey = ? and transactionid = ? and rowid = ?;"
      var prepStmt = preparedStatementsMap.getOrElse(query,null)
      if( prepStmt == null ){
	prepStmt = session.prepare(query)
	preparedStatementsMap.put(query,prepStmt)
      }
      keys.foreach(key => {
	batch.add(prepStmt.bind(new java.lang.Long(key.timePartition),
				  key.bucketKey.mkString(","),
				  new java.lang.Long(key.transactionId),
				  new java.lang.Integer(key.rowId)).
		      setConsistencyLevel(consistencylevelDelete))
      })
      session.execute(batch);
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  private def getRowKey(rs: Row) : Key = {
    var timePartition = rs.getLong("timepartition")
    var keyStr = rs.getString("bucketkey")
    var tId = rs.getLong("transactionid")
    var rId = rs.getInt("rowid")
    val bucketKey = if (keyStr != null) keyStr.split(",").toArray else new Array[String](0)
    new Key(timePartition, bucketKey, tId, rId)
  }

  // range deletes are not supported by cassandra
  // so identify the rowKeys for the time range and delete them as a batch
  override def del(containerName: String, time: TimeRange, bucketKeys: Array[Array[String]]): Unit = {
    var tableName = toFullTableName(containerName)
    try{
      CheckTableExists(containerName)
      var query = "select timepartition,bucketkey,transactionid,rowid from " + tableName + " where bucketkey = ? and timepartition >= ?  and timepartition <= ? "
      var prepStmt = preparedStatementsMap.getOrElse(query,null)
      if( prepStmt == null ){
	prepStmt = session.prepare(query)
	preparedStatementsMap.put(query,prepStmt)
      }
      var rowKeys = new Array[Key](0)
      bucketKeys.foreach(bucketKey => {
	var rows = session.execute(prepStmt.bind(bucketKey.mkString(","),
						 new java.lang.Long(time.beginTime),
						 new java.lang.Long(time.endTime)).
				   setConsistencyLevel(consistencylevelDelete))
	for( rs <- rows ){
	  rowKeys = rowKeys :+ getRowKey(rs)
	}
      })
      del(containerName,rowKeys)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  // get operations
  def getRowCount(containerName: String, whereClause: String): Long = {
    var tableName = toFullTableName(containerName)
    var getCountStmt = new SimpleStatement("select count(*) from " + tableName)
    var rows = session.execute(getCountStmt)
    var row = rows.one()
    var cnt = row.getLong("count")
    return cnt
  }

  private def convertByteBufToArrayOfBytes(buffer: java.nio.ByteBuffer): Array[Byte] = {
    var ba = new Array[Byte](buffer.remaining())
    buffer.get(ba, 0, ba.length);
    ba
  }

  private def processRow(rs: Row,callbackFunction: (Key, Value) => Unit){
    var timePartition = rs.getLong("timepartition")
    var keyStr = rs.getString("bucketkey")
    var tId = rs.getLong("transactionid")
    var rId = rs.getInt("rowid")
    var st = rs.getString("serializertype")
    var buf = rs.getBytes("serializedinfo")
    // format the data to create Key/Value
    val bucketKey = if (keyStr != null) keyStr.split(",").toArray else new Array[String](0)
    var key = new Key(timePartition, bucketKey, tId, rId)
    var ba = convertByteBufToArrayOfBytes(buf)
    var value = new Value(st, ba)
    (callbackFunction)(key, value)
  }

  private def processRow(key: Key, rs: Row,callbackFunction: (Key, Value) => Unit){
    var st = rs.getString("serializertype")
    var buf = rs.getBytes("serializedinfo")
    var ba = convertByteBufToArrayOfBytes(buf)
    var value = new Value(st, ba)
    (callbackFunction)(key, value)
  }

  private def processKey(rs: Row,callbackFunction: (Key) => Unit){
    var timePartition = rs.getLong("timepartition")
    var keyStr = rs.getString("bucketkey")
    var tId = rs.getLong("transactionid")
    var rId = rs.getInt("rowid")
    // format the data to create Key/Value
    val bucketKey = if (keyStr != null) keyStr.split(",").toArray else new Array[String](0)
    var key = new Key(timePartition, bucketKey, tId, rId)
    (callbackFunction)(key)
  }

  private def getData(tableName: String, query: String, callbackFunction: (Key, Value) => Unit): Unit = {
    try{
      var getDataStmt = new SimpleStatement(query)
      var rows = session.execute(getDataStmt)
      var rs:Row = null
      for( rs <- rows ){
	processRow(rs,callbackFunction)
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def get(containerName: String, callbackFunction: (Key, Value) => Unit): Unit = {
    CheckTableExists(containerName)
    var tableName = toFullTableName(containerName)
    var query = "select * from " + tableName
    getData(tableName, query, callbackFunction)
  }


  private def getKeys(tableName: String, query: String, callbackFunction: (Key) => Unit): Unit = {
    try{
      var getDataStmt = new SimpleStatement(query)
      var rows = session.execute(getDataStmt)
      var rs:Row = null
      for( rs <- rows ){
	processKey(rs,callbackFunction)
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def getKeys(containerName: String, callbackFunction: (Key) => Unit): Unit = {
    CheckTableExists(containerName)
    var tableName = toFullTableName(containerName)
    var query = "select timepartition,bucketkey,transactionid,rowid from " + tableName
    getKeys(tableName, query, callbackFunction)
  }

  override def getKeys(containerName: String, keys: Array[Key], callbackFunction: (Key) => Unit): Unit = {
    var tableName = toFullTableName(containerName)
    try{
      CheckTableExists(containerName)
      var query = "select timepartition,bucketkey,transactionid,rowid from " + tableName + " where timepartition = ? and bucketkey = ? and transactionid = ? and rowid = ?"
      var prepStmt = preparedStatementsMap.getOrElse(query,null)
      if( prepStmt == null ){
	prepStmt = session.prepare(query)
	preparedStatementsMap.put(query,prepStmt)
      }
      keys.foreach(key => {
	var rows = session.execute(prepStmt.bind(new java.lang.Long(key.timePartition),
				      key.bucketKey.mkString(","),
				      new java.lang.Long(key.transactionId),
				      new java.lang.Integer(key.rowId)).
		      setConsistencyLevel(consistencylevelRead))
	var rs:Row = null
	for( rs <- rows ){
	  processKey(rs,callbackFunction)
	}
      })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def get(containerName: String, keys: Array[Key], callbackFunction: (Key, Value) => Unit): Unit = {
    var tableName = toFullTableName(containerName)
    try{
      CheckTableExists(containerName)
      var query = "select serializertype,serializedinfo from " + tableName + " where timepartition = ? and bucketkey = ? and transactionid = ? and rowid = ?"
      var prepStmt = preparedStatementsMap.getOrElse(query,null)
      if( prepStmt == null ){
	prepStmt = session.prepare(query)
	preparedStatementsMap.put(query,prepStmt)
      }
      keys.foreach(key => {
	var rows = session.execute(prepStmt.bind(new java.lang.Long(key.timePartition),
				      key.bucketKey.mkString(","),
				      new java.lang.Long(key.transactionId),
				      new java.lang.Integer(key.rowId)).
		      setConsistencyLevel(consistencylevelRead))
	var rs:Row = null
	for( rs <- rows ){
	  processRow(key,rs,callbackFunction)
	}
      })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }

  }

  override def get(containerName: String, time_ranges: Array[TimeRange], callbackFunction: (Key, Value) => Unit): Unit = {
    CheckTableExists(containerName)
    var tableName = toFullTableName(containerName)
    var query = "select timepartition,bucketkey,transactionid,rowid,serializertype,serializedinfo from " + tableName + " where timepartition >= ? and timepartition <= ? ALLOW FILTERING;"
    var prepStmt = preparedStatementsMap.getOrElse(query,null)
    if( prepStmt == null ){
      prepStmt = session.prepare(query)
      preparedStatementsMap.put(query,prepStmt)
    }
    time_ranges.foreach(time_range => {
      var rs:Row = null
      var rows = session.execute(prepStmt.bind(new java.lang.Long(time_range.beginTime),
					       new java.lang.Long(time_range.endTime)).
		      setConsistencyLevel(consistencylevelRead))
      for( rs <- rows ){
	processRow(rs,callbackFunction)
      }
    })
  }

  override def getKeys(containerName: String, time_ranges: Array[TimeRange], callbackFunction: (Key) => Unit): Unit = {
    CheckTableExists(containerName)
    var tableName = toFullTableName(containerName)
    var query = "select timepartition,bucketkey,transactionid,rowid from " + tableName + " where timepartition >= ? and timepartition <= ? ALLOW FILTERING;"
    var prepStmt = preparedStatementsMap.getOrElse(query,null)
    if( prepStmt == null ){
      prepStmt = session.prepare(query)
      preparedStatementsMap.put(query,prepStmt)
    }
    time_ranges.foreach(time_range => {
      var rs:Row = null
      var rows = session.execute(prepStmt.bind(new java.lang.Long(time_range.beginTime),
					       new java.lang.Long(time_range.endTime)).
		      setConsistencyLevel(consistencylevelRead))
      for( rs <- rows ){
	processKey(rs,callbackFunction)
      }
    })
  }


  override def get(containerName: String, time_ranges: Array[TimeRange], bucketKeys: Array[Array[String]], callbackFunction: (Key, Value) => Unit): Unit = {
    var tableName = toFullTableName(containerName)
    try{
      CheckTableExists(containerName)
      var query = "select timepartition,bucketkey,transactionid,rowid,serializertype,serializedinfo from " + tableName + " where timepartition >= ?  and timepartition <= ?  and bucketkey = ? "
      var prepStmt = preparedStatementsMap.getOrElse(query,null)
      if( prepStmt == null ){
	prepStmt = session.prepare(query)
	preparedStatementsMap.put(query,prepStmt)
      }
      time_ranges.foreach(time_range => {
	  bucketKeys.foreach(bucketKey => {
	    var rs:Row = null
	    var rows = session.execute(prepStmt.bind(new java.lang.Long(time_range.beginTime),
						     new java.lang.Long(time_range.endTime),
						     bucketKey.mkString(",")).
				       setConsistencyLevel(consistencylevelRead))
	    for( rs <- rows ){
	      processRow(rs,callbackFunction)
	    }
	  })
      })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def getKeys(containerName: String, time_ranges: Array[TimeRange], bucketKeys: Array[Array[String]], callbackFunction: (Key) => Unit): Unit = {
    var tableName = toFullTableName(containerName)
    try{
      CheckTableExists(containerName)
      var query = "select timepartition,bucketkey,transactionid,rowid from " + tableName + " where timepartition >= ? and timepartition <= ?  and bucketkey = ? "
      var prepStmt = preparedStatementsMap.getOrElse(query,null)
      if( prepStmt == null ){
	prepStmt = session.prepare(query)
	preparedStatementsMap.put(query,prepStmt)
      }
      time_ranges.foreach(time_range => {
	  bucketKeys.foreach(bucketKey => {
	    var rs:Row = null
	    var rows = session.execute(prepStmt.bind(new java.lang.Long(time_range.beginTime),
						     new java.lang.Long(time_range.endTime),
						     bucketKey.mkString(",")).
				       setConsistencyLevel(consistencylevelRead))
	    for( rs <- rows ){
	      processKey(rs,callbackFunction)
	    }
	  })
      })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def get(containerName: String, bucketKeys: Array[Array[String]], callbackFunction: (Key, Value) => Unit): Unit = {
    var tableName = toFullTableName(containerName)
    try{
      CheckTableExists(containerName)
      var query = "select timepartition,bucketkey,transactionid,rowid,serializertype,serializedinfo from " + tableName + " where  bucketkey = ? "
      var prepStmt = preparedStatementsMap.getOrElse(query,null)
      if( prepStmt == null ){
	prepStmt = session.prepare(query)
	preparedStatementsMap.put(query,prepStmt)
      }
      bucketKeys.foreach(bucketKey => {
	var rs:Row = null
	var rows = session.execute(prepStmt.bind(bucketKey.mkString(",")).
				   setConsistencyLevel(consistencylevelRead))
	for( rs <- rows ){
	  processRow(rs,callbackFunction)
	}
      })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def getKeys(containerName: String, bucketKeys: Array[Array[String]], callbackFunction: (Key) => Unit): Unit = {
    var tableName = toFullTableName(containerName)
    try{
      CheckTableExists(containerName)
      var query = "select timepartition,bucketkey,transactionid,rowid from " + tableName + " where  bucketkey = ? "
      var prepStmt = preparedStatementsMap.getOrElse(query,null)
      if( prepStmt == null ){
	prepStmt = session.prepare(query)
	preparedStatementsMap.put(query,prepStmt)
      }
      bucketKeys.foreach(bucketKey => {
	var rows = session.execute(prepStmt.bind(bucketKey.mkString(",")).
				   setConsistencyLevel(consistencylevelRead))
	for( rs <- rows ){
	  processKey(rs,callbackFunction)
	}
      })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
      }
    }
  }

  override def beginTx(): Transaction = {
    new CassandraAdapterTx(this)
  }

  override def endTx(tx: Transaction): Unit = {}

  override def commitTx(tx: Transaction): Unit = {}

  override def rollbackTx(tx: Transaction): Unit = {}

  override def Shutdown(): Unit = {
    logger.info("close the session and connection pool")
    session.close()
    cluster.close()
  }

  private def TruncateContainer(containerName: String): Unit = {
    var fullTableName = toFullTableName(containerName)
    try {
      CheckTableExists(containerName)
      var query = "truncate " + fullTableName
      session.execute(query);
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
    var tableName = toTableName(containerName)
    var fullTableName = toFullTableName(containerName)
    try {
      var query = "drop table if exists " + fullTableName
      session.execute(query);
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

class CassandraAdapterTx(val parent: DataStore) extends Transaction {

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

// To create Cassandra Datastore instance
object CassandraAdapter extends StorageAdapterObj {
  override def CreateStorageAdapter(kvManagerLoader: KamanjaLoaderInfo, datastoreConfig: String): DataStore = new CassandraAdapter(kvManagerLoader, datastoreConfig)
}
