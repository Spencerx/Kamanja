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

class HeaderCodePrinter(ctx : PmmlContext) extends CodePrinter with com.ligadata.pmml.compiler.LogTrait {

	/**
	 *  Answer a string (code representation) for the supplied node.
	 *  @param node the PmmlExecNode
	 *  @param the CodePrinterDispatch to use should recursion to child nodes be required.
 	    @param the kind of code fragment to generate...any 
 	    	{VARDECL, VALDECL, FUNCCALL, DERIVEDCLASS, RULECLASS, RULESETCLASS , MININGFIELD, MAPVALUE, AGGREGATE, USERFUNCTION}
	 *  @order the order to traverse this node...any {INORDER, PREORDER, POSTORDER} 
	 *  
	 *  @return some string representation of this node
	 */
	def print(node : Option[PmmlExecNode]
			, generator : CodePrinterDispatch
			, kind : CodeFragment.Kind
			, order : Traversal.Order) : String = {

		val xnode : xHeader = node match {
			case Some(node) => {
				if (node.isInstanceOf[xHeader]) node.asInstanceOf[xHeader] else null
			}
			case _ => null
		}

		val printThis = if (xnode != null) {
			asString(ctx, xnode)
		} else {
			if (node != null) {
				PmmlError.logError(ctx, s"For ${xnode.qName}, expecting an xHeader... got a ${xnode.getClass.getName}... check CodePrinter dispatch map initialization")
			}
			""
		}
		printThis
	}
	

	private def asString(ctx : PmmlContext, xnode : xHeader) : String =  {
			val header1 = s"/** Copyright : ${xnode.copyright} Application : ${xnode.applicationName} Version : ${xnode.version}\n"
			val header2 = s"    Description : ${xnode.description}\n"
			val header = s"$header1\n$header2 */"
			header
	}
	
}