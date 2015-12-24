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

import com.ligadata.kamanja.metadata.MdMgr
import com.ligadata.kamanja.metadata.MessageDef
import com.ligadata.kamanja.metadata.ContainerDef
import com.ligadata.kamanja.metadata.StructTypeDef
import com.ligadata.kamanja.metadata.AttributeDef
import com.ligadata.kamanja.metadata.MappedMsgTypeDef
import scala.collection.mutable.ArrayBuffer
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.Exceptions.StackTrace

import org.apache.logging.log4j.{ Logger, LogManager }

class ContainerTypeHandler {

  var methodGen = new ConstantMethodGenerator
    val logger = this.getClass.getName
    lazy val log = LogManager.getLogger(logger)
  def handleContainer(msg: Message, mdMgr: MdMgr, ftypeVersion: Long, f: Element, recompile: Boolean, childs: Map[String, Any]): (List[(String, String)], List[(String, String, String, String, Boolean, String)], Set[String], Array[String]) = {
    var scalaclass = new StringBuilder(8 * 1024)
    var assignCsvdata = new StringBuilder(8 * 1024)
    var assignJsondata = new StringBuilder(8 * 1024)
    var assignXmldata = new StringBuilder(8 * 1024)
    var addMsg = new StringBuilder(8 * 1024)
    var list = List[(String, String)]()
    var argsList = List[(String, String, String, String, Boolean, String)]()
    var keysStr = new StringBuilder(8 * 1024)
    var jarset: Set[String] = Set();
    val pad1 = "\t"
    val pad2 = "\t\t"
    val pad3 = "\t\t\t"
    val pad4 = "\t\t\t\t"
    val newline = "\n"
    var fname: String = ""
    var serializedBuf = new StringBuilder(8 * 1024)
    var deserializedBuf = new StringBuilder(8 * 1024)
    var prevObjDeserializedBuf = new StringBuilder(8 * 1024)
    var convertOldObjtoNewObjBuf = new StringBuilder(8 * 1024)
    var mappedMsgFieldsVar = new StringBuilder(8 * 1024)
    var mappedPrevVerMatchkeys = new StringBuilder(8 * 1024)
    var mappedPrevTypNotrMatchkeys = new StringBuilder(8 * 1024)
    var fixedMsgGetKeyStrBuf = new StringBuilder(8 * 1024)
    var withMethod = new StringBuilder(8 * 1024)
    var fromFuncBuf = new StringBuilder(8 * 1024)
    var getNativeKeyValues = new StringBuilder(8 * 1024)
    var returnAB = new ArrayBuffer[String]

    try {
      var ctrDef: ContainerDef = mdMgr.Container(f.Ttype, ftypeVersion, true).getOrElse(null)
      if (ctrDef == null) throw new Exception("Container  " + f.Ttype + " do not exists throwing null pointer")

      scalaclass = scalaclass.append("%svar %s:%s = new %s();%s".format(pad1, f.Name, ctrDef.PhysicalName, ctrDef.PhysicalName, newline))
      assignCsvdata.append("%s%s.populate(inputdata);\n%sinputdata.curPos = inputdata.curPos+1\n".format(pad2, f.Name, pad2))
      assignJsondata.append(methodGen.assignJsonDataMessage(f.Name))
      if (msg.Fixed.toLowerCase().equals("false")) {
        assignJsondata.append("%s fields.put(\"%s\", (-1, %s)) %s%s};%s".format(pad1, f.Name, f.Name, newline, pad1, newline))
      } else if (msg.Fixed.toLowerCase().equals("true")) {
        assignJsondata.append("%s%s};%s".format(newline, pad1, newline))
      }

      assignXmldata.append("%s%s.populate(xmlData)%s".format(pad3, f.Name, newline))

      if ((ctrDef.dependencyJarNames != null) && (ctrDef.jarName != null)) {
        jarset = jarset + ctrDef.JarName ++ ctrDef.dependencyJarNames
      } else if ((ctrDef.jarName != null))
        jarset = jarset + ctrDef.JarName
      else if (ctrDef.dependencyJarNames != null)
        jarset = jarset ++ ctrDef.dependencyJarNames
      // val typ = MdMgr.GetMdMgr.Type(f.Ttype, ftypeVersion, true)
      argsList = (f.NameSpace, f.Name, ctrDef.NameSpace, ctrDef.Name, false, null) :: argsList
      //   keysStr.append("\"" + f.Name + "\",")
      // argsList = (f.NameSpace, f.Name, typ.get.NameSpace, typ.get.Name, false, null) :: argsList

      fixedMsgGetKeyStrBuf.append("%s if(key.equals(\"%s\")) return %s; %s".format(pad1, f.Name, f.Name, newline))

      if (msg.Fixed.toLowerCase().equals("true")) {
        serializedBuf.append("%s {if (%s == null)  com.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos,0);%s".format(pad2, f.Name, newline))

      } else if (msg.Fixed.toLowerCase().equals("false")) {
        serializedBuf.append("%s { val %s = getOrElse(\"%s\", null);%s".format(pad2, f.Name, f.Name, newline))
        serializedBuf.append("%s if (%s == null) com.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos, 0) %s".format(pad2, f.Name, newline))
      }
      serializedBuf.append("%s else { val bytes = SerializeDeserialize.Serialize(%s.asInstanceOf[%s])%s".format(pad2, f.Name, ctrDef.typeString, newline))
      serializedBuf.append("%s com.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos,bytes.length);%s".format(pad2, newline))
      serializedBuf.append("%s dos.write(bytes);%s} %s }%s".format(pad2, newline, newline, newline))

      val curObjtype = ctrDef.physicalName

      var memberExists: Boolean = false
      var membrMatchTypeNotMatch = false
      var sameType: Boolean = false
      if (childs != null) {
        if (childs.contains(f.Name)) {
          var child = childs.getOrElse(f.Name, null)
          if (child != null) {
            val fullname = child.asInstanceOf[AttributeDef].aType.FullName
            if (fullname != null && fullname.trim() != "" && fullname.equals(ctrDef.FullName)) {
              memberExists = true
              val childPhysicalName = child.asInstanceOf[AttributeDef].aType.typeString
              if (childPhysicalName != null && childPhysicalName.trim() != "") {
                if (childPhysicalName.equals(curObjtype))
                  sameType = true
              }
            } else {
              membrMatchTypeNotMatch = true
            }
          }
        }
      }

      /*  case "inpatient" => {
                  val curVerObj = new com.ligadata.messagescontainers.System_InpatientClaim_100_1427140791051()
                  curVerObj.ConvertPrevToNewVerObj(prevObjfield._2._2)
                  fields("inpatient") = (-1, curVerObj)
                }
                * 
                */

      if (memberExists) {
        // convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append("%s%s = oldObj.%s%s".format(pad2, f.Name, f.Name, newline))
        if (msg.Fixed.toLowerCase().equals("true")) {
          prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s{%s%sval curVerObj = new %s()%s".format(pad2, newline, pad2, ctrDef.typeString, newline))
          prevObjDeserializedBuf = prevObjDeserializedBuf.append("%scurVerObj.ConvertPrevToNewVerObj(prevVerObj.%s)%s".format(pad2, f.Name, newline))
          prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s%s = curVerObj}%s".format(pad2, f.Name, newline))
        }
        if (msg.Fixed.toLowerCase().equals("false")) {
          mappedPrevVerMatchkeys.append("\"" + f.Name + "\",")
          prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s case \"%s\" => { %s".format(pad2, f.Name, newline))
          prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s if(prevObjfield._2._2 != null){ fields(\"%s\") = (prevObjfield._2._1,prevObjfield._2._2) }}%s".format(pad2, f.Name, newline))
          if (sameType) {

          } else {
            prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s{%s%sval curVerObj = new %s()%s".format(pad2, newline, pad2, ctrDef.typeString, newline))
            prevObjDeserializedBuf = prevObjDeserializedBuf.append("%scurVerObj.ConvertPrevToNewVerObj(prevObjfield._2._2)%s".format(pad2, f.Name, newline))
            prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s fields(\"%s\") = (prevObjfield._2._1,curVerObj) }%s".format(pad2, f.Name, newline))
          }
        }
      }
      //  val childType = typ.get.typeString.toString().split("\\[")(1).substring(0, typ.get.typeString.toString().split("\\[")(1).length() - 1)

      deserializedBuf.append("%s{ %s val length = com.ligadata.BaseTypes.IntImpl.DeserializeFromDataInputStream(dis) %s".format(pad2, newline, newline))
      deserializedBuf.append("%s if (length > 0) { %s%svar bytes = new Array[Byte](length);%s".format(pad2, newline, pad2, newline))
      deserializedBuf.append("%sdis.read(bytes);%s".format(pad2, newline))
      deserializedBuf.append("%sval inst = SerializeDeserialize.Deserialize(bytes, mdResolver, loader, false, \"%s\");%s".format(pad2, ctrDef.PhysicalName, newline))

      if (msg.Fixed.toLowerCase().equals("true")) {
        deserializedBuf.append("%s%s = inst.asInstanceOf[%s];%s%s} }%s".format(pad2, f.Name, ctrDef.typeString, newline, pad2, newline))
      } else if (msg.Fixed.toLowerCase().equals("false")) {
        deserializedBuf.append("%s fields(\"%s\") = (-1, inst.asInstanceOf[%s]);%s%s}%s".format(pad2, f.Name, ctrDef.typeString, newline, pad2, newline))
        deserializedBuf.append("%s else fields(\"%s\") = (-1, new %s()) } %s".format(pad2, f.Name, ctrDef.typeString, newline, pad2, newline))
        if (membrMatchTypeNotMatch) {
          mappedPrevTypNotrMatchkeys.append("\"" + f.Name + "\",")
        }
      }

      if (msg.Fixed.toLowerCase().equals("true")) {

        withMethod = withMethod.append("%s %s def with%s(value: %s) : %s = {%s".format(newline, pad1, f.Name, ctrDef.PhysicalName, msg.Name, newline))
        withMethod = withMethod.append("%s this.%s = value %s".format(pad1, f.Name, newline))
        withMethod = withMethod.append("%s return this %s %s } %s".format(pad1, newline, pad1, newline))
        fromFuncBuf = fromFuncBuf.append("%s if (other.%s != null) { %s".format(pad2, f.Name, newline))
        fromFuncBuf = fromFuncBuf.append("%s %s = other.%s.Clone.asInstanceOf[%s] %s".format(pad2, f.Name, f.Name, ctrDef.PhysicalName, newline))
        fromFuncBuf = fromFuncBuf.append("%s } %s ".format(pad2, newline))
        fromFuncBuf = fromFuncBuf.append("%s else %s = null; %s".format(pad2, f.Name, newline))

	    getNativeKeyValues = getNativeKeyValues.append("%s keyValues(\"%s\") = (\"%s\", %s); %s".format(pad1, f.Name, f.NativeName, f.Name, newline)) 

      } else if (msg.Fixed.toLowerCase().equals("false")) {
        withMethod = withMethod.append("%s%s def with%s(value: %s) : %s = {%s".format(newline, pad1, f.Name, ctrDef.PhysicalName, msg.Name, newline))
        withMethod = withMethod.append("%s fields(\"%s\") = (-1, value) %s".format(pad1, f.Name, newline))
        withMethod = withMethod.append("%s return this %s %s } %s".format(pad1, newline, pad1, newline))
        fromFuncBuf = fromFuncBuf.append("%s if (other.fields.contains(\"%s\")) { %s".format(pad2, f.Name, newline))
        fromFuncBuf = fromFuncBuf.append("%s fields(\"%s\") = (-1, other.fields(\"%s\")._2.asInstanceOf[%s].Clone.asInstanceOf[%s]); %s".format(pad2, f.Name, f.Name, ctrDef.PhysicalName, ctrDef.PhysicalName, newline))
        fromFuncBuf = fromFuncBuf.append("%s } %s".format(pad2, newline))

      }
      var nativeKeyMap: String = "(\"%s\", \"%s\"), ".format(f.Name, f.NativeName)

      returnAB += scalaclass.toString
      returnAB += assignCsvdata.toString
      returnAB += assignJsondata.toString
      returnAB += assignXmldata.toString
      returnAB += addMsg.toString
      returnAB += keysStr.toString
      returnAB += serializedBuf.toString
      returnAB += deserializedBuf.toString
      returnAB += prevObjDeserializedBuf.toString
      returnAB += convertOldObjtoNewObjBuf.toString
      returnAB += mappedPrevVerMatchkeys.toString
      returnAB += mappedPrevTypNotrMatchkeys.toString
      returnAB += fixedMsgGetKeyStrBuf.toString
      returnAB += withMethod.toString
      returnAB += fromFuncBuf.toString
      returnAB += nativeKeyMap.toString
      returnAB += getNativeKeyValues.toString

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:"+stackTrace)
        throw e
      }
    }
    //  (scalaclass.toString, assignCsvdata.toString, assignJsondata.toString, assignXmldata.toString, list, argsList, addMsg.toString, jarset, keysStr.toString, serializedBuf.toString, deserializedBuf.toString, prevObjDeserializedBuf.toString, convertOldObjtoNewObjBuf.toString, mappedPrevVerMatchkeys.toString, mappedPrevTypNotrMatchkeys.toString, fixedMsgGetKeyStrBuf.toString, withMethod.toString, fromFuncOfFixed.toString)

    (list, argsList, jarset, returnAB.toArray)
  }
}