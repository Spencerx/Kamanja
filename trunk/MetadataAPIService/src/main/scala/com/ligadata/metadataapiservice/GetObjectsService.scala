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
import com.ligadata.AuditAdapterInfo.AuditConstants

object GetObjectsService {
	case class Process(apiArgListJson: String)
}

class GetObjectsService(requestContext: RequestContext, userid:Option[String], password:Option[String], cert:Option[String]) extends Actor {

  import GetObjectsService._
  
  implicit val system = context.system
  import system.dispatcher
  val log = Logging(system, getClass)
  
  
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
 // logger.setLevel(Level.TRACE);

  val APIName = "GetObjects"

  def receive = {
    case Process(apiArgListJson: String) =>
      process(apiArgListJson)
      context.stop(self)
  }

  def GetObjectDef(arg: MetadataApiArg): String = {
    var resultStr:String = ""
    var nameSpace = "system"
    var version = "-1"
    var name = ""
    var formatType = "JSON"
    var apiResult:String = ""

    // Extract the object name from the args var
    if( arg.NameSpace != null ){
      nameSpace = arg.NameSpace
    }
    if( arg.Version != null ){
      version = arg.Version
    }
    if (arg.Name != null) {
      name = arg.Name
    }
    if( arg.FormatType != null ){
      formatType = arg.FormatType
    }
    
    val objectName = (nameSpace + arg.Name + version).toLowerCase
    if (!MetadataAPIImpl.checkAuth(userid,password,cert, MetadataAPIImpl.getPrivilegeName("get",arg.ObjectType))) {
	      MetadataAPIImpl.logAuditRec(userid,Some(AuditConstants.READ),AuditConstants.GETOBJECT,arg.ObjectType,AuditConstants.FAIL,"",nameSpace+"."+name+"."+version)
	      return new ApiResult(ErrorCodeConstants.Failure, APIName, null, "Error:READ not allowed for this user").toString
    }
    arg.ObjectType match {
      case "model" => {
	      apiResult = MetadataAPIImpl.GetModelDef(nameSpace,arg.Name,formatType,version,userid)
      }
      case "message" => {
	      apiResult = MetadataAPIImpl.GetMessageDef(nameSpace,arg.Name,formatType,version,userid)
      }
      case "container" => {
	      apiResult = MetadataAPIImpl.GetContainerDef(nameSpace,arg.Name,formatType,version,userid)
      }
      case "function" => {
	      apiResult = MetadataAPIImpl.GetFunctionDef(nameSpace,arg.Name,formatType,version,userid)
      }
      case "concept" => {
	      apiResult = MetadataAPIImpl.GetConceptDef(nameSpace,arg.Name,formatType,version,userid)
      }
      case "type" => {
	      apiResult = MetadataAPIImpl.GetTypeDef(nameSpace,arg.Name,formatType,version,userid)
      }
      case "outputmsg" => {
        
	      apiResult = MetadataAPIOutputMsg.GetOutputMessageDef(nameSpace,arg.Name,formatType,version, userid)
      }
    }
    apiResult
  }

  def process(apiArgListJson: String) = {
    
    logger.debug(APIName + ":" + apiArgListJson)

    val apiArgList = JsonSerializer.parseApiArgList(apiArgListJson)
    val arguments = apiArgList.ArgList
    var resultStr:String = ""

    if ( arguments.length > 0 ){
      var loop = new Breaks
      loop.breakable {
	      arguments.foreach(arg => {
	        if (arg.ObjectType == null ) {
	          resultStr = APIName + ":Error: The value of object type can't be null"
	          loop.break
	        } 
          
          if (arg.Name == null ) {
	          resultStr = APIName + ":Error: The value of object name can't be null"
	          loop.break
	        } else {
	          resultStr = resultStr + GetObjectDef(arg)
	        }
	      })
      }
    } else {
      resultStr = APIName + ":No arguments passed to the API, nothing much to do"
    }
    
    logger.debug("resultStr => " + resultStr)
    requestContext.complete(resultStr)
  }
}
