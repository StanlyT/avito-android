package com.avito.instrumentation.internal.suite.filter

import com.avito.android.runner.report.factory.ReportFactory
import com.avito.instrumentation.configuration.InstrumentationFilter

internal interface FilterFactory {

    fun createFilter(): TestsFilter

    companion object {

        internal const val JUNIT_IGNORE_ANNOTATION = "org.junit.Ignore"

        fun create(
            filterData: InstrumentationFilter.Data,
            impactAnalysisResult: ImpactAnalysisResult,
            reportConfig: ReportFactory.Config,
            factory: ReportFactory
        ): FilterFactory {
            return FilterFactoryImpl(
                filterData = filterData,
                impactAnalysisResult = impactAnalysisResult,
                reportConfig = reportConfig,
                factory = factory
            )
        }
    }
}
