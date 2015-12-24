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
import scala.io.Source
import java.io.BufferedWriter
import java.io.FileWriter
import sys.process._
import java.io.PrintWriter
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.InputSource
import org.xml.sax.XMLReader
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.kamanja.metadata.MdMgr._
import com.ligadata.kamanja.metadata._
import com.ligadata.kamanja.metadataload.MetadataLoad
import com.ligadata.pmml.transforms.printers.scala.common._
import com.ligadata.pmml.support._
import com.ligadata.pmml.xmlingestion._
import com.ligadata.pmml.transforms.rawtocooked.common._
import com.ligadata.pmml.transforms.xmltoraw.common._
import com.ligadata.Exceptions.StackTrace


/** 
	Original Notes (of historical interest only):
    This object manages the parse, transform, and source code generation and compilation of pmml models.  
	
	Basic idea - xml parsing
	
		There are two types of elements we examine.  One type is a root element that with its children will be enqueued
		for further processing.  Then there are general nodes of interest.  These are collected and added to the current
		element on the stack as that element's child.
	
		There is a container queue and a stack are used to manage the XML SAX traverse data collection effort.  Generally
		results of the XML tree navigation to be retained for further processing are added to the queue.  The processing stack 
		is used strictly to manage hierarchical state in the collection effort. These collections are used as follows:
		
			a) At container start, a new PmmlNode is created and pushed to the top of the processing stack.  It is 
			updated with its children's content assuming the children are in fact of interest to the collection 
			effort.  Similarly their children's content updates their top of stack element, etc.
		
			b) At container end event, the top stack on the process stack contains a PmmlNode with any
			of its children found in the 'children' ArrayBuffer each PmmlNode has. One of two things can
			happen.  That node is the sole element on the stack (it is also checked against the ctx.topLevelContainers list)
			and is enqueued in the ctx.pmmlNodeQueue to await further processing.  
			
			The other possibility is that this is a node of interest that will be added to the previous node on the 
			stack as its child.
		
		The decision as to what to add to the current container is made by the function associated with each element of interest
		found in a PmmlContext dispatcher.  Almost all elements are collected, as most are germane to the generation of
		an executable program.
	
	Basic idea - PmmlNode transformation to PmmlExecNode
	
		The PmmlNode hierarchy rooted at each node in the container queue is transformed.  Generally the information found
		in a given PmmlNode instance will be extracted and update a new PmmlExecNode that corresponds to it.  Additional
		information will be generated and or derived to create a rich set of information capable of generating the 
		Scala source code.  Here is a list of the basic steps:
	
		a) The type information represented by the DataDictionary elements and the TransformationDictionary 
		derived elements is collected into a dictionary (a map). 
		
		b) This dictionary will be consulted to mark type information of the uses in the function bodies and rule bodies. 
		
		c) In the case of the functions, the local variables (in function scope are identified).  For example, an "if" 
		function can be represented as follows: 
		
			def foo001() : boolean = {
				boolean truth = false;
				for {
				 	i <- 1 to NumChildBoolExpressionsInIf
				 		boolExpr1()
				 		boolExpr2()
				 		.
				 		.
				 		.
				 		boolExprN()
				 	} truth = true
				 	
				truth
			}
			 	
		In this case the boolean variable, 'truth', is generated and added to the local Variables for the if function.
		To simplify the example, it is assumed that the if function returns a boolean value rather than coercing to 
		some other type (e.g., representing the return as an integer).
		
		d) Each function will have a name generated for it such that it is unique.  This will decorate the PmmlExecNode
		so that it can be disambiguated from other instances of the same function.  Note that no attempt to optimize
		functions with same syntax tree will be performed.  It is up to the Pmml author to use the TransformationDictionary
		to represent such shared functions with the derived variable type.
		
		e) Certain PmmlExecNode derivatives will have a name generated for them.  If they get a special name, the 
		function Name(PmmlContext) is defined in the PmmlExecNode derivative.  The PmmlContext allows access to the 
		global unique number generator to disambiguate the same functions used in the same scope (for instance... there
		are likely other reasons.  When in doubt a serial number is suffixed to the function names.
		
		h) Where the functions are used, the argument list to call it must be prepared.  For example, RuleSets with a
		default score should pass it to each SimpleRule function in the model.
		
		i) The MiningFields with usageType values of 'predicted' or 'supplementary' are noted as fields to be returned
		by the model as the result.  These fields are returned as the RuleSet function's value.
		
		j) Obviously there could be other decorations to be derived for annotation on the PmmlExecNode instances.  We will
		try to add them here in the documentation as well of course in the implementation.
		
		k) Once the PmmlExecNode for a given PmmlNode derivative is complete, it is enqueued in the execution queue that 
		is part of the application context object.
	
		
	Basic idea - PmmlExecNode Code Generation
	
		Each enriched PmmlExecNode is transformed into a series of Scala statements.  The following steps are used:
	
		a) The module comment is generated from the Header container information collected.
		b) The class declaration is started based upon the RuleSetModel name.
		c) The data and transformation dictionary variables are traversed and the 'var' statements are generated for each element.
		d) If one or more MapValues is/are present in the transformation dictionary, these maps are constructed.
		If more than one is present a mapOfMapValues is generated keyed by field name.  
		e) The functions found in the transformation dictionary and RuleSet are generated (AS REQUIRED)MetadataBootstrap using the 
		derived names associated with each PmmlExecNode. 
			i) The function signature will be derived based upon the 
			ii) The function bodies will include possible setup	of local variables.  
			iii) In some situations, there will be a conversion to another type (e.g., boolean -> {0|1})
		f) For SimpleRules, accept the "default" score as input.  Based upon the outcome, the function will
			return the default value or the SimpleRule's score
		g) For the RuleSet function, it will supply the default score to each of the SimpleRule's called.  It will
		answer a tuple that contains those MiningField variables that have been marked either 'predicted' or 
		'supplementary'.  
		h) For RuleSet, there are several 'RuleSelectionMethod' types.  Initially only 'firstHit' will be supported.  In 
		this approach (for this version at least) rules are executed in order of appearance in the RuleSet.  When
		confidence and weighting are applied in a later version of this, it may be useful to execute the rules in parallel
		and then apply the weight and confidence metrics to the solutions returned to create the score.
			 	
	Basic Idea - Class Responsibilities Described
	
		This note describes how the PmmlCompiler project divides responsibilities and accomplishes key functionality:
		
		1) PmmlCompiler contains the main application that is invoked.  Arguments:
		
			a) the path of the Pmml file to compile
			b) the path of the target directory where the class file will be published.
			c) ... and a lot of others now.. see the main inner function, nextOption, for what can
			be collected from the command line.  Hopefully the String, 'usage', matches that.
			
		2) PmmlParseEventParser is the event handler for the Sax traversal of the Pmml file.
		
		3) PmmlContext is a global container for the application.  It has a number of key containers and variables:
		
			a) pmmlNodeQueue contains those elements pulled from the Pmml file traverse that are to be processed further.
			b) pmmlExecNodeQueue contains decorated versions of the PmmlNodeQueue.
			c) pmmlNodeStack contains PmmlNodes that are of interest during the XML tree navigation.  The start events
			allocate new PmmlNode instances of the appropriate subtype corresponding to the XML detected and push them to 
			this stack.
			d) To specialize handling of the various element types and their child elements, a number of dispatch tables
			are used:  
				
				i) pmmlElementVistitorMap(qName -> ElementVisitFcn) - this will collect the appropriate information from 
				the element/child element being currently visited including the attributes that may be present.  Attributes 
				mentioned in the xml schema for PMML will be set to 'None' values for now. Ideally these are set to defaults 
				specified in the XSD.  If important, we will fix next revision. Should a child element not be represented in 
				this visitor map, it and its contents are ignored and the traversal continues.
				
				ii) pmmlNodeToPmmlExecNodeXformMap(pmmlNodeName -> PmmlNodeToPmmlExecNodeXformFcn) - this is a
				dispatch map that knows how to transform the raw PmmlNode derivative to a corresponding PmmlExecNode
				derivative.
				
				iii) pmmlExecNodeNameDerivationMap (pmmlExecNodeName -> PmmlExecNodeNameDerivationFcn) - this
				function will create a unique name for the function represented by this PmmlNode.  Only the functions 
				will need this feature (or so I think at this moment)
				
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
			h) To contain the generated Scala source code, a StringBuilder is used called scoringModelSource.
			
		4) PmmlNode class hierarchy.  PmmlNode is a abstract class represents a raw XML element of interest to the compiler.
		These are collected in a list for transformation and ultimately the Scala code generation process.  This class 
		has numerous concrete subclasses that describe the various XML nodes of interest found in the PMML file.
		
		5) PmmlExecNode class hierarchy.  Similar to the but contains the type information for the content of the element and 
		any additional information that will prove useful later when the node is transformed to Scala code (e.g., unique
		function name for the same function type appearing in same scope).
		
		6) Static objects are used to contain the dispatch map functions described above for each PmmlNode and PmmlExecNode 
		derived class.  Their class names will be the same as their respective class name counterparts (where there is no
		name conflict with Scala language...e.g., 'if').
		
		7) The class, PmmlExecute, generated by the compiler class PmmlExecuteCompile, will accept the data vector from the 
		companion data extract/filter/aggregate module(s) in the constructor generated, organizing it as necessary for use in 
		the execute function to be generated..  The PmmlExecuteCompile will generate code for function, execute(), that will 
		send this data to the RuleSet function that will score the data according to the rules found in the PMML for the 
		model.  Code is generated that returns the prediction and any supplementary data requested by the model as a tuple to 
		the model user that submitted the input data.
	
 */

object PmmlCompilerGlobalLogger {
    val loggerName = this.getClass.getName()
    val logger = LogManager.getLogger(loggerName)
}

trait LogTrait {
    val logger = PmmlCompilerGlobalLogger.logger
}

object PmmlCompiler extends App with LogTrait {

	def usage : String = {
"""	
Usage: scala com.ligadata.Compiler --pmml <pmml path> --scalaHome <shome> --javaHome <jhome> 
	  --scriptOut <scriptfile> --cp <classpath> --jarpath <jar path> --manifestpath <manifest path> 
	  [--instrumentWithLogInfo {true|false}]
	  [--client <clientname>] [--srcOut <output path>] [--skipjar {true|false}]
         where 	<pmml path> (required) is the file path of the pmml model		
                <classpath> (required) is the classpath containing the runtime needs of the pmml models including 
                            PmmlRuntime.jar, OleBase.jar... among others 
                <shome> (required) is the equivalent of $SCALA_HOME
                <jhome> (required) is the equivalent of $JAVA_HOME
                <jar path> (required) is the destination directory path for the jar file to be produced
				<manifest path> (required) is the path of the manifest template to use for inclusion/generation of the jar
                <clientname> (required) is the name of the customer whose pmml models are being compiledMetadataBootstrap
                 <output path> (optional) is an output file path where a copy of the generated source will be left
                skipjar (optional) is a flag that when used suppresses the compilation/jar step.. often used with the --srcOut option
				instrumentWithLogInfo (optional) if true model derived field classes are instrumented with enter and result 
					logging	messages.  By default, no logging instrumentation

Note that the sources will be included in the jar produced.  The optional scala source described by <output path> is for immediate
inspection in addition to the jar version.  Note that the clientName value supplied is used for scala package name generation, et al.
As such, it must be simple name with alphanumerics and ideally all lower case.
"""
	}
	
	override def main (args : Array[String]) {
		
		logger.debug("********************************")
		logger.debug("********************************")
		logger.debug("* PmmlCompiler.main begins")
		logger.debug("********************************")
		logger.debug("********************************")


		if (args.length == 0) logger.error(usage)
		val arglist = args.toList
		type OptionMap = Map[Symbol, String]
		logger.debug(arglist)
		def nextOption(map : OptionMap, list: List[String]) : OptionMap = {
		  list match {
		    case Nil => map
		    case "--pmml" :: value :: tail =>
		                           nextOption(map ++ Map('pmml -> value), tail)
		    case "--jarpath" :: value :: tail =>
		                           nextOption(map ++ Map('jarpath -> value), tail)
		    case "--manifestpath" :: value :: tail =>
		                           nextOption(map ++ Map('manifestpath -> value), tail)
		    case "--scalaHome" :: value :: tail =>
		                           nextOption(map ++ Map('scalahome -> value), tail)
		    case "--javaHome" :: value :: tail =>
		                           nextOption(map ++ Map('javahome -> value), tail)
		    case "--cp" :: value :: tail =>
		                           nextOption(map ++ Map('classpath -> value), tail)
		    case "--srcOut" :: value :: tail =>
		                           nextOption(map ++ Map('scala -> value), tail)
		    case "--instrumentWithLogInfo" :: value :: tail =>
		                           nextOption(map ++ Map('instrumentWithLogInfo -> value), tail)
		    case "--client" :: value :: tail =>
		                           nextOption(map ++ Map('client -> value), tail)
		    case "--skipjar" :: tail =>
		                           nextOption(map ++ Map('skipjar -> "true"), tail)
		    case option :: tail => logger.error("Unknown option " + option) 
		                           sys.exit(1) 
		  }
		}
		
		val options = nextOption(Map(),arglist)
		val pmmlFilePath = if (options.contains('pmml)) options.apply('pmml) else null
		val classpath = if (options.contains('classpath)) options.apply('classpath) else null 
		val scalahome = if (options.contains('scalahome)) options.apply('scalahome) else null 
		val javahome = if (options.contains('javahome)) options.apply('javahome) else null 
		val scalaSrcTargetPath = if (options.contains('scala)) options.apply('scala) else null 
		val instrumentWithLogInfo = if (options.contains('instrumentWithLogInfo)) 
										options.apply('instrumentWithLogInfo) else null
		val jarTargetDir = if (options.contains('jarpath)) options.apply('jarpath) else null 
		val manifestpath = if (options.contains('manifestpath)) options.apply('manifestpath) else null 
		val clientName = if (options.contains('client)) options.apply('client) else null
		val skipJar : Boolean = if (options.contains('skipjar)) true else false 
		
		var valid : Boolean = (pmmlFilePath != null) 

		if (valid) {
		  
			val injectLogging : Boolean = if (instrumentWithLogInfo != null) {
				val instruLogInfo : String = instrumentWithLogInfo.toLowerCase()
				(instruLogInfo.startsWith("1") || instruLogInfo.startsWith("y") || instruLogInfo.startsWith("t")) 
			} else false
			/** 
			 *  Files, should it be desirable to load the mdmgr from files the paths will be collected from
			 *  the following files from the command line. For now, they are not used.  See MetadataLoad.
			 */
			val typesPath : String = ""
			val fcnPath : String = ""
			val attrPath : String = ""
			val msgCtnPath : String = ""
			val mgr : MdMgr = MdMgr.GetMdMgr
			
			val mdLoader = new MetadataLoad (mgr, typesPath, fcnPath, attrPath, msgCtnPath)
			mdLoader.initialize
			
			val compiler : PmmlCompiler = new PmmlCompiler(mgr, clientName, logger, injectLogging, Array("."))
			//val (modelSrc, msgDef) : (String,ModelDef) = compiler.compileFile(pmmlFilePath)
			/** create a string of it, parse and generate the scala and model definition */
			val xmlSrcTxt : String  = Source.fromFile(pmmlFilePath).mkString
			val (modelSrc, msgDef) : (String,ModelDef) = compiler.compile(xmlSrcTxt,"/tmp")
			valid = if (   modelSrc != null 
			    		&& msgDef != null
		    			&& jarTargetDir != null 
    					&& classpath != null 
    					&& scalahome != null 
    					&& clientName != null 
    					&& javahome != null) true else false
	
			if (! valid) {
				val optionsBuffer : StringBuilder = new StringBuilder
				optionsBuffer.append("{pmmlFilePath = $pmmlFilePath, clientName = $clientName }")
				val options : String = optionsBuffer.toString
				logger.error("Insufficient arguments")
				logger.error(PmmlCompiler.usage)
				logger.error("The following options were supplied:")
				logger.error(s"   $options\n")
				sys.exit(1)
			} 
			
			val (jarPath,depJars) : (String,Array[String]) = if (! skipJar) {
			  compiler.createJar(modelSrc
								, classpath
								, scalaSrcTargetPath
								, jarTargetDir
								, manifestpath
								, scalahome
								, javahome
								, skipJar
							        , "/tmp")
			} else {
				("jar generation was suppressed by command option --skipjar", null)
			}
		
			logger.debug(s"jar path returned from createJar = $jarPath")
			val buffer : StringBuilder = new StringBuilder
			depJars.addString(buffer, ":")
			logger.debug(s"depJars returned from createJar = ${buffer.toString}")
			logger.debug("PmmlCompiler.main ends")
		}
	}    
}


class PmmlCompiler(val mgr : MdMgr, val clientName : String, val logger : Logger, val injectLogging : Boolean, val jaPaths : Array[String]) {
	
	if (jaPaths == null || jaPaths.size == 0)
		PMMLConfiguration.jarPaths = Array(".").toSet
	else
		PMMLConfiguration.jarPaths = jaPaths.toSet
  
	logger.debug("PmmlCompiler ctor ... begins")

	var pmmlFilePath : String = "NOTSET"

	var valid : Boolean = if (clientName != null) true else false
	if (! valid) {
		val optionsBuffer : StringBuilder = new StringBuilder
		optionsBuffer.append("{clientName = $clientName }")
		val options : String = optionsBuffer.toString
		logger.error("Insufficient arguments")
		logger.error("The tenant identifier must be supplied.  Instead, the following options were supplied:")
		logger.error(s"   $options\n")
		sys.exit(1)
	}

	/** the PmmlContext needs a fully operational mdmgr... create it after MetadataLoad. */
	var ctx : PmmlContext = new PmmlContext(mgr, injectLogging)


	/** Compile the source found in the supplied path, producing scala src in the returned srcCode string.
	 *  FIXME: The jars required by the model is to be returned as the second object in the pair returned.
	 *  This is not implemented.  The jars for any Message or ContainerDef encountered during parse needs to 
	 *  collected in a set and returned. */
	def compileFile(pmmlPath : String) : (String, ModelDef) = {
	  
		logger.debug("compile begins")
		pmmlFilePath = pmmlPath

		ctx.pmmlTerms("PMML") = Some(pmmlFilePath)  /** to document/identify originating file in source to be generated */
		ctx.ClientName(clientName)
		
		val file = new File(pmmlFilePath)
		val inputStream = new FileInputStream(file);
		val reader = new InputStreamReader(inputStream, "UTF-8");
		val is = new InputSource(reader);
		is.setEncoding("UTF-8");

		/** 
		 *  1) parse the xml
		 *  2) transform raw tree to cooked tree 
		 *  3) generate code 
		 */
		parseObject(ctx, is);
		xformObject(ctx)
		ctx.NodeXformComplete
		val srcCode : String = generateCode(ctx)
		
		val modelDef : ModelDef = if (ctx.ErrorCount > 0) {
			logger.error(s"Pmml compile completes... error count = ${ctx.ErrorCount}... Scala compile abandoned... model definition will NOT be produced.")
			null
		} else {
			val modlDef : ModelDef = constructModelDef(ctx)
			modlDef
		}
				
		(srcCode, modelDef)
	}
	
	/** Compile the source supplied here in the string argument. Return scala src in the returned srcCode string.
	 *  FIXME: The jars required by the model is to be returned as the second object in the pair returned.
	 *  This is not implemented.  The jars for any Message or ContainerDef encountered during parse needs to 
	 *  collected in a set and returned. */
	def compile(pmmlString : String,workDir: String,recompile: Boolean = false)  : (String, ModelDef) = {
	  
		logger.debug("compile begins")
		
		ctx.pmmlTerms("PMML") = Some("Pmml source supplied as string") 
		ctx.ClientName(clientName)

		val inputStream : InputStream  = new ByteArrayInputStream(pmmlString.getBytes(StandardCharsets.UTF_8));
		val reader = new InputStreamReader(inputStream, "UTF-8");
		val is = new InputSource(reader);
		is.setEncoding("UTF-8");
		
		/** 
		 *  1) parse the xml
		 *  2) transform raw tree to cooked tree 
		 *  3) generate code 
		 */
		parseObject(ctx, is);
		xformObject(ctx)
		val srcCode : String = generateCode(ctx)
		
		if (srcCode != null) {
			/** save a copy of the original xml that was sent as a string to this function.  It is to be included
			 *  in the jar, should that method be called.  Anticipate that possibility.  Use a file name based 
			 *  upon the model name.
			 */
			val optXmlFileName : Option[String] = ctx.pmmlTerms.apply("ClassName")
			val xmlFileName : String = optXmlFileName match {
			  case Some(optXmlFileName) => workDir + "/" + optXmlFileName + ".xml"
			  case _ => workDir + "/somePmmlFileName.xml"
			}
			writeSrcFile(pmmlString, xmlFileName)
			pmmlFilePath = xmlFileName
		}
		
		val modelDef : ModelDef = if (ctx.ErrorCount > 0) {
			logger.error(s"Pmml compile completes... error count = ${ctx.ErrorCount}... Scala compile abandoned... model definition will NOT be produced.")
			null
		} else {
			val modlDef : ModelDef = constructModelDef(ctx,recompile)
			modlDef
		}
		
		(srcCode, modelDef)
	}
	//
	
	private def constructModelDef(ctx : PmmlContext,recompile:Boolean = false) : ModelDef = {
		/**
			val curTmInMilli = System.currentTimeMillis
			val modelPkg = s"com.$clientName.$classname_$curTmInMilli.pmml"
			ctx.pmmlTerms("ModelPackageName") = Some(modelPkg)
			ctx.pmmlTerms("ClassName") = Some(classname)
			ctx.modelInputs(fldRef.field.toLowerCase()) = (cName, fieldName, baseType.NameSpace, baseType.Name, isGlobal, null) // BUGBUG:: We need to fill collectionType properly instead of null
			val fldvalue = (name, fieldBaseTypeDef.NameSpace, fieldBaseTypeDef.typeString)
			modelOutputs(name.toLowerCase()) = fldvalue	 
		 */
		val optModelPkg = ctx.pmmlTerms.apply("ModelPackageName")
		val modelPkg = optModelPkg match {
		  case Some(optModelPkg) => optModelPkg
		  case _ => "there is no pkg for this model... incredible as it seems"
		}
		val optClassName = ctx.pmmlTerms.apply("ClassName")
		val className = optClassName match {
		  case Some(optClassName) => optClassName
		  case _ => "there is no class name for this model... incredible as it seems"
		}

		val optVersion = ctx.pmmlTerms.apply("VersionNumber")
		val versionNo : String = optVersion match {
		  case Some(optVersion) => optVersion.toString
		  case _ => "there is no class name for this model... incredible as it seems"
		}
		
		val modelNamespace = modelPkg
		
		val fqClassName : String = modelNamespace + "." + className + "Factory"
		/** FIXME: This is hard coded now, but should be determined based upon the model type specified in the xml */
		val modelType : String = "RuleSet" 
		val inputVars : List[(String,String,String,String,Boolean,String)] = ctx.modelInputs.values.toList
		logger.debug(s"\n\ndump of the inputVars for the model metadata construction:")
		inputVars.foreach(aVar => {
			/** FIXME: These names are not quite right ... they should match this output  
			 *  ("outpatientInfoThisLastYear", "com.medco.messages.rel10.V1000000.OutpatientClaim", "com.medco.messages.rel10", "outpatientclaim", false, null)
			 *  (input name, input type, type namespace, type name, isglobal, collection type)
			 */
			val (modelNmSpc, varnm, typeNmspc, typeNm, isGlobal, collectionType) : (String,String,String,String,Boolean,String) = aVar
			logger.debug(s"(${'"'}$modelNmSpc${'"'}, ${'"'}$varnm${'"'}, ${'"'}$typeNmspc${'"'}, ${'"'}$typeNm${'"'}, $isGlobal, $collectionType)")
		})
		val outputVars : List[(String,String,String)] = ctx.modelOutputs.values.toList
		logger.debug(s"\n\ndump of the outputVars for the model metadata construction:")
		outputVars.foreach(aVar => {
			val (varnm, typeNmspc, typeNm) : (String,String,String) = aVar
			logger.debug(s"(${'"'}$varnm${'"'}, ${'"'}$typeNmspc${'"'}, ${'"'}$typeNm${'"'})")
		})
		
		val modelVer : Option[String] = ctx.pmmlTerms.apply("VersionNumber")
		val modelVersion : Long = modelVer match {
		  case Some(modelVer) => modelVer.toLong
		  case _ => 1000001
		}
		
		ctx.pmmlTerms("PMML") = Some(pmmlFilePath)  /** to document/identify originating file in source to be generated */
		ctx.ClientName(clientName)
		
		val jarName : String = JarName(ctx)

		val modelDef : ModelDef = mgr.MakeModelDef(modelNamespace
							    , className
							    , fqClassName
							    , modelType
							    , inputVars
							    , outputVars
							    , modelVersion
							    , jarName
							    , ctx.classPathJars.toArray
							    , recompile)

		modelDef
	  
	}
	/**
	 *  4) Compile and jar the generated code 
	 *  5) Optionally write the source file to additional file 
	 *  6) Write the environment variables for the registerModel script (based upon Header info in pmml)
	 */
	def createJar( srcCode : String
				, classpath : String
				, scalaSrcTargetPath : String
				, jarTargetDir : String
				, manifestpath : String
				, scalahome : String
				, javahome : String
				, skipJar : Boolean
			        , workDir: String) : (String,Array[String]) = {
	  
		logger.debug("createJar begins")

		if (scalaSrcTargetPath != null) {
			logger.debug(s"write a copy of the generated source to $scalaSrcTargetPath")
			writeSrcFile(srcCode, scalaSrcTargetPath)
		}
		
		val jarPath = if (! skipJar) {
			val (jarRc, jarpath) = jarCode(ctx, srcCode, classpath, jarTargetDir, manifestpath, clientName, pmmlFilePath, scalahome, javahome,workDir)
			if (jarRc == 0) {
				jarpath 
			} else {
				logger.error("jarCode has failed... no jar produced")
				"Not Set"
			}
		} else {
			"Not Set"
		}
		
		
		val (modelClassName, modelVersion) = deriveClassNameAndModelVersion(ctx, clientName)
			
		logger.debug("#########################################################")
		logger.debug("Dump model variables for diagnostic purposes:")
		logger.debug("#########################################################")
		logger.debug(s"modelClassName=$modelClassName");
		logger.debug(s"modelVersion=$modelVersion");
		logger.debug(s"pmmlFilePath=$pmmlFilePath");
		logger.debug(s"pmml=`cat ${'"'}$pmmlFilePath${'"'}`");
		logger.debug(s"jarPath=$jarPath");
		logger.debug(s"clientName=$clientName");
		
		logger.debug("createJar ends")
		
		val depJars: Array[String] = classpath.split(':').toSet.toArray
		(jarPath,depJars)
	}
	
	private def initializeReader() : XMLReader = {
		val factory = SAXParserFactory.newInstance()
		val parser = factory.newSAXParser()
		val xmlreader = parser.getXMLReader()
		xmlreader
	}

	/** Parse the Pmml file to create a queue of PmmlNodes */
	private def parseObject( ctx: PmmlContext, is :InputSource )  = {
		try {
			val xmlreader = initializeReader();
			val handler = new PmmlParseEventParser(ctx, new PmmlNodeGeneratorDispatcher(ctx))
			xmlreader.setContentHandler(handler);
			xmlreader.parse(is);
		} catch {
			case ex: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(ex)
        logger.debug("StackTrace:"+stackTrace)
        throw ex}
		}
	}
	
	/** Transform the queue of PmmlNodes in the ctx to a queue of PmmlExecNodes */
	private def xformObject( ctx: PmmlContext )  = {
		try {
			val xform : PmmlExecNodeGeneratorDispatcher = new PmmlExecNodeGeneratorDispatcher(ctx);
			xform.transform;
		} catch {
			case t: Throwable => {val stackTrace = StackTrace.ThrowableTraceString(t)
     logger.debug("StackTrace:"+stackTrace)   
      }
		}
	}

	/** Transform the map of PmmlExecNodes in the ctx to scala source code */
	private def generateCode( ctx: PmmlContext ) : String  = {
		var srcCode = "No Source Code"
		try {
			val generator : PmmlModelGenerator = new PmmlModelGenerator(ctx);
			srcCode = generator.ComposePmmlRuleSetModelSource

		} catch {
			case t: Throwable => {
			  /** 
			   *  Something bad has happened ... 
			   *  If there are elements on the ctx's elementStack print them 
			   */
			  val buffer : StringBuilder = new StringBuilder
			  while (ctx.elementStack.nonEmpty) { 
				  buffer.append(ctx.elementStack.pop.toString)
				  if (ctx.elementStack.nonEmpty)
				    buffer.append(" found in ")
			  }
			  if (buffer.size > 0) {
				  val errorVicinity : String = buffer.toString
				  logger.error(s"Exception detected in $errorVicinity")
			  }
			  val stackTrace = StackTrace.ThrowableTraceString(t) 
        logger.debug("StackTrace:"+stackTrace)
			}
		}
		srcCode
	}
	
	/** get the jar name (sans path) for this model */
	private def JarName(ctx : PmmlContext) : String = {
		val appNm : Option[String] = ctx.pmmlTerms.apply("ClassName")
		val moduleName : String = appNm match {
		  case Some(appNm) => appNm
		  case _ => "None"
		}
		val optVersion = ctx.pmmlTerms.apply("VersionNumber")
		val versionNo : String = optVersion match {
		  case Some(optVersion) => optVersion.toString
		  case _ => "there is no class name for this model... incredible as it seems"
		}
		val nmspc : String = "System_" /** only System namespace possible at the moment */
		val moduleNameJar : String = s"$nmspc$moduleName${'_'}$versionNo.jar"
		moduleNameJar.toLowerCase
	}

	
	 /* 
	  * Compile the supplied generated code and jar it, the originating pmml model, and the class output from the 
	  * compile.  Add a registration module as well.  Note the classpath dependencies in the manifest.mf file
	  * that is also included in the jar.
	  */
	private def jarCode ( ctx: PmmlContext
			    , scalaGeneratedCode : String
			    , classpath : String
			    , jarTargetDir : String
			    , manifestpath : String
			    , clientName : String
			    , pmmlFilePath : String
			    , scalahome : String
			    , javahome : String
			    , workDir: String) : (Int, String) =
	{
		/** get the class name to create the file, make directories, jar, etc */
		val appNm : Option[String] = ctx.pmmlTerms.apply("ClassName")
		val moduleName : String = appNm match {
		  case Some(appNm) => appNm
		  case _ => "None"
		}
		
		/** prep the workspace and go there*/
		val killDir = s"rm -Rf $workDir/$moduleName"
		val killDirRc = Process(killDir).! /** remove any work space that may be present from prior failed run  */
		if (killDirRc != 0) {
			logger.error(s"Unable to rm $workDir/$moduleName ... rc = $killDirRc")
			return (killDirRc, "")
		}
		val buildDir = s"mkdir $workDir/$moduleName"
		val tmpdirRc = Process(buildDir).! /** create a clean space to work in */
		if (tmpdirRc != 0) {
			logger.error(s"The compilation of the generated source has failed because $buildDir could not be created ... rc = $tmpdirRc")
			return (tmpdirRc, "")
		}
		/** create a copy of the pmml source in the work directory */
		val cpRc = Process(s"cp $pmmlFilePath $workDir/$moduleName/").!
		if (cpRc != 0) {
			logger.error(s"Unable to create a copy of the pmml source xml for inclusion in jar ... rc = $cpRc")
			return (cpRc, "")
		}

		logger.debug(s"compile $moduleName")
		/** compile the generated code */
		val rc : Int = compile(ctx, s"$workDir/$moduleName", scalahome, moduleName, classpath, scalaGeneratedCode, clientName,workDir)
		if (rc != 0) {
			return (rc, "")
		}
		logger.debug(s"compile or $moduleName ends...rc = $rc")
		
		/** build the manifest */
		//logger.debug(s"create the manifest")
		//val manifestFileName : String =  s"manifest.mf"
		//createManifest(ctx, s"$workDir/$moduleName", manifestFileName, manifestpath, moduleName, clientName)

		/** create the jar */
		var moduleNameJar : String = JarName(ctx)

		var d = new java.util.Date()
		var epochTime = d.getTime
		// insert epochTime into jar file
		val jar_tokens = moduleNameJar.split("\\.")
	        moduleNameJar = jar_tokens(0) + "_" + epochTime + ".jar"
		
		logger.debug(s"create the jar $workDir/$moduleNameJar")
		val jarCmd : String = s"$javahome/bin/jar cvf $workDir/$moduleNameJar -C $workDir/$moduleName/ ."
		logger.debug(s"jar cmd used: $jarCmd")
		logger.debug(s"Jar $moduleNameJar produced.  Its contents:")
		val jarRc : Int = Process(jarCmd).!
		if (jarRc != 0) {
			logger.error(s"unable to create jar $moduleNameJar ... rc = $jarRc")
			return (jarRc, "")
		}
		logger.debug(s"jar of $moduleNameJar complete ... $jarRc")
		
		/** move the new jar to the target dir where it is to live */
		logger.debug(s"move the jar $workDir/$moduleNameJar to the target $jarTargetDir")
		val mvCmd : String = s"mv $workDir/$moduleNameJar $jarTargetDir/"
		val mvCmdRc : Int = Process(mvCmd).!
		if (mvCmdRc != 0) {
			logger.error(s"unable to move new jar $moduleNameJar to target directory, $jarTargetDir ... rc = $mvCmdRc")
			logger.error(s"cmd used : $mvCmd")
		}
		logger.debug(s"move of jar $workDir/$moduleNameJar to the target $jarTargetDir ends... rc = $mvCmdRc")
		
		(jarRc, s"$moduleNameJar")
	}
	
	private def compile (ctx: PmmlContext
				,jarBuildDir : String
				, scalahome : String
				, moduleName : String
				, classpath : String
				, scalaGeneratedCode : String
				, clientName : String
			        , workDir : String) : Int = 
	{  
		val scalaSrcFileName : String = s"$moduleName.scala"
		createScalaFile(s"$jarBuildDir", scalaSrcFileName, scalaGeneratedCode)
		
		val scalacCmd = Seq("sh", "-c", s"$scalahome/bin/scalac -cp $classpath $jarBuildDir/$scalaSrcFileName")
		logger.debug(s"scalac cmd used: $scalacCmd")
		val scalaCompileRc = Process(scalacCmd).!
		if (scalaCompileRc != 0) {
			logger.error(s"Compile for $scalaSrcFileName has failed...rc = $scalaCompileRc")
			logger.error(s"Command used: $scalacCmd")
		}
		
		val firstLine : String = scalaGeneratedCode.takeWhile(ch => ch != '\n').toString
		val topPkgNamePattern = "package[ \t]+([a-zA-Z0-9_]+).*".r
		val topPkgNamePattern(topPkgName) = firstLine
		
		/** The compiled class files are found in com/$client/pmml of the current folder.. mv them to $jarBuildDir*/
		val mvCmd : String = s"mv $topPkgName $workDir/$moduleName/"
		val mvCmdRc : Int = Process(mvCmd).!
		if (mvCmdRc != 0) {
			logger.error(s"unable to move classes to build directory, $jarBuildDir ... rc = $mvCmdRc")
			logger.error(s"cmd used : $mvCmd")
		}		
		//(scalaCompileRc | regBldRc | mvCmdRc)
		(scalaCompileRc | mvCmdRc)
	}
	
	private def createManifest(ctx: PmmlContext, jarBuildDir : String, manifestName : String, manifestpath : String, moduleName : String, clientName : String) {

		val substitutionMap : HashMap[String,String] = HashMap[String,String]()
		substitutionMap += "%applicationName%" -> s"$moduleName"
		substitutionMap += "%client%" -> s"$clientName"
		
		val pwd : String = Process(s"pwd").!!
		val pwdStr : String = pwd.trim
		val manifest : String = ctx.fcnSubstitute.makeFileSubstitutions(manifestpath, substitutionMap)
		logger.debug(s"manifest with substitutions:\n\n$manifest\n")
		val manifestSlash = if (jarBuildDir.last != '/') "/" else "" 
		val manifestFilePath = s"$jarBuildDir$manifestSlash$manifestName" 
		val file = new File(manifestFilePath);
		val bufferedWriter = new BufferedWriter(new FileWriter(file))
		bufferedWriter.write(manifest)
		bufferedWriter.close
	}
	
		/** create the registration scala src and compile it */
	private def createRegistrationClass(ctx: PmmlContext
								, jarBuildDir : String
								, scalahome : String
								, moduleName : String
								, classpath : String
								, clientName : String) : Int = 
	{
		/** form a registration src module in the jarBuildDir */
		val substitutionMap : HashMap[String,String] = HashMap[String,String]()
		substitutionMap += "%ModelPackage%" -> s"com.$clientName.pmml"
		substitutionMap += "%ModelClassName%" -> s"$moduleName"
	
		val pwd : String = Process(s"pwd").!!
		val pwdStr : String = pwd.trim
		val registrationModuleName : String = "RegistrationInfo.scala"
		val registrationTemplatePath : String = s"$pwdStr/resources/RegistrationInfoTemplate.scala"
		val registrationSrc : String = ctx.fcnSubstitute.makeFileSubstitutions(registrationTemplatePath, substitutionMap)
		logger.debug(s"registration source with substitutions made:\n\n$registrationSrc\n")
		val regSrcSlash = if (jarBuildDir.last != '/') "/" else "" 
		val regSrcFilePath = s"$jarBuildDir$regSrcSlash$registrationModuleName" 
		val file = new File(regSrcFilePath);
		val bufferedWriter = new BufferedWriter(new FileWriter(file))
		bufferedWriter.write(registrationSrc)
		bufferedWriter.close
		
		/** compile it */
	  
		val scalacCmd = Seq("sh", "-c", s"$scalahome/bin/scalac -cp $classpath $jarBuildDir/$registrationModuleName")
		logger.debug(s"scalac reg compile cmd used: $scalacCmd")
		val scalaCompileRc = Process(scalacCmd).!
		if (scalaCompileRc != 0) {
			logger.error(s"Compile for $registrationModuleName has failed...rc = $scalaCompileRc")
			logger.error(s"Command used: $scalacCmd")
		}
		
		scalaCompileRc
	}
		
	private def writeSrcFile(scalaGeneratedCode : String, scalaSrcTargetPath : String) {
		val file = new File(scalaSrcTargetPath);
		val bufferedWriter = new BufferedWriter(new FileWriter(file))
		bufferedWriter.write(scalaGeneratedCode)
		bufferedWriter.close
	}
	
	private def createScalaFile(targPath : String, moduleSrcName : String, scalaGeneratedCode : String) {
		val scalaTargetPath = s"$targPath/$moduleSrcName"
		writeSrcFile(scalaGeneratedCode, scalaTargetPath)
	}
	
	private def deriveClassNameAndModelVersion(ctx : PmmlContext, client : String) : (String, Long) = {
	  
	  	/** 
		 *  Generate a name for the class based upon the id info found in the Header 
		 *  Squeeze all but typical alphameric characters from app name and version string
		 */
		val nm : Option[String] = ctx.pmmlTerms.apply("ClassName")
		val className = nm match {
		  case Some(nm) => nm
		  case _ => "NoClassName"
		}

		val pkg : Option[String] = ctx.pmmlTerms.apply("ModelPackageName")
		val pkgName = pkg match {
		  case Some(pkg) => pkg
		  case _ => "NoPackageName"
		}

		val versionBuffer : StringBuilder = new StringBuilder()
		val modelVersion : Option[String] = ctx.pmmlTerms.apply("Version")
		
		val numVersion = if (modelVersion == None) 1000001 else MdMgr.ConvertVersionToLong(MdMgr.FormatVersion(modelVersion.get))
	  
/*		
		val numPattern = "[0-9]+".r
		var versionString : String = modelVersion match {
		  case Some(modelVersion) => modelVersion
		  case _ => "1000001"
		}
		val numPieces = numPattern.findAllIn(versionString)
		for (piece <- numPieces) versionBuffer.append(piece)
		var numVersion : Long = 0
		try {
			numVersion = versionBuffer.toLong
		} catch {
		  case t : Throwable => numVersion = 1000001
		}
*/
		(s"$pkgName.$className", numVersion)
		
	}
	
}

