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

package com.ligadata.pmml.udfs

import scala.reflect.ClassTag
import scala.collection.GenSeq
import scala.collection.mutable._
import scala.math._
import scala.collection.immutable.StringLike
import scala.collection.immutable.List
import scala.collection.immutable.Map
import scala.collection.immutable.Set
import scala.collection.immutable.Iterable
import scala.collection.mutable.{ Map => MutableMap }
import scala.collection.mutable.{ Set => MutableSet }
import scala.collection.mutable.{ Iterable => MutableIterable }
import scala.collection.mutable.ArraySeq
import scala.collection.mutable.TreeSet
import scala.collection.GenSeq
import scala.reflect.ClassTag
import java.util.UUID

import org.joda.time.base
import org.joda.time.chrono
import org.joda.time.convert
import org.joda.time.field
import org.joda.time.format
import org.joda.time.tz
import org.joda.time.{LocalDate, DateTime, Duration, LocalTime, LocalDateTime}
import org.joda.time.Months
import org.joda.time.Years
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.DateTimeParser
import org.joda.time.chrono.JulianChronology

import org.apache.logging.log4j.{ Logger, LogManager }

import com.ligadata.pmml.runtime._
import com.ligadata.Exceptions.StackTrace
import com.ligadata.KamanjaBase._

/**
 * These are the udfs supplied with the system.
 */
object Udfs extends LogTrait {
  
  /** 
      Answer the version number of the supplied BaseMsg
      @param msg : A BaseMsg known to the model.
      @return the version number, a string in the format "NNNNNN.NNNNNN.NNNNNN"
  */

  def Version(msg : BaseMsg) : String = {
      val ver : String = if (msg != null) {
          msg.Version
      } else {
          "000000.000000.000000"
      }
      ver
  }

  /** 
      Answer the version number of the supplied BaseMsg
      @param container : A BaseMsg known to the model.
      @return the version number, a string in the format "NNNNNN.NNNNNN.NNNNNN"
  */

  def Version(container : BaseContainer) : String = {
      val ver : String = if (container != null) {
          container.Version
      } else {
          "000000.000000.000000"
      }
      ver
  }

  /** 
    @deprecated("Use Contains(ctx: Context, containerName: String, partKey: List[String], primaryKey: List[String]): Boolean ", "2015-Jun-08")
    Answer whether the supplied container and key exist in the storage manged by the global context
    @param xId : the transaction id that initialized the model instance that is calling
    @param gCtx : the EnvContext object that initialized the model instance that is calling
    @param containerName : The top level container name that purportedly contains the companion 'key'
    @param key : the object identifer of interest that purportedly lives in supplied 'containerName'
    @return true if the object exists
   */

  def Contains(xId: Long, gCtx: EnvContext, containerName: String, partKey: List[String], primaryKey: List[String]): Boolean = {
    val itExists: Boolean = if (gCtx != null) gCtx.contains(xId, containerName, partKey, primaryKey) else false
    itExists
  }
  /** 
    Answer whether the supplied container and key exist in the storage manged by the global context
    @param ctx : the runtime Context for the calling model instance
    @param containerName : The top level container name that purportedly contains the companion 'key'
    @param key : the object identifer of interest that purportedly lives in supplied 'containerName'
    @return true if the object exists
   */

  def Contains(ctx: Context, containerName: String, partKey: List[String], primaryKey: List[String]): Boolean = {
    val itExists: Boolean = if (ctx != null && ctx.gCtx != null) ctx.gCtx.contains(ctx.xId, containerName, partKey, primaryKey) else false
    itExists
  }
  
  /** 
    @deprecated("Use ContainsAny(ctx: Context, containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): Boolean ", "2015-Jun-08")
    Answer whether ANY of the supplied keys exist in the supplied container.
    @param xId : the transaction id that initialized the model instance that is calling
    @param gCtx : the EnvContext object that initialized the model instance that is calling
    @param containerName : The top level container name that purportedly contains the companion 'key'
    @param keys : an array of identifiers sought in the 'containerName'
    @return true if the object exists
   */
  def ContainsAny(xId: Long, gCtx: EnvContext, containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): Boolean = {
    val itExists: Boolean = if (gCtx != null) gCtx.containsAny(xId, containerName, partKeys, primaryKeys) else false
    itExists
  }
  /** 
    Answer whether ANY of the supplied keys exist in the supplied container.
    @param ctx : the runtime Context for the calling model instance
    @param containerName : The top level container name that purportedly contains the companion 'key'
    @param keys : an array of identifiers sought in the 'containerName'
    @return true if any of the supplied keys are found
   */
  def ContainsAny(ctx: Context, containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): Boolean = {
    val itExists: Boolean = if (ctx != null && ctx.gCtx != null) ctx.gCtx.containsAny(ctx.xId, containerName, partKeys, primaryKeys) else false
    itExists
  }

  /** 
    @deprecated("Use ContainsAll(ctx: Context, containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): Boolean ", "2015-Jun-08")
    Answer whether ALL of the supplied keys exist in the supplied container.
    @param xId : the transaction id that initialized the model instance that is calling
    @param gCtx : the EnvContext object that initialized the model instance that is calling
    @param containerName : The top level container name that purportedly contains the companion 'key'
    @param keys : an array of identifiers sought in the 'containerName'
    @return true if the object exists
   */
  def ContainsAll(xId: Long, gCtx: EnvContext, containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): Boolean = {
    val allExist: Boolean = if (gCtx != null) gCtx.containsAll(xId, containerName, partKeys, primaryKeys) else false
    allExist
  }
  /** 
    Answer whether ALL of the supplied keys exist in the supplied container.
    @param ctx : the runtime Context for the calling model instance
    @param containerName : The top level container name that purportedly contains the companion 'key'
    @param keys : an array of identifiers sought in the 'containerName'
    @return true if the object exists
   */
  def ContainsAll(ctx: Context, containerName: String, partKeys: Array[List[String]], primaryKeys: Array[List[String]]): Boolean = {
    val allExist: Boolean = if (ctx != null && ctx.gCtx != null) ctx.gCtx.containsAll(ctx.xId, containerName, partKeys, primaryKeys) else false
    allExist
  }


  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: String): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new StringDataValue(value))
    }
    set
  }

  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: Array[String]): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new AnyDataValue(value))
    }
    set
  }

  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: ArrayBuffer[String]): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new AnyDataValue(value))
    }
    set
  }

  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: List[String]): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new AnyDataValue(value))
    }
    set
  }

  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: Queue[String]): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new AnyDataValue(value))
    }
    set
  }

  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: Set[String]): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new AnyDataValue(value))
    }
    set
  }

  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: MutableSet[String]): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new AnyDataValue(value))
    }
    set
  }


  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: Int): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new IntDataValue(value))
    }
    set
  }

  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: Array[Int]): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new AnyDataValue(value))
    }
    set
  }

  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: Long): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new LongDataValue(value))
    }
    set
  }

  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: Array[Long]): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new AnyDataValue(value))
    }
    set
  }

  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: Float): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new FloatDataValue(value))
    }
    set
  }

  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: Array[Float]): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new AnyDataValue(value))
    }
    set
  }

  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: Double): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new DoubleDataValue(value))
    }
    set
  }

  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: Array[Double]): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new AnyDataValue(value))
    }
    set
  }


  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: Boolean): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new BooleanDataValue(value))
    }
    set
  }

  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: Array[Boolean]): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new AnyDataValue(value))
    }
    set
  }


  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: Any): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new AnyDataValue(value))
    }
    set
  }

  /**
      Put the supplied 'value' into the dictionary field named 'variableName'.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def Put(ctx: Context, variableName: String, value: Array[Any]): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valuePut(variableName, new AnyDataValue(value))
    }
    set
  }


  /** runtime state increment function */

  /**
      Locate the 'variableName' in the supplied context and increment it by the 'value' supplied.
      @param ctx : the global state singleton for the calling model instance that contains the variables
      @param variableName : the name of the field in either the data or transaction dictionary found in the ctx
      @param value : the value to store.
      @return if it was set, true else false
   */
  def incrementBy(ctx: Context, variableName: String, value: Int): Boolean = {
    var set: Boolean = (ctx != null)
    if (set) {
      set = ctx.valueIncr(variableName, value)
    }
    set
  }

  /**
	  @deprecated("Use Get(ctx: Context, containerId: String, partKey: List[String], primaryKey: List[String]): MessageContainerBase", "2015-Jun-08")      
	  Get the MessageContainerBase associated with the supplied containerId and key from the EnvContext managed stores.
      @param xId : the transaction id that initialized the model instance that is calling
      @param gCtx : the EnvContext object that initialized the model instance that is calling
      @param containerName : The top level container name that purportedly contains the companion 'key'
      @param key : the identifying key sought in the 'containerName'
      @return the MessageContainerBase associated with these keys.
   */
  def Get(xId: Long, gCtx: EnvContext, containerId: String, partKey: List[String], primaryKey: List[String]): MessageContainerBase = {
    gCtx.getObject(xId, containerId, partKey, primaryKey)
  }

  /**
      Get the MessageContainerBase associated with the supplied containerId and key from the EnvContext managed stores.
   *  @param ctx : the Pmml Runtime Context instance for the calling model
      @param containerName : The top level container name that purportedly contains the companion 'key'
      @param key : the identifying key sought in the 'containerName'
      @return the MessageContainerBase associated with these keys.
   */
  def Get(ctx: Context, containerId: String, partKey: List[String], primaryKey: List[String]): MessageContainerBase = {
	  if (ctx != null && ctx.gCtx != null) ctx.gCtx.getObject(ctx.xId, containerId, partKey, primaryKey) else null
  }


  /** 
	  @deprecated("Use GetMsgContainerElseNew(ctx: Context, fqClassName : String, containerId: String, partKey: List[String], primaryKey: List[String]): MessageContainerBase", "2015-Jun-08")      
	  Get the MessageContainerBase associated with the supplied containerId and key from the EnvContext managed stores.
   *  GetMsgContainerElseNew will attempt to retrieve the Message or Container from the container with supplied key.  Should it
   *  not be present in the kv store, a new and empty version of the fqClassName is instantiated and returned.
   *  
   *  @param xId : transaction id from Pmml Runtime Context
   *  @param gCtx : the engine's EnvContext object that possesses the kv stores.
   *  @param fqClassName : the fully qualified class name of the MessageContainerBase subclass that will be created if the keys will not produce an instance
   *  @param containerId : the name of the kv container
   *  @param key : the key within the container being sought
   *  @return either the MessageContainerBase subclass with the supplied key or a brand new instance of the fqClassName (NO FIELDS FILLED) 
   */
  
  def GetMsgContainerElseNew(xId: Long, gCtx: EnvContext, fqClassName : String, containerId: String, partKey: List[String], primaryKey: List[String]): MessageContainerBase = {
    val mc : MessageContainerBase = gCtx.getObject(xId, containerId, partKey, primaryKey)
    if (mc != null) {
    	mc
    } else {
    	gCtx.NewMessageOrContainer(fqClassName)
    }
  }

  /** 
   *  GetMsgContainerElseNew will attempt to retrieve the Message or Container from the container with supplied key.  Should it
   *  not be present in the kv store, a new and empty version of the fqClassName is instantiated and returned.
   *  
   *  @param ctx : the Pmml Runtime Context instance for the calling model
   *  @param fqClassName : the fully qualified class name of the MessageContainerBase subclass that will be created if the keys will not produce an instance
   *  @param containerId : the name of the kv container
   *  @param key : the key within the container being sought
   *  @return either the MessageContainerBase subclass with the supplied key or a brand new instance of the fqClassName (NO FIELDS FILLED) 
   */
  
  def GetMsgContainerElseNew(ctx: Context, fqClassName : String, containerId: String, partKey: List[String], primaryKey: List[String]): MessageContainerBase = {
    val mc : MessageContainerBase = if (ctx != null && ctx.gCtx != null) ctx.gCtx.getObject(ctx.xId, containerId, partKey, primaryKey) else null
    if (mc != null) {
    	mc
    } else {
    	ctx.gCtx.NewMessageOrContainer(fqClassName)
    }
  }

  /** Given the supplied msg or container, answer its partition key.  If there is none,
      answer an empty List
      @param msg : a Message or Container
      @return the partition key as a List[String]
   */
  def GetPartitionKey(msg : BaseMsg) : scala.collection.immutable.List[String] = {
      val partitionKey : List[String] = if (msg.PartitionKeyData != null) {
          msg.PartitionKeyData.toList
      } else {
          scala.collection.immutable.List[String]()
      }
      partitionKey
  }
  
  /** Given the supplied msg or container, answer its partition key.  If there is none,
      answer an empty List
      @param msg : a Message or Container
      @return the partition key as a List[String]
   */
  def GetPartitionKey(msg : BaseContainer) : scala.collection.immutable.List[String] = {
      val partitionKey : List[String] = if (msg.PartitionKeyData != null) {
          msg.PartitionKeyData.toList
      } else {
          scala.collection.immutable.List[String]()
      }
      partitionKey
  }
  
  /** Given the supplied msg or container, answer its primary key.  If there is none,
      answer an empty List
      @param msg : a Message or Container
      @return the primary key as a List[String]
   */
  def GetPrimaryKey(msg : BaseMsg) : scala.collection.immutable.List[String] = {
      val primaryKey : List[String] = if (msg.PrimaryKeyData != null) {
          msg.PrimaryKeyData.toList
      } else {
          scala.collection.immutable.List[String]()
      }
      primaryKey
  }

  /** Given the supplied msg or container, answer its primary key.  If there is none,
      answer an empty List
      @param msg : a Message or Container
      @return the primary key as a List[String]
   */
  def GetPrimaryKey(msg : BaseContainer) : scala.collection.immutable.List[String] = {
      val primaryKey : List[String] = if (msg.PrimaryKeyData != null) {
          msg.PrimaryKeyData.toList
      } else {
          scala.collection.immutable.List[String]()
      }
      primaryKey
  }

  /**
	  @deprecated("Use GetHistory(ctx: Context, containerId: String, partKey: List[String], appendCurrentChanges: Boolean): Array[MessageContainerBase] ", "2015-Jun-08")
	  Get the MessageContainerBase associated with the supplied containerId and key from the EnvContext managed stores.
   *  Answer the array of messages or container for the supplied partition key found in the specified container.  If append boolean is set,
   *  a message has been requested and the current message in the requesting model is appended to the history and returned.
   *  @param xId : the transaction id associated with this model instance
   *  @param gCtx : the engine's EnvContext portal known to the model that gives it access to the persisted data
   *  @param containerId : The name of the container or message
   *  @param partKey : the partition key that identifies which messages are to be retrieved.
   *  @param appendCurrentChanges : a boolean that is useful for messages only .  When true, it will append the incoming message that caused
   *    the calling model to execute to the returned history of messages
   *  @return Array[MessageContainerBase] that will need to be downcast to their concrete type by the model before use.
   */

  def GetHistory(xId: Long, gCtx: EnvContext, containerId: String, partKey: List[String], appendCurrentChanges: Boolean): Array[MessageContainerBase] = {
    gCtx.getHistoryObjects(xId, containerId, partKey, appendCurrentChanges)
  }

  /**
   *  Answer the array of messages or container for the supplied partition key found in the specified container.  If append boolean is set,
   *  a message has been requested and the current message in the requesting model is appended to the history and returned.
   *  @param ctx : the model instance's context
   *  @param containerId : The name of the container or message
   *  @param partKey : the partition key that identifies which messages are to be retrieved.
   *  @param appendCurrentChanges : a boolean that is useful for messages only .  When true, it will append the incoming message that caused
   *    the calling model to execute to the returned history of messages
   *  @return Array[MessageContainerBase] that will need to be downcast to their concrete type by the model before use.
   */

  def GetHistory(ctx: Context, containerId: String, partKey: List[String], appendCurrentChanges: Boolean): Array[MessageContainerBase] = {
      if (ctx != null && ctx.gCtx != null) ctx.gCtx.getHistoryObjects(ctx.xId, containerId, partKey, appendCurrentChanges) else Array[MessageContainerBase]()
  }

  /** 
	  @deprecated("Use GetArray(ctx: Context, gCtx: EnvContext, containerId: String): Array[MessageContainerBase] ", "2015-Jun-08")
   *  Get an array that contains all of the MessageContainerBase elements for the supplied 'containerId'
   *  
   *  @param xId : transaction id from Pmml Runtime Context
   *  @param gCtx : the engine's EnvContext object that possesses the kv stores.
   *  @param fqClassName : the fully qualified class name of the MessageContainerBase subclass that will be created if the keys will not produce an instance
   *  @param containerId : the name of the kv container
   *  @param key : the key within the container being sought
   *  @return either the Array[MessageContainerBase] subclass with the supplied containerId or an empty array 
   */
  
  def GetArray(xId: Long, gCtx: EnvContext, containerId: String): Array[MessageContainerBase] = {
    gCtx.getAllObjects(xId, containerId)
  }

  /** 
   *  Get an array that contains all of the MessageContainerBase elements for the supplied 'containerId'
   *  
   *  @param ctx : the model instance's context
   *  @param fqClassName : the fully qualified class name of the MessageContainerBase subclass that will be created if the keys will not produce an instance
   *  @param containerId : the name of the kv container
   *  @param key : the key within the container being sought
   *  @return either the Array[MessageContainerBase] subclass with the supplied containerId or an empty array 
   */
  
  def GetArray(ctx: Context, containerId: String): Array[MessageContainerBase] = {
      if (ctx != null && ctx.gCtx != null) ctx.gCtx.getAllObjects(ctx.xId, containerId) else Array[MessageContainerBase]()
  }

  /** 
	  @deprecated("Use Put(ctx: Context, containerId: String, key: List[String], value: BaseMsg): Boolean ", "2015-Jun-08")
   *  Add/update the object with the supplied 'containerId' and 'key' in the EnvContext managed storage with the supplied 'value'
   *  
   *  @param xId : transaction id from Pmml Runtime Context
   *  @param gCtx : the engine's EnvContext object that possesses the kv stores.
   *  @param fqClassName : the fully qualified class name of the MessageContainerBase subclass that will be created if the keys will not produce an instance
   *  @param containerId : the name of the kv container
   *  @param key : the key within the container being sought
   *  @return true if it worked
   */
  def Put(xId: Long, gCtx: EnvContext, containerId: String, key: List[String], value: BaseMsg): Boolean = {
    gCtx.setObject(xId, containerId, key, value)
    true
  }

  /** 
   *  Add/update the object with the supplied 'containerId' and 'key' in the EnvContext managed storage with the supplied 'value'
   *  
   *  @param ctx : the model instance's context
   *  @param containerId : the name of the kv container
   *  @param key : the key within the container being sought
   *  @return true if it worked
   */
  def Put(ctx: Context, containerId: String, key: List[String], value: BaseMsg): Boolean = {
      if (ctx != null && ctx.gCtx != null) {ctx.gCtx.setObject(ctx.xId, containerId, key, value); true} else false
  }

  /** 
	  @deprecated("Use Put(ctx: Context, containerId: String, key: List[String], value: BaseContainer): Boolean ", "2015-Jun-08")
   *  Add/update the object with the supplied 'containerId' and 'key' in the EnvContext managed storage with the supplied 'value'
   *  
   *  @param xId : transaction id from Pmml Runtime Context
   *  @param gCtx : the engine's EnvContext object that possesses the kv stores.
   *  @param fqClassName : the fully qualified class name of the MessageContainerBase subclass that will be created if the keys will not produce an instance
   *  @param containerId : the name of the kv container
   *  @param key : the key within the container being sought
   *  @return true if it worked
   */
  def Put(xId: Long, gCtx: EnvContext, containerId: String, key: List[String], value: BaseContainer): Boolean = {
    gCtx.setObject(xId, containerId, key, value)
    true
  }

  /** 
   *  Add/update the object with the supplied 'containerId' and 'key' in the EnvContext managed storage with the supplied 'value'
   *  
   *  @param ctx : the model instance's context
   *  @param fqClassName : the fully qualified class name of the MessageContainerBase subclass that will be created if the keys will not produce an instance
   *  @param containerId : the name of the kv container
   *  @param key : the key within the container being sought
   *  @return true if it worked
   */
  def Put(ctx: Context, containerId: String, key: List[String], value: BaseContainer): Boolean = {
	  if (ctx != null && ctx.gCtx != null) {ctx.gCtx.setObject(ctx.xId, containerId, key, value); true} else false
  }


  /** 
      @deprecated This function is no longer used by the pmml compiler to implement 'and'.  The Scala generated uses the '&&' that supports 
      short-circuit execution. 

      And the supplied boolean expressions.
      @param boolexpr : one or more Boolean expressions
      @return the logical 'and' of the expressions
   */
  def And(boolexpr : Boolean*): Boolean = {
	boolexpr.reduceLeft(_ && _) 
  }
  
  /** 
      And the supplied Int expressions.  '0' is false; all other values are considered true
      @param boolexpr : one or more Int expressions
      @return the logical 'and' of the expressions
   */
  def IntAnd(boolexpr : Int*): Boolean = {
	if (boolexpr.filter(_ == 0).size == 0) true else false 
  }
  
  /** 
      @deprecated This function is no longer used by the pmml compiler to implement 'or'.  The Scala generated uses the '||' that supports 
      short-circuit execution. 

      Or the supplied boolean expressions.
      @param boolexpr : one or more Boolean expressions
      @return the logical 'and' of the expressions
   */
  def Or(boolexpr : Boolean*): Boolean = {
  	boolexpr.reduceLeft(_ || _) 
  }
  
  /** 
      Or the supplied Int expressions.  '0' is false; all other values are considered true
      @param boolexpr : one or more Int expressions
      @return the logical 'and' of the expressions
   */
  def IntOr(boolexpr : Int*): Boolean = {
	if (boolexpr.filter(_ != 0).size > 0) true else false 
  }
  

  /**
      Answer whether the supplied String is found in the ArrayBuffer of Strings.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: String, setExprs: ArrayBuffer[String]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length > 0
  }

  /**
      Answer whether the supplied Int is found in the ArrayBuffer of Int.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: Int, setExprs: ArrayBuffer[Int]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length > 0
  }

  /**
      Answer whether the supplied Float is found in the ArrayBuffer of Float.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: Float, setExprs: ArrayBuffer[Float]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length > 0
  }

  /**
      Answer whether the supplied Double is found in the ArrayBuffer of Double.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: Double, setExprs: ArrayBuffer[Double]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length > 0
  }

  /**
      Answer whether the supplied String is found in the Array of String.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: String, setExprs: Array[String]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length > 0
  }

  /**
      Answer whether the supplied Int is found in the Array of Int.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: Int, setExprs: Array[Int]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length > 0
  }

  /**
      Answer whether the supplied Float is found in the Array of Float.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: Float, setExprs: Array[Float]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length > 0
  }

  /**
      Answer whether the supplied Double is found in the Array of Double.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: Double, setExprs: Array[Double]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length > 0
  }

  /**
      Answer whether the supplied String is found in the List of String.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: String, setExprs: List[String]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length > 0
  }

  /**
      Answer whether the supplied Int is found in the List of Int.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: Int, setExprs: List[Int]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length > 0
  }

  /**
      Answer whether the supplied Float is found in the List of Float.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: Float, setExprs: List[Float]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length > 0
  }

  /**
      Answer whether the supplied Double is found in the List of Double.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: Double, setExprs: List[Double]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length > 0
  }

  /**
      Answer whether the supplied String is found in the Set of String.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: String, setExprs: Set[String]): Boolean = {
    setExprs.contains(fldRefExpr)
  }

  /**
      Answer whether the supplied Int is found in the Set of Int.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: Int, setExprs: Set[Int]): Boolean = {
    setExprs.contains(fldRefExpr)
  }

  /**
      Answer whether the supplied Float is found in the Set of Float.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: Float, setExprs: Set[Float]): Boolean = {
    setExprs.contains(fldRefExpr)
  }

  /**
      Answer whether the supplied Double is found in the Set of Double.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: Double, setExprs: Set[Double]): Boolean = {
    setExprs.contains(fldRefExpr)
  }

  /**
      Answer whether the supplied String is found in the MutableSet of String.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: String, setExprs: MutableSet[String]): Boolean = {
    setExprs.contains(fldRefExpr)
  }

  /**
      Answer whether the supplied Int is found in the MutableSet of Int.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: Int, setExprs: MutableSet[Int]): Boolean = {
    setExprs.contains(fldRefExpr)
  }

  /**
      Answer whether the supplied Float is found in the MutableSet of Float.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: Float, setExprs: MutableSet[Float]): Boolean = {
    setExprs.contains(fldRefExpr)
  }

  /**
      Answer whether the supplied Double is found in the MutableSet of Double.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def IsIn(fldRefExpr: Double, setExprs: MutableSet[Double]): Boolean = {
    setExprs.contains(fldRefExpr)
  }


  /**
      Answer whether the supplied fieldExpr value lies between any of the collection's value pairs.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @param inclusive : when true, the end values will match; when false, the end values will not match
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def FoundInAnyRange(fldRefExpr: String, tuples: Array[(String,String)], inclusive : Boolean): Boolean = {
    tuples.filter(tup =>  { 
    	if (inclusive) 
    	  (fldRefExpr >= tup._1 && fldRefExpr <= tup._2)
    	else 
    	  (fldRefExpr > tup._1 && fldRefExpr < tup._2)   	  
    }).length > 0
  }

  /**
      Answer whether the supplied fieldExpr value lies between any of the collection's value pairs.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @param inclusive : when true, the end values will match; when false, the end values will not match
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def FoundInAnyRange(fldRefExpr: Int, tuples: Array[(Int,Int)], inclusive : Boolean): Boolean = {
    tuples.filter(tup =>  { 
    	if (inclusive) 
    	  (fldRefExpr >= tup._1 && fldRefExpr <= tup._2)
    	else 
    	  (fldRefExpr > tup._1 && fldRefExpr < tup._2)   	  
    }).length > 0
  }

  /**
      Answer whether the supplied fieldExpr value lies between any of the collection's value pairs.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @param inclusive : when true, the end values will match; when false, the end values will not match
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def FoundInAnyRange(fldRefExpr: Long, tuples: Array[(Long,Long)], inclusive : Boolean): Boolean = {
    tuples.filter(tup =>  { 
    	if (inclusive) 
    	  (fldRefExpr >= tup._1 && fldRefExpr <= tup._2)
    	else 
    	  (fldRefExpr > tup._1 && fldRefExpr < tup._2)   	  
    }).length > 0
  }

  /**
      Answer whether the supplied fieldExpr value lies between any of the collection's value pairs.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @param inclusive : when true, the end values will match; when false, the end values will not match
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def FoundInAnyRange(fldRefExpr: Float, tuples: Array[(Float,Float)], inclusive : Boolean): Boolean = {
     tuples.filter(tup =>  { 
    	if (inclusive) 
    	  (fldRefExpr >= tup._1 && fldRefExpr <= tup._2)
    	else 
    	  (fldRefExpr > tup._1 && fldRefExpr < tup._2)   	  
    }).length > 0
  }

  /**
      Answer whether the supplied fieldExpr value lies between any of the collection's value pairs.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @param inclusive : when true, the end values will match; when false, the end values will not match
      @return true if one of the strings matched the supplied fieldRefExpr
   */
  def FoundInAnyRange(fldRefExpr: Double, tuples: Array[(Double,Double)], inclusive : Boolean): Boolean = {
    tuples.filter(tup =>  { 
    	if (inclusive) 
    	  (fldRefExpr >= tup._1 && fldRefExpr <= tup._2)
    	else 
    	  (fldRefExpr > tup._1 && fldRefExpr < tup._2)   	  
    }).length > 0
  }

  /** 
      Answer if any of the supplied array buffer values fall between the left and right margin.  If inclusive,
      the end values are acceptable.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def AnyBetween(arrayExpr: ArrayBuffer[String], leftMargin: String, rightMargin: String, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length > 0
  }

  /** 
      Answer if any of the supplied array buffer values fall between the left and right margin.  If inclusive,
      the end values are acceptable.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def AnyBetween(arrayExpr: ArrayBuffer[Int], leftMargin: Int, rightMargin: Int, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length > 0
  }

  /** 
      Answer if any of the supplied array buffer values fall between the left and right margin.  If inclusive,
      the end values are acceptable.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def AnyBetween(arrayExpr: ArrayBuffer[Long], leftMargin: Long, rightMargin: Long, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length > 0
  }

  /** 
      Answer if any of the supplied array buffer values fall between the left and right margin.  If inclusive,
      the end values are acceptable.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def AnyBetween(arrayExpr: ArrayBuffer[Float], leftMargin: Float, rightMargin: Float, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length > 0
  }

  /** 
      Answer if any of the supplied array buffer values fall between the left and right margin.  If inclusive,
      the end values are acceptable.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def AnyBetween(arrayExpr: ArrayBuffer[Double], leftMargin: Double, rightMargin: Double, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length > 0
  }

  /** 
      Answer if any of the supplied array buffer values fall between the left and right margin.  If inclusive,
      the end values are acceptable.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def AnyBetween(arrayExpr: Array[String], leftMargin: String, rightMargin: String, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length > 0
  }

  /** 
      Answer if any of the supplied array buffer values fall between the left and right margin.  If inclusive,
      the end values are acceptable.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def AnyBetween(arrayExpr: Array[Int], leftMargin: Int, rightMargin: Int, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length > 0
  }

  /** 
      Answer if any of the supplied array buffer values fall between the left and right margin.  If inclusive,
      the end values are acceptable.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def AnyBetween(arrayExpr: Array[Float], leftMargin: Float, rightMargin: Float, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length > 0
  }

  /** 
      Answer if any of the supplied array buffer values fall between the left and right margin.  If inclusive,
      the end values are acceptable.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def AnyBetween(arrayExpr: Array[Double], leftMargin: Double, rightMargin: Double, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length > 0
  }

  /** 
      Answer if all of the supplied array buffer values fall outside the left and right margin.  If inclusive,
      the end values are considered part of the range.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def NotAnyBetween(arrayExpr: ArrayBuffer[String], leftMargin: String, rightMargin: String, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length == 0
  }

  /** 
      Answer if all of the supplied array buffer values fall outside the left and right margin.  If inclusive,
      the end values are considered part of the range.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def NotAnyBetween(arrayExpr: ArrayBuffer[Int], leftMargin: Int, rightMargin: Int, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length == 0
  }

  /** 
      Answer if all of the supplied array buffer values fall outside the left and right margin.  If inclusive,
      the end values are considered part of the range.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def NotAnyBetween(arrayExpr: ArrayBuffer[Long], leftMargin: Long, rightMargin: Long, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length == 0
  }

  /** 
      Answer if all of the supplied array buffer values fall outside the left and right margin.  If inclusive,
      the end values are considered part of the range.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def NotAnyBetween(arrayExpr: ArrayBuffer[Float], leftMargin: Float, rightMargin: Float, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length == 0
  }

  /** 
      Answer if all of the supplied array buffer values fall outside the left and right margin.  If inclusive,
      the end values are considered part of the range.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def NotAnyBetween(arrayExpr: ArrayBuffer[Double], leftMargin: Double, rightMargin: Double, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length == 0
  }

  /** 
      Answer if all of the supplied array buffer values fall outside the left and right margin.  If inclusive,
      the end values are considered part of the range.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def NotAnyBetween(arrayExpr: Array[String], leftMargin: String, rightMargin: String, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length == 0
  }

  /** 
      Answer if all of the supplied array buffer values fall outside the left and right margin.  If inclusive,
      the end values are considered part of the range.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def NotAnyBetween(arrayExpr: Array[Long], leftMargin: Long, rightMargin: Long, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length == 0
  }

  /** 
      Answer if all of the supplied array buffer values fall outside the left and right margin.  If inclusive,
      the end values are considered part of the range.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def NotAnyBetween(arrayExpr: Array[Int], leftMargin: Int, rightMargin: Int, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length == 0
  }

  /** 
      Answer if all of the supplied array buffer values fall outside the left and right margin.  If inclusive,
      the end values are considered part of the range.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def NotAnyBetween(arrayExpr: Array[Float], leftMargin: Float, rightMargin: Float, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length == 0
  }

  /** 
      Answer if all of the supplied array buffer values fall outside the left and right margin.  If inclusive,
      the end values are considered part of the range.
      @param arrayExpr : a collection of values to test against the left and right margin values
      @param leftMargin : the lower bound
      @param rightMargin : the upper bound
      @param inclusive : when true the boundary values are acceptable
      @return true if at least one of the collection values is found in range
   */
  def NotAnyBetween(arrayExpr: Array[Double], leftMargin: Double, rightMargin: Double, inclusive: Boolean): Boolean = {
    arrayExpr.filter(Between(_, leftMargin, rightMargin, inclusive)).length == 0
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(arrayExpr: ArrayBuffer[String], key: String): Boolean = {
    arrayExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(arrayExpr: ArrayBuffer[Long], key: Long): Boolean = {
    arrayExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(arrayExpr: ArrayBuffer[Int], key: Int): Boolean = {
    arrayExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(arrayExpr: ArrayBuffer[Float], key: Float): Boolean = {
    arrayExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(arrayExpr: ArrayBuffer[Double], key: Double): Boolean = {
    arrayExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(arrayExpr: Array[String], key: String): Boolean = {
    arrayExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(arrayExpr: Array[Long], key: Long): Boolean = {
    arrayExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(arrayExpr: Array[Int], key: Int): Boolean = {
    arrayExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(arrayExpr: Array[Float], key: Float): Boolean = {
    arrayExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(arrayExpr: Array[Double], key: Double): Boolean = {
    arrayExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(setExpr: Set[String], key: String): Boolean = {
    setExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(setExpr: Set[Long], key: Long): Boolean = {
    setExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(setExpr: Set[Int], key: Int): Boolean = {
    setExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(setExpr: Set[Float], key: Float): Boolean = {
    setExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(setExpr: Set[Double], key: Double): Boolean = {
    setExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(setExpr: MutableSet[String], key: String): Boolean = {
    setExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(setExpr: MutableSet[Long], key: Long): Boolean = {
    setExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(setExpr: MutableSet[Int], key: Int): Boolean = {
    setExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(setExpr: MutableSet[Float], key: Float): Boolean = {
    setExpr.contains(key)
  }

  /** 
      Answer if the supplied key is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param key : The key sought in the collection
      @return true if found, else false
   */
  def Contains(setExpr: MutableSet[Double], key: Double): Boolean = {
    setExpr.contains(key)
  }

  /** 
      Answer if ANY of the supplied keys is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param keys : The keys sought in the collection
      @return true if found, else false
   */
  def ContainsAny(setExpr: Set[String], keys: Array[String]): Boolean = {
    (keys.filter(key => setExpr.contains(key)).length > 0)
  }

  /** 
      Answer if ANY of the supplied keys is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param keys : The keys sought in the collection
      @return true if found, else false
   */
  def ContainsAny(setExpr: Set[Long], keys: Array[Long]): Boolean = {
    (keys.filter(key => setExpr.contains(key)).length > 0)
  }

  /** 
      Answer if ANY of the supplied keys is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param keys : The keys sought in the collection
      @return true if found, else false
   */
  def ContainsAny(setExpr: Set[Int], keys: Array[Int]): Boolean = {
    (keys.filter(key => setExpr.contains(key)).length > 0)
  }

  /** 
      Answer if ANY of the supplied keys is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param keys : The keys sought in the collection
      @return true if found, else false
   */
  def ContainsAny(setExpr: Set[Float], keys: Array[Float]): Boolean = {
    (keys.filter(key => setExpr.contains(key)).length > 0)
  }

  /** 
      Answer if ANY of the supplied keys is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param keys : The keys sought in the collection
      @return true if found, else false
   */
  def Contains(setExpr: Set[Double], keys: Array[Double]): Boolean = {
    (keys.filter(key => setExpr.contains(key)).length > 0)
  }

  /** 
      Answer if ANY of the supplied keys is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param keys : The keys sought in the collection
      @return true if found, else false
   */
  def ContainsAny(setExpr: MutableSet[String], keys: Array[String]): Boolean = {
    (keys.filter(key => setExpr.contains(key)).length > 0)
  }

  /** 
      Answer if ANY of the supplied keys is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param keys : The keys sought in the collection
      @return true if found, else false
   */
  def ContainsAny(setExpr: MutableSet[Long], keys: Array[Long]): Boolean = {
    (keys.filter(key => setExpr.contains(key)).length > 0)
  }

  /** 
      Answer if ANY of the supplied keys is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param keys : The keys sought in the collection
      @return true if found, else false
   */
  def ContainsAny(setExpr: MutableSet[Int], keys: Array[Int]): Boolean = {
    (keys.filter(key => setExpr.contains(key)).length > 0)
  }

  /** 
      Answer if ANY of the supplied keys is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param keys : The keys sought in the collection
      @return true if found, else false
   */
  def ContainsAny(setExpr: MutableSet[Float], keys: Array[Float]): Boolean = {
    (keys.filter(key => setExpr.contains(key)).length > 0)
  }

  /** 
      Answer if ANY of the supplied keys is equivalent to one of the collection's values
      @param arrayExpr : The collection to search
      @param keys : The keys sought in the collection
      @return true if found, else false
   */
  def Contains(setExpr: MutableSet[Double], keys: Array[Double]): Boolean = {
    (keys.filter(key => setExpr.contains(key)).length > 0)
  }

  /** 
      Answer the intersection of the supplied collections.
      @param left : a collection
      @param right: a collection
      @return a Set of elements from the supplied collections or empty set if nothing is common
   */
  def Intersect[T: ClassTag](left: Array[T], right: Array[T]): Set[T] = {
    if (left == null || right == null || left.size == 0 || right.size == 0)
      return Array[T]().toSet
    (left.toSet & right.toSet)
  }

  /** 
      Answer the intersection of the supplied collections.
      @param left : a collection
      @param right: a collection
      @return a Set of elements from the supplied collections or empty set if nothing is common
   */
  def Intersect[T: ClassTag](left: Array[T], right: Set[T]): Set[T] = {
    if (left == null || right == null || left.size == 0 || right.size == 0)
      return Array[T]().toSet
    (left.toSet & right)
  }

  /** 
      Answer the intersection of the supplied collections.
      @param left : a collection
      @param right: a collection
      @return a Set of elements from the supplied collections or empty set if nothing is common
   */
  def Intersect[T: ClassTag](left: Set[T], right: Array[T]): Set[T] = {
    if (left == null || right == null || left.size == 0 || right.size == 0)
      return Array[T]().toSet
    (left & right.toSet)
  }

  /** 
      Answer the intersection of the supplied collections.
      @param left : a collection
      @param right: a collection
      @return a Set of elements from the supplied collections or empty set if nothing is common
   */
  def Intersect[T: ClassTag](left: Set[T], right: Set[T]): Set[T] = {
    if (left == null || right == null || left.size == 0 || right.size == 0)
      return Array[T]().toSet
    (left & right)
  }

  /** 
      Answer the intersection of the supplied collections.
      @param left : a collection
      @param right: a collection
      @return a Set of elements from the supplied collections or empty set if nothing is common
   */
  def Intersect[T: ClassTag](left: Array[T], right: TreeSet[T]): Set[T] = {
    if (left == null || right == null || left.size == 0 || right.size == 0)
      return Array[T]().toSet
    (left.toSet & right)
  }

  /** 
      Answer the intersection of the supplied collections.
      @param left : a collection
      @param right: a collection
      @return a Set of elements from the supplied collections or empty set if nothing is common
   */
  def Intersect[T: ClassTag](left: TreeSet[T], right: Array[T]): Set[T] = {
    if (left == null || right == null || left.size == 0 || right.size == 0)
      return Array[T]().toSet
    (left & right.toSet).toSet
  }

  /** 
      Answer the intersection of the supplied collections.
      @param left : a collection
      @param right: a collection
      @return a Set of elements from the supplied collections or empty set if nothing is common
   */
  def Intersect[T: ClassTag](left: TreeSet[T], right: TreeSet[T]): Set[T] = {
    if (left == null || right == null || left.size == 0 || right.size == 0)
      return Array[T]().toSet
    (left & right).toSet
  }

  /** 
      Answer the intersection of the supplied collections.
      @param left : a collection
      @param right: a collection
      @return a Set of elements from the supplied collections or empty set if nothing is common
   */
  def Intersect[T: ClassTag](left: Set[T], right: TreeSet[T]): Set[T] = {
    if (left == null || right == null || left.size == 0 || right.size == 0)
      return Array[T]().toSet
    (left & right).toSet
  }

  /** 
      Answer the intersection of the supplied collections.
      @param left : a collection
      @param right: a collection
      @return a Set of elements from the supplied collections or empty set if nothing is common
   */
  def Intersect[T: ClassTag](left: TreeSet[T], right: Set[T]): Set[T] = {
    if (left == null || right == null || left.size == 0 || right.size == 0)
      return Array[T]().toSet
    (left & right).toSet
  }

  /** 
      Answer the union of the supplied collections.
      @param left : a collection
      @param right: a collection
      @return a Set of elements from the supplied collections
   */
  def Union[T: ClassTag](left: ArrayBuffer[T], right: ArrayBuffer[T]): Set[T] = {
    if (left == null || left.size == 0 && right == null || right.size == 0) {
      Array[T]().toSet
    } else if (left == null || left.size == 0) {
      right.toSet
    } else if (right == null || right.size == 0) {
      left.toSet
    } else {
      (left.union(right)).toSet
    }
  }

  /** 
      Answer the union of the supplied collections.
      @param left : a collection
      @param right: a collection
      @return a Set of elements from the supplied collections
   */
  def Union[T: ClassTag](left: Array[T], right: Array[T]): Set[T] = {
    if (left == null || left.size == 0 && right == null || right.size == 0) {
      Array[T]().toSet
    } else if (left == null || left.size == 0) {
      right.toSet
    } else if (right == null || right.size == 0) {
      left.toSet
    } else {
      (left.union(right)).toSet
    }
  }

  /** 
      Answer the union of the supplied collections.
      @param left : a collection
      @param right: a collection
      @return a Set of elements from the supplied collections
   */
  def Union[T: ClassTag](left: Array[T], right: Set[T]): Set[T] = {
    if (left == null || left.size == 0 && right == null || right.size == 0) {
      Array[T]().toSet
    } else if (left == null || left.size == 0) {
      right.toSet
    } else if (right == null || right.size == 0) {
      left.toSet
    } else {
      (left.toSet.union(right))
    }
  }

  /** 
      Answer the union of the supplied collections.
      @param left : a collection
      @param right: a collection
      @return a Set of elements from the supplied collections
   */
  def Union[T: ClassTag](left: Set[T], right: Array[T]): Set[T] = {
    if (left == null || left.size == 0 && right == null || right.size == 0) {
      Array[T]().toSet
    } else if (left == null || left.size == 0) {
      right.toSet
    } else if (right == null || right.size == 0) {
      left.toSet
    } else {
      (left.union(right.toSet))
    }
  }

  /** 
      Answer the union of the supplied collections.
      @param left : a collection
      @param right: a collection
      @return a Set of elements from the supplied collections
   */
  def Union[T: ClassTag](left: Set[T], right: Set[T]): Set[T] = {
    if (left == null || left.size == 0 && right == null || right.size == 0) {
      Array[T]().toSet
    } else if (left == null || left.size == 0) {
      right.toSet
    } else if (right == null || right.size == 0) {
      left.toSet
    } else {
      left.union(right)
    }
  }

  /** 
      Answer the last element in the supplied collection.
      @param coll : a collection
      @return the last element from the collection
   */
  def Last(coll: Array[Any]): Any = {
    coll.last
  }

  /** 
      Answer the last element in the supplied collection.
      @param coll : a collection
      @return the last element from the collection
   */
  def Last(coll: ArrayBuffer[Any]): Any = {
    coll.last
  }

  /** 
      Answer the last element in the supplied collection.
      @param coll : a collection
      @return the last element from the collection
   */
  def Last[T: ClassTag](coll: ArrayBuffer[T]): T = {
    coll.last
  }

  /** 
      Answer the last element in the supplied collection.
      @param coll : a collection
      @return the last element from the collection
   */
  def Last(coll: Queue[Any]): Any = {
    coll.last
  }

  /** 
      Answer the last element in the supplied collection.
      @param coll : a collection
      @return the last element from the collection
   */
  def Last(coll: SortedSet[Any]): Any = {
    coll.last
  }

  /** 
      Answer the first element in the supplied collection.
      @param coll : a collection
      @return the first element from the collection
   */
  def First(coll: Array[Any]): Any = {
    coll.head
  }

  /** 
      Answer the first element in the supplied collection.
      @param coll : a collection
      @return the first element from the collection
   */
  def First(coll: ArrayBuffer[Any]): Any = {
    coll.head
  }

  /** 
      Answer the first element in the supplied collection.
      @param coll : a collection
      @return the first element from the collection
   */
  def First(coll: Queue[Any]): Any = {
    coll.head
  }

  /** 
      Answer the first element in the supplied collection.
      @param coll : a collection
      @return the first element from the collection
   */
  def First(coll: SortedSet[Any]): Any = {
    coll.head
  }

  /** 
      Answer the compliment of the supplied boolean expression
      @param boolexpr : a boolean
      @return if true then false ; if false then true 
   */
  def Not(boolexpr: Boolean): Boolean = {
    !boolexpr
  }

  /**
      Answer whether the supplied value is NOT found in supplied collection.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the values matched the supplied fieldRefExpr
   */
  def IsNotIn(fldRefExpr: String, setExprs: ArrayBuffer[String]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length == 0
  }

  /**
      Answer whether the supplied value is NOT found in supplied collection.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the values matched the supplied fieldRefExpr
   */
  def IsNotIn(fldRefExpr: Int, setExprs: ArrayBuffer[Int]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length == 0
  }

  /**
      Answer whether the supplied value is NOT found in supplied collection.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the values matched the supplied fieldRefExpr
   */
  def IsNotIn(fldRefExpr: Float, setExprs: ArrayBuffer[Float]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length == 0
  }

  /**
      Answer whether the supplied value is NOT found in supplied collection.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the values matched the supplied fieldRefExpr
   */
  def IsNotIn(fldRefExpr: Double, setExprs: ArrayBuffer[Double]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length == 0
  }

  /**
      Answer whether the supplied value is NOT found in supplied collection.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the values matched the supplied fieldRefExpr
   */
  def IsNotIn(fldRefExpr: String, setExprs: List[String]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length == 0
  }

  /**
      Answer whether the supplied value is NOT found in supplied collection.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the values matched the supplied fieldRefExpr
   */
  def IsNotIn(fldRefExpr: Int, setExprs: List[Int]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length == 0
  }

  /**
      Answer whether the supplied value is NOT found in supplied collection.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the values matched the supplied fieldRefExpr
   */
  def IsNotIn(fldRefExpr: Float, setExprs: List[Float]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length == 0
  }

  /**
      Answer whether the supplied value is NOT found in supplied collection.
      @param fldRefExpr : the key sought in the collection 
      @param setExprs : a collection of values to be searched
      @return true if one of the values matched the supplied fieldRefExpr
   */
  def IsNotIn(fldRefExpr: Double, setExprs: List[Double]): Boolean = {
    setExprs.filter(_ == fldRefExpr).length == 0
  }

  /** Between */

  /**
      Answer whether the supplied value is between the left and right margin.  When inclusive is true, margin values
      are deemed acceptible
      @param thisOne : the key sought in the collection 
      @param leftMargin : lower limit value to be considered
      @param rightMargin : upper limit value to be considered
      @param inclusive : when true either limit deemed acceptable
      @return true if value is in range
   */
  def Between(thisOne: String, leftMargin: String, rightMargin: String, inclusive: Boolean): Boolean = {
    if (inclusive) (thisOne >= leftMargin && thisOne <= rightMargin) else (thisOne > leftMargin && thisOne < rightMargin)
  }

  /**
      Answer whether the supplied value is between the left and right margin.  When inclusive is true, margin values
      are deemed acceptible
      @param thisOne : the key sought in the collection 
      @param leftMargin : lower limit value to be considered
      @param rightMargin : upper limit value to be considered
      @param inclusive : when true either limit deemed acceptable
      @return true if value is in range
   */
  def Between(thisOne: Int, leftMargin: Int, rightMargin: Int, inclusive: Boolean): Boolean = {
    if (inclusive) (thisOne >= leftMargin && thisOne <= rightMargin) else (thisOne > leftMargin && thisOne < rightMargin)
  }

  /**
      Answer whether the supplied value is between the left and right margin.  When inclusive is true, margin values
      are deemed acceptible
      @param thisOne : the key sought in the collection 
      @param leftMargin : lower limit value to be considered
      @param rightMargin : upper limit value to be considered
      @param inclusive : when true either limit deemed acceptable
      @return true if value is in range
   */
  def Between(thisOne: Long, leftMargin: Long, rightMargin: Long, inclusive: Boolean): Boolean = {
    if (inclusive) (thisOne >= leftMargin && thisOne <= rightMargin) else (thisOne > leftMargin && thisOne < rightMargin)
  }

  /**
      Answer whether the supplied value is between the left and right margin.  When inclusive is true, margin values
      are deemed acceptible
      @param thisOne : the key sought in the collection 
      @param leftMargin : lower limit value to be considered
      @param rightMargin : upper limit value to be considered
      @param inclusive : when true either limit deemed acceptable
      @return true if value is in range
   */
  def Between(thisOne: Int, leftMargin: Long, rightMargin: Long, inclusive: Boolean): Boolean = {
    if (inclusive) (thisOne >= leftMargin && thisOne <= rightMargin) else (thisOne > leftMargin && thisOne < rightMargin)
  }

  /**
      Answer whether the supplied value is between the left and right margin.  When inclusive is true, margin values
      are deemed acceptible
      @param thisOne : the key sought in the collection 
      @param leftMargin : lower limit value to be considered
      @param rightMargin : upper limit value to be considered
      @param inclusive : when true either limit deemed acceptable
      @return true if value is in range
   */
  def Between(thisOne: Double, leftMargin: Double, rightMargin: Int, inclusive: Boolean): Boolean = {
    if (inclusive) (thisOne >= leftMargin && thisOne <= rightMargin) else (thisOne > leftMargin && thisOne < rightMargin)
  }

  /**
      Answer whether the supplied value is between the left and right margin.  When inclusive is true, margin values
      are deemed acceptible
      @param thisOne : the key sought in the collection 
      @param leftMargin : lower limit value to be considered
      @param rightMargin : upper limit value to be considered
      @param inclusive : when true either limit deemed acceptable
      @return true if value is in range
   */
  def Between(thisOne: Int, leftMargin: Int, rightMargin: Double, inclusive: Boolean): Boolean = {
    if (inclusive) (thisOne >= leftMargin && thisOne <= rightMargin) else (thisOne > leftMargin && thisOne < rightMargin)
  }

  /**
      Answer whether the supplied value is between the left and right margin.  When inclusive is true, margin values
      are deemed acceptible
      @param thisOne : the key sought in the collection 
      @param leftMargin : lower limit value to be considered
      @param rightMargin : upper limit value to be considered
      @param inclusive : when true either limit deemed acceptable
      @return true if value is in range
   */
  def Between(thisOne: Double, leftMargin: Double, rightMargin: Double, inclusive: Boolean): Boolean = {
    if (inclusive) (thisOne >= leftMargin && thisOne <= rightMargin) else (thisOne > leftMargin && thisOne < rightMargin)
  }

  /**
      Answer whether the supplied value is between the left and right margin.  When inclusive is true, margin values
      are deemed acceptible
      @param thisOne : the key sought in the collection 
      @param leftMargin : lower limit value to be considered
      @param rightMargin : upper limit value to be considered
      @param inclusive : when true either limit deemed acceptable
      @return true if value is in range
   */
  def Between(thisOne: Double, leftMargin: Double, rightMargin: Float, inclusive: Boolean): Boolean = {
    if (inclusive) (thisOne >= leftMargin && thisOne <= rightMargin) else (thisOne > leftMargin && thisOne < rightMargin)
  }

  /**
      Answer whether the supplied value is between the left and right margin.  When inclusive is true, margin values
      are deemed acceptible
      @param thisOne : the key sought in the collection 
      @param leftMargin : lower limit value to be considered
      @param rightMargin : upper limit value to be considered
      @param inclusive : when true either limit deemed acceptable
      @return true if value is in range
   */
  def Between(thisOne: Float, leftMargin: Float, rightMargin: Double, inclusive: Boolean): Boolean = {
    if (inclusive) (thisOne >= leftMargin && thisOne <= rightMargin) else (thisOne > leftMargin && thisOne < rightMargin)
  }

  /**
      Answer whether the supplied value is between the left and right margin.  When inclusive is true, margin values
      are deemed acceptible
      @param thisOne : the key sought in the collection 
      @param leftMargin : lower limit value to be considered
      @param rightMargin : upper limit value to be considered
      @param inclusive : when true either limit deemed acceptable
      @return true if value is in range
   */
  def Between(thisOne: Float, leftMargin: Float, rightMargin: Int, inclusive: Boolean): Boolean = {
    if (inclusive) (thisOne >= leftMargin && thisOne <= rightMargin) else (thisOne > leftMargin && thisOne < rightMargin)
  }

  /**
      Answer whether the supplied value is between the left and right margin.  When inclusive is true, margin values
      are deemed acceptible
      @param thisOne : the key sought in the collection 
      @param leftMargin : lower limit value to be considered
      @param rightMargin : upper limit value to be considered
      @param inclusive : when true either limit deemed acceptable
      @return true if value is in range
   */
  def Between(thisOne: Int, leftMargin: Int, rightMargin: Float, inclusive: Boolean): Boolean = {
    if (inclusive) (thisOne >= leftMargin && thisOne <= rightMargin) else (thisOne > leftMargin && thisOne < rightMargin)
  }

  /**
      Answer whether the supplied value is between the left and right margin.  When inclusive is true, margin values
      are deemed acceptible
      @param thisOne : the key sought in the collection 
      @param leftMargin : lower limit value to be considered
      @param rightMargin : upper limit value to be considered
      @param inclusive : when true either limit deemed acceptable
      @return true if value is in range
   */
  def Between(thisOne: Float, leftMargin: Float, rightMargin: Float, inclusive: Boolean): Boolean = {
    if (inclusive) (thisOne >= leftMargin && thisOne <= rightMargin) else (thisOne > leftMargin && thisOne < rightMargin)
  }

  /** 
      Answer true if expr1 is greather than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterThan(expr1: String, expr2: String): Boolean = {
    (expr1 > expr2)
  }

  /** 
      Answer true if expr1 is greather than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterThan(expr1: Int, expr2: Int): Boolean = {
    (expr1 > expr2)
  }

  /** 
      Answer true if expr1 is greather than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterThan(expr1: Double, expr2: Int): Boolean = {
    (expr1 > expr2)
  }

  /** 
      Answer true if expr1 is greather than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterThan(expr1: Int, expr2: Double): Boolean = {
    (expr1 > expr2)
  }

  /** 
      Answer true if expr1 is greather than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterThan(expr1: Double, expr2: Double): Boolean = {
    (expr1 > expr2)
  }

  /** 
      Answer true if expr1 is greather than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterThan(expr1: Double, expr2: Float): Boolean = {
    (expr1 > expr2)
  }

  /** 
      Answer true if expr1 is greather than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterThan(expr1: Float, expr2: Double): Boolean = {
    (expr1 > expr2)
  }

  /** 
      Answer true if expr1 is greather than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterThan(expr1: Float, expr2: Int): Boolean = {
    (expr1 > expr2)
  }

  /** 
      Answer true if expr1 is greather than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterThan(expr1: Int, expr2: Float): Boolean = {
    (expr1 > expr2)
  }

  /** 
      Answer true if expr1 is greather than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterThan(expr1: Float, expr2: Float): Boolean = {
    (expr1 > expr2)
  }

  /** 
      Answer true if expr1 is greather than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterThan(expr1: Int, expr2: Long): Boolean = {
    (expr1 > expr2)
  }

  /** 
      Answer true if expr1 is greather than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterThan(expr1: Long, expr2: Long): Boolean = {
    (expr1 > expr2)
  }

  /** 
      Answer true if expr1 is greather than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterThan(expr1: Long, expr2: Int): Boolean = {
    (expr1 > expr2)
  }

  /** 
      Answer true if expr1 is greather than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterOrEqual(expr1: String, expr2: String): Boolean = {
    (expr1 >= expr2)
  }

  /** 
      Answer true if expr1 is greather than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterOrEqual(expr1: Int, expr2: Int): Boolean = {
    (expr1 >= expr2)
  }

  /** 
      Answer true if expr1 is greather than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterOrEqual(expr1: Double, expr2: Int): Boolean = {
    (expr1 >= expr2)
  }

  /** 
      Answer true if expr1 is greather than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterOrEqual(expr1: Int, expr2: Double): Boolean = {
    (expr1 >= expr2)
  }

  /** 
      Answer true if expr1 is greather than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterOrEqual(expr1: Double, expr2: Double): Boolean = {
    (expr1 >= expr2)
  }

  /** 
      Answer true if expr1 is greather than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterOrEqual(expr1: Double, expr2: Float): Boolean = {
    (expr1 >= expr2)
  }

  /** 
      Answer true if expr1 is greather than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterOrEqual(expr1: Float, expr2: Double): Boolean = {
    (expr1 >= expr2)
  }

  /** 
      Answer true if expr1 is greather than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterOrEqual(expr1: Float, expr2: Int): Boolean = {
    (expr1 >= expr2)
  }

  /** 
      Answer true if expr1 is greather than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterOrEqual(expr1: Int, expr2: Float): Boolean = {
    (expr1 >= expr2)
  }

  /** 
      Answer true if expr1 is greather than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterOrEqual(expr1: Float, expr2: Float): Boolean = {
    (expr1 >= expr2)
  }

  /** 
      Answer true if expr1 is greather than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterOrEqual(expr1: Int, expr2: Long): Boolean = {
    (expr1 >= expr2)
  }

  /** 
      Answer true if expr1 is greather than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterOrEqual(expr1: Long, expr2: Long): Boolean = {
    (expr1 >= expr2)
  }

  /** 
      Answer true if expr1 is greather than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def GreaterOrEqual(expr1: Long, expr2: Int): Boolean = {
    (expr1 >= expr2)
  }

  /** 
      Answer true if expr1 is less than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessOrEqual(expr1: String, expr2: String): Boolean = {
    (expr1 <= expr2)
  }

  /** 
      Answer true if expr1 is less than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessOrEqual(expr1: Int, expr2: Int): Boolean = {
    (expr1 <= expr2)
  }

  /** 
      Answer true if expr1 is less than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessOrEqual(expr1: Double, expr2: Int): Boolean = {
    (expr1 <= expr2)
  }

  /** 
      Answer true if expr1 is less than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessOrEqual(expr1: Int, expr2: Double): Boolean = {
    (expr1 <= expr2)
  }

  /** 
      Answer true if expr1 is less than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessOrEqual(expr1: Double, expr2: Double): Boolean = {
    (expr1 <= expr2)
  }

  /** 
      Answer true if expr1 is less than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessOrEqual(expr1: Double, expr2: Float): Boolean = {
    (expr1 <= expr2)
  }

  /** 
      Answer true if expr1 is less than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessOrEqual(expr1: Float, expr2: Double): Boolean = {
    (expr1 <= expr2)
  }

  /** 
      Answer true if expr1 is less than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessOrEqual(expr1: Float, expr2: Int): Boolean = {
    (expr1 <= expr2)
  }

  /** 
      Answer true if expr1 is less than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessOrEqual(expr1: Int, expr2: Float): Boolean = {
    (expr1 <= expr2)
  }

  /** 
      Answer true if expr1 is less than or equal expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessOrEqual(expr1: Float, expr2: Float): Boolean = {
    (expr1 <= expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessOrEqual(expr1: Int, expr2: Long): Boolean = {
    (expr1 <= expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessOrEqual(expr1: Long, expr2: Long): Boolean = {
    (expr1 <= expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessOrEqual(expr1: Long, expr2: Int): Boolean = {
    (expr1 <= expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessThan(expr1: String, expr2: String): Boolean = {
    (expr1 < expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessThan(expr1: Int, expr2: Int): Boolean = {
    (expr1 < expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessThan(expr1: Double, expr2: Int): Boolean = {
    (expr1 < expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessThan(expr1: Int, expr2: Double): Boolean = {
    (expr1 < expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessThan(expr1: Double, expr2: Double): Boolean = {
    (expr1 < expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessThan(expr1: Double, expr2: Float): Boolean = {
    (expr1 < expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessThan(expr1: Float, expr2: Double): Boolean = {
    (expr1 < expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessThan(expr1: Float, expr2: Int): Boolean = {
    (expr1 < expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessThan(expr1: Int, expr2: Float): Boolean = {
    (expr1 < expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessThan(expr1: Float, expr2: Float): Boolean = {
    (expr1 < expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessThan(expr1: Int, expr2: Long): Boolean = {
    (expr1 < expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessThan(expr1: Long, expr2: Long): Boolean = {
    (expr1 < expr2)
  }

  /** 
      Answer true if expr1 is less than expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def LessThan(expr1: Long, expr2: Int): Boolean = {
    (expr1 < expr2)
  }

  /** 
      Answer true if expr1 is equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def Equal(expr1: String, expr2: String): Boolean = {
    (expr1 == expr2)
  }

  /** 
      Answer true if expr1 is equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def Equal(expr1: Int, expr2: Int): Boolean = {
    (expr1 == expr2)
  }

  /** 
      Answer true if expr1 is equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def Equal(expr1: Double, expr2: Int): Boolean = {
    (expr1 == expr2)
  }

  /** 
      Answer true if expr1 is equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def Equal(expr1: Int, expr2: Double): Boolean = {
    (expr1 == expr2)
  }

  /** 
      Answer true if expr1 is equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def Equal(expr1: Double, expr2: Double): Boolean = {
    (expr1 == expr2)
  }

  /** 
      Answer true if expr1 is equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def Equal(expr1: Double, expr2: Float): Boolean = {
    (expr1 == expr2)
  }

  /** 
      Answer true if expr1 is equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def Equal(expr1: Float, expr2: Double): Boolean = {
    (expr1 == expr2)
  }

  /** 
      Answer true if expr1 is equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def Equal(expr1: Float, expr2: Int): Boolean = {
    (expr1 == expr2)
  }

  /** 
      Answer true if expr1 is equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def Equal(expr1: Int, expr2: Float): Boolean = {
    (expr1 == expr2)
  }

  /** 
      Answer true if expr1 is equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def Equal(expr1: Float, expr2: Float): Boolean = {
    (expr1 == expr2)
  }

  /** 
      Answer true if expr1 is equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def Equal(expr1: Boolean, expr2: Boolean): Boolean = {
    (expr1 == expr2)
  }

  /** 
      Answer true if expr1 is equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def Equal(expr1: Int, expr2: Long): Boolean = {
    (expr1 == expr2)
  }

  /** 
      Answer true if expr1 is equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def Equal(expr1: Long, expr2: Long): Boolean = {
    (expr1 == expr2)
  }

  /** 
      Answer true if expr1 is equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def Equal(expr1: Long, expr2: Int): Boolean = {
    (expr1 == expr2)
  }

  /** 
      Answer true if expr1 is not equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def NotEqual(expr1: String, expr2: String): Boolean = {
    !(expr1 == expr2)
  }

  /** 
      Answer true if expr1 is not equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def NotEqual(expr1: Int, expr2: Int): Boolean = {
    !(expr1 == expr2)
  }

  /** 
      Answer true if expr1 is not equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def NotEqual(expr1: Double, expr2: Int): Boolean = {
    !(expr1 == expr2)
  }

  /** 
      Answer true if expr1 is not equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def NotEqual(expr1: Int, expr2: Double): Boolean = {
    !(expr1 == expr2)
  }

  /** 
      Answer true if expr1 is not equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def NotEqual(expr1: Double, expr2: Double): Boolean = {
    !(expr1 == expr2)
  }

  /** 
      Answer true if expr1 is not equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def NotEqual(expr1: Double, expr2: Float): Boolean = {
    !(expr1 == expr2)
  }

  /** 
      Answer true if expr1 is not equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def NotEqual(expr1: Float, expr2: Double): Boolean = {
    !(expr1 == expr2)
  }

  /** 
      Answer true if expr1 is not equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def NotEqual(expr1: Float, expr2: Int): Boolean = {
    !(expr1 == expr2)
  }

  /** 
      Answer true if expr1 is not equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def NotEqual(expr1: Int, expr2: Float): Boolean = {
    !(expr1 == expr2)
  }

  /** 
      Answer true if expr1 is not equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def NotEqual(expr1: Float, expr2: Float): Boolean = {
    !(expr1 == expr2)
  }

  /** 
      Answer true if expr1 is not equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def NotEqual(expr1: Boolean, expr2: Boolean): Boolean = {
    !(expr1 == expr2)
  }

  /** 
      Answer true if expr1 is not equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def NotEqual(expr1: Int, expr2: Long): Boolean = {
    !(expr1 == expr2)
  }

  /** 
      Answer true if expr1 is not equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def NotEqual(expr1: Long, expr2: Long): Boolean = {
    !(expr1 == expr2)
  }

  /** 
      Answer true if expr1 is not equivalent to expr2
      @param expr1 : a value
      @param expr2 : a value
      @return true if this is so, else false
   */
  def NotEqual(expr1: Long, expr2: Int): Boolean = {
    !(expr1 == expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: String, expr2: String): String = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Int, expr2: Int): Int = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Int, expr2: Int, expr3: Int): Int = {
    (expr1 + expr2 + expr3)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Long, expr2: Long, expr3: Long): Long = {
    (expr1 + expr2 + expr3)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Int, expr2: Int, expr3: Int, expr4: Int): Int = {
    (expr1 + expr2 + expr3 + expr4)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Long, expr2: Long, expr3: Long, expr4: Long): Long = {
    (expr1 + expr2 + expr3 + expr4)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Int, expr2: Int, expr3: Int, expr4: Int, expr5: Int): Int = {
    (expr1 + expr2 + expr3 + expr4 + expr5)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Long, expr2: Long, expr3: Long, expr4: Long, expr5: Long): Long = {
    (expr1 + expr2 + expr3 + expr4 + expr5)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Int, expr2: Int, expr3: Int, expr4: Int, expr5: Int, expr6: Int): Int = {
    (expr1 + expr2 + expr3 + expr4 + expr5 + expr6)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Long, expr2: Long, expr3: Long, expr4: Long, expr5: Long, expr6: Long): Long = {
    (expr1 + expr2 + expr3 + expr4 + expr5 + expr6)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Int, expr2: Int, expr3: Int, expr4: Int, expr5: Int, expr6: Int, expr7: Int): Int = {
    (expr1 + expr2 + expr3 + expr4 + expr5 + expr6 + expr7)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Long, expr2: Long, expr3: Long, expr4: Long, expr5: Long, expr6: Long, expr7: Long): Long = {
    (expr1 + expr2 + expr3 + expr4 + expr5 + expr6 + expr7)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Int, expr2: Int, expr3: Int, expr4: Int, expr5: Int, expr6: Int, expr7: Int, expr8: Int): Int = {
    (expr1 + expr2 + expr3 + expr4 + expr5 + expr6 + expr7 + expr8)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Int, expr2: Int, expr3: Int, expr4: Int, expr5: Int, expr6: Int, expr7: Int, expr8: Long): Long = {
    (expr1 + expr2 + expr3 + expr4 + expr5 + expr6 + expr7 + expr8)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Double, expr2: Double): Double = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Double, expr2: Double, expr3: Double): Double = {
    (expr1 + expr2 + expr3)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Double, expr2: Double, expr3: Double, expr4: Double): Double = {
    (expr1 + expr2 + expr3 + expr4)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Double, expr2: Double, expr3: Double, expr4: Double, expr5: Double): Double = {
    (expr1 + expr2 + expr3 + expr4 + expr5)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Double, expr2: Double, expr3: Double, expr4: Double, expr5: Double, expr6: Double): Double = {
    (expr1 + expr2 + expr3 + expr4 + expr5 + expr6)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Double, expr2: Int): Double = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Int, expr2: Double): Double = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Int, expr2: Long): Double = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Long, expr2: Int): Long = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Int, expr2: Float): Float = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Float, expr2: Int): Float = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Long, expr2: Long): Long = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Double, expr2: Long): Double = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Long, expr2: Double): Double = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Long, expr2: Float): Float = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Float, expr2: Long): Float = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Double, expr2: Float): Double = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Float, expr2: Double): Double = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of expr1 and expr2
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the two values
   */
  def Plus(expr1: Float, expr2: Float): Float = {
    (expr1 + expr2)
  }

  /** 
      Answer the sum of exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the exprs
   */
  def Plus(exprs: ArrayBuffer[String]): String = {
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the exprs
   */
  def Plus(exprs: ArrayBuffer[Int]): Int = {
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the exprs
   */
  def Plus(exprs: ArrayBuffer[Long]): Long = {
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the exprs
   */
  def Plus(exprs: ArrayBuffer[Double]): Double = {
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the exprs
   */
  def Plus(exprs: ArrayBuffer[Float]): Float = {
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the exprs
   */
  def Plus(exprs: Array[String]): String = {
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the exprs
   */
  def Plus(exprs: Array[Int]): Int = {
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the exprs
   */
  def Plus(exprs: Array[Long]): Long = {
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the exprs
   */
  def Plus(exprs: Array[Double]): Double = {
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the sum of the exprs
   */
  def Plus(exprs: Array[Float]): Float = {
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Int, expr2: Int): Int = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Double, expr2: Int): Double = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Int, expr2: Double): Double = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Int, expr2: Long): Double = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Long, expr2: Int): Long = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Int, expr2: Float): Float = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Float, expr2: Int): Float = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Long, expr2: Long): Long = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Double, expr2: Long): Double = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Long, expr2: Double): Double = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Long, expr2: Float): Float = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Float, expr2: Long): Float = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Double, expr2: Double): Double = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Double, expr2: Float): Double = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Float, expr2: Double): Double = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of two exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(expr1: Float, expr2: Float): Float = {
    (expr1 - expr2)
  }

  /** 
      Answer the difference of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(exprs: ArrayBuffer[Int]): Int = {
    exprs.reduceLeft(_ - _)
  }

  /** 
      Answer the difference of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(exprs: ArrayBuffer[Long]): Long = {
    exprs.reduceLeft(_ - _)
  }

  /** 
      Answer the difference of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(exprs: ArrayBuffer[Double]): Double = {
    exprs.reduceLeft(_ - _)
  }

  /** 
      Answer the difference of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(exprs: ArrayBuffer[Float]): Float = {
    exprs.reduceLeft(_ - _)
  }

  /** 
      Answer the difference of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(exprs: Array[Int]): Int = {
    exprs.reduceLeft(_ - _)
  }

  /** 
      Answer the difference of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(exprs: Array[Long]): Long = {
    exprs.reduceLeft(_ - _)
  }

  /** 
      Answer the difference of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(exprs: Array[Double]): Double = {
    exprs.reduceLeft(_ - _)
  }

  /** 
      Answer the difference of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the difference of the exprs
   */
  def Minus(exprs: Array[Float]): Float = {
    exprs.reduceLeft(_ - _)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Int, expr2: Int): Int = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Double, expr2: Int): Double = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Int, expr2: Double): Double = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Int, expr2: Long): Double = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Long, expr2: Int): Long = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Int, expr2: Float): Float = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Float, expr2: Int): Float = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Long, expr2: Long): Long = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Double, expr2: Long): Double = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Long, expr2: Double): Double = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Long, expr2: Float): Float = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Float, expr2: Long): Float = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Double, expr2: Double): Double = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Double, expr2: Float): Double = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Float, expr2: Double): Double = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(expr1: Float, expr2: Float): Float = {
    (expr1 * expr2)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(exprs: ArrayBuffer[Int]): Int = {
    exprs.reduceLeft(_ * _)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(exprs: ArrayBuffer[Long]): Long = {
    exprs.reduceLeft(_ * _)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(exprs: ArrayBuffer[Double]): Double = {
    exprs.reduceLeft(_ * _)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(exprs: ArrayBuffer[Float]): Float = {
    exprs.reduceLeft(_ * _)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(exprs: Array[Int]): Int = {
    exprs.reduceLeft(_ * _)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(exprs: Array[Long]): Long = {
    exprs.reduceLeft(_ * _)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(exprs: Array[Double]): Double = {
    exprs.reduceLeft(_ * _)
  }

  /** 
      Answer the product of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the product of the exprs
   */
  def Multiply(exprs: Array[Float]): Float = {
    exprs.reduceLeft(_ * _)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Int, expr2: Int): Int = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Double, expr2: Int): Double = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Int, expr2: Double): Double = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Int, expr2: Long): Double = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Long, expr2: Int): Long = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Int, expr2: Float): Float = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Float, expr2: Int): Float = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Long, expr2: Long): Long = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Double, expr2: Long): Double = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Long, expr2: Double): Double = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Long, expr2: Float): Float = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Float, expr2: Long): Float = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Double, expr2: Double): Double = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Double, expr2: Float): Double = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Float, expr2: Double): Double = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(expr1: Float, expr2: Float): Float = {
    (expr1 / expr2)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(exprs: ArrayBuffer[Int]): Int = {
    exprs.reduceLeft(_ / _)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(exprs: ArrayBuffer[Long]): Long = {
    exprs.reduceLeft(_ / _)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(exprs: ArrayBuffer[Double]): Double = {
    exprs.reduceLeft(_ / _)
  }

  /** 
      Answer the quotient of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the quotient of the exprs
   */
  def Divide(exprs: ArrayBuffer[Float]): Float = {
    exprs.reduceLeft(_ / _)
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Int, expr2: Int): Int = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Double, expr2: Int): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Int, expr2: Double): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Int, expr2: Long): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Long, expr2: Int): Long = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Int, expr2: Float): Float = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Float, expr2: Int): Float = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Long, expr2: Long): Long = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Double, expr2: Long): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Long, expr2: Double): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Long, expr2: Float): Float = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Float, expr2: Long): Float = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Double, expr2: Double): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Double, expr2: Float): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Float, expr2: Double): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(expr1: Float, expr2: Float): Float = {
    (min(expr1, expr2))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(exprs: ArrayBuffer[Int]): Int = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(exprs: ArrayBuffer[Long]): Long = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(exprs: ArrayBuffer[Double]): Double = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(exprs: ArrayBuffer[Float]): Float = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(exprs: Array[Int]): Int = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(exprs: Array[Long]): Long = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(exprs: Array[Double]): Double = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(exprs: Array[Float]): Float = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(exprs: List[Int]): Int = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(exprs: List[Long]): Long = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(exprs: List[Double]): Double = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the minimum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the minimum of the exprs
   */
  def Min(exprs: List[Float]): Float = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Int, expr2: Int): Int = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Double, expr2: Int): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Int, expr2: Double): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Int, expr2: Long): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Long, expr2: Int): Long = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Int, expr2: Float): Float = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Float, expr2: Int): Float = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Long, expr2: Long): Long = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Double, expr2: Long): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Long, expr2: Double): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Long, expr2: Float): Float = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Float, expr2: Long): Float = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Double, expr2: Double): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Double, expr2: Float): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Float, expr2: Double): Double = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(expr1: Float, expr2: Float): Float = {
    (min(expr1, expr2))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(exprs: ArrayBuffer[Int]): Int = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(exprs: ArrayBuffer[Long]): Long = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(exprs: ArrayBuffer[Double]): Double = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(exprs: ArrayBuffer[Float]): Float = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the maximum of the exprs
      @param expr1 : a value
      @param expr2 : a value
      @return the maximum of the exprs
   */
  def Max(exprs: Array[Int]): Int = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the maximum of the exprs
      @param exprs : a collection of values
      @return the maximum of the exprs
   */
  def Max(exprs: Array[Long]): Long = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the maximum of the exprs
      @param exprs : a collection of values
      @return the maximum of the exprs
   */
  def Max(exprs: Array[Double]): Double = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the maximum of the exprs
      @param exprs : a collection of values
      @return the maximum of the exprs
   */
  def Max(exprs: Array[Float]): Float = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the maximum of the exprs
      @param exprs : a collection of values
      @return the maximum of the exprs
   */
  def Max(exprs: List[Int]): Int = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the maximum of the exprs
      @param exprs : a collection of values
      @return the maximum of the exprs
   */
  def Max(exprs: List[Long]): Long = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the maximum of the exprs
      @param exprs : a collection of values
      @return the maximum of the exprs
   */
  def Max(exprs: List[Double]): Double = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the maximum of the exprs
      @param exprs : a collection of values
      @return the maximum of the exprs
   */
  def Max(exprs: List[Float]): Float = {
    exprs.reduceLeft(min(_, _))
  }

  /** 
      Answer the sum of the exprs
      @param exprs : a collection of values
      @return the sum of the exprs
   */
  def Sum(exprs: ArrayBuffer[Int]): Int = {
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the exprs
      @param exprs : a collection of values
      @return the sum of the exprs
   */
  def Sum(exprs: ArrayBuffer[Long]): Long = {
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the exprs
      @param exprs : a collection of values
      @return the sum of the exprs
   */
  def Sum(exprs: ArrayBuffer[Double]): Double = {
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the exprs
      @param exprs : a collection of values
      @return the sum of the exprs
   */
  def Sum(exprs: ArrayBuffer[Float]): Float = {
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the exprs
      @param exprs : a collection of values
      @return the sum of the exprs
   */
  def Sum(exprs: Array[Int]): Int = {
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the exprs
      @param exprs : a collection of values
      @return the sum of the exprs
   */
  def Sum(exprs: Array[Long]): Long = {
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the exprs
      @param tuple : tuple of values
      @return the sum of the exprs
   */
  def Sum(exprs: Array[Double]): Double = {
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the exprs
      @param tuple : tuple of values
      @return the sum of the exprs
   */
  def Sum(exprs: Array[Float]): Float = {
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the tuple values
      @param tuple : tuple of values
     @return the sum of the tuples
   */
  def Sum(tuples: Tuple2[Int, Int]): Int = {
    val exprs: Array[Int] = ToArray(tuples)
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the tuple values
      @param tuple : tuple of values
      @return the sum of the tuples
   */
  def Sum(tuples: Tuple3[Int, Int, Int]): Int = {
    val exprs: Array[Int] = ToArray(tuples)
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the tuple values
      @param tuple : tuple of values
      @return the sum of the tuples
   */
  def Sum(tuples: Tuple4[Int, Int, Int, Int]): Int = {
    val exprs: Array[Int] = ToArray(tuples)
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the tuple values
      @param tuple : tuple of values
      @return the sum of the tuples
   */
  def Sum(tuples: Tuple5[Int, Int, Int, Int, Int]): Int = {
    val exprs: Array[Int] = ToArray(tuples)
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the tuple values
      @param tuple : tuple of values
      @return the sum of the tuples
   */
  def Sum(tuples: Tuple6[Int, Int, Int, Int, Int, Int]): Int = {
    val exprs: Array[Int] = ToArray(tuples)
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the tuple values
      @param tuple : tuple of values
      @return the sum of the tuples
   */
  def Sum(tuples: Tuple7[Int, Int, Int, Int, Int, Int, Int]): Int = {
    val exprs: Array[Int] = ToArray(tuples)
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the tuple values
      @param tuple : tuple of values
      @return the sum of the tuples
   */
  def Sum(tuples: Tuple8[Int, Int, Int, Int, Int, Int, Int, Int]): Int = {
    val exprs: Array[Int] = ToArray(tuples)
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the tuple values
      @param tuple : tuple of values
      @return the sum of the tuples
   */
  def Sum(tuples: Tuple9[Int, Int, Int, Int, Int, Int, Int, Int, Int]): Int = {
    val exprs: Array[Int] = ToArray(tuples)
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the tuple values
      @param tuple : tuple of values
      @return the sum of the tuples
   */
  def Sum(tuples: Tuple10[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int]): Int = {
    val exprs: Array[Int] = ToArray(tuples)
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the tuple values coerced to Float
      @param tuples : an array of tuples
      @return the sum of the tuples
   */
  def Sum(tuples: Tuple2[Float, Float]): Float = {
    val exprs: Array[Float] = ToArray(tuples)
    if (exprs != null && exprs.size > 0)
      exprs.reduceLeft(_ + _)
    else
      0
  }

  /** 
      Answer the sum of the tuple values coerced to Float
      @param tuples : a tuple
      @return the sum of the tuple elements
   */
  def SumToFloat(tuples: Tuple2[Any, Any]): Float = {
    val exprs: Array[Float] = ToArrayOfFloat(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the array of tuples coerced to an array of Float
      @param tuples : an array of tuples
      @return the sum of the tuple elements
   */
  def SumToArrayOfFloat(tuples: Array[Tuple2[Any, Any]]): Array[Float] = {
    val exprs: Array[Float] = tuples.map(tuple => SumToFloat(tuple))
    //exprs.reduceLeft(_ + _)
    exprs
  }

  /** 
      Answer the sum of the tuple values coerced to Double
      @param tuples : a tuple
      @return the sum of the tuple elements
   */
  def SumToDouble(tuples: Tuple2[Any, Any]): Double = {
    val exprs: Array[Double] = ToArrayOfDouble(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the array of tuples coerced to an array of Double
      @param tuples : an array of tuples
      @return the sum of the tuple elements
   */
  def SumToArrayOfDouble(tuples: Array[Tuple2[Any, Any]]): Array[Double] = {
    val exprs: Array[Double] = tuples.map(tuple => SumToDouble(tuple))
    exprs
  }

  /** 
      Answer the sum of the tuple values coerced to Int
      @param tuples : a tuple
      @return the sum of the tuple elements
   */
  def SumToInt(tuples: Tuple2[Any, Any]): Int = {
    val exprs: Array[Int] = ToArrayOfInt(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the array of tuples coerced to an array of Int
      @param tuples : an array of tuples
      @return the sum of the tuple elements
   */
  def SumToArrayOfInt(tuples: Array[Tuple2[Any, Any]]): Array[Int] = {
    val exprs: Array[Int] = tuples.map(tuple => SumToInt(tuple))
    exprs
  }

  /** 
      Answer the sum of the tuple values 
      @param tuples : an array of tuples
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple3[Float, Float, Float]): Float = {
    val exprs: Array[Float] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values coerced to Float
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def SumToFloat(tuples: Tuple3[Any, Any, Any]): Float = {
    val exprs: Array[Float] = ToArrayOfFloat(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the array of tuples coerced to an array of Float
      @param tuples : an array of tuple 
      @return the sum of the tuple's values as an array
   */
  def SumToArrayOfFloat(tuples: Array[Tuple3[Any, Any, Any]]): Array[Float] = {
    val exprs: Array[Float] = tuples.map(tuple => SumToFloat(tuple))
    //exprs.reduceLeft(_ + _)
    exprs
  }

  /** 
      Answer the sum of the tuple values coerced to Double
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def SumToDouble(tuples: Tuple3[Any, Any, Any]): Double = {
    val exprs: Array[Double] = ToArrayOfDouble(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the array of tuples coerced to an array of Double
      @param tuples : an array of tuple 
      @return the sum of the tuple's values as an array
   */
  def SumToArrayOfDouble(tuples: Array[Tuple3[Any, Any, Any]]): Array[Double] = {
    val exprs: Array[Double] = tuples.map(tuple => SumToDouble(tuple))
    exprs
  }

  /** 
      Answer the sum of the tuple values coerced to Int
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def SumToInt(tuples: Tuple3[Any, Any, Any]): Int = {
    val exprs: Array[Int] = ToArrayOfInt(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the array of tuples coerced to an array of Int
      @param tuples : an array of tuple 
      @return the sum of the tuple's values as an array
   */
  def SumToArrayOfInt(tuples: Array[Tuple3[Any, Any, Any]]): Array[Int] = {
    val exprs: Array[Int] = tuples.map(tuple => SumToInt(tuple))
    exprs
  }

  /** 
      Answer the sum of the tuple values coerced to Float
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple4[Float, Float, Float, Float]): Float = {
    val exprs: Array[Float] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values coerced to Float
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def SumToFloat(tuples: Tuple4[Any, Any, Any, Any]): Float = {
    val exprs: Array[Float] = ToArrayOfFloat(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the array of tuples coerced to an array of Float
      @param tuples : an array of tuple 
      @return the sum of the tuple's values as an array
   */
  def SumToArrayOfFloat(tuples: Array[Tuple4[Any, Any, Any, Any]]): Array[Float] = {
    val exprs: Array[Float] = tuples.map(tuple => SumToFloat(tuple))
    //exprs.reduceLeft(_ + _)
    exprs
  }

  /** 
      Answer the sum of the tuple values coerced to Double
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def SumToDouble(tuples: Tuple4[Any, Any, Any, Any]): Double = {
    val exprs: Array[Double] = ToArrayOfDouble(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the array of tuples coerced to an array of Double
      @param tuples : an array of tuple 
      @return the sum of the tuple's values as an array
   */
  def SumToArrayOfDouble(tuples: Array[Tuple4[Any, Any, Any, Any]]): Array[Double] = {
    val exprs: Array[Double] = tuples.map(tuple => SumToDouble(tuple))
    exprs
  }

  /** 
      Answer the sum of the tuple values coerced to Int
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def SumToInt(tuples: Tuple4[Any, Any, Any, Any]): Int = {
    val exprs: Array[Int] = ToArrayOfInt(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the array of tuples coerced to an array of Int
      @param tuples : an array of tuple 
      @return the sum of the tuple's values as an array
   */
  def SumToArrayOfInt(tuples: Array[Tuple4[Any, Any, Any, Any]]): Array[Int] = {
    val exprs: Array[Int] = tuples.map(tuple => SumToInt(tuple))
    exprs
  }

  /** 
      Answer the sum of the tuple values coerced to Float
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple5[Float, Float, Float, Float, Float]): Float = {
    val exprs: Array[Float] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values coerced to Float
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def SumToFloat(tuples: Tuple5[Any, Any, Any, Any, Any]): Float = {
    val exprs: Array[Float] = ToArrayOfFloat(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the array of tuples coerced to an array of Float
      @param tuples : an array of tuple 
      @return the sum of the tuple's values as an array
   */
  def SumToArrayOfFloat(tuples: Array[Tuple5[Any, Any, Any, Any, Any]]): Array[Float] = {
    val exprs: Array[Float] = tuples.map(tuple => SumToFloat(tuple))
    exprs
  }

  /** 
      Answer the sum of the tuple values coerced to Double
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def SumToDouble(tuples: Tuple5[Any, Any, Any, Any, Any]): Double = {
    val exprs: Array[Double] = ToArrayOfDouble(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the array of tuples coerced to an array of Double
      @param tuples : an array of tuple 
      @return the sum of the tuple's values as an array
   */
  def SumToArrayOfDouble(tuples: Array[Tuple5[Any, Any, Any, Any, Any]]): Array[Double] = {
    val exprs: Array[Double] = tuples.map(tuple => SumToDouble(tuple))
    exprs
  }

  /** 
      Answer the sum of the tuple values coerced to Int
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def SumToInt(tuples: Tuple5[Any, Any, Any, Any, Any]): Int = {
    val exprs: Array[Int] = ToArrayOfInt(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the array of tuples coerced to an array of Int
      @param tuples : an array of tuple 
      @return the sum of the tuple's values as an array
   */
  def SumToArrayOfInt(tuples: Array[Tuple5[Any, Any, Any, Any, Any]]): Array[Int] = {
    val exprs: Array[Int] = tuples.map(tuple => SumToInt(tuple))
    exprs
  }

  /** 
      Answer the sum of the tuple values coerced to Float
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple6[Float, Float, Float, Float, Float, Float]): Float = {
    val exprs: Array[Float] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values coerced to Float
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def SumToFloat(tuples: Tuple6[Any, Any, Any, Any, Any, Any]): Float = {
    val exprs: Array[Float] = ToArrayOfFloat(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the array of tuples coerced to an array of Float
      @param tuples : an array of tuple 
      @return the sum of the tuple's values as an array
   */
  def SumToArrayOfFloat(tuples: Array[Tuple6[Any, Any, Any, Any, Any, Any]]): Array[Float] = {
    val exprs: Array[Float] = tuples.map(tuple => SumToFloat(tuple))
    exprs
  }

  /** 
      Answer the sum of the tuple values coerced to Double
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def SumToDouble(tuples: Tuple6[Any, Any, Any, Any, Any, Any]): Double = {
    val exprs: Array[Double] = ToArrayOfDouble(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the array of tuples coerced to an array of Double
      @param tuples : an array of tuple 
      @return the sum of the tuple's values as an array
   */
  def SumToArrayOfDouble(tuples: Array[Tuple6[Any, Any, Any, Any, Any, Any]]): Array[Double] = {
    val exprs: Array[Double] = tuples.map(tuple => SumToDouble(tuple))
    exprs
  }

  /** 
      Answer the sum of the tuple values coerced to Double
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def SumToInt(tuples: Tuple6[Any, Any, Any, Any, Any, Any]): Int = {
    val exprs: Array[Int] = ToArrayOfInt(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the array of tuples coerced to an array of Int
      @param tuples : an array of tuple 
      @return the sum of the tuple's values as an array
   */
  def SumToArrayOfInt(tuples: Array[Tuple6[Any, Any, Any, Any, Any, Any]]): Array[Int] = {
    val exprs: Array[Int] = tuples.map(tuple => SumToInt(tuple))
    exprs
  }

  /** FIXME: Do SumTo<Scalar> and SumToArrayOf<Scalar> for the remaining Tuple<N> */
  /** 
      Answer the sum of the tuple values
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple7[Float, Float, Float, Float, Float, Float, Float]): Float = {
    val exprs: Array[Float] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple8[Float, Float, Float, Float, Float, Float, Float, Float]): Float = {
    val exprs: Array[Float] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple9[Float, Float, Float, Float, Float, Float, Float, Float, Float]): Float = {
    val exprs: Array[Float] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple10[Float, Float, Float, Float, Float, Float, Float, Float, Float, Float]): Float = {
    val exprs: Array[Float] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple2[Double, Double]): Double = {
    val exprs: Array[Double] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple3[Double, Double, Double]): Double = {
    val exprs: Array[Double] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple4[Double, Double, Double, Double]): Double = {
    val exprs: Array[Double] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple5[Double, Double, Double, Double, Double]): Double = {
    val exprs: Array[Double] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple6[Double, Double, Double, Double, Double, Double]): Double = {
    val exprs: Array[Double] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple7[Double, Double, Double, Double, Double, Double, Double]): Double = {
    val exprs: Array[Double] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple8[Double, Double, Double, Double, Double, Double, Double, Double]): Double = {
    val exprs: Array[Double] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple9[Double, Double, Double, Double, Double, Double, Double, Double, Double]): Double = {
    val exprs: Array[Double] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the tuple values
      @param tuples : a tuple 
      @return the sum of the tuple's values
   */
  def Sum(tuples: Tuple10[Double, Double, Double, Double, Double, Double, Double, Double, Double, Double]): Double = {
    val exprs: Array[Double] = ToArray(tuples)
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Sum(exprs: List[Int]): Int = {
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Sum(exprs: List[Long]): Long = {
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Sum(exprs: List[Double]): Double = {
    exprs.reduceLeft(_ + _)
  }

  /** 
      Answer the sum of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Sum(exprs: List[Float]): Float = {
    exprs.reduceLeft(_ + _)
  }

  /** avg (average)*/

  /** 
      Answer the average of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Avg(exprs: ArrayBuffer[Int]): Int = {
    Sum(exprs) / exprs.length
  }

  /** 
      Answer the average of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Avg(exprs: ArrayBuffer[Long]): Long = {
    Sum(exprs) / exprs.length
  }

  /** 
      Answer the average of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Avg(exprs: ArrayBuffer[Double]): Double = {
    Sum(exprs) / exprs.length
  }

  /** 
      Answer the average of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Avg(exprs: ArrayBuffer[Float]): Float = {
    Sum(exprs) / exprs.length
  }

  /** 
      Answer the average of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Avg(exprs: Array[Int]): Int = {
    Sum(exprs) / exprs.length
  }

  /** 
      Answer the average of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Avg(exprs: Array[Long]): Long = {
    Sum(exprs) / exprs.length
  }

  /** 
      Answer the average of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Avg(exprs: Array[Double]): Double = {
    Sum(exprs) / exprs.length
  }

  /** 
      Answer the average of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Avg(exprs: Array[Float]): Float = {
    Sum(exprs) / exprs.length
  }

  /** 
      Answer the average of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Avg(exprs: List[Int]): Int = {
    Sum(exprs) / exprs.length
  }

  /** 
      Answer the average of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Avg(exprs: List[Long]): Long = {
    Sum(exprs) / exprs.length
  }

  /** 
      Answer the average of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Avg(exprs: List[Double]): Double = {
    Sum(exprs) / exprs.length
  }

  /** 
      Answer the average of the collection elements
      @param exprs : a collection of scalar 
      @return the sum of the collections's values
   */
  def Avg(exprs: List[Float]): Float = {
    Sum(exprs) / exprs.length
  }


  /** 
      Answer the count of the collection elements
      @param exprs : a collection of scalar 
      @return the count of the collections's elements
   */
  def Count[A](exprs: ArrayBuffer[A]): Int = {
    exprs.length
  }

  /** 
      Answer the count of the collection elements
      @param exprs : a collection of scalar 
      @return the count of the collections's elements
   */
  def Count[A](exprs: Array[A]): Int = {
    exprs.length
  }

  /** 
      Answer the count of the collection elements
      @param exprs : a collection of scalar 
      @return the count of the collections's elements
   */
  def Count[A](exprs: List[A]): Int = {
    exprs.length
  }

  /** 
      Answer the count of the collection elements
      @param exprs : a collection of scalar 
      @return the count of the collections's elements
   */
  def Count[A](exprs: Set[A]): Int = {
    exprs.size
  }

  /** 
      Answer the count of the collection elements
      @param exprs : a collection of scalar 
      @return the count of the collections's elements
   */
  def Count[A](exprs: Queue[A]): Int = {
    exprs.size
  }

  /** 
      Answer the count of the collection elements
      @param exprs : a collection of scalar 
      @return the count of the collections's elements
   */
  def Count[A, B](exprs: Map[A, B]): Int = {
    exprs.size
  }

  /** 
      Answer the count of the collection elements
      @param exprs : a collection of scalar 
      @return the count of the collections's elements
   */
  def Count[A, B](exprs: HashMap[A, B]): Int = {
    exprs.size
  }


  /** 
      Answer the median of the collection elements, where mean of two middle values is taken for even number of elements
      @param exprs : a collection of scalar 
      @return the median of the collections's elements
   */
  def Median(exprs: ArrayBuffer[Int]): Int = {
    val mid = exprs.length
    if (mid % 2 == 0) {
      exprs(mid)
    } else {
      val midplus = mid + 1
      (exprs(mid) + exprs(midplus) / 2)
    }
  }

  /** 
      Answer the median of the collection elements, where mean of two middle values is taken for even number of elements
      @param exprs : a collection of scalar 
      @return the median of the collections's elements
   */
  def Median(exprs: ArrayBuffer[Long]): Long = {
    val mid = exprs.length
    if (mid % 2 == 0) {
      exprs(mid)
    } else {
      val midplus = mid + 1
      (exprs(mid) + exprs(midplus) / 2)
    }
  }

  /** 
      Answer the median of the collection elements, where mean of two middle values is taken for even number of elements
      @param exprs : a collection of scalar 
      @return the median of the collections's elements
   */
  def Median(exprs: ArrayBuffer[Double]): Double = {
    val mid = exprs.length
    if (mid % 2 == 0) {
      exprs(mid)
    } else {
      val midplus = mid + 1
      (exprs(mid) + exprs(midplus) / 2)
    }
  }

  /** 
      Answer the median of the collection elements, where mean of two middle values is taken for even number of elements
      @param exprs : a collection of scalar 
      @return the median of the collections's elements
   */
  def Median(exprs: ArrayBuffer[Float]): Float = {
    val mid = exprs.length
    if (mid % 2 == 0) {
      exprs(mid)
    } else {
      val midplus = mid + 1
      (exprs(mid) + exprs(midplus) / 2)
    }
  }

  /** 
      Answer the median of the collection elements, where mean of two middle values is taken for even number of elements
      @param exprs : a collection of scalar 
      @return the median of the collections's elements
   */
  def Median(exprs: Array[Int]): Int = {
    val mid = exprs.length
    if (mid % 2 == 0) {
      exprs(mid)
    } else {
      val midplus = mid + 1
      (exprs(mid) + exprs(midplus) / 2)
    }
  }

  /** 
      Answer the median of the collection elements, where mean of two middle values is taken for even number of elements
      @param exprs : a collection of scalar 
      @return the median of the collections's elements
   */
  def Median(exprs: Array[Long]): Long = {
    val mid = exprs.length
    if (mid % 2 == 0) {
      exprs(mid)
    } else {
      val midplus = mid + 1
      (exprs(mid) + exprs(midplus) / 2)
    }
  }

  /** 
      Answer the median of the collection elements, where mean of two middle values is taken for even number of elements
      @param exprs : a collection of scalar 
      @return the median of the collections's elements
   */
  def Median(exprs: Array[Double]): Double = {
    val mid = exprs.length
    if (mid % 2 == 0) {
      exprs(mid)
    } else {
      val midplus = mid + 1
      (exprs(mid) + exprs(midplus) / 2)
    }
  }

  /** 
      Answer the median of the collection elements, where mean of two middle values is taken for even number of elements
      @param exprs : a collection of scalar 
      @return the median of the collections's elements
   */
  def Median(exprs: Array[Float]): Float = {
    val mid = exprs.length
    if (mid % 2 == 0) {
      exprs(mid)
    } else {
      val midplus = mid + 1
      (exprs(mid) + exprs(midplus) / 2)
    }
  }

  /** 
      Answer the product of the collection elements
      @param exprs : a collection of scalar 
      @return the product of the collections's elements
   */
  def Product(exprs: ArrayBuffer[Int]): Int = {
    Multiply(exprs)
  }

  /** 
      Answer the product of the collection elements
      @param exprs : a collection of scalar 
      @return the product of the collections's elements
   */
  def Product(exprs: ArrayBuffer[Long]): Long = {
    Multiply(exprs)
  }

  /** 
      Answer the product of the collection elements
      @param exprs : a collection of scalar 
      @return the product of the collections's elements
   */
  def Product(exprs: ArrayBuffer[Double]): Double = {
    Multiply(exprs)
  }

  /** 
      Answer the product of the collection elements
      @param exprs : a collection of scalar 
      @return the product of the collections's elements
   */
  def Product(exprs: ArrayBuffer[Float]): Float = {
    Multiply(exprs)
  }

  /** 
      Answer the product of the collection elements
      @param exprs : a collection of scalar 
      @return the product of the collections's elements
   */
  def Product(exprs: Array[Int]): Int = {
    Multiply(exprs)
  }

  /** 
      Answer the product of the collection elements
      @param exprs : a collection of scalar 
      @return the product of the collections's elements
   */
  def Product(exprs: Array[Long]): Long = {
    Multiply(exprs)
  }

  /** 
      Answer the product of the collection elements
      @param exprs : a collection of scalar 
      @return the product of the collections's elements
   */
  def Product(exprs: Array[Double]): Double = {
    Multiply(exprs)
  }

  /** 
      Answer the product of the collection elements
      @param exprs : a collection of scalar 
      @return the product of the collections's elements
   */
  def Product(exprs: Array[Float]): Float = {
    Multiply(exprs)
  }

  /**  log10, ln, sqrt, abs, exp, pow, threshold, floor, ceil, round */

  /**
   * Answer the log 10 of the supplied double.
   * @param expr : a double
   */
  def log10(expr: Double): Double = {
    scala.math.log10(expr)
  }

  /**
   * Answer the natural log of the supplied double.
   * @param expr : a double
   */
  def ln(expr: Double): Double = {
    scala.math.log(expr)
  }

  /**
   * Answer the square root of the supplied double.
   * @param expr : a double
   */
  def sqrt(expr: Double): Double = {
    scala.math.log(expr)
  }

  /**
   * Answer the absolute value of the supplied scalar.
   * @param expr : a scalar
   */
  def abs(expr: Int): Int = {
    scala.math.abs(expr)
  }
  /**
   * Answer the absolute value of the supplied scalar.
   * @param expr : a scalar
   */
  def abs(expr: Long): Long = {
    scala.math.abs(expr)
  }
  /**
   * Answer the absolute value of the supplied scalar.
   * @param expr : a scalar
   */
  def abs(expr: Float): Float = {
    scala.math.abs(expr)
  }
  /**
   * Answer the absolute value of the supplied scalar.
   * @param expr : a scalar
   */
  def abs(expr: Double): Double = {
    scala.math.abs(expr)
  }

  /**
   * Answer Returns Euler's number e raised to the power of the supplied double value
   * @param expr : a double
   * @return the exponential function result
   */
  def exp(expr: Double): Double = {
    scala.math.exp(expr)
  }

  /**
   * Answer the scalar taken to the supplied power
   * @param x : a Double
   * @param y : a Int
   * @return x^y
   */
  def pow(x: Double, y: Int): Double = {
    scala.math.pow(x, y)
  }

  /**
   * Answer if x has met or exceeded the threshold
   * @param x : a scalar
   * @param y : a scalar
   * @return 1 if met else 0
   */
  def threshold(x: Int, y: Int): Int = {
    if (GreaterThan(x, y)) 1 else 0
  }
  /**
   * Answer if x has met or exceeded the threshold
   * @param x : a scalar
   * @param y : a scalar
   * @return 1 if met else 0
   */
  def threshold(x: Long, y: Long): Int = {
    if (GreaterThan(x, y)) 1 else 0
  }
  /**
   * Answer if x has met or exceeded the threshold
   * @param x : a scalar
   * @param y : a scalar
   * @return 1 if met else 0
   */
  def threshold(x: Float, y: Float): Int = {
    if (GreaterThan(x, y)) 1 else 0
  }
  /**
   * Answer if x has met or exceeded the threshold
   * @param x : a scalar
   * @param y : a scalar
   * @return 1 if met else 0
   */
  def threshold(x: Double, y: Double): Int = {
    if (GreaterThan(x, y)) 1 else 0
  }

  /**
   * Answer the floor of the supplied Double
   * @param expr : a Double
   * @return floor(expr)
   */
  def floor(expr: Double): Double = {
    scala.math.floor(expr)
  }

  /**
   * Answer the ceil of the supplied Double
   * @param expr : a Double
   * @return ceil(expr)
   */
  def ceil(expr: Double): Double = {
    scala.math.ceil(expr)
  }

  /**
   * Answer the integer value closest to supplied Double
   * @param expr : a Double
   * @return round(expr)
   */
  def round(expr: Double): Double = {
    scala.math.round(expr)
  }

  /** 
   *  IsMissing determines if the named field DOES NOT exist or EXISTS but with no legal value.
   *  
   *  @param ctx the runtime context for a given model
   *  @param fldName the name of the field being sought... it can be compound '.' qualified name
   *  @return true if it is missing else false
   *
   *  NOTE: Compound names are currently limited to two names (e.g., container.fld).  This will change
   *  when metadata is made available to the runtime context for the models. 
   *  
   */
  def IsMissing(ctx : Context, fldName : String) : Boolean = {
	  val nameParts : Array[String] = if (fldName.contains(".")) {
		  fldName.split('.')
	  } else {
		  Array(fldName)
	  }
	  /** if just one name, look in dictionaries */
	  val notMissing : Boolean = if (nameParts.size == 1) {
		  ctx.valueSetFor(fldName)
	  } else {
		  /** 
		   *  Obtain the MessageContainerBase for the message or container ... for now just the first namePart
		   *  FIXME: This will change when derived concepts (ModelNamespace.ModelName.field) are introduced 
		   */
		  if (nameParts.size == 2) {
			  val msgContainerName : String = nameParts(0)
			  val fieldName : String = nameParts(1)
			  val msgOrContainer : MessageContainerBase = if (ctx.isFieldInTransformationDict(msgContainerName)) {
				  val derivedFld : DataValue = ctx.valueFor(msgContainerName)
				  val anyValue : AnyDataValue = if (derivedFld.isInstanceOf[AnyDataValue]) derivedFld.asInstanceOf[AnyDataValue] else null
				  val mOrC : MessageContainerBase = if (anyValue.Value.isInstanceOf[MessageContainerBase]) anyValue.Value.asInstanceOf[MessageContainerBase] else null
				  mOrC
			  } else {
				  if (ctx.isFieldInDataDict(msgContainerName)) {
					  val dataFld : DataValue = ctx.valueFor(msgContainerName)
					  val anyValue : AnyDataValue = if (dataFld.isInstanceOf[AnyDataValue]) dataFld.asInstanceOf[AnyDataValue] else null
					  val mOrC : MessageContainerBase = if (anyValue.Value.isInstanceOf[MessageContainerBase]) anyValue.Value.asInstanceOf[MessageContainerBase] else null
					  mOrC
				  } else {
					  null
				  }
			  }
		
			  val itsThere : Boolean = if (msgOrContainer != null) {
				  (msgOrContainer.IsFixed || (msgOrContainer.IsKv && (msgOrContainer.getOrElse(fieldName,null) != null)))
			  } else {
				  false
			  }
			  itsThere
		  } else {
			  logger.error("Unable to handle isMissing tests on container of containers at this time... more complete solution coming...")
			  logger.error("... need metadata manager to find type information at runtime to walk down hierarchies > 2 levels.")
			  false
		  }	  
	  }
    
	  (! notMissing)
  }
  
  
  /** 
   *  IsNotMissing determines if the named field exists with a legal value.
   *  
   *  @param ctx the runtime context for a given model
   *  @param fldName the name of the field being sought... it can be compound '.' qualified name
   *  @return true if it is present else false
   *  
   *  NOTE: Compound names are currently limited to two names (e.g., container.fld).  This will change
   *  when metadata is made available to the runtime context for the models. 
   *  
   */
  def IsNotMissing(ctx : Context, fldName : String) : Boolean = {
	  (! IsMissing(ctx,fldName))
  }
  
  /** 
   *  Answer the current transaction id from the caller's runtime context.
   *  
   *  @param ctx the runtime context for a given model
   *  @return xid or 0 if the ctx is bogus
   *  
   */
  def getXid(ctx : Context) : Long = {
	  if (ctx != null) ctx.xId else 0
  }
  
  /** 
      Answer a new string with all characters of the supplied string folded to upper case.
      @param str : a String
      @return a copy of 'str' with all characters folded to upper case.
   */
  def uppercase(str: String): String = {
    str.toUpperCase()
  }

  /** 
      Answer a new string with all characters of the supplied string folded to lower case.
      @param str : a String
      @return a copy of 'str' with all characters folded to lower case.
   */
  def lowercase(str: String): String = {
    str.toLowerCase()
  }

  /** 
      Answer a portion of the supplied string whose first character is at 'startidx' for the specified length.

      @param str : a String
      @param startidx : the first character of interest from the str
      @param len : the number of characters of interest starting with 'startidx'
      @return the substring.
   */
  def substring(str: String, startidx: Int, len: Int): String = {
    str.substring(startidx, (startidx + len - 1))
  }

  /** 
      Answer a portion of the supplied string whose first character is at 'startidx' for the remainder of the string

      @param str : a String
      @param startidx : the first character of interest from the str
      @return the substring.
   */
  def substring(str: String, startidx: Int): String = {
    str.substring(startidx)
  }

  /** 
      Answer if 'inThis' string starts with 'findThis'

      @param str : a String
      @return a Boolean reflecting the answer.
   */
  def startsWith(inThis: String, findThis: String): Boolean = {
    inThis.startsWith(findThis)
  }

  /** 
      Answer if 'inThis' string ends with 'findThis'
      @param str : a String
      @return a Boolean reflecting the answer.
   */
  def endsWith(inThis: String, findThis: String): Boolean = {
    inThis.endsWith(findThis)
  }

  /** 
      Answer if 'inThis' string starts with 'findThis'
      @param str : a String
      @return a new string with the white space trimmed from the beginning and end.
   */
  def trimBlanks(str: String): String = {
    str.trim()
  }

  /**
   * Returns a random UUID string
   * @return random UUID string 
   */
  def idGen() : String = {
      UUID.randomUUID().toString;
  }

  /** 
   *  Accept an indefinite number of objects and concatenate their string representations 
   *  @param args : arguments whose string representations will be concatenated   
   *  @return concatenation of args' string representations
   */
  def concat(args : Any*) : String = {
      val argList : List[Any] = args.toList
      argList.map( arg => if (arg != null) arg.toString else "" ).mkString("")
  }

  /** 
   *  Accept a parent variable, a child variable and a replacement variable.  Replace all instances of the child  inside the parent with the replacement.  The function will return a string
   *  @param relacewithin : The parent variable (can be any type) within which the replacement will be searched for
   *  @param inWord : The child string which will be searched for within the "replacewithin" variable
   *  @param replacewith : The string with which all instances of the child will be replaced in "replacewithin"
   *  @return outString : string where all "inwords" have been replaced by "replacewith"
   */   
  def replace (replacewithin: Any, inWord: Any, replacewith: Any): String = { 
      val replacewithinA : String = replacewithin.toString
      val inWordA : String = inWord.toString
      val replacewithA : String = replacewith.toString
      val outString : String = replacewithinA.replaceAll(inWordA, replacewithA)
      outString
  }

  /** 
   *  Accept a parent variable and a child variable.  Return a boolean value which is true if an instance of the child lies within the parent and false otherwise
   *  @param matchwithin : The parent variable (can be any type) within which the matching variable will be searched for
   *  @param matchwith : The child that will be searched for within "matchwithin"
   *  @return OutBool : Boolean value evaluating whether an instance of "matchwith" exists within "matchwithin"
   */
  def matches (matchwithin: Any, matchwith: Any): Boolean = { 
      val matchwithinA : String = matchwithin.toString
      var matchwithA : String = matchwith.toString
      val outString : String = matchwithinA.replaceAll(matchwithA, matchwithA + matchwithA)
      val outBool : Boolean = if (outString == matchwithinA) false; else true;
      outBool
  }

  /** 
   *  Generate a random Double between 0 and 1
   *  @return ranDouble : random Double between 0 and 1
   */

  def random(): Double = {
    val r : scala.util.Random = scala.util.Random
    val randouble : Double = r.nextDouble
    randouble
  }

  /** 
   *  Answer string's length.  If a null is supplied, 0 length answered
   *  @return length of the supplied string
   */

  def length(str : String): Int = {
    val len : Int = if (str != null) str.size else 0
    len
  }

  /** 
   *  Accept a number of any type and format it in a specified manner
   *  @param num : The number which is to be formatted
   *  @param formatting : The format which the number is to take.  This should be given in standard form, e.g. %.2f for a 2 decimal place float
   *  @return formattedStr : A string version of the number formatted in the required way
   */
  def formatNumber[T]( num : T, formatting : String) : String = {
    val formattedStr : String = (formatting.format(num)).toString
    formattedStr
  }

  /** 
   *  Print the two strings to the log.  The first is some location or context information.  The second
   *  is the event description.
   *  
   *  @param severity  a string describing log severity level ... any {error, warn, info, debug, trace}
   *  @param contextMsg  a string describing the context of why this message is to be logged (or anything else for that matter)
   *  @param eventMsg a string that describes what has actually happened
   *  @param bool a Boolean that is returned as this function's result (to play into the pmml logic as desired)
   *  @return bool
   */
  def logMsg(severity : String, contextMsg : String, eventMsg : String, bool : Boolean) : Boolean = {
      if (severity != null && contextMsg != null && eventMsg != null) {
          val sev : String = severity.toLowerCase.trim
          sev match {
              case "error" => logger.error(s"$contextMsg...$eventMsg")
              case "warn" => logger.warn(s"$contextMsg...$eventMsg")
              case "info" => logger.info(s"$contextMsg...$eventMsg")
              case "debug" => logger.debug(s"$contextMsg...$eventMsg")
              case "trace" => logger.trace(s"$contextMsg...$eventMsg")
              case _ => logger.trace(s"$contextMsg...$eventMsg")
          }
      } else {
          logger.error("LogMsg called with bogus arguments")
      }
      bool
  }
  


  /** 

      Date and Time Functions 

  */


  /** 
      Answer the number of days since the supplied year and today, including the year specified.
      @param yr : a year of interest
      @return the number of days since that time and now
   */
  def dateDaysSinceYear(yr: Int): Long = {
    val dt: org.joda.time.LocalDateTime = new org.joda.time.LocalDateTime(yr, 1, 1, 0, 0, 0)
    var now: org.joda.time.LocalDateTime = new org.joda.time.LocalDateTime()
    val dys: org.joda.time.Days = org.joda.time.Days.daysBetween(dt, now)
    val days : Long = dys.getDays+1
    days
  }

  /** 
      Answer the number of seconds since the supplied year and today, including the year specified.
      @param yr : a year of interest
      @return the number of seconds since that time and now
   */
  def dateSecondsSinceYear(yr: Int): Long = {
    val dt: org.joda.time.LocalDateTime = new org.joda.time.LocalDateTime(yr, 1, 1, 0, 0, 0)
    var now: org.joda.time.LocalDateTime = new org.joda.time.LocalDateTime()
    val scs: org.joda.time.Seconds = org.joda.time.Seconds.secondsBetween(dt, now)
    val secs: Long = scs.getSeconds + (milliSecondsInDay / 1000)
    secs
  }

  /** 
      Answer the number of seconds since midnight today.
      @return the number of seconds since midnight today.
   */
  def dateSecondsSinceMidnight(): Long = {
    var now: org.joda.time.LocalTime = new org.joda.time.LocalTime()
    val secs: Long = now.getHourOfDay() * 60 * 60 + 
    				now.getMinuteOfHour() * 60 + 
    				now.getSecondOfMinute() + 
    				(if (now.getMillisOfSecond() >= 500) 1 else 0)
    secs
  }

  /** 
      Answer the number of milliseconds since midnight today
      @return the number of milliseconds since midnight today.
   */
  def dateMilliSecondsSinceMidnight(): Long = {
    dateSecondsSinceMidnight() * 1000
  }

  /** 
      Answer the number of milliseconds since the midnight today
      @return the number of milliseconds since the midnight today
   */
  def Timenow(): Long = {
    dateSecondsSinceMidnight() * 1000
  }

  /** 
      Answer the number of seconds from the supplied millisecs value
      @param millisecs : number of millisecs
      @return the number of seconds 
   */
  def AsSeconds(milliSecs: Long): Long = {
    milliSecs / 1000
  }

  /** 
      Answer the number of milliseconds since the epoch as of right now.
      @return the number of milliseconds since the epoch as of right now.
   */
  def Now(): Long = {
    var now: org.joda.time.DateTime = new org.joda.time.DateTime()
    now.getMillis()
  }

  /**
   *  Answer the number of millisecs some numYrs ago.
   *  @param numYrs : the number of years
   *  @return the millisecs from the epoch for that time.
   */
  def YearsAgo(numYrs: Int): Long = {
    val rightNow: org.joda.time.DateTime = new org.joda.time.DateTime()
    val someTimeAgo = rightNow.minusYears(numYrs)
    someTimeAgo.getMillis()
  }

  /**
   *  Answer the number of millisecs a numYrs ago from the supplied ISO 8601 compressed int date
   *  @param someDate an 8601 date compressed into integer (format OHHMMSSCC, WHERE CC REPRESENTS HUNDREDTHS OF A SECOND)
   *  @param numDays number of weeks to subtract
   *  @return millisecs for someDate - numYrs
   */
  def YearsAgo(someDate: Int, numYrs: Int): Long = {
    val dateAsMillisecs : Long = toDateTime(someDate).getMillis()
    val someDt : org.joda.time.DateTime = new org.joda.time.DateTime(dateAsMillisecs)
    val someTimeAgo = someDt.minusYears(numYrs)
    someTimeAgo.getMillis()
  }

  /**
   *  Answer an ISO8601 compressed integer for a numDays ago from the supplied ISO 8601 compressed int date 
   *  @param someDate an 8601 date compressed into integer (format OHHMMSSCC, WHERE CC REPRESENTS HUNDREDTHS OF A SECOND)
   *  @param numDays number of years to subtract
   *  @return ISO8601 date for someDate - numYrs
   */
  def YearsAgoAsISO8601(someDate: Int, numYrs: Int): Int = {
    AsCompressedDate(YearsAgo(someDate, numYrs))
  }

  /**
   *  Answer the number of millisecs numMos ago.
   */
  def MonthsAgo(numMos: Int): Long = {
    val rightNow: org.joda.time.DateTime = new org.joda.time.DateTime()
    val someTimeAgo = rightNow.minusMonths(numMos)
    someTimeAgo.getMillis()
  }

  /**
   *  Answer the number of millisecs a numMos ago from the supplied ISO 8601 compressed int date
   *  @param someDate an 8601 date compressed into integer (format OHHMMSSCC, WHERE CC REPRESENTS HUNDREDTHS OF A SECOND)
   *  @param numDays number of weeks to subtract
   *  @return millisecs for someDate - numMos
   */
  def MonthsAgo(someDate: Int, numMos: Int): Long = {
    val dateAsMillisecs : Long = toDateTime(someDate).getMillis()
    val someDt : org.joda.time.DateTime = new org.joda.time.DateTime(dateAsMillisecs)
    val someTimeAgo = someDt.minusMonths(numMos)
    someTimeAgo.getMillis()
  }

  /**
   *  Answer an ISO8601 compressed integer for a numDays ago from the supplied ISO 8601 compressed int date 
   *  @param someDate an 8601 date compressed into integer (format OHHMMSSCC, WHERE CC REPRESENTS HUNDREDTHS OF A SECOND)
   *  @param numDays number of months to subtract
   *  @return ISO8601 date for someDate - numMos
   */
  def MonthsAgoAsISO8601(someDate: Int, numMos: Int): Int = {
    AsCompressedDate(MonthsAgo(someDate, numMos))
  }

  /**
   *  Answer the number of millisecs from the epoch for the time a numWks ago.
   *  @param numWks an integer
   *  @return the tiem a numWks ago.
   */
  def WeeksAgo(numWks: Int): Long = {
    val rightNow: org.joda.time.DateTime = new org.joda.time.DateTime()
    val someTimeAgo = rightNow.minusWeeks(numWks)
    someTimeAgo.getMillis()
  }

  /**
   *  Answer the number of millisecs a numWks ago from the supplied ISO 8601 compressed int date
   *  @param someDate an 8601 date compressed into integer (format OHHMMSSCC, WHERE CC REPRESENTS HUNDREDTHS OF A SECOND)
   *  @param numDays number of weeks to subtract
   *  @return millisecs for someDate - numWks
   */
  def WeeksAgo(someDate: Int, numWks: Int): Long = {
    val dateAsMillisecs : Long = toDateTime(someDate).getMillis()
    val someDt : org.joda.time.DateTime = new org.joda.time.DateTime(dateAsMillisecs)
    val someTimeAgo = someDt.minusWeeks(numWks)
    someTimeAgo.getMillis()
  }

  /**
   *  Answer an ISO8601 compressed integer for a numDays ago from the supplied ISO 8601 compressed int date 
   *  @param someDate an 8601 date compressed into integer (format OHHMMSSCC, WHERE CC REPRESENTS HUNDREDTHS OF A SECOND)
   *  @param numDays number of weeks to subtract
   *  @return ISO8601 date for someDate - numWks
   */
  def WeeksAgoAsISO8601(someDate: Int, numWks: Int): Int = {
    AsCompressedDate(WeeksAgo(someDate, numWks))
  }

   /**
   *  Answer the number of millisecs a numDays ago.
   *  @param numDays number of days to subtract
   *  @return the time in millisecs from the epoch for the time a numDays ago
   */
  def DaysAgo(numDays: Int): Long = {
    val rightNow: org.joda.time.DateTime = new org.joda.time.DateTime()
    val someTimeAgo = rightNow.minusDays(numDays)
    someTimeAgo.getMillis()
  }

  /**
   *  Answer the number of millisecs a numDays ago from the supplied ISO 8601 compressed int date 
   *  @param someDate an 8601 date compressed into integer (format OHHMMSSCC, WHERE CC REPRESENTS HUNDREDTHS OF A SECOND)
   *  @param numDays number of days to subtract
   *  @return millisecs for someDate - numDays
   */
  def DaysAgo(someDate: Int, numDays: Int): Long = {
    val dateAsMillisecs : Long = toDateTime(someDate).getMillis()
    val someDt : org.joda.time.DateTime = new org.joda.time.DateTime(dateAsMillisecs)
    val someTimeAgo = someDt.minusDays(numDays)
    someTimeAgo.getMillis()
  }

  /**
   *  Answer an ISO8601 compressed integer for a numDays ago from the supplied ISO 8601 compressed int date 
   *  @param someDate an 8601 date compressed into integer (format OHHMMSSCC, WHERE CC REPRESENTS HUNDREDTHS OF A SECOND)
   *  @param numDays number of days to subtract
   *  @return ISO8601 int for someDate - numDays
   */
  def DaysAgoAsISO8601(someDate: Int, numDays: Int): Int = {
    AsCompressedDate(DaysAgo(someDate, numDays))
  }

  /** Coerce the yyyymmdd ISO8601 type compressed in integer to a DateTime
   *  
   *   @param yyyymmdd  ISO8601 type int compressed into integer
   *   @return joda DateTime 
   *
   *  NOTE: This function is currently not exported as the joda types are not available (at least for now) in the metadata.
   */
  def toDateTime(yyyymmdd: Int): DateTime = {
    val yyyy: Int = yyyymmdd / 10000
    val mm: Int = (yyyymmdd % 10000) / 100
    val day: Int = yyyymmdd % 100
    val someDate: DateTime = new DateTime(yyyy, mm, day, 0, 0)
    someDate
  }

  /** 
   *  Coerce the YYDDD Julian date to millisecs.  21st century
   *  dates are assumed.  If a bad Julian value is supplied, an error
   *  message is logged and the epoch (i.e., 0) is returned.
   *   @param yyddd  21st century julian date integer
   *   @return Long value of millisecs from epoch.
   */
  def toMillisFromJulian(yyddd: Int): Long = {
    val ydStr : String = yyddd.toString
    val yydddStr : String = if (ydStr.size == 4) ("0" + ydStr) else ydStr
    val reasonable : Boolean = if (yydddStr.length == 5) {
    	val yy : Int = yydddStr.slice(0, 2).toInt
    	val ddd : Int = yydddStr.slice(2,5).toInt
    	(yy >= 1 && yy <= 99 && ddd >= 1 && ddd <= 366)	
    } else {
    	false
    }
    val millis : Long = if (! reasonable) {
    	logger.error(s"toMillisFromJulian(yyddd = $yydddStr) ... malformed Julian date... expect YYDDD where YY>0 && YY <= 99 && DDD>0 && DDD<366")
    	0
    } else { 
	    val formatter : DateTimeFormatter  = DateTimeFormat.forPattern("yyyyDDD").withChronology(JulianChronology.getInstance)
	    try {
		    val lcd : DateTime = formatter.parseDateTime("20" + yydddStr);
		    lcd.getMillis()
	    } catch {
		    case iae:IllegalArgumentException => {
          
		    	logger.error(s"Unable to parse '20 + $yydddStr' with pattern - 'yyyyDDD'")
          
		    	0
		    }
	    }
    }
    millis
  }

  /**
   *  Convert time formatted in integer (compressed decimal)
   *  to millisecs.
   *      Format: OHHMMSSCC, WHERE CC REPRESENTS HUNDREDTHS OF A SECOND
   *  @param time, an Int
   *  @return time, an Int
   */
  def CompressedTimeHHMMSSCC2MilliSecs(compressedTime: Int): Long = {
    val hours = (compressedTime / 1000000) % 100
    val minutes = (compressedTime / 10000) % 100
    val seconds = (compressedTime / 100) % 100
    val millisecs = (compressedTime % 100) * 10

    val millis = (hours * 60 * 60 + minutes * 60 + seconds) * 1000 + millisecs

    millis
  }

  /**
   *  Convert time formatted in integer (compressed decimal)
   *  to seconds.
   *      Format: OHHMMSSCC, WHERE CC REPRESENTS HUNDREDTHS OF A SECOND
   *  @param time, an Int
   *  @return time, an Int
   */
  def CompressedTimeHHMMSSCC2Secs(compressedTime: Int): Int = {
    val hours = (compressedTime / 1000000) % 100
    val minutes = (compressedTime / 10000) % 100
    val seconds = (compressedTime / 100) % 100
    val millisecs = (compressedTime % 100) * 10

    val evtseconds = hours * 60 * 60 + minutes * 60 + seconds + (if (millisecs >= 500) 1 else 0)

    evtseconds
  }

  /** 
      Convert millisecs to ISO8601 style date in integer 
      @param millisecs some time since the epoch expressed in millisecs
      @return an integer with the date encoded in decimal form 
  */

  def AsCompressedDate(milliSecs: Long): Int = {
    val dt: LocalDate = new LocalDate(milliSecs)
    val dtAtMidnight: DateTime = dt.toDateTimeAtStartOfDay
    val yr: Int = dtAtMidnight.year().get()
    val mo: Int = dtAtMidnight.monthOfYear().get()
    val day: Int = dtAtMidnight.dayOfMonth().get()
    val compressedDate: Int = yr * 10000 + mo * 100 + day

    compressedDate
  }

  /** 
      Extract the Month from the IS08601 compressed date 
      @param dt an Int in ISO8601 format 
      @return the number of months from that date
   */
  def MonthFromISO8601Int(dt: Int): Int = {
    val mm: Int = (dt % 1000) / 100
    mm
  }

  /** 
      Extract the Year from the IS08601 compressed date  
      @param dt an Int in ISO8601 format 
      @return the number of years from that date
   */
  def YearFromISO8601Int(dt: Int): Int = {
    val yyyy: Int = dt / 10000
    yyyy
  }

  /** 
      Extract the Day of the Month from the IS08601 compressed date   
      @param dt an Int in ISO8601 format 
      @return the day of the month from that date
   */
  def DayOfMonthFromISO8601Int(dt: Int): Int = {
    val day: Int = dt % 100
    day
  }

  /** 
      Calculate age from yyyymmdd ISO8601 type birthdate as of right now.
      @param yyyymmdd a birth date Int in ISO8601 format
      @return age
   */
  def AgeCalc(yyyymmdd: Int): Int = {
    val yyyy: Int = yyyymmdd / 10000
    val mm: Int = (yyyymmdd % 1000) / 100
    val day: Int = yyyymmdd % 100
    val birthDate: LocalDate = new LocalDate(yyyy, mm, day)
    val age: Int = Years.yearsBetween(birthDate, new LocalDate).getYears
    age
  }

    /**
       Convert the supplied iso8601 date integer according to these format codes:
     
     {{{ 
     Symbol  Meaning                      Presentation  Examples
     ------  -------                      ------------  -------
     G       era                          text          AD
     C       century of era (>=0)         number        20
     Y       year of era (>=0)            year          1996
    
     x       weekyear                     year          1996
     w       week of weekyear             number        27
     e       day of week                  number        2
     E       day of week                  text          Tuesday; Tue
    
     y       year                         year          1996
     D       day of year                  number        189
     M       month of year                month         July; Jul; 07
     d       day of month                 number        10
    
     a       halfday of day               text          PM
     K       hour of halfday (0~11)       number        0
     h       clockhour of halfday (1~12)  number        12
    
     H       hour of day (0~23)           number        0
     k       clockhour of day (1~24)      number        24
     m       minute of hour               number        30
     s       second of minute             number        55
     S       fraction of second           number        978
    
     z       time zone                    text          Pacific Standard Time; PST
     Z       time zone offset/id          zone          -0800; -08:00; America/Los_Angeles
    
     '       escape for text              delimiter
     ''      single quote                 literal

     example: dateFmt("yyyy-MMM-dd", 20140401) produces 2014-Apr-01
     }}}
     @param fmtStr: a String specifying the desired format.
     @param yyyymmdds: an Int encoded with iso8601 date...
     @return string rep of this date
    */
    def iso8601DateFmt(fmtStr : String, yyyymmdds : Int): String = {
        val dateTime : DateTime = toDateTime(yyyymmdds)
        val fmt : DateTimeFormatter  = DateTimeFormat.forPattern(fmtStr);
        val str : String = fmt.print(dateTime);
        str
    }

    /** 
        Answer a String from the time in timestampStr argument formatted according to the format string
        argument presented.

        @param fmtStr - instructions on how to format the string. @see iso860DateFmt for format info 
        @param timestamp - the number of millisecs since epoch
        @return a Long representing the timestamp as millisecs since the epoch (1970 based)
     */
    def timestampFmt(fmtStr : String, timestamp : Long): String = {
        val dateTime : DateTime = new DateTime(timestamp);
        val fmt : DateTimeFormatter  = DateTimeFormat.forPattern(fmtStr);
        val str : String = fmt.print(dateTime);
        str
    }

    /** 
        Answer the number of millisecs from the epoch for the supplied string that presumably
        has the supplied format. If parse fails (IllegalArgumentException caught),
        the epoch is returned.

        @param fmtStr - instructions on how to parse the string. @see iso860DateFmt for format info 
        @param timestampStr - the string to parse
        @return a Long representing the timestamp as millisecs since the epoch (1970 based)
     */
    def timeStampFromStr(fmtStr : String, timestampStr : String): Long = {
        val fmt : DateTimeFormatter  = DateTimeFormat.forPattern(fmtStr);
	    try {
	        val dateTime : DateTime = fmt.parseDateTime(timestampStr);
	        val millis : Long = dateTime.getMillis
    		millis
	    } catch {
		    case iae:IllegalArgumentException => {
          val stackTrace = StackTrace.ThrowableTraceString(iae)
		    	logger.error(s"Unable to parse '$timestampStr' with pattern - '$fmtStr'")
          logger.error("\nStackTrace:"+stackTrace)
		    	0
		    }
	    }
    }
    
    /** 
        Answer the number of millisecs from the epoch for the supplied string that presumably
        has one of the supplied formats found in fmtStrArray. If parse fails (IllegalArgumentException caught),
        the epoch is returned.

        @param fmtStr - instructions on how to parse the string. @see iso860DateFmt for format info 
        @param timestampStr - the string to parse
        @return a Long representing the timestamp as millisecs since the epoch (1970 based)
     */
    def timeStampFromStr(fmtStrArray : Array[String], timestampStr : String): Long = {
        //val fmt : DateTimeFormatter  = DateTimeFormat.forPattern(fmtStr);
		val millis : Long = if (fmtStrArray != null && fmtStrArray.size > 0 && timestampStr != null) {
	        val parsers : Array[DateTimeParser] = fmtStrArray.map (fmt => {
	        	DateTimeFormat.forPattern(fmt).getParser()
	        })
	        val formatter : DateTimeFormatter = new DateTimeFormatterBuilder().append( null, parsers ).toFormatter();
		    try {
		        val dateTime : DateTime = formatter.parseDateTime(timestampStr)
		        val msecs : Long = dateTime.getMillis
		        msecs
		    } catch {
			    case iae:IllegalArgumentException => {
            val stackTrace = StackTrace.ThrowableTraceString(iae)
			    	logger.error(s"Unable to parse '$timestampStr' with any of the patterns - '${fmtStrArray.toString}'")
            logger.error("\nStackTrace:"+stackTrace)
			    	0
			    }
		    }
		} else {
			0
		}
        millis
    }
    
    
    /** 
        Answer the number of millisecs from the epoch for the supplied string that is in one of 
        the following <b>builtin</b> formats:
        
        """
        	 Pattern						Example
			 "yyyy-MM-dd HH:mm:ss:SSS"		2015-02-28 14:02:31:222
			 "yyyy-MM-dd HH:mm:ss"			2015-02-28 14:02:31
			 "MM/dd/yy HH:mm:ss"			04/15/15 23:59:59
			 "dd-MM-yyyy HH:mm:ss"			04/15/2015 23:59:59
			 "dd-MM-yyyy HH:mm:ss:SSS"		04/15/15 23:59:59:999
			 "dd-MMM-yyyy HH:mm:ss"			15-Apr-2015 23:59:59
			 "dd-MM-yyyy HH:mm:ss:SSS"		15-04-2015 23:59:59:999
        """
        
        Should your timestamp not be one of these, use the more general forms of this function that allows
        you to supply one or more formats.  @see timeStampFromStr(fmtStr : String, timestampStr : String): Long
        and @see timeStampFromStr(fmtStrArray : Array[String], timestampStr : String): Long for details.

        @param fmtStr - instructions on how to parse the string. @see iso860DateFmt for format info 
        @param timestampStr - the string to parse
        @return a Long representing the timestamp as millisecs since the epoch (1970 based)
     */
    def timeStampFromString(timestampStr : String): Long = {
        DateTimeHelpers.timeStampFromString(timestampStr)   /** use the builtin helpers in PmmlRuntimeDecls */
    }
    
    /** 
        Answer the number of millisecs from the epoch for the supplied date string that is in one of 
        the following <b>builtin</b> formats:
        
        """
        	 Pattern						Example
        	 yyyy-MM-dd						2015-04-15
			 yyyy-MMM-dd					2015-Apr-15
			 MM/dd/yy						04/15/15
			 MMM-dd-yy						Apr-15-15
			 dd-MM-yyyy						15-04-2015
			 dd-MMM-yyyy					15-Apr-2015
        """
        
        Should your timestamp not be one of these, use the more general forms of this function that allows
        you to supply one or more formats.  @see timeStampFromStr(fmtStr : String, timestampStr : String): Long
        and @see timeStampFromStr(fmtStrArray : Array[String], timestampStr : String): Long and 
        @see dateFromStr(fmtStr : String, timestampStr : String): Long for details.

        @param fmtStr - instructions on how to parse the string. @see iso860DateFmt for format info 
        @param timestampStr - the string to parse
        @return a Long representing the timestamp as millisecs since the epoch (1970 based)
     */
    def dateFromString(timestampStr : String): Long = {
        DateTimeHelpers.dateFromString(timestampStr)  /** use the builtin helpers in PmmlRuntimeDecls */
    }
    
    /** 
        Answer the number of millisecs from the epoch for the supplied time string that is in one of 
        the following <b>builtin</b> formats:
        
        """
        	 Pattern						Example
			 "HH:mm:ss"						23:59:59
			 "HH:mm:ss:SSS"					23:59:59:999
			 "h:mm:ss"						12:45:59
			 "h:mm:ss aa"					12:45:59 AM
        """
        
        Should your time not be formatted like one of these, use the more general forms of this function that allows
        you to supply one or more formats. @see timeStampFromStr(fmtStr : String, timestampStr : String): Long
        and @see timeStampFromStr(fmtStrArray : Array[String], timestampStr : String): Long for details. @see 
        timeFromStr(fmtStr : String, timestampStr : String): Long ... also available.

        @param fmtStr - instructions on how to parse the string. @see iso860DateFmt for format info 
        @param timeStr - the string to parse
        @return a Long representing the timestamp as millisecs since the epoch (1970 based)
     */
    def timeFromString(timeStr : String): Long = {
        DateTimeHelpers.timeFromString(timeStr)   /** use the builtin helpers in PmmlRuntimeDecls */
    }
    
    
    /** 
        Answer the number of millisecs from the epoch for the supplied date portion in the supplied 
        string that presumably has the supplied format. If parse fails (IllegalArgumentException caught),
        the epoch is returned.

        @param fmtStr - instructions on how to parse the string. @see iso860DateFmt for format info 
        @param timestampStr - the string to parse
        @return a Long representing the date as millisecs since the epoch (1970 based)
     */
    def dateFromStr(fmtStr : String, timestampStr : String): Long = {
        val fmt : DateTimeFormatter  = DateTimeFormat.forPattern(fmtStr);
	    try {
	        val dateTime : DateTime = fmt.parseDateTime(timestampStr);
	        val dt : DateTime = new DateTime(dateTime.getYear, dateTime.getMonthOfYear, dateTime.getDayOfMonth, 0, 0)
	        val millis : Long = dt.getMillis
	        millis
	    } catch {
		    case iae:IllegalArgumentException => {
          val stackTrace = StackTrace.ThrowableTraceString(iae)
		    	logger.error(s"Unable to parse '$timestampStr' with pattern - '$fmtStr'")
          logger.error("\nStackTrace:"+stackTrace)
		    	0
		    }
	    }
    }

    def javaEpoch : LocalDateTime = {
        new LocalDateTime(1970, 1, 1, 0, 0)
    }

    /** 
        Answer the number of millisecs from the epoch for the supplied string that presumably
        has the supplied format and represents some wall time.

        @param fmtStr - instructions on how to parse the string. @see iso860DateFmt for format info 
        @param timestampStr - the string to parse
        @return a Long representing the timestamp as millisecs since the epoch (1970 based)
     */
    def timeFromStr(fmtStr : String, timestampStr : String): Long = {
        dateSecondsSinceMidnight(fmtStr, timestampStr)
    }

    /** 
        Answer the number of seconds since midnight for the supplied time (HH:mm:ss:SSS) portion given in the supplied format.

        @param fmtStr - instructions on how to parse the string. @see ISO860DateFmt for format info 
        @param timestampStr - the string to parse
        @return a Long representing the seconds since midnight for the time portion of the supplied timestamp
     */

    def dateSecondsSinceMidnight(fmtStr : String, timestampStr : String): Long = {
        val fmt : DateTimeFormatter  = DateTimeFormat.forPattern(fmtStr);
	    try {
	        val dateTime : DateTime = fmt.parseDateTime(timestampStr);
	        val hrOfDay : Int = dateTime.getHourOfDay
	        val minOfHr : Int = dateTime.getMinuteOfHour
	        val minOfDay : Int = dateTime.getMinuteOfDay
	        val secOfMin : Int = dateTime.getSecondOfMinute
	        val secOfDay : Int = dateTime.getSecondOfDay
	        val tm : LocalDateTime = new LocalDateTime(1970, 1, 1, hrOfDay, minOfHr, secOfMin, dateTime.getMillisOfSecond)
	        val seconds : Long = new Duration(javaEpoch.toDateTime.getMillis, tm.toDateTime.getMillis).getStandardSeconds()
	        seconds
	    } catch {
		    case iae:IllegalArgumentException => {
          val stackTrace = StackTrace.ThrowableTraceString(iae)
		    	logger.error(s"Unable to parse '$timestampStr' with pattern - '$fmtStr'")
          logger.error("\nStackTrace:"+stackTrace)
		    	0
		    }
	    }
    }

    /** 
        Answer the number of seconds since midnight for the supplied time (HH:mm:ss:SSS) portion given in the supplied Long

        @param timestamp - the string to parse
        @return a Long representing the seconds since midnight for the time portion of the supplied timestamp
     */

    def dateSecondsSinceMidnight(timestamp : Long): Long = {
        val dateTime : DateTime = new DateTime(timestamp);
        val hrOfDay : Int = dateTime.getHourOfDay
        val minOfHr : Int = dateTime.getMinuteOfHour
        val minOfDay : Int = dateTime.getMinuteOfDay
        val secOfMin : Int = dateTime.getSecondOfMinute
        val secOfDay : Int = dateTime.getSecondOfDay
        val tm : LocalDateTime = new LocalDateTime(1970, 1, 1, hrOfDay, minOfHr, secOfMin, dateTime.getMillisOfSecond)
        val seconds : Long = new Duration(javaEpoch.toDateTime.getMillis, tm.toDateTime.getMillis).getStandardSeconds()
        seconds
    }

    val milliSecondsInSecond : Long = 1000
    val milliSecondsInMinute : Long = 1000 * 60
    val milliSecondsInHour   : Long = 1000 * 60 * 60 
    val milliSecondsInDay    : Long = 1000 * 60 * 60 * 24
    val milliSecondsInWeek   : Long = 1000 * 60 * 60 * 24 * 7

    /**
        Answer the number of milliseconds between the two time expressions (millisecs since Epoch)
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of milliseconds between the timestamps
     */
    def millisecsBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val diff : Long = abs(time1 - time2) + (if (inclusive) 1 else 0)
        diff
    }

    /**
        Answer the number of seconds between the two time expressions (millisecs since Epoch)
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of seconds between the timestamps
     */
    def secondsBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val diff : Long = abs(time1 - time2) + (if (inclusive) 1 else 0)
        val nominalSeconds : Long = diff / milliSecondsInSecond
        val rem : Long = diff % milliSecondsInSecond
        val seconds : Long = nominalSeconds + (if (rem >= (milliSecondsInSecond / 2)) 1 else 0)
        seconds
    }

    /**
        Answer the number of minutes between the two time expressions (millisecs since Epoch).  Partial hours are rounded
        to the nearest integer value.
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of minutes between the timestamps
     */
    def minutesBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val diff : Long = abs(time1 - time2) + (if (inclusive) 1 else 0)
        val nominalMinutes : Long = diff / milliSecondsInMinute
        val rem : Long = diff % milliSecondsInMinute
        val minutes : Long = nominalMinutes + (if (rem >= (milliSecondsInMinute / 2)) 1 else 0)
        minutes
    }

    /**
        Answer the number of hours between the two time expressions (millisecs since Epoch).  Partial hours are rounded
        to the nearest integer value.
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of hours between the timestamps
     */
    def hoursBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val diff : Long = abs(time1 - time2) + (if (inclusive) 1 else 0)
        val nominalHours : Long = diff / milliSecondsInHour
        val rem : Long = diff % milliSecondsInHour
        val hours : Long = nominalHours + (if (rem >= (milliSecondsInHour / 2)) 1 else 0)
        hours
    }

    /**
        Answer the number of days between the two time expressions (millisecs since Epoch).  Partial days are rounded
        to the nearest integer value.
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of days between the timestamps
     */
    def daysBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val diff : Long = abs(time1 - time2) + (if (inclusive) 1 else 0)
        val nominalDays : Long = diff / milliSecondsInDay
        val rem : Long = diff % milliSecondsInDay
        val days : Long = nominalDays + (if (rem >= (milliSecondsInDay / 2)) 1 else 0)
        days
    }

    /**
        Answer the number of weeks between the two time expressions (millisecs since Epoch).  Partial weeks are rounded
        to the nearest integer value. 7 day week assumed.
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of weeks between the timestamps
     */
    def weeksBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val diff : Long = abs(time1 - time2) + (if (inclusive) 1 else 0)
        val nominalWeeks : Long = diff / milliSecondsInWeek
        val rem : Long = diff % milliSecondsInWeek
        val weeks : Long = nominalWeeks + (if (rem >= (milliSecondsInWeek / 2)) 1 else 0)
        weeks
    }

    /**
        Answer the number of months between the two time expressions (millisecs since Epoch).  
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of weeks between the timestamps
     */
    def monthsBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val t1 : Long = if (time1 <= time2) time1 else time2
        val t2 : Long = if (time1 <= time2) time2 else time1
        val dt1 : DateTime = new DateTime(t1)
        val dt2 : DateTime = new DateTime(t2)
        
        val m : Months = Months.monthsBetween(dt1, dt2)
        val months : Long = m.getMonths
        months
    }

    /**
        Answer the number of years between the two time expressions (millisecs since Epoch).  
        @param time1 : a timestamp
        @param time2 : a timestamp
        @param inclusive (optional default is false) : when true +1 to the difference
        @return number of years between the timestamps
     */
    def yearsBetween(time1 : Long, time2 : Long, inclusive : Boolean = false) : Long = {
        val t1 : Long = if (time1 <= time2) time1 else time2
        val t2 : Long = if (time1 <= time2) time2 else time1
        val dt1 : DateTime = new DateTime(t1)
        val dt2 : DateTime = new DateTime(t2)
        
        val y : Years = Years.yearsBetween(dt1, dt2)
        val years : Long = y.getYears
        years
    }






  /** 
      Create an Array of String tuples from the arguments where the 'left' string
      is _._1 and the elements of the 'right' array are _._2 in the tuple array returned.
      @param left the first tuple in each array element to be returned
      @param right each element becomes the second element in the returned array, each matched with 'left'
      @return an Array of String tuples
   */
  def MakePairs(left: String, right: Array[String]): Array[(String, String)] = {
    if (left == null || right == null || right.size == 0 || left.length == 0)
      return new Array[(String, String)](0)
    right.filter(v => v != null && v.length > 0).map(v => (left, v))
  }

  /** 
      Create an Array of String tuples from the arguments where the 'left' string
      is one of the elements and the 'right' Array elements are each the other one of the pair.  This is similar to 
      MakePairs except the left and right candidates are compared and the lesser value becomes the 
      _._1 and the larger _._2.
      @param left the first tuple in each array element to be returned
      @param right each element becomes the second element in the returned array, each matched with 'left'
      @return an Array of String tuples
   */
  def MakeOrderedPairs(left: String, right: Array[String]): Array[(String, String)] = {
    if (left == null || right == null || right.size == 0 || left.length == 0)
      return new Array[(String, String)](0)
    right.filter(v => v != null && v.length > 0).map(v => {
      if (left.compareTo(v) > 0)
        (v, left)
      else
        (left, v)
    })
  }

  /** 
      Create an Array of String tuples from the arguments where the 'left' string
      is one of the elements and the 'right' ArrayBuffer elements are each the other one of the pair.  
      This is similar to MakePairs except the left and right candidates are compared and the 
      lesser value becomes the _._1 and the larger _._2.
      @param left the first tuple in each array element to be returned
      @param right each element becomes the second element in the returned array, each matched with 'left'
      @return an Array of String tuples
   */
  def MakeOrderedPairs(left: String, right: ArrayBuffer[String]): Array[(String, String)] = {
    if (left == null || right == null || right.size == 0 || left.length == 0)
      return new Array[(String, String)](0)
    right.filter(v => v != null && v.length > 0).map(v => {
      if (left.compareTo(v) > 0)
        (v, left)
      else
        (left, v)
    }).toArray
  }

  /** 
      Answer an array that takes an Array of String tuples and returns an Array of String utilizing
      the supplied separator as the delimiter between the tuple elements.
      @param arr an Array of String tuples
      @return an Array of String delimited by the supplied separator
   */
  def MakeStrings(arr: Array[(String, String)], separator: String): Array[String] = {
    if (arr == null || arr.size == 0)
      return new Array[String](0)
    arr.filter(v => v != null).map(v => ("(" + v._1 + separator + v._2 + ")"))
  }

  /** 
      Coerce the supplied array to a set.
      @param arr an Array[T]
      @return an Set[T]
   */
  def ToSet[T: ClassTag](arr: Array[T]): Set[T] = {
    if (arr == null || arr.size == 0)
      return Set[T]().toSet
    arr.toSet
  }

  /** 
      Coerce the supplied array buffer to a set.
      @param arr an ArrayBuffer[T]
      @return an Set[T]
   */
  def ToSet[T: ClassTag](arr: ArrayBuffer[T]): Set[T] = {
    if (arr == null || arr.size == 0)
      return Set[T]().toSet
    arr.toSet
  }

  /** 
      Coerce the supplied queue to a set.
      @param q a Queue[T]
      @return n Set[T]
   */
  def ToSet[T: ClassTag](q: Queue[T]): Set[T] = {
    if (q == null || q.size == 0)
      return Set[T]().toSet
    q.toSet
  }

  /** 
      Coerce the supplied List to a set.
      @param l a List[T]
      @return n Set[T]
   */
  def ToSet[T: ClassTag](l: List[T]): Set[T] = {
    if (l == null || l.size == 0)
      return Set[T]().toSet
    l.toSet
  }

  /** 
      Coerce the supplied mutable set to an Array.
      @param set a scala.collection.mutable.Set[T]
      @return an Array[T]
   */
  def ToArray[T: ClassTag](set: MutableSet[T]): Array[T] = {
    if (set == null || set.size == 0)
      return Array[T]()
    set.toArray
  }

  /** 
      Coerce the supplied set to an Array.
      @param set a scala.collection.immutable.Set[T]
      @return an Array[T]
   */
  def ToArray[T: ClassTag](set: Set[T]): Array[T] = {
    if (set == null || set.size == 0)
      return Array[T]()
    set.toArray
  }


  /** 
      Coerce the supplied array buffer to an Array.
      @param arr a scala.collection.mutable.ArrayBuffer[T]
      @return an Array[T]
   */
  def ToArray[T: ClassTag](arr: ArrayBuffer[T]): Array[T] = {
    if (arr == null || arr.size == 0)
      return Array[T]()
    arr.toArray
  }

  /** 
      Coerce the supplied array to an Array.
      @param arr a scala.Array[T]
      @return an Array[T]
   */
  def ToArray[T: ClassTag](arr: Array[T]): Array[T] = {
    if (arr == null || arr.size == 0)
      return Array[T]()
    arr.toArray
  }

  /** 
      Coerce the supplied SortedSet to an Array.
      @param set a scala.collection.immutable.SortedSet[T]
      @return an Array[T]
   */
  def ToArray[T: ClassTag](set: SortedSet[T]): Array[T] = {
    if (set == null || set.size == 0)
      return Array[T]()
    set.toArray
  }

  /** 
      Coerce the supplied TreeSet to an Array.
      @param ts a scala.collection.mutable.TreeSet[T]
      @return an Array[T]
   */
  def ToArray[T: ClassTag](ts: TreeSet[T]): Array[T] = {
    if (ts == null || ts.size == 0)
      return Array[T]()
    ts.toArray
  }

  /** 
      Coerce the supplied List to an Array.
      @param l a scala.collection.mutable.List[T]
      @return an Array[T]
   */
  def ToArray[T: ClassTag](l: List[T]): Array[T] = {
    if (l == null || l.size == 0)
      return Array[T]()
    l.toArray
  }

  /** 
      Coerce the supplied Queue to an Array.
      @param q a scala.collection.mutable.Queue[T]
      @return an Array[T]
   */
  def ToArray[T: ClassTag](q: Queue[T]): Array[T] = {
    if (q == null || q.size == 0)
      return Array[T]()
    q.toArray
  }


  /**
   *
   * Suppress stack to array coercion until Stack based types are supported in the MdMgr ....
   *
   * def ToArray[T : ClassTag](stack: Stack[T]): Array[T] = {
   * if (stack == null || stack.size == 0)
   * return Array[T]()
   * stack.toArray
   * }
   *
   * def ToArray(stack: Stack[Any]): Array[Any] = {
   * if (stack == null || stack.size == 0)
   * return Array[Any]()
   * stack.toArray
   * }
   */

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple1[Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple2[Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple doesn't contain appropriate numeric values, a 0 is returned at that position */
  def ToArrayOfFloat(tuple: Tuple2[Any, Any]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => if (itm.isInstanceOf[Float] || itm.isInstanceOf[Int] || itm.isInstanceOf[Long]) itm.asInstanceOf[Float] else 0)
    fArray
  }

  /** if the tuple doesn't contain appropriate numeric values, a 0 is returned at that position */
  def ToArrayOfDouble(tuple: Tuple2[Any, Any]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => if (itm.isInstanceOf[Double] || itm.isInstanceOf[Float] || itm.isInstanceOf[Int] || itm.isInstanceOf[Long]) itm.asInstanceOf[Double] else 0)
    dArray
  }

  /** if the tuple doesn't contain appropriate numeric values, a 0 is returned at that position */
  def ToArrayOfInt(tuple: Tuple2[Any, Any]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
     val iArray: Array[Int] = arr.map(itm => if (itm.isInstanceOf[Int]) itm.asInstanceOf[Int] else 0)
    iArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple2[Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple3[Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple doesn't contain appropriate numeric values, a 0 is returned at that position */
  def ToArrayOfFloat(tuple: Tuple3[Any, Any, Any]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => if (itm.isInstanceOf[Float] || itm.isInstanceOf[Int] || itm.isInstanceOf[Long]) itm.asInstanceOf[Float] else 0)
    fArray
  }

  /** if the tuple doesn't contain appropriate numeric values, a 0 is returned at that position */
  def ToArrayOfDouble(tuple: Tuple3[Any, Any, Any]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => if (itm.isInstanceOf[Double] || itm.isInstanceOf[Float] || itm.isInstanceOf[Int] || itm.isInstanceOf[Long]) itm.asInstanceOf[Double] else 0)
    dArray
  }

  /** if the tuple doesn't contain appropriate numeric values, a 0 is returned at that position */
  def ToArrayOfInt(tuple: Tuple3[Any, Any, Any]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => if (itm.isInstanceOf[Int]) itm.asInstanceOf[Int] else 0)
    iArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple3[Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple4[Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple doesn't contain appropriate numeric values, a 0 is returned at that position */
  def ToArrayOfFloat(tuple: Tuple4[Any, Any, Any, Any]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => if (itm.isInstanceOf[Float] || itm.isInstanceOf[Int] || itm.isInstanceOf[Long]) itm.asInstanceOf[Float] else 0)
    fArray
  }

  /** if the tuple doesn't contain appropriate numeric values, a 0 is returned at that position */
  def ToArrayOfDouble(tuple: Tuple4[Any, Any, Any, Any]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => if (itm.isInstanceOf[Double] || itm.isInstanceOf[Float] || itm.isInstanceOf[Int] || itm.isInstanceOf[Long]) itm.asInstanceOf[Double] else 0)
    dArray
  }

  /** if the tuple doesn't contain appropriate numeric values, a 0 is returned at that position */
  def ToArrayOfInt(tuple: Tuple4[Any, Any, Any, Any]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => if (itm.isInstanceOf[Int]) itm.asInstanceOf[Int] else 0)
    iArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple4[Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple5[Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple doesn't contain appropriate numeric values, a 0 is returned at that position */
  def ToArrayOfFloat(tuple: Tuple5[Any, Any, Any, Any, Any]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => if (itm.isInstanceOf[Float] || itm.isInstanceOf[Int] || itm.isInstanceOf[Long]) itm.asInstanceOf[Float] else 0)
    fArray
  }

  /** if the tuple doesn't contain appropriate numeric values, a 0 is returned at that position */
  def ToArrayOfDouble(tuple: Tuple5[Any, Any, Any, Any, Any]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => if (itm.isInstanceOf[Double] || itm.isInstanceOf[Float] || itm.isInstanceOf[Int] || itm.isInstanceOf[Long]) itm.asInstanceOf[Double] else 0)
    dArray
  }

  /** if the tuple doesn't contain appropriate numeric values, a 0 is returned at that position */
  def ToArrayOfInt(tuple: Tuple5[Any, Any, Any, Any, Any]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => if (itm.isInstanceOf[Int]) itm.asInstanceOf[Int] else 0)
    iArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple5[Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple6[Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple doesn't contain appropriate numeric values, a 0 is returned at that position */
  def ToArrayOfFloat(tuple: Tuple6[Any, Any, Any, Any, Any, Any]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => if (itm.isInstanceOf[Float] || itm.isInstanceOf[Int] || itm.isInstanceOf[Long]) itm.asInstanceOf[Float] else 0)
    fArray
  }

  /** if the tuple doesn't contain appropriate numeric values, a 0 is returned at that position */
  def ToArrayOfDouble(tuple: Tuple6[Any, Any, Any, Any, Any, Any]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => if (itm.isInstanceOf[Double] || itm.isInstanceOf[Float] || itm.isInstanceOf[Int] || itm.isInstanceOf[Long]) itm.asInstanceOf[Double] else 0)
    dArray
  }

  /** if the tuple doesn't contain appropriate numeric values, a 0 is returned at that position */
  def ToArrayOfInt(tuple: Tuple6[Any, Any, Any, Any, Any, Any]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => if (itm.isInstanceOf[Int]) itm.asInstanceOf[Int] else 0)
    iArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple6[Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple7[Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple7[Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple8[Any, Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple8[Any, Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple9[Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple9[Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple10[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple10[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple11[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple11[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple12[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple12[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple13[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple13[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple14[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple14[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple15[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple15[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple16[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple16[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple17[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple17[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple18[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple18[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple19[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple19[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple20[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple20[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple21[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple21[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Any] 
      @param tuple TupleN[Any]
      @return Array[Any]
   */
  def ToArray(tuple: Tuple22[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Any] = {
    tuple.productIterator.toArray
  }

  /** if the tuple is not boolean, a false is returned at that position */
  def ToArrayOfBoolean(tuple: Tuple22[Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any]): Array[Boolean] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val bArray: Array[Boolean] = arr.map(itm => if (itm.isInstanceOf[Boolean]) itm.asInstanceOf[Boolean] else false)
    bArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple1[Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple2[Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple3[Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple4[Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple5[Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple6[Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple7[Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple8[Int, Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple9[Int, Int, Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple10[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple11[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple12[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple13[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple14[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple15[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple16[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple17[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple18[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple19[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple20[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple21[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Int] 
      @param tuple TupleN[Int]
      @return Array[Int]
   */
  def ToArray(tuple: Tuple22[Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int]): Array[Int] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val iArray: Array[Int] = arr.map(itm => itm.asInstanceOf[Int])
    iArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple1[Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple2[Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple3[Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple4[Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple5[Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple6[Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple7[Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple8[Float, Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple9[Float, Float, Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple10[Float, Float, Float, Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple11[Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple12[Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple13[Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple14[Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple15[Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple16[Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple17[Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple18[Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple19[Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple20[Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple21[Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Float] 
      @param tuple TupleN[Float]
      @return Array[Float]
   */
  def ToArray(tuple: Tuple22[Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float, Float]): Array[Float] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val fArray: Array[Float] = arr.map(itm => itm.asInstanceOf[Float])
    fArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple1[Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple2[Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple3[Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple4[Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple5[Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple6[Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple7[Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple8[Double, Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple9[Double, Double, Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple10[Double, Double, Double, Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple11[Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple12[Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple13[Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple14[Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple15[Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple16[Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple17[Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple18[Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple19[Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple20[Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  def ToArray(tuple: Tuple21[Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the supplied tuple to an Array[Double] 
      @param tuple TupleN[Double]
      @return Array[Double]
   */
  def ToArray(tuple: Tuple22[Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double]): Array[Double] = {
    val arr: Array[Any] = tuple.productIterator.toArray
    val dArray: Array[Double] = arr.map(itm => itm.asInstanceOf[Double])
    dArray
  }

  /** 
      Coerce the elements in the MutableSet of Tuple2 to an Map
      @param set scala.collection.mutable.Set[(T,U)]
      @return Map[T, U]
   */
  def ToMap[T: ClassTag, U: ClassTag](set: MutableSet[(T, U)]): Map[T, U] = {
    if (set == null || set.size == 0)
      return Map[T, U]()
    set.toMap
  }

  /** 
      Coerce the elements in the Set of Tuple2 to an Map
      @param set scala.collection.immutable.Set[(T,U)]
      @return Map[T, U]
   */
  def ToMap[T: ClassTag, U: ClassTag](set: Set[(T, U)]): Map[T, U] = {
    if (set == null || set.size == 0)
      return Map[T, U]()
    set.toMap
  }

  /** 
      Coerce the elements in the Set of Tuple2 to an Map
      @param set scala.collection.mutable.Set[(Any, Any)]
      @return Map[Any, Any]
   */
  def ToMap(set: MutableSet[(Any, Any)]): Map[Any, Any] = {
    if (set == null || set.size == 0)
      return Map[Any, Any]()
    set.toMap
  }

  /** 
      Coerce the elements in the Set of Tuple2 to an Map
      @param set scala.collection.immutable.Set[(Any, Any)]
      @return Map[Any, Any]
   */
  def ToMap(set: Set[(Any, Any)]): Map[Any, Any] = {
    if (set == null || set.size == 0)
      return Map[Any, Any]()
    set.toMap
  }

  /** 
      Coerce the elements in the ArrayBuffer of Tuple2 to an Map
      @param set scala.collection.mutable.ArrayBuffer[(T, U)]
      @return Map[T, U]
   */
  def ToMap[T: ClassTag, U: ClassTag](arr: ArrayBuffer[(T, U)]): Map[T, U] = {
    if (arr == null || arr.size == 0)
      return Map[T, U]()
    arr.toMap
  }

  /** 
      Coerce the elements in the ArrayBuffer of Tuple2 to an Map
      @param set scala.collection.mutable.ArrayBuffer[(Any, Any)]
      @return Map[Any, Any]
   */
  def ToMap(arr: ArrayBuffer[(Any, Any)]): Map[Any, Any] = {
    if (arr == null || arr.size == 0)
      return Map[Any, Any]()
    arr.toMap
  }

  /** 
      Coerce the elements in the Array of Tuple2 to an Map
      @param set scala.Array[(Any, Any)]
      @return Map[Any, Any]
   */
  def ToMap[T: ClassTag, U: ClassTag](arr: Array[(T, U)]): Map[T, U] = {
    if (arr == null || arr.size == 0)
      return Map[T, U]()
    arr.toMap
  }

  /** 
      Coerce the elements in the Array of Tuple2 to an Map
      @param set scala.Array[(T, U)]
      @return Map[T, U]
   */
  def ToMap(arr: Array[(Any, Any)]): Map[Any, Any] = {
    if (arr == null || arr.size == 0)
      return Map[Any, Any]()
    arr.toMap
  }

  /** 
      Coerce the elements in the SortedSet of Tuple2 to an Map
      @param set scala.collection.mutable.SortedSet[(T, U)]
      @return Map[T, U]
   */
  def ToMap[T: ClassTag, U: ClassTag](set: SortedSet[(T, U)]): Map[T, U] = {
    if (set == null || set.size == 0)
      return Map[T, U]()
    set.toMap
  }

  /** 
      Coerce the elements in the SortedSet of Tuple2 to an Map
      @param set scala.collection.mutable.SortedSet[(Any, Any)]
      @return Map[Any, Any]
   */
  def ToMap(set: SortedSet[(Any, Any)]): Map[Any, Any] = {
    if (set == null || set.size == 0)
      return Map[Any, Any]()
    set.toMap
  }

  /** 
      Coerce the elements in the TreeSet of Tuple2 to an Map
      @param set scala.collection.mutable.TreeSet[(T, U)]
      @return Map[T, U]
   */
  def ToMap[T: ClassTag, U: ClassTag](ts: TreeSet[(T, U)]): Map[T, U] = {
    if (ts == null || ts.size == 0)
      return Map[T, U]()
    ts.toMap
  }

  /** 
      Coerce the elements in the TreeSet of Tuple2 to an Map
      @param set scala.collection.mutable.TreeSet[(Any, Any)]
      @return Map[Any, Any]
   */
  def ToMap(ts: TreeSet[(Any, Any)]): Map[Any, Any] = {
    if (ts == null || ts.size == 0)
      return Map[Any, Any]()
    ts.toMap
  }

  /** 
      Coerce the elements in the List of Tuple2 to an Map
      @param set scala.collection.immutable.List[(T, U)]
      @return Map[T, U]
   */
  def ToMap[T: ClassTag, U: ClassTag](l: List[(T, U)]): Map[T, U] = {
    if (l == null || l.size == 0)
      return Map[T, U]()
    l.toMap
  }

  /** 
      Coerce the elements in the List of Tuple2 to an Map
      @param set scala.collection.immutable.List[(Any, Any)]
      @return Map[Any, Any]
   */
  def ToMap(l: List[(Any, Any)]): Map[Any, Any] = {
    if (l == null || l.size == 0)
      return Map[Any, Any]()
    l.toMap
  }

  /** 
      Coerce the elements in the Queue of Tuple2 to an Map
      @param set scala.collection.mutable.Queue[(T, U)]
      @return Map[T, U]
   */
  def ToMap[T: ClassTag, U: ClassTag](q: Queue[(T, U)]): Map[T, U] = {
    if (q == null || q.size == 0)
      return Map[T, U]()
    q.toMap
  }

  /** 
      Coerce the elements in the Queue of Tuple2 to an Map
      @param set scala.collection.mutable.Queue[(Any, Any)]
      @return Map[Any, Any]
   */
  def ToMap(q: Queue[(Any, Any)]): Map[Any, Any] = {
    if (q == null || q.size == 0)
      return Map[Any, Any]()
    q.toMap
  }

  /**
   * Suppress Stack type functions until MdMgr supports them properly
   * def ToMap[T : ClassTag, U : ClassTag](stack: Stack[(T,U)]): Map[T,U] = {
   * if (stack == null || stack.size == 0)
   * return Map[T,U]()
   * stack.toMap
   * }
   *
   * def ToMap(stack: Stack[(Any,Any)]): Map[Any,Any] = {
   * if (stack == null || stack.size == 0)
   * return Map[Any,Any]()
   * stack.toMap
   * }
   */

  /** 
      Zip the two arrays together.  The first array's size must be &gt; 0 and &gte; other's size
      @param receiver an Array[T]
      @param other an Array[U]
      @return Array[(T, U)]
   */
  def Zip[T: ClassTag, U: ClassTag](receiver: Array[T], other: Array[U]): Array[(T, U)] = {
    if (receiver == null || receiver.size == 0) {
      return Array[(T, U)]()
    }
    receiver.zip(other)
  }

  /** 
      Zip the two array buffers together.  The first array buffer's size must be &gt; 0 and &gte; other's size
      @param receiver an ArrayBuffer[T]
      @param other an ArrayBuffer[U]
      @return ArrayBuffer[(T, U)]
   */
  def Zip[T: ClassTag, U: ClassTag](receiver: ArrayBuffer[T], other: ArrayBuffer[U]): ArrayBuffer[(T, U)] = {
    if (receiver == null || receiver.size == 0) {
      return ArrayBuffer[(T, U)]()
    }
    receiver.zip(other)
  }

  /** 
      Zip the two Lists together.  The first List's size must be &gt; 0 and &gte; other's size
      @param receiver a List[T]
      @param other a List[U]
      @return List[(T, U)]
   */
  def Zip[T: ClassTag, U: ClassTag](receiver: List[T], other: List[U]): List[(T, U)] = {
    if (receiver == null || receiver.size == 0) {
      return List[(T, U)]()
    }
    receiver.zip(other)
  }

  /** 
      Zip the two Lists together.  The first List's size must be &gt; 0 and &gte; other's size
      @param receiver a List[T]
      @param other a List[U]
      @return List[(T, U)]
   */
  def Zip[T: ClassTag, U: ClassTag](receiver: Queue[T], other: Queue[U]): Queue[(T, U)] = {
    if (receiver == null || receiver.size == 0) {
      return Queue[(T, U)]()
    }
    receiver.zip(other)
  }

  /** 
      Zip the two Sets together.  The first Set's size must be &gt; 0 and &gte; other's size
      @param receiver a Set[T]
      @param other a Set[U]
      @return Set[(T, U)]

      Note: Zipping mutable.Set and/or immutable.Set is typically not a good idea unless the pairing
   *    done absolutely does not matter.  Use SortedSet or TreeSet for predictable pairings.
   */
  def Zip[T: ClassTag, U: ClassTag](receiver: Set[T], other: Set[U]): Set[(T, U)] = {
    if (receiver == null || receiver.size == 0) {
      return Set[(T, U)]()
    }
    receiver.zip(other)
  }

  /** 
      Zip the two Sets together.  The first Set's size must be &gt; 0 and &gte; other's size
      @param receiver a Set[T]
      @param other a Set[U]
      @return Set[(T, U)]

      Note: Zipping mutable.Set and/or immutable.Set is typically not a good idea unless the pairing
   *    done absolutely does not matter.  Use SortedSet or TreeSet for predictable pairings.
   */
  def Zip[T: ClassTag, U: ClassTag](receiver: MutableSet[T], other: MutableSet[U]): MutableSet[(T, U)] = {
    if (receiver == null || receiver.size == 0) {
      return MutableSet[(T, U)]()
    }
    receiver.zip(other)
  }

  /** 
      Zip the two SortedSets together.  The first Set's size must be &gt; 0 and &gte; other's size
      @param receiver a SortedSet[T]
      @param other a SortedSet[U]
      @return SortedSet[(T, U)]
   */
  def Zip[T: ClassTag, U: ClassTag](receiver: SortedSet[T], other: SortedSet[U])(implicit cmp: Ordering[(T, U)]): SortedSet[(T, U)] = {
    if (receiver == null || receiver.size == 0) {
      return SortedSet[(T, U)]()
    }
    receiver.zip(other)
  }

  /** 
      Zip the two TreeSets together.  The first Set's size must be &gt; 0 and &gte; other's size
      @param receiver a TreeSet[T]
      @param other a TreeSet[U]
      @return TreeSet[(T, U)]
   */
  def Zip[T: ClassTag, U: ClassTag](receiver: TreeSet[T], other: TreeSet[U])(implicit cmp: Ordering[(T, U)]): TreeSet[(T, U)] = {
    if (receiver == null || receiver.size == 0) {
      return TreeSet[(T, U)]()
    }
    receiver.zip(other)
  }


  /** 
      Answer the supplied map's keys as an array.
      @param receiver a scala.collection.mutable.Map[T,U]
      @return Array[T]
   */
  def MapKeys[T: ClassTag, U: ClassTag](receiver: MutableMap[T, U]): Array[T] = {
    receiver.keys.toArray
  }

  /** 
      Answer the supplied map's keys as an array.
      @param receiver a scala.collection.immutable.Map[T,U]
      @return Array[T]
   */
  def MapKeys[T: ClassTag, U: ClassTag](receiver: Map[T, U]): Array[T] = {
    receiver.keys.toArray
  } 

  /** 
      Answer the supplied map's keys as an array.
      @param receiver a scala.collection.immutable.Map[Any,Any]
      @return Array[Any]
  
  def MapKeys(receiver: MutableMap[Any, Any]): Array[Any] = {
    receiver.keys.toArray
  } */

  /** 
      Answer the supplied map's keys as an array.
      @param receiver a scala.collection.immutable.Map[Any,Any]
      @return Array[Any]
   
  def MapKeys(receiver: Map[Any, Any]): Array[Any] = {
    receiver.keys.toArray
  }*/

  /** 
      Answer the supplied map's values as an array.
      @param receiver a scala.collection.mutable.Map[T,U]
      @return Array[T]
   */
  def MapValues[T: ClassTag, U: ClassTag](receiver: MutableMap[T, U]): Array[U] = {
    receiver.values.toArray
  }

  /** 
      Answer the supplied map's values as an array.
      @param receiver a scala.collection.mutable.Map[T,U]
      @return Array[T]
   */
  def MapValues[T: ClassTag, U: ClassTag](receiver: Map[T, U]): Array[U] = {
    receiver.values.toArray
  }  

  /** 
      Answer the supplied map's values as an array.
      @param receiver a scala.collection.mutable.Map[Any,Any]
      @return Array[Any]
   */
  def MapValues(receiver: MutableMap[Any, Any]): Array[Any] = {
    receiver.values.toArray
  }

  /** 
      Answer the supplied map's values as an array.
      @param receiver a scala.collection.immutable.Map[Any,Any]
      @return Array[Any]
   */
  def MapValues(receiver: Map[Any, Any]): Array[Any] = {
    receiver.values.toArray
  }

  /** 
      Answer collection's length.
      @param coll a Array[T]
      @return size
   */
  def CollectionLength[T: ClassTag](coll: Array[T]): Int = {
    coll.length
  }

  /** 
      Answer collection's length.
      @param coll a ArrayBuffer[T]
      @return size
   */
  def CollectionLength[T: ClassTag](coll: ArrayBuffer[T]): Int = {
    coll.size
  }

  /** 
      Answer collection's length.
      @param coll a MutableSet[T]
      @return size
   */
  def CollectionLength[T: ClassTag](coll: MutableSet[T]): Int = {
    coll.size
  }

  /** 
      Answer collection's length.
      @param coll a Set[T]
      @return size
   */
  def CollectionLength[T: ClassTag](coll: Set[T]): Int = {
    coll.size
  }

  /** 
      Answer collection's length.
      @param coll a TreeSet[T]
      @return size
   */
  def CollectionLength[T: ClassTag](coll: TreeSet[T]): Int = {
    coll.size
  }

  /** 
      Answer collection's length.
      @param coll a SortedSet[T]
      @return size
   */
  def CollectionLength[T: ClassTag](coll: SortedSet[T]): Int = {
    coll.size
  }

  /** 
      Answer collection's length.
      @param coll a List[T]
      @return size
   */
  def CollectionLength[T: ClassTag](coll: List[T]): Int = {
    coll.size
  }

  /** 
      Answer collection's length.
      @param coll a Queue[T]
      @return size
   */
  def CollectionLength[T: ClassTag](coll: Queue[T]): Int = {
    coll.size
  }

  /**
   * Suppress functions that use Stack  and Vector until Mdmgr supports it
   * def CollectionLength[T : ClassTag](coll : Stack[T]) : Int = {
   * coll.size
   * }
   *
   * def CollectionLength[T : ClassTag](coll : Vector[T]) : Int = {
   * coll.size
   * }
   *
   */

  /** 
      Answer collection's length.
      @param coll a scala.collection.mutable.Map[T]
      @return size
   */
  def CollectionLength[K: ClassTag, V: ClassTag](coll: MutableMap[K, V]): Int = {
    coll.size
  }

  /** 
      Answer collection's length.
      @param coll a Map[T]
      @return size
   */
  def CollectionLength[K: ClassTag, V: ClassTag](coll: Map[K, V]): Int = {
    coll.size
  }

  /** 
      Answer collection's length.
      @param coll a HashMap[T]
      @return size
   */
  def CollectionLength[K: ClassTag, V: ClassTag](coll: HashMap[K, V]): Int = {
    coll.size
  }

  /** 
      Add the supplied item(s) to a collection 
      @param collection a Collection of some type
      @param item(s) an item to append 
      @return a new collection with the item appended
  */

  def Add[T : ClassTag](coll : Array[T], items : T*) : Array[T] = {
      if (coll != null && items != null) {
          val itemArray : Array[T] = items.toArray
          coll ++ (itemArray)
      } else {
          if (coll != null) {
              coll
          } else {
              Array[T]()
          }
      }
  }

  /** 
      Add the supplied item(s) to a collection 
      @param collection a Collection of some type
      @param item(s) an item to append 
      @return a new collection with the item appended
  */

  def Add[T : ClassTag](coll : ArrayBuffer[T], items : T*) : ArrayBuffer[T] = {
      if (coll != null && items != null) {
          val itemArray : Array[T] = items.toArray
          coll ++ (itemArray)
      } else {
          if (coll != null) {
              ArrayBuffer[T]() ++ coll
          } else {
              ArrayBuffer[T]()
          }
      }
  }

  /** 
      Add the supplied item(s) to a collection 
      @param collection a Collection of some type
      @param item(s) an item to append 
      @return a new collection with the item appended
  */

  def Add[T : ClassTag](coll : List[T], items : T*) : scala.collection.immutable.List[T] = {
      if (coll != null && items != null) {
          val itemArray : Array[T] = items.toArray
          coll ++ (itemArray)
      } else {
          if (coll != null) {
              scala.collection.immutable.List[T]() ++ coll
          } else {
              scala.collection.immutable.List[T]()
          }
      }
  }

  /** 
      Add the supplied item(s) to a collection 
      @param collection a Collection of some type
      @param item(s) an item to append 
      @return a new collection with the item appended
  */

  def Add[T : ClassTag](coll : Queue[T], items : T*) : Queue[T] = {
      if (coll != null && items != null) {
          val itemArray : Array[T] = items.toArray
          coll ++ (itemArray)
      } else {
          if (coll != null) {
              Queue[T]() ++ coll
          } else {
              Queue[T]()
          }
      }
  }

  /** 
      Add the supplied item(s) to a collection 
      @param collection a Collection of some type
      @param item(s) an item to append 
      @return a new collection with the item appended
  */

  def Add[T : ClassTag](coll : SortedSet[T], items : T*)(implicit cmp: Ordering[T]): SortedSet[T] = {
      if (coll != null && items != null) {
          val itemArray : Array[T] = items.toArray
          coll ++ (itemArray)
      } else {
          if (coll != null) {
              SortedSet[T]() ++ coll
          } else {
              SortedSet[T]()
          }
      }
  }

  /** 
      Add the supplied item(s) to a collection 
      @param collection a Collection of some type
      @param item(s) an item to append 
      @return a new collection with the item appended
  */

  def Add[T : ClassTag](coll : TreeSet[T], items : T*)(implicit cmp: Ordering[T]) : TreeSet[T] = {
      if (coll != null && items != null) {
          val itemArray : Array[T] = items.toArray
          coll ++ (itemArray)
      } else {
          if (coll != null) {
              TreeSet[T]() ++ coll
          } else {
              TreeSet[T]()
          }
      }
  }

  /** 
      Add the supplied item(s) to a collection 
      @param collection a Collection of some type
      @param item(s) an item to append 
      @return a new collection with the item appended
  */

  def Add[T : ClassTag](coll : MutableSet[T], items : T*) : MutableSet[T] = {
      if (coll != null && items != null) {
          val itemArray : Array[T] = items.toArray
          coll ++ (itemArray)
      } else {
          if (coll != null) {
              MutableSet[T]() ++ coll
          } else {
              MutableSet[T]()
          }
      }
  }

  /** 
      Add the supplied item(s) to a collection 
      @param collection a Collection of some type
      @param item(s) an item to append 
      @return a new collection with the item appended
  */

  def Add[T : ClassTag](coll : Set[T], items : T*) : Set[T] = {
      if (coll != null && items != null) {
          val itemArray : Array[T] = items.toArray
          coll ++ (itemArray)
      } else {
          if (coll != null) {
              Set[T]() ++ coll
          } else {
              Set[T]()
          }
      }
  }


  /** Accept an indefinite number of objects and make them as List of String.
      Null objects produce "" for their values.
      @param args : an indefinite number of objects of any type
      @return a list of their string representations
   */
  def ToStringList(args : Any*) : List[String] = {
  	val argList : List[Any] = args.toList
  	argList.map( arg => if (arg != null) arg.toString else "")
   }

  /** Convert any type to string.  Null object produces ""
      @param arg : any kind of object
      @return its string representation
   */
  def ToString(arg : Any) : String = {
  	if (arg != null) arg.toString else ""
   }
  
  /**
   * Accept an arbitrary number of arguments of any type, except that at least the last one
   * must be a Boolean.  If the last is not a Boolean type, false is returned as the 
   * result.
   * 
   * This function is designed to allow the user to execute an arbitrary number of expressions
   * of any type but still provide a visual cue in the calling PMML that the expectation is
   * that last expression must return a Boolean to participate in the enclosing function or derived field...
   * assuming a PMML caller.
   * 
   * @param args : variable number of Any 
   * @return Boolean
   */
  def CompoundStatementBoolean(args : Any*): Boolean = {
	  val answer : Boolean = if (args == null) {
		  false
	  } else {
		  val argList : List[Any] = args.toList
		  val lastOne : Any = argList.last
		  val ans : Boolean = if (lastOne.isInstanceOf[Boolean]) lastOne.asInstanceOf[Boolean] else false
		  ans
	  }
	  answer
  }

  /**
   * Accept an arbitrary number of arguments of any type, except that at least the last one
   * must be a String.  If the last is not a String type, "" is returned as the 
   * result.
   * 
   * This function is designed to allow the user to execute an arbitrary number of expressions
   * of any type but still provide a visual cue in the calling PMML that the expectation is
   * that last expression must return a String to participate in the enclosing function or derived field...
   * assuming a PMML caller.
   * 
   * @param args : variable number of Any 
   * @return String
   */
  def CompoundStatementString(args : Any*): String = {
	  val answer : String = if (args == null) {
		  ""
	  } else {
		  val argList : List[Any] = args.toList
		  val lastOne : Any = argList.last
		  val ans : String = if (lastOne.isInstanceOf[String]) lastOne.asInstanceOf[String] else ""
		  ans
	  }
	  answer
  }

  /**
   * Accept an arbitrary number of arguments of any type, except that at least the last one
   * must be an Int.  If the last is not a Boolean type, 0 is returned as the 
   * result.
   * 
   * This function is designed to allow the user to execute an arbitrary number of expressions
   * of any type but still provide a visual cue in the calling PMML that the expectation is
   * that last expression must return a Int to participate in the enclosing function or derived field...
   * assuming a PMML caller.
   * 
   * @param args : variable number of Any 
   * @return Int
   */
  def CompoundStatementInt(args : Any*): Int = {
	  val answer : Int = if (args == null) {
		  0
	  } else {
		  val argList : List[Any] = args.toList
		  val lastOne : Any = argList.last
		  val ans : Int = if (lastOne.isInstanceOf[Int]) lastOne.asInstanceOf[Int] else 0
		  ans
	  }
	  answer
  }

  /**
   * Accept an arbitrary number of arguments of any type, except that at least the last one
   * must be a Long.  If the last is not a Long type, 0 is returned as the 
   * result.
   * 
   * This function is designed to allow the user to execute an arbitrary number of expressions
   * of any type but still provide a visual cue in the calling PMML that the expectation is
   * that last expression must return a Long to participate in the enclosing function or derived field...
   * assuming a PMML caller.
   * 
   * @param args : variable number of Any 
   * @return Long
   */
  def CompoundStatementLong(args : Any*): Long = {
	  val answer : Long = if (args == null) {
		  0
	  } else {
		  val argList : List[Any] = args.toList
		  val lastOne : Any = argList.last
		  val ans : Long = if (lastOne.isInstanceOf[Long]) lastOne.asInstanceOf[Long] else 0
		  ans
	  }
	  answer
   }

  /**
   * Accept an arbitrary number of arguments of any type, except that at least the last one
   * must be a Float.  If the last is not a Float type, 0.0 is returned as the 
   * result.
   * 
   * This function is designed to allow the user to execute an arbitrary number of expressions
   * of any type but still provide a visual cue in the calling PMML that the expectation is
   * that last expression must return a Float to participate in the enclosing function or derived field...
   * assuming a PMML caller.
   * 
   * @param args : variable number of Any 
   * @return Float
   */
  def CompoundStatementFloat(args : Any*): Float = {
    val answer : Float = if (args == null) {
    	0.0F 
    } else {
	    val argList : List[Any] = args.toList
	    val lastOne : Any = argList.last
	    val ans : Float = if (lastOne.isInstanceOf[Float]) lastOne.asInstanceOf[Float] else 0.0F
	    ans
    }
    answer
  }

  /**
   * Accept an arbitrary number of arguments of any type, except that at least the last one
   * must be a Double.  If the last is not a Double type, 0.0 is returned as the 
   * result.
   * 
   * This function is designed to allow the user to execute an arbitrary number of expressions
   * of any type but still provide a visual cue in the calling PMML that the expectation is
   * that last expression must return a Double to participate in the enclosing function or derived field...
   * assuming a PMML caller.
   * 
   * @param args : variable number of Any 
   * @return Double
   */
  def CompoundStatementDouble(args : Any*): Double = {
    val answer : Double = if (args == null) {
    	0.0D 
    } else {
	    val argList : List[Any] = args.toList
	    val lastOne : Any = argList.last
	    val ans : Double = if (lastOne.isInstanceOf[Double]) lastOne.asInstanceOf[Double] else 0.0D
	    ans
    }
    answer
  }

  /**
   * Accept an arbitrary number of arguments of any type.  Answer the value returned by
   * the last arg.  
   * @param args : variable number of Any 
   * @return Any
   * 
   * NOTE: There is no type safety with this function.  It is the responsibility of the caller
   * to recognize the real type of the element and treat it appropriately. In PMML the result type
   * of the last expression MUST be the one the enclosing DerivedField or Apply function expects.
   * Failure to do this will result in ClassCastException at runtime.
   */
  def CompoundStatement(args : Any*): Any = {
    if (args != null) args.toList.last else ""
  }
}
