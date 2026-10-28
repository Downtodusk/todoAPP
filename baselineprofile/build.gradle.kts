plugins { id("com.android.test") }

android {
    namespace = "com.example.todo.baselineprofile"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        create("benchmark") { isDebuggable = true; signingConfig = signingConfigs.getByName("debug"); matchingFallbacks += "release" }
        create("profile") { isDebuggable = true; signingConfig = signingConfigs.getByName("debug"); matchingFallbacks += "release" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
androidComponents { beforeVariants(selector().all()) { it.enable = it.buildType in listOf("benchmark", "profile") } }
dependencies {
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
}
