import mu.KotlinLogging
import java.net.InetAddress


fun main() {

    val logger = KotlinLogging.logger("ChatRoomServer")
    val server = ChatRoomServer(logger, InetAddress.getByName("localhost"), 8080)

    server.run()

    while (true) {
        print("> ")
        val cmd = readln()
        if (cmd == "shut") {

            logger.info { "shutting" }
            server.shutdown(10)

            logger.info { "after" }
            break
        }
    }

}