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

package com.ligadata.keyvaluestore

import com.ligadata._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.ligadata.StorageBase.{ DataStore }
import org.apache.logging.log4j._
import com.ligadata.keyvaluestore._
import com.ligadata.Utils.Utils._
import com.ligadata.Utils.{ KamanjaClassLoader, KamanjaLoaderInfo }
import com.ligadata.StorageBase.StorageAdapterObj

object KeyValueManager {
  private val loggerName = this.getClass.getName
  private val logger = LogManager.getLogger(loggerName)
  private val kvManagerLoader = new KamanjaLoaderInfo
  // We will add more implementations here 
  // so we can test  the system characteristics
  //
  def Get(jarPaths: collection.immutable.Set[String], datastoreConfig: String): DataStore = {
    val adapterConfig = if (datastoreConfig != null) datastoreConfig.trim else ""

    if (adapterConfig.size == 0) {
      throw new Exception("Not found valid Storage Configuration.")
    }

    logger.debug("Storage configuration:" + adapterConfig)
    var parsed_json: Map[String, Any] = null
    try {
      val json = parse(adapterConfig)
      if (json == null || json.values == null) {
        logger.error("Failed to parse Storage JSON configuration string:" + adapterConfig)
        throw new Exception("Failed to parse Storage JSON configuration string:" + adapterConfig)
      }
      parsed_json = json.values.asInstanceOf[Map[String, Any]]
    } catch {
      case e: Exception => {
        logger.error("Failed to parse Storage JSON configuration string:%s. Reason:%s Message:%s".format(adapterConfig, e.getCause, e.getMessage))
        throw e
      }
    }

    val storeType = parsed_json.getOrElse("StoreType", "").toString.trim.toLowerCase

    storeType match {

      // Other KV stores
      case "cassandra" => return CassandraAdapter.CreateStorageAdapter(kvManagerLoader, datastoreConfig)
      case "hbase" => return HBaseAdapter.CreateStorageAdapter(kvManagerLoader, datastoreConfig)
      /*
      // Simple file base implementations
      case "redis" => return KeyValueRedis.CreateStorageAdapter(kvManagerLoader, datastoreConfig, tableName)
      */
      case "hashmap" => return HashMapAdapter.CreateStorageAdapter(kvManagerLoader, datastoreConfig)
      case "treemap" => return TreeMapAdapter.CreateStorageAdapter(kvManagerLoader, datastoreConfig)
      // Other relational stores such as sqlserver, mysql
      case "sqlserver" => return SqlServerAdapter.CreateStorageAdapter(kvManagerLoader, datastoreConfig)
      // case "mysql" => return MySqlAdapter.CreateStorageAdapter(kvManagerLoader, datastoreConfig)

      // Default, Load it from Class
      case _ => {
        val (className, jarName, dependencyJars) = getClassNameJarNameDepJarsFromJson(parsed_json)
        logger.debug("className:%s, jarName:%s, dependencyJars:%s".format(className, jarName, dependencyJars))
        if (className != null && className.size > 0 && jarName != null && jarName.size > 0) {
          var allJars: collection.immutable.Set[String] = null
          if (dependencyJars != null && jarName != null) {
            allJars = dependencyJars.toSet + jarName
          } else if (dependencyJars != null) {
            allJars = dependencyJars.toSet
          } else if (jarName != null) {
            allJars = collection.immutable.Set(jarName)
          }

          val allJarsToBeValidated = scala.collection.mutable.Set[String]();

          if (allJars != null) {
            allJarsToBeValidated ++= allJars.map(j => GetValidJarFile(jarPaths, j))
          }

          val nonExistsJars = CheckForNonExistanceJars(allJarsToBeValidated.toSet)
          if (nonExistsJars.size > 0) {
            logger.error("Not found jars in Storage Adapters Jars List : {" + nonExistsJars.mkString(", ") + "}")
            return null
          }

          if (allJars != null) {
            if (LoadJars(allJars.map(j => GetValidJarFile(jarPaths, j)).toArray, kvManagerLoader.loadedJars, kvManagerLoader.loader) == false)
              throw new Exception("Failed to add Jars")
          }

          try {
            Class.forName(className, true, kvManagerLoader.loader)
          } catch {
            case e: Exception => {
              logger.error("Failed to load Storage Adapter class %s with Reason:%s Message:%s".format(className, e.getCause, e.getMessage))
              throw e // Rethrow
            }
          }

          // Convert class name into a class
          val clz = Class.forName(className, true, kvManagerLoader.loader)

          var isDs = false
          var curClz = clz

          while (clz != null && isDs == false) {
            isDs = isDerivedFrom(curClz, "com.ligadata.StorageBase.StorageAdapterObj")
            if (isDs == false)
              curClz = curClz.getSuperclass()
          }

          if (isDs) {
            try {
              val module = kvManagerLoader.mirror.staticModule(className)
              val obj = kvManagerLoader.mirror.reflectModule(module)

              val objinst = obj.instance
              if (objinst.isInstanceOf[StorageAdapterObj]) {
                val storageAdapterObj = objinst.asInstanceOf[StorageAdapterObj]
                return storageAdapterObj.CreateStorageAdapter(kvManagerLoader, datastoreConfig)
              } else {
                logger.error("Failed to instantiate Storage Adapter with configuration:" + adapterConfig)
                return null
              }

            } catch {
              case e: Exception => {
                logger.error("Failed to instantiate Storage Adapter with configuration:" + adapterConfig + ". Reason:" + e.getCause + ". Message:" + e.getMessage)
                return null
              }
            }
          } else {
            logger.error("Failed to instantiate Storage Adapter with configuration:" + adapterConfig)
            return null
          }
        }
      }
    }
    val errMsg = "Failed to instantiate Storage Adapter with configuration:" + adapterConfig
    logger.error(errMsg)
    throw new Exception(errMsg)
  }
}
