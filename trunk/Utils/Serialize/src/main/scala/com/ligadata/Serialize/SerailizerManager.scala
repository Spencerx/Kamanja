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

import com.ligadata.Exceptions._
import com.ligadata.Exceptions.StackTrace
import org.apache.logging.log4j._

object SerializerManager {
   val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
  def GetSerializer(serializerType: String): Serializer = {
    try {
      serializerType.toLowerCase match {
        case "kryo" => return new KryoSerializer
        case "protobuf" => return new ProtoBufSerializer
        case _ => {
          throw new SerializerManagerException("Unknown Serializer Type : " + serializerType)
        }
      }
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw new SerializerManagerException("Failed to create Serializer for : " + serializerType)
      }
    }
  }
}
