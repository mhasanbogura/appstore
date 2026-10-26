// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.gradle.api.GradleException

tasks.register("publishGitHub") {
  group = "publishing"
  description = "Commit and push all changes in Web/ to GitHub. Usage: gradlew publishGitHub -PcommitMessage=\"your message\""
  doLast {
    val webDir = rootProject.file("../Web")
    val message = (project.findProperty("commitMessage") as String?)
      ?: "Update: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    fun runGit(vararg args: String): String {
      val proc = ProcessBuilder(listOf("git", *args))
        .directory(webDir)
        .redirectErrorStream(false)
        .start()
      val output = proc.inputStream.readBytes().toString(Charsets.UTF_8)
      if (proc.waitFor() != 0) {
        throw GradleException("git ${args.joinToString(" ")} failed:\n$output")
      }
      return output
    }

    val status = runGit("status", "--porcelain")
    if (status.isBlank()) {
      println("No changes in Web/ to upload.")
      return@doLast
    }

    runGit("add", "-A")
    runGit("commit", "-m", message)
    runGit("push")
    println("Pushed to GitHub: $message")
  }
}
