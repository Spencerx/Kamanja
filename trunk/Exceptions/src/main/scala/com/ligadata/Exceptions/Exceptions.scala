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

package com.ligadata.Exceptions

case class UnsupportedObjectException(e: String) extends Exception(e)
case class Json4sParsingException(e: String) extends Exception(e)
case class FunctionListParsingException(e: String) extends Exception(e)
case class FunctionParsingException(e: String) extends Exception(e)
case class TypeDefListParsingException(e: String) extends Exception(e)
case class TypeParsingException(e: String) extends Exception(e)
case class TypeDefProcessingException(e: String) extends Exception(e)
case class ConceptListParsingException(e: String) extends Exception(e)
case class ConceptParsingException(e: String) extends Exception(e)
case class MessageDefParsingException(e: String) extends Exception(e)
case class ContainerDefParsingException(e: String) extends Exception(e)
case class ModelDefParsingException(e: String) extends Exception(e)
case class ApiResultParsingException(e: String) extends Exception(e)
case class UnexpectedMetadataAPIException(e: String) extends Exception(e)
case class ObjectNotFoundException(e: String) extends Exception(e)
case class CreateStoreFailedException(e: String) extends Exception(e)
case class UpdateStoreFailedException(e: String) extends Exception(e)

case class LoadAPIConfigException(e: String) extends Exception(e)
case class MissingPropertyException(e: String) extends Exception(e)
case class InvalidPropertyException(e: String) extends Exception(e)
case class KryoSerializationException(e: String) extends Exception(e)
case class InternalErrorException(e: String) extends Exception(e)
case class TranIdNotFoundException(e: String) extends Exception(e)

case class AlreadyExistsException(e: String) extends Throwable(e)
case class ObjectNolongerExistsException(e: String) extends Throwable(e)
case class InvalidArgumentException(e: String) extends Throwable(e)

case class Json4sSerializationException(e: String) extends Exception(e)
case class ZkTransactionParsingException(e: String) extends Exception(e)
case class EngineConfigParsingException(e: String) extends Exception(e)
case class ApiArgListParsingException(e: String) extends Exception(e)
case class JsonException(message: String) extends Exception(message)
case class MessageException(message: String) extends Exception(message)

case class MsgCompilationFailedException(e: String) extends Exception(e)
case class ModelCompilationFailedException(e: String) extends Exception(e)

case class MissingArgumentException(e: String) extends Exception(e)
case class CreateKeySpaceFailedException(e: String) extends Exception(e)
case class KeyNotFoundException(e: String) extends Exception(e)
case class ConnectionFailedException(e: String) extends Exception(e)

case class SerializerManagerException(message: String) extends Exception(message)
case class ProtoBufSerializationException(e: String) extends Throwable(e)

case class KVMessageFormatingException(e: String) extends Exception(e)

case class FatalAdapterException(msg: String, cause: Throwable ) extends Exception(msg)

case class StorageConnectionException(msg: String, cause: Throwable ) extends Exception(msg)
case class StorageFetchException(msg: String, cause: Throwable ) extends Exception(msg)
case class StorageDMLException(msg: String, cause: Throwable ) extends Exception(msg)
case class StorageDDLException(msg: String, cause: Throwable ) extends Exception(msg)
