package com.ligadata.filedataprocessor

import java.io.{IOException, File, PrintWriter}
import java.nio.file.{Path, FileSystems}

import com.ligadata.Exceptions.{InternalErrorException, MissingArgumentException}
import org.apache.logging.log4j.{ Logger, LogManager }

/**
 * Created by danielkozin on 9/24/15.
 */
class DirectoryListener {

}

object LocationWatcher {
  lazy val loggerName = this.getClass.getName
  lazy val logger = LogManager.getLogger(loggerName)
  def main (args: Array[String]) : Unit = {

      if (args.size == 0 || args.size > 1) {
        logger.error("Smart File Consumer requires a configuration file as its argument")
        return
      }

      // Read the config and figure out how many consumers to start
      var config = args(0)
      var properties = scala.collection.mutable.Map[String,String]()

      val lines = scala.io.Source.fromFile(config).getLines.toList
      lines.foreach(line => {
        var lProp = line.split("=")
        try {
          properties(lProp(0)) = lProp(1)
        } catch {
          case iobe: IndexOutOfBoundsException => {
            logger.error("SMART FILE CONSUMER: Invalid format in the configuration file " + config)
            logger.error("SMART FILE CONSUMER: unable to determine the value for property " + lProp(0))
            return
          }
        }

      })

      var numberOfProcessors = properties(SmartFileAdapterConstants.NUMBER_OF_FILE_CONSUMERS).toInt
      var processors: Array[FileProcessor] = new Array[FileProcessor](numberOfProcessors)
      var threads: Array[Thread] = new Array[Thread](numberOfProcessors)
      var path: Path= null
      try {
         path = FileSystems.getDefault().getPath(properties(SmartFileAdapterConstants.DIRECTORY_TO_WATCH))
      } catch {
        case e: IOException => {
          println ("Unable to find the directory to watch")
          return
        }
      }

      logger.info("Starting "+ numberOfProcessors+" file consumers, reading from "+ path)
    try {
      for (i <- 1 to numberOfProcessors) {
        var processor = new FileProcessor(path,i)
        processor.init(properties)
        val watch_thread = new Thread(processor)
        watch_thread.start
      }

    } catch {
      case e: Exception => {
        logger.error("ERROR in starting SMART FILE CONSUMER "+ e)
        return
      }
    }
  }
}
