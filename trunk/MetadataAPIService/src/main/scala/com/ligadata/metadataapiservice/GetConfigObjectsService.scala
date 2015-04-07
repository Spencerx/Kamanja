package com.ligadata.metadataapiservice

import akka.actor.{Actor, ActorRef}
import akka.event.Logging
import akka.io.IO
import spray.routing.RequestContext
import spray.httpx.SprayJsonSupport
import spray.client.pipelining._
import scala.util.{ Success, Failure }
import com.ligadata.MetadataAPI._
import com.ligadata.olep.metadata._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import scala.util.control._
import org.apache.log4j._

object GetConfigObjectsService {
  case class Process(formatType:String)
}

class GetConfigObjectsService(requestContext: RequestContext, userid:Option[String], password:Option[String], cert:Option[String]) extends Actor {

  import GetConfigObjectsService._
  
  implicit val system = context.system
  import system.dispatcher
  val log = Logging(system, getClass)

  val loggerName = this.getClass.getName
  val logger = Logger.getLogger(loggerName)
 // logger.setLevel(Level.TRACE);

  val APIName = "GetConfigObjects"

  def GetConfigObjects(objectType:String): String = {
    var apiResult:String = ""
    var apiResultStr:String = ""

    objectType match {
      case "node" => {
        apiResult = MetadataAPIImpl.GetAllNodes("JSON")
      }
      case "cluster" => {
        apiResult = MetadataAPIImpl.GetAllClusters("JSON")
      }
      case "adapter" => {
        apiResult = MetadataAPIImpl.GetAllAdapters("JSON")
      }
      case "clustercfg" => {
        apiResult = MetadataAPIImpl.GetAllClusterCfgs("JSON")
      }
      case "all" => {
        apiResult = MetadataAPIImpl.GetAllCfgObjects("JSON")
      }
      case _ => {
        apiResult = "The " + objectType + " is not supported yet "
      }
    }
    apiResultStr = MetadataAPIImpl.getApiResult(apiResult)
    MetadataAPIImpl.logAuditRec(userid,Some("get"),"GetConfigObjects",objectType,"Finished","unknown",apiResultStr)
    apiResultStr
  }

  
  def receive = {
    case Process(objectType) =>
      process(objectType)
      context.stop(self)
  }
  
  def process(objectType:String) = {
    log.debug("Requesting GetConfigObjects {}",objectType)
    
    if (!MetadataAPIImpl.checkAuth(userid,password,cert, MetadataAPIImpl.getPrivilegeName("get","config"))) {
      MetadataAPIImpl.logAuditRec(userid,Some(AuditConstants.READ),AuditConstants.GETCONFIG,AuditConstants.CONFIG,AuditConstants.FAIL,"",objectType.substring(0,20))
      requestContext.complete(new ApiResult(-1,APIName, null, "Error: READ not allowed for this user").toString )
    } else {
      val apiResult = GetConfigObjects(objectType)
      MetadataAPIImpl.logAuditRec(userid,Some(AuditConstants.READ),AuditConstants.GETCONFIG,AuditConstants.CONFIG,AuditConstants.SUCCESS,"",objectType.substring(0,20))
      requestContext.complete(apiResult)      
    }
  }
}


