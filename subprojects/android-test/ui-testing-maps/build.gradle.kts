plugins {
    id("convention.kotlin-android-library")
    id("convention.publish-android-library")
    id("convention.libraries")
}

dependencies {
    api(project(":android-test:ui-testing-core"))
    api(libs.playServicesMaps)
}
