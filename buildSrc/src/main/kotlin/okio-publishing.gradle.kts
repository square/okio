// Working in this file? If IntelliJ doesn't syntax-highlight it properly you can cut&paste it into
// one of the build.gradle.kts files that consume it, make edits there, and then move it back.

apply(plugin = "maven-publish")
apply(plugin = "signing")

val javadocsJar by tasks.creating(Jar::class) {
  val dokkaGfm by tasks.getting {}
  dependsOn(dokkaGfm)
  classifier = "javadoc"
  from("${buildDir}/dokka/gfm")
}

fun isReleaseBuild(): Boolean = VERSION_NAME?.contains("SNAPSHOT") == false

val VERSION_NAME: String?
  get() = findProperty("VERSION_NAME") as String?

val POM_ARTIFACT_ID: String?
  get() = findProperty("POM_ARTIFACT_ID") as String?

val POM_NAME: String?
  get() = findProperty("POM_NAME") as String?

val RELEASE_REPOSITORY_URL: String
  get() = findProperty("RELEASE_REPOSITORY_URL") as String?
    ?: "https://oss.sonatype.org/service/local/staging/deploy/maven2/"

val SNAPSHOT_REPOSITORY_URL: String
  get() = findProperty("SNAPSHOT_REPOSITORY_URL") as String?
    ?: "https://oss.sonatype.org/content/repositories/snapshots/"

extensions.getByType<PublishingExtension>().apply {
  publications {
    all {
      if (this !is MavenPublication) return@all

      artifact(javadocsJar)
      pom {
        this.description.set("A modern I/O API for Java")
        this.name.set(POM_NAME)
        this.url.set("https://github.com/square/okio/")
        licenses {
          license {
            this.name.set("The Apache Software License, Version 2.0")
            this.url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            this.distribution.set("repo")
          }
        }
        scm {
          this.url.set("https://github.com/square/okio/")
          this.connection.set("scm:git:git://github.com/square/okio.git")
          this.developerConnection.set("scm:git:ssh://git@github.com/square/okio.git")
        }
        developers {
          developer {
            this.id.set("square")
            this.name.set("Square, Inc.")
          }
        }
      }
    }

    // Use default artifact name for the JVM target
    val kotlinMultiplatform by getting {
      if (this !is MavenPublication) return@getting
      artifactId = POM_ARTIFACT_ID + "-multiplatform"
    }
    val jvm = findByName("jvm")
    if (jvm is MavenPublication) {
      jvm.artifactId = POM_ARTIFACT_ID
    }
  }

  repositories {
    maven {
      setUrl(if (isReleaseBuild()) RELEASE_REPOSITORY_URL else SNAPSHOT_REPOSITORY_URL)
      credentials {
        username = findProperty("mavenCentralRepositoryUsername") as String? ?: ""
        password = findProperty("mavenCentralRepositoryPassword") as String? ?: ""
      }
    }
    maven {
      this.setName("test")
      this.setUrl("file://${rootProject.buildDir}/localMaven")
    }
  }
}

extensions.getByType<SigningExtension>().apply {
  val signingKey = findProperty("signingKey") as String? ?: ""
  if (signingKey.isNotEmpty()) {
    useInMemoryPgpKeys(signingKey, "")
  }
  setRequired { isReleaseBuild() }
  sign(extensions.getByType<PublishingExtension>().publications)
}
