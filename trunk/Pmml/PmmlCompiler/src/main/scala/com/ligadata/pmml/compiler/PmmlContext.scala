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

package com.ligadata.pmml.compiler

import scala.collection.mutable._
import scala.collection.immutable.{ Set }
import org.xml.sax.Attributes
import com.ligadata.kamanja.metadata._
import com.ligadata.pmml.runtime._
import com.ligadata.KamanjaBase._
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.kamanja.metadata._
import scala.util.control.Breaks._
import com.ligadata.pmml.support._
import com.ligadata.pmml.traits._
import com.ligadata.pmml.syntaxtree.raw.common._
import com.ligadata.pmml.syntaxtree.cooked.common._

/** 
3) PmmlContext is a global container for the application.  It has a number of key containers and variables:
		
	a) pmmlNodeQueue contains those elements pulled from the Pmml file traverse that are to be processed further.
	b) pmmlExecNodeQueue contains decorated versions of the PmmlNodeQueue.
	c) pmmlNodeStack contains PmmlNodes that are of interest during the XML tree navigation.  The start events
	allocate new PmmlNode instances of the appropriate subtype corresponding to the XML detected and push them to 
	this stack.
	d) To specialize handling of the various element types and their child elements, a number of dispatch tables
	are used:  val fs = Map("John" -> helloJohn _, "Joe" -> helloJoe _)
		i) pmmlElementVistitorMap(qName -> visitorFcn(ctx:PmmlContext, currNode:PmmlNode[+A]) - this will collect 
		the appropriate information from the element/child element being currently visited.  Should a child element
		not be represented in this visitor map, it and its contents are ignored and the traversal continues.
		ii) pmmlXformDispatchMap(pmmlNodeName -> xFormFcn(ctx:PmmlContext, currNode:PmmlNode[+A]) - this is a
		dispatch map that knows how to transform the raw PmmlNode derivative to a corresponding PmmlExecNode
		derivative.
		iii) pmmlFcnNameDerivationMap (pmmlNodeName -> derivationFcn(ctx:PmmlContext, currNode:PmmlNode[+A]) - this
		function will create a unique name for the function represented by this PmmlNode.
		iv) pmmlBuiltinMap (pmmlBuiltinFcnName -> builtinFcn(ctx:PmmlContext, currNode:PmmlExecNode[+A])
		v) pmmlCodeGeneratorMap (pmmlNodeName -> codeGeneratorFcn(ctx:PmmlContext, currNode:PmmlExecNode[+A]) - this map
		will generate appropriate Scala source to represent this node for emission.
	e) a sequence number generator that will be used to help disambiguate names used for functions.
	f) To manage the variables in the Pmml file's DataDictionary and TransformationDictionary the type information about
	the variables contained there are kept in the dataFieldMap(fieldName -> PmmlDataTypeInfo) and 
	derivedFieldMap(derivedFieldName -> PmmlDataTypeInfo) respectively.
	g) To manage the variables to be returned by the model, the MiningField elements marked with usageType value
	'predicted' or 'supplementary' are gathered in a list, modelOutput : List[Any].  These are emitted at the end of
	the object main function to return the model scoring results to the scoring component's client.
	h) log4j logger is available.
	

*/

class PmmlContext(val mgr : MdMgr, val injectLogging : Boolean)  extends LogTrait {	
	/** used to generate a unique number for fcn names in same scope during scala code generation */
	var counter : Int = 0
	def Counter() : Int = { 
	  counter += 1 
	  counter 
	}
	
	var clientName : String = "unknown"
	def ClientName(nm : String) { clientName = nm }
	def ClientName : String = clientName
	
	/** pmmlNodeQueue stores the root elements of interest out of the xml parse for further parsing by xForm */ 
	var pmmlNodeQueue = new Queue[PmmlNode]()
	/** pmmlNodeStack makes the parent node available during the xml parse so node tree parent/child relationships can be made */
	var pmmlNodeStack  = new Stack[PmmlNode]()

	/** 
	 	pmmlExecNodesStack makes the parent node available during the PmmlNode -> PmmlExecNode transformation .. a number of children
	 	only update the parent rather than establish a full child node 
	 */
	var pmmlExecNodeStack = new Stack[Option[PmmlExecNode]]()
	/** 
	 *  This map will contain the Execution node trees by name after the parse and transformation.  They are 
	 *  used by name in order to generate the source code snips in the correct order 
	 */
	var pmmlExecNodeMap = new HashMap[String, Option[PmmlExecNode]]()
	
	/** 
	 *  This queue enqueues update classes so that apply methods can explicitly change state of a Container field via function.
	 *  Classes are needed so that the container or field to be updated can be expressed as a 'var'.  Given the heavily nested 
	 *  uses of functions in a pmml derived field, it is difficult to update a field and yet still participate in the predicate.
	 *  By doing update and returning true if the update was successful, these direct update functions can participate in the
	 *  regular flow of the predicates at work in a given derived field's apply function.
	 */
	var updateClassQueue = new Queue[String]()
	def UpdateClassQueue : Queue[String] = updateClassQueue
	
	/**
	 * Collect generated "Builds" classes here.  The key contains "class someIdentifer" that reflects the variables and macros in use.
	 * The value contains the generated class string itself.  This is currently limited to tracking the "Builds" classes from
	 * the "Builds/Does" macros.
	 */
	var generatedClasses : Map[String,String] = Map[String,String]()
	def GeneratedClasses : Map[String,String] = generatedClasses
	
	/** Make refs to the DataDictionary and TransformationDictionary in those respective containers for convenience */
	var dDict : HashMap[String,xDataField] = HashMap[String,xDataField]()
	def DataDict : HashMap[String,xDataField] = dDict
	def DataDict( d : HashMap[String,xDataField]) { dDict = d }

	/** 
	 * Answer the full package qualified names of the Scala Objects that contain udfs. These are to be added to the
	 * import list of the generated Scala for the model. It is up to the modeler to specify which UDF objects to use
	 * in the model.
	 *
	 * By design, these full package qualified object names must be specified as enumerated values in the data dictionary
	 * element named "UDFSearchPath".
	 *
	 * @deprecated "UdfSearchPath is being replaced with NamespaceSearchPath" "r1.06"
	 */
	def udfSearchPath : Array[String] = {
		val pathDataField : xDataField = if (dDict.contains("UDFSearchPath")) dDict("UDFSearchPath") else null
		val srchPath : Array[String] = if (pathDataField != null) {
	    	pathDataField.values.map( valPair => valPair._1).toArray
	  	} else {
	    	Array[String]()
	  	}
	  	srchPath
	}

	var xDict : HashMap[String,xDerivedField] = HashMap[String,xDerivedField]()
	def TransformDict : HashMap[String,xDerivedField] = xDict
	def TransformDict( d : HashMap[String,xDerivedField]) { xDict = d }
	
	/** Some of the metadata lookup and navigation methods are found in the helper */
	val mdHelper : MetadataInterpreter = new MetadataInterpreter(this)
	def MetadataHelper : MetadataInterpreter = mdHelper
	
	/** This value used in all map/filter/groupBy apply functions as the element reference */
	val applyElementName : String = "_each"
	  
	  
	/** Error counter */
	var errorCounter : Int = 0
	def IncrErrorCounter : Unit = { errorCounter += 1 }
	def ErrorCount : Int = { errorCounter }
	
	/** While processing elements this stack tracks where we are in the transformation dictionary generation */
	val elementStack : Stack[PmmlExecNode] = Stack[PmmlExecNode]()
	/** FcnTypeInfo stack ... used to decide whether field expressions should be fixed ( e.g., ctr.field) or mapped (e.g., ctr("field")) 
	 *  especially used for iterable functions. */
	val fcnTypeInfoStack : Stack[FcnTypeInfo] = Stack[FcnTypeInfo]()
	
	/** 
	 *  Known namespaces at compile time 
	 */
	var knownNamespacesAtCompileTime : scala.collection.mutable.Set[String] = scala.collection.mutable.Set[String]()
	def KnownNamespacesAtCompileTime : scala.collection.immutable.Set[String] = knownNamespacesAtCompileTime.toSet
	
	/** If the knownNamespacesAtCompileTime is empty, fetch them from the MetadataMgr */
	def CollectNamespaceSet : Unit = {
		if (knownNamespacesAtCompileTime.size == 0) {
			val allModels : scala.collection.immutable.Set[String] = mgr.ActiveModels.map(modelDef => modelDef.NameSpace)
			knownNamespacesAtCompileTime = scala.collection.mutable.Set[String]()
			allModels.foreach(nmspc => knownNamespacesAtCompileTime += nmspc)
		}
	}
	
	/** 
	 *  By default, these two namespaces are always present:  System, Pmml
	 */
	val namespaceSearchPathDefault : Array[(String,String)] = Array[(String,String)]((MdMgr.sysNS,MdMgr.sysNS), ("Pmml","Pmml"))

	/** Prevent the defaults from generating import statements */
	val excludeFromImportConsideration : Array[String] = Array[String](MdMgr.sysNS,"Pmml")
	def ExcludeFromImportConsideration : Array[String] = excludeFromImportConsideration
	
	
	def NameSpaceSearchPathDefaultAsStr : String = {
		val buffer : StringBuilder = new StringBuilder
		buffer.append("{")
		namespaceSearchPathDefault.addString(buffer, ",")
		buffer.append("}")
		buffer.toString
	}
	
	def NameSpaceSearchPathAsStr : String = {
		val buffer : StringBuilder = new StringBuilder
		buffer.append("{")
		nameSpcSearchPath.addString(buffer, ",")
		buffer.append("}")
		buffer.toString
	}
	
	/** 
	 * Answer the namespaceSearchPath for this model, an Array of namespace alias, namespace pairs.
	 *
	 * NOTE: The namespace search path is computed each call until it has been determined that all nodes have been parsed by the xml parser.
	 * Ordinarily there are no calls made to this function until that is the case, but for safety sake, this precaution is made.
	 * 
	 * The namespaceSearchPath will answer an Array[(String,String)] where the first tuple item is the namespace alias and the second the full
	 * package qualified namespace that will be used to look up metadata elements.  
	 * 
	 * As a side effect, a namespaceSearchMap is a also constructed where the key is the alias and the value the full package qualified 
	 * namespace.  The map is used when the alias is explicitly used as part of the field reference expression to defeat the standard first found 
	 * behavior of the list.
	 * 
	 * @return searchpath, an Array[(alias,fqnamespace)]
	 * 
	 */
	var nodeXformComplete : Boolean = false /** When true, the nameSpcSearchPath is stable and will be returned as the namespaceSearchPath result */
	def NodeXformComplete : Unit = { nodeXformComplete = true }
	var namespaceSearchMap : Map[String,String] = Map[String,String]()
	def NamespaceSearchMap : Map[String,String] = namespaceSearchMap
	var nameSpcSearchPath : Array[(String,String)] = null
	def namespaceSearchPath : Array[(String,String)] = {
		val nmSpcs : Array[(String,String)] = if (nodeXformComplete && nameSpcSearchPath != null && nameSpcSearchPath.size > 0) {
			nameSpcSearchPath
		} else {
			val pathDataField : xDataField = if (dDict.contains("NamespaceSearchPath")) dDict("NamespaceSearchPath") else null
			nameSpcSearchPath = if (pathDataField != null) {
		    	val aliasSpcPair : Array[String] = pathDataField.values.map( valPair => valPair._1).toArray
		    	val pathLen : Int = aliasSpcPair.size
		    	val legitSz : Int = aliasSpcPair.filter(each => each.split(',').size == 2).size /** insist on only 2 items in each commma-delimited string */
		    	if (pathLen == legitSz) {
		    		val userSupplied : Array[(String,String)] = aliasSpcPair.map(each => {
		    			val (alias, nmspc) : (String,String) = (each.split(',').head.trim, each.split(',').last.trim) 
		    			/** build up the map so namespaces can be found by their alias */
		    			namespaceSearchMap(alias) = nmspc
		    			/** add the cleaned up alias and path pairs to the userSupplied array that will be considered first before the defaults */
		    			(alias,nmspc)
		    		})
		    		/** Add the default alias/namespace into the namespaceSearchMap so defaults also can be found by alias */
		    		namespaceSearchPathDefault.foreach(pair => namespaceSearchMap(pair._1) = pair._2)
		    		/** Create the nameSpcSearchPath... user supplied namespaces are considered before the defaults */
		    		(Array[(String,String)]() ++ userSupplied ++ namespaceSearchPathDefault)
		    	} else {
		    		PmmlError.logError(this, s"Namespace specification is invalid ... either omit the NamespaceSearchPath DataField or provide one or more ${'"'}namespace alias , full.pkg.name.space${'"'} strings as NamespaceSearchPath's enumerated values")
		    		namespaceSearchPathDefault.foreach(pair => namespaceSearchMap(pair._1) = pair._2)
		    		Array[(String,String)]() ++ namespaceSearchPathDefault
		    	}
		  	} else {
		  		namespaceSearchPathDefault.foreach(pair => namespaceSearchMap(pair._1)=pair._2)
		    	Array[(String,String)]() ++ namespaceSearchPathDefault
		  	}
			nameSpcSearchPath
		}
	  	nmSpcs
	}

	/** 
	 *  Answer the namespaces in the search path that are not contained in the default search path (i.e., not system or pmml).
	 *  Note that it is possible to find the system and pmml in the explicit search path in a model when the author wants to 
	 *  change the ordering of udfs or types in some application specific way.  That is, the system and pmml don't always have
	 *  to be last in the search path. 
	 *  @return Array[(String,String)] with the specified paths.
	 *  
	 */
	def namespaceSearchPathSansDefaults : Array[(String,String)] = {
		namespaceSearchPath.diff(namespaceSearchPathDefault)
	}
	
	/** 
	 *  Answer the paths of the namespace search path pairs that are not in the default search path.
	 *  @return array of search paths
	 */
	def namespaceSearchPathSansDefaultsPathsOnly : Array[String] = {
		val nmspcSansDefaults : Array[(String,String)] = namespaceSearchPathSansDefaults
		nmspcSansDefaults.map(pair => pair._2)
	}
	
	/** 
	 *  Answer the paths of the namespace search path pairs that are not in the default search path.
	 *  @return array of search path aliases
	 */
	def namespaceSearchPathSansDefaultsAliasesOnly : Array[String] = {
		namespaceSearchPathSansDefaults.map(pair => pair._1)
	}
	
	/** 
	 *  When expandCompoundFieldTypes is specified, any container.subcontainer.field... reference has the type 
	 *  information for each  portion of the container returned in the array.  When false, only the leaf type (the field name's type)
	 *  is returned.
	 *  
	 *  @param fldName - a name, possibly with a namespace and/or a container '.' prefix and field name key
	 *  @param expandCompoundFieldTypes - when true the container type (if present) and the field type are 
	 *  	returned.  When false, only the field is returned.
	 *  @return an Array of (typestring, isContainerWithNamedFields, BaseTypeDef) triples
	 */
	def getFieldType(fldname : String, expandCompoundFieldTypes : Boolean) : Array[(String,Boolean,BaseTypeDef)] = {	
		val names : Array[String] = if (fldname.contains(".")) {
			val fldNames : Array[String] = fldname.split('.')
			val isDictItem : Boolean = (dDict.contains(fldNames(0)) || xDict.contains(fldNames(0)))
			/** fold keys to lower case except for dictionary names when hiearchical container field reference detected */
			if (isDictItem) {
				var arr : ArrayBuffer[String] = ArrayBuffer[String]()
				arr += fldNames(0)
				fldNames.tail.foreach(itm => arr += itm.toLowerCase)
				arr.toArray
			} else {
			  	fldname.split('.').map(_.toLowerCase) 
			 }
		} else {
			Array(fldname)
		}
		mdHelper.getFieldType(names, expandCompoundFieldTypes)
	}
	
	/** 
	 *  Get the container's typedef associated with the supplied namespace and name. 
	 */
	def getContainerType(nmSpc : String, container : String) : ContainerTypeDef = {
		val (typeStr,baseTypeDef) : (String,BaseTypeDef) = MetadataHelper.getType(container)
		val containerTypeDef = baseTypeDef match {
		  case c : ContainerTypeDef => baseTypeDef.asInstanceOf[ContainerTypeDef]
		  case _ => {
		    PmmlError.logError(this, s"getContainerType: the container $nmSpc.$container is not a container or message.  Did you forget to catalog it?")
		    val objStr : String = if (baseTypeDef == null) "null" else baseTypeDef.toString
		    PmmlError.logError(this, s"getContainerType: the object returned is $objStr")
		    null
		  }
		}

	  	containerTypeDef
	}
	

	/** 
	 *  Cache the MiningSchemaMap from the xRuleSetModel node here for more convenient access during code generation
	 */
	var miningSchemaMap : HashMap[String,xMiningField] = HashMap[String,xMiningField]()
	def MiningSchemaMap(m : HashMap[String,xMiningField]) { miningSchemaMap = m }
	def MiningSchemaMap : HashMap[String,xMiningField] = miningSchemaMap
	
	/** 
	 *  This map contains values picked up during parse and later used to generate the Scala source code.  The following KEYS
	 *  MUST be present at code generation time to properly identify the generated model source.:
	 *  	ApplicationName (from the Header)
	 *   	FunctionName (from the RuleSetModel ... and other model types we are to support)
	 *   
	 *  The name of the actual PMML source file is also collected
	 *  	PMML (see the PmmlCompiler.scala source for use)
	 *      
	 *  These will be used if present:
	 *   	Version (string version from the Header)
	 *    	ModelNamespace (from application name in the Header)
	 *    	ApplicationName (from the Header)
	 *    	Copyright (from the Header)
	 *      Description (from the Header)
	 *      ModelName (from the RulesetModel ... and other model types we are to support)
	 *      ClassName (the derived class name based upon the ApplicationName)
	 *      VersionNumber  (a numeric version number... only dec digits extracted
	 *      ModelPackageName (the package name used for the generated scala source file)
	 */
	var pmmlTerms : HashMap[String,Option[String]] = HashMap[String,Option[String]]() 
	
	/** this is from command line parameters... those Ole events that this model can/should handle */
	var eventsHandled : ArrayBuffer[String] = ArrayBuffer[String]()
	
	/** 
	 *  [(message name, (appears in ctor signature?, message type for named message, varName))] 
	 *  
	 *  The containersInScope is used to create message and container references in generaal.  Those that are
	 *  messages (declared in the "messages" data dictionary field), will also be generated as part of the 
	 *  generated model's main constructor
	 */
	var containersInScope : ArrayBuffer[(String, Boolean, BaseTypeDef, String)] = ArrayBuffer[(String, Boolean, BaseTypeDef, String)]()
	
	/** Capture field ref variables that are inputs for the model under consideration for model def generation.  For now, this 
	 *  means container field references.  Note: Other kinds of inputs are anticipated.  What makes it into this Map is a moving target.
	 *  
	 *  ctx.modelInputs(fldRef.field.toLowerCase()) = (cName, fieldName, baseType.NameSpace, baseType.Name, isGlobal)
	 *  is the statement used to add one to the map.  The cName is the container name, the field name the container's field, the
	 *  baseType namespace and name are the field's base type's namespace and name, and the boolean is an indication if this is a 
	 *  global (was cataloged in the attrdef map explicitly). 
	 */
	var modelInputs : Map[String, (String, String, String, String, Boolean, String)] = Map[String, (String, String, String, String, Boolean, String)]()
	
	/** Model outputs are collected here for ModelDef generation. 
	 *  The triple consists of the field name, field's type namespace, and the field type */
	var modelOutputs : Map[String, (String, String, String)] = Map[String, (String, String, String)]()
	
	/** collected by RegisterMessages & collectContainers, grist for import statement generation */
	val importStmtInfo : scala.collection.mutable.Set[String] = scala.collection.mutable.Set[String]()
	def ImportStmtInfo : scala.collection.mutable.Set[String] = importStmtInfo
	
	/** 
	 *  Register any messages that will appear in the constructor of the generated model class.  Register the
	 *  the gCtx so it can be added to the constructor even though it is used only in "Get" functions in the 
	 *  use of it in the derived fields.  Allow ContainerDefs as well as MessageDefs there as well as there is 
	 *  little to distinguish them from each other save their names.
	 *  
	 *  As a side effect collect the physical names of the messages and containers found. These contain the 
	 *  appropriate version number suffixes needed for the generation of the import statements.
	 *  
	 *  NOTE: Containers are encountered during semantic analysis as types are examined on each DataField
	 *  and DerivedField.  These are added to the containersInScope map as well.  
	 */ 
	def RegisterMessages : Unit = {
		val messages : xDataField = if (dDict.contains("parameters")) dDict.apply("parameters") else null
		if (messages == null) {
			logger.error("No input message(s) specified for this model. Please specify messages variable with one or more message names as values.")
		} else {
			messages.values.foreach( value => {			
				val msgFldName : String =  value._1 
				val msgFld : xDataField = if (dDict.contains(msgFldName)) dDict.apply(msgFldName) else null
				val tuple = if (msgFld != null) {
					val (msgTypeStr, msgDef) : (String,MessageDef) = MetadataHelper.getMsg(msgFld.dataType)
					if (msgDef != null) {
						val (containerTypeName, msgDefType) : (String, BaseTypeDef) = MetadataHelper.getType(msgFld.dataType)
						if (msgDefType == null) {
							PmmlError.logError(this, "The supplied message has no corresponding message type.  Please add metadata for this message.")
						} else {
							if (msgDefType.isInstanceOf[ContainerTypeDef]) {
								if (MetadataHelper.isContainerWithFieldOrKeyNames(msgDefType)) {
									importStmtInfo += msgDefType.PhysicalName /** collect import info */
								}
								containersInScope += Tuple4(msgFldName,true,msgDefType,msgFldName)
							} else {
								PmmlError.logError(this, s"MessageDef encountered that did not have a container type def... type = ${msgDef.typeString}")
							}
							/** This is a convenient place to pick up the jars needed to compile and execute the model under construction */
							val implJar : String  = msgDef.JarName
							val depJars : Array[String] = msgDef.DependencyJarNames
							collectClassPathJars(implJar, depJars)
						}
					} else { /** a container ... not a message ... this is a bit crazy so far... we have not accepted these in the constructor */
						val (containerTypeName, containerDef) : (String,ContainerDef) = MetadataHelper.getContainer(msgFld.dataType)
						if (containerDef == null) {
							PmmlError.logError(this, "The supplied message has no corresponding message definition.  Please add metadata for this message.")
						} else {
							val (containerTypeName, containerTypeDef) : (String, BaseTypeDef) = MetadataHelper.getType(msgFld.dataType)
							if (containerTypeDef == null) {
								PmmlError.logError(this, "The supplied container has no corresponding container type.  Please add metadata for this container.")
							} else {
								/** This is a convenient place to pick up the jars needed to compile and execute the model under construction */
								val implJar : String  = containerDef.JarName
								val depJars : Array[String] = containerDef.DependencyJarNames
								collectClassPathJars(implJar, depJars)
							  
								if (containerTypeDef.isInstanceOf[ContainerTypeDef]) {
									if (MetadataHelper.isContainerWithFieldOrKeyNames(containerTypeDef)) {
										importStmtInfo += containerTypeDef.PhysicalName /** collect import info */
									}
									containersInScope += Tuple4(msgFldName,true,containerTypeDef,msgFldName)
								} else {
									PmmlError.logError(this, s"MessageDef encountered that did not have a container type def... type = ${containerTypeDef.typeString}")
								}
							}
						}
					}
				} else {
					PmmlError.logError(this, "The input message referenced in the messages field has not been declared in the data dictionary.  Do that before proceeding.")
				}
			})
		}
	}
	
	/** Mechanism to collect the container, message and (soon... function) jars for the class path */
	val classPathJars : scala.collection.mutable.Set[String] = scala.collection.mutable.Set[String]()
	
	/** 
	 *  Used by RegisterMessages and RegisterContainerAsNecessarys, et al... add the implementation jar and dependency jars 
	 *  for each msg,container, and fcn element  
	 */
	def collectClassPathJars(implJar : String, depJars : Array[String]) : Unit = {
		if (implJar != null) {
			classPathJars.add(implJar)
		}
		if (depJars != null && depJars.size > 0) {
			depJars.foreach(jar => classPathJars.add(jar))
		}
	}
	
	/** Check all of the xDerivedFields for dataTypes that are either MessageDefs or ContainerDefs 
	 *  Record them in the context's containersInScope with any containers collected in the DataDictionary's
	 *  "parameters" field.
	 */
	def collectContainers {
		xDict.foreach( fld => {
			val (name, dFld) : (String, xDerivedField) = fld
			RegisterContainerAsNecessary(name, dFld.dataType)
		})
	}
	

	/** 
	 *  Use this function to register any containers and/or messages that are being used by this model.
	 *  This function is called during semantic analysis of DataField and DerivedField elements.  Since they
	 *  didn't appear in the parameter list, instances of them are to be obtained from the gCtx with a Get 
	 *  method call.
	 *  
	 *  NOTE: variable name is not required for the gCtx related containers.  It is used just for the messages
	 *  mentioned in the parameters data field used to prepare the main model constructor.
	 */
	def RegisterContainerAsNecessary(name : String, dataType : String) : Boolean = {
		val (typeStr,elem) : (String,BaseTypeDef) = MetadataHelper.getType(dataType)
		val registered : Boolean = if (elem != null) {
			/** This is a convenient place to pick up the jars needed to compile and execute the model under construction */
			val implJar : String  = elem.JarName
			val depJars : Array[String] = elem.DependencyJarNames
			collectClassPathJars(implJar, depJars)
			
			elem match {
			  case con : ContainerTypeDef => { 
				  			if (MetadataHelper.isContainerWithFieldOrKeyNames(elem)) {
				  				importStmtInfo += elem.PhysicalName /** collect import info */
				  			} else {
				  				val memberContainersWithFieldOrKeyNames : Array[BaseTypeDef] = MetadataHelper.collectMemberContainerTypes(elem)
				  				if (memberContainersWithFieldOrKeyNames != null && memberContainersWithFieldOrKeyNames.size > 0) {
				  					memberContainersWithFieldOrKeyNames.foreach( typ => importStmtInfo += typ.PhysicalName )
				  				}
				  			}
				  			containersInScope += Tuple4(name,false,elem.asInstanceOf[ContainerTypeDef], "n/a")
				  			true
				  		}
			  case _ => { 	//logger.debug(s"Unecessary to register this dataType ... $dataType")
				  			false
			    		}
			} 
		} else { 
		  false
		}
		
		registered
	}
	
	
	/** these get queued for further processing */
	val topLevelContainers : List[String] = List[String]("Header", "DataDictionary", "TransformationDictionary", "RuleSetModel")

	/** 
	 *  This object substitutes pattern map templates with arguments found in the parameter lists of the apply functions in the pmml 
	 *  trees, principally the TransactionDictionary.
	 */
	val fcnSubstitute : FcnSubstitution =  new FcnSubstitution
	
	/** Udf map ... used for generating type appropriate constants, etc */
	var udfMap : HashMap[String, PmmlExecNode] = HashMap[String, PmmlExecNode]()
	def UdfMap : HashMap[String, PmmlExecNode] = udfMap

	/** 
	 *  RuleSetModel and SimpleRule subclass constructor statements collected here for instantiation in the model class' initialize method 
	 *  The pair is ("RuleSetModel" -> Set("new RuleSetModel(....)") or ("SimpleRule" -> Set("new SimpleRule_RULE1_01(....)", "new SimpleRule_RULE2_07(...)", ...) 
	 */
	val simpleRuleInsertionOrder : ArrayBuffer[String] =  ArrayBuffer[String]()
	val ruleRuleSetInstantiators = new HashMap[String, scala.collection.mutable.Set[String]] with MultiMap[String, String]
	def RuleRuleSetInstantiators : HashMap[String, scala.collection.mutable.Set[String]] with MultiMap[String, String] = ruleRuleSetInstantiators
	var miningSchemaInstantiators : HashMap[String,String] = HashMap[String, String]()
	
	/** RuleSetModel attributes collected by a PmmlExecNodeVisitor */
	var defaultScore : String = "None"
	def DefaultScore : String = { defaultScore }
	def DefaultScore(defltScore : String) { defaultScore = defltScore }
	var ruleSetSelectionMethods : ArrayBuffer[xRuleSelectionMethod] = new ArrayBuffer[xRuleSelectionMethod]()
	def RuleSetSelectionMethods(arrayOfMethods : ArrayBuffer[xRuleSelectionMethod]) { ruleSetSelectionMethods = arrayOfMethods }
	def RuleSetSelectionMethods : ArrayBuffer[xRuleSelectionMethod] = ruleSetSelectionMethods 
	
	/** SimpleRule attributes collected by a PmmlExecNodeVisitor.  These are added in the order they are parsed.. They are
	 *  used to update the respective SimpleRule instances added to the RuleSetModel's SimpleRule array */
	var scoreDistributions : ArrayBuffer[ArrayBuffer[xScoreDistribution]] = ArrayBuffer[ArrayBuffer[xScoreDistribution]]()
	def RuleScoreDistributions(arrayOfDistros : ArrayBuffer[xScoreDistribution]) { scoreDistributions += arrayOfDistros }
	def RuleScoreDistributions(idx : Int) : ArrayBuffer[xScoreDistribution] = { scoreDistributions.apply(idx) }
	def RuleScoreDistributions : ArrayBuffer[ArrayBuffer[xScoreDistribution]] = { scoreDistributions }
	

	
	/** Collect the udf function and indirectly their ParameterFields from the TransformationDictionary.
	 *  Note: we may want to go through all of the nodes to collect these ...for now just the TransformationDictionary.
	 */
	def collectUdfs()  {
	    val udfCollector : UdfCollector = new UdfCollector(this)
		val xDictNode : Option[PmmlExecNode] = pmmlExecNodeMap.apply("TransformationDictionary")
		PmmlExecNodeVisitor.Visit(xDictNode, udfCollector)
	}

	/** Collect the input variables required to produce the ModelDef */
	def collectModelInputVars : Unit =  {
	    val inVarCollector : ContainerFieldRefCollector = new ContainerFieldRefCollector(this)
		val dDictNode : Option[PmmlExecNode] = pmmlExecNodeMap.apply("DataDictionary")
		PmmlExecNodeVisitor.Visit(dDictNode, inVarCollector)
		val xDictNode : Option[PmmlExecNode] = pmmlExecNodeMap.apply("TransformationDictionary")
		PmmlExecNodeVisitor.Visit(xDictNode, inVarCollector)
	}
	
	def collectModelOutputVars : Unit = {
		/** filter for predicted and supplementary fields */
		val outputMiningFields : Array[xMiningField] = 
				miningSchemaMap.values.filter( fld => fld.usageType == "predicted" || fld.usageType == "supplementary").toArray
		val expandCompoundFieldTypes : Boolean = true
		outputMiningFields.foreach(fld => { 
			val name = fld.name
			
			val typeInfo : Array[(String,Boolean,BaseTypeDef)] = getFieldType(name, expandCompoundFieldTypes)
			if (typeInfo == null || (typeInfo != null && typeInfo.size == 0) || (typeInfo != null && typeInfo.size > 0 && typeInfo(0)._3 == null)) {
				//throw new RuntimeException(s"collectModelOutputVars: the mining field $name does not refer to a valid field in one of the dictionaries")
				logger.error(s"mining field named '$name' does not exist... your model is going to fail... however, let's see how far we can go to find any other issues...")
				modelOutputs(name.toLowerCase()) = (name, "is a bad mining variable referring to no valid dictionary item", "UNKNOWN TYPE")
			} else {
			
				/** FIXME: if a container is present (typeInfo.size > 1), should the container itself be added to the list of output
				 	vars instead of the field?  I think so... otoh, there is likely just one row out of this message container.. can
				 	the engine pull that row given the information provided.  Shouldn't there be a key emitted to select that row?  Or? */
				if (typeInfo.size > 1) {
					/** ... write them both for now and bring it up tomorrow */
					val (containerType, isContainer, containerBaseTypeDef) = typeInfo(0)
					val (fldType, isFldAContainer, fieldBaseTypeDef) = typeInfo(1)
					val containerName : String = fld.name.split('.').head
					val containervalue = (containerName, containerBaseTypeDef.NameSpace, containerBaseTypeDef.typeString)
					/** give the full container.name for the field reference */
					val typeStr : String = fieldBaseTypeDef.typeString
					val fldTypeName : String = fieldBaseTypeDef.Name
					val fldTypeNameSp : String = fieldBaseTypeDef.NameSpace
					val fldvalue = (name, fldTypeNameSp, fldType)
					//modelOutputs(containerName.toLowerCase()) = containervalue
					modelOutputs(name.toLowerCase()) = fldvalue
				} else {
					val (fldType, isFldAContainer, fieldBaseTypeDef) = typeInfo(0)
					val typeStr : String = fldType
					val fldTypeName : String = fieldBaseTypeDef.Name
					val fldTypeNameSp : String = fieldBaseTypeDef.NameSpace
					val fldvalue = (name, fldTypeNameSp, fldTypeName)
					modelOutputs(name.toLowerCase()) = fldvalue
				}
			}
			
		})
	}

	
	/** 
	 *  Run through the transformation dictionary and data dictionary looking for poor syntax, unknown field ids, 
	 *  and other mess.
	 */
	def syntaxCheck() : Unit =  {
	    val syntaxChecker : FieldIdentifierSyntaxChecker = new FieldIdentifierSyntaxChecker(this)
		val xDictNode : Option[PmmlExecNode] = pmmlExecNodeMap.apply("TransformationDictionary")
		PmmlExecNodeVisitor.Visit(xDictNode, syntaxChecker)
		val dDictNode : Option[PmmlExecNode] = pmmlExecNodeMap.apply("DataDictionary")
		PmmlExecNodeVisitor.Visit(dDictNode, syntaxChecker)
		
		/** At least one prediction in the mining schema please. */
		val atLeastOnePrediction : Boolean = (MiningSchemaMap.values.filter(m => m.usageType.toLowerCase == "predicted").size > 0)
		if (! atLeastOnePrediction) {
			logger.error("Mining Schema must have at least one mining field with usageType == 'predicted'")
			IncrErrorCounter
		}
	}
		
	/** 
	 *  Collect the categorized values from the top level apply functions (i.e., parent node is xDerivedField).
	 *  Update the top level apply functions categorized value array with them, eliminating them from the child
	 *  nodes of the xDerived field.
	 */
	def transformTopLevelApplyNodes() : Unit =  {
	    val catTransformer : IfActionTransform = new IfActionTransform(this)
		val xDictNode : Option[PmmlExecNode] = pmmlExecNodeMap.apply("TransformationDictionary")
		PmmlExecNodeVisitor.Visit(xDictNode, catTransformer)
	}
		
	def ruleSetModelInfoCollector() : Unit =  {
	    val rsModelCollector : RuleSetModelCollector = new RuleSetModelCollector(this)
	    val rsm : Option[PmmlExecNode] = pmmlExecNodeMap.apply("RuleSetModel") 
		PmmlExecNodeVisitor.Visit(rsm, rsModelCollector)
	}

	def simpleRuleInfoCollector() : Unit =  {
	    val rsModelCollector : SimpleRuleCollector = new SimpleRuleCollector(this)
	    val rsm : Option[PmmlExecNode] = pmmlExecNodeMap.apply("RuleSetModel") 
		PmmlExecNodeVisitor.Visit(rsm, rsModelCollector)
	}
	
	def UdfReturnType(udfName : String) : String = {
		val returnType : String = if (udfMap.contains(udfName)) {
			val udf : xDefineFunction = udfMap.apply(udfName).asInstanceOf[xDefineFunction]
			udf.dataType
		} else {
			"Any"
		}
		returnType
	}
	
	/** Answer the pmml dataTypes for the udf with the supplied name as an ArrayBuffer */
	def UdfParameterTypes(udfName : String) : ArrayBuffer[String] = {
	  
		var parameterTypes : ArrayBuffer[String] = new ArrayBuffer[String]()
		if (udfMap.contains(udfName)) {
			val udf : xDefineFunction = udfMap.apply(udfName).asInstanceOf[xDefineFunction]
		  	udf.Children.foreach((child) => {
				child match {
				  case p : xParameterField => parameterTypes += p.dataType
				  case _ => None
				}
		  	})
		}
		parameterTypes
	}
	
	/** Answer the pmml arg names for the udf with the supplied name as an ArrayBuffer */
	def UdfParameterNames(udfName : String) : ArrayBuffer[String] = {
	  
		var parameterNames : ArrayBuffer[String] = new ArrayBuffer[String]()
		if (udfMap.contains(udfName)) {
			val udf : xDefineFunction = udfMap.apply(udfName).asInstanceOf[xDefineFunction]
		  	udf.Children.foreach((child) => {
				child match {
				  case p : xParameterField => parameterNames += p.name
				  case _ => None
				}
		  	})
		}
		parameterNames
	}
	

}

