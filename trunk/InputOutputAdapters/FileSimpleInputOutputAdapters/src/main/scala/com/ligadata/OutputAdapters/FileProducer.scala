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

import org.apache.log4j.Logger
import java.io.{ OutputStream, FileOutputStream, File, BufferedWriter, Writer, PrintWriter }
import java.util.zip.GZIPOutputStream
import java.nio.file.{ Paths, Files }
import com.ligadata.InputOutputAdapterInfo.{ AdapterConfiguration, OutputAdapter, OutputAdapterObj, CountersAdapter }
import com.ligadata.AdaptersConfiguration.FileAdapterConfiguration
import com.ligadata.Exceptions.StackTrace

object FileProducer extends OutputAdapterObj {
  def CreateOutputAdapter(inputConfig: AdapterConfiguration, cntrAdapter: CountersAdapter): OutputAdapter = new FileProducer(inputConfig, cntrAdapter)
}

class FileProducer(val inputConfig: AdapterConfiguration, cntrAdapter: CountersAdapter) extends OutputAdapter {
  private[this] val _lock = new Object()

  private[this] val LOG = Logger.getLogger(getClass);

  private[this] val fc = FileAdapterConfiguration.GetAdapterConfig(inputConfig)

  //BUGBUG:: Not validating the values in FileAdapterConfiguration 

  //BUGBUG:: Open file to write the data

  // Taking only first file, if exists
  val sFileName = if (fc.Files.size > 0) fc.Files(0).trim else null

  if (sFileName == null || sFileName.size == 0)
    throw new Exception("First File Name should not be NULL or empty")

  var os: OutputStream = null

  val newLine = "\n".getBytes("UTF8")

  val compString = if (fc.CompressionString == null) null else fc.CompressionString.trim

  if (compString == null || compString.size == 0) {
    os = new FileOutputStream(sFileName, fc.append);
  } else if (compString.compareToIgnoreCase("gz") == 0) {
    os = new GZIPOutputStream(new FileOutputStream(sFileName, fc.append)) // fc.append make sense here??
  } else {
    throw new Exception("Not yet handled other than text & GZ files")
  }

  // Locking before we write into file
  // To send an array of messages. messages.size should be same as partKeys.size
  override def send(messages: Array[Array[Byte]], partKeys: Array[Array[Byte]]): Unit = _lock.synchronized {
    if (messages.size != partKeys.size) {
      LOG.error("Message and Partition Keys hould has same number of elements. Message has %d and Partition Keys has %d".format(messages.size, partKeys.size))
      return
    }
    if (messages.size == 0) return
    try {
      // Op is not atomic
      messages.foreach(message => {
        os.write(message);
        os.write(newLine)
      })
      val key = Category + "/" + fc.Name + "/evtCnt"
      cntrAdapter.addCntr(key, messages.size) // for now adding rows
    } catch {
      case e: Exception => {
        LOG.error("Failed to send :" + e.getMessage)
      }
    }
  }

  override def Shutdown(): Unit = _lock.synchronized {
    if (os != null)
      os.close
  }
}

