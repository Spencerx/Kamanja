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

package com.ligadata.InputAdapters

import scala.actors.threadpool.{ Executors, ExecutorService }
import java.util.Properties
import scala.collection.mutable.ArrayBuffer
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.InputOutputAdapterInfo.{ AdapterConfiguration, InputAdapter, InputAdapterObj, OutputAdapter, ExecContext, ExecContextObj, CountersAdapter, PartitionUniqueRecordKey, PartitionUniqueRecordValue, StartProcPartInfo, InputAdapterCallerContext }
import com.ligadata.AdaptersConfiguration.{ IbmMqAdapterConfiguration, IbmMqPartitionUniqueRecordKey, IbmMqPartitionUniqueRecordValue }
import javax.jms.{ Connection, Destination, JMSException, Message, MessageConsumer, Session, TextMessage, BytesMessage }
import scala.util.control.Breaks._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.ibm.msg.client.jms.JmsConnectionFactory
import com.ibm.msg.client.jms.JmsFactoryFactory
import com.ibm.msg.client.wmq.WMQConstants
import com.ibm.msg.client.wmq.common.CommonConstants
import com.ibm.msg.client.jms.JmsConstants
import com.ligadata.Exceptions.StackTrace
import com.ligadata.KamanjaBase.DataDelimiters

object IbmMqConsumer extends InputAdapterObj {
  def CreateInputAdapter(inputConfig: AdapterConfiguration, callerCtxt: InputAdapterCallerContext, execCtxtObj: ExecContextObj, cntrAdapter: CountersAdapter): InputAdapter = new IbmMqConsumer(inputConfig, callerCtxt, execCtxtObj, cntrAdapter)
}

class IbmMqConsumer(val inputConfig: AdapterConfiguration, val callerCtxt: InputAdapterCallerContext, val execCtxtObj: ExecContextObj, cntrAdapter: CountersAdapter) extends InputAdapter {
  private def printFailure(ex: Exception) {
    if (ex != null) {
      if (ex.isInstanceOf[JMSException]) {
        processJMSException(ex.asInstanceOf[JMSException])
      } else {
        LOG.error(ex)
      }
    }
  }

  private def processJMSException(jmsex: JMSException) {
    LOG.error(jmsex)
    var innerException: Throwable = jmsex.getLinkedException
    if (innerException != null) {
      LOG.error("Inner exception(s):")
    }
    while (innerException != null) {
      LOG.error(innerException)
      innerException = innerException.getCause
    }
  }

  private[this] val LOG = LogManager.getLogger(getClass);

  //BUGBUG:: Not Checking whether inputConfig is really QueueAdapterConfiguration or not. 
  private[this] val qc = IbmMqAdapterConfiguration.GetAdapterConfig(inputConfig)
  private[this] val lock = new Object()
  private[this] val kvs = scala.collection.mutable.Map[String, (IbmMqPartitionUniqueRecordKey, IbmMqPartitionUniqueRecordValue, IbmMqPartitionUniqueRecordValue)]()

  var connection: Connection = null
  var session: Session = null
  var destination: Destination = null
  var consumer: MessageConsumer = null

  var executor: ExecutorService = _
  val input = this

  override def Shutdown: Unit = lock.synchronized {
    StopProcessing
  }

  override def StopProcessing: Unit = lock.synchronized {
    LOG.debug("===============> Called StopProcessing")
    //BUGBUG:: Make sure we finish processing the current running messages.
    if (consumer != null) {
      try {
        consumer.close()
      } catch {
        case jmsex: Exception => {
          LOG.error("Producer could not be closed.")
          printFailure(jmsex)
        }
      }
    }

    // Do we need to close destination ??

    if (session != null) {
      try {
        session.close()
      } catch {
        case jmsex: Exception => {
          LOG.error("Session could not be closed.")
          printFailure(jmsex)
        }
      }
    }
    if (connection != null) {
      try {
        connection.close()
      } catch {
        case jmsex: Exception => {
          LOG.error("Connection could not be closed.")
          printFailure(jmsex)
        }
      }
    }

    if (executor != null) {
      executor.shutdownNow()
      while (executor.isTerminated == false) {
        Thread.sleep(100) // sleep 100ms and then check
      }
    }

    connection = null
    session = null
    destination = null
    consumer = null
    executor = null
  }

  // Each value in partitionInfo is (PartitionUniqueRecordKey, PartitionUniqueRecordValue, Long, PartitionUniqueRecordValue) key, processed value, Start transactionid, Ignore Output Till given Value (Which is written into Output Adapter) 
  override def StartProcessing(partitionInfo: Array[StartProcPartInfo], ignoreFirstMsg: Boolean): Unit = lock.synchronized {
    LOG.debug("===============> Called StartProcessing")
    if (partitionInfo == null || partitionInfo.size == 0)
      return

    val partInfo = partitionInfo.map(quad => { (quad._key.asInstanceOf[IbmMqPartitionUniqueRecordKey], quad._val.asInstanceOf[IbmMqPartitionUniqueRecordValue], quad._validateInfoVal.asInstanceOf[IbmMqPartitionUniqueRecordValue]) })

    try {
      val ff = JmsFactoryFactory.getInstance(JmsConstants.WMQ_PROVIDER)
      val cf = ff.createConnectionFactory()
      cf.setStringProperty(CommonConstants.WMQ_HOST_NAME, qc.host_name)
      cf.setIntProperty(CommonConstants.WMQ_PORT, qc.port)
      cf.setStringProperty(CommonConstants.WMQ_CHANNEL, qc.channel)
      cf.setIntProperty(CommonConstants.WMQ_CONNECTION_MODE, qc.connection_mode)
      cf.setStringProperty(CommonConstants.WMQ_QUEUE_MANAGER, qc.queue_manager)
      if (qc.ssl_cipher_suite.size > 0)
        cf.setStringProperty(CommonConstants.WMQ_SSL_CIPHER_SUITE, qc.ssl_cipher_suite)
      // cf.setStringProperty(WMQConstants.WMQ_APPLICATIONNAME, qc.application_name)
      connection = cf.createConnection()
      session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
      if (qc.queue_name != null && qc.queue_name.size > 0)
        destination = session.createQueue(qc.queue_name)
      else if (qc.topic_name != null && qc.topic_name.size > 0)
        destination = session.createTopic(qc.topic_name)
      else {
        // Throw error
      }
      consumer = session.createConsumer(destination)
      connection.start()
    } catch {
      case jmsex: Exception =>
        val stackTrace = StackTrace.ThrowableTraceString(jmsex)
        LOG.debug("\nstackTrace:" + stackTrace)
        printFailure(jmsex)
        return
    }

    val delimiters = new DataDelimiters()
    delimiters.keyAndValueDelimiter = qc.keyAndValueDelimiter
    delimiters.fieldDelimiter = qc.fieldDelimiter
    delimiters.valueDelimiter = qc.valueDelimiter

    var threads: Int = 1
    if (threads == 0)
      threads = 1

    // create the consumer streams
    executor = Executors.newFixedThreadPool(threads)

    kvs.clear

    partInfo.foreach(quad => {
      kvs(quad._1.Name) = quad
    })

    LOG.debug("KV Map =>")
    kvs.foreach(kv => {
      LOG.debug("Key:%s => Val:%s".format(kv._2._1.Serialize, kv._2._2.Serialize))
    })

    try {
      for (i <- 0 until threads) {
        executor.execute(new Runnable() {
          override def run() {
            var execThread: ExecContext = null
            var cntr: Long = 0
            var checkForPartition = true
            val uniqueKey = new IbmMqPartitionUniqueRecordKey
            val uniqueVal = new IbmMqPartitionUniqueRecordValue

            uniqueKey.Name = qc.Name
            uniqueKey.QueueManagerName = qc.queue_manager
            uniqueKey.ChannelName = qc.channel
            uniqueKey.QueueName = qc.queue_name
            uniqueKey.TopicName = qc.topic_name

            try {
              breakable {
                while (true /* cntr < 100 */ ) {
                  // LOG.debug("Partition:%d Message:%s".format(message.partition, new String(message.message)))
                  var executeCurMsg = true

                  try {
                    val receivedMessage = consumer.receive() // Can we wait for N milli secs??
                    val readTmNs = System.nanoTime
                    val readTmMs = System.currentTimeMillis

                    executeCurMsg = (receivedMessage != null)

                    if (checkForPartition) {
                      checkForPartition = false
                      execThread = execCtxtObj.CreateExecContext(input, uniqueKey, callerCtxt)
                    }

                    if (executeCurMsg) {
                      try {

                        var msgData: Array[Byte] = null
                        var msgId: String = null

                        if (receivedMessage.isInstanceOf[BytesMessage]) {
                          val bytmsg = receivedMessage.asInstanceOf[BytesMessage]
                          val tmpmsg = new Array[Byte](bytmsg.getBodyLength().toInt)
                          bytmsg.readBytes(tmpmsg)
                          msgData = tmpmsg
                          msgId = bytmsg.getJMSMessageID()
                        } else if (receivedMessage.isInstanceOf[TextMessage]) {
                          val strmsg = receivedMessage.asInstanceOf[TextMessage]
                          msgData = strmsg.getText.getBytes
                          msgId = strmsg.getJMSMessageID()
                        }

                        if (msgData != null) {
                          uniqueVal.MessageId = msgId
                          execThread.execute(msgData, qc.formatOrInputAdapterName, uniqueKey, uniqueVal, readTmNs, readTmMs, false, qc.associatedMsg, delimiters)
                          // consumerConnector.commitOffsets // BUGBUG:: Bad way of calling to save all offsets
                          cntr += 1
                          val key = Category + "/" + qc.Name + "/evtCnt"
                          cntrAdapter.addCntr(key, 1) // for now adding each row

                        } else {
                          LOG.error("Found unhandled message :" + receivedMessage.getClass().getName())
                        }
                      } catch {
                        case e: Exception => {
                          LOG.error("Failed with Message:" + e.getMessage)
                          printFailure(e)
                        }
                      }
                    } else {
                    }
                  } catch {
                    case e: Exception => {
                      LOG.error("Failed with Message:" + e.getMessage)
                      printFailure(e)
                    }
                  }
                  if (executor.isShutdown) {
                    break
                  }
                }
              }
            } catch {
              case e: Exception => {
                LOG.error("Failed with Reason:%s Message:%s".format(e.getCause, e.getMessage))
                printFailure(e)
              }
            }
            LOG.debug("===========================> Exiting Thread")
          }
        });
      }
    } catch {
      case e: Exception => {
        LOG.error("Failed to setup Streams. Reason:%s Message:%s".format(e.getCause, e.getMessage))
        printFailure(e)
      }
    }
  }

  private def GetAllPartitionsUniqueKeys: Array[PartitionUniqueRecordKey] = lock.synchronized {
    val uniqueKey = new IbmMqPartitionUniqueRecordKey

    uniqueKey.Name = qc.Name
    uniqueKey.QueueManagerName = qc.queue_manager
    uniqueKey.ChannelName = qc.channel
    uniqueKey.QueueName = qc.queue_name
    uniqueKey.TopicName = qc.topic_name

    Array[PartitionUniqueRecordKey](uniqueKey)
  }

  override def GetAllPartitionUniqueRecordKey: Array[PartitionUniqueRecordKey] = lock.synchronized {
    GetAllPartitionsUniqueKeys
  }

  override def DeserializeKey(k: String): PartitionUniqueRecordKey = {
    val key = new IbmMqPartitionUniqueRecordKey
    try {
      LOG.debug("Deserializing Key:" + k)
      key.Deserialize(k)
    } catch {
      case e: Exception => {
        LOG.error("Failed to deserialize Key:%s. Reason:%s Message:%s".format(k, e.getCause, e.getMessage))
        printFailure(e)
        throw e
      }
    }
    key
  }

  override def DeserializeValue(v: String): PartitionUniqueRecordValue = {
    val vl = new IbmMqPartitionUniqueRecordValue
    if (v != null) {
      try {
        LOG.debug("Deserializing Value:" + v)
        vl.Deserialize(v)
      } catch {
        case e: Exception => {
          LOG.error("Failed to deserialize Value:%s. Reason:%s Message:%s".format(v, e.getCause, e.getMessage))
          printFailure(e)
          throw e
        }
      }
    }
    vl
  }

  // Not yet implemented
  override def getAllPartitionBeginValues: Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)] = {
    return Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)]()
  }

  // Not yet implemented
  override def getAllPartitionEndValues: Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)] = {
    return Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)]()
  }

}

