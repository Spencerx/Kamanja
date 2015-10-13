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

package com.ligadata.outputmsg

import com.ligadata.kamanja.metadata.OutputMsgDef
import com.ligadata.KamanjaBase.{ BaseMsg, MessageContainerBase }
import com.ligadata.kamanja.metadata.MdMgr
import com.ligadata.kamanja.metadata._
import org.apache.log4j.Logger
import com.ligadata.Exceptions.StackTrace

import scala.collection.mutable.{ ArrayBuffer }
import org.json4s.jackson.Serialization
import scala.collection.JavaConverters._

class OutputMsgGenerator {

  val logger = this.getClass.getName
  lazy val log = Logger.getLogger(logger)
  var myMap: scala.collection.mutable.Map[String, Any] = scala.collection.mutable.Map[String, Any]()

  /**
   * Generates the queue name, partitionkeys and output format
   * @param Message
   * @param Model Results
   * @param Array of OutputMsgDef
   * @return Array of queueName, Parition Keys and OutputFormat
   */
  def generateOutputMsg(message: MessageContainerBase, ModelReslts: scala.collection.mutable.Map[String, Array[(String, Any)]], allOutputMsgs: Array[OutputMsgDef]): Array[(String, Array[String], String)] = {
    try {
      log.info("ModelReslts  " + ModelReslts.size)
      log.info("allOutputMsgs  " + allOutputMsgs.size)
      val (outputMsgExits, exitstingOutputMsgDefs, map) = getOutputMsgdef(message, ModelReslts, allOutputMsgs)
      if (map != null)
        myMap = map
      if (outputMsgExits) {
        val newOutputFormats = extract(exitstingOutputMsgDefs)
        newOutputFormats.foreach(f => log.info("Final 1 " + f._1 + " 2   " + f._2.toList + " 3  " + f._3))
        newOutputFormats
      } else throw new Exception("Output Msg Def in the sent list of OutputMsgDef do not match with either Model Results or Top level Msg")
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.error("\nStackTrace:"+stackTrace)
        throw e
      }
    }
  }

  /**
   * Extract the data from Map and return the queue name, partition keys and output format
   * @param Array of OutputMsgDef
   * @return Array of queue name, partition keys and outputformat
   */
  private def extract(exitstingOutputMsgDefs: Array[OutputMsgDef]): Array[(String, Array[String], String)] = {
    val extractor = """\$\{([^}]+)\}""".r
    val output: ArrayBuffer[(String, Array[String], String)] = new ArrayBuffer[(String, Array[String], String)]()

    try {
      var newOutputFormat: String = ""
      var paritionKeys: Array[String] = Array[String]()
      exitstingOutputMsgDefs.foreach(outputMsgDef => {
        val outputformat = outputMsgDef.OutputFormat
        newOutputFormat = extractor.replaceAllIn(outputformat, GetValue _) //.replaceAllIn(outputMsgDef.OutputFormat, GetValue _)
        outputMsgDef.ParitionKeys.foreach(partitionKey => {
          log.info("partitionKey._1.toLowerCase() " + partitionKey._1.toLowerCase())
          var key: StringBuffer = new StringBuffer()
          partitionKey._2.foreach(prtkey => {
            key = key.append("." + prtkey._1)
          })
          val pkey = partitionKey._1 + key
          val parttionkey = myMap.getOrElse(pkey, "")
          paritionKeys +:= ValueToString(parttionkey, ",")
        })

        val queueName = outputMsgDef.Queue
        val returnVal = (queueName, paritionKeys, newOutputFormat)
        output += returnVal
      })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.error("\nStackTrace:"+stackTrace)
        throw e
      }
    }
    output.toArray
  }

  private def GetValue(m: scala.util.matching.Regex.Match) = {
    import java.util.regex.Matcher
    val grp = m.group(1)
    // Get the value from Declaration Variable or Message or Models for varname.
    val varvalue = myMap.getOrElse(grp.toString().toLowerCase(), null)
    ValueToString(varvalue, ",")
  }
  /**
   *
   */
  private def getOutputMsgdef(Msg: MessageContainerBase, ModelReslts: scala.collection.mutable.Map[String, Array[(String, Any)]], allOutputMsgs: Array[OutputMsgDef]): (Boolean, Array[OutputMsgDef], scala.collection.mutable.Map[String, Any]) = {
    var outputMsgDefExists = false
    var finalOutputMsgs: ArrayBuffer[OutputMsgDef] = new ArrayBuffer[OutputMsgDef]()
    var value: Any = null
    try {
      var count: Int = 0
      var mymap: scala.collection.mutable.Map[String, Any] = scala.collection.mutable.Map[String, Any]()
      allOutputMsgs.foreach(outputMsg => {
        //    val delimiter = outputMsg.DataDeclaration.getOrElse("Delim", ",")
        val delimiter = ","
        outputMsg.DataDeclaration.foreach(f => {
          mymap(f._1.toLowerCase()) = f._2
        })

        outputMsg.Fields.foreach(field => {
          val msgOrMdlFullName = field._1._1
          log.info("msgOrMdlFullName : " + msgOrMdlFullName)
          field._2.foreach(fld => {

            var key: StringBuffer = new StringBuffer()
            fld._1.foreach(f => {
              key = key.append("." + f._1)
            })
            val mapkey = msgOrMdlFullName + key
            val fieldNames = key.toString().split("\\.")
            val fieldName = fieldNames(1)
            log.info("mapkey " + mapkey)
            if (msgOrMdlFullName.toLowerCase().equals(Msg.FullName.toLowerCase())) {
              /////
              // if(Msg.asInstanceOf[MessageContainerBase].get(fldName.toString)) != None)
              value = getFldValue(fld._1, Msg, delimiter)
            } else {
              ModelReslts.foreach(mdlMap => {
                if (mdlMap._1.toLowerCase().equals(msgOrMdlFullName.toLowerCase())) {
                  mdlMap._2.foreach(f => {
                    if (f._1.toLowerCase().equals(fieldName.toLowerCase())) {
                      value = ValueToString(f._2, delimiter)
                    } else if (fld._2 != null && fld._2.trim() != "") { //get the default value
                      value = fld._2
                    }

                  })
                  count = count + 1
                }
              })
            }
            //  }
            log.info("mapkey: " + mapkey + " value: " + value)
            mymap(mapkey) = value
          })
          count = count + 1
        })

        mymap.foreach(map => log.info("map " + map._1 + "value " + map._2))
        if (count > 0) {
          outputMsgDefExists = true
          finalOutputMsgs += outputMsg
        }
      })
      log.info("outputMsgDefExists  " + outputMsgDefExists)
      finalOutputMsgs.foreach(o => {
      })
      (outputMsgDefExists, finalOutputMsgs.toArray, mymap)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.error("\nStackTrace:"+stackTrace)
        throw e
      }
    }
  }

  private def getFldValue(fld: Array[(String, String)], message: MessageContainerBase, delimiter: String): String = {

    var value: String = ""
    if (fld.size == 0) {
      value = null
    } else {
      val fldSize = fld.size
      var iDx: Int = 0
      value = getMsgFldValue(message, fld(0)._1, fld(0)._2, fld, 0, delimiter)

    }
    value

  }

  private def getMsgFldValue(message: Any, fldName: String, fldType: String, fld: Array[(String, String)], index: Int, delimiter: String): String = {
    try {

      log.info("delimter**** : " + delimiter)
      if (fldType == null || fldType.trim() == "")
        throw new Exception("Type of field " + fldName + " do not exist")
      val fldtyp = fldType.split("\\.")

      if (index >= fld.size)
        return null

      val namespace = fldtyp(0).toString
      val name = fldtyp(1).toString
      log.info("fldtyp " + namespace + " : " + name)
      val typ = MdMgr.GetMdMgr.Type(namespace, name, -1, true)

      if (typ == null || typ == None)
        throw new Exception("Type do not exist in metadata for " + fldType)

      val typetype = typ.get.tType.toString().toLowerCase()
      // val typetype = typ.get.tTypeType.toString().toLowerCase()
      log.info("typetype : " + typetype)
      if (typetype != null) {

        if (typetype.toString().toLowerCase().equals("tcontainer") || typetype.toString().toLowerCase().equals("tmessage")) {
          if (fld.size == index)
            return ""
          if (message.isInstanceOf[MessageContainerBase]) {
            val msg = message.asInstanceOf[MessageContainerBase].get(fldName.toString)
            return getMsgFldValue(msg, fld(index + 1)._1, fld(index + 1)._2, fld, index + 1, delimiter)
          }

        } else if (typetype.equals("tarray")) {
          val arrayType = typ.get.asInstanceOf[ArrayTypeDef]

          if (arrayType == null) throw new Exception("Array type do not exist")

          if (arrayType.elemDef.tTypeType.toString().toLowerCase().equals("tscalar")) {
            var arryValue: Array[Any] = Array[Any]()
            arryValue = message.asInstanceOf[MessageContainerBase].get(fldName.toString).asInstanceOf[Array[Any]]
            var valueStr: String = ""
            arryValue.foreach(value => {
              valueStr = value.toString + delimiter
            })

            if (valueStr.trim().length() > 1)
              return valueStr.substring(0, valueStr.length() - 1)
            else return valueStr

          } else {
            val arryMsgs = message.asInstanceOf[MessageContainerBase].get(fldName.toString).asInstanceOf[Array[MessageContainerBase]]
            var value: String = ""
            arryMsgs.foreach(mc => {
              value = getMsgFldValue(mc, fld(index + 1)._1, fld(index + 1)._2, fld, index + 1, delimiter) + delimiter
            })
            if (value.trim().length() > 1)
              return value.substring(0, value.length() - 1)
            else return value
          }

        } else if (typetype.toString().toLowerCase().equals("tarraybuf")) {

          val arrayBufType = typ.get.asInstanceOf[ArrayBufTypeDef]

          if (arrayBufType == null) throw new Exception("Array type do not exist")

          if (arrayBufType.elemDef.tTypeType.toString().toLowerCase().equals("tscalar")) {
            var arryBufValue: ArrayBuffer[Any] = ArrayBuffer[Any]()
            arryBufValue = message.asInstanceOf[MessageContainerBase].get(fldName.toString).asInstanceOf[ArrayBuffer[Any]]
            var valueStr: String = ""
            arryBufValue.foreach(value => {
              valueStr = value.toString + delimiter
            })
            if (valueStr.trim().length() > 1)
              return valueStr.substring(0, valueStr.length() - 1)
            else return valueStr

          } else {

            val arryBuf = message.asInstanceOf[MessageContainerBase].get(fldName.toString).asInstanceOf[ArrayBuffer[MessageContainerBase]]
            var value: String = ""

            arryBuf.foreach(mc => {
              log.info(mc)
              value = getMsgFldValue(mc, fld(index + 1)._1, fld(index + 1)._2, fld, index + 1, delimiter) + delimiter
            })
            if (value.trim().length() > 1)
              return value.substring(0, value.length() - 1)
            else return value
          }
        } else if (message.isInstanceOf[MessageContainerBase]) {
          val value = message.asInstanceOf[MessageContainerBase].get(fldName.toString)
          if (typetype.equals("tstring") || typetype.equals("tlong") || typetype.equals("tfloat") || typetype.equals("tdouble") || typetype.equals("tboolean") || typetype.equals("tchar") || typetype.equals("tint")) {
            return ValueToString(value, ",")
          } else {
            return getMsgFldValue(value, fld(index + 1)._1, fld(index + 1)._2, fld, index + 1, delimiter)
          }
        }
      }

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.error("\nStackTrace:"+stackTrace)
        throw e
      }
    }
    return null
  }

  private def ValueToString(v: Any, delimiter: String): String = {
    if (v == null) return ""
    if (v.isInstanceOf[Set[_]]) {
      return v.asInstanceOf[Set[_]].mkString(delimiter)
    }
    if (v.isInstanceOf[List[_]]) {
      return v.asInstanceOf[List[_]].mkString(delimiter)
    }
    if (v.isInstanceOf[Array[_]]) {
      return v.asInstanceOf[Array[_]].mkString(delimiter)
    }
    if (v.isInstanceOf[ArrayBuffer[_]]) {
      return v.asInstanceOf[ArrayBuffer[_]].mkString(delimiter)
    }
    if (v.isInstanceOf[Map[_, _]]) {
      implicit val formats = org.json4s.DefaultFormats
      return Serialization.write(v.asInstanceOf[Map[String, _]])
    }
    if (v.isInstanceOf[java.util.Map[_, _]]) {
      implicit val formats = org.json4s.DefaultFormats
      return Serialization.write(v.asInstanceOf[java.util.Map[String, _]].asScala)
    }

    v.toString
  }

}
