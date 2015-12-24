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

package com.ligadata.automation.unittests.sqlserveradapter

import org.scalatest._
import Matchers._

import com.ligadata.Utils._
import util.control.Breaks._
import scala.io._
import java.util.{ Date, Calendar, TimeZone }
import java.text.{ SimpleDateFormat }
import java.io._

import sys.process._
import org.apache.logging.log4j._

import com.ligadata.keyvaluestore._
import com.ligadata.KvBase._
import com.ligadata.StorageBase._
import com.ligadata.Serialize._
import com.ligadata.Utils.Utils._
import com.ligadata.Utils.{ KamanjaClassLoader, KamanjaLoaderInfo }
import com.ligadata.StorageBase.StorageAdapterObj
import com.ligadata.keyvaluestore.SqlServerAdapter

import com.ligadata.Exceptions._

@Ignore
class SqlServerPerfTestSpec extends FunSpec with BeforeAndAfter with BeforeAndAfterAll with GivenWhenThen {
  var res: String = null;
  var statusCode: Int = -1;
  var adapter: DataStore = null
  var serializer: Serializer = null

  private val loggerName = this.getClass.getName
  private val logger = LogManager.getLogger(loggerName)
  val dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
  val dateFormat1 = new SimpleDateFormat("yyyy/MM/dd")
  // set the timezone to UTC for all time values
  TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  
  val dataStoreInfo = """{"StoreType": "sqlserver","hostname": "192.168.56.1","instancename":"KAMANJA","portnumber":"1433","database": "test_db","user":"bofauser1","SchemaName":"bofauser","password":"bofauser2","jarpaths":"/media/home2/jdbc","jdbcJar":"sqljdbc4-2.0.jar"}"""
  private val kvManagerLoader = new KamanjaLoaderInfo
  private val maxConnectionAttempts = 10;
  var cnt = 0

  private def CreateAdapter: DataStore = {
    var connectionAttempts = 0
    while (connectionAttempts < maxConnectionAttempts) {
      try {
        adapter = SqlServerAdapter.CreateStorageAdapter(kvManagerLoader, dataStoreInfo)
        return adapter
      } catch {
        case e: StorageConnectionException => {
          logger.error("%s: Message:%s".format(e.getMessage, e.cause.getMessage))
          logger.error("will retry after one minute ...")
          connectionAttempts = connectionAttempts + 1
          Thread.sleep(60 * 1000L)
        }
        case e: Exception => {
          logger.error("Failed to connect: Message:%s".format(e.getMessage))
          logger.error("retrying ...")
        }
      }
    }
    return null;
  }

  override def beforeAll = {
    try {
      logger.info("starting...");

      serializer = SerializerManager.GetSerializer("kryo")
      logger.info("Initialize SqlServerAdapter")
      adapter = CreateAdapter
    } catch {
      case e: StorageConnectionException => {
        logger.error("%s: Message:%s".format(e.getMessage, e.cause.getMessage))
      }
      case e: Exception => {
        logger.error("Failed to connect: Message:%s".format(e.getMessage))
      }
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

  def readCallBack(key: Key, value: Value) {
    logger.info("timePartition => " + key.timePartition)
    logger.info("bucketKey => " + key.bucketKey.mkString(","))
    logger.info("transactionId => " + key.transactionId)
    logger.info("rowId => " + key.rowId)
    logger.info("serializerType => " + value.serializerType)
    logger.info("serializedInfo length => " + value.serializedInfo.length)
    logger.info("----------------------------------------------------")
  }

  def readKeyCallBack(key: Key) {
    logger.info("timePartition => " + key.timePartition)
    logger.info("bucketKey => " + key.bucketKey.mkString(","))
    logger.info("transactionId => " + key.transactionId)
    logger.info("rowId => " + key.rowId)
    logger.info("----------------------------------------------------")
  }

  def deleteFile(path: File): Unit = {
    if (path.exists()) {
      if (path.isDirectory) {
        for (f <- path.listFiles) {
          deleteFile(f)
        }
      }
      path.delete()
    }
  }

  private def GetCurDtTmStr: String = {
    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date(System.currentTimeMillis))
  }

  describe("Load Tests for sqlserver adapter") {

    // validate property setup
    it("Load test api operations") {
      val containerName = "sys.customer1"

      And("Test drop container")
      noException should be thrownBy {
        var containers = new Array[String](0)
        containers = containers :+ containerName
        adapter.DropContainer(containers)
      }

      And("Test create container")
      noException should be thrownBy {
        var containers = new Array[String](0)
        containers = containers :+ containerName
        adapter.CreateContainer(containers)
      }

      And("Test Bulk Put api")

      logger.info(GetCurDtTmStr + ": Start Loading  1 million records 1000 at a time")

      for (batch <- 1 to 10000) {
	var successful = false
	while ( ! successful ){
          var keyValueList = new Array[(Key, Value)](0)
          var keyStringList = new Array[Array[String]](0)
          for (i <- 1 to 1000) {
            var cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -i);
            var currentTime = cal.getTime()
            var keyArray = new Array[String](0)
            var custName = "batch-" + batch + "-customer-" + i
            keyArray = keyArray :+ custName
            keyStringList = keyStringList :+ keyArray
            var key = new Key(currentTime.getTime(), keyArray, i, i)
            var custAddress = "1000" + batch * i + ",Main St, Redmond WA 98052"
            var custNumber = "4256667777" + batch * i
            var obj = new Customer(custName, custAddress, custNumber)
            var v = serializer.SerializeObjectToByteArray(obj)
            var value = new Value("kryo", v)
            keyValueList = keyValueList :+ (key, value)
          }
          var dataList = new Array[(String, Array[(Key, Value)])](0)
          dataList = dataList :+ (containerName, keyValueList)
	  try{
	    adapter.put(dataList)
            logger.info(GetCurDtTmStr + ": Loaded " + batch * 1000 + " objects ")
	    successful = true
	  }
	  catch{
	    case e: Exception => {
	      val stackTrace = StackTrace.ThrowableTraceString(e)
	      logger.info("StackTrace:"+stackTrace)
	      successful = false
	    }
	  }
	}
      }

      val sqlServerAdapter = adapter.asInstanceOf[SqlServerAdapter]

      And("Check the row count after adding a bunch")
      cnt = sqlServerAdapter.getRowCount(containerName, null)
      assert(cnt == 1000000)

      And("Test drop container again, cleanup")
      noException should be thrownBy {
        var containers = new Array[String](0)
        containers = containers :+ containerName
        adapter.DropContainer(containers)
      }

    }
  }
  override def afterAll = {
    var logFile = new java.io.File("logs")
    if (logFile != null) {
      deleteFile(logFile)
    }
  }
}
