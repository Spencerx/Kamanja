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

import akka.actor.{ Actor, ActorRef }
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

object RemoveObjectsService {
  case class Process(apiArgListJson: String)
}

class RemoveObjectsService(requestContext: RequestContext, userid: Option[String], password: Option[String], cert: Option[String]) extends Actor {

  import RemoveObjectsService._

  implicit val system = context.system
  import system.dispatcher
  val log = Logging(system, getClass)

  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
  //logger.setLevel(Level.TRACE);

  val APIName = "RemoveObjects"

  def receive = {
    case Process(apiArgListJson: String) =>
      process(apiArgListJson)
      context.stop(self)
  }

  def RemoveObjectDef(arg: MetadataApiArg): String = {
    var resultStr: String = ""
    var nameSpace = "str"
    var version = "-1"
    var formatType = "JSON"
    var apiResult: String = ""
    var objType = ""

    if (arg.NameSpace != null) {
      nameSpace = arg.NameSpace
    }
    if (arg.Version != null) {
      version = arg.Version
    }
    if (arg.FormatType != null) {
      formatType = arg.FormatType
    }
    if (arg.ObjectType != null) {
      objType = arg.ObjectType
    }

    val objectName = (nameSpace + "." + arg.Name + "." + version).toLowerCase
    if (!MetadataAPIImpl.checkAuth(userid, password, cert, MetadataAPIImpl.getPrivilegeName("delete", arg.ObjectType))) {
      MetadataAPIImpl.logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.DELETEOBJECT, objType, AuditConstants.FAIL, "", objectName)
      return new ApiResult(ErrorCodeConstants.Failure, APIName, null, "Error:UPDATE not allowed for this user").toString
    }

    arg.ObjectType match {
      case "model" => {
	      return MetadataAPIImpl.RemoveModel(nameSpace,arg.Name,version.toLong, userid)
      }
      case "message" => {
	      return MetadataAPIImpl.RemoveMessage(nameSpace,arg.Name,version.toLong, userid)
      }
      case "container" => {
	      return MetadataAPIImpl.RemoveContainer(nameSpace,arg.Name,version.toLong, userid)
      }
      case "function" => {
	      return MetadataAPIImpl.RemoveFunction(nameSpace,arg.Name,version.toLong, userid)
      }
      case "concept" => {
	      return MetadataAPIImpl.RemoveConcept(nameSpace,arg.Name,version.toLong, userid)
      }
      case "type" => {
	      return MetadataAPIImpl.RemoveType(nameSpace,arg.Name,version.toLong, userid)
      }
      case "outputmsg" => {
        return MetadataAPIOutputMsg.RemoveOutputMsg(nameSpace, arg.Name, version.toLong, userid)
      }
    }
    apiResult
  }

  def process(apiArgListJson: String) = {

    logger.debug(APIName + ":" + apiArgListJson)

    val apiArgList = JsonSerializer.parseApiArgList(apiArgListJson)
    val arguments = apiArgList.ArgList
    var resultStr: String = ""
    var finalRC: Int = 0
    var deletedObjects: Array[String] = new Array[String](0)
    var finalAPIResult = ""

    if (arguments.length > 0) {
      var loop = new Breaks
      loop.breakable {
        arguments.foreach(arg => {
          if (arg.ObjectType == null) {
            deletedObjects +:= ":Error: The value of object type can't be null"
            finalRC = -1
            finalAPIResult = (new ApiResult(ErrorCodeConstants.Failure, APIName, null, deletedObjects.mkString(","))).toString
            loop.break
          } else if (arg.Name == null) {
            deletedObjects +:= ":Error: The value of object name can't be null"
            finalRC = -1
            finalAPIResult = (new ApiResult(ErrorCodeConstants.Failure, APIName, null, deletedObjects.mkString(","))).toString
            loop.break
          } else {

            finalAPIResult = RemoveObjectDef(arg)
          }
        })
      }
    } else {
      finalAPIResult = (new ApiResult(ErrorCodeConstants.Failure, APIName, null, "Error:No arguments passed to the API, nothing much to do")).toString
    }
    requestContext.complete(finalAPIResult)
  }
}
