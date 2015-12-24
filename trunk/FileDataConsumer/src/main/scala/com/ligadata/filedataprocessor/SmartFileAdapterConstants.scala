package com.ligadata.filedataprocessor

/**
 * Created by danielkozin on 9/28/15.
 */
object SmartFileAdapterConstants {

  val DIRECTORY_TO_WATCH = "dirToWatch"
  val DIRECTORY_TO_MOVE_TO = "moveToDir"
  val MSG_FORMAT = "msgFormat"
  val MSG_SEPARATOR = "messageSeparator"
  val FIELD_SEPARATOR = "fieldSeparator"
  val VALUE_SEPARATOR = ""
  val KV_SEPARATOR = "kvSeparator"
  val NUMBER_OF_FILE_CONSUMERS = "fileConsumers"
  val PAR_DEGREE_OF_FILE_CONSUMER = "workerdegree"
  val WORKER_BUFFER_SIZE = "workerbuffersize"
  val METADATA_CONFIG_FILE = "metadataConfigFile"
  val KAFKA_BROKER = "kafkaBroker"
  val KAFKA_TOPIC = "topic"
  val KAFKA_STATUS_TOPIC = "statusTopic"
  val MESSAGE_NAME = "messageName"
  val READY_MESSAGE_MASK = "readyMessageMask"
  val KAFKA_ERROR_TOPIC = "errorTopic"
  val STATUS_FREQUENCY = "statusFrequency"
  val KAFKA_ACK = "kafka_ack"
  val KAFKA_BATCH = "kafka_batch"
  val ZOOKEEPER_IGNORE = "ignore_zookeeper"
  val MAX_MEM = "maxAllowedMemory"
  val THROTTLE_TIME = "throttle_ms"

  val KAFKA_LOAD_STATUS = "Kafka_Load_Result,"
  val TOTAL_FILE_STATUS = "File_Total_Result,"
  val CORRUPTED_FILE = "Corrupted_File_Detected,"

}
