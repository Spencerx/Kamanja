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
import com.ligadata.AuditAdapterInfo.AuditConstants
import scala.util.control._
import org.apache.logging.log4j._

object UpdateSourceModelService {
  case class UpdateJava(sourceCode:String)
  case class UpdateScala(sourceCode:String)
}


class UpdateSourceModelService(requestContext: RequestContext, userid:Option[String], password:Option[String], cert:Option[String], modelname: Option[String]) extends Actor {

  import UpdateSourceModelService._

  implicit val system = context.system
  import system.dispatcher
  val log = Logging(system, getClass)
  val APIName = "UpdateSourceModelService"

  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)

  def receive = {
    case UpdateJava(sourceCode) => {
      log.debug("Updating java model")
      updateJava(sourceCode)
      context.stop(self)
    }
    case UpdateScala(sourceCode) => {
      log.debug("Updating scala model")
      updateScala(sourceCode)
   context.stop(self)
    }
  }

  def updateScala(pmmlStr:String) = {
    log.debug("Requesting UpdateSourceModel {}",pmmlStr)
    val usersModelName=userid.getOrElse("")+"."+modelname.getOrElse("")
    logger.debug("user model name is: "+usersModelName)

    if (!MetadataAPIImpl.checkAuth(userid,password,cert, MetadataAPIImpl.getPrivilegeName("update","model"))) {
    //  MetadataAPIImpl.logAuditRec(userid,Some(AuditConstants.WRITE),AuditConstants.UPDATEOBJECT,pmmlStr,AuditConstants.FAIL,"",nameVal)
      requestContext.complete(new ApiResult(ErrorCodeConstants.Failure, APIName, null, "Error:UPDATE not allowed for this user").toString )
    }else if((modelname.getOrElse(""))=="") {
      requestContext.complete(new ApiResult(ErrorCodeConstants.Failure, APIName, null,  "Failed to add model. No model configuration name supplied. Please specify in the header the model configuration name where the key is 'modelname' and the value is the name of the configuration.").toString )
    }
    else {

      val apiResult = MetadataAPIImpl.UpdateModel(pmmlStr,"scala",usersModelName,userid)
      requestContext.complete(apiResult)
    }
  }

  def updateJava(pmmlStr:String) = {

    log.debug("Requesting UpdateSourceModel {}",pmmlStr)
    val usersModelName=userid.getOrElse("")+"."+modelname.getOrElse("")
    logger.debug("(Put request) user model name is: "+usersModelName)

    if (!MetadataAPIImpl.checkAuth(userid,password,cert, MetadataAPIImpl.getPrivilegeName("update","model"))) {
      //  MetadataAPIImpl.logAuditRec(userid,Some(AuditConstants.WRITE),AuditConstants.UPDATEOBJECT,pmmlStr,AuditConstants.FAIL,"",nameVal)
      requestContext.complete(new ApiResult(ErrorCodeConstants.Failure, APIName, null, "Error:UPDATE not allowed for this user").toString )
    }else if((modelname.getOrElse(""))=="") {
      requestContext.complete(new ApiResult(ErrorCodeConstants.Failure, APIName, null,  "Failed to add model. No model configuration name supplied. Please specify in the header the model configuration name where the key is 'modelname' and the value is the name of the configuration.").toString )
    }
    else {
      val apiResult = MetadataAPIImpl.UpdateModel(pmmlStr,"java",usersModelName,userid)
      requestContext.complete(apiResult)
    }
  }
}
