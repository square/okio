plugins {
  id("com.vanniktech.maven.publish.base")
  id("java-platform")
}

dependencies {
  constraints {
    api(project(":okio"))
    api(project(":okio-fakefilesystem"))
    if (kmpJsEnabled) {
      api(project(":okio-nodefilesystem"))
    }
  }
}

extensions.configure<PublishingExtension> {
  publications.create("maven", MavenPublication::class) {
    from(project.components.getByName("javaPlatform"))
  }
}
