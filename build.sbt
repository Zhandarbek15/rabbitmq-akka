ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.0"

lazy val root = (project in file("."))
  .settings(
    name := "rabbit-akka"
  )
libraryDependencies += "com.rabbitmq" % "amqp-client" % "5.16.0"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.8.0"