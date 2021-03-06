package com.avito.runner.scheduler.metrics.model

import com.avito.runner.service.model.TestCase

// todo move to :service
fun TestCase.Companion.createStubInstance(
    className: String = "com.avito.Test",
    methodName: String = "test",
    deviceName: String = "api22"
) = TestCase(className, methodName, deviceName)

internal fun TestKey.Companion.createStubInstance(
    testCase: TestCase = TestCase.createStubInstance(),
    executionNumber: Int = 0
) = TestKey(testCase, executionNumber)

internal fun String.toTestKey() = TestKey.createStubInstance(
    TestCase.createStubInstance(methodName = this)
)
