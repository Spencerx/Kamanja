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

package com.mycompany.pmml.udfs

import java.util.UUID
import org.joda.time.{LocalDate, DateTime, Duration, LocalTime, LocalDateTime, Months, Years}
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.pmml.udfs._
import com.ligadata.pmml.udfs.Udfs._

/**
  * Sample udfs .. com.mycompany.pmml.udfs.SomeCustomUdfs

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

trait LogTrait {
    val loggerName = this.getClass.getName()
    val logger = LogManager.getLogger(loggerName)
}

object CustomUdfs extends LogTrait {
  
    /**
     * Returns a random UUID string
     */
    def ID_GEN() : String = {
        UUID.randomUUID().toString;
    }
    
    /**
     * FNV fast hashing algorithm in 64 bits.
     * @see http://en.wikipedia.org/wiki/Fowler_Noll_Vo_hash
     */
    def hashKey(key: String): Long = {
      val keyBytes : Array[Byte] = key.getBytes
      val PRIME: Long = 1099511628211L
      var i = 0
      val len = keyBytes.length
      var rv: Long = 0xcbf29ce484222325L
      while (i < len) {
        rv = (rv * PRIME) ^ (keyBytes(i) & 0xff)
        i += 1
      }
      rv & 0xffffffffL
    }


    /**
       Convert the supplied iso8601 date integer according to these format codes:
      
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

     example: dateFmt("yyyy-MMM-dd", 20140401) produces 2014-Apr-01
     
     @param fmtStr: a String specifying the desired format.
     @param yyyymmdds: an Int encoded with iso8601 date...
     @return string rep of this date
    */
    def ISO8601DateFmt(fmtStr : String, yyyymmdds : Int): String = {
        val dateTime : DateTime = toDateTime(yyyymmdds)
        val fmt : DateTimeFormatter  = DateTimeFormat.forPattern(fmtStr);
        val str : String = fmt.print(dateTime);
        str
    }

    /** 
        Answer a String from the time in timestampStr argument formatted according to the format string
        argument presented.

        @param fmtStr - instructions on how to parse the string. @see iso860DateFmt for format info 
        @param timestamp - the number of millisecs since epoch
        @return a Long representing the timestamp as millisecs since the epoch (1970 based)
     */
    def timestampFmt(fmtStr : String, timestamp : Long): String = {
        val dateTime : DateTime = new DateTime(timestamp);
        val fmt : DateTimeFormatter  = DateTimeFormat.forPattern(fmtStr);
        val str : String = fmt.print(dateTime);
        str
    }

    /** 
        Answer the number of millisecs from the epoch for the supplied string that presumably
        has the supplied format.

        @param fmtStr - instructions on how to parse the string. @see iso860DateFmt for format info 
        @param timestampStr - the string to parse
        @return a Long representing the timestamp as millisecs since the epoch (1970 based)
     */
    def timeStampFromStr(fmtStr : String, timestampStr : String): Long = {
        val fmt : DateTimeFormatter  = DateTimeFormat.forPattern(fmtStr);
        val dateTime : DateTime = fmt.parseDateTime(timestampStr);
        val millis : Long = dateTime.getMillis
        millis
    }

    /** 
        Answer the number of millisecs from the epoch for the supplied date portion in the supplied 
        string that presumably has the supplied format.

        @param fmtStr - instructions on how to parse the string. @see iso860DateFmt for format info 
        @param timestampStr - the string to parse
        @return a Long representing the date as millisecs since the epoch (1970 based)
     */
    def dateFromStr(fmtStr : String, timestampStr : String): Long = {
        val fmt : DateTimeFormatter  = DateTimeFormat.forPattern(fmtStr);
        val dateTime : DateTime = fmt.parseDateTime(timestampStr);
        val dt : DateTime = new DateTime(dateTime.getYear, dateTime.getMonthOfYear, dateTime.getDayOfMonth, 0, 0)
        val millis : Long = dt.getMillis
        millis
    }

    def javaEpoch : LocalDateTime = {
        new LocalDateTime(1970, 1, 1, 0, 0)
    }

    /** 
        Answer the number of millisecs from the epoch for the supplied string that presumably
        has the supplied format and represents some wall time.

        @param fmtStr - instructions on how to parse the string. @see iso860DateFmt for format info 
        @param timestampStr - the string to parse
        @return a Long representing the timestamp as millisecs since the epoch (1970 based)
     */
    def timeFromStr(fmtStr : String, timestampStr : String): Long = {
        dateSecondsSinceMidnight(fmtStr, timestampStr)
    }

    /** 
        Answer the number of seconds since midnight for the supplied time (HH:mm:ss:SSS) portion given in the supplied format.

        @param fmtStr - instructions on how to parse the string. @see ISO860DateFmt for format info 
        @param timestampStr - the string to parse
        @return a Long representing the seconds since midnight for the time portion of the supplied timestamp
     */

    def dateSecondsSinceMidnight(fmtStr : String, timestampStr : String): Long = {
        val fmt : DateTimeFormatter  = DateTimeFormat.forPattern(fmtStr);
        val dateTime : DateTime = fmt.parseDateTime(timestampStr);
        val tm : LocalDateTime = new LocalDateTime(1970, 1, 1, dateTime.getHourOfDay, dateTime.getMinuteOfDay, dateTime.getSecondOfDay, dateTime.getMillisOfSecond)
        val seconds : Long = new Duration(javaEpoch.toDateTime.getMillis, tm.toDateTime.getMillis).getStandardSeconds()
        seconds
    }

    /** 
        Answer the number of seconds since midnight for the supplied time (HH:mm:ss:SSS) portion given in the supplied Long

        @param timestamp - the string to parse
        @return a Long representing the seconds since midnight for the time portion of the supplied timestamp
     */

    def dateSecondsSinceMidnight(timestamp : Long): Long = {
        val dateTime : DateTime = new DateTime(timestamp);
        val tm : LocalDateTime = new LocalDateTime(1970, 1, 1, dateTime.getHourOfDay, dateTime.getMinuteOfDay, dateTime.getSecondOfDay, dateTime.getMillisOfSecond)
        val seconds : Long = new Duration(javaEpoch.toDateTime.getMillis, tm.toDateTime.getMillis).getStandardSeconds()
        seconds
    }

    val milliSecondsInSecond : Long = 1000
    val milliSecondsInMinute : Long = 1000 * 60
    val milliSecondsInHour   : Long = 1000 * 60 * 60 
    val milliSecondsInDay    : Long = 1000 * 60 * 60 * 24
    val milliSecondsInWeek   : Long = 1000 * 60 * 60 * 24 * 7

    /**
        Answer the number of milliseconds between the two time expressions (millisecs since Epoch)
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of milliseconds between the timestamps
     */
    def millisecsBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val diff : Long = abs(time1 - time2) + (if (inclusive) 1 else 0)
        diff
    }

    /**
        Answer the number of seconds between the two time expressions (millisecs since Epoch)
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of seconds between the timestamps
     */
    def secondsBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val diff : Long = abs(time1 - time2) + (if (inclusive) 1 else 0)
        val nominalSeconds : Long = diff / milliSecondsInSecond
        val rem : Long = diff % milliSecondsInSecond
        val seconds : Long = nominalSeconds + (if (rem >= (milliSecondsInSecond / 2)) 1 else 0)
        seconds
    }

    /**
        Answer the number of minutes between the two time expressions (millisecs since Epoch).  Partial hours are rounded
        to the nearest integer value.
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of minutes between the timestamps
     */
    def minutesBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val diff : Long = abs(time1 - time2) + (if (inclusive) 1 else 0)
        val nominalMinutes : Long = diff / milliSecondsInMinute
        val rem : Long = diff % milliSecondsInMinute
        val minutes : Long = nominalMinutes + (if (rem >= (milliSecondsInMinute / 2)) 1 else 0)
        minutes
    }

    /**
        Answer the number of hours between the two time expressions (millisecs since Epoch).  Partial hours are rounded
        to the nearest integer value.
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of hours between the timestamps
     */
    def hoursBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val diff : Long = abs(time1 - time2) + (if (inclusive) 1 else 0)
        val nominalHours : Long = diff / milliSecondsInHour
        val rem : Long = diff % milliSecondsInHour
        val hours : Long = nominalHours + (if (rem >= (milliSecondsInHour / 2)) 1 else 0)
        hours
    }

    /**
        Answer the number of days between the two time expressions (millisecs since Epoch).  Partial days are rounded
        to the nearest integer value.
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of days between the timestamps
     */
    def daysBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val diff : Long = abs(time1 - time2) + (if (inclusive) 1 else 0)
        val nominalDays : Long = diff / milliSecondsInDay
        val rem : Long = diff % milliSecondsInDay
        val days : Long = nominalDays + (if (rem >= (milliSecondsInDay / 2)) 1 else 0)
        days
    }

    /**
        Answer the number of weeks between the two time expressions (millisecs since Epoch).  Partial weeks are rounded
        to the nearest integer value. 7 day week assumed.
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of weeks between the timestamps
     */
    def weeksBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val diff : Long = abs(time1 - time2) + (if (inclusive) 1 else 0)
        val nominalWeeks : Long = diff / milliSecondsInWeek
        val rem : Long = diff % milliSecondsInWeek
        val weeks : Long = nominalWeeks + (if (rem >= (milliSecondsInWeek / 2)) 1 else 0)
        weeks
    }

    /**
        Answer the number of months between the two time expressions (millisecs since Epoch).  
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of weeks between the timestamps
     */
    def monthsBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val t1 : Long = if (time1 <= time2) time1 else time2
        val t2 : Long = if (time1 <= time2) time2 else time1
        val dt1 : DateTime = new DateTime(t1)
        val dt2 : DateTime = new DateTime(t2)
        
        val m : Months = Months.monthsBetween(dt1, dt2)
        val months : Long = m.getMonths
        months
    }

    /**
        Answer the number of years between the two time expressions (millisecs since Epoch).  
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of years between the timestamps
     */
    def yearsBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val t1 : Long = if (time1 <= time2) time1 else time2
        val t2 : Long = if (time1 <= time2) time2 else time1
        val dt1 : DateTime = new DateTime(t1)
        val dt2 : DateTime = new DateTime(t2)
        
        val y : Years = Years.yearsBetween(dt1, dt2)
        val years : Long = y.getYears
        years
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
     */
    def LogMsg(severity : String, contextMsg : String, eventMsg : String, bool : Boolean) : Boolean = {
        if (severity != null && contextMsg != null && eventMsg != null) {
            val sev : String = severity.toLowerCase.trim
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
     */
    def concat(args : Any*) : String = {
        val argList : List[Any] = args.toList
        val buffer : StringBuilder = new StringBuilder
        argList.foreach ( arg => if (arg != null) buffer.append(arg.toString) else "" )
        val concatenation : String = buffer.toString
        concatenation
    }
  
    /** 
     *  Accept a parent variable, a child variable and a replacement variable.  Replace all instances of the child  inside the parent with the replacement.  The function will return a string
     *  @param relacewithin : The parent variable (can be any type) within which the replacement will be searched for
     *  @param inWord : The child string which will be searched for within the "replacewithin" variable
     *  @param replacewith : The string with which all instances of the child will be replaced in "replacewithin"
     *  @return outString : string where all "inwords" have been replaced by "replacewith"
     */   
    def replace (replacewithin: Any, inWord: Any, replacewith: Any): String = { 
        val replacewithinA : String = replacewithin.toString
        val inWordA : String = inWord.toString
        val replacewithA : String = replacewith.toString
        val outString : String = replacewithinA.replaceAll(inWordA, replacewithA)
        outString
    }
  
    /** 
     *  Accept a parent variable and a child variable.  Return a boolean value which is true if an instance of the child lies within the parent and false otherwise
     *  @param matchwithin : The parent variable (can be any type) within which the matching variable will be searched for
     *  @param matchwith : The child that will be searched for within "matchwithin"
     *  @return OutBool : Boolean value evaluating whether an instance of "matchwith" exists within "matchwithin"
     */
    def matches (matchwithin: Any, matchwith: Any): Boolean = { 
        val matchwithinA : String = matchwithin.toString
        var matchwithA : String = matchwith.toString
        val outString : String = matchwithinA.replaceAll(matchwithA, matchwithA + matchwithA)
        val outBool : Boolean = if (outString == matchwithinA) false; else true;
        outBool
    }

    /** 
     *  Generate a random Double between 0 and 1
     *  @return ranDouble : random Double between 0 and 1
     */

    def random(): Double = {
      val r : scala.util.Random = scala.util.Random
      val randouble : Double = r.nextDouble
      randouble
    }

    /** 
     *  Accept a number of any type and format it in a specified manner
     *  @param num : The number which is to be formatted
     *  @param formatting : The format which the number is to take.  This should be given in standard form, e.g. %.2f for a 2 decimal place float
     *  @return formattedStr : A string version of the number formatted in the required way
     */
    def formatNumber[T]( num : T, formatting : String) : String = {
      val formattedStr : String = (formatting.format(num)).toString
      formattedStr
    }
  
    /**
     * getTokenizedCounts - Method will analyze a given string for the presence of specified tokens and return the Array of integers where
     *                      each element corresponds to the number of times a token in that position of the context array appears in the 
     *                      inputString.  This method is CASE INSENSITIVE 
     *                      
     *  @param String: String to analyze
     *  @param Array[String]: The list of tokens to compare the inputString to
     *  @return Array[Integer]
     */
    def getTokenizedCounts (inputString: String, context: Array[String]): Array[Integer] = {           
      var pos = 0
      var lcIS = inputString.toLowerCase
      var outArray: Array[Integer] = new Array[Integer](context.size) 
      context.foreach(word => {       
        outArray(pos) =  word.toLowerCase.r.findAllIn(lcIS).length   
        pos = pos + 1
      })
      outArray
    }
  
    /**
     * getTokenizedBoolean - Method will analyze a given string for the presence of tokens specified in the context parameters.  If the number of
     *                      present tokens exceeds the degree parameter, return TRUE, else return FALSE.  This method is CASE INSENSITIVE 
     *                        
     *                      
     *  @param String: String to analyze
     *  @param Array[String]: The list of tokens to compare the inputString to
     *  @param Integer: the threshold. 
     *  @return Boolean
     */
    def getTokenizedBoolean (inputString: String, context: Array[String], degree: Integer): Boolean = {
      var total: Int = 0
      getTokenizedCounts(inputString, context).foreach(v => {total = total + v})
      if (total >= degree) true else false
    }
}
