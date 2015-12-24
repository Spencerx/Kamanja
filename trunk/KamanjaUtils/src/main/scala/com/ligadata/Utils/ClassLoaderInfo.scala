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

import scala.collection.mutable.TreeSet
import scala.reflect.runtime.{ universe => ru }
import java.net.{ URL, URLClassLoader }
import org.apache.logging.log4j.{ Logger, LogManager }
import scala.collection.mutable.ArrayBuffer

/*
 * Kamanja custom ClassLoader. We can use this as Parent first (default, which is default for java also) and Parent Last.
 *   So, if same class loaded multiple times in the class loaders hierarchy, 
 *     with parent first it gets the first loaded class.
 *     with parent last it gets the most recent loaded class.
 */
class KamanjaClassLoader(val systemClassLoader: URLClassLoader, val parent: KamanjaClassLoader, val currentClassClassLoader: ClassLoader, val parentLast: Boolean)
  extends URLClassLoader(if (systemClassLoader != null) systemClassLoader.getURLs() else Array[URL](), if (parentLast == false && parent != null) parent else currentClassClassLoader) {
  private val LOG = LogManager.getLogger(getClass)

  override def addURL(url: URL) {
    LOG.debug("Adding URL:" + url.getPath + " to default class loader")
    super.addURL(url)
  }

  protected override def loadClass(className: String, resolve: Boolean): Class[_] = this.synchronized {
    LOG.debug("Trying to load class:" + className + ", resolve:" + resolve)

    if (parentLast) {
      try {
        return super.loadClass(className, resolve) // First try in local
      } catch {
        case e: ClassNotFoundException => {
          if (parent != null)
            return parent.loadClass(className) // If not found, go to Parent
          else
            throw e
        }
      }
    } else {
      return super.loadClass(className, resolve)
    }
  }

  override def getResource(name: String): URL = {
    var url: URL = null;
    LOG.debug("Trying to getResource:" + name)

    // This call to getResource may eventually call findResource again, in case the parent doesn't find anything.

    if (parentLast) {
      url = super.getResource(name) // First try in local
      if (url == null && parent != null)
        url = parent.getResource(name) // If not found, go to Parent
    } else {
      url = super.getResource(name)
    }

    LOG.debug("URL is:" + url)
    url
  }

  override def getResources(name: String): java.util.Enumeration[URL] = {
    var systemUrls: java.util.Enumeration[URL] = null
    LOG.debug("Trying to getResources:" + name)

    var urls = ArrayBuffer[URL]()

    val superUrls = super.getResources(name)
    var parentUrls: java.util.Enumeration[URL] = null

    if (parentLast && parent != null) {
      parentUrls = parent.getResources(name)
    }

    if (superUrls != null) {
      while (superUrls.hasMoreElements()) {
        urls += superUrls.nextElement()
      }
    }

    if (parentUrls != null) {
      while (parentUrls.hasMoreElements()) {
        urls += parentUrls.nextElement()
      }
    }

    LOG.debug("Found %d URLs".format(urls.size))
    new java.util.Enumeration[URL]() {
      var iter = urls.iterator
      def hasMoreElements(): Boolean = iter.hasNext
      def nextElement(): URL = iter.next()
    }
  }
}

/*
 * KamanjaLoaderInfo is just wrapper for ClassLoader to maintain already loaded jars.
 */
class KamanjaLoaderInfo(val parent: KamanjaLoaderInfo = null, val useParentloadedJars: Boolean = false, val parentLast: Boolean = false) {
  // Parent class loader
  val parentKamanLoader: KamanjaClassLoader = if (parent != null) parent.loader else null

  // Class Loader
  val loader = new KamanjaClassLoader(ClassLoader.getSystemClassLoader().asInstanceOf[URLClassLoader], parentKamanLoader, getClass().getClassLoader(), parentLast)

  // Loaded jars
  val loadedJars: TreeSet[String] = if (useParentloadedJars && parent != null) parent.loadedJars else new TreeSet[String]

  // Get a mirror for reflection
  val mirror: reflect.runtime.universe.Mirror = ru.runtimeMirror(loader)
}

