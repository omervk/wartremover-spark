package com.omervk.wartremover.spark

import sbt.Keys._
import sbt._
import wartremover.WartRemover
import wartremover.WartRemover.autoImport.wartremoverClasspaths
import wartremover.spark.{ AutoGenerated, Exported }

object SparkWartsPlugin extends AutoPlugin {

  object autoImport {
    val SparkWarts: Exported = AutoGenerated
  }

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = WartRemover

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    libraryDependencies += "com.omervk" %% "wartremover-spark" % AutoGenerated.Version$ % Provided,
    libraryDependencies ++= {
      // https://github.com/wartremover/wartremover/issues/106#issuecomment-51963190
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 10)) =>
          Seq(compilerPlugin(
            "org.scalamacros" % "paradise" % AutoGenerated.macroParadiseVersion cross CrossVersion.full))
        case _ =>
          Nil
      }
    },
    wartremoverClasspaths ++= {
      (dependencyClasspath in Compile).value.files
        .find(_.name.contains("wartremover-spark"))
        .map(_.toURI.toString)
        .toList
    })
}
