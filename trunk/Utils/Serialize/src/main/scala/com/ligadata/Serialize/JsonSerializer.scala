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

import com.ligadata.kamanja.metadata._
import com.ligadata.kamanja.metadata.ObjType._
import com.ligadata.kamanja.metadata.MdMgr._
import scala.collection.mutable.{ ArrayBuffer }
import org.apache.logging.log4j._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization

import com.ligadata.Exceptions._
import com.ligadata.AuditAdapterInfo.AuditRecord

import java.util.Date
import com.ligadata.Exceptions.StackTrace

case class TypeDef(MetadataType: String, NameSpace: String, Name: String, TypeTypeName: String, TypeNameSpace: String, TypeName: String, PhysicalName: String, var Version: String, JarName: String, DependencyJars: List[String], Implementation: String, Fixed: Option[Boolean], NumberOfDimensions: Option[Int], KeyTypeNameSpace: Option[String], KeyTypeName: Option[String], ValueTypeNameSpace: Option[String], ValueTypeName: Option[String], TupleDefinitions: Option[List[TypeDef]])
case class TypeDefList(Types: List[TypeDef])

case class Argument(ArgName: String, ArgTypeNameSpace: String, ArgTypeName: String)
case class Function(NameSpace: String, Name: String, PhysicalName: String, ReturnTypeNameSpace: String, ReturnTypeName: String, Arguments: List[Argument], Features: List[String], Version: String, JarName: String, DependantJars: List[String])
case class FunctionList(Functions: List[Function])

//case class Concept(NameSpace: String,Name: String, TypeNameSpace: String, TypeName: String,Version: String,Description: String, Author: String, ActiveDate: String)
case class Concept(NameSpace: String, Name: String, TypeNameSpace: String, TypeName: String, Version: String)
case class ConceptList(Concepts: List[Concept])

case class Attr(NameSpace: String, Name: String, Version: Long, CollectionType: Option[String], Type: TypeDef)
case class DerivedConcept(FunctionDefinition: Function, Attributes: List[Attr])

case class MessageStruct(NameSpace: String, Name: String, FullName: String, Version: Long, JarName: String, PhysicalName: String, DependencyJars: List[String], Attributes: List[Attr])
case class MessageDefinition(Message: MessageStruct)
case class ContainerDefinition(Container: MessageStruct)

case class ModelInfo(NameSpace: String, Name: String, Version: String, ModelType: String, JarName: String, PhysicalName: String, DependencyJars: List[String], InputAttributes: List[Attr], OutputAttributes: List[Attr])
case class ModelDefinition(Model: ModelInfo)

case class ParameterMap(RootDir: String, GitRootDir: String, Database: String, DatabaseHost: String, JarTargetDir: String, ScalaHome: String, JavaHome: String, ManifestPath: String, ClassPath: String, NotifyEngine: String, ZooKeeperConnectString: String)
case class MetadataApiConfig(ApiConfigParameters: ParameterMap)

case class ZooKeeperNotification(ObjectType: String, Operation: String, NameSpace: String, Name: String, Version: String, PhysicalName: String, JarName: String, DependantJars: List[String], ConfigContnent: Option[String])
case class ZooKeeperTransaction(Notifications: List[ZooKeeperNotification], transactionId: Option[String])

case class JDataStore(StoreType: String, SchemaName: String, Location: String, AdapterSpecificConfig: Option[String])
case class JZKInfo(ZooKeeperNodeBasePath: String, ZooKeeperConnectString: String, ZooKeeperSessionTimeoutMs: Option[String], ZooKeeperConnectionTimeoutMs: Option[String])
// case class JNodeInfo(NodeId: String, NodePort: Int, NodeIpAddr: String, JarPaths: List[String], Scala_home: String, Java_home: String, Classpath: String, Roles: Option[List[String]])
// case class JClusterCfg(DataStore: String, StatusInfo: String, ZooKeeperInfo: String, EnvironmentContext: String)
// case class JClusterInfo(ClusterId: String, Config: Option[JClusterCfg], Nodes: List[JNodeInfo])
// case class JAdapterInfo(Name: String, TypeString: String, DataFormat: Option[String], InputAdapterToVerify: Option[String], ClassName: String, JarName: String, DependencyJars: Option[List[String]], AdapterSpecificCfg: Option[String], DelimiterString: Option[String], AssociatedMessage: Option[String])
// case class EngineConfig(Clusters: Option[List[JClusterInfo]])
case class JEnvCtxtJsonStr(classname: String, jarname: String, dependencyjars: Option[List[String]])
case class MetadataApiArg(ObjectType: String, NameSpace: String, Name: String, Version: String, FormatType: String)
case class MetadataApiArgList(ArgList: List[MetadataApiArg])

// The implementation class
object JsonSerializer {

  val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)

  @throws(classOf[Json4sParsingException])
  @throws(classOf[FunctionListParsingException])
  def parseFunctionList(funcListJson: String, formatType: String): Array[FunctionDef] = {
    try {
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(funcListJson)

      logger.debug("Parsed the json : " + funcListJson)
      val funcList = json.extract[FunctionList]
      var funcDefList: ArrayBuffer[FunctionDef] = ArrayBuffer[FunctionDef]()

      funcList.Functions.map(fn => {
        try {
          val argList = fn.Arguments.map(arg => (arg.ArgName, arg.ArgTypeNameSpace, arg.ArgTypeName))
          var featureSet: scala.collection.mutable.Set[FcnMacroAttr.Feature] = scala.collection.mutable.Set[FcnMacroAttr.Feature]()
          if (fn.Features != null) {
            fn.Features.foreach(arg => featureSet += FcnMacroAttr.fromString(arg))
          }
          val func = MdMgr.GetMdMgr.MakeFunc(fn.NameSpace, fn.Name, fn.PhysicalName,
            (fn.ReturnTypeNameSpace, fn.ReturnTypeName),
            argList, featureSet,
            fn.Version.toLong,
            fn.JarName,
            fn.DependantJars.toArray)
          funcDefList += func
        } catch {
          case e: AlreadyExistsException => {
            val funcDef = List(fn.NameSpace, fn.Name, fn.Version)
            val funcName = funcDef.mkString(",")
            logger.error("Failed to add the func: " + funcName + ": " + e.getMessage())
          }
        }
      })
      funcDefList.toArray
    } catch {
      case e: MappingException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:" + stackTrace)
        throw Json4sParsingException(e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("\nStackTrace:" + stackTrace)
        throw new FunctionListParsingException(e.getMessage())
      }
    }
  }

  def processTypeDef(typ: TypeDef): BaseTypeDef = {
    var typeDef: BaseTypeDef = null
    try {
      typ.MetadataType match {
        case "ScalarTypeDef" => {
          typeDef = MdMgr.GetMdMgr.MakeScalar(typ.NameSpace, typ.Name, ObjType.fromString(typ.TypeName),
            typ.PhysicalName, typ.Version.toLong, typ.JarName,
            typ.DependencyJars.toArray, typ.Implementation)
        }
        case "ArrayTypeDef" => {
          typeDef = MdMgr.GetMdMgr.MakeArray(typ.NameSpace, typ.Name, typ.TypeNameSpace,
            typ.TypeName, typ.NumberOfDimensions.get,
            typ.Version.toLong)
        }
        case "ArrayBufTypeDef" => {
          typeDef = MdMgr.GetMdMgr.MakeArrayBuffer(typ.NameSpace, typ.Name, typ.TypeNameSpace,
            typ.TypeName, typ.NumberOfDimensions.get,
            typ.Version.toLong)
        }
        case "ListTypeDef" => {
          typeDef = MdMgr.GetMdMgr.MakeList(typ.NameSpace, typ.Name, typ.TypeNameSpace,
            typ.TypeName, typ.Version.toLong)
        }
        case "QueueTypeDef" => {
          typeDef = MdMgr.GetMdMgr.MakeQueue(typ.NameSpace, typ.Name, typ.TypeNameSpace,
            typ.TypeName, typ.Version.toLong)
        }
        case "SetTypeDef" => {
          typeDef = MdMgr.GetMdMgr.MakeSet(typ.NameSpace, typ.Name, typ.TypeNameSpace,
            typ.TypeName, typ.Version.toLong)
        }
        case "ImmutableSetTypeDef" => {
          typeDef = MdMgr.GetMdMgr.MakeImmutableSet(typ.NameSpace, typ.Name, typ.TypeNameSpace,
            typ.TypeName, typ.Version.toLong)
        }
        case "TreeSetTypeDef" => {
          typeDef = MdMgr.GetMdMgr.MakeTreeSet(typ.NameSpace, typ.Name, typ.TypeNameSpace,
            typ.TypeName, typ.Version.toLong)
        }
        case "SortedSetTypeDef" => {
          typeDef = MdMgr.GetMdMgr.MakeSortedSet(typ.NameSpace, typ.Name, typ.TypeNameSpace,
            typ.TypeName, typ.Version.toLong)
        }
        case "MapTypeDef" => {
          val mapKeyType = (typ.KeyTypeNameSpace.get, typ.KeyTypeName.get)
          val mapValueType = (typ.ValueTypeNameSpace.get, typ.ValueTypeName.get)
          typeDef = MdMgr.GetMdMgr.MakeMap(typ.NameSpace, typ.Name, mapKeyType, mapValueType, typ.Version.toLong)
        }
        case "ImmutableMapTypeDef" => {
          val mapKeyType = (typ.KeyTypeNameSpace.get, typ.KeyTypeName.get)
          val mapValueType = (typ.ValueTypeNameSpace.get, typ.ValueTypeName.get)
          typeDef = MdMgr.GetMdMgr.MakeImmutableMap(typ.NameSpace, typ.Name, mapKeyType, mapValueType, typ.Version.toLong)
        }
        case "HashMapTypeDef" => {
          val mapKeyType = (typ.KeyTypeNameSpace.get, typ.KeyTypeName.get)
          val mapValueType = (typ.ValueTypeNameSpace.get, typ.ValueTypeName.get)
          typeDef = MdMgr.GetMdMgr.MakeHashMap(typ.NameSpace, typ.Name, mapKeyType, mapValueType, typ.Version.toLong)
        }
        case "TupleTypeDef" => {
          val tuples = typ.TupleDefinitions.get.map(arg => (arg.NameSpace, arg.Name)).toArray
          typeDef = MdMgr.GetMdMgr.MakeTupleType(typ.NameSpace, typ.Name, tuples, typ.Version.toLong)
        }
        case "ContainerTypeDef" => {
          if (typ.TypeName == "Struct") {
            typeDef = MdMgr.GetMdMgr.MakeStructDef(typ.NameSpace, typ.Name, typ.PhysicalName,
              null, typ.Version.toLong, typ.JarName,
              typ.DependencyJars.toArray, null, null, null) //BUGBUG:: Handle Primary Key, Foreign Keys & Partition Key here
          }
        }
        case _ => {
          throw new TypeDefProcessingException("Internal Error: Unknown Type " + typ.MetadataType)
        }
      }
      typeDef

    } catch {
      case e: AlreadyExistsException => {
        val keyValues = List(typ.NameSpace, typ.Name, typ.Version)
        val typeName = keyValues.mkString(",")
        logger.error("Failed to add the type: " + typeName + ": " + e.getMessage())
        throw new AlreadyExistsException(e.getMessage())
      }
      case e: Exception => {
        val keyValues = List(typ.NameSpace, typ.Name, typ.Version)
        val typeName = keyValues.mkString(",")
        logger.error("Failed to add the type: " + typeName + ": " + e.getMessage())
        throw new TypeDefProcessingException(e.getMessage())
      }
    }
  }

  @throws(classOf[Json4sParsingException])
  @throws(classOf[TypeDefListParsingException])
  def parseTypeList(typeListJson: String, formatType: String): Array[BaseTypeDef] = {
    try {
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(typeListJson)

      logger.debug("Parsed the json : " + typeListJson)
      val typeList = json.extract[TypeDefList]

      logger.debug("Type count  => " + typeList.Types.length)
      var typeDefList: ArrayBuffer[BaseTypeDef] = ArrayBuffer[BaseTypeDef]()

      typeList.Types.map(typ => {

        try {
          val typeDefObj: BaseTypeDef = processTypeDef(typ)
          typeDefList += typeDefObj
        } catch {
          case e: AlreadyExistsException => {
            val keyValues = List(typ.NameSpace, typ.Name, typ.Version)
            val typeName = keyValues.mkString(",")
            logger.error("Failed to add the type: " + typeName + ": " + e.getMessage())
          }
          case e: TypeDefProcessingException => {
            val keyValues = List(typ.NameSpace, typ.Name, typ.Version)
            val typeName = keyValues.mkString(",")
            logger.error("Failed to add the type: " + typeName + ": " + e.getMessage())
          }
        }
      })
      typeDefList.toArray
    } catch {
      case e: MappingException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:" + stackTrace)
        throw Json4sParsingException(e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:" + stackTrace)
        throw new TypeDefListParsingException(e.getMessage())
      }
    }
  }

  @throws(classOf[Json4sParsingException])
  @throws(classOf[ConceptListParsingException])
  def parseConceptList(conceptsStr: String, formatType: String): Array[BaseAttributeDef] = {
    try {
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(conceptsStr)
      val conceptList = json.extract[ConceptList]

      //logger.debug("Parsed the json str " + conceptsStr)
      val attrDefList = new Array[BaseAttributeDef](conceptList.Concepts.length)
      var i = 0;
      conceptList.Concepts.map(o => {
        try {
          //logger.debug("Create Concept for " + o.NameSpace + "." + o.Name)
          val attr = MdMgr.GetMdMgr.MakeConcept(o.NameSpace,
            o.Name,
            o.TypeNameSpace,
            o.TypeName,
            o.Version.toLong,
            false)
          logger.debug("Created AttributeDef for " + o.NameSpace + "." + o.Name)
          attrDefList(i) = attr
          i = i + 1
        } catch {
          case e: AlreadyExistsException => {
            val keyValues = List(o.NameSpace, o.Name, o.Version)
            val fullName = keyValues.mkString(",")
            logger.error("Failed to add the Concept: " + fullName + ": " + e.getMessage())
          }
        }
      })
      //logger.debug("Found " + attrDefList.length + " concepts ")
      attrDefList
    } catch {
      case e: MappingException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:" + stackTrace)
        throw Json4sParsingException(e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:" + stackTrace)
        throw new ConceptListParsingException(e.getMessage())
      }
    }
  }

  @throws(classOf[Json4sParsingException])
  @throws(classOf[ZkTransactionParsingException])
  def parseZkTransaction(zkTransactionJson: String, formatType: String): ZooKeeperTransaction = {
    try {
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(zkTransactionJson)

      logger.debug("Parsed the json : " + zkTransactionJson)

      val zkTransaction = json.extract[ZooKeeperTransaction]

      logger.debug("Serialized ZKTransaction => " + zkSerializeObjectToJson(zkTransaction))

      zkTransaction
    } catch {
      case e: MappingException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        throw Json4sParsingException(e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        throw new ZkTransactionParsingException(e.getMessage())
      }
    }
  }

  @throws(classOf[Json4sParsingException])
  @throws(classOf[ConceptListParsingException])
  def parseDerivedConcept(conceptsStr: String, formatType: String) = {
    try {
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(conceptsStr)
      val concept = json.extract[DerivedConcept]
      val attrList = concept.Attributes.map(attr => (attr.NameSpace, attr.Name, attr.Type.TypeNameSpace, attr.Type.TypeName, false, attr.CollectionType.get))
      val argList = concept.FunctionDefinition.Arguments.map(arg => (arg.ArgName, arg.ArgTypeNameSpace, arg.ArgTypeName))
      var featureSet: scala.collection.mutable.Set[FcnMacroAttr.Feature] = scala.collection.mutable.Set[FcnMacroAttr.Feature]()
      concept.FunctionDefinition.Features.foreach(arg => featureSet += FcnMacroAttr.fromString(arg))

      val func = MdMgr.GetMdMgr.MakeFunc(concept.FunctionDefinition.NameSpace,
        concept.FunctionDefinition.Name,
        concept.FunctionDefinition.PhysicalName,
        (concept.FunctionDefinition.ReturnTypeNameSpace,
          concept.FunctionDefinition.ReturnTypeName),
        argList,
        featureSet,
        concept.FunctionDefinition.Version.toLong,
        concept.FunctionDefinition.JarName,
        concept.FunctionDefinition.DependantJars.toArray)

      //val derivedConcept = MdMgr.GetMdMgr.MakeDerivedAttr(func,attrList)
    } catch {
      case e: AlreadyExistsException => {
        logger.error("Failed to add the DerivedConcept: : " + e.getMessage())

      }
      case e: MappingException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:" + stackTrace)
        throw Json4sParsingException(e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:" + stackTrace)
        throw new ConceptListParsingException(e.getMessage())
      }
    }
  }

  @throws(classOf[Json4sParsingException])
  @throws(classOf[ContainerDefParsingException])
  def parseContainerDef(contDefJson: String, formatType: String): ContainerDef = {
    try {
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(contDefJson)

      logger.debug("Parsed the json : " + contDefJson)

      val ContDefInst = json.extract[ContainerDefinition]
      val attrList = ContDefInst.Container.Attributes.map(attr => (attr.NameSpace, attr.Name, attr.Type.TypeNameSpace, attr.Type.TypeName, false, attr.CollectionType.get))
      val contDef = MdMgr.GetMdMgr.MakeFixedContainer(ContDefInst.Container.NameSpace,
        ContDefInst.Container.Name,
        ContDefInst.Container.PhysicalName,
        attrList.toList,
        ContDefInst.Container.Version.toLong,
        ContDefInst.Container.JarName,
        ContDefInst.Container.DependencyJars.toArray)
      contDef
    } catch {
      case e: MappingException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        throw Json4sParsingException(e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("\nStackTrace:"+stackTrace)
        throw new ContainerDefParsingException(e.getMessage())
      }
    }
  }

  @throws(classOf[Json4sParsingException])
  @throws(classOf[TypeParsingException])
  def parseType(typeJson: String, formatType: String): BaseTypeDef = {
    var typeDef: BaseTypeDef = null
    try {
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(typeJson)
      logger.debug("Parsed the json : " + typeJson)
      val typ = json.extract[TypeDef]
      typeDef = processTypeDef(typ)
      typeDef
    } catch {
      case e: MappingException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:"+stackTrace)
        throw Json4sParsingException(e.getMessage())
      }
      case e: AlreadyExistsException => {
        logger.error("Failed to add the type, json => " + typeJson + "\nError => " + e.getMessage())
        throw new AlreadyExistsException(e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("Stacktrace:"+stackTrace)
        throw new TypeParsingException(e.getMessage())
      }
    }
  }

  @throws(classOf[Json4sParsingException])
  @throws(classOf[ConceptParsingException])
  def parseConcept(conceptJson: String, formatType: String): BaseAttributeDef = {
    try {
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(conceptJson)

      logger.debug("Parsed the json : " + conceptJson)

      val conceptInst = json.extract[Concept]
      val concept = MdMgr.GetMdMgr.MakeConcept(conceptInst.NameSpace,
        conceptInst.Name,
        conceptInst.TypeNameSpace,
        conceptInst.TypeName,
        conceptInst.Version.toLong,
        false)
      concept
    } catch {
      case e: MappingException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw Json4sParsingException(e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw new ConceptParsingException(e.getMessage())
      }
    }
  }

  @throws(classOf[Json4sParsingException])
  @throws(classOf[FunctionParsingException])
  def parseFunction(functionJson: String, formatType: String): FunctionDef = {
    try {
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(functionJson)

      logger.debug("Parsed the json : " + functionJson)

      val functionInst = json.extract[Function]
      val argList = functionInst.Arguments.map(arg => (arg.ArgName, arg.ArgTypeNameSpace, arg.ArgTypeName))
      var featureSet: scala.collection.mutable.Set[FcnMacroAttr.Feature] = scala.collection.mutable.Set[FcnMacroAttr.Feature]()
      if (functionInst.Features != null) {
        functionInst.Features.foreach(arg => featureSet += FcnMacroAttr.fromString(arg))
      }

      val function = MdMgr.GetMdMgr.MakeFunc(functionInst.NameSpace,
        functionInst.Name,
        functionInst.PhysicalName,
        (functionInst.ReturnTypeNameSpace, functionInst.ReturnTypeName),
        argList,
        featureSet,
        functionInst.Version.toLong,
        functionInst.JarName,
        functionInst.DependantJars.toArray)
      function
    } catch {
      case e: MappingException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw Json4sParsingException(e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw new FunctionParsingException(e.getMessage())
      }
    }
  }

  @throws(classOf[Json4sParsingException])
  @throws(classOf[MessageDefParsingException])
  def parseMessageDef(msgDefJson: String, formatType: String): MessageDef = {
    try {
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(msgDefJson)

      logger.debug("Parsed the json : " + msgDefJson)

      val MsgDefInst = json.extract[MessageDefinition]
      val attrList = MsgDefInst.Message.Attributes
      var attrList1 = List[(String, String, String, String, Boolean, String)]()
      for (attr <- attrList) {
        attrList1 ::= (attr.NameSpace, attr.Name, attr.Type.TypeNameSpace, attr.Type.TypeName, false, attr.CollectionType.get)
      }
      val msgDef = MdMgr.GetMdMgr.MakeFixedMsg(MsgDefInst.Message.NameSpace,
        MsgDefInst.Message.Name,
        MsgDefInst.Message.PhysicalName,
        attrList1.toList,
        MsgDefInst.Message.Version.toLong,
        MsgDefInst.Message.JarName,
        MsgDefInst.Message.DependencyJars.toArray)
      msgDef
    } catch {
      case e: MappingException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw Json4sParsingException(e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw new MessageDefParsingException(e.getMessage())
      }
    }
  }

  @throws(classOf[Json4sParsingException])
  @throws(classOf[ModelDefParsingException])
  def parseModelDef(modDefJson: String, formatType: String): ModelDef = {
    try {
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(modDefJson)

      logger.debug("Parsed the json : " + modDefJson)

      val ModDefInst = json.extract[ModelDefinition]

      val inputAttrList = ModDefInst.Model.InputAttributes
      var inputAttrList1 = List[(String, String, String, String, Boolean, String)]()
      for (attr <- inputAttrList) {
        inputAttrList1 ::= (attr.NameSpace, attr.Name, attr.Type.NameSpace, attr.Type.Name, false, attr.CollectionType.get)
      }

      val outputAttrList = ModDefInst.Model.OutputAttributes
      var outputAttrList1 = List[(String, String, String)]()
      for (attr <- outputAttrList) {
        outputAttrList1 ::= (attr.Name, attr.Type.NameSpace, attr.Type.Name)
      }

      val modDef = MdMgr.GetMdMgr.MakeModelDef(ModDefInst.Model.NameSpace,
        ModDefInst.Model.Name,
        ModDefInst.Model.PhysicalName,
        ModDefInst.Model.ModelType,
        inputAttrList1,
        outputAttrList1,
        ModDefInst.Model.Version.toLong,
        ModDefInst.Model.JarName,
        ModDefInst.Model.DependencyJars.toArray)

      modDef
    } catch {
      case e: MappingException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw Json4sParsingException(e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw new ModelDefParsingException(e.getMessage())
      }
    }
  }

  @throws(classOf[Json4sParsingException])
  @throws(classOf[EngineConfigParsingException])
  def parseEngineConfig(configJson: String): Map[String, Any] = {
    try {
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(configJson)
      logger.debug("Parsed the json : " + configJson)

      val fullmap = json.values.asInstanceOf[Map[String, Any]]
      
      fullmap
    } catch {
      case e: MappingException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw Json4sParsingException(e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw new EngineConfigParsingException(e.getMessage())
      }
    }
  }

  @throws(classOf[Json4sParsingException])
  @throws(classOf[ApiArgListParsingException])
  def parseApiArgList(apiArgListJson: String): MetadataApiArgList = {
    try {
      implicit val jsonFormats: Formats = DefaultFormats
      val json = parse(apiArgListJson)
      logger.debug("Parsed the json : " + apiArgListJson)

      val cfg = json.extract[MetadataApiArgList]
      cfg
    } catch {
      case e: MappingException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw Json4sParsingException(e.getMessage())
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw new ApiArgListParsingException(e.getMessage())
      }
    }
  }

  def zkSerializeObjectToJson(o: ZooKeeperTransaction): String = {
    try {
      val json = ("Notifications" -> o.Notifications.toList.map { n =>
        (
          ("ObjectType" -> n.ObjectType) ~
          ("Operation" -> n.Operation) ~
          ("NameSpace" -> n.NameSpace) ~
          ("Name" -> n.Name) ~
          ("Version" -> n.Version.toLong) ~
          ("PhysicalName" -> n.PhysicalName) ~
          ("JarName" -> n.JarName) ~
          ("DependantJars" -> n.DependantJars.toList))
      })
      pretty(render(json))
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw Json4sSerializationException(e.getMessage())
      }
    }
  }

  def zkSerializeObjectToJson(mdObj: BaseElemDef, operation: String): String = {
    try {
      mdObj match {
        // Assuming that zookeeper transaction will be different based on type of object
        case o: ModelDef => {
          val json = (("ObjectType" -> "ModelDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: MessageDef => {
          val json = (("ObjectType" -> "MessageDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: MappedMsgTypeDef => {
          val json = (("ObjectType" -> "MappedMsgTypeDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: StructTypeDef => {
          val json = (("ObjectType" -> "StructTypeDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: ContainerDef => {
          val json = (("ObjectType" -> "ContainerDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: FunctionDef => {
          val json = (("ObjectType" -> "FunctionDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: ArrayTypeDef => {
          val json = (("ObjectType" -> "ArrayTypeDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: ArrayBufTypeDef => {
          val json = (("ObjectType" -> "ArrayBufTypeDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: SortedSetTypeDef => {
          val json = (("ObjectType" -> "SortedSetTypeDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: ImmutableMapTypeDef => {
          val json = (("ObjectType" -> "ImmutableMapTypeDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: MapTypeDef => {
          val json = (("ObjectType" -> "MapTypeDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: HashMapTypeDef => {
          val json = (("ObjectType" -> "HashMapTypeDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: SetTypeDef => {
          val json = (("ObjectType" -> "SetTypeDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: ImmutableSetTypeDef => {
          val json = (("ObjectType" -> "ImmutableSetTypeDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: TreeSetTypeDef => {
          val json = (("ObjectType" -> "TreeSetTypeDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: JarDef => {
          val json = (("ObjectType" -> "JarDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> o.jarName) ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: OutputMsgDef => {
          val json = (("ObjectType" -> "OutputMsgDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.nameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> o.ver) ~
            ("PhysicalName" -> o.physicalName) ~
            ("JarName" -> "") ~
            ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
          pretty(render(json))
        }
        case o: ConfigDef => {
          val json = (("ObjectType" -> "ConfigDef") ~
            ("Operation" -> operation) ~
            ("NameSpace" -> o.NameSpace) ~
            ("Name" -> o.name) ~
            ("Version" -> "0") ~
            ("PhysicalName" -> "") ~
            ("JarName" -> "") ~
            ("DependantJars" -> List[String]()) ~
            ("ConfigContnent" -> o.contents))   
          pretty(render(json))
        }
        case _ => {
          throw new UnsupportedObjectException("zkSerializeObjectToJson doesn't support the  objects of type objectType of " + mdObj.getClass().getName() + " yet.")
        }
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw Json4sSerializationException(e.getMessage())
      }
    }
  }

  def replaceLast(inStr: String, toReplace: String, replacement: String): String = {
    val pos = inStr.lastIndexOf(toReplace);
    if (pos > -1) {
      inStr.substring(0, pos) + replacement + inStr.substring(pos + toReplace.length(), inStr.length());
    } else {
      inStr;
    }
  }

  @throws(classOf[UnsupportedObjectException])
  def SerializeCfgObjectToJson(cfgObj: Object): String = {
    logger.debug("Generating Json for an object of type " + cfgObj.getClass().getName())
    cfgObj match {
      case o: ClusterInfo => {
        val json = (("ClusterId" -> o.clusterId))
        pretty(render(json))
      }
      case o: ClusterCfgInfo => {
        val json = (("ClusterId" -> o.clusterId) ~
          ("CfgMap" -> o.cfgMap))
        pretty(render(json))
      }
      case o: NodeInfo => {
        val json = (("NodeId" -> o.nodeId) ~
          ("NodePort" -> o.nodePort) ~
          ("NodeIpAddr" -> o.nodeIpAddr) ~
          ("JarPaths" -> o.jarPaths.toList) ~
          ("Scala_home" -> o.scala_home) ~
          ("Java_home" -> o.java_home) ~
          ("Roles" -> o.roles.toList) ~
          ("Classpath" -> o.classpath) ~
          ("ClusterId" -> o.clusterId))
        pretty(render(json))
      }
      case o: AdapterInfo => {
        val json = (("Name" -> o.name) ~
          ("TypeString" -> o.typeString) ~
          ("DataFormat" -> o.dataFormat) ~
          ("InputAdapterToVerify" -> o.inputAdapterToVerify) ~
          ("ClassName" -> o.className) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.dependencyJars.toList) ~
          ("AdapterSpecificCfg" -> o.adapterSpecificCfg) ~
          ("KeyAndValueDelimiter" -> o.KeyAndValueDelimiter) ~
          ("FieldDelimiter" -> o.FieldDelimiter) ~
          ("ValueDelimiter" -> o.ValueDelimiter) ~
          ("AssociatedMessage" -> o.associatedMsg))
        pretty(render(json))
      }
      case _ => {
        throw new UnsupportedObjectException("SerializeCfgObjectToJson doesn't support the " +
          "objectType of " + cfgObj.getClass().getName() + " yet")
      }
    }
  }

  @throws(classOf[UnsupportedObjectException])
  def SerializeObjectToJson(mdObj: BaseElemDef): String = {
    mdObj match {
      case o: FunctionDef => {
        val json = (("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("PhysicalName" -> o.physicalName) ~
          ("ReturnTypeNameSpace" -> o.retType.nameSpace) ~
          ("ReturnTypeName" -> o.retType.name) ~
          ("Arguments" -> o.args.toList.map { arg =>
            (
              ("ArgName" -> arg.name) ~
              ("ArgTypeNameSpace" -> arg.Type.nameSpace) ~
              ("ArgTypeName" -> arg.Type.name))
          }) ~
          ("Features" -> o.features.map(_.toString).toList) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("TransactionId" -> o.tranId))
        pretty(render(json))
      }

      case o: MessageDef => {
        // Assume o.containerType is checked for not being null
        val json = ("Message" ->
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("FullName" -> o.FullName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("Persist" -> o.containerType.persist) ~
          ("JarName" -> o.jarName) ~
          ("PhysicalName" -> o.typeString) ~
          ("ObjectDefinition" -> o.objectDefinition) ~
          ("ObjectFormat" -> ObjFormatType.asString(o.objectFormat)) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("TransactionId" -> o.tranId))
        var jsonStr = pretty(render(json))

        o.containerType match {
          case c: StructTypeDef => {
            jsonStr = replaceLast(jsonStr, "}\n}", "").trim + ",\n  \"Attributes\": "
            var memberDefJson = SerializeObjectListToJson(o.containerType.asInstanceOf[StructTypeDef].memberDefs)
            memberDefJson = memberDefJson + "}\n}"
            jsonStr += memberDefJson
            jsonStr
          }
          case c: MappedMsgTypeDef => {
            jsonStr = jsonStr.replaceAll("}", "").trim + ",\n  \"MappedMsgTypeDef\": "
            var memberDefJson = SerializeObjectListToJson(o.containerType.asInstanceOf[MappedMsgTypeDef].attrMap.values.toArray)
            memberDefJson = memberDefJson + "}\n}"
            jsonStr += memberDefJson
            jsonStr
          }
          case _ => {
            throw new UnsupportedObjectException("SerializeObjectToJson doesn't support the " +
              "objectType of " + o.containerType.getClass().getName() + " yet")
          }
        }
      }

      case o: ContainerDef => {
        val json = ("Container" ->
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("FullName" -> o.FullName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("Persist" -> o.containerType.persist) ~
          ("JarName" -> o.jarName) ~
          ("PhysicalName" -> o.typeString) ~
          ("ObjectDefinition" -> o.objectDefinition) ~
          ("ObjectFormat" -> ObjFormatType.asString(o.objectFormat)) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("TransactionId" -> o.tranId))
        var jsonStr = pretty(render(json))

        o.containerType match {
          case c: StructTypeDef => {
            jsonStr = replaceLast(jsonStr, "}\n}", "").trim + ",\n  \"Attributes\": "
            var memberDefJson = SerializeObjectListToJson(o.containerType.asInstanceOf[StructTypeDef].memberDefs)
            memberDefJson = memberDefJson + "}\n}"
            jsonStr += memberDefJson
            jsonStr
          }
          case c: MappedMsgTypeDef => {
            jsonStr = jsonStr.replaceAll("}", "").trim + ",\n  \"MappedMsgTypeDef\": "
            var memberDefJson = SerializeObjectListToJson(o.containerType.asInstanceOf[MappedMsgTypeDef].attrMap.values.toArray)
            memberDefJson = memberDefJson + "}\n}"
            jsonStr += memberDefJson
            jsonStr
          }
          case _ => {
            throw new UnsupportedObjectException(s"SerializeObjectToJson doesn't support the " +
              "objectType of $mdObj.name  yet")
          }
        }
      }

      case o: ModelDef => {
        val json = ("Model" ->
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("ModelType" -> o.modelType) ~
          ("JarName" -> o.jarName) ~
          ("PhysicalName" -> o.typeString) ~
          ("ObjectDefinition" -> o.objectDefinition) ~
          ("ObjectFormat" -> ObjFormatType.asString(o.objectFormat)) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Deleted" -> o.deleted) ~
          ("Active" -> o.active) ~
          ("TransactionId" -> o.tranId))
        var jsonStr = pretty(render(json))
        jsonStr = replaceLast(jsonStr, "}\n}", "").trim
        jsonStr = jsonStr + ",\n\"InputAttributes\": "
        var memberDefJson = SerializeObjectListToJson(o.inputVars)
        jsonStr += memberDefJson

        jsonStr = jsonStr + ",\n\"OutputAttributes\": "
        memberDefJson = SerializeObjectListToJson(o.outputVars)
        memberDefJson = memberDefJson + "}\n}"
        jsonStr += memberDefJson
        jsonStr
      }

      case o: AttributeDef => {
        val json = (("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("CollectionType" -> ObjType.asString(o.collectionType)) ~
          ("TransactionId" -> o.tranId))
        var jsonStr = pretty(render(json))
        //jsonStr = jsonStr.replaceAll("}","").trim + ",\n  \"Type\": "
        jsonStr = replaceLast(jsonStr, "}", "").trim + ",\n  \"Type\": "
        var memberDefJson = SerializeObjectToJson(o.typeDef)
        memberDefJson = memberDefJson + "}"
        jsonStr += memberDefJson
        jsonStr
      }
      case o: ScalarTypeDef => {
        val json = (("MetadataType" -> "ScalarTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.tTypeType)) ~
          ("TypeNameSpace" -> MdMgr.sysNS) ~
          ("TypeName" -> o.name) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("TransactionId" -> o.tranId))
        pretty(render(json))
      }
      case o: SetTypeDef => {
        val json = (("MetadataType" -> "SetTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.tTypeType)) ~
          ("TypeNameSpace" -> o.nameSpace) ~
          ("TypeName" -> o.name) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("Fixed" -> o.IsFixed) ~
          ("KeyTypeNameSpace" -> o.keyDef.nameSpace) ~
          ("KeyTypeName" -> ObjType.asString(o.keyDef.tType)) ~
          ("TransactionId" -> o.tranId))

        pretty(render(json))
      }
      case o: ImmutableSetTypeDef => {
        val json = (("MetadataType" -> "ImmutableSetTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.tTypeType)) ~
          ("TypeNameSpace" -> o.nameSpace) ~
          ("TypeName" -> o.name) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("Fixed" -> o.IsFixed) ~
          ("KeyTypeNameSpace" -> o.keyDef.nameSpace) ~
          ("KeyTypeName" -> ObjType.asString(o.keyDef.tType)) ~
          ("TransactionId" -> o.tranId))

        pretty(render(json))
      }
      case o: TreeSetTypeDef => {
        val json = (("MetadataType" -> "TreeSetTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.tTypeType)) ~
          ("TypeNameSpace" -> o.nameSpace) ~
          ("TypeName" -> o.name) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("Fixed" -> o.IsFixed) ~
          ("KeyTypeNameSpace" -> o.keyDef.nameSpace) ~
          ("KeyTypeName" -> ObjType.asString(o.keyDef.tType)) ~
          ("TransactionId" -> o.tranId))
        pretty(render(json))
      }
      case o: SortedSetTypeDef => {
        val json = (("MetadataType" -> "SortedSetTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.tTypeType)) ~
          ("TypeNameSpace" -> o.nameSpace) ~
          ("TypeName" -> o.name) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("Fixed" -> o.IsFixed) ~
          ("KeyTypeNameSpace" -> o.keyDef.nameSpace) ~
          ("KeyTypeName" -> ObjType.asString(o.keyDef.tType)) ~
          ("TransactionId" -> o.tranId))
        pretty(render(json))
      }
      case o: MapTypeDef => {
        val json = (("MetadataType" -> "MapTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.tTypeType)) ~
          ("TypeNameSpace" -> o.nameSpace) ~
          ("TypeName" -> o.name) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("Fixed" -> o.IsFixed) ~
          ("KeyTypeNameSpace" -> o.keyDef.nameSpace) ~
          ("KeyTypeName" -> ObjType.asString(o.keyDef.tType)) ~
          ("ValueTypeNameSpace" -> o.valDef.nameSpace) ~
          ("ValueTypeName" -> ObjType.asString(o.valDef.tType)) ~
          ("TransactionId" -> o.tranId))
        pretty(render(json))
      }
      case o: ImmutableMapTypeDef => {
        val json = (("MetadataType" -> "ImmutableMapTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.tTypeType)) ~
          ("TypeNameSpace" -> o.nameSpace) ~
          ("TypeName" -> o.name) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("Fixed" -> o.IsFixed) ~
          ("KeyTypeNameSpace" -> o.keyDef.nameSpace) ~
          ("KeyTypeName" -> ObjType.asString(o.keyDef.tType)) ~
          ("ValueTypeNameSpace" -> o.valDef.nameSpace) ~
          ("ValueTypeName" -> ObjType.asString(o.valDef.tType)) ~
          ("TransactionId" -> o.tranId))
        pretty(render(json))
      }
      case o: HashMapTypeDef => {
        val json = (("MetadataType" -> "HashMapTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.tTypeType)) ~
          ("TypeNameSpace" -> o.nameSpace) ~
          ("TypeName" -> o.name) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("Fixed" -> o.IsFixed) ~
          ("KeyTypeNameSpace" -> o.keyDef.nameSpace) ~
          ("KeyTypeName" -> ObjType.asString(o.keyDef.tType)) ~
          ("ValueTypeNameSpace" -> o.valDef.nameSpace) ~
          ("ValueTypeName" -> ObjType.asString(o.valDef.tType)) ~
          ("TransactionId" -> o.tranId))
        pretty(render(json))
      }
      case o: ListTypeDef => {
        val json = (("MetadataType" -> "ListTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.tTypeType)) ~
          ("TypeNameSpace" -> o.nameSpace) ~
          ("TypeName" -> o.name) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("Fixed" -> o.IsFixed) ~
          ("ValueTypeNameSpace" -> o.valDef.nameSpace) ~
          ("ValueTypeName" -> ObjType.asString(o.valDef.tType)) ~
          ("TransactionId" -> o.tranId))
        pretty(render(json))
      }
      case o: QueueTypeDef => {
        val json = (("MetadataType" -> "QueueTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.tTypeType)) ~
          ("TypeNameSpace" -> o.nameSpace) ~
          ("TypeName" -> o.name) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("Fixed" -> o.IsFixed) ~
          ("ValueTypeNameSpace" -> o.valDef.nameSpace) ~
          ("ValueTypeName" -> ObjType.asString(o.valDef.tType)) ~
          ("TransactionId" -> o.tranId))
        pretty(render(json))
      }
      case o: ArrayTypeDef => {
        val json = (("MetadataType" -> "ArrayTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.elemDef.tTypeType)) ~
          ("TypeNameSpace" -> o.elemDef.nameSpace) ~
          ("TypeName" -> ObjType.asString(o.elemDef.tType)) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("NumberOfDimensions" -> o.arrayDims) ~
          ("TransactionId" -> o.tranId))
        pretty(render(json))
      }
      case o: ArrayBufTypeDef => {
        val json = (("MetadataType" -> "ArrayBufTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.elemDef.tTypeType)) ~
          ("TypeNameSpace" -> o.elemDef.nameSpace) ~
          ("TypeName" -> ObjType.asString(o.elemDef.tType)) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("NumberOfDimensions" -> o.arrayDims) ~
          ("TransactionId" -> o.tranId))
        pretty(render(json))
      }
      case o: TupleTypeDef => {
        var json = (("MetadataType" -> "TupleTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.tTypeType)) ~
          ("TypeNameSpace" -> MdMgr.sysNS) ~
          ("TypeName" -> o.name) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("TransactionId" -> o.tranId))
        var jsonStr = pretty(render(json))
        val idxLastCloseParen: Int = jsonStr.lastIndexOf("\n}")
        jsonStr = jsonStr.slice(0, idxLastCloseParen).toString + ",\n  \"TupleDefinitions\": "
        var tupleDefJson = SerializeObjectListToJson(o.tupleDefs)
        tupleDefJson = tupleDefJson + "}"
        jsonStr += tupleDefJson
        jsonStr
      }
      case o: StructTypeDef => {
        val json = (("MetadataType" -> "StructTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.tTypeType)) ~
          ("TypeNameSpace" -> MdMgr.sysNS) ~
          ("TypeName" -> o.name) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("TransactionId" -> o.tranId))
        pretty(render(json))
      }
      case o: MappedMsgTypeDef => {
        val json = (("MetadataType" -> "MappedMsgTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.tTypeType)) ~
          ("TypeNameSpace" -> MdMgr.sysNS) ~
          ("TypeName" -> o.name) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("TransactionId" -> o.tranId))
        pretty(render(json))
      }
      case o: AnyTypeDef => {
        val json = (("MetadataType" -> "AnyTypeDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("TypeTypeName" -> ObjTypeType.asString(o.tTypeType)) ~
          ("TypeNameSpace" -> o.nameSpace) ~
          ("TypeName" -> o.name) ~
          ("PhysicalName" -> o.physicalName) ~
          ("Version" -> MdMgr.Pad0s2Version(o.ver)) ~
          ("JarName" -> o.jarName) ~
          ("DependencyJars" -> o.CheckAndGetDependencyJarNames.toList) ~
          ("Implementation" -> o.implementationName) ~
          ("TransactionId" -> o.tranId))
        pretty(render(json))
      }
      case o: OutputMsgDef => {
        val json = (("ObjectType" -> "OutputMsgDef") ~
          ("NameSpace" -> o.nameSpace) ~
          ("Name" -> o.name) ~
          ("Version" -> o.ver) ~
          ("PhysicalName" -> o.physicalName) ~
          ("JarName" -> "") ~
          ("DependantJars" -> o.CheckAndGetDependencyJarNames.toList))
        pretty(render(json))
      }
      case _ => {
        throw new UnsupportedObjectException(s"SerializeObjectToJson doesn't support the objectType of " + mdObj.getClass().getName() + "  yet")
      }
    }
  }

  def SerializeObjectListToJson[T <: BaseElemDef](objList: Array[T]): String = {
    var json = "[ \n"
    objList.toList.map(obj => { var objJson = SerializeObjectToJson(obj); json += objJson; json += ",\n" })
    json = json.stripSuffix(",\n")
    json += " ]\n"
    json
  }

  def SerializeCfgObjectListToJson[T <: Object](objList: Array[T]): String = {
    var json = "[ \n"
    objList.toList.map(obj => { var objJson = SerializeCfgObjectToJson(obj); json += objJson; json += ",\n" })
    json = json.stripSuffix(",\n")
    json += " ]\n"
    json
  }

  def SerializeObjectListToJson[T <: BaseElemDef](objType: String, objList: Array[T]): String = {
    var json = "{\n" + "\"" + objType + "\" :" + SerializeObjectListToJson(objList) + "\n}"
    json
  }

  def SerializeCfgObjectListToJson[T <: Object](objType: String, objList: Array[T]): String = {
    var json = "{\n" + "\"" + objType + "\" :" + SerializeCfgObjectListToJson(objList) + "\n}"
    json
  }

  def zkSerializeObjectListToJson[T <: BaseElemDef](objList: Array[T], operations: Array[String]): String = {
    var json = "[ \n"
    var i = 0
    objList.toList.map(obj => { var objJson = zkSerializeObjectToJson(obj, operations(i)); i = i + 1; json += objJson; json += ",\n" })
    json = json.stripSuffix(",\n")
    json += " ]\n"
    json
  }

  def zkSerializeObjectListToJson[T <: BaseElemDef](objType: String, objList: Array[T], operations: Array[String]): String = {
    // Insert the highest Transaction ID into the JSON Notification message.
    var max: Long = 0
    objList.foreach(obj => { max = scala.math.max(obj.TranId, max) })

    var json = "{\n" + "\"transactionId\":\"" + max + "\",\n" + "\"" + objType + "\" :" + zkSerializeObjectListToJson(objList, operations) + "\n}"
    json
  }

  def SerializeApiArgListToJson(o: MetadataApiArgList): String = {
    try {
      val json = ("ArgList" -> o.ArgList.map { n =>
        (
          ("ObjectType" -> n.ObjectType) ~
          ("NameSpace" -> n.NameSpace) ~
          ("Name" -> n.Name) ~
          ("Version" -> n.Version) ~
          ("FormatType" -> n.FormatType))
      })
      pretty(render(json))
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw Json4sSerializationException(e.getMessage())
      }
    }
  }

  def SerializeAuditRecordsToJson(ar: Array[AuditRecord]): String = {
    try {
      val json = (ar.toList.map { a => a.toJson })
      pretty(render(json))
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw Json4sSerializationException(e.getMessage())
      }
    }
  }
  
  def SerializeMapToJsonString (map: Map[String,Any]): String = {
     implicit val formats = org.json4s.DefaultFormats
     return Serialization.write(map)
  }

}
