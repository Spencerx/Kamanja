package com.ligadata.MetadataAPI.Utility

import java.io.File

import com.ligadata.MetadataAPI.MetadataAPIImpl

import scala.io.Source

import org.apache.log4j._

import com.ligadata.kamanja.metadata.Utils

/**
 * Created by dhaval on 8/7/15.
 */

object ModelService {
  private val userid: Option[String] = Some("metadataapi")
  val loggerName = this.getClass.getName
  lazy val logger = Logger.getLogger(loggerName)

  def addModelPmml(input: String): String = {
    var modelDef=""
    var response: String = ""
    var modelFileDir: String = ""
    if (input == "") {
      //get the messages location from the config file. If error get the location from github
      modelFileDir = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MODEL_FILES_DIR")
      if (modelFileDir == null) {
        response = "MODEL_FILES_DIR property missing in the metadata API configuration"
      } else {
        //verify the directory where messages can be present
        IsValidDir(modelFileDir) match {
          case true => {
            //get all files with json extension
            val models: Array[File] = new java.io.File(modelFileDir).listFiles.filter(_.getName.endsWith(".xml"))
            models.length match {
              case 0 => {
                val errorMsg = "Models not found at " + modelFileDir
                println(errorMsg)
                response = errorMsg
              }
              case option => {
                var  modelDefs=getUserInputFromMainMenu(models)
                for (modelDef <- modelDefs)
                  response += MetadataAPIImpl.AddModel(modelDef.toString, userid)
              }
            }
          }
          case false => {
            response = "Model directory is invalid."
          }
        }
      }
    } else {
      //   println("Path provided. Added msg")
      //process message
      var model = new File(input.toString)
      if(model.exists()){
        modelDef= Source.fromFile(model).mkString
        response = MetadataAPIImpl.AddModel(modelDef.toString, userid)
      }else{
        response="Model definition file does not exist."
      }
    }
    response
  }

  def updateModel(input: String): String = {
    var modelDef=""
    var response: String = ""
    var modelFileDir: String = ""
    if (input == "") {
      //get the messages location from the config file. If error get the location from github
      modelFileDir = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MODEL_FILES_DIR")
      if (modelFileDir == null) {
        response = "MODEL_FILES_DIR property missing in the metadata API configuration"
      } else {
        //verify the directory where messages can be present
        IsValidDir(modelFileDir) match {
          case true => {
            //get all files with json extension
            val models: Array[File] = new java.io.File(modelFileDir).listFiles.filter(_.getName.endsWith(".xml"))
            models.length match {
              case 0 => {
                val errorMsg = "Models not found at " + modelFileDir
                println(errorMsg)
                response = errorMsg
              }
              case option => {
                var  modelDefs=getUserInputFromMainMenu(models)
                for (modelDef <- modelDefs)
                  response = MetadataAPIImpl.UpdateModel(modelDef.toString, userid)
              }
            }
          }
          case false => {
            //println("Message directory is invalid.")
            response = "Model directory is invalid."
          }
        }
      }
    } else {
      //   println("Path provided. Added msg")
      //process message
      var model = new File(input.toString)
      if(model.exists()){
        modelDef= Source.fromFile(model).mkString
        response = MetadataAPIImpl.UpdateModel(modelDef, userid)
      }else{
        response="Model definition file does not exist."
      }
    }
    response
  }

  def getModel: String ={
    var response=""
    try {
      val modelKeys = MetadataAPIImpl.GetAllModelsFromCache(true, None)
      if (modelKeys.length == 0) {
        val errorMsg="Sorry, No models available, in the Metadata, to display!"
        response=errorMsg
      }
      else{
        println("\nPick the model to be displayed from the following list: ")
        var srno = 0
        for(modelKey <- modelKeys){
          srno+=1
          println("["+srno+"] "+modelKey)
        }
        println("Enter your choice: ")
        val choice: Int = readInt()

        if (choice < 1 || choice > modelKeys.length) {
          val errormsg="Invalid choice " + choice + ". Start with the main menu."
          response=errormsg
        }
        val modelKey = modelKeys(choice - 1)
        val modelKeyTokens = modelKey.split("\\.")
        val modelNameSpace = modelKeyTokens(0)
        val modelName = modelKeyTokens(1)
        val modelVersion = modelKeyTokens(2)
        val apiResult = MetadataAPIImpl.GetModelDefFromCache(modelNameSpace, modelName,"JSON",modelVersion, userid).toString
        response=apiResult
      }

    } catch {
      case e: Exception => {
        response=e.getStackTrace.toString
      }
    }
    response
  }

  def getAllModels: String ={
    var response=""
    val modelKeys = MetadataAPIImpl.GetAllModelsFromCache(true, userid)
    if (modelKeys.length == 0) {
      response="Sorry, No models available in the Metadata"
    }else{
      var srNo = 0
      for(modelKey <- modelKeys){
        srNo += 1
        response+="[" + srNo + "]" + modelKey+"\n"
      }
    }
    response
  }

  def addModelScala(input: String): String = {
    var modelDefs= Array[String]()
    var modelConfig=""
    var modelDef=""
    var response: String = ""
    var modelFileDir: String = ""
    if (input == "") {
      //get the messages location from the config file. If error get the location from github
      modelFileDir = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MODEL_FILES_DIR")
      if (modelFileDir == null) {
        response = "MODEL_FILES_DIR property missing in the metadata API configuration"
      } else {
        //verify the directory where messages can be present
        IsValidDir(modelFileDir) match {
          case true => {
            //get all files with json extension
            val models: Array[File] = new java.io.File(modelFileDir).listFiles.filter(_.getName.endsWith(".scala"))
            models.length match {
              case 0 => {
                val errorMsg = "Models not found at " + modelFileDir
                response = errorMsg
              }
              case option => {

                modelDefs=getUserInputFromMainMenu(models)
              }
            }
          }
          case false => {
            //println("Message directory is invalid.")
            response = "Model directory is invalid."
          }
        }
      }
    } else {
      var model = new File(input.toString)
      modelDef = Source.fromFile(model).mkString
      modelDefs=modelDefs:+modelDef
    }
    if(modelDefs.nonEmpty) {
      for (modelDef <- modelDefs){
        println("Adding the next model in the queue.")
        //before adding a model, add its config file.
        var configKeys = MetadataAPIImpl.getModelConfigNames
        if(configKeys.isEmpty){
          response="No model configuration loaded in the metadata!"
        }else{
          var srNo = 0
          println("\nPick a Model Definition file(s) from below choices\n")
          for (configkey <- configKeys) {
            srNo += 1
            println("[" + srNo + "]" + configkey)
          }
          print("\nEnter your choice: \n")
          var userOption = Console.readInt()

          userOption match {
            case x if ((1 to srNo).contains(userOption)) => {
              //find the file location corresponding to the config file
              modelConfig=configKeys(userOption.toInt - 1)
              println("Model config selected is "+modelConfig)
            }
            case _ => {
              val errorMsg = "Incorrect input " + userOption + ". Please enter the correct option."
              println(errorMsg)
              errorMsg
            }
          }
          response+= MetadataAPIImpl.AddModelFromSource(modelDef, "scala", modelConfig, userid)
        }
      }
    }
    response
  }

  def addModelJava(input: String): String = {
    var modelDefs= Array[String]()
    var modelConfig=""
    var modelDef=""
    var response: String = ""
    var modelFileDir: String = ""
    if (input == "") {
      //get the messages location from the config file. If error get the location from github
      modelFileDir = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MODEL_FILES_DIR")
      if (modelFileDir == null) {
        response = "MODEL_FILES_DIR property missing in the metadata API configuration"
      } else {
        //verify the directory where messages can be present
        IsValidDir(modelFileDir) match {
          case true => {
            //get all files with json extension
            val models: Array[File] = new java.io.File(modelFileDir).listFiles.filter(_.getName.endsWith(".java"))
            models.length match {
              case 0 => {
                val errorMsg = "Models not found at " + modelFileDir
                response = errorMsg
              }
              case option => {

                modelDefs=getUserInputFromMainMenu(models)
              }
            }
          }
          case false => {
            //println("Message directory is invalid.")
            response = "Model directory is invalid."
          }
        }
      }
    } else {
      var model = new File(input.toString)
      modelDef = Source.fromFile(model).mkString
      modelDefs=modelDefs:+modelDef
    }
    if(modelDefs.nonEmpty) {
      for (modelDef <- modelDefs){
        //before adding a model, add its config file.
        var configKeys = MetadataAPIImpl.getModelConfigNames
        if(configKeys.isEmpty){
          response="No model configuration loaded in the metadata!"
        }else{
          var srNo = 0
          println("\nPick a Model Definition file(s) from below choices\n")
          for (configkey <- configKeys) {
            srNo += 1
            println("[" + srNo + "]" + configkey)
          }
          print("\nEnter your choice: \n")
          var userOption = Console.readInt()

          userOption match {
            case x if ((1 to srNo).contains(userOption)) => {
              //find the file location corresponding to the config file
              modelConfig=configKeys(userOption.toInt - 1)
              println("Model config selected is "+modelConfig)
            }
            case _ => {
              val errorMsg = "Incorrect input " + userOption + ". Please enter the correct option."
              println(errorMsg)
              errorMsg
            }
          }
          response+= MetadataAPIImpl.AddModelFromSource(modelDef, "java", modelConfig, userid)
        }
      }
    }
    response
  }

  def removeModel(input: String): String ={
    var response=""
    var modelKey=""
    try {
      //  logger.setLevel(Level.TRACE); //check again
      if (input == ""){
        val modelKeys = MetadataAPIImpl.GetAllModelsFromCache(true, None)

        if (modelKeys.length == 0) {
          val errorMsg="Sorry, No models available, in the Metadata, to delete!"
          //println(errorMsg)
          response=errorMsg
        }
        else {
          println("\nPick the model to be deleted from the following list: ")
          var srno = 0
          for (modelKey <- modelKeys) {
            srno += 1
            println("[" + srno + "] " + modelKey)
          }
          println("Enter your choice: ")
          val choice: Int = readInt()

          if (choice < 1 || choice > modelKeys.length) {
            val errormsg = "Invalid choice " + choice + ". Start with the main menu."
            //println(errormsg)
            response = errormsg
          }
          //TODO
          modelKey = modelKeys(choice - 1)
        }
      }else{
        modelKey=input
        }
      val modelInfo  =    Utils.parseNameToken(modelKey)
        val apiResult = MetadataAPIImpl.RemoveModel(modelInfo._1, modelInfo._2, modelInfo._3.toInt, userid).toString
        response=apiResult
    } catch {
      case e: Exception => {
        //e.printStackTrace
        response=e.getStackTrace.toString
      }
    }
    response
  }

  def activateModel: String ={
    var response=""
    try {
      val modelKeys = MetadataAPIImpl.GetAllModelsFromCache(false, None)
      if (modelKeys.length == 0) {
        val errorMsg="Sorry, No models available, in the Metadata, to activate!"
        response=errorMsg
      }
      else{
        println("\nPick the model to be activated from the following list: ")
        var srno = 0
        for(modelKey <- modelKeys){
          srno+=1
          println("["+srno+"] "+modelKey)
        }
        println("Enter your choice: ")
        val choice: Int = readInt()

        if (choice < 1 || choice > modelKeys.length) {
          val errormsg="Invalid choice " + choice + ". Start with the main menu."
          response=errormsg
        }
        val modelKey = modelKeys(choice - 1)
        val modelKeyTokens = modelKey.split("\\.")
        val modelNameSpace = modelKeyTokens(0)
        val modelName = modelKeyTokens(1)
        val modelVersion = modelKeyTokens(2)
        val apiResult = MetadataAPIImpl.ActivateModel(modelNameSpace, modelName, modelVersion.toLong, userid).toString
        response=apiResult
      }

    } catch {
      case e: Exception => {
        response=e.getStackTrace.toString
      }
    }
    response
  }

  def deactivateModel:String={
    var response=""
    try {
      val modelKeys = MetadataAPIImpl.GetAllModelsFromCache(true, None)

      if (modelKeys.length == 0) {
        val errorMsg="Sorry, No models available, in the Metadata, to deactivate!"
        //println(errorMsg)
        response=errorMsg
      }
      else{
        println("\nPick the model to be de-activated from the following list: ")
        var srno = 0
        for(modelKey <- modelKeys){
          srno+=1
          println("["+srno+"] "+modelKey)
        }
        println("Enter your choice: ")
        val choice: Int = readInt()

        if (choice < 1 || choice > modelKeys.length) {
          val errormsg="Invalid choice " + choice + ". Start with the main menu."
          response=errormsg
        }
        val modelKey = modelKeys(choice - 1)
        val modelKeyTokens = modelKey.split("\\.")
        val modelNameSpace = modelKeyTokens(0)
        val modelName = modelKeyTokens(1)
        val modelVersion = modelKeyTokens(2)
        val apiResult = MetadataAPIImpl.DeactivateModel(modelNameSpace, modelName, modelVersion.toLong, userid).toString
        response=apiResult
      }
    } catch {
      case e: Exception => {
        response=e.getStackTrace.toString
      }
    }
    response
  }

  def IsValidDir(dirName: String): Boolean = {
    val iFile = new File(dirName)
    if (!iFile.exists) {
      println("The File Path (" + dirName + ") is not found: ")
      false
    } else if (!iFile.isDirectory) {
      println("The File Path (" + dirName + ") is not a directory: ")
      false
    } else
      true
  }

  def getUserInputFromMainMenu(models: Array[File]): Array[String] = {
    var listOfModelDef: Array[String]=Array[String]()
    var srNo = 0
    println("\nPick a Model Definition file(s) from below choices\n")
    for (model <- models) {
      srNo += 1
      println("[" + srNo + "]" + model)
    }
    print("\nEnter your choice(If more than 1 choice, please use commas to seperate them): \n")
    var userOptions = Console.readLine().split(",")
    println("User selected the option(s) " + userOptions.length)
    //check if user input valid. If not exit
    for (userOption <- userOptions) {
      userOption.toInt match {
        case x if ((1 to srNo).contains(userOption.toInt)) => {
          //find the file location corresponding to the message

          val model = models(userOption.toInt - 1)
          //process message
          val modelDef = Source.fromFile(model).mkString
          //val response: String = MetadataAPIImpl.AddModel(modelDef, userid).toString
          listOfModelDef = listOfModelDef:+modelDef
        }
      }
    }
    listOfModelDef
  }
}
