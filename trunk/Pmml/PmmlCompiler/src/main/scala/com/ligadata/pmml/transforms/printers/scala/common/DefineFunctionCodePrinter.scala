package com.ligadata.pmml.compiler

import scala.collection.mutable._
import scala.math._
import scala.collection.immutable.StringLike
import scala.util.control.Breaks._
import com.ligadata.pmml.runtime._
import org.apache.log4j.Logger
import com.ligadata.fatafat.metadata._

class DefineFunctionCodePrinter(ctx : PmmlContext) {

	/**
	 *  Answer a string (code representation) for the supplied node.
	 *  @param node the PmmlExecNode
	 *  @param the CodePrinterDispatch to use should recursion to child nodes be required.
 	    @param the kind of code fragment to generate...any 
 	    	{VARDECL, VALDECL, FUNCCALL, DERIVEDCLASS, RULECLASS, RULESETCLASS , MININGFIELD, MAPVALUE, AGGREGATE, USERFUNCTION}
	 *  @order the traversalOrder to traverse this node...any {INORDER, PREORDER, POSTORDER} 
	 *  
	 *  @return some string representation of this node
	 */
	def print(node : Option[PmmlExecNode]
			, generator : CodePrinterDispatch
			, kind : CodeFragment.Kind
			, traversalOrder : Traversal.Order) : String = {

		val xnode : xDefineFunction = node match {
			case Some(node) => {
				if (node.isInstanceOf[xDefineFunction]) node.asInstanceOf[xDefineFunction] else null
			}
			case _ => null
		}

		val printThis = if (xnode != null) {
			codeGenerator(xnode, generator, kind, traversalOrder)
		} else {
			if (node != null) {
				PmmlError.logError(ctx, s"For ${node.qName}, expecting an xDefineFunction... got a ${node.getClass.getName}... check CodePrinter dispatch map initialization")
			}
			""
		}
		printThis
	}
	

	private def asCode(node : xDefineFunction) : String = {
		node.toString
	}

	private def codeGenerator(node : xDefineFunction
							, generator : CodePrinterDispatch
							, kind : CodeFragment.Kind
							, traversalOrder : Traversal.Order) : String = 	{

		val codeStr : String = order match {
			case Traversal.PREORDER => {
				kind match {
					
				  case CodeFragment.FUNCCALL => {
					  	asCode(node)
				  }
				  case _ => { 
					  PmmlError.logError(ctx, s"fragment kind $kind not supported by xDefineFunction")
				      ""
				  }
				} 
			}
			case _ => {
				PmmlError.logError(ctx, s"xDefineFunction only supports Traversal.PREORDER")
				""
			}
		}
		codeStr
	}
}

