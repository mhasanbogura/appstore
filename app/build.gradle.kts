import java.io.File
import java.security.KeyStore

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.mahmuduls.appstore"
    minSdk = 24
    targetSdk = 36
    versionCode = getVersionCode()
    versionName = getVersionName()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debugConfig")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// ---------------------------------------------------------------------------
// Keystore self-heal: `gradlew fixKeystore`
// The debug keystore lives in the project root and is synced between machines
// (e.g. via Google Drive), so it can go missing or get corrupted. This task
// validates it and regenerates it automatically when unreadable, so builds
// never fail with "keystore not found" / "failed to load keystore".
// It runs automatically before every build (preBuild depends on it).
// NOTE: regenerating changes the signing key - apps installed with the old
// key can no longer be updated in place (reinstall instead).
// ---------------------------------------------------------------------------
val debugKeystoreFile = rootProject.file("debug.keystore")
val debugStorePassword = "android"
val debugKeyAlias = "androiddebugkey"

fun isKeystoreValid(file: File, storePassword: String, alias: String): Boolean {
    if (!file.isFile || file.length() == 0L) return false
    for (type in listOf("PKCS12", "JKS")) {
        try {
            val ks = KeyStore.getInstance(type)
            file.inputStream().use { ks.load(it, storePassword.toCharArray()) }
            if (ks.containsAlias(alias) && ks.isKeyEntry(alias)) return true
        } catch (_: Exception) {
            // wrong format or corrupted - try the next type
        }
    }
    return false
}

tasks.register("fixKeystore") {
    group = "build"
    description = "Validates debug.keystore and regenerates it if it is missing or corrupted."
    notCompatibleWithConfigurationCache("Runs keytool as an external process with ad-hoc lookup")
    doLast {
        if (isKeystoreValid(debugKeystoreFile, debugStorePassword, debugKeyAlias)) {
            logger.lifecycle("Keystore OK: ${debugKeystoreFile.name}")
            return@doLast
        }
        logger.warn("Keystore missing or corrupted: ${debugKeystoreFile.absolutePath}")
        logger.warn("Regenerating it (apps signed with the old key will need a reinstall)...")

        // Use the keytool that ships with the JDK running Gradle (may not be on PATH).
        val javaHome = System.getProperty("java.home")
        val keytool = listOf(
            "$javaHome/bin/keytool.exe",
            "$javaHome/bin/keytool",
            "$javaHome/../bin/keytool.exe",
            "$javaHome/../bin/keytool",
        ).map { File(it) }.firstOrNull { it.isFile }
            ?: throw GradleException(
                "keytool not found near $javaHome. " +
                    "Install a JDK (not just a JRE) and make sure Gradle runs on it."
            )

        debugKeystoreFile.parentFile?.mkdirs()
        val proc = ProcessBuilder(
            keytool.absolutePath, "-genkeypair",
            "-keystore", debugKeystoreFile.absolutePath,
            "-storetype", "PKCS12",
            "-alias", debugKeyAlias,
            "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "10000",
            "-storepass", debugStorePassword,
            "-keypass", debugStorePassword,
            "-dname", "CN=Android Debug,O=Android,C=US",
            "-noprompt",
        ).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        if (proc.waitFor() != 0 || !isKeystoreValid(debugKeystoreFile, debugStorePassword, debugKeyAlias)) {
            throw GradleException("Failed to regenerate ${debugKeystoreFile.name}:\n$output")
        }
        logger.lifecycle("Regenerated ${debugKeystoreFile.name} (alias '$debugKeyAlias', valid 10000 days)")
    }
}

// Every build self-heals the keystore before compiling/signing.
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("fixKeystore")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.firebase.auth)
  implementation(libs.play.services.auth)
  implementation(libs.credentials)
  implementation(libs.credentials.googleid)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(libs.androidx.work.runtime)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

fun getVersionCode(): Int {
    return project.rootProject.file("version.properties").useLines { lines ->
        lines.firstOrNull { it.startsWith("versionCode=") }
            ?.substringAfter("=")?.trim()?.toIntOrNull() ?: 1
    }
}
fun getVersionName(): String {
    return project.rootProject.file("version.properties").useLines { lines ->
        lines.firstOrNull { it.startsWith("versionName=") }
            ?.substringAfter("=")?.trim() ?: "1.0.0"
    }
}

tasks.register("publishToDrive") {
    doLast {
        val variantDir = file("build/outputs/apk/debug")
        // Resolve the Drive folder per machine (project folder is synced between
        // Linux and Windows), instead of hardcoding one OS's path.
        val driveDir = listOf(
            File("/home/mhasanbogura/Google Drive/mahmudulhasandhk70/App Store"),
            File("C:/Google Drive/mahmudulhasandhk70/App Store"),
        ).firstOrNull { it.isDirectory } ?: File("/home/mhasanbogura/Google Drive/mahmudulhasandhk70/App Store")
        if (!driveDir.exists()) driveDir.mkdirs()
        val verFile = project.rootProject.file("version.properties")
        val verName = verFile.useLines { lines ->
            lines.firstOrNull { it.startsWith("versionName=") }
                ?.substringAfter("=")?.trim() ?: "1.0.0"
        }
        val verCode = verFile.useLines { lines ->
            lines.firstOrNull { it.startsWith("versionCode=") }
                ?.substringAfter("=")?.trim() ?: "1"
        }
        val newName = "App Store_com.mahmuduls.appstore_v${verName}_build_${verCode}.apk"
        val srcFile = file("${variantDir}/app-debug.apk")
        if (srcFile.exists()) {
            srcFile.copyTo(File(driveDir, newName), overwrite = true)
            logger.lifecycle("Published to Drive: ${driveDir}/${newName}")
            driveDir.listFiles()?.filter { it.name.startsWith("App Store_com.mahmuduls.appstore_v") && it.name != newName }?.forEach { old ->
                old.delete()
                logger.lifecycle("Deleted old: ${old.name}")
            }
        } else {
            logger.warn("APK not found: ${srcFile.absolutePath}")
        }
    }
}

tasks.register("releaseToGitHub") {
    dependsOn("publishToDrive")
    doLast {
        val verFile = project.rootProject.file("version.properties")
        val verName = verFile.useLines { lines ->
            lines.firstOrNull { it.startsWith("versionName=") }
                ?.substringAfter("=")?.trim() ?: "1.0.0"
        }
        val verCode = verFile.useLines { lines ->
            lines.firstOrNull { it.startsWith("versionCode=") }
                ?.substringAfter("=")?.trim() ?: "1"
        }
        val srcApk = file("build/outputs/apk/debug/app-debug.apk")
        val tagName = "v$verName"
        val releaseName = "Release v$verName - Build $verCode"

        if (!srcApk.exists()) {
            logger.error("APK not found: ${srcApk.absolutePath}")
            return@doLast
        }

        // Copy APK to AppStore.apk
        val releaseApk = file("build/outputs/apk/debug/AppStore.apk")
        srcApk.copyTo(releaseApk, overwrite = true)

        // Delete existing tag/release if any
        ProcessBuilder("gh", "release", "delete", tagName, "--yes", "--cleanup-tag")
            .directory(project.rootDir)
            .redirectErrorStream(true)
            .start().waitFor()

        // Create release with APK
        val pb = ProcessBuilder(
            "gh", "release", "create", tagName,
            "--title", releaseName,
            "--generate-notes",
            releaseApk.absolutePath
        )
        pb.directory(project.rootDir)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val output = proc.inputStream.bufferedReader().readText()
        val exitCode = proc.waitFor()

        if (exitCode == 0) {
            logger.lifecycle("GitHub release published: $tagName")
            logger.lifecycle(output.trim())
        } else {
            logger.error("GitHub release failed (exit $exitCode): $output")
        }
    }
}

tasks.register("commitAndPush") {
    dependsOn("releaseToGitHub")
    doLast {
        val verFile = project.rootProject.file("version.properties")
        val verName = verFile.useLines { lines ->
            lines.firstOrNull { it.startsWith("versionName=") }
                ?.substringAfter("=")?.trim() ?: "1.0.0"
        }
        val verCode = verFile.useLines { lines ->
            lines.firstOrNull { it.startsWith("versionCode=") }
                ?.substringAfter("=")?.trim() ?: "1"
        }
        
        ProcessBuilder("git", "add", ".").directory(project.rootDir).start().waitFor()
        ProcessBuilder("git", "commit", "-m", "Release v$verName - Build $verCode").directory(project.rootDir).start().waitFor()
        ProcessBuilder("git", "push", "origin", "main").directory(project.rootDir).start().waitFor()
    }
}

tasks.register("incrementVersion") {
    dependsOn("commitAndPush")
    val verFile = project.rootProject.file("version.properties")
    doLast {
        val lines = verFile.readText().lines().toMutableList()
        var newCode = 1
        for (i in lines.indices) {
            if (lines[i].startsWith("versionCode=")) {
                val code = lines[i].substringAfter("=").trim().toIntOrNull() ?: 1
                newCode = code + 3
                lines[i] = "versionCode=${newCode}"
            }
        }
        for (i in lines.indices) {
            if (lines[i].startsWith("versionName=")) {
                val parts = lines[i].substringAfter("=").trim().split(".").toMutableList()
                while (parts.size < 3) parts.add("0")
                var idx = parts.size - 1
                var carry = 1
                while (idx >= 0 && carry > 0) {
                    val n = (parts[idx].toIntOrNull() ?: 0) + carry
                    parts[idx] = "${n % 100}"
                    carry = n / 100
                    idx--
                }
                if (carry > 0) parts.add(0, "${carry}")
                lines[i] = "versionName=" + parts.take(3).joinToString(".")
            }
        }
        verFile.writeText(lines.joinToString("\n") + "\n")
    }
}

tasks.matching { it.name.startsWith("assemble") }.configureEach {
    finalizedBy("publishToDrive", "incrementVersion")
}


