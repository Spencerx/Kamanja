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
import com.ligadata.kamanja.metadata.AttributeDef

import scala.io.Source
import org.apache.logging.log4j._
/**
 * Created by dhaval on 8/13/15.
 */
object ConceptService {

  private val userid: Option[String] = Some("metadataapi")
  val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)

  def addConcept(input: String): String ={
    var response = ""
    var conceptFileDir: String = ""
    //val gitMsgFile = "https://raw.githubusercontent.com/ligadata-dhaval/Kamanja/master/HelloWorld_Msg_Def.json"
    if (input == "") {
      conceptFileDir = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CONCEPT_FILES_DIR")
      if (conceptFileDir == null) {
        response = "CONCEPT_FILES_DIR property missing in the metadata API configuration"
      } else {
        //verify the directory where messages can be present
        IsValidDir(conceptFileDir) match {
          case true => {
            //get all files with json extension
            val types: Array[File] = new java.io.File(conceptFileDir).listFiles.filter(_.getName.endsWith(".json"))
            types.length match {
              case 0 => {
                println("Concepts not found at " + conceptFileDir)
                "Concepts not found at " + conceptFileDir
              }
              case option => {
                val conceptDefs = getUserInputFromMainMenu(types)
                for (conceptDef <- conceptDefs) {
                  response += MetadataAPIImpl.AddConcepts(conceptDef.toString, "JSON", userid)
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
      var message = new File(input.toString)
      if(message.exists()){
        val conceptDef = Source.fromFile(message).mkString
        response = MetadataAPIImpl.AddConcepts(conceptDef.toString, "JSON", userid)
      }else{
        response="File does not exist"
      }
    }
    response
  }
  def removeConcept(param: String = ""): String ={
    var response = ""
    try {
      if (param.length > 0) {
        try {
            return MetadataAPIImpl.RemoveConcept(param.asInstanceOf[AttributeDef])
        } catch {
          case e: Exception => e.printStackTrace()
        }
      }
      val conceptKeys = MetadataAPIImpl.GetAllConcepts("JSON", userid).toArray

      if (conceptKeys.length == 0) {
        val errorMsg = "Sorry, No concepts available, in the Metadata, to delete!"
        response = errorMsg
      }
      else {
        println("\nPick the concepts to be deleted from the following list: ")
        var srno = 0
        for (conceptKey <- conceptKeys) {
          srno += 1
          println("[" + srno + "] " + conceptKey)
        }
        println("Enter your choice: ")
        val choice: Int = readInt()

        if (choice < 1 || choice > conceptKeys.length) {
          val errormsg = "Invalid choice " + choice + ". Start with the main menu."
          response = errormsg
        }

        val conceptKey = conceptKeys(choice - 1)

        response=MetadataAPIImpl.RemoveConcept(conceptKey.asInstanceOf[AttributeDef])

      }
    } catch {
      case e: Exception => {
        response = e.getStackTrace.toString
      }
    }
    response
  }
  def updateConcept(input: String): String ={
    var response = ""
    var conceptFileDir: String = ""
    //val gitMsgFile = "https://raw.githubusercontent.com/ligadata-dhaval/Kamanja/master/HelloWorld_Msg_Def.json"
    if (input == "") {
      conceptFileDir = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CONCEPT_FILES_DIR")
      if (conceptFileDir == null) {
        response = "CONCEPT_FILES_DIR property missing in the metadata API configuration"
      } else {
        //verify the directory where messages can be present
        IsValidDir(conceptFileDir) match {
          case true => {
            //get all files with json extension
            val types: Array[File] = new java.io.File(conceptFileDir).listFiles.filter(_.getName.endsWith(".json"))
            types.length match {
              case 0 => {
                println("Concepts not found at " + conceptFileDir)
                "Concepts not found at " + conceptFileDir
              }
              case option => {
                val conceptDefs = getUserInputFromMainMenu(types)
                for (conceptDef <- conceptDefs) {
                  response += MetadataAPIImpl.UpdateConcepts(conceptDef.toString, "JSON", userid)
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
      var message = new File(input.toString)
      if(message.exists()){
        val conceptDef = Source.fromFile(message).mkString
        response = MetadataAPIImpl.UpdateConcepts(conceptDef.toString, "JSON", userid)
      }else{
        response="File does not exist"
      }
    }
    response
  }
  def loadConceptsFromAFile: String ={
    var response="NOT REQUIRED. Please use the ADD CONCEPT option."
    response
  }
  def dumpAllConceptsAsJson: String ={
    var response=""
    try{
      response=MetadataAPIImpl.GetAllConcepts("JSON", userid).toString
    }
    catch {
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

  def   getUserInputFromMainMenu(messages: Array[File]): Array[String] = {
    var listOfMsgDef: Array[String] = Array[String]()
    var srNo = 0
    println("\nPick a Type Definition file(s) from below choices\n")
    for (message <- messages) {
      srNo += 1
      println("[" + srNo + "]" + message)
    }
    print("\nEnter your choice(If more than 1 choice, please use commas to seperate them): \n")
    val userOptions: List[Int] = Console.readLine().filter(_ != '\n').split(',').filter(ch => (ch != null && ch != "")).map(_.trim.toInt).toList
    //check if user input valid. If not exit
    for (userOption <- userOptions) {
      userOption match {
        case userOption if (1 to srNo).contains(userOption) => {
          //find the file location corresponding to the message
          var message = messages(userOption - 1)
          //process message
          val messageDef = Source.fromFile(message).mkString
          listOfMsgDef = listOfMsgDef :+ messageDef
        }
        case _ => {
          println("Unknown option: ")
        }
      }
    }
    listOfMsgDef
  }
}
