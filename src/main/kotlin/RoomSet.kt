import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe roomSet implementation.
 */
class RoomSet {

    /*
     * Manages a set of rooms, namely the creation and retrieval.
     * Must be thread-safe.
     */
    private val rooms = HashMap<String, Room>()
    private val mutex = ReentrantLock()

    fun getOrCreateRoom(name: String) : Room{
        mutex.withLock {
            val filteredName = name.trim().removeSuffix(System.lineSeparator())
            if (rooms.containsKey(filteredName)) {
                val room = rooms[filteredName]
                requireNotNull(room)
                return room
            }

            val room = Room(name)
            rooms[filteredName] = room
            return room
        }
    }


}