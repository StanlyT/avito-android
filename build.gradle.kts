@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayExtension.PackageConfig
import com.jfrog.bintray.gradle.BintrayExtension.VersionConfig
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    /**
     * https://docs.gradle.org/current/userguide/base_plugin.html
     * base plugin added to add wiring on check->build tasks for detekt
     */
    base
    id("org.jetbrains.kotlin.jvm") apply false
    id("com.android.application") apply false
    id("com.avito.android.build-verdict")
    id("io.gitlab.arturbosch.detekt")
    id("com.autonomousapps.dependency-analysis") apply false
    id("com.jfrog.bintray") version "1.8.4" apply false
}

/**
 * We use exact version to provide consistent environment and avoid build cache issues
 * (AGP tasks has artifacts from build tools)
 */
val buildTools = "29.0.3"
val javaVersion = JavaVersion.VERSION_1_8
val compileSdk = 29
val infraVersion: Provider<String> = providers.gradleProperty("infraVersion").forUseAtConfigurationTime()
val detektVersion: Provider<String> = providers.systemProperty("detektVersion").forUseAtConfigurationTime()
val artifactoryUrl = providers.gradleProperty("artifactoryUrl").forUseAtConfigurationTime()
val projectVersion = providers.gradleProperty("projectVersion").forUseAtConfigurationTime()
val kotlinVersion = providers.systemProperty("kotlinVersion").forUseAtConfigurationTime()
val androidGradlePluginVersion = providers.systemProperty("androidGradlePluginVersion").forUseAtConfigurationTime()
val publishToArtifactoryTask = tasks.register<Task>("publishToArtifactory") {
    group = "publication"
    doFirst {
        requireNotNull(artifactoryUrl.orNull) {
            "Property artifactoryUrl is required for publishing"
        }
    }
}

val publishReleaseTaskName = "publishRelease"

val finalProjectVersion: String = System.getProperty("avito.project.version").let { env ->
    if (env.isNullOrBlank()) projectVersion.get() else env
}

if (gradle.startParameter.taskNames.contains("buildHealth")) {
    // Reasons to disabling by default:
    // The plugin schedules heavy LocateDependenciesTask tasks even without analysis
    apply(plugin = "com.autonomousapps.dependency-analysis")
}

dependencies {
    add("detektPlugins", "io.gitlab.arturbosch.detekt:detekt-formatting:${detektVersion.get()}")
}

subprojects {
    group = "com.avito.android"
    version = finalProjectVersion

    /**
     * https://www.jetbrains.com/help/teamcity/build-script-interaction-with-teamcity.html#BuildScriptInteractionwithTeamCity-ReportingBuildNumber
     */
    val teamcityPrintVersionTask = tasks.register("teamcityPrintReleasedVersion") {
        group = "publication"
        description = "Prints teamcity service message to display released version as build number"

        doLast {
            logger.lifecycle("##teamcity[buildNumber '$finalProjectVersion']")
        }
    }

    tasks.register(publishReleaseTaskName) {
        group = "publication"
        finalizedBy(teamcityPrintVersionTask)
    }

    plugins.withType<AppPlugin> {
        configure<BaseExtension> {
            packagingOptions {
                exclude("META-INF/*.kotlin_module")
            }
        }
    }

    plugins.withType<LibraryPlugin> {
        val libraryExtension = this@subprojects.extensions.getByType<LibraryExtension>()

        val sourcesTask = tasks.register<Jar>("sourcesJar") {
            archiveClassifier.set("sources")
            from(libraryExtension.sourceSets["main"].java.srcDirs)
        }

        val publishingVariant = "release"

        plugins.withType<MavenPublishPlugin> {
            libraryExtension.libraryVariants
                .matching { it.name == publishingVariant }
                .whenObjectAdded {
                    extensions.getByType<PublishingExtension>().apply {
                        publications {
                            register<MavenPublication>(publishingVariant) {
                                from(components.getAt(publishingVariant))
                                artifact(sourcesTask.get())
                            }
                        }
                    }
                    configureBintray(publishingVariant)
                }
        }
    }

    plugins.matching { it is AppPlugin || it is LibraryPlugin }.whenPluginAdded {
        configure<BaseExtension> {
            sourceSets {
                named("main").configure { java.srcDir("src/main/kotlin") }
                named("androidTest").configure { java.srcDir("src/androidTest/kotlin") }
                named("test").configure { java.srcDir("src/test/kotlin") }
            }

            buildToolsVersion(buildTools)
            compileSdkVersion(compileSdk)

            compileOptions {
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
            }

            defaultConfig {
                minSdkVersion(21)
                targetSdkVersion(28)
            }

            lintOptions {
                isAbortOnError = false
                isWarningsAsErrors = true
                textReport = true
                isQuiet = true
            }
        }
    }

    plugins.withType<KotlinBasePluginWrapper> {
        dependencies {
            constraints {
                add("api", Dependencies.Avito.proxyToast) { version { strictly(infraVersion.get()) } }
                add("api", Dependencies.Avito.testInhouseRunner) { version { strictly(infraVersion.get()) } }
                add("api", Dependencies.Avito.testReport) { version { strictly(infraVersion.get()) } }
                add("api", Dependencies.Avito.junitUtils) { version { strictly(infraVersion.get()) } }
                add("api", Dependencies.Avito.toastRule) { version { strictly(infraVersion.get()) } }
                add("api", Dependencies.Avito.testAnnotations) { version { strictly(infraVersion.get()) } }
                add("api", Dependencies.Avito.uiTestingCore) { version { strictly(infraVersion.get()) } }
                add("api", Dependencies.Avito.reportViewer) { version { strictly(infraVersion.get()) } }
                add("api", Dependencies.Avito.fileStorage) { version { strictly(infraVersion.get()) } }
                add("api", Dependencies.Avito.okhttp) { version { strictly(infraVersion.get()) } }
                add("api", Dependencies.Avito.time) { version { strictly(infraVersion.get()) } }
            }
        }

        this@subprojects.run {
            tasks {
                withType<KotlinCompile> {
                    kotlinOptions {
                        jvmTarget = javaVersion.toString()
                        allWarningsAsErrors = true
                        freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
                    }
                }
            }

            dependencies {
                add("implementation", Dependencies.kotlinStdlib)
            }
        }
    }

    plugins.withId("kotlin-android") {
        configureJunit5Tests()
    }

    // todo more precise configuration for gradle plugins, no need for gradle testing in common kotlin modules
    plugins.withId("kotlin") {

        configureJunit5Tests()

        extensions.getByType<JavaPluginExtension>().run {
            withSourcesJar()
        }

        this@subprojects.tasks {
            withType<Test> {
                systemProperty(
                    "kotlinVersion",
                    plugins.getPlugin(KotlinPluginWrapper::class.java).kotlinPluginVersion
                )
                systemProperty("compileSdkVersion", compileSdk)
                systemProperty("buildToolsVersion", buildTools)
                systemProperty("androidGradlePluginVersion", androidGradlePluginVersion.get())

                /**
                 * IDEA добавляет специальный init script, по нему понимаем что запустили в IDE
                 * используется в `:test-project`
                 */
                systemProperty(
                    "isInvokedFromIde",
                    gradle.startParameter.allInitScripts.find { it.name.contains("ijtestinit") } != null
                )

                systemProperty("isTest", true)

                systemProperty(
                    "junit.jupiter.execution.timeout.default",
                    TimeUnit.MINUTES.toSeconds(10)
                )
            }
        }

        dependencies {
            add("testImplementation", gradleTestKit())
        }
    }

    plugins.withId("java-test-fixtures") {
        dependencies {
            add("testFixturesImplementation", Dependencies.Test.junitJupiterApi)
            add("testFixturesImplementation", Dependencies.Test.truth)
        }
    }

    tasks.withType<Test> {
        systemProperty("rootDir", "${project.rootDir}")

        val testProperties = listOf(
            "avito.kubernetes.url",
            "avito.kubernetes.token",
            "avito.kubernetes.cert",
            "avito.kubernetes.namespace",
            "avito.slack.test.channel",
            "avito.slack.test.token",
            "avito.slack.test.workspace",
            "avito.elastic.endpoint",
            "avito.elastic.indexpattern",
            "teamcityBuildId"
        )
        testProperties.forEach { key ->
            val property = if (project.hasProperty(key)) {
                project.property(key)!!.toString()
            } else {
                ""
            }
            systemProperty(key, property)
        }
    }

    plugins.withType<JavaGradlePluginPlugin> {
        extensions.getByType<GradlePluginDevelopmentExtension>().run {
            isAutomatedPublishing = false
        }
    }

    plugins.withType<MavenPublishPlugin> {
        extensions.getByType<PublishingExtension>().run {

            publications {
                // todo should not depend on ordering
                if (plugins.hasPlugin("kotlin")) {
                    val publicationName = "maven"

                    register<MavenPublication>(publicationName) {
                        from(components["java"])
                        afterEvaluate {
                            artifactId = this@subprojects.getOptionalExtra("artifact-id") ?: this@subprojects.name
                        }
                    }

                    afterEvaluate {
                        configureBintray(publicationName)
                    }
                }
            }

            repositories {
                if (!artifactoryUrl.orNull.isNullOrBlank()) {
                    maven {
                        name = "artifactory"
                        setUrl("${artifactoryUrl.orNull}/libs-release-local")
                        credentials {
                            username = project.getOptionalExtra("avito.artifactory.user")
                            password = project.getOptionalExtra("avito.artifactory.password")
                        }
                    }
                }
            }
        }

        if (!artifactoryUrl.orNull.isNullOrBlank()) {
            publishToArtifactoryTask.configure {
                dependsOn(tasks.named("publishAllPublicationsToArtifactoryRepository"))
            }
        }
    }
}

val Project.sourceSets: SourceSetContainer
    get() = (this as ExtensionAware).extensions.getByName("sourceSets") as SourceSetContainer

val SourceSetContainer.main: NamedDomainObjectProvider<SourceSet>
    get() = named<SourceSet>("main")

fun Project.getOptionalExtra(key: String): String? {
    return if (extra.has(key)) {
        (extra[key] as? String)?.let { if (it.isBlank()) null else it }
    } else {
        null
    }
}

fun Project.configureBintray(vararg publications: String) {
    extensions.findByType<BintrayExtension>()?.run {

        // todo fail fast with meaningful error message
        user = getOptionalExtra("avito.bintray.user")
        key = getOptionalExtra("avito.bintray.key")

        setPublications(*publications)

        dryRun = false
        publish = true
        // You can use override for inconsistently uploaded artifacts
        // Examples of issues:
        // - NoHttpResponseException: api.bintray.com:443 failed to respond
        //   (https://github.com/bintray/gradle-bintray-plugin/issues/325)
        // - Could not upload to 'https://api.bintray.com/...':
        //   HTTP/1.1 405 Not Allowed 405 Not Allowed405 Not Allowednginx
        override = false
        pkg(
            closureOf<PackageConfig> {
                repo = "maven"
                userOrg = "avito"
                name = "avito-android"
                setLicenses("mit")
                vcsUrl = "https://github.com/avito-tech/avito-android.git"

                version(
                    closureOf<VersionConfig> {
                        name = finalProjectVersion
                    }
                )
            }
        )
    }

    tasks.named(publishReleaseTaskName).configure {
        dependsOn(tasks.named("bintrayUpload"))
    }
}

fun Project.configureJunit5Tests() {
    dependencies {
        add("testImplementation", Dependencies.Test.junitJupiterApi)

        add("testRuntimeOnly", Dependencies.Test.junitPlatformRunner)
        add("testRuntimeOnly", Dependencies.Test.junitPlatformLauncher)
        add("testRuntimeOnly", Dependencies.Test.junitJupiterEngine)

        add("testImplementation", Dependencies.Test.truth)

        if (name != "truth-extensions") {
            add("testImplementation", project(":subprojects:common:truth-extensions"))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        maxParallelForks = 8
        failFast = true

        /**
         * fix for retrofit `WARNING: Illegal reflective access by retrofit2.Platform`
         * see square/retrofit/issues/3341
         */
        jvmArgs = listOf("--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED")
    }
}

tasks.withType<Wrapper> {
    // sources unavailable with BIN until https://youtrack.jetbrains.com/issue/IDEA-231667 resolved
    distributionType = Wrapper.DistributionType.ALL
    gradleVersion = "6.8.2"
}

val detektAll = tasks.register<Detekt>("detektAll") {
    description = "Runs over whole code base without the starting overhead for each module."
    parallel = true
    setSource(files(projectDir))

    /**
     * About config:
     * yaml is a copy of https://github.com/detekt/detekt/blob/master/detekt-core/src/main/resources/default-detekt-config.yml
     * all rules are disabled by default, enabled one by one
     */
    config.setFrom(files(project.rootDir.resolve("detekt.yml")))
    buildUponDefaultConfig = false

    include("**/*.kt")
    include("**/*.kts")
    exclude("**/resources/**")
    exclude("**/build/**")
    reports {
        xml.enabled = false
        html.enabled = false
    }
}

tasks.named("check").dependsOn(detektAll)
