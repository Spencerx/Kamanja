
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

package com.ligadata.Utils

import com.google.common.base.Optional
import java.io.{ InputStream, FileInputStream, File }
import java.util.Properties
import java.util.zip.GZIPInputStream
import java.nio.file.{ Paths, Files }
import java.util.jar.JarInputStream
import com.ligadata.Exceptions.StackTrace

import scala.util.control.Breaks._
import scala.collection.mutable.TreeSet
import org.apache.logging.log4j.{ Logger, LogManager }
import scala.collection.mutable.ArrayBuffer

object Utils {
  private val logger = LogManager.getLogger(getClass)

  def SimpDateFmtTimeFromMs(tmMs: Long): String = {
    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date(tmMs))
  }

  def GetCurDtTmStr: String = {
    SimpDateFmtTimeFromMs(GetCurDtTmInMs)
  }

  def GetCurDtTmInMs: Long = {
    System.currentTimeMillis
  }

  def elapsed[A](f: => A): (Long, A) = {
    val s = System.nanoTime
    val ret = f
    ((System.nanoTime - s), ret)
  }

  def toArrayStrings(args: String*): String = {
    val v = new StringBuilder(1024)
    // args.toArray.toString
    var cnt = 0
    for (arg <- args) {
      if (cnt > 0)
        v.append(",")
      cnt += 1
      if (arg != null && arg.length > 0)
        v.append(arg)
      else
        v.append("")
    }
    v.toString
  }

  def toArrayValidStrings(args: String*): String = {
    val v = new StringBuilder(1024)
    // args.toArray.toString
    var nextaddcomma = false
    for (arg <- args) {
      if (arg != null && arg.length > 0) {
        if (nextaddcomma)
          v.append(",")
        v.append(arg)
        nextaddcomma = true
      }
    }
    v.toString
  }

  def loadConfiguration(configFile: String, keysLowerCase: Boolean): (Properties, String) = {
    var configs: Properties = null
    var failStr: String = null
    try {
      val file: File = new File(configFile);
      if (file.exists()) {
        val input: InputStream = new FileInputStream(file)
        try {
          // Load configuration
          configs = new Properties()
          configs.load(input);
        } catch {
          case e: Exception =>
            failStr = "Failed to load configuration. Message:" + e.getMessage
            configs = null
        } finally {
          input.close();
        }
        if (keysLowerCase && configs != null) {
          val it = configs.entrySet().iterator()
          val lowercaseconfigs = new Properties()
          while (it.hasNext()) {
            val entry = it.next();
            lowercaseconfigs.setProperty(entry.getKey().asInstanceOf[String].toLowerCase, entry.getValue().asInstanceOf[String])
          }
          configs = lowercaseconfigs
        }
      } else {
        failStr = "Configuration file not found : " + configFile
        configs = null
      }
    } catch {
      case e: Exception =>
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.error("StackTrace:" + stackTrace)
        failStr = "Invalid Configuration. Message: " + e.getMessage() + "\nStackTrace:" + stackTrace
        configs = null
    }
    return (configs, failStr)
  }

  def optionToOptional[T](option: Option[T]): Optional[T] = {
    option match {
      case Some(value) => Optional.of(value)
      case None        => Optional.absent()
    }
  }

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

  def getClasseNamesInJar(jarName: String): Array[String] = {
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
        return null
    }
  }

  /**
   * LoadJars - Load jars into Custom Class Loader.
   *   Args:
   *     jars - Full Path Jar Names
   *     loadedJars - Already Loaded Jars (Full Paths)
   *     loader - Custom Class Loader
   */
  def LoadJars(jars: Array[String], loadedJars: TreeSet[String], loader: KamanjaClassLoader): Boolean = {
    if (jars != null) {
      // Loading all jars
      for (j <- jars) {
        logger.debug("Processing Jar " + j.trim)
        val fl = new File(j.trim)
        if (fl.exists) {
          try {
            if (loadedJars(fl.getPath())) {
              logger.debug("Jar " + j.trim + " already loaded to class path.")
            } else {
              loader.addURL(fl.toURI().toURL())
              logger.debug("Jar " + j.trim + " added to class path.")
              loadedJars += fl.getPath()
            }
          } catch {
            case e: Exception => {
              logger.error("Jar " + j.trim + " failed added to class path. Reason:%s Message:%s".format(e.getCause, e.getMessage))
              return false
            }
          }
        } else {
          logger.error("Jar " + j.trim + " not found")
          return false
        }
      }
    }

    true
  }

  /**
   * isDerivedFrom - A utility method to see if a class is a cubclass of a given class
   */
  def isDerivedFrom(clz: Class[_], clsName: String): Boolean = {
    var isIt: Boolean = false

    val interfecs = clz.getInterfaces()
    logger.debug("Interfaces => " + interfecs.length + ",isDerivedFrom: Class=>" + clsName)

    breakable {
      for (intf <- interfecs) {
        val intfName = intf.getName()
        logger.debug("Interface:" + intfName)
        if (intfName.equals(clsName)) {
          isIt = true
          break
        }
      }
    }

    if (isIt == false) {
      val superclass = clz.getSuperclass
      if (superclass != null) {
        val scName = superclass.getName()
        logger.debug("SuperClass => " + scName)
        if (scName.equals(clsName)) {
          isIt = true
        }
      }
    }

    isIt
  }

  def getClassNameJarNameDepJarsFromJson(parsed_json: Map[String, Any]): (String, String, Array[String]) = {
    val className = parsed_json.getOrElse("ClassName", "").toString.trim
    val jarName = parsed_json.getOrElse("JarName", "").toString.trim
    val dependencyJars = parsed_json.getOrElse("DependencyJars", null)
    if (dependencyJars != null) {
      if (dependencyJars.isInstanceOf[Set[_]]) {
        val djs = dependencyJars.asInstanceOf[Set[String]]
        return (className, jarName, djs.toArray)
      }
      if (dependencyJars.isInstanceOf[List[_]]) {
        val djs = dependencyJars.asInstanceOf[List[String]]
        return (className, jarName, djs.toArray)
      }
      if (dependencyJars.isInstanceOf[Array[_]]) {
        val djs = dependencyJars.asInstanceOf[Array[String]]
        return (className, jarName, djs)
      }
    }
    return (className, jarName, Array[String]())
  }

  // Each jar should be fully qualified path (physical path)
  def CheckForNonExistanceJars(allJarsToBeValidated: Set[String]): Set[String] = {
    val nonExistsJars = scala.collection.mutable.Set[String]();
    allJarsToBeValidated.foreach(j => {
      val fl = new File(j)
      if (fl.exists == false || fl.canRead == false || fl.isFile == false) {
        nonExistsJars += j
      }
      // else Valid file
    })
    nonExistsJars.toSet
  }
}
