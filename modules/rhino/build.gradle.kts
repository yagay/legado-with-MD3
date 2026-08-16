plugins {
    alias(libs.plugins.android.library)
}

android {
    compileSdk = 37
    namespace = "com.script"
    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
    defaultConfig {
        minSdk = 26

        consumerProguardFiles += file("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint {
        checkDependencies = true
        targetSdk = 37
    }
    testOptions {
        targetSdk = 37
    }
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-Xlint:deprecation")
    }
}

dependencies {
    // Legacy engine used by all existing sources unless explicitly switched.
    api(libs.mozilla.rhino)
    // Current Legado HtmlUnit fork. Packages are org.htmlunit.corejs.*, so it can coexist
    // with the legacy org.mozilla.javascript runtime in the same APK.
    api("org.htmlunit:htmlunit-core-js:5.3.0-legado.3")

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.androidx.collection)
}
