import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

// The code both apps share: Supabase calls, models and the Free/Pro, streak and validation
// rules. Android uses it as a library; iOS gets it as the `Shared` framework that the Xcode
// project builds (see iosApp/README.md). Screens stay native: Compose and SwiftUI.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.stepq.shared"
        compileSdk = 37
        minSdk = 28
        withHostTestBuilder {}
    }

    val xcf = XCFramework("Shared")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            xcf.add(this)
        }
    }

    compilerOptions {
        // Expected/actual classes are fine for the tiny platform layer here.
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.supabase.auth)
            api(libs.supabase.postgrest)
            api(libs.supabase.storage)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.json)
            api(libs.multiplatform.settings)
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
