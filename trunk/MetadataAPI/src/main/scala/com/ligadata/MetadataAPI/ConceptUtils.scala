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
object ConceptUtils {

  lazy val sysNS = "System"
  // system name space
  lazy val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)
  lazy val serializer = SerializerManager.GetSerializer("kryo")

  def DumpAttributeDef(attrDef: AttributeDef) {
    logger.debug("NameSpace => " + attrDef.nameSpace)
    logger.debug("Name => " + attrDef.name)
    logger.debug("Type => " + attrDef.typeString)
  }

  def AddConcept(attributeDef: BaseAttributeDef): String = {
    val key = attributeDef.FullNameWithVer
    val dispkey = attributeDef.FullName + "." + MdMgr.Pad0s2Version(attributeDef.Version)
    try {
      MetadataAPIImpl.SaveObject(attributeDef, MdMgr.GetMdMgr)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddConcept", null, ErrorCodeConstants.Add_Concept_Successful + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Concept_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def RemoveConcept(concept: AttributeDef): String = {
    var key = concept.nameSpace + ":" + concept.name
    val dispkey = key // Not looking version at this moment
    try {
      MetadataAPIImpl.DeleteObject(concept)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveConcept", null, ErrorCodeConstants.Remove_Concept_Successful + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Concept_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def RemoveConcept(key: String, userid: Option[String]): String = {
    try {
      if (userid != None) MetadataAPIImpl.logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.DELETEOBJECT, AuditConstants.FUNCTION, AuditConstants.SUCCESS, "", key)
      val c = MdMgr.GetMdMgr.Attributes(key, false, false)
      c match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveConcept", null, ErrorCodeConstants.Remove_Concept_Failed_Not_Available + ":" + key)
          apiResult.toString()
        case Some(cs) =>
          val conceptArray = cs.toArray
          conceptArray.foreach(concept => {
            MetadataAPIImpl.DeleteObject(concept)
          })
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveConcept", null, ErrorCodeConstants.Remove_Concept_Successful + ":" + key) //JsonSerializer.SerializeObjectListToJson(conceptArray))
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Concept_Failed + ":" + key)
        apiResult.toString()
      }
    }
  }

  def RemoveConcept(nameSpace: String, name: String, version: Long, userid: Option[String]): String = {
    val dispkey = nameSpace + "." + name + "." + MdMgr.Pad0s2Version(version)
    if (userid != None) MetadataAPIImpl.logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.DELETEOBJECT, AuditConstants.CONCEPT, AuditConstants.SUCCESS, "", dispkey)
    try {
      val c = MdMgr.GetMdMgr.Attribute(nameSpace, name, version, true)
      c match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveConcept", null, ErrorCodeConstants.Remove_Concept_Failed_Not_Available + ":" + dispkey)
          apiResult.toString()
        case Some(cs) =>
          val concept = cs.asInstanceOf[AttributeDef]
          MetadataAPIImpl.DeleteObject(concept)
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveConcept", null, ErrorCodeConstants.Remove_Concept_Successful + ":" + dispkey) //JsonSerializer.SerializeObjectListToJson(concept))
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:" + stackTrace)
        val apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Concept_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  // This function is not implemented yet. Currently it does nothing.
  // Need to discuss further whether we have a requirement for DerivedConcept object
  def AddDerivedConcept(conceptsText: String, format: String): String = {
    try {
      if (format != "JSON") {
        var apiResult = new ApiResult(ErrorCodeConstants.Not_Implemented_Yet, "AddDerivedConcept", null, ErrorCodeConstants.Not_Implemented_Yet_Msg + ":" + conceptsText + ".Format not JSON.")
        apiResult.toString()
      } else {
        var concept = JsonSerializer.parseDerivedConcept(conceptsText, format)
	// MetadataAPIImpl.SaveObject(concept, MdMgr.GetMdMgr)
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddDerivedConcept", null, ErrorCodeConstants.Add_Concept_Successful + ":" + conceptsText)
        apiResult.toString()
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:" + stackTrace)
        val apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddDerivedConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Concept_Failed + ":" + conceptsText)
        apiResult.toString()
      }
    }
  }

  def AddConcepts(conceptsText: String, format: String, userid: Option[String]): String = {
    try {
      if (format != "JSON") {
        var apiResult = new ApiResult(ErrorCodeConstants.Not_Implemented_Yet, "AddConcepts", null, ErrorCodeConstants.Not_Implemented_Yet_Msg + ":" + conceptsText + ".Format not JSON.")
        apiResult.toString()
      } else {
        var conceptList = JsonSerializer.parseConceptList(conceptsText, format)
        conceptList.foreach(concept => {
          //logger.debug("Save concept object " + JsonSerializer.SerializeObjectToJson(concept))
          MetadataAPIImpl.logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.INSERTOBJECT, conceptsText, AuditConstants.SUCCESS, "", concept.FullNameWithVer)
          MetadataAPIImpl.SaveObject(concept, MdMgr.GetMdMgr)
        })
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "AddConcepts", null, ErrorCodeConstants.Add_Concept_Successful + ":" + conceptsText)
        apiResult.toString()
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "AddConcepts", null, "Error :" + e.toString() + ErrorCodeConstants.Add_Concept_Failed + ":" + conceptsText)
        apiResult.toString()
      }
    }
  }

  def IsConceptAlreadyExists(attrDef: BaseAttributeDef): Boolean = {
    try {
      var key = attrDef.nameSpace + "." + attrDef.name + "." + attrDef.ver
      val dispkey = attrDef.nameSpace + "." + attrDef.name + "." + MdMgr.Pad0s2Version(attrDef.ver)
      val o = MdMgr.GetMdMgr.Attribute(attrDef.nameSpace,
        attrDef.name,
        attrDef.ver,
        false)
      o match {
        case None =>
          None
          logger.debug("concept not in the cache => " + dispkey)
          return false;
        case Some(m) =>
          logger.debug("concept found => " + m.asInstanceOf[AttributeDef].FullName + "." + MdMgr.Pad0s2Version(m.asInstanceOf[AttributeDef].ver))
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

  def UpdateConcept(concept: BaseAttributeDef): String = {
    val key = concept.FullNameWithVer
    val dispkey = concept.FullName + "." + MdMgr.Pad0s2Version(concept.Version)
    try {
      if (IsConceptAlreadyExists(concept)) {
        concept.ver = concept.ver + 1
      }
      AddConcept(concept)
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "UpdateConcept", null, ErrorCodeConstants.Update_Concept_Successful + ":" + dispkey)
      apiResult.toString()
    } catch {
      case e: AlreadyExistsException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Failed to update the concept, key => " + key + ",Error => " + e.getMessage()+"\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Concept_Failed + ":" + dispkey)
        apiResult.toString()
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Failed to update the concept, key => " + key + ",Error => " + e.getMessage()+"\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Concept_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  def UpdateConcepts(conceptsText: String, format: String, userid: Option[String]): String = {
    try {
      if (format != "JSON") {
        var apiResult = new ApiResult(ErrorCodeConstants.Not_Implemented_Yet, "UpdateConcepts", null, ErrorCodeConstants.Not_Implemented_Yet_Msg + ":" + conceptsText + ".Format not JSON.")
        apiResult.toString()
      } else {
        var conceptList = JsonSerializer.parseConceptList(conceptsText, "JSON")
        conceptList.foreach(concept => {
          MetadataAPIImpl.logAuditRec(userid, Some(AuditConstants.WRITE), AuditConstants.UPDATEOBJECT, conceptsText, AuditConstants.SUCCESS, "", concept.FullNameWithVer)
          UpdateConcept(concept)
        })
        var apiResult = new ApiResult(ErrorCodeConstants.Success, "UpdateConcepts", null, ErrorCodeConstants.Update_Concept_Successful + ":" + conceptsText)
        apiResult.toString()
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "UpdateConcepts", null, "Error :" + e.toString() + ErrorCodeConstants.Update_Concept_Failed + ":" + conceptsText)
        apiResult.toString()
      }
    }
  }

  // RemoveConcepts take all concepts names to be removed as an Array
  def RemoveConcepts(concepts: Array[String], userid: Option[String]): String = {
    val json = ("ConceptList" -> concepts.toList)
    val jsonStr = pretty(render(json))
    //  MetadataAPIImpl.logAuditRec(userid,Some(AuditConstants.WRITE),AuditConstants.UPDATEOBJECT,AuditConstants.CONCEPT,AuditConstants.SUCCESS,"",concepts.mkString(",")) 
    try {
      concepts.foreach(c => {
        RemoveConcept(c, None)
      })
      var apiResult = new ApiResult(ErrorCodeConstants.Success, "RemoveConcepts", null, ErrorCodeConstants.Remove_Concept_Successful + ":" + jsonStr)
      apiResult.toString()
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "RemoveConcepts", null, "Error :" + e.toString() + ErrorCodeConstants.Remove_Concept_Failed + ":" + jsonStr)
        apiResult.toString()
      }
    }
  }

  // All available concepts as a String
  def GetAllConcepts(formatType: String, userid: Option[String]): String = {
    try {
      if (userid != None) MetadataAPIImpl.logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.CONCEPT, AuditConstants.SUCCESS, "", "ALL")
      val concepts = MdMgr.GetMdMgr.Attributes(true, true)
      concepts match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllConcepts", null, ErrorCodeConstants.Get_All_Concepts_Failed_Not_Available)
          apiResult.toString()
        case Some(cs) =>
          val csa = cs.toArray
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllConcepts", JsonSerializer.SerializeObjectListToJson("Concepts", csa), ErrorCodeConstants.Get_All_Concepts_Successful)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllConcepts", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Concepts_Failed)
        apiResult.toString()
      }
    }
  }

  def GetAllConceptsFromCache(active: Boolean, userid: Option[String]): Array[String] = {
    var conceptList: Array[String] = new Array[String](0)
    MetadataAPIImpl.logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETKEYS, AuditConstants.CONCEPT, AuditConstants.SUCCESS, "", AuditConstants.CONCEPT)
    try {
      val contDefs = MdMgr.GetMdMgr.Attributes(active, true)
      contDefs match {
        case None =>
          None
          logger.debug("No Concepts found ")
          conceptList
        case Some(ms) =>
          val msa = ms.toArray
          val contCount = msa.length
          conceptList = new Array[String](contCount)
          for (i <- 0 to contCount - 1) {
            conceptList(i) = msa(i).FullName + "." + MdMgr.Pad0s2Version(msa(i).Version)
          }
          conceptList
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        throw new UnexpectedMetadataAPIException("Failed to fetch all the concepts:" + e.toString+"\nStackTrace:"+stackTrace)
      }
    }
  }


  // A single concept as a string using name and version as the key
  def GetConcept(nameSpace: String, objectName: String, version: String, formatType: String): String = {
    var key = nameSpace + "." + objectName + "." + version.toLong
    val dispkey = nameSpace + "." + objectName + "." + MdMgr.Pad0s2Version(version.toLong)
    try {
      val concept = MdMgr.GetMdMgr.Attribute(nameSpace, objectName, version.toLong, false)
      concept match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetConcept", null, ErrorCodeConstants.Get_Concept_Failed_Not_Available)
          apiResult.toString()
        case Some(cs) =>
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetConcept", JsonSerializer.SerializeObjectToJson(cs), ErrorCodeConstants.Get_Concept_Successful + ":" + dispkey)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Concept_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }

  // A single concept as a string using name and version as the key
  def GetConcept(objectName: String, version: String, formatType: String): String = {
    GetConcept(MdMgr.sysNS, objectName, version, formatType)
  }

  // A single concept as a string using name and version as the key
  def GetConceptDef(nameSpace: String, objectName: String, formatType: String, version: String, userid: Option[String]): String = {
    MetadataAPIImpl.logAuditRec(userid, Some(AuditConstants.READ), AuditConstants.GETOBJECT, AuditConstants.CONCEPT, AuditConstants.SUCCESS, "", nameSpace + "." + objectName + "." + version)
    GetConcept(nameSpace, objectName, version, formatType)
  }

  // A list of concept(s) as a string using name 
  def GetConcept(objectName: String, formatType: String): String = {
    try {
      val concepts = MdMgr.GetMdMgr.Attributes(MdMgr.sysNS, objectName, false, false)
      concepts match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetConcept", null, ErrorCodeConstants.Get_Concept_Failed_Not_Available + ":" + objectName)
          apiResult.toString()
        case Some(cs) =>
          val csa = cs.toArray
          var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetConcept", JsonSerializer.SerializeObjectListToJson("Concepts", csa), ErrorCodeConstants.Get_Concept_Successful + ":" + objectName)
          apiResult.toString()
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Concept_Failed + ":" + objectName)
        apiResult.toString()
      }
    }
  }

  // All available derived concepts(format JSON or XML) as a String
  def GetAllDerivedConcepts(formatType: String): String = {
    try {
      val concepts = MdMgr.GetMdMgr.Attributes(true, true)
      concepts match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllDerivedConcepts", null, ErrorCodeConstants.Get_All_Derived_Concepts_Failed_Not_Available)
          apiResult.toString()
        case Some(cs) =>
          val csa = cs.toArray.filter(t => { t.getClass.getName.contains("DerivedAttributeDef") })
          if (csa.length > 0) {
            var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetAllDerivedConcepts", JsonSerializer.SerializeObjectListToJson("Concepts", csa), ErrorCodeConstants.Get_All_Derived_Concepts_Successful)
            apiResult.toString()
          } else {
            var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllDerivedConcepts", null, ErrorCodeConstants.Get_All_Derived_Concepts_Failed_Not_Available)
            apiResult.toString()
          }
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetAllDerivedConcepts", null, "Error :" + e.toString() + ErrorCodeConstants.Get_All_Derived_Concepts_Failed)
        apiResult.toString()
      }
    }
  }
  // A derived concept(format JSON or XML) as a string using name(without version) as the key
  def GetDerivedConcept(objectName: String, formatType: String): String = {
    try {
      val concepts = MdMgr.GetMdMgr.Attributes(MdMgr.sysNS, objectName, false, false)
      concepts match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetDerivedConcept", null, ErrorCodeConstants.Get_Derived_Concept_Failed_Not_Available + ":" + objectName)
          apiResult.toString()
        case Some(cs) =>
          val csa = cs.toArray.filter(t => { t.getClass.getName.contains("DerivedAttributeDef") })
          if (csa.length > 0) {
            var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetDerivedConcept", JsonSerializer.SerializeObjectListToJson("Concepts", csa), ErrorCodeConstants.Get_Derived_Concept_Successful + ":" + objectName)
            apiResult.toString()
          } else {
            var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetDerivedConcept", null, ErrorCodeConstants.Get_Derived_Concept_Failed_Not_Available + ":" + objectName)
            apiResult.toString()
          }
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetDerivedConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Derived_Concept_Failed + ":" + objectName)
        apiResult.toString()
      }
    }
  }
  // A derived concept(format JSON or XML) as a string using name and version as the key
  def GetDerivedConcept(objectName: String, version: String, formatType: String): String = {
    var key = objectName + "." + version.toLong
    val dispkey = objectName + "." + MdMgr.Pad0s2Version(version.toLong)
    try {
      val concept = MdMgr.GetMdMgr.Attribute(MdMgr.sysNS, objectName, version.toLong, false)
      concept match {
        case None =>
          None
          logger.debug("No concepts found ")
          var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetDerivedConcept", null, ErrorCodeConstants.Get_Derived_Concept_Failed_Not_Available + ":" + dispkey)
          apiResult.toString()
        case Some(cs) =>
          if (cs.isInstanceOf[DerivedAttributeDef]) {
            var apiResult = new ApiResult(ErrorCodeConstants.Success, "GetDerivedConcept", JsonSerializer.SerializeObjectToJson(cs), ErrorCodeConstants.Get_Derived_Concept_Successful + ":" + dispkey)
            apiResult.toString()
          } else {
            logger.debug("No Derived concepts found ")
            var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetDerivedConcept", null, ErrorCodeConstants.Get_Derived_Concept_Failed_Not_Available + ":" + dispkey)
            apiResult.toString()
          }
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        var apiResult = new ApiResult(ErrorCodeConstants.Failure, "GetDerivedConcept", null, "Error :" + e.toString() + ErrorCodeConstants.Get_Derived_Concept_Failed + ":" + dispkey)
        apiResult.toString()
      }
    }
  }
}
