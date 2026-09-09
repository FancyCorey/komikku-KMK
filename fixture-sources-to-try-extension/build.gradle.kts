plugins {
    id("mihon.android.application")
}

configurations.matching { it.name.endsWith("RuntimeClasspath") }.configureEach {
    // Extensions execute inside Komikku, which already owns the Kotlin runtime.
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
}

android {
    namespace = "app.komikku.fixture.sources"

    defaultConfig {
        minSdk = 26
        versionCode = 1
        versionName = "1.6.0"
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "fixture"
    productFlavors {
        create("alpha") {
            dimension = "fixture"
            applicationId = "app.komikku.fixture.sources.alpha"
            buildConfigField("long", "SOURCE_ID", "910000000000000001L")
            resValue("string", "fixture_source_name", "Fixture Source Alpha")
        }
        create("beta") {
            dimension = "fixture"
            applicationId = "app.komikku.fixture.sources.beta"
            buildConfigField("long", "SOURCE_ID", "910000000000000002L")
            resValue("string", "fixture_source_name", "Fixture Source Beta")
        }
    }
}

dependencies {
    compileOnly(projects.core.common)
    compileOnly(projects.sourceApi)
    compileOnly(libs.okhttp.core)
}
