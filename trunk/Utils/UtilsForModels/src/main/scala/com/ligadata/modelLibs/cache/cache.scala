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

package com.ligadata.modelLibs.cache

import scala.collection.JavaConversions._
import scala.collection.mutable._
import scala.util.control.Breaks._
import org.apache.logging.log4j.{ Logger, LogManager }
import java.util.{ Calendar, Date }
import scala.math.abs
import com.ligadata.modelLibs.cache.javafunctions.{ Function1 => JFunction1 }

trait LogTrait {
  val loggerName = this.getClass.getName()
  val logger = LogManager.getLogger(loggerName)
}
// CacheByDay exceptions
case class CbdInvalidTimeRangeError(e: String) extends Exception(e)
case class CdbDateNotInRangeError(e: String) extends Exception(e)

object CacheByDate {
  // convert epoch time into number of days since epoch (starting zero, first day 1970-01-01 = 0)
  def secsInDay = 24 * 60 * 60
  def EpochToDayNum(epochTime: Int) = epochTime / secsInDay
}

object JavaFnUtils {
  def toScalaFunction1[T, R](fun: JFunction1[T, R]): T => R = x => fun.call(x)
}

import CacheByDate._

// high level data structure
//  keys are partitioned into number of sub partitions based on key hash
//  each sub partition consists of a set of cached keys and a set of buckets 
//     where each bucket corresponding to an array of cached keys (copy from set)
//  The idea is that cached objects fall into one of the date buckets and could be appended to corresponding bucket

//  Date is epochtime (should be begin or end of time)
//
class CacheByDate[T](numDays: Int, maxNumDays: Int, numSubHashes: Int, fnHash: T => java.lang.Long) extends LogTrait {
  // This method is declared to call from Java
  def this(numDays: Int, maxNumDays: Int, numSubHashes: Int, f: JFunction1[T, java.lang.Long]) {
    this(numDays: Int, maxNumDays: Int, numSubHashes: Int, JavaFnUtils.toScalaFunction1(f));
  }

  // Client can adjust the startDt and endDt (inclusive range) in order to expire lowest date or call Expire() to individually remove dates
  // Even though it takes range, it actually maintain information at individual date level which allows to have set of
  // dates rather than strict date range.

  def SetRange(startDt: Int, endDt: Int) {
    if (startDt > endDt)
      throw CbdInvalidTimeRangeError("Invalid Time Range, start: %d > end: %d".format(startDt, endDt))
    val (sdt, edt) = (EpochToDayNum(startDt), EpochToDayNum(endDt + secsInDay))
    val numRangeDays = edt - sdt
/*
    //BUGBUG:: For now we are not check this
    if (numRangeDays > maxNumDays)
      throw CbdInvalidTimeRangeError("Invalid Time Range, range size: %d exceeds maxNumDays: %d, start: %d, end: %d".format(numRangeDays, maxNumDays, startDt, endDt))
*/
    subCaches.foreach { sc => sc.SetRange(sdt, edt) }
    stRange = sdt * secsInDay
    endRange = edt * secsInDay // point to beginning of the next day of given end date
  }

  def StartRange = stRange
  def EndRange = endRange - secsInDay // returns endRange as inclusive, but stored as 1 day more

  // Allows to extend or shrink range by N days; if numDays is -ve, it could shrink or extend depending on range start or end
  def ExtendEndByDays(numDays: Int) = SetRange(StartRange, EndRange + numDays * secsInDay)
  def ExtendStartByDays(numDays: Int) = SetRange(StartRange + numDays * secsInDay, EndRange)

  // compare a given epoch time with the active date range and return -1, 0, +1 depending on whether given date is 
  // less, in range or greater than the range.
  def CompareWithRange(dt: Int) = if (dt < stRange) -1 else if (dt >= endRange) +1 else 0

  // compare a given epoch time with the active date range and return -N, 0, +N depending on whether given date is 
  // less, in range or greater than the range. N is number of days less than or greater than range.
  def DiffWithRange(dt: Int) = {
    val (sdt, edt, gdt) = (stRange / secsInDay, endRange / secsInDay, dt / secsInDay) // gdt - given date
    if (gdt < sdt) (gdt - sdt) // returns -ve number if less than starting day
    else if (gdt >= edt) (gdt - edt) // returns +ve number if greater or equal to last day + 1
    else 0
  }

  def Sub(key: T) = subCaches((abs(fnHash(key)) % numSubHashes).toInt)
  def Exist(key: T): Boolean = Sub(key).Exist(key)
  def Add(dt: Int, key: T): Boolean = Sub(key).Add(EpochToDayNum(dt), key)
  //def Expire(dt : Int) = subCaches.foreach(sc => { sc.Expire(dt) })

  def AddKeys(dt: Int, keys: Array[T]) = {
    val dtNum = EpochToDayNum(dt)
    keys.foreach(key => { Sub(key).Add(dtNum, key) })
  }

  // A class that represent sub cache that is associated with hash number (0 .. num of sub hashes)
  private[CacheByDate] class SubCache {
    case class DayBucket(dt: Int, hashIdx: Int) { val keys = new ArrayBuffer[T]() }
    // simple get and set methods to indicate in which slot this sub cache belongs - useful for debugging/tracing purposes
    def Idx(idx: Int) = { idxSC = idx }
    def Idx = idxSC

    // The arguments sdt and edt are epoch dayNum, not epoch time and edt is the last day of inclusive range + 1
    def SetRange(sdt: Int, edt: Int) {
      // check if each day in the range contains day bucket associated with that date
      // if doesn't exist, add to the map
      sdt to edt - 1 map (dt => { if (dateMap.contains(dt) == false) AddDay(dt) })
      // check each key in the map and if that doesn't belong in the given range, expire
      // Note that expiration happens incrementally
      val dtsToExclude = dateMap.keySet.filter(dt => { dt < sdt || dt >= edt })
      dtsToExclude.foreach(dt => { Expire(dt) })
      stRange = sdt * secsInDay
      endRange = edt * secsInDay // point to beginning of the next day of given end date
      if (dtsToExclude.size > 0) Rebuild
    }

    def AddDay(dt: Int) = dateMap += (dt -> DayBucket(dt, idxSC))
    def Expire(dt: Int) = { dateMap.remove(dt) }
    def Exist(key: T) = setKeys.contains(key)

    def Bucket(dt: Int) = {
      val oBucket = dateMap.get(dt);
      if (oBucket.isEmpty)
        throw CdbDateNotInRangeError("Given date: %d is not in the range: %d, %d".format(dt, stRange, endRange))
      oBucket.get
    }

    def Add(bucket: DayBucket, key: T): Boolean = {
      if (setKeys.contains(key) == false) {
        bucket.keys.append(key)
        setKeys += key
        true
      } else false
    }
    // add a key in 
    def Add(dt: Int, key: T): Boolean = Add(Bucket(dt), key)

    def AddKeys(dt: Int, keys: Array[T]) = {
      val bucket = Bucket(dt)
      keys.foreach(key => Add(bucket, key))
    }

    // rebuild set of keys from existing date buckets - this needs to be done after expiring one or more days
    def Rebuild = {
      setKeys.clear
      dateMap.foreach(elem => setKeys ++= elem._2.keys)
    }
    var idxSC = -1 // index of this object in sub cache array
    var stRange = -1
    var endRange = -1
    // set of keys
    val setKeys = Set[T]()
    // map of day number to day bucket
    val dateMap = collection.mutable.Map[Int, DayBucket]()
  }

  private def CreateSubCaches: Unit = {
    for (i <- 0 until numSubHashes) {
      subCaches(i) = new SubCache
      subCaches(i).Idx(i)
    }
  }

  private var subCaches = new Array[SubCache](numSubHashes)
  private var stRange = -1
  private var endRange = -1

  CreateSubCaches
}
