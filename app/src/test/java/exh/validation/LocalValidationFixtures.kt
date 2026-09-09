package exh.validation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Synthetic state shared by host-side feature tests. It contains no application or device data. */
data class LocalAppStateSnapshot(
    val ratings: Map<Long, Int> = emptyMap(),
    val notInterested: Set<Long> = emptySet(),
    val libraryMangaIds: Set<Long> = emptySet(),
    val trackedChapterByManga: Map<Long, String> = emptyMap(),
    val exposureCountByManga: Map<Long, Int> = emptyMap(),
    val actionHistoryIds: List<String> = emptyList(),
    val scheduleEnabled: Boolean = false,
    val scheduleWindowIds: List<String> = emptyList(),
    val evaluatedSourceIds: Set<Long> = emptySet(),
)

/** A resettable in-memory fixture so a test cannot leak state into another test. */
class LocalAppStateFixture(
    private val initial: LocalAppStateSnapshot = LocalAppStateSnapshot(),
) {
    private var current = initial

    @Synchronized
    fun snapshot(): LocalAppStateSnapshot = current

    @Synchronized
    fun replace(snapshot: LocalAppStateSnapshot) {
        current = snapshot
    }

    @Synchronized
    fun reset() {
        current = initial
    }
}

object LocalAppStateFixtures {
    fun baseline(): LocalAppStateSnapshot = LocalAppStateSnapshot(
        ratings = mapOf(101L to 5, 102L to 2),
        notInterested = setOf(103L),
        libraryMangaIds = setOf(101L),
        trackedChapterByManga = mapOf(101L to "chapter-7"),
        exposureCountByManga = mapOf(101L to 2, 104L to 1),
        actionHistoryIds = listOf("preference-101", "chapter-101"),
        scheduleEnabled = true,
        scheduleWindowIds = listOf("weekday-evening"),
        evaluatedSourceIds = setOf(1L, 2L),
    )
}

enum class LocalSourceFixtureMode {
    SUCCESS,
    HTTP_FAILURE,
    MALFORMED_RESPONSE,
    TIMEOUT,
}

enum class LocalSourceEndpoint(val path: String) {
    POPULAR("/popular"),
    LATEST("/latest"),
    SEARCH("/search"),
    DETAIL("/detail"),
    CHAPTERS("/chapters"),
    PREVIEW("/preview"),
}

/**
 * Loopback-only HTTP fixture server for deterministic source and preview tests.
 *
 * It deliberately uses synthetic JSON and localhost only. Production source code never imports
 * this class; later feature tests can select a route and failure mode without contacting a real
 * source or depending on an external service.
 */
class LocalSourceFixtureServer(
    private val mode: LocalSourceFixtureMode = LocalSourceFixtureMode.SUCCESS,
    private val timeoutDelayMillis: Long = 250L,
) : AutoCloseable {
    private val serverSocket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val executor = Executors.newCachedThreadPool()
    private var acceptFuture: Future<*>? = null

    val baseUrl: String
        get() = "http://127.0.0.1:${serverSocket.localPort}"

    fun start(): LocalSourceFixtureServer {
        acceptFuture = executor.submit {
            while (!serverSocket.isClosed) {
                try {
                    executor.submit { respond(serverSocket.accept()) }
                } catch (_: IOException) {
                    if (!serverSocket.isClosed) throw IllegalStateException("fixture-server-accept-failed")
                }
            }
        }
        return this
    }

    private fun respond(socket: Socket) {
        socket.use { client ->
            try {
                val reader = client.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return
                while (reader.readLine()?.isNotEmpty() == true) {
                    // Consume request headers before writing the response.
                }
                val path = requestLine.split(' ').getOrNull(1)?.substringBefore('?')
                val endpoint = LocalSourceEndpoint.entries.firstOrNull { it.path == path }
                val (status, body) = response(endpoint)
                writeResponse(client.getOutputStream(), status, body)
            } catch (_: IOException) {
                // A client-side timeout/cancellation may close the socket before the fixture writes.
            }
        }
    }

    private fun response(endpoint: LocalSourceEndpoint?): Pair<Int, String> {
        if (mode == LocalSourceFixtureMode.TIMEOUT) {
            try {
                Thread.sleep(timeoutDelayMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return 408 to "{\"error\":\"fixture-timeout-interrupted\"}"
            }
        }

        if (endpoint == null) return 404 to "{\"error\":\"fixture-route-not-found\"}"

        return when (mode) {
            LocalSourceFixtureMode.SUCCESS ->
                200 to
                    "{\"endpoint\":\"${endpoint.name.lowercase()}\",\"items\":[\"fixture-${endpoint.name.lowercase()}\"]}"
            LocalSourceFixtureMode.HTTP_FAILURE -> 503 to "{\"error\":\"fixture-http-failure\"}"
            LocalSourceFixtureMode.MALFORMED_RESPONSE -> 200 to "fixture-not-json"
            LocalSourceFixtureMode.TIMEOUT -> 200 to "{\"endpoint\":\"timeout\"}"
        }
    }

    private fun writeResponse(output: OutputStream, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $status ${if (status == 200) "OK" else "ERROR"}\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    override fun close() {
        try {
            serverSocket.close()
        } finally {
            acceptFuture?.cancel(true)
            executor.shutdownNow()
        }
    }
}

class LocalSourceFixtureClient(
    private val server: LocalSourceFixtureServer,
) {
    suspend fun get(endpoint: LocalSourceEndpoint, timeoutMillis: Int = 1_000): String =
        withContext(Dispatchers.IO) {
            val connection = URI("${server.baseUrl}${endpoint.path}").toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            try {
                check(connection.responseCode in 200..299) {
                    "fixture-http-status-${connection.responseCode}"
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
}
