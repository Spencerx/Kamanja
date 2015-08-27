package scala.com.ligadata.MetadataAPI

import java.io.File
import java.util.logging.Logger

import com.ligadata.MetadataAPI.{TestMetadataAPI, MetadataAPIImpl}
import com.ligadata.MetadataAPI.Utility._
import scala.io.Source



/**
 * Created by dhaval Kolapkar on 7/24/15.
 */

object StartMetadataAPI {

  var response = ""
  //get default config
  val defaultConfig = sys.env("KAMANJA_HOME") + "/config/MetadataAPIConfig.properties"
  val loggerName = this.getClass.getName
  lazy val logger = Logger.getLogger(loggerName)
  var action = ""
  var location = ""
  var config = ""

  def main(args: Array[String]) {
    try {

      args.foreach( arg => {
        if (arg.endsWith(".json") || arg.endsWith(".xml") || arg.endsWith(".scala") || arg.endsWith(".java")) {
          location = arg
        } else if (arg.endsWith(".properties")) {
          config = arg
        } else {
          action += arg
        }
      })

      //add configuration
      if (config == "") {
        println("Using default configuration " + defaultConfig)
        config = defaultConfig
      }

      MetadataAPIImpl.InitMdMgrFromBootStrap(config, false)
      if (action == "")
        TestMetadataAPI.StartTest
      else {
        response = route(Action.withName(action.trim), location)
        println("Result: " + response)
      }
    }
    catch {
      case nosuchelement: NoSuchElementException => {
        println(action+ " is an unrecognized command. \n USAGE: kamanja <action> <optional input> \n e.g. kamanja add message $HOME/msg.json")
        //response = action+ " is an unrecognized command. \n USAGE: kamanja <action> <optional input> \n e.g. kamanja add message $HOME/msg.json"
      }
      case e: Throwable => e.getStackTrace.toString
    } finally {
      MetadataAPIImpl.shutdown
    }
  }


  def route(action: Action.Value, input: String): String = {
    var response = ""
    try {
      action match {
        //message management
        case Action.ADDMESSAGE => response = MessageService.addMessage(input)
        case Action.UPDATEMESSAGE => response = MessageService.updateMessage(input)
        case Action.REMOVEMESSAGE => response = MessageService.removeMessage
        case Action.GETALLMESSAGES => response = MessageService.getAllMessages
        //output message management
        case Action.ADDOUTPUTMESSAGE => response = MessageService.addOutputMessage(input)
        case Action.UPDATEOUTPUTMESSAGE => response =MessageService.updateOutputMessage(input)
        case Action.REMOVEOUTPUTMESSAGE => response =MessageService.removeOutputMessage
        case Action.GETALLOUTPUTMESSAGES => response =MessageService.getAllOutputMessages
        //model management
        case Action.ADDMODELPMMML => response = ModelService.addModelPmml(input)
        case Action.ADDMODELSCALA => response = ModelService.addModelScala(input)
        case Action.ADDMODELJAVA => response = ModelService.addModelJava(input)
        case Action.REMOVEMODEL => response = ModelService.removeModel(input)
        case Action.ACTIVATEMODEL => response = ModelService.activateModel
        case Action.DEACTIVATEMODEL => response = ModelService.deactivateModel
        case Action.UPDATEMODEL => response = ModelService.updateModel(input)
        case Action.GETALLMODELS => response = ModelService.getAllModels
        case Action.GETMODEL => response = ModelService.getModel
        //container management
        case Action.ADDCONTAINER => response = ContainerService.addContainer(input)
        case Action.UPDATECONTAINER => response = ContainerService.updateContainer(input)
        case Action.GETCONTAINER => response = ContainerService.getContainer
        case Action.GETALLCONTAINERS => response = ContainerService.getAllContainers
        case Action.REMOVECONTAINER => response = ContainerService.removeContainer
        //Type management
        case Action.ADDTYPE => response = TypeService.addType(input)
        case Action.GETTYPE => response = TypeService.getType
        case Action.GETALLTYPES => response = TypeService.getAllTypes
        case Action.REMOVETYPE => response = TypeService.removeType
        case Action.LOADTYPESFROMAFILE=> response = TypeService.loadTypesFromAFile
        case Action.DUMPALLTYPESBYOBJTYPEASJSON => response = TypeService.dumpAllTypesByObjTypeAsJson
        //function management
        case Action.ADDFUNCTION => response = FunctionService.addFunction(input)
        case Action.GETFUNCTION => response = FunctionService.getFunction
        case Action.REMOVEFUNCTION => response = FunctionService.removeFunction
        case Action.UPDATEFUNCTION => response = FunctionService.updateFunction(input)
        case Action.LOADFUNCTIONSFROMAFILE => response = FunctionService.loadFunctionsFromAFile
        case Action.DUMPALLFUNCTIONSASJSON => response = FunctionService.dumpAllFunctionsAsJson
        //config
        case Action.UPLOADENGINECONFIG => response = ConfigService.uploadEngineConfig(input)
        case Action.UPLOADCOMPILECONFIG => response = ConfigService.uploadCompileConfig(input)
        case Action.DUMPALLCFGOBJECTS => response = ConfigService.dumpAllCfgObjects
        case Action.REMOVEENGINECONFIG => response = ConfigService.removeEngineConfig
        //service
        case Action.ADDCONCEPT => response = ConceptService.addConcept(input)
        case Action.REMOVECONCEPT => response =ConceptService.removeConcept
        case Action.LOADCONCEPTSFROMAFILE => response =ConceptService.loadConceptsFromAFile
        case Action.DUMPALLCONCEPTSASJSON => response =ConceptService.dumpAllConceptsAsJson
        //jar
        case Action.UPLOADJAR=>response = JarService.uploadJar(input)
        //dumps
        case Action.DUMPMETADATA=>response =DumpService.dumpMetadata
        case Action.DUMPALLNODES=>response =DumpService.dumpAllNodes
        case Action.DUMPALLCLUSTERS=>response =DumpService.dumpAllClusters
        case Action.DUMPALLCLUSTERCFGS=>response =DumpService.dumpAllClusterCfgs
        case Action.DUMPALLADAPTERS=>response =DumpService.dumpAllAdapters
        case _ => {
          println("Unexpected action!")
          sys.exit(1)
        }
      }
    }
    catch {
      case e: Exception => response = e.getStackTraceString
    }
    response
  }
}