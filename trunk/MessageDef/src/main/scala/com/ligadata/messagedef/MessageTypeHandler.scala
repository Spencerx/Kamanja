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
import com.ligadata.kamanja.metadata.BaseAttributeDef
import com.ligadata.kamanja.metadata.StructTypeDef
import com.ligadata.kamanja.metadata.AttributeDef
import com.ligadata.Utils.Utils

import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.Exceptions.StackTrace

class MessageTypeHandler {

  private def handleMessage(mdMgr: MdMgr, ftypeVersion: Long, f: Element, msg: Message, childs: Map[String, Any], recompile: Boolean): (String, String, String, String, List[(String, String)], List[(String, String, String, String, Boolean, String)], String, Set[String], String, String, String, String, String, String, String, String) = {

    var scalaclass = new StringBuilder(8 * 1024)
    var assignCsvdata = new StringBuilder(8 * 1024)
    var assignJsondata = new StringBuilder(8 * 1024)
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
    var serializedBuf = new StringBuilder(8 * 1024)
    var deserializedBuf = new StringBuilder(8 * 1024)
    var prevObjDeserializedBuf = new StringBuilder(8 * 1024)
    var convertOldObjtoNewObjBuf = new StringBuilder(8 * 1024)
    var mappedMsgFieldsVar = new StringBuilder(8 * 1024)
    var mappedPrevVerMatchkeys = new StringBuilder(8 * 1024)
    var mappedPrevTypNotrMatchkeys = new StringBuilder(8 * 1024)
    var fixedMsgGetKeyStrBuf = new StringBuilder(8 * 1024)
      val logger = this.getClass.getName
      lazy val log = LogManager.getLogger(logger)
    try {

      var msgDef: MessageDef = mdMgr.Message(f.Ttype, ftypeVersion, true).getOrElse(null)
      if (msgDef == null) throw new Exception(f.Ttype + " do not exists throwing null pointer")
      val msgDefobj = "com.ligadata.KamanjaBase.BaseMsg"
      scalaclass = scalaclass.append("%svar %s:%s = _;%s".format(pad1, f.Name, msgDefobj, newline))
      assignCsvdata.append("%s//Not Handling CSV Populate for member type Message".format(pad2))
      assignJsondata.append("%s//Not Handling JSON Populate for member type Message".format(pad2))
      assignXmldata.append("%s//Not Handling XML Populate for member type Message".format(pad2))
      //  assignCsvdata.append("%s%s.populate(inputdata);\n%sinputdata.curPos = inputdata.curPos+1\n".format(pad2, f.Name, pad2))
      // assignJsondata.append(assignJsonDataMessage(f.Name))
      //  assignXmldata.append("%s%s.populate(xmlData)%s".format(pad3, f.Name, newline))

      if ((msgDef.dependencyJarNames != null) && (msgDef.jarName != null)) {
        jarset = jarset + msgDef.JarName ++ msgDef.dependencyJarNames
      } else if ((msgDef.jarName != null))
        jarset = jarset + msgDef.JarName
      else if (msgDef.dependencyJarNames != null)
        jarset = jarset ++ msgDef.dependencyJarNames

      argsList = (f.NameSpace, f.Name, msgDef.NameSpace, msgDef.Name, false, null) :: argsList
      addMsg.append(pad2 + "if(curVal._2.compareToIgnoreCase(\"" + f.Name + "\") == 0) {" + newline + "\t")
      addMsg.append(pad3 + f.Name + " = msg.asInstanceOf[" + msgDef.typeString + "]" + newline + pad3 + "} else ")
      if (msg.PrimaryKeys != null) {
        getMsg.append(pad2 + "if(curVal._2.compareToIgnoreCase(\"" + f.Name + "\") == 0) {" + newline + "\t")
        getMsg.append("%sval pkd = %s.PrimaryKeyData%s".format(pad3, f.Name, newline))
        getMsg.append("%sif(pkd.sameElements(primaryKey)) {%s".format(pad3, newline))
        getMsg.append("%sreturn %s %s%s}%s%s}else".format(pad3, f.Name, newline, pad3, newline, pad3))
      }

      if (msg.Fixed.toLowerCase().equals("false")) {
        mappedMsgFieldsVar.append("%s fields(\"%s\") = (-1, new %s )%s".format(pad2, f.Name, msgDef.typeString, newline))

      }

      if (msg.Fixed.toLowerCase().equals("true")) {
        serializedBuf.append("%s {if (%s == null)  com.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos,0);%s".format(pad2, f.Name, newline))

      } else if (msg.Fixed.toLowerCase().equals("false")) {
        serializedBuf.append("%s { val (idx, %s) = getOrElse(\"%s\", null);%s".format(pad2, f.Name, f.Name, newline))
        serializedBuf.append("%s if (%s == null) com.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos, 0) %s".format(pad2, f.Name, newline))
      }
      serializedBuf.append("%s else { val bytes = SerializeDeserialize.Serialize(%s.asInstanceOf[%s])%s".format(pad2, f.Name, msgDef.typeString, newline))
      serializedBuf.append("%s com.ligadata.BaseTypes.IntImpl.SerializeIntoDataOutputStream(dos,bytes.length);%s".format(pad2, newline))
      serializedBuf.append("%s dos.write(bytes);%s} %s }%s".format(pad2, newline, newline, newline))

      val curObjtype = msgDef.physicalName

      var memberExists: Boolean = false
      var membrMatchTypeNotMatch = false
      var sameType: Boolean = false
      if (childs != null) {
        if (childs.contains(f.Name)) {
          var child = childs.getOrElse(f.Name, null)
          if (child != null) {
            val fullname = child.asInstanceOf[AttributeDef].aType.FullName
            if (fullname != null && fullname.trim() != "" && fullname.equals(msgDef.FullName)) {
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

      if (memberExists) {
        // convertOldObjtoNewObjBuf = convertOldObjtoNewObjBuf.append("%s%s = oldObj.%s%s".format(pad2, f.Name, f.Name, newline))
        if (msg.Fixed.toLowerCase().equals("true")) {
          prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s{%s%sval curVerObj = new %s()%s".format(pad2, newline, pad2, msgDef.typeString, newline))
          prevObjDeserializedBuf = prevObjDeserializedBuf.append("%scurVerObj.ConvertPrevToNewVerObj(prevVerObj.%s)%s".format(pad2, f.Name, newline))
          prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s%s = curVerObj}%s".format(pad2, f.Name, newline))
        }
        if (msg.Fixed.toLowerCase().equals("false")) {
          mappedPrevVerMatchkeys.append("\"" + f.Name + "\",")
          prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s case \"%s\" => { %s".format(pad2, f.Name, newline))
          prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s if(prevObjfield._2._2 != null){ fields(\"%s\") = (prevObjfield._2._1,prevObjfield._2._2) }}%s".format(pad2, f.Name, newline))
          if (sameType) {

          } else {
            prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s{%s%sval curVerObj = new %s()%s".format(pad2, newline, pad2, msgDef.typeString, newline))
            prevObjDeserializedBuf = prevObjDeserializedBuf.append("%scurVerObj.ConvertPrevToNewVerObj(prevObjfield._2._2)%s".format(pad2, f.Name, newline))
            prevObjDeserializedBuf = prevObjDeserializedBuf.append("%s fields(\"%s\") = (prevObjfield._2._1,curVerObj) }%s".format(pad2, f.Name, newline))
          }
        }
      }

      //  val childType = typ.get.typeString.toString().split("\\[")(1).substring(0, typ.get.typeString.toString().split("\\[")(1).length() - 1)

      deserializedBuf.append("%s{ %s val length = com.ligadata.BaseTypes.IntImpl.DeserializeFromDataInputStream(dis) %s".format(pad2, newline, newline))
      deserializedBuf.append("%s if (length > 0) { %s%svar bytes = new Array[Byte](length);%s".format(pad2, newline, pad2, newline))
      deserializedBuf.append("%sdis.read(bytes);%s".format(pad2, newline))
      deserializedBuf.append("%sval inst = SerializeDeserialize.Deserialize(bytes, mdResolver, loader, false, \"%s\");%s".format(pad2, msgDef.PhysicalName, newline))

      if (msg.Fixed.toLowerCase().equals("true")) {
        deserializedBuf.append("%s%s = inst.asInstanceOf[BaseMsg];%s%s} }%s".format(pad2, f.Name, newline, pad2, newline))
      } else if (msg.Fixed.toLowerCase().equals("false")) {
        deserializedBuf.append("%s fields(\"%s\") = (-1, inst.asInstanceOf[%s]);%s%s}%s".format(pad2, f.Name, msgDef.typeString, newline, pad2, newline))
        deserializedBuf.append("%s else fields(\"%s\") = (-1, new %s()) } %s".format(pad2, f.Name, msgDef.typeString, newline, pad2, newline))
        if (membrMatchTypeNotMatch) {
          mappedPrevTypNotrMatchkeys.append("\"" + f.Name + "\",")

        }
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.debug("StackTrace:"+stackTrace)
        throw e
      }
    }
    (scalaclass.toString, assignCsvdata.toString, assignJsondata.toString, assignXmldata.toString, list, argsList, addMsg.toString, jarset, getMsg.toString, serializedBuf.toString, deserializedBuf.toString, prevObjDeserializedBuf.toString, convertOldObjtoNewObjBuf.toString, mappedPrevVerMatchkeys.toString, mappedPrevTypNotrMatchkeys.toString, fixedMsgGetKeyStrBuf.toString)
  }

}