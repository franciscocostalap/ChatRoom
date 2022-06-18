import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class Room(val name: String) {

    private val mutex = ReentrantLock()
    private val clientsConnected = mutableSetOf<ConnectedClient>()

    companion object {
        fun formatMessage(roomName: String, clientName: String, message: String): String {
            return "[$roomName]${clientName} says $message"
        }
    }

    /**
     * Adds a client to the room
     */
    fun enter(client: ConnectedClient) {
        mutex.withLock {
            clientsConnected.add(client)
        }
    }

    /**
     * Removes a client from the room
     */
    fun leave(client: ConnectedClient){
        mutex.withLock {
            clientsConnected.remove(client)
        }
    }


    /**
     * Sends a message to all clients in the room
     */
    fun post(client: ConnectedClient, message: String){
        val formattedMessage = formatMessage(name, client.name, message)
        mutex.withLock {
            clientsConnected.forEach { receiver ->
                if (receiver != client) {
                    receiver.postRoomMessage(formattedMessage, this)
                }
            }
        }
    }

}