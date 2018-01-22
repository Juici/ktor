package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.content.*
import kotlinx.coroutines.experimental.*
import java.net.*

class CIOEngine(private val config: CIOEngineConfig) : HttpClientEngine {
    private val dispatcher = config.dispatcher ?: HTTP_CLIENT_DEFAULT_DISPATCHER
    private val endpoints = mutableMapOf<SocketAddress, Endpoint>()

    override fun prepareRequest(builder: HttpRequestBuilder, call: HttpClientCall): HttpRequest =
            CIOHttpRequest(call, this, builder.build())

    internal fun executeRequest(
            request: CIOHttpRequest,
            content: OutgoingContent,
            continuation: CancellableContinuation<CIOHttpResponse>
    ) {
        val address = with(request.url) { InetSocketAddress(host, port) }
        val endpoint = synchronized(endpoints) {
            endpoints.computeIfAbsent(address) { Endpoint(dispatcher, config.endpointConfig, address) }
        }
        endpoint.execute(ConnectorRequestTask(request, content, continuation))
    }

    override fun close() {
        endpoints.forEach { (_, endpoint) ->
            endpoint.close()
        }
    }
}
