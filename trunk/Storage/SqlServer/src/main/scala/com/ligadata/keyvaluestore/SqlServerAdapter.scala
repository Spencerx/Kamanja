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
import java.sql.DriverManager
import java.sql.{ Statement, PreparedStatement, CallableStatement, DatabaseMetaData, ResultSet }
import java.sql.Connection
import com.ligadata.KvBase.{ Key, Value, TimeRange }
import com.ligadata.StorageBase.{ DataStore, Transaction, StorageAdapterObj }
import java.nio.ByteBuffer
import org.apache.logging.log4j._
import com.ligadata.Exceptions._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.ligadata.Utils.{ KamanjaLoaderInfo }
import java.util.{ Date, Calendar, TimeZone }
import java.text.SimpleDateFormat
import java.io.File
import java.net.{ URL, URLClassLoader }
import scala.collection.mutable.TreeSet
import java.sql.{ Driver, DriverPropertyInfo, SQLException }
import java.sql.Timestamp
import java.util.Properties
import org.apache.commons.dbcp2.BasicDataSource

class JdbcClassLoader(urls: Array[URL], parent: ClassLoader) extends URLClassLoader(urls, parent) {
  override def addURL(url: URL) {
    super.addURL(url)
  }
}

class DriverShim(d: Driver) extends Driver {
  private var driver: Driver = d

  def connect(u: String, p: Properties): Connection = this.driver.connect(u, p)

  def acceptsURL(u: String): Boolean = this.driver.acceptsURL(u)

  def getPropertyInfo(u: String, p: Properties): Array[DriverPropertyInfo] = this.driver.getPropertyInfo(u, p)

  def getMajorVersion(): Int = this.driver.getMajorVersion

  def getMinorVersion(): Int = this.driver.getMinorVersion

  def jdbcCompliant(): Boolean = this.driver.jdbcCompliant()

  def getParentLogger(): java.util.logging.Logger = this.driver.getParentLogger()
}

class SqlServerAdapter(val kvManagerLoader: KamanjaLoaderInfo, val datastoreConfig: String) extends DataStore {

  private[this] val lock = new Object

  val dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

  val adapterConfig = if (datastoreConfig != null) datastoreConfig.trim else ""
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
  private val loadedJars: TreeSet[String] = new TreeSet[String];
  private val clsLoader = new JdbcClassLoader(ClassLoader.getSystemClassLoader().asInstanceOf[URLClassLoader].getURLs(), getClass().getClassLoader())

  private var containerList: scala.collection.mutable.Set[String] = scala.collection.mutable.Set[String]()
  private var msg: String = ""

  private def CreateConnectionException(msg: String, ie: Exception): StorageConnectionException = {
    logger.error(msg)
    val ex = new StorageConnectionException("Failed to connect to Database", ie)
    ex
  }

  private def CreateDMLException(msg: String, ie: Exception): StorageDMLException = {
    logger.error(msg)
    val ex = new StorageDMLException("Failed to execute select/insert/delete/update operation on Database", ie)
    ex
  }

  private def CreateDDLException(msg: String, ie: Exception): StorageDDLException = {
    logger.error(msg)
    val ex = new StorageDDLException("Failed to execute create/drop operations on Database", ie)
    ex
  }

  if (adapterConfig.size == 0) {
    msg = "Invalid SqlServer Json Configuration string:" + adapterConfig
    throw CreateConnectionException(msg, new Exception("Invalid Configuration"))
  }

  logger.debug("SqlServer configuration:" + adapterConfig)
  var parsed_json: Map[String, Any] = null
  try {
    val json = parse(adapterConfig)
    if (json == null || json.values == null) {
      msg = "Failed to parse SqlServer JSON configuration string:" + adapterConfig
      throw CreateConnectionException(msg, new Exception("Invalid Configuration"))
    }
    parsed_json = json.values.asInstanceOf[Map[String, Any]]
  } catch {
    case e: Exception => {
      var msg = "Failed to parse SqlServer JSON configuration string:%s. Message:%s".format(adapterConfig, e.getMessage)
      throw CreateConnectionException(msg, e)
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
          msg = "Failed to parse SqlServer Adapter Specific JSON configuration string:" + adapterSpecificStr
          throw CreateConnectionException(msg, new Exception("Invalid Configuration"))
        }
        adapterSpecificConfig_json = json.values.asInstanceOf[Map[String, Any]]
      } catch {
        case e: Exception => {
          msg = "Failed to parse SqlServer Adapter Specific JSON configuration string:%s. Message:%s".format(adapterSpecificStr, e.getMessage)
          throw CreateConnectionException(msg, e)
        }
      }
    }
  }

  // Read all sqlServer parameters
  var hostname: String = null;
  if (parsed_json.contains("hostname")) {
    hostname = parsed_json.get("hostname").get.toString.trim
  } else {
    throw CreateConnectionException("Unable to find hostname in adapterConfig ", new Exception("Invalid adapterConfig"))
  }

  var instanceName: String = null;
  if (parsed_json.contains("instancename")) {
    instanceName = parsed_json.get("instancename").get.toString.trim
  }

  var portNumber: String = null;
  if (parsed_json.contains("portnumber")) {
    portNumber = parsed_json.get("portnumber").get.toString.trim
  }

  var database: String = null;
  if (parsed_json.contains("database")) {
    database = parsed_json.get("database").get.toString.trim
  } else {
    throw CreateConnectionException("Unable to find database in adapterConfig ", new Exception("Invalid adapterConfig"))
  }

  var user: String = null;
  if (parsed_json.contains("user")) {
    user = parsed_json.get("user").get.toString.trim
  } else {
    throw CreateConnectionException("Unable to find user in adapterConfig ", new Exception("Invalid adapterConfig"))
  }

  var SchemaName: String = null;
  if (parsed_json.contains("SchemaName")) {
    SchemaName = parsed_json.get("SchemaName").get.toString.trim
  } else {
    logger.info("The SchemaName is not supplied in adapterConfig, defaults to " + user)
    SchemaName = user
  }

  var password: String = null;
  if (parsed_json.contains("password")) {
    password = parsed_json.get("password").get.toString.trim
  } else {
    throw CreateConnectionException("Unable to find password in adapterConfig ", new Exception("Invalid adapterConfig"))
  }

  var jarpaths: String = null;
  if (parsed_json.contains("jarpaths")) {
    jarpaths = parsed_json.get("jarpaths").get.toString.trim
  } else {
    throw CreateConnectionException("Unable to find jarpaths in adapterConfig ", new Exception("Invalid adapterConfig"))
  }

  var jdbcJar: String = null;
  if (parsed_json.contains("jdbcJar")) {
    jdbcJar = parsed_json.get("jdbcJar").get.toString.trim
  } else {
    throw CreateConnectionException("Unable to find jdbcJar in adapterConfig ", new Exception("Invalid adapterConfig"))
  }

  // The following three properties are used for connection pooling
  var maxActiveConnections = 20
  if (parsed_json.contains("maxActiveConnections")) {
    maxActiveConnections = parsed_json.get("maxActiveConnections").get.toString.trim.toInt
  }

  var maxIdleConnections = 10
  if (parsed_json.contains("maxIdleConnections")) {
    maxIdleConnections = parsed_json.get("maxIdleConnections").get.toString.trim.toInt
  }

  var initialSize = 10
  if (parsed_json.contains("initialSize")) {
    initialSize = parsed_json.get("initialSize").get.toString.trim.toInt
  }

  var maxWaitMillis = 10000
  if (parsed_json.contains("maxWaitMillis")) {
    maxWaitMillis = parsed_json.get("maxWaitMillis").get.toString.trim.toInt
  }

  // some misc optional parameters
  var clusteredIndex = "NO"
  if (parsed_json.contains("clusteredIndex")) {
    clusteredIndex = parsed_json.get("clusteredIndex").get.toString.trim
  }

  logger.info("hostname => " + hostname)
  logger.info("username => " + user)
  logger.info("SchemaName => " + SchemaName)
  logger.info("jarpaths => " + jarpaths)
  logger.info("jdbcJar  => " + jdbcJar)

  var sqlServerInstance: String = hostname
  if (instanceName != null) {
    sqlServerInstance = sqlServerInstance + "\\" + instanceName
  }
  if (portNumber != null) {
    sqlServerInstance = sqlServerInstance + ":" + portNumber
  }

  var jdbcUrl = "jdbc:sqlserver://" + sqlServerInstance + ";databaseName=" + database + ";user=" + user + ";password=" + password

  var jars = new Array[String](0)
  var jar = jarpaths + "/" + jdbcJar
  jars = jars :+ jar

  val driverType = "com.microsoft.sqlserver.jdbc.SQLServerDriver"
  try {
    logger.info("Loading the Driver..")
    LoadJars(jars)
    val d = Class.forName(driverType, true, clsLoader).newInstance.asInstanceOf[Driver]
    logger.info("Registering Driver..")
    DriverManager.registerDriver(new DriverShim(d));
  } catch {
    case e: Exception => {
      msg = "Failed to load/register jdbc driver name:%s. Message:%s".format(driverType, e.getMessage)
      throw CreateConnectionException(msg, e)
    }
  }

  // setup connection pooling using apache-commons-dbcp2
  logger.info("Setting up jdbc connection pool ..")
  var dataSource: BasicDataSource = null
  try {
    dataSource = new BasicDataSource
    dataSource.setUrl(jdbcUrl)
    dataSource.setUsername(user)
    dataSource.setPassword(password)
    dataSource.setMaxTotal(maxActiveConnections);
    dataSource.setMaxIdle(maxIdleConnections);
    dataSource.setInitialSize(initialSize);
    dataSource.setMaxWaitMillis(maxWaitMillis);
    dataSource.setTestOnBorrow(true);
    dataSource.setValidationQuery("SELECT 1");
  } catch {
    case e: Exception => {
      msg = "Failed to setup connection pooling using apache-commons-dbcp. Message:%s".format(e.getMessage)
      throw CreateConnectionException(msg, e)
    }
  }

  // set the timezone to UTC for all time values
  TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

  // verify whether schema exists, It can throw an exception due to authorization issues
  var schemaExists: Boolean = false
  try {
    schemaExists = IsSchemaExists(SchemaName)
  } catch {
    case e: Exception => {
      msg = "Message:%s".format(e.getMessage)
      throw CreateDMLException(msg, e)
    }
  }

  // if the schema doesn't exist, we try to create one. It can still fail due to authorization issues
  if (!schemaExists) {
    logger.info("Unable to find the schema " + SchemaName + " in the database, attempt to create one ")
    try {
      CreateSchema(SchemaName)
    } catch {
      case e: Exception => {
        msg = "Message:%s".format(e.getMessage)
        throw CreateDDLException(msg, e)
      }
    }
  }

  // return the date time in the format yyyy-MM-dd HH:mm:ss.SSS
  private def GetCurDtTmStr: String = {
    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date(System.currentTimeMillis))
  }

  private def getConnection: Connection = {
    try{
      var con = dataSource.getConnection
      con
    } catch {
      case e: Exception => {
        var msg = "Message:%s".format(e.getMessage)
        throw CreateConnectionException(msg, e)
      }
    }
  }
    
  private def IsSchemaExists(schemaName: String): Boolean = {
    var con: Connection = null
    var pstmt: PreparedStatement = null
    var rs: ResultSet = null
    var rowCount = 0
    var query = ""
    try {
      con = getConnection
      query = "SELECT count(*) FROM sys.schemas WHERE name = ?"
      pstmt = con.prepareStatement(query)
      pstmt.setString(1, schemaName)
      rs = pstmt.executeQuery();
      while (rs.next()) {
        rowCount = rs.getInt(1)
      }
      if (rowCount > 0) {
        return true
      } else {
        return false
      }
    } catch {
      case e: StorageConnectionException => {
        throw e
      } 
      case e: Exception => {
        throw new Exception("Failed to verify schema existence for the schema " + schemaName + ":" + "query => " + query + ":" + e.getMessage())
      }
    } finally {
      if (rs != null) {
        rs.close
      }
      if (pstmt != null) {
        pstmt.close
      }
      if (con != null) {
        con.close
      }
    }
  }

  private def CreateSchema(schemaName: String): Unit = {
    var con: Connection = null
    var stmt: Statement = null
    try {
      con = dataSource.getConnection
      var query = "create schema " + schemaName
      stmt = con.createStatement()
      stmt.executeUpdate(query);
    } catch {
      case e: Exception => {
        throw CreateDDLException("Failed to create schema  " + schemaName + ":" + e.getMessage(), e)
      }
    } finally {
      if (stmt != null) {
        stmt.close
      }
      if (con != null) {
        con.close
      }
    }
  }

  private def CheckTableExists(containerName: String): Unit = {
    try{
      if (containerList.contains(containerName)) {
	return
      } else {
	CreateContainer(containerName)
	containerList.add(containerName)
      }
    } catch {
      case e: Exception => {
        throw new Exception("Failed to create table  " + toTableName(containerName) + ":" + e.getMessage())
      }
    }
  }
  /**
   * loadJar - load the specified jar into the classLoader
   */

  private def LoadJars(jars: Array[String]): Unit = {
    // Loading all jars
    for (j <- jars) {
      val jarNm = j.trim
      logger.debug("%s:Processing Jar: %s".format(GetCurDtTmStr, jarNm))
      val fl = new File(jarNm)
      if (fl.exists) {
        try {
          if (loadedJars(fl.getPath())) {
            logger.info("%s:Jar %s already loaded to class path.".format(GetCurDtTmStr, jarNm))
          } else {
            clsLoader.addURL(fl.toURI().toURL())
            logger.info("%s:Jar %s added to class path.".format(GetCurDtTmStr, jarNm))
            loadedJars += fl.getPath()
          }
        } catch {
          case e: Exception => {
            val errMsg = "Jar " + jarNm + " failed added to class path. Reason:%s Message:%s".format(e.getCause, e.getMessage)
            throw CreateConnectionException(errMsg, e)
          }
        }
      } else {
        val errMsg = "Jar " + jarNm + " not found"
        throw new Exception(errMsg)
      }
    }
  }

  private def toTableName(containerName: String): String = {
    // Ideally, we need to check for all restrictions for naming a table
    // such as length of the table, special characters etc
    // containerName.replace('.','_')
    containerName.toLowerCase.replace('.', '_').replace('-', '_')
  }

  private def toFullTableName(containerName: String): String = {
    SchemaName + "." + toTableName(containerName)
  }

  override def put(containerName: String, key: Key, value: Value): Unit = {
    var con: Connection = null
    var pstmt: PreparedStatement = null
    var cstmt: CallableStatement = null
    var tableName = toFullTableName(containerName)
    var sql = ""
    try {
      CheckTableExists(containerName)
      con = getConnection
      con.setAutoCommit(false)
      // put is sematically an upsert. An upsert is implemented using a merge
      // statement in sqlserver
      // Ideally a merge should be implemented as stored procedure
      // I am having some trouble in implementing stored procedure
      // We are implementing this as delete followed by insert for lack of time
      sql = "delete from " + tableName + " where timePartition = ? and bucketKey = ? and transactionid = ? and rowId = ?"
      logger.debug("sql => " + sql)
      pstmt = con.prepareStatement(sql)
      pstmt.setLong(1, key.timePartition)
      pstmt.setString(2, key.bucketKey.mkString(","))
      pstmt.setLong(3, key.transactionId)
      pstmt.setInt(4, key.rowId)
      pstmt.executeUpdate();

      sql = "insert into " + tableName + "(timePartition,bucketKey,transactionId,rowId,serializerType,serializedInfo) values(?,?,?,?,?,?)"
      logger.debug("insertsql => " + sql)
      pstmt = con.prepareStatement(sql)
      pstmt.setLong(1, key.timePartition)
      pstmt.setString(2, key.bucketKey.mkString(","))
      pstmt.setLong(3, key.transactionId)
      pstmt.setInt(4, key.rowId)
      pstmt.setString(5, value.serializerType)
      pstmt.setBinaryStream(6, new java.io.ByteArrayInputStream(value.serializedInfo), value.serializedInfo.length)
      pstmt.executeUpdate();
      con.setAutoCommit(true)
    } catch {
      case e: Exception => {
        if (con != null){
	  try{
	    // rollback has thrown exception in some special scenarios, capture it
            con.rollback()
	  }catch {
	    case ie: Exception => {
	      val stackTrace = StackTrace.ThrowableTraceString(ie)
	      logger.error("StackTrace:"+stackTrace)
	    }
	  }
	}
        throw CreateDMLException("Failed to save an object in the table " + tableName + ":" + "sql => " + sql + ":" + e.getMessage(), e)
      }
    } finally {
      if (cstmt != null) {
        cstmt.close
      }
      if (pstmt != null) {
        pstmt.close
      }
      if (con != null) {
        con.close
      }
    }
  }

  override def put(data_list: Array[(String, Array[(Key, Value)])]): Unit = {
    var con: Connection = null
    var pstmt: PreparedStatement = null
    var sql: String = null
    var totalRowsDeleted = 0;
    var totalRowsInserted = 0;
    try {
      logger.debug("Get a new connection...")
      con = getConnection
      // we need to commit entire batch
      con.setAutoCommit(false)
      data_list.foreach(li => {
        var containerName = li._1
        CheckTableExists(containerName)
        var tableName = toFullTableName(containerName)
        sql = "delete from " + tableName + " where timePartition = ? and bucketKey = ? and transactionid = ? and rowId = ? "
        logger.debug("sql => " + sql)
        pstmt = con.prepareStatement(sql)
        var keyValuePairs = li._2
        keyValuePairs.foreach(keyValuePair => {
          var key = keyValuePair._1
          pstmt.setLong(1, key.timePartition)
          pstmt.setString(2, key.bucketKey.mkString(","))
          pstmt.setLong(3, key.transactionId)
          pstmt.setInt(4, key.rowId)
          // Add it to the batch
          pstmt.addBatch()
        })
        logger.debug("Executing bulk Delete...")
        var deleteCount = pstmt.executeBatch();
        deleteCount.foreach(cnt => { totalRowsDeleted += cnt });
        if (pstmt != null) {
          pstmt.close
        }
        logger.debug("Deleted " + totalRowsDeleted + " rows from " + tableName)
        // insert rows 
        sql = "insert into " + tableName + "(timePartition,bucketKey,transactionId,rowId,serializerType,serializedInfo) values(?,?,?,?,?,?)"
        pstmt = con.prepareStatement(sql)
        // 
        // we could have potential memory issue if number of records are huge
        // use a batch size between executions executeBatch
        keyValuePairs.foreach(keyValuePair => {
          var key = keyValuePair._1
          var value = keyValuePair._2
          pstmt.setLong(1, key.timePartition)
          pstmt.setString(2, key.bucketKey.mkString(","))
          pstmt.setLong(3, key.transactionId)
          pstmt.setInt(4, key.rowId)
          pstmt.setString(5, value.serializerType)
          pstmt.setBinaryStream(6, new java.io.ByteArrayInputStream(value.serializedInfo), value.serializedInfo.length)
          // Add it to the batch
          pstmt.addBatch()
        })
        logger.debug("Executing bulk Insert...")
        var insertCount = pstmt.executeBatch();
        insertCount.foreach(cnt => { totalRowsInserted += cnt });
        logger.debug("Inserted " + totalRowsInserted + " rows into " + tableName)
        if (pstmt != null) {
          pstmt.close
        }
      })
      con.commit()
      con.setAutoCommit(true)
    } catch {
      case e: Exception => {
        if (con != null){
	  try{
	    // rollback has thrown exception in some special scenarios, capture it
            con.rollback()
	  }catch {
	    case ie: Exception => {
	      val stackTrace = StackTrace.ThrowableTraceString(ie)
	      logger.error("StackTrace:"+stackTrace)
	    }
	  }
	}
        throw CreateDMLException("Failed to save a batch of objects into the table :" + "sql => " + sql + ":" + e.getMessage(), e)
      }
    } finally {
      if (pstmt != null) {
        pstmt.close
      }
      if (con != null) {
        con.close
      }
    }
  }

  // delete operations
  override def del(containerName: String, keys: Array[Key]): Unit = {
    var con: Connection = null
    var pstmt: PreparedStatement = null
    var cstmt: CallableStatement = null
    var tableName = toFullTableName(containerName)
    var sql = ""
    try {
      CheckTableExists(containerName)
      con = getConnection

      sql = "delete from " + tableName + " where timePartition = ? and bucketKey = ? and transactionid = ? and rowId = ?"
      pstmt = con.prepareStatement(sql)
      // we need to commit entire batch
      con.setAutoCommit(false)
      keys.foreach(key => {
        pstmt.setLong(1, key.timePartition)
        pstmt.setString(2, key.bucketKey.mkString(","))
        pstmt.setLong(3, key.transactionId)
        pstmt.setInt(4, key.rowId)
        // Add it to the batch
        pstmt.addBatch()
      })
      var deleteCount = pstmt.executeBatch();
      con.commit()
      var totalRowsDeleted = 0;
      deleteCount.foreach(cnt => { totalRowsDeleted += cnt });
      logger.info("Deleted " + totalRowsDeleted + " rows from " + tableName)
      con.setAutoCommit(true)
    } catch {
      case e: Exception => {
        if (con != null){
	  try{
	    // rollback has thrown exception in some special scenarios, capture it
            con.rollback()
	  }catch {
	    case ie: Exception => {
	      val stackTrace = StackTrace.ThrowableTraceString(ie)
	      logger.error("StackTrace:"+stackTrace)
	    }
	  }
	}
        throw CreateDMLException("Failed to delete object(s) from the table " + tableName + ":" + "sql => " + sql + ":" + e.getMessage(), e)
      }
    } finally {
      if (cstmt != null) {
        cstmt.close
      }
      if (pstmt != null) {
        pstmt.close
      }
      if (con != null) {
        con.close
      }
    }
  }

  override def del(containerName: String, time: TimeRange, keys: Array[Array[String]]): Unit = {
    var con: Connection = null
    var pstmt: PreparedStatement = null
    var cstmt: CallableStatement = null
    var tableName = toFullTableName(containerName)
    var sql = ""
    try {
      logger.info("begin time => " + dateFormat.format(time.beginTime))
      logger.info("end time => " + dateFormat.format(time.endTime))
      CheckTableExists(containerName)

      con = getConnection
      // we need to commit entire batch
      con.setAutoCommit(false)
      sql = "delete from " + tableName + " where timePartition >= ?  and timePartition <= ? and bucketKey = ?"
      pstmt = con.prepareStatement(sql)
      keys.foreach(keyList => {
        var keyStr = keyList.mkString(",")
        pstmt.setLong(1, time.beginTime)
        pstmt.setLong(2, time.endTime)
        pstmt.setString(3, keyStr)
        // Add it to the batch
        pstmt.addBatch()
      })
      var deleteCount = pstmt.executeBatch();
      con.commit()
      var totalRowsDeleted = 0;
      deleteCount.foreach(cnt => { totalRowsDeleted += cnt });
      logger.info("Deleted " + totalRowsDeleted + " rows from " + tableName)
      con.setAutoCommit(true)
    } catch {
      case e: Exception => {
        if (con != null){
	  try{
	    // rollback has thrown exception in some special scenarios, capture it
            con.rollback()
	  }catch {
	    case ie: Exception => {
	      val stackTrace = StackTrace.ThrowableTraceString(ie)
	      logger.error("StackTrace:"+stackTrace)
	    }
	  }
	}
        throw CreateDMLException("Failed to delete object(s) from the table " + tableName + ":" + "sql => " + sql + ":" + e.getMessage(), e)
      }
    } finally {
      if (cstmt != null) {
        cstmt.close
      }
      if (pstmt != null) {
        pstmt.close
      }
      if (con != null) {
        con.close
      }
    }
  }

  // get operations
  def getRowCount(containerName: String, whereClause: String): Int = {
    var con: Connection = null
    var stmt: Statement = null
    var rs: ResultSet = null
    var rowCount = 0
    var tableName = ""
    var query = ""
    try {
      con = getConnection
      CheckTableExists(containerName)

      tableName = toFullTableName(containerName)
      query = "select count(*) from " + tableName
      if (whereClause != null) {
        query = query + whereClause
      }
      stmt = con.createStatement()
      rs = stmt.executeQuery(query);
      while (rs.next()) {
        rowCount = rs.getInt(1)
      }
      rowCount
    } catch {
      case e: Exception => {
        throw CreateDMLException("Failed to fetch data from the table " + tableName + ":" + "query => " + query + ":" + e.getMessage(), e)
      }
    } finally {
      if (rs != null) {
        rs.close
      }
      if (stmt != null) {
        stmt.close
      }
      if (con != null) {
        con.close
      }
    }
  }

  private def getData(tableName: String, query: String, callbackFunction: (Key, Value) => Unit): Unit = {
    var con: Connection = null
    var stmt: Statement = null
    var rs: ResultSet = null
    logger.info("Fetch the results of " + query)
    try {
      con = getConnection

      stmt = con.createStatement()
      rs = stmt.executeQuery(query);
      while (rs.next()) {
        var timePartition = rs.getLong(1)
        var keyStr = rs.getString(2)
        var tId = rs.getLong(3)
        var rId = rs.getInt(4)
        var st = rs.getString(5)
        var ba = rs.getBytes(6)
        val bucketKey = if (keyStr != null) keyStr.split(",").toArray else new Array[String](0)
        var key = new Key(timePartition, bucketKey, tId, rId)
        // yet to understand how split serializerType and serializedInfo from ba
        // so hard coding serializerType to "kryo" for now
        var value = new Value(st, ba)
        if (callbackFunction != null)
          (callbackFunction)(key, value)
      }
    } catch {
      case e: Exception => {
        throw CreateDMLException("Failed to fetch data from the table " + tableName + ":" + "query => " + query + ":" + e.getMessage(), e)
      }
    } finally {
      if (rs != null) {
        rs.close
      }
      if (stmt != null) {
        stmt.close
      }
      if (con != null) {
        con.close
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
    var con: Connection = null
    var stmt: Statement = null
    var rs: ResultSet = null
    try {
      con = getConnection

      stmt = con.createStatement()
      rs = stmt.executeQuery(query);
      while (rs.next()) {
        var timePartition = rs.getLong(1)
        var keyStr = rs.getString(2)
        var tId = rs.getLong(3)
        var rId = rs.getInt(4)
        val bucketKey = if (keyStr != null) keyStr.split(",").toArray else new Array[String](0)
        var key = new Key(timePartition, bucketKey, tId, rId)
        if (callbackFunction != null)
          (callbackFunction)(key)
      }
    } catch {
      case e: Exception => {
        throw CreateDMLException("Failed to fetch data from the table " + tableName + ":" + "query => " + query + ":" + e.getMessage(), e)
      }
    } finally {
      if (rs != null) {
        rs.close
      }
      if (stmt != null) {
        stmt.close
      }
      if (con != null) {
        con.close
      }
    }
  }

  override def getKeys(containerName: String, callbackFunction: (Key) => Unit): Unit = {
    CheckTableExists(containerName)
    var tableName = toFullTableName(containerName)
    var query = "select timePartition,bucketKey,transactionId,rowId from " + tableName
    getKeys(tableName, query, callbackFunction)
  }

  override def getKeys(containerName: String, keys: Array[Key], callbackFunction: (Key) => Unit): Unit = {
    var con: Connection = null
    var pstmt: PreparedStatement = null
    var tableName = toFullTableName(containerName)
    var query = ""
    try {
      CheckTableExists(containerName)
      con = getConnection

      query = "select timePartition,bucketKey,transactionId,rowId from " + tableName + " where timePartition = ? and bucketKey = ? and transactionid = ? and rowId = ?"
      pstmt = con.prepareStatement(query)
      keys.foreach(key => {
        pstmt.setLong(1, key.timePartition)
        pstmt.setString(2, key.bucketKey.mkString(","))
        pstmt.setLong(3, key.transactionId)
        pstmt.setInt(4, key.rowId)
        var rs = pstmt.executeQuery();
        while (rs.next()) {
          var timePartition = rs.getLong(1)
          var keyStr = rs.getString(2)
          var tId = rs.getLong(3)
          var rId = rs.getInt(4)
          val bucketKey = if (keyStr != null) keyStr.split(",").toArray else new Array[String](0)
          var key = new Key(timePartition, bucketKey, tId, rId)
          if (callbackFunction != null)
            (callbackFunction)(key)
        }
      })
    } catch {
      case e: Exception => {
        throw CreateDMLException("Failed to fetch data from the table " + tableName + ":" + "query => " + query + ":" + e.getMessage(), e)
      }
    } finally {
      if (pstmt != null) {
        pstmt.close
      }
      if (con != null) {
        con.close
      }
    }
  }

  override def get(containerName: String, keys: Array[Key], callbackFunction: (Key, Value) => Unit): Unit = {
    var con: Connection = null
    var pstmt: PreparedStatement = null
    var tableName = toFullTableName(containerName)
    var query = ""
    try {
      CheckTableExists(containerName)
      con = getConnection

      query = "select serializerType,serializedInfo from " + tableName + " where timePartition = ? and bucketKey = ? and transactionid = ? and rowId = ?"
      pstmt = con.prepareStatement(query)
      keys.foreach(key => {
        pstmt.setLong(1, key.timePartition)
        pstmt.setString(2, key.bucketKey.mkString(","))
        pstmt.setLong(3, key.transactionId)
        pstmt.setInt(4, key.rowId)
        var rs = pstmt.executeQuery();
        while (rs.next()) {
          var st = rs.getString(1)
          var ba = rs.getBytes(2)
          var value = new Value(st, ba)
          if (callbackFunction != null)
            (callbackFunction)(key, value)
        }
      })
    } catch {
      case e: Exception => {
        throw CreateDMLException("Failed to fetch data from the table " + tableName + ":" + "query => " + query + ":" + e.getMessage(), e)
      }
    } finally {
      if (pstmt != null) {
        pstmt.close
      }
      if (con != null) {
        con.close
      }
    }
  }

  override def get(containerName: String, time_ranges: Array[TimeRange], callbackFunction: (Key, Value) => Unit): Unit = {
    CheckTableExists(containerName)
    var tableName = toFullTableName(containerName)
    time_ranges.foreach(time_range => {
      var query = "select timePartition,bucketKey,transactionId,rowId,serializerType,serializedInfo from " + tableName + " where timePartition >= " + time_range.beginTime + " and timePartition <= " + time_range.endTime
      logger.debug("query => " + query)
      getData(tableName, query, callbackFunction)
    })
  }

  override def getKeys(containerName: String, time_ranges: Array[TimeRange], callbackFunction: (Key) => Unit): Unit = {
    CheckTableExists(containerName)
    var tableName = toFullTableName(containerName)
    time_ranges.foreach(time_range => {
      var query = "select timePartition,bucketKey,transactionId,rowId from " + tableName + " where timePartition >= " + time_range.beginTime + " and timePartition <= " + time_range.endTime
      logger.debug("query => " + query)
      getKeys(tableName, query, callbackFunction)
    })
  }

  override def get(containerName: String, time_ranges: Array[TimeRange], bucketKeys: Array[Array[String]], callbackFunction: (Key, Value) => Unit): Unit = {
    var con: Connection = null
    var pstmt: PreparedStatement = null
    var tableName = toFullTableName(containerName)
    var query = ""
    try {
      CheckTableExists(containerName)
      //con = DriverManager.getConnection(jdbcUrl);
      con = getConnection

      time_ranges.foreach(time_range => {
        query = "select timePartition,bucketKey,transactionId,rowId,serializerType,serializedInfo from " + tableName + " where timePartition >= " + time_range.beginTime + " and timePartition <= " + time_range.endTime + " and bucketKey = ? "
        pstmt = con.prepareStatement(query)
        bucketKeys.foreach(bucketKey => {
          pstmt.setString(1, bucketKey.mkString(","))
          var rs = pstmt.executeQuery();
          while (rs.next()) {
            var timePartition = rs.getLong(1)
            var keyStr = rs.getString(2)
            var tId = rs.getLong(3)
            var rId = rs.getInt(4)
            var st = rs.getString(5)
            var ba = rs.getBytes(6)
            val bucketKey = if (keyStr != null) keyStr.split(",").toArray else new Array[String](0)
            var key = new Key(timePartition, bucketKey, tId, rId)
            var value = new Value(st, ba)
            if (callbackFunction != null)
              (callbackFunction)(key, value)
          }
        })
        if (pstmt != null) {
          pstmt.close
          pstmt = null
        }
      })
    } catch {
      case e: Exception => {
        throw CreateDMLException("Failed to fetch data from the table " + tableName + ":" + "query => " + query + ":" + e.getMessage(), e)
      }
    } finally {
      if (pstmt != null) {
        pstmt.close
      }
      if (con != null) {
        con.close
      }
    }
  }

  override def getKeys(containerName: String, time_ranges: Array[TimeRange], bucketKeys: Array[Array[String]], callbackFunction: (Key) => Unit): Unit = {
    var con: Connection = null
    var pstmt: PreparedStatement = null
    var tableName = toFullTableName(containerName)
    var query = ""
    try {
      CheckTableExists(containerName)
      con = getConnection

      time_ranges.foreach(time_range => {
        query = "select timePartition,bucketKey,transactionId,rowId from " + tableName + " where timePartition >= " + time_range.beginTime + " and timePartition <= " + time_range.endTime + " and bucketKey = ? "
        logger.debug("query => " + query)
        pstmt = con.prepareStatement(query)
        bucketKeys.foreach(bucketKey => {
          pstmt.setString(1, bucketKey.mkString(","))
          var rs = pstmt.executeQuery();
          while (rs.next()) {
            var timePartition = rs.getLong(1)
            var keyStr = rs.getString(2)
            var tId = rs.getLong(3)
            var rId = rs.getInt(4)
            val bucketKey = if (keyStr != null) keyStr.split(",").toArray else new Array[String](0)
            var key = new Key(timePartition, bucketKey, tId, rId)
            if (callbackFunction != null)
              (callbackFunction)(key)
          }
        })
        if (pstmt != null) {
          pstmt.close
          pstmt = null
        }
      })
    } catch {
      case e: Exception => {
        throw CreateDMLException("Failed to fetch data from the table " + tableName + ":" + "query => " + query + ":" + e.getMessage(), e)
      }
    } finally {
      if (pstmt != null) {
        pstmt.close
      }
      if (con != null) {
        con.close
      }
    }
  }

  override def get(containerName: String, bucketKeys: Array[Array[String]], callbackFunction: (Key, Value) => Unit): Unit = {
    var con: Connection = null
    var pstmt: PreparedStatement = null
    var tableName = toFullTableName(containerName)
    var query = ""
    try {
      CheckTableExists(containerName)
      con = getConnection

      query = "select timePartition,bucketKey,transactionId,rowId,serializerType,serializedInfo from " + tableName + " where  bucketKey = ? "
      pstmt = con.prepareStatement(query)
      bucketKeys.foreach(bucketKey => {
        pstmt.setString(1, bucketKey.mkString(","))
        var rs = pstmt.executeQuery();
        while (rs.next()) {
          var timePartition = rs.getLong(1)
          var keyStr = rs.getString(2)
          var tId = rs.getLong(3)
          var rId = rs.getInt(4)
          var st = rs.getString(5)
          var ba = rs.getBytes(6)
          val bucketKey = if (keyStr != null) keyStr.split(",").toArray else new Array[String](0)
          var key = new Key(timePartition, bucketKey, tId, rId)
          var value = new Value(st, ba)
          if (callbackFunction != null)
            (callbackFunction)(key, value)
        }
      })
    } catch {
      case e: Exception => {
        throw CreateDMLException("Failed to fetch data from the table " + tableName + ":" + "query => " + query + ":" + e.getMessage(), e)
      }
    } finally {
      if (pstmt != null) {
        pstmt.close
      }
      if (con != null) {
        con.close
      }
    }
  }

  override def getKeys(containerName: String, bucketKeys: Array[Array[String]], callbackFunction: (Key) => Unit): Unit = {
    var con: Connection = null
    var pstmt: PreparedStatement = null
    var tableName = toFullTableName(containerName)
    var query = ""
    try {
      CheckTableExists(containerName)
      con = getConnection

      query = "select timePartition,bucketKey,transactionId,rowId from " + tableName + " where  bucketKey = ? "
      pstmt = con.prepareStatement(query)
      bucketKeys.foreach(bucketKey => {
        pstmt.setString(1, bucketKey.mkString(","))
        var rs = pstmt.executeQuery();
        while (rs.next()) {
          var timePartition = rs.getLong(1)
          var keyStr = rs.getString(2)
          var tId = rs.getLong(3)
          var rId = rs.getInt(4)
          val bucketKey = if (keyStr != null) keyStr.split(",").toArray else new Array[String](0)
          var key = new Key(timePartition, bucketKey, tId, rId)
          if (callbackFunction != null)
            (callbackFunction)(key)
        }
      })
    } catch {
      case e: Exception => {
        throw CreateDMLException("Failed to fetch data from the table " + tableName + ":" + "query => " + query + ":" + e.getMessage(), e)
      }
    } finally {
      if (pstmt != null) {
        pstmt.close
      }
      if (con != null) {
        con.close
      }
    }
  }

  override def beginTx(): Transaction = {
    new SqlServerAdapterTx(this)
  }

  override def endTx(tx: Transaction): Unit = {}

  override def commitTx(tx: Transaction): Unit = {}

  override def rollbackTx(tx: Transaction): Unit = {}

  override def Shutdown(): Unit = {
    logger.info("close the connection pool")
  }

  private def TruncateContainer(containerName: String): Unit = {
    var con: Connection = null
    var stmt: Statement = null
    var tableName = toFullTableName(containerName)
    var query = ""
    try {
      CheckTableExists(containerName)
      con = getConnection

      query = "truncate table " + tableName
      stmt = con.createStatement()
      stmt.executeUpdate(query);
    } catch {
      case e: Exception => {
        throw CreateDDLException("Failed to fetch data from the table " + tableName + ":" + "query => " + query + ":" + e.getMessage(), e)
      }
    } finally {
      if (stmt != null) {
        stmt.close
      }
      if (con != null) {
        con.close
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
    var con: Connection = null
    var stmt: Statement = null
    var rs: ResultSet = null
    var tableName = toTableName(containerName)
    var fullTableName = toFullTableName(containerName)
    var query = ""
    try {
      con = getConnection
      // check if the container already dropped
      val dbm = con.getMetaData();
      rs = dbm.getTables(null, SchemaName, tableName, null);
      if (!rs.next()) {
        logger.debug("The table " + fullTableName + " may have beem dropped already ")
      } else {
        query = "drop table " + fullTableName
        stmt = con.createStatement()
        stmt.executeUpdate(query);
      }
    } catch {
      case e: Exception => {
        throw CreateDDLException("Failed to drop the table " + fullTableName + ":" + "query => " + query + ":" + e.getMessage(), e)
      }
    } finally {
      if (rs != null) {
        rs.close
      }
      if (stmt != null) {
        stmt.close
      }
      if (con != null) {
        con.close
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

  private def CreateContainer(containerName: String): Unit = lock.synchronized {
    var con: Connection = null
    var stmt: Statement = null
    var rs: ResultSet = null
    var tableName = toTableName(containerName)
    var fullTableName = toFullTableName(containerName)
    var query = ""
    try {
      con = getConnection
      // check if the container already exists
      val dbm = con.getMetaData();
      rs = dbm.getTables(null, SchemaName, tableName, null);
      if (rs.next()) {
        logger.debug("The table " + tableName + " already exists ")
      } else {
        query = "create table " + fullTableName + "(timePartition bigint,bucketKey varchar(1024), transactionId bigint, rowId Int, serializerType varchar(128), serializedInfo varbinary(max))"
        stmt = con.createStatement()
        stmt.executeUpdate(query);
        stmt.close
        var index_name = "ix_" + tableName
	if( clusteredIndex.equalsIgnoreCase("YES") ){
	  logger.info("Creating clustered index...")
          query = "create clustered index " + index_name + " on " + fullTableName + "(timePartition,bucketKey,transactionId,rowId)"
	}
	else{
	  logger.info("Creating non-clustered index...")
          query = "create index " + index_name + " on " + fullTableName + "(timePartition,bucketKey,transactionId,rowId)"
	}
        stmt = con.createStatement()
        stmt.executeUpdate(query);
        stmt.close
        index_name = "ix1_" + tableName
        query = "create index " + index_name + " on " + fullTableName + "(bucketKey,transactionId,rowId)"
        stmt = con.createStatement()
        stmt.executeUpdate(query);
        stmt.close
        index_name = "ix2_" + tableName
        query = "create index " + index_name + " on " + fullTableName + "(timePartition,bucketKey)"
        stmt = con.createStatement()
        stmt.executeUpdate(query);
      }
    } catch {
      case e: Exception => {
        throw CreateDDLException("Failed to create table or index " + tableName + ": ddl = " + query + ":" + e.getMessage(), e)
      }
    } finally {
      if (rs != null) {
        rs.close
      }
      if (stmt != null) {
        stmt.close
      }
      if (con != null) {
        con.close
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
}

class SqlServerAdapterTx(val parent: DataStore) extends Transaction {

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

// To create SqlServer Datastore instance
object SqlServerAdapter extends StorageAdapterObj {
  override def CreateStorageAdapter(kvManagerLoader: KamanjaLoaderInfo, datastoreConfig: String): DataStore = new SqlServerAdapter(kvManagerLoader, datastoreConfig)
}
