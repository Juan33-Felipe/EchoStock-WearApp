import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val configuredApiKey: String = localProperties.getProperty("echostock.apiKey")
    ?: System.getenv("ECHOSTOCK_API_KEY")
    ?: "echostock-dev-key"
val configuredApiBaseUrl: String = (
    localProperties.getProperty("echostock.apiBaseUrl")
        ?: System.getenv("ECHOSTOCK_API_BASE_URL")
        ?: "http://10.0.2.2:8080/api/v1/"
    ).let { if (it.endsWith("/")) it else "$it/" }

android {
    namespace = "com.echostock.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.echostock.wear"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // 10.0.2.2 = localhost del PC visto desde el emulador (sobrescribible con echostock.apiBaseUrl).
        buildConfigField("String", "API_BASE_URL", "\"$configuredApiBaseUrl\"")
        buildConfigField("String", "API_KEY", "\"$configuredApiKey\"")
        buildConfigField("String", "OPERATOR_CODE", "\"OP-07\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
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
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// ---- Modelo de voz offline (Vosk, español). No se versiona: se descarga una vez a .cache/ ----
val voskModel = "vosk-model-small-es-0.42"
val voskAssetsRoot = rootProject.layout.projectDirectory.dir(".cache/vosk-assets")

val downloadVoskModel by tasks.registering {
    description = "Descarga el modelo de reconocimiento de voz en español ($voskModel)."
    val outDir = voskAssetsRoot.dir("model-es").asFile
    outputs.dir(outDir)
    onlyIf { !File(outDir, "uuid").exists() }
    doLast {
        val zip = File(temporaryDir, "$voskModel.zip")
        uri("https://alphacephei.com/vosk/models/$voskModel.zip").toURL().openStream().use { input ->
            zip.outputStream().use { input.copyTo(it) }
        }
        outDir.deleteRecursively()
        project.copy {
            from(zipTree(zip)) {
                eachFile { path = path.substringAfter('/') }
                exclude("**/README")
            }
            includeEmptyDirs = false
            into(outDir)
        }
        // StorageService de Vosk usa este archivo para saber si ya desempaquetó el modelo.
        File(outDir, "uuid").writeText(voskModel)
    }
}

android.sourceSets["main"].assets.srcDir(voskAssetsRoot)
tasks.named("preBuild") { dependsOn(downloadVoskModel) }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.vosk.android) { exclude(group = "net.java.dev.jna") }
    implementation(libs.jna) { artifact { type = "aar" } }
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
