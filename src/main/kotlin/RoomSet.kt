import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RoomSet {

    /*
     * TODO
     * Manages a set of rooms, namely the creation and retrieval.
     * Must be thread-safe.
     */
    private val rooms = HashMap<String, Room>()
    private val mutex = ReentrantLock()

    fun getOrCreateRoom(name: String) : Room{
        mutex.withLock {
            if(rooms.containsKey(name)){
                val room = rooms[name]
                requireNotNull(room)
                return room
            }

            val room = Room(name);
            rooms[name] = room
            return room;
        }
    }

    

}