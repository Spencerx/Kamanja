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

import org.apache.hadoop.hbase._
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.filter._
import org.apache.hadoop.hbase.filter.CompareFilter._
import org.apache.hadoop.hbase.util.Bytes

import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.io.compress.Compression.Algorithm;
import org.apache.hadoop.conf.Configuration;

import org.apache.hadoop.hbase._
import org.apache.logging.log4j._

import java.nio.ByteBuffer
import java.io.IOException

import java.util.Date
import java.util.Calendar
import com.ligadata.Exceptions._


//import org.apache.hadoop.hbase.util.Bytes;
/*
 * create 'default', 'value'
 *
 * put 'default', 'KEYKEY', 'value', 'ValueValue'
 *
 * scan 'default'
 *
 */

class AuditHBaseAdapter extends AuditAdapter
{
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
  var keyspace: String = _ 
  var hostnames: String = _
  var table: String = _
  
  var config = new org.apache.hadoop.conf.Configuration
  var connection:HConnection = _
  var tableHBase: org.apache.hadoop.hbase.client.HTableInterface = _
  var adapterProperties: scala.collection.mutable.Map[String,String] =   scala.collection.mutable.Map[String,String]()
  
  /**
   * init - This method implements all the needed steps required to use this adapter.  
   * @param String - file name where all the information needed for initialization is stored.
   * @return Unit
   */
  override def init(parms: String): Unit = {
 
      if (parms != null) {
        logger.info("HBASE AUDIT: Initializing to "+parms)
        initPropertiesFromFile(parms)   
      }
      
      keyspace = adapterProperties.getOrElse("schema", "default") ;
      hostnames = adapterProperties.getOrElse("hostlist", "localhost")
      table = adapterProperties.getOrElse("table", "metadata_audit")
     
      config.setInt("zookeeper.session.timeout", 5000);
      config.setInt("zookeeper.recovery.retry", 1);
      config.setInt("hbase.client.retries.number", 3);
      config.setInt("hbase.client.pause", 5000);
  
      config.set("hbase.zookeeper.quorum", hostnames);
      try{
        connection = HConnectionManager.createConnection(config);
      }
      catch{
        case e:Exception => {
          val stackTrace = StackTrace.ThrowableTraceString(e)
          logger.debug("Stacktrace:"+stackTrace)
          throw new ConnectionFailedException("Unable to connect to hbase at " + hostnames + ":" + e.getMessage())
        }
      }
      createTable(table)
      tableHBase = connection.getTable(table);
      
      logger.info("HBASE AUDIT: Initialized with "+keyspace+"."+hostnames+"."+table)
  }
  
  private def createTable(tableName:String) : Unit = {
    val  admin = new HBaseAdmin(config);
    if (! admin.tableExists(tableName)) {
      val  tableDesc = new HTableDescriptor(TableName.valueOf(tableName));
      var  colDesc =  new HColumnDescriptor("actiontime".getBytes())
      tableDesc.addFamily(colDesc)
      colDesc =  new HColumnDescriptor("userorrole".getBytes())
      tableDesc.addFamily(colDesc)
      colDesc =  new HColumnDescriptor("userprivilege".getBytes())
      tableDesc.addFamily(colDesc)
      colDesc =  new HColumnDescriptor("action".getBytes())
      tableDesc.addFamily(colDesc)
      colDesc =  new HColumnDescriptor("objectaccessed".getBytes())
      tableDesc.addFamily(colDesc)
      colDesc =  new HColumnDescriptor("success".getBytes())
      tableDesc.addFamily(colDesc)
      colDesc =  new HColumnDescriptor("transactionid".getBytes())
      tableDesc.addFamily(colDesc)
      colDesc =  new HColumnDescriptor("notes".getBytes())
      tableDesc.addFamily(colDesc)

      admin.createTable(tableDesc);
    }
  }
  
  /**
   * addAuditRecord - Adds the Audit Record to the underlying storage for this adapter.
   * @param - AuditRecord
   * @return - Unit
   */
  def addAuditRecord(rec: AuditRecord) = {
    try{
      logger.info("HBASE AUDIT: Audit event ")
      var at:java.lang.Long = rec.actionTime.toLong
      var p = new Put(Bytes.toBytes(at.toString()))
      //p.add(Bytes.toBytes("actiontime"), Bytes.toBytes("base"),Bytes.toBytes(at))
      p.add(Bytes.toBytes("userorrole"), Bytes.toBytes("base"),Bytes.toBytes(rec.userOrRole))
      p.add(Bytes.toBytes("userprivilege"), Bytes.toBytes("base"),Bytes.toBytes(rec.userPrivilege))
      p.add(Bytes.toBytes("action"), Bytes.toBytes("base"),Bytes.toBytes(rec.action))
      p.add(Bytes.toBytes("objectaccessed"), Bytes.toBytes("base"),Bytes.toBytes(rec.objectAccessed))
      p.add(Bytes.toBytes("success"), Bytes.toBytes("base"),Bytes.toBytes(rec.success))
      p.add(Bytes.toBytes("transactionid"), Bytes.toBytes("base"),Bytes.toBytes(rec.transactionId))
      p.add(Bytes.toBytes("notes"), Bytes.toBytes("base"),Bytes.toBytes(rec.notes))
      tableHBase.put(p)
    } catch {
      case e:Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:"+stackTrace)
	      throw new Exception("Failed to save an object in HBase table " + table + ":" + e.getMessage())
      }
    }
  }

  def getAuditRecord(startTime: Date, endTime: Date, userOrRole: String, action: String, objectAccessed: String): Array[AuditRecord] = {
    var auditRecords = new Array[AuditRecord](0)
    try{
      var stime = (new Date().getTime() - 10 * 60 * 1000L)
      if( startTime != null ){
	stime = startTime.getTime()
      }

      var etime = new Date().getTime()
      if( endTime != null ){
	etime = endTime.getTime()
      }

      val filters = new java.util.ArrayList[Filter]()

      val filter1 = new SingleColumnValueFilter(Bytes.toBytes("actiontime"), Bytes.toBytes("base"),
						CompareOp.GREATER_OR_EQUAL, Bytes.toBytes(stime.toString()))
      filters.add(filter1);  

      val filter2 = new SingleColumnValueFilter(Bytes.toBytes("actiontime"), Bytes.toBytes("base"),
						CompareOp.LESS_OR_EQUAL, Bytes.toBytes(etime.toString()))
      filters.add(filter2);  

      if( userOrRole != null ){
	val filter3 = new SingleColumnValueFilter(Bytes.toBytes("userorrole"), Bytes.toBytes("base"),
						CompareOp.EQUAL, Bytes.toBytes(userOrRole))
	filters.add(filter3);  
      }

      if( action != null ){
	val filter4 = new SingleColumnValueFilter(Bytes.toBytes("action"), Bytes.toBytes("base"),
						CompareOp.EQUAL, Bytes.toBytes(action))
	filters.add(filter4);
      }

      if( objectAccessed != null ){
	val filter5 = new SingleColumnValueFilter(Bytes.toBytes("objectaccessed"), Bytes.toBytes("base"),
						CompareOp.EQUAL, Bytes.toBytes(objectAccessed))
	filters.add(filter5);  
      }

      val filterList1 = new FilterList(filters);  
  
      val scan = new Scan();  
      scan.setFilter(filterList1);  
      val rs = tableHBase.getScanner(scan);
      val it = rs.iterator()
      while( it.hasNext() ){
	val a = new AuditRecord()
	val r = it.next()
	a.actionTime = Bytes.toString(r.getRow())
	val kvit = r.list().iterator()
	while( kvit.hasNext() ){
	  val kv = kvit.next()
	  val q = Bytes.toString(kv.getFamily())
	  q match  {
	    case "actiontime" => {
	      a.actionTime = Bytes.toString(kv.getValue())
	    }
	    case "userorrole" => {
	      a.userOrRole = Bytes.toString(kv.getValue())
	    }
	    case "userprivilege" => {
	      a.userPrivilege = Bytes.toString(kv.getValue())
	    }
	    case "action" => {
	      a.action = Bytes.toString(kv.getValue())
	    }
	    case "objectaccessed" => {
	      a.objectAccessed = Bytes.toString(kv.getValue())
	    }
	    case "success" => {
	      a.success = Bytes.toString(kv.getValue())
	    }
	    case "transactionid" => {
	      a.transactionId = Bytes.toString(kv.getValue())
	    }
	    case "notes" => {
	      a.notes = Bytes.toString(kv.getValue())
	    }
	  }
	}
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
   * Shutdown - Shutdown all the resources used by this class.
   */
  override def Shutdown() = {
    if(tableHBase != null ){
      tableHBase.close()
      tableHBase = null
    }
    if( connection != null ){
      connection.close()
      connection = null
    }
  }
  
  private def getAllKeys(handler: (Array[Byte]) => Unit): Unit = {
    var p = new Scan()

    val tableHBase = connection.getTable(table);
    try {
      val iter = tableHBase.getScanner(p)

      try {
        var fContinue = true

        do {
          val row = iter.next()
          if (row != null) {
            handler(row.getRow())
          } else {
            fContinue = false;
          }
        } while (fContinue)

      } finally {
        iter.close()
      }
    } catch {
      case e: Exception => throw e
    } finally {
      if (tableHBase != null)
        tableHBase.close
    }
  }

  private def del(key: Array[Byte]): Unit = {
    val p = new Delete(key)

    val tableHBase = connection.getTable(table);
    try {
      val result = tableHBase.delete(p)
    } catch {
      case e: Exception => throw e
    } finally {
      if (tableHBase != null)
        tableHBase.close
    }
  }

  override def TruncateStore(): Unit = {
    getAllKeys({ (key: Array[Byte]) => del(key) })
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

