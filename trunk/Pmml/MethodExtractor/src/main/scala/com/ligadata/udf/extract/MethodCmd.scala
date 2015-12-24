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

package com.ligadata.udf.extract

import scala.collection.mutable._
import util.control.Breaks._
import scala.util.matching.Regex
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.Exceptions.StackTrace
import com.ligadata.kamanja.metadata._


/** 
 	Class MethodCmd
 	
 	Prepare a MdMgr.MakeFunc command from the method information gleaned from an full object path sent to the MethodExtract.
 	As a side effect, update the typeMap with any types that are found in the object's methods' type signature.
 	
    Turn method information like this:
    
	    method MakeStrings ... 
	  		name = MakeStrings, 
	    	fullName = com.ligadata.pmml.udfs.Udfs.MakeStrings, 
	     	returnType = scala.Array[String], 
	      	typeSig = (arr: scala.Array[(String, String)], separator: String)scala.Array[String] 
	      	
		method Plus ... 
			name = Plus, 
			fullName = com.ligadata.pmml.udfs.Udfs.Plus, 
			returnType = scala.Float, 
			typeSig = (expr1: scala.Float, expr2: scala.Int)scala.Float
    
    into this:
       
		MdMgr.MakeFunc(mgr
				, MdMgr.sysNS, "Get"
				, (MdMgr.sysNS,"Any")
				, List(("gCtx", MdMgr.sysNS, "EnvContext")
				    ,("containerId", MdMgr.sysNS, "String")
				    ,("key", MdMgr.sysNS, "Int"))
				, false)   
 
		MdMgr.MakeFunc(mgr, "<namespace>", MdMgr.sysNS, "Get" , ("System","Any") , List(("gCtx", "System", "EnvContext") ,("containerId", "System", "String") ,("key", "System", "Int")), false)   
 		where namespace is the value passed to the MethodExtract application from user.
 		
 	In addition, there may be numerous types that are used in the methods that are not defined in the metadata.  To this end,
 	a Map[String, String] where the key is the namespace qualified name of the type and the value is the appropriate "Make" command 
 	for that type.
 	
 	For the collections, the name of the type generated will be the container type + "of" + the name of the element(s).
 	For example, a Set of Int is named SetOfInt, a List of Float is ListOfFloat, an ArrayBuffer of String is an ArrayBufferOfString,
 	a Map of String,Int pairs is a MapOfStringInt.
 	
 	These "Make" types strings will be emitted just like the "MakeFunc" strings. 
 	
 	@param logger the log4j logger in which to deposit diagnostics
 	@param namespace the command line supplied namespace name to be used to catalog the methods from the command line supplied object.
 	@param typeMap this map will be updated with the types that are used by the methods being processed in the object.
 	@param name this is the simple name of the function currently being processed.
 	@param fullName this is the full package qualified name of the function being processed.
 	@param returnType this is the return type of the function being processed
 	@param typeSig is the argument type list and the the return type suffixed to it of the current method
 	
 */


/** 
 *	Collect the basic elements if a FunctionDef with an instance of this:
 */
class FuncDefArgs (val namespace : String
				, val fcnName : String
				, val physicalName : String
				, val returnNmSpc : String
				, val returnTypeName : String
				, val argTriples : Array[(String,String,String)] 
				, val versionNo : Long
				, val hasIndefiniteArity : Boolean)
{}

class MethodCmd(  val mgr : MdMgr
				, val initialVersion : Long
				, val namespace : String
			    , var typeMap : Map[String, BaseElemDef]
			    , var typeArray : ArrayBuffer[BaseElemDef]
			    , val name : String
			    , val fullName : String
			    , val returnType : String
			    , val typeSig : String) extends LogTrait {  

	/** 
	 *  Answer a FuncDefArgs instance and a string representation of the MakeFunc command that would catalog
	 *  the function described by the constructor arguments.  The FuncDefArgs has all the information needed
	 *  to build a FunctionDef except for function's jar and the jars that its jar depends
	 */
	def makeFuncDef : (FuncDefArgs,String) = {

	  	val buffer : StringBuilder = new StringBuilder()
	  	val (returnTypeVal, baseElem, _) : (String, BaseElemDef, Boolean) = ReturnType
	  	val (argNm_NmSpc_TypNm, argTriples, hasIndefiniteArity) : (Array[String], Array[(String,String,String)], Boolean) = TypeSig

	  	buffer.append(s"MdMgr.MakeFunc(mgr, ${'"'}$namespace${'"'}, ")
	  	val rStr = returnTypeVal.toString
	  	val retElements : Array[String] = rStr.split('.')
	  	val retTypeStr = if (retElements.size == 2) {
	  		val rnmspc : String = retElements(0)
	  		val rnm : String = retElements(1)
	  		s"${'"'}$rnmspc${'"'}, ${'"'}$rnm${'"'}"
	  	} else {
	  		logger.error("The type generation has failed in some way for return type $rStr of function $fullName with args $typeSig" )
	  		"bogus return type"
	  	}

	  	buffer.append(s"${'"'}$name${'"'}, ($retTypeStr), List(")
	  	
	  	val argbuffer : StringBuilder = new StringBuilder
	  	argNm_NmSpc_TypNm.addString(argbuffer, ",")
	  	val argsStr : String = argbuffer.toString
	  	buffer.append(s"$argsStr")
	  	
	  	buffer.append(s"), null)\n")
	  	
	  	val funcDefArgs = new FuncDefArgs(namespace, name, fullName, baseElem.NameSpace, baseElem.Name, argTriples, initialVersion, hasIndefiniteArity)
	  	
	  	
	  	(funcDefArgs, buffer.toString)
	} 
	
	/** 
	 *  Answer the return type string with a namespace injected as necessary.
	 */
	private def ReturnType : (String, BaseElemDef, Boolean) = {
		collectType(returnType.split('.'), returnType)
	}

	/**
	 * 	Create an array of type string arguments, including variable names from the supplied typeSig on the ctor.
	 *  These strings look like this:
	 *  
	 *  	typeSig = (expr1: scala.Float, expr2: scala.Int)scala.Float
	 *   	typeSig = (arr: scala.Array[(String, String)], separator: String)scala.Array[String]
	 *    
	 *  Like the return type, each arg type is sought in the typeMap supplied to the ctor.  If it is present,
	 *  the type is used from the map.  If not present, the namespace qualified typename is used as the key
	 *  and a make<Type> command string is added to the map for it... any {MakeArray, MakeArrayBuffer, MakeSet, MakeMap, MakeList}
	 *  
	 *  @return a type string for each argument.
	 */
	private def TypeSig : (Array[String], Array[(String,String,String)], Boolean) = {
	  
		var typeArgTriples : ArrayBuffer[(String,String,String)] = ArrayBuffer[(String,String,String)]()
		
		val typeSigArgs : String = encloseElementArgs(typeSig, '(', ')')
		val (nm_nmspc_typnm, hasIndefiniteArity) : (Array[String],Boolean) = if (typeSigArgs == "") {
			(Array[String](),false)
		} else {	
			val reNames = "[a-zA-Z0-9_]+:".r
			var argNames : Array[String] = null
			val reTypes = "[a-zA-Z0-9_]+:|, [a-zA-Z0-9_]+: "
			var argTypes : Array[String] = null
		  
			try {
				argNames = ((reNames findAllIn typeSigArgs).mkString(",")).split(',').map(nm => nm.stripSuffix(":"))
				argTypes = typeSigArgs.split(reTypes).filter(_.length > 0).map(typ => typ.trim)
			} catch {
				case e : Exception => { val stackTrace = StackTrace.ThrowableTraceString(e)	
          logger.debug("StackTrace:"+stackTrace)}
			}
			
			if (argNames.size == 0 || argTypes.size == 0) {
				(Array[String](), false)
			} else {
				/** a few transformations... */
				
				val hasQName : Boolean = (argNames.filter(_ == "q").size > 0)
				if (hasQName) {
					val stop : Int = 0
				}
				
				/** Collect the types of each argument */
				val argsWithNmSpcFixed : Array[(String,BaseElemDef,Boolean)] = argTypes.map( argtype => {
					val argNodes : Array[String] = argtype.split('.')
					if (argtype.contains("scala.Double, scala.Double, scala.Double")) {
						val stop : Int = 0
					}
					collectType(argNodes, argtype)
				})
				
				/** transform the names and the argtype namespace and typename to a triple */
				val nameAndTypePair = argNames.zip(argsWithNmSpcFixed)
				val nameNmSpcTypNameTriples : Array[(String,String,String)] = nameAndTypePair.map( pair => {
				  val (name, typeNSNmTypeTriple) = pair
				  val (typeNSNm, _, _) : (String, BaseElemDef, Boolean) = typeNSNmTypeTriple
				  val typePairSplit = typeNSNm.split('.')
				  val typePair  = typePairSplit.map(_.trim)  
				  
				  (name.trim,typePair(0),typePair(1))
				})
				/** transform the triple into a single string */
				val nmnmspctyptrip : Array[String] =  nameNmSpcTypNameTriples.map ( tup => {
					val (nm,nmspc,typnm) = tup
					typeArgTriples += tup /** ... and remember the triple too */
					s"($nm, $nmspc, $typnm)"
				})
				val repeatingArg : Boolean = argsWithNmSpcFixed.last._3
				(nmnmspctyptrip,repeatingArg)
			}
		}
		(nm_nmspc_typnm, typeArgTriples.toArray, hasIndefiniteArity)
	}

	/** 
	 *  The current type being processed (either from the return type or one of the function arguments) has more than
	 *  two nodes delimited by dots (e.g., scala.collection.mutable.ArrayBuffer[Float]).  Determine the generated name
	 *  for this type, see if it is present in the typeMap passed to the primary constructor.  If not present, create a 
	 *  the appropriate Make<Type> string for it ... any {MakeArray, MakeArrayBuffer, MakeSet, MakeMap, MakeList}
	 *  
	 *  @param typeParts the nodes of the type string split on the dots
	 *  @return type string for this type fully qualified with '.' as delimiter and the BaseElemDef for it
	 */
	private def collectType(typeParts : Array[String], typeString : String) : (String, BaseElemDef, Boolean) = {
	  	val (typeCollected, typeInstance, hasIndefiniteArity) : (String, BaseElemDef, Boolean) = if (typeParts.size == 0) ("", null, false) else {
			val (unQualifiedTypeName, isRepeatingArg) : (String, Boolean) = typePartsLast(typeParts, typeString) 
			val (typeName, typInst) : (String, BaseElemDef) = unQualifiedTypeName match {
				case "List" => ListType(unQualifiedTypeName, typeParts)
				case "Array" => ArrayType(unQualifiedTypeName, typeParts)
				case "Array[T]" => ArrayType(unQualifiedTypeName, typeParts)
				case "ArrayBuffer" => ArrayBufferType(unQualifiedTypeName, typeParts)
				case "ArrayBuffer[T]" => ArrayBufferType(unQualifiedTypeName, typeParts)
				case "Map" => MapType(unQualifiedTypeName, typeParts)
				case "Map[A,B]" => MapType(unQualifiedTypeName, typeParts)
				case "Set" => SetType(unQualifiedTypeName, typeParts)
				case "TreeSet" => TreeSetType(unQualifiedTypeName, typeParts)			
				case "TreeSet[T]" => TreeSetType(unQualifiedTypeName, typeParts)			
				case "Set[T]" => SetType(unQualifiedTypeName, typeParts)
				case _ => {
					if (unQualifiedTypeName.startsWith("List")) {
						ListType(unQualifiedTypeName,typeParts)
					} else {
						if (unQualifiedTypeName.startsWith("ArrayBuffer")) {
							ArrayBufferType(unQualifiedTypeName,typeParts)
						} else {
							if (unQualifiedTypeName.startsWith("Array")) {
								ArrayType(unQualifiedTypeName,typeParts)
							} else {
								if (unQualifiedTypeName.startsWith("Map")) {
									MapType(unQualifiedTypeName,typeParts)				
								} else {
									if (unQualifiedTypeName.startsWith("TreeSet")) {
										TreeSetType(unQualifiedTypeName,typeParts)
									} else {
										if (unQualifiedTypeName.startsWith("SortedSet")) {
											SortedSetType(unQualifiedTypeName,typeParts)
										} else {
											if (unQualifiedTypeName.startsWith("Set")) {
												SetType(unQualifiedTypeName,typeParts)
											} else {
												if (unQualifiedTypeName.startsWith("Queue")) {
													QueueType(unQualifiedTypeName,typeParts)
												} else {
													if (unQualifiedTypeName.startsWith("HashMap")) {
														HashMapType(unQualifiedTypeName,typeParts)
													} else {
														if (unQualifiedTypeName.startsWith("Tuple")) {
															TupleType(unQualifiedTypeName,typeParts)
														} else {
															SimpleType(unQualifiedTypeName, typeParts)
														}
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
			(typeName, typInst, isRepeatingArg)
	  	}
	  	(typeCollected,typeInstance,hasIndefiniteArity)
	}
	
	/** 
	 *  A List type with the item qualifier in brackets is presented. 
	 *  e.g., scala.collection.mutable.List[Float] => System.ListOfFloat
	 */
	private def ListType(unQualifiedTypeName : String, typeParts : Array[String]) : (String, BaseElemDef) = {
  		val isImmutable : Boolean = (typeParts.filter(part => part == "immutable").size > 0)

  		val itemKey = formKeyFromElementSpecifiers(unQualifiedTypeName)
		val key = s"System.ListOf$itemKey"
		val elem : BaseElemDef = if (! typeMap.contains(key)) {
			val nmspcAndNm : Array[String] = key.split('.')
			val (nmspc,nm) : (String, String) = (nmspcAndNm(0), nmspcAndNm(1))
			val itmNmSpc : Array[(String,String)] = formNmSpcQualifedElementSpecifiers(unQualifiedTypeName)
			val (itmnmspc,itmnm) : (String, String) = itmNmSpc(0)
			val listElem : ListTypeDef = if (isImmutable) { 
				logger.error("Immutable Lists are not currently supported")
				//mgr.AddImmutableList(nmspc, nm, itmnmspc, itmnm, initialVersion)
				val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
				val elem : BaseTypeDef = if (alreadyCataloged) {
					mgr.ActiveType(nmspc, nm) 
				} else {
					mgr.AddList(nmspc, nm, itmnmspc, itmnm, initialVersion)
					mgr.ActiveType(nmspc, nm) 
				}
				elem.asInstanceOf[ListTypeDef]				
			} else {
				val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
				val elem : BaseTypeDef = if (alreadyCataloged) {
					mgr.ActiveType(nmspc, nm) 
				} else {
					mgr.AddList(nmspc, nm, itmnmspc, itmnm, initialVersion)
					mgr.ActiveType(nmspc, nm) 
				}
				elem.asInstanceOf[ListTypeDef]				
			}
			typeMap(key) = listElem
			typeArray += listElem			
			listElem
	  	} else {
	  		typeMap(key)
	  	}
	  	(key, elem)
	}
	
	/** 
	 *  A Queue type with the item qualifier in brackets is presented. 
	 *  e.g., scala.collection.mutable.Queue[Float] => System.QueueOfFloat
	 */
	private def QueueType(unQualifiedTypeName : String, typeParts : Array[String]) : (String, BaseElemDef) = {
  		val isImmutable : Boolean = (typeParts.filter(part => part == "immutable").size > 0)

  		val itemKey = formKeyFromElementSpecifiers(unQualifiedTypeName)
		val key = s"System.QueueOf$itemKey"
		val elem : BaseElemDef = if (! typeMap.contains(key)) {
			val nmspcAndNm : Array[String] = key.split('.')
			val (nmspc,nm) : (String, String) = (nmspcAndNm(0), nmspcAndNm(1))
			val itmNmSpc : Array[(String,String)] = formNmSpcQualifedElementSpecifiers(unQualifiedTypeName)
			val (itmnmspc,itmnm) : (String, String) = itmNmSpc(0)	
			val queueElem : QueueTypeDef = if (isImmutable) { 
				logger.error("Immutable Queues are not currently supported")
				//mgr.AddImmutableQueue(nmspc, nm, itmnmspc, itmnm, initialVersion)
				val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
				val elem : BaseTypeDef = if (alreadyCataloged) {
					mgr.ActiveType(nmspc, nm) 
				} else {
					mgr.AddQueue(nmspc, nm, itmnmspc, itmnm, initialVersion)
					mgr.ActiveType(nmspc, nm) 
				}
				elem.asInstanceOf[QueueTypeDef]
			} else {
				val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
				val elem : BaseTypeDef = if (alreadyCataloged) {
					mgr.ActiveType(nmspc, nm) 
				} else {
					mgr.AddQueue(nmspc, nm, itmnmspc, itmnm, initialVersion)
					mgr.ActiveType(nmspc, nm) 
				}
				elem.asInstanceOf[QueueTypeDef]
			}
			typeMap(key) = queueElem
			typeArray += queueElem			
			queueElem
	  	} else {
	  		typeMap(key)
	  	}
	  	(key, elem)
	}
	
	/** 
	 *  An Array type with the element qualifier in brackets is presented. 
	 *  e.g., scala.collection.mutable.Array[Float] => System.ArrayOfFloat
	 */
	private def ArrayType(unQualifiedTypeName : String, typeParts : Array[String]) : (String, BaseElemDef) = {
  		val isImmutable : Boolean = (typeParts.filter(part => part == "immutable").size > 0)

 		val itemKey = formKeyFromElementSpecifiers(unQualifiedTypeName)
		val key = s"System.ArrayOf$itemKey"
		val elem : BaseElemDef = if (! typeMap.contains(key)) {
			val nmspcAndNm : Array[String] = key.split('.')
			val (nmspc,nm) : (String, String) = (nmspcAndNm(0), nmspcAndNm(1))
			val itmNmSpc : Array[(String,String)] = formNmSpcQualifedElementSpecifiers(unQualifiedTypeName)
			val (itmnmspc,itmnm) : (String, String) = itmNmSpc(0)	
			val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
			val elem : BaseTypeDef = if (alreadyCataloged) {
				mgr.ActiveType(nmspc, nm) 
			} else {
				mgr.AddArray(nmspc, nm, itmnmspc, itmnm, 1, initialVersion)
				mgr.ActiveType(nmspc, nm) 
			}
			typeMap(key) = elem
			typeArray += elem			
			elem
	  	} else {
	  		typeMap(key)
	  	}
	  	(key, elem)
	}
	
	/** 
	 *  An ArrayBuffer type with the element qualifier in brackets is presented. 
	 *  e.g., scala.collection.mutable.ArrayBuffer[Float] => System.ArrayBufferOfFloat
	 */
	private def ArrayBufferType(unQualifiedTypeName : String, typeParts : Array[String]) : (String, BaseElemDef) = {
  		val isImmutable : Boolean = (typeParts.filter(part => part == "immutable").size > 0)

 		val itemKey = formKeyFromElementSpecifiers(unQualifiedTypeName)
		val key = s"System.ArrayBufferOf$itemKey"
		val elem : BaseElemDef = if (! typeMap.contains(key)) {
			val nmspcAndNm : Array[String] = key.split('.')
			val (nmspc,nm) : (String, String) = (nmspcAndNm(0), nmspcAndNm(1))
			val itmNmSpc : Array[(String,String)] = formNmSpcQualifedElementSpecifiers(unQualifiedTypeName)
			val (itmnmspc,itmnm) : (String, String) = itmNmSpc(0)	
			val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
			val elem : BaseTypeDef = if (alreadyCataloged) {
				mgr.ActiveType(nmspc, nm) 
			} else {
				mgr.AddArrayBuffer(nmspc, nm, itmnmspc, itmnm, 1, initialVersion)
				mgr.ActiveType(nmspc, nm) 
			}
			typeMap(key) = elem
			typeArray += elem			
			elem
	  	} else {
	  		typeMap(key)
	  	}
	  	(key, elem)
	}
	
	/** 
	 *  A Map type with the key/value in brackets is presented. 
	 *  e.g., scala.collection.mutable.Map[String, Float] => System.MapOfStringFloat
	 */
	private def MapType(unQualifiedTypeName : String, typeParts : Array[String]) : (String, BaseElemDef) = {
  		val isImmutable : Boolean = (typeParts.filter(part => part == "immutable").size > 0)

 		val itemKey = formKeyFromElementSpecifiers(unQualifiedTypeName)
		val key = s"System.MapOf$itemKey"
		val elem : BaseElemDef = if (! typeMap.contains(key)) {
			val nmspcAndNm : Array[String] = key.split('.')
			val (nmspc,nm) : (String, String) = (nmspcAndNm(0), nmspcAndNm(1))
			val itmNmSpc : Array[(String,String)] = formNmSpcQualifedElementSpecifiers(unQualifiedTypeName)
			val (keynmspc,keynm) : (String, String) = itmNmSpc(0)	
			val (valnmspc,valnm) : (String, String) = itmNmSpc(1)	
			val mapElem : ContainerTypeDef = if (isImmutable) { 
				val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
				val elem : BaseTypeDef = if (alreadyCataloged) {
					mgr.ActiveType(nmspc, nm) 
				} else {
					mgr.AddImmutableMap(nmspc, nm, (keynmspc,keynm), (valnmspc,valnm), initialVersion)
					mgr.ActiveType(nmspc, nm) 
				}
				elem.asInstanceOf[ContainerTypeDef]	
			} else {
				val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
				val elem : BaseTypeDef = if (alreadyCataloged) {
					mgr.ActiveType(nmspc, nm) 
				} else {
					mgr.AddMap(nmspc, nm, (keynmspc,keynm), (valnmspc,valnm), initialVersion)
					mgr.ActiveType(nmspc, nm) 
				}
				elem.asInstanceOf[ContainerTypeDef]	
			}
			typeMap(key) = mapElem
			typeArray += mapElem			
			mapElem
	  	} else {
	  		typeMap(key)
	  	}
	  	(key, elem)
	}
	
	/** 
	 *  A HashMapType type with the key/value in brackets is presented. 
	 *  e.g., scala.collection.mutable.Map[String, Float] => System.MapOfStringFloat
	 */
	private def HashMapType(unQualifiedTypeName : String, typeParts : Array[String]) : (String, BaseElemDef) = {
  		val isImmutable : Boolean = (typeParts.filter(part => part == "immutable").size > 0)

 		val itemKey = formKeyFromElementSpecifiers(unQualifiedTypeName)
		val key = s"System.HashMapOf$itemKey"
		val elem : BaseElemDef = if (! typeMap.contains(key)) {
			val nmspcAndNm : Array[String] = key.split('.')
			val (nmspc,nm) : (String, String) = (nmspcAndNm(0), nmspcAndNm(1))
			val itmNmSpc : Array[(String,String)] = formNmSpcQualifedElementSpecifiers(unQualifiedTypeName)
			val (keynmspc,keynm) : (String, String) = itmNmSpc(0)	
			val (valnmspc,valnm) : (String, String) = itmNmSpc(1)	
			val mapElem : HashMapTypeDef = if (isImmutable) { 
				logger.error("Immutable HashMaps are not currently supported")
				//mgr.AddImmutableHashMap(nmspc, nm, (keynmspc,keynm), (valnmspc,valnm), initialVersion)
				val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
				val elem : BaseTypeDef = if (alreadyCataloged) {
					mgr.ActiveType(nmspc, nm) 
				} else {
					mgr.AddHashMap(nmspc, nm, (keynmspc,keynm), (valnmspc,valnm), initialVersion)
					mgr.ActiveType(nmspc, nm) 
				}
				elem.asInstanceOf[HashMapTypeDef]
			} else {
				val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
				val elem : BaseTypeDef = if (alreadyCataloged) {
					mgr.ActiveType(nmspc, nm) 
				} else {
					mgr.AddHashMap(nmspc, nm, (keynmspc,keynm), (valnmspc,valnm), initialVersion)
					mgr.ActiveType(nmspc, nm) 
				}
				elem.asInstanceOf[HashMapTypeDef]
			}
			typeMap(key) = mapElem
			typeArray += mapElem			
			mapElem
	  	} else {
	  		typeMap(key)
	  	}
	  	(key, elem)
	}
	
	/** 
	 *  A Set type with the key in brackets is presented. 
	 *  e.g., scala.collection.mutable.Set[String]
	 */
	private def SetType(unQualifiedTypeName : String, typeParts : Array[String]) : (String, BaseElemDef) = {
  		val isImmutable : Boolean = (typeParts.filter(part => part == "immutable").size > 0)

 		val itemKey = formKeyFromElementSpecifiers(unQualifiedTypeName)
		val key = s"System.SetOf$itemKey"
		val elem : BaseElemDef = if (! typeMap.contains(key)) {
			val nmspcAndNm : Array[String] = key.split('.')
			val (nmspc,nm) : (String, String) = (nmspcAndNm(0), nmspcAndNm(1))
			val itmNmSpc : Array[(String,String)] = formNmSpcQualifedElementSpecifiers(unQualifiedTypeName)
			val (itmnmspc,itmnm) : (String, String) = itmNmSpc(0)	
			val setElem : ContainerTypeDef = if (isImmutable) { 
				val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
				val elem : BaseTypeDef = if (alreadyCataloged) {
					mgr.ActiveType(nmspc, nm) 
				} else {
					mgr.AddImmutableSet(nmspc, nm, itmnmspc, itmnm, initialVersion)
					mgr.ActiveType(nmspc, nm) 
				}
				elem.asInstanceOf[ContainerTypeDef]
			} else {
				val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
				val elem : BaseTypeDef = if (alreadyCataloged) {
					mgr.ActiveType(nmspc, nm) 
				} else {
					mgr.AddSet(nmspc, nm, itmnmspc, itmnm, initialVersion)
					mgr.ActiveType(nmspc, nm) 
				}
				elem.asInstanceOf[ContainerTypeDef]
			}
			typeMap(key) = setElem
			typeArray += setElem			
			setElem
	  	} else {
	  		typeMap(key)
	  	}
	  	(key, elem)
	}
	
	/** 
	 *  A TreeSet type with the key in brackets is presented. 
	 *  e.g., scala.collection.mutable.TreeSet[String]
	 */
	private def TreeSetType(unQualifiedTypeName : String, typeParts : Array[String]) : (String, BaseElemDef) = {
  		val isImmutable : Boolean = (typeParts.filter(part => part == "immutable").size > 0)

 		val itemKey = formKeyFromElementSpecifiers(unQualifiedTypeName)
		val key = s"System.TreeSetOf$itemKey"
		val elem : BaseElemDef = if (! typeMap.contains(key)) {
			val nmspcAndNm : Array[String] = key.split('.')
			val (nmspc,nm) : (String, String) = (nmspcAndNm(0), nmspcAndNm(1))
			val itmNmSpc : Array[(String,String)] = formNmSpcQualifedElementSpecifiers(unQualifiedTypeName)
			val (itmnmspc,itmnm) : (String, String) = itmNmSpc(0)	
			val setElem : TreeSetTypeDef = if (isImmutable) { 
				logger.error("Immutable TreeSets are not currently supported")
				//mgr.AddImmutableTreeSet(nmspc, nm, itmnmspc, itmnm, initialVersion)
				val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
				val elem : BaseTypeDef = if (alreadyCataloged) {
				  mgr.ActiveType(nmspc, nm) 
				} else {
				  mgr.AddTreeSet(nmspc, nm, itmnmspc, itmnm, initialVersion)
				  mgr.ActiveType(nmspc, nm) 
				}
				elem.asInstanceOf[TreeSetTypeDef]
			} else {
				val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
				val elem : BaseTypeDef = if (alreadyCataloged) {
					mgr.ActiveType(nmspc, nm) 
				} else {
					mgr.AddTreeSet(nmspc, nm, itmnmspc, itmnm, initialVersion)
					mgr.ActiveType(nmspc, nm) 
				}
				elem.asInstanceOf[TreeSetTypeDef]
			}
			typeMap(key) = setElem
			typeArray += setElem			
			setElem
	  	} else {
	  		typeMap(key)
	  	}
	  	(key, elem)
	}
	
	/** 
	 *  A SortedSet type with the key in brackets is presented. 
	 *  e.g., scala.collection.mutable.SortedSet[String]
	 */
	private def SortedSetType(unQualifiedTypeName : String, typeParts : Array[String]) : (String, BaseElemDef) = {
  		val isImmutable : Boolean = (typeParts.filter(part => part == "immutable").size > 0)

 		val itemKey = formKeyFromElementSpecifiers(unQualifiedTypeName)
		val key = s"System.SortedSetOf$itemKey"
		val elem : BaseElemDef = if (! typeMap.contains(key)) {
			val nmspcAndNm : Array[String] = key.split('.')
			val (nmspc,nm) : (String, String) = (nmspcAndNm(0), nmspcAndNm(1))
			val itmNmSpc : Array[(String,String)] = formNmSpcQualifedElementSpecifiers(unQualifiedTypeName)
			val (itmnmspc,itmnm) : (String, String) = itmNmSpc(0)	
			val setElem : SortedSetTypeDef = if (isImmutable) { 
				logger.error("Immutable SortedSets are not currently supported")
				//mgr.AddImmutableSortedSet(nmspc, nm, itmnmspc, itmnm, initialVersion)
				val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
				val elem : BaseTypeDef = if (alreadyCataloged) {
					mgr.ActiveType(nmspc, nm) 
				} else {
					mgr.AddSortedSet(nmspc, nm, itmnmspc, itmnm, initialVersion)
					mgr.ActiveType(nmspc, nm) 
				}
				elem.asInstanceOf[SortedSetTypeDef]
			} else {
				val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, nm) != null)
				val elem : BaseTypeDef = if (alreadyCataloged) {
					mgr.ActiveType(nmspc, nm) 
				} else {
					mgr.AddSortedSet(nmspc, nm, itmnmspc, itmnm, initialVersion)
					mgr.ActiveType(nmspc, nm) 
				}
				elem.asInstanceOf[SortedSetTypeDef]
			}
			typeMap(key) = setElem
			typeArray += setElem			
			setElem
	  	} else {
	  		typeMap(key)
	  	}
	  	(key, elem)
	}
	
	/** 
	 *  A scalar or other simple type 
	 */
	private def SimpleType(unQualifiedTypeName : String, typeParts : Array[String]) : (String, BaseElemDef) = {
	  	var key : String = s"System.$unQualifiedTypeName"
	  	//logger.debug(s"SimpleType(unQualifiedTypeName = $unQualifiedTypeName)")

	  	if (unQualifiedTypeName.contains("Double")) {
	  		val stop : Int = 0
	  	}
	  	
	  	val elem : BaseElemDef = if (! typeMap.contains(key)) {
			val nmspcAndNm : Array[String] = key.split('.')
			val (nmspc,nm) : (String, String) = (nmspcAndNm(0), nmspcAndNm(1))
			
			if (nm == "T") {
				val stop : Int = 0
			}
			val coercedNm : String = if (nm.size == 1 && AlphaCaps.contains(nm)) {
	  			"Any"
	  		} else {
	  			nm
	  		}
			
			val typ : ObjType.Type = ObjType.fromString(coercedNm)
			val alreadyCataloged : Boolean = (mgr.ActiveType(nmspc, coercedNm) != null)
			val scalarElem : BaseTypeDef = if (alreadyCataloged) {
				mgr.ActiveType(nmspc, coercedNm) 
			} else {
				mgr.AddScalar(nmspc, coercedNm, typ, ObjType.asString(typ), initialVersion)
				mgr.ActiveType(nmspc, coercedNm) 
			}
			/** redo the key as it may have been coerced to "Any" */
			key = s"System.$coercedNm"
			typeMap(key) = scalarElem
			typeArray += scalarElem			
			scalarElem
	  	} else {
	  		typeMap(key)
	  	}
	  	(key, elem)
	}
	
	/**
	 * Handle the explicit Tuple<N>[type,type,....,typeN] types.  For example,
	 * 
	 * 	def ToArrayOfInt(tuple : Tuple6[Any,Any,Any,Any,Any,Any]) : Array[Int]  => arg type name is TupleOfAny6
	 * 	def SumSomeScalars(tuple : Tuple4[Float,Double,Int,Long]) : Double => arg type name is TupleOfFloatDoubleIntLong
	 *  
	 *  FIXME: This code will not support member types that also have member types (e.g., Tuple2[scala.Array[String],Map[String,scala.Array[String,Map[String,scala.Array[String]]]]])
	 *  Recursion needs to be introduced here by asking the formKeyFromElementSpecifiers method for the type string.
	 */
	private def TupleType(unQualifiedTypeName : String, typeParts : Array[String]) : (String, BaseElemDef) = {
		val (key,tups) : (String,Array[(String,String)]) = if (unQualifiedTypeName.contains("[")) {
			val buffer : StringBuilder = new StringBuilder()
			val args : String = encloseElementArgs(unQualifiedTypeName, '[', ']')  
		  	val types : Array[String] = args.split(',')
		  	
		  	/** Map template symbols to Any before generation */
		  	val typesX : Array[String] = types.map(typ => {
		  		if (typ.size == 1 && AlphaCaps.contains(typ)) "Any" else typ
		  	})
		  		
		  	/** Generate the type name */
		  	val allSameElemTypes : Boolean = (typesX.toSet.size == 1)
		  	val typeName : String = if (allSameElemTypes) {
		  		val size = typesX.size
		  		val elemType = typesX(0)
		  		s"Tupleof$elemType$size"
		  	} else {
		  		typesX.addString(buffer, "").toString
			}
		  	buffer.clear
		  	val tuples : Array[(String,String)] = typesX.map( typ => ("System", typ.split('.').last.trim))
		  	(s"System.$typeName",tuples)
		} else {
			(s"System.$unQualifiedTypeName", Array[(String,String)]())
		}
		
	  	val elem : BaseElemDef = if (! typeMap.contains(key)) {
			val nmspcAndNm : Array[String] = key.split('.')
			val (nmspc,nm) : (String, String) = (nmspcAndNm(0), nmspcAndNm(1))
			/** Generate the tuples array ... NOTE:  The types from metadata can be prefixed with pkg qualifiers... eliminate the prefix portion */
			mgr.AddTupleType(nmspc, nm, tups, initialVersion)
			val tupElem : BaseTypeDef = mgr.ActiveType(nmspc,nm)
			typeMap(key) = tupElem
			typeArray += tupElem			
			tupElem
	  	} else {
	  		typeMap(key)
	  	}
	  
	  	(key, elem)
	}
	
	
	/**
	 *    This version of TupleType handles the "(type,type,...,typeN)" form.
	 *    
	 *  FIXME: This code will not support member types that also have member types (e.g., Tuple2[scala.Array[String],Map[String,scala.Array[String,Map[String,scala.Array[String]]]]])
	 *  Recursion needs to be introduced here by asking the formKeyFromElementSpecifiers method for the type string.
	 */
	
	private def TupleType(tupleTypeStr : String) : (String, BaseElemDef) = {
		val buffer : StringBuilder = new StringBuilder()
		val args : String = encloseElementArgs(tupleTypeStr, '(', ')')  
	  	val types : Array[String] = args.split(',')
	  	
		if (tupleTypeStr == "(T, U)") {
			val stop : Int = 0
		}
				
	  	/** Map template symbols to Any before generation */
	  	val typesX : Array[String] = types.map(typ => {
	  		val trimmedTyp : String = typ.trim
			val argNodes : Array[String] = trimmedTyp.split('.')
			/** NOTE: indefinite arity should never be set here */
			val (typeStr, elemDef, hasIndefArity) : (String, BaseElemDef, Boolean) = collectType(argNodes, trimmedTyp)
			if (hasIndefArity) {
				val stop : Boolean = true
			}
			val typeStrSansNmSpc : String = typeStr.split('.').last
	  		if (typeStrSansNmSpc.size == 1 && AlphaCaps.contains(typeStrSansNmSpc)) "Any" else typeStrSansNmSpc
	  	})
	  		
	  	/** Generate the type name */
	  	val allSameElemTypes : Boolean = (typesX.toSet.size == 1)
	  	val typeName : String = if (allSameElemTypes) {
	  		val size = typesX.size
	  		val elemType = typesX(0)
	  		s"TupleOf$elemType$size"
	  	} else {
	  		buffer.append("TupleOf")
	  		typesX.addString(buffer, "").toString
		}
	  	buffer.clear

	  	val key : String = s"System.$typeName"
	  	val elem : BaseElemDef = if (! typeMap.contains(key)) {
			val nmspcAndNm : Array[String] = key.split('.')
			val (nmspc,nm) : (String, String) = (nmspcAndNm(0), nmspcAndNm(1))
			/** Generate the tuples array ... NOTE:  The types from metadata can be prefixed with pkg qualifiers... eliminate the prefix portion */
			val tuples : Array[(String,String)] = typesX.map( typ => ("System", typ.split('.').last.trim))
			
			mgr.AddTupleType(nmspc, nm, tuples, initialVersion)
			val tupElem : BaseTypeDef = mgr.ActiveType(nmspc,nm)
			typeMap(key) = tupElem
			typeArray += tupElem			
			tupElem
	  	} else {
	  		typeMap(key)
	  	}
		
	  	(key, elem)
	}

	private val AlphaCaps : String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

	private def formKeyFromElementSpecifiers(unQualifiedTypeName : String) : String = {
		val buffer : StringBuilder = new StringBuilder()
		val args : String = encloseElementArgs(unQualifiedTypeName, '[', ']')
	  	val types : Array[String] = args.split(',')
	  	val typesRecursed : Array[(String, BaseElemDef, Boolean)] = types.map( typ => {
	  		if (typ.contains("[")) {
	  			collectType(typ.split('.'), typ)
	  		} else {
	  			val key : String = if (typ.size == 1 && AlphaCaps.contains(typ)) {
	  				"Any"
	  			} else {
	  				typ
	  			}
	  			val elem : BaseElemDef = mgr.ActiveType("System", key) 
	  			(key, elem, false)
	  		}
	  	})
	  	if (args.size == 1 && AlphaCaps.contains(args)) {
	  		buffer.append("Any")
	  	} else {
	  		val typesWithoutDots : Array[String] = typesRecursed.map( typTrip => { 
	  			val (typStr, typedef, hasIndefArity) : (String, BaseElemDef, Boolean) = typTrip
	  			typStr.split('.').last 
	  		})
	  		typesWithoutDots.addString(buffer, "")
	  	}		
	  	val argKey : String = buffer.toString
	  	argKey
	}
	
	/** Each argument type of the element arguments, pair with the System namespace and quote them each */
	private def formNmSpcQualifedElementSpecifiers(unQualifiedTypeName : String) : Array[(String,String)] = {
		val args : String = encloseElementArgs(unQualifiedTypeName, '[', ']')
	  	val types : Array[String] = args.split(',')
	  	val typesRecursed : Array[(String, BaseElemDef, Boolean)] = types.map( typ => {
	  		if (typ.contains("[")) {
	  			collectType(typ.split('.'), typ)
	  		} else {
	  			val key : String = if (typ.size == 1 && AlphaCaps.contains(typ)) {
	  				"Any"
	  			} else {
	  				typ
	  			}
	  			val elem : BaseElemDef = mgr.ActiveType("System", key) 
	  			(key, elem, false)
	  		}
	  	})
	  	val nmspcdAndQuoted = if (args.size == 1 && AlphaCaps.contains(args)) {
	  		Array(("System","Any"))
	  	} else {
	  		val typesWithoutDots : Array[String] = typesRecursed.map( typTrip => { 
	  			val (typStr, typedef, hasIndefArity) : (String, BaseElemDef, Boolean) = typTrip   		  
	  			typStr.split('.').last 		
	  		})
	  		typesWithoutDots.map( typ => ("System", typ))
	  	}		
	  	nmspcdAndQuoted
	}
	
	/** Each argument type of the element arguments, pair with the System namespace and quote them each */
	private def formQuotedNmSpcQualifedElementSpecifiers(unQualifiedTypeName : String) : Array[(String,String)] = {
		val args : String = encloseElementArgs(unQualifiedTypeName, '[', ']')
	  	val types : Array[String] = args.split(',')
	  	val typesRecursed : Array[(String, BaseElemDef, Boolean)] = types.map( typ => {
	  		if (typ.contains("[")) {
	  			collectType(typ.split('.'), typ)
	  		} else {
	  			val elem : BaseElemDef = mgr.ActiveType("System", typ) 
	  			(typ, elem, false)
	  		}
	  	})
	  	val nmspcdAndQuoted = if (args.size == 1 && AlphaCaps.contains(args)) {
	  		Array((s"${'"'}System${'"'}",s"${'"'}Any${'"'}"))
	  	} else {
	  		val typesWithoutDots : Array[String] = typesRecursed.map( typTrip => { 
	  			val (typStr, typedef, hasIndefArity) : (String, BaseElemDef, Boolean) = typTrip   		  
	  			typStr.split('.').last 		
	  		})
	  		typesWithoutDots.map( typ => (s"${'"'}System${'"'}", s"${'"'}$typ${'"'}"))
	  	}		
	  	nmspcdAndQuoted
	}
	
	/**
	 * Used by the various type makers above, split the qualified name into a namespace
	 * and name, returning the values as quoted strings.
	 */
	private def splitAndQuote(qualifiedTypeName : String) : Array[String] = {
		val splitAndQuoted : Array[String] = qualifiedTypeName.split('.').map( itm => {
			s"${'"'}$itm${'"'}"
		})
		/** there should only be two elements post split */
		splitAndQuoted
	}
	
	private def encloseElementArgs(typeString : String, openBracketOrParen : Char, closeBracketOrParen : Char) : String = {
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
	 *  get the last portion of the name, including any member notation 
	 *  
	 *  Handle things like this:
	 *  	scala.collection.mutable.HashMap[scala.Int,scala.Int]
	 *  as well as:
	 *  	Int, scala.Int, etc
	 *  
	 */
	private def typePartsLast(typeParts : Array[String], typeString : String) : (String, Boolean) = {
	  
		/** for the ordinary case, the . split done by the caller is adequate.  For a case where dots
		 *  appear in the collection element spec, the dots break badly.  Therefore we look re-look 
		 *  at the originating string passed as the 2nd arg to determine the appropriate last type part. */
		var idx : Int = 0
		breakable {
			typeParts.foreach( itm => {
				if (itm.contains("["))
					break
				idx += 1
			})
		}
		val (lastPart,hasIndefiniteArity) : (String, Boolean) = if (idx < typeParts.size) {
			val elementArgPart : String = encloseElementArgs(typeString, '[', ']')
			/** check for tuple ... when true transform type TupleOf<whatever is in the ()> */
			val elementArgPartsChecked : String = if (elementArgPart(0) == '(') {
				val (nmSpcQualifiedTupTypeString, _) : (String, BaseElemDef) = TupleType(elementArgPart)
				nmSpcQualifiedTupTypeString
			} else {
				elementArgPart
			}
			val collectionTypeWithOpenBracket : String = typeParts(idx)
			/** scrape off the bracket and any part of the element type that may be after it */
			val collectionType : String = collectionTypeWithOpenBracket.split('[').head
			/** reassemble the last parts so as to construct the full type name ... the dots in the item spec for a collection case */
			val buf : StringBuilder = new StringBuilder
			buf.append(collectionType)
			buf.append("[")
			buf.append(elementArgPartsChecked)
			buf.append("]")
			(buf.toString, false)  /** FIXME: It is possible that a collection can be tagged variadic as well */
		} else {
			/** it is still possibly a tuple type expression of form '(type,type,...,type)' */
			if (typeString(0) == '(') {
				val (nmSpcQualifiedTupTypeString, elemDef) : (String, BaseElemDef) = TupleType(typeString)
				(nmSpcQualifiedTupTypeString.split('.').last, false) /** FIXME: ditto tuples can be tagged variadic as well */
			} else {
				val hasIndefiniteArity : Boolean = typeParts.last.endsWith("*")
				if (hasIndefiniteArity) {
					(typeParts.last.slice(0, typeParts.last.size - 1), hasIndefiniteArity)
				} else {
					(typeParts.last, hasIndefiniteArity)
				}
				
			}
		}
		(lastPart,hasIndefiniteArity)
	}
}

