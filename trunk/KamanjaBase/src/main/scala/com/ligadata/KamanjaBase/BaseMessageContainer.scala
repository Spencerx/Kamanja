
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

package com.ligadata.KamanjaBase
import java.net.URL
import java.net.URLClassLoader
import java.io.{ ByteArrayInputStream, DataInputStream, DataOutputStream, ByteArrayOutputStream }
import com.ligadata.Exceptions.StackTrace
import org.apache.logging.log4j._
import java.util.Date
import java.util.Calendar
import java.text.SimpleDateFormat

trait MessageContainerBase {
  // System Columns
  var transactionId: Long
  var timePartitionData: Long = 0 // By default we are taking Java date with 0 milliseconds.
  var rowNumber: Int = 0 // This is unique value with in transactionId

  // System Attributes Functions
  final def TransactionId(transId: Long): Unit = { transactionId = transId }
  final def TransactionId(): Long = transactionId

  final def TimePartitionData(timeInMillisecs: Long): Unit = { timePartitionData = timeInMillisecs }
  final def TimePartitionData(): Long = timePartitionData

  final def RowNumber(rno: Int): Unit = { rowNumber = rno }
  final def RowNumber(): Int = rowNumber

  def isMessage: Boolean
  def isContainer: Boolean
  def IsFixed: Boolean
  def IsKv: Boolean
  def CanPersist: Boolean
  def populate(inputdata: InputData): Unit
  def set(key: String, value: Any): Unit
  def get(key: String): Any
  def getOrElse(key: String, default: Any): Any
  def AddMessage(childPath: Array[(String, String)], msg: BaseMsg): Unit
  def GetMessage(childPath: Array[(String, String)], primaryKey: Array[String]): BaseMsg
  def Version: String // Message or Container Version
  def PartitionKeyData: Array[String] // Partition key data
  def PrimaryKeyData: Array[String] // Primary key data
  def FullName: String // Message or Container Full Name
  def NameSpace: String // Message or Container NameSpace
  def Name: String // Message or Container Name
  def Deserialize(dis: DataInputStream, mdResolver: MdBaseResolveInfo, loader: java.lang.ClassLoader, savedDataVersion: String): Unit
  def Serialize(dos: DataOutputStream): Unit
  def Save(): Unit
  def Clone(): MessageContainerBase
  def hasPrimaryKey: Boolean
  def hasPartitionKey: Boolean
  def hasTimeParitionInfo: Boolean
  def getNativeKeyValues: scala.collection.immutable.Map[String, (String, Any)]
}

trait MessageContainerObjBase {
  def isMessage: Boolean
  def isContainer: Boolean
  def IsFixed: Boolean
  def IsKv: Boolean
  def CanPersist: Boolean
  def FullName: String // Message or Container FullName
  def NameSpace: String // Message or Container NameSpace
  def Name: String // Message or Container Name
  def Version: String // Message or Container Version
  def PartitionKeyData(inputdata: InputData): Array[String] // Partition key data
  def PrimaryKeyData(inputdata: InputData): Array[String] // Primary key data
  def getTimePartitionInfo: (String, String, String) // FieldName, Format & Time Partition Types(Daily/Monthly/Yearly)
  def TimePartitionData(inputdata: InputData): Long
  def hasPrimaryKey: Boolean
  def hasPartitionKey: Boolean
  def hasTimeParitionInfo: Boolean

  private def extractTime(fieldData: String, timeFormat: String): Long = {
    if (fieldData == null || fieldData.trim() == "") return 0

    if (timeFormat == null || timeFormat.trim() == "") return 0

    if (timeFormat.compareToIgnoreCase("epochtimeInMillis") == 0)
      return fieldData.toLong

    if (timeFormat.compareToIgnoreCase("epochtimeInSeconds") == 0 || timeFormat.compareToIgnoreCase("epochtime") == 0)
      return fieldData.toLong * 1000

    // Now assuming Date partition format exists.
    val dtFormat = new SimpleDateFormat(timeFormat);
    val tm =
      if (fieldData.size == 0) {
        new Date(0)
      } else {
        dtFormat.parse(fieldData)
      }
    tm.getTime()
  }

  def ComputeTimePartitionData(fieldData: String, timeFormat: String, timePartitionType: String): Long = {
    val fldTimeDataInMs = extractTime(fieldData, timeFormat)

    // Align to Partition
    var cal: Calendar = Calendar.getInstance();
    cal.setTime(new Date(fldTimeDataInMs));

    if (timePartitionType == null || timePartitionType.trim() == "") return 0

    timePartitionType.toLowerCase match {
      case "yearly" => {
        var newcal: Calendar = Calendar.getInstance();
        newcal.setTimeInMillis(0)
        newcal.set(Calendar.YEAR, cal.get(Calendar.YEAR));
        return newcal.getTime().getTime()
      }
      case "monthly" => {
        var newcal: Calendar = Calendar.getInstance();
        newcal.setTimeInMillis(0)
        newcal.set(Calendar.YEAR, cal.get(Calendar.YEAR))
        newcal.set(Calendar.MONTH, cal.get(Calendar.MONTH))
        return newcal.getTime().getTime()
      }
      case "daily" => {
        var newcal: Calendar = Calendar.getInstance();
        newcal.setTimeInMillis(0)
        newcal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        return newcal.getTime().getTime()
      }
    }
    return 0
  }

}

trait MdBaseResolveInfo {
  def getMessgeOrContainerInstance(typName: String): MessageContainerBase
}

object SerializeDeserialize {
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
  def Serialize(inst: MessageContainerBase): Array[Byte] = {
    val bos: ByteArrayOutputStream = new ByteArrayOutputStream(1024 * 1024)
    val dos = new DataOutputStream(bos)

    try {
      dos.writeUTF(inst.FullName)
      dos.writeUTF(inst.Version)
      dos.writeUTF(inst.getClass.getName)
      inst.Serialize(dos)
      val arr = bos.toByteArray
      dos.close
      bos.close
      return arr

    } catch {
      case e: Exception => {
        //LOG.error("Failed to get classname :" + clsName)
        logger.debug("StackTrace:" + StackTrace.ThrowableTraceString(e))
        dos.close
        bos.close
        throw e
      }
    }
    null
  }

  def Serialize(inst: MessageContainerBase, dos: DataOutputStream): Unit = {
    try {
      dos.writeUTF(inst.FullName)
      dos.writeUTF(inst.Version)
      dos.writeUTF(inst.getClass.getName)
      inst.Serialize(dos)
    } catch {
      case e: Exception => {
        //LOG.error("Failed to get classname :" + clsName)
        logger.debug("StackTrace:" + StackTrace.ThrowableTraceString(e))
        throw e
      }
    }
  }

  def Deserialize(bytearray: Array[Byte], mdResolver: MdBaseResolveInfo, loader: java.lang.ClassLoader, isTopObject: Boolean, desClassName: String): MessageContainerBase = {
    var dis = new DataInputStream(new ByteArrayInputStream(bytearray));

    val typName = dis.readUTF
    val version = dis.readUTF
    val classname = dis.readUTF
    try {
      // Expecting type name
      // get class instance for this type
      val typ =
        if (isTopObject) {
          mdResolver.getMessgeOrContainerInstance(typName)
        } else {
          try {
            Class.forName(desClassName, true, loader)
          } catch {
            case e: Exception => {
              logger.error("Failed to load Message/Container class %s with Reason:%s Message:%s".format(desClassName, e.getCause, e.getMessage))
              throw e // Rethrow
            }
          }
          var curClz = Class.forName(desClassName, true, loader)
          curClz.newInstance().asInstanceOf[MessageContainerBase]
        }
      if (typ == null) {
        throw new Exception("Message/Container %s not found to deserialize".format(typName))
      }
      typ.Deserialize(dis, mdResolver, loader, version.toString)
      dis.close
      return typ
    } catch {
      case e: Exception => {
        // LOG.error("Failed to get classname :" + clsName)
        logger.debug("StackTrace:" + StackTrace.ThrowableTraceString(e))
        dis.close
        throw e
      }
    }
    null
  }
}

trait BaseContainer extends MessageContainerBase {
  override def isMessage: Boolean = false
  override def isContainer: Boolean = true
}

trait BaseContainerObj extends MessageContainerObjBase {
  override def isMessage: Boolean = false
  override def isContainer: Boolean = true
  def CreateNewContainer: BaseContainer
}

trait BaseMsg extends MessageContainerBase {
  override def isMessage: Boolean = true
  override def isContainer: Boolean = false
}

trait BaseMsgObj extends MessageContainerObjBase {
  override def isMessage: Boolean = true
  override def isContainer: Boolean = false
  def NeedToTransformData: Boolean // Filter & Rearrange input attributes if needed
  def TransformDataAttributes: TransformMessage // Filter & Rearrange input columns if needed
  def CreateNewMessage: BaseMsg
}

// BUGBUG:: for now handling only CSV input data.
// Assuming this is filled properly, we are not checking whether outputFields are subset of inputFields or not.
// Assuming the field names are all same case (lower or upper). Because we don't want to convert them every time.
class TransformMessage {
  var messageType: String = null // Type of the message (first field from incoming data)
  var inputFields: Array[String] = null // All input fields
  var outputFields: Array[String] = null // All output fields filters from input field. These are subset of input fields.
  var outputKeys: Array[String] = null // Output Key field names from input fields.
}

case class MessageContainerBaseWithModFlag(modified: Boolean, value: MessageContainerBase)

