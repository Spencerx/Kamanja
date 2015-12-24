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

import scala.util.control._
import org.apache.logging.log4j._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._


object GetLeaderService {
  case class Process(nodeList:Array[String])
}

class GetLeaderService(requestContext: RequestContext, userid:Option[String], password:Option[String], cert:Option[String]) extends Actor {

  import GetLeaderService._
  
  implicit val system = context.system
  import system.dispatcher
  val log = Logging(system, getClass)
  
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)

  val APIName = "GetLeader"

  def receive = {
    case Process(nodeList) =>
      process(nodeList)
      context.stop(self)
  }

  def process(nodeList: Array[String]) = {
    val leaderHost = MetadataAPIImpl.getLeaderHost(MetadataAPIServiceLeader.LeaderNode)
    logger.debug("leader host => " + leaderHost)
    requestContext.complete(leaderHost)
  }
}


