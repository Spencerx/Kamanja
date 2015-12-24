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

import com.ligadata.kamanja.metadata._
import scala.collection._
import scala.collection.mutable.ArrayBuffer
import com.ligadata.Exceptions.StackTrace
import org.apache.logging.log4j.{ Logger, LogManager }

class BaseTypesHandler {

  private val pad1 = "\t"
  private val pad2 = "\t\t"
  private val pad3 = "\t\t\t"
  private val pad4 = "\t\t\t\t"
  private val newline = "\n"
  val transactionid: String = "transactionid"
  var cnstObjVar = new ConstantMsgObjVarGenerator
  private val LOG = LogManager.getLogger(getClass)

  def handleBaseTypes(keysSet: Set[String], fixed: String, typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], f: Element, msgVersion: String, childs: Map[String, Any], prevVerMsgBaseTypesIdxArry: ArrayBuffer[String], recompile: Boolean, mappedTypesABuf: ArrayBuffer[String], firstTimeBaseType: Boolean, msg: Message): (List[(String, String)], List[(String, String, String, String, Boolean, String)], Set[String], ArrayBuffer[String], ArrayBuffer[String], Array[String]) = {
    var scalaclass = new StringBuilder(8 * 1024)
    var assignCsvdata = new StringBuilder(8 * 1024)
    var assignJsondata = new StringBuilder(8 * 1024)
    var assignXmldata = new StringBuilder(8 * 1024)
    var assignKvData = new StringBuilder(8 * 1024)
    var addMsg = new StringBuilder(8 * 1024)
    var list = List[(String, String)]()
    var argsList = List[(String, String, String, String, Boolean, String)]()
    var jarset: Set[String] = Set();
    val pad1 = "\t"
    val pad2 = "\t\t"
    val pad3 = "\t\t\t"
    val pad4 = "\t\t\t\t"
    val newline = "\n"
    var keysStr = new StringBuilder(8 * 1024)
    var mappedPrevVerMatchkeys = new StringBuilder(8 * 1024)
    var typeImpl = new StringBuilder(8 * 1024)
    var fname: String = ""
    var serializedBuf = new StringBuilder(8 * 1024)
    var deserializedBuf = new StringBuilder(8 * 1024)
    var prevObjDeserializedBuf = new StringBuilder(8 * 1024)
    var convertOldObjtoNewObjBuf = new StringBuilder(8 * 1024)
    var mappedPrevTypNotMatchkeys = new StringBuilder(8 * 1024)
    var prevObjTypNotMatchDeserializedBuf = new StringBuilder(8 * 1024)
    var prevVerMsgBaseTypesIdxArry1: ArrayBuffer[String] = new ArrayBuffer[String]
    var fixedMsgGetKeyStrBuf = new StringBuilder(8 * 1024)
    var getNativeKeyValues = new StringBuilder(8 * 1024)

    var withMethod = new StringBuilder(8 * 1024)
    var fromFuncBaseTypesBuf = new StringBuilder(8 * 1024)
    var returnAB = new ArrayBuffer[String]

    var mapBaseTypesSetRet: Set[Int] = Set()
    try {

      if (typ.get.implementationName.isEmpty())
        throw new Exception("Implementation Name not found in metadata for namespace %s" + f.Ttype)

      if (typ.getOrElse("None").equals("None"))
        throw new Exception("Type not found in metadata for Name: " + f.Name + " , NameSpace: " + f.NameSpace + " , Version: " + msgVersion + " , Type : " + f.Ttype)

      if (typ.get.typeString.isEmpty())
        throw new Exception("Type not found in metadata for namespace %s" + f.Ttype)

      argsList = (f.NameSpace, f.Name, typ.get.NameSpace, typ.get.Name, false, null) :: argsList

      fname = typ.get.implementationName + ".Input"

      if ((typ.get.dependencyJarNames != null) && (typ.get.JarName != null))
        jarset = jarset + typ.get.JarName ++ typ.get.dependencyJarNames
      else if (typ.get.JarName != null)
        jarset = jarset + typ.get.JarName
      else if (typ.get.dependencyJarNames != null)
        jarset = jarset ++ typ.get.dependencyJarNames

      val dval: String = cnstObjVar.getDefVal(typ.get.typeString.toLowerCase())
      list = (f.Name, f.Ttype) :: list

      var baseTypId = -1

      if (fixed.toLowerCase().equals("true")) {

        if (f.SystemField) {
          scalaclass = scalaclass.append("")
          withMethod = withMethod.append("")

        } else {
          scalaclass = scalaclass.append("%svar %s:%s = _ ;%s".format(pad1, f.Name, typ.get.physicalName, newline))
          assignCsvdata.append("%s%s = %s(list(inputdata.curPos));\n%sinputdata.curPos = inputdata.curPos+1;\n".format(pad2, f.Name, fname, pad2))
          assignJsondata.append("%s %s = %s(map.getOrElse(\"%s\", %s).toString);%s".format(pad2, f.Name, fname, f.Name, dval, newline))
          assignXmldata.append("%sval _%sval_  = (xml \\\\ \"%s\").text.toString %s%sif (_%sval_  != \"\")%s%s =  %s( _%sval_ ) else %s = %s;%s".format(pad3, f.Name, f.Name, newline, pad3, f.Name, pad2, f.Name, fname, f.Name, f.Name, dval, newline))
          assignKvData.append("%s %s = %s(map.getOrElse(\"%s\", %s).toString);%s".format(pad2, f.Name, fname, f.Name, dval, newline))

          withMethod = withMethod.append("%s%s def with%s(value: %s) : %s = {%s".format(newline, pad1, f.Name, typ.get.typeString, msg.Name, newline))
          withMethod = withMethod.append("%s this.%s = value %s".format(pad1, f.Name, newline))
          withMethod = withMethod.append("%s return this %s %s } %s".format(pad1, newline, pad1, newline))
          getNativeKeyValues = getNativeKeyValues.append("%s keyValues(\"%s\") = (\"%s\", %s); %s".format(pad1, f.Name, f.NativeName, f.Name, newline))

        }

      } else if (fixed.toLowerCase().equals("false")) {

        if (keysSet != null)
          keysSet.foreach { key =>
            if (f.Name.toLowerCase().equals(key.toLowerCase()))
              scalaclass = scalaclass.append("%svar %s:%s = _ ;%s".format(pad1, f.Name, typ.get.physicalName, newline))
          }
        var typstring = typ.get.implementationName
        if (mappedTypesABuf.contains(typstring)) {
          if (mappedTypesABuf.size == 1 && firstTimeBaseType)
            baseTypId = mappedTypesABuf.indexOf(typstring)
        } else {

          mappedTypesABuf += typstring
          baseTypId = mappedTypesABuf.indexOf(typstring)
        }

        keysStr.append("(\"" + f.Name + "\", " + mappedTypesABuf.indexOf(typstring) + "),")
        if (f.SystemField) {
          withMethod = withMethod.append("")

        } else {
          withMethod = withMethod.append("%s%s def with%s(value: %s) : %s = {%s".format(newline, pad1, f.Name, typ.get.typeString, msg.Name, newline))
          withMethod = withMethod.append("%s fields(\"%s\") = (%s, value) %s".format(pad1, f.Name, mappedTypesABuf.indexOf(typstring), newline))
          withMethod = withMethod.append("%s return this %s %s } %s".format(pad1, newline, pad1, newline))
        }

      }

      serializedBuf = serializedBuf.append(serializeMsgContainer(typ, fixed, f, baseTypId))
      deserializedBuf = deserializedBuf.append(deSerializeMsgContainer(typ, fixed, f, baseTypId))
      val (prevObjDeserialized, convertOldObjtoNewObj, mappedPrevVerMatch, mappedPrevTypNotMatchkey, prevObjTypNotMatchDeserialized, prevVerMsgBaseTypesIdxArryBuf) = prevObjDeserializeMsgContainer(typ, fixed, f, childs, baseTypId, prevVerMsgBaseTypesIdxArry)
      prevObjDeserializedBuf = prevObjDeserializedBuf.append(prevObjDeserialized)
      convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append(convertOldObjtoNewObj)
      mappedPrevVerMatchkeys = mappedPrevVerMatchkeys.append(mappedPrevVerMatch)
      mappedPrevTypNotMatchkeys = mappedPrevTypNotMatchkeys.append(mappedPrevTypNotMatchkey)
      prevObjTypNotMatchDeserializedBuf = prevObjTypNotMatchDeserializedBuf.append(prevObjTypNotMatchDeserialized)
      prevVerMsgBaseTypesIdxArry1 = prevVerMsgBaseTypesIdxArryBuf
      fixedMsgGetKeyStrBuf.append("%s if(key.equals(\"%s\")) return %s; %s".format(pad1, f.Name, f.Name, newline))
      fromFuncBaseTypesBuf.append(fromFunc(typ, fixed, f, baseTypId))

      var nativeKeyMap: String = "";
      if (!f.Name.equalsIgnoreCase(transactionid)) {
        nativeKeyMap = "(\"%s\", \"%s\"), ".format(f.Name, f.NativeName)
      }
      returnAB += scalaclass.toString
      returnAB += assignCsvdata.toString
      returnAB += assignJsondata.toString
      returnAB += assignXmldata.toString
      returnAB += addMsg.toString
      returnAB += keysStr.toString
      returnAB += typeImpl.toString
      returnAB += serializedBuf.toString
      returnAB += deserializedBuf.toString
      returnAB += prevObjDeserializedBuf.toString
      returnAB += convertOldObjtoNewObjBuf.toString
      returnAB += mappedPrevVerMatchkeys.toString
      returnAB += mappedPrevTypNotMatchkeys.toString
      returnAB += prevObjTypNotMatchDeserializedBuf.toString
      returnAB += fixedMsgGetKeyStrBuf.toString
      returnAB += withMethod.toString
      returnAB += fromFuncBaseTypesBuf.toString
      returnAB += assignKvData.toString
      returnAB += nativeKeyMap.toString
      returnAB += getNativeKeyValues.toString

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    //(scalaclass.toString, assignCsvdata.toString, assignJsondata.toString, assignXmldata.toString, list, argsList, addMsg.toString, keysStr.toString, typeImpl.toString, jarset, serializedBuf.toString, deserializedBuf.toString, prevObjDeserializedBuf.toString, convertOldObjtoNewObjBuf.toString, mappedTypesABuf, mappedPrevVerMatchkeys.toString, mappedPrevTypNotMatchkeys.toString, prevObjTypNotMatchDeserializedBuf.toString, prevVerMsgBaseTypesIdxArry1, fixedMsgGetKeyStrBuf.toString, withMethod.toString, fromFuncOfFixed.toString)
    (list, argsList, jarset, mappedTypesABuf, prevVerMsgBaseTypesIdxArry1, returnAB.toArray)

  }

  def serializeMsgContainer(typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], fixed: String, f: Element, mappedMsgBaseTypeIdx: Int): String = {
    serialize(typ, fixed, f, mappedMsgBaseTypeIdx)
  }

  def deSerializeMsgContainer(typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], fixed: String, f: Element, mappedMsgBaseTypeIdx: Int): String = {
    deSerialize(typ, fixed, f, mappedMsgBaseTypeIdx)
  }

  def prevObjDeserializeMsgContainer(typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], fixed: String, f: Element, childs: Map[String, Any], baseTypIdx: Int, prevVerMsgBaseTypesIdxArry: ArrayBuffer[String]): (String, String, String, String, String, ArrayBuffer[String]) = {
    prevObjDeserialize(typ, fixed, f, childs, baseTypIdx, prevVerMsgBaseTypesIdxArry)
  }

  private def serialize(typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], fixed: String, f: Element, mappedMsgBaseTypeIdx: Int): String = {
    var serializedBuf = new StringBuilder(8 * 1024)
    try {
      if (typ.getOrElse("None").equals("None"))
        throw new Exception("Type not found in metadata for Name: " + f.Name + " , NameSpace: " + f.NameSpace + " , Type : " + f.Ttype)
      if (f.Name == null || f.Name.trim() == "")
        throw new Exception("Field name do not exists ")

      val serType = typ.get.implementationName + ".SerializeIntoDataOutputStream"
      if (serType != null && serType.trim() != "") {
        if (fixed.toLowerCase().equals("true")) {
          serializedBuf = serializedBuf.append("%s%s(%s,%s);%s".format(pad1, serType, "dos", f.Name, newline))
        } else if (fixed.toLowerCase().equals("false")) {
          if (mappedMsgBaseTypeIdx != -1)
            serializedBuf = serializedBuf.append("%s case %s => %s(dos, field._2._2.asInstanceOf[%s])  %s".format(pad1, mappedMsgBaseTypeIdx, serType, typ.get.physicalName, newline))
        }
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        throw new Exception("Exception occured " + e.getCause())
      }
    }

    serializedBuf.toString
  }

  private def deSerialize(typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], fixed: String, f: Element, mappedMsgBaseTypeIdx: Int): String = {
    var deserializedBuf = new StringBuilder(8 * 1024)

    try {
      if (typ.getOrElse("None").equals("None"))
        throw new Exception("Type not found in metadata for Name: " + f.Name + " , NameSpace: " + f.NameSpace + " , Type : " + f.Ttype)
      if (f.Name == null || f.Name.trim() == "")
        throw new Exception("Field name do not exists")

      val implName = typ.get.implementationName
      if (implName == null || implName.trim() == "")
        throw new Exception("Implementation Name do not exist in Type")

      val deserType = implName + ".DeserializeFromDataInputStream"
      val dis = "dis"
      if (deserType != null && deserType.trim() != "") {
        if (fixed.toLowerCase().equals("true")) {
          deserializedBuf = deserializedBuf.append("%s%s = %s(%s);%s".format(pad1, f.Name, deserType, dis, newline))
        } else if (fixed.toLowerCase().equals("false")) {
          if (mappedMsgBaseTypeIdx != -1)
            deserializedBuf = deserializedBuf.append("%s case %s => fields(key) = (typIdx, %s(dis));%s".format(pad1, mappedMsgBaseTypeIdx, deserType, newline))

        }
      }

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        throw new Exception("Exception occured " + e.getCause())
      }
    }

    deserializedBuf.toString
  }

  private def prevObjDeserialize(typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], fixed: String, f: Element, childs: Map[String, Any], baseTypIdx: Int, prevVerMsgBaseTypesIdxArry: ArrayBuffer[String]): (String, String, String, String, String, ArrayBuffer[String]) = {
    var prevObjDeserializedBuf = new StringBuilder(8 * 1024)
    var convertOldObjtoNewObjBuf = new StringBuilder(8 * 1024)
    var mappedPrevVerMatchkeys = new StringBuilder(8 * 1024)
    var mappedPrevTypNotrMatchkeys = new StringBuilder(8 * 1024)
    var prevObjTypNotMatchDeserializedBuf = new StringBuilder(8 * 1024)

    try {
      if (typ.getOrElse("None").equals("None"))
        throw new Exception("Type not found in metadata for Name: " + f.Name + " , NameSpace: " + f.NameSpace + " , Type : " + f.Ttype)
      if (f.Name == null || f.Name.trim() == "")
        throw new Exception("Field name do not exists")
      if (typ.get.FullName == null || typ.get.FullName.trim() == "")
        throw new Exception("Full name of Type " + f.Ttype + " do not exists in metadata ")

      var memberExists: Boolean = false
      var membrMatchTypeNotMatch = false // for mapped messages to handle if prev ver obj and current version obj member types do not match...
      var childTypeImplName: String = ""
      var childtypeName: String = ""
      var childtypePhysicalName: String = ""

      var sameType: Boolean = false
      if (childs != null) {
        if (childs.contains(f.Name)) {
          var child = childs.getOrElse(f.Name, null)
          if (child != null) {
            val typefullname = child.asInstanceOf[AttributeDef].aType.FullName
            childtypeName = child.asInstanceOf[AttributeDef].aType.tTypeType.toString
            childtypePhysicalName = child.asInstanceOf[AttributeDef].aType.physicalName
            if (typefullname != null && typefullname.trim() != "" && typefullname.equals(typ.get.FullName)) {
              memberExists = true

            } else {
              membrMatchTypeNotMatch = true
              childTypeImplName = child.asInstanceOf[AttributeDef].aType.implementationName
            }
          }
        }
      }
      if (memberExists) {
        if (fixed.toLowerCase().equals("true")) {

          prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s%s = prevVerObj.%s;%s".format(pad1, f.Name, f.Name, newline))
          convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append("%s%s = oldObj.%s;%s".format(pad2, f.Name, f.Name, newline))
        } else if (fixed.toLowerCase().equals("false")) {
          mappedPrevVerMatchkeys.append("\"" + f.Name + "\",")
          //if (baseTypIdx != -1)
          //prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s case %s => fields(key) = (typIdx, prevObjfield._2._2);)%s".format(pad2, baseTypIdx, newline))

          //prevObjDeserializedBuf = prevObjDeserializedBuf.append("%sset(\"%s\", prevVerObj.getOrElse(\"%s\", null))%s".format(pad1, f.Name, f.Name, newline))
          // convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append("%sset(\"%s\", oldObj.getOrElse(\"%s\", null))%s".format(pad2, f.Name, f.Name, newline))
        }
      }
      if (membrMatchTypeNotMatch) {
        if (fixed.toLowerCase().equals("false")) {

          if (childtypeName.toLowerCase().equals("tscalar")) {

            val implName = typ.get.implementationName + ".Input"

            //  => data = com.ligadata.BaseTypes.StringImpl.toString(prevObjfield._2._2.asInstanceOf[String];
            mappedPrevTypNotrMatchkeys = mappedPrevTypNotrMatchkeys.append("\"" + f.Name + "\",")
            if (!prevVerMsgBaseTypesIdxArry.contains(childTypeImplName)) {
              prevVerMsgBaseTypesIdxArry += childTypeImplName
              prevObjTypNotMatchDeserializedBuf = prevObjTypNotMatchDeserializedBuf.append("%s case \"%s\" => data = %s.toString(value.asInstanceOf[%s]); %s".format(pad2, childTypeImplName, childTypeImplName, childtypePhysicalName, newline))
            }
          }
        }

      }

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        throw new Exception("Exception occured " + e.getCause())
      }
    }

    (prevObjDeserializedBuf.toString, convertOldObjtoNewObjBuf.toString, mappedPrevVerMatchkeys.toString, mappedPrevTypNotrMatchkeys.toString, prevObjTypNotMatchDeserializedBuf.toString, prevVerMsgBaseTypesIdxArry)
  }

  /**
   * From Func for mapped and Fixed Messages   *
   *
   */

  private def fromFunc(typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], fixed: String, f: Element, mappedMsgBaseTypeIdx: Int): String = {
    var fromFuncBuf = new StringBuilder(8 * 1024)
    try {
      if (typ.getOrElse("None").equals("None"))
        throw new Exception("Type not found in metadata for Name: " + f.Name + " , NameSpace: " + f.NameSpace + " , Type : " + f.Ttype)
      if (f.Name == null || f.Name.trim() == "")
        throw new Exception("Field name do not exists ")

      if (f.Name.toLowerCase().equals(transactionid)) {
        fromFuncBuf = fromFuncBuf.append("")
      } else {

        val implClone = typ.get.implementationName + ".Clone"
        if (implClone != null && implClone.trim() != "") {
          if (fixed.toLowerCase().equals("true")) {
            fromFuncBuf = fromFuncBuf.append("%s%s = %s(other.%s);%s".format(pad2, f.Name, implClone, f.Name, newline))
          } else if (fixed.toLowerCase().equals("false")) {
            if (mappedMsgBaseTypeIdx != -1)
              fromFuncBuf = fromFuncBuf.append("%s case %s => fields(key) = (%s, %s(ofield._2._2.asInstanceOf[%s]));  %s".format(pad1, mappedMsgBaseTypeIdx, mappedMsgBaseTypeIdx, implClone, typ.get.physicalName, newline))

          }
        }
      }
    } catch {
      case e: Exception => throw new Exception("Exception occured " + e.getCause())
    }

    fromFuncBuf.toString
  }

}