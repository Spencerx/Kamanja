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

package com.ligadata.samples.messages

import java.util.Date
import org.json4s.jackson.JsonMethods._
import com.ligadata.KamanjaBase.{ InputData, DelimitedData, JsonData, XmlData }
import java.io.{ DataInputStream, DataOutputStream }
import com.ligadata.KamanjaBase.{ BaseMsg, BaseMsgObj, TransformMessage, BaseContainer, BaseContainerObj, MdBaseResolveInfo, RDDObject, RDD, JavaRDDObject, MessageContainerBase }

object CustAlertHistory extends RDDObject[CustAlertHistory] with BaseContainerObj {
  type T = CustAlertHistory

  override def FullName: String = "com.ligadata.samples.messages.CustAlertHistory"
  override def NameSpace: String = "com.ligadata.samples.messages"
  override def Name: String = "CustAlertHistory"
  override def Version: String = "000000.000001.000000"
  override def CreateNewContainer: BaseContainer = new CustAlertHistory()
  override def IsFixed: Boolean = true;
  override def IsKv: Boolean = false;
  override def CanPersist: Boolean = true;

  def build = new T
  def build(from: T) = new T(from)

  val partitionKeys: Array[String] = null;
  val partKeyPos = Array(0)
  val primaryKeys: Array[String] = null;
   

  override def PartitionKeyData(inputdata: InputData): Array[String] = Array[String]()
  override def PrimaryKeyData(inputdata: InputData): Array[String] = Array[String]()
  override def getTimePartitionInfo: (String, String, String) = (null, null, null) // FieldName, Format & Time Partition Types(Daily/Monthly/Yearly)
  override def TimePartitionData(inputdata: InputData): Long = 0
  
  override def hasPrimaryKey(): Boolean = {
	if(primaryKeys == null) return false;
	(primaryKeys.size > 0);
  }

  override def hasPartitionKey(): Boolean = {
	 if(partitionKeys == null) return false;
    (partitionKeys.size > 0);
  }

  override def hasTimeParitionInfo(): Boolean = {
    val tmPartInfo = getTimePartitionInfo
    (tmPartInfo != null && tmPartInfo._1 != null && tmPartInfo._2 != null && tmPartInfo._3 != null);
  }
  
  override def getFullName = FullName
  override def toJavaRDDObject: JavaRDDObject[T] = JavaRDDObject.fromRDDObject[T](this)
}

class CustAlertHistory(var transactionId: Long, other: CustAlertHistory) extends BaseContainer {
  if (other != null && other != this) {
    // call copying fields from other to local variables
  }

  def this(txnId: Long) = {
    this(txnId, null)
  }
  def this(other: CustAlertHistory) = {
    this(0, other)
  }
  def this() = {
    this(0, null)
  }
  
  override def IsFixed: Boolean = CustAlertHistory.IsFixed;
  override def IsKv: Boolean = CustAlertHistory.IsKv;
  override def CanPersist: Boolean = CustAlertHistory.CanPersist;
  override def FullName: String = CustAlertHistory.FullName
  override def NameSpace: String = CustAlertHistory.NameSpace
  override def Name: String = CustAlertHistory.Name
  override def Version: String = CustAlertHistory.Version
   

  var custid: Long = 0;
  var branchid: Int = 0;
  var accno: Long = 0;
  var alertDtTmInMs: Long = _;
  var alertType: String = ""
  var numDaysWithLessBalance: Int = 0

  def Clone(): MessageContainerBase = {
    CustAlertHistory.build(this)
  }
  
  def withAlertDtTmInMs(curDtTmInMs: Long): CustAlertHistory = { this }
  def withAlertType(alertType: String): CustAlertHistory = { this }
  def withNumDays(daysWithLessBalance: Int): CustAlertHistory = { this }

  override def Save: Unit = { CustAlertHistory.saveOne(this) }
  override def PartitionKeyData: Array[String] = null
  override def PrimaryKeyData: Array[String] = null
  override def set(key: String, value: Any): Unit = {}
  override def get(key: String): Any = null
  override def getOrElse(key: String, default: Any): Any = { throw new Exception("getOrElse function is not yet implemented") }
  private def getByName(key: String): Any = null
  private def getWithReflection(key: String): Any = null
  override def AddMessage(childPath: Array[(String, String)], msg: BaseMsg): Unit = {}
  override def GetMessage(childPath: Array[(String, String)], primaryKey: Array[String]): com.ligadata.KamanjaBase.BaseMsg = null
  def populate(inputdata: InputData) = {}
  private def populateCSV(inputdata: DelimitedData): Unit = {}
  private def populateJson(json: JsonData): Unit = {}
  def CollectionAsArrString(v: Any): Array[String] = null
  private def assignJsonData(json: JsonData): Unit = {}
  private def populateXml(xmlData: XmlData): Unit = {}
  override def Serialize(dos: DataOutputStream): Unit = {}
  override def Deserialize(dis: DataInputStream, mdResolver: MdBaseResolveInfo, loader: java.lang.ClassLoader, savedDataVersion: String): Unit = {}
  def ConvertPrevToNewVerObj(obj: Any): Unit = {}
  override def getNativeKeyValues(): scala.collection.immutable.Map[String, (String, Any)] = null
  
  override def hasPrimaryKey(): Boolean = {
    CustAlertHistory.hasPrimaryKey;
  }

  override def hasPartitionKey(): Boolean = {
    CustAlertHistory.hasPartitionKey;
  }

  override def hasTimeParitionInfo(): Boolean = {
    CustAlertHistory.hasTimeParitionInfo;
  }
}

object CustPreferences extends RDDObject[CustPreferences] with BaseContainerObj {
  type T = CustPreferences

  override def FullName: String = "com.ligadata.samples.messages.CustPreferences"
  override def NameSpace: String = "com.ligadata.samples.messages"
  override def Name: String = "CustPreferences"
  override def Version: String = "000000.000001.000000"
  override def CreateNewContainer: BaseContainer = new CustPreferences()
  override def IsFixed: Boolean = true;
  override def IsKv: Boolean = false;
  override def CanPersist: Boolean = true;

  def build = new T
  def build(from: T) = new T(from)

  val partitionKeys: Array[String] = null;
  val partKeyPos = Array(0)
  val primaryKeys: Array[String] = null;
   
  override def PartitionKeyData(inputdata: InputData): Array[String] = Array[String]()
  override def PrimaryKeyData(inputdata: InputData): Array[String] = Array[String]()
  override def getTimePartitionInfo: (String, String, String) = (null, null, null) // FieldName, Format & Time Partition Types(Daily/Monthly/Yearly)
  override def TimePartitionData(inputdata: InputData): Long = 0

  override def hasPrimaryKey(): Boolean = {
	if(primaryKeys == null) return false;
	(primaryKeys.size > 0);
  }

  override def hasPartitionKey(): Boolean = {
	 if(partitionKeys == null) return false;
    (partitionKeys.size > 0);
  }

  override def hasTimeParitionInfo(): Boolean = {
    val tmPartInfo = getTimePartitionInfo
   (tmPartInfo != null && tmPartInfo._1 != null && tmPartInfo._2 != null && tmPartInfo._3 != null);
  }
  override def getFullName = FullName
  override def toJavaRDDObject: JavaRDDObject[T] = JavaRDDObject.fromRDDObject[T](this)
}

class CustPreferences(var transactionId: Long, other: CustPreferences) extends BaseContainer {
  if (other != null && other != this) {
    // call copying fields from other to local variables
  }

  def this(txnId: Long) = {
    this(txnId, null)
  }
  def this(other: CustPreferences) = {
    this(0, other)
  }
  def this() = {
    this(0, null)
  }

  override def IsFixed: Boolean = CustPreferences.IsFixed;
  override def IsKv: Boolean = CustPreferences.IsKv;
  override def CanPersist: Boolean = CustPreferences.CanPersist;
  override def FullName: String = CustPreferences.FullName
  override def NameSpace: String = CustPreferences.NameSpace
  override def Name: String = CustPreferences.Name
  override def Version: String = CustPreferences.Version
 
  def withMinBalanceAlertOptout(curDt: Date): CustPreferences = { this }
  def withOverdraftlimit(alertType: String): CustPreferences = { this }
  def withMultiDayMinBalanceAlertOptout(daysWithLessBalance: Int): CustPreferences = { this }

  def Clone(): MessageContainerBase = {
    CustPreferences.build(this)
  }
  
  var custid: Long = 0;
  var branchid: Int = 0;
  var accno: Long = 0;
  var minBalanceAlertOptout = false;
  var overdraftlimit: Double = 0;
  var multiDayMinBalanceAlertOptout = false;

  override def Save: Unit = { CustPreferences.saveOne(this) }
  override def PartitionKeyData: Array[String] = null
  override def PrimaryKeyData: Array[String] = null
  override def set(key: String, value: Any): Unit = {}
  override def get(key: String): Any = null
  override def getOrElse(key: String, default: Any): Any = { throw new Exception("getOrElse function is not yet implemented") }
  private def getByName(key: String): Any = null
  private def getWithReflection(key: String): Any = null
  override def AddMessage(childPath: Array[(String, String)], msg: BaseMsg): Unit = {}
  override def GetMessage(childPath: Array[(String, String)], primaryKey: Array[String]): com.ligadata.KamanjaBase.BaseMsg = null
  def populate(inputdata: InputData) = {}
  private def populateCSV(inputdata: DelimitedData): Unit = {}
  private def populateJson(json: JsonData): Unit = {}
  def CollectionAsArrString(v: Any): Array[String] = null
  private def assignJsonData(json: JsonData): Unit = {}
  private def populateXml(xmlData: XmlData): Unit = {}
  override def Serialize(dos: DataOutputStream): Unit = {}
  override def Deserialize(dis: DataInputStream, mdResolver: MdBaseResolveInfo, loader: java.lang.ClassLoader, savedDataVersion: String): Unit = {}
  def ConvertPrevToNewVerObj(obj: Any): Unit = {}
  override def getNativeKeyValues(): scala.collection.immutable.Map[String, (String, Any)] = null
  
  
  override def hasPrimaryKey(): Boolean = {
    CustPreferences.hasPrimaryKey;
  }

  override def hasPartitionKey(): Boolean = {
    CustPreferences.hasPartitionKey;
  }

  override def hasTimeParitionInfo(): Boolean = {
    CustPreferences.hasTimeParitionInfo;
  }

}

object CustTransaction extends RDDObject[CustTransaction] with BaseMsgObj {
  type T = CustTransaction

  override def TransformDataAttributes: TransformMessage = null
  override def NeedToTransformData: Boolean = false
  override def FullName: String = "com.ligadata.samples.messages.CustTransaction"
  override def NameSpace: String = "com.ligadata.samples.messages"
  override def Name: String = "CustTransaction"
  override def Version: String = "000000.000001.000000"
  override def CreateNewMessage: BaseMsg = new CustTransaction()
  override def IsFixed: Boolean = true;
  override def IsKv: Boolean = false;
  override def CanPersist: Boolean = true;

  val partitionKeys: Array[String] = null;
  val partKeyPos = Array(0)
  val primaryKeys: Array[String] = null;
   
  def build = new T
  def build(from: T) = new T(from)

  override def PartitionKeyData(inputdata: InputData): Array[String] = Array[String]()
  override def PrimaryKeyData(inputdata: InputData): Array[String] = Array[String]()
  override def getTimePartitionInfo: (String, String, String) = (null, null, null) // FieldName, Format & Time Partition Types(Daily/Monthly/Yearly)
  override def TimePartitionData(inputdata: InputData): Long = 0

  override def hasPrimaryKey(): Boolean = {
	if(primaryKeys == null) return false;
	(primaryKeys.size > 0);
  }

  override def hasPartitionKey(): Boolean = {
	 if(partitionKeys == null) return false;
    (partitionKeys.size > 0);
  }

  override def hasTimeParitionInfo(): Boolean = {
    val tmPartInfo = getTimePartitionInfo
    (tmPartInfo != null && tmPartInfo._1 != null && tmPartInfo._2 != null && tmPartInfo._3 != null);
  }
  
  override def getFullName = FullName
  override def toJavaRDDObject: JavaRDDObject[T] = JavaRDDObject.fromRDDObject[T](this)
}

class CustTransaction(var transactionId: Long, other: CustTransaction) extends BaseMsg {
  if (other != null && other != this) {
    // call copying fields from other to local variables
  }

  def this(txnId: Long) = {
    this(txnId, null)
  }
  def this(other: CustTransaction) = {
    this(0, other)
  }
  def this() = {
    this(0, null)
  }

  override def IsFixed: Boolean = CustTransaction.IsFixed;
  override def IsKv: Boolean = CustTransaction.IsKv;
  override def CanPersist: Boolean = CustTransaction.CanPersist;
  override def FullName: String = CustTransaction.FullName
  override def NameSpace: String = CustTransaction.NameSpace
  override def Name: String = CustTransaction.Name
  override def Version: String = CustTransaction.Version
  
  def Clone(): MessageContainerBase = {
    CustTransaction.build(this)
  }
  
  var custid: Long = 0;
  var branchid: Int = 0;
  var accno: Long = 0;
  var amount: Double = 0;
  var balance: Double = 0;
  var date: Int = 0;
  var time: Int = 0;
  var locationid: Int = 0;
  var transtype: String = "";

  override def Save: Unit = { CustTransaction.saveOne(this) }
  override def PartitionKeyData: Array[String] = null
  override def PrimaryKeyData: Array[String] = null
  override def set(key: String, value: Any): Unit = {}
  override def get(key: String): Any = null
  override def getOrElse(key: String, default: Any): Any = { throw new Exception("getOrElse function is not yet implemented") }
  private def getByName(key: String): Any = null
  private def getWithReflection(key: String): Any = null
  override def AddMessage(childPath: Array[(String, String)], msg: BaseMsg): Unit = {}
  override def GetMessage(childPath: Array[(String, String)], primaryKey: Array[String]): com.ligadata.KamanjaBase.BaseMsg = null
  def populate(inputdata: InputData) = {}
  private def populateCSV(inputdata: DelimitedData): Unit = {}
  private def populateJson(json: JsonData): Unit = {}
  def CollectionAsArrString(v: Any): Array[String] = null
  private def assignJsonData(json: JsonData): Unit = {}
  private def populateXml(xmlData: XmlData): Unit = {}
  override def Serialize(dos: DataOutputStream): Unit = {}
  override def Deserialize(dis: DataInputStream, mdResolver: MdBaseResolveInfo, loader: java.lang.ClassLoader, savedDataVersion: String): Unit = {}
  def ConvertPrevToNewVerObj(obj: Any): Unit = {}
  override def getNativeKeyValues(): scala.collection.immutable.Map[String, (String, Any)] = null
  
  
  override def hasPrimaryKey(): Boolean = {
    CustTransaction.hasPrimaryKey;
  }

  override def hasPartitionKey(): Boolean = {
    CustTransaction.hasPartitionKey;
  }

  override def hasTimeParitionInfo(): Boolean = {
    CustTransaction.hasTimeParitionInfo;
  }

}

object GlobalPreferences extends RDDObject[GlobalPreferences] with BaseContainerObj {
  type T = GlobalPreferences

  override def FullName: String = "com.ligadata.samples.messages.GlobalPreferences"
  override def NameSpace: String = "com.ligadata.samples.messages"
  override def Name: String = "GlobalPreferences"
  override def Version: String = "000000.000001.000000"
  override def CreateNewContainer: BaseContainer = new GlobalPreferences()
  override def IsFixed: Boolean = true;
  override def IsKv: Boolean = false;
  override def CanPersist: Boolean = true;

  def build = new T
  def build(from: T) = new T(from)

  val partitionKeys: Array[String] = null;
  val partKeyPos = Array(0)
  val primaryKeys: Array[String] = null;
   
  override def PartitionKeyData(inputdata: InputData): Array[String] = Array[String]()
  override def PrimaryKeyData(inputdata: InputData): Array[String] = Array[String]()
  override def getTimePartitionInfo: (String, String, String) = (null, null, null) // FieldName, Format & Time Partition Types(Daily/Monthly/Yearly)
  override def TimePartitionData(inputdata: InputData): Long = 0

  override def hasPrimaryKey(): Boolean = {
	if(primaryKeys == null) return false;
	(primaryKeys.size > 0);
  }

  override def hasPartitionKey(): Boolean = {
	 if(partitionKeys == null) return false;
    (partitionKeys.size > 0);
  }

  override def hasTimeParitionInfo(): Boolean = {
    val tmPartInfo = getTimePartitionInfo
    (tmPartInfo != null && tmPartInfo._1 != null && tmPartInfo._2 != null && tmPartInfo._3 != null);
  }
  
  override def getFullName = FullName
  override def toJavaRDDObject: JavaRDDObject[T] = JavaRDDObject.fromRDDObject[T](this)
}

class GlobalPreferences(var transactionId: Long, other: GlobalPreferences) extends BaseContainer {
  if (other != null && other != this) {
    // call copying fields from other to local variables
  }

  def this(txnId: Long) = {
    this(txnId, null)
  }
  def this(other: GlobalPreferences) = {
    this(0, other)
  }
  def this() = {
    this(0, null)
  }

  override def IsFixed: Boolean = GlobalPreferences.IsFixed;
  override def IsKv: Boolean = GlobalPreferences.IsKv;
  override def CanPersist: Boolean = GlobalPreferences.CanPersist;
  override def FullName: String = GlobalPreferences.FullName
  override def NameSpace: String = GlobalPreferences.NameSpace
  override def Name: String = GlobalPreferences.Name
  override def Version: String = GlobalPreferences.Version
 
  def Clone(): MessageContainerBase = {
    GlobalPreferences.build(this)
  }
  
  var overDraftLimit: Double = 0.0;
  var minAlertBalance: Double = 0.0
  var minAlertDurationInHrs: Int = 48;
  var numLookbackDaysForMultiDayMinBalanceAlert: Int = 30;
  var maxNumDaysAllowedWithMinBalance: Int = 2;

  override def Save: Unit = { GlobalPreferences.saveOne(this) }
  override def PartitionKeyData: Array[String] = null
  override def PrimaryKeyData: Array[String] = null
  override def set(key: String, value: Any): Unit = {}
  override def get(key: String): Any = null
  override def getOrElse(key: String, default: Any): Any = { throw new Exception("getOrElse function is not yet implemented") }
  private def getByName(key: String): Any = null
  private def getWithReflection(key: String): Any = null
  override def AddMessage(childPath: Array[(String, String)], msg: BaseMsg): Unit = {}
  override def GetMessage(childPath: Array[(String, String)], primaryKey: Array[String]): com.ligadata.KamanjaBase.BaseMsg = null
  def populate(inputdata: InputData) = {}
  private def populateCSV(inputdata: DelimitedData): Unit = {}
  private def populateJson(json: JsonData): Unit = {}
  def CollectionAsArrString(v: Any): Array[String] = null
  private def assignJsonData(json: JsonData): Unit = {}
  private def populateXml(xmlData: XmlData): Unit = {}
  override def Serialize(dos: DataOutputStream): Unit = {}
  override def Deserialize(dis: DataInputStream, mdResolver: MdBaseResolveInfo, loader: java.lang.ClassLoader, savedDataVersion: String): Unit = {}
  def ConvertPrevToNewVerObj(obj: Any): Unit = {}
  override def getNativeKeyValues(): scala.collection.immutable.Map[String, (String, Any)] = null
  
  
  override def hasPrimaryKey(): Boolean = {
    GlobalPreferences.hasPrimaryKey;
  }

  override def hasPartitionKey(): Boolean = {
    GlobalPreferences.hasPartitionKey;
  }

  override def hasTimeParitionInfo(): Boolean = {
    GlobalPreferences.hasTimeParitionInfo;
  }

}

