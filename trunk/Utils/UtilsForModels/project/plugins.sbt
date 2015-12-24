resolvers += Resolver.url("sbt-plugin-releases-scalasbt", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.5.0")

addSbtPlugin("com.orrsella" % "sbt-sublime" % "1.0.9")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.11.2")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")

addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.6.2")
