# wartremover-spark

[![Build Status](https://travis-ci.org/omervk/wartremover-spark.svg?branch=master)](https://travis-ci.org/wartremover/wartremover-spark)

A selection of additional warts for wartremover managed by the community.

## Usage

Add the following to your `project/plugins.sbt`:

```scala
addSbtPlugin("io.omervk" % "sbt-wartremover-spark" % "1.2.3")
```

```scala
// In build.sbt
wartremoverErrors += SparkWart.UnserializableCapture // Or whichever warts you want to add
```

## Warts

Here is a list of warts under the `io.omervk.wartremover.spark.warts` package.
