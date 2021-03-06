plugins {
    id("convention.kotlin-jvm")
    id("convention.publish-kotlin-library")
    id("convention.libraries")
}

dependencies {
    implementation(gradleApi())

    testImplementation(project(":gradle:test-project"))
}
