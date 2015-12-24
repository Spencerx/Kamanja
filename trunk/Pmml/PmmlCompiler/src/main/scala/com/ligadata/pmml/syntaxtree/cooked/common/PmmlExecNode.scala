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

package com.ligadata.pmml.syntaxtree.cooked.common

import com.ligadata.pmml.syntaxtree.raw.common._

import scala.collection.mutable._
import scala.math._
import scala.collection.immutable.StringLike
import scala.util.control.Breaks._
import com.ligadata.pmml.runtime._
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.kamanja.metadata._
import com.ligadata.pmml.compiler._
import com.ligadata.pmml.support._
import com.ligadata.pmml.traits._
import com.ligadata.pmml.syntaxtree.cooked.common._
import com.ligadata.pmml.transforms.printers.scala.common._
import com.ligadata.Exceptions.StackTrace


class PmmlExecNode (val qName : String, val lineNumber : Int, val columnNumber : Int) extends com.ligadata.pmml.compiler.LogTrait  { 
	var children : Queue[PmmlExecNode] = new Queue[PmmlExecNode]()
	
	def addChild(child : PmmlExecNode) = {
		children += child
	}
	def replaceChildren(newChildren : Queue[PmmlExecNode]) { children = newChildren }
	
	def Children : Queue[PmmlExecNode] = children
	
	def ElementValue : String = qName
	
	def asString(ctx : PmmlContext) : String = "PMML"
	  
	override def toString : String = s"Node $qName ($lineNumber:$columnNumber)"
	
	/** general access to PmmlExecNode trees .. our node visitor */
	def Visit(visitor : PmmlExecVisitor, navStack : Stack[PmmlExecNode]) {
		visitor.Visit(this,navStack)
		navStack.push(this) /** make the node hierarchy available to the visitors */
		children.foreach((child) => {
			child.Visit(visitor, navStack)
	  	})
	  	navStack.pop
	}

}

class xConstant(lineNumber : Int, columnNumber : Int, val dataType : String, val value : DataValue) 
			extends PmmlExecNode("Constant", lineNumber, columnNumber) {
	def Value : DataValue = value
	
	override def asString(ctx : PmmlContext) : String = {
	  	val strRep : String = value.toString
	  	val dtype : String = dataType.toLowerCase
	  	dtype match {
	  	  	case "long" => {
				/** check the argType... if all numeric (i.e., a long constant) suffix it with "L" to help the Scala compiler recognize this is a long */
				val isAllNumeric : Boolean = strRep.filter(ch => (ch >= '0' && ch <= '9')).size == strRep.size
				if (isAllNumeric) {
					s"${strRep}L"
				} else {
					strRep
				}
	  	  	}
	  		case "string" | "date" | "time" | "dateTime" => s"${'"'}$strRep${'"'}"
	  		case "ident"  => {
	  			val constStrExpr : String = if (strRep == ctx.applyElementName) {
	  			   s"${ctx.applyElementName}"  /** When the value is _each, the item is being passed as a whole ... common with collection args ... */
	  			} else {
	  				val strRep1 = strRep.toLowerCase
					/** get _each's type from the containerStack to decide whether a mapped or fixed style of field expression is needed */
					val fcnTypeInfo : FcnTypeInfo = if (ctx.fcnTypeInfoStack.nonEmpty) ctx.fcnTypeInfoStack.top else null
					val reasonable : Boolean = (fcnTypeInfo != null && fcnTypeInfo.containerTypeDef != null) // sanity 
					if (! reasonable) {
						if (fcnTypeInfo != null) {
							val fullnm : String = if (fcnTypeInfo.fcnDef.FullName != null) fcnTypeInfo.fcnDef.FullName else "Unknown function"
							  
							PmmlError.logError(ctx, s"iterable function $fullnm does not have a containerType for its Iterable (arg1)")
						} else {
							PmmlError.logError(ctx, "fcnTypeInfo is null for a function with 'ident' names (iterable function)")
						}
						"BadIdent"
					} else {
						if (fcnTypeInfo.containerTypeDef != null && fcnTypeInfo.containerTypeDef.IsFixed) {
							s"${ctx.applyElementName}.$strRep1"  
						} else {
							/** Only add field cast for the MappedMsgTypDef fields at this point since the 'get' fcn returns an Any */
							/** NOTE: the fcnTypeInfo WILL have items in it as the 'ident' type only refers to a (first) sibling container */
							
							val mappedContainer : MappedMsgTypeDef = if (fcnTypeInfo.containerTypeDef.isInstanceOf[MappedMsgTypeDef]) 
									fcnTypeInfo.containerTypeDef.asInstanceOf[MappedMsgTypeDef] else null
							if (mappedContainer != null) {
								val attrType : BaseAttributeDef = mappedContainer.attributeFor(strRep1)
								val attrTypeStr : String = if (attrType != null) attrType.typeString else null
								if (attrTypeStr != null) {
									s"${ctx.applyElementName}.get(${'"'}$strRep1${'"'}).asInstanceOf[$attrTypeStr]"
								} else {
									PmmlError.logError(ctx, s"Field $strRep1 is NOT a field name in the container with type ${mappedContainer.typeString}")
									s"${ctx.applyElementName}.get(${'"'}$strRep1${'"'})" /** this will cause scala compile error (or worse) */
								}
							} else {
								val elementTypes : Array[BaseTypeDef] = fcnTypeInfo.collectionElementTypes
													
								/** NOTE: Only Iterable types with single element type are currently supported. */
								if (elementTypes.size == 1) {
									val baseElemType : BaseTypeDef = elementTypes.last
									val mappedContainerElemType : MappedMsgTypeDef = if (baseElemType.isInstanceOf[MappedMsgTypeDef]) 
										baseElemType.asInstanceOf[MappedMsgTypeDef] else null
									if (mappedContainerElemType != null) {
										val attrType : BaseAttributeDef = mappedContainerElemType.attributeFor(strRep1)
										val attrTypeStr : String = if (attrType != null) attrType.typeString else null
										if (attrTypeStr != null) {
											s"${ctx.applyElementName}.get(${'"'}$strRep1${'"'}).asInstanceOf[$attrTypeStr]"
										} else {
											PmmlError.logError(ctx, s"Field $strRep1 is NOT a field name in the container with type ${mappedContainer.typeString}")
											s"${ctx.applyElementName}.get(${'"'}$strRep1${'"'})" /** this will cause scala compile error */
										}
									} else { /** one more time... see if the element is also an array (i.e., array of array of some (mapped or fixed) struct */
										if (baseElemType.isInstanceOf[StructTypeDef]) {
											s"${ctx.applyElementName}.$strRep1"
										} else {
											val nestedElems : Array[BaseTypeDef] = if (baseElemType.isInstanceOf[ContainerTypeDef]) {
													baseElemType.asInstanceOf[ContainerTypeDef].ElementTypes
												} else {
													null
												}
											if (nestedElems != null && nestedElems.size == 1) {
												val nestedBaseElemType : BaseTypeDef = nestedElems.last
												val nestedMappedContainerElemType : MappedMsgTypeDef = if (nestedBaseElemType.isInstanceOf[MappedMsgTypeDef]) 
													nestedBaseElemType.asInstanceOf[MappedMsgTypeDef] else null
												if (nestedMappedContainerElemType != null) {
													val attrType : BaseAttributeDef = nestedMappedContainerElemType.attributeFor(strRep1)
													val attrTypeStr : String = if (attrType != null) attrType.typeString else null
													if (attrTypeStr != null) {
														s"${ctx.applyElementName}.get(${'"'}$strRep1${'"'}).asInstanceOf[$attrTypeStr]"
													} else {
														PmmlError.logError(ctx, s"Field $strRep1 is NOT a field name in the container with type ${mappedContainer.typeString}")
														s"${ctx.applyElementName}.get(${'"'}$strRep1${'"'})" /** this will cause scala compile error */
													}
												} else {
													s"${ctx.applyElementName}.$strRep1"
												}										
											} else {	
												if (nestedElems != null) {
													PmmlError.logWarning(ctx, s"Type ${baseElemType.typeString} has ${nestedElems.size} elements... we are only supporting collections with single 'mapped' type elements at the moment")
												}
												s"${ctx.applyElementName}.$strRep1"
											}
										}
									}
								} else {
									PmmlError.logWarning(ctx, s"Type ${fcnTypeInfo.containerTypeDef.typeString} has ${elementTypes.size} elements... we are only supporting collections with single 'mapped' type elements at the moment")
									s"${ctx.applyElementName}.get(${'"'}$strRep1${'"'})"
								}				  
							}
						}
					}
	  			}
	  			constStrExpr
	  		}
  		    /** 
  		     *  The constant value of either the typename or mbrTypename is:
  		     *  
  		     *  o the name of a datafield 
		     *  o the name a derived field.  
		     *  o the name of a dataType
		     *  
		     *  Retrieve the typeString (i.e., the full package qualified typename) for it or 
		     *  its member type. For Maps, the value's type is returned for mbrTypename
  		     */ 
	  		case "typename"  => { 
	  		  val expandCompoundFieldTypes : Boolean = false
	  		  val fldTypeInfo : Array[(String,Boolean,BaseTypeDef)] = ctx.getFieldType(strRep, expandCompoundFieldTypes) 
	  		  val (typestr, isContainerWMbrs, typedef) : (String,Boolean,BaseTypeDef) = if (fldTypeInfo != null && fldTypeInfo.size > 0) fldTypeInfo.last else (null,false,null)
	  		  if (typestr != null) {
	  			  s"${'"'}$typestr${'"'}" 
	  		  } else {
	  		      /** see if it is a type name we know */
	  			  val (typeStr,typ) : (String,BaseTypeDef) = ctx.MetadataHelper.getType(strRep)
	  			  val typStr : String = if (typeStr != null) {
	  				  s"${'"'}$typeStr${'"'}"
	  			  } else {
	  				  "Any"
	  			  }
	  			  typStr
	  		  }
	  		}
	  		case "mbrtypename"  => { 
	  		  /** 
	  		   *  Only common single member collections are supported (no Maps yet) and no derived collection types.
	  		   */ 
	  		  val expandCompoundFieldTypes : Boolean = false
	  		  val fldTypeInfo : Array[(String,Boolean,BaseTypeDef)] = ctx.getFieldType(strRep, expandCompoundFieldTypes) 
	  		  val (typestr, isContainerWMbrs, typedef) : (String,Boolean,BaseTypeDef) = if (fldTypeInfo != null && fldTypeInfo.size > 0) fldTypeInfo.last else (null,false,null)
	  		  val ts : String = if (typedef != null && (typestr.contains("scala.Array") || typestr.contains("scala.collection"))) {
	  			  val typeStr : String = typedef match {
	  			    case ar : ArrayTypeDef => typedef.asInstanceOf[ArrayTypeDef].elemDef.typeString
	  			    case arb : ArrayBufTypeDef => typedef.asInstanceOf[ArrayTypeDef].elemDef.typeString
	  			    case lst : ListTypeDef => typedef.asInstanceOf[ListTypeDef].valDef.typeString
	  			    case qt : QueueTypeDef => typedef.asInstanceOf[QueueTypeDef].valDef.typeString
	  			    case st : SortedSetTypeDef => typedef.asInstanceOf[SortedSetTypeDef].keyDef.typeString
	  			    case tt : TreeSetTypeDef => typedef.asInstanceOf[TreeSetTypeDef].keyDef.typeString
	  			    case it : ImmutableSetTypeDef => typedef.asInstanceOf[ImmutableSetTypeDef].keyDef.typeString
	  			    case s  : SetTypeDef => typedef.asInstanceOf[SetTypeDef].keyDef.typeString
	  			    case _ => "Any"
	  			  }
	  			  typeStr 
	  		  } else {
	  		      /** see if it is a type name we know... if so, return the last ElementType's typestring (no Maps yet).
	  		       *  If it is not a container type, "Any" is printed... no doubt being the wrong thing and ending badly. */
	  			  val containerType : ContainerTypeDef = ctx.MetadataHelper.getContainerType(strRep)
	  			  val typestr : String = if (containerType != null) containerType.typeString else null
	  			  val typeStr : String = if (typestr != null  && (typestr.contains("scala.Array") || typestr.contains("scala.collection"))) {
	  				  val lastMbrType : BaseTypeDef = containerType.ElementTypes.last
	  				  lastMbrType.typeString
	  			  } else {
	  				  "Any"
	  			  }
	  			  typeStr
	  		  }
	  		  ts
	  		}
	  		case "context"  => { 
	  			/** Note: the constant value could be anything... the point of the "context" constant dataType is to inject the Context object
	  			 *  for the current model instance into the code generation output stream... This works because the Context is present in 
	  			 *  each of the classes that are generated... be they the model class itself or the derived variables, ruleset, etc.*/
	  			"ctx"
	  		}

	  		case _ => strRep
	  	}
	}

}

class xHeader(lineNumber : Int, columnNumber : Int, val copyright : String, val description : String) 
			extends PmmlExecNode("Header", lineNumber, columnNumber) {

	var namespace : String = "System"
	var applicationName : String = "Some App"
	var version : String = "Some Version"
	def ApplicationNameVersion(appName : String, ver : String) {
		applicationName = appName
		version = ver
	}
	def ModelNamespace(namespc : String) {
		namespace = namespc
	}
	override def asString(ctx : PmmlContext) : String =  {
			val header1 = s"/** Copyright : $copyright Application : $applicationName Version : $version\n"
			val header2 = s"    Description : $description\n"
			val header = s"$header1\n$header2 */"
			header
	}
	
}


class dateDaysSince0(timesecs : Long)
class dateDaysSince1960(timesecs : Long)
class dateDaysSince1970(timesecs : Long)
class dateDaysSince1980(timesecs : Long)
class dateTimeSecondsSince0(timesecs : Long)
class dateTimeSecondsSince1960(timesecs : Long)
class dateTimeSecondsSince1970(timesecs : Long)
class dateTimeSecondsSince1980(timesecs : Long)
class timeSeconds(timesecs : Long)
class xDataField(lineNumber : Int, columnNumber : Int
				, val name : String
				, val displayName : String
				, val optype : String
				, val dataType : String
				, val taxonomy : String
				, val isCyclic : String) extends PmmlExecNode("DataField", lineNumber, columnNumber) {
	var values : ArrayBuffer[(String,String)] = ArrayBuffer[(String,String)]() /** (value, property) */
	var leftMargin : String = ""
	var rightMargin : String =""
	var closure : String = ""
	
	def addValuePropertyPair(value : String, proprty : String) {
		values += Tuple2(value, proprty)
	}
	def continuousConstraints(left : String, right : String, closr : String) {
		leftMargin = left
		rightMargin = right;
		closure = closr
	}

	override def asString(ctx : PmmlContext) : String = 
	{
		val typ = PmmlTypes.scalaDataType(dataType)
		val variable = s"var $name : $typ "
		variable
	}

}


class xDataDictionary(lineNumber : Int, columnNumber : Int) extends PmmlExecNode("DataDictionary", lineNumber, columnNumber) {
	var map = new HashMap[String,xDataField]()
	
	def DataDictionary = { map }
	
	def add(node : xDataField) {
		map += (node.name -> node)
	}

	override def asString(ctx : PmmlContext) : String = 
	{
		val dictBuffer : StringBuilder = new StringBuilder()
		dictBuffer.append("dataDict : Map (\n")
		
		val beginchars : String = "\n\t\t"
		map.foreach {
		  	case ( _,dfld) => { 
			  		val dName = dfld.name
			  		val typ = PmmlTypes.scalaDataType(dfld.dataType)

		    		dictBuffer.append(s"$beginchars${'"'}$dName${'"'} => ${'"'}$typ${'"'}")
		  		}
			}
		dictBuffer.append(beginchars)
		dictBuffer.append(")")
		dictBuffer.toString
  	}

	def asCode : String = {
		val dictBuffer : StringBuilder = new StringBuilder()
		//dictBuffer.append("dataDict : Map[String, DataField]()\n")  .. pre-declared as empty in the context 
		
		var serNo : Int = 0
		map.foreach {
		  	case ( _,dfld) => { 
		  		val dName = dfld.name
				val dNameFixer = "[-.]+".r
				val className : String = dNameFixer.replaceAllIn(dName,"_")

				val typ = PmmlTypes.scalaDataType(dfld.dataType)
		  		val lm : String = dfld.leftMargin
		  		val rm : String = dfld.rightMargin
		  		val cls : String = dfld.closure
		  		serNo += 1
		  		val valnm = s"dfoo$serNo"
		  		var valuesStr = NodePrinterHelpers.valuesHlp(dfld.values, valnm, "        ")
		  		dictBuffer.append(s"        var $valnm : ArrayBuffer[(String,String)] = $valuesStr\n")
		  		var dataFieldStr : String = s"        ctx.dDict += (${'"'}$dName${'"'} -> new DataField(${'"'}$dName${'"'}, ${'"'}$typ${'"'}, $valnm, ${'"'}$lm${'"'}, ${'"'}$rm${'"'}, ${'"'}$cls${'"'}))\n"
	    		dictBuffer.append(dataFieldStr)
		  	}
		}
		dictBuffer.toString
	}

}	 

class xDerivedField(lineNumber : Int, columnNumber : Int
					, val name : String
					, val displayName : String
					, val optype : String
					, val dataType : String) extends PmmlExecNode("DerivedField", lineNumber, columnNumber) {
	var values : ArrayBuffer[(String,String)] = ArrayBuffer[(String,String)]() /** (value, property) */
	var leftMargin : String = _
	var rightMargin : String = _
	var closure : String = ""

	var categorizedValues : ArrayBuffer[xConstant] = ArrayBuffer[xConstant]()
	def CategorizedValue(idx : Int) : xConstant = categorizedValues.apply(idx)
	def CategorizedValues : ArrayBuffer[xConstant] = categorizedValues
	def addCategorizedValue(aConst : xConstant)  : ArrayBuffer[xConstant] = { categorizedValues += aConst }
	
	def addValuePropertyPair(value : String, proprty : String) {
		values += Tuple2(value, proprty)
	}
	def continuousConstraints(left : String, right : String, closr : String) {
		leftMargin = left
		rightMargin = right;
		closure = closr
	}
	
	def Name(ctx : PmmlContext) : String = { 
	  name
	}
	
	override def toString : String = s"DerivedField '$name' ($lineNumber:$columnNumber)"

	override def asString(ctx : PmmlContext) : String = 
	{
		val typ = PmmlTypes.scalaDataType(dataType)
		val fcnNm = name + "_Fcn"
		val variable = s"var $name : $typ = $fcnNm"
		variable
	}
	
}

class xTransformationDictionary(lineNumber : Int, columnNumber : Int) 
			extends PmmlExecNode("TransformationDictionary", lineNumber, columnNumber) {
	var map = new HashMap[String,xDerivedField]()
	
	def add(node : xDerivedField) {
		map += (node.name -> node)
	}
	override def asString(ctx : PmmlContext) : String = 
	{
		val dictBuffer : StringBuilder = new StringBuilder()
		dictBuffer.append("var xDict : Map (\n")

		val beginchars : String = "\n\t\t"
		map.foreach {
		  	case ( _,dfld) => { 
			  		val dName = dfld.name
			  		val typ = PmmlTypes.scalaDataType(dfld.dataType)
		
		    		dictBuffer.append(s"$beginchars${'"'}$dName${'"'} => ${'"'}$typ${'"'}")
		  		}
			}
		dictBuffer.append(beginchars)
		dictBuffer.append(")")
		dictBuffer.toString
  	}

	def asCode : String = {
		val dictBuffer : StringBuilder = new StringBuilder()
		//dictBuffer.append("dataDict : Map[String, DataField]()\n").. pre-declared as empty in the context 
		
		var serNo : Int = 0
		map.foreach {
		  	case ( _,dfld) => { 
	  			/** pmmlElementVistitorMap += ("Constant" -> PmmlNode.mkPmmlConstant) */
		  		val dName = dfld.name
		  		
				val classname : String = s"Derive_$dName"
				val classNameFixer = "[-.]+".r
				val className : String = classNameFixer.replaceAllIn(classname,"_")

		  		val typ = PmmlTypes.scalaDataType(dfld.dataType)
		  		val lm : String = dfld.leftMargin
		  		val rm : String = dfld.rightMargin
		  		val cls : String = dfld.closure
		  		serNo += 1
		  		val serNoStr = serNo.toString

		  		val valnm = s"xbar$serNoStr"
		  		var valuesStr = NodePrinterHelpers.valuesHlp(dfld.values, valnm, "        ")
		  		dictBuffer.append(s"        var $valnm : ArrayBuffer[(String,String)] = $valuesStr\n")
		  		var derivedFieldStr : String = s"        ctx.xDict += (${'"'}$dName${'"'} -> new $className(${'"'}$dName${'"'}, ${'"'}$typ${'"'}, $valnm, ${'"'}$lm${'"'}, ${'"'}$rm${'"'}, ${'"'}$cls${'"'}))\n"
	    		dictBuffer.append(derivedFieldStr)
		  	}
		}
		dictBuffer.toString
	}

}	 

class xDefineFunction(lineNumber : Int, columnNumber : Int
					, val name : String
					, val optype : String
					, val dataType : String)  extends PmmlExecNode("DefineFunction", lineNumber, columnNumber) {	
	override def asString(ctx : PmmlContext) : String = 
	{
		val applyv = s"name = $name, optype = $optype, dataType = $dataType"
		applyv
	}
	
	def NumberArguments : Int = {
		Children.length
	}
	
	def ArgumentTypes : Array[String] = {
		    
		val types  = Children.map(child => {
				child match {
				  	case parm : xParameterField => PmmlTypes.scalaDataType(parm.dataType)
				  	case _ => "String"
				 }
			}).toArray
		types
			
	}
	
	def ArgumentDecls : Array[String] = {
		/** FIXME build scala argument list here instead of this place holder that returns the ArgumentTypes */
		ArgumentTypes
	}

}

class xParameterField(lineNumber : Int, columnNumber : Int
					, val name : String
					, val optype : String
					, val dataType : String)  extends PmmlExecNode("ParameterField", lineNumber, columnNumber) {	

	override def asString(ctx : PmmlContext) : String = 
	{
		val applyv = s"name = $name, optype = $optype, dataType = $dataType"
		applyv
	}
	
}

class xApply(lineNumber : Int, columnNumber : Int, val function : String, val mapMissingTo : String, val invalidValueTreatment : String = "returnInvalid") 
			extends PmmlExecNode("Apply", lineNumber, columnNumber) {
  
	var typeInfo : FcnTypeInfo = null
	def SetTypeInfo(typInfo : FcnTypeInfo) = { typeInfo = typInfo }
	def GetTypeInfo : FcnTypeInfo = { typeInfo }
	
	var ifActionElements : ArrayBuffer[PmmlExecNode] = ArrayBuffer[PmmlExecNode]()
	def IfActionElement(idx : Int) : PmmlExecNode = ifActionElements.apply(idx)
	def IfActionElements : ArrayBuffer[PmmlExecNode] = ifActionElements
	def addIfAction(anAction : PmmlExecNode)  : ArrayBuffer[PmmlExecNode] = { ifActionElements += anAction; ifActionElements }
	
	override def asString(ctx : PmmlContext) : String = 
	{
		val applyv = s"function = $function"
		applyv
	}
	
	
	override def toString : String = s"Apply function '$function' ($lineNumber:$columnNumber)"


}

class xFieldRef(lineNumber : Int, columnNumber : Int, val field : String, val mapMissingTo : String ) 
			extends PmmlExecNode("FieldRef", lineNumber, columnNumber) {
  
	override def asString(ctx : PmmlContext) : String = 
	{
		val fr = s"FieldRef(field = $field)"
		fr
	}

}

class xExtension(lineNumber : Int, columnNumber : Int, val extender : String, val name : String , val value : String ) 
			extends PmmlExecNode("Extension", lineNumber, columnNumber) {

	override def asString(ctx : PmmlContext) : String = 
	{
		val ext = s"Extension (extender = $extender, name = $name, value = $value)"
		ext
	}
	
}     

/** 
 *  The xMapValues structure will be used to represent queries result sets and tables.  Both Maps and 
 *  Vectors of Tuples are supported.  The expectation is that the field will contain the necessary
 *  field names to name the content in the 'map'.  For the standard use case (a standard key/value map), 
 *  the fieldColumnMap will have the "database" field name as the key (e.g., beneficiary.pufId or 
 *  a schema qualified one - cms.beneficiary.pufId) and the value will be the "internal" column name 
 *  to use to refer to the data in the loaded table (whether it is from an external database or file, 
 *  or an in-line table declaration. 
 *  
 *  In terms of the MapValue syntax tree nodes, there will be these kinds:
 *      o xMapValuesMapExternal		- for TableLocator based Map requests
 *  	o xMapValuesMapInline		- for InlineTable based Map requests
 *   	o xMapValuesArrayExternal	- for TableLocator based Array of tuples
 *    	o xMapValuesArrayInline		- for InlineTable based Array of tuples
 *  
 *   The TableLocator is used for external table content
 *  
 *  	<TableLocator>
 *         <Extension> name="connection string" value="blah blah"/>
 *         <Extension> name="table" value="humana.beneficiary"/>
 *         <Extension> extender="inner join on pufId" name="tablenames" value="beneficiaries,outPatient,inPatient"
 *         <Extension> name="filter" value="some filter"
 *         <Extension> name="fieldname" value="pufId"
 *         <Extension> name="fieldname" value="inSummary"
 *         <Extension> extender="Aggregate" name="sum(inCost)" value="inPatientCosts"
 *         <Extension> extender="Aggregate" name="sum(outCost)" value="outPatientCosts"
 *         .
 *         .
 *         .
 *         <Aggregate field="outPatientCosts" function="sum" groupField="pufId"/>
 *         <Aggregate field="outPatientCosts" function="sum" groupField="pufId"/>
 *      </TableLocator>
 *      
 *      Since the Extension attributes are not checked by the syntax checkers, I think this is ok.  
 *      Obviously this needs more thought to properly and succinctly represent the data base query.
 *      We may want to define our own type here (e.g, XligaQuery) rather than general scheme.  Also
 *      it seems like just giving the full query as the value of one of the TableLocator's Extensions
 *      might be good.  If so, then additional queries that gives the metadata for the columns and functions
 *      used in it might also be good:
 *      
 *  	<TableLocator>
 *         <Extension> name="connection string" value="blah blah"/>
 *         <Extension> extender = "data" name="query" value="select... blah blah ... order by 1,2"/>
 *         <Extension> extender="metadata" name="columns" value="select attname, atttyp, isarray from mpg_attribute where..."/>
 *         <Extension> extender="metadata" name="functions" value="select ... from pg_proc ..."/>
 *         <Extension> extender="metadata" name="tables" value="select ... from pg_class ..."/>
 *      </TableLocator>
 *      
 *      
 *      File loads could be represented like this:
 *      
 *  	<TableLocator>
 *         <Extension> name="tableType" value="<{Map | Vector}>"/>
 *         <Extension> extender="metadata" name="metafile" value="<metadata-description-file-path>"/>
 *         <Extension> extender="data" name="datafile" value="<data-file-path>"/> <!-- metadata in line above describes this file -->
 *      </TableLocator>
 *      
 *      Metadata could be a serialized dictionary....
 *      
 *      Inline tables could be like this:
 *      
 *      <InlineTable>
 *          <Extension> extender="metadata" name="file" value="<metadata-description-file-path>"/> <!-- describes row tuples -->
 *          <Extension> name="tableType" value="<{Map | Vector}>"/>
 *          <row><key>m</key><value>male</value>
 *          .
 *          .
 *          .
 *      </InlineTable>
 *              
 */
class xMapValuesMap(lineNumber : Int, columnNumber : Int
					, val mapMissingTo : String
					, val defaultValue : String
					, val outputColumn : String
					, val dataType : String
					, val treatAsKeyValueMap : String
					, val dataSourceIsInline : String) extends PmmlExecNode("MapValuesMap", lineNumber, columnNumber) {
	var fieldColumnMap : Map[String,String]  = Map[String,String]()
	var table : Map[String,String] = Map[String,String]()
	var metadata : Map[String, Tuple2[String,Boolean]] = Map[String,Tuple2[String,Boolean]]() /** colnm/(type,isarray) */
	var extensions : Array[xExtension] = Array[xExtension]()
	
	def add(key: String, value : String) {
		table(key) = value
	}
	def addFieldColumnPair ( fieldname : String, column : String) {
		fieldColumnMap(fieldname) = column
	}
	def addMetaData ( column : String, meta : Tuple2[String,Boolean]) {
		metadata(column) = meta
	}
	def addExtensions ( xs : Array[xExtension]) {
		extensions ++= xs
	}
 
	/** general diagnostic function to see what is there */
	override def asString(ctx : PmmlContext) : String = 
	{
		val typ = PmmlTypes.scalaDataType(dataType)
		val ext = s"xMapValuesMap(outputColumn = $outputColumn dataType = $typ"
		ext
	}

}

class xMapValuesMapInline(lineNumber : Int, columnNumber : Int
					, mapMissingTo : String
					, defaultValue : String
					, outputColumn : String
					, dataType : String
					, val containerStyle : String
					, val dataSource : String) 
			extends xMapValuesMap(lineNumber, columnNumber, mapMissingTo, defaultValue, outputColumn, dataType, containerStyle, dataSource) {

	/** general diagnostic function to see what is there */
	override def asString(ctx : PmmlContext) : String = 
	{
		val typ = PmmlTypes.scalaDataType(dataType)
		val ext = s"xMapValuesMapInline(outputColumn = $outputColumn dataType = $typ"
		ext
	}
	
}

class xMapValuesMapExternal(lineNumber : Int, columnNumber : Int
					, mapMissingTo : String
					, defaultValue : String
					, outputColumn : String
					, dataType : String
					, containerStyle : String
					, dataSource : String)
			extends xMapValuesMap(lineNumber, columnNumber, mapMissingTo, defaultValue, outputColumn, dataType, containerStyle, dataSource) {

	/** FIXME : need containers here to capture the content in the TableLocator and represent the data and metadata queries */
  override def asString(ctx : PmmlContext) : String = 
	  {
		val typ = PmmlTypes.scalaDataType(dataType)
			val ext = s"xMapValuesMapExternal(outputColumn = $outputColumn dataType = $typ"
			ext
	  }

}

class xMapValuesArray(lineNumber : Int, columnNumber : Int
					, val mapMissingTo : String
					, val defaultValue : String
					, val outputColumn : String
					, val dataType : String
					, val containerStyle : String
					, val dataSource : String)
			extends PmmlExecNode("MapValuesArray", lineNumber, columnNumber) {
	var fieldColumnMap : Map[String,String]  = Map[String,String]() /** maps external table names to internal */
	var table : ArrayBuffer[String] = ArrayBuffer[String]()
	var metadata : Map[String, Tuple2[String,Boolean]] = Map[String,Tuple2[String,Boolean]]() /** colnm/(type,isarray) */
	var extensions : Array[xExtension] = Array[xExtension]()
	
	def add(key: String, value : String) {
		table += value
	}
	def addFieldColumnPair ( fieldname : String, column : String) {
		fieldColumnMap(fieldname) = column
	}
	def addMetaData ( column : String, meta : Tuple2[String,Boolean]) {
		metadata(column) = meta
	}
	def addExtensions ( xs : Array[xExtension]) {
		extensions ++= xs
	}
	override def asString(ctx : PmmlContext) : String = {
		val typ = PmmlTypes.scalaDataType(dataType)
		val ext = s"xMapValuesArray(outputColumn = $outputColumn dataType = $typ"
		ext
	}

}

class xMapValuesArrayExternal(lineNumber : Int, columnNumber : Int
					, mapMissingTo : String
					, defaultValue : String
					, outputColumn : String
					, dataType : String
					, containerStyle : String
					, dataSource : String)
			extends xMapValuesArray(lineNumber, columnNumber, mapMissingTo, defaultValue, outputColumn, dataType, containerStyle, dataSource) {

	/** FIXME : need containers here to capture the content in the TableLocator and represent the data and metadata queries */
	override def asString(ctx : PmmlContext) : String = {
		val typ = PmmlTypes.scalaDataType(dataType)
		val ext = s"xMapValuesArrayExternal(outputColumn = $outputColumn dataType = $typ"
		ext
	}

}

class xMapValuesArrayInline(lineNumber : Int, columnNumber : Int
					, mapMissingTo : String
					, defaultValue : String
					, outputColumn : String
					, dataType : String
					, containerStyle : String
					, dataSource : String)
			extends xMapValuesArray(lineNumber, columnNumber, mapMissingTo, defaultValue, outputColumn, dataType, containerStyle, dataSource) {

	/** FIXME : need containers here to capture the content in the TableLocator and represent the data and metadata queries */
	override def asString(ctx : PmmlContext) : String = {
		val typ = PmmlTypes.scalaDataType(dataType)
		val ext = s"xMapValuesArrayInline(outputColumn = $outputColumn dataType = $typ"
		ext
	}

}

/**xMapValuesVectorExternal
 
	<xs:element name="TableLocator">
	  <xs:complexType>
	    <xs:sequence>
	      <xs:element ref="Extension" minOccurs="0" maxOccurs="unbounded"/>
	    </xs:sequence>
	  </xs:complexType>
	</xs:element> 
	
    <TableLocator>
      <Extension name="dbname" value="myDB"/>
    </TableLocator>
    
  <xs:element name="Extension">
    <xs:complexType>
      <xs:complexContent mixed="true">
        <xs:restriction base="xs:anyType">
          <xs:sequence>
            <xs:any processContents="skip" minOccurs="0" maxOccurs="unbounded"/>
          </xs:sequence>
          <xs:attribute name="extender" type="xs:string" use="optional"/>
          <xs:attribute name="name" type="xs:string" use="optional"/>
          <xs:attribute name="value" type="xs:string" use="optional"/>
        </xs:restriction>
      </xs:complexContent>
    </xs:complexType>
  </xs:element>
 
*/


class xFieldColumnPair(lineNumber : Int, columnNumber : Int, val field : String, val column : String) 
			extends PmmlExecNode("FieldColumnPair", lineNumber, columnNumber) {

	override def asString(ctx : PmmlContext) : String = {
		val ext = s"xFieldColumnPair(field = $field column = $column)"
		ext
	}

}

class xMiningField(lineNumber : Int, columnNumber : Int
				, val name : String
			    , val usageType : String
			    , val opType : String
			    , var importance : Double
			    , val outliers : String
			    , var lowValue : Double
			    , var highValue : Double
			    , val missingValueReplacement : String
			    , val missingValueTreatment : String
			    , val invalidValueTreatment : String) extends PmmlExecNode("MiningField", lineNumber, columnNumber) {

	def Importance(impertance : Double) { importance = impertance }
	def LowValue(lval : Double) { lowValue = lval }
	def HighValue(hval : Double) { highValue = hval }

	override def asString(ctx : PmmlContext) : String = {
		val ext = s"xMiningField(name = $name)"
		ext
	}
 
}

class xRuleSelectionMethod(lineNumber : Int, columnNumber : Int, val criterion : String) 
			extends PmmlExecNode("RuleSelectionMethod", lineNumber, columnNumber) {

}

class xRuleSetModel(lineNumber : Int, columnNumber : Int
				, val modelName : String
				, val functionName : String
				, val algorithmName : String
				, val isScorable : String) extends PmmlExecNode("RuleSetModel", lineNumber, columnNumber) {
	var miningSchemaMap : HashMap[String,xMiningField] = HashMap[String,xMiningField]()
	var ruleSet : ArrayBuffer[xSimpleRule] = ArrayBuffer[xSimpleRule]()
	var ruleSetSelectionMethods : ArrayBuffer[xRuleSelectionMethod] = ArrayBuffer[xRuleSelectionMethod]()
	var defaultScore : String = "NONE"
	
	def MiningSchemaMap : HashMap[String,xMiningField] = { miningSchemaMap }
	
	def addMiningField(fieldName : String, fieldDetails: xMiningField) {
		miningSchemaMap(fieldName) = fieldDetails
	}

	def addRule(r : xSimpleRule) {
		ruleSet += r
	}

	def addRuleSetSelectionMethod(m : xRuleSelectionMethod) {
		ruleSetSelectionMethods += m
	}

	def outputFieldNames() : Array[String] = {
		val outputs = miningSchemaMap.filter((m) => m._2.opType == "predicted" || m._2.opType == "supplementary")
		outputs.keys.toArray
	}

	def outputFields() : Array[xMiningField] = {
		val outputs = miningSchemaMap.filter((m) => m._2.opType == "predicted" || m._2.opType == "supplementary")
		outputs.values.toArray
	}
	
	def DefaultScore (s : String) {
		defaultScore = s
	}

	def DefaultScore : String = defaultScore
	

	override def asString(ctx : PmmlContext) : String = {
		val ext = s"xRuleSetModel(modelName = $modelName)"
		ext
	}

}

class xRuleSet(lineNumber : Int, columnNumber : Int
				, val recordCount : String
				, val nbCorrect : String
				, val defaultScore : String
				, val defaultConfidence : String)  extends PmmlExecNode("RuleSet", lineNumber, columnNumber) {

	override def asString(ctx : PmmlContext) : String = {
		val ext = s"xRuleSet(recordCount = $recordCount, nbCorrect = $nbCorrect, defaultScore = $defaultScore, defaultConfidence = $defaultConfidence )"
		ext
	}
	
}

class xSimpleRule(lineNumber : Int, columnNumber : Int
				, val id : Option[String]
			    , val score : String
			    , var recordCount : Double
			    , var nbCorrect : Double
			    , var confidence : Double
			    , var weight : Double) extends PmmlExecNode("SimpleRule", lineNumber, columnNumber) {
  
	var scoreDistributions : ArrayBuffer[xScoreDistribution] = ArrayBuffer[xScoreDistribution]()
	
	def Name(ctx : PmmlContext) : String = { 
	  val idstr : String = id match {
	    case Some(id) => "RuleId_" + id + ctx.Counter()
	    case _ => "RuleId_NoId_" + ctx.Counter()
	  }
	  idstr
	}

	def addScoreDistribution(sd : xScoreDistribution) {
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

	override def asString(ctx : PmmlContext) : String = {
		val ext = s"xSimpleRule(id = $id)"
		ext
	}
	
}

class xScoreDistribution(lineNumber : Int, columnNumber : Int
				, var value : String
			    , var recordCount : Double
			    , var confidence : Double
			    , var probability : Double) extends PmmlExecNode("ScoreDistribution", lineNumber, columnNumber) {

	def RecordCount(rc : Double) { recordCount = rc }
	def Confidence(conf : Double) { confidence = conf }
	def Probability(prob : Double) { probability = prob }
	override def asString(ctx : PmmlContext) : String = {
		val ext = s"xScoreDistribution(value = $value)"
		ext
	}
	
}

class xCompoundPredicate(lineNumber : Int, columnNumber : Int, val booleanOperator : String) 
			extends PmmlExecNode("CompoundPredicate", lineNumber, columnNumber) {

	override def asString(ctx : PmmlContext) : String = {
		val ext = s"xCompoundPredicate(booleanOperator = $booleanOperator)"
		ext
	}
  
}

class xSimplePredicate(lineNumber : Int, columnNumber : Int, val field : String, val operator : String, val value : String) 
			extends PmmlExecNode("SimplePredicate", lineNumber, columnNumber) {
  
	override def asString(ctx : PmmlContext) : String = {
		val ext = s"xSimplePredicate($field $operator $value)"
		ext
	}

}

/**
  <SimpleSetPredicate field="art" booleanOperator="isIn">
      <Array n="1" type="string">0</Array>
   </SimpleSetPredicate> 
 
 */
class xSimpleSetPredicate(lineNumber : Int, columnNumber : Int, val field: String, val booleanOperator: String) 
			extends PmmlExecNode("SimpleSetPredicate", lineNumber, columnNumber) {

	override def asString(ctx : PmmlContext) : String = {
		val ext = s"xSimpleSetPredicate($field $booleanOperator {...})"
		ext
  	}

}

class xArray(lineNumber : Int, columnNumber : Int, val n: String, val arrayType : String, val array : Array[String]) 
			extends PmmlExecNode("Array", lineNumber, columnNumber) {

	override def asString(ctx : PmmlContext) : String = {
		val s = array.toString()
		val ext = s"xArray({$s})"
		ext
	}

}

class xValue(lineNumber : Int, columnNumber : Int, val value : String, val property : String) 
			extends PmmlExecNode("Value", lineNumber, columnNumber) {
	
	override def asString(ctx : PmmlContext) : String = {
		val ext = s"xValue(value = $value, property = $property)"
		ext
	}

}



object PmmlExecNode extends com.ligadata.pmml.compiler.LogTrait {

	def mkPmmlExecConstant(ctx : PmmlContext, c : PmmlConstant) : Option[xConstant] = {
		var const : xConstant = new xConstant(c.lineNumber, c.columnNumber
											, c.dataType
											, PmmlTypes.dataValueFromString(c.dataType, c.Value()))
		
		Some(const)
	}

	def mkPmmlExecHeader(ctx : PmmlContext, headerNode : PmmlHeader) : Option[xHeader] = {
		/** Collect the copyright and description if present for later use during code generation */
		ctx.pmmlTerms("Copyright") = Some(headerNode.copyright)
		ctx.pmmlTerms("Description") = Some(headerNode.description)
	  
		Some(new xHeader(headerNode.lineNumber, headerNode.columnNumber, headerNode.copyright, headerNode.description))
	}
	
	def mkPmmlExecApplication(ctx : PmmlContext, headerNode : PmmlApplication) : Option[PmmlExecNode] = {
		/** Collect the Application name and the version if it is present */
		ctx.pmmlTerms("ApplicationName") = Some(headerNode.name)
		ctx.pmmlTerms("Version") = Some(MdMgr.FormatVersion(if (headerNode.version == null || headerNode.version.trim.isEmpty) "1.1" else headerNode.version))

		/** update the header parent node with the application name and version, don't create node*/
		
		val top : Option[PmmlExecNode] = ctx.pmmlExecNodeStack.top
		top match {
		  case Some(top) => {
			  var header : xHeader = top.asInstanceOf[xHeader]
			  header.ApplicationNameVersion(headerNode.name, headerNode.version)
		  }
		  case _ => None
		}
		
		None 
	}
	
	def mkPmmlExecDataDictionary(ctx : PmmlContext, node : PmmlDataDictionary) : Option[xDataDictionary] = {
		Some(new xDataDictionary(node.lineNumber, node.columnNumber))
	}
	
	def mkPmmlExecDataField(ctx : PmmlContext, d : PmmlDataField) : Option[xDataField] = {
		/** update the data dictionary parent node with the datafield; don't create node*/
		val top : Option[PmmlExecNode] = ctx.pmmlExecNodeStack.top
		var datafld : xDataField = null
		top match {
		  case Some(top) => {
			  var dict : xDataDictionary = top.asInstanceOf[xDataDictionary]
			  datafld = new xDataField(d.lineNumber, d.columnNumber, d.name, d.displayName, d.optype, d.dataType, d.taxonomy, d.isCyclic)
			  dict.add(datafld)
			  ctx.dDict(datafld.name) = datafld
			  if (d.Children.size > 0) {
				  /** pick up the values for DataFields */
				  d.Children.foreach( aNode => {
					  if (aNode.isInstanceOf[PmmlValue]) {
					  val vNode : PmmlValue = aNode.asInstanceOf[PmmlValue]
						  datafld.addValuePropertyPair(vNode.value, vNode.property)
					  }
				  })
			  }
		  }
		  case _ => None
		}
		None
	}
	
	def mkPmmlExecValue(ctx : PmmlContext, node : PmmlValue) : Option[xValue] = {
		val top : Option[PmmlExecNode] = ctx.pmmlExecNodeStack.top
		top match {
		  case Some(top) => {
			  if (top.isInstanceOf[xDataField]) {
				  var d : xDataField = top.asInstanceOf[xDataField]
				  d.addValuePropertyPair(node.value, node.property)
			  } else {
				  var df : xDerivedField = top.asInstanceOf[xDerivedField]
				  df.addValuePropertyPair(node.value, node.property)
			  }
		  }
		  case _ => None
		}
		None
	}

	def mkPmmlExecInterval(ctx : PmmlContext, node : PmmlInterval) : Option[PmmlExecNode] = {
		val top : Option[PmmlExecNode] = ctx.pmmlExecNodeStack.top
		top match {
			case Some(top) => {
				if (top.isInstanceOf[xDataField]) {
					  var d : xDataField = top.asInstanceOf[xDataField]
					  d.continuousConstraints(d.leftMargin, d.rightMargin, d.closure)
				} else {
					  var df : xDerivedField = top.asInstanceOf[xDerivedField]
					  df.continuousConstraints(df.leftMargin, df.rightMargin, df.closure)
				}
			}
			case _ => None
		}
		None
	}

	
	def mkPmmlExecTransformationDictionary(ctx : PmmlContext, node : PmmlTransformationDictionary) : Option[xTransformationDictionary] = {
		Some(new xTransformationDictionary(node.lineNumber, node.columnNumber))
	}

	def mkPmmlExecDerivedField(ctx : PmmlContext, d : PmmlDerivedField) : Option[PmmlExecNode] = {
		/** update the data dictionary parent node with the datafield; don't create node*/
		val top : Option[PmmlExecNode] = ctx.pmmlExecNodeStack.top
		var fld : xDerivedField = null
		top match {
		  case Some(top) => {
			  	var dict : xTransformationDictionary = top.asInstanceOf[xTransformationDictionary]
			  	fld = new xDerivedField(d.lineNumber, d.columnNumber, d.name, d.displayName, d.optype, d.dataType)
				dict.add(fld)
				ctx.xDict(fld.name) = fld
		  }
		  case _ => None
		}
		if (fld == null) {
			None
		} else {
		  Some(fld)
		}
		
	}
	
	def mkPmmlExecDefineFunction(ctx : PmmlContext, node : PmmlDefineFunction) : Option[xDefineFunction] = {  
		Some(new xDefineFunction(node.lineNumber, node.columnNumber, node.name, node.optype, node.dataType))
	}
			
	def mkPmmlExecParameterField(ctx : PmmlContext, node : PmmlParameterField) : Option[xParameterField] = {  
		Some(new xParameterField(node.lineNumber, node.columnNumber, node.name, node.optype, node.dataType))
	}
			
	def mkPmmlExecApply(ctx : PmmlContext, node : PmmlApply) : Option[xApply] = {  
		Some(new xApply(node.lineNumber, node.columnNumber, node.function, node.mapMissingTo, node.invalidValueTreatment))
	}

	def mkPmmlExecFieldRef(ctx : PmmlContext, node : PmmlFieldRef) : Option[xFieldRef] = {
		Some(new xFieldRef(node.lineNumber, node.columnNumber, node.field, node.mapMissingTo))
	}
	
	def mkPmmlExecMapValues(ctx : PmmlContext, d : PmmlMapValues) : Option[PmmlExecNode] = {
		val typ = d.dataType
		val containerStyle = d.containerStyle
		val dataSrc = d.dataSource
		
		var mapvalues = if (containerStyle == "map") {
			if (dataSrc == "inline") {
				new xMapValuesMapInline(d.lineNumber, d.columnNumber, d.mapMissingTo, d.defaultValue, d.outputColumn, d.dataType, containerStyle, dataSrc)
			  
			} else { /** locator */
				new xMapValuesMapExternal(d.lineNumber, d.columnNumber, d.mapMissingTo, d.defaultValue, d.outputColumn, d.dataType, containerStyle, dataSrc) 
			}
		} else { /** array */
			if (dataSrc == "inline") {
				new xMapValuesArrayInline(d.lineNumber, d.columnNumber, d.mapMissingTo, d.defaultValue, d.outputColumn, d.dataType, containerStyle, dataSrc)
			} else { /** locator */
				new xMapValuesArrayExternal(d.lineNumber, d.columnNumber, d.mapMissingTo, d.defaultValue, d.outputColumn, d.dataType, containerStyle, dataSrc) 
			}
		}
		Some(mapvalues)
	}
	
	def mkPmmlExecFieldColumnPair(ctx : PmmlContext, d : PmmlFieldColumnPair) : Option[xFieldColumnPair] = {
		/** update the value map node with the field col pair; don't create node*/
		val top : Option[PmmlExecNode] = ctx.pmmlExecNodeStack.top
		top match {
		  case Some(top) => {
			  	var mv :xMapValuesMap = top.asInstanceOf[xMapValuesMap]
				mv.addFieldColumnPair (d.field , d.column) 
		  }
		  case _ => None
		}
		None
	}
	
	def mkPmmlExecrow(ctx : PmmlContext, d : PmmlRow) : Option[PmmlExecNode] = {
		val top : Option[PmmlExecNode] = ctx.pmmlExecNodeStack.top
		top match {
		  case Some(top) => {
			  	var mv :xMapValuesMap = top.asInstanceOf[xMapValuesMap]

				/** Each PmmlRow has children (in this implementation we expect two values...
				 *  a key and a value.  The value maps to the MapValues output and the 
				 *  there is but one key sine we represent the inline table as a Map.
				 *  NOTE that more than one key can be present according to the spec. 
				 *  For this revision, this is not supported.  Tuple2 only.
				 */
				val tuples : ArrayBuffer[Option[PmmlRowTuple]] = d.children.filter(_.isInstanceOf[PmmlRowTuple]).asInstanceOf[ArrayBuffer[Option[PmmlRowTuple]]]
		
				val valuelen = tuples.length
				if (valuelen == 2) {
					val t0 = tuples(0)
					val k : String = t0 match {
					  case Some(t0) => t0.Value()
					  case _ => ""
					}
					val t1 = tuples(1)
					t1 match {
					  case Some(t1) => {
						  if (k.length() > 0)
							  mv.add(k,t1.Value())
					  }
					  case _ => 
					}
				} else {
					PmmlError.logError(ctx, s"Currently only 2 tuple rows (simple maps) are supported for InlineTables... row has $valuelen elements")
				}
		  }
		  case _ => None
		}
		None
	}
	
	def mkPmmlExecTableLocator(ctx : PmmlContext, node : PmmlTableLocator) : Option[PmmlExecNode] = {
		val pmmlxtensions : Queue[Option[PmmlExtension]] = node.children.filter(_.isInstanceOf[PmmlExtension]).asInstanceOf[Queue[Option[PmmlExtension]]]

		if (pmmlxtensions.length > 0) {
			val extens  = pmmlxtensions.map(x => {
				x match {
				  	case Some(x) => new xExtension(node.lineNumber, node.columnNumber, x.extender, x.name, x.value)
				  	case _ => new xExtension(node.lineNumber, node.columnNumber, "", "", "")
				}
			}).toArray
		  
			val extensions = extens.filter(_.name.length > 0)
			if (! ctx.pmmlExecNodeStack.isEmpty) {
				val top : Option[PmmlExecNode] = ctx.pmmlExecNodeStack.top
				top match {
					case Some(top) => {
						top match {
						  	case xMap : xMapValuesMap => xMap.addExtensions(extensions) 
						  	case xArr : xMapValuesArray => xArr.addExtensions(extensions) 
						  	case _ => PmmlError.logError(ctx, "Parent of TableLocator is not a kind MapValues...discarded")
						}
					}
					case _ => PmmlError.logError(ctx, "TableLocator cannot be added")
				}
			}
		}
	  
		None
	} 

	def mkPmmlExecInlineTable(ctx : PmmlContext, node : PmmlInlineTable) : Option[PmmlExecNode] = {
		val pmmlxtensions : Queue[Option[PmmlExtension]] = node.children.filter(_.isInstanceOf[PmmlExtension]).asInstanceOf[Queue[Option[PmmlExtension]]]

		if (pmmlxtensions.length > 0) {
			val extenss  = pmmlxtensions.map(x => {
				x match {
				  	case Some(x) => new xExtension(node.lineNumber, node.columnNumber, x.extender, x.name, x.value)
				  	case _ => new xExtension(node.lineNumber, node.columnNumber, "", "", "")
				}
			}).toArray
			val extensions = extenss.filter(_.name.length > 0)
			if (! ctx.pmmlExecNodeStack.isEmpty) {
				val top : Option[PmmlExecNode] = ctx.pmmlExecNodeStack.top
				top match {
					case Some(top) => {
						top match {
						  	case xMap : xMapValuesMap => xMap.addExtensions(extensions) 
						  	case xArr : xMapValuesArray => xArr.addExtensions(extensions) 
						  	case _ => PmmlError.logError(ctx, "Parent of InlineTable is not a kind MapValues...discarded")
						}
					}
					case _ => PmmlError.logError(ctx, "inline table cannot be added")
				}
			}
		}
		None
	} 

	def mkPmmlExecRuleSetModel(ctx : PmmlContext, node : PmmlRuleSetModel) : Option[xRuleSetModel] = {
		/** Gather content from RuleSetModel attributes for later use by the Scala code generator */
		ctx.pmmlTerms("ModelName") = Some(node.modelName)
		ctx.pmmlTerms("FunctionName") = Some(node.functionName)
		
		Some(new xRuleSetModel(node.lineNumber, node.columnNumber, node.modelName, node.functionName, node.algorithmName, node.isScorable))
	}
	
	def hlpMkMiningField(ctx : PmmlContext, d : PmmlMiningField) : xMiningField = {
		val name : String = d.name
		var fld : xMiningField = new xMiningField(d.lineNumber, d.columnNumber, d.name
											    , d.usageType
											    , d.optype
											    , 0.0
											    , d.outliers
											    , 0.0
											    , 0.0
											    , d.missingValueReplacement
											    , d.missingValueTreatment
											    , d.invalidValueTreatment)
		try {
			fld.Importance(d.importance.toDouble)
			fld.LowValue(d.lowValue.toDouble)
			fld.HighValue(d.highValue.toDouble)
		} catch {
			case _ : Throwable => {
        val stackTrace = StackTrace.ThrowableTraceString(_)
        ctx.logger.debug("\nStackTrace:"+stackTrace)
        ctx.logger.debug (s"Unable to coerce one or more of the mining field doubles... name = $name")}
      
		}
	  	fld
	}
	
	def mkPmmlExecMiningSchema(ctx : PmmlContext, s : PmmlMiningSchema) : Option[PmmlExecNode] = {
		s.Children.foreach((child) => {
			val ch = child.asInstanceOf[PmmlMiningField]
			mkPmmlExecMiningField(ctx, ch)
		})
		None
	}

	def mkPmmlExecMiningField(ctx : PmmlContext, d : PmmlMiningField) : Option[xMiningField] = {
		val top : Option[PmmlExecNode] = ctx.pmmlExecNodeStack.top
		top match {
		  case Some(top) => {
			  	var mf : xRuleSetModel = top.asInstanceOf[xRuleSetModel]
				mf.addMiningField (d.name , hlpMkMiningField(ctx, d)) 
		  }
		  case _ => None
		}
		None
	}
	
	def mkPmmlExecRuleSet(ctx : PmmlContext, prs : PmmlRuleSet) : Option[xRuleSet] = {
		val top : Option[PmmlExecNode] = ctx.pmmlExecNodeStack.top
		top match {
		  case Some(top) => {
			  	var rsm : xRuleSetModel = top.asInstanceOf[xRuleSetModel]
				rsm.DefaultScore(prs.defaultScore)
		  }
		  case _ => None
		}
		Some(new xRuleSet(prs.lineNumber, prs.columnNumber, prs.recordCount, prs.nbCorrect, prs.defaultScore, prs.defaultConfidence))
	}
	
	
	def mkPmmlExecSimpleRule(ctx : PmmlContext, d : PmmlSimpleRule) : Option[PmmlExecNode] = {
		var rsm : Option[xRuleSetModel] = ctx.pmmlExecNodeStack.apply(1).asInstanceOf[Option[xRuleSetModel]]
		
		val id : Option[String] = Some(d.id)
		var rule : xSimpleRule = new xSimpleRule( d.lineNumber, d.columnNumber, id
													    , d.score
													    , 0.0 /** recordCount */
													    , 0.0 /** nbCorrect */
													    , 0.0 /** confidence */
													    , 0.0) /** weight */
		rsm match {
			case Some(rsm) => {
				
				try {
					rule.RecordCount(d.recordCount.toDouble)
					rule.CorrectCount(d.nbCorrect.toDouble)
					rule.Confidence(d.confidence.toDouble)
					rule.Weight(d.weight.toDouble)
				} catch {
					case _ : Throwable => {
            val stackTrace = StackTrace.ThrowableTraceString(_)
            ctx.logger.debug("\nStackTrace:"+stackTrace)
            ctx.logger.debug (s"Unable to coerce one or more mining 'double' fields... name = $id")}
				}
			
				rsm.addRule (rule) 
			}
			case _ => None
		}
		Some(rule)
	}
	
	def mkPmmlExecScoreDistribution(ctx : PmmlContext, d : PmmlScoreDistribution) : Option[PmmlExecNode] = {
		val top : Option[PmmlExecNode] = ctx.pmmlExecNodeStack.top
		top match {
		  case Some(top) => {
			  	var mf : xSimpleRule = ctx.pmmlExecNodeStack.top.asInstanceOf[xSimpleRule]
				var sd : xScoreDistribution = new xScoreDistribution(d.lineNumber, d.columnNumber, d.value
															, 0.0 /** recordCount */
														    , 0.0 /** confidence */
														    , 0.0) /** probability */
				try {
					sd.RecordCount(d.recordCount.toDouble)
					sd.Confidence(d.confidence.toDouble)
					sd.Probability(d.probability.toDouble)
				} catch {
				  case _ : Throwable => {
            val stackTrace = StackTrace.ThrowableTraceString(_)
            ctx.logger.debug("\nStackTrace:"+stackTrace)
            ctx.logger.debug ("Unable to coerce one or more score probablity Double values")}
				}
					
				mf.addScoreDistribution(sd)
		  }
		  case _ => None
		}
		None
	}
	
	def mkPmmlExecRuleSelectionMethod(ctx : PmmlContext, d : PmmlRuleSelectionMethod) : Option[PmmlExecNode] = {
		val top : Option[PmmlExecNode] = ctx.pmmlExecNodeStack.apply(1)   // xRuleSetModel is the grandparent
		top match {
		  case Some(top) => {
			  	var mf : xRuleSetModel = top.asInstanceOf[xRuleSetModel]
			  	var rsm : xRuleSelectionMethod = new xRuleSelectionMethod(d.lineNumber, d.columnNumber, d.criterion)
				mf.addRuleSetSelectionMethod(rsm)
		  }
		  case _ => None
		}
		None
	}

	def mkPmmlExecArray(ctx : PmmlContext, a : PmmlArray) : Option[xArray] = {
		val valStr : String = a.valueString
		val elements = valStr.split(' ').toArray
		var trimmedElems  = elements.map (e => e.trim)
		val arrayPattern = """\"(.*)\"""".r
		
		val sanQuotesTrimmedElems = trimmedElems.map { x =>
			val matched = arrayPattern.findFirstMatchIn(x)
			matched match {
			  case Some(m) => m.group(1)
			  case _ => x
			}
		}
 		
		var arr : xArray = new xArray(a.lineNumber, a.columnNumber, a.n, a.arrayType, sanQuotesTrimmedElems)
		Some(arr)
	}

	def mkPmmlExecSimplePredicate(ctx : PmmlContext, d : PmmlSimplePredicate) : Option[xSimplePredicate] = {  
		Some(new xSimplePredicate(d.lineNumber, d.columnNumber, d.field, d.operator, d.value))
	}
	
	def mkPmmlExecSimpleSetPredicate(ctx : PmmlContext, d : PmmlSimpleSetPredicate) : Option[xSimpleSetPredicate] = {
		Some(new xSimpleSetPredicate(d.lineNumber, d.columnNumber, d.field, d.booleanOperator))
	}
	
	def mkPmmlExecCompoundPredicate(ctx : PmmlContext, d : PmmlCompoundPredicate) : Option[xCompoundPredicate] = {
		Some(new xCompoundPredicate(d.lineNumber, d.columnNumber, d.booleanOperator))
	}

	def prepareFieldReference(ctx : PmmlContext, field : String, order : Traversal.Order = Traversal.PREORDER) : String = {

		val fieldRefStr : String = order match {
			case Traversal.INORDER => { "" }
			case Traversal.POSTORDER => { "" }
			case Traversal.PREORDER => {

				/** prepare a field usage reference for the supplied field */
			  
				val fieldNodes : Array[String] = field.split('.')
				val possibleContainer : String = fieldNodes.head
				val expandCompoundFieldTypes : Boolean = true
				/** See the types for the containers with this to educate ourselves about it */
				val explodedFieldTypes : Array[(String,Boolean,BaseTypeDef)] = ctx.getFieldType(field, expandCompoundFieldTypes)

				/** Verify outer level container is a defined container in the pmml... */
				val isContainerInScope : Boolean = ctx.containersInScope.filter( tup4 => {
					possibleContainer == tup4._1 
				}).length > 0
				if (isContainerInScope) {
					val containerInfo : Option[(String, Boolean, BaseTypeDef, String)] = ctx.containersInScope.find( p => p._1 == possibleContainer)

					val (container, typeStr) : (BaseTypeDef, String) = containerInfo match {
					  case Some(containerInfo) => (containerInfo._3, containerInfo._3.typeString)    //containerType.typeString)
					  case _ => (null,"")
					}
					if (typeStr != "") {
						val varRefBuffer : StringBuilder = new StringBuilder
						val dataValueType : String = PmmlTypes.scalaTypeToDataValueType(typeStr)
						varRefBuffer.append(s"ctx.valueFor(${'"'}$possibleContainer${'"'}).asInstanceOf[$dataValueType]")
						/** Handle arbitrarily nested fields e.g., container.sub.subsub.field */
						if (fieldNodes.size > 1) { 
							val containerField : String= fieldNodes(1)
							val fieldsContainerType : ContainerTypeDef = container.asInstanceOf[ContainerTypeDef]
							
							/** lastContainer is non null when explodedFieldTypes.last is a container type.  It is stacked for use for determining field dereference 
							 *  expression print... either a fixed print ( container.fld ) or "mapped" print ( container.get("fld") )
							 */
							val containerFieldRep : String = compoundFieldPrint(fieldsContainerType, fieldNodes.tail, explodedFieldTypes.tail)

							varRefBuffer.append(s".Value.asInstanceOf[$typeStr]$containerFieldRep")
						} else {
							varRefBuffer.append(s".Value.asInstanceOf[$typeStr]")
						}
						
						varRefBuffer.toString
		
					} else {
						field
					}
				} else {
					/** 
					 *  With the container dereference case handled above, we can concern ourselves with only
					 *  the simple fields and maps or arrays of same. Ask the context to determine the type
					 *  and generate the correct coercion string suffix for the field access.
					 *  
					 *  FIXME: derived variables can also be dereferenced.  
					 */
					val expandCompoundFieldTypes : Boolean = true
					val typeStrs : Array[(String,Boolean,BaseTypeDef)] = ctx.getFieldType(field, ! expandCompoundFieldTypes)
					if (typeStrs != null && typeStrs.size > 0) {
						val typeStr : String = typeStrs(0)._1
						if (typeStr != "Unknown") {
							val dataValueType : String = PmmlTypes.scalaTypeToDataValueType(typeStr)
							if (dataValueType == "AnyDataValue")
								s"ctx.valueFor(${'"'}$field${'"'}).asInstanceOf[$dataValueType].Value.asInstanceOf[$typeStr]"
							else
								s"ctx.valueFor(${'"'}$field${'"'}).asInstanceOf[$dataValueType].Value"
						} else {
							PmmlError.logError(ctx, "Field '$field' is not known in either the data or transaction dictionary... fix this ")
							s"ctx.valueFor(${'"'}$field${'"'})"
						}
					} else {
						PmmlError.logError(ctx, s"Field reference '$field' did not produce any type(s)... is it mis-spelled?")
						
						s"UNKNOWN FIELD $field"
					}
				}
			}
		}
		fieldRefStr
	}

	/**
		Print the remaining field nodes in the supplied array, coercing them to their corresponding type.  The outer
		field node (a variable in one of the pmml dictionaries) is handled by the caller.	For the container types,
		generate the appropriate mapped (.apply(field)) or fixed (.field) access expression	
						
		@param fieldsContainerType the container type that contains this field, used to decide field dereference
			syntax (mapped vs. fixed)
		@param fieldNodes an array of the names found in a container qualified field reference (e.g., the 
			container.sub.subsub.fld of a field reference like msg.container.sub.subsub.fld)
		@param the type information corresponding to the fieldNodes.
		@return a string rep for the compound field
							
	 */	
						
	def compoundFieldPrint(fieldsContainerType : ContainerTypeDef
						, fieldNodes : Array[String]
						, explodedFieldTypes : Array[(String,Boolean,BaseTypeDef)]) : String = {
		val varRefBuffer : StringBuilder = new StringBuilder
		 
		fieldNodes.zip(explodedFieldTypes).foreach ( tuple => {
			val (field, typeInfo) : (String, (String,Boolean,BaseTypeDef)) = tuple
			val (typeStr, isContainerWFields, baseType) : (String,Boolean,BaseTypeDef) = typeInfo
			
			val isMappedContainer : Boolean = if (baseType != null) baseType.isInstanceOf[MappedMsgTypeDef] else false
			if (isMappedContainer) {
				varRefBuffer.append(s".get(${'"'}$field${'"'}).asInstanceOf[$typeStr]")			  
			} else {
				if (! fieldsContainerType.IsFixed) {
					varRefBuffer.append(s".get(${'"'}$field${'"'}).asInstanceOf[$typeStr]")
				} else {
					varRefBuffer.append(s".$field")
				}
			}
		})
		
		val container : ContainerTypeDef = if (explodedFieldTypes.last._3 != null && explodedFieldTypes.last._3.isInstanceOf[ContainerTypeDef]) {
			explodedFieldTypes.last._3.asInstanceOf[ContainerTypeDef]
		} else {
			null
		}
			
		varRefBuffer.toString
	}

}



