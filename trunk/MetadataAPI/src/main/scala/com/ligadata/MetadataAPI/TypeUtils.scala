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

import java.util.Properties
import java.io._
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.text.ParseException
import scala.Enumeration
import scala.io._
import scala.collection.mutable.ArrayBuffer

import scala.collection.mutable._
import scala.reflect.runtime.{ universe => ru }

import com.ligadata.kamanja.metadata.ObjType._
import com.ligadata.kamanja.metadata._
import com.ligadata.kamanja.metadata.MdMgr._

import com.ligadata.kamanja.metadataload.MetadataLoad

// import com.ligadata.keyvaluestore._
import com.ligadata.HeartBeat.HeartBeatUtil
import com.ligadata.StorageBase.{ DataStore, Transaction }
import com.ligadata.KvBase.{ Key, Value, TimeRange }

import scala.util.parsing.json.JSON
import scala.util.parsing.json.{ JSONObject, JSONArray }
import scala.collection.immutable.Map
import scala.collection.immutable.HashMap
import scala.collection.mutable.HashMap

import com.google.common.base.Throwables

import com.ligadata.messagedef._
import com.ligadata.Exceptions._

import scala.xml.XML
import org.apache.logging.log4j._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import com.ligadata.ZooKeeper._
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.CreateMode

import com.ligadata.keyvaluestore._
import com.ligadata.Serialize._
import com.ligadata.Utils._
import com.ligadata.AuditAdapterInfo._
import com.ligadata.SecurityAdapterInfo.SecurityAdapter
import com.ligadata.keyvaluestore.KeyValueManager
import com.ligadata.Exceptions.StackTrace

import java.util.Date

import org.json4s.jackson.Serialization

// The implementation class
object TypeUtils {

  lazy val sysNS = "System"
  // system name space
  lazy val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)
  lazy val serializer = SerializerManager.GetSerializer("kryo")

  def AddType(typeText: String, format: String): String = {
    try {
      logger.debug("Parsing type object given as Json String..")
      val typ = JsonSerializer.parseType(typeText, "JSON")
      MetadataAPIImpl.SaveObject(typ, MdMgr.GetMdMgr)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddType", typeText, ErrorCodeConstants.Add_Type_Successful)
      apiResult.toString()
    } catch {
      case e: AlreadyExistsException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.warn("Failed to add the type, json => " + typeText + "\nError => " + e.getMessage() + "\nStackTrace:" + stackTrace)
        val apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddType", typeText, "Error :" + e.toString() + ErrorCodeConstants.Add_Type_Failed + "\nStackTrace:" + stackTrace)
        apiResult.toString()
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:" + stackTrace)
        val apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddType", typeText, "Error :" + e.toString() + ErrorCodeConstants.Add_Type_Failed + "\nStackTrace:" + stackTrace)
        apiResult.toString()
      }
    }
  }

  def DumpTypeDef(typeDef: ScalarTypeDef) {
    logger.debug("NameSpace => " + typeDef.nameSpace)
    logger.debug("Name => " + typeDef.name)
    logger.debug("Version => " + MdMgr.Pad0s2Version(typeDef.ver))
    logger.debug("Description => " + typeDef.description)
    logger.debug("Implementation class name => " + typeDef.implementationNm)
    logger.debug("Implementation jar name => " + typeDef.jarName)
  }

  def AddType(typeDef: BaseTypeDef): String = {
    val dispkey = typeDef.FullName + "." + MdMgr.Pad0s2Version(typeDef.Version)
    try {
      var key = typeDef.FullNameWithVer
      var value = JsonSerializer.SerializeObjectToJson(typeDef);
      logger.debug("key => " + key + ",value =>" + value);
      MetadataAPIImpl.SaveObject(typeDef, MdMgr.GetMdMgr)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddType", null, ErrorCodeConstants.Add_Type_Successful + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        val apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddType" , null, "Error :" + e.toString() + ErrorCodeConstants.Add_Type_Failed+ ":" + dispkey+"\nStackTrace:"+stackTrace)
        apiResult.toString()
      }
    }
  }

  def AddTypes(typesText: String, format: String, userid: Option[String]): String = {
    try {
      if (format != "JSON") {
        var apiResult = new ApiResult(ErrorCodeConstants.Not_Implemented_Yet, "AddTypes", typesText, ErrorCodeConstants.Not_Implemented_Yet_Msg)
        apiResult.toString()
      } else {
        var typeList = JsonSerializer.parseTypeList(typesText, "JSON")
        if (typeList.length > 0) {
          logger.debug("Found " + typeList.length + " type objects ")
          typeList.foreach(typ => {
            MetadataAPIImpl.logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.INSERTOBJECT, typesText, AuditConstants.SUCCESS, "", typ.FullNameWithVer)
            MetadataAPIImpl.SaveObject(typ, MdMgr.GetMdMgr)
            logger.debug("Type object name => " + typ.FullName + "." + MdMgr.Pad0s2Version(typ.Version))
          })
          /** Only report the ones actually saved... if there were others, they are in the log as "fail to add" due most likely to already being defined */
          val typesSavedAsJson: String = JsonSerializer.SerializeObjectListToJson(typeList)
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddTypes", typesText, ErrorCodeConstants.Add_Type_Successful)
          apiResult.toString()
        } else {
          var apiResult = new ApiResult(ErrorCodeConstants.Warning, "AddTypes", null, "All supplied types are already available. No types to add.")
          apiResult.toString()
        }
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:" + stackTrace)
        val apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddTypes", typesText, "Error :" + e.toString() + ErrorCodeConstants.Add_Type_Failed + "\nStackTrace:" + stackTrace)
        apiResult.toString()
      }
    }
  }

  // Remove type for given TypeName and Version
  def RemoveType(typeNameSpace: String, typeName: String, version: Long, userid: Option[String]): String = {
    val key = typeNameSpace + "." + typeName + "." + version
    val dispkey = typeNameSpace + "." + typeName + "." + MdMgr.Pad0s2Version(version)
    if (userid != None) MetadataAPIImpl.logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.DELETEOBJECT, AuditConstants.TYPE, AuditConstants.SUCCESS, "", key)
    try {
      val typ = MdMgr.GetMdMgr.Type(typeNameSpace, typeName, version, true)
      typ match {
        case None =>
          None
          logger.debug("Type " + dispkey + " is not found in the cache ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveType", null, ErrorCodeConstants.Remove_Type_Not_Found + ":" + dispkey)
          apiResult.toString()
        case Some(ts) =>
          MetadataAPIImpl.DeleteObject(ts.asInstanceOf[BaseElemDef])
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveType", null, ErrorCodeConstants.Remove_Type_Successful + ":" + dispkey)
          apiResult.toString()
      }
    } catch {
      case e: ObjectNolongerExistsException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveType", null, "Error: " + e.toString() + ErrorCodeConstants.Remove_Type_Failed + ":" + dispkey+"\nStackTrace:"+stackTrace)
        apiResult.toString()
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveType", null, "Error: " + e.toString() + ErrorCodeConstants.Remove_Type_Failed + ":" + dispkey+"\nStackTrace:"+stackTrace)
        apiResult.toString()
      }
    }
  }

  def UpdateType(typeJson: String, format: String, userid: Option[String]): String = {
    implicit val jsonFormats: Formats = DefaultFormats
    val typeDef = JsonSerializer.parseType(typeJson, "JSON")
    MetadataAPIImpl.logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.UPDATEOBJECT, typeJson, AuditConstants.SUCCESS, "", typeDef.FullNameWithVer)

    val key = typeDef.nameSpace + "." + typeDef.name + "." + typeDef.Version
    val dispkey = typeDef.nameSpace + "." + typeDef.name + "." + MdMgr.Pad0s2Version(typeDef.Version)
    var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateType", null, ErrorCodeConstants.Update_Type_Internal_Error + ":" + dispkey)
    try {
      val fName = MdMgr.MkFullName(typeDef.nameSpace, typeDef.name)
      val latestType = MdMgr.GetMdMgr.Types(typeDef.nameSpace, typeDef.name, true, true)
      latestType match {
        case None =>
          None
          logger.debug("No types with the name " + fName + " are found ")
          apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateType", null, ErrorCodeConstants.Update_Type_Failed_Not_Found + ":" + dispkey)
        case Some(ts) =>
          val tsa = ts.toArray
          logger.debug("Found " + tsa.length + " types ")
          if (tsa.length > 1) {
            apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateType", null, ErrorCodeConstants.Update_Type_Failed_More_Than_One_Found + ":" + dispkey)
          } else {
            val latestVersion = tsa(0)
            if (latestVersion.ver > typeDef.ver) {
              RemoveType(latestVersion.nameSpace, latestVersion.name, latestVersion.ver, userid)
              AddType(typeDef)
              apiResult = new ApiResult(ErrorCodeConstants.Success, "UpdateType", null, ErrorCodeConstants.Update_Type_Successful + ":" + key)
            } else {
              apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateType", null, ErrorCodeConstants.Update_Type_Failed_Higher_Version_Required + ":" + dispkey)
            }
          }
      }
      apiResult.toString()
    } catch {
      case e: MappingException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Failed to parse the type, json => " + typeJson + ",Error => " + e.getMessage()+"\nStackTrace:"+stackTrace)
        apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateType", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Type_Failed + ":" + dispkey)
        apiResult.toString()
      }
      case e: AlreadyExistsException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Failed to update the type, json => " + typeJson + ",Error => " + e.getMessage()+"\nStackTrace:"+stackTrace)
        apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateType", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Type_Failed + ":" + dispkey)
        apiResult.toString()
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Failed to up the type, json => " + typeJson + ",Error => " + e.getMessage()+"\nStackTrace:"+stackTrace)
        apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateType", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Type_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  // All available types(format JSON or XML) as a String
  def GetAllTypes(formatType: String, userid: Option[String]): String = {
    try {
      if (userid != None) MetadataAPIImpl.logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.TYPE, AuditConstants.SUCCESS, "", "ALL")
      val typeDefs = MdMgr.GetMdMgr.Types(true, true)
      typeDefs match {
        case None =>
          None
          logger.debug("No typeDefs found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllTypes", null, ErrorCodeConstants.Get_All_Types_Failed_Not_Available)
          apiResult.toString()
        case Some(ts) =>
          val tsa = ts.toArray
          logger.debug("Found " + tsa.length + " types ")
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllTypes", JsonSerializer.SerializeObjectListToJson("Types", tsa), ErrorCodeConstants.Get_All_Types_Successful)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllTypes", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Types_Failed)
        apiResult.toString()
      }
    }
  }

  def GetAllTypesFromCache(active: Boolean, userid: Option[String]): Array[String] = {
    var typeList: Array[String] = new Array[String](0)
    MetadataAPIImpl.logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETKEYS, AuditConstants.TYPE, AuditConstants.SUCCESS, "", AuditConstants.TYPE)
    try {
      val contDefs = MdMgr.GetMdMgr.Types(active, true)
      contDefs match {
        case None =>
          None
          logger.debug("No Types found ")
          typeList
        case Some(ms) =>
          val msa = ms.toArray
          val contCount = msa.length
          typeList = new Array[String](contCount)
          for (i <- 0 to contCount - 1) {
            typeList(i) = msa(i).FullName + "." + MdMgr.Pad0s2Version(msa(i).Version)
          }
          typeList
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        throw new UnexpectedMetadataAPIException("Failed to fetch all the types:" + e.toString+"\nStackTrace:"+stackTrace)
      }
    }
  }

  // Get types for a given name
  def GetType(objectName: String, formatType: String): String = {
    try {
      val typeDefs = MdMgr.GetMdMgr.Types(MdMgr.sysNS, objectName, false, false)
      typeDefs match {
        case None =>
          None
          logger.debug("No typeDefs found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetType", null, ErrorCodeConstants.Get_Type_Failed_Not_Available + ":" + objectName)
          apiResult.toString()
        case Some(ts) =>
          val tsa = ts.toArray
          logger.debug("Found " + tsa.length + " types ")
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetType", JsonSerializer.SerializeObjectListToJson("Types", tsa), ErrorCodeConstants.Get_Type_Successful + ":" + objectName)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:" + stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetType", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Type_Failed + ":" + objectName)
        apiResult.toString()
      }
    }
  }

  def GetTypeDef(nameSpace: String, objectName: String, formatType: String, version: String, userid: Option[String]): String = {
    var key = nameSpace + "." + objectName + "." + version.toLong
    val dispkey = nameSpace + "." + objectName + "." + MdMgr.Pad0s2Version(version.toLong)
    MetadataAPIImpl.logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.TYPE, AuditConstants.SUCCESS, "", dispkey)
    try {
      val typeDefs = MdMgr.GetMdMgr.Types(nameSpace, objectName, false, false)
      typeDefs match {
        case None =>
          None
          logger.debug("No typeDefs found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetTypeDef", null, ErrorCodeConstants.Get_Type_Def_Failed_Not_Available + ":" + dispkey)
          apiResult.toString()
        case Some(ts) =>
          val tsa = ts.toArray
          logger.debug("Found " + tsa.length + " types ")
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetTypeDef", JsonSerializer.SerializeObjectListToJson("Types", tsa), ErrorCodeConstants.Get_Type_Def_Successful + ":" + dispkey)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetTypeDef", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Type_Def_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def GetType(nameSpace: String, objectName: String, version: String, formatType: String, userid: Option[String]): Option[BaseTypeDef] = {
    try {
      val dispkey = nameSpace + "." + objectName + "." + version
      if (userid != None) MetadataAPIImpl.logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.TYPE, AuditConstants.SUCCESS, "", dispkey)
      val typeDefs = MdMgr.GetMdMgr.Types(nameSpace, objectName, false, false)
      typeDefs match {
        case None => None
        case Some(ts) =>
          val tsa = ts.toArray.filter(t => { t.ver == version.toLong })
          tsa.length match {
            case 0 => None
            case 1 => Some(tsa(0))
            case _ => {
              logger.debug("Found More than one type, Doesn't make sesne")
              Some(tsa(0))
            }
          }
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Failed to fetch the typeDefs:" + e.getMessage()+"\nStackTrace:"+stackTrace)
        None
      }
    }
  }

  // All available types(format JSON or XML) as a String
  def GetAllTypesByObjType(formatType: String, objType: String): String = {
    logger.debug("Find the types of type " + objType)
    try {
      val typeDefs = MdMgr.GetMdMgr.Types(true, true)
      typeDefs match {
        case None =>
          None
          logger.debug("No typeDefs found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllTypesByObjType", null, ErrorCodeConstants.Get_All_Types_Failed_Not_Available + ":" + objType)
          apiResult.toString()
        case Some(ts) =>
          val tsa = ts.toArray.filter(t => { t.getClass.getName == objType })
          if (tsa.length == 0) {
            var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllTypesByObjType", null, ErrorCodeConstants.Get_All_Types_Failed_Not_Available + ":" + objType)
            apiResult.toString()
          } else {
            var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllTypesByObjType", JsonSerializer.SerializeObjectListToJson("Types", tsa), ErrorCodeConstants.Get_All_Types_Successful + ":" + objType)
            apiResult.toString()
          }
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllTypesByObjType", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Types_Failed + ":" + objType)
        apiResult.toString()
      }
    }
  }

  def IsTypeAlreadyExists(typeDef: BaseTypeDef): Boolean = {
    try {
      var key = typeDef.nameSpace + "." + typeDef.name + "." + typeDef.ver
      val dispkey = typeDef.nameSpace + "." + typeDef.name + "." + MdMgr.Pad0s2Version(typeDef.ver)
      val o = MdMgr.GetMdMgr.Type(typeDef.nameSpace,
        typeDef.name,
        typeDef.ver,
        false)
      o match {
        case None =>
          None
          logger.debug("Type not in the cache => " + key)
          return false;
        case Some(m) =>
          logger.debug("Type found => " + m.asInstanceOf[BaseTypeDef].FullName + "." + MdMgr.Pad0s2Version(m.asInstanceOf[BaseTypeDef].ver))
          return true
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        throw new UnexpectedMetadataAPIException(e.getMessage()+"\nStackTrace:"+stackTrace)
      }
    }
  }
}
