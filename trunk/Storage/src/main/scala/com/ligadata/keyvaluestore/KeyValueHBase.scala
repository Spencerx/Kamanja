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

package com.ligadata.keyvaluestore.hbase

import com.ligadata.keyvaluestore._
import org.apache.hadoop.hbase._
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.util.Bytes

import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.io.compress.Compression.Algorithm;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

import org.apache.hadoop.hbase._
import org.apache.logging.log4j._

import java.nio.ByteBuffer
import java.io.IOException
import org.apache.hadoop.security.UserGroupInformation;
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.ligadata.Exceptions._
import com.ligadata.Exceptions.StackTrace

//import org.apache.hadoop.hbase.util.Bytes;
/*
 * create 'default', 'value'
 *
 * put 'default', 'KEYKEY', 'value', 'ValueValue'
 *
 * scan 'default'
 *
 */

class KeyValueHBaseTx(owner: DataStore) extends Transaction {
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

class KeyValueHBase(parameter: PropertyMap) extends DataStore {
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)

  var keyspace = parameter.getOrElse("schema", "default");
  var hostnames = parameter.getOrElse("hostlist", "localhost");

  var table = keyspace + ":" + parameter.getOrElse("table", "default")
  val config = HBaseConfiguration.create();

  config.setInt("zookeeper.session.timeout", 5000);
  config.setInt("zookeeper.recovery.retry", 1);
  config.setInt("hbase.client.retries.number", 3);
  config.setInt("hbase.client.pause", 5000);
  config.set("hbase.zookeeper.quorum", hostnames);
  config.setInt("hbase.client.keyvalue.maxsize", 104857600);

  val adapterspecificconfig = parameter.getOrElse("adapterspecificconfig", "").trim
  var isKerberos: Boolean = false
  var ugi: UserGroupInformation = null

  if (adapterspecificconfig.size > 0) {
    try {
      logger.debug("HBase adapterspecificconfig:" + adapterspecificconfig)
      val json = parse(adapterspecificconfig)
      if (json == null || json.values == null) {
        logger.error("Failed to parse JSON string AdapterSpecificConfig:" + adapterspecificconfig)
        throw new Exception("Failed to parse JSON string AdapterSpecificConfig:" + adapterspecificconfig)
      }
      val parsed_json = json.values.asInstanceOf[Map[String, Any]]
      val auth = parsed_json.getOrElse("authentication", "").toString.trim
      isKerberos = auth.compareToIgnoreCase("kerberos") == 0
      if (isKerberos) {
        val regionserver_principal = parsed_json.getOrElse("regionserver_principal", "").toString.trim
        val master_principal = parsed_json.getOrElse("master_principal", "").toString.trim
        val principal = parsed_json.getOrElse("principal", "").toString.trim
        val keytab = parsed_json.getOrElse("keytab", "").toString.trim

        logger.debug("HBase info => Hosts:" + hostnames + ", Keyspace:" + keyspace + ", Principal:" + principal + ", Keytab:" + keytab + ", hbase.regionserver.kerberos.principal:" + regionserver_principal + ", hbase.master.kerberos.principal:" + master_principal)

        config.set("hadoop.proxyuser.hdfs.groups", "*")
        config.set("hadoop.security.authorization", "true")
        config.set("hbase.security.authentication", "kerberos")
        config.set("hadoop.security.authentication", "kerberos")
        config.set("hbase.regionserver.kerberos.principal", regionserver_principal)
        config.set("hbase.master.kerberos.principal", master_principal)

        org.apache.hadoop.security.UserGroupInformation.setConfiguration(config);

        UserGroupInformation.loginUserFromKeytab(principal, keytab);

        ugi = UserGroupInformation.getLoginUser
      } else {
        logger.error("Not handling any authentication other than KERBEROS. AdapterSpecificConfig:" + adapterspecificconfig)
        throw new Exception("Not handling any authentication other than KERBEROS. AdapterSpecificConfig:" + adapterspecificconfig)
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw e
      }
    }
  } else {
    logger.debug("HBase info => Hosts:" + hostnames + ", Keyspace:" + keyspace)
  }

  var connection: HConnection = _
  try {
    connection = HConnectionManager.createConnection(config);
  } catch {
    case e: Exception => {
      val stackTrace = StackTrace.ThrowableTraceString(e)
      logger.debug("StackTrace:"+stackTrace)
      throw new ConnectionFailedException("Unable to connect to hbase at " + hostnames + ":" + e.getMessage())
    }
  }

  createTable(table)
  var tableHBase = connection.getTable(table);

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

  def createTable(tableName: String): Unit = {
    relogin
    val admin = new HBaseAdmin(config);
    if (!admin.tableExists(tableName)) {
      val tableDesc = new HTableDescriptor(TableName.valueOf(tableName));
      val colDesc1 = new HColumnDescriptor("key".getBytes())
      val colDesc2 = new HColumnDescriptor("value".getBytes())
      tableDesc.addFamily(colDesc1)
      tableDesc.addFamily(colDesc2)
      admin.createTable(tableDesc);
    }
  }

  def add(source: IStorage) = {
    relogin
    var p = new Put(source.Key.toArray[Byte])

    p.add(Bytes.toBytes("value"), Bytes.toBytes("base"), source.Value.toArray[Byte])

    val succeeded = tableHBase.checkAndPut(p.getRow(), Bytes.toBytes("value"), Bytes.toBytes("base"), null, p)
    if (!succeeded) {
      throw new Exception("not applied")
    }
  }

  def put(source: IStorage) = {
    relogin
    var p = new Put(source.Key.toArray[Byte])

    p.add(Bytes.toBytes("value"), Bytes.toBytes("base"), source.Value.toArray[Byte])

    tableHBase.put(p)

  }

  def putBatch(sourceArray: Array[IStorage]) = {
    relogin
    sourceArray.foreach(source => {
      var p = new Put(source.Key.toArray[Byte])
      p.add(Bytes.toBytes("value"), Bytes.toBytes("base"), source.Value.toArray[Byte])
      tableHBase.put(p)
    })
  }

  def delBatch(keyArray: Array[Key]) = {
    relogin
    keyArray.foreach(k => {
      val p = new Delete(k.toArray[Byte])
      val result = tableHBase.delete(p)
    })
  }

  def get(key: Key, handler: (Value) => Unit) = {
    relogin
    try {
      var p = new Get(key.toArray[Byte])

      p.addColumn(Bytes.toBytes("value"), Bytes.toBytes("base"))

      val result = tableHBase.get(p)

      val v = result.getValue(Bytes.toBytes("value"), Bytes.toBytes("base"))

      val value = new Value
      for (b <- v)
        value += b

      handler(value)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw new KeyNotFoundException(e.getMessage())
      }
    }
  }

  def get(key: Key, target: IStorage) = {
    relogin
    try {
      var p = new Get(key.toArray[Byte])

      p.addColumn(Bytes.toBytes("value"), Bytes.toBytes("base"))

      val result = tableHBase.get(p)

      val v = result.getValue(Bytes.toBytes("value"), Bytes.toBytes("base"))

      val value = new Value
      for (b <- v)
        value += b

      target.Construct(key, value)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw new KeyNotFoundException(e.getMessage())
      }
    }
  }

  def del(key: Key) = {
    relogin
    val p = new Delete(key.toArray[Byte])

    val result = tableHBase.delete(p)
  }

  def del(source: IStorage) = { del(source.Key) }

  def beginTx(): Transaction = { new KeyValueHBaseTx(this) }

  def endTx(tx: Transaction) = {}

  def commitTx(tx: Transaction) = {
    relogin
    tableHBase.flushCommits
  }

  override def Shutdown() = {
    if (tableHBase != null) {
      tableHBase.close()
      tableHBase = null
    }
    if (connection != null) {
      connection.close()
      connection = null
    }
  }

  def TruncateStore() = {
    relogin
    /*
		val a = new HBaseAdmin(connection)

		if (a.isTableEnabled(table))
			a.disableTable(table);

		a.deleteTable(table);

		a.createTable(tableHBase.getTableDescriptor(), Array(Bytes.toBytes("value:base") ) )

		a.close()
*/
    getAllKeys({ (key: Key) => del(key) })

  }

  def getAllKeys(handler: (Key) => Unit) {
    relogin
    var p = new Scan()

    val iter = tableHBase.getScanner(p)

    try {
      var fContinue = true

      do {
        val row = iter.next()
        if (row != null) {
          val v = row.getRow()
          val key = new Key
          for (b <- v)
            key += b

          handler(key)
        } else {
          fContinue = false;
        }
      } while (fContinue)

    } finally {
      iter.close()
    }

  }
}

