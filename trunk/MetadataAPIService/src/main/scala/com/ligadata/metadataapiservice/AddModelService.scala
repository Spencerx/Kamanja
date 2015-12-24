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
import com.ligadata.kamanja.metadata._
import scala.util.{ Success, Failure }

import com.ligadata.MetadataAPI._

import scala.util.control._
import org.apache.logging.log4j._
import com.ligadata.AuditAdapterInfo.AuditConstants

object AddModelService {
  case class Process(pmmlStr:String)
}

class AddModelService(requestContext: RequestContext, userid:Option[String], password:Option[String], cert:Option[String]) extends Actor {

  import AddModelService._
  
  implicit val system = context.system
  import system.dispatcher
  val log = Logging(system, getClass)
  val APIName = "AddModelService"
  
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
  //logger.setLevel(Level.TRACE);

  def receive = {
    case Process(pmmlStr) =>
      process(pmmlStr)
      context.stop(self)
  }
  
  def process(pmmlStr:String) = {
    
    logger.debug("Requesting AddModel: " + pmmlStr.substring(0,500))

    var nameVal = APIService.extractNameFromPMML(pmmlStr) 
    
    if (!MetadataAPIImpl.checkAuth(userid,password,cert, MetadataAPIImpl.getPrivilegeName("insert","model"))) {
	    MetadataAPIImpl.logAuditRec(userid,Some(AuditConstants.WRITE),AuditConstants.INSERTOBJECT,pmmlStr,AuditConstants.FAIL,"",nameVal)    
	    requestContext.complete(new ApiResult(ErrorCodeConstants.Failure, APIName, null,  "Error:UPDATE not allowed for this user").toString )
    } else {
      val apiResult = MetadataAPIImpl.AddModel(pmmlStr,userid) 
      requestContext.complete(apiResult)      
    }
  }
}
