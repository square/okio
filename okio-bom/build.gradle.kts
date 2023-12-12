plugins {
  id("com.vanniktech.maven.publish.base")
  id("java-platform")
}

collectBomConstraints()

extensions.configure<PublishingExtension> {
  publications.create("maven", MavenPublication::class) {
    from(project.components.getByName("javaPlatform"))
  }
}
