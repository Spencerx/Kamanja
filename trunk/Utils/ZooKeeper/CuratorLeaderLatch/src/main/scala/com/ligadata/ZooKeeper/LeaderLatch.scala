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

import org.apache.curator.framework._
import org.apache.curator.framework.recipes.cache._
import org.apache.curator.framework.api._
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.framework.recipes.leader.LeaderLatch
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.apache.zookeeper.WatchedEvent
import org.apache.curator.RetryLoop
import java.util.concurrent.Callable
import org.apache.logging.log4j._
import scala.collection.JavaConverters._
import org.apache.curator.framework.api.CuratorEventType._;
import com.ligadata.Exceptions.StackTrace
import org.apache.logging.log4j._

case class ClusterStatus(nodeId: String, isLeader: Boolean, leader: String, participants: Iterable[String])

class ZkLeaderLatch(val zkcConnectString: String, val leaderPath: String, val nodeId: String, val EventChangeCallback: (ClusterStatus) => Unit, sessionTimeoutMs: Int = 1000, connectionTimeoutMs: Int = 30000) {
  private var curatorFramework: CuratorFramework = null
  private var leaderLatch: LeaderLatch = _
  private val LOG = LogManager.getLogger(getClass);
  private var clstStatus: ClusterStatus = _
  private var isShuttingDown: Boolean = false
  private[this] val lock = new Object()
  
   val loggerName = this.getClass.getName
  val logger = LogManager.getLogger(loggerName)

  def SetIsShuttingDown(isIt: Boolean) = lock.synchronized {
    isShuttingDown = isIt
  }

  class ZkLeaderLatchListener extends LeaderLatchListener {
    override def isLeader() {
      LOG.debug("Got leadership");
      updateClusterStatus
    }

    override def notLeader() {
      LOG.debug("Lost leadership");
      updateClusterStatus
    }
  }

  def Shutdown: Unit = {
    SetIsShuttingDown(true)
    if (leaderLatch != null)
      leaderLatch.close
    leaderLatch = null
    if (curatorFramework != null)
      curatorFramework.close
    curatorFramework = null
  }

  def getClsuterStatus = clstStatus

  def SelectLeader = {
    try {
      // Make sure we have the path created before we execute this
      CreateClient.CreateNodeIfNotExists(zkcConnectString, leaderPath)

      curatorFramework = CreateClient.createSimple(zkcConnectString, sessionTimeoutMs, connectionTimeoutMs)

      leaderLatch = new LeaderLatch(curatorFramework, leaderPath, nodeId, LeaderLatch.CloseMode.NOTIFY_LEADER)

      leaderLatch.addListener(new ZkLeaderLatchListener)

      leaderLatch.start()

      watchLeaderChildren()
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        logger.debug("StackTrace:"+stackTrace)
        throw new Exception("Failed to start a zookeeper session with(" + zkcConnectString + "): " + e.getMessage())
      }
    }
  }

  private def updateClusterStatus {
    try {
      val participants = leaderLatch.getParticipants.asScala

      clstStatus = ClusterStatus(leaderLatch.getId, leaderLatch.hasLeadership, leaderLatch.getLeader.getId, participants.map(_.getId))

      val isLeader = if (clstStatus.isLeader) "true" else "false"

      // Do something with cluster status (log leadership change, etc)
      LOG.info("NodeId:%s, IsLeader:%s, Leader:%s, AllParticipents:{%s}".format(clstStatus.nodeId, isLeader, clstStatus.leader, clstStatus.participants.mkString(",")))
      if (EventChangeCallback != null)
        EventChangeCallback(clstStatus)
    } catch {
      case e: Exception => {
        
        LOG.error("Leader callback has some error. Reason:%s, Message:%s".format(e.getCause, e.getMessage))
      }
    }
  }

  private def watchLeaderChildren() {
    curatorFramework.getChildren.usingWatcher(
      new CuratorWatcher {
        def process(event: WatchedEvent) {
          if (isShuttingDown == false) {
            updateClusterStatus
            // Re-set watch
            curatorFramework.getChildren.usingWatcher(this).inBackground.forPath(leaderPath)
          }
        }
      }).inBackground.forPath(leaderPath)
  }
}

object ZkLeaderLatchTest {
  private type OptionMap = Map[Symbol, Any]
  private val LOG = LogManager.getLogger(getClass);

  private def nextOption(map: OptionMap, list: List[String]): OptionMap = {
    def isSwitch(s: String) = (s(0) == '-')
    list match {
      case Nil => map
      case "--zkconnectstring" :: value :: tail =>
        nextOption(map ++ Map('zkconnectstring -> value), tail)
      case "--leaderpath" :: value :: tail =>
        nextOption(map ++ Map('leaderpath -> value), tail)
      case "--nodeid" :: value :: tail =>
        nextOption(map ++ Map('nodeid -> value), tail)
      case option :: tail => {
        LOG.error("Unknown option " + option)
        sys.exit(1)
      }
    }
  }

  private def PrintUsage(): Unit = {
    LOG.debug("Usage:")
    LOG.debug("    --zkconnectstring <ConnectString> --leaderpath <LeaderPathString> --nodeid <UniqueNodeIdString>")
  }

  def main(args: Array[String]): Unit = {
    if (args.length == 0) {
      PrintUsage()
      return
    }

    val options = nextOption(Map(), args.toList)
    val zkcConnectString = options.getOrElse('zkconnectstring, "").toString.replace("\"", "").trim
    if (zkcConnectString.size == 0) {
      LOG.debug("Need zkcConnectString")
      return
    }

    val leaderPath = options.getOrElse('leaderpath, "").toString.replace("\"", "").trim
    if (leaderPath.size == 0) {
      LOG.debug("Need leaderPath")
      return
    }

    val nodeId = options.getOrElse('nodeid, "").toString.replace("\"", "").trim
    if (nodeId.size == 0) {
      LOG.debug("Need nodeId")
      return
    }

    val ll = new ZkLeaderLatch(zkcConnectString, leaderPath, nodeId, null)
    ll.SelectLeader

    LOG.debug("Sleeping for 365 days or CTRL + C")
    Thread.sleep(365L * 24 * 60 * 60 * 1000L)
  }
}


