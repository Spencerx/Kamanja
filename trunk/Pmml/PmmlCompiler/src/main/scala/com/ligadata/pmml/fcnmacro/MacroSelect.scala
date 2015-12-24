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

package com.ligadata.pmml.fcnmacro

import scala.collection.mutable._
import scala.math._
import scala.collection.immutable.StringLike
import scala.util.control.Breaks._
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.kamanja.metadata._
import com.ligadata.pmml.compiler._
import com.ligadata.pmml.traits._
import com.ligadata.pmml.syntaxtree.cooked.common._
import com.ligadata.pmml.support._
import com.ligadata.Exceptions.StackTrace

/**
 * class MacroSelect retrieves a MacroDef from the mdmgr based upon the function signature.
 * Since the Macro is so similar to the function, a FunctionSelect is supplied to service
 * key formation and relaxation.
 */
class MacroSelect(val ctx : PmmlContext
				, val mgr : MdMgr
				, val node : xApply
				, val generator : CodePrinterDispatch
				, val fcnSelector : FunctionSelect)  extends com.ligadata.pmml.compiler.LogTrait {
							    
  
	def selectMacro : (MacroDef, Array[(String,Boolean,BaseTypeDef)]) = {
	  	/** create a search key from the function name and its children (the arguments). */
	  	val expandingContainerFields = true
	  	
	  	if (fcnSelector.isIterableFcn) {
	  		return (null,null)
	  	}

	  	/** debug helper */
	  	val ofInterest : Boolean = (node.function == "setField")
	  	if (ofInterest) {
	  		val stop : Int = 0
	  	}
	  	
	  	var returnedArgs : Array[(Array[(String,Boolean,BaseTypeDef)],Array[(String,Boolean,BaseTypeDef)],ContainerTypeDef,Array[BaseTypeDef], String)]
	  			= fcnSelector.collectArgKeys(expandingContainerFields)

	  	/** Project the various argTypeExpanded pieces into their own arrays */
	  	val argTypesExp : Array[Array[(String,Boolean,BaseTypeDef)]] = returnedArgs.map( tuple => tuple._1)
	  	val mbrFcnTypesExp : Array[Array[(String,Boolean,BaseTypeDef)]] = returnedArgs.map( tuple => tuple._2)
	  	val containerTypeDefs : Array[ContainerTypeDef] = returnedArgs.map( tuple => tuple._3)
	  	val memberTypes : Array[Array[BaseTypeDef]] = returnedArgs.map( tuple => tuple._4)
	  	val returnTypes : Array[String] = returnedArgs.map( tuple => tuple._5)  	
	  	
	  	/** There is no support for iterables in the macros.. grab the outer function args (note in expanded form) */
	  	/** Check for legitimate type info here before flatten.. avoiding null ptr exception */
	  	val hasNulls : Boolean = (argTypesExp == null || argTypesExp.filter(_ == null).size > 0)
	  	val (macroDefRet, argTypesRet) : (MacroDef, Array[(String,Boolean,BaseTypeDef)]) =  if (hasNulls) {
	  		PmmlError.logError(ctx, s"Macro selection cannot continue... unable to find type information for one or more function/macro arguments")
	  		(null,null)
	  	} else {
		  	val argTypes : Array[(String,Boolean,BaseTypeDef)] = argTypesExp.flatten
		  	
		  	var simpleKey : String = fcnSelector.buildSimpleKey(node.function, argTypes.map( argPair => argPair._1))
		  	var winningKey : String = simpleKey
		  	val nmspcsSearched : String = ctx.NameSpaceSearchPathAsStr
		  	logger.debug(s"selectMacro ... key used for mdmgr search = '$nmspcsSearched.$simpleKey'...")
		  	var macroDef : MacroDef = ctx.MetadataHelper.MacroByTypeSig(simpleKey)
		  	if (macroDef == null) {
		  		val simpleKeysToTry : Array[String] = fcnSelector.relaxSimpleKey(node.function, argTypes, returnTypes)
		  		breakable {
		  		  	simpleKeysToTry.foreach( key => {
		  		  		logger.debug(s"selectMacro ...searching mdmgr with a relaxed key ... $key")
		  		  		macroDef = ctx.MetadataHelper.MacroByTypeSig(key)
		  		  		if (macroDef != null) {
		  		  			logger.debug(s"selectMacro ...found macroDef with $key")
		  		  			winningKey = key
		  		  			break
		  		  		}
		  		  	})	  		  
		  		}
		  	}
		  	
		  	if (macroDef == null) {
		  		val expandingContainerFields = false
		  		var returnedArgsNoExp : Array[(Array[(String,Boolean,BaseTypeDef)],Array[(String,Boolean,BaseTypeDef)],ContainerTypeDef,Array[BaseTypeDef], String)]
		  			= fcnSelector.collectArgKeys(expandingContainerFields)

		  		val argTypesNoExp : Array[Array[(String,Boolean,BaseTypeDef)]] = returnedArgsNoExp.map( tuple => tuple._1)
		  		val returnTypes : Array[String] = returnedArgsNoExp.map( tuple => tuple._5)
			  	
		  		/** There is no support for iterables in the macros.. grab the outer function args (note in expanded form) */
			  	val argTypes : Array[(String,Boolean,BaseTypeDef)] = argTypesNoExp.flatten
		  	  
			  	//var simpleKeyNoExp : String = fcnSelector.buildSimpleKey(argTypesNoExp.map( argPair => argPair._1))
			  	//var simpleKeyNoExp : String = fcnSelector.buildSimpleKey(node.function, argTypesNoExp.map( argPair => argPair._1))
			  	var simpleKeyNoExp : String = fcnSelector.buildSimpleKey(node.function, returnTypes)
			  	val nmspcsSearched : String = ctx.NameSpaceSearchPathAsStr
			  	ctx.logger.debug(s"selectMacro ... key used for mdmgr search = '$nmspcsSearched.$simpleKeyNoExp'...")
			  	macroDef = ctx.MetadataHelper.MacroByTypeSig(simpleKeyNoExp)
			  	if (macroDef == null) {
			  		val simpleKeysToTry : Array[String] = fcnSelector.relaxSimpleKey(node.function, argTypes, returnTypes)
			  		breakable {
			  		  	simpleKeysToTry.foreach( key => {
			  		  		ctx.logger.debug(s"selectMacro ...searching mdmgr with a relaxed key ... $key")
			  		  		macroDef = ctx.MetadataHelper.MacroByTypeSig(key)
			  		  		if (macroDef != null) {
			  		  			ctx.logger.debug(s"selectMacro ...found macroDef with $key")
			  		  			winningKey = key
			  		  			break
			  		  		}
			  		  	})	  		  
			  		}
			  	}
		  	  
		  	}
		  	val foundDef : String = if (macroDef != null)  s"YES ...$winningKey found ${node.function}" else "NO!!"
		  	if (macroDef != null) { 
		  		logger.debug(s"selectMacro ...macroDef produced?  $foundDef")
		  	} else {
		  		PmmlError.logError(ctx, s"selectMacro ...macroDef produced?  $foundDef Either a function or a macro by this name must exist or perhaps the spelling or function signature is incorrect")
		  	}
		  	(macroDef, argTypes)
	  	}
	  	(macroDefRet, argTypesRet)

	}
	
	/**
	 * 	Generate possibly two snippets of code, based upon the macro features that are specified
	 *  in the macro definition:
	 *  
	 *  <ulist>
	 *  <li>the 'builds' snippet</li>
	 *  <p>The 'builds' snippet is generated when CLASSUPDATE (and possibly other) features are specified in the MacroDef.
	 *  The builds snippet will contain the appropriate macro tempate instance fully spliced with the values required from
	 *  the argTypes and the xApply node supplied at the constructor time.  The arguments are positional with %1% corresponding to
	 *  argTypes(0), etc.   The appropriate macro is determined based upon whether there are containers present that require
	 *  a container.field treatment or a container(field) treatment.  In other words, is the container "fixed" or "mapped"?
	 *  This code snippet is enqueued for insertion into the code buffer just before the end of the compilation unit being built...
	 *  namely a derived field class.</p>
	 *  <li>the 'does' snippet</li>
	 *  <p>The 'does' snippet is always generated.  This snippet is inserted directly into the code buffer... at the current point.
	 *  Should this macro have the class update feature, this snippet will be responsible for an instantiation of the class and
	 *  execution of the method (the xApply node.function) enclosed in it (e.g., new someClass(...).incrementBy ...).</p>
	 *  </ulist>
	 *  
	 *  Pre-conditions:
	 *  <ulist>
	 *  <li>There is no provision currently for handling a macro that has BOTH a "fixed" and "mapped" container field at the same
	 *  time.</li>
	 *  </ulist>
	 *  
	 *  @param macroDef a MacroDef that describes the macro to be used for this code generation.
	 *  @param argTypes the function macro arg types and whether they are containers, (and functions too eventually).  It may be useful
	 *  	to return the actual type metadata in the second arg instead of a boolean or in addition to the boolean.
	 *  @return the "builds/does" pair as described above.  If there is no "builds" required, either an empty string or a null 
	 *  	string is returned in that position.
	 *  	
	 *  
	 */
	def generateCode(macroDef : MacroDef, argTypes : Array[(String,Boolean,BaseTypeDef)]) : (String, String,String) = {
	  
  		if (macroDef.features.contains(FcnMacroAttr.CLASSUPDATE)) {
  			generateClassBuildsAndDoes(macroDef, argTypes)
  		} else {
  			generateOnlyDoes(macroDef, argTypes)
  		}
	}
  	
	/** 
	 *  Some macros don't generate the "builds" class.  They emit some possibly elaborate code to be inserted inline in the 
	 *  current function's string builder buffer.  NOTE: only the 2nd item in the returned tuple is filled.  This is 
	 *  the one for the "does" piece.  The first item is only filled for those macro uses that have the FcnMacroAttr.CLASSUPDATE 
	 *  feature.  
	 *  
	 *  @param macroDef a MacroDef that describes the macro to be used for this code generation.
	 *  @param argTypes the function macro arg types and whether they are containers, (and functions too eventually).  It may be useful
	 *  to return the actual type metadata in the second arg instead of a boolean or in addition to the boolean.
	 *  @return a "builds/does" pair with a null "builds" value in tuple._1 since it is not used.
	 */
	def generateOnlyDoes(macroDef : MacroDef, argTypes : Array[(String,Boolean,BaseTypeDef)]) : (String,String,String) = {
	  
	  	/** either template can be used.  There is no mapped container */
  		val templateUsed : String = macroDef.macroTemplate._1
		val parametersUsed : Array[Int] = getArgParameterIndices(templateUsed)
		val parameterValues : Array[String] = getArgValues

		val functionMacroExpr : String = generateDoes(templateUsed, parametersUsed, parameterValues, argTypes)
		(null,null,functionMacroExpr)
	}
	
	/**
	 * 	Create an array of substitution values from the xApply node's children (i.e., the arguments)
	 */
	def getArgValues : Array[String] = {  
		var substitutionValues : ArrayBuffer[String] = ArrayBuffer[String]()
	  	val fcnBuffer : StringBuilder = new StringBuilder

	  	node.Children.foreach(child => {
	  		fcnBuffer.clear
	  		generator.generate(Some(child), fcnBuffer, CodeFragment.FUNCCALL)
	  		val argPrint : String = fcnBuffer.toString
	  		substitutionValues += argPrint
  		})
	  
  		substitutionValues.toArray
	}
	
	/** 
	 *  A FcnMacroAttr.CLASSUPDATE featured macro has been supplied.  Create the "builds" and "does" portion of the
	 *  macro generation and return as a tuple.
	 *  
	 *  @param macroDef a MacroDef with CLASSUPDATE feature
	 *  @param argTypes an array of triples - one per argument supplied to the macro function in the pmml - where
	 *  	the values are (typeString representation, isAContainer, the BaseTypeDef for the argument)
	 *   
	 *   NOTE: The BaseTypeDef can be a typedef for any of the types or a function definition.  When it is a function
	 *   definition, the typestring is the result type string representation
	 */
	def generateClassBuildsAndDoes(macroDef : MacroDef, argTypes : Array[(String,Boolean,BaseTypeDef)]) : (String,String,String) = {
	  	/** 
	  	 *  Prepare the print representations for each node and assign it to the variables.  Variable
	  	 *  assignment is $1 for first argument, $1.type for its type, $2 for second argument, $2.type
	  	 *  for the its type, etc.
	  	 *  
	  	 *  We will pick and choose from them in that there may be container.field xFieldRef that have been split
	  	 *  into container and field
	  	 */
	  	
	  	val fcnBuffer : StringBuilder = new StringBuilder
		var fcnArgValues : ArrayBuffer[((String,String),String,Boolean)] = ArrayBuffer[((String,String),String,Boolean)]()
		var fcnArgTypes : ArrayBuffer[String] = ArrayBuffer[String]()
  		fcnBuffer.clear
  		var idx : Int = 0
  		val childCnt : Int = node.Children.size
  		val argTypesCnt : Int = argTypes.size
  		val reasonable : Boolean = (childCnt <= argTypesCnt)
  		val containerDotAddressingPresent : Boolean = (childCnt < argTypesCnt)
  		
  		/** determine which template should be used... either the "fixed" or the "mapped" */
  		val isFixed : Boolean = if (argTypesCnt > 0) {
  			val fixedArgTypes : Boolean = (argTypes.filter(triple => {
  				val (argTypeStr, isContainer, argElem) : (String,Boolean,BaseTypeDef) = triple
  				val typStr : String = argElem.typeString
  				val isCollection : Boolean = typStr.contains("scala.Array") || typStr.contains("scala.collection")
  				(isContainer && argElem.isInstanceOf[ContainerTypeDef] && argElem.asInstanceOf[ContainerTypeDef].IsFixed && ! isCollection)
  			}).size > 0)
  			val mappedArgTypes : Boolean = (argTypes.filter(tripleM => {
  				val (argTypeStr, isContainer, argElem) : (String,Boolean,BaseTypeDef) = tripleM
  				val typStr : String = argElem.typeString
  				val isCollection : Boolean = typStr.contains("scala.Array") || typStr.contains("scala.collection")
  				(isContainer && argElem.isInstanceOf[ContainerTypeDef] && ! argElem.asInstanceOf[ContainerTypeDef].IsFixed && ! isCollection)
  			}).size > 0)
			if (mappedArgTypes && fixedArgTypes) {
				false  /** bias toward the mapped in this case... */
			} else {
				fixedArgTypes
			}
  		} else {
 	  		PmmlError.logError(ctx, s"generateClassBuildsAndDoes... macro without arguments... not supported.")
 	  		true
  		}
	  	
	  	/** Paw through the children and the argTypes supplied (considered together) to find the correct argument names
	  	 *  and types for the "builds" portion of the generation.  Notice that both the declarative "builds" name and whatever 
	  	 *  the runtime "does" expression string are collected.  Except for the container.field cases, the 2nd expression string
	  	 *  in the tuple is used in the "does" string formation that is immediately inserted into the derived field's execute
	  	 *  function printer.  For the containers, we need to regenerate a variable extraction for just the container name.
	  	 */
		/** insure variable names are unique by using  */
		var nmSet : scala.collection.mutable.Set[String] = scala.collection.mutable.Set[String]()
  		node.Children.foreach((child) => {
  			val (argTypeStr, isContainer, argElem) : (String,Boolean,BaseTypeDef) = argTypes(idx)
	  		generator.generate(Some(child), fcnBuffer, CodeFragment.FUNCCALL)
	  		val argPrint : String = fcnBuffer.toString
	  		val illegalsEx = "[.]".r
	  		/** Note: to support multiple level containers ... e.g., container.container.field... recursion needs to be introduced here */
	  		if (isContainer && child.asInstanceOf[xFieldRef].field.contains('.')) {
	  			val container : ContainerTypeDef = argElem.asInstanceOf[ContainerTypeDef]
	  			val compoundNm : Array[String] = child.asInstanceOf[xFieldRef].field.split('.')
	  			val containerName : String = compoundNm(0)
	  			val fieldName : String = compoundNm(1)
   				val (buildsContainerName,doesContainerName) : (String,String) = if (nmSet.contains(containerName)) ((containerName + ctx.Counter().toString), containerName) else { nmSet += containerName; (containerName,containerName) }
	  			fcnArgValues += Tuple3((buildsContainerName,doesContainerName),containerName,true)
	  			fcnArgTypes += argTypeStr
	  			idx += 1
	  			val (fldArgType, isFldAContainer, fldElem) : (String,Boolean,BaseTypeDef) = argTypes(idx)
   				val (buildsfieldName,doesFieldName) : (String,String) = if (nmSet.contains(fieldName)) (fieldName,(fieldName + ctx.Counter().toString)) else { nmSet += fieldName; (fieldName,fieldName) }
   				/** alias qualified names have their '.' replaced with '_' via following stmt */
	  			val buildsfieldName_scrubbed : String = if (buildsfieldName.contains(".")) { illegalsEx.replaceAllIn(buildsfieldName, "_") } else buildsfieldName
   				val actualfieldName : String = fieldName
	  			fcnArgValues += Tuple3((buildsfieldName_scrubbed,doesFieldName),fieldName,false)
	  			fcnArgTypes += fldArgType
	  		} else {
	  			val (argNams,argTyp) : ((String,String),String) = if (child.isInstanceOf[xConstant]) {
	  							val uniqNo : Int = ctx.Counter()
			  					val constNm = "value" + uniqNo.toString 
			  					((constNm,constNm), argTypeStr)
				  			} else {
				  				if (child.isInstanceOf[xApply]) {
				  					val fcnArg : xApply = child.asInstanceOf[xApply]
				  					val fcnTypeInfo : FcnTypeInfo = fcnArg.GetTypeInfo
				  					val fcnDef : FunctionDef = if (fcnTypeInfo != null) fcnTypeInfo.fcnDef else null
									val fcnArgNm = child.asInstanceOf[xApply].function + "Result" +  idx.toString
									((fcnArgNm,fcnArgNm), argTypeStr)
					  			} else {
					  			    if (isContainer && argElem.isInstanceOf[ContainerTypeDef]) {
				  			    		val containerType : ContainerTypeDef = argElem.asInstanceOf[ContainerTypeDef]
		  			    				val containerName : String = child.asInstanceOf[xFieldRef].field
		  			    				val (buildsCntrName,doesCntrName) : (String,String) = if (nmSet.contains(containerName)) ((containerName + ctx.Counter().toString),containerName) else { nmSet += containerName; (containerName,containerName) }
		  			    				((buildsCntrName,doesCntrName), argTypeStr)
					  			    } else {
					  			    	if (child.isInstanceOf[xFieldRef]) {
					  			    		val fld : String = child.asInstanceOf[xFieldRef].field
			  			    				val (buildsFldNm,doesFldNm) : (String,String) = if (nmSet.contains(fld)) ((fld + ctx.Counter().toString),fld) else { nmSet += fld; (fld,fld) }
					  			    		((buildsFldNm,doesFldNm), argTypeStr)
					  			    	} else {
					  			    		((argPrint,argPrint), argTypeStr)
					  			    	}
					  			    }
					  			}
				  			}
	  			/** alias qualified names have their '.' replaced with '_' via ff statements */
	  			val (bldName,exprName) : (String,String) = argNams
  				val bldName_scrubbed : String = if (bldName.contains(".")) { illegalsEx.replaceAllIn(bldName, "_") } else bldName
	  			
	  			fcnArgValues += Tuple3((bldName_scrubbed, exprName),argPrint,false)
	  			fcnArgTypes += argTyp
	  		}
  			idx += 1
	  		fcnBuffer.clear
  		})
 	  
  		/** debugging checks */
  		val sizeOfArgVals : Int = fcnArgValues.size
  		val sizeOfArgTypes : Int = fcnArgTypes.size
  		val sameAmtArgsAsTypes : Boolean = (sizeOfArgVals == sizeOfArgTypes)
  		
  		/** construct the substitution map for the builds part */
  		var substitutionMap : HashMap[String,String] = HashMap[String,String]()
  		val argFcnMap = fcnArgValues zip fcnArgTypes
  		argFcnMap.zipWithIndex.foreach( pair => {
  			val (argval, argtype) = pair._1
  			val argidx : Int = pair._2 + 1
  			val argIdxName = argidx.toString 
  			val (buildsName,doesName) : (String,String) = argval._1
  			val argIdxTypeName = argidx.toString + "_type"
  			logger.debug(s"%$argIdxName% = $buildsName, %$argIdxTypeName% = $argtype)")
  			substitutionMap += s"%$argIdxName%" -> s"$buildsName"
  			substitutionMap += s"%$argIdxTypeName%" -> s"$argtype"
  		})
  		
  		/** get the fixed template */
  		val buildsTemplate : String = macroDef.macroTemplate._1
  		/** get the mapped template */
  		val mappedBuildsTemplate : String = macroDef.macroTemplate._2
  		
  		val (substitutedClassExpr,templateUsed) : (String,String) = if (isFixed) {
   			val subExpr : String = ctx.fcnSubstitute.makeSubstitutions(buildsTemplate, substitutionMap)
   			(subExpr,buildsTemplate)
  		} else {
  			val subExpr : String = ctx.fcnSubstitute.makeSubstitutions(mappedBuildsTemplate, substitutionMap)
   			(subExpr,buildsTemplate)
  		}
		
		val buildsClassKeyCandidate : String = substitutedClassExpr.split('(').head
		val buildsClassKeyCandidateTrimmed = if (buildsClassKeyCandidate != null && buildsClassKeyCandidate.size > 0) buildsClassKeyCandidate.trim else null
		val buildsClassKey : String = if (buildsClassKeyCandidateTrimmed != null && buildsClassKeyCandidateTrimmed.size > 0 &&
		    								buildsClassKeyCandidateTrimmed.startsWith("class")) buildsClassKeyCandidateTrimmed else null
		logger.debug("builds class to be queued:")
		logger.debug(s"\n$buildsClassKey = \n$substitutedClassExpr")		  
		
		/** 
		 *	Create the "does" portion 
		 *  	a) Get generated class name from generated string.  
		 *   	b) From the template, get the parameter indices needed in the arg list for our "does" cmd
		 *    	c) construct the argument list accordingly
		 */
		val classUpdateClassName : String = getClassName(substitutedClassExpr)
		val parametersUsed : Array[Int] = getArgParameterIndices(templateUsed)
		// var fcnArgValues : ArrayBuffer[((String,String),String,Boolean)]
		val doesCmd : String = generateDoes(macroDef, argTypes, fcnArgValues, classUpdateClassName, parametersUsed)

		logger.debug("'does' use of 'builds' class to be used:")
		logger.debug(s"\n$doesCmd")		  
		
	  
	  	(buildsClassKey, substitutedClassExpr, doesCmd)
	}
	
	/** 
	 *  Answer the "does" string... the one that will be inserted into the current function's write buffer.  The content of that string
	 *  will instantiate the CLASSUPDATE class that will be inserted just before the closing brace of the derived field enclosing this function.
	 *  
	 *  @param macroDef the function macro that defines the metadata particulars for the current xApply's function (i.e., the one used to construct
	 *  	this MacroSelect instance).
	 *  @param argTypes an array of triples - one per argument supplied to the macro function in the pmml - where
	 *  	the values are (typeString representation, isAContainer, the BaseTypeDef for the argument)
	 *  @param fcnArgValues - an ArrayBuffer of triples where 
	 *  
	 *  	<ul> 
	 *   	<li>The first argument is a string tuple that contains the "builds" name that was used to generate the class that was 
	 *    	written by the caller.  The second of the tuple is the "does" name appropriate for generating the expression to 
	 *     	create the instance of the builds class. It has the original name in it, and is the one used here.</li>  
	 *      <li>The second string is the what the standard generate function produced; this will be used in most cases to produce 
	 *      the arguments for the "does" snippet to be produced here, and</li>
	 *    	<li>The boolean flag as to whether the argument was one that is a Container.field pattern.  When that is the case, 
	 *     	the standard generate text is incorrect because it is fetching the field content, not the reference to the container, 
	 *      the field's parent needed for the update.</li>
	 *      </ul>
	 *      
	 *  @param classUpdateClassName - this is the generated class name produced in (e.g.,...) generateClassBuildsAndDoes.  It will be part of 
	 *  	string to be generated here "
	 *  @param parameterIndices - these are indices into the fcnArgValues parameter (the 2nd arg) that are needed for the "does" arguments to 
	 *  	be produced here.
	 *  @return the generated use of the class that was generated by the "builds" method .
	 */
	def generateDoes(macroDef : MacroDef, argTypes : Array[(String,Boolean,BaseTypeDef)], fcnArgValues : ArrayBuffer[((String,String),String,Boolean)], classUpdateClassName : String, parameterIndices : Array[Int]) : String = {
		val buffer : StringBuilder = new StringBuilder
		buffer.append(s"new $classUpdateClassName(ctx, ")
		var cnt : Int = 0
		val paramCnt : Int = parameterIndices.size
		if (argTypes.size == 5) {
			val stopHere = 0
		}
		parameterIndices.foreach( idx => {
			val (typStr, isCntnr, elemdef) : (String,Boolean,BaseTypeDef) = argTypes(idx)
			val ((buildsArgName, doesArgName), exprStr, isContainer) : ((String,String),String,Boolean) = fcnArgValues(idx)
			//val childNode = if (! isContainer) node.Children.apply(cnt) else null
			val childNode = if (! elemdef.isInstanceOf[ContainerTypeDef]) node.Children.apply(cnt) else null
			if (ctx.MetadataHelper.isContainerWithFieldOrKeyNames(elemdef)) { //elemdef.isInstanceOf[ContainerTypeDef]) {
				val appropriateTypeArgs = argTypes(cnt)
				val (typeStr, isCtnr, elem) : (String,Boolean,BaseTypeDef) = appropriateTypeArgs
				val whichType : String = typStr
				buffer.append(s"ctx.valueFor(${'"'}$doesArgName${'"'}).asInstanceOf[AnyDataValue].Value.asInstanceOf[$whichType]")
			} else {
				buffer.append(exprStr)
				if (typStr == "Long") {
					/** check the exprStr... if all numeric (i.e., a long constant) suffix it with "L" to help the Scala compiler recognize this is a long */
					val isAllNumeric : Boolean = exprStr.filter(ch => (ch >= '0' && ch <= '9')).size == exprStr.size
					if (isAllNumeric) {
						buffer.append('L')
					}
				}
			}
			cnt += 1
			if (cnt < paramCnt) {
				buffer.append(", ")
			}
			val doesExprSoFar : String = buffer.toString
			val letsLook : Boolean = true
		})
		/** 
		 *  By convention, the name of the function in the class that executes is the name of the function that appears 
		 *  in the pmml function name.  It is currently up to the macro writer to use the right name (i.e., the same
		 *  name that is used in the pmml apply function.
		 */
		val function : String = node.function
		buffer.append(s").$function")
		val doesString : String = buffer.toString
		doesString
	}
	
	/** 
	 *  From the substituted "builds" string, get the class name.  
	 *  
	 *  NOTE: It is assumed that the template starts with optional white
	 *  space and then the keyword class and then what will be the name
	 *  after substitution.
	 *  
	 *  This could be made more robust perhaps...
	 *  
	 */
	def getClassName(substitutedClassExpr : String) : String = {
		val firstPart : String = substitutedClassExpr.split('(').head.trim
		var className : String = firstPart.split("[ \t\r\n\f]+").last
		
		className
	}

	/** 
	 *  Collect the arguments that are used in the class parameter list from
	 *  the template and translate them to array indices.
	 */
	def getArgParameterIndices(templateUsed : String) : Array[Int] = {
	  	val numeric = "^([0-9]+)$".r
	  	val argsOnly : String = encloseArgs(templateUsed, '(', ')')
	  	val postionalArgNos : Array[String] = if (argsOnly != null) {
	  		val roughArray : Array[String] = argsOnly.split('%')
	  		roughArray.map( part => {
	  			var arg : String = null
	  			try {
		  			val numeric(argNum) = part
		  			arg = argNum
	  			} catch {
	  			  case e: Exception => {
              val stackTrace = StackTrace.ThrowableTraceString(e)
              logger.debug("StackTrae:"+stackTrace)
              arg = null}
	  			}
	  			arg
	  		})
	  	} else {
	  	   Array[String]()
	  	}
	  	postionalArgNos.filter(arg => arg != null).map( num => num.toInt - 1)
	}

	def encloseArgs(typeString : String, openBracketOrParen : Char, closeBracketOrParen : Char) : String = {
		var begoffset : Int = 0
		var offset : Int = 0
		var bracketOrParenCnt : Int = 0
		var bracketOrParenSet : Boolean = false
		breakable {
			for (ch <- typeString) {			
				if (ch == openBracketOrParen) {
					if (! bracketOrParenSet) {
						begoffset = offset
						bracketOrParenSet = true
					}
					bracketOrParenCnt += 1 
				} else {
					if (ch == closeBracketOrParen) {
						bracketOrParenCnt -= 1
						if (bracketOrParenCnt == 0) {  /** find the close bracket or paren matching the first one */
							offset += 1
							break
						} 
					}
				}
				offset = offset + 1	  
			}
		}
		
		/** there has to be at least one character between the (...) of the typeString arg portion */
		val typeArgs : String = if (offset > 2) typeString.substring(begoffset + 1, offset - 1) else ""
		  
		typeArgs
	}

	/** 
	 *  Answer the "does" string for simpler macros.. those that don't generate classes.  This method generates the 
	 *  simple function macro use.
	 *  
	 *  @param templateUsed one of the two templates from the function macro... 
	 *  @param parametersUsed - these are indices into the parameterValues parameter (the 2nd arg) that are needed for the "does" arguments to 
	 *  	be produced here (for sanity checking during debug)
	 *  @param parameterValues - the substitution values that are available for the substitution.  Note that not all of them
	 *  	necessarily need to be used.  The parametersUsed determines which will be used in the substitution map.
	 *  @return the generated expression based upon the supplied template and substitution values.
	 */
	def generateDoes(templateUsed : String, parametersUsed : Array[Int], parameterValues : Array[String], argTypes : Array[(String,Boolean,BaseTypeDef)] ) : String = {
		val buffer : StringBuilder = new StringBuilder

		val paramCnt : Int = parametersUsed.size
		val argTypesSize : Int = argTypes.size
		if (parameterValues.size == 5) {  /** little debug station */
			val stopHere = 0
		}
		
  		/** construct the substitution map used to fill in copy of template */
  		var substitutionMap : HashMap[String,String] = HashMap[String,String]()
  		parameterValues.zipWithIndex.foreach( pair => {
  			val argExpr : String = pair._1
  			val argidx : Int = pair._2 + 1
  			val argIdxName = argidx.toString 
  			
  			val (argType,isContainer,typedef) : (String,Boolean,BaseTypeDef) = if (pair._2 < argTypesSize) argTypes.apply(pair._2) else ("Any",false,null)
  			val exprToUse : String = if (argType == "Long") {
				/** check the argType... if all numeric (i.e., a long constant) suffix it with "L" to help the Scala compiler recognize this is a long */
				val isAllNumeric : Boolean = argExpr.filter(ch => (ch >= '0' && ch <= '9')).size == argExpr.size
				if (isAllNumeric) {
					argExpr + "L"
				} else {
					argExpr
				}
  			} else {
  				argExpr
  			}

  			ctx.logger.trace(s"%$argIdxName% = $exprToUse")
  			substitutionMap += s"%$argIdxName%" -> s"$exprToUse"
  		})
  		
  		
  		val subExpr : String = ctx.fcnSubstitute.makeSubstitutions(templateUsed, substitutionMap)
		subExpr
	}
	
}

