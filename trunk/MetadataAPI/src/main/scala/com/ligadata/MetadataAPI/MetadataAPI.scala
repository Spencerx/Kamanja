package com.ligadata.MetadataAPI

import scala.Enumeration
import scala.io.Source._
import java.util._
import scala.util.parsing.json.{JSONObject, JSONArray}
import scala.collection.immutable.Map

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

/** A class that defines the result any of the API function uniformly
 * @constructor creates a new ApiResult with a statusCode,statusDescription,resultData
 * @param statusCode status of the API call, 0 => success, non-zero => failure.
 * @param statusDescription relevant in case of non-zero status code
 * @param resultData A string value representing string, either XML or JSON format
 */
class ApiResult(var statusCode:Int, var statusDescription: String, var resultData: String){
/**
 * Override toString to return ApiResult as a String
 */
  override def toString: String = {
    val json = ("APIResults" -> ("statusCode" -> statusCode) ~
		  ("statusDescription"  -> statusDescription) ~
		  ("resultData"  -> resultData))
    pretty(render(json))
  }
}

trait MetadataAPI {
  /** MetadataAPI defines the CRUD (create, read, update, delete) operations on metadata objects supported
   * by this system. The metadata objects includes Types, Functions, Concepts, Derived Concepts,
   * MessageDefinitions, Model Definitions. All functions take String values as input in XML or JSON Format
   * returns JSON string of ApiResult object.
   */

  /** Add new types 
   * @param typesText an input String of types in a format defined by the next parameter formatType
   * @param formatType format of typesText ( JSON or XML)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def AddType(typesText:String, formatType:String): String

  /** Update existing types
   * @param typesText an input String of types in a format defined by the next parameter formatType
   * @param formatType format of typesText ( JSON or XML)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def UpdateType(typesText:String, formatType:String): String

  /** Remove Type for given typeName and version
   * @param typeName name of the Type
   * @version version of the Type
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def RemoveType(typeName:String, version:Int): String

  // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  /** Upload Jars into system. Dependency jars may need to upload first. Once we upload the jar,
   * if we retry to upload it will throw an exception.
   * @param implPath fullPathName of the jar file
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def UploadImplementation(implPath:String): String

  /** Add new functions 
   * @param functionsText an input String of functions in a format defined by the next parameter formatType
   * @param formatType format of functionsText ( JSON or XML)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def AddFunctions(functionsText:String, formatType:String): String

  /** Update existing functions
   * @param functionsText an input String of functions in a format defined by the next parameter formatType
   * @param formatType format of functionsText ( JSON or XML)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def UpdateFunctions(functionsText:String, formatType:String): String

  /** Remove function for given FunctionName and Version
   * @param functionName name of the function
   * @version version of the function
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def RemoveFunction(functionName:String, version:Int): String

  /** Add new concepts 
   * @param conceptsText an input String of concepts in a format defined by the next parameter formatType
   * @param formatType format of conceptsText ( JSON or XML)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def AddConcepts(conceptsText:String, format:String): String // Supported format is JSON/XML

  /** Update existing concepts
   * @param conceptsText an input String of concepts in a format defined by the next parameter formatType
   * @param formatType format of conceptsText ( JSON or XML)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def UpdateConcepts(conceptsText:String, format:String): String

  /** RemoveConcepts take all concepts names to be removed as an Array
   * @param cocepts array of Strings where each string is name of the concept
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def RemoveConcepts(concepts:Array[String]): String

  /** Add message given messageText
   * 
   * @param messageText text of the message (as JSON/XML string as defined by next parameter formatType)
   * @param formatType format of messageText ( JSON or XML)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def AddMessage(messageText:String, formatType:String): String 

  /** Update message given messageText
   * 
   * @param messageText text of the message (as JSON/XML string as defined by next parameter formatType)
   * @param formatType format of messageText (as JSON/XML string)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def UpdateMessage(messageText:String, format:String): String

  /** Remove message with MessageName and Vesion Number
   * 
   * @param messageName Name of the given message
   * @param version   Version of the given message
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def RemoveMessage(messageName:String, version:Int): String

  /** Add model given pmmlText in XML
   * 
   * @param pmmlText text of the model (as XML string)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def AddModel(pmmlText:String): String

  /** Update model given pmmlText
   * 
   * @param pmmlText text of the model (as XML string)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def UpdateModel(pmmlText:String): String

  /** Remove model with ModelName and Vesion Number
   * 
   * @param modelName Name of the given model
   * @param version   Version of the given model
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def RemoveModel(modelName:String, version:Int): String

  /** Retrieve All available ModelDefs from Metadata Store
   *
   * @param formatType format of the return value, either JSON or XML
   * @return the ModelDef(s) either as a JSON or XML string depending on the parameter formatType
   */
  def GetAllModelDefs(formatType: String) : String

  /** Retrieve specific ModelDef(s) from Metadata Store
   *
   * @param objectName Name of the ModelDef
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * ModelDef(s) either as a JSON or XML string depending on the parameter formatType
   */
  def GetModelDef(objectName:String,formatType: String) : String

  /** Retrieve a specific ModelDef from Metadata Store
   *
   * @param objectName Name of the ModelDef
   * @param version  Version of the ModelDef
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the ModelDef either as a JSON or XML string depending on the parameter formatType
   */
  def GetModelDef( objectName:String,version:String, formatType: String) : String


  /** Retrieve All available MessageDefs from Metadata Store
   *
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the MessageDef(s) either as a JSON or XML string depending on the parameter formatType
   */
  def GetAllMessageDefs(formatType: String) : String

  /** Retrieve specific MessageDef(s) from Metadata Store
   *
   * @param objectName Name of the MessageDef
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the MessageDef(s) either as a JSON or XML string depending on the parameter formatType
   */
  def GetMessageDef(objectName:String,formatType: String) : String

  /** Retrieve a specific MessageDef from Metadata Store
   *
   * @param objectName Name of the MessageDef
   * @param version  Version of the MessageDef
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the MessageDef either as a JSON or XML string depending on the parameter formatType
   */
  def GetMessageDef( objectName:String,version:String, formatType: String) : String

  /** Retrieve All available FunctionDefs from Metadata Store
   *
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the FunctionDef(s) either as a JSON or XML string depending on the parameter formatType
   */
  def GetAllFunctionDefs(formatType: String) : String

  /** Retrieve specific FunctionDef(s) from Metadata Store
   *
   * @param objectName Name of the FunctionDef
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the FunctionDef(s) either as a JSON or XML string depending on the parameter formatType
   */
  def GetFunctionDef(objectName:String,formatType: String) : String

  /** Retrieve a specific FunctionDef from Metadata Store
   *
   * @param objectName Name of the FunctionDef
   * @param version  Version of the FunctionDef
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the FunctionDef either as a JSON or XML string depending on the parameter formatType
   */
  def GetFunctionDef( objectName:String,version:String, formatType: String) : String

  /** Retrieve All available Concepts from Metadata Store
   *
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the Concept(s) either as a JSON or XML string depending on the parameter formatType
   */
  def GetAllConcepts(formatType: String) : String

  /** Retrieve specific Concept(s) from Metadata Store
   *
   * @param objectName Name of the Concept
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the Concept(s) either as a JSON or XML string depending on the parameter formatType
   */
  def GetConcept(objectName:String, formatType: String) : String

  /** Retrieve a specific Concept from Metadata Store
   *
   * @param objectName Name of the Concept
   * @param version  Version of the Concept
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the Concept either as a JSON or XML string depending on the parameter formatType
   */
  def GetConcept(objectName:String,version: String, formatType: String) : String


  /** Retrieve All available derived concepts from Metadata Store
   *
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the Derived Concept(s) either as a JSON or XML string depending on the parameter formatType
   */
  def GetAllDerivedConcepts(formatType: String) : String


  /** Retrieve specific Derived Concept(s) from Metadata Store
   *
   * @param objectName Name of the Derived Concept
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the Derived Concept(s) either as a JSON or XML string depending on the parameter formatType
   */
  def GetDerivedConcept(objectName:String, formatType: String) : String

  /** Retrieve a specific Derived Concept from Metadata Store
   *
   * @param objectName Name of the Derived Concept
   * @param version  Version of the Derived Concept
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the Derived Concept either as a JSON or XML string depending on the parameter formatType
   */
  def GetDerivedConcept(objectName:String, version:String, formatType: String) : String

  /** Retrieves all available Types from Metadata Store
   *
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the available types as a JSON or XML string depending on the parameter formatType
   */
  def GetAllTypes(formatType: String) : String

  /** Retrieve a specific Type  from Metadata Store
   *
   * @param objectName Name of the Type
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the Type object either as a JSON or XML string depending on the parameter formatType
   */
  def GetType(objectName:String, formatType: String) : String

}
