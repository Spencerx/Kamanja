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

package com.ligadata.MetadataAPI.Utility

import java.io.File

import com.ligadata.MetadataAPI.MetadataAPIImpl

import scala.io.Source
import org.apache.logging.log4j._
/**
 * Created by dhaval on 8/12/15.
 */
object FunctionService {
  private val userid: Option[String] = Some("metadataapi")
  val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)

  def addFunction(input: String): String ={
    var response = ""
    var functionFileDir: String = ""
    //val gitMsgFile = "https://raw.githubusercontent.com/ligadata-dhaval/Kamanja/master/HelloWorld_Msg_Def.json"
    if (input == "") {
      functionFileDir = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("FUNCTION_FILES_DIR")
      if (functionFileDir == null) {
        response = "FUNCTION_FILES_DIR property missing in the metadata API configuration"
      } else {
        //verify the directory where messages can be present
        IsValidDir(functionFileDir) match {
          case true => {
            //get all files with json extension
            val types: Array[File] = new java.io.File(functionFileDir).listFiles.filter(_.getName.endsWith(".json"))
            types.length match {
              case 0 => {
                println("Functions not found at " + functionFileDir)
                "Functions not found at " + functionFileDir
              }
              case option => {
                val functionDefs = getUserInputFromMainMenu(types)
                for (functionDef <- functionDefs) {
                  response += MetadataAPIImpl.AddFunctions(functionDef.toString, "JSON", userid)
                }
              }
            }
          }
          case false => {
            //println("Message directory is invalid.")
            response = "Message directory is invalid."
          }
        }
      }
    } else {
      //input provided
      var function = new File(input.toString)
      if(function.exists()){
        val functionDef = Source.fromFile(function).mkString
        response = MetadataAPIImpl.AddFunctions(functionDef.toString, "JSON", userid)
      }else{
        response="File does not exist"
      }
    }
    response
  }
  def getFunction(param: String = ""): String ={
    var response=""
    try {
      if (param.length > 0) {
        val(ns, name, ver) = com.ligadata.kamanja.metadata.Utils.parseNameToken(param)
        try {
          return MetadataAPIImpl.GetFunctionDef(ns, name,"JSON", userid)
        } catch {
          case e: Exception => e.printStackTrace()
        }
      }
      val functionKeys = MetadataAPIImpl.GetAllFunctionsFromCache(true, None)
      if (functionKeys.length == 0) {
        val errorMsg="Sorry, No functions available, in the Metadata, to display!"
        response=errorMsg
      }
      else{
        println("\nPick the type to be displayed from the following list: ")
        var srno = 0
        for(functionKey <- functionKeys){
          srno+=1
          println("["+srno+"] "+functionKey)
        }
        println("Enter your choice: ")
        val choice: Int = readInt()

        if (choice < 1 || choice > functionKeys.length) {
          val errormsg="Invalid choice " + choice + ". Start with the main menu."
          response=errormsg
        }
        val functionKey = functionKeys(choice - 1)
        val functionKeyTokens = functionKey.split("\\.")
        val functionNameSpace = functionKeyTokens(0)
        val functionName = functionKeyTokens(1)
        val functionVersion = functionKeyTokens(2)
        response = MetadataAPIImpl.GetFunctionDef(functionNameSpace, functionName,"JSON", userid).toString
      }

    } catch {
      case e: Exception => {
        response=e.getStackTrace.toString
      }
    }
    response
  }
  def removeFunction(param: String = ""): String ={
    var response=""
    try {
      if (param.length > 0) {
        val(ns, name, ver) = com.ligadata.kamanja.metadata.Utils.parseNameToken(param)
        try {
          return MetadataAPIImpl.RemoveFunction(ns, name,ver.toInt, userid)
        } catch {
          case e: Exception => e.printStackTrace()
        }
      }

      val functionKeys =MetadataAPIImpl.GetAllFunctionsFromCache(true, None)
      if (functionKeys.length == 0) {
        val errorMsg="Sorry, No functions available, in the Metadata, to delete!"
        //println(errorMsg)
        response=errorMsg
      }
      else{
        println("\nPick the function to be deleted from the following list: ")
        var srno = 0
        for(functionKey <- functionKeys){
          srno+=1
          println("["+srno+"] "+functionKey)
        }
        println("Enter your choice: ")
        val choice: Int = readInt()

        if (choice < 1 || choice > functionKeys.length) {
          val errormsg="Invalid choice " + choice + ". Start with the main menu."
          //println(errormsg)
          response=errormsg
        }
        val fcnKey = functionKeys(choice - 1)

        val(fcnNameSpace, fcnName, fcnVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(fcnKey)

        response=MetadataAPIImpl.RemoveFunction(fcnNameSpace, fcnName, fcnVersion.toLong, userid)
      }
    } catch {
      case e: Exception => {
        //e.printStackTrace
        response=e.getStackTrace.toString
      }
    }
    response
  }
  def updateFunction(input: String): String ={
    var response = ""
    var functionFileDir: String = ""
    //val gitMsgFile = "https://raw.githubusercontent.com/ligadata-dhaval/Kamanja/master/HelloWorld_Msg_Def.json"
    if (input == "") {
      functionFileDir = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("FUNCTION_FILES_DIR")
      if (functionFileDir == null) {
        response = "FUNCTION_FILES_DIR property missing in the metadata API configuration"
      } else {
        //verify the directory where messages can be present
        IsValidDir(functionFileDir) match {
          case true => {
            //get all files with json extension
            val types: Array[File] = new java.io.File(functionFileDir).listFiles.filter(_.getName.endsWith(".json"))
            types.length match {
              case 0 => {
                println("Functions not found at " + functionFileDir)
                "Functions not found at " + functionFileDir
              }
              case option => {
                val functionDefs = getUserInputFromMainMenu(types)
                for (functionDef <- functionDefs) {
                  response += MetadataAPIImpl.UpdateFunctions(functionDef.toString, "JSON", userid)
                }
              }
            }
          }
          case false => {
            //println("Message directory is invalid.")
            response = "Message directory is invalid."
          }
        }
      }
    } else {
      //input provided
      var function = new File(input.toString)
      if(function.exists()){
        val functionDef = Source.fromFile(function).mkString
        response = MetadataAPIImpl.UpdateFunctions(functionDef.toString, "JSON", userid)
      }else{
        response="File does not exist"
      }
    }
    response
  }
  //NOT REQUIRED
  def loadFunctionsFromAFile: String ={
    var response="NOT REQUIRED. Please use the ADD TYPE option."
    response
  }
  def dumpAllFunctionsAsJson: String ={
    var response=""
    try{
      response=MetadataAPIImpl.GetAllFunctionDefs("JSON", userid).toString()
    }
    catch {
      case e: Exception => {
        response=e.getStackTrace.toString
      }
    }
    response
  }

  //utility
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
    println("\nPick a Function Definition file(s) from below choices\n")
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
