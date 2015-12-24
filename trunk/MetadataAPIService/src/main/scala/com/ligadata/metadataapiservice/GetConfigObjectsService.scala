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

import akka.actor.{Actor, ActorRef}
import akka.event.Logging
import akka.io.IO
import spray.routing.RequestContext
import spray.httpx.SprayJsonSupport
import spray.client.pipelining._
import scala.util.{ Success, Failure }
import com.ligadata.MetadataAPI._
import com.ligadata.kamanja.metadata._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import scala.util.control._
import org.apache.logging.log4j._
import com.ligadata.AuditAdapterInfo.AuditConstants

object GetConfigObjectsService {
  case class Process(formatType:String)
}

class GetConfigObjectsService(requestContext: RequestContext, userid:Option[String], password:Option[String], cert:Option[String]) extends Actor {

  import GetConfigObjectsService._
  
  implicit val system = context.system
  import system.dispatcher
  val log = Logging(system, getClass)

  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
 // logger.setLevel(Level.TRACE);

  val APIName = "GetConfigObjects"

  def GetConfigObjects(objectType:String): String = {
    var apiResult:String = ""

    objectType match {
      case "node" => {
        apiResult = MetadataAPIImpl.GetAllNodes("JSON",userid)
      }
      case "cluster" => {
        apiResult = MetadataAPIImpl.GetAllClusters("JSON",userid)
      }
      case "adapter" => {
        apiResult = MetadataAPIImpl.GetAllAdapters("JSON",userid)
      }
      case "clustercfg" => {
        apiResult = MetadataAPIImpl.GetAllClusterCfgs("JSON",userid)
      }
      case "all" => {
        apiResult = MetadataAPIImpl.GetAllCfgObjects("JSON",userid)
      }
      case _ => {
        apiResult = "The " + objectType + " is not supported yet "
      }
    }
    apiResult
  }

  
  def receive = {
    case Process(objectType) =>
      process(objectType)
      context.stop(self)
  }
  
  def process(objectType:String) = {
    log.debug("Requesting GetConfigObjects {}",objectType)
    
    if (!MetadataAPIImpl.checkAuth(userid,password,cert, MetadataAPIImpl.getPrivilegeName("get","config"))) {
      MetadataAPIImpl.logAuditRec(userid,Some(AuditConstants.READ),AuditConstants.GETCONFIG,AuditConstants.CONFIG,AuditConstants.FAIL,"",objectType)
      requestContext.complete(new ApiResult(ErrorCodeConstants.Failure,APIName, null, "Error: READ not allowed for this user").toString )
    } else {
      val apiResult = GetConfigObjects(objectType)
      requestContext.complete(apiResult)      
    }
  }
}


