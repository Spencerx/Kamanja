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

package com.ligadata.Serialize

import java.util.Properties
import java.io._
import scala.Enumeration
import scala.io.Source._
import org.apache.logging.log4j._

import scala.collection.JavaConversions._

import com.ligadata.kamanja.metadata._

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.ExtensionRegistry;

import com.ligadata.Serialize.MetadataObjects._
import com.ligadata.Serialize.MetadataObjects.MetadataType._
import java.io._
import com.ligadata.Exceptions._
import com.ligadata.Exceptions.StackTrace

class ProtoBufSerializer extends Serializer{

  val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)
  private[this] var classLoader: java.lang.ClassLoader = null

  def buildProtoBaseElem(o: BaseElemDef) : ProtoBaseElem = {
    logger.debug("Build ProtoBaseElem from " + o.getClass().getName())
    val pbe = ProtoBaseElem.newBuilder()
      .setNameSpace(o.NameSpace)
      .setName(o.Name)
      .setVer(o.Version)
    if( o.PhysicalName != null ){
      pbe.setPhysicalName(o.PhysicalName)
    }
    if( o.JarName != null ){
      pbe.setJarName(o.JarName)
    }
    if( o.DependencyJarNames.length > 0 ){
      o.DependencyJarNames.toList.map(obj => {pbe.addDependencyJarNames(obj)})
    }
    pbe.build
  }

  def buildProtoBaseType(o: BaseTypeDef) : ProtoBaseType = {
    logger.debug("Build ProtoBaseType from " + o.getClass().getName())
    val pbt = ProtoBaseType.newBuilder()
      .setPbe(buildProtoBaseElem(o))
   //   .setTypeType(ObjTypeType.asString(o.tTypeType))
   //   .setType(ObjType.asString(o.tType))

     if(o.implementationName != null ){
      pbt.setImplementationName(o.implementationName)
     }
     pbt.build()
  }

  def buildProtoBaseTypeKey(o: BaseTypeDef) : ProtoBaseTypeKey = {
    logger.debug("Build ProtoBaseTypekey from " + o.getClass().getName())
    val pbtk = ProtoBaseTypeKey.newBuilder()
      .setNameSpace(o.NameSpace)
      .setName(o.Name)
      .setVer(o.Version)
     pbtk.build()
  }

  def buildAttribute(o: AttributeDef) : Attribute = {
    val a = 
      Attribute.newBuilder()
      .setPbe(buildProtoBaseElem(o))
      .setPbt(buildProtoBaseTypeKey(o.aType))

    if(o.collectionType != ObjType.tNone ){
      a.setCollectionType(ObjType.asString(o.collectionType))
    }
    a.build()
  }

  def buildModel(o: ModelDef) : Model = {
    val m = Model.newBuilder()
    
    m.setPbe(buildProtoBaseElem(o))
    m.setModelType("RuleSet")
    o.inputVars.toList.map(obj => {m.addInputVars(buildAttribute(obj.asInstanceOf[AttributeDef]))})
    o.outputVars.toList.map(obj => {m.addOutputVars(buildAttribute(obj.asInstanceOf[AttributeDef]))})
    m.build()
  }


  def buildModel(o: Model) : ModelDef = {

    try{
      val inputAttrList = o.getInputVarsList()

      logger.debug("Prepping InputVarList...")

      var inputAttrList1 = List[(String, String, String,String,Boolean,String)]()
      for (attr <- inputAttrList) {
	var collType:String = null;
	if( attr.getCollectionType() != null ){
	  collType = attr.getCollectionType();
	}
	inputAttrList1 ::= (attr.getPbe().getNameSpace(),
			    attr.getPbe().getName(),
			    attr.getPbt().getNameSpace(),
			    attr.getPbt().getName(),
			    false, 
			    collType)
      }

      logger.debug("Prepping OutputVarList...")

      val outputAttrList = o.getOutputVarsList()
      var outputAttrList1 = List[(String, String, String)]()
      for (attr <- outputAttrList) {
	outputAttrList1 ::= (attr.getPbe().getName(),
			     attr.getPbt().getNameSpace(),
			     attr.getPbt().getName())
      }


      logger.debug("Prepping DependencyJars...")
      // Couldn't use toArray on List[java.lang.String], need to make it better
      val depJarsList = o.getPbe().getDependencyJarNamesList()
      val depJars1 = new Array[String](depJarsList.length)
      var i:Int = 0
      for( str <- depJarsList){
	depJars1(i) = str
	i += 1
      }

      //logger.debug("depJars contain " + depJars1.length + " entries ")
      //depJars1.foreach(s => { logger.debug(s) })

      logger.debug("Create the ModelDef object...")
      val m = MdMgr.GetMdMgr.MakeModelDef(o.getPbe().getNameSpace(),
					  o.getPbe().getName(),
					  o.getPbe().getPhysicalName(),
					  o.getModelType(),
					  inputAttrList1,
					  outputAttrList1,
					  o.getPbe().getVer(),
					  o.getPbe().getJarName(),
					  depJars1)
      
      m
    }catch{
      case e:Exception => {
	val stackTrace = StackTrace.ThrowableTraceString(e)
  logger.debug("StackTrace:"+stackTrace)
	throw new ProtoBufSerializationException("Failed to Deserialize the object: " + e.getMessage())
      }
    }
  }


  override def SerializeObjectToByteArray(obj : Object) : Array[Byte] = {
    try{
      obj match {
	case o:AttributeDef => {
	  val ae = buildAttribute(o)
	  val ba = ae.toByteArray();
	  logger.debug("Serialized data contains " + ba.length + " bytes ")
	  ba
	}
	case o:ModelDef => {
	  val m = buildModel(o)
	  val ba = m.toByteArray();
	  logger.debug("Serialized data contains " + ba.length + " bytes ")
	  ba
	}
      }
    }catch{
      case e:Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
	throw new ProtoBufSerializationException("Failed to Serialize the object(" + obj.getClass().getName() + "): " + e.getMessage())
      }
    }
  }


  override def DeserializeObjectFromByteArray(ba: Array[Byte], objectType:String) : Object = {
    logger.debug("Parse " + ba.length + " bytes to create a " + objectType + " object ")
    try{
      objectType match{
	case "com.ligadata.kamanja.metadata.AttributeDef" => {
	  val a = Attribute.parseFrom(ba);
	  logger.debug("Attribute => " + a)
	  a
	}
	case "com.ligadata.kamanja.metadata.ModelDef" => {
	  val m = Model.parseFrom(ba);
	  logger.debug("Deserialized Model as protobuf object => " + m)
	  val mDef = buildModel(m)
	  mDef
	}
        case _ => {
	  throw new ProtoBufSerializationException("Failed to DeSerialize the object of type: " + objectType)
	}
      }
    }catch{
      case e:Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
	throw new ProtoBufSerializationException("Failed to DeSerialize the object:" + e.getMessage())
      }
    }
  }


  override def DeserializeObjectFromByteArray(ba: Array[Byte]) : Object = {
    throw new ProtoBufSerializationException("Failed to DeSerialize the object: Unable to deserialize the object without ObjectType")
  }

  override def SetClassLoader(cl : java.lang.ClassLoader): Unit = {
    classLoader = cl
  }

}
