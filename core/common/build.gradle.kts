plugins {
    id("mihon.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("com.github.ben-manes.versions")
}

android {
    namespace = "eu.kanade.tachiyomi.core.common"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    implementation(projects.i18n)
    // SY -->
    implementation(projects.i18nSy)
    // SY <--

    api(libs.logcat)

    api(libs.rxjava)

    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.okhttp.brotli)
    api(libs.okhttp.dnsoverhttps)
    api(libs.okio)

    implementation(libs.image.decoder)

    implementation(libs.unifile)
    implementation(libs.libarchive)

    // KMK --> 1.14.0 reconciliation: align :core:common's own compile classpath to the same
    // coroutines version the rest of the app resolves to (matches the platform() pattern already
    // used by :domain, :app, :source-api) -- without this, this module alone resolves an old
    // transitive coroutines-core via some other dependency in this fork's graph, which breaks
    // the coroutines 1.11.0 `resume(value, onCancellation)` 3-arg overload OkHttpExtensions.kt
    // (which lives in this module) now uses.
    implementation(platform(kotlinx.coroutines.bom))
    // KMK <--
    api(kotlinx.coroutines.core)
    api(kotlinx.serialization.json)
    api(kotlinx.serialization.json.okio)

    api(libs.preferencektx)

    implementation(libs.jsoup)

    // Sort
    implementation(libs.natural.comparator)

    // JavaScript engine
    implementation(libs.bundles.js.engine)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // SY -->
    implementation(sylibs.xlog)
    implementation(sylibs.exifinterface)
    // SY <--
}
