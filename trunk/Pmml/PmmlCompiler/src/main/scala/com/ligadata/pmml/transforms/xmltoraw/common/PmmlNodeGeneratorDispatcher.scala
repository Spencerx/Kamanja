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

package com.ligadata.pmml.transforms.xmltoraw.common

import org.apache.logging.log4j.{ Logger, LogManager }
import org.xml.sax.Attributes
import com.ligadata.pmml.compiler._
import com.ligadata.pmml.traits._
import com.ligadata.pmml.support._
import com.ligadata.pmml.syntaxtree.raw.common._
import com.ligadata.pmml.transforms.xmltoraw.common._
import com.ligadata.pmml.transforms.xmltoraw.ruleset._


class PmmlNodeGeneratorDispatcher(ctx : PmmlContext) extends PmmlNodeGeneratorDispatch with LogTrait {
  
  
	/** 
		Select the appropriate PmmlNode generator for the supplied xml values, locate its
	    PmmlNodeGenerator (the 'qName' is the key), and dispatch it. The returned node is added to the 
	    syntax tree owned by (or addressable by) the PmmlNodeGeneratorDispatch implementation.
 	    
 	    @param namespaceURI: String
 	    @param localName: String 
 	    @param qName:String
 	    @param atts: Attributes
 	    @param lineNumber : Int
 	    @param columnNumber : Int   
 	    @return Unit
	 */

	def dispatch(namespaceURI: String, localName: String , qName:String , atts: Attributes, lineNumber : Int, columnNumber : Int) : Unit = {
		val generator : PmmlNodeGenerator = if (qName != null) {
		  	val gen : PmmlNodeGenerator = pmmlNodeDispatchMap.getOrElse(qName, null)
			if (gen == null) {
				if (! ignoreList.contains(qName)) {
					PmmlError.logError(ctx, s"there is no PmmlNode generator for node type $qName ... check dispatch map initialization")
				}
			}
			gen
		}
		else {
			PmmlError.logError(ctx, s"This is a fine howdy-doo! No node supplied to the CodePrinterDispatch")
			null
	  	}
		
		if (generator != null) {
			val node : PmmlNode = generator.make(namespaceURI, localName, qName, atts, lineNumber, columnNumber)
			if (node != null) {
				/** update the parent on the stack if appropriate */
				if (! ctx.pmmlNodeStack.isEmpty) {
					ctx.pmmlNodeStack.top.addChild(node)
				}
				
				/** push the newly established node to the stack */
				ctx.pmmlNodeStack.push(node)
			}
		}

	}
	
	/** In the event it is desirable to ignore certain missing handlers, add the qName here */
	val ignoreList : Set[String] = Set[String]("PMML")

    val pmmlNodeDispatchMap  = Map[String, PmmlNodeGenerator](
	      ("Pmml" -> new ApplicationPmmlNodeGenerator)
	    , ("Application" -> new ApplicationPmmlNodeGenerator)
	    , ("Apply" -> new ApplyPmmlNodeGenerator)
	    , ("Array" -> new ArrayPmmlNodeGenerator)
	    , ("CompoundPredicate" -> new CompoundPredicatePmmlNodeGenerator)
	    , ("Constant" -> new ConstantPmmlNodeGenerator)
	    , ("DataDictionary" -> new DataDictionaryPmmlNodeGenerator)
	    , ("DataField" -> new DataFieldPmmlNodeGenerator)
	    , ("DefineFunction" -> new DefineFunctionPmmlNodeGenerator)
	    , ("DerivedField" -> new DerivedFieldPmmlNodeGenerator)
	    , ("FieldColumnPair" -> new FieldColumnPairPmmlNodeGenerator)
	    , ("FieldRef" -> new FieldRefPmmlNodeGenerator)
	    , ("InlineTable" -> new InlineTablePmmlNodeGenerator)
	    , ("Interval" -> new IntervalPmmlNodeGenerator)
	    , ("MapValues" -> new MapValuesPmmlNodeGenerator)
	    , ("MiningField" -> new MiningFieldPmmlNodeGenerator)
	    , ("MiningSchema" -> new MiningSchemaPmmlNodeGenerator)
	    , ("ParameterField" -> new ParameterFieldPmmlNodeGenerator)
	    , ("Header" -> new PmmlHeaderPmmlNodeGenerator)
	    , ("row" -> new RowPmmlNodeGenerator)
	    , ("RowTuple" -> new RowTuplePmmlNodeGenerator)
	    , ("RuleSelectionMethod" -> new RuleSelectionMethodPmmlNodeGenerator)
	    , ("RuleSetModel" -> new RuleSetModelPmmlNodeGenerator)
	    , ("RuleSet" -> new RuleSetPmmlNodeGenerator)
	    , ("ScoreDistribution" -> new ScoreDistributionPmmlNodeGenerator)
	    , ("SimplePredicate" -> new SimplePredicatePmmlNodeGenerator)
	    , ("SimpleRule" -> new SimpleRulePmmlNodeGenerator)
	    , ("SimpleSetPredicate" -> new SimpleSetPredicatePmmlNodeGenerator)
	    , ("TableLocator" -> new TableLocatorPmmlNodeGenerator)
	    , ("TransformationDictionary" -> new TransformationDictionaryPmmlNodeGenerator)
	    , ("Value" -> new ValuePmmlNodeGenerator))
	
}	
	
