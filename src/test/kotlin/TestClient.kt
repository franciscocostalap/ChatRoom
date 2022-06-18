import ChatRoomServer.Companion.OK_MESSAGE_FORMAT
import java.io.PrintWriter
import java.net.InetAddress
import java.net.Socket
import java.util.*

class TestClient {

    val socket = Socket(InetAddress.getLocalHost(), SERVER_TEST_PORT)
    val writer = PrintWriter(socket.getOutputStream(), true)
    val reader = Scanner(socket.getInputStream())

    init {
        waitOk() // Skip welcome message
    }

    fun enterRoom(roomName: String): String? {
        sendMessage("/enter $roomName")
        return waitOk()
    }

    fun leaveRoom(): String? {
        sendMessage("/leave")
        return waitOk()
    }

    fun sendMessage(message: String) {
        writer.print(message)
        writer.flush()
    }

    fun readLine(): String? {
        return try {
            reader.nextLine()
        } catch (e: NoSuchElementException) {
            null
        }
    }

    private fun waitOk(): String? {
        val line = readLine() ?: return "Readline returns null"
        return if (line == OK_MESSAGE_FORMAT)
            null
        else
            line
    }

}