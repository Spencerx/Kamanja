package com.ligadata.utilityservice

import akka.actor._
import akka.actor.ActorDSL._
import akka.event.Logging
import akka.io.IO
import akka.io.Tcp._
import spray.can.Http
import spray.routing._
import spray.http._


import org.apache.logging.log4j._
import scala.util.control.Breaks._
import com.ligadata.Exceptions._
import com.ligadata.Exceptions.StackTrace

/**
 * @author danielkozin
 */
object UtilityService {
  
  def main(args: Array[String]): Unit = {
    val mgr = new UtilityService(args)
    mgr.StartService(args) 
  }
    
}

class UtilityService extends Runnable {
  val PORT = "--port"
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("utility-service")
  val log = Logging(system, getClass)
  private type OptionMap = Map[Symbol, Any]
  
  val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)
  
  var inArgs: Array[String] = null
  def this(args: Array[String]) = {
    this
    inArgs = args  
  }
  
  def run() {
    StartService(inArgs) 
  }
  
  /**
   * Initialize the utility service... and bind it to the right port.
   */
  private def StartService(args: Array[String]) : Unit = {
   
    var configFile: String = ""
    
    // We will allow access to this web service from all the servers on the PORT # defined in the config file 
    val serviceHost = "0.0.0.0"    
    var servicePort = -1
    
    if (args.length == 0) {
      logger.error("Port# must be provided")
      sys.exit(1)
    } else {
      if (args.length != 2) {
        logger.error("Invalid Parameters")
        sys.exit(1)
      }
      if (args(0).toLowerCase.trim.equalsIgnoreCase(PORT))
        servicePort = args(1).trim.toInt
      else {
        logger.error("Unknown Paramter " + args(0))
        sys.exit(1)
      }
    }
    
    if (servicePort <= 0) {
      logger.error("Invalid port number, exiting ... ")
      sys.exit(1)     
    }
    
    // create and start our service actor
    val callbackActor = actor(new Act {
      become {
        case b @ Bound(connection) => logger.debug(b.toString)
        case cf @ CommandFailed(command) => logger.error(cf.toString)
        case all => logger.debug("ApiService Received a message from Akka.IO: " + all.toString)
      }
    })
    val service = system.actorOf(Props[UtilityServiceActor], "metadata-api-service")
   
    // start a new HTTP server on a specified port with our service actor as the handler
    IO(Http).tell(Http.Bind(service, serviceHost, servicePort), callbackActor)
  }
  
  
  /**
   * parse the inputfile.
   */
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
    
}




/**
 * Actor
 */
class UtilityServiceActor extends Actor with UtilityServiceTrait {
  def actorRefFactory = context
  def receive = runRoute(utilRoute)
}

/**
 * HttpService - very simple for now.
 */
trait UtilityServiceTrait extends HttpService {
   val KAFKA = "kafka"
   val utilRoute = {
      get {
        requestContext => requestContext.complete("ERROR: Unknown GET path \n")
      } ~
      post {
        entity(as[String]) 
        { reqBody =>
          // ..../util/kafka/topic
          path("kamanjaUtilService" / Rest) { str => {
            val postCall = str.split("/")
            if (postCall(0).toLowerCase.trim.equalsIgnoreCase(KAFKA)) {
              requestContext => requestContext.complete(KafkaCmdImpl.executeCommand(postCall(1),  postCall(2), reqBody) + "\n")   
            } else {
              requestContext => requestContext.complete("ERROR: Unknown POST path")
            }
          }}
        }
      } ~ 
      put {
        requestContext => requestContext.complete("ERROR: Unknown PUT path  \n")      
      } ~
      delete {
        requestContext => requestContext.complete("ERROR: Unknown DELETE path  \n")     
      }
   }

}

