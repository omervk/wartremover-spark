val wartremoverVersion = "2.3.7"
//val scala210 = "2.10.7"
val scala211 = "2.11.12"
val scala212 = "2.12.7"
//val scala213 = "2.13.0-M5"

enablePlugins(GitVersioning)

lazy val commonSettings: Seq[Setting[_]] = Seq(
  organization := "com.omervk",
  licenses := Seq(
    "Apache-2.0" ->
      url("http://www.apache.org/licenses/LICENSE-2.0.txt")
  ),
  publishMavenStyle := false,
  bintrayRepository := "wartremover-spark",
  bintrayOrganization in bintray := None,
  bintrayVcsUrl := Some("git@github.com:omervk/wartremover-spark.git"),
//  homepage := None,
//  useGpg := true,
  pomExtra :=
    <scm>
      <url>git@github.com:omervk/wartremover-spark.git</url>
      <connection>scm:git:git@github.com:omervk/wartremover-spark.git</connection>
    </scm>
      <developers>
        <developer>
          <name>Omer van Kloeten</name>
        </developer>
      </developers>,
  git.useGitDescribe := true,
)

lazy val root = project.in(file("."))
  .withId("wartremover-spark")
  .aggregate(core, tests, sbtPlug)
  .settings(commonSettings)
  .settings(
    publishArtifact := false,
    releaseCrossBuild := true,
    releaseProcess := Seq[ReleaseStep](
      releaseStepCommandAndRemaining("+publishSigned")
    )
  )

def macroParadiseVersion = "2.1.1"

lazy val core = project.in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "wartremover-spark",
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, v)) if v >= 13 =>
          // if scala 2.13.0-M4 or later, macro annotations merged into scala-reflect
          // https://github.com/scala/scala/pull/6606
          Nil
        case _ =>
          Seq(compilerPlugin("org.scalamacros" % "paradise" % macroParadiseVersion cross CrossVersion.full))
      }
    },
    libraryDependencies := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 10)) =>
          libraryDependencies.value :+ ("org.scalamacros" %% "quasiquotes" % "2.0.1")
        case _ =>
          libraryDependencies.value
      }
    },
    scalaVersion := scala211,
    crossScalaVersions := Seq(/*scala210, scala211, scala213 */ scala211, scala212),
    libraryDependencies ++= Seq(
      "org.wartremover" %% "wartremover" % wartremoverVersion
    )
  )

lazy val tests = project.in(file("tests"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    publish := {},
    scalaVersion := scala211,
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-sql" % "2.3.2" % Test,
      "org.scalatest" %% "scalatest" % "3.0.5" % Test
    )
  )

lazy val sbtPlug: Project = project.in(file("sbt-plugin"))
  .withId("sbt-plugin")
  .settings(commonSettings)
  .settings(
    commonSettings,
    sbtPlugin := true,
    name := "sbt-wartremover-spark",
    scalaVersion := scala212,
    addSbtPlugin("org.wartremover" %% "sbt-wartremover" % wartremoverVersion),
    sourceGenerators in Compile += Def.task {
      val base = (sourceManaged in Compile).value
      val file = base / "wartremover" / "spark" / "AutoGenerated.scala"
      val wartsDir = core.base / "src" / "main" / "scala" / "com" / "omervk" / "wartremover" / "spark" / "warts"
      val warts: Seq[String] = wartsDir
        .listFiles
        .withFilter(f => f.getName.endsWith(".scala") && f.isFile)
        .map(_.getName.replaceAll("""\.scala$""", ""))
        .sorted
      val content =
        s"""package wartremover.spark
           |import wartremover.Wart
           |// Autogenerated code, see build.sbt.
           |object AutoGenerated extends Exported {
           |  val Version$$ = "${version.value}"
           |  def macroParadiseVersion: String = "$macroParadiseVersion"
           |}
           |
           |trait Exported {
           |  lazy val all: List[Wart] = List(${warts mkString ", "})
           |  /** A fully-qualified class name of a custom Wart implementing `org.wartremover.WartTraverser`. */
           |  private[this] def w(nm: String): Wart = new Wart(s"com.omervk.wartremover.spark.warts.$$nm")
           |  ${warts.map(w => s"""val $w = w("$w")""").mkString("\n  ")}
           |}
           |""".stripMargin
      IO.write(file, content)
      Seq(file)
    }
  )
