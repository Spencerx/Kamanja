
package com.ligadata.OnLEPBase

class AdapterConfiguration {
  var Name: String = _ // Name of the Adapter, KafkaQueue Name/MQ Name/File Adapter Logical Name/etc
  var Typ: String = _ // KafkaQueue/MQ/File
  var className: String = _ // Class where the Adapter can be loaded (Object derived from InputAdapterObj)
  var jarName: String = _  // Jar where the className can be found
  var dependencyJars: Set[String] = _ // All dependency Jars for jarName 
  var adapterSpecificTokens: Array[String] = _ // Rest of the tokens are only adapter specific 
}

trait CountersAdapter {
  def addCntr(key: String, cnt: Long): Long
  def addCntr(cntrs: scala.collection.immutable.Map[String, Long]): Unit
  def getCntr(key: String): Long 
  def getDispString(delim: String): String
  def copyMap: scala.collection.immutable.Map[String, Long]
}

// Input Adapter Object to create Adapter
trait InputAdapterObj {
  def CreateInputAdapter(inputConfig: AdapterConfiguration, output: Array[OutputAdapter], envCtxt: EnvContext, mkExecCtxt: MakeExecContext, cntrAdapter : CountersAdapter): InputAdapter
}

// Input Adapter
trait InputAdapter {
  val inputConfig: AdapterConfiguration // Configuration
  val output: Array[OutputAdapter] // Outputs adapters associated to this Input adapter
  val envCtxt: EnvContext // Environment Context

  def Category = "Input"
  def Shutdown(): Unit
}

// Output Adapter Object to create Adapter
trait OutputAdapterObj {
  def CreateOutputAdapter(inputConfig: AdapterConfiguration, cntrAdapter : CountersAdapter): OutputAdapter
}

// Output Adapter
trait OutputAdapter {
  val inputConfig: AdapterConfiguration // Configuration

  def send(message: String, partKey: String): Unit
  def send(message: Array[Byte], partKey: Array[Byte]): Unit
  def Shutdown(): Unit
  def Category = "Output"
}

trait ExecContext {
  val input: InputAdapter
  val curPartitionId: Int
  val output: Array[OutputAdapter]
  val envCtxt: EnvContext

  def execute(data: String, readTmNanoSecs: Long, readTmMilliSecs: Long): Unit
}

trait MakeExecContext {
  def CreateExecContext(input: InputAdapter, curPartitionId: Int, output: Array[OutputAdapter], envCtxt: EnvContext): ExecContext
}

