plugins {
    kotlin("multiplatform") version "2.3.10"
}

group = "deltawaken"

repositories {
    mavenCentral()
}

kotlin {
    jvm()

    jvmToolchain(17)

    sourceSets {
        commonMain {
            // No dependencies — pure Kotlin math only
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
