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

package com.ligadata.FactoryOfModelInstanceFactory

import com.ligadata.kamanja.metadata.{ ModelDef, BaseElem }
import com.ligadata.KamanjaBase.{ FactoryOfModelInstanceFactory, ModelInstanceFactory, EnvContext, NodeContext }
import com.ligadata.Utils.{ Utils, KamanjaClassLoader, KamanjaLoaderInfo }
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.Exceptions.StackTrace

object JarFactoryOfModelInstanceFactory extends FactoryOfModelInstanceFactory {
  private[this] val loggerName = this.getClass.getName()
  private[this] val LOG = LogManager.getLogger(loggerName)

  private[this] def LoadJarIfNeeded(metadataLoader: KamanjaLoaderInfo, jarPaths: collection.immutable.Set[String], elem: BaseElem): Boolean = {
    val allJars = GetAllJarsFromElem(jarPaths, elem)
    if (allJars.size > 0) {
      return Utils.LoadJars(allJars.toArray, metadataLoader.loadedJars, metadataLoader.loader)
    } else {
      return true
    }
  }

  private[this] def GetAllJarsFromElem(jarPaths: collection.immutable.Set[String], elem: BaseElem): Set[String] = {
    var allJars: Array[String] = null

    val jarname = if (elem.JarName == null) "" else elem.JarName.trim

    if (elem.DependencyJarNames != null && elem.DependencyJarNames.size > 0 && jarname.size > 0) {
      allJars = elem.DependencyJarNames :+ jarname
    } else if (elem.DependencyJarNames != null && elem.DependencyJarNames.size > 0) {
      allJars = elem.DependencyJarNames
    } else if (jarname.size > 0) {
      allJars = Array(jarname)
    } else {
      return Set[String]()
    }

    return allJars.map(j => Utils.GetValidJarFile(jarPaths, j)).toSet
  }

  private[this] def CheckAndPrepModelFactory(nodeContext: NodeContext, metadataLoader: KamanjaLoaderInfo, clsName: String, mdl: ModelDef): ModelInstanceFactory = {
    var isModel = true
    var curClass: Class[_] = null

    try {
      // Convert class name into a class
      var curClz = Class.forName(clsName, true, metadataLoader.loader)
      curClass = curClz

      isModel = false

      while (curClz != null && isModel == false) {
        isModel = Utils.isDerivedFrom(curClz, "com.ligadata.KamanjaBase.ModelInstanceFactory")
        if (isModel == false)
          curClz = curClz.getSuperclass()
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.error("Failed to get classname %s. Reason:%s, Cause:%s\nStackTrace:%s".format(clsName, e.getMessage, e.getCause, stackTrace))
        return null
      }
    }

    if (isModel) {
      try {
        var objinst: Any = null
        try {
          // Trying Regular class instantiation
          objinst = curClass.getConstructor(classOf[ModelDef], classOf[NodeContext]).newInstance(mdl, nodeContext)
        } catch {
          case e: Exception => {
            val stackTrace = StackTrace.ThrowableTraceString(e)
            LOG.error("Failed to instantiate ModelInstanceFactory. Reason:%s, Cause:%s\nStackTrace:%s".format(e.getMessage, e.getCause, stackTrace))
            return null
          }
        }

        if (objinst.isInstanceOf[ModelInstanceFactory]) {
          val modelobj = objinst.asInstanceOf[ModelInstanceFactory]
          val mdlName = (mdl.NameSpace.trim + "." + mdl.Name.trim).toLowerCase
          LOG.info("Created Model:" + mdlName)
          return modelobj
        }
        LOG.error("Failed to instantiate ModelInstanceFactory :" + clsName + ". ObjType0:" + objinst.getClass.getSimpleName + ". ObjType1:" + objinst.getClass.getCanonicalName)
        return null
      } catch {
        case e: Exception =>
          val stackTrace = StackTrace.ThrowableTraceString(e)
          LOG.error("Failed to instantiate ModelInstanceFactory for classname:%s. Reason:%s, Cause:%s\nStackTrace:%s".format(clsName, e.getMessage, e.getCause, stackTrace))
          return null
      }
    }
    return null
  }

  override def getModelInstanceFactory(modelDef: ModelDef, nodeContext: NodeContext, loaderInfo: KamanjaLoaderInfo, jarPaths: collection.immutable.Set[String]): ModelInstanceFactory = {
    LoadJarIfNeeded(loaderInfo, jarPaths, modelDef)

    var clsName = modelDef.PhysicalName.trim
    var orgClsName = clsName

    var mdlInstanceFactory = CheckAndPrepModelFactory(nodeContext, loaderInfo, clsName, modelDef)

    if (mdlInstanceFactory == null) {
      LOG.error("Failed to instantiate ModelInstanceFactory :" + orgClsName)
    }

    mdlInstanceFactory
  }

  // Input: Model String, input & output Message Names. Output: ModelDef
  override def prepareModel(nodeContext: NodeContext, modelString: String, inputMessage: String, outputMessage: String, loaderInfo: KamanjaLoaderInfo, jarPaths: collection.immutable.Set[String]): ModelDef = {
    null
  }
}

