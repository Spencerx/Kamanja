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

package com.ligadata.metadataapiservice

import com.ligadata.kamanja.metadata.ObjType._
import com.ligadata.kamanja.metadata._
import com.ligadata.kamanja.metadataload.MetadataLoad
import com.ligadata.MetadataAPI._
import com.ligadata.kamanja.metadata._
import com.ligadata.kamanja.metadata.MdMgr._

import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.Serialize._
import com.ligadata.ZooKeeper._
import org.apache.curator.framework._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import org.apache.logging.log4j._
import com.ligadata.Exceptions.StackTrace



object MetadataAPIServiceLeader {
  private[this] val LOG = LogManager.getLogger(getClass);
  private[this] val lock = new Object()
  private[this] var clusterStatus = ClusterStatus("", false, "", null)
  private[this] var zkLeaderLatch: ZkLeaderLatch = _
  private[this] var nodeId: String = _
  private[this] var zkConnectString: String = _
  private[this] var apiLeaderZkNodePath: String = _
  private[this] var zkSessionTimeoutMs: Int = _
  private[this] var zkConnectionTimeoutMs: Int = _
  private[this] var isLeader: Boolean = false
  private[this] var leaderNode: String = _

  // Here Leader can change or Participants can change
  private def EventChangeCallback(cs: ClusterStatus): Unit = {
    try{
      clusterStatus = cs
      isLeader = cs.isLeader
      leaderNode = cs.leader
      val isLeaderStr = if (cs.isLeader) "true" else "false"
      LOG.debug("NodeId:%s, IsLeader:%s, Leader:%s, AllParticipents:{%s}".format(cs.nodeId, isLeaderStr, cs.leader, cs.participants.mkString(",")))
    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("EventChangeCallback => Found exception. reason %s, message %s".format(e.getCause, e.getMessage)+"\nStackTrace:"+stackTrace)
      }
    }
  }


  def IsLeader: Boolean = {
    isLeader
  }

  def LeaderNode: String = {
    leaderNode
  }

  def Init(nodeId1: String, zkConnectString1: String, apiLeaderZkNodePath1: String, zkSessionTimeoutMs1: Int, zkConnectionTimeoutMs1: Int): Unit = {
    nodeId = nodeId1.toLowerCase
    zkConnectString = zkConnectString1
    apiLeaderZkNodePath = apiLeaderZkNodePath1
    zkSessionTimeoutMs = zkSessionTimeoutMs1
    zkConnectionTimeoutMs = zkConnectionTimeoutMs1

    if (zkConnectString != null && zkConnectString.isEmpty() == false && apiLeaderZkNodePath != null && apiLeaderZkNodePath.isEmpty() == false) {
      try {
        zkLeaderLatch = new ZkLeaderLatch(zkConnectString, apiLeaderZkNodePath, nodeId, EventChangeCallback, zkSessionTimeoutMs, zkConnectionTimeoutMs)
        zkLeaderLatch.SelectLeader
      } catch {
        case e: Exception => {
          LOG.error("Failed to initialize ZooKeeper Connection." + e.getMessage)
          throw e
        }
      }
    } else {
      LOG.error("Not connected to elect Leader and not distributing data between nodes.")
    }
  }

  def Shutdown: Unit = {
    if (zkLeaderLatch != null)
      zkLeaderLatch.Shutdown
    zkLeaderLatch = null
  }
}

