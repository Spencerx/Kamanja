
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

package com.ligadata.OutputAdapters

import java.util.Properties
import kafka.message._
import kafka.producer.{ ProducerConfig, Producer, KeyedMessage }
import org.apache.log4j.Logger
import com.ligadata.InputOutputAdapterInfo.{ AdapterConfiguration, OutputAdapter, OutputAdapterObj, CountersAdapter }
import com.ligadata.AdaptersConfiguration.KafkaQueueAdapterConfiguration
import com.ligadata.Exceptions.StackTrace
import scala.collection.mutable.ArrayBuffer

object KafkaProducer extends OutputAdapterObj {
  def CreateOutputAdapter(inputConfig: AdapterConfiguration, cntrAdapter: CountersAdapter): OutputAdapter = new KafkaProducer(inputConfig, cntrAdapter)
}

class KafkaProducer(val inputConfig: AdapterConfiguration, cntrAdapter: CountersAdapter) extends OutputAdapter {
  private[this] val LOG = Logger.getLogger(getClass);

  //BUGBUG:: Not Checking whether inputConfig is really QueueAdapterConfiguration or not. 
  private[this] val qc = KafkaQueueAdapterConfiguration.GetAdapterConfig(inputConfig)

  val clientId = qc.Name + "_" + hashCode.toString

  val compress: Boolean = false
  val synchronously: Boolean = false
  val batchSize: Integer = 1024
  val queueTime: Integer = 50
  val queueSize: Integer = 16 * 1024 * 1024
  val bufferMemory: Integer = 16 * 1024 * 1024
  val messageSendMaxRetries: Integer = 3
  val requestRequiredAcks: Integer = 1

  val codec = if (compress) DefaultCompressionCodec.codec else NoCompressionCodec.codec

  val props = new Properties()
  props.put("compression.codec", codec.toString)
  props.put("producer.type", if (synchronously) "sync" else "async")
  props.put("metadata.broker.list", qc.hosts.mkString(","))
  props.put("batch.num.messages", batchSize.toString)
  props.put("batch.size", batchSize.toString)
  props.put("queue.time", queueTime.toString)
  props.put("queue.size", queueSize.toString)
  props.put("message.send.max.retries", messageSendMaxRetries.toString)
  props.put("request.required.acks", requestRequiredAcks.toString)
  props.put("buffer.memory", bufferMemory.toString)
  // props.put("buffer.size", bufferMemory.toString)
  // props.put("socket.send.buffer", bufferMemory.toString)
  // props.put("socket.receive.buffer", bufferMemory.toString)
  props.put("client.id", clientId)

  val producer = new Producer[Array[Byte], Array[Byte]](new ProducerConfig(props)) // Not closing this producer at this moment

  // To send an array of messages. messages.size should be same as partKeys.size
  override def send(messages: Array[Array[Byte]], partKeys: Array[Array[Byte]]): Unit = {
    if (messages.size != partKeys.size) {
      LOG.error("Message and Partition Keys hould has same number of elements. Message has %d and Partition Keys has %d".format(messages.size, partKeys.size))
      return
    }
    if (messages.size == 0) return
    try {
      val keyMessages = new ArrayBuffer[KeyedMessage[Array[Byte], Array[Byte]]](messages.size)
      for (i <- 0 until messages.size) {
        keyMessages += new KeyedMessage(qc.topic, partKeys(i), messages(i))
      }
      producer.send(keyMessages: _*) // Thinking this op is atomic for (can write multiple partitions into one queue, but don't know whether it is atomic per partition in the queue).
      val key = Category + "/" + qc.Name + "/evtCnt"
      cntrAdapter.addCntr(key, messages.size) // for now adding rows
    } catch {
      case e: Exception => {
        LOG.error("Failed to send :" + e.getMessage)
      }
    }

  }

  override def Shutdown(): Unit = {
    producer.close
  }
}

