plugins {
    `kotlin-dsl`
    id("convention.libraries")
}

group = "com.avito.android.buildlogic"

dependencies {
    implementation("com.avito.android.buildlogic:libraries")
    implementation(libs.kotlinPlugin)
    implementation(libs.nebulaIntegTest)
}

repositories {
    mavenCentral()
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}
