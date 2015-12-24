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

import java.io._
import com.ligadata.kamanja.metadata._

import java.io.ByteArrayOutputStream

import org.apache.logging.log4j._

trait Serializer{
  def SetClassLoader(cl : java.lang.ClassLoader): Unit
  def SerializeObjectToByteArray(obj : Object) : Array[Byte]
  def DeserializeObjectFromByteArray(ba: Array[Byte]) : Object
  def DeserializeObjectFromByteArray(ba: Array[Byte],objectType: String) : Object
}
