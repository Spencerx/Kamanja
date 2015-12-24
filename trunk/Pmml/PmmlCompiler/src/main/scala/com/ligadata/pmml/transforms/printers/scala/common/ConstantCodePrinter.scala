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

package com.ligadata.pmml.transforms.printers.scala.common

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


class ConstantCodePrinter(ctx : PmmlContext) extends CodePrinter with com.ligadata.pmml.compiler.LogTrait {

	/**
	 *  Answer a string (code representation) for the supplied node.
	 *  @param node the PmmlExecNode
	 *  @param the CodePrinterDispatch to use should recursion to child nodes be required.
 	    @param the kind of code fragment to generate...any 
 	    	{VARDECL, VALDECL, FUNCCALL, DERIVEDCLASS, RULECLASS, RULESETCLASS , MININGFIELD, MAPVALUE, AGGREGATE, USERFUNCTION}
	 *  @order the order to traverse this node...any {INORDER, PREORDER, POSTORDER} 
	 *  
	 *  @return some string representation of this node
	 */
	def print(node : Option[PmmlExecNode]
			, generator : CodePrinterDispatch
			, kind : CodeFragment.Kind
			, order : Traversal.Order) : String = {

		val xnode : xConstant = node match {
			case Some(node) => {
				if (node.isInstanceOf[xConstant]) node.asInstanceOf[xConstant] else null
			}
			case _ => null
		}

		val printThis = if (xnode != null) {
			asString(ctx, xnode)
		} else {
			if (node != null) {
				PmmlError.logError(ctx, s"For ${xnode.qName}, expecting an xConstant... got a ${xnode.getClass.getName}... check CodePrinter dispatch map initialization")
			}
			""
		}
		printThis
	}
	

	private def asString(ctx : PmmlContext, xnode : xConstant) : String = {
	  	val strRep : String = xnode.value.toString
	  	xnode.dataType match {
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
	  		case "mbrTypename"  => { 
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