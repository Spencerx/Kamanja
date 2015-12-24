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

package com.ligadata.pmml.udfs

import java.util.UUID
import org.joda.time.{LocalDate, DateTime}
import com.ligadata.pmml.udfs._
import com.ligadata.pmml.udfs.Udfs._
import scala.reflect.runtime.universe._
import org.joda.time.LocalDate
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.pmml.runtime._

/**
  * Sample udfs .. com.ligadata.pmml.udfs.CustomUdfs
    This is a sample udf library to illustrate how one would add their own library of
    functions to the Kamanja system such that they could be used in the PMML models run
    there.
    The udfs used in Kamanja must be declared in an object (i.e., they are static methods
    for those of you familiar with java).
    NOTE: If you want to invoke functions in the core library, make your UDF project that 
    builds your UDFs dependent on PmmlUdfs:
        lazy val CustomUdfLib = project.in(file("SampleApplication/CustomUdfLib")) dependsOn(PmmlUdfs)
    
    Include the following two lines in your imports:
        import com.ligadata.pmml.udfs._
        import com.ligadata.pmml.udfs.Udfs._
    The sample udf, ISO8601DateFmt, does use a function from the core udfs, so we include it here.
    Once it is built, you can run the extractUDFLibMetadata.scala script on it that
    will produce the metadata from it.
    Assuming you have the sbtProjDependencies.scala and extractUDFLibMetadata.scala
    scripts in a folder on your PATH, you can use this command from 'trunk' to compile it:
    extractUdfLibMetadata.scala --sbtProject PmmlUdfs 
                                --fullObjectPath com.mycompany.pmml.udfs.SomeCustomUdfs
                                --namespace Pmml
                                --versionNumber 1000000
                                --typeDefsPath /tmp/typesforSomeCustomUdfs.json
                                --fcnDefsPath /tmp/fcnsforSomCustomUdfs.json
    The version number supplied should be greater than any prior version used for the same 
    Udfs.  This is currently not checked.  It will complain when you try to load the metadata instead.
    The 1000000 will produce a "000000.000001.000000" version number for each of your udf functions.
    The last two arguments are paths to the json that is produced by the script looking
    into the udf jar for PmmlUdfs at the functions on the 'fullObjectPath'.
    As written, the types do not really need to be loaded into the MetadataAPI, as they 
    have all been defined in the Kamanja metadata bootstrap.  The udfs json file must be loaded 
    however.  The types would be needed if you introduced a type that has not been previously declared
    in the bootstrap.  If you are not sure there is no harm loading the types file.  If one of 
    the types is already present, an error will be logged to that effect.  This is probably ok,
    however, you should inspect the types that are duplicate to verify they reflect what
    your intention is.
    To use this udf library in one of your models, you need to reference it in the model
    itself.  In the DataDictionary section, put the following:
        <DataField name="UDFSearchPath" displayName="UDFSearchPath" dataType="container">
          <Value value="com.mycompany.pmml.udfs.CustomUdfs" property="valid"/>
        </DataField>
    Important: Don't forget to upload your library to the Kamanja cluster.  There is an upload jar
    protocol for this purpose.
  */
object CustomUdfs extends LogTrait {
  
    /**
     * Returns a random UUID string
     * NOTE: idGen() is available in the core udf library now.. this one is deprecated
     */
    def ID_GEN() : String = {
        UUID.randomUUID().toString;
    }

   /**
       Convert the supplied iso8601 date integers according to these format codes:
      
     Symbol  Meaning                      Presentation  Examples
     ------  -------                      ------------  -------
     G       era                          text          AD
     C       century of era (>=0)         number        20
     Y       year of era (>=0)            year          1996
    
     x       weekyear                     year          1996
     w       week of weekyear             number        27
     e       day of week                  number        2
     E       day of week                  text          Tuesday; Tue
    
     y       year                         year          1996
     D       day of year                  number        189
     M       month of year                month         July; Jul; 07
     d       day of month                 number        10
    
     a       halfday of day               text          PM
     K       hour of halfday (0~11)       number        0
     h       clockhour of halfday (1~12)  number        12
    
     H       hour of day (0~23)           number        0
     k       clockhour of day (1~24)      number        24
     m       minute of hour               number        30
     s       second of minute             number        55
     S       fraction of second           number        978
    
     z       time zone                    text          Pacific Standard Time; PST
     Z       time zone offset/id          zone          -0800; -08:00; America/Los_Angeles
    
     '       escape for text              delimiter
     ''      single quote                 literal    
     
     @param fmtStr: a String specifying the desired format.
     @param yyyymmdds: one or more iso8601 dates... only the last will print
     @return string rep of this date
     NOTE: iso8601DateFmt(String, Int) is available in the core udf library now.. this one is deprecated
     */
    def dateBlock(fmtStr : String, yyyymmdds : Any*): String = {
        val dateTime : DateTime = toDateTime(yyyymmdds.toList.last.asInstanceOf[Int])
        val fmt : DateTimeFormatter  = DateTimeFormat.forPattern(fmtStr);
        val str : String = fmt.print(dateTime);
        str
    }

    /** 
     *  Print the two strings to the log.  The first is some location or context information.  The second
     *  is the event description.
     *  
     *  @param severity  a string describing log severity level ... any {error, warn, info, debug, trace}
     *  @param contextMsg  a string describing the context of why this message is to be logged (or anything else for that matter)
     *  @param eventMsg a string that describes what has actually happened
     *  @param bool a Boolean that is returned as this function's result (to play into the pmml logic as desired)
     *  @return bool
     *
     *  NOTE: logMsg(String,String,String,Boolean) is available in the core udf library now.. this one is deprecated
    */
    def LogMsg(severity : String, contextMsg : String, eventMsg : String, bool : Boolean) : Boolean = {
        if (severity != null && contextMsg != null && eventMsg != null) {
            val sev : String = severity.toLowerCase
            sev match {
                case "error" => logger.error(s"$contextMsg...$eventMsg")
                case "warn" => logger.warn(s"$contextMsg...$eventMsg")
                case "info" => logger.info(s"$contextMsg...$eventMsg")
                case "debug" => logger.debug(s"$contextMsg...$eventMsg")
                case "trace" => logger.trace(s"$contextMsg...$eventMsg")
                case _ => logger.trace(s"$contextMsg...$eventMsg")
            }
        } else {
          logger.error("LogMsg called with bogus arguments")
        }
        bool
    }
    
    /** 
     *  Accept an indefinite number of objects and concatenate their string representations 
     *  @param args : arguments whose string representations will be concatenated   
     *  @return concatenation of args' string representations
     *
     *  NOTE: concat(args : Any*) is available in the core udf library now.. this one is deprecated
     */
    def Concat(args : Any*) : String = {
      val argList : List[Any] = args.toList
      val buffer : StringBuilder = new StringBuilder
      argList.foreach ( arg => if (arg != null) buffer.append(arg.toString) else "" )
      val concatenation : String = buffer.toString
      concatenation
     }
  
    /**
     * matchTermsetCount - Method will analyze a given string for the presence of specified tokens and return the Array of integers where
     *                      each element corresponds to the number of times a token in that position of the context array appears in the 
     *                      inputString.  This method is CASE INSENSITIVE 
     *                      
     *  @param String: String to analyze
     *  @param Array[String]: The list of tokens to compare the inputString to
     *  @return Array[Integer]
     */
    def matchTermsetCount (inputString: String, context: Array[String]): Array[Integer] = {           
      var pos = 0
      var lcIS = inputString.toLowerCase
      var outArray: Array[Integer] = new Array[Integer](context.size) 
      context.foreach(word => {  

        // Tricky thing here... this will not pick up a word if it is a first or the 
        // last word in an inputString. so we need to do some fancy sting checking to
        // handle these 2 cases.
        // General case... chech for a middle word in the inputString   
         outArray(pos) =  (" "+word+" ").toLowerCase.r.findAllIn(lcIS).length
 
        // Check for the last word in an input string
        if (lcIS.endsWith(" "+word.toLowerCase)) outArray(pos) += 1
        // Check for the first word in an input string
        if (lcIS.startsWith(word.toLowerCase+" ")) outArray(pos) += 1
    if (lcIS.equalsIgnoreCase(word)) outArray(pos) += 1
        println("   ==>"+outArray(pos))  
        pos = pos + 1
      })
      outArray
    }
      def getMatchingTokens (inputString: String, context: Array[String]): String = {
      if(inputString==null) return ""
      if(context==null)return ""
      var pos = 0
      var lcIS:String = inputString.toLowerCase
      var outArray:String = ""
      context.foreach(word => {  

        // Tricky thing here... this will not pick up a word if it is a first or the 
        // last word in an inputString. so we need to do some fancy sting checking to
        // handle these 2 cases.
        // General case... chech for a middle word in the inputString   
        var num =  (" "+word+" ").toLowerCase.r.findAllIn(lcIS).length
 
        // Check for the last word in an input string
        if (lcIS.endsWith(" "+word.toLowerCase)) num += 1
        // Check for the first word in an input string
        if (lcIS.startsWith(word.toLowerCase+" ")) num += 1    
    if (lcIS.equalsIgnoreCase(word)) num += 1
        if (num > 0) {
          outArray = outArray + "." + word
        }
      })
      outArray
    }
  
    /**
     * matchTermsetBoolean - Method will analyze a given string for the presence of tokens specified in the context parameters.  If the number of
     *                      present tokens exceeds the degree parameter, return TRUE, else return FALSE.  This method is CASE INSENSITIVE 
     *                        
     *                      
     *  @param String: String to analyze
     *  @param Array[String]: The list of tokens to compare the inputString to
     *  @param Integer: the threshold. 
     *  @return Boolean
     */
    def matchTermsetBoolean (inputString: String, context: Array[String], degree: Integer): Boolean = {
      var total: Int = 0
      if(inputString==null) return false
      if(context==null)return false
      matchTermsetCount(inputString, context).foreach(v => {total = total + v})
      if (total >= degree) true else false
    }
      def Length(str : String)  : Int = {
       val len : Int = if (str != null) str.size else 0
       len
     }
}