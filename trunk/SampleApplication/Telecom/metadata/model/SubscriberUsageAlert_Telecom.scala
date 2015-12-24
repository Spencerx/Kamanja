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

// The following model generates an alert when an individual subscriber or account with sharedplan exceed usage
// above threshold as defined by a rate plan
// The following containers are used for lookup
// SubscriberGlobalPreferences : defines the thresholds in terms of percentage values
//                               at which we choose to call the message a warning or alert
// SubscriberInfo: individual subscriber information such as his phone number(msisdn), account number
//                 kind of ratePlan, activation date, preference to be notified or not
// SubscriberPlans: rate plan information such as kind of plan(shared or individual), plan limit, individual limit if any
// SubscriberAggregatedUsage: An object that maintains the aggregated usage for a given subscriber and for the current month
//                            Current Month is defined as whatever the month of the day, irrespective of activation date
//                            require enhancements if we aggregate the usage over each 30 days after activation
// AccountAggregatedUsage: An object that maintains the aggregated usage for a given account(could have
//                             multiple subscribers) and for the current month
//                            Current Month is defined as whatever the month of the day, irrespective of activation date
//                            require enhancements if we aggregate the usage over each 30 days after activation
// AccountInfo: individual account information such as account number, preference to be notified or not
//               Every subscriber is associated with an account
//     
package com.ligadata.models.samples.models

import com.ligadata.KvBase.{ Key, Value, TimeRange }
import com.ligadata.KamanjaBase._
import RddUtils._
import RddDate._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.apache.logging.log4j.{ Logger, LogManager }
import org.joda.time.format.DateTimeFormat
import org.joda.time.DateTime
import java.util.Locale
import java.io._
import com.ligadata.kamanja.metadata.ModelDef;

class SubscriberUsageAlertFactory(modelDef: ModelDef, nodeContext: NodeContext) extends ModelInstanceFactory(modelDef, nodeContext) {
  override def isValidMessage(msg: MessageContainerBase): Boolean = return msg.isInstanceOf[SubscriberUsage]
  override def createModelInstance(): ModelInstance = return new SubscriberUsageAlert(this)
  override def getModelName(): String = "System.SubscriberUsageAlert" // Model Name
  override def getVersion(): String = "0.0.1" // Model Version
  override def createResultObject(): ModelResultBase = new SubscriberUsageAlertResult()
}

class SubscriberUsageAlertResult extends ModelResultBase {
  var msisdn: Long = 0;
  var curUsage: Long = 0
  var alertType: String = ""
  var triggerTime: Long = 0

  lazy val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)

  def withMsisdn(cId: Long): SubscriberUsageAlertResult = {
    msisdn = cId
    this
  }

  def withCurusage(curTotalUsage: Long): SubscriberUsageAlertResult = {
    curUsage = curTotalUsage
    this
  }

  def withAlertType(alertTyp: String): SubscriberUsageAlertResult = {
    alertType = alertTyp
    this
  }

  def withTriggerTime(triggerTm: Long): SubscriberUsageAlertResult = {
    triggerTime = triggerTm
    this
  }

  override def toJson: List[org.json4s.JsonAST.JObject] = {
    val json = List(
      ("Msisdn" -> msisdn) ~
        ("Curusage" -> curUsage) ~
        ("AlertType" -> alertType) ~
        ("TriggerTime" -> triggerTime))
    return json
  }

  override def toString: String = {
    compact(render(toJson))
  }

  override def get(key: String): Any = {
    if (key.compareToIgnoreCase("msisdn") == 0) return msisdn
    if (key.compareToIgnoreCase("curUsage") == 0) return curUsage
    if (key.compareToIgnoreCase("alertType") == 0) return alertType
    if (key.compareToIgnoreCase("triggerTime") == 0) return triggerTime
    return null
  }

  override def asKeyValuesMap: Map[String, Any] = {
    val map = scala.collection.mutable.Map[String, Any]()
    map("msisdn") = msisdn
    map("curusage") = curUsage
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


class AccountUsageAlertResult extends ModelResultBase {
  var actNo: String = ""
  var curUsage: Long = 0
  var alertType: String = ""
  var triggerTime: Long = 0

  lazy val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)

  def withAct(aId: String): AccountUsageAlertResult = {
    actNo = aId
    this
  }

  def withCurusage(curTotalUsage: Long): AccountUsageAlertResult = {
    curUsage = curTotalUsage
    this
  }

  def withAlertType(alertTyp: String): AccountUsageAlertResult = {
    alertType = alertTyp
    this
  }

  def withTriggerTime(triggerTm: Long): AccountUsageAlertResult = {
    triggerTime = triggerTm
    this
  }

  override def toJson: List[org.json4s.JsonAST.JObject] = {
    val json = List(
      ("ActNo" -> actNo) ~
        ("Curusage" -> curUsage) ~
        ("AlertType" -> alertType) ~
        ("TriggerTime" -> triggerTime))
    return json
  }

  override def toString: String = {
    compact(render(toJson))
  }

  override def get(key: String): Any = {
    if (key.compareToIgnoreCase("actNo") == 0) return actNo
    if (key.compareToIgnoreCase("curUsage") == 0) return curUsage
    if (key.compareToIgnoreCase("alertType") == 0) return alertType
    if (key.compareToIgnoreCase("triggerTime") == 0) return triggerTime
    return null
  }

  override def asKeyValuesMap: Map[String, Any] = {
    val map = scala.collection.mutable.Map[String, Any]()
    map("actno") = actNo
    map("curusage") = curUsage
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

class SubscriberUsageAlert(factory: ModelInstanceFactory) extends ModelInstance(factory) {
  lazy val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)
  val df = DateTimeFormat.forPattern("yyyyMMdd").withLocale(Locale.US)
  
  private def getMonth(dt: String): Int = {
    val jdt = DateTime.parse(dt,df)
    jdt.monthOfYear().get()
  }

  private def getCurrentMonth : Int = {
    val jdt = new DateTime()
    jdt.monthOfYear().get()
  }

  private def dumpAppLog(logStr: String) = {
    val fw = new FileWriter("SubscriberUsageAlertAppLog.txt", true)
    try {
      fw.write(logStr + "\n")
    }
    finally fw.close()
  }

  override def execute(txnCtxt: TransactionContext, outputDefault: Boolean): ModelResultBase = {

    // Make sure current transaction has some data
    val rcntTxn = SubscriberUsage.getRecent
    if (rcntTxn.isEmpty) {
      return null
    }

    // Get the current subscriber, account info and global preferences
    val gPref = SubscriberGlobalPreferences.getRecentOrNew(Array("Type1"))
    val subInfo = SubscriberInfo.getRecentOrNew(Array(rcntTxn.get.msisdn.toString))
    val actInfo = AccountInfo.getRecentOrNew(Array(subInfo.actno))
    val planInfo  = SubscriberPlans.getRecentOrNew(Array(subInfo.planname))

    var logTag = "SubscriberUsageAlertApp(" + subInfo.msisdn + "," +  actInfo.actno + "): "

    // Get current values of aggregatedUsage
    val subAggrUsage = SubscriberAggregatedUsage.getRecentOrNew(Array(subInfo.msisdn.toString))
    val actAggrUsage = AccountAggregatedUsage.getRecentOrNew(Array(actInfo.actno))

    //dumpAppLog(logTag + "Before: Subscriber current month usage => " + subAggrUsage.thismonthusage + ",Account current month usage => " + actAggrUsage.thismonthusage)

    // Get current month
    val curDtTmInMs = RddDate.currentGmtDateTime
    val txnMonth = getMonth(rcntTxn.get.date.toString)
    val currentMonth = getCurrentMonth

    // planLimit values are supplied as GB. But SubscriberUsage record contains the usage as MB
    // So convert planLimit to MB
    val planLimit = planInfo.planlimit * 1000
    val indLimit  = planInfo.individuallimit * 1000


    //dumpAppLog(logTag + "Subscriber plan name => " + subInfo.planname + ",plan type => " + planInfo.plantype + ",plan limit => " + planLimit + ",individual limit => " + indLimit)
    dumpAppLog(logTag + "Subscriber usage in the current transaction  => " + rcntTxn.get.usage)

    // we are supposed to check whether the usage belongs to current month
    // if the usage doesn't belong to this month, we are supposed to ignore it
    // Here we let all the data pass through just to generate sample alerts no matter
    // what the actual usage data is
    //if( txnMonth != currentMonth ){
    //dumpAppLog(logTag + "The month value " + txnMonth + " is either older than current month " + currentMonth + " or incorrect,transaction ignored " + txnMonth)
    //  return null
    //}

    // aggregate account usage
    val actMonthlyUsage = actAggrUsage.thismonthusage + rcntTxn.get.usage
    actAggrUsage.withthismonthusage(actMonthlyUsage).Save

    // aggregate the usage 
    // aggregate individual subscriber usage
    val subMonthlyUsage = subAggrUsage.thismonthusage + rcntTxn.get.usage
    subAggrUsage.withthismonthusage(subMonthlyUsage).Save


    dumpAppLog(logTag + "After Aggregation: Subscriber current month usage => " + subMonthlyUsage + ",Account current month usage => " + actMonthlyUsage)

    val curTmInMs = curDtTmInMs.getDateTimeInMs
    
    // generate alerts if plan limits are exceeded based on planType
    planInfo.plantype match {
      case 1 => { // shared plans
	// exceeded plan limit
	if ( actMonthlyUsage > planLimit ){
	  if (actInfo.thresholdalertoptout == false) {
	    dumpAppLog(logTag + "Creating Alert for a shared plan account " + actInfo.actno)
	    dumpAppLog(logTag + "---------------------------")
	    return new AccountUsageAlertResult().withAct(actInfo.actno).withCurusage(actMonthlyUsage).withAlertType("pastThresholdAlert").withTriggerTime(curTmInMs)
	    //return null
	  }
	}
      }
      case 2 => { // individual plans
	// individual plan,  individual limit may have been exceeded
	if ( subMonthlyUsage > indLimit ){
	  if (subInfo.thresholdalertoptout == false) {
	    dumpAppLog(logTag + "Creating alert for individual subscriber account " + rcntTxn.get.msisdn)
	    dumpAppLog(logTag + "---------------------------")
	    return new SubscriberUsageAlertResult().withMsisdn(rcntTxn.get.msisdn).withCurusage(subMonthlyUsage).withAlertType("pastThresholdAlert").withTriggerTime(curTmInMs)
	    //return null
	  }
	}
      }
      case _ => {
	// unsupported plan type
	//dumpAppLog("Unknown planType => " + planInfo.plantype)
      }
    }
    return null
  }
}
