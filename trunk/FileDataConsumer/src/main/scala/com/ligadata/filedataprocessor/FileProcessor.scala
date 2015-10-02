package com.ligadata.filedataprocessor

import java.util.zip.GZIPInputStream

import com.ligadata.Exceptions.{MissingPropertyException, StackTrace}
import com.ligadata.MetadataAPI.MetadataAPIImpl
import org.apache.curator.framework.CuratorFramework
import org.apache.log4j.Logger
import org.json4s.jackson.JsonMethods._

import scala.collection.mutable.HashMap
import scala.collection.JavaConverters._
import util.control.Breaks._
import java.io._
import java.nio.file._
import scala.actors.threadpool.{Executors, ExecutorService }
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.Files.copy
import java.nio.file.Paths.get

case class BufferLeftoversArea (workerNumber: Int, leftovers: Array[Char], relatedChunk: Int)
case class BufferToChunk(len: Int, payload: Array[Char], chunkNumber: Int, relatedFileName: String, firstValidOffset: Int)
case class KafkaMessage (msg: Array[Char], offsetInFile: Int, isLast: Boolean, relatedFileName: String)
case class EnqueuedFile (name: String, offset: Int)

/**
 *
 * @param path
 * @param partitionId
 */
class FileProcessor(val path:Path, val partitionId: Int) extends Runnable {

  private val watchService = path.getFileSystem().newWatchService()
  private val keys = new HashMap[WatchKey,Path]
  private var kml: KafkaMessageLoader = null
  private var zkc: CuratorFramework = null
  lazy val loggerName = this.getClass.getName
  lazy val logger = Logger.getLogger(loggerName)
  var fileConsumers: ExecutorService = Executors.newFixedThreadPool(3)

  var isCosuming = true
  var isProducing = true

  private var workerBees: ExecutorService = null

  // QUEUES used in file processing... will be synchronized.s
  private var fileQ: scala.collection.mutable.Queue[EnqueuedFile] = new scala.collection.mutable.Queue[EnqueuedFile]()
  private var msgQ: scala.collection.mutable.Queue[Array[KafkaMessage]] = scala.collection.mutable.Queue[Array[KafkaMessage]]()
  private var bufferQ: scala.collection.mutable.Queue[BufferToChunk] = scala.collection.mutable.Queue[BufferToChunk]()
  private var blg = new BufferLeftoversArea(-1, null, -1)
  private var fileOffsetTracker: scala.collection.mutable.Map[String,(Int,Boolean)] = scala.collection.mutable.Map[String,(Int,Boolean)]()
  private var bufferingQ_map: scala.collection.mutable.Map[String,Long] = scala.collection.mutable.Map[String,Long]()

  // Locks used for Q synchronization.
  private var fileQLock = new Object
  private var msgQLock = new Object
  private var bufferQLock = new Object
  private var beeLock = new Object
  private var trackerLock = new Object
  private var bufferingQLock = new Object

  private var msgCount = 0

  // Confugurable Properties
  private var dirToWatch: String = ""
  private var message_separator: Char = _
  private var field_separator: Char = _
  private var kv_separator: Char = _
  private var NUMBER_OF_BEES: Int = 2
  private var maxlen: Int = _
  private var partitionSelectionNumber: Int = _
  private var localMetadataConfig = ""
  private var kafkaTopic = ""


  /**
   * Called by the Directory Listener to initialize
   * @param props
   */
  def init(props: scala.collection.mutable.Map[String,String]): Unit = {
    message_separator = props(SmartFileAdapterConstants.MSG_SEPARATOR).toInt.toChar
    dirToWatch = props.getOrElse(SmartFileAdapterConstants.DIRECTORY_TO_WATCH,null)
    NUMBER_OF_BEES = props.getOrElse(SmartFileAdapterConstants.PAR_DEGREE_OF_FILE_CONSUMER, "1").toInt
    maxlen = props.getOrElse(SmartFileAdapterConstants.WORKER_BUFFER_SIZE, "4").toInt * 1024 * 1024
    partitionSelectionNumber = props(SmartFileAdapterConstants.NUMBER_OF_FILE_CONSUMERS).toInt

    kafkaTopic = props.getOrElse(SmartFileAdapterConstants.KAFKA_TOPIC, null)

    // Bail out if dirToWatch, Topic are not set
    if (kafkaTopic == null) {
      logger.error("SMART_FILE_CONSUMER Kafka Topic to populate must be specified")
      shutdown
      throw new MissingPropertyException ("Missing Paramter: " + SmartFileAdapterConstants.KAFKA_TOPIC )
    }

    if (dirToWatch == null) {
      logger.error("SMART_FILE_CONSUMER Directory to watch must be specified")
      shutdown
      throw new MissingPropertyException ("Missing Paramter: " + SmartFileAdapterConstants.DIRECTORY_TO_WATCH )
    }

    // Initialize threads
    localMetadataConfig = props(SmartFileAdapterConstants.METADATA_CONFIG_FILE)
    MetadataAPIImpl.InitMdMgrFromBootStrap(localMetadataConfig, false)
    try {
      kml = new KafkaMessageLoader (partitionId, props)
    } catch {
      case e: Exception => {
        shutdown
        throw e
      }
    }


    // will need to check zookeeper here
    val zkcConnectString = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("ZOOKEEPER_CONNECT_STRING")
    logger.debug("SMART_FILE_CONSUMER Using zookeeper " +zkcConnectString)
    val znodePath = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("ZNODE_PATH") + "/smartFileConsumer/" + partitionId
    zkc = kml.initZookeeper

  }

  private def enQBufferedFile(file: String): Unit = {
    bufferingQLock.synchronized {
      bufferingQ_map(file) = new File(file).length
    }
  }

  private def getKnownOffset(file: String): (Int, Boolean) = {
    trackerLock.synchronized {
      return fileOffsetTracker.getOrElse(file,(0,false))
    }
  }

  private def setOffsetForFile(file:String, offset: Int): Unit = {
    trackerLock.synchronized {
      fileOffsetTracker(file) = (offset,false)
    }
  }

  private def markFileAsFinished(file:String): Unit = {
    trackerLock.synchronized {
      var currentoffset = fileOffsetTracker(file)._1
      fileOffsetTracker(file) = (currentoffset, true)
    }
  }

  /**
   *
   * @param file
   * @param offset
   */
  private def enQFile(file: String, offset: Int): Unit = {
    fileQLock.synchronized {
      fileQ += new EnqueuedFile(file, offset)
    }
  }

  /**
   *
   * @return EnqueuedFile
   */
  private def deQFile: EnqueuedFile = {
    fileQLock.synchronized {
      if (fileQ.isEmpty) {
        return null
      }
      return fileQ.dequeue
    }
  }

  private def enQMsg(buffer: Array[KafkaMessage], bee: Int): Unit = {
    msgQLock.synchronized {
      msgQ += buffer
    }
  }

  private def deQMsg(): Array[KafkaMessage] = {
    msgQLock.synchronized {
      if (msgQ.isEmpty)  {
        return null
      }
      return msgQ.dequeue
    }
  }

  private def enQBuffer(buffer: BufferToChunk): Unit = {
    bufferQLock.synchronized {
        bufferQ += buffer
    }
  }

  private def deQBuffer(bee: Int): BufferToChunk = {
    msgQLock.synchronized {
      if (bufferQ.isEmpty) {
          return null
      }
      return bufferQ.dequeue
    }
  }

  private def getLeftovers(code: Int): BufferLeftoversArea = {
    beeLock.synchronized {
      return blg
    }
  }

  private def setLeftovers(in: BufferLeftoversArea, code: Int) = {
    beeLock.synchronized {
      blg = in
    }
  }




  /**
   * Register a particular file or directory to be watched
   */
  private def register(dir:Path): Unit = {
    val key = dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.OVERFLOW)
    keys(key) = dir
  }


  /**
   * Each worker bee will run this code... looking for work to do.
   * @param beeNumber
   */
   private def processBuffers(beeNumber: Int) = {

     var msgNum: Int = 0
     var myLeftovers: BufferLeftoversArea = null
     var buffer: BufferToChunk = null;

     while (isCosuming) {
       var messages: scala.collection.mutable.LinkedHashSet[KafkaMessage] = null
       var leftOvers: Array[Char] = new Array[Char](0)
       var fileNameToProcess: String = ""

       // Try to get a new file to process.
       buffer = deQBuffer(beeNumber)

       // If the buffer is there to process, do it
       if (buffer != null) {

         // If the new file being processed, reset offsets to messages in this file to 0.
         if (!fileNameToProcess.equalsIgnoreCase(buffer.relatedFileName)) {
           msgNum = 0
           fileNameToProcess = buffer.relatedFileName
         }
         // need a ordered structure to keep the messages.
         messages = scala.collection.mutable.LinkedHashSet[KafkaMessage]()
         var indx = 0
         var prevIndx = indx
         var temp: String = new String(buffer.payload)

         // Look for messages.
         buffer.payload.foreach(x => {
           if (x.asInstanceOf[Char] == message_separator) {
             var newMsg: Array[Char] = buffer.payload.slice(prevIndx, indx)

             msgNum += 1
             logger.debug("SMART_FILE_CONSUMER Message offset " + msgNum + ", and the buffer offset is " + buffer.firstValidOffset)
             if ( buffer.firstValidOffset < msgNum)
               messages.add(new KafkaMessage(newMsg, msgNum, false, buffer.relatedFileName))
             prevIndx = indx + 1
           }
           indx = indx + 1
         })

         // record the file offset for the last message to be able to tell.
         setOffsetForFile(buffer.relatedFileName, msgNum)

         // Wait for a previous worker be to finish so that we can get the leftovers.,, If we are the first buffer, then
         // just publish
         if ( buffer.chunkNumber == 0) {
           enQMsg(messages.toArray, beeNumber)
         }

         var foundRelatedLeftovers = false
         while(!foundRelatedLeftovers &&
                 buffer.chunkNumber != 0) {
           myLeftovers = getLeftovers(beeNumber)
           if (myLeftovers.relatedChunk == (buffer.chunkNumber - 1)) {
             leftOvers = myLeftovers.leftovers
             foundRelatedLeftovers = true

             // Prepend the leftovers to the first element of the array of messages
             val msgArray = messages.toArray
             val firstMsgWithLefovers = new KafkaMessage(leftOvers ++ msgArray(0).msg, msgArray(0).offsetInFile, false, buffer.relatedFileName)
             msgArray(0) = firstMsgWithLefovers
             enQMsg(msgArray, beeNumber)
           } else {
             Thread.sleep(100)
           }
         }

         // whatever is left is the leftover we need to pass to another thread.
         indx = scala.math.min(indx, buffer.len)
         if (indx != prevIndx) {
           var newFileLeftOvers = BufferLeftoversArea(beeNumber, buffer.payload.slice(prevIndx, indx), buffer.chunkNumber)
           setLeftovers(newFileLeftOvers, beeNumber)
         } else {
           var newFileLeftOvers = BufferLeftoversArea(beeNumber, new Array[Char](0),buffer.chunkNumber)
           setLeftovers(newFileLeftOvers, beeNumber)
         }

       } else {
         // Ok, we did not find a buffer to process on the BufferQ.. wait.
         Thread.sleep(100)
       }
     }
   }

  /**
   * This will be run under a CONSUMER THREAD.
   * @param file
   */
  private def readBytesChunksFromFile (file: EnqueuedFile) : Unit = {

    val buffer = new Array[Char](maxlen)
    var readlen = 0
    var len: Int = 0
    var totalLen = 0
    var chunkNumber = 0

    var fileName = file.name
    var offset = file.offset

    // Start the worker bees... should only be started the first time..
    if (workerBees == null) {
      workerBees = Executors.newFixedThreadPool(NUMBER_OF_BEES)
      for (i <- 1 to NUMBER_OF_BEES) {
        workerBees.execute(new Runnable() {
          override def run() = {
            processBuffers(i)
          }
        })
      }
    }

    // Grab the InputStream from the file and start processing it.  Enqueue the chunks onto the BufferQ for the
    // worker bees to pick them up.
    //var bis: InputStream = new ByteArrayInputStream(Files.readAllBytes(Paths.get(fileName)))
    var bis: BufferedReader = null
    if (isCompressed(fileName)) {
      bis = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(fileName))))
    } else {
      bis = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)))
    }

    // Intitialize the leftover area for this file reading.
    var newFileLeftOvers = BufferLeftoversArea(0, Array[Char](), -1)
    setLeftovers(newFileLeftOvers, 0)

    do {
      readlen = bis.read(buffer, 0, maxlen -1)
      if (readlen > 0) {
        totalLen += readlen
        len += readlen
        var BufferToChunk = new BufferToChunk(readlen, buffer.slice(0,maxlen), chunkNumber, fileName, offset)
        enQBuffer(BufferToChunk)
        chunkNumber += 1
      }
    } while (readlen > 0)

    // Pass the leftovers..  - some may have been left by the last chunkBuffer... nothing else will pick it up...
    // make it a KamfkaMessage buffer.
    var myLeftovers: BufferLeftoversArea = null
    var foundRelatedLeftovers = false
    while(!foundRelatedLeftovers) {
      myLeftovers = getLeftovers(1000)
      // if this is for the last chunk written...
      if (myLeftovers.relatedChunk == (chunkNumber - 1)) {
        // EnqMsg here.. but only if there is something in there.
        if (myLeftovers.leftovers.size > 0) {
          var messages: scala.collection.mutable.LinkedHashSet[KafkaMessage] = scala.collection.mutable.LinkedHashSet[KafkaMessage]()
          messages.add(new KafkaMessage(myLeftovers.leftovers, getKnownOffset(fileName)._1 + 1, true, fileName))
          enQMsg(messages.toArray, 1000)
        } else {
          var messages: scala.collection.mutable.LinkedHashSet[KafkaMessage] = scala.collection.mutable.LinkedHashSet[KafkaMessage]()
          messages.add(new KafkaMessage(null, -1, true, fileName))
          enQMsg(messages.toArray, 1000)
        }
        foundRelatedLeftovers = true
      } else {
        Thread.sleep(100)
      }
    }
    // Done with this file... mark is as closed
    markFileAsFinished(fileName)
  }

  /**
   *  This is the "FILE CONSUMER"
   */
  private def doSomeConsuming(): Unit = {
    while (isCosuming) {
      val fileToProcess = deQFile
      var curTimeStart: Long = 0
      var curTimeEnd: Long = 0
      if (fileToProcess == null) {
        Thread.sleep(500)
      } else {
        logger.info("SMART_FILE_CONSUMER partition "+partitionId + " Processing file "+fileToProcess)
        curTimeStart = System.currentTimeMillis
        readBytesChunksFromFile(fileToProcess)
        curTimeEnd = System.currentTimeMillis
      }
    }
  }

  /**
   * This is a "PUSHER" file.
   */
  private def doSomePushing(): Unit = {
    while (isProducing) {
      var msg = deQMsg
      if (msg == null) {
        Thread.sleep(250)
      }
      else {
        kml.pushData(msg)
      }
    }
  }


  /**
   *
   */
  private def monitorBufferingFiles: Unit = {
    while (isCosuming) {
      // Scan all the files that we are buffering, if there is not difference in their file size.. move them onto
      // the FileQ, they are ready to process.
      bufferingQLock.synchronized {
        var iter = bufferingQ_map.iterator
        iter.foreach(fileTuple => {
          try {
            val d = new File(fileTuple._1)

            // If the the new length of the file is the same as a second ago... this file is done, so move it
            // onto the ready to process q.  Else update the latest length
            if (fileTuple._2 == d.length) {
              logger.info("SMART_FILE_CONSUMER partition "+partitionId + "  File READY TO PROCESS " + d.toString)
              enQFile(fileTuple._1, 0)
              bufferingQ_map.remove(fileTuple._1)
            } else {
              bufferingQ_map(fileTuple._1) = d.length
            }
          } catch {
            case ioe: IOException => {
              logger.error ("SMART_FILE_CONSUMER Unable to find the directory to watch, Shutting down File Consumer " + partitionId)
              shutdown
              throw ioe
            }
          }


        })
      }
      // Give all the files a 1 second to add a few bytes to the contents
      Thread.sleep(1000)
    }
  }

  /**
   * The main directory watching thread
   */
  override def run(): Unit = {
    try {


      // Initialize and launch the File Processor thread(s), and kafka producers
      fileConsumers.execute(new Runnable() {
        override def run() = {
          doSomeConsuming
        }
      })

      fileConsumers.execute(new Runnable() {
        override def run() = {
          doSomePushing
        }
      })

      fileConsumers.execute(new Runnable() {
        override def run() = {
          monitorBufferingFiles
        }
      })

      // Register a listener on a watch directory.
      register(path)
      val d = new File(dirToWatch)

      // Lets see if we have failed previously on this partition Id, and need to replay some messages first.
      if (zkc.checkExists().forPath(MetadataAPIImpl.GetMetadataAPIConfig.getProperty("ZNODE_PATH") + "/smartFileConsumer/" + partitionId) != null ) {
        var priorFailures = zkc.getData.forPath(MetadataAPIImpl.GetMetadataAPIConfig.getProperty("ZNODE_PATH") + "/smartFileConsumer/" + partitionId)
        if (priorFailures != null) {
          var map = parse(new String(priorFailures)).values.asInstanceOf[Map[String, Any]]
          if (map != null) map.foreach(fileToReprocess => {
            enQFile(fileToReprocess._1.asInstanceOf[String], fileToReprocess._2.asInstanceOf[BigInt].intValue)
            if (d.exists && d.isDirectory) {
              var files = d.listFiles.filter(_.isFile)
              while (files.size != 0) {
                Thread.sleep(1000)
                files = d.listFiles.filter(_.isFile)
              }
            }
          })
        }

      }

      // Process all the existing files in the directory that are not marked complete.
      if (d.exists && d.isDirectory) {
        val files = d.listFiles.filter(_.isFile).sortWith(_.toString < _.toString).toList
        files.foreach(file => {
          var assignment =  scala.math.abs(file.toString.hashCode) % partitionSelectionNumber
          if ((assignment+ 1) == partitionId) {
            if (isValidFile(file.toString)) {
              enQBufferedFile(file.toString)
            }
          }
        })
      }
      logger.info("SMART_FILE_CONSUMER partition " +partitionId + " Initialization complete for partition " + partitionId)

      // Begin the listening process, TAKE()
      breakable {
        while (isCosuming) {
          val key = watchService.take()
          val dir = keys.getOrElse(key, null)
          if(dir != null) {
            key.pollEvents.asScala.foreach( event => {
              val kind = event.kind
              logger.info("SMART_FILE_CONSUMER partition " + partitionId + " *** Event: " + kind + " for "+ event.context().asInstanceOf[Path])
              // Only worry about new files.
              if(kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
                val event_path = event.context().asInstanceOf[Path]
                val fileName = dirToWatch + "/" + event_path.toString

                var assignment =  scala.math.abs(fileName.hashCode) % partitionSelectionNumber
                if ((assignment+ 1) == partitionId) {
                  if (isValidFile(fileName)) {
                    enQBufferedFile(fileName)
                  }
                }
              }
            })
          } else {
            logger.warn("SMART_FILE_CONSUMER partition "+partitionId + " WatchKey not recognized!!")
          }

          if (!key.reset()) {
            keys.remove(key)
            if (keys.isEmpty) {
              break
            }
          }
        }
      }
    } catch {
      case ie: InterruptedException => logger.error("InterruptedException: " + ie)
      case ioe: IOException => logger.error ("Unable to find the directory to watch, Shutting down File Consumer " + partitionId)
      case e: Exception => logger.error("Exception: " + e.printStackTrace())
    }
  }

  /**
   *
   * @param fileName
   * @return
   */
  private def isValidFile(fileName: String): Boolean = {
    if (!fileName.endsWith("_COMPLETE"))
      return true
    return false
  }

  /**
   *
   * @param inputfile
   * @return
   */
  private def isCompressed(inputfile: String): Boolean = {
    var is: FileInputStream = null
    try {
      is = new FileInputStream(inputfile)
    } catch {
      case e: Exception =>
        val stackTrace = StackTrace.ThrowableTraceString(e)
        e.printStackTrace()
        return false
    }

    val maxlen = 2
    val buffer = new Array[Byte](maxlen)
    val readlen = is.read(buffer, 0, maxlen)

    is.close() // Close before we really check and return the data

    if (readlen < 2)
      return false;

    val b0: Int = buffer(0)
    val b1: Int = buffer(1)

    val head = (b0 & 0xff) | ((b1 << 8) & 0xff00)

    return (head == GZIPInputStream.GZIP_MAGIC);
  }


  /**
   *
   */
  private def shutdown: Unit = {
    isCosuming = false
    isProducing = false
    if (fileConsumers != null) {
      fileConsumers.shutdown()
    }
    MetadataAPIImpl.shutdown
    if (zkc != null)
      zkc.close
    Thread.sleep(2000)
  }
}


