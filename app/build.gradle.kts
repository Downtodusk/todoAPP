import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}
val detailMotion = providers.gradleProperty("detailMotion").getOrElse("B")
require(detailMotion in listOf("A", "B")) { "detailMotion must be A or B" }
val signingFile = rootProject.file("keystore.properties")
val releaseIdentity = Properties().apply { if (signingFile.isFile) signingFile.inputStream().use { load(it) } }
android {
    namespace = "com.example.todo"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "com.example.todo"
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = if (detailMotion == "B") "0.4.3" else "0.4.3-A"
        buildConfigField("boolean", "CLASSIC_DETAIL_MOTION", (detailMotion == "A").toString())
        testInstrumentationRunner = "com.example.todo.LocalizedTestRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    // Both languages must remain available offline, including future app bundles.
    bundle { language { enableSplit = false } }
    signingConfigs {
        if (signingFile.isFile) create("release") {
            listOf("storeFile", "storePassword", "keyAlias", "keyPassword").forEach {
                check(!releaseIdentity.getProperty(it).isNullOrBlank()) { "Missing release signing property: $it" }
            }
            storeFile = rootProject.file(releaseIdentity.getProperty("storeFile"))
            storePassword = releaseIdentity.getProperty("storePassword")
            keyAlias = releaseIdentity.getProperty("keyAlias")
            keyPassword = releaseIdentity.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        create("profile") {
            initWith(getByName("benchmark"))
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += "release"
        }
    }
    sourceSets.getByName("profile") {
        java.srcDir("src/benchmark/java")
        manifest.srcFile("src/benchmark/AndroidManifest.xml")
    }
    sourceSets.getByName("androidTest").assets.srcDir("schemas")
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    // Compose still requests Espresso 3.5.0; 3.7.0 avoids removed InputManager reflection on API 37.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Explicit opt-in only. Ordinary builds/tests never contact a model provider.
tasks.register<JavaExec>("evaluateNotices") {
    dependsOn("compileDebugUnitTestKotlin", "processDebugUnitTestJavaRes")
    mainClass.set("com.example.todo.ai.NoticeParseEvalTest")
    doFirst {
        listOf("LLM_BASE_URL", "LLM_KEY", "LLM_MODEL").forEach { check(!System.getenv(it).isNullOrBlank()) { "$it must be set for explicit model evaluation" } }
        classpath = tasks.named<Test>("testDebugUnitTest").get().classpath
    }
}

val requireReleaseIdentity by tasks.registering {
    doLast { check(signingFile.isFile) { "Release signing is not configured. Restore keystore.properties and its key, or explicitly run scripts/create-release-key.py for a new identity." } }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(requireReleaseIdentity) }
