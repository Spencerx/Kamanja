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

package com.ligadata.udf.extract

import scala.reflect.runtime.universe._
import scala.reflect.runtime.{ universe => ru }
import scala.reflect.api._
import scala.reflect.runtime.universe.MemberScope
import scala.collection.mutable._
import scala.collection.immutable.{ Set, TreeMap }
import scala.Symbol
import sys.process._
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.File
import java.io.PrintWriter
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.pmml.udfs._
import com.ligadata.kamanja.metadata._
import scala.util.parsing.json.JSON
import scala.util.parsing.json.{ JSONObject, JSONArray }
import org.json4s._
import org.json4s.JsonDSL._
import com.ligadata.Serialize._
import com.ligadata.Utils.{ Utils, KamanjaClassLoader, KamanjaLoaderInfo }
import com.ligadata.Exceptions.StackTrace

/**
 * MethodExtract accepts an fully qualifed scala object name.  The object's package
 * path will server as the the kamanja namespace for its methods in the metadata manager.
 * The method metadata for the methods in this object are extracted and
 * MetadataManager FunctionDef catalog method invocations are
 * formed.
 *
 * As part of the process, the types that are needed in order to catalog
 * the methods in the object are also noted and commands are built for them
 * as well.
 *
 * The current procedure is to save these invocations in a file
 * that are then compiled and executed.
 *
 * Better approach is needed ordered worst to best...
 *
 * 1) Use the MetadataAPI command line interface
 * and send each FunctionDef spec (in the form it expects)
 * to the API.
 * 2) dynamically compile a program that adds this information
 * directly to the MetadataApi
 * 3) The MetadataAPI implements the previous item in its
 * implementation.  The API would accept the fully qualified
 * object path and possibly the jar in which it appears.  It would
 * dynamically load the class and pass the path name to the
 * MethodExtract that would then generate the source code.
 * The API would compile the returned source code and
 * execute it.
 *
 */

object UdfExtractGlobalLogger {
    val loggerName = this.getClass.getName()
    val logger = LogManager.getLogger(loggerName)
}

trait LogTrait {
  val logger = UdfExtractGlobalLogger.logger
}

object MethodExtract extends App with LogTrait {

  override def main(args: Array[String]) {

    var typeMap: Map[String, BaseElemDef] = Map[String, BaseElemDef]()
    var typeArray: ArrayBuffer[BaseElemDef] = ArrayBuffer[BaseElemDef]()
    var funcDefArgs: ArrayBuffer[FuncDefArgs] = ArrayBuffer[FuncDefArgs]()

    val arglist = args.toList
    type OptionMap = scala.collection.mutable.Map[Symbol, String]
    def nextOption(map: OptionMap, list: List[String]): OptionMap = {
      list match {
        case Nil => map
        case "--object" :: value :: tail =>
          nextOption(map ++ Map('object -> value), tail)
        case "--cp" :: value :: tail =>
          nextOption(map ++ Map('cp -> value), tail)
        case "--exclude" :: value :: tail =>
          nextOption(map ++ Map('excludeList -> value), tail)
        case "--versionNumber" :: value :: tail =>
          nextOption(map ++ Map('versionNumber -> value), tail)
        case "--deps" :: value :: tail =>
          nextOption(map ++ Map('deps -> value), tail)
        case "--typeDefsPath" :: value :: tail =>
          nextOption(map ++ Map('typeDefsPath -> value), tail)
        case "--fcnDefsPath" :: value :: tail =>
          nextOption(map ++ Map('fcnDefsPath -> value), tail)
        case option :: tail => {
          logger.error("Unknown option " + option)
          val usageMsg: String = usage
          logger.error(s"$usageMsg")
          sys.exit(1)
        }

      }
    }

    val options = nextOption(Map(), arglist)
    val clsName = if (options.contains('object)) options.apply('object) else null
    val classPath = if (options.contains('cp)) options.apply('cp) else null
    val namespace: String = if (clsName != null && clsName.contains('.')) {
      val clsnameNodes: Array[String] = clsName.split('.')
      val takeFirstN: Int = clsnameNodes.size
      /** actually we are taking all of them ... object name included */
      val firstN: Array[String] = clsnameNodes.map(_.trim).take(takeFirstN)
      firstN.addString(new StringBuilder, ".").toString
    } else {
      null
    }
    val excludeListStr = if (options.contains('excludeList)) options.apply('excludeList) else null
    var excludeList: Array[String] = null
    val versionNumberStr = if (options.contains('versionNumber)) options.apply('versionNumber) else null
    var versionNumber: Long = 1000000
    try {
      if (versionNumberStr != null) versionNumber = versionNumberStr.toLong
    } catch {
      case _: Throwable => {
        val stackTrace = StackTrace.ThrowableTraceString(_)
        logger.debug("StackTrace:" + stackTrace)
        versionNumber = 1000000
      }
    }
    val depsIn = if (options.contains('deps)) options.apply('deps) else null
    val typedefPath = if (options.contains('typeDefsPath)) options.apply('typeDefsPath) else null
    val fcndefPath = if (options.contains('fcnDefsPath)) options.apply('fcnDefsPath) else null

    if (clsName == null || classPath == null || namespace == null || depsIn == null || typedefPath == null || fcndefPath == null) {
      val usageStr = usage
      val errMsg: String = if (clsName != null && classPath != null && namespace == null && depsIn != null && typedefPath != null && fcndefPath != null) {
        "...Namespace-less udf object is not supported... the object must be package qualifed... the full package name is to be used as the namespace."
      } else {
        "...Missing arguments"
      }
      logger.error(errMsg)
      logger.error(usageStr)
      sys.exit(1)
    }
    /**
     *  Split the deps ... the first element and the rest... the head element has the
     *  jar name where this lib lives (assuming that the sbtProjDependencies.scala script was used
     *  to prepare the dependencies).  For the jar, we only need the jar's name... strip the path
     */
    val depsArr: Array[String] = depsIn.split(',').map(_.trim)
    val jarName = depsArr.head.split('/').last
    val deps: Array[String] = depsArr.tail

    /** prepare the class path array so that the udf can be loaded (and its udfs introspected) */
    val cp: Array[String] = classPath.split(':').map(_.trim)
    val udfLoaderInfo = new KamanjaLoaderInfo
    Utils.LoadJars(cp, udfLoaderInfo.loadedJars, udfLoaderInfo.loader)

    val justObjectsFcns: String = clsName.split('.').last.trim
    //logger.debug(s"Just catalog the functions found in $clsName")

    if (excludeListStr != null) {
      excludeList = excludeListStr.split(',').map(fn => fn.trim)
    } else {
      excludeList = Array[String]()
    }
    /** get the members */

    /** Create an array buffer filled with the methods from the object with the supplied fully qualified class name */
    val mirror = ru.runtimeMirror(udfLoaderInfo.loader)
    var mbrs: ArrayBuffer[scala.reflect.runtime.universe.Symbol] = ArrayBuffer[scala.reflect.runtime.universe.Symbol]()
    try {
      val clz1 = Class.forName(clsName + "$", true, udfLoaderInfo.loader)
      val clz1Sym = mirror.classSymbol(clz1)
      val istrait = clz1Sym.isTrait
      val isabstract = clz1Sym.isAbstractClass
      val clsType = clz1Sym.toType
      val members = clsType.declarations
      members.filter(_.toString.startsWith("method")).foreach(m => mbrs += m)
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Failed to load class %s with Reason:%s Message:%s\nStackTrace:%s".format(clsName + "$", e.getCause, e.getMessage, stackTrace))
        sys.exit
      }
      case t: Throwable => {
        val stackTrace = StackTrace.ThrowableTraceString(t)
        logger.error("Failed to load class %s with Reason:%s Message:%s\nStackTrace:%s".format(clsName + "$", t.getCause, t.getMessage, stackTrace))
        sys.exit
      }
    }

    val initFcnNameBuffer: StringBuilder = new StringBuilder()
    val initFcnNameNodes: Array[String] = clsName.split('.').map(node => node.trim)
    val initFcnBuffer: StringBuilder = new StringBuilder()
    initFcnNameNodes.addString(initFcnBuffer, "_")
    val initFcnName: String = initFcnBuffer.toString
    initFcnBuffer.append(s"\ndef init_$initFcnName {\n")

    val mgr: MdMgr = InitializeMdMgr

    /** filter out the methods and then only utilize those that are in the object ... ignore inherited trait methods */
    mbrs.foreach { fcnMethod =>
      val fcnMethodObj = fcnMethod.asInstanceOf[MethodSymbol]
      val name = fcnMethodObj.name
      val returnType = fcnMethodObj.returnType
      val fullName = fcnMethodObj.fullName

      if (name.toString == "And" || name.toString == "Or") {
        val stop: Int = 0
      }

      val typeSig = fcnMethodObj.typeSignature

      if (fullName.contains(justObjectsFcns)) {
        val nm: String = name.toString
        val fnm: String = fullName.toString
        val rt: String = returnType.toString
        val ts: String = typeSig.toString

        val notExcluded: Boolean = (excludeList.filter(exclnm => nm.contains(exclnm) || rt.contains(exclnm)).length == 0)
        if (notExcluded && !nm.contains("$")) {

          val cmd: MethodCmd = new MethodCmd(mgr, versionNumber, namespace, typeMap, typeArray, nm, fnm, rt, ts)
          if (cmd != null) {
            val (funcInfo, cmdStr): (FuncDefArgs, String) = cmd.makeFuncDef
            if (funcInfo != null) {
              funcDefArgs += funcInfo
            }
            initFcnBuffer.append(s"\t$cmdStr")
          }
        } else {
          logger.debug(s"Method $fullName returning $rt excluded")
        }
      }
    }
    initFcnBuffer.append(s"}\n")
    val fcnStr = initFcnBuffer.toString
    //logger.debug(s"$fcnStr")

    /** Serialize the types that were generated during the UDF lib introspection and print them to stdout */
    val sortedTypeMap: LinkedHashMap[String, BaseElemDef] = LinkedHashMap(typeMap.toSeq.sortBy(_._1): _*)
    //sortedTypeMap.keys.toArray.foreach( typ => println(typ))
    //println

    /**
     * What is above (the sorted map) is fine for understanding what types are actually needed by the
     * UDF lib supplied, however it cannot be emitted that way for intake by the MetadataAPI.  The types build
     * upon themselves with the inner types of an array of array of tupleN having the tupleN emitted first.
     *
     * Therefore the typeArray is iterated.  To avoid duplicate emissions, a set is used to track what has been
     * emitted.  Only one type with a given name should be emitted.  These are collected in the emitTheseTypes array.
     */
    var trackEmission: scala.collection.mutable.Set[String] = scala.collection.mutable.Set[String]()
    var emitTheseTypes: ArrayBuffer[BaseElemDef] = ArrayBuffer[BaseElemDef]()
    var i: Int = 0
    typeArray.foreach(typ =>
      if (!trackEmission.contains(typ.Name)) {
        i += 1
        emitTheseTypes += typ
        trackEmission += typ.Name
      })
    /** println(s"There are $i unique types with ${typeArray.size} instances") */

    /** Serialize and write json type definitions to file */
    val typesAsJson: String = JsonSerializer.SerializeObjectListToJson("Types", emitTheseTypes.toArray)
    writeFile(typesAsJson, typedefPath)

    /** Create the FunctionDef objects to be serialized by combining the FuncDefArgs collected with the version and deps info */

    val funcDefs: Array[FunctionDef] = funcDefArgs.toArray.map(fArgs => {
      var features: scala.collection.mutable.Set[FcnMacroAttr.Feature] = scala.collection.mutable.Set[FcnMacroAttr.Feature]()
      if (fArgs.hasIndefiniteArity) {
        features += FcnMacroAttr.HAS_INDEFINITE_ARITY
      }
      mgr.MakeFunc(fArgs.namespace, fArgs.fcnName, fArgs.physicalName, (fArgs.returnNmSpc, fArgs.returnTypeName), fArgs.argTriples.toList, features, fArgs.versionNo, jarName, deps)
    })

    /** Serialize and write json function definitions to file */
    val functionsAsJson: String = JsonSerializer.SerializeObjectListToJson("Functions", funcDefs.toArray)
    writeFile(functionsAsJson, fcndefPath)

    //logger.debug("Complete!")
  }

  def usage: String = {
    """
Collect the function definitions from the supplied object that is found in the supplied class path.  The classpath contains
the object and any supporting libraries it might require.  The supplied version number will be used for the  
version value for all function definitions produced.  The deps string contains the same jars as the classpath argument
but without paths.  These are used to create the deps values for the function definitions.  The results for the types and function
definitions are written to the supplied typeDefsPath and fcnDefsPath respectively.
	  
Usage: scala com.ligadata.udf.extract.MethodExtract --object <fully qualifed scala object name> 
													--cp <classpath>
                                                    --exclude <a list of functions to ignore>
                                                    --versionNumber <N>
                                                    --deps <jar dependencies comma delimited list>
													--typeDefsPath <types file path>
													--fcnDefsPath <function definition file path>
         where 	<fully qualifed scala object name> (required) is the scala object name that contains the 
					functions to be cataloged
				<the kamanja namespace> in which these UDFs should be cataloged
				<a list of functions to ignore> is a comma delimited list of functions to ignore (OPTIONAL)
				<N> is the version number to be assigned to all functions in the UDF lib.  It should be greater than
					the prior versions that may have been for a prior version of the UDFs.
				<jar dependencies comma delimited list> this is the list of jars that this UDF lib (jar) depends upon.
					A complete dependency list can be obtained by running the sbtProjDependencies.scala script.
				<types file path> the file path that will receive any type definitions that may be needed to catalog the functions
					being collected
				<function definition file path> the file path that will receive the function definitions
				
	  
       NOTE: The jar containing this scala object and jars upon which it depends should be on the class path.  Except for
	   the exclusion list, all arguments are mandatory.  
	   NOTE: The full package name of the object containing the udfs will become the namespace for the udfs to be cataloged.

"""
  }

  /**
   *  Retrieve a fresh and empty MdMgr from MdMgr object.  Seed it with some essential scalars (and essential system containers)
   *  to start the ball rolling.
   *
   *  @return nearly empty MdMgr... seeded with essential scalars
   */
  def InitializeMdMgr: MdMgr = {
    val versionNumber: Long = 1
    val mgr: MdMgr = MdMgr.GetMdMgr

    /** seed essential types */
    mgr.AddScalar(MdMgr.sysNS, "Any", ObjType.tAny, "Any", versionNumber, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.AnyImpl")
    mgr.AddScalar(MdMgr.sysNS, "String", ObjType.tString, "String", versionNumber, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.StringImpl")
    mgr.AddScalar(MdMgr.sysNS, "Int", ObjType.tInt, "Int", versionNumber, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.IntImpl")
    mgr.AddScalar(MdMgr.sysNS, "Integer", ObjType.tInt, "Int", versionNumber, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.IntImpl")
    mgr.AddScalar(MdMgr.sysNS, "Long", ObjType.tLong, "Long", versionNumber, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.LongImpl")
    mgr.AddScalar(MdMgr.sysNS, "Boolean", ObjType.tBoolean, "Boolean", versionNumber, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.BoolImpl")
    mgr.AddScalar(MdMgr.sysNS, "Bool", ObjType.tBoolean, "Boolean", versionNumber, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.BoolImpl")
    mgr.AddScalar(MdMgr.sysNS, "Double", ObjType.tDouble, "Double", versionNumber, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.DoubleImpl")
    mgr.AddScalar(MdMgr.sysNS, "Float", ObjType.tFloat, "Float", versionNumber, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.FloatImpl")
    mgr.AddScalar(MdMgr.sysNS, "Char", ObjType.tChar, "Char", versionNumber, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.CharImpl")

    mgr.AddFixedContainer(MdMgr.sysNS, "Context", "com.ligadata.pmml.runtime.Context", List())

    mgr.AddFixedContainer(MdMgr.sysNS, "EnvContext", "com.ligadata.KamanjaBase.EnvContext", List())

    mgr.AddFixedContainer(MdMgr.sysNS, "BaseMsg", "com.ligadata.KamanjaBase.BaseMsg", List())

    mgr.AddFixedContainer(MdMgr.sysNS, "BaseContainer", "com.ligadata.KamanjaBase.BaseContainer", List())

    mgr.AddFixedContainer(MdMgr.sysNS, "MessageContainerBase", "com.ligadata.KamanjaBase.MessageContainerBase", List())

    mgr
  }

  /**
   * 	Write the supplied text to the supplied path.
   *
   *   @param text a string with either type or function definition declarations as its content
   *   @param targetPath the file path that will receive the text.
   */

  private def writeFile(text: String, targetPath: String) {
    val file = new File(targetPath);
    val bufferedWriter = new BufferedWriter(new FileWriter(file))
    bufferedWriter.write(text)
    bufferedWriter.close
  }
}
