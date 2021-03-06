plugins {
    id("convention.kotlin-jvm")
    id("convention.publish-gradle-plugin")
    id("convention.libraries")
    id("convention.gradle-testing")
}

dependencies {
    implementation(project(":gradle:gradle-logger"))
    implementation(project(":gradle:android"))
    implementation(project(":gradle:gradle-extensions"))
    implementation(project(":common:okhttp"))

    implementation(libs.retrofit)
    implementation(libs.retrofitConverterGson)
    implementation(libs.okhttpLogging)

    gradleTestImplementation(project(":gradle:test-project"))
    gradleTestImplementation(project(":common:test-okhttp"))
}

gradlePlugin {
    plugins {
        create("prosector") {
            id = "com.avito.android.prosector"
            implementationClass = "ProsectorPlugin"
            displayName = "Prosector"
        }
    }
}
