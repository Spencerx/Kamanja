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

package com.ligadata.pmml.runtime

import scala.collection.mutable._
import scala.language.implicitConversions
import org.joda.time._
import org.joda.time.format._
import com.ligadata.kamanja.metadata._
import com.ligadata.KamanjaBase._
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.Exceptions.StackTrace

object RuntimeGlobalLogger {
    val loggerName = this.getClass.getName()
    val logger = LogManager.getLogger(loggerName)
}

trait LogTrait {
    val logger = RuntimeGlobalLogger.logger
}

/** class Context(mgr : MdMgr) { <== we may want 'mgr' this at runtime ...*/
class Context(val xId : Long, val gCtx : EnvContext) extends LogTrait {
	
	override def equals(another : Any) : Boolean = {
		(this == another)
	}
	
	/** general unique number counter... scope is model instance execution */
	var counter : Int = 0
	/** Answer a unique number for this model execution */
	def Counter() : Int = { 
	  counter += 1 
	  counter 
	}
		
	var xformDict: HashMap[String,DerivedField] = HashMap[String,DerivedField]()
	def xDict : HashMap[String,DerivedField] = xformDict 
	def xDict(dict : HashMap[String,DerivedField]) { xformDict = dict } 

	var dataDict: HashMap[String,DataField] = HashMap[String,DataField]()
	def dDict : HashMap[String,DataField] = dataDict 
	def dDict(dict : HashMap[String,DataField]) { dataDict = dict } 
	
	var rulesetModel : RuleSetModel = new RuleSetModel("someModel", "someFcn", "someAlgo", "false")
	def SetRuleSetModel(model : RuleSetModel) = { rulesetModel = model }
	def GetRuleSetModel : RuleSetModel = { rulesetModel }
	
	var miningSchemaMap : Map[String,MiningField] = Map[String,MiningField]()
	def MiningSchemaMap : Map[String,MiningField] = miningSchemaMap
	def MiningSchemaMap( m : Map[String,MiningField]) { miningSchemaMap = m }

	/**
	 * Answer a value associated with the supplied field when a given field has not been set through
	 * execution of the model.  The missing value for data and derived fields are specified on the
	 * mining field values found in the rule set model's mining schema dictionary.
	 * @param fldName a name of a derived or data field
	 * @return an appropriate DataValue derivative according to the fldName's dataType
	 */
	def MissingValueFor (fldName : String) : DataValue = {
		val returnval : DataValue = rulesetModel.MissingValueFor(this, fldName)
		returnval
	}

	
	/** 
	    Various model identifiers from the model source file header and RuleSetModel:
	 	ApplicationName , FunctionName, PMML, Version, Copyright, Description, ModelName
	 */
	var pmmlModelIdentifiers : HashMap[String,Option[String]] = HashMap[String,Option[String]]()

	/** Answer the Application name (from the header of the PMML) */
	def ModelAppName : String = {
		val name : Option[String] = pmmlModelIdentifiers.apply("Application")
		name match {
		  case Some(name) => name
		  case _ => "No application name supplied"
		}
	}
	
	/** Answer the ModelVersion (from the header of the PMML) */
	def ModelVersion : String = {
		val version : Option[String] = pmmlModelIdentifiers.apply("Version")
		version match {
		  case Some(version) => version
		  case _ => "No version supplied"
		}
	}
	
	/** Answer the ModelDescription (from the header of the PMML) */
	def ModelDescription : String = {
		val descr : Option[String] = pmmlModelIdentifiers.apply("Description")
		descr match {
		  case Some(descr) => descr
		  case _ => "No description supplied"
		}
	}
	
	/** Answer the Source file supplied (from the header of the PMML) 
	 *  @deprecated("Source is typically sent as a stream of characters now", "2015-Jun-03")
	 */
	def ModelSourceFile : String = {
		val srcfile : Option[String] = pmmlModelIdentifiers.apply("PMML")
		srcfile match {
		  case Some(srcfile) => srcfile
		  case _ => "No source file collected..."
		}
	}
	
	/** 
	 *  Answer the DataValue derivative associated with the supplied field name - either naming a
	 *  DataDictionary or TransactionDictionary field.  Should the field not be set due to failure to 
	 *  initialize the DataField or execute the code that sets the DerivedField, the missing value 
	 *  ascribed to the mining field for this field is accessed and its missingValueReplacement is 
	 *  returned.
	 *  
	 *  @param fldName the name of the field whose value is sought
	 *  @return a DataValue derivative appropriate for the fldName's dataType   
	 */ 
	def valueFor(fldName : String) : DataValue = {

		val returnvalue : DataValue = if (dDict.contains(fldName)) {
			val fld : DataField = dDict.apply(fldName)
			if (fld.ValueSet) {
				fld.Value
			} else {
				MissingValueFor(fldName)
			}
		} else {
			val rvalue : DataValue = if (xDict.contains(fldName)) {
				 /** lazy evaluation done for transaction dict derived fields */
				var fld : DerivedField = xDict.apply(fldName)
				if (fld.ValueSet) {
					fld.Value 
				} else {
					val rVal : DataValue = fld.execute(this)
					fld.Value(rVal)
					rVal
				} 
			} else {
				MissingValueFor (fldName)
			}
			rvalue
		}
		returnvalue
	}
	
	
	/** 
	 *  Determine if the DataField or DerivedField has been set  
	 *  
	 *  @param fldName : the name of the DataField or DerivedField of interest
	 *  @return true if the field has a valid value.
	 */ 
	def valueSetFor(fldName : String) : Boolean = {

		val isSet : Boolean = if (dDict.contains(fldName)) {
			val fld : DataField = dDict.apply(fldName)
			fld.ValueSet
		} else {
			if (xDict.contains(fldName)) {
				val fld : DerivedField = xDict.apply(fldName)
				fld.ValueSet 
			} else {
				false
			}
		}
		isSet
	}
	
	/** 
	 *  Explicitly update the supplied field name with the supplied DataValue.
	 *  @param fldName name of field to update ... could be in either data or transaction dictionary
	 *  @param a DataValue presumably appropriately matched to the field's dataType
	 *  @return whether the field was set
	 */ 
	def valuePut(fldName : String, value : DataValue) : Boolean = {
		var set : Boolean = false
		if (dDict.contains(fldName)) {
			var fld : DataField = dDict.apply(fldName)
			fld.Value(value)
			set = fld.ValueSet
		} else {
			if (xDict.contains(fldName)) {
				 /** lazy evaluation done for transaction dict derived fields */
				var fld : DerivedField = xDict.apply(fldName)
				fld.Value(value)
				set = fld.ValueSet
			} 
		}
		set
	}

	/** 
	 *  Explicitly update the supplied field name with the supplied DataValue.
	 *  @param fldName name of field to update ... could be in either data or transaction dictionary
	 *  @param a String value
	 *  @return whether the field was set
	 */ 
	def valuePut(fldName : String, value : String) : Boolean = { 
		valuePut(fldName, new StringDataValue(value))
	}

	/** 
	 *  Explicitly update the supplied field name with the supplied DataValue.
	 *  @param fldName name of field to update ... could be in either data or transaction dictionary
	 *  @param an Int value
	 *  @return whether the field was set
	 */ 
	def valuePut(fldName : String, value : Int) : Boolean = { 
		valuePut(fldName, new IntDataValue(value))
	}

	/** 
	 *  Explicitly update the supplied field name with the supplied DataValue.
	 *  @param fldName name of field to update ... could be in either data or transaction dictionary
	 *  @param a Long value
	 *  @return whether the field was set
	 */ 
	def valuePut(fldName : String, value : Long) : Boolean = { 
		valuePut(fldName, new LongDataValue(value))
	}

	/** 
	 *  Explicitly update the supplied field name with the supplied DataValue.
	 *  @param fldName name of field to update ... could be in either data or transaction dictionary
	 *  @param a Double value
	 *  @return whether the field was set
	 */ 
	def valuePut(fldName : String, value : Double) : Boolean = { 
		valuePut(fldName, new DoubleDataValue(value))
	}

	/** 
	 *  Explicitly update the supplied field name with the supplied DataValue.
	 *  @param fldName name of field to update ... could be in either data or transaction dictionary
	 *  @param a Float value
	 *  @return whether the field was set
	 */ 
	def valuePut(fldName : String, value : Float) : Boolean = { 
		valuePut(fldName, new FloatDataValue(value))
	}

	/** 
	 *  Explicitly update the supplied field name with the supplied DataValue.
	 *  @param fldName name of field to update ... could be in either data or transaction dictionary
	 *  @param any dataType value
	 *  @return whether the field was set
	 */ 
	def valuePut(fldName : String, value : Any) : Boolean = { 
		valuePut(fldName, new AnyDataValue(value))
	}
	
	/** 
	 *  Explicitly update the supplied field name with the supplied DataValue.
	 *  @param fldName name of field to update ... could be in either data or transaction dictionary
	 *  @param a Boolean value
	 *  @return whether the field was set
	 */ 
	def valuePut(fldName : String, value : Boolean) : Boolean = { 
		valuePut(fldName, new BooleanDataValue(value))
	}
	
	
	/** 
	 *  Explicitly increment the supplied field's value by the increment supplied. The field
	 *  can be an integer, Long, double or float.
	 *  @param fldName the name of the scalar field to update
	 *  @param incrAmt the amount (integers only at this time) to increment or decrement (if negative)
	 *  @param whether the field was incremented.
	 */
	def valueIncr(fldName : String, incrAmt : Int) : Boolean = {
		var set : Boolean = false
		if (dDict.contains(fldName)) {
			var fld : DataField = dDict.apply(fldName)
			set = fld.Value match {
			  case i: IntDataValue =>  {
				  val value : Int = fld.asInstanceOf[IntDataValue].Value + incrAmt
				  fld.Value(new IntDataValue(value))
				  true
			  }
			  case l: LongDataValue =>   {
				  val value : Long = fld.asInstanceOf[LongDataValue].Value + incrAmt
				  fld.Value(new LongDataValue(value))
				  true
			  } 
			  case d: DoubleDataValue =>    {
				  val value : Double = fld.asInstanceOf[DoubleDataValue].Value + incrAmt
				  fld.Value(new DoubleDataValue(value))
				  true
			  }
			  case f: FloatDataValue =>    {
				  val value : Float = fld.asInstanceOf[FloatDataValue].Value + incrAmt
				  fld.Value(new FloatDataValue(value))
				  true
			  }
			  case _ => false
			}
		} else {
			if (xDict.contains(fldName)) {
				var fld : DerivedField = xDict.apply(fldName)
				set = fld.Value match {
				  case i: IntDataValue =>  {
					  val value : Int = fld.asInstanceOf[IntDataValue].Value + incrAmt
					  fld.Value(new IntDataValue(value))
					  true
				  }
				  case l: LongDataValue =>   {
					  val value : Long = fld.asInstanceOf[LongDataValue].Value + incrAmt
					  fld.Value(new LongDataValue(value))
					  true
				  } 
				  case d: DoubleDataValue =>    {
					  val value : Double = fld.asInstanceOf[DoubleDataValue].Value + incrAmt
					  fld.Value(new DoubleDataValue(value))
					  true
				  }
				  case f: FloatDataValue =>    {
					  val value : Float = fld.asInstanceOf[FloatDataValue].Value + incrAmt
					  fld.Value(new FloatDataValue(value))
					  true
				  }
				  case _ => false
				}
			}
		}
		set
	}
	
	/** Answer whether a field with the supplied name exists in the transformation dictionary
	 *  @param fldName the name of the field sought
	 *  @param true if it exists
	 */
	def isFieldInTransformationDict(fldName : String) : Boolean = {
		xDict.contains(fldName)
	}
	
	/** Answer whether a field with the supplied name exists in the data dictionary
	 *  @param fldName the name of the field sought
	 *  @param true if it exists
	 */
	def isFieldInDataDict(fldName : String) : Boolean = {
		dDict.contains(fldName)
	}
	
	
}


/** 
 *  DataValue is a root object that contains behavior common to all derived classes.  DataValue instances
 *  are used to store the data values inside of the two dictionaries, dataDict and xFormDict in the Context.
 */
class DataValue (val dataType : String) {
	override def toString : String = "a DataValue"
}

/** 
 *  The DataValue object offers type sensitive compare, default value determination based upon dataType, and
 *  object factory services based upon type and initialization string.
 */
object DataValue {
	
	/** Compare the supplied data types.  The scalars, time, date, dateTime, Boolean and String are supported 
	 *	@param o1 some instance
	 * 	@param o2 some instance
	 *  @return true if equivalent else false (or if a type not supported)  
	 */
	def compare(o1 : Any, o2 : Any) : Boolean = {
		try {
			o1 match {
				case d:Double => o1.asInstanceOf[Double] == o1.asInstanceOf[Double]
				case f:Float => o1.asInstanceOf[Float] == o1.asInstanceOf[Float]
				case l:Long => o1.asInstanceOf[Long] == o1.asInstanceOf[Long]
				case i:Int => o1.asInstanceOf[Int] == o1.asInstanceOf[Int]
				case b:Boolean => o1.asInstanceOf[Boolean] == o1.asInstanceOf[Boolean]
				//case ldt:LocalDate => o1.asInstanceOf[Long] == o1.asInstanceOf[Long]
				//case ltm:LocalTime => o1.asInstanceOf[Long] == o1.asInstanceOf[Long]
				//case dtm:DateTime => o1.asInstanceOf[Long] == o1.asInstanceOf[Long]
				case s:String => o1.asInstanceOf[String] == o1.asInstanceOf[String]
				case _ => {
					println("Unknown type supplied for result comparison")
					false
				}
			}
		} catch {
			case _ : Throwable => false 
		}
	} 

	/** Answer the default value appropriate for each of the supported data types... 
	 *  any {string,integer,long,float,double,date,datetime,time}.  The defaults respectively
	 *  are {NotSet,  0    , 0  , 0   ,  0   , now,     now, now}
	 *  @param dataType the attribute from some PMML field or constant
	 *  @return the appropriately typed DataValue based upon the supplied dataType.  For any other
	 *  type supplied here, the default value is an AnyDataValue with value = List[String]("NotSet")
	 */
	def defaultValue(dataType : String) : DataValue = {
		val default : DataValue = dataType.toLowerCase match {
		  case "string" => new StringDataValue("NotSet")
		  case "integer" =>  new IntDataValue(0)
		  case "long" =>  new LongDataValue(0)
		  case "float" =>  new FloatDataValue(0) 
		  case "double" =>  new DoubleDataValue(0) 
		  case "date" =>  new LongDataValue(new DateTime().getMillis) 
		  case "datetime" =>  new LongDataValue(new DateTime().getMillis)  
		  case "time" =>  {
			  val tm : LocalTime = LocalTime.now()
			  new LongDataValue(new DateTime(0,0,0,tm.getHourOfDay(),tm.getMinuteOfHour(),tm.getSecondOfMinute()).getMillis()) 
		  }
		  
		  case _ => new AnyDataValue(List[String]("NotSet"))
		}
		default
	}
	
	/**
	 * Factory method for the builtin PMML types.  
	 * 
	 * Note: The datedayssince[NNNN], timeseconds, and datetimesecondssince[NNNN] are not supported. 
	 * The dataTypes date, time, and datetime are answered as millisecs since 
	 * the java epoch (1970).  When a use case is presented for these unsupported 
	 * time/date dataTypes, perhaps one or more of them will be implemented.
	 * 
	 * Principal use is for creating default values expressed in the mining fields and apply elements.
	 * 
	 * @param dataType a string rep describing the type
	 * @param stringRep the string to interpret
	 * @param a DataValue of the proper sort that contains the value (assuming success).  Unknown types are treated
	 * 		as string data values.
	 *   
	 */
	def make(dataType : String, stringRep : String) : DataValue = {
		val dataValue : DataValue = if (dataType != null && stringRep.size > 0 && dataType != null && stringRep.size > 0) {
			dataType.toLowerCase match {
			    case "string" => StringDataValue.fromString(stringRep)
			    case "int" => IntDataValue.fromString(stringRep)
			    case "integer" => IntDataValue.fromString(stringRep) 
			    case "long" => {
			    	LongDataValue.fromString(stringRep)			    	
			    }
			    case "float" => FloatDataValue.fromString(stringRep)
			    case "double" => DoubleDataValue.fromString(stringRep)
			    case "real" => DoubleDataValue.fromString(stringRep) 
			    case "boolean" => BooleanDataValue.fromString(stringRep)
			    case "date" => new LongDataValue(DateTimeHelpers.dateFromString(stringRep)) 
			    case "time" => new LongDataValue(DateTimeHelpers.timeFromString(stringRep)) 
			    case "datetime" => new LongDataValue(DateTimeHelpers.timeStampFromString(stringRep))
			    /** FIXME: support these as necessary... */
			    case "datedayssince[0]" => new LongDataValue(0) 
			    case "datedayssince[1960]" => new LongDataValue(0)  
			    case "datedayssince[1970]" => new LongDataValue(0)  
			    case "datedayssince[1980]" => new LongDataValue(0)  
			    case "timeseconds" => new LongDataValue(0) 
			    case "datetimesecondssince[0]" => new LongDataValue(0)  
			    case "datetimesecondssince[1960]" => new LongDataValue(0)  
			    case "datetimesecondssince[1970]" => new LongDataValue(0)  
			    case "datetimesecondssince[1980]" => new LongDataValue(0) 
			    
				case _ => { /** Any */ 
					new AnyDataValue(stringRep)
				}
			}
		} else {
			StringDataValue.fromString("null")
		}
		dataValue
	}

	/**
	 * Factory method for missingValueReplacement values and their ilk that gave a !compile: instruction.  
	 * For example, given this mining field in the PMML being parsed...
	 * 
	 * 		<MiningField name="outPatientClaimCostsByDate" 
	 *   				usageType="supplementary" 
	 *   				missingValueReplacement="!compile:Map[Int,Double]()"
	 *       />
	 *  
	 *  The !compile: prefix tells the PMML compiler to accept the subsequent text as some NATIVE scala initialization
	 *  code.  In this case the code that will be inserted into the PMML compiler output is "Map[Int,Double]()" 
	 *  which will instantiate an empty map with Int key and Double value.  This runtime method services this
	 *  object created inline in the argument list of the generated DataValue.make expression.  For example, if this
	 *  is a missingValueReplacement for a minining field, the code generated by the compiler currently would look
	 *  like this ("Map[Int,Double]()" in line):
	 *  
	 *       ruleSetModel.AddMiningField("outPatientClaimCostsByDate"
	 *       							, new MiningField("outPatientClaimCostsByDate"
	 *              									,"supplementary","",0.0,"",0.0,0.0
	 *                   								,DataValue.make(Map[Int,Double]())
	 *                       							,""
	 *                          						,"")
	 *                                	)
	 *                                 
	 *  This snippet simply adds the mining field to the ruleset's mining dictionary.
	 * 
	 * @param anyObj some instantiated object that was compiled inline as an argument expression to this make method.
	 * @return an AnyDataValue that accepts the instantiated object and makes it the value of the returned AnyDataValue 
	 *   
	 */
	def make(anyObj : Any) : DataValue = {
		new AnyDataValue(anyObj)
	}

}


class IntDataValue (var value : Int) extends DataValue("Int") {
	def Value : Int = value
	def Value(valu : Int) { value = valu }
	override def toString : String = value.toString
}

/** String => Int coercion is primitive to be kind */
object IntDataValue {
  implicit def asInt(s: String) : Int = augmentString(s).toInt
  def fromString1(i: Int) = i
  def fromString(s : String) : IntDataValue = new IntDataValue(fromString1(s))

  def main(args: Array[String]) {
	  val n: IntDataValue = fromString("1")
  }
}

class LongDataValue (var value : Long) extends DataValue("Long") {
	def Value : Long = value
	def Value(valu : Long) { value = valu }
	override def toString : String = value.toString
}

/** String => Long coercion is primitive to be kind */
object LongDataValue {
	implicit def asLong(s: String) : Long = augmentString(s).toLong
	def fromString1(l: Long) = l
	def fromString(s : String) : LongDataValue = {
    	val strtoParse : String = if (s.endsWith("L")) {
    		s.split('L').head
    	} else {
    		if (s.endsWith("l")) {
    			s.split('l').head
    		} else {
    			s
    		}
    	}
	  
		new LongDataValue(fromString1(strtoParse))
	}
	def main(args: Array[String]) {
		val n: LongDataValue = fromString("1")
	}
}

class AnyDataValue(var value : Any) extends DataValue("Any") {
	def Value : Any = value
	def Value(valu : Any) { value = valu }  
	override def toString : String = ValueString(value)

	def ValueString(v: Any): String = {
		if (v == null) {
			return "null"		
		}
	    if (v.isInstanceOf[scala.collection.immutable.Set[_]]) {
	      return v.asInstanceOf[scala.collection.immutable.Set[_]].mkString(",")
	    }
	    if (v.isInstanceOf[scala.collection.mutable.Set[_]]) {
	      return v.asInstanceOf[scala.collection.mutable.Set[_]].mkString(",")
	    }
	    if (v.isInstanceOf[List[_]]) {
	      return v.asInstanceOf[List[_]].mkString(",")
	    }
	    if (v.isInstanceOf[Array[_]]) {
	      return v.asInstanceOf[Array[_]].mkString(",")
	    }
	    v.toString
	}
}

object AnyDataValue {
	def fromString(s: String) = new AnyDataValue(s)
}

class StringDataValue(var value : String) extends DataValue("String") {
	def Value : String = value
	def Value(valu : String) { value = valu }  
	override def toString : String = value
}

object StringDataValue {
	def fromString(s: String) = new StringDataValue(s)
}

class FloatDataValue(var value : Float) extends DataValue("Float") {
	def Value : Float = value
	def Value(valu : Float) { value = valu }  
	override def toString : String = value.toString
}

/** String => Float coercion is primitive to be kind */
object FloatDataValue {
	private implicit def asFloat(s: String) : Float = augmentString(s).toFloat
	private def fromString1(l: Float) = l
	def fromString(s : String) : FloatDataValue = new FloatDataValue(fromString1(s))
	def main(args: Array[String]) {
		val n: FloatDataValue = fromString("1")
	}
}

class DoubleDataValue(var value : Double) extends DataValue("Double") {
	def Value : Double = value
	def Value(valu : Double) { value = valu }  
	override def toString : String = value.toString
}

/** String => Double coercion is primitive to be kind */
object DoubleDataValue {
	private implicit def asDouble(s: String) : Double = augmentString(s).toDouble
	private def fromString1(l: Double) = l
	def fromString(s : String) : DoubleDataValue = new DoubleDataValue(fromString1(s))
	def main(args: Array[String]) {
		val n: DoubleDataValue = fromString("1")
	}
}

/** @deprecated("time and date now represented as millisecs since epoch as Long","2015-Jun-03") */
class DateDataValue(var value : LocalDate) extends DataValue("LocalDate") {
	def Value : LocalDate = value
	def Value(valu : LocalDate) { value = valu }  
	override def toString : String = {
		val locDate : org.joda.time.LocalDate = value.asInstanceOf[DateDataValue].Value
		val mo = locDate.getMonthOfYear()
		val day = locDate.getDayOfMonth()
		val yr = locDate.getYear()
		val formatter : org.joda.time.format.DateTimeFormatter = org.joda.time.format.DateTimeFormat.forPattern("yyyy-MM-dd");
		val formattedDate : String  = formatter.print(locDate);
		s"${'"'}$formattedDate${'"'}"
	}
}

/** @deprecated("time and date now represented as millisecs since epoch as Long","2015-Jun-03") */
object DateDataValue {
	def fromString(dateTimeStr : String) : DateDataValue =  {
		val dateTime : org.joda.time.LocalDate = LocalDate.parse(dateTimeStr)
		new DateDataValue(dateTime)
	}
}

/** @deprecated("time and date now represented as millisecs since epoch as Long","2015-Jun-03") */
class DateTimeDataValue(var value : DateTime) extends DataValue("DateTime") {
	def Value : DateTime = value
	def Value(valu : DateTime) { value = valu }  
	override def toString : String = {
		val locDate : org.joda.time.DateTime = value.asInstanceOf[DateTimeDataValue].Value
		val mo = locDate.getMonthOfYear()
		val day = locDate.getDayOfMonth()
		val yr = locDate.getYear()
		val formatter : org.joda.time.format.DateTimeFormatter = org.joda.time.format.DateTimeFormat.forPattern("yyyy-MM-dd");
		val formattedDate : String  = formatter.print(locDate);
		s"${'"'}$formattedDate${'"'}"
	}
}

/** @deprecated("time and date now represented as millisecs since epoch as Long","2015-Jun-03") */
object DateTimeDataValue {
	def fromString(dateTimeStr : String)  : DateTimeDataValue = {
		val dateTime : org.joda.time.DateTime = DateTime.parse(dateTimeStr)
		new DateTimeDataValue(dateTime)
	}
}

/** @deprecated("time and date now represented as millisecs since epoch as Long","2015-Jun-03") */
class TimeDataValue(var value : LocalTime) extends DataValue("LocalTime") {
	def Value : LocalTime = value
	def Value(valu : LocalTime) { value = valu }  
	override def toString : String =  {
		val locTime : org.joda.time.LocalTime = value.asInstanceOf[TimeDataValue].Value
		val hrs = locTime.getHourOfDay()
		val mins = locTime.getMinuteOfHour()
		val secs = locTime.getSecondOfMinute()
		val ms = locTime.getMillisOfSecond()
		val formatter : org.joda.time.format.DateTimeFormatter = org.joda.time.format.DateTimeFormat.forPattern("k:m:s:S");
		val formattedTime : String  = formatter.print(locTime);
		s"${'"'}$formattedTime${'"'}"
	}
}

/** @deprecated("time and date now represented as millisecs since epoch as Long","2015-Jun-03") */
object TimeDataValue {
	def fromString(locTimeStr : String) : TimeDataValue = {
		var locTime : org.joda.time.LocalTime = LocalTime.parse(locTimeStr)
		new TimeDataValue(locTime)
	}
}

class BooleanDataValue(var value : Boolean) extends DataValue("Boolean") {
	def Value : Boolean = value
	def Value(valu : Boolean) { value = valu }  
	override def toString : String = {
		if (value) "true" else "false"
	}
}

object BooleanDataValue {
	def fromString(boolStr : String) : BooleanDataValue = {
		val bStr = boolStr.trim.toLowerCase()
		var boolObj : BooleanDataValue = bStr match {
			case "true" | "t" | "1" => { 
				new BooleanDataValue(true)
			}
			case "false" | "f" | "0" => {
				new BooleanDataValue(false)
			}
			case _ => new BooleanDataValue(false)
		}
		boolObj
	}
}


/** 
 *  The DataField is the runtime representation of the DataFields found in the DataDictionary
 *  in the PMML file.   
 */
class DataField(val name : String, val dataType : String, values : ArrayBuffer[(String,String)], leftMargin : String, rightMargin : String, closure : String) {
	
	var valueHasBeenSet : Boolean = false
	var valueRef : DataValue = null
	def Value : DataValue = { if (valueRef == null) DataValue.defaultValue(dataType) else valueRef }
	def Value(valu : DataValue) { 
	  valueRef = valu 
	  valueHasBeenSet = true
	}
	
	def ValueSet : Boolean = { valueHasBeenSet }
}

/** 
 *  The DerivedField is the runtime representation of the DataFields found in the TransformationDictionary
 *  in the PMML file.  The top level function of the DervivedField, when it executes sets the valueRef.
 *  The DerivedField is the base class for all generated DerivedField_<fieldName> classes in the PMML generation.
 */
class DerivedField(val name : String, val dataType : String, values : ArrayBuffer[(String,String)], leftMargin : String, rightMargin : String, closure : String) {
	var valueHasBeenSet : Boolean = false
	var valueRef : DataValue = null
	def Value : DataValue =  { if (valueRef == null) DataValue.defaultValue(dataType) else valueRef }
	def Value(valu : DataValue) { 
	  	valueRef = valu 
	  	valueHasBeenSet = true
	}
	
	def ValueSet : Boolean = { valueHasBeenSet }
	
	/** generated model will override this with the apply function implementation */
	def execute(ctx : Context) : DataValue = { new StringDataValue("bogus value") }
}

/** 
 *  The MiningField indicates the actual fields that the model uses in its Model.  It indicates which of the fields are 
 *  designated as model output via the opType field... any{"active","predicted","supplementary","group","order","frequencyWeight","analysisWeight"}
 *  
 *
 */
class MiningField(val name : String
				, val usageType : String
				, val opType : String
				, val importance : Double
				, val outliers : String
				, val lowValue : Double
				, val highValue : Double
				, val missingValueReplacement : DataValue
				, val missingValueTreatment : String
				, val invalidValueTreatment : String) {
	
}

class SimpleRule(val id : String
			    , val score : String
			    , var recordCount : Double
			    , var nbCorrect : Double
			    , var confidence : Double
			    , var weight : Double)  {
  
	var scoreDistributions : ArrayBuffer[ScoreDistribution] = ArrayBuffer[ScoreDistribution]()
	
	def Name(ctx : Context) : String = { "RuleId_" + id + ctx.Counter() }

	def addScoreDistribution(sd : ScoreDistribution) {
		scoreDistributions += sd
	}

	def RecordCount(rCount : Double) {
		recordCount = rCount
	}

	def CorrectCount(cCount : Double) {
		nbCorrect = cCount
	}

	def Confidence(conf : Double) {
		confidence = conf
	}

	def Weight(wt : Double) {
		weight = wt
	}

	/** Subclasses will implement this one */
	def execute(ctx : Context, defaultScore : StringDataValue) : String = { "" }
}

class ScoreDistribution(var value : String
			    , var recordCount : Double
			    , var confidence : Double
			    , var probability : Double) {

	def RecordCount(rc : Double) { recordCount = rc }
	def Confidence(conf : Double) { confidence = conf }
	def Probability(prob : Double) { probability = prob }

}

class RuleSelectionMethod(criterion : String) {

}


class RuleSetModel(val modelName : String
				, val functionName : String
				, val algorithmName : String
				, val isScorable : String) extends LogTrait {
  
	var miningSchemaMap : Map[String,MiningField] = Map[String,MiningField]()
	var ruleSet : ArrayBuffer[SimpleRule] = ArrayBuffer[SimpleRule]()
	var ruleSelectionMethods : ArrayBuffer[RuleSelectionMethod] = ArrayBuffer[RuleSelectionMethod]()
	var defaultScore : StringDataValue = null /** this attribute picked up from RuleSet */
	
	def AddMiningField(fieldName : String, fieldDetails: MiningField) {
		miningSchemaMap(fieldName) = fieldDetails
	}

	def AddRule(r : SimpleRule) {
		ruleSet += r
	}
	
	def AddRuleSelectionMethod(method : RuleSelectionMethod) {
		ruleSelectionMethods += method
	}

	def DefaultScore(dflt : StringDataValue) { defaultScore = dflt }
	def DefaultScore() : StringDataValue = { if (defaultScore != null) defaultScore else new StringDataValue("0") }
	

	def MiningSchemaMap() : Map[String,MiningField] = { miningSchemaMap } 
	def RuleSet() : ArrayBuffer[SimpleRule] = { ruleSet } 
	def RuleSelectionMethods() : ArrayBuffer[RuleSelectionMethod] = { ruleSelectionMethods } 
	
	
	def OutputFieldNames() : Array[String] = {
		val outputs = miningSchemaMap.filter((m) => m._2.opType == "predicted" || m._2.opType == "supplementary").keys.toArray
		outputs
	}
	
	def OutputFieldValues(ctx : Context) : Array[Any] = {
		OutputFieldNames().map(outfldName => ctx.valueFor(outfldName))
	}
	
	/**	
		Answer a value appropriate for the supplied mining field name.  Should one not exist for this name, answer
		the default value appropriate for this field's type (assuming it in fact is a legitimate data or derived field.
		@param ctx the runtime context for the model executing
		@param field the name of the mining field whose missing value is to be returned.
		@return a DataValue derivative appropriate for this field's dataType.
	 */
	
	def MissingValueFor (ctx : Context, field : String) : DataValue = {
		val value : DataValue = if (miningSchemaMap.contains(field)) {
			miningSchemaMap.apply(field).missingValueReplacement
		} else {
			logger.warn(s"Supplied field name $field is not found in the mining schema")
			logger.warn(s"To reach MissingValueFor, it also means that the field has not been set by execution.  ")
			logger.warn(s"It would behoove the model developer to add a mining field with missing value replacement for $field")
			logger.warn(s"if this is a continuing possibility.  If not that, consider remedies to the model.")
			 /** no mining field defined for this one... try to find the field to determine its type */		 
			val defVal : DataValue = if (ctx.isFieldInTransformationDict(field)) {
				val xFld : DerivedField = ctx.xDict.apply(field)
				xFld.Value
			} else {
				val dVal : DataValue = if (ctx.isFieldInDataDict(field)) {
					val dFld : DataField = ctx.dDict.apply(field)
					dFld.Value
				} else {
					new DataValue(s"bad mining schema field key $field")
				}
				dVal
			}
			defVal		 
		}
		value
	}
	
	/** 
	 *  FIXME: The current mechanism only supports "FirstHit".  For models with selection methods
	 *  "weightedSum" and/or "weightedMax" with no defaultScore, a more elaborate mechanism is needed
	 *  that uses the model developer's rule confidence, weight, and score distribution values to 
	 *  choose among the rules that completed
	 */
	def MakePrediction(ctx : Context, results : ArrayBuffer[String]) = {
		val predictedField : String = miningSchemaMap.filter((m) => m._2.usageType == "predicted").keys.toArray.apply(0)
		
		/**
		 * FIXME: nested local transformation dictionary support
		 */
		if (ctx.isFieldInDataDict(predictedField)) {
			if (results.length > 0) {
				val prediction : String = results.apply(0)	
				var value : StringDataValue = new StringDataValue(prediction)
				value.Value(prediction)
				ctx.dataDict.apply(predictedField).Value(value) 
			} else {			
				ctx.dataDict.apply(predictedField).Value(DefaultScore()) 			
			}
		} else {
			if (ctx.isFieldInTransformationDict(predictedField)) {
				val prediction : String = results.apply(0)	
				var value : StringDataValue = new StringDataValue(prediction)
				value.Value(prediction)
				ctx.xDict.apply(predictedField).Value(value) 
			} else {
				logger.error(s"fantastic.. the predicted field found in the mining schema does not exist in either dictionary")		
				ctx.dataDict.apply(predictedField).Value(DefaultScore()) 			
			}
		} 
	}

	/** Subclasses will implement this one */
	def execute(ctx : Context) { }
}

/** 
 *  DateTimeHelpers contains several builtin date & time parse functions.  Pre-built patterns of the 
 *  common formats (hopefully) for timestamps, dates, and times are used to determine the number 
 *  of millisecs since the epoch (all date and time types are represented in this fashion).  Those 
 *  values then can be converted to other useful formats using the core udf library in the PmmlUdfs object.
 */
object DateTimeHelpers extends LogTrait {

    val timeStampPatterns : Array[String] = Array[String]("yyyy-MM-dd HH:mm:ss:SSS"
    													, "yyyy-MM-dd HH:mm:ss"
    													, "MM/dd/yy HH:mm:ss"
    													, "dd-MM-yyyy HH:mm:ss"
    													, "dd-MM-yyyy HH:mm:ss:SSS"
    													, "dd-MMM-yyyy HH:mm:ss"
    													, "dd-MM-yyyy HH:mm:ss:SSS"
    													, "dd-MM-yyyy h:mm:ss aa"
    													)
    val timeStampParsers : Array[DateTimeParser] = timeStampPatterns.map (fmt => {
    	DateTimeFormat.forPattern(fmt).getParser()
    })
    
    val tsformatter : DateTimeFormatter = new DateTimeFormatterBuilder().append( null, timeStampParsers ).toFormatter();

    /** 
        Answer the number of millisecs from the epoch for the supplied string that is in one of 
        the following builtin formats:
        
        """
        	 Pattern						Example
			 "yyyy-MM-dd HH:mm:ss:SSS"		2015-02-28 14:02:31:222
			 "yyyy-MM-dd HH:mm:ss"			2015-02-28 14:02:31
			 "MM/dd/yy HH:mm:ss"			04/15/15 23:59:59
			 "dd-MM-yyyy HH:mm:ss"			04/15/2015 23:59:59
			 "dd-MM-yyyy HH:mm:ss:SSS"		04/15/15 23:59:59:999
			 "dd-MMM-yyyy HH:mm:ss"			15-Apr-2015 23:59:59
			 "dd-MM-yyyy HH:mm:ss:SSS"		15-04-2015 23:59:59:999
        """
        
        @param fmtStr - instructions on how to parse the string. @see iso860DateFmt for format info 
        @param timestampStr - the string to parse
        @return a Long representing the timestamp as millisecs since the epoch (1970 based)
     */
    def timeStampFromString(timestampStr : String): Long = {
		val millis : Long = if (timestampStr != null) {
		    try {
		        val dateTime : DateTime = tsformatter.parseDateTime(timestampStr)
		        val msecs : Long = dateTime.getMillis
		        msecs
		    } catch {
			    case iae:IllegalArgumentException => {
            
            logger.error(s"Unable to parse '$timestampStr' with any of the patterns - '${timeStampPatterns.toString}'")
            
			    	0
			    }
		    }
		} else {
			0
		}
        millis
    }
    
    val datePatterns : Array[String] = Array[String]("yyyy-MM-dd"
													, "yyyy-MMM-dd"
													, "MM/dd/yy"
													, "MMM/dd/yy"
													, "dd-MM-yyyy"
													, "dd-MMM-yyyy"
													)
    val dateParsers : Array[DateTimeParser] = datePatterns.map (fmt => {
    	DateTimeFormat.forPattern(fmt).getParser()
    })

	val dtformatter : DateTimeFormatter = new DateTimeFormatterBuilder().append( null, dateParsers ).toFormatter();
    
    /** 
        Answer the number of millisecs from the epoch for the supplied date string that is in one of 
        the following builtin formats:
        
        """
        	 Pattern						Example
        	 yyyy-MM-dd						2015-04-15
			 yyyy-MMM-dd					2015-Apr-15
			 MM/dd/yy						04/15/15
			 MMM-dd-yy						Apr-15-15
			 dd-MM-yyyy						15-04-2015
			 dd-MMM-yyyy					15-Apr-2015
        """
        
        @param fmtStr - instructions on how to parse the string. @see iso860DateFmt for format info 
        @param dateStr - the string to parse
        @return a Long representing the timestamp as millisecs since the epoch (1970 based)
     */
    def dateFromString(dateStr : String): Long = {
		val millis : Long = if (dateStr != null) {
		    try {
		        val dateTime : DateTime = dtformatter.parseDateTime(dateStr)
		        val msecs : Long = dateTime.getMillis
		        msecs
		    } catch {
			    case iae:IllegalArgumentException => {
            
			    	logger.error(s"Unable to parse '$dateStr' with any of the patterns - '${datePatterns.toString}'")
            
			    	0
			    }
		    }
		} else {
			0
		}
        millis
    }
    
    val timePatterns : Array[String] =      Array[String]("HH:mm:ss"
    													, "HH:mm:ss:SSS"
    													, "hh:mm:ss"
    													, "h:mm:ss aa"
    													)
    val timeParsers : Array[DateTimeParser] = timePatterns.map (fmt => {
    	DateTimeFormat.forPattern(fmt).getParser()
    })

	val tmformatter : DateTimeFormatter = new DateTimeFormatterBuilder().append( null, timeParsers ).toFormatter();
    
    /** 
        Answer the number of millisecs from the epoch for the supplied time string that is in one of 
        the following builtin formats:
        
        """
        	 Pattern						Example
			 "HH:mm:ss"						23:59:59
			 "HH:mm:ss:SSS"					23:59:59:999
			 "h:mm:ss"						12:45:59
			 "h:mm:ss aa"					12:45:59 PM
        """
        
        @param fmtStr - instructions on how to parse the string. @see iso860DateFmt for format info 
        @param timeStr - the string to parse
        @return a Long representing the timestamp as millisecs since the epoch (1970 based)
     */
    def timeFromString(timeStr : String): Long = {
		val millis : Long = if (timeStr != null) {
		    try {
		        val dateTime : DateTime = tmformatter.parseDateTime(timeStr)
		        val msecs : Long = dateTime.getMillis
		        msecs
		    } catch {
		      case iae:IllegalArgumentException => {
            
		    	  logger.error(s"Unable to parse '$timeStr' with any of the patterns - '${timePatterns.toString}'")
            
		    	  0
		      }
		    }
		} else {
			0
		}
        millis
    }
  
}

