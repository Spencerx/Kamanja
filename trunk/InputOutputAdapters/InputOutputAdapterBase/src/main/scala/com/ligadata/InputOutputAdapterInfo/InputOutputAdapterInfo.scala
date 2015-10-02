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

package com.ligadata.InputOutputAdapterInfo

import com.ligadata.KamanjaBase.DataDelimiters

class AdapterConfiguration {
  var Name: String = _ // Name of the Adapter, KafkaQueue Name/MQ Name/File Adapter Logical Name/etc
  var formatOrInputAdapterName: String = _ // CSV/JSON/XML for input adapter. For output it is just corresponding input adapter name. For Status it is default
  var associatedMsg: String = _ // Queue Associated Message
  var className: String = _ // Class where the Adapter can be loaded (Object derived from InputAdapterObj)
  var jarName: String = _ // Jar where the className can be found
  var dependencyJars: Set[String] = _ // All dependency Jars for jarName 
  var adapterSpecificCfg: String = _ // adapter specific (mostly json) string 
  var keyAndValueDelimiter: String = _ // Delimiter String for keyAndValueDelimiter
  var fieldDelimiter: String = _ // Delimiter String for fieldDelimiter
  var valueDelimiter: String = _ // Delimiter String for valueDelimiter
}

trait CountersAdapter {
  def addCntr(key: String, cnt: Long): Long
  def addCntr(cntrs: scala.collection.immutable.Map[String, Long]): Unit
  def getCntr(key: String): Long
  def getDispString(delim: String): String
  def copyMap: scala.collection.immutable.Map[String, Long]
}

trait InputAdapterCallerContext {
}

// Input Adapter Object to create Adapter
trait InputAdapterObj {
  def CreateInputAdapter(inputConfig: AdapterConfiguration, callerCtxt: InputAdapterCallerContext, execCtxtObj: ExecContextObj, cntrAdapter: CountersAdapter): InputAdapter
}

class StartProcPartInfo {
  var _key: PartitionUniqueRecordKey = null
  var _val: PartitionUniqueRecordValue = null
  var _validateInfoVal: PartitionUniqueRecordValue = null
}

// Input Adapter
trait InputAdapter {
  val inputConfig: AdapterConfiguration // Configuration
  val callerCtxt: InputAdapterCallerContext

  def UniqueName: String = { // Making String from key
    return "{\"Name\" : \"%s\"}".format(inputConfig.Name)
  }

  def Category = "Input"
  def Shutdown: Unit
  def StopProcessing: Unit
  def StartProcessing(partitionInfo: Array[StartProcPartInfo], ignoreFirstMsg: Boolean): Unit // each value in partitionInfo is (PartitionUniqueRecordKey, PartitionUniqueRecordValue, Long, PartitionUniqueRecordValue). // key, processed value, Start transactionid, Ignore Output Till given Value (Which is written into Output Adapter) & processing Transformed messages (processing & total)
  def GetAllPartitionUniqueRecordKey: Array[PartitionUniqueRecordKey]
  def DeserializeKey(k: String): PartitionUniqueRecordKey
  def DeserializeValue(v: String): PartitionUniqueRecordValue
  def getAllPartitionBeginValues: Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)]
  def getAllPartitionEndValues: Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)]
}

// Output Adapter Object to create Adapter
trait OutputAdapterObj {
  def CreateOutputAdapter(inputConfig: AdapterConfiguration, cntrAdapter: CountersAdapter): OutputAdapter
}

// Output Adapter
trait OutputAdapter {
  val inputConfig: AdapterConfiguration // Configuration

  def send(message: String, partKey: String): Unit = send(Array(message.getBytes("UTF8")), Array(partKey.getBytes("UTF8")))

  def send(message: Array[Byte], partKey: Array[Byte]): Unit = send(Array(message), Array(partKey))

  // To send an array of messages. messages.size should be same as partKeys.size
  def send(messages: Array[String], partKeys: Array[String]): Unit = send(messages.map(m => m.getBytes("UTF8")), partKeys.map(k => k.getBytes("UTF8")))
  
  // To send an array of messages. messages.size should be same as partKeys.size
  def send(messages: Array[Array[Byte]], partKeys: Array[Array[Byte]]): Unit
  
  def Shutdown: Unit
  def Category = "Output"
}

trait ExecContext {
  val input: InputAdapter
  val curPartitionKey: PartitionUniqueRecordKey
  val callerCtxt: InputAdapterCallerContext

  def execute(data: Array[Byte], format: String, uniqueKey: PartitionUniqueRecordKey, uniqueVal: PartitionUniqueRecordValue, readTmNanoSecs: Long, readTmMilliSecs: Long, ignoreOutput: Boolean, associatedMsg: String, delimiters: DataDelimiters): Unit
}

trait ExecContextObj {
  def CreateExecContext(input: InputAdapter, curPartitionKey: PartitionUniqueRecordKey, callerCtxt: InputAdapterCallerContext): ExecContext
}

trait PartitionUniqueRecordKey {
  val Type: String // Type of the Key -- For now putting File/Kafka like that. This is mostly for readable purpose (for which adapter etc)
  def Serialize: String // Making String from key
  def Deserialize(key: String): Unit // Making Key from Serialized String
}

trait PartitionUniqueRecordValue {
  def Serialize: String // Making String from Value
  def Deserialize(key: String): Unit // Making Value from Serialized String
}


