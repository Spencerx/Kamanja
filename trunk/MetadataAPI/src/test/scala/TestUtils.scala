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

package com.ligadata.automation.unittests.api.setup

import java.io.{File, FileNotFoundException, IOException}
import java.net.ServerSocket

import scala.util.Random

/**
 * Created by wtarver on 4/23/15.
 */
object TestUtils {
  private val logger = org.apache.logging.log4j.LogManager.getLogger(this.getClass)
  private val random: Random = new Random()

  def constructTempDir(dirPrefix:String):File = {
    val file:File = new File(System.getProperty("java.io.tmpdir"), dirPrefix + random.nextInt(10000000))
    if(!file.mkdirs()){
      throw new RuntimeException("Could not create temp directory: " + file.getAbsolutePath)
    }
    file.deleteOnExit()
    return file
  }

  def getAvailablePort:Int = {
    try{
      val socket:ServerSocket = new ServerSocket(0)
      try {
        return socket.getLocalPort
      }
      finally {
        socket.close();
      }
    }
    catch {
      case e:IOException => throw new IllegalStateException("Cannot find available port: " + e.getMessage, e)
    }
  }

  def deleteFile(path:File):Unit = {
    if(path.exists()){
      if (path.isDirectory){
	for(f <- path.listFiles) {
          deleteFile(f)
	}
      }
      logger.debug("AUTOMATION-TESTUTILS: Deleting file '" + path + "'")
      path.delete()
    }
  }
}
