plugins {
    id("convention.kotlin-jvm")
    id("convention.publish-gradle-plugin")
    id("convention.libraries")
    id("convention.gradle-testing")
}

dependencies {
    implementation(project(":gradle:android"))
    implementation(project(":gradle:gradle-extensions"))

    gradleTestImplementation(project(":gradle:test-project"))
}

gradlePlugin {
    plugins {
        create("robolectricConfig") {
            id = "com.avito.android.robolectric"
            implementationClass = "com.avito.android.plugin.RobolectricPlugin"
            displayName = "Robolectric config"
        }
    }
}
