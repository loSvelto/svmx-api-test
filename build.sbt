name := "quick-force"

version := "1.0"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.6.3",
  "org.webjars" % "bootstrap" % "3.3.6",
  javaCore,
  javaWs,
  guice
)

routesGenerator := InjectedRoutesGenerator

TaskKey[Unit]("default") := {
  (run in Compile).toTask("").value
}