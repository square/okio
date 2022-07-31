plugins {
  id("com.vanniktech.maven.publish.base")
  id("java-platform")
}

dependencies {
  constraints {
    api(projects.okio)
    api(projects.okioFakefilesystem)
    if (kmpJsEnabled) {
      api(projects.okioNodefilesystem)
    }
  }
}

extensions.configure<PublishingExtension> {
  publications.create("maven", MavenPublication::class) {
    from(project.components.getByName("javaPlatform"))
  }
}
