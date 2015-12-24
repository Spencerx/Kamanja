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

package com.ligadata.metadataapiservice

import akka.actor.{ActorSystem, Props}
import akka.actor.ActorDSL._
import akka.event.Logging
import akka.io.IO
import akka.io.Tcp._
import spray.can.Http

import com.ligadata.kamanja.metadata.ObjType._
import com.ligadata.kamanja.metadata._
import com.ligadata.kamanja.metadataload.MetadataLoad
import com.ligadata.MetadataAPI._
import org.apache.logging.log4j._
import com.ligadata.Utils._
import com.ligadata.Exceptions.StackTrace

object APIInit {
  val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)
  var databaseOpen = false
  var configFile:String = _
  private[this] val lock = new Object()

  def Shutdown(exitCode: Int): Unit = lock.synchronized{
    if( databaseOpen ){
      MetadataAPIImpl.CloseDbStore
      databaseOpen = false;
    }
    MetadataAPIServiceLeader.Shutdown
  }

  def SetConfigFile(cfgFile:String): Unit = {
    configFile = cfgFile
  }

  def SetDbOpen : Unit = {
    databaseOpen = true
  }

  def InitLeaderLatch: Unit = {
      // start Leader detection component
      val nodeId     = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("NODE_ID")
      val zkNode     = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("API_LEADER_SELECTION_ZK_NODE")  + "/metadataleader"
      val zkConnStr  = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("ZOOKEEPER_CONNECT_STRING")
      val sesTimeOut = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("ZK_SESSION_TIMEOUT_MS").toInt
      val conTimeOut = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("ZK_CONNECTION_TIMEOUT_MS").toInt

      MetadataAPIServiceLeader.Init(nodeId,zkConnStr,zkNode,sesTimeOut,conTimeOut)
  }

  def Init : Unit = {
    try{
      // Open db connection
      //MetadataAPIImpl.InitMdMgrFromBootStrap(configFile)
      //databaseOpen = true

      // start Leader detection component
      logger.debug("Initialize Leader Latch")
      InitLeaderLatch
    } catch {
      case e: Exception => {
	     val stackTrace =   StackTrace.ThrowableTraceString(e)
              logger.debug("Stacktrace:"+stackTrace)
      }
    }
  }
}
