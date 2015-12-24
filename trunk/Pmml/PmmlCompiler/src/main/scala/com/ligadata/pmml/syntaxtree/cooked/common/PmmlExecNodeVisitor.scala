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

import scala.collection.mutable.Stack
import scala.collection.immutable.Set
import scala.util.control.Breaks._
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.kamanja.metadata._
import com.ligadata.pmml.compiler._


trait PmmlExecVisitor {
	
	def Visit(node : PmmlExecNode, navStack : Stack[PmmlExecNode])
	
}

/** 
 *  										[(message name, (appears in ctor signature?, message type for named message, varName))] 
 *  var containersInScope : ArrayBuffer[(String, Boolean, BaseTypeDef, String)] = ArrayBuffer[(String, Boolean, BaseTypeDef, String)]()
 *  
 *  (model var namespace (could be msg or concept or modelname), variable name, type namespace
   *  					, type name, and whether the variable is global (a concept that has been independently cataloged))
 *  
 */


class ContainerFieldRefCollector(ctx : PmmlContext) extends PmmlExecVisitor {
  
	override def Visit(node : PmmlExecNode, navStack : Stack[PmmlExecNode]) {
		val expandCompoundFieldTypes : Boolean = true
		node match {
		  case x : xFieldRef => {
			  val fldRef : xFieldRef = node.asInstanceOf[xFieldRef]
			  val fieldTypeInfo : Array[(String,Boolean,BaseTypeDef)] = ctx.getFieldType(fldRef.field, expandCompoundFieldTypes)
			  if (fieldTypeInfo != null) {
				  fieldTypeInfo.foreach(triple => {
					  val (typeStr, isContainer, typedef) : (String,Boolean,BaseTypeDef) = triple
					  val isAnyKindOfContainer : Boolean = (typedef != null && typedef.isInstanceOf[ContainerTypeDef])
					  val isStructOrMappedType : Boolean = ctx.mdHelper.isContainerWithFieldOrKeyNames(typedef)
					  if (isStructOrMappedType) {
						  val key = fldRef.field.toLowerCase + "." + typeStr
						  ctx.modelInputs(key) = (fldRef.field, typeStr, typedef.NameSpace, typedef.Name, false, null)
					  } else {
						  val containerOrMessageMemberTypes : Array[BaseTypeDef] = ctx.mdHelper.collectMemberContainerTypes(typedef)
						  if (containerOrMessageMemberTypes.size > 0) {
							  containerOrMessageMemberTypes.foreach(typ => {
								  val key = fldRef.field.toLowerCase + "." + typ.typeString
								  ctx.modelInputs(key) = (fldRef.field, typ.typeString, typ.NameSpace, typ.Name, false, null)
							  })
							  
						  }
					  }
				    
				  })
			    
			  }			  
		  }
		  case _ => None
		}
	}
}

class UdfCollector(ctx : PmmlContext) extends PmmlExecVisitor {
  
	override def Visit(node : PmmlExecNode, navStack : Stack[PmmlExecNode]) {
		node match {
		  case x : xDefineFunction => {
			  ctx.UdfMap(x.name) = x
		  }
		  case _ => None
		}
	}
}



class RuleSetModelCollector(ctx : PmmlContext) extends PmmlExecVisitor {
  
	override def Visit(node : PmmlExecNode, navStack : Stack[PmmlExecNode]) {
		node match {
		  case x : xRuleSetModel => {
			  val srs : xRuleSetModel = x.asInstanceOf[xRuleSetModel]
			  ctx.DefaultScore(srs.DefaultScore)
			  ctx.RuleSetSelectionMethods(srs.ruleSetSelectionMethods)
		  }
		  case _ => None
		}
	}
}

class SimpleRuleCollector(ctx : PmmlContext) extends PmmlExecVisitor {
  
	override def Visit(node : PmmlExecNode, navStack : Stack[PmmlExecNode]) {
		node match {
		  case x : xSimpleRule => {
			  val sr : xSimpleRule = x.asInstanceOf[xSimpleRule]
			  ctx.RuleScoreDistributions(sr.scoreDistributions)
		  }
		  case _ => None
		}
	}
}

object FieldIdentifierSyntaxChecker {
	val legalChars : Set[Char] = ".ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz0123456789".toSet
}

class FieldIdentifierSyntaxChecker(ctx : PmmlContext) extends PmmlExecVisitor {
  
	override def Visit(node : PmmlExecNode, navStack : Stack[PmmlExecNode]) {
		node match {
		  case x : xDataField => {
			  val df : xDataField = x.asInstanceOf[xDataField]
			  checkNameIdentifier(df.name, node)
			  checkIfUniqueIdentifier(df.name, node)
			  checkBaseTypeIdentifier(df.dataType, node)
		  }
		  case x : xDerivedField => {
			  val df : xDerivedField = x.asInstanceOf[xDerivedField]
			  checkNameIdentifier(df.name, node)
			  checkIfUniqueIdentifier(df.name, node)
			  checkBaseTypeIdentifier(df.dataType, node)
		  }
		  case x : xFieldRef => {
			  val fr : xFieldRef = x.asInstanceOf[xFieldRef]
			  checkNameIdentifier(fr.field, node)
		  }
		  case x : xConstant => {
			  val c : xConstant = x.asInstanceOf[xConstant]
			  val id : String = c.dataType.toLowerCase
			  if (id == "ident" || id == "typename" || id == "mbrtypename") {
				  checkNameIdentifier(c.Value.toString, node)
				  if (id == "ident") {
					  checkIfValidContainerField(c.Value.toString, node, navStack)
				  }
			  }
		  }
		  case _ => None
		}
	}
	
	/** 
	 *  Log error if the identifier contains bogus characters unsuitable for mdmgr lookup.
	 *  
	 *  syntax is:
	 *  """
	 *  	op       ::=  opchar {opchar}
	 *  	idrest   ::=  {letter | digit} [‘_’ op]
	 *  """
	 *  
	 *  @param ident : String the name of a data field or derived field
	 *  @param node : the node being examined.
	 */
	def checkNameIdentifier(ident : String, node : PmmlExecNode) : Unit = {
		val identLen : Int = if (ident != null) ident.size else 0
		val legalCnt : Int = ident.filter(c => FieldIdentifierSyntaxChecker.legalChars.contains(c)).size
		if (identLen == 0 || identLen > legalCnt) {
			ctx.logger.error(s"Name identifer '$ident' found in ${node.toString} is not a legal identifer... it contains one or more illegal characters")
			ctx.IncrErrorCounter
		} 
	}
	
	/** 
	 *  Log error if the identifier is not unique... it should only exist in DataDictionary 
	 *  or TransformationDictionary, not both.
	 *  @param ident : String the name of a data field or derived field
	 *  @param node : the node being examined.
	 */
	def checkIfUniqueIdentifier(ident : String, node : PmmlExecNode) : Unit = {
		if (ctx.DataDict.contains(ident) && ctx.TransformDict.contains(ident)) {
			ctx.logger.error(s"Name identifer '$ident' found in ${node.toString} is not a legal identifer... two or more Data or Derived fields have this name")
			ctx.IncrErrorCounter
		}
	}
	
	/** 
	 *  Log error if the type identifier contains bogus characters unsuitable for mdmgr lookup.
	 *  
	 *  syntax is:
	 *  """
	 *  	op       ::=  opchar {opchar}
	 *  	idrest   ::=  {letter | digit} [‘_’ op]
	 *  """
	 *  
	 *  @param ident : String the name of a data field or derived field
	 *  @param node : the node being examined.
	 */
	def checkBaseTypeIdentifier(typeid : String, node : PmmlExecNode) : Unit = {
		val identLen : Int = if (typeid != null) typeid.size else 0
		val legalCnt : Int = typeid.filter(c => FieldIdentifierSyntaxChecker.legalChars.contains(c)).size
		if (identLen == 0 || identLen > legalCnt) {
			ctx.logger.error(s"Type identifer '$typeid' found in ${node.toString} is not a legal identifer... it contains one or more illegal characters")
			ctx.IncrErrorCounter
		} 		
	}
	
	/** 
	 *  Log error if the supplied constant with dataType="ident" is actually a field in the 
	 *  iterable argument nearby (available via the navStack).
	 *  @param ident : String the name of a data field or derived field
	 *  @param node : the node being examined.
	 *  @param navStack : current stack of nodes above this one
	 */
	def checkIfValidContainerField(ident : String, node : PmmlExecNode, navStack : Stack[PmmlExecNode]) : Unit = {
		/** obtain the type of the companion iterable (only FieldRef supported at the moment) */
		val iterablesElem : PmmlExecNode = if (navStack != null && navStack.size > 0 && navStack.top.Children.length > 0) {
			navStack.top.Children(0)
		} else {
			null
		}
		
		/** avoid the iterable when collections of collections appear ... */
		if (ident!= null && ident.trim.toLowerCase == ctx.applyElementName) {
			return
		}
		/** 
		 *  FIXME: subclassed collections are not handled here... fix this if such use case comes up. Superclass introspection
		 *  would be required here. 
		 *  FIXME: When functions are permitted in the Iterable position, then we should just pass on this check and wait
		 *  until the full function type inference is performed. 
		 *  
		 *  NOTE: Nested collections are possible (e.g., Array[Array[Tuple6[Any,Any,Any,Any,Any,Any]])
		 *  Dive to the StructTypeDef or MappedMsgTypeDef elemType 
		 */
		var passedInitialSmellTest : Boolean = true
		var notACollection : Boolean = false
		var isAMap : Boolean = false
		val isValidField : Boolean = if (ident != null && iterablesElem != null && iterablesElem.isInstanceOf[xFieldRef]) {
			val fldRef : xFieldRef = iterablesElem.asInstanceOf[xFieldRef]
			val fldNames : Array[String] = fldRef.field.split('.')
			val expandCompoundFieldTypes : Boolean = false
			val fldInfo : Array[(String,Boolean,BaseTypeDef)] = ctx.mdHelper.getFieldType(fldNames, expandCompoundFieldTypes)
			val (fldType, isContainer, typedef) : (String,Boolean,BaseTypeDef) = if (fldInfo.size > 0) fldInfo.last else (null,false,null)
			val ifvalid : Boolean = if (isContainer && (fldType.contains("scala.Array") || fldType.contains("scala.collection"))) {
				val containerTypeDef : ContainerTypeDef = typedef.asInstanceOf[ContainerTypeDef]
				val elementTypes : Array[BaseTypeDef] = containerTypeDef.ElementTypes

				val firstElemType : BaseTypeDef = FirstElementNonCollectionType(elementTypes.head)
				
				if (firstElemType.isInstanceOf[StructTypeDef]) {
					(firstElemType.asInstanceOf[StructTypeDef].attributeFor(ident) != null)
				} else {
					if (firstElemType.isInstanceOf[MappedMsgTypeDef]) {
						(firstElemType.asInstanceOf[MappedMsgTypeDef].attributeFor(ident) != null)
					} else {
						false
					}
				}
			} else {
				isAMap = (fldType.contains("scala.collection") && fldType.contains("Map"))
				notACollection = true
				false
			}
			ifvalid
		} else {
			passedInitialSmellTest = false
			false
		}
		
		if (! isValidField && passedInitialSmellTest) {
			val notCollMsg : String = if (notACollection) " the found Iterable is not a collection..." else ""
			val isAMapMsg : String = if (isAMap) " Map Iterable is not supported..." else ""

			if (notCollMsg.size > 0) {
				ctx.logger.error(s"Field identifer '$ident' found in ${node.toString} is not a legal identifer... $notCollMsg")
			}
			if (isAMapMsg.size > 0) {
				ctx.logger.error(s"Field identifer '$ident' found in ${node.toString} is not a legal identifer... $isAMapMsg")
			}
			
			val badFieldRef : String = if (notCollMsg.size == 0 && isAMapMsg.size == 0) {
				"this field is not found in the iterable's member type"
			} else ""
			  
			if (badFieldRef.size > 0) {
				ctx.logger.error(s"Field identifer '$ident' found in ${node.toString} is not a legal identifer... $badFieldRef")
			}
			ctx.logger.error("    See the Iterable function documentation in the wiki for details. ")
			
			ctx.IncrErrorCounter
		} else {
			if (! passedInitialSmellTest) {
				val identStr : String = if (ident != null) ident else "<NO IDENT SUPPLIED>"
				val noParentStr : String = if (iterablesElem != null) iterablesElem.toString else "<No iterable function found here>"
				ctx.logger.error(s"Field identifer '$identStr' found in $noParentStr is unacceptable... ")
				ctx.IncrErrorCounter
			}
		}
	}
	
	/** Answer the first structure candidate ... something that is "not collection".  It had better be
	 *  some StructTypeDef or MappedMsgTypeDef or it will be considered bogus.
	 *  @param candidate a BaseTypeDef that possibly is a StructTypeDef or MappedMsgTypeDef .. or a collection of same
	 *  @return true candidate (a non-collection type)
	 */
	def FirstElementNonCollectionType(candidate : BaseTypeDef) : BaseTypeDef = {				  
		val structureCandidate : BaseTypeDef = if (candidate == null) { 
			null
		} else {
			val typeStr : String = candidate.typeString
			val sCandidt : BaseTypeDef = if (typeStr.contains("scala.Array") || typeStr.contains("scala.collection")) {
				val firstBaseType : BaseTypeDef = candidate.asInstanceOf[ContainerTypeDef].ElementTypes.head
				FirstElementNonCollectionType(firstBaseType)  /** if nested ... recurse */
			} else {
				candidate
			}
			sCandidt
		}	
		structureCandidate
	}
}


/** Derived fields in the transformation dictionary with 'if' functions are processed here.
 *  The if statement's true and false actions are removed from the child list for the node and placed
 *  in the apply (if) function's that contains categorized value array.  
 *  
 *  These actions are rendered after the 'if' predicate is computed.  NOTE: Only the 'top' level
 *  applies are treated this way.  If there are if's nested at lower levels in a nested set of apply elements,
 *  they will be rendered in place.  That sort of expression is untested at this point, but we need to support it.
 */

class IfActionTransform (ctx : PmmlContext) extends PmmlExecVisitor {
  
	override def Visit(node : PmmlExecNode, navStack : Stack[PmmlExecNode]) {
		node match {
		  case x : xDerivedField => {
			  if (IfActionPresent(node)) {
				  stripIfActionsFromApplysChildren(node)
			  }
		  }
		  case _ => None
		}
	}
	
	def IfActionPresent(node : PmmlExecNode) : Boolean = {
 
		val thisIsDerivedField = if (node.isInstanceOf[xDerivedField]) true else false
		var ifActionExprCnt = 0
		if (thisIsDerivedField) {
			val derivedFld : xDerivedField = node.asInstanceOf[xDerivedField]
			
			val thisDerivedFieldHasApply : Boolean = if (derivedFld.Children.length == 1 && 
			    								derivedFld.Children.head.isInstanceOf[xApply]) 
															{ true } else { false }
			ifActionExprCnt = if (thisDerivedFieldHasApply) {
				val topLevelApply : xApply = derivedFld.Children.head.asInstanceOf[xApply]
				val childCnt : Int = topLevelApply.Children.length
				/** 
				 *  If the top level function is one of our udfs and could have numerous arguments... 
				 *  make sure they are preserved. 
				 */
				val fcn : String = topLevelApply.function
				val (minParmsNeeded, maxParmsNeeded) : (Int,Int) = ctx.MetadataHelper.minMaxArgsForTheseFcns(fcn)
				
				if (fcn == "if" && (childCnt - maxParmsNeeded) >= 2) { 
					
					ctx.logger.debug(s"categorizedReturnValuesPresent() - topLevelApply 'if' fcn = $fcn")
					
					2
				} else {
					0
				}
			} else {
				0
			}
		}
		if (ifActionExprCnt > 0) true else false
	}
	
	/** 
	 *  Strip the if action elements from the supplied 'if' apply node leaving just the predicate, reserving them 
	 *  in the apply's IfAction array.
	 *  
	 *  pre-condition : IfActionPresent(node) == true
	 */
	private def stripIfActionsFromApplysChildren(node : PmmlExecNode) : Unit = {
		val topLevelApply : xApply = node.Children.head.asInstanceOf[xApply]
		val n : Int = topLevelApply.Children.length - 2
		val (children, catValues) = topLevelApply.Children.splitAt(n)
		catValues.foreach((value) => {
			val aNode : PmmlExecNode = value.asInstanceOf[PmmlExecNode]
			topLevelApply.addIfAction(aNode)
		}) 
		topLevelApply.replaceChildren(children)
	}

}

object PmmlExecNodeVisitor {  

	def Visit (rootNode : Option[PmmlExecNode], visitor : PmmlExecVisitor) {
		var navStack  = new Stack[PmmlExecNode]()
		rootNode match {
		  case Some(rootNode) => rootNode.Visit(visitor, navStack)
		  case _ => None
		}
	}
}


