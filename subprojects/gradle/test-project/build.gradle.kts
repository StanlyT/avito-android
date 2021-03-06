plugins {
    id("convention.kotlin-jvm")
    id("convention.publish-kotlin-library")
    id("convention.libraries")
    id("convention.gradle-testing")
}

dependencies {
    api(libs.okhttpMockWebServer)
    api(gradleTestKit())

    implementation(project(":gradle:process"))
    implementation(project(":gradle:android"))
    implementation(project(":common:truth-extensions"))
    implementation(testFixtures(project(":common:logger")))

    implementation(libs.kotlinReflect)
    implementation(libs.truth)

    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinTestJUnit)
}
