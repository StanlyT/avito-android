plugins {
    id("convention.kotlin-jvm")
}

dependencies {
    api(project(":gradle:artifactory-app-backup"))

    implementation(project(":common:test-okhttp"))
}
