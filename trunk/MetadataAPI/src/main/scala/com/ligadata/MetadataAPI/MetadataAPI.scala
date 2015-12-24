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

package com.ligadata.MetadataAPI

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.fasterxml.jackson.annotation.JsonFormat
import com.ligadata.Serialize.JsonSerializer

/** A class that defines the result any of the API function uniformly
 * @constructor creates a new ApiResult with a statusCode,functionName,statusDescription,resultData
 * @param statusCode status of the API call, 0 => success, non-zero => failure.
 * @param statusDescription relevant in case of non-zero status code
 * @param resultData A string value representing string, either XML or JSON format
 */
class ApiResult(var statusCode:Int, var functionName: String, var resultData: String, var description: String){
/**
 * Override toString to return ApiResult as a String
 */
  override def toString: String = {
    
    var json = ("APIResults" -> ("Status Code" -> statusCode) ~ 
                                ("Function Name" -> functionName) ~ 
                                ("Result Data"  -> resultData) ~ 
                                ("Result Description" -> description))    

    pretty(render(json))
  }
}

/**
 * A class that defines the result any of the API function - The ResultData reuturns a Complex Array of Values.
 * @param statusCode
 * @param functionName
 * @param resultData
 * @param description
 */
class ApiResultComplex (var statusCode:Int, var functionName: String, var resultData: String, var description: String){
  /**
   * Override toString to return ApiResult as a String
   */
  override def toString: String = {

    val resultArray = parse(resultData).values.asInstanceOf[List[Map[String,Any]]]

    var json = ("APIResults" -> ("Status Code" -> statusCode) ~
                                ("Function Name" -> functionName) ~
                                ("Result Data"  -> resultArray.map {nodeInfo => JsonSerializer.SerializeMapToJsonString(nodeInfo)}) ~
                                ("Result Description" -> description))

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
   * 
   * Example:
   * 
   * {{{
   * val sampleScalarTypeStr = """
   * {
   * "MetadataType" : "ScalarTypeDef",
   * "NameSpace" : "system",
   * "Name" : "my_char",
   * "TypeTypeName" : "tScalar",
   * "TypeNameSpace" : "System",
   * "TypeName" : "Char",
   * "PhysicalName" : "Char",
   * "Version" : 100,
   * "JarName" : "basetypes_2.10-0.1.0.jar",
   * "DependencyJars" : [ "metadata_2.10-1.0.jar" ],
   * "Implementation" : "com.ligadata.BaseTypes.CharImpl"
   * }
   * """
   * var apiResult = MetadataAPIImpl.AddType(sampleScalarTypeStr,"JSON")
   * var result = MetadataAPIImpl.getApiResult(apiResult)
   * println("Result as Json String => \n" + result._2)
   * }}}
   * 
   */
  def AddType(typesText:String, formatType:String): String

  /** Update existing types
   * @param typesText an input String of types in a format defined by the next parameter formatType
   * @param formatType format of typesText ( JSON or XML)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   *
   * Example:
   * 
   * {{{
   * val sampleScalarTypeStr = """
   * {
   * "MetadataType" : "ScalarTypeDef",
   * "NameSpace" : "system",
   * "Name" : "my_char",
   * "TypeTypeName" : "tScalar",
   * "TypeNameSpace" : "System",
   * "TypeName" : "Char",
   * "PhysicalName" : "Char",
   * "Version" : 101,
   * "JarName" : "basetypes_2.10-0.1.0.jar",
   * "DependencyJars" : [ "metadata_2.10-1.0.jar" ],
   * "Implementation" : "com.ligadata.BaseTypes.CharImpl"
   * }
   * """
   * var apiResult = MetadataAPIImpl.UpdateType(sampleScalarTypeStr,"JSON")
   * var result = MetadataAPIImpl.getApiResult(apiResult)
   * println("Result as Json String => \n" + result._2)
   * }}}
   * 
   */
  def UpdateType(typesText:String, formatType:String, userid: Option[String]): String

  /** Remove Type for given typeName and version
   * @param typeName name of the Type
   * @version version of the Type
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   *
   * Example:
   * 
   * {{{
   * val apiResult = MetadataAPIImpl.RemoveType(MdMgr.sysNS,"my_char",100)
   * val (statusCode,resultData) = MetadataAPIImpl.getApiResult(apiResult)
   * println("Result as Json String => \n" + resultData)
   * }}}
   * 
   */
  def RemoveType(typeNameSpace:String, typeName:String, version:Long, userid: Option[String]): String

  // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  /** Upload Jars into system. Dependency jars may need to upload first. Once we upload the jar,
   * if we retry to upload it will throw an exception.
   * @param implPath fullPathName of the jar file
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   *
   */
  def UploadJar(jarPath:String): String

  /** Add new functions 
   * @param functionsText an input String of functions in a format defined by the next parameter formatType
   * @param formatType format of functionsText ( JSON or XML)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   *
   * Example:
   * {{{
   *   val sampleFunctionStr = """
   *  {
   *  "NameSpace" : "pmml",
   *  "Name" : "my_min",
   *  "PhysicalName" : "com.ligadata.pmml.udfs.Udfs.Min",
   *  "ReturnTypeNameSpace" : "system",
   *  "ReturnTypeName" : "double",
   *  "Arguments" : [ {
   *  "ArgName" : "expr1",
   *  "ArgTypeNameSpace" : "system",
   *  "ArgTypeName" : "int"
   *  }, {
   *  "ArgName" : "expr2",
   *  "ArgTypeNameSpace" : "system",
   *  "ArgTypeName" : "double"
   *  } ],
   *  "Version" : 1,
   *  "JarName" : null,
   *  "DependantJars" : [ "basetypes_2.10-0.1.0.jar", "metadata_2.10-1.0.jar" ]
   *  }
   *"""
   *    var apiResult = MetadataAPIImpl.AddFunction(sampleFunctionStr,"JSON")
   *    var result = MetadataAPIImpl.getApiResult(apiResult)
   *    println("Result as Json String => \n" + result._2)
   *}}}
   */
  def AddFunctions(functionsText:String, formatType:String, userid: Option[String]): String

  /** Update existing functions
   * @param functionsText an input String of functions in a format defined by the next parameter formatType
   * @param formatType format of functionsText ( JSON or XML)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   *
   * Example:
   * {{{
   *   val sampleFunctionStr = """
   *  {
   *  "NameSpace" : "pmml",
   *  "Name" : "my_min",
   *  "PhysicalName" : "com.ligadata.pmml.udfs.Udfs.Min",
   *  "ReturnTypeNameSpace" : "system",
   *  "ReturnTypeName" : "double",
   *  "Arguments" : [ {
   *  "ArgName" : "expr1",
   *  "ArgTypeNameSpace" : "system",
   *  "ArgTypeName" : "int"
   *  }, {
   *  "ArgName" : "expr2",
   *  "ArgTypeNameSpace" : "system",
   *  "ArgTypeName" : "double"
   *  } ],
   *  "Version" : 1,
   *  "JarName" : null,
   *  "DependantJars" : [ "basetypes_2.10-0.1.0.jar", "metadata_2.10-1.0.jar" ]
   *  }
   *"""
   *    var apiResult = MetadataAPIImpl.UpdateFunction(sampleFunctionStr,"JSON")
   *    var result = MetadataAPIImpl.getApiResult(apiResult)
   *    println("Result as Json String => \n" + result._2)
   * }}}
   *
   */
  def UpdateFunctions(functionsText:String, formatType:String, userid: Option[String]): String

  /** Remove function for given FunctionName and Version
   * @param functionName name of the function
   * @version version of the function
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   *
   * Example:
   * {{{
   * val apiResult = MetadataAPIImpl.RemoveFunction(MdMgr.sysNS,"my_min",100)
   * val (statusCode,resultData) = MetadataAPIImpl.getApiResult(apiResult)
   * println("Result as Json String => \n" + resultData)
   *}}}
   * 
   */
  def RemoveFunction(nameSpace:String, functionName:String, version:Long, userid: Option[String]): String

  /** Add new concepts 
   * @param conceptsText an input String of concepts in a format defined by the next parameter formatType
   * @param formatType format of conceptsText ( JSON or XML)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   *
   * Example:
   *
   * {{{
   *   val sampleConceptStr = """
   *  {"Concepts" : [
   *  "NameSpace":"Ligadata",
   *  "Name":"ProviderId",
   *  "TypeNameSpace":"System",
   *  "TypeName" : "String",
   *  "Version"  : 100 ]
   *  }
   *"""
   *    var apiResult = MetadataAPIImpl.AddConcepts(sampleConceptStr,"JSON")
   *    var result = MetadataAPIImpl.getApiResult(apiResult)
   *    println("Result as Json String => \n" + result._2)
   *}}}
   * 
   */
  def AddConcepts(conceptsText:String, format:String, userid: Option[String]): String // Supported format is JSON/XML

  /** Update existing concepts
   * @param conceptsText an input String of concepts in a format defined by the next parameter formatType
   * @param formatType format of conceptsText ( JSON or XML)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   *
   * Example:
   *
   * {{{
   *   val sampleConceptStr = """
   *  {"Concepts" : [
   *  "NameSpace":"Ligadata",
   *  "Name":"ProviderId",
   *  "TypeNameSpace":"System",
   *  "TypeName" : "String",
   *  "Version"  : 101 ]
   *  }
   *"""
   *    var apiResult = MetadataAPIImpl.UpdateConcepts(sampleConceptStr,"JSON")
   *    var result = MetadataAPIImpl.getApiResult(apiResult)
   *    println("Result as Json String => \n" + result._2)
   * 
   *}}}
   * 
   */
  def UpdateConcepts(conceptsText:String, format:String, userid: Option[String]): String

  /** RemoveConcepts take all concepts names to be removed as an Array
   * @param cocepts array of Strings where each string is name of the concept
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   *
   * Example:
   * {{{
   * val apiResult = MetadataAPIImpl.RemoveConcepts(Array("Ligadata.ProviderId.100"))
   * val (statusCode,resultData) = MetadataAPIImpl.getApiResult(apiResult)
   * println("Result as Json String => \n" + resultData)
   *}}}
   * 
   */
  def RemoveConcepts(concepts:Array[String], userid: Option[String]): String

  /** Add message given messageText
   * 
   * @param messageText text of the message (as JSON/XML string as defined by next parameter formatType)
   * @param formatType format of messageText ( JSON or XML)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   *
   * Example
   * 
   * {{{
   * var apiResult = MetadataAPIImpl.AddMessage(msgStr,"JSON"))
   * var result = MetadataAPIImpl.getApiResult(apiResult)
   * println("Result as Json String => \n" + result._2)
   * }}}
   */
  def AddMessage(messageText:String, formatType:String, userid: Option[String]): String 

  /** Update message given messageText
   * 
   * @param messageText text of the message (as JSON/XML string as defined by next parameter formatType)
   * @param formatType format of messageText (as JSON/XML string)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def UpdateMessage(messageText:String, format:String, userid: Option[String]): String

  /** Remove message with MessageName and Vesion Number
   * 
   * @param messageName Name of the given message
   * @param version   Version of the given message
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def RemoveMessage(messageName:String, version:Long, userid: Option[String]): String

  /** Add model given pmmlText in XML
   * 
   * @param pmmlText text of the model (as XML string)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */


  /** Add container given containerText
   * 
   * @param containerText text of the container (as JSON/XML string as defined by next parameter formatType)
   * @param formatType format of containerText ( JSON or XML)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   *
   * Example
   * 
   * {{{
   * var apiResult = MetadataAPIImpl.AddContainer(msgStr,"JSON"))
   * var result = MetadataAPIImpl.getApiResult(apiResult)
   * println("Result as Json String => \n" + result._2)
   * }}}
   */
  def AddContainer(containerText:String, formatType:String, userid: Option[String]): String 

  /** Update container given containerText
   * 
   * @param containerText text of the container (as JSON/XML string as defined by next parameter formatType)
   * @param formatType format of containerText (as JSON/XML string)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def UpdateContainer(containerText:String, format:String, userid: Option[String]): String

  /** Remove container with ContainerName and Vesion Number
   * 
   * @param containerName Name of the given container
   * @param version   Version of the given container
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def RemoveContainer(containerName:String, version:Long, userid: Option[String]): String

  /** Add model given pmmlText in XML
   * 
   * @param pmmlText text of the model (as XML string)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */

  def AddModel(pmmlText:String, userid: Option[String]): String

  /** Update model given pmmlText
   * 
   * @param pmmlText text of the model (as XML string)
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def UpdateModel(pmmlText:String, userid: Option[String]): String

  /** Remove model with ModelName and Vesion Number
   * 
   * @param modelName Name of the given model
   * @param version   Version of the given model
   * @return the result as a JSON String of object ApiResult where ApiResult.statusCode
   * indicates success or failure of operation: 0 for success, Non-zero for failure. The Value of
   * ApiResult.statusDescription and ApiResult.resultData indicate the nature of the error in case of failure
   */
  def RemoveModel(modelName:String, version:Long, userid: Option[String]): String

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


  /** Retrieve a specific MessageDef from Metadata Store
   *
   * @param objectNameSpace NameSpace of the MessageDef
   * @param objectName Name of the MessageDef
   * @param version  Version of the MessageDef
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the MessageDef either as a JSON or XML string depending on the parameter formatType
   */
  def GetMessageDef(objectNameSpace:String,objectName:String,version:String, formatType: String, userid: Option[String]) : String

  /** Retrieve All available ContainerDefs from Metadata Store
   *
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the ContainerDef(s) either as a JSON or XML string depending on the parameter formatType
   */
  def GetAllContainerDefs(formatType: String) : String

  /** Retrieve specific ContainerDef(s) from Metadata Store
   *
   * @param objectName Name of the ContainerDef
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the ContainerDef(s) either as a JSON or XML string depending on the parameter formatType
   */
  def GetContainerDef(objectName:String,formatType: String) : String

  /** Retrieve a specific ContainerDef from Metadata Store
   *
   * @param objectName Name of the ContainerDef
   * @param version  Version of the ContainerDef
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the ContainerDef either as a JSON or XML string depending on the parameter formatType
   */
  def GetContainerDef( objectName:String,version:String, formatType: String) : String

  /** Retrieve a specific ContainerDef from Metadata Store
   *
   * @param objectNameSpace NameSpace of the ContainerDef
   * @param objectName Name of the ContainerDef
   * @param version  Version of the ContainerDef
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the ContainerDef either as a JSON or XML string depending on the parameter formatType
   */
  def GetContainerDef(objectNameSpace:String,objectName:String,version:String, formatType: String, userid: Option[String]) : String

  /** Retrieve All available FunctionDefs from Metadata Store. Answer the count and a string representation
   *  of them.
   *
   * @param formatType format of the return value, either JSON or XML
   * @return the function count and the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the FunctionDef(s) either as a JSON or XML string depending on the parameter formatType as a Tuple2[Int,String]
   */

  def GetAllFunctionDefs(formatType: String, userid: Option[String]) : (Int,String)

  /** Retrieve specific FunctionDef(s) from Metadata Store
   *
   * @param objectName Name of the FunctionDef
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the FunctionDef(s) either as a JSON or XML string depending on the parameter formatType
   */
  def GetFunctionDef(objectName:String,formatType: String, userid: Option[String]) : String

  /** Retrieve a specific FunctionDef from Metadata Store
   *
   * @param objectName Name of the FunctionDef
   * @param version  Version of the FunctionDef
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the FunctionDef either as a JSON or XML string depending on the parameter formatType
   */
  def GetFunctionDef( objectName:String,version:String, formatType: String, userid: Option[String]) : String

  /** Retrieve All available Concepts from Metadata Store
   *
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the Concept(s) either as a JSON or XML string depending on the parameter formatType
   */
  def GetAllConcepts(formatType: String, userid: Option[String]) : String

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
  def GetAllTypes(formatType: String, userid: Option[String]) : String

  /** Retrieve a specific Type  from Metadata Store
   *
   * @param objectName Name of the Type
   * @param formatType format of the return value, either JSON or XML
   * @return the result as a JSON String of object ApiResult where ApiResult.resultData contains
   * the Type object either as a JSON or XML string depending on the parameter formatType
   */
  def GetType(objectName:String, formatType: String) : String
  
    /**
   *  getHealthCheck - will return all the health-check information for the nodeId specified. 
   *  @parm - nodeId: String - if no parameter specified, return health-check for all nodes 
   */
  def getHealthCheck(nodeId: String): String 
}
