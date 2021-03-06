package com.avito.android.plugin.build_metrics

import com.avito.android.gradle.metric.GradleCollector
import com.avito.android.gradle.metric.MetricsConsumer
import com.avito.android.plugin.build_metrics.internal.AppBuildTimeListener
import com.avito.android.plugin.build_metrics.internal.CompositeBuildMetricsListener
import com.avito.android.plugin.build_metrics.internal.ConfigurationTimeListener
import com.avito.android.plugin.build_metrics.internal.SlowTasksListener
import com.avito.android.plugin.build_metrics.internal.TotalBuildTimeListener
import com.avito.android.sentry.environmentInfo
import com.avito.android.stats.statsd
import com.avito.kotlin.dsl.getOptionalStringProperty
import com.avito.kotlin.dsl.isRoot
import com.avito.logger.GradleLoggerFactory
import com.avito.logger.create
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * Inspired by [gradle-metrics-plugin](https://github.com/nebula-plugins/gradle-metrics-plugin)
 */
public open class BuildMetricsPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        check(project.isRoot()) {
            "Plugin must be applied to the root project but was applied to ${project.path}"
        }

        val loggerFactory = GradleLoggerFactory.fromPlugin(this, project)
        val logger = loggerFactory.create<BuildMetricsPlugin>()

        if (!project.pluginIsEnabled) {
            logger.debug("Build metrics plugin is disabled")
            return
        }

        project.tasks.register<CollectTeamcityMetricsTask>("collectTeamcityMetrics") {
            buildId.set(project.getOptionalStringProperty("avito.build.metrics.teamcityBuildId"))
        }

        GradleCollector.initialize(
            project,
            consumers(project)
        )
    }

    private fun consumers(project: Project): List<MetricsConsumer> {
        val metricTracker = BuildMetricTracker(
            project.environmentInfo(),
            project.statsd
        )
        val buildResultListeners = listOf(
            ConfigurationTimeListener(metricTracker),
            TotalBuildTimeListener(metricTracker),
            SlowTasksListener(metricTracker),
            AppBuildTimeListener.from(project, metricTracker)
        )
        return listOf(
            CompositeBuildMetricsListener(
                listeners = buildResultListeners,
            )
        )
    }
}

@Suppress("UnstableApiUsage")
internal val Project.pluginIsEnabled: Boolean
    get() = providers
        .gradleProperty(enabledProp)
        .forUseAtConfigurationTime()
        .map { it.toBoolean() }
        .getOrElse(false)

private const val enabledProp = "avito.build.metrics.enabled"
