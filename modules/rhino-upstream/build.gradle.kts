plugins {
    alias(libs.plugins.android.library)
}

android {
    compileSdk = 37
    namespace = "com.script.upstream"
    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint {
        checkDependencies = true
        targetSdk = 37
    }
}

dependencies {
    api("org.htmlunit:htmlunit-core-js:5.3.0-legado.3")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.androidx.collection)
}
