package com.omervk.wartremover.spark.test

import com.omervk.wartremover.spark.warts.UnserializableCapture
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import org.wartremover.test.{ResultAssertions, WartTestTraverser}
import org.apache.spark.sql.{Dataset, SparkSession}

import scala.util.Random

class UnserializableCaptureTest extends FunSuite with ResultAssertions with BeforeAndAfterAll {
  private var spark: SparkSession = _

  override protected def beforeAll(): Unit = {
    spark = SparkSession.builder()
      .master("local[1]")
      .getOrCreate()
  }

  override protected def afterAll(): Unit = {
    spark.close()
  }

  test("Captured primitive instances do not raise an error") {
    val implicits = spark.implicits
    import implicits._
    val dataset: Dataset[Int] = spark.createDataset(Seq.empty[Int])

    val capture = Random.nextInt()

    val result = WartTestTraverser(UnserializableCapture) {
      dataset.map { value: Int =>
        value + capture
      }
    }

    assertEmpty(result)
  }

  test("Captured serializable instances do not raise an error") {
    val implicits = spark.implicits
    import implicits._
    val dataset: Dataset[Int] = spark.createDataset(Seq.empty[Int])

    val capture = new SerializableWithInt(Random.nextInt())

    val result = WartTestTraverser(UnserializableCapture) {
      dataset.map { value: Int =>
        value + capture.value
      }
    }

    assertEmpty(result)
  }

  test("Captured instances that are not serializable themselves are caught") {
    val implicits = spark.implicits
    import implicits._
    val dataset: Dataset[Int] = spark.createDataset(Seq.empty[Int])

    val unserializableCapture = new UnserializableWithInt(Random.nextInt())

    val result = WartTestTraverser(UnserializableCapture) {
      dataset.map { value: Int =>
        value + unserializableCapture.value
      }
    }

    assertError(result)("Functions sent to Spark may not close over unserializable values (unserializableCapture)")
  }

  test("Similarly named instances in internal block do not interfere with checking") {
    val implicits = spark.implicits
    import implicits._
    val dataset: Dataset[Int] = spark.createDataset(Seq.empty[Int])

    val unserializableCapture = new UnserializableWithInt(Random.nextInt())

    val result = WartTestTraverser(UnserializableCapture) {
      dataset.map { value: Int =>
        def func = {
          val unserializableCapture = new UnserializableWithInt(Random.nextInt())
          unserializableCapture
        }

        value + func.value + unserializableCapture.value
      }
    }

    assertError(result)("Functions sent to Spark may not close over unserializable values (unserializableCapture)")
  }

  test("Unserializable instances instantiated inside the block do not error out") {
    val implicits = spark.implicits
    import implicits._
    val dataset: Dataset[Int] = spark.createDataset(Seq.empty[Int])

    val result = WartTestTraverser(UnserializableCapture) {
      dataset.map { value: Int =>
        val baz = new UnserializableWithInt(Random.nextInt())

        value + baz.value
      }
    }

    assertEmpty(result)
  }

  test("Catches chained calls") {
    val implicits = spark.implicits
    import implicits._
    val dataset: Dataset[Int] = spark.createDataset(Seq.empty[Int])

    val unserializableCapture = new UnserializableWithInt(Random.nextInt())

    val result = WartTestTraverser(UnserializableCapture) {
      dataset
        .map { value: Int => value + 1 }
        .filter { value: Int => value == unserializableCapture.value }
    }

    assertError(result)("Functions sent to Spark may not close over unserializable values (unserializableCapture)")
  }

  test("Catches chained calls in a different order") {
    val implicits = spark.implicits
    import implicits._
    val dataset: Dataset[Int] = spark.createDataset(Seq.empty[Int])

    val unserializableCapture = new UnserializableWithInt(Random.nextInt())

    val result = WartTestTraverser(UnserializableCapture) {
      dataset
        .filter { value: Int => value == 1 }
        .map { value: Int => value + unserializableCapture.value }
    }

    assertError(result)("Functions sent to Spark may not close over unserializable values (unserializableCapture)")
  }

  test("Does not catch non-Spark functions") {
    val unserializableCapture = new UnserializableWithInt(Random.nextInt())
    val dataset = Seq.empty[Int]

    val result = WartTestTraverser(UnserializableCapture) {
      dataset
        .map { value: Int => value + unserializableCapture.value }
    }

    assertEmpty(result)
  }

  // TODO: Test RDDs
  /*
  TODO: s in closure means instance of B, which is not serializable
  class B {

    val s="456"
    def add=(rdd:RDD[Int])=>{
      rdd.map(e=>e+" "+s).foreach(println)
    }
  }
   */
  // TODO: Refer to test cases here: https://github.com/apache/spark/blob/master/core/src/test/scala/org/apache/spark/util/ClosureCleanerSuite.scala

  test("UnserializableCapture wart obeys SuppressWarnings") {
    val implicits = spark.implicits
    import implicits._
    val dataset: Dataset[Int] = spark.createDataset(Seq.empty[Int])

    val unserializableCapture = new UnserializableWithInt(Random.nextInt())

    val result = WartTestTraverser(UnserializableCapture) {
      @SuppressWarnings(Array("com.omervk.wartremover.spark.warts.UnserializableCapture"))
      def wrapper() = {
        dataset.map { value: Int =>
          value + unserializableCapture.value
        }
      }
    }

    assertEmpty(result)
  }
}

final class UnserializableWithInt(val value: Int)
final class SerializableWithInt(val value: Int) extends Serializable
