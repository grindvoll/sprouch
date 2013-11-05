name := "sprouch"

version := "0.6.0"

libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "2.0.RC3" % "test",
    "io.spray" % "spray-can" % "1.2-RC2" withSources(),
    "io.spray" % "spray-client" % "1.2-RC2" withSources(),
    "io.spray" % "spray-http" % "1.2-RC2" withSources(),
    "io.spray" % "spray-httpx" % "1.2-RC2" withSources(),
    "io.spray" %%  "spray-json" % "1.2.5",
    "com.typesafe.akka" %% "akka-actor" % "2.2.3",
    "com.novocode" % "junit-interface" % "0.10" % "test"
)
  
resolvers ++= Seq(
        "spray repo" at "http://repo.spray.io",
        "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

scalacOptions ++= Seq("-feature", "-deprecation")

testOptions in Test := Nil

parallelExecution in Test := false

scalaVersion := "2.10.3"
