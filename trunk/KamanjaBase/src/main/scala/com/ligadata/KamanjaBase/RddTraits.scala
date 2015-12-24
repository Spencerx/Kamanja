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

package com.ligadata.KamanjaBase

import scala.language.implicitConversions
import java.util.{ Date, Calendar, TimeZone }

// import scala.reflect.{ classTag, ClassTag }
import org.apache.logging.log4j.{ Logger, LogManager }
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.reflect.{ classTag, ClassTag }
import com.ligadata.Exceptions._
import com.ligadata.KamanjaBase.api.java.function.{ Function1 => JFunction1, FlatMapFunction1 => JFlatMapFunction1, PairFunction => JPairFunction }
import com.google.common.base.Optional
import com.ligadata.Utils.Utils
import java.util.{ Comparator, List => JList, Iterator => JIterator }
import java.lang.{ Iterable => JIterable, Long => JLong }
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConversions._
import com.ligadata.KvBase.{ Key, Value, TimeRange }

object ThreadLocalStorage {
  final val txnContextInfo = new ThreadLocal[TransactionContext]();
}

object KamanjaUtils {
  def fakeClassTag[T]: ClassTag[T] = ClassTag.AnyRef.asInstanceOf[ClassTag[T]]
  def toScalaFunction1[T, R](fun: JFunction1[T, R]): T => R = x => fun.call(x)
  def pairFunToScalaFun[A, B, C](x: JPairFunction[A, B, C]): A => (B, C) = y => x.call(y)
}

import KamanjaUtils._

class Stats {
  // # of Rows, Total Size of the data, Avg Size, etc
}

// RDD traits/classes
/**
 * More functions available on RDDs of (key, value) pairs via an implicit conversion.
 */
class PairRDDFunctions[K, V](self: RDD[(K, V)])(implicit kt: ClassTag[K], vt: ClassTag[V], ord: Ordering[K] = null) {
  val LOG = LogManager.getLogger(getClass);

  def count: Long = self.size

  /**
   * Count the number of elements for each key, and return the result as a Map.
   */
  def countByKey: Map[K, Long] = {
    self.Collection.groupBy(t => t._1).mapValues(listOfPairs => listOfPairs.size.toLong)
  }

  /**
   * Group the values for each key in the RDD into a iterable trait.
   */
  def groupByKey: RDD[(K, Iterable[V])] = {
    val newrdd = RDD.makeRDD(self.Collection.groupBy(t => t._1).mapValues(listOfPairs => listOfPairs.map(pair => pair._2).asInstanceOf[Iterable[V]]).toArray)
    newrdd
  }

  // Join Functions
  /**
   * Return an RDD containing all pairs of elements with matching keys in this and other.
   * Each pair of elements will be returned as a (k, (v1, v2)) tuple, where (k, v1) is in this and (k, v2) is in other.
   */
  def join[W](other: RDD[(K, W)]): RDD[(K, (V, W))] = {
    val s1 = self.Collection.groupBy(t => t._1)
    val s2 = other.Collection.groupBy(t => t._1)
    val result = ArrayBuffer[(K, (V, W))]()
    s1.foreach(s1kv => {
      val fndValPairs = s2.getOrElse(s1kv._1, null)
      if (fndValPairs != null) {
        s1kv._2.foreach(s1pair => {
          fndValPairs.foreach(s2pair => {
            result += ((s1kv._1, (s1pair._2, s2pair._2)))
          })
        })
      }
    })

    val newrdd = RDD.makeRDD(result.toArray)
    newrdd
  }

  /**
   * Return the Cartesian product of this and other, that is,
   * the RDD of all pairs of elements (v, w) where v is in this and w is in other.
   */
  def fullOuterJoin[W](other: RDD[(K, W)]): RDD[(K, (Option[V], Option[W]))] = {
    val s1 = self.Collection.groupBy(t => t._1)
    val s2 = other.Collection.groupBy(t => t._1)
    val result = ArrayBuffer[(K, (Option[V], Option[W]))]()
    s1.foreach(s1kv => {
      val fndValPairs = s2.getOrElse(s1kv._1, null)
      if (fndValPairs != null) {
        s1kv._2.foreach(s1pair => {
          fndValPairs.foreach(s2pair => {
            result += ((s1kv._1, (Some(s1pair._2), Some(s2pair._2))))
          })
        })
      } else {
        s1kv._2.foreach(s1pair => {
          result += ((s1kv._1, (Some(s1pair._2), None)))
        })
      }
    })

    // Looking for the Remaining stuff from s2
    s2.foreach(s2kv => {
      val fndValPairs = s1.getOrElse(s2kv._1, null)
      if (fndValPairs == null) { // if this is not present in s1 map, add this
        s2kv._2.foreach(s2pair => {
          result += ((s2kv._1, (None, Some(s2pair._2))))
        })
      }
    })

    val newrdd = RDD.makeRDD(result.toArray)
    newrdd
  }

  /**
   * Performs a left outer join of this and other.
   * For each element (k, v) in this, the resulting RDD will either contain all pairs (k, (v, Some(w))) for w in other,
   * or the pair (k, (v, None)) if no elements in other have key k.
   */
  def leftOuterJoin[W](other: RDD[(K, W)]): RDD[(K, (V, Option[W]))] = {
    val s1 = self.Collection.groupBy(t => t._1)
    val s2 = other.Collection.groupBy(t => t._1)
    val result = ArrayBuffer[(K, (V, Option[W]))]()
    s1.foreach(s1kv => {
      val fndValPairs = s2.getOrElse(s1kv._1, null)
      if (fndValPairs != null) {
        s1kv._2.foreach(s1pair => {
          fndValPairs.foreach(s2pair => {
            result += ((s1kv._1, (s1pair._2, Some(s2pair._2))))
          })
        })
      } else {
        s1kv._2.foreach(s1pair => {
          result += ((s1kv._1, (s1pair._2, None)))
        })
      }
    })

    val newrdd = RDD.makeRDD(result.toArray)
    newrdd
  }

  /**
   * Performs a right outer join of this and other.
   * For each element (k, w) in other, the resulting RDD will either contain all pairs (k, (Some(v), w)) for v in this,
   * or the pair (k, (None, w)) if no elements in this have key k.
   */
  def rightOuterJoin[W](other: RDD[(K, W)]): RDD[(K, (Option[V], W))] = {
    val s1 = self.Collection.groupBy(t => t._1)
    val s2 = other.Collection.groupBy(t => t._1)
    val result = ArrayBuffer[(K, (Option[V], W))]()
    s2.foreach(s2kv => {
      val fndValPairs = s1.getOrElse(s2kv._1, null)
      if (fndValPairs != null) {
        s2kv._2.foreach(s2pair => {
          fndValPairs.foreach(s1pair => {
            result += ((s2kv._1, (Some(s1pair._2), s2pair._2)))
          })
        })
      } else {
        s2kv._2.foreach(s2pair => {
          result += ((s2kv._1, (None, s2pair._2)))
        })
      }
    })

    val newrdd = RDD.makeRDD(result.toArray)
    newrdd
  }

  /**
   * Pass each value in the key-value pair RDD through a map function without changing the keys.
   */
  def mapValues[U](f: V => U): RDD[(K, U)] = {
    val newrdd = RDD.makeRDD(self.Collection.map(v => (v._1, f(v._2))).toArray)
    newrdd
  }
}

/**
 * RDD This is a scala Object. Used as a factory class to create scala RDD objects to
 */
object RDD {
  implicit def rddToPairRDDFunctions[K, V](rdd: RDD[(K, V)])(implicit kt: ClassTag[K], vt: ClassTag[V], ord: Ordering[K] = null): PairRDDFunctions[K, V] = {
    new PairRDDFunctions(rdd)
  }

  /**
   * Create an RDD of type [T] from a given array
   * @param values Array[T]
   * @return RDD[T]
   */
  def makeRDD[T](values: Array[T]): RDD[T] = {
    val newrdd = new RDD[T]()(ClassTag.AnyRef.asInstanceOf[ClassTag[T]])
    newrdd.collection ++= values
    newrdd
  }
}

/**
 * RDD - One of the basic classes for Kamanja development.  It is basically a collection of individual scala RDDObjects.  If you are working with Java,
 * JavaRDD will support the same nethod.
 *
 * Note that  calling toJavaRDD on any RDD[Object] will return a JavaRDD[T]
 */
class RDD[T: ClassTag] {
  private val collection = ArrayBuffer[T]()
  private[KamanjaBase] def Collection = collection
  private val LOG = LogManager.getLogger(getClass);

  /**
   * Return the underlying iterator for this RDD
   * @return Iterator[T]
   */
  def iterator: Iterator[T] = collection.iterator

  /**
   * Return the classTag
   */
  def elementClassTag: ClassTag[T] = classTag[T]

  /**
   * Return a new RDD by applying a function to all elements of this RDD.
   * @param f T => U a function that converts a class T into Class U
   * @return new RDD[U]
   */
  def map[U: ClassTag](f: T => U): RDD[U] = {
    val newrdd = new RDD[U]()
    newrdd.collection ++= collection.iterator.map(f)
    newrdd
  }

  /**
   * Return a new RDD by applying a function to all elements of this RDD, and selecting only
   * those elements that satisfy the provided time range
   *
   * @param tmRange TimeRange
   * @param f T => U
   * @return new RDD[U]
   */
  def map[U: ClassTag](tmRange: TimeRange, f: T => U): RDD[U] = {
    val newrdd = new RDD[U]()
    // BUGBUG:: Yet to implement filtering tmRange
    newrdd.collection ++= collection.iterator.map(f)
    newrdd
  }

  /**
   * Return a new RDD by first applying a function to all elements of this RDD, and then flattening the results.
   * @param f T => U
   * @return new  RDD[U]
   */
  def flatMap[U: ClassTag](f: T => TraversableOnce[U]): RDD[U] = {
    val newrdd = new RDD[U]()
    newrdd.collection ++= collection.iterator.flatMap(f)
    newrdd
  }

  /**
   * Return a new RDD containing only the elements that satisfy a predicate.
   * @param f T => Boolean
   * @return new RDD[T]
   */
  def filter(f: T => Boolean): RDD[T] = {
    val newrdd = new RDD[T]()
    newrdd.collection ++= collection.iterator.filter(f)
    newrdd
  }

  /**
   * Return a new RDD containing only the elements that satisfy a predicate, and a provided timerRange
   * @param tmRange TimeRange
   * @param f T => Boolean
   * @return new RDD[T]
   */
  def filter(tmRange: TimeRange, f: T => Boolean): RDD[T] = {
    val newrdd = new RDD[T]()
    // BUGBUG:: Yet to implement filtering tmRange
    newrdd.collection ++= collection.iterator.filter(f)
    newrdd
  }

  /**
   * Return the union of this RDD and another one.
   * @param other RDD[T]
   * @return new RDD[T]
   */
  def union(other: RDD[T]): RDD[T] = {
    val newrdd = new RDD[T]()
    newrdd.collection ++= (collection ++ other.iterator)
    newrdd
  }

  /**
   * Return the union of this RDD and another one. (same us this.union)
   * @param other RDD[T]
   * @return new RDD[T]
   */
  def ++(other: RDD[T]): RDD[T] = this.union(other)

  // def sortBy[K](f: (T) => K, ascending: Boolean = true) (implicit ord: Ordering[K], ctag: ClassTag[K]): RDD[T] = this.keyBy[K](f).sortByKey(ascending, numPartitions).values

  /**
   * Return the intersection of this RDD and another one.
   * @param other RDD[T]
   * @return new RDD[T]
   */
  def intersection(other: RDD[T]): RDD[T] = {
    throw new Exception("Unhandled function intersection")
  }

  /**
   * Return an RDD of grouped elements. Each group consists of a key and a sequence of elements mapping to that key.
   * @param f T=>K
   * @return RDD[(K, Iterable[T])] - an RDD of tuples, Same Ks are grouped into the tuple's Iterable.
   */
  def groupBy[K](f: T => K)(implicit kt: ClassTag[K]): RDD[(K, Iterable[T])] = {
    val newrdd = new RDD[(K, Iterable[T])]()
    newrdd.collection ++= collection.map(x => (f(x), x)).groupBy(t => t._1).mapValues(listOfPairs => listOfPairs.map(pair => pair._2))
    newrdd
  }

  /**
   * Applies a function f to all elements of this RDD.
   * @param f T => Unit
   */
  def foreach(f: T => Unit): Unit = {
    collection.iterator.foreach(f)
  }

  /**
   * Return an array that contains all of the elements in this RDD.
   * @return Array[T]
   */
  def toArray: Array[T] = {
    collection.toArray
  }

  /**
   * Return the list that contains all of the elements in this RDD.
   * @return List[T]
   */
  def toList: List[T] = {
    collection.toList
  }

  /**
   * Subtract values from this RDD that are present in the parameter RDD (Unsupported)
   * @return RDD[T]
   */
  def subtract(other: RDD[T]): RDD[T] = {
    throw new Exception("Unhandled function subtract")
  }

  /**
   * Return the number of elements in the RDD.
   * @return Long
   */
  def count(): Long = size()

  /**
   * Return the number of elements in the RDD.
   * @return Long
   */
  def size(): Long = collection.size

  /**
   * Return the first element in this RDD.
   * @return Option[T]
   */
  def first(): Option[T] = {
    if (collection.size > 0)
      return Some(collection(0))
    None
  }

  /**
   * Return the last element in this RDD.
   * @return Option[T]
   */
  def last(): Option[T] = {
    if (collection.size > 0)
      return Some(collection(collection.size - 1))
    None
  }

  /**
   * Sort the data in the array and than return the top N element(s).
   * @param num number of elements to return
   * @return  Array[T]
   */
  def top(num: Int): Array[T] = {
    // BUGBUG:: Order the data and select top N
    throw new Exception("Unhandled function top")
  }

  /**
   * Return the maximum element in this collection.
   * @param f  (Option[U], T) => U (this function must determine a larger of the 2 give T values)
   * @return Option[U]
   */
  def max[U: ClassTag](f: (Option[U], T) => U): Option[U] = {
    var maxVal: Option[U] = None
    collection.foreach(c => {
      maxVal = Some(f(maxVal, c))
    })
    maxVal
  }

  /**
   * Return the minimum element in this collection.
   * @param f (Option[U], T) => U  (this function must determine a smaller of the 2 give T values)
   * @return Option[U]
   */
  def min[U: ClassTag](f: (Option[U], T) => U): Option[U] = {
    var minVal: Option[U] = None
    collection.foreach(c => {
      minVal = Some(f(minVal, c))
    })
    minVal
  }

  /**
   * Check whether this collection is empty.
   * @return Boolean
   */
  def isEmpty(): Boolean = collection.isEmpty

  def keyBy[K](f: T => K): RDD[(K, T)] = {
    map(x => (f(x), x))
  }

  /**
   * Convert this scala RDD[T] to JavaRDD[T]
   * @return JavaRDD[T]
   */
  def toJavaRDD(): JavaRDD[T] = {
    new JavaRDD(this)(elementClassTag)
  }
}

/**
 * Static class used to create RDD[T]. implecitely converts between JavaRDD[T] and RDD[T]
 */
object JavaRDD {
  implicit def fromRDD[T: ClassTag](rdd: RDD[T]): JavaRDD[T] = new JavaRDD[T](rdd)
  implicit def toRDD[T](rdd: JavaRDD[T]): RDD[T] = rdd.rdd
}

abstract class AbstractJavaRDDLike[T, This <: JavaRDDLike[T, This]]
  extends JavaRDDLike[T, This]

/**
 *  This is a trait to provides implementations for JavaRDDs, these methods can be accessed from java code and mirror the
 *  methods defined in RDD[T]
 */
trait JavaRDDLike[T, This <: JavaRDDLike[T, This]] {
  def wrapRDD(rdd: RDD[T]): This

  implicit val classTag: ClassTag[T]

  def rdd: RDD[T]

  def iterator: java.util.Iterator[T] = asJavaIterator(rdd.iterator)

  /**
   * @see RDD[T]
   */
  def map[R](f: JFunction1[T, R]): JavaRDD[R] = {
    JavaRDD.fromRDD(rdd.map(KamanjaUtils.toScalaFunction1(f))(fakeClassTag))(fakeClassTag)
  }

  /**
   * @see RDD[T]
   */
  def map[R](tmRange: TimeRange, f: JFunction1[T, R]): JavaRDD[R] = {
    // BUGBUG:: Yet to implement filtering tmRange
    JavaRDD.fromRDD(rdd.map(KamanjaUtils.toScalaFunction1(f))(fakeClassTag))(fakeClassTag)
  }

  /**
   * @see RDD[T]
   */
  def flatMap[U](f: JFlatMapFunction1[T, U]): JavaRDD[U] = {
    def fn = (x: T) => f.call(x).asScala
    JavaRDD.fromRDD(rdd.flatMap(fn)(fakeClassTag[U]))(fakeClassTag[U])
  }

  /**
   * @see RDD[T]
   */
  def groupBy[U](f: JFunction1[T, U]): JavaPairRDD[U, JIterable[T]] = {
    // The type parameter is U instead of K in order to work around a compiler bug; see SPARK-4459
    implicit val ctagK: ClassTag[U] = fakeClassTag
    implicit val ctagV: ClassTag[JList[T]] = fakeClassTag
    JavaPairRDD.fromRDD(JavaPairRDD.groupByResultToJava(rdd.groupBy(KamanjaUtils.toScalaFunction1(f))(fakeClassTag)))
  }

  /**
   * @see RDD[T]
   */
  def count(): Long = rdd.count()

  /**
   * @see RDD[T]
   */
  def size(): Long = rdd.size()

  /**
   * @see RDD[T]
   */
  def first(): Optional[T] = Utils.optionToOptional(rdd.first)

  /**
   * @see RDD[T]
   */
  def last(): Optional[T] = Utils.optionToOptional(rdd.last)

  /**
   * @see RDD[T]
   */
  def isEmpty(): Boolean = rdd.isEmpty()
}

/**
 *  JavaRDD is a collection of JavaRDDObjects.  It is provided ot enable developers to write code to interface with Kamanja runtime engine
 */
class JavaRDD[T](val rdd: RDD[T])(implicit val classTag: ClassTag[T])
  extends AbstractJavaRDDLike[T, JavaRDD[T]] {

  override def wrapRDD(rdd: RDD[T]): JavaRDD[T] = JavaRDD.fromRDD(rdd)

  /**
   * @see RDD[T]
   */
  def filter(f: JFunction1[T, java.lang.Boolean]): JavaRDD[T] = wrapRDD(rdd.filter((x => f.call(x).booleanValue())))

  /**
   * @see RDD[T]
   */
  def filter(tmRange: TimeRange, f: JFunction1[T, java.lang.Boolean]) = wrapRDD(rdd.filter(tmRange, (x => f.call(x).booleanValue())))

  /**
   * @see RDD[T]
   */
  def union(other: JavaRDD[T]): JavaRDD[T] = wrapRDD(rdd.union(other.rdd))

  /**
   * @see RDD[T]
   */
  def intersection(other: JavaRDD[T]): JavaRDD[T] = wrapRDD(rdd.intersection(other.rdd))

  /**
   * @see RDD[T]
   */
  def subtract(other: JavaRDD[T]): JavaRDD[T] = wrapRDD(rdd.subtract(other))

  // def sortBy[S](f: JFunction1[T, S], ascending: Boolean): JavaRDD[T]
}

/**
 * A static class to use for working with JavaPairRDD objects.
 */
object JavaPairRDD {
  /**
   * Return an JIterable RDD of grouped elements. Each group consists of a key and a sequence of elements mapping to that key.
   * @param rdd RDD[(K, Iterable[T])]
   * @return RDD[(K, JIterable[T])]
   */
  def groupByResultToJava[K: ClassTag, T](rdd: RDD[(K, Iterable[T])]): RDD[(K, JIterable[T])] = {
    RDD.rddToPairRDDFunctions(rdd).mapValues(asJavaIterable)
  }

  /**
   * Create a new JavaPairRDD from a give RDD
   * @param rdd RDD[(K, V)]
   * @return JavaPairRDD[K, V]
   */
  def fromRDD[K: ClassTag, V: ClassTag](rdd: RDD[(K, V)]): JavaPairRDD[K, V] = {
    new JavaPairRDD[K, V](rdd)
  }

  implicit def toRDD[K, V](rdd: JavaPairRDD[K, V]): RDD[(K, V)] = rdd.rdd

  /**
   *  Convert a JavaRDD of key-value pairs to JavaPairRDD.
   *  @param rdd JavaRDD[(K, V)]
   *  @return JavaPairRDD[K, V]
   */
  def fromJavaRDD[K, V](rdd: JavaRDD[(K, V)]): JavaPairRDD[K, V] = {
    implicit val ctagK: ClassTag[K] = fakeClassTag
    implicit val ctagV: ClassTag[V] = fakeClassTag
    new JavaPairRDD[K, V](rdd.rdd)
  }
}

/**
 *  A Key-Value implementation for a JavaRDDs.
 */
class JavaPairRDD[K, V](val rdd: RDD[(K, V)])(implicit val kClassTag: ClassTag[K], implicit val vClassTag: ClassTag[V])
  extends AbstractJavaRDDLike[(K, V), JavaPairRDD[K, V]] {

  override def wrapRDD(rdd: RDD[(K, V)]): JavaPairRDD[K, V] = JavaPairRDD.fromRDD(rdd)

  override val classTag: ClassTag[(K, V)] = rdd.elementClassTag

  import JavaPairRDD._

  /**
   * Return a new RDD containing only the elements that satisfy the predicate.
   */
  def filter(f: JFunction1[(K, V), java.lang.Boolean]): JavaPairRDD[K, V] =
    new JavaPairRDD[K, V](rdd.filter(x => f.call(x).booleanValue()))

  /**
   * Return the union of this RDD and another one.
   */
  def union(other: JavaPairRDD[K, V]): JavaPairRDD[K, V] =
    new JavaPairRDD[K, V](rdd.union(other.rdd))

  /**
   * Return the intersection of this RDD and another one. The output will not contain any duplicate elements.
   */
  def intersection(other: JavaPairRDD[K, V]): JavaPairRDD[K, V] =
    new JavaPairRDD[K, V](rdd.intersection(other.rdd))

  /**
   * Return an RDD from this that are not in the other.
   */
  def subtract(other: JavaPairRDD[K, V]): JavaPairRDD[K, V] =
    fromRDD(rdd.subtract(other))

  // def join[W](other: JavaPairRDD[K, W]): JavaPairRDD[K, (V, W)]
}

/**
 * An instance object that represents either an individual Container or an individual Message in Kamanja
 */
abstract class RDDObject[T: ClassTag] {
  val LOG = LogManager.getLogger(getClass);

  private def getCurrentTransactionContext: TransactionContext = ThreadLocalStorage.txnContextInfo.get

  /**
   * Implemented by an actual Message or Container class that is generated during message/container deployment
   * @return T
   */
  def build: T

  /**
   * Implemented by an actual Message or Container class that is generated during message/container deployment
   * @param from T
   * @return T
   */
  def build(from: T): T

  /**
   * Implemented by an actual Message or Container class that is generated during message/container deployment
   * @return String
   */
  def getFullName: String // Gets Message/Container Name

  /**
   * Implemented by an actual Message or Container class that is generated during message/container deployment
   * @return JavaRDDObject[T]
   */
  def toJavaRDDObject: JavaRDDObject[T]
  // Methods needs to be overrided in inherited class -- End

  /**
   * Return a instance of an RDDObject[T]
   * @return   RDDObject[T]
   */
  final def toRDDObject: RDDObject[T] = this

  /**
   * First group of functions retrieve one object (either recent or for a given key & filter)
   * Get recent entry for the Current Key
   *
   * @return Option[T]
   */
  final def getRecent: Option[T] = {
    val txnContext = getCurrentTransactionContext
    if (txnContext != null) {
      val fndVal = txnContext.getNodeCtxt.getEnvCtxt.getRecent(txnContext.transId, getFullName, txnContext.getMessage.PartitionKeyData.toList, null, null)
      if (fndVal != None)
        return Some(fndVal.get.asInstanceOf[T])
    }
    None
  }

  /**
   * Get recent entry or if recent entry not present, return a new entry.  WARNING.. THIS IS DANGEROUS this can easily run your application out of storage
   * if there are many messages in the history storage.
   *
   * @return T
   */
  final def getRecentOrNew: T = {
    val rcnt = getRecent
    if (rcnt.isEmpty)
      return build
    rcnt.get
  }

  /**
   * Get the latest RDDObject with a given key.  This will search within the model context.
   *
   * @param key Array[String]
   * @return Option[T]
   */
  final def getRecent(key: Array[String]): Option[T] = {
    val txnContext = getCurrentTransactionContext
    if (txnContext != null) {
      val fndVal = txnContext.getNodeCtxt.getEnvCtxt.getRecent(txnContext.transId, getFullName, key.toList, null, null)
      if (fndVal != None)
        return Some(fndVal.get.asInstanceOf[T])
    }
    None
  }

  /**
   * Get recent entry or if recent entry not present, return a new entry.
   *
   * @param key Array[String]
   * @return T
   */
  final def getRecentOrNew(key: Array[String]): T = {
    val rcnt = getRecent(key)
    if (rcnt.isEmpty) return build
    rcnt.get
  }

  /**
   * Find an entry for the given key.
   *
   * @param tmRange TimeRange
   * @param f MessageContainerBase => Boolean
   * @return Option[T]
   */
  final def getOne(tmRange: TimeRange, f: MessageContainerBase => Boolean): Option[T] = {
    val txnContext = getCurrentTransactionContext
    if (txnContext != null) {
      val fndVal = txnContext.getNodeCtxt.getEnvCtxt.getRecent(txnContext.transId, getFullName, txnContext.getMessage.PartitionKeyData.toList, tmRange, f)
      if (fndVal != None)
        return Some(fndVal.get.asInstanceOf[T])
    }
    None
  }

  /**
   * Find an entry for the given key or return a new one.
   *
   * @param tmRange TimeRange
   * @param f MessageContainerBase => Boolean
   * @return T
   */
  final def getOneOrNew(tmRange: TimeRange, f: MessageContainerBase => Boolean): T = {
    val one = getOne(tmRange, f)
    if (one.isEmpty) return build
    one.get
  }

  /**
   * Find an entry for the given key.
   *
   * @param key Array[String]
   * @param tmRange: TimeRange
   * @param f MessageContainerBase => Boolean
   * @return Option[T]
   */
  final def getOne(key: Array[String], tmRange: TimeRange, f: MessageContainerBase => Boolean): Option[T] = {
    val txnContext = getCurrentTransactionContext
    if (txnContext != null) {
      val fndVal = txnContext.getNodeCtxt.getEnvCtxt.getRecent(txnContext.transId, getFullName, key.toList, tmRange, f)
      if (fndVal != None)
        return Some(fndVal.get.asInstanceOf[T])
    }
    None
  }

  /**
   * Find an entry for the given key or return a new one.
   *
   * @param key Array[String]
   * @param tmRange TimeRange
   * @param f MessageContainerBase => Boolean
   * @return T
   */
  final def getOneOrNew(key: Array[String], tmRange: TimeRange, f: MessageContainerBase => Boolean): T = {
    val one = getOne(key, tmRange, f)
    if (one.isEmpty) return build
    one.get
  }

  /**
   * This group of functions retrieve collection of objects for a give key.  Key will be pulled from a model Context
   *
   * @param f MessageContainerBase => Boolean
   * @return RDD[T]
   */
  final def getRDDForCurrKey(f: MessageContainerBase => Boolean): RDD[T] = {
    val txnContext = getCurrentTransactionContext
    var values: Array[T] = Array[T]()
    if (txnContext != null) {
      val fndVal = txnContext.getNodeCtxt.getEnvCtxt.getRDD(txnContext.transId, getFullName, txnContext.getMessage.PartitionKeyData.toList, null, f)
      if (fndVal != null)
        values = fndVal.map(v => v.asInstanceOf[T])
    }
    RDD.makeRDD(values)
  }

  /**
   * Return a RDD for the current key. Key will be pulled from a model Context
   *
   * @param tmRange TimeRange
   * @return RDD[T]
   */
  final def getRDDForCurrKey(tmRange: TimeRange): RDD[T] = {
    val txnContext = getCurrentTransactionContext
    var values: Array[T] = Array[T]()
    if (txnContext != null) {
      val fndVal = txnContext.getNodeCtxt.getEnvCtxt.getRDD(txnContext.transId, getFullName, txnContext.getMessage.PartitionKeyData.toList, tmRange, null)
      if (fndVal != null)
        values = fndVal.map(v => v.asInstanceOf[T])
    }
    RDD.makeRDD(values)
  }

  /**
   * Return a RDD for the current key. Key will be pulled from a model Context
   *
   * @param tmRange TimeRange
   * @param f MessageContainerBase => Boolean
   * @return RDD[T]
   */
  final def getRDDForCurrKey(tmRange: TimeRange, f: MessageContainerBase => Boolean): RDD[T] = {
    val txnContext = getCurrentTransactionContext
    var values: Array[T] = Array[T]()
    if (txnContext != null) {
      val fndVal = txnContext.getNodeCtxt.getEnvCtxt.getRDD(txnContext.transId, getFullName, txnContext.getMessage.PartitionKeyData.toList, tmRange, f)
      if (fndVal != null)
        values = fndVal.map(v => v.asInstanceOf[T])
    }
    RDD.makeRDD(values)
  }

  // With too many messages, these may fail - mostly useful for message types where number of messages are relatively small 
  /**
   * Return a RDD -- If the filtering parameters are not sufficiently strict, this method can return a very large amout of
   * RDDObjects, causing memory issues.
   *
   * @return RDD[T]
   */
  final def getRDD(): RDD[T] = {
    val txnContext = getCurrentTransactionContext
    var values: Array[T] = Array[T]()
    if (txnContext != null) {
      val fndVal = txnContext.getNodeCtxt.getEnvCtxt.getRDD(txnContext.transId, getFullName, null, null, null)
      if (fndVal != null)
        values = fndVal.map(v => v.asInstanceOf[T])
    }
    RDD.makeRDD(values)
  }

  /**
   * Return a RDD- If the filtering parameters are not sufficiently strict, this method can return a very large amout of
   * RDDObjects, causing memory issues.
   *
   * @param tmRangeTimeRange
   * @param f MessageContainerBase => Boolean
   * @return RDD[T]
   */
  final def getRDD(tmRange: TimeRange, f: MessageContainerBase => Boolean): RDD[T] = {
    val txnContext = getCurrentTransactionContext
    var values: Array[T] = Array[T]()
    if (txnContext != null) {
      val fndVal = txnContext.getNodeCtxt.getEnvCtxt.getRDD(txnContext.transId, getFullName, null, tmRange, f)
      if (fndVal != null)
        values = fndVal.map(v => v.asInstanceOf[T])
    }
    RDD.makeRDD(values)
  }

  /**
   * Return a RDD - If the filtering parameters are not sufficiently strict, this method can return a very large amout of
   * RDDObjects, causing memory issues.
   *
   * @param tmRangeTimeRange
   * @return RDD[T]
   */
  final def getRDD(tmRange: TimeRange): RDD[T] = {
    val txnContext = getCurrentTransactionContext
    var values: Array[T] = Array[T]()
    if (txnContext != null) {
      val fndVal = txnContext.getNodeCtxt.getEnvCtxt.getRDD(txnContext.transId, getFullName, null, tmRange, null)
      if (fndVal != null)
        values = fndVal.map(v => v.asInstanceOf[T])
    }
    RDD.makeRDD(values)
  }

  /**
   * Return a RDD - If the filtering parameters are not sufficiently strict, this method can return a very large amout of
   * RDDObjects, causing memory issues.
   *
   * @param f MessageContainerBase => Boolean
   * @return RDD[T]
   */
  final def getRDD(f: MessageContainerBase => Boolean): RDD[T] = {
    val txnContext = getCurrentTransactionContext
    var values: Array[T] = Array[T]()
    if (txnContext != null) {
      val fndVal = txnContext.getNodeCtxt.getEnvCtxt.getRDD(txnContext.transId, getFullName, null, null, f)
      if (fndVal != null)
        values = fndVal.map(v => v.asInstanceOf[T])
    }
    RDD.makeRDD(values)
  }

  /**
   * Return a RDD
   *
   * @param key Array[String]
   * @param tmRange TimeRange
   * @param f MessageContainerBase => Boolean
   * @return RDD[T]
   */
  final def getRDD(key: Array[String], tmRange: TimeRange, f: MessageContainerBase => Boolean): RDD[T] = {
    val txnContext = getCurrentTransactionContext
    var values: Array[T] = Array[T]()
    if (txnContext != null) {
      val fndVal = txnContext.getNodeCtxt.getEnvCtxt.getRDD(txnContext.transId, getFullName, key.toList, tmRange, f)
      if (fndVal != null)
        values = fndVal.map(v => v.asInstanceOf[T])
    }
    RDD.makeRDD(values)
  }

  /**
   * Return a RDD
   *
   * @param key Array[String]
   * @param f MessageContainerBase => Boolean
   * @return RDD[T]
   */
  final def getRDD(key: Array[String], f: MessageContainerBase => Boolean): RDD[T] = {
    val txnContext = getCurrentTransactionContext
    var values: Array[T] = Array[T]()
    if (txnContext != null) {
      val fndVal = txnContext.getNodeCtxt.getEnvCtxt.getRDD(txnContext.transId, getFullName, key.toList, null, f)
      if (fndVal != null)
        values = fndVal.map(v => v.asInstanceOf[T])
    }
    RDD.makeRDD(values)
  }

  /**
   * Return a RDD
   *
   * @param key Array[String]
   * @param tmRange TimeRange
   * @param f MessageContainerBase => Boolean
   * @return RDD[T]
   */
  final def getRDD(key: Array[String], tmRange: TimeRange): RDD[T] = {
    val txnContext = getCurrentTransactionContext
    var values: Array[T] = Array[T]()
    if (txnContext != null) {
      val fndVal = txnContext.getNodeCtxt.getEnvCtxt.getRDD(txnContext.transId, getFullName, key.toList, tmRange, null)
      if (fndVal != null)
        values = fndVal.map(v => v.asInstanceOf[T])
    }
    RDD.makeRDD(values)
  }

  /**
   * Return a RDD
   *
   * @param key Array[String]
   * @return RDD[T]
   */
  final def getRDD(key: Array[String]): RDD[T] = {
    val txnContext = getCurrentTransactionContext
    var values: Array[T] = Array[T]()
    if (txnContext != null) {
      val fndVal = txnContext.getNodeCtxt.getEnvCtxt.getRDD(txnContext.transId, getFullName, key.toList, null, null)
      if (fndVal != null)
        values = fndVal.map(v => v.asInstanceOf[T])
    }
    RDD.makeRDD(values)
  }

  /**
   * Save a an RDDObject in the underlying history
   *
   * @param inst T
   */
  final def saveOne(inst: T): Unit = {
    val txnContext = getCurrentTransactionContext
    if (txnContext != null) {
      val obj = inst.asInstanceOf[MessageContainerBase]
      txnContext.getNodeCtxt.getEnvCtxt.saveOne(txnContext.transId, getFullName, obj.PartitionKeyData.toList, obj)
    }
  }

  /**
   * Save a an RDDObject in the underlying history
   *
   * @param key Array[String]
   * @param inst T
   */
  final def saveOne(key: Array[String], inst: T): Unit = {
    val txnContext = getCurrentTransactionContext
    if (txnContext != null) {
      txnContext.getNodeCtxt.getEnvCtxt.saveOne(txnContext.transId, getFullName, key.toList, inst.asInstanceOf[MessageContainerBase])
    }
  }

  /**
   * Save a an RDDObject in the underlying history
   *
   * @param data Array[String]
   */
  final def saveRDD(data: RDD[T]): Unit = {
    val txnContext = getCurrentTransactionContext
    if (txnContext != null) {
      txnContext.getNodeCtxt.getEnvCtxt.saveRDD(txnContext.transId, getFullName, data.Collection.map(v => v.asInstanceOf[MessageContainerBase]).toArray)
    }
  }
}

object JavaRDDObject {
  def fromRDDObject[T: ClassTag](rddObj: RDDObject[T]): JavaRDDObject[T] = new JavaRDDObject[T](rddObj)
  def toRDDObject[T](rddObj: JavaRDDObject[T]): RDDObject[T] = rddObj.rddObj
}

/**
 * Defined and implements methods to enable Java code to interact with RDD[T].
 */
abstract class AbstractJavaRDDObjectLike[T, This <: JavaRDDObjectLike[T, This]]
  extends JavaRDDObjectLike[T, This]

/**
 * A trait that implements a number of methods to be accessible from the Java programming language.
 *
 * Defines and implements same functionality as RDDObject but reuturn types are java Optional[T] instead of scal
 * Option[T] where appropriate
 */
trait JavaRDDObjectLike[T, This <: JavaRDDObjectLike[T, This]] {
  private val LOG = LogManager.getLogger(getClass);
  private def wrapRDD(rdd: RDD[T]): JavaRDD[T] = JavaRDD.fromRDD(rdd)

  /**
   * Return a JavaRDDObject implementation for a given rddObject[T]
   * @param rddObj RDDObject[T]
   * @return JavaRDDObject[T]
   */
  def wrapRDDObject(rddObj: RDDObject[T]): This

  implicit val classTag: ClassTag[T]

  /**
   * Return an uderlying RDDObject[T]
   * @return RDDObject[T]
   */
  def rddObj: RDDObject[T]

  // get builder
  /**
   * @see RDDObject
   */
  def build: T = rddObj.build

  /**
   * @see RDDObject
   */
  def build(from: T): T = rddObj.build(from)

  // First group of functions retrieve one object (either recent or for a given key & filter)
  // Get recent entry for the Current Key
  /**
   * @see RDDObject but returns a java Optional[T] instead of a scala Option[T]
   */
  def getRecent: Optional[T] = Utils.optionToOptional(rddObj.getRecent)

  /**
   * @see RDDObject
   */
  def getRecentOrNew: T = rddObj.getRecentOrNew

  // Get recent entry for the given key
  /**
   * @see RDDObject but returns a java Optional[T] instead of a scala Option[T]
   */
  def getRecent(key: Array[String]): Optional[T] = Utils.optionToOptional(rddObj.getRecent(key))

  /**
   * @see RDDObject
   */
  def getRecentOrNew(key: Array[String]): T = rddObj.getRecentOrNew(key)

  /**
   * @see RDDObject but returns a java Optional[T] instead of a scala Option[T]
   */
  def getOne(tmRange: TimeRange, f: JFunction1[MessageContainerBase, java.lang.Boolean]): Optional[T] = Utils.optionToOptional(rddObj.getOne(tmRange, (x => f.call(x).booleanValue())))

  /**
   * @see RDDObject
   */
  def getOneOrNew(tmRange: TimeRange, f: JFunction1[MessageContainerBase, java.lang.Boolean]): T = rddObj.getOneOrNew(tmRange, (x => f.call(x).booleanValue()))

  /**
   * @see RDDObject but returns a java Optional[T] instead of a scala Option[T]
   */
  def getOne(key: Array[String], tmRange: TimeRange, f: JFunction1[MessageContainerBase, java.lang.Boolean]): Optional[T] = Utils.optionToOptional(rddObj.getOne(key, tmRange, (x => f.call(x).booleanValue())))

  /**
   * @see RDDObject
   */
  def getOneOrNew(key: Array[String], tmRange: TimeRange, f: JFunction1[MessageContainerBase, java.lang.Boolean]): T = rddObj.getOneOrNew(key, tmRange, (x => f.call(x).booleanValue()))

  // This group of functions retrieve collection of objects 
  /**
   * @see RDDObject
   */
  def getRDDForCurrKey(f: JFunction1[MessageContainerBase, java.lang.Boolean]): JavaRDD[T] = rddObj.getRDDForCurrKey((x => f.call(x).booleanValue()))

  /**
   * @see RDDObject
   */
  def getRDDForCurrKey(tmRange: TimeRange): JavaRDD[T] = rddObj.getRDDForCurrKey(tmRange)

  /**
   * @see RDDObject
   */
  def getRDDForCurrKey(tmRange: TimeRange, f: JFunction1[MessageContainerBase, java.lang.Boolean]): JavaRDD[T] = rddObj.getRDDForCurrKey(tmRange, (x => f.call(x).booleanValue()))

  // With too many messages, these may fail - mostly useful for message types where number of messages are relatively small 
  /**
   * @see RDDObject
   */
  def getRDD(): JavaRDD[T] = wrapRDD(rddObj.getRDD())

  /**
   * @see RDDObject
   */
  def getRDD(tmRange: TimeRange, f: JFunction1[MessageContainerBase, java.lang.Boolean]): JavaRDD[T] = wrapRDD(rddObj.getRDD(tmRange, { x: MessageContainerBase => f.call(x).booleanValue() }))
  /**
   * @see RDDObject
   */
  def getRDD(tmRange: TimeRange): JavaRDD[T] = wrapRDD(rddObj.getRDD(tmRange))

  /**
   * @see RDDObject
   */
  def getRDD(f: JFunction1[MessageContainerBase, java.lang.Boolean]): JavaRDD[T] = wrapRDD(rddObj.getRDD((x => f.call(x).booleanValue())))

  /**
   * @see RDDObject
   */
  def getRDD(key: Array[String], tmRange: TimeRange, f: JFunction1[MessageContainerBase, java.lang.Boolean]): JavaRDD[T] = wrapRDD(rddObj.getRDD(key, tmRange, (x => f.call(x).booleanValue())))

  /**
   * @see RDDObject
   */
  def getRDD(key: Array[String], f: JFunction1[MessageContainerBase, java.lang.Boolean]): JavaRDD[T] = wrapRDD(rddObj.getRDD(key, { x: MessageContainerBase => f.call(x).booleanValue() }))

  /**
   * @see RDDObject
   */
  def getRDD(key: Array[String], tmRange: TimeRange): JavaRDD[T] = wrapRDD(rddObj.getRDD(key, tmRange))

  /**
   * @see RDDObject
   */
  def getRDD(key: Array[String]): JavaRDD[T] = wrapRDD(rddObj.getRDD(key))

  // Saving data
  /**
   * @see RDDObject
   */
  def saveOne(inst: T): Unit = rddObj.saveOne(inst)

  /**
   * @see RDDObject
   */
  def saveOne(key: Array[String], inst: T): Unit = rddObj.saveOne(key, inst)

  /**
   * @see RDDObject
   */
  def saveRDD(data: RDD[T]): Unit = rddObj.saveRDD(data)
}

/**
 * This class actually implements the JavaRDDObjectLike, refer to that trait for doc
 */
class JavaRDDObject[T](val rddObj: RDDObject[T])(implicit val classTag: ClassTag[T])
  extends AbstractJavaRDDObjectLike[T, JavaRDDObject[T]] {

  /**
   * Return a JavaRDDObject implementation for a given rddObject[T]
   * @param rddObj RDDObject[T]
   * @return JavaRDDObject[T]
   */
  override def wrapRDDObject(rddObj: RDDObject[T]): JavaRDDObject[T] = JavaRDDObject.fromRDDObject(rddObj)

  /**
   * get a JavaRDDObject from.
   * @return JavaRDDObject[T]
   */
  def toJavaRDDObject: JavaRDDObject[T] = this

  /**
   * get an RDDObject from a JavaRDDObject
   * @return RDDObject[T]
   */
  def toRDDObject: RDDObject[T] = rddObj
}

/**
 * Some simple StringUtils to use while wroking with RDDs
 */
object StringUtils {
  val emptyStr = ""
  val quotes3 = "\"\"\""
  val quotes2 = "\"\""
  val spaces2 = "  "
  val spaces3 = "   "
  val spaces4 = spaces3 + " "

  val tab1 = spaces2
  val tab2 = tab1 + tab1
  val tab3 = tab2 + tab1
  val tab4 = tab3 + tab1
  val tab5 = tab4 + tab1
  val tab6 = tab5 + tab1
  val tab7 = tab6 + tab1
  val tab8 = tab7 + tab1
  val tab9 = tab8 + tab1

  val lfTab0 = "\n"
  val lfTab1 = lfTab0 + tab1
  val lfTab2 = lfTab1 + tab1
  val lfTab3 = lfTab2 + tab1
  val lfTab4 = lfTab3 + tab1
  val lfTab5 = lfTab4 + tab1
  val lfTab6 = lfTab5 + tab1
  val lfTab7 = lfTab6 + tab1
  val lfTab8 = lfTab7 + tab1
  val lfTab9 = lfTab8 + tab1

  // return zero for NULL and empty strings, for others length of the given string
  def len(str: String) = str match { case null => 0; case _ => str.length }
  def emptyIfNull(str: String) = str match { case null => ""; case _ => str }
  def emptyIfNull(str: String, fn: String => String) = str match { case null => ""; case _ => fn(str) }

  def catStrs(strs: Array[String], sep: String, fn: String => String) = {
    val sb = new StringBuilder();
    strs.foldLeft(false)((f, s) => { if (f) sb.append(sep); sb.append(emptyIfNull(s, fn)); true });
    sb.result
  }

  def catStrs(strs: Array[String], sep: String): String = catStrs(strs, sep, { str: String => str })

  // returns the given string with its first character in lower case. no checks for null or empty string
  private def firstLowerNoCheck(str: String) = { str.charAt(0).toLower + str.substring(1) }

  // returns a new string with its first character in lower case  and rest unchanged (null or empty string returns the given string)
  def firstLower(str: String) = len(str) match { case 0 => str; case _ => firstLowerNoCheck(str) }

  /**
   * Add a new line character to each element.
   */
  def AddAsLinesToBuf(buf: StringBuilder, lines: Array[String]) = lines.foreach(line => buf.append(line).append("\n"))
  def firstNonNull(args: String*): String = { if (args == null) return null; for (arg <- args) { if (arg != null) return arg }; null; }

  def prefixSpaces(str: String): String = {
    val cnt = str.prefixLength({ ch => (ch == ' ') || (ch == '\t') });
    if (cnt == 0) emptyStr
    else str.substring(0, cnt)
  }

  /**
   * Return last non-empty element.
   */
  def lastNonEmptyLine(strs: Array[String]): String = {
    var idx = strs.length
    while (idx > 0) { idx -= 1; val str = strs(idx); if (str.length > 0) return str; }
    emptyStr
  }
}

import StringUtils._
import RddUtils._

/**
 * Used for various RDD and RDDObject methods.
 */
object RddDate {
  val milliSecsPerHr = 3600 * 1000
  val milliSecsPerDay = 24 * milliSecsPerHr
  def currentDateTime = RddDate(currentDateTimeInMs)
  def currentGmtDateTime = RddDate(currentGmtDateTimeInMs)
  def currentDateTimeInMs = GetCurDtTmInMs
  def currentGmtDateTimeInMs = GetCurGmtDtTmInMs
}

/**
 * Used for various RDD and RDDObject methods.
 */
case class RddDate(val dttmInMs: Long) {

  /**
   * Return difference in hours between this object and the input
   * @param dt RddDate
   * @return int
   *
   */
  def timeDiffInHrs(dt: RddDate): Int = {
    if (dt.dttmInMs > dttmInMs)
      return ((dt.dttmInMs - dttmInMs) / RddDate.milliSecsPerHr).toInt
    ((dttmInMs - dt.dttmInMs) / RddDate.milliSecsPerHr).toInt
  }

  /**
   * Return difference in days between this object and the input
   * @param dt RddDate
   * @return int
   *
   */
  def timeDiffInDays(dt: RddDate): Int = {
    if (dt.dttmInMs > dttmInMs)
      return ((dt.dttmInMs - dttmInMs) / RddDate.milliSecsPerDay).toInt
    ((dttmInMs - dt.dttmInMs) / RddDate.milliSecsPerDay).toInt
  }

  //BUGBUG:: Do we need to return in UTC??????

  /**
   * Return the TimeRange object that spans the last N days. N is an input
   *
   * @param days Int
   * @return TimeRange
   *
   */
  def lastNdays(days: Int): TimeRange = {
    val cal = Calendar.getInstance()
    cal.setTimeInMillis(dttmInMs)
    cal.add(Calendar.DATE, days * -1)
    return TimeRange(cal.getTime.getTime(), dttmInMs)
  }

  //BUGBUG:: Do we need to return in UTC??????
  /**
   * Return the TimeRange object that spans the next N days. N is an input
   *
   * @param days Int
   * @return TimeRange
   *
   */
  def nextNdays(days: Int): TimeRange = {
    val cal = Calendar.getInstance()
    cal.setTimeInMillis(dttmInMs)
    cal.add(Calendar.DATE, days)
    return TimeRange(dttmInMs, cal.getTime.getTime())
  }

  def getDateTimeInMs = dttmInMs
}

/**
 * Utilities that can be used to work with RDD and RDDObjects
 */
object RddUtils {
  def IsEmpty(str: String) = (str == null || str.length == 0)
  def IsNotEmpty(str: String) = (!IsEmpty(str))
  def IsEmptyArray[T](a: Array[T]) = (a == null || a.size == 0)
  def IsNotEmptyArray[T](a: Array[T]) = (!IsEmptyArray(a))

  def SimpDateFmtTimeFromMs(tmMs: Long, fmt: String): String = { new java.text.SimpleDateFormat(fmt).format(new java.util.Date(tmMs)) }

  // Get current time (in ms) as part of valid identifier and appends to given string
  def GetCurDtTmAsId(prefix: String): String = { prefix + SimpDateFmtTimeFromMs(GetCurDtTmInMs, "yyyyMMdd_HHmmss_SSS") }
  def GetCurDtTmAsId: String = GetCurDtTmAsId("")

  def GetCurDtTmStr: String = { SimpDateFmtTimeFromMs(GetCurDtTmInMs, "yyyy:MM:dd HH:mm:ss.SSS") }
  def GetCurDtStr: String = { SimpDateFmtTimeFromMs(GetCurDtTmInMs, "yyyy:MM:dd") }
  def GetCurDtTmInMs: Long = { System.currentTimeMillis }
  def GetCurGmtDtTmInMs: Long = { Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis() }
  def elapsed[A](f: => A): (Long, A) = { val s = System.nanoTime; val ret = f; ((System.nanoTime - s), ret); }
}
