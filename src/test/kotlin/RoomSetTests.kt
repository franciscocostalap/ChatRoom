import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.test.assertTrue

class RoomSetTests {

    @Test
    fun `multiple threads try to create a room with the same name only one is created`() {
        val roomSet = RoomSet()
        val roomName = "test"
        val threadPool = Executors.newCachedThreadPool()
        val mutex = ReentrantLock()
        val rooms = mutableListOf<Room>()

        repeat(10) {
            threadPool.execute() {
                val room = roomSet.getOrCreateRoom(roomName)
                mutex.withLock {
                    rooms.add(room)
                }
            }
        }

        threadPool.shutdown()
        threadPool.awaitTermination(1, TimeUnit.SECONDS)

        assertTrue(rooms.fold(true) { acc, room -> acc && room === rooms.first() })
    }

}