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

import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.InputSource
import org.xml.sax.XMLReader
import scala.collection.mutable._
import java.io.BufferedWriter
import java.io.FileWriter
import sys.process._
import java.io.PrintWriter
import org.apache.logging.log4j._
import com.ligadata.kamanja.metadata._
import com.ligadata._
import com.ligadata.messagedef._
import com.ligadata.pmml.compiler._
import com.ligadata.kamanja.metadata.ObjFormatType._
import com.ligadata.Serialize._
import com.ligadata.Exceptions._
import java.util.jar.JarInputStream
import scala.util.control.Breaks._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.ligadata.Utils.{ Utils, KamanjaClassLoader, KamanjaLoaderInfo }
import com.ligadata.KamanjaBase.{ ModelInstanceFactory, EnvContext, FactoryOfModelInstanceFactory }


// CompilerProxy has utility functions to:
// Call MessageDefinitionCompiler, 
// Call PmmlCompiler, 
// Generate jar files out of output of above compilers
// Persist model definitions and corresponding jar files in Metadata Mgr
// Persist message definitions, and corresponding jar files in Metadata Mgr
class CompilerProxy {

  val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)
  private var userId: Option[String] = _
  lazy val compiler_work_dir = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("COMPILER_WORK_DIR")

  def setSessionUserId(id: Option[String]): Unit = { userId = id }

  /**
   * compileModelFromSource - This gets called from the mainline AddModel... we just know the modelConfigName, and need
   *                          to figure out the build dependencies.
   *
   */
  def compileModelFromSource(sourceCode: String, modelConfigName: String, sourceLang: String = "scala"): ModelDef = {
    try {
      // Figure out the metadata information needed for 
      val additinalDeps = addDepsFromClassPath
      val (classPath, elements, totalDeps, nonTypeDeps) = getClassPathFromModelConfig(modelConfigName, additinalDeps)
      val msgDefClassFilePath = compiler_work_dir + "/" + removeUserid(modelConfigName) + "." + sourceLang
      val ((modelNamespace, modelName, modelVersion, pname), repackagedCode, tempPackage) = parseSourceForMetadata(sourceCode, modelConfigName, sourceLang, msgDefClassFilePath, classPath, elements)
      return generateModelDef(repackagedCode, sourceLang, pname, classPath, tempPackage, modelName,
        modelVersion, msgDefClassFilePath, elements, sourceCode,
        totalDeps,
        MetadataAPIImpl.getModelMessagesContainers(modelConfigName, None),
        nonTypeDeps,false)
    } catch {
      case e: Exception => {
        logger.error("COMPILER_PROXY: unable to determine model metadata information during AddModel. ERROR " + e.getMessage)
        throw e
      }
    }
  }

  /**
   * compileModelFromSource - This will get called from the recompile path due to a message/container change.  All the info
   *                          is available.. so just generate the new ModelDef
   *
   */
  def recompileModelFromSource(sourceCode: String, pName: String, deps: List[String], typeDeps: List[String], sourceLang: String = "scala"): ModelDef = {
    try {
      val (classPath, elements, totalDeps, nonTypeDeps) = buildClassPath(deps, typeDeps)
      val msgDefClassFilePath = compiler_work_dir + "/tempCode." + sourceLang
      val ((modelNamespace, modelName, modelVersion, pname), repackagedCode, tempPackage) = parseSourceForMetadata(sourceCode, "tempCode", sourceLang, msgDefClassFilePath, classPath, elements)
      return generateModelDef(repackagedCode, sourceLang, pname, classPath, tempPackage, modelName,
        modelVersion, msgDefClassFilePath, elements, sourceCode, totalDeps, typeDeps, nonTypeDeps,true)
    } catch {
      case e: Exception => {
        logger.error("COMPILER_PROXY: unable to determine model metadata information during recompile. ERROR " + e.getMessage)
        throw e
      }
    }
  }

  /**
   *
   */
  def compilePmml(pmmlStr: String, recompile: Boolean = false): (String, ModelDef) = {
    try {
      /** Ramana, if you set this to true, you will cause the generation of logger.info (...) stmts in generated model */
      var injectLoggingStmts: Boolean = false

      val model_exec_log = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MODEL_EXEC_LOG")
      if (model_exec_log.equalsIgnoreCase("true")) {
        injectLoggingStmts = true
      }

      val compiler = new PmmlCompiler(MdMgr.GetMdMgr, "ligadata", logger, injectLoggingStmts,
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS").split(","))
      val (classStr, modDef) = compiler.compile(pmmlStr, compiler_work_dir, recompile)

      /**
       * if errors were encountered... the model definition is not manufactured.
       *  Avoid Scala compilation of the broken src.  Src file MAY be available
       *  in classStr.  However, if there were simple syntactic issues or simple semantic
       *  issues, it may not be generated.
       */
      if (modDef != null) {
        var pmmlScalaFile = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_TARGET_DIR") + "/" + modDef.name + ".pmml"
        var classPath = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CLASSPATH").trim

        if (classPath.size == 0)
          classPath = "."

        val jarPaths = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS").split(",").toSet

        if (modDef.DependencyJarNames != null) {
          val depJars = modDef.DependencyJarNames.map(j => Utils.GetValidJarFile(jarPaths, j)).mkString(":")
          if (classPath != null && classPath.size > 0) {
            classPath = classPath + ":" + depJars
          } else {
            classPath = depJars
          }
        }

        var (jarFile, depJars) = compiler.createJar(classStr,
          classPath,
          pmmlScalaFile,
          MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_TARGET_DIR"),
          MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MANIFEST_PATH"),
          MetadataAPIImpl.GetMetadataAPIConfig.getProperty("SCALA_HOME"),
          MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAVA_HOME"),
          false,
          compiler_work_dir)

        /* The following check require cleanup at some point */
        if (jarFile.compareToIgnoreCase("Not Set") == 0) {
          throw new ModelCompilationFailedException("Failed to produce the jar file")
        }

        modDef.jarName = jarFile
        modDef.dependencyJarNames = depJars.map(f => { (new java.io.File(f)).getName })
        if (modDef.ver == 0) {
          modDef.ver = 1
        }
        if (modDef.modelType == null) {
          modDef.modelType = "RuleSet"
        }

        modDef.objectDefinition = pmmlStr
        modDef.objectFormat = fXML

      }
      /** end of (modDef != null) */
      // Alles gut! return class and modelDef
      (classStr, modDef)
    } catch {
      case e: Exception => {
        logger.error("Failed to compile the model definition " + e.toString)
        throw new ModelCompilationFailedException(e.getMessage())
      }
      case e: AlreadyExistsException => {
        logger.error("Failed to compile the model definition " + e.toString)
        throw new ModelCompilationFailedException(e.getMessage())
      }
    }
  }

  /**
   * compileMessageDef - Compile Messages/Containers here
   */
  @throws(classOf[MsgCompilationFailedException])
  def compileMessageDef(msgDefStr: String, recompile: Boolean = false): (String, ContainerDef, String) = {
    try {
      val mgr = MdMgr.GetMdMgr
      val msg = new MessageDefImpl()
      logger.debug("Call Message Compiler ....")
      val ((classStrVer, classStrVerJava), msgDef, (classStrNoVer, classStrNoVerJava)) = msg.processMsgDef(msgDefStr, "JSON", mgr, recompile)
      logger.debug("Message Compilation done ...." + JsonSerializer.SerializeObjectToJson(msgDef))

      val nameArray = msgDef.PhysicalName.split('.')
      var realClassName: String = ""
      if (nameArray.length > 0) {
        realClassName = nameArray(nameArray.length - 1)
      }

      // This may be temporary, but need to lowercase all the packages name since they serve as namespaces and MsgDef lowercases them
      var tPackageName = extractPackageNameFromSource(classStrVer)
      var tPackageNameNoVer = extractPackageNameFromSource(classStrNoVer)
      var r_classStrVer = replacePackageNameInSource(classStrVer, tPackageName.toLowerCase)
      var r_classStrVerJava = replacePackageNameInSource(classStrVerJava, tPackageName.toLowerCase)
      var r_classStrNoVer = replacePackageNameInSource(classStrNoVer, tPackageNameNoVer.toLowerCase)
      var r_classStrNoVerJava = replacePackageNameInSource(classStrNoVerJava, tPackageNameNoVer.toLowerCase)

      // Save the message.scala, messageFactory.java and message_local.scala in the work directory
      val msgDefClassFilePath = compiler_work_dir + "/" + realClassName + ".scala"
      dumpStrTextToFile(r_classStrVer, msgDefClassFilePath)
      val msgDefHelperClassFilePath = compiler_work_dir + "/" + realClassName + "Factory.java"
      dumpStrTextToFile(r_classStrVerJava, msgDefHelperClassFilePath)
      val msgDefClassFilePathLocal = compiler_work_dir + "/" + realClassName + "_local.scala"
      dumpStrTextToFile(r_classStrNoVer, msgDefClassFilePathLocal)

      var classPath = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CLASSPATH").trim

      if (msgDef.DependencyJarNames != null) {
        val jarPaths = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS").split(",").toSet
        val depJars = msgDef.DependencyJarNames.map(j => Utils.GetValidJarFile(jarPaths, j)).mkString(":")
        if (classPath != null && classPath.size > 0) {
          classPath = classPath + ":" + depJars
        } else {
          classPath = depJars
        }
      }

      // Call JarCode 2x, first call will generate a versioned Jar File. Second will generate
      // the not versioned one.
      logger.debug("Generating Versioned JarFile for " + msgDefClassFilePath)
      var (status, jarFileVersion) = jarCode(msgDef.nameSpace,
        realClassName,
        msgDef.ver.toString,
        classStrVer,
        classPath,
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_TARGET_DIR"),
        "Test Client",
        msgDefClassFilePath,
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("SCALA_HOME"),
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAVA_HOME"),
        false, "scala",
        classStrVerJava,
        msgDefHelperClassFilePath)
      logger.debug("Status => " + status)

      // This is a java file
      val msgDefHelperClassFilePathLocal = compiler_work_dir + "/" + realClassName + "Factory.java"
      dumpStrTextToFile(r_classStrNoVerJava, msgDefHelperClassFilePathLocal)

      logger.debug("Generating No Versioned JarFile for " + msgDefClassFilePath)
      var (status2, jarFileNoVersion) = jarCode(msgDef.nameSpace,
        realClassName,
        msgDef.ver.toString,
        classStrNoVer,
        classPath,
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_TARGET_DIR"),
        "Test Client",
        msgDefClassFilePathLocal,
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("SCALA_HOME"),
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAVA_HOME"),
        true, "scala",
        classStrNoVerJava,
        msgDefHelperClassFilePathLocal)

      logger.debug("Status => " + status2)

      if (status != 0) {
        logger.error("Compilation of MessgeDef scala file has failed, Message is not added")
        throw new MsgCompilationFailedException(msgDefStr)
      }

      logger.debug("Jar Files => " + jarFileVersion + ", " + jarFileNoVersion)

      if (msgDef.nameSpace == null) {
        msgDef.nameSpace = MetadataAPIImpl.sysNS
      }

      msgDef.jarName = jarFileVersion
      if (msgDef.containerType.isInstanceOf[ContainerTypeDef])
        msgDef.containerType.asInstanceOf[ContainerTypeDef].jarName = jarFileVersion

      msgDef.objectDefinition = msgDefStr
      msgDef.objectFormat = fJSON

      (classStrVer, msgDef, classStrNoVer)
    } catch {
      case e: Exception => {
        logger.error("Failed to compile the message definition " + e.toString)
        throw new MsgCompilationFailedException(e.getMessage())
      }
      case e: AlreadyExistsException => {
        logger.error("Failed to compile the message definition " + e.toString)
        throw new MsgCompilationFailedException(e.getMessage())
      }
    }
  }

  /* 
   * Compile the supplied generated code and jar it, the originating pmml model, and the class output from the 
   * compile.  Add a registration module as well.  Note the classpath dependencies in the manifest.mf file
   * that is also included in the jar.
   */

  private def compile(jarBuildDir: String, scalahome: String, moduleName: String, classpath: String, sourceCode: String, clientName: String, targetClassFolder: String, packageRoot: String, sourceLanguage: String = "scala"): Int =
    {

      var srcFileName: String = ""
      var compileCommand: scala.collection.mutable.Seq[String] = null

      // See what is the source language the source code is in.
      if (sourceLanguage.equals("java")) {
        srcFileName = s"$moduleName.java"
        // need to add the -d option to the JAVAC
        compileCommand = Seq("sh", "-c", s"$scalahome/bin/javac -d $jarBuildDir -cp $classpath $jarBuildDir/$srcFileName")
      } else {
        srcFileName = s"$moduleName.scala"
        compileCommand = Seq("sh", "-c", s"$scalahome/bin/scalac -cp $classpath $jarBuildDir/$srcFileName")
      }
      logger.info("COMPILER_PROXY: Compiling " + srcFileName + "  source code is in " + jarBuildDir)
      // Save the source file in the correct directory.
      createScalaFile(s"$jarBuildDir", srcFileName, sourceCode)

      logger.info(s"compile cmd used: $compileCommand")

      val compileRc = Process(compileCommand).!
      if (compileRc != 0) {
        logger.error(s"Compile for $srcFileName has failed...rc = $compileRc")
        logger.error(s"Command used: $compileCommand")
        compileRc
      } else {
        //  The compiled scala class files are found in com/$client/pmml of the current folder.. mv them to $jarBuildDir.  We 
        //  use the -d option on the java compiler command...  so no need to move anything if java.
        if (sourceLanguage.equalsIgnoreCase("java")) {
          return compileRc
        }

        /** get the top level package name */
        val packageLine: String = sourceCode.takeWhile(ch => ch != '\n')
        val pkgPattern = "package[ \t][ \t]*([A-Za-z0-9_.][A-Za-z0-9_.]+).*".r
        val pkgPattern(fullpkgName) = packageLine
        val topLevelPkg: String = fullpkgName.split('.').head

        val mvCmd: String = s"mv $topLevelPkg $compiler_work_dir/$moduleName/"
        val mvCmdRc: Int = Process(mvCmd).!
        if (mvCmdRc != 0) {
          logger.error(s"unable to move classes to build directory, $jarBuildDir ... rc = $mvCmdRc")
          logger.error(s"cmd used : $mvCmd")
        }
        mvCmdRc
      }
    }

  /**
   *
   */
  private def jarCode(moduleNamespace: String, modelName: String, moduleVersion: String, sourceCode: String, classpath: String, jarTargetDir: String, clientName: String, sourceFilePath: String, scalahome: String, javahome: String, isLocalOnly: Boolean = false, sourceLanguage: String = "scala", helperJavaSource: String = null, helperJavaSourcePath: String = null): (Int, String) =
    {
      var currentWorkFolder: String = modelName
      if (moduleNamespace == null || moduleNamespace.length == 0) throw new ModelCompilationFailedException("Missing Namespace")

      if (isLocalOnly) {
        currentWorkFolder = currentWorkFolder + "_local"
      }

      // Remove this directory with all the junk if it already exists
      val killDir = s"rm -Rf $compiler_work_dir/" + currentWorkFolder
      val killDirRc = Process(killDir).!
      /** remove any work space that may be present from prior failed run  */
      if (killDirRc != 0) {
        logger.error(s"Unable to rm $compiler_work_dir/$modelName ... rc = $killDirRc")
        return (killDirRc, "")
      }

      // Create a new clean directory
      val buildDir = s"mkdir $compiler_work_dir/" + currentWorkFolder
      val tmpdirRc = Process(buildDir).!
      /** create a clean space to work in */
      if (tmpdirRc != 0) {
        logger.error(s"The compilation of the generated source has failed because $buildDir could not be created ... rc = $tmpdirRc")
        return (tmpdirRc, "")
      }

      /** create a copy of the pmml source in the work directory */
      val cpRc = Process(s"cp $sourceFilePath $compiler_work_dir/" + currentWorkFolder).!
      if (cpRc != 0) {
        logger.error(s"Unable to create a copy of the pmml source xml for inclusion in jar ... rc = $cpRc")
        return (cpRc, "")
      }

      /** compile the generated code if its a local copy, make sure we save off the  postfixed _local in the working directory*/
      var sourceName: String = getClassName(sourceCode, sourceLanguage, modelName)
      var cHome: String = scalahome
      if (sourceLanguage.equalsIgnoreCase("java"))
        cHome = javahome
      else {
        if (isLocalOnly) sourceName = modelName + "_local"
      }

      // Compile 
      var packageRoot = (moduleNamespace.split('.'))(0).trim
      val rc = compile(s"$compiler_work_dir/$currentWorkFolder", cHome, sourceName, classpath, sourceCode, clientName, null, packageRoot, sourceLanguage)

      // Bail if compilation filed.
      if (rc != 0) {
        return (rc, "")
      }

      // if helperJavaSource is not null, means we are generating Factory java files.
      if (helperJavaSource != null) {
        val tempClassPath = classpath + ":" + s"$compiler_work_dir/$currentWorkFolder"
        val rc = compile(s"$compiler_work_dir/$currentWorkFolder", javahome, modelName + "Factory", tempClassPath, helperJavaSource, clientName, modelName, packageRoot, "java")

        // Bail if compilation filed.
        if (rc != 0) {
          return (rc, "")
        }
      }

      // Ok, all the compilation has been done, all the class files are present, so start the JARING process.
      var moduleNameJar: String = ""
      if (!isLocalOnly) {
        var d = new java.util.Date()
        var epochTime = d.getTime
        moduleNameJar = moduleNamespace + "_" + modelName + "_" + moduleVersion + "_" + epochTime + ".jar"
      } else {
        moduleNameJar = moduleNamespace + "_" + modelName + ".jar"
      }

      val jarPath = compiler_work_dir + "/" + moduleNameJar
      val jarCmd: String = s"$javahome/bin/jar cvf $jarPath -C $compiler_work_dir/" + currentWorkFolder + "/ ."
      logger.debug(s"jar cmd used: $jarCmd")
      logger.debug(s"Jar $moduleNameJar produced.  Its contents:")

      // Issue the Jar Command - Jar file will be created in the Work Directory.
      val jarRc: Int = Process(jarCmd).!
      if (jarRc != 0) {
        logger.error(s"unable to create jar $moduleNameJar ... rc = $jarRc")
        return (jarRc, "")
      }

      // If this is a dummy jar, then don't move it into the application dir.
      if (moduleVersion.trim.equalsIgnoreCase("V0")) {
        return (0, s"$moduleNameJar")
      }

      // Move the jar to Application Jar directory
      val mvCmd: String = s"mv $jarPath $jarTargetDir/"
      logger.debug(s"mv cmd used: $mvCmd")
      val mvCmdRc: Int = Process(mvCmd).!
      if (mvCmdRc != 0) {
        logger.error(s"unable to move new jar $moduleNameJar to target directory, $jarTargetDir ... rc = $mvCmdRc")
        logger.error(s"cmd used : $mvCmd")
      }
      (0, s"$moduleNameJar")
    }

  /**
   * compileModelFromSource - Generate a jarfile from a sourceCode.
   *
   */
  // The last parameter of generateModelDef represents whether we are recompiling a model due to a change
  // on a dependant message(or container) or compiling for the first time. 
  // MdMgr.MakeModelDef requires this information and function behaves differently depending on whether 
  // we are compiling first time or recompiling an existing model.
  private def generateModelDef(repackagedCode: String, sourceLang: String, pname: String, classPath: String, modelNamespace: String, modelName: String,
    modelVersion: String, msgDefClassFilePath: String, elements: Set[BaseElemDef], originalSource: String,
    deps: scala.collection.immutable.Set[String], typeDeps: List[String], notTypeDeps: scala.collection.immutable.Set[String], recompile: Boolean = false): ModelDef = {
    try {
      // Now, we need to create a real jar file - Need to add an actual Package name with a real Napespace and Version numbers.
      val packageName = modelNamespace + ".V" + MdMgr.ConvertVersionToLong(MdMgr.FormatVersion(modelVersion))
      var packagedSource = "package " + packageName + ";\n " + repackagedCode.substring(repackagedCode.indexOf("import"))
      dumpStrTextToFile(packagedSource, msgDefClassFilePath)

      //Classpath is set by now.
      var (status2, jarFileName) = jarCode(modelNamespace + ".V" + MdMgr.ConvertVersionToLong(MdMgr.FormatVersion(modelVersion)),
        modelName,
        modelVersion,
        packagedSource,
        classPath,
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_TARGET_DIR"),
        "TestClient",
        msgDefClassFilePath,
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("SCALA_HOME"),
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAVA_HOME"),
        false,
        sourceLang)

      /* The following check require cleanup at some point */
      if (status2 > 1) {
        throw new ModelCompilationFailedException("Failed to produce the jar file")
      }

      val depJars = getJarsFromClassPath(classPath)
      
      // figure out the Physical Model Name
      var (dummy1, dummy2, dummy3, pName) = getModelMetadataFromJar(jarFileName, elements, depJars)

      // Create the ModelDef object
      val modDef: ModelDef = MdMgr.GetMdMgr.MakeModelDef(modelNamespace, modelName, "", "RuleSet",
        getInputVarsFromElements(elements),
        List[(String, String, String)](),
        MdMgr.ConvertVersionToLong(MdMgr.FormatVersion(modelVersion)), "",
        deps.toArray[String],
        recompile,false)

      // Need to set some values by hand here.
      modDef.jarName = jarFileName
      modDef.physicalName = pName
      if (sourceLang.equalsIgnoreCase("scala")) modDef.objectFormat = fSCALA else modDef.objectFormat = fJAVA
      modDef.ObjectDefinition(createSavedSourceCode(originalSource, notTypeDeps, typeDeps, pname))
      modDef
    } catch {
      case e: AlreadyExistsException => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Failed to compile the model definition:%s.\nStackTrace:%s".format(e.toString, stackTrace))
        throw e
      }
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("Failed to compile the model definition:%s.\nStackTrace:%s".format(e.toString, stackTrace))
        throw new ModelCompilationFailedException(e.getMessage())
      }
    }

  }

  /**
   * getInputVarsFromElements
   */
  private def getInputVarsFromElements(elements: Set[BaseElemDef]): List[(String, String, String, String, Boolean, String)] = {
    // Input Vars are just Message and Containers here... so the element array has them all!  Create the List of them to be used
    // in modelCreation.
    var inVars: List[(String, String, String, String, Boolean, String)] = List[(String, String, String, String, Boolean, String)]()
    elements.foreach(elem => {
      // inputVars of model definition represents the container/message object on which model depends
      // It is not derived from individual members of these messages and containers
      // However if these members of messages(or containers) are represent message objects,
      // they should be captured recursively. For now, the elements represent all the dependant
      // messages and containers as defined in model-compile-config file(as a json string)
      inVars = (elem.NameSpace, elem.Name, elem.NameSpace, elem.Name, false, null) :: inVars
      /*
      if (elem.asInstanceOf[ContainerDef].containerType.isInstanceOf[StructTypeDef]) {
          elem.asInstanceOf[ContainerDef].containerType.asInstanceOf[StructTypeDef].memberDefs.foreach(attr => { inVars = (attr.NameSpace, attr.Name, attr.typeDef.NameSpace, attr.typeDef.Name, false, null) :: inVars })
      } else if (elem.asInstanceOf[ContainerDef].containerType.isInstanceOf[MappedMsgTypeDef]) {
        elem.asInstanceOf[ContainerDef].containerType.asInstanceOf[MappedMsgTypeDef].attrMap.foreach(attr => { inVars = (attr._2.NameSpace, attr._2.Name, attr._2.typeDef.NameSpace, attr._2.typeDef.Name, false, null) :: inVars })
      }
      */
    })
    inVars
  }

  /**
   *
   * parseSourceForMetadata - this method will parse the source code of a custom scala or java
   *                          model for model metadata.
   *
   *
   */
  private def parseSourceForMetadata(sourceCode: String,
    modelConfigName: String,
    sourceLang: String,
    msgDefClassFilePath: String,
    classPath: String,
    elements: Set[BaseElemDef]): ((String, String, String, String), String, String) = {

    // We have to create a dummy jar file for this so that we can interrogate the generated Object for Modelname
    // and Model Version.  To do this, we create a dummy source with V0 in the package name.
    var packageName = extractPackageNameFromSource(sourceCode)
    var repackagedCode = replacePackageNameInSource(sourceCode, packageName, ".V0;\n")

    // We need to add the imports to the actual TypeDependency Jars...  All the Message,Container, etc Elements
    // have been passed into this def.  
    var typeNamespace: Array[String] = null
    var typeImports: String = ""

    // Create the IMPORT satements for all the dependent Types
    elements.foreach(elem => {
      typeImports = typeImports + "\nimport " + elem.physicalName + ";"
      // Java Models need to import the Factory Classes as well.
      if (sourceLang.equalsIgnoreCase("java"))
        typeImports = typeImports + "\nimport " + elem.physicalName + "Factory;"

    })

    // Remove all the existing reference to the dependent types code, they are not really valid
    elements.foreach(elem => {
      var eName: Array[String] = elem.PhysicalName.split('.').map(_.trim)
      if ((eName.length - 1) > 0) {
        typeNamespace = new Array[String](eName.length - 1)
        for (i <- 0 until typeNamespace.length) {
          typeNamespace(i) = eName(i)
        }
        var typeClassName: String = eName(eName.length - 1)
        // Replace the "import com...ClassName" import statement
        repackagedCode = repackagedCode.replaceAll(("\\s*import\\s*" + typeNamespace.mkString(".") + "[.*]" + typeClassName + "\\;*"), "")
      }
    })

    //typeNamesace contains all the messages and containers 
    if (typeNamespace == null) {
      logger.error("COMPILER_PROXY: Unable to find at least one message in the Metadata for this model")
      throw new MsgCompilationFailedException(modelConfigName)
    }

    //Replace the "import com....*;" statement - JAVA STYLE IMPORT ALL
    repackagedCode = repackagedCode.replaceAll(("\\s*import\\s*" + typeNamespace.mkString(".") + "[.*]" + "\\*\\;*"), "")
    // Replace the "import com...._;" type of statement  - SCALA STYLE IMPORT ALL
    repackagedCode = repackagedCode.replaceAll(("\\s*import\\s*" + typeNamespace.mkString(".") + "[.*]" + "_\\;*"), "")
    // Replace the "import com....{xxx};" type of statement  - SCALA STYLE IMPORT SPECIFIC CLASSES IN BATCH
    repackagedCode = repackagedCode.replaceAll("\\s*import\\s*" + typeNamespace.mkString(".") + "\\.\\{.*?\\}", "")

    // Add all the needed imports - have to recalculate the beginning of the imports in the original source code, since a bunch of imports were
    // removed.
    var finalSourceCode = "package " + packageName + ".V0;\n" + typeImports + "\n" + repackagedCode.substring(repackagedCode.indexOf("import"))
    dumpStrTextToFile(finalSourceCode, msgDefClassFilePath)

    // Need to determine the name of the class file in case of Java - to be able to compile we need to know the public class name. 
    val tempClassName = getClassName(sourceCode, sourceLang, modelConfigName)

    // Create a temporary jarFile file so that we can figure out what the metadata info for this class is. 
    var (status, jarFileName) = jarCode(packageName + ".V0",
      tempClassName,
      "V0",
      finalSourceCode,
      classPath,
      MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_TARGET_DIR"),
      "TestClient",
      msgDefClassFilePath,
      MetadataAPIImpl.GetMetadataAPIConfig.getProperty("SCALA_HOME"),
      MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAVA_HOME"),
      false,
      sourceLang)

    if (status != 0) {
      logger.error("COMPILER_PROXY: Error compiling model source. Unable to create Jar RC = " + status)
      throw new MsgCompilationFailedException(modelConfigName)
    }
    
    val depJars = getJarsFromClassPath(classPath)

    (getModelMetadataFromJar(jarFileName, elements, depJars), finalSourceCode, packageName)

  }

  private def GetAllJarsFromElem(elem: BaseElem, jarPaths: collection.immutable.Set[String]): collection.immutable.Set[String] = {
    var allJars: Array[String] = null

    val jarname = if (elem.JarName == null) "" else elem.JarName.trim

    if (elem.DependencyJarNames != null && elem.DependencyJarNames.size > 0 && jarname.size > 0) {
      allJars = elem.DependencyJarNames :+ jarname
    } else if (elem.DependencyJarNames != null && elem.DependencyJarNames.size > 0) {
      allJars = elem.DependencyJarNames
    } else if (jarname.size > 0) {
      allJars = Array(jarname)
    } else {
      return collection.immutable.Set[String]()
    }

    return allJars.map(j => Utils.GetValidJarFile(jarPaths, j)).toSet
  }

  private[this] def ResolveFactoryOfModelInstanceFactoryDef(clsName: String, fDef: FactoryOfModelInstanceFactoryDef, loaderInfo: KamanjaLoaderInfo): FactoryOfModelInstanceFactory = {
    var isValid = true
    var curClass: Class[_] = null

    try {
      // If required we need to enable this test
      // Convert class name into a class
      var curClz = Class.forName(clsName, true, loaderInfo.loader)
      curClass = curClz

      isValid = false

      while (curClz != null && isValid == false) {
        isValid = Utils.isDerivedFrom(curClz, "com.ligadata.KamanjaBase.FactoryOfModelInstanceFactory")
        if (isValid == false)
          curClz = curClz.getSuperclass()
      }
    } catch {
      case e: Exception => {
        logger.error("Failed to get classname :" + clsName)
        return null
      }
    }

    if (isValid) {
      try {
        var objinst: Any = null
        try {
          // Trying Singleton Object
          val module = loaderInfo.mirror.staticModule(clsName)
          val obj = loaderInfo.mirror.reflectModule(module)
          // curClz.newInstance
          objinst = obj.instance
        } catch {
          case e: Exception => {
            val stackTrace = StackTrace.ThrowableTraceString(e)
            logger.debug("StackTrace:" + stackTrace)
            // Trying Regular Object instantiation
            objinst = curClass.newInstance
          }
        }

        if (objinst.isInstanceOf[FactoryOfModelInstanceFactory]) {
          val factoryObj = objinst.asInstanceOf[FactoryOfModelInstanceFactory]
          val fName = (fDef.NameSpace.trim + "." + fDef.Name.trim).toLowerCase
          logger.info("Created FactoryOfModelInstanceFactory:" + fName)
          return factoryObj
        } else {
          logger.error("Failed to instantiate FactoryOfModelInstanceFactory object :" + clsName)
          logger.debug("Failed to instantiate FactoryOfModelInstanceFactory object :" + clsName + ". ObjType0:" + objinst.getClass.getSimpleName + ". ObjType1:" + objinst.getClass.getCanonicalName)
          return null
        }
      } catch {
        case e: Exception =>
          logger.error("Failed to instantiate FactoryOfModelInstanceFactory object:" + clsName + ". Reason:" + e.getCause + ". Message:" + e.getMessage)
          return null
      }
    }
    return null
  }
  
  private def ResolveAllFactoryOfMdlInstFactoriesObjects(loaderInfo: KamanjaLoaderInfo, jarPaths: collection.immutable.Set[String]): scala.collection.immutable.Map[String, FactoryOfModelInstanceFactory] = {
    val fDefsOptions = MdMgr.GetMdMgr.FactoryOfMdlInstFactories(true, true)
    val tmpFactoryOfMdlInstFactObjects = scala.collection.mutable.Map[String, FactoryOfModelInstanceFactory]()

    if (fDefsOptions != None) {
      val fDefs = fDefsOptions.get

      logger.debug("Found %d FactoryOfModelInstanceFactory objects".format(fDefs.size))
      
      fDefs.foreach(f => {
        val allJars = GetAllJarsFromElem(f, jarPaths)
        if (allJars.size > 0) {
          Utils.LoadJars(allJars.toArray, loaderInfo.loadedJars, loaderInfo.loader)
        }
        var clsName = f.PhysicalName.trim
        var orgClsName = clsName

        logger.debug("FactoryOfModelInstanceFactory. FullName:%s, ClassName:%s".format(f.FullName, clsName))
        var fDefObj = ResolveFactoryOfModelInstanceFactoryDef(clsName, f, loaderInfo)
        if (fDefObj == null) {
          if (clsName.size > 0 && clsName.charAt(clsName.size - 1) != '$') { // if no $ at the end we are taking $
            clsName = clsName + "$"
            fDefObj = ResolveFactoryOfModelInstanceFactoryDef(clsName, f, loaderInfo)
          }
        }

        if (fDefObj != null) {
          tmpFactoryOfMdlInstFactObjects(f.FullName.toLowerCase()) = fDefObj
        } else {
          logger.error("Failed to resolve FactoryOfModelInstanceFactory object:" + f.FullName)
        }
      })
    } else {
      logger.debug("Not Found any FactoryOfModelInstanceFactory objects")
    }

    tmpFactoryOfMdlInstFactObjects.toMap
  }

  private def GetFactoryOfMdlInstanceFactory(fqName: String, loaderInfo: KamanjaLoaderInfo, jarPaths: collection.immutable.Set[String]): FactoryOfModelInstanceFactory = {
    val factObjs = ResolveAllFactoryOfMdlInstFactoriesObjects(loaderInfo, jarPaths)
    factObjs.getOrElse(fqName.toLowerCase(), null)
  }

  def PrepareModelFactory(loaderInfo: KamanjaLoaderInfo, jarPaths: collection.immutable.Set[String], mdl: ModelDef): ModelInstanceFactory = {
      // else Assuming we are already loaded all the required jars
      val factoryOfMdlInstFactoryFqName = "com.ligadata.FactoryOfModelInstanceFactory.JarFactoryOfModelInstanceFactory" //BUGBUG:: We need to get the name from Model Def.
      val factoryOfMdlInstFactory: FactoryOfModelInstanceFactory = GetFactoryOfMdlInstanceFactory(factoryOfMdlInstFactoryFqName, loaderInfo, jarPaths)
      if (factoryOfMdlInstFactory == null) {
        logger.error("FactoryOfModelInstanceFactory %s not found in metadata. Unable to create ModelInstanceFactory for %s".format(factoryOfMdlInstFactoryFqName, mdl.FullName))
        return null
      } else {
        val factory = factoryOfMdlInstFactory.getModelInstanceFactory(mdl, null, loaderInfo, jarPaths)
        return factory
      }
  }

  private def getModelMetadataFromJar(jarFileName: String, elements: Set[BaseElemDef], depJars: List[String]): (String, String, String, String) = {

    // Resolve ModelNames and Models versions - note, the jar file generated is still in the workDirectory.
    val loaderInfo = new KamanjaLoaderInfo()
    // val jarPaths0 = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("COMPILER_WORK_DIR").split(",").toSet
    var jarPaths0 = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS").split(",").toSet
    jarPaths0 = jarPaths0 + MetadataAPIImpl.GetMetadataAPIConfig.getProperty("COMPILER_WORK_DIR")

    var allJars = collection.mutable.Set[String]()

    allJars = allJars + jarFileName
    elements.foreach(elem => {
      if (elem.JarName != null) {
        allJars = allJars + elem.JarName
      }
      if (elem.DependencyJarNames != null) {
        allJars = allJars ++ elem.DependencyJarNames
      }
    })

    if (depJars != null)
      allJars = allJars ++ depJars

    Utils.LoadJars(allJars.map(j => Utils.GetValidJarFile(jarPaths0, j)).toArray, loaderInfo.loadedJars, loaderInfo.loader)

    val jarName0 = Utils.GetValidJarFile(jarPaths0, jarFileName)
    var classNames = Utils.getClasseNamesInJar(jarName0)

    var tempCurClass: Class[_] = null
    classNames.foreach(clsName => {
      try {
        Class.forName(clsName, true, loaderInfo.loader)
      } catch {
        case e: Exception => {
          logger.error("Failed to load Model class %s with Reason:%s Message:%s".format(clsName, e.getCause, e.getMessage))
          throw e // Rethrow
        }
      }

      var curClz = Class.forName(clsName, true, loaderInfo.loader)
      tempCurClass = curClz

      var isModel = false
      while (curClz != null && isModel == false) {
        isModel = Utils.isDerivedFrom(curClz, "com.ligadata.KamanjaBase.ModelInstanceFactory")
        if (isModel == false)
          curClz = curClz.getSuperclass()
      }
      if (isModel) {
        try {
          val mdlDef = MdMgr.GetMdMgr.MakeModelDef("", "", clsName, "jar", List[(String, String, String, String, Boolean, String)](), List[(String, String, String)]()) // Java/Scala/Jar/PMML models are marked as JAR here.
          val mdlFactory = PrepareModelFactory(loaderInfo, jarPaths0, mdlDef)
  
          if (mdlFactory != null) {
            var fullName = mdlFactory.getModelName.split('.')
            return (fullName.dropRight(1).mkString("."), fullName(fullName.length - 1), mdlFactory.getVersion, clsName)
          }

          logger.error("COMPILER_PROXY: Unable to resolve a class Object from " + jarName0)
          throw new MsgCompilationFailedException(clsName)
        } catch {
          case e: Exception => {
            // Trying Regular Object instantiation
            val stackTrace = StackTrace.ThrowableTraceString(e)
            logger.error("COMPILER_PROXY: Exception encountered trying to determin metadata from Class:%s, Reason:%s Message:%s.\nStackTrace:%s".format(clsName, e.getCause, e.getMessage, stackTrace))
            throw new MsgCompilationFailedException(clsName)
          }
        }
  
/*      
        try {
          // If we are dealing with
          var objInst: Any = null
          try {
            // Trying Singleton Object
            val module = loaderInfo.mirror.staticModule(clsName)
            val obj = loaderInfo.mirror.reflectModule(module)

            objInst = obj.instance
            logger.debug("COMPILER_PROXY: " + clsName + " is a Scala Class... ")
          } catch {
            case e: java.lang.NoClassDefFoundError => {
              val stackTrace = StackTrace.ThrowableTraceString(e)
              logger.debug("Stacktrace:" + stackTrace)
              throw e
            }
            case e: Exception => {
              logger.debug("COMPILER_PROXY: " + clsName + " is a Java Class... ")
              objInst = tempCurClass.newInstance
            }
          }
          // Pull the Model metadata out of the actual object here... NameSpace,Name, and Version all come from
          // this temporary class
          var baseModelTrait: com.ligadata.KamanjaBase.ModelInstance = null
          if (objInst.isInstanceOf[com.ligadata.KamanjaBase.ModelInstance]) {
            baseModelTrait = objInst.asInstanceOf[com.ligadata.KamanjaBase.ModelInstance]
            var fullName = baseModelTrait.getModelName.split('.')
            return (fullName.dropRight(1).mkString("."), fullName(fullName.length - 1), baseModelTrait.getVersion, clsName)
          }
          logger.error("COMPILER_PROXY: Unable to resolve a class Object from " + jarName0)
          throw new MsgCompilationFailedException(clsName)
        } catch {
          case e: Exception => {
            // Trying Regular Object instantiation
            val stackTrace = StackTrace.ThrowableTraceString(e)
            logger.error("COMPILER_PROXY: Exception encountered trying to determin metadata from Class:%s, Reason:%s Message:%s.\nStackTrace:%s".format(clsName, e.getCause, e.getMessage, stackTrace))
            throw new MsgCompilationFailedException(clsName)
          }
        }
*/
        
      }
    })
    logger.error("COMPILER_PROXY: No class/objects implementing com.ligadata.KamanjaBase.ModelInstanceFactory was found in the jarred source " + jarFileName)
    throw new MsgCompilationFailedException(jarFileName)
  }

  /**
   * getJavaClassName - pull the java class name fromt he source code so that we can name the
   *                    saved file appropriately.
   */
  private def getClassName(sourceCode: String, sourceLang: String, modelConfigName: String): String = {

    // Need to determine the name of the class file in case of Java - to be able to compile we need to know the public class name. 
    var tempClassName: String = removeUserid(modelConfigName)
    if (sourceLang.equalsIgnoreCase("java")) {
      var publicClassExpr = "\\s*public\\s*class\\s*\\S*\\s*extends".r
      var classMatchResult = publicClassExpr.findFirstMatchIn(sourceCode).getOrElse(null)
      if (classMatchResult != null) {
        var classSubString = sourceCode.substring(classMatchResult.start, classMatchResult.end)
        var bExpr = "\\s*public\\s*class\\s*".r
        var eExpr = "\\s*extends".r
        var match1 = bExpr.findFirstMatchIn(classSubString).get
        var match2 = eExpr.findFirstMatchIn(classSubString).get
        return classSubString.substring(match1.end, match2.start).trim
      }
      return ""
    }
    return tempClassName
  }

  /**
   * getDependencyElement - return a BaseElemDef of the element represented by the key.
   */
  private def getDependencyElement(key: String): Option[BaseElemDef] = {
    var elem: Option[BaseElemDef] = None

    // is it a container?
    var contElemSet = MdMgr.GetMdMgr.Containers(key, true, true).getOrElse(scala.collection.immutable.Set[ContainerDef]())
    if (contElemSet.size > 0) {
      elem = Some(contElemSet.last)
      return elem
    }

    // is it a message
    var msgElemSet = MdMgr.GetMdMgr.Messages(key, true, true).getOrElse(scala.collection.immutable.Set[MessageDef]())
    if (msgElemSet.size > 0) {
      elem = Some(msgElemSet.last)
      return elem
    }
    // Return None if nothing found
    None
  }

  private def getJarsFromClassPath(clsPath: String): List[String] = {
    // Pull all the jar files in the classpath into a set...  THIS WILL CHANGE IN A FUTURE since we
    // dont want to allow developers using the classpath to pass in dependencies.
    var returnList = ArrayBuffer[String]()
    var depArray: Array[String] = clsPath.trim.split(":")
    depArray.foreach(dep => {
      // Check it is the file & the file exists
      val fl = new File(dep.trim)
      if (fl.exists && fl.isFile /* && fl.canRead() */) {
          returnList += fl.getName
      } else {
        var parsedJarpath = dep.split("/")
        var tempSanity = parsedJarpath(parsedJarpath.length - 1).split('.')
        if (tempSanity(tempSanity.length - 1).trim.equalsIgnoreCase("jar"))
          returnList += (tempSanity.mkString(".").trim)
      }
    })
    returnList.toList
  }
  
  /**
   * addDepsFromClassPath - this is probably a temporary metho here for now.  THIS WILL CHANGE IN A FUTURE since we
   * dont want to allow developers using the classpath to pass in dependencies.
   */
  private def addDepsFromClassPath(): List[String] = {
    // Pull all the jar files in the classpath into a set...  THIS WILL CHANGE IN A FUTURE since we
    // dont want to allow developers using the classpath to pass in dependencies.
    getJarsFromClassPath(MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CLASSPATH"))
  }

  /**
   *  buildClassPath
   */
  private def buildClassPath(inDeps: List[String], inMC: List[String], cpDeps: List[String] = null): (String, Set[BaseElemDef], scala.collection.immutable.Set[String], scala.collection.immutable.Set[String]) = {
    var depElems: Set[BaseElemDef] = Set[BaseElemDef]()
    var totalDeps: Set[String] = Set[String]()
    var classPathDeps: Set[String] = Set[String]()

    // Get classpath and jarpath ready
    var classPath = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CLASSPATH").trim
    if (classPath.size == 0) classPath = "."
    val jarPaths = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS").split(",").toSet

    var combinedDeps: scala.collection.immutable.Set[String] = scala.collection.immutable.Set[String]()
    var nonTypeDeps: scala.collection.immutable.Set[String] = scala.collection.immutable.Set[String]()
    if (cpDeps != null) {
      combinedDeps = inDeps.toSet[String] ++ cpDeps.toSet[String]
      nonTypeDeps = inDeps.toSet[String] ++ cpDeps.toSet[String]
    }

    var msgContDepSet: Set[String] = Set[String]()
    var msgJars: String = ""
    if (inMC == null)
      logger.warn("Dependant message/containers were not provided into Model Compiler")
    else {
      inMC.foreach(dep => {
        val elem: BaseElemDef = getDependencyElement(dep).getOrElse(null)
        if (elem == null)
          logger.warn("Unknown dependency " + dep)
        else {
          elem.dependencyJarNames.foreach(aDep => {msgContDepSet = msgContDepSet + aDep})
          depElems += elem
          logger.info("Resolved dependency " + dep + " to " + elem.jarName)
          msgContDepSet = msgContDepSet + elem.jarName
        }
      })

      combinedDeps = combinedDeps ++ msgContDepSet
      msgJars = msgContDepSet.map(j => Utils.GetValidJarFile(jarPaths, j)).mkString(":")
    }

    // Handle the Dependency Jar stuff
    if (inDeps != null) {
      var depJars = inDeps.map(j => Utils.GetValidJarFile(jarPaths, j)).mkString(":")
      if (depJars != null && depJars.length > 0) depJars = depJars + ":" + msgJars else depJars = msgJars
      if (classPath != null && classPath.size > 0) {
        classPath = classPath + ":" + depJars
      } else {
        classPath = depJars
      }
    }
    (classPath, depElems, combinedDeps, nonTypeDeps)
  }

  /**
   * extractPackageNameFromSource - use regex to pull out the package name from a scala or java source code.
   */
  private def extractPackageNameFromSource(sourceCode: String): String = {
    val packageExpression = "\\s*package\\s*".r
    val endPackageEpression = "[\t\n\r\f;]".r
    var indx1: Integer = -1
    var indx2: Integer = -1
    var packageName: String = ""

    // Extract all characters between "package" and "whitespace" or ";"  this is your package name.
    var sMatchResult = packageExpression.findFirstMatchIn(sourceCode).getOrElse(null)
    if (sMatchResult != null) indx1 = sMatchResult.end else logger.error("COMPILER_PROXY: Bad Source. " + sourceCode.subSequence(0, 100))
    var eMatchResult = endPackageEpression.findFirstMatchIn(sourceCode.substring(indx1)).getOrElse(null)
    if (eMatchResult != null) indx2 = eMatchResult.end else logger.error("COMPILER_PROXY: Bad Source. " + sourceCode.subSequence(0, 100))

    // If there are no package present, we will not compile this.
    if (indx1 != -1 && (indx2 + indx1) > indx1) {
      packageName = sourceCode.substring(indx1, (indx2 + indx1 - 1)).trim
      return packageName
    } else {
      logger.error("COMPILER_PROXY: Error compiling model source. unable to find package")
      throw new MsgCompilationFailedException("Unable to determine package name")
    }
  }

  /**
   * replacePackageNameInSource - Replace package name with a different package.  Models will add version, and Messages need to
   *                              change to lowercase.
   */
  private def replacePackageNameInSource(sourceCode: String, newPackageName: String, version: String = ";\n"): String = {

    var codeBeginIndx = sourceCode.indexOf("import")
    var repackagedCode = "package " + newPackageName + version + sourceCode.substring(codeBeginIndx)
    repackagedCode
  }

  /**
   * getClassPath -
   *
   */
  private def getClassPathFromModelConfig(modelName: String, cpDeps: List[String]): (String, Set[BaseElemDef], scala.collection.immutable.Set[String], scala.collection.immutable.Set[String])
           = buildClassPath(MetadataAPIImpl.getModelDependencies(modelName, userId),
                            MetadataAPIImpl.getModelMessagesContainers(modelName, userId),
                            cpDeps)

  /**
   * createSavedSourceCode - use this to create a string that a recompile model can use for recompile when a dependent type
   *                         like a message or container changes. The format is going to be JSON strings as follows:
   *                         { "source":"sourcecode",
   *                           "dependencies":List[String],
   *                           "messagescontainers":List[String],
   *                           "physicalname":"physicalName"
   *                         }
   */
  private def createSavedSourceCode(source: String, deps: scala.collection.immutable.Set[String], typeDeps: List[String], pName: String): String = {
    val json = ((ModelCompilationConstants.SOURCECODE -> source) ~
      (ModelCompilationConstants.DEPENDENCIES -> deps.toList) ~
      (ModelCompilationConstants.TYPES_DEPENDENCIES -> typeDeps) ~
      (ModelCompilationConstants.PHYSICALNAME -> pName))
    compact(render(json))
  }

  private def dumpStrTextToFile(strText: String, filePath: String) {
    val file = new File(filePath);
    val bufferedWriter = new BufferedWriter(new FileWriter(file))
    bufferedWriter.write(strText)
    bufferedWriter.close
  }

  private def writeSrcFile(scalaGeneratedCode: String, scalaSrcTargetPath: String) {
    val file = new File(scalaSrcTargetPath);
    val bufferedWriter = new BufferedWriter(new FileWriter(file))
    bufferedWriter.write(scalaGeneratedCode)
    bufferedWriter.close
  }

  private def removeUserid(in: String): String = {
    var tempNameArray = in.split('.')
    var fileName: String = tempNameArray(tempNameArray.length - 1)
    fileName
  }

  private def createScalaFile(targPath: String, moduleSrcName: String, scalaGeneratedCode: String) {
    val scalaTargetPath = s"$targPath/$moduleSrcName"
    writeSrcFile(scalaGeneratedCode, scalaTargetPath)
  }

}
