package com.ligadata.MetadataAPI.Utility

import java.io.File

import com.ligadata.MetadataAPI.MetadataAPIImpl

import scala.io.Source
import org.apache.log4j._
/**
 * Created by dhaval on 8/13/15.
 */
object JarService {
  private val userid: Option[String] = Some("metadataapi")
  val loggerName = this.getClass.getName
  lazy val logger = Logger.getLogger(loggerName)
def uploadJar(input: String): String ={
  var response = ""
  var typeFileDir: String = ""
  //val gitMsgFile = "https://raw.githubusercontent.com/ligadata-dhaval/Kamanja/master/HelloWorld_Msg_Def.json"
  if (input == "") {
    typeFileDir = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_TARGET_DIR")
    if (typeFileDir == null) {
      response = "JAR_TARGET_DIR property missing in the metadata API configuration"
    } else {
      //verify the directory where messages can be present
      IsValidDir(typeFileDir) match {
        case true => {
          //get all files with json extension
          val types: Array[File] = new java.io.File(typeFileDir).listFiles.filter(_.getName.endsWith(".jar"))
          types.length match {
            case 0 => {
              println("Jars not found at " + typeFileDir)
              response="Jars not found at " + typeFileDir
            }
            case option => {
              val jarDefs = getUserInputFromMainMenu(types)
              for (jarDef <- jarDefs) {
                response += MetadataAPIImpl.UploadJar(jarDef.toString)
              }
            }
          }
        }
        case false => {
          response = "JAR directory is invalid."
        }
      }
    }
  } else {
    //input provided
    var message = new File(input.toString)
    if(message.exists()){
      val jarDef = Source.fromFile(message).mkString
      response =MetadataAPIImpl.UploadJar(jarDef.toString)
    }else{
      response="Jar location is invalid"
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
    println("\nPick a Jar Definition file(s) from below choices\n")
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
