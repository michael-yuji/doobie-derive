name         := "doobie-derive"
version      := "0.0.1"
organization := "xyz.myuji"
ThisBuild / scalaVersion := "2.13.8"

val circeVersion = "0.13.0"
val doobieVersion = "0.10.0"

lazy val derive = project
  .in(file("modules/derive"))
  .settings(
    scalacOptions ++= Seq(
      "-Ymacro-annotations",
      "-deprecation"
    ),
    libraryDependencies += ("org.scala-lang" % "scala-reflect" % scalaVersion.value),
    libraryDependencies += "org.tpolecat" %% "doobie-core" % doobieVersion,
  )

lazy val circe = project
  .in(file("modules/circe"))
  .dependsOn(derive)
  .settings(
    libraryDependencies += "io.circe" %% "circe-core" % circeVersion
  )