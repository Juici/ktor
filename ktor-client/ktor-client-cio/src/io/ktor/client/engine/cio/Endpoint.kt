package io.ktor.client.engine.cio

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import java.io.*
import java.net.*
import java.util.concurrent.atomic.*

class EndpointConfig {
    var queueSizePerThread: Int = 32
    var maxConnectionsPerRoute: Int = 1000
    var keepAliveTime: Int = 5000
}

internal class Endpoint(
        private val dispatcher: CoroutineDispatcher,
        private val endpointConfig: EndpointConfig,
        private val address: InetSocketAddress
) : Closeable {
    private val tasks: Channel<ConnectorRequestTask> = Channel(Channel.UNLIMITED)
    private val connections = AtomicInteger()
    private val queueSize = AtomicInteger()

    init {
        newConnection()
    }

    fun execute(task: ConnectorRequestTask) {
        queueSize.incrementAndGet()
        tasks.offer(task)

        val queueSize = queueSize.get()
        val connectionsCount = connections.get()
        if (queueSize > endpointConfig.queueSizePerThread && connectionsCount < endpointConfig.maxConnectionsPerRoute) {
            newConnection()
        }
    }

    private fun newConnection() {
        connections.incrementAndGet()

        val connection = ConnectionPipeline(dispatcher, endpointConfig.keepAliveTime, address, tasks) {
            queueSize.decrementAndGet()
        }

        connection.pipelineContext.invokeOnCompletion {
            connections.decrementAndGet()
        }
    }

    override fun close() {
        tasks.close()
    }
}
