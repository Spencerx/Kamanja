package com.ligadata.pmml.compiler

import scala.collection.mutable._
import scala.math._
import scala.collection.immutable.StringLike
import scala.util.control.Breaks._
import com.ligadata.pmml.runtime._
import org.apache.log4j.Logger
import com.ligadata.fatafat.metadata._

class RuleSetModelCodePrinter(ctx : PmmlContext) {

	/**
	 *  Answer a string (code representation) for the supplied node.
	 *  @param node the PmmlExecNode
	 *  @param the CodePrinterDispatch to use should recursion to child nodes be required.
 	 *  @param the kind of code fragment to generate...any 
 	 *   	{VARDECL, VALDECL, FUNCCALL, DERIVEDCLASS, RULECLASS, RULESETCLASS , MININGFIELD, MAPVALUE, AGGREGATE, USERFUNCTION}
	 *  @order the traversalOrder to traverse this node...any {INORDER, PREORDER, POSTORDER} 
	 *  
	 *  @return some string representation of this node
	 */
	def print(node : Option[PmmlExecNode]
			, generator : CodePrinterDispatch
			, kind : CodeFragment.Kind
			, traversalOrder : Traversal.Order) : String = {

		val xnode : xRuleSetModel = node match {
			case Some(node) => {
				if (node.isInstanceOf[xRuleSetModel]) node.asInstanceOf[xRuleSetModel] else null
			}
			case _ => null
		}

		val printThis = if (xnode != null) {
			codeGenerator(xnode, generator, kind, traversalOrder)
		} else {
			if (node != null) {
				PmmlError.logError(ctx, s"For ${node.qName}, expecting an xRuleSetModel... got a ${node.getClass.getName}... check CodePrinter dispatch map initialization")
			}
			""
		}
		printThis
	}
	

	private def codeGenerator(node : xRuleSetModel
							, generator : CodePrinterDispatch
							, kind : CodeFragment.Kind
							, traversalOrder : Traversal.Order) : String = 	{

		val rsm : String = order match {
			case Traversal.INORDER => { "" }
			case Traversal.POSTORDER => { "" }
			case Traversal.PREORDER => {
				kind match {
					case CodeFragment.RULESETCLASS => {
						NodePrinterHelpers.ruleSetModelHelper(this, ctx, generator, kind, order)						
					}
					case CodeFragment.RULECLASS | CodeFragment.MININGFIELD => {  /** continue diving for the RULECLASS and MININGFIELD generators */
						val clsBuffer : StringBuilder = new StringBuilder()
						Children.foreach((child) => {
							generator.generate(Some(child), clsBuffer, kind)
						})
					   	clsBuffer.toString
					}
					case _ => { 
						val kindStr : String = kind.toString
						PmmlError.logError(ctx, s"RuleSetModel node - unsupported CodeFragment.Kind - $kindStr") 
						""
					}
				}
			}
		}
		rsm
	}

}
