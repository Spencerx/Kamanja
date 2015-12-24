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

package com.ligadata.Controller

import com.ligadata.ZooKeeper.CreateClient
import org.apache.curator.framework.CuratorFramework
import org.apache.logging.log4j.{ Logger, LogManager }
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import scala.collection.mutable.ArrayBuffer

/**
 * Sample Aggregator Object
 */
object SampleAggregator {
  private val LOG = LogManager.getLogger(getClass)
  val aggs = new ArrayBuffer[SampleAggregator]()
  var aggsLock = new Object
  var zkConnectString: String = null
  var tt: Long = 0
  var seed:Long = 0

  var zcf: CuratorFramework = null
  def setTT(v: Long): Unit = {tt = v}
  def setZkConnectString(inVal: String):Unit = {zkConnectString = inVal}
  def getZkConnectString(): String = {zkConnectString}

  /**
   * getNewSampleAggregator - get a new instance of an aggregator
   * @return SampleAggregator
   */
  def getNewSampleAggregator : SampleAggregator = {
    val agg = new SampleAggregator
    aggs += agg
    return agg
  }

  /**
   * reset - reset individual fields anre release zookeeper resources
   */
  def reset():Unit = {
    if (zcf != null)
      zcf.close
    zcf = null
    aggsLock.synchronized {
      aggs.clear
    }
  }

  /**
   * openZkConnection
   * @param zkConnectString String
   * @param zkBasePath String
   */
  def openZkConnection(zkConnectString: String, zkBasePath: String) : Unit = {
    if (zcf == null) {
      val zkDataPath = zkBasePath + "/monitor/data"
      CreateClient.CreateNodeIfNotExists(zkConnectString, zkDataPath)
      zcf = CreateClient.createSimple(zkConnectString)
    }
  }


  /**
   * externalizeExistingData - externalize data collected up to this point to the persistence location.
   * @param zkConnectString
   * @param zkBasePath
   * @param seed
   */
  def externalizeExistingData(zkConnectString: String, zkBasePath: String, seed: Long):Unit = {
    // make a copy for thread safety

    var dataArrays: ArrayBuffer[SampleAggregator] = null
    aggsLock.synchronized {
      dataArrays = aggs.asInstanceOf[ArrayBuffer[SampleAggregator]]
    }
    val finalData: scala.collection.mutable.Map[String,scala.collection.mutable.Map[List[String],Int]] =
      scala.collection.mutable.Map[String,scala.collection.mutable.Map[List[String],Int]]()

    val finalKeys: scala.collection.mutable.Map[String,List[String]] = scala.collection.mutable.Map[String,List[String]]()
    var aggKeys = Map[String,List[String]]()
    var totalProcessed: Long = 0
    var statusData = Map[Int,Long]()
    var totalMsgProcessed: Long = 0

    // Aggregate all values - go through the agg array frist.
    dataArrays.foreach(agg =>{
      agg.lock.synchronized {
        val aggData = agg.aggregatedData.toMap
        aggKeys = agg.keysToMonitor.toMap
        statusData = agg.aggregatedStatusData.toMap

        // Total Messages Processed - number of message out of KamanjaEngine output kafka queues
        totalMsgProcessed = totalMsgProcessed + agg.getMsgProcessed()

        // aggregate values reported by the status queues.
        statusData.foreach(elem => {
          totalProcessed = totalProcessed + elem._2
        })

        // Create a list of keys being reported on for reporting purposes.
        aggKeys.foreach(model => {
          if (!finalKeys.contains(model._1)) {
            finalKeys(model._1) = model._2
          }
        })

        // Aggregate actual engine data.
        aggData.foreach(model => {
          val tModelMap = if (!finalData.contains(model._1)) scala.collection.mutable.Map[List[String],Int]() else finalData(model._1)
          //add each element one by one.
          model._2.toMap.foreach(kv => {
            if (!tModelMap.contains(kv._1)) {
              tModelMap(kv._1) = kv._2
            } else {
              tModelMap(kv._1) = tModelMap(kv._1) + kv._2
            }
          })
          finalData(model._1) = tModelMap
        })
      }
    })

    // Create a Monitored Data message in Json Format.
    val json1 =
     ("Results" ->
       (("Models" -> finalData.map(mdl =>
         (("ModelName" -> mdl._1) ~
           ("Key" -> (aggKeys(mdl._1).toList :+ "Counter")) ~
           ("Values" -> (mdl._2.toList.map(vals => ("KeyValue" -> (vals._1.toList :+ vals._2.toString)))))))) ~
         ("TransactionsProcessed" -> (totalProcessed - this.seed)) ~
         ("AlertsQueued" -> totalMsgProcessed) ~
         ("AverageAlertsLatency" -> "0"))
       )

    val jsonStr = compact(render(json1))

    openZkConnection(zkConnectString, zkBasePath)

    // Data has been collected - externalize it.
    if (zcf != null) {
       val zkDataPath = zkBasePath + "/monitor/data"
       zcf.setData.forPath(zkDataPath,jsonStr.getBytes)
    } else {
      LOG.error("Error - No zookeeper connection.")
    }
  }
}


class SampleAggregator  {

  private val LOG = LogManager.getLogger(getClass)
  var lock: Object = new Object
  private var isLocked: Boolean = false
  var totalMsg: Long = 0
  var isLookingForSeed: Boolean = false
  var seed: Long = 0
  var models: List[String] = List[String]()
  var keysToMonitor: scala.collection.mutable.Map[String,List[String]] = scala.collection.mutable.Map[String,List[String]]()
  var valuesToDisplay: scala.collection.mutable.Map[String,List[List[String]]] = scala.collection.mutable.Map[String,List[List[String]]]()
  var aggregatedData: scala.collection.mutable.Map[String,scala.collection.mutable.Map[List[String],Int]] = scala.collection.mutable.Map[String,scala.collection.mutable.Map[List[String],Int]]()
  var aggregatedStatusData: scala.collection.mutable.Map[Int,Long] = scala.collection.mutable.Map[Int,Long]()
  var seedStatusData: scala.collection.mutable.Map[Int,Long] = scala.collection.mutable.Map[Int,Long]()

  def setIsLookingForSeed(in: Boolean):Unit = {isLookingForSeed = in}
  def getSeed():Long = {seed}
  def getMsgProcessed():Long = {totalMsg}
  def getData = aggregatedData.toMap

  /**
   * processCSVMessage - Used to process messages out of the standard KamanjaEngine status queue.
   * @param msg - message
   * @param seedOffset - offset of the message that contains for this Status Queue.
   */
  def processCSVMessage(msg: String, seedOffset: Long): Unit = lock.synchronized {

    var total: Long = 0
    val parsedMsg = msg.split(",")
    val payloads = parsedMsg(3)
    val parsedPayloads = payloads.split("~")

    parsedPayloads.foreach(load => {
      if(load.startsWith("Input/")) {
        val indVals = load.split("->")
        total = total + indVals(1).trim.toLong
      } 
    })

    seed = 0
    if (SampleAggregator.tt == seedOffset) {
      LOG.debug("Using "+ total +"as seed")
      SampleAggregator.seed = total
      return
    } else {
      aggregatedStatusData(parsedMsg(1).trim.toInt) = total
    }


  }

  /**
   * processJsonMessage - this will process a message out of the KamanjaEngine output kafka queue..  They are guaranteed
   * to be JSON format as defined by the existing KamanjaEngine documentation.
   * @param msg Map[String,Any] - The standard Map representation of the JSON string.
   */
  def processJsonMessage(msg: Map[String,Any]): Unit = lock.synchronized {
    totalMsg = totalMsg + 1
    val modelResults = msg.getOrElse("ModelsResult",null).asInstanceOf[List[Map[String,Any]]]
    if (modelResults == null) {
      return
    }

    modelResults.foreach(modelData => {
      val modelName = modelData.getOrElse("ModelName",null).asInstanceOf[String]
      val outputArray = modelData.getOrElse("output",null).asInstanceOf[List[Map[String,String]]]

      // For now each model can have 1 set of Keys... we'll change it later
      val keys: List[String] = keysToMonitor(modelName)
      val modelAggregate: scala.collection.mutable.Map[List[String],Int] = aggregatedData(modelName)
      var viewKey: List[String] = List[String]()
      outputArray.foreach(outObject => {
        keys.foreach(key => {
          val partialView = outObject.getOrElse("Name",null).asInstanceOf[String]
          if (partialView.equals(key)) {
            val oValue: String = outObject.getOrElse("Value",null)
            viewKey = viewKey ::: List(oValue)
          }
        })
      })

      if (modelAggregate.contains(viewKey)) {
        modelAggregate(viewKey) = modelAggregate(viewKey) + 1
      } else {
        modelAggregate(viewKey) = 1
      }

      aggregatedData(modelName) = modelAggregate
    })
  }

  /**
   * addModelToMonitor - called by initialization routing to set internal maps
   * @param modelName String
   */
  def addModelToMonitor(modelName: String): Unit = {
    if (!keysToMonitor.contains(modelName) ) {
      keysToMonitor(modelName) = List[String]()
      models :::=  List(modelName)
      aggregatedData(modelName) = scala.collection.mutable.Map[List[String],Int]()
    }

    if (!valuesToDisplay.contains(modelName) ) {
      valuesToDisplay(modelName) = List[List[String]]()
    }
  }

  /**
   * addAggregationKey - called by initialization routing to set internal maps
   * @param modelName String
   * @param keys List[String]
   */
  def addAggregationKey(modelName: String, keys: List[String]): Unit = {
    if (!keysToMonitor.contains(modelName)) {
      println("ERROR: unkown model")
      return
    }
    keysToMonitor(modelName) = keys

  }

  /**
   * addValueCombination - called by initialization routing to set internal maps
   * @param modelName String
   * @param vals List[String]
   */
  def addValueCombination(modelName: String, vals: List[String]): Unit = {
    if (!valuesToDisplay.contains(modelName) ) {
      println("ERROR: unkown model")
      return
    }
    val currentVals = valuesToDisplay(modelName)
    val newVals: List[List[String]] = vals.asInstanceOf[List[String]] ::currentVals.asInstanceOf[List[List[String]]]
    valuesToDisplay(modelName) = newVals

    val tempMap: scala.collection.mutable.Map[List[String],Int] = aggregatedData(modelName)
    val key = keysToMonitor(modelName)
    if (!tempMap.contains(key)) {
      tempMap(key) = 0
    }
  }


  /**
   * printMonitoredData - Servicability call, will externalize the Models/Keys being monitored by this Aggregator
   */
  def printMonitoredData(): Unit = {
    models.foreach(model => {
      LOG.debug("Monitoring Model " + model + " with following parameters")
      keysToMonitor(model).foreach(key => {
        LOG.debug(" key:" + key + " ")
      })
      println()
    })
  }

}
