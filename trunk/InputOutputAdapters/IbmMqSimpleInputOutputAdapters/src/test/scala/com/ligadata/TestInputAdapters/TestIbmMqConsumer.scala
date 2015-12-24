/*
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
 * 
 *  
 */

package com.ligadata.TestInputAdapters

import org.scalamock.scalatest.MockFactory
import org.scalatest.FlatSpec
import com.ligadata.AdaptersConfiguration.IbmMqPartitionUniqueRecordKey
import com.ligadata.InputOutputAdapterInfo.AdapterConfiguration
import com.ligadata.InputOutputAdapterInfo.CountersAdapter
import com.ligadata.InputOutputAdapterInfo.ExecContext
import com.ligadata.InputOutputAdapterInfo.ExecContextObj
import com.ligadata.InputOutputAdapterInfo.InputAdapter
import com.ligadata.InputOutputAdapterInfo.InputAdapterCallerContext
import com.ligadata.InputOutputAdapterInfo.PartitionUniqueRecordKey
import com.ligadata.InputAdapters.IbmMqConsumer
import com.ligadata.InputOutputAdapterInfo.PartitionUniqueRecordValue
import com.ligadata.KamanjaBase.DataDelimiters
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

class TestIbmMqConsumer extends FlatSpec with MockFactory {

  // dummy objects to satisfy inputs for IbmMqConsumer
  class ClsExecContextObj extends ExecContextObj {
    def CreateExecContext(input: InputAdapter, curPartitionKey: PartitionUniqueRecordKey, callerCtxt: InputAdapterCallerContext): ExecContext = {
      class ClsExecContext extends ExecContext {
        val input: InputAdapter = null
        val curPartitionKey: PartitionUniqueRecordKey = null
        val callerCtxt: InputAdapterCallerContext = null
        def execute(data: Array[Byte], format: String, uniqueKey: PartitionUniqueRecordKey, uniqueVal: PartitionUniqueRecordValue, readTmNanoSecs: Long, readTmMilliSecs: Long, ignoreOutput: Boolean, associatedMsg: String, delimiters: DataDelimiters): Unit = {}

      }
      new ClsExecContext
    }
  }
  class ClsInputAdapterCallerContext extends InputAdapterCallerContext
  class ClsCountersAdapter extends CountersAdapter {
    def addCntr(key: String, cnt: Long): Long = {
      val a: Long = 0
      a
    }
    def addCntr(cntrs: scala.collection.immutable.Map[String, Long]): Unit = {}
    def getCntr(key: String): Long = {
      val a: Long = 0
      a
    }
    def getDispString(delim: String): String = {
      val a: String = ""
      a
    }
    def copyMap: scala.collection.immutable.Map[String, Long] = {
      val a: scala.collection.immutable.Map[String, Long] = null
      a
    }
  }
  // function for config creation
  private def createSampleConfig(msgType: String): AdapterConfiguration = {
    val inputConfig = new AdapterConfiguration
    var adapterSpecificConfiguration = "{\"host_name\": \"localhost\",\"port\":\"9092\",\"channel\":\"SomeChannelName\",\"queue_manager\":\"SomeQueueManagerName\",\"application_name\":\"SomeApplicationName\",\"queue_name\":\"SomeQueueName\",\"topic_name\":\"SomeTopicName\",\"content_type\":\"SomeContentType\",\"ssl_cipher_suite\":\"SomeSslCipherSuite\",\"message_type\": \"SomeMsgType\" }"

    msgType match {
      case "text"      => { adapterSpecificConfiguration = adapterSpecificConfiguration.replace("SomeMsgType", "text") }
      case "bytearray" => { adapterSpecificConfiguration = adapterSpecificConfiguration.replace("SomeMsgType", "bytearray") }
      case _           => { adapterSpecificConfiguration = adapterSpecificConfiguration.replace("SomeMsgType", "Default") }
    }

    inputConfig.adapterSpecificCfg = adapterSpecificConfiguration
    inputConfig.Name = "SomeName"
    inputConfig.formatOrInputAdapterName = "SomeformatOrInputAdapterName"
    inputConfig.className = "com.example.package.class"
    inputConfig.jarName = "org.example.somejar.jar"
    inputConfig.dependencyJars = Set("org.example.somedependencyjar1.jar", "org.example.somedependencyjar2.jar")
    //for the next 4, nulls should be handled.
    inputConfig.associatedMsg = "SomeAssociatedMessage"
    inputConfig.keyAndValueDelimiter = ","
    inputConfig.fieldDelimiter = "|"
    inputConfig.valueDelimiter = "~"

    inputConfig
  } //printFailure - excluded as trivial

  // Test cases here

  //processJMSException - excluded as only processes JMS Exception
  //Shutdown - excluded as it only calls StopProcessing
  //StopProcessing - closes session, connection and executor. 
  //StartProcessing - excluded as cannot instantiate connection
  //GetAllPartitionsUniqueKeys - private and tested in GetAllPartitionUniqueRecordKey
  //GetAllPartitionUniqueRecordKey 
  "Definition GetAllPartitionUniqueRecordKey" should "return correct Array[PartitionUniqueRecordKey] based on the input config" in {
    // Prepare expected output
    val iPartitionUniqueRecordKey = new IbmMqPartitionUniqueRecordKey
    val key = "{\"Version\":1,\"Type\":\"IbmMq\",\"Name\":\"SomeName\",\"QueueManagerName\":\"SomeQueueManagerName\",\"ChannelName\":\"SomeChannelName\",\"QueueName\":\"SomeQueueName\",\"TopicName\":\"SomeTopicName\"}"
    iPartitionUniqueRecordKey.Deserialize(key)
    val iaPartitionUniqueRecordKey = Array[PartitionUniqueRecordKey](iPartitionUniqueRecordKey)
    //Create sample input config
    val inputConfig = createSampleConfig("")
    //Create stub implementaitons of the traits that are required to instantiate IbmMqConsumer
    val callerCtxt = new ClsInputAdapterCallerContext
    val execCtxtObj = new ClsExecContextObj
    val cntrAdapter = new ClsCountersAdapter
    //instantiate IbmMqConsumer
    val testIbmMqConsumer = IbmMqConsumer.CreateInputAdapter(inputConfig, callerCtxt, execCtxtObj, cntrAdapter)
    //execute the GetAllPartitionUniqueRecordKey method
    val oPartitionUniqueRecordKey = testIbmMqConsumer.GetAllPartitionUniqueRecordKey
    //carry out assertion
    assert(oPartitionUniqueRecordKey(0).Serialize == iaPartitionUniqueRecordKey(0).Serialize)
  }

  //DeserializeKey - only calls IbmMqPartitionUniqueRecordKey.Deserialize, which has already been unit tested in TestIbmMqPartitionUniqueRecordKey
  //DeserializeValue - calls IbmMqPartitionUniqueRecordValue.Deserialize, which has already been unit tested in TestIbmMqPartitionUniqueRecordKey
  //getAllPartitionBeginValues - excluded - not yet implemented
  //getAllPartitionEndValues - excluded - not yet implemented

}