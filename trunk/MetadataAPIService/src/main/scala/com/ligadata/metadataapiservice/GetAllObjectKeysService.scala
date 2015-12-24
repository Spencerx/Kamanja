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
import com.ligadata.Serialize._
import com.ligadata.kamanja.metadata._

import scala.util.control._
import org.apache.logging.log4j._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.ligadata.AuditAdapterInfo.AuditConstants

object GetAllObjectKeysService {
  case class Process(formatType:String)
}

class GetAllObjectKeysService(requestContext: RequestContext, userid:Option[String], password:Option[String], cert:Option[String]) extends Actor {

  import GetAllObjectKeysService._
  
  implicit val system = context.system
  import system.dispatcher
  val log = Logging(system, getClass)
  
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
//  logger.setLevel(Level.TRACE);

  val APIName = "GetAllObjectKeys"

  def receive = {
    case Process(objectType) =>
      process(objectType)
      context.stop(self)
  }

  def GetAllObjectKeys(objectType:String): String = {
    var apiResult:Array[String] = new Array[String](0)
    
    if (!MetadataAPIImpl.checkAuth(userid,password,cert, MetadataAPIImpl.getPrivilegeName("get","keys"))) {
	      MetadataAPIImpl.logAuditRec(userid,Some(AuditConstants.READ),AuditConstants.GETKEYS,objectType,AuditConstants.FAIL,"",objectType)
	      return new ApiResult(ErrorCodeConstants.Failure, APIName, null, "Error:READ not allowed for this user").toString   
    }

    objectType match {
      case "model" => {
	      apiResult = MetadataAPIImpl.GetAllModelsFromCache(false,userid)
      }
      case "message" => {
	      apiResult = MetadataAPIImpl.GetAllMessagesFromCache(true,userid)
      }
      case "container" => {
	      apiResult = MetadataAPIImpl.GetAllContainersFromCache(true,userid)
      }
      case "function" => {
	      apiResult = MetadataAPIImpl.GetAllFunctionsFromCache(true,userid)
      }
      case "concept" => {
	      apiResult = MetadataAPIImpl.GetAllConceptsFromCache(true,userid)
      }
      case "type" => {
	      apiResult = MetadataAPIImpl.GetAllTypesFromCache(true,userid)
      }
      case "outputmsg" => {
	      apiResult = MetadataAPIOutputMsg.GetAllOutputMsgsFromCache(true, userid)
      }
      case _ => {
         apiResult = Array[String]("The " + objectType + " is not supported yet ")
         return new ApiResult(ErrorCodeConstants.Failure, APIName, null,  "Invalid URL:" + apiResult.mkString).toString
      }
    }
    MetadataAPIImpl.logAuditRec(userid,Some(AuditConstants.READ),AuditConstants.GETKEYS,objectType,AuditConstants.SUCCESS,"",objectType)
    new ApiResult(ErrorCodeConstants.Success,  APIName, "Object Keys:" + apiResult.mkString(","), ErrorCodeConstants.Get_All_Object_Keys_Successful).toString
  }

  def process(objectType: String) = {
    logger.debug(APIName + ":" + objectType)
    val objectList = GetAllObjectKeys(objectType)
    logger.debug(APIName + "(results):" + objectList)
    requestContext.complete(objectList)
  }
}


