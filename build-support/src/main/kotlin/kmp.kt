// If false - JS targets will not be configured in multiplatform projects.
val kmpJsEnabled = System.getProperty("kjs", "true").toBoolean()

// If false - Native targets will not be configured in multiplatform projects.
val kmpNativeEnabled = System.getProperty("knative", "true").toBoolean()
