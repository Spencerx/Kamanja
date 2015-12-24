
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

package com.ligadata.KamanjaManager

import org.apache.logging.log4j.{ Logger, LogManager }

object ManagerUtils {
  private[this] val LOG = LogManager.getLogger(getClass);

  def getComponentElapsedTimeStr(compName: String, UniqVal: String, readTmInNs: Long, compStartTimInNs: Long): String = {
    val curTmInNs = System.nanoTime
    var elapsedTimeFromRead = (curTmInNs - readTmInNs) / 1000
    if (elapsedTimeFromRead <= 0) elapsedTimeFromRead = 1
    var elapsedTimeForComp = (curTmInNs - compStartTimInNs) / 1000
    if (elapsedTimeForComp <= 0) elapsedTimeForComp = 1
    "ElapsedTimeCalc => UniqVal:%s, ElapsedTimeFromRead:%d, %sElapsedTime:%d".format(UniqVal, elapsedTimeFromRead, compName, elapsedTimeForComp)
  }
}
