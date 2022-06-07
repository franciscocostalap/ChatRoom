import mu.KotlinLogging
import java.net.InetAddress
import java.nio.channels.AsynchronousSocketChannel


fun main() {

    val logger = KotlinLogging.logger("ChatRoomServer")
    val server = ChatRoomServer(logger, InetAddress.getByName("localhost"), 8080)

    server.run()

    while (true) {
        print("> ")
        val cmd = readln()
        if (cmd == "exit") {
            break
        }
    }

}