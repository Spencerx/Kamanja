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

package com.ligadata.audit.adapters

import com.ligadata.AuditAdapterInfo._
import org.apache.logging.log4j._
import java.util.Date
import java.util.Calendar
import com.datastax.driver.core.ConsistencyLevel
import com.datastax.driver.core.Cluster
import com.datastax.driver.core.Session
import com.ligadata.Exceptions._

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

class AuditCassandraAdapter extends AuditAdapter {
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)

  var adapterProperties: scala.collection.mutable.Map[String,String] = scala.collection.mutable.Map[String,String]()

  // Read all cassandra parameters
  var hostnames: String = _
  var keyspace: String = _
  var user: String = _
  var password: String = _
  var table: String = _
  var replication_class: String = _
  var replication_factor: String = _
  var consistencylevelRead: com.datastax.driver.core.ConsistencyLevel = _
  var consistencylevelWrite: com.datastax.driver.core.ConsistencyLevel = _
  var consistencylevelDelete: com.datastax.driver.core.ConsistencyLevel = _
  
  var clusterBuilder = Cluster.builder()
  var cluster: Cluster = _
  var session: Session = _
  var keyspace_exists = false
  
  var insertStmt: com.datastax.driver.core.PreparedStatement = null

  /**
   * init - This is a method that must be implemented by the adapter impl.  This method should preform any necessary
   *        steps to set up the destination of the Audit Records (in this case Cassandra Keyspace/Table).
   * @param String - class name that contains parameters required to initialize the Cassandra connection
   * @return Unit
   */
  override def init (parms: String): Unit = {
    
    if (parms != null) {
      logger.info("CASSANDRA AUDIT: Initializing to "+parms)
      initPropertiesFromFile(parms)   
    }
    
    hostnames = adapterProperties.getOrElse("hostlist", "localhost").toString; 
    keyspace = adapterProperties.getOrElse("schema", "metadata").toString;
    table = adapterProperties.getOrElse("table", "metadata_audit").toString;
    user = adapterProperties.getOrElse("user", "").toString;
    password = adapterProperties.getOrElse("password", "").toString;
    replication_class = adapterProperties.getOrElse("replication_class", "SimpleStrategy").toString
    replication_factor = adapterProperties.getOrElse("replication_factor", "1").toString
    consistencylevelRead = ConsistencyLevel.valueOf("ONE")
    consistencylevelWrite = ConsistencyLevel.valueOf("ANY")
    consistencylevelDelete = ConsistencyLevel.valueOf("ANY")  
    
    try {
      clusterBuilder.addContactPoints(hostnames)
      if (!user.equalsIgnoreCase(""))
        clusterBuilder.withCredentials(user, password)
      cluster = clusterBuilder.build()

      if (cluster.getMetadata().getKeyspace(keyspace) == null){
        logger.warn("The keyspace " + keyspace + " doesn't exist yet, we will create a new keyspace and continue")
        // create a session that is not associated with a key space yet so we can create one if needed
        session = cluster.connect();
        // create keyspace if not exists
        val createKeySpaceStmt = "CREATE KEYSPACE IF NOT EXISTS " + keyspace + " with replication = {'class':'" + replication_class + "', 'replication_factor':" + replication_factor + "};"
        try {
          session.execute(createKeySpaceStmt);
        } catch {
          case e: Exception => {
              val stackTrace =   StackTrace.ThrowableTraceString(e)
              logger.debug("Stacktrace:"+stackTrace)
              throw new CreateKeySpaceFailedException("Unable to create keyspace " + keyspace + ":" + e.getMessage()) 
            }
        }
      
        // make sure the session is associated with the new tablespace, can be expensive if we create recycle sessions  too often
        session.close()
        session = cluster.connect(keyspace)
      }
      else{
        keyspace_exists = true
        logger.debug("The keyspace " + keyspace + " exists, connect to existing keyspace and continue")
        session = cluster.connect(keyspace)
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:"+stackTrace)
        throw new ConnectionFailedException("Unable to connect to cassandra at " + hostnames + ":" + e.getMessage())
      }
    } 
       
    // Check if table to store AUDIT info already exists or create if needed
    val createTblStmt = "CREATE TABLE IF NOT EXISTS " + table + 
    " (auditYear  int " +
    " ,actionTime  bigint " +
    " ,userOrRole varchar" +
    " ,userPrivilege varchar" +
    " ,action varchar" +
    " ,objectAccessed varchar" +
    " ,success varchar" +
    " ,transactionId varchar" +
    " ,notes varchar " +
    " ,primary key(auditYear,actionTime) " +
    ");"

    session.execute(createTblStmt);
    
    // Prepare statements that will be needed to insert audit data into cassandra instance.
    var insertSql = "INSERT INTO " + table + 
     " (audityear,actiontime,userorrole,userprivilege,action,objectaccessed,success,transactionid,notes) " +
     " values(?,?,?,?,?,?,?,?,?);"
    insertStmt = session.prepare(insertSql)
    
    logger.info("CASSANDRA AUDIT: Initialized with "+keyspace+"."+hostnames+"."+table)
  }
  
  
  /**
   * getYear - get the 4 digit format of the current year
   */
  private def getYear(dt: Long) : Int = {
    val cal = Calendar.getInstance();
    cal.setTime(new Date(dt));
    val year = cal.get(Calendar.YEAR);
    year
  }    

  /**
   * addAuditRecord - adds the auditRecord to the audit table.
   */
  def addAuditRecord(rec: AuditRecord) = {
    try{
      logger.debug("Audit Event occured")
      var at:java.lang.Long = rec.actionTime.toLong

      var year:java.lang.Integer = 0
      year = getYear(at)

      val e = session.execute(insertStmt.bind(year,
					      at,
					      rec.userOrRole,
					      rec.userPrivilege,
					      rec.action,
					      rec.objectAccessed,
					      rec.success,
					      rec.transactionId,
					      rec.notes).setConsistencyLevel(consistencylevelWrite))

      if (e.getColumnDefinitions().size() > 1){
        logger.warn("Unexpected value for " + e.getColumnDefinitions().size())
      }
    }catch {
      case e: Exception => 
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:"+stackTrace)
        throw new Exception(e.getMessage())
    }
  }
  
  /**
   * getAuditRecords - gets the array of Audit Records that exist in the Cassandra audit tables.
   *                   Filters:
   *                       - startTime (if none specified, defaults to 10 minutes prior to the call
   *                       - endTime (if none specified, current time is sued
   *                       - user
   *                       - action
   *                       - objectAccessed
   *                       
   */
  def getAuditRecord(startTime: Date, endTime: Date, userOrRole: String, action: String, objectAccessed: String): Array[AuditRecord] = {
    var auditRecords = new Array[AuditRecord](0)
    try{
      var selectSql = "SELECT actiontime, action, userorrole, objectAccessed, success, userprivilege, notes, transactionid from " + table + " where "
      
      // Set up the SELECT PREDICATE.  will always have start and end times.
      var stime = (new Date().getTime() - 10 * 60 * 1000L)
      if( startTime != null ){
        stime = startTime.getTime()
      }

      var year:java.lang.Integer = 0;
      year = getYear(stime)

      selectSql = selectSql + " audityear = " +  year.toString()

      selectSql = selectSql + " and actiontime >= " +  stime.toString()
      var etime = new Date().getTime()
      if( endTime != null ){
        etime = endTime.getTime()
      }
      selectSql = selectSql + " and actiontime <= " +  etime.toString()
      if( userOrRole != null ){
        selectSql = selectSql + " and userOrRole = '" + userOrRole + "'"
      }
      if( action != null ){
        selectSql = selectSql + " and action = '" + action + "'"
      }
      if( objectAccessed != null ){
        selectSql = selectSql + " and objectAccessed = '" + objectAccessed + "'"
      }
      
      // Execute the selelct
      logger.debug(selectSql)
      val rs = session.execute(selectSql)

      // Process the results
      var i = 0
      val rowList = rs.all()
      var rowCount = rowList.size()
      for(i <- 0 to rowCount - 1){
        var row = rowList.get(i)
        val a = new AuditRecord()
        a.actionTime     = row.getLong("actiontime").toString()
        a.userOrRole     = row.getString("userorrole")
        a.userPrivilege  = row.getString("userprivilege")
        a.action         = row.getString("action")
        a.objectAccessed = row.getString("objectAccessed")
        a.success        = row.getString("success")
        a.transactionId  = row.getString("transactionId")
        a.notes          = row.getString("notes")
        auditRecords = auditRecords :+ a
      }
      auditRecords
    } catch {
      case e:Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:"+stackTrace)
        throw new Exception("Failed to fetch audit records: " + e.getMessage())
      }
    }
  }

  /**
   * Shutdown - clean up all the resources used by this class.
   */
  override def Shutdown() =  {
    session.close()
    cluster.close()
  }
  
  override def TruncateStore: Unit = {
    try {
      var stmt = session.prepare("truncate " + table + ";")
      val rs = session.execute(stmt.bind().setConsistencyLevel(consistencylevelDelete))
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        throw new Exception("Failed to truncate Audit Store: " + e.getMessage())
      }
    }
  }

  private def initPropertiesFromFile(parmFile: String): Unit = {
    
    try {
       scala.io.Source.fromFile(parmFile).getLines.foreach(line => {
         var parsedLine = line.split('=')
         adapterProperties(parsedLine(0).trim) = parsedLine(1).trim      
       })
    } catch {
      case e:Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:"+stackTrace)
        throw new Exception("Failed to read Audit Configuration: " + e.getMessage())
      }     
    }
  }
}

