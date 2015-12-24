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
 * This test suite contains unit tests for the IbmMqConfiguration script. 
 * 
 * Exclusions:
 *    1. Case classes IbmMqKeyData and IbmMqRecData
 *    2. Serialize methods from classes IbmMqPartitionUniqueRecordKey and IbmMqPartitionUniqueRecordValue - these employ third party JsonAST objects and methods. 
 *  
 */

package com.ligadata.TestAdapterConfiguration

import org.scalatest.FlatSpec
import org.scalamock.scalatest.MockFactory
import com.ligadata.InputOutputAdapterInfo.{ AdapterConfiguration, PartitionUniqueRecordKey, PartitionUniqueRecordValue }
import com.ligadata.AdaptersConfiguration.IbmMqAdapterConfiguration
import scala.collection.mutable.ArrayBuffer
import com.ligadata.AdaptersConfiguration.MessageType
import com.ligadata.AdaptersConfiguration.IbmMqPartitionUniqueRecordKey
import com.ligadata.AdaptersConfiguration.IbmMqPartitionUniqueRecordValue

class TestIbmMqConfiguration extends FlatSpec with MockFactory {

  // Test 1: check defaults
  "The port,content type and message type" should "be set to defaults" in {
    val mockIbmMqAdapterConfiguration = mock[IbmMqAdapterConfiguration]
    assert(mockIbmMqAdapterConfiguration.port == 1414)
    assert(mockIbmMqAdapterConfiguration.content_type == "application/json")
    assert(mockIbmMqAdapterConfiguration.msgType == MessageType.fText)
  }

  // Test 2: test that the null input adapter config causes exception
  "GetAdapterConfig method" should "throw exception for null input configuration" in {
    //create new AdapterConfiguration, but leave it null
    val inputConfig = new AdapterConfiguration
    intercept[Exception] {
      IbmMqAdapterConfiguration.GetAdapterConfig(inputConfig)
      val mockIbmMqAdapterConfiguration = mock[IbmMqAdapterConfiguration]
    }
  }

  // Test 3: test that the not null input adapter config with no adapter specific config causes exception
  "GetAdapterConfig method" should "throw exception for input configuration that does not have any adapter specific config" in {
    //create new AdapterConfiguration
    val inputConfig = new AdapterConfiguration
    //Put empty adapterSpecificCfg, so that the inputConfig does not remain null
    inputConfig.adapterSpecificCfg = ""
    intercept[Exception] {
      IbmMqAdapterConfiguration.GetAdapterConfig(inputConfig)
      val mockIbmMqAdapterConfiguration = mock[IbmMqAdapterConfiguration]
    }
  }

  // Test 4: pass a full configuration and verify if the QC attributes are being set properly. The message type should be text by default

  "GetAdapterConfig method" should "set the adapter configuration with default message type based on the input adapter configuration" in {

    //Create new adapter config
    val inputConfig = createSampleConfig("DefaultMsgType")
    //Create MQ adapter with the config
    val testIbmMqAdapterConfiguration = IbmMqAdapterConfiguration.GetAdapterConfig(inputConfig)

    // Assert that the2 values have been set.
    assert(testIbmMqAdapterConfiguration.Name == "SomeName")
    assert(testIbmMqAdapterConfiguration.formatOrInputAdapterName == "SomeformatOrInputAdapterName")
    assert(testIbmMqAdapterConfiguration.className == "com.example.package.class")
    assert(testIbmMqAdapterConfiguration.jarName == "org.example.somejar.jar")
    assert(testIbmMqAdapterConfiguration.dependencyJars == Set("org.example.somedependencyjar1.jar", "org.example.somedependencyjar2.jar"))
    assert(testIbmMqAdapterConfiguration.associatedMsg == "SomeAssociatedMessage")
    assert(testIbmMqAdapterConfiguration.keyAndValueDelimiter == ",")
    assert(testIbmMqAdapterConfiguration.fieldDelimiter == "|")
    assert(testIbmMqAdapterConfiguration.valueDelimiter == "~")
    assert(testIbmMqAdapterConfiguration.host_name == "localhost")
    assert(testIbmMqAdapterConfiguration.port == 9092) // shouldn't this be checked for IsNumber in the code?
    assert(testIbmMqAdapterConfiguration.channel == "SomeChannel")
    assert(testIbmMqAdapterConfiguration.queue_manager == "SomeQueueManager")
    assert(testIbmMqAdapterConfiguration.application_name == "SomeApplicationName")
    assert(testIbmMqAdapterConfiguration.queue_name == "SomeQueueName")
    assert(testIbmMqAdapterConfiguration.topic_name == "SomeTopicName")
    assert(testIbmMqAdapterConfiguration.content_type == "SomeContentType")
    assert(testIbmMqAdapterConfiguration.ssl_cipher_suite == "SomeSslCipherSuite")
    assert(testIbmMqAdapterConfiguration.msgType == MessageType.fText) // by defauly this should be text

  }

  // Test 5: check if text message type is set properly 

  "GetAdapterConfig method" should "handle text message type" in {

    //Create new adapter config
    val inputConfig = createSampleConfig("text")
    //Create MQ adapter with the config
    val testIbmMqAdapterConfiguration = IbmMqAdapterConfiguration.GetAdapterConfig(inputConfig)

    // Assert that the2 values have been set.
    assert(testIbmMqAdapterConfiguration.Name == "SomeName")
    assert(testIbmMqAdapterConfiguration.formatOrInputAdapterName == "SomeformatOrInputAdapterName")
    assert(testIbmMqAdapterConfiguration.className == "com.example.package.class")
    assert(testIbmMqAdapterConfiguration.jarName == "org.example.somejar.jar")
    assert(testIbmMqAdapterConfiguration.dependencyJars == Set("org.example.somedependencyjar1.jar", "org.example.somedependencyjar2.jar"))
    assert(testIbmMqAdapterConfiguration.associatedMsg == "SomeAssociatedMessage")
    assert(testIbmMqAdapterConfiguration.keyAndValueDelimiter == ",")
    assert(testIbmMqAdapterConfiguration.fieldDelimiter == "|")
    assert(testIbmMqAdapterConfiguration.valueDelimiter == "~")
    assert(testIbmMqAdapterConfiguration.host_name == "localhost")
    assert(testIbmMqAdapterConfiguration.port == 9092) // shouldn't this be checked for IsNumber in the code?
    assert(testIbmMqAdapterConfiguration.channel == "SomeChannel")
    assert(testIbmMqAdapterConfiguration.queue_manager == "SomeQueueManager")
    assert(testIbmMqAdapterConfiguration.application_name == "SomeApplicationName")
    assert(testIbmMqAdapterConfiguration.queue_name == "SomeQueueName")
    assert(testIbmMqAdapterConfiguration.topic_name == "SomeTopicName")
    assert(testIbmMqAdapterConfiguration.content_type == "SomeContentType")
    assert(testIbmMqAdapterConfiguration.ssl_cipher_suite == "SomeSslCipherSuite")
    assert(testIbmMqAdapterConfiguration.msgType == MessageType.fText) // by defauly this should be text

  }

  // Test 6: check if byte array message type is set properly 

  "GetAdapterConfig method" should "handle byte array message type" in {

    //Create new adapter config
    val inputConfig = createSampleConfig("bytearray")
    //Create MQ adapter with the config
    val testIbmMqAdapterConfiguration = IbmMqAdapterConfiguration.GetAdapterConfig(inputConfig)

    // Assert that the2 values have been set.
    assert(testIbmMqAdapterConfiguration.Name == "SomeName")
    assert(testIbmMqAdapterConfiguration.formatOrInputAdapterName == "SomeformatOrInputAdapterName")
    assert(testIbmMqAdapterConfiguration.className == "com.example.package.class")
    assert(testIbmMqAdapterConfiguration.jarName == "org.example.somejar.jar")
    assert(testIbmMqAdapterConfiguration.dependencyJars == Set("org.example.somedependencyjar1.jar", "org.example.somedependencyjar2.jar"))
    assert(testIbmMqAdapterConfiguration.associatedMsg == "SomeAssociatedMessage")
    assert(testIbmMqAdapterConfiguration.keyAndValueDelimiter == ",")
    assert(testIbmMqAdapterConfiguration.fieldDelimiter == "|")
    assert(testIbmMqAdapterConfiguration.valueDelimiter == "~")
    assert(testIbmMqAdapterConfiguration.host_name == "localhost")
    assert(testIbmMqAdapterConfiguration.port == 9092) // shouldn't this be checked for IsNumber in the code?
    assert(testIbmMqAdapterConfiguration.channel == "SomeChannel")
    assert(testIbmMqAdapterConfiguration.queue_manager == "SomeQueueManager")
    assert(testIbmMqAdapterConfiguration.application_name == "SomeApplicationName")
    assert(testIbmMqAdapterConfiguration.queue_name == "SomeQueueName")
    assert(testIbmMqAdapterConfiguration.topic_name == "SomeTopicName")
    assert(testIbmMqAdapterConfiguration.content_type == "SomeContentType")
    assert(testIbmMqAdapterConfiguration.ssl_cipher_suite == "SomeSslCipherSuite")
    assert(testIbmMqAdapterConfiguration.msgType == MessageType.fByteArray) // by defauly this should be text

  }

  private def createSampleConfig(msgType: String): AdapterConfiguration = {
    val inputConfig = new AdapterConfiguration
    var adapterSpecificConfiguration = "{\"host_name\": \"localhost\",\"port\":\"9092\",\"channel\":\"SomeChannel\",\"queue_manager\":\"SomeQueueManager\",\"application_name\":\"SomeApplicationName\",\"queue_name\":\"SomeQueueName\",\"topic_name\":\"SomeTopicName\",\"content_type\":\"SomeContentType\",\"ssl_cipher_suite\":\"SomeSslCipherSuite\",\"message_type\": \"SomeMsgType\" }"

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
  }

}

class TestIbmMqPartitionUniqueRecordKey extends FlatSpec {
  // test Deserialize with type IbmMq and version 1
  "IbmMqPartitionUniqueRecordKey.Deserialize" should "parse key when type and version are as expected" in {

    val testIbmMqPartitionUniqueRecordKey = new IbmMqPartitionUniqueRecordKey
    val key = "{\"Version\":1,\"Type\":\"IbmMq\",\"Name\":\"SomeName\",\"QueueManagerName\":\"SomeQueueManagerName\",\"ChannelName\":\"SomeChannelName\",\"QueueName\":\"SomeQueueName\",\"TopicName\":\"SomeTopicName\"}"
    testIbmMqPartitionUniqueRecordKey.Deserialize(key)

    // carry out assertions    
    assert(testIbmMqPartitionUniqueRecordKey.Name == "SomeName")
    assert(testIbmMqPartitionUniqueRecordKey.QueueManagerName == "SomeQueueManagerName")
    assert(testIbmMqPartitionUniqueRecordKey.ChannelName == "SomeChannelName")
    assert(testIbmMqPartitionUniqueRecordKey.QueueName == "SomeQueueName")
    assert(testIbmMqPartitionUniqueRecordKey.TopicName == "SomeTopicName")
  }

  // test Deserialize with type other than IbmMq
  "IbmMqPartitionUniqueRecordKey.Deserialize" should "not parse key when type is not IbmMq" in {
    val testIbmMqPartitionUniqueRecordKey = new IbmMqPartitionUniqueRecordKey
    val key = "{\"Version\":1,\"Type\":\"Kafka\",\"Name\":\"SomeName\",\"QueueManagerName\":\"SomeQueueManagerName\",\"ChannelName\":\"SomeChannelName\",\"QueueName\":\"SomeQueueName\",\"TopicName\":\"SomeTopicName\"}"
    testIbmMqPartitionUniqueRecordKey.Deserialize(key)
    // carry out assertions
    assert(testIbmMqPartitionUniqueRecordKey.Name == null)
    assert(testIbmMqPartitionUniqueRecordKey.QueueManagerName == null)
    assert(testIbmMqPartitionUniqueRecordKey.ChannelName == null)
    assert(testIbmMqPartitionUniqueRecordKey.QueueName == "")
    assert(testIbmMqPartitionUniqueRecordKey.TopicName == "")
  }

  // test Deserialize with version other than 1
  "IbmMqPartitionUniqueRecordKey.Deserialize" should "not parse key when version is not 1" in {
    val testIbmMqPartitionUniqueRecordKey = new IbmMqPartitionUniqueRecordKey
    val key = "{\"Version\":10,\"Type\":\"IbmMq\",\"Name\":\"SomeName\",\"QueueManagerName\":\"SomeQueueManagerName\",\"ChannelName\":\"SomeChannelName\",\"QueueName\":\"SomeQueueName\",\"TopicName\":\"SomeTopicName\"}"
    testIbmMqPartitionUniqueRecordKey.Deserialize(key)
    // carry out assertions
    assert(testIbmMqPartitionUniqueRecordKey.Name == null)
    assert(testIbmMqPartitionUniqueRecordKey.QueueManagerName == null)
    assert(testIbmMqPartitionUniqueRecordKey.ChannelName == null)
    assert(testIbmMqPartitionUniqueRecordKey.QueueName == "")
    assert(testIbmMqPartitionUniqueRecordKey.TopicName == "")
  }
}

class TestIbmMqPartitionUniqueRecordValue extends FlatSpec {

  // test Deserialize with version 1
  "IbmMqPartitionUniqueRecordValue.Deserialize" should "parse record message ID when version is as expected" in {

    val testIbmMqPartitionUniqueRecordValue = new IbmMqPartitionUniqueRecordValue
    val record = "{\"Version\":1,\"MessageId\":\"SomeMessageId\"}"
    testIbmMqPartitionUniqueRecordValue.Deserialize(record)

    // carry out assertions    
    assert(testIbmMqPartitionUniqueRecordValue.MessageId == "SomeMessageId")
  }

  // test Deserialize with version other than 1
  "IbmMqPartitionUniqueRecordValue.Deserialize" should "not parse record message ID version is not as expected" in {

    val testIbmMqPartitionUniqueRecordValue = new IbmMqPartitionUniqueRecordValue
    val record = "{\"Version\":10,\"MessageId\":\"SomeMessageId\"}"
    testIbmMqPartitionUniqueRecordValue.Deserialize(record)

    // carry out assertions    
    assert(testIbmMqPartitionUniqueRecordValue.MessageId == "")
  }

}