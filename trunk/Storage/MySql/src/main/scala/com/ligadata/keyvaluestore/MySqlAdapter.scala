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
 * When testing mysql for MetadataAPI operations on my Centos 6.6,
 * I ran into few exceptions. Had to configure the following
 * mysql paramters in /etc/my.cnf
max_allowed_packet=500M
#
# Remove leading # and set to the amount of RAM for the most important data
# cache in MySQL. Start at 70% of total RAM for dedicated server, else 10%.
innodb_buffer_pool_size=512M
innodb_log_file_size=256M

* Also, we need to configure more parameters to allow for bucketKey
* size of 1024 characters
* For now, I changed it to bucketKey(512) to avoid the following error
* "Specified key was too long; max key length is 767 bytes"
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
import java.sql.{ Driver, DriverPropertyInfo }
import java.sql.Timestamp
import java.util.Properties
import org.apache.commons.dbcp.BasicDataSource

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

class MySqlAdapter(val kvManagerLoader: KamanjaLoaderInfo, val datastoreConfig: String) extends DataStore {

  private[this] val lock = new Object

  val dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

  val adapterConfig = if (datastoreConfig != null) datastoreConfig.trim else ""
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
  private val loadedJars: TreeSet[String] = new TreeSet[String];
  private val clsLoader = new JdbcClassLoader(ClassLoader.getSystemClassLoader().asInstanceOf[URLClassLoader].getURLs(), getClass().getClassLoader())

  private var containerList: scala.collection.mutable.Set[String] = scala.collection.mutable.Set[String]()

  //val classLoader = new KamanjaLoaderInfo
  //val classLoader = URLClassLoader

  if (adapterConfig.size == 0) {
    throw new Exception("Not found valid MySql Configuration.")
  }

  logger.debug("MySql configuration:" + adapterConfig)
  var parsed_json: Map[String, Any] = null
  try {
    val json = parse(adapterConfig)
    if (json == null || json.values == null) {
      logger.error("Failed to parse MySql JSON configuration string:" + adapterConfig)
      throw new Exception("Failed to parse MySql JSON configuration string:" + adapterConfig)
    }
    parsed_json = json.values.asInstanceOf[Map[String, Any]]
  } catch {
    case e: Exception => {
      logger.error("Failed to parse MySql JSON configuration string:%s. Reason:%s Message:%s".format(adapterConfig, e.getCause, e.getMessage))
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
          logger.error("Failed to parse MySql Adapter Specific JSON configuration string:" + adapterSpecificStr)
          throw new Exception("Failed to parse MySql Adapter Specific JSON configuration string:" + adapterSpecificStr)
        }
        adapterSpecificConfig_json = json.values.asInstanceOf[Map[String, Any]]
      } catch {
        case e: Exception => {
          logger.error("Failed to parse MySql Adapter Specific JSON configuration string:%s. Reason:%s Message:%s".format(adapterSpecificStr, e.getCause, e.getMessage))
          throw e
        }
      }
    }
  }

  // Read all sqlServer parameters
  var hostname: String = null;
  if (parsed_json.contains("hostname")) {
    hostname = parsed_json.get("hostname").get.toString.trim
  } else {
    throw new ConnectionFailedException("Unable to find hostname in adapterConfig ")
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
    throw new ConnectionFailedException("Unable to find database in adapterConfig ")
  }

  var user: String = null;
  if (parsed_json.contains("user")) {
    user = parsed_json.get("user").get.toString.trim
  } else {
    throw new ConnectionFailedException("Unable to find user in adapterConfig ")
  }

  var password: String = null;
  if (parsed_json.contains("password")) {
    password = parsed_json.get("password").get.toString.trim
  } else {
    throw new ConnectionFailedException("Unable to find password in adapterConfig ")
  }

  var jarpaths: String = null;
  if (parsed_json.contains("jarpaths")) {
    jarpaths = parsed_json.get("jarpaths").get.toString.trim
  } else {
    throw new ConnectionFailedException("Unable to find jarpaths in adapterConfig ")
  }

  var jdbcJar: String = null;
  if (parsed_json.contains("jdbcJar")) {
    jdbcJar = parsed_json.get("jdbcJar").get.toString.trim
  } else {
    throw new ConnectionFailedException("Unable to find jdbcJar in adapterConfig ")
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

  logger.info("hostname => " + hostname)
  logger.info("jarpaths => " + jarpaths)
  logger.info("jdbcJar  => " + jdbcJar)

  var sqlServerInstance:String = hostname
  if( instanceName != null ){
    sqlServerInstance = sqlServerInstance + "\\" + instanceName
  }
  if( portNumber != null ){
    sqlServerInstance = sqlServerInstance + ":" + portNumber
  }

  var jdbcUrl = "jdbc:mysql://" + sqlServerInstance + "/" + database + "?user=" + user + "?password=" + password
  var jdbcUrl1 = "jdbc:mysql://" + sqlServerInstance + "/" + database

  logger.info("jdbcUrl  => " + jdbcUrl)

  var jars = new Array[String](0)
  var jar = jarpaths + "/" + jdbcJar
  jars = jars :+ jar
  LoadJars(jars)

  val driverType = "com.mysql.jdbc.Driver"
  Class.forName(driverType, true, clsLoader)

  val d = Class.forName(driverType, true, clsLoader).newInstance.asInstanceOf[Driver]
  logger.info("%s:Registering Driver".format(GetCurDtTmStr))
  DriverManager.registerDriver(new DriverShim(d));

  // setup connection pooling using apache-commons-dbcp
  val dataSource = new BasicDataSource
  dataSource.setDriverClassName(driverType)
  dataSource.setUrl(jdbcUrl1)
  dataSource.setUsername(user)
  dataSource.setPassword(password)
  //dataSource.setMaxActive(maxActiveConnections);
  //dataSource.setMaxIdle(maxIdleConnections);
  //dataSource.setInitialSize(initialSize);

  // set the timezone to UTC for all time values
  TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

  private def GetCurDtTmStr: String = {
    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date(System.currentTimeMillis))
  }

  private def CheckTableExists(containerName: String): Unit = {
    if (containerList.contains(containerName)) {
      return
    } else {
      CreateContainer(containerName)
      containerList.add(containerName)
    }
  }

  private def RoundDateToSecs(d: Date): Date = {
    var c = Calendar.getInstance()
    if (d == null) {
      c.setTime(new Date(0))
      c.getTime
    } else {
      c.setTime(d)
      c.set(Calendar.MILLISECOND, 0)
      c.getTime
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
            logger.debug("%s:Jar %s already loaded to class path.".format(GetCurDtTmStr, jarNm))
          } else {
            clsLoader.addURL(fl.toURI().toURL())
            logger.debug("%s:Jar %s added to class path.".format(GetCurDtTmStr, jarNm))
            loadedJars += fl.getPath()
          }
        } catch {
          case e: Exception => {
            val errMsg = "Jar " + jarNm + " failed added to class path. Reason:%s Message:%s".format(e.getCause, e.getMessage)
            logger.error("Error:" + errMsg)
            throw new Exception(errMsg)
          }
        }
      } else {
        val errMsg = "Jar " + jarNm + " not found"
        throw new Exception(errMsg)
      }
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
    // database + "." + "dbo" + "." + containerName.replace('.','_')
    //database + "." + "dbo" + "." + toTableName(containerName)
    toTableName(containerName)
  }

  override def put(containerName: String, key: Key, value: Value): Unit = {
    var con: Connection = null
    var pstmt: PreparedStatement = null
    var cstmt: CallableStatement = null
    var tableName = toFullTableName(containerName)
    try {
      CheckTableExists(containerName)
      // con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection
      // put is sematically an upsert. An upsert is implemented using a merge
      // statement in sqlserver
      // Ideally a merge should be implemented as stored procedure
      // I am having some trouble in implementing stored procedure
      // We are implementing this as delete followed by insert for lack of time
      var deleteSql = "delete from " + tableName + " where timePartition = ? and bucketKey = ? and transactionid = ? and rowId = ?"
      logger.debug("deletesql => " + deleteSql)
      pstmt = con.prepareStatement(deleteSql)
      pstmt.setLong(1, key.timePartition)
      pstmt.setString(2, key.bucketKey.mkString(","))
      pstmt.setLong(3, key.transactionId)
      pstmt.setInt(4, key.rowId)
      pstmt.executeUpdate();

      var insertSql = "insert into " + tableName + "(timePartition,bucketKey,transactionId,rowId,serializerType,serializedInfo) values(?,?,?,?,?,?)"
      logger.debug("insertsql => " + insertSql)
      pstmt = con.prepareStatement(insertSql)
      pstmt.setLong(1, key.timePartition)
      pstmt.setString(2, key.bucketKey.mkString(","))
      pstmt.setLong(3, key.transactionId)
      pstmt.setInt(4, key.rowId)
      pstmt.setString(5, value.serializerType)
      pstmt.setBinaryStream(6, new java.io.ByteArrayInputStream(value.serializedInfo),
        value.serializedInfo.length)
      pstmt.executeUpdate();
      /*
      var proc = "\"{call PROC_UPSERT_" + tableName + "(?,?,?,?)}\""
      cstmt = con.prepareCall(proc)
      cstmt.setTimestamp(1,new java.sql.Timestamp(RoundDateToSecs(key.timePartition).getTime))
      cstmt.setString(2,key.bucketKey.mkString(","))
      cstmt.setLong(3,key.transactionId)
      cstmt.setBinaryStream(4,new java.io.ByteArrayInputStream(value.serializedInfo),
			   value.serializedInfo.length)
      cstmt.execute();
      */
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
        throw new Exception("Failed to save an object in the table " + tableName + ":" + e.getMessage())
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
    var deleteSql: String = null
    var insertSql: String = null
    var totalRowsDeleted = 0;
    var totalRowsInserted = 0;
    try {
      //con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection
      // we need to commit entire batch
      con.setAutoCommit(false)
      data_list.foreach(li => {
        var containerName = li._1
        CheckTableExists(containerName)
        var tableName = toFullTableName(containerName)
        var deleteSql = "delete from " + tableName + " where timePartition = ? and bucketKey = ? and transactionid = ? and rowId = ? "
        logger.debug("deletesql => " + deleteSql)
        pstmt = con.prepareStatement(deleteSql)
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
        var deleteCount = pstmt.executeBatch();
        deleteCount.foreach(cnt => { totalRowsDeleted += cnt });
        if (pstmt != null) {
          pstmt.close
        }
        logger.info("Deleted " + totalRowsDeleted + " rows from " + tableName)
        // insert rows 
        var insertSql = "insert into " + tableName + "(timePartition,bucketKey,transactionId,rowId,serializerType,serializedInfo) values(?,?,?,?,?,?)"
        pstmt = con.prepareStatement(insertSql)
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
          pstmt.setBinaryStream(6, new java.io.ByteArrayInputStream(value.serializedInfo),
            value.serializedInfo.length)
          // Add it to the batch
	  //pstmt.executeUpdate();
	  //totalRowsInserted = totalRowsInserted + 1
          pstmt.addBatch()
        })
        var insertCount = pstmt.executeBatch();
        insertCount.foreach(cnt => { totalRowsInserted += cnt });
        logger.info("Inserted " + totalRowsInserted + " rows into " + tableName)
        if (pstmt != null) {
          pstmt.close
        }
      })
      con.commit()
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
        throw new Exception("Batch put operation failed:" + e.getMessage())
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
    var totalRowsDeleted = 0;
    try {
      CheckTableExists(containerName)
      //con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection

      var deleteSql = "delete from " + tableName + " where timePartition = ? and bucketKey = ? and transactionid = ? and rowId = ?"
      pstmt = con.prepareStatement(deleteSql)
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
      deleteCount.foreach(cnt => { totalRowsDeleted += cnt });
      logger.info("Deleted " + totalRowsDeleted + " rows from " + tableName)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:" + stackTrace)
        throw new Exception("Failed to delete object(s) from the table " + tableName + ":" + e.getMessage())
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
    var totalRowsDeleted = 0;
    try {
      logger.info("begin time => " + dateFormat.format(time.beginTime))
      logger.info("end time => " + dateFormat.format(time.endTime))
      CheckTableExists(containerName)

      //con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection

      // we need to commit entire batch
      con.setAutoCommit(false)
      var deleteSql = "delete from " + tableName + " where timePartition >= ?  and timePartition <= ? and bucketKey = ?"
      pstmt = con.prepareStatement(deleteSql)
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
      deleteCount.foreach(cnt => { totalRowsDeleted += cnt });
      logger.info("Deleted " + totalRowsDeleted + " rows from " + tableName)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
        throw new Exception("Failed to delete object(s) from the table " + tableName + ":" + e.getMessage())
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
    try {
      //con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection
      CheckTableExists(containerName)

      tableName = toFullTableName(containerName)
      var query = "select count(*) from " + tableName
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
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:" + stackTrace)
        throw new Exception("Failed to fetch data from the table " + tableName + ":" + e.getMessage())
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
      //con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection

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
        (callbackFunction)(key, value)
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
        //throw new Exception("Failed to fetch data from the table " + tableName + ":" + e.getMessage())
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
      //con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection

      stmt = con.createStatement()
      rs = stmt.executeQuery(query);
      while (rs.next()) {
        var timePartition = rs.getLong(1)
        var keyStr = rs.getString(2)
        var tId = rs.getLong(3)
        var rId = rs.getInt(4)
        val bucketKey = if (keyStr != null) keyStr.split(",").toArray else new Array[String](0)
        var key = new Key(timePartition, bucketKey, tId, rId)
        (callbackFunction)(key)
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:" + stackTrace)
        //throw new Exception("Failed to fetch data from the table " + tableName + ":" + e.getMessage())
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
    try {
      CheckTableExists(containerName)
      //con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection

      var query = "select timePartition,bucketKey,transactionId,rowId from " + tableName + " where timePartition = ? and bucketKey = ? and transactionid = ? and rowId = ?"
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
          (callbackFunction)(key)
        }
      })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:" + stackTrace)
        //throw new Exception("Failed to fetch object(s) from the table " + tableName + ":" + e.getMessage())
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
    try {
      CheckTableExists(containerName)
      //con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection

      var query = "select serializerType,serializedInfo from " + tableName + " where timePartition = ? and bucketKey = ? and transactionid = ? and rowId = ?"
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
          // yet to understand how split serializerType and serializedInfo from ba
          // so hard coding serializerType to "kryo" for now
          var value = new Value(st, ba)
          (callbackFunction)(key, value)
        }
      })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:" + stackTrace)
        //throw new Exception("Failed to fetch object(s) from the table " + tableName + ":" + e.getMessage())
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
    try {
      CheckTableExists(containerName)
      //con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection

      time_ranges.foreach(time_range => {
	var query = "select timePartition,bucketKey,transactionId,rowId,serializerType,serializedInfo from " + tableName + " where timePartition >= " + time_range.beginTime + " and timePartition <= " + time_range.endTime + " and bucketKey = ? "
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
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
        //throw new Exception("Failed to fetch object(s) from the table " + tableName + ":" + e.getMessage())
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
    try {
      CheckTableExists(containerName)
      //con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection

      time_ranges.foreach(time_range => {
	var query = "select timePartition,bucketKey,transactionId,rowId from " + tableName + " where timePartition >= " + time_range.beginTime + " and timePartition <= " + time_range.endTime + " and bucketKey = ? "
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
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
        //throw new Exception("Failed to fetch object(s) from the table " + tableName + ":" + e.getMessage())
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
    try {
      CheckTableExists(containerName)
      //con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection

      var query = "select timePartition,bucketKey,transactionId,rowId,serializerType,serializedInfo from " + tableName + " where  bucketKey = ? "
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
          (callbackFunction)(key, value)
        }
      })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
        //throw new Exception("Failed to fetch object(s) from the table " + tableName + ":" + e.getMessage())
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
    try {
      CheckTableExists(containerName)
      //con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection

      var query = "select timePartition,bucketKey,transactionId,rowId from " + tableName + " where  bucketKey = ? "
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
          (callbackFunction)(key)
        }
      })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
        //throw new Exception("Failed to fetch object(s) from the table " + tableName + ":" + e.getMessage())
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
    new MySqlAdapterTx(this)
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
    try {
      CheckTableExists(containerName)
      //con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection

      var query = "truncate table " + tableName
      stmt = con.createStatement()
      stmt.executeUpdate(query);
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:" + stackTrace)
        throw new Exception("Failed to truncate table " + tableName + ":" + e.getMessage())
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
    try {
      //con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection()
      // check if the container already dropped
      val dbm = con.getMetaData();
      rs = dbm.getTables(null, null, tableName, null);
      if (!rs.next()) {
        logger.debug("The table " + fullTableName + " may have beem dropped already ")
      } else {
        var query = "drop table " + fullTableName
        stmt = con.createStatement()
        stmt.executeUpdate(query);
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
        throw new Exception("Failed to drop the table " + fullTableName + ":" + e.getMessage())
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
    try {
      //con = DriverManager.getConnection(jdbcUrl);
      con = dataSource.getConnection

      // check if the container already exists
      val dbm = con.getMetaData();
      rs = dbm.getTables(null, null, tableName, null);
      if (rs.next()) {
        logger.debug("The table " + tableName + " already exists ")
      } else {
        var query = "create table " + fullTableName + "(timePartition bigint,bucketKey varchar(512), transactionId bigint, rowId Int, serializerType varchar(128), serializedInfo longblob)"
        stmt = con.createStatement()
        stmt.executeUpdate(query);
        stmt.close
        var clustered_index_name = "ix_" + tableName
        query = "create unique index " + clustered_index_name + " on " + fullTableName + "(timePartition,bucketKey,transactionId,rowId)"
        stmt = con.createStatement()
        stmt.executeUpdate(query);
        stmt.close
        var index_name = "ix1_" + tableName
        query = "create index " + index_name + " on " + fullTableName + "(bucketKey,transactionId,rowId)"
        stmt = con.createStatement()
        stmt.executeUpdate(query);
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Stacktrace:" + stackTrace)
        //throw new Exception("Failed to create the table " + tableName + ":" + e.getMessage())
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

class MySqlAdapterTx(val parent: DataStore) extends Transaction {

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

// To create MySql Datastore instance
object MySqlAdapter extends StorageAdapterObj {
  override def CreateStorageAdapter(kvManagerLoader: KamanjaLoaderInfo, datastoreConfig: String): DataStore = new MySqlAdapter(kvManagerLoader, datastoreConfig)
}
