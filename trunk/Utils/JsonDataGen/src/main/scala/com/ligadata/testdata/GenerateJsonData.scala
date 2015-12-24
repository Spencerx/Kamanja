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

package com.ligadata.testdata

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scala.collection.mutable.HashMap
import java.io.File
import java.io.PrintWriter
import com.ligadata.Exceptions.StackTrace
import org.apache.logging.log4j._

object GenerateJsonData {

  private type OptionMap = Map[Symbol, Any]

  private def nextOption(map: OptionMap, list: List[String]): OptionMap = {
    def isSwitch(s: String) = (s.charAt(0) == '-')
    list match {
      case Nil => map
      case "--inputfile" :: value :: tail =>
        nextOption(map ++ Map('inputfile -> value), tail)
      case "--outputfile" :: value :: tail =>
        nextOption(map ++ Map('outputfile -> value), tail)
      case "--formatfile" :: value :: tail =>
        nextOption(map ++ Map('formatfile -> value), tail)
      case option :: tail => {
        println("Unknown option " + option)
        sys.exit(1)
      }
    }
  }

  def main(args: Array[String]) {

    if (args.length == 0)
      throw new Exception("Please pass the input file as parameter")
    val options = nextOption(Map(), args.toList)

    val inputfile = options.getOrElse('inputfile, null).toString.trim
    if (inputfile == null && inputfile.toString().trim() == "")
      throw new Exception("Please pass the input file as parameter")

    val outputfile = options.getOrElse('outputfile, null).toString.trim
    if (outputfile == null && outputfile.toString().trim() == "")
      throw new Exception("Please pass the input file as parameter")

    val formatfile = options.getOrElse('formatfile, null).toString.trim
    if (formatfile == null && formatfile.toString().trim() == "")
      throw new Exception("Please pass the message format file as parameter")

    var genBen: GenerateJsonData = new GenerateJsonData()
    genBen.processInputData(inputfile.toString, outputfile.toString, formatfile.toString())
  }

}

class GenerateJsonData {
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
  def processInputData(inputfile: String, outputfile: String, formatfile: String) = {
    try {
      var outfile = new PrintWriter(new File(outputfile))
      var formatMap: Map[String, Array[String]] = Map()
      var formatResult: Array[String] = Array[String]()

      //if ((inputfile == null) || outputfile == null || formatfile == null) throw new Exception(" Error")

      for (formatline <- scala.io.Source.fromFile(new File(formatfile), "utf-8").getLines()) {
        if (formatline != null && formatline.trim.size != 0 && formatline.size > 1) {
          var formatResult = formatline.toLowerCase.split(",", -1)
          formatMap += (formatResult(0).toString() -> formatResult)
        }
      }

      for (line <- scala.io.Source.fromFile(new File(inputfile), "utf-8").getLines()) {

        if (line != null && line.trim.size != 0 && line.size > 1) {
          genData(line, formatMap, outfile)
        }
      }
      outfile.close
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:"+stackTrace)
        throw new Exception("Error: " + stackTrace)
      }
    }
  }

  def genData(line: String, formatMap: Map[String, Array[String]], outputfile: PrintWriter) = {
    try {
      var jsonMap: Map[String, String] = Map()
      var finalMap: Map[String, Map[String, String]] = Map()
      var count = 1

      var dataResult = line.split(",", -1);
      var fldData = formatMap.getOrElse(dataResult(0).toLowerCase, null);

      if ((fldData != null) && fldData.isInstanceOf[Array[String]]) {
        if (dataResult.size > fldData.size) {
          println("Warn: " + dataResult(0) + " data size: " + dataResult.size + ", format size: " + fldData.size)
        }
        if (dataResult.size >= fldData.size) {
          var index = 0
          fldData.foreach { fld =>
            if (index > 0)
              jsonMap += (fld -> dataResult(index))
            index = index + 1
          }
          finalMap += (dataResult(0).toLowerCase -> jsonMap)
          writeFile(outputfile, finalMap)
        } else {
          println("Error: " + dataResult(0) + " data size: " + dataResult.size + ", format size: " + fldData.size)
        }
      }
      count = count + 1
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:"+stackTrace)
        throw new Exception("Error: " + stackTrace)
      }
    }

  }

  def writeFile(outputfile: PrintWriter, jsonMap: Map[String, Map[String, String]]) = {
    try {

      outputfile.write(compact(render(jsonMap)))
      outputfile.write("\n")
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:"+stackTrace)
        throw new Exception("Error: " + stackTrace)
      }
    }

  }

}

