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

package com.ligadata.automation.unittests.api

import com.ligadata.automation.unittests.api.setup._
import org.scalatest._
import Matchers._

import com.ligadata.MetadataAPI._
import com.ligadata.kamanja.metadata._
import com.ligadata.kamanja.metadata.MdMgr._

import com.ligadata.Utils._
import util.control.Breaks._
import scala.io._
import java.util.Date
import java.io._

import sys.process._
import org.apache.logging.log4j._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.ligadata.Serialize._

import com.ligadata.kamanja.metadataload.MetadataLoad

class MetadataAPISpec extends FunSpec with LocalTestFixtures with BeforeAndAfter with BeforeAndAfterAll with GivenWhenThen {
  var res : String = null;
  var statusCode: Int = -1;
  var apiResKey:String = "\"Status Code\" : 0"
  var objName:String = null
  var contStr:String = null
  var version:String = null
  var o:Option[ContainerDef] = None
  var dirName: String = null
  var iFile: File = null
  var fileList: List[String] = null
  var newVersion:String = null

  private val loggerName = this.getClass.getName
  private val logger = LogManager.getLogger(loggerName)

  private def TruncateDbStore = {
      val db = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("DATABASE")
      assert(null != db)
      db match {
	case "sqlserver" | "mysql" | "hbase" | "cassandra" | "hashmap" | "treemap" => {
	  var ds = MetadataAPIImpl.GetMainDS
	  var containerList:Array[String] = Array("config_objects","jar_store","model_config_objects","metadata_objects","transaction_id")
	  ds.TruncateContainer(containerList)
	}
	case _ => {
	  logger.info("TruncateDbStore is not supported for database " + db)
	}
      }
  }

  private def DropDbStore = {
      val db = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("DATABASE")
      assert(null != db)
      db match {
	case "sqlserver" | "mysql" | "hbase" | "cassandra" | "hashmap" | "treemap" => {
	  var ds = MetadataAPIImpl.GetMainDS
	  var containerList:Array[String] = Array("config_objects","jar_store","model_config_objects","metadata_objects","transaction_id")
	  ds.DropContainer(containerList)
	}
	case _ => {
	  logger.info("DropDbStore is not supported for database " + db)
	}
      }
  }

  override def beforeAll = {
    try {

      logger.info("starting...");

      logger.info("resource dir => " + getClass.getResource("/").getPath)

      logger.info("Initialize MetadataManager")
      mdMan.config.classPath = ConfigDefaults.metadataClasspath
      mdMan.initMetadataCfg

      logger.info("Initialize MdMgr")
      MdMgr.GetMdMgr.truncate
      val mdLoader = new MetadataLoad(MdMgr.mdMgr, "", "", "", "")
      mdLoader.initialize

      val zkServer = EmbeddedZookeeper
      zkServer.instance.startup

      logger.info("Initialize zooKeeper connection")
      MetadataAPIImpl.initZkListeners(false)

      logger.info("Initialize datastore")
      var tmpJarPaths = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS")
      logger.info("jarPaths => " + tmpJarPaths)
      val jarPaths = if (tmpJarPaths != null) tmpJarPaths.split(",").toSet else scala.collection.immutable.Set[String]()
      MetadataAPIImpl.OpenDbStore(jarPaths, MetadataAPIImpl.GetMetadataAPIConfig.getProperty("METADATA_DATASTORE"))

      var jp = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS")
      logger.info("jarPaths => " + jp)


      logger.info("Truncating dbstore")
      TruncateDbStore

      And("PutTranId updates the tranId")
      noException should be thrownBy {
	MetadataAPIImpl.PutTranId(0)
      }

      logger.info("Load All objects into cache")
      MetadataAPIImpl.LoadAllObjectsIntoCache

      // The above call is resetting JAR_PATHS based on nodeId( node-specific configuration)
      // This is causing unit-tests to fail
      // restore JAR_PATHS value
      MetadataAPIImpl.GetMetadataAPIConfig.setProperty("JAR_PATHS",tmpJarPaths)
      jp = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("JAR_PATHS")
      logger.info("jarPaths => " + jp)

      logger.info("Initialize security adapter")
      MetadataAPIImpl.InitSecImpl

      MetadataAPIImpl.TruncateAuditStore
      MetadataAPIImpl.isInitilized = true
      logger.info(MetadataAPIImpl.GetMetadataAPIConfig)
   }
    catch {
      case e: EmbeddedZookeeperException => {
        throw new EmbeddedZookeeperException("EmbeddedZookeeperException detected\n" + e)
      }
      case e: Exception => throw new Exception("Failed to execute set up properly\n" + e)
    }
  }

  /**
   * extractNameFromPMML - pull the Application name="xxx" from the PMML doc and construct
   *                       a name  string from it, cloned from APIService.scala
   */
  def extractNameFromPMML (pmmlObj: String): String = {
    var firstOccurence: String = "unknownModel"
    val pattern = """Application[ ]*name="([^ ]*)"""".r
    val allMatches = pattern.findAllMatchIn(pmmlObj)
    allMatches.foreach( m => {
      if (firstOccurence.equalsIgnoreCase("unknownModel")) {
      firstOccurence = (m.group(1))
      }
    })
    return firstOccurence
  }

  describe("Unit Tests for all MetadataAPI operations") {

    // validate property setup
    it ("Validate properties for MetadataAPI") {
      And("MetadataAPIImpl.GetMetadataAPIConfig should have been initialized")
      val cfg = MetadataAPIImpl.GetMetadataAPIConfig
      assert(null != cfg)

      And("The property DATABASE must have been defined")
      val db = cfg.getProperty("DATABASE")
      assert(null != db)
      if ( db == "cassandra" ){
	And("The property MetadataLocation must have been defined for store type " + db)
	val loc = cfg.getProperty("DATABASE_LOCATION")
	assert(null != loc)
	And("The property MetadataSchemaName must have been defined for store type " + db)
	val schema = cfg.getProperty("DATABASE_SCHEMA")
	assert(null != schema)
      }
      And("The property NODE_ID must have been defined")
      assert(null != cfg.getProperty("NODE_ID"))  

      
      And("The property JAR_TRAGET_DIR must have been defined")
      val d = cfg.getProperty("JAR_TARGET_DIR")
      assert(null != d)

      And("Make sure the Directory " + d + " exists")
      val f = new File(d)
      assert(null != f)

      And("The property SCALA_HOME must have been defined")
      val sh = cfg.getProperty("SCALA_HOME")
      assert(null != sh)

      And("The property JAVA_HOME must have been defined")
      val jh = cfg.getProperty("SCALA_HOME")
      assert(null != jh)

      And("The property CLASSPATH must have been defined")
      val cp = cfg.getProperty("CLASSPATH")
      assert(null != cp)

      And("The property ZNODE_PATH must have been defined")
      val zkPath = cfg.getProperty("ZNODE_PATH")
      assert(null != zkPath)

      And("The property ZOOKEEPER_CONNECT_STRING must have been defined")
      val zkConnStr = cfg.getProperty("ZOOKEEPER_CONNECT_STRING")
      assert(null != zkConnStr)

      And("The property SERVICE_HOST must have been defined")
      val shost = cfg.getProperty("SERVICE_HOST")
      assert(null != shost)

      And("The property SERVICE_PORT must have been defined")
      val sport = cfg.getProperty("SERVICE_PORT")
      assert(null != sport)

      And("The property JAR_PATHS must have been defined")
      val jp = cfg.getProperty("JAR_PATHS")
      assert(null != jp)
      logger.info("jar_paths => " + jp)

      And("The property SECURITY_IMPL_JAR  must have been defined")
      val sij = cfg.getProperty("SECURITY_IMPL_JAR")
      assert(null != sij)

      And("The property SECURITY_IMPL_CLASS  must have been defined")
      val sic = cfg.getProperty("SECURITY_IMPL_CLASS")
      assert(null != sic)

      And("The property DO_AUTH  must have been defined")
      val da = cfg.getProperty("DO_AUTH")
      assert(null != da)

      And("The property AUDIT_IMPL_JAR  must have been defined")
      val aij = cfg.getProperty("AUDIT_IMPL_JAR")
      assert(null != sij)

      And("The property AUDIT_IMPL_CLASS  must have been defined")
      val aic = cfg.getProperty("AUDIT_IMPL_CLASS")
      assert(null != sic)

      And("The property DO_AUDIT  must have been defined")
      val dau = cfg.getProperty("DO_AUDIT")
      assert(null != dau)

      And("The property SSL_CERTIFICATE  must have been defined")
      val sc = cfg.getProperty("SSL_CERTIFICATE")
      assert(null != sc)
    }

    // CRUD operations on container objects
    it ("Container Tests") {
      And("Check whether CONTAINER_FILES_DIR defined as property")
      dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CONTAINER_FILES_DIR")
      assert(null != dirName)

      And("Check Directory Path")
      iFile = new File(dirName)
      assert(true == iFile.exists)

      And("Check whether " + dirName + " is a directory ")
      assert(true == iFile.isDirectory)

      And("Make sure there are few JSON container files in " + dirName);
      val contFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      assert(0 != contFiles.length)

      //fileList = List("CoughCodes.json","EnvCodes.json","DyspnoeaCodes.json","SmokeCodes.json","SputumCodes.json")
      fileList = List("EnvCodes.json")
      fileList.foreach(f1 => {
	And("Add the Container From " + f1)
	And("Make Sure " + f1 + " exist")
	var exists = false
	var file: java.io.File = null
	breakable{
	  contFiles.foreach(f2 => {
	    if( f2.getName() == f1 ){
	      exists = true
	      file = f2
	      break
	    }
	  })
	}
	assert(true == exists)

	And("GetContainerDef API to fetch the container that may not even exist, check for Status Code of -1")
	objName = f1.stripSuffix(".json").toLowerCase
	version = "0000000000001000000"
	res = MetadataAPIImpl.GetContainerDef("system",objName,"JSON",version,None)
	res should include regex ("\"Status Code\" : -1")

	And("AddContainer first time from " + file.getPath)
	contStr = Source.fromFile(file).mkString
	res = MetadataAPIImpl.AddContainer(contStr,"JSON",None)
	res should include regex ("\"Status Code\" : 0")

	And("GetContainerDef API to fetch the container that was just added")
	res = MetadataAPIImpl.GetContainerDef("system",objName,"JSON",version,None)
	res should include regex ("\"Status Code\" : 0")

	And("AddContainer second time from " + file.getPath + ",should result in error")
	contStr = Source.fromFile(file).mkString
	res = MetadataAPIImpl.AddContainer(contStr,"JSON",None)
	res should include regex ("\"Status Code\" : -1")

	And("RemoveContainer API for the container that was just added")
	res = MetadataAPIImpl.RemoveContainer(objName,1000000,None)
	res should include regex ("\"Status Code\" : 0")

	And("GetContainerDef API to fetch the container that was just removed, should fail, check for Status Code of -1")
	res = MetadataAPIImpl.GetContainerDef("system",objName,"JSON",version,None)
	res should include regex ("\"Status Code\" : -1")

	And("AddContainer again to add Container from " + file.getPath)
	contStr = Source.fromFile(file).mkString
	res = MetadataAPIImpl.AddContainer(contStr,"JSON",None)
	res should include regex ("\"Status Code\" : 0")

	And("GetContainerDef API to fetch  the container that was just added")
	res = MetadataAPIImpl.GetContainerDef("system",objName,"JSON",version,None)
	res should include regex ("\"Status Code\" : 0")

	And("Get the container object from the cache")
	o = MdMgr.GetMdMgr.Container("system",objName, version.toLong, true)
	assert(o != None )

	And("Deactivate container that was just added")
	MetadataAPIImpl.DeactivateObject(o.get.asInstanceOf[BaseElemDef])

	And("Get the active container object from the cache after deactivating")
	o = MdMgr.GetMdMgr.Container("system",objName, version.toLong, true)
	assert(o == None )

	And("Make sure the container object from the cache nolonger active ")
	o = MdMgr.GetMdMgr.Container("system",objName, version.toLong, false)
	assert(o != None )

	And("Activate container that was just deactivated")
	MetadataAPIImpl.ActivateObject(o.get.asInstanceOf[BaseElemDef])

	And("Make sure the container object from the cache is active")
	o = MdMgr.GetMdMgr.Container("system",objName, version.toLong, true)
	assert(o != None )

	And("Update the container without changing version number, should fail ")
	res = MetadataAPIImpl.UpdateContainer(contStr,None)
	res should include regex ("\"Status Code\" : -1")

	And("Clone the input json and update the version number to simulate a container for an update operation")
	contStr = contStr.replaceFirst("01.00","01.01")
	assert(contStr.indexOf("\"00.01.01\"") >= 0)
	res = MetadataAPIImpl.UpdateContainer(contStr,None)
	res should include regex ("\"Status Code\" : 0")

	And("GetContainerDef API to fetch the container that was just updated")
	newVersion = "0000000000001000001"
	res = MetadataAPIImpl.GetContainerDef("system",objName,"JSON",newVersion,None)
	res should include regex ("\"Status Code\" : 0")

	And("Get the active container object from the cache after updating")
	o = MdMgr.GetMdMgr.Container("system",objName, newVersion.toLong, true)
	assert(o != None )

	And("Make sure old(pre update version) container object nolonger active after the update")
	o = MdMgr.GetMdMgr.Container("system",objName, version.toLong, true)
	assert(o == None )
      })
    }

    // CRUD operations on message objects
    it ("Message Tests") {
      And("Check whether MESSAGE_FILES_DIR defined as property")
      dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MESSAGE_FILES_DIR")
      assert(null != dirName)

      And("Check Directory Path")
      iFile = new File(dirName)
      assert(true == iFile.exists)

      And("Check whether " + dirName + " is a directory ")
      assert(true == iFile.isDirectory)

      And("Make sure there are few JSON message files in " + dirName);
      val msgFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      assert(0 != msgFiles.length)

      //fileList = List("outpatientclaim.json","inpatientclaim.json","hl7.json","beneficiary.json")
      fileList = List("HelloWorld_Msg_Def.json")
      fileList.foreach(f1 => {
	And("Add the Message From " + f1)
	And("Make Sure " + f1 + " exist")
	var exists = false
	var file: java.io.File = null
	breakable{
	  msgFiles.foreach(f2 => {
	    if( f2.getName() == f1 ){
	      exists = true
	      file = f2
	      break
	    }
	  })
	}
	assert(true == exists)

	And("AddMessage first time from " + file.getPath)
	var msgStr = Source.fromFile(file).mkString
	res = MetadataAPIImpl.AddMessage(msgStr,"JSON",None)
	res should include regex ("\"Status Code\" : 0")

	And("GetMessageDef API to fetch the message that was just added")
	var objName = f1.stripSuffix(".json").toLowerCase
	var version = "0000000000001000000"
	res = MetadataAPIImpl.GetMessageDef("system",objName,"JSON",version,None)
	res should include regex ("\"Status Code\" : 0")

	And("RemoveMessage API for the message that was just added")
	res = MetadataAPIImpl.RemoveMessage(objName,1000000,None)
	res should include regex ("\"Status Code\" : 0")

	And("AddMessage again to add Message from " + file.getPath)
	msgStr = Source.fromFile(file).mkString
	res = MetadataAPIImpl.AddMessage(msgStr,"JSON",None)
	res should include regex ("\"Status Code\" : 0")

	And("GetMessageDef API to fetch  the message that was just added")
	objName = f1.stripSuffix(".json").toLowerCase
	res = MetadataAPIImpl.GetMessageDef("system",objName,"JSON",version,None)
	res should include regex ("\"Status Code\" : 0")

	And("Get the message object from the cache")
	var o = MdMgr.GetMdMgr.Message("system",objName, version.toLong, true)
	assert(o != None )

	And("Deactivate message that was just added")
	MetadataAPIImpl.DeactivateObject(o.get.asInstanceOf[BaseElemDef])

	And("Get the active message object from the cache after deactivating")
	o = MdMgr.GetMdMgr.Message("system",objName, version.toLong, true)
	assert(o == None )

	And("Make sure the message object from the cache nolonger active ")
	o = MdMgr.GetMdMgr.Message("system",objName, version.toLong, false)
	assert(o != None )

	And("Activate message that was just deactivated")
	MetadataAPIImpl.ActivateObject(o.get.asInstanceOf[BaseElemDef])

	And("Make sure the message object from the cache is active")
	o = MdMgr.GetMdMgr.Message("system",objName, version.toLong, true)
	assert(o != None )

	And("Clone the input json and update the version number to simulate a message for an update operation")
	msgStr = msgStr.replaceFirst("01.00","01.01")
	assert(msgStr.indexOf("\"00.01.01\"") >= 0)
	res = MetadataAPIImpl.UpdateMessage(msgStr,None)
	res should include regex ("\"Status Code\" : 0")

	And("GetMessageDef API to fetch the message that was just updated")
	newVersion = "0000000000001000001"
	res = MetadataAPIImpl.GetMessageDef("system",objName,"JSON",newVersion,None)
	res should include regex ("\"Status Code\" : 0")

	And("Get the active message object from the cache after updating")
	o = MdMgr.GetMdMgr.Message("system",objName, newVersion.toLong, true)
	assert(o != None )

	And("Make sure old(pre update version) message object nolonger active after the update")
	o = MdMgr.GetMdMgr.Message("system",objName, version.toLong, true)
	assert(o == None )

      })
    }

    // CRUD operations on Model objects
    it ("Model Tests") {
      And("Check whether MODEL_FILES_DIR defined as property")
      dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("MODEL_FILES_DIR")
      assert(null != dirName)

      And("Check Directory Path")
      iFile = new File(dirName)
      assert(true == iFile.exists)

      And("Check whether " + dirName + " is a directory ")
      assert(true == iFile.isDirectory)

      And("Make sure there are few pmml model files in " + dirName);
      val modFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".xml"))
      assert(0 != modFiles.length)

      //fileList = List("COPDv1.xml")
      fileList = List("PMML_Model_HelloWorld.xml")
      fileList.foreach(f1 => {
	And("Add the Model From " + f1)
	And("Make Sure " + f1 + " exist")
	var exists = false
	var file: java.io.File = null
	breakable{
	  modFiles.foreach(f2 => {
	    if( f2.getName() == f1 ){
	      exists = true
	      file = f2
	      break
	    }
	  })
	}
	assert(true == exists)

	And("Call AddModel MetadataAPI Function to add Model from " + file.getPath)
	var modStr = Source.fromFile(file).mkString
	res = MetadataAPIImpl.AddModel(modStr,None)
	res should include regex ("\"Status Code\" : 0")

	And("GetModelDef API to fetch the model that was just added")
	// Unable to use fileName to identify the name of the object
	// Use this function to extract the name of the model
	var objName = extractNameFromPMML(modStr).toLowerCase
	logger.info("ModelName => " + objName)
	assert(objName != "unknownModel")

	var version = "0000000000001000000"
	res = MetadataAPIImpl.GetModelDef("system",objName,"XML",version,None)
	res should include regex ("\"Status Code\" : 0")

	And("RemoveModel API for the model that was just added")
	res = MetadataAPIImpl.RemoveModel("system",objName,1000000,None)
	res should include regex ("\"Status Code\" : 0")

	And("AddModel again to add Model from " + file.getPath)
	//modStr = Source.fromFile(file).mkString
	res = MetadataAPIImpl.AddModel(modStr,None)
	res should include regex ("\"Status Code\" : 0")

	And("GetModelDef API to fetch  the model that was just added")
	//objName = f1.stripSuffix(".json").toLowerCase
	res = MetadataAPIImpl.GetModelDef("system",objName,"XML",version,None)
	res should include regex ("\"Status Code\" : 0")

	And("Get the model object from the cache")
	var o = MdMgr.GetMdMgr.Model("system",objName, version.toLong, true)
	assert(o != None )

	And("Deactivate model that was just added")
	MetadataAPIImpl.DeactivateObject(o.get.asInstanceOf[BaseElemDef])

	And("Get the active model object from the cache after deactivating")
	o = MdMgr.GetMdMgr.Model("system",objName, version.toLong, true)
	assert(o == None )

	And("Make sure the model object from the cache nolonger active ")
	o = MdMgr.GetMdMgr.Model("system",objName, version.toLong, false)
	assert(o != None )

	And("Activate model that was just deactivated")
	MetadataAPIImpl.ActivateObject(o.get.asInstanceOf[BaseElemDef])

	And("Make sure the model object from the cache is active")
	o = MdMgr.GetMdMgr.Model("system",objName, version.toLong, true)
	assert(o != None )

	And("Clone the input json and update the version number to simulate a model for an update operation")
	modStr = modStr.replaceFirst("01.00","01.01")
	assert(modStr.indexOf("\"00.01.01\"") >= 0)
	res = MetadataAPIImpl.UpdateModel(modStr,None)
	res should include regex ("\"Status Code\" : 0")

	And("GetModelDef API to fetch the model that was just updated")
	newVersion = "0000000000001000001"
	res = MetadataAPIImpl.GetModelDef("system",objName,"XML",newVersion,None)
	res should include regex ("\"Status Code\" : 0")

	And("Make sure new model object is active after updating")
	o = MdMgr.GetMdMgr.Model("system",objName, newVersion.toLong, true)
	assert(o != None )

	And("Make sure old(pre update version) model object not active after the update")
	o = MdMgr.GetMdMgr.Model("system",objName, version.toLong, true)
	assert(o == None )
      })
    }

    // CRUD operations on type objects
    it ("Type Tests") {
      And("Check whether TYPE_FILES_DIR defined as property")
      dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("TYPE_FILES_DIR")
      assert(null != dirName)

      And("Check Directory Path")
      iFile = new File(dirName)
      assert(true == iFile.exists)

      And("Check whether " + dirName + " is a directory ")
      assert(true == iFile.isDirectory)

      And("Make sure there are few JSON type files in " + dirName);
      val typeFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      assert(0 != typeFiles.length)

      fileList = List("SampleTypes.json")
      fileList.foreach(f1 => {
	And("Add the Type From " + f1)
	And("Make Sure " + f1 + " exist")
	var exists = false
	var file: java.io.File = null
	breakable{
	  typeFiles.foreach(f2 => {
	    if( f2.getName() == f1 ){
	      exists = true
	      file = f2
	      break
	    }
	  })
	}
	assert(true == exists)

	var typKeys = MetadataAPIImpl.GetAllTypesFromCache(true,None)
	assert(typKeys.length != 0)

	And("Call AddTypes MetadataAPI Type to add Type from " + file.getPath)
	var typeStr = Source.fromFile(file).mkString
	res = MetadataAPIImpl.AddTypes(typeStr,"JSON",None)
	res should include regex ("\"Status Code\" : 0")

	var resCode:Int = 0
	And("GetAllTypes API to fetch all the types that were just added as json string")
	var typeJsonStr1 = MetadataAPIImpl.GetAllTypes("JSON",None)
	assert(typeJsonStr1 != null)
	
	And("verify the type count after adding new types")
	var typKeys1 = MetadataAPIImpl.GetAllTypesFromCache(true,None)
	assert(typKeys1.length != 0)
	assert(typKeys1.length - typKeys.length == 5)

	And("RemoveType API for the types that were just added")
	implicit val jsonFormats: Formats = DefaultFormats
	var json = parse(typeStr)
	//logger.trace("Parsed the json : " + typeStr)
	var typesAdded = json.extract[TypeDefList]

	//var typesAdded = JsonSerializer.parseTypeList(typeStr,"JSON")
	assert(typesAdded.Types.length == 5)

	typesAdded.Types.foreach(typ => {
	  res = MetadataAPIImpl.RemoveType(typ.NameSpace, typ.Name, typ.Version.toLong,None)
	  res should include regex ("\"Status Code\" : 0")
	})

	And("verify the type count after removing new types")
	typKeys1 = MetadataAPIImpl.GetAllTypesFromCache(true,None)
	assert(typKeys1.length != 0)
	assert(typKeys.length - typKeys1.length == 0)

	And("Check whether all the types are removed")
	typesAdded.Types.foreach(typ => {
	  res = MetadataAPIImpl.GetTypeDef(typ.NameSpace, typ.Name, "JSON", typ.Version,None)
	  res should include regex ("\"Status Code\" : 0")
	})


	And("AddTypes again ")
	res = MetadataAPIImpl.AddTypes(typeStr,"JSON",None)
	res should include regex ("\"Status Code\" : 0")

	And("Check GetType function")
	var typeObjList = new Array[BaseTypeDef](0)
	typesAdded.Types.foreach(typ => {
	  var o = MetadataAPIImpl.GetType(typ.NameSpace, typ.Name, typ.Version,"JSON",None)
	  assert(o != None )
	  typeObjList = typeObjList :+ o.get
	})
	assert(typeObjList.length == typesAdded.Types.length)

	And("Generate Json String from typeList generated using GetType function")
	var typeJsonStr2 = JsonSerializer.SerializeObjectListToJson("Types",typeObjList)
	//logger.trace(typeJsonStr2)

	And("RemoveType API for the types that were just added")
	var json1 = parse(typeJsonStr2)
	logger.trace("Parsed the json : " + typeJsonStr2)
	var typesAdded1 = json1.extract[TypeDefList]
	assert(typesAdded1.Types.length == 5)
	typesAdded1.Types.foreach(typ => {
	  logger.trace("Remove the type " + Array(typ.NameSpace,typ.Name,typ.Version).mkString("."))
	  res = MetadataAPIImpl.RemoveType(typ.NameSpace, typ.Name, typ.Version.toLong,None)
	  res should include regex ("\"Status Code\" : 0")
	})

	And("AddTypes again this time use the json string generated by JsonSerializer")
	res = MetadataAPIImpl.AddTypes(typeJsonStr2,"JSON",None)
	res should include regex ("\"Status Code\" : 0")
      })
    }
    
    it ("Function Tests") {

      And("Check whether FUNCTION_FILES_DIR defined as property")
      dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("FUNCTION_FILES_DIR")
      assert(null != dirName)

      And("Check Directory Path")
      iFile = new File(dirName)
      assert(true == iFile.exists)

      And("Check whether " + dirName + " is a directory ")
      assert(true == iFile.isDirectory)

      And("Make sure there are few JSON function files in " + dirName);
      val funcFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      assert(0 != funcFiles.length)

      fileList = List("SampleFunctions.json")
      fileList.foreach(f1 => {
	And("Add the Function From " + f1)
	And("Make Sure " + f1 + " exist")
	var exists = false
	var file: java.io.File = null
	breakable{
	  funcFiles.foreach(f2 => {
	    if( f2.getName() == f1 ){
	      exists = true
	      file = f2
	      break
	    }
	  })
	}
	assert(true == exists)

	And("Count the functions before adding sample functions")
	var fcnKeys = MetadataAPIImpl.GetAllFunctionsFromCache(true,None)
	var fcnKeyCnt = fcnKeys.length
	assert(fcnKeyCnt > 0)

	And("Call AddFunctions MetadataAPI Function to add Function from " + file.getPath)
	var funcListJson = Source.fromFile(file).mkString
	res = MetadataAPIImpl.AddFunctions(funcListJson,"JSON",None)
	res should include regex ("\"Status Code\" : 0")

	And("GetAllFunctionDef API to fetch all the functions that were just added")
	var rs1 = MetadataAPIImpl.GetAllFunctionDefs("JSON",None)
	assert(rs1._1 != 0)
	assert(rs1._2 != null)

	And("Verify function count after adding sample functions")
	fcnKeys = MetadataAPIImpl.GetAllFunctionsFromCache(true,None)
	var fcnKeyCnt1 = fcnKeys.length
	assert(fcnKeyCnt1 > 0)

	And("SampleFunctions.json contains only 2 functions and newCount is greater by 2")
	assert(fcnKeyCnt1 - fcnKeyCnt == 2)

	And("Explictly parse the funcJsonStr to identify the functions that were just added")
	implicit val jsonFormats: Formats = DefaultFormats
	var json = parse(funcListJson)
	logger.trace("Parsed the json : " + funcListJson)
	val funcList = json.extract[FunctionList]
	assert(funcList.Functions.length == 2)

	And("RemoveFunction API for all the functions that were just added")
	funcList.Functions.foreach(fcn => {
	  res = MetadataAPIImpl.RemoveFunction(fcn.NameSpace, fcn.Name, fcn.Version.toLong,None)
	})

	And("Check whether all the functions are removed")
	funcList.Functions.foreach(fcn => {
	  res = MetadataAPIImpl.GetFunctionDef(fcn.NameSpace, fcn.Name, "JSON",fcn.Version,None)
	  res should include regex("\"Status Code\" : 0")
	})

	And("Verify function count after removing the sample functions")
	fcnKeys = MetadataAPIImpl.GetAllFunctionsFromCache(true,None)
	fcnKeyCnt1 = fcnKeys.length
	assert(fcnKeyCnt1 > 0)
	assert(fcnKeyCnt1 - fcnKeyCnt == 0)

	And("AddFunctions MetadataAPI Function again to add Function from " + file.getPath)
	res = MetadataAPIImpl.AddFunctions(funcListJson,"JSON",None)
	res should include regex ("\"Status Code\" : 0")

	funcList.Functions.foreach(fcn => {
	  var o = MdMgr.GetMdMgr.Functions(fcn.NameSpace,fcn.Name,true,true)
	  assert(o != None )
	})
      })
    }

    it ("Concept Tests") {

      And("Check whether CONCEPT_FILES_DIR defined as property")
      dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CONCEPT_FILES_DIR")
      assert(null != dirName)

      And("Check Directory Path")
      iFile = new File(dirName)
      assert(true == iFile.exists)

      And("Check whether " + dirName + " is a directory ")
      assert(true == iFile.isDirectory)

      And("Make sure there are few JSON concept files in " + dirName);
      val conceptFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      assert(0 != conceptFiles.length)

      fileList = List("SampleConcepts.json")
      fileList.foreach(f1 => {
	And("Add the Concept From " + f1)
	And("Make Sure " + f1 + " exist")
	var exists = false
	var file: java.io.File = null
	breakable{
	  conceptFiles.foreach(f2 => {
	    if( f2.getName() == f1 ){
	      exists = true
	      file = f2
	      break
	    }
	  })
	}
	assert(true == exists)

	And("Count the concepts before adding sample concepts")
	var conceptKeys = MetadataAPIImpl.GetAllConceptsFromCache(true,None)
	var conceptKeyCnt = conceptKeys.length
	assert(conceptKeyCnt == 0)

	And("Call AddConcepts MetadataAPI Concept to add Concept from " + file.getPath)
	var conceptListJson = Source.fromFile(file).mkString
	res = MetadataAPIImpl.AddConcepts(conceptListJson,"JSON",None)
	res should include regex ("\"Status Code\" : 0")

	And("GetAllConceptDef API to fetch all the concepts that were just added")
	res = MetadataAPIImpl.GetAllConcepts("JSON",None)
	res should include regex ("\"Status Code\" : 0")

	And("Verify concept count after adding sample concepts")
	conceptKeys = MetadataAPIImpl.GetAllConceptsFromCache(true,None)
	var conceptKeyCnt1 = conceptKeys.length
	assert(conceptKeyCnt1 > 0)

	And("SampleConcepts.json contains only 2 concepts and newCount is greater by 2")
	assert(conceptKeyCnt1 - conceptKeyCnt == 2)

	And("Explictly parse the conceptJsonStr to identify the concepts that were just added")
	implicit val jsonFormats: Formats = DefaultFormats
	var json = parse(conceptListJson)
	logger.trace("Parsed the json : " + conceptListJson)
	val conceptList = json.extract[ConceptList]
	assert(conceptList.Concepts.length == 2)

	And("RemoveConcept API for all the concepts that were just added")
	conceptList.Concepts.foreach(concept => {
	  res = MetadataAPIImpl.RemoveConcept(concept.NameSpace, concept.Name, concept.Version.toLong,None)
	})

	And("Check whether all the concepts are removed")
	conceptList.Concepts.foreach(concept => {
	  res = MetadataAPIImpl.GetConceptDef(concept.NameSpace, concept.Name, "JSON",concept.Version,None)
	  res should include regex("\"Status Code\" : -1")
	})

	And("Verify concept count after removing the sample concepts")
	conceptKeys = MetadataAPIImpl.GetAllConceptsFromCache(true,None)
	conceptKeyCnt1 = conceptKeys.length
	assert(conceptKeyCnt1 - conceptKeyCnt == 0)

	And("AddConcepts MetadataAPI Concept again to add Concept from " + file.getPath)
	res = MetadataAPIImpl.AddConcepts(conceptListJson,"JSON",None)
	res should include regex ("\"Status Code\" : 0")

	conceptList.Concepts.foreach(concept => {
	  var o = MdMgr.GetMdMgr.Attributes(concept.NameSpace,concept.Name,true,true)
	  assert(o != None )
	})
      })
    }

    it ("Cluster Config Tests") {

      And("Check whether CONFIG_FILES_DIR defined as property")
      dirName = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("CONFIG_FILES_DIR")
      assert(null != dirName)

      And("Check Directory Path")
      iFile = new File(dirName)
      assert(true == iFile.exists)

      And("Check whether " + dirName + " is a directory ")
      assert(true == iFile.isDirectory)

      And("Make sure there are few JSON config files in " + dirName);
      val cfgFiles = new java.io.File(dirName).listFiles.filter(_.getName.endsWith(".json"))
      assert(0 != cfgFiles.length)

      fileList = List("ClusterConfig.json")
      fileList.foreach(f1 => {
	And("Add the Config From " + f1)
	And("Make Sure " + f1 + " exist")
	var exists = false
	var file: java.io.File = null
	breakable{
	  cfgFiles.foreach(f2 => {
	    if( f2.getName() == f1 ){
	      exists = true
	      file = f2
	      break
	    }
	  })
	}
	assert(true == exists)

	And("AddConfig first time from " + file.getPath)
	var cfgStr = Source.fromFile(file).mkString
	res = MetadataAPIImpl.UploadConfig(cfgStr,None,"testConfig")
	res should include regex ("\"Status Code\" : 0")

	And("GetAllCfgObjects to fetch all config objects")
	res = MetadataAPIImpl.GetAllCfgObjects("JSON",None)
	res should include regex ("\"Status Code\" : 0")

	And("GetAllNodes to fetch the nodes")
	res = MetadataAPIImpl.GetAllNodes("JSON",None)
	res should include regex ("\"Status Code\" : 0")
	logger.info(res)

	And("GetAllAdapters to fetch the adapters")
	res = MetadataAPIImpl.GetAllAdapters("JSON",None)
	res should include regex ("\"Status Code\" : 0")

	And("GetAllClusters to fetch the clusters")
	res = MetadataAPIImpl.GetAllClusters("JSON",None)
	res should include regex ("\"Status Code\" : 0")

	And("Check number of the nodes")
	var nodes = MdMgr.GetMdMgr.Nodes
	assert(nodes.size == 3)

	And("Check number of the adapters")
	var adapters = MdMgr.GetMdMgr.Adapters
	assert(adapters.size == 4)

	And("RemoveConfig API for the config that was just added")
	res = MetadataAPIImpl.RemoveConfig(cfgStr,None,"testConfig")
	res should include regex ("\"Status Code\" : 0")

	And("Check number of the nodes after removing config")
	nodes = MdMgr.GetMdMgr.Nodes
	assert(nodes.size == 0)

	And("Check number of the adapters after removing config")
	adapters = MdMgr.GetMdMgr.Adapters
	assert(adapters.size == 0)

	And("AddConfig second time from " + file.getPath)
	cfgStr = Source.fromFile(file).mkString
	res = MetadataAPIImpl.UploadConfig(cfgStr,None,"testConfig")
	res should include regex ("\"Status Code\" : 0")
      })
    }

    it ("Misc tests not covered by other specs") {
      var cfg = MetadataAPIImpl.GetMetadataAPIConfig
      assert(null != cfg)

      And("Test Authorization functions")
      And("The property DO_AUTH  must have been defined")
      var da = cfg.getProperty("DO_AUTH")
      assert(null != da)
      
      And("checkAuth with correct user and password")
      var chk = MetadataAPIImpl.checkAuth(Some("lonestarr"),Some("vespa"),Some("goodguy"),"read")
      assert(chk == true)

      And("checkAuth with wrong user")
      chk = MetadataAPIImpl.checkAuth(Some("lonestarr1"),Some("vespa"),Some("goodguy"),"read")
      assert(chk == false)

      And("checkAuth with wrong password")
      chk = MetadataAPIImpl.checkAuth(Some("lonestarr"),Some("vespa1"),Some("goodguy"),"read")
      assert(chk == false)

      And("checkAuth for admin user")
      chk = MetadataAPIImpl.checkAuth(Some("root"),Some("secret"),Some("admin"),"adminprivileges")
      assert(chk == true)

      And("GetJarAsArrayOfBytes with invalid jarName as input")
      var ex = the [java.io.FileNotFoundException] thrownBy {
	var aob = MetadataAPIImpl.GetJarAsArrayOfBytes("unknown_junk.jar")
      }
      ex.getMessage should include regex("Jar file \\(unknown_junk.jar\\) is not found")

      And("GetJarAsArrayOfBytes with valid jarName as input")
      noException should be thrownBy {
	var sampleJarFile = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("AUDIT_IMPL_JAR")
	var aob = MetadataAPIImpl.GetJarAsArrayOfBytes(sampleJarFile)
	var fl = new File(sampleJarFile)
        assert(aob.length == fl.length())
      }

      And("Audit Functions")
      And("The property DO_AUDIT  must have been defined")
      da = cfg.getProperty("DO_AUDIT")
      assert(null != da)

      if( da.equalsIgnoreCase("YES") ){
	And("logAuditRec ")
	noException should be thrownBy {
	  MetadataAPIImpl.logAuditRec(Some("lonestarr"),Some("write"),"GetContainerDef","system.coughcodes.000000100000000000","true","12345657","system.coughcodes.000000100000000000")
	}

	And("getAuditRec ")
	res = MetadataAPIImpl.getAuditRec(new Date((new Date).getTime() - 1500 * 60000),null,null,null,null)
	assert(res != null)
	logger.info(res)
	res should include regex("\"Action\" : \"GetContainerDef\"")
	res should include regex("\"UserOrRole\" : \"lonestarr\"")
	res should include regex("\"Status\" : \"true\"")
	res should include regex("\"ObjectAccessed\" : \"system.coughcodes.000000100000000000\"")
	res should include regex("\"ActionResult\" : \"system.coughcodes.000000100000000000\"")
      }

      And("GetTranId returns last transactionId used as long")
      var l = MetadataAPIImpl.GetTranId
      assert(l > 0)

      And("GetNewTranId returns a long")
      var l1 = MetadataAPIImpl.GetNewTranId
      assert(l + 1 == l1)

      And("PutTranId updates the tranId")
      noException should be thrownBy {
	MetadataAPIImpl.PutTranId(l1)
      }

      And("GetTranId after last update of transactionId")
      var l2 = MetadataAPIImpl.GetTranId
      assert(l2 == l1)

    }
  }
  override def afterAll = {
    logger.info("Truncating dbstore")
    var logFile = new java.io.File("logs")
    if( logFile != null ){
      TestUtils.deleteFile(logFile)
    }
    logFile = new java.io.File("lib_managed")
    if( logFile != null ){
      TestUtils.deleteFile(logFile)
    }
    val db = MetadataAPIImpl.GetMetadataAPIConfig.getProperty("DATABASE")
    assert(null != db)
    db match {
      case "hashmap" | "treemap" => {
	DropDbStore
      }
      case _ => {
	logger.info("cleanup...")
      }
    }
    TruncateDbStore
    MetadataAPIImpl.shutdown
  }
  if (zkServer != null) {
    zkServer.instance.shutdown
  }
}
