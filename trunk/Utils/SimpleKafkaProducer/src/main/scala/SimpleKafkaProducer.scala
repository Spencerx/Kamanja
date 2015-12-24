
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

import org.json4s.jackson.JsonMethods._

import scala.actors.threadpool.{ Executors, TimeUnit }
import scala.collection.mutable.ArrayBuffer
import java.util.Properties
import kafka.message._
import kafka.producer.{ ProducerConfig, Producer, KeyedMessage, Partitioner }
import java.io.{ InputStream, ByteArrayInputStream }
import java.util.zip.GZIPInputStream
import java.nio.file.{Files, Paths }
import kafka.utils.VerifiableProperties
import com.ligadata.Utils.KeyHasher
import com.ligadata.Exceptions.StackTrace
import org.apache.logging.log4j._

object ProducerSimpleStats {

  private[this] val _lock = new Object()
  private[this] val _statusInfo = scala.collection.mutable.Map[Int, String]()

  def setKey(key: Int, value: String): Unit = _lock.synchronized {
    _statusInfo(key) = value
  }

  def getKey(key: Int): String = _lock.synchronized {
    _statusInfo.getOrElse(key, "")
  }

  def getDispString(delim: String = ","): String = _lock.synchronized {
    _statusInfo.map(s => s._2).mkString(delim)
  }
}

class  ExtractKey {
  def get(inputData: String, partitionkeyidxs: Array[Int], defaultKey: String, keyPartDelim: String): String = {
    if (partitionkeyidxs.size == 0) {
      return defaultKey
    }
    val str_arr = inputData.split(",", -1)
    if (str_arr.size == 0)
      throw new Exception("Not found any values in message")
    return partitionkeyidxs.map(idx => str_arr(idx)).mkString(keyPartDelim)
  }

  // This is used to create a hashvalue for a Json message. this assumes that partitionkeyindx actually has the values
  // to be used for the key generation.
  def get(inputData: String, partitionkeyidxs: Array[String], defaultKey: String, keyPartDelim: String): String = {
    if (partitionkeyidxs.size == 0) {
      return defaultKey
    }
  
    return partitionkeyidxs.mkString(keyPartDelim)
  }
}

class CustPartitioner(props: VerifiableProperties) extends Partitioner {
   val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
  def partition(key: Any, a_numPartitions: Int): Int = {

    if (key == null) return 0
    try {
      if (key.isInstanceOf[Array[Byte]]) {
        val hashCode = SimpleKafkaProducer.keyHasher.hashKey(key.asInstanceOf[Array[Byte]]).abs
        val bucket = (hashCode % a_numPartitions).toInt
        // println("Key : %s, hashCode: %d, Partitions: %d, Bucket : %d".format(new String(key.asInstanceOf[Array[Byte]]), hashCode, a_numPartitions, bucket))
        return bucket 
      } else {
        // println("Keynot cound, so , Bucket : 0")
        // println("Bucket : is always 0")
        return 0
      }
    } catch {
      case e: Exception =>
        {
          val stackTrace = StackTrace.ThrowableTraceString(e)
          logger.debug("StackTrace:"+stackTrace)
        }
        // println("Exception found, so , Bucket : 0")
        return 0
    }
  }
}

/**
 *  Object used to insert messages from a specified source (files for now) to a specified Kafka queues
 */
object SimpleKafkaProducer {

  class Stats {
    var totalLines: Long = 0;
    var totalRead: Long = 0;
    var totalSent: Long = 0
  }
  val CSV_KEY_FOR_PARTITION_INDEX = "csvkey"
  val keyHasher = KeyHasher.byName("fnv1a-32")
  val clientId: String = "Client1"

  val compress: Boolean = false
  val synchronously: Boolean = false
  val batchSize: Integer = 1024
  val queueTime: Integer = 50
  val queueSize: Integer = 16 * 1024 * 1024
  val bufferMemory: Integer = 64 * 1024 * 1024
  val messageSendMaxRetries: Integer = 3
  val requestRequiredAcks: Integer = 1

  val codec = if (compress) DefaultCompressionCodec.codec else NoCompressionCodec.codec
  
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)

  def send(producer: Producer[AnyRef, AnyRef], topic: String, message: String, partIdx: String): Unit = send(producer, topic, message.getBytes("UTF8"), partIdx.getBytes("UTF8"))

  def send(producer: Producer[AnyRef, AnyRef], topic: String, message: Array[Byte], partIdx: Array[Byte]): Unit = {
    try {
      producer.send(new KeyedMessage(topic, partIdx, message))
      //producer.send(new KeyedMessage(topic, message))
    } catch {
      case e: Exception =>
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        sys.exit(1)
    }
  }


  /**
   * ProcessFile - Process a file specified by the below parameters
   * @param producer - Producer[AnyRef, AnyRef]
   * @param topics - Array[String]
   * @param threadId - Int
   * @param sFileName - String
   * @param msg -String
   * @param sleeptm - Int
   * @param partitionkeyidxs Any
   * @param st - Stats
   * @param ignorelines - Int
   * @param format - String
   * @param isGzip Boolean
   * @param topicpartitions Int
   * @param isVerbose Boolean
   */
  def ProcessFile(producer: Producer[AnyRef, AnyRef], topics: Array[String], threadId: Int, sFileName: String, msg: String, sleeptm: Int, partitionkeyidxs: Any, st: Stats, ignorelines: Int, format: String, isGzip: Boolean, topicpartitions: Int, isVerbose: Boolean): Unit = {

    var bis: InputStream = null

    // If from the Gzip, wrap around a GZIPInput Stream, else... use the ByteArrayInputStream
    if(isGzip) {
      bis = new GZIPInputStream(new ByteArrayInputStream(Files.readAllBytes(Paths.get(sFileName))))
    } else {
      bis = new ByteArrayInputStream(Files.readAllBytes(Paths.get(sFileName)))
    }

    try {
      if (format.equalsIgnoreCase("json")) {
        processJsonFile(producer, topics, threadId, sFileName, msg, sleeptm, partitionkeyidxs.asInstanceOf[scala.collection.mutable.Map[String,List[String]]], st, topicpartitions, bis, isVerbose)
      } else if (format.equalsIgnoreCase("csv")) {
        processCSVFile(producer, topics, threadId, sFileName, msg, sleeptm, partitionkeyidxs.asInstanceOf[Array[Int]], st, ignorelines, topicpartitions, bis, isVerbose)
      }else {
        throw new Exception("Only following formats are supported: CSV,JSON")
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:"+stackTrace)
        println("Error reading from a file ")}
    } finally {
      if (bis != null) bis.close
    }
  }


  /*
* processCSVFile - dealing with CSV File
 */
  private def processCSVFile(producer: Producer[AnyRef, AnyRef], topics: Array[String], threadId: Int, sFileName: String, msg: String, sleeptm: Int, partitionkeyidxs: Array[Int], st: Stats, ignorelines: Int, topicpartitions: Int, bis: InputStream, isVerbose: Boolean): Unit = {
    var len = 0
    var readlen = 0
    var totalLen: Int = 0
    var locallinecntr: Int = 0
    val maxlen = 1024 * 1024
    val buffer = new Array[Byte](maxlen)
    var tm = System.nanoTime
    val extractKey: ExtractKey = new ExtractKey
    var ignoredlines = 0
    var curTime = System.currentTimeMillis
    do {
      readlen = bis.read(buffer, len, maxlen - 1 - len)
      if (readlen > 0) {
        totalLen += readlen
        len += readlen
        var startidx: Int = 0
        var isrn: Boolean = false
        for (idx <- 0 until len) {
          if ((isrn == false && buffer(idx) == '\n') || (buffer(idx) == '\r' && idx + 1 < len && buffer(idx + 1) == '\n')) {
            if ((locallinecntr % 20) == 0)
              curTime = System.currentTimeMillis
            locallinecntr += 1
            val strlen = idx - startidx
            if (sleeptm > 0)
              Thread.sleep(sleeptm)
            if (ignoredlines < ignorelines) {
              ignoredlines += 1
            } else {
              if (strlen > 0) {
                val ln = new String(buffer, startidx, idx - startidx)
                val defPartKey = st.totalLines.toString
                val key = extractKey.get(ln, partitionkeyidxs, defPartKey, ".")
                val hashCode = keyHasher.hashKey(key.getBytes()).abs
                val totalParts = (hashCode % (topicpartitions * topics.size)).toInt.abs
                val topicIdx = (totalParts / topicpartitions)
                val partIdx = (totalParts % topicpartitions)
                val sendmsg = if (msg.size == 0) ln else (msg + "," + ln)
                printline(key+"."+hashCode+"."+totalParts+":  "+sendmsg,isVerbose)
                send(producer, topics(topicIdx), sendmsg, key)
                st.totalRead += ln.size
                st.totalSent += sendmsg.size
              }
            }
            startidx = idx + 1;
            if (buffer(idx) == '\r') // Inc one more char in case of \r \n
            {
              startidx += 1;
              isrn = true
            }
            st.totalLines += 1;

            val curTm = System.nanoTime
            if ((curTm - tm) > 10 * 1000000000L) {
              tm = curTm
              println("Tid:%2d, Time:%10dms, Lines:%8d, Read:%15d, Sent:%15d".format(threadId, curTm / 1000000, st.totalLines, st.totalRead, st.totalSent))
            }
          } else {
            isrn = false
          }
        }

        var destidx: Int = 0
        // move rest of the data left to starting of the buffer
        for (idx <- startidx until len) {
          buffer(destidx) = buffer(idx)
          destidx += 1
        }
        len = destidx
      }
    } while (readlen > 0)

    if (len > 0 && ignoredlines >= ignorelines) {
      val ln = new String(buffer, 0, len)
      val defPartKey = st.totalLines.toString
      val key = extractKey.get(ln, partitionkeyidxs, defPartKey, ".")
      val sendmsg = if (msg.size == 0) ln else (msg + "," + ln)
      printline(key+":  "+sendmsg,isVerbose)
      send(producer, topics((st.totalLines % topics.size).toInt), sendmsg, key)
      st.totalRead += ln.size
      st.totalSent += sendmsg.size
      st.totalLines += 1;
    }

    val curTm = System.nanoTime
    println("Tid:%2d, Time:%10dms, Lines:%8d, Read:%15d, Sent:%15d, Last, file:%s".format(threadId, curTm / 1000000, st.totalLines, st.totalRead, st.totalSent, sFileName))
  }

  /**
   * processJsonFile - dealing with Json File full of individual json documents... parse individual Json documents from it and insert them individually
   *                   the JSON format will be enforced by the json parser. The outer most curly parents are used to determine where the document
   *                   begin and end.
   */
  private def processJsonFile(producer: Producer[AnyRef, AnyRef], topics: Array[String], threadId: Int, sFileName: String, msg: String, sleeptm: Int, partitionkeyidxs:scala.collection.mutable.Map[String,List[String]], st: Stats, topicpartitions: Int, bis: InputStream, isVerbose: Boolean): Unit ={
    var readlen = 0
    val len = 0
    val maxlen = 1024 * 1024
    val buffer = new Array[Byte](maxlen)
    var tm = System.nanoTime

    var op = 0
    var cp = 0
    var balance  = 0
    var curr = 0

    // Loop through the file looking for Json documents and process them
    do {
      readlen = bis.read(buffer, len, maxlen - 1 - len)
     // while (curr < contentArray.size) {
      val nextChunkEnd = curr + readlen
      while (curr < nextChunkEnd) {
        if (buffer(curr).toChar == '{') {
          //first Open paren
          if (balance == 0) {
            op = curr
          }
          balance = balance + 1
        }

        if (buffer(curr).toChar == '}') {
          balance = balance - 1
          // Closes the paren - reset we got the supposedly valid jason document
          if (balance == 0) {
            cp = curr

            val jsonDoc = buffer.slice(op,cp+1).map(byte => byte.toChar).mkString
            processJsonDoc(jsonDoc, producer: Producer[AnyRef, AnyRef], topics, threadId, msg, sleeptm, partitionkeyidxs, st, topicpartitions,isVerbose)
          }
        }
        curr = curr + 1
      }
      if (sleeptm > 0)
        Thread.sleep(sleeptm)


      // let the user know intermediate status updates
      val curTm = System.nanoTime
      if ((curTm - tm) > 10 * 1000000000L) {
        tm = curTm
        println("Tid:%2d, Time:%10dms, Lines:%8d, Read:%15d, Sent:%15d".format(threadId, curTm / 1000000, st.totalLines, st.totalRead, st.totalSent))
      }

    } while (readlen > 0)

    val curTm = System.nanoTime
    println("Tid:%2d, Time:%10dms, Lines:%8d, Read:%15d, Sent:%15d, Last, file:%s".format(threadId, curTm / 1000000, st.totalLines, st.totalRead, st.totalSent, sFileName))
  }

  /*
  * processJsonDoc - add an individual json doc to whatever queue is specified here.
  */
  private def processJsonDoc(doc: String, producer: Producer[AnyRef, AnyRef],
                             topics: Array[String], threadId: Int, msg: String, sleeptm: Int,
                             partitionkeyidxs: scala.collection.mutable.Map[String,List[String]], st: Stats, topicpartitions: Int,
                             isVerbose:Boolean): Unit = {

    val extractKey: ExtractKey = new ExtractKey
    var jsonPartitionKeyidxs: List[String] = List[String]()
    val defPartKey = st.totalLines.toString

    // Get the json object from
    val jsonMsg = parse(doc).values.asInstanceOf[Map[String, Any]]
    val msgTypeIter = partitionkeyidxs.keysIterator

    // There better be only 1 element here, since all messages here must be of the {"Type":{message body}} format.
    // if this changes in the future, we obviously have to revisit this.
    if (!msgTypeIter.hasNext) {
      println ("Empty Document - missing message type for the follwoing docuemnt:")
      println (doc)
      return
    }
    var msgType: String = null
    var msgBody: Map[String, Any] = null

    while (msgBody == null && msgTypeIter.hasNext) {
      msgType = msgTypeIter.next
      msgBody = jsonMsg.getOrElse(msgType, null).asInstanceOf[Map[String, Any]]
    }

    if (msgBody == null) {
      println ("Empty Body for a message- missing message type for the follwoing docuemnt:")
      println (doc)
      return
    }

    // Read the values of Keys as specified on the input paramters, and combine them into t a List[String]
    val keyList = partitionkeyidxs.getOrElse(msgType,null)
    if (keyList != null ) {
      keyList.foreach(key => {
        val value: String = msgBody.getOrElse(key,null).asInstanceOf[String]
        if (value != null){
          jsonPartitionKeyidxs = List[String](value) ::: jsonPartitionKeyidxs}
      })
    }

    // Get the right key partitioning information and insert into the Kafka
    val key = extractKey.get(doc, jsonPartitionKeyidxs.toArray, defPartKey, ".")
    val hashCode = keyHasher.hashKey(key.getBytes()).abs
    val totalParts = (hashCode % (topicpartitions * topics.size)).toInt.abs
    val topicIdx = (totalParts / topicpartitions)
    val partIdx = (totalParts % topicpartitions)
    val sendmsg = if (msg.size == 0) doc else (msg + "," + doc)
    printline(key+"."+hashCode+"."+totalParts+":  "+sendmsg,isVerbose)
    send(producer, topics(topicIdx), sendmsg, key)
    st.totalRead += doc.size
    st.totalSent += sendmsg.size
  }

  /*
  * elapsed
   */
  private def elapsed[A](f: => A): Long = {
    val s = System.nanoTime
    f
    (System.nanoTime - s)
  }

  type OptionMap = Map[Symbol, Any]

  /*
  * nextOption - parsing input options
   */
  private def nextOption(map: OptionMap, list: List[String]): OptionMap = {
    def isSwitch(s: String) = (s(0) == '-')
    list match {
      case Nil => map
      case "--files" :: value :: tail =>
        nextOption(map ++ Map('files -> value), tail)
      case "--sleep" :: value :: tail =>
        nextOption(map ++ Map('sleep -> value), tail)
      case "--gz" :: value :: tail =>
        nextOption(map ++ Map('gz -> value), tail)
      case "--topics" :: value :: tail =>
        nextOption(map ++ Map('topics -> value), tail)
      case "--msg" :: value :: tail =>
        nextOption(map ++ Map('msg -> value), tail)
      case "--threads" :: value :: tail =>
        nextOption(map ++ Map('threads -> value), tail)
      case "--partitionkeyidxs" :: value :: tail =>
        nextOption(map ++ Map('partitionkeyidxs -> value), tail)
      case "--format" :: value :: tail =>
        nextOption(map ++ Map('format -> value), tail)
      case "--ignorelines" :: value :: tail =>
        nextOption(map ++ Map('ignorelines -> value), tail)
      case "--topicpartitions" :: value :: tail =>
        nextOption(map ++ Map('topicpartitions -> value), tail)
      case "--brokerlist" :: value :: tail =>
        nextOption(map ++ Map('brokerlist -> value), tail)
      case "--verbose" :: value :: tail =>
        nextOption(map ++ Map('verbose -> value), tail)
      case option :: tail => {
        println("Unknown option " + option)
        sys.exit(1)
      }
    }
  }

  /*
  * Print stats
   */
  private def statsPrintFn: Unit = {
    val CurTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date(System.currentTimeMillis))
    println("Stats @" + CurTime + "\n\t" + ProducerSimpleStats.getDispString("\n\t") + "\n")
  }

  /*
  * Just a local debug method
   */
  private def printline(inString: String, isVerbose: Boolean): Unit = {
    if (!isVerbose) return
    println(inString)
  }

  /**
   * Entry point for this tool
   * @param args - Array[Strings]
   */
  def main(args: Array[String]): Unit = {

    val options = nextOption(Map(), args.toList)
    val sFilesNames = options.getOrElse('files, null).asInstanceOf[String]
    if (sFilesNames == null) {
      println("Need input files as parameter")
      sys.exit(1)
    }

    val sAllFls = sFilesNames.replace("\"", "").trim.split(",")
    val sAllTrimFls = sAllFls.map(flnm => flnm.trim)
    val sAllValidTrimFls = sAllTrimFls.filter(flnm => flnm.size > 0)

    val sleeptmstr = options.getOrElse('sleep, "0").toString

    val msg = options.getOrElse('msg, "").toString.replace("\"", "").trim

    val tmptopics = options.getOrElse('topics, "").toString.replace("\"", "").trim.toLowerCase.split(",").map(t => t.trim).filter(t => t.size > 0)

    if (tmptopics.size == 0) {
      println("Need queue(s)")
      sys.exit(1)
    }

    val topics = tmptopics.toList.sorted.toArray // Sort topics by names    

    val brokerlist = options.getOrElse('brokerlist, "").toString.replace("\"", "").trim // .toLowerCase

    if (brokerlist.size == 0) {
      println("Need Brokers list (brokerlist) in the format of HOST:PORT,HOST:PORT")
      sys.exit(1)
    }

    val sleeptm = sleeptmstr.toInt

    val gz = options.getOrElse('gz, "false").toString

    val threads = options.getOrElse('threads, "0").toString.toInt

    if (threads <= 0) {
      println("Threads must be more than 0")
      sys.exit(1)
    }

    val ignorelines = options.getOrElse('ignorelines, "0").toString.toInt

    val topicpartitions = options.getOrElse('topicpartitions, "0").toString.replace("\"", "").toInt

    if (topicpartitions <= 0) {
      println("We should have +ve topicpartitions")
      sys.exit(1)
    }

    val format = options.getOrElse('format,"").toString

    val validJsonMap = scala.collection.mutable.Map[String,List[String]]()
   // var partitionkeyidxsTemp: Array[Int] = null
    var partitionkeyidxs: Any = null

    // If this is a JSON path, then the partitionkeyidx is in the format Array["Type:key1~key2~...."]
    if (format.equalsIgnoreCase("json")) {
      val msgTypeKeys = options.getOrElse('partitionkeyidxs, "").toString.replace("\"", "").trim.split(",").filter(part => part.size > 0)

      msgTypeKeys.foreach(msgType => {
        val keyStructure = msgType.split(":")
        var keyList: List[String] = List()
        keyStructure(1).split("~").foreach(key => {
          keyList = List(key) ::: keyList
        })
        validJsonMap(keyStructure(0)) = keyList
      })
      partitionkeyidxs = validJsonMap
    } else {
      // This is the CSV path, partitionkeyidx is in the Array[Int] format
      // If this is old path... keep as before for now....
      partitionkeyidxs = options.getOrElse('partitionkeyidxs, "").toString.replace("\"", "").trim.split(",").map(part => part.trim).filter(part => part.size > 0).map(part => part.toInt)
    }

    val isVerbose = options.getOrElse('verbose, "false").toString

    val props = new Properties()
    props.put("compression.codec", codec.toString)
    props.put("producer.type", if (synchronously) "sync" else "async")
    props.put("metadata.broker.list", brokerlist)
    props.put("batch.num.messages", batchSize.toString)
    props.put("batch.size", batchSize.toString)
    props.put("queue.time", queueTime.toString)
    props.put("queue.size", queueSize.toString)
    props.put("message.send.max.retries", messageSendMaxRetries.toString)
    props.put("request.required.acks", requestRequiredAcks.toString)
    props.put("buffer.memory", bufferMemory.toString)
    props.put("buffer.size", bufferMemory.toString)
    props.put("socket.send.buffer", bufferMemory.toString)
    props.put("socket.receive.buffer", bufferMemory.toString)
    props.put("client.id", clientId)
    props.put("partitioner.class", "CustPartitioner");

    val s = System.nanoTime

    if (sAllValidTrimFls.size > 0) {
      var idx = 0

      val flsLists = if (sAllValidTrimFls.size > threads) threads else sAllValidTrimFls.size

      val executor = Executors.newFixedThreadPool(flsLists)
      val FilesForThreads = new Array[ArrayBuffer[String]](flsLists)
      sAllValidTrimFls.foreach(fl => {
        val index = idx % flsLists
        if (FilesForThreads(index) == null)
          FilesForThreads(index) = new ArrayBuffer[String];
        FilesForThreads(index) += fl
        idx = idx + 1
      })

      val statusPrint = new Runnable {
        def run() {
          statsPrintFn
        }
      }

      idx = 0
      FilesForThreads.foreach(fls => {
        if (fls.size > 0) {
          executor.execute(new Runnable() {
            val threadNo = idx
            val flNames = fls.toArray
            var isGzip: Boolean = false
            override def run() {
              val producer = new Producer[AnyRef, AnyRef](new ProducerConfig(props))
              var tm: Long = 0
              val st: Stats = new Stats
              flNames.foreach(fl => {
                if (gz.trim.compareToIgnoreCase("true") == 0) {
                  isGzip = true
                }
                tm = tm + elapsed(ProcessFile(producer, topics, threadNo, fl, msg, sleeptm, partitionkeyidxs, st, ignorelines, format, isGzip, topicpartitions, isVerbose.equalsIgnoreCase("true")))
                println("%02d. File:%s ElapsedTime:%.02fms".format(threadNo, fl, tm / 1000000.0))
              })
              producer.close
            }
          })
        }
        idx = idx + 1
      })
      executor.shutdown();
      try {
        executor.awaitTermination(Long.MaxValue, TimeUnit.NANOSECONDS);
      } catch {
        case e: Exception => {val stackTrace = StackTrace.ThrowableTraceString(e)
          logger.debug("StackTrace:"+stackTrace)}
      }
    }

    // statsPrintFn
    println("Done. ElapsedTime:%.02fms".format((System.nanoTime - s) / 1000000.0))
    sys.exit(0)

  }
}

