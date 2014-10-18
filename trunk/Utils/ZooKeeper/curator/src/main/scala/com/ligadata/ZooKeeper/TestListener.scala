package com.ligadata.ZooKeeper

import org.apache.curator.RetryPolicy
import org.apache.curator.framework._
import org.apache.curator.framework.recipes.cache._
import org.apache.curator.framework.api._
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.utils._

import java.util.concurrent.locks._

import org.apache.log4j._

object TestListener {

  val loggerName = this.getClass.getName
  lazy val logger = Logger.getLogger(loggerName)
  val contentLock = new ReentrantLock(true);
  val contentAvailable = contentLock.newCondition();

  var content:Object = null

  def setContent(data:Object) = {
    //logger.debug("Received new data: " + data);
    contentLock.lock();
    try {
      content = data
      contentAvailable.signalAll()
    } finally {
      contentLock.unlock()
    }
  }

  def getContent() : Object = {
    contentLock.lock();
    try {
      while (content == null) {
        contentAvailable.await()
      }
      return content
    } finally {
      contentLock.unlock()
    }
  }

  def LoopForever = {
    while(true){
      content = null
      val newData = getContent().asInstanceOf[ChildData]
      logger.debug("New data received => " + new String(newData.getData()))
    }
  }

  def main(args: Array[String]) = {
    val retryPolicy = new ExponentialBackoffRetry(1000, 3)
    val curatorZookeeperClient = CuratorFrameworkFactory.newClient("localhost:2181", retryPolicy)
    curatorZookeeperClient.start
    curatorZookeeperClient.getZookeeperClient.blockUntilConnectedOrTimedOut
    val znodePath = "/ligadata/models"
    val originalData = new String(curatorZookeeperClient.getData.forPath(znodePath)) // This should be "Some data"
    logger.trace("original data => " + originalData)
    /* Zookeeper NodeCache service to get properties from ZNode */
    val nodeCache = new NodeCache(curatorZookeeperClient, znodePath)
    nodeCache.getListenable.addListener(new NodeCacheListener {
      @Override
      def nodeChanged = {
	try {
	  val dataFromZNode = nodeCache.getCurrentData
	  setContent(dataFromZNode)
	  // This should be some new data after it is changed in the Zookeeper ensemble
	  //val newData = new String(currentData.getData) 
	} catch {
	  case ex: Exception => {
	    logger.error("Exception while fetching properties from zookeeper ZNode, reason " + ex.getCause)
	  }
	}
      }})
    nodeCache.start
    logger.setLevel(Level.TRACE);
    LoopForever
   }
}