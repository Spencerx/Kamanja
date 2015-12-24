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
import scala.collection.immutable.Map
import com.ligadata.kamanja.metadata._
import scala.util.{ Success, Failure }

import com.ligadata.MetadataAPI._

import scala.util.control._
import org.apache.logging.log4j._

object AddSourceModelService {
  case class ProcessJava(sourceCode:String)
  case class ProcessScala(sourceCode:String)
}

class AddSourceModelService(requestContext: RequestContext, userid:Option[String], password:Option[String], cert:Option[String], modelname: Option[String]) extends Actor {

  import AddSourceModelService._

  implicit val system = context.system
  import system.dispatcher
  val log = Logging(system, getClass)
  val APIName = "AddSourceModelService"

  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
  //logger.setLevel(Level.TRACE);

  def receive = {
    case ProcessJava(sourceCode) =>{
      processJava(sourceCode)
      context.stop(self)
    }
    case ProcessScala(sourceCode) =>{
      processScala(sourceCode)
      context.stop(self)
    }

  }

  def processJava(sourceCode:String) = {
    logger.debug("Requesting AddSourceModel JAVA.")
    val usersModelName=userid.getOrElse("")+"."+modelname.getOrElse("")
    logger.debug("user model name is: "+usersModelName)

    if (!MetadataAPIImpl.checkAuth(userid,password,cert, MetadataAPIImpl.getPrivilegeName("insert","model"))) {
	    requestContext.complete(new ApiResult(ErrorCodeConstants.Failure, APIName, null,  "Error:UPDATE not allowed for this user").toString )
    }else if((modelname.getOrElse(""))=="") {
      requestContext.complete(new ApiResult(ErrorCodeConstants.Failure, APIName, null,  "Failed to add model. No model configuration name supplied. Please specify in the header the model configuration name where the key is 'modelname' and the value is the name of the configuration.").toString )
    }
    else {
      val apiResult = MetadataAPIImpl.AddModelFromSource(sourceCode,"java",usersModelName,userid)
      requestContext.complete(apiResult)
    }
  }

  def processScala(sourceCode:String) = {

    logger.debug("Requesting AddSourceModel SCALA.")
    /*var modelName=""
    val regex=" \"([^\"]*)\"(.*$)".r

    val arr=sourceCode.split("\n")
    for(i <- arr){
      if(i.contains("ModelName")){
       modelName=(regex.findFirstIn(i).getOrElse("No Match").replace("\"","")).trim
      }
    }
    val usersModelName=userid.getOrElse("")+"."+modelName
    */
    val usersModelName=userid.getOrElse("")+"."+modelname.getOrElse("")
    if (!MetadataAPIImpl.checkAuth(userid,password,cert, MetadataAPIImpl.getPrivilegeName("insert","model"))) {
     // MetadataAPIImpl.logAuditRec(userid,Some(AuditConstants.WRITE),AuditConstants.INSERTOBJECT,sourceCode,AuditConstants.FAIL,"",nameVal)
      requestContext.complete(new ApiResult(ErrorCodeConstants.Failure, APIName, null,  "Error:UPDATE not allowed for this user").toString )
    }else if((modelname.getOrElse(""))=="") {
      requestContext.complete(new ApiResult(ErrorCodeConstants.Failure, APIName, null,"Failed to add model. No model configuration name supplied. Please specify in the header the model configuration name where the key is 'modelname' and the value is the name of the configuration." ).toString )
    }
    else {
      //def AddModelFromSource(sourceCode: String, sourceLang: String, modelName: String, userid: Option[String]): String = {
      val apiResult = MetadataAPIImpl.AddModelFromSource(sourceCode,"scala",usersModelName,userid)
      requestContext.complete(apiResult)
    }
  }
}
