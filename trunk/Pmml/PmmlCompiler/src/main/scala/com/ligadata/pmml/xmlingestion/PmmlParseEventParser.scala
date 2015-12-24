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

package com.ligadata.pmml.xmlingestion

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.Locator
import scala.collection._
import scala.collection.mutable.Stack
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.pmml.compiler._
import com.ligadata.pmml.support._
import com.ligadata.pmml.traits._
import com.ligadata.pmml.syntaxtree.raw.common._


class PmmlParseEventParser(ctx : PmmlContext, dispatcher : PmmlNodeGeneratorDispatch)  extends DefaultHandler with com.ligadata.pmml.compiler.LogTrait {
	var start = false
	var node: PmmlNode = _
	val buffer:StringBuilder = new StringBuilder()
	var _locator : Locator = null
	
	override def setDocumentLocator(locator : Locator) {
		_locator = locator
	}
	//  dispatch(namespaceURI: String, localName: String , qName:String , atts: Attributes, lineNumber : Int, columnNumber : Int)
	override def startElement(namespaceURI: String, localName: String , qName:String , atts: Attributes ) = {
		buffer.setLength(0);

		logger.trace(s"...encountered $qName")
		/** dispatch the element visitor that will build the appropriate kind of PmmlNode for it.and push to stack 
		 	The dispatcher pushes it on the stack to be decorated by its children. */
		dispatcher.dispatch(namespaceURI, localName, qName, atts, _locator.getLineNumber(), _locator.getColumnNumber())
	}

	override def endElement( uri: String, localName: String, qName: String) = {

  		var pmmlnode : PmmlNode = if (ctx.pmmlNodeStack.isEmpty) null else ctx.pmmlNodeStack.top
  		
  		if (pmmlnode == null) {
  			logger.debug(s"endElement... pmmlNodeStack is empty ... qName = $qName")
  		} else {

			val buf : String = buffer.toString()
			val len = buf.length()
			if (buf.length() >= 0) {
				logger.debug(s"the following string was collected for $qName (length = $len) : $buf")
				
				pmmlnode.qName match {
				  case "Constant" => pmmlnode.asInstanceOf[PmmlConstant].Value(buf)
				  case "Array" => pmmlnode.asInstanceOf[PmmlArray].SetArrayValue(buf)
				  case _ => None
				}
				
			}
			
			if (ctx.topLevelContainers.contains(qName)) {
				ctx.pmmlNodeQueue.enqueue(pmmlnode)
			}
			
			ctx.pmmlNodeStack.pop
	  	}
		
		buffer.setLength(0);
	}

	override def characters(ch: Array[Char],start: Int , length: Int) = {
		buffer.append(new String(ch, start, length))
	}

}  