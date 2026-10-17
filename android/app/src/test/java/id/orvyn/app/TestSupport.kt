package id.orvyn.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import java.net.ServerSocket

/** A tiny HTTP server on a free local port that answers each request with [answer] of its path ("/api/requests"). Plain sockets: no test libraries. */
class StubServer(answer: (path: String) -> String) : AutoCloseable {
    private val socket = ServerSocket(0)
    val url = "http://127.0.0.1:${socket.localPort}/api"

    init {
        Thread {
            while (!socket.isClosed) runCatching {
                socket.accept().use { c ->
                    val input = c.getInputStream().bufferedReader()
                    val path = input.readLine().orEmpty().split(' ').getOrElse(1) { "" }.substringBefore('?')
                    var length = 0
                    while (true) { val h = input.readLine() ?: break; if (h.isEmpty()) break; if (h.startsWith("Content-Length:", true)) length = h.substringAfter(':').trim().toInt() }
                    repeat(length) { input.read() } // the request body, unread
                    val body = answer(path).toByteArray()
                    c.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(body); flush()
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    override fun close() = socket.close()
}

/**
 * MASTER (Button, List row): text wraps between words. Returns every word on screen that was broken across lines although it
 * would fit on a line of its own. A word wider than its whole line can only break; that is a width limit, not a layout bug.
 */
fun ComposeTestRule.avoidableWordBreaks(): List<String> {
    val out = mutableListOf<String>()
    for (node in onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()) {
        val r = mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.firstOrNull() ?: continue
        val text = r.layoutInput.text.text
        for (line in 0 until r.lineCount - 1) {
            val end = r.getLineEnd(line)
            if (end <= 0 || end >= text.length || text[end - 1].isWhitespace() || text[end].isWhitespace()) continue
            var a = end; while (a > 0 && !text[a - 1].isWhitespace()) a--
            var b = end; while (b < text.length && !text[b].isWhitespace()) b++
            val width = (a until b).sumOf { r.getBoundingBox(it).width.toDouble() }
            if (width <= r.size.width) out += "\"${text.substring(a, b)}\" broken in \"$text\""
        }
    }
    return out
}
