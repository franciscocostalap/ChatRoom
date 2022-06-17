package sync

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.LinkedList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.resume


class MessageQueue<T> {

    data class PendingTake<T>(val continuation: CancellableContinuation<T>){
        var isComplete: Boolean = false
    }
    private val pendingTakers = LinkedList<PendingTake<T>>()
    private var queue = LinkedList<T>()
    private val mutex = ReentrantLock()


    /**
     * Tries to take a message from the queue returning
     * @throws TimeoutCancellationException if the timeout is reached
     */
    suspend fun take(timeout : Long) : T{
        mutex.withLock {
            if(queue.isNotEmpty()){
                return queue.removeFirst()
            }
        }

        return withTimeout(timeout) {
            suspendCancellableCoroutine<T> { cont ->

                val node = internalTake(cont)

                cont.invokeOnCancellation {
                    cancelCleanup(node)
                }
            }
        }
    }

    /**
     *
     */
    private fun internalTake(cont: CancellableContinuation<T>) : PendingTake<T>? {
        mutex.withLock {
            if (queue.isNotEmpty()) {
                val element = queue.removeFirst()
                cont.resume(element)
                return null
            }


            val node = PendingTake(cont)
            pendingTakers.add(node)
            return node
        }
    }

    private fun cancelCleanup(node : PendingTake<T>?) {
        if (node != null) {
            mutex.withLock {
                if (!node.isComplete) {
                    pendingTakers.remove(node)
                    node.isComplete = true
                }
            }
        }
    }


    fun put(elem : T){
        var node: PendingTake<T>? = null
        mutex.withLock {
            if(pendingTakers.isEmpty())
                queue.add(elem)
            else{
                node = pendingTakers.removeFirst()
                node?.isComplete = true
            }
        }
        node?.continuation?.resume(elem)
    }

}