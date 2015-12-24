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
import org.apache.logging.log4j.{ Logger, LogManager }
import java.io.{ InputStream, FileInputStream }
import java.util.zip.GZIPInputStream
import java.nio.file.{ Paths, Files }
import com.ligadata.InputOutputAdapterInfo.{ AdapterConfiguration, InputAdapter, InputAdapterObj, OutputAdapter, ExecContext, ExecContextObj, CountersAdapter, PartitionUniqueRecordKey, PartitionUniqueRecordValue, StartProcPartInfo, InputAdapterCallerContext }
import com.ligadata.AdaptersConfiguration.{ FileAdapterConfiguration, FilePartitionUniqueRecordKey, FilePartitionUniqueRecordValue }
import scala.util.control.Breaks._
import com.ligadata.Exceptions.StackTrace
import com.ligadata.KamanjaBase.DataDelimiters

object FileConsumer extends InputAdapterObj {
  def CreateInputAdapter(inputConfig: AdapterConfiguration, callerCtxt: InputAdapterCallerContext, execCtxtObj: ExecContextObj, cntrAdapter: CountersAdapter): InputAdapter = new FileConsumer(inputConfig, callerCtxt, execCtxtObj, cntrAdapter)
}

class FileConsumer(val inputConfig: AdapterConfiguration, val callerCtxt: InputAdapterCallerContext, val execCtxtObj: ExecContextObj, cntrAdapter: CountersAdapter) extends InputAdapter {
  private[this] val LOG = LogManager.getLogger(getClass);

  private[this] val fc = FileAdapterConfiguration.GetAdapterConfig(inputConfig)
  private[this] var uniqueKey: FilePartitionUniqueRecordKey = new FilePartitionUniqueRecordKey
  private[this] val lock = new Object()

  uniqueKey.Name = "File"

  var executor: ExecutorService = _

  // LOG.debug("FileConsumer")

  //BUGBUG:: Not validating the values in FileAdapterConfiguration 

  val input = this

  val execThread = execCtxtObj.CreateExecContext(input, uniqueKey, callerCtxt)

  class Stats {
    var totalLines: Long = 0;
    var totalSent: Long = 0
  }

  private def ProcessFile(sFileName: String, format: String, msg: String, st: Stats, ignorelines: Int, AddTS2MsgFlag: Boolean, isGz: Boolean): Unit = {
    var is: InputStream = null

    LOG.debug("FileConsumer Processing File:" + sFileName)

    try {
      if (isGz)
        is = new GZIPInputStream(new FileInputStream(sFileName))
      else
        is = new FileInputStream(sFileName)
    } catch {
      case e: Exception =>
        LOG.error("Failed to open FileConsumer for %s. Message:%s".format(sFileName, e.getMessage))
        throw e
        return
    }

    val delimiters = new DataDelimiters()
    delimiters.keyAndValueDelimiter = fc.keyAndValueDelimiter
    delimiters.fieldDelimiter = fc.fieldDelimiter
    delimiters.valueDelimiter = fc.valueDelimiter

    val uniqueVal = new FilePartitionUniqueRecordValue
    uniqueVal.FileFullPath = sFileName

    val trimMsg = if (msg != null) msg.trim else null
    var len = 0
    var readlen = 0
    var totalLen: Int = 0
    var locallinecntr: Int = 0
    val maxlen = 1024 * 1024
    val buffer = new Array[Byte](maxlen)
    var tm = System.nanoTime
    var ignoredlines = 0
    try {
      breakable {
        do {
          readlen = is.read(buffer, len, maxlen - 1 - len)
          if (readlen > 0) {
            totalLen += readlen
            len += readlen
            var startidx: Int = 0
            var isrn: Boolean = false
            for (idx <- 0 until len) {
              if ((isrn == false && buffer(idx) == '\n') || (buffer(idx) == '\r' && idx + 1 < len && buffer(idx + 1) == '\n')) {
                locallinecntr += 1
                var strlen = idx - startidx
                if (ignoredlines < ignorelines) {
                  ignoredlines += 1
                } else {
                  if (strlen > 0) {
                    var readTmNs = System.nanoTime
                    var readTmMs = System.currentTimeMillis

                    val ln = new String(buffer, startidx, idx - startidx)
                    val sendmsg = (if (trimMsg != null && trimMsg.isEmpty() == false) (trimMsg + ",") else "") + (if (AddTS2MsgFlag) (readTmMs.toString + ",") else "") + ln

                    try {
                      // Creating new string to convert from Byte Array to string
                      uniqueVal.Offset = 0 //BUGBUG:: yet to fill this information
                      execThread.execute(sendmsg.getBytes, format, uniqueKey, uniqueVal, readTmNs, readTmMs, false, fc.associatedMsg, delimiters)
                    } catch {
                      case e: Exception => {
                        LOG.error("Failed with Message:" + e.getMessage)
                      }
                    }

                    st.totalSent += sendmsg.size
                  }
                }

                if (executor.isShutdown)
                  break

                startidx = idx + 1;
                if (buffer(idx) == '\r') // Inc one more char in case of \r \n
                {
                  startidx += 1;
                  isrn = true
                }
                st.totalLines += 1;

                val key = Category + "/" + fc.Name + "/evtCnt"
                cntrAdapter.addCntr(key, 1)

                val curTm = System.nanoTime
                if ((curTm - tm) > 1000000000L) {
                  tm = curTm
                  LOG.debug("Time:%10dms, Lns:%8d, Sent:%15d".format(curTm / 1000000, st.totalLines, st.totalSent))
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
          var readTmNs = System.nanoTime
          var readTmMs = System.currentTimeMillis

          val ln = new String(buffer, 0, len)
          val sendmsg = (if (trimMsg != null && trimMsg.isEmpty() == false) (trimMsg + ",") else "") + (if (AddTS2MsgFlag) (readTmMs.toString + ",") else "") + ln

          try {
            // Creating new string to convert from Byte Array to string
            uniqueVal.Offset = 0 //BUGBUG:: yet to fill this information
            execThread.execute(sendmsg.getBytes, format, uniqueKey, uniqueVal, readTmNs, readTmMs, false, fc.associatedMsg, delimiters)
          } catch {
            case e: Exception => {
              LOG.error("Failed with Message:" + e.getMessage)
            }
          }

          st.totalSent += sendmsg.size
          st.totalLines += 1;
          val key = Category + "/" + fc.Name + "/evtCnt"
          // cntrAdapter.addCntr(key, 1)
        }
      }
    } catch {
      case e: Exception => {
        LOG.error("Failed with Reason:%s Message:%s".format(e.getCause, e.getMessage))
      }
    }

    val curTm = System.nanoTime
    LOG.debug("Time:%10dms, Lns:%8d, Sent:%15d, Last, file:%s".format(curTm / 1000000, st.totalLines, st.totalSent, sFileName))
    is.close();
  }

  private def elapsedTm[A](f: => A): Long = {
    val s = System.nanoTime
    f
    (System.nanoTime - s)
  }

  override def Shutdown: Unit = lock.synchronized {
    StopProcessing
  }

  override def StopProcessing: Unit = lock.synchronized {
    if (executor == null) return

    executor.shutdown

    while (executor.isTerminated == false) {
      Thread.sleep(100) // sleep 100ms and then check
    }

    executor = null
  }

  // Each value in partitionInfo is (PartitionUniqueRecordKey, PartitionUniqueRecordValue, Long, PartitionUniqueRecordValue) key, processed value, Start transactionid, Ignore Output Till given Value (Which is written into Output Adapter)
  override def StartProcessing(partitionInfo: Array[StartProcPartInfo], ignoreFirstMsg: Boolean): Unit = lock.synchronized {
    if (partitionInfo == null || partitionInfo.size == 0)
      return

    // BUGBUG:: Not really handling partitionUniqueRecordKeys & partitionUniqueRecordValues

    /*
    val keys = partitionUniqueRecordKeys.map(k => {
      val key = new FilePartitionUniqueRecordKey
      key.Deserialize(k)
      key
    })
*/

    executor = Executors.newFixedThreadPool(1)
    executor.execute(new Runnable() {
      override def run() {

        // LOG.debug("FileConsumer.run")

        val s = System.nanoTime

        var tm: Long = 0
        val st: Stats = new Stats
        val compString = if (fc.CompressionString == null) null else fc.CompressionString.trim
        val isTxt = (compString == null || compString.size == 0)
        val isGz = (compString != null && compString.compareToIgnoreCase("gz") == 0)
        fc.Files.foreach(fl => {
          if (isTxt || isGz) {
            tm = tm + elapsedTm(ProcessFile(fl, fc.formatOrInputAdapterName, fc.MessagePrefix, st, fc.IgnoreLines, fc.AddTS2MsgFlag, isGz))
          } else {
            throw new Exception("Not yet handled other than text & GZ files")
          }
          if (executor.isShutdown) {
            LOG.debug("Stop processing File:%s in the middle ElapsedTime:%.02fms".format(fl, tm / 1000000.0))
            break
          } else {
            LOG.debug("File:%s ElapsedTime:%.02fms".format(fl, tm / 1000000.0))
          }
        })
        /*
      if (st.totalLines > 0) {
        val rem = (st.totalLines - (st.totalLines / 100) * 100)
        if (rem > 0) {
          val key = Category + "/" + fc.Name + "/evtCnt"
          cntrAdapter.addCntr(key, rem)

        }
      }
*/
        LOG.debug("Done. ElapsedTime:%.02fms".format((System.nanoTime - s) / 1000000.0))
      }
    });

  }

  override def GetAllPartitionUniqueRecordKey: Array[PartitionUniqueRecordKey] = lock.synchronized {
    if (uniqueKey != null) {
      return Array(uniqueKey)
    }
    null
  }

  override def DeserializeKey(k: String): PartitionUniqueRecordKey = {
    val key = new FilePartitionUniqueRecordKey
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
    val vl = new FilePartitionUniqueRecordValue
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

  // Not yet implemented
  override def getAllPartitionBeginValues: Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)] = {
    return Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)]()
  }

  // Not yet implemented
  override def getAllPartitionEndValues: Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)] = {
    return Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)]()
  }
}

