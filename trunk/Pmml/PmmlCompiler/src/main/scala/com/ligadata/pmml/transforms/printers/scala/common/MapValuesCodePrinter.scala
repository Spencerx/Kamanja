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

import scala.collection.mutable._
import scala.math._
import scala.collection.immutable.StringLike
import scala.util.control.Breaks._
import com.ligadata.pmml.runtime._
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.kamanja.metadata._
import com.ligadata.pmml.compiler._
import com.ligadata.pmml.support._
import com.ligadata.pmml.traits._
import com.ligadata.pmml.syntaxtree.cooked.common._

class MapValuesCodePrinter(ctx : PmmlContext) extends CodePrinter with com.ligadata.pmml.compiler.LogTrait {

	/**
	 *  Answer a string (code representation) for the supplied node.  The MapValuesCodePrinter doesn't have a node type, simply
	 *	redirects printing to the appropriate flavor of MapValues node... any {xMapValuesMap, xMapValuesMapInline, xMapValuesMapExternal,
	 *	xMapValuesArray, xMapValuesArrayExternal, xMapValuesArrayInline}
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

		val xnode : PmmlExecNode = node match {
			case Some(node) => {
				node match {
					case mvmi : xMapValuesMapInline =>  node
					case mvme : xMapValuesMapExternal =>  node
					case mvm : xMapValuesMap =>  node
					case mvai : xMapValuesArrayExternal =>  node
					case mvae : xMapValuesArrayInline =>  node
					case mva : xMapValuesArray =>  node
					case _ =>  null
				}
			}
			case _ => null
		}

		val printThis = if (xnode != null) {
			codeGenerator(xnode, generator, kind, traversalOrder)
		} else {
			if (node != null) {
				PmmlError.logError(ctx, s"For ${xnode.qName}, expecting an xMapValues family node... got a ${xnode.getClass.getName}... check CodePrinter dispatch map initialization")
			}
			""
		}
		printThis
	}
	

	private def codeGenerator(node : PmmlExecNode
							, generator : CodePrinterDispatch
							, kind : CodeFragment.Kind
							, traversalOrder : Traversal.Order) : String = 	{

		val buffer : StringBuilder = new StringBuilder	
		val mapVM : String = traversalOrder match {
			case Traversal.INORDER => { "" }
			case Traversal.POSTORDER => { "" }
			case Traversal.PREORDER => {
				node match {
					case mvmi : xMapValuesMapInline =>  generator.generate(Some(node), buffer, kind)
					case mvme : xMapValuesMapExternal =>  generator.generate(Some(node), buffer, kind)
					case mvm : xMapValuesMap =>  generator.generate(Some(node), buffer, kind)
					case mvai : xMapValuesArrayExternal =>  generator.generate(Some(node), buffer, kind)
					case mvae : xMapValuesArrayInline =>  generator.generate(Some(node), buffer, kind)
					case mva : xMapValuesArray =>  generator.generate(Some(node), buffer, kind)
					case _ =>  null
				}

			}
			buffer.toString
		}
		mapVM
	}
}

