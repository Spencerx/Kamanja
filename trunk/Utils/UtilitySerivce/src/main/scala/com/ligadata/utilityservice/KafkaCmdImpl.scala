package com.ligadata.utilityservice

import kafka.producer.{ ProducerConfig, Producer, KeyedMessage, Partitioner }
import kafka.utils.VerifiableProperties
import org.apache.logging.log4j._


import java.util.Properties
/**
 * @author danielkozin
 */
object KafkaCmdImpl {

  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)  
  var partIdx: Int = 0
   
  /**
   * Exectute a Kafka command... for now this is only a Send to a kafka queue, in the future, this
   * maybe something else
   */
  def executeCommand (broker:String, topic: String, msg: String): String = {

    // Set up some properties for the Kafka Producer
    val props = new Properties()
    props.put("metadata.broker.list", broker);
    props.put("request.required.acks", "1")
 
    // create the producer object
    val producer = new Producer[AnyRef, AnyRef](new ProducerConfig(props))
    if (send(producer, topic, msg.getBytes("UTF8"), partIdx.toString.getBytes("UTF8"))) {
      partIdx = partIdx + 1 
      return "SUCCESS: Added message to Topic:"+topic
    } else {
      return "FAILURE: Failed to add message to Topic:"+topic
    } 
  }
  
  /**
   * send message
   */
  private def send(producer: Producer[AnyRef, AnyRef], topic: String, message: Array[Byte], partIdx: Array[Byte]): Boolean = {
    try {
      producer.send(new KeyedMessage(topic, partIdx, message))
      return true
    } catch {
      case e: Exception =>
        logger.error("Could not add to the queue due to an Exception "+ e.getMessage)
        e.printStackTrace()
        return false
    }
  }
  
}



