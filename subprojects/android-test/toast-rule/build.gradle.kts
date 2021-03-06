plugins {
    id("convention.kotlin-android-library")
    id("convention.publish-android-library")
    id("convention.libraries")
}

dependencies {
    implementation(libs.androidAnnotations)
    implementation(libs.junit)

    implementation(project(":android-lib:proxy-toast"))
    implementation(project(":android-test:ui-testing-core"))
    implementation(project(":common:junit-utils"))
}
