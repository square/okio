plugins {
  id("com.vanniktech.maven.publish.base")
  id("java-platform")
}

dependencies {
  constraints {
    api(projects.okio)
    api(projects.okio.group + ":okio-jvm:" + projects.okio.version)
    api(projects.okioFakefilesystem)
    if (kmpJsEnabled) {
      // No typesafe project accessor as the accessor won't exist if kmpJs is not enabled.
      api(project(":okio-nodefilesystem"))
    }
  }
}

extensions.configure<PublishingExtension> {
  publications.create("maven", MavenPublication::class) {
    from(project.components.getByName("javaPlatform"))
  }
}
