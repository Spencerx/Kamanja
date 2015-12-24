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

package com.ligadata.Serialize

import java.util.Properties
import java.io._
import scala.Enumeration
import scala.io.Source._
import org.apache.logging.log4j._

import com.ligadata.kamanja.metadata._

import com.twitter.chill.ScalaKryoInstantiator
import com.esotericsoftware.kryo.io.{Input, Output}
import com.ligadata.Exceptions._
import com.ligadata.Exceptions.StackTrace

class KryoSerializer extends Serializer{

  lazy val kryoFactory = new ScalaKryoInstantiator
  private[this] var classLoader: java.lang.ClassLoader = null

  val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)

  override def SetClassLoader(cl : java.lang.ClassLoader): Unit = {
    classLoader = cl
  }

  override def SerializeObjectToByteArray(obj : Object) : Array[Byte] = {
    try{
      val kryo = kryoFactory.newKryo()
      val baos = new ByteArrayOutputStream
      val output = new Output(baos)
      if (classLoader != null)
        kryo.setClassLoader(classLoader)
      kryo.writeClassAndObject(output,obj)
      output.close()
      val ba = baos.toByteArray()
      logger.debug("Serialized data contains " + ba.length + " bytes ")
      ba
    }catch{
      case e:Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
	throw new KryoSerializationException("Failed to Serialize the object(" + obj.getClass().getName() + "): " + e.getMessage())
      }
    }
  }


  override def DeserializeObjectFromByteArray(ba: Array[Byte]) : Object = {
    try{
      val kryo = kryoFactory.newKryo()
      val bais = new ByteArrayInputStream(ba);
      val inp = new Input(bais)
      if (classLoader != null)
        kryo.setClassLoader(classLoader)
      val m = kryo.readClassAndObject(inp)
      logger.debug("DeSerialized object => " + m.getClass().getName())
      inp.close()
      m
    }catch{
      case e:Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
	throw new KryoSerializationException("Failed to DeSerialize the object:" + e.getMessage())
      }
    }
  }

  override def DeserializeObjectFromByteArray(ba: Array[Byte],objectType: String) : Object = {
    DeserializeObjectFromByteArray(ba)
  }
}
