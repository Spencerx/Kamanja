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

package com.ligadata.outputmsgdef

import scala.collection.mutable.{ Map, HashMap, MultiMap, Set, SortedSet, ArrayBuffer }
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.ligadata.kamanja.metadata.ObjType._
import com.ligadata.kamanja.metadata._
import com.ligadata.kamanja.metadata.MdMgr._
import com.ligadata.Exceptions._
import org.apache.log4j.Logger
import scala.collection.mutable.ListBuffer
import com.ligadata.Exceptions.StackTrace

class OutputMessage(var NameSpace: String, var Name: String, var Version: String, var Description: String, var Queue: String, var PartitionKey: List[String], var Defaults: List[scala.collection.mutable.Map[String, String]], var DataDeclaration: List[scala.collection.mutable.Map[String, String]], var OutputFormat: String)

//case class OutputMessageStruct(NameSpace: String, Name: String, Version: String, Description: String, Queue: String, PartitionKey: List[String], Defaults: List[scala.collection.immutable.Map[String, String]], DataDeclaration: List[scala.collection.immutable.Map[String, String]], OutputFormat: String)
//case class OutputMessageDefinition(OutputMessage: OutputMessageStruct)

object OutputMsgDefImpl {

  val logger = this.getClass.getName
  lazy val log = Logger.getLogger(logger)

  /**
   * ${System.PatientDetails.Name.FirstName}
   * System.Beneficiary.inpatient_claims.claim_id
   * outputQ ===:TestOutMq_1
   * paritionKeys ===:List(${System.DrDigMsg.ent_srt_cde}, ${System.DrDigMsg.ent_acc_num})
   * dataDeclaration ===:List(Map(Name -> Delim, Default -> ,))
   * Defaults ===:List(Map(Name -> System.DrDigMsg.ent_dte, Default -> 15001))
   * outputFormat ===:{ "_DD": "${System.DrivingDigital._DD}", "_HD": "${System.DrivingDigital._HD}", "_PNA": [ "${System.DrivingDigital._PNA}" ${Delim} "SecondValue"  ], "EntryDate": "${System.DrDigMsg.ent_dte}" }
   *
   */
  def parseOutputMessageDef(outputmsgDefJson: String, formatType: String): OutputMsgDef = {
    var outputMsgDef = new OutputMsgDef
    try {

      implicit val jsonFormats: Formats = DefaultFormats
      val outputMessageDef = parseOutMsg(outputmsgDefJson)
      if (outputMessageDef == null)
        throw new Exception("output message definition info do not exists")

      log.debug("Name " + outputMessageDef.Name)
      log.debug("NameSpace " + outputMessageDef.NameSpace)
      log.debug("Version " + outputMessageDef.Version)
      log.debug("Queue" + outputMessageDef.Queue)
      log.debug("OutputFormat " + outputMessageDef.OutputFormat)
      outputMessageDef.DataDeclaration.foreach(f =>  log.debug("f " + f))
      outputMessageDef.Defaults.foreach(f =>  log.debug("f " + f))

      val outputQ = outputMessageDef.Queue.toLowerCase()
      val paritionKeys = outputMessageDef.PartitionKey
      val dataDeclaration = outputMessageDef.DataDeclaration
      val outputFormat = outputMessageDef.OutputFormat.toLowerCase()
      val defaults = outputMessageDef.Defaults
      val name = outputMessageDef.Name.toLowerCase()
      val nameSpace = outputMessageDef.NameSpace.toLowerCase()

      val versionStr = outputMessageDef.Version

      if (versionStr == null || versionStr.trim() == "")
        throw new Exception(" Please provide the version in the Output Message definition")

      val version = MdMgr.ConvertVersionToLong(versionStr)

      if (name == null || name.trim() == "")
        throw new Exception(" Please provide the Name in the Output Message definition")
      if (nameSpace == null || nameSpace.trim() == "")
        throw new Exception(" Please provide the NameSpace in the Output Message definition")
      if (outputQ == null || outputQ.trim() == "")
        throw new Exception(" Please provide the outputQ in the Output Message definition")
      if (outputFormat == null || outputFormat.trim() == "")
        throw new Exception(" Please provide the Output Format in the Output Message definition")

      log.info("outputQ ===:" + outputQ)
      log.info("paritionKeys ===:" + paritionKeys)
      log.info("dataDeclaration ===:" + dataDeclaration)
      log.info("Defaults ===:" + defaults)
      log.info("outputFormat ===:" + outputFormat)

      var dfaults = scala.collection.mutable.Map[String, String]()
      defaults.foreach(dflt => {
        if ((dflt.getOrElse("Name", null) != null) && (dflt.getOrElse("Default", null) != null))
          dfaults(dflt.get("Name").get.toString().toLowerCase()) = dflt.get("Default").get.toString()
      })
      var i: Int = 1
      //var Fields : Map[(String, String), Set[(Array[(String, String)], String)]] = _  // Fields from Message/Model. Map Key is Message/Model Full Qualified Name as first value in key tuple and "Mdl" Or "Msg" String as the second value in key tuple. Value is Set of fields(Array[(String, String)](("inpatient_claims", "System.arrayOfInpatientClaims"), ("claim_id", "System.Long"))) & corresponding Default Value (if not present NULL)
      var Fields: scala.collection.mutable.Map[(String, String), scala.collection.mutable.Set[(Array[(String, String)], String)]] = scala.collection.mutable.Map()
      var fieldscheck: Set[(String, String)] = Set[(String, String)]()
      var partionFieldKeys = Array[(String, Array[(String, String)], String, String)]()

      if (paritionKeys != null && paritionKeys.size > 0) {
        paritionKeys.foreach(partionkey => {
          val (fullname, fieldsInfo, typeOf, fullpartionkey) = getFieldsInfo(partionkey)
          partionFieldKeys = partionFieldKeys :+ (fullname, fieldsInfo.toArray, typeOf, fullpartionkey)
          var defaultValue: String = null
          if (dfaults.contains(partionkey)) {
            defaultValue = dfaults(partionkey)
          }
          var fldInfo: Array[(String, String)] = fieldsInfo.asInstanceOf[Array[(String, String)]]
          var value: Array[(String, String)] = Array[(String, String)]()
          value = fldInfo
          if (Fields.contains((fullname, typeOf)))
            Fields((fullname, typeOf)) += ((value, defaultValue))
          else {
            var valueVal: scala.collection.mutable.Set[(Array[(String, String)], String)] = scala.collection.mutable.Set[(Array[(String, String)], String)]()
            valueVal += ((fldInfo, defaultValue))
            Fields((fullname, typeOf)) = (valueVal)
          }

        })
      }

      var dataDeclrtion = scala.collection.mutable.Map[String, String]()
      if (dataDeclaration != null && dataDeclaration.size > 0) {
        dataDeclaration.foreach(dd => { dd.foreach(d => { dataDeclrtion(d._1.toLowerCase()) = d._2 }) })
      }

      val allOutputFormatFlds = extractOutputFormat(outputFormat)
      // var outputFormatFields = Map[(String, Array[(String, String)], String, String)]()

      if (allOutputFormatFlds != null && allOutputFormatFlds.size > 0) {
        allOutputFormatFlds.foreach(outputFormatFld => {
          val outputFmtFld = outputFormatFld.substring(2, outputFormatFld.length() - 1).toLowerCase()
          var defaultValue: String = null
          if (dfaults.contains(outputFmtFld)) {
            defaultValue = dfaults(outputFmtFld)
          }
          val (fullname, fieldsInfo, typeOf, fullFieldkey) = getFieldsInfo(outputFormatFld)

          var fldInfo: Array[(String, String)] = fieldsInfo.asInstanceOf[Array[(String, String)]]
          var value: Array[(String, String)] = Array[(String, String)]()
          value = fldInfo
          if (Fields.contains((fullname, typeOf)))
            Fields((fullname, typeOf)) += ((value, defaultValue))
          else {
            var valueVal: scala.collection.mutable.Set[(Array[(String, String)], String)] = scala.collection.mutable.Set[(Array[(String, String)], String)]()
            valueVal += ((fldInfo, defaultValue))
            Fields((fullname, typeOf)) = (valueVal)
          }
        })
      }

      outputMsgDef = MdMgr.GetMdMgr.MakeOutputMsg(nameSpace.toLowerCase(), name.toLowerCase(), version, outputQ, partionFieldKeys, dfaults, dataDeclrtion, Fields, outputFormat)

    } catch {
      case e: ObjectNolongerExistsException => {
        log.error(s"Either Model or Message or Container do not exists in Metadata. Error: " + e.getMessage)
        throw e
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.trace("Error " + e.getMessage()+"\nStackTrace:"+stackTrace)
        throw e
      }
    }
    outputMsgDef
  }

  private def extractOutputFormat(outputformat: String): Array[String] = {
    val extractor = """\$\{([^}]+)\}""".r
    val finds = extractor.findAllIn(outputformat)
    val allOutputFnds = finds.toArray
    allOutputFnds
  }

  private def getFieldsInfo(Fieldkey: String): (String, Array[(String, String)], String, String) = {
    var fieldsInfo: ArrayBuffer[(String, String)] = new ArrayBuffer[(String, String)]()
    var partitionKeys = Array[(String, Array[(String, String)], String, String)]()
    var fullname: String = ""
    var fullpartionkey: String = ""
    var typeof: String = ""
    try {
      if (Fieldkey == null || Fieldkey.trim() == "")
        throw new Exception("Field do not exists")

      fullpartionkey = Fieldkey.substring(2, Fieldkey.length() - 1)

      val partionKeyParts = fullpartionkey.split("\\.")
      if (partionKeyParts.size < 3)
        throw new Exception("Please provide the fiels in format of Namespace.Name.fieldname")
      
      val(namespace, name, field) = com.ligadata.kamanja.metadata.Utils.parseNameToken(fullpartionkey)
      val (containerDef, messageDef, modelDef) = getModelMsgContainer(namespace, name)
      val (childs, typeOf) = getModelMsgContainerChilds(containerDef, messageDef, modelDef)
      typeof = typeOf
      for (i <- 2 until partionKeyParts.size) {
        val fld = partionKeyParts(i).toString().toLowerCase()

        if (i == 2) {
          val fldType = getFieldTypeFromMsgCtr(childs, fld)
          fieldsInfo += ((fld, fldType))

        } else if (i > 2) {
          fieldsInfo.foreach(f => log.info("====fieldInfos========" + f._1 + "======" + f._2))
          val parent = fieldsInfo(i - 3)
          val parentType = parent._2.toLowerCase()
          val fieldName = partionKeyParts(i).toString().toLowerCase()
          val fieldType = getFieldType(fieldName, parentType)
          fieldsInfo += ((fieldName, fieldType))
        }

      }
      fullname = namespace + "." + name

    } catch {
      case e: ObjectNolongerExistsException => {
        log.error(s"Either Model or Message or Container do not exists in Metadata. Error: "+ e.getMessage)
        throw e
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.trace("Error " + e.getMessage()+"\nStackTrace:"+stackTrace)
      }
    }
    (fullname, fieldsInfo.toArray, typeof, fullpartionkey.toLowerCase())
  }

  private def getFieldTypeFromMsgCtr(childs: Map[String, Any], fld: String): String = {
    var fldtype: String = ""
    try {
      childs.foreach(f => {
        if (fld.equals(f._1.toString.toLowerCase())) {
          val typName = f._2.asInstanceOf[AttributeDef].aType.Name
          val typNameSpace = f._2.asInstanceOf[AttributeDef].aType.NameSpace
          fldtype = typNameSpace + "." + typName
          if (typName != null && typName.trim() != "") {
            val typetype = f._2.asInstanceOf[AttributeDef].tTypeType.toString().toLowerCase()
          }
        }
      })
    } catch {
      case e: ObjectNolongerExistsException => {
        log.error(s"Either Model or Message or Container do not exists in Metadata. Error: "+ e.getMessage)
        throw e
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.trace("Error " + e.getMessage()+"\nStackTrace:"+stackTrace)
      }
    }
    fldtype.toLowerCase()
  }

  private def getFieldType(childFldName: String, ParentType: String): String = {
    var fieldType: String = ""

    try {
      val typ = MdMgr.GetMdMgr.Type(ParentType, -1, true)
      if (typ == null || typ == None)
        throw new Exception("Type do not exist in metadata for " + ParentType)

      log.info("typ.get.tType : " + typ.get.tType)

      val typetype = typ.get.tType
      if (typetype != null) {
        if (typetype.toString().toLowerCase().equals("tstruct")) {
          val (containerDef, messageDef, modelDef) = getModelMsgContainer(typ.get.NameSpace, typ.get.Name)

          val (childs, typeOf) = getModelMsgContainerChilds(containerDef, messageDef, modelDef)
          fieldType = getFieldTypeFromMsgCtr(childs, childFldName)
        } else if (typetype.equals("tscalar")) {
          fieldType = typ.get.physicalName
        } else if (typetype.equals("tarray")) {
          val arrayType = typ.get.asInstanceOf[ArrayTypeDef]
          if (arrayType.elemDef.tTypeType.toString().toLowerCase().equals("tscalar")) {
            fieldType = typ.get.typeString
          } else {
            fieldType = getFieldType(childFldName, arrayType.elemDef.NameSpace + "." + arrayType.elemDef.Name)
          }

        } else if (typetype.toString().toLowerCase().equals("tarraybuf")) {
          val arrayBufType = typ.get.asInstanceOf[ArrayBufTypeDef]
          if (arrayBufType.elemDef.tTypeType.toString().toLowerCase().equals("tscalar")) {
            fieldType = typ.get.typeString
          } else {
            fieldType = getFieldType(childFldName, arrayBufType.elemDef.NameSpace + "." + arrayBufType.elemDef.Name)
          }
        }
      }
    } catch {
      case e: ObjectNolongerExistsException => {
        log.error(s"Either Model or Message or Container do not exists in Metadata. Error: "+ e.getMessage)
        throw e
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.trace("Error " + e.getMessage())
      }
    }
    fieldType
  }

  private def getModelMsgContainer(namespace: String, name: String): (ContainerDef, MessageDef, ModelDef) = {
    var msgdefObj: MessageDef = null
    var cntainerObj: ContainerDef = null
    var modelObj: ModelDef = null
    var prevVerMsgObjstr: String = ""
    var childs: ArrayBuffer[(String, String)] = ArrayBuffer[(String, String)]()
    
    if (namespace == null || namespace.trim() == "")
      throw new Exception("Proper Namespace do not exists in message/container definition")
    if (name == null || name.trim() == "")
      throw new Exception("Proper Name do not exists in message")

    val msgdef = MdMgr.GetMdMgr.Message(namespace.toString.toLowerCase(), name.toString.toLowerCase(), -1, false)
    val model = MdMgr.GetMdMgr.Model(namespace.toString.toLowerCase(), name.toString.toLowerCase(), -1, false)
    val container = mdMgr.Container(namespace.toString.toLowerCase(), name.toString.toLowerCase(), -1, false)

    msgdef match {
      case None => {
        msgdefObj = null
      }
      case Some(m) =>
        msgdefObj = m.asInstanceOf[MessageDef]
    }

    container match {
      case None => {
        cntainerObj = null
      }
      case Some(c) =>
        cntainerObj = c.asInstanceOf[ContainerDef]
    }

    model match {
      case None => {
        modelObj = null
      }
      case Some(m) =>
        modelObj = m.asInstanceOf[ModelDef]
    }

    if (msgdefObj == null && cntainerObj == null && modelObj == null) {
      throw new ObjectNolongerExistsException(s"Either Model or Message or Container do not exists in Metadata for $namespace.$name given in output Message definition.")
    }

    (cntainerObj, msgdefObj, modelObj)

  }

  private def getModelMsgContainerChilds(container: ContainerDef, message: MessageDef, model: ModelDef): (Map[String, Any], String) = {
    var prevVerCtrdef: ContainerDef = new ContainerDef()
    var prevVerMsgdef: MessageDef = new MessageDef()
    var typeOf = ""
    var childs: Map[String, Any] = Map[String, Any]()
    var prevVerMsgBaseTypesIdxArry = new ArrayBuffer[String]

    try {
      if (container != null) {
        val fixed = container.containerType.asInstanceOf[StructTypeDef].IsFixed

        if (fixed) {
          val memberDefs = container.containerType.asInstanceOf[StructTypeDef].memberDefs
          if (memberDefs != null) {
            log.info("==== fixedcontainer")
            childs ++= memberDefs.filter(a => (a.isInstanceOf[AttributeDef])).map(a => (a.Name, a))
          }
          typeOf = "fixedcontainer"
        } else if (!fixed) {
          val attrMap = container.containerType.asInstanceOf[MappedMsgTypeDef].attrMap
          if (attrMap != null) {
            childs ++= attrMap.filter(a => (a._2.isInstanceOf[AttributeDef])).map(a => (a._2.Name, a._2))
          }
          typeOf = "mappedcontainer"
          log.info(typeOf)
        }
      } else if (message != null) {
        val fixed = message.containerType.asInstanceOf[StructTypeDef].IsFixed

        if (fixed) {
          val memberDefs = message.containerType.asInstanceOf[StructTypeDef].memberDefs
          if (memberDefs != null) {
            childs ++= memberDefs.filter(a => (a.isInstanceOf[AttributeDef])).map(a => (a.Name, a))
          }
          typeOf = "fixedmessage"
          log.info(typeOf)
        } else if (!fixed) {
          val attrMap = message.containerType.asInstanceOf[MappedMsgTypeDef].attrMap
          if (attrMap != null) {
            childs ++= attrMap.filter(a => (a._2.isInstanceOf[AttributeDef])).map(a => (a._2.Name, a._2))
          }
          typeOf = "mappedmessage"
          log.info(typeOf)
        }
      } else if (model != null) {
        childs ++= model.inputVars.map(a => { (a.Name, a) })
        childs ++= model.outputVars.map(a => (a.Name, a))
        typeOf = "model"
        log.info(typeOf)
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("Error " + e.getMessage()+"\nStackTrace:"+stackTrace)
        throw e
      }
    }
    (childs, typeOf)
  }

  def parseOutMsg(outputmsgDefJson: String): OutputMessage = {
    var partitionKeysList: List[String] = null
    var dataDeclBuffer = new ListBuffer[Map[String, String]]
    var defaultsListBuffer = new ListBuffer[Map[String, String]]
    var defaults = new ListBuffer[Map[String, String]]
    type MapList = List[scala.collection.immutable.Map[String, String]]
    type StringList = List[String]
    type keyMap = scala.collection.immutable.Map[String, String]
    implicit val jsonFormats: Formats = DefaultFormats
    val map = parse(outputmsgDefJson).values.asInstanceOf[scala.collection.immutable.Map[String, Any]]
    val outputKey = "OutputMessage"

    if (map.contains(outputKey)) {
      val outputmsg = map.get(outputKey).get.asInstanceOf[scala.collection.immutable.Map[String, Any]]
      log.debug("outputmsg 1 : " + outputmsg)

      if (outputmsg != null) {
        if (outputmsg.getOrElse("NameSpace", null) == null)
          throw new Exception("Please provide the Name space in the output message definition ")

        if (outputmsg.getOrElse("Name", null) == null)
          throw new Exception("Please provide the Name of the output message definition ")

        if (outputmsg.getOrElse("Version", null) == null)
          throw new Exception("Please provide the Version of the output message definition ")

        if (outputmsg.getOrElse("Queue", null) == null)
          throw new Exception("Please provide the output Queue of the output message definition ")

        if (outputmsg.getOrElse("OutputFormat", null) == null)
          throw new Exception("Please provide the OutputFormat of the output message definition ")

        val nameSpace = outputmsg.get("NameSpace").get.toString
        val name = outputmsg.get("Name").get.toString
        val version = outputmsg.get("Version").get.toString
        val queue = outputmsg.get("Queue").get.toString
        val desc: String = outputmsg.getOrElse("Description", "").toString
        val outputFrmt = outputmsg.get("OutputFormat").get.toString

        val dataDecl = outputmsg.getOrElse("DataDeclaration", null)

        if (dataDecl != null && dataDecl.isInstanceOf[MapList]) {
          val dataDeclList = dataDecl.asInstanceOf[MapList]
          for (l <- dataDeclList) {
            val dataDeclMap = l.asInstanceOf[scala.collection.immutable.Map[String, String]]
            var dd: Map[String, String] = Map[String, String]()
            dataDeclMap.foreach(f => { dd(f._1) = f._2 })
            dataDeclBuffer += dd
          }
        }

        val defaults = outputmsg.getOrElse("Defaults", null)
        if (defaults != null && defaults.isInstanceOf[MapList]) {
          val dfltslList = defaults.asInstanceOf[MapList]
          for (l <- dfltslList) {
            val dfltslMap = l.asInstanceOf[scala.collection.immutable.Map[String, String]]
            var dd: Map[String, String] = Map[String, String]()
            dfltslMap.foreach(f => { dd(f._1) = f._2 })
            defaultsListBuffer += dd
          }
        }

        val partitionKeys = outputmsg.getOrElse("PartitionKey", null)
        if (partitionKeys != null && partitionKeys.isInstanceOf[StringList]) {
          partitionKeysList = partitionKeys.asInstanceOf[StringList]
        }
        return new OutputMessage(nameSpace, name, version, desc, queue, partitionKeysList, defaultsListBuffer.toList, dataDeclBuffer.toList, outputFrmt)
      } else return null

    } else throw new Exception("Incorrect Output Message Definition json")
  }

}