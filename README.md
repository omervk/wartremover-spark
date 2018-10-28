# wartremover-spark

[![Build Status](https://travis-ci.org/omervk/wartremover-spark.svg?branch=master)](https://travis-ci.org/wartremover/wartremover-spark)

[Apache Spark](https://spark.apache.org/) warts for [wartremover](https://github.com/wartremover/wartremover).

## Usage

Add the following to your `project/plugins.sbt`:

```scala
addSbtPlugin("com.omervk" % "sbt-wartremover-spark" % "0.0.1")
```

```scala
// In build.sbt
wartremoverErrors ++= SparkWart.All
// Or alternatively
wartremoverErrors += SparkWart.UnserializableCapture
```

## Warts

Here is a list of warts under the `com.omervk.wartremover.spark.warts` package.

### UnserializableCapture

One of the most painful things with Spark is getting an error that a captures value is not serializable, since this only
ever happens at runtime.

**Important Note:** This wart is a work in progress to cover as many of the cases where this happens. Please open issues detailing
issues with it (both false-positives and false-negatives) with detailed repros.

```scala
val captured = new Unserializable(value = Random.nextInt())

dataset.map { value: Int =>
  value + captured.value // Won't compile: Functions sent to Spark may not close over unserializable values (captured)
}
```