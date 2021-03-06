package com.avito.runner.service

import com.avito.coroutines.extensions.Dispatchers
import com.avito.logger.LoggerFactory
import com.avito.logger.create
import com.avito.runner.service.listener.TestListener
import com.avito.runner.service.model.intention.Intention
import com.avito.runner.service.model.intention.IntentionResult
import com.avito.runner.service.worker.DeviceWorker
import com.avito.runner.service.worker.DeviceWorkerMessage
import com.avito.runner.service.worker.device.Device
import com.avito.runner.service.worker.listener.CompositeDeviceListener
import com.avito.runner.service.worker.listener.DeviceListener
import com.avito.runner.service.worker.listener.DeviceLogListener
import com.avito.runner.service.worker.listener.MessagesDeviceListener
import com.avito.time.TimeProvider
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import java.io.File

class IntentionExecutionServiceImplementation(
    private val outputDirectory: File,
    private val loggerFactory: LoggerFactory,
    private val devices: ReceiveChannel<Device>,
    private val intentionsRouter: IntentionsRouter = IntentionsRouter(loggerFactory = loggerFactory),
    private val testListener: TestListener,
    private val deviceMetricsListener: DeviceListener,
    private val timeProvider: TimeProvider,
    private val deviceWorkersDispatcher: Dispatchers = Dispatchers.SingleThread
) : IntentionExecutionService {

    private val logger = loggerFactory.create<IntentionExecutionService>()

    private val intentions: Channel<Intention> =
        Channel(Channel.UNLIMITED)
    private val results: Channel<IntentionResult> =
        Channel(Channel.UNLIMITED)
    private val messages: Channel<DeviceWorkerMessage> =
        Channel(Channel.UNLIMITED)
    private val deviceSignals: Channel<Device.Signal> =
        Channel(Channel.UNLIMITED)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun start(scope: CoroutineScope): IntentionExecutionService.Communication {
        scope.launch(CoroutineName("intention-execution-service")) {
            launch(CoroutineName("device-workers")) {
                for (device in devices) {
                    DeviceWorker(
                        intentionsRouter = intentionsRouter,
                        device = device,
                        outputDirectory = outputDirectory,
                        deviceListener = CompositeDeviceListener(
                            listOf(
                                MessagesDeviceListener(messages),
                                DeviceLogListener(device.logger),
                                deviceMetricsListener
                            )
                        ),
                        testListener = testListener,
                        dispatchers = deviceWorkersDispatcher,
                        timeProvider = timeProvider
                    ).run(scope)
                }
                throw IllegalStateException("devices channel was closed")
            }

            launch(CoroutineName("send-intentions")) {
                for (intention in intentions) {
                    logger.debug("received intention: $intention")
                    intentionsRouter.sendIntention(intention = intention)
                }
            }

            launch(CoroutineName("worker-messages")) {
                for (message in messages) {
                    logger.debug("received message: $message")
                    when (message) {
                        is DeviceWorkerMessage.ApplicationInstalled ->
                            logger.debug("Application: ${message.installation.installation.application} installed")

                        is DeviceWorkerMessage.FailedIntentionProcessing -> {
                            logger.debug(
                                "Received worker failed message during executing intention:" +
                                    " ${message.intention}. Rescheduling..."
                            )

                            intentionsRouter.sendIntention(intention = message.intention)
                        }

                        is DeviceWorkerMessage.Result ->
                            results.send(message.intentionResult)

                        is DeviceWorkerMessage.WorkerDied ->
                            deviceSignals.send(Device.Signal.Died(message.coordinate))
                    }
                }
            }
        }

        return IntentionExecutionService.Communication(
            intentions = intentions,
            results = results,
            deviceSignals = deviceSignals
        )
    }

    override fun stop() {
        intentionsRouter.cancel()
        intentions.cancel()
        results.cancel()
        messages.cancel()
        devices.cancel()
        deviceSignals.cancel()
    }
}
