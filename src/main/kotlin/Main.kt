import mu.KotlinLogging
import java.net.InetAddress
import kotlin.time.Duration.Companion.seconds


fun main() {

    val logger = KotlinLogging.logger("ChatRoomServer")
    val server = ChatRoomServer(logger, InetAddress.getByName("localhost"), 8080)

    server.run()

    while (true) {
        print("> ")
        val readline = readln()
        when (val line = Line.parseServer(readline)) {
            is Line.InvalidLine -> println(line.reason)
            is Line.ShutdownCommand -> {
                logger.info { "Started shutdown." }

                server.shutdown(line.timeout.seconds)

                logger.info { "Terminated shutdown." }
                break
            }
            is Line.ExitCommand -> {
                logger.info { "Exiting" }

                server.stop()
                break
            }
            else -> error("Not expected here")
        }
    }

}