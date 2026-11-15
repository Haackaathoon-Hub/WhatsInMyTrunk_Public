import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pleos.vehiclecontext"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pleos.vehiclecontext"
        // Pleos Connect SDK 요구사항: Minimum Android SDK API 28 이상, 권장 API 34
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "mode"
    productFlavors {
        create("prod") {
            dimension = "mode"
            isDefault = true
        }
        create("dev") {
            dimension = "mode"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources.noCompress += "wav"
}

val voiceAssets = layout.buildDirectory.dir("generated/cargoVoiceAssets")
val koreanModel = voiceAssets.map { it.file("cargo_voice/vosk-ko-0.22.zip") }
val prepareCargoSpeechModel by tasks.registering {
    val expectedHash = "eea36124087fed26c59996a4761519458e3bd185e8ea9d9865ad8760c4a1d989"
    inputs.property("modelSha256", expectedHash)
    outputs.file(koreanModel)
    doLast {
        val destination = koreanModel.get().asFile
        if (destination.isFile) {
            val digest = MessageDigest.getInstance("SHA-256")
            destination.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            if (digest.digest().joinToString("") { "%02x".format(it) } == expectedHash) {
                return@doLast
            }
        }
        destination.parentFile.mkdirs()
        val partial = destination.resolveSibling(destination.name + ".part")
        val sources = listOf(
            "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
            "https://huggingface.co/rhasspy/vosk-models/resolve/main/ko/vosk-model-small-ko-0.22.zip"
        )
        var failure: Exception? = null
        for (source in sources) {
            try {
                val connection = URI(source).toURL().openConnection().apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                }
                val digest = MessageDigest.getInstance("SHA-256")
                DigestInputStream(connection.getInputStream().buffered(), digest).use { input ->
                    partial.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                check(digest.digest().joinToString("") { "%02x".format(it) } == expectedHash) {
                    "Korean model checksum mismatch"
                }
                Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                failure = null
                break
            } catch (error: Exception) {
                failure = error
                logger.warn("Could not fetch Korean speech model from $source: ${error.message}")
            } finally {
                partial.delete()
            }
        }
        if (failure != null) throw GradleException("Could not download the offline Korean speech model. Check the PC network and build again.", failure)
    }
}
android.sourceSets.getByName("main").assets.srcDir(voiceAssets)
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareCargoSpeechModel) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation("com.alphacephei:vosk-android:0.3.75")

    // Pleos Connect SDK 공식 의존성 (Vehicle SDK, NaviHelper SDK, SpeechToText SDK)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:2.0.3")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.ui.tooling)
}

