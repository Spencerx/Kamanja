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

class ConceptTypeHandler {

  var cnstObjVar = new ConstantMsgObjVarGenerator

  def handleConcept(mdMgr: MdMgr, ftypeVersion: Long, f: Element, msg: Message): (String, String, String, String, List[(String, String)], List[(String, String, String, String, Boolean, String)], String, Set[String], String, String, String) = {

    var scalaclass = new StringBuilder(8 * 1024)
    var assignCsvdata = new StringBuilder(8 * 1024)
    var assignJsondata = new StringBuilder(8 * 1024)
    var assignXmldata = new StringBuilder(8 * 1024)
    var addMsg = new StringBuilder(8 * 1024)
    var list = List[(String, String)]()
    var argsList = List[(String, String, String, String, Boolean, String)]()
    var jarset: Set[String] = Set();
    val pad1 = "\t"
    val pad2 = "\t\t"
    val pad3 = "\t\t\t"
    val pad4 = "\t\t\t\t"
    val newline = "\n"
    var fname: String = ""
    var fixedMsgGetKeyStrBuf = new StringBuilder(8 * 1024)
    var withMethod = new StringBuilder(8 * 1024)
    var fromFuncBuf = new StringBuilder(8 * 1024)
    val LOG = LogManager.getLogger(getClass)
    try {

      var attribute: BaseAttributeDef = mdMgr.Attribute(f.Ttype, ftypeVersion, true).getOrElse(null)

      if (attribute == null) throw new Exception("Attribute " + f.Ttype + " do not exists throwing Null pointer")

      if ((attribute.typeString.equals("String")) || (attribute.typeString.equals("Int")) || (attribute.typeString.equals("Float")) || (attribute.typeString.equals("Double")) || (attribute.typeString.equals("Char"))) {
        if (attribute.typeDef.implementationName.isEmpty())
          throw new Exception("Implementation Name not found in metadata for namespace %s" + f.Ttype)
        else
          fname = attribute.typeDef.implementationName + ".Input"

        if (f.Name.split("\\.").size == 2) {

          val dval: String = cnstObjVar.getDefVal("system." + attribute.typeString.toString().toLowerCase())

          scalaclass = scalaclass.append("%svar %s:%s = _ ;%s".format(pad1, f.Name.split("\\.")(1), attribute.typeString, newline))
          assignCsvdata.append("%s%s = %s(list(inputdata.curPos));\n%sinputdata.curPos = inputdata.curPos+1\n".format(pad2, f.Name.split("\\.")(1), fname, pad2))
          assignJsondata.append("%s %s = %s(map.getOrElse(\"%s\", %s).toString)%s".format(pad2, f.Name.split("\\.")(1), fname, f.Name.split("\\.")(1), dval, newline))
          assignXmldata.append("%sval _%sval_  = (xml \\\\ \"%s\").text.toString %s%sif (_%sval_  != \"\")%s%s =  %s( _%sval_ ) else %s = %s%s".format(pad3, f.Name.split("\\.")(1), f.Name.split("\\.")(1), newline, pad3, f.Name.split("\\.")(1), pad2, f.Name.split("\\.")(1), fname, f.Name.split("\\.")(1), f.Name.split("\\.")(1), dval, newline))

        }

        // assignCsvdata.append("%s%s = %s(list(inputdata.curPos));\n%sinputdata.curPos = inputdata.curPos+1\n".format(pad2, f.Name, fname, pad2))

      } else {
        scalaclass = scalaclass.append("%svar %s:%s = new %s();%s".format(pad1, f.Name, attribute.typeString, attribute.typeString, newline))
      }
      //assignCsvdata.append("%sinputdata.curPos = %s.assignCSV(list, inputdata.curPos);\n%sinputdata.curPos = inputdata.curPos+1\n".format(pad2, f.Name, pad2))

      if ((attribute.dependencyJarNames != null) && (attribute.jarName != null)) {
        jarset = jarset + attribute.JarName ++ attribute.dependencyJarNames
      } else if ((attribute.jarName != null))
        jarset = jarset + attribute.JarName
      else if (attribute.dependencyJarNames != null)
        jarset = jarset ++ attribute.dependencyJarNames

      argsList = (attribute.NameSpace, attribute.Name, attribute.typeDef.NameSpace, attribute.typeDef.Name, true, null) :: argsList
      fixedMsgGetKeyStrBuf.append("%s if(key.equals(\"%s\")) return %s; %s".format(pad1, f.Name, f.Name, newline))

      if (msg.Fixed.toLowerCase().equals("true")) {

        withMethod = withMethod.append("%s %s def with%s(value: %s) : %s = {%s".format(newline, pad1, f.Name, attribute.typeString, msg.Name, newline))
        withMethod = withMethod.append("%s this.%s = value %s".format(pad1, f.Name, newline))
        withMethod = withMethod.append("%s return this %s %s } %s".format(pad1, newline, pad1, newline))
        fromFuncBuf = fromFuncBuf.append("%s if (other.%s != null ) { %s".format(pad2, f.Name, f.Name, newline))
        fromFuncBuf = fromFuncBuf.append("%s%s = other.%s%s".format(pad2, f.Name, f.Name, newline))
        fromFuncBuf = fromFuncBuf.append("%s else %s = null; %s".format(pad2, f.Name, newline))

      } else if (msg.Fixed.toLowerCase().equals("false")) {
        withMethod = withMethod.append("%s%s def with%s(value: %s) : %s = {%s".format(newline, pad1, f.Name, attribute.typeString, msg.Name, newline))
        withMethod = withMethod.append("%s fields(\"%s\") = (-1, value )%s".format(pad1, f.Name, newline))
        withMethod = withMethod.append("%s return this %s %s } %s".format(pad1, newline, pad1, newline))

        fromFuncBuf = fromFuncBuf.append("%s if (other.fields.contains(\"%s\")) { %s".format(pad2, f.Name, newline))
        fromFuncBuf = fromFuncBuf.append("%s fields(\"%s\") = (-1, other.fields(\"%s\")._2.asInstanceOf[%s].Clone.asInstanceOf[%s]); %s".format(pad2, f.Name, f.Name, attribute.typeString, attribute.typeString, newline))
        fromFuncBuf = fromFuncBuf.append("%s } %s".format(pad2, newline))

      }

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:"+stackTrace)
        throw e
      }
    }
    (scalaclass.toString, assignCsvdata.toString, assignJsondata.toString, assignXmldata.toString, list, argsList, addMsg.toString, jarset, fixedMsgGetKeyStrBuf.toString, withMethod.toString, fromFuncBuf.toString)
  }

}