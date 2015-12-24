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
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.kamanja.metadata._
import com.ligadata.pmml.compiler._
import com.ligadata.pmml.support._
import com.ligadata.pmml.traits._
import com.ligadata.pmml.syntaxtree.cooked.common._

/** 
 *	Print an iterable function.  General print form is 
 * 
 * 		collectionFromArg1.<fcnName>( mbr => {
 *   		mbrFunction(arg1.arg2,...)
 *   	})
 *    
 *  except all on one line.
 *  
 *  For those iterable functions without an iterable function ...
 *  
 *  	collectionFromArg1.<fcnName>( mbr => {
 *   		(arg1.arg2,...)
 *   	})
 *    
 *  If only one argument, the parentheses are omitted
 *  
 *  @param fcnName - the top level function name (before possible translation)
 *  @param node - the original syntax tree node
 *  @param ctx - the PmmlContext for this compilation
 *  @param generator - the print navigation control for printing syntax trees
 *  @param order - the child navigation order supplied by the generator responsible for orchestration of the print
 *  @param iterableFcn - the metadata that describes the selected iterable function (this is the outer function)
 *  @param iterableArgTypes - the type information collected for the iterableFcn (and its first arg, the collection)
 *  @param mbrFcn - the member function metadata for the function that will operate on the collection's members (this may be null)
 *  @param mbrArgTypes - the member function's argument types
 *  @param collectionType - the type of the first argument of the apply function - some kind of iterable
 *  @param collectionsElementTypes - an array of base types that describe the types used to describe the collection content
 *  	(e.g., the element type for a List, the key and value for a map, etc.)
 */
class IterableFcnPrinter(val fcnName : String
						, val node : xApply
					    , val ctx : PmmlContext
					    , val generator : CodePrinterDispatch
					    , val generate : CodeFragment.Kind
					    , val order : Traversal.Order
					    , val fcnTypeInfo :  FcnTypeInfo) extends com.ligadata.pmml.compiler.LogTrait {
					    
	/** 
	 *  Iterable functions are comprised of three pieces:
	 *  
	 *  1) The receiver Iterable collection and the scala function that corresponds to the pmml apply name along with
	 *  	the mbr variable to used by the member function or projection. For example,
	 *  	
	 *   		coll.map(itm =>
	 *   
	 *  2) The member function that uses the member variable.  For example a "Between" function:
	 *  
	 *  		{ Pmml.Between(itm.intfield, 2012, 2014, true) }
	 *    
	 *  3) The coercion or casting of the result to the appropriate type.. say from an Array[Any] that executed
	 *  	to the array of the elements that are returned by the Between function variant selected:
	 *   
	 *   		).asInstanceOf[Array[Int]]
	 */
	def print(fcnBuffer : StringBuilder) : Unit = {
  
		/** push the function type info state on stack for use by xConstant printer when handling 'ident' types */
		if (node.typeInfo != null && node.typeInfo.fcnTypeInfoType != FcnTypeInfoType.SIMPLE_FCN) {
			ctx.fcnTypeInfoStack.push(node.typeInfo)
		} else {
			if (node.typeInfo == null) {
				PmmlError.logError(ctx, "trying to print an iterable function without the typeinfo available... things are going badly and will get worse")
			}
			if (node.typeInfo != null && node.typeInfo.fcnTypeInfoType == FcnTypeInfoType.SIMPLE_FCN) {
				PmmlError.logError(ctx, "trying to print an iterable function with simple function typeinfo ... logic issue.")
			}
		}
	  
		val iterablePart : String = iterablePrint
		val mbrFcnPart : String = mbrFunctionPrint
		val castPrint : String = castResult
		
		if (node.typeInfo != null && node.typeInfo.fcnTypeInfoType != FcnTypeInfoType.SIMPLE_FCN && ctx.fcnTypeInfoStack.nonEmpty) {
			ctx.fcnTypeInfoStack.pop
		} 

		/** fcnBuffer.append(s"$iterablePart${'{'} $mbrFcnPart ${'}'}$castPrint") <<< no CAST needed thus far */
		fcnBuffer.append(s"$iterablePart${'{'} $mbrFcnPart ${'}'})")
		
		val fcnUse : String = fcnBuffer.toString
		logger.debug(s"IterableFcnPrint... fcn use : $fcnUse")
		val huh : String = "huh" // debugging rest stop 
	}

	/**
	 * 	Print the iterable argument (the receiver) and its function with the mbr variable expression 
	 *  
	 *  e.g., "coll.map(_itm =>"
	 */
	private def iterablePrint : String = {
		val scalaFcnName : String = iterableFcnNameToPrint(fcnName)
		val iterArgBuffer : StringBuilder = new StringBuilder
		val iterChild : PmmlExecNode = node.Children.head
		
		generator.generate(Some(iterChild), iterArgBuffer, CodeFragment.FUNCCALL)
		iterArgBuffer.append(s".$scalaFcnName( ${ctx.applyElementName} => ")
		
		iterArgBuffer.toString
	}
	
	/**
	 * 	Print the member function that uses the member variable.  For example a "Between" function:
	 *  
	 *  		{ Pmml.Between(_each.intfield, 2012, 2014, true) }
	 *  
	 *  It is also possible that there is no member function, only field references or constant expressions
	 *  This is common with the 'map' function when a simple projection is done on one or more fields.
	 *  If more than one field is found in this situation, a tuple is returned with for the mentioned fields
	 *  and constants. 
	 *  			between(Admtng_Icd9_Dgns_Cd"49300""49392"true)
	 *  @return string representation of the member function or projection of the variables
	 */
	private def mbrFunctionPrint : String =  {
		val mbrFcnBuffer : StringBuilder = new StringBuilder
		val (firstElemArgIdx, lastElemArgIdx) : (Int,Int) = fcnTypeInfo.elemFcnArgRange
		val hasMbrFcn : Boolean = (fcnTypeInfo.mbrFcn != null)
		val isIterableFcn : Boolean = if (hasMbrFcn) fcnTypeInfo.mbrFcn.isIterableFcn else false
		if (hasMbrFcn) {
			if (isIterableFcn) {
				val iterableName : String = fcnTypeInfo.mbrFcn.name
				val scalaName : String = iterableFcnNameToPrint(iterableName)			
				mbrFcnBuffer.append(s"${ctx.applyElementName}.$scalaName( ${ctx.applyElementName} => { (")
			} else {
				mbrFcnBuffer.append(s"${fcnTypeInfo.mbrFcn.physicalName}(")
			}
			val firstElemArgIdxActually : Int = if (isIterableFcn) (firstElemArgIdx + 1) else firstElemArgIdx
			if ((lastElemArgIdx - firstElemArgIdxActually) >= 0) { 
				for (i <- firstElemArgIdxActually to lastElemArgIdx) {
					val child : PmmlExecNode = node.Children.apply(i)
					generator.generate(Some(child), mbrFcnBuffer, CodeFragment.FUNCCALL)
					if (i < lastElemArgIdx) {
						mbrFcnBuffer.append(", ")
					}
				}
			} else {
				logger.warn(s"There are no arguments for this function ... ${fcnTypeInfo.mbrFcn.Name}")
			}
			/** Enclosing tuple on an iterable... should iterable mbrfcns have their own mbr fcns, this will need to change */
			if (isIterableFcn) {
				mbrFcnBuffer.append(")})") 
			} else {
				mbrFcnBuffer.append(")")
			}
		} else {
			val mbrFcnNm : String = if (fcnTypeInfo.mbrFcn != null) fcnTypeInfo.mbrFcn.Name else ""
			if ((lastElemArgIdx - firstElemArgIdx) >= 0) {
				val howManyInProjection = (lastElemArgIdx - firstElemArgIdx) + 1

				if (howManyInProjection > 1) {
					mbrFcnBuffer.append("(")
				}
				
				for (i <- firstElemArgIdx to lastElemArgIdx) {
					val child : PmmlExecNode = node.Children.apply(i)
					generator.generate(Some(child), mbrFcnBuffer, CodeFragment.FUNCCALL)
					if (i < lastElemArgIdx) {
						mbrFcnBuffer.append(", ")
					}
				}
				
				if (howManyInProjection > 1) {
					mbrFcnBuffer.append(")")
				}
				
			}  else {
				PmmlError.logError(ctx, s"Not only are there There are no arguments.. is there no function too? ... '$mbrFcnNm'... investigate this.")
			}
		}
		mbrFcnBuffer.toString
	}
	
	private def castResult : String = {
	  
		val tc : TypeCollector = new TypeCollector(fcnTypeInfo)
		val fcnReturnType : String = tc.determineReturnType(node)
		val buffer : StringBuilder = new StringBuilder
		buffer.append(s").asInstanceOf[$fcnReturnType]")
		val castStr : String = buffer.toString
		castStr
	}
		
	/** 
	 *  Answer the appropriate function name to print for this iterable function.  There are several possible outcomes:
	 *  
	 *  """
	 *  	a. if the function is an iterable (e.g., ContainerMap, ContainerFilter, et al, the map, filter name is substituted.
	 *   	b. if the function is namespace alias qualified, the namespace from the global context search map is substituted for the alias.
	 *  """
	 *  @param fcnName the name to consider 
	 *  @return the function string to print 
	 */
	private def iterableFcnNameToPrint(fcnName : String) : String = {
		var scalaFcnName : String = PmmlTypes.scalaNameForIterableFcnName(fcnName)
		if (scalaFcnName == null) {
			scalaFcnName = if (node.function.contains(".")) { /** NOTE: builtin case above cannot match this predicate... by definition */
				/** substitute the alias present with the corresponding namespace (i.e., full pkg) name */
				val alias : String = node.function.split('.').head.trim
				val fnName : String = node.function.split('.').last.trim
				val nmspc : String = if (ctx.NamespaceSearchMap.contains(alias)) {
					ctx.NamespaceSearchMap(alias).trim
				} else {
					PmmlError.logError(ctx, s"Function ${node.function}'s namespace alias could not produce a namespace from the map... logic/coding error in ctx!")
					alias
				}
				s"$nmspc.$fnName"
			} else {
				scalaFcnName
			}
			
		}
		scalaFcnName
	}
	

}

