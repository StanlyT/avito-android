package com.avito.android.build_verdict.internal

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle

abstract class BaseBuildListener : BuildListener {
    override fun buildStarted(gradle: Gradle) {}

    override fun settingsEvaluated(settings: Settings) {}

    override fun projectsLoaded(gradle: Gradle) {}

    override fun projectsEvaluated(gradle: Gradle) {}

    override fun buildFinished(result: BuildResult) {}
}
