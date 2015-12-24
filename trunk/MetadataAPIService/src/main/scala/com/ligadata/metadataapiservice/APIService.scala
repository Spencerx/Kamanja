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

import akka.actor.{ActorSystem, Props}
import akka.actor.ActorDSL._
import akka.event.Logging
import akka.io.IO
import akka.io.Tcp._
import spray.can.Http
import org.json4s.jackson.JsonMethods._
import com.ligadata.kamanja.metadata.ObjType._
import com.ligadata.kamanja.metadata._
import com.ligadata.kamanja.metadataload.MetadataLoad
import com.ligadata.MetadataAPI.MetadataAPIImpl
import org.apache.logging.log4j._
import com.ligadata.Utils._
import scala.util.control.Breaks._
import com.ligadata.Exceptions._
import com.ligadata.Exceptions.StackTrace

class APIService extends LigadataSSLConfiguration with Runnable{

  private type OptionMap = Map[Symbol, Any]
  var inArgs: Array[String] = null

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("metadata-api-service")
  val log = Logging(system, getClass)

  val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)
  var databaseOpen = false
  
  /**
   * 
   */
  def this(args: Array[String]) = {
    this
    inArgs = args  
  }

  /**
   * 
   */
  def run() {
    StartService(inArgs) 
  }
  
  
  private def PrintUsage(): Unit = {
    logger.warn("    --config <configfilename>")
  }

  private def nextOption(map: OptionMap, list: List[String]): OptionMap = {
    def isSwitch(s: String) = (s(0) == '-')
    list match {
      case Nil => map
      case "--config" :: value :: tail =>
        nextOption(map ++ Map('config -> value), tail)
      case option :: tail => {
        logger.error("Unknown option " + option)
        sys.exit(1)
      }
    }
  }

  private def Shutdown(exitCode: Int): Unit = {
    APIInit.Shutdown(0)
    //System.exit(0)
  }

  private def StartService(args: Array[String]) : Unit = {
    try{
      var configFile = ""
      if (args.length == 0) {
        try {
          configFile = sys.env("KAMANJA_HOME") + "/config/MetadataAPIConfig.properties"
        } catch {
          case nsee: java.util.NoSuchElementException => {
            logger.warn("Either a CONFIG FILE parameter must be passed to start this service or KAMANJA_HOME must be set")
            return
          }
          case e: Exception => {
            e.printStackTrace()
            return
          }
        }

        logger.warn("Config File defaults to " + configFile)
        logger.warn("One Could optionally pass a config file as a command line argument:  --config myConfig.properties")
        logger.warn("The config file supplied is a complete path name of a  json file similar to one in github/Kamanja/trunk/MetadataAPI/src/main/resources/MetadataAPIConfig.properties")
      } else {
        val options = nextOption(Map(), args.toList)
        val cfgfile = options.getOrElse('config, null)
        if (cfgfile == null) {
          logger.error("Need configuration file as parameter")
          throw new MissingArgumentException("Usage: configFile  supplied as --config myConfig.properties")
        }
        configFile = cfgfile.asInstanceOf[String]
      }

      val (loadConfigs, failStr) = com.ligadata.Utils.Utils.loadConfiguration(configFile.toString, true)
      if (failStr != null && failStr.size > 0) {
        logger.error(failStr)
        Shutdown(1)
        return
      }
      
      if (loadConfigs == null) {
        Shutdown(1)
        return
      }

      APIInit.SetConfigFile(configFile.toString)

      // Read properties file and Open db connection
      MetadataAPIImpl.InitMdMgrFromBootStrap(configFile, true)
      // APIInit deals with shutdown activity and it needs to know
      // that database connections were successfully made
      APIInit.SetDbOpen

      logger.debug("API Properties => " + MetadataAPIImpl.GetMetadataAPIConfig)

      // We will allow access to this web service from all the servers on the PORT # defined in the config file 
      val serviceHost = "0.0.0.0"
      val servicePort = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("SERVICE_PORT").toInt

      // create and start our service actor
      val callbackActor = actor(new Act {
        become {
          case b @ Bound(connection) => logger.debug(b.toString)
          case cf @ CommandFailed(command) => logger.error(cf.toString)
          case all => logger.debug("ApiService Received a message from Akka.IO: " + all.toString)
        }
      })
      val service = system.actorOf(Props[MetadataAPIServiceActor], "metadata-api-service")

      // start a new HTTP server on a specified port with our service actor as the handler
      IO(Http).tell(Http.Bind(service, serviceHost, servicePort), callbackActor)

      logger.debug("MetadataAPIService started, listening on (%s,%s)".format(serviceHost,servicePort))

      sys.addShutdownHook({
        logger.debug("ShutdownHook called")
        Shutdown(0)
      })

      Thread.sleep(365*24*60*60*1000L)
    } catch {
      case e: InterruptedException => {
        logger.debug("Unexpected Interrupt")
      }
      case e: Exception => {
        val stackTrace =   StackTrace.ThrowableTraceString(e)
              logger.debug("Stacktrace:"+stackTrace)
      }
    } finally {
      Shutdown(0)
    }
  }
}
 
object APIService {

  def main(args: Array[String]): Unit = {
    val mgr = new APIService
    mgr.StartService(args) 
  }
  
  
  /**
   * extractNameFromJson - applies to a simple Kamanja object
   */
  def extractNameFromJson (jsonObj: String, objType: String): String = {
    var inParm: Map[String,Any] = null
    try {
      inParm = parse(jsonObj).values.asInstanceOf[Map[String,Any]] 
    } catch {
      case e: Exception => {
        return "Unknown:NameParsingError"
      }
    }
    var vals: Map[String,String] = inParm.getOrElse(objType,null).asInstanceOf[Map[String,String]]
    if (vals == null) {
      return "unknown "+ objType
    }
    return vals.getOrElse("NameSpace","system")+"."+vals.getOrElse("Name","")+"."+vals.getOrElse("Version","-1")
  }

  
  /**
   * extractNameFromPMML - pull the Application name="xxx" version="xxx.xx.xx" from the PMML doc and construct
   *                       a name  string from it
   */
  def extractNameFromPMML (pmmlObj: String): String = {
    var firstOccurence: String = "unknownModel"
    val pattern = """Application[ ]*name="([^ ]*)"[ ]*version="([^ ]*)"""".r
    val allMatches = pattern.findAllMatchIn(pmmlObj)
    allMatches.foreach( m => {
      if (firstOccurence.equalsIgnoreCase("unknownModel")) {
      firstOccurence = (m.group(1)+"."+m.group(2))
      }
    })
    return firstOccurence
  }
}
