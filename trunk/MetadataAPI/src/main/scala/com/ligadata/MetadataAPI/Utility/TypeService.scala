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
object TypeService {
  private val userid: Option[String] = Some("metadataapi")
  val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)

  def addType(input: String): String ={
    var response = ""
    var typeFileDir: String = ""
    //val gitMsgFile = "https://raw.githubusercontent.com/ligadata-dhaval/Kamanja/master/HelloWorld_Msg_Def.json"
    if (input == "") {
      typeFileDir = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("TYPE_FILES_DIR")
      if (typeFileDir == null) {
        response = "TYPE_FILES_DIR property missing in the metadata API configuration"
      } else {
        //verify the directory where messages can be present
        IsValidDir(typeFileDir) match {
          case true => {
            //get all files with json extension
            val types: Array[File] = new java.io.File(typeFileDir).listFiles.filter(_.getName.endsWith(".json"))
            types.length match {
              case 0 => {
                println("Types not found at " + typeFileDir)
                "Types not found at " + typeFileDir
              }
              case option => {
                val typeDefs = getUserInputFromMainMenu(types)
                for (typeDef <- typeDefs) {
                  response += MetadataAPIImpl.AddTypes(typeDef.toString, "JSON", userid)
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
        val typeDef = Source.fromFile(message).mkString
        response = MetadataAPIImpl.AddTypes(typeDef.toString, "JSON", userid)

      }else{
        response="File does not exist"
      }
    }
    response
  }

  def getType(param: String=""): String ={
    var response=""
    try {
      if (param.length > 0) {
        val(ns, name, ver) = com.ligadata.kamanja.metadata.Utils.parseNameToken(param)
        try {
          return MetadataAPIImpl.GetType(ns, name,ver,"JSON", userid).toString
        } catch {
          case e: Exception => e.printStackTrace()
        }
      }
      val typeKeys = MetadataAPIImpl.GetAllKeys("TypeDef", None)
      if (typeKeys.length == 0) {
        val errorMsg="Sorry, No types available, in the Metadata, to display!"
        response=errorMsg
      }
      else{
        println("\nPick the type to be displayed from the following list: ")
        var srno = 0
        for(typeKey <- typeKeys){
          srno+=1
          println("["+srno+"] "+typeKey)
        }
        println("Enter your choice: ")
        val choice: Int = readInt()

        if (choice < 1 || choice > typeKeys.length) {
          val errormsg="Invalid choice " + choice + ". Start with the main menu."
          response=errormsg
        }
        val typeKey = typeKeys(choice - 1)
        val typeKeyTokens = typeKey.split("\\.")
        val typeNameSpace = typeKeyTokens(0)
        val typeName = typeKeyTokens(1)
        val typeVersion = typeKeyTokens(2)
        response = MetadataAPIImpl.GetType(typeNameSpace, typeName,typeVersion, "JSON",userid).toString

      }

    } catch {
      case e: Exception => {
        response=e.getStackTrace.toString
      }
    }
    response
  }
  def getAllTypes: String = {
    MetadataAPIImpl.GetAllTypes("JSON", userid)
  }

  def removeType(param: String = ""): String ={
    var response=""
    try {
      if (param.length > 0) {
        val(ns, name, ver) = com.ligadata.kamanja.metadata.Utils.parseNameToken(param)
        try {
          return MetadataAPIImpl.RemoveType(ns, name,ver.toLong, userid).toString
        } catch {
          case e: Exception => e.printStackTrace()
        }
      }
      val typeKeys =MetadataAPIImpl.GetAllKeys("TypeDef", None)

      if (typeKeys.length == 0) {
        val errorMsg="Sorry, No types available, in the Metadata, to delete!"
        //println(errorMsg)
        response=errorMsg
      }
      else{
        println("\nPick the type to be deleted from the following list: ")
        var srno = 0
        for(modelKey <- typeKeys){
          srno+=1
          println("["+srno+"] "+modelKey)
        }
        println("Enter your choice: ")
        val choice: Int = readInt()

        if (choice < 1 || choice > typeKeys.length) {
          val errormsg="Invalid choice " + choice + ". Start with the main menu."
          //println(errormsg)
          response=errormsg
        }
        val typeKey = typeKeys(choice - 1)
        val(typeNameSpace, typeName, typeVersion) = com.ligadata.kamanja.metadata.Utils.parseNameToken(typeKey)
        response = MetadataAPIImpl.RemoveType(typeNameSpace, typeName, typeVersion.toLong, userid).toString
      }

    } catch {
      case e: Exception => {
        //e.printStackTrace
        response=e.getStackTrace.toString
      }
    }
    response
  }
  //NOT REQUIRED
  def loadTypesFromAFile: String ={

    val response="NOT REQUIRED. Please use the ADD TYPE option."
    response
  }

  def dumpAllTypesByObjTypeAsJson: String ={
    var response=""
    try {
      val typeMenu = Map(1 -> "ScalarTypeDef",
        2 -> "ArrayTypeDef",
        3 -> "ArrayBufTypeDef",
        4 -> "SetTypeDef",
        5 -> "TreeSetTypeDef",
        6 -> "AnyTypeDef",
        7 -> "SortedSetTypeDef",
        8 -> "MapTypeDef",
        9 -> "HashMapTypeDef",
        10 -> "ImmutableMapTypeDef",
        11 -> "ListTypeDef",
        12 -> "QueueTypeDef",
        13 -> "TupleTypeDef")
      var selectedType = "com.ligadata.kamanja.metadata.ScalarTypeDef"
      var done = false
      while (done == false) {
        println("\n\nPick a Type ")
        var seq = 0
        typeMenu.foreach(key => { seq += 1; println("[" + seq + "] " + typeMenu(seq)) })
        seq += 1
        println("[" + seq + "] Main Menu")
        print("\nEnter your choice: ")
        val choice: Int = readInt()
        if (choice <= typeMenu.size) {
          selectedType = "com.ligadata.kamanja.metadata." + typeMenu(choice)
          done = true
        } else if (choice == typeMenu.size + 1) {
          done = true
        } else {
          logger.error("Invalid Choice : " + choice)
        }
      }

      response = MetadataAPIImpl.GetAllTypesByObjType("JSON", selectedType)
    } catch {
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
