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

package com.ligadata.kamanja.metadataload

import scala.collection.mutable.{ Set }
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.kamanja.metadata.MdMgr._
import com.ligadata.kamanja.metadata.ObjType._
import com.ligadata.kamanja.metadata._
import com.ligadata.KamanjaBase._
import com.ligadata.BaseTypes._
import org.joda.time.base
import org.joda.time.chrono
import org.joda.time.convert
import org.joda.time.field
import org.joda.time.format
import org.joda.time.tz
import org.joda.time.LocalDate
import org.joda.time.DateTime
import org.joda.time.Years


trait LogTrait {
    val loggerName = this.getClass.getName()
    val logger = LogManager.getLogger(loggerName)
}

/** 
 *  FIXME: As an intermediate development, we might load the metadata manager with file content before resurrecting
 *  the cache from a kv store... hence the arguments (currently unused)
 *	
 *  For now, we just call some functions in the object MetadataLoad to load the various kinds of metadata.
 *  The functions used to load metadata depend on metadata that the loaded element needs being present
 *  before hand in the metadata store (e.g., a type of a function arg must exist before the function can
 *  be loaded.
 *  
 *  
 */

object MetadataLoad {
	def BaseContainersInfo: Array[(String, String, String, List[(String, String, String, String, Boolean, String)])] = {
		// nameSpace: String, name: String, physicalName: String, args: List[(String, String, String, String, Boolean, String)
		return Array[(String, String, String, List[(String, String, String, String, Boolean, String)])](
			(MdMgr.sysNS, "EnvContext", "com.ligadata.KamanjaBase.EnvContext", List()),
			(MdMgr.sysNS, "BaseMsg", "com.ligadata.KamanjaBase.BaseMsg", List()),
			(MdMgr.sysNS, "BaseContainer", "com.ligadata.KamanjaBase.BaseContainer", List()),
			(MdMgr.sysNS, "MessageContainerBase", "com.ligadata.KamanjaBase.MessageContainerBase", List()),
			(MdMgr.sysNS, "Context", "com.ligadata.pmml.runtime.Context", List()))
	}
}

class MetadataLoad (val mgr : MdMgr, val typesPath : String, val fcnPath : String, val attrPath : String, msgCtnPath : String) extends LogTrait {
	val baseTypesVer : Long = 1000000 // Which is 00.01.000000
  
	/** construct the loader and call this to complete the cache initialization */
	def initialize { 

		logger.debug("MetadataLoad...loading typedefs")
		InitTypeDefs

		logger.debug("MetadataLoad...loading BaseContainers definitions")
		InitBaseContainers
	    
		logger.debug("MetadataLoad...loading Pmml types")
		initTypesFor_com_ligadata_pmml_udfs_Udfs

		logger.debug("MetadataLoad...loading Pmml udfs")
		init_com_ligadata_pmml_udfs_Udfs
		init_com_ligadata_pmml_udfs_Udfs1
		
		logger.debug("MetadataLoad...loading Iterable functions")
		InitFcns
			
		logger.debug("MetadataLoad...loading function macro definitions")
		initMacroDefs

		logger.debug("MetadataLoad...loading FactoryOfModelInstanceFactories definitions")
		initFactoryOfModelInstanceFactories
	}
	
	private def initFactoryOfModelInstanceFactories: Unit = {
		mgr.AddFactoryOfModelInstanceFactory("com.ligadata.FactoryOfModelInstanceFactory", "JarFactoryOfModelInstanceFactory", "com.ligadata.FactoryOfModelInstanceFactory.JarFactoryOfModelInstanceFactory$", baseTypesVer, "jarfactoryofmodelinstancefactory_2.10-1.0.jar", Array("metadata_2.10-1.0.jar", "exceptions_2.10-1.0.jar", "kamanjabase_2.10-1.0.jar", "log4j-core-2.4.1.jar", "log4j-api-2.4.1.jar"))
	}
	
	// CMS messages + the dimensional data (treated as Containers)
	def InitBaseContainers: Unit = {
		val baseContainerInfo = MetadataLoad.BaseContainersInfo
		baseContainerInfo.foreach(bc => {
			logger.debug("MetadataLoad...loading " + bc._2)
			mgr.AddFixedContainer(bc._1, bc._2, bc._3, bc._4)
		})
	}

	/** Define any types that may be used in the container, message, fcn, and model metadata */
	def InitTypeDefs = {
		mgr.AddScalar(MdMgr.sysNS, "Any", tAny, "Any", baseTypesVer, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.AnyImpl")
		mgr.AddScalar(MdMgr.sysNS, "String", tString, "String", baseTypesVer, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.StringImpl")
		mgr.AddScalar(MdMgr.sysNS, "Int", tInt, "Int", baseTypesVer, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.IntImpl")
		mgr.AddScalar(MdMgr.sysNS, "Integer", tInt, "Int", baseTypesVer, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.IntImpl")
		mgr.AddScalar(MdMgr.sysNS, "Long", tLong, "Long", baseTypesVer, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.LongImpl")
		mgr.AddScalar(MdMgr.sysNS, "Boolean", tBoolean, "Boolean", baseTypesVer, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.BoolImpl")
		mgr.AddScalar(MdMgr.sysNS, "Bool", tBoolean, "Boolean", baseTypesVer, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.BoolImpl")
		mgr.AddScalar(MdMgr.sysNS, "Double", tDouble, "Double", baseTypesVer, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.DoubleImpl")
		mgr.AddScalar(MdMgr.sysNS, "Float", tFloat, "Float", baseTypesVer, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.FloatImpl")
		mgr.AddScalar(MdMgr.sysNS, "Char", tChar, "Char", baseTypesVer, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.CharImpl")

		mgr.AddScalar(MdMgr.sysNS, "date", tLong, "Long", baseTypesVer, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.LongImpl")
		mgr.AddScalar(MdMgr.sysNS, "dateTime", tLong, "Long", baseTypesVer, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.LongImpl")
		mgr.AddScalar(MdMgr.sysNS, "time", tLong, "Long", baseTypesVer, "basetypes_2.10-0.1.0.jar", Array("metadata_2.10-1.0.jar"), "com.ligadata.BaseTypes.LongImpl")

		
		mgr.AddArray(MdMgr.sysNS, "ArrayOfAny", MdMgr.sysNS, "Any", 1, baseTypesVer)
		mgr.AddArray(MdMgr.sysNS, "ArrayOfString", MdMgr.sysNS, "String", 1, baseTypesVer)
		mgr.AddArray(MdMgr.sysNS, "ArrayOfInt", MdMgr.sysNS, "Int", 1, baseTypesVer)
		mgr.AddArray(MdMgr.sysNS, "ArrayOfLong", MdMgr.sysNS, "Long", 1, baseTypesVer)
		mgr.AddArray(MdMgr.sysNS, "ArrayOfDouble", MdMgr.sysNS, "Double", 1, baseTypesVer)
		mgr.AddArray(MdMgr.sysNS, "ArrayOfFloat", MdMgr.sysNS, "Float", 1, baseTypesVer)
		mgr.AddArray(MdMgr.sysNS, "ArrayOfBoolean", MdMgr.sysNS, "Boolean", 1, baseTypesVer)

		mgr.AddArray(MdMgr.sysNS, "ArrayOfArrayOfAny", MdMgr.sysNS, "ArrayOfAny", 1, baseTypesVer)
		mgr.AddArray(MdMgr.sysNS, "ArrayOfArrayOfString", MdMgr.sysNS, "ArrayOfString", 1, baseTypesVer)
		mgr.AddArray(MdMgr.sysNS, "ArrayOfArrayOfInt", MdMgr.sysNS, "ArrayOfInt", 1, baseTypesVer)
		mgr.AddArray(MdMgr.sysNS, "ArrayOfArrayOfLong", MdMgr.sysNS, "ArrayOfLong", 1, baseTypesVer)
		mgr.AddArray(MdMgr.sysNS, "ArrayOfArrayOfDouble", MdMgr.sysNS, "ArrayOfDouble", 1, baseTypesVer)
		mgr.AddArray(MdMgr.sysNS, "ArrayOfArrayOfFloat", MdMgr.sysNS, "ArrayOfFloat", 1, baseTypesVer)
		mgr.AddArray(MdMgr.sysNS, "ArrayOfArrayOfBoolean", MdMgr.sysNS, "ArrayOfBoolean", 1, baseTypesVer)
		
		
		mgr.AddArrayBuffer(MdMgr.sysNS, "ArrayBufferOfAny", MdMgr.sysNS, "Any", 1, baseTypesVer)
		mgr.AddArrayBuffer(MdMgr.sysNS, "ArrayBufferOfString", MdMgr.sysNS, "String", 1, baseTypesVer)
		mgr.AddArrayBuffer(MdMgr.sysNS, "ArrayBufferOfFloat", MdMgr.sysNS, "Float", 1, baseTypesVer)
		mgr.AddArrayBuffer(MdMgr.sysNS, "ArrayBufferOfDouble", MdMgr.sysNS, "Double", 1, baseTypesVer)
		mgr.AddArrayBuffer(MdMgr.sysNS, "ArrayBufferOfLong", MdMgr.sysNS, "Long", 1, baseTypesVer)
		mgr.AddArrayBuffer(MdMgr.sysNS, "ArrayBufferOfInt", MdMgr.sysNS, "Int", 1, baseTypesVer)
		mgr.AddArrayBuffer(MdMgr.sysNS, "ArrayBufferOfBoolean", MdMgr.sysNS, "Boolean", 1, baseTypesVer)

		mgr.AddList(MdMgr.sysNS, "ListOfAny", MdMgr.sysNS, "Any", baseTypesVer)
		mgr.AddList(MdMgr.sysNS, "ListOfFloat", MdMgr.sysNS, "Float", baseTypesVer)
		mgr.AddList(MdMgr.sysNS, "ListOfDouble", MdMgr.sysNS, "Double", baseTypesVer)
		mgr.AddList(MdMgr.sysNS, "ListOfLong", MdMgr.sysNS, "Long", baseTypesVer)
		mgr.AddList(MdMgr.sysNS, "ListOfInt", MdMgr.sysNS, "Int", baseTypesVer)
		mgr.AddList(MdMgr.sysNS, "ListOfString", MdMgr.sysNS, "String", baseTypesVer)
		mgr.AddList(MdMgr.sysNS, "ListOfBoolean", MdMgr.sysNS, "Boolean", baseTypesVer)
		
		mgr.AddQueue("System", "QueueOfAny", "System", "Any", baseTypesVer)
		mgr.AddQueue("System", "QueueOfString", "System", "String", baseTypesVer)
		mgr.AddQueue("System", "QueueOfInt", "System", "Int", baseTypesVer)
		mgr.AddQueue("System", "QueueOfLong", "System", "Long", baseTypesVer)
		mgr.AddQueue("System", "QueueOfFloat", "System", "Float", baseTypesVer)
		mgr.AddQueue("System", "QueueOfDouble", "System", "Double", baseTypesVer)
		mgr.AddQueue("System", "QueueOfBoolean", "System", "Boolean", baseTypesVer)

		mgr.AddSortedSet("System", "SortedSetOfAny", "System", "Any", baseTypesVer)

		mgr.AddTreeSet("System", "TreeSetOfAny", "System", "Any", baseTypesVer)

		mgr.AddSet("System", "SetOfAny", "System", "Any", baseTypesVer)

		mgr.AddImmutableSet("System", "ImmutableSetOfAny", "System", "Any", baseTypesVer)
		//mgr.AddImmutableMap("System", "ImmutableSetOfAny", "System", "Any", baseTypesVer)

		mgr.AddHashMap(MdMgr.sysNS, "HashMapOfStringInt", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "Int"), baseTypesVer)
		mgr.AddTreeSet(MdMgr.sysNS, "TreeSetOfString", MdMgr.sysNS, "String", baseTypesVer)
		
		mgr.AddTupleType(MdMgr.sysNS, "TupleOfStringString", Array((MdMgr.sysNS,"String"), (MdMgr.sysNS,"String")), baseTypesVer)
		mgr.AddArray(MdMgr.sysNS, "ArrayOfTupleOfStringString", MdMgr.sysNS, "TupleOfStringString", 1, baseTypesVer)

		mgr.AddTupleType(MdMgr.sysNS, "TupleOfString2", Array((MdMgr.sysNS,"String"), (MdMgr.sysNS,"String")), baseTypesVer)
		mgr.AddArray(MdMgr.sysNS, "ArrayOfTupleOfString2", MdMgr.sysNS, "TupleOfString2", 1, baseTypesVer)
		mgr.AddTupleType(MdMgr.sysNS, "TupleOfLong2", Array((MdMgr.sysNS,"Long"), (MdMgr.sysNS,"Long")), baseTypesVer)
		mgr.AddArray(MdMgr.sysNS, "ArrayOfTupleOfLong2", MdMgr.sysNS, "TupleOfLong2", 1, baseTypesVer)

		mgr.AddHashMap(MdMgr.sysNS, "HashMapOfIntInt", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "Int"), baseTypesVer)
		mgr.AddHashMap(MdMgr.sysNS, "HashMapOfIntArrayBufferOfInt", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayBufferOfInt"), baseTypesVer)

		mgr.AddHashMap(MdMgr.sysNS, "HashMapOfAnyAny", (MdMgr.sysNS, "Any"), (MdMgr.sysNS, "Any"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfAnyAny", (MdMgr.sysNS, "Any"), (MdMgr.sysNS, "Any"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfAnyAny", (MdMgr.sysNS, "Any"), (MdMgr.sysNS, "Any"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfIntAny", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "Any"), baseTypesVer)

		
		mgr.AddMap(MdMgr.sysNS, "MapOfStringFloat", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "Float"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfIntFloat", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "Float"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfLongFloat", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "Float"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfFloatFloat", (MdMgr.sysNS, "Float"), (MdMgr.sysNS, "Float"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfDoubleFloat", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "Float"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfBooleanFloat", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "Float"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfStringFloat", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "Float"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfIntFloat", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "Float"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfLongFloat", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "Float"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfFloatFloat", (MdMgr.sysNS, "Float"), (MdMgr.sysNS, "Float"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfDoubleFloat", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "Float"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfBooleanFloat", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "Float"), baseTypesVer)
		
		mgr.AddMap(MdMgr.sysNS, "MapOfStringDouble", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "Double"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfIntDouble", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "Double"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfLongDouble", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "Double"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfFloatDouble", (MdMgr.sysNS, "Float"), (MdMgr.sysNS, "Double"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfDoubleDouble", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "Double"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfBooleanDouble", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "Double"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfStringDouble", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "Double"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfIntDouble", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "Double"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfLongDouble", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "Double"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfFloatDouble", (MdMgr.sysNS, "Float"), (MdMgr.sysNS, "Double"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfDoubleDouble", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "Double"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfBooleanDouble", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "Double"), baseTypesVer)
		
		mgr.AddMap(MdMgr.sysNS, "MapOfStringArrayOfDouble", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayOfDouble"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfIntArrayOfDouble", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayOfDouble"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfLongArrayOfDouble", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayOfDouble"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfDoubleArrayOfDouble", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayOfDouble"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfBooleanArrayOfDouble", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayOfDouble"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfStringArrayOfDouble", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayOfDouble"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfIntArrayOfDouble", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayOfDouble"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfLongArrayOfDouble", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayOfDouble"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfDoubleArrayOfDouble", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayOfDouble"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfBooleanArrayOfDouble", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayOfDouble"), baseTypesVer)
		
		mgr.AddMap(MdMgr.sysNS, "MapOfStringArrayOfInt", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayOfInt"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfIntArrayOfInt", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayOfInt"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfLongArrayOfInt", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayOfInt"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfDoubleArrayOfInt", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayOfInt"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfBooleanArrayOfInt", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayOfInt"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfStringArrayOfInt", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayOfInt"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfIntArrayOfInt", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayOfInt"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfLongArrayOfInt", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayOfInt"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfDoubleArrayOfInt", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayOfInt"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfBooleanArrayOfInt", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayOfInt"), baseTypesVer)
		
		mgr.AddMap(MdMgr.sysNS, "MapOfStringArrayOfLong", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayOfLong"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfIntArrayOfLong", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayOfLong"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfLongArrayOfLong", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayOfLong"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfDoubleArrayOfLong", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayOfLong"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfBooleanArrayOfLong", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayOfLong"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfStringArrayOfLong", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayOfLong"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfIntArrayOfLong", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayOfLong"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfLongArrayOfLong", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayOfLong"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfDoubleArrayOfLong", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayOfLong"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfBooleanArrayOfLong", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayOfLong"), baseTypesVer)

		mgr.AddMap(MdMgr.sysNS, "MapOfStringArrayOfAny", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayOfAny"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfIntArrayOfAny", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayOfAny"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfLongArrayOfAny", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayOfAny"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfDoubleArrayOfAny", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayOfAny"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfBooleanArrayOfAny", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayOfAny"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfStringArrayOfAny", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayOfAny"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfIntArrayOfAny", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayOfAny"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfLongArrayOfAny", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayOfAny"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfDoubleArrayOfAny", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayOfAny"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfBooleanArrayOfAny", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayOfAny"), baseTypesVer)
		
		mgr.AddMap(MdMgr.sysNS, "MapOfStringArrayBufferOfDouble", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayBufferOfDouble"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfIntArrayBufferOfDouble", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayBufferOfDouble"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfLongArrayBufferOfDouble", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayBufferOfDouble"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfDoubleArrayBufferOfDouble", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayBufferOfDouble"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfBooleanArrayBufferOfDouble", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayBufferOfDouble"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfStringArrayBufferOfDouble", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayBufferOfDouble"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfIntArrayBufferOfDouble", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayBufferOfDouble"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfLongArrayBufferOfDouble", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayBufferOfDouble"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfDoubleArrayBufferOfDouble", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayBufferOfDouble"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfBooleanArrayBufferOfDouble", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayBufferOfDouble"), baseTypesVer)
		
		mgr.AddMap(MdMgr.sysNS, "MapOfStringArrayBufferOfInt", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayBufferOfInt"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfIntArrayBufferOfInt", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayBufferOfInt"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfLongArrayBufferOfInt", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayBufferOfInt"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfDoubleArrayBufferOfInt", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayBufferOfInt"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfBooleanArrayBufferOfInt", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayBufferOfInt"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfStringArrayBufferOfInt", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayBufferOfInt"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfIntArrayBufferOfInt", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayBufferOfInt"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfLongArrayBufferOfInt", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayBufferOfInt"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfDoubleArrayBufferOfInt", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayBufferOfInt"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfBooleanArrayBufferOfInt", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayBufferOfInt"), baseTypesVer)
		
		mgr.AddMap(MdMgr.sysNS, "MapOfStringArrayBufferOfLong", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayBufferOfLong"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfIntArrayBufferOfLong", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayBufferOfLong"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfLongArrayBufferOfLong", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayBufferOfLong"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfDoubleArrayBufferOfLong", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayBufferOfLong"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfBooleanArrayBufferOfLong", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayBufferOfLong"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfStringArrayBufferOfLong", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayBufferOfLong"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfIntArrayBufferOfLong", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayBufferOfLong"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfLongArrayBufferOfLong", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayBufferOfLong"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfDoubleArrayBufferOfLong", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayBufferOfLong"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfBooleanArrayBufferOfLong", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayBufferOfLong"), baseTypesVer)

		mgr.AddMap(MdMgr.sysNS, "MapOfStringArrayBufferOfAny", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayBufferOfAny"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfIntArrayBufferOfAny", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayBufferOfAny"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfLongArrayBufferOfAny", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayBufferOfAny"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfDoubleArrayBufferOfAny", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayBufferOfAny"), baseTypesVer)
		mgr.AddMap(MdMgr.sysNS, "MapOfBooleanArrayBufferOfAny", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayBufferOfAny"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfStringArrayBufferOfAny", (MdMgr.sysNS, "String"), (MdMgr.sysNS, "ArrayBufferOfAny"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfIntArrayBufferOfAny", (MdMgr.sysNS, "Int"), (MdMgr.sysNS, "ArrayBufferOfAny"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfLongArrayBufferOfAny", (MdMgr.sysNS, "Long"), (MdMgr.sysNS, "ArrayBufferOfAny"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfDoubleArrayBufferOfAny", (MdMgr.sysNS, "Double"), (MdMgr.sysNS, "ArrayBufferOfAny"), baseTypesVer)
		mgr.AddImmutableMap(MdMgr.sysNS, "ImmutableMapOfBooleanArrayBufferOfAny", (MdMgr.sysNS, "Boolean"), (MdMgr.sysNS, "ArrayBufferOfAny"), baseTypesVer)
		
		/** should ImmutableSet and Set be done for the MapOf... above too? Yes */
		
		
		
		mgr.AddImmutableSet(MdMgr.sysNS, "ImmutableSetOfString", MdMgr.sysNS, "String", baseTypesVer)
		mgr.AddImmutableSet(MdMgr.sysNS, "ImmutableSetOfInt", MdMgr.sysNS, "Int", baseTypesVer)
		mgr.AddImmutableSet(MdMgr.sysNS, "ImmutableSetOfLong", MdMgr.sysNS, "Long", baseTypesVer)
		mgr.AddImmutableSet(MdMgr.sysNS, "ImmutableSetOfFloat", MdMgr.sysNS, "Float", baseTypesVer)
		mgr.AddImmutableSet(MdMgr.sysNS, "ImmutableSetOfDouble", MdMgr.sysNS, "Double", baseTypesVer)
		mgr.AddSet(MdMgr.sysNS, "SetOfString", MdMgr.sysNS, "String", baseTypesVer)
		mgr.AddSet(MdMgr.sysNS, "SetOfInt", MdMgr.sysNS, "Int", baseTypesVer)
		mgr.AddSet(MdMgr.sysNS, "SetOfLong", MdMgr.sysNS, "Long", baseTypesVer)
		mgr.AddSet(MdMgr.sysNS, "SetOfFloat", MdMgr.sysNS, "Float", baseTypesVer)
		mgr.AddSet(MdMgr.sysNS, "SetOfDouble", MdMgr.sysNS, "Double", baseTypesVer)

		
	}
	
	/**
	  
	 
	 */
def initTypesFor_com_ligadata_pmml_udfs_Udfs {

		mgr.AddTupleType("System", "TupleOfAny1", Array(("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny2", Array(("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny3", Array(("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny4", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny5", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny6", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny7", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny8", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny9", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny10", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny11", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny12", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny13", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny14", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny15", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny16", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny17", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny18", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny19", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny20", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny21", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfAny22", Array(("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any"), ("System","Any")), baseTypesVer)
		
		/** doing this for arrays only for now... probably should tool out the arraybuffer and the rest in similar way */
		mgr.AddArray("System", "ArrayOfTupleOfAny2", "System", "TupleOfAny2", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfAny3", "System", "TupleOfAny3", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfAny4", "System", "TupleOfAny4", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfAny5", "System", "TupleOfAny5", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfAny6", "System", "TupleOfAny6", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfAny7", "System", "TupleOfAny7", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfAny8", "System", "TupleOfAny8", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfAny9", "System", "TupleOfAny9", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfAny10", "System", "TupleOfAny10", 1, baseTypesVer)
		
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfAny2", "System", "ArrayOfTupleOfAny2", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfAny3", "System", "ArrayOfTupleOfAny3", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfAny4", "System", "ArrayOfTupleOfAny4", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfAny5", "System", "ArrayOfTupleOfAny5", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfAny6", "System", "ArrayOfTupleOfAny6", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfAny7", "System", "ArrayOfTupleOfAny7", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfAny8", "System", "ArrayOfTupleOfAny8", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfAny9", "System", "ArrayOfTupleOfAny9", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfAny10", "System", "ArrayOfTupleOfAny10", 1, baseTypesVer)
		
		mgr.AddTupleType("System", "TupleOfFloat1", Array(("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat2", Array(("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat3", Array(("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat4", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat5", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat6", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat7", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat8", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat9", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat10", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat11", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat12", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat13", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat14", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat15", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat16", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat17", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat18", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat19", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat20", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat21", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfFloat22", Array(("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float"), ("System","Float")), baseTypesVer)
		
		/** doing this for arrays only for now... probably should tool out the arraybuffer and the rest in similar way */
		mgr.AddArray("System", "ArrayOfTupleOfFloat2", "System", "TupleOfFloat2", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfFloat3", "System", "TupleOfFloat3", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfFloat4", "System", "TupleOfFloat4", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfFloat5", "System", "TupleOfFloat5", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfFloat6", "System", "TupleOfFloat6", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfFloat7", "System", "TupleOfFloat7", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfFloat8", "System", "TupleOfFloat8", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfFloat9", "System", "TupleOfFloat9", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfFloat10", "System", "TupleOfFloat10", 1, baseTypesVer)
		
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfFloat2", "System", "ArrayOfTupleOfFloat2", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfFloat3", "System", "ArrayOfTupleOfFloat3", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfFloat4", "System", "ArrayOfTupleOfFloat4", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfFloat5", "System", "ArrayOfTupleOfFloat5", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfFloat6", "System", "ArrayOfTupleOfFloat6", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfFloat7", "System", "ArrayOfTupleOfFloat7", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfFloat8", "System", "ArrayOfTupleOfFloat8", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfFloat9", "System", "ArrayOfTupleOfFloat9", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfFloat10", "System", "ArrayOfTupleOfFloat10", 1, baseTypesVer)
		
		mgr.AddTupleType("System", "TupleOfDouble1", Array(("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble2", Array(("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble3", Array(("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble4", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble5", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble6", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble7", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble8", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble9", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble10", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble11", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble12", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble13", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble14", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble15", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble16", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble17", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble18", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble19", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble20", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble21", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfDouble22", Array(("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double"), ("System","Double")), baseTypesVer)
		
		/** doing this for arrays only for now... probably should tool out the arraybuffer and the rest in similar way */
		mgr.AddArray("System", "ArrayOfTupleOfDouble2", "System", "TupleOfDouble2", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfDouble3", "System", "TupleOfDouble3", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfDouble4", "System", "TupleOfDouble4", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfDouble5", "System", "TupleOfDouble5", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfDouble6", "System", "TupleOfDouble6", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfDouble7", "System", "TupleOfDouble7", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfDouble8", "System", "TupleOfDouble8", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfDouble9", "System", "TupleOfDouble9", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfDouble10", "System", "TupleOfDouble10", 1, baseTypesVer)
		
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfDouble2", "System", "ArrayOfTupleOfDouble2", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfDouble3", "System", "ArrayOfTupleOfDouble3", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfDouble4", "System", "ArrayOfTupleOfDouble4", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfDouble5", "System", "ArrayOfTupleOfDouble5", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfDouble6", "System", "ArrayOfTupleOfDouble6", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfDouble7", "System", "ArrayOfTupleOfDouble7", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfDouble8", "System", "ArrayOfTupleOfDouble8", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfDouble9", "System", "ArrayOfTupleOfDouble9", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfDouble10", "System", "ArrayOfTupleOfDouble10", 1, baseTypesVer)
		
		mgr.AddTupleType("System", "TupleOfInt1", Array(("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt2", Array(("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt3", Array(("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt4", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt5", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt6", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt7", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt8", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt9", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt10", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt11", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt12", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt13", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt14", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt15", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt16", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt17", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt18", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt19", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt20", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt21", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		mgr.AddTupleType("System", "TupleOfInt22", Array(("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int"), ("System","Int")), baseTypesVer)
		
		/** doing this for arrays only for now... probably should tool out the arraybuffer and the rest in similar way */
		mgr.AddArray("System", "ArrayOfTupleOfInt2", "System", "TupleOfInt2", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfInt3", "System", "TupleOfInt3", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfInt4", "System", "TupleOfInt4", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfInt5", "System", "TupleOfInt5", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfInt6", "System", "TupleOfInt6", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfInt7", "System", "TupleOfInt7", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfInt8", "System", "TupleOfInt8", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfInt9", "System", "TupleOfInt9", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfTupleOfInt10", "System", "TupleOfInt10", 1, baseTypesVer)
		
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfInt2", "System", "ArrayOfTupleOfInt2", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfInt3", "System", "ArrayOfTupleOfInt3", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfInt4", "System", "ArrayOfTupleOfInt4", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfInt5", "System", "ArrayOfTupleOfInt5", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfInt6", "System", "ArrayOfTupleOfInt6", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfInt7", "System", "ArrayOfTupleOfInt7", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfInt8", "System", "ArrayOfTupleOfInt8", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfInt9", "System", "ArrayOfTupleOfInt9", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfArrayOfTupleOfInt10", "System", "ArrayOfTupleOfInt10", 1, baseTypesVer)
		


		
		mgr.AddSortedSet("System", "SortedSetOfTupleOfAny2", "System", "TupleOfAny2", baseTypesVer) 
		mgr.AddTreeSet("System", "TreeSetOfTupleOfAny2", "System", "TupleOfAny2", baseTypesVer) 
		mgr.AddList("System", "ListOfTupleOfAny2", "System", "TupleOfAny2", baseTypesVer)
		mgr.AddQueue("System", "QueueOfTupleOfAny2", "System", "TupleOfAny2", baseTypesVer)
		//mgr.AddStack("System", "QueueOfTupleOfAny2", "System", "TupleOfAny2", baseTypesVer)  ### AddStack needs to be added to mdmgr
		
		mgr.AddImmutableSet(MdMgr.sysNS, "ImmutableSetOfTupleOfAny2", MdMgr.sysNS, "TupleOfAny2", baseTypesVer)
		mgr.AddSet(MdMgr.sysNS, "SetOfTupleOfAny2", MdMgr.sysNS, "TupleOfAny2", baseTypesVer)
		mgr.AddArrayBuffer("System", "ArrayBufferOfTupleOfAny2", "System", "TupleOfAny2", 1, baseTypesVer)

		mgr.AddArray("System", "ArrayOfBaseContainer", "System", "BaseContainer", 1, baseTypesVer)
		mgr.AddArray("System", "ArrayOfMessageContainerBase", "System", "MessageContainerBase", 1, baseTypesVer)
	}


		
	
	def init_com_ligadata_pmml_udfs_Udfs {
	  
		mgr.AddFunc("Pmml", "MakeStrings", "com.ligadata.pmml.udfs.Udfs.MakeStrings", ("System", "ArrayOfString"), List(("arr", "System", "ArrayOfTupleOfStringString"),("separator", "System", "String")), null)
		mgr.AddFunc("Pmml", "MakeOrderedPairs", "com.ligadata.pmml.udfs.Udfs.MakeOrderedPairs", ("System", "ArrayOfTupleOfStringString"), List(("left", "System", "String"),("right", "System", "ArrayBufferOfString")), null)
		mgr.AddFunc("Pmml", "MakeOrderedPairs", "com.ligadata.pmml.udfs.Udfs.MakeOrderedPairs", ("System", "ArrayOfTupleOfStringString"), List(("left", "System", "String"),("right", "System", "ArrayOfString")), null)
		mgr.AddFunc("Pmml", "MakePairs", "com.ligadata.pmml.udfs.Udfs.MakePairs", ("System", "ArrayOfTupleOfStringString"), List(("left", "System", "String"),("right", "System", "ArrayOfString")), null)
		
	    mgr.AddFunc("Pmml", "idGen", "com.ligadata.pmml.udfs.Udfs.idGen", ("System", "String"), List(), null)
	    mgr.AddFunc("Pmml", "concat", "com.ligadata.pmml.udfs.Udfs.concat", ("System", "String"), List(("args", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
	    mgr.AddFunc("Pmml", "replace", "com.ligadata.pmml.udfs.Udfs.replace", ("System", "String"), List(("replacewithin", "System", "Any"),("inWord", "System", "Any"),("replacewith", "System", "Any")), null)
	    mgr.AddFunc("Pmml", "matches", "com.ligadata.pmml.udfs.Udfs.matches", ("System", "Boolean"), List(("matchwithin", "System", "Any"),("matchwith", "System", "Any")), null)
	    mgr.AddFunc("Pmml", "random", "com.ligadata.pmml.udfs.Udfs.random", ("System", "Double"), List(), null)
	    mgr.AddFunc("Pmml", "formatNumber", "com.ligadata.pmml.udfs.Udfs.formatNumber", ("System", "String"), List(("num", "System", "Any"),("formatting", "System", "String")), null)
	    mgr.AddFunc("Pmml", "logMsg", "com.ligadata.pmml.udfs.Udfs.logMsg", ("System", "Boolean"), List(("severity", "System", "String"),("contextMsg", "System", "String"),("eventMsg", "System", "String"),("bool", "System", "Boolean")), null)


	    mgr.AddFunc("Pmml", "CompoundStatementBoolean", "com.ligadata.pmml.udfs.Udfs.CompoundStatementBoolean", ("System", "Boolean"), List(("args", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
	    mgr.AddFunc("Pmml", "CompoundStatementString", "com.ligadata.pmml.udfs.Udfs.CompoundStatementString", ("System", "String"), List(("args", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
	    mgr.AddFunc("Pmml", "CompoundStatementInt", "com.ligadata.pmml.udfs.Udfs.CompoundStatementInt", ("System", "Int"), List(("args", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
	    mgr.AddFunc("Pmml", "CompoundStatementLong", "com.ligadata.pmml.udfs.Udfs.CompoundStatementLong", ("System", "Long"), List(("args", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
	    mgr.AddFunc("Pmml", "CompoundStatementFloat", "com.ligadata.pmml.udfs.Udfs.CompoundStatementFloat", ("System", "Float"), List(("args", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
	    mgr.AddFunc("Pmml", "CompoundStatementDouble", "com.ligadata.pmml.udfs.Udfs.CompoundStatementDouble", ("System", "Double"), List(("args", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
	    mgr.AddFunc("Pmml", "CompoundStatement", "com.ligadata.pmml.udfs.Udfs.CompoundStatement", ("System", "Any"), List(("args", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))


		mgr.AddFunc("Pmml", "length", "com.ligadata.pmml.udfs.Udfs.length", ("System", "String"), List(("str", "System", "String")), null)
		mgr.AddFunc("Pmml", "trimBlanks", "com.ligadata.pmml.udfs.Udfs.trimBlanks", ("System", "String"), List(("str", "System", "String")), null)
		mgr.AddFunc("Pmml", "endsWith", "com.ligadata.pmml.udfs.Udfs.endsWith", ("System", "Boolean"), List(("inThis", "System", "String"),("findThis", "System", "String")), null)
		mgr.AddFunc("Pmml", "startsWith", "com.ligadata.pmml.udfs.Udfs.startsWith", ("System", "Boolean"), List(("inThis", "System", "String"),("findThis", "System", "String")), null)
		mgr.AddFunc("Pmml", "substring", "com.ligadata.pmml.udfs.Udfs.substring", ("System", "String"), List(("str", "System", "String"),("startidx", "System", "Int")), null)
		mgr.AddFunc("Pmml", "substring", "com.ligadata.pmml.udfs.Udfs.substring", ("System", "String"), List(("str", "System", "String"),("startidx", "System", "Int"),("len", "System", "Int")), null)
		mgr.AddFunc("Pmml", "lowercase", "com.ligadata.pmml.udfs.Udfs.lowercase", ("System", "String"), List(("str", "System", "String")), null)
		mgr.AddFunc("Pmml", "uppercase", "com.ligadata.pmml.udfs.Udfs.uppercase", ("System", "String"), List(("str", "System", "String")), null)
		mgr.AddFunc("Pmml", "round", "com.ligadata.pmml.udfs.Udfs.round", ("System", "Double"), List(("expr", "System", "Double")), null)
		mgr.AddFunc("Pmml", "ceil", "com.ligadata.pmml.udfs.Udfs.ceil", ("System", "Double"), List(("expr", "System", "Double")), null)
		mgr.AddFunc("Pmml", "floor", "com.ligadata.pmml.udfs.Udfs.floor", ("System", "Double"), List(("expr", "System", "Double")), null)
		mgr.AddFunc("Pmml", "threshold", "com.ligadata.pmml.udfs.Udfs.threshold", ("System", "Int"), List(("x", "System", "Double"),("y", "System", "Double")), null)
		mgr.AddFunc("Pmml", "threshold", "com.ligadata.pmml.udfs.Udfs.threshold", ("System", "Int"), List(("x", "System", "Float"),("y", "System", "Float")), null)
		mgr.AddFunc("Pmml", "threshold", "com.ligadata.pmml.udfs.Udfs.threshold", ("System", "Int"), List(("x", "System", "Long"),("y", "System", "Long")), null)
		mgr.AddFunc("Pmml", "threshold", "com.ligadata.pmml.udfs.Udfs.threshold", ("System", "Int"), List(("x", "System", "Int"),("y", "System", "Int")), null)
		mgr.AddFunc("Pmml", "pow", "com.ligadata.pmml.udfs.Udfs.pow", ("System", "Double"), List(("x", "System", "Double"),("y", "System", "Int")), null)
		mgr.AddFunc("Pmml", "exp", "com.ligadata.pmml.udfs.Udfs.exp", ("System", "Double"), List(("expr", "System", "Double")), null)
		mgr.AddFunc("Pmml", "abs", "com.ligadata.pmml.udfs.Udfs.abs", ("System", "Double"), List(("expr", "System", "Double")), null)
		mgr.AddFunc("Pmml", "abs", "com.ligadata.pmml.udfs.Udfs.abs", ("System", "Float"), List(("expr", "System", "Float")), null)
		mgr.AddFunc("Pmml", "abs", "com.ligadata.pmml.udfs.Udfs.abs", ("System", "Long"), List(("expr", "System", "Long")), null)
		mgr.AddFunc("Pmml", "abs", "com.ligadata.pmml.udfs.Udfs.abs", ("System", "Int"), List(("expr", "System", "Int")), null)
		mgr.AddFunc("Pmml", "sqrt", "com.ligadata.pmml.udfs.Udfs.sqrt", ("System", "Double"), List(("expr", "System", "Double")), null)
		mgr.AddFunc("Pmml", "ln", "com.ligadata.pmml.udfs.Udfs.ln", ("System", "Double"), List(("expr", "System", "Double")), null)
		mgr.AddFunc("Pmml", "log10", "com.ligadata.pmml.udfs.Udfs.log10", ("System", "Double"), List(("expr", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Product", "com.ligadata.pmml.udfs.Udfs.Product", ("System", "Float"), List(("exprs", "System", "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "Product", "com.ligadata.pmml.udfs.Udfs.Product", ("System", "Double"), List(("exprs", "System", "ArrayOfDouble")), null)
		mgr.AddFunc("Pmml", "Product", "com.ligadata.pmml.udfs.Udfs.Product", ("System", "Long"), List(("exprs", "System", "ArrayOfLong")), null)
		mgr.AddFunc("Pmml", "Product", "com.ligadata.pmml.udfs.Udfs.Product", ("System", "Int"), List(("exprs", "System", "ArrayOfInt")), null)
		mgr.AddFunc("Pmml", "Product", "com.ligadata.pmml.udfs.Udfs.Product", ("System", "Float"), List(("exprs", "System", "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "Product", "com.ligadata.pmml.udfs.Udfs.Product", ("System", "Double"), List(("exprs", "System", "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "Product", "com.ligadata.pmml.udfs.Udfs.Product", ("System", "Long"), List(("exprs", "System", "ArrayBufferOfLong")), null)
		mgr.AddFunc("Pmml", "Product", "com.ligadata.pmml.udfs.Udfs.Product", ("System", "Int"), List(("exprs", "System", "ArrayBufferOfInt")), null)
		mgr.AddFunc("Pmml", "Median", "com.ligadata.pmml.udfs.Udfs.Median", ("System", "Float"), List(("exprs", "System", "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "Median", "com.ligadata.pmml.udfs.Udfs.Median", ("System", "Double"), List(("exprs", "System", "ArrayOfDouble")), null)
		mgr.AddFunc("Pmml", "Median", "com.ligadata.pmml.udfs.Udfs.Median", ("System", "Long"), List(("exprs", "System", "ArrayOfLong")), null)
		mgr.AddFunc("Pmml", "Median", "com.ligadata.pmml.udfs.Udfs.Median", ("System", "Int"), List(("exprs", "System", "ArrayOfInt")), null)
		mgr.AddFunc("Pmml", "Median", "com.ligadata.pmml.udfs.Udfs.Median", ("System", "Float"), List(("exprs", "System", "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "Median", "com.ligadata.pmml.udfs.Udfs.Median", ("System", "Double"), List(("exprs", "System", "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "Median", "com.ligadata.pmml.udfs.Udfs.Median", ("System", "Long"), List(("exprs", "System", "ArrayBufferOfLong")), null)
		mgr.AddFunc("Pmml", "Median", "com.ligadata.pmml.udfs.Udfs.Median", ("System", "Int"), List(("exprs", "System", "ArrayBufferOfInt")), null)
		mgr.AddFunc("Pmml", "Count", "com.ligadata.pmml.udfs.Udfs.Count", ("System", "Float"), List(("exprs", "System", "ListOfFloat")), null)
		mgr.AddFunc("Pmml", "Count", "com.ligadata.pmml.udfs.Udfs.Count", ("System", "Double"), List(("exprs", "System", "ListOfDouble")), null)
		mgr.AddFunc("Pmml", "Count", "com.ligadata.pmml.udfs.Udfs.Count", ("System", "Long"), List(("exprs", "System", "ListOfLong")), null)
		mgr.AddFunc("Pmml", "Count", "com.ligadata.pmml.udfs.Udfs.Count", ("System", "Int"), List(("exprs", "System", "ListOfInt")), null)
		mgr.AddFunc("Pmml", "Count", "com.ligadata.pmml.udfs.Udfs.Count", ("System", "Float"), List(("exprs", "System", "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "Count", "com.ligadata.pmml.udfs.Udfs.Count", ("System", "Double"), List(("exprs", "System", "ArrayOfDouble")), null)
		mgr.AddFunc("Pmml", "Count", "com.ligadata.pmml.udfs.Udfs.Count", ("System", "Long"), List(("exprs", "System", "ArrayOfLong")), null)
		mgr.AddFunc("Pmml", "Count", "com.ligadata.pmml.udfs.Udfs.Count", ("System", "Int"), List(("exprs", "System", "ArrayOfInt")), null)
		mgr.AddFunc("Pmml", "Count", "com.ligadata.pmml.udfs.Udfs.Count", ("System", "Float"), List(("exprs", "System", "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "Count", "com.ligadata.pmml.udfs.Udfs.Count", ("System", "Double"), List(("exprs", "System", "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "Count", "com.ligadata.pmml.udfs.Udfs.Count", ("System", "Long"), List(("exprs", "System", "ArrayBufferOfLong")), null)
		mgr.AddFunc("Pmml", "Count", "com.ligadata.pmml.udfs.Udfs.Count", ("System", "Int"), List(("exprs", "System", "ArrayBufferOfInt")), null)
		mgr.AddFunc("Pmml", "Avg", "com.ligadata.pmml.udfs.Udfs.Avg", ("System", "Float"), List(("exprs", "System", "ListOfFloat")), null)
		mgr.AddFunc("Pmml", "Avg", "com.ligadata.pmml.udfs.Udfs.Avg", ("System", "Double"), List(("exprs", "System", "ListOfDouble")), null)
		mgr.AddFunc("Pmml", "Avg", "com.ligadata.pmml.udfs.Udfs.Avg", ("System", "Long"), List(("exprs", "System", "ListOfLong")), null)
		mgr.AddFunc("Pmml", "Avg", "com.ligadata.pmml.udfs.Udfs.Avg", ("System", "Int"), List(("exprs", "System", "ListOfInt")), null)
		mgr.AddFunc("Pmml", "Avg", "com.ligadata.pmml.udfs.Udfs.Avg", ("System", "Float"), List(("exprs", "System", "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "Avg", "com.ligadata.pmml.udfs.Udfs.Avg", ("System", "Double"), List(("exprs", "System", "ArrayOfDouble")), null)
		mgr.AddFunc("Pmml", "Avg", "com.ligadata.pmml.udfs.Udfs.Avg", ("System", "Long"), List(("exprs", "System", "ArrayOfLong")), null)
		mgr.AddFunc("Pmml", "Avg", "com.ligadata.pmml.udfs.Udfs.Avg", ("System", "Int"), List(("exprs", "System", "ArrayOfInt")), null)
		mgr.AddFunc("Pmml", "Avg", "com.ligadata.pmml.udfs.Udfs.Avg", ("System", "Float"), List(("exprs", "System", "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "Avg", "com.ligadata.pmml.udfs.Udfs.Avg", ("System", "Double"), List(("exprs", "System", "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "Avg", "com.ligadata.pmml.udfs.Udfs.Avg", ("System", "Long"), List(("exprs", "System", "ArrayBufferOfLong")), null)
		mgr.AddFunc("Pmml", "Avg", "com.ligadata.pmml.udfs.Udfs.Avg", ("System", "Int"), List(("exprs", "System", "ArrayBufferOfInt")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("exprs", "System", "ListOfFloat")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Double"), List(("exprs", "System", "ListOfDouble")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Long"), List(("exprs", "System", "ListOfLong")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Int"), List(("exprs", "System", "ListOfInt")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("exprs", "System", "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Double"), List(("exprs", "System", "ArrayOfDouble")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Long"), List(("exprs", "System", "ArrayOfLong")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Int"), List(("exprs", "System", "ArrayOfInt")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("exprs", "System", "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Double"), List(("exprs", "System", "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Long"), List(("exprs", "System", "ArrayBufferOfLong")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Int"), List(("exprs", "System", "ArrayBufferOfInt")), null)
		
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfDouble2")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfFloat2")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfInt2")), null)
		
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfDouble3")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfFloat3")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfInt3")), null)
		
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfDouble4")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfFloat4")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfInt4")), null)
		
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfDouble5")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfFloat5")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfInt5")), null)
		
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfDouble6")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfFloat6")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfInt6")), null)
		
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfDouble7")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfFloat7")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfInt7")), null)
		
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfDouble8")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfFloat8")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfInt8")), null)
		
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfDouble9")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfFloat9")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfInt9")), null)
		
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfDouble10")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfFloat10")), null)
		mgr.AddFunc("Pmml", "Sum", "com.ligadata.pmml.udfs.Udfs.Sum", ("System", "Float"), List(("tup", "System", "TupleOfInt10")), null)
		
		
		
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Float"), List(("exprs", "System", "ListOfFloat")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Double"), List(("exprs", "System", "ListOfDouble")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Long"), List(("exprs", "System", "ListOfLong")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Int"), List(("exprs", "System", "ListOfInt")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Float"), List(("exprs", "System", "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Double"), List(("exprs", "System", "ArrayOfDouble")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Long"), List(("exprs", "System", "ArrayOfLong")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Int"), List(("exprs", "System", "ArrayOfInt")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Float"), List(("exprs", "System", "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Double"), List(("exprs", "System", "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Long"), List(("exprs", "System", "ArrayBufferOfLong")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Int"), List(("exprs", "System", "ArrayBufferOfInt")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Double"), List(("expr1", "System", "Float"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Float"), List(("expr1", "System", "Long"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Double"), List(("expr1", "System", "Long"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Float"), List(("expr1", "System", "Int"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Double"), List(("expr1", "System", "Int"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Double"), List(("expr1", "System", "Int"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Max", "com.ligadata.pmml.udfs.Udfs.Max", ("System", "Int"), List(("expr1", "System", "Int"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Float"), List(("exprs", "System", "ListOfFloat")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Double"), List(("exprs", "System", "ListOfDouble")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Long"), List(("exprs", "System", "ListOfLong")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Int"), List(("exprs", "System", "ListOfInt")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Float"), List(("exprs", "System", "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Double"), List(("exprs", "System", "ArrayOfDouble")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Long"), List(("exprs", "System", "ArrayOfLong")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Int"), List(("exprs", "System", "ArrayOfInt")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Float"), List(("exprs", "System", "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Double"), List(("exprs", "System", "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Long"), List(("exprs", "System", "ArrayBufferOfLong")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Int"), List(("exprs", "System", "ArrayBufferOfInt")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Double"), List(("expr1", "System", "Float"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Float"), List(("expr1", "System", "Long"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Double"), List(("expr1", "System", "Long"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Float"), List(("expr1", "System", "Int"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Double"), List(("expr1", "System", "Int"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Double"), List(("expr1", "System", "Int"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Min", "com.ligadata.pmml.udfs.Udfs.Min", ("System", "Int"), List(("expr1", "System", "Int"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Float"), List(("exprs", "System", "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Double"), List(("exprs", "System", "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Long"), List(("exprs", "System", "ArrayBufferOfLong")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Int"), List(("exprs", "System", "ArrayBufferOfInt")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Double"), List(("expr1", "System", "Float"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Float"), List(("expr1", "System", "Long"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Double"), List(("expr1", "System", "Long"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Float"), List(("expr1", "System", "Int"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Double"), List(("expr1", "System", "Int"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Double"), List(("expr1", "System", "Int"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Divide", "com.ligadata.pmml.udfs.Udfs.Divide", ("System", "Int"), List(("expr1", "System", "Int"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Float"), List(("exprs", "System", "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Double"), List(("exprs", "System", "ArrayOfDouble")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Long"), List(("exprs", "System", "ArrayOfLong")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Int"), List(("exprs", "System", "ArrayOfInt")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Float"), List(("exprs", "System", "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Double"), List(("exprs", "System", "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Long"), List(("exprs", "System", "ArrayBufferOfLong")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Int"), List(("exprs", "System", "ArrayBufferOfInt")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Double"), List(("expr1", "System", "Float"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Float"), List(("expr1", "System", "Long"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Double"), List(("expr1", "System", "Long"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Float"), List(("expr1", "System", "Int"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Double"), List(("expr1", "System", "Int"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Double"), List(("expr1", "System", "Int"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Multiply", "com.ligadata.pmml.udfs.Udfs.Multiply", ("System", "Int"), List(("expr1", "System", "Int"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Float"), List(("exprs", "System", "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Double"), List(("exprs", "System", "ArrayOfDouble")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Long"), List(("exprs", "System", "ArrayOfLong")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Int"), List(("exprs", "System", "ArrayOfInt")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Float"), List(("exprs", "System", "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Double"), List(("exprs", "System", "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Long"), List(("exprs", "System", "ArrayBufferOfLong")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Int"), List(("exprs", "System", "ArrayBufferOfInt")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Double"), List(("expr1", "System", "Float"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Float"), List(("expr1", "System", "Long"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Double"), List(("expr1", "System", "Long"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Float"), List(("expr1", "System", "Int"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Double"), List(("expr1", "System", "Int"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Double"), List(("expr1", "System", "Int"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Minus", "com.ligadata.pmml.udfs.Udfs.Minus", ("System", "Int"), List(("expr1", "System", "Int"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Float"), List(("exprs", "System", "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Double"), List(("exprs", "System", "ArrayOfDouble")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Long"), List(("exprs", "System", "ArrayOfLong")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Int"), List(("exprs", "System", "ArrayOfInt")), null)
		//mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "String"), List(("exprs", "System", "ArrayOfString")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Float"), List(("exprs", "System", "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Double"), List(("exprs", "System", "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Long"), List(("exprs", "System", "ArrayBufferOfLong")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Int"), List(("exprs", "System", "ArrayBufferOfInt")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "String"), List(("exprs", "System", "ArrayBufferOfString")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Double"), List(("expr1", "System", "Float"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Float"), List(("expr1", "System", "Long"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Double"), List(("expr1", "System", "Long"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Float"), List(("expr1", "System", "Float"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Float"), List(("expr1", "System", "Int"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Double"), List(("expr1", "System", "Int"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Double"), List(("expr1", "System", "Int"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Double"),("expr3", "System", "Double"),("expr4", "System", "Double"),("expr5", "System", "Double"),("expr6", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Double"),("expr3", "System", "Double"),("expr4", "System", "Double"),("expr5", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Double"),("expr3", "System", "Double"),("expr4", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Double"),("expr3", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Double"), List(("expr1", "System", "Double"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Long"), List(("expr1", "System", "Int"),("expr2", "System", "Int"),("expr3", "System", "Int"),("expr4", "System", "Int"),("expr5", "System", "Int"),("expr6", "System", "Int"),("expr7", "System", "Int"),("expr8", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Int"), List(("expr1", "System", "Int"),("expr2", "System", "Int"),("expr3", "System", "Int"),("expr4", "System", "Int"),("expr5", "System", "Int"),("expr6", "System", "Int"),("expr7", "System", "Int"),("expr8", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Long"),("expr3", "System", "Long"),("expr4", "System", "Long"),("expr5", "System", "Long"),("expr6", "System", "Long"),("expr7", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Int"), List(("expr1", "System", "Int"),("expr2", "System", "Int"),("expr3", "System", "Int"),("expr4", "System", "Int"),("expr5", "System", "Int"),("expr6", "System", "Int"),("expr7", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Long"),("expr3", "System", "Long"),("expr4", "System", "Long"),("expr5", "System", "Long"),("expr6", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Int"), List(("expr1", "System", "Int"),("expr2", "System", "Int"),("expr3", "System", "Int"),("expr4", "System", "Int"),("expr5", "System", "Int"),("expr6", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Long"),("expr3", "System", "Long"),("expr4", "System", "Long"),("expr5", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Int"), List(("expr1", "System", "Int"),("expr2", "System", "Int"),("expr3", "System", "Int"),("expr4", "System", "Int"),("expr5", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Long"),("expr3", "System", "Long"),("expr4", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Int"), List(("expr1", "System", "Int"),("expr2", "System", "Int"),("expr3", "System", "Int"),("expr4", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Long"), List(("expr1", "System", "Long"),("expr2", "System", "Long"),("expr3", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Int"), List(("expr1", "System", "Int"),("expr2", "System", "Int"),("expr3", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "Int"), List(("expr1", "System", "Int"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Plus", "com.ligadata.pmml.udfs.Udfs.Plus", ("System", "String"), List(("expr1", "System", "String"),("expr2", "System", "String")), null)
		mgr.AddFunc("Pmml", "NotEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Long"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "NotEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Long"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "NotEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "NotEqual", "com.ligadata.pmml.udfs.Udfs.NotEqual", ("System", "Boolean"), List(("expr1", "System", "Boolean"),("expr2", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "NotEqual", "com.ligadata.pmml.udfs.Udfs.NotEqual", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "NotEqual", "com.ligadata.pmml.udfs.Udfs.NotEqual", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "NotEqual", "com.ligadata.pmml.udfs.Udfs.NotEqual", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "NotEqual", "com.ligadata.pmml.udfs.Udfs.NotEqual", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "NotEqual", "com.ligadata.pmml.udfs.Udfs.NotEqual", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "NotEqual", "com.ligadata.pmml.udfs.Udfs.NotEqual", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "NotEqual", "com.ligadata.pmml.udfs.Udfs.NotEqual", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "NotEqual", "com.ligadata.pmml.udfs.Udfs.NotEqual", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "NotEqual", "com.ligadata.pmml.udfs.Udfs.NotEqual", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "NotEqual", "com.ligadata.pmml.udfs.Udfs.NotEqual", ("System", "Boolean"), List(("expr1", "System", "String"),("expr2", "System", "String")), null)
		mgr.AddFunc("Pmml", "Equal", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Long"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Equal", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Long"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Equal", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Equal", "com.ligadata.pmml.udfs.Udfs.Equal", ("System", "Boolean"), List(("expr1", "System", "Boolean"),("expr2", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "Equal", "com.ligadata.pmml.udfs.Udfs.Equal", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Equal", "com.ligadata.pmml.udfs.Udfs.Equal", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Equal", "com.ligadata.pmml.udfs.Udfs.Equal", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Equal", "com.ligadata.pmml.udfs.Udfs.Equal", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Equal", "com.ligadata.pmml.udfs.Udfs.Equal", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Equal", "com.ligadata.pmml.udfs.Udfs.Equal", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Equal", "com.ligadata.pmml.udfs.Udfs.Equal", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Equal", "com.ligadata.pmml.udfs.Udfs.Equal", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Equal", "com.ligadata.pmml.udfs.Udfs.Equal", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Equal", "com.ligadata.pmml.udfs.Udfs.Equal", ("System", "Boolean"), List(("expr1", "System", "String"),("expr2", "System", "String")), null)
		mgr.AddFunc("Pmml", "LessThan", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Long"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "LessThan", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Long"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "LessThan", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "LessThan", "com.ligadata.pmml.udfs.Udfs.LessThan", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "LessThan", "com.ligadata.pmml.udfs.Udfs.LessThan", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "LessThan", "com.ligadata.pmml.udfs.Udfs.LessThan", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "LessThan", "com.ligadata.pmml.udfs.Udfs.LessThan", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "LessThan", "com.ligadata.pmml.udfs.Udfs.LessThan", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "LessThan", "com.ligadata.pmml.udfs.Udfs.LessThan", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "LessThan", "com.ligadata.pmml.udfs.Udfs.LessThan", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "LessThan", "com.ligadata.pmml.udfs.Udfs.LessThan", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "LessThan", "com.ligadata.pmml.udfs.Udfs.LessThan", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "LessThan", "com.ligadata.pmml.udfs.Udfs.LessThan", ("System", "Boolean"), List(("expr1", "System", "String"),("expr2", "System", "String")), null)
		mgr.AddFunc("Pmml", "LessOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Long"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "LessOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Long"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "LessOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "LessOrEqual", "com.ligadata.pmml.udfs.Udfs.LessOrEqual", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "LessOrEqual", "com.ligadata.pmml.udfs.Udfs.LessOrEqual", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "LessOrEqual", "com.ligadata.pmml.udfs.Udfs.LessOrEqual", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "LessOrEqual", "com.ligadata.pmml.udfs.Udfs.LessOrEqual", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "LessOrEqual", "com.ligadata.pmml.udfs.Udfs.LessOrEqual", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "LessOrEqual", "com.ligadata.pmml.udfs.Udfs.LessOrEqual", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "LessOrEqual", "com.ligadata.pmml.udfs.Udfs.LessOrEqual", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "LessOrEqual", "com.ligadata.pmml.udfs.Udfs.LessOrEqual", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "LessOrEqual", "com.ligadata.pmml.udfs.Udfs.LessOrEqual", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "LessOrEqual", "com.ligadata.pmml.udfs.Udfs.LessOrEqual", ("System", "Boolean"), List(("expr1", "System", "String"),("expr2", "System", "String")), null)
		mgr.AddFunc("Pmml", "GreaterOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Long"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "GreaterOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Long"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "GreaterOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "GreaterOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "GreaterOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "GreaterOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "GreaterOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "GreaterOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "GreaterOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "GreaterOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "GreaterOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "GreaterOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "GreaterOrEqual", "com.ligadata.pmml.udfs.Udfs.GreaterOrEqual", ("System", "Boolean"), List(("expr1", "System", "String"),("expr2", "System", "String")), null)
		mgr.AddFunc("Pmml", "GreaterThan", "com.ligadata.pmml.udfs.Udfs.GreaterThan", ("System", "Boolean"), List(("expr1", "System", "Long"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "GreaterThan", "com.ligadata.pmml.udfs.Udfs.GreaterThan", ("System", "Boolean"), List(("expr1", "System", "Long"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "GreaterThan", "com.ligadata.pmml.udfs.Udfs.GreaterThan", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Long")), null)
		mgr.AddFunc("Pmml", "GreaterThan", "com.ligadata.pmml.udfs.Udfs.GreaterThan", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "GreaterThan", "com.ligadata.pmml.udfs.Udfs.GreaterThan", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "GreaterThan", "com.ligadata.pmml.udfs.Udfs.GreaterThan", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "GreaterThan", "com.ligadata.pmml.udfs.Udfs.GreaterThan", ("System", "Boolean"), List(("expr1", "System", "Float"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "GreaterThan", "com.ligadata.pmml.udfs.Udfs.GreaterThan", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Float")), null)
		mgr.AddFunc("Pmml", "GreaterThan", "com.ligadata.pmml.udfs.Udfs.GreaterThan", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "GreaterThan", "com.ligadata.pmml.udfs.Udfs.GreaterThan", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Double")), null)
		mgr.AddFunc("Pmml", "GreaterThan", "com.ligadata.pmml.udfs.Udfs.GreaterThan", ("System", "Boolean"), List(("expr1", "System", "Double"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "GreaterThan", "com.ligadata.pmml.udfs.Udfs.GreaterThan", ("System", "Boolean"), List(("expr1", "System", "Int"),("expr2", "System", "Int")), null)
		mgr.AddFunc("Pmml", "GreaterThan", "com.ligadata.pmml.udfs.Udfs.GreaterThan", ("System", "Boolean"), List(("expr1", "System", "String"),("expr2", "System", "String")), null)
		mgr.AddFunc("Pmml", "Between", "com.ligadata.pmml.udfs.Udfs.Between", ("System", "Boolean"), List(("thisOne", "System", "Float"),("leftMargin", "System", "Float"),("rightMargin", "System", "Float"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "Between", "com.ligadata.pmml.udfs.Udfs.Between", ("System", "Boolean"), List(("thisOne", "System", "Int"),("leftMargin", "System", "Int"),("rightMargin", "System", "Float"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "Between", "com.ligadata.pmml.udfs.Udfs.Between", ("System", "Boolean"), List(("thisOne", "System", "Float"),("leftMargin", "System", "Float"),("rightMargin", "System", "Int"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "Between", "com.ligadata.pmml.udfs.Udfs.Between", ("System", "Boolean"), List(("thisOne", "System", "Float"),("leftMargin", "System", "Float"),("rightMargin", "System", "Double"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "Between", "com.ligadata.pmml.udfs.Udfs.Between", ("System", "Boolean"), List(("thisOne", "System", "Double"),("leftMargin", "System", "Double"),("rightMargin", "System", "Float"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "Between", "com.ligadata.pmml.udfs.Udfs.Between", ("System", "Boolean"), List(("thisOne", "System", "Double"),("leftMargin", "System", "Double"),("rightMargin", "System", "Double"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "Between", "com.ligadata.pmml.udfs.Udfs.Between", ("System", "Boolean"), List(("thisOne", "System", "Int"),("leftMargin", "System", "Int"),("rightMargin", "System", "Double"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "Between", "com.ligadata.pmml.udfs.Udfs.Between", ("System", "Boolean"), List(("thisOne", "System", "Double"),("leftMargin", "System", "Double"),("rightMargin", "System", "Int"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "Between", "com.ligadata.pmml.udfs.Udfs.Between", ("System", "Boolean"), List(("thisOne", "System", "Int"),("leftMargin", "System", "Long"),("rightMargin", "System", "Long"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "Between", "com.ligadata.pmml.udfs.Udfs.Between", ("System", "Boolean"), List(("thisOne", "System", "Long"),("leftMargin", "System", "Long"),("rightMargin", "System", "Long"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "Between", "com.ligadata.pmml.udfs.Udfs.Between", ("System", "Boolean"), List(("thisOne", "System", "Int"),("leftMargin", "System", "Int"),("rightMargin", "System", "Int"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "Between", "com.ligadata.pmml.udfs.Udfs.Between", ("System", "Boolean"), List(("thisOne", "System", "String"),("leftMargin", "System", "String"),("rightMargin", "System", "String"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "IsNotIn", "com.ligadata.pmml.udfs.Udfs.IsNotIn", ("System", "Boolean"), List(("fldRefExpr", "System", "Double"),("setExprs", "System", "ListOfDouble")), null)
		mgr.AddFunc("Pmml", "IsNotIn", "com.ligadata.pmml.udfs.Udfs.IsNotIn", ("System", "Boolean"), List(("fldRefExpr", "System", "Float"),("setExprs", "System", "ListOfFloat")), null)
		mgr.AddFunc("Pmml", "IsNotIn", "com.ligadata.pmml.udfs.Udfs.IsNotIn", ("System", "Boolean"), List(("fldRefExpr", "System", "Int"),("setExprs", "System", "ListOfInt")), null)
		mgr.AddFunc("Pmml", "IsNotIn", "com.ligadata.pmml.udfs.Udfs.IsNotIn", ("System", "Boolean"), List(("fldRefExpr", "System", "String"),("setExprs", "System", "ListOfString")), null)
		mgr.AddFunc("Pmml", "IsNotIn", "com.ligadata.pmml.udfs.Udfs.IsNotIn", ("System", "Boolean"), List(("fldRefExpr", "System", "Double"),("setExprs", "System", "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "IsNotIn", "com.ligadata.pmml.udfs.Udfs.IsNotIn", ("System", "Boolean"), List(("fldRefExpr", "System", "Float"),("setExprs", "System", "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "IsNotIn", "com.ligadata.pmml.udfs.Udfs.IsNotIn", ("System", "Boolean"), List(("fldRefExpr", "System", "Int"),("setExprs", "System", "ArrayBufferOfInt")), null)
		mgr.AddFunc("Pmml", "IsNotIn", "com.ligadata.pmml.udfs.Udfs.IsNotIn", ("System", "Boolean"), List(("fldRefExpr", "System", "String"),("setExprs", "System", "ArrayBufferOfString")), null)
		mgr.AddFunc("Pmml", "Not", "com.ligadata.pmml.udfs.Udfs.Not", ("System", "Boolean"), List(("boolexpr", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "First", "com.ligadata.pmml.udfs.Udfs.First", ("System", "Any"), List(("coll", "System", "SortedSetOfAny")), null)
		mgr.AddFunc("Pmml", "First", "com.ligadata.pmml.udfs.Udfs.First", ("System", "Any"), List(("coll", "System", "QueueOfAny")), null)
		mgr.AddFunc("Pmml", "First", "com.ligadata.pmml.udfs.Udfs.First", ("System", "Any"), List(("coll", "System", "ArrayBufferOfAny")), null)
		mgr.AddFunc("Pmml", "First", "com.ligadata.pmml.udfs.Udfs.First", ("System", "Any"), List(("coll", "System", "ArrayOfAny")), null)
		mgr.AddFunc("Pmml", "Last", "com.ligadata.pmml.udfs.Udfs.Last", ("System", "Any"), List(("coll", "System", "SortedSetOfAny")), null)
		mgr.AddFunc("Pmml", "Last", "com.ligadata.pmml.udfs.Udfs.Last", ("System", "Any"), List(("coll", "System", "QueueOfAny")), null)
		mgr.AddFunc("Pmml", "Last", "com.ligadata.pmml.udfs.Udfs.Last", ("System", "Any"), List(("coll", "System", "ArrayBufferOfAny")), null)
		mgr.AddFunc("Pmml", "Last", "com.ligadata.pmml.udfs.Udfs.Last", ("System", "Any"), List(("coll", "System", "ArrayOfAny")), null)
		mgr.AddFunc("Pmml", "Union", "com.ligadata.pmml.udfs.Udfs.Union", ("System", "SetOfAny"), List(("left", "System", "SetOfAny"),("right", "System", "SetOfAny")), null)
		mgr.AddFunc("Pmml", "Union", "com.ligadata.pmml.udfs.Udfs.Union", ("System", "SetOfAny"), List(("left", "System", "SetOfAny"),("right", "System", "ArrayOfAny")), null)
		mgr.AddFunc("Pmml", "Union", "com.ligadata.pmml.udfs.Udfs.Union", ("System", "SetOfAny"), List(("left", "System", "ArrayOfAny"),("right", "System", "SetOfAny")), null)
		mgr.AddFunc("Pmml", "Union", "com.ligadata.pmml.udfs.Udfs.Union", ("System", "SetOfAny"), List(("left", "System", "ArrayOfAny"),("right", "System", "ArrayOfAny")), null)
		mgr.AddFunc("Pmml", "Union", "com.ligadata.pmml.udfs.Udfs.Union", ("System", "SetOfAny"), List(("left", "System", "ArrayBufferOfAny"),("right", "System", "ArrayBufferOfAny")), null)
		mgr.AddFunc("Pmml", "Intersect", "com.ligadata.pmml.udfs.Udfs.Intersect", ("System", "SetOfAny"), List(("left", "System", "TreeSetOfAny"),("right", "System", "SetOfAny")), null)
		mgr.AddFunc("Pmml", "Intersect", "com.ligadata.pmml.udfs.Udfs.Intersect", ("System", "SetOfAny"), List(("left", "System", "SetOfAny"),("right", "System", "TreeSetOfAny")), null)
		mgr.AddFunc("Pmml", "Intersect", "com.ligadata.pmml.udfs.Udfs.Intersect", ("System", "SetOfAny"), List(("left", "System", "TreeSetOfAny"),("right", "System", "TreeSetOfAny")), null)
		mgr.AddFunc("Pmml", "Intersect", "com.ligadata.pmml.udfs.Udfs.Intersect", ("System", "SetOfAny"), List(("left", "System", "TreeSetOfAny"),("right", "System", "ArrayOfAny")), null)
		mgr.AddFunc("Pmml", "Intersect", "com.ligadata.pmml.udfs.Udfs.Intersect", ("System", "SetOfAny"), List(("left", "System", "ArrayOfAny"),("right", "System", "TreeSetOfAny")), null)
		mgr.AddFunc("Pmml", "Intersect", "com.ligadata.pmml.udfs.Udfs.Intersect", ("System", "SetOfAny"), List(("left", "System", "SetOfAny"),("right", "System", "SetOfAny")), null)
		mgr.AddFunc("Pmml", "Intersect", "com.ligadata.pmml.udfs.Udfs.Intersect", ("System", "SetOfAny"), List(("left", "System", "SetOfAny"),("right", "System", "ArrayOfAny")), null)
		mgr.AddFunc("Pmml", "Intersect", "com.ligadata.pmml.udfs.Udfs.Intersect", ("System", "SetOfAny"), List(("left", "System", "ArrayOfAny"),("right", "System", "SetOfAny")), null)
		mgr.AddFunc("Pmml", "Intersect", "com.ligadata.pmml.udfs.Udfs.Intersect", ("System", "SetOfAny"), List(("left", "System", "ArrayOfAny"),("right", "System", "ArrayOfAny")), null)
		mgr.AddFunc("Pmml", "Contains", "com.ligadata.pmml.udfs.Udfs.Contains", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayOfDouble"),("key", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Contains", "com.ligadata.pmml.udfs.Udfs.Contains", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayOfFloat"),("key", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Contains", "com.ligadata.pmml.udfs.Udfs.Contains", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayOfInt"),("key", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Contains", "com.ligadata.pmml.udfs.Udfs.Contains", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayOfLong"),("key", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Contains", "com.ligadata.pmml.udfs.Udfs.Contains", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayOfString"),("key", "System", "String")), null)
		mgr.AddFunc("Pmml", "Contains", "com.ligadata.pmml.udfs.Udfs.Contains", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayBufferOfDouble"),("key", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Contains", "com.ligadata.pmml.udfs.Udfs.Contains", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayBufferOfFloat"),("key", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Contains", "com.ligadata.pmml.udfs.Udfs.Contains", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayBufferOfInt"),("key", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Contains", "com.ligadata.pmml.udfs.Udfs.Contains", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayBufferOfLong"),("key", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Contains", "com.ligadata.pmml.udfs.Udfs.Contains", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayBufferOfString"),("key", "System", "String")), null)
		
		mgr.AddFunc("Pmml", "Contains", "com.ligadata.pmml.udfs.Udfs.Contains", ("System", "Boolean"), List(("setExpr", "System", "SetOfDouble"),("key", "System", "Double")), null)
		mgr.AddFunc("Pmml", "Contains", "com.ligadata.pmml.udfs.Udfs.Contains", ("System", "Boolean"), List(("setExpr", "System", "SetOfFloat"),("key", "System", "Float")), null)
		mgr.AddFunc("Pmml", "Contains", "com.ligadata.pmml.udfs.Udfs.Contains", ("System", "Boolean"), List(("setExpr", "System", "SetOfInt"),("key", "System", "Int")), null)
		mgr.AddFunc("Pmml", "Contains", "com.ligadata.pmml.udfs.Udfs.Contains", ("System", "Boolean"), List(("setExpr", "System", "SetOfLong"),("key", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Contains", "com.ligadata.pmml.udfs.Udfs.Contains", ("System", "Boolean"), List(("setExpr", "System", "SetOfString"),("key", "System", "String")), null)
		
		mgr.AddFunc("Pmml", "ContainsAny", "com.ligadata.pmml.udfs.Udfs.ContainsAny", ("System", "Boolean"), List(("setExpr", "System", "SetOfString"),("keys", "System", "ArrayOfString")), null)
		mgr.AddFunc("Pmml", "ContainsAny", "com.ligadata.pmml.udfs.Udfs.ContainsAny", ("System", "Boolean"), List(("setExpr", "System", "SetOfLong"),("keys", "System", "ArrayOfLong")), null)
		mgr.AddFunc("Pmml", "ContainsAny", "com.ligadata.pmml.udfs.Udfs.ContainsAny", ("System", "Boolean"), List(("setExpr", "System", "SetOfInt"),("keys", "System", "ArrayOfInt")), null)
		mgr.AddFunc("Pmml", "ContainsAny", "com.ligadata.pmml.udfs.Udfs.ContainsAny", ("System", "Boolean"), List(("setExpr", "System", "SetOfFloat"),("keys", "System", "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "ContainsAny", "com.ligadata.pmml.udfs.Udfs.ContainsAny", ("System", "Boolean"), List(("setExpr", "System", "SetOfDouble"),("keys", "System", "ArrayOfDouble")), null)
		
		mgr.AddFunc("Pmml", "ContainsAny", "com.ligadata.pmml.udfs.Udfs.ContainsAny", ("System", "Boolean"), List(("setExpr", "System", "ImmutableSetOfString"),("keys", "System", "ArrayOfString")), null)
		mgr.AddFunc("Pmml", "ContainsAny", "com.ligadata.pmml.udfs.Udfs.ContainsAny", ("System", "Boolean"), List(("setExpr", "System", "ImmutableSetOfString"),("keys", "System", "ArrayOfLong")), null)
		mgr.AddFunc("Pmml", "ContainsAny", "com.ligadata.pmml.udfs.Udfs.ContainsAny", ("System", "Boolean"), List(("setExpr", "System", "ImmutableSetOfString"),("keys", "System", "ArrayOfInt")), null)
		mgr.AddFunc("Pmml", "ContainsAny", "com.ligadata.pmml.udfs.Udfs.ContainsAny", ("System", "Boolean"), List(("setExpr", "System", "ImmutableSetOfString"),("keys", "System", "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "ContainsAny", "com.ligadata.pmml.udfs.Udfs.ContainsAny", ("System", "Boolean"), List(("setExpr", "System", "ImmutableSetOfString"),("keys", "System", "ArrayOfDouble")), null)
		
		mgr.AddFunc("Pmml", "NotAnyBetween", "com.ligadata.pmml.udfs.Udfs.NotAnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayOfDouble"),("leftMargin", "System", "Double"),("rightMargin", "System", "Double"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "NotAnyBetween", "com.ligadata.pmml.udfs.Udfs.NotAnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayOfFloat"),("leftMargin", "System", "Float"),("rightMargin", "System", "Float"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "NotAnyBetween", "com.ligadata.pmml.udfs.Udfs.NotAnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayOfInt"),("leftMargin", "System", "Int"),("rightMargin", "System", "Int"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "NotAnyBetween", "com.ligadata.pmml.udfs.Udfs.NotAnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayOfLong"),("leftMargin", "System", "Long"),("rightMargin", "System", "Long"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "NotAnyBetween", "com.ligadata.pmml.udfs.Udfs.NotAnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayOfString"),("leftMargin", "System", "String"),("rightMargin", "System", "String"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "NotAnyBetween", "com.ligadata.pmml.udfs.Udfs.NotAnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayBufferOfDouble"),("leftMargin", "System", "Double"),("rightMargin", "System", "Double"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "NotAnyBetween", "com.ligadata.pmml.udfs.Udfs.NotAnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayBufferOfFloat"),("leftMargin", "System", "Float"),("rightMargin", "System", "Float"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "NotAnyBetween", "com.ligadata.pmml.udfs.Udfs.NotAnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayBufferOfLong"),("leftMargin", "System", "Long"),("rightMargin", "System", "Long"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "NotAnyBetween", "com.ligadata.pmml.udfs.Udfs.NotAnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayBufferOfInt"),("leftMargin", "System", "Int"),("rightMargin", "System", "Int"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "NotAnyBetween", "com.ligadata.pmml.udfs.Udfs.NotAnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayBufferOfString"),("leftMargin", "System", "String"),("rightMargin", "System", "String"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "AnyBetween", "com.ligadata.pmml.udfs.Udfs.AnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayOfDouble"),("leftMargin", "System", "Double"),("rightMargin", "System", "Double"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "AnyBetween", "com.ligadata.pmml.udfs.Udfs.AnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayOfFloat"),("leftMargin", "System", "Float"),("rightMargin", "System", "Float"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "AnyBetween", "com.ligadata.pmml.udfs.Udfs.AnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayOfInt"),("leftMargin", "System", "Int"),("rightMargin", "System", "Int"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "AnyBetween", "com.ligadata.pmml.udfs.Udfs.AnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayOfString"),("leftMargin", "System", "String"),("rightMargin", "System", "String"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "AnyBetween", "com.ligadata.pmml.udfs.Udfs.AnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayBufferOfDouble"),("leftMargin", "System", "Double"),("rightMargin", "System", "Double"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "AnyBetween", "com.ligadata.pmml.udfs.Udfs.AnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayBufferOfFloat"),("leftMargin", "System", "Float"),("rightMargin", "System", "Float"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "AnyBetween", "com.ligadata.pmml.udfs.Udfs.AnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayBufferOfLong"),("leftMargin", "System", "Long"),("rightMargin", "System", "Long"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "AnyBetween", "com.ligadata.pmml.udfs.Udfs.AnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayBufferOfInt"),("leftMargin", "System", "Int"),("rightMargin", "System", "Int"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "AnyBetween", "com.ligadata.pmml.udfs.Udfs.AnyBetween", ("System", "Boolean"), List(("arrayExpr", "System", "ArrayBufferOfString"),("leftMargin", "System", "String"),("rightMargin", "System", "String"),("inclusive", "System", "Boolean")), null)

		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "Double"),("setExprs", "System", "ListOfDouble")), null)
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "Float"),("setExprs", "System", "ListOfFloat")), null)
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "Int"),("setExprs", "System", "ListOfInt")), null)
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "String"),("setExprs", "System", "ListOfString")), null)

		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "Double"),("setExprs", "System", "ArrayOfDouble")), null)
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "Float"),("setExprs", "System", "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "Int"),("setExprs", "System", "ArrayOfInt")), null)
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "String"),("setExprs", "System", "ArrayOfString")), null)

		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "Double"),("setExprs", "System", "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "Float"),("setExprs", "System", "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "Int"),("setExprs", "System", "ArrayBufferOfInt")), null)
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "String"),("setExprs", "System", "ArrayBufferOfString")), null)

		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "String"),("setExprs", "System", "SetOfString")), null)
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "String"),("setExprs", "System", "SetOfInt")), null)
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "String"),("setExprs", "System", "SetOfFloat")), null)
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "String"),("setExprs", "System", "SetOfDouble")), null)
				
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "String"),("setExprs", "System", "ImmutableSetOfString")), null)
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "String"),("setExprs", "System", "ImmutableSetOfInt")), null)
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "String"),("setExprs", "System", "ImmutableSetOfFloat")), null)
		mgr.AddFunc("Pmml", "IsIn", "com.ligadata.pmml.udfs.Udfs.IsIn", ("System", "Boolean"), List(("fldRefExpr", "System", "String"),("setExprs", "System", "ImmutableSetOfDouble")), null)
				
		mgr.AddFunc("Pmml", "FoundInAnyRange", "com.ligadata.pmml.udfs.Udfs.FoundInAnyRange", ("System", "Boolean"), List(("fldRefExpr", "System", "String"),("tuples", "System", "ArrayOfTupleOfString2"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "FoundInAnyRange", "com.ligadata.pmml.udfs.Udfs.FoundInAnyRange", ("System", "Boolean"), List(("fldRefExpr", "System", "Int"),("tuples", "System", "ArrayOfTupleOfInt2"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "FoundInAnyRange", "com.ligadata.pmml.udfs.Udfs.FoundInAnyRange", ("System", "Boolean"), List(("fldRefExpr", "System", "Long"),("tuples", "System", "ArrayOfTupleOfLong2"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "FoundInAnyRange", "com.ligadata.pmml.udfs.Udfs.FoundInAnyRange", ("System", "Boolean"), List(("fldRefExpr", "System", "Float"),("tuples", "System", "ArrayOfTupleOfFloat2"),("inclusive", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "FoundInAnyRange", "com.ligadata.pmml.udfs.Udfs.FoundInAnyRange", ("System", "Boolean"), List(("fldRefExpr", "System", "Double"),("tuples", "System", "ArrayOfTupleOfDouble2"),("inclusive", "System", "Boolean")), null)


		mgr.AddFunc("Pmml", "IntAnd", "com.ligadata.pmml.udfs.Udfs.IntAnd", ("System", "Boolean"), List(("boolexpr", "System", "Boolean")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
		mgr.AddFunc("Pmml", "And", "com.ligadata.pmml.udfs.Udfs.And", ("System", "Boolean"), List(("boolexpr", "System", "Boolean")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
		
		mgr.AddFunc("Pmml", "IntOr", "com.ligadata.pmml.udfs.Udfs.IntOr", ("System", "Boolean"), List(("boolexpr", "System", "Boolean")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
		mgr.AddFunc("Pmml", "Or", "com.ligadata.pmml.udfs.Udfs.Or", ("System", "Boolean"), List(("boolexpr", "System", "Boolean")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
		
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", ("System", "Boolean"), List(("xId", "System", "Long"),("gCtx", "System", "EnvContext"),("containerId", "System", "String"),("partKey", "System", "ListOfString"),("value", "System", "BaseMsg")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", ("System", "Boolean"), List(("ctx", "System", "Context"),("containerId", "System", "String"),("partKey", "System", "ListOfString"),("value", "System", "BaseMsg")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", ("System", "Boolean"), List(("xId", "System", "Long"),("gCtx", "System", "EnvContext"),("containerId", "System", "String"),("partKey", "System", "ListOfString"),("value", "System", "BaseContainer")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", ("System", "Boolean"), List(("ctx", "System", "Context"),("containerId", "System", "String"),("partKey", "System", "ListOfString"),("value", "System", "BaseContainer")), null)


		mgr.AddFunc("Pmml", "GetArray", "com.ligadata.pmml.udfs.Udfs.GetArray", ("System", "ArrayOfMessageContainerBase"), List(("xId", "System", "Long"),("gCtx", "System", "EnvContext"),("containerId", "System", "String")), null)
		mgr.AddFunc("Pmml", "GetArray", "com.ligadata.pmml.udfs.Udfs.GetArray", ("System", "ArrayOfMessageContainerBase"), List(("ctx", "System", "Context"),("containerId", "System", "String")), null)
		mgr.AddFunc("Pmml", "GetHistory", "com.ligadata.pmml.udfs.Udfs.GetHistory", ("System", "ArrayOfMessageContainerBase"), List(("xId", "System", "Long"),("gCtx", "System", "EnvContext"),("containerId", "System", "String"),("partKey", "System", "ListOfString"),("appendCurrentChanges", "System", "Boolean")), null)
		mgr.AddFunc("Pmml", "GetHistory", "com.ligadata.pmml.udfs.Udfs.GetHistory", ("System", "ArrayOfMessageContainerBase"), List(("ctx", "System", "Context"),("containerId", "System", "String"),("partKey", "System", "ListOfString"),("appendCurrentChanges", "System", "Boolean")), null)

		mgr.AddFunc("Pmml", "Get", "com.ligadata.pmml.udfs.Udfs.Get", ("System", "MessageContainerBase"), List(("xId", "System", "Long"),("gCtx", "System", "EnvContext"),("containerId", "System", "String"),("partKey", "System", "ListOfString"),("primaryKey", "System", "ListOfString")), null)
		mgr.AddFunc("Pmml", "Get", "com.ligadata.pmml.udfs.Udfs.Get", ("System", "MessageContainerBase"), List(("ctx", "System", "Context"),("containerId", "System", "String"),("partKey", "System", "ListOfString"),("primaryKey", "System", "ListOfString")), null)

		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "String")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayOfString")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayBufferOfString")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ListOfString")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "QueueOfString")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "SetOfString")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ImmutableSetOfString")), null)

		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "Int")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayOfInt")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayBufferOfInt")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ListOfInt")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "QueueOfInt")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "SetOfInt")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ImmutableSetOfInt")), null)

		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "Long")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayOfLong")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayBufferOfLong")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ListOfLong")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "QueueOfLong")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "SetOfLong")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ImmutableSetOfLong")), null)

		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "Float")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ListOfFloat")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "QueueOfFloat")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "SetOfFloat")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ImmutableSetOfFloat")), null)

		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "Double")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayOfDouble")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ListOfDouble")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "QueueOfDouble")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "SetOfDouble")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ImmutableSetOfDouble")), null)

		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "Boolean")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayOfBoolean")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayBufferOfBoolean")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ListOfBoolean")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "QueueOfBoolean")), null)

		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "Any")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayOfAny")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayBufferOfAny")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ListOfAny")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "QueueOfAny")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "SetOfAny")), null)
		mgr.AddFunc("Pmml", "Put", "com.ligadata.pmml.udfs.Udfs.Put", (MdMgr.sysNS, "Boolean"), List(("ctx", "System", "Context"),("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ImmutableSetOfAny")), null)

		mgr.AddFunc("Pmml", "CollectionLength", "com.ligadata.pmml.udfs.Udfs.CollectionLength", ("System", "Int"), List(("coll", "System", "QueueOfAny")), null)
		mgr.AddFunc("Pmml", "CollectionLength", "com.ligadata.pmml.udfs.Udfs.CollectionLength", ("System", "Int"), List(("coll", "System", "ListOfAny")), null)
		mgr.AddFunc("Pmml", "CollectionLength", "com.ligadata.pmml.udfs.Udfs.CollectionLength", ("System", "Int"), List(("coll", "System", "SortedSetOfAny")), null)
		mgr.AddFunc("Pmml", "CollectionLength", "com.ligadata.pmml.udfs.Udfs.CollectionLength", ("System", "Int"), List(("coll", "System", "TreeSetOfAny")), null)
		mgr.AddFunc("Pmml", "CollectionLength", "com.ligadata.pmml.udfs.Udfs.CollectionLength", ("System", "Int"), List(("coll", "System", "ImmutableSetOfAny")), null)
		mgr.AddFunc("Pmml", "CollectionLength", "com.ligadata.pmml.udfs.Udfs.CollectionLength", ("System", "Int"), List(("coll", "System", "SetOfAny")), null)
		mgr.AddFunc("Pmml", "CollectionLength", "com.ligadata.pmml.udfs.Udfs.CollectionLength", ("System", "Int"), List(("coll", "System", "ArrayBufferOfAny")), null)
		mgr.AddFunc("Pmml", "CollectionLength", "com.ligadata.pmml.udfs.Udfs.CollectionLength", ("System", "Int"), List(("coll", "System", "ArrayOfAny")), null)
		mgr.AddFunc("Pmml", "CollectionLength", "com.ligadata.pmml.udfs.Udfs.CollectionLength", ("System", "Int"), List(("coll", "System", "HashMapOfAnyAny")), null)
		mgr.AddFunc("Pmml", "CollectionLength", "com.ligadata.pmml.udfs.Udfs.CollectionLength", ("System", "Int"), List(("coll", "System", "MapOfAnyAny")), null)
		mgr.AddFunc("Pmml", "CollectionLength", "com.ligadata.pmml.udfs.Udfs.CollectionLength", ("System", "Int"), List(("coll", "System", "ImmutableMapOfAnyAny")), null)
		//mgr.AddFunc("Pmml", "CollectionLength", "com.ligadata.pmml.udfs.Udfs.CollectionLength", ("System", "Int"), List(("coll", "System", "Stack[T]")), null)
		//mgr.AddFunc("Pmml", "CollectionLength", "com.ligadata.pmml.udfs.Udfs.CollectionLength", ("System", "Int"), List(("coll", "System", "Vector[T]")), null)

  		mgr.AddFunc("Pmml", "Add", "com.ligadata.pmml.udfs.Udfs.Add", ("System", "ArrayofAny"), List(("coll", "System", "ArrayOfAny"), ("items", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
  		mgr.AddFunc("Pmml", "Add", "com.ligadata.pmml.udfs.Udfs.Add", ("System", "ArrayBufferofAny"), List(("coll", "System", "ArrayBufferOfAny"), ("items", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
  		mgr.AddFunc("Pmml", "Add", "com.ligadata.pmml.udfs.Udfs.Add", ("System", "ListofAny"), List(("coll", "System", "ListOfAny"), ("items", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
  		mgr.AddFunc("Pmml", "Add", "com.ligadata.pmml.udfs.Udfs.Add", ("System", "QueueofAny"), List(("coll", "System", "QueueOfAny"), ("items", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
  		mgr.AddFunc("Pmml", "Add", "com.ligadata.pmml.udfs.Udfs.Add", ("System", "SortedSetofAny"), List(("coll", "System", "SortedSetOfAny"), ("items", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
  		mgr.AddFunc("Pmml", "Add", "com.ligadata.pmml.udfs.Udfs.Add", ("System", "TreeSetofAny"), List(("coll", "System", "TreeSetOfAny"), ("items", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
  		mgr.AddFunc("Pmml", "Add", "com.ligadata.pmml.udfs.Udfs.Add", ("System", "ImmutableSetOfAny"), List(("coll", "System", "ImmutableSetOfAny"), ("items", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
  		mgr.AddFunc("Pmml", "Add", "com.ligadata.pmml.udfs.Udfs.Add", ("System", "SetOfAny"), List(("coll", "System", "SetOfAny"), ("items", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))

  		mgr.AddFunc("Pmml", "GetPartitionKey", "com.ligadata.pmml.udfs.Udfs.GetPartitionKey", ("System", "ListOfString"), List(("msg", "System", "BaseMsg")), null)
  		mgr.AddFunc("Pmml", "GetPartitionKey", "com.ligadata.pmml.udfs.Udfs.GetPartitionKey", ("System", "ListOfString"), List(("msg", "System", "BaseContainer")), null)
  		mgr.AddFunc("Pmml", "GetPrimaryKey", "com.ligadata.pmml.udfs.Udfs.GetPrimaryKey", ("System", "ListOfString"), List(("msg", "System", "BaseMsg")), null)
  		mgr.AddFunc("Pmml", "GetPrimaryKey", "com.ligadata.pmml.udfs.Udfs.GetPrimaryKey", ("System", "ListOfString"), List(("msg", "System", "BaseContainer")), null)
  		
  		mgr.AddFunc("Pmml", "getXid", "com.ligadata.pmml.udfs.Udfs.getXid", ("System", "Long"), List(("ctx", "System", "Context")), null)
 	}
	
	def init_com_ligadata_pmml_udfs_Udfs1 {
	  
		mgr.AddFunc("Pmml", "Between", "com.ligadata.pmml.udfs.Udfs.Between", ("System", "ArrayOfAny"), List(("set", "System", "SetOfAny")), null)

		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfAny"), List(("arr", "System", "ArrayOfAny")), null)
		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfInt"), List(("arr", "System", "ArrayOfInt")), null)
		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfLong"), List(("arr", "System", "ArrayOfLong")), null)
		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfDouble"), List(("arr", "System", "ArrayOfDouble")), null)
		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfFloat"), List(("arr", "System", "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfString"), List(("arr", "System", "ArrayOfString")), null)

		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfAny"), List(("arr", "System", "ArrayBufferOfAny")), null)
		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfInt"), List(("arr", "System", "ArrayBufferOfInt")), null)
		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfLong"), List(("arr", "System", "ArrayBufferOfLong")), null)
		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfDouble"), List(("arr", "System", "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfFloat"), List(("arr", "System", "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfString"), List(("arr", "System", "ArrayBufferOfString")), null)

		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfAny"), List(("arr", "System", "ListOfAny")), null)
		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfInt"), List(("arr", "System", "ListOfInt")), null)
		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfLong"), List(("arr", "System", "ListOfLong")), null)
		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfDouble"), List(("arr", "System", "ListOfDouble")), null)
		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfFloat"), List(("arr", "System", "ListOfFloat")), null)
		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfString"), List(("arr", "System", "ListOfString")), null)

		mgr.AddFunc("Pmml", "ToSet", "com.ligadata.pmml.udfs.Udfs.ToSet", ("System", "SetOfAny"), List(("arr", "System", "QueueOfAny")), null)

		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("arr", "System", "ArrayOfAny")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("arr", "System", "ArrayOfInt")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfLong"), List(("arr", "System", "ArrayOfLong")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("arr", "System", "ArrayOfDouble")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("arr", "System", "ArrayOfFloat")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfBoolean"), List(("arr", "System", "ArrayOfBoolean")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfString"), List(("arr", "System", "ArrayOfString")), null)

		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("arr", "System", "ArrayBufferOfAny")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("arr", "System", "ArrayBufferOfInt")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfLong"), List(("arr", "System", "ArrayBufferOfLong")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("arr", "System", "ArrayBufferOfDouble")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("arr", "System", "ArrayBufferOfFloat")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfBoolean"), List(("arr", "System", "ArrayBufferOfBoolean")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfString"), List(("arr", "System", "ArrayBufferOfString")), null)
 

		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("aList", "System", "ListOfAny")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("aList", "System", "ListOfInt")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfLong"), List(("aList", "System", "ListOfLong")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("aList", "System", "ListOfDouble")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("aList", "System", "ListOfFloat")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfBoolean"), List(("aList", "System", "ListOfBoolean")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfString"), List(("aList", "System", "ListOfString")), null)

 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("aSet", "System", "SetOfAny")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("aSet", "System", "SetOfInt")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfLong"), List(("aSet", "System", "SetOfLong")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("aSet", "System", "SetOfDouble")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("aSet", "System", "SetOfFloat")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfString"), List(("aSet", "System", "SetOfString")), null)


 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("arr", "System", "SortedSetOfAny")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("arr", "System", "TreeSetOfAny")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("arr", "System", "QueueOfAny")), null)
 		//mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("arr", "System", "StackOfAny")), null)
		
		/** ToArray and Sum methods for Tuple<N> */
		/** FIXME:  Add other array conversions for other scalars and tuple combinations */
 		mgr.AddFunc("Pmml", "ToArrayOfFloat", "com.ligadata.pmml.udfs.Udfs.ToArrayOfFloat", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfAny2")), null)
 		mgr.AddFunc("Pmml", "SumToFloat", "com.ligadata.pmml.udfs.Udfs.SumToFloat", ("System", "Float"), List(("tup", "System", "TupleOfAny2")), null)
 		mgr.AddFunc("Pmml", "SumToArrayOfFloat", "com.ligadata.pmml.udfs.Udfs.SumToArrayOfFloat", ("System", "ArrayOfFloat"), List(("tup", "System", "ArrayOfTupleOfAny2")), null)

 		mgr.AddFunc("Pmml", "ToArrayOfDouble", "com.ligadata.pmml.udfs.Udfs.ToArrayOfDouble", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfAny2")), null)
 		mgr.AddFunc("Pmml", "SumToDouble", "com.ligadata.pmml.udfs.Udfs.SumToDouble", ("System", "Double"), List(("tup", "System", "TupleOfAny2")), null)
 		mgr.AddFunc("Pmml", "SumToArrayOfDouble", "com.ligadata.pmml.udfs.Udfs.SumToArrayOfDouble", ("System", "ArrayOfDouble"), List(("tup", "System", "ArrayOfTupleOfAny2")), null)

 		mgr.AddFunc("Pmml", "ToArrayOfInt", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfAny2")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny2")), null)
 		mgr.AddFunc("Pmml", "SumToInt", "com.ligadata.pmml.udfs.Udfs.SumToInt", ("System", "Int"), List(("tup", "System", "TupleOfAny2")), null)
 		mgr.AddFunc("Pmml", "SumToArrayOfInt", "com.ligadata.pmml.udfs.Udfs.SumToArrayOfInt", ("System", "ArrayOfInt"), List(("tup", "System", "ArrayOfTupleOfAny2")), null)


 		mgr.AddFunc("Pmml", "ToArrayOfFloat", "com.ligadata.pmml.udfs.Udfs.ToArrayOfFloat", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfAny3")), null)
 		mgr.AddFunc("Pmml", "SumToFloat", "com.ligadata.pmml.udfs.Udfs.SumToFloat", ("System", "Float"), List(("tup", "System", "TupleOfAny3")), null)
 		mgr.AddFunc("Pmml", "SumToArrayOfFloat", "com.ligadata.pmml.udfs.Udfs.SumToArrayOfFloat", ("System", "ArrayOfFloat"), List(("tup", "System", "ArrayOfTupleOfAny3")), null)

 		mgr.AddFunc("Pmml", "ToArrayOfDouble", "com.ligadata.pmml.udfs.Udfs.ToArrayOfDouble", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfAny3")), null)
 		mgr.AddFunc("Pmml", "SumToDouble", "com.ligadata.pmml.udfs.Udfs.SumToDouble", ("System", "Double"), List(("tup", "System", "TupleOfAny3")), null)
 		mgr.AddFunc("Pmml", "SumToArrayOfDouble", "com.ligadata.pmml.udfs.Udfs.SumToArrayOfDouble", ("System", "ArrayOfDouble"), List(("tup", "System", "ArrayOfTupleOfAny3")), null)

 		mgr.AddFunc("Pmml", "ToArrayOfInt", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfAny3")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny3")), null)
 		mgr.AddFunc("Pmml", "SumToInt", "com.ligadata.pmml.udfs.Udfs.SumToInt", ("System", "Int"), List(("tup", "System", "TupleOfAny3")), null)
 		mgr.AddFunc("Pmml", "SumToArrayOfInt", "com.ligadata.pmml.udfs.Udfs.SumToArrayOfInt", ("System", "ArrayOfInt"), List(("tup", "System", "ArrayOfTupleOfAny3")), null)


 		mgr.AddFunc("Pmml", "ToArrayOfFloat", "com.ligadata.pmml.udfs.Udfs.ToArrayOfFloat", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfAny4")), null)
 		mgr.AddFunc("Pmml", "SumToFloat", "com.ligadata.pmml.udfs.Udfs.SumToFloat", ("System", "Float"), List(("tup", "System", "TupleOfAny4")), null)
 		mgr.AddFunc("Pmml", "SumToArrayOfFloat", "com.ligadata.pmml.udfs.Udfs.SumToArrayOfFloat", ("System", "ArrayOfFloat"), List(("tup", "System", "ArrayOfTupleOfAny4")), null)

 		mgr.AddFunc("Pmml", "ToArrayOfDouble", "com.ligadata.pmml.udfs.Udfs.ToArrayOfDouble", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfAny4")), null)
 		mgr.AddFunc("Pmml", "SumToDouble", "com.ligadata.pmml.udfs.Udfs.SumToDouble", ("System", "Double"), List(("tup", "System", "TupleOfAny4")), null)
 		mgr.AddFunc("Pmml", "SumToArrayOfDouble", "com.ligadata.pmml.udfs.Udfs.SumToArrayOfDouble", ("System", "ArrayOfDouble"), List(("tup", "System", "ArrayOfTupleOfAny4")), null)

 		mgr.AddFunc("Pmml", "ToArrayOfInt", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfAny4")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny4")), null)
 		mgr.AddFunc("Pmml", "SumToInt", "com.ligadata.pmml.udfs.Udfs.SumToInt", ("System", "Int"), List(("tup", "System", "TupleOfAny4")), null)
 		mgr.AddFunc("Pmml", "SumToArrayOfInt", "com.ligadata.pmml.udfs.Udfs.SumToArrayOfInt", ("System", "ArrayOfInt"), List(("tup", "System", "ArrayOfTupleOfAny4")), null)


 		mgr.AddFunc("Pmml", "ToArrayOfFloat", "com.ligadata.pmml.udfs.Udfs.ToArrayOfFloat", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfAny5")), null)
 		mgr.AddFunc("Pmml", "SumToFloat", "com.ligadata.pmml.udfs.Udfs.SumToFloat", ("System", "Float"), List(("tup", "System", "TupleOfAny5")), null)
 		mgr.AddFunc("Pmml", "SumToArrayOfFloat", "com.ligadata.pmml.udfs.Udfs.SumToArrayOfFloat", ("System", "ArrayOfFloat"), List(("tup", "System", "ArrayOfTupleOfAny5")), null)

 		mgr.AddFunc("Pmml", "ToArrayOfDouble", "com.ligadata.pmml.udfs.Udfs.ToArrayOfDouble", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfAny5")), null)
 		mgr.AddFunc("Pmml", "SumToDouble", "com.ligadata.pmml.udfs.Udfs.SumToDouble", ("System", "Double"), List(("tup", "System", "TupleOfAny5")), null)
 		mgr.AddFunc("Pmml", "SumToArrayOfDouble", "com.ligadata.pmml.udfs.Udfs.SumToArrayOfDouble", ("System", "ArrayOfDouble"), List(("tup", "System", "ArrayOfTupleOfAny5")), null)

 		mgr.AddFunc("Pmml", "ToArrayOfInt", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfAny5")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny5")), null)
 		mgr.AddFunc("Pmml", "SumToInt", "com.ligadata.pmml.udfs.Udfs.SumToInt", ("System", "Int"), List(("tup", "System", "TupleOfAny5")), null)
 		mgr.AddFunc("Pmml", "SumToArrayOfInt", "com.ligadata.pmml.udfs.Udfs.SumToArrayOfInt", ("System", "ArrayOfInt"), List(("tup", "System", "ArrayOfTupleOfAny5")), null)


 		mgr.AddFunc("Pmml", "ToArrayOfFloat", "com.ligadata.pmml.udfs.Udfs.ToArrayOfFloat", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfAny6")), null)
 		mgr.AddFunc("Pmml", "SumToFloat", "com.ligadata.pmml.udfs.Udfs.SumToFloat", ("System", "Float"), List(("tup", "System", "TupleOfAny6")), null)
 		mgr.AddFunc("Pmml", "SumToArrayOfFloat", "com.ligadata.pmml.udfs.Udfs.SumToArrayOfFloat", ("System", "ArrayOfFloat"), List(("tup", "System", "ArrayOfTupleOfAny6")), null)

 		mgr.AddFunc("Pmml", "ToArrayOfDouble", "com.ligadata.pmml.udfs.Udfs.ToArrayOfDouble", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfAny6")), null)
 		mgr.AddFunc("Pmml", "SumToDouble", "com.ligadata.pmml.udfs.Udfs.SumToDouble", ("System", "Double"), List(("tup", "System", "TupleOfAny6")), null)
 		mgr.AddFunc("Pmml", "SumToArrayOfDouble", "com.ligadata.pmml.udfs.Udfs.SumToArrayOfDouble", ("System", "ArrayOfDouble"), List(("tup", "System", "ArrayOfTupleOfAny6")), null)

 		mgr.AddFunc("Pmml", "ToArrayOfInt", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfAny6")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny6")), null)
 		mgr.AddFunc("Pmml", "SumToInt", "com.ligadata.pmml.udfs.Udfs.SumToInt", ("System", "Int"), List(("tup", "System", "TupleOfAny6")), null)
 		mgr.AddFunc("Pmml", "SumToArrayOfInt", "com.ligadata.pmml.udfs.Udfs.SumToArrayOfInt", ("System", "ArrayOfInt"), List(("tup", "System", "ArrayOfTupleOfAny6")), null)

 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny7")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny8")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny9")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny10")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny11")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny12")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny13")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny14")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny15")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny16")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny17")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny18")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny19")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny20")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny21")), null)
 		mgr.AddFunc("Pmml", "ToArrayOfBoolean", "com.ligadata.pmml.udfs.Udfs.ToArrayOfInt", ("System", "ArrayOfBoolean"), List(("tup", "System", "TupleOfAny22")), null)

 	
 		//SumToArrayOfFloat(tuples: Array[Tuple5[Any,Any,Any,Any,Any]]): Float
  		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny1")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny2")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny3")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny4")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny5")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny6")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny7")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny8")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny9")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny10")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny11")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny12")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny13")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny14")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny15")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny16")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny17")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny18")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny19")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny20")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny21")), null)
 		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfAny"), List(("tup", "System", "TupleOfAny22")), null)
 		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble1")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat1")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt1")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble2")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat2")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt2")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble3")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat3")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt3")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble4")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat4")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt4")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble5")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat5")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt5")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble6")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat6")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt6")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble7")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat7")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt7")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble8")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat8")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt8")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble9")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat9")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt9")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble10")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat10")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt10")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble11")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat11")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt11")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble12")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat12")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt12")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble13")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat13")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt13")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble14")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat14")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt14")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble15")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat15")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt15")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble16")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat16")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt16")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble17")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat17")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt17")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble18")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat18")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt18")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble19")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat19")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt19")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble20")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat20")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt20")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble21")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat21")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt21")), null)
		
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfDouble"), List(("tup", "System", "TupleOfDouble22")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfFloat"), List(("tup", "System", "TupleOfFloat22")), null)
		mgr.AddFunc("Pmml", "ToArray", "com.ligadata.pmml.udfs.Udfs.ToArray", ("System", "ArrayOfInt"), List(("tup", "System", "TupleOfInt22")), null)

 		mgr.AddFunc("Pmml", "ToMap", "com.ligadata.pmml.udfs.Udfs.ToMap", ("System", "MapOfAnyAny"), List(("set", "System", "SetOfTupleOfAny2")), null)
 		mgr.AddFunc("Pmml", "ToMap", "com.ligadata.pmml.udfs.Udfs.ToMap", ("System", "MapOfAnyAny"), List(("set", "System", "ImmutableSetOfTupleOfAny2")), null)
 		mgr.AddFunc("Pmml", "ToMap", "com.ligadata.pmml.udfs.Udfs.ToMap", ("System", "MapOfAnyAny"), List(("arr", "System", "ArrayBufferOfTupleOfAny2")), null)
 		mgr.AddFunc("Pmml", "ToMap", "com.ligadata.pmml.udfs.Udfs.ToMap", ("System", "MapOfAnyAny"), List(("arr", "System", "ArrayOfTupleOfAny2")), null)
 		mgr.AddFunc("Pmml", "ToMap", "com.ligadata.pmml.udfs.Udfs.ToMap", ("System", "MapOfAnyAny"), List(("set", "System", "SortedSetOfTupleOfAny2")), null)
 		mgr.AddFunc("Pmml", "ToMap", "com.ligadata.pmml.udfs.Udfs.ToMap", ("System", "MapOfAnyAny"), List(("set", "System", "TreeSetOfTupleOfAny2")), null)
 		mgr.AddFunc("Pmml", "ToMap", "com.ligadata.pmml.udfs.Udfs.ToMap", ("System", "MapOfAnyAny"), List(("list", "System", "ListOfTupleOfAny2")), null)
 		mgr.AddFunc("Pmml", "ToMap", "com.ligadata.pmml.udfs.Udfs.ToMap", ("System", "MapOfAnyAny"), List(("queue", "System", "QueueOfTupleOfAny2")), null)
 		// need to add Stack add mechanism to mdmgr first ...
 		//mgr.AddFunc("Pmml", "ToMap", "com.ligadata.pmml.udfs.Udfs.ToMap", ("System", "MapOfAnyAny"), List(("stack", "System", "StackOfTupleOfAny2")), null)
  
 		mgr.AddFunc("Pmml", "Zip", "com.ligadata.pmml.udfs.Udfs.Zip", ("System", "ArrayOfTupleOfAny2"), List(("receiver", "System", "ArrayOfAny"), ("other", "System", "ArrayOfAny")), null)
 		mgr.AddFunc("Pmml", "Zip", "com.ligadata.pmml.udfs.Udfs.Zip", ("System", "ArrayBufferOfTupleOfAny2"), List(("receiver", "System", "ArrayBufferOfAny"), ("other", "System", "ArrayBufferOfAny")), null)
 		mgr.AddFunc("Pmml", "Zip", "com.ligadata.pmml.udfs.Udfs.Zip", ("System", "ListOfTupleOfAny2"), List(("receiver", "System", "ListOfAny"), ("other", "System", "ListOfAny")), null)
 		mgr.AddFunc("Pmml", "Zip", "com.ligadata.pmml.udfs.Udfs.Zip", ("System", "QueueOfTupleOfAny2"), List(("receiver", "System", "QueueOfAny"), ("other", "System", "QueueOfAny")), null)
 		mgr.AddFunc("Pmml", "Zip", "com.ligadata.pmml.udfs.Udfs.Zip", ("System", "SetOfTupleOfAny2"), List(("receiver", "System", "SetOfAny"), ("other", "System", "SetOfAny")), null)
 		mgr.AddFunc("Pmml", "Zip", "com.ligadata.pmml.udfs.Udfs.Zip", ("System", "ImmutableSetOfTupleOfAny2"), List(("receiver", "System", "ImmutableSetOfAny"), ("other", "System", "ImmutableSetOfAny")), null)
 		mgr.AddFunc("Pmml", "Zip", "com.ligadata.pmml.udfs.Udfs.Zip", ("System", "SortedSetOfTupleOfAny2"), List(("receiver", "System", "SortedSetOfAny"), ("other", "System", "SortedSetOfAny")), null)

 		mgr.AddFunc("Pmml", "MapKeys", "com.ligadata.pmml.udfs.Udfs.MapKeys", ("System", "ArrayOfInt"), List(("receiver", "System", "ImmutableMapOfIntAny")), null)
 		mgr.AddFunc("Pmml", "MapKeys", "com.ligadata.pmml.udfs.Udfs.MapKeys", ("System", "ArrayOfAny"), List(("receiver", "System", "ImmutableMapOfAnyAny")), null)
 		mgr.AddFunc("Pmml", "MapValues", "com.ligadata.pmml.udfs.Udfs.MapValues", ("System", "ArrayOfAny"), List(("receiver", "System", "ImmutableMapOfAnyAny")), null)


 		/** time/date functions */
		mgr.AddFunc("Pmml", "AgeCalc", "com.ligadata.pmml.udfs.Udfs.AgeCalc", ("System", "Int"), List(("yyyymmdd", "System", "Int")), null)
		mgr.AddFunc("Pmml", "CompressedTimeHHMMSSCC2Secs", "com.ligadata.pmml.udfs.Udfs.CompressedTimeHHMMSSCC2Secs", ("System", "Int"), List(("compressedTime", "System", "Int")), null)
		mgr.AddFunc("Pmml", "AsCompressedDate", "com.ligadata.pmml.udfs.Udfs.AsCompressedDate", ("System", "Int"), List(("milliSecs", "System", "Long")), null)	
		mgr.AddFunc("Pmml", "MonthFromISO8601Int", "com.ligadata.pmml.udfs.Udfs.MonthFromISO8601Int", ("System", "Int"), List(("dt", "System", "Int")), null)
		mgr.AddFunc("Pmml", "YearFromISO8601Int", "com.ligadata.pmml.udfs.Udfs.YearFromISO8601Int", ("System", "Int"), List(("dt", "System", "Int")), null)
		mgr.AddFunc("Pmml", "DayOfMonthFromISO8601Int", "com.ligadata.pmml.udfs.Udfs.DayOfMonthFromISO8601Int", ("System", "Int"), List(("dt", "System", "Int")), null)

		mgr.AddFunc("Pmml", "AsSeconds", "com.ligadata.pmml.udfs.Udfs.AsSeconds", ("System", "Long"), List(("milliSecs", "System", "Long")), null)
		mgr.AddFunc("Pmml", "Timenow", "com.ligadata.pmml.udfs.Udfs.Timenow", ("System", "Long"), List(), null)
		mgr.AddFunc("Pmml", "Now", "com.ligadata.pmml.udfs.Udfs.Now", ("System", "Long"), List(), null)
		
		mgr.AddFunc("Pmml", "YearsAgo", "com.ligadata.pmml.udfs.Udfs.YearsAgo", ("System", "Long"), List(("numYrs", "System", "Int")), null)

		mgr.AddFunc("Pmml", "YearsAgo", "com.ligadata.pmml.udfs.Udfs.YearsAgo", ("System", "Long"), List(("someDate", "System", "Int"),("numYrs", "System", "Int")), null)
		mgr.AddFunc("Pmml", "MonthsAgo", "com.ligadata.pmml.udfs.Udfs.YearsAgo", ("System", "Long"), List(("numMos", "System", "Int")), null)
		mgr.AddFunc("Pmml", "MonthsAgo", "com.ligadata.pmml.udfs.Udfs.YearsAgo", ("System", "Long"), List(("someDate", "System", "Int"),("numMos", "System", "Int")), null)
		mgr.AddFunc("Pmml", "WeeksAgo", "com.ligadata.pmml.udfs.Udfs.YearsAgo", ("System", "Long"), List(("numWks", "System", "Int")), null)
		mgr.AddFunc("Pmml", "WeeksAgo", "com.ligadata.pmml.udfs.Udfs.YearsAgo", ("System", "Long"), List(("someDate", "System", "Int"),("numWks", "System", "Int")), null)
		mgr.AddFunc("Pmml", "DaysAgo", "com.ligadata.pmml.udfs.Udfs.YearsAgo", ("System", "Long"), List(("numDays", "System", "Int")), null)
		mgr.AddFunc("Pmml", "DaysAgo", "com.ligadata.pmml.udfs.Udfs.YearsAgo", ("System", "Long"), List(("someDate", "System", "Int"),("numDays", "System", "Int")), null)
		mgr.AddFunc("Pmml", "toMillisFromJulian", "com.ligadata.pmml.udfs.Udfs.toMillisFromJulian", ("System", "Long"), List(("yyddd", "System", "Int")), null)
		mgr.AddFunc("Pmml", "CompressedTimeHHMMSSCC2MilliSecs", "com.ligadata.pmml.udfs.Udfs.CompressedTimeHHMMSSCC2MilliSecs", ("System", "Long"), List(("compressedTime", "System", "Int")), null)
		mgr.AddFunc("Pmml", "DaysAgoAsISO8601", "com.ligadata.pmml.udfs.Udfs.DaysAgoAsISO8601", ("System", "Int"), List(("someDate", "System", "Int"),("numDays", "System", "Int")), null)
		mgr.AddFunc("Pmml", "WeeksAgoAsISO8601", "com.ligadata.pmml.udfs.Udfs.WeeksAgoAsISO8601", ("System", "Int"), List(("someDate", "System", "Int"),("numDays", "System", "Int")), null)
		mgr.AddFunc("Pmml", "MonthsAgoAsISO8601", "com.ligadata.pmml.udfs.Udfs.MonthsAgoAsISO8601", ("System", "Int"), List(("someDate", "System", "Int"),("numDays", "System", "Int")), null)
		mgr.AddFunc("Pmml", "YearsAgoAsISO8601", "com.ligadata.pmml.udfs.Udfs.YearsAgoAsISO8601", ("System", "Int"), List(("someDate", "System", "Int"),("numDays", "System", "Int")), null)
        mgr.AddFunc("Pmml", "iso8601DateFmt", "com.ligadata.pmml.udfs.Udfs.iso8601DateFmt", ("System", "String"), List(("fmtStr", "System", "String"),("yyyymmdds", "System", "Int")), null)
        mgr.AddFunc("Pmml", "timestampFmt", "com.ligadata.pmml.udfs.Udfs.timestampFmt", ("System", "String"), List(("fmtStr", "System", "String"),("timestamp", "System", "Long")), null)
        mgr.AddFunc("Pmml", "timeStampFromStr", "com.ligadata.pmml.udfs.Udfs.timeStampFromStr", ("System", "Long"), List(("fmtStr", "System", "String"),("timestampStr", "System", "String")), null)
        mgr.AddFunc("Pmml", "dateFromStr", "com.ligadata.pmml.udfs.Udfs.dateFromStr", ("System", "Long"), List(("fmtStr", "System", "String"),("timestampStr", "System", "String")), null)
        mgr.AddFunc("Pmml", "timeFromStr", "com.ligadata.pmml.udfs.Udfs.timeFromStr", ("System", "Long"), List(("fmtStr", "System", "String"),("timestampStr", "System", "String")), null)
		mgr.AddFunc("Pmml", "dateSecondsSinceYear", "com.ligadata.pmml.udfs.Udfs.dateSecondsSinceYear", ("System", "Long"), List(("yr", "System", "Int")), null)
		mgr.AddFunc("Pmml", "dateDaysSinceYear", "com.ligadata.pmml.udfs.Udfs.dateDaysSinceYear", ("System", "Long"), List(("yr", "System", "Int")), null)
		mgr.AddFunc("Pmml", "dateMilliSecondsSinceMidnight", "com.ligadata.pmml.udfs.Udfs.dateMilliSecondsSinceMidnight", ("System", "Long"), List(), null)
		mgr.AddFunc("Pmml", "dateSecondsSinceMidnight", "com.ligadata.pmml.udfs.Udfs.dateSecondsSinceMidnight", ("System", "Long"), List(), null)
        mgr.AddFunc("Pmml", "dateSecondsSinceMidnight", "com.ligadata.pmml.udfs.Udfs.dateSecondsSinceMidnight", ("System", "Long"), List(("fmtStr", "System", "String"),("timestampStr", "System", "String")), null)
        mgr.AddFunc("Pmml", "dateSecondsSinceMidnight", "com.ligadata.pmml.udfs.Udfs.dateSecondsSinceMidnight", ("System", "Long"), List(("timestamp", "System", "Long")), null)
        mgr.AddFunc("Pmml", "millisecsBetween", "com.ligadata.pmml.udfs.Udfs.millisecsBetween", ("System", "Long"), List(("time1", "System", "Long"),("time2", "System", "Long"),("inclusive", "System", "Boolean")), null)
        mgr.AddFunc("Pmml", "secondsBetween", "com.ligadata.pmml.udfs.Udfs.secondsBetween", ("System", "Long"), List(("time1", "System", "Long"),("time2", "System", "Long"),("inclusive", "System", "Boolean")), null)
        mgr.AddFunc("Pmml", "minutesBetween", "com.ligadata.pmml.udfs.Udfs.minutesBetween", ("System", "Long"), List(("time1", "System", "Long"),("time2", "System", "Long"),("inclusive", "System", "Boolean")), null)
        mgr.AddFunc("Pmml", "hoursBetween", "com.ligadata.pmml.udfs.Udfs.hoursBetween", ("System", "Long"), List(("time1", "System", "Long"),("time2", "System", "Long"),("inclusive", "System", "Boolean")), null)
        mgr.AddFunc("Pmml", "daysBetween", "com.ligadata.pmml.udfs.Udfs.daysBetween", ("System", "Long"), List(("time1", "System", "Long"),("time2", "System", "Long"),("inclusive", "System", "Boolean")), null)
        mgr.AddFunc("Pmml", "weeksBetween", "com.ligadata.pmml.udfs.Udfs.weeksBetween", ("System", "Long"), List(("time1", "System", "Long"),("time2", "System", "Long"),("inclusive", "System", "Boolean")), null)
        mgr.AddFunc("Pmml", "monthsBetween", "com.ligadata.pmml.udfs.Udfs.monthsBetween", ("System", "Long"), List(("time1", "System", "Long"),("time2", "System", "Long"),("inclusive", "System", "Boolean")), null)
        mgr.AddFunc("Pmml", "yearsBetween", "com.ligadata.pmml.udfs.Udfs.yearsBetween", ("System", "Long"), List(("time1", "System", "Long"),("time2", "System", "Long"),("inclusive", "System", "Boolean")), null)

  
		mgr.AddFunc("Pmml", "Version", "com.ligadata.pmml.udfs.Udfs.Version", ("System", "String"), List(("msg", "System", "BaseMsg")), null)
		mgr.AddFunc("Pmml", "Version", "com.ligadata.pmml.udfs.Udfs.Version", ("System", "String"), List(("msg", "System", "BaseContainer")), null)
					
		mgr.AddFunc("Pmml", "GetMsgContainerElseNew", "com.ligadata.pmml.udfs.Udfs.GetMsgContainerElseNew"
				, ("System", "MessageContainerBase")
				, List(("xId", "System", "Long"),("gCtx", "System", "EnvContext"),("fqClassName", "System", "String"),("containerId", "System", "String"),("partKey", "System", "ListOfString"),("primaryKey", "System", "ListOfString")), null)
		mgr.AddFunc("Pmml", "GetMsgContainerElseNew", "com.ligadata.pmml.udfs.Udfs.GetMsgContainerElseNew"
				, ("System", "MessageContainerBase")
				, List(("ctx", "System", "Context"),("fqClassName", "System", "String"),("containerId", "System", "String"),("partKey", "System", "ListOfString"),("primaryKey", "System", "ListOfString")), null)
		
		mgr.AddFunc("Pmml", "ToStringList", "com.ligadata.pmml.udfs.Udfs.ToStringList", ("System", "ListOfString"), List(("args", "System", "Any")), scala.collection.mutable.Set[FcnMacroAttr.Feature](FcnMacroAttr.HAS_INDEFINITE_ARITY))
		mgr.AddFunc("Pmml", "ToString", "com.ligadata.pmml.udfs.Udfs.ToString", ("System", "String"), List(("arg", "System", "Any")), null)
	
	}

	def InitFcns = {
		/** 
		    NOTE: These functions are variable in nature, more like macros than
		    actual functions.  They actually deploy two
		    functions (in most cases): the outer container function (e.g., Map or Filter) and the inner
		    function that will operate on the members of the container in some way.
		    
		    Since we only know the outer function that will be used, only it is
		    described.  The inner function is specified in the pmml and the arguments
		    and function lookup are separately done for it. The inner functions will be one of the 
		    be one of the other udfs that are defined in the core udf lib 
		    (e.g., Between(somefield, low, hi, inclusive) 
		    
		    Note too that only the "Any" version of these container types are defined.
		    The code generation will utilize the real item type of the container
		    to cast the object "down" to the right type. 
		    
		    Note that they all have the "isIterable" boolean set to true.
		    
		    nameSpace: String
		      , name: String
		      , physicalName: String
		      , retTypeNsName: (String, String)
		      , args: List[(String, String, String)]
		      , fmfeatures : Set[FcnMacroAttr.Feature]
		 
		 */
		var fcnMacrofeatures : Set[FcnMacroAttr.Feature] = Set[FcnMacroAttr.Feature]()
		fcnMacrofeatures += FcnMacroAttr.ITERABLE
		logger.debug("MetadataLoad...loading container filter functions")
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerFilter"
					, "com.ligadata.pmml.udfs.Udfs.ContainerFilter"
					, (MdMgr.sysNS, "ArrayOfAny")
					, List(("containerId", MdMgr.sysNS, "ArrayOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerFilter"
					, "com.ligadata.pmml.udfs.Udfs.ContainerFilter"
					, (MdMgr.sysNS, "ArrayBufferOfAny")
					, List(("containerId", MdMgr.sysNS, "ArrayBufferOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerFilter"
					, "com.ligadata.pmml.udfs.Udfs.ContainerFilter"
					, (MdMgr.sysNS, "ListOfAny")
					, List(("containerId", MdMgr.sysNS, "ListOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerFilter"
					, "com.ligadata.pmml.udfs.Udfs.ContainerFilter"
					, (MdMgr.sysNS, "SetOfAny")
					, List(("containerId", MdMgr.sysNS, "SetOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerFilter"
					, "com.ligadata.pmml.udfs.Udfs.ContainerFilter"
					, (MdMgr.sysNS, "ImmutableSetOfAny")
					, List(("containerId", MdMgr.sysNS, "ImmutableSetOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerFilter"
					, "com.ligadata.pmml.udfs.Udfs.ContainerFilter"
					, (MdMgr.sysNS, "TreeSetOfAny")
					, List(("containerId", MdMgr.sysNS, "TreeSetOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerFilter"
					, "com.ligadata.pmml.udfs.Udfs.ContainerFilter"
					, (MdMgr.sysNS, "MapOfAnyAny")
					, List(("containerId", MdMgr.sysNS, "MapOfAnyAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerFilter"
					, "com.ligadata.pmml.udfs.Udfs.ContainerFilter"
					, (MdMgr.sysNS, "HashMapOfAnyAny")
					, List(("containerId", MdMgr.sysNS, "HashMapOfAnyAny"))
					, fcnMacrofeatures)	  


		logger.debug("MetadataLoad...loading container map functions")
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerMap"
					, "com.ligadata.pmml.udfs.Udfs.ContainerMap"
					, (MdMgr.sysNS, "ArrayOfAny")
					, List(("containerId", MdMgr.sysNS, "ArrayOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerMap"
					, "com.ligadata.pmml.udfs.Udfs.ContainerMap"
					, (MdMgr.sysNS, "ArrayBufferOfAny")
					, List(("containerId", MdMgr.sysNS, "ArrayBufferOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerMap"
					, "com.ligadata.pmml.udfs.Udfs.ContainerMap"
					, (MdMgr.sysNS, "ListOfAny")
					, List(("containerId", MdMgr.sysNS, "ListOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerMap"
					, "com.ligadata.pmml.udfs.Udfs.ContainerMap"
					, (MdMgr.sysNS, "SetOfAny")
					, List(("containerId", MdMgr.sysNS, "SetOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerMap"
					, "com.ligadata.pmml.udfs.Udfs.ContainerMap"
					, (MdMgr.sysNS, "ImmutableSetOfAny")
					, List(("containerId", MdMgr.sysNS, "ImmutableSetOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerMap"
					, "com.ligadata.pmml.udfs.Udfs.ContainerMap"
					, (MdMgr.sysNS, "TreeSetOfAny")
					, List(("containerId", MdMgr.sysNS, "TreeSetOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerMap"
					, "com.ligadata.pmml.udfs.Udfs.ContainerMap"
					, (MdMgr.sysNS, "MapOfAnyAny")
					, List(("containerId", MdMgr.sysNS, "MapOfAnyAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "ContainerMap"
					, "com.ligadata.pmml.udfs.Udfs.ContainerMap"
					, (MdMgr.sysNS, "HashMapOfAnyAny")
					, List(("containerId", MdMgr.sysNS, "HashMapOfAnyAny"))
					, fcnMacrofeatures)	  
					
		
		logger.debug("MetadataLoad...loading container groupBy functions")
		mgr.AddFunc(MdMgr.sysNS
					, "GroupBy"
					, "com.ligadata.pmml.udfs.Udfs.GroupBy"
					, (MdMgr.sysNS, "ArrayOfAny")
					, List(("containerId", MdMgr.sysNS, "ArrayOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "GroupBy"
					, "com.ligadata.pmml.udfs.Udfs.GroupBy"
					, (MdMgr.sysNS, "ArrayBufferOfAny")
					, List(("containerId", MdMgr.sysNS, "ArrayBufferOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "GroupBy"
					, "com.ligadata.pmml.udfs.Udfs.GroupBy"
					, (MdMgr.sysNS, "ListOfAny")
					, List(("containerId", MdMgr.sysNS, "ListOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "GroupBy"
					, "com.ligadata.pmml.udfs.Udfs.GroupBy"
					, (MdMgr.sysNS, "SetOfAny")
					, List(("containerId", MdMgr.sysNS, "SetOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "GroupBy"
					, "com.ligadata.pmml.udfs.Udfs.GroupBy"
					, (MdMgr.sysNS, "ImmutableSetOfAny")
					, List(("containerId", MdMgr.sysNS, "ImmutableSetOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "GroupBy"
					, "com.ligadata.pmml.udfs.Udfs.GroupBy"
					, (MdMgr.sysNS, "TreeSetOfAny")
					, List(("containerId", MdMgr.sysNS, "TreeSetOfAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "GroupBy"
					, "com.ligadata.pmml.udfs.Udfs.GroupBy"
					, (MdMgr.sysNS, "MapOfAnyAny")
					, List(("containerId", MdMgr.sysNS, "MapOfAnyAny"))
					, fcnMacrofeatures)	  
		mgr.AddFunc(MdMgr.sysNS
					, "GroupBy"
					, "com.ligadata.pmml.udfs.Udfs.GroupBy"
					, (MdMgr.sysNS, "HashMapOfAnyAny")
					, List(("containerId", MdMgr.sysNS, "HashMapOfAnyAny"))
					, fcnMacrofeatures)	  
					
		
	  
	}
	
	def initMacroDefs {

		logger.debug("MetadataLoad...loading Macro functions")

		
		/** ************************************************************
		 *  
		 *  NOTE: For the Builds portion of the Builds/Does macros that 
		 *  do the class update contexts... make sure the class name is
		 *  unique.  Do this by qualifying the name with all of the arguments
		 *  s.t. there is no confusion between 3 and 4 argument macros that
		 *  have the same class base name.  
		 *  
		 ***************************************************************/
		
		/** catalog the CLASSUPDATE oriented macros: 
		 
	  		"incrementBy(Int,Int)"  
	  		"incrementBy(Double,Double)"  
	  		"incrementBy(Long,Long)"  
		 	"Put(Any,Any,Any)"
		 	"Put(String,String)"
		 	"Put(Int,Int)"
		 	"Put(Long,Long)"
		 	"Put(Double,Double)"
		 	"Put(Boolean,Boolean)"
		 	"Put(Any,Any)"

		 */

		var fcnMacrofeatures : Set[FcnMacroAttr.Feature] = Set[FcnMacroAttr.Feature]()
		fcnMacrofeatures += FcnMacroAttr.CLASSUPDATE
		  
		
		/** Macros Associated with this macro template:
	  		"incrementBy(Any,Int,Int)"  
	  		"incrementBy(Any,Double,Double)"  
	  		"incrementBy(Any,Long,Long)"  
	  		
	  		Something like the following code would cause the macro to be used were
	  		the AlertsToday a FixedField container...
	  		<Apply function="incrementBy">
				<FieldRef field="AlertsToday.Sent"/>
				<Constant dataType="integer">1</Constant> 
			</Apply>
	  		
		 */
		val SetFieldMacroStringFixed : String =  """
	class %1%_%2%_%3%_setField(val ctx : Context, var %1% : %1_type%, val %3% : %3_type%)
	{
	  	def setField  : Boolean = { %1%.%2% = %3%.asInstanceOf[%2_type%]; true }
	} """
		
		val SetFieldMacroStringMapped : String =  """
	class %1%_%2%_%3%_setField(val ctx : Context, var %1% : %1_type%, val %3% : %3_type%)
	{
	  	def setField  : Boolean = { %1%.set("%2%", %3%.asInstanceOf[%2_type%]); true }
	} """
		
		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Int"), ("value", MdMgr.sysNS, "Int"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  
		  
		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "ArrayOfInt"), ("value", MdMgr.sysNS, "ArrayOfInt"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  
		  
		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Double"), ("value", MdMgr.sysNS, "Double"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  
		  
		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "ArrayOfDouble"), ("value", MdMgr.sysNS, "ArrayOfDouble"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  
		  
		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Long"), ("value", MdMgr.sysNS, "Long"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  

		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "ArrayOfLong"), ("value", MdMgr.sysNS, "ArrayOfLong"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  

		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Boolean"), ("value", MdMgr.sysNS, "Boolean"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  

		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "ArrayOfBoolean"), ("value", MdMgr.sysNS, "ArrayOfBoolean"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  

		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "String"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  

		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "ArrayOfString"), ("value", MdMgr.sysNS, "ArrayOfString"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  

		
		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Int"), ("value", MdMgr.sysNS, "Any"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  
		  
		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "ArrayOfInt"), ("value", MdMgr.sysNS, "ArrayOfAny"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  
		  
		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Double"), ("value", MdMgr.sysNS, "Any"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  
		  
		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "ArrayOfDouble"), ("value", MdMgr.sysNS, "ArrayOfAny"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  
		  
		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Long"), ("value", MdMgr.sysNS, "Any"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  

		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "ArrayOfLong"), ("value", MdMgr.sysNS, "ArrayOfAny"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  

		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Boolean"), ("value", MdMgr.sysNS, "Any"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  

		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "ArrayOfBoolean"), ("value", MdMgr.sysNS, "ArrayOfAny"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  

		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "Any"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  

		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "ArrayOfString"), ("value", MdMgr.sysNS, "ArrayOfAny"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  

		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Any"), ("value", MdMgr.sysNS, "Any"))
					, fcnMacrofeatures
					, (SetFieldMacroStringFixed,SetFieldMacroStringMapped))	  

		val SetFieldNullMacroStringFixed : String =  """
	class %1%_%2%_setFieldNull(val ctx : Context, var %1% : %1_type%)
	{
	  	def setFieldNull  : Boolean = { %1%.%2% = null.asInstanceOf[%2_type%]; true }
	} """
		
		val SetFieldNullMacroStringMapped : String =  """
	class %1%_%2%_setFieldNull(val ctx : Context, var %1% : %1_type%)
	{
	  	def setFieldNull  : Boolean = { %1%.set("%2%", null.asInstanceOf[%2_type%]); true }
	} """
		
		mgr.AddMacro(MdMgr.sysNS
					, "setFieldNull"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Any"))
					, fcnMacrofeatures
					, (SetFieldNullMacroStringFixed,SetFieldNullMacroStringMapped))	  


		val SetFieldMacroContainerStringFixed : String =  """
	class %1%_%2%_%3%_%4%_setField(val ctx : Context, var %1% : %1_type%, val %3% : %3_type%)
	{
	  	def setField  : Boolean = { %1%.%2% = %3%.%4%; true }
	} """
		
		val SetFieldMacroContainerStringMapped : String =  """
	class %1%_%2%_%3%_%4%_setField(val ctx : Context, var %1% : %1_type%, val %3% : %3_type%)
	{
	  	def setField  : Boolean = { %1%.set("%2%", %3%.get("%4%").asInstanceOf[%4_type%]); true }
	} """
		
		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("toContainer", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Int"), ("fromContainer", MdMgr.sysNS, "Any"), ("value", MdMgr.sysNS, "Int"))
					, fcnMacrofeatures
					, (SetFieldMacroContainerStringFixed,SetFieldMacroContainerStringMapped))	  
		  
		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("toContainer", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Double"), ("fromContainer", MdMgr.sysNS, "Any"), ("value", MdMgr.sysNS, "Double"))
					, fcnMacrofeatures
					, (SetFieldMacroContainerStringFixed,SetFieldMacroContainerStringMapped))	  
		  
		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("toContainer", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Long"), ("fromContainer", MdMgr.sysNS, "Any"), ("value", MdMgr.sysNS, "Long"))
					, fcnMacrofeatures
					, (SetFieldMacroContainerStringFixed,SetFieldMacroContainerStringMapped))	  
		  
		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("toContainer", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Boolean"), ("fromContainer", MdMgr.sysNS, "Any"), ("value", MdMgr.sysNS, "Boolean"))
					, fcnMacrofeatures
					, (SetFieldMacroContainerStringFixed,SetFieldMacroContainerStringMapped))	  
		  
		mgr.AddMacro(MdMgr.sysNS
					, "setField"
					, (MdMgr.sysNS, "Boolean")
					, List(("toContainer", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "String"), ("fromContainer", MdMgr.sysNS, "Any"), ("value", MdMgr.sysNS, "String"))
					, fcnMacrofeatures
					, (SetFieldMacroContainerStringFixed,SetFieldMacroContainerStringMapped))	  
		  
		/** Macros Associated with this macro template:
	  		"incrementBy(Any,Int,Int)"  
	  		"incrementBy(Any,Double,Double)"  
	  		"incrementBy(Any,Long,Long)"  
	  		
	  		Something like the following code would cause the macro to be used were
	  		the AlertsToday a FixedField container...
	  		<Apply function="incrementBy">
				<FieldRef field="AlertsToday.Sent"/>
				<Constant dataType="integer">1</Constant> 
			</Apply>
	  		
		 */
		val incrementByMacroStringFixed : String =  """
	class %1%_%2%_%3%_incrementBy(val ctx : Context, var %1% : %1_type%, val %3% : %3_type%)
	{
	  	def incrementBy  : Boolean = { %1%.%2% += %3%; true }
	} """
		
		val incrementByMacroStringMapped : String =  """
	class %1%_%2%_%3%_incrementBy(val ctx : Context, var %1% : %1_type%, val %3% : %3_type%)
	{
	  	def incrementBy  : Boolean = { %1%.set("%2%", (%1%.get("%2%").asInstanceOf[%2_type%] + %3%)); true }
	} """
		
		mgr.AddMacro(MdMgr.sysNS
					, "incrementBy"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Int"), ("value", MdMgr.sysNS, "Int"))
					, fcnMacrofeatures
					, (incrementByMacroStringFixed,incrementByMacroStringMapped))	  
		  
		mgr.AddMacro(MdMgr.sysNS
					, "incrementBy"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Double"), ("value", MdMgr.sysNS, "Double"))
					, fcnMacrofeatures
					, (incrementByMacroStringFixed,incrementByMacroStringMapped))	  
		  
		mgr.AddMacro(MdMgr.sysNS
					, "incrementBy"
					, (MdMgr.sysNS, "Boolean")
					, List(("container", MdMgr.sysNS, "Any"), ("containerField", MdMgr.sysNS, "Long"), ("value", MdMgr.sysNS, "Long"))
					, fcnMacrofeatures
					, (incrementByMacroStringFixed,incrementByMacroStringMapped))	  

		/** **************************************************************************************************************/
					
		val putGlobalContainerFixedMacroTemplate : String =  """
	class %1%_%2%_%3%_%4%_Put(val ctx : Context, var %1% : %1_type%, val %2% : %2_type%, val %3% : %3_type%, val %4% : %4_type%)
	{
	  	def Put  : Boolean = { %1%.setObject(ctx.xId, %2%, %3%, %4%); true }
	} """
		
		val putGlobalContainerMappedMacroTemplate : String =  """
	class %1%_%2%_%3%_%4%_Put(val ctx : Context, var %1% : %1_type%, val %2% : %2_type%, val %3% : %3_type%, val %4% : %4_type%)
	{
	  	def Put  : Boolean = { %1%.setObject(ctx.xId, %2%, %3%, %4%); true }
	} """
		
		/**	EnvContext write access methods:
		 * 	  def setObject(transId: Long, containerName: String, key: String, value: MessageContainerBase): Unit
		 *	  def setObject(transId: Long, containerName: String, key: Any, value: MessageContainerBase): Unit
		 
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("gCtx", MdMgr.sysNS, "EnvContext")
						, ("containerName", MdMgr.sysNS, "String")
						, ("key", MdMgr.sysNS, "ListOfString")
						, ("value", MdMgr.sysNS, "MessageContainerBase"))
					, fcnMacrofeatures
					, (putGlobalContainerFixedMacroTemplate,putGlobalContainerMappedMacroTemplate))	  
		*/
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("gCtx", MdMgr.sysNS, "EnvContext")
						, ("containerName", MdMgr.sysNS, "String")
						, ("key", MdMgr.sysNS, "ListOfString")
						, ("value", MdMgr.sysNS, "BaseMsg"))
					, fcnMacrofeatures
					, (putGlobalContainerFixedMacroTemplate,putGlobalContainerMappedMacroTemplate))	  

		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("gCtx", MdMgr.sysNS, "EnvContext")
						, ("containerName", MdMgr.sysNS, "String")
						, ("key", MdMgr.sysNS, "ListOfString")
						, ("value", MdMgr.sysNS, "BaseContainer"))
					, fcnMacrofeatures
					, (putGlobalContainerFixedMacroTemplate,putGlobalContainerMappedMacroTemplate))	  

		/**
		val putLongVariableMacroPmmlDict : String =    """
	class %1%_%2%_PutLong(val ctx : Context, var %1% : %1_type%, val %2% : %2_type%)
	{
		 //resort to setting the Long value to local variable to insure scala compiler recognizes the appropriate coercion...
		 // 	with a constant as the value present, it will match to Int and fail for large values
	  	def Put  : Boolean = { val l : %2_type% = %2%; Put(ctx, %1%, l); true }
	} """

		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "Long"))
					, fcnMacrofeatures
					, (putLongVariableMacroPmmlDict,putLongVariableMacroPmmlDict))	  
 */		  

		/** **************************************************************************************************************/

		/** ***********************************************************************
		 *  Catalog the ITERABLE only macros (no class generation needed for these 
		 **************************************************************************/
		fcnMacrofeatures.clear
		fcnMacrofeatures += FcnMacroAttr.ITERABLE

		/** 
		 	Macros associated with the 'putVariableMacroPmmlDict' macro template:
			 	"Put(String,String)"
			 	"Put(String,Int)"
			 	"Put(String,Long)"
			 	"Put(String,Double)"
			 	"Put(String,Boolean)"
			 	"Put(String,Any)"
		 	
		 	Notes: 
		 		1) No "mapped" version of the template needed for this case.
		 		2) These functions can ONLY be used inside objects that have access to the model's ctx
		 		   (e.g., inside the 'execute(ctx : Context)' function of a derived field)
		 */
		
		val putVariableMacroPmmlDict : String =   """Put(ctx, %1%, %2%)"""

		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "String"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayOfString"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayBufferOfString"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ListOfString"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "QueueOfString"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "SetOfString"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ImmutableSetOfString"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  

		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "Int"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict))	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayOfInt"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayBufferOfInt"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ListOfInt"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "QueueOfInt"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "SetOfInt"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ImmutableSetOfInt"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  

		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "Long"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict))	  

		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayOfLong"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayBufferOfLong"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ListOfLong"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "QueueOfLong"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "SetOfLong"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ImmutableSetOfLong"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  

		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "Double"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict))	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayOfDouble"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayBufferOfDouble"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ListOfDouble"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "QueueOfDouble"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "SetOfDouble"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ImmutableSetOfDouble"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  

		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "Float"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict))	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayOfFloat"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayBufferOfFloat"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ListOfFloat"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "QueueOfFloat"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "SetOfFloat"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ImmutableSetOfFloat"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  

		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "Boolean"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict))	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayOfBoolean"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayBufferOfBoolean"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ListOfBoolean"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "QueueOfBoolean"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  

		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "Any"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict))	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayOfAny"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ArrayBufferOfAny"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ListOfAny"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "QueueOfAny"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "SetOfAny"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Put"
					, (MdMgr.sysNS, "Boolean")
					, List(("variableName", MdMgr.sysNS, "String"), ("value", MdMgr.sysNS, "ImmutableSetOfAny"))
					, fcnMacrofeatures
					, (putVariableMacroPmmlDict,putVariableMacroPmmlDict)
					,-1)	  
		  

		/** 
		 *  isMissing and isNotMissing macros
		 *  no special macro needed for mapped ... 
		 */
		val isMissingMacro : String =   """IsMissing(ctx, %1%)"""
		val isNotMissingMacro : String =   """IsNotMissing(ctx, %1%)"""

		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "isMissing"
					, (MdMgr.sysNS, "Boolean")
					, List(("fieldRefName", MdMgr.sysNS, "String"))
					, fcnMacrofeatures
					, (isMissingMacro,isMissingMacro)
					,-1)	  
		  
		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "isNotMissing"
					, (MdMgr.sysNS, "Boolean")
					, List(("fieldRefName", MdMgr.sysNS, "String"))
					, fcnMacrofeatures
					, (isNotMissingMacro,isNotMissingMacro)
					,-1)	  
		  
		/** 
		 *  Transaction id access
		 *  no special macro needed for mapped ... 
		 */
		val getXidMacro : String =   """ctx.xId"""

		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "GetXid"
					, (MdMgr.sysNS, "Long")
					, List()
					, fcnMacrofeatures
					, (getXidMacro,getXidMacro)
					,-1)	  

		/** 
			DowncastArrayMbr Macro used to cast arrays of MessageContainerBase to arrays of some specified type
		 */			
		val DowncastArrayMbrTemplate : String =   """%1%.map(itm => itm.asInstanceOf[%2%])"""
					
		mgr.AddMacro(MdMgr.sysNS
					, "DownCastArrayMembers"
					, (MdMgr.sysNS, "ArrayOfAny")
					, List(("arrayExpr", MdMgr.sysNS, "ArrayOfAny"), ("mbrType", MdMgr.sysNS, "Any"))
					, fcnMacrofeatures
					, (DowncastArrayMbrTemplate,DowncastArrayMbrTemplate))	  
					
					
		/** 
		    Catalog EnvContext read access macros.  Inject the transaction id as the first arg   

			def getAllObjects(transId: Long, containerName: String): Array[MessageContainerBase]
			def getObject(transId: Long, containerName: String, key: String): MessageContainerBase
			
			def contains(transId: Long, containerName: String, key: String): Boolean
			def containsAny(transId: Long, containerName: String, keys: Array[String]): Boolean
			def containsAll(transId: Long, containerName: String, keys: Array[String]): Boolean
		*/

		val getAllObjectsMacroTemplate : String =   """GetArray(ctx.xId, %1%, %2%)"""

		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "GetArray"
					, (MdMgr.sysNS, "ArrayOfMessageContainerBase")
					, List(("gCtx", MdMgr.sysNS, "EnvContext")
					    , ("containerName", MdMgr.sysNS, "String"))
					, fcnMacrofeatures
					, (getAllObjectsMacroTemplate,getAllObjectsMacroTemplate)
					,-1)	  

		val getHistoryMacroTemplate : String =   """GetHistory(ctx.xId, %1%, %2%, %3%, %4%)"""

		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "GetHistory"
					, (MdMgr.sysNS, "ArrayOfMessageContainerBase")
					, List(("gCtx", MdMgr.sysNS, "EnvContext")
					    , ("containerName", MdMgr.sysNS, "String")
					    , ("partKey", MdMgr.sysNS, "ListOfString")
					    , ("appendCurrentChanges", MdMgr.sysNS, "Boolean"))
					, fcnMacrofeatures
					, (getHistoryMacroTemplate,getHistoryMacroTemplate)
					,-1)	  

		val getObjectMacroTemplate : String =   """Get(ctx.xId, %1%, %2%, %3%, %4%)"""

		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "Get"
					, (MdMgr.sysNS, "MessageContainerBase")
					, List(("gCtx", MdMgr.sysNS, "EnvContext")
					    , ("containerName", MdMgr.sysNS, "String")
					    , ("partKey", MdMgr.sysNS, "ListOfString")
					    , ("primaryKey", MdMgr.sysNS, "ListOfString"))
					, fcnMacrofeatures
					, (getObjectMacroTemplate,getObjectMacroTemplate)
					,-1)	  


		val getObjectElseNewMacroTemplate : String =   """GetMsgContainerElseNew(ctx.xId, %1%, %2%, %3%, %4%, %5%)"""

		/** @deprecated("Use <Constant dataType="context">ctx</Constant> as the first arg and match function directly", "2015-Jun-08") */
		mgr.AddMacro(MdMgr.sysNS
					, "GetMsgContainerElseNew"
					, (MdMgr.sysNS, "MessageContainerBase")
					, List(("gCtx", MdMgr.sysNS, "EnvContext")
					    , ("fqClassName", MdMgr.sysNS, "String")
					    , ("containerName", MdMgr.sysNS, "String")
					    , ("partKey", MdMgr.sysNS, "ListOfString")
					    , ("primaryKey", MdMgr.sysNS, "ListOfString"))
					, fcnMacrofeatures
					, (getObjectElseNewMacroTemplate,getObjectElseNewMacroTemplate)
					,-1)

					
		val containsMacroTemplate : String =   """Contains(ctx.xId, %1%, %2%, %3%, %4%)"""

		mgr.AddMacro(MdMgr.sysNS
					, "Contains"
					, (MdMgr.sysNS, "Boolean")
					, List(("gCtx", MdMgr.sysNS, "EnvContext")
					    , ("containerName", MdMgr.sysNS, "String")
					    , ("partKey", MdMgr.sysNS, "ListOfString")
					    , ("primaryKey", MdMgr.sysNS, "ListOfString"))
					, fcnMacrofeatures
					, (containsMacroTemplate,containsMacroTemplate)
					,-1)	  

/*
		val containsAnyMacroTemplate : String =   """ContainsAny(ctx.xId, %1%, %2%, %3%)"""

		mgr.AddMacro(MdMgr.sysNS
					, "ContainsAny"
					, (MdMgr.sysNS, "Boolean")
					, List(("gCtx", MdMgr.sysNS, "EnvContext")
					    , ("containerName", MdMgr.sysNS, "String")
					    , ("key", MdMgr.sysNS, "ArrayOfString"))
					, fcnMacrofeatures
					, (containsAnyMacroTemplate,containsAnyMacroTemplate)
					,-1)	  

		val containsAllMacroTemplate : String =   """ContainsAll(ctx.xId, %1%, %2%, %3%)"""

		mgr.AddMacro(MdMgr.sysNS
					, "ContainsAll"
					, (MdMgr.sysNS, "Boolean")
					, List(("gCtx", MdMgr.sysNS, "EnvContext")
					    , ("containerName", MdMgr.sysNS, "String")
					    , ("key", MdMgr.sysNS, "ArrayOfString"))
					, fcnMacrofeatures
					, (containsAllMacroTemplate,containsAllMacroTemplate)
					,-1)	  
*/
	}

}

