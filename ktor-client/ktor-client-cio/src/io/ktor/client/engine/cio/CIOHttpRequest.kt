package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.net.*
import java.util.*


class CIOHttpRequest(
        override val call: HttpClientCall,
        private val engine: CIOEngine,
        requestData: HttpRequestData
) : HttpRequest {
    override val attributes: Attributes = Attributes()
    override val method: HttpMethod = requestData.method
    override val url: Url = requestData.url
    override val headers: Headers = requestData.headers

    override val executionContext: CompletableDeferred<Unit> = CompletableDeferred()

    init {
        require(url.protocol.name == "http") { "CIOEngine support only http yet" }
    }

    override suspend fun execute(content: OutgoingContent): HttpResponse {
        return suspendCancellableCoroutine<CIOHttpResponse> {
            engine.executeRequest(this, content, it)
        }
    }
}

