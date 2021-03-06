package io.ktor.sessions

import io.ktor.application.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*
import kotlin.reflect.*

class SessionTrackerById(val type: KClass<*>, val serializer: SessionSerializer, val storage: SessionStorage, val sessionIdProvider: () -> String) : SessionTracker {
    private val SessionIdKey = AttributeKey<String>("SessionId")

    override suspend fun load(call: ApplicationCall, transport: String?): Any? {
        val sessionId = transport ?: return null

        call.attributes.put(SessionIdKey, sessionId)
        try {
            return storage.read(sessionId) { channel ->
                val text = channel.readUTF8Line() ?: throw IllegalStateException("Failed to read stored session from $channel")
                serializer.deserialize(text)
            }
        } catch (notFound: NoSuchElementException) {
            call.application.log.debug("Failed to lookup session: $notFound")
        }
        return null
    }

    override suspend fun store(call: ApplicationCall, value: Any): String {
        val sessionId = call.attributes.computeIfAbsent(SessionIdKey, sessionIdProvider)
        val serialized = serializer.serialize(value)
        storage.write(sessionId) { channel ->
            channel.writeStringUtf8(serialized)
            channel.close()
        }
        return sessionId
    }

    override suspend fun clear(call: ApplicationCall) {
        val sessionId = call.attributes.takeOrNull(SessionIdKey)
        if (sessionId != null) {
            storage.invalidate(sessionId)
        }
    }

    override fun validate(value: Any) {
        if (!type.javaObjectType.isAssignableFrom(value.javaClass)) {
            throw IllegalArgumentException("Value for this session tracker expected to be of type $type but was $value")
        }
    }
}
