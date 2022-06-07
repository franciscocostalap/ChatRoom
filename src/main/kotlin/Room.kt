import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class Room(val name : String){

    private val mutex = ReentrantLock()
    private val clientsConnected = mutableSetOf<ConnectedClient>()

    /**
     * Adds a client to the room
     */
    fun enter(client: ConnectedClient){
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
        val formattedMessage = if(message != "\n") "[$name]${client.name} says $message" else "\n"
        mutex.withLock {
            clientsConnected.forEach { receiver ->
                if (receiver != client) {
                    receiver.postRoomMessage(formattedMessage, this)
                }
            }
        }
    }

}