plugins {
    id("convention.kotlin-jvm")
    id("convention.publish-kotlin-library")
    id("convention.libraries")
}

dependencies {
    implementation(libs.kotlinCompilerEmbeddable)

    testImplementation(project(":gradle:test-project")) {
        because("File extensions") // todo probably move to :common:files
    }
}
