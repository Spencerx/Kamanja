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
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.Exceptions.StackTrace

import scala.collection.mutable.{ ArrayBuffer }
import org.json4s.jackson.Serialization
import scala.collection.JavaConverters._

class OutputMsgGenerator {

  val logger = this.getClass.getName
  lazy val log = LogManager.getLogger(logger)
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
      log.debug("ModelReslts:%s,allOutputMsgs:%s".format(ModelReslts.size, allOutputMsgs.size))
      val (outputMsgExits, exitstingOutputMsgDefs, map) = getOutputMsgdef(message, ModelReslts, allOutputMsgs)
      if (outputMsgExits) {
        val newOutputFormats = extract(exitstingOutputMsgDefs, map)
        // newOutputFormats.foreach(f => log.info("Final 1 " + f._1 + " 2   " + f._2.toList + " 3  " + f._3))
        newOutputFormats
      } else {
        log.info("Output Msg Def in the sent list of OutputMsgDef do not match with either Model Results or Top level Msg")
        return Array[(String, Array[String], String)]()
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.error("Got Exception while preparing Output messages.\nStackTrace:" + stackTrace)
        throw e
      }
    }
  }

  /**
   * Extract the data from Map and return the queue name, partition keys and output format
   * @param Array of OutputMsgDef
   * @return Array of queue name, partition keys and outputformat
   */
  private def extract(exitstingOutputMsgDefs: Array[OutputMsgDef], myMap: java.util.HashMap[String, Any]): Array[(String, Array[String], String)] = {
    val output: ArrayBuffer[(String, Array[String], String)] = new ArrayBuffer[(String, Array[String], String)]()

    try {
      val sb = new StringBuilder()
      exitstingOutputMsgDefs.foreach(outputMsgDef => {
        sb.clear()

        outputMsgDef.FormatSplittedArray.foreach(t => {
          if (t._1 != null && t._1.size > 0)
            sb.append(t._1)
          if (t._2 != null && t._2.size > 0)
            sb.append(ValueToString(myMap.get(t._2), ","))
        })

        val newOutputFormat = sb.toString()
        var paritionKeys =
          outputMsgDef.ParitionKeys.map(partitionKey => {
            log.info("partitionKey._1.toLowerCase() " + partitionKey._1.toLowerCase())
            sb.clear()
            partitionKey._2.foreach(prtkey => {
              sb.append("." + prtkey._1)
            })
            val pkey = partitionKey._1 + sb
            val parttionkey = myMap.get(pkey)
            ValueToString(parttionkey, ",")
          })

        val queueName = outputMsgDef.Queue
        val returnVal = (queueName, paritionKeys, newOutputFormat)
        output += returnVal
      })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.error("\nStackTrace:" + stackTrace)
        throw e
      }
    }
    output.toArray
  }

  /**
   *
   */
  private def getOutputMsgdef(Msg: MessageContainerBase, ModelReslts: scala.collection.mutable.Map[String, Array[(String, Any)]], allOutputMsgs: Array[OutputMsgDef]): (Boolean, Array[OutputMsgDef], java.util.HashMap[String, Any]) = {
    var outputMsgDefExists = false
    var finalOutputMsgs: ArrayBuffer[OutputMsgDef] = new ArrayBuffer[OutputMsgDef]()
    try {
      val msgFullName = Msg.FullName.toLowerCase()
      val sb = new StringBuilder()
      var mymap = new java.util.HashMap[String, Any]()
      allOutputMsgs.foreach(outputMsg => {
        //    val delimiter = outputMsg.DataDeclaration.getOrElse("Delim", ",")
        var foundCount: Int = 0
        var notFoundCount: Int = 0
        val delimiter = ","
        outputMsg.DataDeclaration.foreach(f => {
          mymap.put(f._1.toLowerCase(), f._2)
        })

        outputMsg.Fields.foreach(field => {
          val msgOrMdlFullName = field._1._1
          field._2.foreach(fld => {
            sb.clear()
            fld._1.foreach(f => {
              sb.append("." + f._1)
            })
            val mapkey = msgOrMdlFullName + sb
            val fieldName = fld._1(0)._1
            // log.info("mapkey " + mapkey)
            var valueFound = false
            var value: Any = null
            if (msgOrMdlFullName.toLowerCase().equals(Msg.FullName.toLowerCase())) {
              /////
              // if(Msg.asInstanceOf[MessageContainerBase].get(fldName.toString)) != None)
              value = getFldValue(fld._1, Msg, delimiter)
              valueFound = true // BUGBUG:: for now we are simply taking the value is matched if the message is matched
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
                  valueFound = true // BUGBUG:: for now we are simply taking the value is matched if the model is matched
                }
              })
            }
            //  }
            // log.info("mapkey: " + mapkey + " value: " + value)
            if (valueFound) {
              foundCount += 1
              mymap.put(mapkey, value)
            } else {
              notFoundCount += 1
            }
          })
        })

        // mymap.foreach(map => log.info("map " + map._1 + "value " + map._2))
        if (notFoundCount == 0) { // BUGBUG:: Taking only if all models & messages found
          log.debug("All fields are accessable for output message definitioin: " + outputMsg.FullName)
          outputMsgDefExists = true
          finalOutputMsgs += outputMsg
        } else {
          log.debug("Not found all fields for message definition:%s. Found:%d, Notfound:%d".format(outputMsg.FullName, foundCount, notFoundCount))
        }
      })
      log.info("outputMsgDefExists  " + outputMsgDefExists)
      (outputMsgDefExists, finalOutputMsgs.toArray, mymap)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.error("\nStackTrace:" + stackTrace)
        throw e
      }
    }
  }

  private def getFldValue(fld: Array[(String, String, String, String)], message: MessageContainerBase, delimiter: String): String = {
    var value: String = ""
    if (fld.size == 0) {
      value = null
    } else {
      value = getMsgFldValue(message, fld, 0, delimiter)
    }
    value
  }

  private def getMsgFldValue(message: Any, fld: Array[(String, String, String, String)], index: Int, delimiter: String): String = {
    try {
      if (index >= fld.size)
        return null

      val fldName: String = fld(index)._1
      val fldType: String = fld(index)._2
      log.info("delimter**** : " + delimiter)
      if (fldType == null || fldType.trim() == "")
        throw new Exception("Type of field " + fldName + " do not exist")

      val tType = fld(index)._3
      val tTypeType = fld(index)._4

      // log.info("typetype : " + typetype)
      if (tType != null) {

        if (tTypeType.equals("tcontainer") || tTypeType.equals("tmessage")) {
          if (fld.size == index)
            return ""
          if (message.isInstanceOf[MessageContainerBase]) {
            val msg = message.asInstanceOf[MessageContainerBase].get(fldName.toString)
            return getMsgFldValue(msg, fld, index + 1, delimiter)
          }

        } else if (tType.equals("tarray")) {
          val typ = MdMgr.GetMdMgr.Type(fldType, -1, true)
          if (typ == null || typ == None)
            throw new Exception("Type do not exist in metadata for " + fldType)

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
              value = getMsgFldValue(mc, fld, index + 1, delimiter) + delimiter
            })
            if (value.trim().length() > 1)
              return value.substring(0, value.length() - 1)
            else return value
          }

        } else if (tType.equals("tarraybuf")) {
          val typ = MdMgr.GetMdMgr.Type(fldType, -1, true)
          if (typ == null || typ == None)
            throw new Exception("Type do not exist in metadata for " + fldType)

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
              value = getMsgFldValue(mc, fld, index + 1, delimiter) + delimiter
            })
            if (value.trim().length() > 1)
              return value.substring(0, value.length() - 1)
            else return value
          }
        } else if (message.isInstanceOf[MessageContainerBase]) {
          val value = message.asInstanceOf[MessageContainerBase].get(fldName.toString)
          if (tType.equals("tstring") || tType.equals("tlong") || tType.equals("tfloat") || tType.equals("tdouble") || tType.equals("tboolean") || tType.equals("tchar") || tType.equals("tint")) {
            return ValueToString(value, ",")
          } else {
            return getMsgFldValue(value, fld, index + 1, delimiter)
          }
        }
      }

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        log.error("\nStackTrace:" + stackTrace)
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
