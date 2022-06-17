import mu.KotlinLogging
import java.net.InetAddress


fun main() {

    val logger = KotlinLogging.logger("ChatRoomServer")
    val server = ChatRoomServer(logger, InetAddress.getByName("localhost"), 8080)

    server.run()
    server.join()

}