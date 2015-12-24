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

package com.ligadata.MetadataAPI

import java.util.Properties
import java.io._
import scala.Enumeration
import scala.io._

import com.ligadata.kamanja.metadata.ObjType._
import com.ligadata.kamanja.metadata._
import com.ligadata.kamanja.metadata.MdMgr._

import com.ligadata.kamanja.metadataload.MetadataLoad

import scala.util.parsing.json.JSON
import scala.util.parsing.json.{ JSONObject, JSONArray }
import scala.collection.mutable.Map
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer

import com.ligadata.messagedef._
import util.control.Breaks._

import scala.xml.XML
import org.apache.logging.log4j._


class MsgContainers(var contMsgEntity: ContainerDef) {

  val contObject = contMsgEntity

  var parents = new ArrayBuffer[(String, String)] // Immediate parent comes at the end, grand parent last but one, ... Messages/Containers. the format is Message/Container Type name and the variable in that.

  var children = new ArrayBuffer[(String, String)] // Child Messages/Containers (Name & type). We fill this when we create message and populate parent later from this

}

 

object GetDependentMessages {
  val messageContainerObjects = new HashMap[String, MsgContainers]
  private[this] val mdMgr = GetMdMgr

  lazy val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)

  private def GetChildrenFromEntity(entity: EntityType, children: ArrayBuffer[(String, String)]): Unit = {

    if (entity.isInstanceOf[MappedMsgTypeDef]) {

      var attrMap = entity.asInstanceOf[MappedMsgTypeDef].attrMap
      //BUGBUG:: Checking for only one level at this moment
      if (attrMap != null) {
        children ++= attrMap.filter(a => (a._2.isInstanceOf[AttributeDef] && (a._2.asInstanceOf[AttributeDef].aType.isInstanceOf[MappedMsgTypeDef] || a._2.asInstanceOf[AttributeDef].aType.isInstanceOf[StructTypeDef]))).map(a => (a._2.Name, a._2.asInstanceOf[AttributeDef].aType.FullName))
        // If the attribute is an arraybuffer (not yet handling others)
        children ++= attrMap.filter(a => (a._2.isInstanceOf[AttributeDef] && a._2.asInstanceOf[AttributeDef].aType.isInstanceOf[ArrayBufTypeDef] && (a._2.asInstanceOf[AttributeDef].aType.asInstanceOf[ArrayBufTypeDef].elemDef.isInstanceOf[MappedMsgTypeDef] || a._2.asInstanceOf[AttributeDef].aType.asInstanceOf[ArrayBufTypeDef].elemDef.isInstanceOf[StructTypeDef]))).map(a => (a._2.Name, a._2.asInstanceOf[AttributeDef].aType.asInstanceOf[ArrayBufTypeDef].elemDef.FullName))
      }
    } else if (entity.isInstanceOf[StructTypeDef]) {
      var memberDefs = entity.asInstanceOf[StructTypeDef].memberDefs
      //BUGBUG:: Checking for only one level at this moment
      if (memberDefs != null) {
        children ++= memberDefs.filter(a => (a.isInstanceOf[AttributeDef] && (a.asInstanceOf[AttributeDef].aType.isInstanceOf[MappedMsgTypeDef] || a.asInstanceOf[AttributeDef].aType.isInstanceOf[StructTypeDef]))).map(a => (a.Name, a.asInstanceOf[AttributeDef].aType.FullName))
        // If the attribute is an arraybuffer (not yet handling others)
        children ++= memberDefs.filter(a => (a.isInstanceOf[AttributeDef] && a.asInstanceOf[AttributeDef].aType.isInstanceOf[ArrayBufTypeDef] && (a.asInstanceOf[AttributeDef].aType.asInstanceOf[ArrayBufTypeDef].elemDef.isInstanceOf[MappedMsgTypeDef] || a.asInstanceOf[AttributeDef].aType.asInstanceOf[ArrayBufTypeDef].elemDef.isInstanceOf[StructTypeDef]))).map(a => (a.Name, a.asInstanceOf[AttributeDef].aType.asInstanceOf[ArrayBufTypeDef].elemDef.FullName))
      }
    } else {
      // Nothing to do at this moment
    }
  }
  
  def buildMsgHierarchy(contMsgEntity: ContainerDef) {
    val tmpMsgDefs = mdMgr.Messages(true, true)
    val tmpContainerDefs = mdMgr.Containers(true, true)

    val msgDefs = tmpMsgDefs.get
    msgDefs.foreach(msg => {
      val msgName = msg.FullName.toLowerCase
      val mgsObj = new MsgContainers(msg)
      GetChildrenFromEntity(msg.containerType, mgsObj.children)
      messageContainerObjects(msgName) = mgsObj
    })
    val containerDefs = tmpContainerDefs.get
    containerDefs.foreach(container => {
      val contName = container.FullName.toLowerCase
      val contObj = new MsgContainers(container)
      GetChildrenFromEntity(container.containerType, contObj.children)
      messageContainerObjects(contName) = contObj

    })
    // Prepare Parents for each message now
    val childToParentMap = scala.collection.mutable.Map[String, (String, String)]() // ChildType, (ParentType, ChildAttrName)
    // 1. First prepare one level of parents
    messageContainerObjects.foreach(m => {
      m._2.children.foreach(c => {
	// Checking whether we already have in childToParentMap or not before we replace. So that way we can check same child under multiple parents.
	val childMsgNm = c._2.toLowerCase
	val fnd = childToParentMap.getOrElse(childMsgNm, null)
	if (fnd != null) {
	  logger.error(s"$childMsgNm is used as child under $c and $fnd._1. First detected $fnd._1, so using as child of $fnd._1 as it is.")
	} else {
	  childToParentMap(childMsgNm) = (m._1.toLowerCase, c._1)
	}
      })
    })
    // 2. Now prepare Full Parent Hierarchy
    messageContainerObjects.foreach(m => {
      var curParent = childToParentMap.getOrElse(m._1.toLowerCase, null)
      while (curParent != null) {
	m._2.parents += curParent
	curParent = childToParentMap.getOrElse(curParent._1.toLowerCase, null)
      }
    })
    // 3. Order Parent Hierarchy properly

    messageContainerObjects.foreach(m => {
      m._2.parents.reverse
    })
  }

  def getDependentObjects(cont: ContainerDef): Array[String] = {
    var depObjects = new Array[String](0)
    buildMsgHierarchy(cont)
    
    logger.debug("Built " + messageContainerObjects.size + " objects ")
    
    val contName = cont.FullName.toLowerCase

    val containers = messageContainerObjects.getOrElse(contName,null)
    if( containers != null ){
      containers.parents.foreach(p => {
	depObjects = depObjects :+ p._1
      })
    }
    logger.debug("Found " + depObjects.length + " dependant objects ")
    depObjects
  }
}
