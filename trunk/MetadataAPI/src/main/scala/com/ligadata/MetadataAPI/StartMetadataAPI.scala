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

package scala.com.ligadata.MetadataAPI

import java.io.File
import org.apache.logging.log4j.{ Logger, LogManager }

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
  lazy val logger = LogManager.getLogger(loggerName)
  var action = ""
  var location = ""
  var config = ""
  val WITHDEP = "dependsOn"
  val REMOVE = "remove"
  val GET = "get"
  val ACTIVATE="activate"
  val OUTPUT="output"
  val DEACTIVATE="deactivate"
  val UPDATE="update"
  val MODELS="models"
  val MESSAGES="messages"
  val CONTAINERS="containers"
  var expectDep = false
  var expectRemoveParm = false
  var depName: String = ""
  var parmName: String = ""

  def main(args: Array[String]) {
    try {
      var argsUntilParm = 2
      args.foreach(arg =>
        if(arg.equalsIgnoreCase(OUTPUT) || arg.equalsIgnoreCase(UPDATE) || arg.equalsIgnoreCase(MODELS) || arg.equalsIgnoreCase(MESSAGES) || arg.equalsIgnoreCase(CONTAINERS)){
          argsUntilParm=3
        }
      )
      args.foreach( arg => {

         if (arg.endsWith(".json") || arg.endsWith(".xml") || arg.endsWith(".scala") || arg.endsWith(".java") || arg.endsWith(".jar")) {
          location = arg
        } else if (arg.endsWith(".properties")) {
          config = arg
        } else {
          if (arg.equalsIgnoreCase(WITHDEP)) {
            expectDep = true
          }
          else if (expectDep) {
            depName = arg
            expectDep = false
          } else {
            if((arg.equalsIgnoreCase(REMOVE)) || (arg.equalsIgnoreCase(GET)) || (arg.equalsIgnoreCase(ACTIVATE)) || (arg.equalsIgnoreCase(DEACTIVATE)) || (arg.equalsIgnoreCase(UPDATE))) {
              expectRemoveParm = true
            }

            if (expectRemoveParm) {
              argsUntilParm = argsUntilParm - 1
            }

            if (argsUntilParm < 0)
              depName = arg
            else
              action += arg
          }
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
        response = route(Action.withName(action.trim), location, depName)
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


  def route(action: Action.Value, input: String, param: String = ""): String = {
    var response = ""
    try {
      action match {
        //message management
        case Action.ADDMESSAGE => response = MessageService.addMessage(input)
        case Action.UPDATEMESSAGE => response = MessageService.updateMessage(input)

        case Action.REMOVEMESSAGE => {
          if (param.length == 0)
            response = MessageService.removeMessage()
          else
            response = MessageService.removeMessage(param)
        }

        case Action.GETALLMESSAGES => response = MessageService.getAllMessages
        case Action.GETMESSAGE => {
          if(param.length == 0)
            response = MessageService.getMessage()
          else
            response = MessageService.getMessage(param)
        }

        //output message management
        case Action.ADDOUTPUTMESSAGE => response = MessageService.addOutputMessage(input)
        case Action.UPDATEOUTPUTMESSAGE => response =MessageService.updateOutputMessage(input)
        case Action.REMOVEOUTPUTMESSAGE => response ={
          if (param.length == 0)
            MessageService.removeOutputMessage()
          else
            MessageService.removeOutputMessage(param)
      }

        case Action.GETALLOUTPUTMESSAGES => response =MessageService.getAllOutputMessages
        case Action.GETOUTPUTMESSAGE => response ={
      if (param.length == 0)
        MessageService.getOutputMessage()
      else
        MessageService.getOutputMessage(param)
    }

        //model management
        case Action.ADDMODELPMMML => response = ModelService.addModelPmml(input)

        case Action.ADDMODELSCALA => {
          if (param.length == 0)
            response = ModelService.addModelScala(input)
          else
            response = ModelService.addModelScala(input, param)
        }

        case Action.ADDMODELJAVA => {
          if (param.length == 0)
            response = ModelService.addModelJava(input)
          else
            response = ModelService.addModelJava(input, param)
        }

        case Action.REMOVEMODEL => {
          if (param.length == 0)
            response = ModelService.removeModel()
          else
            response = ModelService.removeModel(param)
        }

        case Action.ACTIVATEMODEL =>
          response =
            {
              if (param.length == 0)
                ModelService.activateModel()
              else
                ModelService.activateModel(param)
            }


        case Action.DEACTIVATEMODEL => response =  {
          if (param.length == 0)
            ModelService.deactivateModel()
          else
            ModelService.deactivateModel(param)
        }
        case Action.UPDATEMODELPMML => response = ModelService.updateModelpmml(input)
        //case Action.UPDATEMODELSCALA => response = ModelService.updateModelscala(input)
        //case Action.UPDATEMODELJAVA => response = ModelService.updateModeljava(input)

        case Action.UPDATEMODELSCALA => {
          if (param.length == 0)
            response = ModelService.updateModelscala(input)
          else
            response = ModelService.updateModelscala(input, param)
        }

        case Action.UPDATEMODELJAVA => {
          if (param.length == 0)
            response = ModelService.updateModeljava(input)
          else
            response = ModelService.updateModeljava(input, param)
        }
//

        case Action.GETALLMODELS => response = ModelService.getAllModels
        case Action.GETMODEL => response =
          {
            if (param.length == 0)
              ModelService.getModel()
            else
              ModelService.getModel(param)
          }

        //container management
        case Action.ADDCONTAINER => response = ContainerService.addContainer(input)
        case Action.UPDATECONTAINER => response = ContainerService.updateContainer(input)
        case Action.GETCONTAINER => response = {
          if (param.length == 0)
            ContainerService.getContainer()
          else
            ContainerService.getContainer(param)
        }

        case Action.GETALLCONTAINERS => response = ContainerService.getAllContainers

        case Action.REMOVECONTAINER => {
          if (param.length == 0)
            response = ContainerService.removeContainer()
          else
            response = ContainerService.removeContainer(param)
        }
        //Type management
        case Action.ADDTYPE => response = TypeService.addType(input)
        case Action.GETTYPE => response =
          {
            if (param.length == 0)
              TypeService.getType()
            else
              TypeService.getType(param)
          }

        case Action.GETALLTYPES => response = TypeService.getAllTypes
        case Action.REMOVETYPE => response =
          {
            if (param.length == 0)
              TypeService.removeType()
            else
              TypeService.removeType(param)

          }
        case Action.LOADTYPESFROMAFILE=> response = TypeService.loadTypesFromAFile
        case Action.DUMPALLTYPESBYOBJTYPEASJSON => response = TypeService.dumpAllTypesByObjTypeAsJson
        //function management
        case Action.ADDFUNCTION => response = FunctionService.addFunction(input)
        case Action.GETFUNCTION => response =
          {
            if (param.length == 0)
              FunctionService.getFunction()
            else
              FunctionService.getFunction(param)

          }
        case Action.REMOVEFUNCTION => response =
          {
            if (param.length == 0)
              FunctionService.removeFunction()
            else
              FunctionService.removeFunction(param)

          }

        case Action.UPDATEFUNCTION => response = FunctionService.updateFunction(input)
        case Action.LOADFUNCTIONSFROMAFILE => response = FunctionService.loadFunctionsFromAFile
        case Action.DUMPALLFUNCTIONSASJSON => response = FunctionService.dumpAllFunctionsAsJson
        //config
        case Action.UPLOADCLUSTERCONFIG => response = ConfigService.uploadClusterConfig(input)
        case Action.UPLOADCOMPILECONFIG => response = ConfigService.uploadCompileConfig(input)
        case Action.DUMPALLCFGOBJECTS => response = ConfigService.dumpAllCfgObjects
        case Action.REMOVEENGINECONFIG => response = ConfigService.removeEngineConfig
        //concept
        case Action.ADDCONCEPT => response = ConceptService.addConcept(input)
        case Action.REMOVECONCEPT => response =
          {
            if (param.length == 0)
              ConceptService.removeConcept()
            else
              ConceptService.removeConcept(param)

          }

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
