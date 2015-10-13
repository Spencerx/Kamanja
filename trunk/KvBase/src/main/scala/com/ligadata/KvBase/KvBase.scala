
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

package com.ligadata.KvBase

import java.util.{ Comparator };

case class Key(timePartition: Long, bucketKey: Array[String], transactionId: Long, rowId: Int)
case class Value(serializerType: String, serializedInfo: Array[Byte])
case class TimeRange(beginTime: Long, endTime: Long)

object KvBaseDefalts {
  val defaultTime = 0L
  val defualtBucketKeyComp = new BucketKeyComp() // By BucketKey, time, then PrimaryKey/{Transactionid & Rowid}
  val defualtTimePartComp = new TimePartComp() // By time, BucketKey, then PrimaryKey/{transactionid & rowid}. This is little cheaper if we are going to get exact match, because we compare time & then bucketid
  val defaultLoadKeyComp = new LoadKeyComp() // By BucketId, BucketKey, Time Range
}

// We are doing this per Container. Not across containers
case class KeyWithBucketIdAndPrimaryKey(bucketId: Int, key: Key, hasPrimaryKey: Boolean, primaryKey: Array[String]);

case class LoadKeyWithBucketId(bucketId: Int, tmRange: TimeRange, bucketKey: Array[String]);

object KeyWithBucketIdAndPrimaryKeyCompHelper {

  def BucketIdForBucketKey(bucketKey: Array[String]): Int = {
    if (bucketKey == null) return 0
    val prime = 31;
    var result = 1;
    bucketKey.foreach(k => {
      result = result * prime
      if (k != null)
        result += k.hashCode();
    })
    return result
  }

  def CompareBucketKey(k1: KeyWithBucketIdAndPrimaryKey, k2: KeyWithBucketIdAndPrimaryKey): Int = {
    // First compare bucketId
    if (k1.bucketId < k2.bucketId)
      return -1
    if (k1.bucketId > k2.bucketId)
      return 1

    if (k1.key.bucketKey == null && k2.key.bucketKey == null)
      return 0

    if (k1.key.bucketKey != null && k2.key.bucketKey == null)
      return 1

    if (k1.key.bucketKey == null && k2.key.bucketKey != null)
      return -1

    // Next compare Bucket Keys
    if (k1.key.bucketKey.size < k2.key.bucketKey.size)
      return -1
    if (k1.key.bucketKey.size > k2.key.bucketKey.size)
      return 1

    for (i <- 0 until k1.key.bucketKey.size) {
      val cmp = k1.key.bucketKey(i).compareTo(k2.key.bucketKey(i))
      if (cmp != 0)
        return cmp
    }
    return 0
  }

  def CompareTimePartitionData(k1: KeyWithBucketIdAndPrimaryKey, k2: KeyWithBucketIdAndPrimaryKey): Int = {
    if (k1.key.timePartition < k2.key.timePartition)
      return -1
    if (k1.key.timePartition > k2.key.timePartition)
      return 1

    return 0
  }

  def ComparePrimaryKeyData(k1: KeyWithBucketIdAndPrimaryKey, k2: KeyWithBucketIdAndPrimaryKey): Int = {
    if (k1.primaryKey == null && k2.primaryKey == null)
      return 0

    if (k1.primaryKey != null && k2.primaryKey == null)
      return 1

    if (k1.primaryKey == null && k2.primaryKey != null)
      return -1

    if (k1.primaryKey.size < k2.primaryKey.size)
      return -1
    if (k1.primaryKey.size > k2.primaryKey.size)
      return 1

    for (i <- 0 until k1.primaryKey.size) {
      val cmp = k1.primaryKey(i).compareTo(k2.primaryKey(i))
      if (cmp != 0)
        return cmp
    }
    return 0
  }

  def CompareTransactionId(k1: KeyWithBucketIdAndPrimaryKey, k2: KeyWithBucketIdAndPrimaryKey): Int = {
    if (k1.key.transactionId < k2.key.transactionId)
      return -1
    if (k1.key.transactionId > k2.key.transactionId)
      return 1
    return 0
  }

  def CompareRowId(k1: KeyWithBucketIdAndPrimaryKey, k2: KeyWithBucketIdAndPrimaryKey): Int = {
    if (k1.key.rowId < k2.key.rowId)
      return -1
    if (k1.key.rowId > k2.key.rowId)
      return 1
    return 0
  }
}

class BucketKeyComp extends Comparator[KeyWithBucketIdAndPrimaryKey] {
  override def compare(k1: KeyWithBucketIdAndPrimaryKey, k2: KeyWithBucketIdAndPrimaryKey): Int = {
    // First compare Bucket Keys
    var cmp = KeyWithBucketIdAndPrimaryKeyCompHelper.CompareBucketKey(k1, k2)
    if (cmp != 0)
      return cmp

    // Next Compare time
    cmp = KeyWithBucketIdAndPrimaryKeyCompHelper.CompareTimePartitionData(k1, k2)
    if (cmp != 0)
      return cmp

    if (k1.hasPrimaryKey) {
      // Next compare Bucket Keys
      cmp = KeyWithBucketIdAndPrimaryKeyCompHelper.ComparePrimaryKeyData(k1, k2)
      if (cmp != 0)
        return cmp
    } else {
      // Next compare TransactionId
      cmp = KeyWithBucketIdAndPrimaryKeyCompHelper.CompareTransactionId(k1, k2)
      if (cmp != 0)
        return cmp

      // Next compare Row Id
      cmp = KeyWithBucketIdAndPrimaryKeyCompHelper.CompareRowId(k1, k2)
      if (cmp != 0)
        return cmp
    }

    return 0
  }
}

class TimePartComp extends Comparator[KeyWithBucketIdAndPrimaryKey] {
  override def compare(k1: KeyWithBucketIdAndPrimaryKey, k2: KeyWithBucketIdAndPrimaryKey): Int = {
    // First Compare time
    var cmp = KeyWithBucketIdAndPrimaryKeyCompHelper.CompareTimePartitionData(k1, k2)
    if (cmp != 0)
      return cmp

    // Next compare Bucket Keys
    cmp = KeyWithBucketIdAndPrimaryKeyCompHelper.CompareBucketKey(k1, k2)
    if (cmp != 0)
      return cmp

    if (k1.hasPrimaryKey) {
      // Next compare Bucket Keys
      cmp = KeyWithBucketIdAndPrimaryKeyCompHelper.ComparePrimaryKeyData(k1, k2)
      if (cmp != 0)
        return cmp
    } else {
      // Next compare TransactionId
      cmp = KeyWithBucketIdAndPrimaryKeyCompHelper.CompareTransactionId(k1, k2)
      if (cmp != 0)
        return cmp

      // Next compare Row Id
      cmp = KeyWithBucketIdAndPrimaryKeyCompHelper.CompareRowId(k1, k2)
      if (cmp != 0)
        return cmp
    }

    return 0
  }
}

class LoadKeyComp extends Comparator[LoadKeyWithBucketId] {
  override def compare(k1: LoadKeyWithBucketId, k2: LoadKeyWithBucketId): Int = {
    // First compare bucketId
    if (k1.bucketId < k2.bucketId)
      return -1
    if (k1.bucketId > k2.bucketId)
      return 1

    if (k1.bucketKey == null && k2.bucketKey == null)
      return 0

    if (k1.bucketKey != null && k2.bucketKey == null)
      return 1

    if (k1.bucketKey == null && k2.bucketKey != null)
      return -1

    // Next compare Bucket Keys
    if (k1.bucketKey.size < k2.bucketKey.size)
      return -1
    if (k1.bucketKey.size > k2.bucketKey.size)
      return 1

    for (i <- 0 until k1.bucketKey.size) {
      val cmp = k1.bucketKey(i).compareTo(k2.bucketKey(i))
      if (cmp != 0)
        return cmp
    }

    if (k1.tmRange == null && k2.tmRange == null)
      return 0

    if (k1.tmRange != null && k2.tmRange == null)
      return 1

    if (k1.tmRange == null && k2.tmRange != null)
      return -1

    if (k1.tmRange.beginTime < k2.tmRange.beginTime)
      return -1
    if (k1.tmRange.beginTime > k2.tmRange.beginTime)
      return 1

    if (k1.tmRange.endTime < k2.tmRange.endTime)
      return -1
    if (k1.tmRange.endTime > k2.tmRange.endTime)
      return 1
      
    return 0
  }
}


