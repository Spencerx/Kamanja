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

package com.ligadata.pmml.transforms.rawtocooked.common

import org.apache.logging.log4j.{ Logger, LogManager }
import org.xml.sax.Attributes
import com.ligadata.pmml.compiler._
import com.ligadata.pmml.support._
import com.ligadata.pmml.traits._
import com.ligadata.pmml.syntaxtree.raw.common._
import com.ligadata.pmml.syntaxtree.cooked.common._
import com.ligadata.pmml.transforms.rawtocooked.ruleset._

class PmmlExecNodeGeneratorDispatcher(ctx : PmmlContext) extends PmmlExecNodeGeneratorDispatch with com.ligadata.pmml.compiler.LogTrait {

	def transform : Unit = {
		for (node <- ctx.pmmlNodeQueue) {
			dispatch(node.qName, node)		
		}

	}

	/** Select the appropriate PmmlExecNode generator (a PmmlExecNodeGenerator) for the supplied xml element name by locating its
	    PmmlExecNodeGenerator (the 'qName' is the element name key), and dispatch it. The returned node is added to the syntax tree
	    being constructed.  The dispatcher is passed as an argument so that the generators are free to access the
	    'raw' and 'cooked' syntax trees being built via the Context method. 
 	    
 	    @param qName: String 
 	    @param pmmlnode:PmmlNode
  	    @return Unit
	 */
	def dispatch(qName : String, pmmlnode : PmmlNode) : Unit = {

		val generator : PmmlExecNodeGenerator = if (qName != null && pmmlnode != null) {
			if (qName == "SimpleRule") {
				val stop : Boolean = true
			}
		  	val gen : PmmlExecNodeGenerator = pmmlXNodeDispatchMap.getOrElse(qName, null)
			if (gen == null) {
				if (! ignoreList.contains(qName)) {
					PmmlError.logError(ctx, s"unable to obtain generator for node $qName (node type ${pmmlnode.getClass.getName}) ... check dispatch map initialization")
				}
			}
			gen
		} else {
		  	PmmlError.logError(ctx, s"This is a fine howdy-doo! No node supplied to the CodePrinterDispatch")
			null
		}
		
		/** Note: Some nodes will not produce a PmmlExecNode.  Their content is used to update the parent node instead */
		val node : Option[PmmlExecNode] = 
			if (generator != null) {
				generator.make(this, pmmlnode.qName, pmmlnode) 
			} else {
				None
			}
		node match {
			case Some(node) => {		  
				/** update the parent on the stack if appropriate */
				if (! ctx.pmmlExecNodeStack.isEmpty) {				  
					val top : Option[PmmlExecNode] = ctx.pmmlExecNodeStack.top
					top match {
					  	case Some(top) => {
						  	var parent : PmmlExecNode = top.asInstanceOf[PmmlExecNode]
							parent.addChild(node)
					  	}
					  	case _ => logger.error("Fantastic... there are None elements on the stack!")
					}
				}
				ctx.pmmlExecNodeStack.push(Some(node))
				val aNewNode : PmmlExecNode = node
				pmmlnode.Children.foreach((child) => {
					val chNode : PmmlNode = child.asInstanceOf[PmmlNode]
					dispatch(chNode.qName, chNode)
				})
				val completedXformNode : Option[PmmlExecNode] = ctx.pmmlExecNodeStack.pop().asInstanceOf[Option[PmmlExecNode]]
				completedXformNode match {
					case Some(completedXformNode) => {
						val xnode : PmmlExecNode = completedXformNode.asInstanceOf[PmmlExecNode]
						if (ctx.topLevelContainers.contains(xnode.qName)) {
							ctx.pmmlExecNodeMap(completedXformNode.qName) = Some(completedXformNode)
						}
					}
					case _ => {
						 logger.trace(s"node ${pmmlnode.qName} does not have children or had children subsumed by the parent")
					}
				}
			}
			case _ => {
				logger.debug(s"node ${pmmlnode.qName} make did not produce a PmmlExecNode derivative")
			}
		}
	}

	/** In the event it is desirable to ignore certain missing handlers, add the qName here */
	val ignoreList : Set[String] = Set[String]("PMML", "Header", "RuleSet")

	/**
		Answer the global context that contains the syntax tree and other useful state information needed to 
		properly build the syntax tree.
		@return the PmmlContext singleton
	 */
	def context : PmmlContext = ctx


	/** The dispatch map for cooking PmmlNodes to become PmmlExecNodes */
    val  pmmlXNodeDispatchMap = Map[String, PmmlExecNodeGenerator](
	      ("Application" -> new ApplicationPmmlExecNodeGenerator(ctx))
	    , ("Apply" -> new ApplyPmmlExecNodeGenerator(ctx))
	    , ("Array" -> new ArrayPmmlExecNodeGenerator(ctx))
	    , ("CompoundPredicate" -> new CompoundPredicatePmmlExecNodeGenerator(ctx))
	    , ("Constant" -> new ConstantPmmlExecNodeGenerator(ctx))
	    , ("DataDictionary" -> new DataDictionaryPmmlExecNodeGenerator(ctx))
	    , ("DataField" -> new DataFieldPmmlExecNodeGenerator(ctx))
	    , ("DefineFunction" -> new DefineFunctionPmmlExecNodeGenerator(ctx))
	    , ("DerivedField" -> new DerivedFieldPmmlExecNodeGenerator(ctx))
	    , ("FieldColumn" -> new FieldColumnPairPmmlExecNodeGenerator(ctx))
	    , ("FieldRef" -> new FieldRefPmmlExecNodeGenerator(ctx))
	    , ("Header" -> new HeaderPmmlExecNodeGenerator(ctx))
	    , ("InlineTable" -> new InlineTablePmmlExecNodeGenerator(ctx))
	    , ("Interval" -> new IntervalPmmlExecNodeGenerator(ctx))
	    , ("MapValues" -> new MapValuesPmmlExecNodeGenerator(ctx))
	    , ("MiningField" -> new MiningFieldPmmlExecNodeGenerator(ctx))
	    , ("MiningSchema" -> new MiningSchemaPmmlExecNodeGenerator(ctx))
	    , ("ParameterField" -> new ParameterFieldPmmlExecNodeGenerator(ctx))
	    , ("RowColumnPair" -> new RowColumnPairPmmlExecNodeGenerator(ctx))
	    , ("RuleSelectionMethod" -> new RuleSelectionMethodPmmlExecNodeGenerator(ctx))
	    , ("RuleSet", new RuleSetPmmlExecNodeGenerator(ctx))
	    , ("RuleSetModel" -> new RuleSetModelPmmlExecNodeGenerator(ctx))
	    , ("ScoreDistribution" -> new ScoreDistributionPmmlExecNodeGenerator(ctx))
	    , ("SimplePredicate" -> new SimplePredicatePmmlExecNodeGenerator(ctx))
	    , ("SimpleRule" -> new SimpleRulePmmlExecNodeGenerator(ctx))
	    , ("SimpleSetPredicate" -> new SimpleSetPredicatePmmlExecNodeGenerator(ctx))
	    , ("TableLocator" -> new TableLocatorPmmlExecNodeGenerator(ctx))
	    , ("TransformationDictionary" -> new TransformationDictionaryPmmlExecNodeGenerator(ctx))
	    , ("Value" -> new ValuePmmlExecNodeGenerator(ctx)))
	
}
	
