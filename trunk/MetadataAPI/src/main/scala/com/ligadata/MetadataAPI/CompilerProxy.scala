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
import org.apache.log4j._
import com.ligadata.fatafat.metadata._
import com.ligadata._
import com.ligadata.messagedef._
import com.ligadata.pmml.compiler._
import com.ligadata.fatafat.metadata.ObjFormatType._
import com.ligadata.Serialize._
import com.ligadata.Exceptions._
import java.util.jar.JarInputStream
import scala.util.control.Breaks._
import scala.reflect.runtime.{ universe => ru }
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import java.net.URL
import java.net.URLClassLoader

/**
 *  MetadataClassLoader - contains the classes that need to be dynamically resolved the the
 *  reflective calls
 */
class CompilerProxyClassLoader(urls: Array[URL], parent: ClassLoader) extends URLClassLoader(urls, parent) {
  override def addURL(url: URL) {
    super.addURL(url)
  }
}

/**
 * MetadataLoaderInfo
 */
class CompilerProxyLoader {
  // class loader
  val loader: CompilerProxyClassLoader = new CompilerProxyClassLoader(ClassLoader.getSystemClassLoader().asInstanceOf[URLClassLoader].getURLs(), getClass().getClassLoader())
  // Loaded jars
  val loadedJars: TreeSet[String] = new TreeSet[String]
  // Get a mirror for reflection
  val mirror: scala.reflect.runtime.universe.Mirror = ru.runtimeMirror(loader)
}

object JarPathsUtils{
  def GetValidJarFile(jarPaths: collection.immutable.Set[String], jarName: String): String = {
    if (jarPaths == null) return jarName // Returning base jarName if no jarpaths found
    jarPaths.foreach(jPath => {
      val fl = new File(jPath + "/" + jarName)
      if (fl.exists) {
        return fl.getPath
      }
    })
    return jarName // Returning base jarName if not found in jar paths
  }
}

// CompilerProxy has utility functions to:
// Call MessageDefinitionCompiler, 
// Call PmmlCompiler, 
// Generate jar files out of output of above compilers
// Persist model definitions and corresponding jar files in Metadata Mgr
// Persist message definitions, and corresponding jar files in Metadata Mgr
class CompilerProxy{

  val loggerName = this.getClass.getName
  lazy val logger = Logger.getLogger(loggerName)
  private var userId: Option[String] = _
  lazy val compiler_work_dir = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("COMPILER_WORK_DIR")

  def setSessionUserId(id: Option[String]): Unit = {userId=id}
  def setLoggerLevel(level: Level){
    logger.setLevel(level);
  }

  /**
   * compileModelFromSource - This gets called from the mainline AddModel... we just know the modelConfigName, and need
   *                          to figure out the build dependencies.
   *
   */
  def compileModelFromSource (sourceCode: String, modelConfigName: String , sourceLang: String = "scala"): ModelDef = {
    try {
      // Figure out the metadata information needed for 
      val additinalDeps = addDepsFromClassPath
      val (classPath, elements, totalDeps, nonTypeDeps) =  getClassPathFromModelConfig(modelConfigName, additinalDeps)
      val msgDefClassFilePath = compiler_work_dir + "/" + removeUserid(modelConfigName) + "."+sourceLang
      val ((modelNamespace, modelName, modelVersion, pname),repackagedCode,tempPackage) = parseSourceForMetadata(sourceCode, modelConfigName,sourceLang,msgDefClassFilePath,classPath,elements)
      return generateModelDef(repackagedCode, sourceLang, pname, classPath, tempPackage, modelName,
        modelVersion, msgDefClassFilePath, elements, sourceCode,
        totalDeps,
        MetadataAPIImpl.getModelMessagesContainers(modelConfigName,None),
        nonTypeDeps)
    } catch {
      case e: Exception => {
        logger.error("COMPILER_PROXY: unable to determine model metadata information during AddModel. ERROR "+e.getMessage)
        logger.error(e.getStackTraceString)
        throw e
      }
    }
  }

  /**
   * compileModelFromSource - This will get called from the recompile path due to a message/container change.  All the info 
   *                          is available.. so just generate the new ModelDef
   *
   */
  def recompileModelFromSource (sourceCode: String, pName: String, deps: List[String], typeDeps: List[String], sourceLang: String = "scala"): ModelDef = {
    try {
      val (classPath, elements, totalDeps, nonTypeDeps) =  buildClassPath(deps, typeDeps)
      val msgDefClassFilePath = compiler_work_dir + "/tempCode."+sourceLang
      val ((modelNamespace, modelName, modelVersion,pname),repackagedCode,tempPackage) = parseSourceForMetadata(sourceCode, "tempCode", sourceLang,msgDefClassFilePath,classPath,elements)
      return generateModelDef(repackagedCode, sourceLang, pname, classPath, tempPackage, modelName,
        modelVersion, msgDefClassFilePath, elements, sourceCode, totalDeps, typeDeps, nonTypeDeps)
    }  catch {
      case e: Exception => {
        logger.error("COMPILER_PROXY: unable to determine model metadata information during recompile. ERROR "+e.getMessage)
        logger.error(e.getStackTraceString)
        throw e
      }
    }
  }


  /**
   *
   */
  def compilePmml(pmmlStr: String, recompile: Boolean = false) : (String,ModelDef) = {
    try{
      /** Ramana, if you set this to true, you will cause the generation of logger.info (...) stmts in generated model */
      var injectLoggingStmts : Boolean = false

      val model_exec_log = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MODEL_EXEC_LOG")
      if(model_exec_log.equalsIgnoreCase("true")){
        injectLoggingStmts = true
      }

      val compiler  = new PmmlCompiler(MdMgr.GetMdMgr, "ligadata", logger, injectLoggingStmts,
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS").split(","))
      val (classStr,modDef) = compiler.compile(pmmlStr,compiler_work_dir,recompile)

      /** if errors were encountered... the model definition is not manufactured.
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
          val depJars = modDef.DependencyJarNames.map(j => JarPathsUtils.GetValidJarFile(jarPaths, j)).mkString(":")
          if (classPath != null && classPath.size > 0) {
            classPath = classPath + ":" + depJars
          } else {
            classPath = depJars
          }
        }

        var (jarFile,depJars) = compiler.createJar(classStr,
          classPath,
          pmmlScalaFile,
          MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_TARGET_DIR"),
          MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MANIFEST_PATH"),
          MetadataAPIImpl.GetMetadataAPIConfig.getProperty("SCALA_HOME"),
          MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAVA_HOME"),
          false,
          compiler_work_dir)

        /* The following check require cleanup at some point */
        if(jarFile.compareToIgnoreCase("Not Set") == 0 ){
          throw new ModelCompilationFailedException("Failed to produce the jar file")
        }

        modDef.jarName = jarFile
        modDef.dependencyJarNames = depJars.map(f => {(new java.io.File(f)).getName})
        if( modDef.ver == 0 ) {
          modDef.ver = 1
        }
        if( modDef.modelType == null) {
          modDef.modelType = "RuleSet"
        }

        modDef.objectDefinition = pmmlStr
        modDef.objectFormat = fXML

      } /** end of (modDef != null) */
      // Alles gut! return class and modelDef
      (classStr,modDef)
    } catch {
      case e:Exception =>{
        logger.error("Failed to compile the model definition " + e.toString)
        throw new ModelCompilationFailedException(e.getMessage())
      }
      case e:AlreadyExistsException => {
        logger.error("Failed to compile the model definition " + e.toString)
        throw new ModelCompilationFailedException(e.getMessage())
      }
    }
  }

  /**
   * compileMessageDef - Compile Messages/Containers here
   */
  @throws(classOf[MsgCompilationFailedException])
  def compileMessageDef(msgDefStr: String,recompile:Boolean = false) : (String,ContainerDef,String) = {
    try{
      val mgr = MdMgr.GetMdMgr
      val msg = new MessageDefImpl()
      logger.debug("Call Message Compiler ....")
      val((classStrVer, classStrVerJava), msgDef, (classStrNoVer, classStrNoVerJava)) = msg.processMsgDef(msgDefStr, "JSON",mgr,recompile)
      logger.debug("Message Compilation done ...." + JsonSerializer.SerializeObjectToJson(msgDef))

      val nameArray = msgDef.PhysicalName.split('.')
      var realClassName: String = ""
      if (nameArray.length > 0) {
        realClassName = nameArray(nameArray.length-1)
      }

      val msgDefFilePath = compiler_work_dir + "/" +realClassName + ".txt"
      dumpStrTextToFile(msgDefStr,msgDefFilePath)

      val msgDefClassFilePath = compiler_work_dir + "/" + realClassName + ".scala"
      dumpStrTextToFile(classStrVer,msgDefClassFilePath)

      // This is a java file
      val msgDefHelperClassFilePath = compiler_work_dir + "/" + realClassName + "Factory.java"
      dumpStrTextToFile(classStrVerJava,msgDefHelperClassFilePath)

      val msgDefFilePathLocal = compiler_work_dir + "/" + realClassName + "_local.txt"
      dumpStrTextToFile(msgDefStr,msgDefFilePathLocal)

      val msgDefClassFilePathLocal = compiler_work_dir + "/" + realClassName + "_local.scala"
      dumpStrTextToFile(classStrNoVer,msgDefClassFilePathLocal)

      var classPath = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CLASSPATH").trim



      if (msgDef.DependencyJarNames != null) {
        val jarPaths = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS").split(",").toSet
        val depJars = msgDef.DependencyJarNames.map(j => JarPathsUtils.GetValidJarFile(jarPaths, j)).mkString(":")
        if (classPath != null && classPath.size > 0) {
          classPath = classPath + ":" + depJars
        } else {
          classPath = depJars
        }
      }

      // Call JarCode 2x, first call will generate a versioned Jar File. Second will generate
      // the not versioned one.
      logger.debug("Generating Versioned JarFile for "+msgDefClassFilePath)
      var(status,jarFileVersion) = jarCode(msgDef.nameSpace,
        realClassName,
        msgDef.ver.toString,
        classStrVer,
        classPath,
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_TARGET_DIR"),
        "Test Client",
        msgDefFilePath,
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("SCALA_HOME"),
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAVA_HOME"),
        false,"scala",
        classStrVerJava,
        msgDefHelperClassFilePath)
      logger.debug("Status => " + status)

      // This is a java file
      val msgDefHelperClassFilePathLocal = compiler_work_dir + "/" + realClassName + "Factory.java"
      dumpStrTextToFile(classStrNoVerJava,msgDefHelperClassFilePathLocal)

      logger.debug("Generating No Versioned JarFile for "+msgDefClassFilePath)
      var(status2,jarFileNoVersion) = jarCode(msgDef.nameSpace,
        realClassName,
        msgDef.ver.toString,
        classStrNoVer,
        classPath,
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_TARGET_DIR"),
        "Test Client",
        msgDefFilePathLocal,
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("SCALA_HOME"),
        MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAVA_HOME"),
        true,"scala",
        classStrNoVerJava,
        msgDefHelperClassFilePathLocal)


      logger.debug("Status => " + status2)

      if( status != 0 ){
        logger.error("Compilation of MessgeDef scala file has failed, Message is not added")
        throw new MsgCompilationFailedException(msgDefStr)
      }

      logger.debug("Jar Files => " + jarFileVersion + ", "+jarFileNoVersion)

      if ( msgDef.nameSpace == null ){
        msgDef.nameSpace = MetadataAPIImpl.sysNS
      }

      msgDef.jarName = jarFileVersion
      if (msgDef.containerType.isInstanceOf[ContainerTypeDef])
        msgDef.containerType.asInstanceOf[ContainerTypeDef].jarName = jarFileVersion

      msgDef.objectDefinition = msgDefStr
      msgDef.objectFormat = fJSON

      (classStrVer,msgDef,classStrNoVer)
    }
    catch{
      case e:Exception =>{
        logger.debug("Failed to compile the message definition " + e.toString)
        e.printStackTrace
        throw new MsgCompilationFailedException(e.getMessage())
      }
      case e:AlreadyExistsException =>{
        logger.debug("Failed to compile the message definition " + e.toString)
        //e.printStackTrace
        throw new MsgCompilationFailedException(e.getMessage())
      }
    }
  }


  /* 
   * Compile the supplied generated code and jar it, the originating pmml model, and the class output from the 
   * compile.  Add a registration module as well.  Note the classpath dependencies in the manifest.mf file
   * that is also included in the jar.
   */

  private def compile (jarBuildDir : String
                       , scalahome : String
                       , moduleName : String
                       , classpath : String
                       , sourceCode : String
                       , clientName : String
                       , targetClassFolder: String
                       , sourceLanguage : String = "scala") : Int =
  {

    var srcFileName: String = ""
    var compileCommand: scala.collection.mutable.Seq[String] = null

    // See what is the source language the source code is in.
    if (sourceLanguage.equals("java")) {
      srcFileName = s"$moduleName.java"
      // need to add the -d option to the JAVAC
      compileCommand = Seq("sh", "-c", s"$scalahome/bin/javac -d $jarBuildDir -cp $classpath $jarBuildDir/$srcFileName")
    }
    else {
      srcFileName = s"$moduleName.scala"
      compileCommand = Seq("sh", "-c", s"$scalahome/bin/scalac -cp $classpath $jarBuildDir/$srcFileName")
    }
    logger.info ("COMPILER_PROXY: Compiling "+srcFileName+ "  source code is in "+ jarBuildDir)
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
      val mvCmd : String = s"mv com $compiler_work_dir/$moduleName/"
      val mvCmdRc : Int = Process(mvCmd).!
      if (mvCmdRc != 0) {
        logger.warn(s"unable to move classes to build directory, $jarBuildDir ... rc = $mvCmdRc")
        logger.warn(s"cmd used : $mvCmd")
      }
      mvCmdRc

    }
  }


  /**
   *
   */
  private def jarCode ( moduleNamespace: String
                        , moduleName: String
                        , moduleVersion: String
                        , sourceCode : String
                        , classpath : String
                        , jarTargetDir : String
                        , clientName : String
                        , sourceFilePath : String
                        , scalahome : String
                        , javahome : String
                        , isLocalOnly: Boolean = false
                        , sourceLanguage: String = "scala"
                        , helperJavaSource: String = null
                        , helperJavaSourcePath: String = null) : (Int, String) =
  {
    var currentWorkFolder: String = moduleName
    if (isLocalOnly) {
      currentWorkFolder = currentWorkFolder + "_local"
    }

    // Remove this directory with all the junk if it already exists
    val killDir = s"rm -Rf $compiler_work_dir/"+currentWorkFolder
    val killDirRc = Process(killDir).! /** remove any work space that may be present from prior failed run  */
    if (killDirRc != 0) {
      logger.error(s"Unable to rm $compiler_work_dir/$moduleName ... rc = $killDirRc")
      return (killDirRc, "")
    }

    // Create a new clean directory
    val buildDir = s"mkdir $compiler_work_dir/"+currentWorkFolder
    val tmpdirRc = Process(buildDir).! /** create a clean space to work in */
    if (tmpdirRc != 0) {
      logger.error(s"The compilation of the generated source has failed because $buildDir could not be created ... rc = $tmpdirRc")
      return (tmpdirRc, "")
    }

    /** create a copy of the pmml source in the work directory */
    val cpRc = Process(s"cp $sourceFilePath $compiler_work_dir/"+currentWorkFolder).!
    if (cpRc != 0) {
      logger.error(s"Unable to create a copy of the pmml source xml for inclusion in jar ... rc = $cpRc")
      return (cpRc, "")
    }

    /** compile the generated code if its a local copy, make sure we save off the  postfixed _local in the working directory*/
    var sourceName: String = moduleName
    var cHome: String = scalahome
    if (sourceLanguage.equalsIgnoreCase("java"))
      cHome = javahome
    else {
      if (isLocalOnly) sourceName = moduleName+"_local"
    }

    // Compile 
    val rc = compile(s"$compiler_work_dir/$currentWorkFolder", cHome, sourceName, classpath, sourceCode, clientName, null, sourceLanguage)

    // Bail if compilation filed.
    if (rc != 0) {
      return (rc, "")
    }

    // if helperJavaSource is not null, means we are generating Factory java files.
    if (helperJavaSource != null) {
      val tempClassPath = classpath+":"+s"$compiler_work_dir/$currentWorkFolder"
      val rc = compile(s"$compiler_work_dir/$currentWorkFolder", javahome, moduleName+"Factory", tempClassPath, helperJavaSource, clientName, moduleName, "java")
      // Bail if compilation filed.
      if (rc != 0) {
        return (rc, "")
      }
    }

    // Ok, all the compilation has been done, all the class files are present, so start the JARING process.
    var  moduleNameJar: String = ""
    if (!isLocalOnly) {
      var d = new java.util.Date()
      var epochTime = d.getTime
      moduleNameJar = moduleNamespace + "_" + moduleName + "_" + moduleVersion + "_" + epochTime + ".jar"
    } else {
      moduleNameJar = moduleNamespace + "_" + moduleName + ".jar"
    }

    val jarPath = compiler_work_dir + "/" + moduleNameJar
    val jarCmd : String = s"$javahome/bin/jar cvf $jarPath -C $compiler_work_dir/"+currentWorkFolder +"/ ."
    logger.debug(s"jar cmd used: $jarCmd")
    logger.debug(s"Jar $moduleNameJar produced.  Its contents:")

    // Issue the Jar Command - Jar file will be created in the Work Directory.
    val jarRc : Int = Process(jarCmd).!
    if (jarRc != 0) {
      logger.error(s"unable to create jar $moduleNameJar ... rc = $jarRc")
      return (jarRc, "")
    }

    // If this is a dummy jar, then don't move it into the application dir.
    if (moduleVersion.trim.equalsIgnoreCase("V0")) {
      return (0, s"$moduleNameJar")
    }

    // Move the jar to Application Jar directory
    val mvCmd : String = s"mv $jarPath $jarTargetDir/"
    logger.debug(s"mv cmd used: $mvCmd")
    val mvCmdRc : Int = Process(mvCmd).!
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
  //private def generateModelDef (sourceCode: String, modelConfigName: String , sourceLang: String = "scala"): ModelDef = {
  private def generateModelDef (repackagedCode: String, sourceLang: String , pname: String, classPath: String, modelNamespace: String, modelName: String,
                                modelVersion: String, msgDefClassFilePath: String, elements: Set[BaseElemDef], originalSource: String,
                                deps: scala.collection.immutable.Set[String], typeDeps: List[String], notTypeDeps: scala.collection.immutable.Set[String]): ModelDef = {
    try {

      // Now, we need to create a real jar file - Need to add an actual Package name with a real Napespace and Version numbers.
      val packageName = modelNamespace +".V"+ MdMgr.ConvertVersionToLong(MdMgr.FormatVersion(modelVersion))
      var packagedSource  = "package "+packageName + ";\n " + repackagedCode.substring(repackagedCode.indexOf("import"))
      dumpStrTextToFile(packagedSource,msgDefClassFilePath)


      //Classpath is set by now.
      var (status2,jarFileName) = jarCode(modelNamespace +".V"+ MdMgr.ConvertVersionToLong(MdMgr.FormatVersion(modelVersion)),
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
      if (status2 > 1 ){
        throw new ModelCompilationFailedException("Failed to produce the jar file")
      }

      // figure out the Physical Model Name
      var (dummy1,dummy2, dummy3, pName) = getModelMetadataFromJar(jarFileName,elements)

      // Create the ModelDef object
      // TODO... for now keep modelNapespace hardcoded to System... since nothing in this product can process a real namespace name
      var modelNamespaceTemp = "System"
      val modDef : ModelDef = MdMgr.GetMdMgr.MakeModelDef(modelNamespaceTemp, modelName,"","RuleSet",
        getInputVarsFromElements(elements),
        List[(String, String, String)]() ,
        MdMgr.ConvertVersionToLong(MdMgr.FormatVersion(modelVersion)),"",
        //deps.toArray[String] ++ elements.map(e=>{e.JarName}).toArray[String],
        deps.toArray[String],
        false
      )

      // Need to set some values by hand here.
      modDef.jarName = jarFileName
      modDef.physicalName = pName
      if (sourceLang.equalsIgnoreCase("scala")) modDef.objectFormat = fSCALA else modDef.objectFormat = fJAVA
      modDef.ObjectDefinition(createSavedSourceCode(originalSource, notTypeDeps, typeDeps, pname))
      modDef
    } catch {
      case e:AlreadyExistsException =>{
        logger.error("Failed to compile the model definition " + e.toString)
        throw e
      }
      case e:Exception =>{
        e.printStackTrace()
        logger.error("Failed to compile the model definition " + e.toString)
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
    elements.foreach (elem => {
      if (elem.asInstanceOf[ContainerDef].containerType.isInstanceOf[StructTypeDef]) {
        elem.asInstanceOf[ContainerDef].containerType.asInstanceOf[StructTypeDef].memberDefs.foreach (attr => {inVars = (attr.NameSpace,attr.Name,elem.NameSpace,elem.Name,false,null) :: inVars })
      }
      else if (elem.asInstanceOf[ContainerDef].containerType.isInstanceOf[MappedMsgTypeDef]) {
        elem.asInstanceOf[ContainerDef].containerType.asInstanceOf[MappedMsgTypeDef].attrMap.foreach (attr => {inVars = (elem.NameSpace,elem.Name,attr._2.NameSpace,attr._2.Name,false,null) :: inVars })
      }
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

  private def parseSourceForMetadata (sourceCode: String,
                                      modelConfigName: String,
                                      sourceLang: String,
                                      msgDefClassFilePath: String,
                                      classPath: String,
                                      elements: Set[BaseElemDef]): ((String, String, String, String), String,String) = {

    // We have to create a dummy jar file for this so that we can interrogate the generated Object for Modelname
    // and Model Version.  To do this, we create a dummy source with V0 in the package name.
    val packageExpression = "\\s*package\\s*".r
    val endPackageEpression = "[\t\n\r\f;]".r
    var indx1: Integer = -1
    var indx2: Integer = -1

    var packageName: String = ""
    var codeBeginIndx = sourceCode.indexOf("import")
    var sMatchResult = packageExpression.findFirstMatchIn(sourceCode).getOrElse(null)
    if (sMatchResult != null) indx1 = sMatchResult.end else logger.error("COMPILER_PROXY: Bad Source. "+ sourceCode.subSequence(0, 100))
    var eMatchResult = endPackageEpression.findFirstMatchIn(sourceCode.substring(indx1)).getOrElse(null)
    if (eMatchResult != null) indx2 = eMatchResult.end else logger.error("COMPILER_PROXY: Bad Source. "+ sourceCode.subSequence(0, 100))

    // If there are no package present, we will not compile this.
    if (indx1 != -1 && (indx2 + indx1) > indx1)
      packageName = sourceCode.substring(indx1,(indx2 + indx1 -1)).trim
    else {
      logger.error("COMPILER_PROXY: Error compiling model source. unable to find package")
      throw new MsgCompilationFailedException(modelConfigName)
    }

    logger.debug("COMPILER_PROXY: packageName at the top of the source is ->"+packageName)
    // Augment the code with the new package name V0, which is invalid inside the engine, but this is really a dummy
    // class.
    var repackagedCode = "package "+ packageName +".V0;\n" + sourceCode.substring(codeBeginIndx)


    // We need to add the imports to the actual TypeDependency Jars...  All the Message,Container, etc Elements
    // have been passed into this def.  
    var typeNamespace: Array[String] = null
    var typeImports: String = ""

    // Create the IMPORT satements for all the dependent Types
    elements.foreach (elem =>{
      typeImports = typeImports + "\nimport "+ elem.physicalName +";"
      // Java Models need to import the Factory Classes as well.
      if (sourceLang.equalsIgnoreCase("java"))
        typeImports = typeImports + "\nimport "+ elem.physicalName +"Factory;"
    })

    // Remove all the existing reference to the dependent types code, they are not really valid
    elements.foreach(elem => {
      var eName: Array[String] = elem.PhysicalName.split('.').map(_.trim)
      if ((eName.length - 1) > 0) {
        typeNamespace = new Array[String](eName.length - 1)
        for (i <- 0 until typeNamespace.length ) {
          typeNamespace(i) = eName(i)
        }
        var typeClassName: String = eName(eName.length - 1)
        // Replace the "import com...ClassName" import statement
        repackagedCode = repackagedCode.replaceAll(("\\s*import\\s*"+typeNamespace.mkString(".")+"[.*]"+typeClassName + "\\;*"), "")
      }
    })
    
    //typeNamesace contains all the messages and containers 
    if (typeNamespace == null) {
      logger.error("COMPILER_PROXY: Unable to find at least one message in the Metadata for this model")
      throw new MsgCompilationFailedException(modelConfigName)
    }
    
    //Replace the "import com....*;" statement - JAVA STYLE IMPORT ALL
    repackagedCode = repackagedCode.replaceAll(("\\s*import\\s*"+typeNamespace.mkString(".")+"[.*]"+"\\*\\;*"), "")
    // Replace the "import com...._;" type of statement  - SCALA STYLE IMPORT ALL
    repackagedCode = repackagedCode.replaceAll(("\\s*import\\s*"+typeNamespace.mkString(".")+"[.*]"+"_\\;*"), "")
    // Replace the "import com....{xxx};" type of statement  - SCALA STYLE IMPORT SPECIFIC CLASSES IN BATCH
    repackagedCode = repackagedCode.replaceAll( "\\s*import\\s*"+typeNamespace.mkString(".")+"\\.\\{.*?\\}", "")

    // Add all the needed imports - have to recalculate the beginning of the imports in the original source code, since a bunch of imports were
    // removed.
    var finalSourceCode = "package "+ packageName +".V0;\n" + typeImports +"\n" + repackagedCode.substring(repackagedCode.indexOf("import"))
    dumpStrTextToFile(finalSourceCode,msgDefClassFilePath)

    // Need to determine the name of the class file in case of Java - to be able to compile we need to know the public class name. 
    var tempClassName: String = removeUserid(modelConfigName)
    if (sourceLang.equalsIgnoreCase("java")) {
      tempClassName = getJavaClassName(sourceCode)
    }

    // Create a temporary jarFile file so that we can figure out what the metadata info for this class is. 
    var (status,jarFileName) = jarCode(packageName+".V0",
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
      logger.error("COMPILER_PROXY: Error compiling model source. Unable to create Jar RC = "+status)
      throw new MsgCompilationFailedException(modelConfigName)
    }

    ( getModelMetadataFromJar(jarFileName, elements),finalSourceCode, packageName)

  }


  private def getModelMetadataFromJar(jarFileName: String, elements: Set[BaseElemDef]): (String, String, String, String) = {

    // Resolve ModelNames and Models versions - note, the jar file generated is still in the workDirectory.
    val classLoader: CompilerProxyLoader = new CompilerProxyLoader
    // val jarPaths0 = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("COMPILER_WORK_DIR").split(",").toSet
    var jarPaths0 = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS").split(",").toSet
    jarPaths0 = jarPaths0 + MetadataAPIImpl.GetMetadataAPIConfig.getProperty("COMPILER_WORK_DIR")

    // Add this temp jar...
    val jarName0 = JarPathsUtils.GetValidJarFile(jarPaths0, jarFileName)
    loadJarFile(jarName0, classLoader)
    elements.foreach(elem => {
      val jarNameType =  JarPathsUtils.GetValidJarFile(jarPaths0, elem.JarName)
      loadJarFile(jarNameType, classLoader)
    })


    var classNames = getClasseNamesInJar(jarName0)
    var tempCurClass: Class[_] = null
    classNames.foreach (clsName => {
      var curClz = Class.forName(clsName, true, classLoader.loader)
      tempCurClass = curClz

      var isModel = false
      while (curClz != null && isModel == false) {
        isModel = isDerivedFrom(curClz, "com.ligadata.FatafatBase.ModelBaseObj")
        if (isModel == false)
          curClz = curClz.getSuperclass()
      }
      if (isModel) {
        try {
          // If we are dealing with
          var objInst: Any = null
          try {
            // Trying Singleton Object
            val module = classLoader.mirror.staticModule(clsName)
            val obj = classLoader.mirror.reflectModule(module)

            objInst = obj.instance
            logger.debug("COMPILER_PROXY: "+clsName+" is a Scala Class... ")
          } catch {
            case e: java.lang.NoClassDefFoundError => {
              e.printStackTrace
              throw e
            }
            case e: Exception => {
              logger.debug("COMPILER_PROXY: "+clsName+" is a Java Class... ")
              objInst = tempCurClass.newInstance
            }
          }
          // Pull the Model metadata out of the actual object here... NameSpace,Name, and Version all come from
          // this temporary class
          var baseModelTrait: com.ligadata.FatafatBase.ModelBaseObj = null
          if (objInst.isInstanceOf[com.ligadata.FatafatBase.ModelBaseObj]) {
            baseModelTrait = objInst.asInstanceOf[com.ligadata.FatafatBase.ModelBaseObj]
            var fullName = baseModelTrait.ModelName.split('.')
            return (fullName.dropRight(1).mkString("."), fullName(fullName.length-1), baseModelTrait.Version, clsName)
          }
          logger.error("COMPILER_PROXY: Unable to resolve a class Object from "+jarName0)
          throw new MsgCompilationFailedException(clsName)
        } catch {
          case e: Exception => {
            // Trying Regular Object instantiation
            logger.error("COMPILER_PROXY: Exception encountered trying to determin metadata from "+clsName)
            e.printStackTrace()
            throw new MsgCompilationFailedException(clsName)
          }
        }
      }
    })
    logger.error("COMPILER_PROXY: No class/objects implementing com.ligadata.FatafatBase.ModelBaseObj was found in the jarred source "+jarFileName)
    throw new MsgCompilationFailedException(jarFileName)
  }




  /**
   * getClasseNamesInJar - A utility method to grab all class files from the jarfile
   */
  private  def getClasseNamesInJar(jarName: String): Array[String] = {
    try {
      val jarFile = new JarInputStream(new FileInputStream(jarName))
      val classes = new ArrayBuffer[String]
      val taillen = ".class".length()
      breakable {
        while (true) {
          val jarEntry = jarFile.getNextJarEntry();
          if (jarEntry == null)
            break;
          if (jarEntry.getName().endsWith(".class") && !jarEntry.isDirectory()) {
            val clsnm: String = jarEntry.getName().replaceAll("/", ".").trim // Replace / with .
            classes += clsnm.substring(0, clsnm.length() - taillen)
          }
        }
      }
      return classes.toArray
    } catch {
      case e: Exception =>
        e.printStackTrace();
        return Array[String]()
    }
  }

  /**
   * isDerivedFrom - A utility method to see if a class is a cubclass of a given class
   */
  private def isDerivedFrom(clz: Class[_], clsName: String): Boolean = {
    var isIt: Boolean = false

    val interfecs = clz.getInterfaces()
    logger.debug("Interfaces => " + interfecs.length + ",isDerivedFrom: Class=>" + clsName)

    breakable {
      for (intf <- interfecs) {
        logger.debug("Interface:" + intf.getName())
        if (intf.getName().equals(clsName)) {
          isIt = true
          break
        }
      }
    }
    isIt
  }

  /**
   * getJavaClassName - pull the java class name fromt he source code so that we can name the 
   *                    saved file appropriately.  
   */
  private def getJavaClassName(sourceCode: String):String = {
    var publicClassExpr = "\\s*public\\s*class\\s*\\S*\\s*extends".r
    var classMatchResult = publicClassExpr.findFirstMatchIn(sourceCode).getOrElse(null)
    if (classMatchResult != null) {
      var classSubString = sourceCode.substring(classMatchResult.start,classMatchResult.end)
      var bExpr = "\\s*public\\s*class\\s*".r
      var eExpr = "\\s*extends".r
      var match1 = bExpr.findFirstMatchIn(classSubString).get
      var match2 = eExpr.findFirstMatchIn(classSubString).get
      return classSubString.substring(match1.end,match2.start).trim
    }
    ""
  }

  /**
   * getDependencyElement - return a BaseElemDef of the element represented by the key.
   */
  private def getDependencyElement (key: String) : Option[BaseElemDef] = {
    var elem: Option[BaseElemDef] = None

    // is it a container?
    var contElemSet =  MdMgr.GetMdMgr.Containers(key, true, true).getOrElse(scala.collection.immutable.Set[ContainerDef]())
    if (contElemSet.size > 0) {
      elem = Some(contElemSet.last)
      return elem
    }

    // is it a message
    var msgElemSet =  MdMgr.GetMdMgr.Messages(key, true, true).getOrElse(scala.collection.immutable.Set[MessageDef]())
    if (msgElemSet.size > 0) {
      elem = Some(msgElemSet.last)
      return elem
    }
    // Return None if nothing found
    None
  }

  /**
   *
   */
  private def loadJarFile (jarName: String, classLoader: CompilerProxyLoader): Unit = {
    val fl = new File(jarName)
    if (fl.exists) {
      try {
        classLoader.loader.addURL(fl.toURI().toURL())
        classLoader.loadedJars += fl.getPath()
      } catch {
        case e: Exception => {
          logger.error("Failed to add "+jarName + " due to internal exception " + e.printStackTrace)
          return
        }
      }
    } else {
      logger.error("Unable to locate Jar '"+jarName+"'")
      return
    }
  }

  /**
   * addDepsFromClassPath - this is probably a temporary metho here for now.  THIS WILL CHANGE IN A FUTURE since we
   * dont want to allow developers using the classpath to pass in dependencies.
   */
  private def addDepsFromClassPath(): List[String] = {
    // Pull all the jar files in the classpath into a set...  THIS WILL CHANGE IN A FUTURE since we
    // dont want to allow developers using the classpath to pass in dependencies.
    var returnList: List[String] = List[String]()
    var depArray: Array[String] = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CLASSPATH").trim.split(":")
    depArray.foreach(dep => {
      var parsedJarpath = dep.split("/")
      var tempSanity = parsedJarpath(parsedJarpath.length - 1).split('.')
      if (tempSanity(tempSanity.length-1).trim.equalsIgnoreCase("jar"))
        returnList = returnList ::: List[String](tempSanity.mkString(".").trim)
    })
    returnList
  }


  /**
   *  buildClassPath 
   */
  private def buildClassPath(inDeps: List[String], inMC: List[String], cpDeps: List[String] = null):(String,Set[BaseElemDef],scala.collection.immutable.Set[String],scala.collection.immutable.Set[String]) = {

    inDeps.foreach { x => println("Dep-> "+x) }
    cpDeps.foreach { x => println("CP-> "+x) }
    
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
      combinedDeps = inDeps.toSet[String] //++ cpDeps.toSet[String]
      nonTypeDeps = inDeps.toSet[String] //++ cpDeps.toSet[String]
    }

    var msgContDepSet: Set[String] = Set[String]()
    var msgJars: String = ""
    if (inMC == null)
      logger.warn("Dependant message/containers were not provided into Model Compiler")
    else {
      inMC.foreach (dep => {
        val elem: BaseElemDef = getDependencyElement(dep).getOrElse(null)
        if (elem == null)
          logger.warn("Unknown dependency "+dep)
        else {
          depElems += elem
          msgContDepSet = msgContDepSet + elem.jarName
        }
      })
      combinedDeps = combinedDeps ++ msgContDepSet
      msgJars = msgContDepSet.map(j => JarPathsUtils.GetValidJarFile(jarPaths, j)).mkString(":")
    }

    // Handle the Dependency Jar stuff
    if (inDeps != null) {
      var depJars = inDeps.map(j => JarPathsUtils.GetValidJarFile(jarPaths, j)).mkString(":")
      if (depJars != null && depJars.length > 0) depJars = depJars + ":" + msgJars else depJars = msgJars
      classPath = depJars
   //   if (classPath != null && classPath.size > 0) {
   //     classPath = classPath + ":" + depJars
   //   } else {
   //     classPath = depJars
   //   }
    }
    println("COMPILER_PROXY: List of all the dependencies needed to compile this model")
    println("==========================================")
    combinedDeps.foreach (x=>println(x))
    println("=============End Of list==================")
    (classPath,depElems,combinedDeps,nonTypeDeps)
  }

  /**
   * getClassPath - 
   *
   */
  private def getClassPathFromModelConfig(modelName: String, cpDeps: List[String]): (String,Set[BaseElemDef], scala.collection.immutable.Set[String],scala.collection.immutable.Set[String]) =  buildClassPath(MetadataAPIImpl.getModelDependencies(modelName,userId),
    MetadataAPIImpl.getModelMessagesContainers(modelName,userId),
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
    println("COMILER_PROXY: recording for future recompile of the model")
    println("==========================================")
    println("Dependencies:")
    deps.foreach(x=>{println(x)})
    println("==========================================")
    println("Types:")
    typeDeps.foreach(x=>{println(x)})
    println("==========================================")

    val json = ((ModelCompilationConstants.SOURCECODE -> source) ~
      (ModelCompilationConstants.DEPENDENCIES -> deps.toList) ~
      (ModelCompilationConstants.TYPES_DEPENDENCIES -> typeDeps) ~
      (ModelCompilationConstants.PHYSICALNAME -> pName))
    compact(render(json))
  }


  private def dumpStrTextToFile(strText : String, filePath : String) {
    val file = new File(filePath);
    val bufferedWriter = new BufferedWriter(new FileWriter(file))
    bufferedWriter.write(strText)
    bufferedWriter.close
  }

  private def writeSrcFile(scalaGeneratedCode : String, scalaSrcTargetPath : String) {
    val file = new File(scalaSrcTargetPath);
    val bufferedWriter = new BufferedWriter(new FileWriter(file))
    bufferedWriter.write(scalaGeneratedCode)
    bufferedWriter.close
  }

  private def removeUserid (in:String) : String = {
    var tempNameArray = in.split('.')
    var fileName: String = tempNameArray(tempNameArray.length - 1)
    fileName
  }

  private def createScalaFile(targPath : String, moduleSrcName : String, scalaGeneratedCode : String) {
    val scalaTargetPath = s"$targPath/$moduleSrcName"
    writeSrcFile(scalaGeneratedCode, scalaTargetPath)
  }

}
