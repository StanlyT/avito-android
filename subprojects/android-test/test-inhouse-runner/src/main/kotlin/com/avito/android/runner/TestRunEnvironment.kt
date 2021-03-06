package com.avito.android.runner

import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.avito.android.elastic.ElasticConfig
import com.avito.android.log.ElasticConfigFactory
import com.avito.android.runner.annotation.resolver.HostAnnotationResolver
import com.avito.android.runner.annotation.resolver.NETWORKING_TYPE_KEY
import com.avito.android.runner.annotation.resolver.NetworkingType
import com.avito.android.runner.annotation.resolver.TEST_METADATA_KEY
import com.avito.android.sentry.SentryConfig
import com.avito.android.stats.SeriesName
import com.avito.android.stats.StatsDConfig
import com.avito.android.test.report.ArgsProvider
import com.avito.android.test.report.model.TestMetadata
import com.avito.android.test.report.video.VideoFeatureValue
import com.avito.report.model.ReportCoordinates
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.util.UUID

sealed class TestRunEnvironment {

    data class ReportConfig(
        val reportApiUrl: String,
        val reportViewerUrl: String
    )

    fun asRunEnvironmentOrThrow(): RunEnvironment {
        if (this !is RunEnvironment) {
            throw RuntimeException("Expected run environment type: RunEnvironment, actual: $this")
        }

        return this
    }

    fun executeIfRealRun(action: (RunEnvironment) -> Unit) {
        if (this is RunEnvironment) {
            action(this)
        }
    }

    /**
     * We use TestOrchestrator when run tests from Android Studio, for consistency with CI runs (isolated app processes)
     * Orchestrator runs this runner before any test to determine which tests to run and spawn separate processes
     *
     * If it is this special run, we don't need to run any of our special moves here, better skip all of them
     *
     * @link https://developer.android.com/training/testing/junit-runner#using-android-test-orchestrator
     */
    object OrchestratorFakeRunEnvironment : TestRunEnvironment() {

        override fun toString(): String = this::class.java.simpleName
    }

    data class InitError(val error: String) : TestRunEnvironment()

    data class RunEnvironment(
        val isImitation: Boolean,

        val deviceId: String,
        val deviceName: String,
        val teamcityBuildId: Int,

        val buildBranch: String,
        val buildCommit: String,
        val testMetadata: TestMetadata,
        val networkType: NetworkingType,
        /**
         * Основное место где определяется apiUrl для тестов
         * Будет использоваться как для всех запросов приложения,
         * так и для всех сервисов ресурсов (RM, messenger, integration...)
         *
         * TestApplication подхватит значение из бандла, который там доступен в статике,
         * putString здесь как раз переопредяет его чтобы оно не оказалось пустым
         *
         * Ресурсы инциализируются в графе даггера и получают этот инстанс HttpUrl
         *
         * 1. Аннотация имеет главный приоритет см. [com.avito.android.runner.annotation.resolver.AnnotationResolver]
         *    и [HostAnnotationResolver] в частности
         * 2. далее instrumentation аргумент apiUrlParameterKey
         */
        val apiUrl: HttpUrl,
        val mockWebServerUrl: String,
        val slackToken: String,
        val videoRecordingFeature: VideoFeatureValue,
        val outputDirectory: Lazy<File>,
        val elasticConfig: ElasticConfig,
        val sentryConfig: SentryConfig,
        val statsDConfig: StatsDConfig,
        val fileStorageUrl: String,
        val reportConfig: ReportConfig?,
        val testRunCoordinates: ReportCoordinates,
        val isSyntheticStepsEnabled: Boolean
    ) : TestRunEnvironment()

    companion object {
        internal const val LOCAL_STUDIO_RUN_ID = -1
    }
}

fun provideEnvironment(
    apiUrlParameterKey: String = "unnecessaryUrl",
    mockWebServerUrl: String = "localhost",
    argumentsProvider: ArgsProvider,
): TestRunEnvironment {
    return try {
        val coordinates = ReportCoordinates(
            planSlug = argumentsProvider.getMandatoryArgument("planSlug"),
            jobSlug = argumentsProvider.getMandatoryArgument("jobSlug"),
            runId = argumentsProvider.getMandatoryArgument("runId")
        )
        val isReportEnabled = argumentsProvider.getOptionalArgument("avito.report.enabled")?.toBoolean() ?: false
        val reportConfig = if (isReportEnabled) {
            TestRunEnvironment.ReportConfig(
                reportApiUrl = argumentsProvider.getMandatoryArgument("reportApiUrl"),
                reportViewerUrl = argumentsProvider.getMandatoryArgument("reportViewerUrl")
            )
        } else {
            null
        }
        TestRunEnvironment.RunEnvironment(
            isImitation = argumentsProvider.getOptionalArgument("imitation") == "true",
            deviceId = argumentsProvider.getOptionalArgument("deviceId")
                ?: UUID.randomUUID().toString(),
            deviceName = argumentsProvider.getMandatoryArgument("deviceName"),
            teamcityBuildId = argumentsProvider.getMandatoryArgument("teamcityBuildId").toInt(),
            buildBranch = argumentsProvider.getMandatoryArgument("buildBranch"),
            buildCommit = argumentsProvider.getMandatoryArgument("buildCommit"),
            testMetadata = argumentsProvider.getMandatorySerializableArgument(TEST_METADATA_KEY),
            networkType = argumentsProvider.getMandatorySerializableArgument(NETWORKING_TYPE_KEY),
            // todo url'ы не обязательные параметры
            apiUrl = provideApiUrl(
                argumentsProvider = argumentsProvider,
                apiUrlParameterKey = apiUrlParameterKey
            ),
            mockWebServerUrl = mockWebServerUrl,
            videoRecordingFeature = provideVideoRecordingFeature(
                argumentsProvider = argumentsProvider
            ),
            outputDirectory = lazy {
                ContextCompat.getExternalFilesDirs(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    null
                )[0]
            },
            // from GradleInstrumentationPluginConfiguration
            slackToken = argumentsProvider.getMandatoryArgument("slackToken"),
            elasticConfig = ElasticConfigFactory.parse(argumentsProvider),
            sentryConfig = parseSentryConfig(argumentsProvider),
            statsDConfig = parseStatsDConfig(argumentsProvider),
            fileStorageUrl = argumentsProvider.getMandatoryArgument("fileStorageUrl"),
            testRunCoordinates = coordinates,
            reportConfig = reportConfig,
            isSyntheticStepsEnabled =
            argumentsProvider.getOptionalArgument("isSyntheticStepsEnabled")?.toBoolean() ?: false
        )
    } catch (e: Throwable) {
        TestRunEnvironment.InitError(e.message ?: "Can't parse arguments for creating TestRunEnvironment")
    }
}

private fun parseSentryConfig(argumentsProvider: ArgsProvider): SentryConfig {
    val sentryDsn = argumentsProvider.getOptionalArgument("sentryDsn")
    return if (sentryDsn.isNullOrBlank()) {
        SentryConfig.Disabled
    } else {
        SentryConfig.Enabled(
            dsn = sentryDsn,
            environment = "android-test",
            serverName = "",
            release = "",
            tags = emptyMap()
        )
    }
}

private fun parseStatsDConfig(argumentsProvider: ArgsProvider): StatsDConfig {
    val host = argumentsProvider.getOptionalArgument("statsDHost")
    val port = argumentsProvider.getOptionalIntArgument("statsDPort")
    val namespace = argumentsProvider.getOptionalArgument("statsDNamespace")
    return if (!host.isNullOrBlank() && port != null && !namespace.isNullOrBlank()) {
        StatsDConfig.Enabled(
            host = host,
            fallbackHost = host,
            port = port,
            namespace = SeriesName.create(namespace, multipart = true)
        )
    } else {
        StatsDConfig.Disabled
    }
}

private fun provideApiUrl(
    argumentsProvider: ArgsProvider,
    apiUrlParameterKey: String
): HttpUrl {
    val host = argumentsProvider.getOptionalArgument(HostAnnotationResolver.KEY)
        ?: argumentsProvider.getOptionalArgument(apiUrlParameterKey)
        ?: "https://localhost"

    return host.toHttpUrl()
}

private fun provideVideoRecordingFeature(argumentsProvider: ArgsProvider): VideoFeatureValue {
    val videoRecordingArgument = argumentsProvider.getOptionalArgument("videoRecording")

    return when (argumentsProvider.getOptionalArgument("videoRecording")) {
        null, "disabled" -> VideoFeatureValue.Disabled
        "failed" -> VideoFeatureValue.Enabled.OnlyFailed
        "all" -> VideoFeatureValue.Enabled.All
        else -> throw IllegalArgumentException(
            "Failed to resolve video recording resolution from argument: $videoRecordingArgument"
        )
    }
}
