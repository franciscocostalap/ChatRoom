import mu.KotlinLogging
import java.net.InetAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

const val SERVER_TEST_PORT = 8856

class ServerTests {

    @Test
    fun `multiple threads calling run only one succeeds the others leave with an exception`() {

        val threadPool = Executors.newCachedThreadPool()
        val server = ChatRoomServer(KotlinLogging.logger { }, InetAddress.getLocalHost(), SERVER_TEST_PORT)
        val cdl = CountDownLatch(50)
        val exceptionResults = ConcurrentLinkedQueue<Throwable>()

        repeat(50) {
            threadPool.execute {
                try {
                    server.run()
                    cdl.countDown()
                } catch (exc: Exception) {
                    exceptionResults.add(exc)
                }
            }
        }

        threadPool.shutdown()
        threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)

        server.stop()

        assertEquals(49, cdl.count)
        assertEquals(49, exceptionResults.size)
        exceptionResults.forEach {
            assertIs<IllegalStateException>(it)
        }
    }

    @Test
    fun `try to join with the server offline throws exception`() {
        val server = ChatRoomServer(KotlinLogging.logger { }, InetAddress.getLocalHost(), SERVER_TEST_PORT)
        assertFailsWith<IllegalStateException> {
            server.join()
        }
    }

    @Test
    fun `try to join with the server after stop throws exception`() {
        val server = ChatRoomServer(KotlinLogging.logger { }, InetAddress.getLocalHost(), SERVER_TEST_PORT)
        server.run()
        server.stop()
        assertFailsWith<IllegalStateException> {
            server.join()
        }
    }

    @Test
    fun `try to join with the server after shutdown throws exception`() {
        val server = ChatRoomServer(KotlinLogging.logger { }, InetAddress.getLocalHost(), SERVER_TEST_PORT)
        server.run()
        server.shutdown(100.seconds)
        assertFailsWith<IllegalStateException> {
            server.join()
        }
    }

    @Test
    fun `simple exchange between two clients`() {
        val server = ChatRoomServer(KotlinLogging.logger { }, InetAddress.getLocalHost(), SERVER_TEST_PORT)
        server.run()

        val roomName = "test"
        val client1 = TestClient()
        val client2 = TestClient()

        assertNull(client1.enterRoom(roomName))
        assertNull(client2.enterRoom(roomName))

        val messageSent = "abcd"
        repeat(1) {
            client1.sendMessage(messageSent)
            client2.sendMessage(messageSent)

            assertEquals(Room.formatMessage(roomName, "client-2", messageSent), client1.readLine())
            assertEquals(Room.formatMessage(roomName, "client-1", messageSent), client2.readLine())
        }
        assertNull(client1.leaveRoom())
        assertNull(client2.leaveRoom())

        server.shutdown(1.seconds)
    }

    data class ClientMessage(val client: TestClient, val message: String?)

    @Test
    fun `multiple clients send messages`() {
        val clients = mutableListOf<TestClient>()
        val server = ChatRoomServer(KotlinLogging.logger { }, InetAddress.getLocalHost(), SERVER_TEST_PORT)
        val receivedMessages = mutableListOf<ClientMessage>()
        val messageSent = "abcd"
        val clientsConnected = 100
        val messagesSent = 50

        val totalMessagesReceivedPerPersonExpected = (messagesSent * clientsConnected) - messagesSent
        val totalMessagesExpected = totalMessagesReceivedPerPersonExpected * clientsConnected

        server.run()

        repeat(clientsConnected) {
            val client = TestClient()
            clients.add(client)
            assertNull(client.enterRoom("teste"))
        }

        repeat(messagesSent) {
            clients.forEach { transmitter ->
                transmitter.sendMessage(messageSent)
                clients.filter { receiver -> receiver != transmitter }
                    .forEach { receiver ->
                        val messageReceived = receiver.readLine()
                        val clientMessage = ClientMessage(receiver, messageReceived)
                        receivedMessages.add(clientMessage)
                    }
            }
        }
        clients.forEach {
            it.leaveRoom()
        }

        server.shutdown(1.seconds)
        assertEquals(totalMessagesExpected, receivedMessages.size)

        clients.forEach { receiver ->
            val messageOcurrencesByClient: Map<TestClient, Int> = receivedMessages
                .filter { message -> message.client != receiver }
                .groupingBy { it.client }
                .eachCount()

            messageOcurrencesByClient.keys.forEach { client ->
                assertEquals(totalMessagesReceivedPerPersonExpected, messageOcurrencesByClient[client])
            }
        }

    }

}