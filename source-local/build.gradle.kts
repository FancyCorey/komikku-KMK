import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.library")
    kotlin("multiplatform")
}

kotlin {
    androidTarget()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.sourceApi)
                api(projects.i18n)
                // SY -->
                api(projects.i18nSy)
                // SY <--

                implementation(libs.unifile)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(projects.core.archive)
                implementation(projects.core.common)
                implementation(projects.coreMetadata)

                // Move ChapterRecognition to separate module?
                implementation(projects.domain)

                implementation(kotlinx.bundles.serialization)
            }
        }
        // KMK_CLAUDE_REMAINING_FIXTURE_BLOCKER_IMPLEMENTATION_PLAN_2026-08-03 Phase 4: this module
        // had no test source set at all before this pass. androidUnitTest is the standard Kotlin
        // Multiplatform Android-target unit test source set name; `mihon.library`'s configureTest()
        // (buildSrc/src/main/kotlin/mihon/buildlogic/ProjectExtensions.kt) already wires
        // useJUnitPlatform() for every Test task in every module using that plugin, so no additional
        // JUnit5 platform configuration is needed here.
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.bundles.test)
                implementation(kotlinx.coroutines.test)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

android {
    namespace = "tachiyomi.source.local"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    // KMK_CLAUDE_REMAINING_FIXTURE_BLOCKER_IMPLEMENTATION_PLAN_2026-08-03 Phase 4: com.hippo.unifile's
    // RawFile (the java.io.File-backed UniFile implementation these new JVM unit tests exercise via
    // UniFile.fromFile) calls android.text.TextUtils.isEmpty(...) internally, which the default
    // Android JAR stub throws on ("not mocked") rather than executing. Every name this test suite
    // passes is non-empty, so TextUtils.isEmpty's real behavior would also return false here --
    // isReturnDefaultValues (returning the stub default, false, for that call) is behaviorally
    // identical to the real implementation for every case these tests exercise, not a semantic
    // shortcut around anything under test.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}
