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

package com.ligadata.pmml.support

import scala.collection.mutable._
import scala.collection.immutable.{ Set }
import scala.math._
import scala.collection.immutable.StringLike
import scala.util.control.Breaks._
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.kamanja.metadata._
import com.ligadata.pmml.compiler._
import com.ligadata.pmml.syntaxtree.cooked.common._


class MetadataInterpreter(val ctx : PmmlContext) extends com.ligadata.pmml.compiler.LogTrait {
  
	val mgr : MdMgr = ctx.mgr

	/** 
	 *  Answer min/max count of arguments for the functions with the supplied name 
	 *  @param name a namespace.name string key.
	 */
	def minMaxArgsForTheseFcns(name : String) : (Int,Int) = {
		var argCntMin = 1000000
		var argCntMax = 0
	  	val availableFcns : Set[FunctionDef] = getFunctions(name)
	  	if (availableFcns != null) {
	  		availableFcns.foreach( f => {
	  			argCntMin = if (f.args.size > argCntMin) f.args.size else argCntMin 
	  			argCntMax = if (f.args.size < argCntMax) f.args.size else argCntMax 
	  		})
	  	}
	  	argCntMax = if (argCntMax == 1000000) 0 else argCntMax
	  	(argCntMin,argCntMax) 
	}
	
	
	/** 
	 *  Answer an Array[(String,Boolean,BaseTypeDef)] that describe the types in a nested container field reference.
	 *  If expandCompoundFieldTypes is true, all types for each container and final leaf field element are returned in
	 *  the array.  If false, only the field's type is returned.
	 *  
	 *  @param names - an array of the string components from a (possibly) compound field reference expression
	 *  @param expandCompoundFieldTypes - when true, the full type information for each type in the field expression
	 *  	are returned.  When false, only the leaf type information is returned.
	 *  @return an array of triples consisting of (type string representation, isContainerWithNamedValues, the BaseTypeDef)
	 */
	
	def getFieldType(names : Array[String], expandCompoundFieldTypes : Boolean) : Array[(String,Boolean,BaseTypeDef)] = {	
		var baseTypeTriples : ArrayBuffer[(String,Boolean,BaseTypeDef)] = ArrayBuffer[(String,Boolean,BaseTypeDef)]()
		val buffer = new StringBuilder
		names.addString(buffer, ".")
		val fldRefExprStr : String = buffer.toString
		val totalNames : Int = names.size

		getFieldType(names, fldRefExprStr, totalNames, null, baseTypeTriples)
		
		val returnArray : Array[(String,Boolean,BaseTypeDef)] = if (! expandCompoundFieldTypes ) {
			if (baseTypeTriples.size > 0) {
				Array[(String,Boolean,BaseTypeDef)](baseTypeTriples.last)
			} else {
				Array[(String,Boolean,BaseTypeDef)]()
			}
		} else {
			baseTypeTriples.toArray
		}
		returnArray	
	}

	/** 
	 *  Collect an array buffer of(String,Boolean,BaseTypeDef) that describe the types in a nested container field 
	 *  reference (e.g., container.subcontainer.field)
	 *  
	 *  @param names - the names in a container.subcontainer.subsubcontainer....field reference expression that remain
	 *  				to be examined.
	 *  @param fieldRefRep - the original field ref as a string rep for diagnostics
	 *  @param totalNames - the count of container/field names in the original field ref expression
	 *  @param parentContainer - except for the first container in a nested field reference, this parameter contains
	 *  		the immediate preceding type reference derived from the prior name.  It is used to lookup the current name.
	 *  @param baseTypeTriples - this array buffer is updated with the triples.
	 *  
	 */
	
	def getFieldType(names : Array[String]
					, fieldRefRep : String
					, totalNames : Int
					, parentContainer : ContainerTypeDef
					, baseTypeTriples : ArrayBuffer[(String,Boolean,BaseTypeDef)]) : Unit = {	
	  	var priorType : BaseTypeDef = null
	  	var name : String = null
	  	if (names != null && names.size > 0) {
			name = names.head
			/** The variable is either defined in one of the dictionaries (ddict or xdict) or is a field (or key) inside
			 *  one of these containers  */
			if (name != null) {
				if (ctx.dDict.contains(name) && parentContainer == null) {
					val fld : xDataField = ctx.dDict.apply(name)
					val containerType : ContainerTypeDef = getContainerWithNamesType(fld.dataType)
					if (containerType == null && names.size != 1) { /** this is not a container and therefore should be last name */
						PmmlError.logError(ctx, s"Malformed compound name... The name '$name' in field reference value '$fieldRefRep' is not a container. ")
						PmmlError.logError(ctx, s"Either the metadata type for this field is incorrect or it should be the last field in the field reference.")
					} else {
						/** it may indeed be the last one, but let's see if there is more */
						if (containerType == null) {
							if (totalNames == 1) {
								val conceptCollected : Boolean = collectConceptType(name, baseTypeTriples)
								if (! conceptCollected) {
									val (typestring,typedef) : (String,BaseTypeDef) = getDDictFieldType(name)
									if (typedef == null) {
										logger.debug(s"The field type, '${fld.dataType} for data dictionary field '$name' is not found")
										logger.debug("Either correct the spelling or add a type with this name to the metadata")
									} else {
										baseTypeTriples += Tuple3(typestring, (containerType != null), typedef)
									}
								}
							} else {
								val parentsName : String = if (parentContainer != null) parentContainer.Name else "(no parent name avaialable)"
								/** embarrassing situation... there is a logic error in this code... fix it */
								PmmlError.logError(ctx, s"The name '$name' in '$fieldRefRep' was not found.  Its parent was '$parentsName'. ")
								PmmlError.logError(ctx, s"If there is a parent name, then there is a logic error. Contact us.")
							}
						} else {
							/** this is a container */
							priorType = containerType /** remember this fact for recurse at bottom of function */
							val isContainerWithNamedFields = (containerType.isInstanceOf[StructTypeDef] || containerType.isInstanceOf[MappedMsgTypeDef])
							baseTypeTriples += Tuple3(priorType.typeString, isContainerWithNamedFields, priorType)
						}
					}
			  
				} else {
					val inXDict : Boolean = (ctx.xDict.contains(name)  && parentContainer == null)
					if (inXDict) {
						val fld : xDerivedField = ctx.xDict.apply(name)
						val containerType : ContainerTypeDef = getContainerWithNamesType(fld.dataType)
						if (containerType == null && names.size != 1) { /** this is not a container and therefore should be last name */
							PmmlError.logError(ctx, s"Malformed compound name... The name '$name' in field reference value '$fieldRefRep' is not a container. ")
							PmmlError.logError(ctx, s"Either the metadata type for this field is incorrect or it should be the last field in the field reference.")
						} else {
							/** it may indeed be the last one, but let's see if there is more */
							if (containerType == null) {
								if (totalNames == 1) {
									val conceptCollected : Boolean = collectConceptType(name, baseTypeTriples)
									if (! conceptCollected) {
										val (typestring,typedef) : (String,BaseTypeDef) = getXDictFieldType(name)
										if (typedef == null) {
											logger.debug(s"The field type, '${fld.dataType} for data dictionary field '$name' is not found")
											logger.debug("Either correct the spelling or add a type with this name to the metadata")
										} else {
											baseTypeTriples += Tuple3(typestring, (containerType == null), typedef)
										}
									}
								} else {
									val parentsName : String = if (parentContainer != null) parentContainer.Name else "(no parent name avaialable)"
									/** embarrassing situation... there is a logic error in this code... fix it */
									PmmlError.logError(ctx, s"The name '$name' in '$fieldRefRep' was not found.  Its parent was '$parentsName'. ")
									PmmlError.logError(ctx, s"If there is a parent name, then there is a logic error. Contact us.")
								}
							} else {
								/** this is a container */
								priorType = containerType /** remember this fact for recurse at bottom of function */
								val isContainerWithNamedFields = (containerType.isInstanceOf[StructTypeDef] || containerType.isInstanceOf[MappedMsgTypeDef])
								baseTypeTriples += Tuple3(priorType.typeString, isContainerWithNamedFields, priorType)
							}
						}
					} else {
						/** this field name is not in the pmml dictionaries... search the parent container for it, if appropriate, else
						 *  record the field type information for it, assuming it is a valid base concept (explicitly cataloged attribute) 
						 *  or possibly a simple scalar */
						if (parentContainer != null) {						
							val attr : BaseAttributeDef = parentContainer match {
							  case s : StructTypeDef => parentContainer.asInstanceOf[StructTypeDef].attributeFor(name)
							  case m : MappedMsgTypeDef => parentContainer.asInstanceOf[MappedMsgTypeDef].attributeFor(name)
							  case _ => null
							}
							if (attr != null) {
								priorType = attr.typeDef
								val isContainerWithNamedFields = (priorType.isInstanceOf[StructTypeDef] || priorType.isInstanceOf[MappedMsgTypeDef])
								baseTypeTriples += Tuple3(priorType.typeString, isContainerWithNamedFields, priorType)
							} else {
								val parentsName : String = if (parentContainer != null) parentContainer.Name else "NO PARENT"
								PmmlError.logError(ctx, s"The name '$name' in field reference value '$fieldRefRep' could not be found in parent container '$parentsName'. ")
								PmmlError.logError(ctx, s"Either the name is incorrect or this field should be added as an attribute to the $parentsName.")
							}
						} else {
							/** This may be the sole element in the field reference and a public concept... let's see */
							if (totalNames == 1) {
								val conceptCollected : Boolean = collectConceptType(name, baseTypeTriples)
								if (! conceptCollected) {
									/** consider that this may be a simple type ... one last search in active types */
									val huh : String = "huh" // debugging rest stop 
								}
							} else {
								val parentsName : String = if (parentContainer != null) parentContainer.Name else "(no parent name avaialable)"
								/** embarrassing situation... there is a logic error in this code... fix it */
								PmmlError.logError(ctx, s"The name '$name' in '$fieldRefRep' was not found.  Its parent was '$parentsName'. ")
								PmmlError.logError(ctx, s"If there is a parent name, then there is a logic error. Contact us.")
							}
						}
					}	  
				}
			}
		}
	  	if (names.size > 1) {
	  		val newParentContainer : ContainerTypeDef = if (priorType.isInstanceOf[ContainerTypeDef]) priorType.asInstanceOf[ContainerTypeDef] else null
	  		if (newParentContainer == null) {
				PmmlError.logError(ctx, s"Malformed compound name... The name '$name' in field reference value '$fieldRefRep' is not a container. ")
				PmmlError.logError(ctx, s"Either the metadata type for this field is incorrect or it should be the last field in the field reference.")	  
	  		} else {
		  		/** discard the just processed name and recurse to interpret remaining names... */
	  			val restOfNames : Array[String] = names.tail
		  		getFieldType(restOfNames, fieldRefRep, totalNames, newParentContainer, baseTypeTriples)
	  		}
	  	}
	}
			
	/** 
	 *  Answer a ContainerTypeDef that has names (either StructTypeDef or MappedMsgTypeDef) or null 
	 *  if not a container.  If there is a namespace alias supplied with the type, use it.  Otherwise
	 *  use the namespace search path and choose the first match found.
	 *  @param name the type name from some dataType of a DataField or DerivedField (typically)
	 *  @return the StructTypeDef or MappedMsgTypeDef with this name ... or null if name not found
	 */
	def getContainerWithNamesType(name : String) : ContainerTypeDef = {
	  	var containerType : ContainerTypeDef = null
	  	if (name != null && name.size > 0 && name.contains('.')) {
	  		val alias : String =  name.split('.').head	  		
	  		val containerTypeName : String =  name.split('.').tail.head
	  		if (containerTypeName.contains('.')) {
	  			PmmlError.logError(ctx, s"Malformed type name argument... The name '$name' should either be an unqualified type name or a namespace alias qualified name. See the wiki.")
	  		}
	  		val nmSpc : String = if (ctx.NamespaceSearchMap.contains(alias)) ctx.NamespaceSearchMap.apply(alias) else null
	  		containerType = if (nmSpc != null) getContainerWithNamesType(nmSpc, containerTypeName) else null
	  	} else {
		  	breakable {
		  		val nmspcSrchPath : Array[(String,String)] = ctx.namespaceSearchPath
				nmspcSrchPath.foreach( nmspc => {			
					val cntnr : ContainerTypeDef = getContainerWithNamesType(nmspc._2, name)
					if (cntnr != null) {
						containerType = cntnr
						break
					}
				})
		  	}
	  	}
	  	containerType
	}
	
	/** 
	 *  OK
	 *  Answer a ContainerTypeDef that has named fields or null if not a container.  Two types currently
	 *  have this characteristic: StructTypeDef and MappedMsgTypeDef
	 *  @param nmSpc - the name of a possible container
	 *  @param container - the name of a possible container
	 *  @return the first ContainerTypeDef associated with this name or null if not found
	 */
	def getContainerWithNamesType(nmSpc : String, container : String) : ContainerTypeDef = {
		val baseTypeDef : BaseTypeDef = mgr.ActiveType(MdMgr.MkFullName(nmSpc, container))
		val containerTypeDef : ContainerTypeDef = baseTypeDef match {
		  case s : StructTypeDef => baseTypeDef.asInstanceOf[StructTypeDef]
		  case m : MappedMsgTypeDef => baseTypeDef.asInstanceOf[MappedMsgTypeDef]
		  case _ => null
		}

	  	containerTypeDef
	}

	/** 
	 *  Answer a ContainerTypeDef or null if not a container.  If the dataType name supplied has 
	 *  an namespace alias prefix, use it.  Otherwise, use the Namespaces from the namespace
	 *  search path paired with the supplied name to locate the container.
	 *  
	 *  NOTE: This function will return any kind of ContainerTypeDef, not just those with
	 *  known names.  Included types would be class ListTypeDef, QueueTypeDef, ArrayTypeDef
	 *  ArrayBufTypeDef, TupleTypeDef, among others.
	 *  
	 *  @param name - the name of a possible container
	 *  @return the first ContainerTypeDef associated with this name or null if not found
	 */
	def getContainerType(name : String) : ContainerTypeDef = {
	  	var containerType : ContainerTypeDef = null
	  	if (name != null && name.size > 0 && name.contains('.')) {
	  		val alias : String =  name.split('.').head
	  		val containerTypeName : String =  name.split('.').tail.head
	  		if (containerTypeName.contains('.')) {
	  			PmmlError.logError(ctx, s"Malformed type name argument... The name '$name' should either be an unqualified type name or a namespace alias qualified name. See the wiki.")
	  		}
	  		val nmSpc : String = if (ctx.NamespaceSearchMap.contains(alias)) ctx.NamespaceSearchMap.apply(alias) else null
	  		containerType = if (nmSpc != null) getContainerType(nmSpc, containerTypeName) else null
	  	} else {
		  	breakable {
				val nmspcSrchPath : Array[(String,String)] = ctx.namespaceSearchPath
				nmspcSrchPath.foreach( nmspc => {			
					val cntnr : ContainerTypeDef = getContainerType(nmspc._2, name)
					if (cntnr != null) {
						containerType = cntnr
						break
					}
				})
		  	}
	  	}
	  	containerType
	}

	
	/** 
	 *  OK
	 *  Answer a ContainerTypeDef or null if not a container
	 *  @return a ContainerTypeDef that matches
	 */
	private def getContainerType(nmSpc : String, container : String) : ContainerTypeDef = {
		val baseTypeDef : BaseTypeDef = mgr.ActiveType(MdMgr.MkFullName(nmSpc, container))
		val containerTypeDef = baseTypeDef match {
		  case c : ContainerTypeDef => baseTypeDef.asInstanceOf[ContainerTypeDef]
		  case _ => null
		}

	  	containerTypeDef
	}

	/** 
	 *  Answer a BaseAttributeDef or null for the supplied concept name.  Namespaces from the namespace
	 *  search path are used until the concept is found or they are exhausted.
	 */
	def getConcept(name : String) : BaseAttributeDef = {	
	  	var attr : BaseAttributeDef = null
	  	if (name != null && name.size > 0 && name.contains('.')) {
	  		val alias : String =  name.split('.').head
	  		val restOfName : String =  name.split('.').tail.head
	  		if (restOfName.contains('.')) {
	  			PmmlError.logError(ctx, s"Malformed concept name argument... The concept name '$name' should either be an unqualified name or a namespace alias qualified name. See the wiki.")
	  		}
	  		val nmSpc : String = if (ctx.NamespaceSearchMap.contains(alias)) ctx.NamespaceSearchMap.apply(alias) else null
	  		attr = if (nmSpc != null) mgr.ActiveAttribute(nmSpc, restOfName) else null
	  	} else {
		  	breakable {
				val nmspcSrchPath : Array[(String,String)] = ctx.namespaceSearchPath
				nmspcSrchPath.foreach( nmspc => {			
					attr  = mgr.ActiveAttribute(nmspc._2, name)
					if (attr != null) {
						break
					}
				})
		  	}
	  	}
	  	attr
	}

	/** 
	 *  Answer a BaseTypeDef or null for the supplied concept name.  Namespaces from the namespace
	 *  search path are used until the concept is found or they are exhausted.
	 */
	def getConceptType(name : String) : BaseTypeDef = {
		val attr : BaseAttributeDef = getConcept(name)
	  	val attrType : BaseTypeDef = if (attr != null) attr.typeDef else null
	  	attrType
	}

	/** 
	 *  Take a flier... assume it is a base concept (an independently cataloged attribute).  Collect it if 
	 *  you can and report success/failure of this effort.
	 */
	def collectConceptType(name : String, baseTypeTriples : ArrayBuffer[(String,Boolean,BaseTypeDef)]) : Boolean = {
		val conceptType : BaseTypeDef = getConceptType(name)
		val gotOne = if (conceptType != null) {
			val isContainerWithNamedFields = (conceptType.isInstanceOf[StructTypeDef] || conceptType.isInstanceOf[MappedMsgTypeDef])
			baseTypeTriples += Tuple3(conceptType.typeString, isContainerWithNamedFields, conceptType)
			true
		} else {
			//logger.debug(s"The name '$name' in field reference value is not a valid concept. ")
			false
		}
		gotOne
	}

	/**
	 * Find the Set[FunctionDef] for the function name supplied.  Function names may be qualified with a namespace
	 * if desired.  If not qualified this way, the namespaceSearchPath is used to supply the namespace for the 
	 * search.  Each is searched in order until either the namespace.fcnName matches or all namespace.fcnName's 
	 * have been used and the search fails.
	 * 
	 * @param fcnName the name, possibly "namespace." qualified key 
	 * @return FunctionDef
	 */
	def getFunctions(fcnName : String) : scala.collection.immutable.Set[FunctionDef] = {

		var functions : Set[FunctionDef] = Set[FunctionDef]()
		var nmSpcFunctions : Set[FunctionDef] = null
		if (fcnName != null && fcnName.size > 0) {	  
			val nameParts : Array[String] = if (fcnName.contains(".")) {
				fcnName.split('.')
			} else {
				Array(fcnName)
			}
			if (nameParts.size > 2) { /** we won't use any more than two  ... no nested namespaces */
				PmmlError.logError(ctx, "There are more namespaces than can be used... getFunctions fails due to bad argument $fcnName.  Use namespace alias, not full pkg qualified namespace.  See wiki.")
			} else {
				if (nameParts.size == 2) {  /** asking for specific namespace */
					val alias : String =  nameParts(0)
					val nmSpc : String = if (ctx.NamespaceSearchMap.contains(alias)) ctx.NamespaceSearchMap.apply(alias) else alias
					val nmspFcns : Set[FunctionDef] = mgr.FunctionsAvailable(nmSpc,nameParts(1))
					if (functions != null && nmspFcns != null && nmspFcns.size > 0) { 
						functions ++= nmspFcns
					}
				} else { /** just one unqualified name */
					/** use the namespaceSearchPath elements for the namespace until a match or all possibilities exhausted */

					val nmspcSrchPath : Array[(String,String)] = ctx.namespaceSearchPath
					nmspcSrchPath.foreach( nmspc => {			
						val nmspFcns : Set[FunctionDef] = mgr.FunctionsAvailable(nmspc._2,fcnName)
						if (functions != null && nmspFcns != null && nmspFcns.size > 0) {
						  functions ++= nmspFcns
						}
					})
				}
			}
		}
		functions
	}
	
	/** 
	 *  Answer any function definitions with the supplied namespace alias and name that have the indefinite arity feature.
	 *  
	 *  @param name : the name, possibly prefixed with a namespace alias, of this function.  With namespace alias, only it will be
	 *  	searched.  If no namespace is is specified, the namespace search path (part of the PmmlContext containing
	 *   	possibly multiple namespace alias/namespace pairs in a list) is utilized to find first match.
	 *  @return scala.collection.immutable.Set[FunctionDef] if any are present else null
	 */
	def FunctionsAvailableWithIndefiniteArity(fcnName : String) : scala.collection.immutable.Set[FunctionDef] = {
		val fcnsAvailable : scala.collection.immutable.Set[FunctionDef] = getFunctions(fcnName)
		val indefArityFcns : scala.collection.immutable.Set[FunctionDef] = if (fcnsAvailable != null) {
			fcnsAvailable.filter(fcn => fcn.features.contains(FcnMacroAttr.HAS_INDEFINITE_ARITY))
		} else {
		    null
		}
		indefArityFcns
  	}

	/** 
	 *  Answer if indefinite arity feature is present in any function with this name.
	 *  
	 *  @param name : the name, possibly prefixed with a namespace, of this function.  With namespace, only it will be
	 *  	searched.  If no namespace is is specified, the namespace search path (part of the PmmlContext containing
	 *   	possibly multiple namespaces in a list) is used to
	 *   	for keys for the metadata search.
	 *  @return Boolean
	 */
	def FunctionsWithIndefiniteArity(fcnName : String) : Boolean = {
		(FunctionsAvailableWithIndefiniteArity(fcnName).size > 0)
  	}

	/** 
	 *  With the supplied function signature, try to find the FunctionDef that matches it. If a namespace alias is prepended to the
	 *  function signature (i.e., namespace exists in the [namespace.]fcnName([argtype1,argtype2,...,argtypeN])), look up its
	 *  actual namespace if possible.
	 *  
	 *  @paramn typesig : a function identifer with args in the form [namespace.]fcnName([argtype1,argtype2,...,argtypeN]) 
	 *  @return matching FunctionDef or null
	 */
	
	def FunctionByTypeSig(typesig : String) : FunctionDef = {
		var fdef : FunctionDef = null
		val buffer : StringBuilder = new StringBuilder
	  	/** if the namespace alias was supplied... use it */
	  	if (typesig.split('(').head.contains(".")) {
	  		val (namepart, args) : (String,String) = typesig.span(_ != '(')
	  		val names : Array[String] = namepart.split('.')
	  		val reasonable : Boolean = (names.size == 2)
	  		if (reasonable) {
	  			val alias : String =  names(0)
	  			val nm : String = names(1)
	  			val nmSpc : String = if (ctx.NamespaceSearchMap.contains(alias)) ctx.NamespaceSearchMap.apply(alias) else alias
	  			
	  			val key : String = nmSpc.concat(".").concat(nm).concat(args)
	  			fdef = mgr.FunctionByTypeSig(key)
	  		} else {
	  			fdef = mgr.FunctionByTypeSig(typesig)
	  		}
	  	} else { /** prefix the sans namespace key with each namespace in the search path and try to find one */
	  		breakable {
			  	val nmspcSrchPath : Array[(String,String)] = ctx.namespaceSearchPath
				nmspcSrchPath.foreach( path => {
			  		buffer.clear
			  		buffer.append(path._2)
			  		buffer.append(".")
			  		buffer.append(typesig)
			  		val key = buffer.toString
			  		fdef = mgr.FunctionByTypeSig(key)
			  		if (fdef != null) {
			  			break
			  		}
			  	})
	  		}
		}
	  	fdef
	}
	
	/** 
	 *  With the supplied function signature, try to find the MacroByTypeSig that matches it
	 *  
	 *  @param typesig : a function identifer with args in the form [namespace.]fcnName([argtype1,argtype2,...,argtypeN]) 
	 *  @return matching MacroDef or null
	 *  
	 *  Note: At this time, all macros are actually defined in System namespace.  However, there may come a day when
	 *  macros become first class citizens in metadata and have protocol for adding them, modifying them, and deleting
	 *  them... all with their own namespaces if that is what the application developer wishes.  For this reason 
	 *  macro lookup by typesig gets the same treatment as the function lookup. 
	 */

	def MacroByTypeSig(typesig : String) : MacroDef = {
		var mdef : MacroDef = null
		val buffer : StringBuilder = new StringBuilder
	  	if (typesig.split('(').head.contains(".")) {
	  		val (namepart, args) : (String,String) = typesig.span(_ != '(')
	  		val names : Array[String] = namepart.split('.')
	  		val reasonable : Boolean = (namepart.size == 2)
	  		if (reasonable) {
	  			val alias : String =  names(0)
	  			val nm : String = names(1)
	  			val nmSpc : String = if (ctx.NamespaceSearchMap.contains(alias)) ctx.NamespaceSearchMap.apply(alias) else alias
	  			
	  			val key : String = nmSpc.concat(nm).concat(args)
	  			mdef = mgr.MacroByTypeSig(key)
	  		} else {
	  			
	  			mdef = mgr.MacroByTypeSig(typesig)
	  		}
	  	} else { /** prefix the sans namespace key with each namespace in the search path and try to find one */
			breakable {
				val nmspcSrchPath : Array[(String,String)] = ctx.namespaceSearchPath
				nmspcSrchPath.foreach( path => {
			  		buffer.clear
			  		buffer.append(path._2)
			  		buffer.append(".")
			  		buffer.append(typesig)
			  		val key = buffer.toString
			  		mdef = mgr.MacroByTypeSig(key)
			  		if (mdef != null) {
			  			break
			  		}
			  	})
			}
	  	}

	  	mdef
	}
	
	/** Search this bad boy, with and without namespace qualifiers...
	 *  @param nm the name of the data dictionary data field
	 *  @return a tuple (type rep string , corresponding base type) 
	 */
	def getDDictFieldType(nm : String) : (String,BaseTypeDef) = {
		var typeStr : String = null
		var baseType : BaseTypeDef = null
		val withNmSpc : Boolean = true
		breakable {
		  	val nmspcSrchPath : Array[(String,String)] = ctx.namespaceSearchPath
				nmspcSrchPath.foreach( nmSpc => {
		  		val (typeStrWOut, baseTypeWOut) = getDDictFieldType(nmSpc._2, nm : String, ! withNmSpc)
		  		if (baseTypeWOut != null) {
					typeStr = typeStrWOut
					baseType = baseTypeWOut
		  			break
		  		}
		  		val (typeStrWith, baseTypeWith) = getDDictFieldType(nmSpc._2, nm : String, withNmSpc)
		  		if (baseTypeWith != null) {
					typeStr = typeStrWith
					baseType = baseTypeWith
		  			break
		  		}
		  	})
		}

	  	(typeStr, baseType)
	}
	
	/** 
	 *  OK
	 *  Search for the supplied field name, 'nm'.  Use the supplied 'nmSpc' name to locate the BaseType information for the field.
	 *  When the DataField or DerivedField named 'nm' has a dataType that is prefixed with a valid namespace alias, it will be used 
	 *  instead of the supplied 'nmSpc'.
	 *  @param nmSpc a namespace key to use for the type search when withNmSpc arg set UNLESS the dictionary field has a namespace alias qualified dataType. In that
	 *  	case the namespace alias is used if valid in lieu of the supplied namespace. @see getDDictFieldType(nm : String) implementation for details.
	 *  @param nm the name of the data dictionary field
	 *  @param withNmSpc the namespace is used to prefix the dataType name
	 *  @return a tuple (type rep string , corresponding base type) 
	 */
	private def getDDictFieldType(nmSpc : String, nm : String, withNmSpc : Boolean) : (String,BaseTypeDef) = {	
		val key : String = if (withNmSpc) nmSpc + "." + nm else nm
		var fldTypedef : BaseTypeDef = null
		val scalaType : String = if (ctx.dDict.contains(key)) {
			val fld : xDataField = ctx.dDict.apply(key)
			val fldType : String = fld.dataType
			
			val alias : String =  if (fldType.contains('.')) fldType.split('.').head else null
			val fldTypeName : String = if (fldType.contains('.')) fldType.split('.').tail.head else fldType
			val nameSpc : String = if (alias != null && ctx.NamespaceSearchMap.contains(alias)) ctx.NamespaceSearchMap.apply(alias) else nmSpc
					
			fldTypedef = mgr.ActiveType(MdMgr.MkFullName(nameSpc, fldTypeName)).asInstanceOf[BaseTypeDef]
			if (fldTypedef != null) {
				fldTypedef.typeString
			} else {
				/** Translate the possible builtin type to a scala equiv and try again. */
				val scalaVersionOfPmmlBuiltinType : String = PmmlTypes.scalaDataType(fldType)
				/** verify it is present in the metadata and get metadata description for it */
				fldTypedef = mgr.ActiveType(MdMgr.MkFullName(nmSpc, scalaVersionOfPmmlBuiltinType))
				if (fldTypedef != null) {
					fldTypedef.typeString
				} else {
					"None"
				}
			}
		} else null 
		(scalaType,fldTypedef)
	}

	/** Search this bad boy, with and without namespace qualifiers...
	 *  @param nm the name of the transformation dictionary derived field
	 *  @return a tuple (type rep string , corresponding base type) 
	 */
	def getXDictFieldType(nm : String) : (String,BaseTypeDef) = {
		var typeStr : String = null
		var baseType : BaseTypeDef = null
		val withNmSpc : Boolean = true
		breakable {
		  	val nmspcSrchPath : Array[(String,String)] = ctx.namespaceSearchPath
			nmspcSrchPath.foreach( nmSpc => {
		  		val (typeStrWOut, baseTypeWOut) = getXDictFieldType(nmSpc._2, nm : String, ! withNmSpc)
		  		if (baseTypeWOut != null) {
					typeStr = typeStrWOut
					baseType = baseTypeWOut
		  			break
		  		}
		  		val (typeStrWith, baseTypeWith) = getXDictFieldType(nmSpc._2, nm : String, withNmSpc)
		  		if (baseTypeWith != null) {
					typeStr = typeStrWith
					baseType = baseTypeWith
		  			break
		  		}
		  	})
		}

	  	(typeStr, baseType)
	}
	
	/** 
	 *  OK
	 *  Search for xDict field with and without namespace qualifiers...
	 *  @param nmSpc the namespace key to use for the search when withNmSpc arg set
	 *  @param nm the name of the transformation dictionary derived field
	 *  @param withNmSpc the namespace is used to prefix the dataType name
	 *  @return (typename string, typedef)
	 */
	private def getXDictFieldType(nmSpc : String, nm : String, withNmSpc : Boolean) : (String,BaseTypeDef) = {	
		val key : String = if (withNmSpc) nmSpc + "." + nm else nm
		var fldTypedef : BaseTypeDef = null
		val scalaType : String = if (ctx.xDict.contains(key)) {
			val fld : xDerivedField = ctx.xDict.apply(key)
			val fldType : String = fld.dataType
			
			val alias : String =  if (fldType.contains('.')) fldType.split('.').head else null
			val fldTypeName : String = if (fldType.contains('.')) fldType.split('.').tail.head else fldType
			val nameSpc : String = if (alias != null && ctx.NamespaceSearchMap.contains(alias)) ctx.NamespaceSearchMap.apply(alias) else nmSpc
			val nameKey : String = MdMgr.MkFullName(nameSpc, fldTypeName)
			fldTypedef = mgr.ActiveType(nameKey).asInstanceOf[BaseTypeDef]
			if (fldTypedef != null) {
				fldTypedef.typeString
			} else {
				/** Translate the possible builtin type to a scala equiv and try again. */
				val scalaVersionOfPmmlBuiltinType : String = PmmlTypes.scalaDataType(fldType)
				/** verify it is present in the metadata and get metadata description for it */
				fldTypedef = mgr.ActiveType(MdMgr.MkFullName(nmSpc, scalaVersionOfPmmlBuiltinType))
				if (fldTypedef != null) {
					fldTypedef.typeString
				} else {
					"None"
				}
			}
		} else null 
		(scalaType,fldTypedef)
	}

	/** 
	 *  Search the dictionaries for the supplied field....  Answer (scala typestring, BaseTypeDef) or (null,null) if not found.
	 *  Note: Precautions have been taken to guarantee that the Union(xdict names, ddict names) == Intersect (xdict names, ddict names)
	 *  
	 *  @param nm : String the field name 
	 *  @return pair (scala type string, baseTypeDef for named field's type)
	 */
		
	def getDictFieldType(nm : String) : (String,BaseTypeDef) = {
	  
		val (typeStr, typedef) : (String,BaseTypeDef) = if (ctx.TransformDict.contains(nm)) {
			getXDictFieldType(nm)
		} else {
			getDDictFieldType(nm)
		}

		(typeStr,typedef)
	}


	/** 
	 *  OK
	 *  Answer the typedef string and typedef for the supplied typename.
	 *  @param typename the type name of the transformation dictionary derived field
	 *  @return (type name string, typedef)
	 */
	def getType(typename : String) : (String,BaseTypeDef) = {
		var typeStr : String = null
		var baseType : BaseTypeDef = null
	  	if (typename != null && typename.size > 0 && typename.contains('.')) {
	  		val alias : String =  typename.split('.').head
	  		val restOfName : String =  typename.split('.').tail.head
	  		if (restOfName.contains('.')) {
	  			PmmlError.logError(ctx, s"Malformed type name argument... The type name '$typename' should either be an unqualified name or a namespace alias qualified name. See the wiki.")
	  		}
	  		val nmSpc : String = if (ctx.NamespaceSearchMap.contains(alias)) ctx.NamespaceSearchMap.apply(alias) else null
	  		baseType = if (nmSpc != null) mgr.ActiveType(nmSpc, restOfName) else null
	  		if (baseType != null) {
	  			typeStr = baseType.typeString
	  		}
	  	} else {
			breakable {
			  	val nmspcSrchPath : Array[(String,String)] = ctx.namespaceSearchPath
				nmspcSrchPath.foreach( nmSpc => {
			  		val fldTypedef : BaseTypeDef = mgr.ActiveType(nmSpc._2, typename)
					if (fldTypedef != null) {
						typeStr = fldTypedef.typeString
						baseType = fldTypedef
						break
					} else {
						/** Translate the possible builtin type to a scala equiv and try again. */
						val scalaVersionOfPmmlBuiltinType : String = PmmlTypes.scalaDataType(typename)
						/** verify it is present in the metadata and get metadata description for it */
						val fldTypedef : BaseTypeDef = mgr.ActiveType(nmSpc._2, scalaVersionOfPmmlBuiltinType)
						if (fldTypedef != null) {
							 typeStr = fldTypedef.typeString
							 baseType = fldTypedef
							 break
						}
					}
			  	})
			}
	  	}

	  	(typeStr, baseType)	
	}
	
	/** Answer the MessageDef for the supplied msgName.
	 *  @param msgName the type name of the transformation dictionary derived field
	 *  @return (msg type name string, MessageDef)
	 */
	def getMsg(msgName : String) : (String,MessageDef) = {
		var typeStr : String = null
		var msg : MessageDef = null
	  	if (msgName != null && msgName.size > 0 && msgName.contains('.')) {
	  		val alias : String =  msgName.split('.').head
	  		val restOfName : String =  msgName.split('.').tail.head
	  		if (restOfName.contains('.')) {
	  			PmmlError.logError(ctx, s"Malformed message name reference... The message name '$msgName' should either be an unqualified name or a namespace alias qualified name. See the wiki.")
	  		}
	  		val nmSpc : String = if (ctx.NamespaceSearchMap.contains(alias)) ctx.NamespaceSearchMap.apply(alias) else null
	  		msg = if (nmSpc != null) mgr.ActiveMessage(nmSpc, restOfName) else null
	  		if (msg != null) {
	  			typeStr = msg.typeString
	  		}
	  	} else {
			breakable {
			  	val nmspcSrchPath : Array[(String,String)] = ctx.namespaceSearchPath
				nmspcSrchPath.foreach( nmSpc => {
			  		val aMsg : MessageDef = mgr.ActiveMessage(nmSpc._2, msgName)
					if (aMsg != null) {
						typeStr = aMsg.typeString
						msg = aMsg
						break
					}
			  	})
			}
	  	}

	  	(typeStr, msg)	
	}

	/** Answer the ContainerDef for the supplied containerName.
	 *  @param containerName the type name of the transformation dictionary derived field
	 *  @return (container type name string, ContainerDef)
	 */
	def getContainer(containerName : String) : (String,ContainerDef) = {
		var typeStr : String = null
		var container : ContainerDef = null
	  	if (containerName != null && containerName.size > 0 && containerName.contains('.')) {
	  		val alias : String =  containerName.split('.').head
	  		val restOfName : String =  containerName.split('.').tail.head
	  		if (restOfName.contains('.')) {
	  			PmmlError.logError(ctx, s"Malformed message name reference... The message name '$containerName' should either be an unqualified name or a namespace alias qualified name. See the wiki.")
	  		}
	  		val nmSpc : String = if (ctx.NamespaceSearchMap.contains(alias)) ctx.NamespaceSearchMap.apply(alias) else null
	  		container = if (nmSpc != null) mgr.ActiveContainer(nmSpc, restOfName) else null
	  		if (container != null) {
	  			typeStr = container.typeString
	  		}
	  	} else {
			breakable {
			  	val nmspcSrchPath : Array[(String,String)] = ctx.namespaceSearchPath
				nmspcSrchPath.foreach( nmSpc => {
			  		val aContainer : ContainerDef = mgr.ActiveContainer(nmSpc._2, containerName)
					if (aContainer != null) {
						typeStr = aContainer.typeString
						container = aContainer
						break
					}
			  	})
			}
	  	}

	  	(typeStr, container)	
	}

	/** 
	 *  Answer whether this is a container that has a list of keys (MappedMsgTypeDef) or contains fields (StructTypeDef)
	 *  @param typedef A BaseTypeDef
	 *  @return true if typedef is any {StructTypeDef, MappedMsgTypeDef}
	 */
	def isContainerWithFieldOrKeyNames(typedef : BaseTypeDef) : Boolean = {
	  	(typedef != null && (typedef.isInstanceOf[StructTypeDef] || typedef.isInstanceOf[MappedMsgTypeDef]))
	}

	/** 
	 *  Answer whether this type is a ContainerTypeDef that has member types that are container types.
	 *  Nested collections are supported.
	 *  @param typedef A BaseTypeDef
	 *  @return true if this a collection with member types in {StructTypeDef, MappedMsgTypeDef}
	 */
	def collectMemberContainerTypes(typedef : BaseTypeDef) : Array[BaseTypeDef] = {
	  	var baseTypes : ArrayBuffer[BaseTypeDef] = ArrayBuffer[BaseTypeDef]()
	  	if (typedef != null && typedef.isInstanceOf[ContainerTypeDef]) {
	  		val container : ContainerTypeDef = typedef.asInstanceOf[ContainerTypeDef]
	  		val mbrTypes : Array[BaseTypeDef] = container.ElementTypes
	  		if (mbrTypes != null && mbrTypes.size > 0) {
	  			mbrTypes.foreach(typ => {
	  				val hasContainerType : Boolean = isContainerWithFieldOrKeyNames(typ)
	  				if (! hasContainerType && typ.isInstanceOf[ContainerTypeDef]) {
	  					val subTypes : Array[BaseTypeDef] = collectMemberContainerTypes(typ)  /** recurse here to look at the subtypes */
	  					if (subTypes.size > 0) {
	  						baseTypes ++= subTypes
	  					}
	  				} else {
	  					if (hasContainerType) {
	  						baseTypes += typ
	  					}
	  				}
	  			})
	  		}
	  	}
	  	baseTypes.toArray
	}

}




