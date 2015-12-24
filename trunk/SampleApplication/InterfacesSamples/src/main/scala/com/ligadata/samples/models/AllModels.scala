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

package com.ligadata.models.samples.models

import com.ligadata.kamanja.metadata.{ ModelDef }
import com.ligadata.KamanjaBase.{ BaseMsg, BaseContainer, RddUtils, RddDate, BaseContainerObj, MessageContainerBase, RDDObject, RDD }
import com.ligadata.KamanjaBase.{ ModelInstance, ModelInstanceFactory, ModelResultBase, TransactionContext, EnvContext, NodeContext }
import com.ligadata.samples.messages.{ CustAlertHistory, GlobalPreferences, CustPreferences, CustTransaction }
import RddUtils._
import RddDate._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scala.io.Source
import java.io.{ DataInputStream, DataOutputStream }
import com.ligadata.kamanja.metadata.ModelDef;

// Implementation notes:
//   There is no need to pass context in every call to Builder or getRecent as the context is stored
//   in thread local storage and accessible to all methods used in the call path.
//   Of course, this assumes model doesn't launch thread and make calls to these objects.
//   If model needs to launch another thread of use thread pool where it needs to make calls to
//   the engine provided interface, we need more complex implementation and that is topic for future.
//   
//
// Messages : CustTransaction, CustAlertHisotry, CustPreferences, GlobalPreferences
// 
// CustTransaction    - Time series and primary input message on which models act (fact)
// CustPreferences    - Preference information maintained one record per customer (dimension)
// CustAlertHistory   - Time series information generated conditionally by models (fact) 
//                    - could be think as dimension if only recent message is maintained.
// ModelPreferences   - Common preferences to control model parameters. Only one instance per model is maintained.
// 
// Each CustTransaction message is associated with a customer (customerid field) and these 
// messages are partitioned by that field.
// The other messages CustAlertHistory, CustPreferences are also associated with a customer and these are  
// linked with CustTransaction message via common key - customer id. This allows to retrieve related messages
// without explicitly specifying in the higher level API calls.
// 

// Model: LowBalanceAlert
// Description: Generate low balance alerts based on current balance after each transaction performed by a customer
// Conditions: Generate an alert 
//   if current balance < 100 (configurable) and 
//   if low balance alert hasn't been issued in 48 hours and 
//   if customer preferences has minBalanceAlertOptOut is false.
//
// Inputs:
//   CustPreferences     - one record for each customer
//   CustAlertHistory    - one record for each customer (no history accessed even if system maintains)
//   ModelPreferences    - one global record where model level constants are configured
//   CustTransaction     - current transaction message on which this model makes decision
//                         no transaction history is used to make alert decision
//                         balance attribute in transaction reflects the current balance after this transaction
// Output:
//   CustAlertHistory    - a new entry is created - system decides on how to store/how many to keep based on policies
//   LowBalanceAlertRslt - a new entry is created - system decides what to do with the generated object
//

class LowBalanceAlertFactory(modelDef: ModelDef, nodeContext: NodeContext) extends ModelInstanceFactory(modelDef, nodeContext) {
  override def getModelName(): String = "LowBalanceAlert" // Model Name
  override def getVersion(): String = "0.0.1" // Model Version
  override def isValidMessage(msg: MessageContainerBase): Boolean = return msg.isInstanceOf[CustTransaction] // Check to fire the model
  override def createModelInstance(): ModelInstance = return new LowBalanceAlert(this) // Creating same type of object with given values 
  override def createResultObject(): ModelResultBase = new LowBalanceAlertResult() // ResultClass associated the model. Mainly used for Returning results as well as Deserialization
}

class LowBalanceAlertResult extends ModelResultBase {
  var custId: Long = 0;
  var branchId: Int = 0;
  var accNo: Long = 0;
  var curBalance: Double = 0
  var alertType: String = ""
  var triggerTime: Long = 0

  def withCustId(cId: Long): LowBalanceAlertResult = {
    custId = cId
    this
  }

  def withBranchId(bId: Int): LowBalanceAlertResult = {
    branchId = bId
    this
  }

  def withAccNo(aNo: Long): LowBalanceAlertResult = {
    accNo = aNo
    this
  }

  def withCurBalance(curBal: Double): LowBalanceAlertResult = {
    curBalance = curBal
    this
  }

  def withAlertType(alertTyp: String): LowBalanceAlertResult = {
    alertType = alertTyp
    this
  }

  def withTriggerTime(triggerTm: Long): LowBalanceAlertResult = {
    triggerTime = triggerTm
    this
  }

  override def toJson: List[org.json4s.JsonAST.JObject] = {
    val json = List(
      ("CustId" -> custId) ~
        ("BranchId" -> branchId) ~
        ("AccNo" -> accNo) ~
        ("CurBalance" -> curBalance) ~
        ("AlertType" -> alertType) ~
        ("TriggerTime" -> triggerTime))
    return json
  }

  override def toString: String = {
    compact(render(toJson))
  }

  override def get(key: String): Any = {
    if (key.compareToIgnoreCase("custId") == 0) return custId
    if (key.compareToIgnoreCase("branchId") == 0) return branchId
    if (key.compareToIgnoreCase("accNo") == 0) return accNo
    if (key.compareToIgnoreCase("curBalance") == 0) return curBalance
    if (key.compareToIgnoreCase("alertType") == 0) return alertType
    if (key.compareToIgnoreCase("triggerTime") == 0) return triggerTime
    return null
  }

  override def asKeyValuesMap: Map[String, Any] = {
    val map = scala.collection.mutable.Map[String, Any]()
    map("custid") = custId
    map("branchid") = branchId
    map("accno") = accNo
    map("curbalance") = curBalance
    map("alerttype") = alertType
    map("triggertime") = triggerTime
    map.toMap
  }

  override def Deserialize(dis: DataInputStream): Unit = {
    // BUGBUG:: Yet to implement
  }

  override def Serialize(dos: DataOutputStream): Unit = {
    // BUGBUG:: Yet to implement
  }
}

class LowBalanceAlert(factory: ModelInstanceFactory) extends ModelInstance(factory) {
  override def execute(txnCtxt: TransactionContext, outputDefault: Boolean): ModelResultBase = {
    // First check the preferences and decide whether to continue or not
    val gPref = GlobalPreferences.getRecentOrNew(Array("Type1"))
    val pref = CustPreferences.getRecentOrNew
    if (pref.minBalanceAlertOptout == false)
      return null

    // Check if at least min number of hours elapsed since last alert  
    val curDtTmInMs = RddDate.currentGmtDateTime
    val alertHistory = CustAlertHistory.getRecentOrNew
    if (curDtTmInMs.timeDiffInHrs(RddDate(alertHistory.alertDtTmInMs)) < gPref.minAlertDurationInHrs)
      return null

    // continue with alert generation only if balance from current transaction is less than threshold
    val rcntTxn = CustTransaction.getRecent
    if (rcntTxn.isEmpty || rcntTxn.get.balance >= gPref.minAlertBalance)
      return null

    val curTmInMs = curDtTmInMs.getDateTimeInMs
    // create new alert history record and persist (if policy is to keep only one, this will replace existing one)
    CustAlertHistory.build.withAlertDtTmInMs(curTmInMs).withAlertType("lowBalanceAlert").Save
    // results
    factory.createResultObject().asInstanceOf[LowBalanceAlertResult].withCustId(rcntTxn.get.custid).withBranchId(rcntTxn.get.branchid).withAccNo(rcntTxn.get.accno).withCurBalance(rcntTxn.get.balance).withAlertType("lowBalanceAlert").withTriggerTime(curTmInMs)
  }
}

// Model: LowBalanceAlert2
// Description: Generate low balance alerts based on current amount after each transaction performed by a customer
//              This model uses more complex logic than simply comparing the current balance
//
// Conditions: Generate an alert 
//   if current balance < 100 (configurable) and 
//   if number of days that balance < 100 in last 30 days is more than 3 days
//   if low balance alert hasn't been issued in 48 hours and 
//   if customer preferences has minBalanceAlertOptOut is false.
//
// Inputs:
//   CustPreferences     - one record for each customer
//   CustAlertHistory    - one record for each customer (no history accessed even if system maintains)
//   ModelPreferences    - one global record where model level constants are configured
//   CustTransaction     - current transaction message and history of messages in last 30 days
//                         balance attribute in transaction reflects the current balance after this transaction
// Output:
//   CustAlertHistory    - a new entry is created - system decides on how to store/how many to keep based on policies
//   LowBalanceAlertRslt - a new entry is created - system decides what to do with the generated object
//

class LowBalanceAlert2Factory(modelDef: ModelDef, nodeContext: NodeContext) extends ModelInstanceFactory(modelDef, nodeContext) {
  override def getModelName(): String = "LowBalanceAlert2" // Model Name
  override def getVersion(): String = "0.0.1" // Model Version
  override def isValidMessage(msg: MessageContainerBase): Boolean = return msg.isInstanceOf[CustTransaction] // Check to fire the model
  override def createModelInstance(): ModelInstance = return new LowBalanceAlert2(this) // Creating same type of object with given values 
  override def createResultObject(): ModelResultBase = new LowBalanceAlert2Result() // ResultClass associated the model. Mainly used for Returning results as well as Deserialization
}

class LowBalanceAlert2Result extends ModelResultBase {
  var custId: Long = 0;
  var branchId: Int = 0;
  var accNo: Long = 0;
  var curBalance: Double = 0
  var alertType: String = ""
  var triggerTime: Long = 0

  def withCustId(cId: Long): LowBalanceAlert2Result = {
    custId = cId
    this
  }

  def withBranchId(bId: Int): LowBalanceAlert2Result = {
    branchId = bId
    this
  }

  def withAccNo(aNo: Long): LowBalanceAlert2Result = {
    accNo = aNo
    this
  }

  def withCurBalance(curBal: Double): LowBalanceAlert2Result = {
    curBalance = curBal
    this
  }

  def withAlertType(alertTyp: String): LowBalanceAlert2Result = {
    alertType = alertTyp
    this
  }

  def withTriggerTime(triggerTm: Long): LowBalanceAlert2Result = {
    triggerTime = triggerTm
    this
  }

  override def toJson: List[org.json4s.JsonAST.JObject] = {
    val json = List(
      ("CustId" -> custId) ~
        ("BranchId" -> branchId) ~
        ("AccNo" -> accNo) ~
        ("CurBalance" -> curBalance) ~
        ("AlertType" -> alertType) ~
        ("TriggerTime" -> triggerTime))
    return json
  }

  override def toString: String = {
    compact(render(toJson))
  }

  override def get(key: String): Any = {
    if (key.compareToIgnoreCase("custId") == 0) return custId
    if (key.compareToIgnoreCase("branchId") == 0) return branchId
    if (key.compareToIgnoreCase("accNo") == 0) return accNo
    if (key.compareToIgnoreCase("curBalance") == 0) return curBalance
    if (key.compareToIgnoreCase("alertType") == 0) return alertType
    if (key.compareToIgnoreCase("triggerTime") == 0) return triggerTime
    return null
  }

  override def asKeyValuesMap: Map[String, Any] = {
    val map = scala.collection.mutable.Map[String, Any]()
    map("custid") = custId
    map("branchid") = branchId
    map("accno") = accNo
    map("curbalance") = curBalance
    map("alerttype") = alertType
    map("triggertime") = triggerTime
    map.toMap
  }

  override def Deserialize(dis: DataInputStream): Unit = {
    // BUGBUG:: Yet to implement
  }

  override def Serialize(dos: DataOutputStream): Unit = {
    // BUGBUG:: Yet to implement
  }
}

class LowBalanceAlert2(factory: ModelInstanceFactory) extends ModelInstance(factory) {
  override def execute(txnCtxt: TransactionContext, outputDefault: Boolean): ModelResultBase = {
    // First check the preferences and decide whether to continue or not
    val gPref = GlobalPreferences.getRecentOrNew(Array("Type1"))
    val pref = CustPreferences.getRecentOrNew
    if (pref.multiDayMinBalanceAlertOptout == false)
      return null

    // Check if at least min number of hours elapsed since last alert  
    val curDtTmInMs = RddDate.currentGmtDateTime
    val alertHistory = CustAlertHistory.getRecentOrNew
    if (curDtTmInMs.timeDiffInHrs(RddDate(alertHistory.alertDtTmInMs)) < gPref.minAlertDurationInHrs)
      return null

    // get history of transaction whose balance is less than minAlertBalance in last N days
    val lookBackTime = curDtTmInMs.lastNdays(gPref.numLookbackDaysForMultiDayMinBalanceAlert)
    val rcntTxns = CustTransaction.getRDD(lookBackTime, { trnsaction: MessageContainerBase =>
      {
        val trn = trnsaction.asInstanceOf[CustTransaction]
        trn.balance < gPref.minAlertBalance
      }
    })

    // quick check to whether it is worth doing group by operation
    if (rcntTxns.isEmpty || rcntTxns.count < gPref.maxNumDaysAllowedWithMinBalance)
      return null

    // compute distinct days in the retrieved history (number of days with balance less than minAlertBalance)
    // and check if those days meet the threshold
    val daysWhenBalanceIsLessThanMin = rcntTxns.groupBy({ trn => trn.date }).count.toInt
    if (daysWhenBalanceIsLessThanMin <= gPref.maxNumDaysAllowedWithMinBalance)
      return null

    // create new alert history record and persist (if policy is to keep only one, this will replace existing one)
    CustAlertHistory.build.withAlertDtTmInMs(curDtTmInMs.getDateTimeInMs).withAlertType("tooManyMinBalanceDays").withNumDays(daysWhenBalanceIsLessThanMin).Save

    val rcntTxn = CustTransaction.getRecent
    val curTmInMs = curDtTmInMs.getDateTimeInMs

    // results
    factory.createResultObject().asInstanceOf[LowBalanceAlert2Result].withCustId(rcntTxn.get.custid).withBranchId(rcntTxn.get.branchid).withAccNo(rcntTxn.get.accno).withCurBalance(rcntTxn.get.balance).withAlertType("lowBalanceAlert2").withTriggerTime(curTmInMs)
  }
}

// Model: LocationAlert
// Description: Generate alert based on locations of transaction transaction .
//              This model uses more complex logic of maintaining than simply comparing the current balance
//
// Conditions: Generate an alert 
//   if current location is more than < 100 (configurable) and 
//   if number of days that balance < 100 in last 30 days is more than 3 days
//   if low balance alert hasn't been issued in 48 hours and 
//   if customer preferences has minBalanceAlertOptOut is false.
//
// Inputs:
//   CustPreferences     - one record for each customer
//   CustAlertHistory    - one record for each customer (no history accessed even if system maintains)
//   ModelPreferences    - one global record where model level constants are configured
//   CustTransaction     - current transaction message and history of messages in last 30 days
//                         balance attribute in transaction reflects the current balance after this transaction
// Output:
//   CustAlertHistory    - a new entry is created - system decides on how to store/how many to keep based on policies
//   LowBalanceAlertRslt - a new entry is created - system decides what to do with the generated object
//

