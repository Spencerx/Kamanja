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

package com.ligadata.ZooKeeper

import org.apache.curator.RetryPolicy
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.CreateMode
import scala.collection.mutable.ArrayBuffer
import com.ligadata.Exceptions.StackTrace
import org.apache.logging.log4j._

object CreateClient {
  val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)
  def CreateNodeIfNotExists(zkcConnectString: String, znodePath: String) = {
    var zkc: CuratorFramework = null
    try {
      zkc = createSimple(zkcConnectString)

      // Get all paths
      val allZNodePaths = new ArrayBuffer[String]
      var nFromPos = 0

      while (nFromPos != -1) {
        nFromPos = znodePath.indexOf('/', nFromPos + 1)
        if (nFromPos == -1) {
          allZNodePaths += znodePath
        } else {
          allZNodePaths += znodePath.substring(0, nFromPos)
        }
      }

      allZNodePaths.foreach(path => {
        if (zkc.checkExists().forPath(path) == null) {
          zkc.create().withMode(CreateMode.PERSISTENT).forPath(path, null);
        }
      })
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:" + stackTrace)
        throw new Exception("Failed to start a zookeeper session with(" + zkcConnectString + "): " + e.getMessage())
      }
    } finally {
      if (zkc != null) {
        zkc.close()
      }
    }
  }

  def createSimple(connectionString: String, sessionTimeoutMs: Int, connectionTimeoutMs: Int): CuratorFramework = {
    // these are reasonable arguments for the ExponentialBackoffRetry. The first
    // retry will wait 1 second - the second will wait up to 2 seconds - the
    // third will wait up to 4 seconds.
    val retryPolicy = new ExponentialBackoffRetry(1000, 3)

    // The simplest way to get a CuratorFramework instance. This will use default values.
    // The only required arguments are the connection string and the retry policy
    val curatorZookeeperClient = CuratorFrameworkFactory.newClient(connectionString, sessionTimeoutMs, connectionTimeoutMs, retryPolicy);
    curatorZookeeperClient.start
    var retry = true
    while (retry) {
      retry = false
      try {
        curatorZookeeperClient.getZookeeperClient.blockUntilConnectedOrTimedOut
      } catch {
        case e: java.lang.InterruptedException => {
          val stackTrace = StackTrace.ThrowableTraceString(e)
          logger.warn("Got InterruptedException. Going to retry after 50ms. StackTrace:" + stackTrace)
          Thread.sleep(50)
          retry = true
        }
      }
    }
    curatorZookeeperClient
  }

  def createSimple(connectionString: String): CuratorFramework = {
    // these are reasonable arguments for the ExponentialBackoffRetry. The first
    // retry will wait 1 second - the second will wait up to 2 seconds - the
    // third will wait up to 4 seconds.
    val retryPolicy = new ExponentialBackoffRetry(1000, 3)

    // The simplest way to get a CuratorFramework instance. This will use default values.
    // The only required arguments are the connection string and the retry policy
    val curatorZookeeperClient = CuratorFrameworkFactory.newClient(connectionString, retryPolicy);
    curatorZookeeperClient.start
    var retry = true
    while (retry) {
      retry = false
      try {
        curatorZookeeperClient.getZookeeperClient.blockUntilConnectedOrTimedOut
      } catch {
        case e: java.lang.InterruptedException => {
          val stackTrace = StackTrace.ThrowableTraceString(e)
          logger.warn("Got InterruptedException. Going to retry after 50ms. StackTrace:" + stackTrace)
          Thread.sleep(50)
          retry = true
        }
      }
    }
    curatorZookeeperClient
  }

  def createWithOptions(connectionString: String,
                        retryPolicy: RetryPolicy,
                        connectionTimeoutMs: Int,
                        sessionTimeoutMs: Int): CuratorFramework = {
    // using the CuratorFrameworkFactory.builder() gives fine grained control
    // over creation options. See the CuratorFrameworkFactory.Builder javadoc
    // details
    CuratorFrameworkFactory.builder()
      .connectString(connectionString)
      .retryPolicy(retryPolicy)
      .connectionTimeoutMs(connectionTimeoutMs)
      .sessionTimeoutMs(sessionTimeoutMs)
      .build();
  }
}
