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

package com.ligadata.AdaptersConfiguration

import com.ligadata.InputOutputAdapterInfo.{ AdapterConfiguration, PartitionUniqueRecordKey, PartitionUniqueRecordValue }
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

class KafkaQueueAdapterConfiguration extends AdapterConfiguration {
  var hosts: Array[String] = _ // each host is HOST:PORT
  var topic: String = _ // topic name
  var noDataSleepTimeInMs: Int = 300 // No Data Sleep Time in milli secs. Default is 300 ms
  var instancePartitions: Set[Int] = _ // Valid only for Input Queues. These are the partitions we handle for this Queue. For now we are treating Partitions as Ints. (Kafka handle it as ints)
  var otherconfigs = scala.collection.mutable.Map[String, String]() // Making the key is lowercase always
}

object KafkaConstants {
  val KAFKA_SEND_SUCCESS = 0
  val KAFKA_SEND_Q_FULL = 1
  val KAFKA_SEND_DEAD_PRODUCER = 2
  val KAFKA_NOT_SEND = 3
}

object KafkaQueueAdapterConfiguration {
  def GetAdapterConfig(inputConfig: AdapterConfiguration): KafkaQueueAdapterConfiguration = {
    if (inputConfig.adapterSpecificCfg == null || inputConfig.adapterSpecificCfg.size == 0) {
      val err = "Not found Host/Brokers and topicname for Kafka Adapter Config:" + inputConfig.Name
      throw new Exception(err)
    }

    val qc = new KafkaQueueAdapterConfiguration
    qc.Name = inputConfig.Name
    qc.formatOrInputAdapterName = inputConfig.formatOrInputAdapterName
    qc.className = inputConfig.className
    qc.jarName = inputConfig.jarName
    qc.dependencyJars = inputConfig.dependencyJars
    qc.associatedMsg = if (inputConfig.associatedMsg == null) null else inputConfig.associatedMsg.trim
    qc.keyAndValueDelimiter = if (inputConfig.keyAndValueDelimiter == null) null else inputConfig.keyAndValueDelimiter.trim
    qc.fieldDelimiter = if (inputConfig.fieldDelimiter == null) null else inputConfig.fieldDelimiter.trim
    qc.valueDelimiter = if (inputConfig.valueDelimiter == null) null else inputConfig.valueDelimiter.trim

    val adapCfg = parse(inputConfig.adapterSpecificCfg)
    if (adapCfg == null || adapCfg.values == null) {
      val err = "Not found Host/Brokers and topicname for Kafka Adapter Config:" + inputConfig.Name
      throw new Exception(err)
    }
    val values = adapCfg.values.asInstanceOf[Map[String, String]]

    values.foreach(kv => {
      if (kv._1.compareToIgnoreCase("HostList") == 0) {
        qc.hosts = kv._2.split(",").map(str => str.trim).filter(str => str.size > 0)
      } else if (kv._1.compareToIgnoreCase("TopicName") == 0) {
        qc.topic = kv._2.trim
      } else if (kv._1.compareToIgnoreCase("NoDataSleepTimeInMs") == 0) {
        qc.noDataSleepTimeInMs = kv._2.trim.toInt
        if (qc.noDataSleepTimeInMs < 0)
          qc.noDataSleepTimeInMs = 0
      } else {
        qc.otherconfigs(kv._1.toLowerCase()) = kv._2;
      }
    })

    qc.instancePartitions = Set[Int]()

    qc
  }
}

case class KafkaKeyData(Version: Int, Type: String, Name: String, TopicName: Option[String], PartitionId: Option[Int]) // Using most of the values as optional values. Just thinking about future changes. Don't know the performance issues.

class KafkaPartitionUniqueRecordKey extends PartitionUniqueRecordKey {
  val Version: Int = 1
  val Type: String = "Kafka"
  var Name: String = _ // Name
  var TopicName: String = _ // Topic Name
  var PartitionId: Int = _ // Partition Id

  override def Serialize: String = { // Making String from key
    val json =
      ("Version" -> Version) ~
        ("Type" -> Type) ~
        ("Name" -> Name) ~
        ("TopicName" -> TopicName) ~
        ("PartitionId" -> PartitionId)
    compact(render(json))
  }

  override def Deserialize(key: String): Unit = { // Making Key from Serialized String
    implicit val jsonFormats: Formats = DefaultFormats
    val keyData = parse(key).extract[KafkaKeyData]
    if (keyData.Version == Version && keyData.Type.compareTo(Type) == 0) {
      Name = keyData.Name
      TopicName = keyData.TopicName.get
      PartitionId = keyData.PartitionId.get
    }
    // else { } // Not yet handling other versions
  }
}

case class KafkaRecData(Version: Int, Offset: Option[Long]) // Using most of the values as optional values. Just thinking about future changes. Don't know the performance issues.

class KafkaPartitionUniqueRecordValue extends PartitionUniqueRecordValue {
  val Version: Int = 1
  var Offset: Long = -1 // Offset 

  override def Serialize: String = { // Making String from Value
    val json =
      ("Version" -> Version) ~
        ("Offset" -> Offset)
    compact(render(json))
  }

  override def Deserialize(key: String): Unit = { // Making Value from Serialized String
    implicit val jsonFormats: Formats = DefaultFormats
    val recData = parse(key).extract[KafkaRecData]
    if (recData.Version == Version) {
      Offset = recData.Offset.get
    }
    // else { } // Not yet handling other versions
  }
}

