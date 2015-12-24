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
import com.ligadata.AuditAdapterInfo.AuditConstants
import scala.util.control._

import org.apache.logging.log4j._

object ActivateObjectsService {
	case class Process(apiArgListJson: String)
}

class ActivateObjectsService(requestContext: RequestContext, userid:Option[String], password:Option[String], cert:Option[String]) extends Actor {

  import ActivateObjectsService._
  
  implicit val system = context.system
  import system.dispatcher
  val log = Logging(system, getClass)
  
  
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
 // logger.setLevel(Level.TRACE);

  val APIName = "ActivateObjects"

  def receive = {
    case Process(apiArgListJson: String) =>
      process(apiArgListJson)
      context.stop(self)
  }

  def ActivateObjectDef(nameSpace: String, name: String, version: String, objectType: String ): String = {
    var resultStr:String = ""
    var apiResult:String = ""

    objectType match {
      case "model" => {
	      MetadataAPIImpl.ActivateModel(nameSpace,name,version.toLong,userid).toString
      }
      case _ => {
	      new ApiResult(ErrorCodeConstants.Failure, APIName, null,  "Deactivate/Activate on " + objectType + " is not supported yet").toString 
      }
    }
  }

   /**
   * process - perform the activation process on the list of objects in the parameter var.
   */
  def process(apiArgListJson: String):Unit = {
    
    val apiArgList = JsonSerializer.parseApiArgList(apiArgListJson)
    val arguments = apiArgList.ArgList
    var nameSpace = "str"
    var version = "-1"
    var name = ""
    var authDone = false
    
    logger.debug(APIName + ":" + apiArgListJson)
    
    var resultStr:String = ""

    if ( arguments.length > 0 ){
      var loop = new Breaks
      loop.breakable{
	      arguments.foreach(arg => {
          
          // Extract the object name from the ARGS
          if( arg.NameSpace != null ){
            nameSpace = arg.NameSpace
          }
          if( arg.Version != null ){
            version = arg.Version
          }
          if( arg.Name != null ){
            name = arg.Name
          }
          
          // Do it here so that we know which OBJECT is being activated for the Audit purposes.
          if ((!MetadataAPIImpl.checkAuth(userid, password, cert, MetadataAPIImpl.getPrivilegeName("activate","model"))) && !authDone) {
            MetadataAPIImpl.logAuditRec(userid,Some(AuditConstants.WRITE),AuditConstants.ACTIVATEOBJECT,AuditConstants.MODEL,AuditConstants.FAIL,"",nameSpace+"."+name+"."+version)
            requestContext.complete(new ApiResult(ErrorCodeConstants.Failure, APIName, null,  "Error:UPDATE not allowed for this user").toString )
            return
          }
          
          // Make sure that we do not perform another AUTH check, and add this object name to the list of objects processed in this call.
          authDone = true
    
	        if(arg.ObjectType == null ){
	          resultStr =  new ApiResult(ErrorCodeConstants.Failure, APIName, null,"Error: The value of object type can't be null").toString
	          loop.break
	        }
	        if(arg.Name == null ){
	          resultStr = new ApiResult(ErrorCodeConstants.Failure, APIName, null, "Error: The value of object name can't be null").toString 
	          loop.break
	        }
	        else {
	          resultStr = resultStr + ActivateObjectDef(nameSpace,name,version, arg.ObjectType)
	        }
	      })
      }
    }
    else{
      resultStr = new ApiResult(ErrorCodeConstants.Failure, APIName, null,"No arguments passed to the API, nothing much to do").toString 
    }
    requestContext.complete(resultStr)
  }
}
