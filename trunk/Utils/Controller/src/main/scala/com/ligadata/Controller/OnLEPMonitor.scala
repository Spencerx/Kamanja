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

import java.io.File

import com.ligadata.AdaptersConfiguration.KafkaPartitionUniqueRecordValue
import com.ligadata.InputAdapters.KafkaSimpleConsumer
import com.ligadata.InputOutputAdapterInfo._
import com.ligadata.ZooKeeper.{ ZooKeeperListener, CreateClient }
import org.apache.curator.framework.CuratorFramework
import org.apache.logging.log4j.{ Logger, LogManager }

import scala.io.Source
import org.json4s.jackson.JsonMethods._
import scala.sys.process.{ ProcessIO, Process }
import com.ligadata.KamanjaBase.DataDelimiters

object SimpleStats extends CountersAdapter {

  override def addCntr(key: String, cnt: Long): Long = 0
  override def addCntr(cntrs: scala.collection.immutable.Map[String, Long]): Unit = {}
  override def getCntr(key: String): Long = 0
  override def getDispString(delim: String): String = ""
  override def copyMap: scala.collection.immutable.Map[String, Long] = null
}

object KamanjaMonitorConfig {
  var modelsData: List[Map[String, Any]] = null
}

object ExecContextObjImpl extends ExecContextObj {
  def CreateExecContext(input: InputAdapter, curPartitionKey: PartitionUniqueRecordKey, callerCtxt: InputAdapterCallerContext): ExecContext = {
    new ExecContextImpl(input, curPartitionKey, callerCtxt)
  }
}

// There are no locks at this moment. Make sure we don't call this with multiple threads for same object
class ExecContextImpl(val input: InputAdapter, val curPartitionKey: PartitionUniqueRecordKey, val callerCtxt: InputAdapterCallerContext) extends ExecContext {
  val agg = SampleAggregator.getNewSampleAggregator
  initializeModelsToMonitor(KamanjaMonitorConfig.modelsData, agg)
  agg.setIsLookingForSeed(true)

  private def initializeModelsToMonitor(modelsToMonitorInfo: List[Map[String, Any]], agg: SampleAggregator): Unit = {
    modelsToMonitorInfo.foreach(model => {
      val mName = model("name").asInstanceOf[String]
      agg.addModelToMonitor(mName)

      val keys = model("keys").asInstanceOf[List[String]]
      agg.addAggregationKey(mName, keys)

      val valCombos = model("displayValues").asInstanceOf[List[Map[String, List[String]]]]
      valCombos.foreach(value => {
        val dvals = value("values").asInstanceOf[List[String]]
        agg.addValueCombination(mName, dvals)
      })
    })

  }

  def execute(data: Array[Byte], format: String, uniqueKey: PartitionUniqueRecordKey, uniqueVal: PartitionUniqueRecordValue, readTmNanoSecs: Long, readTmMilliSecs: Long, ignoreOutput: Boolean, associatedMsg: String, delimiters: DataDelimiters): Unit = {

    if (format.equalsIgnoreCase("json")) {
      //if (data.charAt(0).toString.equals("{")) {
      agg.processJsonMessage(parse(new String(data)).values.asInstanceOf[Map[String, Any]])
    } else {
      agg.processCSVMessage(new String(data), uniqueVal.asInstanceOf[KafkaPartitionUniqueRecordValue].Offset)
    }
  }
}

class KamanjaMonitor {
  private val LOG = LogManager.getLogger(getClass)
  type OptionMap = Map[Symbol, Any]
  var isStarted: Boolean = false
  var zkNodeBasePath: String = null
  var zkConnectString: String = null
  var dataLoadCommand: String = null
  var zkDataPath: String = null
  var zkActionPath: String = null
  var seed: Long = 0
  var prc: Process = null

  val conf = new AdapterConfiguration
  val s_conf = new AdapterConfiguration
  var adapter: InputAdapter = _
  var s_adapter: InputAdapter = _

  var oAdaptersConfigs: List[AdapterConfiguration] = List[AdapterConfiguration]()
  var oAdapters: List[InputAdapter] = List[InputAdapter]()
  var sAdaptersConfigs: List[AdapterConfiguration] = List[AdapterConfiguration]()
  var sAdapters: List[InputAdapter] = List[InputAdapter]()

  /**
   * ActionOnActionChange - This method will get called when the /ligadata/monitor/action value in zookeeper is changed.
   *                        this method is passed as a callback into a zookeeper listener implementation.
   * @param receivedString String
   */
  private def ActionOnActionChange(receivedString: String): Unit = {
    if (receivedString.size == 1 && receivedString.toInt == 1) {
      LOG.debug("Monitoring turned ON")
      if (isStarted) {
        return
      }

      // Start all the neede Kafka Adapters for Output queues
      oAdaptersConfigs.foreach(conf => {
        // Create Kafka Consumer Here for the output queue
        val t_adapter = KafkaSimpleConsumer.CreateInputAdapter(conf, null, ExecContextObjImpl, SimpleStats)
        val t_adapterMeta: Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)] = t_adapter.getAllPartitionEndValues

        //  Initialize and start the adapter that is going to read the output queues here.
        val inputMeta = t_adapterMeta.map(partMeta => {
          val info = new StartProcPartInfo
          info._key = partMeta._1
          info._val = partMeta._2
          info._validateInfoVal = partMeta._2
          info
        }).toArray
        t_adapter.StartProcessing(inputMeta, false)
        oAdapters = oAdapters ::: List(t_adapter)
      })

      // Initialize and start the adapter that is going to read the status queue here.  - there is onlly 1 of these for now.
      var sub: Boolean = false
      s_adapter = KafkaSimpleConsumer.CreateInputAdapter(sAdaptersConfigs.head, null, ExecContextObjImpl, SimpleStats)
      val s_adapterMeta: Array[(PartitionUniqueRecordKey, PartitionUniqueRecordValue)] = s_adapter.getAllPartitionEndValues
      val s_inputMeta = s_adapterMeta.map(partMeta => {
        if (!sub) {
          partMeta._2.asInstanceOf[KafkaPartitionUniqueRecordValue].Offset = partMeta._2.asInstanceOf[KafkaPartitionUniqueRecordValue].Offset - 1
          SampleAggregator.setTT(partMeta._2.asInstanceOf[KafkaPartitionUniqueRecordValue].Offset)
          sub = true
        }

        val info = new StartProcPartInfo
        info._key = partMeta._1
        info._val = partMeta._2
        info._validateInfoVal = partMeta._2
        info
      }).toArray
      s_adapter.StartProcessing(s_inputMeta, false)
      sAdapters = sAdapters ::: List(s_adapter)

      // The adapters have been scheduled to run.. data will be coming in and aggregated  asynchrinously..  Flip the switch to externalize the collected
      // data to zookeeper (or another destination when we allow for it)
      isStarted = true

      // If it is required start loading input data into the Kamanja engine by executing a separate script.
      // for example: "java -jar /mnt/d3/demo_20150115/engine/SimpleKafkaProducer-0.1.0 --gz true --topics \"testin_1\" --threads 1 --topicpartitions 8 --brokerlist \"localhost:9092\" --files \"/mnt/d3/demo_20150115/demodata/msgdata/copdv1_eval_combined_25000.csv.gz\" --partitionkeyidxs \"1\"  --sleep 10 --format CSV"
      if (dataLoadCommand != null) {
        val pb = Process(dataLoadCommand)
        val pio = new ProcessIO(_ => (),
          stdout => scala.io.Source.fromInputStream(stdout).getLines.foreach(LOG.debug),
          err => { scala.io.Source.fromInputStream(err).getLines.foreach(LOG.error) })

        prc = pb.run(pio)
      }
    } else {
      LOG.debug("Monitoring turned OFF")
      isStarted = false

      // clean up monitor resources here.
      if (prc != null) {
        prc.destroy()
        prc = null
      }

      if (sAdapters.size > 0) {
        sAdapters.map(adapter => { adapter.Shutdown })
        sAdapters = List[InputAdapter]()
      }

      if (oAdapters.size > 0) {
        oAdapters.map(adapter => { adapter.Shutdown })
        oAdapters = List[InputAdapter]()
      }

      SampleAggregator.reset
    }
  }

  /**
   * start - start monitoring the objects described in the parameter file.  This method will terminate when a user
   *         turns off the appropriate flag in the zookeeper.
   */
  def start(inParms: Array[String]): Unit = {

    // Parse the input.
    val options = nextOption(Map(), inParms.toList)

    // Validate and set flags based on the input
    val parmFile = options.getOrElse('parm, null).asInstanceOf[String]
    if (parmFile == null) {
      LOG.error("Need input files as parameter")
      return
    }

    // Get the output
    val jsonparms = parse(Source.fromFile(parmFile).getLines.mkString).values.asInstanceOf[Map[String, Any]]

    // Get the ZooKeeper info and preform the zookeeper initialization
    val zkInfo1 = jsonparms.getOrElse("ZooKeeperInfo", null)
    if (zkInfo1 == null) {
      LOG.error("ERROR: missing Zookeeper info")
      return
    }
    val zkInfo = zkInfo1.asInstanceOf[Map[String, String]]
    zkNodeBasePath = zkInfo.getOrElse("ZooKeeperNodeBasePath", null)
    zkConnectString = zkInfo.getOrElse("ZooKeeperConnectString", null)
    dataLoadCommand = jsonparms.getOrElse("inputProcessString", null).toString

    val zkUpdateFreq = zkInfo.getOrElse("ZooKeeperUpdateFrequency", null)
    if (zkUpdateFreq == null) {
      LOG.error("ERROR: Invalid Freq")
      return
    }

    if (zkNodeBasePath == null || zkConnectString == null) {
      LOG.error("ERROR: Missing Zookeeper BASE PATH or CONNECT STRING parameter")
      return
    }

    // base paths
    zkActionPath = zkNodeBasePath + "/monitor/action"
    zkDataPath = zkNodeBasePath + "/monitor/data"

    // Just in case this is the first time to this zk instance.
    CreateClient.CreateNodeIfNotExists(zkConnectString, zkActionPath)
    val zcf: CuratorFramework = CreateClient.createSimple(zkConnectString)

    // Read the kafka queue informations
    val kafkaInfo = jsonparms.getOrElse("KafkaInfo", null).asInstanceOf[Map[String, List[Any]]]
    if (kafkaInfo == null) {
      LOG.warn("WARN: NOTHING TO MONITOR - No kafka sources specified")
      return
    }

    // configure kafka adapters configs
    configureKafkaAdapters(kafkaInfo)

    // Read the section that tells us which models to monitor and how.
    val modelsToMonitorInfo = jsonparms.getOrElse("ModelsToMonitor", null).asInstanceOf[Map[String, List[Any]]]

    if (modelsToMonitorInfo == null) {
      LOG.warn("WARN: NOTHING TO MONITOR - Models to monitor info is missing")
      return
    }

    // each model instance is an array.
    val modelsData = modelsToMonitorInfo.getOrElse("Models", null).asInstanceOf[List[Map[String, Any]]]

    if (modelsData == null) {
      LOG.warn("WARN: NOTHING TO MONITOR  - no models specified")
      return
    }
    KamanjaMonitorConfig.modelsData = modelsData

    // Establish a listener for the action field.  If that value changes, "ActinOnActionChange" callback function will be
    // called.
    val zkMonitorListener = new ZooKeeperListener
    zkMonitorListener.CreateListener(zkConnectString, zkActionPath, ActionOnActionChange, 3000, 3000)

    // Loop until its time to externalize data.
    while (true) {
      Thread.sleep(zkUpdateFreq.toInt)
      if (isStarted) {
        SampleAggregator.externalizeExistingData(zkConnectString, zkNodeBasePath, seed)
      }
    }

    // in case of stuff hitting the fan, free up the prc
    if (prc != null) {
      prc.destroy()
    }
    return ;
  }

  /**
   * configureKafkaAdapters - set up the oAdapters and sAdapter arrays here.
   * @param aConfigs  Map[String,List[Any]]
   */
  private def configureKafkaAdapters(aConfigs: Map[String, List[Any]]): Unit = {
    val statusQs: List[Map[String, Any]] = aConfigs.getOrElse("StatusQs", null).asInstanceOf[List[Map[String, Any]]]
    val outputQs: List[Map[String, Any]] = aConfigs.getOrElse("OutputQs", null).asInstanceOf[List[Map[String, Any]]]

    // Process all the status kafka Qs here
    if (statusQs != null) {
      statusQs.foreach(qConf => {
        val thisConf: AdapterConfiguration = new AdapterConfiguration
        thisConf.Name = qConf.getOrElse("Name", "").toString
        thisConf.formatOrInputAdapterName = qConf.getOrElse("Format", "").toString
        thisConf.className = qConf.getOrElse("ClassName", "").toString
        thisConf.jarName = qConf.getOrElse("JarName", "").toString
        thisConf.dependencyJars = qConf.getOrElse("DependencyJars", "").asInstanceOf[List[String]].toSet
        thisConf.adapterSpecificCfg = qConf.getOrElse("AdapterSpecificCfg", "").toString
        val delimiterString = qConf.getOrElse("DelimiterString", null)
        thisConf.keyAndValueDelimiter = qConf.getOrElse("KeyAndValueDelimiter", "").toString
        val fieldDelimiter = qConf.getOrElse("FieldDelimiter", null)
        thisConf.fieldDelimiter = if (fieldDelimiter != null) fieldDelimiter.toString else if (delimiterString != null) delimiterString.toString else "" 
        thisConf.valueDelimiter = qConf.getOrElse("ValueDelimiter", "").toString
        thisConf.associatedMsg = qConf.getOrElse("AssociatedMessage", "").toString

        // Ignore if any value in the adapter was not set.
        if (thisConf.Name.size > 0 &&
          thisConf.formatOrInputAdapterName.size > 0 &&
          thisConf.className.size > 0 &&
          thisConf.jarName.size > 0 &&
          thisConf.dependencyJars.size > 0 &&
          thisConf.dependencyJars.size > 0) {
          sAdaptersConfigs = sAdaptersConfigs ::: List[AdapterConfiguration](thisConf)
        }
      })
    }

    // Process all the output Kafka Qs
    if (outputQs != null) {
      outputQs.foreach(qConf => {
        val thisConf: AdapterConfiguration = new AdapterConfiguration
        thisConf.Name = qConf.getOrElse("Name", "").toString
        thisConf.formatOrInputAdapterName = qConf.getOrElse("Format", "").toString
        thisConf.className = qConf.getOrElse("ClassName", "").toString
        thisConf.jarName = qConf.getOrElse("JarName", "").toString
        thisConf.dependencyJars = qConf.getOrElse("DependencyJars", "").asInstanceOf[List[String]].toSet
        thisConf.adapterSpecificCfg = qConf.getOrElse("AdapterSpecificCfg", "").toString
        val delimiterString = qConf.getOrElse("DelimiterString", null)
        thisConf.keyAndValueDelimiter = qConf.getOrElse("KeyAndValueDelimiter", "").toString
        val fieldDelimiter = qConf.getOrElse("FieldDelimiter", null)
        thisConf.fieldDelimiter = if (fieldDelimiter != null) fieldDelimiter.toString else if (delimiterString != null) delimiterString.toString else "" 
        thisConf.valueDelimiter = qConf.getOrElse("ValueDelimiter", "").toString
        thisConf.associatedMsg = qConf.getOrElse("AssociatedMessage", "").toString

        // Ignore if any value in the adapter was not set.
        if (thisConf.Name.size > 0 &&
          thisConf.formatOrInputAdapterName.size > 0 &&
          thisConf.className.size > 0 &&
          thisConf.jarName.size > 0 &&
          thisConf.dependencyJars.size > 0 &&
          thisConf.dependencyJars.size > 0) {
          oAdaptersConfigs = oAdaptersConfigs ::: List[AdapterConfiguration](thisConf)
        }
      })
    }
  }

  /*
  * nextOption - parsing input options
   */
  private def nextOption(map: OptionMap, list: List[String]): OptionMap = {
    def isSwitch(s: String) = (s(0) == '-')
    list match {
      case Nil => map
      case "--parm" :: value :: tail =>
        nextOption(map ++ Map('parm -> value), tail)
      case option :: tail => {
        LOG.error("Unknown option " + option)
        sys.exit(1)
      }
    }
  }
}

/**
 * Created by dan on 1/9/15.
 */
object Monitor {
  def main(args: Array[String]): Unit = {

    val monitor: KamanjaMonitor = new KamanjaMonitor()
    monitor.start(args)

  }
}
