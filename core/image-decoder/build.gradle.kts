plugins {
    id("mihon.library")
    kotlin("android")
}

android {
    namespace = "tachiyomi.decoder"

    defaultConfig {
        minSdk = 21

        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
