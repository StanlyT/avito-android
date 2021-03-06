plugins {
    id("convention.kotlin-jvm")
    id("convention.publish-gradle-plugin")
    id("convention.libraries")
    id("convention.gradle-testing")
}

dependencies {
    api(project(":gradle:impact-shared"))

    implementation(gradleApi())
    implementation(project(":gradle:android"))
    implementation(project(":gradle:gradle-logger"))
    implementation(project(":common:files"))
    implementation(project(":gradle:git"))
    implementation(project(":gradle:build-environment"))
    implementation(project(":gradle:gradle-extensions"))
    implementation(project(":gradle:sentry-config"))
    implementation(project(":gradle:statsd-config"))

    implementation(libs.antPattern)
    implementation(libs.kotlinPlugin)

    gradleTestImplementation(project(":gradle:test-project"))
    gradleTestImplementation(project(":gradle:impact-shared-test-fixtures"))
}

gradlePlugin {
    plugins {
        create("impact") {
            id = "com.avito.android.impact"
            implementationClass = "com.avito.impact.plugin.ImpactAnalysisPlugin"
            displayName = "Impact analysis"
        }
    }
}
