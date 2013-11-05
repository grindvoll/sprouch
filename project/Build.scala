import sbt._
import Keys._

object ApplicationBuild extends Build {

  val dependencies = Seq(
    "org.scalatest" %% "scalatest" % "2.0.RC3" % "test",
    "io.spray" % "spray-can" % "1.2-RC2" withSources(),
    "io.spray" % "spray-client" % "1.2-RC2" withSources(),
    "io.spray" % "spray-http" % "1.2-RC2" withSources(),
    "io.spray" % "spray-httpx" % "1.2-RC2" withSources(),
    "io.spray" %%  "spray-json" % "1.2.5",
    "com.typesafe.akka" %% "akka-actor" % "2.2.3",
    "com.novocode" % "junit-interface" % "0.10" % "test"
  )

  val main = Project(id = "sprouch", base = new File("."), settings = Project.defaultSettings ++ Seq(
    (scalaVersion := "2.10.3"),
    (libraryDependencies ++= dependencies),
    (resolvers ++= Seq(
        "spray repo" at "http://repo.spray.io",
        "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
    )),
    (testOptions in Test := Nil),
    (parallelExecution in Test := false),
    (publishTo := Some(Resolver.file(
        "gh-pages",
        new File("/home/k/workspaces/sprouch-pages/repository/")
    ))),
    (version := "0.6.0"),
    (scalacOptions += "-feature"),
    (scalacOptions += "-deprecation")
  ))

}
