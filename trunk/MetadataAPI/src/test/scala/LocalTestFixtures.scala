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

package com.ligadata.automation.unittests.api.setup

trait CommonResources {
  def dataDirectory:String = Some(getClass.getResource("/DataDirectories").getPath).getOrElse(throw new Exception("Failed to get resource 'DataDirectories'"))
  def configurationDirectory:String = Some(getClass.getResource("/ConfigurationFiles").getPath).getOrElse(throw new Exception("Failed to get resource 'ConfigurationFiles'"))
  def metadataDirectory:String = Some(getClass.getResource("/Metadata").getPath).getOrElse(throw new Exception("Failed to get resource 'Metadata'"))
  def systemJarsDirectory:String = Some(getClass.getResource("/jars/lib/system").getPath).getOrElse(throw new Exception("Failed to get resource '/jars/lib/system'"))
  def applicationJarsDirectory:String = Some(getClass.getResource("/jars/lib/application").getPath).getOrElse(throw new Exception("Failed to get resource '/jars/lib/application'"))

  def cassandraDataDirectory:String = dataDirectory + "/cassandra"
}

trait LocalTestFixtures extends CommonResources {
  val zkServer = EmbeddedZookeeper
  val mdMan: MetadataManager = new MetadataManager(new MetadataAPIProperties(zkConnStr = zkServer.instance.getConnection))
}
