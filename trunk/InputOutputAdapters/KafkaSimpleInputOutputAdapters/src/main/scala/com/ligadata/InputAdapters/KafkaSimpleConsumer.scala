
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

import com.ligadata.AdaptersConfiguration.{ KafkaPartitionUniqueRecordKey, KafkaPartitionUniqueRecordValue, KafkaQueueAdapterConfiguration }
import com.ligadata.InputOutputAdapterInfo._
import kafka.api._
import kafka.common.TopicAndPartition
import scala.actors.threadpool.{ TimeUnit, ExecutorService, Executors }
import scala.util.control.Breaks._
import kafka.consumer.{ SimpleConsumer }
import java.net.{ InetAddress }
import org.apache.log4j.Logger
import scala.collection.mutable.Map
import scala.collection.breakOut
import com.ligadata.Exceptions.StackTrace

object KafkaSimpleConsumer extends InputAdapterObj {
  val METADATA_REQUEST_CORR_ID = 2
  val QUEUE_FETCH_REQUEST_TYPE = 1
  val METADATA_REQUEST_TYPE = "metadataLookup"
  val MAX_FAILURES = 2
  val MONITOR_FREQUENCY = 10000 // Monitor Topic queues every 20 seconds
  val SLEEP_DURATION = 1000 // Allow 1 sec between unsucessful fetched
  var CURRENT_BROKER: String = _
  val FETCHSIZE = 64 * 1024
  val ZOOKEEPER_CONNECTION_TIMEOUT_MS = 3000
  def CreateInputAdapter(inputConfig: AdapterConfiguration, callerCtxt: InputAdapterCallerContext, execCtxtObj: ExecContextObj, cntrAdapter: CountersAdapter): InputAdapter = new KafkaSimpleConsumer(inputConfig, callerCtxt, execCtxtObj, cntrAdapter)
}

class KafkaSimpleConsumer(val inputConfig: AdapterConfiguration, val callerCtxt: InputAdapterCallerContext, val execCtxtObj: ExecContextObj, cntrAdapter: CountersAdapter) extends InputAdapter {
  val input = this
  private val lock = new Object()
  private val LOG = Logger.getLogger(getClass)
  private var isQuiesced = false
  private var startTime: Long = 0

  private val qc = KafkaQueueAdapterConfiguration.GetAdapterConfig(inputConfig)

  LOG.debug("KAFKA ADAPTER: allocating kafka adapter for " + qc.hosts.size + " broker hosts")

  private var numberOfErrors: Int = _
  private var replicaBrokers: Set[String] = Set()
  private var readExecutor: ExecutorService = _
  private val kvs = scala.collection.mutable.Map[Int, (KafkaPartitionUniqueRecordKey, KafkaPartitionUniqueRecordValue, KafkaPartitionUniqueRecordValue)]()
  private val kvs_per_threads = scala.collection.mutable.Map[Int, scala.collection.mutable.Map[Int, (KafkaPartitionUniqueRecordKey, KafkaPartitionUniqueRecordValue, KafkaPartitionUniqueRecordValue)]]()
  private var clientName: String = _

  // Heartbeat monitor related variables.
  private var hbRunning: Boolean = false
  private var hbTopicPartitionNumber = -1
  private val hbExecutor = Executors.newFixedThreadPool(qc.hosts.size)

  /**
   *  This will stop all the running reading threads and close all the underlying connections to Kafka.
   */
  override def Shutdown(): Unit = lock.synchronized {
    StopProcessing
  }

  /**
   * Will stop all the running read threads only - a call to StartProcessing will restart the reading process
   */
  def StopProcessing(): Unit = {
    terminateReaderTasks
    terminateHBTasks
  }

  /**
   * Start processing - will start a number of threads to read the Kafka queues for a topic.  The list of Hosts servicing a
   * given topic, and the topic have been set when this KafkaConsumer_V2 Adapter was instantiated.  The partitionIds should be
   * obtained via a prior call to the adapter.  One of the hosts will be a chosen as a leader to service the requests by the
   * spawned threads.
   * @param maxParts Int - Number of Partitions
   * @param partitionIds Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue, Long, PartitionUniqueRecordValue)] - an Array of partition ids
   */
  def StartProcessing(partitionIds: Array[StartProcPartInfo], ignoreFirstMsg: Boolean): Unit = lock.synchronized {

    // Check to see if this already started
    if (startTime > 0) {
      LOG.error("KAFKA-ADAPTER: already started, or in the process of shutting down")
    }
    startTime = System.nanoTime
    LOG.debug("KAFKA-ADAPTER: Starting to read Kafka queues for topic: " + qc.topic)

    if (partitionIds == null || partitionIds.size == 0) {
      LOG.error("KAFKA-ADAPTER: Cannot process the kafka queue request, invalid parameters - number")
      return
    }

    // See how many cores there are on the system.
    var numOfCores = Runtime.getRuntime.availableProcessors;

    // Get the data about the request and set the instancePartition list.
    val partitionInfo = partitionIds.map(quad => {
      (quad._key.asInstanceOf[KafkaPartitionUniqueRecordKey],
        quad._val.asInstanceOf[KafkaPartitionUniqueRecordValue],
        quad._validateInfoVal.asInstanceOf[KafkaPartitionUniqueRecordValue])
    })

    qc.instancePartitions = partitionInfo.map(partQuad => { partQuad._1.PartitionId }).toSet

    // Make sure the data passed was valid.
    if (qc.instancePartitions == null) {
      LOG.error("KAFKA-ADAPTER: Cannot process the kafka queue request, invalid parameters - partition instance list")
      return
    }

    // Figure out the size of the thread pool to use and create that pool
    var threads = numOfCores
    // var threads = 0
    //if (threads == 0) {
    //  if (qc.instancePartitions.size == 0)
    //    threads = 1
    //  else
    //    threads = qc.instancePartitions.size
   // }

    readExecutor = Executors.newFixedThreadPool(threads)

    // Create a Map of all the partiotion Ids.
    kvs.clear
    kvs_per_threads.clear
    partitionInfo.foreach(quad => {
      kvs(quad._1.PartitionId) = quad
    })


    // Partition the incoming sack of Partitions into buckets.  For now assume the number of threads executing these will
    // be the number of logical processors available to the JVM
    for (idnx <- 1 to threads) {
      kvs_per_threads(idnx) = scala.collection.mutable.Map[Int, (KafkaPartitionUniqueRecordKey, KafkaPartitionUniqueRecordValue, KafkaPartitionUniqueRecordValue)]()
      println("Creating Bucket")
    }

    kvs.foreach(quad => {
      var bucket = (quad._1 % threads) + 1
      var temp = kvs_per_threads.get(bucket).get
      temp(quad._1) = quad._2
    })

    // Enable the adapter to process
    isQuiesced = false
    LOG.debug("KAFKA-ADAPTER: Starting " + kvs.size + " threads to process partitions")



    // Schedule a task to perform a read from a give partition.
    kvs_per_threads.foreach(kvsThreadElements => {
      readExecutor.execute(new Runnable() {
        override def run() {

          // Initialize partition scope variables.
          var sleepDuration = KafkaSimpleConsumer.SLEEP_DURATION
          var execThread: ExecContext = null
          val threadPartitions = kvsThreadElements._2

          // Initizlise message processed.
          var messagesProcessed:  scala.collection.mutable.Map[Int,Long] = (threadPartitions.map(part => (part._1,0.toLong)).toMap).map(identity)(breakOut)

          // Initialize all the offset to -1... meaning assume that unless specified, we start reading from the
          // beginning.
          var readOffsets: scala.collection.mutable.Map[Int,Long] = (threadPartitions.map(part => (part._1, -1.toLong)).toMap).map(identity)(breakOut)

          //Initialzie the LEAD BROKERS for each Partition in this subset.
          var leadBrokers: scala.collection.mutable.Map[Int,String] = (threadPartitions.map(part => (part._1,"")).toMap).map(identity)(breakOut)

          // List of already initialized Partitions.
          var initializedPartitions: scala.collection.mutable.Set[Int] = scala.collection.mutable.Set[Int]()

          // Map of consumer ojbects... per Kafka Broker.  no need to have one per partitions, since a number of partitions
          // will go to the same broker
          var consumers: scala.collection.mutable.Map[String,SimpleConsumer] = scala.collection.mutable.Map[String,SimpleConsumer]()

          // Run through all partitions assigned to this thread...  run until the thread is stopped.
          while (!isQuiesced) {
            // Process each partition
            threadPartitions.foreach(kvsElement => {
              val partitionId = kvsElement._1
              val partitionInfo = kvsElement._2
              val uniqueRecordValue = if (ignoreFirstMsg) partitionInfo._3.Offset else partitionInfo._3.Offset - 1

              val uniqueKey = new KafkaPartitionUniqueRecordKey
              val uniqueVal = new KafkaPartitionUniqueRecordValue

              // Prepare the call to Kafka.
              clientName = "Client" + qc.Name + "/" + partitionId
              uniqueKey.Name = qc.Name
              uniqueKey.TopicName = qc.topic
              uniqueKey.PartitionId = partitionId

              // Do we need to initialize this partion information.. leadborker, offset, etc....
              if (!initializedPartitions.contains(partitionId)) {
                // Get LeadBroker.
                leadBrokers(partitionId) = getKafkaConfigId(findLeader(qc.hosts, partitionId))
                // Start processing from either a beginning or a number specified by the KamanjaMananger
                readOffsets(partitionId) = getKeyValueForPartition(leadBrokers(partitionId), partitionId, kafka.api.OffsetRequest.EarliestTime)
                if (partitionInfo._2.Offset > readOffsets(partitionId)) {
                  readOffsets(partitionId) = partitionInfo._2.Offset
                }
                // See if we can determine the right offset, bail if we can't
                if (readOffsets(partitionId) == -1) {
                  LOG.error("KAFKA-ADAPTER: Unable to initialize new reader thread for partition {" + partitionId + "} starting at offset " + readOffsets(partitionId) + " on server - " + leadBrokers(partitionId) + ", Invalid OFFSET")
                  return
                }
                LOG.debug("KAFKA-ADAPTER: Initializing new reader thread for partition {" + partitionId + "} starting at offset " + readOffsets(partitionId) + " on server - " + leadBrokers(partitionId))

                val brokerId = convertIp(leadBrokers(partitionId))
                val brokerName = brokerId.split(":")
                if (!consumers.contains(brokerId)) {
                  consumers(leadBrokers(partitionId)) = new SimpleConsumer(brokerName(0), brokerName(1).toInt,
                                                            KafkaSimpleConsumer.ZOOKEEPER_CONNECTION_TIMEOUT_MS,
                                                            KafkaSimpleConsumer.FETCHSIZE,
                                                            KafkaSimpleConsumer.METADATA_REQUEST_TYPE)
                }
              }

              // This partition has been initiazlised... issue a call to fetch messages.
              val fetchReq = new FetchRequestBuilder().clientId(clientName).addFetch(qc.topic, partitionId, readOffsets(partitionId), KafkaSimpleConsumer.FETCHSIZE).build();
              val fetchResp = consumers(leadBrokers(partitionId)).fetch(fetchReq)
              // Check for errors
              if (fetchResp.hasError) {
                LOG.error("KAFKA-ADAPTER: Error occured reading from " + leadBrokers(partitionId) + " " + ", error code is " + fetchResp.errorCode(qc.topic, partitionId))
                numberOfErrors = numberOfErrors + 1
                if (numberOfErrors > KafkaSimpleConsumer.MAX_FAILURES) {
                  LOG.error("KAFKA-ADAPTER: Too many failures reading from kafka adapters.")
                  if (consumers.contains(leadBrokers(partitionId))) consumers(leadBrokers(partitionId)).close
                  return
                }
              }

              val ignoreTillOffset = if (ignoreFirstMsg) partitionInfo._2.Offset else partitionInfo._2.Offset - 1

              // Pull the individual messages out
              fetchResp.messageSet(qc.topic, partitionId).foreach (msgBuffer => {
                val bufferPayload = msgBuffer.message.payload
                val message: Array[Byte] = new Array[Byte](bufferPayload.limit)
                readOffsets(partitionId) = msgBuffer.nextOffset
                breakable {
                  val readTmNs = System.nanoTime
                  val readTmMs = System.currentTimeMillis
                  messagesProcessed(partitionId) = messagesProcessed(partitionId) + 1

                  // Engine in interested in message at OFFSET + 1, Because I cannot guarantee that offset for a partition
                  // is increasing by one, and I cannot simple set the offset to offset++ since that can cause our of
                  // range errors on the read, we simple ignore the message by with the offset specified by the engine.
                  if (msgBuffer.offset <= ignoreTillOffset) {
                    LOG.debug("KAFKA-ADAPTER: skipping a message at  Broker: " + leadBrokers(partitionId) + "_" + partitionId + " OFFSET " + msgBuffer.offset + " " + new String(message, "UTF-8") + " - previously processed! ")
                    break
                  }

                  // OK, present this message to the Engine. - May need to create a execution context.
                  bufferPayload.get(message)
                  LOG.debug("KAFKA-ADAPTER: Broker: " + leadBrokers(partitionId) + "_" + partitionId + " OFFSET " + msgBuffer.offset + " Message: " + new String(message, "UTF-8"))


                  // Create a new EngineMessage and call the engine.
                  if (execThread == null) {
                    execThread = execCtxtObj.CreateExecContext(input, uniqueKey, callerCtxt)
                  }
                  uniqueVal.Offset = msgBuffer.offset
                  val dontSendOutputToOutputAdap = uniqueVal.Offset <= uniqueRecordValue
                  execThread.execute(message, qc.formatOrInputAdapterName, uniqueKey, uniqueVal, readTmNs, readTmMs, dontSendOutputToOutputAdap, qc.associatedMsg, qc.delimiterString)
                  val key = Category + "/" + qc.Name + "/evtCnt"
                  cntrAdapter.addCntr(key, 1) // for now adding each row

                }
              })
            })  // Process each partition


            // Ok, we have been throught the partitions once... see if we need to suspend a bit if there aren't any
            //
            try {
              // Sleep here, only if input parm for sleep is set and we haven't gotten any messages on the previous kafka call.
              if ((qc.noDataSleepTimeInMs > 0) && (messagesProcessed.filter(id => id._2 > 0).toSet.size == 0)) Thread.sleep(qc.noDataSleepTimeInMs)
              messagesProcessed = (threadPartitions.map(part => (part._1,0.toLong)).toMap).map(identity)(breakOut)
            } catch {
              case e: java.lang.InterruptedException =>
              {
                val stackTrace = StackTrace.ThrowableTraceString(e)
                LOG.debug("KAFKA ADAPTER: Forcing down the Consumer Reader thread"+"\nStackTrace:"+stackTrace)
              }
            }
          } // while(quiesce) loop

          // we are out of subpartition "infinite loop"  clean up the resources... Consumers that is.
          consumers.foreach(consumer => {consumer._2.close})
        }  // RUN - the executable thread for each sub-partition
      })
    })  // Process all subpartition.
  }

  /**
   * getServerInfo - returns information about hosts and their coresponding partitions.
   * @return Array[PartitionUniqueRecordKey] - return data
   */
  def GetAllPartitionUniqueRecordKey: Array[PartitionUniqueRecordKey] = lock.synchronized {
    // iterate through all the simple consumers - collect the metadata about this topic on each specified host
    var partitionRecord: scala.collection.mutable.Set[String] = scala.collection.mutable.Set[String]()

    val topics: Array[String] = Array(qc.topic)
    val metaDataReq = new TopicMetadataRequest(topics, KafkaSimpleConsumer.METADATA_REQUEST_CORR_ID)
    var partitionNames: List[KafkaPartitionUniqueRecordKey] = List()

    LOG.debug("KAFKA-ADAPTER - Querying kafka for Topic " + qc.topic + " metadata(partitions)")

    qc.hosts.foreach(broker => {
      val brokerName = broker.split(":")
      val partConsumer = new SimpleConsumer(brokerName(0), brokerName(1).toInt,
        KafkaSimpleConsumer.ZOOKEEPER_CONNECTION_TIMEOUT_MS,
        KafkaSimpleConsumer.FETCHSIZE,
        KafkaSimpleConsumer.METADATA_REQUEST_TYPE)
      try {
        val metaDataResp: kafka.api.TopicMetadataResponse = partConsumer.send(metaDataReq)
        val metaData = metaDataResp.topicsMetadata
        metaData.foreach(topicMeta => {
          topicMeta.partitionsMetadata.foreach(partitionMeta => {
            val uniqueKey = new KafkaPartitionUniqueRecordKey
            uniqueKey.PartitionId = partitionMeta.partitionId
            uniqueKey.Name = qc.Name
            uniqueKey.TopicName = qc.topic
            if (!partitionRecord.contains(qc.topic+partitionMeta.partitionId.toString)) {
              partitionNames = uniqueKey :: partitionNames 
              partitionRecord = partitionRecord + (qc.topic+partitionMeta.partitionId.toString)
            }
          })
        })
      } catch {
        case e: java.lang.InterruptedException =>{
          LOG.error("KAFKA-ADAPTER: Communication interrupted with broker " + broker + " while getting a list of partitions")}
      } finally {
        if (partConsumer != null) { partConsumer.close }
      }
    })
    return partitionNames.toArray
  }

  /**
   * Return an array of PartitionUniqueKey/PartitionUniqueRecordValues whre key is the partion and value is the offset
   * within the kafka queue where it begins.
   * @return Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)]
   */
  override def getAllPartitionBeginValues: Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)] = lock.synchronized {
    return getKeyValues(kafka.api.OffsetRequest.EarliestTime)
  }

  /**
   * Return an array of PartitionUniqueKey/PartitionUniqueRecordValues whre key is the partion and value is the offset
   * within the kafka queue where it eds.
   * @return Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)]
   */
  override def getAllPartitionEndValues: Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)] = lock.synchronized {
    return getKeyValues(kafka.api.OffsetRequest.LatestTime)
  }

  override def DeserializeKey(k: String): PartitionUniqueRecordKey = {
    val key = new KafkaPartitionUniqueRecordKey
    try {
      LOG.debug("Deserializing Key:" + k)
      key.Deserialize(k)
    } catch {
      case e: Exception => {
        LOG.error("Failed to deserialize Key:%s. Reason:%s Message:%s".format(k, e.getCause, e.getMessage))
        throw e
      }
    }
    key
  }

  override def DeserializeValue(v: String): PartitionUniqueRecordValue = {
    val vl = new KafkaPartitionUniqueRecordValue
    if (v != null) {
      try {
        LOG.debug("Deserializing Value:" + v)
        vl.Deserialize(v)
      } catch {
        case e: Exception => {
          LOG.error("Failed to deserialize Value:%s. Reason:%s Message:%s".format(v, e.getCause, e.getMessage))
          throw e
        }
      }
    }
    vl
  }

  /**
   *  Find a leader of for this topic for a given partition.
   */
  private def findLeader(brokers: Array[String], inPartition: Int): kafka.api.PartitionMetadata = lock.synchronized {
    var leaderMetadata: kafka.api.PartitionMetadata = null

    LOG.debug("KAFKA-ADAPTER: Looking for Kafka Topic Leader for partition " + inPartition)
    try {
      breakable {
        brokers.foreach(broker => {
          // Create a connection to this broker to obtain the metadata for this broker.
          val brokerName = broker.split(":")
          val llConsumer = new SimpleConsumer(brokerName(0), brokerName(1).toInt, KafkaSimpleConsumer.ZOOKEEPER_CONNECTION_TIMEOUT_MS,
            KafkaSimpleConsumer.FETCHSIZE, KafkaSimpleConsumer.METADATA_REQUEST_TYPE)
          val topics: Array[String] = Array(qc.topic)
          val llReq = new TopicMetadataRequest(topics, KafkaSimpleConsumer.METADATA_REQUEST_CORR_ID)

          // get the metadata on the llConsumer
          try {
            val llResp: kafka.api.TopicMetadataResponse = llConsumer.send(llReq)
            val metaData = llResp.topicsMetadata

            // look at each piece of metadata, and analyze its partitions
            metaData.foreach(metaDatum => {
              val partitionMetadata = metaDatum.partitionsMetadata
              partitionMetadata.foreach(partitionMetadatum => {
                // If we found the partitionmetadatum for the desired partition then this must be the leader.
                if (partitionMetadatum.partitionId == inPartition) {
                  // Create a list of replicas to be used in case of a fetch failure here.
                  replicaBrokers.empty
                  partitionMetadatum.replicas.foreach(replica => {
                    replicaBrokers = replicaBrokers + (replica.host)
                  })
                  leaderMetadata = partitionMetadatum
                }
              })
            })
          } catch {
            case e: Exception => { 
              val stackTrace = StackTrace.ThrowableTraceString(e)
              LOG.debug("KAFKA-ADAPTER: Communicatin problem with broker " + broker + " trace " + stackTrace) }
          } finally {
            if (llConsumer != null) llConsumer.close()
          }
        })
      }

    } catch {
      case e: Exception => { 
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("KAFKA ADAPTER - Fatal Error for FindLeader for partition " + inPartition+"\nStackTrace:"+stackTrace) }
    }
    return leaderMetadata;
  }

  /*
* getKeyValues - get the values from the OffsetMetadata call and combine them into an array
 */
  private def getKeyValues(time: Long): Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)] = {
    var infoList = List[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)]()
    // Always get the complete list of partitions for this.
    val kafkaKnownParitions = GetAllPartitionUniqueRecordKey
    val partitionList = kafkaKnownParitions.map(uKey => { uKey.asInstanceOf[KafkaPartitionUniqueRecordKey].PartitionId }).toSet

    // Now that we know for sure we have a partition list.. process them
    partitionList.foreach(partitionId => {
      val offset = getKeyValueForPartition(getKafkaConfigId(findLeader(qc.hosts, partitionId)), partitionId, time)
      val rKey = new KafkaPartitionUniqueRecordKey
      val rValue = new KafkaPartitionUniqueRecordValue
      rKey.PartitionId = partitionId
      rKey.Name = qc.Name
      rKey.TopicName = qc.topic
      rValue.Offset = offset
      infoList = (rKey, rValue) :: infoList
    })
    return infoList.toArray
  }

  /**
   * getKeyValueForPartition - get the valid offset range in a given partition.
   */
  def getKeyValueForPartition(leadBroker: String, partitionId: Int, timeFrame: Long): Long = {
    var offset: Long = -1
    var llConsumer: kafka.javaapi.consumer.SimpleConsumer = null
    val brokerName = leadBroker.split(":")
    try {
      llConsumer = new kafka.javaapi.consumer.SimpleConsumer(brokerName(0), brokerName(1).toInt,
        KafkaSimpleConsumer.ZOOKEEPER_CONNECTION_TIMEOUT_MS,
        KafkaSimpleConsumer.FETCHSIZE,
        KafkaSimpleConsumer.METADATA_REQUEST_TYPE)

      // Set up the request object
      val jtap: kafka.common.TopicAndPartition = kafka.common.TopicAndPartition(qc.topic.toString, partitionId)
      val requestInfo: java.util.HashMap[TopicAndPartition, PartitionOffsetRequestInfo] = new java.util.HashMap[TopicAndPartition, PartitionOffsetRequestInfo]()
      requestInfo.put(jtap, new PartitionOffsetRequestInfo(timeFrame, 1))
      val offsetRequest: kafka.javaapi.OffsetRequest = new kafka.javaapi.OffsetRequest(requestInfo, kafka.api.OffsetRequest.CurrentVersion, clientName)

      // Issue the call
      val response: kafka.javaapi.OffsetResponse = llConsumer.getOffsetsBefore(offsetRequest)

      // Return the value, or handle the error
      if (response.hasError) {
        LOG.error("KAFKA ADAPTER: error occured trying to find out the valid range for partition {" + partitionId + "}")
      } else {
        val offsets: Array[Long] = response.offsets(qc.topic.toString, partitionId)
        offset = offsets(0)
      }
    } catch {
      case e: java.lang.Exception => {
        
        LOG.error("KAFKA ADAPTER: Exception during offset inquiry request for partiotion {" + partitionId + "}") }
    } finally {
      if (llConsumer != null) { llConsumer.close }
    }
    return offset
  }

  /**
   *  Previous request failed, need to find a new leader
   */
  private def findNewLeader(oldBroker: String, partitionId: Int): kafka.api.PartitionMetadata = {
    // There are moving parts in Kafka under the failure condtions, we may not have an immediately availabe new
    // leader, so lets try 3 times to get the new leader before bailing
    for (i <- 0 until 3) {
      try {
        val leaderMetaData = findLeader(replicaBrokers.toArray[String], partitionId)
        // Either new metadata leader is not available or the the new broker has not been updated in kafka
        if (leaderMetaData == null || leaderMetaData.leader == null ||
          (leaderMetaData.leader.get.host.equalsIgnoreCase(oldBroker) && i == 0)) {
          Thread.sleep(KafkaSimpleConsumer.SLEEP_DURATION)
        } else {
          return leaderMetaData
        }
      } catch {
        case e: InterruptedException => {
          val stackTrace = StackTrace.ThrowableTraceString(e)
          LOG.debug("Adapter terminated during findNewLeader"+"\nStackTrace:"+stackTrace) }
      }
    }
    return null
  }

  /**
   * beginHeartbeat - This adapter will begin monitoring the partitions for the specified topic
   */
  def beginHeartbeat(): Unit = lock.synchronized {
    LOG.debug("Starting monitor for Kafka QUEUE: " + qc.topic)
    startHeartBeat()
  }

  /**
   *  stopHeartbeat - signal this adapter to shut down the monitor thread
   */
  def stopHearbeat(): Unit = lock.synchronized {
    try {
      hbRunning = false
      hbExecutor.shutdownNow()
    } catch {
      case e: java.lang.InterruptedException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("Heartbeat terminated"+"\nStackTrace:"+stackTrace)}
    }
  }

  /**
   * Private method to start a heartbeat task, and the code that the heartbeat task will execute.....
   */
  private def startHeartBeat(): Unit = {
    // only start 1 heartbeat
    if (hbRunning) return

    // Block any more heartbeats from being spawned
    hbRunning = true

    // start new heartbeat here.
    hbExecutor.execute(new Runnable() {
      override def run() {
        // Get a connection to each server
        val hbConsumers: Map[String, SimpleConsumer] = Map()
        qc.hosts.foreach(host => {
          val brokerName = host.split(":")
          hbConsumers(host) = new SimpleConsumer(brokerName(0), brokerName(1).toInt,
            KafkaSimpleConsumer.ZOOKEEPER_CONNECTION_TIMEOUT_MS,
            KafkaSimpleConsumer.FETCHSIZE,
            KafkaSimpleConsumer.METADATA_REQUEST_TYPE)
        })

        val topics = Array[String](qc.topic)
        // Get the metadata for each monitored Topic and see if it changed.  If so, notify the engine

        try {
          while (hbRunning) {
            LOG.debug("Heartbeat checking status of " + hbConsumers.size + " broker(s)")
            hbConsumers.foreach {
              case (key, consumer) => {
                val req = new TopicMetadataRequest(topics, KafkaSimpleConsumer.METADATA_REQUEST_CORR_ID)
                val resp: kafka.api.TopicMetadataResponse = consumer.send(req)
                resp.topicsMetadata.foreach(metaTopic => {
                  if (metaTopic.partitionsMetadata.size != hbTopicPartitionNumber) {
                    // TODO: Need to know how to call back to the Engine
                    // first time through the heartbeat
                    if (hbTopicPartitionNumber != -1) {
                      LOG.debug("Partitions changed for TOPIC - " + qc.topic + " on broker " + key + ", it is now" + metaTopic.partitionsMetadata.size)
                    }
                    hbTopicPartitionNumber = metaTopic.partitionsMetadata.size
                  }
                })
              }
            }
            try {
              Thread.sleep(KafkaSimpleConsumer.MONITOR_FREQUENCY)
            } catch {
              case e: java.lang.InterruptedException =>
                val stackTrace = StackTrace.ThrowableTraceString(e)
                LOG.debug("Shutting down the Monitor heartbeat"+"\nStackTrace:"+stackTrace)
                hbRunning = false
            }
          }
        } catch {
          case e: java.lang.Exception => {
            LOG.error("Heartbeat forced down due to exception + ")}
        } finally {
          hbConsumers.foreach({ case (key, consumer) => { consumer.close } })
          hbRunning = false
          LOG.debug("Monitor is down")
        }
      }
    })
  }

  /**
   *  Convert the "localhost:XXXX" into an actual IP address.
   */
  private def convertIp(inString: String): String = {
    val brokerName = inString.split(":")
    if (brokerName(0).equalsIgnoreCase("localhost")) {
      brokerName(0) = InetAddress.getLocalHost().getHostAddress()
    }
    val brokerId = brokerName(0) + ":" + brokerName(1)
    brokerId
  }
  


  /**
   * combine the ip address and port number into a Kafka Configuratio ID
   */
  private def getKafkaConfigId(metadata: kafka.api.PartitionMetadata): String = {
    return metadata.leader.get.host + ":" + metadata.leader.get.port;
  }

  /**
   * terminateHBTasks - Just what it says
   */
  private def terminateHBTasks(): Unit = {
    if (hbExecutor == null) return
    hbExecutor.shutdownNow
    while (hbExecutor.isTerminated == false ) {
      Thread.sleep(100) // sleep 100ms and then check
    }
  }

  /**
   *  terminateReaderTasks - well, just what it says
   */
  private def terminateReaderTasks(): Unit = {
    if (readExecutor == null) return

    // Tell all thread to stop processing on the next interval, and shutdown the Excecutor.
    quiesce

    // Give the threads to gracefully stop their reading cycles, and then execute them with extreme prejudice.
    Thread.sleep(qc.noDataSleepTimeInMs + 1)
    readExecutor.shutdownNow
    while (readExecutor.isTerminated == false ) {
      Thread.sleep(100)
    }

    LOG.debug("KAFKA_ADAPTER - Shutdown Complete")
    readExecutor = null
    startTime = 0
  }

  /* no need for any synchronization here... it can only go one way.. worst case scenario, a reader thread gets to try to
  *  read the kafka queue one extra time (100ms lost)
   */
  private def quiesce: Unit = {
    isQuiesced = true
  }

}

