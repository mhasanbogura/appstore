pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "My Application"

include(":app")

// This project folder is synced between a Linux machine and Windows machines
// (Google Drive), so the sdk.dir in local.properties can point to a path that
// does not exist on the current machine. Detect the real SDK location and fix
// local.properties automatically before the Android plugin reads it.
run {
  val localProperties = File(rootDir, "local.properties")
  val os = System.getProperty("os.name").orEmpty().lowercase()
  val sdkDir = if (os.contains("win")) {
    File(System.getenv("LOCALAPPDATA") ?: "${System.getProperty("user.home")}/AppData/Local", "Android/Sdk")
  } else {
    File(System.getProperty("user.home") ?: "/home/mhasanbogura", "android-sdk")
  }
  if (sdkDir.isDirectory) {
    val props = java.util.Properties()
    if (localProperties.exists()) localProperties.inputStream().use { props.load(it) }
    if (props.getProperty("sdk.dir")?.let { File(it).isDirectory } != true) {
      props.setProperty("sdk.dir", sdkDir.absolutePath.replace('\\', '/'))
      localProperties.outputStream().use { props.store(it, "Auto-detected SDK location (machine-specific)") }
      println("local.properties: sdk.dir -> ${sdkDir.absolutePath}")
    }
  }
}
