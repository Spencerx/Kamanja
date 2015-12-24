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

import com.ligadata.KamanjaBase._
import com.ligadata.KvBase.TimeRange
import RddUtils._
import RddDate._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import java.io.{ DataInputStream, DataOutputStream }
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.kamanja.metadata.ModelDef;

class LowBalanceAlertFactory(modelDef: ModelDef, nodeContext: NodeContext) extends ModelInstanceFactory(modelDef, nodeContext) {
  override def isValidMessage(msg: MessageContainerBase): Boolean = return msg.isInstanceOf[TransactionMsg]
  override def createModelInstance(): ModelInstance = return new LowBalanceAlert(this)
  override def getModelName(): String = "LowBalanceAlert" // Model Name
  override def getVersion(): String = "0.0.1" // Model Version
  override def createResultObject(): ModelResultBase = new LowBalanceAlertResult()
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
  // private[this] val LOG = LogManager.getLogger(getClass);
  override def execute(txnCtxt: TransactionContext, outputDefault: Boolean): ModelResultBase = {
    // First check the preferences and decide whether to continue or not
    val gPref = GlobalPreferences.getRecentOrNew(Array("Type1"))
    val pref = CustPreferences.getRecentOrNew
    if (pref.minbalancealertoptout == true) {
      return null
    }

    // Check if at least min number of hours elapsed since last alert  
    val curDtTmInMs = RddDate.currentGmtDateTime
    val alertHistory = CustAlertHistory.getRecentOrNew
    if (curDtTmInMs.timeDiffInHrs(RddDate(alertHistory.alertdttminms)) < gPref.minalertdurationinhrs) {
      return null
    }

    // continue with alert generation only if balance from current transaction is less than threshold
    val rcntTxn = TransactionMsg.getRecent
    if (rcntTxn.isEmpty) {
      return null
    }
    
    if (rcntTxn.isEmpty || rcntTxn.get.balance >= gPref.minalertbalance) {
      return null
    }

    val curTmInMs = curDtTmInMs.getDateTimeInMs
    // create new alert history record and persist (if policy is to keep only one, this will replace existing one)
    CustAlertHistory.build.withalertdttminms(curTmInMs).withalerttype("lowbalancealert").Save
    // results
    new LowBalanceAlertResult().withCustId(rcntTxn.get.custid).withBranchId(rcntTxn.get.branchid).withAccNo(rcntTxn.get.accno).withCurBalance(rcntTxn.get.balance).withAlertType("lowBalanceAlert").withTriggerTime(curTmInMs)
  }
}

