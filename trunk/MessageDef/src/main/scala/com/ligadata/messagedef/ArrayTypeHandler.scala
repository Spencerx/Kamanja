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
import scala.collection.mutable.ArrayBuffer
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.Exceptions.StackTrace
import org.apache.logging.log4j.{ Logger, LogManager }

class ArrayTypeHandler {

  private val pad1 = "\t"
  private val pad2 = "\t\t"
  private val pad3 = "\t\t\t"
  private val pad4 = "\t\t\t\t"
  private val newline = "\n"
  val logger = this.getClass.getName
  lazy val log = LogManager.getLogger(logger)
  var cnstObjVar = new ConstantMsgObjVarGenerator
  var methodGen = new ConstantMethodGenerator
  private val LOG = LogManager.getLogger(getClass)

  def handleArrayType(keysSet: Set[String], typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], f: Element, msg: Message, childs: Map[String, Any], prevVerMsgBaseTypesIdxArry: ArrayBuffer[String], recompile: Boolean): (List[(String, String)], List[(String, String, String, String, Boolean, String)], Set[String], Array[String]) = {
    var scalaclass = new StringBuilder(8 * 1024)
    var assignCsvdata = new StringBuilder(8 * 1024)
    var assignJsondata = new StringBuilder(8 * 1024)
    var assignKvdata = new StringBuilder(8 * 1024)
    var assignXmldata = new StringBuilder(8 * 1024)
    var addMsg = new StringBuilder(8 * 1024)
    var getMsg = new StringBuilder(8 * 1024)
    var list = List[(String, String)]()
    var argsList = List[(String, String, String, String, Boolean, String)]()
    var jarset: Set[String] = Set();
    val pad1 = "\t"
    val pad2 = "\t\t"
    val pad3 = "\t\t\t"
    val pad4 = "\t\t\t\t"
    val newline = "\n"
    var fname: String = ""
    var keysStr = new StringBuilder(8 * 1024)
    var arrayType: ArrayTypeDef = null
    var serializedBuf = new StringBuilder(8 * 1024)
    var deserializedBuf = new StringBuilder(8 * 1024)
    var prevObjDeserializedBuf = new StringBuilder(8 * 1024)
    var convertOldObjtoNewObjBuf = new StringBuilder(8 * 1024)
    var collections = new StringBuilder(8 * 1024)
    var mappedPrevVerMatchkeys = new StringBuilder(8 * 1024)
    var mappedMsgFieldsArry = new StringBuilder(8 * 1024)
    var msgAndCntnrsStr = new StringBuilder(8 * 1024)
    var mappedPrevTypNotrMatchkeys = new StringBuilder(8 * 1024)
    var fixedMsgGetKeyStrBuf = new StringBuilder(8 * 1024)
    var withMethod = new StringBuilder(8 * 1024)
    var fromFuncBuf = new StringBuilder(8 * 1024)
    var getNativeKeyValues = new StringBuilder(8 * 1024)
    var returnAB = new ArrayBuffer[String]

    try {
      arrayType = typ.get.asInstanceOf[ArrayTypeDef]

      if (arrayType == null) throw new Exception("Array type " + f.Ttype + " do not exist")

      if ((arrayType.elemDef.physicalName.equals("String")) || (arrayType.elemDef.physicalName.equals("Int")) || (arrayType.elemDef.physicalName.equals("Float")) || (arrayType.elemDef.physicalName.equals("Double")) || (arrayType.elemDef.physicalName.equals("Char")) || (arrayType.elemDef.physicalName.equals("Long")) || (arrayType.elemDef.physicalName.equals("Boolean"))) {
        if (arrayType.elemDef.implementationName.isEmpty())
          throw new Exception("Implementation Name not found in metadata for namespace %s" + f.Ttype)
        else
          fname = arrayType.elemDef.implementationName + ".Input"

        if (msg.Fixed != null) {
          if (msg.Fixed.toLowerCase().equals("true")) {
            assignCsvdata.append("%s%s = list(inputdata.curPos).split(arrvaldelim, -1).map(v => %s(v));\n%sinputdata.curPos = inputdata.curPos+1\n".format(pad2, f.Name, fname, pad2))
            scalaclass = scalaclass.append("%svar %s: %s = _ ;%s".format(pad1, f.Name, typ.get.typeString, newline))

          } else if (msg.Fixed.toLowerCase().equals("false")) {

            //scalaclass = scalaclass.append("%svar %s: %s = _ ;%s".format(pad1, f.Name, typ.get.typeString, newline))
            collections.append("\"" + f.Name + "\",")

            mappedMsgFieldsArry = mappedMsgFieldsArry.append("%s fields(\"%s\") = (-1, new %s(0));%s".format(pad1, f.Name, typ.get.typeString, newline))

            assignCsvdata.append(" ")

          }
          val (serStr, prevDeserStr, deserStr, converToNewObj, mappedPrevVerMatch, mappedPrevVerTypNotMatchKys) = getSerDeserPrimitives(typ.get.typeString, typ.get.FullName, f.Name, arrayType.elemDef.implementationName, childs, false, recompile, msg.Fixed.toLowerCase())
          // println("serStr   " + serStr)
          serializedBuf.append(serStr)
          deserializedBuf.append(deserStr)
          prevObjDeserializedBuf.append(prevDeserStr)
          convertOldObjtoNewObjBuf.append(converToNewObj)
          mappedPrevVerMatchkeys.append(mappedPrevVerMatch)
          mappedPrevTypNotrMatchkeys.append(mappedPrevVerTypNotMatchKys)
          fromFuncBuf.append(getPrimitivesFromFunc(f.Name, arrayType.elemDef.implementationName, msg.Fixed, false, typ.get.typeString))
        }
        assignJsondata.append(methodGen.assignJsonForArray(f.Name, fname, msg, typ.get.typeString))
        assignKvdata.append(methodGen.assignKvDataForArray(f.Name, fname, msg, typ.get.typeString))

      } else {
        if (arrayType.elemDef.tTypeType.toString().toLowerCase().equals("tcontainer")) {
          assignCsvdata.append(newline + methodGen.getArrayStr(f.Name, arrayType.elemDef.physicalName) + newline + "\t\tinputdata.curPos = inputdata.curPos+1" + newline)
          assignJsondata.append(methodGen.assignJsonForCntrArrayBuffer(f.Name, arrayType.elemDef.physicalName))
          keysStr.append("\"" + f.Name + "\",")

          if (msg.Fixed.toLowerCase().equals("false")) {
            assignJsondata.append("%s fields.put(\"%s\", (-1, %s)) ;%s".format(pad1, f.Name, f.Name, newline))
          }

          scalaclass = scalaclass.append("%svar %s: %s = %s();%s".format(pad1, f.Name, typ.get.typeString, typ.get.typeString, newline))

        } else if (arrayType.elemDef.tTypeType.toString().toLowerCase().equals("tmessage")) {
          throw new Exception("Adding Child Message/Messages are not allowed in Message/Container Definition")

          if (typ.get.typeString.toString().split("\\[").size == 2) {
            addMsg.append(pad2 + "if(curVal._2.compareToIgnoreCase(\"" + f.Name + "\") == 0) {" + newline + "\t")
            addMsg.append(pad3 + f.Name + " += msg.asInstanceOf[" + typ.get.typeString.toString().split("\\[")(1) + newline + pad3 + "} else ")
            if (msg.PrimaryKeys != null) {
              getMsg.append(pad2 + "if(curVal._2.compareToIgnoreCase(\"" + f.Name + "\") == 0) {" + newline + "\t")
              getMsg.append("%s%s.foreach(o => {%s%sif(o != null) { val pkd = o.PrimaryKeyData%s%sif(pkd.sameElements(primaryKey)) {%s%sreturn o%s%s}}%s%s)}%s%s}".format(pad3, f.Name, newline, pad3, newline, pad3, newline, pad3, newline, pad2, newline, pad2))
            }
          }
        }
        serializedBuf = serializedBuf.append(serializeMsgContainer(typ, msg.Fixed.toLowerCase(), f))
        deserializedBuf = deserializedBuf.append(deSerializeMsgContainer(typ, msg.Fixed.toLowerCase(), f, false))
        val (prevObjDeserialized, convertOldObjtoNewObj, mappedPrevVerMatch, mappedPrevTypNotMatchKys) = prevObjDeserializeMsgContainer(typ, msg.Fixed.toLowerCase(), f, childs, false)
        prevObjDeserializedBuf = prevObjDeserializedBuf.append(prevObjDeserialized)
        convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append(convertOldObjtoNewObj)
        msgAndCntnrsStr.append("\"" + f.Name + "\",")
        mappedPrevVerMatchkeys.append(mappedPrevVerMatch)
        mappedPrevTypNotrMatchkeys.append(mappedPrevTypNotMatchKys)
        fromFuncBuf.append(getFromFuncContainers(f.Name, msg.Fixed: String, false, typ.get.typeString))

        //assignCsvdata.append(newline + "//Array of " + arrayType.elemDef.physicalName + "not handled at this momemt" + newline)

      }

      if (msg.Fixed.toLowerCase().equals("true")) {

        withMethod = withMethod.append("%s %s def with%s(value: %s) : %s = {%s".format(newline, pad1, f.Name, typ.get.typeString, msg.Name, newline))
        withMethod = withMethod.append("%s this.%s = value %s".format(pad1, f.Name, newline))
        withMethod = withMethod.append("%s return this %s %s } %s".format(pad1, newline, pad1, newline))

        getNativeKeyValues = getNativeKeyValues.append("%s keyValues(\"%s\") = (\"%s\", %s); %s".format(pad1, f.Name, f.NativeName, f.Name, newline))

      } else if (msg.Fixed.toLowerCase().equals("false")) {
        withMethod = withMethod.append("%s%s def with%s(value: %s) : %s = {%s".format(newline, pad1, f.Name, typ.get.typeString, msg.Name, newline))
        withMethod = withMethod.append("%s fields(\"%s\") = (-1, value) %s".format(pad1, f.Name, newline))
        withMethod = withMethod.append("%s return this %s %s } %s".format(pad1, newline, pad1, newline))
       
      }

      fixedMsgGetKeyStrBuf.append("%s if(key.equals(\"%s\")) return %s; %s".format(pad1, f.Name, f.Name, newline))
      var nativeKeyMap : String = "(\"%s\", \"%s\"), ".format(f.Name, f.NativeName)

      argsList = (f.NameSpace, f.Name, typ.get.NameSpace, typ.get.Name, false, null) :: argsList
      log.debug("typ.get.typeString " + typ.get.typeString)

      if ((arrayType.dependencyJarNames != null) && (arrayType.JarName != null))
        jarset = jarset + arrayType.JarName ++ arrayType.dependencyJarNames
      else if (arrayType.JarName != null)
        jarset = jarset + arrayType.JarName
      else if (arrayType.dependencyJarNames != null)
        jarset = jarset ++ arrayType.dependencyJarNames

      returnAB += scalaclass.toString
      returnAB += assignCsvdata.toString
      returnAB += assignJsondata.toString
      returnAB += assignXmldata.toString
      returnAB += addMsg.toString
      returnAB += keysStr.toString
      returnAB += getMsg.toString
      returnAB += serializedBuf.toString
      returnAB += deserializedBuf.toString
      returnAB += prevObjDeserializedBuf.toString
      returnAB += convertOldObjtoNewObjBuf.toString
      returnAB += collections.toString
      returnAB += mappedPrevVerMatchkeys.toString
      returnAB += mappedMsgFieldsArry.toString
      returnAB += mappedPrevTypNotrMatchkeys.toString
      returnAB += fixedMsgGetKeyStrBuf.toString
      returnAB += withMethod.toString
      returnAB += fromFuncBuf.toString
      returnAB += assignKvdata.toString
      returnAB += nativeKeyMap.toString
      returnAB += getNativeKeyValues.toString

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        throw e
      }
    }

    // (scalaclass.toString, assignCsvdata.toString, assignJsondata.toString, assignXmldata.toString, list, argsList, addMsg.toString, jarset, keysStr.toString, getMsg.toString, serializedBuf.toString, deserializedBuf.toString, prevObjDeserializedBuf.toString, convertOldObjtoNewObjBuf.toString, collections.toString, mappedPrevVerMatchkeys.toString, mappedMsgFieldsArry.toString, mappedPrevTypNotrMatchkeys.toString, fixedMsgGetKeyStrBuf.toString, withMethod.toString, fromFuncOfFixed.toString)
    (list, argsList, jarset, returnAB.toArray)

  }

  def handleArrayBuffer(keysSet: Set[String], msg: Message, typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], f: Element, childs: Map[String, Any], prevVerMsgBaseTypesIdxArry: ArrayBuffer[String], recompile: Boolean): (List[(String, String)], List[(String, String, String, String, Boolean, String)], Set[String], Array[String]) = {
    var scalaclass = new StringBuilder(8 * 1024)
    var assignCsvdata = new StringBuilder(8 * 1024)
    var assignJsondata = new StringBuilder(8 * 1024)
    var assignXmldata = new StringBuilder(8 * 1024)
    var assignKvData = new StringBuilder(8 * 1024)
    var addMsg = new StringBuilder(8 * 1024)
    var getMsg = new StringBuilder(8 * 1024)
    var list = List[(String, String)]()
    var argsList = List[(String, String, String, String, Boolean, String)]()
    var jarset: Set[String] = Set();
    val pad1 = "\t"
    val pad2 = "\t\t"
    val pad3 = "\t\t\t"
    val pad4 = "\t\t\t\t"
    val newline = "\n"
    var fname: String = ""
    var arrayBufType: ArrayBufTypeDef = null
    var keysStr = new StringBuilder(8 * 1024)
    var msgAndCntnrsStr = new StringBuilder(8 * 1024)
    var msgNameSpace = ""
    var serializedBuf = new StringBuilder(8 * 1024)
    var deserializedBuf = new StringBuilder(8 * 1024)
    var prevObjDeserializedBuf = new StringBuilder(8 * 1024)
    var convertOldObjtoNewObjBuf = new StringBuilder(8 * 1024)
    var collections = new StringBuilder(8 * 1024)
    var mappedPrevVerMatchkeys = new StringBuilder(8 * 1024)
    var mappedMsgFieldsVar = new StringBuilder(8 * 1024)
    var mappedPrevTypNotrMatchkeys = new StringBuilder(8 * 1024)
    var mappedMsgFieldsArryBuffer = new StringBuilder(8 * 1024)
    var fixedMsgGetKeyStrBuf = new StringBuilder(8 * 1024)
    var withMethod = new StringBuilder(8 * 1024)
    var fromFuncBuf = new StringBuilder(8 * 1024)
    var getNativeKeyValues = new StringBuilder(8 * 1024)
    var returnAB = new ArrayBuffer[String]

    try {
      arrayBufType = typ.get.asInstanceOf[ArrayBufTypeDef]
      if (arrayBufType == null) throw new Exception("Array Byffer of " + f.Ttype + " do not exists throwing Null Pointer")

      if ((arrayBufType.elemDef.physicalName.equals("String")) || (arrayBufType.elemDef.physicalName.equals("Int")) || (arrayBufType.elemDef.physicalName.equals("Float")) || (arrayBufType.elemDef.physicalName.equals("Double")) || (arrayBufType.elemDef.physicalName.equals("Char")) || (arrayBufType.elemDef.physicalName.equals("Long")) || (arrayBufType.elemDef.physicalName.equals("Boolean"))) {
        if (arrayBufType.elemDef.implementationName.isEmpty())
          throw new Exception("Implementation Name not found in metadata for namespace %s" + f.Ttype)
        else
          fname = arrayBufType.elemDef.implementationName + ".Input"

        if (msg.Fixed != null) {
          if (msg.Fixed.toLowerCase().equals("true")) {
            //  list(inputdata.curPos).split(arrvaldelim, -1).map(v => { xyz :+= com.ligadata.BaseTypes.IntImpl.Input(v)});
            assignCsvdata.append("%slist(inputdata.curPos).split(arrvaldelim, -1).map(v => {%s :+= %s(v)});\n%sinputdata.curPos = inputdata.curPos+1\n".format(pad2, f.Name, fname, pad2))
            scalaclass = scalaclass.append("%svar %s: %s = new %s;%s".format(pad1, f.Name, typ.get.typeString, typ.get.typeString, newline))

          } else if (msg.Fixed.toLowerCase().equals("false")) {
            //scalaclass = scalaclass.append("%svar %s: %s = new %s;%s".format(pad1, f.Name, typ.get.typeString, typ.get.typeString, newline))

            assignCsvdata.append(" ")
            //Adding fields to keys Map
            // keysStr.append("(\"" + f.Name + "\"," + typ.get.implementationName + "),")

            collections.append("\"" + f.Name + "\",")
            mappedMsgFieldsArryBuffer = mappedMsgFieldsArryBuffer.append("%s fields(\"%s\") = (-1, new %s(0));%s".format(pad1, f.Name, typ.get.typeString, newline))
          }

          val (serStr, prevDeserStr, deserStr, converToNewObj, mappedPrevVerMatch, mappedPrevVerTypNotMatchKys) = getSerDeserPrimitives(typ.get.typeString, typ.get.FullName, f.Name, arrayBufType.elemDef.implementationName, childs, true, recompile, msg.Fixed.toLowerCase())
          serializedBuf.append(serStr)
          deserializedBuf.append(deserStr)
          prevObjDeserializedBuf.append(prevDeserStr)
          convertOldObjtoNewObjBuf.append(converToNewObj)
          mappedPrevVerMatchkeys.append(mappedPrevVerMatch)
          mappedPrevTypNotrMatchkeys.append(mappedPrevVerTypNotMatchKys)
          fromFuncBuf.append(getPrimitivesFromFunc(f.Name, arrayBufType.elemDef.implementationName, msg.Fixed, true, typ.get.typeString))

        }
        assignJsondata.append(methodGen.assignJsonForPrimArrayBuffer(f.Name, fname, msg, typ.get.typeString))
        assignKvData.append(methodGen.assignKvForPrimArrayBuffer(f.Name, fname, msg, typ.get.typeString))

      } else {
        if (msg.NameSpace != null)
          msgNameSpace = msg.NameSpace
        argsList = (msgNameSpace, f.Name, arrayBufType.NameSpace, arrayBufType.Name, false, null) :: argsList
        val msgtype = "scala.collection.mutable.ArrayBuffer[com.ligadata.KamanjaBase.BaseMsg]"
        if (msg.Fixed.toLowerCase().equals("true")) //--- --- commented to declare the arraybuffer of messages in memeber variables section for both fixed adn mapped messages
          scalaclass = scalaclass.append("%svar %s: %s = new %s;%s".format(pad1, f.Name, typ.get.typeString, typ.get.typeString, newline))

        if (f.ElemType.toLowerCase().equals("container")) {
          // scalaclass = scalaclass.append("%svar %s: %s = new %s;%s".format(pad1, f.Name, typ.get.typeString, typ.get.typeString, newline))
          assignCsvdata.append("%s//%s Implementation of Array Buffer of Container is not handled %s".format(pad2, f.Name, newline))

          if (typ.get.typeString.toString().split("\\[").size == 2) {
            assignJsondata.append(methodGen.assignJsonForCntrArrayBuffer(f.Name, typ.get.typeString.toString().split("\\[")(1).split("\\]")(0)))
          }
          if (msg.Fixed.toLowerCase().equals("false")) {
            scalaclass = scalaclass.append("%svar %s: %s = new %s;%s".format(pad1, f.Name, typ.get.typeString, typ.get.typeString, newline))
            assignJsondata.append("%s fields.put(\"%s\", (-1, %s)); %s".format(pad1, f.Name, f.Name, newline))

            //    keysStr.append("(\"" + f.Name + "\"," + typ.get.typeString + "),")
          }
          // assignJsondata.append("%s%s.assignJsonData(map)%s".format(pad1, f.Name, newline))
          // assignCsvdata.append(newline + getArrayStr(f.Name, arrayBufType.elemDef.physicalName) + newline + "\t\tinputdata.curPos = inputdata.curPos+1" + newline)
        } else if (f.ElemType.toLowerCase().equals("message")) {
          throw new Exception("Adding Child Message/Messages is not allowed in Message/Container Definition")

          //  assignCsvdata.append("%s//%s Implementation of messages is not handled at this time%s".format(pad2, f.Name, newline))
          //  assignJsondata.append("%s//%s Implementation of messages is not handled %s".format(pad2, f.Name, newline))
          if (typ.get.typeString.toString().split("\\[").size == 2) {
            if (msg.Fixed.toLowerCase().equals("true")) {

              addMsg.append(newline + pad2 + "if(curVal._2.compareToIgnoreCase(\"" + f.Name + "\") == 0) {" + newline + "\t")
              addMsg.append(pad3 + f.Name + " += msg.asInstanceOf[" + typ.get.typeString.toString().split("\\[")(1) + newline + pad3 + "} else ")

            } else if (msg.Fixed.toLowerCase().equals("false")) {

              addMsg.append(pad2 + "if(curVal._2.compareToIgnoreCase(\"" + f.Name + "\") == 0) {" + newline + "\t")
              addMsg.append(newline + pad3 + "val x = getOrElse(\"" + f.Name + "\", null)" + newline + pad3)
              addMsg.append("var " + f.Name + ": " + typ.get.typeString + " = null " + newline) //--- commented to declare the arraybuffer of messages in memeber variables section
              addMsg.append(pad3 + "if (x == null) {" + newline + pad3 + f.Name + " = new " + typ.get.typeString + newline + pad3 + "} else {" + newline)
              addMsg.append(pad3 + f.Name + "= x.asInstanceOf[" + typ.get.typeString + "]" + newline + pad3 + "}" + newline)
              addMsg.append(pad3 + f.Name + " += msg.asInstanceOf[" + typ.get.typeString.toString().split("\\[")(1) + newline)
              addMsg.append(pad3 + "fields(\"" + f.Name + "\") = (-1, " + f.Name + ")" + newline + pad2 + newline + pad2 + "} else ")

              mappedMsgFieldsVar.append("%s fields(\"%s\") = (-1, new %s )%s".format(pad2, f.Name, typ.get.typeString, newline))

              //adding Array of Messages to Map
              // keysStr.append("(\"" + f.Name + "\"," + arrayBuf + "[" + msgType + "),")
            }
            if (msg.PrimaryKeys != null) {
              getMsg.append(pad2 + "if(curVal._2.compareToIgnoreCase(\"" + f.Name + "\") == 0) {" + newline + "\t")

              if (msg.Fixed.toLowerCase().equals("false")) {
                getMsg.append(newline + pad3 + "val x = getOrElse(\"" + f.Name + "\", null)" + newline)
                getMsg.append(pad3 + "if (x == null) return null" + newline)
                getMsg.append(pad3 + "val " + f.Name + "= x.asInstanceOf[" + typ.get.typeString + "]" + newline + pad3)
                getMsg.append("%s%s.foreach(o => {%s%s if(o != null) { val pkd = o.PrimaryKeyData%s%sif(pkd.sameElements(primaryKey)) {%s%sreturn o%s%s} }%s%s})%s%s}else ".format(pad2, f.Name, newline, pad3, newline, pad3, newline, pad3, newline, pad2, newline, pad2, newline, pad2))

              } else if (msg.Fixed.toLowerCase().equals("true")) {
                getMsg.append("%s%s.foreach(o => {%s%s if(o != null) { val pkd = o.PrimaryKeyData%s%sif(pkd.sameElements(primaryKey)) {%s%sreturn o%s%s}}%s%s})%s%s}else ".format(pad2, f.Name, newline, pad3, newline, pad3, newline, pad3, newline, pad2, newline, pad2, newline, pad2))
              }
            }
          }

        }
        serializedBuf = serializedBuf.append(serializeMsgContainer(typ, msg.Fixed.toLowerCase(), f))
        deserializedBuf = deserializedBuf.append(deSerializeMsgContainer(typ, msg.Fixed.toLowerCase(), f, true))
        val (prevObjDeserialized, convertOldObjtoNewObj, mappedPrevVerMatch, mappedPrevTypNotMatchKys) = prevObjDeserializeMsgContainer(typ, msg.Fixed.toLowerCase(), f, childs, true)
        prevObjDeserializedBuf = prevObjDeserializedBuf.append(prevObjDeserialized)
        convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append(convertOldObjtoNewObj)
        msgAndCntnrsStr.append("\"" + f.Name + "\",")
        mappedPrevVerMatchkeys.append(mappedPrevVerMatch)
        mappedPrevTypNotrMatchkeys.append(mappedPrevTypNotMatchKys)
        fromFuncBuf.append(getFromFuncContainers(f.Name, msg.Fixed: String, true, typ.get.typeString))

      }
      fixedMsgGetKeyStrBuf.append("%s if(key.equals(\"%s\")) return %s; %s".format(pad1, f.Name, f.Name, newline))

      if (msg.Fixed.toLowerCase().equals("true")) {

        withMethod = withMethod.append("%s %s def with%s(value: %s) : %s = {%s".format(newline, pad1, f.Name, typ.get.typeString, msg.Name, newline))
        withMethod = withMethod.append("%s this.%s = value %s".format(pad1, f.Name, newline))
        withMethod = withMethod.append("%s return this %s %s } %s".format(pad1, newline, pad1, newline))

        getNativeKeyValues = getNativeKeyValues.append("%s keyValues(\"%s\") = (\"%s\", %s); %s".format(pad1, f.Name, f.NativeName, f.Name, newline))

      } else if (msg.Fixed.toLowerCase().equals("false")) {
        withMethod = withMethod.append("%s%s def with%s(value: %s) : %s = {%s".format(newline, pad1, f.Name, typ.get.typeString, msg.Name, newline))
        withMethod = withMethod.append("%s fields(\"%s\") = (-1, value) %s".format(pad1, f.Name, newline))
        withMethod = withMethod.append("%s return this %s %s } %s".format(pad1, newline, pad1, newline))

      }

      if ((arrayBufType.dependencyJarNames != null) && (arrayBufType.JarName != null))
        jarset = jarset + arrayBufType.JarName ++ arrayBufType.dependencyJarNames
      else if (arrayBufType.JarName != null)
        jarset = jarset + arrayBufType.JarName
      else if (arrayBufType.dependencyJarNames != null)
        jarset = jarset ++ arrayBufType.dependencyJarNames

      var nativeKeyMap: String = "(\"%s\", \"%s\"), ".format(f.Name, f.NativeName)

      returnAB += scalaclass.toString
      returnAB += assignCsvdata.toString
      returnAB += assignJsondata.toString
      returnAB += assignXmldata.toString
      returnAB += addMsg.toString
      returnAB += msgAndCntnrsStr.toString
      returnAB += keysStr.toString
      returnAB += getMsg.toString
      returnAB += serializedBuf.toString
      returnAB += deserializedBuf.toString
      returnAB += prevObjDeserializedBuf.toString
      returnAB += convertOldObjtoNewObjBuf.toString
      returnAB += collections.toString
      returnAB += mappedPrevVerMatchkeys.toString
      returnAB += mappedMsgFieldsVar.toString
      returnAB += mappedPrevTypNotrMatchkeys.toString
      returnAB += mappedMsgFieldsArryBuffer.toString
      returnAB += fixedMsgGetKeyStrBuf.toString
      returnAB += withMethod.toString
      returnAB += fromFuncBuf.toString
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
    //, withMethod.toString, fromFuncOfFixed.toString
    // (scalaclass.toString, assignCsvdata.toString, assignJsondata.toString, assignXmldata.toString, list, argsList, addMsg.toString, jarset, msgAndCntnrsStr.toString, keysStr.toString, getMsg.toString, serializedBuf.toString, deserializedBuf.toString, prevObjDeserializedBuf.toString, convertOldObjtoNewObjBuf.toString, collections.toString, mappedPrevVerMatchkeys.toString, mappedMsgFieldsVar.toString, mappedPrevTypNotrMatchkeys.toString, mappedMsgFieldsArryBuffer.toString, fixedMsgGetKeyStrBuf.toString, withMethod.toString)

    (list, argsList, jarset, returnAB.toArray)
  }

  def serializeMsgContainer(typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], fixed: String, f: Element): String = {
    serialize(typ, fixed, f)
  }

  def deSerializeMsgContainer(typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], fixed: String, f: Element, isArayBuf: Boolean): String = {
    deSerialize(typ, fixed, f, isArayBuf)
  }

  def prevObjDeserializeMsgContainer(typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], fixed: String, f: Element, childs: Map[String, Any], isArayBuf: Boolean): (String, String, String, String) = {
    prevObjDeserialize(typ, fixed, f, childs, isArayBuf)
  }

  //serialize String for array of messages or containers
  private def serialize(typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], fixed: String, f: Element): String = {
    var serializedBuf = new StringBuilder(8 * 1024)
    try {
      if (f.Name == null || f.Name.trim() == "")
        throw new Exception("Field name do not exists ")
      if (fixed.toLowerCase().equals("true")) {

        serializedBuf.append("%sif ((%s==null) ||(%s.size == 0)) com.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos,0);%s".format(pad2, f.Name, f.Name, newline))
        serializedBuf.append("%selse {%s%scom.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos,%s.size);%s".format(pad2, newline, pad2, f.Name, newline))
        serializedBuf.append("%s%s.foreach(obj => {%s".format(pad2, f.Name, newline))
        serializedBuf.append("%sval bytes = SerializeDeserialize.Serialize(obj)%s".format(pad2, newline))
        serializedBuf.append("%scom.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos,bytes.length)%s".format(pad2, newline))
        serializedBuf.append("%sdos.write(bytes)})}%s".format(pad2, newline))

      } else if (fixed.toLowerCase().equals("false")) {

        serializedBuf.append("%s{%s%sval v = getOrElse(\"%s\", null)%s".format(pad2, newline, pad3, f.Name, newline))
        serializedBuf.append("%s if (v == null) com.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos,0)%s".format(pad3, newline))
        serializedBuf.append("%s else {  val o = v.asInstanceOf[%s]%s".format(pad3, typ.get.typeString, newline))
        serializedBuf.append("%s com.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos,o.size)%s".format(pad3, newline))
        serializedBuf.append("%s o.foreach(obj => { if(obj != null){ val bytes = SerializeDeserialize.Serialize(obj)%s".format(pad3, newline))
        serializedBuf.append("%scom.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos,bytes.length)%s".format(pad3, newline))
        serializedBuf.append("%sdos.write(bytes)}})}}%s".format(pad3, newline))
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        throw new Exception("Exception occured " + e.getCause())
      }
    }

    return serializedBuf.toString()
  }

  //Deserialize String for array of messages or containers
  private def deSerialize(typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], fixed: String, f: Element, isArayBuf: Boolean): String = {
    var deserializedBuf = new StringBuilder(8 * 1024)

    try {
      if (typ.getOrElse("None").equals("None"))
        throw new Exception("Type not found in metadata for Name: " + f.Name + " , NameSpace: " + f.NameSpace + " , Type : " + f.Ttype)

      if (f.Name == null || f.Name.trim() == "")
        throw new Exception("Field name do not exists ")

      val typeStr = typ.get.typeString.toString()

      if (typeStr == null || typeStr.trim() == "")
        throw new Exception("Implementation Name do not exist in Type")

      val childType = typeStr.split("\\[")(1).substring(0, typeStr.split("\\[")(1).length() - 1)

      if (fixed.toLowerCase().equals("true")) {

        deserializedBuf.append("%s{%s%s%svar arraySize = com.ligadata.BaseTypes.IntImpl.DeserializeFromDataInputStream(dis);%s".format(pad1, newline, pad1, pad2, newline))
        if (!isArayBuf)
          deserializedBuf.append("%s %s %s = new %s(arraySize) %s".format(pad2, newline, f.Name, typ.get.typeString, newline))
        deserializedBuf.append("%sval i:Int = 0;%s".format(pad2, newline))
        deserializedBuf.append("%s for (i <- 0 until arraySize) {%s".format(pad2, newline))
        deserializedBuf.append("%svar bytes = new Array[Byte](com.ligadata.BaseTypes.IntImpl.DeserializeFromDataInputStream(dis))%s".format(pad2, newline))
        deserializedBuf.append("%sdis.read(bytes);%s".format(pad2, newline))
        deserializedBuf.append("%sval inst = SerializeDeserialize.Deserialize(bytes, mdResolver, loader, false, \"%s\");%s".format(pad2, childType, newline))
        if (isArayBuf)
          deserializedBuf.append("%s%s += inst.asInstanceOf[%s;%s%s}%s%s%s}%s".format(pad2, f.Name, typ.get.typeString.toString().split("\\[")(1), newline, pad2, newline, newline, pad2, newline))
        else
          deserializedBuf.append("%s%s(i) = inst.asInstanceOf[%s;%s%s}%s%s%s}%s".format(pad2, f.Name, typ.get.typeString.toString().split("\\[")(1), newline, pad2, newline, newline, pad2, newline))
      } else if (fixed.toLowerCase().equals("false")) {
        deserializedBuf.append("%s{ var arraySize = com.ligadata.BaseTypes.IntImpl.DeserializeFromDataInputStream(dis)%s".format(pad2, newline))

        if (isArayBuf)
          deserializedBuf.append("%s%s var %s = new %s %s".format(pad2, newline, f.Name, typ.get.typeString, newline))
        else
          deserializedBuf.append("%s %s var %s = new %s(arraySize) %s".format(pad2, newline, f.Name, typ.get.typeString, newline))
        // deserializedBuf.append("%sval %s = new %s %s".format(pad2, f.Name, typ.get.typeString.toString(), newline))
        deserializedBuf.append("%s val i:Int = 0;%s".format(pad2, newline))
        deserializedBuf.append("%s for (i <- 0 until arraySize) {%s".format(pad2, newline))
        deserializedBuf.append("%s  val byteslength = com.ligadata.BaseTypes.IntImpl.DeserializeFromDataInputStream(dis) %s".format(pad2, newline))
        deserializedBuf.append("%s var bytes = new Array[Byte](byteslength)%s".format(pad2, newline))
        deserializedBuf.append("%s dis.read(bytes);%s".format(pad2, newline))
        deserializedBuf.append("%s val inst = SerializeDeserialize.Deserialize(bytes, mdResolver, loader, false, \"%s\");%s".format(pad2, childType, newline))
        if (isArayBuf)
          deserializedBuf.append("%s%s += inst.asInstanceOf[%s%s}%s".format(pad2, f.Name, typ.get.typeString.toString().split("\\[")(1), newline, pad2, newline))
        else
          deserializedBuf.append("%s%s(i) = inst.asInstanceOf[%s%s}%s".format(pad2, f.Name, typ.get.typeString.toString().split("\\[")(1), newline, pad2, newline))
        deserializedBuf.append("%s%s fields(\"%s\") = (-1, %s)}%s".format(newline, pad2, f.Name, f.Name, newline))
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        throw new Exception("Exception occured " + e.getCause())
      }
    }
    return deserializedBuf.toString
  }

  //Previous object Deserialize String and Convert Old object to new Object for array of messages or containers
  private def prevObjDeserialize(typ: Option[com.ligadata.kamanja.metadata.BaseTypeDef], fixed: String, f: Element, childs: Map[String, Any], isArayBuf: Boolean): (String, String, String, String) = {
    var prevObjDeserializedBuf = new StringBuilder(8 * 1024)
    var convertOldObjtoNewObjBuf = new StringBuilder(8 * 1024)
    var mappedPrevVerMatchkeys = new StringBuilder(8 * 1024)
    var mappedPrevTypNotrMatchkeys = new StringBuilder(8 * 1024)
    try {
      if (typ.getOrElse("None").equals("None"))
        throw new Exception("Type not found in metadata for Name: " + f.Name + " , NameSpace: " + f.NameSpace + " , Type : " + f.Ttype)

      if (f.Name == null || f.Name.trim() == "")
        throw new Exception("Field name do not exists ")

      val curObjtype = typ.get.typeString.toString()
      var curObjtypeStr: String = ""
      if (curObjtype != null && curObjtype.trim() != "") {
        curObjtypeStr = curObjtype.split("\\[")(1).substring(0, curObjtype.split("\\[")(1).length() - 1)
      }
      var memberExists: Boolean = false
      var membrMatchTypeNotMatch = false // for mapped messages to handle if prev ver obj and current version obj member types do not match...
      var childTypeImplName: String = ""

      var childName: String = ""
      var sameType: Boolean = false
      if (childs != null) {
        if (childs.contains(f.Name)) {
          var child = childs.getOrElse(f.Name, null)
          if (child != null) {
            val fullname = child.asInstanceOf[AttributeDef].aType.FullName
            if (fullname != null && fullname.trim() != "" && fullname.equals(typ.get.FullName)) {
              memberExists = true
              val childPhysicalName = child.asInstanceOf[AttributeDef].aType.typeString
              if (childPhysicalName != null && childPhysicalName.trim() != "") {
                childName = childPhysicalName.toString().split("\\[")(1).substring(0, childPhysicalName.toString().split("\\[")(1).length() - 1)
                if (childName.equals(curObjtypeStr))
                  sameType = true
              }
            } else {
              membrMatchTypeNotMatch = true
              childTypeImplName = child.asInstanceOf[AttributeDef].aType.implementationName
            }
          }
        }
      }
      if (fixed.toLowerCase().equals("true")) {
        if (isArayBuf) {
          if (memberExists) {
            prevObjDeserializedBuf = prevObjDeserializedBuf.append("%sprevVerObj.%s.foreach(child => {%s".format(pad2, f.Name, newline))
            if (sameType)
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s%s += child});%s".format(pad2, f.Name, newline))
            else {
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%sval curVerObj = new %s()%s".format(pad2, curObjtypeStr, newline))
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%scurVerObj.ConvertPrevToNewVerObj(child)%s".format(pad2, newline))
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s%s += curVerObj});%s".format(pad2, f.Name, newline))
            }
          }
        } else {
          if (memberExists) {
            prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s for(i <- 0 until prevVerObj.%s.length) { %s".format(pad2, f.Name, newline))
            if (sameType)
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s%s(i) = prevVerObj.%s(i)};%s".format(pad2, f.Name, f.Name, newline))
            else {
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%sval curVerObj = new %s()%s".format(pad2, curObjtypeStr, newline))
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%scurVerObj.ConvertPrevToNewVerObj(child)%s".format(pad2, newline))
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s%s(i)= curVerObj}%s".format(pad2, f.Name, newline))
            }
          }

        }
      } else if (fixed.toLowerCase().equals("false")) {
        if (isArayBuf) {
          if (memberExists) {
            mappedPrevVerMatchkeys.append("\"" + f.Name + "\",")
            // prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s%s{var obj =  prevVerObj.getOrElse(\"%s\", null)%s".format(newline, pad2, f.Name, newline))
            prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s case \"%s\" => { %s".format(pad2, f.Name, newline))

            if (sameType)
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%sif(prevObjfield._2._2 != null){ fields(\"%s\") = (-1, prevObjfield._2._2)}}%s".format(pad2, f.Name, newline))
            else {
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s type typ = scala.collection.mutable.ArrayBuffer[%s]%s".format(pad2, childName, newline))
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s var %s : %s = new %s;%s".format(pad2, f.Name, curObjtype, curObjtype, newline))

              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s if(prevObjfield._2._2 != null  && prevObjfield._2._2.isInstanceOf[typ]){%s%s prevObjfield._2._2.asInstanceOf[typ].foreach(child => {%s".format(pad2, newline, pad2, newline))
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s val curVerObj = new %s()%s".format(pad2, curObjtypeStr, newline))
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s curVerObj.ConvertPrevToNewVerObj(child)%s".format(pad2, newline))
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s %s += curVerObj})}%s".format(pad2, f.Name, newline))
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s fields(\"%s\") = (-1, %s)}%s".format(pad2, f.Name, f.Name, newline))

            }
          } else if (membrMatchTypeNotMatch) {
            mappedPrevTypNotrMatchkeys = mappedPrevTypNotrMatchkeys.append("\"" + f.Name + "\",")
          }
        } else {
          if (memberExists) {
            mappedPrevVerMatchkeys.append("\"" + f.Name + "\",")
            // prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s%s{var obj =  prevVerObj.getOrElse(\"%s\", null)%s".format(newline, pad2, f.Name, newline))
            prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s case \"%s\" => { %s".format(pad2, f.Name, newline))

            if (sameType)
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%sif(prevObjfield._2._2 != null){ fields(\"%s\") = (-1, prevObjfield._2._2)}}%s".format(pad2, f.Name, newline))
            else {
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s type typ = scala.Array[%s]%s".format(pad2, childName, newline))
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s var %s : %s = %s();%s".format(pad2, f.Name, curObjtype, curObjtype, newline))

              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s if(prevObjfield._2._2 != null  && prevObjfield._2._2.isInstanceOf[typ]){%s%s for (i <- 0 until prevObjfield._2._2.length) {%s".format(pad2, newline, pad2, newline))
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s val curVerObj = new %s()%s".format(pad2, curObjtypeStr, newline))
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s curVerObj.ConvertPrevToNewVerObj(child)%s".format(pad2, newline))
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s %s(i) = curVerObj}}%s".format(pad2, f.Name, newline))
              prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s fields(\"%s\") = (-1, %s)}%s".format(pad2, f.Name, f.Name, newline))

            }

          } else if (membrMatchTypeNotMatch) {
            mappedPrevTypNotrMatchkeys = mappedPrevTypNotrMatchkeys.append("\"" + f.Name + "\",")
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

    (prevObjDeserializedBuf.toString, convertOldObjtoNewObjBuf.toString, mappedPrevVerMatchkeys.toString, mappedPrevTypNotrMatchkeys.toString)
  }

  //Serialize, deserailize array of primitives  
  def getSerDeserPrimitives(typeString: String, typFullName: String, fieldName: String, implName: String, childs: Map[String, Any], isArayBuf: Boolean, recompile: Boolean, fixed: String): (String, String, String, String, String, String) = {
    var serializedBuf = new StringBuilder(8 * 1024)
    var deserializedBuf = new StringBuilder(8 * 1024)
    var prevObjDeserializedBuf = new StringBuilder(8 * 1024)
    var convertOldObjtoNewObjBuf = new StringBuilder(8 * 1024)
    var mappedPrevVerMatchkeys = new StringBuilder(8 * 1024)
    var mappedPrevVerTypNotMatchkeys = new StringBuilder(8 * 1024)
    val pad1 = "\t"
    val pad2 = "\t\t"
    val pad3 = "\t\t\t"
    val pad4 = "\t\t\t\t"
    val newline = "\n"

    if (implName == null || implName.trim() == "")
      throw new Exception("Check Type Implementation Name")

    val serType = implName + ".SerializeIntoDataOutputStream"
    val deserType = implName + ".DeserializeFromDataInputStream"
    val dis = "dis"

    if (fixed.toLowerCase().equals("true")) {
      serializedBuf.append("%sif (%s==null || %s.size == 0) com.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos,0);%s".format(pad2, fieldName, fieldName, newline))
      serializedBuf.append("%selse {%s%scom.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos,%s.size);%s".format(pad2, newline, pad2, fieldName, newline))
      serializedBuf.append("%s%s.foreach(v => {%s".format(pad2, fieldName, newline))
      serializedBuf.append("%s%s(%s, v);%s".format(pad1, serType, "dos", newline))
      serializedBuf.append("%s})}%s".format(pad2, newline))

      deserializedBuf.append("%s{%s%s%svar arraySize = com.ligadata.BaseTypes.IntImpl.DeserializeFromDataInputStream(dis);%s".format(pad1, newline, pad1, pad2, newline))
      deserializedBuf.append("%sval i:Int = 0;%s".format(pad2, newline))
      deserializedBuf.append("%s if(arraySize > 0){ %s = new %s(arraySize)%s%s for (i <- 0 until arraySize) {%s".format(pad2, fieldName, typeString, newline, pad2, newline))
      deserializedBuf.append("%sval inst = %s(%s);%s".format(pad1, deserType, dis, newline))
      if (isArayBuf)
        deserializedBuf.append("%s%s :+= inst;%s%s}%s%s%s} }%s".format(pad2, fieldName, newline, pad2, newline, newline, pad2, newline))
      else {
        // deserializedBuf.append("%sif(inst != null)%s".format(pad2, newline))
        deserializedBuf.append("%s%s(i) = inst;%s%s}%s%s%s}}%s".format(pad2, fieldName, newline, pad2, newline, newline, pad2, newline))
      }

    } else if (fixed.toLowerCase().equals("false")) {

      serializedBuf.append("%s{val arr = getOrElse(\"%s\", null)%s".format(pad2, fieldName, newline))
      serializedBuf.append("%sif (arr == null ) com.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos, 0)%s".format(pad2, newline))
      serializedBuf.append("%s else { %s val a = arr.asInstanceOf[%s]%s".format(pad2, newline, typeString, newline))
      serializedBuf.append("%sif( a.size == 0) com.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos, 0);%s".format(pad2, newline))
      serializedBuf.append("%selse{ com.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos, a.size)%s".format(pad2, newline))
      serializedBuf.append("%s a.foreach(v => {%s %s(dos, %s.Input(v.toString))})}}}%s".format(pad2, newline, serType, implName, newline))

      deserializedBuf.append("%s{val arraySize = com.ligadata.BaseTypes.IntImpl.DeserializeFromDataInputStream(dis)%s".format(pad2, newline))
      deserializedBuf.append("%sval i:Int = 0%s".format(pad2, newline))
      deserializedBuf.append("%sif(arraySize > 0){ var arrValue = new %s(arraySize)%s".format(pad2, typeString, newline))
      deserializedBuf.append("%sfor (i <- 0 until arraySize) { val inst = %s(dis)%s".format(pad2, deserType, newline))
      if (isArayBuf)
        deserializedBuf.append("%s arrValue :+= inst }%s".format(pad2, newline))
      else
        deserializedBuf.append("%sarrValue(i) = inst }%s".format(pad2, newline))
      deserializedBuf.append("%s fields(\"%s\") = (-1, arrValue)}}%s".format(pad2, fieldName, newline))

    }
    var membrMatchTypeNotMatch = false // for mapped messages to handle if prev ver obj and current version obj member types do not match...
    var childTypeImplName: String = ""
    var memberExists: Boolean = false
    var sameType: Boolean = false
    if (childs != null) {
      if (childs.contains(fieldName)) {
        var child = childs.getOrElse(fieldName, null)
        if (child != null) {
          val fullname = child.asInstanceOf[AttributeDef].aType.FullName

          if (fullname != null && fullname.trim() != "" && fullname.equals(typFullName)) {
            memberExists = true
          } else {
            membrMatchTypeNotMatch = true
            childTypeImplName = child.asInstanceOf[AttributeDef].aType.implementationName
          }
        }
      }
    }
    //prevVerObj.icd9_dgns_cds.foreach(v => icd9_dgns_cds :+= v);)
    // prevVerObj.inpaticonvertOldObjtoNewObjBufent_claims.foreach(ip => inpatient_claims += ip)
    //typ.get.typeString.toString().split("\\[")(1)
    if (memberExists) {

      if (fixed.toLowerCase().equals("true")) {
        //prevObjDeserializedBuf = prevObjDeserializedBuf.append("%sprevVerObj.%s.foreach(v => %s :+= v);%s".format(pad1, fieldName, fieldName, newline))
        prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s%s = prevVerObj.%s;%s".format(pad1, fieldName, fieldName, newline))
        convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append("%s%s = oldObj.%s%s".format(pad2, fieldName, fieldName, newline))
      } else if (fixed.toLowerCase().equals("false")) {

        mappedPrevVerMatchkeys.append("\"" + fieldName + "\",")
        //prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s{ val (prevFldIdx, prevFldVal) = prevVerObj.fields(\"%s\")%s".format(pad1, fieldName, newline))
        //prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s  fields(\"%s\") = (prevFldIdx, prevFldVal)%s } %s".format(pad1, fieldName, newline, newline))

        // prevObjDeserializedBuf = prevObjDeserializedBuf.append("%sfields(\"%s\") = (1, prevVerObj.getOrElse(\"%s\", null))%s".format(pad1, fieldName, fieldName, newline))
        //convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append("%s fields(\"%s\",) = (-1, oldObj.getOrElse(\"%s\", null))%s".format(pad2, fieldName, fieldName, newline))

      }
    }
    // particular for mapped messages...
    if (membrMatchTypeNotMatch) {
      if (fixed.toLowerCase().equals("false")) {
        mappedPrevVerTypNotMatchkeys.append("\"" + fieldName + "\",")
        // prevObjDeserializedBuf = prevObjDeserializedBuf.append("%sfields(\"%s\") = (1, prevVerObj.getOrElse(\"%s\", null))%s".format(pad1, fieldName, fieldName, newline))
        //convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append("%s fields(\"%s\",) = (-1, oldObj.getOrElse(\"%s\", null))%s".format(pad2, fieldName, fieldName, newline))

      }
    }

    (serializedBuf.toString, prevObjDeserializedBuf.toString, deserializedBuf.toString, convertOldObjtoNewObjBuf.toString, mappedPrevVerMatchkeys.toString, mappedPrevVerTypNotMatchkeys.toString)
  }

  /**
   * FromFunc for Primitive Arrays for Fixed and Mapped Messages
   *
   */

  private def getPrimitivesFromFunc(fldName: String, implementationName: String, fixed: String, isArayBuf: Boolean, typeString: String): String = {
    var fromFuncBuf = new StringBuilder(8 * 1024)
    try {
      if (fldName == null || fldName.trim() == "")
        throw new Exception("Field name do not exists ")

      if (implementationName != null && implementationName.trim() != "") {
        if (fixed.toLowerCase().equals("true")) {
          fromFuncBuf = fromFuncBuf.append("%s if (other.%s != null ) { %s".format(pad2, fldName, newline))
          if (isArayBuf) {
            fromFuncBuf = fromFuncBuf.append("%s %s.clear;  %s".format(pad2, fldName, newline))
            fromFuncBuf = fromFuncBuf.append("%s other.%s.map(v =>{ %s :+= %s.Clone(v)}); %s".format(pad2, fldName, fldName, implementationName, newline))
          } else {
            fromFuncBuf = fromFuncBuf.append("%s %s = new %s(other.%s.length); %s".format(pad2, fldName, typeString, fldName, newline)) //typ.get.typeString
            fromFuncBuf = fromFuncBuf.append("%s %s = other.%s.map(v => %s.Clone(v)); %s".format(pad2, fldName, fldName, implementationName, newline)) //arrayType.elemDef.implementationName

          }
          fromFuncBuf = fromFuncBuf.append("%s } %s".format(pad2, newline))
          fromFuncBuf = fromFuncBuf.append("%s else %s = null; %s".format(pad2, fldName, newline))

        } else if (fixed.toLowerCase().equals("false")) {
          if (isArayBuf) {
            fromFuncBuf = fromFuncBuf.append("%s if (other.fields.contains(\"%s\")) { %s".format(pad2, fldName, newline))
            fromFuncBuf = fromFuncBuf.append("%s val o = (other.fields(\"%s\")._2.asInstanceOf[%s])  %s".format(pad2, fldName, typeString, newline))
            fromFuncBuf = fromFuncBuf.append("%s var %s = new %s(o.size); %s".format(pad2, fldName, typeString, newline))
            fromFuncBuf = fromFuncBuf.append("%s for (i <- 0 until o.length) { %s".format(pad2, newline))
            fromFuncBuf = fromFuncBuf.append("%s %s :+= %s.Clone(o(i)) %s".format(pad2, fldName, implementationName, newline))
            fromFuncBuf = fromFuncBuf.append("%s } %s".format(pad2, newline))
            fromFuncBuf = fromFuncBuf.append("%s fields(\"%s\") = (-1, %s); %s".format(pad2, fldName, fldName, newline))
            fromFuncBuf = fromFuncBuf.append("%s } %s".format(pad2, newline))

          } else {

            fromFuncBuf = fromFuncBuf.append("%s  if (other.fields.contains(\"%s\")) { %s".format(pad2, fldName, newline))
            fromFuncBuf = fromFuncBuf.append("%s  val o = (other.fields(\"%s\")._2.asInstanceOf[%s]) %s".format(pad2, fldName, typeString, newline))
            fromFuncBuf = fromFuncBuf.append("%s var %s = new %s(o.size) %s".format(pad2, fldName, typeString, newline))
            fromFuncBuf = fromFuncBuf.append("%s for (i <- 0 until o.length) { %s".format(pad2, newline))
            fromFuncBuf = fromFuncBuf.append("%s %s(i) = %s.Clone(o(i)) %s".format(pad2, fldName, implementationName, newline))
            fromFuncBuf = fromFuncBuf.append("%s } %s".format(pad2, newline))
            fromFuncBuf = fromFuncBuf.append("%s  fields(\"%s\") = (-1, %s); %s".format(pad2, fldName, fldName, newline))
            fromFuncBuf = fromFuncBuf.append("%s } %s".format(pad2, newline))
          }
        }
      }

    } catch {
      case e: Exception => throw new Exception("Exception occured " + e.getCause())
    }

    fromFuncBuf.toString

  }

  private def getFromFuncContainers(fldName: String, fixed: String, isArayBuf: Boolean, typeString: String): String = {
    var fromFuncBuf = new StringBuilder(8 * 1024)
    try {
      if (fldName == null || fldName.trim() == "")
        throw new Exception("Field name do not exists ")

      var typeStr: String = ""
      if (typeString.toString().split("\\[").size == 2) {
        typeStr = typeString.toString().split("\\[")(1)
      }

      if (fixed.toLowerCase().equals("true")) {
        if (isArayBuf) {
          fromFuncBuf = fromFuncBuf.append("%s if (other.%s != null ) { %s".format(pad2, fldName, newline))
          fromFuncBuf = fromFuncBuf.append("%s %s.clear;  %s".format(pad2, fldName, newline))
          fromFuncBuf = fromFuncBuf.append("%s other.%s.map(v =>{ %s :+= v.Clone.asInstanceOf[%s}); %s".format(pad2, fldName, fldName, typeStr, newline))
          fromFuncBuf = fromFuncBuf.append("%s } %s".format(pad2, newline))
          fromFuncBuf = fromFuncBuf.append("%s else %s = null; %s".format(pad2, fldName, newline))

        } else {
          fromFuncBuf = fromFuncBuf.append("%s if (other.%s != null) { %s".format(pad2, fldName, newline))
          fromFuncBuf = fromFuncBuf.append("%s %s = new %s(other.%s.length) %s".format(pad2, fldName, typeString, fldName, newline))
          fromFuncBuf = fromFuncBuf.append("%s %s = other.%s.map(f => f.Clone.asInstanceOf[%s ); %s".format(pad2, fldName, fldName, typeStr, newline))
          fromFuncBuf = fromFuncBuf.append("%s } %s".format(pad2, newline))
          fromFuncBuf = fromFuncBuf.append("%s else %s = null; %s".format(pad2, fldName, newline))

        }
      } else if (fixed.toLowerCase().equals("false")) {
        if (isArayBuf) {
          fromFuncBuf = fromFuncBuf.append("%s if (other.fields.contains(\"%s\")) { %s".format(pad2, fldName, newline))
          fromFuncBuf = fromFuncBuf.append("%s  %s.clear; %s".format(pad2, fldName, newline))
          fromFuncBuf = fromFuncBuf.append("%s val o = (other.fields(\"%s\")._2.asInstanceOf[%s]) ; %s".format(pad2, fldName, typeString, newline))
          fromFuncBuf = fromFuncBuf.append("%s fields(\"%s\") = (-1, %s) %s".format(pad2, fldName, fldName, newline))
          fromFuncBuf = fromFuncBuf.append("%s  } %s".format(pad2, newline))
        } else {

          fromFuncBuf = fromFuncBuf.append("%s if (other.fields.contains(\"%s\")) {  %s".format(pad2, fldName, newline))
          fromFuncBuf = fromFuncBuf.append("%s  val o = (other.fields(\"%s\")._2.asInstanceOf[%s]) ; %s".format(pad2, fldName, typeString, newline))
          fromFuncBuf = fromFuncBuf.append("%s %s = new %s(o.size) ;%s".format(pad2, fldName, typeString, newline))
          fromFuncBuf = fromFuncBuf.append("%s for(i <- 0 until o.length){ %s".format(pad2, newline))
          fromFuncBuf = fromFuncBuf.append("%s  %s(i) = o(i).Clone.asInstanceOf[%s  ;%s".format(pad2, fldName, typeStr, newline))
          fromFuncBuf = fromFuncBuf.append("%s  } %s".format(pad2, newline))
          fromFuncBuf = fromFuncBuf.append("%s fields(\"%s\") = (-1, %s) ;%s".format(pad2, fldName, fldName, newline))
          fromFuncBuf = fromFuncBuf.append("%s  } %s".format(pad2, newline))

        }
      }

    } catch {
      case e: Exception => throw new Exception("Exception occured " + e.getCause())
    }

    fromFuncBuf.toString

  }

}