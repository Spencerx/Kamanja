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

package com.ligadata.messagedef

import scala.util.parsing.json.JSON
import scala.reflect.runtime.universe
import scala.io.Source
import java.io.File
import java.io.PrintWriter
import java.util.Date
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.ArrayBuffer
import org.json4s.jackson.JsonMethods._
import org.json4s.DefaultFormats
import org.json4s.Formats
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.kamanja.metadata.MdMgr
import com.ligadata.kamanja.metadata.EntityType
import com.ligadata.kamanja.metadata.MessageDef
import com.ligadata.kamanja.metadata.BaseAttributeDef
import com.ligadata.kamanja.metadata.ContainerDef
import com.ligadata.kamanja.metadata.ArrayTypeDef
import com.ligadata.kamanja.metadata.ArrayBufTypeDef
import com.ligadata.kamanja.metadata._
import com.ligadata.Exceptions._
import com.ligadata.Exceptions.StackTrace
import java.text.SimpleDateFormat

trait Attrib {
  var NameSpace: String
  var Name: String
  var Type: String
}

class Message(var msgtype: String, var NameSpace: String, var Name: String, var PhysicalName: String, var Version: String, var Description: String, var Fixed: String, var Persist: Boolean, var Elements: List[Element], var TDataExists: Boolean, var TrfrmData: TransformData, var jarset: Set[String], var pkg: String, var concepts: List[String], var Ctype: String, var CCollectiontype: String, var Containers: List[String], var PartitionKey: List[String], var PrimaryKeys: List[String], var ClsNbr: Long, var timePartition: TimePartition)
class TransformData(var input: Array[String], var output: Array[String], var keys: Array[String])
class Field(var NameSpace: String, var Name: String, var Ttype: String, var CollectionType: String, var Fieldtype: String, var FieldtypeVer: String)
class Element(var NameSpace: String, var Name: String, var Ttype: String, var CollectionType: String, var ElemType: String, var FieldtypeVer: String, var SystemField: Boolean, var NativeName: String)
class TimePartition(var Key: String, var Format: String, var DType: String)

case class Concept(var NameSpace: Option[String], var Name: Option[String], var Type: Option[String])
class ConceptList(var Concepts: List[Concept])
case class DictionaryMessage(var NameSpace: Option[String], var Name: Option[String], var PhysicalName: Option[String], var Version: Option[String], var Description: Option[String], var Fixed: Option[String], var concepts: Option[List[String]])

// The class caster that can throw exceptions...
class ClassCaster[T] {
  def unapply(a: Any): Option[T] = Some(a.asInstanceOf[T])
}

// concrete instances of the casters for pattern matching
object AsMap extends ClassCaster[Map[String, Any]]
object AsList extends ClassCaster[List[Any]]
object AsString extends ClassCaster[String]

class MessageDefImpl {

  val logger = this.getClass.getName
  lazy val log = LogManager.getLogger(logger)
  //val pkg: String = "com.ligadata.messagescontainers"
  var rddHandler = new RDDHandler
  var methodGen = new ConstantMethodGenerator
  var messageFldsExtractor = new MessageFldsExtractor
  var cnstObjVar = new ConstantMsgObjVarGenerator
  val timePartitionTypeList: List[String] = List("yearly", "monthly", "daily") //"weekly", "30minutes", "60minutes", "15minutes", "5minutes", "1minute")
  val timePartitionFormatList: List[String] = List("epochtimeinmillis", "epochtimeinseconds", "epochtime")
  val timePartInfo: String = "TimePartitionInfo"

  private def error[T](prefix: String): Option[T] =
    throw MessageException("%s must be specified".format(prefix))

  //creates the class string

  private def getSerializedType(valType: String): String = {
    valType match {
      case "int" => "writeInt"
      case "string" => "writeUTF"
      case "long" => "writeLong"
      case "double" => "writeDouble"
      case "boolean" => "writeBoolean"
      case "char" => "writeChar"
      case "float" => "writeFloat"
      case _ => ""
    }
  }

  private def getDeserializedType(valType: String): String = {
    valType match {
      case "int" => "readInt"
      case "string" => "readUTF"
      case "long" => "readLong"
      case "double" => "readDouble"
      case "boolean" => "readBoolean"
      case "char" => "readChar"
      case "float" => "readFloat"
      case _ => ""
    }
  }

  ///serialize deserialize primative types

  //get the childs from previous existing message or container

  //Get the previous Existing Message/continer from metadata for deserialize function purpose

  /* private def getPrevVerMappedMsgChilds(pMsgdef: ContainerDef, isMsg: Boolean, fixed: String): Map[String, Any] = {
    var prevVerCtrdef: ContainerDef = new ContainerDef()
    var prevVerMsgdef: MessageDef = new MessageDef()
    var childs: Map[String, Any] = Map[String, Any]()

    if (pMsgdef != null) {

      if (isMsg) {
        prevVerCtrdef = pMsgdef.asInstanceOf[MessageDef]
      } else {
        prevVerCtrdef = pMsgdef.asInstanceOf[ContainerDef]
      }
      if (fixed.toLowerCase().equals("false")) {
        val memberDefs = prevVerCtrdef.containerType.asInstanceOf[MappedMsgTypeDef].attrMap
        children ++= attrMap.filter(a => (a._2.isInstanceOf[AttributeDef] && (a._2.asInstanceOf[AttributeDef].aType.isInstanceOf[MappedMsgTypeDef] || a._2.asInstanceOf[AttributeDef].aType.isInstanceOf[StructTypeDef]))).map(a => (a._2.Name, a._2.asInstanceOf[AttributeDef]))

        if (memberDefs != null) {
          //    childs ++= memberDefs.filter(a => (a.isInstanceOf[MappedMsgTypeDef])).map(a => (a.Name, a))
        }
      }
    }
    childs
  }
  *
  */

  /* private def partitionkeyStrObj(message: Message): String = {

    val partitionKeys = if (message.PartitionKey != null) ("Array(\"" + message.PartitionKey.map(p => p.toLowerCase).mkString(", ") + "\")") else ""

    if (partitionKeys != null && partitionKeys.trim() != "")
      "\n	val partitionKeys : Array[String] = " + partitionKeys
    else
      ""
  }

  private def primarykeyStrObj(message: Message): String = {
    val prmryKeys = if (message.PrimaryKeys != null) ("Array(\"" + message.PrimaryKeys.map(p => p.toLowerCase).mkString(", ") + "\")") else ""

    if (prmryKeys != null && prmryKeys.trim() != "")
      "\n	val primaryKeys : Array[String] = " + prmryKeys
    else
      ""
  }
  *
  * */

  // For Mapped messages to track the type of field assign specific value for each type

  private def getBaseTypeID(valType: String): Int = {
    valType match {
      case "string" => 0
      case "int" => 1
      case "float" => 2
      case "boolean" => 3
      case "double" => 4
      case "long" => 5
      case "char" => 6
      case _ => -1

    }
  }

  /*{
    """
    def get(key: String): Any ={
		null
	}
	def getOrElse(key: String, default: Any): Any ={
	    null
	}
	def set(key: String, value: Any): Unit = {
	    null
	}
    """
  }*/

  //creates the message class file
  private def createScalaFile(scalaClass: String, version: String, className: String): Unit = {
    try {
      val writer = new PrintWriter(new File("/tmp/" + className + "_" + version + ".scala"))
      writer.write(scalaClass.toString)
      writer.close()
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
  }

  def processMsgDef(jsonstr: String, msgDfType: String, mdMgr: MdMgr, recompile: Boolean = false): ((String, String), ContainerDef, (String, String)) = {
    var classname: String = null
    var ver: String = null
    var classstr_1: String = null
    var nonVerClassStrVal_1: String = null
    var containerDef: ContainerDef = null
    var javaFactoryClassstr_1: String = null
    var nonVerJavaFactoryClassstr_1: String = null
    try {
      if (mdMgr == null)
        throw new Exception("MdMgr is not found")
      if (msgDfType.equals("JSON")) {
        var message: Message = null
        message = processJson(jsonstr, mdMgr, recompile).asInstanceOf[Message]

        if (message.Fixed.equals("true")) {
          val ((classStrVal, javaFactoryClassstr), containerDefVal, (nonVerClassStrVal, nonVerJavaFactoryClassstr)) = createFixedMsgClass(message, mdMgr, recompile)
          classstr_1 = classStrVal
          javaFactoryClassstr_1 = javaFactoryClassstr
          nonVerJavaFactoryClassstr_1 = nonVerJavaFactoryClassstr
          containerDef = containerDefVal
          nonVerClassStrVal_1 = nonVerClassStrVal
        } else if (message.Fixed.equals("false")) {
          val ((classStrVal, javaFactoryClassstr), containerDefVal, (nonVerClassStrVal, nonVerJavaFactoryClassstr)) = createMappedMsgClass(message, mdMgr, recompile)
          classstr_1 = classStrVal
          javaFactoryClassstr_1 = javaFactoryClassstr
          nonVerClassStrVal_1 = nonVerClassStrVal
          nonVerJavaFactoryClassstr_1 = nonVerJavaFactoryClassstr
          containerDef = containerDefVal
        }
      } else throw new Exception("MsgDef Type JSON is only supported")
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    ((classstr_1, javaFactoryClassstr_1), containerDef, (nonVerClassStrVal_1, nonVerJavaFactoryClassstr_1))
  }

  private def createFixedMsgClass(message: Message, mdMgr: MdMgr, recompile: Boolean = false): ((String, String), ContainerDef, (String, String)) = {
    var containerDef: ContainerDef = null
    var classstr_1: String = null
    var nonVerScalaClassstr_1: String = null
    var javaFactoryClassstr_1: String = null
    var nonVerJavaFactoryClassstr_1: String = null
    try {
      val (classname, ver, (classstr, javaFactoryClassstr), list, argsList, (nonVerScalaClassstr, nonVerJavaFactoryClassstr)) = createClassStr(message, mdMgr, recompile)
      classstr_1 = classstr
      javaFactoryClassstr_1 = javaFactoryClassstr
      nonVerScalaClassstr_1 = nonVerScalaClassstr
      nonVerJavaFactoryClassstr_1 = nonVerJavaFactoryClassstr
      //createScalaFile(classstr, ver, classname)
      val cname = message.pkg + "." + message.NameSpace + "_" + message.Name.toString() + "_" + MdMgr.ConvertVersionToLong(message.Version).toString

      if (message.msgtype.equals("Message"))
        containerDef = createFixedMsgDef(message, list, mdMgr, argsList, recompile)
      else if (message.msgtype.equals("Container"))
        containerDef = createFixedContainerDef(message, list, mdMgr, argsList, recompile)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    ((classstr_1, javaFactoryClassstr_1), containerDef, (nonVerScalaClassstr_1, nonVerJavaFactoryClassstr_1))
  }

  private def createMappedMsgClass(message: Message, mdMgr: MdMgr, recompile: Boolean = false): ((String, String), ContainerDef, (String, String)) = {
    var containerDef: ContainerDef = null
    var classstr_1: String = null
    var nonVerScalaClassstr_1: String = null
    var javaFactoryClassstr_1: String = null
    var nonVerJavaFactoryClassstr_1: String = null
    try {
      val (classname, ver, (classstr, javaFactoryClassstr), list, argsList, (nonVerScalaClassstr, nonVerJavaFactoryClassstr)) = createMappedClassStr(message, mdMgr, recompile)
      classstr_1 = classstr
      javaFactoryClassstr_1 = javaFactoryClassstr
      nonVerScalaClassstr_1 = nonVerScalaClassstr
      nonVerJavaFactoryClassstr_1 = nonVerJavaFactoryClassstr
      // createScalaFile(classstr, ver, classname)
      val cname = message.pkg + "." + message.NameSpace + "_" + message.Name.toString() + "_" + MdMgr.ConvertVersionToLong(message.Version).toString

      if (message.msgtype.equals("Message"))
        containerDef = createMappedMsgDef(message, list, mdMgr, argsList, recompile)
      else if (message.msgtype.equals("Container"))
        containerDef = createMappedContainerDef(message, list, mdMgr, argsList, recompile)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    ((classstr_1, javaFactoryClassstr_1), containerDef, (nonVerScalaClassstr_1, nonVerJavaFactoryClassstr_1))
  }

  private def createClassStr(message: Message, mdMgr: MdMgr, recompile: Boolean): (String, String, (String, String), List[(String, String)], List[(String, String, String, String, Boolean, String)], (String, String)) = {
    var scalaclass = new StringBuilder(8 * 1024)
    var nonVerScalaCls = new StringBuilder(8 * 1024)
    var verJavaFactory = new StringBuilder(8 * 1024)
    var nonverJavaFactory = new StringBuilder(8 * 1024)
    val ver = MdMgr.ConvertVersionToLong(message.Version).toString
    val newline = "\n"
    var addMsgStr: String = ""
    var getMsgStr: String = ""
    var list_msg = List[(String, String)]()
    var argsList_msg = List[(String, String, String, String, Boolean, String)]()

    try {
      //val (classstr, csvassignstr, jsonstr, xmlStr, count, list, argsList, addMsg, getMsg, partkeyPos, primarykeyPos, serializedBuf, deserializedBuf, prevDeserializedBuf, prevVerMsgObjstr, convertOldObjtoNewObjStr, collections, "", "", withMethods, fromFunc) = messageFldsExtractor.classStr(message, mdMgr, recompile)

      val (scalaReturnStrArray, count, list, argsList, partkeyPos, primarykeyPos) = messageFldsExtractor.classStr(message, mdMgr, recompile)
      val classstr = scalaReturnStrArray(0)
      val csvassignstr = scalaReturnStrArray(1)
      val jsonstr = scalaReturnStrArray(2)
      val xmlStr = scalaReturnStrArray(3)
      val addMsg = scalaReturnStrArray(4)
      val getMsg = scalaReturnStrArray(5)
      val serializedBuf = scalaReturnStrArray(6)
      val deserializedBuf = scalaReturnStrArray(7)
      val prevDeserializedBuf = scalaReturnStrArray(8)
      val prevVerMsgObjstr = scalaReturnStrArray(9)
      val convertOldObjtoNewObjStr = scalaReturnStrArray(10)
      val collections = scalaReturnStrArray(11)
      val mappedSerBaseTypesBuf = scalaReturnStrArray(12)
      val mappedDeserBaseTypesBuf = scalaReturnStrArray(13)
      val withMethods = scalaReturnStrArray(14)
      val fromFunc = scalaReturnStrArray(15)
      val fromFuncBaseTypes = scalaReturnStrArray(16)
      val assignKvData = scalaReturnStrArray(17)
      val timePartitionKeyFld = scalaReturnStrArray(18)
      val timePartitionPos = scalaReturnStrArray(19)
      val nativeKeyMapStr = scalaReturnStrArray(20)
      val nativeKeyValues = scalaReturnStrArray(21)

      val fromFuncOfFixed = cnstObjVar.fromFuncOfFixedMsgs(message, fromFunc, fromFuncBaseTypes)
      val getSerializedFuncStr = methodGen.getSerializedFunction(serializedBuf)
      val getWithMethod = withMethods
      // println("withMethods" + withMethods)
      val getDeserializedFuncStr = methodGen.getDeserializedFunction(true, deserializedBuf, prevDeserializedBuf, prevVerMsgObjstr, recompile)
      val convertOldObjtoNewObj = methodGen.getConvertOldVertoNewVer(convertOldObjtoNewObjStr, prevVerMsgObjstr, message.PhysicalName, message)
      val populateKvData = methodGen.populateKvData(assignKvData)
      val hasPrimaryPartitionTimePartitionKeys = cnstObjVar.hasClsPrimaryPartitionTimePartitionKeys(message)

      list_msg = list
      argsList_msg = argsList
      addMsgStr = cnstObjVar.addMessage(addMsg, message)
      getMsgStr = cnstObjVar.getMessage(getMsg)
      val (btrait, striat, csetters) = cnstObjVar.getBaseTrait(message)
      val (clsstr, objstr, clasname) = cnstObjVar.classname(message, recompile)
      val cobj = cnstObjVar.createObj(message, partkeyPos, primarykeyPos, clasname, timePartitionKeyFld, timePartitionPos)
      val isFixed = cnstObjVar.getIsFixed(message)
      val (versionPkgImport, nonVerPkgImport, verPkg, nonVerPkg) = cnstObjVar.importStmts(message)
      scalaclass = scalaclass.append(versionPkgImport.toString() + newline + newline + objstr + newline + cobj.toString + newline + clsstr.toString + newline)
      scalaclass = scalaclass.append(classstr + cnstObjVar.logStackTrace + csetters + addMsgStr + getMsgStr + cnstObjVar.saveObject(message) + methodGen.getNativeKeyMapVar(nativeKeyMapStr) + methodGen.getFixedMsgNativeKeyValueData(nativeKeyValues) + methodGen.populate + methodGen.populatecsv(csvassignstr, count) + methodGen.populateJson + methodGen.assignJsonData(jsonstr) + methodGen.assignXmlData(xmlStr) + populateKvData + getSerializedFuncStr + getDeserializedFuncStr + convertOldObjtoNewObj + withMethods + fromFuncOfFixed + cnstObjVar.computeTimePartitionDate(message) + cnstObjVar.getTimePartition(message) + cnstObjVar.transactionIdFuncs(message) + hasPrimaryPartitionTimePartitionKeys + " \n}")

      verJavaFactory = verJavaFactory.append(verPkg + rddHandler.javaMessageFactory(message) + " \n")

      nonVerScalaCls = nonVerScalaCls.append(nonVerPkgImport.toString() + newline + newline + objstr + newline + cobj.toString + newline + clsstr.toString + newline)
      nonVerScalaCls = nonVerScalaCls.append(classstr + cnstObjVar.logStackTrace + csetters + addMsgStr + getMsgStr + cnstObjVar.saveObject(message) + methodGen.getNativeKeyMapVar(nativeKeyMapStr) + methodGen.getFixedMsgNativeKeyValueData(nativeKeyValues) + methodGen.populate + methodGen.populatecsv(csvassignstr, count) + methodGen.populateJson + methodGen.assignJsonData(jsonstr) + methodGen.assignXmlData(xmlStr) + populateKvData + getSerializedFuncStr + getDeserializedFuncStr + convertOldObjtoNewObj + withMethods + fromFuncOfFixed + cnstObjVar.computeTimePartitionDate(message) + cnstObjVar.getTimePartition(message) + cnstObjVar.transactionIdFuncs(message) + hasPrimaryPartitionTimePartitionKeys + " \n}")

      nonverJavaFactory = nonverJavaFactory.append(nonVerPkg + rddHandler.javaMessageFactory(message) + " \n")
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    (message.Name, ver.toString, (scalaclass.toString, verJavaFactory.toString), list_msg, argsList_msg, (nonVerScalaCls.toString, nonverJavaFactory.toString))
  }

  private def createMappedClassStr(message: Message, mdMgr: MdMgr, recompile: Boolean): (String, String, (String, String), List[(String, String)], List[(String, String, String, String, Boolean, String)], (String, String)) = {
    var scalaclass = new StringBuilder(8 * 1024)
    val ver = MdMgr.ConvertVersionToLong(message.Version).toString
    var verJavaFactory = new StringBuilder(8 * 1024)
    var nonverJavaFactory = new StringBuilder(8 * 1024)
    val newline = "\n"
    var addMsgStr: String = ""
    var getMsgStr: String = ""
    var list_msg = List[(String, String)]()
    var argsList_msg = List[(String, String, String, String, Boolean, String)]()
    var nonVerScalaCls = new StringBuilder(8 * 1024)
    try {
      //   val (classstr, csvassignstr, jsonstr, xmlStr, count, list, argsList, addMsg, getMsg, partitionPos, primaryPos, serializedBuf, deserializedBuf, prevDeserializedBuf, prevVerMsgObjstr, convertOldObjtoNewObjStr, collections, mappedSerBaseTypesBuf, mappedDeserBaseTypesBuf, withMethods, "") = messageFldsExtractor.classStr(message, mdMgr, recompile)

      val (scalaReturnStrArray, count, list, argsList, partitionPos, primaryPos) = messageFldsExtractor.classStr(message, mdMgr, recompile)
      val classstr = scalaReturnStrArray(0)
      val csvassignstr = scalaReturnStrArray(1)
      val jsonstr = scalaReturnStrArray(2)
      val xmlStr = scalaReturnStrArray(3)
      val addMsg = scalaReturnStrArray(4)
      val getMsg = scalaReturnStrArray(5)
      val serializedBuf = scalaReturnStrArray(6)
      val deserializedBuf = scalaReturnStrArray(7)
      val prevDeserializedBuf = scalaReturnStrArray(8)
      val prevVerMsgObjstr = scalaReturnStrArray(9)
      val convertOldObjtoNewObjStr = scalaReturnStrArray(10)
      val collections = scalaReturnStrArray(11)
      val mappedSerBaseTypesBuf = scalaReturnStrArray(12)
      val mappedDeserBaseTypesBuf = scalaReturnStrArray(13)
      val withMethods = scalaReturnStrArray(14)
      val fromFunc = scalaReturnStrArray(15)
      val fromFuncBaseTypesMapped = scalaReturnStrArray(16)
      val assignKvData = scalaReturnStrArray(17)
      val timePartitionKeyFld = scalaReturnStrArray(18)
      val timePartitionPos = scalaReturnStrArray(19)
      val nativeKeyMapStr = scalaReturnStrArray(20)
      val nativeKeyValues = scalaReturnStrArray(21)


      val fromFuncOfMapped = cnstObjVar.fromFuncOfMappedMsgs(message, fromFunc, fromFuncBaseTypesMapped)

      list_msg = list
      argsList_msg = argsList
      addMsgStr = cnstObjVar.addMessage(addMsg, message)
      getMsgStr = cnstObjVar.getMessage(getMsg)

      val hasPrimaryPartitionTimePartitionKeys = cnstObjVar.hasClsPrimaryPartitionTimePartitionKeys(message)

      //val fromFuncOfFixed = fromFuncOfFixedMsgs(message, fromFunc)
      val getSerializedFuncStr = methodGen.getSerializedFunction(serializedBuf)
      //println(deserializedBuf)
      mappedMsgPrevVerDeser
      val mapBaseDeser = methodGen.MappedMsgDeserBaseTypes(mappedDeserBaseTypesBuf)
      val getDeserializedFuncStr = methodGen.getDeserializedFunction(false, mapBaseDeser + deserializedBuf, methodGen.prevVerLessThanCurVerCheck(prevDeserializedBuf), prevVerMsgObjstr, recompile)
      val convertOldObjtoNewObj = methodGen.getConvertOldVertoNewVer(methodGen.getConvertOldVertoNewVer, prevVerMsgObjstr, message.PhysicalName, message)

      val (btrait, striat, csetters) = cnstObjVar.getBaseTrait(message)
      val (clsstr, objstr, clasname) = cnstObjVar.classname(message, recompile)
      val cobj = cnstObjVar.createObj(message, partitionPos, primaryPos, clasname, timePartitionKeyFld, timePartitionPos)
      val isFixed = cnstObjVar.getIsFixed(message)
      val (versionPkgImport, nonVerPkgImport, verPkg, nonVerPkg) = cnstObjVar.importStmts(message)

      scalaclass = scalaclass.append(versionPkgImport.toString() + newline + newline + objstr + newline + cobj.toString + newline + clsstr.toString + newline)
      scalaclass = scalaclass.append(classstr + cnstObjVar.logStackTrace + cnstObjVar.getTransactionIdMapped + cnstObjVar.getCollectionsMapped(collections) + csetters + addMsgStr + getMsgStr + cnstObjVar.saveObject(message) + methodGen.getNativeKeyMapVar(nativeKeyMapStr) + methodGen.getMappedMsgNativeKeyValueData() + methodGen.populate + methodGen.populateMappedCSV(csvassignstr, count) + methodGen.populateJson + methodGen.assignMappedJsonData(jsonstr) + methodGen.assignMappedXmlData(xmlStr) + methodGen.MappedMsgSerialize + methodGen.populateMappedMsgKvData(assignKvData) + methodGen.MappedMsgSerializeBaseTypes(mappedSerBaseTypesBuf) + methodGen.MappedMsgSerializeArrays(serializedBuf) + "" + getDeserializedFuncStr + convertOldObjtoNewObj + withMethods + fromFuncOfMapped + cnstObjVar.computeTimePartitionDate(message) + cnstObjVar.getTimePartition(message) + cnstObjVar.transactionIdFuncs(message) + hasPrimaryPartitionTimePartitionKeys + " \n}") //cnstObjVar.fromFuncOfMappedMsg(message) 

      verJavaFactory = verJavaFactory.append(verPkg + rddHandler.javaMessageFactory(message) + " \n")

      nonVerScalaCls = nonVerScalaCls.append(nonVerPkgImport.toString() + newline + newline + objstr + newline + cobj.toString + newline + clsstr.toString + newline)
      nonVerScalaCls = nonVerScalaCls.append(classstr + cnstObjVar.logStackTrace + cnstObjVar.getTransactionIdMapped + cnstObjVar.getCollectionsMapped(collections) + csetters + addMsgStr + getMsgStr + cnstObjVar.saveObject(message) + methodGen.getNativeKeyMapVar(nativeKeyMapStr) + methodGen.getMappedMsgNativeKeyValueData() + methodGen.populate + methodGen.populateMappedCSV(csvassignstr, count) + methodGen.populateJson + methodGen.assignMappedJsonData(jsonstr) + methodGen.assignMappedXmlData(xmlStr) + methodGen.populateMappedMsgKvData(assignKvData) + methodGen.MappedMsgSerialize + methodGen.MappedMsgSerializeBaseTypes(mappedSerBaseTypesBuf) + methodGen.MappedMsgSerializeArrays(serializedBuf) + "" + getDeserializedFuncStr + convertOldObjtoNewObj + withMethods + fromFuncOfMapped + cnstObjVar.computeTimePartitionDate(message) + cnstObjVar.getTimePartition(message) + cnstObjVar.transactionIdFuncs(message) + hasPrimaryPartitionTimePartitionKeys + " \n}") //cnstObjVar.fromFuncOfMappedMsg(message)

      nonverJavaFactory = nonverJavaFactory.append(nonVerPkg + rddHandler.javaMessageFactory(message) + " \n")

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    (message.Name, ver.toString, (scalaclass.toString, verJavaFactory.toString), list_msg, argsList_msg, (nonVerScalaCls.toString, nonverJavaFactory.toString))

  }

  /*private def getFields = {
    """
   var fields: Map[String, Any] = new HashMap[String, Any];
    """
  }*/

  private def createMappedContainerDef(msg: Message, list: List[(String, String)], mdMgr: MdMgr, argsList: List[(String, String, String, String, Boolean, String)], recompile: Boolean = false): ContainerDef = {
    var containerDef: ContainerDef = new ContainerDef()
    try {

      if (msg.Persist) {
        if (msg.PartitionKey == null || msg.PartitionKey.size == 0) {
          throw new Exception("Please provide parition keys in the MessageDefinition since the Message has to be Persisted")
        }
      }
      if (msg.PartitionKey != null)
        containerDef = mdMgr.MakeMappedContainer(msg.NameSpace, msg.Name, msg.PhysicalName, argsList, MdMgr.ConvertVersionToLong(msg.Version), null, msg.jarset.toArray, null, null, msg.PartitionKey.toArray, recompile, msg.Persist)
      else
        containerDef = mdMgr.MakeMappedContainer(msg.NameSpace, msg.Name, msg.PhysicalName, argsList, MdMgr.ConvertVersionToLong(msg.Version), null, msg.jarset.toArray, null, null, null, recompile, msg.Persist)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    containerDef
  }

  private def createMappedMsgDef(msg: Message, list: List[(String, String)], mdMgr: MdMgr, argsList: List[(String, String, String, String, Boolean, String)], recompile: Boolean = false): MessageDef = {
    var msgDef: MessageDef = new MessageDef()
    try {

      if (msg.Persist) {
        if (msg.PartitionKey == null || msg.PartitionKey.size == 0) {
          throw new Exception("Please provide parition keys in the MessageDefinition since the Message has to be Persisted")
        }
      }

      if (msg.PartitionKey != null)
        msgDef = mdMgr.MakeMappedMsg(msg.NameSpace, msg.Name, msg.PhysicalName, argsList, MdMgr.ConvertVersionToLong(msg.Version), null, msg.jarset.toArray, null, null, msg.PartitionKey.toArray, recompile, msg.Persist)
      else
        msgDef = mdMgr.MakeMappedMsg(msg.NameSpace, msg.Name, msg.PhysicalName, argsList, MdMgr.ConvertVersionToLong(msg.Version), null, msg.jarset.toArray, null, null, null, recompile, msg.Persist)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    msgDef
  }

  private def createFixedContainerDef(msg: Message, list: List[(String, String)], mdMgr: MdMgr, argsList: List[(String, String, String, String, Boolean, String)], recompile: Boolean = false): ContainerDef = {
    var containerDef: ContainerDef = new ContainerDef()
    try {
      if (msg.Persist) {
        if (msg.PartitionKey == null || msg.PartitionKey.size == 0) {
          throw new Exception("Please provide parition keys in the MessageDefinition since the Message has to be Persisted")
        }
      }

      if (msg.PartitionKey != null)
        containerDef = mdMgr.MakeFixedContainer(msg.NameSpace, msg.Name, msg.PhysicalName, argsList, MdMgr.ConvertVersionToLong(msg.Version), null, msg.jarset.toArray, null, null, msg.PartitionKey.toArray, recompile, msg.Persist)
      else
        containerDef = mdMgr.MakeFixedContainer(msg.NameSpace, msg.Name, msg.PhysicalName, argsList, MdMgr.ConvertVersionToLong(msg.Version), null, msg.jarset.toArray, null, null, null, recompile, msg.Persist)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    containerDef
  }

  private def createFixedMsgDef(msg: Message, list: List[(String, String)], mdMgr: MdMgr, argsList: List[(String, String, String, String, Boolean, String)], recompile: Boolean = false): MessageDef = {
    var msgDef: MessageDef = new MessageDef()

    try {
      var version = MdMgr.ConvertVersionToLong(msg.Version)
      if (msg.Persist) {
        if (msg.PartitionKey == null || msg.PartitionKey.size == 0) {
          throw new Exception("Please provide parition keys in the MessageDefinition since the Message will be Persisted based on Partition Keys")
        }
      }

      if (msg.PartitionKey != null)
        msgDef = mdMgr.MakeFixedMsg(msg.NameSpace, msg.Name, msg.PhysicalName, argsList, version, null, msg.jarset.toArray, null, null, msg.PartitionKey.toArray, recompile, msg.Persist)
      else
        msgDef = mdMgr.MakeFixedMsg(msg.NameSpace, msg.Name, msg.PhysicalName, argsList, version, null, msg.jarset.toArray, null, null, null, recompile, msg.Persist)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    msgDef
  }

  private def mappedMsgPrevVerDeser(): String = {

    """
    prevVerObj.fields.foreach(field => {

          if (prevVerMatchKeys.contains(field)) {
            fields(field._1.toLowerCase) = (field._2._1, field._2._2)
          } else {
            fields(field._1.toLowerCase) =  (0, ValueToString(field._2._2))
          }

        })

    """
  }

  /*
  def processConcept(conceptJson: String, mdMgr: MdMgr): Unit = {
    val list: List[(String, String, String)] = listConceptArgs(parseConceptJson(conceptJson))

  }

  def listConceptArgs(cpts: Concepts): List[(String, String, String)] = {
    var list = List[(String, String, String)]()
    for (c <- cpts.Concepts) {
      list ::= (c.NameSpace, c.Name, c.Type)
    }
    list
  }

  def parseConceptJson(conceptJson: String): Concepts = {
    try {
      val pConcpt = JSON.parseFull(conceptJson)
      if (pConcpt.isEmpty) throw MessageException("Unable to parse JSON.")

      val cptsDef = for {
        Some(AsMap(map)) <- List(pConcpt)
        AsList(concepts) <- map get "Concepts"
      } yield new Concepts(for {
        AsMap(concept) <- concepts
        AsString(name) <- concept get "Name" orElse error("Structure Name in Field does not exist in json")
        AsString(ctype) <- concept get "Type" orElse error("Structure Type in Field does not exist in json")
        AsString(namespace) <- concept get "NameSpace" orElse error("Structure Type in Field does not exist in json")

      } yield new Concept(namespace, name, ctype))
      cptsDef.head
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:"+stackTrace)
        throw e
      }
    }
  }
*/
  private def processJson(json: String, mdMgr: MdMgr, recompile: Boolean = false): Message = {
    var message: Message = null
    var jtype: String = null
    //val parsed = JSON.parseFull(json)
    val map = parse(json).values.asInstanceOf[Map[String, Any]]
    // val map = parsed.get.asInstanceOf[Map[String, Any]]
    //  val map = parsed
    var key: String = ""
    try {
      jtype = geJsonType(map)
      message = processJsonMap(jtype, map, mdMgr, recompile).asInstanceOf[Message]

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    message
  }

  private def geJsonType(map: Map[String, Any]): String = {
    var jtype: String = null
    try {
      if (map.contains("Message"))
        jtype = "Message"
      else if (map.contains("Container"))
        jtype = "Container"
      else if (map.contains("Concepts"))
        jtype = "Concepts"
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    jtype
  }

  private def processJsonMap(key: String, map: Map[String, Any], mdMgr: MdMgr, recompile: Boolean = false): Message = {
    var msg1: Message = null
    type messageMap = Map[String, Any]
    try {
      if (map.contains(key)) {
        if (map.get(key).get.isInstanceOf[messageMap]) {
          val message = map.get(key).get.asInstanceOf[Map[String, Any]]
          // if (message.get("Fixed").get.equals("true")) {
          msg1 = getMsgorCntrObj(message, key, mdMgr, recompile).asInstanceOf[Message]
          //  } else if (message.get("Fixed").get.equals("false")) {
          //    msg1 = geKVMsgorCntrObj(message, key).asInstanceOf[Message]
          //  } else throw new Exception("Message in json is not Map")

        }
      } else throw new Exception("Incorrect json")
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }

    msg1
  }

  /* private def geKVMsgorCntrObj(message: Map[String, Any], mtype: String): Message = {
    var pkg: String = "com.ligadata.messagescontainers"
    val physicalName: String = pkg + "." + message.get("NameSpace").get.toString + "_" + message.get("Name").get.toString() + "_" + MdMgr.ConvertVersionToInt(extractVersion(message)).toString
    var tdata: TransformData = null
    var tdataexists: Boolean = false
    var conceptList: List[String] = null
    try {
      //println("message" + message)
      if (message.contains("Concepts")) {
        // println("concepts")
        conceptList = message.get("Concepts").get.asInstanceOf[List[String]]
      }
      for (c <- conceptList) {
        // println("concept list" + c)
      }

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:"+stackTrace)
        throw e
      }
    }
    new Message(mtype, message.get("NameSpace").get.toString, message.get("Name").get.toString(), physicalName, extractVersion(message), message.get("Description").get.toString(), message.get("Fixed").get.toString(), null, tdataexists, tdata, null, pkg, conceptList, null, null, null, null, null, 0)
  }
  *
  */

  // Make sure the version is in the format of nn.nn.nn
  private def extractVersion(message: Map[String, Any]): String = {
    MdMgr.FormatVersion(message.getOrElse("Version", "0").toString)
  }

  private def getMsgorCntrObj(message: Map[String, Any], mtype: String, mdMgr: MdMgr, recompile: Boolean = false): Message = {
    var ele: List[Element] = null
    var elements: List[Element] = null
    var tdata: TransformData = null
    var tdataexists: Boolean = false
    var container: Message = null
    val tkey: String = "TransformData"
    var pKey: String = null
    var prmryKey: String = null
    var partitionKeysList: List[String] = null
    var primaryKeysList: List[String] = null
    var conceptsList: List[String] = null
    var msgVersion: String = ""
    var persistMsg: Boolean = false
    var timePartition: TimePartition = null
    var fldList: Set[String] = Set[String]()
    try {
      if (message != null) {
        if (message.getOrElse("NameSpace", null) == null)
          throw new Exception("Please provide the Name space in the message definition ")

        if (message.getOrElse("Name", null) == null)
          throw new Exception("Please provide the Name of the message definition ")

        if (message.getOrElse("Version", null) == null)
          throw new Exception("Please provide the Version of the message definition ")

        if (message.getOrElse("Fixed", null) == null)
          throw new Exception("Please provide the type of the message definition (either Fixed or Mapped) ")

        val persist = message.getOrElse("Persist", "false").toString.toLowerCase

        if (persist.equals("true"))
          persistMsg = true

        for (key: String <- message.keys) {
          if (key.equals("Elements") || key.equals("Fields") || key.equals("Concepts")) {
            ele = getElementsObj(message, key).asInstanceOf[List[Element]]

            // container = getElementsObj(message, key)._2.asInstanceOf[Message]
          }

          if (mtype.equals("Message") && message.contains(tkey)) {
            if (key.equals(tkey)) {
              tdataexists = true
              tdata = getTransformData(message, key)
            }
          }

          if (key.equals("PartitionKey")) {
            var partitionKeys = message.getOrElse("PartitionKey", null)
            if (partitionKeys != null) {
              partitionKeysList = partitionKeys.asInstanceOf[List[String]]
              partitionKeysList = partitionKeysList.map(p => p.toLowerCase())
              for (partitionKey: String <- partitionKeysList) {
                if (partitionKeysList.size == 1)
                  pKey = partitionKey.toLowerCase()
              }
            }
          }

          if (key.equals("PrimaryKey")) {
            var primaryKeys = message.getOrElse("PrimaryKey", null)

            if (primaryKeys != null) {
              primaryKeysList = primaryKeys.asInstanceOf[List[String]]
              primaryKeysList = primaryKeysList.map(p => p.toLowerCase())
            }
          }

          if (ele != null && ele.size > 0) {
            ele.foreach(Fld => { fldList += Fld.Name })

            if (fldList != null && fldList.size > 0) {
              if (partitionKeysList != null && partitionKeysList.size > 0) {
                if (!(partitionKeysList.toSet subsetOf fldList))
                  throw new Exception("Partition Keys should be included in fields/elements of message/container definition " + message.get("Name").get.toString())
              }

              if (primaryKeysList != null && primaryKeysList.size > 0) {
                if (!(primaryKeysList.toSet subsetOf fldList))
                  throw new Exception("Primary Keys should be included in fields/elements of message/container definition " + message.get("Name").get.toString())
              }
            }
          }
          if (key.equals("Concepts")) {
            conceptsList = getConcepts(message, key)
          }
        }

        //DateParition Info

        if (message.getOrElse(timePartInfo, null) != null) {
          timePartition = getMsgTimePartitionInfo(timePartInfo, message, fldList)
        }

        if (message.get("Fixed").get.toString().toLowerCase == "true" && ele == null)
          throw new Exception("Either Fields or Elements or Concepts  do not exist in the Fixed Message/Container " + message.get("Name").get.toString())

        if (ele != null)
          ele = ele :+ new Element("", "transactionId", "system.long", "", "Fields", null, true, "transactionId")
        else
          ele = List(new Element("", "transactionId", "system.long", "", "Fields", null, true, "transactionId"))

        ele = ele :+ new Element("", "timePartitionData", "system.long", "", "Fields", null, true, "timePartitionData")

        // ele.foreach(f => log.debug("====" + f.Name))

        if (recompile) {
          msgVersion = messageFldsExtractor.getRecompiledMsgContainerVersion(mtype, message.get("NameSpace").get.toString, message.get("Name").get.toString(), mdMgr)
        } else {
          msgVersion = extractVersion(message)
        }

      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }

    val cur_time = System.currentTimeMillis
    //  val physicalName: String = pkg + "." + message.get("NameSpace").get.toString + "." + message.get("Name").get.toString() + "." + MdMgr.ConvertVersionToLong(msgVersion).toString + "_" + cur_time
    val pkg = message.get("NameSpace").get.toString
    val physicalName: String = pkg + ".V" + MdMgr.ConvertVersionToLong(msgVersion).toString + "." + message.get("Name").get.toString()

    new Message(mtype, message.get("NameSpace").get.toString, message.get("Name").get.toString(), physicalName, msgVersion, message.get("Description").get.toString(), message.get("Fixed").get.toString(), persistMsg, ele, tdataexists, tdata, null, pkg.trim(), conceptsList, null, null, null, partitionKeysList, primaryKeysList, cur_time, timePartition)
  }

  // Parse Date partition Info from the Message Definition
  private def getMsgTimePartitionInfo(key: String, message: Map[String, Any], fldList: Set[String]): TimePartition = {
    var timePartitionKey: String = null
    var timePartitionKeyFormat: String = null
    var timePartitionType: String = null
    type sMap = Map[String, String]
    try {

      if (message.getOrElse(key, null) != null && message.get(key).get.isInstanceOf[sMap]) {
        val timePartitionMap: sMap = message.get(key).get.asInstanceOf[sMap]

        if (timePartitionMap.contains("Key") && (timePartitionMap.get("Key").get.isInstanceOf[String])) {
          timePartitionKey = timePartitionMap.get("Key").get.asInstanceOf[String].toLowerCase()

          if (!fldList.contains(timePartitionKey))
            throw new Exception("Time Partition Key " + timePartitionKey + " should be defined as one of the fields in the message definition");

        } else throw new Exception("Time Partition Key should be defined in the message definition");

        if (timePartitionMap.contains("Format") && (timePartitionMap.get("Format").get.isInstanceOf[String])) {
          timePartitionKeyFormat = timePartitionMap.get("Format").get.asInstanceOf[String] //.toLowerCase()

          if (!validateTimePartitionFormat(timePartitionKeyFormat))
            throw new Exception("Time Parition format given in message definition " + timePartitionKeyFormat + " is not a valid format");
        } else throw new Exception("Time Partition Format should be defined in the message definition");

        if (timePartitionMap.contains("Type") && (timePartitionMap.get("Type").get.isInstanceOf[String])) {
          timePartitionType = timePartitionMap.get("Type").get.asInstanceOf[String].toLowerCase()

          if (!containsIgnoreCase(timePartitionTypeList, timePartitionType))
            throw new Exception("Time Parition Type " + timePartitionType + " defined in the message definition is not a valid Type");
        } else throw new Exception("Time Partition Type should be defined in the message definition");

      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }

    new TimePartition(timePartitionKey, timePartitionKeyFormat, timePartitionType);

  }

  //Validate the Time Partition format 
  private def validateTimePartitionFormat(format: String): Boolean = {

    if (containsIgnoreCase(timePartitionFormatList, format))
      return true;
    else return validateDateTimeFormat(format)

    return false
  }

  //Validate if the date time format is valid SimpleDateFormat

  private def validateDateTimeFormat(format: String): Boolean = {
    try {
      new SimpleDateFormat(format);
      return true
    } catch {
      case e: Exception => {
        return false
      }
    }
    return false
  }

  private def getTransformData(message: Map[String, Any], tkey: String): TransformData = {
    var iarr: Array[String] = null
    var oarr: Array[String] = null
    var karr: Array[String] = null
    type tMap = Map[String, Any]

    if (message.get(tkey).get.isInstanceOf[tMap]) {
      val tmap: Map[String, Any] = message.get(tkey).get.asInstanceOf[Map[String, Any]]
      for (key <- tmap.keys) {
        if (key.equals("Input"))
          iarr = gettData(tmap, key)
        if (key.equals("Output"))
          oarr = gettData(tmap, key)
        if (key.equals("Keys"))
          karr = gettData(tmap, key)
      }
    }
    new TransformData(iarr, oarr, karr)
  }

  private def gettData(tmap: Map[String, Any], key: String): Array[String] = {
    type tList = List[String]
    var tlist: List[String] = null
    if (tmap.contains(key) && tmap.get(key).get.isInstanceOf[tList])
      tlist = tmap.get(key).get.asInstanceOf[List[String]]
    tlist.toArray
  }

  private def getElementsObj(message: Map[String, Any], key: String): List[Element] = {
    // type list = List[Element]
    var elist: List[Element] = null
    var containerList: List[String] = null
    var container: Message = null
    var lbuffer = new ListBuffer[Element]
    var conceptStr: String = "Concepts"

    try {
      if (key.equals("Elements") || key.equals("Fields")) {
        // val (elist, container) = getElements(message, key).asInstanceOf[List[Element]]
        elist = getElements(message, key).asInstanceOf[List[Element]]
        // container = getElements(message, key)._2.asInstanceOf[Message]
      } else if (key.equals("Concepts")) {
        elist = getConceptData(message.get(conceptStr).get.asInstanceOf[List[String]], conceptStr)

      } else throw new Exception("Either Fields or Elements or Concepts  do not exist in " + key + " json")
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }

    elist
  }

  private def getConceptData(ccpts: List[String], key: String): List[Element] = {
    var element: Element = null
    var lbuffer = new ListBuffer[Element]
    type string = String;
    if (ccpts == null) throw new Exception("Concepts List is null")
    try {
      for (l <- ccpts)
        lbuffer += new Element(null, l.toString(), l.toString(), null, key, null, false, l.toString())
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    lbuffer.toList
  }

  private def getConcepts(message: Map[String, Any], key: String): List[String] = {
    var list: List[Element] = Nil
    var lbuffer = new ListBuffer[Element]
    type ccptList = List[String]
    var conceptList: ccptList = null
    try {
      if (message.get(key).get.isInstanceOf[ccptList]) {
        //val eList = message.get(key).get.asInstanceOf[List[String]]
        conceptList = message.getOrElse("Concepts", null).asInstanceOf[ccptList]

      } else throw new Exception("Elements list do not exist in json")
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    conceptList
  }

  private def getElements(message: Map[String, Any], key: String): List[Element] = {
    // var fbuffer = new ListBuffer[Field]
    var lbuffer = new ListBuffer[Element]
    var container: Message = null
    type messageList = List[Map[String, Any]]
    type keyMap = Map[String, Any]
    type typList = List[String]
    var cntrList: List[String] = null
    try {
      if (message.get(key).get.isInstanceOf[messageList]) {
        val eList = message.get(key).get.asInstanceOf[List[Map[String, Any]]]
        for (l <- eList) {
          if (l.isInstanceOf[keyMap]) {
            val eMap: Map[String, Any] = l.asInstanceOf[Map[String, Any]]

            if (eMap.contains("Field")) {
              lbuffer += getElement(eMap)

            } else if (key.equals("Fields")) {
              lbuffer += getElementData(eMap.asInstanceOf[Map[String, String]], key)

            } else if (eMap.contains("Container") || eMap.contains("Message") || eMap.contains("Containers") || eMap.contains("Messages") || eMap.contains("Concept") || eMap.contains("Concepts")) {

              var key: String = ""
              if (eMap.contains("Container"))
                key = "Container"
              else if (eMap.contains("Containers"))
                key = "Containers"
              else if (eMap.contains("Message"))
                key = "Message"
              else if (eMap.contains("Messages"))
                key = "Messages"
              else if (eMap.contains("Concepts"))
                key = "Concepts"
              else if (eMap.contains("Concept"))
                key = "Concept"

              if (eMap.get(key).get.isInstanceOf[keyMap]) {
                val containerMap: Map[String, Any] = eMap.get(key).get.asInstanceOf[Map[String, Any]]
                lbuffer += getElementData(containerMap.asInstanceOf[Map[String, String]], key)
              } else if (eMap.get(key).get.isInstanceOf[typList]) {
                lbuffer ++= getConceptData(eMap.get(key).get.asInstanceOf[List[String]], key)
              } else if (eMap.get(key).get.isInstanceOf[String]) {
                var cntrList: ListBuffer[String] = new ListBuffer[String]()
                cntrList += eMap.get(key).get.toString
                lbuffer ++= getConceptData(cntrList.toList, key)
              }

            } else if (message.get("Fixed").get.toString().toLowerCase == "true") throw new Exception("Either Fields or Container or Message or Concepts  do not exist in " + key + " json")
          }
        }
      } else throw new Exception("Elements list do not exist in message/container definition json")
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    //  println("fbuffer" + fbuffer.toList)
    //  val ele: Element = new Element(fbuffer.toList, container)
    //  println("ele " + ele.Fields(1).Name)
    // lbuffer += ele
    (lbuffer.toList)
  }

  /* def getContainerObj(eMap: Map[String, Any]): Message = {

    var lbuffer = new ListBuffer[Element]
    var ContainerType = ""
    var CollectionType = ""
    // message = processJsonMap("Container", eMap).asInstanceOf[Message]
    // get the Name, is is Array of Container from map
    //if container in message has fields get the array of fields
    //if the container has list of container name get the array of container
    //create a message object

    var fbuffer = new ListBuffer[Field]
    println("eMap " + eMap)
    if (eMap.contains("Fields")) {
      val eList = eMap.get("Fields").get.asInstanceOf[List[Map[String, Any]]]
      for (l <- eList)
        fbuffer += getElementData(eMap.asInstanceOf[Map[String, String]], "Fields")
      val ele: Element = new Element(fbuffer.toList, null)
      println("ele 11111 " + ele.Fields(1).Name)
      lbuffer += ele

    }
    if (eMap.contains("Type")) {
      ContainerType = eMap.get("Type").get.asInstanceOf[String]
      if (eMap.contains("CollectionType")) {
        CollectionType = eMap.get("CollectionType").get.asInstanceOf[String]
      }
    }

    new Message("Container", null, eMap.get("Name").get.toString(), null, null, null, null, lbuffer.toList, false, null, null, null, null, ContainerType, CollectionType, null)

  }*/

  private def getConcept(eMap: Map[String, Any], key: String): List[Field] = {
    var lbuffer = new ListBuffer[Field]
    var concept: Concept = null
    if (eMap == null) throw new Exception("Concept Map is null")
    try {
      for (eKey: String <- eMap.keys) {
        val namestr = eMap.get(eKey).get.toString.split("\\.")
        if ((namestr != null) && (namestr.size == 2)) {
          concept.NameSpace = Some(namestr(0))
          concept.Name = Some(namestr(1))
          //  concept.Type = MdMgr.GetMdMgr.Attribute(namestr(0), namestr(1)).tType.toString()
          // concept.Type = MdMgr.GetMdMgr.Attribute(key, ver, onlyActive)(namestr(0), namestr(1)).tType.toString()
          val e = getcElement(concept, key)
          lbuffer += e
          //   val attr = MdMgr.GetMdMgr.Attribute(namestr(0), namestr(1))
        }
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    lbuffer.toList
  }

  private def getcElement(concept: Concept, eType: String): Field = {
    new Field(concept.NameSpace.getOrElse(null), concept.Name.getOrElse(null), concept.Type.getOrElse(null), null, eType, null)
  }

  private def getElement(eMap: Map[String, Any]): Element = {
    var fld: Element = null
    type keyMap = Map[String, String]
    if (eMap == null) throw new Exception("element Map is null")
    try {
      for (eKey: String <- eMap.keys) {
        if (eMap.get(eKey).get.isInstanceOf[keyMap]) {
          val element = eMap.get(eKey).get.asInstanceOf[Map[String, String]]
          fld = getElementData(element, eKey)
        }
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    fld
  }

  private def getElementData(field: Map[String, String], key: String): Element = {
    var fld: Element = null
    var name: String = ""
    var fldTypeVer: String = null
    var nativeName: String = ""

    var namespace: String = ""
    var ttype: String = ""
    var collectionType: String = ""
    type string = String;
    if (field == null) throw new Exception("element Map is null")
    try {
      if (field.contains("NameSpace") && (field.get("NameSpace").get.isInstanceOf[string]))
        namespace = field.get("NameSpace").get.asInstanceOf[String]

      if (field.contains("Name") && (field.get("Name").get.isInstanceOf[String])){
        name = field.get("Name").get.asInstanceOf[String].toLowerCase()
	nativeName = field.get("Name").get.asInstanceOf[String]
      }else throw new Exception("Field Name do not exist in " + key)

      if (field.contains("Type") && (field.get("Type").get.isInstanceOf[string])) {
        val fieldstr = field.get("Type").get.toString.split("\\.")
        if ((fieldstr != null) && (fieldstr.size == 2)) {
          namespace = fieldstr(0).toLowerCase()
          ttype = field.get("Type").get.asInstanceOf[String].toLowerCase()
        } else
          ttype = field.get("Type").get.asInstanceOf[String].toLowerCase()
        if (field.contains("CollectionType") && (field.get("CollectionType").get.isInstanceOf[string])) {
          collectionType = field.get("CollectionType").get.asInstanceOf[String].toLowerCase()
        }
        if (field.contains("Version") && (field.get("Version").get.isInstanceOf[string])) {
          fldTypeVer = field.get("Version").get.asInstanceOf[String].toLowerCase()
        }

      } else throw new Exception("Field Type do not exist in " + key)
      //log.debug("key========" + key)
      fld = new Element(namespace, name, ttype, collectionType, key, fldTypeVer, false, nativeName)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    fld
  }

  private def processConcept(conceptsStr: String, formatType: String) {
    val json = parse(conceptsStr)
    //println(json.values.asInstanceOf[Map[Any, Any]].contains("Concepts"))
    implicit val jsonFormats: Formats = DefaultFormats
    val ConceptList = json.extract[ConceptList]
    ConceptList.Concepts.foreach { concept =>
      log.debug("NameSpace => " + concept.NameSpace.getOrElse(null));
      log.debug("Name => " + concept.Name.getOrElse(None));
      log.debug("Type => " + concept.Type.getOrElse(None));
      log.debug("=========>");
    }

  }

  def containsIgnoreCase(list: List[String], s: String): Boolean = {

    list.foreach(l => {
      if (l.equalsIgnoreCase(s))
        return true;
    })
    return false;
  }

}
