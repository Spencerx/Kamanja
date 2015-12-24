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

object DeactivateObjectsService {
	case class Process(apiArgListJson: String)
}

class DeactivateObjectsService(requestContext: RequestContext, userid:Option[String], password:Option[String], cert:Option[String]) extends Actor {

  import DeactivateObjectsService._
  
  implicit val system = context.system
  import system.dispatcher
  val log = Logging(system, getClass)
  
  
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
  //logger.setLevel(Level.TRACE);

  val APIName = "DeactivateObjects"

  def receive = {
    case Process(apiArgListJson: String) =>
      process(apiArgListJson)
      context.stop(self)
  }

  def DeactivateObjectDef(nameSpace: String, name: String, version: String, objectType: String ): String = {

    objectType match {
      case "model" => {
 	      MetadataAPIImpl.DeactivateModel(nameSpace,name,version.toLong,userid).toString
      }
      case _ => {
	      new ApiResult(ErrorCodeConstants.Failure, APIName, null, "Deactivate/Activate on " + objectType + " is not supported yet").toString 
      }
    }
  }

  /**
   * process - perform the deactivation process on the list of objects in the parameter var.
   */
  def process(apiArgListJson: String): Unit = {
    
    logger.debug(APIName + ":" + apiArgListJson)
    
    var activatedType = "unkown"
    var nameSpace = "str"
    var version = "-1"
    var name = ""
    var authDone = false
    val apiArgList = JsonSerializer.parseApiArgList(apiArgListJson)
    val arguments = apiArgList.ArgList
    var resultStr:String = ""
    var objectList: List[String]  = List[String]()

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
          if ((!MetadataAPIImpl.checkAuth(userid,password,cert, MetadataAPIImpl.getPrivilegeName("deactivate","model"))) && !authDone) {
            MetadataAPIImpl.logAuditRec(userid,Some(AuditConstants.WRITE),AuditConstants.DEACTIVATEOBJECT,arg.ObjectType,AuditConstants.FAIL,"unknown",nameSpace+"."+name+"."+version)
            requestContext.complete(new ApiResult(-1, APIName, null, "Error:UPDATE not allowed for this user").toString )
            return
          }
          
          // Make sure that we do not perform another AUTH check, and add this object name to the list of objects processed in this call.
          authDone = true
          objectList :::= List(nameSpace+"."+name+"."+version)
          
	        if(arg.ObjectType == null ){
	          resultStr = new ApiResult(ErrorCodeConstants.Failure, APIName, null, "Error: The value of object type can't be null").toString
	          loop.break
	        }
	        if(arg.Name == null ){
	          resultStr = new ApiResult(ErrorCodeConstants.Failure, APIName, null, "Error: The value of object name can't be null").toString
	          loop.break
	        }
	        else {
	         resultStr = resultStr + DeactivateObjectDef(nameSpace,name,version, arg.ObjectType)
           activatedType = arg.ObjectType
	        }
	      })
      }
    }
    else{
      resultStr = new ApiResult(ErrorCodeConstants.Failure, APIName, null, "No arguments passed to the API, nothing much to do").toString 
    }
    requestContext.complete(resultStr)
  }
}
