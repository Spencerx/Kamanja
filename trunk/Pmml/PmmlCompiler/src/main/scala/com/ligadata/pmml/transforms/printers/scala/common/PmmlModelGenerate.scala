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

import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.pmml.compiler._
import com.ligadata.pmml.support._
import com.ligadata.pmml.traits._
import com.ligadata.kamanja.metadata._
import com.ligadata.pmml.compiler._
import com.ligadata.pmml.support._
import com.ligadata.pmml.traits._
import com.ligadata.pmml.syntaxtree.cooked.common._
import com.ligadata.pmml.transforms.printers.scala.ruleset._


class PmmlModelGenerator(ctx : PmmlContext) extends CodePrinterDispatch with com.ligadata.pmml.compiler.LogTrait {

	/** Select the appropriate code generator for the supplied PmmlExecNode, invoke
	    its CodePrinter, and (presumably) add the generated code string to a
	    StringBuilder owned by the CodePrinterDispatch implementation.

	    The CodeFragment.Kind describes what kind of string should be produced.  Note 
	    that many nodes can produce different representations. For example, is a 
	    declaration or an "use" expression required for a DataField.
 	    
 	    @param node the PmmlExecNode
 	    @param buffer the caller's buffer to which the print for the node (and below it) will be left
 	    @param the kind of code fragment to generate...any 
 	    	{VARDECL, VALDECL, FUNCCALL, DERIVEDCLASS, RULECLASS, RULESETCLASS , MININGFIELD, MAPVALUE, AGGREGATE, USERFUNCTION}
	 */
	def generate(node : Option[PmmlExecNode]
				, buffer: StringBuilder
				, kind : CodeFragment.Kind) : Unit = {
		val printer : CodePrinter = node match {
		  case Some(node) => {
			  val nodeName : String = node.qName
			  if (nodeName == "SimpleRule") {
				  val stop : Boolean = true
			  }
			  val pr : CodePrinter = pmmlXNodePrinterMap.getOrElse(node.qName, null)
			  if (pr == null) {
				  PmmlError.logError(ctx, s"there is no printer for node type ${node.getClass.getName} ... check dispatch map initialization")
			  }
			  pr
		  }
		  case _ => {
			  PmmlError.logError(ctx, s"This is a fine howdy-doo! No node supplied to the CodePrinterDispatch")
			  null
		  }
		}
		
		if (printer != null && buffer != null) {
			val codeString : String = printer.print(node,this,kind,Traversal.PREORDER)
			buffer.append(codeString)
		} else {
			if (buffer == null) {
				PmmlError.logError(ctx, s"no print buffer was supplied to stash the results...try again")
			}
		}
	}
	
    val  pmmlXNodePrinterMap = Map[String, CodePrinter](
	      ("Apply" -> new ApplyCodePrinter(ctx))
	    , ("Array" -> new ArrayCodePrinter(ctx))
	    , ("CompoundPredicate" -> new CompoundPredicateCodePrinter(ctx))
	    , ("Constant" -> new ConstantCodePrinter(ctx))
	    , ("DataDictionary" -> new DataDictionaryCodePrinter(ctx))
	    , ("DataField" -> new DataFieldCodePrinter(ctx))
	    , ("DefineFunction" -> new DefineFunctionCodePrinter(ctx))
	    , ("DerivedField" -> new DerivedFieldCodePrinter(ctx))
	    , ("FieldColumnPair" -> new FieldColumnPairCodePrinter(ctx))
	    , ("FieldRef" -> new FieldRefCodePrinter(ctx))
	    , ("Header" -> new HeaderCodePrinter(ctx))
	    , ("MapValues" -> new MapValuesMapCodePrinter(ctx))
	    , ("MapValuesArray" -> new MapValuesArrayCodePrinter(ctx))
	    , ("MapValuesArrayExternal" -> new MapValuesArrayExternalCodePrinter(ctx))
	    , ("MapValuesArrayInline" -> new MapValuesArrayCodePrinter(ctx))
	    , ("MapValuesMap" -> new MapValuesMapCodePrinter(ctx))
	    , ("MapValuesMapExternal" -> new MapValuesMapExternalCodePrinter(ctx))
	    , ("MapValuesMapInline" -> new MapValuesMapInlineCodePrinter(ctx))
	    , ("MiningField" -> new MiningFieldCodePrinter(ctx))
	    , ("ParameterField" -> new ParameterFieldCodePrinter(ctx))
	    , ("RuleSelectionMethod" -> new RuleSelectionMethodCodePrinter(ctx))
	    , ("RuleSet" -> new RuleSetCodePrinter(ctx))
	    , ("RuleSetModel" -> new RuleSetModelCodePrinter(ctx))
	    , ("ScoreDistribution" -> new ScoreDistributionCodePrinter(ctx))
	    , ("SimplePredicate" -> new SimplePredicateCodePrinter(ctx))
	    , ("SimpleRule" -> new SimpleRuleCodePrinter(ctx))
	    , ("SimpleSetPredicate" -> new SimpleSetPredicateCodePrinter(ctx))
	    , ("TransformationDictionary" -> new TransformationDictionaryCodePrinter(ctx)))	

	/** 
	 *   Answer the scala source for this RuleSetModel.  
	 *  
	 *   Collect messages and containers for ModelDef dependencies.  Perform syntactical and 
	 *   simple semantic checks aborting the print if there are any issues.  Do some simple 
	 *   planning on some nodes (e.g., apply 'if').  Generate the derived field classes and the
	 *   model class. 
	 *   
	 *   @return scala source file to be compiled that represents the supplied RuleSetModel PMML.
	 */
	def ComposePmmlRuleSetModelSource : String = {
		/** fix up the ctx before proceeding */
		val rsm : Option[PmmlExecNode] = ctx.pmmlExecNodeMap.apply("RuleSetModel") 
		rsm match {
		  case Some(rsm) => {
			  val rsmInst : xRuleSetModel = rsm.asInstanceOf[xRuleSetModel]
			  ctx.MiningSchemaMap(rsmInst.MiningSchemaMap)
		  }
		  case _ => PmmlError.logError(ctx, s"there was no RuleSetModel avaialble... whoops!\n")
		}
		
		/** Collect the namespaces for all known models */
		ctx.CollectNamespaceSet
		
		/** Register the parameters */
		ctx.RegisterMessages
		/** 
		 *  Pass through the TransactionDictionary xDerivedFields seeking any message or container definitions. 
		 *  These are added to the ctx.containersInScope just like the parameters field values in the datadictionary
		 */
		ctx.collectContainers
		
		/** Syntactic and Preliminary Semantic checks ... if this one fails, don't bother with the rest of it. */
		ctx.syntaxCheck
		if (ctx.ErrorCount > 0) {
			logger.error(s"\nThere are either syntax or simple semantic issues with this pmml... no point proceeding...\n")
			"No Source was generated"
		} else {	
			/** collect the udf functions from the TransactionDictinary, etc. */
			ctx.collectUdfs()
			/** transform the 'if' apply elements rewriting the child list by removing the true and false elements at the list end.  */
			ctx.transformTopLevelApplyNodes()
			
			/** collect additional information needed to decorate the runtime RuleSetModel class */
			ctx.ruleSetModelInfoCollector()
			/** ditto for SimpleRule class */
			ctx.simpleRuleInfoCollector() 
			
			/** collect the input field and output fields for model definition generation */
			ctx.collectModelInputVars
			ctx.collectModelOutputVars
			
			/** 
			 *  Build the source code returning StringBuilders for the pieces.  The
			 *  classDecls for the RuleSetModel, Rule and Derived fields are processed
			 *  first... they are printed in the other order however with the model class first.
			 */
			val codeBuffer : StringBuilder = new StringBuilder()
	
			val classDeclsBuffer : StringBuilder = NodePrinterHelpers.classDecls(ctx, this)
			val modelClassBuffer : StringBuilder = NodePrinterHelpers.modelClass(ctx, this)
			
			codeBuffer.append(modelClassBuffer.toString)
			codeBuffer.append(classDeclsBuffer.toString)
			
			/** 
			 *  End of Application Class generation.  NOTE: All Derive_<fieldname>, SimpleRule_<rulename>, et al are enclosed in 
			 *  the model class as nested classes when the '}' is placed here.  This allows the namespace to be shared 
			 *  among many models by nesting potentially conflicting names inside the model class.  
			 */
			codeBuffer.append(s"}\n")

			
			codeBuffer.toString
		}
    }
}
	
	
	
